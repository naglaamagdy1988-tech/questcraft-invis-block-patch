package com.example.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
public class SodiumWorldRendererMixin {

    @ModifyVariable(
        method = "setupTerrain",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private boolean questcraft$forceChunkUpdates(boolean updateChunksImmediately) {
        return !RenderPassType.isVanilla() || updateChunksImmediately;
    }
}
