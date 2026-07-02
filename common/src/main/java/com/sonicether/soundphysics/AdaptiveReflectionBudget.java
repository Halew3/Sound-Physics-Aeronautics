package com.sonicether.soundphysics;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

final class AdaptiveReflectionBudget {

    static final int MIN_RAYS = 8;
    static final int MIN_BOUNCES = 2;
    static final int CONTINUOUS_MAX_RAYS = 16;
    static final int CONTINUOUS_MAX_BOUNCES = 3;
    static final int PROPELLER_MAX_RAYS = 12;
    static final int PROPELLER_MAX_BOUNCES = 2;
    static final int CROSSWIND_MAX_RAYS = 12;
    static final int CROSSWIND_MAX_BOUNCES = 2;
    static final int VERY_FAR_MAX_RAYS = 8;
    static final int VERY_FAR_MAX_BOUNCES = 2;
    static final double NEAR_FULL_DISTANCE_BLOCKS = 64.0D;
    static final double VERY_FAR_DISTANCE_BLOCKS = 192.0D;

    private AdaptiveReflectionBudget() {
    }

    static Budget resolve(SoundPhysicsConfig config, SoundPhysicsSoundPolicy.SoundContext context, double distance) {
        int configuredRays = config.environmentEvaluationRayCount.get();
        int configuredBounces = config.environmentEvaluationRayBounces.get();

        if (!config.adaptiveReflectionBudgetEnabled.get()) {
            return new Budget(configuredRays, configuredBounces, configuredRays, configuredBounces, Reason.LEGACY);
        }

        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            return capped(configuredRays, configuredBounces, PROPELLER_MAX_RAYS, PROPELLER_MAX_BOUNCES, Reason.PROPELLER);
        }

        if (SoundPhysicsSoundPolicy.isCrosswindWindLoop(context)) {
            return capped(configuredRays, configuredBounces, CROSSWIND_MAX_RAYS, CROSSWIND_MAX_BOUNCES, Reason.CROSSWIND_LOOP);
        }

        if (context.tickable()
                || SoundPhysicsSoundPolicy.isContinuousLoop(context)
                || SoundPhysicsSoundPolicy.isRecord(context)
                || !context.startEvent()) {
            return capped(configuredRays, configuredBounces, CONTINUOUS_MAX_RAYS, CONTINUOUS_MAX_BOUNCES, Reason.CONTINUOUS_LOOP);
        }

        if (distance <= NEAR_FULL_DISTANCE_BLOCKS) {
            return new Budget(configuredRays, configuredBounces, configuredRays, configuredBounces, Reason.NEAR_FULL);
        }

        if (distance >= VERY_FAR_DISTANCE_BLOCKS) {
            return capped(configuredRays, configuredBounces, VERY_FAR_MAX_RAYS, VERY_FAR_MAX_BOUNCES, Reason.FAR_REDUCED);
        }

        int halfRays = (configuredRays + 1) / 2;
        int oneFewerBounce = Math.max(configuredBounces - 1, MIN_BOUNCES);
        return capped(configuredRays, configuredBounces, halfRays, oneFewerBounce, Reason.FAR_REDUCED);
    }

    private static Budget capped(int configuredRays, int configuredBounces, int maxRays, int maxBounces, Reason reason) {
        return new Budget(
                configuredRays,
                configuredBounces,
                reduce(configuredRays, maxRays, MIN_RAYS),
                reduce(configuredBounces, maxBounces, MIN_BOUNCES),
                reason
        );
    }

    private static int reduce(int configured, int cap, int minimum) {
        int capped = Math.min(configured, cap);
        if (configured < minimum) {
            return capped;
        }
        return Math.max(minimum, capped);
    }

    record Budget(int configuredRays, int configuredBounces, int rays, int bounces, Reason reason) {

        boolean reduced() {
            return rays != configuredRays || bounces != configuredBounces;
        }
    }

    enum Reason {
        LEGACY("legacy"),
        NEAR_FULL("near_full"),
        FAR_REDUCED("far_reduced"),
        CONTINUOUS_LOOP("continuous_loop"),
        PROPELLER("propeller"),
        CROSSWIND_LOOP("crosswind_loop");

        private final String diagnosticName;

        Reason(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }

        String diagnosticName() {
            return diagnosticName;
        }
    }

}
