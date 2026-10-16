package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Shadowbox;
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
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The hold combo's martial-arts moves ({@link ZFighterCombo}), on everyone who throws them, and the same moves when
 * shadowboxing ({@link Shadowbox}, over MeditatePose). Every move is a whole-body move: a step or lunge into the blow
 * (the lead foot goes out, the rear leg drives), the hips and shoulders turning through it, the weight shifting from the
 * back foot onto the front (or onto the standing leg for a kick), the upper body tipping at the hip and the head tucked
 * behind the shoulder, then the follow-through and back into a bladed stance (left foot forward, fists up).
 *
 * <p>Each move is keyed as a wind-up (anticipation: the coil, the chamber, the weight back), a strike (the blow lands
 * halfway, where the server strikes), a follow-through past it and often a landing (the kicking foot stepping down in
 * front, the rear foot dragging up behind a punch). The keys blend on eased curves: the wind-up eases in, the strike
 * accelerates into contact, the follow-through decelerates out of it and the recovery eases back to the stance; a new
 * move blends out of wherever the last one was. Spinning kicks turn the whole body round once.
 *
 * <p>Whole-body work happens on the model: the hips turn everything below and above them (the head stays on the foe),
 * the upper body tips forward, back or sideways at the hip, and the whole body drops into a sweep or rises into a jump.
 * In your own first-person view only the limbs move (the whole-body turns, tips and spins would swing the camera's body
 * about).
 */
public final class ZFighterComboPose {
	/** The pose this frame, how much of it shows, whether it's your own seen from your eyes, shadowboxing, a heavy tremble. */
	public record Pose(float[] frame, float amount, boolean ownView, boolean shadow, float tremble) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_combo");
	/** How fast the pose comes on and goes off (share a tick). */
	static final float EASE_IN = 0.34F;
	static final float EASE_OUT = 0.2F;
	/** Into a new move: how much of it blends out of where the last one was. */
	static final float BLEND = 0.3F;
	/** Shadowboxing: how much of a move fades in and out next to a move that isn't one of these, and the settle in (ticks). */
	static final float SHADOW_FADE = 0.2F;
	static final float SHADOW_SETTLE = 12.0F;
	/** Catching his breath after a finisher: how fast and how deep. */
	static final float BREATH_RATE = 0.32F;
	static final float BREATH_DEPTH = 0.035F;
	/** Heavy-gravity shadowboxing: how hard the arms tremble. */
	static final float HEAVY_TREMBLE = 0.05F;

	/** Shares of a move: wound up, struck (the blow lands), followed through, recovered; the landing peaks at LAND. */
	static final float WOUND = 0.3F, STRUCK = 0.5F, FOLLOWED = 0.62F, FOLLOW_GONE = 0.82F, RECOVERED = 0.95F;
	static final float LAND = 0.84F, LAND_IN = 0.14F, LAND_OUT = 0.14F;

	// frame slots: right (rear) arm x y z, left (lead) arm x y z, right leg x y z, left leg x y z (arm yaws are aimed from the
	// stance's front, whatever the hips do; legs swing in the hips' frame); torso twist (shoulders, + brings the left side
	// forward), hips (the whole body's turn, same sign); tip at the hip (+ forward), tip sideways (+ over to the left); drop
	// or rise (blocks, + up); weight forward (blocks); weight to the left (blocks); head nod (+ chin down) and turn; spin
	// (whole turns, - turns the right side back)
	static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLY = 7, RLZ = 8, LLX = 9, LLY = 10, LLZ = 11, TORSO = 12,
		HIPS = 13, PITCH = 14, ROLL = 15, DY = 16, FWD = 17, SIDE = 18, HEADX = 19, HEADY = 20, SPIN = 21, SLOTS = 22;

	/** Bladed, left foot forward, rear foot turned out, fists up by the chin, chin tucked, a little forward on the balls of the feet. */
	static final float[] STANCE = {-1.25F, -0.3F, 0.1F, -1.4F, 0.25F, -0.05F, 0.28F, 0.15F, 0.06F, -0.3F, -0.05F, -0.05F, 0.1F, 0.35F, 0.08F,
		0.0F, 0.0F, 0.0F, 0.0F, 0.12F, 0.0F, 0.0F};

	/** The hips, in model units: tips pivot here, and the hips turn about the line through it. */
	private static final Vector3f HIP = new Vector3f(0.0F, 12.0F, 0.0F);

	/** A move's keys: wound up, struck, followed through (from the strike), landing (from the stance, or null); its spin. */
	private record Art(float[] wind, float[] hit, float[] follow, float[] land, float spinAt, float spinEnd) {
	}

