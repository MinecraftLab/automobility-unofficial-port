package io.github.foundationgames.automobility.client.dashboard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.api.dashboard.*;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.RuntimeMetadata018;
import java.util.Locale;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/** Physical cockpit drawing consumes an immutable extraction snapshot. It never controls or opens audio. */
public final class VehicleCabinDashboard {
    private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final int FULL_BRIGHT = 0x00f000f0;
    private record Mount(float x, float y, float z, float width, float height, int accent) {}
    // Authored OBJ rear dashboard faces, in actual model metres; final camera/occlusion calibration is still required.
    private static final Map<String, Mount> MOUNTS = Map.of(
        "automobility:frame/mx5_na", new Mount(-.14f, -.505f, -.2585f, .28f, .125f, 0xffffb449),
        "automobility:frame/defender_90", new Mount(-.14f, -.770f, -.495f, .28f, .110f, 0xff95d48b),
        "automobility:frame/vw_t3", new Mount(-.14f, -.868f, -1.748f, .28f, .110f, 0xffffb449));
    public record Captured(float raise, float qx, float qy, float qz, float qw, float yaw,
                           float bodyOffset, float x, float y, float z, float width, float height,
                           int ambientLight, int accent, String rowA, String rowB, String rowC, String rowD, float vu, long revision,
                           int decorationRgb, float decorationIntensity, float brightness,CabinTuning027.Face face,CabinTuning027.Speakers speakers,
                           boolean tapeInserted,int tapeRgb,float leftCoil,float rightCoil, boolean progressAvailable, float progress, Identifier logoTexture,InfotainmentTheme.Palette palette) {
        public Captured(float raise,float qx,float qy,float qz,float qw,float yaw,float bodyOffset,float x,float y,float z,float width,float height,
                int ambientLight,int accent,String rowA,String rowB,String rowC,String rowD,float vu,long revision,int decorationRgb,float decorationIntensity,
                float brightness,CabinTuning027.Face face,CabinTuning027.Speakers speakers,boolean tapeInserted,int tapeRgb,float leftCoil,float rightCoil,
                boolean progressAvailable,float progress,Identifier logoTexture){
            this(raise,qx,qy,qz,qw,yaw,bodyOffset,x,y,z,width,height,ambientLight,accent,rowA,rowB,rowC,rowD,vu,revision,decorationRgb,decorationIntensity,
                    brightness,face,speakers,tapeInserted,tapeRgb,leftCoil,rightCoil,progressAvailable,progress,logoTexture,InfotainmentTheme.palette(accent&0xffffff,1));
        }
        /** Compatibility with the previous physical render snapshot constructor. */
        public Captured(float raise, float qx, float qy, float qz, float qw, float yaw,
                           float bodyOffset, float x, float y, float z, float width, float height,
                           int ambientLight, int accent, String rowA, String rowB, String rowC, String rowD, float vu, long revision,
                           int decorationRgb, float decorationIntensity, float brightness,CabinTuning027.Face face,CabinTuning027.Speakers speakers,
                           boolean tapeInserted,int tapeRgb,float leftCoil,float rightCoil) {
            this(raise, qx, qy, qz, qw, yaw, bodyOffset, x, y, z, width, height, ambientLight, accent, rowA, rowB, rowC, rowD, vu, revision, decorationRgb, decorationIntensity, brightness, face, speakers, tapeInserted, tapeRgb, leftCoil, rightCoil, false, 0, null);
        }
        public Captured {
            if (!Float.isFinite(progress) || progress < 0 || progress > 1 || !progressAvailable && progress != 0)
                throw new IllegalArgumentException("invalid captured dashboard progress");
        }
    }
    private VehicleCabinDashboard() {}
    private record Lighting(double sampledTick,float night) { }
    private static final Map<AutomobileEntity,Lighting> LIGHTING=new WeakHashMap<>();
    private static float night(AutomobileEntity car,CabinPreferences022.Settings settings,float delta,int packedLight){
        float sky=Math.clamp(car.level().getSkyDarken()/11f,0,1);
        float ambient=1-Math.max(((packedLight>>>4)&15)/15f,((packedLight>>>20)&15)/15f*(1-sky));
        boolean manualHeadlights=car.getVehicleLightMode()!=io.github.foundationgames.automobility.automobile.light.VehicleLightMode.AUTO
                &&car.getEffectiveVehicleLightMode().emitsHeadlightBeam();
        float target=switch(settings.lighting()){case DAY->0;case NIGHT->1;case AUTO->Math.max(Math.max(sky,ambient),manualHeadlights?1:0);};
        double now=car.level().getGameTime()+delta;var previous=LIGHTING.get(car);
        float value=previous==null||now<previous.sampledTick()?target:(float)(previous.night()+(target-previous.night())*(1-Math.exp(-Math.min(5,now-previous.sampledTick())*.15)));
        LIGHTING.put(car,new Lighting(now,value));return value;
    }

