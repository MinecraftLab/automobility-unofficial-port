package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.Automobility;
import java.io.IOException;
import java.nio.file.Path;

/** One published client layout; drafts never change the live renderer until Save succeeds. */
public final class VehicleUiState {
    private static VehicleUiSettings current=VehicleUiSettings.migrate(VehicleHudPreferences.defaults());
    private static VehicleUiStore store;
    private static boolean recoveryReady;
    public static boolean recoveryReady() {return recoveryReady;}
    public static void preserveBeforeBootstrap(Path config) {
        recoveryReady=false;
        try {
            var oldStore=new VehicleHudPreferenceStore(config.resolve("automobility-vehicle-ui.json"));
            var old=oldStore.load();
            new VehicleUiStore(config.resolve("automobility-ui-v2.json"),config.resolve("automobility-vehicle-ui.json"),config.resolve("momentum.json")).load(old,true);
            recoveryReady=true;
        } catch(IOException error) {Automobility.LOG.warn("Legacy UI recovery could not complete; bootstrap config rewrite will be skipped",error);}
    }
    private VehicleUiState() {}
    public static void initialize(Path config,VehicleHudPreferences old,boolean oldReadable) {
        current=VehicleUiSettings.migrate(old);
        store=new VehicleUiStore(config.resolve("automobility-ui-v2.json"),config.resolve("automobility-vehicle-ui.json"),config.resolve("momentum.json"));
        try { current=store.load(old,oldReadable); }
        catch(IOException error) {Automobility.LOG.warn("Could not read UI layout; preserving existing files",error);}
    }
    public static String legacyInput(String name) {
        try {return store==null?null:store.legacyInput(name);}
        catch(IOException error) {Automobility.LOG.warn("Could not read preserved legacy HUD snapshot",error);return null;}
    }
    public static VehicleUiSettings current() { return current; }
    public static boolean unified() { return !current.legacyLayout(); }
    public static boolean writable() { return store!=null && store.readable(); }
    public static boolean save(VehicleUiSettings draft) {
        if(store==null || draft==null) return false;
        try {var candidate=draft.sanitized();store.save(candidate);current=candidate;return true;}
        catch(IOException error) {Automobility.LOG.warn("Could not save UI layout; draft not applied",error);return false;}
    }
}
