package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.ritual.DragonTongue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 尼德霍格评论的字幕：屏幕中下方的一行字 + 同步念出来的龙语。
 *
 * <p>字幕不走聊天栏，也不占原版字幕（那会被玩家的字幕设置影响，而且位置在最底下、
 * 会和物品栏挤在一起）。这里自己画：名字一行、正文一行，正文长了按屏宽折行，
 * 底下垫一层半透明黑衬，暗处也看得清。</p>
 *
 * <p>声音是<b>本地播放</b>的——这句话只说给本人听，没必要让服务器广播出去。
 * 音节表由正文现场编译（{@code DragonTongue.compile} 是纯函数，两端算出来一模一样），
 * 所以网络包里只需要传一个语言键。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class CommentaryOverlay
{
    /** 说完之后再留多少 tick 才让字幕消失。原来 25（1.25 秒）拖得人难受，压到 6。 */
    private static final int HOLD_TICKS = 6;

    /**
     * 「系统提示」类字幕的停留时间：基础 {@value #PLAIN_BASE_TICKS} tick，
     * 每个字再加 2 tick，上限 {@value #PLAIN_MAX_TICKS}。
     *
     * <p>这类字幕不发声，时长就没法像她说话那样由音节算出来，只能按阅读速度给一个。
     * 祭坛的数数提示每点一下推一条，靠 {@code CommentaryClientData.begin} 把已经过去的
     * 时间接续下来，所以反复重推也不会一直在半透明里闪。</p>
     */
    private static final int PLAIN_BASE_TICKS = 20;
    private static final int PLAIN_MAX_TICKS = 60;
    /** 正文最宽占屏幕宽度的比例，超了就折行。 */
    private static final float MAX_WIDTH_RATIO = 0.62F;

    /**
     * 评论的语速倍率。
     *
     * <p>仪式里的对白可以慢慢念，但一句物品评论拖七八秒就太久了——这里是「随口点评」，
     * 用 {@code DragonTongue.compile(text, scale)} 整体加速。收尾龙吼的时长不受影响。</p>
     */
    private static final float COMMENTARY_SPEED = 2.2F;

    /** 字幕底边距血条/饥饿条上方多少像素。 */
    private static final int HUD_GAP = 6;
    /** 原版血条那一行距屏幕底部的距离（和 {@code Gui} 里的常量一致）。 */
    private static final int HUD_BOTTOM_ROW = 39;
    /** 血条之上还有一行护甲 / 氧气时，再往上让出的高度。 */
    private static final int HUD_EXTRA_ROW = 10;

    /** 名字的颜色：暗紫，和聊天栏里她的名字一致。 */
    private static final int COLOR_NAME = 0xB070E0;
    private static final int COLOR_TEXT = 0xFFFFFF;

    private static DragonTongue.Utterance utterance;
    private static int syllableIndex;
    private static int syllableTick;

    private CommentaryOverlay() {}

    // —————————————————————— 推进 ——————————————————————

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
        {
            reset();
            return;
        }

        if (CommentaryClientData.consumeFresh())
        {
            start(minecraft, CommentaryClientData.key());
        }
        if (!CommentaryClientData.active())
        {
            utterance = null;
            return;
        }

        tickSyllables(minecraft);
        CommentaryClientData.tick();
    }

    private static void start(Minecraft minecraft, String key)
    {
        if (key == null || minecraft.player == null)
        {
            return;
        }
        // 正文按玩家的语言解析出来（可能带几个整数参数），龙语再从这段正文编译——
        // 同一句话每次听都一样。
        String text = Component.translatable(key, args()).getString();
        syllableIndex = 0;
        syllableTick = 0;

        if (!CommentaryClientData.speaking())
        {
            // 借字幕显示的系统提示：不编译龙语、不出声，按字数给一点阅读时间就够
            utterance = null;
            CommentaryClientData.begin(Math.min(PLAIN_MAX_TICKS, PLAIN_BASE_TICKS + text.length() * 2));
            return;
        }

        utterance = DragonTongue.compile(text, COMMENTARY_SPEED);
        CommentaryClientData.begin(utterance.totalTicks() + HOLD_TICKS);
    }

    /** 把语言键里的整数参数转成 {@code Component.translatable} 要的 {@code Object[]}。 */
    private static Object[] args()
    {
        int[] raw = CommentaryClientData.args();
        Object[] boxed = new Object[raw.length];
        for (int i = 0; i < raw.length; i++)
        {
            boxed[i] = raw[i];
        }
        return boxed;
    }

    /** 把到点的音节一个个丢出去，和仪式里 {@code tickUtterance()} 是同一套做法。 */
    private static void tickSyllables(Minecraft minecraft)
    {
        if (utterance == null)
        {
            return;
        }
        List<DragonTongue.Syllable> syllables = utterance.syllables();
        while (syllableIndex < syllables.size()
                && syllableTick >= syllables.get(syllableIndex).at())
        {
            playSyllable(minecraft, syllables.get(syllableIndex++));
        }
        syllableTick++;
    }

    /**
     * 只放给本人听。
     *
     * <p><b>用非定位音源，不要用 {@code level.playLocalSound()}</b>——
     * 那个方法名字里有 local，内部却是转成 {@code ClientLevel.playSound()} 走的定位路径：
     * 会按「听者到声源的距离」做线性衰减、还要算距离延迟。放在玩家自己身上时虽然距离约等于 0、
     * 理论上听不出差别，但实测就是不出声。{@link SimpleSoundInstance#forUI} 走的是
     * {@code Attenuation.NONE} + 相对坐标那条路，是原版所有界面音效用的同一条，最稳。</p>
     *
     * <p>代价是它落在 <b>MASTER</b> 分轨上，玩家不能单独调「语音」音量——
     * 想要那个滑块的话得自己写一个继承 {@code AbstractSoundInstance} 的实例。</p>
     */
    private static void playSyllable(Minecraft minecraft, DragonTongue.Syllable syllable)
    {
        SoundEvent event = syllable.event().get();
        if (event == null)
        {
            return;
        }
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(event, syllable.pitch(), syllable.volume()));
    }

    private static void reset()
    {
        utterance = null;
        syllableIndex = 0;
        syllableTick = 0;
    }

    // —————————————————————— 绘制 ——————————————————————

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        if (!CommentaryClientData.active())
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null)
        {
            return;
        }
        float alpha = CommentaryClientData.alpha();
        if (alpha <= 0.02F)
        {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int a = (int) (alpha * 255.0F);

        Component name = Component.translatable("entity.mythic_relic.nidhogg").withStyle(ChatFormatting.DARK_PURPLE);
        // 她说话时是白色斜体；借字幕显示的系统提示用正体白字，一眼能分清是谁在说
        boolean speaking = CommentaryClientData.speaking();
        FormattedText body = speaking
                ? Component.translatable(CommentaryClientData.key(), args())
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)
                : Component.translatable(CommentaryClientData.key(), args())
                        .withStyle(ChatFormatting.WHITE);
        List<FormattedCharSequence> lines = font.split(body, (int) (screenWidth * MAX_WIDTH_RATIO));

        int lineHeight = font.lineHeight + 2;
        int textWidth = 0;
        for (FormattedCharSequence line : lines)
        {
            textWidth = Math.max(textWidth, font.width(line));
        }
        // 系统提示不报名，所以那一行不占高度
        int nameWidth = speaking ? font.width(name) : 0;

        int boxWidth = Math.max(textWidth, nameWidth) + 16;
        int boxHeight = (lines.size() + (speaking ? 1 : 0)) * lineHeight + 10;
        int boxLeft = (screenWidth - boxWidth) / 2;
        int boxTop = hudTop(minecraft, screenHeight) - boxHeight;

        graphics.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, (a / 3) << 24 | 0x000000);

        int y = boxTop + 5;
        if (speaking)
        {
            graphics.drawString(font, name, (screenWidth - nameWidth) / 2, y, withAlpha(COLOR_NAME, a), true);
            y += lineHeight;
        }
        for (FormattedCharSequence line : lines)
        {
            graphics.drawString(font, line, (screenWidth - font.width(line)) / 2, y, withAlpha(COLOR_TEXT, a), true);
            y += lineHeight;
        }
    }

    /**
     * 字幕底边该落在哪 —— 也就是原版状态栏（血条那一块）的顶边再往上让一点。
     *
     * <p>血条 / 饥饿固定在距屏幕底部 {@value #HUD_BOTTOM_ROW} 像素处；玩家身上有护甲值、
     * 或者在水下憋气时，上面还会多出一行护甲 / 氧气条，所以那两种情况再往上让
     * {@value #HUD_EXTRA_ROW} 像素。这样不论玩家处于什么状态，字幕都不会压住状态栏。</p>
     *
     * <p><b>为什么不再额外躲聊天栏</b>：这里曾经为了让开聊天栏而往上抬过一版
     * （用 {@code ChatComponent.getHeight()} 把字幕顶到聊天栏上边）。代价是没聊天的时候
     * 字幕也常驻偏高。而真正会把聊天栏刷屏的那一处——诅咒祭坛点一百下的数数提示——
     * 已经改成走字幕通道了（见 {@code HegniAltarBlock}），聊天栏不再被刷，
     * 所以按用户要求把位置改回「贴状态栏」。以后若再出现「一边刷聊天栏一边要显示字幕」
     * 的场景，再把那段偏移加回来。</p>
     */
    private static int hudTop(Minecraft minecraft, int screenHeight)
    {
        int top = screenHeight - HUD_BOTTOM_ROW;
        if (minecraft.player != null
                && (minecraft.player.getArmorValue() > 0
                    || minecraft.player.getAirSupply() < minecraft.player.getMaxAirSupply()))
        {
            top -= HUD_EXTRA_ROW;
        }
        return top - HUD_GAP;
    }

    private static int withAlpha(int rgb, int alpha)
    {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
