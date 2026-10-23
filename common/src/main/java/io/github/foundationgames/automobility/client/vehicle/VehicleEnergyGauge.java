package io.github.foundationgames.automobility.client.vehicle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Pixel instrument row. Values are already sampled; drawing never touches a producer or network. */
public final class VehicleEnergyGauge {
    private VehicleEnergyGauge() {}
    public static void draw(GuiGraphicsExtractor graphics, VehicleEnergyReading value, int width) {
        if (!value.present()) return;
        var font = Minecraft.getInstance().font;
        int color = switch (value.state()) {
            case WRONG_FUEL, CRITICAL, EMPTY -> 0xFFFF9090;
            case LOW -> 0xFFFFD095;
            case UNAVAILABLE -> 0xFFA7BAC5;
            default -> value.kind() == VehicleEnergyReading.Kind.BATTERY || value.diesel() ? 0xFF8ADEAE : 0xFFE7C16C;
        };
        int y = VehicleEnergyPresentation.BASE_HEIGHT;
        graphics.fill(0,y,width,y+12,0xEA171A18);
        graphics.fill(0,y,width,y+1,0xFF596E7B);
        graphics.fill(0,y+11,width,y+12,0xFF0A1014);
        if (value.kind() == VehicleEnergyReading.Kind.BATTERY) {
            graphics.outline(4,y+3,9,6,color);graphics.fill(13,y+5,15,y+7,color);
        } else {
            graphics.fill(5,y+3,11,y+9,color);graphics.fill(6,y+4,10,y+6,0xFF202020);
            graphics.fill(4,y+9,12,y+10,color);graphics.fill(11,y+4,14,y+5,color);graphics.fill(13,y+5,14,y+9,color);
        }
        var fuel = Component.translatable("ui.automobility.energy.fuel");
        var battery = Component.translatable("ui.automobility.energy.battery");
        int labelWidth = Math.max(font.width(fuel),font.width(battery));
        graphics.text(font,value.kind() == VehicleEnergyReading.Kind.BATTERY ? battery : fuel,18,y+2,color,false);
        var amount = value.known() ? Component.translatable("ui.automobility.energy.percent",value.percent()) : Component.literal("—");
        int amountWidth = Math.max(font.width(Component.translatable("ui.automobility.energy.percent",100)),font.width(amount));
        int right = width-5-amountWidth, start = 23+labelWidth, barWidth = Math.max(0,right-start-6);
        int count = barWidth/4, filled = value.filledSegments(count);
        for (int i=0;i<count;i++) {
            int x=start+i*4;
            graphics.fill(x,y+4,x+3,y+9,i<filled?color:0xFF40534F);
        }
        graphics.text(font,amount,right,y+2,color,false);
        if (value.state()==VehicleEnergyReading.State.WRONG_FUEL) graphics.text(font,Component.literal("!"),width-5,y+2,color,false);
    }
}
