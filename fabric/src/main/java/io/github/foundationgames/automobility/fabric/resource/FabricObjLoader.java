package io.github.foundationgames.automobility.fabric.resource;

import io.github.foundationgames.automobility.automobile.render.obj.ObjLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricObjLoader extends ObjLoader implements IdentifiableResourceReloadListener {
    public static final FabricObjLoader INSTANCE = new FabricObjLoader();

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    static {
        ObjLoader.INSTANCE = INSTANCE;
    }
}
