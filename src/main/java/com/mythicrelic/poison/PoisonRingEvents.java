package com.mythicrelic.poison;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.accessory.ModAccessories;
import com.mythicrelic.registry.ModItems;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 「百毒不侵之戒」的判定。
 *
 * <h2>什么时候生效</h2>
 * <p>判据是「<b>饰品栏里有没有这枚戒指</b>」，也就是真的戴在身上——光攥在手里没用。
 * 这点和神棱偏转镜不一样（镜子是右键激活的，戒指是常驻的）。</p>
 *
 * <h2>两道防线</h2>
 * <ol>
 *   <li><b>挡新的</b>：{@link MobEffectEvent.Applicable} 里，凡是
 *       {@link MobEffectCategory#HARMFUL} 的效果一律 {@code DENY}。
 *       注意这个事件不是 {@code @Cancelable}，是 {@code @HasResult}，用 DENY 表达
 *       「不要施加」（和赫格尼之剑拦凋零是同一个写法）；</li>
 *   <li><b>化旧的</b>：每秒扫一次，把身上已经挂着的负面效果清掉。
 *       否则戴上戒指的那一刻，先前中的毒还得自己熬过去，「百毒不侵」就不成立了。</li>
 * </ol>
 *
 * <p>注意别在遍历 {@code getActiveEffects()} 的时候直接删——会
 * {@code ConcurrentModificationException}，所以先拷一份要删的列表。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class PoisonRingEvents
{
    /** 每隔多少 tick 化一次身上已有的负面效果。 */
    private static final int CLEANSE_INTERVAL = 20;

    private PoisonRingEvents() {}

    /** 饰品栏里有没有这枚戒指。 */
    public static boolean hasRing(Player player)
    {
        return player.getCapability(ModAccessories.ACCESSORIES)
                .map(accessories -> accessories.contains(ModItems.POISON_RING.get()))
                .orElse(false);
    }

    // —————————————————————— 挡下新的负面效果 ——————————————————————

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.HARMFUL)
        {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !hasRing(player))
        {
            return;
        }
        // 这个事件是 @HasResult 而不是 @Cancelable，用 DENY 表达「不要施加」
        event.setResult(Event.Result.DENY);
    }

    // —————————————————————— 化掉已有的负面效果 ——————————————————————

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % CLEANSE_INTERVAL != 0)
        {
            return;
        }
        if (!hasRing(player))
        {
            return;
        }

        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects())
        {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL)
            {
                toRemove.add(instance.getEffect());
            }
        }
        for (MobEffect effect : toRemove)
        {
            player.removeEffect(effect);
        }
    }
}
