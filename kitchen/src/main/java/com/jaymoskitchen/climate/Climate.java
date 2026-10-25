package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * (0.5.25) Thirst ({@link Thirst}) and body heat ({@link BodyHeat}): their items, rules and server loop. Water is
 * boiled clean by using a bottle of it or a {@link WaterskinItem} on a lit campfire. Ops can switch either off with
 * the game rules {@code jaymoskitchen:thirst} and {@code jaymoskitchen:body_heat}.
 */
public final class Climate {
	public static Item CLEAN_WATER;
	public static Item WATERSKIN;
	public static DataComponentType<WaterskinItem.Water> WATERSKIN_WATER;
	public static GameRule<Boolean> THIRST;
	public static GameRule<Boolean> BODY_HEAT;

	private Climate() {
	}

	public static void register() {
		THIRST = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("thirst"));
		BODY_HEAT = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("body_heat"));
		WATERSKIN_WATER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("waterskin_water"),
			DataComponentType.<WaterskinItem.Water>builder()
				.persistent(WaterskinItem.Water.CODEC)
				.networkSynchronized(WaterskinItem.Water.STREAM_CODEC)
				.build());
		CLEAN_WATER = item("clean_water", Item::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
		WATERSKIN = item("waterskin", WaterskinItem::new, new Item.Properties().stacksTo(1)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(CLEAN_WATER);
			output.accept(WATERSKIN);
		});

		// class-load the synced attachments on both sides (a dedicated server's clients must know them)
		JaymosKitchen.LOGGER.debug("climate attachments {} {}", Thirst.WATER.identifier(), BodyHeat.HEAT.identifier());
		ServerTickEvents.END_SERVER_TICK.register(Climate::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Thirst.forget(handler.player.getUUID());
			BodyHeat.forget(handler.player.getUUID());
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
			if (CampfireBlock.isLitCampfire(level.getBlockState(pos)) && boils(player.getMainHandItem())) {
				if (player instanceof ServerPlayer server) {
					boil(server, pos);
				}
				return InteractionResult.SUCCESS;
			}
			// sneaking empty-handed at water over a block (a shallow pond): drink, rather than whatever the block does
			if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
				&& (Thirst.lookingAtWater(player) || Thirst.inRainLookingUp(player))) {
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

	/** A bottle of water, or a waterskin of dirty water: what a lit campfire boils clean. */
	static boolean boils(ItemStack held) {
		if (held.is(Items.POTION)) {
			PotionContents contents = held.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		if (held.is(WATERSKIN)) {
			WaterskinItem.Water water = WaterskinItem.water(held);
			return water.sips() > 0 && !water.clean();
		}
		return false;
	}

	/** It comes off the fire clean. */
	private static void boil(ServerPlayer player, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		if (held.is(WATERSKIN)) {
			held.set(WATERSKIN_WATER, new WaterskinItem.Water(WaterskinItem.water(held).sips(), true));
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
