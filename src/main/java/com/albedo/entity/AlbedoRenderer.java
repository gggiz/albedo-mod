package com.albedo.entity;

import com.albedo.AlbedoMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class AlbedoRenderer extends HumanoidMobRenderer<AlbedoBoss, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = AlbedoMod.id("textures/entity/albedo.png");

    public AlbedoRenderer(EntityRendererProvider.Context context) {
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
    public void extractRenderState(AlbedoBoss boss, HumanoidRenderState renderState, float partialTick) {
        super.extractRenderState(boss, renderState, partialTick);

        int attackState = boss.getAttackState();
        switch (attackState) {
            case 2: // Sweep - right arm extended for horizontal slash
                renderState.rightArmPose = HumanoidModel.ArmPose.SPEAR;
                break;
            case 3: // Thrust - both arms forward
                renderState.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                break;
            case 4: // Wave / Clone - arms out casting
                renderState.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
                break;
            case 5: // Judgment - arms raised
                renderState.rightArmPose = HumanoidModel.ArmPose.TOOT_HORN;
                renderState.leftArmPose = HumanoidModel.ArmPose.TOOT_HORN;
                break;
            default:
                break;
        }
    }
}
