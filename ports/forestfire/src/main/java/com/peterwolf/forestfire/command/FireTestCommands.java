package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.hose.HoseConnection;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.item.HoseRollItem;
import com.peterwolf.forestfire.item.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class FireTestCommands {
   private FireTestCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                    "firetest"
                                 )
                                 .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("grid")
                                       .executes(ctx -> grid(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), 5)))
                                    .then(
                                       Commands.argument("size", IntegerArgumentType.integer(1, 24))
                                          .executes(
                                             ctx -> grid(
                                                ((CommandSourceStack)ctx.getSource()).getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "size")
                                             )
                                          )
                                    )
                              ))
                           .then(
                              Commands.literal("stats")
                                 .executes(
                                    ctx -> {
                                       ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                                       FireSimulation simulation = FireSimulation.get(level);
                                       int cells = simulation.cells().size();
                                       int activeCells = simulation.activeCellCount();
                                       int chunkTickets = simulation.activeFireChunkTicketCount();
                                       ForestFireConfig.Data cfg = ForestFireConfig.get();
                                       int incidents = IncidentManager.get(level).openIncidents().size();
                                       ((CommandSourceStack)ctx.getSource())
                                          .sendSuccess(
                                             () -> Component.literal(
                                                "Fire cells tracked="
                                                   + cells
                                                   + " active="
                                                   + activeCells
                                                   + "/"
                                                   + cfg.maxBurningBlocksPerWorld
                                                   + " chunk tickets="
                                                   + chunkTickets
                                                   + "/"
                                                   + cfg.maxFireChunkTickets
                                                   + " open incidents="
                                                   + incidents
                                             ),
                                             false
                                          );
                                       return cells;
                                    }
                                 )
                           ))
                        .then(Commands.literal("clear").executes(ctx -> {
                           ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                           IncidentManager manager = IncidentManager.get(level);

                           for (FireIncident incident : manager.openIncidents().toArray(FireIncident[]::new)) {
                              manager.remove(incident.id);
                           }

                           FireSimulation simulation = FireSimulation.get(level);
                           simulation.cells().clear();
                           simulation.clearAllSparks();
                           manager.markDirty();
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Cleared all fire simulation data."), true);
                           return 1;
                        })))
                     .then(
                        Commands.literal("hoses")
                           .executes(
                              ctx -> {
                                 ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                                 HoseConnectionManager mgr = HoseConnectionManager.get(level);
                                 int n = 0;

                                 for (HoseConnection c : mgr.all()) {
                                    n++;
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(
                                          () -> Component.literal(
                                             c.type
                                                + " "
                                                + c.connectionId.toString().substring(0, 8)
                                                + " src="
                                                + c.sourcePos.toShortString()
                                                + " tgt="
                                                + c.targetPos.toShortString()
                                                + " len="
                                                + c.deployedLength
                                                + " path="
                                                + String.format("%.1f", c.currentPathLength)
                                                + " "
                                                + (c.connected ? "OK" : "DOWN")
                                          ),
                                          false
                                       );
                                 }

                                 int total = n;
                                 ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Automatic hose connections: " + total), false);
                                 return total;
                              }
                           )
                     ))
                  .then(Commands.literal("hosekit").executes(ctx -> {
                     ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                     giveHoseTestKit(player);
                     ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Hose test kit given."), false);
                     return 1;
                  }))
            )
         );
   }

   private static void giveHoseTestKit(ServerPlayer player) {
      Inventory inv = player.getInventory();
      inv.add(new ItemStack(ModItems.PORTABLE_PUMP));
      inv.add(new ItemStack(ModItems.FIRE_HOSE_NOZZLE));
      inv.add(new ItemStack(ModItems.HOSE_CONNECTOR));
      inv.add(new ItemStack(ModItems.INTAKE_STRAINER));
      inv.add(new ItemStack(ModItems.PUMP_FUEL_CAN, 4));
      ItemStack large = new ItemStack(ModItems.HOSE_ROLL_LARGE);
      HoseRollItem.setRemaining(large, 64);
      inv.add(large);
      ItemStack std = new ItemStack(ModItems.HOSE_ROLL_STANDARD);
      HoseRollItem.setRemaining(std, 32);
      inv.add(std);
   }

   private static int grid(ServerPlayer player, int size) {
      ServerLevel level = player.level();
      BlockPos origin = player.blockPosition();
      FireIncident incident = IncidentManager.get(level).createAt(origin, size);
      FireSimulation sim = FireSimulation.get(level);
      int ignited = 0;

      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            BlockPos pos = origin.offset(x - size / 2, 0, z - size / 2);
            if (sim.tryIgnite(pos, incident.id, 70, true)) {
               ignited++;
            }

            if (sim.tryIgnite(pos.above(), incident.id, 60, true)) {
               ignited++;
            }
         }
      }

      player.sendSystemMessage(Component.literal("Test grid size=" + size + " ignited=" + ignited + " incident=#" + incident.id));
      return ignited;
   }
}
