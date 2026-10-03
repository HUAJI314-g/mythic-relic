package com.mythicrelic.client;

import com.mythicrelic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端手里的「死亡之心」冷却副本。
 *
 * <p>tooltip 里显示冷却剩余时间，需要知道当前游戏 tick 总数。
 * 客户端用 {@code Minecraft.getInstance().level.getGameTime()} 取。</p>
 *
 * <p>另外，服务端在「回溯刚刚触发」的那一个同步包里会带上一个动画标记，
 * 这里收到后就播一次不死图腾式的物品弹出动画——只不过弹的是死亡之心。</p>
 *
 * <p><b>这些字段是客户端全局的，不跟着玩家走。</b>所以断开连接时必须
 * {@link #reset()}，否则同一个客户端换存档/换服务器之后会先显示上一个角色的状态。</p>
 */
public final class DeathHeartClientData
{
    /** 冷却结束的服务端 tick 总数。 */
    private static int cooldownEndTick;
    /** 回溯生效结束的 tick 总数（0=未生效）。 */
    private static int activeEndTick;
    /** 回溯期间的真实血量，可以是负数。 */
    private static float virtualHealth;

    private DeathHeartClientData() {}

    /**
     * 收到服务端的同步包。
     *
     * @param animate 是否要播一次「图腾式」物品弹出动画。只有「这一刻刚刚触发回溯」
     *                的那一个包才为 {@code true}；登录、重生、每 5 tick 的血量同步都为
     *                {@code false}。
     *                <p>这一定是由服务端显式指定的，客户端<b>不能</b>靠「activeEndTick 从 0
     *                变成非 0」去猜：重登进一场进行中的回溯时也会出现这种跳变，那会平白
     *                播一次动画。</p>
     */
    public static void set(int cooldownEndTick, int activeEndTick, float virtualHealth, boolean animate)
    {
        DeathHeartClientData.cooldownEndTick = cooldownEndTick;
        DeathHeartClientData.activeEndTick = activeEndTick;
        DeathHeartClientData.virtualHealth = virtualHealth;

        if (animate)
        {
            playActivationAnimation();
        }
    }

    /**
     * 断开连接时清空。
     *
     * <p>这几个字段是<b>客户端全局</b>的，不跟着玩家走。不清的话，换存档/换服务器之后
     * 新角色会先顶着上一个角色的冷却与负血量 HUD，直到服务端把同步包发过来。</p>
     */
    public static void reset()
    {
        cooldownEndTick = 0;
        activeEndTick = 0;
        virtualHealth = 0.0F;
    }

    /** 播放死亡之心的「图腾式」弹出动画（屏幕中央，约 2 秒）。 */
    private static void playActivationAnimation()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer != null)
        {
            minecraft.gameRenderer.displayItemActivation(new ItemStack(ModItems.DEATH_HEART.get()));
        }
    }

    public static int cooldownEndTick()
    {
        return cooldownEndTick;
    }

    public static int activeEndTick()
    {
        return activeEndTick;
    }

    /** 回溯期间的真实血量，可以是负数。未生效时无意义。 */
    public static float virtualHealth()
    {
        return virtualHealth;
    }

    /** 返回冷却还剩多少 tick（给 tooltip 用）。客户端自己算 current tick。 */
    public static int cooldownLeft()
    {
        int now = clientTick();
        return Math.max(0, cooldownEndTick - now);
    }

    private static int clientTick()
    {
        try
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null)
            {
                return (int) mc.level.getGameTime();
            }
        }
        catch (Throwable ignored) { }
        return 0;
    }
}
