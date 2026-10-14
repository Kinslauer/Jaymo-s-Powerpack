package com.jaymospowers.client.power.xenoform;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Wyrm's wings: the ender dragon's own, small. Each is the dragon model's wing (a bone with its membrane, and the tip
 * with its own membrane, the same boxes and texture coordinates as vanilla's EnderDragonModel) drawn from the game's own
 * dragon texture at {@link #SCALE} of the dragon's size, hung from the shoulder blades. Nothing is copied: the texture
 * is the one already in everyone's game, looked up by its name. A humanoid model of its own with empty limbs, so the
 * wings ride the body through every pose ours lay over humanoid models (flight, the slam...) and through crouching.
 * Open in the air they beat, fold back at speed, flare when falling; on the ground they wrap round the body like a cloak.
 */
public final class WyrmWings extends HumanoidModel<AvatarRenderState> {
	/** The game's own ender dragon texture (256x256), never copied into the mod. */
	public static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
	/** The dragon's wing is 56 + 56 long and 56 deep (model pixels): this much of it. */
	static final float SCALE = 0.2F;
	/** Where each wing roots on the back (model pixels, from the body's pivot at the neck). */
	static final float ROOT_X = 2.0F, ROOT_Y = 2.0F, ROOT_Z = 2.5F;

	/** In flight: the beat's speed and depth; folded, the wings sweep back to this yaw and lift this little. */
	static final float FLAP_RATE = 0.55F, FLAP_DEPTH = 0.95F, FOLDED_YAW = 1.35F, FOLDED_LIFT = 0.15F;
	/** In flight, how far the tips bend behind the beat (the dragon's own tip lag). */
	static final float TIP_FLEX = 0.6F;
	/** Falling without flying, how far the wings flare open; how hard a landing slams them down. */
	static final float FALL_FLARE = 0.8F, LAND_SLAM = 0.6F;
	/**
	 * Wrapped on the ground: the wings hang down (pitch) and swing forward round the sides past the arms (yaw), the
	 * tips fold in across the chest.
	 */
	static final float WRAP_PITCH = -Mth.HALF_PI, WRAP_YAW = -0.42F, WRAP_FOLD = -2.25F;

	private static WyrmWings instance;

	private final ModelPart rightWing, leftWing, rightTip, leftTip;

	private WyrmWings(ModelPart root) {
		super(root);
		this.rightWing = this.body.getChild("right_wing");
		this.leftWing = this.body.getChild("left_wing");
		this.rightTip = this.rightWing.getChild("right_wing_tip");
		this.leftTip = this.leftWing.getChild("left_wing_tip");
	}

	static WyrmWings get() {
		if (instance == null) {
			instance = new WyrmWings(mesh().bakeRoot());
		}
		return instance;
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// a humanoid with nothing on it but the wings, its parts where the player model's are
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		// the dragon's wings (EnderDragonModel.createBodyLayer), at the dragon's size: the part's scale shrinks them
		PartDefinition left = body.addOrReplaceChild("left_wing",
			CubeListBuilder.create().mirror().addBox("bone", 0.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
			PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE));
		left.addOrReplaceChild("left_wing_tip",
			CubeListBuilder.create().mirror().addBox("bone", 0.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
			PartPose.offset(56.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing",
			CubeListBuilder.create().addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
			PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE));
		right.addOrReplaceChild("right_wing_tip",
			CubeListBuilder.create().addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
			PartPose.offset(-56.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
		float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
		float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold());
		XenoMotion.Read m = XenoMotion.read(state);
		float walk = state.walkAnimationSpeed;
		// on the ground and not flying, the wings wrap round it; in the air they open
		float wrap = XenoformFlightPose.smooth(Mth.clamp((1.0F - m.air()) * (1.0F - fly), 0.0F, 1.0F));

		// open (in the air): a slow stir at rest, the dragon's deep strokes flying, dying away as the wings fold at speed
		float idle = m.breath() * 0.08F + Mth.sin(t * 0.25F) * 0.05F;
		float flap = Mth.sin(t * FLAP_RATE) * FLAP_DEPTH * (1.0F - fold);
		float beat = Mth.lerp(fly, idle, flap);
		float lift = fold * FOLDED_LIFT * fly;
		// dropping without flying, they flare open to catch the air; landing slams them down
		float flare = Math.max(0.0F, m.fall()) * m.air() * (1.0F - fly) * FALL_FLARE;
		float slam = m.land() * LAND_SLAM;
		// both tip together out of a turn, the outer wing up
		float bank = m.turn() * 0.3F;
		float openZ = -0.3F + beat + lift - flare + slam + bank;
		float openY = Mth.lerp(fold * fly, 0.6F + Mth.cos(t * 0.25F) * 0.1F - flare * 0.5F, FOLDED_YAW);
		// the tips trail the stroke like the dragon's (its tip runs 2 radians behind), straightening as they fold
		float openTip = -(Mth.sin(t * FLAP_RATE - 2.0F) + 0.5F) * TIP_FLEX * fly * (1.0F - fold) + flare * 0.3F;

		// wrapped (on the ground): the cloak breathes, sways with each step and swings out behind a turn
		float step = Mth.sin(state.walkAnimationPos * 0.6662F) * walk;
		float wrapX = WRAP_PITCH + m.breath() * 0.03F + walk * 0.15F + m.land() * 0.25F;
		float wrapY = WRAP_YAW + m.breath() * 0.03F + Mth.abs(step) * 0.06F;
		float wrapFold = WRAP_FOLD + walk * 0.25F - m.breath() * 0.04F;

		this.rightWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
		this.leftWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
		this.rightWing.yRot = Mth.lerp(wrap, openY, wrapY + step * 0.08F - m.trail() * 0.2F);
		this.leftWing.yRot = Mth.lerp(wrap, -openY, -wrapY + step * 0.08F - m.trail() * 0.2F);
		this.rightWing.zRot = Mth.lerp(wrap, openZ, 0.0F);
		this.leftWing.zRot = Mth.lerp(wrap, -openZ + 2.0F * bank, 0.0F);
		// the tips: open, they flex behind the stroke (about the wing's length axis it's zRot); wrapped, that axis
		// stands upright, so zRot folds them forward across the chest
		this.rightTip.zRot = Mth.lerp(wrap, openTip, wrapFold);
		this.leftTip.zRot = Mth.lerp(wrap, -openTip, -wrapFold);
	}
}
