package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.entity.GarageService;

import io.github.foundationgames.automobility.automobile.driving.GarageDrivingStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import io.github.foundationgames.automobility.api.dashboard.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.github.foundationgames.automobility.screen.GarageUiDraw.*;
import static io.github.foundationgames.automobility.screen.GarageUiButton.Style.*;
import static io.github.foundationgames.automobility.screen.GarageUiLayout.Mode.*;

/** Native showroom. Every candidate, inventory transaction and resulting vehicle is server-owned. */
public final class GarageScreen extends AbstractContainerScreen<GarageScreenHandler> {
    public static final int PANEL_WIDTH = GarageUiLayout.PANEL_WIDTH, PANEL_HEIGHT = GarageUiLayout.PANEL_HEIGHT;
    private static final int CARS_PER_PAGE = 4, PARTS_PER_PAGE = 4;
    private static Object cameraLevel;
    private static final Map<UUID, Camera> CAMERAS = new LinkedHashMap<>();
    private static GarageUiLayout.Mode rememberedMode = OVERVIEW;
    private static boolean rememberedEngine = true;
    private final Map<Integer, Button> actionButtons = new HashMap<>();
    private final Map<String, Button> viewButtons = new HashMap<>();
    private final Map<String, UUID> rememberedVariants = new HashMap<>();
    private final List<String> visibleGroups = new ArrayList<>();
    private final GarageSelectionTransition selectionTransition = new GarageSelectionTransition();
    private boolean transitionSettled = true;
    private final List<GarageUiButton> widgets = new ArrayList<>();
    private GarageUiLayout.Layout layout;
    private GarageUiLayout.Mode mode = OVERVIEW;
    private boolean radioTuning027;
    private boolean engineTab = true, draggingPreview, rotating = true, ownedParts, exchangePane;
    private boolean hadPending, initializedView;
    private io.github.foundationgames.automobility.entity.GarageService.Components lastDraft;
    private int carPage, partPage, fingerprint = Integer.MIN_VALUE;
    private float yaw = 35, zoom = 1;
    private long lastFrameNanos;
    private UUID cameraId;
    private Component hoveredClippedText;
    private int labelMouseX, labelMouseY;
    private GarageUiLayout.Box previewBox;
    private String lastStatus = "";
    private int feedbackTicks;
    private float viewportScale=1;

    private record Camera(float yaw,float zoom,boolean rotating,boolean radioTuning) {}

