package com.jaymoskitchen.cooking;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.net.Payloads.CookUpdate;
import com.jaymoskitchen.kitchen.KitchenBlocks;
import com.jaymoskitchen.kitchen.KitchenPool;
import java.util.ArrayList;
import java.util.BitSet;
import com.jaymoskitchen.kitchen.HobQueue;
import com.jaymoskitchen.kitchen.OvenHob;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server-side cooking sessions, one per player. */
public final class Kitchen {
	/** How far (squared) a player may wander from the station before the session is dropped. */
	private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** The dishes whose sessions here are running at {@code pos} in {@code level} (for the oven's cookware locks). */
	static void dishesAt(net.minecraft.server.level.ServerLevel level, BlockPos pos, java.util.List<Dish> out) {
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			if (entry.getValue().pos.equals(pos)) {
				net.minecraft.server.level.ServerPlayer cook = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (cook != null && cook.level() == level) {
					out.add(entry.getValue().dish);
				}
			}
		}
	}

	/**
	 * Every dish whose minigame is running at {@code pos} in {@code level}, from any kind of session (pot steps,
	 * grilling and oven baking, searing, smoking). Server side; used for the oven's cookware locks (see HobLocks).
	 */
	public static List<Dish> dishesCookingAt(ServerLevel level, BlockPos pos) {
		List<Dish> out = new ArrayList<>();
		dishesAt(level, pos, out);
		Grill.dishesAt(level, pos, out);
		Sear.dishesAt(level, pos, out);
		Smoke.dishesAt(level, pos, out);
		Minigames.dishesAt(level, pos, out);
		return out;
	}

	private Kitchen() {
	}

	private static final class Session {
		final Dish dish;
		final BlockPos pos;
		final Station station;
		final int startTick;
		/** Par and burn time for this cook: each is the dish's base, randomly stretched or squeezed. */
		final int parTicks;
		final int limitTicks;
		int progress;
		int mistakes;

		Session(Dish dish, BlockPos pos, Station station, int startTick, RandomSource random) {
			this.dish = dish;
			this.pos = pos;
			this.station = station;
			this.startTick = startTick;
			this.parTicks = Math.round(dish.parTicks(station) * (0.85F + random.nextFloat() * 0.3F));
			this.limitTicks = Math.round(dish.limitTicks(station) * (0.9F + random.nextFloat() * 0.2F));
		}
	}

	/** Opens the cooking screen at a station and starts a dish there straight away (from the cooking table). */
	public static void startAt(ServerPlayer player, BlockPos pos, Station station, Dish dish) {
		ServerPlayNetworking.send(player, new com.jaymoskitchen.net.Payloads.OpenKitchen(pos, station.ordinal(), stocked(player, pos, station)));
		start(player, pos, dish.ordinal(), station.ordinal());
	}

