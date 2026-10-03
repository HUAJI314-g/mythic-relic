package com.mythicrelic.ritual;

import com.mythicrelic.block.ChaosAltarBlock;
import com.mythicrelic.entity.NidhoggEntity;
import com.mythicrelic.registry.ModBlocks;
import com.mythicrelic.registry.ModEntities;
import com.mythicrelic.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 「混沌封印仪式」——把小说《混沌神龙·尼德霍格》的剧情做成玩家可以亲身经历的一段演出。
 *
 * <p>玩家用「混沌之钥」右键末影折跃门后进入封印之地。这里不是把文字丢给玩家读，而是：</p>
 * <ol>
 *   <li>玩家先落在广场外的黑暗里（{@link SealedLand#ENTRY_Z}），眼前一片漆黑——
 *       但广场上的封印符文<b>从一开始就在微弱地闪</b>，那是黑暗里仅有的两点活物之一；</li>
 *   <li>远处只有祭坛亮着——一束幽光直冲穹顶，替玩家指出方向，
 *       <b>剩下的路由玩家自己走过去</b>（时间轴会在这一步停下等他）；</li>
 *   <li>等他真的踏上广场，一尊真实的 {@link NidhoggEntity} 才从黑暗中凝聚成形，悬在祭坛上方；</li>
 *   <li>对白、龙翼扇动，直到广场上所有封印符文一起烧成金色，她化作一道紫光涌入玩家体内。</li>
 * </ol>
 *
 * <p>黑暗效果由 {@link #tickDarkness()} 每 {@value #DARKNESS_REFRESH} tick 续一小段，
 * 恰好覆盖整场仪式，玩家脚踏回主世界的那一 tick 就解除——既不会半途断掉，
 * 也不会被带进主世界。</p>
 *
 * <p>所有状态都从服务端每 tick 推进，不依赖任何客户端逻辑；多人服务器下每个玩家各自独立运行一次。
 * 所有旁白与对白只走左下角的聊天栏，不再往屏幕中央打大字。</p>
 *
 * <h2>她说的话</h2>
 * <p>尼德霍格讲的是上古混沌龙神的语言——按设定，当代人<b>根本不可能听懂</b>，
 * 但她的意思会直接穿过语言落进你的意识里。所以这里：</p>
 * <ul>
 *   <li>聊天栏里出现的仍然是一句通顺的中文，那是你「听懂」的意思（排版上用斜体标出来）；</li>
 *   <li>耳朵里听到的则是 {@link DragonTongue} 用原版怪物音效现场拼出来的一串音节——
 *       低沉的喉音、咔哒声和一声收尾的龙吼，完全不承载语义。</li>
 * </ul>
 * <p>对白区每一句占多少时间由 {@link #buildDialogue} 按龙语的实际长度算出来，
 * 后面的仪式段落整体顺延，改台词不用再手工对齐时间轴。</p>
 */
public class ChaosRitual
{
    /** 时间轴上的一个步骤：到达 {@code at} tick 时执行 {@code action}。 */
    public record Step(int at, Consumer<ChaosRitual> action) {}

    private static final List<Step> SCRIPT = new ArrayList<>();

    private static final ChatFormatting C_NIDHOGG = ChatFormatting.DARK_PURPLE;
    private static final ChatFormatting C_YOU = ChatFormatting.AQUA;

    /** 说话人名字。{@link #speak} 靠它判断这句话该用什么排版。 */
    private static final String SPEAKER_NIDHOGG = "尼德霍格";
    private static final String SPEAKER_YOU = "你";

    // —————— 演出节奏（tick，20 tick = 1 秒） ——————
    /** 解除盲眼、点亮祭坛的时刻。 */
    private static final int REVEAL_AT = 110;
    /** 引路光柱熄灭、交还给符文光环的时刻。 */
    private static final int BEACON_END = 520;
    private static final int EMERGE_START = 620;
    private static final int EMERGE_END = 780;
    /** 她开口之前的最后一段旁白：说明「听不懂，但意思直接落进意识里」。 */
    private static final int DIALOGUE_PRELUDE = 60;
    /** 第一句台词的时刻。 */
    private static final int DIALOGUE_START = 950;

    /**
     * 等玩家走过来的最长时限。
     *
     * <p>正常走完引道只要几秒；万一玩家站着不动（或卡住），最多等两分钟就继续往下演，
     * 免得整场仪式吊在那里。</p>
     */
    private static final int ARRIVAL_TIMEOUT = 2400;

    /**
     * 对白前最后一段旁白的时刻：她开口之前那一刻。
     *
     * <p>这里曾经会顺手给玩家上一个「定身」的缓慢效果，现已取消——演出归演出，
     * 玩家仍然可以自由走动，只是把镜头转向她。</p>
     */
    private static final int PRELUDE_AT = DIALOGUE_START - DIALOGUE_PRELUDE;

    /**
     * 黑暗效果每次续期的时长与间隔。
     *
     * <p>不再一次性给一个「够长」的数字——那样玩家走得慢时黑暗会在半途突然断掉，
     * 走得快时又会一路拖进主世界。改成每 {@link #DARKNESS_REFRESH} tick 续
     * {@link #DARKNESS_CHUNK} tick：黑暗<b>恰好</b>覆盖整场仪式，
     * 到 {@link #returnAt} 那一刻由剧本亲手解除，回到主世界就是亮的。</p>
     */
    private static final int DARKNESS_CHUNK = 600;
    private static final int DARKNESS_REFRESH = 200;

    // —————— 下面这些在静态块里由「龙语」的实际长度算出来，所以不是 final ——————

    /** 对白区结束的时刻。 */
    private static int dialogueEnd;
    /** 广场上封印符文一起亮起的时刻。 */
    private static int runesAt;
    /** 她化作紫光涌入玩家体内的时刻。 */
    private static int absorbAt;
    /** 消散开始 / 结束的时刻。 */
    private static int vanishStart;
    private static int vanishEnd;
    /** 把玩家送回原世界的时刻。 */
    private static int returnAt;
    /** 整场仪式收尾的时刻。 */
    private static int finishAt;

    /**
     * 对白区的最后一句之后，再停多少 tick 才进入仪式段落。
     *
     * <p>也用来把 {@link #runesAt} 之后的所有节点串起来。</p>
     */
    private static final int AFTER_DIALOGUE = 60;
    private static final int RUNES_TO_ABSORB = 110;
    private static final int ABSORB_TO_VANISH = 20;
    private static final int VANISH_LENGTH = 140;
    private static final int VANISH_TO_RETURN = 40;
    private static final int RETURN_TO_FINISH = 180;

    /**
     * 对白区里的一句话。
     *
     * @param speaker 说话人
     * @param color   说话人名字的颜色
     * @param text    台词。玩家看到的是这句话的「意思」
     * @param tongue  {@code true} 表示这句话是用龙语念出来的（会配上音节）；玩家的台词为 {@code false}
     * @param hold    这句话占完之后再停多少 tick（玩家的台词不出声，这就是纯阅读时间）
     */
    private record DialogueLine(String speaker, ChatFormatting color, String text, boolean tongue, int hold) {}

    /**
     * 对白区的全部台词。
     *
     * <p>尼德霍格说的每一句都用 {@link DragonTongue} 编译成一串音节——她讲的是上古龙语，
     * 你一个字也听不懂，但意思会直接浮现在你脑子里，所以聊天栏里照样是通顺的中文。
     * 玩家的两句不出声：那是你自己心里说的话。</p>
     */
    private static final List<DialogueLine> DIALOGUE = List.of(
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG, "终于……有人来了吗？", true, 30),
            new DialogueLine(SPEAKER_YOU, C_YOU, "你是谁？", false, 45),
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG, "一个被封印得快要发霉的老家伙罢了。", true, 25),
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG,
                    "我是上古混沌神龙——尼德霍格。诸神之战后，我被那些神明联手封印在此。", true, 25),
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG,
                    "他们离开前，其中一位对我说：「我们预言，未来的某一天，会有一个人来到这里，"
                            + "把你连同这份封印，一起转移到他的体内。到那时，便是你重见天日之时。」", true, 30),
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG,
                    "你是这不知几万年来，唯一到来的人。可是……你的气息太普通了。你，真的能承受我的力量吗？", true, 25),
            new DialogueLine(SPEAKER_YOU, C_YOU,
                    "我是能手持岩浆、身背 64×25×30 个金块而面不改色的方块人。我对自己的身体有自信。"
                            + "况且——就算撑不住，又能怎样？我见过别人不曾见过的神明，做了别人不敢做的事。"
                            + "就算死了，我也无怨无悔。", false, 50),
            new DialogueLine(SPEAKER_NIDHOGG, C_NIDHOGG,
                    "好！有胆！看来你就是那个预言之人。那么现在——开始吧！", true, 0));

    private static void at(int tick, Consumer<ChaosRitual> action)
    {
        SCRIPT.add(new Step(tick, action));
    }

    /**
     * 尼德霍格台词「说完之后再停多久」的缩放系数。
     *
     * <p>她的每一句龙语本身已经自带尾音和一声收尾龙吼（{@link DragonTongue#totalTicks()}），
     * 在此之上还会按 {@link DialogueLine#hold()} 再停一段。原来那段是按「一句一句慢慢品」
     * 配的，连起来听就有点拖，这里压到 {@value}。</p>
     *
     * <p><b>只作用于她的台词。</b>玩家的台词没有语音，{@code hold} 就是这句话的纯阅读时间，
     * 跟着缩短会让人来不及读，所以原样保留。</p>
     */
    private static final float HOLD_SCALE = 0.5F;

    /**
     * 铺开整个对白区。
     *
     * <p>每一句占多少时间不再由人手工指定，而是由「龙语」本身的长度决定：
     * 改了台词，节奏会自己跟着变。玩家的台词不出声，只按阅读时间给一个固定停顿；
     * 尼德霍格那几句的停顿再乘 {@link #HOLD_SCALE}。</p>
     *
     * @param start 第一句开口的时刻
     * @return 对白区结束的时刻
     */
    private static int buildDialogue(int start)
    {
        int cursor = start;
        for (DialogueLine line : DIALOGUE)
        {
            DragonTongue.Utterance tongue = line.tongue() ? DragonTongue.compile(line.text()) : null;
            at(cursor, r -> r.say(line, tongue));
            int hold = tongue == null ? line.hold() : Math.round(line.hold() * HOLD_SCALE);
            cursor += (tongue == null ? 0 : tongue.totalTicks()) + hold;
        }
        return cursor;
    }

    static
    {
        // ————————————— 序：黑暗降临，玩家自己走向祭坛 —————————————
        at(0, ChaosRitual::begin);
        at(50, r -> {
            r.sound(SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.9F);
            // 小说原文这里是「手电筒发出的光只传播了一米」，但模组里玩家身上并没有光源，
            // 这里也压根没有天光——所以换成不依赖任何道具的说法。
            r.narrate("四周如夜一般漆黑，浓得像是能把光整个吞掉。你伸出手，看不见自己的五指。");
        });
        at(REVEAL_AT, r -> {
            r.clearBlindness();
            r.lightAltar();
            r.narrate("可你分明感觉到——前方，有什么东西在召唤你。");
        });
        at(180, r -> {
            // 小说原文是「一分钟，两分钟，十分钟……不知走了多久」，但这里玩家其实
            // 只走十几格，而且祭坛那道光是看得见的——所以按真实体感重写。
            r.narrate("你朝着那道光走去。脚下的黑石冰凉，一步、两步……路比想象中近得多。");
            // 时间轴在这里停下，等玩家自己走到祭坛前。
            r.awaitArrival();
        });

        // ————————————— 抵达：祭坛与封印 —————————————
        at(200, r -> {
            r.sound(SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.6F);
            r.narrate("光越来越近。你终于看清了它的源头——一座黑石祭坛，被两级石阶托在黑暗的正中央。");
        });
        at(260, r -> {
            r.setLit(true);
            r.sound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.9F, 0.6F);
            r.narrate("祭坛上，上古符咒密密麻麻地排布成一重又一重的封印。");
        });
        at(350, r -> r.narrate("封印的力量强得可怕，仿佛在镇压着什么极其恐怖的存在。"));
        at(430, r -> {
            r.sound(SoundEvents.ENDER_DRAGON_GROWL, 0.7F, 0.5F);
            r.narrate("那些吞噬光亮的黑暗，不过是它泄漏出来的一丝能量。而封印的正中心，是彻底的无光。");
        });
        at(510, r -> {
            r.darkBurst(40);
            r.sound(SoundEvents.ENDER_DRAGON_FLAP, 0.9F, 0.6F);
            r.narrate("就在你思索这封印究竟为何物时——封印中的存在，动了。");
        });

        // ————————————— 尼德霍格现身 —————————————
        at(EMERGE_START - 20, r -> {
            r.spawnNidhogg();
            r.narrate("黑暗开始凝聚、成形，最终化作一个人形。");
        });
        at(EMERGE_END, r -> {
            r.lookAtNidhogg();
            r.sound(SoundEvents.ENDER_DRAGON_AMBIENT, 0.8F, 0.7F);
            r.narrate("那是一名女子，穿着如暗夜星空般的黑色长裙，气质冰冷而高贵，像九幽之下盛开的黑色玫瑰。");
        });
        at(EMERGE_END + 90, r -> r.narrate("她的面容全黑，可比起她，周围的空间竟显得有几分微亮。她背后，是一对同样漆黑的龙翼。"));

        // ————————————— 对白 —————————————
        // 她开口之前，先把这条设定交代清楚：你听不懂她说的话，但意思直接落进了你的意识。
        at(PRELUDE_AT, r -> {
            r.lookAtNidhogg();
            r.narrate("她开口了。那不是任何一种你听过的语言——古老、沙哑，音节之间像有岩石在互相摩擦。");
            r.narrate("可奇怪的是，你一个字也听不懂，意思却清清楚楚地浮现在脑子里：她的话绕过了语言，直接落进你的意识。");
        });

        // 六句龙语 + 两句人话。每一句占多久，由「龙语」自己算出来。
        dialogueEnd = buildDialogue(DIALOGUE_START);

        // ————————————— 仪式：封印符文亮起，紫光灌体 —————————————
        runesAt = dialogueEnd + AFTER_DIALOGUE;
        absorbAt = runesAt + RUNES_TO_ABSORB;
        vanishStart = absorbAt + ABSORB_TO_VANISH;
        vanishEnd = vanishStart + VANISH_LENGTH;
        returnAt = vanishEnd + VANISH_TO_RETURN;
        finishAt = vanishEnd + RETURN_TO_FINISH;

        at(runesAt, r -> {
            r.sound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
            r.sound(SoundEvents.END_PORTAL_SPAWN, 0.8F, 0.6F);
            r.narrate("祭坛上的封印符文，亮了起来。一道在几万年前就被设定好的程序，开始执行。");
            // 广场上每一块符文砖同时烧起来——小说里那句话，在这里是真的看得见的。
            r.setRuneGlow(SealedLandStructure.RUNE_BLAZING);
        });
        at(absorbAt, r -> {
            r.sound(SoundEvents.PORTAL_TRIGGER, 1.0F, 0.7F);
            r.narrate("尼德霍格、封印，连同那片漆黑的空间，一并化作一道紫光，涌入你的体内。");
        });
        at(vanishStart, r -> {
            r.blind(120);
            r.sound(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.6F);
            r.narrate("你被紫黑色的能量包裹，眼前只剩漆黑——混沌的颜色。");
        });

        // ————————————— 归来 —————————————
        at(returnAt, r -> {
            r.clearBlindness();
            // 黑暗到此为止：脚踏回主世界的那一 tick 就解除，一点都不带过去。
            r.clearDarkness();
            r.setLit(false);
            // 封印已经被她带走了，符文随之熄灭。
            r.setRuneGlow(SealedLandStructure.RUNE_OFF);
            r.despawnNidhogg();
            r.sendBack();
            r.narrate("不知过了多久，黑色褪去，你回到了原来的世界。");
        });
        at(finishAt, ChaosRitual::finish);
    }

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos altarPos;
    private final double cx;
    private final double cy;
    private final double cz;

    /**
     * 广场上所有符文方块的坐标。进场时算一次——仪式期间要反复用它来撒金星，
     * 每 tick 重新推一遍几何太浪费。
     */
    private final List<BlockPos> runePositions;

    private int age;
    private int step;
    private boolean finished;

    /** 不受「等玩家走过来」那个暂停影响的 tick 计数，只用来给黑暗效果续期。 */
    private int effectTick;

    /** 非 null 时，时间轴暂停在这一步，直到条件成立（或等超时）。 */
    @Nullable
    private Predicate<ChaosRitual> gate;
    private int gateTicks;

    @Nullable
    private NidhoggEntity nidhogg;

    // —————— 正在说的那句「龙语」 ——————

    /** 当前这句台词编译出来的音节表；没在说话时为 null。 */
    @Nullable
    private DragonTongue.Utterance utterance;
    /** 下一个该放响的音节在 {@link DragonTongue.Utterance#syllables()} 里的下标。 */
    private int utteranceIndex;
    /** 从这句开口算起过了多少 tick。 */
    private int utteranceTick;

    public ChaosRitual(ServerPlayer player, ServerLevel level, BlockPos altarPos)
    {
        this.player = player;
        this.level = level;
        this.altarPos = altarPos.immutable();
        this.cx = this.altarPos.getX() + 0.5D;
        this.cy = this.altarPos.getY();
        this.cz = this.altarPos.getZ() + 0.5D;
        this.runePositions = SealedLandStructure.runePositions(this.altarPos);
    }

    public UUID playerId()
    {
        return this.player.getUUID();
    }

    public ServerLevel level()
    {
        return this.level;
    }

    public boolean isFinished()
    {
        return this.finished;
    }

    /** 由管理器每 tick 调用一次。 */
    public void tick()
    {
        if (this.finished)
        {
            return;
        }

        // 玩家掉线、死亡，或祭坛被拆掉时，仪式直接收尾，避免残留特效与悬浮实体。
        if (!this.player.isAlive() || this.player.hasDisconnected())
        {
            stop();
            return;
        }

        if (!this.level.getBlockState(this.altarPos).is(ModBlocks.CHAOS_ALTAR.get()))
        {
            this.player.displayClientMessage(
                    Component.translatable("ritual.mythic_relic.altar_lost").withStyle(ChatFormatting.RED), false);
            stop();
            return;
        }

        // 玩家在剧本把他送回去之前就离开了封印之地——自己搭了下界传送门、被 /tp 走，
        // 或者别的模组动了手脚。那这场仪式就没必要再演下去：不拦的话它会继续在空无一人的
        // 维度里跑完，然后在主世界那头凭空发一枚印记。
        //
        // 必须卡在 returnAt 之前判定：过了那一刻，玩家本来就该在外面。
        //
        // 这里只是「中止」，不动玩家的位置（stop() 会先看一眼维度），
        // 所以玩家可以下次带着钥匙重新进来，从头再演一遍。
        if (this.age < returnAt && !this.player.level().dimension().equals(SealedLand.DIMENSION))
        {
            // 聊天栏留一句。不然玩家回头只会发现仪式莫名其妙没了，
            // 也不知道是彻底错过了还是能再来一次。
            this.player.displayClientMessage(
                    Component.translatable("ritual.mythic_relic.abandoned").withStyle(ChatFormatting.GRAY), false);
            stop();
            return;
        }

        // 剧本可以停在某一步等人：等待期间 age 不推进，后面所有节点整体顺延。
        if (this.gate != null)
        {
            this.gateTicks++;
            if (this.gate.test(this) || this.gateTicks >= ARRIVAL_TIMEOUT)
            {
                this.gate = null;
            }
            else
            {
                tickDarkness();
                waitingForPlayer();
                tickPerformance();
                tickUtterance();
                ambientParticles();
                return;
            }
        }

        this.age++;

        while (this.step < SCRIPT.size() && this.age >= SCRIPT.get(this.step).at())
        {
            SCRIPT.get(this.step).action().accept(this);
            this.step++;
        }

        tickDarkness();
        tickPerformance();
        tickUtterance();
        ambientParticles();
    }

    /**
     * 给黑暗效果续期。
     *
     * <p>这里刻意用 {@link #effectTick} 而不是 {@link #age}：等玩家走过来时 {@code age}
     * 是停住的，但黑暗不能跟着停。</p>
     */
    private void tickDarkness()
    {
        if (this.age >= returnAt)
        {
            return;   // 已经把人送回去了，不再续
        }
        if (this.effectTick % DARKNESS_REFRESH == 0)
        {
            this.player.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, DARKNESS_CHUNK, 0, false, false, false));
        }
        this.effectTick++;
    }

    // —————————————————————— 剧本动作 ——————————————————————

    private void begin()
    {
        sound(SoundEvents.PORTAL_TRIGGER, 1.0F, 0.5F);
        // 黑暗贯穿整场仪式，但由 tickDarkness() 每 200 tick 续一小段。
        // 不再在这里一次性给一个「够长」的数字——那正是它会中途断掉、或者一路拖进主世界的原因。
        tickDarkness();
        // 盲眼只用于「刚被拉进空间」与「混沌灌体」两段。
        blind(REVEAL_AT);
        // 玩家是被那股召唤硬拽进来的，不是自己走进去的。小说原文那句「他走进那个神秘的空间」
        // 在这里会让人以为门在哪儿、自己怎么没走——所以照实写成传送。
        narrate("脚下的世界骤然消失——你被那股召唤硬生生拽了进去。");
    }

    private void finish()
    {
        clearBlindness();
        clearDarkness();
        setLit(false);
        setRuneGlow(SealedLandStructure.RUNE_OFF);
        despawnNidhogg();

        narrate("但你能感觉到：从今往后，那尊混沌神龙将盘踞在你体内，永远地伴随着你。");
        grantNidhoggMark();
        stop();
    }

    /** 赐予信物「尼德霍格之印」——直接放进专属的饰品栏，之后便再也取不下来。 */
    private void grantNidhoggMark()
    {
        // 已经受过就不再发第二枚。玩家中断仪式之后可以再进来重看一遍，
        // 但印记是「一次」的东西，重复发放会塞满饰品栏。
        com.mythicrelic.chaos.PlayerChaos existing =
                com.mythicrelic.chaos.ModChaos.get(this.player);
        if (existing != null && existing.markGranted())
        {
            return;
        }

        ItemStack mark = new ItemStack(ModItems.NIDHOGG_MARK.get());
        boolean stored = this.player.getCapability(com.mythicrelic.accessory.ModAccessories.ACCESSORIES)
                .map(accessories -> accessories.addToFirstFreeSlot(mark))
                .orElse(false);
        if (!stored)
        {
            // 理论上不会发生（饰品栏有 6 格），兜底掉在脚下。
            this.player.drop(mark, false);
        }
        // 和赫格尼之剑同理：这是「直接发放」，不经过捡起 / 合成事件，
        // 不手动喊一声的话她对这枚印记也不会有评论
        com.mythicrelic.chaos.NidhoggCommentary.onGranted(this.player, mark);

        // 记下「曾经受过印记」。它是绑定物，取不下来也丢不掉，
        // 万一饰品栏因为什么意外空了，登录时会照着这个标记补发一枚。
        com.mythicrelic.chaos.PlayerChaos chaos =
                com.mythicrelic.chaos.ModChaos.get(this.player);
        if (chaos != null)
        {
            chaos.setMarkGranted(true);
        }

        sound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
        this.player.sendSystemMessage(Component.literal("你获得了「尼德霍格之印」，它已烙印在你的灵魂之上。")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 让祭坛在漆黑里亮起来——这是玩家在封印之地唯一能看见的东西。 */
    private void lightAltar()
    {
        setLit(true);
        sound(SoundEvents.BEACON_ACTIVATE, 0.7F, 0.6F);
    }

    /**
     * 时间轴在这里停下，直到玩家自己走到祭坛跟前。
     *
     * <p>没有任何传送——那一段路是玩家自己走的。</p>
     */
    private void awaitArrival()
    {
        this.gateTicks = 0;
        this.gate = r -> SealedLand.isNearAltar(r.player);
    }

    /**
     * 等待玩家走过来时，每隔几秒从祭坛方向传来一次闷响。
     *
     * <p>黑暗的续期交给 {@link #tickDarkness()}，这里不再重复管。</p>
     */
    private void waitingForPlayer()
    {
        if (this.gateTicks % 100 == 0)
        {
            sound(SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.7F);
        }
    }

    /** 把玩家送回重生点（与原版末地祭坛一致）。 */
    private void sendBack()
    {
        SealedLand.sendBack(this.player);
    }

    /** 强制玩家把视线投向尼德霍格——「你的目光被牢牢钉在她身上」。 */
    private void lookAtNidhogg()
    {
        if (this.nidhogg != null && this.nidhogg.isAlive())
        {
            this.player.connection.send(new ClientboundPlayerLookAtPacket(
                    EntityAnchorArgument.Anchor.EYES, this.nidhogg, EntityAnchorArgument.Anchor.EYES));
        }
    }

    /** 旁白：只进左下角的聊天栏。 */
    private void narrate(String text)
    {
        this.player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 对白：说话人带颜色，正文白色，只走聊天栏。
     *
     * <p>尼德霍格的话用<b>斜体</b>——她说的不是你能用耳朵听懂的语言，
     * 那行字是你脑子里直接浮现出来的「意思」。排版上和旁白、和你自己的话区分开，
     * 这条设定才算真的落在画面上。</p>
     */
    private void speak(String speaker, ChatFormatting color, String text)
    {
        MutableComponent body = Component.literal(text);
        if (SPEAKER_NIDHOGG.equals(speaker))
        {
            body.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC);
        }
        else
        {
            body.withStyle(ChatFormatting.WHITE);
        }
        this.player.sendSystemMessage(Component.literal(speaker + "：").withStyle(color).append(body));
    }

    /**
     * 说出一句对白；如果这句是龙语，同时开始放那一串音节。
     *
     * @param tongue 已经编译好的音节表，{@code null} 表示这句话不出声（玩家自己的话）
     */
    private void say(DialogueLine line, @Nullable DragonTongue.Utterance tongue)
    {
        speak(line.speaker(), line.color(), line.text());
        if (tongue != null)
        {
            this.utterance = tongue;
            this.utteranceIndex = 0;
            this.utteranceTick = 0;
        }
    }

    /**
     * 每 tick 把当前这句龙语往下推。
     *
     * <p>音节表在编译时就已经排好了「开口后第几 tick 响哪个音」，所以这里只是把到点的
     * 音节一个个丢出去，没有随机数、没有状态机，同一句话每次听都一样。</p>
     */
    private void tickUtterance()
    {
        if (this.utterance == null)
        {
            return;
        }

        List<DragonTongue.Syllable> syllables = this.utterance.syllables();
        while (this.utteranceIndex < syllables.size()
                && this.utteranceTick >= syllables.get(this.utteranceIndex).at())
        {
            DragonTongue.Syllable syllable = syllables.get(this.utteranceIndex++);
            // 音色表里存的是 RegistryObject，到这一刻才解析——那时注册表早就冻结了。
            playVoice(syllable.event().get(), true, syllable.volume(), syllable.pitch());
        }

        if (this.utteranceIndex >= syllables.size())
        {
            this.utterance = null;   // 这句说完了
            return;
        }
        this.utteranceTick++;
    }

    /**
     * 把一个音节只放给当前玩家。
     *
     * <p>两点考虑：</p>
     * <ul>
     *   <li>用 {@link SoundSource#VOICE} 分轨——玩家可以在声音设置里单独调「语音」音量，
     *       不会和音效、音乐混在一起。</li>
     *   <li>直接发 {@link ClientboundSoundPacket} 而不是 {@code level.playSound}，
     *       这样只有本人听得到，多人服务器里旁边的人不会莫名其妙听见尼德霍格说话。</li>
     * </ul>
     *
     * <p>注意音量<b>不能</b>用来补偿距离：客户端会先把 volume 钳到 1.0，再乘上
     * {@code 1 - 距离 / attenuation_distance} 的线性滚降。真正决定「她离你十格还能不能
     * 听得清」的是 {@code sounds.json} 里的 {@code attenuation_distance}，这里给到了 48。</p>
     *
     * @param fromNidhogg 声音是从她那边传来（有方向感），还是从玩家自己嘴边传来
     */
    private void playVoice(SoundEvent event, boolean fromNidhogg, float volume, float pitch)
    {
        double x;
        double y;
        double z;
        if (fromNidhogg && this.nidhogg != null && this.nidhogg.isAlive())
        {
            x = this.nidhogg.getX();
            y = this.nidhogg.getY() + 1.4D;
            z = this.nidhogg.getZ();
        }
        else if (fromNidhogg)
        {
            // 她还没成形（理论上不会走到这里），退化成广场中心。
            x = this.cx;
            y = this.cy + 2.0D;
            z = this.cz;
        }
        else
        {
            x = this.player.getX();
            y = this.player.getEyeY();
            z = this.player.getZ();
        }

        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event);
        this.player.connection.send(new ClientboundSoundPacket(
                holder, SoundSource.VOICE, x, y, z, volume, pitch, this.level.random.nextLong()));
    }

    private void sound(SoundEvent event, float volume, float pitch)
    {
        ServerLevel current = this.player.serverLevel();
        if (current == this.level)
        {
            current.playSound(null, this.cx, this.cy + 1.0D, this.cz, event, SoundSource.AMBIENT, volume, pitch);
        }
        else
        {
            // 玩家已被送回原来的世界，此时音效要跟着玩家走。
            current.playSound(null, this.player.getX(), this.player.getY(), this.player.getZ(),
                    event, SoundSource.AMBIENT, volume, pitch);
        }
    }

    private void blind(int ticks)
    {
        this.player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, false));
    }

    private void clearBlindness()
    {
        this.player.removeEffect(MobEffects.BLINDNESS);
    }

    private void clearDarkness()
    {
        this.player.removeEffect(MobEffects.DARKNESS);
    }

    private void setLit(boolean lit)
    {
        BlockState state = this.level.getBlockState(this.altarPos);
        if (state.is(ModBlocks.CHAOS_ALTAR.get()) && state.getValue(ChaosAltarBlock.LIT) != lit)
        {
            this.level.setBlockAndUpdate(this.altarPos, state.setValue(ChaosAltarBlock.LIT, lit));
        }
    }

    /** 把广场上所有封印符文一起调到某一档亮度。 */
    private void setRuneGlow(int glow)
    {
        SealedLandStructure.setRuneGlow(this.level, this.altarPos, glow);
    }

    // —————————————————————— 尼德霍格实体 ——————————————————————

    /** 让她从黑暗中凝聚成形。 */
    private void spawnNidhogg()
    {
        if (this.nidhogg != null && this.nidhogg.isAlive())
        {
            return;
        }
        NidhoggEntity entity = ModEntities.NIDHOGG.get().create(this.level);
        if (entity == null)
        {
            return;
        }
        entity.moveTo(this.cx, this.cy + NidhoggEntity.HOVER_HEIGHT, this.cz, 0.0F, 0.0F);
        entity.setEmerge(0.0F);
        // 不给她挂名字牌：头顶飘一行「尼德霍格」太出戏，而且她的身份本来就是
        // 这段对白里自己交代的。NidhoggRenderer.shouldShowName 返回 hasCustomName()，
        // 所以不设名字，名字牌自然就不会渲染。
        this.level.addFreshEntity(entity);
        this.nidhogg = entity;
    }

    private void despawnNidhogg()
    {
        if (this.nidhogg != null)
        {
            this.nidhogg.discard();
            this.nidhogg = null;
        }
    }

    /** 每 tick 推进「显现 / 消散」的进度。 */
    private void tickPerformance()
    {
        if (this.nidhogg == null || !this.nidhogg.isAlive())
        {
            return;
        }
        if (this.age >= EMERGE_START && this.age <= EMERGE_END)
        {
            float progress = (this.age - EMERGE_START) / (float) (EMERGE_END - EMERGE_START);
            this.nidhogg.setEmerge(progress);
        }
        if (this.age >= vanishStart && this.age <= vanishEnd)
        {
            float progress = (this.age - vanishStart) / (float) (vanishEnd - vanishStart);
            this.nidhogg.setVanish(progress);
        }

        // 她周围不断溢出漆黑与星辉
        if (this.age >= EMERGE_START && this.age < vanishEnd && this.age % 2 == 0)
        {
            double ex = this.nidhogg.getX();
            double ey = this.nidhogg.getY() + 1.2D;
            double ez = this.nidhogg.getZ();
            particle(ParticleTypes.SQUID_INK, ex, ey, ez, 3, 0.5D, 0.9D, 0.5D, 0.0D);
            if (this.age % 6 == 0)
            {
                particle(ParticleTypes.END_ROD, ex, ey, ez, 1, 0.6D, 1.0D, 0.6D, 0.01D);
            }
        }
    }

    /** 结束仪式并清理所有残留状态（可重复调用）。 */
    public void stop()
    {
        if (this.finished)
        {
            return;
        }
        this.finished = true;
        this.gate = null;
        this.utterance = null;   // 话说到一半被打断时，剩下的音节不再放
        despawnNidhogg();
        setLit(false);
        setRuneGlow(SealedLandStructure.RUNE_OFF);
        clearBlindness();
        clearDarkness();

        // 仪式中途被打断时要把人送回去，否则玩家会被困在封印之地。
        //
        // 但他可能本来就已经不在那儿了，这时再送一次会把人从原地硬拽回重生点：
        //   · 正常跑完的话，时间轴在 returnAt 那一刻已经送过一次，
        //     而 finish() 结尾还会再调一次 stop()（两者相差 RETURN_TO_FINISH = 180 tick）；
        //   · 玩家也可能自己搭了下界传送门跑掉。
        // 所以先看一眼维度——只在他还困在封印之地时才送。
        if (this.player.level().dimension().equals(SealedLand.DIMENSION))
        {
            SealedLand.sendBack(this.player);
        }
    }

    // —————————————————————— 每 tick 的氛围特效 ——————————————————————

    private void ambientParticles()
    {
        // 玩家还在黑暗里走路：祭坛上方那道光柱是唯一的路标。
        if (this.age >= REVEAL_AT && this.age < BEACON_END)
        {
            altarBeacon();
        }
        if (this.age >= BEACON_END && this.age < vanishEnd + 20 && this.age % 4 == 0)
        {
            runeRing();
        }
        if (this.age >= runesAt && this.age < vanishStart && this.age % 2 == 0)
        {
            plazaRuneGlow();
            gatherOnPlayer();
        }
        if (this.age >= runesAt + 50 && this.age < vanishStart - 10)
        {
            flashOnPlayer();
        }
    }

    /**
     * 黑暗里那座祭坛的高亮：一束直冲穹顶的幽光 + 地面上缓缓扩散的光环。
     *
     * <p>封印之地没有天光、环境光为 0，所以这束粒子就是玩家唯一能看见的东西——
     * 它足够远就能看见，玩家会自己朝着它走过去。</p>
     */
    private void altarBeacon()
    {
        for (int i = 0; i < 2; i++)
        {
            double x = this.cx + (this.level.random.nextDouble() - 0.5D) * 0.9D;
            double z = this.cz + (this.level.random.nextDouble() - 0.5D) * 0.9D;
            double y = this.cy + 0.9D + this.level.random.nextDouble() * 20.0D;
            particle(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        }

        if (this.age % 5 == 0)
        {
            double radius = 0.9D + (this.age % 80) / 80.0D * 3.0D;
            for (int i = 0; i < 8; i++)
            {
                double angle = i * (Math.PI * 2.0D / 8.0D);
                particle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.cx + Math.cos(angle) * radius, this.cy + 0.6D, this.cz + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 祭坛顶面缓缓旋转的符文光环。 */
    private void runeRing()
    {
        double radius = 1.35D;
        double phase = this.age * 0.05D;
        for (int i = 0; i < 10; i++)
        {
            double angle = phase + i * (Math.PI * 2.0D / 10.0D);
            double x = this.cx + Math.cos(angle) * radius;
            double z = this.cz + Math.sin(angle) * radius;
            particle(ParticleTypes.ENCHANT, x, this.cy + 0.55D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (this.age % 12 == 0)
        {
            particle(ParticleTypes.END_ROD, this.cx, this.cy + 0.6D, this.cz, 1, 0.1D, 0.05D, 0.1D, 0.01D);
        }
    }

    /**
     * 广场上那上百块符文一起烧起来：金星从符环与咒文上不断冒起。
     *
     * <p>方块本身的发光等级交给 {@link SealedLandStructure#RUNE_BLAZING}，
     * 这里补的是「火星」——亮度是静态的，火星才让人觉得它真的在燃烧。
     * 每次随机挑几块，所以整片广场看起来是此起彼伏的。</p>
     */
    private void plazaRuneGlow()
    {
        for (int i = 0; i < 8; i++)
        {
            BlockPos pos = this.runePositions.get(this.level.random.nextInt(this.runePositions.size()));
            double x = pos.getX() + 0.5D + (this.level.random.nextDouble() - 0.5D) * 0.7D;
            double z = pos.getZ() + 0.5D + (this.level.random.nextDouble() - 0.5D) * 0.7D;
            double y = pos.getY() + 1.0D + this.level.random.nextDouble() * 0.4D;
            particle(ParticleTypes.FLAME, x, y, z, 1, 0.0D, 0.012D, 0.0D, 0.0D);
            if (i % 4 == 0)
            {
                particle(ParticleTypes.FIREWORK, x, y, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }
    }

    /** 紫光从四面八方汇入玩家体内。 */
    private void gatherOnPlayer()
    {
        double px = this.player.getX();
        double py = this.player.getY() + 1.0D;
        double pz = this.player.getZ();
        for (int i = 0; i < 6; i++)
        {
            double angle = this.level.random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.6D + this.level.random.nextDouble() * 1.4D;
            double x = px + Math.cos(angle) * radius;
            double z = pz + Math.sin(angle) * radius;
            double y = py + (this.level.random.nextDouble() - 0.4D) * 2.0D;
            particle(ParticleTypes.PORTAL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 2 == 0)
            {
                particle(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void flashOnPlayer()
    {
        particle(ParticleTypes.FLASH, this.player.getX(), this.player.getY() + 1.0D, this.player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** 黑暗在人形成形处的一次爆散。 */
    private void darkBurst(int count)
    {
        particle(ParticleTypes.SQUID_INK, this.cx, this.cy + 1.6D, this.cz, count, 1.2D, 1.2D, 1.2D, 0.02D);
        particle(ParticleTypes.LARGE_SMOKE, this.cx, this.cy + 1.6D, this.cz, count / 2, 1.0D, 1.0D, 1.0D, 0.01D);
        particle(ParticleTypes.FLASH, this.cx, this.cy + 1.6D, this.cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void particle(ParticleOptions type, double x, double y, double z,
                          int count, double dx, double dy, double dz, double speed)
    {
        this.level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }
}