    /** Extraction thread only. An absent provider or unsupported frame has no added radio geometry. */
    public static Captured capture(AutomobileEntity car, AutomobileEntityRenderer.State state) {
        if (state.isInvisible || state.distanceToCameraSq > 24 * 24 || car.isRemoved() || car.getFrame().isEmpty() || GarageTransferAccess.blocked(car)) return null;
        var mount = MOUNTS.get(car.getFrame().model().modelId().toString());
        if (mount == null) return null;
        CabinSettingsClient022.observe(car);
        var preferences=CabinSettingsClient022.preferences(car).orElse(null);
        if(preferences==null||!preferences.enabled())return null;
        float night=night(car,preferences,state.tickDelta,state.lightCoords);
        float brightness=preferences.dayBrightness()+(preferences.nightBrightness()-preferences.dayBrightness())*night;
        if(brightness<=0)return null;
        CabinTuningClient027.observe(car);
        var tuning=CabinTuningClient027.preferences(car).orElse(CabinTuning027.Settings.defaults());
        var radio=VehicleDashboardClient.snapshotForLoadedCar(car).orElse(null);
        var metadata=CoreDrivetrainBridge.clientMetadata(car).orElse(null);
        var mechanical=CoreDrivetrainBridge.clientSnapshot(car).orElse(null);
        boolean paired=metadata!=null&&metadata.pairs(mechanical);
        var measured=paired?metadata.measured():null;
        var presentation=radio==null?null:CabinMediaPresentation060.capture(radio.media());
        String status=presentation==null?"RADIO --":text("media_status."+presentation.status().name().toLowerCase(Locale.ROOT),presentation.status().fallback());
        boolean activeMedia=presentation!=null&&(presentation.status()==CabinMediaPresentation060.DisplayStatus.PLAY||presentation.status()==CabinMediaPresentation060.DisplayStatus.LIVE);
        String station=presentation==null?text("radio_unavailable","Radio unavailable"):presentation.station();
        if(station.isBlank())station="RADIO "+status;
        String track=presentation==null?"":presentation.title();
        String artist=presentation==null?"":presentation.artist();
        Identifier logo=preferences.page()==CabinPreferences022.Page.POWER||presentation==null||presentation.logoTexture().isEmpty()?null:Identifier.tryParse(presentation.logoTexture());
        if(logo!=null&&Minecraft.getInstance().getResourceManager().getResource(logo).isEmpty())logo=null;
        boolean progressAvailable=preferences.page()!=CabinPreferences022.Page.POWER&&presentation!=null&&presentation.progressAvailable();
        float progress=progressAvailable?(float)presentation.progress():0;
        String tape=radio==null?"":radio.cassette().map(c->switch(c.state()){
            case EMPTY->text("tape_empty","No cassette");case OCCUPIED->label("tape_label","TAPE %s",c.label());
            case PENDING_DROP->text("tape_pending","Cassette pending repair");case UNRECOGNIZED->text("tape_unknown","Cassette unavailable");
        }).orElse("");
        String range=mechanical==null?"--":mechanical.parkingEngaged()?"P":mechanical.gear()<0?"R":mechanical.gear()==0?"N":paired&&measured.kind()==RuntimeMetadata018.Kind.ELECTRIC?"D":Integer.toString(mechanical.gear());
        String rpm=paired?label("rpm_range","%s rpm %s",String.format(Locale.ROOT,"%.0f",Units.radiansPerSecondToRpm(mechanical.engineOmega())),range):"RPM -- "+range;
        String speed=label("speed_range","%s km/h · %s",String.format(Locale.ROOT,"%.0f",Math.abs(mechanical==null?car.getEffectiveSpeed()*72:mechanical.speedMetresPerSecond()*3.6)),range);
        var energy=io.github.foundationgames.automobility.client.vehicle.VehicleEnergyHud.reading(car);
        String energyLine=energy.known()?label(energy.kind()==io.github.foundationgames.automobility.client.vehicle.VehicleEnergyReading.Kind.BATTERY?"battery_percent":"fuel_percent",energy.kind()==io.github.foundationgames.automobility.client.vehicle.VehicleEnergyReading.Kind.BATTERY?"Battery %s%%":"Fuel %s%%",energy.percent()):text("energy_unknown","Energy --");
        String availability=measured==null?text("power_unavailable","Power unavailable"):measured.available()?"":text("availability."+measured.availability().name().toLowerCase(Locale.ROOT),switch(measured.availability()){
            case OWNER_UNAVAILABLE->"Energy system unavailable";case DENIED->"Energy unavailable";case UNKNOWN_PROFILE->"Profile unavailable";default->"Power unavailable";
        });
        String power=availability,torque="",powerTorque=availability;
        if(measured!=null&&measured.available()){
            double watts=measured.kind()==RuntimeMetadata018.Kind.COMBUSTION?measured.combustionPowerWatts():measured.electricShaftPowerWatts();
            power=label("shaft_power","%s kW shaft",String.format(Locale.ROOT,"%.1f",watts/1000));
            torque=measured.kind()==RuntimeMetadata018.Kind.COMBUSTION?label("torque","%s Nm",String.format(Locale.ROOT,"%.0f",measured.combustionTorqueNm()))
                    :label("ev_torque","T %s regen %s Nm",String.format(Locale.ROOT,"%.0f",measured.driveTorqueNm()),String.format(Locale.ROOT,"%.0f",measured.regenerationTorqueNm()));
            powerTorque=label("power_torque","%s kW · %s Nm",String.format(Locale.ROOT,"%.1f",watts/1000),String.format(Locale.ROOT,"%.0f",measured.kind()==RuntimeMetadata018.Kind.COMBUSTION?measured.combustionTorqueNm():measured.driveTorqueNm()-measured.regenerationTorqueNm()));
        }
        boolean recordedTape=radio!=null&&radio.cassette().map(value->value.state()==DashboardCassetteSnapshot.State.OCCUPIED).orElse(false);
        String a,b,c,d;
        switch(preferences.page()){
            case POWER->{a=speed;b=rpm;c=powerTorque;d=energyLine;}
            case RADIO->{a=station;b=track.isBlank()?artist:track;c=track.isBlank()||artist.isBlank()?tape:artist;d=track.isBlank()||artist.isBlank()||tape.isBlank()?status:status+" · "+tape;}
            default->{a=speed;b=rpm;c=station;d=trackLine(Minecraft.getInstance().font,track,artist,logo==null?104:84);}
        }
        var font=Minecraft.getInstance().font;
        int textWidth=logo==null?104:84;
        a=font.plainSubstrByWidth(a,textWidth);b=font.plainSubstrByWidth(b,textWidth);c=font.plainSubstrByWidth(c,textWidth);d=font.plainSubstrByWidth(d,textWidth);
        float vu=radio!=null&&radio.analysis().available()&&activeMedia&&preferences.page()!=CabinPreferences022.Page.POWER?radio.analysis().level():0;
        float offset=-car.getWheels().model().radius()/16+car.getSuspensionBounce(state.tickDelta)*.048f
                +(car.engineRunning()?(float)(Math.cos((car.getTime()+state.tickDelta)*2.7)/156):0);
        float width=mount.width(),height=mount.height();
        if(tuning.face()==CabinTuning027.Face.DIGITAL){width=mount.width()+.07f;height=mount.height()+.05f;}
        var palette=InfotainmentTheme.palette(tuning.accentRgb()<0?preferences.accentRgb():tuning.accentRgb(),night);
        return new Captured(state.verticalOffset,state.angularRotation.x(),state.angularRotation.y(),state.angularRotation.z(),state.angularRotation.w(),
                state.yaw,offset,mount.x()+(mount.width()-width)*.5f,mount.y()+(mount.height()-height)*.5f,mount.z(),width,height,state.lightCoords,
                tint(palette.accent(),brightness),a,b,c,d,vu,radio==null?0:radio.media().revision(),
                radio==null?0:radio.decoration().rgb(),radio==null?0:radio.decoration().intensity()*brightness,brightness,tuning.face(),tuning.speakers(),
                recordedTape,radio==null?0:radio.cassette().map(DashboardCassetteSnapshot::labelRgb).orElse(0),
                radio==null?0:radio.cassetteMotion().map(DashboardCassetteMotion027::leftAngle).orElse(0f),
                radio==null?0:radio.cassetteMotion().map(DashboardCassetteMotion027::rightAngle).orElse(0f),progressAvailable,progress,logo,palette);
    }
    /** Both names receive a share of the fixed row budget; a long title cannot erase the artist. */
    private static String trackLine(Font font,String title,String artist,int width){
        if(title.isBlank())return font.plainSubstrByWidth(artist,width);
        if(artist.isBlank())return font.plainSubstrByWidth(title,width);
        String separator=" · ";var budget=CabinMediaPresentation060.textBudget(width,font.width(separator),font.width(title),font.width(artist));
        return font.plainSubstrByWidth(title,budget.titlePixels())+separator+font.plainSubstrByWidth(artist,budget.artistPixels());
    }
    private static String label(String key,String fallback,Object...args){return Component.translatableWithFallback("vehicle.automobility.cabin."+key,fallback,args).getString();}
    private static String text(String key,String fallback){return Component.translatableWithFallback("vehicle.automobility.cabin."+key,fallback).getString();}
    private static int tint(int rgb,float brightness){return 0xff000000|((int)(((rgb>>16)&255)*brightness)<<16)|((int)(((rgb>>8)&255)*brightness)<<8)|(int)((rgb&255)*brightness);}

