package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.42, Andy's NOEA picks) Banking into turns: a Z Fighter in flight rolls his body into the turn, leaning right
 * as he turns right, in proportion to how fast he is turning ({@link #DEGREES_PER_TURN_DEGREE} of lean per degree of turn
 * per tick, up to {@link #MAX_BANK}), easing in and out ({@link #EASE}). It is seen on everyone's screen, from how their
 * facing changes. Upright (hovering) it is a roll about the body's middle through {@code FlightRolls.rollUpright}; at
 * cruising speed Wind's laid-out flight already rolls into turns, so this fades out as that pose takes over.
 */
public final class ZFighterBank {
	static final float DEGREES_PER_TURN_DEGREE = 4.0F;
	static final float MAX_BANK = 35.0F;
	static final float EASE = 0.25F;

	/** The bank on a player's render state (degrees, positive to the right); absent when not flying. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_bank");

	/** entity id → {facing last tick, bank last tick, bank now}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterBank() {
	}

	/** Every client tick, from {@link ZFighterPowerClient#init}: follow each flying Z Fighter's turn. */
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
			boolean flying = ZFighterPower.flightPose(player) > 0;
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (!flying) {
					continue;
				}
				s = new float[] {player.getYRot(), 0.0F, 0.0F};
				STATE.put(player.getId(), s);
			}
			float turned = Mth.wrapDegrees(player.getYRot() - s[0]);
			s[0] = player.getYRot();
			s[1] = s[2];
			float target = flying ? Mth.clamp(turned * DEGREES_PER_TURN_DEGREE, -MAX_BANK, MAX_BANK) : 0.0F;
			s[2] += (target - s[2]) * EASE;
		}
		STATE.entrySet().removeIf(entry -> !(level.getEntity(entry.getKey()) instanceof Player p)
			|| ZFighterPower.flightPose(p) <= 0 && Math.abs(entry.getValue()[1]) < 0.05F && Math.abs(entry.getValue()[2]) < 0.05F);
	}

	/** From {@code FlightRolls.extract}: this frame's bank on the render state. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		state.setData(KEY, s == null ? null : Mth.lerp(partialTicks, s[1], s[2]));
	}

	/** The bank on this render state (0 when none), degrees, positive to the right. */
	public static float angle(LivingEntityRenderState state) {
		Float angle = state.getData(KEY);
		return angle == null ? 0.0F : angle;
	}
}
