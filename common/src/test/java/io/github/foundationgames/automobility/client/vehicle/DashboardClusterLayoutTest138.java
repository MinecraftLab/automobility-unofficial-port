package io.github.foundationgames.automobility.client.vehicle;

/** Geometry constraints for one movable/scalable instrument+radio reservation, not native visual QA. */
public final class DashboardClusterLayoutTest138 {
    private static int checks;
    public static void main(String[] args){
        for(int[] viewport:new int[][]{{320,180},{427,240},{640,360},{1280,720}})
            for(int percent:new int[]{75,100,150})for(int guiScale:new int[]{1,2,3,4}){
                float scale=VehicleEnergyPresentation.panelScale(viewport[0],viewport[1],DashboardClusterLayout.WIDTH,58,percent,guiScale);
                var panel=VehicleHudPlacement.anchored(viewport[0],viewport[1],(int)Math.ceil(DashboardClusterLayout.WIDTH*scale),(int)Math.ceil(58*scale),new VehicleUiSettings.Placement(VehicleUiSettings.Anchor.BOTTOM_RIGHT,8,58));
                var radio=DashboardClusterLayout.radio(panel,scale);
                check(radio.x()>=panel.x()&&radio.y()>=panel.y()&&radio.x()+radio.width()<=panel.x()+panel.width()&&radio.y()+radio.height()<=panel.y()+panel.height(),"radio escaped the cluster drag reservation");
                check(radio.x()>=panel.x()+DashboardClusterLayout.INSTRUMENT_WIDTH*scale,"radio covered the instruments");
                check(Math.abs(radio.width()-DashboardClusterLayout.RADIO_WIDTH*scale)<=1,"radio ignored the cluster scale");
                var moved=new VehicleHudPlacement.Box(panel.x()+7,panel.y()-5,panel.width(),panel.height());
                var movedRadio=DashboardClusterLayout.radio(moved,scale);
                check(movedRadio.x()-radio.x()==7&&movedRadio.y()-radio.y()==-5,"radio moved independently of its instruments");
            }
        System.out.println("PASS DashboardClusterLayoutTest138 checks="+checks);
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
