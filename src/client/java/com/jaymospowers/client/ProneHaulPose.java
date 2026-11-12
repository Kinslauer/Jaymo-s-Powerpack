package com.jaymospowers.client;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.ProneHaul;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How the prone haul up a block looks ({@link ProneHaul}), drawn over the crawl (from {@link CrawlPose}, after the swimming
 * tilt, so the model's Y runs along the body to the head and Z is up off the ground, the feet at the origin).
 *
 * <p>Three ways, by the haul's style:
 * <ul>
 * <li><b>The usual</b>: the chest arches up and both hands reach up onto the top and plant; then the body is dragged up the
 * face, the hips coming up to the edge and the chest folding over it while the legs scrabble at the face, the arms going
 * from reaching to pressing down under the chest, trembling with it; a knee comes up over the edge and the body slides in
 * flat on the top.</li>
 * <li><b>Perfectly healthy</b>: the same, quick and clean: no tremble, hardly a scrabble, a strong knee driven up onto the
 * edge.</li>
 * <li><b>One working arm</b>: the good hand alone reaches and pulls, the body rolled onto that side and the dead arm hanging
 * (Dismembered draws a lost one gone); the legs kick hard; part way up it slips back and has to heave again
 * ({@link ProneHaul#rise}) before it gets over.</li>
 * </ul>
 * Downed, the arms shake with every pull. The body is drawn where the struggle puts it (the feet on the ground, then up the
 * face), wherever the haul's own movement has the player's box: the box goes straight up the face and then in, as the ledge
 * climb's does.
 */
public final class ProneHaulPose {
	/** The reach: the body tipped up off the ground about the feet (degrees) and the chest arched up at the hips (radians). */
	static final float REACH_PITCH = 25.0F, REACH_ARCH = -0.45F;
	/** Up the face: the body tipped up (degrees) and the chest folded forward over the edge at the hips (radians), for a full block. */
	static final float PULL_PITCH = 60.0F, PULL_FOLD = 0.79F;
	/**
	 * Up the face, the feet end this far from the face (blocks) and this high (share of the rise); the reach scoots them
	 * this far ahead.
	 */
	static final float FEET_FROM_FACE = 0.45F, FEET_UP = 0.09F, REACH_SCOOT = 0.2F;
	/** The arms: reaching onto the top this far below pointing level ahead (radians), pressing this far back of straight down. */
	static final float REACH_DIP = 0.35F, PRESS_BACK = 0.25F;
	/** The arms spread out to the sides (radians): reaching, and pressing. */
	static final float REACH_OUT = 0.3F, PRESS_OUT = 0.15F;
	/** Sliding in, the arms end pushing back under the chest (radians). */
	static final float SLIDE_ARMS = -0.7F;
	/** The legs scrabbling at the face (radians each way) and how fast (per tick), by style: healthy, usual, one-armed. */
	static final float[] SCRABBLE = {0.12F, 0.4F, 0.6F};
	static final float[] SCRABBLE_RATE = {0.9F, 1.1F, 1.5F};
	/** The knee driven up onto the edge (radians), by style. */
	static final float[] KNEE_UP = {1.2F, 0.9F, 1.0F};
	/** The arms' tremble with the effort (radians), by style; downed, this much more. */
	static final float[] TREMBLE = {0.0F, 0.03F, 0.05F};
	static final float DOWNED_TREMBLE = 0.06F;
	/** One-armed, the body rolled onto the good side (degrees). */
	static final float ONE_ARM_ROLL = 12.0F;
	/** The dead arm, hanging by the side (radians). */
	static final float DEAD_ARM_X = -0.25F, DEAD_ARM_OUT = 0.2F;
	/** The head looks ahead over the top (radians from looking along the ground), and strains down this much on the pull. */
	static final float HEAD_LOOK = -1.3F, HEAD_STRAIN = 0.25F;

	/**
	 * A frame of a haul: its style, how far through ({@code p}), how far the body's box has gone along the face's way and up
	 * from where it started ({@code along}, {@code up}), the rise's height and the slide's length ({@code height},
	 * {@code depth}), the face's distance from the body's middle at the start ({@code wall}), ticks for the shakes, the broken
	 * limbs and the lost ones ({@link Dismember} bits), and whether downed.
	 */
	public record Frame(int style, float p, float along, float up, float height, float depth, float wall, float age, int broken,
		int missing, boolean downed) {
	}

	public static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "jaymospowers:prone_haul");

	private ProneHaulPose() {
	}

	/** From {@link CrawlPose#extract}: this frame of the player's haul, or none. Turns the body square to the face too. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Player player = entity instanceof Player p ? p : null;
		boolean local = player != null && player == Minecraft.getInstance().player;
		ProneHaul.Haul haul = player == null ? null : local ? ProneHaulClient.localHaul(Minecraft.getInstance().player)
			: player.getAttached(ProneHaul.HAUL);
		if (haul == null) {
			state.setData(KEY, null);
			return;
		}
		float p = local ? ProneHaulClient.localProgress(partialTicks) : haul.progress(player.level().getGameTime(), partialTicks);
		Direction face = haul.direction();
		Vec3 off = player.getPosition(partialTicks).subtract(haul.from());
		double along = off.x * face.getStepX() + off.z * face.getStepZ();
		Vec3 slide = haul.to().subtract(haul.from());
		float depth = (float) Math.abs(slide.x * face.getStepX() + slide.z * face.getStepZ());
		state.setData(KEY, new Frame(haul.style(), p, (float) along, (float) off.y, (float) slide.y, depth, haul.wall(),
			player.tickCount + partialTicks, Wounds.brokenLimbs(player), Dismember.missing(player), Downed.isDowned(player)));
		// square to the face, the head turned on the neck as it was
		float turn = Mth.wrapDegrees(face.toYRot() - state.bodyRot);
		state.bodyRot += turn;
		state.yRot = Mth.wrapDegrees(state.yRot - turn);
	}

	/** The shape of the body at this frame: tipped up (degrees), folded at the hips (radians), and the feet's place (blocks). */
	private record Shape(float pitch, float fold, float feetAhead, float feetUp, float roll) {
	}

	private static Shape shape(Frame f) {
		float q = ProneHaul.reach(f.style(), f.p()), r = ProneHaul.rise(f.style(), f.p()), c = ProneHaul.across(f.style(), f.p());
		float k = Mth.clamp(f.height(), 0.5F, 1.1F);
		float reach = q * (1.0F - r);
		float pitch = (REACH_PITCH * reach + PULL_PITCH * k * r) * (1.0F - c);
		float fold = (REACH_ARCH * reach + PULL_FOLD * k * r) * (1.0F - c);
		float pulled = 1.0F - FEET_FROM_FACE + f.wall();
		float feetAhead = (REACH_SCOOT * reach + pulled * r) * (1.0F - c) + f.depth() * c;
		float feetUp = FEET_UP * k * r * (1.0F - c) + f.height() * c;
		float roll = 0.0F;
		if (f.style() == ProneHaul.ONE_HAND) {
			roll = (rightGood(f) ? 1.0F : -1.0F) * ONE_ARM_ROLL * (0.6F * reach + r * (1.0F - c));
		}
		return new Shape(pitch, fold, feetAhead, feetUp, roll);
	}

	private static boolean rightGood(Frame f) {
		return ((f.broken() | f.missing()) & Dismember.RIGHT_ARM) == 0;
	}

	private static boolean leftGood(Frame f) {
		return ((f.broken() | f.missing()) & Dismember.LEFT_ARM) == 0;
	}

	/**
	 * From {@link CrawlPose#rotate}, after the swimming tilt: the body moved to where the struggle has it (the feet on the
	 * ground and dragged up the face, whatever the box is doing), rolled and tipped up about the feet. False with no haul.
	 */
	public static boolean rotate(AvatarRenderState state, PoseStack poseStack) {
		Frame f = state.getData(KEY);
		if (f == null) {
			return false;
		}
		Shape s = shape(f);
		float scale = state.scale > 0.0F ? state.scale : 1.0F;
		poseStack.translate(0.0F, (s.feetAhead() - f.along()) / scale, (s.feetUp() - f.up()) / scale);
		if (Math.abs(s.roll()) > 1.0E-3F) {
			poseStack.rotateDegrees(Axis.YP, s.roll());
		}
		poseStack.rotateDegrees(Axis.XP, s.pitch());
		return true;
	}

	/** From {@link CrawlPose#applyModel}: the limbs of the struggle on {@code frame}. False with no haul. */
	public static boolean pose(AvatarRenderState state, PoseBlend.Frame frame) {
		Frame f = state.getData(KEY);
		if (f == null) {
			return false;
		}
		Shape s = shape(f);
		int style = Mth.clamp(f.style(), 0, 2);
		float r = ProneHaul.rise(style, f.p()), c = ProneHaul.across(style, f.p());
		float pull = r * (1.0F - c);
		// the chest's angle up off the ground, the fold at the hips taken off the body's tip
		float alpha = s.pitch() * Mth.DEG_TO_RAD - s.fold();
		float reachX = -Mth.PI + alpha + REACH_DIP;
		float pressX = -Mth.HALF_PI + alpha + PRESS_BACK;
		float armX = Mth.lerp(c, Mth.lerp(r, reachX, pressX), SLIDE_ARMS);
		float armOut = Mth.lerp(r, REACH_OUT, PRESS_OUT);
		boolean right = rightGood(f), left = leftGood(f);
		if (style == ProneHaul.ONE_HAND) {
			// the good hand does it all; the other hangs dead by the side
			setArm(frame, true, right ? armX : DEAD_ARM_X, right ? armOut : DEAD_ARM_OUT);
			setArm(frame, false, left && !right ? armX : DEAD_ARM_X, left && !right ? armOut : DEAD_ARM_OUT);
		} else {
			setArm(frame, true, armX, armOut);
			setArm(frame, false, armX, armOut);
		}
		float tremble = (TREMBLE[style] + (f.downed() ? DOWNED_TREMBLE : 0.0F)) * pull * Mth.sin(f.age() * 2.4F);
		if (tremble != 0.0F) {
			frame.shake(PoseBlend.RIGHT_ARM, tremble);
			frame.shake(PoseBlend.LEFT_ARM, -tremble * 0.8F);
		}
		frame.set(PoseBlend.HIP_PITCH, s.fold());
		frame.set(PoseBlend.HEAD, HEAD_LOOK + alpha + HEAD_STRAIN * pull, 0.0F, 0.0F);
		// the legs scrabble at the face while the body goes up it, then a knee comes up over the edge
		float end = ProneHaul.pullEnd(style);
		float scrabble = SCRABBLE[style] * PoseBlend.bump(f.p(), 0.1F, 0.25F, end - 0.05F, end + 0.05F);
		float kick = Mth.sin(f.age() * SCRABBLE_RATE[style]) * scrabble;
		float knee = KNEE_UP[style] * PoseBlend.bump(f.p(), end - 0.12F, end, end + 0.1F, 1.0F);
		boolean rightKnee = style != ProneHaul.NORMAL;
		frame.set(PoseBlend.RIGHT_LEG, kick - (rightKnee ? knee : 0.0F), 0.0F, 0.12F);
		frame.set(PoseBlend.LEFT_LEG, -kick - (rightKnee ? 0.0F : knee), 0.0F, -0.12F);
		return true;
	}

	private static void setArm(PoseBlend.Frame frame, boolean right, float x, float out) {
		frame.set(right ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM, x, 0.0F, right ? out : -out);
	}
}
