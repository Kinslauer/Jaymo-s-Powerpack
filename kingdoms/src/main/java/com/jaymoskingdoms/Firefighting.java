package com.jaymoskingdoms;

import com.jaymoskingdoms.disasters.Calamities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.11) A settlement's people answer a fire. A fire lit in or near a settlement ({@code BaseFireBlockMixin}) is
 * watched until it's out. Each grown villager near it chooses once how to answer: some fight it (they run to it and
 * put it out, either beating it out, now and then knocking out the burning block under it so it can't spread, or
 * (0.7.300) throwing a water bottle on it, which splashes out the fire round it; no more buckets of water left on the
 * ground), some run to the nearest child and lead it away, the rest run
 * from it. Children always run. The kingdom's folk (guards, builders, farmers...) not already fighting put it out, and
 * (0.5.42) so do its iron golems, beating it out with their fists.
 * <p>
 * (0.5.48) A fire kept burning on purpose (on netherrack or anything else that burns forever: a fire pit) is left
 * alone: it's where golems and guards drop corpses ({@link Undertakers}). Anyone sent to a fire puts down the corpse
 * they were carrying first.
 * <p>
 * Andy (2026-11-26): "it should spread from person to person until everyone has a hand in fighting the fire if
 * possible". The word of a fire runs through the whole settlement ({@link FireAlarm}) and everyone grown and able turns
 * out: the brave and the guards as before, and now everyone else too, but for a few (and the timid) who take the
 * children away from it. With {@link #LINE_MIN} or more at one fire and water within {@link #LINE_WATER}, the ones past
 * the front {@link #FRONT} form a bucket line from the water: each pass runs down the line hand to hand and the front
 * one throws it. Anyone already running from a monster or a twister ({@link Frights}) leaves the fire to the others.
 */
public final class Firefighting {
	/** Villagers and folk this close to a fire answer it; a fire this close to a settlement's seat is watched. */
	static final double REACH = 18.0, WATCHED = 96.0;
	/** How often fires are answered (ticks); the most watched at once, and answered in one go. */
	static final int EVERY = 10, MOST_WATCHED = 512, MOST_ANSWERED = 48;
	/** Close enough to put it out (blocks). */
	static final double PUT_OUT = 3.2;
	/** Firefighters go to a spot this far short of the fire (they won't walk into it). */
	static final double STAND_OFF = 2.0;
	/** How far they run; how long a thrown bottle has to put its fire out before the fire is answered again (ticks). */
	static final int RUN = 12, SPLASH_TICKS = 40;
	/** The share of grown villagers (not the timid, who always do when one's near) who look after a child rather than fight it; a choice is kept this long. */
	static final float GUARD = 0.15F;
	/** (0.7.31) With a wildfire bearing down ({@code Calamities}), this share fight it, every one with water. */
	static final float WILDFIRE_FIGHT = 0.8F;
	static final long CHOICE_TICKS = 600L;
	/** How fast they go: villagers fighting or fleeing, and folk. */
	static final float VILLAGER_SPEED = 0.7F;
	static final double FOLK_SPEED = 1.15;
	/** How fast golems go to a fire. */
	static final double GOLEM_SPEED = 1.0;
	/** How hard a water bottle is thrown at a fire. */
	static final float THROW_POWER = 0.5F;
	/** A bucket line forms with this many fighting one fire (the nearest {@link #FRONT} throw, the rest pass), from water this near it (blocks). */
	static final int LINE_MIN = 4, FRONT = 2;
	static final double LINE_WATER = 24.0;
	/** How long a look for water near a fire holds (ticks); how far from the fire's level the water may lie (blocks). */
	static final long WATER_LOOK = 600L;
	static final int WATER_RISE = 6;
	/** How often a line calls out (ticks), and its lines (lang {@code talk.jaymoskingdoms.fire_alarm.line.<n>}). */
	static final long LINE_CALL_EVERY = 400L;
	static final int LINE_LINES = 4;

	enum Answer { FIGHT, GUARD, RUN }

	private record Choice(Answer answer, long until) {
	}

	private record Splash(BlockPos fire, long until) {
	}

	/** The water found nearest a fire's cell (or none), looked for until {@code until}. */
	private record Water(@Nullable BlockPos at, long until) {
	}

	private static final Set<BlockPos> FIRES = new HashSet<>();
	private static final Map<UUID, Choice> CHOICES = new HashMap<>();
	private static final List<Splash> SPLASHES = new ArrayList<>();
	/** Water near each fire cell (8 blocks a side), its bucket line's last call; and this pass's cells with a line, and who stands in one. */
	private static final Map<Long, Water> WATER = new HashMap<>();
	private static final Map<Long, Long> LINE_CALLED = new HashMap<>();
	private static final Set<Long> LINE_DONE = new HashSet<>();
	private static final Set<UUID> LINED = new HashSet<>();

	private Firefighting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Firefighting::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIRES.clear();
			CHOICES.clear();
			SPLASHES.clear();
			WATER.clear();
			LINE_CALLED.clear();
		});
		FireAlarm.register();
	}

	/** The fires being watched just now (a copy). */
	static List<BlockPos> watched() {
		return new ArrayList<>(FIRES);
	}

	/** A fire was lit at {@code pos} ({@code BaseFireBlockMixin}): watched if it's in or near a settlement. */
	public static void lit(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || FIRES.size() >= MOST_WATCHED || server != Factions.level(server.getServer())) {
			return;
		}
		if (server.getBlockState(pos.below()).is(BlockTags.INFINIBURN_OVERWORLD)) {
			// (0.5.48) a kept fire (a fire pit), not one to put out
			return;
		}
		Faction near = Factions.around(server.getServer(), server, pos, WATCHED);
		if (near != null) {
			FIRES.add(pos.immutable());
			// (0.7.530) enough fires in a town are a fire to recover from
			TownTroubles.fire(server.getServer(), near);
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		// a thrown bottle that missed: the fire is answered again
		for (Iterator<Splash> it = SPLASHES.iterator(); it.hasNext(); ) {
			Splash s = it.next();
			if (now >= s.until()) {
				if (com.jaymoskingdoms.Loaded.made(level, s.fire()) && level.getBlockState(s.fire()).is(BlockTags.FIRE)) {
					FIRES.add(s.fire());
				}
				it.remove();
			}
		}
		if (now % EVERY != 0 || FIRES.isEmpty()) {
			return;
		}
		FIRES.removeIf(p -> {
			if (!com.jaymoskingdoms.Loaded.made(level, p)) {
				return true;
			}
			if (level.getBlockState(p).is(BlockTags.FIRE)) {
				return false;
			}
			// (0.7.61) out, and not by our people's hands (theirs are struck off as they do it): a player's, maybe
			Deeds.fireOut(level, p);
			return true;
		});
		CHOICES.values().removeIf(c -> c.until() < now);
		WATER.values().removeIf(w -> w.until() < now);
		LINE_CALLED.values().removeIf(t -> now - t > LINE_CALL_EVERY * 3);
		LINE_DONE.clear();
		LINED.clear();
		int answered = 0;
		for (BlockPos fire : new ArrayList<>(FIRES)) {
			if (answered++ >= MOST_ANSWERED) {
				break;
			}
			answer(level, fire, now);
		}
	}

	/** Everyone near {@code fire} answers it. */
	private static void answer(ServerLevel level, BlockPos fire, long now) {
		AABB near = new AABB(fire).inflate(REACH);
		if (LINE_DONE.add(cell(fire)) && line(level, fire, now)) {
			// (the line's front threw: this fire's answered for now)
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, near, LivingEntity::isAlive)) {
			if (Theft.rioting(v) || LINED.contains(v.getUUID())) {
				// (busy with the arsonist, or passing water in a line)
				continue;
			}
			if (v.isBaby()) {
				run(level, v, fire);
				continue;
			}
			if (Frights.afraid(v.getUUID()) || McaCompat.panicking(v)) {
				// (running from a monster or a twister: that comes first)
				continue;
			}
			Choice choice = choice(level, v, fire, now);
			switch (choice.answer()) {
				case FIGHT -> {
					Undertakers.calledAway(level, v);
					if (close(v, fire)) {
						putOut(level, v, fire, now);
						return;
					}
					BehaviorUtils.setWalkAndLookTargetMemories(v, beside(v, fire), VILLAGER_SPEED, 0);
				}
				case GUARD -> guard(level, v, fire);
				default -> run(level, v, fire);
			}
		}
		for (Folk folk : level.getEntitiesOfClass(Folk.class, near, f -> f.isAlive() && f.getTarget() == null && !Theft.rioting(f) && !LINED.contains(f.getUUID())
			&& ours(level, f, fire))) {
			Undertakers.calledAway(level, folk);
			if (close(folk, fire)) {
				putOut(level, folk, fire, now);
				return;
			}
			BlockPos to = beside(folk, fire);
			folk.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, FOLK_SPEED);
		}
		// (0.5.42) golems not already fighting stride over and beat it out with their fists
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, near, g -> g.isAlive() && g.getTarget() == null)) {
			Undertakers.calledAway(level, golem);
			if (close(golem, fire)) {
				beat(level, golem, fire);
				return;
			}
			BlockPos to = beside(golem, fire);
			golem.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, GOLEM_SPEED);
		}
	}

	/**
	 * (0.7.62) Folk fight only their own side's fires: never a raider or a war party's soldier (they set them, and leave
	 * them burning, theirs or the town's), nor anyone standing on enemy land.
	 */
	private static boolean ours(ServerLevel level, Folk folk, BlockPos fire) {
		return !Assault.attacker(folk) && !Assault.enemyLand(level, folk, fire);
	}

	/** {@code golem} beats out {@code fire}: an arm swung down on it (no water, nothing knocked out). */
	private static void beat(ServerLevel level, IronGolem golem, BlockPos fire) {
		golem.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5);
		// (the golem's own attack swing and its sound)
		level.broadcastEntityEvent(golem, EntityEvent.START_ATTACKING);
		level.setBlock(fire, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		ForestFireCompat.douse(level, fire, 1);
		level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.2F);
		FIRES.remove(fire);
	}

	/** {@code v}'s answer to the fire, chosen now if it hasn't one (said with a cry), kept {@link #CHOICE_TICKS}. */
	private static Choice choice(ServerLevel level, Villager v, BlockPos fire, long now) {
		Choice choice = CHOICES.get(v.getUUID());
		if (choice == null) {
			choice = new Choice(choose(level, v), now + CHOICE_TICKS);
			CHOICES.put(v.getUUID(), choice);
			JaymosKingdoms.LOGGER.debug("a villager at {} answers the fire at {}: {}", v.blockPosition(), fire, choice.answer());
			level.playSound(null, v.blockPosition(), choice.answer() == Answer.RUN ? SoundEvents.VILLAGER_HURT : SoundEvents.VILLAGER_NO,
				SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		return choice;
	}

	/**
	 * How a grown villager answers: fights it, or looks after a child (if one's near). Andy once had "only the brave and
	 * guards should ever fight"; now (2026-11-26) "everyone has a hand in fighting the fire if possible": the brave and
	 * the guards always fight, the timid take a child away if one's near, and of the rest only a few do.
	 */
	private static Answer choose(ServerLevel level, Villager v) {
		if (Temperaments.fearless(v) || BlastFright.standsGround(v)) {
			return Answer.FIGHT;
		}
		if ((Temperaments.runs(v) || level.getRandom().nextFloat() < GUARD) && child(level, v) != null) {
			return Answer.GUARD;
		}
		return Answer.FIGHT;
	}

	/**
	 * {@link FireAlarm}: {@code v} has the word of {@code fire} and is still a way off: one who'll fight it hurries to
	 * {@code to}; one who'll take a child away goes for the child. Children don't come.
	 */
	static void turnOut(ServerLevel level, Villager v, BlockPos fire, BlockPos to, float speed, long now) {
		if (v.isBaby()) {
			return;
		}
		Choice choice = choice(level, v, fire, now);
		if (choice.answer() == Answer.FIGHT) {
			Undertakers.calledAway(level, v);
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, speed, 0);
		} else if (choice.answer() == Answer.GUARD) {
			guard(level, v, fire);
		}
	}

	private static @Nullable Villager child(ServerLevel level, Villager v) {
		Villager best = null;
		double bestD = REACH * REACH;
		for (Villager c : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(REACH), x -> x.isAlive() && x.isBaby())) {
			double d = c.distanceToSqr(v);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	/** A spot {@link #STAND_OFF} short of {@code fire}, on {@code who}'s side of it. */
	static BlockPos beside(LivingEntity who, BlockPos fire) {
		Vec3 off = who.position().subtract(Vec3.atBottomCenterOf(fire)).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 at = Vec3.atBottomCenterOf(fire).add(off.normalize().scale(STAND_OFF));
		return BlockPos.containing(at.x, fire.getY(), at.z);
	}

	private static boolean close(LivingEntity who, BlockPos fire) {
		return who.distanceToSqr(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5) <= PUT_OUT * PUT_OUT;
	}

	/** To the nearest child, then away with it. */
	private static void guard(ServerLevel level, Villager v, BlockPos fire) {
		Villager c = child(level, v);
		if (c == null) {
			run(level, v, fire);
			return;
		}
		if (v.distanceToSqr(c) > 4.0) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(c, false), VILLAGER_SPEED, 1);
			return;
		}
		BlockPos away = away(level, c, fire);
		BehaviorUtils.setWalkAndLookTargetMemories(v, away, VILLAGER_SPEED, 2);
		BehaviorUtils.setWalkAndLookTargetMemories(c, away, VILLAGER_SPEED, 2);
	}

	private static void run(ServerLevel level, Villager v, BlockPos fire) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, away(level, v, fire), VILLAGER_SPEED, 2);
	}

	/** A spot on the ground {@link #RUN} blocks from {@code fire}, the way {@code who} already is from it. */
	private static BlockPos away(ServerLevel level, LivingEntity who, BlockPos fire) {
		Vec3 off = who.position().subtract(Vec3.atCenterOf(fire)).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 to = who.position().add(off.normalize().scale(RUN));
		int x = (int)Math.floor(to.x), z = (int)Math.floor(to.z);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** {@code who} puts out {@code fire}: a water bottle thrown on it, or beaten out (now and then knocking out what burns under it). */
	private static void putOut(ServerLevel level, Mob who, BlockPos fire, long now) {
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		who.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5);
		if (level.getRandom().nextBoolean() || Calamities.wildfireNear(fire)) {
			// (0.7.31) against a wildfire, always water: the whole settlement passing bottles along
			throwWater(level, who, fire);
			SPLASHES.add(new Splash(fire, now + SPLASH_TICKS));
		} else {
			level.setBlock(fire, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			// Peterwolf's fire (when it's in the pack) goes out with it, or the hot ground lights it again; a thrown
			// bottle does the same where it splashes (mixin/forestfire/SplashDousesForestFireMixin)
			ForestFireCompat.douse(level, fire, 1);
			level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.4F);
			// what's burning under it, knocked out so it can't spread from there
			BlockPos under = fire.below();
			BlockState fuel = level.getBlockState(under);
			if (level.getRandom().nextBoolean() && fuel.ignitedByLava() && burningBeside(level, under)) {
				level.destroyBlock(under, true, who);
			}
		}
		FIRES.remove(fire);
	}

	/**
	 * (0.7.300) {@code who} throws a splash water bottle at {@code fire}: vanilla's splash puts out the fire it lands on and
	 * beside it (Powers widens that to two blocks round) and anyone burning near it.
	 */
	private static void throwWater(ServerLevel level, Mob who, BlockPos fire) {
		ThrownSplashPotion bottle = new ThrownSplashPotion(level, who, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
		double dx = fire.getX() + 0.5 - who.getX(), dz = fire.getZ() + 0.5 - who.getZ();
		double dy = fire.getY() + 0.3 - bottle.getY();
		// a lob: up by a fifth of the distance to carry it against the bottle's gravity
		bottle.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, THROW_POWER, 1.0F);
		level.addFreshEntity(bottle);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
	}

	// ---- bucket lines ----------------------------------------------------------------------------------------------

	/** {@code fire}'s cell: 8 blocks a side, one bucket line to a cell. */
	private static long cell(BlockPos fire) {
		return BlockPos.asLong(fire.getX() >> 3, fire.getY() >> 3, fire.getZ() >> 3);
	}

	/**
	 * Enough hands at {@code fire} and water near: the nearest {@link #FRONT} fight it as ever, the rest stand in a line to
	 * the water. Each pass a bucket goes hand to hand from the water end, one swing a pass, and when it reaches the front
	 * the nearest throws it. True if the front threw just now.
	 */
	private static boolean line(ServerLevel level, BlockPos fire, long now) {
		AABB near = new AABB(fire).inflate(REACH);
		List<Mob> hands = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, near, v -> v.isAlive() && !v.isBaby() && !Theft.rioting(v)
			&& !Frights.afraid(v.getUUID()))) {
			Choice c = CHOICES.get(v.getUUID());
			if (c != null && c.answer() == Answer.FIGHT) {
				hands.add(v);
			}
		}
		for (Folk folk : level.getEntitiesOfClass(Folk.class, near, f -> f.isAlive() && f.getTarget() == null && !Theft.rioting(f) && ours(level, f, fire))) {
			hands.add(folk);
		}
		if (hands.size() < LINE_MIN) {
			return false;
		}
		BlockPos water = water(level, fire, now);
		if (water == null) {
			return false;
		}
		Vec3 at = Vec3.atBottomCenterOf(fire);
		hands.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
		Mob front = hands.get(0);
		List<Mob> line = hands.subList(FRONT, hands.size());
		int n = line.size();
		// the line runs from beside the fire to a step short of the water
		Vec3 head = Vec3.atBottomCenterOf(beside(front, fire));
		Vec3 toFire = head.subtract(Vec3.atBottomCenterOf(water)).multiply(1.0, 0.0, 1.0);
		Vec3 tail = Vec3.atBottomCenterOf(water).add(toFire.lengthSqr() < 1.0E-3 ? Vec3.ZERO : toFire.normalize().scale(1.5));
		// (the line stands in a fixed order, so nobody swaps places each pass: by how far each is from the water)
		List<Mob> order = new ArrayList<>(line);
		order.sort((a, b) -> Double.compare(b.distanceToSqr(tail), a.distanceToSqr(tail)));
		long pass = (now / EVERY) % (n + 1);
		for (int k = 0; k < n; k++) {
			Mob m = order.get(k);
			LINED.add(m.getUUID());
			if (m instanceof net.minecraft.world.entity.PathfinderMob pm) {
				Undertakers.calledAway(level, pm);
			}
			// slot k: k = 0 next to the front, k = n - 1 at the water
			Vec3 spot = head.lerp(tail, (k + 1.0) / n);
			BlockPos to = Construction.standable(level, BlockPos.containing(spot.x, fire.getY(), spot.z), 3);
			if (m.distanceToSqr(Vec3.atBottomCenterOf(to)) > 2.25) {
				if (m instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, to, VILLAGER_SPEED, 0);
				} else {
					m.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, FOLK_SPEED);
				}
				continue;
			}
			m.getNavigation().stop();
			// facing up the line, toward the fire
			Mob next = k == 0 ? front : order.get(k - 1);
			m.getLookControl().setLookAt(next, 30.0F, 30.0F);
			if (pass == n - 1 - k) {
				m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				if (k == n - 1) {
					level.playSound(null, water, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 0.6F, 1.0F);
				}
			}
		}
		Long called = LINE_CALLED.get(cell(fire));
		if (called == null || now - called >= LINE_CALL_EVERY) {
			LINE_CALLED.put(cell(fire), now);
			Speech.say(level, order.get(n - 1), Component.translatable("talk.jaymoskingdoms.fire_alarm.line." + level.getRandom().nextInt(LINE_LINES)),
				Speech.GOLD, FireAlarm.SHOUT * 2.0);
		}
		if (pass == n && front.distanceToSqr(Vec3.atCenterOf(fire)) <= (PUT_OUT + STAND_OFF) * (PUT_OUT + STAND_OFF)) {
			// the bucket's reached the front: it's thrown on
			front.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			front.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5);
			throwWater(level, front, fire);
			SPLASHES.add(new Splash(fire, now + SPLASH_TICKS));
			FIRES.remove(fire);
			return true;
		}
		return false;
	}

	/**
	 * The water nearest {@code fire} within {@link #LINE_WATER}: open water (a well, a pond, a river) or a filled cauldron
	 * at the surface, not too far above or below the fire. Null if none. Looked for once a while for each cell.
	 */
	private static @Nullable BlockPos water(ServerLevel level, BlockPos fire, long now) {
		long cell = cell(fire);
		Water had = WATER.get(cell);
		if (had != null && had.until() > now) {
			return had.at();
		}
		BlockPos best = null;
		double bestD = LINE_WATER * LINE_WATER;
		int r = (int)LINE_WATER;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int x = fire.getX() + dx, z = fire.getZ() + dz;
				if (!com.jaymoskingdoms.Loaded.made(level, x, z)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				if (Math.abs(top - fire.getY()) > WATER_RISE) {
					continue;
				}
				BlockPos p = new BlockPos(x, top, z);
				var fluid = level.getFluidState(p);
				if (fluid.is(FluidTags.WATER) && fluid.isSource() || level.getBlockState(p).is(Blocks.WATER_CAULDRON)) {
					double d = p.distSqr(fire);
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		WATER.put(cell, new Water(best, now + WATER_LOOK));
		return best;
	}

	/** Fire is still at {@code pos}'s side (it's burning). */
	private static boolean burningBeside(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(pos.relative(d)).is(BlockTags.FIRE)) {
				return true;
			}
		}
		return false;
	}
}
