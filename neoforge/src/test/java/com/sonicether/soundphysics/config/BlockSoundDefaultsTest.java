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
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.blocksound.BlockDefinition;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
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
        Properties v3ReflectivityProperties = loadResourceProperties(ReflectivityConfig.V3_MATERIAL_DEFAULTS_RESOURCE);
        Properties v3OcclusionProperties = loadResourceProperties(OcclusionConfig.V3_MATERIAL_DEFAULTS_RESOURCE);

        assertEquals(83, reflectivityProperties.stringPropertyNames().size());
        assertEquals(83, occlusionProperties.stringPropertyNames().size());
        assertEquals(680, v3ReflectivityProperties.stringPropertyNames().size());
        assertEquals(680, v3OcclusionProperties.stringPropertyNames().size());
    }

    @Test
    void v3DefaultsDoNotOverlapCreateAeronauticsDefaults() throws IOException {
        Properties createReflectivityProperties = loadResourceProperties(ReflectivityConfig.CREATE_AERONAUTICS_DEFAULTS_RESOURCE);
        Properties createOcclusionProperties = loadResourceProperties(OcclusionConfig.CREATE_AERONAUTICS_DEFAULTS_RESOURCE);
        Properties v3ReflectivityProperties = loadResourceProperties(ReflectivityConfig.V3_MATERIAL_DEFAULTS_RESOURCE);
        Properties v3OcclusionProperties = loadResourceProperties(OcclusionConfig.V3_MATERIAL_DEFAULTS_RESOURCE);

        Set<String> createReflectivityKeys = createReflectivityProperties.stringPropertyNames();
        Set<String> createOcclusionKeys = createOcclusionProperties.stringPropertyNames();

        assertEquals(0, v3ReflectivityProperties.stringPropertyNames().stream()
                .filter(createReflectivityKeys::contains)
                .count());
        assertEquals(0, v3OcclusionProperties.stringPropertyNames().stream()
                .filter(createOcclusionKeys::contains)
                .count());
    }

    @Test
    void v3CommentedCandidatesRemainInactive() throws IOException {
        String reflectivityText = loadResourceText(ReflectivityConfig.V3_MATERIAL_DEFAULTS_RESOURCE);
        String occlusionText = loadResourceText(OcclusionConfig.V3_MATERIAL_DEFAULTS_RESOURCE);
        Properties reflectivityProperties = loadResourceProperties(ReflectivityConfig.V3_MATERIAL_DEFAULTS_RESOURCE);
        Properties occlusionProperties = loadResourceProperties(OcclusionConfig.V3_MATERIAL_DEFAULTS_RESOURCE);

        assertEquals(8, countCommentedCandidates(reflectivityText));
        assertEquals(8, countCommentedCandidates(occlusionText));
        assertFalse(reflectivityProperties.containsKey("create_connected:copycat_beam"));
        assertFalse(reflectivityProperties.containsKey("the_bumblezone:pollen_puff"));
        assertFalse(occlusionProperties.containsKey("create_connected:copycat_beam"));
        assertFalse(occlusionProperties.containsKey("the_bumblezone:pollen_puff"));
    }

    @Test
    void curatedTagDefaultsAreLoaded() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        Map<String, Float> reflectivity = byConfigString(reflectivityConfig.getBlockDefinitions());
        Map<String, Float> occlusion = byConfigString(occlusionConfig.getBlockDefinitions());

        assertEquals(0.1F, reflectivity.get("#aeronautics:envelope"), 0.0001F);
        assertEquals(1.5F, occlusion.get("#aeronautics:envelope"), 0.0001F);
        assertFalse(reflectivity.containsKey("#create:windmill_sails"));
        assertFalse(occlusion.containsKey("#create:windmill_sails"));
    }

    @Test
    void vanillaWoolResolvesThroughWoolSoundTypeDefaults() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        assertEquals(0.1F, reflectivityConfig.getBlockDefinitionValue(Blocks.WHITE_WOOL.defaultBlockState()), 0.0001F);
        assertEquals(1.5F, occlusionConfig.getBlockDefinitionValue(Blocks.WHITE_WOOL.defaultBlockState()), 0.0001F);
    }

    @Test
    void blockAcousticDefaultsDoNotContainBelowMinimumValues() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        assertNoBelowMinimumValues("reflectivity", byConfigString(reflectivityConfig.getBlockDefinitions()));
        assertNoBelowMinimumValues("occlusion", byConfigString(occlusionConfig.getBlockDefinitions()));
    }

    @Test
    void absentOptionalBlockIdsDoNotBecomeAirDefaults() {
        ReflectivityConfig reflectivityConfig = new ReflectivityConfig(tempDir.resolve("reflectivity.properties"));
        OcclusionConfig occlusionConfig = new OcclusionConfig(tempDir.resolve("occlusion.properties"));

        Map<String, Float> reflectivity = byConfigString(reflectivityConfig.getBlockDefinitions());
        Map<String, Float> occlusion = byConfigString(occlusionConfig.getBlockDefinitions());

        assertFalse(reflectivity.containsKey("minecraft:air"));
        assertFalse(occlusion.containsKey("minecraft:air"));
        assertFalse(reflectivity.containsKey("aether:aerogel"));
        assertFalse(occlusion.containsKey("aether:aerogel"));
    }

    private Properties loadResourceProperties(String resourcePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = BlockSoundDefaultsTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource " + resourcePath);
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private String loadResourceText(String resourcePath) throws IOException {
        try (InputStream inputStream = BlockSoundDefaultsTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource " + resourcePath);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private long countCommentedCandidates(String text) {
        return text.lines()
                .filter(line -> line.matches("^#\\s+[^#].*\\\\:.*=.*$"))
                .count();
    }

    private Map<String, Float> byConfigString(Map<BlockDefinition, Float> definitions) {
        return definitions.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getConfigString(), Map.Entry::getValue));
    }

    private void assertNoBelowMinimumValues(String configName, Map<String, Float> definitions) {
        Map<String, Float> belowMinimum = definitions.entrySet().stream()
                .filter(entry -> entry.getValue() >= 0F && entry.getValue() < 0.01F)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of(), belowMinimum, configName + " contains values below 0.01");
    }

}
