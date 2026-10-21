package com.jaymospowers.client.power.cryo;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.cryo.CryoBlizzard;
import com.jaymospowers.power.cryo.CryoBreath;
import com.jaymospowers.power.cryo.CryoNet;
import com.jaymospowers.power.cryo.CryoPower;
import com.jaymospowers.power.cryo.CryoShards;
import com.jaymospowers.power.cryo.CryoState;
import com.jaymospowers.power.web.WebSymbiote;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.14.0) Cryokinesis' client half: right of the hotbar, in Wind's chip style, R G V B with their cooldowns (G and V
 * show snow in Snow mode); over the crosshair, how long Frost Breath has left, the Blizzard's time, or the skating speed.
 * X (the shared power skill key, registered by the symbiote's menu) is taken here for a Cryokinesis user at the start of
 * the tick and sent as the Ice Wall. The skating is {@link CryoSlideClient}, the lane everyone sees {@link CryoLaneClient},
 * the whiteout {@link CryoBlizzardClient}.
 */
public final class CryoPowerClient implements PowerClient {
	public static final CryoPowerClient INSTANCE = new CryoPowerClient();

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = CryoPower.COLOR;
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote";

	private static @Nullable KeyMapping key;
	private static boolean held;

	private CryoPowerClient() {
	}

	public static void init() {
		PowerClients.register(CryoPower.ID, INSTANCE);
		ClientTickEvents.START_CLIENT_TICK.register(CryoPowerClient::input);
		CryoLaneClient.init();
		CryoBlizzardClient.init();
		ClientTickEvents.END_CLIENT_TICK.register(CryoPose::tick);
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		CryoSlideClient.tick(player);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		CryoSlideClient.reset();
	}

	private static @Nullable KeyMapping key(Minecraft minecraft) {
		if (key == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (KEY_NAME.equals(mapping.getName())) {
					key = mapping;
					break;
				}
			}
		}
		return key;
	}

	/** X for a Cryokinesis user: a press raises the Ice Wall. */
	private static void input(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KeyMapping mapping = key(minecraft);
		if (mapping == null) {
			return;
		}
		if (player == null || !Powers.has(player, CryoPower.INSTANCE) || WebSymbiote.isBonded(player)) {
			held = false;
			return;
		}
		while (mapping.consumeClick()) {
			// taken: only the press counts
		}
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down && !held && ClientPlayNetworking.canSend(CryoNet.Wall.TYPE)) {
			ClientPlayNetworking.send(new CryoNet.Wall());
		}
		held = down;
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Font font = Minecraft.getInstance().font;
		long now = player.level().getGameTime();
		CryoState state = CryoPower.state(player);
		bar(graphics, font, player, state, now, graphics.guiWidth() / 2, graphics.guiHeight() / 2);
		chips(graphics, font, state, now, CryoPower.snow(player));
	}

	private static void bar(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, CryoState state, long now, int cx, int cy) {
		float fill;
		Component label;
		if (state.breathFrom() >= 0) {
			fill = 1.0F - Mth.clamp((now - state.breathFrom()) / (float)CryoBreath.MAX_TICKS, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.cryo.breath");
		} else if (state.blizzardFrom() >= 0) {
			fill = 1.0F - Mth.clamp((now - state.blizzardFrom()) / (float)CryoBlizzard.DURATION_TICKS, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.cryo.blizzard");
		} else if (CryoSlideClient.skating()) {
			fill = (float)CryoSlideClient.speedFraction(player);
			label = Component.translatable("hud.jaymospowers.cryo.slide");
		} else {
			return;
		}
		int width = 52;
		int x0 = cx - width / 2;
		int y = cy - 33;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 3, 0xFF000000 | CHIP_COLOR);
		graphics.centeredText(font, label.copy().withColor(CHIP_COLOR), cx, y - 11, 0xFFFFFFFF);
	}

	private static void chips(GuiGraphicsExtractor graphics, Font font, CryoState state, long now, boolean snow) {
		int total = CHIP * 4 + CHIP_GAP * 3;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		int step = CHIP + CHIP_GAP;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.ICE, state, CryoState.SLIDE, now);
		chip(graphics, font, x + step, y, PowerKey.ABILITY_2, snow ? Items.SNOWBALL : Items.POWDER_SNOW_BUCKET, state, CryoState.BREATH, now);
		chip(graphics, font, x + step * 2, y, PowerKey.ABILITY_3, snow || CryoShards.ICE_SHARD == null ? Items.SNOWBALL : CryoShards.ICE_SHARD, state,
			CryoState.SHARDS, now);
		chip(graphics, font, x + step * 3, y, PowerKey.ABILITY_4, Items.PACKED_ICE, state, CryoState.FREEZE, now);
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, CryoState state, int slot, long now) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(key)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		long left = state.left(slot, now);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, state.length()[slot])));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}
}