	public static void start(ServerPlayer player, BlockPos pos, int dishIndex, int stationIndex) {
		Dish dish = Dish.byIndex(dishIndex);
		ServerLevel level = player.level();
		// the station the screen was opened for, and it must still cook the dish (an oven: still lit, the dish's pot
		// or pan still on the hob; other cookware may have come or gone)
		Station station = Station.byIndex(stationIndex);
		if (station != null && (dish == null || !station.cooks(level, pos, dish))) {
			station = null;
		}
		if (dish == null || station == null || !inReach(player, pos) || SESSIONS.containsKey(player.getUUID())
			|| Grill.isGrilling(player) || Smoke.isSmoking(player) || Sear.isSearing(player) || Minigames.isPlaying(player)) {
			reject(player, dishIndex, "message.jaymoskitchen.station_gone");
			return;
		}
		if (!station.allows(dish)) {
			reject(player, dishIndex, "message.jaymoskitchen.wrong_station");
			return;
		}
		if (level.getBlockEntity(pos) instanceof OvenBlockEntity oven && !player.hasInfiniteMaterials()
			&& !OvenHob.spend(oven, OvenHob.DISH_COST)) {
			reject(player, dishIndex, "message.jaymoskitchen.oven_low_heat");
			return;
		}
		// creative mode cooks for free
		if (!player.hasInfiniteMaterials()) {
			if (!hasIngredients(sources(player, pos), dish.ingredients())) {
				reject(player, dishIndex, "message.jaymoskitchen.missing_ingredients");
				return;
			}
			takeIngredients(player, pos, dish.ingredients());
		}
		switch (dish.method()) {
			case GRILL, OVEN -> {
				Grill.start(player, dish, pos, station);
				return;
			}
			case SMOKE -> {
				Smoke.start(player, dish, pos);
				return;
			}
			case SEAR -> {
				Sear.start(player, dish, pos);
				return;
			}
			case CHOP, ROAST, STIR -> {
				Minigames.start(player, dish, pos);
				return;
			}
			default -> {
			}
		}
		Session session = new Session(dish, pos.immutable(), station, level.getServer().getTickCount(), player.getRandom());
		SESSIONS.put(player.getUUID(), session);
		playAt(level, pos, SoundEvents.SMOKER_SMOKE, 1.0F);
		send(player, session, CookUpdate.STATUS_COOKING, 0);
	}

