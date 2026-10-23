package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.access.VehicleAccessState;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.network.VehicleAccessPackets.Action;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.UUID;

public final class VehicleAccessScreen extends Screen {
    private final Screen parent;
    private final AutomobileEntity car;
    private int left,top,panelWidth,panelHeight,page,selected;
    private boolean small;
    private EditBox name;
    private Button claim,lock,publicDrive,publicConfigure,sharedConfigure,add,choose,remove,sound;
    public VehicleAccessScreen(Screen parent,AutomobileEntity car) {
        super(text("title")); this.parent=parent;this.car=car;
    }
    @Override protected void init() {
        claim=lock=publicDrive=publicConfigure=sharedConfigure=add=choose=remove=sound=null;name=null;
        panelWidth=Math.max(1,Math.min(360,width-16));panelHeight=Math.max(1,Math.min(252,height-16));
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;small=panelWidth<240||panelHeight<224;
        if (!small) {
            for(int i=0;i<3;i++) {
                final int target=i;int w=(panelWidth-40)/3;
                var b=addRenderableWidget(Button.builder(text(new String[]{"general","sharing","sounds"}[i]),ignored->{page=target;rebuildWidgets();})
                    .bounds(left+16+i*(w+4),top+44,w,20).build());b.active=i!=page;
            }
            if(page==0) {
                claim=button(74,()->request(car.getVehicleAccess().owner()==null?Action.CLAIM:Action.RELEASE,0,""));
                claim.setTooltip(Tooltip.create(text("claim_help")));
                lock=button(99,()->request(Action.LOCK,car.getVehicleAccess().locked()?0:1,""));
                publicDrive=button(124,()->request(Action.PUBLIC_DRIVE,car.getVehicleAccess().publicDrive()?0:1,""));
                publicConfigure=button(149,()->request(Action.PUBLIC_CONFIGURE,car.getVehicleAccess().publicConfigure()?0:1,""));
                sharedConfigure=button(174,()->request(Action.SHARED_CONFIGURE,car.getVehicleAccess().sharedConfigure()?0:1,""));
            } else if(page==1) {
                name=addRenderableWidget(new EditBox(font,left+16,top+74,panelWidth-32,20,text("name_hint")));
                name.setMaxLength(36);name.setHint(text("name_hint"));
                add=button(99,()->{request(Action.ADD_KEY,0,name.getValue().strip());name.setValue("");});
                add.setMessage(text("add_key"));add.setTooltip(Tooltip.create(text("name_help")));
                choose=button(124,()->{selected++;updateState();});
                remove=button(149,()->{var key=selectedKey();if(key!=null)request(Action.REMOVE_KEY,0,key.id().toString());});
                remove.setMessage(text("remove_key"));
            } else {
                sound=button(74,()->request(Action.FEEDBACK,(car.getVehicleAccess().feedback().click()
                        ?VehicleAccessState.Feedback.SILENT:VehicleAccessState.Feedback.CLICK).ordinal(),""));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),ignored->onClose())
                .bounds(Math.max(0,left+16),Math.max(0,top+panelHeight-28),Math.max(1,panelWidth-32),20).build());
        updateState();
    }
    private Button button(int y,Runnable action) {return addRenderableWidget(Button.builder(Component.empty(),ignored->action.run()).bounds(left+16,top+y,panelWidth-32,20).build());}
    private void request(Action action,int value,String target) {VehicleClientControls.requestAccess(car,action,value,target);}
    private VehicleAccessState.TrustedDriver selectedKey() {
        var keys=car.getVehicleAccess().trusted();return keys.isEmpty()?null:keys.get(Math.floorMod(selected,keys.size()));
    }
    private void updateState() {
        if(minecraft.player==null)return;var a=car.getVehicleAccess();UUID id=minecraft.player.getUUID();
        boolean owner=a.isOwner(id),near=VehicleClientControls.canUseMenu(minecraft,car);
        if(claim!=null) {
            claim.active=near&&(a.owner()==null?(car.getPassengers().isEmpty()||car.getFirstPassenger()==minecraft.player):owner&&!a.locked()&&car.getPassengers().isEmpty());
            claim.setMessage(text(a.owner()==null?"claim":"release"));
            lock.active=near&&a.owner()!=null&&a.hasKey(id);lock.setMessage(text(a.locked()?"unlock_button":"lock_button"));
            publicDrive.active=publicConfigure.active=sharedConfigure.active=near&&owner;
            publicConfigure.active&=a.publicDrive();
            publicDrive.setMessage(option("public_drive",a.publicDrive()));
            publicConfigure.setMessage(option("public_configure",a.publicConfigure()));
            sharedConfigure.setMessage(option("shared_configure",a.sharedConfigure()));
        }
        if(add!=null) {
            add.active=near&&owner&&!name.getValue().isBlank()&&a.trusted().size()<VehicleAccessState.MAX_TRUSTED;
            var key=selectedKey();choose.active=key!=null;
            choose.setMessage(key==null?text("no_keys"):Component.translatable("vehicle.automobility.access.selected_key",key.name()));
            choose.setTooltip(key==null?null:Tooltip.create(Component.literal(key.id().toString())));
            remove.active=near&&owner&&key!=null;
        }
        if(sound!=null) {sound.active=near&&a.mayConfigure(id);sound.setMessage(Component.translatable("vehicle.automobility.access.sound_label",text(a.feedback().click()?"sound.click":"sound.silent")));}
    }
    @Override public void tick() {if(!VehicleClientControls.canUseMenu(minecraft,car)){minecraft.setScreen(null);return;}updateState();}
    @Override public void onClose() {minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0x9910171D);
        if(small) graphics.textWithWordWrap(font,Component.translatable("vehicle.automobility.too_small"),12,12,Math.max(1,width-24),0xFFE4EDF1);
        else {
            graphics.fill(left,top,left+panelWidth,top+panelHeight,0xFF162128);
            graphics.outline(left,top,panelWidth,panelHeight,0xFF586D7A);
            graphics.fill(left+1,top+1,left+panelWidth-1,top+35,0xFF23343F);
            graphics.text(font,title,left+16,top+10,0xFFE4EDF1,false);
            var a=car.getVehicleAccess();String label=a.owner()==null?text("unclaimed").getString():a.isOwner(minecraft.player.getUUID())?text("you_own").getString():text("owned").getString();
            graphics.text(font,Component.literal(label),left+16,top+23,0xFFA7BAC5,false);
            Component help=text(page==0?"general_help":page==1?"sharing_help":"sounds_help");
            var response=VehicleClientControls.feedbackFor(car);if(response!=null)help=response;
            var lines=font.split(help,panelWidth-32);
            int y=page==0?201:page==1?178:106;
            for(int i=0;i<Math.min(page==0?1:3,lines.size());i++)if(y+i*11+font.lineHeight<=panelHeight-30)graphics.text(font,lines.get(i),left+16,top+y+i*11,0xFFA7BAC5,false);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private static Component option(String key,boolean value){return Component.translatable("vehicle.automobility.access."+key,Component.translatable(value?"options.on":"options.off"));}
    private static Component text(String key){return Component.translatable("vehicle.automobility.access."+key);}
}
