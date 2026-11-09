package com.example.client;

import com.example.api.QuestCraftVRApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class QuestCraftRenderPatchClient implements ClientModInitializer {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("QuestCraftRenderPatch");
    private static KeyMapping toggleEstimatedLegTracking;
    private static boolean estimatedLegTrackingEnabled;
    private static Path settingsFile;

    @Override
    public void onInitializeClient() {
        QuestCraftVRApi.ApiStatus status = QuestCraftVRApi.getApiStatus();
        LOGGER.info("[QuestCraft Render Patch] Vivecraft client API status: {}", status);

        settingsFile = FabricLoader.getInstance().getConfigDir()
                .resolve("questcraft-render-patch.json");
        loadSettings();

        toggleEstimatedLegTracking = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.questcraft-render-patch.toggle_estimated_legs",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_K,
                        KeyMapping.Category.MISC
                )
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleEstimatedLegTracking.consumeClick()) {
                estimatedLegTrackingEnabled = !estimatedLegTrackingEnabled;
                saveSettings();
                LOGGER.info("Estimated leg tracking {}",
                        estimatedLegTrackingEnabled ? "enabled" : "disabled");
            }
        });
    }

    /** Whether the user enabled approximate, non-camera leg poses. */
    public static boolean isEstimatedLegTrackingEnabled() {
        return estimatedLegTrackingEnabled;
    }

    private static void loadSettings() {
        if (!Files.exists(settingsFile)) return;
        try (var reader = Files.newBufferedReader(settingsFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("estimatedLegTracking")) {
                estimatedLegTrackingEnabled = json.get("estimatedLegTracking").getAsBoolean();
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load VR tracking settings", exception);
        }
    }

    private static void saveSettings() {
        JsonObject json = new JsonObject();
        json.addProperty("estimatedLegTracking", estimatedLegTrackingEnabled);
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, json.toString() + System.lineSeparator());
        } catch (IOException exception) {
            LOGGER.warn("Could not save VR tracking settings", exception);
        }
    }
}
