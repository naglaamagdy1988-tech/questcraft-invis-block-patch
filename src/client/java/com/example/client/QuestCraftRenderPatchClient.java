package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class QuestCraftRenderPatchClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Run a safe registration loop right after the game finish loading libraries
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            // Scan for custom added mods (ignoring vanilla Minecraft data)
            if (!id.getNamespace().equals("minecraft")) {
                // Safely assign the block to a solid VR texture layer on boot
                BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
            }
        }
    }
}
