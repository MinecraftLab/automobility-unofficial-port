package io.github.foundationgames.automobility.client.vehicle.energy;

import java.security.MessageDigest;
import java.util.HexFormat;

/** Target class pins await exact producer handoffs; empty expected digests fail closed.
 * Startup-only, pre-transformation resource checks. No producer classes are loaded here. */
public final class EnergyCompatibilityGate {
    private EnergyCompatibilityGate() {}
    private static boolean matches(String resource, String expected) {
        try (var input = EnergyCompatibilityGate.class.getClassLoader().getResourceAsStream(resource)) {
            return input != null && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.readAllBytes())).equalsIgnoreCase(expected);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | LinkageError failure) {
            return false;
        }
    }
    private static final class Qualified {
        static final boolean PUMPS = matches("dev/rosenvik/pumpsandpistons/client/PumpsAndPistonsAutomobilityClient.class", "9075B64F880B27FA6BA83A2EFC0B4F88E344EB205EE41EDF2A6262742B83299D")
                && matches("dev/rosenvik/pumpsandpistons/FuelledAutomobile.class", "5D34DFDEB1B164491F05291B8A0C89B9EF26CE5B2BBB79ABD8E6C8FB6AD2DE6B")
                && matches("dev/rosenvik/pumpsandpistons/compat/automobility/api/AutomobilityIntegrationAccess.class", "F9DC1ACFB99FFC308B7418C5C48036AFB01260E9C68F50A672F7BBE99A6E0BC6");
        static final boolean POWERWORKS = matches("com/offchunk/powerworks/compat/automobility/VehicleBatteryAccess.class", "0C354C91A2CD89CA1ED52B8F659A8C79B74F16B1ED200617B3DEE407E53C9CD4")
                && matches("com/offchunk/powerworks/compat/automobility/AutomobilityBridge.class", "7303822937BC268F3152A5A2472AAE85FCE0B52A225534C03B77FD16DFC1914A");
    }
    public static boolean pumps() { return Qualified.PUMPS; }
    public static boolean powerworks() { return Qualified.POWERWORKS; }
}
