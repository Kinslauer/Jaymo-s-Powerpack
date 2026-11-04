package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * (0.20.21, Ultimates patch 1) Pyre's Ultimate, Sunheart: Pyre's own body (the same humanoid build and gait, a body of living flame
 * with hair of fire streaming up off the head and flames licking off the shoulders and forearms) with plates of cooled black basalt
 * on the shoulders, chest, back, belt, forearms and shins, a basalt crown ringed with flame-tipped spikes, spikes on the shoulder
 * blades, elbows and knees, a bigger sun in the chest, and white-yellow cracks glowing in the plates (the skin's glow map, drawn
 * fullbright by the layer). It hovers a hand's width off the ground as it walks. The skin is {@code tools/ultimates/pyre.py}, 128x128;
 * {@link #UV} here is the same table that script prints ({@code pyre.py layout}). The base limbs keep the vanilla layout in the top-left
 * 64x64.
 * <p>
 * The change-in film zooms on, in order, {@code right_arm}, {@code left_arm}, {@code core}, {@code hair} and {@code crown}: those names
 * are parts of this mesh.
 */
public final class UltimateSunheart extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** How far (pixels) it hovers off the ground as it walks, and how much of that is left when crouched. */
	static final float HOVER = 3.0F, HOVER_CROUCH = 0.4F;
	/** The flames flicker faster by this much at full speed, and stream back this far (radians) at full speed and when falling. */
	static final float FLICKER_SPEED = 0.8F, STREAM_SPEED = 0.45F, STREAM_FALL = 0.4F;
	/** How far the crown's and the blades' spikes sway (radians), and how fast. */
	static final float SPIKE_SWAY = 0.06F, SPIKE_RATE = 0.19F;
	/** The crown's spikes: how many, the ring they stand on (pixels from the middle), how far they lean out (radians). */
	static final int CROWN_SPIKES = 6;
	static final float CROWN_RING = 4.4F, CROWN_LEAN = 0.4F;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/pyre.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("crown", 0, 64), uv("cspike", 36, 64), uv("hair", 40, 64), uv("tip", 60, 64), uv("chestplate", 72, 64), uv("core", 94, 64),
		uv("backplate", 106, 64), uv("belt", 0, 79), uv("pauld", 30, 79), uv("bracer", 54, 79), uv("bspike", 74, 79), uv("greave", 78, 79),
		uv("plume", 98, 79), uv("lick", 110, 79));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart hair, hairTip, rightPlume, leftPlume, rightLick, leftLick;
	private final ModelPart[] spikes;

	public UltimateSunheart() {
		super(XenoFormModels.bake(mesh(), 128, 128), "pyre");
		// on the sun in the chest, which it taps to change back
		this.insigniaY = 4.0F;
		this.insigniaZ = -4.0F;
		this.insigniaSize = 0.8F;
		this.hair = this.head.getChild("hair");
		this.hairTip = this.hair.getChild("tip");
		this.rightPlume = this.body.getChild("right_plume");
		this.leftPlume = this.body.getChild("left_plume");
		this.rightLick = this.rightArm.getChild("lick_r");
		this.leftLick = this.leftArm.getChild("lick_l");
		ModelPart crown = this.head.getChild("crown");
		this.spikes = new ModelPart[CROWN_SPIKES + 4];
		for (int i = 0; i < CROWN_SPIKES; i++) {
			this.spikes[i] = crown.getChild("crown_spike_" + i);
		}
		this.spikes[CROWN_SPIKES] = this.body.getChild("blade_spike_r0");
		this.spikes[CROWN_SPIKES + 1] = this.body.getChild("blade_spike_r1");
		this.spikes[CROWN_SPIKES + 2] = this.body.getChild("blade_spike_l0");
		this.spikes[CROWN_SPIKES + 3] = this.body.getChild("blade_spike_l1");
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

	private static MeshDefinition mesh() {
		// Pyre's build: the humanoid parts a little puffed (0.3), on the vanilla layout
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		// a basalt crown round the top of the head with flame-tipped spikes leaning out of it, and the hair rising through it, higher than Pyre's
		PartDefinition crown = head.addOrReplaceChild("crown", cube("crown", false, -4.5F, -10.0F, -4.5F, 9, 2, 9, 0.45F), PartPose.ZERO);
		for (int i = 0; i < CROWN_SPIKES; i++) {
			double a = Math.PI / CROWN_SPIKES + i * Math.PI * 2.0 / CROWN_SPIKES;
			float dx = (float)Math.sin(a), dz = (float)-Math.cos(a);
			crown.addOrReplaceChild("crown_spike_" + i, cube("cspike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
				PartPose.offsetAndRotation(dx * CROWN_RING, -9.5F, dz * CROWN_RING, -dz * CROWN_LEAN, 0.0F, dx * CROWN_LEAN));
		}
		PartDefinition hair = head.addOrReplaceChild("hair", cube("hair", false, -2.5F, -10.0F, -2.5F, 5, 10, 5, 0.0F), PartPose.offset(0.0F, -7.5F, 1.0F));
		hair.addOrReplaceChild("tip", cube("tip", false, -1.5F, -7.0F, -1.5F, 3, 7, 3, 0.0F), PartPose.offset(0.0F, -9.0F, 0.0F));
		// the body: a plated chest with the sun set in it, a plate down the back with a pair of spikes on each shoulder blade, a belt of basalt
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("chestplate", cube("chestplate", false, -4.5F, 0.5F, -3.0F, 9, 8, 2, 0.0F), PartPose.ZERO);
		body.addOrReplaceChild("core", cube("core", false, -2.5F, 0.5F, -3.8F, 5, 7, 1, 0.0F), PartPose.ZERO);
		body.addOrReplaceChild("backplate", cube("backplate", false, -4.0F, 1.0F, 2.6F, 8, 8, 1, 0.0F), PartPose.ZERO);
		body.addOrReplaceChild("belt", cube("belt", false, -4.5F, 9.5F, -3.0F, 9, 2, 6, 0.0F), PartPose.ZERO);
		body.addOrReplaceChild("blade_spike_r0", cube("cspike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(-3.0F, 2.0F, 3.4F, -0.7F, 0.0F, -0.3F));
		body.addOrReplaceChild("blade_spike_r1", cube("cspike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(-1.5F, 4.5F, 3.4F, -0.55F, 0.0F, -0.15F));
		body.addOrReplaceChild("blade_spike_l0", cube("cspike", true, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(3.0F, 2.0F, 3.4F, -0.7F, 0.0F, 0.3F));
		body.addOrReplaceChild("blade_spike_l1", cube("cspike", true, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(1.5F, 4.5F, 3.4F, -0.55F, 0.0F, 0.15F));
		// flames off the shoulders, up above the pauldrons
		body.addOrReplaceChild("right_plume", cube("plume", false, -2.0F, -5.0F, -1.5F, 3, 6, 3, 0.0F), PartPose.offset(-6.5F, -1.5F, 0.0F));
		body.addOrReplaceChild("left_plume", cube("plume", true, -1.0F, -5.0F, -1.5F, 3, 6, 3, 0.0F), PartPose.offset(6.5F, -1.5F, 0.0F));
		// the arms: a basalt pauldron on each shoulder, a bracer on each forearm with a spike at the elbow, a lick of flame off the outside
		PartDefinition rightArm = root.getChild("right_arm");
		rightArm.addOrReplaceChild("pauldron_r", cube("pauld", false, -4.0F, -3.5F, -3.0F, 6, 3, 6, 0.0F), PartPose.ZERO);
		rightArm.addOrReplaceChild("bracer_r", cube("bracer", false, -3.5F, 5.5F, -2.5F, 5, 5, 5, 0.2F), PartPose.ZERO);
		rightArm.addOrReplaceChild("elbow_spike_r", cube("bspike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
			PartPose.offsetAndRotation(-1.0F, 7.5F, 3.0F, -1.35F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("lick_r", cube("lick", false, -1.0F, -5.0F, -1.0F, 2, 5, 2, 0.0F), PartPose.offset(-3.0F, 6.0F, 0.5F));
		PartDefinition leftArm = root.getChild("left_arm");
		leftArm.addOrReplaceChild("pauldron_l", cube("pauld", true, -2.0F, -3.5F, -3.0F, 6, 3, 6, 0.0F), PartPose.ZERO);
		leftArm.addOrReplaceChild("bracer_l", cube("bracer", true, -1.5F, 5.5F, -2.5F, 5, 5, 5, 0.2F), PartPose.ZERO);
		leftArm.addOrReplaceChild("elbow_spike_l", cube("bspike", true, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
			PartPose.offsetAndRotation(1.0F, 7.5F, 3.0F, -1.35F, 0.0F, 0.0F));
		leftArm.addOrReplaceChild("lick_l", cube("lick", true, -1.0F, -5.0F, -1.0F, 2, 5, 2, 0.0F), PartPose.offset(3.0F, 6.0F, 0.5F));
		// the legs: a basalt greave on each shin, a spike on each knee
		for (String side : new String[] {"right", "left"}) {
			boolean left = side.equals("left");
			PartDefinition leg = root.getChild(side + "_leg");
			leg.addOrReplaceChild("greave_" + side.charAt(0), cube("greave", left, -2.5F, 5.5F, -2.5F, 5, 5, 5, 0.3F), PartPose.ZERO);
			leg.addOrReplaceChild("knee_spike_" + side.charAt(0), cube("bspike", left, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
				PartPose.offsetAndRotation(0.0F, 5.0F, -2.9F, 1.35F, 0.0F, 0.0F));
		}
		return mesh;
	}

	/** The forearm flames upright for the first-person arm (the model is shared, so they hold whoever was drawn last). */
	public void handPose() {
		this.rightLick.xRot = 0.0F;
		this.leftLick.xRot = 0.0F;
		this.rightLick.zRot = 0.25F;
		this.leftLick.zRot = -0.25F;
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		float t = state.ageInTicks;
		XenoMotion.Read m = XenoMotion.read(state);
		// it hovers a hand's width off the ground
		this.root().y = -HOVER * (state.isCrouching ? HOVER_CROUCH : 1.0F);
		// the flames flicker faster and stream back the faster it goes, lean out of a turn, and leap up as it falls
		float flicker = t * (1.0F + m.speed() * FLICKER_SPEED);
		float stream = m.speed() * STREAM_SPEED + Math.max(0.0F, m.fall()) * STREAM_FALL;
		this.hair.xRot = Mth.cos(flicker * 0.27F) * 0.08F - 0.35F - stream;
		this.hair.zRot = Mth.sin(flicker * 0.35F) * 0.12F - m.trail() * 0.25F;
		this.hairTip.xRot = Mth.cos(flicker * 0.41F + 0.7F) * 0.15F - 0.3F - stream * 0.8F;
		this.hairTip.zRot = Mth.sin(flicker * 0.53F + 1.3F) * 0.2F - m.trail() * 0.3F;
		this.rightPlume.zRot = 0.3F + Mth.sin(flicker * 0.4F) * 0.15F - m.trail() * 0.2F;
		this.leftPlume.zRot = -0.3F - Mth.sin(flicker * 0.4F + 1.0F) * 0.15F - m.trail() * 0.2F;
		this.rightPlume.xRot = -stream;
		this.leftPlume.xRot = -stream;
		// the forearm flames point up whatever the arm does (cancelling its swing) and stream back
		this.rightLick.xRot = -this.rightArm.xRot - stream + Mth.sin(flicker * 0.47F) * 0.15F;
		this.leftLick.xRot = -this.leftArm.xRot - stream + Mth.sin(flicker * 0.47F + 2.0F) * 0.15F;
		this.rightLick.zRot = 0.25F + Mth.cos(flicker * 0.38F) * 0.12F;
		this.leftLick.zRot = -0.25F - Mth.cos(flicker * 0.38F + 0.5F) * 0.12F;
		// the spikes sway a little, out of step with one another
		for (int i = 0; i < this.spikes.length; i++) {
			this.spikes[i].resetPose();
			this.spikes[i].zRot += Mth.sin(t * SPIKE_RATE + i * 1.7F) * SPIKE_SWAY;
			this.spikes[i].xRot += Mth.cos(t * SPIKE_RATE * 0.8F + i) * SPIKE_SWAY * 0.5F - stream * 0.3F;
		}
	}
}