    public GarageScreen(GarageScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override protected void init() {
        viewportScale=GarageUiLayout.viewportScale(width,height);
        width=(int)Math.ceil(width/viewportScale);height=(int)Math.ceil(height/viewportScale);
        super.init();
        if (cameraLevel != minecraft.level) {
            cameraLevel = minecraft.level; CAMERAS.clear(); rememberedMode = OVERVIEW; rememberedEngine = true;
        }
        if (!initializedView) {
            mode = menu.resumeTuning() ? TUNING : rememberedMode;
            engineTab = rememberedEngine;
            initializedView = true;
        }
        if (minecraft.options.screenEffectScale().get() == 0) rotating = false;
        restoreCamera(); rebuild();
    }

    /** QA uses the same visible, focusable production widgets as a player. */
    public @Nullable Button buttonForAction(int menuButtonId) { return actionButtons.get(menuButtonId); }
    public @Nullable Button buttonForView(String id) { return viewButtons.get(id); }
    public String visibleView() { return mode.name().toLowerCase(Locale.ROOT); }
    public boolean showingEngines() { return !radioTuning027&&engineTab; }
    public boolean showingRadioTuning027(){return radioTuning027;}
    public boolean showingOwnedParts() { return ownedParts; }
    public boolean showingExchange() { return !radioTuning027&&exchangePane; }
    public float previewZoom() { return zoom; }
    public boolean previewTransitionSettled() { return transitionSettled; }
    public boolean reducedMotion() { return minecraft.options.screenEffectScale().get() == 0; }
    public List<String> visibleVehicleGroups() { return List.copyOf(visibleGroups); }
    public @Nullable Button buttonForVehicleGroup(String key) { return viewButtons.get("vehicleGroup:" + key); }
    public String groupKeyFor(UUID id) {
        for (var group : carGroups()) for (var variant : group.variants()) if (variant.id().equals(id)) return group.key();
        return "";
    }

    private void rememberCamera() {
        if (cameraId != null) {
            if (CAMERAS.size() >= 128 && !CAMERAS.containsKey(cameraId)) CAMERAS.remove(CAMERAS.keySet().iterator().next());
            CAMERAS.put(cameraId, new Camera(yaw,zoom,rotating,radioTuning027));
        }
    }
    private void restoreCamera() {
        var selected = menu.selectedChoiceId();
        if (Objects.equals(selected, cameraId)) return;
        var previousCamera = new Camera(yaw,zoom,rotating,radioTuning027);
        String previousGroup = menu.isCatalog() ? groupKeyFor(cameraId) : "";
        String nextGroup = menu.isCatalog() ? groupKeyFor(selected) : "";
        boolean sameColors = previousGroup.startsWith("family:") && previousGroup.equals(nextGroup);
        rememberCamera(); cameraId = selected;
        var camera = sameColors ? previousCamera : CAMERAS.get(selected);
        radioTuning027=camera!=null&&camera.radioTuning();
        yaw = camera == null ? 35 : camera.yaw();
        zoom = camera == null ? 1 : camera.zoom();
        rotating = minecraft.options.screenEffectScale().get() != 0 && (camera == null || camera.rotating());
        lastFrameNanos = 0; draggingPreview = false; partPage = 0;
        var groups = carGroups();
        for (int i = 0; i < groups.size(); i++) for (var variant : groups.get(i).variants()) if (variant.id().equals(selected)) {
            carPage = i / CARS_PER_PAGE; rememberedVariants.put(groups.get(i).key(), selected);
        }
    }
    private void show(GarageUiLayout.Mode nextMode) {
        if (!menu.getCarried().isEmpty()) return;
        rememberCamera(); mode = nextMode; rememberedMode = mode;
        hoveredSlot = null; quickCraftSlots.clear(); rebuild();
    }
    private void tune(boolean engine) {
        engineTab = engine; rememberedEngine = engine; partPage = 0; show(TUNING);
    }
    private void exchange() {
        if (!menu.getCarried().isEmpty()) return;
        exchangePane = true; show(TUNING);
    }
    private void setZoom(float value) {
        zoom = GaragePreviewCamera.clampZoom(value); rememberCamera(); rebuild();
    }

    private GarageUiButton widget(GarageUiLayout.Box box, Component label, GarageUiButton.Style style, Runnable action) {
        var result = new GarageUiButton(box, label, ignored -> action.run(), style);
        widgets.add(result); return addRenderableWidget(result);
    }
    private GarageUiButton view(String id, GarageUiLayout.Box box, Component label, GarageUiButton.Style style, Runnable action) {
        var result = widget(box, label, style, action); viewButtons.put(id, result); return result;
    }
    private GarageUiButton action(int id, GarageUiLayout.Box box, Component label, GarageUiButton.Style style) {
        var result = widget(box, label, style, () -> sendAction(id)); actionButtons.put(id, result); return result;
    }
    private void sendAction(int id) {
        if (!layout.small() && minecraft.gameMode != null && minecraft.player != null && minecraft.player.containerMenu == menu)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private void rebuild() {
        Integer focusedAction = null; String focusedView = null;
        var focused = getFocused();
        for (var entry : actionButtons.entrySet()) if (entry.getValue() == focused) focusedAction = entry.getKey();
        for (var entry : viewButtons.entrySet()) if (entry.getValue() == focused) focusedView = entry.getKey();
        clearWidgets(); widgets.clear(); actionButtons.clear(); viewButtons.clear();
        layout = GarageUiLayout.compute(width, height, mode);
        leftPos = layout.panel().x(); topPos = layout.panel().y();
        if (layout.small()) {
            var close = view("close", new GarageUiLayout.Box(Math.max(0, width / 2 - 60), Math.max(0, height - 32), Math.min(120, width), 20),
                    Component.translatable("gui.back"), SECONDARY, this::onClose);
            setInitialFocus(close); return;
        }
        var overview = view("overview", layout.at(254, 12, 76, 22), text("polish.overview"), TAB, () -> show(OVERVIEW));
        var tuning = view("tuning", layout.at(334, 12, 68, 22), text("polish.tuning"), TAB, () -> show(TUNING));
        var details = view("details", layout.at(406, 12, 68, 22), text("polish.details"), TAB, () -> show(DETAILS));
        overview.setSelected(mode == OVERVIEW); tuning.setSelected(mode == TUNING); details.setSelected(mode == DETAILS);
        overview.active = tuning.active = details.active = menu.getCarried().isEmpty();
        var rotation = view("rotation", layout.at(478, 12, 78, 22), text("polish.motion"), QUIET, () -> {
            rotating = !rotating; rememberCamera(); rebuild();
        });
        rotation.setSelected(rotating); rotation.setTooltip(Tooltip.create(text(rotating ? "rotation.pause" : "rotation.resume")));
        if (minecraft.options.screenEffectScale().get() == 0) {
            rotation.active = false; rotation.setTooltip(Tooltip.create(text("workshop.motion_disabled")));
        }
        var close = view("close", layout.at(560, 12, 36, 22), text("polish.close"), QUIET, this::onClose);
        close.setTooltip(Tooltip.create(text("polish.close")));
        close.setContentRenderer((g, control, mx, my, delta) -> GarageUiDraw.button(g, font,
                layout.at(560, 12, 36, 22), Component.literal("×"), QUIET, control.active, control.isHovered(), control.isFocused(), false, 1));
        var owned = view("owned", layout.at(14, 50, 53, 22), text("polish.my"), TAB, () -> { if (menu.isCatalog()) sendAction(5); });
        var catalog = view("catalog", layout.at(69, 50, 53, 22), text("polish.catalog"), TAB, () -> { if (!menu.isCatalog()) sendAction(5); });
        owned.setSelected(!menu.isCatalog()); catalog.setSelected(menu.isCatalog());
        owned.active = catalog.active = !menu.hasPendingChanges() && menu.getCarried().isEmpty();
        owned.setTooltip(Tooltip.create(text(menu.hasPendingChanges() ? "polish.cancel_first" : "owned")));
        catalog.setTooltip(Tooltip.create(text(menu.hasPendingChanges() ? "polish.cancel_first" : "catalog")));
        buildCars();
        if (mode == OVERVIEW) buildOverview();
        else if (mode == TUNING) buildTuning();
        else view("engines", layout.at(144, 318, 104, 22), text("polish.back_to_tuning"), SECONDARY, () -> tune(engineTab));
        buildZoom(); buildVariants(); buildPendingBar();
        if (focusedView != null && focusedView.startsWith("vehicleGroup:") && viewButtons.containsKey(focusedView)) setFocused(viewButtons.get(focusedView));
        else if (focusedAction != null && actionButtons.containsKey(focusedAction)) setFocused(actionButtons.get(focusedAction));
        else if (focusedView != null && viewButtons.containsKey(focusedView)) setFocused(viewButtons.get(focusedView));
        fingerprint = fingerprint();
    }

    private List<GarageScreenHandler.VehicleOption> cars() {
        return menu.selectionOptions().stream().filter(option -> (option.kind() == GarageScreenHandler.CATALOG) == menu.isCatalog()).toList();
    }
    private List<GarageVehicleCatalog.Group<GarageScreenHandler.VehicleOption>> carGroups() {
        return GarageVehicleCatalog.group(cars(), GarageScreenHandler.VehicleOption::frameId, GarageScreenHandler.VehicleOption::id, menu.isCatalog());
    }
    private void buildCars() {
        var groups = carGroups(); visibleGroups.clear();
        carPage = Math.clamp(carPage, 0, Math.max(0, (groups.size() - 1) / CARS_PER_PAGE));
        for (int row = 0; row < CARS_PER_PAGE; row++) {
            int index = carPage * CARS_PER_PAGE + row; if (index >= groups.size()) break;
            var group = groups.get(index); visibleGroups.add(group.key());
            var car = group.representative(GarageScreenHandler.VehicleOption::selected,
                    option -> option.id().equals(rememberedVariants.get(group.key())));
            var bounds = layout.at(14, 78 + row * 58, 108, 56);
            var name = group.colorGroup() ? Component.translatable(group.titleKey()) : car.name();
            var subtitle = group.colorGroup() ? text("picker.colors").copy().append(" · " + group.variants().size())
                    : car.kind() == GarageScreenHandler.LIVE && !car.owned() ? text("polish.shared") : kindText(car.kind());
            var label = Component.empty().append(name).append(" · ").append(subtitle);
            var button = view("vehicleGroup:" + group.key(), bounds, label, RAIL, () -> {
                if (group.colorGroup() && mode != OVERVIEW) show(OVERVIEW);
                if (!car.selected()) sendAction(car.buttonId());
            });
            actionButtons.put(car.buttonId(), button);
            button.active = menu.getCarried().isEmpty() && (!menu.hasPendingChanges() || car.selected()); button.setSelected(car.selected());
            button.setTooltip(Tooltip.create(menu.hasPendingChanges() && !car.selected() ? text("polish.cancel_first")
                    : Component.empty().append(label).append(group.colorGroup() ? "\n" : "").append(group.colorGroup() ? text("picker.color_help") : Component.empty())));
            int thumbnailSlot = row;
            button.setContentRenderer((graphics, control, mx, my, delta) -> {
                GarageUiDraw.railRow(graphics, font, bounds, Component.empty(), null, car.selected(), control.isHovered(), control.isFocused());
                var parts = car.components();
                if (parts != null && parts.complete()) GarageVehicleThumbnail.extract(graphics, bounds.x() + 4, bounds.y() + 2,
                        100, 34, thumbnailSlot, parts, car.rearAttachment(), car.frontAttachment());
                else graphics.item(car.frameIcon(), bounds.x() + 46, bounds.y() + 10);
                graphics.nextStratum();
                label(graphics, name, bounds.x() + 7, bounds.y() + 35, 96, TEXT);
                label(graphics, subtitle, bounds.x() + 7, bounds.y() + 46, 96, car.selected() ? ACCENT : MUTED);
            });
        }
        var previous = view("previousCars", layout.at(16, 318, 48, 20), text("polish.previous"), QUIET, () -> { carPage--; rebuild(); });
        var next = view("nextCars", layout.at(70, 318, 48, 20), text("polish.next"), QUIET, () -> { carPage++; rebuild(); });
        previous.active = carPage > 0; next.active = (carPage + 1) * CARS_PER_PAGE < groups.size();
        previous.setTooltip(Tooltip.create(text("polish.previous"))); next.setTooltip(Tooltip.create(text("polish.next")));
    }

    private void buildVariants() {
        if (mode != OVERVIEW || !menu.isCatalog()) return;
        for (var group : carGroups()) {
            if (!group.colorGroup() || group.variants().stream().noneMatch(GarageScreenHandler.VehicleOption::selected)) continue;
            int x = 588 - (group.variants().size() * 16 - 2);
            for (var car : group.variants()) {
                var info = GarageVehicleCatalog.describe(car.frameId()).orElseThrow();
                var bounds = layout.at(x, 84, 14, 18); x += 16;
                var label = Component.empty().append(Component.translatable(group.titleKey())).append(" · ").append(Component.translatable(info.colorKey()));
                var button = action(car.buttonId(), bounds, label, QUIET);
                button.active = menu.getCarried().isEmpty() && (!menu.hasPendingChanges() || car.selected()); button.setSelected(car.selected());
                button.setTooltip(Tooltip.create(menu.hasPendingChanges() && !car.selected() ? text("polish.cancel_first") : label));
                button.setContentRenderer((graphics, control, mx, my, delta) -> {
                    GarageUiDraw.button(graphics, font, bounds, Component.empty(), QUIET, control.active, control.isHovered(), control.isFocused(), car.selected(), 1);
                    int color = info.colorArgb();
                    if (!control.active) color = (color & 0x00FFFFFF) | 0x66000000;
                    graphics.fill(bounds.x() + 3, bounds.y() + 4, bounds.right() - 3, bounds.bottom() - 4, color);
                    if (car.selected()) graphics.fill(bounds.x() + 4, bounds.bottom() - 2, bounds.right() - 4, bounds.bottom() - 1, ACCENT);
                });
            }
        }
    }
    private GarageScreenHandler.Action primaryAction() {
        if (menu.isCatalog()) return GarageScreenHandler.Action.ASSEMBLE;
        if (menu.hasPendingChanges()) return GarageScreenHandler.Action.APPLY;
        return menu.isStored() ? GarageScreenHandler.Action.DEPLOY : GarageScreenHandler.Action.PARK;
    }
    private int actionId(GarageScreenHandler.Action action) {
        if(action==GarageScreenHandler.Action.APPLY&&menu.hasEngineSource())return menu.applyButtonId();
        return switch (action) { case APPLY -> 0; case PARK -> 3; case DEPLOY -> 4; case ASSEMBLE -> 6; case CANCEL -> 7; case TEST_DRIVE -> 8; };
    }
    private Component actionLabel(GarageScreenHandler.Action action) {
        if(action==GarageScreenHandler.Action.APPLY&&menu.hasEngineSource())return text(menu.enginePrepared()?"engine_confirm":"engine_prepare");
        return text(switch (action) { case APPLY -> "polish.apply"; case CANCEL -> "polish.cancel";
            case PARK -> "park_vehicle"; case DEPLOY -> "deploy_vehicle"; case ASSEMBLE -> "assemble"; case TEST_DRIVE -> "test_drive.start"; });
    }
    private void buildOverview() {
        overviewPart(true, layout.motorCard()); overviewPart(false, layout.wheelCard());
        if (menu.hasPendingChanges()) {
            var summary = view("exchangeOverview", layout.primaryAction(), text("workshop.summary"), SECONDARY, this::exchange);
            summary.setTooltip(Tooltip.create(exchangeTooltip()));
            return;
        }
        var selectedAction = primaryAction(); var availability = menu.actionAvailability(selectedAction);
        var bounds = layout.primaryAction();
        var button = action(actionId(selectedAction), bounds, actionLabel(selectedAction), PRIMARY);
        button.active = availability.enabled(); button.setTooltip(Tooltip.create(actionTooltip(availability.reason())));
        button.setContentRenderer((graphics, control, mx, my, delta) -> {
            GarageUiDraw.button(graphics, font, bounds, Component.empty(), PRIMARY, control.active, control.isHovered(), control.isFocused(), false, 1);
            int color = control.active ? INK : MUTED;
            label(graphics, actionLabel(selectedAction), bounds.x() + 12, bounds.y() + 10, bounds.width() - 24, color);
            label(graphics, control.active ? text("polish.ready") : actionReason(availability.reason()),
                    bounds.x() + 12, bounds.y() + 29, bounds.width() - 24, color);
        });
    }
    private void overviewPart(boolean engine, GarageUiLayout.Box bounds) {
        var parts = menu.previewComponents(); var base = menu.currentComponents();
        var stack = parts == null ? ItemStack.EMPTY : engine ? parts.engineStack() : parts.wheelStack();
        var partId = parts == null ? null : (engine ? parts.engine().unwrapKey() : parts.wheel().unwrapKey()).orElse(null);
        var partName = partId == null ? stack.getHoverName() : Component.translatable((engine ? "engine." : "wheel.")
                + partId.identifier().getNamespace() + "." + partId.identifier().getPath());
        boolean changed = parts != null && base != null && (engine ? menu.hasEngineSource()||!parts.engine().equals(base.engine()) : !parts.wheel().equals(base.wheel()));
        var button = view(engine ? "engines" : "wheels", bounds, text(engine ? "engine" : "wheels"), SECONDARY, () -> tune(engine));
        button.active = base != null;
        button.setContentRenderer((graphics, control, mx, my, delta) -> {
            GarageUiDraw.partCard(graphics, font, bounds, text(engine ? "engine" : "wheels"), partName,
                    text(changed ? "polish.pending" : "polish.configure"), null, changed, control.active, control.isHovered(), control.isFocused());
            graphics.item(stack, bounds.right() - 23, bounds.y() + 3);
        });
        button.setTooltip(Tooltip.create(Component.empty().append(partName).append("\n").append(text(engine ? "choose_engine_help" : "choose_wheels_help"))));
    }
    private List<GarageScreenHandler.PartOption> parts() {
        return GaragePartBrowser.visible(engineTab ? menu.engineOptions() : menu.wheelOptions(), ownedParts,
                part -> new GaragePartBrowser.State(part.id().toString(), part.installed(), part.selected(), part.available(), part.required()));
    }
    private void buildTuning() {
        var radio = view("cabinTuning027",layout.at(266,190,66,18),cabinText027("tab","Radio"),TAB,()->{radioTuning027=true;rebuild();});
        radio.setSelected(radioTuning027);radio.active=menu.getCarried().isEmpty();

        var engine = view("engines", layout.at(144, 190, 60, 18), text("engine"), TAB, () -> {radioTuning027=false;tune(true);});
        var wheels = view("wheels", layout.at(208, 190, 54, 18), text("wheels"), TAB, () -> {radioTuning027=false;tune(false);});
        engine.setSelected(!radioTuning027&&engineTab); wheels.setSelected(!radioTuning027&&!engineTab);
        if(radioTuning027){buildCabinTuning027();return;}
        var filter = view(ownedParts ? "allParts" : "ownedParts", layout.at(336,190,40,18),
                cabinText027(ownedParts?"owned":"all",ownedParts?"Owned":"All"), TAB,
                () -> { ownedParts = !ownedParts; partPage = 0; rebuild(); });
        filter.setSelected(ownedParts); filter.setTooltip(Tooltip.create(text("workshop.filter_help")));
        var choices = parts(); partPage = Math.clamp(partPage, 0, Math.max(0, (choices.size() - 1) / PARTS_PER_PAGE));
        var previous = view("previousParts", layout.at(380,190,14,18), Component.literal("‹"), QUIET, () -> { partPage--; rebuild(); });
        var next = view("nextParts", layout.at(398,190,14,18), Component.literal("›"), QUIET, () -> { partPage++; rebuild(); });
        previous.setTooltip(Tooltip.create(text("polish.previous"))); next.setTooltip(Tooltip.create(text("polish.next")));
        previous.active = partPage > 0; next.active = (partPage + 1) * PARTS_PER_PAGE < choices.size();
        for (int row = 0; row < PARTS_PER_PAGE; row++) {
            int index = partPage * PARTS_PER_PAGE + row; if (index >= choices.size()) break;
            var part = choices.get(index); var bounds = layout.at(144, 210 + row * 25, 268, 23);
            var button = action(part.buttonId(), bounds, part.name(), SECONDARY);
            button.active = part.enabled(); button.setSelected(part.selected());
            var availability = part.required() == 0 ? text("workshop.no_cost") : Component.literal(part.available() + " / " + part.required());
            var status = !part.enabled() ? part.reason() == io.github.foundationgames.automobility.entity.GarageService.Status.MISSING_PARTS
                    ? Component.empty().append(text("workshop.missing")).append(" " + Math.max(0, part.required() - part.available()) + "×") : reason(part.reason())
                    : text(part.installed() ? "polish.installed" : part.selected() ? menu.isCatalog()?"polish.selected":"workshop.selected_replacement" : "polish.available");
            var partHelp=Component.empty().append(part.name()).append("\n")
                    .append(text("polish.owned_parts")).append(" ").append(availability).append("\n")
                    .append(part.enabled() ? status : reason(part.reason()));
            for(var detail:GarageEngineItemDetails.lines(part.icon()))partHelp.append("\n").append(detail);
            button.setTooltip(Tooltip.create(partHelp));
            button.setContentRenderer((graphics, control, mx, my, delta) -> {
                GarageUiDraw.button(graphics, font, bounds, Component.empty(), SECONDARY, control.active,
                        control.isHovered(), control.isFocused(), part.selected(), 1);
                graphics.item(part.icon(), bounds.x() + 5, bounds.y() + 4);
                label(graphics, part.name(), bounds.x() + 26, bounds.y() + 3, 173, control.active ? TEXT : MUTED);
                label(graphics, status, bounds.x() + 26, bounds.y() + 13, 173, !part.enabled() ? NEGATIVE : part.selected() ? ACCENT : MUTED);
                label(graphics, availability, bounds.right() - 57, bounds.y() + 8, 51,
                        part.required() == 0 ? MUTED : part.available() >= part.required() ? POSITIVE : NEGATIVE);
            });
        }
        var inventory = view("inventory", layout.at(426, 190, 79, 18), playerInventoryTitle, TAB,
                () -> { exchangePane = false; rebuild(); });
        var exchange = view("exchange", layout.at(509, 190, 79, 18), text("workshop.exchange"), TAB, this::exchange);
        inventory.setSelected(!exchangePane); exchange.setSelected(exchangePane);
        exchange.active = menu.getCarried().isEmpty();
        exchange.setTooltip(Tooltip.create(exchangeTooltip()));
    }

    private static Component cabinText027(String key,String fallback){return Component.translatableWithFallback("garage.automobility.cabin."+key,fallback);}
    private void buildCabinTuning027(){
        var car=menu.selectedVehicle();var state=CabinTuningClient027.view(car).orElse(null);
        var settings=state==null?null:state.preferences().orElse(null);
        boolean writable=settings!=null&&state.mayConfigure()&&!CabinTuningClient027.pending(car)&&!menu.hasPendingChanges()&&menu.getCarried().isEmpty();
        var face=view("cabinFace027",layout.at(144,212,268,22),cabinText027("face","Radio panel").copy().append(": ").append(settings==null?Component.literal("…"):cabinText027("face."+settings.face().name().toLowerCase(java.util.Locale.ROOT),switch(settings.face()){case RETRO->"Retro";case CASSETTE->"Cassette";case DIGITAL->"Digital";})),SECONDARY,()->{
            var current=CabinTuningClient027.view(menu.selectedVehicle()).flatMap(CabinTuningPayloads027.State::preferences).orElse(null);
            if(current!=null)CabinTuningClient027.save(menu.selectedVehicle(),current.next(CabinTuning027.Face.values()[(current.face().ordinal()+1)%3],current.speakers()));
        });
        var speakers=view("cabinSpeakers027",layout.at(144,238,268,22),cabinText027("speakers","Speaker grilles").copy().append(": ").append(settings==null?Component.literal("…"):cabinText027("speakers."+settings.speakers().name().toLowerCase(java.util.Locale.ROOT),switch(settings.speakers()){case COMPACT->"Compact";case TOURING->"Touring";case STUDIO->"Studio";})),SECONDARY,()->{
            var current=CabinTuningClient027.view(menu.selectedVehicle()).flatMap(CabinTuningPayloads027.State::preferences).orElse(null);
            if(current!=null)CabinTuningClient027.save(menu.selectedVehicle(),current.next(current.face(),CabinTuning027.Speakers.values()[(current.speakers().ordinal()+1)%3]));
        });
        var accent=view("cabinAccent027",layout.at(144,264,268,22),cabinText027("theme","Infotainment theme").copy().append(settings==null?": …":settings.accentRgb()<0?": Auto":": "+io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme.name(settings.accentRgb())),SECONDARY,()->{
            var current=CabinTuningClient027.view(menu.selectedVehicle()).flatMap(CabinTuningPayloads027.State::preferences).orElse(null);
            if(current!=null)CabinTuningClient027.save(menu.selectedVehicle(),current.nextAccent(io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme.next(current.accentRgb())));
        });
        accent.active=writable;
        Component info=menu.isStored()?cabinText027("retrieve","Retrieve this car to customize its cockpit."):menu.isCatalog()?cabinText027("assemble","Assemble this car before cockpit tuning.")
                :menu.hasPendingChanges()?cabinText027("parts_first","Apply or cancel the pending part changes first.")
                :state!=null&&state.status()==CabinTuning027.Status.OPAQUE?cabinText027("opaque","Unknown cockpit data is preserved.")
                :!CabinTuningClient027.capable()?cabinText027("unavailable","Server cockpit tuning is unavailable."):state==null?cabinText027("waiting","Waiting for the server…"):!state.mayConfigure()?cabinText027("denied","Configuration permission is required.")
                :CabinTuningClient027.pending(car)?cabinText027("waiting","Waiting for the server…"):cabinText027("help","Changes apply after server confirmation. Visual styles do not change audio or power.");
        face.active=speakers.active=writable;face.setTooltip(Tooltip.create(info));speakers.setTooltip(Tooltip.create(info));
        var status=view("cabinHelp027",layout.at(144,290,268,20),info,QUIET,()->{});status.active=false;status.setTooltip(Tooltip.create(info));
    }
    private void drawCabinPreview027(GuiGraphicsExtractor graphics){
        int x=leftPos+432,y=topPos+220;
        var style=CabinTuningClient027.preferences(menu.selectedVehicle()).orElse(CabinTuning027.Settings.defaults());
        var display=CabinSettingsClient022.preferences(menu.selectedVehicle()).orElse(CabinPreferences022.Settings.defaults());
        int color=0xff000000|(style.accentRgb()<0?display.accentRgb():style.accentRgb());
        graphics.fill(x,y+16,x+150,y+77,0xff11171e);
        graphics.outline(x+20,y+24,110,42,style.face()==CabinTuning027.Face.RETRO?0xffa9ada5:0xff485459);
        graphics.fill(x+25,y+29,x+125,y+59,0xff06100d);
        if(style.face()==CabinTuning027.Face.CASSETTE){graphics.outline(x+91,y+32,29,22,color);graphics.fill(x+96,y+40,x+101,y+45,color);graphics.fill(x+110,y+40,x+115,y+45,color);}
        if(style.face()==CabinTuning027.Face.RETRO)for(int i=0;i<12;i++)graphics.fill(x+29+i*7,y+53,x+30+i*7,y+57,color);
        graphics.text(font,Component.literal(style.face()==CabinTuning027.Face.RETRO?"FM":style.face()==CabinTuning027.Face.CASSETTE?"TAPE":"RADIO"),x+29,y+34,color,false);
        int grille=switch(style.speakers()){case COMPACT->8;case TOURING->12;case STUDIO->16;};
        for(int side=0;side<2;side++)for(int line=0;line<8;line++){int sx=side==0?x+1:x+149-grille;graphics.fill(sx,y+28+line*4,sx+grille,y+29+line*4,0xff708087);}
        label(graphics,cabinText027("preview","Appearance preview"),x,y,150,MUTED);
        label(graphics,cabinText027("models","MX-5 / Defender / T3"),x,y+82,150,MUTED);
    }
    private int cabinFingerprint027(){
        var car=menu.selectedVehicle();var state=CabinTuningClient027.view(car).orElse(null);
        return Objects.hash(radioTuning027,car==null?null:car.getUUID(),state==null?null:state.sequence(),CabinTuningClient027.pending(car));
    }

    private void buildZoom() {
        var out = view("zoomOut", layout.at(148, 84, 22, 18), Component.literal("−"), QUIET, () -> setZoom(zoom - GaragePreviewCamera.ZOOM_STEP));
        var reset = view("zoomReset", layout.at(174, 84, 48, 18), Component.literal(String.format(Locale.ROOT, "%.1f×", zoom)), QUIET, () -> setZoom(1));
        var in = view("zoomIn", layout.at(226, 84, 22, 18), Component.literal("+"), QUIET, () -> setZoom(zoom + GaragePreviewCamera.ZOOM_STEP));
        out.active = zoom > GaragePreviewCamera.MIN_ZOOM; in.active = zoom < GaragePreviewCamera.MAX_ZOOM;
        out.setTooltip(Tooltip.create(text("workshop.zoom_out"))); reset.setTooltip(Tooltip.create(text("workshop.zoom_reset")));
        in.setTooltip(Tooltip.create(text("workshop.zoom_in")));
    }
    private void buildPendingBar() {
        var driveState = menu.actionAvailability(GarageScreenHandler.Action.TEST_DRIVE);
        var drive = action(8, layout.at(256, 318, 116, 22), text("test_drive.start"), PRIMARY);
        drive.active = driveState.enabled();
        Component driveHelp = driveState.enabled()
                ? text(menu.isCatalog() ? "test_drive.catalog_help" : menu.isStored() ? "test_drive.stored_help" : "test_drive.start_help")
                : menu.hasPendingChanges() ? text("test_drive.pending_help") : actionReason(driveState.reason());
        if (menu.isCatalog()) driveHelp = Component.empty().append(driveHelp).append("\n").append(exchangeTooltip());
        drive.setTooltip(Tooltip.create(driveHelp));
        if (!menu.hasPendingChanges() && mode == OVERVIEW) return;
        var apply = menu.isCatalog() ? GarageScreenHandler.Action.ASSEMBLE : GarageScreenHandler.Action.APPLY;
        var state = menu.actionAvailability(apply);
        var summary = view("exchangeSummary", layout.at(144, 318, 106, 22),
                !state.enabled() && menu.hasPendingChanges() ? actionReason(state.reason()) : menu.hasPendingChanges() ? changedParts() : text("workshop.summary"), QUIET, this::exchange);
        summary.setTooltip(Tooltip.create(exchangeTooltip()));
        summary.active = menu.getCarried().isEmpty();
        var confirm = action(actionId(apply), layout.at(378, 318, 126, 22), actionLabel(apply), PRIMARY);
        confirm.active = state.enabled(); confirm.setTooltip(Tooltip.create(actionTooltip(state.reason())));
        var cancel = action(7, layout.at(510, 318, 86, 22), text("polish.cancel"), SECONDARY);
        cancel.active = menu.canCancel(); cancel.setTooltip(Tooltip.create(text("polish.cancel_help")));
    }
    private Component changedParts() {
        var before = menu.currentComponents(); var after = menu.previewComponents();
        if (before == null || after == null) return text("workshop.preview");
        boolean engine = menu.hasEngineSource()||!before.engine().equals(after.engine()), wheels = !before.wheel().equals(after.wheel());
        return Component.empty().append(text("workshop.preview")).append(" · ").append(text(engine && wheels ? "workshop.both" : engine ? "engine" : "wheels"));
    }

    private int fingerprint() {
        int value = Objects.hash(cabinFingerprint027(),menu.selectedChoiceId(), menu.isCatalog(), menu.getStatus(), menu.hasPendingChanges(),
                menu.currentComponents(), menu.previewComponents(), menu.selectedEngineSourceSlot(),menu.engineConfirmationRevision(),menu.engineOwnerReason(),menu.pendingEngine().getComponents(),menu.pendingEngine().getCount(),menu.returnedEngineView().getComponents(),menu.getCarried().isEmpty(), mode, engineTab, ownedParts, exchangePane, reducedMotion());
        for (var car : cars()) value = 31 * value + Objects.hash(car.id(), car.kind(), car.selected(), car.components());
        for (var part : menu.engineOptions()) value = 31 * value + Objects.hash(part.id(),part.buttonId(),part.icon().getComponents(),part.icon().getCount(), part.available(), part.required(), part.enabled(), part.selected(), part.reason());
        for (var part : menu.wheelOptions()) value = 31 * value + Objects.hash(part.id(),part.buttonId(),part.icon().getComponents(),part.icon().getCount(), part.available(), part.required(), part.enabled(), part.selected(), part.reason());
        for (var action : GarageScreenHandler.Action.values()) value = 31 * value + menu.actionAvailability(action).hashCode();
        return value;
    }
    @Override protected void containerTick() {
        super.containerTick(); restoreCamera();
        if(radioTuning027)CabinTuningClient027.observe(menu.selectedVehicle());
        if (reducedMotion() && rotating) { rotating = false; rememberCamera(); }
        var draft = menu.previewComponents();
        if (!Objects.equals(lastDraft, draft)) { partPage = 0; lastDraft = draft; }
        if (menu.hasPendingChanges() && !hadPending && menu.getCarried().isEmpty()) exchangePane = true;
        hadPending = menu.hasPendingChanges();
        if (layout != null && !layout.small() && fingerprint != fingerprint()) rebuild();
        String status = menu.getStatus().name();
        if (!status.equals(lastStatus)) {
            if (status.equals("APPLIED") || status.equals("ASSEMBLED") || status.equals("DEPLOYED")) {
                feedbackTicks = 20;
                if (status.equals("APPLIED")) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_GOLEM_REPAIR, .9f, .35f));
            }
            lastStatus = status;
        }
        if (feedbackTicks > 0) feedbackTicks--;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();graphics.pose().scale(viewportScale);
        try { extractShowroom(graphics,(int)(mouseX/viewportScale),(int)(mouseY/viewportScale),delta); }
        finally { graphics.pose().popMatrix(); }
    }
    private void extractShowroom(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        if (layout == null) return;
        if (layout.small()) {
            graphics.fill(0, 0, width, height, BACKGROUND);
            graphics.textWithWordWrap(font, text("too_small"), 16, 20, Math.max(1, width - 32), TEXT);
            for (var button : widgets) button.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (layout == null || layout.small()) return;
        hoveredClippedText = null; labelMouseX = mouseX - leftPos; labelMouseY = mouseY - topPos;
        GarageUiDraw.backdrop(graphics, layout);
        label(graphics, text("polish.brand"), leftPos + 18, topPos + 12, 230, TEXT);
        label(graphics, statusText(), leftPos + 18, topPos + 25, 230, MUTED);
        var hero = layout.hero();
        label(graphics, menu.vehicleName(), hero.x() + 14, hero.y() + 10, mode == OVERVIEW ? 330 : 268, TEXT);
        label(graphics, kindText(menu.isCatalog() ? GarageScreenHandler.CATALOG : menu.isStored() ? GarageScreenHandler.STORED : GarageScreenHandler.LIVE),
                hero.x() + 14, hero.y() + 24, 268, MUTED);
        if (mode == OVERVIEW) previewBox = new GarageUiLayout.Box(hero.x() + 6, hero.y() + 35, hero.width() - 12, hero.height() - 65);
        else previewBox = new GarageUiLayout.Box(hero.x() + 6, hero.y() + 34, 286, hero.height() - 36);
        var parts = menu.previewComponents(); if (parts == null) parts = menu.currentComponents();
        advanceRotation(mouseX, mouseY);
        var transition = selectionTransition.update(menu.selectedChoiceId(), System.nanoTime(), !reducedMotion());
        transitionSettled = !transition.active();
        if (parts != null && parts.complete()) GarageVehiclePreview.extract(graphics, previewBox.x(), previewBox.y(), previewBox.width(), previewBox.height(),
                parts, yaw, menu.selectedRearAttachmentType(), menu.selectedFrontAttachmentType(), zoom);
        else graphics.centeredText(font, menu.selectedChoiceId() == null ? text("empty")
                        : menu.getStatus() == GarageService.Status.INTEGRATION ? text("preview.integration") : Component.literal("…"),
                previewBox.x() + previewBox.width() / 2, previewBox.y() + previewBox.height() / 2, MUTED);
        if (transition.active()) {
            graphics.nextStratum();
            int alpha = Math.clamp(Math.round(transition.curtainAlpha() * 255), 0, 255);
            graphics.fill(previewBox.x(), previewBox.y(), previewBox.right(), previewBox.bottom(), (alpha << 24) | 0x111A21);
        }
        var current = GarageDrivingStats.snapshot(menu.currentComponents(), menu.drivingMode());
        var preview = GarageDrivingStats.snapshot(menu.previewComponents(), menu.drivingMode()); if (preview == null) preview = current;
        drawSummaryStats(graphics, current, preview);
        if(mode==TUNING) drawInstalledParts(graphics);
        if (mode == TUNING) {
            GarageUiDraw.surface(graphics, layout.drawer(), GarageUiDraw.Surface.DRAWER);
            if (radioTuning027) drawCabinPreview027(graphics); else if (exchangePane) drawExchange(graphics);
            else {
                label(graphics, text("polish.owned_parts"), leftPos + 426, topPos + 214, 162, MUTED);
                for (int i = GarageScreenHandler.PLAYER_SLOT_START; i < menu.slots.size(); i++) {
                    var slot = menu.slots.get(i); GarageUiDraw.inventorySlot(graphics, leftPos + slot.x, topPos + slot.y, false);
                }
            }
        } else if (mode == DETAILS) drawDetails(graphics, current, preview);
        GarageUiDraw.surface(graphics, layout.footer(), GarageUiDraw.Surface.DRAWER);
        if (menu.hasPendingChanges()) graphics.fill(layout.footer().x(), layout.footer().y(), layout.footer().right(), layout.footer().y() + 1, ACCENT);
        else if (mode == OVERVIEW) {
            var availability = menu.actionAvailability(primaryAction());
            var status = feedbackTicks > 0 ? text("polish.confirmed") : !availability.enabled() ? actionReason(availability.reason()) : text("workshop.camera_help");
            label(graphics, status, leftPos + 144, topPos + 325, 106, feedbackTicks > 0 ? POSITIVE : MUTED);
            label(graphics, text("test_drive.footer_help"), leftPos + 380, topPos + 325, 208, MUTED);
        }
        if (mode == OVERVIEW && current != null)
            label(graphics, text(current.extended() ? "polish.extended" : "polish.original"), hero.right() - 144, hero.y() + 12, 130, ACCENT);
    }
    private void drawSummaryStats(GuiGraphicsExtractor graphics, GarageDrivingStats.Snapshot current, GarageDrivingStats.Snapshot preview) {
        if (mode == DETAILS) {
            var parts = menu.previewComponents() == null ? menu.currentComponents() : menu.previewComponents();
            GaragePowertrainPresentation.renderCurve(graphics, font, new GarageUiLayout.Box(leftPos + 438, topPos + 63, 146, 112),
                    menu.currentComponents(), parts, labelMouseX, labelMouseY).ifPresent(value -> hoveredClippedText = value);
            return;
        }
        if(mode==TUNING&&engineTab&&!radioTuning027){
            var parts=menu.previewComponents()==null?menu.currentComponents():menu.previewComponents();
            label(graphics,Component.translatableWithFallback("gui.automobility.garage.physical.installed","Installed"),leftPos+438,topPos+63,146,MUTED);
            label(graphics,GaragePowertrainPresentation.compact(menu.currentComponents()),leftPos+438,topPos+77,146,TEXT);
            label(graphics,Component.translatableWithFallback("gui.automobility.garage.physical.preview","Preview"),leftPos+438,topPos+95,146,MUTED);
            label(graphics,GaragePowertrainPresentation.compact(parts),leftPos+438,topPos+109,146,ACCENT);
            return;
        }
        if (current == null || preview == null) return;
        float[] before = {current.acceleration(), current.speedBlocksPerSecond(), current.handling(), current.grip()};
        float[] after = {preview.acceleration(), preview.speedBlocksPerSecond(), preview.handling(), preview.grip()};
        String[] names = {"acceleration", "speed", "handling", "grip"};
        for (int i = 0; i < names.length; i++) {
            boolean overview = mode == OVERVIEW;
            int x = leftPos + (overview ? 148 + i * 112 : 438), y = topPos + (overview ? 228 : 63 + i * (mode==TUNING?23:28));
            int barWidth = overview ? 100 : 146;
            label(graphics, text((overview ? "workshop.stat." : "polish.stat.") + names[i]), x, y, barWidth - 40, TEXT);
            float change = after[i] - before[i]; String mark = delta(before[i], after[i]);
            label(graphics, Component.literal(mark), x + barWidth - 38, y, 38, Math.abs(change) < .00001 ? MUTED : change > 0 ? POSITIVE : NEGATIVE);
            GarageUiDraw.statBar(graphics, new GarageUiLayout.Box(x, y + 14, barWidth, 4), before[i], after[i], i == 1 ? 40 : 1);
        }
    }
    /** Confirmed UUID/generation-bound menu snapshot only; pending preview never becomes an installed label. */
    public List<Component> installedPartLabels() {
        var heading=text(menu.isCatalog()?"workshop.base_parts":"workshop.installed_parts");
        var parts=menu.currentComponents();
        if(parts==null) return List.of(heading,text("workshop.parts_loading"));
        var engine=parts.engine().unwrapKey().orElseThrow().identifier();
        var wheels=parts.wheel().unwrapKey().orElseThrow().identifier();
        return List.of(heading,Component.empty().append(text("engine")).append(": ").append(Component.translatable("engine."+engine.getNamespace()+"."+engine.getPath())),
                Component.empty().append(text("wheels")).append(": ").append(Component.translatable("wheel."+wheels.getNamespace()+"."+wheels.getPath())));
    }
    private void drawInstalledParts(GuiGraphicsExtractor graphics) {
        var box=layout.installedParts();var lines=installedPartLabels();
        for(int i=0;i<lines.size();i++) label(graphics,lines.get(i),box.x()+8,box.y()+(i==0?0:i==1?12:23),146,i==0?MUTED:TEXT);
    }
    private Component installedPartsTooltip() {
        var result=Component.empty();for(var line:installedPartLabels()){if(!result.getString().isEmpty())result.append("\n");result.append(line);}return result;
    }
    private void drawDetails(GuiGraphicsExtractor graphics, GarageDrivingStats.Snapshot current, GarageDrivingStats.Snapshot preview) {
        GarageUiDraw.surface(graphics, layout.drawer(), GarageUiDraw.Surface.DRAWER);
        label(graphics, text("polish.parameters"), leftPos + 148, topPos + 197, 176, TEXT);
        label(graphics, text("current"), leftPos + 330, topPos + 197, 126, MUTED);
        label(graphics, text("preview"), leftPos + 460, topPos + 197, 126, ACCENT);
        if (current != null && preview != null) {
            float[] before = {current.acceleration(), current.speedBlocksPerSecond(), current.handling(), current.grip()};
            float[] after = {preview.acceleration(), preview.speedBlocksPerSecond(), preview.handling(), preview.grip()};
            String[] names = {"acceleration", "speed", "handling", "grip"};
            for (int i = 0; i < names.length; i++) {
                int y = topPos + 218 + i * 14;
                label(graphics, text("stat." + names[i]), leftPos + 148, y, 176, MUTED);
                label(graphics, Component.literal(number(before[i])), leftPos + 330, y, 126, TEXT);
                label(graphics, Component.literal(number(after[i])), leftPos + 460, y, 126, after[i] >= before[i] ? POSITIVE : NEGATIVE);
            }
        }
        var physicalPreview = menu.previewComponents() == null ? menu.currentComponents() : menu.previewComponents();
        label(graphics, Component.translatableWithFallback("gui.automobility.garage.physical.torque", "Torque"), leftPos + 148, topPos + 274, 240, MUTED);
        label(graphics, GaragePowertrainPresentation.torque(menu.currentComponents()), leftPos + 330, topPos + 274, 126, TEXT);
        label(graphics, GaragePowertrainPresentation.torque(physicalPreview), leftPos + 460, topPos + 274, 126, ACCENT);
        label(graphics, Component.translatableWithFallback("gui.automobility.garage.physical.power", "Power"), leftPos + 148, topPos + 288, 240, MUTED);
        label(graphics, GaragePowertrainPresentation.compactPower(menu.currentComponents()), leftPos + 330, topPos + 288, 126, TEXT);
        label(graphics, GaragePowertrainPresentation.compactPower(physicalPreview), leftPos + 460, topPos + 288, 126, ACCENT);
        label(graphics,GaragePowertrainPresentation.powerRpm(menu.currentComponents()),leftPos+330,topPos+300,126,MUTED);
        label(graphics,GaragePowertrainPresentation.powerRpm(physicalPreview),leftPos+460,topPos+300,126,MUTED);
    }
    private static String delta(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after)) return "—";
        if (Math.abs(after - before) < .00001) return "=";
        if (Math.abs(before) < .00001) return after > before ? "+" : "−";
        double percentage = (after - before) / Math.abs(before) * 100.0;
        return Math.abs(percentage) < .5 ? "≈" : String.format(Locale.ROOT, "%+.0f%%", percentage);
    }
    private void drawExchange(GuiGraphicsExtractor graphics) {
        var summary = menu.exchangeSummary();
        label(graphics, text("workshop.consume"), leftPos + 426, topPos + 214, 162, TEXT);
        if (summary.creativeFree()) {
            graphics.textWithWordWrap(font, text("workshop.creative_free"), leftPos + 428, topPos + 232, 156, MUTED); return;
        }
        int y = 227;
        for (var line : summary.debits()) { drawExchangeLine(graphics, line, y); y += 16; }
        if (summary.debits().isEmpty()) label(graphics, text("workshop.no_debit"), leftPos + 428, topPos + y, 156, MUTED);
        if (!summary.assembly()) {
            label(graphics, text("workshop.return"), leftPos + 426, topPos + 267, 162, TEXT);
            y = 279;
            for (var line : summary.credits()) { drawExchangeLine(graphics, line, y); y += 16; }
            if (summary.credits().isEmpty()) label(graphics, text("workshop.no_return"), leftPos + 428, topPos + 282, 156, MUTED);
        } else label(graphics, text("workshop.assembly_help"), leftPos + 428, topPos + 286, 156, MUTED);
    }
    private void drawExchangeLine(GuiGraphicsExtractor graphics, GarageScreenHandler.ExchangeLine line, int y) {
        graphics.item(line.icon(), leftPos + 426, topPos + y - 2);
        var title = Component.literal(line.count() + "× ").append(line.name());
        label(graphics, title, leftPos + 446, topPos + y + 2, line.available() < 0 ? 142 : 101, TEXT);
        if (line.available() >= 0) label(graphics, Component.literal(line.available() + "/" + line.count()),
                leftPos + 550, topPos + y + 2, 38, line.available() >= line.count() ? POSITIVE : NEGATIVE);
    }
    private void advanceRotation(int mouseX, int mouseY) {
        long now = System.nanoTime();
        boolean reading = mode == TUNING && layout.drawer().contains(mouseX, mouseY);
        if (rotating && !reading && !draggingPreview && lastFrameNanos != 0) {
            double elapsed = Math.max(0, Math.min(.25, (now - lastFrameNanos) / 1_000_000_000.0));
            yaw = (float)((yaw + elapsed * 10) % 360);
        }
        lastFrameNanos = now;
    }
    private Component statusText() {
        var status = menu.getStatus();
        if(status!=GarageService.Status.READY&&status!=GarageService.Status.NO_CHANGES)return actionReason(status);
        if (menu.hasPendingChanges()) return text("polish.pending_help");
        return reason(status);
    }
    private Component actionTooltip(io.github.foundationgames.automobility.entity.GarageService.Status status) {
        if(menu.hasEngineSource()&&menu.engineOwnerReason()!=io.github.foundationgames.automobility.automobile.garage.GarageEngineTransferParticipant.Reason.NONE)
            return text("engine_reason."+menu.engineOwnerReason().name().toLowerCase(Locale.ROOT));

        return Component.empty().append(actionReason(status)).append("\n").append(exchangeTooltip());
    }
    private Component actionReason(io.github.foundationgames.automobility.entity.GarageService.Status status) {
        if (status == io.github.foundationgames.automobility.entity.GarageService.Status.MISSING_PARTS)
            for (var line : menu.exchangeSummary().debits()) if (line.available() < line.count())
                return Component.empty().append(text("workshop.missing")).append(" " + (line.count() - line.available()) + "× ").append(line.name());
        return reason(status);
    }
    private Component exchangeTooltip() {
        var summary = menu.exchangeSummary(); var value = Component.empty().append(text("workshop.summary"));
        if (summary.creativeFree()) return value.append("\n").append(text("workshop.creative_free"));
        value.append("\n").append(text("workshop.consume"));
        for (var line : summary.debits()) {value.append("\n").append(line.count() + "× ").append(line.name())
                .append(" · ").append(text("polish.owned_parts")).append(" " + line.available() + " / " + line.count());for(var detail:GarageEngineItemDetails.lines(line.icon()))value.append("\n").append(detail);}
        if (summary.debits().isEmpty()) value.append("\n").append(text("workshop.no_debit"));
        if (!summary.assembly()) {
            value.append("\n").append(text("workshop.return"));
            for (var line : summary.credits()) {value.append("\n").append(line.count() + "× ").append(line.name());for(var detail:GarageEngineItemDetails.lines(line.icon()))value.append("\n").append(detail);}
            if (summary.credits().isEmpty()) value.append("\n").append(text("workshop.no_return"));
        }
        if(menu.hasEngineSource())value.append("\n").append(text("engine_exchange_rules"));
        return value;
    }
    private static Component reason(io.github.foundationgames.automobility.entity.GarageService.Status status) {
        if (status == io.github.foundationgames.automobility.entity.GarageService.Status.MISSING_PARTS)
            return text("polish.missing_parts");
        return text("status." + status.name().toLowerCase(Locale.ROOT));
    }
    private static Component kindText(int kind) {
        return text(kind == GarageScreenHandler.CATALOG ? "polish.catalog_car" : kind == GarageScreenHandler.STORED ? "polish.stored" : "polish.at_workshop");
    }
    private static String number(float value) { return Float.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—"; }
    private void label(GuiGraphicsExtractor graphics, Component value, int x, int y, int available, int color) {
        clippedText(graphics, value, x - leftPos, y - topPos, available, color);
    }
    private void clippedText(GuiGraphicsExtractor graphics, Component value, int x, int y, int available, int color) {
        var fitted = GarageTextLayout.fit(font, value.getString(), available);
        graphics.text(font, fitted.visualOrder(), leftPos + x, topPos + y, color, false);
        if (GarageTextTooltip.isHoveredClipped(fitted.fullWidth(), x, y, available, font.lineHeight, labelMouseX, labelMouseY)) hoveredClippedText = value;
    }
    private boolean inventoryVisible() { return layout != null && layout.inventoryVisible() && !exchangePane && !radioTuning027; }
    private boolean visibleSlot(Slot slot) { return inventoryVisible() && slot != null && menu.slots.indexOf(slot) >= GarageScreenHandler.PLAYER_SLOT_START; }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}
    @Override protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (inventoryVisible()) super.extractSlots(graphics, mouseX, mouseY); else hoveredSlot = null;
    }
    @Override protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (visibleSlot(slot)) super.extractSlot(graphics, slot, mouseX, mouseY);
    }
    @Override protected void slotClicked(Slot slot, int index, int button, ContainerInput input) {
        if (inventoryVisible() && (slot == null || visibleSlot(slot))) super.slotClicked(slot, index, button, input);
    }
    @Override protected boolean checkHotbarKeyPressed(KeyEvent event) { return inventoryVisible() && super.checkHotbarKeyPressed(event); }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (layout == null || layout.small()) return;
        if (inventoryVisible()) super.extractTooltip(graphics, mouseX, mouseY);
        if(mode==TUNING && layout.installedParts().contains(mouseX,mouseY))
            graphics.setTooltipForNextFrame(font,installedPartsTooltip(),mouseX,mouseY);
        else if (layout.statsTooltip().contains(mouseX,mouseY)) {
            var explanation = Component.empty().append(text("workshop.delta_help"))
                    .append("\n").append(text("estimate_help"));
            int tooltipWidth = Math.max(1, Math.min(240, width - 24));
            graphics.setTooltipForNextFrame(font, font.split(explanation, tooltipWidth), mouseX, mouseY);
        }
        else if (mode == TUNING && exchangePane && layout.at(426, 210, 162, 100).contains(mouseX, mouseY))
            graphics.setTooltipForNextFrame(font, exchangeTooltip(), mouseX, mouseY);
        else if (hoveredClippedText != null) graphics.setTooltipForNextFrame(font, hoveredClippedText, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        event=logicalMouse(event);
        for (var button : widgets) {
            if (!button.visible || event.x() < button.getX() || event.x() >= button.getX() + button.getWidth()
                    || event.y() < button.getY() || event.y() >= button.getY() + button.getHeight()) continue;
            Integer clickedAction = null; String clickedView = null;
            for (var entry : actionButtons.entrySet()) if (entry.getValue() == button) clickedAction = entry.getKey();
            for (var entry : viewButtons.entrySet()) if (entry.getValue() == button) clickedView = entry.getKey();
            if (button.active && button.mouseClicked(event, doubleClick)) {
                if (widgets.contains(button)) setFocused(button);
                else if (clickedAction != null) setFocused(actionButtons.get(clickedAction));
                else if (clickedView != null) setFocused(viewButtons.get(clickedView));
            }
            return true;
        }
        if (layout == null || layout.small()) return false;
        if (event.button() == 0 && previewBox != null && previewBox.contains(event.x(), event.y())) {
            draggingPreview = true; rotating = false; rememberCamera(); rebuild(); return true;
        }
        if (inventoryVisible() && layout.inventory().inset(0).contains(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        return layout.panel().contains(event.x(), event.y());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        event=logicalMouse(event);dragX/=viewportScale;dragY/=viewportScale;
        if (draggingPreview && event.button() == 0) {
            if (Double.isFinite(dragX)) yaw = (float)((yaw + dragX * .65 + 360) % 360); rememberCamera(); return true;
        }
        return inventoryVisible() && super.mouseDragged(event, dragX, dragY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        event=logicalMouse(event);
        if (draggingPreview) { draggingPreview = false; rememberCamera(); return true; }
        return inventoryVisible() && super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX/=viewportScale;mouseY/=viewportScale;
        if (layout == null || layout.small()) return false;
        if (previewBox != null && previewBox.contains(mouseX, mouseY) && Double.isFinite(scrollY) && scrollY != 0) {
            setZoom(GaragePreviewCamera.zoomBySteps(zoom, scrollY)); return true;
        }
        if (layout.rail().contains(mouseX, mouseY)) {
            carPage = Math.clamp(carPage + (scrollY < 0 ? 1 : -1), 0, Math.max(0, (carGroups().size() - 1) / CARS_PER_PAGE)); rebuild(); return true;
        }
        if (mode == TUNING && !radioTuning027 && layout.at(140, 210, 276, 100).contains(mouseX, mouseY)) {
            partPage = Math.clamp(partPage + (scrollY < 0 ? 1 : -1), 0, Math.max(0, (parts().size() - 1) / PARTS_PER_PAGE)); rebuild(); return true;
        }
        return inventoryVisible() && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!inventoryVisible()) hoveredSlot = null;
        if (event.key() == 256) { onClose(); return true; }
        return super.keyPressed(event);
    }
    private MouseButtonEvent logicalMouse(MouseButtonEvent event){return new MouseButtonEvent(event.x()/viewportScale,event.y()/viewportScale,event.buttonInfo());}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/viewportScale,y/viewportScale);}
    @Override public void onClose() { rememberCamera(); super.onClose(); }
    @Override public void removed() { rememberCamera(); super.removed(); }
    private static Component text(String key) { return Component.translatable("garage.automobility." + key); }
}
