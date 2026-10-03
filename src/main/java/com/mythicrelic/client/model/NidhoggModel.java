package com.mythicrelic.client.model;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.entity.NidhoggEntity;
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

/**
 * 尼德霍格的模型：一位身着漆黑长裙、背生龙翼的女子。
 *
 * <p>整体沿用方块人（人形）的骨架，只是把「腿」换成了曳地的长裙，并在背后加了一对蝙蝠状的龙翼。
 * 模型坐标遵循 MC 约定：+y 向下，脚底落在 y=24。</p>
 */
public class NidhoggModel<T extends NidhoggEntity> extends EntityModel<T>
{
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(MythicRelic.MODID, "nidhogg"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart armRight;
    private final ModelPart armLeft;
    private final ModelPart wingRight;
    private final ModelPart wingLeft;

    public NidhoggModel(ModelPart root)
    {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.armRight = this.body.getChild("arm_right");
        this.armLeft = this.body.getChild("arm_left");
        this.wingRight = this.body.getChild("wing_right");
        this.wingLeft = this.body.getChild("wing_left");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 躯干 + 曳地长裙（用两层逐渐放大的方块模拟裙摆）
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
                        .texOffs(0, 16)
                        .addBox(-5.0F, 12.0F, -5.0F, 10.0F, 6.0F, 10.0F)
                        .texOffs(40, 16)
                        .addBox(-6.0F, 18.0F, -6.0F, 12.0F, 6.0F, 12.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // 头 + 长发
        body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(24, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                        .texOffs(56, 0)
                        .addBox(-4.5F, -9.0F, -4.5F, 9.0F, 3.0F, 9.0F)
                        .texOffs(0, 36)
                        .addBox(-3.5F, -6.0F, 3.0F, 7.0F, 18.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // 双臂
        body.addOrReplaceChild("arm_right",
                CubeListBuilder.create()
                        .texOffs(20, 36)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        body.addOrReplaceChild("arm_left",
                CubeListBuilder.create()
                        .texOffs(32, 36)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        // 龙翼：一根骨架 + 一片下垂的翼膜
        body.addOrReplaceChild("wing_right",
                CubeListBuilder.create()
                        .texOffs(44, 36)
                        .addBox(0.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                        .texOffs(84, 36)
                        .addBox(3.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(3.0F, 2.0F, 3.0F));
        body.addOrReplaceChild("wing_left",
                CubeListBuilder.create().mirror()
                        .texOffs(44, 36)
                        .addBox(-18.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                        .texOffs(84, 36)
                        .addBox(-15.0F, -1.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(-3.0F, 2.0F, 3.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch)
    {
        // 悬停时极轻微的上下浮动，让她看起来是「飘」着的
        this.body.y = Mth.sin(ageInTicks * 0.06F) * 0.4F;

        // 头部跟随玩家视线
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180.0F) * 0.6F;
        this.head.xRot = headPitch * ((float) Math.PI / 180.0F) * 0.4F;

        // 龙翼扇动 + 向后收拢
        float flap = Mth.sin(ageInTicks * 0.28F) * 0.32F;
        this.wingRight.zRot = -0.35F - flap;
        this.wingRight.yRot = -0.45F;
        this.wingLeft.zRot = 0.35F + flap;
        this.wingLeft.yRot = 0.45F;

        // 双臂自然垂落，随呼吸轻摆
        float sway = Mth.sin(ageInTicks * 0.1F) * 0.06F;
        this.armRight.xRot = sway;
        this.armLeft.xRot = -sway;
        this.armRight.zRot = -0.12F;
        this.armLeft.zRot = 0.12F;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer buffer,
                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
