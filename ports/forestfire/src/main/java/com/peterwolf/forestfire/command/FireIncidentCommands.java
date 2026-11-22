package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.mission.MissionScorer;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FireIncidentCommands {
   private FireIncidentCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                      "fireincident"
                                                   )
                                                   .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                                                .then(
                                                   ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("create")
                                                            .executes(ctx -> create(ctx, 3, null)))
                                                         .then(
                                                            Commands.argument("size", IntegerArgumentType.integer(1, 32))
                                                               .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "size"), null))
                                                         ))
                                                      .then(
                                                         Commands.argument("x", IntegerArgumentType.integer())
                                                            .then(
                                                               Commands.argument("y", IntegerArgumentType.integer())
                                                                  .then(
                                                                     Commands.argument("z", IntegerArgumentType.integer())
                                                                        .then(
                                                                           Commands.argument("size", IntegerArgumentType.integer(1, 32))
                                                                              .executes(
                                                                                 ctx -> create(
                                                                                    ctx,
                                                                                    IntegerArgumentType.getInteger(ctx, "size"),
                                                                                    new BlockPos(
                                                                                       IntegerArgumentType.getInteger(ctx, "x"),
                                                                                       IntegerArgumentType.getInteger(ctx, "y"),
                                                                                       IntegerArgumentType.getInteger(ctx, "z")
                                                                                    )
                                                                                 )
                                                                              )
                                                                        )
                                                                  )
                                                            )
                                                      )
                                                ))
                                             .then(Commands.literal("list").executes(FireIncidentCommands::list)))
                                          .then(
                                             Commands.literal("info")
                                                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(FireIncidentCommands::info))
                                          ))
                                       .then(
                                          Commands.literal("contain")
                                             .then(
                                                Commands.argument("id", IntegerArgumentType.integer(1))
                                                   .executes(ctx -> setStatus(ctx, IncidentStatus.CONTAINED))
                                             )
                                       ))
                                    .then(
                                       Commands.literal("extinguish")
                                          .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(FireIncidentCommands::extinguish))
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("douse").executes(ctx -> douse(ctx, 16)))
                                       .then(
                                          Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                             .executes(ctx -> douse(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                                       )
                                 ))
                              .then(
                                 Commands.literal("remove")
                                    .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(FireIncidentCommands::remove))
                              ))
                           .then(
                              Commands.literal("teleport")
                                 .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(FireIncidentCommands::teleport))
                           ))
                        .then(
                           Commands.literal("control")
                              .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> setStatus(ctx, IncidentStatus.CONTROLLED)))
                        ))
                     .then(
                        Commands.literal("close")
                           .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> setStatus(ctx, IncidentStatus.CLOSED)))
                     )
               );
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fireextinguish")
                           .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .executes(ctx -> douse(ctx, 16)))
                     .then(
                        Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                           .executes(ctx -> douse(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                     )
               );
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("gaspozaru")
                           .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .executes(ctx -> douse(ctx, 16)))
                     .then(
                        Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                           .executes(ctx -> douse(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                     )
               );
            }
         );
   }

   private static int create(CommandContext<CommandSourceStack> ctx, int size, BlockPos pos) throws CommandSyntaxException {
      ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
      if (pos == null) {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         pos = player.blockPosition();
      }

      try {
         FireIncident incident = IncidentManager.get(level).createAt(pos, size);
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Created " + incident.summaryLine()), true);
         return incident.id;
      } catch (IllegalStateException var5) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(var5.getMessage()));
         return 0;
      }
   }

   private static int list(CommandContext<CommandSourceStack> ctx) {
      IncidentManager manager = IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel());
      if (manager.all().isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("No incidents."), false);
         return 0;
      } else {
         for (FireIncident incident : manager.all()) {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(incident.summaryLine()), false);
         }

         return manager.all().size();
      }
   }

   private static int info(CommandContext<CommandSourceStack> ctx) {
      int id = IntegerArgumentType.getInteger(ctx, "id");
      Optional<FireIncident> incident = IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel()).get(id);
      if (incident.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Unknown incident #" + id));
         return 0;
      } else {
         for (String line : incident.get().infoLines()) {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(line), false);
         }

         float score = MissionScorer.score(incident.get());
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("Mission grade: " + MissionScorer.grade(score) + " (" + String.format("%.0f", score) + ")"), false);
         return 1;
      }
   }

   private static int setStatus(CommandContext<CommandSourceStack> ctx, IncidentStatus status) {
      int id = IntegerArgumentType.getInteger(ctx, "id");
      boolean ok = IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel()).setStatus(id, status);
      if (!ok) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Unknown incident #" + id));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Incident #" + id + " -> " + status), true);
         return 1;
      }
   }

   private static int extinguish(CommandContext<CommandSourceStack> ctx) {
      int id = IntegerArgumentType.getInteger(ctx, "id");
      Optional<FireIncident> incident = IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel()).get(id);
      if (incident.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Unknown incident #" + id));
         return 0;
      } else {
         IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel()).extinguishAll(id);
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Extinguished incident #" + id), true);
         return 1;
      }
   }

   private static int douse(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
      ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
      BlockPos center;
      if (((CommandSourceStack)ctx.getSource()).getEntity() instanceof ServerPlayer player) {
         center = player.blockPosition();
      } else {
         center = BlockPos.containing(((CommandSourceStack)ctx.getSource()).getPosition());
      }

      FireSimulation.DouseResult result = FireSimulation.get(level).extinguishInRadius(center, radius);
      IncidentManager.get(level).markDirty();
      int n = result.cells();
      int sparks = result.sparks();
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> Component.literal(
               "Doused fire in radius "
                  + radius
                  + " around "
                  + center.toShortString()
                  + " — extinguished "
                  + n
                  + " fire cell(s) (+ vanilla fire blocks) and "
                  + sparks
                  + " airborne spark(s)."
            ),
            true
         );
      return Math.max(1, n + sparks);
   }

   private static int remove(CommandContext<CommandSourceStack> ctx) {
      int id = IntegerArgumentType.getInteger(ctx, "id");
      boolean ok = IncidentManager.get(((CommandSourceStack)ctx.getSource()).getLevel()).remove(id);
      if (!ok) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Unknown incident #" + id));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Removed incident #" + id), true);
         return 1;
      }
   }

   private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      int id = IntegerArgumentType.getInteger(ctx, "id");
      Optional<FireIncident> incident = IncidentManager.get(player.level()).get(id);
      if (incident.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Unknown incident #" + id));
         return 0;
      } else {
         BlockPos pos = incident.get().ignitionPos;
         player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Teleported to " + incident.get().name), false);
         return 1;
      }
   }
}
