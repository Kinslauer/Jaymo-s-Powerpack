package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.power.web.WebExecutions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-12): "downed enemies should react to the executions ... instead of being beaten into the ground". How a
 * downed mob takes a finisher ({@code Finishers}) or one of the symbiote's ({@code WebExecutions}) while it plays, for
 * everyone watching. Both are synced on the one doing it (which move, since when, on whom), so the body under it knows
 * the beats its blows land on:
 * <ul>
 * <li>before the first blow and between blows it braces: arms thrown up over the face (or over the back of the head,
 * face down), knees drawn in, head turned away, shaking;</li>
 * <li>a crushing blow (a slam, a hammer, a rock, a stomp, ki) drives the whole body down into the ground and it bounces
 * back off it, arms and legs flung up with the shock, harder on the last;</li>
 * <li>a blade or spear through it pins it: it sinks under the point, goes rigid, limbs out straight, and quivers;</li>
 * <li>a cut or a shot jerks it once, sharp;</li>
 * <li>a strangle (the neck snap's hold, the garrotte, the symbiote's choke, bite and tearing) is fought: hands clawing
 * up at the grip, heels kicking, harder as each strain comes.</li>
 * </ul>
 * The drop and bounce are a move of the whole drawing ({@link #press}), so they show on any model, modded ones too;
 * the limbs are {@code DownedPose}'s people ({@link #limbs}); animals, bugs and the rest kick with their own legs
 * ({@link #thrash}). Hoisted off the ground by the symbiote, a body only struggles. The death itself is Kitchen's corpse,
 * which now starts lying as they lay ({@code DownedLie}).
 */
final class ExecutionReact {
	/** A crushing blow: how far the body is driven down (blocks, for a body two blocks tall), the last blow's, how fast the bounce rings and dies (per tick). */
	static final float CRUSH_SINK = 0.16F, CRUSH_SINK_LAST = 0.24F, CRUSH_RING = 1.3F, CRUSH_DECAY = 0.4F;
	/** Pinned by a point: how far it sinks under it (blocks), and how slowly it comes back (per tick). */
	static final float STAB_SINK = 0.08F, STAB_DECAY = 0.12F;
	/** A cut or a shot: the jerk (blocks), its ring and decay (per tick). */
	static final float SNAP_SINK = 0.07F, SNAP_RING = 1.7F, SNAP_DECAY = 0.6F;
	/** How fast the limbs come back down after a blow flings them (per tick), by kind of blow. */
	static final float CRUSH_LIMBS_DECAY = 0.22F, STAB_LIMBS_DECAY = 0.07F, SNAP_LIMBS_DECAY = 0.45F;
	/** Bracing: how long it takes to get its arms up (ticks), and the shake (radians, radians a tick). */
	static final float BRACE_TICKS = 6.0F, TREMBLE = 0.04F, TREMBLE_RATE = 2.4F;
	/** Pinned and quivering: the shake (radians, radians a tick). */
	static final float QUIVER = 0.09F, QUIVER_RATE = 3.1F;
	/** Fighting a strangle: the least of it, how long before a strain it builds (ticks), its pace (radians a tick), the arms' and legs' swing (radians). */
	static final float STRUGGLE_LEAST = 0.45F, STRUGGLE_BUILD = 14.0F, STRUGGLE_RATE = 0.9F, STRUGGLE_ARMS = 0.3F, STRUGGLE_LEGS = 0.6F;
	/** An animal under a blow: how hard its legs kick (walk speed), and how far the whole body rocks with it (degrees). */
	static final float THRASH_SWING = 1.2F, THRASH_ROCK = 7.0F;
	/** The height the sinks above are meant for (blocks): smaller bodies move less. */
	static final float FULL_HEIGHT = 1.8F;

	/** How a blow lands on the one under it. */
	enum Blow { CRUSH, STAB, CUT, SHOT, CHOKE }

	/**
	 * This frame of being finished: the kind of blow, ticks {@code since} the last one landed (negative: none yet), ticks
	 * {@code until} the next (negative: none to come), ticks since it {@code begun}, whether the one that landed was a
	 * heavy one ({@code hard}: the last but one beat on, or the only), and whether the body is held up off the ground.
	 */
	record React(Blow blow, float since, float until, float begun, boolean hard, boolean hoisted) {
	}

	private ExecutionReact() {
	}

	/** What is being done to {@code entity} just now, or null. */
	static @Nullable React of(LivingEntity entity, float partialTicks) {
		int id = entity.getId();
		float now = entity.level().getGameTime() + partialTicks;
		for (Player player : entity.level().players()) {
			Finishers.Finish finish = Finishers.of(player);
			if (finish != null && finish.target() == id) {
				Finishers.Kind kind = finish.type();
				Blow blow = switch (kind.strike) {
					case CUT -> Blow.CUT;
					case STAB, PIERCE -> Blow.STAB;
					case CRUSH, KI -> Blow.CRUSH;
					case SHOT, BOLT, BLAST -> Blow.SHOT;
					case SNAP -> Blow.CHOKE;
					case HOLD -> null;
				};
				return blow == null ? null : react(blow, kind.beats, now - finish.start(), false);
			}
			WebExecutions.Execution execution = WebExecutions.of(player);
			if (execution != null && execution.target() == id) {
				WebExecutions.Move move = execution.type();
				float t = now - execution.start();
				boolean hoisted = WebExecutions.lift(move, t) > 0.05;
				Blow blow = switch (move.strike) {
					case SNAP, TEAR, BITE -> Blow.CHOKE;
					case PIERCE -> Blow.STAB;
					case PUNCH, CRUSH, SLAM, QUAKE, THROW -> Blow.CRUSH;
				};
				return react(hoisted ? Blow.CHOKE : blow, move.beats, t, hoisted);
			}
		}
		return null;
	}

	private static React react(Blow blow, int[] beats, float t, boolean hoisted) {
		float since = -1.0F, until = -1.0F;
		boolean hard = false;
		for (int i = 0; i < beats.length; i++) {
			if (t >= beats[i]) {
				since = t - beats[i];
				hard = i >= beats.length - 2;
			} else {
				until = beats[i] - t;
				break;
			}
		}
		return new React(blow, since, until, Math.max(0.0F, t), hard, hoisted);
	}

	/** How far the whole body is moved up (down, negative) by the blow that landed: driven in, and bouncing back (blocks, for a full-sized body). */
	static float press(React r) {
		if (r.since() < 0.0F || r.hoisted()) {
			return 0.0F;
		}
		float s = r.since();
		return switch (r.blow()) {
			case CRUSH -> -(r.hard() ? CRUSH_SINK_LAST : CRUSH_SINK) * (float) Math.exp(-s * CRUSH_DECAY) * Mth.cos(s * CRUSH_RING);
			case STAB -> -STAB_SINK * (float) Math.exp(-s * STAB_DECAY);
			case CUT, SHOT -> -SNAP_SINK * (float) Math.exp(-s * SNAP_DECAY) * Mth.cos(s * SNAP_RING);
			case CHOKE -> 0.0F;
		};
	}

	/** The shock of the blow that landed, in the limbs (1 as it lands, dying away). */
	static float jolt(React r) {
		if (r.since() < 0.0F) {
			return 0.0F;
		}
		float decay = switch (r.blow()) {
			case CRUSH -> CRUSH_LIMBS_DECAY;
			case STAB -> STAB_LIMBS_DECAY;
			case CUT, SHOT -> SNAP_LIMBS_DECAY;
			case CHOKE -> 1.0F;
		};
		return (float) Math.exp(-r.since() * decay);
	}

	/** Braced for what's coming (0-1): up as it begins, let go while a blow's shock has the limbs. */
	static float brace(React r) {
		if (r.blow() == Blow.CHOKE) {
			return 0.0F;
		}
		return PoseBlend.smooth(Mth.clamp(r.begun() / BRACE_TICKS, 0.0F, 1.0F)) * (1.0F - jolt(r));
	}

	/** Fighting a hold (0-1): always some, most just before each strain. */
	static float struggle(React r) {
		if (r.blow() != Blow.CHOKE) {
			return 0.0F;
		}
		float build = r.until() < 0.0F ? 0.0F : 1.0F - Mth.clamp(r.until() / STRUGGLE_BUILD, 0.0F, 1.0F);
		return PoseBlend.smooth(Mth.clamp(r.begun() / BRACE_TICKS, 0.0F, 1.0F)) * (STRUGGLE_LEAST + (1.0F - STRUGGLE_LEAST) * build);
	}

	/** An animal, a bug, a bird: how hard its own legs kick under it (0-1). */
	static float thrash(React r) {
		return Math.max(struggle(r), Math.max(jolt(r), 0.3F * brace(r)));
	}

	/** How far a body not built like a person rocks over with the blow (degrees, signed by {@code id} so they don't all go one way). */
	static float rock(React r, int id) {
		if (r.since() < 0.0F || r.blow() == Blow.CHOKE) {
			return 0.0F;
		}
		return ((id & 1) == 0 ? 1.0F : -1.0F) * THRASH_ROCK * (float) Math.exp(-r.since() * CRUSH_DECAY) * Mth.cos(r.since() * CRUSH_RING);
	}

	/**
	 * A person's limbs, laid over however they lie: {@code faceUp} on their back or side ("forward", negative x, is up
	 * off the ground), else face down (a leg's positive x lifts the heel). A broken limb stays where it lies.
	 */
	static void limbs(PoseBlend.Frame f, React r, boolean faceUp, int broken, float age, int id) {
		float brace = brace(r), jolt = jolt(r), struggle = struggle(r);
		boolean rigid = r.blow() == Blow.STAB;
		float a = age * STRUGGLE_RATE + (id & 15);
		if (faceUp) {
			if (brace > 0.0F) {
				// arms crossed up over the face, knees drawn in, head turned from it
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -1.9F, 0.0F, -0.45F, brace);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -1.75F, 0.0F, 0.45F, brace);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, -0.9F, 0.0F, 0.1F, brace);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, -0.5F, 0.0F, -0.1F, brace);
				f.mix(PoseBlend.HEAD, 0.25F, 0.8F, 0.0F, brace);
			}
			if (jolt > 0.0F && rigid) {
				// run through: arched off the ground, arms out stiff at the sides, legs straight, head thrown back
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -0.25F, 0.0F, 1.3F, jolt);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -0.25F, 0.0F, -1.3F, jolt);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, 0.1F, 0.0F, 0.2F, jolt);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, 0.1F, 0.0F, -0.2F, jolt);
				f.mix(PoseBlend.HEAD, -0.45F, 0.0F, 0.0F, jolt);
			} else if (jolt > 0.0F) {
				// the shock goes through it: arms and legs flung up off the ground, the head snapped up
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -1.0F, 0.0F, 1.2F, jolt);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -1.0F, 0.0F, -1.2F, jolt);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, -1.05F, 0.0F, 0.25F, jolt);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, -0.85F, 0.0F, -0.25F, jolt);
				f.mix(PoseBlend.HEAD, 0.65F, r.blow() == Blow.CUT ? -0.7F : 0.0F, 0.0F, jolt);
			}
			if (struggle > 0.0F) {
				// both hands clawing up at the grip on the neck, heels kicking in turn, the head wrenched about
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -2.2F + Mth.sin(a) * STRUGGLE_ARMS, 0.0F, -0.3F, struggle);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -2.2F + Mth.sin(a + 2.0F) * STRUGGLE_ARMS, 0.0F, 0.3F, struggle);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, -0.5F + Mth.sin(a * 1.3F) * STRUGGLE_LEGS, 0.0F, 0.1F, struggle);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, -0.5F + Mth.sin(a * 1.3F + 2.6F) * STRUGGLE_LEGS, 0.0F, -0.1F, struggle);
				f.mix(PoseBlend.HEAD, -0.2F, Mth.sin(a * 0.8F) * 0.4F, 0.0F, struggle);
			}
		} else {
			if (brace > 0.0F) {
				// face down: hands clasped over the back of the head, face pressed into the dirt
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -2.75F, 0.5F, 0.0F, brace);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -2.75F, -0.5F, 0.0F, brace);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, 0.0F, 0.0F, 0.1F, brace);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, 0.0F, 0.0F, -0.1F, brace);
				f.mix(PoseBlend.HEAD, 0.3F, 0.0F, 0.0F, brace);
			}
			if (jolt > 0.0F && rigid) {
				// pinned to the ground: fingers dug in ahead, legs out stiff, the head hauled up
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -2.9F, 0.0F, 0.7F, jolt);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -2.9F, 0.0F, -0.7F, jolt);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, 0.15F, 0.0F, 0.2F, jolt);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, 0.15F, 0.0F, -0.2F, jolt);
				f.mix(PoseBlend.HEAD, -0.7F, 0.0F, 0.0F, jolt);
			} else if (jolt > 0.0F) {
				// the shock goes through it: heels kicked up, arms thrown wide, the head snapped back
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -2.2F, 0.0F, 1.0F, jolt);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -2.2F, 0.0F, -1.0F, jolt);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, 0.75F, 0.0F, 0.2F, jolt);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, 0.55F, 0.0F, -0.2F, jolt);
				f.mix(PoseBlend.HEAD, -0.9F, r.blow() == Blow.CUT ? -0.6F : 0.0F, 0.0F, jolt);
			}
			if (struggle > 0.0F) {
				// scrabbling at the ground ahead, heels kicking in turn
				arm(f, PoseBlend.RIGHT_ARM, Dismember.RIGHT_ARM, broken, -2.6F + Mth.sin(a) * STRUGGLE_ARMS, 0.0F, 0.3F, struggle);
				arm(f, PoseBlend.LEFT_ARM, Dismember.LEFT_ARM, broken, -2.6F + Mth.sin(a + 2.0F) * STRUGGLE_ARMS, 0.0F, -0.3F, struggle);
				arm(f, PoseBlend.RIGHT_LEG, Dismember.RIGHT_LEG, broken, 0.4F + Mth.sin(a * 1.3F) * 0.5F, 0.0F, 0.1F, struggle);
				arm(f, PoseBlend.LEFT_LEG, Dismember.LEFT_LEG, broken, 0.4F + Mth.sin(a * 1.3F + 2.6F) * 0.5F, 0.0F, -0.1F, struggle);
				f.mix(PoseBlend.HEAD, -0.6F, Mth.sin(a * 0.8F) * 0.4F, 0.0F, struggle);
			}
		}
		// shaking as it waits for the blow; quivering on the point that pins it
		float shake = Mth.sin(age * TREMBLE_RATE + id) * TREMBLE * brace
			+ (rigid && r.since() >= 0.0F ? Mth.sin(age * QUIVER_RATE + id) * QUIVER * jolt : 0.0F);
		if (shake != 0.0F) {
			f.shake(PoseBlend.RIGHT_ARM, shake);
			f.shake(PoseBlend.LEFT_ARM, -shake);
			f.shake(PoseBlend.RIGHT_LEG, -shake * 0.6F);
			f.shake(PoseBlend.LEFT_LEG, shake * 0.6F);
			f.shake(PoseBlend.HEAD, shake * 0.5F);
		}
	}

	/** One limb toward an angle, unless it's broken (it lies where it is). */
	private static void arm(PoseBlend.Frame f, int part, int bit, int broken, float x, float y, float z, float w) {
		if ((broken & bit) == 0) {
			f.mix(part, x, y, z, w);
		}
	}
}
