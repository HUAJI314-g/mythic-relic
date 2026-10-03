package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 死亡回溯期间，把原版血条换成「死亡之心图标 + 真实血量数值」。
 *
 * <p>为什么必须替换：回溯期间真实 MC 血量被钉死在 {@code 0.5f}（原版 setHealth 不允许负数），
 * 原版血条永远只画半颗心，而虚拟血量可以掉到 -50——玩家从血条上完全看不出自己已经负血了。</p>
 *
 * <p>拆成两个事件是有意的：
 * <ul>
 *   <li>{@link RenderGuiOverlayEvent.Pre} 只负责「干掉原版血条」；</li>
 *   <li>真正的绘制放在 {@link RenderGuiEvent.Post}——它晚于所有 GUI overlay
 *       （含其它 mod 注册的），所以装了 ClassicBars 之类的血条 mod 时，
 *       我们的徽章仍然压在它们上面，不会被盖住。</li>
 * </ul>
 * 注意：cancel 只能拦下「原版血条」，拦不住别的 mod 自绘的血条——那些 mod 不会检查
 * {@code isCanceled()}。所以处理冲突靠的是「画得比它晚 + 深色底衬」，不是 cancel。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class DeathHeartHealthOverlay
{
    /** 负血量：死亡红。 */
    private static final int COLOR_NEGATIVE = 0xFFFF5555;
    /** 已回到非负：存活绿。 */
    private static final int COLOR_ALIVE = 0xFF55FF55;
    /** 半透明黑底衬——装了别的血条 mod 时，徽章压在对方血条上也照样看得清。 */
    private static final int COLOR_BACKDROP = 0x90000000;

    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int PADDING = 2;

    private DeathHeartHealthOverlay() {}

    /** 只做一件事：回溯生效期间取消原版血条。 */
    @SubscribeEvent
    public static void onRenderHealthBar(RenderGuiOverlayEvent.Pre event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_HEALTH.type())
        {
            return;
        }
        // 只在回溯生效期间接管，其余时候原版血条照常。
        if (DeathHeartClientData.activeEndTick() <= 0)
        {
            return;
        }

        event.setCanceled(true);
    }

    /** 画「死亡之心图标 + 血量数值」，放在 Post 里保证盖在所有 HUD 之上。 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        if (DeathHeartClientData.activeEndTick() <= 0)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
        {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        float health = DeathHeartClientData.virtualHealth();
        String text = String.format("%.1f", health);

        // 沿用原版心形的位置
        int left = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int top = event.getWindow().getGuiScaledHeight() - 39 - 3;
        int contentWidth = ICON_SIZE + ICON_GAP + minecraft.font.width(text);

        graphics.fill(left - PADDING, top - PADDING,
                left + contentWidth + PADDING, top + ICON_SIZE + PADDING, COLOR_BACKDROP);

        // 图标直接用死亡之心本身，主题一致，也不用额外画贴图
        graphics.renderItem(new ItemStack(ModItems.DEATH_HEART.get()), left, top);

        graphics.drawString(minecraft.font, text, left + ICON_SIZE + ICON_GAP,
                top + (ICON_SIZE - 8) / 2, health < 0.0f ? COLOR_NEGATIVE : COLOR_ALIVE, true);
    }
}
