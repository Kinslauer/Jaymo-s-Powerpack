package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Life round a camp's fire (Andy, 2026-11-19, on a clip of barbarians standing round their fire doing nothing: "there
 * should be some kind of idle interaction, especially when barbarians come together round the fire like this, they can
 * use any of the sitting poses or have a spit to cook on. sharing food with all.").
 * <p>
 * Outside supper ({@link CampSuppers}), a camp's band (barbarians, greenskins, pirates ashore: anyone of the camp a
 * player is near enough to see) drift to its lit fire a few at a time, mostly by night or in the rain, and sit a while
 * on its log benches or the ground round it ({@link #seats}, {@link GroundSeats}), each in a way of their own
 * ({@link #POSE}, drawn by the client's {@code FiresidePose}): warming their hands, leaning back on them, hunched over
 * their knees. Then they get up and go about the camp again.
 * <ul>
 * <li><b>Talk</b>: now and then one tells a tale, a boast, a joke or a gripe, or says what the rain is like, and the
 * others turn to listen; another laughs, scoffs or agrees. By night they sing. Words over their heads ({@link Callouts},
 * in their own tongue), never chat.</li>
 * <li><b>The spit</b> (with Jaymo's Kitchen, {@link KitchenSpits}): one of them gets up to cook, puts a spit over the
 * fire if there isn't one, and roasts meat on it a few pieces at a time, from the camp's own larder ({@link CampLarder}: its
 * chests and barrels round the fire, and what its hunters, {@link CampHunters}, set down by it), and nothing else: a camp
 * whose larder is bare goes hungry. Each piece cooked is torn off and carried round:
 * everyone at the fire gets a share, then a player the camp lets be who's sat with them, then the captives get the
 * scraps, and the cook eats last. Left too long, it burns.</li>
 * <li>Others whet their blades, or throw a stick on the fire.</li>
 * </ul>
 * Anyone who gets a foe, goes down, or is wanted elsewhere ({@link Comrades#busy}) is up at once; a stranger the camp
 * won't let be walking up to the fire brings them all to their feet.
 */
public final class Firesides {
	/** Looked over this often (ticks). */
	static final int EVERY = 10;
	/** A camp with a player this near (blocks) sits round its fire; its band is everyone of it this near the fire. */
	static final double WATCHED = 96.0, BAND = 32.0;
	/** Seats are sought this far from the fire (blocks, across the ground), and this far up or down. */
	static final double SEAT_MIN = 1.8, SEAT_MAX = 4.6;
	static final int SEAT_RISE = 1;
	/** At most this many sit at once by night (or in the rain), by day. */
	static final int SEATS_NIGHT = 10, SEATS_DAY = 3;
	/** The chance each look that a free one of the band comes to sit, by night (or in the rain), by day. */
	static final float JOIN_NIGHT = 0.03F, JOIN_DAY = 0.004F;
	/** How long one stays at the fire (ticks), and how long before they come back once they've gone. */
	static final int STAY_MIN = 1200, STAY_MORE = 2400, AWAY = 1800;
	/** Close enough to their seat to sit (blocks); stuck short of it, this close will do. */
	static final double ARRIVE = 1.4, NEAR_ENOUGH = 2.6;
	/** Night, for the fire: from dusk's end ({@link CampSuppers#TO}) to this time of day. */
	static final long DAWN = 23500L;
	/** Talk: how long between two lines round one fire (ticks, a least and how much more); the answer comes this long after. */
	static final int TALK_MIN = 120, TALK_MORE = 200, ANSWER = 40;
	/** How long the others turn to the one talking (ticks); a song lasts this long. */
	static final int LISTEN = 80, SONG = 200;
	/** The chance each look that one sitting changes how they sit, whets a blade, feeds the fire. */
	static final float SHIFT = 0.01F, WHET = 0.006F, STOKE = 0.003F;
	/** Whetting lasts this long (ticks). */
	static final int WHET_TICKS = 100;
	/** A stranger this near the fire brings them to their feet (blocks). */
	static final double WARY = 12.0;
	/** A player the camp lets be this near the fire is offered a share (blocks). */
	static final double GUEST = 4.5;
	/** Captives this near the fire get the scraps (blocks). */
	static final double CAPTIVES = 14.0;
	/** Eating a share: how long (ticks), a bite this often, and the health it gives back. */
	static final int EAT_TICKS = 80, BITE = 20;
	static final float MEAL_HEAL = 4.0F;
	/** The cook turns the spit this often (ticks); a cook who can't reach whom they're feeding gives up after this (ticks). */
	static final int TURN = 40, CARRY_TICKS = 300;
	/** Pieces the spit's bar takes (Kitchen's {@code SpitBlockEntity.PLACES}). */
	static final int PLACES = 5;
	/** A share is handed over this close (blocks). */
	static final double HAND = 2.2;
	/** The cook stands this far off the fire's middle (blocks), to the side of it. */
	static final int COOK_SIDE = 2;
	/**
	 * A whole beast (Andy, 2026-11-19, of the great spit: "the barbarians should use that"): a body lying this near the
	 * fire (blocks) is skinned and gutted where it lies with this many knife strokes, one a second, carried to the fire and
	 * roasted whole. Given up on after this long (ticks) and not looked for again for as long.
	 */
	static final double CARCASS_NEAR = 24.0, DRESS_REACH = 2.0;
	static final int DRESS_STROKES = 8, CARCASS_GIVE_UP = 900;

	/** How one sitting at a fire sits (and what their hands are about), for the client to draw. Not kept. */
	public static final int WARM = 1, LEAN = 2, HUNCH = 3, EAT = 4, SING = 5, WHETTING = 6;
	public static final AttachmentType<Integer> POSE = AttachmentRegistry.create(JaymosKingdoms.id("fireside_pose"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** The talk round a fire, and how many lines each kind has (lang part {@code firesides.json}). */
	private static final String[] TALK = {"fire.tale", "fire.boast", "fire.joke", "fire.gripe"};
	private static final String[] LINES = {"fire.tale", "fire.boast", "fire.joke", "fire.gripe", "fire.laugh", "fire.scoff", "fire.agree",
		"fire.rain", "fire.song", "fire.cook", "fire.serve", "fire.thanks", "fire.burnt", "fire.guest", "fire.scraps", "fire.hungry",
		"fire.stranger", "fire.stoke"};

	private enum Step {
		/** Getting the spit up, or meat on it. */
		SKEWER,
		/** Watching it roast, turning it. */
		ROAST,
		/** Carrying a share to whoever's next. */
		CARRY,
		/** Skinning and gutting a beast's body lying in camp, for the great spit. */
		DRESS,
		/** Carrying it, dressed, to the fire. */
		BRING
	}

	private static final class Sitter {
		final long until;
		@Nullable Vec3 seat;
		long since;
		int base;
		int act;
		long actUntil;
		/** A share in their hand (eating till {@link #eatUntil}); whether it went in their off hand. */
		long eatUntil = -1L;
		ItemStack meal = ItemStack.EMPTY;
		boolean holding;

		Sitter(long until, long since) {
			this.until = until;
			this.since = since;
		}
	}

	private static final class Fire {
		final int camp;
		BlockPos fire;
		List<BlockPos> seats = List.of();
		long seatsAt = -1000L;
		final Map<Mob, Sitter> sitters = new IdentityHashMap<>();
		@Nullable Mob cook;
		Step step = Step.SKEWER;
		long stepAt;
		ItemStack carrying = ItemStack.EMPTY;
		boolean cookHolding;
		@Nullable LivingEntity feeding;
		/** Fed round this fire tonight (sitters, guests, captives). */
		final Set<UUID> fed = new HashSet<>();
		long nextTalk;
		@Nullable Mob talker;
		long talkUntil;
		@Nullable String answer;
		long answerAt;
		boolean wary;
		boolean outOfFood;
		/** A beast's body the cook is readying for the great spit; knife strokes into it; no other looked for till then. */
		@Nullable Entity carcass;
		int strokes;
		long carcassAfter;

		Fire(int camp, BlockPos fire) {
			this.camp = camp;
			this.fire = fire;
		}
	}

	private static final Map<Integer, Fire> FIRES = new HashMap<>();

	private record RingCache(List<BlockPos> seats, long at) {
	}

	/** Supper's seats round each fire, looked for again now and then. */
	private static final Map<BlockPos, RingCache> RINGS = new HashMap<>();
	/** Who left a fire, and when they may come back (game time). */
	private static final Map<Mob, Long> AWAY_UNTIL = new WeakHashMap<>();

	private Firesides() {
	}

	static void register() {
		for (String kind : LINES) {
			Callouts.LINES.put(kind, 3);
		}
		ServerTickEvents.END_SERVER_TICK.register(Firesides::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIRES.clear();
			AWAY_UNTIL.clear();
			RINGS.clear();
		});
	}

	/** {@code m} sits at its camp's fire just now. */
	static boolean sitting(LivingEntity m) {
		for (Fire f : FIRES.values()) {
			if (f.sitters.containsKey(m) || f.cook == m) {
				return true;
			}
		}
		return false;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		long dayTime = level.getOverworldClockTime() % 24000L;
		boolean supper = dayTime >= CampSuppers.FROM && dayTime < CampSuppers.TO;
		Set<Integer> seen = new HashSet<>();
		for (Map.Entry<String, Camps.CampData> e : Camps.all(server).entrySet()) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			BlockPos center = e.getValue().center();
			Fire f = FIRES.get(id);
			if (supper || !Loaded.made(level, center) || !watched(level, center)) {
				// supper is CampSuppers' (they sit and eat there), and a fire nobody's near is left be
				if (f != null) {
					end(level, f);
				}
				continue;
			}
			BlockPos fire = f != null && lit(level, f.fire) ? f.fire : findFire(level, center);
			if (fire == null) {
				if (f != null) {
					end(level, f);
				}
				continue;
			}
			if (f == null) {
				f = new Fire(id, fire);
				f.nextTalk = now + TALK_MIN;
				FIRES.put(id, f);
			}
			f.fire = fire;
			seen.add(id);
			step(level, f, now, dayTime);
		}
		for (Fire f : new ArrayList<>(FIRES.values())) {
			if (!seen.contains(f.camp)) {
				end(level, f);
			}
		}
	}

	private static boolean watched(ServerLevel level, BlockPos fire) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(fire, WATCHED)) {
				return true;
			}
		}
		return false;
	}

	private static boolean lit(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.CAMPFIRES) && (!state.hasProperty(CampfireBlock.LIT) || state.getValue(CampfireBlock.LIT));
	}

	/** A lit fire near the camp's centre (they don't build one for this; supper does). */
	static @Nullable BlockPos findFire(ServerLevel level, BlockPos center) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
			if (lit(level, p) && p.distSqr(center) < bestD) {
				bestD = p.distSqr(center);
				best = p.immutable();
			}
		}
		return best;
	}

	private static void step(ServerLevel level, Fire f, long now, long dayTime) {
		RandomSource random = level.getRandom();
		String side = "f:" + f.camp;
		boolean night = dayTime >= CampSuppers.TO && dayTime < DAWN;
		boolean wet = level.isRainingAt(f.fire.above(2));
		if (now - f.seatsAt > 600L) {
			f.seats = seats(level, f.fire);
			f.seatsAt = now;
		}
		// a stranger walking up brings them to their feet; one creeping up isn't seen till close (as at supper), and nobody
		// new sits down while one's about
		ServerPlayer stranger = stranger(level, f);
		if (stranger != null) {
			Mob saw = f.cook != null && f.cook.hasLineOfSight(stranger) ? f.cook : null;
			for (Mob m : f.sitters.keySet()) {
				if (saw == null && m.hasLineOfSight(stranger)) {
					saw = m;
				}
			}
			if (saw != null) {
				if (!f.wary) {
					Callouts.say(saw, "fire.stranger", stranger, true, stranger.getDisplayName());
				}
				f.wary = true;
				standAll(level, f, now);
				return;
			}
		} else {
			f.wary = false;
		}
		// those who must go
		for (Mob m : new ArrayList<>(f.sitters.keySet())) {
			Sitter s = f.sitters.get(m);
			if (!fit(m, side) || now >= s.until) {
				leave(level, f, m, now);
			}
		}
		if (f.cook != null && !fit(f.cook, side)) {
			dropCook(f, now);
		}
		// those who come
		int room = (night || wet ? SEATS_NIGHT : SEATS_DAY) - f.sitters.size() - (f.cook == null ? 0 : 1);
		if (room > 0 && !f.seats.isEmpty() && stranger == null) {
			float join = night || wet ? JOIN_NIGHT : JOIN_DAY;
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(f.fire).inflate(BAND), m -> fit(m, side) && free(m, now, night))) {
				if (room <= 0) {
					break;
				}
				if (random.nextFloat() < join) {
					Sitter s = new Sitter(now + STAY_MIN + random.nextInt(STAY_MORE), now);
					s.base = wet || random.nextInt(3) == 0 ? WARM : random.nextBoolean() ? LEAN : HUNCH;
					f.sitters.put(m, s);
					room--;
				}
			}
		}
		Set<BlockPos> taken = new HashSet<>();
		for (Sitter s : f.sitters.values()) {
			if (s.seat != null) {
				taken.add(BlockPos.containing(s.seat));
			}
		}
		// to their seats, and sitting
		for (Map.Entry<Mob, Sitter> e : f.sitters.entrySet()) {
			Mob m = e.getKey();
			Sitter s = e.getValue();
			if (!GroundSeats.sitting(m)) {
				if (s.seat == null) {
					BlockPos seat = nearestSeat(f.seats, taken, m.blockPosition());
					if (seat == null) {
						continue;
					}
					taken.add(seat);
					s.seat = Vec3.atBottomCenterOf(seat);
				}
				if (reach(m, s.seat, f.fire)) {
					GroundSeats.sitDown(m, Vec3.atBottomCenterOf(f.fire));
				}
				continue;
			}
			sat(level, f, m, s, now, random);
		}
		talk(level, f, now, night, wet, random);
		if (KitchenSpits.ready()) {
			cook(level, f, now, random);
		}
		if (f.sitters.isEmpty() && f.cook == null) {
			// nobody at the fire: what's left on the spit stays there for the next to come
			f.talker = null;
		}
	}

	/** One of {@code side}'s that can sit: alive, up, unbound, not fighting and not wanted elsewhere. */
	private static boolean fit(Mob m, String side) {
		return m.isAlive() && !m.isRemoved() && !m.isBaby() && !Downed.isDowned(m) && !Captives.bound(m) && Temperaments.humanoid(m)
			&& side.equals(Comrades.side(m)) && Comrades.target(m) == null && m.getTarget() == null && !Comrades.busy(m) && !m.isSleeping()
			&& !m.isNoAi() && (!m.isPassenger() || GroundSeats.sitting(m)) && !CampSuppers.eating(m);
	}

	/** Not at a fire already, not just back from one; by day a camp's builders and workers are at their work. */
	private static boolean free(Mob m, long now, boolean night) {
		if (m.isPassenger() || sitting(m) || AWAY_UNTIL.getOrDefault(m, Long.MIN_VALUE) > now || m.hurtTime > 0) {
			return false;
		}
		return night || !Barbarians.campWorker(m);
	}

	/** A player near the fire whom the camp wouldn't let be (not a captive minding themselves). */
	private static @Nullable ServerPlayer stranger(ServerLevel level, Fire f) {
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || p.isInvisible() || !p.blockPosition().closerThan(f.fire, WARY)) {
				continue;
			}
			if (!Barbarians.spares(p, f.camp) && !CaptiveConduct.behaving(p)
				&& (!p.isShiftKeyDown() || p.blockPosition().closerThan(f.fire, CampSuppers.UNWARY))) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Close enough to {@code seat} to sit down: there, or as near as they can get once their path has run out (a bench in
	 * the way), so long as it's by the fire. Otherwise they're sent on their way to it.
	 */
	static boolean reach(Mob m, Vec3 seat, BlockPos fire) {
		double d = m.position().distanceToSqr(seat);
		if (d <= ARRIVE * ARRIVE) {
			return true;
		}
		if (!m.getNavigation().isDone()) {
			return false;
		}
		double fx = m.getX() - (fire.getX() + 0.5), fz = m.getZ() - (fire.getZ() + 0.5);
		double fromFire = Math.sqrt(fx * fx + fz * fz);
		if (d <= NEAR_ENOUGH * NEAR_ENOUGH && fromFire >= SEAT_MIN - 0.3 && fromFire <= SEAT_MAX + 0.5) {
			return true;
		}
		boolean going = m.getNavigation().moveTo(seat.x, seat.y, seat.z, 0.5);
		return !going && fromFire >= SEAT_MIN - 0.3 && fromFire <= SEAT_MAX + 0.5;
	}

	/**
	 * Where one can sit round {@code fire}: the tops of the logs laid by it for benches first (a camp's are three blocks
	 * out, where a plain ring would put them inside the wood), then open ground; each a block one can stand in, on
	 * something solid, {@link #SEAT_MIN} to {@link #SEAT_MAX} out. In order round the fire.
	 */
	static List<BlockPos> seats(ServerLevel level, BlockPos fire) {
		List<BlockPos> benches = new ArrayList<>(), ground = new ArrayList<>();
		int r = (int)Math.ceil(SEAT_MAX);
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-r, -SEAT_RISE, -r), fire.offset(r, SEAT_RISE, r))) {
			double dx = p.getX() - fire.getX(), dz = p.getZ() - fire.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < SEAT_MIN || d > SEAT_MAX) {
				continue;
			}
			BlockPos below = p.below();
			BlockState under = level.getBlockState(below);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				|| !under.isFaceSturdy(level, below, Direction.UP) || under.is(BlockTags.CAMPFIRES) || level.getBlockState(p).is(BlockTags.CAMPFIRES)
				|| !level.getFluidState(p).isEmpty()) {
				continue;
			}
			(under.is(BlockTags.LOGS) ? benches : ground).add(p.immutable());
		}
		Comparator<BlockPos> round = Comparator.comparingDouble(p -> Math.atan2(p.getZ() - fire.getZ(), p.getX() - fire.getX()));
		benches.sort(round);
		ground.sort(round);
		List<BlockPos> all = new ArrayList<>(benches);
		all.addAll(ground);
		return all;
	}

	/** The {@code i}th of {@code n} seats spread round {@code fire} (for {@link CampSuppers}): a bench first, else open ground. */
	static Vec3 ringSeat(ServerLevel level, BlockPos fire, int i, int n) {
		long now = level.getGameTime();
		RingCache cached = RINGS.get(fire);
		if (cached == null || now - cached.at() > 600L) {
			cached = new RingCache(seats(level, fire), now);
			RINGS.put(fire.immutable(), cached);
		}
		List<BlockPos> all = cached.seats();
		if (all.isEmpty()) {
			double a = Math.PI * 2.0 * i / Math.max(1, n);
			return Vec3.atBottomCenterOf(fire).add(Math.cos(a) * 3.0, 0.0, Math.sin(a) * 3.0);
		}
		return Vec3.atBottomCenterOf(all.get(i * all.size() / Math.max(1, n) % all.size()));
	}

	private static @Nullable BlockPos nearestSeat(List<BlockPos> seats, Set<BlockPos> taken, BlockPos from) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < seats.size(); i++) {
			BlockPos p = seats.get(i);
			if (taken.contains(p)) {
				continue;
			}
			// a little nudge to the benches (listed first)
			double d = p.distSqr(from) + (i < seats.size() / 2 ? 0.0 : 4.0);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	// ---- sitting -------------------------------------------------------------------------------------------------------

	private static void sat(ServerLevel level, Fire f, Mob m, Sitter s, long now, RandomSource random) {
		m.getNavigation().stop();
		// eating a share
		if (s.eatUntil >= 0L) {
			if (now >= s.eatUntil) {
				finishMeal(m, s);
				if (random.nextInt(2) == 0) {
					Callouts.say(m, "fire.thanks", f.cook, false);
				}
			} else if ((now - s.since) % BITE < EVERY) {
				bite(level, m, s.meal);
			}
		}
		int pose = s.eatUntil >= 0L ? EAT : now < s.actUntil ? s.act : s.base;
		if (s.eatUntil < 0L && now >= s.actUntil) {
			if (random.nextFloat() < SHIFT) {
				s.base = random.nextInt(3) + WARM;
			} else if (random.nextFloat() < WHET && (m.getMainHandItem().is(ItemTags.SWORDS) || m.getMainHandItem().is(ItemTags.AXES))) {
				s.act = WHETTING;
				s.actUntil = now + WHET_TICKS;
			} else if (random.nextFloat() < STOKE && m.distanceToSqr(Vec3.atCenterOf(f.fire)) < 3.5 * 3.5) {
				stoke(level, f, m);
			}
		}
		if (pose == WHETTING && (now - s.since) % 20 < EVERY) {
			level.playSound(null, m.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.25F, 1.4F + random.nextFloat() * 0.2F);
		}
		setPose(m, pose);
		// whom they look at: the one talking, the cook with the food, else the fire
		if (f.talker != null && f.talker != m && now < f.talkUntil && f.talker.isAlive()) {
			m.getLookControl().setLookAt(f.talker, 30.0F, 30.0F);
		} else if (f.cook != null && f.feeding == m) {
			m.getLookControl().setLookAt(f.cook, 30.0F, 30.0F);
		} else {
			m.getLookControl().setLookAt(f.fire.getX() + 0.5, f.fire.getY() + 0.5, f.fire.getZ() + 0.5);
		}
	}

	private static void setPose(Mob m, int pose) {
		Integer had = m.getAttached(POSE);
		if (had == null || had != pose) {
			m.setAttached(POSE, pose);
		}
	}

	private static void stoke(ServerLevel level, Fire f, Mob m) {
		m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, f.fire, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.FLAME, f.fire.getX() + 0.5, f.fire.getY() + 0.6, f.fire.getZ() + 0.5, 8, 0.2, 0.15, 0.2, 0.02);
		level.sendParticles(ParticleTypes.LAVA, f.fire.getX() + 0.5, f.fire.getY() + 0.6, f.fire.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
		Callouts.say(m, "fire.stoke", null, false);
	}

	/** {@code m} gets up and goes; back in a while. */
	private static void leave(ServerLevel level, Fire f, Mob m, long now) {
		Sitter s = f.sitters.remove(m);
		if (s != null) {
			finishMeal(m, s);
		}
		m.removeAttached(POSE);
		GroundSeats.standUp(m);
		AWAY_UNTIL.put(m, now + AWAY + level.getRandom().nextInt(AWAY));
		if (f.talker == m) {
			f.talker = null;
		}
		if (f.feeding == m) {
			f.feeding = null;
		}
	}

	private static void standAll(ServerLevel level, Fire f, long now) {
		for (Mob m : new ArrayList<>(f.sitters.keySet())) {
			leave(level, f, m, now);
		}
		dropCook(f, now);
	}

	/** The fire's left: everyone up, the cook's share eaten where they stand. */
	private static void end(ServerLevel level, Fire f) {
		long now = level.getGameTime();
		standAll(level, f, now);
		FIRES.remove(f.camp);
	}

	private static void bite(ServerLevel level, Mob m, ItemStack meal) {
		m.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, m.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.5F, 0.9F + m.getRandom().nextFloat() * 0.2F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meal.isEmpty() ? Items.COOKED_MUTTON : meal.getItem()), m.getX(), m.getEyeY() - 0.1,
			m.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
	}

	/** {@code m} is handed {@code meal}: in their off hand if it's empty, eaten bit by bit. */
	private static void serve(Mob m, Sitter s, ItemStack meal, long now) {
		s.meal = meal;
		s.eatUntil = now + EAT_TICKS;
		s.holding = m.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty();
		if (s.holding) {
			m.setItemSlot(EquipmentSlot.OFFHAND, meal.copyWithCount(1));
		}
	}

	private static void finishMeal(Mob m, Sitter s) {
		if (s.eatUntil < 0L) {
			return;
		}
		if (s.holding && m.getItemBySlot(EquipmentSlot.OFFHAND).is(s.meal.getItem())) {
			m.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		m.heal(MEAL_HEAL);
		CampLarder.fed(m);
		s.eatUntil = -1L;
		s.meal = ItemStack.EMPTY;
		s.holding = false;
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	private static void talk(ServerLevel level, Fire f, long now, boolean night, boolean wet, RandomSource random) {
		List<Mob> seated = new ArrayList<>();
		for (Mob m : f.sitters.keySet()) {
			if (GroundSeats.sitting(m)) {
				seated.add(m);
			}
		}
		if (f.answer != null && now >= f.answerAt) {
			List<Mob> others = new ArrayList<>(seated);
			others.remove(f.talker);
			if (!others.isEmpty()) {
				Callouts.say(others.get(random.nextInt(others.size())), f.answer, f.talker, false);
			}
			f.answer = null;
		}
		if (seated.size() < 2 || now < f.nextTalk) {
			return;
		}
		f.nextTalk = now + TALK_MIN + random.nextInt(TALK_MORE);
		Mob speaker = seated.get(random.nextInt(seated.size()));
		Sitter s = f.sitters.get(speaker);
		if (s == null || s.eatUntil >= 0L) {
			return;
		}
		String kind;
		if (wet && random.nextInt(4) == 0) {
			kind = "fire.rain";
		} else if (night && random.nextInt(5) == 0) {
			kind = "fire.song";
		} else {
			kind = TALK[random.nextInt(TALK.length)];
		}
		if (!Callouts.say(speaker, kind, null, false)) {
			return;
		}
		f.talker = speaker;
		f.talkUntil = now + (kind.equals("fire.song") ? SONG : LISTEN);
		switch (kind) {
			case "fire.song" -> {
				// they take it up with them
				s.act = SING;
				s.actUntil = now + SONG;
				for (Mob m : seated) {
					Sitter o = f.sitters.get(m);
					if (o != null && m != speaker && o.eatUntil < 0L && random.nextBoolean()) {
						o.act = SING;
						o.actUntil = now + SONG - random.nextInt(40);
					}
				}
			}
			case "fire.joke", "fire.tale" -> f.answer = "fire.laugh";
			case "fire.boast" -> f.answer = random.nextBoolean() ? "fire.scoff" : "fire.laugh";
			default -> f.answer = "fire.agree";
		}
		if (f.answer != null) {
			f.answerAt = now + ANSWER;
		}
	}

	// ---- the spit ------------------------------------------------------------------------------------------------------

	private static void cook(ServerLevel level, Fire f, long now, RandomSource random) {
		Mob cook = f.cook;
		if (cook == null) {
			// someone gets up to cook once a few are sat and there's someone not fed
			if (f.outOfFood && carcass(level, f, now) == null || f.sitters.size() < 2 || next(level, f) == null) {
				return;
			}
			Mob pick = null;
			for (Map.Entry<Mob, Sitter> e : f.sitters.entrySet()) {
				if (GroundSeats.sitting(e.getKey()) && e.getValue().eatUntil < 0L && !BandLeaders.isLeader(e.getKey())) {
					pick = e.getKey();
					break;
				}
			}
			if (pick == null) {
				return;
			}
			Sitter s = f.sitters.remove(pick);
			if (s != null) {
				finishMeal(pick, s);
			}
			pick.removeAttached(POSE);
			GroundSeats.standUp(pick);
			f.cook = pick;
			f.step = Step.SKEWER;
			f.stepAt = now;
			return;
		}
		BlockPos spitPos = f.fire.above();
		BlockEntity spit = KitchenSpits.at(level, spitPos);
		switch (f.step) {
			case SKEWER -> {
				// a beast's body lying in camp goes on the great spit whole, once the spit is bare
				if (spit == null || !KitchenSpits.roasting(spit)) {
					Entity carcass = carcass(level, f, now);
					if (carcass != null) {
						f.carcass = carcass;
						f.strokes = 0;
						f.step = Step.DRESS;
						f.stepAt = now;
						return;
					}
				}
				if (!tend(cook, f)) {
					return;
				}
				if (spit == null) {
					// put the spit up over the fire
					Block block = KitchenSpits.block();
					if (block == null || !level.getBlockState(spitPos).isAir()) {
						f.outOfFood = true;
						dropCook(f, now);
						return;
					}
					cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.setBlock(spitPos, block.defaultBlockState(), Block.UPDATE_ALL);
					level.playSound(null, spitPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
					return;
				}
				boolean bare = !KitchenSpits.roasting(spit);
				if (bare || f.strokes > 0 && f.strokes < loadFor(f) && !KitchenSpits.full(spit)) {
					// (Andy: "they could put maybe 5 steaks on that thing") a piece for each mouth, a piece a look, till
					// the bar's full; the count of pieces put on rides in strokes while loading
					ItemStack meat = meat(level, f);
					if (meat.isEmpty()) {
						if (bare) {
							Callouts.say(cook, "fire.hungry", null, false);
							f.outOfFood = true;
							dropCook(f, now);
							return;
						}
					} else if (!KitchenSpits.skewer(spit, meat)) {
						if (bare) {
							f.outOfFood = true;
							dropCook(f, now);
							return;
						}
					} else {
						cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						level.playSound(null, spitPos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 0.8F);
						if (bare) {
							Callouts.say(cook, "fire.cook", null, false);
						}
						f.strokes++;
						if (f.strokes < loadFor(f) && !KitchenSpits.full(spit)) {
							return;
						}
					}
				}
				f.strokes = 0;
				f.step = Step.ROAST;
				f.stepAt = now;
			}
			case ROAST -> {
				tend(cook, f);
				if (spit == null || !KitchenSpits.roasting(spit)) {
					f.step = Step.SKEWER;
					return;
				}
				cook.getLookControl().setLookAt(spitPos.getX() + 0.5, spitPos.getY() + 0.2, spitPos.getZ() + 0.5);
				if ((now - f.stepAt) % TURN < EVERY) {
					cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}
				float done = KitchenSpits.doneness(spit);
				if (done < KitchenSpits.COOKED) {
					return;
				}
				boolean burnt = done >= KitchenSpits.BURNT;
				ItemStack chunk = KitchenSpits.tear(spit, level, cook);
				if (burnt || chunk.isEmpty()) {
					if (burnt) {
						level.playSound(null, spitPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
						Callouts.say(cook, "fire.burnt", null, false);
					}
					f.step = Step.SKEWER;
					return;
				}
				level.playSound(null, spitPos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 1.0F);
				f.carrying = chunk;
				f.cookHolding = cook.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty();
				if (f.cookHolding) {
					cook.setItemSlot(EquipmentSlot.OFFHAND, chunk.copyWithCount(1));
				}
				f.feeding = next(level, f);
				f.step = Step.CARRY;
				f.stepAt = now;
			}
			case CARRY -> {
				LivingEntity to = f.feeding;
				if (to == null || !to.isAlive() || to.isRemoved() || now - f.stepAt > CARRY_TICKS || f.fed.contains(to.getUUID())
					|| to instanceof Mob mob && !f.sitters.containsKey(mob) && !Captives.bound(mob)) {
					to = f.feeding = next(level, f);
					f.stepAt = now;
				}
				if (to == null) {
					// nobody left to feed: the cook eats it and sits down
					handOver(level, f, cook, now, random);
					return;
				}
				if (cook.distanceTo(to) > HAND) {
					if (cook.getNavigation().isDone()) {
						cook.getNavigation().moveTo(to, 0.55);
					}
					return;
				}
				cook.getNavigation().stop();
				cook.getLookControl().setLookAt(to, 30.0F, 30.0F);
				cook.swing(f.cookHolding ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				ItemStack share = takeCarried(f, cook);
				if (share.isEmpty()) {
					f.feeding = null;
					f.step = Step.SKEWER;
					return;
				}
				f.fed.add(to.getUUID());
				if (to instanceof ServerPlayer player) {
					if (Barbarians.spares(player, f.camp)) {
						Callouts.say(cook, "fire.guest", player, true, player.getDisplayName());
					} else {
						// a captive minding themselves
						Callouts.say(cook, "fire.scraps", player, true);
					}
					if (!player.getInventory().add(share)) {
						player.drop(share, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
				} else if (to instanceof Mob mob && f.sitters.get(mob) instanceof Sitter s) {
					Callouts.say(cook, "fire.serve", mob, false);
					serve(mob, s, share, now);
				} else {
					// a captive's scraps
					Callouts.say(cook, "fire.scraps", to, false);
					bite(level, (Mob)to, share);
					to.heal(MEAL_HEAL);
				}
				f.feeding = null;
				f.step = Step.SKEWER;
				f.stepAt = now;
			}
			case DRESS -> {
				Entity carcass = f.carcass;
				if (carcass == null || carcass.isRemoved() || KitchenCompat.carrier(carcass) != null || now - f.stepAt > CARCASS_GIVE_UP) {
					giveUpCarcass(f, now);
					return;
				}
				if (cook.distanceTo(carcass) > DRESS_REACH) {
					if (cook.getNavigation().isDone()) {
						cook.getNavigation().moveTo(carcass.getX(), carcass.getY(), carcass.getZ(), 0.55);
					}
					return;
				}
				cook.getNavigation().stop();
				cook.getLookControl().setLookAt(carcass.getX(), carcass.getY() + 0.3, carcass.getZ());
				if (!KitchenCompat.dressedWhole(carcass)) {
					// skinning and gutting it where it lies, a knife stroke a second
					if ((now - f.stepAt) % 20 < EVERY) {
						cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						level.playSound(null, carcass.blockPosition(), f.strokes % 2 == 0 ? SoundEvents.SHEEP_SHEAR : SoundEvents.SLIME_SQUISH_SMALL,
							SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.2F);
						level.sendParticles(ParticleTypes.CRIT, carcass.getX(), carcass.getY() + 0.3, carcass.getZ(), 3, 0.3, 0.2, 0.3, 0.05);
						if (++f.strokes >= DRESS_STROKES && !KitchenCompat.dressForSpit(carcass)) {
							giveUpCarcass(f, now);
						}
					}
					return;
				}
				if (!KitchenCompat.carry(carcass, cook)) {
					giveUpCarcass(f, now);
					return;
				}
				f.strokes = 0;
				f.step = Step.BRING;
				f.stepAt = now;
			}
			case BRING -> {
				Entity carcass = f.carcass;
				if (carcass == null || carcass.isRemoved() || KitchenCompat.carrier(carcass) != cook || now - f.stepAt > CARCASS_GIVE_UP) {
					giveUpCarcass(f, now);
					return;
				}
				if (!tend(cook, f)) {
					return;
				}
				Block great = KitchenSpits.great();
				if (!KitchenSpits.isGreat(level, spitPos)) {
					// the great spit up over the fire, in place of a bare spit
					if (great == null || spit != null && KitchenSpits.roasting(spit) || spit == null && !level.getBlockState(spitPos).isAir()) {
						giveUpCarcass(f, now);
						return;
					}
					cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.setBlock(spitPos, great.defaultBlockState(), Block.UPDATE_ALL);
					level.playSound(null, spitPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
					return;
				}
				if (spit == null || !KitchenSpits.skewerWhole(spit, carcass)) {
					giveUpCarcass(f, now);
					return;
				}
				cook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				Callouts.say(cook, "fire.cook", null, false);
				f.carcass = null;
				f.outOfFood = false;
				f.step = Step.ROAST;
				f.stepAt = now;
			}
		}
	}

	/** How many pieces the cook puts on the spit at once: one for each sat at the fire, the bar's worth at most. */
	private static int loadFor(Fire f) {
		return Math.max(1, Math.min(PLACES, f.sitters.size() + 1));
	}

	/** A beast's body near the fire the cook could roast whole (none while one's given up on, or with an old Kitchen). */
	private static @Nullable Entity carcass(ServerLevel level, Fire f, long now) {
		if (now < f.carcassAfter || KitchenSpits.great() == null) {
			return null;
		}
		return KitchenCompat.spitCarcassNear(level, Vec3.atCenterOf(f.fire), CARCASS_NEAR);
	}

	/** The beast's body is left be (gone, taken, or it wouldn't go on): set down where the cook is, and back to the spit. */
	private static void giveUpCarcass(Fire f, long now) {
		Entity carcass = f.carcass;
		if (carcass != null && f.cook != null && KitchenCompat.carrier(carcass) == f.cook) {
			KitchenCompat.throwAt(carcass, f.cook.position());
		}
		f.carcass = null;
		f.strokes = 0;
		f.carcassAfter = now + CARCASS_GIVE_UP;
		f.step = Step.SKEWER;
		f.stepAt = now;
	}

	/**
	 * The cook goes to the fire's side; true once there. (Andy, on a clip of a cook stood in the smoke over the fire: "they
	 * should stand to the side".) They stand {@link #COOK_SIDE} blocks off the fire, on whichever side of it is clear and
	 * nearest them, out of the smoke, never over it.
	 */
	private static boolean tend(Mob cook, Fire f) {
		ServerLevel level = (ServerLevel) cook.level();
		Vec3 by = null;
		double best = Double.MAX_VALUE;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos spot = f.fire.relative(side, COOK_SIDE);
			BlockPos stand = Construction.standable(level, spot, 0);
			if (stand.distManhattan(spot) > 1 || Math.abs(stand.getY() - f.fire.getY()) > 1) {
				continue;
			}
			double d = cook.distanceToSqr(Vec3.atBottomCenterOf(stand));
			if (d < best) {
				best = d;
				by = Vec3.atBottomCenterOf(stand);
			}
		}
		if (by == null) {
			by = Vec3.atBottomCenterOf(f.fire.relative(Direction.NORTH, COOK_SIDE));
		}
		Vec3 fire = Vec3.atBottomCenterOf(f.fire);
		double fromFire = Math.hypot(cook.getX() - fire.x, cook.getZ() - fire.z);
		if (cook.position().distanceToSqr(by) <= 1.2 * 1.2) {
			cook.getNavigation().stop();
			return true;
		}
		if (cook.getNavigation().isDone() && !cook.getNavigation().moveTo(by.x, by.y, by.z, 0.55)) {
			// can't get to the fire's side: near enough if they're beside it, not over it
			return fromFire >= 1.4 && fromFire <= 4.0;
		}
		return false;
	}

	/** The share the cook is carrying, out of their hand. */
	private static ItemStack takeCarried(Fire f, Mob cook) {
		ItemStack share = f.carrying;
		if (f.cookHolding && !share.isEmpty() && cook.getItemBySlot(EquipmentSlot.OFFHAND).is(share.getItem())) {
			cook.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		f.carrying = ItemStack.EMPTY;
		f.cookHolding = false;
		return share;
	}

	/** Everyone's fed: the cook eats the last share and sits down with them. */
	private static void handOver(ServerLevel level, Fire f, Mob cook, long now, RandomSource random) {
		ItemStack share = takeCarried(f, cook);
		f.cook = null;
		f.fed.add(cook.getUUID());
		Sitter s = new Sitter(now + STAY_MIN + random.nextInt(STAY_MORE), now);
		s.base = HUNCH;
		f.sitters.put(cook, s);
		if (!share.isEmpty()) {
			serve(cook, s, share, now);
		}
	}

	/** The cook stops cooking (called away, or nothing to cook); what they carried they eat. */
	private static void dropCook(Fire f, long now) {
		Mob cook = f.cook;
		if (cook == null) {
			return;
		}
		if (!f.carrying.isEmpty()) {
			takeCarried(f, cook);
			cook.heal(MEAL_HEAL);
		}
		if (f.carcass != null && KitchenCompat.carrier(f.carcass) == cook) {
			KitchenCompat.throwAt(f.carcass, cook.position());
		}
		f.carcass = null;
		f.strokes = 0;
		cook.removeAttached(POSE);
		cook.getNavigation().stop();
		AWAY_UNTIL.put(cook, now + AWAY);
		f.cook = null;
		f.feeding = null;
	}

	/**
	 * Who's fed next: whoever's sat at the fire and hasn't eaten (nearest the cook first), then a player the camp lets be
	 * sat by the fire, then the captives in the camp. Null when all have had their share.
	 */
	private static @Nullable LivingEntity next(ServerLevel level, Fire f) {
		Mob cook = f.cook;
		Vec3 from = cook != null ? cook.position() : Vec3.atCenterOf(f.fire);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Map.Entry<Mob, Sitter> e : f.sitters.entrySet()) {
			Mob m = e.getKey();
			if (!f.fed.contains(m.getUUID()) && GroundSeats.sitting(m) && e.getValue().eatUntil < 0L) {
				double d = m.position().distanceToSqr(from);
				if (d < bestD) {
					bestD = d;
					best = m;
				}
			}
		}
		if (best != null) {
			return best;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !f.fed.contains(p.getUUID()) && p.blockPosition().closerThan(f.fire, GUEST)
				&& (Barbarians.spares(p, f.camp) || CaptiveConduct.behaving(p))) {
				return p;
			}
		}
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(f.fire).inflate(CAPTIVES), m -> m.isAlive() && Captives.bound(m))) {
			if (!f.fed.contains(m.getUUID())) {
				return m;
			}
		}
		return null;
	}

	/** A piece of meat for the spit, out of the camp's larder ({@link CampLarder}); empty when there's none. */
	private static ItemStack meat(ServerLevel level, Fire f) {
		return CampLarder.take(level, f.fire, stack -> stack.is(KitchenSpits.ROASTABLE));
	}
}
