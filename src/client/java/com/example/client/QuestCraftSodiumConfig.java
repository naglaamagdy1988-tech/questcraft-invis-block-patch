package com.example.client;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Adds the block patch toggle to Sodium's own Video Settings page. */
public final class QuestCraftSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable(
                                "options.questcraft-render-patch.sodium_page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(builder.createBooleanOption(
                                                Identifier.parse("questcraft-render-patch:invisible_block_patch"))
                                        .setName(Component.translatable(
                                                "options.questcraft-render-patch.invisible_blocks.name"))
                                        .setTooltip(Component.translatable(
                                                "options.questcraft-render-patch.invisible_blocks.tooltip"))
                                        .setStorageHandler(
                                                QuestCraftRenderPatchClient::saveSettings)
                                        .setBinding(
                                                QuestCraftRenderPatchClient::setInvisibleBlockPatchEnabled,
                                                QuestCraftRenderPatchClient::isInvisibleBlockPatchEnabled)
                                        .setDefaultValue(true))));
    }
}
