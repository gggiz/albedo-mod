package com.albedo.entity;

import com.albedo.AlbedoMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class AxolotlMageRenderer extends HumanoidMobRenderer<AxolotlMage, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = AlbedoMod.id("textures/entity/axolotl_mage.png");

    public AxolotlMageRenderer(EntityRendererProvider.Context context) {
        super(context,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)),
                0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public void extractRenderState(AxolotlMage mage, HumanoidRenderState renderState, float partialTick) {
        super.extractRenderState(mage, renderState, partialTick);

        // Casting pose
        if (mage.isCasting()) {
            renderState.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
            renderState.leftArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
    }
}