	private static final Art[] ARTS = new Art[ZFighterCombo.MOVES];

	static {
		// the lead jab: the front foot steps out, the rear leg drives, the left shoulder thrown at it, the rear foot drags up after
		art(ZFighterCombo.JAB,
			new float[] {LAX, -1.3F, LAY, 0.45F, LAZ, -0.1F, LLX, -0.5F, RLX, 0.3F, TORSO, 0.0F, HIPS, 0.3F, PITCH, 0.04F, FWD, -0.03F, SIDE, -0.03F,
				HEADX, 0.15F},
			new float[] {LAX, -1.6F, LAY, 0.0F, LAZ, 0.0F, LLX, -0.55F, LLY, -0.1F, RLX, 0.48F, RLY, 0.25F, TORSO, 0.5F, HIPS, 0.55F, PITCH, 0.2F,
				FWD, 0.12F, SIDE, 0.04F, RAX, -1.35F, RAY, -0.45F, HEADX, 0.2F, HEADY, -0.08F},
			new float[] {LAX, -1.62F, TORSO, 0.58F, PITCH, 0.22F, FWD, 0.14F},
			new float[] {RLX, 0.15F, LLX, -0.38F, SIDE, 0.02F}, 0.0F, 0.0F);
		// the reverse punch: coiled with the fist chambered low, then the hips turned right through it, the lead hand pulled
		// back to the ribs, the rear heel up and turning
		art(ZFighterCombo.REVERSE_PUNCH,
			new float[] {RAX, -0.75F, RAY, -0.05F, RAZ, 0.2F, LAX, -1.55F, LAY, 0.1F, LLX, -0.48F, RLX, 0.32F, TORSO, 0.35F, HIPS, 0.55F,
				PITCH, 0.04F, SIDE, -0.03F, FWD, -0.02F, HEADX, 0.14F},
			new float[] {RAX, -1.6F, RAY, -0.05F, RAZ, 0.0F, LAX, -0.75F, LAY, 0.55F, LAZ, -0.15F, LLX, -0.5F, RLX, 0.55F, RLY, -0.3F, TORSO, -0.4F,
				HIPS, -0.25F, PITCH, 0.22F, FWD, 0.14F, SIDE, 0.04F, HEADX, 0.16F},
			new float[] {TORSO, -0.55F, HIPS, -0.35F, RAX, -1.62F, PITCH, 0.25F},
			new float[] {RLX, 0.12F, LLX, -0.36F, SIDE, 0.02F}, 0.0F, 0.0F);
		// the rear elbow: a short step and a sink, the arm cocked out wide, then swept across the face line with the hips
		art(ZFighterCombo.ELBOW,
			new float[] {RAX, -1.55F, RAY, 0.75F, RAZ, 0.35F, LAX, -1.5F, LAY, 0.35F, LLX, -0.52F, RLX, 0.32F, TORSO, 0.35F, HIPS, 0.55F, DY, -0.03F,
				PITCH, 0.06F, SIDE, -0.02F, HEADX, 0.2F},
			new float[] {RAX, -1.72F, RAY, -0.85F, RAZ, 0.1F, LAX, -1.2F, LAY, 0.6F, LAZ, -0.1F, LLX, -0.5F, RLX, 0.55F, RLY, -0.35F, TORSO, -0.6F,
				HIPS, -0.35F, PITCH, 0.24F, ROLL, 0.06F, FWD, 0.13F, SIDE, 0.05F, HEADX, 0.22F},
			new float[] {RAY, -1.2F, TORSO, -0.8F, HIPS, -0.45F, ROLL, 0.1F},
			new float[] {RLX, 0.12F, LLX, -0.36F, SIDE, 0.02F}, 0.0F, 0.0F);
		// the chi palm: sunk wide and low, the palm drawn back by the ribs, then thrust out from the hips, the lead hand pulled
		// home; held out a beat for the light
		art(ZFighterCombo.CHI_PALM,
			new float[] {RAX, -0.35F, RAY, 0.0F, RAZ, 0.3F, LAX, -1.5F, LAY, 0.05F, LAZ, 0.0F, LLX, -0.55F, RLX, 0.4F, LLZ, -0.18F, RLZ, 0.18F,
				TORSO, 0.4F, HIPS, 0.6F, DY, -0.07F, PITCH, 0.0F, SIDE, -0.03F, HEADX, 0.1F},
			new float[] {RAX, -1.57F, RAY, -0.02F, RAZ, -0.02F, LAX, -0.55F, LAY, 0.3F, LAZ, -0.3F, LLX, -0.58F, RLX, 0.55F, RLY, -0.25F,
				LLZ, -0.14F, RLZ, 0.14F, TORSO, -0.4F, HIPS, -0.15F, DY, -0.05F, PITCH, 0.24F, FWD, 0.16F, SIDE, 0.04F, HEADX, 0.18F},
			new float[] {RAX, -1.6F, PITCH, 0.27F, FWD, 0.18F},
			new float[] {RLX, 0.12F, LLX, -0.36F, SIDE, 0.02F}, 0.0F, 0.0F);
		// the rising knee: a step onto the front foot, both hands up reaching for the head, then pulled down onto the knee
		// driving up, rising on the toes and leaning back off it
		art(ZFighterCombo.RISING_KNEE,
			new float[] {RAX, -2.0F, RAY, -0.25F, RAZ, 0.15F, LAX, -2.0F, LAY, 0.25F, LAZ, -0.15F, LLX, -0.45F, RLX, 0.5F, TORSO, 0.2F, HIPS, 0.45F,
				PITCH, 0.1F, SIDE, 0.04F, FWD, 0.03F, HEADX, 0.1F},
			new float[] {RLX, -1.6F, RLY, 0.0F, RLZ, 0.04F, LLX, 0.12F, LLY, -0.05F, RAX, -1.0F, RAY, -0.3F, RAZ, 0.05F, LAX, -1.0F, LAY, 0.3F,
				LAZ, -0.05F, TORSO, -0.15F, HIPS, 0.05F, PITCH, -0.15F, DY, 0.08F, FWD, 0.13F, SIDE, 0.05F, HEADX, 0.28F},
			new float[] {RLX, -1.75F, DY, 0.1F, PITCH, -0.2F},
			new float[] {RLX, -0.15F, LLX, -0.1F, DY, -0.02F}, 0.0F, 0.0F);
		// the front snap kick: the knee chambered up, the foot snapped out leaning back off it, the rear arm swung down for
		// balance; it steps down in front (the feet switched for a moment)
		art(ZFighterCombo.FRONT_KICK,
			new float[] {RLX, -1.35F, RLZ, 0.04F, LLX, 0.05F, RAX, -1.35F, RAY, -0.35F, LAX, -1.5F, LAY, 0.3F, TORSO, 0.05F, HIPS, 0.15F,
				PITCH, -0.04F, DY, 0.03F, SIDE, 0.04F, FWD, 0.03F, HEADX, 0.14F},
			new float[] {RLX, -1.78F, RLZ, 0.02F, LLX, 0.14F, RAX, -0.55F, RAY, -0.1F, RAZ, 0.4F, LAX, -1.45F, LAY, 0.3F, TORSO, 0.1F, HIPS, 0.05F,
				PITCH, -0.24F, DY, 0.05F, FWD, 0.12F, SIDE, 0.05F, HEADX, 0.24F},
			new float[] {RLX, -1.6F, PITCH, -0.2F},
			new float[] {RLX, -0.3F, LLX, 0.22F, HIPS, -0.15F, TORSO, -0.05F, SIDE, -0.02F, FWD, 0.06F}, 0.0F, 0.0F);
		// the roundhouse: a step out with the front foot turned, then the hips rolled right over, the shin swung round into it,
		// leaning away, the rear arm flung down and back
		art(ZFighterCombo.ROUNDHOUSE,
			new float[] {LLX, -0.42F, LLY, -0.55F, RLX, 0.38F, RLZ, 0.22F, TORSO, 0.4F, HIPS, 0.6F, LAX, -1.5F, LAY, 0.3F, RAX, -1.3F, SIDE, 0.04F,
				PITCH, 0.04F, HEADX, 0.14F},
			new float[] {HIPS, -1.15F, TORSO, -0.25F, RLX, -0.45F, RLY, 0.0F, RLZ, 1.35F, LLX, 0.1F, LLY, -0.7F, LLZ, -0.06F, ROLL, 0.42F, PITCH, -0.06F,
				RAX, -0.45F, RAY, 0.25F, RAZ, 0.55F, LAX, -1.5F, LAY, 0.45F, DY, 0.03F, FWD, 0.08F, SIDE, 0.05F, HEADX, 0.12F},
			new float[] {HIPS, -1.4F, RLZ, 1.05F, RLX, -0.15F, ROLL, 0.32F},
			new float[] {RLX, -0.1F, LLX, 0.15F, HIPS, -0.1F, TORSO, -0.1F}, 0.0F, 0.0F);
		// the leg sweep: dropped low over the bent front leg, a hand down, then the rear leg swept round along the ground
		art(ZFighterCombo.LEG_SWEEP,
			new float[] {DY, -0.32F, LLX, -1.25F, LLZ, -0.12F, RLX, 0.6F, RLZ, 0.15F, HIPS, 0.6F, TORSO, 0.4F, PITCH, 0.35F, LAX, -0.6F, LAZ, -0.35F,
				RAX, -0.7F, RAZ, 0.25F, HEADX, -0.05F},
			new float[] {DY, -0.55F, HIPS, -0.9F, TORSO, -0.2F, RLX, -0.12F, RLY, 0.0F, RLZ, 1.45F, LLX, -1.5F, LLZ, -0.2F, PITCH, 0.42F, ROLL, 0.22F,
				LAX, -0.15F, LAZ, -0.65F, RAX, -0.75F, RAZ, 0.85F, FWD, 0.05F, HEADX, -0.2F},
			new float[] {HIPS, -1.35F, RLZ, 1.25F, ROLL, 0.15F},
			new float[] {DY, -0.12F, LLX, -0.55F, PITCH, 0.15F}, 0.0F, 0.0F);
		// the crescent kick: the leg swung up from outside, over the top and across, the hips turning with it
		art(ZFighterCombo.CRESCENT_KICK,
			new float[] {RLX, -0.65F, RLZ, 0.65F, RLY, 0.0F, LLX, 0.02F, LLY, -0.3F, HIPS, 0.45F, TORSO, 0.3F, ROLL, 0.08F, SIDE, 0.04F, RAX, -1.3F,
				LAX, -1.5F, DY, 0.02F, HEADX, 0.14F},
			new float[] {RLX, -1.75F, RLZ, -0.25F, LLX, 0.1F, LLY, -0.35F, HIPS, -0.25F, TORSO, -0.1F, ROLL, 0.14F, PITCH, -0.16F, RAX, -0.65F,
				RAZ, 0.5F, LAX, -1.4F, LAY, 0.35F, DY, 0.05F, FWD, 0.1F, SIDE, 0.05F, HEADX, 0.2F},
			new float[] {RLX, -1.0F, RLZ, -0.55F, HIPS, -0.45F, ROLL, 0.1F},
			new float[] {RLX, -0.28F, LLX, 0.22F, HIPS, -0.1F}, 0.0F, 0.0F);
		// the axe kick: the leg raised high, leaning back on the toes, then chopped straight down, the body folding over it
		art(ZFighterCombo.AXE_KICK,
			new float[] {RLX, -2.55F, RLZ, 0.22F, LLX, 0.06F, PITCH, -0.3F, HIPS, 0.25F, TORSO, 0.1F, RAX, -0.95F, RAZ, 0.35F, LAX, -1.55F, DY, 0.07F,
				SIDE, 0.05F, FWD, 0.02F, HEADX, -0.12F},
			new float[] {RLX, -0.6F, RLZ, 0.02F, LLX, 0.12F, PITCH, 0.3F, HIPS, 0.15F, RAX, -0.35F, RAZ, 0.2F, LAX, -1.05F, DY, -0.02F, FWD, 0.15F,
				SIDE, 0.04F, HEADX, 0.32F},
			new float[] {RLX, -0.32F, PITCH, 0.36F, DY, -0.05F},
			new float[] {RLX, -0.25F, LLX, 0.2F, SIDE, -0.02F}, 0.0F, 0.0F);
		// the spinning back kick: turning the right side away, the back to the foe and the heel driven straight back into it,
		// tipped away over the standing leg, then on round
		art(ZFighterCombo.SPIN_BACK_KICK,
			new float[] {RLX, 0.3F, LLY, 0.3F, PITCH, 0.12F, TORSO, 0.25F, HIPS, 0.2F, RAX, -1.45F, LAX, -1.45F, DY, -0.02F, HEADX, 0.15F},
			new float[] {RLX, 1.3F, RLZ, 0.1F, LLX, -0.06F, PITCH, 0.48F, HIPS, 0.0F, TORSO, 0.0F, RAX, -0.95F, RAY, -0.2F, LAX, -1.0F, LAY, 0.2F,
				FWD, -0.1F, HEADX, 0.25F},
			new float[] {RLX, 0.95F, PITCH, 0.38F},
			new float[] {RLX, 0.1F, LLX, -0.2F}, -0.5F, -1.0F);
		// the spinning hook kick: all the way round, the heel whipping across at head height, leaning away from it, arms out
		art(ZFighterCombo.SPIN_HOOK_KICK,
			new float[] {PITCH, 0.12F, DY, -0.03F, RAX, -1.45F, LAX, -1.45F, RLX, 0.25F, HEADX, 0.15F},
			new float[] {RLX, -0.3F, RLZ, 1.4F, LLX, 0.05F, LLZ, -0.05F, ROLL, 0.5F, PITCH, 0.02F, RAX, -0.5F, RAZ, 0.55F, LAX, -0.6F, LAZ, -0.65F,
				HIPS, 0.0F, TORSO, 0.0F, DY, 0.04F, HEADX, 0.1F},
			new float[] {RLZ, 1.15F, RLX, 0.25F, ROLL, 0.4F},
			new float[] {RLX, 0.15F, LLX, -0.25F}, -0.72F, -1.0F);
		// the double palm: a long step in, sunk low with both palms drawn back to the chest, then both driven out together
		// with the whole body behind them, squared up
		art(ZFighterCombo.DOUBLE_PALM,
			new float[] {RAX, -0.5F, RAY, 0.1F, RAZ, 0.15F, LAX, -0.5F, LAY, -0.1F, LAZ, -0.15F, HIPS, 0.1F, TORSO, 0.0F, DY, -0.08F, PITCH, -0.08F,
				LLX, -0.6F, RLX, 0.38F, LLZ, -0.12F, RLZ, 0.12F, FWD, -0.03F, HEADX, 0.06F},
			new float[] {RAX, -1.58F, RAY, -0.12F, RAZ, 0.0F, LAX, -1.58F, LAY, 0.12F, LAZ, 0.0F, HIPS, 0.0F, TORSO, 0.0F, DY, -0.04F, PITCH, 0.32F,
				FWD, 0.2F, LLX, -0.7F, RLX, 0.6F, LLZ, -0.1F, RLZ, 0.1F, HEADX, 0.2F},
			new float[] {RAX, -1.55F, LAX, -1.55F, PITCH, 0.34F, FWD, 0.22F},
			new float[] {RLX, 0.15F, LLX, -0.4F}, 0.0F, 0.0F);
		// the flying knee: a dip and a running step, arms pumping, then up off the ground with the knee driven at it and both
		// hands up; down again on the front foot
		art(ZFighterCombo.FLYING_KNEE,
			new float[] {LLX, -0.5F, RLX, 0.5F, RAX, 0.4F, RAZ, 0.1F, LAX, -1.2F, DY, -0.05F, PITCH, 0.16F, HIPS, 0.3F, TORSO, 0.2F, HEADX, 0.12F,
				SIDE, 0.03F},
			new float[] {DY, 0.45F, RLX, -1.75F, RLZ, 0.04F, LLX, 0.45F, HIPS, 0.05F, TORSO, -0.2F, RAX, -2.1F, RAY, -0.3F, LAX, -2.1F, LAY, 0.3F,
				PITCH, -0.1F, FWD, 0.15F, HEADX, 0.22F},
			new float[] {DY, 0.35F, RLX, -1.45F},
			new float[] {DY, -0.06F, LLX, -0.35F, RLX, 0.35F}, 0.0F, 0.0F);
		// the flying double kick: up off both feet, the left foot kicking out first, then scissored for the right, arms flung wide
		art(ZFighterCombo.FLYING_DOUBLE_KICK,
			new float[] {DY, 0.3F, LLX, -1.55F, LLZ, -0.05F, RLX, 0.45F, PITCH, -0.12F, RAX, -0.5F, RAZ, 0.55F, LAX, -0.6F, LAZ, -0.55F, HIPS, 0.2F,
				TORSO, 0.0F, FWD, 0.05F, HEADX, 0.18F},
			new float[] {DY, 0.5F, RLX, -1.75F, RLZ, 0.05F, LLX, 0.45F, PITCH, -0.25F, RAX, -0.35F, RAZ, 0.75F, LAX, -0.35F, LAZ, -0.75F, HIPS, 0.0F,
				TORSO, 0.0F, FWD, 0.15F, HEADX, 0.25F},
			new float[] {DY, 0.38F, RLX, -1.5F},
			new float[] {DY, -0.07F, LLX, -0.35F, RLX, 0.35F, PITCH, 0.15F}, 0.0F, 0.0F);
		// the rising dragon fist: crouched deep and wound right round, the fist low by the hip, then up off the ground in a
		// corkscrew, the fist driven straight up through it, eyes following it; down into a crouch
		art(ZFighterCombo.DRAGON_FIST,
			new float[] {DY, -0.25F, LLX, -0.95F, LLZ, -0.08F, RLX, 0.5F, RLZ, 0.1F, PITCH, 0.35F, HIPS, 0.6F, TORSO, 0.5F, RAX, 0.35F, RAY, 0.0F,
				RAZ, 0.25F, LAX, -1.3F, LAY, 0.3F, HEADX, 0.0F},
			new float[] {DY, 0.7F, RAX, -3.0F, RAY, -0.1F, RAZ, -0.1F, LAX, -0.55F, LAY, 0.0F, LAZ, -0.3F, RLX, 0.18F, RLZ, 0.05F, LLX, -0.65F,
				LLZ, 0.0F, HIPS, -0.4F, TORSO, -0.4F, PITCH, -0.15F, ROLL, 0.1F, FWD, 0.12F, HEADX, -0.45F},
			new float[] {DY, 0.85F, RAX, -3.05F, PITCH, -0.2F},
			new float[] {DY, -0.1F, PITCH, 0.2F, LLX, -0.5F, RLX, 0.4F}, -0.12F, -1.0F);
		for (int move = 0; move < ARTS.length; move++) {
			if (ARTS[move] == null) {
				throw new IllegalStateException("No pose for Z Fighter combo move " + move);
			}
		}
	}

