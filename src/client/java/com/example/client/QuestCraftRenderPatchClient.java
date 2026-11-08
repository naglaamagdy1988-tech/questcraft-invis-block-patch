package com.example.client;

import com.example.api.QuestCraftVRApi;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestCraftRenderPatchClient implements ClientModInitializer {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("QuestCraftRenderPatch");

    @Override
    public void onInitializeClient() {
        QuestCraftVRApi.ApiStatus status = QuestCraftVRApi.getApiStatus();
        LOGGER.info("[QuestCraft Render Patch] Vivecraft client API status: {}", status);
    }
}
