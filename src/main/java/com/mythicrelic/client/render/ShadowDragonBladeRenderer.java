package com.mythicrelic.client.render;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.entity.ShadowDragonBlade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 暗影龙刃的渲染器：一张朝向摄像机的方片，像一道横着斩出去的剑气。
 *
 * <p>用朝向摄像机而不是沿着飞行方向，是因为投射物自己带的那种朝向同步在这边没做，
 * 而正对镜头的剑气本来也更好看。</p>
 *
 * <p>贴图里那道刃身是沿 45° <b>斜贯</b>整块方片的（从左上到右下），所以这里要把它转平，
 * 否则一眼看过去是柄飞出去的短刀、不是斩出去的气。转平之后刃身正好落在水平方向，
 * 长度等于方片对角线（约 {@code 1.41 ×} 缩放值）。</p>
 */
public class ShadowDragonBladeRenderer extends EntityRenderer<ShadowDragonBlade>
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/entity/shadow_dragon_blade.png");

    /** 剑气的基准大小（蓄力为 0 时）。 */
    private static final float BASE_SCALE = 1.6F;
    /** 蓄满时再额外放大的量。 */
    private static final float CHARGE_SCALE = 1.4F;
    /** 把贴图里 45° 斜着的刃身转平的角度。 */
    private static final float BLADE_FLATTEN = 45.0F;

    public ShadowDragonBladeRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowDragonBlade entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(ShadowDragonBlade entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // 蓄得越满，剑气越长越宽
        float scale = BASE_SCALE + CHARGE_SCALE * entity.chargeRatio();
        poseStack.scale(scale, scale, 1.0F);
        // 把斜着的刃身放平（缩放是均匀的，和这一步谁先谁后都一样）
        poseStack.mulPose(Axis.ZP.rotationDegrees(BLADE_FLATTEN));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        quad(consumer, pose, packedLight);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light)
    {
        vertex(consumer, pose, -0.5F, -0.5F, 0.0F, 0.0F, light);
        vertex(consumer, pose, -0.5F, 0.5F, 0.0F, 1.0F, light);
        vertex(consumer, pose, 0.5F, 0.5F, 1.0F, 1.0F, light);
        vertex(consumer, pose, 0.5F, -0.5F, 1.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float u, float v, int light)
    {
        consumer.vertex(pose.pose(), x, y, 0.0F)
                .color(255, 255, 255, 235)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
