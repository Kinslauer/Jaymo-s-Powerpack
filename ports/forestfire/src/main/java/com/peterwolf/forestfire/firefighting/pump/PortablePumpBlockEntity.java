package com.peterwolf.forestfire.firefighting.pump;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.firefighting.hose.HoseNetwork;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import com.peterwolf.forestfire.sound.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PortablePumpBlockEntity extends BlockEntity {
   private PumpState state = PumpState.OFF;
   private int fuel;
   private float pressure;
   private float heat;
   private int intakeWater;
   private int connectedNozzles;
   private int hoseLength;
   private boolean hasIntake;
   private boolean hasStrainer;
   private float efficiency = 1.0F;
   private int startTicks;
   private int debris;

   public PortablePumpBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlockEntities.PORTABLE_PUMP, pos, state);
      this.fuel = ForestFireConfig.get().pumpFuelCapacity;
   }

   public static void serverTick(Level level, BlockPos pos, BlockState blockState, PortablePumpBlockEntity pump) {
      if (level instanceof ServerLevel serverLevel) {
         pump.tickPump(serverLevel);
      }
   }

   private void tickPump(ServerLevel level) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      HoseNetwork.PumpLinks links = HoseNetwork.scan(level, this.worldPosition);
      this.hasIntake = links.hasIntake;
      this.hoseLength = links.outputLength;
      int openNozzles = HoseNetwork.countOpenNozzlesForPump(level, this.worldPosition);
      this.connectedNozzles = Math.max(links.activeNozzles, openNozzles);
      this.intakeWater = links.availableWater;
      this.hasStrainer = links.hasStrainer;
      float intakeEff = 1.0F;
      HoseConnectionManager.IntakeScanResult autoIntake = HoseConnectionManager.get(level).scanIntake(level, this.worldPosition);
      if (autoIntake.hasIntake()) {
         intakeEff = Math.max(0.2F, autoIntake.efficiency());
      }

      if (this.state == PumpState.DAMAGED || this.state == PumpState.OFF) {
         this.pressure = Math.max(0.0F, this.pressure - 0.05F);
      } else if (this.state == PumpState.STARTING) {
         this.startTicks++;
         if (this.startTicks >= 40) {
            this.state = PumpState.RUNNING;
            level.playSound(null, this.worldPosition, ModSounds.PUMP_IDLE, SoundSource.BLOCKS, 0.6F, 1.0F);
         }
      } else {
         if (level.getGameTime() % 20L == 0L) {
            this.fuel = Math.max(0, this.fuel - cfg.pumpFuelPerSecond);
         }

         if (this.fuel <= 0) {
            this.state = PumpState.OFF;
            this.pressure = 0.0F;
            level.playSound(null, this.worldPosition, ModSounds.PUMP_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
            this.setChanged();
         } else if (this.hasIntake && this.intakeWater > 0) {
            if (!this.hasStrainer) {
               this.debris++;
               if (this.debris > 800) {
                  this.efficiency = Math.max(0.55F, this.efficiency - 3.0E-4F);
               }
            } else if (this.efficiency < 1.0F && level.getGameTime() % 40L == 0L) {
               this.efficiency = Math.min(1.0F, this.efficiency + 0.01F);
               this.debris = Math.max(0, this.debris - 5);
            }

            float load = 1.0F + this.connectedNozzles * 0.18F + this.hoseLength * cfg.pressureLossPerSegment * 0.75F;
            float targetPressure = cfg.basePumpPressure * this.efficiency * intakeEff / Math.max(0.55F, load * 0.55F);
            this.pressure = this.pressure * 0.8F + targetPressure * 0.2F;
            this.heat += load * 0.0015F;
            this.heat = Math.max(0.0F, this.heat - 0.004F);
            if (this.heat > 1.0F) {
               this.state = PumpState.OVERHEATED;
               this.pressure = Math.min(this.pressure, targetPressure * 0.65F);
               this.heat = Math.max(0.85F, this.heat - 0.01F);
            } else if (load > 4.0F) {
               this.state = PumpState.OVERLOADED;
               this.pressure = Math.min(this.pressure, targetPressure * 0.85F);
            } else {
               this.state = PumpState.RUNNING;
            }

            if (links.tankPos != null && level.getBlockEntity(links.tankPos) instanceof PortableWaterTankBlockEntity tank) {
               tank.drain(Math.max(1, this.connectedNozzles * 2));
            }

            if (level.getGameTime() % 80L == 0L && this.state == PumpState.RUNNING) {
               level.playSound(null, this.worldPosition, ModSounds.PUMP_LOAD, SoundSource.BLOCKS, 0.35F, 1.0F);
            }

            this.setChanged();
         } else {
            this.state = PumpState.STARVED;
            this.pressure = Math.max(0.0F, this.pressure - 0.08F);
            if (level.getGameTime() % 40L == 0L) {
               level.playSound(null, this.worldPosition, ModSounds.PUMP_LOW_WATER, SoundSource.BLOCKS, 0.5F, 1.0F);
            }
         }
      }
   }

   public void togglePower(ServerPlayer player) {
      if (this.state == PumpState.DAMAGED) {
         player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_damaged"));
      } else {
         if (this.state != PumpState.OFF && this.state != PumpState.STARVED && this.state != PumpState.OVERHEATED) {
            this.state = PumpState.OFF;
            this.pressure = 0.0F;
            player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_off"));
         } else {
            if (this.fuel <= 0) {
               player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_no_fuel"));
               player.sendSystemMessage(Component.literal("Tip: use Pump Fuel Can on the pump."));
               return;
            }

            if (this.level instanceof ServerLevel serverLevel) {
               HoseNetwork.PumpLinks links = HoseNetwork.scan(serverLevel, this.worldPosition);
               this.hasIntake = links.hasIntake;
               this.intakeWater = links.availableWater;
               this.hasStrainer = links.hasStrainer;
            }

            if (!this.hasIntake || this.intakeWater <= 0) {
               player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_no_intake"));
               player.sendSystemMessage(
                  Component.literal("Tip: place pump next to water OR connect Intake Hose into water. Optional: Intake Strainer in water.")
               );
            }

            this.state = PumpState.STARTING;
            this.startTicks = 0;
            this.heat = Math.max(0.0F, this.heat - 0.3F);
            if (this.level != null) {
               this.level.playSound(null, this.worldPosition, ModSounds.PUMP_START, SoundSource.BLOCKS, 0.8F, 1.0F);
            }

            player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_starting"));
            player.sendSystemMessage(Component.literal("§aPump STARTING → RUNNING. Right-click again to turn OFF."));
         }

         this.setChanged();
      }
   }

   public void addFuel(int amount) {
      this.fuel = Math.min(ForestFireConfig.get().pumpFuelCapacity, this.fuel + amount);
      this.setChanged();
   }

   public PumpState getPumpState() {
      return this.state;
   }

   public float getPressure() {
      return this.pressure;
   }

   public boolean canSupplyNozzle() {
      if (this.fuel <= 0 || this.pressure <= 0.05F) {
         return false;
      } else {
         return this.state == PumpState.OFF || this.state == PumpState.DAMAGED || this.state == PumpState.STARTING
            ? false
            : this.state != PumpState.STARVED || this.intakeWater > 0;
      }
   }

   public float consumeForNozzle(float demand) {
      if (!this.canSupplyNozzle()) {
         return 0.0F;
      } else {
         float factor = this.state == PumpState.OVERHEATED ? 0.65F : (this.state == PumpState.OVERLOADED ? 0.8F : 1.0F);
         float supplied = Math.min(demand, this.pressure) * factor;
         this.pressure = Math.max(0.08F, this.pressure - demand * 0.006F);
         return supplied;
      }
   }

   public List<Component> statusLines() {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.literal("Portable Fire Pump"));
      lines.add(Component.literal("State: " + this.state.name()));
      lines.add(Component.literal(String.format(Locale.ROOT, "Pressure: %.2f", this.pressure)));
      lines.add(Component.literal("Fuel: " + this.fuel + "/" + ForestFireConfig.get().pumpFuelCapacity));
      lines.add(Component.literal("Intake: " + (this.hasIntake ? "OK" : "MISSING") + " water=" + this.intakeWater));
      lines.add(Component.literal("Hose length: " + this.hoseLength + " | Nozzles: " + this.connectedNozzles));
      lines.add(Component.literal(String.format(Locale.ROOT, "Efficiency: %.0f%%  Heat: %.0f%%", this.efficiency * 100.0F, this.heat * 100.0F)));
      if (!this.hasStrainer) {
         lines.add(Component.literal("Warning: no intake strainer — debris risk"));
      }

      if (this.state == PumpState.STARVED) {
         lines.add(Component.literal("Low/no water intake"));
      }

      if (this.state == PumpState.OVERHEATED) {
         lines.add(Component.literal("Pump overheating"));
      }

      return lines;
   }

   protected void saveAdditional(ValueOutput tag) {
      super.saveAdditional(tag);
      tag.putString("state", this.state.name());
      tag.putInt("fuel", this.fuel);
      tag.putFloat("pressure", this.pressure);
      tag.putFloat("heat", this.heat);
      tag.putFloat("efficiency", this.efficiency);
      tag.putInt("debris", this.debris);
      tag.putBoolean("strainer", this.hasStrainer);
   }

   protected void loadAdditional(ValueInput tag) {
      super.loadAdditional(tag);

      try {
         this.state = PumpState.valueOf(tag.getStringOr("state", "OFF"));
      } catch (Exception var3) {
         this.state = PumpState.OFF;
      }

      this.fuel = tag.getIntOr("fuel", ForestFireConfig.get().pumpFuelCapacity);
      this.pressure = tag.getFloatOr("pressure", 0.0F);
      this.heat = tag.getFloatOr("heat", 0.0F);
      this.efficiency = tag.getFloatOr("efficiency", 1.0F);
      this.debris = tag.getIntOr("debris", 0);
      this.hasStrainer = tag.getBooleanOr("strainer", false);
   }

   public static int countNearbyWater(Level level, BlockPos origin) {
      int water = 0;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -3; dx <= 3; dx++) {
         for (int dy = -2; dy <= 1; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
               cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               if (level.getFluidState(cursor).is(Fluids.WATER) || level.getBlockState(cursor).is(Blocks.WATER)) {
                  water++;
               }
            }
         }
      }

      return water;
   }
}
