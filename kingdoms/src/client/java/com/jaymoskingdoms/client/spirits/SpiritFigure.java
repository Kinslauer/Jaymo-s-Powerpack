package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.client.spirits.SpiritArms.Anchor;
import com.jaymoskingdoms.spirits.Spirits;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * The spirit's figure: a faint warrior you can half-see, which comes fully into being when it speaks (Andy: "a faint but
 * visible warrior ... kind of like Johnny from cyberpunk"). {@link SpiritManifest} draws it in two places: faint over its
 * keeper's back, its torso's top level with the arms that hang from its shoulders ({@link #shoulder}, {@link #back}), and
 * manifested, standing on the ground in front of its keeper at about a person's size ({@link #STAGE}) with its own arms
 * talking. Each kind has its own make:
 * <ul>
 * <li>Grimsby: a dead traveller, a gaunt swordsman's bones under a tattered cowl and cloak, a scabbard at his hip.</li>
 * <li>Varro: a warlord in plate, great pauldrons, a crested and plumed helm with a visor slit, tassets and sabatons.</li>
 * <li>Ambrose: an elder in a hooded robe, a long beard under bushy brows, mantle and stole, wide sleeves, a satchel of scrolls.</li>
 * <li>Thessaly: slim, a high collar and shawl, hair long down her back and in side locks under a circlet, bell sleeves.</li>
 * <li>The Quiet One: never a body (Andy: "only animating the large arms for the quiet one"): only its arms, when it fights.</li>
 * <li>The Grue ({@link #GRUE}, not a spirit): ghastly and winged, a horned skull under a torn hood, long clawed arms, great
 * ragged wings. It fights the spirits when it lashes at their keeper, and is the body of the boss in its trial.</li>
 * </ul>
 * None of them has legs (Andy: "the lower half should be wispy"): below the waist each thins into smoke that trails away.
 * The mesh is written from {@code tools/blockbench/spirit-figures/figures.py} by {@code gen.py} (edit those, not this file);
 * the same script writes the .bbmodels, with the moves below keyed as Blockbench animations.
 */
final class SpiritFigure {
	/** On a figure's own render state: how to pose it this frame. */
	static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create(() -> "jaymoskingdoms:spirit_figure");
	/** The Grue's figure, after the five spirits' (by the same index in every table here). */
	static final int GRUE = Spirits.QUIET + 1, KINDS = GRUE + 1;

	/** Each kind's figure, by kind: its scale over your back (a player's is 1), its torso's half width and how far it reaches forward of its middle (model pixels at scale 1), and the lower body's root below the shoulder line. */
	static final float[] SCALE = {1.6F, 1.9F, 1.8F, 1.2F, 1.7F, 1.6F};
	static final float[] HALF = {3.5F, 6.0F, 5.0F, 3.2F, 4.0F, 4.0F};
	static final float[] FRONT = {3.3F, 5.0F, 4.0F, 3.6F, 3.45F, 3.4F};
	static final float[] TAIL_Y = {11.0F, 12.0F, 12.0F, 11.0F, 12.0F, 12.0F};
	/** The sole below the shoulder line (model pixels at scale 1), and the manifested figure's scale. */
	static final float[] FOOT = {19.8F, 33.4F, 22.0F, 20.6F, 24.0F, 24.0F};
	static final float[] STAGE = {1.1F, 1.05F, 1.1F, 1.0F, 1.0F, 1.55F};
	/** Where the figure's own right arm hangs from (model pixels at scale 1; the left mirrors it). */
	static final float[] ARM_X = {-4.4F, -8.0F, -6.4F, -4.2F, -5.0F, -5.0F};
	static final float[] ARM_Y = {0.8F, 0.6F, 0.8F, 0.8F, 0.8F, 0.8F};
	/** Where the figure's arm bends: the elbow, below its shoulder (model pixels at scale 1). */
	static final float[] ELBOW = {5.6F, 4.8F, 6.0F, 5.2F, 6.0F, 6.6F};
	/** Where the right wing grows from its back (model pixels at scale 1; only the Grue has wings). */
	static final float[] WING_X = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6F};
	static final float[] WING_Y = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.2F};
	static final float[] WING_Z = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.2F};
	/** How far each kind's arm reaches in toward the body from its pivot, per unit of arm size (half its upper arm's width, less the vanilla 1). */
	static final float[] ARM_INNER = {0.5F, 1.5F, 1.0F, 0.5F, 1.0F, 1.0F};
	/** Model pixels the figure keeps clear of your back and head (a player's head reaches 4 behind the middle). */
	static final float BEHIND = 4.5F;
	/** How long the figure holds after a move shows it, then how long it takes to fade back to faint (ticks). The arms keep their own time. */
	static final float HOLD_TICKS = 8.0F, FADE_TICKS = 16.0F;
	/** The pulse: how far its light swells and how fast, how much it throbs in size, and how far it swells as it fades (breaking up). */
	static final float PULSE = 0.35F, PULSE_SPEED = 0.3F, THROB = 0.03F, SURGE = 0.12F;
	/** The lower body's sway (radians, and how fast). */
	static final float TAIL_SWAY = 0.12F, TAIL_SPEED = 0.07F, STAGE_SWAY = 0.03F;
	/** How far the figure's head turns with yours, over your back. */
	static final float HEAD_FOLLOW = 0.5F;
	/** How far the manifested figure hovers off the ground, and how far it rises from as it forms (model pixels). */
	static final float HOVER = 3.0F, RISE_FROM = 4.0F, BOB = 0.8F;
	/** Talking: the head's nod and tilt, the right hand making its point, the left hand's sway (radians), and how fast. */
	static final float NOD = 0.08F, TILT = 0.06F, POINT_X = -0.85F, POINT_SWING = 0.28F, POINT_TURN = 0.35F, HANG_SWAY = 0.08F, TALK_SPEED = 0.3F;
	/** Breathing at rest: how far the arms drift, and how fast. */
	static final float BREATH = 0.035F, BREATH_SPEED = 0.08F;
	/** Level up: arms flung up (radians), spread, and the ticks to throw them up, hold, and bring them down. */
	static final float LEVEL_RAISE = -2.75F, LEVEL_SPREAD = 0.45F, LEVEL_UP = 6.0F, LEVEL_HOLD = 18.0F, LEVEL_DOWN = 10.0F;
	/** Forming: how far the arms are spread as it rises (radians). Fading: how far the head bows. */
	static final float FORM_SPREAD = 0.8F, FADE_BOW = 0.35F;
	/** A blow (the fight with the Grue, the Grue's own): the arm drawn back, then thrown forward (radians), and the share of the swing spent drawing back. */
	static final float STRIKE_BACK = 0.7F, STRIKE_OUT = -1.95F, STRIKE_DRAW = 0.45F, STRIKE_LEAN = 0.18F;
	/** The elbow (radians, negative bends the forearm forward and up): at rest, talking, drawn back for a blow, thrown out, and arms up. */
	static final float ELBOW_REST = -0.25F, ELBOW_TALK = -0.95F, ELBOW_TALK_SWING = 0.3F, ELBOW_DRAWN = -1.7F, ELBOW_THROWN = -0.08F, ELBOW_UP = -0.2F;
	/** The wings: how far spread at rest (radians back from the body), how far they beat, and how fast at a flap of 1. */
	static final float WING_SPREAD = 0.45F, WING_BEAT = 0.55F, WING_SPEED = 0.35F, WING_TIP = 0.25F;
	/** Its shells: thinner and dimmer than the arms', so the arms read over it. */
	static final float[] SHELLS = {0.0F, 0.5F, 1.2F}, SHELL_LIGHT = {0.45F, 0.16F, 0.06F};

	private SpiritFigure() {
	}

	/**
	 * How one figure stands this frame. {@code staged}: manifested on the ground (else faint over its keeper's back, placed by
	 * {@code style} and {@code size} as the arms are). {@code manifest} 0-1 how far formed (it rises and closes its arms as it
	 * forms, bows as it goes); {@code talk} 0-1 how much it gestures; {@code levelAge} ticks since a level up, or -1;
	 * {@code headYaw} and {@code headPitch} where its head turns (radians).
	 */
	record Frame(int kind, boolean staged, int style, float size, float manifest, float talk, float levelAge, float headYaw, float headPitch,
		boolean fading, float strike, float strikePhase, float flap, boolean armsOnly) {
		/** A frame with no fighting in it. */
		Frame(int kind, boolean staged, int style, float size, float manifest, float talk, float levelAge, float headYaw, float headPitch, boolean fading) {
			this(kind, staged, style, size, manifest, talk, levelAge, headYaw, headPitch, fading, 0.0F, 0.0F, 0.0F, false);
		}
	}

	/** The elbow at {@code phase} (0-1) of a blow: folded as the arm draws back, snapped straight as it lands, then easing to rest. */
	static float elbowSwing(float phase) {
		float p = phase - (float)Math.floor(phase);
		if (p < STRIKE_DRAW) {
			float k = p / STRIKE_DRAW;
			return Mth.lerp(k * k * (3.0F - 2.0F * k), ELBOW_REST, ELBOW_DRAWN);
		}
		float k = (p - STRIKE_DRAW) / (1.0F - STRIKE_DRAW);
		return k < 0.15F ? Mth.lerp(k / 0.15F, ELBOW_DRAWN, ELBOW_THROWN) : Mth.lerp((k - 0.15F) / 0.85F, ELBOW_THROWN, ELBOW_REST);
	}

	/** The arm's swing at {@code phase} (0-1) of a blow: drawn back, thrown forward, recovered (radians on x). */
	static float swing(float phase) {
		float p = phase - (float)Math.floor(phase);
		if (p < STRIKE_DRAW) {
			float k = p / STRIKE_DRAW;
			return STRIKE_BACK * k * k * (3.0F - 2.0F * k);
		}
		float k = (p - STRIKE_DRAW) / (1.0F - STRIKE_DRAW);
		// the snap: most of the way out in the first fifth, then easing back toward rest
		float out = k < 0.2F ? k / 0.2F : 1.0F - (k - 0.2F) / 0.8F;
		return Mth.lerp(Math.min(k / 0.2F, 1.0F), STRIKE_BACK, STRIKE_OUT * out);
	}

	/** How strong the figure's flare is {@code since} ticks after a move showed it: up at once, held, then faded out. */
	static float strength(float since) {
		if (since < 0.0F) {
			return 0.0F;
		}
		if (since < SpiritArms.APPEAR_TICKS) {
			return since / SpiritArms.APPEAR_TICKS;
		}
		float k = Mth.clamp((since - HOLD_TICKS) / FADE_TICKS, 0.0F, 1.0F);
		return 1.0F - k * k * (3.0F - 2.0F * k);
	}

	/** The light's pulse at {@code ageInTicks}. */
	static float pulse(float ageInTicks) {
		return 1.0F + PULSE * Mth.sin(ageInTicks * PULSE_SPEED);
	}

	/** The figure's scale for a spirit of {@code kind} conjured at arm size {@code size} (the Quiet One's changes with it). */
	static float scale(int kind, float size) {
		return SCALE[kind] * size / Spirits.of(kind).size();
	}

	/** How far out from your middle the figure's shoulders (its arms' pivots) are, in model pixels. */
	static float shoulder(int kind, float size) {
		return scale(kind, size) * HALF[kind] + ARM_INNER[kind] * size;
	}

	/** How far behind your middle the figure stands (and its arms' pivots), in model pixels: where its anchor says, but clear of you. */
	static float back(int kind, Anchor anchor, float lift, float size) {
		return Math.max(anchor.back() * lift, BEHIND + FRONT[kind] * scale(kind, size));
	}

	/** How much of a level up's arms-up is on, {@code age} ticks after it. */
	static float levelRaise(float age) {
		if (age < 0.0F) {
			return 0.0F;
		}
		if (age < LEVEL_UP) {
			float k = age / LEVEL_UP;
			return k * k * (3.0F - 2.0F * k);
		}
		float k = Mth.clamp((age - LEVEL_UP - LEVEL_HOLD) / LEVEL_DOWN, 0.0F, 1.0F);
		return 1.0F - k * k * (3.0F - 2.0F * k);
	}

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/**
	 * One shell of one kind's figure. A HumanoidModel whose only visible part is the body, which holds the torso's cubes, the
	 * {@code skull} (at the neck), the {@code tail} (the lower body, at the waist) and its own two arms (shown only manifested).
	 */
	static final class Model extends HumanoidModel<HumanoidRenderState> {
		private final int kind;
		private final ModelPart skull, tail, armR, armL, foreR, foreL, wingR, wingL;

		Model(ModelPart root, int kind) {
			super(root);
			this.kind = kind;
			this.skull = this.body.getChild("skull");
			this.tail = this.body.getChild("tail");
			this.armR = this.body.getChild("figure_right_arm");
			this.armL = this.body.getChild("figure_left_arm");
			this.foreR = this.armR.getChild("forearm");
			this.foreL = this.armL.getChild("forearm");
			this.wingR = this.body.getChild("figure_right_wing");
			this.wingL = this.body.getChild("figure_left_wing");
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			this.head.visible = false;
			this.hat.visible = false;
			this.rightArm.visible = false;
			this.leftArm.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			Frame frame = state.getData(FRAME);
			this.body.visible = frame != null;
			if (frame == null) {
				return;
			}
			float t = state.ageInTicks;
			float unformed = 1.0F - frame.manifest();
			if (!frame.staged()) {
				// over your back: its torso's top level with its arms' tops, its middle behind you; the spirit's real arms hang from it
				Anchor anchor = SpiritArms.anchor(frame.style());
				float lift = Mth.clamp(frame.size(), 1.0F, SpiritArms.FLOAT_SIZE_CAP);
				float s = scale(this.kind, frame.size()) * (1.0F + THROB * Mth.sin(t * PULSE_SPEED) + SURGE * unformed);
				this.body.x = -anchor.right() * lift;
				this.body.y = SpiritArms.ARM_PIVOT_Y - anchor.up() * lift - 2.0F * frame.size();
				this.body.z = back(this.kind, anchor, lift, frame.size());
				this.body.xScale = s;
				this.body.yScale = s;
				this.body.zScale = s;
				this.armR.visible = false;
				this.armL.visible = false;
				this.wingR.visible = false;
				this.wingL.visible = false;
				this.skull.yRot = frame.headYaw() * HEAD_FOLLOW;
				this.skull.xRot = frame.headPitch() * HEAD_FOLLOW;
				this.tail.xRot = TAIL_SWAY * (0.5F + 0.5F * Mth.sin(t * TAIL_SPEED));
				this.tail.zRot = TAIL_SWAY * 0.5F * Mth.sin(t * TAIL_SPEED * 0.7F);
				return;
			}
			// manifested: floating over the ground (the model's ground is at y 24), its smoke's tip just clear, rising as it forms
			float s = STAGE[this.kind] * (1.0F + SURGE * 0.5F * unformed);
			this.body.x = 0.0F;
			this.body.z = 0.0F;
			this.body.y = 24.0F - (FOOT[this.kind] * s) - HOVER + RISE_FROM * unformed * unformed + BOB * Mth.sin(t * BREATH_SPEED);
			// the Quiet One has no body: only its arms show, hanging where its shoulders would be
			this.skull.visible = !frame.armsOnly();
			this.tail.visible = !frame.armsOnly();
			this.body.skipDraw = frame.armsOnly();
			this.wingR.visible = frame.flap() > 0.0F;
			this.wingL.visible = frame.flap() > 0.0F;
			if (frame.flap() > 0.0F) {
				float beat = Mth.sin(t * WING_SPEED * frame.flap());
				this.wingR.yRot = WING_SPREAD;
				this.wingL.yRot = -WING_SPREAD;
				this.wingR.zRot = WING_BEAT * beat - WING_TIP;
				this.wingL.zRot = -WING_BEAT * beat + WING_TIP;
				// it rides its wingbeats
				this.body.y -= 0.6F * beat;
			}
			this.body.xScale = s;
			this.body.yScale = s;
			this.body.zScale = s;
			this.armR.visible = true;
			this.armL.visible = true;
			this.tail.xRot = STAGE_SWAY * Mth.sin(t * TAIL_SPEED);
			this.tail.zRot = STAGE_SWAY * 0.5F * Mth.sin(t * TAIL_SPEED * 0.7F);
			// the head: looks at you, nodding and tilting as it talks, bowing as it goes
			float talk = frame.talk();
			this.skull.yRot = frame.headYaw();
			this.skull.xRot = frame.headPitch() + talk * NOD * Mth.sin(t * TALK_SPEED * 1.8F) + (frame.fading() ? FADE_BOW * unformed : 0.0F);
			this.skull.zRot = talk * TILT * Mth.sin(t * TALK_SPEED * 1.03F);
			// the arms: at rest they breathe; talking, the right hand makes its point and the left sways; a level up flings both high
			float breath = BREATH * Mth.sin(t * BREATH_SPEED);
			float rx = Mth.lerp(talk, -0.1F + breath, POINT_X - POINT_SWING * Mth.sin(t * TALK_SPEED));
			float ry = talk * (POINT_TURN + 0.15F * Mth.sin(t * TALK_SPEED * 0.7F));
			float rz = Mth.lerp(talk, 0.12F, -0.1F);
			float lx = 0.05F - breath - talk * (0.25F + HANG_SWAY * Mth.sin(t * TALK_SPEED + 1.0F));
			// the elbows: a little bent at rest, the talking hand's folded up and turning, both near straight flung up
			float re = Mth.lerp(talk, ELBOW_REST, ELBOW_TALK + ELBOW_TALK_SWING * Mth.sin(t * TALK_SPEED * 1.3F));
			float le = Mth.lerp(talk, ELBOW_REST, ELBOW_REST * 1.6F);
			float lz = -0.12F;
			float up = levelRaise(frame.levelAge());
			rx = Mth.lerp(up, rx, LEVEL_RAISE);
			lx = Mth.lerp(up, lx, LEVEL_RAISE);
			rz = Mth.lerp(up, rz, LEVEL_SPREAD);
			re = Mth.lerp(up, re, ELBOW_UP);
			le = Mth.lerp(up, le, ELBOW_UP);
			lz = Mth.lerp(up, lz, -LEVEL_SPREAD);
			// forming, the arms are spread wide and close in
			rz += FORM_SPREAD * unformed;
			lz -= FORM_SPREAD * unformed;
			// fighting: the arms trade blows, the right then the left, the body leaning into each
			float w = frame.strike();
			if (w > 0.0F) {
				rx = Mth.lerp(w, rx, swing(frame.strikePhase()));
				lx = Mth.lerp(w, lx, swing(frame.strikePhase() + 0.5F));
				ry = Mth.lerp(w, ry, -0.2F);
				re = Mth.lerp(w, re, elbowSwing(frame.strikePhase()));
				le = Mth.lerp(w, le, elbowSwing(frame.strikePhase() + 0.5F));
				this.body.xRot = w * STRIKE_LEAN * Mth.sin(frame.strikePhase() * Mth.TWO_PI * 2.0F);
			}
			this.armR.xRot = rx;
			this.armR.yRot = ry;
			this.armR.zRot = rz;
			this.armL.xRot = lx;
			this.armL.zRot = lz;
			this.foreR.xRot = re;
			this.foreL.xRot = le;
		}
	}

	// ---- the mesh ----------------------------------------------------------------------------------------------------------

	/** The mesh of one kind's figure, inflated by {@code out} (one shell). The other humanoid parts are empty. */
	static MeshDefinition mesh(int kind, float out) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		CubeDeformation g = new CubeDeformation(out);
		PartDefinition body = root.addOrReplaceChild("body", torso(kind, g), PartPose.ZERO);
		body.addOrReplaceChild("skull", skull(kind, g), PartPose.ZERO);
		body.addOrReplaceChild("tail", tail(kind, g), PartPose.offset(0.0F, TAIL_Y[kind], 0.0F));
		PartDefinition armR = body.addOrReplaceChild("figure_right_arm", arm(kind, g, 1.0F), PartPose.offset(ARM_X[kind], ARM_Y[kind], 0.0F));
		PartDefinition armL = body.addOrReplaceChild("figure_left_arm", arm(kind, g, -1.0F), PartPose.offset(-ARM_X[kind], ARM_Y[kind], 0.0F));
		armR.addOrReplaceChild("forearm", fore(kind, g, 1.0F), PartPose.offset(0.0F, ELBOW[kind], 0.0F));
		armL.addOrReplaceChild("forearm", fore(kind, g, -1.0F), PartPose.offset(0.0F, ELBOW[kind], 0.0F));
		body.addOrReplaceChild("figure_right_wing", wing(kind, g, 1.0F), PartPose.offset(WING_X[kind], WING_Y[kind], WING_Z[kind]));
		body.addOrReplaceChild("figure_left_wing", wing(kind, g, -1.0F), PartPose.offset(-WING_X[kind], WING_Y[kind], WING_Z[kind]));
		return mesh;
	}

	/** A box centred on x {@code cx} and z {@code cz}, from {@code y} down {@code h}. */
	private static void box(CubeListBuilder c, float cx, float y, float cz, float w, float h, float d, CubeDeformation g) {
		c.addBox(cx - w / 2.0F, y, cz - d / 2.0F, w, h, d, g);
	}

	/** The torso, from the shoulder line (y 0) down (model -z ahead). */
	private static CubeListBuilder torso(int kind, CubeDeformation g) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, 0.0F, 0.0F, 0.0F, 7.0F, 11.0F, 3.5F, g); // the gaunt chest
				box(c, 0.0F, 0.2F, 0.0F, 8.0F, 1.0F, 3.0F, g); // the collarbones
				box(c, 0.0F, 2.6F, -1.9F, 6.0F, 0.7F, 0.6F, g); // ribs
				box(c, 0.0F, 4.4F, -1.9F, 5.6F, 0.7F, 0.6F, g);
				box(c, 0.0F, 6.2F, -1.9F, 5.0F, 0.7F, 0.6F, g);
				box(c, 0.0F, 8.0F, -1.9F, 4.2F, 0.7F, 0.6F, g);
				box(c, 0.0F, 2.0F, 2.0F, 1.2F, 1.2F, 1.0F, g); // the spine's knobs
				box(c, 0.0F, 5.0F, 2.0F, 1.2F, 1.2F, 1.0F, g);
				box(c, 0.0F, 8.0F, 2.0F, 1.2F, 1.2F, 1.0F, g);
				box(c, 0.0F, -0.4F, 2.4F, 9.0F, 14.0F, 0.7F, g); // the cloak, hanging down the back
				box(c, -3.4F, -0.6F, 0.4F, 2.6F, 1.6F, 4.6F, g); // rags over the shoulders
				box(c, 3.4F, -0.6F, 0.4F, 2.6F, 1.6F, 4.6F, g);
				box(c, 0.0F, 9.6F, 0.0F, 7.6F, 1.0F, 4.0F, g); // a rope belt
				box(c, 2.9F, 9.0F, 1.4F, 1.2F, 1.2F, 1.2F, g); // its knot
				box(c, -4.0F, 9.4F, 0.6F, 1.2F, 9.0F, 1.6F, g); // the scabbard at the hip
			}
			case Spirits.VARRO -> {
				box(c, 0.0F, 0.0F, 0.0F, 12.0F, 12.0F, 6.0F, g); // the plated chest
				box(c, 0.0F, 1.0F, -3.2F, 10.0F, 7.0F, 1.0F, g); // the breastplate, standing proud
				box(c, 0.0F, 2.4F, -3.8F, 2.0F, 4.6F, 0.6F, g); // its ridge
				box(c, 0.0F, 8.4F, -3.2F, 9.0F, 1.2F, 0.6F, g); // the fauld's plates
				box(c, 0.0F, 9.8F, -3.2F, 9.6F, 1.2F, 0.6F, g);
				box(c, 0.0F, -1.2F, 0.0F, 7.0F, 1.6F, 5.0F, g); // the gorget
				box(c, 0.0F, 10.0F, 0.0F, 12.6F, 2.0F, 6.6F, g); // the belt
				box(c, 0.0F, 10.4F, -3.5F, 2.4F, 1.4F, 0.6F, g); // its buckle
				box(c, 0.0F, 0.4F, 3.4F, 11.0F, 16.0F, 0.8F, g); // the war cloak
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, 0.0F, 0.0F, 10.0F, 12.0F, 5.0F, g); // the robed chest
				box(c, 0.0F, -0.6F, 0.0F, 11.4F, 3.0F, 6.2F, g); // the mantle
				box(c, -2.4F, 2.0F, -2.8F, 1.6F, 10.0F, 0.6F, g); // the stole
				box(c, 2.4F, 2.0F, -2.8F, 1.6F, 10.0F, 0.6F, g);
				box(c, 0.0F, 9.4F, 0.0F, 10.6F, 1.0F, 5.6F, g); // the cord belt
				box(c, -1.6F, 10.2F, -2.9F, 0.6F, 3.0F, 0.6F, g); // its tassel
				box(c, 5.2F, 6.4F, 0.6F, 1.6F, 4.0F, 3.6F, g); // a satchel of scrolls
				box(c, 5.2F, 5.4F, 1.0F, 1.0F, 1.4F, 1.0F, g); // a scroll's end
			}
			case Spirits.THESSALY -> {
				box(c, 0.0F, 0.0F, 0.0F, 6.4F, 6.0F, 3.4F, g); // the slim chest
				box(c, 0.0F, 6.0F, 0.0F, 5.0F, 5.0F, 3.0F, g); // the waist
				box(c, 0.0F, -0.4F, 0.4F, 7.6F, 2.0F, 4.0F, g); // a shawl
				box(c, 0.0F, -1.4F, 0.0F, 4.6F, 1.2F, 3.8F, g); // the high collar
				box(c, 0.0F, 1.6F, -1.9F, 1.2F, 1.2F, 0.5F, g); // a brooch
				box(c, 0.0F, 9.6F, 0.0F, 5.6F, 1.0F, 3.4F, g); // the sash
				box(c, 1.8F, 10.4F, -1.7F, 1.0F, 4.0F, 0.4F, g); // its trailing end
			}
			case Spirits.QUIET -> {
				box(c, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 4.0F, g); // the smooth chest
				box(c, 0.0F, 8.0F, 0.0F, 6.6F, 4.0F, 3.4F, g); // the waist
			}
			default -> {
				box(c, 0.0F, 0.0F, 0.0F, 8.0F, 12.0F, 4.0F, g); // the starved chest
				box(c, 0.0F, 0.2F, 0.0F, 9.4F, 1.2F, 3.6F, g); // collarbones like blades
				box(c, 0.0F, 2.6F, -2.2F, 7.0F, 0.8F, 0.6F, g); // ribs
				box(c, 0.0F, 4.6F, -2.2F, 6.4F, 0.8F, 0.6F, g);
				box(c, 0.0F, 6.6F, -2.2F, 5.6F, 0.8F, 0.6F, g);
				box(c, 0.0F, 8.6F, -2.2F, 4.4F, 0.8F, 0.6F, g);
				box(c, 0.0F, 1.4F, 2.4F, 1.6F, 9.0F, 1.4F, g); // the spine
				box(c, 0.0F, 1.0F, 2.6F, 1.0F, 10.0F, 2.6F, g); // its ridge of spurs
				box(c, -4.2F, -0.8F, 0.2F, 2.8F, 2.0F, 5.0F, g); // the torn hood's shoulders
				box(c, 4.2F, -0.8F, 0.2F, 2.8F, 2.0F, 5.0F, g);
			}
		}
		return c;
	}

	/** The neck and head, above the shoulder line. */
	private static CubeListBuilder skull(int kind, CubeDeformation g) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, 0.0F, -1.5F, 0.3F, 2.4F, 1.6F, 2.4F, g); // the neck
				box(c, 0.0F, -8.5F, 0.0F, 6.6F, 7.0F, 6.6F, g); // the skull
				box(c, 0.0F, -6.4F, -3.5F, 6.2F, 0.8F, 0.6F, g); // the brow over the sockets
				box(c, -1.6F, -4.4F, -3.4F, 1.4F, 0.8F, 0.5F, g); // cheekbones
				box(c, 1.6F, -4.4F, -3.4F, 1.4F, 0.8F, 0.5F, g);
				box(c, 0.0F, -4.8F, -3.6F, 0.8F, 1.2F, 0.5F, g); // the nose's hollow ridge
				box(c, 0.0F, -2.2F, -0.6F, 5.0F, 1.4F, 5.0F, g); // the jaw
				box(c, 0.0F, -2.9F, -3.2F, 4.2F, 0.5F, 0.4F, g); // teeth
				box(c, 0.0F, -9.4F, 0.6F, 8.2F, 1.2F, 7.8F, g); // the cowl: its crown
				box(c, -3.7F, -9.0F, 0.6F, 0.9F, 7.4F, 7.8F, g); // its sides
				box(c, 3.7F, -9.0F, 0.6F, 0.9F, 7.4F, 7.8F, g);
				box(c, 0.0F, -9.0F, 4.0F, 8.2F, 8.0F, 1.0F, g); // its back
				box(c, 0.0F, -9.9F, -3.0F, 6.4F, 0.8F, 1.6F, g); // its brim, shading the face
			}
			case Spirits.VARRO -> {
				box(c, 0.0F, -1.2F, 0.0F, 4.0F, 1.4F, 4.0F, g); // the neck
				box(c, 0.0F, -10.0F, 0.0F, 9.0F, 9.0F, 9.0F, g); // the helm
				box(c, 0.0F, -5.6F, -4.7F, 6.6F, 3.6F, 0.6F, g); // its faceplate
				box(c, 0.0F, -7.2F, -4.9F, 8.0F, 1.0F, 0.6F, g); // the visor's brow
				box(c, 0.0F, -6.0F, -5.0F, 6.0F, 0.4F, 0.4F, g); // the eye slit's lower lip
				box(c, -4.6F, -6.0F, -1.0F, 0.6F, 5.0F, 5.0F, g); // cheek guards
				box(c, 4.6F, -6.0F, -1.0F, 0.6F, 5.0F, 5.0F, g);
				box(c, 0.0F, -12.0F, 0.5F, 1.4F, 2.4F, 10.0F, g); // the crest
				box(c, 0.0F, -11.0F, 5.2F, 1.2F, 6.0F, 1.2F, g); // its plume, falling behind
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, -8.6F, 0.0F, 7.0F, 7.6F, 7.0F, g); // the head
				box(c, 0.0F, -6.6F, -3.7F, 5.6F, 0.9F, 0.7F, g); // bushy brows
				box(c, 0.0F, -5.4F, -3.8F, 1.2F, 1.8F, 0.8F, g); // the nose
				box(c, 0.0F, -4.4F, -3.7F, 5.0F, 0.9F, 0.7F, g); // the moustache
				box(c, 0.0F, -3.6F, -3.4F, 4.6F, 8.4F, 1.4F, g); // the long beard
				box(c, 0.0F, 4.6F, -3.2F, 2.6F, 2.4F, 1.0F, g); // its point
				box(c, 0.0F, -9.8F, 0.5F, 9.4F, 1.4F, 9.0F, g); // the hood: its crown
				box(c, -4.2F, -9.0F, 0.5F, 1.0F, 8.6F, 9.0F, g); // its sides
				box(c, 4.2F, -9.0F, 0.5F, 1.0F, 8.6F, 9.0F, g);
				box(c, 0.0F, -9.4F, 4.5F, 9.4F, 9.4F, 1.2F, g); // its back
				box(c, 0.0F, -11.4F, 1.5F, 4.0F, 1.8F, 5.0F, g); // its point
			}
			case Spirits.THESSALY -> {
				box(c, 0.0F, -1.4F, 0.0F, 1.8F, 1.6F, 1.8F, g); // the neck
				box(c, 0.0F, -7.6F, 0.0F, 6.0F, 6.2F, 6.0F, g); // the head
				box(c, 0.0F, -8.2F, 0.5F, 6.6F, 1.2F, 6.6F, g); // the hair
				box(c, 0.0F, -7.8F, 2.6F, 6.6F, 12.0F, 1.6F, g); // falling down the back
				box(c, -3.1F, -7.0F, -0.8F, 0.8F, 7.0F, 2.4F, g); // side locks
				box(c, 3.1F, -7.0F, -0.8F, 0.8F, 7.0F, 2.4F, g);
				box(c, 0.0F, -6.6F, 0.0F, 6.6F, 0.6F, 6.6F, g); // a circlet
				box(c, 0.0F, -6.9F, -3.4F, 1.0F, 1.0F, 0.4F, g); // its stone
				box(c, 0.0F, -4.4F, -3.1F, 2.4F, 0.4F, 0.3F, g); // spectacles
			}
			case Spirits.QUIET -> {
				box(c, 0.0F, -1.6F, 0.0F, 2.0F, 1.8F, 2.0F, g); // the neck
				box(c, 0.0F, -10.0F, 0.0F, 6.4F, 8.6F, 6.4F, g); // the long faceless head
				box(c, 0.0F, -12.6F, -3.2F, 6.0F, 0.5F, 0.5F, g); // a ring over it
				box(c, 0.0F, -12.6F, 3.2F, 6.0F, 0.5F, 0.5F, g);
				box(c, -3.2F, -12.6F, 0.0F, 0.5F, 0.5F, 6.0F, g);
				box(c, 3.2F, -12.6F, 0.0F, 0.5F, 0.5F, 6.0F, g);
			}
			default -> {
				box(c, 0.0F, -1.8F, 0.4F, 2.6F, 2.0F, 2.6F, g); // the neck
				box(c, 0.0F, -9.4F, -0.4F, 7.0F, 7.6F, 7.4F, g); // the long skull
				box(c, 0.0F, -7.0F, -4.2F, 6.6F, 1.0F, 0.6F, g); // the brow, heavy over hollow eyes
				box(c, 0.0F, -3.4F, -1.2F, 5.2F, 1.8F, 5.6F, g); // the jaw, hanging open
				box(c, -1.6F, -2.0F, -3.9F, 0.6F, 1.4F, 0.6F, g); // fangs
				box(c, 1.6F, -2.0F, -3.9F, 0.6F, 1.4F, 0.6F, g);
				box(c, -3.8F, -12.4F, 0.6F, 1.4F, 3.6F, 1.4F, g); // the horns, sweeping back
				box(c, 3.8F, -12.4F, 0.6F, 1.4F, 3.6F, 1.4F, g);
				box(c, -4.6F, -14.6F, 2.4F, 1.0F, 2.6F, 2.6F, g);
				box(c, 4.6F, -14.6F, 2.4F, 1.0F, 2.6F, 2.6F, g);
				box(c, 0.0F, -10.4F, 1.6F, 8.6F, 1.2F, 8.0F, g); // the torn hood
				box(c, -4.0F, -9.6F, 1.8F, 0.8F, 8.0F, 7.0F, g);
				box(c, 4.0F, -9.6F, 1.8F, 0.8F, 8.0F, 7.0F, g);
				box(c, 0.0F, -9.6F, 5.4F, 8.6F, 11.0F, 0.8F, g); // its back, hanging to the waist
			}
		}
		return c;
	}

	/** The lower body, down from the waist. */
	private static CubeListBuilder tail(int kind, CubeDeformation g) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, 0.0F, 0.0F, 0.0F, 6.0F, 2.4F, 3.0F, g); // the hips
				box(c, 0.0F, 2.2F, 0.3F, 6.4F, 4.0F, 3.4F, g); // no legs: the cloak's hem thinning to smoke
				box(c, 0.0F, 6.0F, 0.6F, 4.6F, 4.0F, 2.6F, g);
				box(c, -1.8F, 10.0F, 0.8F, 1.2F, 5.0F, 1.2F, g); // ragged strands of it
				box(c, 0.2F, 10.0F, 0.9F, 1.4F, 7.0F, 1.4F, g);
				box(c, 1.9F, 10.0F, 0.8F, 1.0F, 4.0F, 1.0F, g);
				box(c, 0.4F, 16.8F, 1.1F, 0.7F, 3.0F, 0.7F, g); // the last wisp
				box(c, -2.0F, 1.0F, -1.9F, 1.2F, 6.0F, 0.6F, g); // tatters in front
				box(c, 1.6F, 1.0F, -1.9F, 1.0F, 8.0F, 0.6F, g);
			}
			case Spirits.VARRO -> {
				box(c, 0.0F, 0.0F, 0.0F, 11.0F, 4.0F, 6.2F, g); // the mail skirt
				box(c, -3.0F, 0.0F, -0.5F, 5.4F, 5.0F, 6.4F, g); // the tassets
				box(c, 3.0F, 0.0F, -0.5F, 5.4F, 5.0F, 6.4F, g);
				box(c, 0.0F, 4.6F, 0.5F, 9.4F, 4.4F, 5.0F, g); // no legs: the war-ghost's smoke
				box(c, 0.0F, 9.0F, 1.0F, 6.6F, 4.4F, 3.6F, g);
				box(c, -1.4F, 13.4F, 1.4F, 2.4F, 4.0F, 2.2F, g);
				box(c, 1.6F, 13.4F, 1.4F, 1.8F, 5.4F, 1.8F, g); // two trails of it
				box(c, 1.6F, 18.8F, 1.6F, 0.8F, 2.6F, 0.8F, g); // the last wisp
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, 0.0F, 0.3F, 11.0F, 6.0F, 6.4F, g); // the robe
				box(c, 0.0F, 6.0F, 0.6F, 10.4F, 5.0F, 6.0F, g); // no feet: the robe thinning to a wisp
				box(c, 0.0F, 11.0F, 1.1F, 7.4F, 4.4F, 4.4F, g);
				box(c, -1.2F, 15.4F, 1.5F, 3.6F, 4.0F, 2.6F, g);
				box(c, 1.4F, 15.4F, 1.5F, 2.0F, 5.6F, 1.6F, g); // curling trails
				box(c, -1.4F, 19.4F, 1.8F, 1.2F, 2.6F, 1.2F, g); // the last wisp
				box(c, -2.4F, 2.0F, -3.0F, 1.6F, 7.0F, 0.6F, g); // the stole hanging on
				box(c, 2.4F, 2.0F, -3.0F, 1.6F, 7.0F, 0.6F, g);
			}
			case Spirits.THESSALY -> {
				box(c, 0.0F, 0.0F, 0.2F, 7.0F, 5.0F, 4.4F, g); // the skirt
				box(c, 0.0F, 5.0F, 0.6F, 6.4F, 5.0F, 3.8F, g); // no feet: the skirt thinning to a wisp
				box(c, 0.0F, 10.0F, 1.0F, 4.2F, 4.6F, 2.6F, g);
				box(c, -1.1F, 14.6F, 1.2F, 1.4F, 4.6F, 1.2F, g); // two trails
				box(c, 1.1F, 14.6F, 1.2F, 1.0F, 6.0F, 1.0F, g);
				box(c, -1.4F, 2.0F, 3.0F, 0.8F, 11.0F, 0.8F, g); // ribbons trailing
				box(c, 1.4F, 2.0F, 3.0F, 0.8F, 13.0F, 0.8F, g);
			}
			case Spirits.QUIET -> {
				box(c, 0.0F, 0.0F, 0.4F, 6.0F, 5.0F, 3.2F, g); // the tail
				box(c, 0.0F, 5.0F, 0.8F, 4.4F, 5.0F, 2.6F, g);
				box(c, 0.0F, 10.0F, 1.2F, 3.0F, 5.0F, 2.0F, g);
				box(c, 0.0F, 15.0F, 1.5F, 1.6F, 5.0F, 1.4F, g);
			}
			default -> {
				box(c, 0.0F, 0.0F, 0.3F, 7.4F, 4.0F, 4.0F, g); // no legs: rags of smoke
				box(c, 0.0F, 4.0F, 0.8F, 6.0F, 4.4F, 3.2F, g);
				box(c, -1.6F, 8.4F, 1.2F, 2.6F, 5.0F, 2.2F, g);
				box(c, 1.4F, 8.4F, 1.2F, 2.0F, 7.0F, 1.8F, g);
				box(c, -1.8F, 13.4F, 1.5F, 1.0F, 4.0F, 1.0F, g); // trailing to nothing
				box(c, 1.4F, 15.4F, 1.6F, 0.8F, 4.0F, 0.8F, g);
			}
		}
		return c;
	}

	/** The figure's upper arm, from its shoulder to the elbow; {@code side} 1 the right, -1 the left (mirrored across). */
	private static CubeListBuilder arm(int kind, CubeDeformation g, float side) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, side * 0.0F, -1.0F, 0.0F, 3.2F, 3.4F, 3.4F, g); // the ragged sleeve
				box(c, side * 0.0F, 0.0F, 0.0F, 1.6F, 6.0F, 1.6F, g); // the upper bone
			}
			case Spirits.VARRO -> {
				box(c, side * 0.0F, -2.0F, 0.0F, 6.4F, 3.6F, 7.0F, g); // the pauldron
				box(c, side * 0.0F, 1.0F, 0.0F, 5.6F, 1.4F, 6.4F, g); // its lower lame
				box(c, side * 0.0F, 0.0F, 0.0F, 4.0F, 5.4F, 4.0F, g); // the rerebrace
			}
			case Spirits.AMBROSE -> {
				box(c, side * 0.0F, -1.0F, 0.0F, 4.4F, 3.0F, 4.4F, g); // the shoulder of the robe
				box(c, side * 0.0F, 0.0F, 0.0F, 4.0F, 6.4F, 4.0F, g); // the sleeve
			}
			case Spirits.THESSALY -> {
				box(c, side * 0.0F, -0.6F, 0.0F, 2.6F, 2.4F, 2.6F, g); // the shoulder
				box(c, side * 0.0F, 0.0F, 0.0F, 2.0F, 5.6F, 2.0F, g); // the slim sleeve
			}
			case Spirits.QUIET -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 3.0F, 6.2F, 3.0F, g); // the smooth arm
			}
			default -> {
				box(c, side * 0.0F, -1.0F, 0.0F, 3.2F, 3.0F, 3.2F, g); // the bony shoulder
				box(c, side * 0.0F, 0.0F, 0.0F, 2.0F, 7.0F, 2.0F, g); // the long upper arm
			}
		}
		return c;
	}

	/** The figure's forearm, from its elbow down; {@code side} 1 the right, -1 the left. */
	private static CubeListBuilder fore(int kind, CubeDeformation g, float side) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 2.0F, g); // the elbow
				box(c, side * 0.0F, 0.8F, 0.0F, 1.4F, 5.0F, 1.4F, g); // the forearm
				box(c, side * 0.0F, 5.6F, -0.2F, 2.0F, 2.2F, 1.2F, g); // the hand
				box(c, side * 0.4F, 7.6F, -0.2F, 0.5F, 1.6F, 0.5F, g); // finger bones
				box(c, side * -0.4F, 7.6F, -0.2F, 0.5F, 1.8F, 0.5F, g);
			}
			case Spirits.VARRO -> {
				box(c, side * 0.0F, 0.0F, -0.2F, 4.8F, 1.8F, 4.8F, g); // the couter
				box(c, side * 0.0F, 1.6F, 0.0F, 4.0F, 5.0F, 4.0F, g); // the vambrace
				box(c, side * 0.0F, 6.2F, 0.0F, 4.8F, 3.4F, 4.8F, g); // the gauntlet
				box(c, side * 0.0F, 9.2F, -1.8F, 4.2F, 0.9F, 1.2F, g); // its knuckle studs
			}
			case Spirits.AMBROSE -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 5.6F, 4.6F, 5.6F, g); // its wide cuff
				box(c, side * 0.0F, 3.8F, -0.2F, 2.6F, 2.6F, 2.6F, g); // the hand
				box(c, side * 0.8F, 6.2F, -0.6F, 0.7F, 1.4F, 0.7F, g); // fingers
				box(c, side * -0.4F, 6.2F, -0.6F, 0.7F, 1.6F, 0.7F, g);
			}
			case Spirits.THESSALY -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 3.4F, 3.0F, 3.4F, g); // the bell cuff
				box(c, side * 0.0F, 2.8F, 0.0F, 1.6F, 3.2F, 1.6F, g); // the forearm
				box(c, side * 0.0F, 5.8F, -0.2F, 1.8F, 1.8F, 1.2F, g); // the hand
				box(c, side * 0.0F, 6.4F, -0.2F, 2.0F, 0.4F, 1.4F, g); // a ring
			}
			case Spirits.QUIET -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 3.0F, 6.0F, 3.0F, g); // its forearm
			}
			default -> {
				box(c, side * 0.0F, 0.0F, 0.0F, 2.4F, 1.2F, 2.4F, g); // the elbow
				box(c, side * 0.0F, 1.0F, 0.0F, 1.8F, 6.0F, 1.8F, g); // the forearm
				box(c, side * 0.0F, 6.8F, -0.2F, 2.8F, 2.2F, 1.6F, g); // the hand
				box(c, side * -0.9F, 8.8F, -0.6F, 0.6F, 3.4F, 0.6F, g); // long claws
				box(c, side * 0.0F, 8.8F, -0.6F, 0.6F, 4.0F, 0.6F, g);
				box(c, side * 0.9F, 8.8F, -0.6F, 0.6F, 3.4F, 0.6F, g);
			}
		}
		return c;
	}

	/** The figure's wing, out from its back; {@code side} 1 the right, -1 the left. Only the Grue has any. */
	private static CubeListBuilder wing(int kind, CubeDeformation g, float side) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
			}
			case Spirits.VARRO -> {
			}
			case Spirits.AMBROSE -> {
			}
			case Spirits.THESSALY -> {
			}
			case Spirits.QUIET -> {
			}
			default -> {
				box(c, side * 0.0F, -0.8F, 0.0F, 1.6F, 1.6F, 1.6F, g); // the wing's root
				box(c, side * -4.0F, -2.4F, 0.0F, 8.0F, 1.4F, 1.4F, g); // its arm bone, reaching out and up
				box(c, side * -9.0F, -5.0F, 0.0F, 3.0F, 1.2F, 1.2F, g);
				box(c, side * -10.6F, -5.6F, 0.0F, 1.4F, 1.4F, 1.4F, g); // the wrist knuckle
				box(c, side * -11.2F, -11.0F, 0.0F, 1.0F, 6.0F, 1.0F, g); // a finger bone, raised
				box(c, side * -12.0F, -13.2F, 0.0F, 1.6F, 2.2F, 1.0F, g); // its hooked claw
				box(c, side * -15.6F, -6.0F, 0.0F, 9.0F, 1.0F, 1.0F, g); // a finger bone, out
				box(c, side * -14.4F, -3.6F, 0.0F, 1.0F, 12.0F, 1.0F, g); // a finger bone, down
				box(c, side * -7.0F, -1.4F, 0.3F, 12.0F, 7.0F, 0.4F, g); // the membrane, torn
				box(c, side * -13.6F, -10.4F, 0.3F, 3.4F, 15.0F, 0.4F, g);
				box(c, side * -17.6F, -5.4F, 0.3F, 4.6F, 10.0F, 0.4F, g);
				box(c, side * -4.0F, 5.6F, 0.3F, 6.0F, 4.0F, 0.4F, g); // its scalloped trailing edge
				box(c, side * -10.4F, 5.6F, 0.3F, 4.0F, 6.0F, 0.4F, g);
				box(c, side * -15.0F, 4.6F, 0.3F, 3.0F, 4.0F, 0.4F, g);
			}
		}
		return c;
	}
}
