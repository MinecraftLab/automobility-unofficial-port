package io.github.milkucha.momentum.network;
/** Pure connection-local exchange policy; context objects are compared by identity. */
public final class CruiseMotorLease<C> {
    public static final int LEASE_MS = 1000, CLIENT_MARGIN_MS = 100, RENEW_MS = 250;
    public record Pending<C>(long sequence,C context,float target,long sentNanos,boolean renewal) {}
    private long nonce, sequence, grantSent; private C context; private float target;
    private int durationMs; private Pending<C> pending;
    public void begin(long nonce) { if(nonce==0)throw new IllegalArgumentException("Nonzero feature nonce");this.nonce=nonce;sequence=0;clear(); }
    public void end() { clear();nonce=0;sequence=0; }
    private void clear(){context=null;durationMs=0;pending=null;}
    public static boolean fresh(long sent,long now,int ms) { long age=now-sent;return ms>0&&ms<=LEASE_MS&&age>=0&&age<(long)ms*1_000_000; }
    public boolean active(C key,long now) { return nonce!=0&&context==key&&fresh(grantSent,now,durationMs); }
    public float target(C key,long now) { return active(key,now)?target:Float.NaN; }
    public boolean waiting(long now) { return pending!=null&&fresh(pending.sentNanos,now,LEASE_MS-CLIENT_MARGIN_MS); }
    public boolean needsRequest(C key,float requested,long now) {
        if(waiting(now)&&pending.context==key&&Float.floatToIntBits(pending.target)==Float.floatToIntBits(requested))return false;
        if(!active(key,now)||Float.floatToIntBits(target)!=Float.floatToIntBits(requested))return true;
        return now-grantSent>=(long)RENEW_MS*1_000_000;
    }
    private long next(){if(nonce==0||sequence==Long.MAX_VALUE){end();return 0;}return ++sequence;}
    public Pending<C> request(C key,float requested,long now) {
        if(key==null||!Float.isFinite(requested)||requested<0)return null;
        boolean renewal=active(key,now)&&Float.floatToIntBits(target)==Float.floatToIntBits(requested);
        long seq=next();if(seq==0)return null;
        if(!renewal){context=null;durationMs=0;}
        return pending=new Pending<>(seq,key,requested,now,renewal);
    }
    public boolean receive(long nonce,long seq,C key,float approved,boolean accepted,int ms,long now) {
        var p=pending;
        if(p==null||nonce==0||nonce!=this.nonce||seq!=p.sequence||key!=p.context)return false;
        pending=null;
        if(!accepted||!Float.isFinite(approved)||Float.floatToIntBits(approved)!=Float.floatToIntBits(p.target)
                ||ms<=CLIENT_MARGIN_MS||ms>LEASE_MS||!fresh(p.sentNanos,now,ms-CLIENT_MARGIN_MS)) {context=null;durationMs=0;return false;}
        context=key;target=approved;grantSent=p.sentNanos;durationMs=ms-CLIENT_MARGIN_MS;return true;
    }
    /** Keeps the sequence watermark: a late grant cannot revive a canceled exchange. */
    public long cancel(){long seq=next();clear();return seq;}
    public long nonce(){return nonce;}
    public Pending<C> pending(){return pending;}
    /** An offered cap does not own the legacy pedal. First-use RENEW confirms ownership. */
    public static final class ForwardOwnership {
        private boolean confirmed;
        public void offer(){confirmed=false;}
        public void confirm(){confirmed=true;}
        public boolean withdraw(boolean manualForward){boolean remove=confirmed&&!manualForward;confirmed=false;return remove;}
    }
    public static final class SequenceGate {
        private long nonce,last;
        public boolean accept(long nonce,long sequence){if(nonce==0||sequence<=0||sequence<=last)return false;if(this.nonce!=0&&this.nonce!=nonce)return false;this.nonce=nonce;last=sequence;return true;}
        public long nonce(){return nonce;}
        public long last(){return last;}
    }
}
