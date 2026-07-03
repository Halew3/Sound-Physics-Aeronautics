package com.sonicether.soundphysics.config;

import java.util.Objects;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.configbuilder.entry.ConfigEntry;

public class SoundPhysicsConfig {

    public final ConfigEntry<Boolean> enabled;

    public final ConfigEntry<Float> attenuationFactor;
    public final ConfigEntry<Float> reverbAttenuationDistance;
    public final ConfigEntry<Float> reverbGain;
    public final ConfigEntry<Float> reverbBrightness;
    public final ConfigEntry<Float> reverbDistance;
    public final ConfigEntry<Float> blockAbsorption;
    public final ConfigEntry<Float> occlusionVariation;
    public final ConfigEntry<Float> defaultBlockReflectivity;
    public final ConfigEntry<Float> defaultBlockOcclusionFactor;
    public final ConfigEntry<Float> soundDistanceAllowance;
    public final ConfigEntry<Float> airAbsorption;
    public final ConfigEntry<Float> underwaterFilter;
    public final ConfigEntry<Boolean> evaluateAmbientSounds;

    public final ConfigEntry<Integer> environmentEvaluationRayCount;
    public final ConfigEntry<Integer> environmentEvaluationRayBounces;
    public final ConfigEntry<Boolean> adaptiveReflectionBudgetEnabled;
    public final ConfigEntry<Float> nonFullBlockOcclusionFactor;
    public final ConfigEntry<Integer> maxOcclusionRays;
    public final ConfigEntry<Float> maxOcclusion;
    public final ConfigEntry<Boolean> strictOcclusion;
    public final ConfigEntry<Boolean> soundDirectionEvaluation;
    public final ConfigEntry<Boolean> redirectNonOccludedSounds;
    public final ConfigEntry<Boolean> updateMovingSounds;
    public final ConfigEntry<Integer> soundUpdateInterval;
    public final ConfigEntry<Integer> recordAcousticUpdateIntervalTicks;
    public final ConfigEntry<Double> maxSoundProcessingDistance;
    public final ConfigEntry<Boolean> soundPhysicsTraceLogging;
    public final ConfigEntry<Boolean> openAlErrorChecks;
    public final ConfigEntry<Boolean> sableAcousticsEnabled;
    public final ConfigEntry<Boolean> forceRootAcousticProvider;
    public final ConfigEntry<Boolean> sableAcousticDebugLogging;
    public final ConfigEntry<Boolean> soundPhysicsApplyToWeatherSounds;
    public final ConfigEntry<Boolean> soundPhysicsApplyToPositionalAmbientSounds;
    public final ConfigEntry<Boolean> soundPhysicsApplyToPositionalAmbientMachinery;
    public final ConfigEntry<Boolean> soundPhysicsApplyToRecords;
    public final ConfigEntry<Boolean> soundPhysicsApplyToMusic;
    public final ConfigEntry<Boolean> soundPhysicsApplyToCrosswindWind;
    public final ConfigEntry<String> soundPhysicsSoundAllowlist;
    public final ConfigEntry<String> soundPhysicsSoundDenylist;
    public final ConfigEntry<Boolean> soundPhysicsPolicyDebugLogging;
    public final ConfigEntry<Boolean> dopplerEnabled;
    public final ConfigEntry<Double> dopplerStrength;
    public final ConfigEntry<Double> dopplerSpeedOfSound;
    public final ConfigEntry<Double> dopplerMinPitchMultiplier;
    public final ConfigEntry<Double> dopplerMaxPitchMultiplier;
    public final ConfigEntry<Integer> dopplerUpdateIntervalTicks;
    public final ConfigEntry<Integer> dopplerSmoothingTimeMs;
    public final ConfigEntry<Boolean> dopplerApplyToBlockSounds;
    public final ConfigEntry<Boolean> dopplerApplyToEntitySounds;
    public final ConfigEntry<Boolean> dopplerApplyToWeatherSounds;
    public final ConfigEntry<Boolean> dopplerApplyToAmbientSounds;
    public final ConfigEntry<Boolean> dopplerApplyToPositionalAmbientSounds;
    public final ConfigEntry<Boolean> dopplerApplyToSableDelegatedSounds;
    public final ConfigEntry<Boolean> dopplerApplyToMusic;
    public final ConfigEntry<Boolean> dopplerApplyToRecords;
    public final ConfigEntry<Boolean> dopplerApplyToAeronauticsPropellers;
    public final ConfigEntry<Boolean> dopplerApplyToPositionalAmbientMachinery;
    public final ConfigEntry<Boolean> dopplerApplyToCrosswindWind;
    public final ConfigEntry<String> dopplerSoundAllowlist;
    public final ConfigEntry<String> dopplerSoundDenylist;
    public final ConfigEntry<Double> dopplerMaxListenerSpeed;
    public final ConfigEntry<Double> dopplerMaxSourceSpeed;
    public final ConfigEntry<Boolean> dopplerDebugLogging;
    public final ConfigEntry<Boolean> propellerLongRangeEnabled;
    public final ConfigEntry<String> propellerLongRangeProfile;
    public final ConfigEntry<Double> propellerLongRangeMaxDistance;
    public final ConfigEntry<Double> propellerLongRangeMinDistance;
    public final ConfigEntry<Double> propellerLongRangeAbsoluteCapBlocks;
    public final ConfigEntry<Double> propellerLongRangeMaxProcessingDistance;
    public final ConfigEntry<Integer> propellerLongRangeSmallFallbackSails;
    public final ConfigEntry<Integer> propellerLongRangeLargeFallbackSails;
    public final ConfigEntry<Double> propellerLongRangeSizeReferenceSails;
    public final ConfigEntry<Double> propellerLongRangeRpmReference;
    public final ConfigEntry<Double> propellerLongRangeSizeExponent;
    public final ConfigEntry<Double> propellerLongRangeRpmExponent;
    public final ConfigEntry<Double> propellerLongRangePitchAtReferenceRpm;
    public final ConfigEntry<Double> propellerLongRangeReferenceDistanceFraction;
    public final ConfigEntry<Double> propellerLongRangeReferenceDistanceMin;
    public final ConfigEntry<Double> propellerLongRangeReferenceDistanceMax;
    public final ConfigEntry<Double> propellerLongRangeRolloffFactor;
    public final ConfigEntry<Boolean> propellerLongRangeApplyInSafeMode;
    public final ConfigEntry<Boolean> propellerLongRangeApplyToCrosswindVehiclePropellers;
    public final ConfigEntry<Double> propellerLongRangeCloseEndBlocks;
    public final ConfigEntry<Double> propellerLongRangeMidEndBlocks;
    public final ConfigEntry<Double> propellerLongRangeMidGain;
    public final ConfigEntry<Double> propellerLongRangeFarMinGain;
    public final ConfigEntry<Boolean> propellerFarFieldEnabled;
    public final ConfigEntry<Double> propellerFarFieldStartFraction;
    public final ConfigEntry<Double> propellerFarFieldStartBlocks;
    public final ConfigEntry<Double> propellerFarFieldFullBlocks;
    public final ConfigEntry<Double> propellerFarFieldStartRatio;
    public final ConfigEntry<Double> propellerFarFieldFullRatio;
    public final ConfigEntry<Double> propellerFarFieldDirectCutoffAtMax;
    public final ConfigEntry<Double> propellerFarFieldDirectGainAtMax;
    public final ConfigEntry<Double> propellerFarCutoffMin;
    public final ConfigEntry<Double> propellerFarFieldAirAbsorptionBonus;
    public final ConfigEntry<Boolean> propellerFarCompensateHighSourceVolume;
    public final ConfigEntry<Double> propellerFarSourceVolumeCompensationStrength;
    public final ConfigEntry<Double> propellerFarFieldSkipReverbAfterDistance;
    public final ConfigEntry<Boolean> distantHorizonsFarPropellerOcclusionEnabled;
    public final ConfigEntry<Double> distantHorizonsFarPropellerMinDistance;
    public final ConfigEntry<Integer> distantHorizonsFarPropellerProbeIntervalTicks;
    public final ConfigEntry<Double> distantHorizonsFarPropellerMaxStrength;
    public final ConfigEntry<Double> distantHorizonsFarPropellerGainAtFullOcclusion;
    public final ConfigEntry<Double> distantHorizonsFarPropellerCutoffAtFullOcclusion;
    public final ConfigEntry<Integer> distantHorizonsFarPropellerMaxRayLength;
    public final ConfigEntry<Boolean> distantHorizonsFarPropellerDebugLogging;
    public final ConfigEntry<Integer> soundPhysicsMaxSoundStartsPerTick;
    public final ConfigEntry<Integer> soundPhysicsMaxDebugRaysPerTick;
    public final ConfigEntry<Boolean> soundPhysicsImpactBurstDedupeEnabled;
    public final ConfigEntry<Boolean> soundPhysicsImpactBurstDedupeApplyToTickableSounds;
    public final ConfigEntry<Double> soundPhysicsImpactBurstDedupeRadius;
    public final ConfigEntry<Integer> soundPhysicsImpactBurstDedupeTicks;
    public final ConfigEntry<Boolean> unsafeLevelAccess;
    public final ConfigEntry<Integer> levelCloneRange;
    public final ConfigEntry<Integer> levelCloneMaxRetainTicks;
    public final ConfigEntry<Integer> levelCloneMaxRetainBlockDistance;

