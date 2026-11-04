package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * (0.20.21, Ultimates patch 1) Wisp's Ultimate, Revenant: Wisp's own body (the same humanoid build, no legs, arms that hang and drift,
 * a tattered hood, a hem that sways and drags behind) with a bone-pale mask over the face, bone collar plates, pauldrons and bracers,
 * a crest of bone spikes on the hood and the collar, seven long tendrils trailing off the hem (each on its own sway), and thin ice-blue
 * seams through the cloth (the skin's glow map, drawn fullbright by the layer). It floats a hand's width off the ground and bobs, its
 * skin shows see-through and fullbright over {@link #KI_SHELLS} shells of ki set farther out than Wisp's ({@link #submitExtras}).
 * The skin is {@code tools/ultimates/wisp.py}, 128x128; {@link #UV} here is the same table that script prints ({@code wisp.py layout}).
 * The base limbs keep the vanilla layout in the top-left 64x64.
 * <p>
 * The change-in film zooms on, in order, {@code tatters} (the hem and its tendrils), {@code body}, {@code hood} and {@code head}: those
 * names are parts of this mesh.
 */
public final class UltimateRevenant extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** How far the ki shells stand out from the body (model pixels), inside out: more than Wisp's two, set farther out. */
	static final float[] KI_SHELLS = {0.6F, 1.5F, 2.5F, 3.6F};
	/** How strongly each shell shows (additive), inside out, and its colour; how opaque the see-through skin is over the body (0-255). */
	static final float[] SHELL_LIGHT = {0.6F, 0.38F, 0.22F, 0.12F};
	static final int KI_COLOR = 0x8FD8FF, SKIN_ALPHA = 110;
	/** How fast the ki's wisps rise, and its breathing. */
	static final float RISE = 0.015F, BREATHE = 0.12F, BREATHE_SPEED = 0.13F;
	static final int FULL_BRIGHT = 0xF000F0;
	/** How high it floats (pixels: 0.3 of a block), its bob, and the bob's speed. */
	static final float FLOAT = 4.8F, BOB = 1.0F, BOB_SPEED = 0.09F;
	/** How far back its arms trail at a run (radians), and their slow drift. */
	static final float ARM_TRAIL = 0.55F, ARM_DRIFT = 0.1F;
	/** The tendrils' sway: resting lean, sway (radians) and its rate, how far they stream back at a run, how far a fall lifts them. */
	static final float TEND_LEAN = 0.22F, TEND_SWAY = 0.2F, TEND_RATE = 0.1F, TEND_STREAM = 0.8F, TEND_FALL = 0.6F;
	/** The hem's drag, as Wisp's, and how far the hood and the spikes sway (radians). */
	static final float HEM_STREAM = 0.5F, HOOD_SWAY = 0.04F, SPIKE_SWAY = 0.06F;
	/** The tendrils: how many, where along the hem (x pixels) and which side (z pixels) each hangs, and whether it is the long kind. */
	static final int TENDRILS = 7;
	private static final float[] TEND_X = {-3.2F, -2.1F, -1.0F, 0.0F, 1.0F, 2.1F, 3.2F};
	private static final float[] TEND_Z = {-1.2F, 1.2F, -0.6F, 1.4F, -1.4F, 0.6F, 1.2F};
	private static final boolean[] TEND_LONG = {false, true, true, true, true, true, false};
	/** Its ki's texture. */
	static final Identifier KI_TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_glow.png");

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/wisp.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("hood", 0, 64), uv("mask", 40, 64), uv("collar", 58, 64), uv("cspike", 94, 64), uv("tatters", 98, 64), uv("tend_s", 122, 64),
		uv("tend_l", 0, 84), uv("pauld", 6, 84), uv("bracer", 30, 84));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart hood, tatters;
	private final ModelPart[] tendrils = new ModelPart[TENDRILS];
	private final ModelPart[] spikes = new ModelPart[5];
	/** Its ki, the body again a little bigger each time ({@link #KI_SHELLS}); none on a shell itself. */
	private final UltimateRevenant[] shells;

	public UltimateRevenant() {
		this(0.0F);
	}

	private UltimateRevenant(float grow) {
		super(XenoFormModels.bake(mesh(grow), 128, 128), "wisp");
		this.hood = this.head.getChild("hood");
		this.tatters = this.body.getChild("tatters");
		for (int i = 0; i < TENDRILS; i++) {
			this.tendrils[i] = this.tatters.getChild("tendril_" + i);
		}
		for (int i = 0; i < 3; i++) {
			this.spikes[i] = this.hood.getChild("hood_spike_" + i);
		}
		this.spikes[3] = this.body.getChild("collar_spike_r");
		this.spikes[4] = this.body.getChild("collar_spike_l");
		if (grow == 0.0F) {
			this.shells = new UltimateRevenant[KI_SHELLS.length];
			for (int i = 0; i < KI_SHELLS.length; i++) {
				this.shells[i] = new UltimateRevenant(KI_SHELLS[i]);
			}
		} else {
			this.shells = new UltimateRevenant[0];
		}
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d, float grow) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d, new CubeDeformation(grow));
	}

	private static MeshDefinition mesh(float grow) {
		// Wisp's build, the vanilla humanoid on its layout, puffed by the shell's grow
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(grow), 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		// the tattered hood over the head, a bone mask on the face, and three spikes leaning back off the crown of the hood
		PartDefinition hood = head.addOrReplaceChild("hood", cube("hood", false, -5.0F, -9.0F, -5.0F, 10, 10, 10, grow), PartPose.ZERO);
		head.addOrReplaceChild("mask", cube("mask", false, -4.0F, -8.0F, -5.1F, 8, 7, 1, grow), PartPose.ZERO);
		float[] spikeX = {-2.5F, 0.0F, 2.5F};
		for (int i = 0; i < 3; i++) {
			hood.addOrReplaceChild("hood_spike_" + i, cube("cspike", i == 2, -0.5F, -5.0F, -0.5F, 1, 5, 1, grow),
				PartPose.offsetAndRotation(spikeX[i], -8.8F, 1.0F, -0.6F, 0.0F, spikeX[i] * 0.12F));
		}
		// the body: a bone collar across the shoulders with a spike at each end, the hem and its tendrils
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("collar", cube("collar", false, -6.0F, -1.5F, -3.0F, 12, 2, 6, grow), PartPose.ZERO);
		body.addOrReplaceChild("collar_spike_r", cube("cspike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, grow),
			PartPose.offsetAndRotation(-5.6F, -1.2F, 0.0F, 0.0F, 0.0F, -0.55F));
		body.addOrReplaceChild("collar_spike_l", cube("cspike", true, -0.5F, -5.0F, -0.5F, 1, 5, 1, grow),
			PartPose.offsetAndRotation(5.6F, -1.2F, 0.0F, 0.0F, 0.0F, 0.55F));
		PartDefinition tatters = body.addOrReplaceChild("tatters", cube("tatters", false, -4.0F, 0.0F, -2.0F, 8, 7, 4, grow),
			PartPose.offset(0.0F, 12.0F, 0.0F));
		for (int i = 0; i < TENDRILS; i++) {
			tatters.addOrReplaceChild("tendril_" + i, TEND_LONG[i] ? cube("tend_l", i % 2 == 0, -1.0F, 0.0F, -0.5F, 2, 13, 1, grow)
				: cube("tend_s", i % 2 == 0, -1.0F, 0.0F, -0.5F, 2, 9, 1, grow), PartPose.offset(TEND_X[i], 6.5F, TEND_Z[i]));
		}
		// the arms: a bone pauldron on each shoulder and a bracer on each forearm
		PartDefinition rightArm = root.getChild("right_arm");
		rightArm.addOrReplaceChild("pauldron_r", cube("pauld", false, -4.0F, -3.5F, -3.0F, 6, 3, 6, grow), PartPose.ZERO);
		rightArm.addOrReplaceChild("bracer_r", cube("bracer", false, -3.5F, 5.5F, -2.5F, 5, 4, 5, grow + 0.2F), PartPose.ZERO);
		PartDefinition leftArm = root.getChild("left_arm");
		leftArm.addOrReplaceChild("pauldron_l", cube("pauld", true, -2.0F, -3.5F, -3.0F, 6, 3, 6, grow), PartPose.ZERO);
		leftArm.addOrReplaceChild("bracer_l", cube("bracer", true, -1.5F, 5.5F, -2.5F, 5, 4, 5, grow + 0.2F), PartPose.ZERO);
		return mesh;
	}

	@Override
	protected void pose(AvatarRenderState state) {
		// it doesn't walk: the arms take no swing from its steps (the state is put back for the next model)
		float walk = state.walkAnimationSpeed;
		state.walkAnimationSpeed = 0.0F;
		super.pose(state);
		state.walkAnimationSpeed = walk;
		this.rightLeg.visible = false;
		this.leftLeg.visible = false;
		this.hat.visible = false;
		XenoMotion.Read m = XenoMotion.read(state);
		float t = state.ageInTicks;
		// it floats a hand's width off the ground and bobs, unless it is lying (swimming, sleeping)
		this.root().y = state.pose == Pose.STANDING || state.pose == Pose.CROUCHING ? -(FLOAT + BOB * Mth.sin(t * BOB_SPEED)) : 0.0F;
		// arms hang loose, drifting, and trail back as it glides along
		float move = Math.min(1.0F, walk);
		this.rightArm.xRot += move * ARM_TRAIL + Mth.sin(t * 0.07F) * ARM_DRIFT;
		this.leftArm.xRot += move * ARM_TRAIL + Mth.sin(t * 0.07F + 1.7F) * ARM_DRIFT;
		this.rightArm.zRot += 0.1F + Mth.cos(t * 0.05F) * 0.05F;
		this.leftArm.zRot -= 0.1F + Mth.cos(t * 0.05F + 1.1F) * 0.05F;
		// the hood sways a little, the hem drags behind, billows up as it drops and swings out of a turn
		this.hood.zRot = Mth.sin(t * 0.06F) * HOOD_SWAY - m.trail() * 0.05F;
		this.tatters.xRot = Mth.sin(t * 0.12F) * 0.15F + walk * HEM_STREAM + Math.abs(m.fall()) * 0.6F * m.air() + m.land() * 0.3F;
		this.tatters.zRot = Mth.cos(t * 0.09F) * 0.1F - m.trail() * 0.35F;
		// each tendril on its own sway, streaming back harder the faster it goes and lifting as it falls
		float lift = Math.abs(m.fall()) * TEND_FALL * m.air();
		for (int i = 0; i < TENDRILS; i++) {
			ModelPart tendril = this.tendrils[i];
			float phase = i * 1.3F;
			tendril.xRot = TEND_LEAN + Mth.sin(t * TEND_RATE + phase) * TEND_SWAY + walk * TEND_STREAM + lift;
			tendril.zRot = Mth.cos(t * TEND_RATE * 0.8F + phase * 1.7F) * TEND_SWAY * 0.7F - m.trail() * 0.4F + (i % 2 == 0 ? 0.06F : -0.06F);
		}
		// the spikes sway a little, out of step with one another
		for (int i = 0; i < this.spikes.length; i++) {
			this.spikes[i].zRot += Mth.sin(t * 0.17F + i * 1.7F) * SPIKE_SWAY;
			this.spikes[i].xRot += Mth.cos(t * 0.14F + i) * SPIKE_SWAY * 0.5F;
		}
		XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
		if (flight != null && flight.drift() > 0.001F) {
			// (0.20.23) floating upright, the hem and the tendrils hang below and ripple, trailing a little as it drifts
			float g = Mth.clamp(flight.drift(), 0.0F, 1.0F);
			g = g * g * (3.0F - 2.0F * g);
			float moving = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			this.tatters.xRot = Mth.lerp(g, this.tatters.xRot, 0.12F + moving * 0.35F + Mth.sin(t * 0.2F) * 0.12F);
			this.tatters.zRot = Mth.lerp(g, this.tatters.zRot, Mth.sin(t * 0.15F) * 0.12F);
			for (int i = 0; i < TENDRILS; i++) {
				this.tendrils[i].xRot = Mth.lerp(g, this.tendrils[i].xRot, 0.15F + moving * 0.3F + Mth.sin(t * 0.25F + i) * 0.15F);
			}
		}
	}

	/**
	 * Its skin shows see-through and fullbright over the body, and the shells of ki stand out round it, each taking the body's pose when
	 * it is drawn (the same {@code pose}).
	 */
	@Override
	public void submitExtras(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateTexture), FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, ARGB.color(SKIN_ALPHA, 255, 255, 255), null, 0);
		float rise = (state.ageInTicks * RISE) % 1.0F;
		for (int i = 0; i < this.shells.length; i++) {
			collector.order(2 + i).submitModel(this.shells[i], state, poseStack, RenderTypes.energySwirl(KI_TEXTURE, i * 0.31F, -rise * (1 + i)), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, tint(i, state.ageInTicks), null, 0);
		}
	}

	/** (0.20.21) The first-person arm, drawn as the body is: see-through and fullbright, in its ki shells. */
	public void submitArm(boolean right, ModelPart limb, PoseStack poseStack, SubmitNodeCollector collector, float time) {
		collector.submitModelPart(limb, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateTexture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
			ARGB.color(SKIN_ALPHA, 255, 255, 255));
		float rise = (time * RISE) % 1.0F;
		for (int i = 0; i < this.shells.length; i++) {
			ModelPart arm = right ? this.shells[i].rightArm : this.shells[i].leftArm;
			arm.loadPose(limb.storePose());
			arm.visible = true;
			collector.submitModelPart(arm, poseStack, RenderTypes.energySwirl(KI_TEXTURE, i * 0.31F, -rise * (1 + i)), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, tint(i, time));
		}
	}

	/** Additive, so the colour's brightness is how strongly it shows. */
	private static int tint(int shell, float time) {
		float light = Mth.clamp(SHELL_LIGHT[shell] * (1.0F + BREATHE * Mth.sin(time * BREATHE_SPEED + shell)), 0.0F, 1.0F);
		return ARGB.color(255, (int)(ARGB.red(KI_COLOR) * light), (int)(ARGB.green(KI_COLOR) * light), (int)(ARGB.blue(KI_COLOR) * light));
	}
}
