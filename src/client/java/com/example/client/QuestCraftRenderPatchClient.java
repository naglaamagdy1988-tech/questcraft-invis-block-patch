package com.example.client;

import com.example.api.QuestCraftVRApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class QuestCraftRenderPatchClient implements ClientModInitializer {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("QuestCraftRenderPatch");
    private static boolean estimatedLegTrackingEnabled;
    private static volatile boolean invisibleBlockPatchEnabled = true;
    private static Path settingsFile;

    @Override
    public void onInitializeClient() {
        QuestCraftVRApi.ApiStatus status = QuestCraftVRApi.getApiStatus();
        LOGGER.info("[QuestCraft Render Patch] Vivecraft client API status: {}", status);

        settingsFile = FabricLoader.getInstance().getConfigDir()
                .resolve("questcraft-render-patch.json");
        loadSettings();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof VideoSettingsScreen) {
                Button blockPatchButton = Button.builder(
                                blockPatchLabel(),
                                button -> {
                                    invisibleBlockPatchEnabled = !invisibleBlockPatchEnabled;
                                    saveSettings();
                                    button.setMessage(blockPatchLabel());
                                    LOGGER.info("Invisible block patch {}",
                                            invisibleBlockPatchEnabled ? "enabled" : "disabled");
                                })
                        .bounds(scaledWidth / 2 - 100, scaledHeight - 82, 200, 20)
                        .build();
                Button legTrackingButton = Button.builder(
                                legTrackingLabel(),
                                button -> {
                                    estimatedLegTrackingEnabled = !estimatedLegTrackingEnabled;
                                    saveSettings();
                                    button.setMessage(legTrackingLabel());
                                    LOGGER.info("Estimated leg poses {}",
                                            estimatedLegTrackingEnabled ? "enabled" : "disabled");
                                })
                        .bounds(scaledWidth / 2 - 100, scaledHeight - 56, 200, 20)
                        .build();
                Screens.getWidgets(screen).add(blockPatchButton);
                Screens.getWidgets(screen).add(legTrackingButton);
            }
        });
    }

    private static Component blockPatchLabel() {
        return Component.translatable(invisibleBlockPatchEnabled
                ? "options.questcraft-render-patch.invisible_blocks.on"
                : "options.questcraft-render-patch.invisible_blocks.off");
    }

    private static Component legTrackingLabel() {
        return Component.translatable(estimatedLegTrackingEnabled
                ? "options.questcraft-render-patch.estimated_legs.on"
                : "options.questcraft-render-patch.estimated_legs.off");
    }

    /** Whether the user enabled approximate, non-camera leg poses. */
    public static boolean isEstimatedLegTrackingEnabled() {
        return estimatedLegTrackingEnabled;
    }

    /** Whether the optional Sodium/Vivecraft invisible-block patch is enabled. */
    public static boolean isInvisibleBlockPatchEnabled() {
        return invisibleBlockPatchEnabled;
    }

    static void setInvisibleBlockPatchEnabled(boolean enabled) {
        invisibleBlockPatchEnabled = enabled;
    }

    private static void loadSettings() {
        if (!Files.exists(settingsFile)) return;
        try (var reader = Files.newBufferedReader(settingsFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("estimatedLegTracking")) {
                estimatedLegTrackingEnabled = json.get("estimatedLegTracking").getAsBoolean();
            }
            if (json.has("invisibleBlockPatch")) {
                invisibleBlockPatchEnabled = json.get("invisibleBlockPatch").getAsBoolean();
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load VR tracking settings", exception);
        }
    }

    static void saveSettings() {
        JsonObject json = new JsonObject();
        json.addProperty("estimatedLegTracking", estimatedLegTrackingEnabled);
        json.addProperty("invisibleBlockPatch", invisibleBlockPatchEnabled);
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, json.toString() + System.lineSeparator());
        } catch (IOException exception) {
            LOGGER.warn("Could not save VR tracking settings", exception);
        }
    }
}
