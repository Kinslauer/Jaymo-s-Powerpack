package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Deck;
import com.jaymoskingdoms.sailing.Ship;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A crewed ship's provisions (Andy, 2026-11-26: "they should sail and fish off the sides for sustenance like all other
 * boat crews now should"). Every ship sailed by a crew of her own, a pirate, a realm's warship, a merchantman, a whaler,
 * a smack, a pillagers' long ship, puts to sea with {@link #DAYS_STOCKED} days' food for each of her hands, and they eat a
 * day's each day. While her stores aren't full, there's no fight on and no foe aboard, a few of her idle hands go to her
 * rail and fish over the side ({@link Anglers}: the float trails with her, and every fish is fought and landed in view), and
 * each fish landed is a day's food for one. A smack on her grounds fishes whatever her stores are, and what isn't eaten is
 * her catch, landed at home. Run out and her company goes hungry: weak, and nobody's wounds mend, till fish come aboard
 * again.
 */
public final class Provisions {
	/** Days' food for each hand she puts to sea with, the most her stores hold, and how low they run before she heaves to to fish. */
	static final int DAYS_STOCKED = 6, DAYS_FULL = 10, DAYS_LOW = 3;
	/** How often (ticks) her stores are looked at, and how long a hungry hand's weakness lasts each time. */
	static final int LOOK = 100, WEAK_TICKS = 160;
	/** A line goes over her side only while she makes no more way than this (blocks a tick). */
	static final double MOST_WAY = 0.2;
	/** At most this share of her hands fish at once (at least one). */
	static final int ANGLERS_PER = 3;
	/** The float goes out this far beyond her rail (blocks, plus up to as much again), a little ahead or astern. */
	static final double CAST_LEAST = 4.0, CAST_ALONG = 3.0;
	/** A hungry hand carries this tag: no healing ({@code LivingEntityHealMixin}). */
	public static final String STARVING = "jaymoskingdoms.starving";
	static final String FOOD = "food", FED = "fed", HUNGRY = "hungry", CATCH = "catch:";

	private Provisions() {
	}

