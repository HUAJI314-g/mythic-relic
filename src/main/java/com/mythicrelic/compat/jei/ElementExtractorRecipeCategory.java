package com.mythicrelic.compat.jei;

import com.mythicrelic.recipe.ElementExtractorRecipe;
import com.mythicrelic.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * JEI 里「元素提取器」这一类配方的展示：<b>1 个输入 → 最多 3 个产出</b>，
 * 中间一支箭头。布局刻意和机器界面里那排槽位对齐（左输入、右三输出）。
 */
public class ElementExtractorRecipeCategory implements IRecipeCategory<ElementExtractorRecipe>
{
    private static final int WIDTH = 118;
    private static final int HEIGHT = 40;

    /** 槽位的竖直位置（和机器 GUI 里的 35 一个道理，只是这里没有顶部留白）。 */
    private static final int SLOT_Y = 11;
    private static final int INPUT_X = 1;
    private static final int ARROW_X = 27;
    private static final int OUTPUT_X = 63;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public ElementExtractorRecipeCategory(IGuiHelper guiHelper)
    {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.ELEMENT_EXTRACTOR.get()));
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("container.mythic_relic.element_extractor");
    }

    @Override
    public RecipeType<ElementExtractorRecipe> getRecipeType()
    {
        return MythicRelicJeiPlugin.ELEMENT_EXTRACTOR;
    }

    @Override
    public Component getTitle()
    {
        return this.title;
    }

    @Override
    public IDrawable getBackground()
    {
        return this.background;
    }

    @Override
    public int getWidth()
    {
        return WIDTH;
    }

    @Override
    public int getHeight()
    {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon()
    {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ElementExtractorRecipe recipe, IFocusGroup focuses)
    {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y).addIngredients(recipe.getIngredient());

        List<ItemStack> results = recipe.getResults();
        for (int i = 0; i < results.size(); i++)
        {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + i * 18, SLOT_Y)
                    .addItemStack(results.get(i));
        }
    }

    @Override
    public void draw(ElementExtractorRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY)
    {
        this.arrow.draw(guiGraphics, ARROW_X, SLOT_Y);
    }

    /** 鼠标停在那支箭头上时，告诉玩家这一炉要烧多久。 */
    @Override
    public List<Component> getTooltipStrings(ElementExtractorRecipe recipe, IRecipeSlotsView slotsView,
                                             double mouseX, double mouseY)
    {
        if (mouseX >= ARROW_X && mouseX <= ARROW_X + this.arrow.getWidth()
                && mouseY >= SLOT_Y && mouseY <= SLOT_Y + this.arrow.getHeight())
        {
            return List.of(Component.translatable("jei.mythic_relic.element_extractor.duration",
                    recipe.getDuration() / 20));
        }
        return List.of();
    }

    @Override
    public ResourceLocation getRegistryName(ElementExtractorRecipe recipe)
    {
        return recipe.getId();
    }
}
