package com.mythicrelic.accessory;

/**
 * 标记接口：可以放进本模组的饰品栏。
 *
 * <p>饰品栏只接受实现了本接口的物品——材料、武器之类的普通物品放不进去。
 * 以后新增饰品时记得实现它；如果那个饰品还需要「放进去就取不下来」，
 * 再叠加 {@link BoundAccessory} 即可（它已经继承本接口）。</p>
 */
public interface AccessoryItem
{
    /**
     * 手持右键时，是否自动装进第一个空的饰品槽。
     *
     * <p>默认 {@code true}——大多数饰品「右键即佩戴」最省事。但有些饰品自己要用右键
     * （比如神棱偏转镜：右键是「激活」而不是「戴上」），就把这个改成 {@code false}，
     * 由 {@code AccessoryEvents} 让开，把右键交还给物品自己的 {@code use()}。
     * 这类饰品仍然可以正常放进饰品栏，只是得从饰品界面（V）拖进去。</p>
     */
    default boolean equipOnRightClick()
    {
        return true;
    }
}
