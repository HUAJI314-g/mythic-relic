package com.mythicrelic.chaos;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.network.CommentaryPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 尼德霍格对玩家身边发生的事随口点评两句。
 *
 * <p>触发条件：玩家<b>戴着尼德霍格之印</b>，且发生下面三件事之一——</p>
 * <ul>
 *   <li><b>重生</b>：每次都会说一句；</li>
 *   <li><b>击败凋零</b>：由击杀者判定，同样每次都说；</li>
 *   <li><b>拿到本模组的物品</b>：<b>每件东西只说一次</b>，说过就记进
 *       {@link PlayerChaos#markCommented}，落盘保存，重登也不会再说。</li>
 * </ul>
 *
 * <p>评论本身走 {@link CommentaryPacket} 发给本人，客户端负责把正文渲染成字幕、
 * 用 {@code DragonTongue} 现场编译出龙语音节念出来。</p>
 *
 * <p>两处节流：物品是「一件一次」，另外所有评论之间还隔着一个全局冷却
 * （{@value #COMMENT_COOLDOWN_TICKS} tick）。冷却期内触发的会<b>被丢弃而不是排队</b>——
 * 物品的那条因为没被标记成「已评论」，下次再拿到还会补上，所以不会永久丢失。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class NidhoggCommentary
{
    public static final String KEY_RESPAWN = "commentary.mythic_relic.respawn";
    public static final String KEY_WITHER = "commentary.mythic_relic.wither";

    /** 两条评论之间的最短间隔（tick）。 */
    private static final int COMMENT_COOLDOWN_TICKS = 100;

    /**
     * 会对哪些物品发表评论。
     *
     * <p>存的是注册路径——语言键由 {@code commentary.mythic_relic.item.<路径>} 推出来，
     * 所以加一件新东西只需要在这里补一个名字、再去 lang 里补一条文案。</p>
     *
     * <p>取舍标准是<b>「常驻玩法循环里的东西」</b>，而不是「第一次拿到是不是在印记之前」——
     * 后者几乎筛不掉任何东西，因为下面这些物品在仪式前就全都露过面了。真正不收的是
     * <b>封印之地那一章的一次性剧情道具</b>：</p>
     * <ul>
     *   <li>{@code chaos_key}——打完末影龙拿到，是进入封印之地的门票；</li>
     *   <li>{@code chaos_rune} / {@code chaos_altar}——她封印上的符文、她被压着的祭坛。</li>
     * </ul>
     *
     * <p>而 {@code element_extractor} 与 {@code chaos_trace / chaos_fragment / chaos_crystal}
     * 虽然是仪式之前就要准备好的，但它们<b>同时也是印记之后一直在用的那套东西</b>：
     * 提取器只要黑曜石 + 活塞 + 漏斗就能再造（一点混沌材料都不要），
     * 黑曜石本身还能无限提取出痕迹。所以照收——她看着你一点一点榨取她的力量，
     * 这句话本来就该有。</p>
     */
    private static final Set<String> COMMENTED_ITEMS = Set.of(
            "element_extractor",
            "chaos_trace",
            "chaos_fragment",
            "chaos_crystal",
            "nidhogg_mark",
            "death_trace",
            "death_fragment",
            "death_crystal",
            "death_heart",
            "space_trace",
            "space_fragment",
            "space_crystal",
            "prism_trace",
            "prism_fragment",
            "prism_crystal",
            "prism_mirror",
            "toxic_trace",
            "toxic_fragment",
            "toxic_crystal",
            "poison_ring",
            "mythic_book",
            "rift_feather",
            "hegni_sword");

    /** 上次说话的世界时间，key 是玩家 UUID。 */
    private static final Map<UUID, Long> LAST_COMMENT = new HashMap<>();

    private NidhoggCommentary() {}

    // —————————————————————— 触发 ——————————————————————

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        // isEndConquered 是从末地打完龙回主世界，那不算「死过一回」。
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        speak(player, KEY_RESPAWN, null);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof WitherBoss))
        {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player)
        {
            speak(player, KEY_WITHER, null);
        }
    }

    /** 从地上捡起来。 */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            speakForItem(player, event.getItem().getItem());
        }
    }

    /** 工作台里做出来——那条路不会触发拾取事件，所以得单独接一次。 */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            speakForItem(player, event.getCrafting());
        }
    }

    // —————————————————————— 内部 ——————————————————————

    /**
     * 针对某件物品说一句。
     *
     * @param key 用于「说过就不再重复」的标记；{@code null} 表示不记，每次都允许说
     */
    private static void speak(ServerPlayer player, String translationKey, String key)
    {
        speak(player, translationKey, key, false);
    }

    /**
     * @param bypassCooldown 跳过全局冷却。
     *
     * <p>只给「一辈子只发一次」的东西用——比如从诅咒祭坛里拔出来的赫格尼之剑：
     * 那一句要是正好撞上冷却被丢掉，就<b>再也没有第二次机会</b>了
     * （祭坛拔完就没了，剑也不会再发第二把）。见 {@link #onGranted}。</p>
     */
    private static void speak(ServerPlayer player, String translationKey, String key, boolean bypassCooldown)
    {
        if (!ChaosEvents.hasMark(player))
        {
            return;
        }
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null)
        {
            return;
        }
        if (key != null)
        {
            if (chaos.hasCommented(key))
            {
                return;
            }
        }

        long now = player.level().getGameTime();
        Long last = LAST_COMMENT.get(player.getUUID());
        if (!bypassCooldown && last != null && now - last < COMMENT_COOLDOWN_TICKS)
        {
            // 冷却期内不记「已评论」，这样这件东西下次还有机会被念叨
            return;
        }
        LAST_COMMENT.put(player.getUUID(), now);
        if (key != null)
        {
            chaos.markCommented(key);
        }

        CommentaryPacket.send(player, translationKey);
    }

    /**
     * 模组<b>直接发放</b>物品时，手动触发一次评论。
     *
     * <p>评论原本只挂在 {@link EntityItemPickupEvent 捡起} 与
     * {@link PlayerEvent.ItemCraftedEvent 合成} 两个事件上，但模组自己给的东西
     * 是直接塞进背包的——仪式给的「尼德霍格之印」进的是饰品栏能力，
     * 祭坛拔出来的「赫格尼之剑」走的是 {@code getInventory().add()}——
     * 两条路都不经过那两个事件，所以它们<b>一直不吭声</b>。</p>
     *
     * <p>凡是绕过「捡起 / 合成」把物品塞给玩家的地方，都要记得调这个。</p>
     */
    public static void onGranted(ServerPlayer player, ItemStack stack)
    {
        // 直接发放的东西一辈子只有一次，允许它跳过全局冷却
        speakForItem(player, stack, true);
    }

    private static void speakForItem(ServerPlayer player, ItemStack stack)
    {
        speakForItem(player, stack, false);
    }

    private static void speakForItem(ServerPlayer player, ItemStack stack, boolean bypassCooldown)
    {
        if (stack.isEmpty())
        {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !MythicRelic.MODID.equals(id.getNamespace()))
        {
            return;
        }
        if (!COMMENTED_ITEMS.contains(id.getPath()))
        {
            return;
        }
        speak(player, "commentary.mythic_relic.item." + id.getPath(), "item:" + id.getPath(), bypassCooldown);
    }
}
