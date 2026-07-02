package com.sonicether.soundphysics.propeller;

public record PropellerAudioProfile(
        int sailCount,
        double rpm,
        double sizeFactor,
        double rpmFactor,
        double computedMaxDistance,
        String sizeSource,
        String rpmSource
) {
}
