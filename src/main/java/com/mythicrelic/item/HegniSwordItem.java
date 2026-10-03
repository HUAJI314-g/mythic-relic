package com.mythicrelic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「赫格尼之剑」——从诅咒祭坛上拔下来的重剑。
 *
 * <h2>数值</h2>
 * <p>基础攻击力 {@value #BASE_ATTACK_DAMAGE} 点，另外还有 3% 目标最大生命的附加伤害
 * （见 {@code curse/HegniCurseEvents}，那部分走事件加，不在属性里）。
 * 命中时会给目标挂上「赫格尼的诅咒」：无法回血，且不死图腾救不回来。</p>
 *
 * <p>属性怎么算出 20 的：{@code SwordItem} 的总攻击力 =
 * 玩家基础 1 + (attackDamageModifier + 材质加成)。下界合金的加成是 4，
 * 所以这里传 {@code 15}：1 + 15 + 4 = 20。</p>
 *
 * <p>反过来，<b>拔出这把剑的人永久免疫凋零</b>——祭坛那边用凋零逼你手快，
 * 一旦拔出来就算获得了剑的认可，此后一辈子免疫，不必一直攥在手里
 * （判据是玩家数据里的「拔出过」标记，见 {@code HegniCurseEvents#hasClaimed}）。</p>
 */
public class HegniSwordItem extends SwordItem
{
    /** 基础攻击力（点）。 */
    public static final double BASE_ATTACK_DAMAGE = 20.0D;
    /** 附加伤害：目标最大生命的这个比例。 */
    public static final float BONUS_MAX_HEALTH_RATIO = 0.03F;

    /** 下界合金的加成是 4，玩家基础 1 —— 补到 20 需要 15。 */
    private static final int DAMAGE_MODIFIER = 15;
    private static final float ATTACK_SPEED_MODIFIER = -2.4F;

    public HegniSwordItem(Properties properties)
    {
        super(Tiers.NETHERITE, DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.hegni_sword.1",
                String.format("%.0f", BASE_ATTACK_DAMAGE)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.mythic_relic.hegni_sword.2",
                String.format("%.0f", BONUS_MAX_HEALTH_RATIO * 100.0F)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.mythic_relic.hegni_sword.3").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.mythic_relic.hegni_sword.4").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.mythic_relic.hegni_sword.5").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return true;
    }
}
