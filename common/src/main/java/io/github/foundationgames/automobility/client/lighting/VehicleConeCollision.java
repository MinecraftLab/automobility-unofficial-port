package io.github.foundationgames.automobility.client.lighting;
import java.util.Arrays;
import net.minecraft.world.phys.shapes.Shapes;
import static io.github.foundationgames.automobility.client.lighting.VehicleConeGeometry.*;
/** Synchronous visitor; preserves AABB constructor coordinate normalization without making an AABB/List. */
final class VehicleConeCollision implements Shapes.DoubleLineConsumer {
 Volume volume;int x,y,z;long boxes;int unknown;private boolean busy;private final Thread owner=Thread.currentThread();
 private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Foreign shape callback thread");}
 void begin(Volume next,int x,int y,int z){thread();if(busy)throw new IllegalStateException("Reentrant shape enumeration");busy=true;volume=next;this.x=x;this.y=y;this.z=z;boxes=0;unknown=0;}
 void end(){thread();if(!busy)throw new IllegalStateException("Shape visitor not held");busy=false;volume=null;}
 public void consume(double a,double b,double c,double d,double e,double f){
  thread();if(!busy)throw new IllegalStateException("Unowned shape callback");double x0=Math.min(a,d),y0=Math.min(b,e),z0=Math.min(c,f),x1=Math.max(a,d),y1=Math.max(b,e),z1=Math.max(c,f);
  boxes++;if(x0< -1||y0< -1||z0< -1||x1>2||y1>2||z1>2){Arrays.fill(volume.cut,0);unknown++;}
  volume.obstacle(x+x0,y+y0,z+z0,x+x1,y+y1,z+z1);
 }
}
