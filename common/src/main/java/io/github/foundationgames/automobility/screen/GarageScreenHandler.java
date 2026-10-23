package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.block.GarageBlock;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.GarageService;
import io.github.foundationgames.automobility.entity.GarageBatchPlan;
import io.github.foundationgames.automobility.entity.GarageStoredTransaction;
import io.github.foundationgames.automobility.entity.GarageStorage;
import io.github.foundationgames.automobility.entity.GarageTransferRuntime;
import io.github.foundationgames.automobility.entity.GaragePlacementPolicy;
import io.github.foundationgames.automobility.entity.GarageReturnPlacement;
import io.github.foundationgames.automobility.entity.GarageTestDriveService;
import io.github.foundationgames.automobility.item.AutomobileItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import io.github.foundationgames.automobility.item.NewVehicleContent;
import io.github.foundationgames.automobility.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Function;

/** A server-owned virtual draft. Only a successful batch Apply can change real parts or inventory. */
public class GarageScreenHandler extends AbstractContainerMenu {
    public static final int LIVE=0, STORED=1, CATALOG=2, NONE=3, MAX_CHOICES=128, MAX_PART_OPTIONS=64;
    public static final int DISPLAY_FRAME=0, DISPLAY_WHEEL=1, DISPLAY_ENGINE=2, PENDING_ENGINE=3, PENDING_WHEEL=4, RETURNED_ENGINE=5;
    public static final int PLAYER_SLOT_START=5, PLAYER_HOTBAR_START=32, PLAYER_SLOT_END=41;
    public static final int ENGINE_ACTION_BASE=100, WHEEL_ACTION_BASE=200, VEHICLE_ACTION_BASE=1000, PHYSICAL_ENGINE_ACTION_BASE=300, ENGINE_CONFIRM_BASE=10000;
    public enum Action { APPLY, CANCEL, PARK, DEPLOY, ASSEMBLE, TEST_DRIVE }
    public record ActionAvailability(boolean enabled, GarageService.Status reason) {}
    public enum Part { FRAME, ENGINE, WHEELS }
    public record ExchangeLine(Part part, ItemStack icon, Component name, int count, int available) {
        public ExchangeLine {
            if(count<=0||available < -1) throw new IllegalArgumentException("Invalid exchange counts");
            icon=icon.copy(); icon.setCount(1); name=name.copy();
        }
        @Override public ItemStack icon() { return icon.copy(); }
        @Override public Component name() { return name.copy(); }
    }
    public record ExchangeSummary(List<ExchangeLine> debits, List<ExchangeLine> credits,
                                  GarageService.Status reason, boolean assembly, boolean creativeFree) {
        public ExchangeSummary { debits=List.copyOf(debits); credits=List.copyOf(credits); }
    }
    public record Requirement(ItemStack icon, int available, int required) {
        public Requirement { icon=icon.copy(); }
        @Override public ItemStack icon() { return icon.copy(); }
    }
    public record PartOption(Identifier id, int buttonId, ItemStack icon, int available, int required,
                             boolean installed, boolean selected, boolean enabled, GarageService.Status reason, Part part) {
        public PartOption { icon=icon.copy(); }
        public PartOption(Identifier id,int buttonId,ItemStack icon,int available,int required,boolean installed,boolean selected,boolean enabled,GarageService.Status reason){this(id,buttonId,icon,available,required,installed,selected,enabled,reason,buttonId<WHEEL_ACTION_BASE?Part.ENGINE:Part.WHEELS);}
        @Override public ItemStack icon() { return icon.copy(); }
        public ItemStack template() { return icon(); }
        public int actionId() { return buttonId; }
        public Component name() { return Component.translatable((part==Part.ENGINE ? "engine." : "wheel.") + id.getNamespace()+"."+id.getPath()).append(buttonId>=PHYSICAL_ENGINE_ACTION_BASE&&buttonId<PHYSICAL_ENGINE_ACTION_BASE+36?Component.translatable("garage.automobility.engine_source_slot",buttonId-PHYSICAL_ENGINE_ACTION_BASE+1):Component.empty()); }
    }
    public record VehicleOption(UUID id, int buttonId, Component name, int kind, ItemStack frameIcon, boolean selected, boolean owned,
                                Identifier frameId, GarageService.Components components, RearAttachmentType<?> rearAttachment, FrontAttachmentType<?> frontAttachment) {
        public VehicleOption { frameIcon=frameIcon.copy(); }
        public VehicleOption(UUID id,int buttonId,Component name,int kind,ItemStack frameIcon,boolean selected,boolean owned) {
            this(id,buttonId,name,kind,frameIcon,selected,owned,Automobility.rl("empty"),null,RearAttachmentType.EMPTY,FrontAttachmentType.EMPTY);
        }
        @Override public ItemStack frameIcon() { return frameIcon.copy(); }
        public int actionId() { return buttonId; }
    }
    private record Memory(UUID owned, UUID catalog, boolean catalogMode) {}
    private record StoredSelection(GarageStorage.Entry entry, GarageService.Components components, int drivingMode) {}
    private static Predicate<Player> clientCompatibility=player->false;
    private static final Map<Player,Long> LAST_OPEN=new WeakHashMap<>();
    private static final Map<Player,Memory> LAST_SELECTION=new WeakHashMap<>();
    public static void setClientCompatibility(Predicate<Player> predicate) { clientCompatibility=java.util.Objects.requireNonNull(predicate); }
    private final Level level;
    private final Player owner;
    private final OpeningData opening;
    private final SimpleContainer display=new SimpleContainer(6);
    private final DataSlot status=DataSlot.standalone(), selected=DataSlot.standalone(), kind=DataSlot.standalone();
    private final DataSlot catalog=DataSlot.standalone(), mode=DataSlot.standalone(), requiredCount=DataSlot.standalone();
    private final List<Identifier> engineIds, wheelIds;
    private final List<DataSlot> engineCounts=new ArrayList<>(), engineReasons=new ArrayList<>(), wheelCounts=new ArrayList<>(), wheelReasons=new ArrayList<>();
    private final List<DataSlot> actionReasons=new ArrayList<>();
    private final List<DataSlot> recipeCounts=new ArrayList<>();
    private final Map<UUID,StoredSelection> storedSelections=new HashMap<>();
    private final Map<UUID,GarageService.Components> confirmedComponents=new HashMap<>();
    private GarageService.Components expected;
    private GarageStorage.Entry stored;

    private final DataSlot engineSource=DataSlot.standalone(),engineRevision=DataSlot.standalone(),engineOwnerReason=DataSlot.standalone();
    private final List<DataSlot> physicalEngineReasons=new ArrayList<>();
    private io.github.foundationgames.automobility.entity.GarageEnginePlan.Frozen engineDraft;
    private int nextEngineRevision;
    private boolean closed, committing;
    private GarageService.Status feedback;
    private long feedbackUntil, nextPlacementCheck=Long.MIN_VALUE;
    private GarageService.Components probedParts;
    private boolean placementClear;

