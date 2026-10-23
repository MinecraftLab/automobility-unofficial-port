package io.github.foundationgames.automobility.client.lighting.direct;

/** Pure bounds before any owned allocation or native target mutation. */
final class DirectAdmission {
    static final int STRIDE=56,MAX_BYTES=4*1024*1024,MAX_NATIVE_VERTICES=16777215;
    static boolean allows(int sourceVertices,int targetVertices){
        return sourceVertices>0&&(sourceVertices&3)==0&&targetVertices>=0&&(targetVertices&3)==0
                &&(long)sourceVertices*STRIDE<=MAX_BYTES&&(long)targetVertices+sourceVertices<=MAX_NATIVE_VERTICES;
    }
    static int bytes(int sourceVertices){if(!allows(sourceVertices,0))throw new IllegalArgumentException("Bounded whole ENTITY56 quads");return sourceVertices*STRIDE;}
}
