package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * (0.21.37, Ultimates patch 2) Duplex's Ultimate, Copper Storm: the copper golem's own build (the wide flat head with its nose, the
 * short body, long arms hanging near the ground, stubby legs and the belt; same boxes, same gait) grown to about 1.6 blocks, with
 * patina-green plates over the crown and cheeks, the shoulders, the forearms, the back and the boots, dark copper spikes on the crown,
 * shoulders and back, a bright copper coil of three rings wound under the rod's ball tip, and electric blue-white seams (the skin's glow
 * map, drawn fullbright by the layer). The skin is {@code tools/ultimates/duplex.py}, 128x128; {@link #UV} here is the same table that
 * script prints ({@code duplex.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code belt}, {@code right_arm} and {@code left_arm}, {@code head}, then {@code rod} and
 * {@code knob}: those names are parts of this mesh.
 */
public final class UltimateCopperStorm extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The golem's 16 pixels, at the player's 0.9375 and Copper Storm's scale of 0.9, come to this many blocks: it is drawn up from the feet to that. */
	static final float HEIGHT = 1.6F;
	/** The rod nods with each step (radians at full speed), whips back on a landing and lags a turn; how far the coil's rings sway after it. */
	static final float ROD_STEP = 0.1F, ROD_LAND = 0.35F, ROD_FALL = 0.25F, ROD_TRAIL = 0.3F, RING_LAG = 0.35F;
	/** The heavy body's roll with each step (radians at full speed). */
	static final float ROLL = 0.04F;
	/** The crown's and the back's spikes: how far they lean back (radians). */
	static final float CROWN_LEAN = -0.3F, BACK_LEAN = -1.1F, SHOULDER_LEAN = 0.5F;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/duplex.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0), uv("nose", 36, 0), uv("crown", 44, 0), uv("cheek", 84, 0), uv("rod", 98, 0), uv("coil", 106, 0),
		uv("knob", 0, 15), uv("body", 16, 15), uv("belt", 44, 15), uv("backplate", 72, 15), uv("arm", 86, 15), uv("pauld", 100, 15),
		uv("bracer", 0, 29), uv("leg", 18, 29), uv("boot", 34, 29), uv("spike", 56, 29));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart rod, knob;
	private final ModelPart[] rings = new ModelPart[3];

	public UltimateCopperStorm() {
		super(XenoFormModels.bake(mesh(), 128, 128), "duplex");
		this.insigniaY = 2.5F;
		this.insigniaZ = -3.0F;
		this.insigniaSize = 0.65F;
		// drawn up from the feet: 16 pixels tall at the player's 0.9375 and the Ultimate's scale (0.9)
		this.renderScale = HEIGHT / (0.9375F * com.jaymospowers.power.xenoform.forms.ultimate.CopperStorm.SCALE);
		this.rod = this.head.getChild("rod");
		this.knob = this.rod.getChild("knob");
		for (int i = 0; i < 3; i++) {
			this.rings[i] = this.rod.getChild("coil_" + i);
		}
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter); {@code left} mirrors it. */
	private static CubeListBuilder cube(String key, boolean left, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		CubeListBuilder b = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (left) {
			b = b.mirror();
		}
		return b.addBox(x, y, z, w, h, d);
	}

	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d) {
		return cube(key, false, x, y, z, w, h, d);
	}

	private static MeshDefinition mesh() {
		// the golem's boxes (CopperGolemModel), stood on the humanoid's feet line (y 24): legs from 19, body and head from 13;
		// the arms hang from the top of the body
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", cube("head", -4.0F, -5.0F, -5.0F, 8, 5, 10), PartPose.offset(0.0F, 13.0F, 0.0F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("nose", cube("nose", -1.0F, -2.0F, -6.0F, 2, 3, 2), PartPose.ZERO);
		// a crown plate across the top with three spikes leaning back, and a cheek plate each side
		PartDefinition crown = head.addOrReplaceChild("crown", cube("crown", -4.5F, -6.0F, -5.5F, 9, 1, 11), PartPose.ZERO);
		for (int i = 0; i < 3; i++) {
			crown.addOrReplaceChild("crown_spike_" + i, cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1),
				PartPose.offsetAndRotation(-2.5F + 2.5F * i, -6.0F, -1.0F + (i == 1 ? 1.0F : 0.0F), CROWN_LEAN, 0.0F, (i - 1) * 0.2F));
		}
		head.addOrReplaceChild("cheek_r", cube("cheek", -5.0F, -4.0F, -3.0F, 1, 4, 6), PartPose.ZERO);
		head.addOrReplaceChild("cheek_l", cube("cheek", true, 4.0F, -4.0F, -3.0F, 1, 4, 6), PartPose.ZERO);
		// the lightning rod, a coil of three rings wound under the polished ball tip
		PartDefinition rod = head.addOrReplaceChild("rod", cube("rod", -1.0F, -6.0F, -1.0F, 2, 6, 2), PartPose.offset(0.0F, -6.0F, 0.0F));
		for (int i = 0; i < 3; i++) {
			rod.addOrReplaceChild("coil_" + i, cube("coil", -2.0F, 0.0F, -2.0F, 4, 1, 4), PartPose.offset(0.0F, -2.0F - 1.5F * i, 0.0F));
		}
		rod.addOrReplaceChild("knob", cube("knob", -2.0F, -4.0F, -2.0F, 4, 4, 4), PartPose.offset(0.0F, -6.0F, 0.0F));
		// the body, its belt, a plate across the back and a ridge of spikes down it
		PartDefinition body = root.addOrReplaceChild("body", cube("body", -4.0F, 0.0F, -3.0F, 8, 6, 6), PartPose.offset(0.0F, 13.0F, 0.0F));
		body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(UV.get("belt")[0], UV.get("belt")[1]).addBox(-4.0F, 4.0F, -3.0F, 8.0F, 2.0F, 6.0F,
			new CubeDeformation(0.3F)), PartPose.ZERO);
		PartDefinition back = body.addOrReplaceChild("backplate", cube("backplate", -3.0F, 0.5F, 3.0F, 6, 4, 1), PartPose.ZERO);
		for (int i = 0; i < 3; i++) {
			back.addOrReplaceChild("back_spike_" + i, cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1),
				PartPose.offsetAndRotation(0.0F, 1.0F + 1.6F * i, 4.0F, BACK_LEAN, 0.0F, 0.0F));
		}
		// the arms, long, with a shouldered plate (and a spike on it) and a bracer over the forearm
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", cube("arm", -3.0F, -1.0F, -2.0F, 3, 10, 4), PartPose.offset(-4.0F, 14.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", cube("arm", true, 0.0F, -1.0F, -2.0F, 3, 10, 4), PartPose.offset(4.0F, 14.0F, 0.0F));
		PartDefinition pauldR = rightArm.addOrReplaceChild("pauldron_r", cube("pauld", -4.0F, -2.5F, -3.0F, 5, 2, 6), PartPose.ZERO);
		PartDefinition pauldL = leftArm.addOrReplaceChild("pauldron_l", cube("pauld", true, -1.0F, -2.5F, -3.0F, 5, 2, 6), PartPose.ZERO);
		pauldR.addOrReplaceChild("shoulder_spike_r", cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1),
			PartPose.offsetAndRotation(-2.0F, -2.5F, 0.0F, 0.0F, 0.0F, -SHOULDER_LEAN));
		pauldL.addOrReplaceChild("shoulder_spike_l", cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1),
			PartPose.offsetAndRotation(2.0F, -2.5F, 0.0F, 0.0F, 0.0F, SHOULDER_LEAN));
		rightArm.addOrReplaceChild("bracer_r", cube("bracer", -3.5F, 3.5F, -2.5F, 4, 5, 5), PartPose.ZERO);
		leftArm.addOrReplaceChild("bracer_l", cube("bracer", true, -0.5F, 3.5F, -2.5F, 4, 5, 5), PartPose.ZERO);
		// the stubby legs, each with a plate over the boot
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", cube("leg", -2.0F, 0.0F, -2.0F, 4, 5, 4), PartPose.offset(-2.0F, 19.0F, 0.0F));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", cube("leg", true, -2.0F, 0.0F, -2.0F, 4, 5, 4), PartPose.offset(2.0F, 19.0F, 0.0F));
		rightLeg.addOrReplaceChild("boot_r", cube("boot", -2.5F, 3.0F, -3.0F, 5, 2, 6), PartPose.ZERO);
		leftLeg.addOrReplaceChild("boot_l", cube("boot", true, -2.5F, 3.0F, -3.0F, 5, 2, 6), PartPose.ZERO);
		return mesh;
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		XenoMotion.Read m = XenoMotion.read(state);
		float sp = Math.min(1.0F, state.walkAnimationSpeed);
		float p = state.walkAnimationPos * 0.6662F;
		// heavy: it rolls a little over each planted foot
		this.body.zRot = Mth.cos(p) * ROLL * sp;
		// the rod nods with each step, lags a turn and whips back on landing; the rings and the ball follow a beat behind
		float step = Mth.sin(state.walkAnimationPos * 0.6662F * 2.0F) * ROD_STEP * state.walkAnimationSpeed;
		float whip = -m.land() * ROD_LAND + Math.max(0.0F, m.fall()) * ROD_FALL * m.air();
		float trail = -m.trail() * ROD_TRAIL + Mth.sin(state.ageInTicks * 0.05F) * 0.03F;
		this.rod.xRot = step + whip;
		this.rod.zRot = trail;
		for (int i = 0; i < 3; i++) {
			this.rings[i].xRot = Mth.sin(state.ageInTicks * 0.2F + i * 0.9F) * 0.04F * RING_LAG;
		}
		this.knob.xRot = whip * 0.3F;
		this.knob.zRot = trail * 0.3F;
	}
}
