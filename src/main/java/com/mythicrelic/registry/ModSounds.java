package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本模组的自定义音效——只有「龙语」一类。
 *
 * <p>尼德霍格说的上古龙语，是由 {@code DragonTongue} 用一串原版怪物音效拼出来的。
 * 但那些音效<b>不能直接播</b>，否则会踩两个坑，所以这里给每个音色都套一层自己的
 * {@link SoundEvent}：</p>
 *
 * <ul>
 *   <li><b>字幕会穿帮。</b>玩家一旦在设置里开了字幕，直接播原版音效就会看到
 *       「远古守卫者低鸣」「末影人受伤了」「铁傀儡攻击」——一句话里蹦出几十个怪物名字，
 *       整段演出立刻出戏。挂在自己的事件名下，字幕就由 {@code sounds.json} 说了算，
 *       统一写成「尼德霍格说着你听不懂的上古语言」。</li>
 *   <li><b>衰减距离不够。</b>原版这些音效的 {@code attenuation_distance} 是 16 格，
 *       而客户端是按 {@code 1 - 距离 / 衰减距离} 线性滚降的，且包里的 volume 会被钳到
 *       1.0（<b>没法用音量补偿距离</b>）。她悬在祭坛上方、玩家站在九格外，
 *       算下来只剩三成音量。自己的事件可以把它放大到 48 格。</li>
 * </ul>
 *
 * <p>音频本身还是原版的，只是「引用」过来——见
 * {@code tools/gen_dragon_tongue_sounds.py}，它读原版 {@code sounds.json} 展开真实文件清单、
 * 拿资源索引逐个校验、再写出 {@code sounds.json}。<b>不要手写那个文件。</b></p>
 *
 * <p><b>注册名必须和 {@code sounds.json} 里的 key 完全一致</b>（都是不含命名空间的裸路径）。
 * 原版客户端是拿 {@code new ResourceLocation(包命名空间, key)} 拼出来的，所以
 * {@code sounds.json} 里的 key 写成 {@code syllable/0} 就够了，<b>不要</b>写成
 * {@code mythic_relic:syllable/0}——那样会拼出一个带两个冒号的非法名字，音频直接静默。</p>
 */
public final class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MythicRelic.MODID);

    /**
     * 音节音色池的大小。
     *
     * <p>必须和 {@code sounds.json} 里的 {@code syllable/0} ~ {@code syllable/N-1}
     * 一一对应——改了这里就要重新跑一遍 {@code tools/gen_dragon_tongue_sounds.py}。</p>
     */
    private static final int SYLLABLE_COUNT = 8;

    /**
     * 音节音色池，下标就是 {@code DragonTongue} 里用的音色编号。
     *
     * <p>元素是 {@link RegistryObject}（也就是延迟解析的 {@code Supplier}）而不是
     * {@link SoundEvent} 本身——<b>这一点很关键</b>：{@code DragonTongue} 的音色表是在
     * {@code ChaosRitual} 的静态初始化里读的，那时注册表早就冻结了，但静态块本身不该
     * 依赖「已经冻结」这个前提。存着 Supplier，等真正播放的那一刻再 {@code get()}，
     * 就完全绕开了这个时序问题。</p>
     */
    public static final List<RegistryObject<SoundEvent>> SYLLABLES = buildSyllables();

    /** 长句的收尾：一记三秒的龙吟。 */
    public static final RegistryObject<SoundEvent> ROAR_LONG = voice("roar/long");
    /** 短句的收尾：一声一秒的怒吼。 */
    public static final RegistryObject<SoundEvent> ROAR_SHORT = voice("roar/short");

    private static List<RegistryObject<SoundEvent>> buildSyllables()
    {
        List<RegistryObject<SoundEvent>> pool = new ArrayList<>(SYLLABLE_COUNT);
        for (int i = 0; i < SYLLABLE_COUNT; i++)
        {
            pool.add(voice("syllable/" + i));
        }
        return List.copyOf(pool);
    }

    /**
     * 造一个「距离可变衰减」的音效事件。
     *
     * <p>真正的传播距离由 {@code sounds.json} 里每个 sound 的 {@code attenuation_distance}
     * 决定，这里只负责把注册名绑上。</p>
     */
    private static RegistryObject<SoundEvent> voice(String path)
    {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(MythicRelic.MODID, path)));
    }

    private ModSounds() {}
}
