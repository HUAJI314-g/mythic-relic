package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.item.ChaosEssenceItem;
import com.mythicrelic.item.ChaosKeyItem;
import com.mythicrelic.item.DeathHeartItem;
import com.mythicrelic.item.HegniSwordItem;
import com.mythicrelic.item.MythicBookItem;
import com.mythicrelic.item.NidhoggMarkItem;
import com.mythicrelic.item.PoisonRingItem;
import com.mythicrelic.item.PrismMirrorItem;
import com.mythicrelic.item.RiftFeatherItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MythicRelic.MODID);

    public static final RegistryObject<Item> ELEMENT_EXTRACTOR_ITEM = ITEMS.register("element_extractor",
            () -> new BlockItem(ModBlocks.ELEMENT_EXTRACTOR.get(), new Item.Properties()));

    // 三种混沌能量物质同时也是消耗品：右键食用即可换取一份混沌进度。
    public static final RegistryObject<Item> CHAOS_TRACE = ITEMS.register("chaos_trace",
            () -> new ChaosEssenceItem(new Item.Properties(), 10));

    public static final RegistryObject<Item> CHAOS_FRAGMENT = ITEMS.register("chaos_fragment",
            () -> new ChaosEssenceItem(new Item.Properties(), 40));

    public static final RegistryObject<Item> CHAOS_CRYSTAL = ITEMS.register("chaos_crystal",
            () -> new ChaosEssenceItem(new Item.Properties(), 150));

    public static final RegistryObject<Item> CHAOS_ALTAR_ITEM = ITEMS.register("chaos_altar",
            () -> new BlockItem(ModBlocks.CHAOS_ALTAR.get(), new Item.Properties()));

    public static final RegistryObject<Item> CHAOS_RUNE_ITEM = ITEMS.register("chaos_rune",
            () -> new BlockItem(ModBlocks.CHAOS_RUNE.get(), new Item.Properties()));

    public static final RegistryObject<Item> NIDHOGG_MARK = ITEMS.register("nidhogg_mark",
            () -> new NidhoggMarkItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> CHAOS_KEY = ITEMS.register("chaos_key",
            () -> new ChaosKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // 死亡能量三件套（原料，非消耗品）
    public static final RegistryObject<Item> DEATH_TRACE = ITEMS.register("death_trace",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DEATH_FRAGMENT = ITEMS.register("death_fragment",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DEATH_CRYSTAL = ITEMS.register("death_crystal",
            () -> new Item(new Item.Properties()));

    // 死亡之心：饰品，可自由佩戴/取下，死亡回溯一次
    public static final RegistryObject<Item> DEATH_HEART = ITEMS.register("death_heart",
            () -> new DeathHeartItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // 破界之羽：右键瞬移至视线所指，潜行右键开坐标界面（从 riftfeather 模组搬来）
    public static final RegistryObject<Item> RIFT_FEATHER = ITEMS.register("rift_feather",
            () -> new RiftFeatherItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    // 赫格尼之剑：从诅咒祭坛上点满一百下拔出来的重剑
    public static final RegistryObject<Item> HEGNI_SWORD = ITEMS.register("hegni_sword",
            () -> new HegniSwordItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // —————————————— 空间能量三件套 ——————————————
    // 末影珍珠 / 紫颂果在提取混沌之余，还会漏出「空间」这一层。
    // 和死亡能量一样是纯原料，不做消耗品。
    public static final RegistryObject<Item> SPACE_TRACE = ITEMS.register("space_trace",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SPACE_FRAGMENT = ITEMS.register("space_fragment",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SPACE_CRYSTAL = ITEMS.register("space_crystal",
            () -> new Item(new Item.Properties()));

    // —————————————— 棱镜能量三件套 ——————————————
    // 玻璃类材料（普通玻璃 / 染色玻璃 / 遮光玻璃）提取出来的光之精华。
    public static final RegistryObject<Item> PRISM_TRACE = ITEMS.register("prism_trace",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> PRISM_FRAGMENT = ITEMS.register("prism_fragment",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> PRISM_CRYSTAL = ITEMS.register("prism_crystal",
            () -> new Item(new Item.Properties()));

    // 神棱偏转镜：5 棱镜碎片 + 4 空间碎片合成。右键开 60 秒偏转窗口，
    // 也能戴进饰品栏，血量低于 5 点时自动触发。
    public static final RegistryObject<Item> PRISM_MIRROR = ITEMS.register("prism_mirror",
            () -> new PrismMirrorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // —————————————— 剧毒能量三件套 ——————————————
    // 蜘蛛眼 / 剧毒马铃薯 / 河豚榨出痕迹，发酵蛛眼出碎片，凋零玫瑰出结晶。
    public static final RegistryObject<Item> TOXIC_TRACE = ITEMS.register("toxic_trace",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> TOXIC_FRAGMENT = ITEMS.register("toxic_fragment",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> TOXIC_CRYSTAL = ITEMS.register("toxic_crystal",
            () -> new Item(new Item.Properties()));

    // 百毒不侵之戒：8 片剧毒碎片围成一圈。戴上后一切负面效果都近不了身。
    public static final RegistryObject<Item> POISON_RING = ITEMS.register("poison_ring",
            () -> new PoisonRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // 神话之书：开局白送一本，之后可以用「书 + 黑曜石」再造。
    // 右键翻开的正是尼德霍格自己写的那本记录。
    public static final RegistryObject<Item> MYTHIC_BOOK = ITEMS.register("mythic_book",
            () -> new MythicBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /**
     * 「模组徽记」——只为当创造模式物品栏的图标，不参与玩法。
     *
     * <p>1.20.1 的 {@code CreativeModeTab.icon} 只收 {@code ItemStack}，没法直接给一张贴图，
     * 所以图标必须由某个物品来承载。这一件<b>故意不放进</b> {@code displayItems}：
     * 物品栏里看不到它，正常玩法也拿不到（要用只能 /give）。</p>
     *
     * <p>贴图 {@code textures/item/mod_icon.png} 由 {@code tools/gen_tab_icon.py} 从封面
     * {@code mythic_relic.png} 缩下来（金环 + 金色 ᛉ + 深紫底）。</p>
     */
    public static final RegistryObject<Item> MOD_ICON = ITEMS.register("mod_icon",
            () -> new Item(new Item.Properties()));

    private ModItems() {}
}
