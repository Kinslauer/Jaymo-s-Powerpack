package com.jaymospowers.client.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.ember.AvatarMoves;
import com.jaymospowers.power.ember.FlameAvatar;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.15.151) The Flame Avatar's client half ({@link FlameAvatar}).
 * <ul>
 * <li><b>Keys:</b> R, G and B are the avatar's with no power; beside a power, sneak + the key (a press that starts
 * sneaking goes to the avatar and stays theirs until let go; the power never hears it).</li>
 * <li><b>HUD:</b> right of the hotbar in Wind's chip style (above the power's own chips when there is one): the flame
 * bar, then R G B with their cooldowns.</li>
 * <li><b>The look, for everyone:</b> the body burnt to char ({@link #CHAR}, over the skin), lava veins glowing through
 * it in the dark ({@link #VEINS}), and the whole body wreathed in vanilla's own flames (not round your own
 * first-person camera, not in water). Embers and smoke rise off them; with the flame bar low they gutter (fewer
 * flames, more smoke).</li>
 * </ul>
 * Textures from {@code tools/gen_flame_avatar.py}.
 */
public final class FlameAvatarClient {
	/** On a player's render state: present for an avatar (their flame, 0-1). */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:flame_avatar");
	static final Identifier CHAR = JaymosPowers.id("textures/entity/ember/avatar_char.png");
	static final Identifier VEINS = JaymosPowers.id("textures/entity/ember/avatar_veins.png");
	/** The veins' brightest glow (the full flame), and how dim they get as it gutters (share). */
	static final float VEIN_LOW = 0.35F;
	/** An ember off the body in this many ticks (full flame; guttering, it's rarer), and smoke with one in this many. */
	static final int EMBER_EVERY = 3, SMOKE_ONE_IN = 4;
	private static final int CHIP = 20, CHIP_GAP = 2;
	static final int COLOR = 0xFF8A1E, LOW_COLOR = 0xB03A10;

	/** R, G and B: the keys the avatar takes ({@link PowerKey} ordinals). */
	private static final int[] KEYS = {AvatarMoves.KEY_R, AvatarMoves.KEY_G, AvatarMoves.KEY_B};
	/** Which of the four ability keys the avatar holds now, and which were physically down last tick. */
	private static final boolean[] HELD = new boolean[4], WAS = new boolean[4];

	private FlameAvatarClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("flame_avatar"), FlameAvatarClient::renderHud);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Look(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(FlameAvatarClient::embers);
	}

	// ---- keys ----------------------------------------------------------------------------------------------------------

	/**
	 * From the power keys' tick, before they're reported: {@code physical} is whether {@code key}'s mapping is down (no
	 * screen, not seized), {@code powerHeld} whether the power already holds it. Returns whether the avatar holds it
	 * now (then the power is told it's up).
	 */
	public static boolean route(LocalPlayer player, PowerKey key, boolean physical, boolean powerHeld) {
		int i = key.ordinal();
		boolean mine = i == AvatarMoves.KEY_R || i == AvatarMoves.KEY_G || i == AvatarMoves.KEY_B;
		boolean pressed = physical && !WAS[i];
		WAS[i] = physical;
		if (HELD[i]) {
			if (!physical || !FlameAvatar.is(player)) {
				HELD[i] = false;
				send(i, false);
			}
			return HELD[i];
		}
		if (mine && pressed && !powerHeld && FlameAvatar.is(player) && !player.isSpectator()
			&& (Powers.of(player) == null || player.isShiftKeyDown())) {
			HELD[i] = true;
			send(i, true);
		}
		return HELD[i];
	}

	private static void send(int key, boolean down) {
		if (ClientPlayNetworking.canSend(FlameAvatar.Key.TYPE)) {
			ClientPlayNetworking.send(new FlameAvatar.Key(key, down));
		}
	}

	// ---- HUD -----------------------------------------------------------------------------------------------------------

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !FlameAvatar.is(player)) {
			return;
		}
		Font font = minecraft.font;
		Power power = Powers.of(player);
		int total = CHIP * 3 + CHIP_GAP * 2;
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + CHIP * 4 + CHIP_GAP * 3 > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		if (power != null) {
			// above the power's own chips
			y -= CHIP + 14;
		}
		float flame = FlameAvatar.flame(player);
		int color = flame < 0.25F ? LOW_COLOR : COLOR;
		int barY = y - 6;
		graphics.fill(x - 1, barY - 1, x + total + 1, barY + 4, 0x90000000);
		graphics.fill(x, barY, x + Mth.ceil(total * Mth.clamp(flame, 0.0F, 1.0F)), barY + 3, 0xFF000000 | color);
		String amount = Integer.toString(Mth.ceil(flame * FlameAvatar.MAX_FLAME));
		Component label = Component.translatable("hud.jaymospowers.avatar.flame").append(" " + amount);
		graphics.text(font, label.copy().withColor(color), x, barY - 10, 0xFFFFFFFF);
		long now = player.level().getGameTime();
		long[] cd = player.getAttached(AvatarMoves.COOLDOWNS);
		Item[] icons = {Items.FIRE_CHARGE, Items.BLAZE_POWDER, Items.FLINT_AND_STEEL};
		for (int i = 0; i < 3; i++) {
			int move = i;
			chip(graphics, font, x + (CHIP + CHIP_GAP) * i, y, PowerKey.byIndex(KEYS[i]), icons[i],
				cd == null ? 0 : cd[move] - now, cd == null ? 1 : (int)cd[move + 3], power != null);
		}
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, long left, int length, boolean sneak) {
		String label = font.plainSubstrByWidth((sneak ? "⇧" : "") + JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, length)));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	// ---- the look ------------------------------------------------------------------------------------------------------

	/** From every living renderer's extractRenderState: an avatar's flame, and the body wreathed in fire. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		if (!(entity instanceof Player player) || !FlameAvatar.is(player)) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, FlameAvatar.flame(player));
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownCamera = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
		if (!ownCamera && !player.isInWater() && !player.isInvisible() && FlameAvatar.flame(player) > 0.0F) {
			state.displayFireAnimation = true;
		}
	}

	/** The char over the skin and the veins glowing through it. */
	static final class Look extends RenderLayer<AvatarRenderState, PlayerModel> {
		Look(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Float flame = state.getData(KEY);
			if (flame == null || state.isInvisible || BodyCover.body(state)) {
				return;
			}
			PlayerModel model = this.getParentModel();
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(CHAR), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
			// the veins dim as the flame gutters
			float glow = VEIN_LOW + (1.0F - VEIN_LOW) * Mth.clamp(flame, 0.0F, 1.0F);
			int shade = (int)(glow * 255.0F);
			int color = 0xFF000000 | shade << 16 | shade << 8 | shade;
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(VEINS), lightCoords, OverlayTexture.NO_OVERLAY, color, null,
				state.outlineColor);
		}
	}

	/** Every client tick: embers and smoke rising off each avatar (fewer embers, more smoke as the flame gutters). */
	private static void embers(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (!FlameAvatar.is(player) || player.isInvisible() || player.isSpectator()
				|| player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			RandomSource random = player.getRandom();
			float flame = FlameAvatar.flame(player);
			int every = flame > 0.5F ? EMBER_EVERY : flame > 0.0F ? EMBER_EVERY * 2 : EMBER_EVERY * 6;
			if (random.nextInt(every) != 0) {
				continue;
			}
			double x = player.getX() + (random.nextDouble() - 0.5) * player.getBbWidth();
			double y = player.getY() + random.nextDouble() * player.getBbHeight();
			double z = player.getZ() + (random.nextDouble() - 0.5) * player.getBbWidth();
			if (player.isInWaterOrRain()) {
				level.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.05, 0.0);
				continue;
			}
			level.addParticle(flame > 0.25F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.04, 0.0);
			if (random.nextInt(SMOKE_ONE_IN) == 0 || flame < 0.25F) {
				level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0.0, 0.05, 0.0);
			}
			if (random.nextInt(20) == 0) {
				level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
			}
		}
	}
}
