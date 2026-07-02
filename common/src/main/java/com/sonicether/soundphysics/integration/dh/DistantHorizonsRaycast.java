package com.sonicether.soundphysics.integration.dh;

record DistantHorizonsRaycast(
        boolean queried,
        boolean hit,
        double hitDistance,
        byte detailLevel,
        String dimension,
        String reason,
        String message
) {

    static DistantHorizonsRaycast failure(String reason, String message) {
        return new DistantHorizonsRaycast(false, false, -1.0D, (byte) -1, "unknown", reason, message);
    }

    static DistantHorizonsRaycast miss(String dimension, String reason, String message) {
        return new DistantHorizonsRaycast(true, false, -1.0D, (byte) -1, dimension, reason, message);
    }

    static DistantHorizonsRaycast hit(String dimension, double hitDistance, byte detailLevel) {
        return new DistantHorizonsRaycast(true, true, hitDistance, detailLevel, dimension, "ray_hit", "hit");
    }

}
