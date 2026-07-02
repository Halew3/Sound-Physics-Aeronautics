package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigDiagnosticsTest {

    @TempDir
    Path tempDir;

    @Nullable
    private SoundPhysicsConfig previousConfig;

    @BeforeEach
    void captureConfig() {
        previousConfig = SoundPhysicsMod.CONFIG;
        DiagnosticRuntimeOverrides.clear();
    }

    @AfterEach
    void restoreConfig() {
        SoundPhysicsMod.CONFIG = previousConfig;
        DiagnosticRuntimeOverrides.clear();
    }

    @Test
    void configPathLinesIncludeActiveSoundPhysicsPathAndValues() throws IOException {
        Path gameDir = tempDir.resolve("game");
        Path configFolder = gameDir.resolve("config");
        Files.createDirectories(ConfigDiagnostics.configDirectory(configFolder));
        Files.createFile(ConfigDiagnostics.soundPhysicsConfigPath(configFolder));
        SoundPhysicsMod.CONFIG = config(configFolder);

        List<String> lines = ConfigDiagnostics.configPathLines(gameDir, configFolder);
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("Game directory: " + gameDir.toAbsolutePath()));
        assertTrue(joined.contains("Config folder: " + configFolder.toAbsolutePath()));
        assertTrue(joined.contains("soundphysics.properties: " + ConfigDiagnostics.soundPhysicsConfigPath(configFolder).toAbsolutePath()));
        assertTrue(joined.contains("soundphysics.properties exists: true"));
        assertTrue(joined.contains("soundphysics.properties last modified: "));
        assertTrue(joined.contains("enabled=true"));
        assertTrue(joined.contains("sable_acoustics_enabled=true"));
        assertTrue(joined.contains("adaptive_reflection_budget_enabled=true"));
        assertTrue(joined.contains("doppler_enabled=true"));
    }

    @Test
    void configDumpAndExampleIncludeNewPolicyValues() throws IOException {
        Path gameDir = tempDir.resolve("game_dump");
        Path configFolder = gameDir.resolve("config");
        Files.createDirectories(ConfigDiagnostics.configDirectory(configFolder));
        SoundPhysicsMod.CONFIG = config(configFolder);

        String dump = String.join("\n", ConfigDiagnostics.configDumpLines(gameDir, configFolder));
        assertTrue(dump.contains("openal_error_checks=false"));
        assertTrue(dump.contains("adaptive_reflection_budget_enabled=true"));
        assertTrue(dump.contains("sound_physics_apply_to_records=false"));
        assertTrue(dump.contains("sound_physics_apply_to_positional_ambient_machinery=true"));
        assertTrue(dump.contains("doppler_apply_to_positional_ambient_machinery=true"));
        assertTrue(dump.contains("doppler_apply_to_aeronautics_propellers=true"));
        assertTrue(dump.contains("propeller_long_range_enabled=true"));
        assertTrue(dump.contains("propeller_long_range_profile=balanced"));
        assertTrue(dump.contains("propeller_long_range_max_distance=896.0"));
        assertTrue(dump.contains("propeller_long_range_absolute_cap_blocks=1024.0"));
        assertTrue(dump.contains("propeller_long_range_mid_gain=0.22"));
        assertTrue(dump.contains("propeller_far_field_enabled=true"));
        assertTrue(dump.contains("propeller_far_field_start_blocks=72.0"));
        assertTrue(dump.contains("propeller_far_cutoff_min=0.28"));
        assertTrue(dump.contains("propeller_far_compensate_high_source_volume=true"));
        assertTrue(dump.contains("sound_physics_max_sound_starts_per_tick=32"));
        assertTrue(dump.contains("sound_physics_impact_burst_dedupe_apply_to_tickable_sounds=false"));

        Path example = ConfigDiagnostics.exportExample(configFolder);
        String exampleText = Files.readString(example);
        assertTrue(exampleText.contains("openal_error_checks=false"));
        assertTrue(exampleText.contains("adaptive_reflection_budget_enabled=true"));
        assertTrue(exampleText.contains("sound_physics_apply_to_crosswind_wind=false"));
        assertTrue(exampleText.contains("doppler_apply_to_crosswind_wind=false"));
        assertTrue(exampleText.contains("doppler_apply_to_aeronautics_propellers=true"));
        assertTrue(exampleText.contains("propeller_long_range_profile=balanced"));
        assertTrue(exampleText.contains("propeller_long_range_apply_to_crosswind_vehicle_propellers=false"));
        assertTrue(exampleText.contains("propeller_long_range_far_min_gain=0.035"));
        assertTrue(exampleText.contains("propeller_far_source_volume_compensation_strength=1.0"));
        assertTrue(exampleText.contains("propeller_far_field_skip_reverb_after_distance=384.0"));
        assertTrue(ConfigDiagnostics.criticalValuesSummary().contains("openal_error_checks=false"));
        assertTrue(ConfigDiagnostics.criticalValuesSummary().contains("adaptive_reflection_budget_enabled=true"));
    }

    private SoundPhysicsConfig config(Path configFolder) {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(ConfigDiagnostics.soundPhysicsConfigPath(configFolder))
                .saveAfterBuild(false)
                .build();
    }

}
