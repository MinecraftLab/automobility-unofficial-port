package io.github.foundationgames.automobility.fabric.resource;

import io.github.foundationgames.automobility.automobile.render.JsonEntityModelLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class FabricJsonEntityModelLoader implements ResourceManagerReloadListener, IdentifiableResourceReloadListener {
    public static final FabricJsonEntityModelLoader INSTANCE = new FabricJsonEntityModelLoader();

    @Override
    public Identifier getFabricId() {
        return JsonEntityModelLoader.ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        JsonEntityModelLoader.reload(resourceManager);
    }
}
