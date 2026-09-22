package com.example.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRenderManager.class, priority = 500) // Lower priority so QuestCraft loads its VR engine first
public class QuestCraftRenderPatch {

    @Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true) // Run at 'RETURN' to prevent early crashes
    private void forceSafeVRLayer(BlockState state, CallbackInfoReturnable<RenderLayer> info) {
        if (state != null && state.getBlock() != null) {
            String namespace = state.getBlock().getRegistryEntry().getKey().get().getValue().getNamespace();
            // Only intervene if the block texture layout is returning blank/null or is a modded block
            if (!namespace.equals("minecraft")) {
                if (info.getReturnValue() == null) {
                    info.setReturnValue(RenderLayer.getCutout());
                }
            }
        }
    }
}
