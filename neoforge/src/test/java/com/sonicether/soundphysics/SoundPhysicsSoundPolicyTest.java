package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.doppler.DopplerSoundPolicy;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundPhysicsSoundPolicyTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsPolicyDiagnostics.reset();
    }

    @Test
    void crosswindWindSkippedByDefaultButHowlFallsThrough() {
        SoundPhysicsConfig config = config();

        SoundPhysicsSoundPolicy.Decision light = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("crosswind", "weather.wind.light", SoundSource.WEATHER)
        );
        SoundPhysicsSoundPolicy.Decision howl = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("crosswind", "weather.wind.howl", SoundSource.WEATHER)
        );

        assertFalse(light.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.CROSSWIND_WIND, light.reason());
        assertTrue(howl.apply());
    }

    @Test
    void recordsAndMusicAreSkippedByDefault() {
        SoundPhysicsConfig config = config();

        assertFalse(SoundPhysicsSoundPolicy.evaluateAcoustic(config, context("minecraft", "music_disc.cat", SoundSource.RECORDS)).apply());
        assertFalse(SoundPhysicsSoundPolicy.evaluateDoppler(config, context("minecraft", "music.menu", SoundSource.MUSIC)).apply());
    }

    @Test
    void blockSoundsAreAllowed() {
        SoundPhysicsConfig config = config();

        assertTrue(SoundPhysicsSoundPolicy.evaluateAcoustic(config, context("minecraft", "block.note_block.pling", SoundSource.BLOCKS)).apply());
        assertTrue(SoundPhysicsSoundPolicy.evaluateDoppler(config, context("minecraft", "block.note_block.pling", SoundSource.BLOCKS)).apply());
    }

    @Test
    void knownPropellerAmbientMachineryIsAllowedWithoutGlobalAmbient() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext propeller = propeller(false, false, true);

        assertTrue(SoundPhysicsSoundPolicy.isKnownPropeller(propeller));
        assertTrue(SoundPhysicsSoundPolicy.isKnownPropeller(new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("example", "other"),
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                true,
                true
        )));
        assertTrue(SoundPhysicsSoundPolicy.evaluateAcoustic(config, propeller).apply());
        assertTrue(SoundPhysicsSoundPolicy.evaluateDoppler(config, propeller).apply());
    }

    @Test
    void sableDelegatedAeronauticsPropellerDopplerIsAllowedByDefaultPropellerConfig() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToAeronauticsPropellers.set(true);
        config.dopplerApplyToSableDelegatedSounds.set(false);
        SoundPhysicsSoundPolicy.SoundContext propeller = sableDelegatedAeronauticsPropeller();

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateDoppler(config, propeller);

        assertTrue(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_ALLOWED, decision.reason());
    }

    @Test
    void sableDelegatedAeronauticsPropellerDopplerRequiresPropellerOrDiagnosticPolicy() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToAeronauticsPropellers.set(false);
        config.dopplerApplyToPositionalAmbientMachinery.set(false);
        config.dopplerApplyToSableDelegatedSounds.set(false);

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateDoppler(
                config,
                sableDelegatedAeronauticsPropeller()
        );

        assertFalse(decision.apply());
    }

    @Test
    void genericSableDelegatedNonPropellerDopplerIsSkippedByDefault() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToSableDelegatedSounds.set(false);

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateDoppler(
                config,
                new SoundPhysicsSoundPolicy.SoundContext(
                        ResourceLocation.fromNamespaceAndPath("example", "machine.loop"),
                        SoundSource.BLOCKS,
                        DopplerSoundPolicy.SABLE_MOVING_SOUND_INSTANCE_DELEGATE,
                        false,
                        false,
                        true,
                        false,
                        false
                )
        );

        assertFalse(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.SABLE_DELEGATE, decision.reason());
    }

    @Test
    void sableDelegatedPropellerSafeModeStillSkipsDoppler() {
        SoundPhysicsConfig config = config();
        DiagnosticRuntimeOverrides.enablePropellerSafe();

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateDoppler(
                config,
                sableDelegatedAeronauticsPropeller()
        );

        assertFalse(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SAFE_MODE, decision.reason());
    }

    @Test
    void sableDelegatedPropellerDebugModeStillAllowsDoppler() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToAeronauticsPropellers.set(false);
        config.dopplerApplyToPositionalAmbientMachinery.set(false);
        config.dopplerApplyToSableDelegatedSounds.set(false);
        DiagnosticRuntimeOverrides.enablePropellerDebug();

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateDoppler(
                config,
                sableDelegatedAeronauticsPropeller()
        );

        assertTrue(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_ALLOWED, decision.reason());
    }

    @Test
    void knownPropellerStartWithNoAttenuationDefersWithoutMuting() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext start = propeller(false, true, true);

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateAcoustic(config, start);

        assertFalse(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_START_DEFERRED, decision.reason());
        assertTrue(SoundPhysicsSoundPolicy.shouldLeaveSourceUntouchedOnSkip(start, decision.reason()));
    }

    @Test
    void knownPropellerIsExemptFromImpactDedupeThrottleAndRateLimit() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext propeller = propeller(false, false, true);

        assertTrue(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, propeller));
        assertTrue(SoundPhysicsSoundPolicy.isStartThrottleExempt(propeller));
        assertTrue(SoundPhysicsSoundPolicy.isSoundRateLimitExempt(propeller));
        assertTrue(SoundPhysicsSoundPolicy.shouldLeaveSourceUntouchedOnSkip(
                propeller,
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_THROTTLE
        ));
    }

    @Test
    void knownPropellerSafeModeSkipsProcessingWithoutMuting() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext propeller = propeller(false, false, true);
        DiagnosticRuntimeOverrides.enablePropellerSafe();

        SoundPhysicsSoundPolicy.Decision acoustic = SoundPhysicsSoundPolicy.evaluateAcoustic(config, propeller);
        SoundPhysicsSoundPolicy.Decision doppler = SoundPhysicsSoundPolicy.evaluateDoppler(config, propeller);

        assertFalse(acoustic.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SAFE_MODE, acoustic.reason());
        assertFalse(doppler.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SAFE_MODE, doppler.reason());
        assertTrue(SoundPhysicsSoundPolicy.shouldLeaveSourceUntouchedOnSkip(propeller, acoustic.reason()));
    }

    @Test
    void relativeKnownPropellerIsNotWorldRaycastedButIsLeftAudible() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext propeller = propeller(true, false, true);

        SoundPhysicsSoundPolicy.Decision decision = SoundPhysicsSoundPolicy.evaluateAcoustic(config, propeller);

        assertFalse(decision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SKIPPED_RELATIVE, decision.reason());
        assertTrue(SoundPhysicsSoundPolicy.shouldLeaveSourceUntouchedOnSkip(propeller, decision.reason()));
    }

    @Test
    void recordsRequireConfigOrExplicitTestMode() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext record = context("minecraft", "music_disc.cat", SoundSource.RECORDS);

        SoundPhysicsSoundPolicy.Decision defaultDecision = SoundPhysicsSoundPolicy.evaluateAcoustic(config, record);
        assertFalse(defaultDecision.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.RECORD_SKIPPED_POLICY, defaultDecision.reason());

        config.soundPhysicsApplyToRecords.set(true);
        assertEquals(
                SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_CONFIG,
                SoundPhysicsSoundPolicy.evaluateAcoustic(config, record).reason()
        );

        config.soundPhysicsApplyToRecords.set(false);
        DiagnosticRuntimeOverrides.enableRecordTest();
        assertEquals(
                SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_TEST_MODE,
                SoundPhysicsSoundPolicy.evaluateAcoustic(config, record).reason()
        );
    }

    @Test
    void noAttenuationRecordNeedsUnsafeRecordTestMode() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext record = new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.cat"),
                SoundSource.RECORDS,
                "example.RecordSound",
                false,
                true,
                true,
                true,
                false
        );

        SoundPhysicsSoundPolicy.Decision safeMode = SoundPhysicsSoundPolicy.evaluateAcoustic(config, record);
        assertFalse(safeMode.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.RECORD_SKIPPED_NO_ATTENUATION, safeMode.reason());

        DiagnosticRuntimeOverrides.enableRecordTestUnsafe();
        SoundPhysicsSoundPolicy.Decision unsafeMode = SoundPhysicsSoundPolicy.evaluateAcoustic(config, record);
        assertTrue(unsafeMode.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_UNSAFE, unsafeMode.reason());
    }

    @Test
    void positionalAmbientMachineryPolicyDoesNotAllowArbitraryAmbient() {
        SoundPhysicsConfig config = config();

        SoundPhysicsSoundPolicy.Decision result = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("example", "ambient.machine_loop", SoundSource.AMBIENT)
        );

        assertFalse(result.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.AMBIENT_POLICY, result.reason());
    }

    @Test
    void acousticExactSoundDenylistStillSkips() {
        SoundPhysicsConfig config = config();
        config.soundPhysicsSoundDenylist.set("minecraft:block.note_block.pling");

        SoundPhysicsSoundPolicy.Decision result = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("minecraft", "block.note_block.pling", SoundSource.BLOCKS)
        );

        assertFalse(result.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.DENYLIST, result.reason());
    }

    @Test
    void acousticExactClassAllowlistStillApplies() {
        SoundPhysicsConfig config = config();
        config.soundPhysicsSoundAllowlist.set("example.SoundInstance");

        SoundPhysicsSoundPolicy.Decision result = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("minecraft", "music.menu", SoundSource.MUSIC)
        );

        assertTrue(result.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST, result.reason());
    }

    @Test
    void acousticSubstringPrefixesMatchCaseInsensitively() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext customClass = new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("example", "quiet.loop"),
                SoundSource.MUSIC,
                "example.CustomMachineSound",
                false,
                false,
                false,
                true,
                false
        );

        config.soundPhysicsSoundAllowlist.set("sound:NOTE_BLOCK");
        assertEquals(
                SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST,
                SoundPhysicsSoundPolicy.evaluateAcoustic(config, context("minecraft", "block.note_block.pling", SoundSource.MUSIC)).reason()
        );

        config.soundPhysicsSoundAllowlist.set("class:custommachine");
        assertEquals(
                SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST,
                SoundPhysicsSoundPolicy.evaluateAcoustic(config, customClass).reason()
        );
    }

    @Test
    void acousticDenylistWinsOverAllowlist() {
        SoundPhysicsConfig config = config();
        config.soundPhysicsSoundAllowlist.set("example.SoundInstance");
        config.soundPhysicsSoundDenylist.set("minecraft:music.menu");

        SoundPhysicsSoundPolicy.Decision result = SoundPhysicsSoundPolicy.evaluateAcoustic(
                config,
                context("minecraft", "music.menu", SoundSource.MUSIC)
        );

        assertFalse(result.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.DENYLIST, result.reason());
    }

    @Test
    void dopplerCompiledAllowAndDenyListsAreUsed() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext music = context("minecraft", "music.menu", SoundSource.MUSIC);

        config.dopplerSoundAllowlist.set("sound:MUSIC.MENU");
        SoundPhysicsSoundPolicy.Decision allowed = SoundPhysicsSoundPolicy.evaluateDoppler(config, music);
        assertTrue(allowed.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST, allowed.reason());

        config.dopplerSoundDenylist.set("minecraft:music.menu");
        SoundPhysicsSoundPolicy.Decision denied = SoundPhysicsSoundPolicy.evaluateDoppler(config, music);
        assertFalse(denied.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.DENYLIST, denied.reason());
    }

    @Test
    void matcherCacheRefreshesAfterDirectConfigSetWithoutReload() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext music = context("minecraft", "music.menu", SoundSource.MUSIC);

        config.soundPhysicsSoundAllowlist.set("minecraft:music.menu");
        assertEquals(
                SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST,
                SoundPhysicsSoundPolicy.evaluateAcoustic(config, music).reason()
        );

        config.soundPhysicsSoundAllowlist.set("");
        SoundPhysicsSoundPolicy.Decision afterSet = SoundPhysicsSoundPolicy.evaluateAcoustic(config, music);
        assertFalse(afterSet.apply());
        assertEquals(SoundPhysicsSoundPolicy.DecisionReason.MUSIC, afterSet.reason());
    }

    private SoundPhysicsSoundPolicy.SoundContext context(String namespace, String path, SoundSource category) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath(namespace, path),
                category,
                "example.SoundInstance",
                false,
                false,
                false,
                true,
                false
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext propeller(boolean relative, boolean noAttenuation, boolean startEvent) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                relative,
                noAttenuation,
                false,
                startEvent,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext sableDelegatedAeronauticsPropeller() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
                SoundSource.AMBIENT,
                DopplerSoundPolicy.SABLE_MOVING_SOUND_INSTANCE_DELEGATE,
                false,
                false,
                true,
                false,
                true
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

}
