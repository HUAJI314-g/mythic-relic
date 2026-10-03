package com.mythicrelic.client;

import com.mythicrelic.Config;
import com.mythicrelic.network.ModNetwork;
import com.mythicrelic.network.RiftTeleportPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 破界之羽的坐标界面：手填 X / Y / Z，回车或点「破界前往」传送。
 *
 * <p>输入框支持原版命令那种 {@code ~} 相对坐标（如 {@code ~120}），留空则取当前位置。
 * 客户端这边只做「格式 / 高度 / 距离」的即时校验，好让玩家当场看到红字提示；
 * 真正的判定在服务端 {@code RiftFeatherItem.teleportToCoordinates} 里重做一遍。</p>
 */
public class CoordinateScreen extends Screen
{
    private static final int COLOR_TEXT = 0xFFE8E6F2;
    private static final int COLOR_SUBTLE = 0xFF9A94B4;
    private static final int COLOR_ERROR = 0xFFFF6B6B;

    private EditBox xEdit;
    private EditBox yEdit;
    private EditBox zEdit;

    private Component status = Component.empty();
    private int statusColor = COLOR_SUBTLE;

    public CoordinateScreen()
    {
        super(Component.translatable("screen.mythic_relic.rift_feather.title"));
    }

    @Override
    protected void init()
    {
        int cx = this.width / 2;
        int rowY = this.height / 2 - 46;
        int boxX = cx - 44;
        int boxW = 126;

        this.xEdit = new EditBox(this.font, boxX, rowY, boxW, 20,
                Component.translatable("screen.mythic_relic.rift_feather.x"));
        this.yEdit = new EditBox(this.font, boxX, rowY + 26, boxW, 20,
                Component.translatable("screen.mythic_relic.rift_feather.y"));
        this.zEdit = new EditBox(this.font, boxX, rowY + 52, boxW, 20,
                Component.translatable("screen.mythic_relic.rift_feather.z"));

        for (EditBox box : new EditBox[]{this.xEdit, this.yEdit, this.zEdit})
        {
            box.setMaxLength(12);
            // 只允许 整数 / 负数 / ~ 相对偏移，省得玩家填出个解析不了的东西
            box.setFilter(value -> value.isEmpty() || value.matches("~?-?\\d*"));
            this.addRenderableWidget(box);
        }

        this.fillCurrentPosition();

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.mythic_relic.rift_feather.here"), button -> this.fillCurrentPosition())
                .bounds(cx - 60, rowY + 80, 120, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.mythic_relic.rift_feather.confirm"), button -> this.commit())
                .bounds(cx - 94, rowY + 106, 90, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.mythic_relic.rift_feather.cancel"), button -> this.onClose())
                .bounds(cx + 4, rowY + 106, 90, 20).build());

        this.setInitialFocus(this.xEdit);
    }

    private void fillCurrentPosition()
    {
        Player player = Minecraft.getInstance().player;
        if (player == null)
        {
            return;
        }
        BlockPos pos = player.blockPosition();
        this.xEdit.setValue(String.valueOf(pos.getX()));
        this.yEdit.setValue(String.valueOf(pos.getY()));
        this.zEdit.setValue(String.valueOf(pos.getZ()));
        this.setStatus(Component.translatable("screen.mythic_relic.rift_feather.ready"), COLOR_SUBTLE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);

        int cx = this.width / 2;
        int rowY = this.height / 2 - 46;

        graphics.drawCenteredString(this.font, this.title, cx, rowY - 46, COLOR_TEXT);

        this.drawRightAligned(graphics, Component.translatable("screen.mythic_relic.rift_feather.x"), cx - 52, rowY + 6);
        this.drawRightAligned(graphics, Component.translatable("screen.mythic_relic.rift_feather.y"), cx - 52, rowY + 32);
        this.drawRightAligned(graphics, Component.translatable("screen.mythic_relic.rift_feather.z"), cx - 52, rowY + 58);

        graphics.drawCenteredString(this.font, this.status, cx, rowY + 132, this.statusColor);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.mythic_relic.rift_feather.hint", Config.riftLongRange),
                cx, rowY + 146, COLOR_SUBTLE);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRightAligned(GuiGraphics graphics, Component text, int right, int y)
    {
        graphics.drawString(this.font, text, right - this.font.width(text), y, COLOR_SUBTLE, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
        {
            this.commit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commit()
    {
        Player player = Minecraft.getInstance().player;
        if (player == null)
        {
            this.onClose();
            return;
        }
        BlockPos base = player.blockPosition();

        Integer x = this.parseValue(this.xEdit.getValue(), base.getX());
        Integer y = this.parseValue(this.yEdit.getValue(), base.getY());
        Integer z = this.parseValue(this.zEdit.getValue(), base.getZ());
        if (x == null || y == null || z == null)
        {
            this.setStatus(Component.translatable("screen.mythic_relic.rift_feather.error.invalid"), COLOR_ERROR);
            return;
        }

        Level level = player.level();
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        if (y < minY || y > maxY)
        {
            this.setStatus(Component.translatable("screen.mythic_relic.rift_feather.error.bad_y", minY, maxY), COLOR_ERROR);
            return;
        }

        int limit = Config.riftLongRange;
        double distance = Math.sqrt(player.position().distanceToSqr(x + 0.5D, y, z + 0.5D));
        if (distance > limit)
        {
            this.setStatus(Component.translatable("screen.mythic_relic.rift_feather.error.too_far",
                    (int) Math.round(distance), limit), COLOR_ERROR);
            return;
        }

        ModNetwork.CHANNEL.sendToServer(new RiftTeleportPacket(x, y, z));
        this.onClose();
    }

    /** 把输入框里的字符串解析成绝对坐标；支持 {@code ~偏移}，留空取 {@code base}。 */
    private Integer parseValue(String raw, int base)
    {
        String text = raw.trim();
        if (text.isEmpty())
        {
            return base;
        }
        if (text.startsWith("~"))
        {
            String offset = text.substring(1).trim();
            if (offset.isEmpty())
            {
                return base;
            }
            Integer parsed = this.toInt(offset);
            return parsed == null ? null : base + parsed;
        }
        return this.toInt(text);
    }

    private Integer toInt(String text)
    {
        try
        {
            return Integer.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private void setStatus(Component message, int color)
    {
        this.status = message;
        this.statusColor = color;
    }
}
