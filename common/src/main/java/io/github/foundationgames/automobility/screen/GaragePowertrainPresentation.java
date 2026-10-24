package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.drivetrain.TorqueCurve;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.PhysicalDefinitions;
import io.github.foundationgames.automobility.api.drivetrain.profile.VehicleProfileSync015Client;
import io.github.foundationgames.automobility.entity.GarageService;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Same engine curve as the mechanical catalog; only reads confirmed/current and pending preview parts. */
public final class GaragePowertrainPresentation {
    public static Component compact(GarageService.Components parts) {
        return profile(parts).<Component>map(p->Component.literal(String.format(Locale.ROOT,"%.0f Nm · %.1f kW",p.garageStatistics().torqueNewtonMetres().value(),p.garageStatistics().powerKilowatts().value())))
                .orElseGet(GaragePowertrainPresentation::unknown);
    }
    public static Component compactPower(GarageService.Components parts){return profile(parts).<Component>map(p->Component.literal(String.format(Locale.ROOT,"%.1f kW / %.0f PS",p.garageStatistics().powerKilowatts().value(),p.garageStatistics().powerKilowatts().value()/.73549875))).orElseGet(GaragePowertrainPresentation::unknown);}
    public static Component powerRpm(GarageService.Components parts){return profile(parts).<Component>map(p->Component.literal(String.format(Locale.ROOT,"@ %.0f rpm",p.garageStatistics().powerKilowatts().rpm()))).orElseGet(Component::empty);}
    private static final int TORQUE = GarageUiDraw.ACCENT, POWER = 0xff91b8ed;
    private GaragePowertrainPresentation() { }

    public record Profile(String id, TorqueCurve torqueCurve, double maximumRpm, TorqueCurve.Statistics garageStatistics) { }
    public static Optional<PhysicalDefinitions.Selection> selection(GarageService.Components parts) {
        if (parts == null || !parts.complete()) return Optional.empty();
        String frame = parts.frame().unwrapKey().orElseThrow().identifier().toString();
        String wheel = parts.wheel().unwrapKey().orElseThrow().identifier().toString();
        String id = parts.engine().unwrapKey().orElseThrow().identifier().toString();
        return VehicleProfileSync015Client.acceptedReference().flatMap(reference -> VehicleProfileSync015Client.approved(reference, frame, wheel, id));
    }
    public static Optional<Profile> profile(GarageService.Components parts) {
        return selection(parts).map(selection -> {
                    var definition = selection.engine();
                    return definition.combustion() != null
                            ? new Profile(selection.engineId(), definition.combustion().torqueCurve(), definition.combustion().redlineRpm(), definition.statistics())
                            : new Profile(selection.engineId(), definition.electric().motoringNewtonMetres(), definition.electric().maximumRpm(), definition.statistics());
                });
    }
    /** Empty assembly only: dynamic people/local cargo/attachments remain separately approved, never guessed from a preview. */
    public static Component emptyMass(GarageService.Components parts) {
        return selection(parts).<Component>map(s -> Component.literal(String.format(Locale.ROOT, "%.0f kg", s.emptyMassKg()))).orElseGet(GaragePowertrainPresentation::unknown);
    }
    public static Component torque(GarageService.Components parts) {
        return profile(parts).<Component>map(definition -> {
            var peak = definition.garageStatistics().torqueNewtonMetres();
            return Component.literal(String.format(Locale.ROOT, "%.0f Nm @ %.0f rpm", peak.value(), peak.rpm()));
        }).orElseGet(GaragePowertrainPresentation::unknown);
    }
    public static Component power(GarageService.Components parts) {
        return profile(parts).<Component>map(definition -> {
            var peak = definition.garageStatistics().powerKilowatts();
            return Component.literal(String.format(Locale.ROOT, "%.1f kW / %.0f PS @ %.0f rpm", peak.value(), peak.value() / 0.73549875, peak.rpm()));
        }).orElseGet(GaragePowertrainPresentation::unknown);
    }
    private static Component unknown() {
        return Component.translatableWithFallback("gui.automobility.garage.physical.unknown", "No physical profile");
    }

