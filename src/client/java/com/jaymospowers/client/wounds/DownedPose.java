package com.jaymospowers.client.wounds;

import com.jaymospowers.client.CrawlPose;
import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Player;

/**
 * (0.14.1, from Kingdoms' 0.7.73) Someone downed ({@link Downed}) on the ground. People ({@link BodyPlan#HUMANOID}): a
 * crawler lies face down and hauls itself along on its forearms, legs dragging, head raised; the rest lie on their backs,
 * one hand clutched to the chest, the other arm flung out, a knee drawn up, head lolled to one side, breathing shallow.
 * Anything else (a downed wolf or horse) lies on its side. Players aren't drawn here: the downed state holds them in
 * {@code Crawl}'s pose ({@code CrawlPose}). Nor is anyone being carried ({@code CarryPose}).
 * <p>
 * (0.15.42) They go down with weight: the knees give, then the body drops faster and faster and bounces a little as it
 * hits. A crawler crawls as a downed player does ({@link CrawlPose#stroke}: arms reaching and pulling in turn, the
 * opposite knee driving, the body rocking, a broken limb dragged). One on its back breathes hard and uneven, a hand
 * clutched to its worst wound (a broken arm or leg, Wounds' breaks being what everyone sees; else the chest), and now and
 * then tries to sit up and sinks back. Their limbs ride springs ({@link PoseBlend}).
 * <p>
 * (0.15.142) Andy (2026-10-23): "Mobs should flail around or crawl or emit noise, when downed as they need some more
 * signifiers to differentiate from being a corpse". One on its back thrashes now and then (arms beating, legs kicking,
 * head tossed), hardest just after it lands, and tries to sit up more often; an animal on its side paddles its legs; one
 * passed out does neither. Whatever it held its arms up for (a crossbow aimed, a bow drawn, a spell) is let go
 * ({@link #calm}), and illagers, whose model isn't a HumanoidModel, are posed too ({@link PoseBlend#parts}): a downed
 * pillager no longer aims its crossbow at the sky. (The groans, and where a crawler crawls: {@code DownedCrawl}.)
 */
public final class DownedPose {
	/** How long the fall to the ground takes (ticks): the knees giving way first (this share of it), then the drop. */
	static final float FALL_TICKS = 9.0F, BUCKLE_SHARE = 0.3F, BUCKLE_AMOUNT = 0.12F;
	/** The bounce as it hits: how far past flat (share of the fall), how fast it rings (per tick), how fast it dies (per tick). */
	static final float BOUNCE = 0.05F, BOUNCE_RING = 1.1F, BOUNCE_DECAY = 0.35F;
	/** How far the body is raised off the ground lying face down and on its back (blocks). */
	static final float FACE_DOWN_LIFT = 0.3F, ON_BACK_LIFT = 0.2F;
	/** An animal on its side: how far over it rolls (degrees). */
	static final float SIDE_ROLL = 90.0F;
	/** A crawler: its stroke (radians a tick, struggling in place, and per unit of the walk cycle when it gets somewhere) and rock (degrees). */
	static final float CRAWL_STROKE = 0.08F, CRAWL_STROKE_STRIDE = 0.35F, CRAWL_ROCK = 6.0F, MOVING_PER_SPEED = 4.0F;
	/** Laboured breathing on its back: beats per tick (two, out of step, so it's uneven) and depth (radians). */
	static final float BREATH_RATE = 0.16F, BREATH_RATE_2 = 0.27F, BREATH_DEPTH = 0.09F;
	/** Trying to sit up: every this many ticks (give or take), how long (ticks), how far up at the hips (radians). */
	static final float RISE_EVERY = 160.0F, RISE_TICKS = 50.0F, RISE_PITCH = 0.55F;
	/**
	 * (0.15.142) Thrashing on its back: every this many ticks (give or take), how long (ticks), how fast (radians a tick),
	 * how far the arms, legs and head swing (radians), and the first, longer one as it lands (ticks).
	 */
	static final float FLAIL_EVERY = 110.0F, FLAIL_TICKS = 26.0F, FLAIL_RATE = 0.8F, FLAIL_ARMS = 0.9F, FLAIL_LEGS = 0.55F, FLAIL_HEAD = 0.5F,
		FIRST_FLAIL_TICKS = 40.0F;
	/** How much stiffer the springs are while it thrashes (so the quick beats aren't smoothed away). */
	static final float FLAIL_STIFFNESS = 2.5F;
	/** (0.15.142) An animal on its side paddling in the same bursts: its legs' pace (walk cycle a tick) and swing (walk speed). */
	static final float PADDLE_RATE = 0.9F, PADDLE_SWING = 0.9F;

