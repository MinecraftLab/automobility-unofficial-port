package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.*;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Entry;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Owner;
import io.github.foundationgames.automobility.entity.GarageCarrierAdmission.Carrier;
import io.github.foundationgames.automobility.mixin.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-owned unchanged-car transfers. A local owner lease never releases the durable Core fence. */
public final class GarageTransferRuntime implements GarageTransferAccess.Authority {
    private static final GarageTransferRuntime INSTANCE=new GarageTransferRuntime();
    private static final Map<MinecraftServer,State> STATES=new IdentityHashMap<>();
    private static Set<String> loadedOwners=Set.of();
    private static final ThreadLocal<Deque<Scope>> SCOPES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<MinecraftServer,Set<UUID>> PLAYER_FENCES=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean actorPending(MinecraftServer server,UUID actor){return PLAYER_FENCES.getOrDefault(server,Set.of()).contains(actor);}
    private static void refreshPlayerFences(State state){
        var actors=new HashSet<UUID>();
        for(var entry:state.latest.values())if(!entry.finalized()&&GarageInventoryExchange.present(entry.before()))actors.add(entry.request().actor());
        PLAYER_FENCES.put(state.server,Set.copyOf(actors));
    }
    private record AwaitingInventory(ServerPlayer player,long deadlineNanos,int deadlineTick){}
    private static final Map<ServerPlayer,AwaitingInventory> AWAITING_INVENTORY=new IdentityHashMap<>();
    private static boolean installed;
    private static final Map<String,GarageTransferParticipant> QUALIFIED=new TreeMap<>();
    private GarageTransferRuntime(){}
    private static final class State {
        final MinecraftServer server;final GarageTransferJournal journal;final GarageCarrierAdmission admission=new GarageCarrierAdmission();
        final GarageRawQuarantine quarantine;final Path quarantineDirectory;
        final Map<UUID,Entry> latest=new HashMap<>();final Set<UUID> ambiguous=new HashSet<>();
        final Set<Object> storages=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean failure;String failureDetail="";long nextRecovery;boolean inTransfer;
        State(MinecraftServer server)throws IOException {
            this.server=server;var data=server.getWorldPath(LevelResource.ROOT).resolve("data");
            journal=new GarageTransferJournal(data.resolve("automobility-garage-transactions"));
            quarantineDirectory=data.resolve("automobility-garage-quarantine");quarantine=new GarageRawQuarantine(quarantineDirectory);
            for(var entry:journal.load()){admission.observe(entry);latest.put(entry.request().vehicle(),entry);}
            if(Files.exists(quarantineDirectory))try(var files=Files.list(quarantineDirectory)){
                var records=files.limit(GarageTransferJournal.MAX_RECORDS*4L+1).toList();long bytes=0;int count=0;
                if(records.size()>GarageTransferJournal.MAX_RECORDS*4)throw new IOException("Garage quarantine file capacity exceeded");
                for(var path:records){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular Garage quarantine input");bytes=Math.addExact(bytes,Files.size(path));
                    if(!path.getFileName().toString().endsWith(".nbt"))continue;
                    if(++count>GarageTransferJournal.MAX_RECORDS)throw new IOException("Garage quarantine record capacity exceeded");
                    var retained=GarageRawQuarantine.read(path);var id=UUID.fromString(retained.getStringOr("vehicle",""));
                    if(GarageRetainedInput.ambiguous(retained.getCompound("raw").orElseThrow(),id,retained.getStringOr("dimension","")))ambiguous.add(id);
                }
                if(bytes>GarageTransferJournal.MAX_TOTAL_BYTES)throw new IOException("Garage quarantine byte capacity exceeded including pending files");
            }
        }
        void observe(Entry entry){try{admission.observe(entry);latest.put(entry.request().vehicle(),entry);refreshPlayerFences(this);}catch(IOException error){failure=true;failureDetail=error.toString();throw new IllegalStateException(error);}}
    }
    private static final class Scope implements AutoCloseable {
        final AutomobileEntity car;final Request request;final CompoundTag expected;final boolean quiet,mutation,probe;
        Scope(AutomobileEntity car,Request request,CompoundTag expected,boolean quiet,boolean mutation,boolean probe){this.car=car;this.request=request;this.expected=expected==null?null:expected.copy();this.quiet=quiet;this.mutation=mutation;this.probe=probe;SCOPES.get().push(this);}
        public void close(){if(SCOPES.get().peek()!=this)throw new IllegalStateException("Garage scope order");SCOPES.get().pop();if(SCOPES.get().isEmpty())SCOPES.remove();}
    }
    private static Scope scope(){return SCOPES.get().peek();}
    public static void initialize(Set<String> knownLoadedOwners){
        if(installed)return;loadedOwners=Set.copyOf(knownLoadedOwners);GarageTransferAccess.install(INSTANCE);GarageEngineTransferAccess.install(GarageTransferRuntime::enginePermit);installed=true;
    }
    /** Called only by the exact-artifact bootstrap before SERVER_STARTING. */
    public static boolean registerQualified(GarageTransferParticipant owner){
        if(!installed||!STATES.isEmpty()||owner==null||!loadedOwners.contains(owner.id())||owner.apiVersion()!=1||owner.capsuleVersion()!=1||QUALIFIED.containsKey(owner.id()))return false;
        if(!GarageTransferAccess.register(owner))return false;QUALIFIED.put(owner.id(),owner);return true;
    }
    public static void start(MinecraftServer server){
        if(!installed)throw new IllegalStateException("Garage authority not installed");
        GarageTransferAccess.sealRegistration();
        try{var state=new State(server);STATES.put(server,state);refreshPlayerFences(state);}catch(IOException error){throw new IllegalStateException("Garage journal could not be admitted before world load; original data retained",error);}
    }
    public static void stop(MinecraftServer server){AWAITING_INVENTORY.keySet().removeIf(p->p.level().getServer()==server);PLAYER_FENCES.remove(server);var state=STATES.remove(server);if(state!=null)for(var storage:state.storages)GarageFailedOrigins.closed(storage);}
    private static State state(MinecraftServer server){return STATES.get(server);}
    private static String dimension(ServerLevel level){return level.dimension().identifier().toString();}
    private static void serverThread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Garage operation must use server thread");}
    private static boolean special(AutomobileEntity car,boolean quiet,boolean mutation){
        var scope=scope();return scope!=null&&scope.car==car&&car.level() instanceof ServerLevel level&&level.getServer().isSameThread()
                &&(!quiet||scope.quiet)&&(!mutation||scope.mutation);
    }
    public static AutoCloseable probe(AutomobileEntity car){
        if(!(car.level() instanceof ServerLevel level))return ()->{};serverThread(level.getServer());
        if(level.getEntity(car.getUUID())==car)throw new IllegalStateException("World entity cannot be a placement probe");
        return new Scope(car,null,null,true,false,true);
    }
    @Override public boolean blocked(MinecraftServer server,UUID vehicle){var state=state(server);return state==null||state.failure||state.ambiguous.contains(vehicle)||state.admission.blocked(vehicle);}
    @Override public boolean blocked(AutomobileEntity car){
        if(!(car.level() instanceof ServerLevel level))return false;
        var local=scope();if(local!=null&&local.car==car&&local.probe)return true;
        var state=state(level.getServer());return state==null||state.failure||state.ambiguous.contains(car.getUUID())
                ||state.admission.blocked(car,car.getUUID(),dimension(level),car.garageCarrierMarker());
    }
    @Override public boolean quietLoading(MinecraftServer server,UUID vehicle){var scope=scope();return scope!=null&&scope.quiet&&scope.car.level() instanceof ServerLevel level&&level.getServer()==server&&server.isSameThread()
            &&(scope.request==null?scope.car.getUUID().equals(vehicle):scope.request.vehicle().equals(vehicle));}
    @Override public boolean quietLoading(AutomobileEntity car){return special(car,true,false);}
    public static boolean mayRemove(AutomobileEntity car,Entity.RemovalReason reason){return !reason.shouldDestroy()||!GarageTransferAccess.blocked(car)||special(car,false,true);}
    public static void unload(AutomobileEntity car){if(car.level() instanceof ServerLevel level){var state=state(level.getServer());if(state!=null)state.admission.unload(car,car.getUUID());}}
    public static void retentionFailed(MinecraftServer server,Object storage,Throwable error){var state=state(server);if(state!=null){state.storages.add(storage);state.failure=true;state.failureDetail=error.toString();}LoggerFactory.getLogger("AutomobilityGarage").error("Garage recovery stop: affected entity chunk NOT SAVED; original disk input retained",error);server.halt(false);}
    static boolean rejectIncoming(ServerLevel level,UUID id,CompoundTag raw,GarageWorldAdmission.Origin origin){
        var state=state(level.getServer());if(state==null)throw new GarageWorldAdmission.RetentionFailure(new IOException("Garage admission not initialized"));
        var marker=GarageCarrierMarker.read(raw).orElse(null);
        boolean missingOwner=false;
        if(state.admission.tracked(id))try{requiredOwnersReady(level,raw,state.latest.get(id));}catch(IOException unavailable){missingOwner=true;}
        if(!missingOwner&&!state.failure&&!state.ambiguous.contains(id)&&state.admission.allowsRaw(id,dimension(level),marker))return false;
        retain(state,level,id,raw,origin,"PENDING_OR_STALE_CARRIER",missingOwner||state.failure||state.ambiguous.contains(id)||!dimension(level).equals(state.latest.containsKey(id)?state.latest.get(id).request().dimension():"")||ambiguous(state,id,marker));return true;
    }
    private static boolean ambiguous(State state,UUID id,GarageCarrierMarker marker){
        var latest=state.latest.get(id);if(marker==null||latest==null)return true;
        var before=GarageCarrierMarker.read(latest.before()).orElse(null);var after=GarageCarrierMarker.read(latest.after()).orElse(null);
        if(before==null||after==null)return true;
        if(marker.generation()<before.generation())return false;
        return !marker.equals(before)&&!marker.equals(after);
    }
    private static void retain(State state,ServerLevel level,UUID id,CompoundTag raw,GarageWorldAdmission.Origin origin,String reason,boolean ambiguous){
        var retained=new CompoundTag();retained.putInt("runtimeSchema",1);retained.put("processedEntity",raw.copy());retained.putBoolean("ambiguous",ambiguous);
        if(origin!=null){state.storages.add(origin.storage);retained.putInt("sourceChunkX",origin.chunk.x());retained.putInt("sourceChunkZ",origin.chunk.z());retained.put("originalDiskChunk",origin.original.copy());}
        else retained.putString("source","DIRECT_FACTORY");
        var current=state.latest.get(id);if(current!=null){retained.putString("observedTransaction",current.request().transaction().toString());retained.putLong("observedGeneration",current.request().generation());}
        try{state.quarantine.retain(id,dimension(level),retained,reason);if(ambiguous)state.ambiguous.add(id);}
        catch(IOException error){throw new GarageWorldAdmission.RetentionFailure(error);}
    }
    public static void beforeLoad(AutomobileEntity car,ValueInput input){
        if(!(car.level() instanceof ServerLevel level))return;serverThread(level.getServer());
        var raw=GarageWorldAdmission.raw(input);var state=state(level.getServer());
        if(raw==null){
            var id=input.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            if(input.read(GarageCarrierMarker.KEY,com.mojang.serialization.Codec.PASSTHROUGH).isPresent()||state==null||(id!=null&&(state.admission.tracked(id)||state.ambiguous.contains(id))))
                throw new GarageWorldAdmission.RetentionFailure(new IOException("Protected load requires original raw input"));return;
        }
        var id=GarageWorldAdmission.uuid(raw);if(id==null){if(raw.contains(GarageCarrierMarker.KEY))throw new GarageWorldAdmission.RetentionFailure(new IOException("Protected carrier UUID is invalid"));return;}var marker=GarageCarrierMarker.read(raw).orElse(null);var local=scope();
        if(local!=null&&local.car==car&&local.quiet&&local.request!=null){
            if(!id.equals(local.request.vehicle())||local.expected==null||!local.expected.equals(raw)||marker==null)throw new IllegalStateException("Garage quiet load identity/raw changed");
            car.setUUID(id);car.garageCarrierMarker(marker);return;
        }
        boolean missingOwner=false;
        if(state!=null&&state.admission.tracked(id))try{requiredOwnersReady(level,raw,state.latest.get(id));}catch(IOException unavailable){missingOwner=true;}
        if(missingOwner||state==null||state.failure||state.ambiguous.contains(id)||!state.admission.admit(car,id,dimension(level),marker)){
            if(state==null)throw new GarageWorldAdmission.RetentionFailure(new IOException("Garage admission not initialized"));
            retain(state,level,id,raw,GarageWorldAdmission.current(),"ENTITY_ADMISSION_REFUSED",true);
            throw new IllegalStateException("Garage carrier is fenced or a duplicate; complete raw retained");
        }
        car.setUUID(id);car.garageCarrierMarker(marker);GarageWorldAdmission.bound(car);
    }
    public static void loadFailed(AutomobileEntity car,ValueInput input,Throwable error){
        if(!(car.level() instanceof ServerLevel level)||special(car,true,false))return;
        var state=state(level.getServer());if(state==null||!state.admission.tracked(car.getUUID()))return;
        var raw=GarageWorldAdmission.raw(input);if(raw!=null)retain(state,level,car.getUUID(),raw,GarageWorldAdmission.current(),"PROTECTED_LOAD_FAILED",true);unload(car);
    }
    private static Set<String> required(CompoundTag raw,Entry journal){
        var ids=new TreeSet<>(loadedOwners);for(var key:raw.keySet()){if(key.startsWith("AutoFuel")||key.equals("AutoTrip"))ids.add("pumps_and_pistons");if(key.startsWith("powerworks:"))ids.add("powerworks");}
        if(journal!=null)ids.addAll(journal.owners().keySet());return ids;
    }
    private static void requiredOwnersReady(ServerLevel level,CompoundTag raw,Entry entry)throws IOException{
        if(entry!=null&&entry.request().operation()==Operation.ENGINE_SWAP){var frozen=GarageEnginePlan.read(level.registryAccess(),entry);var selected=engineOwners(raw,entry,frozen.input().actualIncomingSource());if(!selected.keySet().equals(frozen.owners().keySet()))throw new IOException("Engine admission owner set changed");return;}
        owners(raw,entry);
    }
    private static Map<String,GarageTransferParticipant> owners(CompoundTag raw,Entry journal)throws IOException {
        var all=QUALIFIED;
        var selected=new TreeMap<String,GarageTransferParticipant>();for(var id:required(raw,journal)){var owner=all.get(id);if(owner==null||owner.apiVersion()!=1||owner.capsuleVersion()!=1)throw new IOException("Qualified Garage owner unavailable: "+id);selected.put(id,owner);}return selected;
    }
    public static boolean available(){return installed&&QUALIFIED.keySet().containsAll(loadedOwners);}
    public static boolean canStore(GarageService.Components parts){return available()&&parts!=null&&parts.complete();}
    public static boolean canDeploy(ServerLevel level,GarageStorage.Entry receipt){
        if(receipt==null||!available())return false;var state=state(level.getServer());if(state==null||state.failure||state.ambiguous.contains(receipt.id())||state.admission.pending(receipt.id()))return false;
        if(!GarageReceiptLineage.accepts(state.latest.get(receipt.id()),receipt))return false;
        try{owners(receipt.entity(),state.latest.get(receipt.id()));return true;}catch(IOException error){return false;}
    }
    public static boolean canLegacyStoredTune(ServerLevel level,GarageStorage.Entry receipt){
        var state=state(level.getServer());return receipt!=null&&state!=null&&!state.failure&&!state.ambiguous.contains(receipt.id())
                &&!state.admission.tracked(receipt.id())&&!receipt.entity().contains(GarageCarrierMarker.KEY)&&GarageStorage.canRestore(receipt);
    }
    public static boolean canStoredWheelTune(ServerLevel level,GarageStorage.Entry receipt){
        var state=state(level.getServer());var previous=state==null||receipt==null?null:state.latest.get(receipt.id());
        return previous!=null&&previous.finalized()&&canDeploy(level,receipt)
                &&GarageReceiptLineage.accepts(previous,receipt)&&!actorPending(level.getServer(),receipt.owner());
    }
    /** The stored raw is changed only by a new journal generation; no ordinary entity preview is loaded. */
    public static GarageService.Status tuneStoredWheels(ServerPlayer actor,GarageStorage.Entry stored,
                                                       GarageService.Batch batch,GarageBatchPlan inventory)throws IOException {
        var level=actor.level();serverThread(level.getServer());var state=state(level.getServer());
        if(state==null||state.inTransfer||!canStoredWheelTune(level,stored))return GarageService.Status.INTEGRATION;
        if(!stored.owner().equals(actor.getUUID())||!dimension(level).equals(stored.dimension())
                ||stored.state()!=GarageStorage.State.PARKED||GarageStorage.uuidPresent(level,stored.id()))return GarageService.Status.PROTECTED;
        if(!stored.equals(GarageStorage.get(level,actor.getUUID(),stored.id())))return GarageService.Status.STALE;
        if(!GaragePlayerExchange.cursorEmpty(actor)||!inventory.ready()||!inventory.matches(actor.getInventory()))return GarageService.Status.STALE;
        var original=io.github.foundationgames.automobility.screen.GarageStoredMetadata.read(stored.entity());
        var current=original==null?null:original.resolve(level.registryAccess());var replacement=batch.replacement();
        if(batch.status()!=GarageService.Status.READY||current==null||replacement==null
                ||!current.frame().equals(replacement.frame())||!current.engine().equals(replacement.engine()))return GarageService.Status.INTEGRATION;
        if(current.wheel().equals(replacement.wheel()))return GarageService.Status.NO_CHANGES;
        if(!storageReady(level,chunk(stored.entity())))return GarageService.Status.UNLOADED;
        var previous=state.latest.get(stored.id());var generation=previous==null?1:Math.addExact(previous.request().generation(),1);
        var beforeMarker=GarageCarrierMarker.readStored(stored.entity()).orElseThrow(()->new IOException("Invalid stored marker"));
        if(!state.admission.resolvedMatches(stored.id(),dimension(level),beforeMarker,false))return GarageService.Status.RECOVERY_REQUIRED;
        var tx=UUID.randomUUID();var engine=stored.entity().getStringOr("engine","");
        var request=new Request(tx,generation,Operation.STORED_TUNE,actor.getUUID(),stored.id(),dimension(level),engine,engine);
        var selected=owners(stored.entity(),previous);var capsules=inspect(level,request,stored.entity(),selected);
        var afterRaw=stored.entity().copy();afterRaw.putString("wheels",replacement.wheel().unwrapKey().orElseThrow().identifier().toString());
        var afterMarker=new GarageCarrierMarker(generation,tx,GarageCarrierMarker.Kind.STORED);afterMarker.write(afterRaw);
        var afterReceipt=new GarageStorage.Entry(stored.id(),stored.owner(),stored.name(),stored.dimension(),stored.x(),stored.z(),GarageStorage.State.PARKED,afterRaw);
        var before=image(stored.id(),dimension(level),beforeMarker,stored.entity(),stored);
        var after=image(stored.id(),dimension(level),afterMarker,afterRaw,afterReceipt);
        GarageInventoryExchange.attach(request,before,after,GaragePlayerExchange.snapshot(actor,inventory.before()),
                GaragePlayerExchange.snapshot(actor,inventory.after()),GaragePlayerExchange.marker(actor));
        var intent=new Entry(request,Decision.UNDECIDED,false,false,before,after,capsules,Set.of());
        var result=execute(state,level,previous,intent,null,selected,false);
        return result.status()==GarageService.Status.READY?GarageService.Status.APPLIED:result.status();
    }
    private static void disconnectPendingActor(State state,Request request){
        if(!actorPending(state.server,request.actor()))return;
        var actor=state.server.getPlayerList().getPlayer(request.actor());
        if(actor!=null)actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("automobility.garage.inventory_recovery"));
    }
    /** Defer only the inventory listener; vanilla finishes login and installs ordinary chunk tickets. */
    public static boolean admitPlayerInventory(ServerPlayer actor){
        var server=actor.level().getServer();if(!actorPending(server,actor.getUUID()))return true;
        AWAITING_INVENTORY.putIfAbsent(actor,new AwaitingInventory(actor,System.nanoTime()+10_000_000_000L,server.getTickCount()+200));
        return false;
    }
    private static boolean mayAwaitChunkAdmission(State state,ServerPlayer actor){
        boolean waiting=false;
        for(var entry:state.latest.values()){
            if(entry.finalized()||!entry.request().actor().equals(actor.getUUID()))continue;
            if(entry.request().operation()!=Operation.ENGINE_SWAP||!entry.before().getBooleanOr("active",false)
                    ||!dimension(actor.level()).equals(entry.request().dimension())||state.ambiguous.contains(entry.request().vehicle()))return false;
            try{
                var frozen=GarageEnginePlan.read(actor.registryAccess(),entry);var owners=engineOwners(entity(entry.before()),entry,frozen.input().actualIncomingSource());
                if(!owners.keySet().equals(frozen.owners().keySet())||GaragePlayerExchange.state(actor,entry)==GarageInventoryExchange.State.UNKNOWN)return false;
                var source=chunk(entity(entry.before()));var target=chunk(entity(entry.after()));
                if(storageReady(actor.level(),source)&&storageReady(actor.level(),target))return false;
                waiting=true;
            }catch(IOException|RuntimeException error){return false;}
        }return waiting&&!state.failure;
    }
    private static void resumeInventoryAdmissions(MinecraftServer server){
        var state=state(server);if(state==null)return;
        for(var pending:List.copyOf(AWAITING_INVENTORY.values())){
            var actor=pending.player();if(actor.level().getServer()!=server)continue;
            if(server.getPlayerList().getPlayer(actor.getUUID())!=actor||actor.isRemoved()){AWAITING_INVENTORY.remove(actor);continue;}
            // A chunk can finish between the periodic recovery ticks. Try its now-ready
            // recovery before classifying the remaining fence as a hard refusal.
            if(actorPending(server,actor.getUUID())&&!state.failure)recoverPending(server,actor.getUUID());
            if(!actorPending(server,actor.getUUID())){
                AWAITING_INVENTORY.remove(actor);actor.initInventoryMenu();continue;
            }
            if(System.nanoTime()>=pending.deadlineNanos()||server.getTickCount()>=pending.deadlineTick()||!mayAwaitChunkAdmission(state,actor)){
                AWAITING_INVENTORY.remove(actor);actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("automobility.garage.inventory_recovery"));
            }
        }
    }
    private static CompoundTag image(UUID id,String dimension,GarageCarrierMarker marker,CompoundTag raw,GarageStorage.Entry receipt){
        var image=new Carrier(id,dimension,marker.kind()==GarageCarrierMarker.Kind.ACTIVE,marker).image();image.put("entity",raw.copy());if(receipt!=null)image.put("receipt",GarageStorage.encodeEntry(receipt));return image;
    }
    private static GarageStorage.Entry receipt(CompoundTag image)throws IOException {return image.contains("receipt")?GarageStorage.decodeEntry(image.getCompound("receipt").orElseThrow()):null;}
    private static GarageCarrierMarker marker(CompoundTag image)throws IOException {return GarageCarrierMarker.read(image).orElseThrow(()->new IOException("Malformed Core marker"));}
    private static CompoundTag entity(CompoundTag image)throws IOException {return image.getCompound("entity").orElseThrow(()->new IOException("Missing Core raw entity"));}
    private static final class Lease implements Fence {
        final State state;final Request request;final AutomobileEntity car;final ServerLevel level;final GarageCarrierMarker marker;final CompoundTag expectedRaw;
        Lease(State state,Request request,AutomobileEntity car,ServerLevel level,GarageCarrierMarker marker,CompoundTag expectedRaw){this.state=state;this.request=request;this.car=car;this.level=level;this.marker=marker;this.expectedRaw=expectedRaw.copy();}
        public UUID transactionId(){return request.transaction();}public long generation(){return request.generation();}
        public boolean heldFor(AutomobileEntity actual,UUID transaction,long generation){return actual==car&&actual.level()==level&&level.getServer()==state.server&&state.server.isSameThread()
                &&actual.garageCarrierMarker().equals(marker)&&actual.getUUID().equals(request.vehicle())&&dimension(level).equals(request.dimension())&&request.transaction().equals(transaction)&&request.generation()==generation&&state.admission.pending(request.vehicle());}
        public boolean intentDurableFor(AutomobileEntity actual,UUID transaction,long generation){var entry=state.latest.get(request.vehicle());return heldFor(actual,transaction,generation)&&entry!=null&&entry.request().equals(request);}
        public Decision decisionFor(AutomobileEntity actual,UUID transaction,long generation){var entry=state.latest.get(request.vehicle());return intentDurableFor(actual,transaction,generation)?entry.decision():Decision.UNDECIDED;}
    }
    private static Map<String,Owner> inspect(ServerLevel level,Request request,CompoundTag raw,Map<String,GarageTransferParticipant> owners)throws IOException {
        var result=new TreeMap<String,Owner>();for(var row:owners.entrySet()){
            var untouched=raw.copy();var inspection=row.getValue().inspectRaw(level,request,untouched);
            if(!untouched.equals(raw)||inspection.verdict()!=Verdict.READY||inspection.capsule()==null)throw new IOException("Garage owner raw refused: "+row.getKey()+" "+inspection.verdict());
            result.put(row.getKey(),new Owner(row.getValue().capsuleVersion(),inspection.capsule()));
        }return result;
    }
    private static AutomobileEntity materialize(ServerLevel level,Request request,CompoundTag raw)throws IOException {
        if(raw.contains("Passengers")&&(!(raw.get("Passengers") instanceof ListTag list)||!list.isEmpty()))throw new IOException("Stored passengers require explicit recovery");
        var car=new AutomobileEntity(level);
        try(var quiet=new Scope(car,request,raw,true,false,false)){
            car.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),raw.copy()));
        }catch(RuntimeException error){throw new IOException("Garage quiet load refused",error);}
        if(!car.getUUID().equals(request.vehicle())||car.isVehicle()||car.isPassenger()||!car.getVehicleAccess().isOwner(request.actor()))throw new IOException("Garage carrier identity/access changed");
        return car;
    }
    private static Map<String,Prepared> prepare(Lease lease,Map<String,GarageTransferParticipant> owners,Map<String,Owner> expected,boolean recovery,Decision decision)throws IOException {
        var prepared=new TreeMap<String,Prepared>();try{
            for(var row:owners.entrySet()){
                var capsule=expected.get(row.getKey());if(capsule==null||capsule.version()!=row.getValue().capsuleVersion())throw new IOException("Garage capsule owner/version changed");
                var result=recovery?row.getValue().recover(lease.request,lease.car,lease,capsule.capsule(),decision):row.getValue().prepare(lease.request,lease.car,lease,capsule.capsule());
                if(result.verdict()!=Verdict.READY||result.prepared()==null||!capsule.capsule().equals(result.prepared().capsule()))throw new IOException("Garage owner prepare refused: "+row.getKey()+" "+result.verdict());
                prepared.put(row.getKey(),result.prepared());
            }return prepared;
        }catch(IOException|RuntimeException error){for(var owner:prepared.values())try{owner.closeLocal();}catch(Exception ignored){}throw error;}
    }
    /** No player inventory mutation is part of STORE/DEPLOY. Reject another open cargo menu before fencing. */
    private static boolean cargoBusy(ServerLevel level,AutomobileEntity car){var cargo=car.underlyingContainer();if(cargo==null)return false;
        for(var player:level.getServer().getPlayerList().getPlayers())if(player.containerMenu!=player.inventoryMenu)
            for(var slot:player.containerMenu.slots)if(slot.container==car||slot.container==cargo)return true;return false;
    }
    public static GarageService.Status store(ServerPlayer actor,AutomobileEntity car)throws IOException {
        var level=actor.level();var state=state(level.getServer());serverThread(level.getServer());
        if(actorPending(level.getServer(),actor.getUUID()))return GarageService.Status.RECOVERY_REQUIRED;
        if(state==null||state.failure||state.inTransfer||!canStore(car.garageComponents()))return GarageService.Status.INTEGRATION;
        if(GarageTransferAccess.blocked(car))return GarageService.Status.RECOVERY_REQUIRED;
        if(car.level()!=level||car.isRemoved()||!car.isAlive()||level.getEntity(car.getUUID())!=car||!GarageReturnPlacement.finite(car.position())||!car.getVehicleAccess().isOwner(actor.getUUID())||cargoBusy(level,car))return GarageService.Status.PROTECTED;
        var operating=GarageService.operatingStatus(car);if(operating!=GarageService.Status.READY)return operating;
        var snapshot=GarageStorage.snapshot(car,actor.getUUID());var previous=state.latest.get(car.getUUID());
        if(!state.admission.resolvedMatches(car.getUUID(),dimension(level),car.garageCarrierMarker(),true))return GarageService.Status.RECOVERY_REQUIRED;
        if(!storageReady(level,chunk(snapshot.entity())))return GarageService.Status.UNLOADED;
        var generation=previous==null?1:Math.addExact(previous.request().generation(),1);var tx=UUID.randomUUID();var engine=snapshot.entity().getStringOr("engine","");
        var request=new Request(tx,generation,Operation.STORE,actor.getUUID(),car.getUUID(),dimension(level),engine,engine);
        var selected=owners(snapshot.entity(),previous);var capsules=inspect(level,request,snapshot.entity(),selected);
        var afterRaw=snapshot.entity().copy();var afterMarker=new GarageCarrierMarker(generation,tx,GarageCarrierMarker.Kind.STORED);afterMarker.write(afterRaw);
        var parked=new GarageStorage.Entry(snapshot.id(),snapshot.owner(),snapshot.name(),snapshot.dimension(),snapshot.x(),snapshot.z(),GarageStorage.State.PARKED,afterRaw);
        var priorReceipt=GarageStorage.get(level,actor.getUUID(),car.getUUID());
        var intent=new Entry(request,Decision.UNDECIDED,false,false,image(car.getUUID(),dimension(level),car.garageCarrierMarker(),snapshot.entity(),priorReceipt),image(car.getUUID(),dimension(level),afterMarker,afterRaw,parked),capsules,Set.of());
        return execute(state,level,previous,intent,car,selected,false).status();
    }
    public record Result(GarageService.Status status,AutomobileEntity car){}
    public static Result deploy(ServerPlayer actor,GarageStorage.Entry receipt,Vec3 position,float yaw)throws IOException {
        var level=actor.level();var state=state(level.getServer());serverThread(level.getServer());
        if(actorPending(level.getServer(),actor.getUUID()))return new Result(GarageService.Status.RECOVERY_REQUIRED,null);
        if(position==null||!GarageReturnPlacement.finite(position)||!Float.isFinite(yaw)||state==null||state.failure||state.inTransfer||!canDeploy(level,receipt))return new Result(GarageService.Status.INTEGRATION,null);
        if(!receipt.owner().equals(actor.getUUID())||receipt.state()!=GarageStorage.State.PARKED||!dimension(level).equals(receipt.dimension()))return new Result(GarageService.Status.PROTECTED,null);
        if(GarageStorage.uuidPresent(level,receipt.id()))return new Result(GarageService.Status.DUPLICATE,null);
        if(!GarageStorage.originalChunkLoaded(level,receipt))return new Result(GarageService.Status.UNLOADED,null);
        var previous=state.latest.get(receipt.id());var beforeMarker=GarageCarrierMarker.readStored(receipt.entity()).orElseThrow(()->new IOException("Stored marker invalid"));
        // Legacy receipts are admitted only when no prior transaction exists; their original raw stays retained.
        if(beforeMarker.kind()!=GarageCarrierMarker.Kind.STORED||!state.admission.resolvedMatches(receipt.id(),dimension(level),beforeMarker,false)
                ||!Objects.equals(receipt,GarageStorage.get(level,actor.getUUID(),receipt.id())))return new Result(GarageService.Status.RECOVERY_REQUIRED,null);
        var targetChunk=new ChunkPos(((int)Math.floor(position.x))>>4,((int)Math.floor(position.z))>>4);
        if(!storageReady(level,chunk(receipt.entity()))||!storageReady(level,targetChunk))return new Result(GarageService.Status.UNLOADED,null);
        var generation=previous==null?1:Math.addExact(previous.request().generation(),1);var tx=UUID.randomUUID();var engine=receipt.entity().getStringOr("engine","");
        var request=new Request(tx,generation,Operation.DEPLOY,actor.getUUID(),receipt.id(),dimension(level),engine,engine);
        var selected=owners(receipt.entity(),previous);var capsules=inspect(level,request,receipt.entity(),selected);
        var afterRaw=receipt.entity().copy();var afterMarker=new GarageCarrierMarker(generation,tx,GarageCarrierMarker.Kind.ACTIVE);afterMarker.write(afterRaw);
        afterRaw.put("Pos",Vec3.CODEC.encodeStart(NbtOps.INSTANCE,position).getOrThrow());var rotation=new ListTag();rotation.add(FloatTag.valueOf(yaw));rotation.add(FloatTag.valueOf(0));afterRaw.put("Rotation",rotation);
        var deployed=new GarageStorage.Entry(receipt.id(),receipt.owner(),receipt.name(),receipt.dimension(),receipt.x(),receipt.z(),GarageStorage.State.DEPLOYED,afterRaw);
        var intent=new Entry(request,Decision.UNDECIDED,false,false,image(receipt.id(),dimension(level),beforeMarker,receipt.entity(),receipt),image(receipt.id(),dimension(level),afterMarker,afterRaw,deployed),capsules,Set.of());
        return execute(state,level,previous,intent,null,selected,false);
    }
    private static Result execute(State state,ServerLevel level,Entry previous,Entry intent,AutomobileEntity carrier,Map<String,GarageTransferParticipant> owners,boolean recovery)throws IOException {
        if(recovery&&intent.decision()==Decision.UNDECIDED){var rollback=intent.decide(Decision.ROLLBACK);state.journal.write(intent,rollback);state.observe(rollback);intent=rollback;}
        var request=intent.request();if(!state.admission.hold(request.vehicle(),request.transaction()))return new Result(GarageService.Status.RECOVERY_REQUIRED,null);
        state.inTransfer=true;Map<String,Prepared> prepared=Map.of();
        try{
            if(carrier!=null)io.github.foundationgames.automobility.api.vehicle.VehicleQuiescenceProviders.garageHold(carrier,request.transaction(),request.generation());
            CompoundTag selectedImage;
            if(carrier!=null){
                selectedImage=request.operation()==Operation.STORE?intent.before():intent.after();
                if(recovery&&request.operation()==Operation.DEPLOY&&intent.decision()!=Decision.COMMIT)throw new IOException("Unexpected live carrier during DEPLOY rollback");
                if(carrier.level()!=level||carrier.isRemoved()||!carrier.isAlive()||level.getEntity(request.vehicle())!=carrier
                        ||!carrier.garageCarrierMarker().equals(marker(selectedImage)))throw new IOException("Existing recovery carrier is not the exact selected journal image");
            }else{
                selectedImage=recovery?(intent.decision()==Decision.COMMIT?intent.after():intent.before()):intent.after();
                carrier=materialize(level,request,entity(selectedImage));
            }
            var lease=new Lease(state,request,carrier,level,marker(selectedImage),entity(selectedImage));prepared=prepare(lease,owners,intent.owners(),recovery,intent.decision());
            var work=new Work(state,level,intent,lease);
            var outcome=recovery?GarageTransferCoordinator.recover(state.journal.repository(),intent,work,prepared,state::observe):GarageTransferCoordinator.execute(state.journal.repository(),previous,intent,work,prepared,state::observe);
            var latest=state.latest.get(request.vehicle());
            if(outcome.retained()!=null&&outcome.retained().finalized()){
                state.admission.release(request.vehicle(),request.transaction());
                var resolved=outcome.retained().decision()==Decision.COMMIT?outcome.retained().after():outcome.retained().before();
                if(resolved.getBooleanOr("active",false)&&level.getEntity(request.vehicle()) instanceof AutomobileEntity active)
                    state.admission.admit(active,active.getUUID(),dimension(level),active.garageCarrierMarker());
            }else if(latest==null||!latest.request().equals(request)){state.admission.release(request.vehicle(),request.transaction());}
            if(outcome.status()==GarageTransferCoordinator.Status.RECOVERY_REQUIRED)disconnectPendingActor(state,request);
            return new Result(outcome.status()==GarageTransferCoordinator.Status.APPLIED?GarageService.Status.READY:outcome.status()==GarageTransferCoordinator.Status.ROLLED_BACK?GarageService.Status.STORAGE_ERROR:GarageService.Status.RECOVERY_REQUIRED,
                    level.getEntity(request.vehicle()) instanceof AutomobileEntity active?active:null);
        }catch(IOException|RuntimeException error){
            for(var owner:prepared.values())try{owner.closeLocal();}catch(Exception ignored){}
            var latest=state.latest.get(request.vehicle());if(latest==null||!latest.request().equals(request))state.admission.release(request.vehicle(),request.transaction());disconnectPendingActor(state,request);throw error;
        }finally{state.inTransfer=false;}
    }
    private static final class Work implements GarageTransferCoordinator.CoreWork {
        final State state;final ServerLevel level;final Entry entry;final Lease lease;
        Work(State state,ServerLevel level,Entry entry,Lease lease){this.state=state;this.level=level;this.entry=entry;this.lease=lease;}
        private boolean receiptIs(CompoundTag image){try{var expected=receipt(image);var actual=GarageStorage.get(level,entry.request().actor(),entry.request().vehicle());return Objects.equals(expected,actual);}catch(IOException error){return false;}}
        private boolean liveIs(CompoundTag image){
            var live=level.getEntity(entry.request().vehicle());if(!image.getBooleanOr("active",false))return live==null;
            try{return live==lease.car&&live instanceof AutomobileEntity car&&car.level()==level&&car.garageCarrierMarker().equals(marker(image))&&car.getVehicleAccess().isOwner(entry.request().actor());}catch(IOException error){return false;}
        }
        private boolean roundTrip(){
            try{var actual=GarageStorage.snapshot(lease.car,entry.request().actor()).entity();
                return persistentRaw(actual).equals(persistentRaw(lease.expectedRaw))&&ownerRaw(actual).equals(ownerRaw(lease.expectedRaw))
                        &&GarageOpaqueState.presentExactly(actual,lease.car.garageOpaqueSnapshot());
            }catch(IOException|RuntimeException error){return false;}
        }
        private ServerPlayer actor(){var player=state.server.getPlayerList().getPlayer(entry.request().actor());return player!=null&&player.level()==level?player:null;}
        private boolean inventoryBefore(){
            if(!GarageInventoryExchange.present(entry.before()))return true;
            try{return actor()!=null&&GaragePlayerExchange.state(actor(),entry)==GarageInventoryExchange.State.UNPAID;}catch(IOException error){return false;}
        }
        private boolean inventoryAfter(){
            if(!GarageInventoryExchange.present(entry.after()))return true;
            try{return actor()!=null&&GaragePlayerExchange.state(actor(),entry)==GarageInventoryExchange.State.PAID;}catch(IOException error){return false;}
        }
        public boolean validateBefore(){return receiptIs(entry.before())&&liveIs(entry.before())&&roundTrip()&&inventoryBefore();}
        public boolean checkpointBeforeIntent()throws IOException {
            if(!GarageInventoryExchange.present(entry.before()))return true;
            var actor=actor();return actor!=null&&validateBefore()&&GaragePlayerExchange.checkpoint(actor,entry,false)&&validateBefore();
        }
        public boolean stageAfterQuiet(){
            if(!validateBefore())return false;
            if(entry.request().operation()==Operation.DEPLOY)return GarageReturnPlacement.supported(level,lease.car.garageComponents(),lease.car.position(),lease.car.getYRot());
            return entry.request().operation()==Operation.STORED_TUNE||GarageService.operatingStatus(lease.car)==GarageService.Status.READY;
        }
        public boolean validateStaged(){return stageAfterQuiet();}
        private boolean writeReceipt(CompoundTag image){try{var wanted=receipt(image);if(wanted==null)return receiptIs(image);GarageStorage.write(level,wanted);return receiptIs(image);}catch(IOException error){return false;}}
        public boolean ensureAfterQuiet(){
            if(!receiptIs(entry.before())&&!receiptIs(entry.after()))return false;
            if(GarageInventoryExchange.present(entry.after())){
                try{if(actor()==null||!GaragePlayerExchange.apply(actor(),entry))return false;}catch(IOException|RuntimeException error){return false;}
            }
            if(!writeReceipt(entry.after()))return false;
            if(entry.request().operation()==Operation.DEPLOY){
                var existing=level.getEntity(entry.request().vehicle());if(existing!=null)return existing==lease.car&&liveIs(entry.after());
                try(var mutate=new Scope(lease.car,entry.request(),null,false,true,false)){if(!level.addFreshEntity(lease.car))return false;}
            }
            return true;
        }
        public boolean validateAfter(){return receiptIs(entry.after())&&roundTrip()&&(entry.request().operation()==Operation.STORE||liveIs(entry.after()))&&inventoryAfter();}
        public boolean ensureBeforeQuiet(){
            if(!receiptIs(entry.before())||!inventoryBefore())return false;
            if(entry.before().getBooleanOr("active",false)&&level.getEntity(entry.request().vehicle())==null){
                try(var mutate=new Scope(lease.car,entry.request(),null,false,true,false)){return level.addFreshEntity(lease.car);}
            }
            return true;
        }
        public boolean checkpoint()throws IOException {
            var current=state.latest.get(entry.request().vehicle());if(current==null||current.decision()==Decision.UNDECIDED)return false;
            var resolved=current.decision()==Decision.COMMIT?entry.after():entry.before();
            if(!resolved.getBooleanOr("active",false)){
                var live=level.getEntity(entry.request().vehicle());
                if(live!=null){if(live!=lease.car)return false;try(var mutate=new Scope(lease.car,entry.request(),null,false,true,false)){lease.car.discard();}}
            }
            for(var world:level.getServer().getAllLevels())if(world!=level&&world.getEntity(entry.request().vehicle())!=null)return false;
            if(entry.request().operation()==Operation.STORED_TUNE||entry.request().operation()==Operation.ENGINE_SWAP&&!resolved.getBooleanOr("active",true)){
                // A protected, already-finalized inactive origin cannot publish a world entity.
                // Only its receipt and the actor's atomic playerdata image change in this operation.
                if(entry.before().getBooleanOr("active",true)||entry.after().getBooleanOr("active",true)
                        ||!receiptIs(resolved)||!liveIs(resolved)||!roundTrip()||state.failure)return false;
                if(entry.request().operation()==Operation.STORED_TUNE&&!ownerRaw(entity(entry.before())).equals(ownerRaw(entity(entry.after()))))return false;
                var actor=actor();if(actor==null||!GaragePlayerExchange.checkpoint(actor,entry,current.decision()==Decision.COMMIT))return false;
                for(var world:level.getServer().getAllLevels())if(world.getEntity(entry.request().vehicle())!=null)return false;
                boolean checkpointed = !state.failure&&receiptIs(resolved)&&roundTrip();
                if (checkpointed && current.decision()==Decision.COMMIT) DrivetrainGarageCommit.publish(lease.car, entry.request());
                return checkpointed;
            }
            if(!receiptIs(resolved)||!liveIs(resolved)||state.failure)return false;
            if(GarageInventoryExchange.present(entry.before())){
                var actor=actor();if(actor==null||!GaragePlayerExchange.checkpoint(actor,entry,current.decision()==Decision.COMMIT))return false;
            }
            boolean checkpointed = checkpointActiveImage(resolved);
            if (checkpointed && current.decision()==Decision.COMMIT) DrivetrainGarageCommit.publish(lease.car, entry.request());
            return checkpointed;
        }
        boolean checkpointActiveImage(CompoundTag resolved)throws IOException {
            level.save(null,true,false);
            if(state.failure||!receiptIs(resolved))return false;
            var source=entity(entry.before());var target=entity(resolved);
            var chunks=new HashSet<ChunkPos>();chunks.add(chunk(source));chunks.add(chunk(target));
            int matches=0;
            for(var chunk:chunks){
                if(!level.areEntitiesLoaded(chunk.pack()))return false;
                var storage=((GarageManagerStorageAccess)(Object)((GarageLevelStorageAccess)(Object)level).automobility$garageStorage()).automobility$garageStorage();
                if(!(storage instanceof EntityStorage))return false;state.storages.add(storage);
                if(GarageFailedOrigins.failed(storage,chunk.pack()))return false;
                var saved=((GarageRegionStorageAccess)(Object)storage).automobility$garageStorage().read(chunk).join();
                if(saved.isEmpty())continue;
                for(var tag:saved.get().getListOrEmpty("Entities"))if(tag instanceof CompoundTag raw&&entry.request().vehicle().equals(GarageWorldAdmission.uuid(raw))){
                    matches++;if(!GarageCarrierMarker.read(raw).orElse(GarageCarrierMarker.LEGACY).equals(marker(resolved)))return false;
                    // Exact owned subtrees must equal the frozen owner state; incidental vanilla counters may differ.
                    if(entry.request().operation()==Operation.ENGINE_SWAP&&!GarageEnginePlan.pose(raw).equals(GarageEnginePlan.pose(target)))return false;
                    if(!ownerRaw(raw).equals(ownerRaw(target))||!persistentRaw(raw).equals(persistentRaw(target))||!GarageOpaqueState.presentExactly(raw,lease.car.garageOpaqueSnapshot()))return false;
                }
            }
            return matches==(resolved.getBooleanOr("active",false)?1:0);
        }
    }
    private static boolean storageReady(ServerLevel level,ChunkPos chunk){
        if(!level.hasChunk(chunk.x(),chunk.z())||!level.areEntitiesLoaded(chunk.pack()))return false;
        var storage=((GarageManagerStorageAccess)(Object)((GarageLevelStorageAccess)(Object)level).automobility$garageStorage()).automobility$garageStorage();
        return storage instanceof EntityStorage&&!GarageFailedOrigins.failed(storage,chunk.pack());
    }
    private static ChunkPos chunk(CompoundTag raw)throws IOException {var pos=Vec3.CODEC.parse(NbtOps.INSTANCE,raw.get("Pos")).result().orElseThrow(()->new IOException("Missing raw position"));if(!GarageReturnPlacement.finite(pos)||Math.abs(pos.x)>30_000_000||Math.abs(pos.z)>30_000_000)throw new IOException("Invalid raw position");return new ChunkPos(((int)Math.floor(pos.x))>>4,((int)Math.floor(pos.z))>>4);}
    private static CompoundTag persistentRaw(CompoundTag raw){
        var out=new CompoundTag();for(var key:List.of("frame","wheels","engine","rearAttachment","frontAttachment","vehicleAccess","vehicleDrivingMode","automobility:drivetrain_settings","automobilityAutoradioCarV1","automobilityAutoradioCassetteV1","automobilityCabinCosmeticsV1","automobilityAutoradioAccessoryV1","automobilityCabinTuningV1","vehicleEngineSettingsVersion","automaticEngineStart","idleEngineShutdownSeconds","lightsWithEngine","mx5HoodOpen","vehicleLightSettingsVersion","vehicleLightMode","vehicleSignalSettingsVersion","vehicleSignalMode","vehicleSignalAutoCancel","vehicleComfortSignals","vehicleLockSignalConfirmation","unattendedLightsVersion","unattendedLightSeconds","CustomName","CustomNameVisible","Tags"))if(raw.contains(key))out.put(key,raw.get(key).copy());return out;
    }
    public static CompoundTag ownerRaw(CompoundTag raw){var result=new CompoundTag();for(var key:raw.keySet())if(key.startsWith("AutoFuel")||key.equals("AutoTrip")||key.startsWith("powerworks:"))result.put(key,raw.get(key).copy());return result;}
    public static void tick(MinecraftServer server){
        var state=state(server);if(state==null||state.inTransfer)return;
        if(!state.failure&&server.getTickCount()>=state.nextRecovery){state.nextRecovery=server.getTickCount()+20;recoverPending(server,null);}
        resumeInventoryAdmissions(server);
    }
    private static void recoverPending(MinecraftServer server,UUID onlyActor){
        var state=state(server);if(state==null||state.failure||state.inTransfer)return;
        for(var entry:List.copyOf(state.latest.values())){
            if(onlyActor!=null&&!onlyActor.equals(entry.request().actor()))continue;
            if(entry.finalized()||state.ambiguous.contains(entry.request().vehicle()))continue;
            var actor=server.getPlayerList().getPlayer(entry.request().actor());if(actor==null||!dimension(actor.level()).equals(entry.request().dimension()))continue;
            try{
                var before=entity(entry.before());var after=entity(entry.after());
                if(entry.request().operation()!=Operation.STORED_TUNE&&!(entry.request().operation()==Operation.ENGINE_SWAP&&!entry.before().getBooleanOr("active",true))&&(!actor.level().hasChunk(chunk(before).x(),chunk(before).z())||!actor.level().areEntitiesLoaded(chunk(before).pack())
                        ||!actor.level().hasChunk(chunk(after).x(),chunk(after).z())||!actor.level().areEntitiesLoaded(chunk(after).pack())))continue;
                if(entry.request().operation()==Operation.ENGINE_SWAP){
                    var frozen=GarageEnginePlan.read(actor.registryAccess(),entry);var selected=engineOwners(before,entry,frozen.input().actualIncomingSource());
                    var carrier=actor.level().getEntity(entry.request().vehicle()) instanceof AutomobileEntity car?car:null;
                    executeEngine(state,actor.level(),null,entry,carrier,selected,true);continue;
                }
                var selected=owners(before,entry);var inspected=inspect(actor.level(),entry.request(),before,selected);if(!inspected.equals(entry.owners()))continue;
                AutomobileEntity carrier=actor.level().getEntity(entry.request().vehicle()) instanceof AutomobileEntity car?car:null;
                execute(state,actor.level(),null,entry,carrier,selected,true);
            }catch(Exception error){LoggerFactory.getLogger("AutomobilityGarage").warn("Garage recovery remains fenced: {}",entry.request().vehicle(),error);}
        }
    }

    // API2 has a separately qualified cohort; API1 registration never unlocks engines.
    private static final Map<String,GarageEngineTransferParticipant> ENGINE_OWNERS=new TreeMap<>();
    private record EngineComponentScope(EngineLease lease,String target){}
    private static final ThreadLocal<EngineComponentScope> ENGINE_SCOPE=new ThreadLocal<>();
    public static boolean registerQualifiedEngine(GarageEngineTransferParticipant owner){
        if(!installed||!STATES.isEmpty()||owner==null||!loadedOwners.contains(owner.id())
                ||owner.engineApiVersion()!=2||owner.enginePlanVersion()<1||ENGINE_OWNERS.containsKey(owner.id()))return false;
        ENGINE_OWNERS.put(owner.id(),owner);return true;
    }
    private static boolean enginePermit(AutomobileEntity car,net.minecraft.resources.Identifier target){
        var scope=ENGINE_SCOPE.get();if(scope==null||!scope.target().equals(target.toString()))return false;var lease=scope.lease();
        if(!lease.matchesPlan(car,lease.request.transaction(),lease.request.generation(),lease.frozen.exchange().frozenPlanDigest()))return false;
        var decision=lease.decisionFor(car,lease.request.transaction(),lease.request.generation());
        return decision==Decision.COMMIT?target.toString().equals(lease.request.afterEngine()):decision==Decision.ROLLBACK?target.toString().equals(lease.request.beforeEngine())
                :target.toString().equals(lease.request.beforeEngine())||target.toString().equals(lease.request.afterEngine());
    }
    private static Map<String,GarageEngineTransferParticipant> engineOwners(CompoundTag raw,Entry previous,net.minecraft.world.item.ItemStack input)throws IOException{
        var ids=new TreeSet<>(required(raw,previous));
        var custom=input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(custom!=null)for(var key:custom.copyTag().keySet()){
            if(key.equals("powerworks:traction_battery"))ids.add("powerworks");else throw new IOException("Unsupported engine item payload");
        }
        var result=new TreeMap<String,GarageEngineTransferParticipant>();
        for(var id:ids){var owner=ENGINE_OWNERS.get(id);if(owner==null||owner.engineApiVersion()!=2||owner.enginePlanVersion()<1)throw new IOException("Qualified engine owner unavailable: "+id);result.put(id,owner);}return result;
    }
    public static boolean enginesAvailable(){return installed&&ENGINE_OWNERS.keySet().containsAll(loadedOwners);}
    public record EngineDraft(GarageService.Status status,GarageEngineTransferParticipant.Reason reason,GarageEnginePlan.Frozen frozen){}
    /** Explicit prepare action only. Refresh/hover must never call this method. No inventory/vehicle writes. */
    public static EngineDraft prepareEngine(ServerPlayer actor,AutomobileEntity active,GarageStorage.Entry stored,int slot,net.minecraft.world.item.ItemStack offered)throws IOException{
        var level=actor.level();serverThread(level.getServer());var state=state(level.getServer());
        if(state==null||state.failure||state.inTransfer||!enginesAvailable()||actorPending(state.server,actor.getUUID()))return new EngineDraft(GarageService.Status.INTEGRATION,GarageEngineTransferParticipant.Reason.UNSUPPORTED_DATA,null);
        if((active==null)==(stored==null)||slot<0||slot>=36||offered==null||offered.isEmpty()||!GaragePlayerExchange.cursorEmpty(actor)
                ||!net.minecraft.world.item.ItemStack.matches(actor.getInventory().getItem(slot),offered))return new EngineDraft(GarageService.Status.STALE,GarageEngineTransferParticipant.Reason.INVALID_INPUT,null);
        GarageStorage.Entry snapshot;GarageStorage.Entry priorReceipt;GarageCarrierMarker beforeMarker;
        if(active!=null){
            if(active.level()!=level||active.isRemoved()||level.getEntity(active.getUUID())!=active||GarageTransferAccess.blocked(active)||!active.getVehicleAccess().isOwner(actor.getUUID())||cargoBusy(level,active))return new EngineDraft(GarageService.Status.PROTECTED,GarageEngineTransferParticipant.Reason.BUSY,null);
            var operating=GarageService.operatingStatus(active);if(operating!=GarageService.Status.READY)return new EngineDraft(operating,GarageEngineTransferParticipant.Reason.BUSY,null);
            snapshot=GarageStorage.snapshot(active,actor.getUUID());priorReceipt=GarageStorage.get(level,actor.getUUID(),active.getUUID());beforeMarker=active.garageCarrierMarker();
        }else{
            if(!canStoredWheelTune(level,stored)||!stored.owner().equals(actor.getUUID())||!dimension(level).equals(stored.dimension())||stored.state()!=GarageStorage.State.PARKED
                    ||!stored.equals(GarageStorage.get(level,actor.getUUID(),stored.id()))||GarageStorage.uuidPresent(level,stored.id()))return new EngineDraft(GarageService.Status.PROTECTED,GarageEngineTransferParticipant.Reason.BUSY,null);
            snapshot=stored;priorReceipt=stored;beforeMarker=GarageCarrierMarker.readStored(stored.entity()).orElseThrow(()->new IOException("Stored engine marker invalid"));
        }
        var previous=state.latest.get(snapshot.id());
        if(!state.admission.resolvedMatches(snapshot.id(),dimension(level),beforeMarker,active!=null)||!storageReady(level,chunk(snapshot.entity())))return new EngineDraft(GarageService.Status.UNLOADED,GarageEngineTransferParticipant.Reason.BUSY,null);
        var factory=io.github.foundationgames.automobility.item.AutomobilityItems.AUTOMOBILE_ENGINE.require();
        if(!offered.is(factory)||factory.getComponent(offered,level.registryAccess()).isEmpty())return new EngineDraft(GarageService.Status.INVALID_PART,GarageEngineTransferParticipant.Reason.INVALID_INPUT,null);
        var newKey=offered.get(factory.dataComponent.require());
        var oldId=net.minecraft.resources.Identifier.tryParse(snapshot.entity().getStringOr("engine",""));if(oldId==null||newKey==null)return new EngineDraft(GarageService.Status.INVALID_PART,GarageEngineTransferParticipant.Reason.INVALID_INPUT,null);
        var oldKey=net.minecraft.resources.ResourceKey.create(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY,oldId);
        if(level.registryAccess().lookupOrThrow(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY).get(oldKey).filter(h->!h.value().isEmpty()).isEmpty())return new EngineDraft(GarageService.Status.INVALID_PART,GarageEngineTransferParticipant.Reason.UNSUPPORTED_ENGINE,null);
        var generation=previous==null?1:Math.addExact(previous.request().generation(),1);var tx=UUID.randomUUID();
        var request=new Request(tx,generation,Operation.ENGINE_SWAP,actor.getUUID(),snapshot.id(),dimension(level),oldId.toString(),newKey.identifier().toString());
        var origin=active==null?GarageEngineTransferParticipant.Origin.STORED:GarageEngineTransferParticipant.Origin.ACTIVE;
        var er=new GarageEngineTransferParticipant.Request(request,origin);var input=new GarageEngineTransferParticipant.Input(snapshot.entity(),offered,factory.createStack(oldKey),slot);
        var selected=engineOwners(snapshot.entity(),previous,offered);
        if(!GarageEnginePlan.supportedEngine(request.beforeEngine(),selected.keySet())||!GarageEnginePlan.supportedEngine(request.afterEngine(),selected.keySet()))return new EngineDraft(GarageService.Status.INTEGRATION,GarageEngineTransferParticipant.Reason.UNSUPPORTED_ENGINE,null);
        if(!GarageEnginePlan.canonicalInput(offered,factory.createStack(newKey),selected.keySet()))return new EngineDraft(GarageService.Status.INVALID_PART,GarageEngineTransferParticipant.Reason.UNSUPPORTED_DATA,null);
        for(var owner:selected.values()){var ready=owner.previewRaw(level,er,input);if(ready.verdict()!=Verdict.READY)return new EngineDraft(GarageService.Status.INTEGRATION,ready.reason(),null);}
        var plans=new TreeMap<String,GarageEnginePlan.Versioned>();
        for(var row:selected.entrySet()){
            var p=row.getValue().planRaw(level,er,input);if(p.verdict()!=Verdict.READY||p.plan()==null)return new EngineDraft(GarageService.Status.INTEGRATION,p.reason(),null);
            plans.put(row.getKey(),new GarageEnginePlan.Versioned(row.getValue().enginePlanVersion(),p.plan()));
        }
        var returned=GarageEnginePlan.mergeReturned(input.canonicalReturnedBase(),plans);
        var inventory=GarageBatchPlan.planEngineAtSlot(GarageBatchPlan.snapshot(actor.getInventory()),slot,offered,returned);
        if(!inventory.ready())return new EngineDraft(inventory.status(),GarageEngineTransferParticipant.Reason.INVALID_INPUT,null);
        var afterRaw=GarageEnginePlan.mergeCar(snapshot.entity(),offered,plans);afterRaw.putString("engine",newKey.identifier().toString());
        var afterMarker=new GarageCarrierMarker(generation,tx,beforeMarker.kind());afterMarker.write(afterRaw);
        var afterReceipt=priorReceipt==null?null:new GarageStorage.Entry(priorReceipt.id(),priorReceipt.owner(),priorReceipt.name(),priorReceipt.dimension(),priorReceipt.x(),priorReceipt.z(),priorReceipt.state(),afterRaw);
        var before=image(snapshot.id(),dimension(level),beforeMarker,snapshot.entity(),priorReceipt);var after=image(snapshot.id(),dimension(level),afterMarker,afterRaw,afterReceipt);
        GarageInventoryExchange.attach(request,before,after,GaragePlayerExchange.snapshot(actor),GaragePlayerExchange.snapshot(actor,inventory.after()),GaragePlayerExchange.marker(actor));
        return new EngineDraft(GarageService.Status.READY,GarageEngineTransferParticipant.Reason.NONE,GarageEnginePlan.freeze(level.registryAccess(),er,input,before,after,plans,returned));
    }
    public static GarageService.Status confirmEngine(ServerPlayer actor,GarageEnginePlan.Frozen frozen)throws IOException{
        var level=actor.level();serverThread(level.getServer());var state=state(level.getServer());if(state==null||state.inTransfer||state.failure||actorPending(state.server,actor.getUUID())||frozen==null||!frozen.request().transaction().actor().equals(actor.getUUID())||!frozen.request().transaction().dimension().equals(dimension(level)))return GarageService.Status.STALE;
        var request=frozen.request().transaction();var previous=state.latest.get(request.vehicle());
        if(request.generation()!=(previous==null?1:previous.request().generation()+1)||previous!=null&&!previous.finalized())return GarageService.Status.STALE;
        var entry=new Entry(request,Decision.UNDECIDED,false,false,frozen.before(),frozen.after(),frozen.journalOwners(),Set.of());
        var checked=GarageEnginePlan.read(level.registryAccess(),entry);var selected=engineOwners(entity(entry.before()),previous,checked.input().actualIncomingSource());
        if(!selected.keySet().equals(checked.owners().keySet()))return GarageService.Status.INTEGRATION;
        var car=level.getEntity(request.vehicle()) instanceof AutomobileEntity found?found:null;
        if(frozen.request().origin()==GarageEngineTransferParticipant.Origin.ACTIVE){
            if(car==null||cargoBusy(level,car)||GarageService.operatingStatus(car)!=GarageService.Status.READY||car.distanceToSqr(actor)>64)return GarageService.Status.PROTECTED;
        }else if(car!=null||!canStoredWheelTune(level,receipt(entry.before())))return GarageService.Status.STALE;
        if(!storageReady(level,chunk(entity(entry.before()))))return GarageService.Status.UNLOADED;
        return executeEngine(state,level,previous,entry,car,selected,false).status();
    }
    private static final class EngineLease implements GarageEngineTransferParticipant.EngineFence{
        final State state;final ServerLevel level;final AutomobileEntity car;final Request request;final GarageCarrierMarker pinned;final GarageEnginePlan.Frozen frozen;final boolean recoveredDetached;
        EngineLease(State state,ServerLevel level,AutomobileEntity car,GarageEnginePlan.Frozen frozen,boolean recoveredDetached){this.recoveredDetached=recoveredDetached;this.state=state;this.level=level;this.car=car;this.frozen=frozen;request=frozen.request().transaction();pinned=car.garageCarrierMarker();}
        public UUID transactionId(){return request.transaction();}public long generation(){return request.generation();}
        public boolean heldFor(AutomobileEntity actual,UUID tx,long gen){return actual==car&&car.level()==level&&level.getServer()==state.server&&state.server.isSameThread()&&car.getUUID().equals(request.vehicle())&&car.garageCarrierMarker().equals(pinned)&&dimension(level).equals(request.dimension())&&request.transaction().equals(tx)&&request.generation()==gen&&state.admission.pending(request.vehicle());}
        public boolean matchesPlan(AutomobileEntity actual,UUID tx,long gen,String digest){return heldFor(actual,tx,gen)&&frozen.exchange().frozenPlanDigest().equals(digest);}
        public boolean intentDurableFor(AutomobileEntity actual,UUID tx,long gen){var entry=state.latest.get(request.vehicle());return heldFor(actual,tx,gen)&&entry!=null&&entry.request().equals(request);}
        public Decision decisionFor(AutomobileEntity actual,UUID tx,long gen){var entry=state.latest.get(request.vehicle());return intentDurableFor(actual,tx,gen)?entry.decision():Decision.UNDECIDED;}
    }
    private static Map<String,Prepared> prepareEngineOwners(EngineLease lease,Map<String,GarageEngineTransferParticipant> owners,boolean recovery,Decision decision)throws IOException{
        var result=new TreeMap<String,Prepared>();try{
            for(var row:owners.entrySet()){
                var versioned=lease.frozen.owners().get(row.getKey());if(versioned==null||versioned.version()!=row.getValue().enginePlanVersion())throw new IOException("Engine owner plan version changed");
                var preparation=recovery?row.getValue().recover(lease.frozen.request(),lease.car,lease,versioned.plan(),lease.frozen.exchange(),decision):row.getValue().prepare(lease.frozen.request(),lease.car,lease,versioned.plan(),lease.frozen.exchange());
                if(preparation.verdict()!=Verdict.READY||preparation.prepared()==null||!versioned.plan().equals(preparation.prepared().plan())){
                    if(preparation.prepared()!=null)try{preparation.prepared().closeLocal();}catch(Exception ignored){}
                    throw new IOException("Engine owner lease refused: "+row.getKey());
                }
                var delegate=preparation.prepared();var id=row.getKey();var version=versioned.version();
                result.put(id,new Prepared(){
                    public CompoundTag capsule(){return GarageEnginePlan.ownerCapsule(lease.frozen,id,new GarageEnginePlan.Versioned(version,delegate.plan()));}
                    public Verdict validateBeforeApply(){return delegate.validateBeforeApply();}public Verdict applyQuiet(){return delegate.applyQuiet();}public Verdict validateApplied(){return delegate.validateApplied();}public Verdict restoreQuiet(){return delegate.restoreQuiet();}public Verdict publish(Decision d){return delegate.publish(d);}public Verdict closeLocal(){return delegate.closeLocal();}
                });
            }return result;
        }catch(IOException|RuntimeException error){for(var value:result.values())try{value.closeLocal();}catch(Exception ignored){}throw error;}
    }
    private static Result executeEngine(State state,ServerLevel level,Entry previous,Entry entry,AutomobileEntity car,Map<String,GarageEngineTransferParticipant> owners,boolean recovery)throws IOException{
        if(recovery&&entry.decision()==Decision.UNDECIDED){var rollback=entry.decide(Decision.ROLLBACK);state.journal.write(entry,rollback);state.observe(rollback);entry=rollback;}
        var frozen=GarageEnginePlan.read(level.registryAccess(),entry);var request=entry.request();
        if(!owners.keySet().equals(frozen.owners().keySet())||!state.admission.hold(request.vehicle(),request.transaction()))return new Result(GarageService.Status.RECOVERY_REQUIRED,null);
        state.inTransfer=true;Map<String,Prepared> prepared=Map.of();try{
            if(car!=null)io.github.foundationgames.automobility.api.vehicle.VehicleQuiescenceProviders.garageHold(car,request.transaction(),request.generation());
            var selected=recovery&&entry.decision()==Decision.COMMIT?entry.after():entry.before();
            if(car==null)car=materialize(level,request,entity(selected));
            else if(car.level()!=level||car.isRemoved()||level.getEntity(request.vehicle())!=car||!car.getVehicleAccess().isOwner(request.actor())
                    ||!(car.garageCarrierMarker().equals(marker(entry.before()))||entry.decision()==Decision.COMMIT&&car.garageCarrierMarker().equals(marker(entry.after()))))throw new IOException("Engine recovery carrier changed");
            var lease=new EngineLease(state,level,car,frozen,recovery&&level.getEntity(request.vehicle())==null);prepared=prepareEngineOwners(lease,owners,recovery,entry.decision());var work=new EngineWork(state,level,entry,lease);
            var outcome=recovery?GarageTransferCoordinator.recover(state.journal.repository(),entry,work,prepared,state::observe):GarageTransferCoordinator.execute(state.journal.repository(),previous,entry,work,prepared,state::observe);
            if(outcome.retained()!=null&&outcome.retained().finalized()){
                state.admission.release(request.vehicle(),request.transaction());
                if(level.getEntity(request.vehicle()) instanceof AutomobileEntity live)state.admission.admit(live,live.getUUID(),dimension(level),live.garageCarrierMarker());
            }else{var latest=state.latest.get(request.vehicle());if(latest==null||!latest.request().equals(request))state.admission.release(request.vehicle(),request.transaction());}
            if(outcome.status()==GarageTransferCoordinator.Status.RECOVERY_REQUIRED)disconnectPendingActor(state,request);
            return new Result(outcome.status()==GarageTransferCoordinator.Status.APPLIED?GarageService.Status.APPLIED:outcome.status()==GarageTransferCoordinator.Status.ROLLED_BACK?GarageService.Status.STORAGE_ERROR:GarageService.Status.RECOVERY_REQUIRED,level.getEntity(request.vehicle()) instanceof AutomobileEntity live?live:null);
        }catch(IOException|RuntimeException error){for(var owner:prepared.values())try{owner.closeLocal();}catch(Exception ignored){}var latest=state.latest.get(request.vehicle());if(latest==null||!latest.request().equals(request))state.admission.release(request.vehicle(),request.transaction());disconnectPendingActor(state,request);throw error;
        }finally{state.inTransfer=false;}
    }
    private static final class EngineWork implements GarageTransferCoordinator.CoreWork{
        final State state;final ServerLevel level;final Entry entry;final EngineLease lease;
        EngineWork(State state,ServerLevel level,Entry entry,EngineLease lease){this.state=state;this.level=level;this.entry=entry;this.lease=lease;}
        private ServerPlayer actor(){var actor=state.server.getPlayerList().getPlayer(entry.request().actor());return actor!=null&&actor.level()==level?actor:null;}
        private boolean inventory(boolean paid){try{return actor()!=null&&GaragePlayerExchange.state(actor(),entry)==(paid?GarageInventoryExchange.State.PAID:GarageInventoryExchange.State.UNPAID);}catch(IOException error){return false;}}
        private boolean receiptIs(CompoundTag image){try{return Objects.equals(receipt(image),GarageStorage.get(level,entry.request().actor(),entry.request().vehicle()));}catch(IOException error){return false;}}
        private boolean bound(){if(!lease.car.getUUID().equals(entry.request().vehicle())||lease.car.level()!=level||!lease.car.getVehicleAccess().isOwner(entry.request().actor()))return false;
            for(var world:state.server.getAllLevels()){var found=world.getEntity(entry.request().vehicle());if(found!=null&&(world!=level||!entry.before().getBooleanOr("active",false)||found!=lease.car))return false;}
            return !entry.before().getBooleanOr("active",false)||level.getEntity(entry.request().vehicle())==lease.car||lease.recoveredDetached&&level.getEntity(entry.request().vehicle())==null;
        }
        private boolean rawIs(CompoundTag image,boolean owners){try{var actual=GarageStorage.snapshot(lease.car,entry.request().actor()).entity();var expected=entity(image);
            return bound()&&GarageEnginePlan.pose(actual).equals(GarageEnginePlan.pose(expected))&&persistentRaw(actual).equals(persistentRaw(expected))&&(!owners||ownerRaw(actual).equals(ownerRaw(expected)))&&GarageOpaqueState.presentExactly(actual,lease.car.garageOpaqueSnapshot());
        }catch(IOException|RuntimeException error){return false;}}
        private boolean component(boolean after,boolean publish){
            try{var raw=entity(after?entry.after():entry.before());var id=net.minecraft.resources.Identifier.tryParse(raw.getStringOr("engine",""));if(id==null)return false;
                var target=level.registryAccess().lookupOrThrow(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY).get(net.minecraft.resources.ResourceKey.create(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY,id)).orElse(null);if(target==null)return false;
                if(ENGINE_SCOPE.get()!=null)return false;ENGINE_SCOPE.set(new EngineComponentScope(lease,id.toString()));try{return publish?lease.car.garagePublishEngine(target):lease.car.garageStageEngine(target);}finally{ENGINE_SCOPE.remove();}
            }catch(IOException|RuntimeException error){return false;}
        }
        public boolean validateBefore(){return rawIs(entry.before(),true)&&receiptIs(entry.before())&&inventory(false);}
        public boolean checkpointBeforeIntent()throws IOException{
            if(actor()==null||!validateBefore()||!GaragePlayerExchange.checkpoint(actor(),entry,false)||!validateBefore())return false;
            if(entry.before().getBooleanOr("active",false)){
                // Persist the exact entity preimage as well as playerdata before INTENT.
                return new Work(state,level,entry,new Lease(state,entry.request(),lease.car,level,lease.pinned,entity(entry.before()))).checkpointActiveImage(entry.before());
            }return true;
        }
        public boolean stageAfterQuiet(){return validateBefore()&&component(true,false);}
        public boolean validateStaged(){return rawIs(entry.after(),true)&&receiptIs(entry.before())&&inventory(false);}
        private boolean knownOwnerImage(){try{var raw=GarageStorage.snapshot(lease.car,entry.request().actor()).entity();
            for(var id:lease.frozen.owners().keySet()){var plan=lease.frozen.owners().get(id).plan();var now=GarageEnginePlan.projectCar(id,raw);if(!now.equals(plan.beforeCarOwner())&&!now.equals(plan.afterCarOwner()))return false;}return true;
        }catch(IOException|RuntimeException error){return false;}}
        public boolean ensureAfterQuiet(){
            if(!(receiptIs(entry.before())||receiptIs(entry.after()))||!(rawIs(entry.before(),false)||rawIs(entry.after(),false))||!component(true,false))return false;
            try{if(actor()==null||!GaragePlayerExchange.apply(actor(),entry))return false;var target=receipt(entry.after());if(target!=null)GarageStorage.write(level,target);return receiptIs(entry.after());}catch(IOException|RuntimeException error){return false;}
        }
        public boolean validateAfter(){return rawIs(entry.after(),true)&&receiptIs(entry.after())&&inventory(true);}
        public boolean ensureBeforeQuiet(){return receiptIs(entry.before())&&inventory(false)&&(rawIs(entry.before(),false)||rawIs(entry.after(),false))&&component(false,false);}
        public boolean checkpoint()throws IOException{
            var current=state.latest.get(entry.request().vehicle());if(current==null||current.decision()==Decision.UNDECIDED)return false;boolean committed=current.decision()==Decision.COMMIT;var resolved=committed?entry.after():entry.before();
            if(!rawIs(resolved,true)||!receiptIs(resolved)||!inventory(committed)||!component(committed,true))return false;
            lease.car.garageCarrierMarker(marker(resolved));
            if(resolved.getBooleanOr("active",false)&&level.getEntity(entry.request().vehicle())==null){
                if(!lease.recoveredDetached)return false;
                try(var mutate=new Scope(lease.car,entry.request(),null,false,true,false)){if(!level.addFreshEntity(lease.car))return false;}
            }
            return new Work(state,level,entry,new Lease(state,entry.request(),lease.car,level,marker(resolved),entity(resolved))).checkpoint();
        }
    }
}
