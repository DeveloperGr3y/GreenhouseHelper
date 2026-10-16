package io.github.developergr3y.greenhousehelper.mixin;

import io.github.developergr3y.greenhousehelper.greenhouse.PlantHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips drawing the plant models {@link PlantHider} hides. Only drawing: the entities are untouched. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void greenhousehelper$hidePlants(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (PlantHider.INSTANCE.isHidden(entity)) cir.setReturnValue(false);
    }
}
