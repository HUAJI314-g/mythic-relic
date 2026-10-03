package com.mythicrelic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 蓄力用的「混沌微尘」——自己写一个粒子，唯一的理由是<b>寿命可以定</b>。
 *
 * <p>原版 {@code PORTAL} / {@code DRAGON_BREATH} 的寿命分别约 40~50 与 16~80 tick
 * （最长 4 秒），而原版<b>没有任何外部手段能改粒子寿命</b>（{@code lifetime} 是
 * {@code protected}，也没有 setter），所以松手之后那团紫雾要飘好几秒才散，
 * 看着就是「很久才能散去」。</p>
 *
 * <p>这里把寿命压到 {@link #LIFETIME_MIN}~{@link #LIFETIME_MAX} tick（约 0.3~0.6 秒），
 * 尾段还会淡出。贴图直接借原版的 {@code generic_0~7}（八张轮播，有点微微闪烁）。</p>
 *
 * <p><b>两种颜色共用这一个类</b>：基色由 provider 从构造函数传进来——暗紫
 * （{@link #PURPLE_RED} 那一组）和墨黑（{@link #DARK_RED} 那一组，取的是暗影龙刃刃身
 * 那个近黑暗紫）。蓄力时每颗紫的都配一颗等量的黑的，颗数不变、整团更厚。</p>
 */
public class ChaosMoteParticle extends TextureSheetParticle
{
    /**
     * 寿命上下限（tick）。
     *
     * <p><b>这就是「多久散去」那个旋钮</b>：想让它散得慢一点就往上调，
     * 12 → 1 tick = 0.05 秒，20 就是 1 秒。</p>
     */
    private static final int LIFETIME_MIN = 6;
    private static final int LIFETIME_MAX = 12;

    /** 粒子大小上下限（格）。调大一圈，整团会明显更「实」。 */
    private static final float SIZE_MIN = 0.08F;
    private static final float SIZE_MAX = 0.18F;

    /** 明暗抖动下限：每颗再乘 0.55~1.0，整团才不会看成一块平色。 */
    private static final float SHADE_MIN = 0.55F;

    /** 暗紫基色。 */
    public static final float PURPLE_RED = 0.44F;
    public static final float PURPLE_GREEN = 0.22F;
    public static final float PURPLE_BLUE = 0.78F;

    /** 墨黑基色：直接取暗影龙刃刃身那个近黑的暗紫 {@code (24, 9, 42)}。 */
    public static final float DARK_RED = 24 / 255.0F;
    public static final float DARK_GREEN = 9 / 255.0F;
    public static final float DARK_BLUE = 42 / 255.0F;

    private final SpriteSet sprites;
    private final float red;
    private final float green;
    private final float blue;

    protected ChaosMoteParticle(ClientLevel level, double x, double y, double z,
                                double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites,
                                float red, float green, float blue)
    {
        super(level, x, y, z);
        this.sprites = sprites;
        this.red = red;
        this.green = green;
        this.blue = blue;

        this.setParticleSpeed(xSpeed, ySpeed, zSpeed);
        this.lifetime = LIFETIME_MIN + this.random.nextInt(LIFETIME_MAX - LIFETIME_MIN + 1);
        this.quadSize = SIZE_MIN + this.random.nextFloat() * (SIZE_MAX - SIZE_MIN);
        // 不落不飘，只带一点阻尼——寿命本来就短，再让它乱飞就散了
        this.gravity = 0.0F;
        this.friction = 0.88F;
        this.hasPhysics = false;

        float shade = SHADE_MIN + this.random.nextFloat() * (1.0F - SHADE_MIN);
        this.setColor(red * shade, green * shade, blue * shade);
        this.setAlpha(1.0F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick()
    {
        super.tick();
        // 八张 generic 贴图按年龄轮播
        this.setSpriteFromAge(this.sprites);
        // 尾段淡出：越接近寿命末端越透明
        this.setAlpha(1.0F - (float) this.age / (float) this.lifetime);
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        // 原版默认是 OPAQUE，那种下面 setAlpha() 是不起作用的，必须换半透明
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 客户端注册用的 provider（见 {@code ClientEvents}）：一个 provider 认一种基色。 */
    public static class Provider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;
        private final float red;
        private final float green;
        private final float blue;

        public Provider(SpriteSet sprites, float red, float green, float blue)
        {
            this.sprites = sprites;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed)
        {
            return new ChaosMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                    this.sprites, this.red, this.green, this.blue);
        }
    }
}
