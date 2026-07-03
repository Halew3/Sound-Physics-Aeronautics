package com.sonicether.soundphysics.integration.dh;

public record DistantTerrainOcclusionResult(
        boolean available,
        boolean queried,
        boolean hit,
        double hitDistance,
        byte detailLevel,
        double sourceDistance,
        double occlusionStrength,
        float directGainMultiplier,
        float directCutoffMultiplier,
        String reason
) {

    private static final byte UNKNOWN_DETAIL = (byte) -1;

    public static DistantTerrainOcclusionResult none(String reason) {
        return identity(false, false, -1.0D, reason);
    }

    public static DistantTerrainOcclusionResult identity(boolean available, boolean queried, double sourceDistance, String reason) {
        return new DistantTerrainOcclusionResult(
                available,
                queried,
                false,
                -1.0D,
                UNKNOWN_DETAIL,
                sourceDistance,
                0.0D,
                1.0F,
                1.0F,
                reason
        );
    }

}
