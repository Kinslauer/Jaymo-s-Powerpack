package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.RiverbankPalettes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

final class RiverbankPaletteEditScreen extends com.AdamNew.dynamicwaters.client.gui.DWScreen {
    private static final int ROW = 18;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]+");

    private final RiverbankPalettesScreen parent;
    private final RiverbankPaletteDraft draft;

    private final String fromFile;
    private int tab = RiverbankPaletteDraft.RAPIDS;
    private int page;
    private long seed = 1L;
    private int previewMode = ShorePreview.RIVER;
    private boolean dirty;
    private Component error;

    private EditBox nameBox;
    private int left, colW, listY, listRows, prevX, prevY, prevW, prevH;

    RiverbankPaletteEditScreen(RiverbankPalettesScreen parent, RiverbankPaletteDraft draft, String fromFile) {
        super(Component.translatable("dynamicwaters.gui.banks.editorTitle"));
        this.parent = parent;
        this.draft = draft;
        this.fromFile = fromFile;
    }

    @Override
    protected void init() {
        left = 8;
        final int fullW = this.width - 16;
        colW = Math.min(264, (int) (this.width * 0.55) - 14);

        final String name = nameBox != null ? nameBox.getValue() : draft.name;
        nameBox = box(left, 18, 96, name, "name", s -> draft.name = s.trim());
        nameBox.setMaxLength(40);
        box(left + 100, 18, fullW - 100, RiverbankPaletteDraft.joinList(draft.biomes), "biomes", s -> {
            draft.biomes.clear();
            draft.biomes.addAll(RiverbankPaletteDraft.parseList(s, false));
        });
        final int half = (fullW - 4) / 2;
        box(left, 38, half, tagText(draft.tags), "tags", s -> {
            draft.tags.clear();
            draft.tags.addAll(RiverbankPaletteDraft.parseList(s, true));
        });
        box(left + half + 4, 38, fullW - half - 4, RiverbankPaletteDraft.joinList(draft.not), "not", s -> {
            draft.not.clear();
            draft.not.addAll(RiverbankPaletteDraft.parseNot(s));
        });

        final int tabW = colW / RiverbankPaletteDraft.PLACES.length;
        for (int p = 0; p < RiverbankPaletteDraft.PLACES.length; p++) {
            final int place = p;
            final Button b = Button.builder(tabLabel(p), btn -> {
                        tab = place;
                        page = 0;
                        dirty = true;
                    })
                    .bounds(left + p * tabW, 58, tabW - 2, 16)
                    .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks.place." + RiverbankPaletteDraft.PLACES[p] + ".desc")))
                    .build();
            b.active = p != tab;
            addRenderableWidget(b);
        }

        listY = 78;
        final int addY = this.height - 76;
        listRows = Math.max(1, (addY - 2 - listY) / ROW);
        final List<RiverbankPaletteDraft.Entry> entries = draft.places[tab];
        final int pages = Math.max(1, (entries.size() + listRows - 1) / listRows);
        page = Mth.clamp(page, 0, pages - 1);
        for (int r = 0; r < listRows; r++) {
            final int i = page * listRows + r;
            if (i >= entries.size()) break;
            final RiverbankPaletteDraft.Entry e = entries.get(i);
            final int y = listY + r * ROW;
            final int bx = left + colW - 52;
            small("-", bx, y, "weightDown", () -> e.weight = Math.max(1, e.weight - 1));
            small("+", bx + 17, y, "weightUp", () -> e.weight = Math.min(RiverbankPaletteDraft.MAX_WEIGHT, e.weight + 1));
            small("x", bx + 34, y, "remove", () -> {
                entries.remove(e);
                dirty = true;
            });
        }

        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.addBlock"), b -> openPicker(null))
                .bounds(left, addY, 96, 20).build());
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        page--;
                        dirty = true;
                    }).bounds(left + colW - 44, addY, 20, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        page++;
                        dirty = true;
                    }).bounds(left + colW - 20, addY, 20, 20).build()).active = page < pages - 1;
        }

        prevX = left + colW + 12;
        prevY = 70;
        prevW = this.width - 8 - prevX;
        prevH = addY - 4 - prevY;
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.translatable("dynamicwaters.gui.banks.preview." + ShorePreview.MODES[v]), previewMode)
                .withValues(ShorePreview.RIVER, ShorePreview.STREAM, ShorePreview.LAKE)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("dynamicwaters.gui.banks.previewMode.tooltip")))
                .create(prevX, addY, Math.max(60, prevW - 84), 20, Component.translatable("dynamicwaters.gui.banks.previewMode"),
                        (b, v) -> previewMode = v));
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.reroll"), b -> seed++)
                .bounds(this.width - 8 - 80, addY, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks.reroll.tooltip")))
                .build());

        final int sy = this.height - 52;
        final int sw = (fullW - 3) / 2;
        addRenderableWidget(new ValueSlider(left, sy, sw, "patch", 1, 12, 1, draft.patchSize,
                v -> draft.patchSize = (int) Math.round(v)));
        addRenderableWidget(new ValueSlider(left + sw + 3, sy, fullW - sw - 3, "shoreHeight", 0, 4, 1, draft.shoreHeight,
                v -> draft.shoreHeight = (int) Math.round(v)));

        final int by = this.height - 28;
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("dynamicwaters.gui.banks.styleOn").withStyle(ChatFormatting.GREEN),
                        Component.translatable("dynamicwaters.gui.banks.styleOff").withStyle(ChatFormatting.RED), draft.enabled)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("dynamicwaters.gui.banks.styleSwitch.tooltip")))
                .create(left, by, 130, 20, Component.translatable("dynamicwaters.gui.banks.styleSwitch"),
                        (b, v) -> draft.enabled = v));
        addRenderableWidget(Button.builder(Component.translatable(draft.builtIn
                                ? "dynamicwaters.gui.banks.saveCopy" : "dynamicwaters.gui.banks.save"), b -> save())
                .bounds(this.width - 8 - 206, by, 102, 20)
                .tooltip(draft.builtIn ? Tooltip.create(Component.translatable("dynamicwaters.gui.banks.saveCopy.tooltip")) : null)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width - 8 - 102, by, 102, 20).build());
    }

    private EditBox box(int x, int y, int w, String value, String key, Consumer<String> onChange) {
        final EditBox b = new EditBox(this.font, x, y, w, 16, Component.translatable("dynamicwaters.gui.banks." + key));
        b.setMaxLength(512);
        b.setValue(value);
        b.setHint(Component.translatable("dynamicwaters.gui.banks." + key + ".hint").withStyle(ChatFormatting.DARK_GRAY));
        b.setTooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks." + key + ".tooltip")));
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private void small(String label, int x, int y, String tipKey, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks." + tipKey)))
                .build());
    }

    private Component tabLabel(int p) {
        final int n = draft.places[p].size();
        final Component name = Component.translatable("dynamicwaters.gui.banks.place." + RiverbankPaletteDraft.PLACES[p]);
        return n == 0 ? name : name.copy().append(Component.literal(" " + n).withStyle(ChatFormatting.GRAY));
    }

    private static String tagText(List<String> tags) {
        return String.join(", ", tags.stream().map(t -> "#" + t).toList());
    }

    private void openPicker(RiverbankPaletteDraft.Entry replace) {
        this.minecraft.gui.setScreen(new BlockPickerScreen(this, id -> {
            if (replace != null) {
                replace.id = id;
                return;
            }
            for (RiverbankPaletteDraft.Entry e : draft.places[tab]) {
                if (e.id.equals(id)) {
                    e.weight = Math.min(RiverbankPaletteDraft.MAX_WEIGHT, e.weight + 1);
                    return;
                }
            }
            draft.places[tab].add(new RiverbankPaletteDraft.Entry(id, 1));
        }));
    }

    private RiverbankPaletteDraft.Entry rowAt(double mx, double my) {
        if (mx < left || mx >= left + colW - 56 || my < listY || my >= listY + listRows * ROW) return null;
        final int i = page * listRows + (int) ((my - listY) / ROW);
        final List<RiverbankPaletteDraft.Entry> entries = draft.places[tab];
        return i >= 0 && i < entries.size() ? entries.get(i) : null;
    }

    private void save() {
        final String name = nameBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            error = Component.translatable("dynamicwaters.gui.banks.badName").withStyle(ChatFormatting.RED);
            return;
        }
        final Path dir = RiverbankPalettes.userFolder();
        final Path file = dir.resolve(name + ".json");
        final boolean sameFile = name.equals(fromFile);
        if (Files.exists(file) && !sameFile) {
            error = Component.translatable("dynamicwaters.gui.banks.nameTaken", name).withStyle(ChatFormatting.RED);
            return;
        }
        draft.name = name;
        try {
            Files.writeString(file, draft.toJsonText(), StandardCharsets.UTF_8);
            if (fromFile != null && !sameFile) Files.deleteIfExists(dir.resolve(fromFile + ".json"));
        } catch (IOException e) {
            error = Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
            return;
        }
        RiverbankPalettes.reload();
        parent.refresh(name);
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        com.AdamNew.dynamicwaters.client.gui.DWConfigScreen.tintWater(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (dirty) {
            dirty = false;
            rebuildWidgets();
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        final var font = this.font;
        final Component source = draft.builtIn
                ? Component.translatable("dynamicwaters.gui.banks.builtIn").withStyle(ChatFormatting.DARK_AQUA)
                : Component.translatable("dynamicwaters.gui.banks.yours").withStyle(ChatFormatting.GREEN);
        g.centeredText(font, this.title.copy().append(" ").append(source), this.width / 2, 6, 0xFFFFFFFF);

        final List<RiverbankPaletteDraft.Entry> entries = draft.places[tab];
        final int total = draft.totalWeight(tab);
        final RiverbankPaletteDraft.Entry hover = rowAt(mouseX, mouseY);
        for (int r = 0; r < listRows; r++) {
            final int i = page * listRows + r;
            if (i >= entries.size()) break;
            final RiverbankPaletteDraft.Entry e = entries.get(i);
            final int y = listY + r * ROW;
            if (e == hover) g.fill(left - 1, y - 1, left + colW - 55, y + 17, 0x40FFFFFF);
            BlockSwatch.draw(g, e.id, left, y, 16);
            if (RiverbankPaletteDraft.KEEP.equals(e.id)) g.text(font, "~", left + 10, y + 8, 0xFFFFFFFF);
            final String share = "x" + e.weight + "  " + Math.round(100.0 * e.weight / Math.max(1, total)) + "%";
            final int shareW = font.width(share);
            final int nameW = colW - 56 - 22 - shareW - 6;
            g.text(font, RiverbankPalettesScreen.fit(font, BlockSwatch.name(e.id).getString(), nameW), left + 20, y + 4,
                    BlockSwatch.exists(e.id) ? 0xFFFFFFFF : 0xFFFF5555);
            g.text(font, share, left + colW - 56 - shareW - 2, y + 4, 0xFFA0A0A0);
        }
        if (entries.isEmpty()) {
            final String key = tab == RiverbankPaletteDraft.BARS ? "dynamicwaters.gui.banks.emptyBars"
                    : tab == RiverbankPaletteDraft.FLATS ? "dynamicwaters.gui.banks.emptyFlats" : "dynamicwaters.gui.banks.emptyPlace";
            g.text(font, Component.translatable(key), left, listY + 4, 0xFF909090);
        }

        g.text(font, Component.translatable("dynamicwaters.gui.banks.preview"), prevX, prevY - 11, 0xFF9EFFFE);
        if (draft.climate != null) {
            final Component c = Component.translatable("dynamicwaters.gui.banks.hasClimate");
            g.text(font, c, this.width - 8 - font.width(c), prevY - 11, 0xFFC0C080);
        }
        ShorePreview.render(g, draft, seed, previewMode, prevX, prevY, prevW, prevH);

        if (error != null) g.centeredText(font, error, this.width / 2, this.height - 64, 0xFFFF5555);

        if (hover != null) {
            g.setComponentTooltipForNextFrame(font, List.of(BlockSwatch.name(hover.id),
                    Component.literal(hover.id).withStyle(ChatFormatting.DARK_GRAY),
                    Component.translatable("dynamicwaters.gui.banks.clickToChange").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        final double mx = event.x(), my = event.y();
        final int button = event.button();
        final RiverbankPaletteDraft.Entry e = rowAt(mx, my);
        if (e != null && button == 0) {
            openPicker(e);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String key;
        private final double min, max, step;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, String key, double min, double max, double step, double value, DoubleConsumer onChange) {
            super(x, y, w, 20, Component.empty(), Mth.clamp((value - min) / (max - min), 0.0, 1.0));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            setTooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks." + key + ".tooltip")));
            updateMessage();
        }

        private double current() {
            return Math.round((min + (max - min) * this.value) / step) * step;
        }

        @Override
        protected void updateMessage() {
            final double v = current();
            setMessage(Component.translatable("dynamicwaters.gui.banks." + key,
                    step >= 1.0 ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.2f", v)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
