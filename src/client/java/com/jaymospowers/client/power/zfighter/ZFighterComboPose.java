package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterCombo;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

/**
 * The hold combo's punches and kicks ({@link ZFighterCombo}), on everyone who throws them. Each move winds up, snaps
 * out (the blow lands halfway), holds a beat and comes back to a fighting stance; spinning kicks turn the whole body
 * round once.
 */
public final class ZFighterComboPose {
	/** The pose this frame and how much of it shows (eased in and out). */
	public record Pose(float[] frame, float amount) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_combo");
	static final float EASE_IN = 0.34F;
	static final float EASE_OUT = 0.2F;

	// frame slots: right arm x y z, left arm x y z, right leg x z, left leg x z, body turn (y), body lean (x), body tilt (z),
	// spin (turns)
	private static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLZ = 7, LLX = 8, LLZ = 9, BY = 10, BX = 11, BZ = 12,
		SPIN = 13, SLOTS = 14;

	/** Fists up, left foot forward. */
	private static final float[] STANCE = {-1.2F, -0.45F, 0.0F, -1.35F, 0.45F, 0.0F, 0.2F, 0.05F, -0.25F, -0.05F, 0.15F, 0.1F, 0.0F, 0.0F};

	/** Each move's wind-up and strike, as changes from the stance (slot, value pairs). */
	private static final float[][][] MOVES = new float[ZFighterCombo.MOVES][][];

	/** Shares of the move: wound up, struck, held. Then back to the stance. */
	private static final float WOUND = 0.3F, STRUCK = 0.5F, HELD = 0.72F;

	static {
		move(ZFighterCombo.JAB,
			new float[] {LAX, -1.15F, LAY, 0.55F, BY, 0.25F},
			new float[] {LAX, -1.62F, LAY, 0.08F, BY, 0.45F, LLX, -0.35F});
		move(ZFighterCombo.CROSS,
			new float[] {RAX, -1.0F, RAY, -0.3F, BY, 0.4F},
			new float[] {RAX, -1.62F, RAY, -0.05F, BY, -0.5F, RLX, 0.4F});
		move(ZFighterCombo.HOOK,
			new float[] {LAX, -1.5F, LAY, -0.7F, LAZ, -0.3F, BY, -0.25F},
			new float[] {LAX, -1.55F, LAY, 0.75F, LAZ, 0.0F, BY, 0.6F, BZ, 0.1F});
		move(ZFighterCombo.UPPERCUT,
			new float[] {RAX, -0.25F, RAY, -0.15F, BY, 0.3F, BX, 0.35F, RLX, 0.35F, LLX, -0.4F},
			new float[] {RAX, -2.85F, RAY, -0.2F, BY, -0.45F, BX, -0.2F, LLX, -0.1F});
		move(ZFighterCombo.KNEE,
			new float[] {RLX, 0.45F, BX, 0.05F},
			new float[] {RLX, -1.45F, RAX, -0.8F, LAX, -0.85F, BX, 0.3F});
		move(ZFighterCombo.FRONT_KICK,
			new float[] {RLX, -0.7F, BX, 0.1F},
			new float[] {RLX, -1.65F, BX, -0.25F, LLX, 0.1F, RAX, -0.6F, RAZ, 0.3F});
		move(ZFighterCombo.ROUNDHOUSE,
			new float[] {LLX, -0.4F, LLZ, -0.5F, BY, 0.3F},
			new float[] {LLX, -0.7F, LLZ, -1.4F, BY, -0.65F, BZ, 0.35F, LAX, -0.5F, LAZ, -0.6F});
		move(ZFighterCombo.SPIN_KICK,
			new float[] {RLX, 0.1F, RLZ, 0.4F, SPIN, 0.4F, BY, 0.0F},
			new float[] {RLX, -0.35F, RLZ, 1.45F, SPIN, 0.72F, BY, 0.0F, BZ, -0.35F, RAX, -0.4F, RAZ, -0.4F, LAX, -0.4F, LAZ, -0.5F});
		move(ZFighterCombo.AXE_KICK,
			new float[] {RLX, -2.6F, RLZ, 0.15F, BX, -0.25F},
			new float[] {RLX, -0.35F, RLZ, 0.0F, BX, 0.35F});
		move(ZFighterCombo.PALM,
			new float[] {RAX, 0.4F, RAY, 0.0F, RAZ, 0.25F, LAX, 0.4F, LAY, 0.0F, LAZ, -0.25F, BY, 0.35F, BX, 0.15F},
			new float[] {RAX, -1.62F, RAY, -0.25F, LAX, -1.62F, LAY, 0.25F, BY, 0.0F, BX, -0.15F, RLX, 0.45F, LLX, -0.5F});
		move(ZFighterCombo.DRAGON_KICK,
			new float[] {RLX, 0.3F, LLX, 0.3F, SPIN, 0.45F, BY, 0.0F, BX, 0.2F, RAX, 0.5F, LAX, 0.5F},
			new float[] {RLX, -1.55F, RLZ, 0.1F, LLX, -1.3F, LLZ, -0.1F, SPIN, 0.85F, BY, 0.0F, BX, -0.5F, RAX, 0.65F, RAZ, 0.35F, LAX, 0.65F,
				LAZ, -0.35F});
	}

