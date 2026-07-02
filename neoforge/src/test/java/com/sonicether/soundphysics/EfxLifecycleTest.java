package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EfxLifecycleTest {

    @AfterEach
    void reset() {
        SoundPhysics.resetEfxLifecycleForTests();
    }

    @Test
    void destroyClearsExistingEfxStateAndRecordsCounters() {
        SoundPhysics.resetEfxLifecycleForTests();
        SoundPhysics.markEfxInitializedForTests("first init");

        SoundPhysics.destroyEfxForTests("audio reset");

        String status = SoundPhysics.audioStatusText();
        assertTrue(status.contains("efxInitialized=false"));
        assertTrue(status.contains("efxInitCount=1"));
        assertTrue(status.contains("efxDestroyCount=1"));
        assertTrue(status.contains("efxLastDestroyReason=audio reset"));
        assertTrue(status.contains("efxActiveAuxSlots=0"));
    }

    @Test
    void audioStatusShowsActiveSlotsAfterInit() {
        SoundPhysics.resetEfxLifecycleForTests();
        SoundPhysics.markEfxInitializedForTests("test init");

        String status = SoundPhysics.audioStatusText();

        assertTrue(status.contains("efxInitialized=true"));
        assertTrue(status.contains("efxInitCount=1"));
        assertTrue(status.contains("efxActiveAuxSlots=4"));
        assertTrue(status.contains("efxLastInitReason=test init"));
    }
}
