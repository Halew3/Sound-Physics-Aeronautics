package com.sonicether.soundphysics.config;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.integration.voicechat.AudioChannel;
import de.maxhenkel.configbuilder.CommentedProperties;
import de.maxhenkel.configbuilder.CommentedPropertyConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SoundRateConfig extends CommentedPropertyConfig {

    public static final String CURATED_DEFAULTS_RESOURCE = "assets/sound_physics_remastered/sound_rate_defaults/curated_sound_rates.properties";

    private Map<ResourceLocation, Integer> soundRateConfig;

    public SoundRateConfig(Path path) {
        super(new CommentedProperties(false));
        this.path = path;
        reload();
    }

    @Override
    public void load() throws IOException {
        super.load();

        Map<ResourceLocation, Integer> map = createDefaultMap();

        for (String key : properties.keySet()) {
            String valueString = properties.get(key);
            if (valueString == null) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(valueString);
            } catch (NumberFormatException ignored) {
                try {
                    boolean enabled = Boolean.parseBoolean(valueString); // Convert allowedSounds to sound rate
                    value = enabled ? -1 : 0;
                } catch (Exception e) {
                    Loggers.warn("Failed to set sound rate entry {}", key);
                    continue;
                }
            }
            ResourceLocation resourceLocation = ResourceLocation.tryParse(key);
            if (resourceLocation == null) {
                Loggers.warn("Failed to set sound rate entry {}", key);
                continue;
            }

            if (!AudioChannel.isVoicechatSound(resourceLocation)) {
                logIfUnknownSound(resourceLocation);
            }

            map.put(resourceLocation, value);
        }
        soundRateConfig = ConfigUtils.sortMap(map);

        saveSync();
    }

    private void logIfUnknownSound(ResourceLocation resourceLocation) {
        try {
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(resourceLocation);
            if (soundEvent == null) {
                Loggers.log("Unknown sound in sound rate config: {}", resourceLocation);
            }
        } catch (Exception e) {
            Loggers.warn("Failed to parse sound rate entry {}", resourceLocation, e);
        }
    }

    @Override
    public void saveSync() {
        properties.clear();

        properties.addHeaderComment("Max sounds per tick.");
        properties.addHeaderComment("Set to '-1' for an unlimited number of sounds per tick processed.");
        properties.addHeaderComment("Set to '0' to disable sound physics for that sound.");
        properties.addHeaderComment("Set to '>=1' to configure the maximum number of sounds per tick processed.");
        properties.addHeaderComment("This can help prevent lag when some mod or mechanism produces hundreds of sounds per tick.");

        for (Map.Entry<ResourceLocation, Integer> entry : soundRateConfig.entrySet()) {
            properties.set(entry.getKey().toString(), String.valueOf(entry.getValue()));
        }

        super.saveSync();
    }

    public Map<ResourceLocation, Integer> getSoundRateConfig() {
        return soundRateConfig;
    }

    public Integer getMaxCount(ResourceLocation soundEvent) {
        int count = soundRateConfig.getOrDefault(soundEvent, -1);
        return count <= -1 ? Integer.MAX_VALUE : count;
    }

    public Map<ResourceLocation, Integer> createDefaultMap() {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        for (SoundEvent event : BuiltInRegistries.SOUND_EVENT) {
            map.put(event.getLocation(), -1);
        }

        map.put(SoundEvents.WEATHER_RAIN.getLocation(), 0);
        map.put(SoundEvents.WEATHER_RAIN_ABOVE.getLocation(), 0);
        map.put(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation(), 0);
        SoundEvents.GOAT_HORN_SOUND_VARIANTS.forEach(r -> map.put(r.key().location(), 0));

        addDefaultsFromResource(map, CURATED_DEFAULTS_RESOURCE);

        return map;
    }

    private void addDefaultsFromResource(Map<ResourceLocation, Integer> map, String resourcePath) {
        Properties resourceProperties = new Properties();
        try (InputStream inputStream = SoundRateConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                Loggers.warn("Sound rate defaults resource {} not found", resourcePath);
                return;
            }
            resourceProperties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Loggers.warn("Failed to load sound rate defaults resource {}", resourcePath, e);
            return;
        }

        for (String key : resourceProperties.stringPropertyNames()) {
            String valueString = resourceProperties.getProperty(key);
            int value;
            try {
                value = Integer.parseInt(valueString.trim());
            } catch (NumberFormatException e) {
                Loggers.warn("Failed to parse value of {} in sound rate defaults resource {}", key, resourcePath);
                continue;
            }

            ResourceLocation resourceLocation = ResourceLocation.tryParse(key);
            if (resourceLocation == null) {
                Loggers.warn("Sound rate default {} from resource {} is not a valid sound ID", key, resourcePath);
                continue;
            }
            if (BuiltInRegistries.SOUND_EVENT.getOptional(resourceLocation).isEmpty()) {
                continue;
            }

            map.put(resourceLocation, value);
        }
    }

}
