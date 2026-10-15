package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.NightGifts;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.66) The client half of {@link NightGifts}: the two keys ({@code O}: Blood Dash or Leap; {@code P}: Groundpound or
 * Howl), the kin-sense compass at the top of the screen, people a groundpound or howl showed glowing through walls,
 * and a vampire's bat wings, spread when it glides without an elytra.
 */
public final class NightGiftsClient {
	/** Outline of someone a groundpound or howl showed: a vampire's (blood) and a werewolf's (amber). */
	static final int REVEAL_BLOOD = 0xFFFF3B30, REVEAL_WOLF = 0xFFFFB020;
	/** The compass's colours and how long it holds without word from the server (ticks). */
	static final int TEXT_BLOOD = 0xFFD43A3A, TEXT_WOLF = 0xFFE8A33A, TEXT_DIM = 0xFF9A9A9A;
	static final int SENSE_STALE = 60;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static KeyMapping dash, pound;
	/** Shown people: entity id → game time the glow ends. */
	private static final Map<Integer, Long> REVEALED = new HashMap<>();
	private static List<Integer> bearings = List.of(), distances = List.of();
	private static int kinCount;
	private static long senseAt = Long.MIN_VALUE;

	private NightGiftsClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("night_gifts"));
		dash = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.night_dash", InputConstants.KEY_O, category));
		pound = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.night_pound", InputConstants.KEY_P, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (dash.consumeClick()) {
				send(minecraft, 0);
			}
			while (pound.consumeClick()) {
				send(minecraft, 1);
			}
			if (minecraft.level != null) {
				long now = minecraft.level.getGameTime();
				REVEALED.values().removeIf(end -> end < now);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(NightGifts.Sense.TYPE, (payload, context) -> {
			bearings = payload.bearings();
			distances = payload.distances();
			kinCount = payload.count();
			senseAt = context.client().level == null ? 0L : context.client().level.getGameTime();
		});
		ClientPlayNetworking.registerGlobalReceiver(NightGifts.Reveal.TYPE, (payload, context) -> {
			if (context.client().level == null) {
				return;
			}
			long end = context.client().level.getGameTime() + payload.ticks();
			for (int id : payload.ids()) {
				REVEALED.put(id, end);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			REVEALED.clear();
			bearings = List.of();
			kinCount = 0;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("kin_sense"), NightGiftsClient::compass);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new BatWings(avatar, new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA))));
			}
		});
	}

	private static void send(Minecraft minecraft, int which) {
		if (minecraft.player != null && minecraft.gui.screen() == null && NightGifts.gift(minecraft.player) != null
			&& ClientPlayNetworking.canSend(NightGifts.Move.TYPE)) {
			ClientPlayNetworking.send(new NightGifts.Move(which));
		}
	}

	/** On a player's render state: their bat wings are spread. */
	public static final RenderStateDataKey<Boolean> WINGS = BatWings.SPREAD;

	/** Called from the render-state mixin. */
	public static Boolean wings(LivingEntity entity) {
		return BatWings.spread(entity);
	}

	/** Whether a groundpound or howl is showing {@code entity} to this player now. */
	public static boolean revealed(Entity entity) {
		return !REVEALED.isEmpty() && REVEALED.containsKey(entity.getId());
	}

	/** The outline colour for someone shown. */
	public static int revealColor() {
		LocalPlayer player = Minecraft.getInstance().player;
		NightGifts.Gift g = player == null ? null : NightGifts.gift(player);
		return g != null && g.wolf() ? REVEAL_WOLF : REVEAL_BLOOD;
	}

	/** The kin-sense compass: top left, an arrow and distance to each of the nearest kin. */
	private static void compass(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		NightGifts.Gift g = NightGifts.gift(player);
		if (g == null || minecraft.level.getGameTime() - senseAt > SENSE_STALE) {
			return;
		}
		Font font = minecraft.font;
		int color = g.wolf() ? TEXT_WOLF : TEXT_BLOOD;
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < bearings.size() && i < distances.size(); i++) {
			float rel = Mth.wrapDegrees(bearings.get(i) - player.getYRot());
			String arrow = ARROWS[Math.floorMod(Math.round(rel / 45.0F), 8)];
			if (!line.isEmpty()) {
				line.append("   ");
			}
			line.append(arrow).append(' ').append(distances.get(i)).append(" m");
		}
		Component label = Component.translatable(g.wolf() ? "hud.jaymoskingdoms.night.pack" : "hud.jaymoskingdoms.night.kin");
		Component text = kinCount == 0 ? Component.translatable("hud.jaymoskingdoms.night.none", label)
			: Component.translatable("hud.jaymoskingdoms.night.sense", label, line.toString(),
				kinCount > bearings.size() ? Component.translatable("hud.jaymoskingdoms.night.more", kinCount - bearings.size()) : Component.empty());
		graphics.text(font, text, 4, 4, kinCount == 0 ? TEXT_DIM : color, true);
		if (g.blood() && g.wings() < NightGifts.WINGS_MAX) {
			// (the wings' strength, under the compass, while they're worn down)
			graphics.text(font, Component.translatable("hud.jaymoskingdoms.night.wings", Math.round(100.0F * g.wings() / NightGifts.WINGS_MAX)), 4, 15,
				TEXT_DIM, true);
		}
	}

	/** A vampire's bat wings: the elytra's shape in leather, spread only while it glides with no elytra on. */
	static final class BatWings extends RenderLayer<AvatarRenderState, PlayerModel> {
		static final RenderStateDataKey<Boolean> SPREAD = RenderStateDataKey.create(() -> "jaymoskingdoms:bat_wings");
		private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/vampire/bat_wings.png");
		/** How much bigger than an elytra. */
		static final float SCALE = 1.35F;
		private final ElytraModel model;

		BatWings(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, ElytraModel model) {
			super(renderer);
			this.model = model;
		}

		/** Called from the render-state mixin. */
		static Boolean spread(LivingEntity entity) {
			return entity instanceof Player p && NightGifts.onBatWings(p) ? Boolean.TRUE : null;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(SPREAD)) || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			poseStack.translate(0.0F, -0.05F, 0.125F);
			poseStack.scale(SCALE, SCALE, SCALE);
			collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
			poseStack.popPose();
		}
	}
}
