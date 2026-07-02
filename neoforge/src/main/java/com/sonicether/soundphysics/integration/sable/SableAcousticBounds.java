package com.sonicether.soundphysics.integration.sable;

record SableAcousticBounds(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {

    static SableAcousticBounds of(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new SableAcousticBounds(
                Math.min(minX, maxX),
                Math.min(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minX, maxX),
                Math.max(minY, maxY),
                Math.max(minZ, maxZ)
        );
    }

    static SableAcousticBounds segment(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return of(fromX, fromY, fromZ, toX, toY, toZ);
    }

    SableAcousticBounds inflate(double amount) {
        return new SableAcousticBounds(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    boolean intersects(SableAcousticBounds other) {
        return maxX >= other.minX && minX <= other.maxX
                && maxY >= other.minY && minY <= other.maxY
                && maxZ >= other.minZ && minZ <= other.maxZ;
    }

}
