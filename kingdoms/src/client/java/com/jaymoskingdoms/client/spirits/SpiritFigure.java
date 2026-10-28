package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.client.spirits.SpiritArms.Anchor;
import com.jaymoskingdoms.client.spirits.SpiritArms.Look;
import com.jaymoskingdoms.spirits.Spirits;
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
 * (0.7.298) The spirit's figure, the body its arms grow from. Andy (2026-10-28): "start with a larger figure that pulses behind
 * you like now, and build the arms off their frame. they should fade, while the arms stay." Each spirit stands over your back,
 * bigger than you ({@link #SCALE}), made of the same aura shells as its arms, and its arms hang from its shoulders: the arms'
 * pivots are placed at the figure's shoulders ({@link #shoulder}), at its depth ({@link #back}), its torso's top level with the
 * arms' tops. Whenever the projection forms (a move, a summon, a word) the figure shows with it, pulsing, then fades over
 * {@link #FADE_TICKS} while the arms go on with the move. Each kind has its own make:
 * <ul>
 * <li>Grimsby: a gaunt wraith, ribs and the knobs of his spine, a skull in a cowl, his tail in ragged strands.</li>
 * <li>Varro: a warlord in plate, a breastplate standing proud, a crested helm with a faceplate, tassets.</li>
 * <li>Ambrose: an elder in a hooded robe, a long beard, a mantle and stole, the robe flaring and thinning to a wisp.</li>
 * <li>Thessaly: slim, hair falling down her back under a circlet, a shawl and sash, a skirt trailing ribbons.</li>
 * <li>The Quiet One: smooth and faceless, a long head with a ring hanging over it.</li>
 * </ul>
 * No legs: below the waist each figure thins into a ghost's tail that sways. The models are in
 * {@code tools/blockbench/spirit-figures/} (made from the same numbers as this mesh).
 */
final class SpiritFigure {
	/** Each kind's figure, by kind: its scale (a player's is 1), its torso's half width and how far it reaches forward of its middle (model pixels at scale 1), and the tail's root below the shoulder line. */
	static final float[] SCALE = {1.6F, 1.9F, 1.8F, 1.2F, 1.7F};
	static final float[] HALF = {3.5F, 6.0F, 5.0F, 3.2F, 4.0F};
	static final float[] FRONT = {3.3F, 5.0F, 4.0F, 3.6F, 3.45F};
	static final float[] TAIL_Y = {11.0F, 12.0F, 12.0F, 11.0F, 12.0F};
	/** How far each kind's arm reaches in toward the body from its pivot, per unit of arm size (half its upper arm's width, less the vanilla 1). */
	static final float[] ARM_INNER = {0.5F, 1.5F, 1.0F, 0.5F, 1.0F};
	/** Model pixels the figure keeps clear of your back and head (a player's head reaches 4 behind the middle). */
	static final float BEHIND = 4.5F;
	/** How long the figure holds after it shows, then how long it takes to fade (ticks). The arms keep their own time. */
	static final float HOLD_TICKS = 8.0F, FADE_TICKS = 16.0F;
	/** The pulse: how far its light swells and how fast, how much it throbs in size, and how far it swells as it fades (breaking up). */
	static final float PULSE = 0.35F, PULSE_SPEED = 0.3F, THROB = 0.03F, SURGE = 0.12F;
	/** The tail's sway (radians, and how fast). */
	static final float TAIL_SWAY = 0.12F, TAIL_SPEED = 0.07F;
	/** How far the figure's head turns with yours. */
	static final float HEAD_FOLLOW = 0.5F;
	/** Its shells: thinner and dimmer than the arms', so the arms read over it. */
	static final float[] SHELLS = {0.0F, 0.5F, 1.2F}, SHELL_LIGHT = {0.45F, 0.16F, 0.06F};

	private SpiritFigure() {
	}

	/** How strong the figure is {@code since} ticks after the projection showed: up at once, held, then faded out. */
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

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/**
	 * One shell of one kind's figure. A HumanoidModel (as the arms' is) whose only visible part is the body, which holds the
	 * torso's cubes, the {@code skull} (at the neck) and the {@code tail} (at the waist).
	 */
	static final class Model extends HumanoidModel<HumanoidRenderState> {
		private final int kind;
		private final ModelPart skull, tail;

		Model(ModelPart root, int kind) {
			super(root);
			this.kind = kind;
			this.skull = this.body.getChild("skull");
			this.tail = this.body.getChild("tail");
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
			Look look = state.getData(SpiritArms.LOOK);
			this.body.visible = look != null && look.figure() > 0.0F;
			if (look == null) {
				return;
			}
			Anchor anchor = SpiritArms.anchor(look.style());
			float lift = Mth.clamp(look.size(), 1.0F, SpiritArms.FLOAT_SIZE_CAP);
			float s = scale(this.kind, look.size()) * (1.0F + THROB * Mth.sin(state.ageInTicks * PULSE_SPEED) + SURGE * (1.0F - look.figure()));
			// its torso's top level with its arms' tops, its middle over your back
			this.body.x = -anchor.right() * lift;
			this.body.y = SpiritArms.ARM_PIVOT_Y - anchor.up() * lift - 2.0F * look.size();
			this.body.z = back(this.kind, anchor, lift, look.size());
			this.body.xScale = s;
			this.body.yScale = s;
			this.body.zScale = s;
			// it looks where you look, half as far; its tail drifts
			this.skull.yRot = state.yRot * Mth.DEG_TO_RAD * HEAD_FOLLOW;
			this.skull.xRot = state.xRot * Mth.DEG_TO_RAD * HEAD_FOLLOW;
			this.tail.xRot = TAIL_SWAY * (0.5F + 0.5F * Mth.sin(state.ageInTicks * TAIL_SPEED));
			this.tail.zRot = TAIL_SWAY * 0.5F * Mth.sin(state.ageInTicks * TAIL_SPEED * 0.7F);
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
				box(c, 0.0F, 3.0F, -1.9F, 6.0F, 0.7F, 0.6F, g); // ribs
				box(c, 0.0F, 5.0F, -1.9F, 5.6F, 0.7F, 0.6F, g);
				box(c, 0.0F, 7.0F, -1.9F, 5.0F, 0.7F, 0.6F, g);
				box(c, 0.0F, 2.0F, 2.0F, 1.2F, 1.2F, 1.0F, g); // the spine's knobs
				box(c, 0.0F, 5.0F, 2.0F, 1.2F, 1.2F, 1.0F, g);
				box(c, 0.0F, 8.0F, 2.0F, 1.2F, 1.2F, 1.0F, g);
			}
			case Spirits.VARRO -> {
				box(c, 0.0F, 0.0F, 0.0F, 12.0F, 12.0F, 6.0F, g); // the plated chest
				box(c, 0.0F, 1.0F, -3.2F, 10.0F, 7.0F, 1.0F, g); // the breastplate, standing proud
				box(c, 0.0F, -1.2F, 0.0F, 7.0F, 1.6F, 5.0F, g); // the gorget
				box(c, 0.0F, 10.0F, 0.0F, 12.6F, 2.0F, 6.6F, g); // the belt
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, 0.0F, 0.0F, 10.0F, 12.0F, 5.0F, g); // the robed chest
				box(c, 0.0F, -0.6F, 0.0F, 11.4F, 3.0F, 6.2F, g); // the mantle
				box(c, -2.4F, 2.0F, -2.8F, 1.6F, 10.0F, 0.6F, g); // the stole
				box(c, 2.4F, 2.0F, -2.8F, 1.6F, 10.0F, 0.6F, g);
			}
			case Spirits.THESSALY -> {
				box(c, 0.0F, 0.0F, 0.0F, 6.4F, 6.0F, 3.4F, g); // the slim chest
				box(c, 0.0F, 6.0F, 0.0F, 5.0F, 5.0F, 3.0F, g); // the waist
				box(c, 0.0F, -0.4F, 0.4F, 7.6F, 2.0F, 4.0F, g); // a shawl
				box(c, 0.0F, 9.6F, 0.0F, 5.6F, 1.0F, 3.4F, g); // the sash
			}
			default -> {
				box(c, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 4.0F, g); // the smooth chest
				box(c, 0.0F, 8.0F, 0.0F, 6.6F, 4.0F, 3.4F, g); // the waist
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
				box(c, 0.0F, -2.2F, -0.6F, 5.0F, 1.4F, 5.0F, g); // the jaw
				box(c, 0.0F, -9.4F, 0.6F, 8.2F, 1.2F, 7.8F, g); // the cowl: its crown
				box(c, -3.7F, -9.0F, 0.6F, 0.9F, 7.4F, 7.8F, g); // its sides
				box(c, 3.7F, -9.0F, 0.6F, 0.9F, 7.4F, 7.8F, g);
				box(c, 0.0F, -9.0F, 4.0F, 8.2F, 8.0F, 1.0F, g); // its back
				box(c, 0.0F, -8.0F, 4.8F, 4.0F, 6.0F, 1.2F, g); // and its tail, hanging
			}
			case Spirits.VARRO -> {
				box(c, 0.0F, -1.2F, 0.0F, 4.0F, 1.4F, 4.0F, g); // the neck
				box(c, 0.0F, -10.0F, 0.0F, 9.0F, 9.0F, 9.0F, g); // the helm
				box(c, 0.0F, -5.6F, -4.7F, 6.6F, 3.6F, 0.6F, g); // its faceplate
				box(c, 0.0F, -7.2F, -4.9F, 8.0F, 1.0F, 0.6F, g); // the visor's brow
				box(c, 0.0F, -12.0F, 0.5F, 1.4F, 2.4F, 10.0F, g); // the crest
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, -8.6F, 0.0F, 7.0F, 7.6F, 7.0F, g); // the head
				box(c, 0.0F, -2.5F, -3.3F, 4.4F, 8.0F, 1.4F, g); // the long beard
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
				box(c, 0.0F, -7.8F, 2.6F, 6.6F, 9.0F, 1.6F, g); // falling down the back
				box(c, 0.0F, -6.6F, 0.0F, 6.6F, 0.6F, 6.6F, g); // a circlet
				box(c, 0.0F, -6.9F, -3.4F, 1.0F, 1.0F, 0.4F, g); // its stone
			}
			default -> {
				box(c, 0.0F, -1.6F, 0.0F, 2.0F, 1.8F, 2.0F, g); // the neck
				box(c, 0.0F, -10.0F, 0.0F, 6.4F, 8.6F, 6.4F, g); // the long faceless head
				box(c, 0.0F, -12.6F, -3.2F, 6.0F, 0.5F, 0.5F, g); // a ring over it
				box(c, 0.0F, -12.6F, 3.2F, 6.0F, 0.5F, 0.5F, g);
				box(c, -3.2F, -12.6F, 0.0F, 0.5F, 0.5F, 6.0F, g);
				box(c, 3.2F, -12.6F, 0.0F, 0.5F, 0.5F, 6.0F, g);
			}
		}
		return c;
	}

	/** The ghost's tail, down from the waist. */
	private static CubeListBuilder tail(int kind, CubeDeformation g) {
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				box(c, 0.0F, 0.0F, 0.3F, 6.0F, 4.0F, 3.0F, g); // the ghost's tail
				box(c, 0.0F, 4.0F, 0.6F, 4.4F, 4.0F, 2.4F, g);
				box(c, -1.6F, 8.0F, 0.9F, 1.2F, 5.0F, 1.2F, g); // ragged strands
				box(c, 0.3F, 8.0F, 0.9F, 1.2F, 7.0F, 1.2F, g);
				box(c, 1.8F, 8.0F, 0.9F, 1.0F, 4.0F, 1.0F, g);
			}
			case Spirits.VARRO -> {
				box(c, -3.0F, 0.0F, -0.5F, 5.4F, 5.0F, 6.4F, g); // the tassets
				box(c, 3.0F, 0.0F, -0.5F, 5.4F, 5.0F, 6.4F, g);
				box(c, 0.0F, 5.0F, 0.5F, 9.0F, 4.0F, 4.5F, g); // then the ghost's tail
				box(c, 0.0F, 9.0F, 1.0F, 6.0F, 4.0F, 3.0F, g);
				box(c, 0.0F, 13.0F, 1.4F, 3.4F, 4.0F, 2.0F, g);
			}
			case Spirits.AMBROSE -> {
				box(c, 0.0F, 0.0F, 0.3F, 12.0F, 6.0F, 7.0F, g); // the robe flaring
				box(c, 0.0F, 6.0F, 0.8F, 10.0F, 5.0F, 6.0F, g);
				box(c, 0.0F, 11.0F, 1.3F, 7.0F, 5.0F, 4.4F, g); // then thinning to a wisp
				box(c, 0.0F, 16.0F, 1.8F, 4.0F, 4.0F, 2.6F, g);
			}
			case Spirits.THESSALY -> {
				box(c, 0.0F, 0.0F, 0.2F, 7.0F, 5.0F, 4.4F, g); // the skirt
				box(c, 0.0F, 5.0F, 0.6F, 6.0F, 5.0F, 3.6F, g);
				box(c, 0.0F, 10.0F, 1.0F, 4.0F, 5.0F, 2.6F, g);
				box(c, -1.2F, 15.0F, 1.2F, 0.8F, 5.0F, 0.8F, g); // ribbons trailing
				box(c, 1.2F, 15.0F, 1.2F, 0.8F, 6.0F, 0.8F, g);
			}
			default -> {
				box(c, 0.0F, 0.0F, 0.4F, 6.0F, 5.0F, 3.2F, g); // the tail
				box(c, 0.0F, 5.0F, 0.8F, 4.4F, 5.0F, 2.6F, g);
				box(c, 0.0F, 10.0F, 1.2F, 3.0F, 5.0F, 2.0F, g);
				box(c, 0.0F, 15.0F, 1.5F, 1.6F, 5.0F, 1.4F, g);
			}
		}
		return c;
	}
}
