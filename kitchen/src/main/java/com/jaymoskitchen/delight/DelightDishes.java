package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.33) The rest of Farmer's Delight's food (MIT, by vectorwing; see LICENSE_farmersdelight), cooked through the
 * Kitchen's own minigames: its dishes are entries of {@link Dish} (soups, sauces, sandwiches, rolls, pies...), and this
 * class makes what isn't one: the slices cut from a pie, a cake, a pumpkin or a kelp roll, the milk bottle, a raw ham
 * (from a pig or hoglin killed with a knife), and the gleaming salad served from its feast. It also makes the pies
 * themselves ({@link PieBlock}) and carries dog food and horse feed to tamed wolves and horses.
 */
public final class DelightDishes {
	/** Chance a pig killed with a knife drops a ham (a hoglin always does). */
	static final float PIG_HAM_CHANCE = 0.5F;
	/** How long dog food and horse feed last on the animal: five minutes. A Perfect dog food lasts twice as long. */
	static final int FEED_TICKS = 6000;

	public static Item MILK_BOTTLE, HAM, PUMPKIN_SLICE, CAKE_SLICE, PUMPKIN_PIE_SLICE, APPLE_PIE_SLICE, SWEET_BERRY_CHEESECAKE_SLICE,
		CHOCOLATE_PIE_SLICE, KELP_ROLL_SLICE, GLEAMING_SALAD, HORSE_FEED;

	/** The whole pies (from ModItems, for the pie dishes), for the creative tab. */
	private static final List<Item> PIES = new ArrayList<>();

	private DelightDishes() {
	}

