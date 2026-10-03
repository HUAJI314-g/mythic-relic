package com.mythicrelic.network;

import com.mythicrelic.accessory.AccessoryMenu;
import com.mythicrelic.accessory.ModAccessories;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** 客户端按下饰品栏快捷键后，请求服务端打开界面。 */
public class OpenAccessoriesPacket
{
    public OpenAccessoriesPacket() {}

    public static void encode(OpenAccessoriesPacket packet, FriendlyByteBuf buffer)
    {
        // 无负载
    }

    public static OpenAccessoriesPacket decode(FriendlyByteBuf buffer)
    {
        return new OpenAccessoriesPacket();
    }

    public static void handle(OpenAccessoriesPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null)
        {
            context.enqueueWork(() -> player.getCapability(ModAccessories.ACCESSORIES).ifPresent(accessories ->
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inventory, p) -> new AccessoryMenu(id, inventory, accessories),
                            Component.translatable("container.mythic_relic.accessories")))));
        }
        context.setPacketHandled(true);
    }
}
