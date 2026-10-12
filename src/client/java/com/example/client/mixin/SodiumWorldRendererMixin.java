package com.example.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = {
    "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer",
    "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer"
})
public class SodiumWorldRendererMixin {

    @Group(name = "questcraft$forceChunkUpdate", min = 1, max = 1)
    @ModifyVariable(
        method = "updateChunks",
        at = @At("STORE"),
        ordinal = 1,
        expect = 0
    )
    private boolean questcraft$forceChunkUpdates(boolean dirty) {
        return !questcraft$isVanillaPass() || dirty;
    }

    @Group(name = "questcraft$forceChunkUpdate", min = 1, max = 1)
    @ModifyVariable(
        method = "setupTerrain",
        at = @At("STORE"),
        ordinal = 2,
        expect = 0
    )
    private boolean questcraft$forceChunkUpdatesSodium5(boolean dirty) {
        return !questcraft$isVanillaPass() || dirty;
    }

    private boolean questcraft$isVanillaPass() {
        try {
            Class<?> renderPassType = Class.forName(
                "org.vivecraft.client_xr.render_pass.RenderPassType"
            );
            return (boolean) renderPassType
                .getMethod("isVanilla")
                .invoke(null);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
