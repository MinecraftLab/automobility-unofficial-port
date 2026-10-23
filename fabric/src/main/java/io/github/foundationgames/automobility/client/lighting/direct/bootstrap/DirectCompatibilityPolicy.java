package io.github.foundationgames.automobility.client.lighting.direct.bootstrap;
/** Pure admission; no optional types or game classes may be linked here. */
public final class DirectCompatibilityPolicy {
    public static final String IRIS_VERSION="1.11.4+mc26.2",SODIUM_VERSION="0.9.2+mc26.2";
    public static final String IRIS_SHA="F1F7AB57C974D193BA33AA285864A0DED949216F402116FCEAF4DC7739B4DD7C";
    public static final String SODIUM_SHA="16A5E91DB49750F8C046ECCA3B8F2AF5A28EA6F9B6581B42F123CA8FDA20864F";
    public record Inputs(boolean requested,boolean client,String minecraft,String core,String irisVersion,String sodiumVersion,String irisSha,String sodiumSha){}
    public record Decision(boolean allowed,String reason){}
    public static Decision evaluate(Inputs i){
        if(i==null)return new Decision(false,"missing inputs");
        if(!i.requested())return new Decision(false,"opt-in OFF");
        if(!i.client())return new Decision(false,"client only");
        if(!"26.2".equals(i.minecraft())||(!"1.2.0-dev.50+26.2-fabric".equals(i.core())&&!"1.2.0-dev.51+26.2-fabric".equals(i.core())&&!"1.2.0-dev.52+26.2-fabric".equals(i.core())&&!"1.3.0-dev.8+26.2-fabric".equals(i.core())&&!"1.3.1+26.2-fabric".equals(i.core())))return new Decision(false,"unsupported game/core");
        if(!IRIS_VERSION.equals(i.irisVersion())||!SODIUM_VERSION.equals(i.sodiumVersion()))return new Decision(false,"unsupported optional versions");
        if(!IRIS_SHA.equals(i.irisSha())||!SODIUM_SHA.equals(i.sodiumSha()))return new Decision(false,"unsupported optional bytes");
        return new Decision(true,"pinned direct backend admitted");
    }
    private DirectCompatibilityPolicy(){}
}
