package io.github.foundationgames.automobility.api.dashboard;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.GarageService;
/** Read-only invocation of the existing actual garage service admission; never a transaction or inventory action. */
public interface GarageCabinTuningAccess027 {
    GarageService.Status cabinTuning$accessReason(AutomobileEntity car);
}
