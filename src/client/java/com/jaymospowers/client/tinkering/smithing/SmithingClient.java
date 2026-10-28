package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smithing.Forging;
import com.jaymospowers.tinkering.smithing.Grade;
import com.jaymospowers.tinkering.smithing.Grades;
import com.jaymospowers.tinkering.smithing.HotMetal;
import com.jaymospowers.tinkering.smithing.Smithing;
import com.jaymospowers.tinkering.smithing.SmithingAnvilBlock;
import com.jaymospowers.tinkering.smithing.SmithingNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) The hammering game on the smith's screen: the bar under the crosshair (good zone, perfect zone, the arrow
 * sweeping back and forth), judging each blow as the hammer lands and sending it; the forge screen; the grade and heat
 * lines on tooltips; and what lies on anvils and in hearths.
 */
public final class SmithingClient {
	/** The bar: half its width (px), height, and how far under the crosshair. */
	private static final int BAR_HALF = 80, BAR_HEIGHT = 7, BAR_DROP = 22;
	private static final int BACK = 0xA0000000, GOOD_COLOR = 0xFFE0A030, PERFECT_COLOR = 0xFF60E060, ARROW_COLOR = 0xFFFFFFFF,
		MISS_COLOR = 0xFFE04040;
	/** How long a blow's colour flashes on the bar (ticks). */
	private static final int FLASH_TICKS = 8;
	/** Further than this from the anvil and the game is put away (blocks, squared). */
	private static final double AWAY_SQ = 10 * 10;

	private static SmithingNet.@Nullable Session session;
	/** The arrow (0 to 100) now and last tick, and which way it's going. */
	private static float arrow, lastArrow;
	private static int direction = 1;
	private static int flash, flashResult;

	private SmithingClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SmithingNet.OpenForge.TYPE, (payload, context) ->
			Minecraft.getInstance().gui.setScreen(new ForgeScreen(payload.pos(), payload.choices())));
		ClientPlayNetworking.registerGlobalReceiver(SmithingNet.Session.TYPE, (payload, context) -> {
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
			}
		});
		SmithingAnvilBlock.clientBlow = SmithingClient::blow;
		ClientTickEvents.END_CLIENT_TICK.register(SmithingClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("smithing_bar"), SmithingClient::bar);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));
		BlockEntityRenderers.register(Smithing.SMITHING_ANVIL_ENTITY, SmithingAnvilRenderer::new);
		BlockEntityRenderers.register(Smithing.HEARTH_ENTITY, HearthRenderer::new);
		WiderSmithingClient.init();
	}

	private static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		SmithingNet.@Nullable Session s = session;
		LocalPlayer player = minecraft.player;
		if (s == null) {
			return;
		}
		if (player == null || player.blockPosition().distSqr(s.pos()) > AWAY_SQ
			|| !(minecraft.level.getBlockState(s.pos()).getBlock() instanceof SmithingAnvilBlock)) {
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

	/** A hammer swung at an anvil: judge the blow if its game is up here. */
	private static boolean blow(Player player, BlockPos pos) {
		SmithingNet.@Nullable Session s = session;
		if (s == null || !s.pos().equals(pos)) {
			return false;
		}
		int result = Forging.judge(arrow, s.seed(), s.done(), s.difficulty(), s.bonus());
		ClientPlayNetworking.send(new SmithingNet.Hit(pos, result));
		ItemStack hammer = player.getMainHandItem();
		player.getCooldowns().addCooldown(hammer, Forging.BLOW_TICKS);
		flash = FLASH_TICKS;
		flashResult = result;
		// the next blow's zones show at once; the server's answer puts it right if it differs
		session = new SmithingNet.Session(s.pos(), true, s.total(), s.done() + 1, s.difficulty(), s.bonus(), s.seed());
		return true;
	}

	private static void bar(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		SmithingNet.@Nullable Session s = session;
		Minecraft minecraft = Minecraft.getInstance();
		if (s == null || minecraft.player == null || s.done() >= s.total()) {
			return;
		}
		int cx = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 + BAR_DROP;
		int left = cx - BAR_HALF, width = BAR_HALF * 2;
		float centre = Forging.centre(s.seed(), s.done());
		float good = Forging.goodHalf(s.done(), s.difficulty(), s.bonus()), perfect = Forging.perfectHalf(s.done(), s.difficulty(), s.bonus());
		int border = flash > 0 ? (flashResult == Forging.PERFECT ? PERFECT_COLOR : flashResult == Forging.GOOD ? GOOD_COLOR : MISS_COLOR) : BACK;
		graphics.fill(left - 2, y - 2, left + width + 2, y + BAR_HEIGHT + 2, border);
		graphics.fill(left - 1, y - 1, left + width + 1, y + BAR_HEIGHT + 1, BACK);
		graphics.fill(px(left, width, centre - good), y, px(left, width, centre + good), y + BAR_HEIGHT, GOOD_COLOR);
		graphics.fill(px(left, width, centre - perfect), y, px(left, width, centre + perfect), y + BAR_HEIGHT, PERFECT_COLOR);
		float at = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastArrow, arrow);
		int notch = px(left, width, at);
		graphics.fill(notch - 1, y - 3, notch + 1, y + BAR_HEIGHT + 3, ARROW_COLOR);
		String blows = (s.done() + 1) + "/" + s.total();
		graphics.text(minecraft.font, blows, cx - minecraft.font.width(blows) / 2, y + BAR_HEIGHT + 4, 0xFFFFFFFF, true);
	}

	private static int px(int left, int width, float at) {
		return left + Math.round(width * Mth.clamp(at, 0.0F, 100.0F) / 100.0F);
	}

	/** The grade under the name (a Masterwork's name in gold) and how long hot metal stays hot. */
	private static void tooltip(ItemStack stack, java.util.List<Component> lines) {
		Grade grade = Grades.of(stack);
		if (grade != null && !lines.isEmpty()) {
			if (grade == Grade.MASTER) {
				lines.set(0, lines.getFirst().copy().withStyle(ChatFormatting.GOLD));
			}
			lines.add(1, grade.displayName());
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			Component hot = HotMetal.hotLine(stack, minecraft.level.getGameTime());
			if (hot != null) {
				lines.add(Math.min(lines.size(), grade != null ? 2 : 1), hot);
			}
		}
	}
}
