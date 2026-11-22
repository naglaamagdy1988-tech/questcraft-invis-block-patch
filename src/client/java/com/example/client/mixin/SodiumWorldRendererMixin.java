package com.example.client.mixin;

import com.example.client.QuestCraftRenderPatchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private static ClientLevel questcraft$lastRefreshLevel;
    private static long questcraft$lastRefreshTick = Long.MIN_VALUE;

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
        return dirty || questcraft$shouldForceChunkGraphRefresh();
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
        return dirty || questcraft$shouldForceChunkGraphRefresh();
    }

    private boolean questcraft$shouldForceChunkGraphRefresh() {
        if (!QuestCraftRenderPatchClient.isInvisibleBlockPatchEnabled()
                || questcraft$isVanillaPass()) {
            return false;
        }

        if (!QuestCraftRenderPatchClient.areVrChunkRefreshesReduced()) {
            return true;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;

        long tick = level.getGameTime();
        if (level == questcraft$lastRefreshLevel
                && tick == questcraft$lastRefreshTick) {
            return false;
        }

        questcraft$lastRefreshLevel = level;
        questcraft$lastRefreshTick = tick;
        return true;
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
