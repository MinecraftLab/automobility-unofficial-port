package io.github.foundationgames.automobility.client.vehicle;

/** One stable reservation includes the small radio even while its source is idle. */
public final class DashboardClusterLayout {
    public static final int INSTRUMENT_WIDTH=VehicleEnergyPresentation.PANEL_WIDTH,RADIO_WIDTH=36,RADIO_HEIGHT=18,GAP=4;
    public static final int WIDTH=INSTRUMENT_WIDTH+GAP+RADIO_WIDTH;
    private DashboardClusterLayout() { }
    public static VehicleHudPlacement.Box radio(VehicleHudPlacement.Box panel,float scale){
        int x=panel.x()+(int)Math.floor((INSTRUMENT_WIDTH+GAP)*scale);
        return new VehicleHudPlacement.Box(x,panel.y(),Math.min((int)Math.ceil(RADIO_WIDTH*scale),Math.max(0,panel.x()+panel.width()-x)),(int)Math.ceil(RADIO_HEIGHT*scale));
    }
}
