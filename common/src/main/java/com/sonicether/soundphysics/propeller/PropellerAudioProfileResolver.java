package com.sonicether.soundphysics.propeller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class PropellerAudioProfileResolver {

    private static final Map<Class<?>, ClassAccess> CLASS_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final List<String> SAIL_FIELD_NAMES = List.of(
            "totalSailPower",
            "sails",
            "sailCount",
            "bladeCount",
            "propellerSize"
    );
    private static final List<String> RPM_FIELD_NAMES = List.of(
            "rpm",
            "speed",
            "angularSpeed",
            "rotationSpeed"
    );

    private PropellerAudioProfileResolver() {
    }

    public static PropellerAudioProfile resolve(
            @Nullable SoundInstance soundInstance,
            @Nullable ResourceLocation soundId,
            float pitch,
            float volume
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return fallbackProfile(soundId, 1.6F, 16, 192.0D, "config_uninitialized", "config_uninitialized");
        }

        SailResolution sails = resolveSailCount(soundInstance, soundId, config);
        RpmResolution rpm = resolveRpm(soundInstance, pitch, volume, config);
        return profile(sails.count(), rpm.rpm(), sails.source(), rpm.source(), config);
    }

    public static PropellerAudioProfile fallbackForContext(@Nullable ResourceLocation soundId) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return fallbackProfile(soundId, 1.6F, 16, 192.0D, "config_uninitialized", "config_uninitialized");
        }
        int sails = fallbackSails(soundId, config);
        return profile(sails, config.propellerLongRangeRpmReference.get(), "sound_id_fallback", "reference_fallback", config);
    }

    public static PropellerAudioProfile profile(
            int sailCount,
            double rpm,
            String sizeSource,
            String rpmSource,
            SoundPhysicsConfig config
    ) {
        double sizeReference = PropellerLongRangeTuning.sizeReference(config);
        double rpmReference = PropellerLongRangeTuning.rpmReference(config);
        double sizeBase = Mth.clamp(sailCount / sizeReference, 0.0D, 1.0D);
        double rpmBase = Mth.clamp(rpm / rpmReference, 0.0D, 1.0D);
        double sizeFactor = Math.pow(sizeBase, PropellerLongRangeTuning.sizeExponent(config));
        double rpmFactor = Math.pow(rpmBase, PropellerLongRangeTuning.rpmExponent(config));
        double minDistance = PropellerLongRangeTuning.minDistance(config);
        double maxDistance = Math.max(minDistance, PropellerLongRangeTuning.maxDistance(config));
        double cap = Math.max(minDistance, PropellerLongRangeTuning.absoluteCap(config));
        double computed = Math.min(cap, minDistance + (maxDistance - minDistance) * sizeFactor * rpmFactor);
        return new PropellerAudioProfile(
                Math.max(0, sailCount),
                Math.max(0.0D, rpm),
                sizeFactor,
                rpmFactor,
                computed,
                sizeSource,
                rpmSource
        );
    }

    public static void clearReflectionCache() {
        CLASS_ACCESS_CACHE.clear();
    }

    private static PropellerAudioProfile fallbackProfile(
            @Nullable ResourceLocation soundId,
            float pitch,
            int defaultSails,
            double defaultRpm,
            String sizeSource,
            String rpmSource
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            double sizeFactor = Math.pow(Mth.clamp(defaultSails / 48.0D, 0.0D, 1.0D), 1.15D);
            double rpmFactor = Math.pow(Mth.clamp(defaultRpm / 192.0D, 0.0D, 1.0D), 0.80D);
            double computed = 96.0D + (896.0D - 96.0D) * sizeFactor * rpmFactor;
            return new PropellerAudioProfile(defaultSails, defaultRpm, sizeFactor, rpmFactor, computed, sizeSource, rpmSource);
        }
        return profile(fallbackSails(soundId, config), pitchProxyRpm(pitch, config), sizeSource, rpmSource, config);
    }

    private static SailResolution resolveSailCount(@Nullable SoundInstance soundInstance, @Nullable ResourceLocation soundId, SoundPhysicsConfig config) {
        if (soundInstance != null) {
            Object be = readField(soundInstance, "be");
            Number totalSailPower = readNumber(be, "totalSailPower");
            if (totalSailPower != null) {
                return new SailResolution((int) Math.round(totalSailPower.doubleValue()), "reflection:be.totalSailPower");
            }

            Integer sailPositions = resolveCollectionSize(be, "sailPositions");
            if (sailPositions != null) {
                return new SailResolution(sailPositions, "reflection:be.sailPositions");
            }

            Integer layerSails = resolveLayerProxySails(be);
            if (layerSails != null) {
                return new SailResolution(layerSails, "reflection:be.behaviour.layers");
            }

            Integer direct = resolveNamedNumber(soundInstance, SAIL_FIELD_NAMES);
            if (direct != null) {
                return new SailResolution(direct, "reflection:sound_field");
            }
            direct = resolveNamedNumber(be, SAIL_FIELD_NAMES);
            if (direct != null) {
                return new SailResolution(direct, "reflection:be_field");
            }
        }
        return new SailResolution(fallbackSails(soundId, config), "sound_id_fallback");
    }

    private static RpmResolution resolveRpm(@Nullable SoundInstance soundInstance, float pitch, float volume, SoundPhysicsConfig config) {
        if (soundInstance != null) {
            Object be = readField(soundInstance, "be");
            Number angularSpeed = invokeNumber(be, "getAngularSpeed");
            if (angularSpeed != null) {
                double rpm = Math.abs(angularSpeed.doubleValue()) / 64.0D * config.propellerLongRangeRpmReference.get();
                return new RpmResolution(rpm, "reflection:be.getAngularSpeed");
            }

            Number rotationSpeed = invokeNumber(be, "getRotationSpeed");
            if (rotationSpeed != null) {
                double rpm = Math.abs(rotationSpeed.doubleValue()) / 64.0D * config.propellerLongRangeRpmReference.get();
                return new RpmResolution(rpm, "reflection:be.getRotationSpeed");
            }

            Double direct = resolveNamedDouble(soundInstance, RPM_FIELD_NAMES);
            if (direct != null) {
                return new RpmResolution(normalizeRpmField(direct, config), "reflection:sound_field");
            }
            direct = resolveNamedDouble(be, RPM_FIELD_NAMES);
            if (direct != null) {
                return new RpmResolution(normalizeRpmField(direct, config), "reflection:be_field");
            }
        }

        return new RpmResolution(pitchProxyRpm(pitch, config), "pitch_proxy");
    }

    private static double normalizeRpmField(double value, SoundPhysicsConfig config) {
        double absolute = Math.abs(value);
        if (absolute <= config.propellerLongRangeRpmReference.get() * 2.0D) {
            return absolute;
        }
        return absolute / 64.0D * config.propellerLongRangeRpmReference.get();
    }

    private static double pitchProxyRpm(float pitch, SoundPhysicsConfig config) {
        double pitchAtReference = Math.max(config.propellerLongRangePitchAtReferenceRpm.get(), 0.001D);
        return Mth.clamp(pitch / pitchAtReference, 0.0D, 1.0D) * config.propellerLongRangeRpmReference.get();
    }

    private static int fallbackSails(@Nullable ResourceLocation soundId, SoundPhysicsConfig config) {
        if (SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE.equals(soundId)) {
            return config.propellerLongRangeLargeFallbackSails.get();
        }
        if (SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL.equals(soundId)) {
            return config.propellerLongRangeSmallFallbackSails.get();
        }
        return config.propellerLongRangeSmallFallbackSails.get();
    }

    @Nullable
    private static Integer resolveLayerProxySails(@Nullable Object be) {
        Object behavior = readField(be, "behavior");
        Number radius = readNumber(behavior, "radius");
        if (radius != null && radius.doubleValue() > 0.0D) {
            double radiusValue = radius.doubleValue();
            return Math.max(1, (int) Math.round(radiusValue * radiusValue * 0.33D));
        }
        Integer layers = resolveCollectionSize(behavior, "propellerLayers");
        if (layers != null && layers > 0) {
            return layers * 16;
        }
        return null;
    }

    @Nullable
    private static Integer resolveCollectionSize(@Nullable Object owner, String fieldName) {
        Object value = readField(owner, fieldName);
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return null;
    }

    @Nullable
    private static Integer resolveNamedNumber(@Nullable Object owner, List<String> names) {
        Double value = resolveNamedDouble(owner, names);
        return value == null ? null : (int) Math.round(value);
    }

    @Nullable
    private static Double resolveNamedDouble(@Nullable Object owner, List<String> names) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            Number value = readNumber(owner, name);
            if (value != null) {
                return value.doubleValue();
            }
            value = invokeNumber(owner, getterName(name));
            if (value != null) {
                return value.doubleValue();
            }
        }
        return null;
    }

    @Nullable
    private static Number readNumber(@Nullable Object owner, String fieldName) {
        Object value = readField(owner, fieldName);
        return value instanceof Number number ? number : null;
    }

    @Nullable
    private static Number invokeNumber(@Nullable Object owner, String methodName) {
        Object value = invokeObject(owner, methodName);
        return value instanceof Number number ? number : null;
    }

    @Nullable
    private static Object readField(@Nullable Object owner, String fieldName) {
        if (owner == null) {
            return null;
        }
        Field field = access(owner.getClass()).field(fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Object invokeObject(@Nullable Object owner, String methodName) {
        if (owner == null) {
            return null;
        }
        Method method = access(owner.getClass()).method(methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ClassAccess access(Class<?> type) {
        return CLASS_ACCESS_CACHE.computeIfAbsent(type, ClassAccess::scan);
    }

    private static String getterName(String name) {
        if (name.isBlank()) {
            return name;
        }
        return "get" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private record SailResolution(int count, String source) {
    }

    private record RpmResolution(double rpm, String source) {
    }

    private record ClassAccess(Map<String, Field> fields, Map<String, Method> methods) {

        static ClassAccess scan(Class<?> initial) {
            Map<String, Field> fields = new ConcurrentHashMap<>();
            Map<String, Method> methods = new ConcurrentHashMap<>();
            Class<?> type = initial;
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        fields.putIfAbsent(field.getName(), field);
                    } catch (Throwable ignored) {
                    }
                }
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getParameterCount() != 0) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        methods.putIfAbsent(method.getName(), method);
                    } catch (Throwable ignored) {
                    }
                }
                type = type.getSuperclass();
            }
            return new ClassAccess(fields, methods);
        }

        @Nullable
        Field field(String name) {
            return fields.get(name);
        }

        @Nullable
        Method method(String name) {
            return methods.get(name);
        }
    }
}
