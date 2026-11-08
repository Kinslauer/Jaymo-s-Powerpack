package com.jaymoskitchen.rats;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mixin.MobAccessor;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Andy: "the rat interactions with the kitchen ... better animations and traps for getting them out" (the Rats mod,
 * LGPL-3.0, by Alexthe666 and its maintainers: Andy chose to use its rat and rat-trap models, textures and sounds; see
 * LICENSE_rats; our own code). Rats ({@link Rat}) come for food left out and unguarded ({@link RatSpawning}) and eat or
 * steal it ({@link FoodStores}). To get rid of them: a snap trap ({@link SnapTrapBlock}), a cage trap that catches them
 * alive ({@link CageTrapBlock}), and cats, which hunt them (and keep them away: no rat turns up near one).
 */
public final class Rats {
	/** Set on a hung carcass the rats have been at: its meat won't keep (see CorpseEntity's cutting). */
	public static final String GNAWED_TAG = "jaymoskitchen.rat_gnawed";

	public static EntityType<Rat> RAT;
	public static Block SNAP_TRAP, CAGE_TRAP;
	public static BlockEntityType<SnapTrapEntity> SNAP_TRAP_ENTITY;
	public static BlockEntityType<CageTrapEntity> CAGE_TRAP_ENTITY;
	public static Item SNAP_TRAP_ITEM, CAGE_TRAP_ITEM, CAGED_RAT, RAT_SPAWN_EGG;
	public static SoundEvent RAT_AMBIENT, RAT_HURT, RAT_DEATH;

	private Rats() {
	}

	public static void register() {
		RAT_AMBIENT = sound("entity.rat.ambient");
		RAT_HURT = sound("entity.rat.hurt");
		RAT_DEATH = sound("entity.rat.death");

		ResourceKey<EntityType<?>> ratKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("rat"));
		RAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, ratKey,
			EntityType.Builder.<Rat>of(Rat::new, MobCategory.AMBIENT)
				.sized(0.45F, 0.35F)
				.eyeHeight(0.25F)
				.clientTrackingRange(8)
				.build(ratKey));
		FabricDefaultAttributeRegistry.register(RAT, Rat.createAttributes());

		SNAP_TRAP = block("snap_trap", SnapTrapBlock::new, BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.WOOD).noOcclusion());
		CAGE_TRAP = block("cage_trap", CageTrapBlock::new, BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion());
		SNAP_TRAP_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("snap_trap"),
			new BlockEntityType<>(SnapTrapEntity::new, Set.of(SNAP_TRAP)));
		CAGE_TRAP_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("cage_trap"),
			new BlockEntityType<>(CageTrapEntity::new, Set.of(CAGE_TRAP)));
		CAGED_RAT = item("caged_rat", p -> new CagedRatItem(CAGE_TRAP, p), new Item.Properties().stacksTo(1));
		SNAP_TRAP_ITEM = item("snap_trap", p -> new BlockItem(SNAP_TRAP, p), new Item.Properties());
		CAGE_TRAP_ITEM = item("cage_trap", p -> new BlockItem(CAGE_TRAP, p), new Item.Properties());
		RAT_SPAWN_EGG = item("rat_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(RAT));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(SNAP_TRAP_ITEM);
			output.accept(CAGE_TRAP_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(RAT_SPAWN_EGG));

		ServerTickEvents.END_SERVER_TICK.register(RatSpawning::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// cats (and ocelots) hunt rats as they do rabbits, tame or not
			if (entity instanceof Cat || entity instanceof Ocelot) {
				var mob = (net.minecraft.world.entity.Mob) entity;
				((MobAccessor) mob).jaymoskitchen$targets().addGoal(1, new NearestAttackableTargetGoal<>(mob, Rat.class, false));
			}
		});
	}

	private static SoundEvent sound(String id) {
		Identifier name = JaymosKitchen.id(id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, name, SoundEvent.createVariableRangeEvent(name));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
