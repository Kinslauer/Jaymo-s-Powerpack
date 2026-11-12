package com.AdamNew.dynamicwaters.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DWConfigScreen extends DWScreen {

    private static final int BOX_WIDTH = 400;
    private static final int BUTTON_WIDTH = 190;
    private static final int BUTTON_GAP = 6;
    private static final int SUBTITLE_Y = 30;
    private static final int SUBTITLE_LINE = 10;

    private final Screen lastScreen;

    public DWConfigScreen(Screen lastScreen) {
        super(Component.translatable("dynamicwaters.gui.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        this.lastScreen = lastScreen;
    }

    private List<net.minecraft.util.FormattedCharSequence> subtitle = List.of();

    @Override
    protected void init() {
        final int textWidth = Math.max(100, Math.min(BOX_WIDTH, this.width) - 20);
        final List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        lines.addAll(this.font.split(Component.translatable("dynamicwaters.gui.subtitle1"), textWidth));
        lines.addAll(this.font.split(Component.translatable("dynamicwaters.gui.subtitle2"), textWidth));
        this.subtitle = lines;

        final List<DWConfigCategories.Category> categories = DWConfigCategories.all();
        final int left = this.width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        final int right = this.width / 2 + BUTTON_GAP / 2;

        int y = SUBTITLE_Y + SUBTITLE_LINE * lines.size() + 10;
        for (int i = 0; i < categories.size(); i++) {
            final DWConfigCategories.Category c = categories.get(i);

            final boolean alone = i == categories.size() - 1 && i % 2 == 0;
            final int x = alone ? this.width / 2 - BUTTON_WIDTH / 2 : (i % 2 == 0) ? left : right;
            addRenderableWidget(new CategoryButton(x, y, c, btn -> this.minecraft.gui.setScreen(new DWCategoryScreen(this, c))));
            if (i % 2 == 1) y += Button.DEFAULT_HEIGHT + 4;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }


    public static void tintWater(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, 0x66003366, 0xAA001133);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) {
            // The river panorama: PanoramaMixin draws its cube map whenever this screen is open.
            extractPanorama(graphics, partialTick);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }

        final int x0 = this.width / 2 - BOX_WIDTH / 2, x1 = this.width / 2 + BOX_WIDTH / 2;
        graphics.fill(x0, 0, x1, this.height, 0x66000000);
        graphics.fill(x0 - 1, 0, x0, this.height, 0x66999999);
        graphics.fill(x1, 0, x1 + 1, this.height, 0x66999999);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
        for (int i = 0; i < subtitle.size(); i++) {
            graphics.centeredText(this.font, subtitle.get(i), this.width / 2, SUBTITLE_Y + SUBTITLE_LINE * i, 0xFF9EFFFE);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.lastScreen);
    }

    private static final class CategoryButton extends Button {
        private final DWConfigCategories.Category category;

        CategoryButton(int x, int y, DWConfigCategories.Category category, OnPress onPress) {
            super(x, y, BUTTON_WIDTH, DEFAULT_HEIGHT, category.title(), onPress, DEFAULT_NARRATION);
            this.category = category;
            setTooltip(Tooltip.create(category.description()));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);

            final var font = Minecraft.getInstance().font;
            final int textY = getY() + (getHeight() - 8) / 2;

            graphics.item(new net.minecraft.world.item.ItemStack(category.icon()), getX() + 3, getY() + 2);
            final boolean enabled = category.enabled().getAsBoolean();
            final int colour = !this.active ? 0xFFA0A0A0 : enabled ? 0xFFFFFFFF : 0xFF8A8A8A;
            graphics.text(font, category.title(), getX() + 23, textY, colour);
            Component status = category.status().get();
            if (!status.getString().isEmpty()) {
                final int nameEnd = getX() + 23 + font.width(category.title());
                final int avail = getX() + getWidth() - 6 - nameEnd - 8;
                if (font.width(status) > avail) {
                    final String cut = font.plainSubstrByWidth(status.getString(), Math.max(0, avail - font.width("...")));
                    status = Component.literal(cut.stripTrailing() + "...").withStyle(ChatFormatting.AQUA);
                }
                final int sw = font.width(status);
                graphics.text(font, status, getX() + getWidth() - 6 - sw, textY, colour);
            }
        }
    }
}
