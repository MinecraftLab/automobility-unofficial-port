package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.*;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Per-vehicle authoritative decision, including finalized generation tombstones.
 * Atomic visibility and process-crash recovery do not claim Windows power-loss durability. */
public final class GarageTransferJournal {
    public static final int MAX_BYTES = 4_194_304, MAX_RECORDS = 256;
    public static final long MAX_TOTAL_BYTES = 67_108_864;
    private static final int MAX_FILES = MAX_RECORDS * 4;
    public record Owner(int version, CompoundTag capsule) {
        public Owner {
            if (version < 1 || capsule == null || capsule.isEmpty() || capsule.sizeInBytes() > 1_048_576)
                throw new IllegalArgumentException("Bounded versioned owner capsule required");
            capsule = capsule.copy();
        }
        @Override public CompoundTag capsule() { return capsule.copy(); }
    }
    public record Entry(Request request, Decision decision, boolean checkpointed, boolean finalized,
                        CompoundTag before, CompoundTag after, Map<String,Owner> owners, Set<String> published) {
        public Entry {
            Objects.requireNonNull(request); Objects.requireNonNull(decision);
            if (before == null || before.isEmpty() || after == null || after.isEmpty() || owners == null
                    || owners.size() > 16 || published == null || !owners.keySet().containsAll(published)
                    || owners.keySet().stream().anyMatch(k -> !k.matches("[a-z0-9_]{1,48}"))
                    || (decision == Decision.UNDECIDED && (!published.isEmpty() || checkpointed || finalized))
                    || (checkpointed && !published.equals(owners.keySet())) || (finalized && !checkpointed))
                throw new IllegalArgumentException("Invalid Garage decision state");
            before = before.copy(); after = after.copy(); owners = Map.copyOf(owners); published = Set.copyOf(published);
        }
        @Override public CompoundTag before() { return before.copy(); }
        @Override public CompoundTag after() { return after.copy(); }
        public Entry decide(Decision next) {
            if (next == Decision.UNDECIDED || (decision != Decision.UNDECIDED && decision != next))
                throw new IllegalStateException("Garage decision is immutable");
            return new Entry(request,next,checkpointed,finalized,before,after,owners,published);
        }
        public Entry acknowledge(String id) {
            if (decision == Decision.UNDECIDED || !owners.containsKey(id)) throw new IllegalStateException("Uncommitted owner ACK");
            var all = new HashSet<>(published); all.add(id);
            return new Entry(request,decision,checkpointed,finalized,before,after,owners,all);
        }
        public Entry checkpoint() {
            if (decision == Decision.UNDECIDED || !published.equals(owners.keySet())) throw new IllegalStateException("Owners pending");
            return new Entry(request,decision,true,false,before,after,owners,published);
        }
        public Entry finish() {
            if (!checkpointed) throw new IllegalStateException("Vanilla persistence checkpoint required");
            return new Entry(request,decision,true,true,before,after,owners,published);
        }
    }
    public interface Repository {
        Entry read(UUID vehicle) throws IOException;
        void write(Entry expected, Entry next) throws IOException;
    }
    private final Path directory;
    public GarageTransferJournal(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path directory() { return directory; }
    private Path path(UUID vehicle) { return directory.resolve(vehicle + ".nbt"); }
    public Entry read(UUID vehicle) throws IOException {
        var target = path(vehicle);
        if (!Files.exists(target)) return null;
        var value = readFile(target);
        if (!value.request().vehicle().equals(vehicle)) throw new IOException("Journal filename identity mismatch");
        return value;
    }
    public List<Entry> load() throws IOException {
        if (!Files.exists(directory)) return List.of();
        var files=managedFiles();
        long bytes=0; int records=0;
        for(var file:files) {
            bytes=Math.addExact(bytes,Files.size(file));
            if(file.getFileName().toString().endsWith(".nbt")) records++;
        }
        if(records>MAX_RECORDS||bytes>MAX_TOTAL_BYTES) throw new IOException("Garage journal capacity exceeded, including retained pending files");
        var entries=new ArrayList<Entry>();
        for (var file:files) {
            if(!file.getFileName().toString().endsWith(".nbt")) continue;
            var entry=readFile(file);
            if (!file.getFileName().toString().equals(entry.request().vehicle()+".nbt")) throw new IOException("Journal identity mismatch");
            entries.add(entry);
        }
        return List.copyOf(entries);
    }
    private List<Path> managedFiles() throws IOException {
        try(var stream=Files.list(directory)) {
            var files=stream.filter(p->{var n=p.getFileName().toString();return n.endsWith(".nbt")||n.contains(".nbt.pending-");}).sorted().limit(MAX_FILES+1L).toList();
            if(files.size()>MAX_FILES) throw new IOException("Too many retained Garage journal files");
            for(var file:files) if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Non-regular Garage journal input");
            return files;
        }
    }
    public Repository repository() {
        return new Repository() {
            public Entry read(UUID id) throws IOException { return GarageTransferJournal.this.read(id); }
            public void write(Entry expected,Entry next) throws IOException { GarageTransferJournal.this.write(expected,next); }
        };
    }
    public void write(Entry expected,Entry next) throws IOException {
        var current=read(next.request().vehicle());
        if (!Objects.equals(current,expected)) throw new IOException("Stale Garage journal preimage");
        validateTransition(expected,next);
        Files.createDirectories(directory);
        var target=path(next.request().vehicle()); var bytes=encode(next);
        long total=bytes.length; int count=Files.exists(target)?0:1;
        var files=managedFiles();
        // Admission bounds peak disk usage too: the previous target survives until atomic replacement.
        for(var file:files) {
            total=Math.addExact(total,Files.size(file));
            if(file.getFileName().toString().endsWith(".nbt")) count++;
        }
        if(count>MAX_RECORDS||files.size()>=MAX_FILES||total>MAX_TOTAL_BYTES) throw new IOException("Garage journal capacity exceeded, including retained pending files");
        var pending=directory.resolve(target.getFileName()+".pending-"+UUID.randomUUID());
        try {
            try(var output=Files.newOutputStream(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) { output.write(bytes); }
            try(var channel=FileChannel.open(pending,StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(!next.equals(read(next.request().vehicle()))) throw new IOException("Garage journal readback mismatch");
        } catch(IOException error) {
            // A successful rename followed by an I/O exception is already authoritative.
            // Never infer rollback merely from the exception.
            try { if(next.equals(read(next.request().vehicle()))) return; }
            catch(IOException uncertain) { error.addSuppressed(uncertain); }
            throw error;
        }
    }
    public static void validateTransition(Entry previous,Entry next) throws IOException {
        if(previous==null) {
            if(next.request().generation()!=1||next.decision()!=Decision.UNDECIDED) throw new IOException("First Garage intent required");
            return;
        }
        if(!previous.request().transaction().equals(next.request().transaction())) {
            if(!previous.finalized()||previous.request().generation()==Long.MAX_VALUE
                    ||next.request().generation()!=previous.request().generation()+1||next.decision()!=Decision.UNDECIDED
                    ||!previous.request().vehicle().equals(next.request().vehicle())) throw new IOException("Previous transaction unresolved");
            return;
        }
        if(!previous.request().equals(next.request())||!previous.before().equals(next.before())||!previous.after().equals(next.after())
                ||!previous.owners().equals(next.owners())||!next.published().containsAll(previous.published())
                ||(previous.decision()!=Decision.UNDECIDED&&next.decision()!=previous.decision())
                ||(previous.checkpointed()&&!next.checkpointed())||(previous.finalized()&&!next.equals(previous)))
            throw new IOException("Garage immutable decision/preimage changed");
        if(previous.equals(next)) return;
        if(previous.decision()!=next.decision()) {
            if(!previous.published().equals(next.published())||next.checkpointed()||next.finalized()) throw new IOException("Decision cannot imply publication/checkpoint");
        } else if(!previous.published().equals(next.published())) {
            if(next.decision()==Decision.UNDECIDED||next.published().size()!=previous.published().size()+1
                    ||next.checkpointed()!=previous.checkpointed()||next.finalized()!=previous.finalized()) throw new IOException("One explicit owner ACK required");
        } else if(previous.checkpointed()!=next.checkpointed()) {
            if(next.finalized()) throw new IOException("Checkpoint and finalization are separate durable steps");
        } else if(previous.finalized()!=next.finalized()) {
            if(!previous.checkpointed()) throw new IOException("Prior durable checkpoint required");
        } else throw new IOException("Invalid Garage phase transition");
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] nbtBytes(CompoundTag value) throws IOException {
        if(value.sizeInBytes()>MAX_BYTES) throw new IOException("Garage payload too large");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) { NbtIo.write(value,out); }
        if(bytes.size()>MAX_BYTES) throw new IOException("Garage payload too large");
        return bytes.toByteArray();
    }
    static byte[] encode(Entry entry) throws IOException {
        var raw=payload(entry);var bytes=nbtBytes(raw);var envelope=new CompoundTag();
        envelope.putInt("schema",1);envelope.putByteArray("payload",bytes);envelope.putString("sha256",hash(bytes));
        return nbtBytes(envelope);
    }
    static Entry readFile(Path path) throws IOException {
        if(Files.size(path)>MAX_BYTES) throw new IOException("Garage journal too large");
        try(var in=new DataInputStream(Files.newInputStream(path))) {
            var envelope=NbtIo.read(in,NbtAccounter.create(MAX_BYTES));
            if(in.read()!=-1||envelope.getIntOr("schema",0)!=1||!envelope.keySet().equals(Set.of("schema","payload","sha256"))) throw new IOException("Unknown Garage journal envelope");
            requireType(envelope,"schema",Tag.TAG_INT);requireType(envelope,"sha256",Tag.TAG_STRING);
            var bytes=envelope.getByteArray("payload").orElseThrow(()->new IOException("Missing Garage payload"));
            if(!hash(bytes).equals(envelope.getStringOr("sha256",""))) throw new IOException("Garage payload hash mismatch");
            try(var payload=new DataInputStream(new ByteArrayInputStream(bytes))) {
                var value=NbtIo.read(payload,NbtAccounter.create(MAX_BYTES));
                if(payload.read()!=-1) throw new IOException("Trailing Garage payload");
                return fromPayload(value);
            }
        } catch(RuntimeException error) { throw new IOException("Malformed Garage transaction",error); }
    }
    private static CompoundTag payload(Entry entry) {
        var r=entry.request();var out=new CompoundTag();out.putInt("schema",1);
        out.putString("transaction",r.transaction().toString());out.putLong("generation",r.generation());
        out.putString("operation",r.operation().name());out.putString("actor",r.actor().toString());out.putString("vehicle",r.vehicle().toString());
        out.putString("dimension",r.dimension());out.putString("beforeEngine",r.beforeEngine());out.putString("afterEngine",r.afterEngine());
        out.putString("decision",entry.decision().name());out.putBoolean("checkpointed",entry.checkpointed());out.putBoolean("finalized",entry.finalized());
        out.put("before",entry.before());out.put("after",entry.after());var owners=new CompoundTag();
        for(var item:entry.owners().entrySet()) {
            var value=new CompoundTag();value.putInt("version",item.getValue().version());value.put("capsule",item.getValue().capsule());
            value.putBoolean("published",entry.published().contains(item.getKey()));owners.put(item.getKey(),value);
        }
        out.put("owners",owners);return out;
    }
    private static boolean strictBoolean(CompoundTag value,String key) throws IOException {
        var tag=value.get(key);
        if(ByteTag.valueOf(false).equals(tag)) return false;
        if(ByteTag.valueOf(true).equals(tag)) return true;
        throw new IOException("Malformed Garage boolean: "+key);
    }
    private static void requireType(CompoundTag value,String key,int type) throws IOException {
        var tag=value.get(key);
        if(tag==null||tag.getId()!=type) throw new IOException("Malformed Garage field: "+key);
    }
    private static Entry fromPayload(CompoundTag value) throws IOException {
        if(value.getIntOr("schema",0)!=1||!value.keySet().equals(Set.of("schema","transaction","generation","operation","actor","vehicle","dimension","beforeEngine","afterEngine","decision","checkpointed","finalized","before","after","owners"))) throw new IOException("Unknown Garage transaction schema");
        requireType(value,"schema",Tag.TAG_INT); requireType(value,"generation",Tag.TAG_LONG);
        for(var key:List.of("transaction","operation","actor","vehicle","dimension","beforeEngine","afterEngine","decision")) requireType(value,key,Tag.TAG_STRING);
        boolean checkpointed=strictBoolean(value,"checkpointed"),finalized=strictBoolean(value,"finalized");
        var request=new Request(UUID.fromString(value.getStringOr("transaction","")),value.getLongOr("generation",-1),
                Operation.valueOf(value.getStringOr("operation","")),UUID.fromString(value.getStringOr("actor","")),
                UUID.fromString(value.getStringOr("vehicle","")),value.getStringOr("dimension",""),
                value.getStringOr("beforeEngine",""),value.getStringOr("afterEngine",""));
        var owners=new TreeMap<String,Owner>();var published=new HashSet<String>();
        var raw=value.getCompound("owners").orElseThrow(()->new IOException("Missing owner capsules"));
        for(var id:raw.keySet()) {
            var row=raw.getCompound(id).orElseThrow(()->new IOException("Malformed owner capsule"));
            if(!row.keySet().equals(Set.of("version","capsule","published"))) throw new IOException("Unknown Garage owner metadata");
            requireType(row,"version",Tag.TAG_INT);
            owners.put(id,new Owner(row.getIntOr("version",0),row.getCompound("capsule").orElseThrow()));
            if(strictBoolean(row,"published")) published.add(id);
        }
        return new Entry(request,Decision.valueOf(value.getStringOr("decision","")),checkpointed,
                finalized,value.getCompound("before").orElseThrow(),value.getCompound("after").orElseThrow(),owners,published);
    }
}
