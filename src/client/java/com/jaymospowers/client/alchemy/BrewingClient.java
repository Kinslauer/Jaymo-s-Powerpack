package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.brewing.BrewQuality;
import com.jaymospowers.alchemy.brewing.Brewing;
import com.jaymospowers.alchemy.brewing.BrewingNet;
import com.jaymospowers.alchemy.brewing.MortarBlock;
import com.jaymospowers.alchemy.brewing.MortarEntity;
import com.jaymospowers.alchemy.cauldron.CauldronStirring;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.client.tinkering.smithing.TimingBar;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) Brewing on the client: the grinding and stirring bars under the crosshair (the anvil's bar, one at a time),
 * the mortar's batch, and the tooltips for grades and ground ingredients.
 */
public final class BrewingClient {
	private static final int BAR_HALF = 80, BAR_HEIGHT = 7;
	private static final int MISS_COLOR = 0xFFE04040;
	/** How long a stroke's colour flashes on the bar (ticks). */
	private static final int FLASH_TICKS = 8;
	/** Further than this from the block and the game is put away (blocks, squared). */
	private static final double AWAY_SQ = 10 * 10;

	private static BrewingNet.@Nullable BrewBar session;
	private static float arrow, lastArrow;
	private static int direction = 1;
	private static int flash, flashResult;
	/** Ticks until another stroke counts (the server drops ones closer together). */
	/** Extra ticks over the server's minimum, so jitter does not get a stroke dropped. */
	private static final int CLIENT_GAP_MARGIN = 2;
	private static int gap;

	private BrewingClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BrewingNet.BrewBar.TYPE, (payload, context) -> {
			if (!payload.active()) {
				if (session != null && session.pos().equals(payload.pos())) {
					session = null;
				}
				return;
			}
			boolean fresh = session == null || !session.pos().equals(payload.pos()) || session.game() != payload.game();
			session = payload;
			if (fresh) {
				arrow = lastArrow = 0.0F;
				direction = 1;
				gap = 0;
			}
			if (payload.last() >= 0) {
				flash = FLASH_TICKS;
				flashResult = payload.last();
			}
		});
		MortarBlock.clientStroke = (player, pos) -> stroke(player, pos, BrewQuality.GRIND_GAME);
		CauldronStirring.clientStir = (player, pos) -> stroke(player, pos, BrewQuality.STIR_GAME);
		ClientTickEvents.END_CLIENT_TICK.register(BrewingClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("brewing_bar"), BrewingClient::bar);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));
		BlockEntityRenderers.register(Brewing.MORTAR_ENTITY, MortarRenderer::new);
	}

	private static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		if (gap > 0) {
			gap--;
		}
		BrewingNet.@Nullable BrewBar s = session;
		LocalPlayer player = minecraft.player;
		if (s == null) {
			return;
		}
		if (player == null || minecraft.level == null || player.blockPosition().distSqr(s.pos()) > AWAY_SQ
			|| !(s.game() == BrewQuality.GRIND_GAME ? minecraft.level.getBlockState(s.pos()).getBlock() instanceof MortarBlock
				: minecraft.level.getBlockState(s.pos()).is(Blocks.WATER_CAULDRON))) {
			session = null;
			return;
		}
		lastArrow = arrow;
		arrow += direction * BrewQuality.speed(BrewQuality.bar(s.game()), s.done());
		if (arrow >= 100.0F) {
			arrow = 200.0F - arrow;
			direction = -1;
		} else if (arrow <= 0.0F) {
			arrow = -arrow;
			direction = 1;
		}
	}

	/** The block was used while this game is up here: judge the stroke and send it. True when the game is up. */
	private static boolean stroke(Player player, BlockPos pos, int game) {
		BrewingNet.@Nullable BrewBar s = session;
		if (s == null || !s.pos().equals(pos) || s.game() != game || s.done() >= s.total()) {
			return false;
		}
		// the server drops strokes this close together, so don't count them here
		if (gap > 0) {
			return true;
		}
		gap = (game == BrewQuality.GRIND_GAME ? MortarEntity.MIN_STROKE_GAP : CauldronStirring.MIN_STROKE_GAP) + CLIENT_GAP_MARGIN;
		int result = BrewQuality.judge(BrewQuality.bar(game), arrow, s.seed(), s.done(), s.bonus());
		ClientPlayNetworking.send(new BrewingNet.BrewStroke(pos, game, arrow));
		flash = FLASH_TICKS;
		flashResult = result;
		// the next stroke's zones show at once; the server's answer puts it right if it differs
		session = new BrewingNet.BrewBar(pos, true, game, s.total(), s.done() + 1, s.bonus(), s.seed(), -1);
		return true;
	}

	private static void bar(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		BrewingNet.@Nullable BrewBar s = session;
		Minecraft minecraft = Minecraft.getInstance();
		if (s == null || minecraft.player == null || s.done() >= s.total()) {
			return;
		}
		int[] slot = HudZones.place(HudZones.BELOW, "jaymospowers:brewing", 50, BAR_HALF * 2 + 4, BAR_HEIGHT + 6 + 4 + 9);
		if (slot == null) {
			return;
		}
		int cx = slot[0] + BAR_HALF + 2, y = slot[1] + 3;
		int left = cx - BAR_HALF;
		BrewQuality.Bar bar = BrewQuality.bar(s.game());
		float centre = BrewQuality.centre(bar, s.seed(), s.done());
		float good = BrewQuality.goodHalf(bar, s.done(), s.bonus()), perfect = BrewQuality.perfectHalf(bar, s.done(), s.bonus());
		int border = TimingBar.BACK;
		if (flash > 0) {
			border = switch (flashResult) {
				case BrewQuality.PERFECT -> TimingBar.PERFECT_COLOR;
				case BrewQuality.GOOD -> TimingBar.GOOD_COLOR;
				default -> MISS_COLOR;
			};
		}
		float at = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastArrow, arrow);
		TimingBar.draw(graphics, left, y, BAR_HALF, BAR_HEIGHT, centre, good, perfect, at, border);
		Component label = Component.translatable(s.game() == BrewQuality.GRIND_GAME ? "hud.jaymospowers.brewing.grind"
			: "hud.jaymospowers.brewing.stir", s.done() + 1, s.total());
		graphics.text(minecraft.font, label, cx - minecraft.font.width(label) / 2, y + BAR_HEIGHT + 4, 0xFFFFFFFF, true);
	}

	/** The grade under the name (Masterwork's name in gold), "Distilled", and how finely an ingredient is ground. */
	private static void tooltip(ItemStack stack, List<Component> lines) {
		if (lines.isEmpty()) {
			return;
		}
		BrewGrade grade = BrewGrades.of(stack);
		if (grade != null) {
			if (grade == BrewGrade.MASTERWORK) {
				lines.set(0, lines.getFirst().copy().withStyle(ChatFormatting.GOLD));
			}
			lines.add(Math.min(1, lines.size()), grade.displayName());
			if (BrewGrades.distilled(stack)) {
				lines.add(Math.min(2, lines.size()), Component.translatable("tooltip.jaymospowers.brew.distilled").withStyle(ChatFormatting.GRAY));
			}
		}
		int ground = BrewGrades.ground(stack);
		if (ground > 0) {
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.ground." + Mth.clamp(ground, 1, 3)).withStyle(ChatFormatting.GRAY));
		}
	}
}
