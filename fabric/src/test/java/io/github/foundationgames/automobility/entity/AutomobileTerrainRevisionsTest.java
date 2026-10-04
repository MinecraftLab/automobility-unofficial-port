package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.WheelBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileTerrainRevisionsTest {
    @Test
    void stationaryCarRefreshesForLateTerrainEvenAfterThousandsOfTicks() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
        for (int i = 0; i < 2_000; i++) {
            assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8), "unchanged parked cars do not scan");
        }
        terrain.changed(0, 0); // Full chunk arrival or a floor block delivered after the first scan.
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
    }

    @Test
    void laterSupportChangesAndRepeatedChunkPacketsAreNotOnlyAnInitialSpawnFix() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(0, 0);
        displacement.shouldRefreshTerrain(terrain, 8, 8);
        for (int event = 0; event < 4; event++) {
            terrain.changed(0, 0);
            assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
            assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
        }
    }

    @Test
    void unrelatedTerrainDoesNotRescanAStationaryCarAndBurstUpdatesCoalesce() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(0, 0);
        displacement.shouldRefreshTerrain(terrain, 8, 8);
        for (int i = 0; i < 100; i++) {
            terrain.changed(30, -40);
            assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
        }
        for (int i = 0; i < 100; i++) terrain.changed(0, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
    }

    @Test
    void longAddonWheelbaseCoversNeighboringChunkRegardlessOfYaw() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = new AutomobileEntity.Displacement();
        displacement.applyWheelbase(WheelBase.basic(72, 32));
        terrain.changed(0, 0);
        terrain.changed(1, 0);
        displacement.shouldRefreshTerrain(terrain, 13, 8);
        terrain.changed(1, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 13, 8), "long wheel scan plus shape halo crosses x=16");
        terrain.changed(0, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 13, 8), "new revision in either chunk cannot cancel another");
    }

    @Test
    void negativeCoordinatesAndMovedFootprintsUseFloorNotTruncation() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(-1, 0);
        terrain.changed(0, 0);
        displacement.shouldRefreshTerrain(terrain, -0.25, 8);
        terrain.changed(-1, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, -0.25, 8));
        assertTrue(displacement.shouldRefreshTerrain(terrain, 24, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 24, 8));
    }

    @Test
    void actualDisplacementWheelbaseAndWheelInvalidationRefreshWithoutTerrainChange() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        displacement.shouldRefreshTerrain(terrain, 8, 8);
        displacement.applyWheelbase(WheelBase.basic(49, 24));
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8), "frame metadata changes the scan points");
        assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
        displacement.invalidateTerrain(); // Same production invalidation used for WHEEL_TYPE and ownership changes.
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
    }

    @Test
    void unloadingDropsStorageAndInvalidatesAnOverlappingFootprint() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(0, 0);
        terrain.changed(1, 0);
        displacement.shouldRefreshTerrain(terrain, 15.5, 8);
        terrain.unloaded(1, 0);
        assertEquals(1, terrain.trackedChunkCount());
        assertTrue(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
        terrain.changed(1, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
        for (int i = 20; i < 1_020; i++) {
            terrain.changed(i, 0);
            terrain.unloaded(i, 0);
        }
        assertEquals(2, terrain.trackedChunkCount(), "visited chunks must not accumulate forever");
    }

    @Test
    void aDifferentClientWorldRefreshesEvenWithMatchingRevisionNumbers() {
        var firstWorld = new AutomobileTerrainRevisions();
        var secondWorld = new AutomobileTerrainRevisions();
        firstWorld.changed(0, 0);
        secondWorld.changed(0, 0);
        var displacement = standardDisplacement();
        displacement.shouldRefreshTerrain(firstWorld, 8, 8);
        assertTrue(displacement.shouldRefreshTerrain(secondWorld, 8, 8));
    }

    @Test
    void viewStorageChangesInvalidateUnchangedFootprintsAndDiscardSilentEvictions() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(0, 0);
        terrain.changed(1, 0);
        displacement.shouldRefreshTerrain(terrain, 15.5, 8);
        terrain.storageChanged(); // Radius shrink or center shift can skip ClientLevel.unload.
        assertEquals(0, terrain.trackedChunkCount());
        assertTrue(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
        assertFalse(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
        terrain.changed(50, 50);
        assertFalse(displacement.shouldRefreshTerrain(terrain, 15.5, 8), "unrelated new chunks do not hide a later local update");
        terrain.changed(1, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 15.5, 8));
    }

    @Test
    void repeatedViewTransitionsDoNotAccumulateEvictedChunkRevisions() {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        displacement.shouldRefreshTerrain(terrain, 8, 8);
        for (int i = 0; i < 1_000; i++) {
            terrain.changed(i, i);
            terrain.storageChanged();
            assertEquals(0, terrain.trackedChunkCount());
            assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
            assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
        }
    }

    @Test
    void hugeOrInvalidAddonFootprintsConservativelyUseTheWorldRevision() {
        for (double radius : new double[] {1.0E12, Double.POSITIVE_INFINITY, Double.NaN}) {
            var terrain = new AutomobileTerrainRevisions();
            var sample = new AutomobileTerrainRevisions.Sample();
            assertTrue(sample.shouldRefresh(terrain, 8, 8, radius));
            assertFalse(sample.shouldRefresh(terrain, 8, 8, radius));
            terrain.changed(1_000_000, 0);
            assertTrue(sample.shouldRefresh(terrain, 8, 8, radius));
        }
    }

    @Test
    void revisionOverflowStartsANewGenerationInsteadOfHidingChangedChunks() throws Exception {
        var terrain = new AutomobileTerrainRevisions();
        var displacement = standardDisplacement();
        terrain.changed(0, 0);
        displacement.shouldRefreshTerrain(terrain, 8, 8);
        var counter = AutomobileTerrainRevisions.class.getDeclaredField("revision");
        counter.setAccessible(true);
        counter.setLong(terrain, Long.MAX_VALUE);
        terrain.changed(50, 50);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8), "generation reset invalidates earlier samples");
        assertFalse(displacement.shouldRefreshTerrain(terrain, 8, 8));
        terrain.changed(0, 0);
        assertTrue(displacement.shouldRefreshTerrain(terrain, 8, 8));
    }

    private static AutomobileEntity.Displacement standardDisplacement() {
        var displacement = new AutomobileEntity.Displacement();
        displacement.applyWheelbase(WheelBase.basic(32, 24));
        return displacement;
    }
}
