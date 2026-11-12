package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.RiverbankPalettes;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class RiverbankPalettesScreen extends com.AdamNew.dynamicwaters.client.gui.DWScreen {
    private final Screen parent;
    private StyleList list;
    private Button editButton;
    private Button deleteButton;
    private final List<RiverbankPaletteDraft> drafts = new ArrayList<>();
    private String selectName;

    public RiverbankPalettesScreen(Screen parent) {
        super(Component.translatable("dynamicwaters.gui.banks.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        this.parent = parent;
        load();
    }

    void refresh(String select) {
        this.selectName = select;
        load();
    }

    private void load() {
        drafts.clear();
        final Set<String> mine = new HashSet<>();
        final Path dir = RiverbankPalettes.userFolder();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".json") && Files.isRegularFile(f)).sorted().toList()) {
                final String name = p.getFileName().toString().replaceFirst("\\.json$", "");
                try {
                    drafts.add(RiverbankPaletteDraft.fromJson(name, Files.readString(p, StandardCharsets.UTF_8), false));
                    mine.add(name);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        for (String file : RiverbankPalettes.builtInFileNames()) {
            final String name = file.replaceFirst("\\.json$", "");
            if (mine.contains(name)) {
                for (RiverbankPaletteDraft d : drafts) {
                    if (d.name.equals(name)) d.replacesBuiltIn = true;
                }
                continue;
            }
            final String text = RiverbankPalettes.builtInText(file);
            if (text != null) drafts.add(RiverbankPaletteDraft.fromJson(name, text, true));
        }
    }

    @Override
    protected void init() {
        list = new StyleList(this.minecraft, this.width, this.height - 36 - 58, 36, 24);
        for (RiverbankPaletteDraft d : drafts) {
            final StyleEntry e = new StyleEntry(d);
            list.add(e);
            if (d.name.equals(selectName)) list.setSelected(e);
        }
        addRenderableWidget(list);

        final int cx = this.width / 2;
        editButton = addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.edit"), b -> edit())
                .bounds(cx - 154, this.height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.new"), b -> create())
                .bounds(cx - 50, this.height - 52, 100, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.delete"), b -> delete())
                .bounds(cx + 54, this.height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.openFolder"),
                        b -> com.mojang.blaze3d.Blaze3D.openPath(RiverbankPalettes.userFolder()))
                .bounds(cx - 154, this.height - 28, 150, 20)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.banks.openFolder.tooltip")))
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 4, this.height - 28, 150, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        final StyleEntry sel = list == null ? null : list.getSelected();
        if (editButton != null) {
            editButton.active = sel != null;
            editButton.setTooltip(sel != null && sel.draft.builtIn
                    ? Tooltip.create(Component.translatable("dynamicwaters.gui.banks.edit.builtInTip")) : null);
        }
        if (deleteButton != null) {
            deleteButton.active = sel != null && !sel.draft.builtIn;
            deleteButton.setTooltip(sel != null && sel.draft.replacesBuiltIn
                    ? Tooltip.create(Component.translatable("dynamicwaters.gui.banks.delete.restoresTip")) : null);
        }
    }

    private void edit() {
        final StyleEntry sel = list.getSelected();
        if (sel == null) return;
        this.minecraft.gui.setScreen(new RiverbankPaletteEditScreen(this, sel.draft.copy(), sel.draft.builtIn ? null : sel.draft.name));
    }

    private void create() {
        String name = "my_palette";
        for (int i = 2; exists(name); i++) name = "my_palette_" + i;
        this.minecraft.gui.setScreen(new RiverbankPaletteEditScreen(this, RiverbankPaletteDraft.blank(name), null));
    }

    private boolean exists(String name) {
        for (RiverbankPaletteDraft d : drafts) {
            if (d.name.equals(name)) return true;
        }
        return false;
    }

    private void delete() {
        final StyleEntry sel = list.getSelected();
        if (sel == null || sel.draft.builtIn) return;
        final String name = sel.draft.name;
        this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    Files.deleteIfExists(RiverbankPalettes.userFolder().resolve(name + ".json"));
                } catch (IOException ignored) {
                }
                RiverbankPalettes.reload();
                refresh(name);
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("dynamicwaters.gui.banks.deleteConfirm", name),
                sel.draft.replacesBuiltIn ? Component.translatable("dynamicwaters.gui.banks.delete.restoresTip")
                        : Component.translatable("dynamicwaters.gui.banks.deleteForever")));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        com.AdamNew.dynamicwaters.client.gui.DWConfigScreen.tintWater(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        g.centeredText(this.font, Component.translatable("dynamicwaters.gui.banks.subtitle"), this.width / 2, 21, 0xFF9EFFFE);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    static String fit(net.minecraft.client.gui.Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
    }

    static String matchText(RiverbankPaletteDraft d) {
        return matchText(d.biomes, d.tags, d.climate, d.not, d.isDefault());
    }

    static String matchText(List<String> biomes, List<String> tags, com.google.gson.JsonElement climate, List<String> not,
            boolean isDefault) {
        final StringBuilder sb = new StringBuilder();
        if (!biomes.isEmpty()) sb.append(String.join(", ", biomes.stream().map(s -> s.replace("minecraft:", "")).toList()));
        if (!tags.isEmpty()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.join(", ", tags.stream().map(s -> "#" + s.replace("minecraft:", "")).toList()));
        }
        if (climate != null) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(Component.translatable("dynamicwaters.gui.banks.climate").getString());
        }
        if (!not.isEmpty()) {
            sb.append("  -").append(String.join(", -", not.stream().map(s -> s.replace("minecraft:", "")).toList()));
        }
        if (isDefault && sb.length() == 0) return Component.translatable("dynamicwaters.gui.banks.default").getString();
        return sb.toString();
    }

    static String commonest(List<RiverbankPaletteDraft.Entry> mix) {
        String best = RiverbankPaletteDraft.KEEP;
        int most = 0;
        for (RiverbankPaletteDraft.Entry e : mix) {
            if (e.weight > most) {
                most = e.weight;
                best = e.id;
            }
        }
        return best;
    }

    private final class StyleList extends ObjectSelectionList<StyleEntry> {
        StyleList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        void add(StyleEntry e) {
            addEntry(e);
        }

        @Override
        public int getRowWidth() {
            return Math.min(380, this.width - 40);
        }

        @Override
        public void setSelected(StyleEntry entry) {
            super.setSelected(entry);
            updateButtons();
        }
    }

    private final class StyleEntry extends ObjectSelectionList.Entry<StyleEntry> {
        final RiverbankPaletteDraft draft;
        private long lastClick;

        StyleEntry(RiverbankPaletteDraft draft) {
            this.draft = draft;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            final int top = getContentY(), left = getContentX(), width = getContentWidth();
            final var font = RiverbankPalettesScreen.this.font;

            final int[] order = { RiverbankPaletteDraft.SHORE, RiverbankPaletteDraft.BARS, RiverbankPaletteDraft.RAPIDS, RiverbankPaletteDraft.SILT };
            for (int k = 0; k < 4; k++) {
                BlockSwatch.draw(g, commonest(draft.effective(order[k])), left + 2 + k * 17, top + 2, 16);
                if (k >= 2) g.fill(left + 2 + k * 17, top + 2, left + 18 + k * 17, top + 18, 0x303567C9);
            }
            final int tx = left + 74;
            final Component source = draft.builtIn
                    ? Component.translatable("dynamicwaters.gui.banks.builtIn").withStyle(ChatFormatting.DARK_AQUA)
                    : draft.replacesBuiltIn
                            ? Component.translatable("dynamicwaters.gui.banks.replaces").withStyle(ChatFormatting.GOLD)
                            : Component.translatable("dynamicwaters.gui.banks.yours").withStyle(ChatFormatting.GREEN);
            final int sw = font.width(source);
            g.text(font, source, left + width - sw - 4, top + 2, 0xFFFFFFFF);
            g.text(font, fit(font, draft.name, width - 80 - sw - 8), tx, top + 2, draft.enabled ? 0xFFFFFFFF : 0xFF808080);
            final String match = draft.enabled ? matchText(draft)
                    : Component.translatable("dynamicwaters.gui.banks.off").getString() + " - " + matchText(draft);
            g.text(font, fit(font, match, width - 80), tx, top + 12, draft.enabled ? 0xFF909090 : 0xFFB05050);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            final long now = Util.getMillis();
            if (RiverbankPalettesScreen.this.list.getSelected() == this && now - lastClick < 300L) {
                edit();
            }
            lastClick = now;
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(draft.name);
        }
    }
}
