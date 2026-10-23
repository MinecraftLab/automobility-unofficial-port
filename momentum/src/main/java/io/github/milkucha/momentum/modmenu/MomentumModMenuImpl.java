package io.github.milkucha.momentum.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.foundationgames.automobility.screen.AutomobilityOptionsScreen;

public class MomentumModMenuImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutomobilityOptionsScreen::new;
    }
}
