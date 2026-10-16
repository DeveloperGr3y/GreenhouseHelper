package io.github.developergr3y.greenhousehelper.mixin;

import io.github.developergr3y.greenhousehelper.greenhouse.PlantHider;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Leaves the crop blocks {@link PlantHider} hides (sugar cane, stems, wart…) out of the chunk's mesh, so they aren't
 * drawn. Only the drawing: the world, collisions and breaking are untouched. Runs on chunk-building threads.
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    @Redirect(
        method = "compile",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState greenhousehelper$hideCrops(RenderSectionRegion region, BlockPos pos) {
        BlockState state = region.getBlockState(pos);
        return PlantHider.INSTANCE.hidesBlock(pos) ? Blocks.AIR.defaultBlockState() : state;
    }
}
