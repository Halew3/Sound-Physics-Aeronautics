package com.sonicether.soundphysics;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.Command;
import com.sonicether.soundphysics.debug.RaycastRenderer;
import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.integration.sable.SableAcousticIntegration;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

final class NeoForgeDiagnosticsCommand {

    private NeoForgeDiagnosticsCommand() {
    }

    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("spr_aero")
                        .then(Commands.literal("diagnostics")
                                .executes(context -> dumpShort(context.getSource()))
                                .then(Commands.literal("short")
                                        .executes(context -> dumpShort(context.getSource())))
                                .then(Commands.literal("full")
                                        .executes(context -> dumpFull(context.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(context -> reset(context.getSource()))))
                        .then(Commands.literal("config_path")
                                .executes(context -> configPath(context.getSource())))
                        .then(Commands.literal("config_dump")
                                .executes(context -> configDump(context.getSource())))
                        .then(Commands.literal("config_export_defaults")
                                .executes(context -> configExportDefaults(context.getSource())))
                        .then(Commands.literal("perf")
                                .executes(context -> perf(context.getSource()))
                                .then(Commands.literal("reset")
                                        .executes(context -> perfReset(context.getSource()))))
                        .then(Commands.literal("logging")
                                .then(Commands.literal("status")
                                        .executes(context -> loggingStatus(context.getSource())))
                                .then(Commands.literal("quiet")
                                        .executes(context -> loggingQuiet(context.getSource())))
                                .then(Commands.literal("capture")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                .executes(context -> loggingCapture(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                                .then(Commands.literal("trace")
                                        .then(Commands.literal("on")
                                                .executes(context -> loggingTrace(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> loggingTrace(context.getSource(), false)))))
                        .then(Commands.literal("rays")
                                .then(Commands.literal("off")
                                        .executes(context -> raysMode(context.getSource(), RaycastRenderer.RenderMode.OFF)))
                                .then(Commands.literal("occlusion")
                                        .executes(context -> raysMode(context.getSource(), RaycastRenderer.RenderMode.OCCLUSION)))
                                .then(Commands.literal("bounce")
                                        .executes(context -> raysMode(context.getSource(), RaycastRenderer.RenderMode.BOUNCE)))
                                .then(Commands.literal("both")
                                        .executes(context -> raysMode(context.getSource(), RaycastRenderer.RenderMode.BOTH)))
                                .then(Commands.literal("clear")
                                        .executes(context -> raysClear(context.getSource())))
                                .then(Commands.literal("status")
                                        .executes(context -> raysStatus(context.getSource())))
                                .then(Commands.literal("filter")
                                        .then(Commands.literal("propeller")
                                                .executes(context -> raysFilter(context.getSource(), RaycastRenderer.RayFilter.PROPELLER)))
                                        .then(Commands.literal("records")
                                                .executes(context -> raysFilter(context.getSource(), RaycastRenderer.RayFilter.RECORDS)))
                                        .then(Commands.literal("latest")
                                                .executes(context -> raysFilter(context.getSource(), RaycastRenderer.RayFilter.LATEST)))
                                        .then(Commands.literal("none")
                                                .executes(context -> raysFilter(context.getSource(), RaycastRenderer.RayFilter.NONE)))))
                        .then(Commands.literal("doppler")
                                .then(Commands.literal("status")
                                        .executes(context -> dopplerStatus(context.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(context -> dopplerReset(context.getSource())))
                                .then(Commands.literal("sources")
                                        .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.all(12)))
                                        .then(Commands.literal("propeller")
                                                .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.propeller(12))))
                                        .then(Commands.literal("records")
                                                .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.records(12))))
                                        .then(Commands.literal("sound")
                                                .then(Commands.argument("substring", StringArgumentType.word())
                                                        .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.sound(StringArgumentType.getString(context, "substring"), 12)))))
                                        .then(Commands.literal("latest")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                                        .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.latest(IntegerArgumentType.getInteger(context, "count"))))))
                                        .then(Commands.literal("moving")
                                                .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.moving(12))))
                                        .then(Commands.literal("unreliable")
                                                .executes(context -> dopplerSources(context.getSource(), DopplerEngine.SourceQuery.unreliable(12)))))
                                .then(Commands.literal("simulate")
                                        .then(Commands.literal("approach")
                                                .executes(context -> dopplerSimulate(context.getSource(), true)))
                                        .then(Commands.literal("recede")
                                                .executes(context -> dopplerSimulate(context.getSource(), false))))
                                .then(Commands.literal("force_multiplier")
                                        .then(Commands.literal("propeller")
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.05D, 4.0D))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                                .executes(context -> dopplerForcePropeller(context.getSource(), DoubleArgumentType.getDouble(context, "multiplier"), IntegerArgumentType.getInteger(context, "seconds"))))))
                                        .then(Commands.literal("propeller_all")
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.05D, 4.0D))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                                .executes(context -> dopplerForcePropellerAll(context.getSource(), DoubleArgumentType.getDouble(context, "multiplier"), IntegerArgumentType.getInteger(context, "seconds"))))))
                                        .then(Commands.literal("latest")
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.05D, 4.0D))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                                .executes(context -> dopplerForceLatest(context.getSource(), DoubleArgumentType.getDouble(context, "multiplier"), IntegerArgumentType.getInteger(context, "seconds"))))))
                                        .then(Commands.literal("source")
                                                .then(Commands.argument("sourceId", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.05D, 4.0D))
                                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                                        .executes(context -> dopplerForceSource(context.getSource(), IntegerArgumentType.getInteger(context, "sourceId"), DoubleArgumentType.getDouble(context, "multiplier"), IntegerArgumentType.getInteger(context, "seconds"))))))))
                                .then(Commands.literal("force_status")
                                        .executes(context -> dopplerForceStatus(context.getSource())))
                                .then(Commands.literal("audible_test")
                                        .then(Commands.literal("on")
                                                .executes(context -> dopplerAudibleTest(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> dopplerAudibleTest(context.getSource(), false))))
                                .then(Commands.literal("exaggerate_test")
                                        .then(Commands.literal("on")
                                                .executes(context -> dopplerExaggerateOn(context.getSource())))
                                        .then(Commands.literal("off")
                                                .executes(context -> dopplerExaggerateOff(context.getSource())))))
                        .then(Commands.literal("propeller")
                                .then(Commands.literal("status")
                                        .executes(context -> propellerStatus(context.getSource())))
                                .then(Commands.literal("sources")
                                        .executes(context -> propellerSources(context.getSource())))
                                .then(Commands.literal("range")
                                        .executes(context -> propellerRange(context.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(context -> propellerReset(context.getSource()))))
                        .then(Commands.literal("records")
                                .then(Commands.literal("status")
                                        .executes(context -> recordsStatus(context.getSource())))
                                .then(Commands.literal("sources")
                                        .executes(context -> recordsSources(context.getSource())))
                                .then(Commands.literal("watch")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                .executes(context -> recordsWatch(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"), false))
                                                .then(Commands.literal("verbose")
                                                        .executes(context -> recordsWatch(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"), true)))))
                                .then(Commands.literal("reset")
                                        .executes(context -> recordsReset(context.getSource()))))
                        .then(Commands.literal("audio")
                                .then(Commands.literal("status")
                                        .executes(context -> audioStatus(context.getSource())))
                                .then(Commands.literal("recover")
                                        .executes(context -> audioRecover(context.getSource())))
                                .then(Commands.literal("sources")
                                        .executes(context -> audioSources(context.getSource()))))
                        .then(Commands.literal("preset")
                                .then(Commands.literal("quiet")
                                        .executes(context -> presetQuiet(context.getSource())))
                                .then(Commands.literal("normal")
                                        .executes(context -> presetNormal(context.getSource())))
                                .then(Commands.literal("propeller_test")
                                        .executes(context -> presetPropellerTest(context.getSource())))
                                .then(Commands.literal("record_test")
                                        .executes(context -> presetRecordTest(context.getSource())))
                                .then(Commands.literal("doppler_test")
                                        .executes(context -> presetDopplerTest(context.getSource()))))
                        .then(Commands.literal("reset_debug_flags")
                                .executes(context -> resetDebugFlags(context.getSource())))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("root_debug")
                                        .executes(context -> rootDebugMode(context.getSource())))
                                .then(Commands.literal("propeller_safe")
                                        .executes(context -> propellerSafeMode(context.getSource())))
                                .then(Commands.literal("propeller_debug")
                                        .executes(context -> propellerDebugMode(context.getSource())))
                                .then(Commands.literal("record_test")
                                        .executes(context -> recordTestMode(context.getSource())))
                                .then(Commands.literal("record_test_unsafe")
                                        .executes(context -> recordTestUnsafeMode(context.getSource())))
                                .then(Commands.literal("normal")
                                        .executes(context -> normalMode(context.getSource()))))
        );
    }

    private static int dumpShort(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics diagnostics short");
        send(source, "Core: " + SoundPhysicsTrace.diagnosticsSummaryText());
        send(source, "Policy: " + SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText());
        send(source, "Doppler: " + DopplerEngine.diagnosticsSummaryText());
        send(source, "Perf: " + SoundPhysicsPerfDiagnostics.shortSummaryText());
        send(source, "Sable: " + sableDiagnosticsText());
        Loggers.log(
                "SPR Aeronautics diagnostics short | Core: {} | Policy: {} | Doppler: {} | Perf: {} | Sable: {}",
                SoundPhysicsTrace.diagnosticsSummaryText(),
                SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText(),
                DopplerEngine.diagnosticsSummaryText(),
                SoundPhysicsPerfDiagnostics.shortSummaryText(),
                sableDiagnosticsText()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int dumpFull(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics diagnostics full");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        send(source, "Core: " + SoundPhysicsTrace.diagnosticsSummaryText());
        send(source, "Policy: " + SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText());
        send(source, "Ray renderer: " + RaycastRenderer.diagnosticsSummaryText());
        send(source, "Doppler: " + DopplerEngine.debugStatusText());
        sendLines(source, DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.all(8)));
        send(source, "Sable: " + sableDiagnosticsText());
        send(source, "Perf: " + SoundPhysicsPerfDiagnostics.summaryText());
        Loggers.log(
                "SPR Aeronautics diagnostics full | Core: {} | Policy: {} | Ray renderer: {} | Doppler: {} | Sable: {} | Perf: {}",
                SoundPhysicsTrace.diagnosticsSummaryText(),
                SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText(),
                RaycastRenderer.diagnosticsSummaryText(),
                DopplerEngine.debugStatusText(),
                sableDiagnosticsText(),
                SoundPhysicsPerfDiagnostics.summaryText()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(net.minecraft.commands.CommandSourceStack source) {
        SoundPhysicsTrace.reset();
        SoundProcessingDeduper.reset();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsPerfDiagnostics.reset();
        RaycastRenderer.resetDiagnostics();
        DopplerEngine.resetDiagnostics();
        PropellerLongRangeAudio.resetDiagnostics();
        RuntimeLoggingController.resetDiagnostics();
        RecordDiagnostics.reset();
        if (ModList.get().isLoaded("sable")) {
            SableAcousticIntegration.resetDiagnostics();
        }
        send(source, "SPR Aeronautics diagnostics reset");
        Loggers.log("SPR Aeronautics diagnostics reset");
        return Command.SINGLE_SUCCESS;
    }

    private static int configPath(net.minecraft.commands.CommandSourceStack source) {
        for (String line : ConfigDiagnostics.configPathLines(FMLLoader.getGamePath(), FMLLoader.getGamePath().resolve("config"))) {
            send(source, line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int configDump(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics config dump");
        for (String line : ConfigDiagnostics.configDumpLines(FMLLoader.getGamePath(), FMLLoader.getGamePath().resolve("config"))) {
            send(source, line);
        }
        Loggers.log("SPR Aeronautics config dump | {}", ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int configExportDefaults(net.minecraft.commands.CommandSourceStack source) {
        try {
            Path path = ConfigDiagnostics.exportExample(FMLLoader.getGamePath().resolve("config"));
            send(source, "SPR Aeronautics config example exported: " + path.toAbsolutePath());
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            send(source, "SPR Aeronautics config example export failed: " + exception.getMessage());
            Loggers.warn("SPR Aeronautics config example export failed: {}", exception.getMessage());
            return 0;
        }
    }

    private static int perf(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics perf: " + SoundPhysicsPerfDiagnostics.summaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int perfReset(net.minecraft.commands.CommandSourceStack source) {
        SoundPhysicsPerfDiagnostics.reset();
        send(source, "SPR Aeronautics perf diagnostics reset");
        return Command.SINGLE_SUCCESS;
    }

    private static int loggingStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics logging: " + RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        return Command.SINGLE_SUCCESS;
    }

    private static int loggingQuiet(net.minecraft.commands.CommandSourceStack source) {
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        send(source, "SPR Aeronautics logging quiet: verbose logging disabled; chat diagnostics remain available.");
        send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        return Command.SINGLE_SUCCESS;
    }

    private static int loggingCapture(net.minecraft.commands.CommandSourceStack source, int seconds) {
        int clamped = RuntimeLoggingController.startCaptureSeconds(seconds);
        send(source, "SPR Aeronautics logging capture enabled for " + clamped + " seconds.");
        send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        return Command.SINGLE_SUCCESS;
    }

    private static int loggingTrace(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        if (enabled) {
            RuntimeLoggingController.enableTrace();
            send(source, "SPR Aeronautics trace logging enabled.");
        } else {
            RuntimeLoggingController.disableTrace();
            send(source, "SPR Aeronautics trace logging disabled.");
        }
        send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        return Command.SINGLE_SUCCESS;
    }

    private static int raysMode(net.minecraft.commands.CommandSourceStack source, RaycastRenderer.RenderMode mode) {
        RaycastRenderer.setRenderMode(mode);
        send(source, "SPR Aeronautics rays mode set to " + mode.name().toLowerCase(java.util.Locale.ROOT));
        send(source, RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int raysFilter(net.minecraft.commands.CommandSourceStack source, RaycastRenderer.RayFilter filter) {
        RaycastRenderer.setRayFilter(filter);
        send(source, "SPR Aeronautics rays filter set to " + filter.name().toLowerCase(java.util.Locale.ROOT));
        send(source, RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int raysClear(net.minecraft.commands.CommandSourceStack source) {
        RaycastRenderer.clearRays();
        send(source, "SPR Aeronautics rays cleared");
        send(source, RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int raysStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics rays: " + RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics Doppler status: " + DopplerEngine.debugStatusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerReset(net.minecraft.commands.CommandSourceStack source) {
        DopplerEngine.resetDiagnostics();
        send(source, "SPR Aeronautics Doppler diagnostics reset");
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerSources(net.minecraft.commands.CommandSourceStack source, DopplerEngine.SourceQuery query) {
        sendLines(source, DopplerEngine.sourcesDiagnosticsLines(query));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerSimulate(net.minecraft.commands.CommandSourceStack source, boolean approach) {
        send(source, "SPR Aeronautics Doppler simulate: Math-only simulation. This does not play or alter audio. " + DopplerEngine.simulateText(approach));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerForcePropeller(net.minecraft.commands.CommandSourceStack source, double multiplier, int seconds) {
        send(source, DopplerEngine.forceLatestPropellerMultiplierText(multiplier, seconds));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerForcePropellerAll(net.minecraft.commands.CommandSourceStack source, double multiplier, int seconds) {
        send(source, DopplerEngine.forceAllPropellersMultiplierText(multiplier, seconds));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerForceLatest(net.minecraft.commands.CommandSourceStack source, double multiplier, int seconds) {
        send(source, DopplerEngine.forceLatestMultiplierText(multiplier, seconds));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerForceSource(net.minecraft.commands.CommandSourceStack source, int sourceId, double multiplier, int seconds) {
        send(source, DopplerEngine.forceSourceMultiplierText(sourceId, multiplier, seconds));
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerForceStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, DopplerEngine.forceStatusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerAudibleTest(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        send(source, enabled ? DopplerEngine.audibleTestOnText() : DopplerEngine.audibleTestOffText());
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerExaggerateOn(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enableDopplerDebug();
        applyRuntimeMode();
        send(source, "SPR Aeronautics Doppler exaggerate_test enabled");
        send(source, DopplerEngine.debugStatusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int dopplerExaggerateOff(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.clear();
        applyRuntimeMode();
        send(source, "SPR Aeronautics Doppler exaggerate_test disabled");
        send(source, DopplerEngine.debugStatusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int rootDebugMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enableRootDebug();
        applyRuntimeMode();
        send(source, "SPR Aeronautics mode set to root_debug");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        Loggers.log("SPR Aeronautics mode root_debug | {}", ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int normalMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.clear();
        applyRuntimeMode();
        send(source, "SPR Aeronautics mode set to normal");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        Loggers.log("SPR Aeronautics mode normal | {}", ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerSafeMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enablePropellerSafe();
        applyRuntimeMode();
        send(source, "SPR Aeronautics mode set to propeller_safe");
        send(source, "propeller_safe bypasses SPR acoustic/Doppler processing for known propellers so Aeronautics audio remains vanilla/audible. Expected: no muffling/reverb/Doppler on propellers.");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerDebugMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        applyRuntimeMode();
        send(source, "SPR Aeronautics mode set to propeller_debug");
        send(source, "propeller_debug enables SPR acoustic processing for known propellers with safety exemptions and verbose diagnostics. Expected: muffling/reverb should work; Doppler remains diagnostic/experimental.");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int recordTestMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enableRecordTest();
        applyRuntimeMode();
        RecordDiagnostics.markModeSwitch(DiagnosticRuntimeOverrides.mode().commandName());
        send(source, "SPR Aeronautics mode set to record_test");
        send(source, "Records are temporarily allowed for acoustic/Doppler diagnostics. Record Doppler works in record_test/unsafe. Record acoustic muffling requires periodic record acoustic updates and is experimental. Use /spr_aero mode normal to clear.");
        send(source, RecordDiagnostics.statusText());
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        Loggers.log("SPR Aeronautics mode record_test | {}", ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int recordTestUnsafeMode(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.enableRecordTestUnsafe();
        applyRuntimeMode();
        RecordDiagnostics.markModeSwitch(DiagnosticRuntimeOverrides.mode().commandName());
        send(source, "SPR Aeronautics mode set to record_test_unsafe");
        send(source, "Records with no attenuation are temporarily allowed for diagnostics if they have a valid world position. Record Doppler works in record_test/unsafe. Record acoustic muffling requires periodic record acoustic updates and is experimental. Use /spr_aero mode normal to clear.");
        send(source, RecordDiagnostics.statusText());
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        Loggers.log("SPR Aeronautics mode record_test_unsafe | {}", ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics propeller status");
        send(source, "Mode: " + DiagnosticRuntimeOverrides.mode().commandName());
        if (DiagnosticRuntimeOverrides.propellerSafeMode()) {
            send(source, "safe mode: acoustic bypass active, Doppler bypass active");
        } else {
            send(source, "safe mode: inactive");
        }
        send(source, SoundPhysicsPolicyDiagnostics.latestPropellerObservationText());
        send(source, "Policy: " + SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText());
        send(source, "Long range: " + PropellerLongRangeAudio.diagnosticsSummaryText());
        send(source, "Doppler: " + DopplerEngine.debugStatusText());
        sendLines(source, PropellerLongRangeAudio.rangeDiagnosticsLines(12));
        sendLines(source, DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerSources(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics propeller sources");
        sendLines(source, PropellerLongRangeAudio.rangeDiagnosticsLines(20));
        sendLines(source, DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(20)));
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerRange(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics propeller range");
        sendLines(source, PropellerLongRangeAudio.rangeDiagnosticsLines(20));
        return Command.SINGLE_SUCCESS;
    }

    private static int propellerReset(net.minecraft.commands.CommandSourceStack source) {
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsPerfDiagnostics.reset();
        DopplerEngine.resetDiagnostics();
        PropellerLongRangeAudio.resetDiagnostics();
        send(source, "SPR Aeronautics propeller diagnostics reset");
        return Command.SINGLE_SUCCESS;
    }

    private static int recordsStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics records status");
        send(source, "Mode: " + DiagnosticRuntimeOverrides.mode().commandName());
        send(source, RecordDiagnostics.statusText());
        sendLines(source, RecordDiagnostics.sourceLines(8));
        return Command.SINGLE_SUCCESS;
    }

    private static int recordsSources(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics records sources");
        sendLines(source, RecordDiagnostics.sourceLines(20));
        return Command.SINGLE_SUCCESS;
    }

    private static int recordsWatch(net.minecraft.commands.CommandSourceStack source, int seconds, boolean verbose) {
        int clamped = RecordDiagnostics.startWatchSeconds(seconds);
        if (verbose) {
            RuntimeLoggingController.startCaptureSeconds(clamped);
            send(source, "SPR Aeronautics records watch verbose enabled for " + clamped + " seconds. Runtime capture is active; a final records summary will print automatically.");
            send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        } else {
            send(source, "SPR Aeronautics records watch enabled for " + clamped + " seconds. A final records summary will print automatically. Runtime capture remains off; use /spr_aero records watch " + clamped + " verbose or /spr_aero logging capture " + clamped + " for verbose logs.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int recordsReset(net.minecraft.commands.CommandSourceStack source) {
        RecordDiagnostics.reset();
        send(source, "SPR Aeronautics record diagnostics reset");
        send(source, "If a record is already playing, no new start event may appear; moving updates after reset will be reported explicitly.");
        return Command.SINGLE_SUCCESS;
    }

    private static int audioStatus(net.minecraft.commands.CommandSourceStack source) {
        send(source, "SPR Aeronautics audio: " + SoundPhysics.audioStatusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int audioRecover(net.minecraft.commands.CommandSourceStack source) {
        send(source, AudioSourceRecovery.recover("manual /spr_aero audio recover", false));
        send(source, "Does not delete config.");
        send(source, AudioSourceRecovery.sourcesText());
        return Command.SINGLE_SUCCESS;
    }

    private static int audioSources(net.minecraft.commands.CommandSourceStack source) {
        send(source, AudioSourceRecovery.sourcesText());
        return Command.SINGLE_SUCCESS;
    }

    private static int resetDebugFlags(net.minecraft.commands.CommandSourceStack source) {
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.OFF);
        RaycastRenderer.clearRays();
        send(source, "SPR Aeronautics debug flags reset: logging quiet, rays off, rays cleared.");
        send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        send(source, RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int presetQuiet(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.clear();
        applyRuntimeMode();
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.OFF);
        RaycastRenderer.clearRays();
        resetDiagnosticsOnly();
        send(source, "SPR Aeronautics preset quiet applied: mode normal, logging quiet, rays off, rays cleared, diagnostics reset.");
        send(source, RuntimeLoggingController.statusText(SoundPhysicsMod.CONFIG));
        send(source, RaycastRenderer.diagnosticsSummaryText());
        return Command.SINGLE_SUCCESS;
    }

    private static int presetNormal(net.minecraft.commands.CommandSourceStack source) {
        DiagnosticRuntimeOverrides.clear();
        applyRuntimeMode();
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.OFF);
        RaycastRenderer.clearRays();
        send(source, "SPR Aeronautics preset normal applied: mode normal, logging quiet, rays off, rays cleared.");
        send(source, "Config: " + ConfigDiagnostics.criticalValuesSummary());
        return Command.SINGLE_SUCCESS;
    }

    private static int presetPropellerTest(net.minecraft.commands.CommandSourceStack source) {
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        RaycastRenderer.clearRays();
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.PROPELLER);
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        applyRuntimeMode();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsPerfDiagnostics.reset();
        DopplerEngine.resetDiagnostics();
        send(source, "SPR Aeronautics preset propeller_test applied: logging quiet, ray filter propeller, mode propeller_debug, propeller/Doppler diagnostics reset.");
        send(source, "Next: /spr_aero propeller sources");
        send(source, "Next: /spr_aero doppler force_multiplier propeller 1.5 5");
        send(source, "Next: /spr_aero doppler sources propeller");
        return Command.SINGLE_SUCCESS;
    }

    private static int presetRecordTest(net.minecraft.commands.CommandSourceStack source) {
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        RaycastRenderer.clearRays();
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.RECORDS);
        RecordDiagnostics.reset();
        DiagnosticRuntimeOverrides.enableRecordTest();
        applyRuntimeMode();
        RecordDiagnostics.markModeSwitch(DiagnosticRuntimeOverrides.mode().commandName());
        int watchSeconds = RecordDiagnostics.startWatchSeconds(10);
        send(source, "SPR Aeronautics preset record_test applied: logging quiet, ray filter records, records reset, mode record_test, records watch 10s. Runtime capture remains off.");
        send(source, "Record Doppler works in record_test/unsafe. Record acoustic muffling requires periodic record acoustic updates and is experimental.");
        send(source, RecordDiagnostics.statusText());
        return Command.SINGLE_SUCCESS;
    }

    private static int presetDopplerTest(net.minecraft.commands.CommandSourceStack source) {
        RuntimeLoggingController.quiet(SoundPhysicsMod.CONFIG);
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        applyRuntimeMode();
        DopplerEngine.resetDiagnostics();
        send(source, "SPR Aeronautics preset doppler_test applied: logging quiet, mode propeller_debug, Doppler diagnostics reset.");
        send(source, "SPR Aeronautics Doppler simulate: Math-only simulation. This does not play or alter audio. " + DopplerEngine.simulateText(true));
        send(source, "SPR Aeronautics Doppler simulate: Math-only simulation. This does not play or alter audio. " + DopplerEngine.simulateText(false));
        send(source, "Next: /spr_aero doppler force_multiplier propeller 1.5 5");
        return Command.SINGLE_SUCCESS;
    }

    private static void resetDiagnosticsOnly() {
        SoundPhysicsTrace.reset();
        SoundProcessingDeduper.reset();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsPerfDiagnostics.reset();
        RaycastRenderer.resetDiagnostics();
        DopplerEngine.resetDiagnostics();
        PropellerLongRangeAudio.resetDiagnostics();
        RuntimeLoggingController.resetDiagnostics();
        RecordDiagnostics.reset();
        if (ModList.get().isLoaded("sable")) {
            SableAcousticIntegration.resetDiagnostics();
        }
    }

    private static void applyRuntimeMode() {
        DopplerEngine.onConfigReload();
        PropellerLongRangeAudio.onConfigReload();
        if (ModList.get().isLoaded("sable")) {
            SableAcousticIntegration.applyConfig();
        }
    }

    private static String sableDiagnosticsText() {
        if (!ModList.get().isLoaded("sable")) {
            return "Sable not loaded";
        }
        return SableAcousticIntegration.diagnosticsSummaryText();
    }

    private static void send(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static void sendLines(net.minecraft.commands.CommandSourceStack source, Iterable<String> lines) {
        for (String line : lines) {
            send(source, line);
        }
    }

}
