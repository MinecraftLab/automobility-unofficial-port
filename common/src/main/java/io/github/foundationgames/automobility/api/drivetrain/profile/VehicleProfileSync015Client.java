package io.github.foundationgames.automobility.api.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainProtocol;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;

/** Actual optional channel receiver backed by the bounded accepted cache; no client-local physics fallback. */
public final class VehicleProfileSync015Client implements ClientModInitializer{
    private static final ProfileClientCache CACHE=new ProfileClientCache();
    private static Object connection,sender,level;private static long tick,requestSentAt;private static int outstanding=-1;
    @Override public void onInitializeClient(){
        ClientPlayConnectionEvents.JOIN.register((handler,responseSender,client)->{reset();connection=handler;sender=responseSender;level=client.level;});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(connection==handler)reset();});
        ClientPlayNetworking.registerGlobalReceiver(ProfileDefinitionPayloads015.Announce.TYPE,(payload,context)->{
            Object received=context.client().getConnection(),origin=context.responseSender(),world=context.client().level;
            context.client().execute(()->{if(received!=connection||origin!=sender||received!=context.client().getConnection()||world!=context.client().level)return;syncWorld(world);CACHE.bind(connection,payload.offer().worldSession());if(CACHE.offer(connection,payload.offer(),tick))outstanding=-1;});
        });
        ClientPlayNetworking.registerGlobalReceiver(ProfileDefinitionPayloads015.Chunk.TYPE,(payload,context)->{
            Object received=context.client().getConnection(),origin=context.responseSender(),world=context.client().level;
            context.client().execute(()->{if(received!=connection||origin!=sender||received!=context.client().getConnection()||world!=context.client().level)return;
                syncWorld(world);var offer=CACHE.pendingOffer().orElse(null);if(offer==null||outstanding!=payload.index())return;var r=payload.reference();
                if(!offer.worldSession().equals(r.worldSession())||offer.epoch()!=r.epoch()||!offer.sha256().equals(r.sha256()))return;
                CACHE.chunk(connection,offer,payload.index(),payload.bytes(),tick);outstanding=-1;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(connection!=client.getConnection()){reset();return;}syncWorld(client.level);
            if(tick==Long.MAX_VALUE){reset();return;}CACHE.tick(++tick);var pending=CACHE.pendingOffer().orElse(null);
            if(pending==null||outstanding>=0&&tick-requestSentAt>=20)outstanding=-1;
            if(connection==null||pending==null||outstanding>=0||!ClientPlayNetworking.canSend(ProfileDefinitionPayloads015.Request.TYPE))return;
            int next=CACHE.nextChunkIndex();var reference=new ProfileViews.Reference(pending.worldSession(),pending.epoch(),pending.sha256());
            try{ClientPlayNetworking.send(new ProfileDefinitionPayloads015.Request(reference,next));outstanding=next;requestSentAt=tick;}catch(RuntimeException unavailable){CACHE.disconnect();outstanding=-1;}
        });
    }
    /** Both future garage presentation and Runtime prediction resolve via this entry; missing/old hash returns empty. */
    public static Optional<PhysicalDefinitions.Selection>approved(ProfileViews.Reference authority,String frame,String wheel,String engine){if(!actualContext())return Optional.empty();return ProfileViews.client(CACHE,connection,authority,frame,wheel,engine);}
    public static Optional<PhysicalDefinitions.Selection>approvedPrediction(ProfilePayloadApproval approval,DrivetrainProtocol.Identity actualIdentity,
            long acceptedSnapshotSequence,String frame,String wheel,String engine,Optional<UUID>physicalMotor){
        if(!actualContext())return Optional.empty();return Objects.requireNonNull(approval).prediction(CACHE,connection,actualIdentity,acceptedSnapshotSequence,frame,wheel,engine,physicalMotor);
    }
    /** Read-only current world authority for a garage without a mounted/live car. No client JSON or provider fallback. */
    public static Optional<ProfileViews.Reference>acceptedReference(){
        if(!actualContext())return Optional.empty();return CACHE.accepted().map(a->new ProfileViews.Reference(a.offer().worldSession(),a.offer().epoch(),a.offer().sha256()));
    }
    private static boolean actualContext(){var client=Minecraft.getInstance();if(connection==null||connection!=client.getConnection()||client.level==null)return false;syncWorld(client.level);return true;}
    /** Packet callbacks and end tick share the same native level fence, so a just-received new-world offer is not erased on the next tick. */
    private static void syncWorld(Object actualLevel){if(level!=actualLevel){level=actualLevel;CACHE.disconnect();outstanding=-1;}}
    private static void reset(){CACHE.disconnect();connection=sender=level=null;tick=requestSentAt=0;outstanding=-1;}
}