	public static void press(ServerPlayer player, int stepIndex) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || stepIndex < 0 || stepIndex >= Step.values().length) {
			return;
		}
		if (!inReach(player, session.pos) || !session.station.cooks(player.level(), session.pos, session.dish)) {
			finish(player, session, true);
			return;
		}
		Step pressed = Step.values()[stepIndex];
		if (session.dish.steps().get(session.progress) == pressed) {
			session.progress++;
			playAt(player.level(), session.pos, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F + session.progress * 0.1F);
		} else {
			session.mistakes++;
			playAt(player.level(), session.pos, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
		}
		if (session.progress >= session.dish.steps().size() || session.mistakes >= Quality.MAX_MISTAKES) {
			finish(player, session, false);
		} else {
			send(player, session, CookUpdate.STATUS_COOKING, 0);
		}
	}

	/** Closing the screen mid-cook leaves the pot on the fire: it burns. On the grill, it's served as-is. */
	public static void abandon(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		if (session != null) {
			finish(player, session, true);
		}
		Grill.serve(player);
		Smoke.abandon(player);
		Sear.abandon(player);
		Minigames.abandon(player);
		Prep.abandon(player);
	}

	public static void tick(MinecraftServer server) {
		tickFinishing(server);
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			Session session = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				// logged out mid-cook: nothing to hand the dish to
				it.remove();
			} else if (server.getTickCount() - session.startTick > session.limitTicks) {
				it.remove();
				award(player, session, true);
			}
		}
	}

	private static void finish(ServerPlayer player, Session session, boolean timedOut) {
		SESSIONS.remove(player.getUUID());
		award(player, session, timedOut);
	}

	private static void award(ServerPlayer player, Session session, boolean timedOut) {
		int elapsed = player.level().getServer().getTickCount() - session.startTick;
		Quality quality = Quality.judge(session.mistakes, elapsed, session.parTicks, timedOut);
		award(player, session.dish, session.pos, quality);
		send(player, session, CookUpdate.STATUS_FINISHED, quality.ordinal());
	}

	/**
	 * Hands the player the finished dish at the given quality. At an oven it isn't ready at once: the screen closes,
	 * and it keeps cooking on the oven (bubbling, sizzling, baking) for its own time first, see HobQueue and
	 * {@link Dish#hobTicks()}.
	 */
	static void award(ServerPlayer player, Dish dish, BlockPos pos, Quality quality) {
		if (ExplodingPot.blowsUp(player, pos, quality)) {
			return;
		}
		ItemStack stack = new ItemStack(ModItems.dish(dish), dish.batch());
		quality.apply(stack, dish);
		ServerLevel level = player.level();
		if (level.getBlockState(pos).getBlock() instanceof OvenBlock && level.getBlockEntity(pos) instanceof OvenBlockEntity oven) {
			int ticks = dish.hobTicks();
			Component name = stack.getHoverName();
			HobQueue.add(oven, level, stack, quality, player.getUUID(), ticks);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cooking_timer", name, HobQueue.clock(ticks)));
			return;
		}
		serve(player, stack, level, pos, quality);
	}

	/** Gives the cook a finished dish (dropped at their feet if their pockets are full), with a sound for how it went. */
	public static void serve(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, Quality quality) {
		give(player, stack);
		SoundEvent sound = switch (quality) {
			case BURNT, CHARRED, UNDERCOOKED -> SoundEvents.FIRE_EXTINGUISH;
			case GOOD -> SoundEvents.EXPERIENCE_ORB_PICKUP;
			case PERFECT -> SoundEvents.PLAYER_LEVELUP;
		};
		playAt(level, pos, sound, 1.0F);
	}

	/**
	 * An undercooked dish held against a lit station (campfire, furnace, smoker, oven...) goes back on to finish
	 * cooking: a few seconds later it comes back edible, at ordinary (Good) quality. False if it isn't one.
	 */
	public static boolean reheat(ServerPlayer player, BlockPos pos, ItemStack held) {
		Dish dish = ModItems.dishOf(held.getItem());
		if (dish == null || Quality.of(held) != Quality.UNDERCOOKED) {
			return false;
		}
		ServerLevel level = player.level();
		if (level.getBlockEntity(pos) instanceof OvenBlockEntity oven && !player.hasInfiniteMaterials()
			&& !OvenHob.spend(oven, OvenHob.REHEAT_COST)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.oven_low_heat"));
			return true;
		}
		held.shrink(1);
		ItemStack stack = new ItemStack(ModItems.dish(dish));
		Quality.GOOD.apply(stack, dish);
		if (level.getBlockEntity(pos) instanceof OvenBlockEntity oven) {
			// on an oven it waits there with the other dishes (and is saved with it)
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.reheating", stack.getHoverName()));
			HobQueue.add(oven, level, stack, Quality.GOOD, player.getUUID(), REHEAT_TICKS);
			playAt(level, pos, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0F);
			return true;
		}
		FINISHING.add(new Finishing(player.getUUID(), level, pos.immutable(), stack, Quality.GOOD,
			level.getServer().getTickCount() + FINISH_TICKS));
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.reheating", stack.getHoverName()));
		playAt(level, pos, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0F);
		return true;
	}

	/** How long a reheated dish takes at a campfire, furnace etc. (kept in memory, see tickFinishing). */
	private static final int FINISH_TICKS = 80;
	/** How long a reheated dish takes on an oven (queued on the oven, see HobQueue). */
	private static final int REHEAT_TICKS = 10 * 20;

	private record Finishing(UUID player, ServerLevel level, BlockPos pos, ItemStack stack, Quality quality, int readyTick) {
	}

	private static final List<Finishing> FINISHING = new ArrayList<>();

	/** Dishes finishing on an oven: they bubble and steam (or smoke, in a pan), then go to the cook. */
	private static void tickFinishing(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Finishing> it = FINISHING.iterator();
		while (it.hasNext()) {
			Finishing f = it.next();
			double x = f.pos().getX() + 0.5, y = f.pos().getY() + 1.1, z = f.pos().getZ() + 0.5;
			boolean pan = !(f.level().getBlockEntity(f.pos()) instanceof OvenBlockEntity oven) || OvenHob.cookware(oven) != OvenHob.Cookware.POT;
			if (now % 3 == 0) {
				if (pan) {
					f.level().sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.2, 0.05, 0.2, 0.02);
				} else {
					f.level().sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.2, z, 3, 0.2, 0.05, 0.2, 0.02);
					f.level().sendParticles(ParticleTypes.CLOUD, x, y + 0.3, z, 1, 0.1, 0.05, 0.1, 0.02);
				}
			}
			if (now % 20 == 0) {
				f.level().playSound(null, f.pos(), pan ? SoundEvents.CAMPFIRE_CRACKLE : SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
					SoundSource.BLOCKS, 0.8F, 1.0F);
			}
			if (now >= f.readyTick()) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(f.player());
				if (player != null) {
					// named before it's handed over: once it's in the inventory this stack is empty
					Component name = f.stack().getHoverName();
					serve(player, f.stack(), f.level(), f.pos(), f.quality());
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.ready", name));
				} else {
					// the cook left: it's waiting on the oven
					net.minecraft.world.level.block.Block.popResource(f.level(), f.pos().above(), f.stack());
				}
			}
		}
	}

	static boolean inReach(ServerPlayer player, BlockPos pos) {
		return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQ;
	}

	/** Client side: whether the pockets alone hold the ingredients. */
	public static boolean hasIngredients(Inventory inventory, Map<Step, Integer> ingredients) {
		for (Map.Entry<Step, Integer> need : ingredients.entrySet()) {
			if (count(inventory, need.getKey()) < need.getValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Where a cook at {@code pos} takes ingredients from: their pockets, then the Cooking for Blockheads kitchen the
	 * spot is part of (see KitchenPool).
	 */
	public static KitchenPool sources(ServerPlayer player, BlockPos pos) {
		return KitchenPool.at(player, pos);
	}

	public static boolean hasIngredients(KitchenPool pool, Map<Step, Integer> ingredients) {
		for (Map.Entry<Step, Integer> need : ingredients.entrySet()) {
			Step step = need.getKey();
			if (countStep(pool, step, need.getValue()) < need.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** How many of an ingredient the pool holds, counting no further than {@code max} (any of its items counts). */
	private static int countStep(KitchenPool pool, Step step, int max) {
		int have = 0;
		for (Item item : step.items()) {
			have += pool.count(item, step::matches, max - have);
			if (have >= max) {
				break;
			}
		}
		return have;
	}

	private static int count(Container container, Step step) {
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (step.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Bit i set: the station's i-th dish can be made from what's in reach (for greying out the rest). A BitSet, so a
	 * station can list any number of dishes (a long held 63).
	 */
	public static BitSet stocked(ServerPlayer player, BlockPos pos, Station station) {
		KitchenPool pool = sources(player, pos);
		List<Dish> dishes = station.dishes();
		BitSet mask = new BitSet(dishes.size());
		for (int i = 0; i < dishes.size(); i++) {
			if (hasIngredients(pool, dishes.get(i).ingredients())) {
				mask.set(i);
			}
		}
		return mask;
	}

	/** Removes the ingredients (pockets first, then the kitchen) and hands back their leftovers. */
	static void takeIngredients(ServerPlayer player, BlockPos pos, Map<Step, Integer> ingredients) {
		KitchenPool pool = sources(player, pos);
		for (Map.Entry<Step, Integer> need : ingredients.entrySet()) {
			Step step = need.getKey();
			int left = need.getValue();
			for (Item item : step.items()) {
				left -= pool.take(item, step::matches, left);
				if (left <= 0) {
					break;
				}
			}
			Item remainder = step.remainder();
			if (remainder != null) {
				give(player, new ItemStack(remainder, need.getValue()));
			}
		}
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static void reject(ServerPlayer player, int dishIndex, String messageKey) {
		player.sendOverlayMessage(Component.translatable(messageKey));
		ServerPlayNetworking.send(player, new CookUpdate(dishIndex, 0, 0, CookUpdate.STATUS_REJECTED, 0, 0, 0));
	}

	private static void send(ServerPlayer player, Session session, int status, int quality) {
		ServerPlayNetworking.send(player, new CookUpdate(session.dish.ordinal(), session.progress, session.mistakes, status,
			quality, session.parTicks, session.limitTicks));
	}

	private static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound, float pitch) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, pitch);
	}
}