    public final ConfigEntry<Boolean> debugLogging;
    public final ConfigEntry<Boolean> occlusionLogging;
    public final ConfigEntry<Boolean> environmentLogging;
    public final ConfigEntry<Boolean> performanceLogging;
    public final ConfigEntry<Boolean> renderSoundBounces;
    public final ConfigEntry<Boolean> renderOcclusion;

    public final ConfigEntry<Boolean> simpleVoiceChatIntegration;
    public final ConfigEntry<Boolean> hearSelf;
    private final CachedSoundMatcher soundPhysicsSoundAllowMatcher = new CachedSoundMatcher();
    private final CachedSoundMatcher soundPhysicsSoundDenyMatcher = new CachedSoundMatcher();
    private final CachedSoundMatcher dopplerSoundAllowMatcher = new CachedSoundMatcher();
    private final CachedSoundMatcher dopplerSoundDenyMatcher = new CachedSoundMatcher();

    public SoundPhysicsConfig(ConfigBuilder builder) {
        enabled = builder.booleanEntry("enabled", true)
                .comment("Enables/Disables all sound effects");

        attenuationFactor = builder
                .floatEntry("attenuation_factor", 1F, 0.1F, 1F)
                .comment(
                        "Affects how quiet a sound gets based on distance",
                        "Lower values mean distant sounds are louder",
                        "This setting requires you to be in singleplayer or having the mod installed on the server",
                        "1.0 is the physically correct value"
                );
        reverbAttenuationDistance = builder
                .floatEntry("reverb_attenuation_distance", 0F, 0F, 512F)
                .comment(
                        "The ray distance at which reverb starts",
                        "0.0 disables reverb attenuation"
                );
        reverbGain = builder
                .floatEntry("reverb_gain", 1F, 0.1F, 2F)
                .comment("The volume of simulated reverberations");
        reverbBrightness = builder.floatEntry("reverb_brightness", 1F, 0.1F, 2F)
                .comment(
                        "The brightness of reverberation",
                        "Higher values result in more high frequencies in reverberation",
                        "Lower values give a more muffled sound to the reverb"
                );
        reverbDistance = builder
                .floatEntry("reverb_distance", 1.5F, 0.1F, 16F)
                .comment("The distance of reverb relative to the sound distance");
        blockAbsorption = builder.floatEntry("block_absorption", 1F, 0.1F, 4F)
                .comment("The amount of sound that will be absorbed when traveling through blocks");
        occlusionVariation = builder.floatEntry("occlusion_variation", 0.35F, 0F, 16F)
                .comment("Higher values mean smaller objects won't be considered as occluding");
        defaultBlockReflectivity = builder.floatEntry("default_block_reflectivity", 0.5F, 0.1F, 4F)
                .comment(
                        "The default amount of sound reflectance energy for all blocks",
                        "Lower values result in more conservative reverb simulation with shorter reverb tails",
                        "Higher values result in more generous reverb simulation with higher reverb tails"
                );
        defaultBlockOcclusionFactor = builder.floatEntry("default_block_occlusion_factor", 1F, 0F, 10F)
                .comment(
                        "The default amount of occlusion for all blocks",
                        "Lower values will result in sounds being less muffled through walls",
                        "Higher values mean sounds may be inaudible through thicker walls"
                );
        soundDistanceAllowance = builder.floatEntry("sound_distance_allowance", 4F, 1F, 6F)
                .comment(
                        "Minecraft won't allow sounds to play past a certain distance",
                        "This parameter is a multiplier for how far away a sound source is allowed to be in order for it to actually play",
                        "This setting only takes effect in singleplayer worlds and when installed on the server"
                );
        airAbsorption = builder.floatEntry("air_absorption", 1F, 0F, 5F)
                .comment(
                        "A value controlling the amount that air absorbs high frequencies with distance",
                        "A value of 1.0 is physically correct for air with normal humidity and temperature",
                        "Higher values mean air will absorb more high frequencies with distance",
                        "0 disables this effect"
                );
        underwaterFilter = builder.floatEntry("underwater_filter", 0.9F, 0F, 1F)
                .comment(
                        "How much sound is filtered when the player is underwater",
                        "0.0 means no filter",
                        "1.0 means fully filtered"
                );
        evaluateAmbientSounds = builder.booleanEntry("evaluate_ambient_sounds", false)
                .comment(
                        "Whether sounds like cave, nether or underwater ambient sounds should have sound physics"
                );

        environmentEvaluationRayCount = builder.integerEntry("environment_evaluation_ray_count", 32, 8, 64)
                .comment(
                        "The number of rays to trace to determine reverberation for each sound source",
                        "More rays provides more consistent tracing results but takes more time to calculate",
                        "Decrease this value if you experience lag spikes when sounds play"
                );
        environmentEvaluationRayBounces = builder.integerEntry("environment_evaluation_ray_bounces", 4, 2, 64)
                .comment(
                        "The number of rays bounces to trace to determine reverberation for each sound source",
                        "More bounces provides more echo and sound ducting but takes more time to calculate",
                        "Decrease this value if you experience lag spikes when sounds play"
                );
        adaptiveReflectionBudgetEnabled = builder.booleanEntry("adaptive_reflection_budget_enabled", true)
                .comment(
                        "Reduces reflection/reverb ray and bounce budgets for distant, looped, and machinery sounds",
                        "Direct occlusion rays and direct gain/cutoff math are not affected"
                );
        nonFullBlockOcclusionFactor = builder.floatEntry("non_full_block_occlusion_factor", 0.25F, 0F, 1F)
                .comment("If sound hits a non-full-square side, block occlusion is multiplied by this");
        maxOcclusionRays = builder.integerEntry("max_occlusion_rays", 16, 1, 128)
                .comment(
                        "The maximum amount of rays to determine occlusion",
                        "Directly correlates to the amount of blocks between walls that are considered"
                );
        maxOcclusion = builder.floatEntry("max_occlusion", 64F, 0F, 1024F)
                .comment("The amount at which occlusion is capped");
        strictOcclusion = builder.booleanEntry("strict_occlusion", false)
                .comment("If enabled, the occlusion calculation only uses one path between the sound source and the listener instead of 9");
        soundDirectionEvaluation = builder.booleanEntry("sound_direction_evaluation", true)
                .comment("Whether to try calculating where the sound should come from based on reflections");
        redirectNonOccludedSounds = builder.booleanEntry("redirect_non_occluded_sounds", true)
                .comment("Skip redirecting non-occluded sounds (the ones you can see directly)");
        updateMovingSounds = builder.booleanEntry("update_moving_sounds", false)
                .comment("If music discs or other longer sounds should be frequently reevaluated");
        soundUpdateInterval = builder.integerEntry("sound_update_interval", 5, 1, Integer.MAX_VALUE)
                .comment(
                        "The interval in ticks that moving sounds are reevaluated",
                        "Lower values mean more frequent reevaluation but also more lag",
                        "This option only takes effect if update_moving_sounds is enabled"
                );
        recordAcousticUpdateIntervalTicks = builder.integerEntry("record_acoustic_update_interval_ticks", 5, 1, 200)
                .comment(
                        "Experimental interval in ticks for periodic jukebox/record acoustic reevaluation",
                        "Only used when record acoustic processing is enabled by record_test mode or sound_physics_apply_to_records"
                );
        maxSoundProcessingDistance = builder.doubleEntry("max_sound_processing_distance", 512D, 0D, Double.MAX_VALUE)
                .comment(
                        "The maximum distance a sound can be processed"
                );
        soundPhysicsTraceLogging = builder.booleanEntry("sound_physics_trace_logging", false)
                .comment(
                        "Temporary diagnostic logging for the root sound-processing path.",
                        "Use only while debugging because it logs per processed sound and ray."
                );
        openAlErrorChecks = builder.booleanEntry("openal_error_checks", false)
                .comment(
                        "Checks OpenAL error state after selected source writes.",
                        "Disabled by default because alGetError is expensive on per-source hot paths."
                );
        sableAcousticsEnabled = builder.booleanEntry("sable_acoustics_enabled", true)
                .comment(
                        "Enables Sable acoustic snapshots and Sable-aware raycasts when Sable is installed.",
                        "Disable this compatibility switch to force behavior close to original root-world SPR."
                );
        forceRootAcousticProvider = builder.booleanEntry("force_root_acoustic_provider", false)
                .comment(
                        "Diagnostic switch that forces sound processing to use the root acoustic provider even when Sable is installed.",
                        "This is not intended as final Sable behavior."
                );
        sableAcousticDebugLogging = builder.booleanEntry("sable_acoustic_debug_logging", false)
                .comment("Logs sampled Sable acoustic snapshot and cache diagnostics without enabling all debug logging.");
        soundPhysicsApplyToWeatherSounds = builder.booleanEntry("sound_physics_apply_to_weather_sounds", true)
                .comment("Apply acoustic occlusion/reverb to positional weather sounds.");
        soundPhysicsApplyToPositionalAmbientSounds = builder.booleanEntry("sound_physics_apply_to_positional_ambient_sounds", false)
                .comment("Apply acoustic occlusion/reverb to arbitrary positional ambient loops. Disabled by default because many ambient loops are global.");
        soundPhysicsApplyToPositionalAmbientMachinery = builder.booleanEntry("sound_physics_apply_to_positional_ambient_machinery", true)
                .comment("Apply acoustic occlusion/reverb to known positional ambient machinery loops such as Aeronautics propeller bearings.");
        soundPhysicsApplyToRecords = builder.booleanEntry("sound_physics_apply_to_records", false)
                .comment("Experimental: apply acoustic occlusion/reverb to jukebox records. Disabled by default; use record_test modes for diagnostics before enabling.");
        soundPhysicsApplyToMusic = builder.booleanEntry("sound_physics_apply_to_music", false)
                .comment("Apply acoustic occlusion/reverb to music. Disabled by default.");
        soundPhysicsApplyToCrosswindWind = builder.booleanEntry("sound_physics_apply_to_crosswind_wind", false)
                .comment("Apply acoustic occlusion/reverb to Crosswind global wind loops. Crosswind howl is not treated as a global wind loop.");
        soundPhysicsSoundAllowlist = builder.stringEntry("sound_physics_sound_allowlist", "")
                .comment("Comma-separated sound ids or class patterns that force acoustic physics after positional safety checks. Use sound: or class: prefixes for substring matches.");
        soundPhysicsSoundDenylist = builder.stringEntry("sound_physics_sound_denylist", "")
                .comment("Comma-separated sound ids or class patterns that skip acoustic physics.");
        soundPhysicsPolicyDebugLogging = builder.booleanEntry("sound_policy_debug_logging", false)
                .comment("Logs sound policy decisions for propeller/Crosswind candidates and skipped sounds.");
        dopplerEnabled = builder.booleanEntry("doppler_enabled", true)
                .comment("Enables pitch shifting based on source/listener relative motion");
        dopplerStrength = builder.doubleEntry("doppler_strength", 2.5D, 0.0D, 4.0D)
                .comment("Scales the Doppler effect. 1.0 is realistic but boring; higher values are more dramatic");
        dopplerSpeedOfSound = builder.doubleEntry("doppler_speed_of_sound_blocks_per_second", 343.0D, 20.0D, 2000.0D)
                .comment("Speed of sound used for Doppler calculations, in blocks per second");
        dopplerMinPitchMultiplier = builder.doubleEntry("doppler_min_pitch_multiplier", 0.50D, 0.05D, 4.0D)
                .comment("Minimum Doppler pitch multiplier");
        dopplerMaxPitchMultiplier = builder.doubleEntry("doppler_max_pitch_multiplier", 2.00D, 0.05D, 4.0D)
                .comment("Maximum Doppler pitch multiplier");
        dopplerUpdateIntervalTicks = builder.integerEntry("doppler_update_interval_ticks", 1, 1, 20)
                .comment("How often active sound pitch is updated.");
        dopplerSmoothingTimeMs = builder.integerEntry("doppler_smoothing_time_ms", 100, 0, 2000)
                .comment("Exponential smoothing time for Doppler pitch changes, in milliseconds");
        dopplerApplyToBlockSounds = builder.booleanEntry("doppler_apply_to_block_sounds", true)
                .comment("Apply Doppler to positional block sounds");
        dopplerApplyToEntitySounds = builder.booleanEntry("doppler_apply_to_entity_sounds", true)
                .comment("Apply Doppler to positional entity sounds");
        dopplerApplyToWeatherSounds = builder.booleanEntry("doppler_apply_to_weather_sounds", true)
                .comment("Apply Doppler to positional weather sounds");
        dopplerApplyToAmbientSounds = builder.booleanEntry("doppler_apply_to_ambient_sounds", false)
                .comment("Apply Doppler to ambient sounds. Disabled by default because many ambient loops are non-positional");
        dopplerApplyToPositionalAmbientSounds = builder.booleanEntry("doppler_apply_to_positional_ambient_sounds", false)
                .comment("Apply Doppler to positional ambient loops after ambient Doppler is enabled. Keep disabled unless testing known positional machinery loops");
        dopplerApplyToSableDelegatedSounds = builder.booleanEntry("doppler_apply_to_sable_delegated_sounds", false)
                .comment("Apply manual Doppler to Sable delegated sounds. Disabled by default because Sable already writes OpenAL velocity and Doppler state");
        dopplerApplyToMusic = builder.booleanEntry("doppler_apply_to_music", false)
                .comment("Apply Doppler to music. Disabled by default");
        dopplerApplyToRecords = builder.booleanEntry("doppler_apply_to_records", false)
                .comment("Experimental: apply Doppler to music discs and records. Disabled by default; record_test modes are safer for diagnostics.");
        dopplerApplyToAeronauticsPropellers = builder.booleanEntry("doppler_apply_to_aeronautics_propellers", true)
                .comment("Apply Doppler diagnostics/pitch control to known Aeronautics propeller bearing loops when safe.");
        dopplerApplyToPositionalAmbientMachinery = builder.booleanEntry("doppler_apply_to_positional_ambient_machinery", true)
                .comment("Apply Doppler to known positional ambient machinery loops such as Aeronautics propeller bearings.");
        dopplerApplyToCrosswindWind = builder.booleanEntry("doppler_apply_to_crosswind_wind", false)
                .comment("Apply Doppler to Crosswind global wind loops. Crosswind howl is not treated as a global wind loop.");
        dopplerSoundAllowlist = builder.stringEntry("doppler_sound_allowlist", "")
                .comment("Comma-separated sound ids or class patterns that force Doppler after positional safety checks. Use sound: or class: prefixes for substring matches.");
        dopplerSoundDenylist = builder.stringEntry("doppler_sound_denylist", "")
                .comment("Comma-separated sound ids or class patterns that skip Doppler.");
        dopplerMaxListenerSpeed = builder.doubleEntry("doppler_max_listener_speed_blocks_per_second", 250.0D, 0.0D, 2000.0D)
                .comment("Listener velocity samples above this speed are treated as unreliable");
        dopplerMaxSourceSpeed = builder.doubleEntry("doppler_max_source_speed_blocks_per_second", 350.0D, 0.0D, 2000.0D)
                .comment("Source velocity samples above this speed are treated as unreliable");
        dopplerDebugLogging = builder.booleanEntry("doppler_debug_logging", false)
                .comment("Logs compact Doppler diagnostics periodically");
        propellerLongRangeEnabled = builder.booleanEntry("propeller_long_range_enabled", true)
                .comment("Extends OpenAL distance parameters for known Aeronautics propeller bearing loops.");
        propellerLongRangeProfile = builder.stringEntry("propeller_long_range_profile", "balanced")
                .comment("Long-range propeller tuning profile. Supported values: balanced, loud, legacy.");
        propellerLongRangeMaxDistance = builder.doubleEntry("propeller_long_range_max_distance", 896.0D, 1.0D, 4096.0D)
                .comment("Theoretical maximum audible distance for a reference-size/reference-RPM Aeronautics propeller.");
        propellerLongRangeMinDistance = builder.doubleEntry("propeller_long_range_min_distance", 96.0D, 1.0D, 4096.0D)
                .comment("Minimum theoretical long-range distance for eligible propellers.");
        propellerLongRangeAbsoluteCapBlocks = builder.doubleEntry("propeller_long_range_absolute_cap_blocks", 1024.0D, 1.0D, 4096.0D)
                .comment("Hard cap for computed long-range propeller distance, preserving technical 1024-block support.");
        propellerLongRangeMaxProcessingDistance = builder.doubleEntry("propeller_long_range_max_processing_distance", 1024.0D, 1.0D, 4096.0D)
                .comment("Maximum acoustic processing distance used for propeller-specific long-range processing.");
        propellerLongRangeSmallFallbackSails = builder.integerEntry("propeller_long_range_small_fallback_sails", 16, 1, 512)
                .comment("Fallback sail count for Aeronautics small propeller bearing loop when reflection cannot resolve size.");
        propellerLongRangeLargeFallbackSails = builder.integerEntry("propeller_long_range_large_fallback_sails", 48, 1, 512)
                .comment("Fallback sail count for Aeronautics large propeller bearing loop when reflection cannot resolve size.");
        propellerLongRangeSizeReferenceSails = builder.doubleEntry("propeller_long_range_size_reference_sails", 48.0D, 1.0D, 512.0D)
                .comment("Sail count that maps to the maximum size factor.");
        propellerLongRangeRpmReference = builder.doubleEntry("propeller_long_range_rpm_reference", 192.0D, 1.0D, 4096.0D)
                .comment("RPM proxy that maps to the maximum RPM factor.");
        propellerLongRangeSizeExponent = builder.doubleEntry("propeller_long_range_size_exponent", 1.15D, 0.01D, 4.0D)
                .comment("Nonlinear exponent for propeller size range scaling.");
        propellerLongRangeRpmExponent = builder.doubleEntry("propeller_long_range_rpm_exponent", 0.80D, 0.01D, 4.0D)
                .comment("Nonlinear exponent for propeller RPM range scaling.");
        propellerLongRangePitchAtReferenceRpm = builder.doubleEntry("propeller_long_range_pitch_at_reference_rpm", 1.6D, 0.01D, 8.0D)
                .comment("Pitch value treated as reference RPM when real RPM cannot be reflected.");
        propellerLongRangeReferenceDistanceFraction = builder.doubleEntry("propeller_long_range_reference_distance_fraction", 0.018D, 0.001D, 1.0D)
                .comment("Reference distance as a fraction of computed propeller range.");
        propellerLongRangeReferenceDistanceMin = builder.doubleEntry("propeller_long_range_reference_distance_min", 8.0D, 1.0D, 512.0D)
                .comment("Minimum OpenAL reference distance for long-range propellers.");
        propellerLongRangeReferenceDistanceMax = builder.doubleEntry("propeller_long_range_reference_distance_max", 18.0D, 1.0D, 512.0D)
                .comment("Maximum OpenAL reference distance for long-range propellers.");
        propellerLongRangeRolloffFactor = builder.doubleEntry("propeller_long_range_rolloff_factor", 1.35D, 0.5D, 4.0D)
                .comment("OpenAL rolloff factor for long-range propeller sources.");
        propellerLongRangeApplyInSafeMode = builder.booleanEntry("propeller_long_range_apply_in_safe_mode", true)
                .comment("Allows propeller_safe mode to keep the non-destructive source distance extension while bypassing acoustic/Doppler processing.");
        propellerLongRangeApplyToCrosswindVehiclePropellers = builder.booleanEntry("propeller_long_range_apply_to_crosswind_vehicle_propellers", false)
                .comment("Opt-in extension for Crosswind vehicle propeller loops. Disabled by default to keep this pass focused on Aeronautics bearings.");
        propellerLongRangeCloseEndBlocks = builder.doubleEntry("propeller_long_range_close_end_blocks", 48.0D, 1.0D, 4096.0D)
                .comment("Distance where propeller gameplay gain starts fading from near-field strength.");
        propellerLongRangeMidEndBlocks = builder.doubleEntry("propeller_long_range_mid_end_blocks", 192.0D, 1.0D, 4096.0D)
                .comment("Distance where propeller gameplay gain reaches the configured mid-distance gain.");
        propellerLongRangeMidGain = builder.doubleEntry("propeller_long_range_mid_gain", 0.22D, 0.0D, 1.0D)
                .comment("Extra direct gain multiplier at the mid-distance propeller gameplay point.");
        propellerLongRangeFarMinGain = builder.doubleEntry("propeller_long_range_far_min_gain", 0.035D, 0.0D, 1.0D)
                .comment("Minimum extra direct gain multiplier at the far end of propeller range.");
        propellerFarFieldEnabled = builder.booleanEntry("propeller_far_field_enabled", true)
                .comment("Applies extra distant filtering/compression to long-range propeller sources.");
        propellerFarFieldStartFraction = builder.doubleEntry("propeller_far_field_start_fraction", 0.25D, 0.0D, 1.0D)
                .comment("Legacy distance fraction where propeller far-field filtering begins.");
        propellerFarFieldStartBlocks = builder.doubleEntry("propeller_far_field_start_blocks", 72.0D, 0.0D, 4096.0D)
                .comment("Absolute distance where balanced propeller far-field muffling starts.");
        propellerFarFieldFullBlocks = builder.doubleEntry("propeller_far_field_full_blocks", 280.0D, 0.0D, 4096.0D)
                .comment("Absolute distance where balanced propeller far-field muffling reaches full strength.");
        propellerFarFieldStartRatio = builder.doubleEntry("propeller_far_field_start_ratio", 0.08D, 0.0D, 1.0D)
                .comment("Range ratio where propeller far-field muffling starts.");
        propellerFarFieldFullRatio = builder.doubleEntry("propeller_far_field_full_ratio", 0.35D, 0.0D, 1.0D)
                .comment("Range ratio where propeller far-field muffling reaches full strength.");
        propellerFarFieldDirectCutoffAtMax = builder.doubleEntry("propeller_far_field_direct_cutoff_at_max", 0.35D, 0.0D, 1.0D)
                .comment("Legacy direct high-frequency cutoff multiplier at max propeller range.");
        propellerFarFieldDirectGainAtMax = builder.doubleEntry("propeller_far_field_direct_gain_at_max", 0.72D, 0.0D, 1.0D)
                .comment("Legacy direct gain multiplier at max propeller range.");
        propellerFarCutoffMin = builder.doubleEntry("propeller_far_cutoff_min", 0.28D, 0.0D, 1.0D)
                .comment("Minimum direct high-frequency cutoff multiplier for balanced far propellers.");
        propellerFarFieldAirAbsorptionBonus = builder.doubleEntry("propeller_far_field_air_absorption_bonus", 1.75D, 0.0D, 5.0D)
                .comment("Additional per-source air absorption at max propeller far-field distance.");
        propellerFarCompensateHighSourceVolume = builder.booleanEntry("propeller_far_compensate_high_source_volume", true)
                .comment("Dampen excessive raw propeller source volume at mid/far distances.");
        propellerFarSourceVolumeCompensationStrength = builder.doubleEntry("propeller_far_source_volume_compensation_strength", 1.0D, 0.0D, 1.0D)
                .comment("Blend strength for high source-volume compensation at distance.");
        propellerFarFieldSkipReverbAfterDistance = builder.doubleEntry("propeller_far_field_skip_reverb_after_distance", 384.0D, 0.0D, 4096.0D)
                .comment("Distance after which propeller processing skips expensive reverb rays and uses direct/far-field filtering only.");
        distantHorizonsFarPropellerOcclusionEnabled = builder.booleanEntry("distant_horizons_far_propeller_occlusion_enabled", false)
                .comment("Uses Distant Horizons LOD terrain to softly muffle far eligible propellers behind distant terrain.");
        distantHorizonsFarPropellerMinDistance = builder.doubleEntry("distant_horizons_far_propeller_min_distance", 192.0D, 0.0D, 4096.0D)
                .comment("Minimum listener-to-propeller distance before Distant Horizons terrain occlusion may query.");
        distantHorizonsFarPropellerProbeIntervalTicks = builder.integerEntry("distant_horizons_far_propeller_probe_interval_ticks", 20, 10, 200)
                .comment("Tick interval for Distant Horizons terrain probes per propeller source.");
        distantHorizonsFarPropellerMaxStrength = builder.doubleEntry("distant_horizons_far_propeller_max_strength", 0.80D, 0.0D, 1.0D)
                .comment("Maximum DH terrain occlusion blend strength for far propellers.");
        distantHorizonsFarPropellerGainAtFullOcclusion = builder.doubleEntry("distant_horizons_far_propeller_gain_at_full_occlusion", 0.55D, 0.0D, 1.0D)
                .comment("Direct gain multiplier when DH terrain occlusion reaches full configured strength.");
        distantHorizonsFarPropellerCutoffAtFullOcclusion = builder.doubleEntry("distant_horizons_far_propeller_cutoff_at_full_occlusion", 0.45D, 0.0D, 1.0D)
                .comment("Direct high-frequency cutoff multiplier when DH terrain occlusion reaches full configured strength.");
        distantHorizonsFarPropellerMaxRayLength = builder.integerEntry("distant_horizons_far_propeller_max_ray_length", 2048, 1, 8192)
                .comment("Maximum Distant Horizons terrain ray length in blocks.");
        distantHorizonsFarPropellerDebugLogging = builder.booleanEntry("distant_horizons_far_propeller_debug_logging", false)
                .comment("Logs compact Distant Horizons far propeller occlusion probe diagnostics.");
        soundPhysicsMaxSoundStartsPerTick = builder.integerEntry("sound_physics_max_sound_starts_per_tick", 32, 0, Integer.MAX_VALUE)
                .comment("Maximum acoustic sound starts processed per tick. 0 disables throttling.");
        soundPhysicsMaxDebugRaysPerTick = builder.integerEntry("sound_physics_max_debug_rays_per_tick", 4096, 0, Integer.MAX_VALUE)
                .comment("Maximum debug rays queued per tick. 0 disables throttling.");
        soundPhysicsImpactBurstDedupeEnabled = builder.booleanEntry("sound_physics_impact_burst_dedupe_enabled", true)
                .comment("Coalesce repeated same-sound same-position impact bursts for a few ticks.");
        soundPhysicsImpactBurstDedupeApplyToTickableSounds = builder.booleanEntry("sound_physics_impact_burst_dedupe_apply_to_tickable_sounds", false)
                .comment("Apply impact burst dedupe to tickable/continuous sounds. Disabled by default so machinery loops are not suppressed.");
        soundPhysicsImpactBurstDedupeRadius = builder.doubleEntry("sound_physics_impact_burst_dedupe_radius", 1.5D, 0.1D, 32.0D)
                .comment("Position radius used by impact burst dedupe.");
        soundPhysicsImpactBurstDedupeTicks = builder.integerEntry("sound_physics_impact_burst_dedupe_ticks", 2, 0, 40)
                .comment("Tick window used by impact burst dedupe. 0 disables the tick window.");
        unsafeLevelAccess = builder.booleanEntry("unsafe_level_access", false)
                .comment(
                        "Disable level clone and cache. This will fall back to original main thread access.",
                        "WARNING! Enabling this will cause instability and issues with other mods."
                );
        levelCloneRange = builder.integerEntry("level_clone_range", 4, 2, 16)
                .comment("The radius of chunks to clone for level access");
        levelCloneMaxRetainTicks = builder.integerEntry("level_clone_max_retain_ticks", 20, 1, Integer.MAX_VALUE,
                "The maximum number of ticks to retain the cloned level in the cache"
        );
        levelCloneMaxRetainBlockDistance = builder.integerEntry("level_clone_max_retain_block_distance", 16, 1, Integer.MAX_VALUE,
                "The maximum distance a player can move from the cloned origin before invalidation"
        );


        debugLogging = builder.booleanEntry("debug_logging", false)
                .comment("Enables debug logging");
        occlusionLogging = builder.booleanEntry("occlusion_logging", false)
                .comment("Provides more information about occlusion in the logs");
        environmentLogging = builder.booleanEntry("environment_logging", false)
                .comment("Provides more information about the environment calculation in the logs");
        performanceLogging = builder.booleanEntry("performance_logging", false)
                .comment("Provides more information about how long computations take");
        renderSoundBounces = builder.booleanEntry("render_sound_bounces", false)
                .comment("If enabled, the path of the sound will be rendered in game");
        renderOcclusion = builder.booleanEntry("render_occlusion", false)
                .comment("If enabled, occlusion will be visualized in game");

        simpleVoiceChatIntegration = builder.booleanEntry("simple_voice_chat_integration", true)
                .comment("Enables/Disables sound effects for Simple Voice Chat audio");
        hearSelf = builder.booleanEntry("simple_voice_chat_hear_self", false)
                .comment("Enables/Disables hearing your own echo with Simple Voice Chat");
        reloadSoundPolicyMatchers();
    }

