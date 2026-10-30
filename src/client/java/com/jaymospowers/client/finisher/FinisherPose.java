package com.jaymospowers.client.finisher;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.finisher.Finishers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.98) How a finisher looks ({@link Finishers}), for everyone: the body turned to face whoever lies downed, a step
 * in toward them if they're further off than the move reaches, and the move played key by key ({@link #KEYS}). Each key
 * sets how far the body sinks (onto a knee, into a lunge), how far it bends forward from the hips and turns (a
 * wind-up), both arms, both legs (as the world sees them, so they stay planted under the bend), the head, and the grip:
 * how far the held weapon is turned in the hand toward the arm's own line (0 as held, 90 along the arm like a thrust,
 * 180 turned point down; a turn through a full circle is a twirl). Between keys it eases, and the limbs follow on
 * {@link PoseBlend}'s springs, so a strike lands with a little follow-through.
 * <p>
 * Your own finisher is watched from behind ({@link #CINEMATIC}): first person goes to third for it and comes back after.
 */
public final class FinisherPose {
	// ===== the knobs ================================================================================================
	/** Watch your own finisher from behind (first person is put back after it). */
	static final boolean CINEMATIC = true;
	/** How far ahead the moves reach (blocks): someone lying further off is stepped in toward, up to {@link #STEP_MAX}. */
	static final float REACH = 1.3F, STEP_MAX = 1.4F;
	/** The hips over the feet (blocks): the bend and the turn are about them. */
	static final float HIP = 0.75F;
	/** How quick the move eases in and out (the weight's spring, times its usual), and how stiff the limbs follow it. */
	static final float ENTRY_SPEED = 1.6F, LIMB_STIFFNESS = 1.6F;
	// ================================================================================================================

	/** A key: at tick {@code t}; sink (blocks), bend and turn (degrees), the arms' x y z, the legs, the head (radians), the grip (degrees). */
	private record Key(float t, float sink, float pitch, float twist, float rax, float ray, float raz, float lax, float lay, float laz,
		float rightLeg, float leftLeg, float head, float grip) {
	}

	private static Key k(float t, float sink, float pitch, float twist, float rax, float ray, float raz, float lax, float lay, float laz,
		float rightLeg, float leftLeg, float head, float grip) {
		return new Key(t, sink, pitch, twist, rax, ray, raz, lax, lay, laz, rightLeg, leftLeg, head, grip);
	}

	/** Standing at the start and the end of every move: weapon low, looking down at them. */
	private static Key rest(float t, float grip) {
		return k(t, 0.0F, 0.0F, 0.0F, -0.25F, 0.0F, 0.05F, -0.25F, 0.0F, -0.05F, 0.0F, 0.0F, 0.35F, grip);
	}

	/** Every move's keys (ticks match {@link Finishers.Kind}'s length and beats). */
	static final Map<Finishers.Kind, Key[]> KEYS = new EnumMap<>(Finishers.Kind.class);

	static {
		// Run Through: both hands up high with the point turned down, driven in on one knee, twisted, wrenched free
		KEYS.put(Finishers.Kind.SWORD, new Key[] {
			rest(0, 0),
			k(6, 0.0F, 0, 0, -1.3F, -0.45F, 0, -1.3F, 0.45F, 0, 0, 0, 0.5F, 90),
			k(13, -0.04F, -8, 0, -2.9F, 0, 0.3F, -2.9F, 0, -0.3F, 0, 0, 0.45F, 160),
			k(15, -0.05F, -10, 0, -2.95F, 0, 0.3F, -2.95F, 0, -0.3F, 0, 0, 0.45F, 165),
			k(17, 0.45F, 35, 0, -0.4F, -0.4F, 0, -0.4F, 0.4F, 0, -1.0F, 1.35F, 0.6F, 90),
			k(20, 0.5F, 38, 0, -0.35F, -0.4F, 0, -0.35F, 0.4F, 0, -1.0F, 1.35F, 0.65F, 90),
			k(25, 0.5F, 38, 12, -0.3F, -0.65F, 0, -0.45F, 0.2F, 0, -1.0F, 1.35F, 0.7F, 105),
			k(28, 0.3F, 25, 0, -1.1F, -0.4F, 0, -1.1F, 0.4F, 0, -0.8F, 1.1F, 0.5F, 90),
			k(32, 0.08F, 8, 0, -0.8F, -0.1F, 0.05F, -0.5F, 0, -0.05F, -0.2F, 0.3F, 0.45F, 30),
			rest(38, 0)});
		// Headsman's Stroke: turned away with the blade over the right shoulder, then one cleave down through them
		KEYS.put(Finishers.Kind.GREATSWORD, new Key[] {
			rest(0, 0),
			k(8, 0.0F, -5, -25, -2.4F, -0.4F, 0.1F, -2.2F, 0.6F, 0, -0.15F, 0.15F, 0.3F, 0),
			k(16, -0.03F, -12, -35, -3.0F, -0.3F, 0.2F, -2.8F, 0.5F, -0.1F, -0.25F, 0.2F, 0.25F, -10),
			k(20, 0.3F, 40, 8, -0.55F, -0.35F, 0, -0.55F, 0.35F, 0, -0.9F, 0.7F, 0.55F, 70),
			k(22, 0.32F, 42, 8, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -0.9F, 0.7F, 0.6F, 70),
			k(28, 0.25F, 35, 8, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -0.9F, 0.7F, 0.55F, 70),
			rest(36, 0)});
		// Mercy Stroke: on one knee, the off hand pinning them, three quick stabs, then the blade drawn across the throat
		Key draw = k(0, 0.42F, 22, 0, -1.85F, 0.1F, 0.15F, -0.4F, 0.2F, -0.1F, -1.15F, 1.35F, 0.6F, 120);
		Key stab = k(0, 0.45F, 32, 0, -0.5F, -0.15F, 0, -0.4F, 0.2F, -0.1F, -1.15F, 1.35F, 0.65F, 90);
		KEYS.put(Finishers.Kind.DAGGER, new Key[] {
			rest(0, 0),
			k(6, 0.42F, 25, 0, -0.9F, 0, 0.1F, -0.5F, 0.2F, -0.1F, -1.15F, 1.35F, 0.6F, 90),
			at(draw, 8), at(stab, 10), at(draw, 12), at(stab, 14), at(draw, 16), at(stab, 18),
			k(22, 0.44F, 28, 0, -0.6F, -0.6F, 0.1F, -0.7F, 0.3F, -0.1F, -1.15F, 1.35F, 0.7F, 90),
			k(28, 0.44F, 28, -12, -0.6F, 0.5F, 0.1F, -0.7F, 0.3F, -0.1F, -1.15F, 1.35F, 0.7F, 90),
			k(32, 0.42F, 22, 0, -0.4F, 0, 0.1F, -0.3F, 0, -0.1F, -1.15F, 1.35F, 0.5F, 60),
			rest(40, 0)});
		// Executioner: feet planted, the axe up high behind the head, and down on the neck
		KEYS.put(Finishers.Kind.AXE, new Key[] {
			rest(0, 0),
			k(8, 0.0F, -4, 0, -2.5F, -0.2F, 0.25F, -2.5F, 0.2F, -0.25F, -0.3F, 0.25F, 0.4F, 0),
			k(17, -0.05F, -14, 0, -3.1F, 0, 0.3F, -3.1F, 0, -0.3F, -0.35F, 0.3F, 0.35F, -20),
			k(21, 0.25F, 38, 0, -0.5F, -0.35F, 0, -0.5F, 0.35F, 0, -0.7F, 0.6F, 0.55F, 65),
			k(23, 0.28F, 40, 0, -0.42F, -0.35F, 0, -0.42F, 0.35F, 0, -0.7F, 0.6F, 0.6F, 65),
			k(27, 0.28F, 40, 0, -0.42F, -0.35F, 0, -0.42F, 0.35F, 0, -0.7F, 0.6F, 0.6F, 65),
			k(31, 0.15F, 25, 0, -1.2F, -0.3F, 0, -1.2F, 0.3F, 0, -0.5F, 0.4F, 0.5F, 30),
			rest(36, 0)});
		// Skull Crusher: a one-handed blow, the weapon heaved up in both hands, and brought down with the whole body
		KEYS.put(Finishers.Kind.BLUNT, new Key[] {
			rest(0, 0),
			k(8, 0.0F, -6, -10, -2.9F, 0, 0.25F, -0.6F, 0, -0.3F, -0.2F, 0.2F, 0.35F, 0),
			k(14, 0.2F, 30, 5, -0.5F, -0.1F, 0, -0.4F, 0, -0.3F, -0.6F, 0.5F, 0.55F, 60),
			k(18, 0.15F, 25, 5, -0.6F, -0.1F, 0, -0.4F, 0, -0.3F, -0.6F, 0.5F, 0.55F, 55),
			k(26, -0.06F, -15, 0, -3.1F, 0, 0.3F, -3.1F, 0, -0.3F, -0.3F, 0.3F, 0.3F, -10),
			k(31, 0.35F, 45, 0, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -0.9F, 0.75F, 0.6F, 65),
			k(36, 0.35F, 45, 0, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -0.9F, 0.75F, 0.6F, 65),
			rest(44, 0)});
		// Pinned: the spear raised point down, driven through, leant on with all the weight, wrenched out
		KEYS.put(Finishers.Kind.SPEAR, new Key[] {
			rest(0, 0),
			k(5, 0.0F, 0, 0, -1.4F, -0.45F, 0, -1.4F, 0.45F, 0, 0, 0, 0.5F, 90),
			k(11, -0.04F, -6, 0, -2.7F, -0.1F, 0.2F, -2.5F, 0.3F, -0.2F, -0.2F, 0.2F, 0.45F, 170),
			k(14, 0.25F, 30, 0, -0.5F, -0.3F, 0, -0.5F, 0.3F, 0, -0.8F, 0.7F, 0.6F, 90),
			k(18, 0.35F, 42, 0, -0.35F, -0.3F, 0, -0.35F, 0.3F, 0, -0.9F, 0.8F, 0.65F, 90),
			k(24, 0.38F, 46, 0, -0.3F, -0.3F, 0, -0.3F, 0.3F, 0, -0.9F, 0.8F, 0.7F, 90),
			k(27, 0.2F, 20, 0, -1.0F, -0.3F, 0, -1.0F, 0.3F, 0, -0.6F, 0.5F, 0.5F, 90),
			k(31, 0.05F, 5, 0, -0.7F, -0.2F, 0, -0.6F, 0.2F, 0, -0.2F, 0.2F, 0.45F, 45),
			rest(36, 0)});
		// Reaping: turned right round with the polearm swung back low and wide, then the long arc down across them
		KEYS.put(Finishers.Kind.POLEARM, new Key[] {
			rest(0, 0),
			k(8, 0.05F, -5, -40, -1.9F, -0.2F, 0.6F, -1.5F, 0.6F, 0, -0.3F, 0.3F, 0.3F, 0),
			k(15, 0.08F, -10, -60, -2.6F, -0.2F, 0.7F, -2.2F, 0.6F, -0.1F, -0.4F, 0.4F, 0.25F, -10),
			k(20, 0.3F, 35, 25, -0.6F, -0.5F, 0, -0.6F, 0.4F, 0, -0.8F, 0.7F, 0.55F, 60),
			k(23, 0.32F, 38, 35, -0.5F, -0.6F, 0, -0.5F, 0.4F, 0, -0.8F, 0.7F, 0.6F, 60),
			k(28, 0.25F, 30, 30, -0.5F, -0.6F, 0, -0.5F, 0.4F, 0, -0.8F, 0.7F, 0.55F, 60),
			rest(34, 0)});
		// The staff twirled in front, raised upright, and its butt driven down twice
		KEYS.put(Finishers.Kind.STAFF, new Key[] {
			rest(0, 0),
			k(4, 0.0F, 0, 0, -1.3F, -0.4F, 0, -1.0F, 0.4F, 0, 0, 0, 0.4F, 0),
			k(8, 0.0F, 0, 0, -1.3F, -0.4F, 0, -1.0F, 0.4F, 0, 0, 0, 0.4F, 180),
			k(12, 0.0F, 0, 0, -1.3F, -0.4F, 0, -1.0F, 0.4F, 0, 0, 0, 0.4F, 360),
			k(15, -0.03F, -5, 0, -2.4F, -0.1F, 0.2F, -2.2F, 0.1F, -0.2F, 0, 0, 0.45F, 450),
			k(18, 0.25F, 30, 0, -0.6F, -0.3F, 0, -0.6F, 0.3F, 0, -0.7F, 0.6F, 0.6F, 450),
			k(22, 0.1F, 10, 0, -2.2F, -0.1F, 0.2F, -2.0F, 0.1F, -0.2F, -0.4F, 0.3F, 0.5F, 450),
			k(27, 0.3F, 35, 0, -0.5F, -0.3F, 0, -0.5F, 0.3F, 0, -0.8F, 0.7F, 0.6F, 450),
			k(31, 0.3F, 35, 0, -0.5F, -0.3F, 0, -0.5F, 0.3F, 0, -0.8F, 0.7F, 0.6F, 450),
			rest(38, 360)});
		// Loosed Close: the bow brought up, an arrow drawn slow to the cheek, aimed straight down, loosed
		KEYS.put(Finishers.Kind.BOW, new Key[] {
			rest(0, 0),
			k(6, 0.1F, 15, 0, -1.2F, -0.2F, 0, -1.2F, 0.6F, 0, -0.3F, 0.3F, 0.6F, 0),
			k(18, 0.12F, 25, 0, -0.4F, -0.1F, 0, -1.25F, 0.85F, 0, -0.4F, 0.35F, 0.65F, 0),
			k(21, 0.12F, 25, 0, -0.38F, -0.1F, 0, -1.3F, 0.9F, 0, -0.4F, 0.35F, 0.65F, 0),
			k(22, 0.12F, 22, 0, -0.45F, -0.1F, 0, -0.9F, 0.2F, -0.3F, -0.4F, 0.35F, 0.6F, 0),
			k(26, 0.1F, 18, 0, -0.6F, -0.1F, 0, -0.5F, 0.1F, -0.2F, -0.35F, 0.3F, 0.55F, 0),
			rest(32, 0)});
		// A bolt to the heart: the crossbow levelled at them in both hands, the shot, the kick
		KEYS.put(Finishers.Kind.CROSSBOW, new Key[] {
			rest(0, 0),
			k(6, 0.05F, 15, 0, -0.6F, -0.3F, 0, -0.7F, 0.6F, 0, -0.2F, 0.2F, 0.6F, 0),
			k(12, 0.05F, 18, 0, -0.55F, -0.3F, 0, -0.65F, 0.6F, 0, -0.2F, 0.2F, 0.6F, 0),
			k(13, 0.03F, 10, 0, -0.95F, -0.3F, 0, -1.05F, 0.6F, 0, -0.2F, 0.2F, 0.5F, 0),
			k(18, 0.05F, 12, 0, -0.7F, -0.3F, 0, -0.75F, 0.5F, 0, -0.2F, 0.2F, 0.55F, 0),
			rest(26, 0)});
		// (0.15.114) Point Blank: the gun brought up at arm's length, cocked, levelled down at the head, fired, the kick
		KEYS.put(Finishers.Kind.GUN, new Key[] {
			rest(0, 0),
			k(6, 0.0F, 8, 0, -1.5F, -0.1F, 0, -0.5F, 0.3F, 0, -0.1F, 0.1F, 0.45F, 0),
			k(11, 0.05F, 16, 0, -1.2F, -0.05F, 0, -0.9F, 0.6F, 0, -0.2F, 0.2F, 0.6F, 0),
			k(19, 0.05F, 18, 0, -1.1F, -0.05F, 0, -0.35F, 0.1F, 0, -0.2F, 0.2F, 0.65F, 0),
			k(20, 0.02F, 10, 0, -1.75F, -0.05F, 0.05F, -0.35F, 0.1F, 0, -0.2F, 0.2F, 0.5F, 0),
			k(24, 0.03F, 12, 0, -1.3F, -0.05F, 0, -0.35F, 0.1F, 0, -0.2F, 0.2F, 0.55F, 0),
			rest(30, 0)});
		// Bare hands: down on a knee, the head taken in both hands; (0.15.114) Andy: "neck break should take 5s to finish":
		// held, it's wrenched one way and fought back twice (40, 70), a wind back, and the snap at 100
		KEYS.put(Finishers.Kind.FIST, new Key[] {
			rest(0, 0),
			k(7, 0.45F, 30, 0, -0.6F, 0.1F, -0.1F, -0.6F, -0.1F, 0.1F, -1.15F, 1.35F, 0.6F, 0),
			k(13, 0.5F, 42, 0, -0.4F, -0.25F, -0.05F, -0.4F, 0.25F, 0.05F, -1.15F, 1.35F, 0.7F, 0),
			k(24, 0.5F, 42, -6, -0.45F, -0.3F, -0.05F, -0.35F, 0.2F, 0.05F, -1.15F, 1.35F, 0.7F, 0),
			k(38, 0.5F, 43, 6, -0.35F, -0.45F, 0.1F, -0.5F, 0.4F, -0.05F, -1.15F, 1.35F, 0.72F, 0),
			k(40, 0.52F, 44, 9, -0.3F, -0.5F, 0.14F, -0.55F, 0.45F, -0.07F, -1.15F, 1.35F, 0.75F, 0),
			k(45, 0.5F, 41, 2, -0.42F, -0.32F, 0, -0.4F, 0.28F, 0.03F, -1.15F, 1.35F, 0.7F, 0),
			k(56, 0.5F, 42, -4, -0.45F, -0.28F, -0.04F, -0.36F, 0.22F, 0.05F, -1.15F, 1.35F, 0.7F, 0),
			k(68, 0.5F, 43, 8, -0.33F, -0.48F, 0.12F, -0.52F, 0.42F, -0.06F, -1.15F, 1.35F, 0.73F, 0),
			k(70, 0.52F, 44, 11, -0.28F, -0.52F, 0.16F, -0.56F, 0.47F, -0.08F, -1.15F, 1.35F, 0.76F, 0),
			k(76, 0.5F, 41, 3, -0.42F, -0.32F, 0, -0.4F, 0.28F, 0.03F, -1.15F, 1.35F, 0.7F, 0),
			k(90, 0.5F, 42, -8, -0.48F, -0.25F, -0.08F, -0.32F, 0.18F, 0.06F, -1.15F, 1.35F, 0.7F, 0),
			k(97, 0.5F, 42, -10, -0.5F, -0.22F, -0.1F, -0.3F, 0.15F, 0.08F, -1.15F, 1.35F, 0.7F, 0),
			k(100, 0.5F, 42, 18, -0.25F, -0.6F, 0.2F, -0.6F, 0.6F, -0.1F, -1.15F, 1.35F, 0.75F, 0),
			k(106, 0.5F, 40, 15, -0.25F, -0.6F, 0.2F, -0.6F, 0.6F, -0.1F, -1.15F, 1.35F, 0.7F, 0),
			k(111, 0.3F, 20, 0, -0.3F, 0, 0.1F, -0.3F, 0, -0.1F, -1.0F, 1.2F, 0.5F, 0),
			rest(118, 0)});
		// (0.15.146) Wild Slam: whatever's in hand swung up over the head in both hands and hammered down three times, turned
		// a little this way, then that, the last with the whole body behind it
		Key up = k(0, -0.05F, -14, 0, -3.1F, 0, 0.3F, -3.1F, 0, -0.3F, -0.35F, 0.3F, 0.35F, -20);
		Key down = k(0, 0.3F, 40, 0, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -0.8F, 0.65F, 0.6F, 65);
		KEYS.put(Finishers.Kind.SLAM, new Key[] {
			rest(0, 0),
			at(up, 7), at(down, 10), at(down, 12),
			turned(up, 16, 10), turned(down, 19, 10), turned(down, 21, 8),
			turned(up, 25, -8), k(28, 0.38F, 48, -6, -0.4F, -0.35F, 0, -0.4F, 0.35F, 0, -0.95F, 0.8F, 0.65F, 65),
			k(32, 0.36F, 46, -4, -0.42F, -0.35F, 0, -0.42F, 0.35F, 0, -0.95F, 0.8F, 0.62F, 65),
			rest(36, 0)});
		// (0.15.146) Stabbed Through: the blade up point down and driven through them on one knee; then stuck: tugged at
		// leaning back, this way and that, and the last heave wrenches it out with a stagger back
		Key pull = k(0, 0.32F, 14, 0, -0.85F, -0.3F, 0, -0.85F, 0.3F, 0, -0.9F, 1.2F, 0.5F, 95);
		Key stuck = k(0, 0.44F, 32, 0, -0.45F, -0.4F, 0, -0.45F, 0.4F, 0, -1.0F, 1.35F, 0.6F, 90);
		KEYS.put(Finishers.Kind.IMPALE, new Key[] {
			rest(0, 0),
			k(5, -0.03F, -6, 0, -2.8F, 0, 0.3F, -2.8F, 0, -0.3F, 0, 0, 0.45F, 160),
			k(9, -0.05F, -10, 0, -2.95F, 0, 0.3F, -2.95F, 0, -0.3F, 0, 0, 0.45F, 165),
			k(12, 0.45F, 36, 0, -0.4F, -0.4F, 0, -0.4F, 0.4F, 0, -1.0F, 1.35F, 0.6F, 90),
			at(stuck, 17), turned(pull, 21, 8), at(stuck, 24), turned(pull, 28, -8), at(stuck, 31),
			k(34, 0.25F, 4, 0, -1.0F, -0.3F, 0, -1.0F, 0.3F, 0, -0.8F, 1.0F, 0.45F, 100),
			k(38, 0.3F, 10, 0, -0.9F, -0.3F, 0, -0.9F, 0.3F, 0, -0.85F, 1.1F, 0.5F, 95),
			k(40, 0.02F, -10, 0, -1.5F, -0.3F, 0.1F, -1.3F, 0.3F, -0.1F, -0.2F, 0.35F, 0.3F, 60),
			k(43, 0.0F, -4, 0, -0.9F, -0.1F, 0.05F, -0.6F, 0, -0.05F, -0.1F, 0.2F, 0.35F, 30),
			rest(46, 0)});
		// (0.15.148) Ball and Chain: the flail held up overhead and whirled (the handle twirling in the hand; the ball and
		// chain are LashRenderer's), brought down on them, whirled again, and down with the whole body behind it
		Key whirl = k(0, -0.03F, -6, 0, -3.0F, 0, 0.35F, -0.5F, 0.1F, -0.3F, -0.25F, 0.25F, 0.3F, 0);
		Key crash = k(0, 0.32F, 40, 0, -0.45F, -0.3F, 0, -0.5F, 0.3F, 0, -0.85F, 0.7F, 0.6F, 60);
		KEYS.put(Finishers.Kind.FLAIL, new Key[] {
			rest(0, 0),
			at(whirl, 4), twirl(whirl, 9, 180), twirl(whirl, 14, 360),
			at(crash, 18), at(crash, 22),
			twirl(whirl, 26, 0), twirl(whirl, 30, 180), twirl(whirl, 33, 360),
			k(36, 0.4F, 50, 0, -0.35F, -0.35F, 0, -0.4F, 0.35F, 0, -1.0F, 0.85F, 0.65F, 65),
			k(41, 0.38F, 46, 0, -0.4F, -0.35F, 0, -0.42F, 0.35F, 0, -1.0F, 0.85F, 0.62F, 65),
			rest(46, 0)});
		// (0.15.148) Garrotte: the whip drawn back and cracked out round their neck, then both hands on the lash, leaning
		// back on it, feet braced, three hauls, the last one hard
		Key haul = k(0, 0.12F, -12, 0, -1.0F, -0.15F, 0, -1.1F, 0.25F, 0, -0.45F, 0.55F, 0.45F, 0);
		Key tight = k(0, 0.2F, -22, 0, -0.55F, -0.2F, 0, -0.65F, 0.3F, 0, -0.6F, 0.75F, 0.5F, 0);
		KEYS.put(Finishers.Kind.WHIP, new Key[] {
			rest(0, 0),
			k(5, 0.0F, -6, -15, -2.7F, -0.2F, 0.2F, -0.4F, 0, -0.1F, -0.15F, 0.2F, 0.3F, 0),
			k(Finishers.Kind.WHIP_CRACK, 0.05F, 18, 5, -1.0F, -0.1F, 0, -0.5F, 0, -0.1F, -0.4F, 0.4F, 0.5F, 0),
			at(haul, 14), at(haul, 20), at(tight, 24), at(haul, 30), at(haul, 37), at(tight, 42), at(haul, 48), at(haul, 53),
			k(58, 0.28F, -30, 10, -0.3F, -0.25F, 0, -0.4F, 0.35F, 0, -0.7F, 0.9F, 0.5F, 0),
			at(haul, 61), rest(64, 0)});
		// (0.17.181) Stoned: down on a knee at their head, the off hand flat on their chest, the rock raised high in the main
		// hand and smashed down three times, quicker each time; then both hands under it, lifted overhead, and dropped onto
		// the skull with the whole body behind it, held a moment, and up
		Key raise = k(0, 0.4F, 10, -6, -2.85F, -0.1F, 0.25F, -0.75F, 0.25F, -0.1F, -1.15F, 1.35F, 0.5F, 0);
		Key smash = k(0, 0.46F, 38, 4, -0.55F, -0.2F, 0.05F, -0.75F, 0.3F, -0.1F, -1.15F, 1.35F, 0.7F, 0);
		Key heave = k(0, 0.32F, -8, 0, -3.05F, 0.3F, 0.15F, -3.05F, -0.3F, -0.15F, -1.15F, 1.35F, 0.35F, 0);
		Key drop = k(0, 0.52F, 48, 0, -0.5F, 0.35F, 0, -0.5F, -0.35F, 0, -1.15F, 1.35F, 0.75F, 0);
		KEYS.put(Finishers.Kind.ROCK, new Key[] {
			rest(0, 0),
			k(5, 0.42F, 25, 0, -0.8F, -0.1F, 0.1F, -0.75F, 0.3F, -0.1F, -1.15F, 1.35F, 0.6F, 0),
			at(raise, 9), at(smash, 12), at(smash, 13),
			turned(raise, 17, -10), turned(smash, 20, 6), turned(smash, 21, 6),
			turned(raise, 25, -4), turned(smash, 28, 2), at(smash, 29),
			at(heave, 36), at(drop, 40), at(drop, 44),
			k(48, 0.3F, 15, 0, -0.5F, -0.1F, 0.1F, -0.4F, 0.1F, -0.1F, -1.0F, 1.2F, 0.5F, 0),
			rest(52, 0)});
		zfighter();
	}

	/**
	 * (0.18.25) A Z Fighter's bare hands ({@code ZFighterExecutions}): the ki in them is the server's (particles, beams,
	 * the film); these are the bodies under it.
	 */
	private static void zfighter() {
		// Point-Blank: stood over them, the palm thrust down at the chest, the off hand bracing the wrist as it fills, the kick
		KEYS.put(Finishers.Kind.Z_PALM, new Key[] {
			rest(0, 0),
			k(6, 0.1F, 20, -10, -1.0F, 0.1F, 0.1F, -0.3F, 0, -0.15F, -0.35F, 0.3F, 0.55F, 0),
			k(18, 0.15F, 24, -14, -1.05F, 0.15F, 0.12F, -0.5F, 0.3F, -0.1F, -0.4F, 0.35F, 0.6F, 0),
			k(21, 0.12F, 22, -16, -1.0F, 0.15F, 0.12F, -0.55F, 0.35F, -0.1F, -0.4F, 0.35F, 0.6F, 0),
			k(22, 0.05F, 10, -8, -1.35F, 0.1F, 0.15F, -0.6F, 0.3F, -0.1F, -0.3F, 0.3F, 0.45F, 0),
			k(26, 0.08F, 14, -6, -1.2F, 0.1F, 0.1F, -0.4F, 0.2F, -0.1F, -0.3F, 0.3F, 0.5F, 0),
			rest(34, 0)});
		// Flurry: down on a knee over them, right, left, right... too fast to follow, then the right fist raised high and
		// brought down with everything
		Key right = k(0, 0.45F, 36, 6, -0.55F, 0, 0, 0.1F, 0, -0.1F, -1.15F, 1.35F, 0.65F, 0);
		Key left = k(0, 0.45F, 36, -6, 0.1F, 0, 0.1F, -0.55F, 0, 0, -1.15F, 1.35F, 0.65F, 0);
		KEYS.put(Finishers.Kind.Z_FLURRY, new Key[] {
			rest(0, 0),
			k(5, 0.42F, 30, 0, -0.8F, 0, 0.1F, -0.8F, 0, -0.1F, -1.15F, 1.35F, 0.6F, 0),
			at(right, 8), at(left, 11), at(right, 14), at(left, 17), at(right, 20), at(left, 23), at(right, 26),
			k(32, 0.38F, 20, -14, -2.9F, 0, 0.3F, -0.4F, 0, -0.1F, -1.15F, 1.35F, 0.5F, 0),
			k(33, 0.38F, 19, -15, -2.95F, 0, 0.3F, -0.4F, 0, -0.1F, -1.15F, 1.35F, 0.5F, 0),
			k(36, 0.5F, 46, 6, -0.4F, 0, 0, -0.5F, 0.1F, -0.1F, -1.15F, 1.35F, 0.75F, 0),
			k(40, 0.5F, 45, 5, -0.42F, 0, 0, -0.5F, 0.1F, -0.1F, -1.15F, 1.35F, 0.72F, 0),
			rest(46, 0)});
		// Hammer Fist: both fists clasped overhead, down on them, up again, and down with the whole body behind it
		Key overhead = k(0, -0.04F, -10, 0, -3.0F, -0.3F, 0, -3.0F, 0.3F, 0, -0.3F, 0.3F, 0.35F, 0);
		Key hammered = k(0, 0.3F, 40, 0, -0.6F, -0.35F, 0, -0.6F, 0.35F, 0, -0.85F, 0.7F, 0.6F, 0);
		KEYS.put(Finishers.Kind.Z_HAMMER, new Key[] {
			rest(0, 0),
			at(overhead, 9), at(hammered, 14), at(hammered, 17), at(overhead, 25),
			k(30, 0.42F, 50, 0, -0.45F, -0.35F, 0, -0.45F, 0.35F, 0, -1.0F, 0.85F, 0.7F, 0),
			k(34, 0.42F, 49, 0, -0.47F, -0.35F, 0, -0.47F, 0.35F, 0, -1.0F, 0.85F, 0.68F, 0),
			rest(40, 0)});
		// Cupped Wave: turned to the right with both hands cupped at the hip as it gathers, then thrust down at them
		Key hip = k(0, 0.12F, 8, -35, 0.3F, -0.1F, 0.15F, -0.2F, 0.7F, 0.35F, -0.45F, 0.5F, 0.4F, 0);
		Key thrust = k(0, 0.2F, 26, 0, -1.15F, -0.25F, 0, -1.15F, 0.25F, 0, -0.6F, 0.55F, 0.55F, 0);
		KEYS.put(Finishers.Kind.Z_WAVE, new Key[] {
			rest(0, 0),
			at(hip, 6),
			k(26, 0.14F, 6, -38, 0.35F, -0.1F, 0.15F, -0.15F, 0.72F, 0.35F, -0.5F, 0.55F, 0.4F, 0),
			at(thrust, 30), at(thrust, 37),
			k(40, 0.15F, 20, 0, -1.25F, -0.25F, 0, -1.25F, 0.25F, 0, -0.5F, 0.5F, 0.5F, 0),
			rest(46, 0)});
		// Crimson Cannon: the arms flung wide as it gathers, the head back, then the palms slammed together at them, the recoil
		Key wide = k(0, 0.1F, -6, 0, -0.2F, 0, 1.35F, -0.2F, 0, -1.35F, -0.35F, 0.35F, 0.2F, 0);
		Key palms = k(0, 0.15F, 22, 0, -1.25F, -0.3F, 0, -1.25F, 0.3F, 0, -0.55F, 0.5F, 0.55F, 0);
		KEYS.put(Finishers.Kind.Z_CANNON, new Key[] {
			rest(0, 0),
			at(wide, 7),
			k(22, 0.12F, -10, 0, -0.3F, 0, 1.45F, -0.3F, 0, -1.45F, -0.4F, 0.4F, 0.15F, 0),
			at(palms, 26), at(palms, 28),
			k(30, 0.1F, 12, 0, -1.45F, -0.3F, 0, -1.45F, 0.3F, 0, -0.5F, 0.5F, 0.45F, 0),
			at(palms, 36), rest(44, 0)});
		// Spiral Lance: two fingers to the brow, the off hand gripping the wrist, crackling; then the arm thrown down at them
		Key point = k(0, 0.1F, 18, 0, -1.05F, -0.05F, 0, -0.3F, 0, -0.1F, -0.4F, 0.35F, 0.55F, 0);
		KEYS.put(Finishers.Kind.Z_LANCE, new Key[] {
			rest(0, 0),
			k(6, 0.05F, 0, 0, -2.5F, -0.6F, 0, -1.5F, 0.6F, 0, -0.3F, 0.3F, 0.15F, 0),
			k(26, 0.05F, -2, 0, -2.55F, -0.62F, 0, -1.55F, 0.62F, 0, -0.3F, 0.3F, 0.1F, 0),
			at(point, 29), at(point, 30),
			k(32, 0.06F, 10, 0, -1.3F, -0.05F, 0, -0.3F, 0, -0.1F, -0.4F, 0.35F, 0.45F, 0),
			at(point, 36), rest(42, 0)});
		// Frost Needle: stood tall, the off hand behind the back, one finger pointed down: a leg, an arm, a moment's
		// look at the head, and the last
		Key aim = k(0, 0.0F, 6, 0, -0.95F, -0.05F, 0.05F, 0.55F, 0.4F, 0.15F, -0.1F, 0.1F, 0.5F, 0);
		Key flick = k(0, 0.0F, 4, 0, -1.15F, -0.05F, 0.05F, 0.55F, 0.4F, 0.15F, -0.1F, 0.1F, 0.45F, 0);
		KEYS.put(Finishers.Kind.Z_NEEDLE, new Key[] {
			rest(0, 0),
			turned(aim, 6, 8), turned(aim, 11, 8), turned(flick, 12, 8),
			turned(aim, 18, -6), turned(aim, 21, -6), turned(flick, 22, -6),
			k(28, 0.0F, 4, 0, -1.0F, -0.05F, 0.05F, 0.55F, 0.4F, 0.15F, -0.1F, 0.1F, 0.6F, 0),
			k(35, 0.0F, 4, 0, -1.0F, -0.05F, 0.05F, 0.55F, 0.4F, 0.15F, -0.1F, 0.1F, 0.6F, 0),
			at(flick, 36), at(flick, 39), rest(48, 0)});
		// Supernova Palm: on a knee, the palm flat on their chest, trembling as the aura pours through it, and the flare
		Key pressed = k(0, 0.45F, 36, 0, -0.6F, 0, 0.05F, 0.05F, 0, -0.2F, -1.15F, 1.35F, 0.65F, 0);
		KEYS.put(Finishers.Kind.Z_NOVA, new Key[] {
			rest(0, 0),
			at(pressed, 6), turned(pressed, 12, 3), turned(pressed, 16, -3), turned(pressed, 20, 3), turned(pressed, 24, -3),
			turned(pressed, 28, 0),
			k(30, 0.4F, 28, 0, -0.75F, 0, 0.05F, 0.1F, 0, -0.3F, -1.15F, 1.35F, 0.5F, 0),
			k(36, 0.4F, 28, 0, -0.75F, 0, 0.05F, 0.1F, 0, -0.3F, -1.15F, 1.35F, 0.5F, 0),
			rest(44, 0)});
	}

	/** {@code key} at {@code t} with the weapon turned {@code grip} degrees in the hand (a full circle twirls it). */
	private static Key twirl(Key key, float t, float grip) {
		return k(t, key.sink(), key.pitch(), key.twist(), key.rax(), key.ray(), key.raz(), key.lax(), key.lay(), key.laz(), key.rightLeg(),
			key.leftLeg(), key.head(), grip);
	}

	private static Key turned(Key key, float t, float twist) {
		return k(t, key.sink(), key.pitch(), twist, key.rax(), key.ray(), key.raz(), key.lax(), key.lay(), key.laz(), key.rightLeg(),
			key.leftLeg(), key.head(), key.grip());
	}

	private static Key at(Key key, float t) {
		return k(t, key.sink(), key.pitch(), key.twist(), key.rax(), key.ray(), key.raz(), key.lax(), key.lay(), key.laz(), key.rightLeg(),
			key.leftLeg(), key.head(), key.grip());
	}

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("finisher");

	/** What's drawn: the move's pose at this moment, how far it steps in (blocks), and whether the main hand is the right. */
	public record Pose(Key key, float step, boolean rightHanded) {
		/** The held weapon's turn in the hand (degrees, toward the arm's line). */
		public float grip() {
			return this.key.grip();
		}
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:finisher");

	/** Whether we put the view in third person for our own finisher (and so put it back after). */
	private static boolean viewTaken;

	private FinisherPose() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FinisherPose::tick);
	}

	/** Our own finisher watched from behind, and first person given back after. */
	private static void tick(Minecraft minecraft) {
		if (!CINEMATIC) {
			return;
		}
		boolean on = minecraft.player != null && Finishers.busy(minecraft.player);
		if (on && !viewTaken && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
			viewTaken = true;
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (!on && viewTaken) {
			viewTaken = false;
			if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.FIRST_PERSON);
			}
		}
	}

	/** From the avatar renderer's extractRenderState: the pose now, and the body turned to face them. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Finishers.Finish finish = Finishers.of(entity);
		if (finish == null) {
			state.setData(KEY, null);
			return;
		}
		Finishers.Kind kind = finish.type();
		float t = entity.level().getGameTime() - finish.start() + partialTicks;
		Key[] keys = KEYS.get(kind);
		if (keys == null || t < 0.0F || t >= kind.length) {
			state.setData(KEY, null);
			return;
		}
		float step = 0.0F;
		Entity body = entity.level().getEntity(finish.target());
		if (body != null) {
			// (0.15.146) the neck snap kneels to the head, not the middle; (0.17.181) so does the rock
			net.minecraft.world.phys.Vec3 at = (kind == Finishers.Kind.FIST || kind == Finishers.Kind.ROCK) && body instanceof net.minecraft.world.entity.LivingEntity living
				? Finishers.headAt(living) : body.position();
			double dx = at.x - entity.getX(), dz = at.z - entity.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float head = state.bodyRot + state.yRot;
			state.bodyRot = yaw;
			state.yRot = Mth.clamp(Mth.wrapDegrees(head - yaw), -50.0F, 50.0F);
			// (a step in, over the first few ticks, if they lie further off than the move reaches)
			float in = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
			step = Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) - REACH, 0.0F, STEP_MAX) * in * in * (3.0F - 2.0F * in);
		}
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new Pose(sample(keys, t), step, entity.getMainArm() == HumanoidArm.RIGHT));
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
				return k(t, Mth.lerp(p, a.sink(), b.sink()), Mth.lerp(p, a.pitch(), b.pitch()), Mth.lerp(p, a.twist(), b.twist()),
					Mth.lerp(p, a.rax(), b.rax()), Mth.lerp(p, a.ray(), b.ray()), Mth.lerp(p, a.raz(), b.raz()),
					Mth.lerp(p, a.lax(), b.lax()), Mth.lerp(p, a.lay(), b.lay()), Mth.lerp(p, a.laz(), b.laz()),
					Mth.lerp(p, a.rightLeg(), b.rightLeg()), Mth.lerp(p, a.leftLeg(), b.leftLeg()), Mth.lerp(p, a.head(), b.head()),
					Mth.lerp(p, a.grip(), b.grip()));
			}
		}
		return keys[keys.length - 1];
	}

	/** From setupRotations, after the body faces its way: the step in, the sink, the turn and the bend from the hips. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Pose pose = state.getData(KEY);
		if (pose == null) {
			return;
		}
		PoseBlend.Track track = BLEND.peek(state.id);
		float w = track == null ? 0.0F : track.amount();
		Key key = pose.key();
		// (after the body's turn, forward is -Z)
		poseStack.translate(0.0F, -key.sink() * w, -pose.step() * w);
		poseStack.translate(0.0F, HIP, 0.0F);
		poseStack.rotateDegrees(Axis.YP, key.twist() * w);
		poseStack.rotateDegrees(Axis.XP, -key.pitch() * w);
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** After the model animates: the arms, legs and head into the move, eased in and out. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = state.getData(KEY);
		PoseBlend.Track track = BLEND.track(state.id, pose != null, 0.0F, ENTRY_SPEED);
		if (track == null) {
			return;
		}
		if (pose == null) {
			track.apply(h, null);
			return;
		}
		Key key = pose.key();
		PoseBlend.Frame frame = new PoseBlend.Frame(h);
		// (the moves are made right-handed; a left-handed one mirrors them)
		float m = pose.rightHanded() ? 1.0F : -1.0F;
		int main = pose.rightHanded() ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM;
		int off = pose.rightHanded() ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		frame.set(main, key.rax(), key.ray() * m, key.raz() * m);
		frame.set(off, key.lax(), key.lay() * m, key.laz() * m);
		frame.set(PoseBlend.BODY, 0.0F, 0.0F, 0.0F);
		// the legs as the world sees them, so they stay planted under the bend
		float bend = key.pitch() * Mth.DEG_TO_RAD;
		int mainLeg = pose.rightHanded() ? PoseBlend.RIGHT_LEG : PoseBlend.LEFT_LEG;
		int offLeg = pose.rightHanded() ? PoseBlend.LEFT_LEG : PoseBlend.RIGHT_LEG;
		frame.set(mainLeg, key.rightLeg() - bend, 0.08F * m, 0.0F);
		frame.set(offLeg, key.leftLeg() - bend, -0.08F * m, 0.0F);
		frame.set(PoseBlend.HEAD, key.head(), frame.vanilla(PoseBlend.HEAD + 1), 0.0F);
		track.apply(h, frame, LIMB_STIFFNESS);
	}

	/**
	 * From the held-item layer, just before the main hand's item is drawn (the pose stack at the grip): the weapon turned
	 * in the hand (the main hand's only).
	 */
	public static void grip(Object renderState, HumanoidArm arm, PoseStack poseStack) {
		if (!(renderState instanceof AvatarRenderState state)) {
			return;
		}
		@Nullable Pose pose = state.getData(KEY);
		if (pose == null || arm != state.mainArm || Math.abs(pose.grip()) < 0.01F) {
			return;
		}
		// (about the hand's own side-to-side axis: + turns the weapon toward the line of the arm)
		poseStack.rotateDegrees(Axis.XP, -pose.grip());
	}
}
