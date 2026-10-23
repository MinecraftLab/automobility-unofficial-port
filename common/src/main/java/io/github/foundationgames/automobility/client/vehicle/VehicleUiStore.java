package io.github.foundationgames.automobility.client.vehicle;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Atomic, strict, externally guarded store. Legacy inputs are immutable and backed up once. */
public final class VehicleUiStore {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    private final Path path;
    private final List<Path> legacy;
    private final Map<Path,byte[]> legacyBytes=new LinkedHashMap<>();
    private final Map<Path,byte[]> legacyObserved=new LinkedHashMap<>();
    private boolean loaded,readable;
    private String lastRead;
    public VehicleUiStore(Path path,Path... legacy) { this.path=path;this.legacy=List.of(legacy); }
    public boolean readable() { return loaded && readable; }
    public VehicleUiSettings load(VehicleHudPreferences previous,boolean previousReadable) throws IOException {
        loaded=true;readable=false;
        lastRead=Files.exists(path)?Files.readString(path,StandardCharsets.UTF_8):null;
        if(lastRead==null) {
            if(!previousReadable) throw new IOException("Unreadable legacy UI settings must be preserved");
            // Persist recovery before Momentum's existing bootstrap migration can rewrite its config.
            Files.createDirectories(path.toAbsolutePath().getParent());
            for(var source:legacy) {
                byte[] observed=Files.exists(source)?Files.readAllBytes(source):null;
                legacyObserved.put(source,observed);
                Path backup=path.toAbsolutePath().resolveSibling(path.getFileName()+".legacy-"+source.getFileName()+".bak");
                if(Files.exists(backup)) legacyBytes.put(source,Files.readAllBytes(backup));
                else {
                    if(observed!=null) publishBackup(backup,observed);
                    legacyBytes.put(source,observed);
                }
            }
            readable=true;
            return VehicleUiSettings.migrate(previous);
        }
        try {
            JsonObject object;
            try(var reader=new JsonReader(new StringReader(lastRead))) {
                reader.setStrictness(Strictness.STRICT);
                object=readObject(reader);
                if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing content");
            }
            integer(object,"version");
            int schema=object.get("version").getAsInt();
            if(schema==2) {
                fields(object,"version","legacyLayout","basic","previous","panel","hints","hintMode","reservation");
                // Upgrade only the parsed draft. lastRead and on-disk bytes remain untouched until Save.
                object.addProperty("version",VehicleUiSettings.VERSION);object.addProperty("energyEnabled",true);
            } else if(schema==VehicleUiSettings.VERSION) {
                fields(object,"version","legacyLayout","basic","previous","panel","hints","hintMode","reservation","energyEnabled");
                bool(object,"energyEnabled");
            } else throw new IllegalArgumentException("Unsupported version");
            bool(object,"legacyLayout");string(object,"hintMode");
            for(String name:List.of("basic","previous")) {
                var o=object.getAsJsonObject(name);fields(o,"enabled","scalePercent","position","cameraDistanceBlocks");
                bool(o,"enabled");integer(o,"scalePercent");string(o,"position");integer(o,"cameraDistanceBlocks");
            }
            for(String name:List.of("panel","hints")) {
                var o=object.getAsJsonObject(name);fields(o,"anchor","x","y");string(o,"anchor");integer(o,"x");integer(o,"y");
            }
            var r=object.getAsJsonObject("reservation");fields(r,"enabled","x","y","width","height");bool(r,"enabled");
            for(String name:List.of("x","y","width","height")) integer(r,name);
            var result=GSON.fromJson(object,VehicleUiSettings.class);
            if(result.hintMode()==null || result.panel().anchor()==null || result.hints().anchor()==null
                    || result.basic().position()==null || result.previous().position()==null) throw new IllegalArgumentException("Unknown enum");
            readable=true;return result.sanitized();
        } catch(RuntimeException error) { throw new IOException("Invalid UI settings; preserving the file",error); }
    }
    /** Same-directory rename publishes complete bytes and refuses an existing destination. */
    static void publishBackup(Path backup,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(backup.getParent(),backup.getFileName()+".",".tmp");
        try {Files.write(temporary,bytes);Files.move(temporary,backup);}
        finally {Files.deleteIfExists(temporary);}
    }
    private static JsonObject readObject(JsonReader reader) throws IOException {
        if(reader.peek()!=JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException("Expected object");
        var object=new JsonObject();reader.beginObject();
        while(reader.hasNext()) {
            String name=reader.nextName();if(object.has(name)) throw new IllegalArgumentException("Duplicate field");
            if(reader.peek()==JsonToken.BEGIN_OBJECT) object.add(name,readObject(reader));
            else object.add(name,GSON.fromJson(reader,JsonElement.class));
        }
        reader.endObject();return object;
    }
    private static void fields(JsonObject object,String... names) {
        if(object==null || !object.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Unsupported fields");
    }
    private static void bool(JsonObject o,String k) { if(!o.get(k).isJsonPrimitive() || !o.getAsJsonPrimitive(k).isBoolean()) throw new IllegalArgumentException("Expected boolean"); }
    private static void string(JsonObject o,String k) { if(!o.get(k).isJsonPrimitive() || !o.getAsJsonPrimitive(k).isString()) throw new IllegalArgumentException("Expected string"); }
    private static void integer(JsonObject o,String k) { if(!o.get(k).isJsonPrimitive() || !o.getAsJsonPrimitive(k).isNumber()) throw new IllegalArgumentException("Expected integer");o.get(k).getAsBigDecimal().intValueExact(); }
    /** Raw pre-bootstrap snapshot; no foreign config is rewritten on restoration. */
    public String legacyInput(String name) throws IOException {
        for(var entry:legacyBytes.entrySet()) if(entry.getKey().getFileName().toString().equals(name))
            return entry.getValue()==null?null:new String(entry.getValue(),StandardCharsets.UTF_8);
        Path backup=path.toAbsolutePath().resolveSibling(path.getFileName()+".legacy-"+name+".bak");
        return Files.exists(backup)?Files.readString(backup,StandardCharsets.UTF_8):null;
    }
    public void save(VehicleUiSettings value) throws IOException {
        if(!readable()) throw new IOException("Unreadable UI settings must be preserved");
        String current;
        try {current=Files.exists(path)?Files.readString(path,StandardCharsets.UTF_8):null;}
        catch(IOException error) {readable=false;throw error;}
        if(!Objects.equals(current,lastRead)) {readable=false;throw new IOException("UI settings changed externally");}
        if(lastRead==null) for(var entry:legacyBytes.entrySet()) {
            var source=entry.getKey();byte[] now=Files.exists(source)?Files.readAllBytes(source):null;
            if(!source.getFileName().toString().equals("momentum.json") && !Arrays.equals(legacyObserved.get(source),now)) {readable=false;throw new IOException("Legacy settings changed before migration");}
        }
        Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());
        if(lastRead==null) for(var entry:legacyBytes.entrySet()) if(entry.getValue()!=null) {
            Path backup=target.resolveSibling(target.getFileName()+".legacy-"+entry.getKey().getFileName()+".bak");
            if(Files.exists(backup)) {if(!Arrays.equals(Files.readAllBytes(backup),entry.getValue())) throw new IOException("Preserve different legacy backup");}
            else publishBackup(backup,entry.getValue());
        }
        String content=GSON.toJson(value.sanitized())+"\n";
        Path temporary=Files.createTempFile(target.getParent(),target.getFileName()+".",".tmp");
        try {
            Files.writeString(temporary,content,StandardCharsets.UTF_8);
            try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            lastRead=content;legacyBytes.clear();
        } finally {Files.deleteIfExists(temporary);}
    }
}