    public GarageScreenHandler(int id, Inventory playerInventory, OpeningData data) {
        super(Automobility.GARAGE_SCREEN.require(),id);
        owner=playerInventory.player; level=owner.level(); opening=data;
        engineIds=data.engineIds().isEmpty()?optionIds(level,true):data.engineIds();
        wheelIds=data.wheelIds().isEmpty()?optionIds(level,false):data.wheelIds();
        for(int i=0;i<5;i++) addSlot(new GarageReadonlySlot(display,i));
        for(int row=0;row<3;row++) for(int col=0;col<9;col++)
            addSlot(new Slot(playerInventory,col+row*9+9,426+col*18,228+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(playerInventory,col,426+col*18,286));
        // Appended after ordinary inventory: preserve all existing player menu slot indices.
        addSlot(new GarageReadonlySlot(display,RETURNED_ENGINE));
        for(var slot:List.of(engineSource,engineRevision,engineOwnerReason))addDataSlot(slot);
        for(int i=0;i<36;i++){var dataSlot=DataSlot.standalone();physicalEngineReasons.add(dataSlot);addDataSlot(dataSlot);}
        for(var slot:List.of(status,selected,kind,catalog,mode,requiredCount)) addDataSlot(slot);
        for(int i=0;i<3;i++) { var count=DataSlot.standalone(); recipeCounts.add(count); addDataSlot(count); }
        for(var action:Action.values()) { var slot=DataSlot.standalone(); slot.set(GarageService.Status.PROTECTED.ordinal()); actionReasons.add(slot); addDataSlot(slot); }
        addOptionSlots(engineIds,engineCounts,engineReasons); addOptionSlots(wheelIds,wheelCounts,wheelReasons);
        selected.set(-1); kind.set(NONE);
        if(!level.isClientSide()) selectDesired(data.desired(),data.catalogMode());
    }
    private void addOptionSlots(List<Identifier> ids,List<DataSlot> counts,List<DataSlot> reasons) {
        for(var ignored:ids) {
            var count=DataSlot.standalone(); var reason=DataSlot.standalone(); reason.set(GarageService.Status.MISSING_PARTS.ordinal());
            counts.add(count); reasons.add(reason); addDataSlot(count); addDataSlot(reason);
        }
    }
    private static List<Identifier> optionIds(Level level,boolean engine) {
        return (engine?level.registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).listElements().filter(ref->!ref.value().isEmpty()).map(ref->ref.unwrapKey().orElseThrow().identifier())
                :level.registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).listElements().filter(ref->!ref.value().isEmpty()).map(ref->ref.unwrapKey().orElseThrow().identifier()))
                .sorted(Comparator.comparing(Identifier::toString)).limit(MAX_PART_OPTIONS).toList();
    }
    public static boolean open(Level level,BlockPos pos,Player player) {
        Memory memory; synchronized(LAST_SELECTION) { memory=LAST_SELECTION.get(player); }
        return open(level,pos,player,false,memory==null?null:memory.catalogMode()?memory.catalog():memory.owned(),memory!=null&&memory.catalogMode());
    }
    private static boolean open(Level level,BlockPos pos,Player player,boolean serverCommitReopen,UUID desired,boolean showCatalog) {
        return open(level,pos,player,serverCommitReopen,desired,showCatalog,false);
    }
    /** Only a server-owned successful return may request the tuning view. */
    public static boolean openTestDriveReturn(ServerPlayer player,BlockPos pos,UUID desired,boolean resumeTuning) {
        if(desired==null||player.distanceToSqr(Vec3.atCenterOf(pos))>64||!(player.level().getEntity(desired) instanceof AutomobileEntity car)
                ||!car.getVehicleAccess().isOwner(player.getUUID())||car.distanceToSqr(Vec3.atCenterOf(pos))>36||car.distanceToSqr(player)>64) return false;
        if(!open(player.level(),pos,player,true,desired,false,resumeTuning)) return false;
        if(player.containerMenu instanceof GarageScreenHandler menu&&desired.equals(menu.selectedChoiceId())
                &&!menu.isCatalog()&&!menu.isStored()&&menu.stillValid(player)) return true;
        if(player.containerMenu instanceof GarageScreenHandler) player.closeContainer();
        return false;
    }
    private static boolean open(Level level,BlockPos pos,Player player,boolean serverCommitReopen,UUID desired,boolean showCatalog,boolean resumeTuning) {
        if(level.isClientSide()) return true;
        if(!(level instanceof ServerLevel server)||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)) return false;
        if(!clientCompatibility.test(player)) { player.sendOverlayMessage(Component.translatable("garage.automobility.status.client_required")); return false; }
        if(!serverCommitReopen) synchronized(LAST_OPEN) {
            long now=level.getGameTime(); var previous=LAST_OPEN.get(player);
            if(previous!=null&&now>=previous&&now-previous<10) return false;
            LAST_OPEN.put(player,now);
        }
        var choices=new ArrayList<Choice>();
        var cars=level.getEntitiesOfClass(AutomobileEntity.class,new AABB(pos).inflate(GarageBlock.SERVICE_RADIUS),
                car->car.isAlive()&&!car.isDecorative()&&car.distanceToSqr(Vec3.atCenterOf(pos))<=36&&car.getVehicleAccess().mayConfigure(player.getUUID()));
        for(var car:boundedLiveChoices(cars,AutomobileEntity::getUUID,desired)) choices.add(new Choice(car.getUUID(),GarageService.displayName(car),LIVE,-1,
                car.getVehicleDrivingMode().id(),car.getRearAttachmentType().id(),car.getFrontAttachmentType().id(),
                componentId(car.garageComponents(),Part.FRAME),componentId(car.garageComponents(),Part.WHEELS),componentId(car.garageComponents(),Part.ENGINE),
                car.getVehicleAccess().isOwner(player.getUUID())));
        int storedCount;
        try {
            var entries=GarageStorage.list(server,player.getUUID()); storedCount=entries.size();
            for(var entry:entries.stream().limit(GarageStorage.MAX_STORED).toList())
                if(choices.stream().noneMatch(c->c.id().equals(entry.id()))) choices.add(new Choice(entry.id(),Component.literal(entry.name()),STORED,-1,
                        entry.entity().getIntOr("vehicleDrivingMode",0),parseId(entry.entity().getCompoundOrEmpty("rearAttachment").getStringOr("type","automobility:empty")),
                        parseId(entry.entity().getCompoundOrEmpty("frontAttachment").getStringOr("type","automobility:empty")),
                        parseId(entry.entity().getStringOr("frame","automobility:empty")),
                        parseId(entry.entity().getStringOr("wheels","automobility:empty")),parseId(entry.entity().getStringOr("engine","automobility:empty")),true));
        } catch(IOException error) { player.sendOverlayMessage(Component.translatable("garage.automobility.status.storage_error")); return false; }
        for(int i=0;i<AutomobileItem.PREFABS.size()&&choices.size()<MAX_CHOICES;i++) {
            var prefab=AutomobileItem.PREFABS.get(i);
            if(!NewVehicleContent.allowsPrefab(prefab,level.registryAccess())) continue;
            var key=(prefab.prefabName().map(Object::toString).orElse("")+":"+i).getBytes(StandardCharsets.UTF_8);
            choices.add(new Choice(UUID.nameUUIDFromBytes(key),prefab.asStack().getHoverName(),CATALOG,i,0,Automobility.rl("empty"),Automobility.rl("empty"),
                    prefab.frame().identifier(),prefab.wheel().identifier(),prefab.engine().identifier(),false));
        }
        var data=new OpeningData(pos.immutable(),choices,optionIds(level,true),optionIds(level,false),desired,showCatalog,storedCount,resumeTuning);
        player.openMenu(Platform.get().extendedMenuProvider(Component.translatable("block.automobility.garage"),
                (id,inv,user)->new GarageScreenHandler(id,inv,data),user->data));
        return true;
    }
    /** The bounded list must retain a server-requested exact vehicle even in a crowded workshop. */
    static <T> List<T> boundedLiveChoices(List<T> candidates,Function<T,UUID> identity,UUID desired) {
        return candidates.stream().sorted(Comparator.<T,Boolean>comparing(value->!identity.apply(value).equals(desired))
                .thenComparing(value->identity.apply(value).toString())).limit(32).toList();
    }
    private static Identifier parseId(String id) { var value=Identifier.tryParse(id); return value==null?Automobility.rl("empty"):value; }
    private static Identifier componentId(GarageService.Components parts,Part part) {
        if(parts==null) return Automobility.rl("empty");
        return switch(part) {
            case FRAME -> parts.frame().unwrapKey().map(key->key.identifier()).orElse(Automobility.rl("empty"));
            case WHEELS -> parts.wheel().unwrapKey().map(key->key.identifier()).orElse(Automobility.rl("empty"));
            case ENGINE -> parts.engine().unwrapKey().map(key->key.identifier()).orElse(Automobility.rl("empty"));
        };
    }
    private Choice choice() { int index=selected.get(); return index>=0&&index<opening.choices().size()?opening.choices().get(index):null; }
    public UUID selectedChoiceId() { return choice()==null?null:choice().id(); }
    public AutomobileEntity selectedVehicle() {
        if(kind.get()!=LIVE||choice()==null) return null;
        var entity=level.getEntity(choice().id()); return entity instanceof AutomobileEntity car&&car.isAlive()?car:null;
    }
    public boolean isAssembler() { return level.getBlockState(opening.pos()).is(AutomobilityBlocks.AUTOMOBILE_ASSEMBLER.require()); }
    @Override public boolean stillValid(Player player) {
        if(closed||player!=owner||!player.isAlive()||player.isSpectator()||player.level()!=level) return false;
        if(level.isClientSide()) return true;
        return level.hasChunkAt(opening.pos())&&player.distanceToSqr(Vec3.atCenterOf(opening.pos()))<=64&&player.mayBuild()
                &&level.mayInteract(player,opening.pos())&&(isAssembler()||level.getBlockState(opening.pos()).is(AutomobilityBlocks.GARAGE.require()));
    }
    private GarageService.Status accessReason(AutomobileEntity car) {
        if(car!=null&&io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess.blocked(car))return GarageService.Status.RECOVERY_REQUIRED;
        if(!stillValid(owner)) return GarageService.Status.PROTECTED;
        if(car==null) return GarageService.Status.NO_VEHICLE;
        boolean authorized=!car.isDecorative()&&car.getVehicleAccess().mayConfigure(owner.getUUID())&&(!car.isInvulnerable()||owner.isCreative());
        boolean loaded=level.hasChunkAt(car.blockPosition());
        boolean inRange=car.distanceToSqr(Vec3.atCenterOf(opening.pos()))<=36&&car.distanceToSqr(owner)<=64;
        boolean areaAccessible=authorized&&loaded&&inRange&&level.mayInteract(owner,car.blockPosition());
        return GarageService.accessStatus(true,authorized,loaded,inRange,areaAccessible);
    }
    private void remember() {
        var id=selectedChoiceId(); if(id==null) return;
        synchronized(LAST_SELECTION) {
            var old=LAST_SELECTION.get(owner);
            LAST_SELECTION.put(owner,isCatalog()?new Memory(old==null?null:old.owned(),id,true):new Memory(id,old==null?null:old.catalog(),false));
        }
    }
    private void selectDesired(UUID desired,boolean showCatalog) {
        if(desired!=null) for(int i=0;i<opening.choices().size();i++) if(opening.choices().get(i).id().equals(desired)) { select(i); return; }
        selectFirst(showCatalog);
    }
    private void selectFirst(boolean showCatalog) {
        Memory memory; synchronized(LAST_SELECTION) { memory=LAST_SELECTION.get(owner); }
        var desired=memory==null?null:showCatalog?memory.catalog():memory.owned();
        if(desired!=null) for(int i=0;i<opening.choices().size();i++) if(opening.choices().get(i).id().equals(desired)&&(opening.choices().get(i).kind()==CATALOG)==showCatalog) { select(i); return; }
        catalog.set(showCatalog?1:0);
        for(int i=0;i<opening.choices().size();i++) if((opening.choices().get(i).kind()==CATALOG)==showCatalog) { select(i); return; }
        selected.set(-1); kind.set(NONE); expected=null; stored=null; clearDraft(); refreshDisplay(); refreshStatus();
    }
    private void step(int delta) {
        int count=opening.choices().size(); if(count==0) return;
        for(int step=1;step<=count;step++) { int i=Math.floorMod(selected.get()+delta*step,count); if((opening.choices().get(i).kind()==CATALOG)==isCatalog()) { select(i); return; } }
    }
    private void select(int index) {
        selected.set(index); var selectedChoice=choice(); kind.set(selectedChoice.kind()); catalog.set(selectedChoice.kind()==CATALOG?1:0);
        mode.set(selectedChoice.drivingMode()); expected=null; stored=null; clearDraft();
        try {
            if(kind.get()==LIVE) { var car=selectedVehicle(); if(car!=null) expected=car.garageComponents(); }
            else if(kind.get()==STORED&&level instanceof ServerLevel server) {
                var cached=storedSelections.get(selectedChoice.id());
                if(cached==null) {
                    if(storedSelections.size()>=GarageStorage.MAX_STORED) throw new IOException("Too many selected storage records");
                    var entry=GarageStorage.get(server,owner.getUUID(),selectedChoice.id());
                    cached=storedSelection(entry);
                    storedSelections.put(selectedChoice.id(),cached);
                }
                stored=cached.entry();expected=cached.components();mode.set(cached.drivingMode());
            } else if(kind.get()==CATALOG) expected=prefabComponents(selectedChoice.prefab());
        } catch(IOException|RuntimeException error) { feedback=GarageService.Status.STORAGE_ERROR; feedbackUntil=level.getGameTime()+60; }
        remember(); refreshDisplay(); refreshStatus();
    }
    private StoredSelection storedSelection(GarageStorage.Entry entry) {
        var metadata=entry==null?null:GarageStoredMetadata.read(entry.entity());
        return new StoredSelection(entry,metadata==null||entry.state()==GarageStorage.State.DEPLOYED?null:metadata.resolve(level.registryAccess()),
                metadata==null?0:metadata.drivingMode());
    }
    private GarageService.Components prefabComponents(int index) {
        if(index<0||index>=AutomobileItem.PREFABS.size()) return null;
        var prefab=AutomobileItem.PREFABS.get(index); var registry=level.registryAccess();
        var frame=registry.lookupOrThrow(AutomobileFrame.REGISTRY).get(prefab.frame());
        var wheel=registry.lookupOrThrow(AutomobileWheel.REGISTRY).get(prefab.wheel()); var engine=registry.lookupOrThrow(AutomobileEngine.REGISTRY).get(prefab.engine());
        return frame.isEmpty()||wheel.isEmpty()||engine.isEmpty()?null:new GarageService.Components(frame.get(),wheel.get(),engine.get());
    }
    private ItemStack template(Identifier id,boolean engine) {
        if(engine) {
            var key=net.minecraft.resources.ResourceKey.create(AutomobileEngine.REGISTRY,id);
            var part=level.registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).get(key);
            return part.isEmpty()||part.get().value().isEmpty()?ItemStack.EMPTY:AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key);
        }
        var key=net.minecraft.resources.ResourceKey.create(AutomobileWheel.REGISTRY,id);
        var part=level.registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).get(key);
        return part.isEmpty()||part.get().value().isEmpty()?ItemStack.EMPTY:AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key);
    }
    private int available(ItemStack template) {
        int count=0; for(int i=0;i<36;i++) { var stack=owner.getInventory().getItem(i); if(ItemStack.isSameItemSameComponents(stack,template)) count+=stack.getCount(); } return count;
    }
    private int wheelCount() { return expected==null?0:expected.frame().value().model().wheelBase().wheelCount(); }
    private GarageService.Batch draft(boolean assembly) {
        return GarageService.batch(expected,display.getItem(PENDING_ENGINE),display.getItem(PENDING_WHEEL),level.registryAccess(),assembly);
    }
    private GarageBatchPlan inventoryPlan(GarageService.Batch batch) {
        return GarageBatchPlan.plan(GarageBatchPlan.snapshot(owner.getInventory()),batch.debits(),batch.credits());
    }
    private GarageService.Status baseReason() {
        if(!stillValid(owner)) return GarageService.Status.PROTECTED;
        if(choice()==null) return GarageService.Status.NO_VEHICLE;
        return expected==null||!expected.complete()?GarageService.Status.INVALID_PART:GarageService.Status.READY;
    }
    private GarageService.Status liveReason() {
        var car=selectedVehicle(); var access=accessReason(car); if(access!=GarageService.Status.READY) return access;
        if(!expected.equals(car.garageComponents())) return GarageService.Status.STALE;
        return GarageService.operatingStatus(car);
    }
    private GarageService.Status storedReason() {
        if(!isAssembler()) return GarageService.Status.ASSEMBLER_REQUIRED;
        if(stored==null||stored.state()!=GarageStorage.State.PARKED) return GarageService.Status.RECOVERY_REQUIRED;
        if(!(level instanceof ServerLevel checked)||!GarageTransferRuntime.canStoredWheelTune(checked,stored)) return GarageService.Status.INTEGRATION;
        if(!(level instanceof ServerLevel server)||!GarageStorage.originalChunkLoaded(server,stored)) return GarageService.Status.UNLOADED;
        return GarageStorage.uuidPresent(server,stored.id())?GarageService.Status.DUPLICATE:GarageService.Status.READY;
    }
    private GarageService.Status storedDeployReason(){
        if(!isAssembler())return GarageService.Status.ASSEMBLER_REQUIRED;
        if(stored==null||stored.state()!=GarageStorage.State.PARKED)return GarageService.Status.RECOVERY_REQUIRED;
        if(!(level instanceof ServerLevel server)||!GarageTransferRuntime.canDeploy(server,stored))return GarageService.Status.INTEGRATION;
        if(!GarageStorage.originalChunkLoaded(server,stored))return GarageService.Status.UNLOADED;
        return GarageStorage.uuidPresent(server,stored.id())?GarageService.Status.DUPLICATE:GarageService.Status.READY;
    }
    private GarageService.Status reason(Action action) {
        var base=baseReason(); if(base!=GarageService.Status.READY) return base;
        if(action==Action.CANCEL) return hasPendingChanges()?GarageService.Status.READY:GarageService.Status.NO_CHANGES;
        if(action==Action.APPLY) {
            if(isCatalog()) return GarageService.Status.CATALOG;
            if(!hasPendingChanges()) return GarageService.Status.NO_CHANGES;
            var target=kind.get()==STORED?storedReason():liveReason(); if(target!=GarageService.Status.READY) return target;
            if(hasEngineSource())return physicalEngineReason(selectedEngineSourceSlot());
            var batch=draft(false); return batch.status()==GarageService.Status.READY?inventoryPlan(batch).status():batch.status();
        }
        if(action==Action.TEST_DRIVE) {
            if(hasPendingChanges()) return GarageService.Status.PENDING_CHANGES;
            if(!owner.containerMenu.getCarried().isEmpty()||owner.isPassenger()) return GarageService.Status.OCCUPIED;
            if(!isAssembler()&&!owner.isCreative()) return GarageService.Status.ASSEMBLER_REQUIRED;
            if(kind.get()==STORED) return reason(Action.DEPLOY);
            if(kind.get()==CATALOG) return reason(Action.ASSEMBLE);
            var live=liveReason(); if(live!=GarageService.Status.READY) return live;
            return selectedVehicle().getVehicleAccess().isOwner(owner.getUUID())?GarageService.Status.READY:GarageService.Status.PROTECTED;
        }
        if(action==Action.PARK) {
            if(kind.get()!=LIVE) return GarageService.Status.STORED;
            if(hasPendingChanges()) return GarageService.Status.PENDING_CHANGES;
            if(!isAssembler()) return GarageService.Status.ASSEMBLER_REQUIRED;
            var live=liveReason(); if(live!=GarageService.Status.READY) return live;
            if(!selectedVehicle().getVehicleAccess().isOwner(owner.getUUID())) return GarageService.Status.PROTECTED;
            if(!GarageTransferRuntime.canStore(expected)) return GarageService.Status.INTEGRATION;
            return opening.storedCount()>=GarageStorage.MAX_STORED?GarageService.Status.GARAGE_FULL:GarageService.Status.READY;
        }
        if(action==Action.DEPLOY) {
            if(kind.get()!=STORED) return GarageService.Status.STORED;
            if(hasPendingChanges()) return GarageService.Status.PENDING_CHANGES;
            var target=storedDeployReason(); return target!=GarageService.Status.READY?target:placementAvailable(expected)?GarageService.Status.READY:GarageService.Status.BLOCKED;
        }
        if(!isCatalog()) return GarageService.Status.CATALOG;
        if(!isAssembler()&&!owner.isCreative()) return GarageService.Status.ASSEMBLER_REQUIRED;
        var batch=draft(true); if(batch.status()!=GarageService.Status.READY) return batch.status();
        if(!owner.isCreative()) { var inventory=inventoryPlan(batch); if(!inventory.ready()) return inventory.status(); }
        return placementAvailable(batch.replacement())?GarageService.Status.READY:GarageService.Status.BLOCKED;
    }
    private boolean placementAvailable(GarageService.Components parts) {
        long now=level.getGameTime();
        if(probedParts!=null&&probedParts.equals(parts)&&now<nextPlacementCheck) return placementClear;
        placementClear=owner instanceof ServerPlayer player&&GarageReturnPlacement.findDeployment(player,parts,opening.pos()).isPresent();
        probedParts=parts; nextPlacementCheck=now+10;
        return placementClear;
    }
    private GarageService.Status optionReason(boolean engine,ItemStack item) {
        var base=baseReason(); if(base!=GarageService.Status.READY) return base;
        if(kind.get()==STORED){var storedGuard=storedReason();if(storedGuard!=GarageService.Status.READY)return storedGuard;}
        if(kind.get()==LIVE) {
            var car=selectedVehicle(); var access=accessReason(car); if(access!=GarageService.Status.READY) return access;
            if(!expected.equals(car.garageComponents())) return GarageService.Status.STALE;
        }
        if(item.isEmpty()) return GarageService.Status.INVALID_PART;
        var installed=engine?expected.engineStack():expected.wheelStack();
        if(ItemStack.isSameItemSameComponents(item,installed)) return GarageService.Status.READY;
        int count=engine?1:wheelCount(); if(count<=0||count>item.getMaxStackSize()) return GarageService.Status.INVALID_PART;
        var engineDraft=engine?item:display.getItem(PENDING_ENGINE); var wheelDraft=engine?display.getItem(PENDING_WHEEL):item.copy();
        if(!engine) wheelDraft.setCount(count);
        var batch=GarageService.batch(expected,engineDraft,wheelDraft,level.registryAccess(),isCatalog());
        if(batch.status()!=GarageService.Status.READY&&batch.status()!=GarageService.Status.NO_CHANGES) return batch.status();
        return (isCatalog()&&owner.isCreative())||available(item)>=count?GarageService.Status.READY:GarageService.Status.MISSING_PARTS;
    }
    private void refreshOptions(boolean engine) {
        var ids=engine?engineIds:wheelIds; var counts=engine?engineCounts:wheelCounts; var reasons=engine?engineReasons:wheelReasons;
        for(int i=0;i<ids.size();i++) { var item=template(ids.get(i),engine); counts.get(i).set(available(item)); reasons.get(i).set(optionReason(engine,item).ordinal()); }
    }
    private void refreshStatus() {
        for(int i=0;i<36;i++)physicalEngineReasons.get(i).set(physicalEngineReason(i).ordinal());
        for(var action:Action.values()) actionReasons.get(action.ordinal()).set(reason(action).ordinal());
        if(selectedVehicle()!=null) mode.set(selectedVehicle().getVehicleDrivingMode().id());
        requiredCount.set(wheelCount()); refreshOptions(true); refreshOptions(false);
        var recipe=draft(true).replacement();
        var ingredients=recipe==null?List.<ItemStack>of():List.of(recipe.frameStack(),recipe.engineStack(),recipe.wheelStack());
        for(int i=0;i<recipeCounts.size();i++) recipeCounts.get(i).set(ingredients.isEmpty()?0:available(ingredients.get(i)));
        var value=hasPendingChanges()?reason(isCatalog()?Action.ASSEMBLE:Action.APPLY):isCatalog()?GarageService.Status.CATALOG:isStored()?GarageService.Status.STORED:GarageService.Status.NO_CHANGES;
        if(choice()==null) value=GarageService.Status.NO_VEHICLE;
        else if(!hasPendingChanges()&&kind.get()==LIVE) {
            var base=baseReason(); var live=base==GarageService.Status.READY?liveReason():base;
            if(live!=GarageService.Status.READY) value=live;
        }
        else if(!hasPendingChanges()&&kind.get()==STORED&&baseReason()==GarageService.Status.INTEGRATION)
            value=GarageService.Status.INTEGRATION;
        status.set(feedback!=null&&level.getGameTime()<feedbackUntil?feedback.ordinal():value.ordinal());
    }
    private void refreshDisplay() {
        var id=selectedChoiceId();
        var binding=id==null?null:GarageReadonlySnapshotBinding.Token.fresh(id);
        display.setItem(DISPLAY_FRAME,expected==null||binding==null?ItemStack.EMPTY:GarageReadonlySnapshotBinding.stamp(expected.frameStack(),binding));
        display.setItem(DISPLAY_WHEEL,expected==null||binding==null?ItemStack.EMPTY:GarageReadonlySnapshotBinding.stamp(expected.wheelStack(),binding));
        display.setItem(DISPLAY_ENGINE,expected==null||binding==null?ItemStack.EMPTY:GarageReadonlySnapshotBinding.stamp(expected.engineStack(),binding));
        rememberConfirmedComponents(expected);
    }
    private void clearDraft() { engineSource.set(0);engineRevision.set(0);engineOwnerReason.set(0);engineDraft=null;display.setItem(RETURNED_ENGINE,ItemStack.EMPTY);display.setItem(PENDING_ENGINE,ItemStack.EMPTY); display.setItem(PENDING_WHEEL,ItemStack.EMPTY); probedParts=null; nextPlacementCheck=Long.MIN_VALUE; }
    @Override public void broadcastChanges() { if(!level.isClientSide()&&!closed&&!committing) refreshStatus(); super.broadcastChanges(); }
    private boolean finish(GarageService.Status result) {
        feedback=result; feedbackUntil=level.getGameTime()+40; refreshDisplay(); refreshStatus(); super.broadcastChanges(); return result==GarageService.Status.APPLIED||result==GarageService.Status.CANCELLED||result==GarageService.Status.READY;
    }
    private boolean reopen(UUID desired,boolean showCatalog) { return open(level,opening.pos(),owner,true,desired,showCatalog); }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(level.isClientSide()||player!=owner||player.containerMenu!=this||!stillValid(player)||committing) return false;
        feedback=null;
        if(button==7) { clearDraft(); return finish(GarageService.Status.CANCELLED); }
        if(button>=PHYSICAL_ENGINE_ACTION_BASE&&button<PHYSICAL_ENGINE_ACTION_BASE+36)return chooseEngineSource(button-PHYSICAL_ENGINE_ACTION_BASE);
        if(button>=ENGINE_ACTION_BASE&&button<ENGINE_ACTION_BASE+engineIds.size()) return isCatalog()?choosePart(true,button-ENGINE_ACTION_BASE):false;
        if(button>=WHEEL_ACTION_BASE&&button<WHEEL_ACTION_BASE+wheelIds.size()) return choosePart(false,button-WHEEL_ACTION_BASE);
        if(button>=VEHICLE_ACTION_BASE&&button<VEHICLE_ACTION_BASE+opening.choices().size()) {
            int index=button-VEHICLE_ACTION_BASE;
            if(index==selected.get()) return true;
            if(hasPendingChanges()) return finish(GarageService.Status.PENDING_CHANGES);
            select(index); super.broadcastChanges(); return true;
        }
        if(button==1||button==2||button==5) {
            if(hasPendingChanges()) return finish(GarageService.Status.PENDING_CHANGES);
            if(button==5) selectFirst(!isCatalog()); else step(button==1?-1:1);
            super.broadcastChanges(); return true;
        }
        try {
            committing=true;
            if(button>=ENGINE_CONFIRM_BASE&&button<ENGINE_CONFIRM_BASE+8192)return confirmEngine(button-ENGINE_CONFIRM_BASE);
            if(button==0) return hasEngineSource()?prepareEngine():apply();
            if(button==3) return park();
            if(button==4) return deploy();
            if(button==6) return assemble();
            if(button==8) return testDrive();
        } catch(IOException|RuntimeException error) { return finish(GarageService.Status.STORAGE_ERROR); }
        finally { committing=false; }
        return false;
    }
    private boolean choosePart(boolean engine,int index) {
        if(hasEngineSource())return finish(GarageService.Status.PENDING_CHANGES);
        var ids=engine?engineIds:wheelIds; var item=template(ids.get(index),engine);
        var reason=optionReason(engine,item); if(reason!=GarageService.Status.READY) return finish(reason);
        var installed=engine?expected.engineStack():expected.wheelStack();
        if(ItemStack.isSameItemSameComponents(item,installed)) item=ItemStack.EMPTY;
        else item.setCount(engine?1:wheelCount());
        display.setItem(engine?PENDING_ENGINE:PENDING_WHEEL,item.copy()); probedParts=null; refreshStatus(); super.broadcastChanges(); return true;
    }
    public boolean hasEngineSource(){return engineSource.get()>0;}
    public int selectedEngineSourceSlot(){return engineSource.get()-1;}
    public boolean enginePrepared(){return hasEngineSource()&&engineRevision.get()>0;}
    public int engineConfirmationRevision(){return engineRevision.get();}
    public int applyButtonId(){return enginePrepared()?ENGINE_CONFIRM_BASE+engineRevision.get():0;}
    public ItemStack returnedEngineView(){return display.getItem(RETURNED_ENGINE).copy();}
    public io.github.foundationgames.automobility.automobile.garage.GarageEngineTransferParticipant.Reason engineOwnerReason(){
        var values=io.github.foundationgames.automobility.automobile.garage.GarageEngineTransferParticipant.Reason.values();var index=engineOwnerReason.get();return values[Math.clamp(index,0,values.length-1)];
    }
    private GarageService.Status physicalEngineReason(int slot){
        if(isCatalog()||slot<0||slot>=36)return GarageService.Status.CATALOG;
        var base=baseReason();if(base!=GarageService.Status.READY)return base;
        if(!display.getItem(PENDING_WHEEL).isEmpty())return GarageService.Status.PENDING_CHANGES;
        if(!GarageTransferRuntime.enginesAvailable())return GarageService.Status.INTEGRATION;
        var target=kind.get()==STORED?storedReason():liveReason();if(target!=GarageService.Status.READY)return target;
        var item=owner.getInventory().getItem(slot);if(item.isEmpty()||!item.is(AutomobilityItems.AUTOMOBILE_ENGINE.require()))return GarageService.Status.INVALID_PART;
        if(hasEngineSource()&&slot==selectedEngineSourceSlot()&&!ItemStack.matches(item,display.getItem(PENDING_ENGINE)))return GarageService.Status.STALE;
        return owner.containerMenu.getCarried().isEmpty()?GarageService.Status.READY:GarageService.Status.TAKE_OUTPUT;
    }
    private boolean chooseEngineSource(int slot){
        var reason=physicalEngineReason(slot);if(reason!=GarageService.Status.READY)return finish(reason);
        clearDraft();engineSource.set(slot+1);display.setItem(PENDING_ENGINE,owner.getInventory().getItem(slot).copy());
        return finish(GarageService.Status.READY);
    }
    private boolean prepareEngine()throws IOException{
        if(engineDraft!=null)return finish(GarageService.Status.READY); // duplicate prepare is never confirmation
        var reason=reason(Action.APPLY);if(reason!=GarageService.Status.READY)return finish(reason);
        if(!(owner instanceof ServerPlayer actor)||nextEngineRevision>=8191)return finish(GarageService.Status.PROTECTED);
        var result=GarageTransferRuntime.prepareEngine(actor,kind.get()==LIVE?selectedVehicle():null,kind.get()==STORED?stored:null,selectedEngineSourceSlot(),display.getItem(PENDING_ENGINE));
        engineOwnerReason.set(result.reason().ordinal());
        if(result.status()!=GarageService.Status.READY||result.frozen()==null)return finish(result.status());
        engineDraft=result.frozen();display.setItem(RETURNED_ENGINE,engineDraft.exchange().finalReturnedEngine());engineRevision.set(++nextEngineRevision);
        return finish(GarageService.Status.READY);
    }
    private boolean confirmEngine(int revision)throws IOException{
        if(engineDraft==null||revision!=engineRevision.get()||revision<=0||!(owner instanceof ServerPlayer actor))return finish(GarageService.Status.STALE);
        var reason=reason(Action.APPLY);if(reason!=GarageService.Status.READY)return finish(reason);
        var result=GarageTransferRuntime.confirmEngine(actor,engineDraft);
        if(result==GarageService.Status.APPLIED){
            if(kind.get()==STORED){stored=GarageStorage.get(actor.level(),owner.getUUID(),selectedChoiceId());var retained=storedSelection(stored);expected=retained.components();mode.set(retained.drivingMode());storedSelections.put(stored.id(),retained);}
            else expected=selectedVehicle().garageComponents();
            clearDraft();
        }
        return finish(result);
    }
    private boolean apply() throws IOException {
        var reason=reason(Action.APPLY); if(reason!=GarageService.Status.READY) return finish(reason);
        var batch=draft(false); var inventory=inventoryPlan(batch); if(!inventory.ready()) return finish(inventory.status());
        var parts=batch.replacement();
        if(kind.get()==STORED) {
            var server=(ServerLevel)level; var latest=GarageStorage.get(server,owner.getUUID(),selectedChoiceId());
            if(latest==null||stored==null||latest.state()!=GarageStorage.State.PARKED||!latest.entity().equals(stored.entity())) return finish(GarageService.Status.STALE);
            if(!GarageStorage.originalChunkLoaded(server,latest)) return finish(GarageService.Status.UNLOADED);
            if(GarageStorage.uuidPresent(server,latest.id())) return finish(GarageService.Status.DUPLICATE);
            if(!(owner instanceof ServerPlayer actor))return finish(GarageService.Status.PROTECTED);
            var result=GarageTransferRuntime.tuneStoredWheels(actor,latest,batch,inventory);
            stored=GarageStorage.get(server,owner.getUUID(),latest.id());
            var retained=storedSelection(stored);expected=retained.components();mode.set(retained.drivingMode());
            if(result==GarageService.Status.APPLIED)clearDraft();
            storedSelections.put(latest.id(),retained);
            return finish(result);
        } else {
            var car=selectedVehicle(); var access=accessReason(car); if(access!=GarageService.Status.READY) return finish(access);
            if(!expected.equals(car.garageComponents())||!inventory.matches(owner.getInventory())) return finish(GarageService.Status.STALE);
            var operating=GarageService.operatingStatus(car); if(operating!=GarageService.Status.READY) return finish(operating);
            try { car.setComponents(parts.frame(),parts.wheel(),parts.engine()); }
            catch(RuntimeException error) {
                // A callback may throw after the complete core change. Commit its real cost once if so.
                if(parts.equals(car.garageComponents())&&inventory.commit(owner.getInventory())) { expected=parts; clearDraft(); return finish(GarageService.Status.APPLIED); }
                if(!expected.equals(car.garageComponents())) car.setComponents(expected.frame(),expected.wheel(),expected.engine());
                return finish(GarageService.Status.STORAGE_ERROR);
            }
            if(!inventory.commit(owner.getInventory())) { car.setComponents(expected.frame(),expected.wheel(),expected.engine()); return finish(GarageService.Status.STALE); }
        }
        expected=parts; clearDraft(); return finish(GarageService.Status.APPLIED);
    }
    private boolean park() throws IOException {
        var reason=reason(Action.PARK); if(reason!=GarageService.Status.READY) return finish(reason);
        var car=selectedVehicle(); var server=(ServerLevel)level; var entries=GarageStorage.list(server,owner.getUUID());
        if(entries.size()>=GarageStorage.MAX_STORED&&entries.stream().noneMatch(entry->entry.id().equals(car.getUUID()))) return finish(GarageService.Status.GARAGE_FULL);
        if(!(owner instanceof ServerPlayer player))return finish(GarageService.Status.PROTECTED);
        var id=car.getUUID();var transferred=GarageTransferRuntime.store(player,car);
        return transferred==GarageService.Status.READY?reopen(id,false):finish(transferred);
    }
    private boolean testDrive() throws IOException {
        var reason=reason(Action.TEST_DRIVE); if(reason!=GarageService.Status.READY) return finish(reason);
        if(kind.get()==STORED) return deploy(true);
        if(kind.get()==CATALOG) return assemble(true);
        return drive(selectedVehicle());
    }
    private boolean drive(AutomobileEntity car) {
        if(!(owner instanceof ServerPlayer player)) return false;
        var result=GarageTestDriveService.start(player,car,opening.pos());
        if(result==GarageTestDriveService.Reason.STARTED) return true;
        if(player.containerMenu!=this) return false;
        if(car!=null&&car.isAlive()&&(kind.get()!=LIVE||!car.getUUID().equals(selectedChoiceId()))) {
            // Assembly/deployment already committed. Keep its exact real car selected if mounting was refused.
            player.sendOverlayMessage(Component.translatable(result.key()));
            return reopen(car.getUUID(),false);
        }
        return finish(switch(result) {
            case MOVING -> GarageService.Status.MOVING;
            case ENGINE_RUNNING -> GarageService.Status.ENGINE_RUNNING;
            case OCCUPIED, MOUNT_FAILED -> GarageService.Status.OCCUPIED;
            case UNLOADED -> GarageService.Status.UNLOADED;
            case BLOCKED -> GarageService.Status.BLOCKED;
            case OUT_OF_RANGE -> GarageService.Status.OUT_OF_RANGE;
            default -> GarageService.Status.PROTECTED;
        });
    }
    private boolean deploy() throws IOException { return deploy(false); }
    private boolean deploy(boolean testDrive) throws IOException {
        if(hasPendingChanges()) return finish(GarageService.Status.PENDING_CHANGES);
        if(kind.get()!=STORED) return finish(GarageService.Status.STORED);
        var target=storedDeployReason(); if(target!=GarageService.Status.READY) return finish(target);
        var server=(ServerLevel)level; var entry=GarageStorage.get(server,owner.getUUID(),selectedChoiceId());
        if(entry==null||stored==null||!entry.equals(stored)) return finish(GarageService.Status.STALE);
        if(!(owner instanceof ServerPlayer player))return finish(GarageService.Status.PROTECTED);
        // Re-evaluate geometry at the actual click, never use the periodic availability cache.
        var placement=GarageReturnPlacement.findDeployment(player,expected,opening.pos());
        if(placement.isEmpty())return finish(GarageService.Status.BLOCKED);
        var result=GarageTransferRuntime.deploy(player,entry,placement.get().car(),placement.get().yaw());
        if(result.status()!=GarageService.Status.READY||result.car()==null)return finish(result.status());
        return testDrive?drive(result.car()):reopen(entry.id(),false);
    }
    private boolean assemble() { return assemble(false); }
    private boolean assemble(boolean testDrive) {
        if(!isCatalog()||choice()==null) return finish(GarageService.Status.CATALOG);
        if(!isAssembler()&&!owner.isCreative()) return finish(GarageService.Status.ASSEMBLER_REQUIRED);
        var current=prefabComponents(choice().prefab()); if(current==null||!current.equals(expected)) return finish(GarageService.Status.STALE);
        var batch=draft(true); if(batch.status()!=GarageService.Status.READY) return finish(batch.status());
        var inventory=inventoryPlan(batch); if(!owner.isCreative()&&!inventory.ready()) return finish(inventory.status());
        var parts=batch.replacement(); var car=new AutomobileEntity(level); car.setComponents(parts.frame(),parts.wheel(),parts.engine());
        car.setVehicleAccess(car.getVehicleAccess().claim(owner.getUUID()));
        var prefab=AutomobileItem.PREFABS.get(choice().prefab());
        car.setVehicleLightMode(prefab.lightMode()); car.setVehicleSignalAutoCancelEnabled(prefab.signalAutoCancel()); car.setVehicleComfortSignalsEnabled(prefab.comfortSignals());
        if(!place(car)) return finish(GarageService.Status.BLOCKED);
        boolean reserved=!owner.isCreative();
        if(reserved&&!inventory.commit(owner.getInventory())) return finish(GarageService.Status.STALE);
        try {
            if(!((ServerLevel)level).addFreshEntity(car)) { car.discard(); if(reserved) refund(inventory); return finish(GarageService.Status.DUPLICATE); }
        } catch(RuntimeException error) { car.discard(); if(reserved) refund(inventory); return finish(GarageService.Status.STORAGE_ERROR); }
        clearDraft(); return testDrive?drive(car):reopen(car.getUUID(),false);
    }
    private void refund(GarageBatchPlan plan) {
        if(plan.rollback(owner.getInventory())) return;
        for(var reserved:plan.debits()) { var stack=reserved.copy(); owner.getInventory().add(stack); if(!stack.isEmpty()) owner.drop(stack,false,net.minecraft.util.Prediction.SERVER_ONLY); }
        owner.getInventory().setChanged();
    }
    /** Only loaded positions, the configured frame bounds, permissions, border and ground support. */
    private boolean place(AutomobileEntity car) {
        var state = level.getBlockState(opening.pos());
        var facing = state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        for (var direction : List.of(facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite())) for (int distance : new int[]{4, 5, 6}) {
            var pos = opening.pos().relative(direction, distance);
            car.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); car.setYRot(direction.toYRot()); car.setDeltaMovement(Vec3.ZERO); car.setOnGround(true);
            // Some production frames (including motorcars) extend below the entity's anchor.
            // Raise the actual entity, not merely the query box, until its lowest collider clears the ground.
            double lift = GaragePlacementPolicy.liftForGround(pos.getY(), placementBounds(car).minY);
            if (!Double.isFinite(lift)) continue;
            car.setPos(car.getX(), car.getY() + lift, car.getZ());
            // A newly materialized car must be usable from this workshop immediately.
            // The opposite side can be near the station yet beyond the player's service range.
            if (!GarageReturnPlacement.withinServiceRange(Vec3.atCenterOf(opening.pos()), car.position(), owner.position())) continue;
            var bounds = placementBounds(car).inflate(0.05, 0, 0.05);
            if (!level.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY - 1, bounds.minZ), BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)) || !level.getWorldBorder().isWithinBounds(bounds)) continue;
            boolean entitiesLoaded = true;
            for (int x = ((int)Math.floor(bounds.minX)) >> 4; x <= ((int)Math.floor(bounds.maxX)) >> 4; x++)
                for (int z = ((int)Math.floor(bounds.minZ)) >> 4; z <= ((int)Math.floor(bounds.maxZ)) >> 4; z++)
                    if (!((ServerLevel)level).areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(x, z))) entitiesLoaded = false;
            if (!entitiesLoaded) continue;
            if (!level.mayInteract(owner, pos) || !level.noCollision(car, bounds)) continue;
            if (level.noCollision(car, bounds.move(0, -0.2, 0))) continue;
            return true;
        }
        return false;
    }
    private AABB placementBounds(AutomobileEntity car) {
        var result = car.getBoundingBox();
        double yaw = Math.toRadians(-car.getYRot()), cos = Math.cos(yaw), sin = Math.sin(yaw);
        for (var box : car.getFrame().hitboxes()) {
            var origin = box.origin();
            double x = car.getX() + origin.x * cos + origin.z * sin;
            double y = car.getY() + origin.y;
            double z = car.getZ() + origin.z * cos - origin.x * sin;
            double half = box.width() / 2.0;
            result = result.minmax(new AABB(x - half, y - box.height() / 2.0, z - half,
                    x + half, y + box.height() / 2.0, z + half));
        }
        return result;
    }

    @Override public void removed(Player player) { super.removed(player); closed=true; clearDraft(); confirmedComponents.clear(); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<PLAYER_SLOT_START||index>=PLAYER_SLOT_END) return ItemStack.EMPTY;
        var slot=slots.get(index); if(!slot.hasItem()||!slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack=slot.getItem(); var original=stack.copy();
        if(index<PLAYER_HOTBAR_START) { if(!moveItemStackTo(stack,PLAYER_HOTBAR_START,PLAYER_SLOT_END,false)) return ItemStack.EMPTY; }
        else if(!moveItemStackTo(stack,PLAYER_SLOT_START,PLAYER_HOTBAR_START,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player,stack); return original;
    }
    private static GarageService.Status status(int ordinal) {
        var values=GarageService.Status.values(); return ordinal>=0&&ordinal<values.length?values[ordinal]:GarageService.Status.INVALID_PART;
    }
    public GarageService.Status getStatus() { return status(status.get()); }
    public int getRequiredCount() { return requiredCount.get(); }
    public Component vehicleName() { return choice()==null?Component.translatable("garage.automobility.status.no_vehicle"):choice().name(); }
    public boolean isCatalog() { return catalog.get()!=0; }
    public boolean isStored() { return kind.get()==STORED; }
    public boolean hasPendingChanges() { return !display.getItem(PENDING_ENGINE).isEmpty()||!display.getItem(PENDING_WHEEL).isEmpty(); }
    public boolean canApply() { return actionAvailability(Action.APPLY).enabled(); }
    public boolean canCancel() { return actionAvailability(Action.CANCEL).enabled(); }
    public boolean canPark() { return actionAvailability(Action.PARK).enabled(); }
    public boolean canDeploy() { return actionAvailability(Action.DEPLOY).enabled(); }
    public boolean canAssemble() { return actionAvailability(Action.ASSEMBLE).enabled(); }
    public boolean canTestDrive() { return actionAvailability(Action.TEST_DRIVE).enabled(); }
    public boolean resumeTuning() { return opening.resumeTuning(); }
    public ActionAvailability actionAvailability(Action action) {
        var reason=status(actionReasons.get(action.ordinal()).get()); return new ActionAvailability(reason==GarageService.Status.READY,reason);
    }
    public ActionAvailability availability(Action action) { return actionAvailability(action); }
    public int selectionCount() { return (int)opening.choices().stream().filter(c->(c.kind()==CATALOG)==isCatalog()).count(); }
    public int selectionIndex() {
        int result=0; for(int i=0;i<opening.choices().size();i++) if((opening.choices().get(i).kind()==CATALOG)==isCatalog()) { if(i==selected.get()) return result; result++; } return -1;
    }
    public List<VehicleOption> selectionOptions() {
        var result=new ArrayList<VehicleOption>();
        var selectedParts=currentComponents(); rememberConfirmedComponents(selectedParts);
        for(int i=0;i<opening.choices().size();i++) {
            var entry=opening.choices().get(i); if((entry.kind()==CATALOG)!=isCatalog()) continue;
            var key=net.minecraft.resources.ResourceKey.create(AutomobileFrame.REGISTRY,entry.frame());
            var icon=level.registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY).get(key).isPresent()?AutomobilityItems.AUTOMOBILE_FRAME.require().createStack(key):ItemStack.EMPTY;
            boolean current=i==selected.get(); var parts=current?selectedParts:confirmedComponents.get(entry.id());
            if(!current&&parts==null) parts=choiceComponents(entry);
            var rear=current?selectedRearAttachmentType():RearAttachmentType.REGISTRY.getOrDefault(entry.rear());
            var front=current?selectedFrontAttachmentType():FrontAttachmentType.REGISTRY.getOrDefault(entry.front());
            result.add(new VehicleOption(entry.id(),VEHICLE_ACTION_BASE+i,entry.name(),entry.kind(),icon,current,entry.owned(),entry.frame(),parts,rear,front));
        }
        return List.copyOf(result);
    }
    private void rememberConfirmedComponents(GarageService.Components selectedParts) {
        var currentChoice=choice();
        // Keep the last complete authoritative menu snapshot after Apply, even when this
        // UUID becomes unselected. Never retain entities or fabricate a different frame.
        if(currentChoice!=null&&selectedParts!=null&&selectedParts.complete()
                &&componentId(selectedParts,Part.FRAME).equals(currentChoice.frame())
                &&(confirmedComponents.containsKey(currentChoice.id())||confirmedComponents.size()<MAX_CHOICES))
            confirmedComponents.put(currentChoice.id(),selectedParts);
    }
    private GarageService.Components choiceComponents(Choice entry) {
        var registries=level.registryAccess();
        var frame=registries.lookupOrThrow(AutomobileFrame.REGISTRY).get(net.minecraft.resources.ResourceKey.create(AutomobileFrame.REGISTRY,entry.frame()));
        var wheel=registries.lookupOrThrow(AutomobileWheel.REGISTRY).get(net.minecraft.resources.ResourceKey.create(AutomobileWheel.REGISTRY,entry.wheel()));
        var engine=registries.lookupOrThrow(AutomobileEngine.REGISTRY).get(net.minecraft.resources.ResourceKey.create(AutomobileEngine.REGISTRY,entry.engine()));
        if(frame.isEmpty()||wheel.isEmpty()||engine.isEmpty()) return null;
        var result=new GarageService.Components(frame.get(),wheel.get(),engine.get()); return result.complete()?result:null;
    }
    public List<VehicleOption> vehicleOptions() { return selectionOptions(); }
    public List<PartOption> engineOptions() {
        if(isCatalog())return options(true);var result=new ArrayList<PartOption>();var item=AutomobilityItems.AUTOMOBILE_ENGINE.require();
        for(int slot=0;slot<36;slot++){var stack=owner.getInventory().getItem(slot);if(stack.isEmpty()||!stack.is(item))continue;
            var key=stack.get(item.dataComponent.require());if(key==null)continue;var reason=status(physicalEngineReasons.get(slot).get());
            result.add(new PartOption(key.identifier(),PHYSICAL_ENGINE_ACTION_BASE+slot,stack,stack.getCount(),1,false,hasEngineSource()&&slot==selectedEngineSourceSlot(),reason==GarageService.Status.READY,reason,Part.ENGINE));
        }return List.copyOf(result);
    }
    public List<PartOption> wheelOptions() { return options(false); }
    /** A detached view of the same batch as Apply/Assemble. Counts come from existing server DataSlots. */
    public ExchangeSummary exchangeSummary() {
        boolean assembly=isCatalog(), free=assembly&&owner.isCreative();
        var reason=actionAvailability(assembly?Action.ASSEMBLE:Action.APPLY).reason();
        var current=currentComponents();
        if(hasEngineSource()){
            var debit=display.getItem(PENDING_ENGINE);var credit=display.getItem(RETURNED_ENGINE);
            var debits=debit.isEmpty()?List.<ExchangeLine>of():List.of(new ExchangeLine(Part.ENGINE,debit,debit.getHoverName(),1,debit.getCount()));
            var credits=credit.isEmpty()?List.<ExchangeLine>of():List.of(new ExchangeLine(Part.ENGINE,credit,credit.getHoverName(),1,-1));
            return new ExchangeSummary(debits,credits,reason,false,false);
        }
        var batch=GarageService.batch(current,display.getItem(PENDING_ENGINE),display.getItem(PENDING_WHEEL),level.registryAccess(),assembly);
        if(free||batch.replacement()==null) return new ExchangeSummary(List.of(),List.of(),reason,assembly,free);
        return new ExchangeSummary(exchangeLines(batch.debits(),batch.replacement(),false),
                exchangeLines(batch.credits(),current,true),reason,assembly,false);
    }
    private List<ExchangeLine> exchangeLines(List<ItemStack> stacks,GarageService.Components parts,boolean credits) {
        var lines=new ArrayList<ExchangeLine>();
        for(var stack:stacks) {
            Part part; Identifier id; int countIndex;
            if(stack.is(AutomobilityItems.AUTOMOBILE_FRAME.require())) { part=Part.FRAME; id=parts.frame().unwrapKey().orElseThrow().identifier(); countIndex=0; }
            else if(stack.is(AutomobilityItems.AUTOMOBILE_ENGINE.require())) { part=Part.ENGINE; id=parts.engine().unwrapKey().orElseThrow().identifier(); countIndex=1; }
            else if(stack.is(AutomobilityItems.AUTOMOBILE_WHEEL.require())) { part=Part.WHEELS; id=parts.wheel().unwrapKey().orElseThrow().identifier(); countIndex=2; }
            else throw new IllegalStateException("Unrecognized garage exchange part");
            var name=Component.translatable((part==Part.FRAME?"frame.":part==Part.ENGINE?"engine.":"wheel.")+id.getNamespace()+"."+id.getPath());
            lines.add(new ExchangeLine(part,stack,name,stack.getCount(),credits?-1:recipeCounts.get(countIndex).get()));
        }
        return List.copyOf(lines);
    }
    public List<Requirement> assemblyRequirements() {
        if(!isCatalog()) return List.of();
        var parts=previewComponents(); if(parts==null) return List.of();
        return List.of(new Requirement(parts.frameStack(),recipeCounts.get(0).get(),1),
                new Requirement(parts.engineStack(),recipeCounts.get(1).get(),1),
                new Requirement(parts.wheelStack(),recipeCounts.get(2).get(),getRequiredCount()));
    }
    private List<PartOption> options(boolean engine) {
        var ids=engine?engineIds:wheelIds; var counts=engine?engineCounts:wheelCounts; var reasons=engine?engineReasons:wheelReasons;
        var current=currentComponents(); var preview=previewComponents(); var result=new ArrayList<PartOption>();
        for(int i=0;i<ids.size();i++) {
            var id=ids.get(i); var icon=template(id,engine); var reason=status(reasons.get(i).get());
            var installedPart=current==null?ItemStack.EMPTY:engine?current.engineStack():current.wheelStack();
            var selectedPart=preview==null?ItemStack.EMPTY:engine?preview.engineStack():preview.wheelStack();
            boolean installed=ItemStack.isSameItemSameComponents(icon,installedPart);
            result.add(new PartOption(id,(engine?ENGINE_ACTION_BASE:WHEEL_ACTION_BASE)+i,icon,counts.get(i).get(),
                    installed&&!isCatalog()?0:engine?1:getRequiredCount(),installed,
                    ItemStack.isSameItemSameComponents(icon,selectedPart),reason==GarageService.Status.READY,reason));
        }
        return List.copyOf(result);
    }
    public VehicleDrivingMode drivingMode() { return VehicleDrivingMode.fromId(mode.get()); }
    public VehicleDrivingMode selectedDrivingMode() { return drivingMode(); }
    public RearAttachmentType<?> selectedRearAttachmentType() {
        var car=selectedVehicle(); return car!=null?car.getRearAttachmentType():choice()==null?RearAttachmentType.EMPTY:RearAttachmentType.REGISTRY.getOrDefault(choice().rear());
    }
    public FrontAttachmentType<?> selectedFrontAttachmentType() {
        var car=selectedVehicle(); return car!=null?car.getFrontAttachmentType():choice()==null?FrontAttachmentType.EMPTY:FrontAttachmentType.REGISTRY.getOrDefault(choice().front());
    }
    public ItemStack pendingEngine() { return display.getItem(PENDING_ENGINE).copy(); }
    public ItemStack pendingWheel() { return display.getItem(PENDING_WHEEL).copy(); }
    public GarageService.Components currentComponents() {
        if(!GarageReadonlySnapshotBinding.matches(selectedChoiceId(),display.getItem(DISPLAY_FRAME),display.getItem(DISPLAY_WHEEL),display.getItem(DISPLAY_ENGINE))) return null;
        var result=new GarageService.Components(AutomobilityItems.AUTOMOBILE_FRAME.require().lookupComponent(display.getItem(DISPLAY_FRAME),level.registryAccess()),
                AutomobilityItems.AUTOMOBILE_WHEEL.require().lookupComponent(display.getItem(DISPLAY_WHEEL),level.registryAccess()),
                AutomobilityItems.AUTOMOBILE_ENGINE.require().lookupComponent(display.getItem(DISPLAY_ENGINE),level.registryAccess()));
        return result.complete()?result:null;
    }
    public GarageService.Components previewComponents() {
        var current=currentComponents(); if(current==null) return null;
        if(hasEngineSource()){
            var engine=AutomobilityItems.AUTOMOBILE_ENGINE.require().lookupComponent(display.getItem(PENDING_ENGINE),level.registryAccess());
            return engine.unwrapKey().isPresent()&&!engine.value().isEmpty()?new GarageService.Components(current.frame(),current.wheel(),engine):null;
        }
        return GarageService.batch(current,display.getItem(PENDING_ENGINE),display.getItem(PENDING_WHEEL),level.registryAccess(),isCatalog()).replacement();
    }
    public AutomobileStats currentStats() { return stats(currentComponents()); }
    public AutomobileStats previewStats() { return stats(previewComponents()); }
    private AutomobileStats stats(GarageService.Components parts) {
        if(parts==null) return null; var value=new AutomobileStats(); value.from(parts.frame().value(),parts.wheel().value(),parts.engine().value()); return value;
    }
    public record Choice(UUID id,Component name,int kind,int prefab,int drivingMode,Identifier rear,Identifier front,Identifier frame,Identifier wheel,Identifier engine,boolean owned) {
        public Choice(UUID id,Component name,int kind,int prefab,int drivingMode,Identifier rear,Identifier front,Identifier frame,boolean owned) {
            this(id,name,kind,prefab,drivingMode,rear,front,frame,Automobility.rl("empty"),Automobility.rl("empty"),owned);
        }
        public Choice(UUID id,Component name,int kind,int prefab,int drivingMode,Identifier rear,Identifier front) { this(id,name,kind,prefab,drivingMode,rear,front,Automobility.rl("empty"),kind==STORED); }
        public Choice(UUID id,Component name,int kind,int prefab,int drivingMode) { this(id,name,kind,prefab,drivingMode,Automobility.rl("empty"),Automobility.rl("empty")); }
    }
    public record OpeningData(BlockPos pos,List<Choice> choices,List<Identifier> engineIds,List<Identifier> wheelIds,UUID desired,boolean catalogMode,int storedCount,boolean resumeTuning) {
        public OpeningData {
            choices=List.copyOf(choices); engineIds=List.copyOf(engineIds); wheelIds=List.copyOf(wheelIds);
            if(choices.size()>MAX_CHOICES||engineIds.size()>MAX_PART_OPTIONS||wheelIds.size()>MAX_PART_OPTIONS||storedCount<0||storedCount>GarageStorage.MAX_RECORDS)
                throw new IllegalArgumentException("Invalid garage opening bounds");
            if(choices.stream().anyMatch(c->c.kind()<LIVE||c.kind()>CATALOG)||choices.stream().map(Choice::id).distinct().count()!=choices.size())
                throw new IllegalArgumentException("Invalid garage selection bindings");
        }
        public OpeningData(BlockPos pos,List<Choice> choices,List<Identifier> engineIds,List<Identifier> wheelIds,UUID desired,boolean catalogMode,int storedCount) {
            this(pos,choices,engineIds,wheelIds,desired,catalogMode,storedCount,false);
        }
        public OpeningData(BlockPos pos,List<Choice> choices) { this(pos,choices,List.of(),List.of(),null,false,0); }
        public OpeningData(BlockPos pos,int entityId,UUID uuid,Component name) { this(pos,List.of(new Choice(uuid,name,LIVE,-1,0))); }
        public static final StreamCodec<RegistryFriendlyByteBuf,OpeningData> STREAM_CODEC=StreamCodec.ofMember(OpeningData::encode,OpeningData::decode);
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos); buffer.writeVarInt(choices.size());
            for(var choice:choices) {
                buffer.writeUUID(choice.id()); ComponentSerialization.STREAM_CODEC.encode(buffer,choice.name()); buffer.writeVarInt(choice.kind()); buffer.writeVarInt(choice.prefab());
                buffer.writeVarInt(choice.drivingMode()); buffer.writeIdentifier(choice.rear()); buffer.writeIdentifier(choice.front()); buffer.writeIdentifier(choice.frame());
                buffer.writeIdentifier(choice.wheel()); buffer.writeIdentifier(choice.engine()); buffer.writeBoolean(choice.owned());
            }
            writeIds(buffer,engineIds); writeIds(buffer,wheelIds); buffer.writeBoolean(desired!=null); if(desired!=null) buffer.writeUUID(desired);
            buffer.writeBoolean(catalogMode); buffer.writeVarInt(storedCount); buffer.writeBoolean(resumeTuning);
        }
        private static void writeIds(RegistryFriendlyByteBuf buffer,List<Identifier> ids) { buffer.writeVarInt(ids.size()); for(var id:ids) buffer.writeIdentifier(id); }
        private static List<Identifier> readIds(RegistryFriendlyByteBuf buffer) {
            int count=buffer.readVarInt(); if(count<0||count>MAX_PART_OPTIONS) throw new IllegalArgumentException("Invalid part option count");
            var ids=new ArrayList<Identifier>(); for(int i=0;i<count;i++) ids.add(buffer.readIdentifier()); return List.copyOf(ids);
        }
        private static OpeningData decode(RegistryFriendlyByteBuf buffer) {
            var pos=buffer.readBlockPos(); int count=buffer.readVarInt(); if(count<0||count>MAX_CHOICES) throw new IllegalArgumentException("Invalid garage choices");
            var choices=new ArrayList<Choice>();
            for(int i=0;i<count;i++) choices.add(new Choice(buffer.readUUID(),ComponentSerialization.STREAM_CODEC.decode(buffer),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),
                    buffer.readIdentifier(),buffer.readIdentifier(),buffer.readIdentifier(),buffer.readIdentifier(),buffer.readIdentifier(),buffer.readBoolean()));
            var engines=readIds(buffer); var wheels=readIds(buffer); var desired=buffer.readBoolean()?buffer.readUUID():null;
            return new OpeningData(pos,choices,engines,wheels,desired,buffer.readBoolean(),buffer.readVarInt(),buffer.readBoolean());
        }
    }
}
