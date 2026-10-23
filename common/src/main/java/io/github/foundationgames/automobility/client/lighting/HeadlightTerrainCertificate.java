package io.github.foundationgames.automobility.client.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.ref.WeakReference;
import java.util.List;

/** Private opt-in local content/loaded-identity certificate; never a physics revision source. */
public final class HeadlightTerrainCertificate {
    public static final boolean ENABLED=Boolean.getBoolean("automobility.experimental.regionalTerrainCertificate");
    static final int MAX_CHUNKS=64,MAX_WATCHED=1024;
    private HeadlightTerrainCertificate(){}

    public interface Source {
        Tracker automobility$headlightTerrain();
        /** Exact native FULL,false lookup; unknown chunk subclasses return null. */
        Object automobility$headlightChunk(int x,int z);
    }
    static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private static final class Token {
        final WeakReference<Object> chunk;
        long version;
        Token(Object chunk){this.chunk=new WeakReference<>(chunk);}
    }
    public static final class Tracker {
        private final Thread owner=Thread.currentThread();
        private final Long2ObjectOpenHashMap<Token> watched=new Long2ObjectOpenHashMap<>();
        private volatile boolean poisoned;
        private long mutations;
        private boolean usable(){if(Thread.currentThread()!=owner)poisoned=true;return !poisoned;}
        private boolean advance(){if(mutations==Long.MAX_VALUE){poisoned=true;return false;}mutations++;return true;}
        private Token watch(int x,int z,Object chunk){
            if(chunk==null||!usable())return null;long key=key(x,z);Token old=watched.get(key);
            if(old!=null&&old.chunk.get()==chunk)return old;
            if(!advance())return null;
            // Bounded retention. Old snapshots lose token identity and immediately fail closed.
            if(old==null&&watched.size()>=MAX_WATCHED)watched.clear();
            var token=new Token(chunk);watched.put(key,token);return token;
        }
        public void changed(int x,int z){
            if(!usable())return;long key=key(x,z);Token token=watched.get(key);if(token==null)return;
            if(!advance())return;
            if(token.version==Long.MAX_VALUE)watched.remove(key);else token.version++;
        }
        int watchedCount(){return watched.size();}
    }
    /** The square deliberately overbounds fan rays, road probes, conservative cell/cone overlap and neighbors. */
    record Envelope(int minX,int minZ,int maxX,int maxZ){
        boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        boolean containsChunk(long packed){return contains((int)(packed>>32),(int)packed);}
        static Envelope capture(List<HeadlightBeam> beams){
            if(beams.isEmpty())return null;
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var beam:beams){
                double radius=radius(beam);if(!Double.isFinite(radius))return null;
                // Four extra blocks on capture, two on each current-pose validation: no footprint extension on reuse.
                radius+=4;
                minX=Math.min(minX,chunk(beam.origin().x-radius));maxX=Math.max(maxX,chunk(beam.origin().x+radius));
                minZ=Math.min(minZ,chunk(beam.origin().z-radius));maxZ=Math.max(maxZ,chunk(beam.origin().z+radius));
            }
            long count=((long)maxX-minX+1)*((long)maxZ-minZ+1);
            return count>0&&count<=MAX_CHUNKS?new Envelope(minX,minZ,maxX,maxZ):null;
        }
        boolean contains(List<HeadlightBeam> actual){
            if(actual.isEmpty())return false;
            for(var beam:actual){double radius=radius(beam);if(!Double.isFinite(radius))return false;radius+=2;
                if(!contains(chunk(beam.origin().x-radius),chunk(beam.origin().z-radius))
                        ||!contains(chunk(beam.origin().x+radius),chunk(beam.origin().z+radius)))return false;
            }return true;
        }
        private static int chunk(double coordinate){return ((int)Math.floor(coordinate))>>4;}
        private static double radius(HeadlightBeam b){
            var f=b.forward();var r=b.right();var u=b.up();
            if(b.range()>38||b.horizontalTangent()>.56||b.verticalTangent()>.56
                    ||Math.abs(b.origin().x)>29_999_800||Math.abs(b.origin().z)>29_999_800
                    ||Math.abs(f.lengthSqr()-1)>1e-6||Math.abs(r.lengthSqr()-1)>1e-6||Math.abs(u.lengthSqr()-1)>1e-6
                    ||Math.abs(f.dot(r))>1e-6||Math.abs(f.dot(u))>1e-6||Math.abs(r.dot(u))>1e-6)return Double.NaN;
            // For orthonormal axes, norm <= sum of axial/horizontal/vertical absolute bounds.
            // SurfaceDiscovery cell-center bounds: R+.5L1(f), .15+R*h+.5L1(r), .10+R*v+.5L1(u).
            // Sum of the three half-L1 radii is <= 2.599; .25 constants plus 2-block
            // cell/neighbor halo and a .15 numeric margin are <5. Result also bounds
            // normalized range fan and the .8+R*h road-probe parallelogram/floor cells.
            // ACTUAL observed reads must additionally remain inside the captured rectangle.
            return b.range()*(1+b.horizontalTangent()+b.verticalTangent())+5;
        }
    }
    static final class Snapshot {
        private final Tracker tracker;
        private final Envelope envelope;
        private final Token[] tokens;
        private final long[] versions;
        private final Object models,terrain,quality;
        private Snapshot(Tracker tracker,Envelope envelope,Token[] tokens,long[] versions,Object models,Object terrain,Object quality){
            this.tracker=tracker;this.envelope=envelope;this.tokens=tokens;this.versions=versions;this.models=models;this.terrain=terrain;this.quality=quality;
        }
        boolean containsChunk(long packed){return envelope.containsChunk(packed);}
        boolean valid(Source source,List<HeadlightBeam> actual,Object models,Object terrain,Object quality){
            if(source==null||source.automobility$headlightTerrain()!=tracker||!tracker.usable()
                    ||this.models!=models||this.terrain!=terrain||this.quality!=quality||!envelope.contains(actual))return false;
            int index=0;long before=tracker.mutations;
            for(int x=envelope.minX;x<=envelope.maxX;x++)for(int z=envelope.minZ;z<=envelope.maxZ;z++){
                Token token=tokens[index];Object chunk=source.automobility$headlightChunk(x,z);
                if(chunk==null||tracker.watched.get(key(x,z))!=token||token.version!=versions[index++]||token.chunk.get()!=chunk)return false;
            }return tracker.usable()&&tracker.mutations==before;
        }
    }
    static Snapshot capture(Source source,List<HeadlightBeam> actual,Object models,Object terrain,Object quality){
        if(source==null||models==null||terrain==null||quality==null)return null;
        var tracker=source.automobility$headlightTerrain();var envelope=Envelope.capture(actual);
        if(tracker==null||envelope==null||!tracker.usable())return null;
        int count=(envelope.maxX-envelope.minX+1)*(envelope.maxZ-envelope.minZ+1),index=0;
        var tokens=new Token[count];var versions=new long[count];
        for(int x=envelope.minX;x<=envelope.maxX;x++)for(int z=envelope.minZ;z<=envelope.maxZ;z++){
            Token token=tracker.watch(x,z,source.automobility$headlightChunk(x,z));if(token==null)return null;
            tokens[index]=token;versions[index++]=token.version;
        }
        var result=new Snapshot(tracker,envelope,tokens,versions,models,terrain,quality);
        return result.valid(source,actual,models,terrain,quality)?result:null;
    }
}
