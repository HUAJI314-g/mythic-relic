package com.mythicrelic.client;

import com.mythicrelic.accessory.AccessoryMenu;
import com.mythicrelic.accessory.PlayerAccessories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * 饰品栏界面。
 *
 * <p>版式与「封印之地」的调性一致：近黑的深紫底色、凹陷的槽位。上半部分是
 * {@link PlayerAccessories} 的 6 个饰品槽，下半部分是玩家背包。</p>
 */
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu>
{
    private static final int BORDER = 0xFF3A2A4A;
    private static final int BG = 0xF0120C18;
    private static final int WELL_SHADOW = 0xFF060409;
    private static final int WELL = 0xFF241A30;
    private static final int SEPARATOR = 0xFF2A1E38;
    private static final int TEXT = 0xFFCFC3E0;

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 保险：让 shift + 右键也走「快速搬运」。
     *
     * <p>原版在按住 shift 时把左右键都映射成 {@code QUICK_MOVE}，这里再兜一次底——
     * 万一以后版本改了映射，shift + 右键 也照样能把饰品放进饰品栏。</p>
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type)
    {
        if (mouseButton == 1 && type == ClickType.PICKUP && hasShiftDown())
        {
            type = ClickType.QUICK_MOVE;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;

        graphics.fill(left - 1, top - 1, left + this.imageWidth + 1, top + this.imageHeight + 1, BORDER);
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, BG);

        // 饰品槽（3 × 2）
        for (int i = 0; i < PlayerAccessories.SLOT_COUNT; i++)
        {
            drawWell(graphics, left + 53 + (i % 3) * 26, top + 22 + (i / 3) * 26);
        }

        // 玩家背包 3 行
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                drawWell(graphics, left + 8 + col * 18, top + 84 + row * 18);
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++)
        {
            drawWell(graphics, left + 8 + col * 18, top + 142);
        }

        // 饰品区与背包之间的分隔线
        graphics.fill(left + 7, top + 69, left + this.imageWidth - 7, top + 70, SEPARATOR);
    }

    private static void drawWell(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, WELL_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, WELL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawCenteredString(this.font, this.title, this.imageWidth / 2, 7, TEXT);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
    }
}
