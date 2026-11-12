package com.AdamNew.dynamicwaters.client.gui.shore;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class BlockPickerScreen extends com.AdamNew.dynamicwaters.client.gui.DWScreen {
    private static final int CELL = 18;

    private final Screen parent;
    private final Consumer<String> onPick;

    private final boolean plants;
    private EditBox search;
    private List<String> shown = List.of();
    private int scroll;
    private int gridX, gridY, cols, rows;

    BlockPickerScreen(Screen parent, Consumer<String> onPick) {
        this(parent, onPick, false);
    }

    BlockPickerScreen(Screen parent, Consumer<String> onPick, boolean plants) {
        super(Component.translatable(plants ? "dynamicwaters.gui.plants.pick" : "dynamicwaters.gui.banks.pick"));
        this.parent = parent;
        this.onPick = onPick;
        this.plants = plants;
    }

    @Override
    protected void init() {
        final String before = search != null ? search.getValue() : "";
        search = new EditBox(this.font, this.width / 2 - 100, 20, 200, 16, Component.translatable("dynamicwaters.gui.banks.search"));
        search.setHint(Component.translatable("dynamicwaters.gui.banks.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(before);
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
        cols = Math.max(4, (this.width - 40) / CELL);
        rows = Math.max(2, (this.height - 42 - 34) / CELL);
        gridX = (this.width - cols * CELL) / 2;
        gridY = 42;
        refilter();
    }

    private void refilter() {
        final String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        if (!plants && (q.isEmpty() || BlockSwatch.name(RiverbankPaletteDraft.KEEP).getString().toLowerCase(Locale.ROOT).contains(q)
                || RiverbankPaletteDraft.KEEP.contains(q))) {
            out.add(RiverbankPaletteDraft.KEEP);
        }
        for (Block b : plants ? BlockSwatch.plantable() : BlockSwatch.dressable()) {
            final String id = BlockSwatch.idOf(b);
            if (q.isEmpty() || id.contains(q) || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(id);
        }
        shown = out;
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + cols - 1) / cols - rows);
    }

    private String at(double mx, double my) {
        if (mx < gridX || my < gridY || mx >= gridX + cols * CELL || my >= gridY + rows * CELL) return null;
        final int i = (scroll + (int) ((my - gridY) / CELL)) * cols + (int) ((mx - gridX) / CELL);
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        com.AdamNew.dynamicwaters.client.gui.DWConfigScreen.tintWater(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 7, 0xFFFFFFFF);
        g.fill(gridX - 2, gridY - 2, gridX + cols * CELL + 2, gridY + rows * CELL + 2, 0x80000000);
        final String hover = at(mouseX, mouseY);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                final int i = (scroll + r) * cols + c;
                if (i >= shown.size()) break;
                final String id = shown.get(i);
                final int x = gridX + c * CELL, y = gridY + r * CELL;
                if (id.equals(hover)) g.fill(x, y, x + CELL, y + CELL, 0x60FFFFFF);
                if (RiverbankPaletteDraft.KEEP.equals(id)) {
                    BlockSwatch.draw(g, id, x + 1, y + 1, 16);
                    g.text(this.font, "~", x + 11, y + 9, 0xFFFFFFFF);
                } else {
                    final Block b = BlockSwatch.block(id);

                    if (b != null && b.asItem() != net.minecraft.world.item.Items.AIR) g.item(new ItemStack(b), x + 1, y + 1);
                    else if (b != null) BlockSwatch.draw(g, id, x + 1, y + 1, 16);
                }
            }
        }
        if (shown.isEmpty()) {
            g.centeredText(this.font, Component.translatable("dynamicwaters.gui.banks.noMatch"), this.width / 2, gridY + 8, 0xFFA0A0A0);
        }
        if (maxScroll() > 0) {
            final int barX = gridX + cols * CELL + 3, total = rows * CELL;
            final int knob = Math.max(8, total * rows / (rows + maxScroll()));
            final int ky = gridY + (total - knob) * scroll / maxScroll();
            g.fill(barX, gridY, barX + 3, gridY + total, 0x40FFFFFF);
            g.fill(barX, ky, barX + 3, ky + knob, 0xC0FFFFFF);
        }
        if (hover != null) {
            g.setComponentTooltipForNextFrame(this.font, List.of(BlockSwatch.name(hover),
                    Component.literal(hover).withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        final double mx = event.x(), my = event.y();
        final int button = event.button();
        final String id = at(mx, my);
        if (id != null && button == 0) {
            onPick.accept(id);
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
