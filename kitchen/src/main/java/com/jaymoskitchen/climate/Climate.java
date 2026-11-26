package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import com.jaymoskitchen.delight.CookwareBlock;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * (0.5.25) Thirst ({@link Thirst}) and body heat ({@link BodyHeat}): their items, rules and server loop. Ops can switch
 * either off with the game rules {@code jaymoskitchen:thirst} and {@code jaymoskitchen:body_heat}.
 * <p>
 * Water is unpurified until it's purified (Andy: "you should also have to purify the water inside"). A bottle of it or a
 * {@link WaterskinItem} is boiled clean by using it on anything you can cook over ({@link #purifiesAt}): a lit campfire,
 * firepit or bonfire, a lit hearth, or a cooking pot or frying pan sitting on heat. A glass bottle dipped in a water
 * cauldron standing over heat draws Clean Water straight out ({@link #drawBoiled}). Or the water goes through a
 * {@link WaterFilterItem}.
 * <p>
 * (0.5.80) Waterskins come in three tiers: Leather (3 sips), Iron (6 sips), Dragon (8 sips, cleans water as it fills).
 * <p>
 * Cactus water (Andy's GT New Horizons picks, patch 3): eight cacti round an empty bucket give a bucket of bitter water,
 * drunk straight from the bucket. It's unpurified until it's boiled on the same fires as a bottle ({@link #BOILED}).
 */
public final class Climate {
	public static Item CLEAN_WATER;
	public static Item WATERSKIN_LEATHER;
	public static Item WATERSKIN_IRON;
	public static Item WATERSKIN_DRAGON;
	public static Item WATER_FILTER;
	public static Item CACTUS_WATER;
	/** On a bucket of cactus water: it has been boiled clean. */
	public static DataComponentType<Unit> BOILED;
	public static DataComponentType<WaterskinItem.Water> WATERSKIN_WATER;
	public static GameRule<Boolean> THIRST;
	public static GameRule<Boolean> BODY_HEAT;
	/** Sprinting spends wind, not food ({@link Stamina}). */
	public static GameRule<Boolean> STAMINA;

	private Climate() {
	}

	public static void register() {
		THIRST = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("thirst"));
		BODY_HEAT = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("body_heat"));
		STAMINA = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("stamina"));
		WATERSKIN_WATER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("waterskin_water"),
			DataComponentType.<WaterskinItem.Water>builder()
				.persistent(WaterskinItem.Water.CODEC)
				.networkSynchronized(WaterskinItem.Water.STREAM_CODEC)
				.build());
		CLEAN_WATER = item("clean_water", Item::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
		WATERSKIN_LEATHER = item("waterskin_leather", p -> new WaterskinItem(WaterskinItem.Tier.LEATHER, p), new Item.Properties().stacksTo(1)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
		WATERSKIN_IRON = item("waterskin_iron", p -> new WaterskinItem(WaterskinItem.Tier.IRON, p), new Item.Properties().stacksTo(1)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
		// dragon hide doesn't burn: dropped in fire or lava the skin survives, as netherite does (the damage-resistant component)
		WATERSKIN_DRAGON = item("waterskin_dragon", p -> new WaterskinItem(WaterskinItem.Tier.DRAGON, p), new Item.Properties().stacksTo(1)
			.fireResistant().component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
		WATER_FILTER = item("water_filter", WaterFilterItem::new, new Item.Properties().durability(WaterFilterItem.USES));
		BOILED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("boiled"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());
		CACTUS_WATER = item("cactus_water_bucket", Item::new, new Item.Properties().stacksTo(1)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.BUCKET));
		ParasiteEffect.register();
		Warmth.register();
		// the climate patch's food, drink and house blocks (bowls of water, the rain barrel, the wall gauge) and the sinew
		WaterBowls.register();
		HouseBlocks.register();
		com.jaymoskitchen.corpse.Sinew.register();
		Soot.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(CLEAN_WATER);
			output.accept(WATERSKIN_LEATHER);
			output.accept(WATERSKIN_IRON);
			output.accept(WATERSKIN_DRAGON);
			output.accept(WATER_FILTER);
			output.accept(CACTUS_WATER);
		});

		// class-load the synced attachments on both sides (a dedicated server's clients must know them)
		JaymosKitchen.LOGGER.debug("climate attachments {} {} {} {}", Thirst.WATER.identifier(), BodyHeat.HEAT.identifier(),
			Wetness.METER.identifier(), Stamina.WIND.identifier());
		ServerTickEvents.END_SERVER_TICK.register(Climate::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Thirst.forget(handler.player.getUUID());
			BodyHeat.forget(handler.player.getUUID());
			Stamina.forget(handler.player.getUUID());
		});
		// 90 seconds' grace from the cold and heat on spawning into the world and on coming back from dying, and a newcomer's
		// first wake gets ten minutes of it, once
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BodyHeat.joined(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				BodyHeat.grace(newPlayer);
			}
		});
		// thirst for what you do: a block broken, an attack (jumps and sprinting are counted each second)
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer server && server.level().getGameRules().get(THIRST)) {
				Thirst.exert(server, Thirst.BREAK);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer server && server.level().getGameRules().get(THIRST)) {
				Thirst.exert(server, Thirst.ATTACK);
			}
			return InteractionResult.PASS;
		});
		// sneak-using a filled water bottle or waterskin pours it over you (unless you're filling it at water)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!player.isShiftKeyDown() || player.isSpectator() || !douses(held) || lookingAtWater(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				douse(server, hand);
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Sip.TYPE, (payload, ctx) -> {
			if (ctx.player().level().getGameRules().get(THIRST)) {
				Thirst.trySip(ctx.player());
			}
		});
		// decided the same on both sides, so the client doesn't go on to drink the bottle the server just boiled
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (boils(player.getMainHandItem()) && purifiesAt(level, pos)) {
				if (player instanceof ServerPlayer server) {
					boil(server, pos);
				}
				return InteractionResult.SUCCESS;
			}
			// a glass bottle in a water cauldron over heat: the water in it has boiled
			if (player.getMainHandItem().is(Items.GLASS_BOTTLE) && boiledCauldron(level, pos)) {
				if (player instanceof ServerPlayer server) {
					drawBoiled(server, pos);
				}
				return InteractionResult.SUCCESS;
			}
			// (Hardrock picks) a waterskin pressed into snow or ice: its water comes away cold
			if (chills(player.getMainHandItem()) && (level.getBlockState(pos).is(BlockTags.ICE) || level.getBlockState(pos).is(BlockTags.SNOW))) {
				if (player instanceof ServerPlayer server) {
					chill(server, pos);
				}
				return InteractionResult.SUCCESS;
			}
			// sneaking empty-handed at water over a block (a shallow pond): drink, rather than whatever the block does
			if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
				&& (Thirst.lookingAtWater(player) || Thirst.inRainLookingUp(player)
					// (0.5.48) a fire drinker at a fire, a lit campfire or lava
					|| Thirst.fireDrinker(player) && (Thirst.fireLookedAt(player) != null || Thirst.lookingAtLava(player)))) {
				if (player instanceof ServerPlayer server && server.level().getGameRules().get(THIRST)) {
					Thirst.trySip(server);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	private static Item item(String id, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** A bottle of water (unpurified or clean) or a waterskin with water in it: what can be poured over yourself. */
	static boolean douses(ItemStack held) {
		return Thirst.bottledWater(held) || chills(held);
	}

	/** Water in reach (a waterskin or bottle used there is being filled, not poured). */
	private static boolean lookingAtWater(net.minecraft.world.entity.player.Player player) {
		net.minecraft.world.phys.HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
		return hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
			&& player.level().getFluidState(block.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
	}

	/** Poured over yourself: wet through, any fire on you put out; a bottle gives its glass back, a waterskin loses a sip. */
	private static void douse(ServerPlayer player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (chills(held)) {
			WaterskinItem.Water water = WaterskinItem.water(held);
			held.set(WATERSKIN_WATER, new WaterskinItem.Water(water.sips() - 1, water.clean(), water.temp(), water.until()));
		} else {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
		}
		Wetness.douse(player);
	}

	/** A waterskin with water in it. */
	static boolean chills(ItemStack held) {
		return (held.is(WATERSKIN_LEATHER) || held.is(WATERSKIN_IRON) || held.is(WATERSKIN_DRAGON)) && WaterskinItem.water(held).sips() > 0;
	}

	/** Its water comes away cold, for a while. */
	private static void chill(ServerPlayer player, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		WaterskinItem.Water water = WaterskinItem.water(held);
		held.set(WATERSKIN_WATER, new WaterskinItem.Water(water.sips(), water.clean(), -1, player.level().getGameTime() + Warmth.SKIN_KEEPS));
		player.level().playSound(null, pos, SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 0.8F, 1.2F);
	}

	/**
	 * A bottle of water, or a waterskin with water in it: what a lit campfire boils clean. (Hardrock picks) a waterskin
	 * comes off the fire hot as well, so clean water can go back on to warm up. A bucket of cactus water not yet boiled.
	 */
	static boolean boils(ItemStack held) {
		if (held.is(CACTUS_WATER)) {
			return !held.has(BOILED);
		}
		if (held.is(WaterBowls.WATER_BOWL)) {
			// a bowl of pond water: boil it clean
			return true;
		}
		if (held.is(Items.POTION)) {
			PotionContents contents = held.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		if (held.is(WATERSKIN_LEATHER) || held.is(WATERSKIN_IRON) || held.is(WATERSKIN_DRAGON)) {
			return WaterskinItem.water(held).sips() > 0;
		}
		return false;
	}

	/**
	 * Heat that boils water held to it: a lit campfire, firepit or bonfire (the campfires tag), a lit bonfire's stone
	 * edge or hearth, or Kitchen's cooking pot or frying pan standing on heat.
	 */
	public static boolean purifiesAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (CampfireBlock.isLitCampfire(state) || CookwareBlock.heated(level, pos)) {
			return true;
		}
		boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
		return lit && (HearthBlock.warm(state) || BONFIRE_EDGE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
	}

	/** Jaymo's Powers' bonfire edge, the stone ring round a 3x3 bonfire. */
	private static final net.minecraft.resources.Identifier BONFIRE_EDGE = net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "bonfire_edge");

	/** A water cauldron with heat under it (anything Kitchen's cookware cooks on). */
	static boolean boiledCauldron(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.WATER_CAULDRON) && CookwareBlock.hot(level.getBlockState(pos.below()));
	}

	/** A bottle of Clean Water drawn from a boiled cauldron, which drops a level as a bottle from any cauldron does. */
	private static void drawBoiled(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(pos);
		ItemStack held = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(held, player, new ItemStack(CLEAN_WATER)));
		player.awardStat(Stats.USE_CAULDRON);
		player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.15, 0.1, 0.15, 0.02);
	}

	/** It comes off the fire clean. */
	private static void boil(ServerPlayer player, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		if (held.is(WATERSKIN_LEATHER) || held.is(WATERSKIN_IRON) || held.is(WATERSKIN_DRAGON)) {
			held.set(WATERSKIN_WATER, new WaterskinItem.Water(WaterskinItem.water(held).sips(), true, 1,
				player.level().getGameTime() + Warmth.SKIN_KEEPS));
		} else if (held.is(WaterBowls.WATER_BOWL)) {
			// one bowl of the stack comes off the fire clean; the rest wait their turn
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(held, player, new ItemStack(WaterBowls.CLEAN_WATER_BOWL)));
		} else if (held.is(CACTUS_WATER)) {
			held.set(BOILED, Unit.INSTANCE);
			held.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskitchen.cactus_water_bucket.boiled"));
		} else {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(held, player, new ItemStack(CLEAN_WATER)));
		}
		ServerLevel level = player.level();
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.15, 0.1, 0.15, 0.02);
	}

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % 20 == 0;
		long seconds = server.getTickCount() / 20L;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			if (level.getGameRules().get(STAMINA)) {
				Stamina.tick(player);
			} else if (player.hasAttached(Stamina.WIND)) {
				Stamina.clear(player);
			}
			boolean thirst = level.getGameRules().get(THIRST);
			boolean heat = level.getGameRules().get(BODY_HEAT);
			if (heat) {
				BodyHeat.tick(player);
			}
			if (thirst) {
				Thirst.tick(player);
			}
			if (second) {
				if (heat) {
					BodyHeat.second(player, seconds);
				}
				if (thirst) {
					Thirst.second(player, seconds);
				}
			}
		}
	}
}