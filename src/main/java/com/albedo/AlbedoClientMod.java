package com.albedo;

import com.albedo.entity.AlbedoRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AlbedoClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AlbedoMod.ALBEDO, AlbedoRenderer::new);
    }
}
