package com.AdamNew.dynamicwaters.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class DWCategoryScreen extends OptionsSubScreen {
    private final DWConfigCategories.Category category;

    private MultiLineTextWidget description;

    public DWCategoryScreen(Screen parent, DWConfigCategories.Category category) {
        super(parent, Minecraft.getInstance().options, category.title().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        this.category = category;
    }

    @Override
    protected void addTitle() {
        final LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(DESCRIPTION_GAP));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        this.description = header.addChild(new MultiLineTextWidget(category.description().copy().withColor(0x9EFFFE), this.font)
                .setMaxWidth(descriptionWidth()).setCentered(true));
        fitHeader();
    }

    @Override
    protected void repositionElements() {
        if (this.description != null) {
            this.description.setMaxWidth(descriptionWidth());
            fitHeader();
        }
        super.repositionElements();
    }

    private static final int DESCRIPTION_GAP = 4;
    private static final int DESCRIPTION_MARGIN = 20;
    private static final int HEADER_PADDING = 14;

    private int descriptionWidth() {
        return Math.max(100, this.width - 2 * DESCRIPTION_MARGIN);
    }

    private void fitHeader() {
        this.layout.setHeaderHeight(Math.max(HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT,
                this.font.lineHeight + DESCRIPTION_GAP + this.description.getHeight() + HEADER_PADDING));
    }

    @Override
    protected void addOptions() {
        for (DWConfigCategories.Option option : category.options()) {
            final AbstractWidget control = controlFor(option);
            if (control == null) continue;
            final StringWidget label = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, option.label(), this.font);
            final Component tip = tooltipFor(option);
            label.setTooltip(Tooltip.create(tip));
            control.setTooltip(Tooltip.create(tip));
            this.list.addSmall(label, control);

            if ("riverbankPalettes".equals(option.key())) {
                editorRow("dynamicwaters.gui.banks",
                        () -> new com.AdamNew.dynamicwaters.client.gui.shore.RiverbankPalettesScreen(this));
            } else if ("waterPlants".equals(option.key())) {
                editorRow("dynamicwaters.gui.plants",
                        () -> new com.AdamNew.dynamicwaters.client.gui.shore.WaterPlantSetsScreen(this));
            }
        }

    }

    private void editorRow(String prefix, java.util.function.Supplier<Screen> editor) {
        this.list.addSmall(
                new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.translatable(prefix + ".label"), this.font),
                Button.builder(Component.translatable(prefix + ".button"), btn -> this.minecraft.gui.setScreen(editor.get()))
                        .width(Button.DEFAULT_WIDTH)
                        .tooltip(Tooltip.create(Component.translatable(prefix + ".button.tooltip")))
                        .build());
    }

    private Component tooltipFor(DWConfigCategories.Option option) {
        final Component base;
        final ModConfigSpec.ValueSpec spec = specOf(option.value());

        final String tipKey = "dynamicwaters.configuration." + option.key() + ".tooltip";
        if (net.minecraft.locale.Language.getInstance().has(tipKey)) {
            base = Component.translatable(tipKey);
        } else if (spec != null && spec.getComment() != null && !spec.getComment().isBlank()) {
            base = Component.literal(spec.getComment());
        } else {
            base = option.label();
        }
        final net.minecraft.network.chat.MutableComponent out = base.copy();
        if (spec != null && spec.getDefault() != null) {
            out.append("\n").append(Component.translatable("dynamicwaters.gui.defaultValue", defaultText(spec.getDefault())).withStyle(ChatFormatting.GRAY));
        }
        if (option.worldgen()) {
            out.append("\n\n").append(Component.translatable("dynamicwaters.gui.worldgenNote").withStyle(ChatFormatting.YELLOW));
        }
        return out;
    }

    private static String defaultText(Object def) {
        if (def instanceof Boolean b) return b ? net.minecraft.client.resources.language.I18n.get("dynamicwaters.gui.on") : net.minecraft.client.resources.language.I18n.get("dynamicwaters.gui.off");
        if (def instanceof Enum<?> e) return DWConfigCategories.enumStatus(e.getDeclaringClass().getSimpleName(), e).getString();
        if (def instanceof Double d) return String.format("%.1f", d);
        return String.valueOf(def);
    }

    private static ModConfigSpec.ValueSpec specOf(ModConfigSpec.ConfigValue<?> value) {
        try {
            final Object raw = com.AdamNew.dynamicwaters.Config.SPEC.getSpec().get(value.getPath());
            return raw instanceof ModConfigSpec.ValueSpec vs ? vs : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private AbstractWidget controlFor(DWConfigCategories.Option option) {
        final ModConfigSpec.ConfigValue<?> value = option.value();
        final int w = Button.DEFAULT_WIDTH, h = Button.DEFAULT_HEIGHT;

        if (value instanceof ModConfigSpec.BooleanValue bool) {
            return CycleButton.onOffBuilder(bool.get())
                    .displayOnlyValue()
                    .create(0, 0, w, h, option.label(), (btn, v) -> {
                        bool.set(v);
                        bool.save();
                    });
        }
        if (value instanceof ModConfigSpec.EnumValue<?> en) {
            return enumControl((ModConfigSpec.EnumValue) en, option, w, h);
        }
        if (value instanceof ModConfigSpec.IntValue iv) {
            final ModConfigSpec.ValueSpec spec = specOf(iv);
            final ModConfigSpec.Range<Integer> range = spec != null ? spec.getRange() : null;
            final int min = range != null ? range.getMin() : 0;
            final int max = range != null ? range.getMax() : Math.max(1, iv.get() * 4);
            return new NumberSlider(w, h, min, max, iv.get(), 1.0, v -> {
                iv.set((int) Math.round(v));
                iv.save();
            });
        }
        if (value instanceof ModConfigSpec.DoubleValue dv) {
            final ModConfigSpec.ValueSpec spec = specOf(dv);
            final ModConfigSpec.Range<Double> range = spec != null ? spec.getRange() : null;
            final double min = range != null ? range.getMin() : 0.0;
            final double max = range != null ? range.getMax() : Math.max(1.0, dv.get() * 4.0);

            final double step = (max - min) <= 20.0 ? 0.1 : 1.0;
            return new NumberSlider(w, h, min, max, dv.get(), step, v -> {
                dv.set(v);
                dv.save();
            });
        }
        return null;
    }

    private <E extends Enum<E>> AbstractWidget enumControl(ModConfigSpec.EnumValue<E> ev, DWConfigCategories.Option option, int w, int h) {
        final E current = ev.get();
        final E[] values = current.getDeclaringClass().getEnumConstants();
        final String enumName = current.getDeclaringClass().getSimpleName();
        return CycleButton.<E>builder(v -> DWConfigCategories.enumStatus(enumName, v), current)
                .withValues(values)
                .displayOnlyValue()
                .create(0, 0, w, h, option.label(), (btn, v) -> {
                    ev.set(v);
                    ev.save();
                });
    }

    private static final class NumberSlider extends AbstractSliderButton {
        private final double min, max, step;
        private final java.util.function.DoubleConsumer onChange;

        NumberSlider(int w, int h, double min, double max, double initial, double step, java.util.function.DoubleConsumer onChange) {
            super(0, 0, w, h, Component.empty(), max > min ? (initial - min) / (max - min) : 0.0);
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            updateMessage();
        }

        private double current() {
            final double raw = min + (max - min) * this.value;
            return Math.round(raw / step) * step;
        }

        @Override
        protected void updateMessage() {
            final double v = current();
            setMessage(Component.literal(step >= 1.0 ? String.format("%d", Math.round(v)) : String.format("%.1f", v)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        DWConfigScreen.tintWater(graphics, this.width, this.height);
    }

    static List<DWConfigCategories.Option> optionsOf(DWConfigCategories.Category c) {
        return c.options();
    }
}