    public static void submit(Captured captured, PoseStack pose, SubmitNodeCollector collector) {
        if (captured == null) return;
        pose.pushPose();
        pose.translate(0, captured.raise(), 0); pose.rotate(new Quaternionf(captured.qx(), captured.qy(), captured.qz(), captured.qw()));
        pose.rotate(Axis.ZP.rotationDegrees(180)); pose.rotate(Axis.YP.rotationDegrees(captured.yaw() + 180));
        pose.translate(0, captured.bodyOffset(), 0); pose.translate(captured.x(), captured.y(), captured.z());
        float width = captured.width(), height = captured.height();
        var layout=CabinDisplayLayout.forPanel(width,height,captured.face()==CabinTuning027.Face.CASSETTE,captured.progressAvailable());
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (transform, buffer) -> {
            quad(transform, buffer, -.008f, -.008f, width + .008f, height + .008f, 0, captured.face()==CabinTuning027.Face.RETRO?0xff757878:captured.face()==CabinTuning027.Face.DIGITAL?0xff11171e:0xff23282a, captured.ambientLight());
            // Authored panel parts use the same depth-tested car transform and a fixed geometry budget.
            float grille=switch(captured.speakers()){case COMPACT->.016f;case TOURING->.024f;case STUDIO->.032f;};
            for(int side=0;side<2;side++){
                float gx=side==0?-grille-.014f:width+.014f;
                quad(transform,buffer,gx,0,gx+grille,height,.001f,0xff303638,captured.ambientLight());
                for(int line=1;line<=6;line++){float gy=height*line/7;quad(transform,buffer,gx+.002f,gy,gx+grille-.002f,gy+.0015f,.002f,0xff090e10,captured.ambientLight());}
            }
            if(captured.face()==CabinTuning027.Face.RETRO){
                for(int tick=0;tick<15;tick++){float tx=.035f+tick*(width-.07f)/15;quad(transform,buffer,tx,height-.034f,tx+.001f,height-.028f,.002f,0xff9daba1,captured.ambientLight());}
                disc(transform,buffer,.014f,height-.015f,.009f,.003f,0xffb9b7a6,captured.ambientLight(),0);
                disc(transform,buffer,width-.014f,height-.015f,.009f,.003f,0xffb9b7a6,captured.ambientLight(),0);
            }
            if(captured.face()==CabinTuning027.Face.CASSETTE){
                float x0=width*.73f,x1=width-.01f,cy=height*.45f;
                quad(transform,buffer,x0,.012f,x1,height-.032f,.003f,0xff1b1b18,captured.ambientLight());
                // The metal cassette slot and lip remain recognisable with no tape inserted.
                quad(transform,buffer,x0+.002f,height-.041f,x1-.002f,height-.035f,.004f,0xff9ba2a0,captured.ambientLight());
                quad(transform,buffer,x0+.004f,cy-.004f,x1-.004f,cy+.004f,.0041f,0xff070909,captured.ambientLight());
                if(captured.tapeInserted()){
                    quad(transform,buffer,x0+.003f,.015f,x1-.003f,height-.035f,.004f,0xff000000|captured.tapeRgb(),captured.ambientLight());
                    disc(transform,buffer,x0+(x1-x0)*.28f,cy,.010f,.005f,0xffced4cd,captured.ambientLight(),captured.leftCoil());
                    disc(transform,buffer,x0+(x1-x0)*.72f,cy,.010f,.005f,0xffced4cd,captured.ambientLight(),captured.rightCoil());
                }
            }
            for (int index = 0; index < 6; index++) {
                float x = .016f + index * (width - .032f) / 6;
                quad(transform, buffer, x, height - .019f, x + (width - .044f) / 6, height - .009f, .0015f, 0xff596166, captured.ambientLight());
            }
        });
        float vu = Math.clamp(captured.vu(), 0, 1);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (transform, buffer) -> {
            quad(transform,buffer,.008f,.008f,width-.008f,height-.027f,.001f,tint(captured.palette().surface(),captured.brightness()),FULL_BRIGHT);
            if (captured.decorationIntensity() > 0) {
                int rgb = captured.decorationRgb();
                float strength = captured.decorationIntensity();
                int color = 0xff000000 | ((int)(((rgb >> 16) & 255) * strength) << 16)
                        | ((int)(((rgb >> 8) & 255) * strength) << 8) | (int)((rgb & 255) * strength);
                quad(transform, buffer, -.006f, -.006f, width + .006f, -.002f, .002f, color, FULL_BRIGHT);
                quad(transform, buffer, -.006f, height + .002f, width + .006f, height + .006f, .002f, color, FULL_BRIGHT);
            }
            for (int index = 0; index < 8; index++) if (vu * 8 > index) {
                float x = width - (.014f + index * .009f + .006f);
                quad(transform, buffer, x, height - .019f, x + .006f, height - .009f, .002f,
                        index < 6 ? captured.accent() : tint(0xe87b55,captured.brightness()), FULL_BRIGHT);
            }
        });
        // One bounded local logo quad and at most two progress quads per captured panel.
        if(captured.progressAvailable())collector.submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(WHITE),(transform,buffer)->{
            float x0=.014f,x1=.014f+(width-.028f)*(captured.face()==CabinTuning027.Face.CASSETTE?.67f:1),railY=height-.036f;
            quad(transform,buffer,x0,railY,x1,railY+.0015f,.0024f,tint(0x33443b,captured.brightness()),FULL_BRIGHT);
            if(captured.progress()>0)quad(transform,buffer,layout.progressX0(captured.progress()),railY,x1,railY+.0015f,.0025f,captured.accent(),FULL_BRIGHT);
        });
        if(captured.logoTexture()!=null)collector.submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(captured.logoTexture()),(transform,buffer)->{
            float contentWidth=(width-.028f)*(captured.face()==CabinTuning027.Face.CASSETTE?.67f:1);
            float size=layout.logoSize(),x=layout.contentX0(),y=.010f;
            int color=tint(0xffffff,captured.brightness());
            logoVertex(transform,buffer,x,y,.0026f,color,1,0);
            logoVertex(transform,buffer,x+size,y,.0026f,color,0,0);
            logoVertex(transform,buffer,x+size,y+size,.0026f,color,0,1);
            logoVertex(transform,buffer,x,y+size,.0026f,color,1,1);
        });
        pose.translate(layout.textX(),layout.textY(),layout.textZ());
        pose.scale(layout.scaleX(),layout.scaleY(),layout.scaleZ());
        collector.submitText(pose,0,0,Component.literal(captured.rowA()).getVisualOrderText(),false,Font.DisplayMode.NORMAL,FULL_BRIGHT,captured.accent(),0,0);
        collector.submitText(pose,0,10,Component.literal(captured.rowB()).getVisualOrderText(),false,Font.DisplayMode.NORMAL,FULL_BRIGHT,tint(captured.palette().text(),captured.brightness()),0,0);
        collector.submitText(pose,0,20,Component.literal(captured.rowC()).getVisualOrderText(),false,Font.DisplayMode.NORMAL,FULL_BRIGHT,captured.accent(),0,0);
        collector.submitText(pose,0,30,Component.literal(captured.rowD()).getVisualOrderText(),false,Font.DisplayMode.NORMAL,FULL_BRIGHT,tint(captured.palette().text(),captured.brightness()),0,0);
        pose.popPose();
    }
    private static void disc(PoseStack.Pose pose,VertexConsumer buffer,float x,float y,float radius,float z,int color,int light,float angle){
        for(int i=0;i<16;i++){
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            vertex(pose,buffer,x,y,z,color,light);vertex(pose,buffer,x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius,z,color,light);
            vertex(pose,buffer,x+(float)Math.cos(b)*radius,y+(float)Math.sin(b)*radius,z,color,light);vertex(pose,buffer,x,y,z,color,light);
        }
        float dx=(float)Math.cos(angle)*radius*.65f,dy=(float)Math.sin(angle)*radius*.65f;
        vertex(pose,buffer,x-dy*.13f,y+dx*.13f,z+.0003f,0xff242b2c,light);vertex(pose,buffer,x+dx-dy*.13f,y+dy+dx*.13f,z+.0003f,0xff242b2c,light);
        vertex(pose,buffer,x+dx+dy*.13f,y+dy-dx*.13f,z+.0003f,0xff242b2c,light);vertex(pose,buffer,x+dy*.13f,y-dx*.13f,z+.0003f,0xff242b2c,light);
    }
    private static void logoVertex(PoseStack.Pose pose,VertexConsumer buffer,float x,float y,float z,int color,float u,float v){
        buffer.addVertex(pose,x,y,z).setColor((color>>16)&255,(color>>8)&255,color&255,255)
                .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose,0,0,1);
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z, int color, int light) {
        // Match the visible logo face and the declared +Z normal.
        vertex(pose, buffer, x0, y0, z, color, light); vertex(pose, buffer, x1, y0, z, color, light);
        vertex(pose, buffer, x1, y1, z, color, light); vertex(pose, buffer, x0, y1, z, color, light);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color, int light) {
        buffer.addVertex(pose, x, y, z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                .setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }
}
