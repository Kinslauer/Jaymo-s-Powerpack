package com.AdamNew.dynamicwaters.command;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.WatershedPath;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.Vec3;

public class LocateRiverTerrainCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("locate_river_terrain")
            .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                Vec3 pos = source.getPosition();

                if (!HydrologyManager.riversIn(source.getLevel())) {
                    source.sendFailure(Component.literal("Mountain rivers only run in the Overworld."));
                    return 0;
                }

                source.sendSystemMessage(Component.literal("Locating nearest mountain river..."));

                HydrologyManager.WatershedContext mountainCtx = HydrologyManager.evaluateWatershedContext(
                    pos.x, pos.z, null, null, null, true);

                if (mountainCtx.bestPath == null) {
                    source.sendFailure(Component.literal("Could not find a mountain river nearby. Try exploring more terrain first."));
                    return 0;
                }

                WatershedPath path = mountainCtx.bestPath;
                int nx = (int) path.segmentsX[0];
                int ny = path.segmentsY[0];
                int nz = (int) path.segmentsZ[0];

                int distance = (int) Math.sqrt(pos.distanceToSqr(nx, pos.y, nz));

                Component message = Component.literal("The nearest mountain river drain is at [X: " + nx + ", Y: " + ny + ", Z: " + nz + "] (" + distance + " blocks away)")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand("/tp " + nx + " " + (ny + 5) + " " + nz))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport!")))
                                .withColor(net.minecraft.ChatFormatting.GREEN));

                source.sendSuccess(() -> message, true);
                return 1;
            }));
    }
}
