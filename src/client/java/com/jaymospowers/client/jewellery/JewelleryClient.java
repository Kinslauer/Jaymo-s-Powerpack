package com.jaymospowers.client.jewellery;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.client.tinkering.smithing.TimingBar;
import com.jaymospowers.jewellery.GemCutting;
import com.jaymospowers.jewellery.Jewellery;
import com.jaymospowers.jewellery.JewelleryNet;
import com.jaymospowers.jewellery.LapidaryWheelBlock;
import com.jaymospowers.tinkering.smithing.Forging;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) Jewellery on the client: the Jeweller's Bench screen, the cut screen, the lapidary wheel's gem, and the
 * cutting game's bar under the crosshair (the anvil's bar, dimmed while the wheel spins back up).
 */
public final class JewelleryClient {
	private static final int BAR_HALF = 80, BAR_HEIGHT = 7;
	private static final int DIM = 0x90000000, SPIN_LINE = 0xFFB0B0C0, CHIP_COLOR = 0xFFB060FF, MISS_COLOR = 0xFFE04040;
	/** How long a stroke's colour flashes on the bar (ticks). */
	private static final int FLASH_TICKS = 8;
	/** Further than this from the wheel and the game is put away (blocks, squared). */
	private static final double AWAY_SQ = 10 * 10;

	private static JewelleryNet.@Nullable CutSession session;
	private static float arrow, lastArrow;
	private static int direction = 1;
	/** Ticks until the wheel has spun back up (a stroke before 0 chips the gem). */
	private static int settle;
	private static int flash, flashResult;

	private JewelleryClient() {
	}

	public static void init() {
		SocketTooltip.init(); // (0.20.7)
		MenuScreens.register(Jewellery.BENCH_MENU, JewellersBenchScreen::new);
		BlockEntityRenderers.register(Jewellery.LAPIDARY_WHEEL_ENTITY, LapidaryWheelRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(JewelleryNet.OpenCuts.TYPE, (payload, context) ->
			Minecraft.getInstance().gui.setScreen(new CutScreen(payload.pos(), payload.gem(), payload.cuts())));
		ClientPlayNetworking.registerGlobalReceiver(JewelleryNet.CutSession.TYPE, (payload, context) -> {
			if (!payload.active()) {
				if (session != null && session.pos().equals(payload.pos())) {
					session = null;
				}
				return;
			}
			boolean fresh = session == null || !session.pos().equals(payload.pos());
			session = payload;
			if (fresh) {
				arrow = lastArrow = 0.0F;
				direction = 1;
				settle = 0;
			}
			if (payload.last() >= 0) {
				flash = FLASH_TICKS;
				flashResult = payload.last();
			}
		});
		LapidaryWheelBlock.clientStroke = JewelleryClient::stroke;
		ClientTickEvents.END_CLIENT_TICK.register(JewelleryClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("gem_cutting_bar"), JewelleryClient::bar);
	}

	private static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		if (settle > 0) {
			settle--;
		}
		JewelleryNet.@Nullable CutSession s = session;
		LocalPlayer player = minecraft.player;
		if (s == null) {
			return;
		}
		if (player == null || minecraft.level == null || player.blockPosition().distSqr(s.pos()) > AWAY_SQ
			|| !(minecraft.level.getBlockState(s.pos()).getBlock() instanceof LapidaryWheelBlock)) {
			session = null;
			return;
		}
		lastArrow = arrow;
		arrow += direction * Forging.speed(s.done(), s.difficulty());
		if (arrow >= 100.0F) {
			arrow = 200.0F - arrow;
			direction = -1;
		} else if (arrow <= 0.0F) {
			arrow = -arrow;
			direction = 1;
		}
	}

	/** The wheel was used while its game is up here: judge the stroke and send it. True when the game is up. */
	private static boolean stroke(Player player, BlockPos pos) {
		JewelleryNet.@Nullable CutSession s = session;
		if (s == null || !s.pos().equals(pos) || s.done() >= s.total()) {
			return false;
		}
		// the server drops strokes this close together, so don't count them here
		if (GemCutting.SETTLE_TICKS - settle < GemCutting.MIN_STROKE_GAP && settle > 0) {
			return true;
		}
		int result = settle > GemCutting.SETTLE_GRACE ? GemCutting.CHIP
			: Forging.judge(arrow, s.seed(), s.done(), s.difficulty(), s.bonus());
		ClientPlayNetworking.send(new JewelleryNet.Stroke(pos, arrow));
		flash = FLASH_TICKS;
		flashResult = result;
		settle = GemCutting.SETTLE_TICKS;
		// the next stroke's zones show at once; the server's answer puts it right if it differs
		session = new JewelleryNet.CutSession(s.pos(), true, s.total(), s.done() + 1, s.difficulty(), s.bonus(), s.seed(),
			s.chips() + (result == GemCutting.CHIP ? 1 : 0), -1);
		return true;
	}

	private static void bar(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		JewelleryNet.@Nullable CutSession s = session;
		Minecraft minecraft = Minecraft.getInstance();
		if (s == null || minecraft.player == null || s.done() >= s.total()) {
			return;
		}
		int[] slot = HudZones.place(HudZones.BELOW, "jaymospowers:gem_cutting", 50, BAR_HALF * 2 + 4, BAR_HEIGHT + 6 + 4 + 9);
		if (slot == null) {
			return;
		}
		int cx = slot[0] + BAR_HALF + 2, y = slot[1] + 3;
		int left = cx - BAR_HALF, width = BAR_HALF * 2;
		float centre = Forging.centre(s.seed(), s.done());
		float good = Forging.goodHalf(s.done(), s.difficulty(), s.bonus()), perfect = Forging.perfectHalf(s.done(), s.difficulty(), s.bonus());
		int border = TimingBar.BACK;
		if (flash > 0) {
			border = switch (flashResult) {
				case Forging.PERFECT -> TimingBar.PERFECT_COLOR;
				case Forging.GOOD -> TimingBar.GOOD_COLOR;
				case GemCutting.CHIP -> CHIP_COLOR;
				default -> MISS_COLOR;
			};
		}
		float at = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastArrow, arrow);
		TimingBar.draw(graphics, left, y, BAR_HALF, BAR_HEIGHT, centre, good, perfect, at, border);
		if (settle > 0) {
			graphics.fill(left, y, left + width, y + BAR_HEIGHT, DIM);
			float spun = 1.0F - (settle - delta.getGameTimeDeltaPartialTick(false)) / GemCutting.SETTLE_TICKS;
			graphics.fill(left, y + BAR_HEIGHT - 1, left + Math.round(width * Mth.clamp(spun, 0.0F, 1.0F)), y + BAR_HEIGHT, SPIN_LINE);
		}
		String count = (s.done() + 1) + "/" + s.total();
		graphics.text(minecraft.font, count, cx - minecraft.font.width(count) / 2, y + BAR_HEIGHT + 4, 0xFFFFFFFF, true);
		if (s.chips() > 0) {
			String pips = "*".repeat(Math.min(s.chips(), 6));
			graphics.text(minecraft.font, pips, cx + minecraft.font.width(count) / 2 + 4, y + BAR_HEIGHT + 4, CHIP_COLOR, true);
		}
	}
}
