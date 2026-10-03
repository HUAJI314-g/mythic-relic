package com.mythicrelic.client.render;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.client.model.NidhoggModel;
import com.mythicrelic.entity.NidhoggEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 尼德霍格的渲染器。
 *
 * <p>核心是 {@link #scale}：根据实体同步下来的 {@code emerge} / {@code vanish} 进度，
 * 把整个模型从一点放大到全尺寸（凝聚成形），或再收缩回一点（化作紫光涌入玩家）。</p>
 */
public class NidhoggRenderer extends MobRenderer<NidhoggEntity, NidhoggModel<NidhoggEntity>>
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/entity/nidhogg.png");

    /** 她比普通人形略大一些。 */
    private static final float BASE_SCALE = 1.18F;

    public NidhoggRenderer(EntityRendererProvider.Context context)
    {
        super(context, new NidhoggModel<>(context.bakeLayer(NidhoggModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NidhoggEntity entity)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(NidhoggEntity entity, PoseStack poseStack, float partialTicks)
    {
        float emerge = Mth.clamp(entity.getEmerge(partialTicks), 0.02F, 1.0F);
        float vanish = Mth.clamp(entity.getVanish(partialTicks), 0.0F, 1.0F);
        float scale = BASE_SCALE * emerge * (1.0F - 0.94F * vanish);
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected boolean shouldShowName(NidhoggEntity entity)
    {
        return entity.hasCustomName();
    }

    /**
     * 封印之地没有天光，而她通体漆黑——这里给她托一个最低亮度，
     * 免得玩家只看见一双眼睛在黑暗里飘。祭坛点亮后她自己就会亮起来。
     */
    @Override
    protected int getBlockLightLevel(NidhoggEntity entity, BlockPos pos)
    {
        return Math.max(9, super.getBlockLightLevel(entity, pos));
    }
}
