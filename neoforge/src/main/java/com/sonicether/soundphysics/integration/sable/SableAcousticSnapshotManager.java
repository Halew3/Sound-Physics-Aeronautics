package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsPerfDiagnostics;
import com.sonicether.soundphysics.acoustic.AcousticSceneContext;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

final class SableAcousticSnapshotManager {

    private static final AtomicReference<SableAcousticSnapshot> CURRENT = new AtomicReference<>();
    private static final SableAcousticDiagnostics DIAGNOSTICS = new SableAcousticDiagnostics();
    private static final SableAcousticSourceCache SOURCE_CACHE = new SableAcousticSourceCache();
    private static final Map<UUID, SableAcousticSpaceMembership> SPACE_MEMBERSHIPS = new HashMap<>();
    private static final long SNAPSHOT_LOG_INTERVAL_TICKS = 20L * 10L;

    private static long nextVersion = 1L;
    private static long nextMembershipVersion = 1L;
    private static long lastSignature = Long.MIN_VALUE;
    private static long lastMembershipSignature = Long.MIN_VALUE;
    private static long lastPeriodicSnapshotLogGameTime = Long.MIN_VALUE;
    private static int lastLoggedSpaceCount = Integer.MIN_VALUE;
    private static int lastLoggedChunkCount = Integer.MIN_VALUE;
    private static int lastLoggedSkippedUnfinalized = Integer.MIN_VALUE;
    private static int lastLoggedSkippedRemoved = Integer.MIN_VALUE;
    private static int lastLoggedSkippedFailed = Integer.MIN_VALUE;
    private static boolean loggedFirstSnapshotWithSpaces;
    @Nullable
    private static ClientLevel lastLevel;

    private SableAcousticSnapshotManager() {
    }

    static @Nullable SableAcousticSnapshot current() {
        return CURRENT.get();
    }

    static SableAcousticDiagnostics diagnostics() {
        return DIAGNOSTICS;
    }

    static String diagnosticsSummaryText() {
        SableAcousticSnapshot snapshot = CURRENT.get();
        return "snapshot=" + (snapshot == null ? "none" : ("poseV=" + snapshot.version() + " membershipV=" + snapshot.membershipVersion() + " gameTime=" + snapshot.gameTime()))
                + ", " + DIAGNOSTICS.summary();
    }

    static void resetDiagnostics() {
        DIAGNOSTICS.reset();
        SOURCE_CACHE.clear();
        resetSnapshotLogState();
    }

    static List<SableAcousticSpace> sourceCandidates(SableAcousticSnapshot snapshot, AcousticSceneContext context) {
        return SOURCE_CACHE.candidatesFor(snapshot, context, DIAGNOSTICS);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;

        if (level == null
                || SoundPhysicsMod.CONFIG == null
                || !SoundPhysicsMod.CONFIG.enabled.get()
                || !DiagnosticRuntimeOverrides.sableAcousticsEnabled(SoundPhysicsMod.CONFIG)) {
            clear();
            return;
        }

        try {
            long startNanos = System.nanoTime();
            SableAcousticSnapshot snapshot = buildSnapshot(level);
            SoundPhysicsPerfDiagnostics.recordSableSnapshotBuild(System.nanoTime() - startNanos);
            CURRENT.set(snapshot);
            DIAGNOSTICS.recordSnapshot(snapshot);
            if (shouldLogSnapshot(snapshot)) {
                DIAGNOSTICS.logSnapshot(snapshot);
            }
        } catch (Throwable throwable) {
            Loggers.warn("Failed to build Sable acoustic snapshot; falling back to root acoustic world: {}", throwable.getMessage());
            clear();
        }
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    static void clear() {
        CURRENT.set(null);
        SOURCE_CACHE.clear();
        SPACE_MEMBERSHIPS.clear();
        lastLevel = null;
        lastSignature = Long.MIN_VALUE;
        lastMembershipSignature = Long.MIN_VALUE;
        resetSnapshotLogState();
    }

    private static void resetSnapshotLogState() {
        lastPeriodicSnapshotLogGameTime = Long.MIN_VALUE;
        lastLoggedSpaceCount = Integer.MIN_VALUE;
        lastLoggedChunkCount = Integer.MIN_VALUE;
        lastLoggedSkippedUnfinalized = Integer.MIN_VALUE;
        lastLoggedSkippedRemoved = Integer.MIN_VALUE;
        lastLoggedSkippedFailed = Integer.MIN_VALUE;
        loggedFirstSnapshotWithSpaces = false;
    }

    private static SableAcousticSnapshot buildSnapshot(ClientLevel level) {
        if (level != lastLevel) {
            SPACE_MEMBERSHIPS.clear();
        }

        ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(level);
        if (container == null) {
            SPACE_MEMBERSHIPS.clear();
            return snapshot(level, List.of(), 0, 0, 0, 0, 0L, 0L);
        }

        List<SableAcousticSpaceSnapshot> spaces = new ArrayList<>();
        Set<UUID> activeMemberships = new HashSet<>();
        int skippedUnfinalized = 0;
        int skippedRemoved = 0;
        int skippedFailed = 0;
        int chunkCount = 0;

        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            UUID subLevelId = subLevel.getUniqueId();
            if (subLevel.isRemoved()) {
                SPACE_MEMBERSHIPS.remove(subLevelId);
                skippedRemoved++;
                continue;
            }
            if (!subLevel.isFinalized()) {
                SPACE_MEMBERSHIPS.remove(subLevelId);
                skippedUnfinalized++;
                continue;
            }

            try {
                SableAcousticSpaceMembership membership = SableAcousticSpaceMembership.create(level, subLevel, SPACE_MEMBERSHIPS.get(subLevelId));
                SPACE_MEMBERSHIPS.put(subLevelId, membership);
                activeMemberships.add(subLevelId);
                SableAcousticSpaceSnapshot space = SableAcousticSpaceSnapshot.create(level, subLevel, membership);
                spaces.add(space);
                chunkCount += space.chunkCount();
            } catch (Throwable throwable) {
                SPACE_MEMBERSHIPS.remove(subLevelId);
                skippedFailed++;
                Loggers.logDebug("Skipping Sable acoustic space {} after snapshot failure: {}", subLevel.getUniqueId(), throwable.getMessage());
            }
        }

        SPACE_MEMBERSHIPS.keySet().removeIf(subLevelId -> !activeMemberships.contains(subLevelId));
        spaces.sort(Comparator.comparing(SableAcousticSpaceSnapshot::acousticId));
        long signature = signature(spaces, skippedUnfinalized, skippedRemoved, skippedFailed, chunkCount);
        long membershipSignature = membershipSignature(spaces, skippedUnfinalized, skippedRemoved, skippedFailed, chunkCount);
        return snapshot(level, spaces, chunkCount, skippedUnfinalized, skippedRemoved, skippedFailed, signature, membershipSignature);
    }

