package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsPerfDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsTrace;
import com.sonicether.soundphysics.utils.RaycastUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class RootAcousticScene implements AcousticScene {

    private final RootAcousticSpace space;

    public RootAcousticScene(RootAcousticSpace space) {
        this.space = space;
    }

    @Override
    public AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore) {
        long startNanos = System.nanoTime();
        try {
            BlockPos ignoredBlock = ignore != null && ignore.space() == space ? ignore.pos() : null;
            BlockHitResult hit = RaycastUtils.rayCast(space, from, to, ignoredBlock);
            SoundPhysicsTrace.recordRootRay(hit.getType(), from, to, hit.getLocation());
            Vec3 normal = new Vec3(hit.getDirection().step());

            return new AcousticRayHit(hit, space, hit.getLocation(), normal);
        } finally {
            SoundPhysicsPerfDiagnostics.recordAcousticRayCast(System.nanoTime() - startNanos);
        }
    }

    @Override
    public AcousticBlockRef blockAt(Vec3 worldPosition) {
        return new AcousticBlockRef(space, BlockPos.containing(worldPosition));
    }

}
