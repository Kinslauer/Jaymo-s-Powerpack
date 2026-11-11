package com.jaymospowers.client.costume;

import com.jaymospowers.power.web.WebPower;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A cape that hangs as cloth: a short chain of hinged strips (the model is built with {@link #chain}), each a damped
 * spring that follows the one above it a little late. What a player sees: the cape lags the body, swings out behind a
 * turn, floats up behind a fall and fans wider on a glide on the web wings, then settles back to hanging.
 * <p>The sim is client-side and looks only: one tiny state per wearer (angles and speeds of each strip), stepped once
 * per game tick by whoever draws the cape first that tick ({@link #pose}), and drawn interpolated between the last two
 * ticks. A wearer not drawn for a while is dropped, so a crowd costs a few floats each. The first strip keeps the
 * vanilla cape's own motion (the caller poses it); the others are hinged under it and bend relative to the one above.
 */
public final class ClothCape {
	// ===== the knobs ================================================================================================
	/** How many strips a cape of {@code length} pixels is cut into: one per this many pixels, at least 2, at most {@link #MAX_STRIPS}. */
	static final float PIXELS_PER_STRIP = 4.0F;
	public static final int MAX_STRIPS = 6;
	/** Each strip's spring toward its target a tick (0-1) and the share of its speed it loses a tick. */
	static final float STIFFNESS = 0.14F, DAMPING = 0.2F;
	/** How much of the strip above's angle a strip aims for (the rest is its own drive): the higher, the more it trails. */
	static final float FOLLOW = 0.62F;
	/** Turning (degrees a tick of the body's yaw) swings the cape out this many radians per degree, per strip further down. */
	static final float TURN_SWING = 0.045F;
	/** Falling (blocks a tick) lifts the cape behind you this many radians per block/tick, most at the hem; at most this much. */
	static final float BILLOW = 1.1F, BILLOW_MAX = 0.9F;
	/** On the web wings the cape fans out: each strip this much wider than the one above (a share), and lifted this much. */
	static final float SPREAD_WIDEN = 0.11F, SPREAD_LIFT = 0.5F;
	/** The most one strip bends relative to the one above (radians). */
	static final float MAX_BEND = 1.1F;
	/** A wearer not drawn for this many ticks is dropped. */
	static final int KEEP_TICKS = 100;
	// ================================================================================================================

	private static final class Sim {
		final float[] pitch = new float[MAX_STRIPS], pitchV = new float[MAX_STRIPS];
		final float[] roll = new float[MAX_STRIPS], rollV = new float[MAX_STRIPS];
		final float[] pitchBefore = new float[MAX_STRIPS], rollBefore = new float[MAX_STRIPS];
		float spread, spreadBefore;
		long tick = Long.MIN_VALUE;
		long seen;
		float yaw;
		boolean hasYaw;
	}

	private static final Map<Integer, Sim> SIMS = new HashMap<>();

	private ClothCape() {
	}

	/**
	 * Builds {@code length} pixels of cape as a chain of strips under {@code parent}: the first named {@code name} with
	 * {@code pose}, each next one named {@code "next"} below the one above. The texture is laid down the strips as it
	 * would be on one tall box starting at ({@code u}, {@code v}).
	 */
	public static void chain(PartDefinition parent, String name, PartPose pose, float x, float width, int length, float z, float depth, int u, int v) {
		int strips = strips(length);
		PartDefinition above = null;
		int top = 0;
		for (int i = 0; i < strips; i++) {
			int bottom = Math.round((i + 1) * (float)length / strips);
			float h = bottom - top;
			CubeListBuilder box = CubeListBuilder.create().texOffs(u, v + top).addBox(x, 0.0F, z, width, h, depth);
			above = above == null ? parent.addOrReplaceChild(name, box, pose)
				: above.addOrReplaceChild("next", box, PartPose.offset(0.0F, top - prevTop(length, strips, i), 0.0F));
			top = bottom;
		}
	}

	/** Where strip {@code i - 1} began (pixels down the cape). */
	private static int prevTop(int length, int strips, int i) {
		return i <= 1 ? 0 : Math.round((i - 1) * (float)length / strips);
	}

	static int strips(int length) {
		return Mth.clamp(Math.round(length / PIXELS_PER_STRIP), 2, MAX_STRIPS);
	}

	/**
	 * Poses the strips under {@code first} (the cape's first strip, which the caller has already posed like the vanilla
	 * cape) for the wearer in {@code state}. {@code side}: +1 if a positive Z turn of the cape swings its hem to the
	 * wearer's right (the plain cape on the body), -1 if to their left (a cape turned round to face the other way).
	 * {@code widen}: 0-1 how far the caller has it open (a furled mantle spreads nothing).
	 */
	public static void pose(ModelPart first, AvatarRenderState state, int side) {
		int strips = 1;
		for (ModelPart p = first; p.hasChild("next"); p = p.getChild("next")) {
			strips++;
		}
		if (strips < 2) {
			return;
		}
		float spread = sample(state, strips, side);
		if (spread < 0.0F) {
			return;
		}
		ModelPart part = first;
		for (int i = 1; i < strips; i++) {
			part = part.getChild("next");
			part.xRot = REL_PITCH[i];
			part.zRot = REL_ROLL[i];
			part.xScale = 1.0F + SPREAD_WIDEN * spread;
		}
	}

	/** Each strip's bend (radians) from the one above, as last worked out by {@link #sample}: [0] is unused. */
	public static final float[] REL_PITCH = new float[MAX_STRIPS], REL_ROLL = new float[MAX_STRIPS];

	/**
	 * Steps the wearer's cloth to this tick (once a tick, whoever asks first) and fills {@link #REL_PITCH} and {@link
	 * #REL_ROLL} for {@code strips} strips, drawn between the last two ticks. Returns how far the cape is spread (0-1,
	 * the glide), or -1 if there is no world. For models that hinge their own cloth in fewer pieces.
	 */
	public static float sample(AvatarRenderState state, int strips, int side) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1.0F;
		}
		strips = Mth.clamp(strips, 2, MAX_STRIPS);
		Entity entity = minecraft.level.getEntity(state.id);
		long now = minecraft.level.getGameTime();
		Sim sim = SIMS.get(state.id);
		if (sim == null) {
			if (SIMS.size() > 256) {
				SIMS.values().removeIf(s -> now - s.seen > KEEP_TICKS);
			}
			sim = new Sim();
			SIMS.put(state.id, sim);
		}
		sim.seen = now;
		if (sim.tick == Long.MIN_VALUE) {
			sim.tick = now;
		}
		// the tick's drives: the vanilla cape's own swing, plus the turn, the fall and the glide
		float drivePitch = (state.capeLean / 2.0F + state.capeFlap) * Mth.DEG_TO_RAD;
		float driveRoll = state.capeLean2 / 2.0F * Mth.DEG_TO_RAD * 0.5F * side;
		float fall = 0.0F, turn = 0.0F, glide = 0.0F;
		if (entity != null) {
			fall = (float)(entity.yo - entity.getY());
			glide = entity.getAttached(WebPower.WINGS) != null || entity instanceof LivingEntity flier && flier.isFallFlying() ? 1.0F : 0.0F;
			if (entity instanceof LivingEntity living) {
				float yaw = living.yBodyRot;
				turn = sim.hasYaw ? Mth.wrapDegrees(yaw - sim.yaw) : 0.0F;
				if (now != sim.tick || !sim.hasYaw) {
					sim.yaw = yaw;
					sim.hasYaw = true;
				}
			}
		}
		int steps = (int)Math.min(3L, now - sim.tick);
		for (int s = 0; s < steps; s++) {
			System.arraycopy(sim.pitch, 0, sim.pitchBefore, 0, MAX_STRIPS);
			System.arraycopy(sim.roll, 0, sim.rollBefore, 0, MAX_STRIPS);
			sim.spreadBefore = sim.spread;
			step(sim, strips, drivePitch, driveRoll, fall, turn / Math.max(1, steps), glide, side);
		}
		if (now - sim.tick > 3L) {
			sim.tick = now;
		} else {
			sim.tick += steps;
		}
		float partial = Mth.clamp(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
		float abovePitch = Mth.lerp(partial, sim.pitchBefore[0], sim.pitch[0]);
		float aboveRoll = Mth.lerp(partial, sim.rollBefore[0], sim.roll[0]);
		for (int i = 1; i < strips; i++) {
			float p = Mth.lerp(partial, sim.pitchBefore[i], sim.pitch[i]);
			float r = Mth.lerp(partial, sim.rollBefore[i], sim.roll[i]);
			REL_PITCH[i] = Mth.clamp(p - abovePitch, -MAX_BEND, MAX_BEND);
			REL_ROLL[i] = Mth.clamp(r - aboveRoll, -MAX_BEND, MAX_BEND);
			abovePitch = p;
			aboveRoll = r;
		}
		return Mth.lerp(partial, sim.spreadBefore, sim.spread);
	}

	/** One game tick of the chain. */
	private static void step(Sim sim, int strips, float drivePitch, float driveRoll, float fall, float turn, float glide, int side) {
		sim.spread += (glide - sim.spread) * 0.12F;
		float billow = Mth.clamp(fall * BILLOW, 0.0F, BILLOW_MAX);
		for (int i = 0; i < strips; i++) {
			float depth = (i + 1) / (float)strips;
			float aimPitch;
			float aimRoll;
			if (i == 0) {
				// the first strip is the vanilla cape's own: it is the drive itself
				aimPitch = drivePitch;
				aimRoll = driveRoll;
				sim.pitch[0] = aimPitch;
				sim.roll[0] = aimRoll;
				continue;
			}
			float ownPitch = drivePitch + billow * depth + SPREAD_LIFT * sim.spread * depth * 0.5F;
			float ownRoll = driveRoll - turn * TURN_SWING * depth * side;
			aimPitch = Mth.lerp(FOLLOW, ownPitch, sim.pitch[i - 1]);
			aimRoll = Mth.lerp(FOLLOW, ownRoll, sim.roll[i - 1]);
			sim.pitchV[i] += (aimPitch - sim.pitch[i]) * STIFFNESS - sim.pitchV[i] * DAMPING;
			sim.rollV[i] += (aimRoll - sim.roll[i]) * STIFFNESS - sim.rollV[i] * DAMPING;
			sim.pitch[i] += sim.pitchV[i];
			sim.roll[i] += sim.rollV[i];
			// (hanging, it can't swing forward through the body)
			if (sim.pitch[i] < -0.25F) {
				sim.pitch[i] = -0.25F;
				sim.pitchV[i] = 0.0F;
			}
		}
	}
}
