package com.mythicrelic.network;

import com.mythicrelic.chaos.ChaosAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：尼德霍格之印的各种主动操作。
 *
 * <p>所有判定都在服务端做一遍，客户端发来的只是「意图」。</p>
 */
public class ChaosActionPacket
{
    /** 客户端能发起的操作。 */
    public enum Action
    {
        /** 松手发射暗影龙刃，{@code value} 是蓄力比例 0~1。 */
        FIRE_BLADE,
        /** 释放混沌领域。 */
        UNLEASH_DOMAIN,
        /** 开始滑翔。 */
        GLIDE_START,
        /** 结束滑翔。 */
        GLIDE_STOP
    }

    private final Action action;
    private final float value;

    public ChaosActionPacket(Action action)
    {
        this(action, 0.0F);
    }

    public ChaosActionPacket(Action action, float value)
    {
        this.action = action;
        this.value = value;
    }

    public static void encode(ChaosActionPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeEnum(packet.action);
        buffer.writeFloat(packet.value);
    }

    public static ChaosActionPacket decode(FriendlyByteBuf buffer)
    {
        return new ChaosActionPacket(buffer.readEnum(Action.class), buffer.readFloat());
    }

    public static void handle(ChaosActionPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null)
        {
            context.enqueueWork(() -> {
                switch (packet.action)
                {
                    case FIRE_BLADE -> ChaosAbilities.fireShadowBlade(player, packet.value);
                    case UNLEASH_DOMAIN -> ChaosAbilities.unleashDomain(player);
                    case GLIDE_START -> ChaosAbilities.setGliding(player, true);
                    case GLIDE_STOP -> ChaosAbilities.setGliding(player, false);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
