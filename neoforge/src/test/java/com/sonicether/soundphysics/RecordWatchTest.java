package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecordWatchTest {

    @AfterEach
    void reset() {
        RecordDiagnostics.reset();
    }

    @Test
    void watchEmitsFinalSummaryAfterWindow() {
        RecordDiagnostics.startWatchSeconds(1);
        RecordDiagnostics.observeSource(8, Vec3.ZERO, record(false));
        RecordDiagnostics.forceWatchExpiredForTests();

        List<String> lines = RecordDiagnostics.pollExpiredWatchSummaryLines();
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("records watch summary"));
        assertTrue(joined.contains("source=8"));
        assertTrue(joined.contains("alreadyActive=true"));
        assertTrue(RecordDiagnostics.statusText().contains("expiredWatches=1"));
    }

    @Test
    void modeSwitchMessageIsVisibleUntilNextSourceTick() {
        RecordDiagnostics.markModeSwitch("record_test_unsafe");

        assertTrue(RecordDiagnostics.statusText().contains("record diagnostics mode switch: record_test_unsafe"));
    }

    private SoundPhysicsSoundPolicy.SoundContext record(boolean startEvent) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.far"),
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
