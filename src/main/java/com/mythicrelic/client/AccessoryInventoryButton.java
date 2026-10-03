package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.network.ModNetwork;
import com.mythicrelic.network.OpenAccessoriesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 在生存模式物品栏里放一个「饰品栏」入口。
 *
 * <p>不用记快捷键也能打开饰品栏：生存物品栏面板上会多出一枚小按钮，点一下就等于按 V 键。</p>
 *
 * <p>位置选在「人物预览」和「合成栏」之间那条空档里（面板坐标 x=78..96）——
 * 那里本来就是空白，不会挡住任何原版控件。</p>
 *
 * <p>配方书开合时整个面板会左右平移，所以按钮位置不能只在 init 时算一次，
 * 每次渲染前都按当前的 {@code getGuiLeft()/getGuiTop()} 重新贴上去。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class AccessoryInventoryButton
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/gui/accessory_button.png");

    private static final int SIZE = 18;
    private static final int OFFSET_X = 78;
    private static final int OFFSET_Y = 8;

    private AccessoryInventoryButton() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event)
    {
        // 只认生存模式的 InventoryScreen；创造模式的 CreativeModeInventoryScreen 不受影响。
        if (event.getScreen() instanceof InventoryScreen screen)
        {
            event.addListener(new AccessoryButton(screen));
        }
    }

    private static final class AccessoryButton extends ImageButton
    {
        private final InventoryScreen screen;

        private AccessoryButton(InventoryScreen screen)
        {
            super(screen.getGuiLeft() + OFFSET_X, screen.getGuiTop() + OFFSET_Y, SIZE, SIZE,
                    0, 0, SIZE, TEXTURE, SIZE, SIZE * 2,
                    button -> ModNetwork.CHANNEL.sendToServer(new OpenAccessoriesPacket()),
                    Component.translatable("gui.mythic_relic.accessories_button"));
            this.screen = screen;
            this.setTooltip(Tooltip.create(Component.translatable("gui.mythic_relic.accessories_button.tip")));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            this.setX(this.screen.getGuiLeft() + OFFSET_X);
            this.setY(this.screen.getGuiTop() + OFFSET_Y);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
