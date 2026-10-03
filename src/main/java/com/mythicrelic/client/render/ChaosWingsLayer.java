package com.mythicrelic.client.render;

import com.mythicrelic.client.ChaosClientData;
import com.mythicrelic.client.model.ChaosWingsModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 把混沌龙翼挂到玩家背上。
 *
 * <p>本地玩家按自己的混沌阶段决定画不画；其他玩家拿不到他们的进度，就退一步——
 * 只要在滑翔就画，至少保证飞行时看得见。</p>
 */
public class ChaosWingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
{
    private final ChaosWingsModel model;

    public ChaosWingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           EntityModelSet models)
    {
        super(parent);
        this.model = new ChaosWingsModel(models.bakeLayer(ChaosWingsModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch)
    {
        if (!shouldRender(player))
        {
            return;
        }
        poseStack.pushPose();
        // 和原版鞘翅层一样的做法：把翼稍微往后挪一点
        poseStack.translate(0.0D, 0.0D, 0.125D);
        this.model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(ChaosWingsModel.TEXTURE));
        this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static boolean shouldRender(AbstractClientPlayer player)
    {
        if (player == Minecraft.getInstance().player)
        {
            return ChaosClientData.hasWings();
        }
        return player.isFallFlying();
    }
}
