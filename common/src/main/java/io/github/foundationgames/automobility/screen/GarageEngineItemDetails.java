package io.github.foundationgames.automobility.screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Read-only supported battery label. Never used for admission, transfer policy or resource mutation. */
public final class GarageEngineItemDetails {
    private GarageEngineItemDetails(){}
    public static List<Component> lines(ItemStack stack){
        var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return List.of();var raw=data.copyTag();
        if(!raw.contains("powerworks:traction_battery"))return List.of();
        var battery=raw.getCompound("powerworks:traction_battery").orElse(null);
        if(battery==null||!(battery.get("version") instanceof IntTag)||battery.getIntOr("version",0)!=1||!(battery.get("stored") instanceof LongTag)||!(battery.get("capacity") instanceof LongTag)||!(battery.get("id") instanceof StringTag))return unknown();
        long stored=battery.getLongOr("stored",-1),capacity=battery.getLongOr("capacity",0);var text=battery.getStringOr("id","");
        try{if(capacity<=0||stored<0||stored>capacity||!UUID.fromString(text).toString().equals(text))return unknown();}catch(IllegalArgumentException error){return unknown();}
        int percent=(int)Math.floor(100.0*stored/capacity);
        return List.of(Component.translatable("garage.automobility.engine_battery",percent+" %",stored,capacity),Component.translatable("garage.automobility.engine_battery_id",text.substring(text.length()-8)));
    }
    private static List<Component> unknown(){return List.of(Component.translatable("garage.automobility.engine_battery_unknown"));}
}
