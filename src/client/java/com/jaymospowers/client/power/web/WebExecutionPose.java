package com.jaymospowers.client.power.web;

import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.power.web.WebExecutions;
import com.jaymospowers.power.web.WebExecutions.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.8) How the symbiote's executions look ({@link WebExecutions}), for everyone, the way FinisherPose draws a
 * weapon's: the body turned to face whoever lies downed, a step in toward them if they lie further off than the move
 * reaches, and the move played key by key ({@link #KEYS}, one table per {@link Move}, its ticks matching the server's
 * length and beats). Each key sets how far the body sinks (onto a knee, into a crouch; below the ground for a leap),
 * how far it bends forward from the hips and turns, both arms, both legs (as the world sees them, so they stay planted
 * under the bend), the head, a lunge forward (a knee drop landing on them, a stomp), whether the teeth are bared
 * ({@code mouth}: WebHulk.teeth opens the jaw for the bite) and how far the tendrils reach ({@code tendril}:
 * WebTendrilLayer.execution draws them from the shoulder roots into the body, {@link #tendrilEnds}). Between keys it
 * eases, and the whole pose eases in and out ({@link WebEase}) from whatever the body was doing (the hunch, the brute
 * walk, vanilla), so nothing snaps. Your own execution is watched from behind (FinisherPose's cinematic view covers it).
 */
public final class WebExecutionPose {
	// ===== the knobs ================================================================================================
	/** Ticks the pose takes to blend in and out. */
	static final float BLEND_TICKS = 4.0F;
	/** How far ahead the moves reach (blocks): someone lying further off is stepped in toward, up to {@link #STEP_MAX}. */
	static final float REACH = 1.3F, STEP_MAX = 1.4F;
	/** The hips over the feet (blocks, before your size): the bend and the turn are about them. */
	static final float HIP = 0.75F;
	/** The head's turn after the body is kept within this (degrees). */
	static final float HEAD_TURN = 50.0F;
	// ================================================================================================================

	/**
	 * A key: at tick {@code t}; sink (blocks), bend and turn (degrees), the arms' x y z (radians), the legs, the head
	 * (radians), the lunge ahead (blocks), the teeth bared (0-1), the tendrils' reach (0-1).
	 */
	public record Key(float t, float sink, float pitch, float twist, float rax, float ray, float raz, float lax, float lay, float laz,
		float rightLeg, float leftLeg, float head, float lunge, float mouth, float tendril) {
		Key lunge(float ahead) {
			return new Key(t, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, ahead, mouth, tendril);
		}

		Key mouth(float open) {
			return new Key(t, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, lunge, open, tendril);
		}

		Key tendril(float reach) {
			return new Key(t, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, lunge, mouth, reach);
		}

		Key at(float when) {
			return new Key(when, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, lunge, mouth, tendril);
		}

		Key turned(float when, float turn) {
			return new Key(when, sink, pitch, turn, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, lunge, mouth, tendril);
		}
	}

	private static Key k(float t, float sink, float pitch, float twist, float rax, float ray, float raz, float lax, float lay, float laz,
		float rightLeg, float leftLeg, float head) {
		return new Key(t, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, 0.0F, 0.0F, 0.0F);
	}

	/** Standing at the start and the end of every move: arms low, looking down at them. */
	private static Key rest(float t) {
		return k(t, 0.0F, 0.0F, 0.0F, -0.25F, 0.0F, 0.05F, -0.25F, 0.0F, -0.05F, 0.0F, 0.0F, 0.35F);
	}

	/** Bent double over them, arms reaching down to take hold. */
	private static Key stoop(float t) {
		return k(t, 0.35F, 45, 0, -0.9F, -0.3F, 0.1F, -0.9F, 0.3F, -0.1F, -0.5F, 0.4F, 0.6F);
	}

	/** Down on one knee at them. */
	private static Key kneel(float t, float pitch, float rax, float ray, float raz, float lax, float lay, float laz, float head) {
		return k(t, 0.45F, pitch, 0, rax, ray, raz, lax, lay, laz, -1.15F, 1.35F, head);
	}

	/** Astride them on both knees, lunged onto them. */
	private static Key astride(float t, float pitch, float rax, float ray, float raz, float lax, float lay, float laz, float head) {
		return k(t, 0.5F, pitch, 0, rax, ray, raz, lax, lay, laz, -1.25F, -1.25F, head).lunge(0.5F);
	}

	/** Every move's keys. */
	static final Map<Move, Key[]> KEYS = new EnumMap<>(Move.class);

	static {
		// ---- the thin suit ----------------------------------------------------------------------------------------------
		// Knee Drop: a crouch, a leap, both knees landing on the chest, the head taken in both hands and wrenched round
		KEYS.put(Move.KNEE_TWIST, new Key[] {
			rest(0),
			k(6, 0.25F, 22, 0, -0.9F, 0.1F, 0.1F, -0.9F, -0.1F, -0.1F, -0.4F, 0.3F, 0.55F),
			k(11, -0.55F, 10, 0, -2.4F, 0.1F, 0.3F, -2.4F, -0.1F, -0.3F, -1.3F, -1.3F, 0.5F).lunge(0.25F),
			astride(16, 38, -0.9F, -0.2F, 0.1F, -0.9F, 0.2F, -0.1F, 0.7F),
			astride(22, 40, -0.6F, -0.4F, 0.05F, -0.6F, 0.4F, -0.05F, 0.7F),
			astride(30, 42, -0.55F, -0.3F, 0, -0.5F, 0.35F, 0, 0.72F).turned(30, -10),
			astride(36, 42, -0.3F, -0.65F, 0.2F, -0.65F, 0.6F, -0.1F, 0.75F).turned(36, 22),
			astride(40, 36, -0.4F, -0.3F, 0.1F, -0.4F, 0.3F, -0.1F, 0.6F).turned(40, 8),
			rest(44)});
		// Heel Stomp: the right leg raised high, arms out for balance, the heel brought down on the head, raised, and down again
		Key heelUp = k(0, -0.05F, -8, 0, -0.6F, -0.5F, 0.3F, -0.6F, 0.5F, -0.3F, -1.6F, 0.1F, 0.55F);
		Key heelDown = k(0, 0.12F, 20, 0, -0.3F, -0.2F, 0.2F, -0.3F, 0.2F, -0.2F, 0.05F, 0.1F, 0.7F).lunge(0.4F);
		KEYS.put(Move.HEEL_STOMP, new Key[] {
			rest(0),
			heelUp.at(6), heelDown.at(12), heelDown.at(14),
			heelUp.at(17), k(22, 0.2F, 28, 0, -0.25F, -0.2F, 0.2F, -0.25F, 0.2F, -0.2F, 0.05F, 0.15F, 0.75F).lunge(0.4F),
			heelDown.at(26),
			rest(30)});
		// Lift and Slam: stooped to take the collar in the right hand, hauled up and held at arm's length, then slammed down
		KEYS.put(Move.LIFT_SLAM, new Key[] {
			rest(0),
			stoop(6),
			k(12, 0.1F, 10, -6, -1.6F, -0.25F, 0.1F, -0.6F, 0.2F, -0.1F, -0.2F, 0.2F, 0.45F),
			k(18, 0.0F, -10, -10, -2.2F, -0.2F, 0.15F, -0.5F, 0.2F, -0.1F, -0.1F, 0.1F, 0.0F),
			k(26, 0.0F, -12, -8, -2.3F, -0.2F, 0.15F, -0.5F, 0.3F, -0.1F, -0.1F, 0.1F, -0.05F),
			k(30, 0.3F, 42, 6, -0.4F, -0.3F, 0.05F, -0.8F, 0.3F, -0.1F, -0.8F, 0.65F, 0.65F).lunge(0.3F),
			k(34, 0.28F, 38, 4, -0.5F, -0.3F, 0.05F, -0.7F, 0.3F, -0.1F, -0.8F, 0.65F, 0.6F).lunge(0.3F),
			rest(40)});
		// Choke-out: kneeling behind them, the right arm locked round the neck and the left clamped over it, tightened
		// twice, leaning back on it, until the neck goes
		Key choke = kneel(0, 15, -1.3F, -0.9F, 0.05F, -1.1F, 0.5F, -0.1F, 0.5F);
		Key tight = kneel(0, -6, -1.45F, -0.95F, 0.1F, -1.25F, 0.55F, -0.15F, 0.35F);
		KEYS.put(Move.CHOKE_OUT, new Key[] {
			rest(0),
			kneel(7, 30, -0.7F, -0.3F, 0, -0.7F, 0.3F, 0, 0.6F),
			choke.at(13),
			tight.turned(22, 6), choke.turned(28, 2),
			tight.turned(42, -6), choke.turned(48, -2),
			tight.turned(56, -8),
			kneel(60, -12, -1.5F, -1.0F, 0.15F, -1.3F, 0.6F, -0.2F, 0.3F).turned(60, 16),
			kneel(66, 0, -1.3F, -0.8F, 0.05F, -1.0F, 0.4F, -0.1F, 0.45F).turned(66, 10),
			rest(70)});
		// Mounted Pummel: astride them, right, left, right, left into the face, then both fists raised and brought down
		Key rightIn = astride(0, 34, -1.45F, -0.25F, 0.05F, -0.5F, 0.3F, -0.1F, 0.7F).turned(0, 8);
		Key leftIn = astride(0, 34, -0.5F, -0.3F, 0.1F, -1.45F, 0.25F, -0.05F, 0.7F).turned(0, -8);
		KEYS.put(Move.PUMMEL, new Key[] {
			rest(0),
			k(4, -0.3F, 15, 0, -1.6F, 0.1F, 0.3F, -1.6F, -0.1F, -0.3F, -1.2F, -1.2F, 0.5F).lunge(0.2F),
			astride(8, 30, -0.9F, -0.2F, 0.1F, -0.9F, 0.2F, -0.1F, 0.65F),
			rightIn.at(12), leftIn.at(17), rightIn.at(22), leftIn.at(27),
			astride(32, 12, -2.3F, -0.2F, 0.3F, -2.3F, 0.2F, -0.3F, 0.5F),
			astride(38, 44, -1.25F, -0.25F, 0.05F, -1.25F, 0.25F, -0.05F, 0.75F),
			astride(42, 40, -1.1F, -0.25F, 0.05F, -1.1F, 0.25F, -0.05F, 0.7F),
			rest(46)});
		// Throat Punch: down on a knee, the right fist drawn back behind the hip with a turn, and driven straight in
		KEYS.put(Move.THROAT_PUNCH, new Key[] {
			rest(0),
			kneel(6, 25, -0.6F, -0.2F, 0.05F, -0.6F, 0.2F, -0.05F, 0.6F),
			kneel(11, 15, 0.55F, -0.1F, 0.15F, -0.9F, 0.4F, -0.1F, 0.55F).turned(11, -28),
			kneel(14, 18, 0.4F, -0.1F, 0.15F, -0.9F, 0.4F, -0.1F, 0.55F).turned(14, -30),
			kneel(16, 40, -1.55F, -0.15F, 0.05F, -0.4F, 0.3F, -0.1F, 0.7F).turned(16, 18),
			kneel(21, 38, -1.45F, -0.15F, 0.05F, -0.4F, 0.3F, -0.1F, 0.7F).turned(21, 14),
			rest(28)});
		// ---- Living Armor and the big suit ------------------------------------------------------------------------------
		// Torn in Half: stooped to take them up, held in front, a grip taken high and low, strained at, and pulled apart
		KEYS.put(Move.RIP_HALF, new Key[] {
			rest(0),
			stoop(4),
			k(14, 0.0F, -8, 0, -1.5F, -0.35F, 0.1F, -1.5F, 0.35F, -0.1F, -0.1F, 0.1F, 0.2F),
			k(16, 0.0F, -8, 0, -2.0F, -0.3F, 0.15F, -1.0F, 0.3F, -0.15F, -0.1F, 0.1F, 0.15F).lunge(0.1F),
			k(22, 0.0F, -14, 10, -2.15F, -0.3F, 0.4F, -0.85F, 0.3F, -0.4F, -0.15F, 0.15F, 0.1F),
			k(28, 0.0F, -15, -10, -2.3F, -0.3F, 0.5F, -0.75F, 0.3F, -0.5F, -0.15F, 0.15F, 0.1F),
			k(33, 0.0F, -16, 8, -2.4F, -0.3F, 0.6F, -0.7F, 0.3F, -0.6F, -0.15F, 0.15F, 0.1F),
			k(36, 0.0F, -22, 0, -2.6F, -0.8F, 0.95F, -0.6F, 0.8F, -0.95F, -0.2F, 0.2F, -0.1F),
			k(42, 0.0F, -12, 0, -1.4F, -0.5F, 0.6F, -0.5F, 0.5F, -0.6F, -0.1F, 0.1F, 0.1F),
			rest(50)});
		// Neck Bite: stooped to take them up to the face in both hands, the jaw opening, the head lunging down into the neck
		KEYS.put(Move.NECK_BITE, new Key[] {
			rest(0),
			stoop(4),
			k(12, 0.0F, -5, 0, -1.6F, -0.4F, 0.1F, -1.6F, 0.4F, -0.1F, -0.1F, 0.1F, -0.2F).mouth(1.0F),
			k(20, 0.0F, 6, 0, -1.7F, -0.4F, 0.1F, -1.7F, 0.4F, -0.1F, -0.1F, 0.1F, 0.1F).mouth(1.0F),
			k(26, 0.0F, 15, 4, -1.75F, -0.4F, 0.1F, -1.75F, 0.4F, -0.1F, -0.1F, 0.1F, 0.5F).mouth(1.0F),
			k(28, 0.02F, 22, 8, -1.8F, -0.4F, 0.1F, -1.8F, 0.4F, -0.1F, -0.1F, 0.1F, 0.7F).mouth(1.0F),
			k(32, 0.02F, 18, -12, -1.75F, -0.4F, 0.1F, -1.75F, 0.4F, -0.1F, -0.1F, 0.1F, 0.4F).mouth(0.8F),
			k(36, 0.1F, 25, -4, -1.0F, -0.3F, 0.1F, -1.0F, 0.3F, -0.1F, -0.3F, 0.3F, 0.5F),
			rest(40)});
		// Ground Pound: both arms raised high, brought down on the chest, raised again, and down with the whole body behind them
		Key poundUp = k(0, -0.05F, -12, 0, -2.9F, -0.1F, 0.3F, -2.9F, 0.1F, -0.3F, -0.3F, 0.25F, 0.3F);
		Key poundDown = k(0, 0.3F, 45, 0, -0.5F, -0.3F, 0, -0.5F, 0.3F, 0, -0.8F, 0.65F, 0.65F).lunge(0.3F);
		KEYS.put(Move.GROUND_POUND, new Key[] {
			rest(0),
			poundUp.at(8), poundDown.at(14), poundDown.at(16),
			k(22, -0.08F, -16, 0, -3.05F, -0.1F, 0.3F, -3.05F, 0.1F, -0.3F, -0.35F, 0.3F, 0.3F),
			k(30, 0.45F, 55, 0, -0.35F, -0.3F, 0, -0.35F, 0.3F, 0, -0.95F, 0.8F, 0.7F).lunge(0.3F),
			k(34, 0.42F, 52, 0, -0.4F, -0.3F, 0, -0.4F, 0.3F, 0, -0.95F, 0.8F, 0.68F).lunge(0.3F),
			rest(38)});
		// Overhead Throw: stooped to take them up, heaved up overhead in both hands, leaning back under the weight, and hurled down ahead
		KEYS.put(Move.OVERHEAD_THROW, new Key[] {
			rest(0),
			stoop(6),
			k(14, 0.05F, 5, 0, -2.2F, -0.3F, 0.2F, -2.2F, 0.3F, -0.2F, -0.3F, 0.3F, 0.1F),
			k(22, -0.05F, -18, 0, -3.0F, -0.3F, 0.25F, -3.0F, 0.3F, -0.25F, -0.25F, 0.3F, -0.3F),
			k(30, -0.05F, -22, -10, -3.05F, -0.3F, 0.3F, -3.05F, 0.3F, -0.3F, -0.3F, 0.35F, -0.35F),
			k(34, 0.25F, 48, 6, -1.0F, -0.3F, 0.1F, -1.0F, 0.3F, -0.1F, -0.8F, 0.6F, 0.6F).lunge(0.4F),
			k(38, 0.22F, 40, 4, -0.8F, -0.3F, 0.1F, -0.8F, 0.3F, -0.1F, -0.7F, 0.55F, 0.6F).lunge(0.4F),
			rest(46)});
		// Skull Crush: down on a knee, the head taken between the palms, squeezed, eased, squeezed, and cracked
		Key palms = kneel(0, 32, -0.6F, -0.45F, 0.15F, -0.6F, 0.45F, -0.15F, 0.6F);
		Key squeeze = kneel(0, 38, -0.55F, -0.25F, 0.1F, -0.55F, 0.25F, -0.1F, 0.65F);
		KEYS.put(Move.SKULL_CRUSH, new Key[] {
			rest(0),
			kneel(8, 30, -0.7F, -0.3F, 0.05F, -0.7F, 0.3F, -0.05F, 0.6F),
			palms.at(12), squeeze.at(18), squeeze.at(21), palms.at(26),
			kneel(34, 42, -0.5F, -0.15F, 0.1F, -0.5F, 0.15F, -0.1F, 0.7F), kneel(37, 42, -0.5F, -0.15F, 0.1F, -0.5F, 0.15F, -0.1F, 0.7F),
			palms.at(41),
			kneel(46, 46, -0.45F, -0.05F, 0.1F, -0.45F, 0.05F, -0.1F, 0.75F).turned(46, 6),
			kneel(50, 38, -0.4F, -0.3F, 0.1F, -0.4F, 0.3F, -0.1F, 0.6F),
			rest(54)});
		// Quake Stomp: a crouch, a hop with the knees drawn up, the right knee raised high in the air, and the foot
		// brought down on them with everything, arms flung out as the ground cracks
		KEYS.put(Move.QUAKE_STOMP, new Key[] {
			rest(0),
			k(8, 0.25F, 15, 0, -0.7F, 0.1F, 0.2F, -0.7F, -0.1F, -0.2F, -0.4F, 0.3F, 0.5F),
			k(13, -0.6F, 5, 0, -1.8F, 0.1F, 0.35F, -1.8F, -0.1F, -0.35F, -1.0F, -1.0F, 0.4F),
			k(17, -0.5F, -5, 0, -1.6F, 0.1F, 0.4F, -1.6F, -0.1F, -0.4F, -1.5F, -0.2F, 0.45F).lunge(0.2F),
			k(22, 0.1F, 25, 0, -0.5F, -0.6F, 0.45F, -0.5F, 0.6F, -0.45F, 0.0F, -0.4F, 0.7F).lunge(0.5F),
			k(26, 0.1F, 24, 0, -0.5F, -0.55F, 0.4F, -0.5F, 0.55F, -0.4F, 0.0F, -0.4F, 0.7F).lunge(0.5F),
			k(31, 0.05F, 12, 0, -0.35F, -0.2F, 0.15F, -0.35F, 0.2F, -0.15F, 0.0F, -0.2F, 0.5F).lunge(0.3F),
			rest(36)});
		// ---- the tendrils, over the thin suit ---------------------------------------------------------------------------
		// Skewer: standing tall, the right shoulder rearing up as its tendril grows, driven down through the chest, twisted
		KEYS.put(Move.TENDRIL_SKEWER, new Key[] {
			rest(0),
			k(6, 0.0F, -8, 0, 0.2F, 0, 0.5F, -0.3F, 0, -0.05F, 0, 0, 0.3F).tendril(0.0F),
			k(12, -0.02F, -12, -6, 0.3F, 0, 0.6F, -0.3F, 0, -0.05F, 0, 0, 0.25F).tendril(0.6F),
			k(16, 0.03F, 12, -10, -0.2F, 0, 0.4F, -0.3F, 0, -0.05F, -0.1F, 0.1F, 0.55F).lunge(0.2F).tendril(1.0F),
			k(22, 0.03F, 14, 8, -0.2F, 0, 0.4F, -0.3F, 0, -0.05F, -0.1F, 0.1F, 0.55F).lunge(0.2F).tendril(1.0F),
			k(26, 0.03F, 16, 18, -0.25F, 0, 0.45F, -0.3F, 0, -0.05F, -0.1F, 0.1F, 0.6F).lunge(0.2F).tendril(1.0F),
			k(30, 0.0F, 6, 8, -0.2F, 0, 0.25F, -0.3F, 0, -0.05F, 0, 0, 0.45F).tendril(0.3F),
			rest(34)});
		// Tendril Snap: both shoulders rear up as two tendrils wind round the neck, hoist them off the ground, hold them
		// dangling (palms open, looking up at them), and snap it with a wrench
		KEYS.put(Move.TENDRIL_SNAP, new Key[] {
			rest(0),
			k(6, 0.0F, -6, 0, 0.1F, 0, 0.4F, 0.1F, 0, -0.4F, 0, 0, 0.3F).tendril(0.4F),
			k(12, 0.0F, -8, 0, -0.5F, -0.5F, 0.4F, -0.5F, 0.5F, -0.4F, 0, 0, 0.1F).tendril(1.0F),
			k(28, 0.0F, -12, 0, -0.6F, -0.5F, 0.45F, -0.6F, 0.5F, -0.45F, -0.1F, 0.1F, -0.3F).tendril(1.0F),
			k(32, 0.0F, -12, -8, -0.6F, -0.5F, 0.45F, -0.6F, 0.5F, -0.45F, -0.1F, 0.1F, -0.3F).tendril(1.0F),
			k(34, 0.0F, -16, 20, -0.7F, -0.6F, 0.5F, -0.7F, 0.6F, -0.5F, -0.15F, 0.15F, -0.25F).tendril(1.0F),
			k(38, 0.0F, 8, 6, -0.5F, -0.3F, 0.3F, -0.5F, 0.3F, -0.3F, -0.1F, 0.1F, 0.3F).tendril(0.6F),
			rest(44)});
		// Drag In: the tendrils hook them, the body leans back and hauls them in across the ground, and the knee comes up
		KEYS.put(Move.DRAG_KNEE, new Key[] {
			rest(0),
			k(6, 0.0F, 10, 0, -0.5F, 0.2F, 0.3F, -0.5F, -0.2F, -0.3F, -0.3F, 0.3F, 0.55F).tendril(0.5F),
			k(10, 0.0F, 10, 0, -0.3F, 0.2F, 0.35F, -0.3F, -0.2F, -0.35F, -0.3F, 0.3F, 0.55F).tendril(1.0F),
			k(16, 0.05F, -14, 0, 0.3F, 0.2F, 0.4F, 0.3F, -0.2F, -0.4F, -0.4F, 0.4F, 0.4F).tendril(1.0F),
			k(24, 0.05F, -10, 0, 0.2F, 0.1F, 0.35F, 0.2F, -0.1F, -0.35F, -0.3F, 0.3F, 0.5F).tendril(1.0F),
			k(28, -0.1F, 24, 4, -0.9F, -0.3F, 0.2F, -0.9F, 0.3F, -0.2F, -1.7F, 0.1F, 0.6F).lunge(0.1F).tendril(0.6F),
			k(31, -0.08F, 22, 4, -0.8F, -0.3F, 0.2F, -0.8F, 0.3F, -0.2F, -1.6F, 0.1F, 0.6F).lunge(0.1F).tendril(0.3F),
			k(35, 0.0F, 8, 0, -0.4F, -0.1F, 0.1F, -0.4F, 0.1F, -0.1F, -0.3F, 0.1F, 0.45F),
			rest(40)});
		// ---- the tendrils, over the armour ------------------------------------------------------------------------------
		// Quartered: the arms spread as four tendrils take the limbs, hoist them, strain this way and that, and tear
		KEYS.put(Move.QUARTER, new Key[] {
			rest(0),
			k(8, 0.0F, 4, 0, -0.9F, -0.5F, 0.3F, -0.9F, 0.5F, -0.3F, -0.1F, 0.1F, 0.45F).tendril(0.5F),
			k(16, 0.0F, 0, 0, -1.0F, -0.5F, 0.4F, -1.0F, 0.5F, -0.4F, -0.1F, 0.1F, 0.4F).tendril(1.0F),
			k(32, 0.0F, -14, 0, -1.2F, -0.5F, 0.6F, -1.2F, 0.5F, -0.6F, -0.2F, 0.2F, -0.1F).tendril(1.0F),
			k(37, 0.0F, -16, 8, -1.0F, -0.4F, 0.8F, -1.0F, 0.4F, -0.8F, -0.2F, 0.2F, -0.1F).tendril(1.0F),
			k(41, 0.0F, -18, -8, -0.9F, -0.4F, 0.9F, -0.9F, 0.4F, -0.9F, -0.2F, 0.2F, -0.1F).tendril(1.0F),
			k(44, 0.0F, -24, 0, -1.6F, -0.6F, 1.2F, -1.6F, 0.6F, -1.2F, -0.25F, 0.25F, -0.3F).tendril(1.0F),
			k(50, 0.0F, -10, 0, -0.9F, -0.3F, 0.6F, -0.9F, 0.3F, -0.6F, -0.1F, 0.1F, 0.2F).tendril(0.4F),
			rest(56)});
		// Impale and Slam: the right shoulder's tendril through the chest, them lifted on it, looked up at, and slammed down
		KEYS.put(Move.IMPALE_SLAM, new Key[] {
			rest(0),
			k(6, 0.0F, -6, 0, 0.2F, 0, 0.5F, -0.3F, 0, -0.05F, 0, 0, 0.3F).tendril(0.4F),
			k(14, 0.03F, 10, -8, -0.2F, 0, 0.45F, -0.3F, 0, -0.05F, -0.1F, 0.1F, 0.55F).lunge(0.2F).tendril(1.0F),
			k(26, 0.0F, -16, -4, -0.3F, 0, 0.55F, -0.4F, 0.1F, -0.1F, -0.15F, 0.15F, -0.4F).tendril(1.0F),
			k(32, 0.0F, -16, -4, -0.3F, 0, 0.55F, -0.4F, 0.1F, -0.1F, -0.15F, 0.15F, -0.4F).tendril(1.0F),
			k(36, 0.3F, 45, 6, -0.5F, -0.2F, 0.3F, -0.6F, 0.3F, -0.1F, -0.8F, 0.65F, 0.65F).lunge(0.3F).tendril(1.0F),
			k(40, 0.28F, 40, 4, -0.5F, -0.2F, 0.3F, -0.6F, 0.3F, -0.1F, -0.8F, 0.65F, 0.6F).lunge(0.3F).tendril(0.6F),
			rest(46)});
		// Wrap and Crush: the tendrils wind round the whole body as the hands close, it's lifted a little, squeezed,
		// eased, and crushed as the hands clench
		KEYS.put(Move.WRAP_CRUSH, new Key[] {
			rest(0),
			k(8, 0.0F, 8, 0, -0.7F, -0.3F, 0.2F, -0.7F, 0.3F, -0.2F, -0.1F, 0.1F, 0.5F).tendril(0.5F),
			k(18, 0.0F, 6, 0, -0.8F, -0.3F, 0.25F, -0.8F, 0.3F, -0.25F, -0.1F, 0.1F, 0.45F).tendril(1.0F),
			k(26, 0.0F, -6, 0, -0.85F, -0.25F, 0.25F, -0.85F, 0.25F, -0.25F, -0.1F, 0.1F, 0.3F).tendril(1.0F),
			k(36, 0.0F, 0, 6, -0.9F, -0.1F, 0.2F, -0.9F, 0.1F, -0.2F, -0.1F, 0.1F, 0.4F).tendril(1.0F),
			k(42, 0.0F, -4, 0, -0.85F, -0.25F, 0.25F, -0.85F, 0.25F, -0.25F, -0.1F, 0.1F, 0.35F).tendril(1.0F),
			k(52, 0.1F, 10, -6, -1.1F, -0.05F, 0.1F, -1.1F, 0.05F, -0.1F, -0.2F, 0.2F, 0.5F).tendril(1.0F),
			k(56, 0.05F, 4, 0, -0.8F, -0.2F, 0.15F, -0.8F, 0.2F, -0.15F, -0.1F, 0.1F, 0.4F).tendril(0.5F),
			rest(60)});
	}

	/**
	 * What's drawn: how far into the pose ({@code in}, 0-1), the move, its pose at this moment, how far it steps in
	 * (blocks), the body's middle in the model's root space (pixels, +Y down; null if it's gone) with its width and
	 * height in pixels (for the tendrils), and the time (ticks, for trembles).
	 */
	public record Run(float in, Move move, Key key, float step, @Nullable Vec3 target, float targetWidth, float targetHeight, float time) {
	}

	public static final RenderStateDataKey<Run> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_execution");

	/** Entity id → how far into the pose, eased. */
	private static final WebEase IN = new WebEase();
	/** Entity id → the last run seen, so the pose can ease out from it. */
	private static final Map<Integer, Run> LAST = new java.util.HashMap<>();

	private WebExecutionPose() {
	}

	/** From the renderer's extractRenderState: the pose now, and the body turned to face them. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !(state instanceof AvatarRenderState avatar)) {
			state.setData(KEY, null);
			return;
		}
		WebExecutions.Execution execution = WebExecutions.of(player);
		Move move = execution == null ? null : execution.type();
		Key[] keys = move == null ? null : KEYS.get(move);
		float t = execution == null ? 0.0F : entity.level().getGameTime() - execution.start() + partialTicks;
		boolean on = keys != null && t >= 0.0F && t < move.length;
		float in = WebEase.smooth(IN.to(entity.getId(), on ? 1.0F : 0.0F, BLEND_TICKS));
		if (in <= 0.001F) {
			LAST.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		if (!on) {
			// easing out of the last pose seen
			Run last = LAST.get(entity.getId());
			state.setData(KEY, last == null ? null : new Run(in, last.move(), last.key(), last.step(), null, 0.0F, 0.0F, last.time()));
			return;
		}
		if (LAST.size() > 64) {
			LAST.keySet().removeIf(id -> id != entity.getId() && entity.level().getEntity(id) == null);
		}
		float step = 0.0F;
		Vec3 target = null;
		float width = 0.0F, height = 0.0F;
		Entity body = entity.level().getEntity(execution.target());
		if (body != null) {
			// the moves done at the head kneel to it, not the middle
			Vec3 at = atHead(move) && body instanceof LivingEntity living ? Finishers.headAt(living) : body.getPosition(partialTicks);
			double dx = at.x - entity.getX(), dz = at.z - entity.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float head = avatar.bodyRot + avatar.yRot;
			avatar.bodyRot = yaw;
			avatar.yRot = Mth.clamp(Mth.wrapDegrees(head - yaw), -HEAD_TURN, HEAD_TURN);
			// (a step in, over the first few ticks, if they lie further off than the move reaches)
			float ease = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
			step = Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) - REACH, 0.0F, STEP_MAX) * ease * ease * (3.0F - 2.0F * ease);
			// its middle in the model's root space: the renderer turns the body to face its way, flips the model and lifts it 1.501
			Vec3 d = body.getPosition(partialTicks).add(0.0, body.getBbHeight() * 0.5, 0.0).subtract(entity.getPosition(partialTicks));
			float scale = Math.max(avatar.scale, 0.01F);
			Vector3f q = Axis.YP.rotationDegrees(-(180.0F - yaw)).transform(new Vector3f((float) d.x / scale, (float) d.y / scale, (float) d.z / scale));
			target = new Vec3(-q.x, 1.501F - q.y, q.z).scale(16.0);
			width = body.getBbWidth() / scale * 16.0F;
			height = body.getBbHeight() / scale * 16.0F;
		}
		avatar.walkAnimationSpeed = 0.0F;
		Run run = new Run(in, move, sample(keys, t), step, target, width, height, entity.level().getGameTime() + partialTicks);
		LAST.put(entity.getId(), run);
		state.setData(KEY, run);
	}

	/** The moves done kneeling at the head. */
	private static boolean atHead(Move move) {
		return switch (move) {
			case KNEE_TWIST, HEEL_STOMP, CHOKE_OUT, PUMMEL, THROAT_PUNCH, SKULL_CRUSH -> true;
			default -> false;
		};
	}

	/** The move's pose at tick {@code t}, eased between its keys. */
	private static Key sample(Key[] keys, float t) {
		if (t <= keys[0].t()) {
			return keys[0];
		}
		for (int i = 0; i < keys.length - 1; i++) {
			Key a = keys[i], b = keys[i + 1];
			if (t < b.t()) {
				float p = (t - a.t()) / Math.max(0.001F, b.t() - a.t());
				p = p * p * (3.0F - 2.0F * p);
				return new Key(t, Mth.lerp(p, a.sink(), b.sink()), Mth.lerp(p, a.pitch(), b.pitch()), Mth.lerp(p, a.twist(), b.twist()),
					Mth.lerp(p, a.rax(), b.rax()), Mth.lerp(p, a.ray(), b.ray()), Mth.lerp(p, a.raz(), b.raz()),
					Mth.lerp(p, a.lax(), b.lax()), Mth.lerp(p, a.lay(), b.lay()), Mth.lerp(p, a.laz(), b.laz()),
					Mth.lerp(p, a.rightLeg(), b.rightLeg()), Mth.lerp(p, a.leftLeg(), b.leftLeg()), Mth.lerp(p, a.head(), b.head()),
					Mth.lerp(p, a.lunge(), b.lunge()), Mth.lerp(p, a.mouth(), b.mouth()), Mth.lerp(p, a.tendril(), b.tendril()));
			}
		}
		return keys[keys.length - 1];
	}

	/** The bite is closing on them: the teeth bare (WebHulk.teeth). */
	static boolean biting(AvatarRenderState state) {
		Run run = state.getData(KEY);
		return run != null && run.key().mouth() > 0.5F;
	}

	/** After the body faces its way: the step and lunge in, the sink, the turn and the bend from the hips. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Run run = state.getData(KEY);
		if (run == null || state.deathTime > 0.0F) {
			return;
		}
		float w = run.in();
		Key key = run.key();
		float hip = HIP * Math.max(state.scale, 0.01F);
		// (after the body's turn, forward is -Z)
		poseStack.translate(0.0F, -key.sink() * w, -(run.step() + key.lunge()) * w);
		poseStack.translate(0.0F, hip, 0.0F);
		poseStack.rotateDegrees(Axis.YP, key.twist() * w);
		poseStack.rotateDegrees(Axis.XP, -key.pitch() * w);
		poseStack.translate(0.0F, -hip, 0.0F);
	}

	/** After the model animates: the arms, legs and head into the move, eased from whatever the body was doing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Run run = avatar.getData(KEY);
		if (run == null || avatar.getData(WebArms.KEY) != null) {
			return;
		}
		float[] was = WebFeral.snapshot(h);
		pose(h, avatar, run.key());
		WebFeral.blend(h, was, run.in());
	}

	/** The move outright: set from the render state alone, so applying it twice changes nothing. */
	private static void pose(HumanoidModel<?> h, AvatarRenderState avatar, Key key) {
		boolean slim = avatar.skin != null && avatar.skin.model() == PlayerModelType.SLIM;
		float armY = slim ? 2.5F : 2.0F;
		// upright at the hip (the bend is the whole body's, in rotate), from wherever the hunch had everything
		WebFeral.place(h.body, 0.0F, 0.0F, 0.0F);
		WebFeral.place(h.head, 0.0F, 0.0F, 0.0F);
		WebFeral.place(h.rightArm, -5.0F, armY, 0.0F);
		WebFeral.place(h.leftArm, 5.0F, armY, 0.0F);
		h.body.xRot = 0.0F;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		h.rightArm.xRot = key.rax();
		h.rightArm.yRot = key.ray();
		h.rightArm.zRot = key.raz();
		h.leftArm.xRot = key.lax();
		h.leftArm.yRot = key.lay();
		h.leftArm.zRot = key.laz();
		// the legs as the world sees them, so they stay planted under the bend
		float bend = key.pitch() * Mth.DEG_TO_RAD;
		h.rightLeg.xRot = key.rightLeg() - bend;
		h.leftLeg.xRot = key.leftLeg() - bend;
		h.rightLeg.yRot = 0.08F;
		h.leftLeg.yRot = -0.08F;
		h.rightLeg.zRot = 0.0F;
		h.leftLeg.zRot = 0.0F;
		h.head.xRot = key.head();
		h.head.yRot = avatar.yRot * Mth.DEG_TO_RAD;
		h.head.zRot = 0.0F;
		h.root().y = 0.0F;
	}

	// ---- the tendrils (drawn by WebTendrilLayer.execution) ------------------------------------------------------------

	/** Which of the layer's shoulder roots the move's tendrils grow from (indices into its ROOTS), none for a move without. */
	public static int[] tendrilRoots(Move move) {
		return switch (move) {
			case TENDRIL_SKEWER, IMPALE_SLAM -> new int[] {1};
			case TENDRIL_SNAP -> new int[] {0, 1};
			case DRAG_KNEE -> new int[] {2, 3};
			case QUARTER -> new int[] {0, 1, 4, 5};
			case WRAP_CRUSH -> new int[] {0, 1, 4, 5, 6, 7};
			default -> new int[0];
		};
	}

	/**
	 * Where each of the move's tendrils ends on the body (root space, pixels, one per {@link #tendrilRoots}): the chest
	 * for a skewer, the neck for the snap, the hips for the drag, the four limbs for the quartering, round the torso for
	 * the wrap. Null if the body is gone.
	 */
	public static Vec3 @Nullable [] tendrilEnds(Run run) {
		Vec3 at = run.target();
		if (at == null) {
			return null;
		}
		double w = run.targetWidth() * 0.5, h = run.targetHeight() * 0.5;
		return switch (run.move()) {
			case TENDRIL_SKEWER, IMPALE_SLAM -> new Vec3[] {at.add(0.0, -h * 0.2, 0.0)};
			case TENDRIL_SNAP -> new Vec3[] {at.add(-1.5, -h * 0.6, 0.0), at.add(1.5, -h * 0.6, 0.0)};
			case DRAG_KNEE -> new Vec3[] {at.add(-w * 0.6, h * 0.2, 0.0), at.add(w * 0.6, h * 0.2, 0.0)};
			case QUARTER -> new Vec3[] {at.add(-w * 0.9, -h * 0.5, 0.0), at.add(w * 0.9, -h * 0.5, 0.0), at.add(-w * 0.6, h * 0.7, 0.0),
				at.add(w * 0.6, h * 0.7, 0.0)};
			case WRAP_CRUSH -> new Vec3[] {at.add(-w, -h * 0.3, 0.0), at.add(w, -h * 0.3, 0.0), at.add(-w, h * 0.1, 0.0), at.add(w, h * 0.1, 0.0),
				at.add(-w * 0.7, h * 0.5, 0.0), at.add(w * 0.7, h * 0.5, 0.0)};
			default -> new Vec3[0];
		};
	}

	/** What the move's tendrils end in: 0 a spear point, 1 a hooked blade, -1 a bare tip (the ones that wind round). */
	public static int tendrilBlade(Move move) {
		return switch (move) {
			case TENDRIL_SKEWER, IMPALE_SLAM, QUARTER -> 0;
			case DRAG_KNEE -> 1;
			default -> -1;
		};
	}
}
