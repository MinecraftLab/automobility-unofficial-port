package io.github.foundationgames.automobility.client.dashboard;

import io.github.foundationgames.automobility.api.dashboard.DashboardClock;
import io.github.foundationgames.automobility.api.dashboard.DashboardMediaSnapshot;
import io.github.foundationgames.automobility.api.dashboard.DashboardSnapshot;
import io.github.foundationgames.automobility.api.dashboard.DashboardHudPresentation060;
import io.github.foundationgames.automobility.client.vehicle.UnifiedVehicleHud;
import io.github.foundationgames.automobility.client.vehicle.VehicleHudPlacement;
import io.github.foundationgames.automobility.client.vehicle.VehicleUiState;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Actual HUD fallback for models without a verified radio surface; never invents a world-space anchor. */
public final class VehicleDashboardHud {
    private static boolean standaloneEnabled = true;
    private VehicleDashboardHud() {}

    /** A combined instruments owner disables only automatic placement, then calls renderAt with its own reserved box. */
    public static void setStandaloneEnabled(boolean enabled) { standaloneEnabled = enabled; }

    public static void renderStandalone(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (!standaloneEnabled || hidden(client) || client.player == null || client.level == null
                || !(client.player.getVehicle() instanceof AutomobileEntity car)) return;
        var frame = VehicleDashboardClient.snapshotFor(car);
        if (frame.isEmpty() || !DashboardHudPresentation060.resolve(frame.get().media()).visible()) return;
        int sw = graphics.guiWidth(), sh = graphics.guiHeight();
        var settings = VehicleUiState.current();
        if(UnifiedVehicleHud.unified())return;
        var instruments = UnifiedVehicleHud.panelBox(sw, sh, settings);
        var hints = UnifiedVehicleHud.hintBox(sw, sh, settings);
        var reservation = VehicleHudPlacement.reservation(sw, sh, settings.reservation());
        var hotbar = new VehicleHudPlacement.Box(Math.max(0, (sw - 186) / 2), Math.max(0, sh - 52), Math.min(sw, 186), Math.min(sh, 52));
        var obstacles = new VehicleHudPlacement.Box[] { instruments, hints, reservation, hotbar };
        int width=36,height=18;
        var desired=new VehicleHudPlacement.Box(instruments.x()+instruments.width()+3,instruments.y(),width,height);
        var box=VehicleHudPlacement.avoidMany(sw,sh,desired,obstacles);
        boolean overlaps=false;for(var obstacle:obstacles)if(box.overlaps(obstacle))overlaps=true;
        if(!overlaps) draw(graphics,frame.get(),box);
        // A tiny/crowded GUI gets no overlapping radio panel; controls and instruments keep their established boxes.
    }

    public static boolean renderAt(GuiGraphicsExtractor graphics, AutomobileEntity car, VehicleHudPlacement.Box box) {
        var client = Minecraft.getInstance();
        if (hidden(client) || box == null || box.width() < 32 || box.height() < 18) return false;
        if (box.x() < 0 || box.y() < 0 || box.x() + box.width() > graphics.guiWidth()
                || box.y() + box.height() > graphics.guiHeight()) return false;
        var snapshot = VehicleDashboardClient.snapshotFor(car);
        if (snapshot.isEmpty() || !DashboardHudPresentation060.resolve(snapshot.get().media()).visible()) return false;
        draw(graphics, snapshot.get(), box);
        return true;
    }
    /** In the layout editor this is a static inactive symbol, never a simulated audio meter. */
    public static void drawPreviewIcon(GuiGraphicsExtractor graphics,VehicleHudPlacement.Box box,float scale){
        graphics.pose().pushMatrix();graphics.pose().translate(box.x(),box.y());graphics.pose().scale(scale);
        try{if(box.width()/Math.max(.001f,scale)<24)tinyOutline(graphics,0xFF6F7B80);else{graphics.fill(0,0,36,18,0xE9191D20);graphics.outline(5,5,11,8,0xFF6F7B80);graphics.fill(7,3,15,4,0xFF6F7B80);}}
        finally{graphics.pose().popMatrix();}
    }

