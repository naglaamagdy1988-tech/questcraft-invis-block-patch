package com.example.client.mixin;

import com.example.client.QuestCraftRenderPatchClient;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    private static volatile Method questcraft$vanillaPassMethod;
    private static volatile boolean questcraft$vanillaPassLookupComplete;

    @Group(name = "questcraft$forceChunkUpdate", min = 1, max = 1)
    @ModifyVariable(
        method = "updateChunks",
        at = @At("STORE"),
        ordinal = 1,
        expect = 0
    )
    private boolean questcraft$forceChunkUpdates(boolean dirty) {
        if (!QuestCraftRenderPatchClient.isInvisibleBlockPatchEnabled()) {
            return dirty;
        }
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
        if (!QuestCraftRenderPatchClient.isInvisibleBlockPatchEnabled()) {
            return dirty;
        }
        return !questcraft$isVanillaPass() || dirty;
    }

    private boolean questcraft$isVanillaPass() {
        Method method = questcraft$getVanillaPassMethod();
        if (method == null) return true;

        try {
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException e) {
            questcraft$vanillaPassMethod = null;
            return true;
        }
    }

    private static Method questcraft$getVanillaPassMethod() {
        if (!questcraft$vanillaPassLookupComplete) {
            synchronized (QuestCraftRenderPatchClient.class) {
                if (!questcraft$vanillaPassLookupComplete) {
                    try {
                        Class<?> renderPassType = Class.forName(
                                "org.vivecraft.client_xr.render_pass.RenderPassType");
                        Method method = renderPassType.getMethod("isVanilla");
                        if (Modifier.isStatic(method.getModifiers())
                                && method.getReturnType() == boolean.class) {
                            questcraft$vanillaPassMethod = method;
                        }
                    } catch (ReflectiveOperationException | LinkageError
                             | RuntimeException ignored) {
                        questcraft$vanillaPassMethod = null;
                    }
                    questcraft$vanillaPassLookupComplete = true;
                }
            }
        }
        return questcraft$vanillaPassMethod;
    }
}
