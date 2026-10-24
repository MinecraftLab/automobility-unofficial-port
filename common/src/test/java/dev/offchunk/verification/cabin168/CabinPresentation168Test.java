package dev.offchunk.verification.cabin168;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme;
import io.github.foundationgames.automobility.client.vehicle.VehicleRiderRenderScale;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** CPU-only checks of the changed final display palette and matched avatar/eye transform. */
public final class CabinPresentation168Test {
    private static int checks;
    public static void main(String[] args) {
        finalDisplayContrast();
        matchedRiderEye();
        System.out.println("PASS_CABIN168 checks="+checks+" boundary=CPU_PRESENTATION_NOT_FABRIC_NATIVE_OR_HEARING");
    }
    private static void finalDisplayContrast() {
        var night=InfotainmentTheme.palette(InfotainmentTheme.FORMULA.accent,1);
        check(contrast(shade(night.surface(),.35f),shade(night.text(),.35f))<4.5,
                "native167 counterexample: scaling an already legible palette loses final contrast");
        int[] accents={InfotainmentTheme.FLAMINGO.accent,InfotainmentTheme.MC_GREEN.accent,
                InfotainmentTheme.NOTCH.accent,InfotainmentTheme.FORMULA.accent,0x91d8ed,0,0xffffff};
        for(int accent:accents) for(float phase:new float[]{0,.25f,.5f,.75f,1}) {
            var base=InfotainmentTheme.palette(accent,phase);
            check(InfotainmentTheme.dim(base,1).equals(base),"full backlight retains existing palette");
            var off=InfotainmentTheme.dim(base,0);
            check(off.surface()==0&&off.text()==0&&off.accent()==0,"zero brightness stays off");
            for(float brightness:new float[]{.15f,.35f,.6f,.85f}) {
                var actual=InfotainmentTheme.dim(base,brightness);
                check(actual.surface()==shade(base.surface(),brightness),"saved surface dimming preserved");
                check(contrast(actual.surface(),actual.text())>=4.5,"final text loses contrast after dimming");
                check(contrast(actual.surface(),actual.accent())>=4.5,"final station colour loses contrast after dimming");
            }
        }
        var dim=InfotainmentTheme.dim(night,.35f);
        check(luminance(dim.text())<luminance(night.text()),"readability correction retains dimmer night text");
        check(InfotainmentTheme.dim(night,Float.NaN).equals(new InfotainmentTheme.Palette(0,0,0)),"invalid backlight cannot brighten panel");
    }
    private static void matchedRiderEye() {
        var mx5=VehicleRiderRenderScale.MX5_MODEL_ID;
        var defender=VehicleRiderRenderScale.DEFENDER_MODEL_ID;
        var w4=Identifier.parse("automobility:frame/vw_t3");
        check(VehicleRiderRenderScale.cameraEyeOffset(w4,1.62)==0,"W4 eye must retain its native167 baseline");
        check(VehicleRiderRenderScale.inwardBlocks(w4,4)==0,"W4 lateral anchor unchanged");
        for(var frame:new Identifier[]{mx5,defender}) {
            double right=VehicleRiderRenderScale.inwardBlocks(frame,6);
            double left=VehicleRiderRenderScale.inwardBlocks(frame,-6);
            near(right,-left,"mirrored seats retain mirrored camera inset");
            check(right<0,"driver camera moves toward actual rendered cabin head");
            check(Math.abs(VehicleRiderRenderScale.inwardBlocks(frame,.25))<=.25/16,"small seat cannot cross cabin centre");
            check(VehicleRiderRenderScale.inwardBlocks(frame,Double.NaN)==0,"invalid resolved seat is neutral");
            check(VehicleRiderRenderScale.cameraEyeOffset(frame,Double.POSITIVE_INFINITY)==0,"invalid eye cannot corrupt camera");
        }
        PoseStack mxPose=new PoseStack(),defPose=new PoseStack(),w4Pose=new PoseStack();
        check(VehicleRiderRenderScale.apply(mx5,5.8,mxPose),"actual compact avatar transform applies to MX5");
        check(VehicleRiderRenderScale.apply(defender,6.2,defPose),"actual compact avatar transform applies to Defender");
        check(!VehicleRiderRenderScale.apply(w4,4,w4Pose),"other cabin poses keep existing transform");
        var mx=mxPose.last().pose().getTranslation(new Vector3f());
        var def=defPose.last().pose().getTranslation(new Vector3f());
        near(mx.x,VehicleRiderRenderScale.inwardBlocks(mx5,5.8),"MX5 camera and rendered inset agree");
        near(def.x,VehicleRiderRenderScale.inwardBlocks(defender,6.2),"Defender camera and rendered inset agree");
        double eye=1.62;
        near(mx.y-def.y,-(VehicleRiderRenderScale.cameraEyeOffset(mx5,eye)
                -VehicleRiderRenderScale.cameraEyeOffset(defender,eye)),"additional MX5 seat drop moves eye and avatar together");
        double hip=VehicleRiderRenderScale.VANILLA_SCALE*(1.501-12.0/16);
        near(hip+(eye-hip)*VehicleRiderRenderScale.SCALE/VehicleRiderRenderScale.VANILLA_SCALE,
                eye+VehicleRiderRenderScale.cameraEyeOffset(defender,eye),"camera scale pivots about retained model hip");
        check(VehicleRiderRenderScale.cameraEyeOffset(mx5,eye)<VehicleRiderRenderScale.cameraEyeOffset(defender,eye),
                "MX5 windscreen clearance is body-specific");
    }
    private static int shade(int rgb,float value) {
        return ((int)(((rgb>>16)&255)*value)<<16)|((int)(((rgb>>8)&255)*value)<<8)|(int)((rgb&255)*value);
    }
    private static double contrast(int a,int b) {
        double left=luminance(a),right=luminance(b);return (Math.max(left,right)+.05)/(Math.min(left,right)+.05);
    }
    private static double luminance(int rgb) {
        return .2126*channel((rgb>>16)&255)+.7152*channel((rgb>>8)&255)+.0722*channel(rgb&255);
    }
    private static double channel(int value) {double c=value/255.0;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
    private static void near(double actual,double expected,String message) {check(Math.abs(actual-expected)<.000001,message+": "+actual+" vs "+expected);}
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
}
