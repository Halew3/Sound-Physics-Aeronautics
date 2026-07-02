package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecordDiagnosticsTest {

    @AfterEach
    void reset() {
        RecordDiagnostics.reset();
    }

    @Test
    void recordStartAndMovingCountersAreSeparate() {
        RecordDiagnostics.reset();

        RecordDiagnostics.observeSource(7, Vec3.ZERO, record(true));
        RecordDiagnostics.observeSource(7, new Vec3(1.0D, 2.0D, 3.0D), record(false));

        String status = RecordDiagnostics.statusText();
        assertTrue(status.contains("startEvents=1"));
        assertTrue(status.contains("movingUpdates=1"));
    }

    @Test
    void resetClearsRecordSpecificCounters() {
        RecordDiagnostics.observeSource(7, Vec3.ZERO, record(true));

        RecordDiagnostics.reset();

        String status = RecordDiagnostics.statusText();
        assertTrue(status.contains("startEvents=0"));
        assertTrue(status.contains("trackedSources=0"));
    }

    @Test
    void movingUpdateAfterResetExplainsAlreadyActiveRecord() {
        RecordDiagnostics.reset();

        RecordDiagnostics.observeSource(7, Vec3.ZERO, record(false));

        String status = RecordDiagnostics.statusText();
        assertTrue(status.contains("staleAfterReset=1"));
        assertTrue(status.contains("record source already active; no new start event seen after reset; moving updates observed"));
    }

    private SoundPhysicsSoundPolicy.SoundContext record(boolean startEvent) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.cat"),
                SoundSource.RECORDS,
                "example.RecordSound",
                false,
                false,
                true,
                startEvent,
                false
        );
    }

}
