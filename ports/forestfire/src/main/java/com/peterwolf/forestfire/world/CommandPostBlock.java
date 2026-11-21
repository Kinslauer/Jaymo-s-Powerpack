package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.FireSector;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentMarker;
import com.peterwolf.forestfire.fire.incident.IncidentMarkerType;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.mission.MissionScorer;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CommandPostBlock extends BaseEntityBlock {
   private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

   public CommandPostBlock(Properties properties) {
      super(properties);
   }


   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new CommandPostBlockEntity(pos, state);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
         IncidentManager manager = IncidentManager.get(serverLevel);
         CommandPostBlockEntity post = level.getBlockEntity(pos) instanceof CommandPostBlockEntity c ? c : null;
         if (player.isShiftKeyDown() && post != null) {
            Optional<FireIncident> nearest = manager.openIncidents().stream().min(Comparator.comparingDouble(i -> i.ignitionPos.distSqr(pos)));
            if (nearest.isPresent()) {
               post.setIncidentId(nearest.get().id);
               nearest.get().markers.removeIf(m -> m.type() == IncidentMarkerType.COMMAND_POST);
               nearest.get().markers.add(new IncidentMarker(IncidentMarkerType.COMMAND_POST, pos, "ICP"));
               manager.markDirty();
               serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.command_bound", new Object[]{nearest.get().name}));
            } else {
               serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.no_incident"));
            }

            return InteractionResult.CONSUME;
         } else {
            int incidentId = post != null ? post.getIncidentId() : -1;
            Optional<FireIncident> incident = incidentId > 0
               ? manager.get(incidentId)
               : manager.openIncidents().stream().min(Comparator.comparingDouble(i -> i.ignitionPos.distSqr(pos)));
            if (incident.isEmpty()) {
               serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.no_incident"));
               return InteractionResult.CONSUME;
            } else {
               FireIncident inc = incident.get();
               serverPlayer.sendSystemMessage(Component.literal("§6=== INCIDENT COMMAND POST ==="));

               for (String line : inc.infoLines()) {
                  serverPlayer.sendSystemMessage(Component.literal(line));
               }

               serverPlayer.sendSystemMessage(Component.literal("Sectors:"));

               for (FireSector sector : inc.sectors) {
                  serverPlayer.sendSystemMessage(
                     Component.literal(
                        "  "
                           + sector.name
                           + " — "
                           + (sector.objective.isEmpty() ? "no objective" : sector.objective)
                           + " ["
                           + sector.status
                           + "] cont="
                           + String.format("%.0f%%", sector.containment)
                     )
                  );
               }

               serverPlayer.sendSystemMessage(Component.literal("Markers: " + inc.markers.size()));
               serverPlayer.sendSystemMessage(
                  Component.literal(
                     "Score preview: " + String.format("%.0f", MissionScorer.score(inc)) + " (" + MissionScorer.grade(MissionScorer.score(inc)) + ")"
                  )
               );
               serverPlayer.sendSystemMessage(Component.literal("§7Sneak-use to bind nearest incident. Use /fireincident contain|extinguish."));
               if (inc.status == IncidentStatus.REPORTED) {
                  inc.status = IncidentStatus.RESPONDING;
                  manager.markDirty();
               }

               inc.addParticipant(serverPlayer.getUUID());
               return InteractionResult.CONSUME;
            }
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
