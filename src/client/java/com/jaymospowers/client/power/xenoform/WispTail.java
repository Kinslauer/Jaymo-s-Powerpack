package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformState;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * Wisp's ghost tail (Andy, on the ghost he wanted Wisp to move like: "the way the tail animates"). Below the waist Wisp
 * is one tapering tail, a chain of {@link #SEGMENTS} links ({@link XenoFormModels.Wisp}), and the chain follows the body
 * late. Hanging in the air it curls back under itself like a comma and wavers; moving, it streams out behind, opposite
 * to where the body is going (back at a run, down on the way up, up on the way down, out to the side of a strafe); a turn
 * swings it wide. Each link holds on to where it was pointing and gives that up a little after the link above it, so a
 * stop or a turn runs down the tail as a wave and the tip arrives last.
 * <p>
 * Kept per player on the client and stepped once a tick ({@link #tick}); a frame reads it between two ticks
 * ({@link #extract}) and the model takes it from the render state ({@link #pose}), so the ki shells bend with the skin.
 */
public final class WispTail {
	// ---- tuning ----
	/** Each link's box, top to tip: width, length, depth (model pixels). The model builds them; each starts this far up inside the last. */
	static final float[][] SEGMENTS = {{8, 4, 4}, {7, 4, 4}, {6, 4, 3}, {5, 4, 3}, {4, 4, 2}, {3, 4, 2}, {2, 4, 1}, {1, 5, 1}};
	static final float OVERLAP = 0.75F;
	/** At rest: how far back each link bends from the one above (radians), so the whole curls into a comma. */
	static final float CURL = 0.3F;
	/** How hard movement pulls the tail (per block a tick), against this much pull straight down. */
	static final float STREAM = 6.0F, HANG = 0.5F;
	/** The streaming tail's pitch stays within these (radians: 0 straight down, a quarter turn straight back). */
	static final float PITCH_MIN = -0.7F, PITCH_MAX = 2.7F, ROLL_MAX = 1.0F;
	/** A turn swings a streaming tail out this far (radians per degree turned in a tick). */
	static final float TURN_SWING = 0.03F;
	/** How fast the top link gives up where it pointed (share a tick), and how fast each link below follows the one above. */
	static final float ROOT_FOLLOW = 0.35F, LINK_FOLLOW = 0.45F;
	/** The waver: side to side at the tip while hanging and while streaming (radians), its rate, and how far it is out of step link to link. */
	static final float WAVE_HANG = 0.2F, WAVE_STREAM = 0.07F, WAVE_RATE = 0.16F, WAVE_STEP = 0.8F;

	/** For a frame: each link's bend back from the one above, then each link's lean to the side, then how much it is streaming (0-1). */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:wisp_tail");

	private static final int N = SEGMENTS.length;
	private static final Map<Integer, Chain> CHAINS = new HashMap<>();

	/** Where every link points (summed from the top), this tick and last; how far each is off where it should be. */
	private static final class Chain {
		final float[] pitch = new float[N], pitchO = new float[N], roll = new float[N], rollO = new float[N];
		final float[] pitchOff = new float[N], rollOff = new float[N];
		float stream, streamO;
		boolean fresh = true;
	}

	private WispTail() {
	}

	/** Every client tick: each Wisp in view has its tail stepped on. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			CHAINS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (!"wisp".equals(player.getAttached(XenoformState.FORM))) {
				continue;
			}
			step(CHAINS.computeIfAbsent(player.getId(), id -> new Chain()), player);
		}
		CHAINS.keySet().removeIf(id -> !(level.getEntity(id) instanceof AbstractClientPlayer player) || !"wisp".equals(player.getAttached(XenoformState.FORM)));
	}

	private static void step(Chain chain, AbstractClientPlayer player) {
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		if (dx * dx + dy * dy + dz * dz > 16.0) {
			// teleported: nothing to trail from
			dx = dy = dz = 0.0;
		}
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		// facing is (-sin, cos); its right hand is (-cos, -sin)
		float forward = (float)(-sin * dx + cos * dz), right = (float)(-cos * dx - sin * dz), up = (float)dy;
		float turn = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);
		float back = forward * STREAM, down = HANG + up * STREAM;
		float stream = Mth.clamp((float)Math.sqrt(forward * forward + right * right + up * up) * STREAM / 1.2F, 0.0F, 1.0F);
		float pitchTo = Mth.clamp((float)Mth.atan2(back, down), PITCH_MIN, PITCH_MAX);
		// (a positive lean is toward the body's right: a strafe trails it the other way, a turn to the right leaves it out on the right)
		float rollTo = Mth.clamp(-(float)Mth.atan2(right * STREAM, Math.sqrt(back * back + down * down)) + turn * TURN_SWING * stream, -ROLL_MAX, ROLL_MAX);
		chain.streamO = chain.stream;
		chain.stream += (stream - chain.stream) * 0.3F;
		float lastPitchOff = 0.0F, lastRollOff = 0.0F;
		for (int i = 0; i < N; i++) {
			chain.pitchO[i] = chain.pitch[i];
			chain.rollO[i] = chain.roll[i];
			// where this link should point: curled at rest; streaming, the top bends over and the rest lie along it
			float reach = Math.min(1.0F, (i + 1) / 2.0F);
			float pitchWant = Mth.lerp(chain.stream, CURL * (i + 1), pitchTo * reach);
			float rollWant = rollTo * Math.min(1.0F, (i + 1) / 3.0F);
			if (chain.fresh) {
				chain.pitch[i] = chain.pitchO[i] = pitchWant;
				chain.roll[i] = chain.rollO[i] = rollWant;
				continue;
			}
			// it is off by however far it still points the old way; the top lets go first, each link after the one above
			float pitchOff = chain.pitch[i] - pitchWant, rollOff = chain.roll[i] - rollWant;
			float pitchNow = i == 0 ? pitchOff * (1.0F - ROOT_FOLLOW) : pitchOff + (lastPitchOff - pitchOff) * LINK_FOLLOW;
			float rollNow = i == 0 ? rollOff * (1.0F - ROOT_FOLLOW) : rollOff + (lastRollOff - rollOff) * LINK_FOLLOW;
			lastPitchOff = chain.pitchOff[i];
			lastRollOff = chain.rollOff[i];
			chain.pitchOff[i] = pitchNow;
			chain.rollOff[i] = rollNow;
			chain.pitch[i] = pitchWant + pitchNow;
			chain.roll[i] = rollWant + rollNow;
		}
		chain.fresh = false;
	}

	/** From the avatar renderer's extractRenderState: the tail between last tick and this one, as each link's own bend. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Chain chain = CHAINS.get(entity.getId());
		if (chain == null) {
			state.setData(KEY, null);
			return;
		}
		float[] bend = new float[N * 2 + 1];
		float pitchAbove = 0.0F, rollAbove = 0.0F;
		for (int i = 0; i < N; i++) {
			float pitch = Mth.lerp(partialTicks, chain.pitchO[i], chain.pitch[i]), roll = Mth.lerp(partialTicks, chain.rollO[i], chain.roll[i]);
			bend[i] = pitch - pitchAbove;
			bend[N + i] = roll - rollAbove;
			pitchAbove = pitch;
			rollAbove = roll;
		}
		bend[N * 2] = Mth.lerp(partialTicks, chain.streamO, chain.stream);
		state.setData(KEY, bend);
	}

	/** Poses a model's tail links from {@code bend} ({@link #KEY}; null: nobody is stepping it, so it hangs curled), with the waver on top. */
	static void pose(ModelPart[] tail, float @Nullable [] bend, float age) {
		float stream = bend == null ? 0.0F : bend[N * 2];
		float wave = Mth.lerp(stream, WAVE_HANG, WAVE_STREAM);
		for (int i = 0; i < tail.length; i++) {
			float along = tail.length == 1 ? 1.0F : i / (float)(tail.length - 1);
			float sway = wave * (0.3F + 0.7F * along) / Math.max(1.0F, tail.length * 0.25F);
			tail[i].xRot = (bend == null ? CURL : bend[i]) + Mth.cos(age * WAVE_RATE * 0.7F - i * WAVE_STEP) * sway * 0.5F;
			tail[i].zRot = (bend == null ? 0.0F : bend[N + i]) + Mth.sin(age * WAVE_RATE - i * WAVE_STEP) * sway;
		}
	}
}