    public void reloadClient() {
        Loggers.log("Reloading reverb parameters");
        reloadSoundPolicyMatchers();
        SoundPhysics.syncReverbParams();
        DopplerEngine.onConfigReload();
        PropellerLongRangeAudio.onConfigReload();
    }

    public void reloadSoundPolicyMatchers() {
        soundPhysicsSoundAllowMatcher.reload(soundPhysicsSoundAllowlist.get());
        soundPhysicsSoundDenyMatcher.reload(soundPhysicsSoundDenylist.get());
        dopplerSoundAllowMatcher.reload(dopplerSoundAllowlist.get());
        dopplerSoundDenyMatcher.reload(dopplerSoundDenylist.get());
    }

    public SoundPhysicsSoundPolicy.SoundMatcher soundPhysicsSoundAllowMatcher() {
        return soundPhysicsSoundAllowMatcher.get(soundPhysicsSoundAllowlist.get());
    }

    public SoundPhysicsSoundPolicy.SoundMatcher soundPhysicsSoundDenyMatcher() {
        return soundPhysicsSoundDenyMatcher.get(soundPhysicsSoundDenylist.get());
    }

    public SoundPhysicsSoundPolicy.SoundMatcher dopplerSoundAllowMatcher() {
        return dopplerSoundAllowMatcher.get(dopplerSoundAllowlist.get());
    }

    public SoundPhysicsSoundPolicy.SoundMatcher dopplerSoundDenyMatcher() {
        return dopplerSoundDenyMatcher.get(dopplerSoundDenylist.get());
    }

    private static final class CachedSoundMatcher {
        private volatile String rawList = "";
        private volatile SoundPhysicsSoundPolicy.SoundMatcher matcher = SoundPhysicsSoundPolicy.SoundMatcher.empty();

        SoundPhysicsSoundPolicy.SoundMatcher get(String currentRawList) {
            String normalizedRawList = normalizeRawList(currentRawList);
            if (!Objects.equals(rawList, normalizedRawList)) {
                reload(normalizedRawList);
            }
            return matcher;
        }

        synchronized void reload(String currentRawList) {
            String normalizedRawList = normalizeRawList(currentRawList);
            if (Objects.equals(rawList, normalizedRawList)) {
                return;
            }
            matcher = SoundPhysicsSoundPolicy.SoundMatcher.compile(normalizedRawList);
            rawList = normalizedRawList;
        }

        private static String normalizeRawList(String rawList) {
            return rawList == null ? "" : rawList;
        }
    }

}
