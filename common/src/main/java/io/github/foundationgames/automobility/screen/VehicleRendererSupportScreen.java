package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.client.lighting.VehicleRendererDiagnostics;
import io.github.foundationgames.automobility.client.lighting.VehicleRenderPassPolicy;
import io.github.foundationgames.automobility.client.vehicle.VehicleEffectsLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Actual runtime information, read only; opening this screen cannot change renderer or shader settings. */
public final class VehicleRendererSupportScreen extends Screen {
    private final Screen parent;
    private VehicleEffectsLayout layout;
    private VehicleRendererDiagnostics.Snapshot snapshot;
    public VehicleRendererSupportScreen(Screen parent) { super(text("title")); this.parent=parent; }
    @Override protected void init() {
        layout=VehicleEffectsLayout.resolve(width,height);
        snapshot=VehicleRendererDiagnostics.capture();
        if(layout.small()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"),ignored->onClose())
                    .bounds(Math.max(0,width/2-60),Math.max(0,height-26),Math.min(120,width),20).build());
            return;
        }
        footer(0,text("copy"),()->minecraft.keyboardHandler.setClipboard(snapshot.toString()));
        footer(1,text("refresh"),()->snapshot=VehicleRendererDiagnostics.capture());
        footer(2,Component.translatable("gui.back"),this::onClose);
    }
    private void footer(int index,Component label,Runnable action) {
        var b=layout.footer(index);
        addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(b.x(),b.y(),b.width(),b.height()).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0xEE162128);
        if(layout.small()) graphics.textWithWordWrap(font,Component.translatable("vehicle.automobility.too_small"),
                12,12,Math.max(1,width-24),0xFFE4EDF1);
        else {
            graphics.text(font,title,layout.left()+16,layout.top()+10,0xFFE4EDF1,false);
            var observation=snapshot.direct().observation();
            Component direct=text(observation!=null && observation.enabled() && observation.armed()?"prepared":"standard");
            var rows=List.of(
                    Component.translatable("vehicle.automobility.renderer.backend",snapshot.backend(),snapshot.gpu()),
                    Component.translatable("vehicle.automobility.renderer.iris",snapshot.iris().isEmpty()?text("absent"):Component.literal(snapshot.iris()),
                            snapshot.shaderPackName().isEmpty()?text("pack."+snapshot.shaderPack()):Component.literal(snapshot.shaderPackName())),
                    text(snapshot.pass()==VehicleRenderPassPolicy.Pass.API_UNAVAILABLE?"suppressed":"projection_available"),
                    Component.translatable("vehicle.automobility.renderer.temporal",snapshot.temporal().active(),snapshot.temporal().reason()),
                    Component.translatable("vehicle.automobility.renderer.direct",direct,observation==null?0:observation.pushes()),
                    text(VehicleRendererDiagnostics.backend(snapshot.backend())==VehicleRendererDiagnostics.Backend.VULKAN?"vulkan_unverified":"scope"));
            for(int index=0;index<rows.size();index++) row(graphics,rows.get(index),43+index*22,mouseX,mouseY);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private void row(GuiGraphicsExtractor graphics,Component value,int y,int mouseX,int mouseY) {
        int x=layout.left()+16,top=layout.top()+y,available=layout.width()-32;
        var lines=font.split(value,available);
        for(int line=0;line<Math.min(2,lines.size());line++) graphics.text(font,lines.get(line),x,top+line*10,0xFFA7BAC5,false);
        if(mouseX>=x && mouseX<x+available && mouseY>=top && mouseY<top+20)
            graphics.setTooltipForNextFrame(font,value,mouseX,mouseY);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    private static Component text(String key){return Component.translatable("vehicle.automobility.renderer."+key);}
}
