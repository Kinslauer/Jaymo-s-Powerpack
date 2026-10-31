package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.freerun.Slide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.6) Andy: "the character should also crouch down and into the prone smoother than it does now, the animations
 * kinda glide together". Going prone by your own crawl (the Crawl key, the stance wheel) is no longer vanilla's swimming
 * tilt, which pivots the body about the feet while it jumps a block back: you crouch, then go forward down onto your
 * hands as the legs slide out behind you, ending exactly where the crawl lies. Getting up plays it backwards: the hands
 * push you up, the feet come in under you, and you rise out of the crouch. In first person the view goes down (or up)
 * with the head instead of dropping in a few ticks.
 * <p>
 * Not for the downed (they fall, {@link CrawlPose}'s jolt), a slide ending prone (already low), a ledge climb, or water.
 */
public final class ProneTransition {
	/** Going down and getting up (ticks); both outlast vanilla's swim tilt easing (0.09 a tick), so it never shows. */
	static final float DOWN_TICKS = 13.0F, UP_TICKS = 12.0F;
	/** The share of the move spent crouching (the rest is the body going down onto the hands, or up off them). */
	static final float CROUCH_SHARE = 0.3F;
	/** The crouch: how far the body sinks (blocks), the lean forward at the hips and the step of the legs (radians). */
	static final float CROUCH_SINK = 0.18F, CROUCH_LEAN = 0.55F, CROUCH_STEP = 0.5F, CROUCH_KNEEL = 0.35F;
	/** The arms: reaching down ahead in the crouch, then planted under the shoulders lying flat (radians), and spread. */
	static final float ARMS_REACH = -0.6F, ARMS_PLANTED = -1.55F, ARMS_OUT = 0.2F;
	/** The head looking ahead as the body tips (share of the tip it pitches back against). */
	static final float HEAD_LEVEL = 0.6F;
	/** Vanilla's crawl, lying flat: the body raised (blocks) and moved back a block along it (swimming tilt and shift). */
	static final float FLAT_RAISE = 0.3F, FLAT_BACK = 1.0F;
	/** The eye up the body standing (blocks), for how far the view goes with the head. */
	static final float BODY_EYE = 1.62F;

	/** Going down ({@code down}) or getting up, started at {@code start} (game time), the eye height on the feet. */
	private record Move(float start, boolean down, float footEye) {
	}

	/** How far down this player is drawn (0 on the feet, 1 lying flat), while they go down or get up. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:prone_move");

	private static final Map<Integer, Move> MOVES = new HashMap<>();
	/** Players who lay prone by their own crawl last tick, and their eye height and whether they were on their feet. */
	private static final Set<Integer> LYING = new HashSet<>();
	private static final Map<Integer, Float> LAST_EYE = new HashMap<>();
	private static final Set<Integer> ON_FEET = new HashSet<>();

	private ProneTransition() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ProneTransition::tick);
	}

	/** Prone by their own crawl, on dry land and not being held there by the downed state or a ledge climb. */
	private static boolean lying(Player player) {
		return Crawl.crawling(player) && !Downed.isDowned(player) && !player.isInWater() && !Ledge.proneClimbing(player);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			MOVES.clear();
			LYING.clear();
			LAST_EYE.clear();
			ON_FEET.clear();
			return;
		}
		float now = level.getGameTime();
		Set<Integer> seen = new HashSet<>();
		for (Player player : level.players()) {
			int id = player.getId();
			seen.add(id);
			boolean lying = lying(player);
			boolean was = LYING.contains(id);
			Float progress = progress(id, now);
			if (lying && !was && ON_FEET.contains(id)) {
				// down from the feet: a crawl that cuts a getting-up short goes on from how far up it got
				float from = progress == null ? 0.0F : progress;
				MOVES.put(id, new Move(now - from * DOWN_TICKS, true, LAST_EYE.getOrDefault(id, player.getEyeHeight(Pose.STANDING))));
			} else if (!lying && was && Crawl.canCrawl(player) && !Downed.isDowned(player)) {
				float from = progress == null ? 1.0F : progress;
				Pose up = player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
				MOVES.put(id, new Move(now - (1.0F - from) * UP_TICKS, false, player.getEyeHeight(up)));
			} else if (lying != was) {
				MOVES.remove(id);
			}
			Move move = MOVES.get(id);
			if (move != null && (now - move.start() > (move.down() ? DOWN_TICKS : UP_TICKS) + 1.0F
				|| move.down() != lying || Downed.isDowned(player) || player.isInWater())) {
				MOVES.remove(id);
			}
			if (lying) {
				LYING.add(id);
			} else {
				LYING.remove(id);
			}
			// on the feet: standing or crouched on dry land, not sliding (a slide that ends prone is low already)
			Pose pose = player.getPose();
			if ((pose == Pose.STANDING || pose == Pose.CROUCHING) && !player.hasAttached(Slide.SLIDING) && !player.isInWater()) {
				ON_FEET.add(id);
			} else {
				ON_FEET.remove(id);
			}
			LAST_EYE.put(id, player.getEyeHeight());
		}
		MOVES.keySet().retainAll(seen);
		LYING.retainAll(seen);
		LAST_EYE.keySet().retainAll(seen);
		ON_FEET.retainAll(seen);
	}

	/** How far down (0 on the feet, 1 flat) this player's move has them at {@code time}, or null with none under way. */
	private static @Nullable Float progress(int id, float time) {
		Move move = MOVES.get(id);
		if (move == null) {
			return null;
		}
		float t = (time - move.start()) / (move.down() ? DOWN_TICKS : UP_TICKS);
		if (t < 0.0F || t >= 1.0F) {
			return null;
		}
		return move.down() ? t : 1.0F - t;
	}

	/** How far down this player is drawn now (0 on the feet, 1 flat), or null with no move under way. */
	public static @Nullable Float progress(Player player, float partialTicks) {
		return progress(player.getId(), player.level().getGameTime() + partialTicks);
	}

	/** From {@link CrawlPose#extract}: the move's progress onto the render state. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, player.isInWater() ? null : progress(player, partialTicks));
	}

	private static float crouch(float down) {
		return PoseBlend.smooth(Mth.clamp(down / CROUCH_SHARE, 0.0F, 1.0F));
	}

	private static float tip(float down) {
		return PoseBlend.smooth(Mth.clamp((down - CROUCH_SHARE) / (1.0F - CROUCH_SHARE), 0.0F, 1.0F));
	}

	/**
	 * After vanilla's {@code setupRotations}: its swimming tilt (and shift) undone, then the body crouched and tipped
	 * forward about the feet as they slide back, ending (at {@code down} 1) exactly as vanilla lays the crawl.
	 */
	static void rotate(AvatarRenderState state, PoseStack poseStack, float down) {
		if (state.swimAmount > 0.0F) {
			if (state.isVisuallySwimming) {
				poseStack.translate(0.0F, 1.0F, -0.3F);
			}
			poseStack.rotateDegrees(Axis.XP, 90.0F * state.swimAmount);
		}
		float c = crouch(down), u = tip(down);
		poseStack.translate(0.0F, -CROUCH_SINK * c * (1.0F - u) + FLAT_RAISE * u * u, FLAT_BACK * u);
		poseStack.rotateDegrees(Axis.XP, -90.0F * u);
	}

	/** The limbs going down (or up): crouched with a leg stepped out, the arms reaching, then planted under the shoulders. */
	static void pose(PoseBlend.Frame f, float down) {
		float c = crouch(down), u = tip(down);
		float crouched = c * (1.0F - u);
		float arms = u > 0.0F ? Mth.lerp(u, ARMS_REACH, ARMS_PLANTED) : ARMS_REACH * c;
		f.set(PoseBlend.RIGHT_ARM, arms, 0.0F, ARMS_OUT);
		f.set(PoseBlend.LEFT_ARM, arms, 0.0F, -ARMS_OUT);
		f.set(PoseBlend.RIGHT_LEG, -CROUCH_STEP * crouched, 0.0F, 0.04F);
		f.set(PoseBlend.LEFT_LEG, CROUCH_KNEEL * crouched, 0.0F, -0.04F);
		f.set(PoseBlend.HEAD, -HEAD_LEVEL * u * Mth.HALF_PI, f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
		f.set(PoseBlend.HIP_PITCH, CROUCH_LEAN * crouched);
		f.set(PoseBlend.BODY + 1, 0.0F);
	}

	/**
	 * First person: the eye height (blocks above the feet) the view should be at while the local player goes down or
	 * gets up, following the head down the move; or null with no move under way.
	 */
	public static @Nullable Float eyeHeight(Player player, float partialTicks) {
		Float down = progress(player, partialTicks);
		Move move = MOVES.get(player.getId());
		if (down == null || move == null) {
			return null;
		}
		float c = crouch(down), u = tip(down);
		float a = u * Mth.HALF_PI;
		float head = -CROUCH_SINK * c * (1.0F - u) + FLAT_RAISE * u * u + BODY_EYE * Mth.cos(a);
		float flat = FLAT_RAISE;
		float g = Mth.clamp((head - flat) / (BODY_EYE - flat), 0.0F, 1.0F);
		return Mth.lerp(g, player.getEyeHeight(Pose.SWIMMING), move.footEye());
	}
}
