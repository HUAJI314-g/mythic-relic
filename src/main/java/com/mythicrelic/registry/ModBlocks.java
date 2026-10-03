package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.block.ChaosAltarBlock;
import com.mythicrelic.block.ChaosRuneBlock;
import com.mythicrelic.block.ElementExtractorBlock;
import com.mythicrelic.block.HegniAltarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MythicRelic.MODID);

    public static final RegistryObject<Block> ELEMENT_EXTRACTOR = BLOCKS.register("element_extractor",
            ElementExtractorBlock::new);

    public static final RegistryObject<Block> CHAOS_ALTAR = BLOCKS.register("chaos_altar",
            ChaosAltarBlock::new);

    /** 封印符文方块：广场上的符文环与咒文都用它铺，仪式灌体时整片点亮。 */
    public static final RegistryObject<Block> CHAOS_RUNE = BLOCKS.register("chaos_rune",
            ChaosRuneBlock::new);

    /**
     * 诅咒祭坛：赫格尼之剑插在中心的那块石头。
     *
     * <p><b>故意不做成物品</b>——它只能由 {@code CursedAltarEvents} 在主世界随机搭出来，
     * 玩家没法自己摆一个。所以它也不进创造模式物品栏。</p>
     */
    public static final RegistryObject<Block> HEGNI_ALTAR = BLOCKS.register("hegni_altar",
            HegniAltarBlock::new);

    private ModBlocks() {}
}
