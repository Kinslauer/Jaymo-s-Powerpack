// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.baubles.artifacts.worn.mesh.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.function.Function;
import java.util.function.Supplier;

public class ArtifactLayers {

    public static final ModelLayerLocation
            DRINKING_HAT = createLayerLocation("drinking_hat"),
            SNORKEL = createLayerLocation("snorkel"),
            NIGHT_VISION_GOGGLES = createLayerLocation("night_vision_goggles"),
            SUPERSTITIOUS_HAT = createLayerLocation("superstitious_hat"),
            BRIMMED_HAT = createLayerLocation("brimmed_hat"),
            COWBOY_HAT = createLayerLocation("cowboy_hat"),
            ANGLERS_HAT = createLayerLocation("anglers_hat"),

            SCARF = createLayerLocation("scarf"),
            CROSS_NECKLACE = createLayerLocation("cross_necklace"),
            PANIC_NECKLACE = createLayerLocation("panic_necklace"),
            PENDANT = createLayerLocation("pendant"),
            CHARM_OF_SINKING = createLayerLocation("charm_of_sinking"),
            CHARM_OF_SHRINKING = createLayerLocation("charm_of_shrinking"),

            CLOUD_IN_A_BOTTLE = createLayerLocation("cloud_in_a_bottle"),
            OBSIDIAN_SKULL = createLayerLocation("obsidian_skull"),
            ANTIDOTE_VESSEL = createLayerLocation("antidote_vessel"),
            UNIVERSAL_ATTRACTOR = createLayerLocation("universal_attractor"),
            CRYSTAL_HEART = createLayerLocation("crystal_heart"),
            HELIUM_FLAMINGO = createLayerLocation("helium_flamingo"),
            CHORUS_TOTEM = createLayerLocation("chorus_totem"),
            WARP_DRIVE = createLayerLocation("warp_drive"),

            AQUA_DASHERS_SMALL = createLayerLocation("aqua_dashers_small"),
            AQUA_DASHERS_LARGE = createLayerLocation("aqua_dashers_large"),
            BUNNY_HOPPERS = createLayerLocation("bunny_hoppers"),
            KITTY_SLIPPERS = createLayerLocation("kitty_slippers"),
            BOOTS_SMALL = createLayerLocation("boots_small"),
            BOOTS_LARGE = createLayerLocation("boots_large"),
            SNOWSHOES = createLayerLocation("snowshoes"),
            STEADFAST_SPIKES = createLayerLocation("steadfast_spikes"),
            FLIPPERS = createLayerLocation("flippers"),

            WHOOPEE_CUSHION = createLayerLocation("whoopee_cushion");

    public static final ArmsModelSet<ModelLayerLocation>
            CLAWS = createArmsLayerLocation("claws"),
            GLOVE = createArmsLayerLocation("glove"),
            GOLDEN_HOOK = createArmsLayerLocation("golden_hook"),
            POCKET_PISTON = createArmsLayerLocation("pocket_piston"),
            ONION_RING = createArmsLayerLocation("onion_ring"),
            PICKAXE_HEATER = createArmsLayerLocation("pickaxe_heater"),
            WITHERED_BRACELET = createArmsLayerLocation("withered_bracelet");

    public static ModelLayerLocation createLayerLocation(String name) {
        return new ModelLayerLocation(JaymosPowers.id("artifacts_worn/" + name), name);
    }

    public static ArmsModelSet<ModelLayerLocation> createArmsLayerLocation(String name) {
        return new ArmsModelSet<>(
                createLayerLocation(name + "_left_wide"),
                createLayerLocation(name + "_right_wide"),
                createLayerLocation(name + "_left_slim"),
                createLayerLocation(name + "_right_slim")
        );
    }

    private static Supplier<LayerDefinition> layer(Supplier<MeshDefinition> mesh, int textureWidth, int textureHeight) {
        return () -> LayerDefinition.create(mesh.get(), textureWidth, textureHeight);
    }

