package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Brought to their knees by Gravity's weight (Crush's pin, a Gravity Well's pull; see {@link GravityPower#KNEEL}),
 * drawn for everyone, on players and humanoid mobs. First they fight it ({@link #RESIST} ticks): knees buckling a
 * little, trembling, arms shoved up against the pressure. Then they drop ({@link #DROP} ticks) into one of four poses
 * and stay down, heaving: hunched on both knees with both hands planted on the ground; down on one knee, a forearm on
 * the other; on both knees shielding their head with both arms; slumped on both knees, head hanging. When the weight
 * lifts they get back up ({@link #GET_UP} ticks).
 */
public final class GravityKneelPose {
	/** The struggle before they go down, the drop, and getting up again (ticks). */
	static final float RESIST = 18.0F, DROP = 5.0F, GET_UP = 14.0F;
	/** How far the knees give while they still fight it (0-1 of the way down). */
	static final float RESIST_SINK = 0.3F;
	/** The hips over the feet when standing (blocks). */
	static final float HIP = 0.75F;

	/**
	 * A pose, fully down: how far the body sinks (blocks), how far it bends forward from the hips (degrees), each leg's
	 * swing back (radians, as the world sees it: + is back, so 1.45 is a shin flat on the ground behind), each arm's
	 * swing and spread (relative to the bent body), and the head's nod (+ down).
	 */
	private record Shape(float sink, float pitch, float rightLeg, float leftLeg, float rightArmX, float rightArmZ, float leftArmX, float leftArmZ,
		float head) {
	}

	private static final Shape[] SHAPES = {
		// hunched on both knees, both hands planted on the ground ahead, straining to look up
		new Shape(0.55F, 50.0F, 1.45F, 1.45F, -1.15F, 0.15F, -1.15F, -0.15F, -0.6F),
		// down on one knee (the left), right foot planted, a forearm across the right knee, the other fist down
		new Shape(0.42F, 25.0F, -1.15F, 1.35F, -0.95F, -0.1F, -0.5F, -0.12F, 0.45F),
		// on both knees, both arms over the head against the weight
		new Shape(0.55F, 20.0F, 1.45F, 1.45F, -2.7F, 0.35F, -2.7F, -0.35F, 0.6F),
		// slumped on both knees, arms hanging, head down
		new Shape(0.55F, 30.0F, 1.45F, 1.45F, -0.5F, 0.25F, -0.5F, -0.25F, 0.75F),
	};

	/** {@code down}: 0 standing to 1 fully down; {@code resist}: how much they're still fighting it (0-1). */
	record Kneel(int style, float down, float resist, float time) {
	}

	static final RenderStateDataKey<Kneel> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_kneel");

	/** Each kneeler's style, how far down they were last drawn, and when the weight lifted (game time, or -1). */
	private static final class Track {
		int style;
		float down;
		float releasedAt = -1.0F;
		float releasedDown;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private GravityKneelPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		GravityPower.Kneel kneel = entity.getAttached(GravityPower.KNEEL);
		Track track = TRACKS.get(entity.getId());
		if (kneel == null && track == null || !humanoid(entity)) {
			state.setData(KEY, null);
			return;
		}
		float now = entity.level().getGameTime() + partialTicks;
		if (kneel != null) {
			if (track == null) {
				track = new Track();
				TRACKS.put(entity.getId(), track);
			}
			track.style = Math.floorMod(kneel.style(), SHAPES.length);
			track.releasedAt = -1.0F;
			float t = Math.max(0.0F, now - kneel.since());
			track.down = down(t);
			state.setData(KEY, new Kneel(track.style, track.down, resist(t), now));
			return;
		}
		// the weight's off: back up, from however far down they were
		if (track.releasedAt < 0.0F) {
			track.releasedAt = now;
			track.releasedDown = track.down;
		}
		float up = Mth.clamp((now - track.releasedAt) / GET_UP, 0.0F, 1.0F);
		if (up >= 1.0F || entity.isRemoved()) {
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		// a heave up, slow at first
		track.down = track.releasedDown * (1.0F - up * up * (3.0F - 2.0F * up));
		state.setData(KEY, new Kneel(track.style, track.down, 0.0F, now));
	}

	/** How far down, {@code t} ticks into the weight: buckling and trembling, then the drop, then heaving. */
	private static float down(float t) {
		if (t < RESIST) {
			float p = t / RESIST;
			return RESIST_SINK * p * p * (3.0F - 2.0F * p) + 0.03F * Mth.sin(t * 2.7F) * p;
		}
		if (t < RESIST + DROP) {
			float p = (t - RESIST) / DROP;
			// they give way: faster and faster, onto their knees
			return Mth.lerp(p * p, RESIST_SINK, 1.04F);
		}
		float after = t - RESIST - DROP;
		// the knees hit, a settle, then heavy breathing
		return 1.0F + 0.04F * Math.max(0.0F, 1.0F - after / 4.0F) + 0.015F * Mth.sin(after * 0.3F);
	}

	/** How hard they still push back against it: all through the struggle, gone once they're down. */
	private static float resist(float t) {
		if (t < RESIST) {
			return Mth.clamp(t / 6.0F, 0.0F, 1.0F);
		}
		return Mth.clamp(1.0F - (t - RESIST) / DROP, 0.0F, 1.0F);
	}

	/** Drawn with a humanoid body (players, zombies, skeletons, piglins...): the only ones the poses fit. */
	private static boolean humanoid(LivingEntity entity) {
		return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?, ?> renderer
			&& renderer.getModel() instanceof HumanoidModel<?>;
	}

	/** After the body faces its way: sunk down onto the knees, and bent forward from the hips. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Kneel kneel = state.getData(KEY);
		if (kneel == null || state.deathTime > 0.0F) {
			return;
		}
		Shape shape = SHAPES[kneel.style()];
		float down = Math.min(1.0F, kneel.down());
		poseStack.translate(0.0F, -shape.sink() * kneel.down(), 0.0F);
		poseStack.translate(0.0F, HIP, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -shape.pitch() * down);
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** After the model animates: legs folded under, arms and head into the pose (pushing up while they still fight it). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Kneel kneel = living.getData(KEY);
		if (kneel == null) {
			return;
		}
		Shape shape = SHAPES[kneel.style()];
		float k = Mth.clamp(kneel.down(), 0.0F, 1.0F);
		float pitch = shape.pitch() * Mth.DEG_TO_RAD;
		// the legs, as the world sees them, so they lie back along the ground under the forward bend
		blend(h.rightLeg, shape.rightLeg() - pitch, 0.08F, 0.0F, k);
		blend(h.leftLeg, shape.leftLeg() - pitch, -0.08F, 0.0F, k);
		blend(h.body, 0.0F, 0.0F, 0.0F, k);
		blend(h.rightArm, shape.rightArmX(), 0.0F, shape.rightArmZ(), k);
		blend(h.leftArm, shape.leftArmX(), 0.0F, shape.leftArmZ(), k);
		h.head.xRot = Mth.lerp(k, h.head.xRot, shape.head());
		float r = kneel.resist();
		if (r > 0.0F) {
			// shoving back up against the weight, arms overhead, shaking
			float shake = Mth.sin(kneel.time() * 2.3F) * 0.08F * r;
			blend(h.rightArm, -2.9F + shake, 0.0F, 0.15F, r * 0.8F);
			blend(h.leftArm, -2.9F - shake, 0.0F, -0.15F, r * 0.8F);
			h.head.xRot = Mth.lerp(r * 0.6F, h.head.xRot, -0.35F);
			h.head.zRot += Mth.sin(kneel.time() * 1.7F) * 0.06F * r;
		} else if (k >= 1.0F) {
			// down: heaving with each breath
			float breath = Mth.sin(kneel.time() * 0.3F) * 0.04F;
			h.body.xRot += breath;
			h.head.xRot += breath;
		}
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
