package io.github.foundationgames.automobility.client.lighting;
import java.util.Arrays;
/** Pure conservative volume clipping. A false intersection shortens a sector; no sampled-ray clear verdict. */
public final class VehicleConeGeometry {
 public static final int SECTORS=16,STEPS=8;
 public static final double MAX_RANGE=12,TANGENT_SCALE=.35,EPS=.002;
 public record V(double x,double y,double z){
  public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
  public V mul(double s){return new V(x*s,y*s,z*s);}public double dot(V b){return x*b.x+y*b.y+z*b.z;}
  public V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
 }
 public record Box(double x0,double y0,double z0,double x1,double y1,double z1){
  public Box {if(!Double.isFinite(x0+y0+z0+x1+y1+z1)||x0>x1||y0>y1||z0>z1)throw new IllegalArgumentException("Finiteorderedbox");}
  public V center(){return new V((x0+x1)/2,(y0+y1)/2,(z0+z1)/2);}public V extent(){return new V((x1-x0)/2,(y1-y0)/2,(z1-z0)/2);}
 }
 public static final class Volume {
  public final V origin,forward,axial;public final V[] edge=new V[SECTORS];public final double[] cut=new double[SECTORS];
  public final double range;public final Box bounds;private final V[][] planes=new V[SECTORS][3];private final Box[] sectorBounds=new Box[SECTORS];
  public Volume(V origin,V f,V r,V u,double sourceRange,double ht,double vt){
   if(!origin.finite()||!f.finite()||!r.finite()||!u.finite()||!Double.isFinite(sourceRange+ht+vt)||sourceRange<=0||ht<=0||vt<=0)throw new IllegalArgumentException("Finitebeam");
   V ru=r.cross(u);double det=f.dot(ru);if(Math.abs(det)<.9||Math.abs(det)>1.1)throw new IllegalArgumentException("Beam basis");
   this.origin=origin;forward=f;axial=ru.mul(1/det);range=Math.min(MAX_RANGE,sourceRange);Arrays.fill(cut,range);
   double x0=origin.x,y0=origin.y,z0=origin.z,x1=x0,y1=y0,z1=z0;
   for(int i=0;i<SECTORS;i++){double a=2*Math.PI*i/SECTORS;edge[i]=f.add(r.mul(ht*TANGENT_SCALE*Math.cos(a))).add(u.mul(vt*TANGENT_SCALE*Math.sin(a)));V p=origin.add(edge[i].mul(range));x0=Math.min(x0,p.x);x1=Math.max(x1,p.x);y0=Math.min(y0,p.y);y1=Math.max(y1,p.y);z0=Math.min(z0,p.z);z1=Math.max(z1,p.z);}
   bounds=new Box(x0-EPS,y0-EPS,z0-EPS,x1+EPS,y1+EPS,z1+EPS);
   for(int i=0;i<SECTORS;i++){V[] rays={f,edge[i],edge[(i+1)%SECTORS]};double ax=origin.x,ay=origin.y,az=origin.z,bx=ax,by=ay,bz=az;
    for(V ray:rays){V p=origin.add(ray.mul(range));ax=Math.min(ax,p.x);bx=Math.max(bx,p.x);ay=Math.min(ay,p.y);by=Math.max(by,p.y);az=Math.min(az,p.z);bz=Math.max(bz,p.z);}sectorBounds[i]=new Box(ax-EPS,ay-EPS,az-EPS,bx+EPS,by+EPS,bz+EPS);
    for(int j=0;j<3;j++){V n=rays[j].cross(rays[(j+1)%3]);if(n.dot(rays[(j+2)%3])<0)n=n.mul(-1);planes[i][j]=n;}}
  }
  private static double support(V n,V ext){return Math.abs(n.x)*ext.x+Math.abs(n.y)*ext.y+Math.abs(n.z)*ext.z;}
  public void obstacle(Box b){obstacle(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1);}
  public void obstacle(double x0,double y0,double z0,double x1,double y1,double z1){
   if(!Double.isFinite(x0+y0+z0+x1+y1+z1)||x0>x1||y0>y1||z0>z1)throw new IllegalArgumentException("Finiteorderedbox");
   double cx=(x0+x1)/2-origin.x,cy=(y0+y1)/2-origin.y,cz=(z0+z1)/2-origin.z;
   double ex=(x1-x0)/2+EPS,ey=(y1-y0)/2+EPS,ez=(z1-z0)/2+EPS;
   double center=axial.x*cx+axial.y*cy+axial.z*cz,support=Math.abs(axial.x)*ex+Math.abs(axial.y)*ey+Math.abs(axial.z)*ez;
   double near=center-support,far=center+support;if(far<0||near>range)return;
   for(int i=0;i<SECTORS;i++){Box sb=sectorBounds[i];if(near>=cut[i]+EPS||x1<sb.x0||x0>sb.x1||y1<sb.y0||y0>sb.y1||z1<sb.z0||z0>sb.z1)continue;
    boolean possible=true;for(V n:planes[i])if(n.x*cx+n.y*cy+n.z*cz+(Math.abs(n.x)*ex+Math.abs(n.y)*ey+Math.abs(n.z)*ez)<0){possible=false;break;}
    if(possible)cut[i]=Math.min(cut[i],Math.max(0,near-EPS));
   }
  }
  public void unknown(Box b){obstacle(b);}
  public V point(int sector,double radial,double distance){return origin.add(forward.mul(distance*(1-radial))).add(edge[Math.floorMod(sector,SECTORS)].mul(distance*radial));}
  public double opacity(double distance,int shell){double t=Math.max(0,Math.min(1,distance/range));double fade=(1-t)*(1-t);double near=Math.min(1,distance/.3);return (shell==0?.120:.054)*fade*near;}
 }
 private VehicleConeGeometry(){}
}
