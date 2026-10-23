package io.github.foundationgames.automobility.entity;
import io.github.foundationgames.automobility.automobile.garage.GarageEngineTransferParticipant.*;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Entry;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Owner;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.io.IOException;
import java.util.*;

/** Frozen API2 resource/item plan codec. No materialization, owner callbacks or inventory mutation. */
public final class GarageEnginePlan {
    public static final String KEY="engineTransfer";
    private GarageEnginePlan(){}
    public record Versioned(int version,OwnerPlan plan){
        public Versioned{if(version<1||plan==null)throw new IllegalArgumentException();}
    }
    public record Frozen(Request request,Input input,Exchange exchange,Map<String,Versioned> owners,
                         CompoundTag before,CompoundTag after,CompoundTag metadata){
        public Frozen{owners=Collections.unmodifiableMap(new TreeMap<>(owners));before=before.copy();after=after.copy();metadata=metadata.copy();}
        @Override public CompoundTag before(){return before.copy();}
        @Override public CompoundTag after(){return after.copy();}
        @Override public CompoundTag metadata(){return metadata.copy();}
        public Map<String,Owner> journalOwners(){
            var out=new TreeMap<String,Owner>();for(var row:owners.entrySet())out.put(row.getKey(),new Owner(2,ownerCapsule(this,row.getKey(),row.getValue())));return Map.copyOf(out);
        }
    }
    private static CompoundTag compound(CompoundTag value,String key)throws IOException{return value.getCompound(key).orElseThrow(()->new IOException("Missing engine plan compound: "+key));}
    private static void keys(CompoundTag value,String... expected)throws IOException{if(!value.keySet().equals(Set.of(expected)))throw new IOException("Unknown or missing engine plan fields");}
    private static boolean bool(CompoundTag value,String key)throws IOException{
        if(ByteTag.valueOf(false).equals(value.get(key)))return false;if(ByteTag.valueOf(true).equals(value.get(key)))return true;throw new IOException("Malformed engine plan boolean");
    }
    public static CompoundTag writeOwnerPlan(OwnerPlan plan){
        var out=new CompoundTag();out.put("beforeCar",plan.beforeCarOwner());out.put("afterCar",plan.afterCarOwner());out.put("incomingItem",plan.incomingItemOwner());out.put("returnedItem",plan.returnedItemOwner());out.putBoolean("singleton",plan.returnedSingleton());out.put("private",plan.privateState());return out;
    }
    public static OwnerPlan readOwnerPlan(CompoundTag raw)throws IOException{
        keys(raw,"beforeCar","afterCar","incomingItem","returnedItem","singleton","private");
        try{return new OwnerPlan(compound(raw,"beforeCar"),compound(raw,"afterCar"),compound(raw,"incomingItem"),compound(raw,"returnedItem"),bool(raw,"singleton"),compound(raw,"private"));}
        catch(IllegalArgumentException error){throw new IOException("Invalid engine owner plan",error);}
    }
    private static boolean carKey(String owner,String key)throws IOException{
        return switch(owner){case "pumps_and_pistons"->key.startsWith("AutoFuel")||key.equals("AutoTrip");case "powerworks"->key.startsWith("powerworks:");default->throw new IOException("Unqualified engine owner: "+owner);};
    }
    private static boolean itemKey(String owner,String key)throws IOException{
        return switch(owner){case "pumps_and_pistons"->false;case "powerworks"->key.equals("powerworks:traction_battery");default->throw new IOException("Unqualified engine owner: "+owner);};
    }
    public static CompoundTag projectCar(String owner,CompoundTag raw)throws IOException{
        carKey(owner,"");var out=new CompoundTag();for(var key:raw.keySet())if(carKey(owner,key))out.put(key,raw.get(key).copy());return out;
    }
    public static CompoundTag projectItem(String owner,ItemStack stack)throws IOException{
        itemKey(owner,"");var data=stack.get(DataComponents.CUSTOM_DATA);var out=new CompoundTag();if(data==null)return out;
        var raw=data.copyTag();for(var key:raw.keySet())if(itemKey(owner,key))out.put(key,raw.get(key).copy());return out;
    }
    private static void ownedCar(String id,CompoundTag raw)throws IOException{for(var key:raw.keySet())if(!carKey(id,key))throw new IOException("Foreign root key in engine owner plan: "+id+"/"+key);}
    private static void ownedItem(String id,CompoundTag raw)throws IOException{for(var key:raw.keySet())if(!itemKey(id,key))throw new IOException("Foreign item key in engine owner plan: "+id+"/"+key);}
    public static CompoundTag mergeCar(CompoundTag original,ItemStack incoming,Map<String,Versioned> plans)throws IOException{
        var out=original.copy();
        for(var row:new TreeMap<>(plans).entrySet()){
            var id=row.getKey();var plan=row.getValue().plan();ownedCar(id,plan.beforeCarOwner());ownedCar(id,plan.afterCarOwner());ownedItem(id,plan.incomingItemOwner());ownedItem(id,plan.returnedItemOwner());
            if(!projectCar(id,original).equals(plan.beforeCarOwner())||!projectItem(id,incoming).equals(plan.incomingItemOwner()))throw new IOException("Owner plan preimage differs from actual engine source");
            for(var key:new HashSet<>(out.keySet()))if(carKey(id,key))out.remove(key);
            for(var key:plan.afterCarOwner().keySet())out.put(key,plan.afterCarOwner().get(key).copy());
        }
        return out;
    }
    public static ItemStack mergeReturned(ItemStack canonical,Map<String,Versioned> plans)throws IOException{
        if(canonical.isEmpty()||canonical.getCount()!=1)throw new IOException("Singleton canonical engine return required");
        var out=canonical.copy();var custom=out.get(DataComponents.CUSTOM_DATA);var data=custom==null?new CompoundTag():custom.copyTag();boolean singleton=false;
        for(var row:new TreeMap<>(plans).entrySet()){
            ownedItem(row.getKey(),row.getValue().plan().returnedItemOwner());singleton|=row.getValue().plan().returnedSingleton();
            var fragment=row.getValue().plan().returnedItemOwner();for(var key:fragment.keySet()){if(data.contains(key))throw new IOException("Overlapping engine item owner fragments");data.put(key,fragment.get(key).copy());}
        }
        if(!data.isEmpty())out.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        if(singleton)out.set(DataComponents.MAX_STACK_SIZE,1);return out;
    }
    public static boolean canonicalInput(ItemStack actual,ItemStack canonical,Set<String> qualifiedOwners)throws IOException{
        if(actual.isEmpty()||canonical.isEmpty())return false;var stripped=actual.copy();stripped.setCount(1);
        var custom=stripped.get(DataComponents.CUSTOM_DATA);
        if(custom!=null){for(var key:custom.copyTag().keySet()){
            boolean owned=false;for(var owner:qualifiedOwners)if(itemKey(owner,key)){owned=true;break;}if(!owned)return false;
        }stripped.remove(DataComponents.CUSTOM_DATA);}
        var maximum=stripped.get(DataComponents.MAX_STACK_SIZE);var expected=canonical.get(DataComponents.MAX_STACK_SIZE);
        if(!Objects.equals(maximum,expected)){
            if(!qualifiedOwners.contains("powerworks")||!Objects.equals(maximum,1)||expected==null)return false;
            stripped.set(DataComponents.MAX_STACK_SIZE,expected);
        }
        return ItemStack.isSameItemSameComponents(stripped,canonical);
    }
    public static CompoundTag stack(HolderLookup.Provider registries,ItemStack stack){return (CompoundTag)ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();}
    private static ItemStack stack(HolderLookup.Provider registries,Tag raw)throws IOException{
        try{return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),raw).getOrThrow();}
        catch(RuntimeException error){throw new IOException("Engine item codec refused",error);}
    }
    private static CompoundTag request(Request request){
        var r=request.transaction();var raw=new CompoundTag();raw.putString("transaction",r.transaction().toString());raw.putLong("generation",r.generation());raw.putString("operation",r.operation().name());raw.putString("actor",r.actor().toString());raw.putString("vehicle",r.vehicle().toString());raw.putString("dimension",r.dimension());raw.putString("beforeEngine",r.beforeEngine());raw.putString("afterEngine",r.afterEngine());return raw;
    }
    private static CompoundTag plans(Map<String,Versioned> owners){
        var out=new CompoundTag();for(var row:owners.entrySet()){var child=new CompoundTag();child.putInt("version",row.getValue().version());child.put("plan",writeOwnerPlan(row.getValue().plan()));out.put(row.getKey(),child);}return out;
    }
    private static CompoundTag base(CompoundTag value){var copy=value.copy();copy.remove(KEY);return copy;}
    private static String digest(Request request,CompoundTag before,CompoundTag after,CompoundTag metadata,Map<String,Versioned> owners)throws IOException{
        var context=new CompoundTag();context.put("request",request(request));context.put("before",base(before));context.put("after",base(after));var clean=metadata.copy();clean.remove("digest");context.put("exchange",clean);context.put("owners",plans(owners));return GarageCanonicalNbt.digest(context);
    }
    private static void verifyInventory(HolderLookup.Provider registries,Input input,ItemStack returned,CompoundTag before,CompoundTag after)throws IOException{
        var original=GarageInventoryExchange.afterInventory(before).getListOrEmpty("slots");if(original.size()!=36)throw new IOException("Missing complete engine source inventory");
        var slots=new ArrayList<ItemStack>();for(var tag:original)slots.add(stack(registries,tag));
        if(!original.get(input.sourceSlot()).equals(stack(registries,input.actualIncomingSource())))throw new IOException("Physical engine source slot differs from frozen input");
        var exchange=GarageBatchPlan.planEngineAtSlot(slots,input.sourceSlot(),input.actualIncomingSource(),returned);
        if(!exchange.ready())throw new IOException("Frozen engine inventory exchange no longer fits");
        var actual=new CompoundTag();var list=new ListTag();for(var slot:exchange.after())list.add(stack(registries,slot));actual.put("slots",list);
        if(!actual.equals(GarageInventoryExchange.afterInventory(after)))throw new IOException("Frozen engine inventory afterimage does not conserve items");
    }
    /** Journal recovery must return to the very pose which was accepted, not an older parked pose. */
    public static CompoundTag pose(CompoundTag raw){
        var result=new CompoundTag();for(var key:List.of("Pos","Rotation","Motion"))if(raw.contains(key))result.put(key,raw.get(key).copy());
        var rotation=raw.getListOrEmpty("Rotation");
        if(rotation.size()==2&&rotation.get(0) instanceof FloatTag yaw&&rotation.get(1) instanceof FloatTag pitch){
            float y=yaw.floatValue()%360f;var canonical=new ListTag();canonical.add(FloatTag.valueOf(y==0?0:y));canonical.add(FloatTag.valueOf(pitch.floatValue()));result.put("Rotation",canonical);
        }return result;
    }
    public static boolean supportedEngine(String id,Set<String> owners){
        return Set.of("automobility:stone","automobility:iron","automobility:copper","automobility:gold","automobility:diamond","automobility:creative").contains(id)
                ||id.equals("powerworks:electric")&&owners.contains("powerworks");
    }
    public static Frozen freeze(HolderLookup.Provider registries,Request request,Input input,CompoundTag before,CompoundTag after,Map<String,Versioned> owners,ItemStack returned)throws IOException{
        if(before.contains(KEY)||after.contains(KEY))throw new IOException("Engine transfer metadata already present");
        if(!supportedEngine(request.transaction().beforeEngine(),owners.keySet())||!supportedEngine(request.transaction().afterEngine(),owners.keySet()))throw new IOException("Engine outside qualified policy");
        GarageInventoryExchange.validate(request.transaction(),before,after);
        for(var image:List.of(before,after)){
            var root=compound(image,"entity");if(root.contains("Rotation")){
                var rotation=root.getListOrEmpty("Rotation");
                if(rotation.size()!=2||!(rotation.get(0) instanceof FloatTag yaw)||!(rotation.get(1) instanceof FloatTag pitch)
                        ||!Float.isFinite(yaw.floatValue())||!Float.isFinite(pitch.floatValue())||pitch.floatValue()<-90||pitch.floatValue()>90)throw new IOException("Engine pose would be changed by entity load");
            }
        }

        if(!compound(before,"entity").equals(input.beforeCarRoot())||!compound(before,"entity").getStringOr("engine","").equals(request.transaction().beforeEngine())
                ||!compound(after,"entity").getStringOr("engine","").equals(request.transaction().afterEngine()))throw new IOException("Engine source/Core component images differ");
        boolean active=request.origin()==Origin.ACTIVE;
        var beforeCarrier=GarageCarrierAdmission.Carrier.read(before);var afterCarrier=GarageCarrierAdmission.Carrier.read(after);
        var transaction=request.transaction();
        var expectedMarker=new GarageCarrierMarker(transaction.generation(),transaction.transaction(),active?GarageCarrierMarker.Kind.ACTIVE:GarageCarrierMarker.Kind.STORED);
        if(!beforeCarrier.vehicle().equals(transaction.vehicle())||!afterCarrier.vehicle().equals(transaction.vehicle())
                ||!beforeCarrier.dimension().equals(transaction.dimension())||!afterCarrier.dimension().equals(transaction.dimension())
                ||beforeCarrier.active()!=active||afterCarrier.active()!=active||beforeCarrier.marker().generation()>=transaction.generation()
                ||!afterCarrier.marker().equals(expectedMarker)
                ||!Objects.equals(GarageWorldAdmission.uuid(compound(before,"entity")),transaction.vehicle())
                ||!Objects.equals(GarageWorldAdmission.uuid(compound(after,"entity")),transaction.vehicle())
                ||!(active?GarageCarrierMarker.read(compound(before,"entity")):GarageCarrierMarker.readStored(compound(before,"entity"))).orElseThrow(()->new IOException("Malformed before raw marker")).equals(beforeCarrier.marker())
                ||!GarageCarrierMarker.read(compound(after,"entity")).orElseThrow(()->new IOException("Malformed after raw marker")).equals(afterCarrier.marker()))throw new IOException("Engine carrier descriptor/request/raw mismatch");
        if(!active)for(var image:List.of(before,after)){
            var receipt=GarageStorage.decodeEntry(compound(image,"receipt"));
            if(!receipt.id().equals(transaction.vehicle())||!receipt.owner().equals(transaction.actor())||!receipt.dimension().equals(transaction.dimension())
                    ||receipt.state()!=GarageStorage.State.PARKED||!receipt.entity().equals(compound(image,"entity")))throw new IOException("Stored engine receipt differs from carrier image");
        }
        if(active){
            if(before.contains("receipt")!=after.contains("receipt"))throw new IOException("Active engine swap cannot add or remove Garage receipt");
            if(before.contains("receipt")){
                var oldReceipt=GarageStorage.decodeEntry(compound(before,"receipt"));var newReceipt=GarageStorage.decodeEntry(compound(after,"receipt"));
                if(!oldReceipt.id().equals(transaction.vehicle())||!oldReceipt.owner().equals(transaction.actor())||!oldReceipt.dimension().equals(transaction.dimension())||oldReceipt.state()!=GarageStorage.State.DEPLOYED)throw new IOException("Active engine receipt identity changed");
                var expectedReceipt=new GarageStorage.Entry(oldReceipt.id(),oldReceipt.owner(),oldReceipt.name(),oldReceipt.dimension(),oldReceipt.x(),oldReceipt.z(),oldReceipt.state(),compound(after,"entity"));
                if(!expectedReceipt.equals(newReceipt))throw new IOException("Active engine receipt does not preserve historical metadata and exact target");
            }
        }
        if(before.getBooleanOr("active",!active)!=active||after.getBooleanOr("active",!active)!=active)throw new IOException("Engine swap cannot change carrier kind");
        var registry=registries.lookupOrThrow(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY);
        var oldId=net.minecraft.resources.Identifier.tryParse(request.transaction().beforeEngine());
        var newId=net.minecraft.resources.Identifier.tryParse(request.transaction().afterEngine());
        if(oldId==null||newId==null||!oldId.toString().equals(request.transaction().beforeEngine())||!newId.toString().equals(request.transaction().afterEngine()))throw new IOException("Invalid engine identifiers");
        var oldKey=net.minecraft.resources.ResourceKey.create(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY,oldId);
        var newKey=net.minecraft.resources.ResourceKey.create(io.github.foundationgames.automobility.automobile.AutomobileEngine.REGISTRY,newId);
        if(registry.get(oldKey).filter(h->!h.value().isEmpty()).isEmpty()||registry.get(newKey).filter(h->!h.value().isEmpty()).isEmpty())throw new IOException("Missing engine definition");
        var factory=io.github.foundationgames.automobility.item.AutomobilityItems.AUTOMOBILE_ENGINE.require();
        if(!ItemStack.matches(factory.createStack(oldKey),input.canonicalReturnedBase())
                ||!canonicalInput(input.actualIncomingSource(),factory.createStack(newKey),owners.keySet()))throw new IOException("Noncanonical or unsupported physical engine source");
        // A digest detects corruption; it does not replace semantic ownership/conservation checks.
        var original=compound(before,"entity");var target=compound(after,"entity");
        for(var key:original.keySet()){
            if((key.startsWith("AutoFuel")||key.equals("AutoTrip"))&&!owners.containsKey("pumps_and_pistons"))throw new IOException("Missing fuel owner");
            if(key.startsWith("powerworks:")&&!owners.containsKey("powerworks"))throw new IOException("Missing battery/access owner");
        }
        var custom=input.actualIncomingSource().get(DataComponents.CUSTOM_DATA);
        if(custom!=null)for(var key:custom.copyTag().keySet()){
            if(!key.equals("powerworks:traction_battery")||!owners.containsKey("powerworks"))throw new IOException("Unqualified source item data");
        }
        var expected=mergeCar(original,input.actualIncomingSource(),owners);
        expected.putString("engine",request.transaction().afterEngine());
        var selected=GarageCarrierMarker.read(after).orElseThrow(()->new IOException("Missing engine after marker"));selected.write(expected);
        if(!expected.equals(target))throw new IOException("Engine afterimage changes unrelated vehicle state");
        var exactReturn=mergeReturned(input.canonicalReturnedBase(),owners);
        if(!ItemStack.matches(exactReturn,returned))throw new IOException("Engine refund differs from merged owner fragments");
        verifyInventory(registries,input,returned,before,after);
        var metadata=new CompoundTag();metadata.putInt("schema",2);metadata.putString("origin",request.origin().name());metadata.putInt("sourceSlot",input.sourceSlot());metadata.put("source",stack(registries,input.actualIncomingSource()));metadata.put("returnedBase",stack(registries,input.canonicalReturnedBase()));metadata.put("returned",stack(registries,returned));
        var versions=new CompoundTag();for(var row:owners.entrySet())versions.putInt(row.getKey(),row.getValue().version());metadata.put("ownerVersions",versions);
        var hash=digest(request,before,after,metadata,owners);metadata.putString("digest",hash);
        var beforeCopy=before.copy();var afterCopy=after.copy();beforeCopy.put(KEY,metadata.copy());afterCopy.put(KEY,metadata.copy());
        var frozen=new Frozen(request,input,new Exchange(input.actualIncomingSource(),returned,hash),owners,beforeCopy,afterCopy,metadata);
        var max=new Entry(request.transaction(),io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Decision.UNDECIDED,false,false,beforeCopy,afterCopy,frozen.journalOwners(),Set.of());
        GarageTransferJournal.encode(max);return frozen;
    }
    public static CompoundTag ownerCapsule(Frozen frozen,String id,Versioned plan){
        var raw=new CompoundTag();raw.putInt("schema",2);raw.putString("owner",id);raw.putInt("planVersion",plan.version());raw.put("plan",writeOwnerPlan(plan.plan()));raw.put("exchange",frozen.metadata());return raw;
    }
    public static Frozen read(HolderLookup.Provider registries,Entry entry)throws IOException{
        var metadata=compound(entry.before(),KEY);if(!metadata.equals(compound(entry.after(),KEY)))throw new IOException("Engine exchange contexts differ");
        keys(metadata,"schema","origin","sourceSlot","source","returnedBase","returned","ownerVersions","digest");
        if(!(metadata.get("schema") instanceof IntTag)||metadata.getIntOr("schema",0)!=2||!(metadata.get("sourceSlot") instanceof IntTag)
                ||!(metadata.get("origin") instanceof StringTag)||!(metadata.get("digest") instanceof StringTag))throw new IOException("Malformed engine exchange context");
        try{
            var request=new Request(entry.request(),Origin.valueOf(metadata.getStringOr("origin","")));
            var input=new Input(compound(entry.before(),"entity"),stack(registries,metadata.get("source")),stack(registries,metadata.get("returnedBase")),metadata.getIntOr("sourceSlot",-1));
            var versions=compound(metadata,"ownerVersions");if(!versions.keySet().equals(entry.owners().keySet()))throw new IOException("Frozen engine owner set changed");
            var owners=new TreeMap<String,Versioned>();
            for(var row:entry.owners().entrySet()){
                var raw=row.getValue().capsule();keys(raw,"schema","owner","planVersion","plan","exchange");
                if(row.getValue().version()!=2||!(raw.get("schema") instanceof IntTag)||raw.getIntOr("schema",0)!=2
                        ||!(raw.get("owner") instanceof StringTag)||!row.getKey().equals(raw.getStringOr("owner",""))
                        ||!(raw.get("planVersion") instanceof IntTag)||!(versions.get(row.getKey()) instanceof IntTag)
                        ||raw.getIntOr("planVersion",0)!=versions.getIntOr(row.getKey(),-1)||!metadata.equals(compound(raw,"exchange")))throw new IOException("Frozen engine owner envelope differs");
                owners.put(row.getKey(),new Versioned(raw.getIntOr("planVersion",0),readOwnerPlan(compound(raw,"plan"))));
            }
            var expected=freeze(registries,request,input,base(entry.before()),base(entry.after()),owners,stack(registries,metadata.get("returned")));
            if(!expected.metadata().equals(metadata)||!expected.journalOwners().equals(entry.owners()))throw new IOException("Engine plan digest or capsule mismatch");
            return expected;
        }catch(IllegalArgumentException error){throw new IOException("Malformed engine transfer request",error);}
    }
}
