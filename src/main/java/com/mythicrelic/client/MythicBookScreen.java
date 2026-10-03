package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.item.MythicBookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 《神话之书》的阅读界面——全套自己画，不用原版书材质。
 *
 * <p>布局仿照匠魂宝典那种「摊开的一本书」：</p>
 * <ul>
 *   <li><b>左页</b>放标题（金色，居中），下面一条横线；</li>
 *   <li><b>右页</b>放正文（深墨色，按像素宽度自动换行）；</li>
 *   <li><b>底部</b>放页码 + 左右箭头。</li>
 * </ul>
 *
 * <p>正文每页结构是 <code>"标题\n正文"</code>——标题占第一行，正文占后面所有行，
 * 翻译键 <code>book.mythic_relic.page.N</code> 一键搞定。</p>
 *
 * <p>书的最前面两页是<b>目录</b>（page 0 / page 1），用 <code>book.mythic_relic.toc.*</code>
 * 系列的翻译键；翻过目录后才是正文（page 2 起）。目录仿照匠魂宝典的做法：按分卷归类，
 * 条目<b>可点击直接跳页</b>（悬停会亮成金色并压一条下划线）；正文页底部另有
 * 「&lt; 目录」把玩家送回目录首页。</p>
 *
 * <p>翻页：点左/右箭头、按键盘左/右箭头、ESC 关闭。</p>
 */
