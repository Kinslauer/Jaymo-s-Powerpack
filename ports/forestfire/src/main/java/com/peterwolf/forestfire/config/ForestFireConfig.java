package com.peterwolf.forestfire.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ForestFireConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static ForestFireConfig.Data data = new ForestFireConfig.Data();

   private ForestFireConfig() {
   }

   public static Path path() {
      return FabricLoader.getInstance().getConfigDir().resolve("peterwolfs_forestfire.json");
   }

   public static void load() {
      Path configPath = path();

      try {
         if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
               ForestFireConfig.Data loaded = (ForestFireConfig.Data)GSON.fromJson(reader, ForestFireConfig.Data.class);
               if (loaded != null) {
                  data = loaded;
               }
            }
         }

         data.sanitize();
         save();
         ForestFireMod.LOGGER.info("Loaded config from {}", configPath);
      } catch (RuntimeException | IOException var7) {
         ForestFireMod.LOGGER.error("Failed to load config, using defaults", var7);
         data = new ForestFireConfig.Data();

         try {
            data.sanitize();
            save();
         } catch (RuntimeException var4) {
         }
      }
   }

   public static void save() {
      Path configPath = path();

      try {
         Files.createDirectories(configPath.getParent());

         try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(data, writer);
         }
      } catch (IOException var6) {
         ForestFireMod.LOGGER.error("Failed to save config", var6);
      }
   }

   public static ForestFireConfig.Data get() {
      return data;
   }

   public static void resetToDefaultsForTests() {
      data = new ForestFireConfig.Data();
      data.sanitize();
   }

   public static final class Data {
      public boolean windEnabled = true;
      public boolean dynamicWind = true;
      public float configuredWindSpeed = 0.25F;
      public float configuredWindYawDegrees = 0.0F;
      public float minimumWindSpeed = 0.0F;
      public float maximumWindSpeed = 1.0F;
      public int windChangeIntervalTicks = 6000;
      public float windChangeStrength = 0.15F;
      public float windDirectionChangeDegrees = 21.0F;
      public float windSmoothingFactor = 0.02F;
      public int maxBurningBlocksPerWorld = 8000;
      public int fireTickInterval = 5;
      public int maxSpreadChecksPerTick = 400;
      public int maxEmbersPerTick = 12;
      public int maxActiveIncidents = 8;
      public int chunkUnloadRetentionRadius = 2;
      public int maxFireChunkTickets = 128;
      public boolean replaceVanillaFireInIncidents = true;
      public boolean allowNaturalIgnition = true;
      public float naturalIgnitionChancePerMinute = 0.02F;
      public boolean sparksEnabled = true;
      public int sparkPoolMultiplier = 10;
      public float emberSpotFireChance = 0.12F;
      public float sparkHeatBonus = 0.55F;
      public float surfaceSpreadMultiplier = 1.0F;
      public float crownSpreadMultiplier = 1.35F;
      public float radiantHeatMultiplier = 1.0F;
      public float uphillSpreadBonus = 0.35F;
      public float downhillSpreadPenalty = 0.25F;
      public boolean firestormEnabled = true;
      public int firestormMinBurningBlocks = 20;
      public float firestormSpreadMultiplierMax = 2.8F;
      public float firestormThresholdSpreadBonus = 0.35F;
      public float firestormWindBoostMax = 0.65F;
      public float firestormThresholdWindBonus = 0.12F;
      public float firestormSparkMultiplier = 2.2F;
      public float firestormSelfHeat = 0.8F;
      public int wetnessDurationTicks = 2400;
      public float waterHeatReduction = 22.0F;
      public float waterMoistureGain = 28.0F;
      public float pumpNozzleExtinguishMultiplier = 1.65F;
      public int maxHoseLength = 48;
      public int nozzleFreeHoseBlocks = 16;
      public float pressureLossPerSegment = 0.02F;
      public float basePumpPressure = 1.0F;
      public boolean nozzleHudEnabled = true;
      public boolean automaticHoseConsumesItems = true;
      public boolean creativeModeInfiniteHose = true;
      public boolean automaticHoseRetraction = true;
      public boolean returnFullHoseLength = true;
      public boolean hoseDamageLossEnabled = false;
      public int maximumIntakeHoseLength = 24;
      public int maximumIntakeVerticalLift = 6;
      public boolean intakeLengthPressureLoss = true;
      public boolean singleAttackLinePerPump = false;
      public int hoseRenderDistance = 96;
      public float hoseEndpointMoveThreshold = 0.25F;
      public int smallTankCapacity = 2000;
      public int mediumTankCapacity = 6000;
      public int largeTankCapacity = 16000;
      public int backpackCapacity = 120;
      public int pumpFuelCapacity = 1000;
      public int pumpFuelPerSecond = 2;
      public boolean smokeEnabled = true;
      public int maxSmokeParticles = 200;
      public float smokeDamagePerSecond = 0.5F;
      public boolean smokeCausesBlindness = true;
      public boolean smokeCausesWeakness = true;
      public boolean heatExposureEnabled = true;
      public float heatDamageMultiplier = 1.0F;
      public boolean peatFireEnabled = false;
      public float peatSpreadChance = 0.01F;
      public boolean treeCollapseEnabled = true;
      public float treeCollapseChance = 0.06F;
      public boolean rescueMissionsEnabled = true;
      public boolean villagerPanicEnabled = true;
      public float scoreStructuresSavedWeight = 25.0F;
      public float scoreLivesSavedWeight = 40.0F;
      public float scoreContainmentSpeedWeight = 15.0F;
      public float scoreWaterEfficiencyWeight = 10.0F;
      public float scoreTeamworkWeight = 10.0F;
      public ForestFireConfig.Data.FirefightingAircraft firefightingAircraft = new ForestFireConfig.Data.FirefightingAircraft();
      public boolean debugLogging = false;
      public boolean showDedicationOnTitle = true;

      private void sanitize() {
         if (this.firefightingAircraft == null) {
            this.firefightingAircraft = new ForestFireConfig.Data.FirefightingAircraft();
         }

         this.firefightingAircraft.sanitize();
         this.minimumWindSpeed = finiteOr(this.minimumWindSpeed, 0.0F);
         this.maximumWindSpeed = finiteOr(this.maximumWindSpeed, 1.0F);
         this.minimumWindSpeed = clamp(this.minimumWindSpeed, 0.0F, 4.0F);
         this.maximumWindSpeed = clamp(this.maximumWindSpeed, this.minimumWindSpeed, 4.0F);
         this.configuredWindSpeed = clamp(finiteOr(this.configuredWindSpeed, 0.25F), this.minimumWindSpeed, this.maximumWindSpeed);
         this.configuredWindYawDegrees = wrapDegrees(finiteOr(this.configuredWindYawDegrees, 0.0F));
         this.windChangeIntervalTicks = Math.max(200, this.windChangeIntervalTicks);
         this.windChangeStrength = clamp(finiteOr(this.windChangeStrength, 0.15F), 0.0F, 4.0F);
         this.windDirectionChangeDegrees = clamp(finiteOr(this.windDirectionChangeDegrees, 21.0F), 0.0F, 180.0F);
         this.windSmoothingFactor = clamp(finiteOr(this.windSmoothingFactor, 0.02F), 0.001F, 1.0F);
         this.chunkUnloadRetentionRadius = Math.max(0, Math.min(4, this.chunkUnloadRetentionRadius));
         this.maxFireChunkTickets = Math.max(1, Math.min(512, this.maxFireChunkTickets));
      }

      private static float finiteOr(float value, float fallback) {
         return Float.isFinite(value) ? value : fallback;
      }

      private static float clamp(float value, float minimum, float maximum) {
         return Math.max(minimum, Math.min(maximum, value));
      }

      private static float wrapDegrees(float degrees) {
         float wrapped = degrees % 360.0F;
         if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
         }

         if (wrapped < -180.0F) {
            wrapped += 360.0F;
         }

         return wrapped;
      }

      public static final class FirefightingAircraft {
         public boolean enabled = true;
         public int tankCapacity = 12000;
         public int waterIntakeRatePerTick = 30;
         public int waterReleaseRatePerTick = 220;
         public double maximumWaterDistanceBlocks = 0.15;
         public double maximumNozzleSubmersionBlocks = 1.25;
         public double minimumScoopingSpeed = 0.25;
         public double maximumScoopingSpeed = 0.85;
         public float maximumScoopingRollDegrees = 20.0F;
         public float maximumScoopingPitchDegrees = 15.0F;
         public float waterWeightPhysicsMultiplier = 1.0F;
         public float maxCargoMassBonus = 0.55F;
         public int wetnessDurationTicks = 6000;
         public int maximumSuppressionOperationsPerTick = 1800;
         public boolean enableSubsystemDamage = false;
         public boolean autoRetractHoseAtUnsafeSpeed = true;
         public double autoRetractSpeed = 1.05;
         public int hoseNotOverWaterTimeoutTicks = 40;
         public float dropStrength = 5.5F;
         public float dropBaseRadius = 6.5F;
         public float dropRadiusPerAltitude = 0.22F;
         public float aerialSuppressionMultiplier = 2.4F;
         public int maxActiveWaterPayloads = 64;
         public int waterSyncThreshold = 25;
         public boolean finiteWaterExtraction = false;
         public boolean debugMetrics = false;

         void sanitize() {
            this.tankCapacity = Math.max(100, Math.min(500000, this.tankCapacity));
            this.waterIntakeRatePerTick = Math.max(1, Math.min(5000, this.waterIntakeRatePerTick));
            this.waterReleaseRatePerTick = Math.max(1, Math.min(10000, this.waterReleaseRatePerTick));
            this.maximumWaterDistanceBlocks = clampD(this.maximumWaterDistanceBlocks, 0.0, 4.0);
            this.maximumNozzleSubmersionBlocks = clampD(this.maximumNozzleSubmersionBlocks, 0.25, 4.0);
            this.minimumScoopingSpeed = clampD(this.minimumScoopingSpeed, 0.0, 2.0);
            this.maximumScoopingSpeed = clampD(this.maximumScoopingSpeed, this.minimumScoopingSpeed, 4.0);
            this.maximumScoopingRollDegrees = clamp(this.maximumScoopingRollDegrees, 5.0F, 80.0F);
            this.maximumScoopingPitchDegrees = clamp(this.maximumScoopingPitchDegrees, 5.0F, 60.0F);
            this.waterWeightPhysicsMultiplier = clamp(this.waterWeightPhysicsMultiplier, 0.0F, 3.0F);
            this.maxCargoMassBonus = clamp(this.maxCargoMassBonus, 0.0F, 2.0F);
            this.wetnessDurationTicks = Math.max(20, Math.min(72000, this.wetnessDurationTicks));
            this.maximumSuppressionOperationsPerTick = Math.max(10, Math.min(5000, this.maximumSuppressionOperationsPerTick));
            this.autoRetractSpeed = clampD(this.autoRetractSpeed, this.maximumScoopingSpeed, 5.0);
            this.hoseNotOverWaterTimeoutTicks = Math.max(5, Math.min(200, this.hoseNotOverWaterTimeoutTicks));
            this.dropStrength = clamp(this.dropStrength, 0.1F, 20.0F);
            this.dropBaseRadius = clamp(this.dropBaseRadius, 0.5F, 16.0F);
            this.dropRadiusPerAltitude = clamp(this.dropRadiusPerAltitude, 0.0F, 1.0F);
            this.aerialSuppressionMultiplier = clamp(this.aerialSuppressionMultiplier, 0.25F, 8.0F);
            this.maxActiveWaterPayloads = Math.max(4, Math.min(256, this.maxActiveWaterPayloads));
            this.waterSyncThreshold = Math.max(1, Math.min(1000, this.waterSyncThreshold));
         }

         private static float clamp(float value, float minimum, float maximum) {
            return Math.max(minimum, Math.min(maximum, value));
         }

         private static double clampD(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
         }
      }
   }
}
