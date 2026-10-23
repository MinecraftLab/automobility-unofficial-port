package io.github.foundationgames.automobility.client.lighting;
import static io.github.foundationgames.automobility.client.lighting.VehicleConeGeometry.*;
/** Scalar emitter preserves the frozen point arithmetic, without temporary vertex vectors/arrays. */
public final class VehicleConeMesh {
 public static final int CAPACITY=2*SECTORS*STEPS*2*4*7;
 public static final class Lease {
  private static long creations;public Lease(){creations++;}public static long creations(){return creations;}
  public final float[] mesh=new float[CAPACITY];private final Thread owner=Thread.currentThread();private boolean held,emitting;private long token;private long acquisitions;
  private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Foreign mesh thread");}
  public long acquire(){thread();if(held)throw new IllegalStateException("Mesh still queued");if(token==Long.MAX_VALUE)throw new IllegalStateException("Mesh token overflow");held=true;acquisitions++;return ++token;}
  public void verify(long expected){thread();if(!held||token!=expected)throw new IllegalStateException("Stale/duplicate mesh callback");}
  public void enterEmission(long expected){verify(expected);if(emitting)throw new IllegalStateException("Reentrant mesh callback");emitting=true;}
  public void release(long expected){verify(expected);emitting=false;held=false;}
  public boolean held(){return held;}public long acquisitions(){return acquisitions;}
 }
 public static int append(Volume v,double rx,double ry,double rz,float[] out,int at){
  for(int s=0;s<SECTORS;s++)for(int shell=0;shell<2;shell++){
   double end=v.cut[s];if(end<=EPS)continue;double radial=shell==0?.5:1;V e=v.edge[s],next=v.edge[(s+1)%SECTORS];
   double dx=next.x()-e.x(),dy=next.y()-e.y(),dz=next.z()-e.z();
   double nx=dy*v.forward.z()-dz*v.forward.y(),ny=dz*v.forward.x()-dx*v.forward.z(),nz=dx*v.forward.y()-dy*v.forward.x();
   double length=Math.sqrt(nx*nx+ny*ny+nz*nz),inv=1/length;nx*=inv;ny*=inv;nz*=inv;
   for(int step=0;step<STEPS;step++){
    double a=end*step/STEPS,b=end*(step+1)/STEPS;
    for(int k=0;k<4;k++){
     V edge=k==0||k==3?e:next;double distance=k<2?a:b,axis=distance*(1-radial),side=distance*radial;
     if(at+7>out.length)throw new IllegalStateException("Mesh capacity");
     out[at++]=(float)(((v.origin.x()+v.forward.x()*axis)+edge.x()*side)-rx);
     out[at++]=(float)(((v.origin.y()+v.forward.y()*axis)+edge.y()*side)-ry);
     out[at++]=(float)(((v.origin.z()+v.forward.z()*axis)+edge.z()*side)-rz);
     out[at++]=(float)v.opacity(distance,shell);out[at++]=(float)nx;out[at++]=(float)ny;out[at++]=(float)nz;
    }
   }
  }return at;
 }
 private VehicleConeMesh(){}
}
