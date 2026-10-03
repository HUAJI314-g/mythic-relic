package com.mythicrelic.network;

import com.mythicrelic.client.CommentaryClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：往「底部字幕」那条通道推一句话。
 *
 * <p>包里传的是<b>语言键</b>而不是正文。两个原因：</p>
 * <ul>
 *   <li>正文要走本地化，服务端拿不到玩家的语言设置；</li>
 *   <li>龙语音节是由正文现场编译出来的（{@code DragonTongue.compile}），
 *       编译是纯函数，客户端拿到正文自己算一遍即可，不必把整张音节表序列化过来。</li>
 * </ul>
 *
 * <h2>两种模式</h2>
 * <ul>
 *   <li>{@link #MODE_SPEAKING}（默认）：尼德霍格本人说话——字幕带她的名字，并按正文念一遍龙语；</li>
 *   <li>{@link #MODE_PLAIN}：只是<b>借用这套字幕</b>显示一句系统提示（比如诅咒祭坛的数数），
 *       不报名、不发声。</li>
 * </ul>
 *
 * <p>为什么要借这条通道：底部字幕的排版（居中、折行、衬底、避开聊天栏）已经在
 * {@code CommentaryOverlay} 里做全了，系统提示再写一套只会两套长得不一样。</p>
 */
public class CommentaryPacket
{
    /** 语言键的长度上限。 */
    private static final int MAX_KEY_LENGTH = 128;
    /** 参数个数上限——只用来填数字（比如「23 / 100」），不需要更多。 */
    private static final int MAX_ARGS = 4;

    /** 尼德霍格本人说话。 */
    public static final int MODE_SPEAKING = 0;
    /** 普通系统提示：借字幕显示，不报名、不发声。 */
    public static final int MODE_PLAIN = 1;

    private final String key;
    private final int mode;
    private final int[] args;

    public CommentaryPacket(String key, int mode, int[] args)
    {
        this.key = key;
        this.mode = mode;
        this.args = args;
    }

    /** 尼德霍格说一句（带龙语念白）。 */
    public static void send(ServerPlayer player, String key)
    {
        send(player, key, MODE_SPEAKING, new int[0]);
    }

    /** 借字幕显示一句系统提示，可以带几个整数参数填进语言键的 {@code %s}。 */
    public static void sendPrompt(ServerPlayer player, String key, int... args)
    {
        send(player, key, MODE_PLAIN, args);
    }

    private static void send(ServerPlayer player, String key, int mode, int[] args)
    {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CommentaryPacket(key, mode, args));
    }

    public static void encode(CommentaryPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeUtf(packet.key, MAX_KEY_LENGTH);
        buffer.writeVarInt(packet.mode);
        // 写入时也要按 MAX_ARGS 截断：解码那边同样截断，两边数量必须一致，
        // 否则流会错位（写多了、读少了，后面所有包都跟着烂掉）。
        int count = Math.min(packet.args.length, MAX_ARGS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++)
        {
            buffer.writeVarInt(packet.args[i]);
        }
    }

    public static CommentaryPacket decode(FriendlyByteBuf buffer)
    {
        String key = buffer.readUtf(MAX_KEY_LENGTH);
        int mode = buffer.readVarInt();
        int count = Math.min(buffer.readVarInt(), MAX_ARGS);
        int[] args = new int[count];
        for (int i = 0; i < count; i++)
        {
            args[i] = buffer.readVarInt();
        }
        return new CommentaryPacket(key, mode, args);
    }

    public static void handle(CommentaryPacket packet, Supplier<NetworkEvent.Context> supplier)
    {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> CommentaryClientData.set(packet.key, packet.mode, packet.args));
        context.setPacketHandled(true);
    }
}