	/**
	 * This frame's downed pose: {@code amount} of the fall (0-1, a little over in the bounce), {@code crawls}, a
	 * {@code person}, {@code broken} limbs ({@link Dismember} bits), {@code down} ticks since it went down, the {@code id}.
	 */
	record Down(float amount, boolean crawls, boolean person, int broken, float down, int id, boolean out) {
	}

	static final RenderStateDataKey<Down> KEY = RenderStateDataKey.create(() -> "jaymospowers:downed");

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("downed_mob");

	/** (0.16.16) Whether this body is down (its limbs are DownedPose's, not Touch'n Motion's: see TnmFollow). */
	public static boolean down(Object state) {
		return state instanceof LivingEntityRenderState living && living.getData(KEY) != null;
	}

	private DownedPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Player || !Downed.isDowned(entity) || Carry.isCarried(entity)) {
			return;
		}
		float down = entity.level().getGameTime() + partialTicks - Downed.since(entity);
		boolean person = BodyPlan.of(entity) == BodyPlan.HUMANOID;
		Down pose = new Down(fall(down), Downed.crawls(entity), person, Wounds.brokenLimbs(entity), Math.max(0.0F, down), entity.getId(),
			Downed.passedOut(entity));
		state.setData(KEY, pose);
		if (!person && !pose.crawls()) {
			// (0.15.142) an animal on its side paddles its legs in its fits (its own walk, run in the air)
			float paddle = flail(pose);
			if (paddle > 0.0F) {
				state.walkAnimationPos = state.ageInTicks * PADDLE_RATE;
				state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, PADDLE_SWING * paddle);
			}
		}
	}

	/**
	 * (0.15.142) Just before the model is posed: whatever it held its arms up for is let go (a crossbow aimed, a bow
	 * drawn, a spell, a shield), so nothing lies there aiming at the sky.
	 */
	public static void calm(LivingEntityRenderState state) {
		Down pose = state.getData(KEY);
		// ((0.18.71) and the tripped, face down with their hands out)
		if (pose == null && !TrippedPose.tripped(state)) {
			return;
		}
		if (state instanceof ArmedEntityRenderState armed) {
			armed.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			armed.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isUsingItem = false;
			humanoid.ticksUsingItem = 0.0F;
			humanoid.isCrouching = false;
		}
		if (state instanceof IllagerRenderState illager) {
			illager.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
			illager.isAggressive = false;
			illager.ticksUsingItem = 0.0F;
		}
	}

	/** The fall, {@code down} ticks on: the knees buckling, then dropping faster and faster, a small bounce as it lands. */
	static float fall(float down) {
		float t = Mth.clamp(down / FALL_TICKS, 0.0F, 1.0F);
		if (t < BUCKLE_SHARE) {
			float b = t / BUCKLE_SHARE;
			return BUCKLE_AMOUNT * b * b * (3.0F - 2.0F * b);
		}
		float d = (t - BUCKLE_SHARE) / (1.0F - BUCKLE_SHARE);
		float amount = BUCKLE_AMOUNT + (1.0F - BUCKLE_AMOUNT) * d * d;
		if (t >= 1.0F) {
			float after = down - FALL_TICKS;
			amount = 1.0F + BOUNCE * (float)Math.exp(-after * BOUNCE_DECAY) * Mth.sin(after * BOUNCE_RING);
		}
		return amount;
	}

	/** After the body's been turned to face its way: lay it on the ground. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Down pose = state.getData(KEY);
		if (pose == null) {
			return;
		}
		float amount = pose.amount();
		float flat = Math.min(1.0F, amount);
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		if (!pose.person()) {
			// on its side, along the ground where it stood, raised by half its width so it lies on the ground, not in it
			float width = state.boundingBoxWidth / Math.max(entityScale, 0.01F);
			poseStack.translate(height * 0.5F * flat, width * 0.5F * flat, 0.0F);
			poseStack.rotate(Axis.ZP.rotationDegrees(SIDE_ROLL * amount));
		} else if (pose.crawls()) {
			// face down, the body along the ground ahead of its feet, rocking with the stroke
			poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * flat, FACE_DOWN_LIFT * flat);
			float rock = CRAWL_ROCK * Mth.sin(phase(state, pose)) * moving(state) * (pose.broken() == 0 ? 1.0F : CrawlPose.HURT_ROCK) * flat;
			poseStack.rotate(Axis.YP.rotationDegrees(rock));
		} else {
			// on its back, fallen backwards
			poseStack.rotate(Axis.XP.rotationDegrees(90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * flat, -ON_BACK_LIFT * flat);
		}
	}

	/** Right after a model was posed for this state (the body, and each armour piece). */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Down pose = living.getData(KEY);
		if (pose == null || !pose.person()) {
			return;
		}
		if (!(model instanceof HumanoidModel<?> m)) {
			// (0.15.142) built like a person but not a HumanoidModel (an illager): posed outright, no springs
			ModelPart[] parts = PoseBlend.parts(model);
			if (parts != null) {
				PoseBlend.Frame f = new PoseBlend.Frame(parts);
				pose(f, living, pose);
				f.put(parts, 1.0F);
			}
			return;
		}
		PoseBlend.Track track = BLEND.track(pose.id(), true);
		if (track == null) {
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(m);
		pose(f, living, pose);
		// ((0.15.142) stiffer while it thrashes, so the beats aren't smoothed away)
		track.apply(m, f, 0.8F + FLAIL_STIFFNESS * (pose.crawls() ? 0.0F : flail(pose)));
		if (!pose.crawls()) {
			// now and then it tries to sit up (the upper body lifting at the hips) and sinks back
			PoseBlend.bend(m, RISE_PITCH * rise(living, pose) * Math.min(1.0F, pose.amount()), 0.0F, 0.0F, 0.0F, 0.0F);
		}
	}

	/** The frame: crawling or lying, come with the fall (as it goes down the limbs go from standing to lying). */
	private static void pose(PoseBlend.Frame f, LivingEntityRenderState living, Down pose) {
		if (pose.crawls()) {
			CrawlPose.stroke(f, phase(living, pose), moving(living), pose.broken(), true);
		} else {
			lie(f, living, pose);
		}
		f.scale(Mth.clamp(pose.amount(), 0.0F, 1.0F));
	}

	/** On its back, so "forward" (negative x rotation) is up off the ground. */
	private static void lie(PoseBlend.Frame f, LivingEntityRenderState state, Down pose) {
		float age = state.ageInTicks;
		// hard, uneven breaths: two beats out of step
		float breath = (Mth.sin(age * BREATH_RATE) * 0.6F + Mth.sin(age * BREATH_RATE_2 + 1.3F) * 0.4F) * BREATH_DEPTH;
		int broken = pose.broken();
		float rise = rise(state, pose);
		// a hand to the worst wound: a broken arm (the other hand on it), a broken leg (down to the thigh), else the chest
		if ((broken & Dismember.RIGHT_ARM) != 0) {
			f.set(PoseBlend.LEFT_ARM, -1.05F + breath, -0.35F, 0.75F);
		} else if ((broken & Dismember.LEFT_ARM) != 0) {
			f.set(PoseBlend.RIGHT_ARM, -1.05F + breath, 0.35F, -0.75F);
		} else if ((broken & Dismember.RIGHT_LEG) != 0) {
			f.set(PoseBlend.RIGHT_ARM, -0.35F + breath * 0.5F, 0.0F, -0.2F);
			f.set(PoseBlend.LEFT_ARM, -0.15F, 0.0F, -1.2F - breath * 0.5F);
		} else if ((broken & Dismember.LEFT_LEG) != 0) {
			f.set(PoseBlend.LEFT_ARM, -0.35F + breath * 0.5F, 0.0F, 0.2F);
			f.set(PoseBlend.RIGHT_ARM, -0.15F, 0.0F, 1.2F + breath * 0.5F);
		} else {
			// right hand clutched to the chest, left arm flung out on the ground
			f.set(PoseBlend.RIGHT_ARM, -1.25F + breath, 0.0F, -0.55F);
			f.set(PoseBlend.LEFT_ARM, -0.15F, 0.0F, -1.2F - breath * 0.5F);
		}
		// right knee drawn up, left leg out straight and a little apart (a broken one left lying straight)
		f.set(PoseBlend.RIGHT_LEG, (broken & Dismember.RIGHT_LEG) != 0 ? 0.0F : -0.55F + breath * 0.3F, 0.0F, 0.08F);
		f.set(PoseBlend.LEFT_LEG, 0.0F, 0.0F, -0.18F);
		// head lolled to one side, lifting with each breath; brought round to the front trying to sit up
		f.set(PoseBlend.HEAD, Mth.lerp(rise, 0.1F - breath, 0.35F), Mth.lerp(rise, 0.65F, 0.1F), 0.0F);
		if (rise > 0.0F) {
			// reaching forward to push up (a broken arm doesn't)
			if ((broken & Dismember.RIGHT_ARM) == 0) {
				f.mix(PoseBlend.RIGHT_ARM, -0.7F, 0.0F, 0.15F, rise);
			}
			if ((broken & Dismember.LEFT_ARM) == 0) {
				f.mix(PoseBlend.LEFT_ARM, -0.7F, 0.0F, -0.15F, rise);
			}
			f.shake(PoseBlend.HEAD, Mth.sin(age * 2.1F) * 0.03F * rise);
		}
		// (0.15.142) now and then a fit: arms beating up off the ground, legs kicking, head tossed side to side (a broken
		// limb lies where it is)
		float flail = flail(pose) * (1.0F - rise);
		if (flail > 0.0F) {
			float a = age * FLAIL_RATE + (pose.id() & 15);
			if ((broken & Dismember.RIGHT_ARM) == 0) {
				f.mix(PoseBlend.RIGHT_ARM, -1.1F + Mth.sin(a) * FLAIL_ARMS, 0.0F, -0.35F + Mth.cos(a * 1.3F) * 0.5F, flail);
			}
			if ((broken & Dismember.LEFT_ARM) == 0) {
				f.mix(PoseBlend.LEFT_ARM, -1.0F + Mth.sin(a * 1.15F + 2.1F) * FLAIL_ARMS, 0.0F, 0.35F + Mth.cos(a * 1.1F + 1.0F) * 0.5F, flail);
			}
			if ((broken & Dismember.RIGHT_LEG) == 0) {
				f.mix(PoseBlend.RIGHT_LEG, -0.45F + Mth.sin(a * 1.25F + 1.0F) * FLAIL_LEGS, 0.0F, 0.1F, flail);
			}
			if ((broken & Dismember.LEFT_LEG) == 0) {
				f.mix(PoseBlend.LEFT_LEG, -0.45F + Mth.sin(a * 1.25F + 2.6F) * FLAIL_LEGS, 0.0F, -0.1F, flail);
			}
			f.mix(PoseBlend.HEAD, 0.2F, Mth.sin(a * 0.9F) * FLAIL_HEAD, 0.0F, flail);
		}
	}

	/**
	 * (0.15.142) A fit (0-1): one long one as it lands, then short ones every so often, each on its own beat. Never while
	 * passed out.
	 */
	private static float flail(Down pose) {
		if (pose.out()) {
			return 0.0F;
		}
		float down = pose.down();
		if (down < FALL_TICKS + FIRST_FLAIL_TICKS) {
			return PoseBlend.bump(down, FALL_TICKS, FALL_TICKS + 4.0F, FALL_TICKS + FIRST_FLAIL_TICKS - 12.0F, FALL_TICKS + FIRST_FLAIL_TICKS);
		}
		float every = FLAIL_EVERY + (pose.id() & 31) * 2.0F;
		float t = (down + (pose.id() * 37 & 0xFF)) % every;
		return PoseBlend.bump(t, 0.0F, 4.0F, FLAIL_TICKS - 8.0F, FLAIL_TICKS);
	}

	/** Lying on its back: the try at sitting up (0-1), every so often (each on its own beat), not until it's been down a while. */
	private static float rise(LivingEntityRenderState state, Down pose) {
		if (pose.out() || pose.down() < FALL_TICKS + RISE_TICKS) {
			return 0.0F;
		}
		float every = RISE_EVERY + (pose.id() & 63) * 2.0F;
		float t = (pose.down() + (pose.id() & 0xFF) * 5.0F) % every;
		// up slowly, held a moment, then falling back
		return PoseBlend.bump(t, 0.0F, RISE_TICKS * 0.55F, RISE_TICKS * 0.7F, RISE_TICKS);
	}

	/** A crawler's place in its stroke: a slow struggle, quicker for the ground it covers. */
	private static float phase(LivingEntityRenderState state, Down pose) {
		return state.ageInTicks * CRAWL_STROKE + state.walkAnimationPos * CRAWL_STROKE_STRIDE + (pose.id() & 0xFF);
	}

	/** How much of a crawler's stroke shows: a weak struggle when it's barely moving. */
	private static float moving(LivingEntityRenderState state) {
		return Math.min(1.0F, 0.35F + state.walkAnimationSpeed * MOVING_PER_SPEED);
	}
}
