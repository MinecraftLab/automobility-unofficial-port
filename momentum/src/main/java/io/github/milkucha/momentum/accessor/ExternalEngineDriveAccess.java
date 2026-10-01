package io.github.milkucha.momentum.accessor;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.milkucha.momentum.api.ExternalEngineDriveApi.DriveState;
import net.minecraft.core.Holder;

/** Internal mixin access used by the versioned public drive API. */
public interface ExternalEngineDriveAccess {
    Holder<AutomobileEngine> momentum$driveEngine();
    DriveState momentum$externalDriveState();
}