	/** Her stores looked at now and then: stocked if they never were, a day's food eaten each day, hunger felt. */
	static void tick(ServerLevel level, Ship ship, Crew crew, long now) {
		if ((now + ship.getId()) % LOOK != 0) {
			return;
		}
		List<LivingEntity> hands = ship.crewAboard();
		long day = Production.day(level.getServer());
		if (crew.stores == null) {
			crew.stores = new HashMap<>();
			crew.stores.put(FOOD, (long)Math.max(1, hands.size()) * DAYS_STOCKED);
			crew.stores.put(FED, day);
		}
		long fed = crew.stores.getOrDefault(FED, day);
		if (day > fed) {
			long eaten = Math.min(3L, day - fed) * hands.size();
			long food = crew.stores.getOrDefault(FOOD, 0L) - eaten;
			crew.stores.put(FOOD, Math.max(0L, food));
			crew.stores.put(FED, day);
			if (food < 0) {
				crew.stores.put(HUNGRY, 1L);
			}
		}
		boolean hungry = crew.stores.getOrDefault(HUNGRY, 0L) != 0L;
		for (LivingEntity hand : hands) {
			if (hungry) {
				hand.addTag(STARVING);
				hand.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, true, false));
			} else {
				hand.removeTag(STARVING);
			}
		}
	}

	/** Whether her hands should be fishing: her stores short, or a smack on her grounds. */
	static boolean wantFish(Ship ship, Crew crew) {
		if (crew.side == Crew.Side.FISHER && "fishing".equals(crew.mode)) {
			return true;
		}
		long food = crew.stores == null ? 0L : crew.stores.getOrDefault(FOOD, 0L);
		return food < (long)Math.max(1, ship.crewAboard().size()) * DAYS_FULL;
	}

	/**
	 * {@code hand}, idle on her deck, fishing over her side if her stores want it ({@link #wantFish}): at it already, he
	 * stays; at her rail, he casts; else he goes to the nearest place at it. False if he has better to do (she's full, or
	 * enough of them are at it), and he strolls as before.
	 */
	static boolean fish(ServerLevel level, Ship ship, Crew crew, Deck deck, LivingEntity hand, Vec3 seat, Crew.Walk walk, long now) {
		if (!(hand instanceof Mob angler)) {
			return false;
		}
		if (Anglers.angling(angler)) {
			return true;
		}
		if (!wantFish(ship, crew) || ship.drift().horizontalDistance() > MOST_WAY) {
			// (full, or making too much way for a line: a hooked fish couldn't keep up)
			return false;
		}
		int at = 0, aboard = 0;
		for (LivingEntity e : ship.crewAboard()) {
			aboard++;
			if (Anglers.angling(e)) {
				at++;
			}
		}
		if (at >= Math.max(1, aboard / ANGLERS_PER)) {
			return false;
		}
		Vec3 rail = rail(deck, seat);
		if (rail == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		if (rail.distanceToSqr(seat) > 0.36) {
			List<Vec3> way = deck.path(seat, rail);
			if (way == null || way.isEmpty()) {
				return false;
			}
			walk.way = new ArrayList<>(way);
			walk.pace = Corsairs.WALK_PACE;
			walk.rest = now + (int)(way.size() / walk.pace) + 10;
			return true;
		}
		// at her rail: facing out over the water, the float sent out beyond it
		double side = Math.signum(rail.x);
		Vec3 spot = new Vec3(rail.x + side * (CAST_LEAST + random.nextDouble() * CAST_LEAST), 0.0, rail.z + (random.nextDouble() - 0.5) * 2.0 * CAST_ALONG);
		Vec3 out = ship.toWorld(spot.x, 0.0, spot.z).subtract(hand.position());
		float yaw = (float)(Mth.atan2(out.z, out.x) * Mth.RAD_TO_DEG) - 90.0F;
		hand.setYRot(yaw);
		hand.setYBodyRot(yaw);
		hand.setYHeadRot(yaw);
		if (!Anglers.castFrom(level, angler, ship, spot, fish -> caught(crew, fish))) {
			walk.rest = now + Corsairs.HAND_IDLE;
			return false;
		}
		walk.rest = now + Corsairs.HAND_IDLE;
		return true;
	}

	/**
	 * Whether a ship with nowhere she must be lies hove to for her hands to fish: her stores down to {@link #DAYS_LOW} days a
	 * hand, till they're {@link #DAYS_FULL} again, or a fish on a line over her side now.
	 */
	static boolean heaveTo(Ship ship, Crew crew) {
		long hands = Math.max(1, ship.crewAboard().size());
		for (LivingEntity e : ship.crewAboard()) {
			if (Anglers.fighting(e)) {
				return true;
			}
		}
		if (crew.stores == null) {
			return false;
		}
		long food = crew.stores.getOrDefault(FOOD, 0L);
		boolean fishing = "fish".equals(crew.mode);
		return food < hands * (fishing ? DAYS_FULL : DAYS_LOW);
	}

	/** Her hands stop fishing (a foe, boarders): every line comes in. */
	static void lines(Ship ship) {
		for (LivingEntity e : ship.crewAboard()) {
			if (e instanceof Mob m && Anglers.angling(m)) {
				Anglers.reelIn(m);
			}
		}
	}

	/** A fish landed aboard: a day's food for a hand, or (her stores full, a smack's) her catch. A pufferfish goes back. */
	static void caught(Crew crew, ItemStack fish) {
		if (fish.is(Items.PUFFERFISH) || fish.isEmpty()) {
			return;
		}
		if (crew.stores == null) {
			crew.stores = new HashMap<>();
		}
		long food = crew.stores.getOrDefault(FOOD, 0L);
		boolean smack = crew.side == Crew.Side.FISHER && "fishing".equals(crew.mode);
		if (!smack || crew.stores.getOrDefault(HUNGRY, 0L) != 0L) {
			crew.stores.put(FOOD, food + fish.getCount());
			crew.stores.remove(HUNGRY);
			return;
		}
		String key = CATCH + BuiltInRegistries.ITEM.getKey(fish.getItem());
		crew.stores.merge(key, (long)fish.getCount(), Long::sum);
	}

	/** Her catch (each kind of fish and how many), taken out of her stores: a smack's, to land. */
	static Map<Item, Integer> takeCatch(Crew crew) {
		Map<Item, Integer> out = new HashMap<>();
		if (crew.stores == null) {
			return out;
		}
		for (Map.Entry<String, Long> e : new ArrayList<>(crew.stores.entrySet())) {
			if (e.getKey().startsWith(CATCH)) {
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(e.getKey().substring(CATCH.length()));
				Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
				if (item != null && item != Items.AIR) {
					out.merge(item, (int)Math.min(Integer.MAX_VALUE, e.getValue()), Integer::sum);
				}
				crew.stores.remove(e.getKey());
			}
		}
		return out;
	}

	/** The place at her rail nearest {@code seat} (in her frame): one with no deck outboard of it. Null with none. */
	private static @Nullable Vec3 rail(Deck deck, Vec3 seat) {
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 p : deck.places.values()) {
			if (Math.abs(p.x) < 1.0 || deck.guns.containsValue(p)) {
				continue;
			}
			int side = p.x > 0.0 ? 1 : -1;
			boolean outboard = false;
			for (int dy = -1; dy <= 1 && !outboard; dy++) {
				outboard = deck.places.containsKey(BlockPos.asLong(Mth.floor(p.x + 0.5) + side, Mth.floor(p.y + 0.01) + dy, Mth.floor(p.z + 0.5)));
			}
			if (outboard) {
				continue;
			}
			double d = p.distanceToSqr(seat);
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}
}
