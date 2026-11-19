package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A camp's hunters. Andy (2026-11-19): "everyone should have to do this or some way of getting food. so you will see them
 * hunting", and "when anyone goes hungry they cant just make the food come out of nowhere". A camp eats what is in its
 * larder ({@link CampLarder}) and nothing else, so when its larder runs short (less meat than it has mouths) one of its
 * band goes out by day after real game ({@link #GAME}: deer, boar, sheep, pigs, cattle, goats, fowl, rabbits) within
 * {@link #RANGE} of the camp, never a town's or a homestead's beasts, nor one on a lead, named or tamed.
 * <ul>
 * <li>They creep up on it, then run it down when it bolts, striking as they reach it.</li>
 * <li>With Jaymo's Kitchen the beast leaves its body: the hunter takes it up and carries it home, and sets it down by
 * the fire, where the camp's cook skins and guts it and roasts it whole on the great spit ({@link Firesides}).
 * Without Kitchen, they gather up its meat where it fell and put it in the camp's chests.</li>
 * <li>Game hunted out near a camp is gone: a camp that has killed everything round it goes hungry, and says so.</li>
 * </ul>
 * Only while a player is near enough to see it ({@link #WATCHED}), as the rest of a camp's life.
 */
final class CampHunters {
	/** Hunts are looked at this often (ticks); a new one is thought about this often (ticks). */
	static final int EVERY = 5, CONSIDER = 200;
	/** A camp with a player this near hunts (blocks); its band is everyone of it this near its centre (blocks). */
	static final double WATCHED = 128.0, BAND = 32.0;
	/** Game is sought this far from the camp (blocks); never this near a realm's seat (blocks). */
	static final double RANGE = 48.0, TOWN_CLEAR = 96.0;
	/** By day only: from dawn to a while before supper (time of day). */
	static final long HUNT_TO = CampSuppers.FROM - 1000L;
	/** A hunt is given up after this long (ticks); after one, the camp waits this long before the next (ticks). */
	static final int HUNT_TICKS = 2400, GAP = 1200;
	/** Creeping on it this far off, then running (blocks); striking this close (blocks), once a second at most (ticks). */
	static final double CREEP = 10.0, STRIKE = 2.2;
	static final int STRIKE_EVERY = 20;
	/** A blow from a hunter with no attack of their own (a villager's hands and knife). */
	static final float BARE_BLOW = 4.0F;
	/** Walking paces: creeping, running it down, carrying it home. */
	static final float CREEP_PACE = 0.45F, RUN_PACE = 0.75F, HOME_PACE = 0.5F;
	/** After the kill, its body (or its meat) is looked for this long (ticks) this near where it fell (blocks); taken up this close (blocks). */
	static final int BODY_WAIT = 80;
	static final double BODY_NEAR = 4.0, TAKE = 1.8;
	/** Home: this near the fire (blocks); what they carried goes down this far off it (blocks). */
	static final double HOME = 3.0, SET_DOWN = 2.0;

	/** What a camp hunts (data/jaymoskingdoms/tags/entity_type/camp_game.json). */
	static final TagKey<EntityType<?>> GAME = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("camp_game"));

	private enum Stage { STALK, FELLED, HOME }

	private static final class Hunt {
		final int camp;
		final BlockPos fire;
		final Mob hunter;
		LivingEntity quarry;
		Stage stage = Stage.STALK;
		final long since;
		long stageAt;
		long struck;
		Vec3 fell = Vec3.ZERO;
		@Nullable Entity body;
		final List<ItemStack> meat = new ArrayList<>();

		Hunt(int camp, BlockPos fire, Mob hunter, LivingEntity quarry, long since) {
			this.camp = camp;
			this.fire = fire;
			this.hunter = hunter;
			this.quarry = quarry;
			this.since = since;
			this.stageAt = since;
		}
	}

	private static final Map<Integer, Hunt> HUNTS = new HashMap<>();
	private static final Map<Mob, Hunt> HUNTERS = new IdentityHashMap<>();
	/** When each camp may next send a hunter out (game time). */
	private static final Map<Integer, Long> NEXT = new HashMap<>();

	private CampHunters() {
	}

	static void register() {
		for (String kind : new String[] {"hunt.out", "hunt.kill", "hunt.back", "hunt.lost", "hunt.none"}) {
			Callouts.LINES.put(kind, 3);
		}
		ServerTickEvents.END_SERVER_TICK.register(CampHunters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HUNTS.clear();
			HUNTERS.clear();
			NEXT.clear();
		});
	}

	/** {@code m} is out hunting for their camp. */
	static boolean busy(Mob m) {
		return HUNTERS.containsKey(m);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Hunt h : new ArrayList<>(HUNTS.values())) {
			step(level, h, now);
		}
		if (server.getTickCount() % CONSIDER != 0) {
			return;
		}
		long dayTime = level.getOverworldClockTime() % 24000L;
		if (dayTime >= HUNT_TO) {
			return;
		}
		for (Map.Entry<String, Camps.CampData> e : Camps.all(server).entrySet()) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			BlockPos center = e.getValue().center();
			if (HUNTS.containsKey(id) || NEXT.getOrDefault(id, Long.MIN_VALUE) > now || !Loaded.made(level, center) || !watched(level, center)) {
				continue;
			}
			consider(level, id, center, now);
		}
	}

	private static boolean watched(ServerLevel level, BlockPos at) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(at, WATCHED)) {
				return true;
			}
		}
		return false;
	}

	/** Whether camp {@code id} needs meat, and if so who goes for what. */
	private static void consider(ServerLevel level, int id, BlockPos center, long now) {
		BlockPos lit = Firesides.findFire(level, center);
		BlockPos fire = lit != null ? lit : center;
		String side = "f:" + id;
		List<Mob> band = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(BAND),
			m -> m.isAlive() && !m.isBaby() && Temperaments.humanoid(m) && Comrades.side(m).equals(side));
		if (band.isEmpty()) {
			return;
		}
		int meat = CampLarder.count(level, fire, stack -> stack.is(KitchenSpits.ROASTABLE));
		if (meat >= band.size() || KitchenCompat.spitCarcassNear(level, Vec3.atCenterOf(fire), Firesides.CARCASS_NEAR) != null) {
			return;
		}
		NEXT.put(id, now + GAP);
		Mob hunter = null;
		for (Mob m : band) {
			if (!Downed.isDowned(m) && Comrades.target(m) == null && !Comrades.busy(m) && !Firesides.sitting(m) && !CampSuppers.eating(m)
				&& !BandLeaders.isLeader(m) && !Captives.bound(m) && !m.isNoAi()) {
				hunter = m;
				break;
			}
		}
		if (hunter == null) {
			return;
		}
		LivingEntity quarry = quarry(level, center);
		if (quarry == null) {
			// hunted out round here
			Callouts.say(hunter, "hunt.none", null, false);
			return;
		}
		Hunt h = new Hunt(id, fire, hunter, quarry, now);
		HUNTS.put(id, h);
		HUNTERS.put(hunter, h);
		Comrades.stand(hunter);
		Callouts.say(hunter, "hunt.out", null, false);
		walk(hunter, quarry.position(), CREEP_PACE);
	}

	/** The nearest game round {@code center} the camp may take: wild, grown, free, on nobody's land. */
	private static @Nullable LivingEntity quarry(ServerLevel level, BlockPos center) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Animal a : level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(RANGE, 16.0, RANGE),
			a -> a.isAlive() && !a.isBaby() && a.getType().builtInRegistryHolder().is(GAME) && !a.hasCustomName() && !(a instanceof Leashable l && l.isLeashed()))) {
			double d = a.distanceToSqr(Vec3.atCenterOf(center));
			if (d >= bestD || claimed(level, a.blockPosition()) || huntedBy(a)) {
				continue;
			}
			best = a;
			bestD = d;
		}
		return best;
	}

	/** Some town's or homestead's: near a realm's seat, or on a player's homestead. */
	private static boolean claimed(ServerLevel level, BlockPos pos) {
		if (Homesteads.ownerAt(level, pos) != null) {
			return true;
		}
		for (Faction f : Factions.all(level.getServer())) {
			if (!f.fallen() && f.type().isRealm() && f.seat().closerThan(pos, TOWN_CLEAR)) {
				return true;
			}
		}
		return false;
	}

	private static boolean huntedBy(Entity a) {
		for (Hunt h : HUNTS.values()) {
			if (h.quarry == a) {
				return true;
			}
		}
		return false;
	}

	private static void step(ServerLevel level, Hunt h, long now) {
		Mob hunter = h.hunter;
		if (!hunter.isAlive() || hunter.isRemoved() || Downed.isDowned(hunter) || Comrades.target(hunter) != null || Captives.bound(hunter)
			|| now - h.since > HUNT_TICKS) {
			end(level, h, now, false);
			return;
		}
		switch (h.stage) {
			case STALK -> {
				LivingEntity q = h.quarry;
				if (q.isRemoved() || !q.isAlive()) {
					if (q.isDeadOrDying() || q.getHealth() <= 0.0F) {
						h.fell = q.position();
						h.stage = Stage.FELLED;
						h.stageAt = now;
						Callouts.say(hunter, "hunt.kill", null, false);
						return;
					}
					end(level, h, now, false);
					return;
				}
				double d = hunter.distanceTo(q);
				hunter.getLookControl().setLookAt(q, 30.0F, 30.0F);
				if (d <= STRIKE) {
					hunter.getNavigation().stop();
					if (now - h.struck >= STRIKE_EVERY) {
						h.struck = now;
						strike(level, hunter, q);
					}
					return;
				}
				// creeping up on it (bent low), till it's near or has seen them and bolted; then running it down
				boolean running = d < CREEP || q.getLastHurtByMob() == hunter;
				hunter.setShiftKeyDown(!running);
				if ((now - h.stageAt) % 10 < EVERY) {
					walk(hunter, q.position(), running ? RUN_PACE : CREEP_PACE);
				}
			}
			case FELLED -> {
				hunter.setShiftKeyDown(false);
				if (h.body == null) {
					Entity body = KitchenCompat.spitCarcassNear(level, h.fell, BODY_NEAR);
					if (body != null) {
						h.body = body;
					} else if (now - h.stageAt < BODY_WAIT && KitchenCompat.makesCorpses()) {
						walk(hunter, h.fell, HOME_PACE);
						return;
					}
				}
				Entity body = h.body;
				if (body != null) {
					if (body.isRemoved() || KitchenCompat.carrier(body) != null && KitchenCompat.carrier(body) != hunter) {
						end(level, h, now, false);
						return;
					}
					if (hunter.distanceTo(body) > TAKE) {
						if ((now - h.stageAt) % 10 < EVERY) {
							walk(hunter, body.position(), HOME_PACE);
						}
						return;
					}
					if (!KitchenCompat.carry(body, hunter)) {
						end(level, h, now, false);
						return;
					}
				} else {
					if (gather(level, h) > 0) {
						return;
					}
					if (h.meat.isEmpty()) {
						if (now - h.stageAt < BODY_WAIT * 2) {
							walk(hunter, h.fell, HOME_PACE);
							return;
						}
						// nothing came of it
						end(level, h, now, false);
						return;
					}
				}
				h.stage = Stage.HOME;
				h.stageAt = now;
				walk(hunter, Vec3.atBottomCenterOf(h.fire), HOME_PACE);
			}
			case HOME -> {
				if (h.body != null && (h.body.isRemoved() || KitchenCompat.carrier(h.body) != hunter)) {
					end(level, h, now, false);
					return;
				}
				if (hunter.position().distanceTo(Vec3.atBottomCenterOf(h.fire)) > HOME) {
					if ((now - h.stageAt) % 20 < EVERY) {
						walk(hunter, Vec3.atBottomCenterOf(h.fire), HOME_PACE);
					}
					return;
				}
				hunter.getNavigation().stop();
				hunter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				Callouts.say(hunter, "hunt.back", null, false);
				end(level, h, now, true);
			}
		}
	}

	/** A blow at the game: with their own attack (and weapon) if they have one, else with their hands and a knife. */
	private static void strike(ServerLevel level, Mob hunter, LivingEntity q) {
		hunter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (hunter.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
			hunter.doHurtTarget(level, q);
		} else {
			q.hurtServer(level, level.damageSources().mobAttack(hunter), BARE_BLOW);
		}
	}

	/**
	 * Without a body to carry: the meat lying where it fell, taken up into their arms as they reach it, walking to the next.
	 * How much is still lying there.
	 */
	private static int gather(ServerLevel level, Hunt h) {
		List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(h.fell)).inflate(BODY_NEAR),
			i -> i.isAlive() && CampLarder.food(i.getItem()));
		int left = 0;
		ItemEntity next = null;
		for (ItemEntity i : lying) {
			if (h.hunter.distanceTo(i) <= TAKE) {
				h.meat.add(i.getItem().copy());
				i.discard();
				level.playSound(null, h.hunter.getX(), h.hunter.getY(), h.hunter.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.5F, 0.9F);
				if (h.hunter.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
					h.hunter.setItemSlot(EquipmentSlot.OFFHAND, h.meat.get(0).copyWithCount(1));
				}
			} else {
				left++;
				if (next == null || h.hunter.distanceToSqr(i) < h.hunter.distanceToSqr(next)) {
					next = i;
				}
			}
		}
		if (next != null) {
			walk(h.hunter, next.position(), HOME_PACE);
		}
		return left;
	}

	/** The hunt's over: home with it ({@code home}: what they carried set down by the fire), or given up where they are. */
	private static void end(ServerLevel level, Hunt h, long now, boolean home) {
		HUNTS.remove(h.camp);
		HUNTERS.remove(h.hunter);
		NEXT.put(h.camp, now + GAP);
		Mob hunter = h.hunter;
		hunter.setShiftKeyDown(false);
		hunter.getNavigation().stop();
		Vec3 down = home ? setDown(h.fire, hunter) : hunter.position();
		if (h.body != null && KitchenCompat.carrier(h.body) == hunter) {
			KitchenCompat.throwAt(h.body, down);
		}
		if (!h.meat.isEmpty()) {
			if (hunter.getItemBySlot(EquipmentSlot.OFFHAND).is(h.meat.get(0).getItem())) {
				hunter.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			}
			for (ItemStack stack : h.meat) {
				if (home) {
					CampLarder.put(level, h.fire, stack);
				} else if (hunter.isAlive()) {
					hunter.spawnAtLocation(level, stack);
				}
			}
			h.meat.clear();
		}
		if (!home && hunter.isAlive() && h.stage == Stage.STALK) {
			Callouts.say(hunter, "hunt.lost", null, false);
		}
	}

	/** Where they set it down: on their side of the fire, a little off it. */
	private static Vec3 setDown(BlockPos fire, Mob hunter) {
		Vec3 c = Vec3.atBottomCenterOf(fire);
		Vec3 d = hunter.position().subtract(c).multiply(1.0, 0.0, 1.0);
		if (d.lengthSqr() < 1.0E-4) {
			d = new Vec3(1.0, 0.0, 0.0);
		}
		return c.add(d.normalize().scale(SET_DOWN));
	}

	private static void walk(Mob m, Vec3 to, float pace) {
		if (m instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(to), pace, 0);
		} else {
			m.getNavigation().moveTo(to.x, to.y, to.z, pace / 0.5F);
		}
	}
}
