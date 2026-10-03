package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组的 MobEffect 注册。
 *
 * <p>MobEffect 是原版注册表（不是 Forge 扩展），所以用 {@code Registries.MOB_EFFECT}。
 * Forge 1.20.1 里 {@code MobEffect} 构造器是 protected，必须继承。</p>
 */
public final class ModMobEffects
{
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MythicRelic.MODID);

    /**
     * 死亡回溯：只是视觉效果，显示右上药水栏倒计时。
     * 颜色 0xC8DFFF（冷白淡蓝）。
     */
    public static final RegistryObject<MobEffect> DEATH_REGRESSION = MOB_EFFECTS.register("death_regression",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC8DFFF) {});

    /**
     * 赫格尼的诅咒：<b>无法回血，且常规不死图腾不会触发</b>。
     *
     * <p>它本身不造成任何伤害，纯粹是个「标记」——真正的判定在
     * {@code curse/HegniCurseEvents} 里（拦 {@code LivingHealEvent} 与
     * {@code LivingUseTotemEvent}）。颜色 0x6E0B14，干涸的血色。</p>
     */
    public static final RegistryObject<MobEffect> HEGNI_CURSE = MOB_EFFECTS.register("hegni_curse",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6E0B14) {});

    /**
     * 神棱偏转：{@code 神棱偏转镜} 的生效窗口（默认 60 秒）。
     *
     * <p>它同样只是个「标记」——真正的判定在 {@code mirror/PrismMirrorEvents} 里：
     * 带着它的时候，<b>有源伤害全额反弹</b>、<b>无源伤害与自伤直接免疫</b>。
     * 颜色 0xBFEAFF，棱镜的冷青白。</p>
     */
    public static final RegistryObject<MobEffect> PRISM_DEFLECTION = MOB_EFFECTS.register("prism_deflection",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xBFEAFF) {});

    private ModMobEffects() {}
}
