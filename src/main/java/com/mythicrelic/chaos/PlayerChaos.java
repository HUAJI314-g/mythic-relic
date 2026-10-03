package com.mythicrelic.chaos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashSet;
import java.util.Set;

/**
 * 玩家身上的「混沌进度」。
 *
 * <p>它是整个尼德霍格之印体系的数据核心：一个单调累加的整数 {@link #progress()}，
 * 阶段由 {@link ChaosTier#of(int)} 从它推出来，终阶之后它还会继续转化成生命上限
 * （{@link #healthBonus()}）。</p>
 *
 * <p>只有服务端会写它，客户端通过 {@code ChaosSyncPacket} 拿一份只读副本。</p>
 */
public class PlayerChaos implements INBTSerializable<CompoundTag>
{
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MARK_GRANTED = "MarkGranted";
    private static final String TAG_COMMENTED = "Commented";
    private static final String TAG_BOOK_GIVEN = "BookGiven";

    /**
     * 终阶之后，每 1 点混沌进度转化成的最大生命。
     *
     * <p>0.2 意味着 5 点换一颗心（2 点生命）。</p>
     */
    public static final double HEALTH_PER_POINT = 0.2D;

    /** 生命加成的上限，防止无限膨胀。 */
    public static final double MAX_HEALTH_BONUS = 100.0D;

    private int progress;

    /** 是否正在滑翔。纯粹的瞬时状态，不写进存档。 */
    private boolean gliding;

    /**
     * 是否已经受过「尼德霍格之印」。
     *
     * <p>它不是用来判定「现在有没有印记」的——那个看饰品栏。它记录的是「曾经受过」，
     * 用来兜底：万一饰品因为什么意外没了（比如死亡复制失败），下次登录时补发一枚。
     * 印记本来就是绑定物，取不下来也丢不掉，所以补发永远是正确的。</p>
     */
    private boolean markGranted;

    /**
     * 已经对哪些「获取事件」发表过评论。
     *
     * <p>存的是物品的注册路径（如 {@code chaos_trace}）或事件名。尼德霍格对同一件东西
     * 只念叨一次，所以这里要落盘——不然每次重登都会把已经说过的话再说一遍。</p>
     */
    private final Set<String> commented = new HashSet<>();

    /**
     * 开局那本《神话之书》是否已经发过。
     *
     * <p>它只在第一次进入世界时塞进背包一次。不给个标记的话，玩家把书弄丢之后
     * 每次登录都会被重新塞一本，那就成骚扰了。丢了只能自己再造一本（书 + 黑曜石）。</p>
     */
    private boolean bookGiven;

    public int progress()
    {
        return this.progress;
    }

    public boolean bookGiven()
    {
        return this.bookGiven;
    }

    public void setBookGiven(boolean value)
    {
        this.bookGiven = value;
    }

    public boolean hasCommented(String key)
    {
        return this.commented.contains(key);
    }

    public void markCommented(String key)
    {
        this.commented.add(key);
    }

    public boolean markGranted()
    {
        return this.markGranted;
    }

    public void setMarkGranted(boolean value)
    {
        this.markGranted = value;
    }

    public boolean gliding()
    {
        return this.gliding;
    }

    public void setGliding(boolean value)
    {
        this.gliding = value;
    }

    public void setProgress(int value)
    {
        this.progress = Math.max(0, value);
    }

    /**
     * 累加混沌进度。
     *
     * @return 如果这次累加让玩家跨进了新阶段，返回新阶段；否则返回 {@code null}
     */
    public ChaosTier addProgress(int amount)
    {
        if (amount <= 0)
        {
            return null;
        }
        ChaosTier before = tier();
        this.progress += amount;
        ChaosTier after = tier();
        return after == before ? null : after;
    }

    public ChaosTier tier()
    {
        return ChaosTier.of(this.progress);
    }

    /** 当前阶段内已经积累的进度（用于进度条）。 */
    public int intoTier()
    {
        return this.progress - tier().threshold();
    }

    /** 当前阶段到下一阶段需要多少进度；终阶返回 0。 */
    public int tierSpan()
    {
        ChaosTier next = tier().next();
        return next == null ? 0 : next.threshold() - tier().threshold();
    }

    /** 终阶之后由混沌进度换来的额外最大生命；未到终阶为 0。 */
    public double healthBonus()
    {
        int overflow = this.progress - ChaosTier.FINAL.threshold();
        if (overflow <= 0)
        {
            return 0.0D;
        }
        return Math.min(MAX_HEALTH_BONUS, overflow * HEALTH_PER_POINT);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_PROGRESS, this.progress);
        tag.putBoolean(TAG_MARK_GRANTED, this.markGranted);
        tag.putBoolean(TAG_BOOK_GIVEN, this.bookGiven);

        ListTag list = new ListTag();
        for (String key : this.commented)
        {
            list.add(StringTag.valueOf(key));
        }
        tag.put(TAG_COMMENTED, list);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        this.progress = Math.max(0, nbt.getInt(TAG_PROGRESS));
        this.markGranted = nbt.getBoolean(TAG_MARK_GRANTED);
        this.bookGiven = nbt.getBoolean(TAG_BOOK_GIVEN);

        this.commented.clear();
        ListTag list = nbt.getList(TAG_COMMENTED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++)
        {
            this.commented.add(list.getString(i));
        }
    }
}