	private static void art(int move, float[] wind, float[] hit, float[] follow, float[] land, float spinAt, float spinEnd) {
		float[] struck = apply(STANCE, hit);
		ARTS[move] = new Art(apply(STANCE, wind), struck, apply(struck, follow), land == null ? null : apply(STANCE, land), spinAt, spinEnd);
	}

	private static float[] apply(float[] from, float[] changes) {
		float[] out = from.clone();
		for (int i = 0; i < changes.length; i += 2) {
			out[(int)changes[i]] = changes[i + 1];
		}
		return out;
	}

	/**
	 * Per player: [0] amount now, [1] amount before, [2] ticks into the move, [3] the move, [4] its ticks, [5] the packed
	 * value (NaN once let go), [6] the move before (NaN: none), [7] how far through it that one got.
	 */
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
				s = new float[8];
				s[5] = Float.NaN;
				s[6] = Float.NaN;
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			if (packed != null && s[5] != packed.floatValue()) {
				// a new blow: it blends out of wherever the last one had got to
				if (s[0] > 0.0F) {
					s[6] = s[3];
					s[7] = Mth.clamp((s[2] + 1.0F) / Math.max(1.0F, s[4]), 0.0F, 1.0F);
				} else {
					s[6] = Float.NaN;
				}
				s[5] = packed.floatValue();
				s[2] = 0.0F;
				s[3] = ZFighterCombo.move(packed);
				s[4] = ZFighterCombo.ticks(packed);
			} else {
				// on through the move (let go, it finishes as it fades)
				s[2]++;
			}
			if (packed != null) {
				s[0] = Math.min(1.0F, s[0] + EASE_IN);
			} else {
				s[5] = Float.NaN;
				s[0] = Math.max(0.0F, s[0] - EASE_OUT);
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		float[] s = STATE.get(entity.getId());
		Pose pose = s == null ? null : combo(s, partialTicks, ownView);
		if (pose == null) {
			pose = shadow(entity, partialTicks, ownView);
		}
		// a spinning kick turns the whole body round once (the head goes with it)
		if (pose != null && !ownView && pose.frame()[SPIN] != 0.0F) {
			state.bodyRot -= pose.frame()[SPIN] * 360.0F * smooth(pose.amount());
		}
		state.setData(KEY, pose);
	}

