package com.mythicrelic.network;

import com.mythicrelic.item.RiftFeatherItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：破界之羽的坐标界面填完了，请求传送到 (x, y, z)。
 *
 * <p>服务端会重新做一遍全部判定——手里有没有羽毛、冷却好没好、距离超没超、落点安不安全。
 * 客户端那边只是为了即时给出错误提示，不是可信来源。</p>
 */
public class RiftTeleportPacket
{
    private final int x;
    private final int y;
    private final int z;

    public RiftTeleportPacket(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(RiftTeleportPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeInt(packet.x);
        buffer.writeInt(packet.y);
        buffer.writeInt(packet.z);
    }

    public static RiftTeleportPacket decode(FriendlyByteBuf buffer)
    {
        return new RiftTeleportPacket(buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    public static void handle(RiftTeleportPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null)
        {
            context.enqueueWork(() ->
                    RiftFeatherItem.teleportToCoordinates(player, packet.x, packet.y, packet.z));
        }
        context.setPacketHandled(true);
    }
}
