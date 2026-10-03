package com.mythicrelic.curse;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.item.HegniSwordItem;
import com.mythicrelic.registry.ModMobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「赫格尼之剑」的全部战斗效果。
 *
 * <h2>命中时</h2>
 * <ul>
 *   <li>附加伤害：目标最大生命的 3%（基础 20 点走的是武器属性，不在这里）；</li>
 *   <li>给目标挂上 {@link ModMobEffects#HEGNI_CURSE 赫格尼的诅咒}。</li>
 * </ul>
 *
 * <h2>诅咒的效果</h2>
 * <ul>
 *   <li>{@link LivingHealEvent} —— 一切回血都失效（自然恢复、药水、金苹果都一样）；</li>
 *   <li>{@link LivingUseTotemEvent} —— 常规不死图腾不会触发，该死就是死。</li>
 * </ul>
 *
 * <h2>拔剑者的特权</h2>
 * <p>{@link MobEffectEvent.Applicable} 里拦掉凋零。注意判据是
 * {@link #hasClaimed}——「<b>拔出过</b>这把剑」这个永久标记，不是「此刻拿着剑」：
 * 拔出来那一刻就算获得了剑的认可，此后一辈子免疫，不必一直攥在手里。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class HegniCurseEvents
{
    /** 命中后诅咒持续多久（tick）。20 秒。 */
    private static final int CURSE_DURATION = 400;

    private HegniCurseEvents() {}

    // —————————————————————— 命中 ——————————————————————

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (!(event.getSource().getEntity() instanceof Player player) || !holdsSword(player))
        {
            return;
        }
        LivingEntity target = event.getEntity();
        event.setAmount(event.getAmount()
                + target.getMaxHealth() * HegniSwordItem.BONUS_MAX_HEALTH_RATIO);
        // 不显示粒子（false）但显示图标（true）——被砍的人得知道自己中招了
        target.addEffect(new MobEffectInstance(ModMobEffects.HEGNI_CURSE.get(),
                CURSE_DURATION, 0, false, true, true));
    }

    // —————————————————————— 诅咒：不能回血 / 图腾无效 ——————————————————————

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event)
    {
        if (event.getEntity().hasEffect(ModMobEffects.HEGNI_CURSE.get()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseTotem(LivingUseTotemEvent event)
    {
        if (event.getEntity().hasEffect(ModMobEffects.HEGNI_CURSE.get()))
        {
            event.setCanceled(true);
        }
    }

    // —————————————————————— 持有者免疫凋零 ——————————————————————

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEffectInstance().getEffect() != MobEffects.WITHER)
        {
            return;
        }
        // 免疫挂在「拔出过剑」这个**永久**标记上，不是「此刻手里拿着剑」。
        // 拔出来那一刻就算获得了剑的认可，此后一辈子免疫，不必一直攥着。
        if (event.getEntity() instanceof Player player && hasClaimed(player))
        {
            // 这个事件不是 @Cancelable，是 @HasResult，用 DENY 表达「不要施加」
            event.setResult(Event.Result.DENY);
        }
    }

    /** 玩家是否已经拔出过赫格尼之剑（永久标记，存在玩家数据里）。 */
    public static boolean hasClaimed(Player player)
    {
        ModCurse.Data data = ModCurse.get(player);
        return data != null && data.hegniClaimed();
    }

    // —————————————————————— 工具 ——————————————————————

    /** 主手或副手拿着赫格尼之剑。 */
    public static boolean holdsSword(Player player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof HegniSwordItem)
            {
                return true;
            }
        }
        return false;
    }
}
