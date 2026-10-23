package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.client.vehicle.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Full-viewport sample preview. Done changes the parent draft; only its Save publishes. */
public final class VehicleHudEditorScreen extends Screen {
    private enum Part { PANEL, HINTS, RESERVATION }
    private final Screen parent;
    private final Consumer<VehicleUiSettings> accepted;
    private VehicleUiSettings draft;
    private Part selected=Part.PANEL;
    private boolean dragging,resizing,keyboardRequested,chromeHidden;
    private Button chrome,anchorButton;
    private final java.util.EnumMap<Part,Button> parts=new java.util.EnumMap<>(Part.class);
    private void updateParts() {
        parts.forEach((part,button)->button.active=part!=selected);
        if(anchorButton!=null) anchorButton.setMessage(text(selected==Part.RESERVATION?"editor.reservation_toggle":"editor.anchor"));
    }
    private void focusAfterAction() {if(keyboardRequested || chromeHidden) {setFocused(null);keyboardRequested=false;}}
    private double lastX,lastY;
    public VehicleHudEditorScreen(Screen parent,VehicleUiSettings draft,Consumer<VehicleUiSettings> accepted) {
        super(text("editor.title"));this.parent=parent;this.draft=draft;this.accepted=accepted;
    }
    @Override protected void init() {
        dragging=false;parts.clear();
        int w=Math.max(1,Math.min(140,(width-24)/3));int x=(width-(w*3+8))/2;
        for(var value:Part.values()) {
            var button=addRenderableWidget(Button.builder(text("editor.part."+value.name().toLowerCase(java.util.Locale.ROOT)),ignored -> {
                selected=value;dragging=false;updateParts();
            }).bounds(x+value.ordinal()*(w+4),6,w,20).build());
            parts.put(value,button);button.active=selected!=value;
        }
        anchorButton=addRenderableWidget(Button.builder(text(selected==Part.RESERVATION?"editor.reservation_toggle":"editor.anchor"),ignored -> {
            if(selected==Part.RESERVATION) {var r=draft.reservation();draft=draft.withReservation(new VehicleUiSettings.Region(!r.enabled(),r.x(),r.y(),r.width(),r.height()));}
            else {
                var p=selected==Part.PANEL?draft.panel():draft.hints();var all=VehicleUiSettings.Anchor.values();
                var next=new VehicleUiSettings.Placement(all[(p.anchor().ordinal()+1)%all.length],8,selected==Part.PANEL?58:105);
                draft=selected==Part.PANEL?draft.withPanel(next):draft.withHints(next);
            }
        }).bounds(x,30,w,20).tooltip(Tooltip.create(text("editor.anchor_help"))).build());
        addRenderableWidget(Button.builder(text("editor.scale"),ignored -> draft=draft.withBasic(draft.basic().nextScale()))
                .bounds(x+w+4,30,w,20).build());
        addRenderableWidget(Button.builder(text("editor.reset"),ignored -> {draft=draft.resetLayout();dragging=false;})
                .bounds(x+2*(w+4),30,w,20).build());
        addRenderableWidget(Button.builder(text("editor.keyboard"),ignored -> {
            dragging=false;keyboardRequested=true;setFocused(null);minecraft.getNarrator().saySystemNow(text("editor.keyboard_help"));
        }).bounds(x,height-26,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),ignored -> onClose())
                .bounds(x+w+4,height-26,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored -> {
            dragging=false;accepted.accept(draft);minecraft.gui.setScreen(parent);
        }).bounds(x+2*(w+4),height-26,w,20).build());
        chrome=addRenderableWidget(Button.builder(Component.literal("UI"),ignored -> {
            chromeHidden=!chromeHidden;
            for(var child:children()) if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.visible=!chromeHidden;
            chrome.visible=true;
        }).bounds(Math.max(0,width-24),54,20,16).tooltip(Tooltip.create(text("editor.chrome_help"))).build());
        if(chromeHidden) for(var child:children()) if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.visible=widget==chrome;
    }
    private VehicleHudPlacement.Box selectedBox() {
        return switch(selected) {
            case PANEL -> UnifiedVehicleHud.panelBox(width,height,draft);
            case HINTS -> UnifiedVehicleHud.hintBox(width,height,draft);
            case RESERVATION -> VehicleHudPlacement.reservation(width,height,draft.reservation());
        };
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice) {
        if(super.mouseClicked(event,twice)) {focusAfterAction();return true;}
        if(!chromeHidden && (event.y()<54 || event.y()>=height-42) || event.button()!=0) return false;
        setFocused(null);var box=selectedBox();
        if(box.contains(event.x(),event.y())) {
            dragging=true;lastX=event.x();lastY=event.y();
            resizing=selected==Part.RESERVATION && (event.hasShiftDown() || event.x()>box.x()+box.width()-10 && event.y()>box.y()+box.height()-10);
        }
        return true;
    }
    private void move(int dx,int dy,boolean resize) {
        var box=selectedBox();
        if(selected==Part.RESERVATION) {
            var r=draft.reservation();
            int px=(int)Math.round(dx*1000.0/Math.max(1,width)),py=(int)Math.round(dy*1000.0/Math.max(1,height));
            if(dx!=0 && px==0) px=Integer.signum(dx);if(dy!=0 && py==0) py=Integer.signum(dy);
            draft=draft.withReservation(resize?new VehicleUiSettings.Region(r.enabled(),r.x(),r.y(),r.width()+px,r.height()+py)
                    :new VehicleUiSettings.Region(r.enabled(),r.x()+px,r.y()+py,r.width(),r.height()));
        } else {
            var p=VehicleHudPlacement.moved(width,height,box,box.x()+dx,box.y()+dy);
            draft=selected==Part.PANEL?draft.withPanel(p):draft.withHints(p);
        }
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(!dragging) return super.mouseDragged(event,dx,dy);
        if(Double.isFinite(event.x()) && Double.isFinite(event.y())) {
            int x=(int)Math.round(event.x()-lastX),y=(int)Math.round(event.y()-lastY);move(x,y,resizing);lastX+=x;lastY+=y;
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {boolean was=dragging;dragging=false;return was || super.mouseReleased(event);}
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape()) {onClose();return true;}
        if(getFocused()!=null) {boolean handled=super.keyPressed(event);focusAfterAction();return handled;}
        int step=event.hasControlDown()?10:1;
        if(event.isLeft() || event.isRight() || event.isUp() || event.isDown()) {
            move(event.isLeft()?-step:event.isRight()?step:0,event.isUp()?-step:event.isDown()?step:0,event.hasShiftDown());return true;
        }
        return super.keyPressed(event);
    }
    @Override public void removed() {dragging=false;super.removed();}
    @Override public void onClose() {dragging=false;minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
    @Override public boolean isInGameUi() {return minecraft!=null && minecraft.level!=null;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float delta) {
        graphics.fill(0,0,width,height,0x66101010);
        graphics.enableScissor(0,chromeHidden?0:54,width,chromeHidden?height:Math.max(55,height-42));
        try {
            UnifiedVehicleHud.drawPreview(graphics,width,height,draft);
            var reserve=VehicleHudPlacement.reservation(width,height,draft.reservation());
            if(draft.reservation().enabled()) {
                graphics.fill(reserve.x(),reserve.y(),reserve.x()+reserve.width(),reserve.y()+reserve.height(),0x4478A266);
                graphics.outline(reserve.x(),reserve.y(),reserve.width(),reserve.height(),0xFFB9D7A9);
                graphics.textWithWordWrap(font,text("editor.reservation_label"),reserve.x()+4,reserve.y()+4,Math.max(1,reserve.width()-8),0xFFFFFFFF);
            }
            var box=selectedBox();graphics.outline(box.x(),box.y(),box.width(),box.height(),0xFFFFFFFF);
        } finally {graphics.disableScissor();}
        if(!chromeHidden) {
            graphics.fill(0,0,width,54,0xEB333333);graphics.fill(0,height-42,width,height,0xEB333333);
            graphics.centeredText(font,text("editor.help"),width/2,height-39,0xFFFFFFFF);
        }
        super.extractRenderState(graphics,mx,my,delta);
    }
    private static Component text(String key) {return Component.translatable("ui.automobility."+key);}
}
