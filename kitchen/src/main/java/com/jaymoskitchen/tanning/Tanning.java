package com.jaymoskitchen.tanning;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Better With Mods' rendering and tanning, MIT; our own code.) Butchering a fatty animal properly (on a butcher block,
 * or by a butcher villager) gives animal fat; fat rendered on a campfire, or in a cooking pot, is tallow; tallow and
 * string make a candle. Stripping a log with an axe sometimes peels off a piece of bark. Leather soaked with bark in a
 * water cauldron tans: right-click the cauldron with leather (a piece of bark from your pockets goes in with each hide),
 * wait {@link #TAN_TICKS}, then right-click it again to lift out tanned leather, which cuts into leather straps.
 */
public final class Tanning {
	/** The chance stripping a log drops a piece of bark. */
	static final float BARK_CHANCE = 0.35F;
	/** How long a batch soaks (5 minutes); adding a hide starts the batch's clock again. */
	static final int TAN_TICKS = 6000;
	/** Hides one cauldron holds at once. */
	static final int MAX_HIDES = 8;
	/** Straps for other mods' recipes: {@code c:leather_straps} (and our own {@code jaymoskitchen:straps}). */
	public static final TagKey<Item> STRAPS = TagKey.create(Registries.ITEM, JaymosKitchen.id("straps"));

	public static Item ANIMAL_FAT;
	public static Item TALLOW;
	public static Item BARK;
	public static Item TANNED_LEATHER;
	public static Item LEATHER_STRAPS;

	/** A batch soaking in one cauldron: how many hides, and the game time they're done. */
	public record Soak(int hides, long ready) {
		public static final Codec<Soak> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("hides").forGetter(Soak::hides),
			Codec.LONG.fieldOf("ready").forGetter(Soak::ready)
		).apply(i, Soak::new));
	}

	/** Every soaking cauldron in a dimension, by packed block position, saved with the level. Server only. */
	public static final AttachmentType<Map<String, Soak>> SOAKS = AttachmentRegistry.create(JaymosKitchen.id("tanning_soaks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Soak.CODEC)));

	/** Logs an axe was just used on, checked next tick: if it came off stripped, maybe a piece of bark. */
	private record Stripping(ServerLevel level, BlockPos pos, Block was, Player player) {
	}

	private static final List<Stripping> STRIPPING = new ArrayList<>();

	private Tanning() {
	}

	public static void register() {
		ANIMAL_FAT = register("animal_fat", new Item.Properties());
		TALLOW = register("tallow", new Item.Properties());
		BARK = register("bark", new Item.Properties());
		TANNED_LEATHER = register("tanned_leather", new Item.Properties());
		LEATHER_STRAPS = register("leather_straps", new Item.Properties());

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(ANIMAL_FAT, TALLOW, BARK, TANNED_LEATHER, LEATHER_STRAPS)) {
				output.accept(new ItemStack(item));
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			ItemStack held = player.getItemInHand(hand);
			if (held.is(ItemTags.AXES) && state.is(BlockTags.LOGS) && level instanceof ServerLevel server) {
				// the axe does the stripping itself, after this; look again next tick
				if (STRIPPING.stream().noneMatch(s -> s.pos().equals(pos) && s.level() == server)) {
					STRIPPING.add(new Stripping(server, pos.immutable(), state.getBlock(), player));
				}
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!state.is(Blocks.WATER_CAULDRON)) {
				// emptied (or broken and put back): the hides went with the water
				if (!level.isClientSide()) {
					forget(level, pos);
				}
				return InteractionResult.PASS;
			}
			if (held.is(Items.LEATHER)) {
				if (!level.isClientSide()) {
					soak((ServerLevel) level, pos, player, held);
				}
				return InteractionResult.SUCCESS;
			}
			if (held.isEmpty() && level.getAttachedOrElse(SOAKS, Map.of()).containsKey(key(pos))) {
				if (!level.isClientSide()) {
					lift((ServerLevel) level, pos, state, player);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Tanning::tick);
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (state.is(Blocks.WATER_CAULDRON)) {
				forget(level, pos);
			}
		});
	}

	private static Item register(String id, Item.Properties properties) {
		return register(id, Item::new, properties);
	}

	private static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Bark from the logs stripped last tick. */
	private static void tick(MinecraftServer server) {
		if (STRIPPING.isEmpty()) {
			return;
		}
		for (Stripping s : List.copyOf(STRIPPING)) {
			Block now = s.level().getBlockState(s.pos()).getBlock();
			if (now != s.was() && BuiltInRegistries.BLOCK.getKey(now).getPath().startsWith("stripped_")
				&& s.level().getRandom().nextFloat() < BARK_CHANCE) {
				Block.popResource(s.level(), s.pos(), new ItemStack(BARK));
			}
		}
		STRIPPING.clear();
	}

	private static String key(BlockPos pos) {
		return Long.toString(pos.asLong());
	}

	/** A hide goes into the water with a piece of bark from the player's pockets; the batch's clock starts again. */
	private static void soak(ServerLevel level, BlockPos pos, Player player, ItemStack leather) {
		Map<String, Soak> soaks = level.getAttachedOrElse(SOAKS, Map.of());
		Soak was = soaks.get(key(pos));
		int hides = was == null ? 0 : was.hides();
		if (hides >= MAX_HIDES) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tanning.full"));
			return;
		}
		int bark = player.getInventory().findSlotMatchingItem(new ItemStack(BARK));
		if (bark < 0 && !player.hasInfiniteMaterials()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tanning.no_bark"));
			return;
		}
		if (!player.hasInfiniteMaterials()) {
			player.getInventory().getItem(bark).shrink(1);
			leather.shrink(1);
		}
		Map<String, Soak> more = new HashMap<>(soaks);
		more.put(key(pos), new Soak(hides + 1, level.getGameTime() + TAN_TICKS));
		level.setAttached(SOAKS, more);
		level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.3F);
		level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.1);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tanning.soaking", hides + 1));
	}

	/** Right-clicked with an empty hand: done, the hides come out tanned (and the water goes down); not yet, how long. */
	private static void lift(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		Map<String, Soak> soaks = level.getAttachedOrElse(SOAKS, Map.of());
		Soak soak = soaks.get(key(pos));
		long left = soak.ready() - level.getGameTime();
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tanning.wait", soak.hides(), (left + 1199) / 1200));
			level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 4, 0.2, 0.0, 0.2, 0.0);
			return;
		}
		Map<String, Soak> less = new HashMap<>(soaks);
		less.remove(key(pos));
		level.setAttached(SOAKS, less);
		player.getInventory().placeItemBackInInventory(new ItemStack(TANNED_LEATHER, soak.hides()), Prediction.SERVER_ONLY);
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tanning.done", soak.hides()));
	}

	/**
	 * The cauldron emptied or broken: the hides in it are lost with the water. Checked when it's broken or someone next
	 * uses it, so a soak never outlives its water.
	 */
	static void forget(Level level, BlockPos pos) {
		Map<String, Soak> soaks = level.getAttachedOrElse(SOAKS, Map.of());
		if (soaks.containsKey(key(pos))) {
			Map<String, Soak> less = new HashMap<>(soaks);
			less.remove(key(pos));
			level.setAttached(SOAKS, less);
		}
	}
}
