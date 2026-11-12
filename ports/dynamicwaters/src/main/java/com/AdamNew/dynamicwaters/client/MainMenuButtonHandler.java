package com.AdamNew.dynamicwaters.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;
import java.util.function.Consumer;

public class MainMenuButtonHandler {
    private static final String[] ROW_BUTTONS = {"fml.menu.mods", "fml.menu.mods.title", "modmenu.title", "menu.online"};

    private static AbstractWidget rowButton(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners) {
        for (String key : ROW_BUTTONS) {
            final String name = net.minecraft.client.resources.language.I18n.get(key);
            for (net.minecraft.client.gui.components.events.GuiEventListener listener : listeners) {
                if (listener instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(name)) return widget;
            }
        }
        return null;
    }

    public static void onScreenInit(Screen screen, List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners,
            Consumer<AbstractWidget> add) {
        if (screen instanceof TitleScreen) {
            if (com.AdamNew.dynamicwaters.Config.SHOW_MAIN_MENU_BUTTON.get()) {
                int targetX = screen.width / 2 + 104;
                int targetY = screen.height / 4 + 48 + 24 * 2;
                final AbstractWidget row = rowButton(listeners);
                if (row != null) {
                    targetY = row.getY();
                    int right = row.getX() + row.getWidth();
                    for (net.minecraft.client.gui.components.events.GuiEventListener listener : listeners) {
                        if (listener instanceof AbstractWidget widget && widget.getY() == targetY) {
                            right = Math.max(right, widget.getX() + widget.getWidth());
                        }
                    }
                    targetX = right + 4;
                }

                add.accept(Button.builder(Component.literal("D").withStyle(net.minecraft.ChatFormatting.AQUA), (button) -> {
                    Minecraft.getInstance().gui.setScreen(new com.AdamNew.dynamicwaters.client.gui.DWConfigScreen(screen));
                })
                .bounds(targetX, targetY, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Dynamic Waters Config")))
                .build());
            }
        }
    }
}
