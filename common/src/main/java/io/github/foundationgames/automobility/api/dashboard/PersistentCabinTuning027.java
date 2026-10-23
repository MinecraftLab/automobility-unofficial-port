package io.github.foundationgames.automobility.api.dashboard;

import net.minecraft.nbt.*;

/** Separate whole-car cosmetic tag; never an engine fragment, inventory or energy owner. */
public interface PersistentCabinTuning027 {
    String KEY="automobilityCabinTuningV1";
    Tag cabinTuning$raw();
    java.util.UUID cabinTuning$incarnation();
    void cabinTuning$replace(CabinTuning027.Settings settings,CabinTuningServer027.Permit permit);
    default CabinTuning027.Read cabinTuning$read(){var raw=cabinTuning$raw();return CabinTuning027.read(raw==null?new byte[0]:raw instanceof ByteArrayTag bytes?bytes.getAsByteArray():new byte[]{0});}
}
