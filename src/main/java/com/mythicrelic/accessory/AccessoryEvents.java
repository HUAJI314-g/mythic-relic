package com.mythicrelic.accessory;

import com.mythicrelic.MythicRelic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 把饰品栏能力挂到玩家身上，并保证死亡后不丢失。
 *
 * <h2>为什么要额外缓存一份</h2>
 * <p>Forge 的 {@code PlayerEvent.Clone} 是在 {@code ServerPlayer.restoreFrom()} 里触发的，
 * 而那一刻旧玩家可能已经被 {@code remove()} 过——实体的能力会在 {@code invalidateCaps()} 里
 * 被 {@code LazyOptional.invalidate()}，于是
 * {@code event.getOriginal().getCapability(...)} 直接拿不到东西，复制就成了空操作，
 * 表现出来就是「死亡之后饰品凭空消失」。</p>
 *
 * <p>所以在 {@link LivingDeathEvent}（死亡瞬间，能力一定还活着）先把数据序列化存一份，
 * Clone 时优先用这份缓存，没命中再退回直接读旧玩家。这样无论 Forge 那边的调用顺序怎么变，
 * 饰品都不会掉。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class AccessoryEvents
{
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(MythicRelic.MODID, "accessories");

    /** 死亡瞬间暂存的数据，key 是玩家 UUID（重生前后一致）。 */
    private static final Map<UUID, CompoundTag> DEATH_BACKUP = new HashMap<>();

    private AccessoryEvents() {}

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            event.addCapability(CAPABILITY_ID, new ModAccessories.Provider());
        }
    }

    /** 死亡的一瞬间就把饰品栏存下来——这时能力一定还没被 invalidate。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            player.getCapability(ModAccessories.ACCESSORIES).ifPresent(accessories ->
                    DEATH_BACKUP.put(player.getUUID(), accessories.serializeNBT()));
        }
    }

    /** 死亡重生时把饰品栏整体搬过去（类似绑定诅咒，不会随死亡掉落）。 */
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
            // 兜底：缓存没命中（例如没走正常的死亡重生流程）时再直接读一次旧玩家
            saved = event.getOriginal().getCapability(ModAccessories.ACCESSORIES)
                    .map(PlayerAccessories::serializeNBT)
                    .orElse(null);
        }
        if (saved == null)
        {
            return;
        }

        CompoundTag data = saved;
        event.getEntity().getCapability(ModAccessories.ACCESSORIES)
                .ifPresent(newStore -> newStore.deserializeNBT(data));
    }

    /**
     * 手持饰品右键 → 直接装到第一个空的饰品槽，不用先开界面。
     *
     * <p>只在服务端执行：客户端拿到的是服务端同步回来的结果，
     * 两边都跑会重复扣物品。</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (event.getLevel().isClientSide())
        {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof AccessoryItem))
        {
            return;
        }
        // 自己要用右键的饰品（神棱偏转镜）让开，交给物品的 use() 去处理
        if (!((AccessoryItem) stack.getItem()).equipOnRightClick())
        {
            return;
        }

        Player player = event.getEntity();
        boolean equipped = player.getCapability(ModAccessories.ACCESSORIES)
                .map(accessories -> accessories.addToFirstFreeSlot(stack.copyWithCount(1)))
                .orElse(false);
        if (!equipped)
        {
            // 饰品栏满了，右键不做事
            return;
        }

        stack.shrink(1);
        player.swing(event.getHand(), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC,
                SoundSource.PLAYERS, 0.8F, 1.2F);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
