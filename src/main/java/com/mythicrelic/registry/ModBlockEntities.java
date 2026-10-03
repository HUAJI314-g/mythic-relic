package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.blockentity.ElementExtractorBlockEntity;
import com.mythicrelic.blockentity.HegniAltarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MythicRelic.MODID);

    public static final RegistryObject<BlockEntityType<ElementExtractorBlockEntity>> ELEMENT_EXTRACTOR =
            BLOCK_ENTITIES.register("element_extractor", () -> BlockEntityType.Builder
                    .of(ElementExtractorBlockEntity::new, ModBlocks.ELEMENT_EXTRACTOR.get())
                    .build(null));

    /** 诅咒祭坛：负责在方块上方举一个显示实体来渲染那把剑。 */
    public static final RegistryObject<BlockEntityType<HegniAltarBlockEntity>> HEGNI_ALTAR =
            BLOCK_ENTITIES.register("hegni_altar", () -> BlockEntityType.Builder
                    .of(HegniAltarBlockEntity::new, ModBlocks.HEGNI_ALTAR.get())
                    .build(null));

    private ModBlockEntities() {}
}
