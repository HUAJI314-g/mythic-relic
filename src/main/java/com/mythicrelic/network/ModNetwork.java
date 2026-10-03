package com.mythicrelic.network;

import com.mythicrelic.MythicRelic;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 模组的网络通道。 */
public final class ModNetwork
{
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MythicRelic.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {}

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenAccessoriesPacket.class,
                OpenAccessoriesPacket::encode,
                OpenAccessoriesPacket::decode,
                OpenAccessoriesPacket::handle);
        CHANNEL.registerMessage(id++, ChaosSyncPacket.class,
                ChaosSyncPacket::encode,
                ChaosSyncPacket::decode,
                ChaosSyncPacket::handle);
        CHANNEL.registerMessage(id++, ChaosActionPacket.class,
                ChaosActionPacket::encode,
                ChaosActionPacket::decode,
                ChaosActionPacket::handle);
        CHANNEL.registerMessage(id++, DeathHeartSyncPacket.class,
                DeathHeartSyncPacket::encode,
                DeathHeartSyncPacket::decode,
                DeathHeartSyncPacket::handle);
        CHANNEL.registerMessage(id++, CommentaryPacket.class,
                CommentaryPacket::encode,
                CommentaryPacket::decode,
                CommentaryPacket::handle);
        CHANNEL.registerMessage(id++, RiftTeleportPacket.class,
                RiftTeleportPacket::encode,
                RiftTeleportPacket::decode,
                RiftTeleportPacket::handle);
        CHANNEL.registerMessage(id++, PrismMirrorSyncPacket.class,
                PrismMirrorSyncPacket::encode,
                PrismMirrorSyncPacket::decode,
                PrismMirrorSyncPacket::handle);
    }
}
