package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.menu.ElementExtractorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ElementExtractorScreen extends AbstractContainerScreen<ElementExtractorMenu>
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/gui/element_extractor.png");

    public ElementExtractorScreen(ElementExtractorMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int left = this.leftPos;
        int top = this.topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        int progress = this.menu.getProgress();
        int total = this.menu.getTotalDuration();
        if (progress > 0)
        {
            int scaled = progress * 24 / total;
            graphics.blit(TEXTURE, left + 58, top + 34, 176, 17, scaled, 17);
        }
        else
        {
            graphics.blit(TEXTURE, left + 58, top + 34, 176, 0, 24, 17);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752);
    }
}
