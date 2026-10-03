package com.mythicrelic.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mythicrelic.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 元素提取器的配方：一份输入 → 至多 {@value #MAX_RESULTS} 份产出。
 *
 * <h2>为什么是「多产出」</h2>
 * <p>一台机器现在有 3 个并排的输出槽，因为有些原料能同时榨出好几层东西——
 * 比如末影珍珠既含混沌、又含空间；紫颂果同理。所以配方的结果从单个
 * {@code result} 变成了 {@code results} 数组，长度 1~3，正好对齐三个输出槽。</p>
 *
 * <p>JSON 两种写法都吃：</p>
 * <pre>
 *   "results": [ { "item": "mythic_relic:chaos_trace", "count": 1 },
 *                { "item": "mythic_relic:space_trace", "count": 2 } ]
 *   "result":  { "item": "mythic_relic:chaos_trace", "count": 1 }     // 旧写法，等价于只有一项的 results
 * </pre>
 *
 * <p>原版配方书 / JEI 这类只认「单一产出」的地方，读的是
 * {@link #getResultItem(RegistryAccess)}——这里约定返回<b>第一项</b>。</p>
 */
public class ElementExtractorRecipe implements Recipe<SimpleContainer>
{
    public static final int DEFAULT_DURATION = 200;

    /** 最多几个产出，和提取器的输出槽数量一一对应。 */
    public static final int MAX_RESULTS = 3;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<ItemStack> results;
    private final int duration;

    public ElementExtractorRecipe(ResourceLocation id, Ingredient ingredient, List<ItemStack> results, int duration)
    {
        this.id = id;
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
        this.duration = duration;
    }

    public int getDuration()
    {
        return this.duration;
    }

    public Ingredient getIngredient()
    {
        return this.ingredient;
    }

    /** 全部产出（1~{@value #MAX_RESULTS} 项）。 */
    public List<ItemStack> getResults()
    {
        return this.results;
    }

    @Override
    public boolean matches(SimpleContainer container, Level level)
    {
        return this.ingredient.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess)
    {
        return this.results.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height)
    {
        return true;
    }

    /** 单产出接口（配方书 / JEI 用）——取第一项。完整产出看 {@link #getResults()}。 */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess)
    {
        return this.results.get(0);
    }

    @Override
    public ResourceLocation getId()
    {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return ModRecipes.ELEMENT_EXTRACTOR_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return ModRecipes.ELEMENT_EXTRACTOR_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<ElementExtractorRecipe>
    {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ElementExtractorRecipe fromJson(ResourceLocation id, JsonObject json)
        {
            // 直接吃 JsonElement：ingredient 既可以是 { "item": ... } / { "tag": ... }，
            // 也可以是一个数组（比如「普通玻璃 + 玻璃板」这种多来源写法）。
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));

            List<ItemStack> results = new ArrayList<>();
            if (json.has("results"))
            {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "results"))
                {
                    results.add(ShapedRecipe.itemStackFromJson(element.getAsJsonObject()));
                }
            }
            else
            {
                // 兼容只有单个 result 的旧写法
                results.add(ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
            }

            if (results.isEmpty())
            {
                throw new JsonParseException("元素提取器配方至少要有一个产出：" + id);
            }
            if (results.size() > MAX_RESULTS)
            {
                throw new JsonParseException("元素提取器配方最多 " + MAX_RESULTS + " 个产出（输出槽就这么多）：" + id);
            }

            int duration = GsonHelper.getAsInt(json, "duration", DEFAULT_DURATION);
            return new ElementExtractorRecipe(id, ingredient, results, duration);
        }

        @Override
        public ElementExtractorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            List<ItemStack> results = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
            {
                results.add(buffer.readItem());
            }
            int duration = buffer.readVarInt();
            return new ElementExtractorRecipe(id, ingredient, results, duration);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElementExtractorRecipe recipe)
        {
            recipe.ingredient.toNetwork(buffer);
            buffer.writeVarInt(recipe.results.size());
            for (ItemStack stack : recipe.results)
            {
                buffer.writeItem(stack);
            }
            buffer.writeVarInt(recipe.duration);
        }
    }
}
