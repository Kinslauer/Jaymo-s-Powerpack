package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Ender's Delight (MIT, by Ax3dGaming; its art and models kept with the notice in textures/item/CREDITS-delight.txt)
 * remade for the Kitchen: the End's own plants (the creeping void pepper, ethereal saffron, the amberveil mushroom and
 * chorusflame, growing wild on the End's stone and on Enderscape's in the Beneath), what a knife takes from the End's
 * creatures (an enderman's eye, an endermite's crust, a shulker's mollusk), the shulker bowl its dishes are served in,
 * the chorus crate, the end stone stove ({@link DelightHome}), Ender Phasing, and a cook's house out on the End's islands
 * (data/jaymoskitchen/worldgen/structure/end_house). Its dishes are entries of {@code Dish}.
 */
public final class EnderDelight {
	/** A shulker shell cuts into this many bowls (as Ender's Delight's). */
	static final int BOWLS_PER_SHELL = 8;
	/** A raw End bite (the crust, the mollusk, a filet): nutrition, saturation, and the Nausea it gives (ticks). */
	static final int RAW_NUTRITION = 1, RAW_NAUSEA = 200;
	static final float RAW_SATURATION = 0.1F;
	/** A serving of stuffed shulker (from its feast; Good quality). */
	static final int STUFFED_SHULKER_NUTRITION = 11;
	static final float STUFFED_SHULKER_SATURATION = 0.8F;
	/** Ender Phasing: a blow carries you this far (blocks, sideways and up or down), no oftener than every this many ticks. */
	static final double PHASE_SIDEWAYS = 5.0D, PHASE_UP_DOWN = 3.0D;
	static final int PHASE_COOLDOWN = 10;
	/** Ender Phasing: water and rain hurt like an enderman's, this much every this many ticks. */
	static final float WATER_HURT = 1.0F;
	static final int WATER_HURT_EVERY = 10;
	/** Ender Phasing's colour (as Ender's Delight's). */
	static final int PHASING_COLOR = 0x2FADCF;

	/** The End's stone the End plants grow on and the void pepper creeps over (end stone, and Enderscape's). */
	public static final TagKey<Block> END_PLANT_SOIL = TagKey.create(Registries.BLOCK, JaymosKitchen.id("end_plant_soil"));

	public static Item SHULKER_BOWL;
	public static VoidpepperBushBlock VOIDPEPPER_BUSH;
	public static EndBushBlock ETHEREAL_SAFFRON_BUSH, AMBERVEIL_MUSHROOM;
	public static ChorusflameBlock CHORUSFLAME_BUSH;
	public static Block CHORUS_CRATE;
	public static Item VOIDPEPPER, ETHEREAL_SAFFRON, AMBERVEIL, CHORUSFLAME, CHORUS_CRATE_ITEM;
	public static Item MITE_CRUST, ENDER_SHARD, ENDERMAN_SIGHT, SIGHT_FRAGMENTS, SHULKER_MOLLUSK, SHULKER_FILET;
	public static Item CHORUS_PIE_SLICE, STUFFED_SHULKER_BOWL;
	public static Holder<MobEffect> PHASING;

	/** When each phasing creature last phased (game time). */
	private static final Map<LivingEntity, Long> LAST_PHASE = new WeakHashMap<>();

	private EnderDelight() {
	}

	/** The shulker bowl: before ModItems, whose shulker-bowl dishes leave one behind. */
	public static void registerBowl() {
		SHULKER_BOWL = item("shulker_bowl", Item::new, new Item.Properties().stacksTo(16));
	}

	public static void register() {
		PHASING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKitchen.id("phasing"),
			new PhasingEffect(MobEffectCategory.BENEFICIAL, PHASING_COLOR));

		BlockBehaviour.Properties bush = BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH);
		VOIDPEPPER_BUSH = block("voidpepper_bush", VoidpepperBushBlock::new, bush);
		ETHEREAL_SAFFRON_BUSH = block("ethereal_saffron_bush", p -> new EndBushBlock(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D), p),
			BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH));
		AMBERVEIL_MUSHROOM = block("amberveil_mushroom", p -> new EndBushBlock(Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D), p),
			BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH));
		CHORUSFLAME_BUSH = block("chorusflame_bush", ChorusflameBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH));
		CHORUS_CRATE = block("chorus_crate", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F, 3.0F)
			.sound(SoundType.WOOD));

		// the plants are planted from what they give
		VOIDPEPPER = item("voidpepper", p -> new BlockItem(VOIDPEPPER_BUSH, p), plant());
		ETHEREAL_SAFFRON = item("ethereal_saffron", p -> new BlockItem(ETHEREAL_SAFFRON_BUSH, p), plant());
		AMBERVEIL = item("amberveil", p -> new BlockItem(AMBERVEIL_MUSHROOM, p), plant());
		CHORUSFLAME = item("chorusflame", p -> new BlockItem(CHORUSFLAME_BUSH, p), plant());
		CHORUS_CRATE_ITEM = item("chorus_crate", p -> new BlockItem(CHORUS_CRATE, p), new Item.Properties());

		// what a knife takes from the End's creatures, and what's cut from it on the board (Chopping)
		MITE_CRUST = item("mite_crust", Item::new, raw());
		ENDER_SHARD = item("ender_shard", Item::new, new Item.Properties());
		ENDERMAN_SIGHT = item("enderman_sight", Item::new, new Item.Properties());
		SIGHT_FRAGMENTS = item("sight_fragments", Item::new, new Item.Properties());
		SHULKER_MOLLUSK = item("shulker_mollusk", Item::new, raw());
		SHULKER_FILET = item("shulker_filet", Item::new, raw());

		// a slice of the chorus pie (DelightDishes#sliceOf), a serving of the stuffed shulker feast (Feast)
		CHORUS_PIE_SLICE = item("chorus_pie_slice", Item::new, new Item.Properties().food(food(3, 0.3F),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(PHASING, 10 * 20, 0))).build()));
		STUFFED_SHULKER_BOWL = item("stuffed_shulker_bowl", Item::new, new Item.Properties()
			.food(food(STUFFED_SHULKER_NUTRITION, STUFFED_SHULKER_SATURATION)).usingConvertsTo(SHULKER_BOWL).stacksTo(16));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Item item : List.of(VOIDPEPPER, ETHEREAL_SAFFRON, AMBERVEIL, CHORUSFLAME)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.accept(new ItemStack(CHORUS_CRATE_ITEM)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(SHULKER_BOWL, ENDER_SHARD, ENDERMAN_SIGHT, SIGHT_FRAGMENTS)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : List.of(MITE_CRUST, SHULKER_MOLLUSK, SHULKER_FILET, CHORUS_PIE_SLICE, STUFFED_SHULKER_BOWL)) {
				output.accept(new ItemStack(item));
			}
		});

		// an enderman, endermite or shulker killed with a knife gives up its eye, its crust or its mollusk
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!(context.getOptional(LootContextParams.THIS_ENTITY) instanceof LivingEntity victim)
				|| !(context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker)
				|| !attacker.getMainHandItem().is(Delight.KNIVES)
				|| victim.getLootTable().filter(table::is).isEmpty()) {
				return;
			}
			Item cut = knifeCut(victim.getType());
			if (cut != null) {
				drops.add(new ItemStack(cut));
			}
		});

		// Ender Phasing: a blow carries you a few blocks off, as it does an enderman (not while water is what's hurting you)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity.hasEffect(PHASING) && !entity.isInWaterOrRain() && entity.level() instanceof ServerLevel level) {
				long now = level.getGameTime();
				Long last = LAST_PHASE.get(entity);
				if (last == null || now - last >= PHASE_COOLDOWN) {
					double x = entity.getX() + (entity.getRandom().nextDouble() * 2.0D - 1.0D) * PHASE_SIDEWAYS;
					double y = entity.getY() + (entity.getRandom().nextDouble() * 2.0D - 1.0D) * PHASE_UP_DOWN;
					double z = entity.getZ() + (entity.getRandom().nextDouble() * 2.0D - 1.0D) * PHASE_SIDEWAYS;
					if (entity.randomTeleport(x, y, z, true, state -> false)) {
						LAST_PHASE.put(entity, now);
					}
				}
			}
			return true;
		});

		registerWorldgen();
	}

	private static Item knifeCut(EntityType<?> type) {
		if (type == EntityTypes.ENDERMAN) {
			return ENDERMAN_SIGHT;
		}
		if (type == EntityTypes.ENDERMITE) {
			return MITE_CRUST;
		}
		return type == EntityTypes.SHULKER ? SHULKER_MOLLUSK : null;
	}

	/**
	 * The End plants grow wild in the End (Stellarity's islands) and in Enderscape's lands of the Beneath, on bare stone
	 * (data/jaymoskitchen/worldgen/placed_feature).
	 */
	private static void registerWorldgen() {
		Predicate<BiomeSelectionContext> end = BiomeSelectors.foundInTheEnd().and(BiomeSelectors.includeByKey(
			net.minecraft.world.level.biome.Biomes.THE_END).negate())
			.or(context -> context.getBiomeKey().identifier().getNamespace().equals("enderscape"));
		// Last step, not vegetal decoration: Enderscape's own plants reach the same biomes later (through Lithostitched), so
		// in the vegetal step the order differs between biomes that share a dimension and the game stops with a
		// "Feature order cycle". In the last step these plants come after everything else in every biome.
		for (String id : List.of("voidpepper", "ethereal_saffron", "amberveil", "chorusflame")) {
			BiomeModifications.addFeature(end, GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id(id)));
		}
	}

	/** Comfort's bowl: a hot dish served in a shulker bowl warms you as one in a wooden bowl does (Delight#onEat). */
	static boolean shulkerBowl(Item item) {
		return SHULKER_BOWL != null && item == SHULKER_BOWL;
	}

	private static Item.Properties plant() {
		return new Item.Properties().useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);
	}

	private static Item.Properties raw() {
		Consumable nausea = Consumables.defaultFood()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, RAW_NAUSEA, 0))).build();
		return new Item.Properties().food(food(RAW_NUTRITION, RAW_SATURATION), nausea);
	}

	private static FoodProperties food(int nutrition, float saturation) {
		return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return item;
	}

	/** Ender Phasing: water and rain hurt whoever has it, as they hurt an enderman. */
	static final class PhasingEffect extends MobEffect {
		PhasingEffect(MobEffectCategory category, int color) {
			super(category, color);
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
			if (mob.isInWaterOrRain()) {
				mob.hurtServer(level, mob.damageSources().drown(), WATER_HURT);
			}
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
			return tickCount % WATER_HURT_EVERY == 0;
		}
	}
}
