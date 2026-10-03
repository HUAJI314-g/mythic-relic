package com.mythicrelic.ritual;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 极简的延迟任务调度器。
 *
 * <p>典型用途：末影龙死亡时它的掉落事件已经触发，但「末地祭坛」（返回传送门）要等死亡动画
 * 播完（约 200 tick）才会生成。混沌之钥必须在那之后才能定位到祭坛正上方，所以需要延迟执行。</p>
 */
public final class ChaosScheduler
{
    private static final List<Task> TASKS = new ArrayList<>();

    private ChaosScheduler() {}

    private static final class Task
    {
        private final ServerLevel level;
        private final Runnable action;
        private int remaining;

        private Task(ServerLevel level, int remaining, Runnable action)
        {
            this.level = level;
            this.remaining = remaining;
            this.action = action;
        }
    }

    public static void delay(ServerLevel level, int ticks, Runnable action)
    {
        TASKS.add(new Task(level, Math.max(1, ticks), action));
    }

    public static void tick(ServerLevel level)
    {
        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext())
        {
            Task task = iterator.next();
            if (task.level != level)
            {
                continue;
            }
            if (--task.remaining <= 0)
            {
                task.action.run();
                iterator.remove();
            }
        }
    }
}
