package io.github.foundationgames.automobility.fabric.mixin;

import io.github.foundationgames.automobility.client.vehicle.VehicleSettingsDrafts;
import io.github.foundationgames.automobility.screen.AutomobilityOptionsScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Participate in vanilla layout/focus/resize rather than floating over other mods' controls. */
@Mixin(OptionsScreen.class)
public abstract class AutomobilityOptionsEntryMixin extends Screen {
    protected AutomobilityOptionsEntryMixin(Component title) { super(title); }
    @ModifyArg(method="init()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),index=0,require=1,allow=1)
    private LayoutElement automobility$settingsFooter(LayoutElement original) {
        int buttonWidth=VehicleSettingsDrafts.footerButtonWidth(width);
        if(original instanceof AbstractWidget widget) widget.setWidth(buttonWidth);
        var row=LinearLayout.horizontal().spacing(8);
        row.addChild(original);
        row.addChild(Button.builder(Component.translatable("ui.automobility.short_title"),ignored ->
                minecraft.gui.setScreen(new AutomobilityOptionsScreen(this))).width(buttonWidth).build());
        return row;
    }
}
