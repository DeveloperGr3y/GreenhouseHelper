package io.github.developergr3y.greenhousehelper.mixin;

import io.github.developergr3y.greenhousehelper.greenhouse.PlantHider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium builds chunk meshes itself, so {@link SectionCompilerMixin} never runs with it: this does the same in
 * Sodium's builder. Only applied when Sodium is installed (see {@link GreenhouseMixinPlugin}); if a Sodium update moves
 * things it's skipped rather than crashing, and the crop blocks just show again.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class SodiumMeshingMixin {
    @Redirect(
        method = "execute",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"),
        require = 0
    )
    private BlockState greenhousehelper$hideCrops(LevelSlice slice, int x, int y, int z) {
        BlockState state = slice.getBlockState(x, y, z);
        return PlantHider.INSTANCE.hidesBlock(x, y, z) ? Blocks.AIR.defaultBlockState() : state;
    }
}
