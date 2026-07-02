package com.sonicether.soundphysics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.blocksound.BlockDefinition;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockSoundDefaultsTest {

    @TempDir
    Path tempDir;

    @Nullable
    private SoundPhysicsConfig previousConfig;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void captureConfig() {
        previousConfig = SoundPhysicsMod.CONFIG;
        SoundPhysicsMod.CONFIG = ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    @AfterEach
    void restoreConfig() {
        SoundPhysicsMod.CONFIG = previousConfig;
    }

    @Test
    void curatedResourceDefaultsArePackaged() throws IOException {
        Properties reflectivityProperties = loadResourceProperties(ReflectivityConfig.CREATE_AERONAUTICS_DEFAULTS_RESOURCE);
        Properties occlusionProperties = loadResourceProperties(OcclusionConfig.CREATE_AERONAUTICS_DEFAULTS_RESOURCE);

        assertEquals(84, reflectivityProperties.stringPropertyNames().size());
        assertEquals(84, occlusionProperties.stringPropertyNames().size());
    }

    @Test
    void curatedTagDefaultsAreLoaded() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        Map<String, Float> reflectivity = byConfigString(reflectivityConfig.getBlockDefinitions());
        Map<String, Float> occlusion = byConfigString(occlusionConfig.getBlockDefinitions());

        assertEquals(0.1F, reflectivity.get("#aeronautics:envelope"), 0.0001F);
        assertEquals(1.5F, occlusion.get("#aeronautics:envelope"), 0.0001F);
        assertEquals(0.2F, reflectivity.get("#create:windmill_sails"), 0.0001F);
        assertEquals(0.1F, occlusion.get("#create:windmill_sails"), 0.0001F);
    }

    @Test
    void absentOptionalBlockIdsDoNotBecomeAirDefaults() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        assertFalse(byConfigString(reflectivityConfig.getBlockDefinitions()).containsKey("minecraft:air"));
        assertFalse(byConfigString(occlusionConfig.getBlockDefinitions()).containsKey("minecraft:air"));
    }

    private Properties loadResourceProperties(String resourcePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = BlockSoundDefaultsTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource " + resourcePath);
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private Map<String, Float> byConfigString(Map<BlockDefinition, Float> definitions) {
        return definitions.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getConfigString(), Map.Entry::getValue));
    }

}
