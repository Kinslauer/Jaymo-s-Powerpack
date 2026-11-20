package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class BurningTreeCollapse {
   public static final String RTF_MOD_ID = "peterwolfs_realistic_tree_felling";
   private static boolean rtfPresent;
   private static boolean rtfReady;
   private static Method forceFallInDirection;
   private static final Map<Long, BurningTreeCollapse.PendingFall> PENDING = new ConcurrentHashMap<>();
   private static final Map<Long, Long> RECENT = new ConcurrentHashMap<>();

   private BurningTreeCollapse() {
   }

   public static void init() {
      rtfPresent = FabricLoader.getInstance().isModLoaded("peterwolfs_realistic_tree_felling");
      if (!rtfPresent) {
         ForestFireMod.LOGGER.info("Realistic Tree Felling not loaded — using simplified burning-tree collapse.");
      } else {
         try {
            Class<?> cutManager = Class.forName("com.peterwolf.realtreefelling.cutting.TreeCutManager");
            forceFallInDirection = cutManager.getMethod("forceFallInDirection", ServerLevel.class, BlockPos.class, Direction.class);
            Class<?> callbacks = Class.forName("com.peterwolf.realtreefelling.api.TreeLandCallbacks");
            Class<?> listenerType = Class.forName("com.peterwolf.realtreefelling.api.TreeLandCallbacks$Listener");
            Method register = callbacks.getMethod("register", listenerType);
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, (proxy, method, args) -> {
               if ("onTreeLanded".equals(method.getName()) && args != null && args.length >= 5) {
                  List<BlockPos> logs = (List<BlockPos>)args[3];
                  List<BlockPos> leaves = (List<BlockPos>)args[4];
                  onTreeLanded((ServerLevel)args[0], (BlockPos)args[1], (Direction)args[2], logs, leaves);
               }

               return null;
            });
            register.invoke(null, listener);
            rtfReady = true;
            ForestFireMod.LOGGER.info("Hooked burning-tree collapse into Realistic Tree Felling.");
         } catch (Exception var5) {
            rtfReady = false;
            ForestFireMod.LOGGER.warn("Realistic Tree Felling present but API hook failed — using simplified collapse. {}", var5.toString());
         }
      }
   }

   public static boolean isRtfActive() {
      return rtfReady;
   }

   public static boolean tryCollapse(ServerLevel level, FireSimulation simulation, BlockPos burningLog, FireCell cell, WindSystem wind, RandomSource random) {
      if (!ForestFireConfig.get().treeCollapseEnabled) {
         return false;
      } else if (!level.getBlockState(burningLog).is(BlockTags.LOGS)) {
         return false;
      } else {
         BlockPos base = findTrunkBase(level, burningLog);
         long baseKey = base.asLong();
         long now = level.getGameTime();
         Long last = RECENT.get(baseKey);
         if (last != null && now - last < 200L) {
            return false;
         } else {
            level.playSound(null, burningLog, SoundEvents.BAMBOO_WOOD_BREAK, SoundSource.BLOCKS, 1.1F, 0.55F);
            level.playSound(null, burningLog, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 0.45F);
            Direction fallDir = wind.cardinal();
            if (wind.speed() < 0.2F && random.nextBoolean()) {
               fallDir = random.nextBoolean() ? fallDir.getClockWise() : fallDir.getCounterClockWise();
            }

            boolean started = false;
            if (rtfReady && forceFallInDirection != null) {
               try {
                  started = forceFallInDirection.invoke(null, level, base, fallDir) instanceof Boolean b && b;
               } catch (Exception var16) {
                  ForestFireMod.LOGGER.debug("RTF forceFall failed: {}", var16.toString());
                  started = false;
               }
            }

            if (started) {
               PENDING.put(baseKey, new BurningTreeCollapse.PendingFall(cell.incidentId, now));
               RECENT.put(baseKey, now);
               clearTreeFireCells(level, simulation, base);
               cell.integrity = 0;
               cell.setStage(BurnStage.CHARRED);
               cell.setHeat(20);
               return true;
            } else {
               started = simpleColumnFall(level, simulation, base, cell, fallDir, random);
               if (started) {
                  RECENT.put(baseKey, now);
               }

               return started;
            }
         }
      }
   }

   public static void tickCleanup(ServerLevel level) {
      long now = level.getGameTime();
      if (now % 100L == 0L) {
         Iterator<Entry<Long, BurningTreeCollapse.PendingFall>> it = PENDING.entrySet().iterator();

         while (it.hasNext()) {
            Entry<Long, BurningTreeCollapse.PendingFall> e = it.next();
            if (now - e.getValue().startedAt > 200L) {
               it.remove();
            }
         }

         Iterator<Entry<Long, Long>> recent = RECENT.entrySet().iterator();

         while (recent.hasNext()) {
            if (now - recent.next().getValue() > 1200L) {
               recent.remove();
            }
         }
      }
   }

   private static void onTreeLanded(ServerLevel level, BlockPos anchor, Direction fallDirection, List<BlockPos> placedLogs, List<BlockPos> placedLeaves) {
      BurningTreeCollapse.PendingFall pending = PENDING.remove(anchor.asLong());
      int incidentId = pending != null ? pending.incidentId : 0;
      FireSimulation simulation = FireSimulation.get(level);

      for (BlockPos log : placedLogs) {
         simulation.tryIgnite(log, incidentId, 70, true);
         simulation.tryIgnite(log.above(), incidentId, 40, false);
         simulation.tryIgnite(log.below(), incidentId, 35, false);

         for (Direction d : Plane.HORIZONTAL) {
            if (level.getRandom().nextFloat() < 0.45F) {
               simulation.tryIgnite(log.relative(d), incidentId, 45, false);
            }
         }
      }

      for (BlockPos leaf : placedLeaves) {
         if (level.getRandom().nextFloat() < 0.55F) {
            simulation.tryIgnite(leaf, incidentId, 50, true);
         }
      }

      int splash = 2 + Math.min(6, placedLogs.size() / 4);

      for (int i = 1; i <= splash; i++) {
         BlockPos ahead = anchor.relative(fallDirection, i + placedLogs.size() / 6);
         BlockPos ground = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, ahead);
         simulation.tryIgnite(ground, incidentId, 40, false);
         simulation.tryIgnite(ground.below(), incidentId, 35, false);
      }

      level.playSound(null, anchor, SoundEvents.GENERIC_BURN, SoundSource.BLOCKS, 0.8F, 0.8F);
      ForestFireMod.LOGGER
         .debug(
            "Burning tree landed @ {} logs={} leaves={} incident=#{}", new Object[]{anchor.toShortString(), placedLogs.size(), placedLeaves.size(), incidentId}
         );
   }

   private static BlockPos findTrunkBase(ServerLevel level, BlockPos pos) {
      MutableBlockPos cursor = pos.mutable();

      for (int i = 0; i < 48 && level.getBlockState(cursor.below()).is(BlockTags.LOGS); i++) {
         cursor.move(Direction.DOWN);
      }

      return cursor.immutable();
   }

   private static void clearTreeFireCells(ServerLevel level, FireSimulation simulation, BlockPos base) {
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dy = 0; dy < 32; dy++) {
         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               cursor.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
               if (level.isLoaded(cursor)) {
                  BlockState state = level.getBlockState(cursor);
                  if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                     simulation.cells().remove(cursor.asLong());
                  }
               }
            }
         }
      }
   }

   private static boolean simpleColumnFall(ServerLevel level, FireSimulation simulation, BlockPos base, FireCell cell, Direction fall, RandomSource random) {
      level.playSound(null, base, SoundEvents.BAMBOO_WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
      boolean any = false;
      MutableBlockPos cursor = base.mutable();

      for (int y = 0; y < 16; y++) {
         cursor.set(base.getX(), base.getY() + y, base.getZ());
         if (!level.isLoaded(cursor)) {
            break;
         }

         BlockState state = level.getBlockState(cursor);
         if (!state.is(BlockTags.LOGS) && y > 0) {
            break;
         }

         if (state.is(BlockTags.LOGS)) {
            any = true;
            BlockPos fallPos = cursor.relative(fall, 1 + y / 2);
            level.removeBlock(cursor, false);
            simulation.cells().remove(cursor.asLong());
            if (!level.getBlockState(fallPos).isAir() && !level.getBlockState(fallPos).canBeReplaced()) {
               if (random.nextFloat() < 0.35F) {
                  level.setBlock(cursor.relative(fall), Blocks.COAL_BLOCK.defaultBlockState(), 3);
                  simulation.tryIgnite(cursor.relative(fall), cell.incidentId, 45, true);
               }
            } else {
               level.setBlock(fallPos, state, 3);
               simulation.tryIgnite(fallPos, cell.incidentId, 55, true);
               simulation.tryIgnite(fallPos.below(), cell.incidentId, 35, false);

               for (Direction d : Plane.HORIZONTAL) {
                  if (random.nextFloat() < 0.4F) {
                     simulation.tryIgnite(fallPos.relative(d), cell.incidentId, 40, false);
                  }
               }
            }
         }
      }

      for (int dx = -2; dx <= 2; dx++) {
         for (int dy = 4; dy <= 14; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
               BlockPos leaf = base.offset(dx, dy, dz);
               if (level.isLoaded(leaf) && level.getBlockState(leaf).is(BlockTags.LEAVES) && random.nextFloat() < 0.25F) {
                  level.removeBlock(leaf, false);
                  BlockPos land = base.relative(fall, 2 + dy / 3).offset(dx, 0, dz);
                  simulation.tryIgnite(land, cell.incidentId, 40, false);
               }
            }
         }
      }

      cell.integrity = 0;
      cell.setStage(BurnStage.CHARRED);
      return any;
   }

   private record PendingFall(int incidentId, long startedAt) {
   }
}
