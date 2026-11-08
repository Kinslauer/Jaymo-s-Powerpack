package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (0.21.64, Andy: "new tuna mob please. skipjack?") The skipjack tuna: a fast, schooling fish of the open sea, dark
 * blue-violet above, silver below with dark stripes along its belly. Schools of {@link #SCHOOL_MIN}-{@link #SCHOOL_MAX}
 * swim in every ocean but the frozen ones; it's too big for a bucket, and takes only a metal rod's hook
 * ({@link FishBites}). Our own model and texture.
 */
public class Skipjack extends AbstractSchoolingFish {
    /** Spawn weight in an ocean (cod is 15 in most), and how many come together. */
    static final int SPAWN_WEIGHT = 6, SCHOOL_MIN = 3, SCHOOL_MAX = 6;
    /** Health and swim speed (a cod has 3 and 0.7). */
    static final double HEALTH = 8.0, SPEED = 1.0;
    /** Its size (blocks). */
    static final float WIDTH = 0.8F, HEIGHT = 0.5F;

    public static EntityType<Skipjack> SKIPJACK;
    public static Item SKIPJACK_SPAWN_EGG;

    public Skipjack(EntityType<? extends Skipjack> type, Level level) {
        super(type, level);
    }

    static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("skipjack"));
        SKIPJACK = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(Skipjack::new, MobCategory.WATER_AMBIENT).sized(WIDTH, HEIGHT).eyeHeight(HEIGHT * 0.6F)
                        .clientTrackingRange(4).build(key));
        FabricDefaultAttributeRegistry.register(SKIPJACK, createSkipjackAttributes());
        SpawnPlacements.register(SKIPJACK, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WaterAnimal::checkSurfaceWaterAnimalSpawnRules);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_OCEAN).and(ctx -> !ctx.getBiomeKey().identifier().getPath().contains("frozen")),
                MobCategory.WATER_AMBIENT, SKIPJACK, SPAWN_WEIGHT, SCHOOL_MIN, SCHOOL_MAX);
        ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosPowers.id("skipjack_spawn_egg"));
        SKIPJACK_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(SKIPJACK)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SKIPJACK_SPAWN_EGG));
    }

    static AttributeSupplier.Builder createSkipjackAttributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
    }

    @Override
    public int getMaxSchoolSize() {
        return SCHOOL_MAX * 2;
    }

    /** Too big for a bucket. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }
}
