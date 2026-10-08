package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.arsenal.ArsenalArmory;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.arsenal.HundredBlades;
import com.jaymospowers.power.arsenal.Summoned;
import com.jaymospowers.power.arsenal.entity.ArsenalEntities;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Pocket Arsenal, client side: the Barrage weapon renderer, the "dissolves in" tooltip line and the HUD. */
public final class ArsenalPowerClient implements PowerClient {
	public static final ArsenalPowerClient INSTANCE = new ArsenalPowerClient();

	private static final int ACCENT = 0xFF9B5CFF;
	private static final int ULTIMATE_ACCENT = 0xFFE8B83A;
	private static final int BAR_W = 46;

	private ArsenalPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(ArsenalPower.ID, INSTANCE);
		EntityRendererRegistry.register(ArsenalEntities.RIFT_BLADE, RiftBladeRenderer::new);
		// Reach Through: the portal and the weapon coming out of it, for everyone in view
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalReachRenderer::render);
		// Hundred Blades: the rifts in the sky
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalSkyRenderer::render);
		// the Void Arsenal's aura, on every corrupted arsenal user in view
		ClientTickEvents.END_CLIENT_TICK.register(ArsenalAura::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Summoned summoned = Summoned.of(stack);
			Minecraft minecraft = Minecraft.getInstance();
			if (summoned == null || minecraft.level == null) {
				return;
			}
			long left = Math.max(0, summoned.expires() - minecraft.level.getGameTime());
			Component line = Component.translatable("tooltip.jaymospowers.arsenal.summoned", seconds(left))
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
			lines.add(Math.min(1, lines.size()), line);
		});
	}

	private static String seconds(long ticks) {
		return Long.toString((ticks + 19) / 20);
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Font font = minecraft.font;
		long now = minecraft.level.getGameTime();
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);

		// to the right of the hotbar, clear of the core's badge (top-left)
		int x = graphics.guiWidth() / 2 + 98;
		int y = graphics.guiHeight() - 34;

		row(graphics, font, x, y, PowerKey.ABILITY_1, "hud.jaymospowers.arsenal.reach", cd.reach() - now, ArsenalPower.REACH_COOLDOWN);
		row(graphics, font, x, y + 11, PowerKey.ABILITY_3, "hud.jaymospowers.arsenal.barrage", cd.barrage() - now, ArsenalPower.BARRAGE_COOLDOWN);

		// summoned weapons I'm holding: how many, and the soonest to go home
		Set<Integer> batches = new HashSet<>();
		long soonest = Long.MAX_VALUE;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			Summoned s = Summoned.of(inventory.getItem(slot));
			if (s != null && s.ownedBy(player.getUUID())) {
				batches.add(s.batch());
				soonest = Math.min(soonest, s.expires() - now);
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		Summoned s = Summoned.of(carried);
		if (s != null && s.ownedBy(player.getUUID())) {
			batches.add(s.batch());
			soonest = Math.min(soonest, s.expires() - now);
		}
		// ascended: Hundred Blades on the ultimate key, above the others; and what the Armory has pinned
		if (Fate.isAscended(player)) {
			ultimateRow(graphics, font, x, y - 11, cd.ultimate() - now);
			Item pin = ArsenalArmory.pinned(player);
			if (pin != null) {
				Component pinned = Component.translatable("hud.jaymospowers.arsenal.pinned", new ItemStack(pin).getHoverName());
				graphics.fill(x - 2, y - 23, x + font.width(pinned) + 2, y - 13, 0x60000000);
				graphics.text(font, pinned, x, y - 22, 0xFFE8C860, true);
			}
		}

		Component summons = batches.isEmpty()
			? Component.translatable("hud.jaymospowers.arsenal.none", ArsenalPower.MAX_HELD)
			: Component.translatable("hud.jaymospowers.arsenal.held", batches.size(), ArsenalPower.MAX_HELD, seconds(Math.max(0, soonest)));
		int color = !batches.isEmpty() && soonest < 200 ? 0xFFFF7070 : 0xFFD8C8FF;
		graphics.fill(x - 2, y + 21, x + font.width(summons) + 2, y + 31, 0x60000000);
		graphics.text(font, summons, x, y + 22, color, true);
	}

	private static void ultimateRow(GuiGraphicsExtractor graphics, Font font, int x, int y, long left) {
		Component keyName = JaymosPowersClient.ultimateKey().getTranslatedKeyMessage();
		Component text = Component.literal("[").append(keyName).append("] ").append(Component.translatable("hud.jaymospowers.arsenal.ultimate"));
		int textW = font.width(text);
		boolean ready = left <= 0;
		graphics.fill(x - 2, y - 1, x + textW + BAR_W + 6, y + 9, 0x60000000);
		graphics.text(font, text, x, y, ready ? 0xFFFFE070 : 0xFFA0A0A0, true);
		int bx = x + textW + 3;
		graphics.fill(bx, y + 2, bx + BAR_W, y + 6, 0xFF2A2140);
		if (ready) {
			graphics.fill(bx, y + 2, bx + BAR_W, y + 6, ULTIMATE_ACCENT);
		} else {
			int w = (int) (BAR_W * (1.0 - Math.min(1.0, (double) left / HundredBlades.COOLDOWN)));
			graphics.fill(bx, y + 2, bx + w, y + 6, 0xFF8A6A20);
		}
	}

	private static void row(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, String label, long left, int total) {
		Component keyName = JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage();
		Component text = Component.literal("[").append(keyName).append("] ").append(Component.translatable(label));
		int textW = font.width(text);
		graphics.fill(x - 2, y - 1, x + textW + BAR_W + 6, y + 9, 0x60000000);
		boolean ready = left <= 0;
		if (!JaymosPowersClient.isUnlocked(key)) {
			// not mastered yet
			graphics.text(font, Component.literal("[").append(keyName).append("] ").append(Component.translatable("screen.jaymospowers.locked")),
				x, y, 0xFF606060, true);
			return;
		}
		graphics.text(font, text, x, y, ready ? 0xFFFFFFFF : 0xFFA0A0A0, true);
		int bx = x + textW + 3;
		graphics.fill(bx, y + 2, bx + BAR_W, y + 6, 0xFF2A2140);
		if (ready) {
			graphics.fill(bx, y + 2, bx + BAR_W, y + 6, ACCENT);
		} else {
			int w = (int) (BAR_W * (1.0 - Math.min(1.0, (double) left / total)));
			graphics.fill(bx, y + 2, bx + w, y + 6, 0xFF5A3D99);
		}
	}
}
