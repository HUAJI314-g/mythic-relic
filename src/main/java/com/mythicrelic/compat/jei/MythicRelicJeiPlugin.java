package com.mythicrelic.compat.jei;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.recipe.ElementExtractorRecipe;
import com.mythicrelic.registry.ModBlocks;
import com.mythicrelic.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * JEI 兼容：让「元素提取器」的配方能在 JEI（以及读 JEI 配方的那些查看器）里翻到。
 *
 * <h2>为什么可以「可选」</h2>
 * <p>JEI 只是 {@code compileOnly} + {@code runtimeOnly}，<b>不会</b>被打进发布 jar。
 * 这个类是 {@code @JeiPlugin} 标注的，JEI 在的时候由它自己扫出来加载；JEI 不在的时候
 * 这个类根本不会被触碰，所以不会 NoClassDefFoundError。玩家装不装 JEI 都能开游戏，
 * 装了就能在 JEI 里看到提取配方。</p>
 *
 * <h2>两个「RecipeType」别搞混</h2>
 * <p>原版有 {@code net.minecraft.world.item.crafting.RecipeType}（我们注册在
 * {@link ModRecipes#ELEMENT_EXTRACTOR_TYPE}），JEI 又有一套自己的
 * {@code mezz.jei.api.recipe.RecipeType}。查询配方用原版那套，往 JEI 里注册用 JEI 那套，
 * 两者靠 {@link #ELEMENT_EXTRACTOR} 这个常量对应起来。</p>
 */
@JeiPlugin
public class MythicRelicJeiPlugin implements IModPlugin
{
    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(MythicRelic.MODID, "jei_plugin");

    /** JEI 侧的配方类型标识（namespace:path 与我们的注册名保持一致）。 */
    public static final mezz.jei.api.recipe.RecipeType<ElementExtractorRecipe> ELEMENT_EXTRACTOR =
            mezz.jei.api.recipe.RecipeType.create(MythicRelic.MODID, "element_extractor",
                    ElementExtractorRecipe.class);

    @Override
    public ResourceLocation getPluginUid()
    {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration)
    {
        registration.addRecipeCategories(
                new ElementExtractorRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration)
    {
        if (Minecraft.getInstance().level == null)
        {
            return;
        }
        List<ElementExtractorRecipe> recipes = Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.ELEMENT_EXTRACTOR_TYPE.get());
        registration.addRecipes(ELEMENT_EXTRACTOR, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration)
    {
        // 在 JEI 里右键「元素提取器」就能直接跳到提取配方
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.ELEMENT_EXTRACTOR.get()), ELEMENT_EXTRACTOR);
    }
}
