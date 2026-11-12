package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.WaterPlantSets;
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

public final class WaterPlantSetsScreen extends com.AdamNew.dynamicwaters.client.gui.DWScreen {
    private final Screen parent;
    private SetList list;
    private Button editButton;
    private Button deleteButton;
    private final List<WaterPlantDraft> drafts = new ArrayList<>();
    private String selectName;

    public WaterPlantSetsScreen(Screen parent) {
        super(Component.translatable("dynamicwaters.gui.plants.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
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
        final Path dir = WaterPlantSets.userFolder();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".json") && Files.isRegularFile(f)).sorted().toList()) {
                final String name = p.getFileName().toString().replaceFirst("\\.json$", "");
                try {
                    drafts.add(WaterPlantDraft.fromJson(name, Files.readString(p, StandardCharsets.UTF_8), false));
                    mine.add(name);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        for (String file : WaterPlantSets.builtInFileNames()) {
            final String name = file.replaceFirst("\\.json$", "");
            if (mine.contains(name)) {
                for (WaterPlantDraft d : drafts) {
                    if (d.name.equals(name)) d.replacesBuiltIn = true;
                }
                continue;
            }
            final String text = WaterPlantSets.builtInText(file);
            if (text != null) drafts.add(WaterPlantDraft.fromJson(name, text, true));
        }
    }

    @Override
    protected void init() {
        list = new SetList(this.minecraft, this.width, this.height - 36 - 58, 36, 24);
        for (WaterPlantDraft d : drafts) {
            final SetEntry e = new SetEntry(d);
            list.add(e);
            if (d.name.equals(selectName)) list.setSelected(e);
        }
        addRenderableWidget(list);

        final int cx = this.width / 2;
        editButton = addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.edit"), b -> edit())
                .bounds(cx - 154, this.height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.plants.new"), b -> create())
                .bounds(cx - 50, this.height - 52, 100, 20)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants.new.tooltip")))
                .build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.delete"), b -> delete())
                .bounds(cx + 54, this.height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dynamicwaters.gui.banks.openFolder"),
                        b -> com.mojang.blaze3d.Blaze3D.openPath(WaterPlantSets.userFolder()))
                .bounds(cx - 154, this.height - 28, 150, 20)
                .tooltip(Tooltip.create(Component.translatable("dynamicwaters.gui.plants.openFolder.tooltip")))
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 4, this.height - 28, 150, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        final SetEntry sel = list == null ? null : list.getSelected();
        if (editButton != null) {
            editButton.active = sel != null;
            editButton.setTooltip(sel != null && sel.draft.builtIn
                    ? Tooltip.create(Component.translatable("dynamicwaters.gui.plants.edit.builtInTip")) : null);
        }
        if (deleteButton != null) {
            deleteButton.active = sel != null && !sel.draft.builtIn;
            deleteButton.setTooltip(sel != null && sel.draft.replacesBuiltIn
                    ? Tooltip.create(Component.translatable("dynamicwaters.gui.plants.delete.restoresTip")) : null);
        }
    }

    private void edit() {
        final SetEntry sel = list.getSelected();
        if (sel == null) return;
        this.minecraft.gui.setScreen(new WaterPlantSetEditScreen(this, sel.draft.copy(), sel.draft.builtIn ? null : sel.draft.name));
    }

    private void create() {
        String name = "my_plants";
        for (int i = 2; exists(name); i++) name = "my_plants_" + i;
        WaterPlantDraft base = null;
        for (WaterPlantDraft d : drafts) {
            if (d.name.equals("default")) base = d;
        }
        final WaterPlantDraft fresh = base != null ? WaterPlantDraft.startingFrom(name, base) : new WaterPlantDraft(name, false);
        this.minecraft.gui.setScreen(new WaterPlantSetEditScreen(this, fresh, null));
    }

    private boolean exists(String name) {
        for (WaterPlantDraft d : drafts) {
            if (d.name.equals(name)) return true;
        }
        return false;
    }

    private void delete() {
        final SetEntry sel = list.getSelected();
        if (sel == null || sel.draft.builtIn) return;
        final String name = sel.draft.name;
        this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    Files.deleteIfExists(WaterPlantSets.userFolder().resolve(name + ".json"));
                } catch (IOException ignored) {
                }
                WaterPlantSets.reload();
                refresh(name);
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("dynamicwaters.gui.plants.deleteConfirm", name),
                sel.draft.replacesBuiltIn ? Component.translatable("dynamicwaters.gui.plants.delete.restoresTip")
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
        g.centeredText(this.font, Component.translatable("dynamicwaters.gui.plants.subtitle"), this.width / 2, 21, 0xFF9EFFFE);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private final class SetList extends ObjectSelectionList<SetEntry> {
        SetList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        void add(SetEntry e) {
            addEntry(e);
        }

        @Override
        public int getRowWidth() {
            return Math.min(380, this.width - 40);
        }

        @Override
        public void setSelected(SetEntry entry) {
            super.setSelected(entry);
            updateButtons();
        }
    }

    private final class SetEntry extends ObjectSelectionList.Entry<SetEntry> {
        final WaterPlantDraft draft;
        private long lastClick;

        SetEntry(WaterPlantDraft draft) {
            this.draft = draft;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            final int top = getContentY(), left = getContentX(), width = getContentWidth();
            final var font = WaterPlantSetsScreen.this.font;

            for (int k = 0; k < WaterPlantDraft.KINDS.length; k++) {
                final int x = left + 2 + k * 17, y = top + 2;
                g.fill(x, y, x + 16, y + 16, 0xFF3567C9);
                if (!draft.plants[k].isEmpty()) BlockSwatch.draw(g, draft.plants[k].get(0).blockId(), x, y, 16);
            }
            final int tx = left + 57;
            final Component source = draft.builtIn
                    ? Component.translatable("dynamicwaters.gui.plants.builtIn").withStyle(ChatFormatting.DARK_AQUA)
                    : draft.replacesBuiltIn
                            ? Component.translatable("dynamicwaters.gui.plants.replaces").withStyle(ChatFormatting.GOLD)
                            : Component.translatable("dynamicwaters.gui.plants.yours").withStyle(ChatFormatting.GREEN);
            final int sw = font.width(source);
            g.text(font, source, left + width - sw - 4, top + 2, 0xFFFFFFFF);
            final String title = draft.name + "  " + Component.translatable("dynamicwaters.gui.plants.count", draft.plantCount()).getString();
            g.text(font, RiverbankPalettesScreen.fit(font, title, width - 63 - sw - 8), tx, top + 2,
                    draft.enabled ? 0xFFFFFFFF : 0xFF808080);
            final String where = RiverbankPalettesScreen.matchText(draft.biomes, draft.tags, draft.climate, draft.not, draft.isDefault());
            final String match = draft.enabled ? where
                    : Component.translatable("dynamicwaters.gui.banks.off").getString() + " - " + where;
            g.text(font, RiverbankPalettesScreen.fit(font, match, width - 63), tx, top + 12, draft.enabled ? 0xFF909090 : 0xFFB05050);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            final long now = Util.getMillis();
            if (WaterPlantSetsScreen.this.list.getSelected() == this && now - lastClick < 300L) {
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
