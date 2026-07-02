package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdaptiveReflectionBudgetTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledReturnsExactConfiguredBudget() {
        SoundPhysicsConfig config = config();
        config.adaptiveReflectionBudgetEnabled.set(false);

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, propeller(true), 512.0D);

        assertEquals(32, budget.rays());
        assertEquals(4, budget.bounces());
        assertFalse(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.LEGACY, budget.reason());
    }

    @Test
    void nearNormalOneShotKeepsFullBudget() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, oneShot(), 16.0D);

        assertEquals(32, budget.rays());
        assertEquals(4, budget.bounces());
        assertFalse(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.NEAR_FULL, budget.reason());
    }

    @Test
    void farNormalOneShotUsesModestReduction() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, oneShot(), 96.0D);

        assertEquals(16, budget.rays());
        assertEquals(3, budget.bounces());
        assertTrue(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.FAR_REDUCED, budget.reason());
    }

    @Test
    void veryFarNormalOneShotUsesVeryFarCap() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, oneShot(), 256.0D);

        assertEquals(8, budget.rays());
        assertEquals(2, budget.bounces());
        assertTrue(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.FAR_REDUCED, budget.reason());
    }

    @Test
    void knownPropellerUsesPropellerCap() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, propeller(true), 8.0D);

        assertEquals(12, budget.rays());
        assertEquals(2, budget.bounces());
        assertTrue(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.PROPELLER, budget.reason());
    }

    @Test
    void crosswindWindLoopUsesCrosswindCap() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget budget = AdaptiveReflectionBudget.resolve(config, crosswindWind(), 8.0D);

        assertEquals(12, budget.rays());
        assertEquals(2, budget.bounces());
        assertTrue(budget.reduced());
        assertEquals(AdaptiveReflectionBudget.Reason.CROSSWIND_LOOP, budget.reason());
    }

    @Test
    void tickableAndMovingUpdateUseContinuousCap() {
        SoundPhysicsConfig config = config();

        AdaptiveReflectionBudget.Budget tickable = AdaptiveReflectionBudget.resolve(config, oneShot(true, true), 8.0D);
        AdaptiveReflectionBudget.Budget movingUpdate = AdaptiveReflectionBudget.resolve(config, oneShot(false, false), 8.0D);

        assertEquals(16, tickable.rays());
        assertEquals(3, tickable.bounces());
        assertEquals(AdaptiveReflectionBudget.Reason.CONTINUOUS_LOOP, tickable.reason());
        assertEquals(16, movingUpdate.rays());
        assertEquals(3, movingUpdate.bounces());
        assertEquals(AdaptiveReflectionBudget.Reason.CONTINUOUS_LOOP, movingUpdate.reason());
    }

    @Test
    void configuredMinimumValuesRemainStable() {
        SoundPhysicsConfig config = config();
        config.environmentEvaluationRayCount.set(8);
        config.environmentEvaluationRayBounces.set(2);

        AdaptiveReflectionBudget.Budget far = AdaptiveReflectionBudget.resolve(config, oneShot(), 128.0D);
        AdaptiveReflectionBudget.Budget continuous = AdaptiveReflectionBudget.resolve(config, oneShot(true, true), 8.0D);

        assertEquals(8, far.rays());
        assertEquals(2, far.bounces());
        assertEquals(8, continuous.rays());
        assertEquals(2, continuous.bounces());
    }

    private SoundPhysicsSoundPolicy.SoundContext oneShot() {
        return oneShot(true, false);
    }

    private SoundPhysicsSoundPolicy.SoundContext oneShot(boolean startEvent, boolean tickable) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling"),
                SoundSource.BLOCKS,
                "example.SoundInstance",
                false,
                false,
                false,
                startEvent,
                tickable
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext propeller(boolean startEvent) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                startEvent,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext crosswindWind() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("crosswind", "weather.wind.light"),
                SoundSource.WEATHER,
                "example.CrosswindWindSound",
                false,
                false,
                false,
                true,
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
