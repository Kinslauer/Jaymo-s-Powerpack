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
 * <p>
 * Andy (2026-11-11): "can every mob type get its own set of at least 2 downed animations?" Each kind of body
 * ({@link Kind}) now has two or more ways to lie ({@link Style}), one picked for each mob (by its id, so it keeps it):
 * a person crawls face down or shuffles along on its back, or lies on its back or curled on its side; a machine lies on
 * its back shorting out or sits slumped, powered down. Animals, bugs, birds, fish, slimes and the rest are
 * {@link DownedBodies}'.
 * <p>
 * Andy (2026-11-12): "downed enemies should react to the executions". While a finisher or one of the symbiote's
 * executions plays on it, the body braces, is driven into the ground by each blow, goes rigid on a blade or fights a
 * strangle ({@link ExecutionReact}), in place of its sit-ups.
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
	 * (0.19.32) A downed machine shorting out ({@code Malfunction}): a fault every this many ticks (give or take), how long
	 * it lasts (ticks), how often its limbs snap to a new angle in one (ticks), how far they snap (radians), how far the
	 * whole body jolts (degrees), and the faint tremor between faults (radians).
	 */
	static final float GLITCH_EVERY = 45.0F, GLITCH_TICKS = 12.0F, GLITCH_STEP = 2.0F, GLITCH_LIMBS = 0.7F, GLITCH_JOLT = 4.0F,
		GLITCH_TREMOR = 0.03F;
	/** Of crawling people, the share that shuffle along on their backs (the rest crawl face down); of the lying, the share curled on their side. */
	static final float SHUFFLE_SHARE = com.jaymospowers.core.wounds.DownedLie.SHUFFLE_SHARE, CURL_SHARE = com.jaymospowers.core.wounds.DownedLie.CURL_SHARE;
	/** Curled on its side: how far off the ground (share of its height), the slow rock (degrees, per tick). */
	static final float CURL_LIFT = 0.09F, CURL_ROCK = 3.0F, CURL_ROCK_RATE = 0.05F;
	/** Shuffling on its back: the stroke (radians a tick still, per unit of walk when moving), the hips' scoot (blocks). */
	static final float SHUFFLE_STROKE = 0.06F, SHUFFLE_STRIDE = 0.45F, SHUFFLE_SURGE = 0.08F;
	/** A machine sat slumped: how far it sinks (share of its height, its legs out flat ahead), how far it folds forward (radians). */
	static final float SLUMP_SINK = 0.4F, SLUMP_FOLD = 0.5F;

	/** What kind of body is down, for how it lies. */
	enum Kind {
		PERSON, MACHINE, BEAST, BUG, WINGED, FISH, BLOB, LUMP
	}

	/**
	 * How it lies. People: {@code CRAWL} face down, {@code SHUFFLE} on the back (crawlers), {@code BACK}, {@code CURL} on
	 * the side (the rest). Machines: {@code BACK}, {@code SLUMP} (built like a person), else {@code TOPPLE} or
	 * {@code FACE}. The rest: see {@link DownedBodies}.
	 */
	enum Style {
		CRAWL, SHUFFLE, BACK, CURL, SLUMP, SIDE, SPRAWL, FLIPPED, SPLAYED, FALLEN, SPREAD, FLOP, BELLY_UP, MELT, SHUDDER, TOPPLE, FACE
	}

	/**
	 * This frame's downed pose: {@code amount} of the fall (0-1, a little over in the bounce), {@code crawls}, a
	 * {@code person}, {@code broken} limbs ({@link Dismember} bits), {@code down} ticks since it went down, the {@code id},
	 * and what a finisher is doing to it just now ({@code react}, see {@link ExecutionReact}), if one is.
	 */
	record Down(float amount, boolean crawls, boolean person, int broken, float down, int id, boolean out, Kind kind, Style style,
		ExecutionReact.@org.jspecify.annotations.Nullable React react) {
		boolean machine() {
			return this.kind == Kind.MACHINE;
		}
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
		if (com.jaymospowers.compat.KingdomsCompat.grabbed(entity)) {
			// (0.21.33) held up by the throat in a vampire's hand (Kingdoms' BloodGrab): it hangs there, posed by Kingdoms, not laid down
			return;
		}
		float down = entity.level().getGameTime() + partialTicks - Downed.since(entity);
		boolean person = BodyPlan.of(entity) == BodyPlan.HUMANOID;
		boolean machine = com.jaymospowers.core.wounds.Malfunction.is(entity);
		boolean crawls = Downed.crawls(entity);
		Kind kind = kind(entity, person, machine);
		Down pose = new Down(fall(down), crawls, person, Wounds.brokenLimbs(entity), Math.max(0.0F, down), entity.getId(),
			Downed.passedOut(entity), kind, style(kind, person, crawls, entity.getId(), entity.isInWater()),
			ExecutionReact.of(entity, partialTicks));
		state.setData(KEY, pose);
		if (pose.react() != null && !person) {
			// being finished: an animal, a bug, a bird kicks with its own legs under the blows, however it lies
			float thrash = ExecutionReact.thrash(pose.react());
			if (thrash > 0.0F) {
				state.walkAnimationPos = state.ageInTicks * PADDLE_RATE;
				state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, ExecutionReact.THRASH_SWING * thrash);
			}
		}
		if (pose.style() == Style.SIDE || pose.style() == Style.FALLEN || pose.style() == Style.FLIPPED) {
			// (0.15.142) an animal on its side paddles its legs in its fits (its own walk, run in the air); a bird kicks, a
			// spider on its back scrabbles
			float paddle = flail(pose);
			if (paddle > 0.0F) {
				state.walkAnimationPos = state.ageInTicks * PADDLE_RATE;
				state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, PADDLE_SWING * paddle);
			}
		}
	}

	/** The kind of body: a machine, a person, a bug, a bird, anything that lives in water, a slime, a beast, or a lump. */
	static Kind kind(LivingEntity entity, boolean person, boolean machine) {
		if (machine) {
			return Kind.MACHINE;
		}
		if (person) {
			return Kind.PERSON;
		}
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == BodyPlan.ARTHROPOD) {
			return Kind.BUG;
		}
		if (plan == BodyPlan.FLYER) {
			return Kind.WINGED;
		}
		net.minecraft.world.entity.MobCategory category = entity.getType().getCategory();
		if (category == net.minecraft.world.entity.MobCategory.WATER_CREATURE || category == net.minecraft.world.entity.MobCategory.WATER_AMBIENT
			|| category == net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE) {
			return Kind.FISH;
		}
		if (plan == BodyPlan.SIMPLE) {
			return entity instanceof net.minecraft.world.entity.monster.cubemob.Slime ? Kind.BLOB : Kind.LUMP;
		}
		return Kind.BEAST;
	}

	/** How this one lies: picked by its id (so it keeps it), a crawler's to suit crawling, a fish in water belly up. */
	static Style style(Kind kind, boolean person, boolean crawls, int id, boolean wet) {
		boolean second = pick(id) < 0.5F;
		return switch (kind) {
			case PERSON -> crawls ? (pick(id) < SHUFFLE_SHARE ? Style.SHUFFLE : Style.CRAWL) : (pick(id) < CURL_SHARE ? Style.CURL : Style.BACK);
			case MACHINE -> person ? (second ? Style.SLUMP : Style.BACK) : (second ? Style.FACE : Style.TOPPLE);
			case BEAST -> crawls || second ? Style.SPRAWL : Style.SIDE;
			case BUG -> crawls || second ? Style.SPLAYED : Style.FLIPPED;
			case WINGED -> crawls || second ? Style.SPREAD : Style.FALLEN;
			case FISH -> wet || second ? Style.BELLY_UP : Style.FLOP;
			case BLOB -> second ? Style.SHUDDER : Style.MELT;
			case LUMP -> second ? Style.FACE : Style.TOPPLE;
		};
	}

	/** A fixed value (0-1) for this body, the same every time it goes down. */
	static float pick(int id) {
		return com.jaymospowers.core.wounds.DownedLie.pick(id);
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
		float width = state.boundingBoxWidth / Math.max(entityScale, 0.01F);
		if (pose.react() != null) {
			// a finisher's blow drives the whole body into the ground and it bounces back off it (any model, however it's built)
			float press = ExecutionReact.press(pose.react()) * Math.min(1.5F, height / ExecutionReact.FULL_HEIGHT) * flat;
			if (press != 0.0F) {
				poseStack.translate(0.0F, press, 0.0F);
			}
			if (!pose.person()) {
				float rock = ExecutionReact.rock(pose.react(), pose.id()) * flat;
				if (rock != 0.0F) {
					poseStack.rotate(Axis.ZP.rotationDegrees(rock));
				}
			}
		}
		if (pose.style() == Style.SLUMP) {
			// (2026-11-11) sat down where it stood, legs out ahead, folded forward over them
			poseStack.translate(0.0F, -height * SLUMP_SINK * flat, 0.0F);
		} else if (pose.style() == Style.CURL) {
			// (2026-11-11) on its side, curled up, rocking a little
			float side = com.jaymospowers.core.wounds.DownedLie.curlSide(pose.id());
			poseStack.translate(side * height * 0.5F * flat, height * CURL_LIFT * flat, 0.0F);
			float rock = pose.out() ? 0.0F : CURL_ROCK * Mth.sin(state.ageInTicks * CURL_ROCK_RATE + pose.id()) * flat;
			poseStack.rotate(Axis.ZP.rotationDegrees(side * (SIDE_ROLL * amount + rock)));
		} else if (!pose.person()) {
			// (2026-11-11) an animal, a bug, a bird, a fish, a slime, a lump, a machine not built like a person: its own
			DownedBodies.rotate(state, poseStack, pose, height, width, amount, flat);
		} else if (pose.style() == Style.CRAWL) {
			// face down, the body along the ground ahead of its feet, rocking with the stroke
			poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * flat, FACE_DOWN_LIFT * flat);
			float rock = CRAWL_ROCK * Mth.sin(phase(state, pose)) * moving(state) * (pose.broken() == 0 ? 1.0F : CrawlPose.HURT_ROCK) * flat;
			poseStack.rotate(Axis.YP.rotationDegrees(rock));
		} else {
			// on its back, fallen backwards
			poseStack.rotate(Axis.XP.rotationDegrees(90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * flat, -ON_BACK_LIFT * flat);
			if (pose.style() == Style.SHUFFLE) {
				// (2026-11-11) the hips scoot toward the heels with each push, then the legs slide out again
				poseStack.translate(0.0F, -SHUFFLE_SURGE * scoot(state, pose) * moving(state) * flat, 0.0F);
			}
		}
		if (pose.machine()) {
			// (0.19.32) a fault jolts the whole frame in hard little jerks (any model, however it's built)
			float glitch = glitch(pose);
			if (glitch > 0.0F) {
				float t = (float) Math.floor(state.ageInTicks / GLITCH_STEP);
				poseStack.rotate(Axis.ZP.rotationDegrees(GLITCH_JOLT * glitch * jerk(pose.id(), t, 7)));
				poseStack.rotate(Axis.XP.rotationDegrees(GLITCH_JOLT * 0.5F * glitch * jerk(pose.id(), t, 11)));
			}
		}
	}

	/** Right after a model was posed for this state (the body, and each armour piece). */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Down pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		if (!pose.person()) {
			// (2026-11-11) animals, bugs, birds and the rest: their own legs and wings, found by name
			DownedBodies.apply(model, living, pose);
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
		// ((0.15.142) stiffer while it thrashes, so the beats aren't smoothed away; and while a finisher's blows land on it)
		track.apply(m, f, pose.machine() || pose.react() != null ? 0.8F + FLAIL_STIFFNESS * 2.0F
			: 0.8F + FLAIL_STIFFNESS * (pose.crawls() ? 0.0F : flail(pose)));
		if (pose.style() == Style.BACK && !pose.machine() && pose.react() == null) {
			// now and then it tries to sit up (the upper body lifting at the hips) and sinks back
			PoseBlend.bend(m, RISE_PITCH * rise(living, pose) * Math.min(1.0F, pose.amount()), 0.0F, 0.0F, 0.0F, 0.0F);
		}
	}

	/** The frame: crawling or lying, come with the fall (as it goes down the limbs go from standing to lying). */
	private static void pose(PoseBlend.Frame f, LivingEntityRenderState living, Down pose) {
		switch (pose.style()) {
			case SLUMP -> slump(f, living, pose);
			case CRAWL -> CrawlPose.stroke(f, phase(living, pose), moving(living), pose.broken(), true);
			case SHUFFLE -> shuffle(f, living, pose);
			case CURL -> curl(f, living, pose);
			default -> {
				if (pose.machine()) {
					shortOut(f, living, pose);
				} else {
					lie(f, living, pose);
				}
			}
		}
		if (pose.react() != null && !pose.machine()) {
			// under a finisher: braced for it, jolted by each blow, fighting a hold
			ExecutionReact.limbs(f, pose.react(), pose.style() != Style.CRAWL, pose.broken(), living.ageInTicks, pose.id());
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
	 * (2026-11-11) Curled on its side: knees drawn up, both arms wrapped over the middle, head tucked down, breathing hard.
	 * In its fits it shudders and draws up tighter; passed out, it uncurls a little and lies slack.
	 */
	private static void curl(PoseBlend.Frame f, LivingEntityRenderState state, Down pose) {
		float age = state.ageInTicks;
		float slack = pose.out() ? 0.5F : 1.0F;
		float breath = pose.out() ? 0.0F
			: (Mth.sin(age * BREATH_RATE) * 0.6F + Mth.sin(age * BREATH_RATE_2 + 1.3F) * 0.4F) * BREATH_DEPTH;
		float fit = flail(pose);
		int broken = pose.broken();
		float tight = slack + fit * 0.35F;
		f.set(PoseBlend.RIGHT_ARM, (broken & Dismember.RIGHT_ARM) != 0 ? -0.3F : -1.0F * tight + breath, 0.0F,
			(broken & Dismember.RIGHT_ARM) != 0 ? 0.1F : -0.5F * tight);
		f.set(PoseBlend.LEFT_ARM, (broken & Dismember.LEFT_ARM) != 0 ? -0.3F : -0.85F * tight - breath * 0.5F, 0.0F,
			(broken & Dismember.LEFT_ARM) != 0 ? -0.1F : 0.45F * tight);
		f.set(PoseBlend.RIGHT_LEG, (broken & Dismember.RIGHT_LEG) != 0 ? -0.2F : -1.15F * tight + breath * 0.3F, 0.0F, 0.06F);
		f.set(PoseBlend.LEFT_LEG, (broken & Dismember.LEFT_LEG) != 0 ? -0.2F : -0.9F * tight, 0.0F, -0.06F);
		f.set(PoseBlend.HEAD, 0.45F * tight - breath, 0.15F, 0.0F);
		f.set(PoseBlend.HIP_PITCH, 0.3F * tight);
		if (fit > 0.0F) {
			// a shudder through it
			float shiver = Mth.sin(age * 2.3F + pose.id()) * 0.08F * fit;
			f.shake(PoseBlend.RIGHT_ARM, shiver);
			f.shake(PoseBlend.LEFT_ARM, -shiver);
			f.shake(PoseBlend.HEAD, shiver * 0.6F);
		}
	}

	/**
	 * (2026-11-11) Shuffling along on its back, feet first: one heel dug in ahead then the other, the hands pushing beside
	 * the hips, the head raised to see where it's going (the scoot itself: {@link #rotate}). A broken leg lies straight.
	 */
	private static void shuffle(PoseBlend.Frame f, LivingEntityRenderState state, Down pose) {
		float u = state.ageInTicks * SHUFFLE_STROKE + state.walkAnimationPos * SHUFFLE_STRIDE + (pose.id() & 0xFF);
		float moving = moving(state);
		int broken = pose.broken();
		float push = Mth.sin(u * Mth.TWO_PI);
		if ((broken & Dismember.RIGHT_LEG) == 0) {
			f.set(PoseBlend.RIGHT_LEG, -0.55F - 0.35F * Math.max(0.0F, push) * moving, 0.0F, 0.1F);
		}
		if ((broken & Dismember.LEFT_LEG) == 0) {
			f.set(PoseBlend.LEFT_LEG, -0.55F - 0.35F * Math.max(0.0F, -push) * moving, 0.0F, -0.1F);
		}
		float hands = 0.25F * scoot(state, pose) * moving;
		if ((broken & Dismember.RIGHT_ARM) == 0) {
			f.set(PoseBlend.RIGHT_ARM, -0.1F - hands, 0.0F, 0.35F);
		}
		if ((broken & Dismember.LEFT_ARM) == 0) {
			f.set(PoseBlend.LEFT_ARM, -0.1F - hands, 0.0F, -0.35F);
		}
		f.set(PoseBlend.HEAD, -0.45F + Mth.sin(u * Mth.TWO_PI * 2.0F) * 0.05F, 0.0F, 0.0F);
	}

	/** (2026-11-11) A shuffle's scoot (0-1): the hips pushed up to the heels, quick, then the legs slid slowly out again. */
	private static float scoot(LivingEntityRenderState state, Down pose) {
		float u = state.ageInTicks * SHUFFLE_STROKE + state.walkAnimationPos * SHUFFLE_STRIDE + (pose.id() & 0xFF);
		float t = u - (float) Math.floor(u);
		return t < 0.3F ? PoseBlend.smooth(t / 0.3F) : 1.0F - PoseBlend.smooth((t - 0.3F) / 0.7F);
	}

	/**
	 * (2026-11-11) A machine sat slumped, powered down: legs out straight ahead, folded forward over them, arms hanging to
	 * the ground, the head dropped. A fault jerks the limbs to new angles and snaps the head up, as on its back.
	 */
	private static void slump(PoseBlend.Frame f, LivingEntityRenderState state, Down pose) {
		float age = state.ageInTicks;
		int id = pose.id();
		float tremor = pose.out() ? 0.0F : GLITCH_TREMOR;
		f.set(PoseBlend.RIGHT_LEG, -1.45F, 0.25F, 0.0F);
		f.set(PoseBlend.LEFT_LEG, -1.45F, -0.25F, 0.0F);
		f.set(PoseBlend.RIGHT_ARM, -0.25F + Mth.sin(age * 2.7F + id) * tremor, 0.0F, 0.12F);
		f.set(PoseBlend.LEFT_ARM, -0.25F + Mth.sin(age * 3.1F + id * 3) * tremor, 0.0F, -0.12F);
		f.set(PoseBlend.HEAD, 0.75F, 0.2F, 0.0F);
		f.set(PoseBlend.HIP_PITCH, SLUMP_FOLD);
		float glitch = glitch(pose);
		if (glitch <= 0.0F) {
			return;
		}
		float t = (float) Math.floor(age / GLITCH_STEP);
		int broken = pose.broken();
		float g = GLITCH_LIMBS;
		if ((broken & Dismember.RIGHT_ARM) == 0) {
			f.mix(PoseBlend.RIGHT_ARM, -0.8F + jerk(id, t, 1) * g, 0.0F, 0.3F + jerk(id, t, 2) * g, glitch);
		}
		if ((broken & Dismember.LEFT_ARM) == 0) {
			f.mix(PoseBlend.LEFT_ARM, -0.8F + jerk(id, t, 3) * g, 0.0F, -0.3F + jerk(id, t, 4) * g, glitch);
		}
		f.mix(PoseBlend.HEAD, -0.2F + jerk(id, t, 8) * 0.4F, jerk(id, t, 9), jerk(id, t, 10) * 0.3F, glitch);
		f.set(PoseBlend.HIP_PITCH, SLUMP_FOLD * (1.0F - glitch * 0.6F));
	}

	/**
	 * (0.19.32) A machine on its back: no breath and no trying to sit up, its limbs locked where they fell, arms out and
	 * legs straight, a faint tremor running through them. In a fault each limb snaps to a new angle every
	 * {@link #GLITCH_STEP} ticks (held, not swung: a seizing servo) and the head twitches round.
	 */
	private static void shortOut(PoseBlend.Frame f, LivingEntityRenderState state, Down pose) {
		float age = state.ageInTicks;
		int id = pose.id();
		float tremor = pose.out() ? 0.0F : GLITCH_TREMOR;
		f.set(PoseBlend.RIGHT_ARM, -0.2F + Mth.sin(age * 2.7F + id) * tremor, 0.0F, 0.6F);
		f.set(PoseBlend.LEFT_ARM, -0.2F + Mth.sin(age * 3.1F + id * 3) * tremor, 0.0F, -0.6F);
		f.set(PoseBlend.RIGHT_LEG, 0.0F, 0.0F, 0.1F);
		f.set(PoseBlend.LEFT_LEG, 0.0F, 0.0F, -0.1F);
		f.set(PoseBlend.HEAD, 0.0F, 0.4F, 0.0F);
		float glitch = glitch(pose);
		if (glitch <= 0.0F) {
			return;
		}
		float t = (float) Math.floor(age / GLITCH_STEP);
		int broken = pose.broken();
		float g = GLITCH_LIMBS;
		if ((broken & Dismember.RIGHT_ARM) == 0) {
			f.mix(PoseBlend.RIGHT_ARM, -0.9F + jerk(id, t, 1) * g, 0.0F, 0.6F + jerk(id, t, 2) * g, glitch);
		}
		if ((broken & Dismember.LEFT_ARM) == 0) {
			f.mix(PoseBlend.LEFT_ARM, -0.9F + jerk(id, t, 3) * g, 0.0F, -0.6F + jerk(id, t, 4) * g, glitch);
		}
		if ((broken & Dismember.RIGHT_LEG) == 0) {
			f.mix(PoseBlend.RIGHT_LEG, -0.3F + jerk(id, t, 5) * g * 0.6F, 0.0F, 0.1F, glitch);
		}
		if ((broken & Dismember.LEFT_LEG) == 0) {
			f.mix(PoseBlend.LEFT_LEG, -0.3F + jerk(id, t, 6) * g * 0.6F, 0.0F, -0.1F, glitch);
		}
		f.mix(PoseBlend.HEAD, jerk(id, t, 8) * 0.4F, jerk(id, t, 9) * 1.0F, jerk(id, t, 10) * 0.3F, glitch);
	}

	/** (0.19.32) A machine's fault (0-1): one as it lands, then every so often, each on its own beat. Never shut down. */
	static float glitch(Down pose) {
		if (pose.out()) {
			return 0.0F;
		}
		float down = pose.down();
		if (down < FALL_TICKS + GLITCH_TICKS * 2.0F) {
			return down < FALL_TICKS ? 0.0F : 1.0F;
		}
		float every = GLITCH_EVERY + (pose.id() & 31);
		float t = (down + (pose.id() * 53 & 0xFF)) % every;
		return t < GLITCH_TICKS ? 1.0F : 0.0F;
	}

	/** (0.19.32) A fixed random value (-1 to 1) for this body, this step of a fault and this joint: the same every frame of the step. */
	static float jerk(int id, float step, int joint) {
		int h = id * 73856093 ^ (int) step * 19349663 ^ joint * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFF) / 32767.5F - 1.0F;
	}

	/**
	 * (0.15.142) A fit (0-1): one long one as it lands, then short ones every so often, each on its own beat. Never while
	 * passed out.
	 */
	static float flail(Down pose) {
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
		if (pose.out() || pose.react() != null || pose.down() < FALL_TICKS + RISE_TICKS) {
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
	static float moving(LivingEntityRenderState state) {
		return Math.min(1.0F, 0.35F + state.walkAnimationSpeed * MOVING_PER_SPEED);
	}
}
