package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The underworld's second stage, the law's half (Andy's pick from the PR #525 plan: "guard raids on dens that run too hot").
 *
 * <p>A den that is a known hotbed and runs past {@link #RAID_HEAT} draws the watch: each look there is a chance
 * ({@link #RAID_CHANCE}, doubled while the watch is cracking down) that a raid is called. Word reaches the den first: for
 * {@link #WARNING} the fence (or whoever of the den's hands is there) tells anyone in the room the watch is coming.
 *
 * <p>Then, if anyone is about to see it, up to {@link #RAIDERS} of the realm's free guards within {@link #MUSTER} of the
 * hatch march on it ({@link Raid}): they throw the hatch open, climb down the ladder, and shout the room still. Anyone found
 * in the den is charged ({@code Theft.Crime#DEALING}, "being found in a speakeasy"), so they're hunted and, caught or
 * yielding, sentenced by {@link Busts} as ever. Every barrel in the den and its fight pit is emptied of contraband. Each guard
 * takes one of the den's hands (the fence, the dice-thrower, the bookie, the pit's attendant, its fighters off the card), walks
 * him to the foot of the ladder, and they climb out one after the other; up top the man is handed to the realm's justice
 * ({@link Arrests}: marched before the ruler and sentenced, pardon to the block). A raid takes {@link #TIMEOUT} at most.
 *
 * <p>If every guard on the raid is downed or killed first, the den has fought them off: it stays open, the watch cracks down,
 * and the Tidings say so. Otherwise the den is <b>shut</b> for {@link #SHUT}: its hatch is nailed down under an iron trapdoor,
 * no hand is hired, no smuggler sent, it's no longer a known hotbed, and its heat drops to {@link #HEAT_AFTER}. Hands the
 * raid had no guard for, and any found below while it's shut, are taken off by the watch (removed from the world). After that
 * the trade comes back: the hatch is a wooden one again and the den hires anew.
 *
 * <p>With nobody near the den when the raid falls due, or no free guard to send, it happens out of sight: the den is shut
 * just the same, and what's below is cleared when someone next comes near.
 */
final class DenRaids {
	/** A den this hot (and a known hotbed) may be raided; the chance each look ({@link Underworld#CHECK_TICKS}), doubled in a crackdown. */
	static final int RAID_HEAT = 70;
	static final float RAID_CHANCE = 0.03F;
	/** How long the den is warned before the watch comes (ticks). */
	static final long WARNING = 1200L;
	/** How many guards go on a raid, from how far round the hatch. */
	static final int RAIDERS = 4;
	static final double MUSTER = 96.0;
	/** How long a raid may take before whatever's left is finished out of sight (ticks). */
	static final long TIMEOUT = 20L * 240;
	/** A raided den is shut this long (ticks); its heat after. */
	static final long SHUT = 24000L * 3;
	static final int HEAT_AFTER = 5;
	/** A guard takes a hand from this close; climbs this fast (blocks a tick); is at the ladder's foot this close. */
	static final double TAKE_REACH = 2.0, CLIMB = 0.15, FOOT = 0.9;
	/** How close to the hatch a marching guard is when he throws it open and goes down; how long he may take getting to the ladder's foot (ticks). */
	static final double AT_HATCH = 2.5;
	static final int FOOT_TICKS = 200;
	/** A guard's pace on a raid; a led man's. */
	static final double GUARD_SPEED = 1.0;
	static final float LED_SPEED = 0.7F;
	/** How far the raid's words carry; lines each (lang {@code raid.jaymoskingdoms.<key>.<n>}). */
	static final double SPEECH = 16.0;
	static final int LINES = 3;

	private enum Step {
		MARCH, DOWN, IN, OUT, UP, TOP, DONE
	}

	/** One guard on a raid, and the man he's taken. */
	private static final class Raider {
		final PathfinderMob guard;
		Step step = Step.MARCH;
		long since;
		@Nullable Villager hand;
		/** What the man was in the den (his tag), given back if his guard goes down before they're out. */
		@Nullable String handTag;
		Step handStep = Step.IN;

		Raider(PathfinderMob guard, long now) {
			this.guard = guard;
			this.since = now;
		}
	}

	/** A raid under way, seen. */
	private static final class Raid {
		final int realm;
		final BlockPos hatch;
		final long started;
		final List<Raider> raiders = new ArrayList<>();
		boolean inside;
		int lost;

		Raid(int realm, BlockPos hatch, long started) {
			this.realm = realm;
			this.hatch = hatch;
			this.started = started;
		}
	}

	/** Realm → its raid, while one is on. Not saved: a raid cut short by a restart is called again (the den's {@code raidAt} stays set till it's over). */
	private static final Map<Integer, Raid> RAIDS = new HashMap<>();
	/** Realm → when its den was last warned (one warning a raid). */
	private static final Map<Integer, Long> WARNED = new HashMap<>();

	private DenRaids() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			long now = level.getGameTime();
			for (Iterator<Raid> it = RAIDS.values().iterator(); it.hasNext();) {
				Raid r = it.next();
				if (!step(level, r, now)) {
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RAIDS.clear();
			WARNED.clear();
		});
	}

	/** {@code realm}'s den is being raided just now (seen). */
	static boolean live(int realm) {
		return RAIDS.containsKey(realm);
	}

	/** {@code id} is a guard on a raid, or a man one has taken (the den's housekeeping leaves them be). */
	static boolean involved(UUID id) {
		for (Raid r : RAIDS.values()) {
			for (Raider x : r.raiders) {
				if (x.guard.getUUID().equals(id) || x.hand != null && x.hand.getUUID().equals(id)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- calling a raid ------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Underworld}'s tick, every built den) the watch and {@code f}'s den: a shut den kept shut and cleared, or
	 * opened again when its time is up; a hot one may draw a raid, warned first, then carried out.
	 */
	static void check(ServerLevel level, Faction f, Underworld.Den d, long now) {
		MinecraftServer server = level.getServer();
		boolean loaded = level.isPositionEntityTicking(d.hatch());
		if (d.shut() > 0L) {
			if (d.shutAt(now)) {
				if (loaded) {
					seal(level, d.hatch(), true);
					sweep(level, f, d, true);
				}
				return;
			}
			if (!loaded) {
				// (it opens again when someone's about to see the hatch put back)
				return;
			}
			seal(level, d.hatch(), false);
			Underworld.put(server, Underworld.den(server, f.id()).reopened());
			return;
		}
		if (RAIDS.containsKey(f.id())) {
			return;
		}
		if (d.raidAt() > 0L) {
			if (now < d.raidAt()) {
				warn(level, f, d, now);
				return;
			}
			start(level, f, d, now, loaded);
			return;
		}
		if (!d.known() || d.heat() < RAID_HEAT) {
			return;
		}
		float chance = RAID_CHANCE * (Underworld.crackdown(server, f.id()) ? 2.0F : 1.0F);
		if (level.getRandom().nextFloat() < chance) {
			Underworld.put(server, d.withRaidAt(now + WARNING));
			JaymosKingdoms.LOGGER.info("The watch of {} means to raid its speakeasy", f.name());
		}
	}

	/** Word from upstairs: the den's hands tell whoever's in the room. */
	private static void warn(ServerLevel level, Faction f, Underworld.Den d, long now) {
		Long last = WARNED.get(f.id());
		if (last != null && last >= d.raidAt() - WARNING || !level.isPositionEntityTicking(d.hatch())) {
			return;
		}
		AABB room = Speakeasy.room(d.hatch()).inflate(1.0);
		if (level.getEntitiesOfClass(ServerPlayer.class, room, p -> !p.isSpectator()).isEmpty()) {
			return;
		}
		Villager hand = level.getEntitiesOfClass(Villager.class, room, v -> v.isAlive() && Underworld.hand(v) != null).stream().findFirst().orElse(null);
		if (hand == null) {
			return;
		}
		WARNED.put(f.id(), now);
		Speech.say(level, hand, Component.translatable("raid.jaymoskingdoms.warning." + level.getRandom().nextInt(LINES)), Speech.GOLD, Underworld.DEN_SPEECH);
		level.playSound(null, hand.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.2F);
	}

	/** The raid falls due: carried out where it's seen, else out of sight. */
	private static void start(ServerLevel level, Faction f, Underworld.Den d, long now, boolean loaded) {
		List<PathfinderMob> guards = loaded ? muster(level, f, d.hatch()) : List.of();
		if (guards.isEmpty()) {
			JaymosKingdoms.LOGGER.info("The watch of {} raided its speakeasy out of sight", f.name());
			shut(level, f, now);
			return;
		}
		Raid r = new Raid(f.id(), d.hatch(), now);
		for (PathfinderMob g : guards) {
			r.raiders.add(new Raider(g, now));
		}
		// (its raidAt stays set until it's over: a raid cut short by a restart is called again)
		RAIDS.put(f.id(), r);
		Speech.say(level, guards.getFirst(), Component.translatable("raid.jaymoskingdoms.muster." + level.getRandom().nextInt(LINES)), Speech.RED, SPEECH);
		JaymosKingdoms.LOGGER.info("The watch of {} raids its speakeasy with {} guards", f.name(), guards.size());
	}

	/** Up to {@link #RAIDERS} of {@code f}'s free guards near the hatch, nearest first. */
	private static List<PathfinderMob> muster(ServerLevel level, Faction f, BlockPos hatch) {
		List<PathfinderMob> out = new ArrayList<>();
		List<Worker> near = new ArrayList<>(Underworld.guards(level, f, new AABB(hatch).inflate(MUSTER)));
		near.sort(java.util.Comparator.comparingDouble(w -> w.mob().distanceToSqr(Vec3.atCenterOf(hatch))));
		for (Worker w : near) {
			PathfinderMob g = w.mob();
			if (out.size() >= RAIDERS) {
				break;
			}
			if (Arrests.busy(g) || Arrests.held(g) || Rulers.target(g) != null || Retinue.sent(g) || Royals.escortOf(w) != null || g.isPassenger()
				|| involved(g.getUUID())) {
				continue;
			}
			out.add(g);
		}
		return out;
	}

	// ---- the raid, seen ------------------------------------------------------------------------------------------------

	/** One tick of raid {@code r}; false when it's over. */
	private static boolean step(ServerLevel level, Raid r, long now) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, r.realm);
		if (f == null || f.fallen()) {
			r.raiders.forEach(x -> release(x));
			return false;
		}
		if (now - r.started > TIMEOUT || !level.isPositionEntityTicking(r.hatch)) {
			// (it's gone on too long, or out of sight: finished where nobody sees)
			r.raiders.forEach(x -> {
				if (x.hand != null && x.handStep != Step.TOP && x.hand.isAlive()) {
					McaCompat.vanish(x.hand);
				}
				release(x);
			});
			shut(level, f, now);
			return false;
		}
		boolean anyLeft = false;
		for (Raider x : r.raiders) {
			if (x.step == Step.DONE) {
				continue;
			}
			if (!x.guard.isAlive() || x.guard.isRemoved() || Downed.isDowned(x.guard)) {
				// a guard down: the man he'd taken is free, and the den's again
				x.step = Step.DONE;
				r.lost++;
				release(x);
				if (x.hand != null && x.handTag != null && x.handStep != Step.TOP && x.hand.isAlive()) {
					x.hand.addTag(x.handTag);
				}
				x.hand = null;
				continue;
			}
			anyLeft = true;
			guard(level, f, r, x, now);
			if (x.hand != null) {
				led(level, f, r, x, now);
			}
		}
		if (anyLeft) {
			return true;
		}
		if (r.lost >= r.raiders.size()) {
			// every one of them downed: the den has fought the watch off
			Underworld.Den d = Underworld.den(server, f.id());
			if (d != null) {
				Underworld.put(server, d.withRaidAt(0L));
			}
			Underworld.crackDown(level, f, null, null);
			Post.news(server, Component.translatable("news.jaymoskingdoms.underworld.raid_beaten", f.name()));
			JaymosKingdoms.LOGGER.info("{}'s speakeasy beat off the watch's raid", f.name());
			return false;
		}
		// (hands nobody could take are rounded up after)
		shut(level, f, now);
		return false;
	}

	/** A guard's part: to the hatch, down the ladder, a man taken, back to the ladder's foot, up and out. */
	private static void guard(ServerLevel level, Faction f, Raid r, Raider x, long now) {
		PathfinderMob g = x.guard;
		BlockPos hatch = r.hatch;
		switch (x.step) {
			case MARCH -> {
				Vec3 top = Vec3.atBottomCenterOf(hatch.above());
				double dx = g.getX() - top.x, dz = g.getZ() - top.z;
				if (dx * dx + dz * dz <= AT_HATCH * AT_HATCH && Math.abs(g.getY() - top.y) <= 2.0) {
					openHatch(level, hatch);
					climbing(g, true);
					g.setPos(hatch.getX() + 0.5, hatch.getY() - 1.0, hatch.getZ() + 0.5);
					x.step = Step.DOWN;
				} else if (now % 10 == 0) {
					walk(g, top, (float)GUARD_SPEED);
				}
			}
			case DOWN -> {
				double floor = Speakeasy.room(hatch).minY;
				if (climb(g, hatch, -CLIMB, floor)) {
					climbing(g, false);
					x.step = Step.IN;
					x.since = now;
					if (!r.inside) {
						r.inside = true;
						burst(level, f, r, g);
					}
				}
			}
			case IN -> {
				if (x.hand != null) {
					x.step = Step.OUT;
					x.since = now;
					return;
				}
				Villager next = nextHand(level, r);
				if (next == null) {
					// (nobody left for him: back up)
					x.step = Step.OUT;
					x.since = now;
					return;
				}
				if (g.distanceTo(next) <= TAKE_REACH) {
					take(level, x, next);
					x.step = Step.OUT;
					x.since = now;
				} else if (now % 10 == 0) {
					walk(g, next.position(), (float)GUARD_SPEED);
				}
			}
			case OUT -> {
				Vec3 foot = foot(hatch);
				double dx = g.getX() - foot.x, dz = g.getZ() - foot.z;
				if (dx * dx + dz * dz <= FOOT * FOOT || now - x.since > FOOT_TICKS) {
					climbing(g, true);
					g.setPos(foot.x, foot.y, foot.z);
					x.step = Step.UP;
				} else if (now % 10 == 0) {
					walk(g, foot, (float)GUARD_SPEED);
				}
			}
			case UP -> {
				if (climb(g, hatch, CLIMB, hatch.getY())) {
					climbing(g, false);
					Vec3 out = Underworld.topside(level, hatch);
					g.setPos(out.x, out.y, out.z);
					x.step = Step.TOP;
				}
			}
			case TOP -> {
				if (x.hand == null) {
					x.step = Step.DONE;
					release(x);
				} else if (x.handStep == Step.TOP) {
					// up and out, the both of them: the man goes before the realm's justice
					Villager v = x.hand;
					x.hand = null;
					x.step = Step.DONE;
					release(x);
					if (v.isAlive() && !Arrests.arrest(level, v, f, "dealing")) {
						McaCompat.vanish(v);
					}
				}
			}
			default -> {
			}
		}
	}

	/** The man {@code x} has taken: at his guard's back, then up the ladder after him. */
	private static void led(ServerLevel level, Faction f, Raid r, Raider x, long now) {
		Villager v = x.hand;
		if (v == null) {
			return;
		}
		if (!v.isAlive() || v.isRemoved()) {
			x.hand = null;
			return;
		}
		BlockPos hatch = r.hatch;
		switch (x.handStep) {
			case IN -> {
				Vec3 foot = foot(hatch);
				if (x.step == Step.UP || x.step == Step.TOP) {
					// he goes up once his guard is clear of the foot of it
					double dx = v.getX() - foot.x, dz = v.getZ() - foot.z;
					if (x.step == Step.TOP || x.guard.getY() > foot.y + 3.0) {
						if (dx * dx + dz * dz <= FOOT * FOOT || now - x.since > FOOT_TICKS) {
							climbing(v, true);
							v.setPos(foot.x, foot.y, foot.z);
							x.handStep = Step.UP;
						} else if (now % 10 == 0) {
							BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(foot), LED_SPEED, 0);
						}
					}
				} else if (now % 10 == 0) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new net.minecraft.world.entity.ai.behavior.EntityTracker(x.guard, false), LED_SPEED, 1);
				}
			}
			case UP -> {
				if (climb(v, hatch, CLIMB, hatch.getY())) {
					climbing(v, false);
					Vec3 out = Underworld.topside(level, hatch).add(0.0, 0.0, 1.0);
					v.setPos(out.x, out.y, out.z);
					x.handStep = Step.TOP;
				}
			}
			default -> {
			}
		}
	}

	/** The first guard down: the room shouted still, whoever's in it charged, the barrels gone through. */
	private static void burst(ServerLevel level, Faction f, Raid r, PathfinderMob first) {
		RandomSource random = level.getRandom();
		Speech.say(level, first, Component.translatable("raid.jaymoskingdoms.burst." + random.nextInt(LINES)), Speech.RED, SPEECH);
		level.playSound(null, first.blockPosition(), SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.HOSTILE, 1.0F, 0.6F);
		List<LivingEntity> watch = new ArrayList<>();
		for (Raider x : r.raiders) {
			watch.add(x.guard);
		}
		for (AABB box : rooms(level.getServer(), r.realm, r.hatch)) {
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator() && !p.isCreative() && p.isAlive())) {
				Theft.commit(p, level, f, Theft.Crime.DEALING, null, false, watch);
				Busts.charge(p, f, "den");
			}
		}
		Underworld.Den d = Underworld.den(level.getServer(), r.realm);
		if (d != null) {
			sweep(level, f, d, false);
		}
	}

	/** The next of the den's hands below that no guard has yet, nearest the hatch. */
	private static @Nullable Villager nextHand(ServerLevel level, Raid r) {
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (AABB box : rooms(level.getServer(), r.realm, r.hatch)) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && takeable(v))) {
				if (taken(r, v)) {
					continue;
				}
				double d = v.distanceToSqr(Vec3.atCenterOf(r.hatch));
				if (d < bestD) {
					bestD = d;
					best = v;
				}
			}
		}
		return best;
	}

	/** One of the den's hands a raid takes (a fighter on tonight's card is left to his bout). */
	private static boolean takeable(Villager v) {
		String tag = Underworld.hand(v);
		return tag != null && !(tag.equals(FightPit.FIGHTER_TAG) && FightPit.onCard(v.getUUID())) && !Arrests.held(v);
	}

	private static boolean taken(Raid r, Villager v) {
		for (Raider x : r.raiders) {
			if (x.hand == v) {
				return true;
			}
		}
		return false;
	}

	/** {@code x}'s guard takes {@code v}: he stops what he's at and goes along. */
	private static void take(ServerLevel level, Raider x, Villager v) {
		x.hand = v;
		x.handTag = Underworld.hand(v);
		x.handStep = Step.IN;
		v.setTradingPlayer(null);
		v.getNavigation().stop();
		x.guard.getLookControl().setLookAt(v, 30.0F, 30.0F);
		RandomSource random = level.getRandom();
		Speech.say(level, x.guard, Component.translatable("raid.jaymoskingdoms.take." + random.nextInt(LINES)), Speech.RED, SPEECH);
		Speech.say(level, v, Component.translatable("raid.jaymoskingdoms.taken." + random.nextInt(LINES)), Speech.WHITE, SPEECH);
		level.playSound(null, v.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		// no longer the den's: an ordinary man before the law
		for (String tag : new String[] {Underworld.FENCE_TAG, Underworld.DICER_TAG, Bookies.TAG, FightPit.ATTENDANT_TAG, FightPit.FIGHTER_TAG}) {
			v.removeTag(tag);
		}
	}

	/** A guard done with the raid (or gone from it): back to his own business, his man (if still below) left. */
	private static void release(Raider x) {
		climbing(x.guard, false);
		if (x.hand != null) {
			climbing(x.hand, false);
		}
	}

	// ---- shutting, sealing, clearing -----------------------------------------------------------------------------------

	/** The raid's done: {@code f}'s den is shut for {@link #SHUT}, cooled, and no longer a hotbed. */
	private static void shut(ServerLevel level, Faction f, long now) {
		MinecraftServer server = level.getServer();
		Underworld.Den d = Underworld.den(server, f.id());
		if (d == null) {
			return;
		}
		Underworld.put(server, d.raided(now + SHUT, HEAT_AFTER));
		Post.news(server, Component.translatable("news.jaymoskingdoms.underworld.raided", f.name()));
		if (level.isPositionEntityTicking(d.hatch())) {
			seal(level, d.hatch(), true);
			sweep(level, f, d, true);
		}
	}

	/** The hatch nailed down under iron ({@code shut}), or a wooden one put back. */
	private static void seal(ServerLevel level, BlockPos hatch, boolean shut) {
		BlockState now = level.getBlockState(hatch);
		boolean iron = now.is(Blocks.IRON_TRAPDOOR);
		if (shut == iron || !(now.getBlock() instanceof TrapDoorBlock) && !now.isAir()) {
			// (already so; or someone's built over it)
			return;
		}
		BlockState put = (shut ? Blocks.IRON_TRAPDOOR : Blocks.SPRUCE_TRAPDOOR).defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
			.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.FACING, Direction.NORTH);
		level.setBlockAndUpdate(hatch, put);
		level.playSound(null, hatch, shut ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	/** A guard throws the hatch open (wood only: a sealed one stays shut). */
	private static void openHatch(ServerLevel level, BlockPos hatch) {
		BlockState s = level.getBlockState(hatch);
		if (s.getBlock() instanceof TrapDoorBlock && !s.is(Blocks.IRON_TRAPDOOR) && !s.getValue(TrapDoorBlock.OPEN)) {
			level.setBlockAndUpdate(hatch, s.setValue(TrapDoorBlock.OPEN, true));
			level.playSound(null, hatch, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
		}
	}

	/**
	 * The den's barrels gone through for contraband; and, with {@code hands}, any of its hands still below taken off by the
	 * watch (the ones a raid had no guard for, or who came back while it's shut).
	 */
	private static void sweep(ServerLevel level, Faction f, Underworld.Den d, boolean hands) {
		for (AABB box : rooms(level.getServer(), f.id(), d.hatch())) {
			BlockPos.betweenClosedStream(box.deflate(0.01)).forEach(pos -> {
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof Container c) {
					for (int i = 0; i < c.getContainerSize(); i++) {
						ItemStack s = c.getItem(i);
						if (Underworld.contraband(s)) {
							c.setItem(i, ItemStack.EMPTY);
						}
					}
				}
			});
			if (!hands) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && Underworld.hand(v) != null && !involved(v.getUUID()))) {
				McaCompat.vanish(v);
			}
		}
	}

	/** The den's room and, if it has one, its fight pit. */
	private static List<AABB> rooms(MinecraftServer server, int realm, BlockPos hatch) {
		List<AABB> out = new ArrayList<>();
		out.add(Speakeasy.room(hatch).inflate(1.0));
		Speakeasy.Pit pit = FightPit.pit(server, realm);
		if (pit != null) {
			out.add(pit.room().inflate(1.0));
		}
		return out;
	}

	// ---- moving about --------------------------------------------------------------------------------------------------

	/** The foot of the den's ladder. */
	private static Vec3 foot(BlockPos hatch) {
		return new Vec3(hatch.getX() + 0.5, Speakeasy.room(hatch).minY, hatch.getZ() + 0.5);
	}

	/** {@code m} a step up ({@code by} over 0) or down the ladder; true once at {@code end}. */
	private static boolean climb(PathfinderMob m, BlockPos hatch, double by, double end) {
		double y = m.getY() + by;
		boolean there = by > 0 ? y >= end : y <= end;
		m.setDeltaMovement(Vec3.ZERO);
		m.setPos(hatch.getX() + 0.5, there ? end : y, hatch.getZ() + 0.5);
		m.getNavigation().stop();
		if (m instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		return there;
	}

	/** {@code m} on the ladder (held up by it), or off it. */
	private static void climbing(PathfinderMob m, boolean on) {
		m.setNoGravity(on);
		if (on) {
			m.getNavigation().stop();
		}
	}

	/** A guard walks to {@code to} (by his feet, as {@link Arrests} has them: folk and MCA guards alike). */
	private static void walk(PathfinderMob m, Vec3 to, float speed) {
		m.getNavigation().moveTo(to.x, to.y, to.z, speed);
	}
}
