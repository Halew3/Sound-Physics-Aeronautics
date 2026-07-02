package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.AcousticRayHit;
import com.sonicether.soundphysics.acoustic.RootAcousticSpace;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SableAcousticSceneTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nearestHitWinsBetweenRootAndSable() {
        FakeAcousticSpace rootProxy = rootProxy().withStone(new BlockPos(4, 0, 0));
        RootAcousticSpace rootSpace = new RootAcousticSpace(rootProxy);
        FakeAcousticSpace sable = space("sable").withStone(new BlockPos(2, 0, 0));
        SableAcousticScene scene = scene(rootSpace, sable);

        AcousticRayHit hit = scene.rayCast(from(), to(), null);

        assertSame(sable, hit.space());
        assertEquals(2D, hit.worldLocation().x, 1.0E-6D);
    }

    @Test
    void sableMissFallsBackToRootHit() {
        FakeAcousticSpace rootProxy = rootProxy().withStone(new BlockPos(4, 0, 0));
        RootAcousticSpace rootSpace = new RootAcousticSpace(rootProxy);
        FakeAcousticSpace sable = space("sable");
        SableAcousticScene scene = scene(rootSpace, sable);

        AcousticRayHit hit = scene.rayCast(from(), to(), null);

        assertSame(rootSpace, hit.space());
        assertEquals(4D, hit.worldLocation().x, 1.0E-6D);
    }

    @Test
    void localHitTransformsToWorldHit() {
        RootAcousticSpace rootSpace = new RootAcousticSpace(rootProxy());
        FakeAcousticSpace sable = new FakeAcousticSpace(
                "sable",
                SableAcousticBounds.of(0D, 0D, 0D, 8D, 2D, 2D),
                new Vec3(10D, 0D, 0D)
        ).withStone(new BlockPos(2, 0, 0))
                .withWorldNormal(Direction.WEST, new Vec3(0D, 0D, -1D));
        SableAcousticScene scene = scene(rootSpace, sable, new Vec3(10D, 0.5D, 0.5D), new Vec3(15D, 0.5D, 0.5D));

        AcousticRayHit hit = scene.rayCast(new Vec3(10D, 0.5D, 0.5D), new Vec3(15D, 0.5D, 0.5D), null);

        assertSame(sable, hit.space());
        assertEquals(12D, hit.worldLocation().x, 1.0E-6D);
        assertEquals(0D, hit.worldNormal().x, 1.0E-6D);
        assertEquals(0D, hit.worldNormal().y, 1.0E-6D);
        assertEquals(-1D, hit.worldNormal().z, 1.0E-6D);
    }

    @Test
    void ignoredBlockOnlyAppliesWithinSameAcousticSpace() {
        RootAcousticSpace rootSpace = new RootAcousticSpace(rootProxy());
        FakeAcousticSpace sable = space("sable").withStone(new BlockPos(2, 0, 0));
        SableAcousticScene scene = scene(rootSpace, sable);

        AcousticRayHit hitWithRootIgnore = scene.rayCast(from(), to(), new AcousticBlockRef(rootSpace, new BlockPos(2, 0, 0)));
        AcousticRayHit hitWithSableIgnore = scene.rayCast(from(), to(), new AcousticBlockRef(sable, new BlockPos(2, 0, 0)));

        assertSame(sable, hitWithRootIgnore.space());
        assertEquals(HitResult.Type.MISS, hitWithSableIgnore.localHit().getType());
    }

    private static SableAcousticScene scene(RootAcousticSpace rootSpace, SableAcousticSpace... spaces) {
        return scene(rootSpace, List.of(spaces), from(), to());
    }

    private static SableAcousticScene scene(RootAcousticSpace rootSpace, SableAcousticSpace space, Vec3 source, Vec3 listener) {
        return scene(rootSpace, List.of(space), source, listener);
    }

    private static SableAcousticScene scene(RootAcousticSpace rootSpace, List<SableAcousticSpace> spaces, Vec3 source, Vec3 listener) {
        SableAcousticSnapshot snapshot = new SableAcousticSnapshot(null, 0L, 1L, 1L, spaces, spaces.size(), 0, 0, 0);
        return new SableAcousticScene(rootSpace, snapshot, new SableAcousticDiagnostics(), source, listener, spaces);
    }

    private static FakeAcousticSpace rootProxy() {
        return new FakeAcousticSpace("root-proxy", SableAcousticBounds.of(-16D, -16D, -16D, 32D, 32D, 32D));
    }

    private static FakeAcousticSpace space(String id) {
        return new FakeAcousticSpace(id, SableAcousticBounds.of(0D, 0D, 0D, 8D, 2D, 2D));
    }

    private static Vec3 from() {
        return new Vec3(0D, 0.5D, 0.5D);
    }

    private static Vec3 to() {
        return new Vec3(8D, 0.5D, 0.5D);
    }

}
