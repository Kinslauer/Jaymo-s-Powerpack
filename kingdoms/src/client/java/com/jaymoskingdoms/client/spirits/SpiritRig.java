package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.spirits.Poses;
import com.jaymoskingdoms.spirits.Spirits;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.245) The spirit arm as a jointed limb, and how it moves. Andy (2026-10-27): "the arms should be animated with joints and
 * fingers, more like the nero gif ... all of the arms need more detail and oomph. these spirits are not easy to get so it
 * should be worth it". Each arm is a shoulder, an upper arm, an elbow, a forearm that can stretch out (Nero's Devil Breaker),
 * a wrist, a palm, jointed fingers (two segments each) and a thumb, with each spirit's own make:
 * <ul>
 * <li>Grimsby: bony and thin, knobbed joints, three long fingers and a blade from the wrist.</li>
 * <li>Varro: plate: a pauldron, a couter at the elbow, a vambrace with a cuff, a gauntlet with plated fingers and studs.</li>
 * <li>Ambrose: an elder's hand twice yours: a sleeve flaring at the elbow, a broad palm with a ward sigil, five long fingers.</li>
 * <li>Thessaly: slim: a bracelet at the wrist, four slender fingers, rings on three of them.</li>
 * <li>The Quiet One: smooth and seamless, no knuckles, three long fingers and a thumb.</li>
 * </ul>
 * Every move has a shape ({@link Shape}): a strike winds up (drawn back, elbow bent, fist), snaps out past its mark and settles
 * (the forearm stretching on a thrust); a swing raises high and cuts across; a grasp reaches open and closes; an open move
 * spreads the hand. Idle, the fingers flex and the wrist drifts. The impact pulses the hand ({@link #pulse}); the motion
 * trails and the hit's burst are {@link SpiritArms}'.
 */
final class SpiritRig {
	/** How a pose moves: holds, strikes (wind-up and snap), swings (high and across), grasps (open, then close), opens. */
	static final int HOLD = 0, STRIKE = 1, SWING = 2, GRASP = 3, OPEN = 4;

	/** Ticks of the wind-up, the tick the blow lands, and how far the snap overshoots (the back-out ease's strength). */
	static final float WIND_T = 3.0F, HIT_T = 5.0F, OVERSHOOT = 2.2F;
	/** A grasp: ticks reaching open, then ticks closing. */
	static final float OPEN_T = 4.0F, CLOSE_T = 3.0F;
	/** The wind-up of a strike: the shoulder drawn back by this, the elbow bent this far. A swing: raised this much higher, opened out this much. */
	static final float WIND_BACK = 0.9F, WIND_ELBOW = -2.1F, SWING_HIGH = 1.0F, SWING_OUT = 0.9F;
	/** The impact: the hand swells by this at the hit, fading over this many ticks. */
	static final float PULSE = 0.3F, PULSE_TICKS = 3.0F;
	/** Idle life: the fingers' flex (how much, how fast), the elbow's rest bend (on your arm, floating). */
	static final float IDLE_CURL = 0.3F, FLEX = 0.12F, FLEX_SPEED = 0.11F, IDLE_ELBOW = -0.45F, FLOAT_ELBOW = -0.7F;
	/**
	 * Idle movement (Andy: "the arm should move in idle, bending at the elbow"): the elbow works slowly in and out by
	 * {@code ELBOW_SWAY}, the shoulder rolls by {@code ROLL}, and now and then (a slow beat, {@code STRETCH_SPEED}) the arm draws
	 * right in and the hand closes, as if stretching, then opens out again ({@code STRETCH_ELBOW}, {@code STRETCH_CURL}).
	 */
	static final float ELBOW_SWAY = 0.35F, ELBOW_SPEED = 0.07F, ROLL = 0.08F, ROLL_SPEED = 0.05F;
	static final float STRETCH_SPEED = 0.035F, STRETCH_ELBOW = -0.9F, STRETCH_CURL = 0.6F, STRETCH_WRIST = 0.35F;
	/** How far a curl turns each finger segment (radians at a full fist), and a spread fans them (radians per finger from the middle). */
	static final float CURL_BASE = 1.45F, CURL_TIP = 1.65F, SPREAD = 0.2F;
	/** The flurry and shove: the arms pump in turn at this speed. */
	static final float PUMP_SPEED = 1.8F;

	/** Model pixels: the upper arm runs from y -2 to the elbow at 4; the forearm is 6 long to the wrist. */
	static final float ELBOW_Y = 4.0F, FORE = 6.0F;

	/** A pose's shape beyond the shoulder angles ({@link SpiritArms}' table): its class, elbow, wrist, fist (0 open-1 closed), spread (0-1), stretch. */
	record Shape(int how, float elbow, float wrist, float curl, float spread, float reach) {
	}

	private static final Shape[] SHAPES = new Shape[Poses.COUNT];

	static {
		Shape hold = new Shape(HOLD, -0.4F, 0.1F, 0.4F, 0.3F, 0.0F);
		java.util.Arrays.fill(SHAPES, hold);
		SHAPES[Poses.IDLE] = hold;
		SHAPES[Poses.REACH] = new Shape(OPEN, -0.1F, -0.2F, 0.15F, 0.9F, 0.5F);
		SHAPES[Poses.GRAB] = new Shape(GRASP, -0.2F, 0.0F, 0.9F, 0.4F, 0.6F);
		SHAPES[Poses.THROW] = new Shape(STRIKE, 0.0F, 0.3F, 0.0F, 1.0F, 0.4F);
		SHAPES[Poses.CATCH] = new Shape(GRASP, -0.3F, -0.4F, 0.8F, 0.6F, 0.3F);
		SHAPES[Poses.SHOVE] = new Shape(STRIKE, 0.0F, -1.2F, 0.1F, 0.8F, 0.9F);
		SHAPES[Poses.PIN] = new Shape(GRASP, -0.2F, -0.6F, 0.7F, 0.7F, 0.7F);
		SHAPES[Poses.HOLD] = new Shape(HOLD, -0.9F, -0.3F, 0.85F, 0.2F, 0.0F);
		SHAPES[Poses.SWORD] = new Shape(SWING, -0.15F, 0.2F, 1.0F, 0.0F, 0.3F);
		SHAPES[Poses.GREATSWORD] = new Shape(SWING, 0.0F, 0.3F, 1.0F, 0.0F, 0.5F);
		SHAPES[Poses.SPEAR] = new Shape(STRIKE, 0.0F, 0.0F, 1.0F, 0.0F, 1.4F);
		SHAPES[Poses.AXE] = new Shape(SWING, -0.2F, 0.4F, 1.0F, 0.0F, 0.3F);
		SHAPES[Poses.HAMMER] = new Shape(SWING, -0.1F, 0.5F, 1.0F, 0.0F, 0.4F);
		SHAPES[Poses.FLURRY] = new Shape(STRIKE, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F);
		SHAPES[Poses.PARRY] = new Shape(HOLD, -1.3F, -0.2F, 0.9F, 0.0F, 0.0F);
		SHAPES[Poses.SLAM] = new Shape(SWING, 0.0F, 0.6F, 1.0F, 0.0F, 0.6F);
		SHAPES[Poses.JAVELIN] = new Shape(STRIKE, -0.2F, 0.4F, 0.2F, 0.9F, 0.5F);
		SHAPES[Poses.VOLLEY] = new Shape(STRIKE, -0.1F, 0.3F, 0.1F, 1.0F, 0.4F);
		SHAPES[Poses.BOMBARD] = new Shape(STRIKE, -0.3F, 0.5F, 0.3F, 0.8F, 0.6F);
		SHAPES[Poses.WALL] = new Shape(OPEN, -0.2F, -1.3F, 0.0F, 1.0F, 0.3F);
		SHAPES[Poses.MARK] = new Shape(OPEN, -0.1F, 0.0F, 0.8F, 0.0F, 0.6F);
		SHAPES[Poses.WARD] = new Shape(OPEN, -0.3F, -1.3F, 0.0F, 1.0F, 0.2F);
		SHAPES[Poses.FINISH] = new Shape(STRIKE, 0.0F, 0.0F, 1.0F, 0.0F, 1.5F);
		SHAPES[Poses.AEGIS] = new Shape(OPEN, -0.15F, -1.4F, 0.0F, 1.0F, 0.4F);
		SHAPES[Poses.BLESS] = new Shape(OPEN, -0.3F, -1.2F, 0.1F, 0.9F, 0.2F);
		SHAPES[Poses.CATCH_ME] = new Shape(OPEN, -0.5F, -0.5F, 0.2F, 1.0F, 0.0F);
		SHAPES[Poses.MEND] = new Shape(OPEN, -1.3F, -0.4F, 0.1F, 0.6F, 0.0F);
		SHAPES[Poses.ARMOUR] = new Shape(HOLD, -1.6F, -0.2F, 0.6F, 0.2F, 0.0F);
		SHAPES[Poses.RIDE] = new Shape(OPEN, 0.0F, -1.0F, 0.0F, 1.0F, 0.6F);
		SHAPES[Poses.PULL] = new Shape(GRASP, -0.2F, 0.0F, 0.9F, 0.5F, 0.9F);
		SHAPES[Poses.STEAL] = new Shape(GRASP, -0.3F, 0.1F, 0.9F, 0.3F, 0.7F);
		SHAPES[Poses.TRAP] = new Shape(GRASP, -0.4F, -0.5F, 0.7F, 0.8F, 0.3F);
		SHAPES[Poses.UNLOCK] = new Shape(GRASP, -0.5F, 0.2F, 0.6F, 0.2F, 0.4F);
		SHAPES[Poses.JUGGLE] = new Shape(OPEN, -0.6F, -0.6F, 0.2F, 0.9F, 0.2F);
		SHAPES[Poses.ECHO] = new Shape(STRIKE, 0.0F, 0.0F, 1.0F, 0.0F, 1.1F);
		SHAPES[Poses.MIRROR] = new Shape(OPEN, -0.2F, -0.3F, 0.0F, 1.0F, 0.2F);
		SHAPES[Poses.STILL] = new Shape(OPEN, -0.3F, -1.1F, 0.0F, 1.0F, 0.0F);
		SHAPES[Poses.SILENT] = new Shape(STRIKE, 0.0F, 0.0F, 0.15F, 0.0F, 1.6F);
		SHAPES[Poses.SENSE] = new Shape(OPEN, -0.3F, -0.6F, 0.0F, 1.0F, 0.0F);
		SHAPES[Poses.CLASH] = new Shape(GRASP, 0.0F, -0.3F, 0.85F, 0.6F, 1.2F);
	}

	private SpiritRig() {
	}

	static Shape shape(int pose) {
		return SHAPES[Mth.clamp(pose, 0, Poses.COUNT - 1)];
	}

	/** Whether {@code pose} lands a blow (a strike or a swing): its hit pulses, trails and bursts. */
	static boolean lands(int pose) {
		int how = shape(pose).how();
		return pose != Poses.IDLE && (how == STRIKE || how == SWING);
	}

	// ---- the frame ------------------------------------------------------------------------------------------------------

	/** One arm's joints this frame: shoulder (x, y, z added to the wearer's arm), elbow, wrist, fist, spread, stretch, hand scale. */
	static final class Frame {
		float sx, sy, sz, elbow, wrist, curl, spread, reach, hand = 1.0F;
		/** Grimsby's weapon this frame ({@link #BLADE} and the rest) and how far grown it is (0 not yet, 1 full, above for the big ones). */
		int weapon;
		float grow;

		void set(float sx, float sy, float sz, float elbow, float wrist, float curl, float spread, float reach) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.elbow = elbow;
			this.wrist = wrist;
			this.curl = curl;
			this.spread = spread;
			this.reach = reach;
		}

		void lerp(float k, Frame a, Frame b) {
			this.sx = Mth.lerp(k, a.sx, b.sx);
			this.sy = Mth.lerp(k, a.sy, b.sy);
			this.sz = Mth.lerp(k, a.sz, b.sz);
			this.elbow = Mth.lerp(k, a.elbow, b.elbow);
			this.wrist = Mth.lerp(k, a.wrist, b.wrist);
			this.curl = Mth.lerp(k, a.curl, b.curl);
			this.spread = Mth.lerp(k, a.spread, b.spread);
			this.reach = Mth.lerp(k, a.reach, b.reach);
		}
	}

	/**
	 * Arm {@code i}'s frame into {@code out}: {@code pose} held {@code age} ticks with the envelope {@code weight} (in, held,
	 * out), the idle shoulder {@code idle} and the pose's {@code target} shoulder angles, {@code ageInTicks} for the idle's life.
	 */
	static void frame(Frame out, int pose, float age, float weight, float[] idle, float[] target, boolean floats, int i, float ageInTicks) {
		Frame rest = new Frame();
		float flex = FLEX * Mth.sin(ageInTicks * FLEX_SPEED + i * 1.7F);
		float bend = ELBOW_SWAY * (0.5F + 0.5F * Mth.sin(ageInTicks * ELBOW_SPEED + i * 2.3F));
		float roll = ROLL * Mth.sin(ageInTicks * ROLL_SPEED + i * 1.1F);
		// the stretch: a sharp beat (a sine to the fourth), each arm on its own
		float beat = Mth.sin(ageInTicks * STRETCH_SPEED + i * 2.6F);
		float stretch = beat > 0.0F ? beat * beat * beat * beat : 0.0F;
		rest.set(idle[0] + roll + stretch * 0.25F, idle[1] - stretch * 0.2F, idle[2] + roll * 0.5F,
			(floats ? FLOAT_ELBOW : IDLE_ELBOW) - bend + stretch * STRETCH_ELBOW, 0.15F + flex + stretch * STRETCH_WRIST,
			IDLE_CURL + flex + stretch * STRETCH_CURL, 0.3F - flex - stretch * 0.2F, 0.0F);
		out.weapon = weapon(pose);
		out.grow = 0.0F;
		if (pose <= Poses.IDLE || pose >= Poses.COUNT || weight <= 0.0F) {
			copy(out, rest);
			out.hand = 1.0F;
			return;
		}
		Shape shape = shape(pose);
		Frame aim = new Frame();
		aim.set(target[0], target[1], target[2], shape.elbow(), shape.wrist(), shape.curl(), shape.spread(), shape.reach());
		Frame now = new Frame();
		switch (shape.how()) {
			case STRIKE -> {
				Frame wind = new Frame();
				wind.set(target[0] + WIND_BACK, target[1] * 0.5F, target[2], WIND_ELBOW, -0.3F, Math.max(shape.curl(), 0.6F), shape.spread() * 0.3F, 0.0F);
				strike(now, age, rest, wind, aim);
			}
			case SWING -> {
				Frame wind = new Frame();
				if (pose == Poses.HAMMER || pose == Poses.SLAM) {
					// a slam: heaved straight up over the head, elbow bent behind, then brought down in front
					wind.set(SLAM_HIGH, 0.0F, target[2], -1.4F, -0.6F, 1.0F, 0.0F, 0.0F);
				} else {
					wind.set(target[0] - SWING_HIGH, target[1] + SWING_OUT, target[2] + 0.3F, -1.1F, -0.5F, 1.0F, 0.0F, 0.0F);
				}
				strike(now, age, rest, wind, aim);
			}
			case GRASP -> {
				Frame open = new Frame();
				open.set(aim.sx, aim.sy, aim.sz, aim.elbow * 0.5F, aim.wrist - 0.3F, 0.0F, 1.0F, aim.reach * 1.1F);
				if (age < OPEN_T) {
					now.lerp(smooth(age / OPEN_T), rest, open);
				} else {
					now.lerp(smooth(Math.min(1.0F, (age - OPEN_T) / CLOSE_T)), open, aim);
				}
			}
			default -> now.lerp(smooth(Math.min(1.0F, age / SpiritArms.POSE_IN)), rest, aim);
		}
		if (pose == Poses.FLURRY || pose == Poses.SHOVE) {
			// the blows: the two arms pump in turn, each straightening out and drawing back
			float pump = 0.5F + 0.5F * Mth.sin(age * PUMP_SPEED + i * Mth.PI);
			now.sx += 0.5F * Mth.sin(age * PUMP_SPEED + i * Mth.PI);
			now.elbow = Mth.lerp(pump, WIND_ELBOW * 0.8F, 0.0F);
			now.reach = shape.reach() * pump;
		}
		// the strike's own timing carries it in; only its going out is eased back to rest by the envelope
		float envelope = shape.how() == STRIKE || shape.how() == SWING ? (age < SpiritArms.POSE_TICKS ? 1.0F : weight) : weight;
		out.lerp(envelope, rest, now);
		out.hand = 1.0F + (lands(pose) ? pulse(age) : 0.0F);
		if (out.weapon != BLADE) {
			// the weapon forms out of the blade over the wind-up, swells at the blow, and melts back as the pose lets go
			float peak = out.weapon == GREAT || out.weapon == MAUL ? BIG_GROW : 1.0F;
			out.grow = weight * peak * (smooth(age / WIND_T) + pulse(age));
		}
	}

	/** Grimsby's weapons: the blade he always wears, and the forms a move makes of it. */
	static final int BLADE = 0, GREAT = 1, PIKE = 2, AXE_HEAD = 3, MAUL = 4, WEAPONS = 5;
	/** How big the greatsword and the hammer grow at full (Andy: "the greatsword should grow, the hammer should grow as well and slam"). */
	static final float BIG_GROW = 1.35F;
	/** The slam's wind-up: the arm straight up overhead (radians). */
	static final float SLAM_HIGH = -3.1F;

	/** Which of Grimsby's weapons {@code pose} makes. */
	static int weapon(int pose) {
		return switch (pose) {
			case Poses.GREATSWORD -> GREAT;
			case Poses.SPEAR -> PIKE;
			case Poses.AXE -> AXE_HEAD;
			case Poses.HAMMER -> MAUL;
			default -> BLADE;
		};
	}

	/** Wind-up, snap past the mark, settle: from {@code rest} to {@code wind}, then out to {@code aim}. */
	private static void strike(Frame now, float age, Frame rest, Frame wind, Frame aim) {
		if (age < WIND_T) {
			now.lerp(smooth(age / WIND_T), rest, wind);
		} else if (age < HIT_T) {
			now.lerp(backOut((age - WIND_T) / (HIT_T - WIND_T)), wind, aim);
		} else {
			copy(now, aim);
		}
	}

	/** The hand's swell at the hit: {@link #PULSE} at {@link #HIT_T}, gone {@link #PULSE_TICKS} later. */
	static float pulse(float age) {
		float since = age - HIT_T;
		return since < 0.0F || since > PULSE_TICKS ? 0.0F : PULSE * (1.0F - since / PULSE_TICKS);
	}

	private static void copy(Frame to, Frame from) {
		to.set(from.sx, from.sy, from.sz, from.elbow, from.wrist, from.curl, from.spread, from.reach);
	}

	private static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}

	/** An ease that runs past 1 and comes back (the snap of a blow). */
	private static float backOut(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F) - 1.0F;
		return 1.0F + k * k * ((OVERSHOOT + 1.0F) * k + OVERSHOOT);
	}

	// ---- the limb -------------------------------------------------------------------------------------------------------

	/** One arm's jointed parts (found once on the baked model). */
	static final class Limb {
		final ModelPart elbow, fore, wrist, thumb, thumbTip;
		final ModelPart[] fingers, tips;
		/** Grimsby's weapons by {@link #BLADE} and the rest; null for the others. */
		final ModelPart @org.jspecify.annotations.Nullable [] weapons;

		Limb(ModelPart arm, int kind) {
			this.elbow = arm.getChild("elbow");
			this.fore = this.elbow.getChild("fore");
			this.wrist = this.elbow.getChild("wrist");
			int n = fingers(kind);
			this.fingers = new ModelPart[n];
			this.tips = new ModelPart[n];
			for (int f = 0; f < n; f++) {
				this.fingers[f] = this.wrist.getChild("finger" + f);
				this.tips[f] = this.fingers[f].getChild("tip");
			}
			this.thumb = this.wrist.getChild("thumb");
			this.thumbTip = this.thumb.getChild("tip");
			if (this.wrist.hasChild("weapon0")) {
				this.weapons = new ModelPart[WEAPONS];
				for (int w = 0; w < WEAPONS; w++) {
					this.weapons[w] = this.wrist.getChild("weapon" + w);
				}
			} else {
				this.weapons = null;
			}
		}

		/** Sets the joints below the shoulder from {@code f} ({@code side} 1 the right arm, -1 the left). */
		void pose(Frame f, float side) {
			this.elbow.xRot = f.elbow;
			float stretch = Math.max(0.0F, f.reach);
			this.fore.yScale = 1.0F + stretch;
			this.wrist.y = FORE * (1.0F + stretch);
			this.wrist.xRot = f.wrist;
			this.wrist.xScale = f.hand;
			this.wrist.yScale = f.hand;
			this.wrist.zScale = f.hand;
			float curl = Mth.clamp(f.curl, 0.0F, 1.0F);
			float mid = (this.fingers.length - 1) * 0.5F;
			for (int i = 0; i < this.fingers.length; i++) {
				this.fingers[i].xRot = -curl * CURL_BASE;
				this.fingers[i].zRot = (mid - i) * SPREAD * f.spread * side;
				this.tips[i].xRot = -curl * CURL_TIP;
			}
			// the thumb lies out to the side when open and folds across the fingers in a fist
			this.thumb.zRot = side * Mth.lerp(curl, 0.55F + 0.3F * f.spread, -0.15F);
			this.thumb.xRot = -curl * 0.9F;
			this.thumbTip.xRot = -curl * 0.8F;
			if (this.weapons != null) {
				// the blade shrinks away as another weapon grows out of it
				float blade = f.weapon == BLADE ? 1.0F : Math.max(0.0F, 1.0F - f.grow / BLADE_YIELD);
				this.weapons[BLADE].visible = blade > 0.02F;
				scale(this.weapons[BLADE], Math.max(0.02F, blade));
				for (int w = 1; w < WEAPONS; w++) {
					boolean on = w == f.weapon && f.grow > 0.02F;
					this.weapons[w].visible = on;
					scale(this.weapons[w], on ? f.grow : 0.02F);
				}
			}
		}

		private static void scale(ModelPart part, float k) {
			part.xScale = k;
			part.yScale = k;
			part.zScale = k;
		}
	}

	/** How far another weapon has grown when the blade is gone. */
	static final float BLADE_YIELD = 0.4F;

	/** How many fingers (not counting the thumb) {@code kind}'s hand has. */
	static int fingers(int kind) {
		return switch (kind) {
			case Spirits.GRIMSBY, Spirits.QUIET -> 3;
			default -> 4;
		};
	}

	// ---- the mesh -------------------------------------------------------------------------------------------------------

	/**
	 * The mesh of one kind's two arms, inflated by {@code out} (one aura shell): each arm a shoulder at the vanilla offset with
	 * the upper arm, an elbow child with the forearm ({@code fore}, stretched on a thrust) and the wrist, the wrist holding the
	 * palm, the fingers (each with a {@code tip}) and the thumb. The other body parts are empty, the humanoid model needing them.
	 */
	static MeshDefinition mesh(int kind, float out) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		CubeDeformation g = new CubeDeformation(out);
		for (int i = 0; i < 2; i++) {
			// the right arm's centre is x -1 about its pivot, the left's +1 (vanilla's arm boxes); in is toward the body
			float cx = i == 0 ? -1.0F : 1.0F;
			float in = i == 0 ? 1.0F : -1.0F;
			PartDefinition arm = root.addOrReplaceChild(i == 0 ? "right_arm" : "left_arm", upper(kind, cx, g), PartPose.offset(i == 0 ? -5.0F : 5.0F, 2.0F, 0.0F));
			PartDefinition elbow = arm.addOrReplaceChild("elbow", joint(kind, cx, g), PartPose.offset(0.0F, ELBOW_Y, 0.0F));
			elbow.addOrReplaceChild("fore", fore(kind, cx, g), PartPose.ZERO);
			PartDefinition wrist = elbow.addOrReplaceChild("wrist", palm(kind, cx, in, g), PartPose.offset(0.0F, FORE, 0.0F));
			fingers(kind, wrist, cx, g);
			thumb(kind, wrist, cx, in, g);
			if (kind == Spirits.GRIMSBY) {
				weapons(wrist, cx, in, g);
			}
		}
		return mesh;
	}

	/** A box centred on x {@code cx} and z {@code cz}, from {@code y} down {@code h}. */
	private static void box(CubeListBuilder c, float cx, float y, float cz, float w, float h, float d, CubeDeformation g) {
		c.addBox(cx - w / 2.0F, y, cz - d / 2.0F, w, h, d, g);
	}

	private static CubeListBuilder cubes() {
		return CubeListBuilder.create().texOffs(0, 0);
	}

	/** The shoulder and the upper arm (y -2 to the elbow at 4). */
	private static CubeListBuilder upper(int kind, float cx, CubeDeformation g) {
		CubeListBuilder c = cubes();
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, cx, -2.0F, 0.0F, 3.0F, 6.0F, 3.0F, g);
				box(c, cx, -2.6F, 0.0F, 4.0F, 2.0F, 4.0F, g); // the shoulder's knob
			}
			case Spirits.VARRO -> {
				box(c, cx, -2.0F, 0.0F, 5.0F, 6.0F, 5.0F, g);
				box(c, cx, -3.2F, 0.0F, 7.0F, 3.2F, 6.4F, g); // the pauldron
				box(c, cx, -0.2F, 0.0F, 7.4F, 0.8F, 6.8F, g); // its rim
				box(c, cx, 2.0F, 0.0F, 5.6F, 0.8F, 5.6F, g); // a band above the elbow
			}
			case Spirits.AMBROSE -> {
				box(c, cx, -2.0F, 0.0F, 4.0F, 6.0F, 4.0F, g);
				box(c, cx, -2.4F, 0.0F, 4.6F, 1.6F, 4.6F, g); // the robe's shoulder
			}
			case Spirits.THESSALY -> {
				box(c, cx, -2.0F, 0.0F, 3.0F, 6.0F, 3.0F, g);
				box(c, cx, 1.0F, 0.0F, 3.4F, 0.6F, 3.4F, g); // an armlet
			}
			default -> box(c, cx, -2.0F, 0.0F, 4.0F, 6.0F, 4.0F, g);
		}
		return c;
	}

	/** The elbow joint itself (on the elbow part, so it bends with the forearm). */
	private static CubeListBuilder joint(int kind, float cx, CubeDeformation g) {
		CubeListBuilder c = cubes();
		switch (kind) {
			case Spirits.GRIMSBY -> box(c, cx, -0.9F, 0.0F, 3.4F, 1.8F, 3.4F, g); // a bony knob
			case Spirits.VARRO -> {
				box(c, cx, -1.0F, 0.0F, 5.8F, 2.0F, 5.8F, g); // the couter
				box(c, cx, -0.6F, 2.9F, 2.0F, 1.6F, 1.2F, g); // its point, at the back
			}
			case Spirits.AMBROSE -> box(c, cx, -0.4F, 0.0F, 5.2F, 3.0F, 5.2F, g); // the sleeve flaring
			case Spirits.THESSALY -> box(c, cx, -0.6F, 0.0F, 3.2F, 1.2F, 3.2F, g);
			default -> box(c, cx, -0.8F, 0.0F, 4.1F, 1.6F, 4.1F, g); // smooth, the seam hidden
		}
		return c;
	}

	/** The forearm, elbow to wrist (scaled long on a thrust). */
	private static CubeListBuilder fore(int kind, float cx, CubeDeformation g) {
		CubeListBuilder c = cubes();
		switch (kind) {
			case Spirits.GRIMSBY -> box(c, cx, 0.0F, 0.0F, 2.6F, FORE, 2.6F, g);
			case Spirits.VARRO -> {
				box(c, cx, 0.0F, 0.0F, 5.0F, FORE, 5.0F, g); // the vambrace
				box(c, cx, FORE - 1.2F, 0.0F, 5.8F, 1.2F, 5.8F, g); // its cuff
			}
			case Spirits.AMBROSE -> box(c, cx, 0.0F, 0.0F, 3.6F, FORE, 3.6F, g);
			case Spirits.THESSALY -> box(c, cx, 0.0F, 0.0F, 2.6F, FORE, 2.6F, g);
			default -> box(c, cx, 0.0F, 0.0F, 4.0F, FORE, 4.0F, g);
		}
		return c;
	}

	/** The wrist's cubes: the palm, and what hangs from the wrist (Grimsby's blade, Ambrose's sigil, Thessaly's bracelet). */
	private static CubeListBuilder palm(int kind, float cx, float in, CubeDeformation g) {
		CubeListBuilder c = cubes();
		switch (kind) {
			case Spirits.GRIMSBY -> box(c, cx, 0.0F, 0.0F, 3.0F, 2.6F, 2.0F, g); // (the blade and the forms are weapons(), below)
			case Spirits.VARRO -> {
				box(c, cx, 0.0F, 0.0F, 6.0F, 4.0F, 5.0F, g); // the gauntlet's palm
				for (int s = 0; s < 4; s++) {
					box(c, cx - 2.25F + s * 1.5F, 3.2F, -2.7F, 1.1F, 1.1F, 1.0F, g); // knuckle studs
				}
			}
			case Spirits.AMBROSE -> {
				box(c, cx, 0.0F, 0.0F, 6.0F, 5.0F, 2.0F, g); // the broad palm
				box(c, cx, 1.2F, -1.1F, 3.0F, 3.0F, 0.3F, g); // the ward sigil on its face
				box(c, cx, -0.6F, 0.0F, 4.4F, 1.0F, 4.4F, g); // the sleeve's cuff
			}
			case Spirits.THESSALY -> {
				box(c, cx, 0.0F, 0.0F, 2.8F, 2.6F, 1.4F, g);
				box(c, cx, -0.6F, 0.0F, 3.2F, 0.8F, 3.2F, g); // the bracelet
			}
			default -> box(c, cx, 0.0F, 0.0F, 4.0F, 3.0F, 2.2F, g); // smooth
		}
		return c;
	}

	/**
	 * Grimsby's weapons, each its own part on the wrist so it can grow out of the fist ({@code weapon0}-{@code weapon4}): the
	 * blade on the outside of the wrist as he always wears it, then the greatsword (broad, with a guard and a fuller), the spear
	 * (a long shaft and a barbed head), the axe (a haft and a bearded head facing forward, the way it chops) and the hammer (a
	 * haft, a great head with a spike behind). Each grows from its own root, so it swells out from the hand.
	 */
	private static void weapons(PartDefinition wrist, float cx, float in, CubeDeformation g) {
		CubeListBuilder blade = cubes();
		// from the wrist on the outside, longer than the fingers, a guard at its root and a point at its end
		box(blade, 0.0F, 0.0F, 0.0F, 0.8F, 12.0F, 2.6F, g);
		box(blade, 0.0F, 12.0F, 0.0F, 0.8F, 1.6F, 1.4F, g);
		box(blade, 0.0F, -0.2F, 0.0F, 1.6F, 0.8F, 3.6F, g);
		wrist.addOrReplaceChild("weapon0", blade, PartPose.offset(cx - in * 1.6F, -0.6F, 0.0F));
		// the others grow from the fist, the fingers closed round them
		PartPose fist = PartPose.offset(cx, 3.0F, -0.6F);
		CubeListBuilder great = cubes();
		box(great, 0.0F, -1.0F, 0.0F, 1.6F, 3.0F, 1.6F, g); // the grip
		box(great, 0.0F, 2.0F, 0.0F, 7.0F, 1.2F, 2.0F, g); // the cross-guard
		box(great, 0.0F, 3.2F, 0.0F, 1.0F, 15.0F, 4.6F, g); // the blade
		box(great, 0.0F, 3.6F, 0.0F, 1.3F, 12.0F, 1.2F, g); // its fuller, standing proud
		box(great, 0.0F, 18.2F, 0.0F, 1.0F, 2.0F, 2.6F, g); // the point
		wrist.addOrReplaceChild("weapon1", great, fist);
		CubeListBuilder spear = cubes();
		box(spear, 0.0F, -3.0F, 0.0F, 1.0F, 22.0F, 1.0F, g); // the shaft, a little behind the fist too
		box(spear, 0.0F, 19.0F, 0.0F, 1.4F, 1.0F, 1.4F, g); // the collar
		box(spear, 0.0F, 20.0F, 0.0F, 0.6F, 5.0F, 2.6F, g); // the head
		box(spear, 0.0F, 21.0F, 0.0F, 0.6F, 1.4F, 3.8F, g); // its barbs
		box(spear, 0.0F, 25.0F, 0.0F, 0.6F, 1.4F, 1.0F, g); // the point
		wrist.addOrReplaceChild("weapon2", spear, fist);
		CubeListBuilder axe = cubes();
		box(axe, 0.0F, -1.0F, 0.0F, 1.2F, 13.0F, 1.2F, g); // the haft
		axe.addBox(-0.5F, 7.0F, -6.5F, 1.0F, 5.0F, 6.0F, g); // the head, facing forward
		axe.addBox(-0.5F, 5.5F, -7.5F, 1.0F, 8.0F, 1.5F, g); // its bearded edge
		axe.addBox(-0.4F, 8.5F, 0.6F, 0.8F, 2.0F, 2.0F, g); // the back spike
		wrist.addOrReplaceChild("weapon3", axe, fist);
		CubeListBuilder maul = cubes();
		box(maul, 0.0F, -1.0F, 0.0F, 1.4F, 12.0F, 1.4F, g); // the haft
		box(maul, 0.0F, 10.0F, -1.0F, 5.0F, 5.0F, 8.0F, g); // the head, long front to back
		box(maul, 0.0F, 10.5F, -5.6F, 5.6F, 4.0F, 1.2F, g); // its striking face
		maul.addBox(-0.6F, 11.6F, 3.0F, 1.2F, 1.8F, 3.0F, g); // the spike behind
		wrist.addOrReplaceChild("weapon4", maul, fist);
	}

	/** Each kind's palm length (where the fingers start), finger width and segment lengths, and how wide the fingers are spaced. */
	private static float palmLength(int kind) {
		return switch (kind) {
			case Spirits.GRIMSBY -> 2.6F;
			case Spirits.VARRO -> 4.0F;
			case Spirits.AMBROSE -> 5.0F;
			case Spirits.THESSALY -> 2.6F;
			default -> 3.0F;
		};
	}

	private static void fingers(int kind, PartDefinition wrist, float cx, CubeDeformation g) {
		int n = fingers(kind);
		float base, tip, width, span;
		switch (kind) {
			case Spirits.GRIMSBY -> {
				base = 3.0F;
				tip = 3.0F;
				width = 0.8F;
				span = 2.4F;
			}
			case Spirits.VARRO -> {
				base = 2.4F;
				tip = 2.2F;
				width = 1.3F;
				span = 4.5F;
			}
			case Spirits.AMBROSE -> {
				base = 3.2F;
				tip = 2.8F;
				width = 1.1F;
				span = 4.6F;
			}
			case Spirits.THESSALY -> {
				base = 2.6F;
				tip = 2.4F;
				width = 0.6F;
				span = 2.1F;
			}
			default -> {
				base = 3.8F;
				tip = 3.4F;
				width = 0.9F;
				span = 2.6F;
			}
		}
		float y = palmLength(kind);
		for (int f = 0; f < n; f++) {
			float x = cx - span / 2.0F + (n == 1 ? 0.0F : span * f / (n - 1));
			// the middle fingers are a touch longer
			float longer = (n > 2 && f > 0 && f < n - 1) ? 0.4F : 0.0F;
			CubeListBuilder b = cubes();
			box(b, 0.0F, 0.0F, 0.0F, width, base + longer, width, g);
			if (kind == Spirits.VARRO) {
				box(b, 0.0F, 0.2F, -0.3F, width + 0.3F, 1.0F, width * 0.6F, g); // a finger plate
			}
			if (kind == Spirits.THESSALY && f < 3) {
				box(b, 0.0F, 0.8F, 0.0F, width + 0.5F, 0.5F, width + 0.5F, g); // a ring
			}
			if (kind == Spirits.GRIMSBY) {
				box(b, 0.0F, base - 0.5F, 0.0F, width + 0.4F, 0.8F, width + 0.4F, g); // the knuckle's knob
			}
			PartDefinition finger = wrist.addOrReplaceChild("finger" + f, b, PartPose.offset(x, y, 0.0F));
			CubeListBuilder t = cubes();
			box(t, 0.0F, 0.0F, 0.0F, width * 0.9F, tip, width * 0.9F, g);
			if (kind == Spirits.VARRO || kind == Spirits.GRIMSBY) {
				box(t, 0.0F, tip - 0.4F, -0.2F, width * 0.6F, 0.9F, width * 0.6F, g); // a claw's point
			}
			finger.addOrReplaceChild("tip", t, PartPose.offset(0.0F, base + longer, 0.0F));
		}
	}

	private static void thumb(int kind, PartDefinition wrist, float cx, float in, CubeDeformation g) {
		float width = switch (kind) {
			case Spirits.VARRO -> 1.5F;
			case Spirits.AMBROSE -> 1.2F;
			case Spirits.THESSALY -> 0.7F;
			default -> 1.0F;
		};
		float half = switch (kind) {
			case Spirits.VARRO -> 3.0F;
			case Spirits.AMBROSE -> 3.0F;
			case Spirits.GRIMSBY, Spirits.THESSALY -> 1.4F;
			default -> 2.0F;
		};
		CubeListBuilder b = cubes();
		box(b, 0.0F, 0.0F, 0.0F, width, 2.2F, width, g);
		PartDefinition thumb = wrist.addOrReplaceChild("thumb", b, PartPose.offset(cx + in * half, palmLength(kind) * 0.35F, -0.3F));
		CubeListBuilder t = cubes();
		box(t, 0.0F, 0.0F, 0.0F, width * 0.9F, 2.0F, width * 0.9F, g);
		thumb.addOrReplaceChild("tip", t, PartPose.offset(0.0F, 2.2F, 0.0F));
	}
}
