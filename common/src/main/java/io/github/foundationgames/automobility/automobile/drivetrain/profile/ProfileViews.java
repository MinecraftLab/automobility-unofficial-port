package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.util.*;

/** Shared mandatory lookup for garage and prediction. An absent accepted definition never falls back to client-local calibration. */
public final class ProfileViews{
    public record Reference(UUID worldSession,long epoch,String sha256){public Reference{Objects.requireNonNull(worldSession);ProfileDefinitionCodec.checkHash(sha256);if(epoch<1)throw new IllegalArgumentException("profile epoch");}}
    private ProfileViews(){}
    public static Optional<PhysicalDefinitions.Selection> client(ProfileClientCache cache,Object actualConnection,Reference authority,
            String frameId,String wheelId,String engineId){
        Objects.requireNonNull(cache);Objects.requireNonNull(authority);
        return cache.lookup(actualConnection,authority.worldSession(),authority.epoch(),authority.sha256()).flatMap(d->d.resolve(frameId,wheelId,engineId));
    }
    public static Optional<PhysicalDefinitions.Selection> server(ProfileReloadGate.Snapshot authority,String frameId,String wheelId,String engineId){
        return Objects.requireNonNull(authority).definitions().resolve(frameId,wheelId,engineId);
    }
}
