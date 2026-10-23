package io.github.foundationgames.automobility.automobile.garage;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Decision;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Verdict;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.UUID;

/** Optional API2. API1 remains an unchanged-owner-state contract; registration alone unlocks nothing.
 * Core alone owns inventory exchange, Core components, durable decisions and carrier admission.
 * Every call uses the server thread. Only planRaw after an explicit accepted player action may
 * allocate an otherwise missing empty resource identity; previewRaw never allocates one.
 */
public interface GarageEngineTransferParticipant {
    int ENGINE_API_VERSION=2;
    enum Origin { ACTIVE, STORED }
    enum Reason { NONE, BUSY, EMPTY_TANK_REQUIRED, INVALID_INPUT, UNSUPPORTED_ENGINE, UNSUPPORTED_DATA, DUPLICATE_BATTERY }
    record Request(GarageTransferParticipant.Request transaction,Origin origin){
        public Request{
            Objects.requireNonNull(transaction);Objects.requireNonNull(origin);
            if(transaction.operation()!=GarageTransferParticipant.Operation.ENGINE_SWAP)throw new IllegalArgumentException("Engine operation required");
        }
    }
    /** The full offered source stack retains its original count. Core later consumes exactly one.
     * Owners must not normalize a duplicated/stacked charged item before validating it.
     */
    record Input(CompoundTag beforeCarRoot,ItemStack actualIncomingSource,ItemStack canonicalReturnedBase,int sourceSlot){
        public Input{
            Objects.requireNonNull(beforeCarRoot);Objects.requireNonNull(actualIncomingSource);Objects.requireNonNull(canonicalReturnedBase);
            if(beforeCarRoot.isEmpty()||beforeCarRoot.sizeInBytes()>1_048_576||actualIncomingSource.isEmpty()
                    ||canonicalReturnedBase.isEmpty()||canonicalReturnedBase.getCount()!=1||sourceSlot<0||sourceSlot>=36)
                throw new IllegalArgumentException("Exact bounded source vehicle and physical engine stack required");
            beforeCarRoot=beforeCarRoot.copy();actualIncomingSource=actualIncomingSource.copy();canonicalReturnedBase=canonicalReturnedBase.copy();
        }
        @Override public CompoundTag beforeCarRoot(){return beforeCarRoot.copy();}
        @Override public ItemStack actualIncomingSource(){return actualIncomingSource.copy();}
        @Override public ItemStack canonicalReturnedBase(){return canonicalReturnedBase.copy();}
    }
    /** Complete owned projections, including exact absence/type/value, not arbitrary root patches.
     * Root ownership: P&P AutoFuel* + AutoTrip; PW powerworks:*.
     * Item ownership: only PW powerworks:traction_battery within CUSTOM_DATA; P&P owns no item key.
     * Core rejects collisions, foreign keys and unknown item components. It merges owner fragments
     * into one returned engine, ORs returnedSingleton and freezes the final shared exchange.
     * privateState is a versioned nonempty owner envelope with policy/disposition and recovery facts.
     * No owner may debit inventory, spawn an item, refund a resource or mutate the input in planning.
     */
    record OwnerPlan(CompoundTag beforeCarOwner,CompoundTag afterCarOwner,CompoundTag incomingItemOwner,
                     CompoundTag returnedItemOwner,boolean returnedSingleton,CompoundTag privateState){
        public OwnerPlan{
            Objects.requireNonNull(beforeCarOwner);Objects.requireNonNull(afterCarOwner);Objects.requireNonNull(incomingItemOwner);Objects.requireNonNull(returnedItemOwner);Objects.requireNonNull(privateState);
            if(privateState.isEmpty()||(long)beforeCarOwner.sizeInBytes()+afterCarOwner.sizeInBytes()+incomingItemOwner.sizeInBytes()+returnedItemOwner.sizeInBytes()+privateState.sizeInBytes()>1_048_576)
                throw new IllegalArgumentException("Bounded exact versioned engine owner plan required");
            beforeCarOwner=beforeCarOwner.copy();afterCarOwner=afterCarOwner.copy();incomingItemOwner=incomingItemOwner.copy();returnedItemOwner=returnedItemOwner.copy();privateState=privateState.copy();
        }
        @Override public CompoundTag beforeCarOwner(){return beforeCarOwner.copy();}
        @Override public CompoundTag afterCarOwner(){return afterCarOwner.copy();}
        @Override public CompoundTag incomingItemOwner(){return incomingItemOwner.copy();}
        @Override public CompoundTag returnedItemOwner(){return returnedItemOwner.copy();}
        @Override public CompoundTag privateState(){return privateState.copy();}
    }
    record Preview(Verdict verdict,Reason reason){
        public Preview{Objects.requireNonNull(verdict);Objects.requireNonNull(reason);if(verdict==Verdict.READY&&reason!=Reason.NONE)throw new IllegalArgumentException();}
    }
    record Planning(Verdict verdict,Reason reason,OwnerPlan plan){
        public Planning{Objects.requireNonNull(verdict);Objects.requireNonNull(reason);if((verdict==Verdict.READY)!=(plan!=null)||(verdict==Verdict.READY&&reason!=Reason.NONE))throw new IllegalArgumentException();}
    }
    /** Created by Core after all owner plans, slots and full before/after Core/inventory images
     * have been frozen. Digest is SHA256 of that canonical full context, excluding the digest itself.
     * It binds the same immutable context on every owner lease and journal recovery attempt.
     */
    record Exchange(ItemStack actualIncomingSource,ItemStack finalReturnedEngine,String frozenPlanDigest){
        public Exchange{
            Objects.requireNonNull(actualIncomingSource);Objects.requireNonNull(finalReturnedEngine);
            if(actualIncomingSource.isEmpty()||finalReturnedEngine.isEmpty()||finalReturnedEngine.getCount()!=1
                    ||frozenPlanDigest==null||!frozenPlanDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Frozen complete engine exchange required");
            actualIncomingSource=actualIncomingSource.copy();finalReturnedEngine=finalReturnedEngine.copy();
        }
        @Override public ItemStack actualIncomingSource(){return actualIncomingSource.copy();}
        @Override public ItemStack finalReturnedEngine(){return finalReturnedEngine.copy();}
    }
    interface EngineFence extends GarageTransferParticipant.Fence {
        /** Exact actual object/world/UUID/tx/generation and full frozen context; this can hold before INTENT.
         * Apply additionally requires inherited intentDurableFor, recovery the actual durable decision.
         */
        boolean matchesPlan(AutomobileEntity car,UUID transaction,long generation,String frozenPlanDigest);
    }
    interface Prepared {
        /** Defensive exact plan. Core compares this with the frozen owner/version capsule at each phase. */
        OwnerPlan plan();
        Verdict validateBeforeApply();
        Verdict applyQuiet();
        Verdict validateApplied();
        /** Core restores the before components first. Refuse COMMIT; no ordinary init/setters/publication. */
        Verdict restoreQuiet();
        Verdict publish(Decision decision);
        Verdict closeLocal();
    }
    record Preparation(Verdict verdict,Prepared prepared){
        public Preparation{Objects.requireNonNull(verdict);if((verdict==Verdict.READY)!=(prepared!=null))throw new IllegalArgumentException();}
    }
    String id();
    default int engineApiVersion(){return ENGINE_API_VERSION;}
    int enginePlanVersion();
    /** Read-only readiness: no random UUID, resource initialization, mutation, claim or publication. */
    Preview previewRaw(ServerLevel level,Request request,Input input);
    /** Only Core's accepted explicit action invokes this, never hover/preview/recovery. Any new empty
     * identity is created once here and frozen before INTENT; failed/unknown payload is not absence.
     */
    Planning planRaw(ServerLevel level,Request request,Input input);
    Preparation prepare(Request request,AutomobileEntity beforeCarrier,EngineFence fence,OwnerPlan plan,Exchange exchange);
    /** Recognize only exact known before/after or planned component/owner intermediate states.
     * No remint, re-debit, refund, ordinary initialization or stale after-state overwrite.
     * COMMIT completes after; ROLLBACK restores before. Every method tolerates repeated fresh leases.
     */
    Preparation recover(Request request,AutomobileEntity carrier,EngineFence fence,OwnerPlan plan,Exchange exchange,Decision decision);
}
