package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.WaterPlantSets;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Pattern;

final class WaterPlantSetEditScreen extends com.AdamNew.dynamicwaters.client.gui.DWScreen {
    private static final int ROW = 22;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]+");

    private static final double MIN_CHANCE = 1.0E-4;

    private static final int DEPTH_STEPS = 32;

    private final WaterPlantSetsScreen parent;
    private final WaterPlantDraft draft;

    private final String fromFile;

    private int tab;
    private int selected;
    private int page;
    private boolean dirty;
    private Component error;

    private EditBox nameBox;
    private int left, colW, listY, listRows, panelX, panelW;

    WaterPlantSetEditScreen(WaterPlantSetsScreen parent, WaterPlantDraft draft, String fromFile) {
        super(Component.translatable("dynamicwaters.gui.plants.editorTitle"));
        this.parent = parent;
        this.draft = draft;
        this.fromFile = fromFile;
    }

    private List<WaterPlantDraft.Plant> plants() {
        return draft.plants[tab];
    }

    private WaterPlantDraft.Plant selectedPlant() {
        final List<WaterPlantDraft.Plant> l = plants();
        return selected >= 0 && selected < l.size() ? l.get(selected) : null;
    }

    @Override
    protected void init() {
        left = 8;
        final int fullW = this.width - 16;
        colW = Math.min(230, (int) (this.width * 0.42));
        panelX = left + colW + 10;
        panelW = this.width - 8 - panelX;

        final String name = nameBox != null ? nameBox.getValue() : draft.name;
        nameBox = box(left, 18, 96, name, "name", s -> draft.name = s.trim());
        nameBox.setMaxLength(40);
        box(left + 100, 18, fullW - 100, RiverbankPaletteDraft.joinList(draft.biomes), "biomes", s -> {
            draft.biomes.clear();
            draft.biomes.addAll(RiverbankPaletteDraft.parseList(s, false));
        });
        final int half = (fullW - 4) / 2;
        box(left, 38, half, String.join(", ", draft.tags.stream().map(t -> "#" + t).toList()), "tags", s -> {
            draft.tags.clear();
            draft.tags.addAll(RiverbankPaletteDraft.parseList(s, true));
        });
        box(left + half + 4, 38, fullW - half - 4, RiverbankPaletteDraft.joinList(draft.not), "not", s -> {
            draft.not.clear();
            draft.not.addAll(RiverbankPaletteDraft.parseNot(s));
        });

        final int tabW = fullW / WaterPlantDraft.KINDS.length;
        for (int k = 0; k < WaterPlantDraft.KINDS.length; k++) {
            final int kind = k;
            final Button b = Button.builder(tabLabel(k), btn -> {
                        tab = kind;
                        selected = 0;
                        page = 0;
                        dirty = true;
                    })
                    .bounds(left + k * tabW, 58, tabW - 2, 16)
                    .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants.kind." + WaterPlantDraft.KINDS[k] + ".desc")))
                    .build();
            b.active = k != tab;
            addRenderableWidget(b);
        }

        listY = 80;
        final int addY = this.height - 52;
        listRows = Math.max(1, (addY - 2 - listY) / ROW);
        final List<WaterPlantDraft.Plant> list = plants();
        selected = Mth.clamp(selected, 0, Math.max(0, list.size() - 1));
        final int pages = Math.max(1, (list.size() + listRows - 1) / listRows);
        page = Mth.clamp(page, 0, pages - 1);
        for (int r = 0; r < listRows; r++) {
            final int i = page * listRows + r;
            if (i >= list.size()) break;
            final int y = listY + r * ROW + 3;
            final int bx = left + colW - 34;
            if (i > 0) {
                small("▲", bx, y, "moveUp", () -> {
                    Collections.swap(list, i, i - 1);
                    if (selected == i) selected = i - 1;
                    else if (selected == i - 1) selected = i;
                });
            }
            small("x", bx + 17, y, "remove", () -> {
                list.remove(i);
                if (selected >= i && selected > 0) selected--;
            });
        }

        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.plants.add"), b -> openPicker(null))
                .bounds(left, addY, 110, 20).build());
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

        final WaterPlantDraft.Plant p = selectedPlant();
        if (p != null) plantControls(p);

        final int by = this.height - 28;
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("dynamicwaters.gui.plants.setOn").withStyle(ChatFormatting.GREEN),
                        Component.translatable("dynamicwaters.gui.plants.setOff").withStyle(ChatFormatting.RED), draft.enabled)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("dynamicwaters.gui.plants.setSwitch.tooltip")))
                .create(left, by, 130, 20, Component.translatable("dynamicwaters.gui.plants.setSwitch"),
                        (b, v) -> draft.enabled = v));
        addRenderableWidget(Button.builder(Component.translatable(draft.builtIn
                                ? "dynamicwaters.gui.banks.saveCopy" : "dynamicwaters.gui.banks.save"), b -> save())
                .bounds(this.width - 8 - 206, by, 102, 20)
                .tooltip(draft.builtIn ? Tooltip.create(Component.translatable("dynamicwaters.gui.plants.saveCopy.tooltip")) : null)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width - 8 - 102, by, 102, 20).build());
    }

    private void plantControls(WaterPlantDraft.Plant p) {
        final int x = panelX, w = panelW, hw = (w - 3) / 2, x2 = x + hw + 3;
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.plants.changeBlock"), b -> openPicker(p))
                .bounds(x + w - 70, listY - 2, 70, 16)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants.changeBlock.tooltip")))
                .build());

        int y = listY + 16;
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("dynamicwaters.gui.plants.at." + v), p.at)
                .withValues(WaterPlantDraft.PLACES)
                .withTooltip(v -> Tooltip.create(Component.translatable("dynamicwaters.gui.plants.at." + v + ".desc")))
                .create(x, y, w, 20, Component.translatable("dynamicwaters.gui.plants.at"), (b, v) -> {
                    p.at = v;
                    dirty = true;
                }));

        y += 22;
        addRenderableWidget(new Slider(x, y, w, (Math.log10(Math.max(MIN_CHANCE, p.chance)) - Math.log10(MIN_CHANCE)) / -Math.log10(MIN_CHANCE),
                pos -> nice(Math.pow(10.0, Math.log10(MIN_CHANCE) * (1.0 - pos))),
                v -> Component.translatable("dynamicwaters.gui.plants.chance", chanceText(v)),
                v -> p.chance = v, "chance"));

        y += 22;
        final boolean shore = "shore".equals(p.at);
        final Slider from = new Slider(x, y, hw, steps(p.minDepth, 1, DEPTH_STEPS), pos -> step(pos, 1, DEPTH_STEPS),
                v -> Component.translatable("dynamicwaters.gui.plants.depthFrom", (int) v), v -> p.minDepth = (int) v, "depth");
        final Slider to = new Slider(x2, y, w - hw - 3,
                steps(p.maxDepth >= WaterPlantDraft.ANY_DEPTH ? DEPTH_STEPS + 1 : Math.min(p.maxDepth, DEPTH_STEPS), 1, DEPTH_STEPS + 1),
                pos -> step(pos, 1, DEPTH_STEPS + 1),
                v -> Component.translatable("dynamicwaters.gui.plants.depthTo", v > DEPTH_STEPS ? "∞" : String.valueOf((int) v)),
                v -> p.maxDepth = v > DEPTH_STEPS ? WaterPlantDraft.ANY_DEPTH : (int) v, "depth");
        from.active = !shore;
        to.active = !shore;
        addRenderableWidget(from);
        addRenderableWidget(to);

        y += 22;
        addRenderableWidget(toggle(x, y, hw, "calm", p.calm, v -> p.calm = v));
        addRenderableWidget(toggle(x2, y, w - hw - 3, "alone", p.alone, v -> p.alone = v));

        y += 22;
        addRenderableWidget(new Slider(x, y, hw, steps(p.patchSize, 0, 16), pos -> step(pos, 0, 16),
                v -> v < 1 ? Component.translatable("dynamicwaters.gui.plants.patchOff")
                        : Component.translatable("dynamicwaters.gui.plants.patch", (int) v),
                v -> {
                    final boolean had = p.patchSize > 0;
                    p.patchSize = (int) v;
                    if (had != p.patchSize > 0) dirty = true;
                }, "patch"));
        final Slider share = new Slider(x2, y, w - hw - 3, steps((int) Math.round(p.patchShare * 20.0), 1, 20), pos -> step(pos, 1, 20) / 20.0,
                v -> Component.translatable("dynamicwaters.gui.plants.patchShare", Math.round(v * 100.0)), v -> p.patchShare = v, "patchShare");
        share.active = p.patchSize > 0;
        addRenderableWidget(share);

        y += 22;
        addRenderableWidget(new Slider(x, y, hw, steps(p.minHeight, 1, 8), pos -> step(pos, 1, 8),
                v -> Component.translatable("dynamicwaters.gui.plants.heightFrom", (int) v), v -> p.minHeight = (int) v, "height"));
        addRenderableWidget(new Slider(x2, y, w - hw - 3, steps(p.maxHeight, 1, 8), pos -> step(pos, 1, 8),
                v -> Component.translatable("dynamicwaters.gui.plants.heightTo", (int) v), v -> p.maxHeight = (int) v, "height"));
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> onChange) {
        return CycleButton.booleanBuilder(Component.translatable("dynamicwaters.gui.plants.yes").withStyle(ChatFormatting.GREEN),
                        Component.translatable("dynamicwaters.gui.plants.no"), value)
                .withTooltip(v -> Tooltip.create(Component.translatable("dynamicwaters.gui.plants." + key + ".tooltip")))
                .create(x, y, w, 20, Component.translatable("dynamicwaters.gui.plants." + key), (b, v) -> onChange.accept(v));
    }

    private EditBox box(int x, int y, int w, String value, String key, Consumer<String> onChange) {
        final EditBox b = new EditBox(this.font, x, y, w, 16, Component.translatable("dynamicwaters.gui.plants." + key));
        b.setMaxLength(512);
        b.setValue(value);
        b.setHint(Component.translatable("dynamicwaters.gui.plants." + key + ".hint").withStyle(ChatFormatting.DARK_GRAY));
        b.setTooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants." + key + ".tooltip")));
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private void small(String label, int x, int y, String tipKey, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    action.run();
                    dirty = true;
                })
                .bounds(x, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants." + tipKey)))
                .build());
    }

    private Component tabLabel(int k) {
        final int n = draft.plants[k].size();
        final Component name = Component.translatable("dynamicwaters.gui.plants.kind." + WaterPlantDraft.KINDS[k]);
        return name.copy().append(Component.literal(" " + n).withStyle(ChatFormatting.GRAY));
    }

    private void openPicker(WaterPlantDraft.Plant change) {
        this.minecraft.gui.setScreen(new BlockPickerScreen(this, id -> {
            if (change != null) {
                change.id = id;
                change.at = WaterPlantDraft.usualPlace(id);
                return;
            }
            plants().add(WaterPlantDraft.plantOf(id));
            selected = plants().size() - 1;
            page = selected / Math.max(1, listRows);
        }, true));
    }

    private int rowAt(double mx, double my) {
        if (mx < left || mx >= left + colW - 36 || my < listY || my >= listY + listRows * ROW) return -1;
        final int i = page * listRows + (int) ((my - listY) / ROW);
        return i >= 0 && i < plants().size() ? i : -1;
    }

    private void save() {
        final String name = nameBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            error = Component.translatable("dynamicwaters.gui.banks.badName").withStyle(ChatFormatting.RED);
            return;
        }
        final Path dir = WaterPlantSets.userFolder();
        final Path file = dir.resolve(name + ".json");
        final boolean sameFile = name.equals(fromFile);
        if (Files.exists(file) && !sameFile) {
            error = Component.translatable("dynamicwaters.gui.plants.nameTaken", name).withStyle(ChatFormatting.RED);
            return;
        }
        draft.name = name;

        for (List<WaterPlantDraft.Plant> list : draft.plants) {
            for (WaterPlantDraft.Plant p : list) {
                if (p.minDepth > p.maxDepth) {
                    final int t = p.minDepth;
                    p.minDepth = p.maxDepth;
                    p.maxDepth = t;
                }
                if (p.minHeight > p.maxHeight) {
                    final int t = p.minHeight;
                    p.minHeight = p.maxHeight;
                    p.maxHeight = t;
                }
            }
        }
        try {
            Files.writeString(file, draft.toJsonText(), StandardCharsets.UTF_8);
            if (fromFile != null && !sameFile) Files.deleteIfExists(dir.resolve(fromFile + ".json"));
        } catch (IOException e) {
            error = Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
            return;
        }
        WaterPlantSets.reload();
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
                ? Component.translatable("dynamicwaters.gui.plants.builtIn").withStyle(ChatFormatting.DARK_AQUA)
                : Component.translatable("dynamicwaters.gui.plants.yours").withStyle(ChatFormatting.GREEN);
        g.centeredText(font, this.title.copy().append(" ").append(source), this.width / 2, 6, 0xFFFFFFFF);

        final List<WaterPlantDraft.Plant> list = plants();
        final int hover = rowAt(mouseX, mouseY);
        for (int r = 0; r < listRows; r++) {
            final int i = page * listRows + r;
            if (i >= list.size()) break;
            final WaterPlantDraft.Plant p = list.get(i);
            final int y = listY + r * ROW;
            if (i == selected) g.fill(left - 2, y, left + colW - 36, y + ROW - 1, 0x5038C8FF);
            else if (i == hover) g.fill(left - 2, y, left + colW - 36, y + ROW - 1, 0x30FFFFFF);
            BlockSwatch.draw(g, p.blockId(), left, y + 3, 16);
            final int tw = colW - 36 - 22;
            g.text(font, RiverbankPalettesScreen.fit(font, BlockSwatch.name(p.blockId()).getString(), tw), left + 20, y + 2,
                    BlockSwatch.exists(p.blockId()) ? 0xFFFFFFFF : 0xFFFF5555);
            g.text(font, RiverbankPalettesScreen.fit(font, summary(p), tw), left + 20, y + 12, 0xFF909090);
        }
        if (list.isEmpty()) {
            g.text(font, Component.translatable("dynamicwaters.gui.plants.empty"), left, listY + 4, 0xFF909090);
        }

        final WaterPlantDraft.Plant p = selectedPlant();
        if (p != null) {
            g.text(font, RiverbankPalettesScreen.fit(font, BlockSwatch.name(p.blockId()).getString() + "  " + p.id, panelW - 76),
                    panelX, listY + 2, 0xFF9EFFFE);
            int hy = listY + 16 + 6 * 22 + 2;
            for (FormattedCharSequence line : font.split(Component.translatable("dynamicwaters.gui.plants.hint"), panelW)) {
                if (hy > this.height - 64) break;
                g.text(font, line, panelX, hy, 0xFF808080);
                hy += 10;
            }
        }
        if (draft.climate != null) {
            final Component c = Component.translatable("dynamicwaters.gui.banks.hasClimate");
            g.text(font, c, this.width - 8 - font.width(c), 6, 0xFFC0C080);
        }
        if (error != null) g.centeredText(font, error, this.width / 2, this.height - 64, 0xFFFF5555);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        final double mx = event.x(), my = event.y();
        final int button = event.button();
        final int i = rowAt(mx, my);
        if (i >= 0 && button == 0) {
            selected = i;
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private static String summary(WaterPlantDraft.Plant p) {
        final StringBuilder sb = new StringBuilder(chanceText(p.chance));
        sb.append(" · ").append(Component.translatable("dynamicwaters.gui.plants.at." + p.at).getString().toLowerCase(Locale.ROOT));
        if (!"shore".equals(p.at)) {
            sb.append(' ').append(Math.min(p.minDepth, p.maxDepth)).append(p.maxDepth >= WaterPlantDraft.ANY_DEPTH ? "+"
                    : "-" + Math.max(p.minDepth, p.maxDepth));
        }
        if (p.calm) sb.append(" · ").append(Component.translatable("dynamicwaters.gui.plants.calm.short").getString());
        if (p.patchSize > 0) sb.append(" · ").append(Component.translatable("dynamicwaters.gui.plants.patch.short").getString());
        if (p.alone) sb.append(" · ").append(Component.translatable("dynamicwaters.gui.plants.alone.short").getString());
        return sb.toString();
    }

    private static String chanceText(double c) {
        if (c >= 0.1) return Math.round(c * 100.0) + "%";
        if (c <= 0.0) return "0";
        return Component.translatable("dynamicwaters.gui.plants.oneIn", Math.round(1.0 / c)).getString();
    }

    private static double nice(double v) {
        if (v <= 0.0) return 0.0;
        return new java.math.BigDecimal(v).round(new java.math.MathContext(2)).doubleValue();
    }

    private static double steps(int value, int min, int max) {
        return max > min ? Mth.clamp((value - min) / (double) (max - min), 0.0, 1.0) : 0.0;
    }

    private static double step(double pos, int min, int max) {
        return min + Math.round(pos * (max - min));
    }

    private static final class Slider extends AbstractSliderButton {
        private final DoubleUnaryOperator toValue;
        private final DoubleFunction<Component> label;
        private final DoubleConsumer onChange;

        Slider(int x, int y, int w, double pos, DoubleUnaryOperator toValue, DoubleFunction<Component> label,
                DoubleConsumer onChange, String tipKey) {
            super(x, y, w, 20, Component.empty(), Mth.clamp(pos, 0.0, 1.0));
            this.toValue = toValue;
            this.label = label;
            this.onChange = onChange;
            setTooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants." + tipKey + ".tooltip")));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(toValue.applyAsDouble(this.value)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(toValue.applyAsDouble(this.value));
        }
    }
}