public class MythicBookScreen extends Screen
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MythicRelic.MODID, "textures/gui/mythic_book.png");

    private static final int IMAGE_W = 288;
    private static final int IMAGE_H = 210;

    /** 左页标题金色 / 右页正文墨色 / 页码淡墨 / 分割线。 */
    private static final int TITLE_COLOR = 0xFFD9B055;
    private static final int TEXT_COLOR = 0xFF34291E;
    private static final int PAGE_NUM_COLOR = 0xFF8E734A;
    private static final int DIVIDER_COLOR = 0xFFA88E5E;

    /** 左右页的渲染区域（与 GUI 底图的页面区对齐）。 */
    private static final int TITLE_CENTER_X_OFFSET = 76;
    private static final int CONTENT_TITLE_Y_OFFSET = 52;
    private static final int CONTENT_DIVIDER_Y_OFFSET = 72;
    private static final int TOC_TITLE_Y_OFFSET = 44;
    private static final int TOC_DIVIDER_Y_OFFSET = 64;

    /** 右页正文区。右页羊皮纸在 x 150..274，留 8px 边距，宽度控制在 104 内。 */
    private static final int BODY_X_OFFSET = 158;
    private static final int BODY_Y_OFFSET = 34;
    private static final int BODY_WIDTH = 104;
    private static final int LINE_STEP = 12;
    private static final int PARAGRAPH_GAP = 4;

    /** 目录条目区（与正文同页）。 */
    private static final int TOC_X_OFFSET = 158;
    private static final int TOC_Y_OFFSET = 32;
    private static final int TOC_STEP = 12;
    private static final int TOC_RIGHT_EDGE = 268;
    private static final int TOC_ROWS_PER_PAGE = 12;

    /**
     * 目录排版表，一行一行照抄下来的：<b>负数</b>是分卷标题（{@code -n} 对应
     * {@code book.mythic_relic.toc.section.(n-1)}），<b>非负数</b>是正文条目索引
     * （条目 {@code j} 的正文在 page {@code 2 + j}）。
     *
     * <p>共 22 行：前 12 行进第 0 页，后 10 行进第 1 页（「目录（续）」）。</p>
     */
    private static final int[] TOC_ROWS = {
            -1, 0,                          // 序
            -2, 1, 2, 3, 4, 5, 6,           // 卷一 · 本源与材料
            -3, 7, 8, 9, 10,                // 卷二 · 封印与神龙
            -4, 11, 12, 13, 14, 15,         // 卷三 · 异宝与禁术
            -5, 16                          // 跋
    };

    /** 分卷标题的暗金色——比正文墨色醒目，又不至于跟金色大标题抢眼。 */
    private static final int SECTION_COLOR = 0xFF8A6B2E;
    /** 底部「&lt; 目录」返回链接：常态 / 悬停。 */
    private static final int LINK_COLOR = 0xFFB89960;
    private static final int LINK_HOVER_COLOR = 0xFFE8C77E;

    /** 底部「&lt; 目录」返回链接的点击区。 */
    private static final int BACK_X_OFFSET = 50;
    private static final int BACK_Y_OFFSET = 189;
    private static final int BACK_W = 44;
    private static final int BACK_H = 14;

    /** 底部页码。 */
    private static final int PAGE_NUM_Y_OFFSET = 192;

    /** 底部箭头的点击区域（左右各一块，16×12 容差）。 */
    private static final int ARROW_W = 16;
    private static final int ARROW_H = 14;
    private static final int ARROW_LEFT_X_OFFSET = 28;
    private static final int ARROW_RIGHT_X_OFFSET = IMAGE_W - 44;
    private static final int ARROW_Y_OFFSET = 188;

    /** 正文从这一页开始（0、1 是目录）。 */
    private static final int FIRST_CONTENT_PAGE = 2;

    private int page;
    private int leftPos;
    private int topPos;

    public MythicBookScreen()
    {
        super(Component.empty());
    }

    /** 由 {@code MythicBookItem#use} 在客户端调用。 */
    public static void open()
    {
        Minecraft.getInstance().setScreen(new MythicBookScreen());
    }

    @Override
    protected void init()
    {
        this.leftPos = (this.width - IMAGE_W) / 2;
        this.topPos = (this.height - IMAGE_H) / 2;
    }

    // —————————————————————— 渲染 ——————————————————————

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick)
    {
        this.renderBackground(g);
        // 必须显式给出纹理真实尺寸：7 参数的那个 blit 重载写死按 256×256 换算 UV，
        // 而本底图是 288×210——u 会算到 288/256 = 1.125，超出的部分被 GL_REPEAT
        // 绕回纹理左缘（画面右侧会莫名多出封面金边），v 只走到 210/256，底部被裁掉。
        g.blit(TEXTURE, leftPos, topPos, 0, 0, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);

        if (this.page < FIRST_CONTENT_PAGE)
        {
            renderToc(g, this.page, mx, my);
        }
        else
        {
            renderContent(g, this.page);
        }

        // 底部：页码 + 左右箭头（两种页面共用）
        String num = (this.page + 1) + " / " + MythicBookItem.PAGE_COUNT;
        g.drawCenteredString(this.font, Component.literal(num),
                leftPos + IMAGE_W / 2, topPos + PAGE_NUM_Y_OFFSET, PAGE_NUM_COLOR);
        drawArrow(g, leftPos + ARROW_LEFT_X_OFFSET, topPos + ARROW_Y_OFFSET + 3, false);
        drawArrow(g, leftPos + ARROW_RIGHT_X_OFFSET, topPos + ARROW_Y_OFFSET + 3, true);

        // 正文页给一个「< 目录」的入口，省得一路往回翻
        if (this.page >= FIRST_CONTENT_PAGE)
        {
            g.drawString(this.font, backLabel(), leftPos + BACK_X_OFFSET, topPos + BACK_Y_OFFSET,
                    isInBackLink(mx, my) ? LINK_HOVER_COLOR : LINK_COLOR, false);
        }

        super.render(g, mx, my, partialTick);
    }

    /** 正文页：左页标题 + 横线，右页逐行换行绘制。 */
    private void renderContent(GuiGraphics g, int pageIndex)
    {
        String raw = Component.translatable("book." + MythicRelic.MODID + ".page." + pageIndex).getString();
        int sep = raw.indexOf('\n');
        String title = sep < 0 ? raw : raw.substring(0, sep);
        String body = sep < 0 ? "" : raw.substring(sep + 1);

        g.drawCenteredString(this.font, Component.literal(title),
                leftPos + TITLE_CENTER_X_OFFSET, topPos + CONTENT_TITLE_Y_OFFSET, TITLE_COLOR);
        g.fill(leftPos + 22, topPos + CONTENT_DIVIDER_Y_OFFSET,
                leftPos + 130, topPos + CONTENT_DIVIDER_Y_OFFSET + 1, DIVIDER_COLOR);

        drawBody(g, body);
    }

    /** 正文逐行绘制——自己按像素宽度换行，中文（无空格）也能正确断行，绝不溢出右页。 */
    private void drawBody(GuiGraphics g, String body)
    {
        int x = leftPos + BODY_X_OFFSET;
        int y = topPos + BODY_Y_OFFSET;
        Font font = this.font;
        for (String para : body.split("\n", -1))
        {
            if (para.isEmpty())
            {
                y += LINE_STEP;
                continue;
            }
            for (String line : wrap(font, para, BODY_WIDTH))
            {
                g.drawString(font, line, x, y, TEXT_COLOR, false);
                y += LINE_STEP;
            }
            y += PARAGRAPH_GAP;
        }
    }

    /** 目录页：左页「目录」大标题，右页按分卷列出条目 + 对应页码；条目可点、可跳。 */
    private void renderToc(GuiGraphics g, int pageIndex, int mouseX, int mouseY)
    {
        boolean cont = pageIndex == 1;
        String title = Component.translatable("book." + MythicRelic.MODID
                + ".toc.title" + (cont ? ".cont" : "")).getString();
        g.drawCenteredString(this.font, Component.literal(title),
                leftPos + TITLE_CENTER_X_OFFSET, topPos + TOC_TITLE_Y_OFFSET, TITLE_COLOR);
        g.fill(leftPos + 22, topPos + TOC_DIVIDER_Y_OFFSET,
                leftPos + 130, topPos + TOC_DIVIDER_Y_OFFSET + 1, DIVIDER_COLOR);

        Font font = this.font;
        int hovered = tocRowIndexAt(mouseX, mouseY);
        int start = pageIndex * TOC_ROWS_PER_PAGE;
        int end = Math.min(start + TOC_ROWS_PER_PAGE, TOC_ROWS.length);
        for (int i = start; i < end; i++)
        {
            int y = topPos + TOC_Y_OFFSET + (i - start) * TOC_STEP;
            int row = TOC_ROWS[i];
            if (row < 0)
            {
                // 分卷标题：只写卷名，不给页码
                g.drawString(font, Component.translatable("book." + MythicRelic.MODID
                        + ".toc.section." + (-row - 1)).getString(),
                        leftPos + TOC_X_OFFSET, y, SECTION_COLOR, false);
                continue;
            }

            boolean hover = i == hovered;
            String label = Component.translatable("book." + MythicRelic.MODID + ".toc.entry." + row).getString();
            g.drawString(font, label, leftPos + TOC_X_OFFSET, y, hover ? TITLE_COLOR : TEXT_COLOR, false);
            if (hover)
            {
                // 悬停时压一条下划线，明确「这一行能点」
                int w = font.width(label);
                g.fill(leftPos + TOC_X_OFFSET, y + font.lineHeight - 1,
                        leftPos + TOC_X_OFFSET + w, y + font.lineHeight, TITLE_COLOR);
            }
            String pageNum = String.valueOf(FIRST_CONTENT_PAGE + row + 1); // 该章正文页的「页码」
            g.drawString(font, pageNum, leftPos + TOC_RIGHT_EDGE - font.width(pageNum), y, PAGE_NUM_COLOR, false);
        }
    }

    /** 底部返回链接的文字（「&lt;」写死在代码里，免得各语言的箭头不一致）。 */
    private String backLabel()
    {
        return "< " + Component.translatable("book." + MythicRelic.MODID + ".toc.back").getString();
    }

    /** 鼠标指着的那一行在 {@link #TOC_ROWS} 里的下标；不在目录页或没指到条目上就返回 -1。 */
    private int tocRowIndexAt(double mouseX, double mouseY)
    {
        if (this.page >= FIRST_CONTENT_PAGE)
        {
            return -1;
        }
        if (mouseX < leftPos + TOC_X_OFFSET || mouseX > leftPos + TOC_RIGHT_EDGE)
        {
            return -1;
        }
        int local = (int) ((mouseY - (topPos + TOC_Y_OFFSET)) / TOC_STEP);
        if (local < 0 || local >= TOC_ROWS_PER_PAGE
                || mouseY > topPos + TOC_Y_OFFSET + local * TOC_STEP + this.font.lineHeight - 1)
        {
            return -1;
        }
        int index = this.page * TOC_ROWS_PER_PAGE + local;
        return index < TOC_ROWS.length ? index : -1;
    }

    /** 鼠标是否指着底部的「&lt; 目录」。 */
    private boolean isInBackLink(double mouseX, double mouseY)
    {
        return this.page >= FIRST_CONTENT_PAGE
                && mouseX >= leftPos + BACK_X_OFFSET && mouseX <= leftPos + BACK_X_OFFSET + BACK_W
                && mouseY >= topPos + BACK_Y_OFFSET && mouseY <= topPos + BACK_Y_OFFSET + BACK_H;
    }

    /** 按字体像素宽度换行：遇到字符就累加宽度，超宽就断行（CJK 逐字断行，不依赖空格）。 */
    private static List<String> wrap(Font font, String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        for (int i = 0; i < text.length(); i++)
        {
            int cw = font.width(text.substring(i, i + 1));
            if (lineW + cw > maxWidth && line.length() > 0)
            {
                lines.add(line.toString());
                line.setLength(0);
                lineW = 0;
            }
            line.append(text.charAt(i));
            lineW += cw;
        }
        if (line.length() > 0)
        {
            lines.add(line.toString());
        }
        return lines;
    }

    /** 4 列宽 × 6 行高的实心三角箭头。 */
    private static void drawArrow(GuiGraphics g, int x, int y, boolean right)
    {
        int color = 0xFFB89960;
        for (int col = 0; col < 4; col++)
        {
            int spanLo = col;
            int spanHi = 6 - col;
            int drawX = right ? x + col : x + (3 - col);
            g.fill(drawX, y + spanLo, drawX + 1, y + spanHi, color);
        }
    }

    // —————————————————————— 输入 ——————————————————————

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // 正文页底部「< 目录」→ 回目录首页
        if (isInBackLink(mouseX, mouseY))
        {
            this.page = 0;
            return true;
        }
        // 目录条目 → 跳到对应正文页
        int row = tocRowIndexAt(mouseX, mouseY);
        if (row >= 0 && TOC_ROWS[row] >= 0)
        {
            this.page = FIRST_CONTENT_PAGE + TOC_ROWS[row];
            return true;
        }
        if (isInArrow(mouseX, mouseY, ARROW_LEFT_X_OFFSET))
        {
            if (this.page > 0)
            {
                this.page--;
            }
            return true;
        }
        if (isInArrow(mouseX, mouseY, ARROW_RIGHT_X_OFFSET))
        {
            if (this.page < MythicBookItem.PAGE_COUNT - 1)
            {
                this.page++;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInArrow(double mouseX, double mouseY, int xOffset)
    {
        return mouseX >= leftPos + xOffset && mouseX <= leftPos + xOffset + ARROW_W
                && mouseY >= topPos + ARROW_Y_OFFSET && mouseY <= topPos + ARROW_Y_OFFSET + ARROW_H;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        // 263 = 左箭头 / 262 = 右箭头（GLFW.KeyMapping）
        if (keyCode == 263 && this.page > 0)
        {
            this.page--;
            return true;
        }
        if (keyCode == 262 && this.page < MythicBookItem.PAGE_COUNT - 1)
        {
            this.page++;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
