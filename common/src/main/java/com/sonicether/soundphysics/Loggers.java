package com.sonicether.soundphysics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL11;

public class Loggers {

    private static final String LOG_PREFIX = "Sound Physics - %s";

    private static final Logger DEBUG_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Debug"));
    private static final Logger PROFILING_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Profiling"));
    private static final Logger ENVIRONMENT_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Environment"));
    private static final Logger OCCLUSION_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Occlusion"));
    private static final Logger DOPPLER_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Doppler"));
    private static final Logger TRACE_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Trace"));
    private static final Logger SABLE_ACOUSTIC_LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "Sable"));
    private static final Logger LOGGER = LogManager.getLogger(String.format(LOG_PREFIX, "General"));

    public static void log(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    public static void logProfiling(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.PROFILING, message)) {
            return;
        }

        PROFILING_LOGGER.info(message, args);
    }

    public static void logDebug(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.DEBUG, message)) {
            return;
        }

        DEBUG_LOGGER.info(message, args);
    }

    public static void logOcclusion(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.OCCLUSION, message)) {
            return;
        }

        OCCLUSION_LOGGER.info(message, args);
    }

    public static void logEnvironment(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.ENVIRONMENT, message)) {
            return;
        }

        ENVIRONMENT_LOGGER.info(message, args);
    }

    public static void logDoppler(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.DOPPLER, message)) {
            return;
        }

        DOPPLER_LOGGER.info(message, args);
    }

    public static void logTrace(String message, Object... args) {
        if (!DiagnosticRuntimeOverrides.traceLoggingEnabled(SoundPhysicsMod.CONFIG)
                || !RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.TRACE, message)) {
            return;
        }

        TRACE_LOGGER.info(message, args);
    }

    public static void logSableAcoustic(String message, Object... args) {
        if (!RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.SABLE, message)) {
            return;
        }

        SABLE_ACOUSTIC_LOGGER.info(message, args);
    }

    public static void logALError(String errorMessage) {
        if (!DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(SoundPhysicsMod.CONFIG)) {
            return;
        }

        logALErrorAlways(errorMessage);
    }

    public static void logALErrorAlways(String errorMessage) {
        int error = AL11.alGetError();

        if (error == AL11.AL_NO_ERROR) {
            return;
        }

        String errorName = switch (error) {
            case AL11.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL11.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL11.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL11.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL11.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> Integer.toString(error);
        };

        if (error == AL11.AL_INVALID_NAME) {
            AudioSourceRecovery.recordOpenAlInvalidName(errorMessage);
        }
        LOGGER.error("{}: OpenAL error {}", errorMessage, errorName);
    }

}
