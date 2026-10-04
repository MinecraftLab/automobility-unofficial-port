package io.github.foundationgames.automobility.fabric.resource;

import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricAutomobileModels extends AutomobileModels implements IdentifiableResourceReloadListener {
    public static final FabricAutomobileModels INSTANCE = new FabricAutomobileModels();

    @Override
    public Identifier getFabricId() {
        return RELOADER_ID;
    }
}
