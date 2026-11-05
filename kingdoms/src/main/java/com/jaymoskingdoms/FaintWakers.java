package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.594) Andy (2026-11-05): "Villagers near you can wake you and either hint at what happened, outright tell you and
 * give you a drink from theirs, or just steal from you".
 * <p>
 * A player passed out from hunger or thirst (Jaymo's Powers' {@code Fainting}) who lies within {@link #RANGE} of one of a
 * settlement's people in sight of them ({@link Recollections#rememberer}: villagers, guards, workers) draws the nearest
 * over. What they do there goes by their temperament ({@link Temperaments}; {@link #choose}):
 * <ul>
 * <li><b>Hint:</b> they shake you awake and, as the black lifts, say something that only hints at it ("you're white as a
 * sheet, when did you last eat?"), softly, plainly or gruffly by temperament.</li>
 * <li><b>Tell and help:</b> they shake you awake and tell you straight that you fainted from thirst (or hunger), and give
 * you a drink from their own waterskin (Kitchen's thirst, {@link #DRINK} clean water) or a bite of their food
 * ({@link #BITE} food).</li>
 * <li><b>Steal:</b> they don't wake you. They go through your pack while you're out and walk off with something
 * ({@link #steal}): you come round with it gone. A villager keeps it in their own inventory, so picking their pocket
 * (Pickpocket) can win it back. Guards and other settlement folk never steal.</li>
 * </ul>
 * Speech bubbles only, no chat. One waker per faint; nobody comes while you're downed, carried or otherwise out of play.
 */
public final class FaintWakers {
	/** How far a waker may be (blocks), how close they come to act, and how long they may take to get there (ticks). */
	static final double RANGE = 16.0, REACH = 2.2;
	static final int GIVE_UP_TICKS = 300;
	/** Walking over: a villager's speed, and settlement folk's. */
	static final float VILLAGER_SPEED = 0.6F;
	static final double FOLK_SPEED = 1.0;
	/** After shaking them, how long before they speak (ticks): the letterbox is opening. */
	static final int SPEAK_AFTER = 30;
	/** The drink a helper gives (Kitchen thirst points, of 20), and the bite of food (food points, saturation). */
	static final int DRINK = 6, BITE = 4;
	static final float BITE_SATURATION = 0.4F;
	/** A thief takes a whole stack of at most this many, else half of it. */
	static final int STEAL_WHOLE = 8;
	/** Lines per moment per tone. */
	static final int LINES = 3;

	enum Deed {
		HINT, TELL, STEAL
	}

	private static final class Waker {
		final Mob mob;
		final Deed deed;
		final long since;
		/** When they shook the player awake (game time), or -1 while walking over. */
		long woke = -1;

		Waker(Mob mob, Deed deed, long since) {
			this.mob = mob;
			this.deed = deed;
			this.since = since;
		}
	}

	/** Fainted player → whoever is on their way to them (or about to speak). */
	private static final Map<UUID, Waker> WAKERS = new HashMap<>();
	/** Fainted players already seen to this faint (no second waker). */
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	private FaintWakers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FaintWakers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAKERS.clear();
			SEEN.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Waker w = WAKERS.get(id);
			if (w != null) {
				if (!follow(player, w)) {
					release(w.mob);
					WAKERS.remove(id);
				}
				continue;
			}
			if (!PowersCompat.fainted(player)) {
				SEEN.remove(id);
				continue;
			}
			if (player.tickCount % 10 != 0 || SEEN.containsKey(id) || Downed.isDowned(player)) {
				continue;
			}
			Mob mob = nearest(player);
			if (mob != null) {
				SEEN.put(id, player.level().getGameTime());
				WAKERS.put(id, new Waker(mob, choose(mob), player.level().getGameTime()));
			}
		}
	}

	/** The nearest of a settlement's people free to come over, in sight of {@code player}. */
	private static @Nullable Mob nearest(ServerPlayer player) {
		List<Mob> near = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> free(m, player));
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : near) {
			double d = m.distanceToSqr(player);
			if (d < bestD) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	private static boolean free(Mob m, ServerPlayer player) {
		if (!(m instanceof PathfinderMob) || !Recollections.rememberer(m) || !m.isAlive() || m.isBaby() || m.isSleeping() || m.isPassenger()
			|| m.getTarget() != null || Downed.isDowned(m) || PowersCompat.outOfPlay(m) || Approaches.talking(m) || !m.hasLineOfSight(player)) {
			return false;
		}
		for (Waker w : WAKERS.values()) {
			if (w.mob == m) {
				return false;
			}
		}
		return true;
	}

	/** What {@code mob} does for (or to) someone passed out, by temperament. */
	static Deed choose(Mob mob) {
		float r = mob.getRandom().nextFloat();
		boolean canSteal = mob instanceof Villager;
		Temperaments.Temperament t = Temperaments.of(mob);
		// hint below the first share, tell below the second, steal past it
		float hint, tell;
		if (t == null) {
			hint = 0.5F;
			tell = 0.8F;
		} else {
			switch (t) {
				case GREEDY -> {
					hint = 0.15F;
					tell = 0.15F;
				}
				case SPITEFUL -> {
					hint = 0.4F;
					tell = 0.4F;
				}
				case KINDHEARTED, DEVOUT -> {
					hint = 0.15F;
					tell = 1.0F;
				}
				case CHEERFUL, TIMID, DUTIFUL -> {
					hint = 0.45F;
					tell = 0.95F;
				}
				case PROUD, HOTHEADED -> {
					hint = 0.7F;
					tell = 0.8F;
				}
				default -> {
					hint = 0.5F;
					tell = 0.8F;
				}
			}
		}
		if (r < hint) {
			return Deed.HINT;
		}
		if (r < tell) {
			return Deed.TELL;
		}
		// guards and settlement folk don't rob the fallen: they just rouse them
		return canSteal ? Deed.STEAL : Deed.HINT;
	}

	/** One tick of a waker's errand; false once it's over. */
	private static boolean follow(ServerPlayer player, Waker w) {
		Mob mob = w.mob;
		long now = player.level().getGameTime();
		if (!mob.isAlive() || mob.isRemoved() || mob.level() != player.level() || Downed.isDowned(mob) || !player.isAlive()
			|| Downed.isDowned(player)) {
			return false;
		}
		if (w.woke >= 0) {
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
			if (now - w.woke >= SPEAK_AFTER) {
				speak(player, w);
				return false;
			}
			return true;
		}
		if (!PowersCompat.fainted(player) || now - w.since > GIVE_UP_TICKS || mob.distanceTo(player) > RANGE * 1.5) {
			return false;
		}
		if (mob.distanceTo(player) > REACH) {
			walk(mob, player);
			return true;
		}
		hold(mob, player);
		if (w.deed == Deed.STEAL) {
			steal(player, mob);
			return false;
		}
		if (PowersCompat.bringRound(player)) {
			w.woke = now;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
				SoundSource.PLAYERS, 0.6F, 0.8F);
			return true;
		}
		return false;
	}

	private static void speak(ServerPlayer player, Waker w) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		boolean thirst = PowersCompat.faintedOfThirst(player);
		String tone = SharedHistory.toneNoGreed(w.mob).key();
		String moment = w.deed == Deed.TELL ? "tell" : "hint";
		Component line = Component.translatable("faint.jaymoskingdoms." + moment + "." + (thirst ? "thirst" : "hunger") + "." + tone + "."
			+ w.mob.getRandom().nextInt(LINES), Recollections.nameTo(player, w.mob));
		Speech.tell(level, w.mob, player, line, Speech.WHITE);
		if (w.deed == Deed.TELL) {
			if (thirst && KitchenCompat.drink(player, DRINK)) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
			} else {
				player.getFoodData().eat(BITE, BITE_SATURATION);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
			}
		}
	}

	/**
	 * Goes through {@code player}'s pack while they're out and takes something: one stack from their inventory (not what's
	 * worn), all of it if it's {@link #STEAL_WHOLE} or fewer, else half. It goes into the thief's own inventory.
	 */
	static void steal(ServerPlayer player, Mob thief) {
		if (!(thief instanceof Villager v)) {
			return;
		}
		var inv = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			if (!inv.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		int slot = slots.get(thief.getRandom().nextInt(slots.size()));
		ItemStack stack = inv.getItem(slot);
		int take = stack.getCount() <= STEAL_WHOLE ? stack.getCount() : stack.getCount() / 2;
		ItemStack taken = stack.split(take);
		ItemStack left = v.getInventory().addItem(taken);
		if (!left.isEmpty()) {
			// their pockets are full: they leave what wouldn't fit
			stack.grow(left.getCount());
		}
		inv.setChanged();
	}

	private static void walk(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), VILLAGER_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(player, FOLK_SPEED);
		}
	}

	private static void hold(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (!mob.isPassenger()) {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	private static void release(LivingEntity mob) {
		if (mob instanceof Villager v && v.isAlive()) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}
}
