package com.sonicether.soundphysics.integration;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.blocksound.BlockDefinition;
import de.maxhenkel.configbuilder.entry.*;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.FloatListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClothConfigIntegration {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder
                .create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("cloth_config.sound_physics_remastered.settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.general"));

        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.enabled.description"),
                SoundPhysicsMod.CONFIG.enabled
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.simple_voice_chat_integration"),
                Component.translatable("cloth_config.sound_physics_remastered.simple_voice_chat_integration.description"),
                SoundPhysicsMod.CONFIG.simpleVoiceChatIntegration
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.simple_voice_chat_hear_self"),
                Component.translatable("cloth_config.sound_physics_remastered.simple_voice_chat_hear_self.description"),
                SoundPhysicsMod.CONFIG.hearSelf
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.attenuation_factor"),
                Component.translatable("cloth_config.sound_physics_remastered.attenuation_factor.description"),
                SoundPhysicsMod.CONFIG.attenuationFactor
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.reverb_attenuation_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.reverb_attenuation_distance.description"),
                SoundPhysicsMod.CONFIG.reverbAttenuationDistance
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.reverb_gain"),
                Component.translatable("cloth_config.sound_physics_remastered.reverb_gain.description"),
                SoundPhysicsMod.CONFIG.reverbGain
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.reverb_brightness"),
                Component.translatable("cloth_config.sound_physics_remastered.reverb_brightness.description"),
                SoundPhysicsMod.CONFIG.reverbBrightness
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.reverb_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.reverb_distance.description"),
                SoundPhysicsMod.CONFIG.reverbDistance
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.block_absorption"),
                Component.translatable("cloth_config.sound_physics_remastered.block_absorption.description"),
                SoundPhysicsMod.CONFIG.blockAbsorption
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.occlusion_variation"),
                Component.translatable("cloth_config.sound_physics_remastered.occlusion_variation.description"),
                SoundPhysicsMod.CONFIG.occlusionVariation
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.default_block_reflectivity"),
                Component.translatable("cloth_config.sound_physics_remastered.default_block_reflectivity.description"),
                SoundPhysicsMod.CONFIG.defaultBlockReflectivity
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.default_block_occlusion_factor"),
                Component.translatable("cloth_config.sound_physics_remastered.default_block_occlusion_factor.description"),
                SoundPhysicsMod.CONFIG.defaultBlockOcclusionFactor
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_distance_allowance"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_distance_allowance.description"),
                SoundPhysicsMod.CONFIG.soundDistanceAllowance
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.air_absorption"),
                Component.translatable("cloth_config.sound_physics_remastered.air_absorption.description"),
                SoundPhysicsMod.CONFIG.airAbsorption
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.underwater_filter"),
                Component.translatable("cloth_config.sound_physics_remastered.underwater_filter.description"),
                SoundPhysicsMod.CONFIG.underwaterFilter
        ));
        general.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.evaluate_ambient_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.evaluate_ambient_sounds.description"),
                SoundPhysicsMod.CONFIG.evaluateAmbientSounds
        ));

        ConfigCategory performance = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.performance"));

        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.environment_evaluation_ray_count"),
                Component.translatable("cloth_config.sound_physics_remastered.environment_evaluation_ray_count.description"),
                SoundPhysicsMod.CONFIG.environmentEvaluationRayCount
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.environment_evaluation_ray_bounces"),
                Component.translatable("cloth_config.sound_physics_remastered.environment_evaluation_ray_bounces.description"),
                SoundPhysicsMod.CONFIG.environmentEvaluationRayBounces
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.adaptive_reflection_budget_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.adaptive_reflection_budget_enabled.description"),
                SoundPhysicsMod.CONFIG.adaptiveReflectionBudgetEnabled
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.non_full_block_occlusion_factor"),
                Component.translatable("cloth_config.sound_physics_remastered.non_full_block_occlusion_factor.description"),
                SoundPhysicsMod.CONFIG.nonFullBlockOcclusionFactor
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.max_occlusion_rays"),
                Component.translatable("cloth_config.sound_physics_remastered.max_occlusion_rays.description"),
                SoundPhysicsMod.CONFIG.maxOcclusionRays
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.max_occlusion"),
                Component.translatable("cloth_config.sound_physics_remastered.max_occlusion.description"),
                SoundPhysicsMod.CONFIG.maxOcclusion
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.strict_occlusion"),
                Component.translatable("cloth_config.sound_physics_remastered.strict_occlusion.description"),
                SoundPhysicsMod.CONFIG.strictOcclusion
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_direction_evaluation"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_direction_evaluation.description"),
                SoundPhysicsMod.CONFIG.soundDirectionEvaluation
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.redirect_non_occluded_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.redirect_non_occluded_sounds.description"),
                SoundPhysicsMod.CONFIG.redirectNonOccludedSounds
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.update_moving_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.update_moving_sounds.description"),
                SoundPhysicsMod.CONFIG.updateMovingSounds
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_update_interval"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_update_interval.description"),
                SoundPhysicsMod.CONFIG.soundUpdateInterval
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.record_acoustic_update_interval_ticks"),
                Component.translatable("cloth_config.sound_physics_remastered.record_acoustic_update_interval_ticks.description"),
                SoundPhysicsMod.CONFIG.recordAcousticUpdateIntervalTicks
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_max_sound_starts_per_tick"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_max_sound_starts_per_tick.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsMaxSoundStartsPerTick
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_max_debug_rays_per_tick"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_max_debug_rays_per_tick.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsMaxDebugRaysPerTick
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_enabled.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeEnabled
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_apply_to_tickable_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_apply_to_tickable_sounds.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeApplyToTickableSounds
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_radius"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_radius.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeRadius
        ));
        performance.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_ticks"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_impact_burst_dedupe_ticks.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeTicks
        ));

        ConfigCategory policy = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.sound_policy"));

        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sable_acoustics_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.sable_acoustics_enabled.description"),
                SoundPhysicsMod.CONFIG.sableAcousticsEnabled
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.force_root_acoustic_provider"),
                Component.translatable("cloth_config.sound_physics_remastered.force_root_acoustic_provider.description"),
                SoundPhysicsMod.CONFIG.forceRootAcousticProvider
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sable_acoustic_debug_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.sable_acoustic_debug_logging.description"),
                SoundPhysicsMod.CONFIG.sableAcousticDebugLogging
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_weather_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_weather_sounds.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToWeatherSounds
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_positional_ambient_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_positional_ambient_sounds.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToPositionalAmbientSounds
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_positional_ambient_machinery"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_positional_ambient_machinery.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToPositionalAmbientMachinery
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_records"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_records.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToRecords
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_music"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_music.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToMusic
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_crosswind_wind"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_apply_to_crosswind_wind.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsApplyToCrosswindWind
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_sound_allowlist"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_sound_allowlist.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsSoundAllowlist
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_sound_denylist"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_physics_sound_denylist.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsSoundDenylist
        ));
        policy.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.sound_policy_debug_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.sound_policy_debug_logging.description"),
                SoundPhysicsMod.CONFIG.soundPhysicsPolicyDebugLogging
        ));

        ConfigCategory doppler = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.doppler"));

        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_enabled.description"),
                SoundPhysicsMod.CONFIG.dopplerEnabled
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_strength"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_strength.description"),
                SoundPhysicsMod.CONFIG.dopplerStrength
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_speed_of_sound_blocks_per_second"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_speed_of_sound_blocks_per_second.description"),
                SoundPhysicsMod.CONFIG.dopplerSpeedOfSound
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_min_pitch_multiplier"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_min_pitch_multiplier.description"),
                SoundPhysicsMod.CONFIG.dopplerMinPitchMultiplier
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_pitch_multiplier"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_pitch_multiplier.description"),
                SoundPhysicsMod.CONFIG.dopplerMaxPitchMultiplier
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_update_interval_ticks"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_update_interval_ticks.description"),
                SoundPhysicsMod.CONFIG.dopplerUpdateIntervalTicks
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_smoothing_time_ms"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_smoothing_time_ms.description"),
                SoundPhysicsMod.CONFIG.dopplerSmoothingTimeMs
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_block_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_block_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToBlockSounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_entity_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_entity_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToEntitySounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_weather_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_weather_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToWeatherSounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_ambient_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_ambient_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToAmbientSounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_positional_ambient_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_positional_ambient_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToPositionalAmbientSounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_positional_ambient_machinery"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_positional_ambient_machinery.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToPositionalAmbientMachinery
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_aeronautics_propellers"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_aeronautics_propellers.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToAeronauticsPropellers
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_sable_delegated_sounds"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_sable_delegated_sounds.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToSableDelegatedSounds
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_records"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_records.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToRecords
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_music"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_music.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToMusic
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_crosswind_wind"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_apply_to_crosswind_wind.description"),
                SoundPhysicsMod.CONFIG.dopplerApplyToCrosswindWind
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_sound_allowlist"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_sound_allowlist.description"),
                SoundPhysicsMod.CONFIG.dopplerSoundAllowlist
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_sound_denylist"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_sound_denylist.description"),
                SoundPhysicsMod.CONFIG.dopplerSoundDenylist
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_listener_speed_blocks_per_second"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_listener_speed_blocks_per_second.description"),
                SoundPhysicsMod.CONFIG.dopplerMaxListenerSpeed
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_source_speed_blocks_per_second"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_max_source_speed_blocks_per_second.description"),
                SoundPhysicsMod.CONFIG.dopplerMaxSourceSpeed
        ));
        doppler.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.doppler_debug_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.doppler_debug_logging.description"),
                SoundPhysicsMod.CONFIG.dopplerDebugLogging
        ));

        ConfigCategory propeller = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.propeller_long_range"));

        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_enabled.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeEnabled
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_profile"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_profile.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeProfile
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_max_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_max_distance.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeMaxDistance
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_min_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_min_distance.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeMinDistance
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_absolute_cap_blocks"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_absolute_cap_blocks.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeAbsoluteCapBlocks
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_max_processing_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_max_processing_distance.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeMaxProcessingDistance
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_small_fallback_sails"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_small_fallback_sails.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeSmallFallbackSails
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_large_fallback_sails"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_large_fallback_sails.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeLargeFallbackSails
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_size_reference_sails"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_size_reference_sails.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeSizeReferenceSails
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rpm_reference"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rpm_reference.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeRpmReference
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_size_exponent"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_size_exponent.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeSizeExponent
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rpm_exponent"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rpm_exponent.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeRpmExponent
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_pitch_at_reference_rpm"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_pitch_at_reference_rpm.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangePitchAtReferenceRpm
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_fraction"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_fraction.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeReferenceDistanceFraction
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_min"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_min.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeReferenceDistanceMin
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_max"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_reference_distance_max.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeReferenceDistanceMax
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rolloff_factor"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_rolloff_factor.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeRolloffFactor
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_apply_in_safe_mode"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_apply_in_safe_mode.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeApplyInSafeMode
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_apply_to_crosswind_vehicle_propellers"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_apply_to_crosswind_vehicle_propellers.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeApplyToCrosswindVehiclePropellers
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_close_end_blocks"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_close_end_blocks.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeCloseEndBlocks
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_mid_end_blocks"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_mid_end_blocks.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeMidEndBlocks
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_mid_gain"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_mid_gain.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeMidGain
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_far_min_gain"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_long_range_far_min_gain.description"),
                SoundPhysicsMod.CONFIG.propellerLongRangeFarMinGain
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_enabled"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_enabled.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldEnabled
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_fraction"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_fraction.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldStartFraction
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_blocks"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_blocks.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldStartBlocks
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_full_blocks"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_full_blocks.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldFullBlocks
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_ratio"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_start_ratio.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldStartRatio
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_full_ratio"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_full_ratio.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldFullRatio
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_direct_cutoff_at_max"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_direct_cutoff_at_max.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldDirectCutoffAtMax
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_direct_gain_at_max"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_direct_gain_at_max.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldDirectGainAtMax
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_cutoff_min"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_cutoff_min.description"),
                SoundPhysicsMod.CONFIG.propellerFarCutoffMin
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_air_absorption_bonus"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_air_absorption_bonus.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldAirAbsorptionBonus
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_compensate_high_source_volume"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_compensate_high_source_volume.description"),
                SoundPhysicsMod.CONFIG.propellerFarCompensateHighSourceVolume
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_source_volume_compensation_strength"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_source_volume_compensation_strength.description"),
                SoundPhysicsMod.CONFIG.propellerFarSourceVolumeCompensationStrength
        ));
        propeller.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_skip_reverb_after_distance"),
                Component.translatable("cloth_config.sound_physics_remastered.propeller_far_field_skip_reverb_after_distance.description"),
                SoundPhysicsMod.CONFIG.propellerFarFieldSkipReverbAfterDistance
        ));

        ConfigCategory reflectivity = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.reflectivity"));

        Map<BlockDefinition, Float> defaultReflectivityMap = new LinkedHashMap<>();
        SoundPhysicsMod.REFLECTIVITY_CONFIG.addDefaults(defaultReflectivityMap);

        for (Map.Entry<BlockDefinition, Float> entry : SoundPhysicsMod.REFLECTIVITY_CONFIG.getBlockDefinitions().entrySet()) {
            FloatListEntry e = entryBuilder
                    .startFloatField(entry.getKey().getName(), entry.getValue())
                    .setMin(0.01F)
                    .setMax(10F)
                    .setDefaultValue(defaultReflectivityMap.getOrDefault(entry.getKey(), SoundPhysicsMod.CONFIG.defaultBlockReflectivity.get()))
                    .setSaveConsumer(value -> SoundPhysicsMod.REFLECTIVITY_CONFIG.setBlockDefinitionValue(entry.getKey(), value)).build();
            reflectivity.addEntry(e);
        }

        ConfigCategory occlusion = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.occlusion"));

        Map<BlockDefinition, Float> defaultOcclusionMap = new LinkedHashMap<>();
        SoundPhysicsMod.OCCLUSION_CONFIG.addDefaults(defaultOcclusionMap);

        for (Map.Entry<BlockDefinition, Float> entry : SoundPhysicsMod.OCCLUSION_CONFIG.getBlockDefinitions().entrySet()) {
            FloatListEntry e = entryBuilder
                    .startFloatField(entry.getKey().getName(), entry.getValue())
                    .setMin(0F)
                    .setMax(10F)
                    .setDefaultValue(defaultOcclusionMap.getOrDefault(entry.getKey(), SoundPhysicsMod.CONFIG.defaultBlockOcclusionFactor.get()))
                    .setSaveConsumer(value -> SoundPhysicsMod.OCCLUSION_CONFIG.setBlockDefinitionValue(entry.getKey(), value)).build();
            occlusion.addEntry(e);
        }

        ConfigCategory logging = builder.getOrCreateCategory(Component.translatable("cloth_config.sound_physics_remastered.category.debug"));

        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.openal_error_checks"),
                Component.translatable("cloth_config.sound_physics_remastered.openal_error_checks.description"),
                SoundPhysicsMod.CONFIG.openAlErrorChecks
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.debug_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.debug_logging.description"),
                SoundPhysicsMod.CONFIG.debugLogging
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.occlusion_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.occlusion_logging.description"),
                SoundPhysicsMod.CONFIG.occlusionLogging
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.environment_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.environment_logging.description"),
                SoundPhysicsMod.CONFIG.environmentLogging
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.performance_logging"),
                Component.translatable("cloth_config.sound_physics_remastered.performance_logging.description"),
                SoundPhysicsMod.CONFIG.performanceLogging
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.render_sound_bounces"),
                Component.translatable("cloth_config.sound_physics_remastered.render_sound_bounces.description"),
                SoundPhysicsMod.CONFIG.renderSoundBounces
        ));
        logging.addEntry(fromConfigEntry(entryBuilder,
                Component.translatable("cloth_config.sound_physics_remastered.render_occlusion"),
                Component.translatable("cloth_config.sound_physics_remastered.render_occlusion.description"),
                SoundPhysicsMod.CONFIG.renderOcclusion
        ));

        builder.setSavingRunnable(() -> {
            Loggers.log("Saving configs");
            SoundPhysicsMod.CONFIG.enabled.save();
            SoundPhysicsMod.REFLECTIVITY_CONFIG.save();
            SoundPhysicsMod.OCCLUSION_CONFIG.save();
            SoundPhysicsMod.SOUND_RATE_CONFIG.save();
            SoundPhysicsMod.CONFIG.reloadClient();
        });

        return builder.build();
    }

    private static <T> AbstractConfigListEntry<T> fromConfigEntry(ConfigEntryBuilder entryBuilder, Component name, Component description, ConfigEntry<T> entry) {
        if (entry instanceof DoubleConfigEntry e) {
            return (AbstractConfigListEntry<T>) entryBuilder
                    .startDoubleField(name, e.get())
                    .setTooltip(description)
                    .setMin(e.getMin())
                    .setMax(e.getMax())
                    .setDefaultValue(e::getDefault)
                    .setSaveConsumer(d -> e.set(d))
                    .build();
        } else if (entry instanceof FloatConfigEntry e) {
            return (AbstractConfigListEntry<T>) entryBuilder
                    .startFloatField(name, e.get())
                    .setTooltip(description)
                    .setMin(e.getMin())
                    .setMax(e.getMax())
                    .setDefaultValue(e::getDefault)
                    .setSaveConsumer(d -> e.set(d))
                    .build();
        } else if (entry instanceof IntegerConfigEntry e) {
            return (AbstractConfigListEntry<T>) entryBuilder
                    .startIntField(name, e.get())
                    .setTooltip(description)
                    .setMin(e.getMin())
                    .setMax(e.getMax())
                    .setDefaultValue(e::getDefault)
                    .setSaveConsumer(i -> e.set(i))
                    .build();
        } else if (entry instanceof BooleanConfigEntry e) {
            return (AbstractConfigListEntry<T>) entryBuilder
                    .startBooleanToggle(name, e.get())
                    .setTooltip(description)
                    .setDefaultValue(e::getDefault)
                    .setSaveConsumer(b -> e.set(b))
                    .build();
        } else if (entry instanceof StringConfigEntry e) {
            return (AbstractConfigListEntry<T>) entryBuilder
                    .startStrField(name, e.get())
                    .setTooltip(description)
                    .setDefaultValue(e::getDefault)
                    .setSaveConsumer(s -> e.set(s))
                    .build();
        }

        return null;
    }

}