    public static void register(LayerRegistration registration) {
        registration.register(DRINKING_HAT, layer(HeadMeshDefinitions::createDrinkingHat, 64, 32));
        registration.register(SNORKEL, layer(HeadMeshDefinitions::createSnorkel, 64, 32));
        registration.register(NIGHT_VISION_GOGGLES, layer(HeadMeshDefinitions::createNightVisionGoggles, 32, 32));
        registration.register(SUPERSTITIOUS_HAT, layer(HeadMeshDefinitions::createSuperstitiousHat, 64, 32));
        registration.register(BRIMMED_HAT, layer(() -> HeadMeshDefinitions.createBrimmedHat(CubeListBuilder.create()), 32, 32));
        registration.register(COWBOY_HAT, layer(HeadMeshDefinitions::createCowboyHat, 32, 32));
        registration.register(ANGLERS_HAT, layer(HeadMeshDefinitions::createAnglersHat, 32, 32));

        registration.register(SCARF, layer(BodyMeshDefinitions::createScarf, 64, 32));
        registration.register(CROSS_NECKLACE, layer(BodyMeshDefinitions::createCrossNecklace, 64, 48));
        registration.register(PANIC_NECKLACE, layer(BodyMeshDefinitions::createPanicNecklace, 64, 48));
        registration.register(PENDANT, layer(BodyMeshDefinitions::createPendant, 64, 48));
        registration.register(CHARM_OF_SINKING, layer(BodyMeshDefinitions::createCharmOfSinking, 64, 48));
        registration.register(CHARM_OF_SHRINKING, layer(BodyMeshDefinitions::createCharmOfShrinking, 64, 48));

        registration.register(CLOUD_IN_A_BOTTLE, layer(BeltMeshDefinitions::createCloudInABottle, 32, 32));
        registration.register(OBSIDIAN_SKULL, layer(BeltMeshDefinitions::createObsidianSkull, 32, 32));
        registration.register(ANTIDOTE_VESSEL, layer(BeltMeshDefinitions::createAntidoteVessel, 32, 32));
        registration.register(UNIVERSAL_ATTRACTOR, layer(BeltMeshDefinitions::createUniversalAttractor, 32, 32));
        registration.register(CRYSTAL_HEART, layer(BeltMeshDefinitions::createCrystalHeart, 32, 32));
        registration.register(HELIUM_FLAMINGO, layer(BeltMeshDefinitions::createHeliumFlamingo, 64, 64));
        registration.register(CHORUS_TOTEM, layer(BeltMeshDefinitions::createChorusTotem, 32, 32));
        registration.register(WARP_DRIVE, layer(BeltMeshDefinitions::createWarpDrive, 32, 32));

        registration.registerArms(CLAWS, ArmsMeshDefinitions::createClaws, 32, 16);
        registration.registerArms(GLOVE, ArmsMeshDefinitions::createGlove, 32, 32);
        registration.registerArms(GOLDEN_HOOK, ArmsMeshDefinitions::createGoldenHook, 64, 32);
        registration.registerArms(POCKET_PISTON, ArmsMeshDefinitions::createPocketPiston, 32, 16);
        registration.registerArms(ONION_RING, b -> ArmsMeshDefinitions.createBracelet(b, 4, -4), 32, 32);
        registration.registerArms(PICKAXE_HEATER, ArmsMeshDefinitions::createPickaxeHeater, 64, 32);
        registration.registerArms(WITHERED_BRACELET, b -> ArmsMeshDefinitions.createBracelet(b, 3, -5), 32, 32);

        registration.register(AQUA_DASHERS_SMALL, layer(() -> LegsMeshDefinitions.createAquaDashers(0.5F), 32, 32));
        registration.register(AQUA_DASHERS_LARGE, layer(() -> LegsMeshDefinitions.createAquaDashers(1.25F), 32, 32));
        registration.register(BUNNY_HOPPERS, layer(LegsMeshDefinitions::createBunnyHoppers, 64, 32));
        registration.register(KITTY_SLIPPERS, layer(LegsMeshDefinitions::createKittySlippers, 64, 32));
        registration.register(BOOTS_SMALL, layer(() -> LegsMeshDefinitions.createBoots(0.5F), 32, 32));
        registration.register(BOOTS_LARGE, layer(() -> LegsMeshDefinitions.createBoots(1.25F), 32, 32));
        registration.register(SNOWSHOES, layer(LegsMeshDefinitions::createSnowshoes, 64, 64));
        registration.register(STEADFAST_SPIKES, layer(LegsMeshDefinitions::createSteadfastSpikes, 64, 32));
        registration.register(FLIPPERS, layer(LegsMeshDefinitions::createFlippers, 64, 64));

        registration.register(WHOOPEE_CUSHION, layer(HeadMeshDefinitions::createWhoopeeCushion, 32, 16));
    }

    @FunctionalInterface
    public interface LayerRegistration {

        void register(ModelLayerLocation layerLocation, Supplier<LayerDefinition> supplier);

        default void registerArms(ArmsModelSet<ModelLayerLocation> layerLocations, Function<Boolean, MeshDefinition> factory, int textureWidth, int textureHeight) {
            for (PlayerModelType modelType : PlayerModelType.values()) {
                for (HumanoidArm arm : HumanoidArm.values()) {
                    register(layerLocations.get(arm, modelType), layer(() -> ArmsMeshDefinitions.retainArm(factory.apply(modelType == PlayerModelType.SLIM), arm), textureWidth, textureHeight));
                }
            }
        }
    }
}
