package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * The stance while charging ki (R): every lineage has its own, and it changes with each form. Eased in and out, with a
 * tremble that grows with the form. Flying, the legs keep their hover.
 */
public final class ZFighterChargePose {
	/** One player this frame: which stance (lineage, form), how far into it (0-1), the time. */
	public record Pose(int lineage, int form, float amount, float time) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_charge");
	static final float EASE = 0.2F;
	/** The tremble (radians) in base form, and more for each form. */
	static final float SHAKE = 0.015F;
	static final float SHAKE_PER_FORM = 0.012F;

	/**
	 * [lineage][form]: lead arm x y zOut, other arm x y zOut, legs apart (zOut), head x. Lineage 0 (not chosen yet) shares
	 * the Earthling's. Arm x: negative raises it forward and up; y: positive turns it inwards; zOut: positive swings it
	 * out from the body.
	 */
	private static final float[][][] STANCES = {
		{},
		// Earthling: fists clenched at the hips; Super roars at the sky; Super 2 braced forward; Super 3 arms flung wide
		{
			{0.35F, 0.0F, 0.35F, 0.35F, 0.0F, 0.35F, 0.25F, 0.2F},
			{0.05F, 0.0F, 0.55F, 0.05F, 0.0F, 0.55F, 0.3F, -0.5F},
			{-0.3F, 0.0F, 0.6F, -0.3F, 0.0F, 0.6F, 0.35F, 0.0F},
			{-0.6F, 0.0F, 1.2F, -0.6F, 0.0F, 1.2F, 0.4F, -0.7F},
		},
		// Warrior-born: fists up before the chest; arms wrenched back, screaming skyward; one fist raised; both straight up
		{
			{-1.1F, 0.5F, 0.0F, -1.1F, 0.5F, 0.0F, 0.3F, 0.1F},
			{0.6F, 0.0F, 0.4F, 0.6F, 0.0F, 0.4F, 0.3F, -0.6F},
			{-2.6F, 0.0F, 0.2F, 0.3F, 0.0F, 0.5F, 0.35F, -0.3F},
			{-2.9F, 0.0F, 0.3F, -2.9F, 0.0F, 0.3F, 0.45F, -0.8F},
		},
		// Verdant: palms together, head bowed; Giant arms out wide; Great Giant a raised V; Titan flexing
		{
			{-1.2F, 0.7F, 0.0F, -1.2F, 0.7F, 0.0F, 0.15F, 0.3F},
			{-0.1F, 0.0F, 1.5F, -0.1F, 0.0F, 1.5F, 0.4F, -0.2F},
			{-2.5F, 0.0F, 0.6F, -2.5F, 0.0F, 0.6F, 0.4F, -0.4F},
			{-0.6F, 0.0F, 1.6F, -0.6F, 0.0F, 1.6F, 0.5F, -0.3F},
		},
		// Frost-born: hands behind the back, chin up; Second Form low and clawed; Final one palm raised, the other on the
		// hip; Golden arms down and open, at ease
		{
			{0.7F, 0.4F, 0.0F, 0.7F, 0.4F, 0.0F, 0.05F, -0.15F},
			{-0.6F, 0.0F, 0.3F, -0.6F, 0.0F, 0.3F, 0.35F, -0.2F},
			{-2.0F, -0.3F, 0.2F, 0.2F, 0.0F, 0.6F, 0.1F, -0.1F},
			{0.2F, 0.0F, 0.9F, 0.2F, 0.0F, 0.9F, 0.15F, -0.3F},
		},
	};

	/** entity id → {amount, amount before, lineage, form}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterChargePose() {
	}

	/** Every client tick: ease everyone into or out of their stance. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Integer charge = player.getAttached(ZFighterPower.CHARGE);
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (charge == null) {
					continue;
				}
				s = new float[4];
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			if (charge != null) {
				s[0] = Math.min(1.0F, s[0] + EASE);
				s[2] = ZFighterLineage.of(player);
				s[3] = Mth.clamp(charge - 1, 0, 3);
			} else {
				s[0] = Math.max(0.0F, s[0] - EASE);
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		if (s == null || !(entity instanceof Player)) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Pose((int)s[2], (int)s[3], Mth.lerp(partialTicks, s[1], s[0]), entity.tickCount + partialTicks));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F) {
			return;
		}
		float a = ZFighterFlightPose.smooth(pose.amount());
		float[] p = STANCES[Mth.clamp(pose.lineage(), ZFighterLineage.EARTHLING, STANCES.length - 1)][pose.form()];
		float shake = (SHAKE + SHAKE_PER_FORM * pose.form()) * Mth.sin(pose.time() * 2.7F);
		ModelPart lead = avatar.mainArm == HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		ModelPart other = lead == h.rightArm ? h.leftArm : h.rightArm;
		float leadOut = lead == h.rightArm ? 1.0F : -1.0F;
		// y turns inwards: for the right arm that's negative yRot, for the left positive
		pose(lead, p[0] + shake, -p[1] * leadOut, p[2] * leadOut + shake, a);
		pose(other, p[3] - shake, p[4] * leadOut, p[5] * -leadOut - shake, a);
		ZFighterFlightPose.Pose flight = avatar.getData(ZFighterFlightPose.KEY);
		if (flight == null || flight.amount() <= 0.001F) {
			pose(h.rightLeg, 0.0F, 0.0F, p[6] * 0.5F, a);
			pose(h.leftLeg, 0.0F, 0.0F, -p[6] * 0.5F, a);
		}
		h.head.xRot = Mth.lerp(a, h.head.xRot, p[7] + shake);
	}

	private static void pose(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
