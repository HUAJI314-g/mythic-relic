package com.mythicrelic.network;

import com.mythicrelic.chaos.ModChaos;
import com.mythicrelic.chaos.PlayerChaos;
import com.mythicrelic.client.ChaosClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步混沌进度。
 *
 * <p>进度只有服务端会写，客户端拿一份只读副本用来画印记的悬浮提示、判断要不要长龙翼。
 * 发送时机：登录、重生、以及每次进度变化之后。</p>
 */
public class ChaosSyncPacket
{
    private final int progress;

    public ChaosSyncPacket(int progress)
    {
        this.progress = progress;
    }

    /** 把玩家当前的进度发给他的客户端。 */
    public static void send(ServerPlayer player)
    {
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos != null)
        {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ChaosSyncPacket(chaos.progress()));
        }
    }

    public static void encode(ChaosSyncPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.progress);
    }

    public static ChaosSyncPacket decode(FriendlyByteBuf buffer)
    {
        return new ChaosSyncPacket(buffer.readVarInt());
    }

    public static void handle(ChaosSyncPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ChaosClientData.setProgress(packet.progress));
        context.setPacketHandled(true);
    }
}
