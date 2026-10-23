package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.VehicleConfigurationProfile;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VehicleProfilesScreen extends Screen {
    private final Screen parent;private final AutomobileEntity car;
    private int left,top,panelWidth,panelHeight,selected;private boolean small,confirmDelete,awaitingApply;
    private EditBox name;private Button save,choose,apply,delete;private Component feedback;
    private String nameDraft="";
    public VehicleProfilesScreen(Screen parent,AutomobileEntity car){super(text("title"));this.parent=parent;this.car=car;}
    @Override protected void init(){
        if(name!=null)nameDraft=name.getValue();
        save=choose=apply=delete=null;name=null;
        panelWidth=Math.max(1,Math.min(360,width-16));panelHeight=Math.max(1,Math.min(252,height-16));
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;small=panelWidth<240||panelHeight<224;
        if(!small) {
            name=addRenderableWidget(new EditBox(font,left+16,top+49,panelWidth-32,20,text("name_hint")));
            name.setMaxLength(32);name.setHint(text("name_hint"));
            name.setValue(nameDraft);
            save=button(74,text("save"),()->{
                awaitingApply=false;
                try {
                    var profile=new VehicleConfigurationProfile(name.getValue(),car.getVehicleEngineSettings(),car.getVehicleLightMode(),car.getVehicleAccess().feedback(),car.isVehicleSignalAutoCancelEnabled(),car.isVehicleComfortSignalsEnabled(),car.isVehicleLockSignalConfirmationEnabled(),car.getVehicleDrivingMode());
                    boolean saved=VehicleClientControls.saveProfile(profile);
                    feedback=text(saved?"saved":"save_failed");
                    if(saved) {selected=VehicleClientControls.profiles().indexOf(profile);name.setValue("");nameDraft="";confirmDelete=false;}
                } catch(RuntimeException error){feedback=text("invalid_name");}
                updateState();
            });
            save.setTooltip(Tooltip.create(text("save_help")));
            choose=button(99,Component.empty(),()->{selected++;confirmDelete=false;updateState();});
            apply=button(124,text("preview.open"),()->{
                var p=selectedProfile();
                if(p!=null&&VehicleClientControls.canUseMenu(minecraft,car)){
                    confirmDelete=false;
                    minecraft.setScreen(new VehicleProfilePreviewScreen(this,car,p));
                }
            });
            apply.setTooltip(Tooltip.create(text("preview.open_help")));
            delete=button(149,text("delete"),()->{
                awaitingApply=false;
                var p=selectedProfile();if(p==null)return;
                if(!confirmDelete){confirmDelete=true;updateState();return;}
                feedback=text(VehicleClientControls.deleteProfile(p)?"deleted":"save_failed");confirmDelete=false;updateState();
            });
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),ignored->onClose()).bounds(Math.max(0,left+16),Math.max(0,top+panelHeight-28),Math.max(1,panelWidth-32),20).build());
        updateState();
    }
    private Button button(int y,Component label,Runnable action){return addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(left+16,top+y,panelWidth-32,20).build());}
    private VehicleConfigurationProfile selectedProfile(){var profiles=VehicleClientControls.profiles();return profiles.isEmpty()?null:profiles.get(Math.floorMod(selected,profiles.size()));}
    private void updateState(){
        if(save==null)return;var profile=selectedProfile();
        save.active=VehicleClientControls.profilesWritable()&&!name.getValue().isBlank();
        choose.active=profile!=null;choose.setMessage(profile==null?text("empty"):Component.translatable("vehicle.automobility.profiles.selected",profile.name()));
        apply.active=profile!=null;
        delete.active=profile!=null&&VehicleClientControls.profilesWritable();delete.setMessage(text(confirmDelete?"confirm_delete":"delete"));
    }
    void awaitProfileReply(VehicleConfigurationProfile profile){
        selected=VehicleClientControls.profiles().indexOf(profile);
        awaitingApply=true;feedback=text("applying");confirmDelete=false;
    }
    @Override public void tick(){if(!VehicleClientControls.canUseMenu(minecraft,car)){minecraft.setScreen(null);return;}if(awaitingApply){var reply=VehicleClientControls.profileFeedbackFor(car);if(reply!=null){feedback=reply;awaitingApply=false;}}updateState();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fill(0,0,width,height,0x9910171D);
        if(small)graphics.textWithWordWrap(font,Component.translatable("vehicle.automobility.too_small"),12,12,Math.max(1,width-24),0xFFE4EDF1);
        else {
            graphics.fill(left,top,left+panelWidth,top+panelHeight,0xFF162128);
            graphics.outline(left,top,panelWidth,panelHeight,0xFF586D7A);
            graphics.fill(left+1,top+1,left+panelWidth-1,top+35,0xFF23343F);
            graphics.text(font,title,left+16,top+10,0xFFE4EDF1,false);
            graphics.text(font,text("subtitle"),left+16,top+23,0xFFA7BAC5,false);
            Component message=!VehicleClientControls.profilesWritable()?text("load_failed"):feedback!=null?feedback:text("help");
            var lines=font.split(message,panelWidth-32);
            for(int i=0;i<Math.min(3,lines.size());i++)if(178+i*11+font.lineHeight<=panelHeight-30)graphics.text(font,lines.get(i),left+16,top+178+i*11,0xFFA7BAC5,false);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private static Component text(String key){return Component.translatable("vehicle.automobility.profiles."+key);}
}
