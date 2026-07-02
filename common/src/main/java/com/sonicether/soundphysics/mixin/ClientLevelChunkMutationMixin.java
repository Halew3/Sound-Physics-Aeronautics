package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.world.ClientChunkMutationTracker;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class ClientLevelChunkMutationMixin {

    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sound_physics_remastered$recordClientChunkMutation(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState oldState = cir.getReturnValue();
        if (!(level instanceof ClientLevel clientLevel) || oldState == null || oldState.equals(state)) {
            return;
        }

        ClientChunkMutationTracker.recordChanged(clientLevel, ((LevelChunk) (Object) this).getPos());
    }

}
