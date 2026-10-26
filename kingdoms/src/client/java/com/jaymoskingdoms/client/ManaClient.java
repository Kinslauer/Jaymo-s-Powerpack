package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaAlchemy;
import com.jaymoskingdoms.ManaSickness;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.212) What the attuned see of mana ({@link Mana}). Andy: "mana is displayed as the glowing ki that z fighters had
 * previously. now it is only visible by those who are ALSO attuned ... the glow is more faint than before, and intensifies
 * with the user's mana." A soft blue glow hugging an attuned player's outline, after Jaymo's Powers' ki glow (our own
 * look and texture): a few shells just off the body, wisps scrolling up them, added like light. Fainter than the ki
 * glow, and brighter the fuller their mana; it flares while they're overloaded, then settles to a dim rest. Drawn only
 * when the one looking is attuned too.
 * <p>
 * (0.7.235) Its rest shows their power ({@link Mana#POWER}): the weakest glow dimmest, and Arcana raises it. The casting
 * folk glow too ({@link Folk}, on every humanoid that isn't a player). Every cast sends a ripple out through the glow,
 * coloured by its intent ({@link #INTENT}). The corrupted see all of it as the attuned do ({@link ManaAlchemy#readsMagic}).
 * <p>
 * And on your own screen: attuned just now, your sight goes blue and settles back over {@link Mana#OVERLOAD_TICKS}; mana
 * sick ({@link ManaSickness}), it's washed a sickly grey-green that pulses, worse the sicker you are.
 */
public final class ManaClient extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a being's render state: its glow, or null for none. */
	public static final RenderStateDataKey<Glow> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:mana_glow");
	public static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/mana_glow.png");
	/** Mana's blue. */
	public static final int COLOR = 0x5A9CFF;
	/** How far out each shell sits (model pixels) and how bright it is at full mana, inside out: fainter than the ki glow. */
	public static final float[] SHELLS = {0.4F, 0.85F, 1.4F};
	public static final float[] SHELL_LIGHT = {0.2F, 0.11F, 0.05F};
	/**
	 * (0.7.235) The resting glow, as a share of the shells' light: a little for anyone attuned, more for every step of
	 * power, and more the fuller their mana. Mana past full (the overload's spill) doesn't brighten it. Andy: the flare
	 * "needs to dim to a dimmer minimum"; "Arcana increases that minimum, so you now glow for others the most dim at your
	 * weakest."
	 */
	static final float REST_BASE = 0.05F, REST_PER_POWER = 0.07F, REST_FULL = 0.25F;
	/** The overload: how much it brightens the glow at its height, and swells it; it eases back down to the rest. */
	static final float FLARE = 2.0F, FLARE_SWELL = 0.1F;
	/** How fast the wisps rise, and the glow's slow breathing. */
	public static final float RISE = 0.01F, BREATHE = 0.1F, BREATHE_SPEED = 0.12F;
	/** (0.7.235) A cast's ripple: how long it runs (ticks), how far it swells, how bright it starts, where its shell sits. */
	static final int RIPPLE_TICKS = 16;
	static final float RIPPLE_SWELL = 0.35F, RIPPLE_LIGHT = 0.55F, RIPPLE_SHELL = 1.0F;
	/** (0.7.235) Intent's colours: harm, healing, warding, will (mana's own blue). */
	static final int[] INTENT = {0xE0484A, 0xF2C45A, 0xE8F0FF, COLOR};
	/** The overload's blue on your screen: the wash over everything and the edges, at its height. */
	static final float WASH = 0.28F, EDGE = 0.6F;
	/** (0.7.235) Mana sickness on your screen: the wash and the edges a stage, and how fast it pulses. */
	static final float SICK_WASH = 0.06F, SICK_EDGE = 0.25F, SICK_PULSE = 0.08F;

	/**
	 * What a being's glow looks like this frame: its strength (share of the shells' light), the overload's flare (0-1), and
	 * a ripple: ticks since the cast ({@code -1} none) and its colour.
	 */
	public record Glow(float strength, float flare, int rippleAge, int rippleColor) {
	}

	private final Shells shells;

	private ManaClient(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.shells = new Shells(this::getParentModel);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new ManaClient(avatar));
			} else if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(folk(renderer));
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("mana_overload"), ManaClient::overload);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Folk<?> folk(RenderLayerParent<?, ?> renderer) {
		return new Folk((RenderLayerParent)renderer);
	}

	/** From the living renderer's extractRenderState: the glow, if this client may see it. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, glow(entity));
		com.jaymoskingdoms.client.spirits.SpiritArms.extract(entity, state);
	}

	private static @Nullable Glow glow(LivingEntity entity) {
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || !Mana.attuned(entity) || entity.isInvisible() || !Mana.attuned(viewer) && !ManaAlchemy.readsMagic(viewer)) {
			return null;
		}
		boolean player = entity instanceof Player;
		int power = entity.getAttachedOrElse(Mana.POWER, player ? 2 : 1);
		// folk who work magic always glow as if full
		float full = player ? Math.min(1.0F, entity.getAttachedOrElse(Mana.GLOW, 0) / (float)Mana.GLOW_STEPS) : 1.0F;
		float flare = entity instanceof Player p ? eased(flare(p, 0.0F)) : 0.0F;
		int age = -1, color = COLOR;
		Long ripple = entity.getAttached(Mana.RIPPLE);
		if (ripple != null) {
			long since = entity.level().getGameTime() - ripple / 4L;
			if (since >= 0L && since < RIPPLE_TICKS) {
				age = (int)since;
				color = INTENT[(int)Math.floorMod(ripple, 4L)];
			}
		}
		return new Glow(REST_BASE + REST_PER_POWER * power + REST_FULL * full + FLARE * flare, flare, age, color);
	}

	/** The overload eased: it hits hard and drains away slowly. */
	static float eased(float k) {
		return k * k * (3.0F - 2.0F * k);
	}

	/** How much of the overload is left on {@code player} (1 just attuned, 0 settled). */
	static float flare(Player player, float partialTicks) {
		Long at = player.getAttached(Mana.OVERLOADED);
		if (at == null) {
			return 0.0F;
		}
		float since = player.level().getGameTime() - at + partialTicks;
		return since < 0.0F || since >= Mana.OVERLOAD_TICKS ? 0.0F : 1.0F - since / Mana.OVERLOAD_TICKS;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		this.shells.submit(poseStack, collector, lightCoords, state);
	}

	/** (0.7.235) The glow on anyone else humanoid: the casting folk (MCA's villagers, our own casters). */
	static final class Folk<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final Shells shells;

		Folk(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
			this.shells = new Shells(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			this.shells.submit(poseStack, collector, lightCoords, state);
		}
	}

	/** The glow's shells over one renderer's model, and the ripple's. */
	static final class Shells {
		private final Supplier<HumanoidModel<?>> wearer;
		private final HumanoidModel<?>[] models = new HumanoidModel<?>[SHELLS.length + 1];

		Shells(Supplier<HumanoidModel<?>> wearer) {
			this.wearer = wearer;
		}

		private HumanoidModel<?> shell(int i) {
			if (this.models[i] == null) {
				float out = i < SHELLS.length ? SHELLS[i] : RIPPLE_SHELL;
				this.models[i] = new WornModel(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(out), 0.0F), 64, 64).bakeRoot(), this.wearer);
			}
			return this.models[i];
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidRenderState state) {
			Glow glow = state.getData(KEY);
			if (glow == null || state.isInvisible) {
				return;
			}
			float breathe = 1.0F + BREATHE * Mth.sin(state.ageInTicks * BREATHE_SPEED);
			float rise = (state.ageInTicks * RISE) % 1.0F;
			float swell = 1.0F + FLARE_SWELL * glow.flare();
			poseStack.pushPose();
			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.scale(swell, swell, swell);
			poseStack.translate(0.0F, -1.5F, 0.0F);
			for (int i = 0; i < SHELLS.length; i++) {
				float light = Mth.clamp(SHELL_LIGHT[i] * glow.strength() * breathe, 0.0F, 1.0F);
				HumanoidModel model = this.shell(i);
				collector.order(2 + i).submitModel(model, state, poseStack, RenderTypes.energySwirl(TEXTURE, i * 0.29F, -rise * (1 + i)), lightCoords,
					OverlayTexture.NO_OVERLAY, tint(COLOR, light), null, 0);
			}
			poseStack.popPose();
			if (glow.rippleAge() >= 0) {
				// (0.7.235) a cast: a ring of light in the intent's colour, swelling out through the glow and fading
				float k = Mth.clamp((glow.rippleAge() + Mth.frac(state.ageInTicks)) / RIPPLE_TICKS, 0.0F, 1.0F);
				float out = 1.0F + RIPPLE_SWELL * k;
				float fade = (1.0F - k) * (1.0F - k);
				poseStack.pushPose();
				poseStack.translate(0.0F, 1.5F, 0.0F);
				poseStack.scale(out, out, out);
				poseStack.translate(0.0F, -1.5F, 0.0F);
				HumanoidModel model = this.shell(SHELLS.length);
				collector.order(2 + SHELLS.length).submitModel(model, state, poseStack, RenderTypes.energySwirl(TEXTURE, 0.5F, -rise * 4.0F - k),
					lightCoords, OverlayTexture.NO_OVERLAY, tint(glow.rippleColor(), RIPPLE_LIGHT * fade), null, 0);
				poseStack.popPose();
			}
		}
	}

	/** {@code color} dimmed to {@code light} (the shells are added like light, so dimmer is fainter). */
	public static int tint(int color, float light) {
		float l = Mth.clamp(light, 0.0F, 1.0F);
		return ARGB.color(255, (int)(ARGB.red(color) * l), (int)(ARGB.green(color) * l), (int)(ARGB.blue(color) * l));
	}

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	/** The overload on your own screen: everything washed blue, and blue round the edges, settling back. And mana sickness. */
	private static void overload(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int sick = ManaSickness.stage(player);
		if (sick > 0) {
			// (0.7.235) sickly grey-green, pulsing
			float pulse = 0.75F + 0.25F * Mth.sin((player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * SICK_PULSE);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(SICK_WASH * sick * pulse, 0.45F, 0.55F, 0.4F));
			float e = Math.min(1.0F, SICK_EDGE * sick * pulse);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
				graphics.guiHeight(), ARGB.colorFromFloat(1.0F, e, e * 0.8F, e));
		}
		float k = flare(player, delta.getGameTimeDeltaPartialTick(false));
		if (k <= 0.0F) {
			return;
		}
		k = eased(k);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(WASH * k, 0.25F, 0.5F, 1.0F));
		float e = EDGE * k;
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), ARGB.colorFromFloat(1.0F, e, e, 0.15F * e));
	}
}
