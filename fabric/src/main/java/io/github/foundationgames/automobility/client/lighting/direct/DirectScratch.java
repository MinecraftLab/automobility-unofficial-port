package io.github.foundationgames.automobility.client.lighting.direct;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.vertices.BufferBuilderPolygonView;
import net.irisshaders.iris.vertices.NormalHelper;
import net.irisshaders.iris.vertices.NormI8;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/** Own native bytes only. Posed methods inherit actual Sodium interface defaults. */
final class DirectScratch implements VertexConsumer,AutoCloseable {
    private ByteBuffer memory;
    private final long[] quadOffsets=new long[4];
    private final BufferBuilderPolygonView polygon=new BufferBuilderPolygonView();
    private final Vector3f face=new Vector3f();
    private long base,current;
    private int expected,count,completed,mask;
    private DirectPinnedIris.Ids ids;

    int capacity(){return memory==null?0:memory.capacity();}
    void reset(int vertices,DirectPinnedIris.Ids context){
        int bytes=DirectAdmission.bytes(vertices);
        if(memory==null||memory.capacity()<bytes){
            int capacity=256;while(capacity<bytes)capacity=Math.multiplyExact(capacity,2);
            if(capacity>DirectAdmission.MAX_BYTES)throw new IllegalArgumentException("Native scratch budget");
            ByteBuffer fresh=MemoryUtil.memAlloc(capacity);if(memory!=null)MemoryUtil.memFree(memory);memory=fresh;
        }
        memory.clear();base=MemoryUtil.memAddress(memory);current=0;expected=vertices;count=completed=mask=0;ids=context;
    }
    private void requireCurrent(){if(current==0||completed==count)throw new IllegalStateException("No open scratch vertex");}
    private void completeVertex(){
        if(current==0||completed==count)return;
        if(mask!=31)throw new IllegalStateException("Incomplete ENTITY vertex attributes");
        quadOffsets[(count-1)&3]=(long)(count-1)*DirectAdmission.STRIDE;completed=count;
        if((count&3)==0){
            polygon.setup(base,quadOffsets,0,16);
            float u=0,v=0;for(int i=0;i<4;i++){u+=polygon.u(i);v+=polygon.v(i);}u/=4;v/=4;
            NormalHelper.computeFaceNormal(face,polygon);
            int normal=NormI8.pack(face.x,face.y,face.z,0);
            int tangent=NormalHelper.computeTangent(face.x,face.y,face.z,polygon);
            for(int i=0;i<4;i++){
                long vertex=base+quadOffsets[i];MemoryUtil.memPutInt(vertex+32,normal);
                MemoryUtil.memPutFloat(vertex+44,u);MemoryUtil.memPutFloat(vertex+48,v);MemoryUtil.memPutInt(vertex+52,tangent);
            }
        }
    }
    ByteBuffer finish(){
        completeVertex();if(count!=expected||completed!=count||(count&3)!=0)throw new IllegalStateException("Exact whole scratch stream");
        memory.position(0).limit(DirectAdmission.bytes(count));return memory;
    }
    @Override public VertexConsumer addVertex(float x,float y,float z){
        completeVertex();if(count==expected)throw new IllegalStateException("Scratch vertex overflow");
        current=base+(long)count*DirectAdmission.STRIDE;count++;mask=0;
        MemoryUtil.memPutFloat(current,x);MemoryUtil.memPutFloat(current+4,y);MemoryUtil.memPutFloat(current+8,z);
        MemoryUtil.memPutShort(current+36,(short)ids.entity());MemoryUtil.memPutShort(current+38,(short)ids.blockEntity());
        MemoryUtil.memPutShort(current+40,(short)ids.item());MemoryUtil.memPutShort(current+42,(short)0);return this;
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a){
        requireCurrent();MemoryUtil.memPutByte(current+12,(byte)r);MemoryUtil.memPutByte(current+13,(byte)g);
        MemoryUtil.memPutByte(current+14,(byte)b);MemoryUtil.memPutByte(current+15,(byte)a);mask|=1;return this;
    }
    @Override public VertexConsumer setColor(int argb){return setColor(argb>>>16&255,argb>>>8&255,argb&255,argb>>>24);}
    @Override public VertexConsumer setUv(float u,float v){requireCurrent();MemoryUtil.memPutFloat(current+16,u);MemoryUtil.memPutFloat(current+20,v);mask|=2;return this;}
    @Override public VertexConsumer setUv1(int u,int v){requireCurrent();MemoryUtil.memPutShort(current+24,(short)u);MemoryUtil.memPutShort(current+26,(short)v);mask|=4;return this;}
    @Override public VertexConsumer setUv2(int u,int v){requireCurrent();MemoryUtil.memPutShort(current+28,(short)u);MemoryUtil.memPutShort(current+30,(short)v);mask|=8;return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){requireCurrent();MemoryUtil.memPutInt(current+32,NormI8.pack(x,y,z,0));mask|=16;return this;}
    @Override public VertexConsumer setLineWidth(float width){throw new UnsupportedOperationException("ENTITY has no line width");}
    @Override public void close(){if(memory!=null){MemoryUtil.memFree(memory);memory=null;}base=current=0;ids=null;}
}
