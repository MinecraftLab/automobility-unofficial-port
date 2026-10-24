package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.api.dashboard.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Actual server-confirmed per-car cosmetics; a draft changes no panel until Apply is acknowledged. */
public final class CabinSettingsScreen022 extends Screen {
    private final Screen parent;private final AutomobileEntity car;
    private CabinPreferences022.Settings draft=CabinPreferences022.Settings.defaults();
    private CabinTuning027.Settings acceptedTheme;
    private int themeDraft=-1;private boolean themeDirty;private long themeReply=-1;
    private long reply=-1;private boolean dirty,small;private int left,top,w,h,ticks;private Button enabled,page,day,night,color,lighting,apply;
    public CabinSettingsScreen022(Screen parent,AutomobileEntity car){super(text("title","Infotainment"));this.parent=parent;this.car=car;CabinSettingsClient022.query(car);CabinTuningClient027.query(car);}
    @Override protected void init(){
        w=Math.max(1,Math.min(360,width-16));h=Math.max(1,Math.min(252,height-16));left=(width-w)/2;top=(height-h)/2;small=w<260||h<244;
        enabled=page=day=night=color=lighting=apply=null;
        if(!small){
            enabled=button(70,()->change(!draft.enabled(),draft.page(),draft.dayBrightness(),draft.nightBrightness(),draft.accentRgb()));
            page=button(95,()->change(draft.enabled(),CabinPreferences022.Page.values()[(draft.page().ordinal()+1)%3],draft.dayBrightness(),draft.nightBrightness(),draft.accentRgb()));
            day=button(120,()->change(draft.enabled(),draft.page(),next(draft.dayBrightness()),draft.nightBrightness(),draft.accentRgb()));
            night=button(145,()->change(draft.enabled(),draft.page(),draft.dayBrightness(),next(draft.nightBrightness()),draft.accentRgb()));
            color=button(170,()->{themeDraft=InfotainmentTheme.next(themeDraft<0?draft.accentRgb():themeDraft);themeDirty=true;update();});
            lighting=button(195,()->{draft=new CabinPreferences022.Settings(draft.revision(),draft.enabled(),draft.page(),draft.dayBrightness(),draft.nightBrightness(),draft.accentRgb(),CabinPreferences022.Lighting.values()[(draft.lighting().ordinal()+1)%3]);dirty=true;update();});
            apply=addRenderableWidget(Button.builder(text("apply","Apply"),b->{
                if(dirty)CabinSettingsClient022.save(car,draft.next(draft.enabled(),draft.page(),draft.dayBrightness(),draft.nightBrightness(),draft.accentRgb()));
                if(themeDirty&&acceptedTheme!=null)CabinTuningClient027.save(car,acceptedTheme.nextAccent(themeDraft));
                update();
            }).bounds(left+16,top+h-28,(w-40)/2,20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(left+24+(w-40)/2,top+h-28,(w-40)/2,20).build());
        }else addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(Math.max(0,width/2-60),Math.max(0,height-26),Math.min(120,width),20).build());
        update();
    }
    private Button button(int y,Runnable action){return addRenderableWidget(Button.builder(Component.empty(),b->action.run()).bounds(left+16,top+y,w-32,20).build());}
    private static float next(float current){for(float v:new float[]{0,.15f,.35f,.6f,.85f,1})if(v>current+.0001f)return v;return 0;}
    private void change(boolean enabled,CabinPreferences022.Page page,float day,float night,int accent){draft=new CabinPreferences022.Settings(draft.revision(),enabled,page,day,night,accent,draft.lighting());dirty=true;update();}
    private void update(){
        var state=CabinSettingsClient022.view(car).orElse(null);
        if(state!=null&&state.sequence()!=reply){reply=state.sequence();if(!dirty||state.result()==CabinPayloads022.Result.CHANGED||state.result()==CabinPayloads022.Result.STALE)state.preferences().ifPresent(s->{draft=s;dirty=false;});}
        var theme=CabinTuningClient027.view(car).orElse(null);
        if(theme!=null&&theme.sequence()!=themeReply){themeReply=theme.sequence();if(!themeDirty||theme.result()==CabinTuningPayloads027.Result.CHANGED||theme.result()==CabinTuningPayloads027.Result.STALE)theme.preferences().ifPresent(s->{acceptedTheme=s;themeDraft=s.accentRgb();themeDirty=false;});}
        if(enabled==null)return;
        boolean writable=state!=null&&state.mayConfigure()&&state.preferences().isPresent()&&!CabinSettingsClient022.pending(car)&&draft.revision()<Long.MAX_VALUE;
        boolean themeWritable=theme!=null&&theme.mayConfigure()&&theme.preferences().isPresent()&&!CabinTuningClient027.pending(car)&&theme.preferences().get().revision()<Long.MAX_VALUE;
        enabled.active=page.active=day.active=night.active=lighting.active=writable;color.active=themeWritable;apply.active=writable&&dirty||themeWritable&&themeDirty;
        enabled.setMessage(text("enabled","Cabin display").copy().append(": ").append(Component.translatable(draft.enabled()?"options.on":"options.off")));
        page.setMessage(text("page","Page").copy().append(": ").append(text("page."+draft.page().name().toLowerCase(Locale.ROOT),switch(draft.page()){case COMBINED->"Infotainment";case RADIO->"Radio & Music";case POWER->"Driving";})));
        day.setMessage(text("day","Day brightness").copy().append(String.format(Locale.ROOT,": %.0f%%",draft.dayBrightness()*100)));
        night.setMessage(text("night","Night brightness").copy().append(String.format(Locale.ROOT,": %.0f%%",draft.nightBrightness()*100)));
        color.setMessage(text("theme","Theme").copy().append(": "+InfotainmentTheme.name(themeDraft<0?draft.accentRgb():themeDraft)));
        lighting.setMessage(text("lighting","Display lighting").copy().append(": ").append(text("lighting."+draft.lighting().name().toLowerCase(Locale.ROOT),switch(draft.lighting()){case AUTO->"Automatic";case DAY->"Day";case NIGHT->"Night";})));
    }
    @Override public void tick(){if(!VehicleClientControls.canUseMenu(minecraft,car)||minecraft.player==null||minecraft.player.getVehicle()!=car){CabinScreenPlatform022.set(minecraft,null);return;}if(++ticks%20==0){CabinSettingsClient022.query(car);CabinTuningClient027.query(car);}update();}
    @Override public void onClose(){CabinScreenPlatform022.set(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fill(0,0,width,height,0x9910171d);
        if(small)graphics.textWithWordWrap(font,Component.translatable("vehicle.automobility.too_small"),12,12,Math.max(1,width-24),0xffe4edf1);
        else{
            AutomobilityUiTheme.panel(graphics,left,top,w,h);AutomobilityUiTheme.divider(graphics,left+16,top+31,w-32);graphics.text(font,title,left+16,top+12,AutomobilityUiTheme.TEXT,false);
            var state=CabinSettingsClient022.view(car).orElse(null);
            var theme=CabinTuningClient027.view(car).orElse(null);
            Component info=theme!=null&&theme.result()==CabinTuningPayloads027.Result.STALE?text("stale","Settings changed. Review and apply again.")
                    :theme!=null&&theme.result()==CabinTuningPayloads027.Result.DENIED?text("forbidden","Vehicle configuration permission is required.")
                    :!CabinSettingsClient022.capable()?text("unavailable","Server cabin settings unavailable."):state==null?text("waiting","Waiting for the server…")
                    :state.status()==CabinPreferences022.Status.OPAQUE?text("opaque","Unrecognized settings preserved; this display is unavailable.")
                    :!state.mayConfigure()?text("forbidden","Vehicle configuration permission is required.")
                    :state.result()==CabinPayloads022.Result.STALE?text("stale","Settings changed. Review and apply again.")
                    :!dirty&&!themeDirty&&(state.result()==CabinPayloads022.Result.CHANGED||theme!=null&&theme.result()==CabinTuningPayloads027.Result.CHANGED)?text("saved","Cabin settings saved."):text("help","Changes apply to this car after server confirmation.");
            graphics.textWithWordWrap(font,info,left+16,top+36,w-32,AutomobilityUiTheme.MUTED);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private static Component text(String key,String fallback){return Component.translatableWithFallback("vehicle.automobility.cabin."+key,fallback);}
}
