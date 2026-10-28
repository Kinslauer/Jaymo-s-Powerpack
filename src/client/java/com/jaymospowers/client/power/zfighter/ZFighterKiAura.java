package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterAura;
import com.jaymospowers.power.zfighter.ZFighterBranchForms;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.20) Andy, pointing at JinGames' Dragon Block C (no licence published, so All Rights Reserved; the idea only):
 * "the aura i want appears when charging, after achieving max it stays for a bit until your ki falls, and it pulses as
 * you are transformed with the current rising debris". A flame-shaped shell of light round the whole body, widest at
 * the hips and breaking at the top into tongues that flicker and lick up past the head, with flames scrolling up it
 * (original texture), in his ki colour (a form's colour in a form). Two layers, the inner
 * one brighter, turning against each other. Full while charging; held after a full charge ({@link ZFighterPower#AURA_HOLD});
 * in a form it flares up and dies back every so often, quicker each form. The faint {@link ZFighterKiGlow} stays the
 * look in between. Not on the Great Ape, while hidden, nor round your own camera in first person.
 * <p>
 * (0.17.46, Andy's NOEA picks) Three styles ({@link ZFighterAura}, picked on the training screen): this flame is
 * "Sparking" (with {@link ZFighterKiAuraStyles#sparks} on it); Smooth and Godly are drawn by {@link ZFighterKiAuraStyles}
 * under the same rules. The fourth number on the render state is the style.
 * <p>
 * (0.17.63) Andy: "all auras need to be more of a wisp making contact and dissipating rather than being a solid mesh":
 * the shells are gone. Every style is now wisps of light that touch the body, lick off it and fade
 * ({@link ZFighterKiAuraStyles#draw}); this class still decides when it shows, how strong, how big and what colour, and
 * the fifth number is how far it streams back in flight.
 */
public final class ZFighterKiAura {
	/** On his render state: the aura's colour (RGB), strength (0-1), size, style and trail (0-1), or null for none. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ki_aura");
	static final int FULL_BRIGHT = 0xF000F0;

	/** Strength while charging, and held after a full charge; how fast it grows in and dies out (share a tick). */
	static final float CHARGING = 1.0F, HOLDING = 0.85F, GROW = 0.35F, FADE = 0.12F;
	/** Bigger each form, and a flicker of size while charging. */
	static final float SIZE_PER_FORM = 0.08F, CHARGE_FLICKER = 0.04F;
	/**
	 * In a form: a pulse every this many ticks (fewer each form, down to the least), rising over this share of it and
	 * dying back this fast; at its height this strong, and this strong in between (0: only the glow shows).
	 */
	static final int PULSE_EVERY = 46, PULSE_PER_FORM = 5, PULSE_LEAST = 24;
	static final float PULSE_RISE = 0.12F, PULSE_DECAY = 5.0F, PULSE_HIGH = 0.75F, PULSE_LOW = 0.0F;
	/** A pulse swells the aura out by this much at its height. */
	static final float PULSE_SWELL = 0.1F;
	/** Flying: the aura starts to stream back at this speed (blocks a tick) and fully at this one. */
	static final double TRAIL_FROM = 0.2, TRAIL_FULL = 1.0;

	/** How strong each one's charge/hold aura is now (eased in and out), and the age it was last eased at. */
	private static final Map<Player, float[]> EASED = new WeakHashMap<>();

	private ZFighterKiAura() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player ? aura(player, state.ageInTicks) : null);
	}

	private static float @Nullable [] aura(Player player, float age) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || player.isInvisible() || player.isSpectator() || ZFighterLineage.ape(player)) {
			EASED.remove(player);
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			return null;
		}
		Integer form = player.getAttached(ZFighterPower.FORM);
		// (0.17.50) a branch form or Ego reads as a higher form
		int f = ZFighterBranchForms.tier(player, form == null ? 0 : form);
		boolean charging = player.hasAttached(ZFighterPower.CHARGE);
		Long hold = player.getAttached(ZFighterPower.AURA_HOLD);
		boolean holding = hold != null && player.level().getGameTime() < hold;
		float target = charging ? CHARGING : holding ? HOLDING : 0.0F;

		float[] eased = EASED.computeIfAbsent(player, p -> new float[] {0.0F, age});
		float dt = Mth.clamp(age - eased[1], 0.0F, 10.0F);
		eased[1] = age;
		float rate = target > eased[0] ? GROW : FADE;
		eased[0] += (target - eased[0]) * Math.min(1.0F, dt * rate);

		float pulse = 0.0F;
		if (f > 0) {
			int every = Math.max(PULSE_LEAST, PULSE_EVERY - PULSE_PER_FORM * com.jaymospowers.power.zfighter.ZFighterForms.tier(f));
			float phase = (age % every) / every;
			float flare = phase < PULSE_RISE ? phase / PULSE_RISE : (float)Math.exp(-(phase - PULSE_RISE) * PULSE_DECAY);
			pulse = PULSE_LOW + (PULSE_HIGH - PULSE_LOW) * flare;
		}
		float strength = Math.max(eased[0], pulse);
		if (strength < 0.01F) {
			return null;
		}
		float size = 1.0F + SIZE_PER_FORM * com.jaymospowers.power.zfighter.ZFighterForms.tier(f) + (pulse > eased[0] ? PULSE_SWELL * pulse : 0.0F)
			+ (charging ? CHARGE_FLICKER * Mth.sin(age * 1.3F) : 0.0F);
		float trail = 0.0F;
		Integer flight = player.getAttached(ZFighterPower.FLIGHT);
		if (flight != null && flight > 0) {
			// (from where he was last tick: a remote player's motion isn't sent)
			double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
			double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
			trail = (float)Mth.clamp((speed - TRAIL_FROM) / (TRAIL_FULL - TRAIL_FROM), 0.0, 1.0);
		}
		return new float[] {ZFighterKiGlow.color(player, f), strength, size, ZFighterAura.style(player), trail};
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			float[] aura = state.getData(KEY);
			if (aura == null || state.isInvisible) {
				return;
			}
			// (0.17.63) whichever style, wisps now; the style decides their look
			ZFighterKiAuraStyles.draw(poseStack, collector, (int)aura[3], (int)aura[0], aura[1], aura[2], aura[4], state.ageInTicks);
		}

		/** Additive: the colour's brightness is how strongly it shows. */
		static int tint(int color, float light) {
			light = Mth.clamp(light, 0.0F, 1.0F);
			return ARGB.color(255, (int)(ARGB.red(color) * light), (int)(ARGB.green(color) * light), (int)(ARGB.blue(color) * light));
		}

		static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
			buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
				.setNormal(pose, 0.0F, -1.0F, 0.0F);
		}
	}
}
