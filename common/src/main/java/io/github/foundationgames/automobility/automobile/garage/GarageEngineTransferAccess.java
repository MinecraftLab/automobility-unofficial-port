package io.github.foundationgames.automobility.automobile.garage;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.resources.Identifier;
import java.util.Objects;
/** Read-only owner hook; no UUID-only, ordinary quiet-load or global bypass authorizes a component change. */
public final class GarageEngineTransferAccess {
    public interface Authority {
        /** True only inside the Core engine component stage for the exact object, server/world,
         * UUID, marker/generation, tx, frozen digest and this exact before/after target engine.
         * A provider must also hold its own matching API2 lease. It suppresses ordinary energy
         * initialization/publication only for this call; API1 quietLoading is not reinterpreted.
         */
        boolean permitsComponentChange(AutomobileEntity car,Identifier targetEngine);
    }
    private static Authority authority;
    private GarageEngineTransferAccess(){}
    public static synchronized void install(Authority value){if(authority!=null)throw new IllegalStateException("Core engine authority already installed");authority=Objects.requireNonNull(value);}
    public static boolean permitsComponentChange(AutomobileEntity car,Identifier targetEngine){var current=authority;return current!=null&&car!=null&&targetEngine!=null&&current.permitsComponentChange(car,targetEngine);}
}
