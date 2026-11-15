package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.DownedLie;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-15): "can we make downed players and mobs sit against a wall?" Someone downed beside a wall
 * ({@link DownedLie#wall}) sits with their back to it: turned to face out of it, slid back against it and down onto the
 * floor, leaning back on it, legs out ahead, arms fallen at their sides, head lolled forward and to one side, breathing
 * hard. A downed mob that doesn't crawl sits as it goes down ({@link DownedPose}). A body that dies sat there stays sat
 * (Jaymo's Kitchen's corpse, {@code DownedLie#markBody}).
 * <p>
 * Andy (2026-11-15): "the view needs to move to the exact height on the model. other players should be able to place
 * them on the wall, and there should be a smooth animation for the 1.5s it takes to get onto the wall." A downed player
 * who lies still beside a wall gets up against it over {@link #IN_TICKS}: rolls over onto their back, swings round, sits
 * up and shuffles back to it. One that another sets down against a wall ({@link DownedLie#PROPPED}) is stood there and
 * slides down it into the sit, out cold or not; one dragged there and let go gets up against it from the floor. Crawling
 * off, the sit plays back down to the floor ({@link #OUT_TICKS}). In first person the view is at the model's own eyes
 * ({@link #eye}).
 */
public final class WallSit {
	/** How far it leans back against the wall (radians). */
	static final float LEAN = 0.22F;
	/** How far it sinks (share of its height): its hips down onto the floor. */
	static final float SINK = 0.31F;
	/** Its back sits this far from the wall (model pixels of a 32-pixel body, plus the lean), so neither it nor its head goes in. */
	static final float BACK = 3.0F, SHOULDERS = 12.0F;
	/** Legs out ahead along the floor, turned out this much (radians) and a little more or less each. */
	static final float LEG_SPLAY = 0.18F, LEG_SPREAD = 0.12F;
	/** Arms fallen at its sides: forward, out (radians). */
	static final float ARM_FORWARD = -0.2F, ARM_OUT = 0.22F;
	/** The head: dropped forward, tipped to one side, turned to it (radians). */
	static final float HEAD_DROP = 0.55F, HEAD_TIP = 0.3F, HEAD_TURN = 0.25F;
	/**
	 * And hung forward off the neck this far (model pixels): the head is twice as deep as the body, so with the back to the
	 * wall it would go into it otherwise.
	 */
	static final float HEAD_FORWARD = 2.0F;
	/** Hard breathing: beats per tick and depth (radians, on the arms and head). */
	static final float BREATH_RATE = 0.18F, BREATH_DEPTH = 0.05F;

	// ---- downed players

	/** A downed player lies still this long beside a wall before they start getting up against it (ticks). */
	static final float STILL_TICKS = 4.0F;
	/** Getting up against the wall from the floor; set against it, sliding down it; back down off it to crawl away (ticks). */
	static final float IN_TICKS = 30.0F, PLACE_TICKS = 16.0F, OUT_TICKS = 12.0F;
	/** Moved further than this in a tick (blocks), a downed player is crawling, not resting. */
	static final double STILL_MOVE = 0.003;
	/** Just set against the wall, their being moved there isn't taken for crawling off for this long (ticks). */
	static final float SET_GRACE = 6.0F;
	/**
	 * Getting up from the floor, the stages (shares of it): rolled over onto the back; sat up, the hips coming down onto
	 * the floor; turned round on them to put the back to the wall; pushed back to it twice on the hands ({@link WallSitArms});
	 * the head lolled at the end. The limbs are taken over from the crawl's by {@code LIMBS_END}.
	 */
	static final float ROLL_FROM = 0.04F, ROLL_END = 0.34F, SIT_FROM = 0.22F, SIT_END = 0.5F, HIPS_FROM = 0.25F, HIPS_END = 0.55F,
		TURN_FROM = 0.42F, TURN_END = 0.68F, PUSH_1_FROM = 0.7F, PUSH_1_END = 0.78F, PUSH_2_FROM = 0.82F, PUSH_2_END = 0.9F,
		LOLL_FROM = 0.86F, LIMBS_END = 0.25F;
	/** How much of the way back to the wall the first push goes; how far each lifts the hips off the floor (blocks). */
	static final float FIRST_PUSH = 0.65F, SCOOT_LIFT = 0.015F;
	/** Sat up, it leans back on its hands as far as the wall behind lets it, but no further than this (radians). */
	static final float PROP = 0.55F;
	/** Rolling over, the body comes up off the floor this much on its shoulder (blocks), so the arm under it doesn't go in. */
	static final float ROLL_LIFT = 0.2F;
	/** Getting up, the chin's tucked (radians). */
	static final float CHIN = 0.35F;
	/** First person: the view moves over from vanilla's crawl eye onto the model's eyes in this first share of getting up. */
	static final float VIEW_END = 0.3F;
	/**
	 * The player's model, in blocks at their own scale before vanilla's tilt (its pixels are 0.9375/16): the hips, the
	 * neck, vanilla's eyes (the view's height, in the head's middle), the legs' length and half their thickness; the hips'
	 * height lying face down in vanilla's crawl, and sat (on the backs of the thighs); and how far their middle is behind
	 * the body's in the crawl.
	 */
	static final float PX = 0.9375F / 16.0F, HIP = 0.7041F, NECK = 1.4072F, EYE = 1.62F, LEG = 12.0F * PX, LEG_HALF = 2.0F * PX,
		CRAWL_HIPS = 0.3F, SEAT = LEG_HALF, CRAWL_BEHIND = 1.0F - HIP;
	/** The hips sit this far out from the wall sat against it (model blocks): the back's own distance, plus the lean. */
	static final float SEAT_BACK = PX * (BACK + SHOULDERS * Mth.sin(LEAN));

	/** Where it sits: the wall's face (out of it, level) and how far that face is from its middle now (blocks). */
	public record Sit(float nx, float nz, float distance) {
		/** The way it faces sat there (a Minecraft yaw, degrees): out of the wall. */
		float yaw() {
			return (float) Math.toDegrees(Math.atan2(-this.nx, this.nz));
		}
	}

	/**
	 * A downed player sitting (or getting up against the wall, or back down off it): the body as it's drawn, and how far;
	 * the body's pitch (radians, the legs kept along the floor under it); the arms ({@link WallSitArms#pose}).
	 */
	record PlayerSit(Matrix4f body, float p, boolean placed, int id, boolean out, int broken, float pitch, float[] arms) {
		/** How far the limbs are the sit's rather than the crawl's (0-1). */
		float taken() {
			return this.placed ? 1.0F : PoseBlend.seg(this.p, 0.0F, LIMBS_END);
		}
	}

	public static final RenderStateDataKey<PlayerSit> KEY = RenderStateDataKey.create(() -> "jaymospowers:wall_sit");

	/** Each downed player's last moving (game time, with the frame's share of a tick). */
	private static final Int2FloatOpenHashMap LAST_MOVED = new Int2FloatOpenHashMap();
	/** Each downed player's last setting-down against a wall that's been taken ({@link DownedLie#PROPPED}), so each is taken once. */
	private static final Int2LongOpenHashMap TAKEN = new Int2LongOpenHashMap();
	/** Each downed player sitting against a wall, getting up against one or going back down off it. */
	private static final Int2ObjectOpenHashMap<Seat> SEATS = new Int2ObjectOpenHashMap<>();

	private static final class Seat {
		Sit sit;
		/** Set against the wall by another, sliding down it (not getting up against it from the floor). */
		boolean placed;
		/** Going (or gone) onto the wall; false: back down off it. */
		boolean in = true;
		/** How far it was ({@link #progress}) when this way began, and when (ticks). */
		float from, at;
		/** Getting up from the floor: the turn to face out of the wall (degrees) from the way the body faced as it began. */
		float turn, facing;
		/** Which way it rolls over onto its back (1 or -1). */
		float roll = 1.0F;
		/** What the arms do getting up (made with the turn). */
		WallSitArms.Plan plan;

		Seat(Sit sit) {
			this.sit = sit;
		}

		/** How far onto the wall (0 lying, 1 sat): along the slide down it when {@link #placed}. */
		float progress(float now) {
			float dt = Math.max(0.0F, now - this.at);
			if (!this.in) {
				return Math.max(0.0F, this.from - dt / OUT_TICKS);
			}
			return Math.min(1.0F, this.from + dt / (this.placed ? PLACE_TICKS : IN_TICKS));
		}

		/**
		 * Getting up from the floor from where it lies now, facing {@code bodyRot}: the turn round it takes, and what the
		 * arms that work do (the one that pushes off ends up on top, rolling over).
		 */
		void turnFrom(Player player, float bodyRot) {
			int id = player.getId();
			this.facing = bodyRot;
			// rolled over onto the back they'd face the other way; the rest of the way round to face out of the wall
			this.turn = Mth.wrapDegrees(bodyRot + 180.0F - this.sit.yaw());
			this.roll = Math.abs(this.turn) > 1.0F ? Math.signum(this.turn) : DownedLie.pick(id * 13) < 0.5F ? 1.0F : -1.0F;
			// (an arm broken, or gone with nothing fitted in its place, isn't used)
			int useless = Wounds.brokenLimbs(player) | Dismember.missing(player) & ~Prosthetics.fitted(player);
			int arms = ((useless & Dismember.RIGHT_ARM) == 0 ? 1 : 0) | ((useless & Dismember.LEFT_ARM) == 0 ? 2 : 0);
			this.plan = WallSitArms.plan(room(this, bodyRot, player.getScale()), this.placed, Downed.passedOut(player), arms, this.turn, this.roll);
			this.roll = this.plan.roll(this.roll);
		}

		/** The turn round, for the body facing {@code bodyRot} now (it ends facing out of the wall however the body turns). */
		float turn(float bodyRot) {
			return this.turn + Mth.wrapDegrees(bodyRot - this.facing);
		}
	}

	private WallSit() {
	}

	/** The wall {@code entity} sits against, or null. */
	static @Nullable Sit of(LivingEntity entity) {
		double[] wall = DownedLie.wall(entity);
		return wall == null ? null : new Sit((float) wall[0], (float) wall[1], (float) wall[2]);
	}

	/**
	 * After the body's been turned to face its way ({@code bodyRot}): turned to face out of the wall instead, slid back to
	 * it, sunk to the floor and leaned back on the wall, {@code t} of the way (0-1). {@code height}: its standing height
	 * in the pose stack's units.
	 */
	static void rotate(PoseStack poseStack, Sit sit, float bodyRot, float height, float t) {
		float px = height / 32.0F;
		float target = px * (BACK + SHOULDERS * Mth.sin(LEAN));
		// moved toward the wall (in the world), into this frame: its right is +x, its front is -z
		float move = (sit.distance() - target) * t;
		float wx = -sit.nx() * move, wz = -sit.nz() * move;
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -fz, rz = fx;
		poseStack.translate(wx * rx + wz * rz, 0.0F, -(wx * fx + wz * fz));
		// turned to face out of the wall
		poseStack.rotate(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyRot - sit.yaw()) * t));
		// down onto the floor, then leaned back (+x turns the top back) about the hips
		float sink = height * SINK * t;
		float hips = height * 0.375F - sink;
		poseStack.translate(0.0F, -sink, 0.0F);
		poseStack.translate(0.0F, hips, 0.0F);
		poseStack.rotate(Axis.XP.rotation(LEAN * t));
		poseStack.translate(0.0F, -hips, 0.0F);
	}

	/** Its limbs sat there: legs out flat (the lean taken off), arms fallen, head lolled, breathing hard unless {@code out} cold. */
	static void limbs(PoseBlend.Frame f, float age, int id, boolean out, int broken) {
		float side = DownedLie.pick(id * 17) < 0.5F ? 1.0F : -1.0F;
		float a = DownedLie.pick(id * 5) - 0.5F, b = DownedLie.pick(id * 7) - 0.5F;
		float breath = out ? 0.0F : Mth.sin(age * BREATH_RATE) * BREATH_DEPTH;
		float flat = -(Mth.HALF_PI - LEAN);
		f.set(PoseBlend.RIGHT_LEG, flat, LEG_SPLAY + a * LEG_SPREAD, 0.04F);
		f.set(PoseBlend.LEFT_LEG, flat, -LEG_SPLAY - b * LEG_SPREAD, -0.04F);
		if ((broken & Dismember.RIGHT_ARM) != 0 && (broken & Dismember.LEFT_ARM) == 0) {
			// a hand held over the broken arm
			f.set(PoseBlend.LEFT_ARM, -0.95F + breath, -0.4F, 0.55F);
		} else {
			f.set(PoseBlend.LEFT_ARM, ARM_FORWARD + b * 0.2F + breath, 0.0F, -ARM_OUT);
		}
		if ((broken & Dismember.LEFT_ARM) != 0 && (broken & Dismember.RIGHT_ARM) == 0) {
			f.set(PoseBlend.RIGHT_ARM, -0.95F + breath, 0.4F, -0.55F);
		} else {
			f.set(PoseBlend.RIGHT_ARM, ARM_FORWARD + a * 0.2F + breath, 0.0F, ARM_OUT);
		}
		f.set(PoseBlend.HEAD, HEAD_DROP - breath, HEAD_TURN * side, HEAD_TIP * side);
	}

	/**
	 * (Andy, 2026-11-15) A mob set against a wall by whoever carried it ({@link DownedLie#PROPPED}), {@code ticks} ago:
	 * how far it's slid down the wall into the sit (0 stood against it, 1 sat). 1 if it wasn't set there standing.
	 */
	static float setDown(@Nullable Long propped, float now) {
		if (propped == null || (propped & 1L) != 0L) {
			return 1.0F;
		}
		return PoseBlend.smooth((now - (propped >> 1)) / PLACE_TICKS);
	}

	// ---------------------------------------------------------------- downed players (CrawlPose)

	/**
	 * Brings this downed player's seat up to now: getting up against a wall once they've lain still beside it a moment
	 * (awake, or propped there by another), set against one, or going back down off it as they move. Null if they aren't
	 * sitting, getting up or going down. Shared by the drawing and the first-person view, so both see the same.
	 */
	private static @Nullable Seat seat(Player player, float partialTicks, boolean rising) {
		int id = player.getId();
		if (!Downed.isDowned(player) || player.isInWater() || player.isPassenger() || Carry.isCarried(player)) {
			LAST_MOVED.remove(id);
			TAKEN.remove(id);
			SEATS.remove(id);
			return null;
		}
		long time = player.level().getGameTime();
		float now = time + partialTicks;
		float bodyRot = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		rising |= player.hasAttached(Downed.RISING);
		Seat seat = SEATS.get(id);
		Long propped = player.getAttached(DownedLie.PROPPED);
		if (propped != null && (!TAKEN.containsKey(id) || TAKEN.get(id) != propped)) {
			// set against a wall by another (or let go beside one): sat there now (once they're seen there)
			Sit sit = of(player);
			if (sit != null) {
				TAKEN.put(id, propped.longValue());
				Seat set = new Seat(sit);
				set.placed = (propped & 1L) == 0L;
				set.from = set.placed || seat == null ? 0.0F : seat.progress(now);
				set.at = now;
				if (time - (propped >> 1) > (set.placed ? PLACE_TICKS : IN_TICKS) + SET_GRACE) {
					// it was before they came in sight: sat there already
					set.placed = false;
					set.from = 1.0F;
				}
				set.turnFrom(player, bodyRot);
				SEATS.put(id, seat = set);
				LAST_MOVED.put(id, now);
			}
		}
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		boolean moved = rising || dx * dx + dz * dz > STILL_MOVE * STILL_MOVE;
		if (moved && !rising && seat != null && propped != null
			&& now - seat.at < SET_GRACE + (seat.placed ? PLACE_TICKS : 0.0F)) {
			// just set there: being moved there isn't crawling off
			moved = false;
		}
		if (moved || !LAST_MOVED.containsKey(id)) {
			LAST_MOVED.put(id, now);
		}
		if (seat != null && seat.placed && seat.progress(now) >= 1.0F) {
			// slid all the way down: sat, as if they'd got up there themselves
			seat.placed = false;
			seat.from = 1.0F;
			seat.at = now;
			seat.turnFrom(player, bodyRot);
		}
		if (seat != null) {
			Sit live = of(player);
			boolean same = live != null && live.nx() == seat.sit.nx() && live.nz() == seat.sit.nz();
			if (same) {
				seat.sit = live;
			}
			if (seat.placed && (moved || !same)) {
				// moved off (or the wall gone) before they'd slid down it: lying, as before
				SEATS.remove(id);
				return null;
			}
			if (seat.in && (moved || !same)) {
				// crawling off (or the wall's gone): back down to the floor
				seat.from = seat.progress(now);
				seat.at = now;
				seat.in = false;
			}
			if (!seat.in && seat.progress(now) <= 0.0F) {
				SEATS.remove(id);
				seat = null;
			}
		}
		if ((seat == null || !seat.in) && !moved && now - LAST_MOVED.get(id) >= STILL_TICKS
			&& (propped != null || !Downed.passedOut(player))) {
			// lying still beside a wall a moment: getting up against it (from where they are, if on the way down)
			Sit sit = of(player);
			if (sit != null) {
				Seat up = seat != null ? seat : new Seat(sit);
				float from = seat != null ? seat.progress(now) : 0.0F;
				if (seat == null || seat.sit.nx() != sit.nx() || seat.sit.nz() != sit.nz()) {
					up.sit = sit;
					up.turnFrom(player, bodyRot);
				}
				up.sit = sit;
				up.placed = false;
				up.in = true;
				up.from = from;
				up.at = now;
				SEATS.put(id, seat = up);
			}
		}
		return seat;
	}

	/**
	 * The body as the seat has it {@code p} of the way, in the standing frame (vanilla's tilt taken off; the hips' pivot
	 * on the y axis): at 0 exactly vanilla's crawl, at 1 sat against the wall. Set against the wall, it's stood there and
	 * slides down it, its feet sliding out along the floor; from the floor it rolls over onto its back, turns round, sits
	 * up and shuffles back to the wall. {@code scale}: the player's own.
	 */
	private static Matrix4f body(Seat seat, float p, float bodyRot, float scale) {
		Sit sit = seat.sit;
		// how far its middle has to go toward the wall to have the hips there (in the model's units)
		float move = (sit.distance() - SEAT_BACK * scale) / scale;
		Matrix4f m = new Matrix4f();
		if (seat.placed) {
			float t = PoseBlend.smooth(p);
			// the legs swing out from straight down to along the floor, the hips riding down on them
			float legs = Mth.HALF_PI * t;
			slide(m, sit, bodyRot, move * t);
			m.rotateY(Mth.wrapDegrees(bodyRot - sit.yaw()) * t * Mth.DEG_TO_RAD);
			m.translate(0.0F, LEG * Mth.cos(legs) + LEG_HALF * Mth.sin(legs), 0.0F);
			m.rotateX(LEAN * t);
			m.translate(0.0F, -HIP, 0.0F);
			return m;
		}
		float roll = PoseBlend.seg(p, ROLL_FROM, ROLL_END), up = PoseBlend.seg(p, SIT_FROM, SIT_END), turn = PoseBlend.seg(p, TURN_FROM, TURN_END);
		float hips = PoseBlend.seg(p, HIPS_FROM, HIPS_END);
		slide(m, sit, bodyRot, move * shuffle(p));
		// (turning round about the hips, once they're under its middle)
		m.rotateY(seat.turn(bodyRot) * turn * Mth.DEG_TO_RAD);
		// the hips: from behind its middle a little off the floor, up onto the shoulder rolling over, down onto the floor,
		// lifted a little on each push back
		m.translate(0.0F, Mth.lerp(hips, CRAWL_HIPS, SEAT) + ROLL_LIFT * Mth.sin(Mth.PI * roll) + SCOOT_LIFT * scoot(p), CRAWL_BEHIND * (1.0F - up));
		// face down (vanilla's crawl) up to sitting, leant back on the hands (rolled over, its back is ahead: tipped that way)
		m.rotateX(pitch(p, move));
		m.rotateY(Mth.PI * roll * seat.roll);
		m.translate(0.0F, -HIP, 0.0F);
		return m;
	}

	/** How far back to the wall it's pushed itself getting up (0-1): two pushes. */
	private static float shuffle(float p) {
		return FIRST_PUSH * PoseBlend.seg(p, PUSH_1_FROM, PUSH_1_END) + (1.0F - FIRST_PUSH) * PoseBlend.seg(p, PUSH_2_FROM, PUSH_2_END);
	}

	/** The hips lifted off the floor on each push (0-1). */
	private static float scoot(float p) {
		return Mth.sin(Mth.PI * Mth.clamp((p - PUSH_1_FROM) / (PUSH_1_END - PUSH_1_FROM), 0.0F, 1.0F))
			+ Mth.sin(Mth.PI * Mth.clamp((p - PUSH_2_FROM) / (PUSH_2_END - PUSH_2_FROM), 0.0F, 1.0F));
	}

	/**
	 * The body's pitch getting up (radians, + tips the top back): face down, up to sitting leant back on its hands as far
	 * as the wall behind lets it ({@link #PROP} at most), which is {@link #LEAN} sat back against it. {@code move}: how far
	 * its middle goes to the wall (model blocks).
	 */
	private static float pitch(float p, float move) {
		float behind = (SEAT_BACK + move * (1.0F - shuffle(p))) / PX - BACK;
		float prop = Mth.clamp((float) Math.asin(Mth.clamp(behind / SHOULDERS, -1.0F, 1.0F)), LEAN, PROP);
		return Mth.lerp(PoseBlend.seg(p, SIT_FROM, SIT_END), -Mth.HALF_PI, -prop);
	}

	/** Where it gets up, for the arms: the body, and the wall in the standing frame turned to face {@code bodyRot}. */
	private static WallSitArms.Room room(Seat seat, float bodyRot, float scale) {
		Vector3f n = new Vector3f(seat.sit.nx(), 0.0F, seat.sit.nz()).rotateY((bodyRot - 180.0F) * Mth.DEG_TO_RAD);
		return new WallSitArms.Room(at -> body(seat, at, bodyRot, scale), n.x, n.z, seat.sit.distance() / scale);
	}

	/** Moves the body {@code move} toward the wall (model blocks, in the world), in the frame turned to face {@code bodyRot}. */
	private static void slide(Matrix4f m, Sit sit, float bodyRot, float move) {
		m.translate(new Vector3f(-sit.nx() * move, 0.0F, -sit.nz() * move).rotateY((bodyRot - 180.0F) * Mth.DEG_TO_RAD));
	}

	/** How far the head's lolled sat {@code p} of the way (0-1). */
	private static float lolled(boolean placed, float p) {
		return placed ? 1.0F : PoseBlend.seg(p, LOLL_FROM, 1.0F);
	}

	/** The head's turn sat {@code p} of the way (x, y, z, as on the model): the chin tucked getting up, then lolled. */
	private static float[] head(boolean placed, float p, int id) {
		float side = DownedLie.pick(id * 17) < 0.5F ? 1.0F : -1.0F;
		float lolled = lolled(placed, p);
		float tuck = placed ? 0.0F : PoseBlend.bump(p, 0.05F, 0.25F, 0.55F, 0.75F);
		return new float[] {HEAD_DROP * lolled + CHIN * tuck, HEAD_TURN * side * lolled, HEAD_TIP * side * lolled};
	}

	/** Read onto a downed player's state: sitting against a wall, or getting up against one, or back down off it. */
	public static void extract(Player player, AvatarRenderState state, float partialTicks, boolean rising) {
		Seat seat = seat(player, partialTicks, rising);
		if (seat == null) {
			state.setData(KEY, null);
			return;
		}
		float p = seat.progress(player.level().getGameTime() + partialTicks);
		boolean out = Downed.passedOut(player);
		int broken = Wounds.brokenLimbs(player);
		// the arms resting sat there at the end, the elbows eased out a little (not one that's broken, or holding it)
		PoseBlend.Frame sat = new PoseBlend.Frame(new ModelPart[0]);
		limbs(sat, player.tickCount + partialTicks, player.getId(), out, broken);
		boolean cradle = Integer.bitCount(broken & (Dismember.RIGHT_ARM | Dismember.LEFT_ARM)) == 1;
		float[] rest = {sat.get(PoseBlend.RIGHT_ARM), sat.get(PoseBlend.RIGHT_ARM + 1), sat.get(PoseBlend.RIGHT_ARM + 2),
			out || cradle || (broken & Dismember.RIGHT_ARM) != 0 ? 0.0F : WallSitArms.FOLD, sat.get(PoseBlend.LEFT_ARM),
			sat.get(PoseBlend.LEFT_ARM + 1), sat.get(PoseBlend.LEFT_ARM + 2),
			out || cradle || (broken & Dismember.LEFT_ARM) != 0 ? 0.0F : -WallSitArms.FOLD};
		float move = (seat.sit.distance() - SEAT_BACK * state.scale) / state.scale;
		state.setData(KEY, new PlayerSit(body(seat, p, state.bodyRot, state.scale), p, seat.placed, player.getId(), out, broken,
			seat.placed ? 0.0F : pitch(p, move), WallSitArms.pose(room(seat, state.bodyRot, state.scale), seat.plan, p, rest)));
	}

	/**
	 * In place of the crawl's turn (after vanilla's swimming tilt): the tilt taken off and the body laid as the sit has it
	 * (which starts as vanilla's crawl). False if they aren't sitting.
	 */
	public static boolean rotatePlayer(AvatarRenderState state, PoseStack poseStack) {
		PlayerSit sitting = state.getData(KEY);
		if (sitting == null) {
			return false;
		}
		if (state.isVisuallySwimming) {
			poseStack.translate(0.0F, 1.0F, -0.3F);
		}
		poseStack.rotateDegrees(Axis.XP, 90.0F * state.swimAmount);
		poseStack.mulPose(sitting.body());
		return true;
	}

	/**
	 * After the crawl's limbs are on {@code f}: over to the sit's as they get up against the wall (outright, set against
	 * it). How far the limbs are the sit's (0-1); 0 if they aren't sitting.
	 */
	public static float posePlayer(AvatarRenderState state, PoseBlend.Frame f) {
		PlayerSit sitting = state.getData(KEY);
		if (sitting == null) {
			return 0.0F;
		}
		float p = sitting.p();
		boolean placed = sitting.placed();
		// the sit's own limbs, read off a frame over nothing (so each slot is the angle itself)
		PoseBlend.Frame sat = new PoseBlend.Frame(new ModelPart[0]);
		limbs(sat, state.ageInTicks, sitting.id(), sitting.out(), sitting.broken());
		// the legs stay along the floor as the body sits up off it and leans back (set against the wall, they swing out from
		// under it as it slides down)
		float up = placed ? PoseBlend.smooth(p) : PoseBlend.seg(p, SIT_FROM, SIT_END);
		float legs = placed ? sat.get(PoseBlend.RIGHT_LEG) * up : -(Mth.HALF_PI + sitting.pitch());
		float rest = placed ? 1.0F : PoseBlend.seg(p, LOLL_FROM, 1.0F);
		float k = sitting.taken();
		float[] head = head(placed, p, sitting.id());
		float breath = sitting.out() ? 0.0F : Mth.sin(state.ageInTicks * BREATH_RATE) * BREATH_DEPTH * rest;
		f.mix(PoseBlend.HEAD, head[0] - breath, head[1], head[2], k);
		float[] arms = sitting.arms();
		for (int i = 0; i < 2; i++) {
			// pushing off, propping, stepping round and pushing back on the floor, then fallen at the sides (WallSitArms),
			// each turned the short way from where the crawl has it
			int arm = i == 0 ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM;
			f.mix(arm, near(arms[i * 4], f.get(arm)), near(arms[i * 4 + 1], f.get(arm + 1)), near(arms[i * 4 + 2], f.get(arm + 2)), k);
		}
		for (int leg : new int[] {PoseBlend.RIGHT_LEG, PoseBlend.LEFT_LEG}) {
			f.mix(leg, legs, sat.get(leg + 1) * up, sat.get(leg + 2), k);
		}
		f.mix(PoseBlend.HIP_PITCH, 0.0F, 0.0F, 0.0F, k);
		f.set(PoseBlend.DROP, Mth.lerp(k, f.get(PoseBlend.DROP), 0.0F));
		f.set(PoseBlend.FORWARD, Mth.lerp(k, f.get(PoseBlend.FORWARD), 0.0F));
		return k;
	}

	/** How far forward this player's head hangs off the neck sat against a wall (model pixels; 0 if they aren't sitting). */
	public static float headForward(AvatarRenderState state) {
		PlayerSit sitting = state.getData(KEY);
		return sitting == null ? 0.0F : HEAD_FORWARD * lolled(sitting.placed(), sitting.p());
	}

	/** {@code a} turned the short way round from {@code from} (radians). */
	private static float near(float a, float from) {
		return from + WallSitArms.wrap(a - from);
	}

	/** How far this player's limbs are the sit's rather than the crawl's (0-1; 0 if they aren't sitting or getting up). */
	public static float taken(AvatarRenderState state) {
		PlayerSit sitting = state.getData(KEY);
		return sitting == null ? 0.0F : sitting.taken();
	}

	/**
	 * How far this player's elbow is folded across for the sit (radians, as {@code KneeBend} folds it), weighed by how
	 * far the limbs are the sit's; 0 if they aren't sitting or getting up.
	 */
	public static float elbow(AvatarRenderState state, boolean right) {
		PlayerSit sitting = state.getData(KEY);
		return sitting == null ? 0.0F : sitting.arms()[right ? 3 : 7] * sitting.taken();
	}

	/**
	 * First person: where the view goes for a downed player sat against a wall (or getting up against it, set against it,
	 * or going back down off it): the model's own eyes, the body drawn as {@link #rotatePlayer} draws it, the head as it
	 * lolls; moved over onto them from where vanilla has it ({@code view}) as they start getting up. Null if they aren't.
	 */
	public static @Nullable Vec3 eye(Player player, float partialTicks, Vec3 view) {
		Seat seat = seat(player, partialTicks, false);
		if (seat == null) {
			return null;
		}
		float p = seat.progress(player.level().getGameTime() + partialTicks);
		float bodyRot = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		float scale = player.getScale();
		float[] head = head(seat.placed, p, player.getId());
		// the head's middle at vanilla's eye height, turned with the head about the neck (the model is drawn flipped on x
		// and y, so those two turn the other way here), then with the body; then into the world with its turn and size
		Vector3f eye = new Vector3f(0.0F, EYE - NECK, 0.0F).rotateX(-head[0]).rotateY(-head[1]).rotateZ(head[2])
			.add(0.0F, NECK, -HEAD_FORWARD * PX * lolled(seat.placed, p));
		body(seat, p, bodyRot, scale).transformPosition(eye);
		eye.rotateY((180.0F - bodyRot) * Mth.DEG_TO_RAD).mul(scale);
		Vec3 at = new Vec3(Mth.lerp(partialTicks, player.xo, player.getX()) + eye.x, Mth.lerp(partialTicks, player.yo, player.getY()) + eye.y,
			Mth.lerp(partialTicks, player.zo, player.getZ()) + eye.z);
		return view.lerp(at, seat.placed ? 1.0F : PoseBlend.seg(p, 0.0F, VIEW_END));
	}
}