	/** The hold combo's blow: on through the move, out of the last one, catching his breath after a finisher. */
	private static @Nullable Pose combo(float[] s, float partialTicks, boolean ownView) {
		float amount = Mth.lerp(partialTicks, s[1], s[0]);
		if (amount <= 0.001F) {
			return null;
		}
		int move = Mth.clamp((int)s[3], 0, ZFighterCombo.MOVES - 1);
		float length = Math.max(1.0F, s[4]);
		float into = s[2] + partialTicks;
		float progress = Mth.clamp(into / length, 0.0F, 1.0F);
		float[] frame = frame(move, progress);
		if (!Float.isNaN(s[6]) && progress < BLEND) {
			float[] last = frame(Mth.clamp((int)s[6], 0, ZFighterCombo.MOVES - 1), s[7]);
			// a spin cut short unwinds the short way
			last[SPIN] -= Math.round(last[SPIN]);
			float k = smooth(progress / BLEND);
			for (int i = 0; i < SLOTS; i++) {
				frame[i] = Mth.lerp(k, last[i], frame[i]);
			}
		}
		if (!Float.isNaN(s[5]) && into > length) {
			breathe(frame, into - length);
		}
		return new Pose(frame, amount, ownView, false, 0.0F);
	}

	/** Shadowboxing one of these moves (MeditatePose draws the rest and the stance underneath). */
	private static @Nullable Pose shadow(Avatar entity, float partialTicks, boolean ownView) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		Meditate.State meditating = Meditate.of(player);
		if (meditating == null || meditating.kind() == Meditate.MEDITATING) {
			return null;
		}
		long ticks = Math.max(0L, entity.level().getGameTime() - meditating.since());
		Shadowbox.Step step = Shadowbox.step(ticks, meditating.combos());
		if (step.art() < 0 || step.art() >= ZFighterCombo.MOVES) {
			return null;
		}
		float t = step.progress(partialTicks);
		// one of these straight after another flows on; next to anything else it fades in and out of MeditatePose's stance
		long start = ticks - step.into();
		float in = artAt(start - 1L, meditating.combos()) ? 1.0F : smooth(t / SHADOW_FADE);
		float out = artAt(start + step.length(), meditating.combos()) ? 1.0F : smooth((1.0F - t) / SHADOW_FADE);
		float amount = Math.min(in, out) * smooth((ticks + partialTicks) / SHADOW_SETTLE);
		if (amount <= 0.001F) {
			return null;
		}
		float tremble = meditating.kind() == Meditate.SHADOWBOXING_HEAVY ? Mth.sin((ticks + partialTicks) * 2.9F) * HEAVY_TREMBLE : 0.0F;
		return new Pose(frame(step.art(), t), amount, ownView, true, tremble);
	}

	private static boolean artAt(long ticks, int combos) {
		return ticks >= 0L && Shadowbox.step(ticks, combos).art() >= 0;
	}

	/** The move {@code t} of the way through (0-1). */
	static float[] frame(int move, float t) {
		Art art = ARTS[move];
		float[] out = STANCE.clone();
		layer(out, art.wind(), windWeight(t));
		layer(out, art.hit(), hitWeight(t));
		layer(out, art.follow(), followWeight(t));
		if (art.land() != null) {
			layer(out, art.land(), landWeight(t));
		}
		if (art.spinAt() != 0.0F || art.spinEnd() != 0.0F) {
			out[SPIN] = t < STRUCK ? art.spinAt() * smooth(t / STRUCK)
				: Mth.lerp(smooth((t - STRUCK) / (RECOVERED - STRUCK)), art.spinAt(), art.spinEnd());
			// all the way round is the same as not at all
			if (t >= 1.0F && Math.abs(out[SPIN]) >= 1.0F) {
				out[SPIN] = 0.0F;
			}
		}
		return out;
	}

	private static void layer(float[] out, float[] key, float weight) {
		if (weight <= 0.0F) {
			return;
		}
		for (int i = 0; i < SPIN; i++) {
			out[i] = Mth.lerp(weight, out[i], key[i]);
		}
	}

	/** Eases into the wind-up, held there while the strike takes over. */
	private static float windWeight(float t) {
		return t < WOUND ? smooth(t / WOUND) : t < FOLLOWED ? 1.0F : 0.0F;
	}

	/** The strike: accelerating out of the wind-up into contact, held, then eased back to the stance. */
	private static float hitWeight(float t) {
		if (t < WOUND) {
			return 0.0F;
		}
		if (t < STRUCK) {
			float k = (t - WOUND) / (STRUCK - WOUND);
			return k * k * (2.0F - k);
		}
		if (t < FOLLOWED) {
			return 1.0F;
		}
		return 1.0F - smooth((t - FOLLOWED) / (RECOVERED - FOLLOWED));
	}

	/** Carrying on past the blow and slowing, then let go. */
	private static float followWeight(float t) {
		if (t < STRUCK) {
			return 0.0F;
		}
		if (t < FOLLOWED) {
			float k = (t - STRUCK) / (FOLLOWED - STRUCK);
			return 1.0F - (1.0F - k) * (1.0F - k);
		}
		return 1.0F - smooth((t - FOLLOWED) / (FOLLOW_GONE - FOLLOWED));
	}

	/** The foot coming down (or dragging up) at the end. */
	private static float landWeight(float t) {
		return t < LAND ? smooth(1.0F - (LAND - t) / LAND_IN) : smooth(1.0F - (t - LAND) / LAND_OUT);
	}

	/** Between strings: the shoulders rise and fall, the hands a little lower. */
	private static void breathe(float[] frame, float after) {
		float in = smooth(after / 4.0F);
		float breath = Mth.sin(after * BREATH_RATE) * BREATH_DEPTH * in;
		frame[PITCH] += breath * 2.0F;
		frame[RAX] += 0.12F * in + breath * 3.0F;
		frame[LAX] += 0.12F * in + breath * 3.0F;
		frame[HEADX] += breath;
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F || avatar.deathTime > 0.0F) {
			return;
		}
		float a = smooth(pose.amount());
		float[] f = pose.frame();
		float whole = pose.ownView() ? 0.0F : 1.0F;
		if (pose.shadow()) {
			// MeditatePose tipped these at the hip for its own stand-in move: back where they belong first
			place(h.body, 0.0F, 0.0F, 0.0F, a);
			place(h.head, 0.0F, 0.0F, 0.0F, a);
			place(h.rightArm, -5.0F, 2.0F, 0.0F, a);
			place(h.leftArm, 5.0F, 2.0F, 0.0F, a);
		}
		// the shoulders go round with the torso, as vanilla's swing does
		float torso = f[TORSO];
		set(h.body, 0.0F, torso, 0.0F, a);
		h.rightArm.z = Mth.lerp(a, h.rightArm.z, Mth.sin(torso) * 5.0F);
		h.rightArm.x = Mth.lerp(a, h.rightArm.x, -Mth.cos(torso) * 5.0F);
		h.leftArm.z = Mth.lerp(a, h.leftArm.z, -Mth.sin(torso) * 5.0F);
		h.leftArm.x = Mth.lerp(a, h.leftArm.x, Mth.cos(torso) * 5.0F);
		// the arms are aimed from the front: the hips' turn (laid on below) is taken back out of them
		float hips = f[HIPS] * whole;
		set(h.rightArm, f[RAX] + pose.tremble(), f[RAY] - hips, f[RAZ], a);
		set(h.leftArm, f[LAX] - pose.tremble(), f[LAY] - hips, f[LAZ], a);
		set(h.rightLeg, f[RLX], f[RLY], f[RLZ], a);
		set(h.leftLeg, f[LLX], f[LLY], f[LLZ], a);
		if (pose.shadow()) {
			h.head.xRot = Mth.lerp(a, h.head.xRot, f[HEADX]);
			h.head.zRot = Mth.lerp(a, h.head.zRot, 0.0F);
		} else {
			h.head.xRot += f[HEADX] * a;
		}
		h.head.yRot += f[HEADY] * a;
		float k = a * whole;
		if (k <= 0.001F) {
			return;
		}
		// the hips turn everything but the head (it stays on the foe)
		turn(new ModelPart[] {h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg}, new Quaternionf().rotationZYX(0.0F, f[HIPS] * k, 0.0F));
		// the upper body tips at the hip (the legs stay planted)
		if (Math.abs(f[PITCH]) > 1.0E-4F || Math.abs(f[ROLL]) > 1.0E-4F) {
			turn(new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}, new Quaternionf().rotationZYX(f[ROLL] * k, 0.0F, f[PITCH] * k));
		}
		// the weight forward or to a side, the drop into a sweep, the rise of a jump (model units: 16 a block, y down, front -z)
		float dx = f[SIDE] * 16.0F * k, dy = -f[DY] * 16.0F * k, dz = -f[FWD] * 16.0F * k;
		for (ModelPart part : new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg}) {
			part.x += dx;
			part.y += dy;
			part.z += dz;
		}
	}

	/** Turns parts about the hips: where they hang from and how they lie. */
	private static void turn(ModelPart[] parts, Quaternionf q) {
		Vector3f at = new Vector3f();
		Vector3f angles = new Vector3f();
		for (ModelPart part : parts) {
			q.transform(at.set(part.x, part.y, part.z).sub(HIP)).add(HIP);
			part.x = at.x;
			part.y = at.y;
			part.z = at.z;
			new Quaternionf(q).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(angles);
			part.xRot = angles.x;
			part.yRot = angles.y;
			part.zRot = angles.z;
		}
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	private static void place(ModelPart part, float x, float y, float z, float amount) {
		part.x = Mth.lerp(amount, part.x, x);
		part.y = Mth.lerp(amount, part.y, y);
		part.z = Mth.lerp(amount, part.z, z);
	}
}
