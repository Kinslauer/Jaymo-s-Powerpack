package com.AdamNew.dynamicwaters.command;

import com.AdamNew.dynamicwaters.FlowZones;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.List;

public final class FlowZonesCommand {
    private FlowZonesCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("flowzones")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> remove(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        final List<FlowZones.Zone> zones = FlowZones.zones(source.getLevel());
        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.dynamicwaters.flowzones.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.dynamicwaters.flowzones.header", zones.size()), false);
        for (FlowZones.Zone zone : zones) {
            final String teleport = "/tp @s " + (zone.minX() + zone.maxX()) / 2 + " " + (zone.maxY() + 1) + " "
                    + (zone.minZ() + zone.maxZ()) / 2;
            final Component line = Component.translatable("commands.dynamicwaters.flowzones.entry", zone.id(),
                            zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ(), FlowZones.describe(zone.direction()))
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.SuggestCommand(teleport))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.translatable("commands.dynamicwaters.flowzones.teleport"))));
            source.sendSuccess(() -> line, false);
        }
        return zones.size();
    }

    private static int remove(CommandSourceStack source, int id) {
        if (FlowZones.remove(source.getLevel(), id) == null) {
            source.sendFailure(Component.translatable("commands.dynamicwaters.flowzones.not_found", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.dynamicwaters.flowzones.removed", id), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        final int n = FlowZones.clear(source.getLevel());
        source.sendSuccess(() -> Component.translatable("commands.dynamicwaters.flowzones.cleared", n), true);
        return n;
    }
}
