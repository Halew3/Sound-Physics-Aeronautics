package com.sonicether.soundphysics;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

public final class DiagnosticRuntimeOverrides {

    private static volatile Mode mode = Mode.NORMAL;

    private DiagnosticRuntimeOverrides() {
    }

    public static void enableRootDebug() {
        mode = Mode.ROOT_DEBUG;
    }

    public static void enableDopplerDebug() {
        mode = Mode.DOPPLER_DEBUG;
    }

    public static void enableRecordTest() {
        mode = Mode.RECORD_TEST;
    }

    public static void enableRecordTestUnsafe() {
        mode = Mode.RECORD_TEST_UNSAFE;
    }

    public static void enablePropellerSafe() {
        mode = Mode.PROPELLER_SAFE;
    }

    public static void enablePropellerDebug() {
        mode = Mode.PROPELLER_DEBUG;
    }

    public static void clear() {
        mode = Mode.NORMAL;
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isRootDebug() {
        return mode == Mode.ROOT_DEBUG;
    }

    public static boolean dopplerDebugMode() {
        return mode == Mode.DOPPLER_DEBUG;
    }

    public static boolean recordTestMode() {
        return mode == Mode.RECORD_TEST || mode == Mode.RECORD_TEST_UNSAFE;
    }

    public static boolean recordTestUnsafeMode() {
        return mode == Mode.RECORD_TEST_UNSAFE;
    }

    public static boolean propellerSafeMode() {
        return mode == Mode.PROPELLER_SAFE;
    }

    public static boolean propellerDebugMode() {
        return mode == Mode.PROPELLER_DEBUG;
    }

    public static boolean soundPhysicsEnabled(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return true;
        }
        return config != null && config.enabled.get();
    }

    public static boolean traceLoggingEnabled(@Nullable SoundPhysicsConfig config) {
        return RuntimeLoggingController.traceLoggingEnabled(config);
    }

    public static boolean openAlErrorChecksEnabled(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug() || dopplerDebugMode() || propellerDebugMode()) {
            return true;
        }
        return RuntimeLoggingController.traceLoggingEnabled(config) || (config != null && config.openAlErrorChecks.get());
    }

    public static boolean forceRootAcousticProvider(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return true;
        }
        return config != null && config.forceRootAcousticProvider.get();
    }

    public static boolean sableAcousticsEnabled(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return false;
        }
        return config == null || config.sableAcousticsEnabled.get();
    }

    public static boolean sableSnapshotLoggingEnabled(@Nullable SoundPhysicsConfig config) {
        return RuntimeLoggingController.verboseActive() || (!isRootDebug() && config != null && config.sableAcousticDebugLogging.get());
    }

    public static boolean dopplerEnabled(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return false;
        }
        if (dopplerDebugMode() || propellerDebugMode()) {
            return config != null && config.enabled.get();
        }
        return config != null && config.enabled.get() && config.dopplerEnabled.get();
    }

    public static double effectiveDopplerStrength(@Nullable SoundPhysicsConfig config) {
        if (dopplerDebugMode() || propellerDebugMode()) {
            return 3.0D;
        }
        return config == null ? 1.0D : config.dopplerStrength.get();
    }

    public static double effectiveDopplerMinPitchMultiplier(@Nullable SoundPhysicsConfig config) {
        if (dopplerDebugMode() || propellerDebugMode()) {
            return 0.5D;
        }
        return config == null ? 0.5D : config.dopplerMinPitchMultiplier.get();
    }

    public static double effectiveDopplerMaxPitchMultiplier(@Nullable SoundPhysicsConfig config) {
        if (dopplerDebugMode() || propellerDebugMode()) {
            return 2.0D;
        }
        return config == null ? 2.0D : config.dopplerMaxPitchMultiplier.get();
    }

    public static int effectiveDopplerSmoothingTimeMs(@Nullable SoundPhysicsConfig config) {
        if (dopplerDebugMode() || propellerDebugMode()) {
            return 50;
        }
        return config == null ? 100 : config.dopplerSmoothingTimeMs.get();
    }

    public static boolean renderOcclusion(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return true;
        }
        return config != null && config.renderOcclusion.get();
    }

    public static boolean renderSoundBounces(@Nullable SoundPhysicsConfig config) {
        if (isRootDebug()) {
            return true;
        }
        return config != null && config.renderSoundBounces.get();
    }

    public enum Mode {
        NORMAL("normal"),
        ROOT_DEBUG("root_debug"),
        DOPPLER_DEBUG("doppler_debug"),
        RECORD_TEST("record_test"),
        RECORD_TEST_UNSAFE("record_test_unsafe"),
        PROPELLER_SAFE("propeller_safe"),
        PROPELLER_DEBUG("propeller_debug");

        private final String commandName;

        Mode(String commandName) {
            this.commandName = commandName;
        }

        public String commandName() {
            return commandName;
        }
    }

}
