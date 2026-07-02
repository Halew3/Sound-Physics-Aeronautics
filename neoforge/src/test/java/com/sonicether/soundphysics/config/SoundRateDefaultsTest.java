package com.sonicether.soundphysics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundRateDefaultsTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void curatedResourceDefaultsArePackaged() throws IOException {
        Properties properties = loadResourceProperties();

        assertEquals(388, properties.stringPropertyNames().size());
    }

    @Test
    void curatedVanillaDefaultsAreApplied() {
        SoundRateConfig config = new SoundRateConfig(tempDir.resolve("sound_rates.properties"));

        assertEquals(0, config.getMaxCount(id("music.menu")));
        assertEquals(1, config.getMaxCount(id("ambient.cave")));
        assertEquals(32, config.getMaxCount(id("block.note_block.pling")));
    }

    @Test
    void curatedDefaultsOverrideHardcodedVanillaDefaults() {
        SoundRateConfig config = new SoundRateConfig(tempDir.resolve("sound_rates.properties"));

        assertEquals(1, config.getMaxCount(id("weather.rain")));
        assertEquals(1, config.getMaxCount(id("weather.rain.above")));
        assertEquals(1, config.getMaxCount(id("entity.lightning_bolt.thunder")));
    }

    @Test
    void absentOptionalModSoundsAreNotAddedToDefaults() {
        SoundRateConfig config = new SoundRateConfig(tempDir.resolve("sound_rates.properties"));

        assertFalse(config.getSoundRateConfig().containsKey(ResourceLocation.fromNamespaceAndPath("create", "cogs")));
        assertFalse(config.getSoundRateConfig().containsKey(ResourceLocation.fromNamespaceAndPath("aeronautics", "block.propeller_bearing.large_loop")));
    }

    @Test
    void userConfigOverridesCuratedDefaults() throws IOException {
        Path path = tempDir.resolve("sound_rates.properties");
        Files.writeString(path, "minecraft\\:weather.rain=5\n", StandardCharsets.UTF_8);

        SoundRateConfig config = new SoundRateConfig(path);

        assertEquals(5, config.getMaxCount(id("weather.rain")));
    }

    private Properties loadResourceProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = SoundRateDefaultsTest.class.getClassLoader().getResourceAsStream(SoundRateConfig.CURATED_DEFAULTS_RESOURCE)) {
            assertNotNull(inputStream, "Missing resource " + SoundRateConfig.CURATED_DEFAULTS_RESOURCE);
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

}
