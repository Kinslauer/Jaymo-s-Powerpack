package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.arsenal.ArsenalArmory;
import com.jaymospowers.power.arsenal.ArsenalChains;
import com.jaymospowers.power.arsenal.ArsenalDamocles;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.arsenal.HundredBlades;
import com.jaymospowers.power.arsenal.Summoned;
import com.jaymospowers.power.arsenal.VoidCrown;
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
		EntityRendererRegistry.register(ArsenalEntities.FROST_LANCE, FrostLanceRenderer::new);
		// Reach Through: the portal and the weapon coming out of it, for everyone in view
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalReachRenderer::render);
		// Hundred Blades: the rifts in the sky
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalSkyRenderer::render);
		// the Void Arsenal's Sword of Damocles: its rift and the sword
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalDamoclesRenderer::render);
		// Cannon Battery: the rifts and their cannons, and X held and let go
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalCannonRenderer::render);
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalPortalRenderer::render);
		// Attendant Blades: the weapons hanging round their holder
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalWingRenderer::render);
		// Blade Storm: the shoulder rift and the blades on their loops
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalStormRenderer::render);
		// Blade riding: the blade under its rider's feet
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalRideRenderer::render);
		// Rift Chains: the rifts around whatever is bound, and their chains
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ArsenalChainsRenderer::render);
		ArsenalPortalRenderer.init();
		EntityRendererRegistry.register(ArsenalEntities.RIFT_CANNONBALL, context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.4F, false));
		ClientTickEvents.START_CLIENT_TICK.register(ArsenalPowerClient::cannonInput);
		ClientTickEvents.START_CLIENT_TICK.register(ArsenalPowerClient::wingInput);
		// the Void Arsenal's aura, on every corrupted arsenal user in view
		ClientTickEvents.END_CLIENT_TICK.register(ArsenalAura::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (VoidCrown.is(stack) && minecraft.player != null && !VoidCrown.mayWear(minecraft.player)) {
				// the crown, in hands it doesn't want
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.void_crown.cold")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				return;
			}
			Summoned summoned = Summoned.of(stack);
			if (summoned == null || minecraft.level == null) {
				return;
			}
			long left = Math.max(0, summoned.expires() - minecraft.level.getGameTime());
			Component line = Component.translatable("tooltip.jaymospowers.arsenal.summoned", seconds(left))
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
			lines.add(Math.min(1, lines.size()), line);
		});
	}

	/** Blade riding: Jump tapped twice, and the ridden blade's flight. */
	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		ArsenalRideClient.tick(minecraft, player);
	}

	/** (0.15.58) Sneak + G (once B is unlocked): the places menu; it picks on letting go. The server skips Return for it. */
	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		Minecraft minecraft = Minecraft.getInstance();
		if (down && key == PowerKey.ABILITY_2 && player.isShiftKeyDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& com.jaymospowers.power.arsenal.ArsenalPlaces.unlocked(player)) {
			minecraft.gui.setScreen(new ArsenalPlacesScreen(JaymosPowersClient.keyMapping(PowerKey.ABILITY_2)));
		}
	}

	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String SKILL_KEY = "key.jaymospowers.web_symbiote";
	private static net.minecraft.client.KeyMapping skillKey;
	private static boolean skillWasDown;

	/** X for an Arsenal user: Cannon Battery's hold and release. Its presses are taken here so the symbiote's menu never opens. */
	private static void cannonInput(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (skillKey == null) {
			for (net.minecraft.client.KeyMapping mapping : minecraft.options.keyMappings) {
				if (SKILL_KEY.equals(mapping.getName())) {
					skillKey = mapping;
				}
			}
		}
		if (skillKey == null || player == null || !com.jaymospowers.core.Powers.has(player, ArsenalPower.INSTANCE)) {
			skillWasDown = false;
			return;
		}
		while (skillKey.consumeClick()) {
			// taken
		}
		boolean down = skillKey.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down != skillWasDown) {
			skillWasDown = down;
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.jaymospowers.power.arsenal.ArsenalCannons.Hold.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.jaymospowers.power.arsenal.ArsenalCannons.Hold(down));
			}
		}
	}

	private static int wingHeld = -1;

	/**
	 * The wing's key (Sneak + V; the Void Arsenal's Sneak + X) held with the blades out: the order wheel opens once it
	 * has been down {@link com.jaymospowers.power.arsenal.ArsenalWing#HOLD_TICKS}. (With no blades out the server calls them.)
	 */
	private static void wingInput(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || player.getAttached(com.jaymospowers.power.arsenal.ArsenalWing.WING) == null
			|| !com.jaymospowers.core.Powers.has(player, ArsenalPower.INSTANCE)) {
			wingHeld = -1;
			return;
		}
		net.minecraft.client.KeyMapping key = Fate.isCorrupted(player) ? skillKey : JaymosPowersClient.keyMapping(PowerKey.ABILITY_3);
		if (key == null || !key.isDown()) {
			wingHeld = -1;
			return;
		}
		if (wingHeld < 0) {
			// only a press that began with Sneak held is the wing's
			wingHeld = player.isShiftKeyDown() ? 0 : Integer.MIN_VALUE;
		}
		if (wingHeld >= 0 && ++wingHeld >= com.jaymospowers.power.arsenal.ArsenalWing.HOLD_TICKS) {
			wingHeld = Integer.MIN_VALUE;
			minecraft.gui.setScreen(new ArsenalWingScreen(key));
		}
	}

	private static String seconds(long ticks) {
		return Long.toString((ticks + 19) / 20);
	}

	@Override
	public int hudHeight(LocalPlayer player) {
		// (HUD rework) the text rows' block in the right dock
		return player.getAttached(com.jaymospowers.power.arsenal.ArsenalWing.WING) == null ? 70 : 92;
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

		// to the right of the hotbar, clear of the core's badge (top-left); a row higher with Rift Chains to show
		boolean corrupted = Fate.isCorrupted(player);
		// (HUD rework) now inside the power block of the right dock (PowerDock), keeping the block's internal arrangement
		int x = com.jaymospowers.client.hud.PowerDock.left() + 2;
		int y = com.jaymospowers.client.hud.PowerDock.bottom() - (corrupted ? 33 : 44);
		// the first ability row: Hundred Blades and the pin stack above it, never over it
		int top = y;

		row(graphics, font, x, y, PowerKey.ABILITY_1, "hud.jaymospowers.arsenal.reach", cd.reach() - now, ArsenalPower.REACH_COOLDOWN);
		if (corrupted) {
			// the Void Arsenal's V is its gates (no cooldown to show); its B is the Sword of Damocles
			row(graphics, font, x, y + 11, PowerKey.ABILITY_4, "hud.jaymospowers.arsenal.damocles", cd.damocles() - now, ArsenalDamocles.COOLDOWN);
		} else {
			row(graphics, font, x, y + 11, PowerKey.ABILITY_3, "hud.jaymospowers.arsenal.barrage", cd.barrage() - now, ArsenalPower.BARRAGE_COOLDOWN);
			row(graphics, font, x, y + 22, PowerKey.ABILITY_4, "hud.jaymospowers.arsenal.chains", cd.chains() - now, ArsenalChains.COOLDOWN);
			y += 11;
		}

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
			ultimateRow(graphics, font, x, top - 11, cd.ultimate() - now);
			Item pin = ArsenalArmory.pinned(player);
			if (pin != null) {
				Component pinned = Component.translatable("hud.jaymospowers.arsenal.pinned", new ItemStack(pin).getHoverName());
				graphics.fill(x - 2, top - 23, x + font.width(pinned) + 2, top - 13, 0x60000000);
				graphics.text(font, pinned, x, top - 22, 0xFFE8C860, true);
			}
		}

		wingPips(graphics, player, x, top - (Fate.isAscended(player) ? 45 : 22), now);

		Component summons = batches.isEmpty()
			? Component.translatable("hud.jaymospowers.arsenal.none", ArsenalPower.MAX_HELD)
			: Component.translatable("hud.jaymospowers.arsenal.held", batches.size(), ArsenalPower.MAX_HELD, seconds(Math.max(0, soonest)));
		int color = !batches.isEmpty() && soonest < 200 ? 0xFFFF7070 : 0xFFD8C8FF;
		graphics.fill(x - 2, y + 21, x + font.width(summons) + 2, y + 31, 0x60000000);
		graphics.text(font, summons, x, y + 22, color, true);
	}

	/** Attendant Blades: one icon a blade, dimmed while it is away striking, with a bar for the time before it goes home. */
	private static void wingPips(GuiGraphicsExtractor graphics, LocalPlayer player, int x, int y, long now) {
		com.jaymospowers.power.arsenal.ArsenalWing.Wing wing = player.getAttached(com.jaymospowers.power.arsenal.ArsenalWing.WING);
		if (wing == null) {
			return;
		}
		int count = wing.blades().size();
		graphics.fill(x - 2, y - 1, x + count * 17 + 1, y + 20, 0x60000000);
		for (int i = 0; i < count; i++) {
			com.jaymospowers.power.arsenal.ArsenalWing.Blade blade = wing.blades().get(i);
			int px = x + i * 17;
			graphics.item(blade.item(), px, y);
			if (wing.out(i, now) < 1.0F) {
				graphics.fill(px, y, px + 16, y + 16, 0xA0000000);
			}
			long left = blade.expires() - now;
			int w = (int) (16 * Math.clamp((double) left / com.jaymospowers.power.arsenal.ArsenalWing.BLADE_TICKS, 0.0, 1.0));
			graphics.fill(px, y + 17, px + 16, y + 19, 0xFF2A2140);
			graphics.fill(px, y + 17, px + w, y + 19, left < 200 ? 0xFFFF7070 : ACCENT);
		}
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
