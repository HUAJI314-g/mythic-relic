package com.mythicrelic.accessory;

/**
 * 标记接口：放进饰品栏后无法再取下的物品。
 *
 * <p>继承 {@link AccessoryItem}——绑定饰品当然也能放进饰品栏。
 * 后续如果有「可以自由更换」的饰品，实现 {@link AccessoryItem} 而不实现本接口即可。</p>
 */
public interface BoundAccessory extends AccessoryItem
{
    /** 是否禁止从饰品栏取下。 */
    default boolean isBound()
    {
        return true;
    }
}
