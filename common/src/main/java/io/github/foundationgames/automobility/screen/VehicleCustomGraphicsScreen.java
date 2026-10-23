package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.client.vehicle.VehicleCustomGraphicsSettings;
import io.github.foundationgames.automobility.client.lighting.VehicleSmallLampLighting;
import io.github.foundationgames.automobility.client.vehicle.VehicleEffectsLayout;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Local draft editor. Only the parent's explicit Save can persist/apply these values. */
public final class VehicleCustomGraphicsScreen extends Screen {
    private final Screen parent;
    private final VehicleLightQuality seed;
    private final Consumer<VehicleCustomGraphicsSettings> accepted;
    private VehicleCustomGraphicsSettings draft;
    private VehicleEffectsLayout layout;
    private boolean particles;

    public VehicleCustomGraphicsScreen(Screen parent, VehicleLightQuality seed, VehicleCustomGraphicsSettings draft,
                                       Consumer<VehicleCustomGraphicsSettings> accepted) {
        super(text("title"));
        this.parent = parent; this.seed = seed; this.draft = draft; this.accepted = accepted;
    }

    @Override protected void init() {
        layout = VehicleEffectsLayout.resolve(width, height);
        if (layout.small()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                    .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 26), Math.min(120, width), 20).build());
            return;
        }
        int x = layout.left() + 16, half = (layout.width() - 36) / 2;
        var lights = addRenderableWidget(Button.builder(text("lights"), ignored -> { particles = false; rebuildWidgets(); })
                .bounds(x, layout.top() + 44, half, 20).build());
        var effects = addRenderableWidget(Button.builder(text("particles"), ignored -> { particles = true; rebuildWidgets(); })
                .bounds(x + half + 4, layout.top() + 44, half, 20).build());
        lights.active = particles; effects.active = !particles;
        if (!particles) {
            row(0, "priority", draft.priorityFullCars() - 1, () -> replace(
                    1 + next(draft.priorityFullCars() - 1, 6 - draft.visibleFullCars()), draft.visibleFullCars(),
                    draft.coneCars(), draft.spill(), draft.driftParticlesPercent(), draft.exhaustSmoke()));
            row(1, "visible", draft.visibleFullCars(), () -> replace(draft.priorityFullCars(),
                    next(draft.visibleFullCars(), 7 - draft.priorityFullCars()), draft.coneCars(), draft.spill(),
                    draft.driftParticlesPercent(), draft.exhaustSmoke()));
            row(2, "cones", draft.coneCars(), () -> replace(draft.priorityFullCars(), draft.visibleFullCars(),
                    next(draft.coneCars(), 7), draft.spill(), draft.driftParticlesPercent(), draft.exhaustSmoke()));
            row(3, "spill", text("spill." + draft.spill().name().toLowerCase(java.util.Locale.ROOT)), () -> {
                var values = VehicleCustomGraphicsSettings.Spill.values();
                replace(draft.priorityFullCars(), draft.visibleFullCars(), draft.coneCars(),
                        values[(draft.spill().ordinal() + 1) % values.length], draft.driftParticlesPercent(), draft.exhaustSmoke());
            });
        } else {
            row(0, "drift", draft.driftParticlesPercent(), () -> replace(draft.priorityFullCars(), draft.visibleFullCars(),
                    draft.coneCars(), draft.spill(), switch (draft.driftParticlesPercent()) {
                        case 0 -> 25; case 25 -> 50; case 50 -> 100; default -> 0;
                    }, draft.exhaustSmoke()));
            row(1, "exhaust", Component.translatable(draft.exhaustSmoke() ? "options.on" : "options.off"), () -> replace(
                    draft.priorityFullCars(), draft.visibleFullCars(), draft.coneCars(), draft.spill(),
                    draft.driftParticlesPercent(), !draft.exhaustSmoke()));
        }
        footer(0, text("reset"), () -> { draft = VehicleCustomGraphicsSettings.forPreset(seed); rebuildWidgets(); });
        footer(1, Component.translatable("gui.cancel"), this::onClose);
        footer(2, Component.translatable("gui.done"), () -> { accepted.accept(draft); onClose(); });
    }

    private static int next(int current, int maximum) { return current >= maximum ? 0 : current + 1; }
    private void replace(int priority, int visible, int cones, VehicleCustomGraphicsSettings.Spill spill, int smoke, boolean exhaust) {
        draft = new VehicleCustomGraphicsSettings(priority, visible, cones, spill, smoke, exhaust);
        rebuildWidgets();
    }
    private void row(int index, String key, Object value, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable("vehicle.automobility.custom." + key, value), ignored -> action.run())
                .bounds(layout.left() + 16, layout.top() + 73 + index * 25, layout.width() - 32, 20)
                .tooltip(Tooltip.create(tooltip(key, draft))).build());
    }
    /** Rebuilt from the current draft on every click, including Off and zero-budget states. */
    static Component tooltip(String key, VehicleCustomGraphicsSettings value) {
        return switch (key) {
            case "priority" -> value.priorityFullCars() == 1 ? text("priority.none_help")
                    : Component.translatable("vehicle.automobility.custom.priority_help",
                            value.priorityFullCars() - 1, value.priorityFullCars());
            case "visible" -> value.visibleFullCars() == 0 ? text("visible.none_help")
                    : Component.translatable("vehicle.automobility.custom.visible_help", value.visibleFullCars());
            case "cones" -> value.coneCars() == 0 ? text("cones.none_help")
                    : Component.translatable("vehicle.automobility.custom.cones_help", value.coneCars());
            case "spill" -> {
                var budget = VehicleSmallLampLighting.budget(value.spill().quality());
                yield Component.translatable("vehicle.automobility.custom.spill."
                        + value.spill().name().toLowerCase(java.util.Locale.ROOT) + "_help",
                        budget.maxCars(), budget.radius());
            }
            case "drift" -> switch (value.driftParticlesPercent()) {
                case 0 -> text("drift.off_help");
                case 100 -> text("drift.full_help");
                default -> Component.translatable("vehicle.automobility.custom.drift_help", value.driftParticlesPercent());
            };
            case "exhaust" -> text(value.exhaustSmoke() ? "exhaust.on_help" : "exhaust_help");
            default -> throw new IllegalArgumentException("Unknown graphics option " + key);
        };
    }
    private void footer(int index, Component label, Runnable action) {
        var b = layout.footer(index);
        addRenderableWidget(Button.builder(label, ignored -> action.run()).bounds(b.x(), b.y(), b.width(), b.height())
                .tooltip(Tooltip.create(index == 0
                        ? Component.translatable("vehicle.automobility.custom.reset_help", Component.translatable(seed.translationKey()))
                        : text(index == 1 ? "cancel_help" : "done_help"))).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xEE162128);
        if (layout.small()) graphics.textWithWordWrap(font, Component.translatable("vehicle.automobility.too_small"),
                12, 12, Math.max(1, width - 24), 0xFFE4EDF1);
        else {
            graphics.text(font, title, layout.left() + 16, layout.top() + 10, 0xFFE4EDF1, false);
            graphics.text(font, text("main"), layout.left() + 16, layout.top() + 26, 0xFFA7BAC5, false);
            Component help = particles ? text("cosmetic") : Component.translatable("vehicle.automobility.custom.total",
                    draft.priorityFullCars() + draft.visibleFullCars(), draft.coneCars());
            int y = particles ? layout.top() + 128 : layout.top() + 176;
            graphics.textWithWordWrap(font, help, layout.left() + 16, y, layout.width() - 32, 0xFFA7BAC5);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    private static Component text(String key) { return Component.translatable("vehicle.automobility.custom." + key); }
}
