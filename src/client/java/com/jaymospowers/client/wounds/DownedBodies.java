package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.wounds.DownedPose.Down;
import com.jaymospowers.client.wounds.DownedPose.Style;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "can every mob type get its own set of at least 2 downed animations?" How everything not built like a
 * person lies downed ({@link DownedPose} draws people and machines built like them), two ways for each kind of body, one
 * picked for each mob ({@link DownedPose#style}):
 * <ul>
 * <li><b>Beasts</b> (wolves, horses, cows, bears): {@code SIDE}, on its side paddling its legs in its fits and lifting
 * its head; {@code SPRAWL}, belly down with the forelegs out ahead and the hind legs splayed back, the head on the ground,
 * now and then pushing its front up on its forelegs and sinking back. A crawling beast drags itself on its forelegs.</li>
 * <li><b>Bugs</b> (spiders, silverfish, bees): {@code FLIPPED}, on its back, legs curled up over the belly and twitching,
 * scrabbling and rocking in its fits to right itself; {@code SPLAYED}, flat on its belly, legs spread out flat, one and
 * another jerking. A crawler hauls itself on its front legs.</li>
 * <li><b>Birds and other flyers</b> (chickens, parrots, bats, phantoms): {@code FALLEN}, on its side, one wing beating
 * feebly in bursts, the other pinned under it, legs drawn up; {@code SPREAD}, belly down, wings spread flat, beating at
 * the ground in its fits with the body hopping.</li>
 * <li><b>Fish and other water life</b>: {@code FLOP}, on its side, flopping and hopping in bursts that weaken as it
 * goes; {@code BELLY_UP}, rolled over, bobbing and drifting in water. Always belly up in water.</li>
 * <li><b>Slimes</b>: {@code MELT}, sagged flat and quivering; {@code SHUDDER}, squat, hiccuping with failed hops.</li>
 * <li><b>Lumps</b> (golems, creepers, blazes) and machines not built like a person: {@code TOPPLE}, on its side,
 * shuddering in its fits; {@code FACE}, pitched forward onto its front.</li>
 * </ul>
 * Everything is a turn of the whole body first (so a modded model we don't know still lies right), and then the legs,
 * wings, head and tail where the model has them under vanilla's names. Passed out, it lies still.
 */
final class DownedBodies {
	/** How far a body sinks lying on its belly (share of its height): a beast's legs folded flat, a bug's, a bird's. */
	static final float BEAST_SINK = 0.42F, BUG_SINK = 0.25F, BIRD_SINK = 0.25F;
	/** A beast sprawled pushing its front up: how far (degrees), every this many ticks (give or take), how long (ticks). */
	static final float PUSH_UP = 14.0F, PUSH_EVERY = 150.0F, PUSH_TICKS = 45.0F;
	/** A crawling beast's or bug's stroke (radians a tick still, per unit of walk). */
	static final float DRAG_STROKE = 0.12F, DRAG_STRIDE = 0.5F;
	/** A bug on its back: how far the legs curl in (radians), how much it rocks trying to right itself (degrees). */
	static final float CURL_LEGS = 0.55F, BUG_ROCK = 12.0F, FLAT_LEGS = 0.6F;
	/** A bird: the free wing's beat (radians, per tick), the wings spread flat (radians), its hop in a fit (blocks). */
	static final float WING_BEAT = 0.9F, WING_RATE = 1.6F, WING_SPREAD = 1.3F, BIRD_HOP = 0.08F;
	/** A fish out of water: a flop every this many ticks (give or take), how long (ticks), its hop (blocks), its roll (degrees), weaker over this long (ticks). */
	static final float FLOP_EVERY = 45.0F, FLOP_TICKS = 18.0F, FLOP_HOP = 0.16F, FLOP_ROLL = 28.0F, FLOP_TIRE = 1200.0F;
	/** A fish belly up in water: its bob (blocks, per tick) and drift (degrees). */
	static final float BOB = 0.04F, BOB_RATE = 0.08F, DRIFT = 10.0F;
	/** A slime melted flat: how much it spreads and sags, its quiver; one shuddering: squat, a hiccup every this many ticks. */
	static final float MELT_SPREAD = 0.35F, MELT_SAG = 0.55F, QUIVER = 0.05F, SQUAT = 0.22F, HICCUP_EVERY = 50.0F, HICCUP_TICKS = 10.0F;
	/** A lump shuddering in its fits (degrees). */
	static final float LUMP_SHUDDER = 3.0F;

	private DownedBodies() {
	}

	/** After the body's been turned to face its way: lay it down its way. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack, Down pose, float height, float width, float amount, float flat) {
		float age = state.ageInTicks;
		float fit = DownedPose.flail(pose);
		switch (pose.style()) {
			case SPRAWL -> {
				Float sink = SINK.get(state.entityType);
				poseStack.translate(0.0F, -height * (sink == null ? BEAST_SINK : sink) * flat, 0.0F);
				float push = pushUp(pose);
				if (push > 0.0F) {
					// the front pushed up off the ground about the hind end
					float back = width * 0.5F;
					poseStack.translate(0.0F, 0.0F, back);
					poseStack.rotate(Axis.XP.rotationDegrees(PUSH_UP * push));
					poseStack.translate(0.0F, 0.0F, -back);
				}
			}
			case SPLAYED -> poseStack.translate(0.0F, -height * BUG_SINK * flat, 0.0F);
			case SPREAD -> {
				float hop = BIRD_HOP * fit * Math.abs(Mth.sin(age * WING_RATE * 0.5F));
				poseStack.translate(0.0F, -height * BIRD_SINK * flat + hop, 0.0F);
			}
			case FLIPPED, BELLY_UP -> {
				// rolled over about its middle, belly to the sky
				float mid = height * 0.5F;
				float bob = 0.0F, drift = 0.0F;
				if (pose.style() == Style.BELLY_UP && !pose.out()) {
					bob = BOB * Mth.sin(age * BOB_RATE + pose.id());
					drift = DRIFT * Mth.sin(age * BOB_RATE * 0.37F + pose.id());
				}
				float rock = pose.style() == Style.FLIPPED ? BUG_ROCK * fit * Mth.sin(age * 0.45F + pose.id()) : 0.0F;
				poseStack.translate(0.0F, mid - height * 0.1F * flat + bob, 0.0F);
				poseStack.rotate(Axis.YP.rotationDegrees(drift));
				poseStack.rotate(Axis.ZP.rotationDegrees(180.0F * amount + rock));
				poseStack.translate(0.0F, -mid, 0.0F);
			}
			case MELT -> {
				float quiver = pose.out() ? 0.0F : QUIVER * Mth.sin(age * 0.6F + pose.id());
				poseStack.scale(1.0F + MELT_SPREAD * flat, Math.max(0.05F, 1.0F - MELT_SAG * flat + quiver), 1.0F + MELT_SPREAD * flat);
			}
			case SHUDDER -> {
				float hic = hiccup(pose);
				poseStack.translate(0.0F, BIRD_HOP * 0.5F * hic * hic, 0.0F);
				float squash = SQUAT * flat * (1.0F - hic) - 0.15F * hic;
				poseStack.scale(1.0F + squash * 0.5F, 1.0F - squash, 1.0F + squash * 0.5F);
			}
			case FACE -> {
				// pitched forward onto its front
				poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
				poseStack.translate(0.0F, -height * 0.5F * flat, width * 0.3F * flat);
				if (!pose.machine() && fit > 0.0F) {
					poseStack.rotate(Axis.YP.rotationDegrees(LUMP_SHUDDER * fit * Mth.sin(age * 2.2F)));
				}
			}
			default -> {
				// on its side (SIDE, FALLEN, FLOP, TOPPLE), along the ground where it stood, raised by half its width
				float hop = 0.0F, roll = 0.0F;
				if (pose.style() == Style.FLOP) {
					float flop = flop(pose);
					hop = FLOP_HOP * flop * Math.abs(Mth.sin(age * 0.9F));
					roll = FLOP_ROLL * flop * Mth.sin(age * 0.9F + 0.6F);
				} else if (pose.style() == Style.TOPPLE && !pose.machine()) {
					roll = LUMP_SHUDDER * fit * Mth.sin(age * 2.2F);
				}
				poseStack.translate(height * 0.5F * flat, width * 0.5F * flat + hop, 0.0F);
				poseStack.rotate(Axis.ZP.rotationDegrees(DownedPose.SIDE_ROLL * amount + roll));
			}
		}
	}

	/** Right after its model was posed: its legs, wings, head and tail, where it has them. */
	static void apply(Model<?> model, LivingEntityRenderState state, Down pose) {
		Parts p = parts(model);
		if (p == null) {
			return;
		}
		if (p.hip > 0.0F && state.boundingBoxHeight > 0.0F && !state.isBaby) {
			// how far down its belly is, for the sprawl (its legs folded out flat): its hips' height, as a share of its height
			SINK.put(state.entityType, Mth.clamp(p.hip / (state.boundingBoxHeight / Math.max(state.scale, 0.01F)), 0.2F, 0.7F));
		}
		float age = state.ageInTicks;
		float w = Mth.clamp(pose.amount(), 0.0F, 1.0F);
		float fit = DownedPose.flail(pose);
		float breath = pose.out() ? 0.0F : Mth.sin(age * DownedPose.BREATH_RATE + pose.id()) * 0.08F;
		float drag = age * DRAG_STROKE + state.walkAnimationPos * DRAG_STRIDE + (pose.id() & 0xFF);
		float moving = pose.crawls() ? DownedPose.moving(state) : 0.0F;
		switch (pose.style()) {
			case SIDE -> {
				// the head lifts now and then, nods with its breath; the tail twitches in a fit
				turn(p.head, (breath - 0.4F * pushUp(pose)) * w, 0.0F, 0.0F);
				turn(p.tail, 0.0F, Mth.sin(age * 0.9F) * 0.4F * fit * w, 0.0F);
			}
			case SPRAWL -> {
				float push = pushUp(pose);
				for (Leg leg : p.legs) {
					float x, z = (leg.right ? 0.2F : -0.2F);
					if (leg.front) {
						x = -1.35F + 1.1F * push;
						if (moving > 0.0F) {
							// hauling itself on its forelegs, one then the other
							float s = Mth.sin(drag + (leg.right ? 0.0F : Mth.PI));
							x = Mth.lerp(moving, x, -1.1F + 0.5F * s);
						}
					} else {
						x = 1.35F + 0.25F * Mth.sin(age * 0.9F + (leg.right ? 0.0F : 2.0F)) * fit;
					}
					set(leg.part, x, leg.part.yRot, z, w);
				}
				set(p.head, 0.35F - 0.5F * push + breath - 0.3F * moving, p.head == null ? 0.0F : p.head.yRot * 0.3F, 0.0F, w);
				turn(p.tail, 0.3F * w, Mth.sin(age * 0.9F) * 0.3F * fit * w, 0.0F);
			}
			case FLIPPED -> {
				int i = 0;
				for (Leg leg : p.legs) {
					float twitch = twitch(pose, age, i++) * 0.25F;
					if (leg.sided) {
						turn(leg.part, 0.0F, 0.0F, (leg.right ? -1.0F : 1.0F) * (CURL_LEGS + twitch) * w);
					} else {
						turn(leg.part, (CURL_LEGS + twitch) * w, 0.0F, 0.0F);
					}
				}
				turn(p.head, 0.3F * w, 0.0F, 0.0F);
			}
			case SPLAYED -> {
				int i = 0;
				for (Leg leg : p.legs) {
					float twitch = twitch(pose, age, i++) * 0.3F;
					float pull = leg.front && moving > 0.0F ? Mth.sin(drag + (leg.right ? 0.0F : Mth.PI)) * 0.5F * moving : 0.0F;
					if (leg.sided) {
						float s = leg.right ? 1.0F : -1.0F;
						turn(leg.part, 0.0F, s * pull, s * (FLAT_LEGS - twitch) * w);
					} else {
						turn(leg.part, (-0.8F + twitch) * w, 0.0F, 0.0F);
					}
				}
				turn(p.head, 0.25F * w, 0.0F, 0.0F);
			}
			case FALLEN -> {
				// one wing beating feebly in a fit, the other pinned; legs drawn up; head lolled
				float beat = fit * (0.6F + 0.4F * Mth.sin(age * WING_RATE)) * WING_BEAT;
				set(p.rightWing, p.rightWing == null ? 0.0F : p.rightWing.xRot, p.rightWing == null ? 0.0F : p.rightWing.yRot, 0.15F + beat, w);
				set(p.leftWing, p.leftWing == null ? 0.0F : p.leftWing.xRot, p.leftWing == null ? 0.0F : p.leftWing.yRot, -0.05F, w);
				for (Leg leg : p.legs) {
					turn(leg.part, -0.6F * w, 0.0F, 0.0F);
				}
				turn(p.head, (0.35F + breath) * w, 0.4F * w, 0.0F);
			}
			case SPREAD -> {
				// wings spread flat, beating at the ground in a fit; legs out behind; chin down, lifting as it beats
				float beat = fit * Mth.sin(age * WING_RATE) * 0.5F;
				set(p.rightWing, p.rightWing == null ? 0.0F : p.rightWing.xRot, p.rightWing == null ? 0.0F : p.rightWing.yRot, WING_SPREAD + beat, w);
				set(p.leftWing, p.leftWing == null ? 0.0F : p.leftWing.xRot, p.leftWing == null ? 0.0F : p.leftWing.yRot, -WING_SPREAD - beat, w);
				for (Leg leg : p.legs) {
					set(leg.part, 1.2F, leg.part.yRot, leg.part.zRot, w);
				}
				turn(p.head, (0.4F - 0.3F * fit + breath) * w, 0.0F, 0.0F);
			}
			default -> {
				// fish, slimes, lumps: the whole body's turn does it; a tail twitches as a fish flops
				if (pose.style() == Style.FLOP) {
					turn(p.tail, 0.0F, Mth.sin(age * 1.8F) * 0.6F * flop(pose) * w, 0.0F);
				}
			}
		}
	}

	/** A sprawled beast pushing its front up (0-1), every so often (each on its own beat); never as it lands, nor out cold. */
	static float pushUp(Down pose) {
		if (pose.out() || pose.crawls() || pose.down() < DownedPose.FALL_TICKS + PUSH_TICKS) {
			return 0.0F;
		}
		float every = PUSH_EVERY + (pose.id() & 63) * 2.0F;
		float t = (pose.down() + (pose.id() & 0xFF) * 3.0F) % every;
		return PoseBlend.bump(t, 0.0F, PUSH_TICKS * 0.45F, PUSH_TICKS * 0.6F, PUSH_TICKS);
	}

	/** A fish's flop (0-1): often at first, weaker the longer it's been out. */
	static float flop(Down pose) {
		if (pose.out() || pose.down() < DownedPose.FALL_TICKS) {
			return 0.0F;
		}
		float every = FLOP_EVERY + (pose.id() & 15) * 2.0F;
		float t = (pose.down() + (pose.id() & 0xFF)) % every;
		float tire = Math.max(0.25F, 1.0F - pose.down() / FLOP_TIRE);
		return PoseBlend.bump(t, 0.0F, 3.0F, FLOP_TICKS - 5.0F, FLOP_TICKS) * tire;
	}

	/** A slime's hiccup (0-1): a quick heave every so often, a hop that doesn't get it anywhere. */
	static float hiccup(Down pose) {
		if (pose.out() || pose.down() < DownedPose.FALL_TICKS) {
			return 0.0F;
		}
		float every = HICCUP_EVERY + (pose.id() & 31);
		float t = (pose.down() + (pose.id() & 0xFF)) % every;
		return PoseBlend.bump(t, 0.0F, HICCUP_TICKS * 0.3F, HICCUP_TICKS * 0.4F, HICCUP_TICKS);
	}

	/** One leg's twitch (-1 to 1): held for a few ticks, then another, each leg on its own; most of the time still. */
	static float twitch(Down pose, float age, int leg) {
		if (pose.out()) {
			return 0.0F;
		}
		float step = (float) Math.floor(age / 3.0F);
		float j = DownedPose.jerk(pose.id(), step, 20 + leg);
		float fit = DownedPose.flail(pose);
		// a twitch now and then, all of them at once in a fit
		return Math.abs(j) > 0.8F - 0.8F * fit ? j : 0.0F;
	}

	private static void turn(@Nullable ModelPart part, float x, float y, float z) {
		if (part != null) {
			part.xRot += x;
			part.yRot += y;
			part.zRot += z;
		}
	}

	private static void set(@Nullable ModelPart part, float x, float y, float z, float w) {
		if (part != null) {
			part.xRot = Mth.lerp(w, part.xRot, x);
			part.yRot = Mth.lerp(w, part.yRot, y);
			part.zRot = Mth.lerp(w, part.zRot, z);
		}
	}

	/** One leg: its part, whether it's on the right, at the front, and whether it's one of a pair (else a row, a bee's). */
	private record Leg(ModelPart part, boolean right, boolean front, boolean sided) {
	}

	/** Its parts, and how high its hind legs hang from (blocks, 0 if it has none). */
	private record Parts(@Nullable ModelPart head, @Nullable ModelPart tail, @Nullable ModelPart rightWing, @Nullable ModelPart leftWing,
		List<Leg> legs, float hip) {
	}

	/** Each kind of beast's belly height as a share of its height, once one has been drawn (else {@link #BEAST_SINK}). */
	private static final Map<net.minecraft.world.entity.EntityType<?>, Float> SINK = new java.util.concurrent.ConcurrentHashMap<>();

	private static final String[] FRONT_LEGS = {"front_leg", "middle_front_leg"};
	private static final String[] HIND_LEGS = {"hind_leg", "middle_hind_leg", "leg"};
	private static final Map<Model<?>, Parts> PARTS = new WeakHashMap<>();
	private static final Parts NONE = new Parts(null, null, null, null, List.of(), 0.0F);

	/** Its parts, found by vanilla's names anywhere in it (once a model); null if it has none of them. */
	private static @Nullable Parts parts(Model<?> model) {
		Parts p = PARTS.computeIfAbsent(model, m -> {
			Function<String, @Nullable ModelPart> look = m.root().createPartLookup();
			List<Leg> legs = new ArrayList<>();
			for (String side : new String[] {"right_", "left_"}) {
				for (String name : FRONT_LEGS) {
					ModelPart part = look.apply(side + name);
					if (part != null) {
						legs.add(new Leg(part, side.equals("right_"), true, true));
					}
				}
				for (String name : HIND_LEGS) {
					ModelPart part = look.apply(side + name);
					if (part != null) {
						legs.add(new Leg(part, side.equals("right_"), false, true));
					}
				}
			}
			for (String row : new String[] {"front_legs", "middle_legs", "back_legs"}) {
				ModelPart part = look.apply(row);
				if (part != null) {
					legs.add(new Leg(part, false, row.startsWith("front"), false));
				}
			}
			ModelPart head = first(look, "head", "head_parts");
			ModelPart tail = first(look, "tail", "tail_base", "tail1");
			ModelPart rightWing = first(look, "right_wing", "right_wing_base");
			ModelPart leftWing = first(look, "left_wing", "left_wing_base");
			if (legs.isEmpty() && head == null && tail == null && rightWing == null) {
				return NONE;
			}
			float hip = 0.0F;
			for (Leg leg : legs) {
				if (leg.sided && !leg.front) {
					// a model's ground is 24 pixels down from its root
					hip = (24.0F - leg.part.getInitialPose().y()) / 16.0F;
					break;
				}
			}
			return new Parts(head, tail, rightWing, leftWing, List.copyOf(legs), hip);
		});
		return p == NONE ? null : p;
	}

	private static @Nullable ModelPart first(Function<String, @Nullable ModelPart> look, String... names) {
		for (String name : names) {
			ModelPart part = look.apply(name);
			if (part != null) {
				return part;
			}
		}
		return null;
	}
}
