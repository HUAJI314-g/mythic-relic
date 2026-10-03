package com.mythicrelic.ritual;

import com.mythicrelic.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 「龙语」编译器：把一句中文台词，编译成一串用<b>原版音效</b>拼出来的音节。
 *
 * <p>设定上，尼德霍格说的是上古混沌龙神的语言，当代人根本听不懂——所以这里
 * <b>不需要任何语音合成</b>，只要听着像一门陌生的、粗糙的、有节奏的语言就够了。
 * 玩家在聊天栏里看到的那行字，是她那句话「直接落进意识里」的意思，
 * 并不是他耳朵听见的音节。</p>
 *
 * <h2>音节是怎么拼出来的</h2>
 * <ul>
 *   <li><b>一个汉字 → 一个音节。</b>音色从 {@link #PALETTE} 里按字符码取，
 *       所以同一句话永远编译出同一串声音：可复现、不随机、不用存档。</li>
 *   <li><b>音池只挑「大、沉、老」的几类生物</b>——远古守卫者的低鸣、末影人的嘶鸣、
 *       潜影贝的空洞共鸣、铁傀儡的金属闷响、监守者的重击。上一版用蝙蝠、史莱姆、
 *       潜影贝开合这类又尖又脆的小动静，听着滑稽；换成这一档之后，
 *       音色本身就带着体量。选材见 {@code tools/gen_dragon_tongue_sounds.py}。</li>
 *   <li><b>整体压到低音域</b>（{@link #PITCHES}，0.65 ~ 0.85），并在分句末尾再降一档
 *       （{@link #CLAUSE_DROP}）。句末降调是「威严」的关键——语气笃定、不容置疑，
 *       而不是往上扬得像在提问。</li>
 *   <li><b>标点 → 停顿。</b>逗号短、破折号中等、句号／问号／感叹号长。</li>
 *   <li><b>固定节拍。</b>每个音节一律推进 {@link #TEMPO} tick（±1 的抖动）。
 *       这一点很重要：音池里长短差了近四倍（0.25s ~ 0.99s），如果让推进量跟着音效时长走，
 *       节奏就会忽快忽慢。改成固定节拍后，长音自然重叠成一片低沉的底噪——那正是「威严」
 *       的来源——短音则负责咬字。</li>
 *   <li><b>整句用一声龙吼收尾。</b>短句一声短促的怒吼，长句一记三秒的龙吟。</li>
 * </ul>
 *
 * <p>时长表里的秒数不是估的，是从原版 1.20.1 的资源索引
 * （{@code assets/indexes/5.json}，注意 {@code 19.json} 是 1.21 的，不能拿来量）
 * 指向的真实 ogg 文件量出来的。现在它们不再参与节奏计算，只用来判断某个音有多长。</p>
 *
 * <p>另外，整句话不会拖过 {@link #MAX_LINE_TICKS}：太长就整体加速，
 * 免得一句台词念到天荒地老。</p>
 */
public final class DragonTongue
{
    /**
     * 一个音节：从这句话开口算起第 {@code at} tick，以 {@code pitch} 音高放响 {@code event}。
     *
     * <p>{@code event} 是 {@link Supplier} 而不是 {@link SoundEvent} 本身——音色表是在
     * 类初始化时读的，那时注册表早已冻结，但存着 Supplier 就不必依赖这个前提。</p>
     */
    public record Syllable(int at, Supplier<SoundEvent> event, float volume, float pitch) {}

    /**
     * 编译好的一句话。
     *
     * @param syllables  按 {@link Syllable#at()} 升序排列的音节表
     * @param totalTicks 整句说多久（含收尾的龙吼），调用方靠它排时间轴
     */
    public record Utterance(List<Syllable> syllables, int totalTicks) {}

    // —————————————————————— 音色池 ——————————————————————

    /**
     * 音节音色池，16 个槽位。
     *
     * <p>同一个音色出现多次 = 提高权重。排在前面的那三个是整句话的底色
     * （低鸣、嘶鸣、空洞共鸣），所以各占三格；中间两个短而闷的负责咬字；
     * 最后三个厚重但很长，只在重音处偶尔露一次面，出现多了会糊成一团。</p>
     *
     * <p>这里引用的都是 {@link ModSounds#SYLLABLES}——本模组自己注册的事件，
     * 只是把音频指向原版文件。原因（字幕会穿帮、衰减距离不够）见 {@link ModSounds}。
     * 下标必须和 {@code sounds.json} 里的 {@code syllable/N} 对齐，
     * 那个文件由 {@code tools/gen_dragon_tongue_sounds.py} 生成，它会把这个槽位表原样打出来，
     * 改音色时照着抄即可。</p>
     */
    private static final List<Supplier<SoundEvent>> PALETTE = List.of(
            // —— 底色（权重 3）——
            ModSounds.SYLLABLES.get(0),   // 深海巨兽的低鸣（远古守卫者，0.36s）
            ModSounds.SYLLABLES.get(0),
            ModSounds.SYLLABLES.get(0),
            ModSounds.SYLLABLES.get(1),   // 异界生物的沙哑嘶鸣（末影人，0.40s）
            ModSounds.SYLLABLES.get(1),
            ModSounds.SYLLABLES.get(1),
            ModSounds.SYLLABLES.get(2),   // 空洞的远古共鸣（潜影贝，0.41s）
            ModSounds.SYLLABLES.get(2),
            ModSounds.SYLLABLES.get(2),
            // —— 咬字（权重 2）：短、闷，负责把音节切开 ——
            ModSounds.SYLLABLES.get(3),   // 沉重的金属闷响（铁傀儡，0.25s）
            ModSounds.SYLLABLES.get(3),
            ModSounds.SYLLABLES.get(4),   // 闷响（潜影贝，0.29s）
            ModSounds.SYLLABLES.get(4),
            // —— 重音（权重 1）：一开口就压住全场，所以只给一格 ——
            ModSounds.SYLLABLES.get(5),   // 金属般的低吼（铁傀儡，0.75s）
            ModSounds.SYLLABLES.get(6),   // 空灵的低吟（末影人，0.66s）
            ModSounds.SYLLABLES.get(7));  // 远古监守者的重击（0.99s）

    /** 音高表：整体压到低音域，听上去更低沉、更像巨兽。 */
    private static final float[] PITCHES = {0.65F, 0.70F, 0.75F, 0.80F, 0.85F};

    /** 分句末尾再降一档：语气笃定，不往上扬。 */
    private static final float CLAUSE_DROP = 0.88F;

    // —————————————————————— 标点 ——————————————————————

    /** 直接忽略的字符：引号、括号、空白、数字。不出声，也不占停顿。 */
    private static final String IGNORED =
            "「」『』《》〈〉【】[]（）()“”‘’\"' \u3000×0123456789";
    /** 短停顿：顿号、逗号、冒号。 */
    private static final String PAUSE_SHORT = "，、：,;:";
    /** 中等停顿：省略号、破折号。 */
    private static final String PAUSE_MID = "…—–～~·";
    /** 长停顿：句末。 */
    private static final String PAUSE_LONG = "。！？.!?";

    private static final int PAUSE_SHORT_TICKS = 2;
    private static final int PAUSE_MID_TICKS = 3;
    private static final int PAUSE_LONG_TICKS = 7;

    // —————————————————————— 节奏参数 ——————————————————————

    private static final int TICKS_PER_SECOND = 20;
    /** 一个音节推进多少 tick（= 语速）。固定节拍，不跟着音效时长走。 */
    private static final int TEMPO = 4;
    /** 再叠加 0 ~ TEMPO_JITTER-1 tick 的抖动，免得像机器在打拍子。 */
    private static final int TEMPO_JITTER = 2;
    /** 两个音节之间最少隔多少 tick。 */
    private static final int MIN_ADVANCE = 2;
    /** 普通音节音量。压得比收尾的龙吼低，让吼声压得住。 */
    private static final float VOLUME_SYLLABLE = 0.55F;
    /** 收尾龙吼音量（可以超过 1，压过一整句音节）。 */
    private static final float VOLUME_ROAR = 1.0F;
    /** 单句上限：超过就整体加速。320 tick = 16 秒。 */
    private static final int MAX_LINE_TICKS = 320;
    /** 音节数达到多少，收尾就用那记长长的龙吟。 */
    private static final int LONG_LINE_SYLLABLES = 25;

    /** 长句的收尾：一记三秒的龙吟。 */
    private static final Supplier<SoundEvent> ROAR_LONG = ModSounds.ROAR_LONG;
    /** 短句的收尾：一声一秒的怒吼。 */
    private static final Supplier<SoundEvent> ROAR_SHORT = ModSounds.ROAR_SHORT;
    /** 上面两段音频的实测时长（秒），用来算它们各占多少 tick。 */
    private static final float ROAR_LONG_SECONDS = 3.00F;
    private static final float ROAR_SHORT_SECONDS = 1.00F;
    /** 收尾龙吼的音高。压深，别用原音高。 */
    private static final float ROAR_PITCH = 0.85F;

    // —————————————————————— 编译 ——————————————————————

    /**
     * 把一句台词编译成一句话。
     *
     * @param text 台词原文（标点、引号都会参与节奏，不要预先清理）
     * @return 音节表 + 总时长；{@code text} 为空时返回空话
     */
    public static Utterance compile(String text)
    {
        return compile(text, 1.0F);
    }

    /**
     * 同上，但可以整体加快语速。
     *
     * <p>给「随口点评」那种短句用的：仪式里的对白可以慢慢念，但一句物品评论拖七八秒就太久了。
     * {@code speedScale} 大于 1 就是加速——它只压缩音节之间的间隔与停顿，
     * 收尾龙吼的时长不动（那是固定长度的一段音频）。</p>
     *
     * @param speedScale 语速倍率，1.0 为原速
     */
    public static Utterance compile(String text, float speedScale)
    {
        List<Token> tokens = tokenize(text);
        int syllables = 0;
        for (Token token : tokens)
        {
            if (token.syllable())
            {
                syllables++;
            }
        }

        // 收尾用哪种吼：短句短吼，长句长吟。
        boolean longLine = syllables >= LONG_LINE_SYLLABLES;
        Supplier<SoundEvent> roar = longLine ? ROAR_LONG : ROAR_SHORT;
        int roarTicks = durationTicks(longLine ? ROAR_LONG_SECONDS : ROAR_SHORT_SECONDS, ROAR_PITCH);

        // 先按常速量一遍：固定部分（停顿 + 收尾吼）不可压缩，其余按语速缩放。
        int fixed = roarTicks;
        int variable = 0;
        for (Token token : tokens)
        {
            if (token.syllable())
            {
                variable += advance(token.value(), 1.0F);
            }
            else
            {
                fixed += token.value();
            }
        }
        int room = MAX_LINE_TICKS - fixed;
        float speed = (variable <= room ? 1.0F : variable / (float) Math.max(1, room))
                * Math.max(0.1F, speedScale);

        // 正式铺音节。
        List<Syllable> syllablesOut = new ArrayList<>(tokens.size() + 1);
        int cursor = 0;
        int pending = 0;
        for (int i = 0; i < tokens.size(); i++)
        {
            Token token = tokens.get(i);
            if (!token.syllable())
            {
                // 停顿也跟着语速一起压，不然「加速」之后只剩字念得快、句读还拖那么长
                pending += Math.max(1, Math.round(token.value() / Math.max(0.1F, speedScale)));
                continue;
            }

            int code = token.value();
            cursor += pending;
            pending = 0;
            // 紧跟着停顿（或已经是最后一个记号）的音节，就是分句末尾——它要降调。
            boolean clauseFinal = i + 1 >= tokens.size() || !tokens.get(i + 1).syllable();
            syllablesOut.add(new Syllable(cursor, paletteOf(code), VOLUME_SYLLABLE, pitchOf(code, clauseFinal)));
            cursor += advance(code, speed);
        }

        cursor += pending;
        syllablesOut.add(new Syllable(cursor, roar, VOLUME_ROAR, ROAR_PITCH));
        return new Utterance(List.copyOf(syllablesOut), cursor + roarTicks);
    }

    /**
     * 切词：把台词拆成「音节」与「停顿」两种记号。
     *
     * @param text 台词原文
     * @return 记号表；{@code Token#syllable()} 为真时 {@code value()} 是字符码，
     *         为假时是停顿的 tick 数
     */
    private static List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (IGNORED.indexOf(c) >= 0)
            {
                continue;
            }
            if (PAUSE_LONG.indexOf(c) >= 0)
            {
                tokens.add(Token.pause(PAUSE_LONG_TICKS));
            }
            else if (PAUSE_MID.indexOf(c) >= 0)
            {
                tokens.add(Token.pause(PAUSE_MID_TICKS));
            }
            else if (PAUSE_SHORT.indexOf(c) >= 0)
            {
                tokens.add(Token.pause(PAUSE_SHORT_TICKS));
            }
            else
            {
                tokens.add(Token.syllable(c));
            }
        }
        return tokens;
    }

    /** 这个字符该用哪个音色。 */
    private static Supplier<SoundEvent> paletteOf(int code)
    {
        return PALETTE.get(Math.floorMod(code, PALETTE.size()));
    }

    /**
     * 这个字符该用哪个音高。
     *
     * @param clauseFinal 这个音节是不是落在分句末尾；是的话再降一档
     */
    private static float pitchOf(int code, boolean clauseFinal)
    {
        float pitch = PITCHES[Math.floorMod(code, PITCHES.length)];
        return clauseFinal ? pitch * CLAUSE_DROP : pitch;
    }

    /** 某个音效在某个音高下，实际会响多少 tick。音高越高，音频播得越短。 */
    private static int durationTicks(float seconds, float pitch)
    {
        return Math.max(2, Math.round(seconds * TICKS_PER_SECOND / pitch));
    }

    /**
     * 一个音节占多少 tick（= 下一个音节要等多久）。
     *
     * <p>固定节拍：{@link #TEMPO} tick，再加一个由字符码决定、0 或 1 tick 的抖动——
     * 这点抖动是「不是机器在打拍子」的关键。整句太长时由 {@code speed} 统一压缩。</p>
     */
    private static int advance(int code, float speed)
    {
        int step = TEMPO + Math.floorMod(code, TEMPO_JITTER);
        return Math.max(MIN_ADVANCE, Math.round(step / speed));
    }

    /** 切词后的一个记号：要么是一个音节（字符码），要么是一段停顿（tick 数）。 */
    private record Token(boolean syllable, int value)
    {
        static Token syllable(int code)
        {
            return new Token(true, code);
        }

        static Token pause(int ticks)
        {
            return new Token(false, ticks);
        }
    }

    private DragonTongue() {}
}