	private static void move(int move, float[] wound, float[] struck) {
		MOVES[move] = new float[][] {apply(wound), apply(struck)};
	}

	private static float[] apply(float[] changes) {
		float[] out = STANCE.clone();
		for (int i = 0; i < changes.length; i += 2) {
			out[(int)changes[i]] = changes[i + 1];
		}
		return out;
	}

	/** Per player: [0] amount now, [1] amount before, [2] ticks into the move, [3] the move, [4] its ticks, [5] the packed value. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterComboPose() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Integer packed = player.getAttached(ZFighterCombo.MOVE);
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (packed == null) {
					continue;
				}
				s = new float[6];
				s[5] = Float.NaN;
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			if (packed != null) {
				if (s[5] != packed.floatValue()) {
					s[5] = packed.floatValue();
					s[2] = 0.0F;
					s[3] = ZFighterCombo.move(packed);
					s[4] = ZFighterCombo.ticks(packed);
				} else {
					s[2]++;
				}
				s[0] = Math.min(1.0F, s[0] + EASE_IN);
			} else {
				s[5] = Float.NaN;
				s[0] = Math.max(0.0F, s[0] - EASE_OUT);
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		if (s == null) {
			state.setData(KEY, null);
			return;
		}
		int move = Mth.clamp((int)s[3], 0, ZFighterCombo.MOVES - 1);
		float progress = Mth.clamp((s[2] + partialTicks) / Math.max(1.0F, s[4]), 0.0F, 1.0F);
		float amount = Mth.lerp(partialTicks, s[1], s[0]);
		float[] frame = frame(MOVES[move], Float.isNaN(s[5]) ? 1.0F : progress);
		// a spinning kick turns the whole body round once (the head goes with it)
		if (frame[SPIN] > 0.0F && amount > 0.001F) {
			state.bodyRot -= frame[SPIN] * 360.0F * amount;
		}
		state.setData(KEY, new Pose(frame, amount));
	}

	/** Stance, wound up, struck, held, back to the stance. */
	private static float[] frame(float[][] keys, float t) {
		float[] out = new float[SLOTS];
		float[] wound = keys[0];
		float[] struck = keys[1];
		for (int i = 0; i < SLOTS; i++) {
			float v;
			if (t < WOUND) {
				v = Mth.lerp(ZFighterFlightPose.smooth(t / WOUND), STANCE[i], wound[i]);
			} else if (t < STRUCK) {
				// the snap: fast out of the wind-up
				float k = (t - WOUND) / (STRUCK - WOUND);
				v = Mth.lerp(1.0F - (1.0F - k) * (1.0F - k), wound[i], struck[i]);
			} else if (t < HELD) {
				v = struck[i];
			} else {
				float to = i == SPIN && struck[SPIN] > 0.0F ? 1.0F : STANCE[i];
				v = Mth.lerp(ZFighterFlightPose.smooth((t - HELD) / (1.0F - HELD)), struck[i], to);
			}
			out[i] = v;
		}
		if (t >= 1.0F && out[SPIN] >= 1.0F) {
			out[SPIN] = 0.0F;
		}
		return out;
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F || avatar.deathTime > 0.0F) {
			return;
		}
		float a = ZFighterFlightPose.smooth(pose.amount());
		float[] f = pose.frame();
		h.body.yRot = Mth.lerp(a, h.body.yRot, f[BY]);
		h.body.xRot = Mth.lerp(a, h.body.xRot, f[BX]);
		h.body.zRot = Mth.lerp(a, h.body.zRot, f[BZ]);
		// the shoulders go round with the body, as vanilla's swing does
		float turn = h.body.yRot;
		h.rightArm.z = Mth.lerp(a, h.rightArm.z, Mth.sin(turn) * 5.0F);
		h.rightArm.x = Mth.lerp(a, h.rightArm.x, -Mth.cos(turn) * 5.0F);
		h.leftArm.z = Mth.lerp(a, h.leftArm.z, -Mth.sin(turn) * 5.0F);
		h.leftArm.x = Mth.lerp(a, h.leftArm.x, Mth.cos(turn) * 5.0F);
		pose(h.rightArm, f[RAX], f[RAY] + turn, f[RAZ], a);
		pose(h.leftArm, f[LAX], f[LAY] + turn, f[LAZ], a);
		pose(h.rightLeg, f[RLX], 0.0F, f[RLZ], a);
		pose(h.leftLeg, f[LLX], 0.0F, f[LLZ], a);
	}

	private static void pose(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
