package com.example.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderManager.class)
public class QuestCraftRenderPatch {

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void forceSafeVRLayer(BlockState state, CallbackInfoReturnable<RenderLayer> info) {
        if (state != null && state.getBlock() != null) {
            String namespace = state.getBlock().getRegistryEntry().getKey().get().getValue().getNamespace();
            if (!namespace.equals("minecraft")) {
                info.setReturnValue(RenderLayer.getCutout());
            }
        }
    }
}
