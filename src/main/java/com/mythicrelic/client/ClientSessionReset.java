package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 断开连接时清空「客户端全局」的数据副本。
 *
 * <p>{@link ChaosClientData}、{@link DeathHeartClientData} 与 {@link PrismMirrorClientData}
 * 都是静态字段，它们不跟着玩家实体走，也就不会在换存档/换服务器时自动归零。不清的话，
 * 新角色会先顶着上一个角色的混沌进度、死亡之心冷却、镜子冷却，甚至是一整条负血量 HUD，
 * 直到服务端把同步包发过来。</p>
 *
 * <p>只在 {@code LoggingOut} 时清——{@code Clone}（重生/换维度）是同一位玩家，不能清，
 * 否则会把还在生效中的冷却显示抹掉。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class ClientSessionReset
{
    private ClientSessionReset() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ChaosClientData.reset();
        DeathHeartClientData.reset();
        PrismMirrorClientData.reset();
        CommentaryClientData.reset();
    }
}
