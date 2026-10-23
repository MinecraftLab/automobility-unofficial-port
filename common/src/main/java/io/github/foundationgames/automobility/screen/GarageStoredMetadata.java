package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.*;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.entity.GarageService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Read-only stored-menu metadata. No entity materialization or addon initialization is involved. */
public record GarageStoredMetadata(Identifier frame,Identifier wheels,Identifier engine,int drivingMode) {
    public static GarageStoredMetadata read(CompoundTag raw) {
        if(raw==null) return null;
        var frame=Identifier.tryParse(raw.getStringOr("frame",""));
        var wheels=Identifier.tryParse(raw.getStringOr("wheels",""));
        var engine=Identifier.tryParse(raw.getStringOr("engine",""));
        if(frame==null||wheels==null||engine==null) return null;
        return new GarageStoredMetadata(frame,wheels,engine,VehicleDrivingMode.fromId(raw.getIntOr("vehicleDrivingMode",0)).id());
    }
    public GarageService.Components resolve(RegistryAccess registries) {
        var f=registries.lookupOrThrow(AutomobileFrame.REGISTRY).get(ResourceKey.create(AutomobileFrame.REGISTRY,frame));
        var w=registries.lookupOrThrow(AutomobileWheel.REGISTRY).get(ResourceKey.create(AutomobileWheel.REGISTRY,wheels));
        var e=registries.lookupOrThrow(AutomobileEngine.REGISTRY).get(ResourceKey.create(AutomobileEngine.REGISTRY,engine));
        if(f.isEmpty()||w.isEmpty()||e.isEmpty()) return null;
        var parts=new GarageService.Components(f.get(),w.get(),e.get());return parts.complete()?parts:null;
    }
}