	public static void register() {
		Consumable pie = Consumables.defaultFood()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0, false, false))).build();
		Consumable cake = Consumables.defaultFood()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0, false, false))).build();

		// a bottle of milk: four from a bucket; drinking it clears what's ailing you, like the bucket
		MILK_BOTTLE = item("milk_bottle", new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().onConsume(ClearAllStatusEffectsConsumeEffect.INSTANCE).build()));
		HAM = food("ham", 5, 0.3F, null, null);
		PUMPKIN_SLICE = food("pumpkin_slice", 3, 0.3F, null, null);
		CAKE_SLICE = food("cake_slice", 2, 0.1F, null, cake);
		PUMPKIN_PIE_SLICE = food("pumpkin_pie_slice", 3, 0.3F, null, pie);
		APPLE_PIE_SLICE = food("apple_pie_slice", 3, 0.3F, null, pie);
		SWEET_BERRY_CHEESECAKE_SLICE = food("sweet_berry_cheesecake_slice", 3, 0.3F, null, pie);
		CHOCOLATE_PIE_SLICE = food("chocolate_pie_slice", 3, 0.3F, null, pie);
		KELP_ROLL_SLICE = food("kelp_roll_slice", 6, 0.5F, null, Consumables.defaultFood().consumeSeconds(0.8F).build());
		// served from the gleaming salad feast
		GLEAMING_SALAD = food("gleaming_salad", 11, 0.75F, Items.BOWL, null);
		HORSE_FEED = item("horse_feed", new Item.Properties());

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : PIES) {
				output.accept(new ItemStack(item));
			}
			for (Item item : List.of(MILK_BOTTLE, HAM, PUMPKIN_SLICE, CAKE_SLICE, PUMPKIN_PIE_SLICE, APPLE_PIE_SLICE, SWEET_BERRY_CHEESECAKE_SLICE,
				CHOCOLATE_PIE_SLICE, KELP_ROLL_SLICE, GLEAMING_SALAD, HORSE_FEED)) {
				output.accept(new ItemStack(item));
			}
		});

		// a pig or hoglin killed with a knife drops a ham, a burning one a smoked ham
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!(context.getOptional(LootContextParams.THIS_ENTITY) instanceof LivingEntity victim)
				|| !(victim.getType() == EntityTypes.PIG || victim.getType() == EntityTypes.HOGLIN)
				|| !(context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker)
				|| !attacker.getMainHandItem().is(Delight.KNIVES)
				|| victim.getLootTable().filter(table::is).isEmpty()) {
				return;
			}
			if (victim.getType() == EntityTypes.PIG && context.getRandom().nextFloat() >= PIG_HAM_CHANCE) {
				return;
			}
			if (victim.isOnFire()) {
				ItemStack smoked = new ItemStack(ModItems.dish(Dish.SMOKED_HAM));
				Quality.GOOD.apply(smoked, Dish.SMOKED_HAM);
				drops.add(smoked);
			} else {
				drops.add(new ItemStack(HAM));
			}
		});

		UseEntityCallback.EVENT.register(DelightDishes::feedAnimal);
	}

	/**
	 * A pie as a block to set down (from ModItems, for a pie dish): what its item is, and its block, which cuts into the
	 * slice item of the same name.
	 */
	public static Item pie(Dish dish, ResourceKey<Item> key) {
		PieBlock block = block(dish.id(), p -> new PieBlock(p, dish, () -> sliceOf(dish)), BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE).noOcclusion());
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new FeastItem(block, new Item.Properties().stacksTo(16).setId(key)));
		PIES.add(item);
		return item;
	}

	/** The slice item a pie dish is cut into. */
	public static Item sliceOf(Dish dish) {
		return switch (dish) {
			case APPLE_PIE -> APPLE_PIE_SLICE;
			case SWEET_BERRY_CHEESECAKE -> SWEET_BERRY_CHEESECAKE_SLICE;
			default -> CHOCOLATE_PIE_SLICE;
		};
	}

	/** Slices cut from a pie dish's item at its quality (null for anything that isn't a pie): four to a pie. */
	static @Nullable ItemStack pieSlices(ItemStack stack) {
		Dish dish = ModItems.dishOf(stack.getItem());
		if (dish == null || !dish.pie()) {
			return null;
		}
		Quality quality = Quality.of(stack);
		ItemStack slices = new ItemStack(sliceOf(dish), PieBlock.SLICES);
		if (quality != null && quality != Quality.GOOD) {
			quality.apply(slices, PieBlock.SLICE_NUTRITION, PieBlock.SLICE_SATURATION, dish.perfectBuffs());
		}
		return slices;
	}

	/**
	 * Dog food on a tamed wolf, horse feed on a tamed horse (donkey, mule or llama): it heals them fully and gives them
	 * speed, strength and resistance (dog food) or speed II and jump boost (horse feed) for five minutes. Dog food that
	 * was cooked badly only heals; a Perfect one lasts twice as long.
	 */
	private static InteractionResult feedAnimal(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (player.isSpectator() || !(target instanceof LivingEntity animal) || !animal.isAlive()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		List<MobEffectInstance> effects;
		boolean dog = target instanceof Wolf wolf && wolf.isTame() && held.is(ModItems.dish(Dish.DOG_FOOD));
		boolean horse = !dog && target instanceof AbstractHorse mount && mount.isTamed() && held.is(HORSE_FEED);
		if (dog) {
			Quality quality = Quality.of(held);
			int ticks = quality == Quality.PERFECT ? FEED_TICKS * 2 : FEED_TICKS;
			boolean spoiled = quality == Quality.BURNT || quality == Quality.CHARRED || quality == Quality.UNDERCOOKED;
			effects = spoiled ? List.of() : List.of(new MobEffectInstance(MobEffects.SPEED, ticks, 0),
				new MobEffectInstance(MobEffects.STRENGTH, ticks, 0), new MobEffectInstance(MobEffects.RESISTANCE, ticks, 0));
		} else if (horse) {
			effects = List.of(new MobEffectInstance(MobEffects.SPEED, FEED_TICKS, 1), new MobEffectInstance(MobEffects.JUMP_BOOST, FEED_TICKS, 0));
		} else {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			animal.setHealth(animal.getMaxHealth());
			for (MobEffectInstance effect : effects) {
				animal.addEffect(effect);
			}
			server.playSound(null, animal.blockPosition(), dog ? SoundEvents.GENERIC_EAT.value() : SoundEvents.HORSE_EAT, SoundSource.PLAYERS, 0.8F, 0.8F);
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, animal.getRandomX(1.0), animal.getRandomY() + 0.5, animal.getRandomZ(1.0), 5, 0.2, 0.2, 0.2, 0.02);
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
				if (dog) {
					ItemStack bowl = new ItemStack(Items.BOWL);
					if (!player.getInventory().add(bowl)) {
						player.drop(bowl, false, Prediction.SERVER_ONLY);
					}
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static Item food(String id, int nutrition, float saturation, @Nullable Item leaves, @Nullable Consumable consumable) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		Item.Properties properties = new Item.Properties();
		if (consumable != null) {
			properties.food(food, consumable);
		} else {
			properties.food(food);
		}
		if (leaves != null) {
			properties.usingConvertsTo(leaves).stacksTo(16);
		}
		return item(id, properties);
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
