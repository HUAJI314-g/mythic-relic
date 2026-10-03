package com.mythicrelic.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** 管理所有正在进行的「混沌封印仪式」。 */
public final class ChaosRitualManager
{
    private static final List<ChaosRitual> ACTIVE = new ArrayList<>();

    private ChaosRitualManager() {}

    public static boolean isRunning(UUID playerId)
    {
        for (ChaosRitual ritual : ACTIVE)
        {
            if (ritual.playerId().equals(playerId))
            {
                return true;
            }
        }
        return false;
    }

    public static ChaosRitual start(ServerPlayer player, BlockPos altarPos)
    {
        // 同一玩家重复触发时，先丢掉上一场，避免特效叠加。
        ACTIVE.removeIf(ritual -> ritual.playerId().equals(player.getUUID()));
        ChaosRitual ritual = new ChaosRitual(player, player.serverLevel(), altarPos);
        ACTIVE.add(ritual);
        return ritual;
    }

    public static void tick(ServerLevel level)
    {
        Iterator<ChaosRitual> iterator = ACTIVE.iterator();
        while (iterator.hasNext())
        {
            ChaosRitual ritual = iterator.next();
            if (ritual.level() != level)
            {
                continue;
            }
            ritual.tick();
            if (ritual.isFinished())
            {
                iterator.remove();
            }
        }
    }
}
