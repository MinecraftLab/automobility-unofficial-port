package io.github.foundationgames.automobility.client.vehicle;

/** Versioned personal presentation only. No gameplay or foreign HUD state is stored here. */
public record VehicleUiSettings(int version, boolean legacyLayout, VehicleHudPreferences basic,
        VehicleHudPreferences previous, Placement panel, Placement hints, HintMode hintMode, Region reservation, boolean energyEnabled) {
    public static final int VERSION=3;
    public VehicleUiSettings(int version, boolean legacyLayout, VehicleHudPreferences basic,
            VehicleHudPreferences previous, Placement panel, Placement hints, HintMode hintMode, Region reservation) {
        this(version, legacyLayout, basic, previous, panel, hints, hintMode, reservation, true);
    }
    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public enum HintMode { AUTO, ALWAYS, OFF }
    public record Placement(Anchor anchor,int x,int y) {
        public Placement sanitized() { return new Placement(anchor==null?Anchor.BOTTOM_LEFT:anchor,Math.clamp(x,0,10000),Math.clamp(y,0,10000)); }
    }
    /** Thousandths of the GUI viewport; independent of window pixels and GUI scale. */
    public record Region(boolean enabled,int x,int y,int width,int height) {
        public Region sanitized() {
            int w=Math.clamp(width,50,1000),h=Math.clamp(height,50,1000);
            return new Region(enabled,Math.clamp(x,0,1000-w),Math.clamp(y,0,1000-h),w,h);
        }
    }
    public static VehicleUiSettings migrate(VehicleHudPreferences old) {
        var legacy=old.sanitized();
        var basic=new VehicleHudPreferences(legacy.enabled(),legacy.scalePercent(),VehicleHudPreferences.Position.BOTTOM_LEFT,legacy.cameraDistanceBlocks());
        return new VehicleUiSettings(VERSION,false,basic,legacy,new Placement(Anchor.BOTTOM_LEFT,8,58),
                new Placement(Anchor.BOTTOM_LEFT,8,105),HintMode.AUTO,new Region(false,750,20,230,300),true);
    }
    public VehicleUiSettings sanitized() {
        return new VehicleUiSettings(VERSION,legacyLayout,basic.sanitized(),previous.sanitized(),panel.sanitized(),hints.sanitized(),hintMode,reservation.sanitized(),energyEnabled);
    }
    public VehicleUiSettings withBasic(VehicleHudPreferences value) { return new VehicleUiSettings(version,legacyLayout,value,previous,panel,hints,hintMode,reservation,energyEnabled).sanitized(); }
    public VehicleUiSettings withPanel(Placement value) { return new VehicleUiSettings(version,false,basic,previous,value,hints,hintMode,reservation,energyEnabled).sanitized(); }
    public VehicleUiSettings withHints(Placement value) { return new VehicleUiSettings(version,false,basic,previous,panel,value,hintMode,reservation,energyEnabled).sanitized(); }
    public VehicleUiSettings withHintMode(HintMode value) { return new VehicleUiSettings(version,legacyLayout,basic,previous,panel,hints,value,reservation,energyEnabled).sanitized(); }
    public VehicleUiSettings withReservation(Region value) { return new VehicleUiSettings(version,legacyLayout,basic,previous,panel,hints,hintMode,value,energyEnabled).sanitized(); }
    public VehicleUiSettings restorePrevious() { return new VehicleUiSettings(version,true,previous.withCameraDistance(basic.cameraDistanceBlocks()),previous,panel,hints,hintMode,reservation,energyEnabled).sanitized(); }
    public VehicleUiSettings withEnergyEnabled(boolean value) { return new VehicleUiSettings(version,legacyLayout,basic,previous,panel,hints,hintMode,reservation,value).sanitized(); }
    public VehicleUiSettings resetLayout() {
        var defaults=migrate(previous);
        return new VehicleUiSettings(version,false,basic,previous,defaults.panel,defaults.hints,HintMode.AUTO,defaults.reservation,energyEnabled).sanitized();
    }
}
