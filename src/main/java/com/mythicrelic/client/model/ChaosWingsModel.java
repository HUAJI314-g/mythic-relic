package com.mythicrelic.client.model;

import com.mythicrelic.MythicRelic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家背后的混沌龙翼——形状与尼德霍格的翼一致，只是挂在玩家身上。
 *
 * <p>坐标沿用玩家模型的空间：躯干原点在肩部，向后是 +z，所以翼根放在 z=3 处。
 * 渲染层只负责把 {@code PoseStack} 平移一点点，跟原版鞘翅层是一个做法。</p>
 */
public class ChaosWingsModel extends EntityModel<Player>
{
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(MythicRelic.MODID, "chaos_wings"), "main");

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/entity/chaos_wings.png");

    private final ModelPart root;
    private final ModelPart wingRight;
    private final ModelPart wingLeft;

    public ChaosWingsModel(ModelPart root)
    {
        this.root = root;
        this.wingRight = root.getChild("wing_right");
        this.wingLeft = root.getChild("wing_left");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 一根骨架 + 一片下垂的翼膜，与 NidhoggModel 里的龙翼同形
        root.addOrReplaceChild("wing_right",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(0.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                        .texOffs(0, 8)
                        .addBox(3.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(3.0F, 2.0F, 3.0F));
        root.addOrReplaceChild("wing_left",
                CubeListBuilder.create().mirror()
                        .texOffs(0, 0)
                        .addBox(-18.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                        .texOffs(0, 8)
                        .addBox(-15.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(-3.0F, 2.0F, 3.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Player entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch)
    {
        boolean gliding = entity.isFallFlying();
        // 滑翔时大幅度扇动，平时只是随呼吸轻轻起伏
        float speed = gliding ? 0.5F : 0.12F;
        float amplitude = gliding ? 0.5F : 0.08F;
        float flap = Mth.sin(ageInTicks * speed) * amplitude;

        this.wingRight.zRot = -0.35F - flap;
        this.wingRight.yRot = -0.45F;
        this.wingLeft.zRot = 0.35F + flap;
        this.wingLeft.yRot = 0.45F;

        // 扇动时稍微张开一点，看起来像在兜风
        this.wingRight.xRot = gliding ? -0.2F : 0.0F;
        this.wingLeft.xRot = gliding ? -0.2F : 0.0F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
