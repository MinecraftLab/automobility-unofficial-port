package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.screen.AutomobileHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Core-owned placement. Optional driving presentation contributes readings, never layout/gameplay. */
public final class UnifiedVehicleHud {
    public interface DrivingContributor {
        int speedKmh(AutomobileEntity auto);
        boolean enabled();
        boolean cruiseActive(AutomobileEntity auto);
        int cruiseTarget();
        default java.util.List<Component> warnings(AutomobileEntity auto) {return java.util.List.of();}
        default Component controlBinding(String action,Component fallback) {return fallback;}
    }
    private static DrivingContributor contributor;
    private static Object riding,world,rider;
    private static boolean wasDriver;
    private static long seatTick;
    private static VehicleUiSettings cachedSettings;
    private static Object cachedFont;
    private static String cachedLanguage;
    private static int cachedW,cachedH,cachedStatusWidth;
    private static boolean cachedEnergy,cachedMechanics;
    private static VehicleHudPlacement.Box panel,hints;
    private static float scale,decorationScale=1;
    private static double cachedGuiScale=Double.NaN;
    private static long layoutResolutions;
    private UnifiedVehicleHud() {}
    public static void register(DrivingContributor value) { contributor=value; }
    public static boolean unified() {return VehicleUiState.unified();}
    public static long layoutResolutions() {return layoutResolutions;}
    /** FontManager keeps the same Font object while replacing or filtering glyph metrics. */
    public static void invalidateFontLayout() {cachedSettings=null;}
    public static void tick(Minecraft client) {
        VehicleEnergyHud.tick(client);
        Object current=client.player==null?null:client.player.getVehicle();
        boolean driver=client.player!=null && current instanceof AutomobileEntity car && VehicleClientControls.isDriver(client,car);
        if(current!=riding || client.level!=world || client.player!=rider || driver!=wasDriver) {
            riding=current;world=client.level;rider=client.player;wasDriver=driver;seatTick=client.level==null?0:client.level.getGameTime();
        }
    }
    private static int statusWidth(Minecraft client) {return VehicleStatusHud.maximumContentWidth();}
    private static void resolve(int w,int h,VehicleUiSettings value,boolean energy) {
        resolve(w,h,value,energy,value.basic().enabled() && riding instanceof AutomobileEntity car && VehicleDrivetrainHud.present(car));
    }
    private static void resolve(int w,int h,VehicleUiSettings value,boolean energy,boolean mechanics) {
        var client=Minecraft.getInstance();var language=client.getLanguageManager().getSelected();
        double guiScale=client.getWindow().getGuiScale();
        if(value.equals(cachedSettings) && cachedFont==client.font && language.equals(cachedLanguage) && cachedW==w && cachedH==h && cachedEnergy==energy && cachedMechanics==mechanics && Double.compare(cachedGuiScale,guiScale)==0) return;
        cachedSettings=value;cachedFont=client.font;cachedLanguage=language;cachedW=w;cachedH=h;cachedEnergy=energy;cachedMechanics=mechanics;cachedGuiScale=guiScale;
        decorationScale=VehicleEnergyPresentation.guiScaleCompensation(guiScale);
        cachedStatusWidth=DashboardClusterLayout.INSTRUMENT_WIDTH;
        int logicalWidth=DashboardClusterLayout.WIDTH;
        int logicalHeight=DashboardClusterLayout.instrumentHeight(energy,mechanics);
        scale=VehicleEnergyPresentation.panelScale(w,h,logicalWidth,logicalHeight,value.basic().scalePercent(),guiScale);
        var reserved=VehicleHudPlacement.reservation(w,h,value.reservation());
        var hotbar=new VehicleHudPlacement.Box(Math.max(0,(w-186)/2),Math.max(0,h-52),Math.min(w,186),Math.min(h,52));
        panel=VehicleHudPlacement.avoid(w,h,VehicleHudPlacement.anchored(w,h,(int)Math.ceil(logicalWidth*scale),(int)Math.ceil(logicalHeight*scale),value.panel()),reserved,hotbar);
        // Stable hint rectangle also reserves warning space over the instruments.
        hints=VehicleHudPlacement.anchored(w,h,Math.min(Math.max(1,w-8),(int)Math.ceil(260*decorationScale)),(int)Math.ceil(68*decorationScale),value.hints());
        hints=VehicleHudPlacement.avoidMany(w,h,hints,reserved,panel,hotbar);
        layoutResolutions++;
    }
    public static VehicleHudPlacement.Box panelBox(int w,int h,VehicleUiSettings value) {resolve(w,h,value,value.energyEnabled() && VehicleEnergyHud.preview().present());return panel;}
    public static VehicleHudPlacement.Box hintBox(int w,int h,VehicleUiSettings value) {resolve(w,h,value,value.energyEnabled() && VehicleEnergyHud.preview().present());return hints;}
    public static VehicleHudPlacement.Box radioBox(int w,int h,VehicleUiSettings value){resolve(w,h,value,value.energyEnabled()&&VehicleEnergyHud.preview().present());return DashboardClusterLayout.radio(panel,scale);}
    public static float clusterScale(){return scale;}
    public static VehicleHudLayout.Layout statusLayout(int w,int h) {
        resolve(w,h,VehicleUiState.current(),VehicleUiState.current().energyEnabled() && VehicleEnergyHud.preview().present());return new VehicleHudLayout.Layout(panel.x(),panel.y(),cachedStatusWidth,scale);
    }
    public static void render(GuiGraphicsExtractor graphics,AutomobileEntity auto) {
        var client=Minecraft.getInstance();if(client.player==null || client.level==null || client.options.hideGui || client.screen!=null) return;
        var value=VehicleUiState.current();
        var energy=value.energyEnabled()?VehicleEnergyHud.reading(auto):VehicleEnergyReading.NONE;
        resolve(graphics.guiWidth(),graphics.guiHeight(),value,energy.present(),value.basic().enabled() && VehicleDrivetrainHud.present(auto));
        int speed=contributor!=null && contributor.enabled()?contributor.speedKmh(auto):(int)Math.round(Math.abs(auto.getEffectiveSpeed()*72));
        boolean active=contributor!=null && contributor.enabled() && contributor.cruiseActive(auto);
        drawPanel(graphics,auto,panel,scale,cachedStatusWidth,speed,active,active?contributor.cruiseTarget():0,value.basic().enabled() && VehicleClientControls.isDriver(client,auto),energy,VehicleEnergyHud.reversing(auto));
        io.github.foundationgames.automobility.client.dashboard.VehicleDashboardHud.renderClusterSymbol(graphics,auto,DashboardClusterLayout.radio(panel,scale),scale);
        var warnings=new java.util.ArrayList<Component>();
        if(VehicleClientControls.isDriver(client,auto)) {
            warnings.addAll(VehicleStatusHud.warningLines(auto));
            var energyWarning=VehicleEnergyHud.warning(energy);
            if(energyWarning!=null) warnings.add(energyWarning);
            if(contributor!=null && contributor.enabled()) warnings.addAll(contributor.warnings(auto));
        }
        var warningBox=drawWarnings(graphics,warnings,value);
        var occupied=warningBox==null?new VehicleHudPlacement.Box(0,0,0,0):warningBox;
        long elapsed=client.level.getGameTime()-seatTick;
        if(!hints.overlaps(occupied) && VehicleHudPlacement.hintsVisible(value.hintMode(),elapsed,Platform.get().controller().inControllerMode())) drawHints(graphics,hints);
    }
    private static VehicleHudPlacement.Box drawWarnings(GuiGraphicsExtractor graphics,java.util.List<Component> warnings,VehicleUiSettings settings) {
        if(warnings.isEmpty()) return null;
        var client=Minecraft.getInstance();int w=0;
        for(var text:warnings) w=Math.max(w,client.font.width(text)+12);
        float warningScale=decorationScale;
        int sw=graphics.guiWidth(),sh=graphics.guiHeight();
        w=Math.min(Math.max(1,(int)((sw-8)/warningScale)),Math.min(304,w));
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(var text:warnings) lines.addAll(client.font.split(text,Math.max(1,w-12)));
        int h=Math.min(Math.max(1,(int)(sh/warningScale)),lines.size()*10+8);
        int width=(int)Math.ceil(w*warningScale),height=(int)Math.ceil(h*warningScale);
        var desired=new VehicleHudPlacement.Box(panel.x(),Math.max(0,panel.y()-height-Math.round(4*warningScale)),width,height);
        var box=VehicleHudPlacement.avoidMany(sw,sh,desired,panel,VehicleHudPlacement.reservation(sw,sh,settings.reservation()),
                new VehicleHudPlacement.Box(Math.max(0,(sw-186)/2),Math.max(0,sh-52),Math.min(sw,186),Math.min(sh,52)));
        graphics.pose().pushMatrix();graphics.pose().translate(box.x(),box.y());graphics.pose().scale(warningScale);
        try {
            graphics.fill(0,0,w,h,0xEB171A18);
            graphics.fill(0,0,2,h,0xFFFFD095);
            for(int i=0;i<lines.size() && 4+i*10+9<=h;i++) graphics.text(client.font,lines.get(i),6,4+i*10,0xFFFFD095,false);
        } finally {graphics.pose().popMatrix();}
        return box;
    }
    public static void drawPreview(GuiGraphicsExtractor graphics,int w,int h,VehicleUiSettings value) {
        var energy=value.energyEnabled()?VehicleEnergyHud.preview():VehicleEnergyReading.NONE;
        resolve(w,h,value,energy.present(),value.basic().enabled());drawPanel(graphics,null,panel,scale,cachedStatusWidth,64,true,64,value.basic().enabled(),energy,false);
        io.github.foundationgames.automobility.client.dashboard.VehicleDashboardHud.drawPreviewIcon(graphics,DashboardClusterLayout.radio(panel,scale),scale);
        if(value.hintMode()!=VehicleUiSettings.HintMode.OFF) drawHints(graphics,hints);
    }
    private static void drawPanel(GuiGraphicsExtractor graphics,AutomobileEntity auto,VehicleHudPlacement.Box box,float scale,int statusWidth,int speed,boolean cruise,int target,boolean status,VehicleEnergyReading energy,boolean reverse) {
        var font=Minecraft.getInstance().font;
        int width=DashboardClusterLayout.INSTRUMENT_WIDTH;
        int height=DashboardClusterLayout.instrumentHeight(energy.present(),cachedMechanics);
        graphics.pose().pushMatrix();graphics.pose().translate(box.x(),box.y());graphics.pose().scale(scale);
        try {
            graphics.fill(0,0,width,height,0xEA171A18);
            graphics.fill(0,0,width,1,0xFF596E7B);
            String digits=Integer.toString(speed);
            float digitScale=Math.min(2.1f,43f/Math.max(1,font.width(digits)));
            graphics.pose().pushMatrix();graphics.pose().translate(4,4);graphics.pose().scale(digitScale);
            graphics.text(font,Component.literal(digits),0,0,0xFFE4EDF1,false);graphics.pose().popMatrix();
            graphics.text(font,Component.literal("km/h"),4,25,0xFFA7BAC5,false);
            if(cruise){
                String text="↗"+target;float fitted=Math.min(1,20f/Math.max(1,font.width(text)));
                graphics.pose().pushMatrix();graphics.pose().translate(31,25);graphics.pose().scale(fitted);
                graphics.text(font,Component.literal(text),0,0,0xFF8ADEAE,false);graphics.pose().popMatrix();
            }
            if(cachedMechanics) drawMechanics(graphics,auto,width);
            else if(reverse) graphics.text(font,Component.literal("R"),57,25,0xFFFFD095,false);
            if(energy.present()){
                graphics.pose().pushMatrix();graphics.pose().translate(0,DashboardClusterLayout.energyY(cachedMechanics)-VehicleEnergyPresentation.BASE_HEIGHT);
                VehicleEnergyGauge.draw(graphics,energy,width);graphics.pose().popMatrix();
            }
            if(status){
                graphics.pose().pushMatrix();graphics.pose().translate(0,DashboardClusterLayout.statusY(energy.present(),cachedMechanics)+1);graphics.pose().scale(.65f);
                if(auto!=null)VehicleStatusHud.drawCompactPanel(graphics,auto,80);
                else {
                    graphics.fill(5,5,14,11,0xFF8ADEAE);graphics.fill(7,3,12,5,0xFF8ADEAE);
                    graphics.fill(25,4,31,11,0xFF8ADEAE);
                    graphics.fill(49,7,56,9,0xFF40534F);graphics.fill(62,7,69,9,0xFF40534F);
                }
                graphics.pose().popMatrix();
            }
        } finally {graphics.pose().popMatrix();}
    }
    /** Already synchronized readings only; no drivetrain, energy or audio state changes. */
    private static void drawMechanics(GuiGraphicsExtractor graphics,AutomobileEntity auto,int width){
        var reading=VehiclePowertrainPresentation.reading(auto).orElse(null);
        if(auto!=null&&reading==null)return;
        String range=reading==null?"3":reading.rangeLabel();
        String mode=reading==null?"A":reading.electric()?"EV":reading.snapshot().controlMode()==io.github.foundationgames.automobility.automobile.drivetrain.PowertrainSimulation.ControlMode.AUTOMATIC?"A":"M";
        double rpm=reading==null?3200:Math.abs(reading.signedRpm()),maximum=reading==null?6500:Math.max(1,reading.maximumRpm());
        boolean stalled=reading!=null&&reading.snapshot().enginePhase()==io.github.foundationgames.automobility.automobile.drivetrain.EngineController.Phase.STALLED;
        int color=stalled||rpm>maximum?0xFFFF8A79:0xFFD7E3DF;
        var font=Minecraft.getInstance().font;
        graphics.fill(width-21,3,width-4,23,0xFF26352F);
        float size=Math.min(1.8f,13f/Math.max(1,font.width(range)));
        graphics.pose().pushMatrix();graphics.pose().translate(width-18,5);graphics.pose().scale(size);
        graphics.text(font,Component.literal(range),0,0,color,false);graphics.pose().popMatrix();
        graphics.text(font,Component.literal(mode),width-20,25,0xFFA7BAC5,false);
        int y=DashboardClusterLayout.HEADER_HEIGHT;
        graphics.text(font,Component.literal(font.plainSubstrByWidth(String.format(java.util.Locale.ROOT,"%.0f rpm",rpm),width-8)),4,y+2,color,false);
        int rail=width-8,fill=(int)Math.round(Math.clamp(rpm/maximum,0,1)*rail);
        graphics.fill(4,y+13,width-4,y+15,0xFF39443F);
        graphics.fill(4,y+13,4+fill,y+15,rpm>maximum*.9?0xFFFFA06A:0xFF8ADEAE);
    }
    public static Component hintBinding(AutomobileHud.ControlHint hint,net.minecraft.client.Options options) {
        var fallback=hint.getKeybindText(options);
        return contributor==null?fallback:contributor.controlBinding(hint.name(),fallback);
    }
    private static void drawHints(GuiGraphicsExtractor graphics,VehicleHudPlacement.Box box) {
        var client=Minecraft.getInstance();int y=0;
        int logicalWidth=Math.max(1,(int)(box.width()/decorationScale));
        graphics.pose().pushMatrix();graphics.pose().translate(box.x(),box.y());graphics.pose().scale(decorationScale);
        try {
            for(var hint:AutomobileHud.CONTROL_HINTS) {
                var key=hintBinding(hint,client.options);var text=Component.empty().append(key).append("  ").append(hint.getText());
                var fitted=client.font.plainSubstrByWidth(text.getString(),Math.max(1,logicalWidth-8));
                graphics.fill(0,y,Math.min(logicalWidth,client.font.width(fitted)+8),y+11,0xBF111111);
                graphics.text(client.font,Component.literal(fitted),4,y+1,0xFFFFFFFF,false);y+=11;
            }
        } finally {graphics.pose().popMatrix();}
    }
}
