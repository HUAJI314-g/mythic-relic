package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的自定义粒子。
 *
 * <p>目前只有一个 {@link #CHAOS_MOTE}，做它的理由很实在：<b>原版粒子改不了寿命</b>。</p>
 *
 * <p>蓄力原本用的是原版 {@code PORTAL} 和 {@code DRAGON_BREATH}，寿命分别约 40~50 tick
 * 和 16~80 tick（最长 4 秒），而这两个数在 {@code Particle} 里是 {@code protected} 且
 * 没有任何外部入口——{@code level.addParticle()} 只管位置和速度。
 * 结果就是松手之后那团紫雾要飘好几秒才散干净。自己注册一个类型，寿命才真正变成一个数。</p>
 *
 * <p>客户端要另外在 {@code ClientEvents} 里挂 provider（{@code RegisterParticleProvidersEvent}），
 * 否则粒子注册得上、却什么都不显示；贴图清单在
 * {@code assets/mythic_relic/particles/chaos_mote.json}，缺了那个文件同样不显示。</p>
 */
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MythicRelic.MODID);

    /** 暗影龙刃蓄力用的「混沌微尘」：深紫黑、寿命很短，一停手就散。 */
    public static final RegistryObject<SimpleParticleType> CHAOS_MOTE =
            PARTICLES.register("chaos_mote", () -> new SimpleParticleType(false));

    /**
     * 同一颗微尘的<b>墨黑</b>版本。
     *
     * <p>蓄力只有紫色的话整团偏「淡」——短寿命粒子同时在场的数量本来就少。
     * 现在每一颗紫色都配一颗等量的黑色（同一个类，只是换了基色），
     * 紫的颗数一颗没少，整团却厚了一倍。</p>
     *
     * <p>两种颜色的基色常量都在 {@code ChaosMoteParticle} 里，形状、寿命、大小完全共用。</p>
     */
    public static final RegistryObject<SimpleParticleType> CHAOS_MOTE_DARK =
            PARTICLES.register("chaos_mote_dark", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
