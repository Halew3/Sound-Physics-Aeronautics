package com.sonicether.soundphysics;

import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.utils.SoundRateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;

final class NeoForgeRuntimeDiagnostics {

    private static boolean warnedDebugFlags;

    private NeoForgeRuntimeDiagnostics() {
    }

    static void onClientTick(ClientTickEvent.Post event) {
        RuntimeLoggingController.tick();
        AudioSourceRecovery.tick();
        DopplerEngine.tick();

        var level = Minecraft.getInstance().level;
        if (level != null) {
            SoundRateManager.onClientTick(level);
        }

        sendAudioRecoveryNote();
        sendExpiredRecordWatchSummary();
        warnDebugFlagsOnce();
    }

    private static void sendAudioRecoveryNote() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String note = AudioSourceRecovery.consumePendingChatNote();
        if (note != null) {
            player.displayClientMessage(Component.literal(note), false);
        }
    }

    private static void sendExpiredRecordWatchSummary() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (String line : RecordDiagnostics.pollExpiredWatchSummaryLines()) {
            player.displayClientMessage(Component.literal(line), false);
        }
    }

    private static void warnDebugFlagsOnce() {
        if (warnedDebugFlags || SoundPhysicsMod.CONFIG == null) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!debugFlagsEnabled()) {
            return;
        }
        warnedDebugFlags = true;
        player.displayClientMessage(Component.literal("SPR Aeronautics debug visualization/logging is enabled. Use /spr_aero preset quiet before normal play."), false);
    }

    private static boolean debugFlagsEnabled() {
        var config = SoundPhysicsMod.CONFIG;
        return config.renderOcclusion.get()
                || config.renderSoundBounces.get()
                || config.debugLogging.get()
                || config.occlusionLogging.get()
                || config.environmentLogging.get()
                || config.performanceLogging.get()
                || config.dopplerDebugLogging.get()
                || config.soundPhysicsPolicyDebugLogging.get()
                || config.sableAcousticDebugLogging.get()
                || config.soundPhysicsTraceLogging.get();
    }

}
