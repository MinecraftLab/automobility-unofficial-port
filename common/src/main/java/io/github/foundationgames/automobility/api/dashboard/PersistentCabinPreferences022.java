package io.github.foundationgames.automobility.api.dashboard;

import net.minecraft.nbt.*;

/** Separate whole-car cosmetic tag; never an engine fragment, inventory or energy owner. */
public interface PersistentCabinPreferences022 {
    String KEY="automobilityCabinCosmeticsV1";
    Tag cabin$raw();
    java.util.UUID cabin$incarnation();
    void cabin$replace(CabinPreferences022.Settings settings,CabinSettingsServer022.Permit permit);
    default CabinPreferences022.Read cabin$read(){var raw=cabin$raw();return CabinPreferences022.read(raw==null?new byte[0]:raw instanceof ByteArrayTag bytes?bytes.getAsByteArray():new byte[]{0});}
}
