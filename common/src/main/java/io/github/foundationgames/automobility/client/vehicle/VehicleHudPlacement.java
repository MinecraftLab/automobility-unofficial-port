package io.github.foundationgames.automobility.client.vehicle;

/** Pure bounded layout. Reservations are read-only; only our own rectangles are moved. */
public final class VehicleHudPlacement {
    private VehicleHudPlacement() {}
    public record Box(int x,int y,int width,int height) {
        public boolean contains(double px,double py) {return px>=x && px<x+width && py>=y && py<y+height;}
        public boolean overlaps(Box b) {return width>0 && height>0 && b.width>0 && b.height>0 && x<b.x+b.width && x+width>b.x && y<b.y+b.height && y+height>b.y;}
    }
    public static Box anchored(int sw,int sh,int w,int h,VehicleUiSettings.Placement p) {
        w=Math.min(Math.max(1,w),Math.max(1,sw));h=Math.min(Math.max(1,h),Math.max(1,sh));
        boolean right=p.anchor()==VehicleUiSettings.Anchor.TOP_RIGHT || p.anchor()==VehicleUiSettings.Anchor.BOTTOM_RIGHT;
        boolean bottom=p.anchor()==VehicleUiSettings.Anchor.BOTTOM_LEFT || p.anchor()==VehicleUiSettings.Anchor.BOTTOM_RIGHT;
        return new Box(Math.clamp(right?sw-w-p.x():p.x(),0,Math.max(0,sw-w)),Math.clamp(bottom?sh-h-p.y():p.y(),0,Math.max(0,sh-h)),w,h);
    }
    public static Box reservation(int sw,int sh,VehicleUiSettings.Region r) {
        return !r.enabled()?new Box(0,0,0,0):new Box(sw*r.x()/1000,sh*r.y()/1000,Math.max(1,sw*r.width()/1000),Math.max(1,sh*r.height()/1000));
    }
    public static Box avoid(int sw,int sh,Box desired,Box reservation,Box other) {return avoidMany(sw,sh,desired,reservation,other);}
    public static Box avoidMany(int sw,int sh,Box desired,Box... obstacles) {
        int bw=Math.min(Math.max(1,desired.width()),Math.max(1,sw)),bh=Math.min(Math.max(1,desired.height()),Math.max(1,sh));
        desired=new Box(Math.clamp(desired.x(),0,Math.max(0,sw-bw)),Math.clamp(desired.y(),0,Math.max(0,sh-bh)),bw,bh);
        boolean blocked=false;for(var b:obstacles) if(desired.overlaps(b)) blocked=true;
        if(!blocked) return desired;
        int w=desired.width(),h=desired.height();var candidates=new java.util.ArrayList<Box>();
        for(var r:obstacles) {
            candidates.add(new Box(desired.x(),Math.max(0,r.y()-h-4),w,h));candidates.add(new Box(desired.x(),Math.min(sh-h,r.y()+r.height()+4),w,h));
            candidates.add(new Box(Math.max(0,r.x()-w-4),desired.y(),w,h));candidates.add(new Box(Math.min(sw-w,r.x()+r.width()+4),desired.y(),w,h));
        }
        candidates.add(new Box(4,4,w,h));candidates.add(new Box(Math.max(0,sw-w-4),4,w,h));
        candidates.add(new Box(4,Math.max(0,sh-h-58),w,h));candidates.add(new Box(Math.max(0,sw-w-4),Math.max(0,sh-h-58),w,h));
        Box best=null;long distance=Long.MAX_VALUE;
        for(var b:candidates) {
            if(b.x()<0 || b.y()<0 || b.x()+w>sw || b.y()+h>sh) continue;
            boolean overlap=false;for(var o:obstacles) if(b.overlaps(o)) overlap=true;
            if(overlap) continue;
            long d=Math.abs(b.x()-desired.x())+Math.abs(b.y()-desired.y());if(d<distance) {distance=d;best=b;}
        }
        return best==null?desired:best;
    }
    public static VehicleUiSettings.Placement moved(int sw,int sh,Box box,int x,int y) {
        x=Math.clamp(x,0,Math.max(0,sw-box.width()));y=Math.clamp(y,0,Math.max(0,sh-box.height()));
        boolean right=x+box.width()/2>sw/2,bottom=y+box.height()/2>sh/2;
        return new VehicleUiSettings.Placement(bottom?(right?VehicleUiSettings.Anchor.BOTTOM_RIGHT:VehicleUiSettings.Anchor.BOTTOM_LEFT)
                :(right?VehicleUiSettings.Anchor.TOP_RIGHT:VehicleUiSettings.Anchor.TOP_LEFT),right?sw-box.width()-x:x,bottom?sh-box.height()-y:y);
    }
    public static boolean hintsVisible(VehicleUiSettings.HintMode mode,long elapsedTicks,boolean controller) {
        return !controller && mode!=VehicleUiSettings.HintMode.OFF && (mode==VehicleUiSettings.HintMode.ALWAYS || elapsedTicks>=0 && elapsedTicks<160);
    }
}
