package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * glow, and brighter the fuller their mana; it flares while they're overloaded. Drawn only when the one looking is
 * attuned too.
 * <p>
 * And the overload itself, on your own screen: attuned just now, your sight goes blue and settles back over
 * {@link Mana#OVERLOAD_TICKS}.
 */
public final class ManaClient extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player's render state: the glow's strength and the overload's flare (0-1), or null for none. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:mana_glow");
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/mana_glow.png");
	/** Mana's blue. */
	static final int COLOR = 0x5A9CFF;
	/** How far out each shell sits (model pixels) and how bright it is at full mana, inside out: fainter than the ki glow. */
	static final float[] SHELLS = {0.4F, 0.85F, 1.4F};
	static final float[] SHELL_LIGHT = {0.2F, 0.11F, 0.05F};
	/** The overload: how much it brightens the glow, and swells it. */
	static final float FLARE = 1.4F, FLARE_SWELL = 0.1F;
	/** How fast the wisps rise, and the glow's slow breathing. */
	static final float RISE = 0.01F, BREATHE = 0.1F, BREATHE_SPEED = 0.12F;
	/** The overload's blue on your screen: the wash over everything and the edges, at its height. */
	static final float WASH = 0.28F, EDGE = 0.6F;

	private final HumanoidModel<?>[] shells = new HumanoidModel<?>[SHELLS.length];

	private ManaClient(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new ManaClient(avatar));
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("mana_overload"), ManaClient::overload);
	}

	/** From the living renderer's extractRenderState: the glow, if this client may see it. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, entity instanceof Player player ? glow(player) : null);
	}

	private static float @Nullable [] glow(Player player) {
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || !Mana.attuned(viewer) || !Mana.attuned(player) || player.isInvisible()) {
			return null;
		}
		float strength = player.getAttachedOrElse(Mana.GLOW, 0) / (float)Mana.GLOW_STEPS;
		float flare = flare(player, 0.0F);
		if (strength <= 0.0F && flare <= 0.0F) {
			return null;
		}
		return new float[] {strength + FLARE * flare, flare};
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

	private HumanoidModel<?> shell(int i) {
		if (this.shells[i] == null) {
			this.shells[i] = new WornModel(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(SHELLS[i]), 0.0F), 64, 64).bakeRoot(),
				this::getParentModel);
		}
		return this.shells[i];
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		float[] glow = state.getData(KEY);
		if (glow == null || state.isInvisible) {
			return;
		}
		float breathe = 1.0F + BREATHE * Mth.sin(state.ageInTicks * BREATHE_SPEED);
		float rise = (state.ageInTicks * RISE) % 1.0F;
		float swell = 1.0F + FLARE_SWELL * glow[1];
		poseStack.pushPose();
		poseStack.translate(0.0F, 1.5F, 0.0F);
		poseStack.scale(swell, swell, swell);
		poseStack.translate(0.0F, -1.5F, 0.0F);
		for (int i = 0; i < SHELLS.length; i++) {
			float light = Mth.clamp(SHELL_LIGHT[i] * glow[0] * breathe, 0.0F, 1.0F);
			int tint = ARGB.color(255, (int)(ARGB.red(COLOR) * light), (int)(ARGB.green(COLOR) * light), (int)(ARGB.blue(COLOR) * light));
			HumanoidModel model = this.shell(i);
			collector.order(2 + i).submitModel(model, state, poseStack, RenderTypes.energySwirl(TEXTURE, i * 0.29F, -rise * (1 + i)), lightCoords,
				OverlayTexture.NO_OVERLAY, tint, null, 0);
		}
		poseStack.popPose();
	}

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	/** The overload on your own screen: everything washed blue, and blue round the edges, settling back. */
	private static void overload(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float k = flare(player, delta.getGameTimeDeltaPartialTick(false));
		if (k <= 0.0F) {
			return;
		}
		// eased: it hits hard and drains away slowly
		k = k * k * (3.0F - 2.0F * k);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(WASH * k, 0.25F, 0.5F, 1.0F));
		float e = EDGE * k;
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), ARGB.colorFromFloat(1.0F, e, e, 0.15F * e));
	}
}