    /** Each colored line uses its explicitly labeled scale; installed curves are dotted. */
    public static Optional<Component> renderCurve(GuiGraphicsExtractor graphics, Font font, GarageUiLayout.Box box,
            GarageService.Components current, GarageService.Components preview, int mouseX, int mouseY) {
        var before = profile(current); var after = profile(preview);
        GarageUiDraw.text(graphics, font, Component.translatableWithFallback("gui.automobility.garage.physical.curve", "Engine curve"),
                box.x(), box.y(), box.width(), GarageUiDraw.TEXT);
        if (after.isEmpty()) {
            GarageUiDraw.text(graphics, font, unknown(), box.x(), box.y() + 18, box.width(), GarageUiDraw.MUTED);
            return Optional.empty();
        }
        var selected = after.get();
        double maxRpm = Math.max(selected.maximumRpm(), before.map(Profile::maximumRpm).orElse(0.0));
        double maxTorque = Math.max(selected.garageStatistics().torqueNewtonMetres().value(), before.map(value -> value.garageStatistics().torqueNewtonMetres().value()).orElse(0.0));
        double maxPower = Math.max(selected.garageStatistics().powerKilowatts().value(), before.map(value -> value.garageStatistics().powerKilowatts().value()).orElse(0.0));
        GarageUiDraw.text(graphics, font, Component.literal(String.format(Locale.ROOT, "%.0f Nm", maxTorque)), box.x(), box.y() + 13, box.width() / 2, TORQUE);
        GarageUiDraw.text(graphics, font, Component.literal(String.format(Locale.ROOT, "%.0f kW", maxPower)), box.x() + box.width() / 2, box.y() + 13, box.width() / 2, POWER);
        var plot = new GarageUiLayout.Box(box.x() + 1, box.y() + 27, box.width() - 2, Math.max(8, box.height() - 52));
        graphics.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), GarageUiDraw.BACKGROUND);
        for (int line = 1; line <= 3; line++) {
            int y = plot.y() + plot.height() * line / 4;
            graphics.fill(plot.x(), y, plot.right(), y + 1, GarageUiDraw.LINE);
        }
        if (before.isPresent() && !before.get().id().equals(after.get().id())) {
            drawCurve(graphics, plot, before.get().torqueCurve(), maxRpm, maxTorque, maxPower, GarageUiDraw.MUTED, GarageUiDraw.MUTED, true);
        }
        drawCurve(graphics, plot, selected.torqueCurve(), maxRpm, maxTorque, maxPower, TORQUE, POWER, false);
        GarageUiDraw.text(graphics, font, Component.literal(String.format(Locale.ROOT, "0 — %.0f rpm", maxRpm)),
                box.x(), plot.bottom() + 4, box.width(), GarageUiDraw.MUTED);
        GarageUiDraw.text(graphics,font,Component.translatableWithFallback("gui.automobility.garage.physical.preview","Preview").copy().append(" —"),box.x(),plot.bottom()+15,box.width()/2,GarageUiDraw.ACCENT);
        GarageUiDraw.text(graphics,font,Component.translatableWithFallback("gui.automobility.garage.physical.installed","Installed").copy().append(" ···"),box.x()+box.width()/2,plot.bottom()+15,box.width()/2,GarageUiDraw.MUTED);
        if (!plot.contains(mouseX, mouseY)) return Optional.empty();
        double rpm = maxRpm * Math.clamp((mouseX - plot.x()) / (double)Math.max(1, plot.width() - 1), 0.0, 1.0);
        String point = String.format(Locale.ROOT, "%.0f rpm\n%.1f Nm · %.1f kW", rpm,
                selected.torqueCurve().torqueAtRpm(rpm), selected.torqueCurve().powerAtRpmKilowatts(rpm));
        return Optional.of(Component.literal(point));
    }
    private static void drawCurve(GuiGraphicsExtractor graphics, GarageUiLayout.Box plot, TorqueCurve curve,
            double maxRpm, double maxTorque, double maxPower, int torqueColor, int powerColor, boolean dotted) {
        int previousTorque = -1, previousPower = -1;
        for (int x = 0; x < plot.width(); x++) {
            if (dotted && (x / 3) % 2 != 0) { previousTorque = -1; previousPower = -1; continue; }
            double rpm = maxRpm * x / Math.max(1, plot.width() - 1);
            int torqueY = ordinate(plot, curve.torqueAtRpm(rpm), maxTorque), powerY = ordinate(plot, curve.powerAtRpmKilowatts(rpm), maxPower);
            if (previousTorque >= 0) segment(graphics, plot.x() + x - 1, previousTorque, plot.x() + x, torqueY, torqueColor);
            else graphics.fill(plot.x() + x, torqueY, plot.x() + x + 1, torqueY + 1, torqueColor);
            if (previousPower >= 0) segment(graphics, plot.x() + x - 1, previousPower, plot.x() + x, powerY, powerColor);
            else graphics.fill(plot.x() + x, powerY, plot.x() + x + 1, powerY + 1, powerColor);
            previousTorque = torqueY; previousPower = powerY;
        }
    }
    private static int ordinate(GarageUiLayout.Box plot, double value, double maximum) {
        return plot.bottom() - 1 - (int)Math.round(Math.clamp(value / Math.max(maximum, 1e-9), 0.0, 1.0) * (plot.height() - 1));
    }
    private static void segment(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int count = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= count; i++) {
            int x = (int)Math.round(x0 + (x1 - x0) * i / (double)count), y = (int)Math.round(y0 + (y1 - y0) * i / (double)count);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
