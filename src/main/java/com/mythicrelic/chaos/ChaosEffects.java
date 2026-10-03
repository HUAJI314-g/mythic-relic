package com.mythicrelic.chaos;

import com.mythicrelic.MythicRelic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 尼德霍格之印的三条被动效果。
 *
 * <h2>贪欲</h2>
 * <p>周边生物的最大生命 +{@value #GREED_HEALTH_BONUS_PERCENT}%，离开范围就撤销；
 * 击杀生物时，它的血量每比 20 点（10 颗心）多出一颗心，掉落数量就多 1%。</p>
 *
 * <h2>暴食</h2>
 * <p>饥饿值消耗速率加倍（实现方式是「观察到的消耗量再补一份」），
 * 饱食度可以突破原版上限 {@value #VANILLA_MAX_FOOD}，并且饱食度不满 18 也能靠饱和度回血。</p>
 *
 * <h2>起源</h2>
 * <p>击杀生物或挖掘矿物时，有概率把同一张掉落表再掷一次，额外掉一份相关物品。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ChaosEffects
{
    // —————— 贪欲 ——————
    /** 周边生物最大生命的提升比例。 */
    private static final double GREED_HEALTH_BONUS_PERCENT = 0.25D;
    /** 生效半径（格）。 */
    private static final double GREED_RADIUS = 16.0D;
    /** 扫描半径，比生效半径大一圈，好把「已经离开」的生物身上的加成撤掉。 */
    private static final double GREED_SCAN_RADIUS = 32.0D;
    /** 贪欲加成的固定 UUID，方便识别与撤销。 */
    private static final UUID GREED_HEALTH_ID = UUID.fromString("2b7d1f04-5c8e-4a91-b6d3-71f0a2c94e55");
    /** 掉落数量加成的上限，免得打一只末影龙掉一地。 */
    private static final float GREED_MAX_MULTIPLIER = 3.0F;

    // —————— 暴食 ——————
    /** 原版的饱食度上限。 */
    private static final int VANILLA_MAX_FOOD = 20;
    /** 突破之后允许到的上限。 */
    private static final int MAX_FOOD = 30;
    /** 靠饱和度回血时的间隔（tick）。 */
    private static final int SATURATION_HEAL_INTERVAL = 10;
    /** 低于这个饱食度才由我们接管回血，避免和原版的自然恢复叠加。 */
    private static final int VANILLA_REGEN_THRESHOLD = 18;

    // —————— 起源 ——————
    /** 击杀生物时额外掉落的概率。 */
    private static final float ORIGIN_KILL_CHANCE = 0.25F;
    /** 挖掘矿物时额外掉落的概率。 */
    private static final float ORIGIN_ORE_CHANCE = 0.30F;

    /** 上一 tick 观察到的饥饿值与消耗值，用来算增量。 */
    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    private static final Map<UUID, Integer> SATURATION_TIMER = new HashMap<>();

    private ChaosEffects() {}

    // —————————————————————— 贪欲：周边生物血量增加 ——————————————————————

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Player player = event.player;
        if (!(player.level() instanceof ServerLevel level) || !ChaosEvents.hasMark(player))
        {
            return;
        }

        if (player.tickCount % 20 == 0)
        {
            applyGreed(level, player);
        }
        applyGluttony(player);
    }

    /** 给半径内的生物挂上加成，给跑远的摘掉。 */
    private static void applyGreed(ServerLevel level, Player player)
    {
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(GREED_SCAN_RADIUS));
        double inner = GREED_RADIUS * GREED_RADIUS;

        for (Mob mob : nearby)
        {
            AttributeInstance attribute = mob.getAttribute(Attributes.MAX_HEALTH);
            if (attribute == null)
            {
                continue;
            }
            boolean inRange = mob.distanceToSqr(player) <= inner;
            AttributeModifier existing = attribute.getModifier(GREED_HEALTH_ID);

            if (inRange && existing == null)
            {
                attribute.addTransientModifier(new AttributeModifier(
                        GREED_HEALTH_ID, "chaos_greed", GREED_HEALTH_BONUS_PERCENT,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                // 多出来的那截血直接补上，免得刚靠近的怪看起来就是残血
                mob.heal((float) (attribute.getValue() - attribute.getBaseValue()));
            }
            else if (!inRange && existing != null)
            {
                attribute.removeModifier(GREED_HEALTH_ID);
                if (mob.getHealth() > mob.getMaxHealth())
                {
                    mob.setHealth(mob.getMaxHealth());
                }
            }
        }
    }

    // —————————————————————— 暴食 ——————————————————————

    private static void applyGluttony(Player player)
    {
        FoodData food = player.getFoodData();
        UUID id = player.getUUID();

        // 1) 消耗速率加倍：把这一 tick 观察到的新增消耗再补一份
        float now = food.getExhaustionLevel();
        Float last = LAST_EXHAUSTION.get(id);
        if (last != null && now > last)
        {
            food.addExhaustion(now - last);
        }
        LAST_EXHAUSTION.put(id, food.getExhaustionLevel());

        // 2) 饱食度不满也能靠饱和度回血
        boolean hungry = food.getFoodLevel() < VANILLA_REGEN_THRESHOLD;
        if (hungry && food.getSaturationLevel() > 0.0F && player.isHurt() && !player.hasEffect(MobEffects.HUNGER))
        {
            int timer = SATURATION_TIMER.merge(id, 1, Integer::sum);
            if (timer >= SATURATION_HEAL_INTERVAL)
            {
                float amount = Math.min(food.getSaturationLevel(), 6.0F);
                player.heal(amount / 6.0F);
                food.addExhaustion(amount);
                SATURATION_TIMER.put(id, 0);
            }
        }
        else
        {
            SATURATION_TIMER.put(id, 0);
        }

        LAST_FOOD.put(id, food.getFoodLevel());
    }

    /** 进食：给混沌进度，并且把被原版截掉的溢出部分补回去（突破饱食度上限）。 */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide())
        {
            return;
        }
        ItemStack stack = event.getItem();
        FoodProperties foodProperties = stack.getFoodProperties(player);
        if (foodProperties == null || !ChaosEvents.hasMark(player))
        {
            return;
        }

        ChaosEvents.award(player, Math.max(1, foodProperties.getNutrition()));

        int before = LAST_FOOD.getOrDefault(player.getUUID(), player.getFoodData().getFoodLevel());
        int wanted = before + foodProperties.getNutrition();
        FoodData food = player.getFoodData();
        if (wanted > VANILLA_MAX_FOOD && food.getFoodLevel() < wanted)
        {
            food.setFoodLevel(Math.min(MAX_FOOD, wanted));
        }
    }

    // —————————————————————— 掉落：贪欲 + 起源 + 击杀进度 ——————————————————————

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event)
    {
        if (!(event.getEntity().level() instanceof ServerLevel level))
        {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player) || !ChaosEvents.hasMark(player))
        {
            return;
        }
        LivingEntity killed = event.getEntity();

        applyGreedMultiplier(event, player, killed);
        applyOriginKillDrop(event, level, player, killed);

        ChaosEvents.award(player, Math.max(2, Math.round(killed.getMaxHealth() / 4.0F)));
    }

    /** 贪欲：目标每比 20 点多一颗心（2 点血），掉落数量 +1%。 */
    private static void applyGreedMultiplier(LivingDropsEvent event, Player player, LivingEntity killed)
    {
        int extraHearts = (int) ((killed.getMaxHealth() - 20.0F) / 2.0F);
        if (extraHearts <= 0)
        {
            return;
        }
        float multiplier = Math.min(GREED_MAX_MULTIPLIER, 1.0F + extraHearts * 0.01F);
        for (ItemEntity drop : event.getDrops())
        {
            ItemStack stack = drop.getItem();
            float bonus = stack.getCount() * (multiplier - 1.0F);
            int extra = (int) bonus;
            if (player.getRandom().nextFloat() < bonus - extra)
            {
                extra++;
            }
            if (extra > 0)
            {
                stack.setCount(stack.getCount() + extra);
            }
        }
    }

    /** 起源：有概率把目标自己的掉落表再掷一次。 */
    private static void applyOriginKillDrop(LivingDropsEvent event, ServerLevel level, Player player, LivingEntity killed)
    {
        if (player.getRandom().nextFloat() >= ORIGIN_KILL_CHANCE)
        {
            return;
        }
        LootTable table = level.getServer().getLootData().getLootTable(killed.getLootTable());
        if (table == LootTable.EMPTY)
        {
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, killed)
                .withParameter(LootContextParams.ORIGIN, killed.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, event.getSource().getDirectEntity())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);

        for (ItemStack extra : table.getRandomItems(params))
        {
            event.getDrops().add(new ItemEntity(level,
                    killed.getX(), killed.getY() + 0.5D, killed.getZ(), extra));
        }
    }

    // —————————————————————— 起源：挖矿额外掉落 ——————————————————————

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        Player player = event.getPlayer();
        if (!ChaosEvents.hasMark(player))
        {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(Tags.Blocks.ORES) || player.getRandom().nextFloat() >= ORIGIN_ORE_CHANCE)
        {
            return;
        }

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(event.getPos()))
                .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(event.getPos()));

        for (ItemStack extra : state.getDrops(builder))
        {
            Block.popResource(level, event.getPos(), extra);
        }
    }

    /** 供别处复用的矿物标签，方便以后想改判定范围。 */
    public static TagKey<Block> oreTag()
    {
        return Tags.Blocks.ORES;
    }
}