    /** Unified instruments paint this after their own background, independent of callback order. */
    public static void renderClusterSymbol(GuiGraphicsExtractor graphics,AutomobileEntity car,VehicleHudPlacement.Box box,float scale){
        var client=Minecraft.getInstance();
        if(!standaloneEnabled||hidden(client)||client.player==null||client.level==null||box==null
                ||!Float.isFinite(scale)||scale<=0||box.width()<1||box.height()<1||box.x()<0||box.y()<0
                ||box.x()+box.width()>graphics.guiWidth()||box.y()+box.height()>graphics.guiHeight())return;
        var frame=VehicleDashboardClient.snapshotFor(car);
        if(frame.isEmpty()||!DashboardHudPresentation060.resolve(frame.get().media()).visible())return;
        graphics.pose().pushMatrix();graphics.pose().translate(box.x(),box.y());graphics.pose().scale(scale);
        try{drawTiny(graphics,frame.get());}finally{graphics.pose().popMatrix();}
    }

    /** A real source/analysis symbol embedded in the instrument indicator row. */
    private static void drawTiny(GuiGraphicsExtractor graphics,DashboardSnapshot frame){
        var rows=DashboardHudPresentation060.resolve(frame.media());
        if(!rows.visible())return;
        var media=frame.media();int color=rows.idle()?0xFF6F7B80:media.accentArgb();
        tinyOutline(graphics,color);
        boolean playing=media.status()==DashboardMediaSnapshot.Status.PLAYING||media.status()==DashboardMediaSnapshot.Status.LIVE;
        if(playing&&frame.analysis().available()){
            int lit=(int)Math.ceil(Math.clamp(frame.analysis().level(),0,1)*3);
            for(int i=0;i<3;i++)graphics.fill(11+i*3,9-(i+1)*2,13+i*3,9,i<lit?color:0xFF35413E);
        }else if(media.status()==DashboardMediaSnapshot.Status.PAUSED){
            graphics.fill(11,3,13,9,color);graphics.fill(16,3,18,9,color);
        }else if(media.status()==DashboardMediaSnapshot.Status.MUTED){
            graphics.text(Minecraft.getInstance().font,Component.literal("×"),11,2,color,false);
        }
    }
    private static void tinyOutline(GuiGraphicsExtractor graphics,int color){
        graphics.outline(1,4,7,6,color);graphics.fill(2,2,7,3,color);
        graphics.fill(3,6,5,8,color);graphics.fill(6,6,7,7,color);
    }

