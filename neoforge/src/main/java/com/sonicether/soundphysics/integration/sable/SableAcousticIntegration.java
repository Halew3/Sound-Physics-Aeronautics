package com.sonicether.soundphysics.integration.sable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.acoustic.AcousticScenes;
import com.sonicether.soundphysics.doppler.DopplerKinematics;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SableAcousticIntegration {

    private static final SableAcousticWorldProvider SABLE_WORLD_PROVIDER = new SableAcousticWorldProvider();
    private static final SableDopplerKinematicsProvider SABLE_KINEMATICS_PROVIDER = new SableDopplerKinematicsProvider();
    private static boolean initialized;

    private SableAcousticIntegration() {
    }

    public static void init() {
        if (initialized) {
            applyConfig();
            return;
        }

        try {
            NeoForge.EVENT_BUS.<ClientTickEvent.Post>addListener(SableAcousticSnapshotManager::onClientTick);
            NeoForge.EVENT_BUS.<ClientPlayerNetworkEvent.LoggingOut>addListener(SableAcousticSnapshotManager::onLoggingOut);
            initialized = true;
            applyConfig();
        } catch (Throwable throwable) {
            AcousticScenes.setProvider(null);
            DopplerKinematics.setProvider(null);
            Loggers.warn("Failed to initialize Sable acoustic world provider; using root acoustic world: {}", throwable.getMessage());
        }
    }

    public static void applyConfig() {
        if (!isSableAcousticsEnabled()) {
            AcousticScenes.setProvider(null);
            DopplerKinematics.setProvider(null);
            SableAcousticSnapshotManager.clear();
            Loggers.log("Sable acoustics disabled by config; using root acoustic world provider");
            return;
        }

        AcousticScenes.setProvider(SABLE_WORLD_PROVIDER);
        DopplerKinematics.setProvider(SABLE_KINEMATICS_PROVIDER);
        Loggers.log("Using Sable acoustic world provider");
    }

    public static String diagnosticsSummaryText() {
        try {
            return SableAcousticSnapshotManager.diagnosticsSummaryText();
        } catch (Throwable throwable) {
            return "Sable diagnostics unavailable: " + throwable.getMessage();
        }
    }

    public static void resetDiagnostics() {
        try {
            SableAcousticSnapshotManager.resetDiagnostics();
        } catch (Throwable throwable) {
            Loggers.warn("Failed to reset Sable diagnostics: {}", throwable.getMessage());
        }
    }

    private static boolean isSableAcousticsEnabled() {
        return DiagnosticRuntimeOverrides.sableAcousticsEnabled(SoundPhysicsMod.CONFIG);
    }

}
