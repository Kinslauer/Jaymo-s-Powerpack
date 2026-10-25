package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.21) A wounded player nurses the wound now and then, for everyone to see: the hand goes to the part that hurts
 * (fingers to the temple for the head, a hand pressed to the side for the body, the other hand holding a hurt arm, a hand
 * on the thigh and a bend for a leg), a short wince, then it lets go. Andy: a festering body wound had the hand going to
 * the head, far too often; it should go to the spot, and come round now and then.
 * <p>
 * Which wound: festering first, then broken, then bleeding (the body before the head, then arms, then legs). Breaks are
 * synced to everyone; festering and bleeding only to the player themselves, so others see the gesture for breaks only.
 * It comes every {@link #GAP_MIN}-{@link #GAP_MAX} seconds, not while any other pose is on, while attacking, using an
 * item, riding, swimming, flying, sleeping or downed, nor on your own body in first person.
 */
public final class WoundNursePose {
	/** Seconds between gestures (picked at random each time), before the first, and the retry after a busy moment. */
	static final float GAP_MIN = 35.0F, GAP_MAX = 70.0F, FIRST_MIN = 8.0F, FIRST_MAX = 20.0F, BUSY_RETRY = 6.0F;
	/** How long one gesture lasts (seconds): the hand gets there by {@link #REACHED}, holds, lets go from {@link #RELEASE}. */
	static final float LENGTH = 2.6F, REACHED = 0.45F, RELEASE = 1.9F;
	/** Faster than this walk (vanilla's walk speed, 0-1) and there's no stopping to nurse it. */
	static final float WALK_MAX = 0.55F;
	/** The wince: the body bends at the hips (radians) and the head drops (radians); the rub's rate and size (radians). */
	static final float WINCE_BEND = 0.18F, LEG_BEND = 0.38F, HEAD_DROP = 0.3F, RUB_RATE = 9.0F, RUB = 0.06F;

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("wound_nurse");
	private static final RandomSource RANDOM = RandomSource.create();
	/** Per entity id: when (System.nanoTime) the next gesture may start, and when the one playing started (or absent). */
	private static final Int2LongOpenHashMap NEXT = new Int2LongOpenHashMap(), STARTED = new Int2LongOpenHashMap();

	/** {@code part}: the wound nursed; {@code left}: nursed with the left hand; {@code age}: seconds into the gesture. */
	public record Nurse(BodyPart part, boolean left, float age) {
	}

	public static final RenderStateDataKey<Nurse> KEY = RenderStateDataKey.create(() -> "jaymospowers:wound_nurse");

	private WoundNursePose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, null);
		int id = entity.getId();
		BodyPart part = entity instanceof Player player ? worst(player) : null;
		if (part == null || !entity.isAlive()) {
			NEXT.remove(id);
			STARTED.remove(id);
			return;
		}
		long now = System.nanoTime();
		if (!NEXT.containsKey(id)) {
			NEXT.put(id, now + nanos(FIRST_MIN + RANDOM.nextFloat() * (FIRST_MAX - FIRST_MIN)));
		}
		if (STARTED.containsKey(id)) {
			float age = (now - STARTED.get(id)) / 1.0E9F;
			if (age < LENGTH && free(entity, state)) {
				state.setData(KEY, new Nurse(part, leftHand(entity, part), age));
				return;
			}
			STARTED.remove(id);
			NEXT.put(id, now + nanos(GAP_MIN + RANDOM.nextFloat() * (GAP_MAX - GAP_MIN)));
			return;
		}
		if (now < NEXT.get(id)) {
			return;
		}
		if (!free(entity, state)) {
			NEXT.put(id, now + nanos(BUSY_RETRY));
			return;
		}
		STARTED.put(id, now);
		state.setData(KEY, new Nurse(part, leftHand(entity, part), 0.0F));
	}

	/** After the model animates: the hand to the wound, the wince, eased in and out. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Nurse nurse = state.getData(KEY);
		// any other pose of ours wins: the gesture lets go under it
		boolean on = nurse != null && !PoseTakeover.posedOtherwise(state);
		PoseBlend.Track track = BLEND.track(state.id, on);
		if (track == null) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		float t = nurse.age();
		float hold = PoseBlend.seg(t, 0.0F, REACHED) * (1.0F - PoseBlend.seg(t, RELEASE, LENGTH));
		float rub = Mth.sin(t * RUB_RATE) * RUB * PoseBlend.bump(t, REACHED, REACHED + 0.2F, RELEASE - 0.2F, RELEASE);
		PoseBlend.Frame frame = new PoseBlend.Frame(h);
		boolean left = nurse.left();
		// the hand's arm, mirrored for the left (y and z turn the other way)
		int arm = left ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		float side = left ? -1.0F : 1.0F;
		switch (nurse.part()) {
			case HEAD -> {
				// fingers to the temple, the head tipped into the hand
				frame.mix(arm, -2.55F, -0.55F * side, 0.25F * side, hold);
				frame.add(PoseBlend.HEAD, HEAD_DROP * 0.6F * hold);
				frame.add(PoseBlend.HEAD + 2, -0.15F * side * hold);
			}
			case BODY -> {
				// a hand pressed to the side, bent over it a little
				frame.mix(arm, -0.75F, -0.35F * side, -0.55F * side, hold);
				frame.add(PoseBlend.HIP_PITCH, WINCE_BEND * hold);
				frame.add(PoseBlend.HEAD, HEAD_DROP * hold);
			}
			case LEFT_ARM, RIGHT_ARM -> {
				// the hurt arm held in to the body, the other hand round it
				int hurt = left ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM;
				frame.mix(hurt, -0.7F, 0.0F, 0.2F * side, hold);
				frame.mix(arm, -1.05F, -0.6F * side, -0.45F * side, hold);
				frame.add(PoseBlend.HEAD, HEAD_DROP * 0.8F * hold);
				frame.add(PoseBlend.HEAD + 1, 0.35F * side * hold);
			}
			default -> {
				// a leg: bent to it, the hand on the thigh, the leg eased forward off its weight
				boolean leftLeg = nurse.part() == BodyPart.LEFT_LEG || nurse.part() == BodyPart.LEFT_FOOT;
				frame.mix(arm, -0.45F, 0.0F, (leftLeg == left ? 0.1F : -0.35F) * side, hold);
				frame.add(leftLeg ? PoseBlend.LEFT_LEG : PoseBlend.RIGHT_LEG, -0.3F * hold);
				frame.add(PoseBlend.HIP_PITCH, LEG_BEND * hold);
				frame.add(PoseBlend.HEAD, HEAD_DROP * 0.5F * hold);
			}
		}
		frame.shake(arm + 2, rub);
		track.apply(h, frame);
	}

	/** The wound to nurse: festering, then broken, then bleeding; null if none. */
	private static @Nullable BodyPart worst(Player player) {
		for (int mask : new int[] {player.getAttachedOrElse(Bleeding.FESTERING, 0), player.getAttachedOrElse(Breaks.BROKEN, 0),
			player.getAttachedOrElse(Bleeding.BLEEDING, 0)}) {
			for (BodyPart part : new BodyPart[] {BodyPart.BODY, BodyPart.HEAD, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM,
				BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.LEFT_FOOT, BodyPart.RIGHT_FOOT}) {
				if ((mask & 1 << part.ordinal()) != 0) {
					return part;
				}
			}
		}
		return null;
	}

	/** The hand that nurses it: the right, unless the right arm is the one hurt or is broken. */
	private static boolean leftHand(Avatar entity, BodyPart part) {
		if (part == BodyPart.RIGHT_ARM) {
			return true;
		}
		if (part == BodyPart.LEFT_ARM) {
			return false;
		}
		return (entity.getAttachedOrElse(Breaks.BROKEN, 0) & 1 << BodyPart.RIGHT_ARM.ordinal()) != 0;
	}

	/** Nothing else is going on that the gesture would get in the way of. */
	private static boolean free(Avatar entity, AvatarRenderState state) {
		if (!entity.onGround() || entity.isPassenger() || entity.isInWater() || entity.isSprinting() || entity.isSleeping()
			|| entity.isFallFlying() || entity.isUsingItem() || state.swingAnimation > 0.0F || state.walkAnimationSpeed > WALK_MAX
			|| Downed.isDowned(entity)) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		return !(minecraft.player != null && entity.getId() == minecraft.player.getId() && minecraft.options.getCameraType().isFirstPerson()
			&& FirstPersonCompat.bodyShown());
	}

	private static long nanos(float seconds) {
		return (long)(seconds * 1.0E9F);
	}
}