    private static void draw(GuiGraphicsExtractor graphics, DashboardSnapshot frame, VehicleHudPlacement.Box box) {
        var client = Minecraft.getInstance();
        var media = frame.media();
        var rows = DashboardHudPresentation060.resolve(media);
        if (!rows.visible()) return;
        int x = box.x(), y = box.y(), width = box.width(), height = box.height();
        graphics.fill(x, y, x + width, y + height, 0xE9191D20);
        graphics.fill(x, y, x + 2, y + height, media.accentArgb());
        // The driving HUD intentionally contains no station, song, artist or status
        // sentence. All metadata remains on the physical infotainment and in menus.
        if(height<29){
            int color=rows.idle()?0xFF6F7B80:media.accentArgb();
            graphics.outline(x+5,y+5,11,8,color);
            graphics.fill(x+7,y+3,x+15,y+4,color);
            graphics.fill(x+7,y+8,x+9,y+10,color);
            graphics.fill(x+11,y+8,x+14,y+11,color);
            boolean playing=media.status()==DashboardMediaSnapshot.Status.PLAYING||media.status()==DashboardMediaSnapshot.Status.LIVE;
            if(playing&&frame.analysis().available()){
                int lit=(int)Math.ceil(Math.clamp(frame.analysis().level(),0,1)*4);
                for(int i=0;i<4;i++)graphics.fill(x+21+i*3,y+13-(i+1)*2,x+23+i*3,y+13,i<lit?color:0xFF35413E);
            }else if(media.status()==DashboardMediaSnapshot.Status.PAUSED){
                graphics.fill(x+23,y+6,x+25,y+12,color);graphics.fill(x+28,y+6,x+30,y+12,color);
            }else if(media.status()==DashboardMediaSnapshot.Status.MUTED){
                graphics.text(client.font,Component.literal("×"),x+23,y+5,color,false);
            }
            return;
        }
        boolean compact = height < 56;
        if (rows.idle()) {
            graphics.text(client.font, fit(status(media).getString(), Math.max(1, width - 16)),
                    x + 8, y + (compact ? 10 : 14), 0xFFAEB9BD, false);
            return;
        }
        int iconSize = compact ? 18 : 28;
        Identifier logo = localLogo(client, rows.logoTexture());
        if (logo != null) graphics.blit(logo, x + 6, y + 6, x + 6 + iconSize, y + 6 + iconSize, 0f, 1f, 0f, 1f);
        int textX = x + iconSize + 12;
        int textWidth = Math.max(1, width - iconSize - 20);
        String station = rows.heading();
        String heading = compact ? status(media).getString() + (station.isBlank() ? "" : " · " + station) : station;
        graphics.text(client.font, fit(heading, textWidth), textX, y + 4, media.accentArgb(), false);
        String primary = compact && rows.primary().isBlank() ? rows.secondary() : rows.primary();
        graphics.text(client.font, fit(primary, textWidth), textX, y + 14, 0xFFF2F2F2, false);
        if (!compact) {
            String secondary = rows.secondary();
            graphics.text(client.font, fit(secondary, textWidth), textX, y + 24, 0xFFAEB9BD, false);
            int railY = y + 38, railWidth = Math.max(1, width - 16);
            graphics.fill(x + 7, railY, x + 7 + railWidth, railY + 2, 0xFF3B454A);
            if (media.clock().kind() == DashboardClock.Kind.FINITE) {
                int progress = (int) Math.round(railWidth * media.clock().progress());
                graphics.fill(x + 7, railY, x + 7 + progress, railY + 2, media.accentArgb());
            }
            if (frame.analysis().available()) {
                int bars = (int) Math.floor(frame.analysis().level() * 8);
                for (int i = 0; i < bars; i++) graphics.fill(x + 7 + i * 3, y + 43, x + 9 + i * 3, y + 46, media.accentArgb());
            }
            graphics.text(client.font, fit(status(media).getString(), Math.max(1, width - 50)), x + 36, y + 42, 0xFFAEB9BD, false);
        }
    }

    /** Blank/invalid metadata never reaches the game parser; only an existing local resource is drawn. */
    private static Identifier localLogo(Minecraft client, String candidate) {
        if (candidate.isEmpty()) return null;
        Identifier logo = Identifier.tryParse(candidate);
        return logo != null && client.getResourceManager().getResource(logo).isPresent() ? logo : null;
    }

    private static Component status(DashboardMediaSnapshot media) {
        String fallback = switch (media.status()) {
            case PLAYING -> "▶"; case PAUSED -> "Ⅱ"; case MUTED -> "×"; case OFF, STOPPED -> "—";
            case LIVE -> "LIVE"; case CONNECTING -> "…"; case CONSENT_REQUIRED, UNAVAILABLE -> "?";
        };
        return Component.translatableWithFallback("hud.automobility_autoradio."
                + media.status().name().toLowerCase(Locale.ROOT), fallback);
    }

    private static Component fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return Component.literal(text);
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…");
    }

    private static boolean hidden(Minecraft client) { return client.gui.hud.isHidden() || client.gui.screen() != null; }
}
