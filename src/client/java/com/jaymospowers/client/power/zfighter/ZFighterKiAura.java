package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
 * (original texture, {@code tools/gen_ki_aura.py}), in his ki colour (a form's colour in a form). Two layers, the inner
 * one brighter, turning against each other. Full while charging; held after a full charge ({@link ZFighterPower#AURA_HOLD});
 * in a form it flares up and dies back every so often, quicker each form. The faint {@link ZFighterKiGlow} stays the
 * look in between. Not on the Great Ape, while hidden, nor round your own camera in first person.
 * <p>
 * (0.17.46, Andy's NOEA picks) Three styles ({@link ZFighterAura}, picked on the training screen): this flame is
 * "Sparking" (with {@link ZFighterKiAuraStyles#sparks} on it); Smooth and Godly are drawn by {@link ZFighterKiAuraStyles}
 * under the same rules. The fourth number on the render state is the style.
 */
public final class ZFighterKiAura {
	/** On his render state: the aura's colour (RGB), strength (0-1), size and style, or null for none. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ki_aura");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_aura.png");
	private static final RenderType TYPE = RenderTypes.energySwirl(TEXTURE, 0.0F, 0.0F);
	static final int FULL_BRIGHT = 0xF000F0;

	/** The shape (model units, y down from the hips' pivot): radius at each ring, bottom (his feet) to top (the tips). */
	static final float[] RADIUS = {0.30F, 0.56F, 0.72F, 0.80F, 0.79F, 0.72F, 0.58F, 0.40F, 0.14F};
	static final float BOTTOM = 1.62F, TOP = -1.2F;
	/** Columns round it; rings from this one up break into tongues that reach up to this much higher (every other one less). */
	static final int COLUMNS = 18, TONGUE_FROM = 5;
	static final float TONGUE = 0.55F, SHORT_TONGUE = 0.45F, TONGUE_SPEED = 0.35F;
	/** The sides' flutter (share of the radius) and how fast it moves. */
	static final float WOBBLE = 0.06F, WOBBLE_SPEED = 0.7F;
	/** How bright each ring is, bottom to top (fading out at his feet and the tips). */
	static final float[] RING_LIGHT = {0.15F, 0.6F, 0.85F, 1.0F, 1.0F, 0.95F, 0.8F, 0.5F, 0.0F};
	/** The layers, inside out: size, brightness, how fast the flames rise and how fast it turns (degrees a tick). */
	static final float[] LAYER_SIZE = {0.82F, 1.0F};
	static final float[] LAYER_LIGHT = {0.55F, 0.34F};
	static final float[] LAYER_RISE = {0.05F, 0.035F};
	static final float[] LAYER_SPIN = {2.2F, -1.4F};
	/** The texture's repeats round and up the aura. */
	static final float U_REPEAT = 2.0F, V_REPEAT = 1.0F;

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
		// (0.17.48) a branch form or Ego reads as a higher form
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
		return new float[] {ZFighterKiGlow.color(player, f), strength, size, ZFighterAura.style(player)};
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
			int color = (int)aura[0];
			float age = state.ageInTicks;
			// (0.17.46) the style he picked: smooth and godly have shells of their own; sparking is the flame below plus sparks
			int style = (int)aura[3];
			if (style == ZFighterAura.SMOOTH) {
				ZFighterKiAuraStyles.smooth(poseStack, collector, color, aura[1], aura[2], age);
				return;
			}
			if (style == ZFighterAura.GODLY) {
				ZFighterKiAuraStyles.godly(poseStack, collector, color, aura[1], aura[2], age);
				return;
			}
			for (int layer = 0; layer < LAYER_SIZE.length; layer++) {
				float light = Mth.clamp(LAYER_LIGHT[layer] * aura[1], 0.0F, 1.0F);
				float size = LAYER_SIZE[layer] * aura[2];
				float spin = age * LAYER_SPIN[layer] * Mth.DEG_TO_RAD;
				float rise = age * LAYER_RISE[layer];
				int seed = layer * 7;
				// after the glow's shells, inner layer first, so the light piles up outward
				collector.order(6 + layer).submitCustomGeometry(poseStack, TYPE,
					(pose, buffer) -> shell(pose, buffer, color, light, size, spin, rise, age, seed));
			}
			ZFighterKiAuraStyles.sparks(poseStack, collector, color, aura[1], aura[2], age);
		}

		/** One layer: a ring of columns, each a strip of quads from his feet up to its tongue's tip. */
		private static void shell(PoseStack.Pose pose, VertexConsumer buffer, int color, float light, float size, float spin, float rise,
			float age, int seed) {
			int rings = RADIUS.length;
			float[][] x = new float[COLUMNS + 1][rings], y = new float[COLUMNS + 1][rings], z = new float[COLUMNS + 1][rings];
			for (int c = 0; c <= COLUMNS; c++) {
				int col = c % COLUMNS;
				float a = Mth.TWO_PI * col / COLUMNS + spin;
				float cos = Mth.cos(a), sin = Mth.sin(a);
				float reach = (col % 2 == 0 ? 1.0F : SHORT_TONGUE)
					* (0.5F + 0.5F * Mth.sin(age * TONGUE_SPEED * (1.0F + 0.13F * ((col * 5 + seed) % 7)) + col * 2.3F + seed));
				for (int r = 0; r < rings; r++) {
					float t = (float)r / (rings - 1);
					float wobble = 1.0F + WOBBLE * Mth.sin(age * WOBBLE_SPEED + col * 1.7F + r * 0.9F + seed);
					float radius = RADIUS[r] * size * wobble;
					float up = r >= TONGUE_FROM ? TONGUE * reach * (r - TONGUE_FROM + 1) / (rings - TONGUE_FROM) : 0.0F;
					x[c][r] = cos * radius;
					z[c][r] = sin * radius;
					// grown from his feet up, so a bigger aura stands on the same ground
					y[c][r] = BOTTOM - (BOTTOM - Mth.lerp(t, BOTTOM, TOP)) * size - up * size;
				}
			}
			for (int c = 0; c < COLUMNS; c++) {
				float u0 = U_REPEAT * c / COLUMNS, u1 = U_REPEAT * (c + 1) / COLUMNS;
				for (int r = 0; r < rings - 1; r++) {
					float v0 = V_REPEAT * (1.0F - (float)r / (rings - 1)) + rise, v1 = V_REPEAT * (1.0F - (float)(r + 1) / (rings - 1)) + rise;
					int lo = tint(color, light * RING_LIGHT[r]), hi = tint(color, light * RING_LIGHT[r + 1]);
					vertex(pose, buffer, x[c][r], y[c][r], z[c][r], u0, v0, lo);
					vertex(pose, buffer, x[c + 1][r], y[c + 1][r], z[c + 1][r], u1, v0, lo);
					vertex(pose, buffer, x[c + 1][r + 1], y[c + 1][r + 1], z[c + 1][r + 1], u1, v1, hi);
					vertex(pose, buffer, x[c][r + 1], y[c][r + 1], z[c][r + 1], u0, v1, hi);
				}
			}
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
