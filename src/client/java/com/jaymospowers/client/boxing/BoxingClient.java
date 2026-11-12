package com.jaymospowers.client.boxing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.boxing.Boxing;
import com.jaymospowers.boxing.BoxingEvades;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.stamina.Feathers;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) Boxing on this side ({@link Boxing}, {@link BoxingEvades}).
 * <ul>
 * <li><b>The evades</b>: while boxing, the Dodge key calls {@link #evade} in place of the roll
 * ({@code stamina/DodgeClient}). The boxer is moved here, at once, and the server is told which way was held.</li>
 * <li><b>The blur</b> a strong attack leaves ({@link BoxingEvades#BLUR}): a pale haze over the world, drawn under the
 * whole HUD so the crosshair, the incoming-blow cues and the bars stay sharp. It fades out over its last
 * {@link #FADE_TICKS}. Players who get motion sick can swap it for a dark frame round the edges (the "Boxing: blur or
 * vignette" key, unbound by default; the choice is kept in {@code config/jaymospowers-boxing.properties}).</li>
 * </ul>
 */
public final class BoxingClient {
	// ---- tuning ----
	/** The haze: its colour, and how opaque at full (0-255). */
	static final int HAZE_COLOR = 0xD4D6DC, HAZE_ALPHA = 150;
	/** The vignette: its colour, how opaque at the very edge (0-255), and how far in it reaches (a share of the screen's height). */
	static final int VIGNETTE_COLOR = 0x000000, VIGNETTE_ALPHA = 215;
	static final float VIGNETTE_REACH = 0.38F;
	/** The steps the vignette's sides are drawn in. */
	static final int VIGNETTE_BANDS = 10;
	/** The blur fades out over its last ticks. */
	static final float FADE_TICKS = 10.0F;

	/** The style and level line: its colour, and its gap from the screen's corner (pixels). */
	static final int STYLE_COLOR = 0xFFE8E2D0, STYLE_MARGIN = 4;

	private static final String FILE = "jaymospowers-boxing.properties";
	private static final String VIGNETTE = "vignette";

	private static @Nullable KeyMapping blurKey;
	/** A dark frame in place of the haze. */
	private static boolean vignette;

	private BoxingClient() {
	}

	public static void init() {
		vignette = Boolean.parseBoolean(load().getProperty(VIGNETTE, "false"));
		blurKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.boxing_blur", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			KeyMapping key = blurKey;
			boolean pressed = false;
			while (key != null && key.consumeClick()) {
				pressed = true;
			}
			if (pressed) {
				vignette = !vignette;
				Properties state = load();
				state.setProperty(VIGNETTE, Boolean.toString(vignette));
				save(state);
			}
		});
		HudElementRegistry.addFirst(JaymosPowers.id("boxing_blur"), BoxingClient::draw);
		HudElementRegistry.addLast(JaymosPowers.id("boxing_style"), BoxingClient::drawStyle);
	}

	/**
	 * The Dodge key, while boxing: the style's evade. Moves the boxer and tells the server; false if there's no style or
	 * no stamina for it.
	 */
	public static boolean evade(LocalPlayer player) {
		Boxing.Style style = Boxing.style(player);
		if (style == null || !Feathers.canSpend(player, 1) || !ClientPlayNetworking.canSend(BoxingEvades.Evade.TYPE)) {
			return false;
		}
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		int direction = strafe > 0.0F ? BoxingEvades.LEFT : strafe < 0.0F ? BoxingEvades.RIGHT
			: forward > 0.0F ? BoxingEvades.FORWARD : forward < 0.0F ? BoxingEvades.BACK : BoxingEvades.NONE;
		// the player's facing on the ground, and the way to their left (as DodgeClient turns the movement keys)
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		Vec3 ahead = new Vec3(-sin, 0.0, cos);
		Vec3 left = new Vec3(cos, 0.0, sin);
		Vec3 motion = player.getDeltaMovement();
		switch (BoxingEvades.move(style, direction)) {
			case SLIP_LEFT -> player.setDeltaMovement(left.x * BoxingEvades.SLIP_SPEED, motion.y, left.z * BoxingEvades.SLIP_SPEED);
			case SLIP_RIGHT -> player.setDeltaMovement(-left.x * BoxingEvades.SLIP_SPEED, motion.y, -left.z * BoxingEvades.SLIP_SPEED);
			case BOB -> player.setDeltaMovement(ahead.x * BoxingEvades.BOB_SPEED, motion.y, ahead.z * BoxingEvades.BOB_SPEED);
			case PULL -> player.setDeltaMovement(-ahead.x * BoxingEvades.PULL_SPEED, BoxingEvades.PULL_UP, -ahead.z * BoxingEvades.PULL_SPEED);
			case ROLL -> player.setDeltaMovement(0.0, motion.y, 0.0);
		}
		player.setSprinting(false);
		ClientPlayNetworking.send(new BoxingEvades.Evade(direction));
		return true;
	}

	/** While boxing: the style and its level, small, in the bottom right corner. */
	private static void drawStyle(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.options.hideGui || !Boxing.boxing(player)) {
			return;
		}
		Boxing.Style style = Boxing.style(player);
		if (style == null) {
			return;
		}
		Component text = Component.translatable("boxing.jaymospowers.hud", Component.translatable("boxing.jaymospowers.style." + style.id),
			Boxing.level(player, style));
		graphics.text(minecraft.font, text, graphics.guiWidth() - minecraft.font.width(text) - STYLE_MARGIN,
			graphics.guiHeight() - minecraft.font.lineHeight - STYLE_MARGIN, STYLE_COLOR, true);
	}

	/** The blur: a haze over the world, or the vignette. */
	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Long until = player.getAttached(BoxingEvades.BLUR);
		if (until == null) {
			return;
		}
		float left = until - player.level().getGameTime() - delta.getGameTimeDeltaPartialTick(false);
		if (left <= 0.0F) {
			return;
		}
		float strength = Mth.clamp(left / FADE_TICKS, 0.0F, 1.0F);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (!vignette) {
			graphics.fill(0, 0, w, h, Math.round(HAZE_ALPHA * strength) << 24 | HAZE_COLOR);
			return;
		}
		int alpha = Math.round(VIGNETTE_ALPHA * strength);
		if (alpha <= 0) {
			return;
		}
		int edge = Math.round(h * VIGNETTE_REACH);
		int solid = alpha << 24 | VIGNETTE_COLOR;
		graphics.fillGradient(0, 0, w, edge, solid, VIGNETTE_COLOR);
		graphics.fillGradient(0, h - edge, w, h, VIGNETTE_COLOR, solid);
		// the sides, in steps (a gradient only runs top to bottom)
		int band = Mth.ceil(edge / (float)VIGNETTE_BANDS);
		for (int i = 0; i < VIGNETTE_BANDS; i++) {
			int shade = Math.round(alpha * (1.0F - i / (float)VIGNETTE_BANDS)) << 24 | VIGNETTE_COLOR;
			graphics.fill(i * band, 0, (i + 1) * band, h, shade);
			graphics.fill(w - (i + 1) * band, 0, w - i * band, h, shade);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static Properties load() {
		Properties state = new Properties();
		if (Files.exists(path())) {
			try (InputStream in = Files.newInputStream(path())) {
				state.load(in);
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't read {}", path(), e);
			}
		}
		return state;
	}

	private static void save(Properties state) {
		try (OutputStream out = Files.newOutputStream(path())) {
			state.store(out, "Jaymo's Powers: boxing (vignette=true swaps the blur for a dark frame)");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't write {}", path(), e);
		}
	}
}
