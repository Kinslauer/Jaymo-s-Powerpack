package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.spirits.Move;
import com.jaymoskingdoms.spirits.Poses;
import com.jaymoskingdoms.spirits.Spirit;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * (0.7.236) What a mana spirit does to your own screen. Andy: "the ones with personalities can kick in on their own to assist
 * you at times similar to how the symbiote operates", and the symbiote's rule is that every autonomous act touches the screen:
 * when the server stamps {@link Spirits#FLASH} (an act, a parry, a ward, a warning) the edges flash for {@link #FLASH_TICKS} in
 * the spirit's colour (white for a ward, red for a warning). While spirit sickness is on you, the edge tints with each of the
 * arm's flickers ({@link SpiritArms}). And while the spirit is out, a line above the right end of the hotbar: its name in its
 * colour, the selected move, and its level and bond, the move greyed while it cools down.
 */
public final class SpiritHud {
	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	/** How long a flash lasts (ticks). */
	static final int FLASH_TICKS = 10;
	/** The edge strength of an act, a parry, a ward and a warning; the sickness flicker's. */
	static final float[] FLASH_ALPHA = {0.25F, 0.35F, 0.35F, 0.3F};
	static final float FLICKER_ALPHA = 0.15F;
	/** A ward flashes white, a warning red. */
	static final int WARD_COLOR = 0xFFFFFF, WARN_COLOR = 0xFF2A2A;
	/** The line: pixels above the screen's bottom edge, and in from the hotbar's right end. */
	static final int LIFT = 62, GAP = 8;
	/** The colour of a move that is cooling down, and of the level and bond. */
	static final int GREY = 0x808080, DIM = 0xB0B0B0, READY = 0xFFFFFF;

	private SpiritHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("spirit_flash"), SpiritHud::flash);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("spirit_line"), SpiritHud::line);
	}

	/** The flash of an autonomous act, and the sickness flicker's edge tint. */
	private static void flash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Long stamp = player.getAttached(Spirits.FLASH);
		if (stamp != null) {
			float age = player.level().getGameTime() + partial - (stamp >> 3);
			if (age >= 0.0F && age < FLASH_TICKS) {
				int kind = (int)(stamp & 7L);
				int color = switch (kind) {
					case Spirits.FLASH_WARD -> WARD_COLOR;
					case Spirits.FLASH_WARN -> WARN_COLOR;
					default -> {
						SpiritState state = Spirits.state(player);
						yield Spirits.of(state == null ? Spirits.GRIMSBY : state.kind()).color();
					}
				};
				float alpha = FLASH_ALPHA[Math.clamp(kind, 0, FLASH_ALPHA.length - 1)] * (1.0F - age / FLASH_TICKS);
				vignette(graphics, color, alpha);
			}
		}
		int flicker = SpiritArms.flickering();
		if (flicker >= 0) {
			vignette(graphics, flicker, FLICKER_ALPHA);
		}
	}

	/** The screen's edges tinted {@code color} at {@code alpha} (the vignette is multiplied by the colour, so dimmer is fainter). */
	private static void vignette(GuiGraphicsExtractor graphics, int color, float alpha) {
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), ARGB.colorFromFloat(1.0F, alpha * ARGB.red(color) / 255.0F, alpha * ARGB.green(color) / 255.0F,
				alpha * ARGB.blue(color) / 255.0F));
	}

	/** The toggles the line marks "on": the move ids of Ambrose's steady hands, the shared armour and the Quiet One's echo strike. */
	static final String STEADY = "steady", ARMOUR = "armour", ECHO = "echo";
	/** How long after the armour pose was stamped the armour still counts as on (the server re-stamps it every 15 ticks while it lasts). */
	static final int ARMOUR_SEEN_TICKS = 22;

	/**
	 * Whether {@code move} is a toggle that is on now. Only the armour can be told from what the server syncs: it re-stamps
	 * {@link Poses#ARMOUR} into the spirit's state every 15 ticks while it lasts. Steady hands and the echo strike live in
	 * server-side sets (Ambrose.STEADY, QuietOne.ECHOING) that nothing syncs, so they read off until the server syncs a bit
	 * for them; this is the one place to read it.
	 */
	private static boolean toggleOn(Move move, SpiritState state, long now) {
		return switch (move.id()) {
			case ARMOUR -> state.pose() == Poses.ARMOUR && now - state.shownAt() <= ARMOUR_SEEN_TICKS;
			case STEADY, ECHO -> false;
			default -> false;
		};
	}

	/** The line above the hotbar's right end: name, move, level and bond. The clash tug bar (another track) is its own element beside this. */
	private static void line(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		SpiritState state = player == null ? null : Spirits.state(player);
		if (state == null || !state.out()) {
			return;
		}
		Font font = minecraft.font;
		Spirit spirit = Spirits.of(state.kind());
		Move move = Spirits.selected(state);
		boolean cooling = move != null && SpiritKey.wait(move, player.level().getGameTime()) > 0L;
		Component name = Component.translatable("spirit.jaymoskingdoms." + spirit.id() + ".name").withColor(spirit.color() | 0xFF000000);
		Component moveName = move == null ? Component.translatable("gui.jaymoskingdoms.spirit.no_move")
			: Component.translatable("spirit.jaymoskingdoms.move." + move.id());
		Component stats = Component.translatable("gui.jaymoskingdoms.spirit.stats", state.level(), state.bond());
		int moveColor = cooling ? GREY : READY;
		if (move != null && toggleOn(move, state, player.level().getGameTime())) {
			moveName = Component.translatable("gui.jaymoskingdoms.spirit.toggle_on", moveName);
		}
		int width = font.width(name) + GAP + font.width(moveName) + GAP + font.width(stats);
		int x = graphics.guiWidth() / 2 + 91 - width;
		int y = graphics.guiHeight() - LIFT;
		graphics.text(font, name, x, y, 0xFFFFFFFF, true);
		x += font.width(name) + GAP;
		graphics.text(font, moveName, x, y, ARGB.opaque(moveColor), true);
		x += font.width(moveName) + GAP;
		graphics.text(font, stats, x, y, ARGB.opaque(DIM), true);
	}
}
