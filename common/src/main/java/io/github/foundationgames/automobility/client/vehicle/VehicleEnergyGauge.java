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
        graphics.fill(0,y,width,y+10,0xEA171A18);
        graphics.fill(0,y,width,y+1,0xFF596E7B);
        if (value.kind() == VehicleEnergyReading.Kind.BATTERY) {
            graphics.outline(4,y+3,9,6,color);graphics.fill(13,y+5,15,y+7,color);
        } else {
            graphics.fill(5,y+3,11,y+9,color);graphics.fill(6,y+4,10,y+6,0xFF202020);
            graphics.fill(4,y+9,12,y+10,color);graphics.fill(11,y+4,14,y+5,color);graphics.fill(13,y+5,14,y+9,color);
        }
        var amount = value.known() ? Component.translatable("ui.automobility.energy.percent",value.percent()) : Component.literal("—");
        int amountWidth = Math.max(font.width(Component.translatable("ui.automobility.energy.percent",100)),font.width(amount));
        boolean compact=width<100;
        int start=compact?18:22,gap=compact?4:7;
        int errorRoom=compact&&value.state()==VehicleEnergyReading.State.WRONG_FUEL?8:0;
        int barWidth=Math.min(76,Math.max(0,width-amountWidth-start-(compact?8:12)-errorRoom)),right=start+barWidth+gap;
        int stride=compact?3:4;
        int count=barWidth/stride,filled=value.filledSegments(count);
        for (int i=0;i<count;i++) {
            int x=start+i*stride;
            graphics.fill(x,y+3,x+stride-1,y+7,i<filled?color:0xFF40534F);
        }
        graphics.text(font,amount,right,y+2,color,false);
        if (value.state()==VehicleEnergyReading.State.WRONG_FUEL) graphics.text(font,Component.literal("!"),width-(compact?8:5),y+2,color,false);
    }
}