    private static SableAcousticSnapshot snapshot(
            ClientLevel level,
            List<SableAcousticSpaceSnapshot> spaces,
            int chunkCount,
            int skippedUnfinalized,
            int skippedRemoved,
            int skippedFailed,
            long signature,
            long membershipSignature
    ) {
        boolean levelChanged = level != lastLevel;
        if (levelChanged || signature != lastSignature) {
            nextVersion++;
            lastSignature = signature;
        }
        if (levelChanged || membershipSignature != lastMembershipSignature) {
            nextMembershipVersion++;
            SOURCE_CACHE.clear();
            lastMembershipSignature = membershipSignature;
        }
        lastLevel = level;

        List<SableAcousticSpace> acousticSpaces = List.copyOf(spaces);
        return new SableAcousticSnapshot(level, level.getGameTime(), nextVersion, signature, nextMembershipVersion, membershipSignature, acousticSpaces, chunkCount, skippedUnfinalized, skippedRemoved, skippedFailed);
    }

    private static long signature(List<SableAcousticSpaceSnapshot> spaces, int skippedUnfinalized, int skippedRemoved, int skippedFailed, int chunkCount) {
        long hash = 1469598103934665603L;
        hash = mix(hash, skippedUnfinalized);
        hash = mix(hash, skippedRemoved);
        hash = mix(hash, skippedFailed);
        hash = mix(hash, chunkCount);

        for (SableAcousticSpaceSnapshot space : spaces) {
            hash = mix(hash, space.signature());
        }

        return hash;
    }

    private static long membershipSignature(List<SableAcousticSpaceSnapshot> spaces, int skippedUnfinalized, int skippedRemoved, int skippedFailed, int chunkCount) {
        long hash = 1469598103934665603L;
        hash = mix(hash, skippedUnfinalized);
        hash = mix(hash, skippedRemoved);
        hash = mix(hash, skippedFailed);
        hash = mix(hash, chunkCount);

        for (SableAcousticSpaceSnapshot space : spaces) {
            hash = mix(hash, space.membershipSignature());
        }

        return hash;
    }

    private static boolean shouldLogSnapshot(SableAcousticSnapshot snapshot) {
        if (!isSableSnapshotLoggingEnabled()) {
            return false;
        }

        boolean firstWithSpaces = snapshot.hasSableSpaces() && !loggedFirstSnapshotWithSpaces;
        boolean countsChanged = lastLoggedSpaceCount != Integer.MIN_VALUE
                && (snapshot.spaces().size() != lastLoggedSpaceCount
                || snapshot.chunkCount() != lastLoggedChunkCount
                || snapshot.skippedUnfinalized() != lastLoggedSkippedUnfinalized
                || snapshot.skippedRemoved() != lastLoggedSkippedRemoved
                || snapshot.skippedFailed() != lastLoggedSkippedFailed);
        boolean firstLog = lastLoggedSpaceCount == Integer.MIN_VALUE;
        boolean periodic = lastPeriodicSnapshotLogGameTime == Long.MIN_VALUE
                || snapshot.gameTime() - lastPeriodicSnapshotLogGameTime >= SNAPSHOT_LOG_INTERVAL_TICKS;

        if (!firstWithSpaces && !countsChanged && !firstLog && !periodic) {
            return false;
        }

        if (snapshot.hasSableSpaces()) {
            loggedFirstSnapshotWithSpaces = true;
        }
        lastLoggedSpaceCount = snapshot.spaces().size();
        lastLoggedChunkCount = snapshot.chunkCount();
        lastLoggedSkippedUnfinalized = snapshot.skippedUnfinalized();
        lastLoggedSkippedRemoved = snapshot.skippedRemoved();
        lastLoggedSkippedFailed = snapshot.skippedFailed();
        if (periodic) {
            lastPeriodicSnapshotLogGameTime = snapshot.gameTime();
        }
        return true;
    }

    private static boolean isSableSnapshotLoggingEnabled() {
        return DiagnosticRuntimeOverrides.sableSnapshotLoggingEnabled(SoundPhysicsMod.CONFIG);
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

}
