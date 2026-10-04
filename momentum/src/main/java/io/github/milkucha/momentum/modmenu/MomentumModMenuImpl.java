package io.github.milkucha.momentum.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.milkucha.momentum.config.MomentumConfigScreen;
import net.fabricmc.loader.api.FabricLoader;

public class MomentumModMenuImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return null;
        }
        return MomentumConfigScreen::create;
    }
}
