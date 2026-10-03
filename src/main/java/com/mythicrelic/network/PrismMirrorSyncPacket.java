package com.mythicrelic.network;

import com.mythicrelic.client.PrismMirrorClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步「神棱偏转镜」的偏转窗口与自动触发冷却。
 *
 * <p>只发两个<b>绝对结束时刻</b>（世界 tick），客户端自己倒计时——省得一秒发二十个包。
 * 发的时候只有三种：开窗那一刻、玩家登录、玩家重生。</p>
 *
 * <p>世界 tick 用 {@code long} 传：它是个一直往上涨的计数器，
 * 用 {@code VarInt}/{@code int} 迟早会溢出成负数（原版死亡之心那边就是 int，
 * 这里是刻意不跟着抄的一点）。</p>
 */
public class PrismMirrorSyncPacket
{
    private final long activeEndTick;
    private final long cooldownEndTick;

    public PrismMirrorSyncPacket(long activeEndTick, long cooldownEndTick)
    {
        this.activeEndTick = activeEndTick;
        this.cooldownEndTick = cooldownEndTick;
    }

    /** 把状态发给本人。 */
    public static void send(ServerPlayer player, long activeEndTick, long cooldownEndTick)
    {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PrismMirrorSyncPacket(activeEndTick, cooldownEndTick));
    }

    public static void encode(PrismMirrorSyncPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeLong(packet.activeEndTick);
        buffer.writeLong(packet.cooldownEndTick);
    }

    public static PrismMirrorSyncPacket decode(FriendlyByteBuf buffer)
    {
        return new PrismMirrorSyncPacket(buffer.readLong(), buffer.readLong());
    }

    public static void handle(PrismMirrorSyncPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> PrismMirrorClientData.set(packet.activeEndTick, packet.cooldownEndTick));
        context.setPacketHandled(true);
    }
}
