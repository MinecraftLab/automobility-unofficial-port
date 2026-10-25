package io.github.foundationgames.automobility.client.vehicle;

/** One narrow reservation; the radio occupies the final indicator row, never a second panel. */
public final class DashboardClusterLayout {
    public static final int INSTRUMENT_WIDTH=72,RADIO_WIDTH=19,RADIO_HEIGHT=12,GAP=0;
    public static final int WIDTH=INSTRUMENT_WIDTH,HEADER_HEIGHT=36,MECHANICS_HEIGHT=16,STATUS_HEIGHT=14;
    private DashboardClusterLayout() { }
    public static int energyY(boolean mechanics){return HEADER_HEIGHT+(mechanics?MECHANICS_HEIGHT:0);}
    public static int statusY(boolean energy,boolean mechanics){return energyY(mechanics)+(energy?VehicleEnergyPresentation.ENERGY_HEIGHT:0);}
    public static int instrumentHeight(boolean energy,boolean mechanics){return statusY(energy,mechanics)+STATUS_HEIGHT;}
    public static VehicleHudPlacement.Box radio(VehicleHudPlacement.Box panel,float scale){
        int w=Math.min(panel.width(),Math.max(0,(int)Math.ceil(RADIO_WIDTH*scale)));
        int h=Math.min(panel.height(),Math.max(0,(int)Math.ceil(RADIO_HEIGHT*scale)));
        return new VehicleHudPlacement.Box(panel.x()+panel.width()-w,panel.y()+panel.height()-h,w,h);
    }
}
