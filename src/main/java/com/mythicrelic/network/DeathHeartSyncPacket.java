package com.mythicrelic.network;

import com.mythicrelic.death.ModDeathHeart;
import com.mythicrelic.death.PlayerDeathHeart;
import com.mythicrelic.client.DeathHeartClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步死亡之心的冷却/生效状态与虚实血量。
 *
 * <p>{@code virtualHealth} 是回溯期间那位「可以是负数」的真实血量，
 * 客户端 HUD 靠它把负血量画出来（原版血条做不到）。</p>
 */
public class DeathHeartSyncPacket
{
    private final int cooldownEndTick;
    private final int activeEndTick;
    private final float virtualHealth;
    /** 客户端收到后要不要播一次物品弹出动画。只有「刚刚触发」的包才为 true。 */
    private final boolean animate;

    public DeathHeartSyncPacket(int cooldownEndTick, int activeEndTick, float virtualHealth, boolean animate)
    {
        this.cooldownEndTick = cooldownEndTick;
        this.activeEndTick = activeEndTick;
        this.virtualHealth = virtualHealth;
        this.animate = animate;
    }

    /** 常规同步：登录、重生、回溯期间每 5 tick 一次。不播动画。 */
    public static void send(ServerPlayer player)
    {
        send(player, false);
    }

    /** 死亡回溯刚刚触发的那一次同步。会顺带让客户端播一次物品弹出动画。 */
    public static void sendActivation(ServerPlayer player)
    {
        send(player, true);
    }

    private static void send(ServerPlayer player, boolean animate)
    {
        PlayerDeathHeart dh = ModDeathHeart.get(player);
        if (dh != null)
        {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new DeathHeartSyncPacket(dh.cooldownEndTick(), dh.activeEndTick(),
                            dh.virtualHealth(), animate));
        }
    }

    public static void encode(DeathHeartSyncPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.cooldownEndTick);
        buffer.writeVarInt(packet.activeEndTick);
        buffer.writeFloat(packet.virtualHealth);
        buffer.writeBoolean(packet.animate);
    }

    public static DeathHeartSyncPacket decode(FriendlyByteBuf buffer)
    {
        return new DeathHeartSyncPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readBoolean());
    }

    public static void handle(DeathHeartSyncPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DeathHeartClientData.set(packet.cooldownEndTick, packet.activeEndTick,
                packet.virtualHealth, packet.animate));
        context.setPacketHandled(true);
    }
}
