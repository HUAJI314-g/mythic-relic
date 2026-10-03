package com.mythicrelic.chaos;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.accessory.ModAccessories;
import com.mythicrelic.network.ChaosSyncPacket;
import com.mythicrelic.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 混沌进度的「基础设施」：能力的挂载与继承、进度的发放、以及终阶之后转化成的生命上限。
 *
 * <p>具体的效果在 {@link ChaosEffects}，主动能力在 {@link ChaosAbilities}。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ChaosEvents
{
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(MythicRelic.MODID, "chaos");

    /** 生命上限加成的固定 UUID，用来反复覆盖同一个修饰符。 */
    private static final UUID HEALTH_BONUS_ID = UUID.fromString("6e2f4c18-9a3b-4d77-8f21-0c5b7a9e3d10");

    /** 每隔多少 tick 校一次生命上限。 */
    private static final int HEALTH_REFRESH_INTERVAL = 20;

    /** 死亡瞬间暂存的混沌进度，key 是玩家 UUID（重生前后一致）。 */
    private static final Map<UUID, CompoundTag> DEATH_BACKUP = new HashMap<>();

    private ChaosEvents() {}

    // —————————————————————— 能力挂载 ——————————————————————

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            event.addCapability(CAPABILITY_ID, new ModChaos.Provider());
        }
    }

    /**
     * 死亡的一瞬间把混沌进度存下来。
     *
     * <p>和饰品栏同理：Clone 事件触发时旧玩家的能力可能已经被 invalidate，
     * 光靠 {@code event.getOriginal().getCapability(...)} 会拿不到东西，
     * 于是进度一并丢失。所以在死亡事件里先抓一份。</p>
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            player.getCapability(ModChaos.CHAOS).ifPresent(chaos ->
                    DEATH_BACKUP.put(player.getUUID(), chaos.serializeNBT()));
        }
    }

    /** 死亡重生时把混沌进度一并带过去——它已经烙在灵魂上了。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        if (!event.isWasDeath())
        {
            return;
        }
        CompoundTag saved = DEATH_BACKUP.remove(event.getOriginal().getUUID());
        if (saved == null)
        {
            saved = event.getOriginal().getCapability(ModChaos.CHAOS)
                    .map(PlayerChaos::serializeNBT)
                    .orElse(null);
        }
        if (saved == null)
        {
            return;
        }
        CompoundTag data = saved;
        event.getEntity().getCapability(ModChaos.CHAOS)
                .ifPresent(newStore -> newStore.deserializeNBT(data));
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(PlayerChaos.class);
    }

    // —————————————————————— 同步与生命上限 ——————————————————————

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            ensureMark(player);
            ChaosSyncPacket.send(player);
            refreshHealth(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            ensureMark(player);
            ChaosSyncPacket.send(player);
            refreshHealth(player);
        }
    }

    /**
     * 兜底补发尼德霍格之印。
     *
     * <p>它本来就是绑定物：取不下来、死亡也不掉。所以只要「曾经受过」而饰品栏里没有，
     * 就一定是有环节出了问题，补发一枚永远是正确且安全的。</p>
     */
    private static void ensureMark(ServerPlayer player)
    {
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null || !chaos.markGranted())
        {
            return;
        }
        player.getCapability(ModAccessories.ACCESSORIES).ifPresent(accessories ->
        {
            if (!accessories.contains(ModItems.NIDHOGG_MARK.get()))
            {
                accessories.addToFirstFreeSlot(new ItemStack(ModItems.NIDHOGG_MARK.get()));
            }
        });
    }

    /** 每秒钟校一次生命上限，省得因为重生、换维度之类的事情把修饰符弄丢。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
        {
            return;
        }
        if (event.player.tickCount % HEALTH_REFRESH_INTERVAL == 0)
        {
            refreshHealth(event.player);
        }
    }

    /** 把「终阶之后多余的混沌进度」换算成最大生命。 */
    public static void refreshHealth(Player player)
    {
        PlayerChaos chaos = ModChaos.get(player);
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (chaos == null || attribute == null)
        {
            return;
        }

        double wanted = chaos.healthBonus();
        AttributeModifier existing = attribute.getModifier(HEALTH_BONUS_ID);
        if (existing != null)
        {
            if (Math.abs(existing.getAmount() - wanted) < 1.0E-4D)
            {
                return;
            }
            attribute.removeModifier(HEALTH_BONUS_ID);
        }
        if (wanted > 0.0D)
        {
            attribute.addTransientModifier(new AttributeModifier(
                    HEALTH_BONUS_ID, "chaos_overflow", wanted, AttributeModifier.Operation.ADDITION));
            // 涨上来的那部分直接补满，免得玩家突然多出一截空血条
            if (existing != null && wanted > existing.getAmount())
            {
                player.heal((float) (wanted - existing.getAmount()));
            }
            else if (existing == null)
            {
                player.heal((float) wanted);
            }
        }
        else if (player.getHealth() > player.getMaxHealth())
        {
            player.setHealth(player.getMaxHealth());
        }
    }

    // —————————————————————— 进度发放 ——————————————————————

    /**
     * 给玩家加混沌进度。
     *
     * <p>跨阶段时会提示并放一声，进度本身通过 {@link ChaosSyncPacket} 同步给客户端。</p>
     */
    public static void award(Player player, int amount)
    {
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null || amount <= 0)
        {
            return;
        }
        ChaosTier reached = chaos.addProgress(amount);
        refreshHealth(player);

        if (player instanceof ServerPlayer serverPlayer)
        {
            ChaosSyncPacket.send(serverPlayer);
        }
        if (reached != null)
        {
            player.sendSystemMessage(Component.translatable("chaos.mythic_relic.tier_up", reached.display())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /** 玩家身上有没有尼德霍格之印——所有效果都以它为门槛。 */
    public static boolean hasMark(Player player)
    {
        return player.getCapability(ModAccessories.ACCESSORIES)
                .map(accessories -> accessories.contains(ModItems.NIDHOGG_MARK.get()))
                .orElse(false);
    }

    /** 「有印记 + 达到某个阶段」的合并判定。 */
    public static boolean hasMarkAt(Player player, ChaosTier tier)
    {
        if (!hasMark(player))
        {
            return false;
        }
        PlayerChaos chaos = ModChaos.get(player);
        return chaos != null && chaos.tier().atLeast(tier);
    }
}
