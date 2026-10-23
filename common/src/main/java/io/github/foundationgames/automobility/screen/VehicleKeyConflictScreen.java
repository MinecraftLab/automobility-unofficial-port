package io.github.foundationgames.automobility.screen;
import io.github.foundationgames.automobility.client.vehicle.VehicleKeyConflictPolicy;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Uses actual registered mappings, including foreign mods, with no automatic remapping. */
public final class VehicleKeyConflictScreen extends Screen {
    public record Pair(KeyMapping first,KeyMapping second,VehicleKeyConflictPolicy.Kind kind) {}
    private final Screen parent;
    private List<Pair> pairs=List.of();
    private int page,perPage,left,top,panelWidth,panelHeight;
    public VehicleKeyConflictScreen(Screen parent) {super(text("keys.title"));this.parent=parent;}
    public static List<Pair> find(KeyMapping[] mappings) {
        var result=new ArrayList<Pair>();var seen=new HashSet<String>();
        for(var a:mappings) if(VehicleKeyConflictPolicy.owned(a.getName()) && !a.isUnbound()) for(var b:mappings) {
            if(a==b || a.getName().equals(b.getName()) || b.isUnbound() || !a.same(b)) continue;
            String key=a.getName().compareTo(b.getName())<0?a.getName()+"\n"+b.getName():b.getName()+"\n"+a.getName();
            if(seen.add(key)) result.add(new Pair(a,b,VehicleKeyConflictPolicy.classify(a.getName(),b.getName())));
        }
        result.sort(Comparator.comparing(Pair::kind));return List.copyOf(result);
    }
    @Override protected void init() {
        pairs=find(minecraft.options.keyMappings);panelWidth=Math.max(1,Math.min(430,width-16));panelHeight=Math.max(1,Math.min(300,height-16));
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;perPage=Math.max(1,(panelHeight-116)/32);
        page=Math.clamp(page,0,Math.max(0,(pairs.size()-1)/perPage));
        int half=(panelWidth-36)/2;
        var previous=addRenderableWidget(Button.builder(text("keys.previous"),ignored->{page--;rebuildWidgets();}).bounds(left+16,top+panelHeight-66,half,20).build());previous.active=page>0;
        var next=addRenderableWidget(Button.builder(text("keys.next"),ignored->{page++;rebuildWidgets();}).bounds(left+20+half,top+panelHeight-66,half,20).build());next.active=(page+1)*perPage<pairs.size();
        addRenderableWidget(Button.builder(Component.translatable("ui.automobility.keybinds"),ignored->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options)))
                .bounds(left+16,top+panelHeight-30,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),ignored->onClose()).bounds(left+20+half,top+panelHeight-30,half,20).build());
    }
    @Override public void onClose() {minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
    @Override public boolean isInGameUi() {return minecraft!=null && minecraft.level!=null;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float delta) {
        graphics.fill(0,0,width,height,0x88101010);AutomobilityUiTheme.panel(graphics,left,top,panelWidth,panelHeight);
        graphics.text(font,title,left+16,top+12,AutomobilityUiTheme.TEXT,false);
        long conflicts=pairs.stream().filter(p->p.kind()==VehicleKeyConflictPolicy.Kind.CONFLICT).count();
        graphics.text(font,Component.translatable("ui.automobility.keys.count",conflicts),left+16,top+29,AutomobilityUiTheme.TEXT,false);
        if(pairs.isEmpty()) graphics.textWithWordWrap(font,text("keys.none"),left+16,top+55,panelWidth-32,AutomobilityUiTheme.TEXT);
        for(int i=0;i<perPage && page*perPage+i<pairs.size();i++) {
            var p=pairs.get(page*perPage+i);int y=top+52+i*32;
            var label=Component.empty().append(p.first().getTranslatedKeyMessage()).append(" • ").append(Component.translatable(p.first().getName()));
            var detail=Component.translatable(p.second().getName());
            graphics.text(font,GarageTextLayout.fit(font,label.getString(),panelWidth-32).visualOrder(),left+16,y,AutomobilityUiTheme.TEXT,false);
            graphics.text(font,GarageTextLayout.fit(font,detail.getString(),panelWidth-32).visualOrder(),left+16,y+10,AutomobilityUiTheme.MUTED,false);
            graphics.text(font,text("keys."+p.kind().name().toLowerCase(Locale.ROOT)),left+16,y+20,p.kind()==VehicleKeyConflictPolicy.Kind.CONFLICT?0xFF7C2525:0xFF285B25,false);
            if(mx>=left+16 && mx<left+panelWidth-16 && my>=y && my<y+30) graphics.setTooltipForNextFrame(font,Component.empty().append(label).append("\n").append(detail).append("\n").append(text("keys.no_remap")),mx,my);
        }
        super.extractRenderState(graphics,mx,my,delta);
    }
    private static Component text(String key) {return Component.translatable("ui.automobility."+key);}
}
