package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.accessory.AccessoryMenu;
import com.mythicrelic.menu.ElementExtractorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes
{
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MythicRelic.MODID);

    public static final RegistryObject<MenuType<ElementExtractorMenu>> ELEMENT_EXTRACTOR_MENU =
            MENUS.register("element_extractor_menu",
                    () -> IForgeMenuType.create(ElementExtractorMenu::new));

    public static final RegistryObject<MenuType<AccessoryMenu>> ACCESSORIES_MENU =
            MENUS.register("accessories_menu",
                    () -> IForgeMenuType.create((windowId, inventory, data) ->
                            new AccessoryMenu(windowId, inventory)));

    private ModMenuTypes() {}
}
