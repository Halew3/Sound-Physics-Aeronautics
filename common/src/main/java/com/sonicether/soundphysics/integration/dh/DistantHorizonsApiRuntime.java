package com.sonicether.soundphysics.integration.dh;

import javax.annotation.Nullable;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class DistantHorizonsApiRuntime {

    @Nullable
    private static IDhApiTerrainDataCache cache;

    private DistantHorizonsApiRuntime() {
    }

    static DistantHorizonsRaycast raycast(ClientLevel level, Vec3 listenerPos, Vec3 sourcePos, int maxRayLength) {
        IDhApiTerrainDataRepo terrainRepo = DhApi.Delayed.terrainRepo;
        IDhApiWorldProxy worldProxy = DhApi.Delayed.worldProxy;
        if (terrainRepo == null || worldProxy == null) {
            return DistantHorizonsRaycast.failure("dh_not_initialized", "DhApi.Delayed terrainRepo/worldProxy unavailable");
        }

        try {
            if (!worldProxy.worldLoaded()) {
                return DistantHorizonsRaycast.failure("world_not_loaded", "DH worldProxy reports no loaded world");
            }
        } catch (Throwable throwable) {
            return DistantHorizonsRaycast.failure("world_not_loaded", throwable.getMessage());
        }

        IDhApiTerrainDataCache softCache = cache(terrainRepo);
        if (softCache == null) {
            return DistantHorizonsRaycast.failure("cache_unavailable", "createSoftCache returned null");
        }

        ResourceLocation dimensionId = level.dimension().location();
        String dimension = dimensionId.toString();
        IDhApiLevelWrapper dhLevel = resolveLevelWrapper(worldProxy, dimension);
        if (dhLevel == null) {
            return DistantHorizonsRaycast.failure("no_level_wrapper", "No DH level wrapper matched " + dimension);
        }

        Vec3 delta = sourcePos.subtract(listenerPos);
        double length = delta.length();
        if (!Double.isFinite(length) || length <= 1.0E-6D || maxRayLength <= 0) {
            return DistantHorizonsRaycast.failure("invalid_ray", "Invalid DH ray vector or length");
        }
        Vec3 direction = delta.scale(1.0D / length);

        try {
            DhApiResult<DhApiRaycastResult> result = terrainRepo.raycast(
                    dhLevel,
                    listenerPos.x,
                    listenerPos.y,
                    listenerPos.z,
                    (float) direction.x,
                    (float) direction.y,
                    (float) direction.z,
                    maxRayLength,
                    softCache
            );
            if (result == null) {
                return DistantHorizonsRaycast.failure("ray_fail", "DH raycast returned null result");
            }
            if (!result.success) {
                return DistantHorizonsRaycast.failure("ray_fail", result.message);
            }
            DhApiRaycastResult payload = result.payload;
            if (payload == null || payload.dataPoint == null) {
                return DistantHorizonsRaycast.miss(dimension, "ray_miss", result.message);
            }
            DhApiVec3i hitPos = payload.pos;
            DhApiTerrainDataPoint dataPoint = payload.dataPoint;
            if (hitPos == null) {
                return DistantHorizonsRaycast.failure("ray_fail", "DH raycast hit without a hit position");
            }
            double hitDistance = listenerPos.distanceTo(new Vec3(hitPos.x, hitPos.y, hitPos.z));
            return DistantHorizonsRaycast.hit(dimension, hitDistance, dataPoint.detailLevel);
        } catch (Throwable throwable) {
            return DistantHorizonsRaycast.failure("exception", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    static synchronized void clearCache() {
        IDhApiTerrainDataCache current = cache;
        cache = null;
        if (current == null) {
            return;
        }
        try {
            if (current instanceof AutoCloseable closeable) {
                closeable.close();
            } else {
                current.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    static synchronized boolean cacheCreated() {
        return cache != null;
    }

    @Nullable
    private static synchronized IDhApiTerrainDataCache cache(IDhApiTerrainDataRepo terrainRepo) {
        if (cache == null) {
            try {
                cache = terrainRepo.createSoftCache();
            } catch (Throwable throwable) {
                return null;
            }
        }
        return cache;
    }

    @Nullable
    private static IDhApiLevelWrapper resolveLevelWrapper(IDhApiWorldProxy worldProxy, String dimension) {
        IDhApiLevelWrapper singlePlayerLevel = null;
        try {
            singlePlayerLevel = worldProxy.getSinglePlayerLevel();
            if (matches(singlePlayerLevel, dimension)) {
                return singlePlayerLevel;
            }
        } catch (Throwable ignored) {
        }

        IDhApiLevelWrapper looseMatch = null;
        try {
            Iterable<IDhApiLevelWrapper> wrappers = worldProxy.getAllLoadedLevelWrappers();
            if (wrappers != null) {
                for (IDhApiLevelWrapper wrapper : wrappers) {
                    if (exactMatch(wrapper, dimension)) {
                        return wrapper;
                    }
                    if (looseMatch == null && matches(wrapper, dimension)) {
                        looseMatch = wrapper;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (looseMatch != null) {
            return looseMatch;
        }
        return singlePlayerLevel;
    }

    private static boolean exactMatch(@Nullable IDhApiLevelWrapper wrapper, String dimension) {
        if (wrapper == null) {
            return false;
        }
        try {
            return dimension.equals(wrapper.getDimensionName()) || dimension.equals(wrapper.getDhIdentifier());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean matches(@Nullable IDhApiLevelWrapper wrapper, String dimension) {
        if (wrapper == null) {
            return false;
        }
        try {
            String dimensionName = wrapper.getDimensionName();
            String dhIdentifier = wrapper.getDhIdentifier();
            String path = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
            return matches(dimensionName, dimension, path) || matches(dhIdentifier, dimension, path);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean matches(@Nullable String candidate, String dimension, String path) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (candidate.equals(dimension) || candidate.equals(path)) {
            return true;
        }
        String lowerCandidate = candidate.toLowerCase(java.util.Locale.ROOT);
        String lowerDimension = dimension.toLowerCase(java.util.Locale.ROOT);
        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
        return lowerCandidate.contains(lowerDimension) || lowerCandidate.contains(lowerPath);
    }

}
