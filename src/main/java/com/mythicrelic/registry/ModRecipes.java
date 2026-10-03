package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.recipe.ElementExtractorRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes
{
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MythicRelic.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MythicRelic.MODID);

    public static final RegistryObject<RecipeType<ElementExtractorRecipe>> ELEMENT_EXTRACTOR_TYPE =
            RECIPE_TYPES.register("element_extractor", () -> new RecipeType<ElementExtractorRecipe>() {});

    public static final RegistryObject<RecipeSerializer<ElementExtractorRecipe>> ELEMENT_EXTRACTOR_SERIALIZER =
            RECIPE_SERIALIZERS.register("element_extractor", () -> ElementExtractorRecipe.Serializer.INSTANCE);

    private ModRecipes() {}
}
