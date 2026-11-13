package com.jaymospowers.client;

import com.jaymospowers.core.Sitting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Falling, for everyone to see: players and every humanoid (villagers, Kingdoms' folk, illagers, zombies, skeletons,
 * piglins...). Andy: "when falling, especially from the aether, humanoids animate from one of many falling animations,
 * take from any in the game". Each fall picks its animations at random (by who is falling and where the fall began, so
 * a fall keeps them) from the ones that suit how far there is to fall, and changes them as the fall goes on:
 * <ul>
 * <li><b>A short drop</b> (under {@link #MEDIUM_DROP} blocks to the ground): arms flung out to the sides, running on air,
 * knees tucked up ready for the ground, the old windmill, or a grab back up at the edge.</li>
 * <li><b>A longer fall</b>: the panic first (windmill, air run, arms out, a flop onto the back, a grab at the edge), and
 * {@link #BRACE_TICKS} before the ground, the brace: upright, legs together, arms over the head.</li>
 * <li><b>A long fall</b> ({@link #LONG_DROP} blocks or more, off the Aether's islands, or nobody can see the bottom):
 * after {@link #SETTLE_TICKS} of panic the body settles into one of the long-fall shapes (a skydiver's arch, an
 * end-over-end tumble, a flat spin, a curled-up roll, a pencil drop, or gone limp), after {@link #SECOND_TICKS} into
 * another, and braces before the ground.</li>
 * </ul>
 * Where each comes from ({@link Style}): our own windmill flail, Leap of Faith's dive, the Sky Dive move, the thrown
 * body's tumble and the limp ragdolls; Not Enough Animations' falling animation (the run on air: tr7zw Protective
 * License, so remade, none of its code) and Fresh Animations: Player Extension's (arms out wide: FreshLX's, all rights
 * reserved, so remade from how it looks); the rest (the edge grab, the back flop, the flat spin, the curl, the pencil and
 * the brace) are new. Touch'n Motion has no fall of its own.
 * <p>
 * A wind user (and a freerunner with Leap of Faith, falling looking down) doesn't flail: they dive, head first and sure of
 * it, arms swept back, legs together, until they call the wind (their flight) and pull out.
 * <p>
 * Worked out from the actual motion, so it shows on everyone in view; anything else that has the body is left alone: a
 * glide, a swing, a flight, an elytra, riding, a ladder, water, a ledge hang, a freerun move, being carried or thrown,
 * held by a web, a grip or a power, downed, limp as a ragdoll. Someone carrying a body only kicks their legs (the arms
 * keep the hold). On landing it eases out over {@link #LAND_FADE} ticks. Your own body isn't tipped over in first person.
 * <p>
 * A player can choose their own: the Sit key in midair goes round the styles ({@link Sitting#FALL_POSE}, Andy 2026-11-13:
 * "the sit key in midair should swap the falling pose and it should remember the last you chose next fall"). The chosen
 * one turns in at once, and every fall after holds it from start to finish in place of the random picks, still bracing
 * for the ground before landing.
 */
public final class FreefallPose {
	// ===== the knobs ================================================================================================
	/** The dive: falling at least this fast (blocks/tick) it starts; by {@code FULL} it's all limbs. */
	static final float START = 0.8F, FULL = 1.3F;
	/** Everyone else: falling at least this fast (about two and a half blocks' drop) it starts; full by {@code FALL_FULL}. */
	static final float FALL_START = 0.6F, FALL_FULL = 1.05F;
	/** Once a fall has started it lasts until the drop slows under this (so a bump doesn't stop and start it). */
	static final float FALL_KEEP = 0.35F;
	/** A drop to the ground under this many blocks is a short one; this many or more a long one. */
	static final double MEDIUM_DROP = 12.0, LONG_DROP = 48.0;
	/** A long fall settles after this many ticks of panic, and changes again after this many. */
	static final float SETTLE_TICKS = 32.0F, SECOND_TICKS = 110.0F;
	/** Ticks before the ground that a fall (not a short drop) braces for it; never in the first {@code BRACE_AFTER} ticks. */
	static final float BRACE_TICKS = 12.0F, BRACE_AFTER = 8.0F;
	/** Ticks one animation takes to turn into the next; the brace comes on faster. */
	static final float BLEND_TICKS = 8.0F, BRACE_BLEND = 5.0F;
	/** Ticks the fall eases out over after landing (or slowing). */
	static final float LAND_FADE = 5.0F;
	/** How far down (blocks) the ground is looked for under an overhang, and how often (ticks) it's looked for again. */
	static final int SCAN_DEPTH = 160;
	static final float SCAN_EVERY = 5.0F;
	/** A fall's tracking is dropped when its body hasn't been drawn this long (nanoseconds); how often that's looked for. */
	static final long FORGET_NANOS = 3_000_000_000L, SWEEP_NANOS = 1_000_000_000L;

	/**
	 * A wind's dive tips over gradually as the fall speeds up: from {@code DIVE_START} degrees as it begins to
	 * {@code DIVE_PITCH} (head pointing down at the ground, not lying flat) by a fall of {@code DIVE_FULL} blocks/tick.
	 */
	static final float DIVE_START = 20.0F, DIVE_PITCH = 150.0F, DIVE_FULL = 2.6F;
	// ================================================================================================================

	/**
	 * One frame of a fall. {@code strength}: how far on (0-1); {@code time}: the animation clock (ticks); {@code dive}:
	 * a wind's or Leap of Faith's dive, and {@code tip} how far over it's gone (0-1). Otherwise {@code from} turning into
	 * {@code to} by {@code mix}, each {@code fromAge}/{@code toAge} ticks in; the body's tilt when {@code to} began
	 * ({@code fromPitch}, {@code fromRoll}, {@code fromYaw}: what it turns from); {@code side} 1 or -1 (mirrored); {@code seed}:
	 * the fall's own number (a tumble's speed);
	 * {@code tilt}: the whole body may be turned; {@code arms}: the arms are free (not holding a carried body).
	 */
	public record Fall(float strength, float time, boolean dive, float tip, Style from, Style to, float mix, float fromAge,
		float toAge, float fromPitch, float fromRoll, float fromYaw, int side, int seed, boolean tilt, boolean arms) {
		static Fall dive(float strength, float time, float tip) {
			return new Fall(strength, time, true, tip, Style.WINDMILL, Style.WINDMILL, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1, 0, true, true);
		}
	}

	public static final RenderStateDataKey<Fall> KEY = RenderStateDataKey.create(() -> "jaymospowers:freefall");

	/** How far there is to fall, judged when the fall starts. */
	enum Drop {
		SHORT, MEDIUM, LONG
	}

	/** What one falling body is doing, on this client. */
	private static final class Track {
		final double startY;
		final int seed;
		final float start;
		Drop drop;
		Style panic, settle, second;
		Style from, to;
		float changedAt, fromChangedAt;
		float snapPitch, snapRoll, snapYaw;
		float lastSeen, landedAt = -1.0F, strength;
		long seenNanos;
		float scannedAt = -1000.0F;
		double groundY = Double.NaN;
		boolean dive;
		float tip;

		Track(LivingEntity entity, float now) {
			this.start = now;
			this.startY = entity.getY();
			this.seed = Mth.murmurHash3Mixer(entity.getId() * 31 + Mth.floor(entity.getY()));
			this.changedAt = now;
			this.fromChangedAt = now;
		}
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static long lastSweep;

	/** The short drop's animations; a longer fall's panic; a long fall's settled shapes. */
	private static final Style[] SHORT_POOL = {Style.SPREAD, Style.AIR_RUN, Style.KNEES, Style.WINDMILL, Style.GRAB};
	private static final Style[] PANIC_POOL = {Style.WINDMILL, Style.AIR_RUN, Style.SPREAD, Style.BACKFLOP, Style.GRAB};
	private static final Style[] LONG_POOL = {Style.ARCH, Style.TUMBLE, Style.SPIN, Style.TURTLE, Style.PENCIL, Style.LIMP};

	private FreefallPose() {
	}

	// ---- reading the fall ---------------------------------------------------------------------------------------------

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(KEY, null);
		float now = entity.tickCount + partialTicks;
		long nanos = System.nanoTime();
		sweep(nanos);
		int id = entity.getId();
		Track track = TRACKS.get(id);
		if (track != null && now < track.lastSeen - 1.0F) {
			// the same id on a body made anew (it came back into view): start over
			TRACKS.remove(id);
			track = null;
		}
		float falling = (float)(entity.yo - entity.getY());
		if (track == null && falling < FALL_START) {
			// the quick way out for everyone standing about
			return;
		}
		if (!person(entity) || otherwise(entity)) {
			// something else has the body (or this isn't a body that falls this way): it takes over at once
			TRACKS.remove(id);
			return;
		}
		boolean dive = entity instanceof Player player && dives(player);
		if (dive) {
			TRACKS.remove(id);
			float strength = Mth.clamp((falling - START) / (FULL - START), 0.0F, 1.0F);
			float tip = Mth.clamp((falling - START) / (DIVE_FULL - START), 0.0F, 1.0F);
			if (strength > 0.0F) {
				state.setData(KEY, Fall.dive(strength, now, tip * tip * (3.0F - 2.0F * tip)));
			}
			return;
		}
		boolean inAir = !entity.onGround() && falling >= (track == null ? FALL_START : FALL_KEEP);
		if (track == null) {
			track = new Track(entity, now);
			look(entity, track, now);
			begin(entity, track);
			TRACKS.put(id, track);
		}
		track.lastSeen = now;
		track.seenNanos = nanos;
		boolean arms = !com.jaymospowers.core.Carry.isCarrying(entity);
		boolean tilt = arms && !ownView(entity);
		if (inAir) {
			track.landedAt = -1.0F;
			if (now - track.scannedAt >= SCAN_EVERY) {
				look(entity, track, now);
			}
			step(entity, track, now, falling);
			track.strength = Mth.clamp((falling - FALL_START) / (FALL_FULL - FALL_START), track.strength, 1.0F);
			state.setData(KEY, frame(track, now, track.strength, tilt, arms));
			return;
		}
		// landed (or slowed): ease out
		if (track.landedAt < 0.0F) {
			track.landedAt = now;
		}
		float fade = 1.0F - (now - track.landedAt) / LAND_FADE;
		if (fade <= 0.0F) {
			TRACKS.remove(id);
			return;
		}
		state.setData(KEY, frame(track, now, track.strength * fade * fade, tilt, arms));
	}

	/** Who falls this way: players and anything built like a person. */
	static boolean person(LivingEntity entity) {
		return entity instanceof Player || entity instanceof AbstractVillager
			|| com.jaymospowers.core.wounds.BodyPlan.of(entity) == com.jaymospowers.core.wounds.BodyPlan.HUMANOID;
	}

	/** Anything else that has the body now, or a fall that isn't one (flying, gliding, riding, in water...). */
	static boolean otherwise(LivingEntity entity) {
		if (!entity.isAlive() || entity.deathTime > 0 || entity.isPassenger() || entity.isFallFlying() || entity.isSwimming()
			|| entity.isInWater() || entity.isInLava() || entity.onClimbable() || entity.isNoGravity() || entity.isSleeping()
			|| entity.hasEffect(MobEffects.SLOW_FALLING) || entity.hasEffect(MobEffects.LEVITATION)) {
			return true;
		}
		// downed (DownedPose lays them out as they go), carried, thrown (ThrownFlight tumbles them), limp (Kitchen's ragdoll)
		if (com.jaymospowers.core.wounds.Downed.isDowned(entity) || com.jaymospowers.core.Carry.isCarried(entity)
			|| com.jaymospowers.core.Carry.isThrown(entity) || com.jaymospowers.power.web.KitchenCompat.isLimp(entity)) {
			return true;
		}
		// held: on a web line, in a symbiote's grab, by Lift, by the throat (Kingdoms' BloodGrab)
		if (entity.hasAttached(com.jaymospowers.power.web.WebPower.HELD) || entity.hasAttached(com.jaymospowers.power.web.WebHeldGrab.HELD)
			|| entity.hasAttached(com.jaymospowers.power.gravity.GravityPower.HELD) || entity.hasAttached(com.jaymospowers.power.gravity.GravityPower.LIFTED)
			|| com.jaymospowers.compat.KingdomsCompat.grabbed(entity)) {
			return true;
		}
		if (!(entity instanceof Player player)) {
			return false;
		}
		if (player.isSpectator() || player.getAbilities().flying || controlled(player)
			// a ledge hang or climb, a freerun move (wall run, bar hang...), a web's ragdoll after the lines snap
			|| com.jaymospowers.core.Ledge.onLedge(player) || player.hasAttached(com.jaymospowers.freerun.Freerun.MOVE)
			|| player.hasAttached(com.jaymospowers.freerun.Freerun.HANG) || player.hasAttached(com.jaymospowers.freerun.Freerun.WALL)
			|| com.jaymospowers.power.web.WebPower.ragdolled(player)) {
			return true;
		}
		com.jaymospowers.power.wind.WindMoves.Move move = player.getAttached(com.jaymospowers.power.wind.WindMoves.MOVE);
		// a Spiral Dive has its own body (WindMovePose): head first and spinning
		return move != null && move.kind() == com.jaymospowers.power.wind.WindMoves.DIVE;
	}

	/** A wind user, or a freerunner with Leap of Faith falling looking down: the dive, not a flail. */
	private static boolean dives(Player player) {
		return com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.wind.WindPower.INSTANCE)
			|| com.jaymospowers.freerun.Freerun.dives(player);
	}

	/** Falling on purpose: a wind's flight, web wings or lines, a rock slab, Weightless, a forward fall, a wall walk. */
	private static boolean controlled(Player player) {
		return player.getAttached(com.jaymospowers.power.wind.WindPower.FLIGHT) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.WINGS) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.LINE) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.LINE_OFF) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.ROCK_FLIGHT) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.WEIGHTLESS_ON) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityForward.FALLING) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityWalk.SURFACE) != null
			|| player.getAttached(com.jaymospowers.power.chrono.ChronoSpeed.WALL_RUN) != null;
	}

	/** Your own body in first person (First-person Model shows it): its limbs move, but it isn't tipped over round the camera. */
	private static boolean ownView(LivingEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
	}

	/** Where the ground is under the body (NaN: not in sight, unloaded or too far down). */
	private static void look(LivingEntity entity, Track track, float now) {
		track.scannedAt = now;
		Level level = entity.level();
		int x = Mth.floor(entity.getX()), z = Mth.floor(entity.getZ());
		double y = entity.getY();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		if (top <= level.getMinY()) {
			track.groundY = Double.NaN;
			return;
		}
		if (top <= y + 0.5) {
			track.groundY = top;
			return;
		}
		// something overhead (an overhang, the island fallen from): look straight down instead
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.floor(y), z);
		for (int i = 0; i < SCAN_DEPTH && pos.getY() > level.getMinY(); i++) {
			pos.move(0, -1, 0);
			BlockState block = level.getBlockState(pos);
			if (!block.getCollisionShape(level, pos).isEmpty() || !block.getFluidState().isEmpty()) {
				track.groundY = pos.getY() + 1.0;
				return;
			}
		}
		track.groundY = Double.NaN;
	}

	/** The falling pose this body's player chose with the Sit key in midair, or null (none: random picks). */
	static @Nullable Style chosen(LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		Integer pick = player.getAttached(Sitting.FALL_POSE);
		return pick == null || pick < 0 || pick >= Sitting.FALL_POSES ? null : Style.values()[pick];
	}

	/**
	 * For the Sit key: -2 if this player isn't falling the way these poses show (on the ground, flying, diving, held...:
	 * the key sits as usual), else the pose their fall shows now (-1: none yet), to go on to the next.
	 */
	public static int fallKeyShown(Player player) {
		if (player.onGround() || !person(player) || otherwise(player) || dives(player)) {
			return -2;
		}
		Track track = TRACKS.get(player.getId());
		Style shown = track == null ? null : track.to != Style.IMPACT ? track.to : track.from != Style.IMPACT ? track.from : null;
		if (shown == null) {
			shown = chosen(player);
		}
		return shown == null ? -1 : shown.ordinal();
	}

	/** The fall's animations, picked when it starts (all one chosen pose, if the player chose one). */
	private static void begin(LivingEntity entity, Track track) {
		double drop = Double.isNaN(track.groundY) ? Double.MAX_VALUE : track.startY - track.groundY;
		track.drop = drop < MEDIUM_DROP ? Drop.SHORT : drop < LONG_DROP ? Drop.MEDIUM : Drop.LONG;
		int seed = track.seed;
		track.panic = (track.drop == Drop.SHORT ? SHORT_POOL : PANIC_POOL)[Math.floorMod(seed, track.drop == Drop.SHORT ? SHORT_POOL.length : PANIC_POOL.length)];
		track.settle = LONG_POOL[Math.floorMod(seed >>> 8, LONG_POOL.length)];
		// the second is never the first again
		track.second = LONG_POOL[Math.floorMod(Math.floorMod(seed >>> 16, LONG_POOL.length - 1) + 1 + track.settle.ordinal() - Style.ARCH.ordinal(), LONG_POOL.length)];
		Style chosen = chosen(entity);
		if (chosen != null) {
			track.panic = chosen;
			track.settle = chosen;
			track.second = chosen;
		}
		track.from = track.panic;
		track.to = track.panic;
	}

	/** Which animation the fall wants now: the panic, the long fall's shapes, the brace. */
	private static void step(LivingEntity entity, Track track, float now, float falling) {
		float age = now - track.start;
		boolean known = !Double.isNaN(track.groundY);
		double below = known ? entity.getY() - track.groundY : Double.MAX_VALUE;
		Style want;
		Style chosen = chosen(entity);
		if (track.drop != Drop.SHORT && known && age > BRACE_AFTER && below / Math.max(falling, 0.1F) < BRACE_TICKS) {
			want = Style.IMPACT;
		} else if (chosen != null) {
			// chosen with the Sit key (maybe just now, mid-fall: it turns in like any other change)
			want = chosen;
		} else if (age > SECOND_TICKS && (track.drop == Drop.LONG || below > LONG_DROP * 0.5)) {
			want = track.second;
		} else if (age > SETTLE_TICKS && (track.drop == Drop.LONG || below > LONG_DROP * 0.5)) {
			// a fall that turned out longer than it looked settles too
			want = track.settle;
		} else {
			want = track.panic;
		}
		if (want != track.to) {
			// the tilt it turns from is where the body is now; the limbs turn from the old animation as it goes on
			int side = side(track);
			float mix = mix(track, now);
			track.snapPitch = blendAngle(track.snapPitch, track.to.pitch(now - track.changedAt, now, track.seed), mix);
			track.snapRoll = blendAngle(track.snapRoll, side * track.to.roll(now - track.changedAt, now, track.seed), mix);
			track.snapYaw = blendAngle(track.snapYaw, side * track.to.yaw(now - track.changedAt, now, track.seed), mix);
			track.from = track.to;
			track.fromChangedAt = track.changedAt;
			track.to = want;
			track.changedAt = now;
		}
	}

	private static int side(Track track) {
		return (track.seed & 0x1000000) != 0 ? -1 : 1;
	}

	private static float mix(Track track, float now) {
		float blend = track.to == Style.IMPACT ? BRACE_BLEND : BLEND_TICKS;
		float m = Mth.clamp((now - track.changedAt) / blend, 0.0F, 1.0F);
		return m * m * (3.0F - 2.0F * m);
	}

	private static Fall frame(Track track, float now, float strength, boolean tilt, boolean arms) {
		return new Fall(strength, now, false, 0.0F, track.from, track.to, mix(track, now), now - track.fromChangedAt,
			now - track.changedAt, track.snapPitch, track.snapRoll, track.snapYaw, side(track), track.seed, tilt, arms);
	}

	/** Falls whose bodies haven't been drawn for a while are forgotten (by the real clock: each body counts its own ticks). */
	private static void sweep(long nanos) {
		if (nanos - lastSweep < SWEEP_NANOS) {
			return;
		}
		lastSweep = nanos;
		TRACKS.values().removeIf(track -> nanos - track.seenNanos > FORGET_NANOS);
	}

	/** {@code a} turned toward {@code b} by {@code t} the short way round (degrees). */
	static float blendAngle(float a, float b, float t) {
		return a + Mth.wrapDegrees(b - a) * t;
	}

	// ---- drawing it ---------------------------------------------------------------------------------------------------

	/** After the body faces its way: the whole body tipped, rolled and spun about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Fall fall = state.getData(KEY);
		if (fall == null || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		if (fall.dive()) {
			poseStack.translate(0.0F, middle, 0.0F);
			// (a negative turn about X tips the top forward)
			poseStack.rotateDegrees(Axis.XP, -Mth.lerp(fall.tip(), DIVE_START * fall.strength(), DIVE_PITCH));
			poseStack.translate(0.0F, -middle, 0.0F);
			return;
		}
		if (!fall.tilt() || yields(state)) {
			return;
		}
		Style to = fall.to();
		int side = fall.side();
		int seed = fall.seed();
		float pitch = blendAngle(fall.fromPitch(), to.pitch(fall.toAge(), fall.time(), seed), fall.mix());
		float roll = blendAngle(fall.fromRoll(), side * to.roll(fall.toAge(), fall.time(), seed), fall.mix());
		float yaw = blendAngle(fall.fromYaw(), side * to.yaw(fall.toAge(), fall.time(), seed), fall.mix());
		float s = fall.strength();
		pitch = Mth.wrapDegrees(pitch) * s;
		roll = Mth.wrapDegrees(roll) * s;
		yaw = Mth.wrapDegrees(yaw) * s;
		if (pitch == 0.0F && roll == 0.0F && yaw == 0.0F) {
			return;
		}
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.YP, yaw);
		// (a negative turn about X tips the top forward: pitch is forward, face toward the ground)
		poseStack.rotateDegrees(Axis.XP, -pitch);
		poseStack.rotateDegrees(Axis.ZP, roll);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** A landing roll (Sure Footing), a ledge caught or a crawl, as the fall eases out on landing: those have the body. */
	private static boolean yields(LivingEntityRenderState state) {
		return state.getData(com.jaymospowers.client.stamina.DodgeRollPose.KEY) != null || state.getData(LedgePose.KEY) != null
			|| state.getData(CrawlPose.KEY) != null;
	}

	/** After the model animates (and before any power's pose, which may take the body over). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Fall fall = living.getData(KEY);
		if (fall == null || living.deathTime > 0.0F || yields(living)) {
			return;
		}
		if (model instanceof HumanoidModel<?> h) {
			if (PosedCopy.of(h)) {
				// MCA's armour copies the body's pose, which already has the fall
				return;
			}
			if (fall.dive()) {
				dive(h, fall);
				return;
			}
			lay(new ModelPart[] {h.head, h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg}, null, fall);
			return;
		}
		if (fall.dive()) {
			return;
		}
		ModelPart[] parts = PoseBlend.parts(model);
		ModelPart root = model.root();
		if (parts != null) {
			// built like a person but not a HumanoidModel (an illager: its crossed arms, shown when it isn't fighting, too)
			lay(parts, root.hasChild("arms") ? root.getChild("arms") : null, fall);
			return;
		}
		// a villager's (or a witch's, a wandering trader's) folded arms are one part
		if (root.hasChild("head") && root.hasChild("right_leg") && root.hasChild("left_leg")) {
			ModelPart[] some = {root.getChild("head"), null, null, null, root.getChild("right_leg"), root.getChild("left_leg")};
			lay(some, root.hasChild("arms") ? root.getChild("arms") : null, fall);
		}
	}

	/** The wind's dive: streamlined, arms swept back along the sides, legs together, chin up to see where it's going. */
	private static void dive(HumanoidModel<?> h, Fall fall) {
		float s = fall.strength(), t = fall.time();
		set(h.rightArm, 0.35F, 0.0F, 0.25F, s);
		set(h.leftArm, 0.35F, 0.0F, -0.25F, s);
		set(h.rightLeg, 0.05F + Mth.sin(t * 0.3F) * 0.04F, 0.0F, 0.03F, s);
		set(h.leftLeg, 0.05F - Mth.sin(t * 0.3F) * 0.04F, 0.0F, -0.03F, s);
		h.head.xRot = Mth.lerp(s, h.head.xRot, -0.9F);
	}

	/** Lays the fall's limbs over {@code parts} (head, body, arms, legs; any may be missing) by its strength. */
	private static void lay(@Nullable ModelPart[] parts, @Nullable ModelPart foldedArms, Fall fall) {
		float[] to = new float[Style.SLOTS];
		fall.to().limbs(to, fall.toAge(), fall.time(), fall.side());
		if (fall.mix() < 1.0F) {
			float[] from = new float[Style.SLOTS];
			fall.from().limbs(from, fall.fromAge(), fall.time(), fall.side());
			for (int i = 0; i < Style.SLOTS; i++) {
				to[i] = Mth.lerp(fall.mix(), from[i], to[i]);
			}
		}
		float s = fall.strength();
		for (int p = 0; p < parts.length; p++) {
			ModelPart part = parts[p];
			if (part == null || !fall.arms() && (p == 2 || p == 3)) {
				continue;
			}
			set(part, to[p * 3], to[p * 3 + 1], to[p * 3 + 2], s);
		}
		if (foldedArms != null && fall.arms()) {
			// both arms in one: they go up as the two arms would (on average), less far (the hands are joined)
			foldedArms.xRot = Mth.lerp(s * 0.8F, foldedArms.xRot, (to[Style.RIGHT_ARM] + to[Style.LEFT_ARM]) * 0.5F);
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	// ---- the animations -----------------------------------------------------------------------------------------------

	/**
	 * One falling animation: the limbs ({@link #limbs}: head, body, right arm, left arm, right leg, left leg, each x, y, z
	 * in radians, as the right-handed version; {@code side} -1 mirrors it) and the whole body's tilt (degrees: {@link #pitch}
	 * forward, face to the ground; {@link #roll} over to the right; {@link #yaw} turned to the left). {@code age}: ticks
	 * since it began; {@code t}: the animation clock.
	 */
	/** (The Sit key's falling poses are the first {@link Sitting#FALL_POSES} of these, in this order: the brace stays last.) */
	public enum Style {
		/** Our own flail (this class's first one): arms thrown up and out, windmilling, legs kicking apart, looking down at the ground. */
		WINDMILL {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.7F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -2.6F + Mth.sin(t * 0.9F) * 0.5F, 0.0F, 0.9F + Mth.cos(t * 0.7F) * 0.35F);
				put(o, LEFT_ARM, -2.6F + Mth.sin(t * 0.9F + 2.0F) * 0.5F, 0.0F, -0.9F - Mth.cos(t * 0.7F + 1.0F) * 0.35F);
				put(o, RIGHT_LEG, Mth.sin(t * 1.1F) * 0.7F, 0.0F, 0.25F);
				put(o, LEFT_LEG, -Mth.sin(t * 1.1F) * 0.7F, 0.0F, -0.25F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return Mth.sin(t * 0.3F) * 4.0F;
			}
		},
		/** Not Enough Animations' fall, remade: running on air, arms and legs pumping as if the ground were still there. */
		AIR_RUN {
			@Override
			void pose(float[] o, float age, float t) {
				float c = Mth.cos(t * 0.85F);
				put(o, HEAD, 0.35F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -1.1F + c * 1.0F, 0.0F, 0.35F);
				put(o, LEFT_ARM, -1.1F - c * 1.0F, 0.0F, -0.35F);
				put(o, RIGHT_LEG, -0.35F - c * 1.0F, 0.0F, 0.08F);
				put(o, LEFT_LEG, -0.35F + c * 1.0F, 0.0F, -0.08F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return 6.0F;
			}
		},
		/** Fresh Animations: Player Extension's fall, remade: arms flung out wide to the sides, waving slowly, legs paddling. */
		SPREAD {
			@Override
			void pose(float[] o, float age, float t) {
				float w = Mth.sin(t * 0.35F), c = Mth.cos(t * 0.35F);
				put(o, HEAD, 0.3F, 0.0F, 0.0F);
				put(o, BODY, 0.0F, w * 0.12F, 0.0F);
				put(o, RIGHT_ARM, -0.15F + w * 0.3F, 0.3F, 1.75F - c * 0.18F);
				put(o, LEFT_ARM, -0.15F - w * 0.3F, -0.3F, -1.9F + c * 0.18F);
				put(o, RIGHT_LEG, -w * 0.35F, 0.15F, 0.09F);
				put(o, LEFT_LEG, w * 0.35F, -0.15F, -0.09F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return -5.0F;
			}

			@Override
			float roll(float age, float t, int seed) {
				return Mth.sin(t * 0.35F) * 4.0F;
			}
		},
		/** A short drop: knees tucked up ready for the ground, arms out in front for balance, eyes on where they'll land. */
		KNEES {
			@Override
			void pose(float[] o, float age, float t) {
				float w = Mth.sin(t * 0.5F) * 0.15F;
				put(o, HEAD, 0.55F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -1.3F + w, 0.0F, 0.45F);
				put(o, LEFT_ARM, -1.3F - w, 0.0F, -0.45F);
				put(o, RIGHT_LEG, -1.2F, 0.0F, 0.12F);
				put(o, LEFT_LEG, -0.9F, 0.0F, -0.12F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return 5.0F;
			}
		},
		/** Grabbing back up at the edge they fell from: both arms straight up and clawing, looking up, legs kicking. */
		GRAB {
			@Override
			void pose(float[] o, float age, float t) {
				float claw = Mth.sin(t * 1.3F);
				put(o, HEAD, -0.9F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -3.0F + claw * 0.25F, 0.0F, 0.25F + claw * 0.1F);
				put(o, LEFT_ARM, -3.0F - claw * 0.25F, 0.0F, -0.25F + claw * 0.1F);
				put(o, RIGHT_LEG, Mth.sin(t * 0.9F) * 0.45F, 0.0F, 0.05F);
				put(o, LEFT_LEG, -Mth.sin(t * 0.9F) * 0.45F, 0.0F, -0.05F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return -8.0F;
			}
		},
		/** Flopped over onto the back, arms and legs up and reaching, chin down to see the feet. */
		BACKFLOP {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.6F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -1.9F + Mth.sin(t * 0.8F) * 0.3F, 0.0F, 0.6F);
				put(o, LEFT_ARM, -1.9F + Mth.sin(t * 0.8F + 1.5F) * 0.3F, 0.0F, -0.6F);
				put(o, RIGHT_LEG, -1.0F + Mth.sin(t * 1.0F) * 0.35F, 0.0F, 0.2F);
				put(o, LEFT_LEG, -0.7F - Mth.sin(t * 1.0F) * 0.35F, 0.0F, -0.2F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return -55.0F * PoseBlend.smooth(Math.min(1.0F, age / 10.0F)) + Mth.sin(t * 0.25F) * 4.0F;
			}
		},
		/** Our Sky Dive move's shape, for anyone: belly to the ground, arms out and forward, knees bent, head up. */
		ARCH {
			@Override
			void pose(float[] o, float age, float t) {
				float flutter = Mth.sin(t * 0.9F) * 0.06F;
				put(o, HEAD, -1.1F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -0.55F + flutter, 0.0F, 1.25F);
				put(o, LEFT_ARM, -0.55F - flutter, 0.0F, -1.25F);
				put(o, RIGHT_LEG, 0.45F, 0.0F, 0.35F + flutter);
				put(o, LEFT_LEG, 0.45F, 0.0F, -0.35F - flutter);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return 80.0F + Mth.sin(t * 0.2F) * 3.0F;
			}

			@Override
			float roll(float age, float t, int seed) {
				return Mth.sin(t * 0.13F) * 5.0F;
			}
		},
		/** The thrown body's tumble (ThrownFlight), for a long fall: end over end, the limbs flung loose and trailing. */
		TUMBLE {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.3F + Mth.sin(t * 0.5F) * 0.3F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -2.2F + Mth.sin(t * 0.6F) * 0.6F, 0.0F, 1.1F + Mth.cos(t * 0.5F) * 0.4F);
				put(o, LEFT_ARM, -1.5F + Mth.sin(t * 0.7F + 1.0F) * 0.6F, 0.0F, -1.3F);
				put(o, RIGHT_LEG, -0.6F + Mth.sin(t * 0.8F) * 0.5F, 0.0F, 0.4F);
				put(o, LEFT_LEG, 0.3F + Mth.sin(t * 0.9F + 2.0F) * 0.5F, 0.0F, -0.3F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				// a turn every two seconds or so, a little faster or slower for each fall
				return age * (7.0F + Math.floorMod(seed >>> 4, 5));
			}

			@Override
			float roll(float age, float t, int seed) {
				return Mth.sin(t * 0.11F) * 12.0F;
			}
		},
		/** A flat spin: laid out face down and turning round and round, limbs flung straight out by the spin. */
		SPIN {
			@Override
			void pose(float[] o, float age, float t) {
				float w = Mth.sin(t * 0.4F) * 0.1F;
				put(o, HEAD, -0.6F + w, 0.0F, 0.3F);
				put(o, RIGHT_ARM, -0.2F, 0.0F, 1.5F + w);
				put(o, LEFT_ARM, -0.2F, 0.0F, -1.5F + w);
				put(o, RIGHT_LEG, 0.1F, 0.0F, 0.55F);
				put(o, LEFT_LEG, 0.1F, 0.0F, -0.55F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return 70.0F;
			}

			@Override
			float yaw(float age, float t, int seed) {
				// spinning up over the first two seconds
				float spin = Math.min(age, 40.0F);
				return spin * spin * 0.18F + Math.max(0.0F, age - 40.0F) * 14.0F;
			}
		},
		/** Curled up tight, arms wrapped over the head and knees to the chest, slowly rolling forward. */
		TURTLE {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.6F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -2.6F, -0.4F, -0.5F);
				put(o, LEFT_ARM, -2.6F, 0.4F, 0.5F);
				put(o, RIGHT_LEG, -1.7F, 0.0F, 0.1F);
				put(o, LEFT_LEG, -1.7F, 0.0F, -0.1F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return age * 4.5F;
			}
		},
		/** A pencil drop: feet first, arms straight up overhead, legs together, swaying a little. */
		PENCIL {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, -0.2F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -3.1F, 0.0F, 0.12F);
				put(o, LEFT_ARM, -3.1F, 0.0F, -0.12F);
				put(o, RIGHT_LEG, 0.05F, 0.0F, 0.02F);
				put(o, LEFT_LEG, 0.05F, 0.0F, -0.02F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return Mth.sin(t * 0.15F) * 6.0F;
			}

			@Override
			float roll(float age, float t, int seed) {
				return Mth.sin(t * 0.11F) * 5.0F;
			}
		},
		/**
		 * Gone limp in the long drop (as the ragdolls go: Kitchen's limp body, the web's ragdoll): back to the ground, the
		 * air pushing the arms and legs up, head lolled, drifting round.
		 */
		LIMP {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.5F, 0.0F, 0.2F + Mth.sin(t * 0.2F) * 0.1F);
				put(o, RIGHT_ARM, -1.5F + Mth.sin(t * 0.5F) * 0.15F, 0.0F, 0.5F);
				put(o, LEFT_ARM, -1.3F + Mth.sin(t * 0.45F + 2.0F) * 0.15F, 0.0F, -0.6F);
				put(o, RIGHT_LEG, -0.9F + Mth.sin(t * 0.4F) * 0.1F, 0.0F, 0.25F);
				put(o, LEFT_LEG, -0.6F, 0.0F, -0.2F);
			}

			@Override
			float pitch(float age, float t, int seed) {
				return -75.0F + Mth.sin(t * 0.1F) * 8.0F;
			}

			@Override
			float roll(float age, float t, int seed) {
				return Mth.sin(t * 0.07F) * 10.0F;
			}

			@Override
			float yaw(float age, float t, int seed) {
				return age * 1.5F;
			}
		},
		/** Bracing for the ground: upright, legs together and ready, arms crossed over the head, chin tucked. */
		IMPACT {
			@Override
			void pose(float[] o, float age, float t) {
				put(o, HEAD, 0.5F, 0.0F, 0.0F);
				put(o, RIGHT_ARM, -2.5F, -0.5F, -0.3F);
				put(o, LEFT_ARM, -2.5F, 0.5F, 0.3F);
				put(o, RIGHT_LEG, -0.45F, 0.0F, 0.03F);
				put(o, LEFT_LEG, -0.35F, 0.0F, -0.03F);
			}
		};

		static final int HEAD = 0, BODY = 3, RIGHT_ARM = 6, LEFT_ARM = 9, RIGHT_LEG = 12, LEFT_LEG = 15, SLOTS = 18;

		/** The right-handed version's limbs. */
		abstract void pose(float[] out, float age, float t);

		/** Degrees tipped forward (face to the ground; negative: onto the back). */
		float pitch(float age, float t, int seed) {
			return 0.0F;
		}

		/** Degrees rolled over to the right (the mirrored version rolls left). */
		float roll(float age, float t, int seed) {
			return 0.0F;
		}

		/** Degrees turned (the mirrored version turns the other way). */
		float yaw(float age, float t, int seed) {
			return 0.0F;
		}

		/** The limbs, mirrored when {@code side} is -1. */
		final void limbs(float[] out, float age, float t, int side) {
			this.pose(out, age, t);
			if (side < 0) {
				mirror(out);
			}
		}

		private static void put(float[] o, int part, float x, float y, float z) {
			o[part] = x;
			o[part + 1] = y;
			o[part + 2] = z;
		}

		/** Left for right: the arms and legs swap sides, and every turn about y and z goes the other way. */
		private static void mirror(float[] o) {
			swap(o, RIGHT_ARM, LEFT_ARM);
			swap(o, RIGHT_LEG, LEFT_LEG);
			for (int p = 0; p < SLOTS; p += 3) {
				o[p + 1] = -o[p + 1];
				o[p + 2] = -o[p + 2];
			}
		}

		private static void swap(float[] o, int a, int b) {
			for (int i = 0; i < 3; i++) {
				float v = o[a + i];
				o[a + i] = o[b + i];
				o[b + i] = v;
			}
		}
	}
}
