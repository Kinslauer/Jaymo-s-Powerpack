package com.jaymospowers.client.power.resonance;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.resonance.ResonancePower;
import com.jaymospowers.power.resonance.ResonanceState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Resonance's client half:
 * <ul>
 * <li>the heartbeat ring (the starter, always on): a faint ring round the crosshair, with a pulsing blip in the
 * direction of every creature within {@link ResonancePower#HEARTBEAT_RADIUS} blocks (and every heart a Shriek let you
 * hear), and a red flash where an unseen blow came from (Heavy hearing);</li>
 * <li>right of the hotbar, in Wind's chip style: R G V B, Z (the sound picked), X (the pitch) once grown into, and C
 * once it answers;</li>
 * <li>over the crosshair: Shatter's hum and Sonic Boom's charge.</li>
 * </ul>
 */
public final class ResonancePowerClient implements PowerClient {
	public static final ResonancePowerClient INSTANCE = new ResonancePowerClient();

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = ResonancePower.COLOR;
	private static final int ULT_COLOR = 0xFFD54A;
	private static final int SHRIEK_COLOR = 0x29DFEB;
	private static final int RING_RADIUS = 26;
	private static final int RING_DOTS = 48;
	private static final int BEAT_PERIOD = 24;
	private static final Item[] VOICE_ICONS = {Items.LEATHER_BOOTS, Items.ZOMBIE_HEAD, Items.OAK_DOOR, Items.PLAYER_HEAD};

	/** Creatures whose hearts the ring shows (entity ids), refreshed every tick. */
	private final List<Integer> hearts = new ArrayList<>();

	private ResonancePowerClient() {
	}

	public static void init() {
		PowerClients.register(ResonancePower.ID, INSTANCE);
		ResonanceSenseClient.init();
		ResonanceInputClient.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("resonance_hush"), ResonanceHushClient::render);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		hearts.clear();
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		hearts.clear();
		double r = ResonancePower.HEARTBEAT_RADIUS;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= r * r)) {
			hearts.add(living.getId());
		}
		for (Map.Entry<Integer, Long> beat : ResonanceSenseClient.heartbeats().entrySet()) {
			if (!hearts.contains(beat.getKey())) {
				hearts.add(beat.getKey());
			}
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float time = player.tickCount + partial;
		long now = player.level().getGameTime();
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		if (minecraft.options.getCameraType().isFirstPerson()) {
			ring(graphics, player, cx, cy, time, partial);
		}
		ResonanceState state = ResonancePower.state(player);
		centerBars(graphics, font, state, now, cx, cy, time);
		chips(graphics, font, player, state, now, time);
	}

	// ---------------------------------------------------------------- the heartbeat ring

	private void ring(GuiGraphicsExtractor graphics, LocalPlayer player, int cx, int cy, float time, float partial) {
		boolean corrupted = Fate.isCorrupted(player);
		int color = corrupted ? SHRIEK_COLOR : 0xD6B4FC;
		if (hearts.isEmpty() && ResonanceSenseClient.flashes().isEmpty()) {
			return;
		}
		for (int i = 0; i < RING_DOTS; i++) {
			double a = Math.PI * 2.0 * i / RING_DOTS;
			int x = cx + (int)Math.round(Math.sin(a) * RING_RADIUS);
			int y = cy - (int)Math.round(Math.cos(a) * RING_RADIUS);
			graphics.fill(x, y, x + 1, y + 1, 0x28000000 | color);
		}
		double px = Mth.lerp(partial, player.xo, player.getX());
		double pz = Mth.lerp(partial, player.zo, player.getZ());
		float yaw = player.getViewYRot(partial);
		for (int id : hearts) {
			Entity entity = player.level().getEntity(id);
			if (entity == null) {
				continue;
			}
			double ex = Mth.lerp(partial, entity.xo, entity.getX());
			double ez = Mth.lerp(partial, entity.zo, entity.getZ());
			double distance = Math.sqrt((ex - px) * (ex - px) + (ez - pz) * (ez - pz));
			float beat = beat(time + (id * 7 % BEAT_PERIOD));
			int alpha = (int)(0x40 + 0xB0 * beat * Mth.clamp(1.0 - distance / (ResonancePower.HEARTBEAT_RADIUS * 1.5), 0.35, 1.0));
			int size = distance < 5.0 ? 3 : 2;
			int[] at = onRing(px, pz, ex, ez, yaw, cx, cy, RING_RADIUS);
			graphics.fill(at[0] - size / 2, at[1] - size / 2, at[0] - size / 2 + size, at[1] - size / 2 + size, alpha << 24 | color);
		}
		long now = player.level().getGameTime();
		for (ResonanceSenseClient.Flash flash : ResonanceSenseClient.flashes()) {
			float left = Mth.clamp((flash.until() - now - partial) / ResonanceSenseClient.FLASH_TICKS, 0.0F, 1.0F);
			int alpha = (int)(0xF0 * left);
			for (int k = -3; k <= 3; k++) {
				int[] at = onRing(px, pz, flash.x(), flash.z(), yaw - k * 4.0F, cx, cy, RING_RADIUS + 3);
				int c = k == 0 ? 0xFFFFFF : 0xFF5050;
				graphics.fill(at[0] - 1, at[1] - 1, at[0] + 2, at[1] + 2, alpha << 24 | c);
			}
		}
	}

	/** A heartbeat: two thumps, then rest (0-1). */
	private static float beat(float t) {
		float phase = t % BEAT_PERIOD;
		float first = Math.max(0.0F, 1.0F - Math.abs(phase - 1.5F) / 2.0F);
		float second = 0.7F * Math.max(0.0F, 1.0F - Math.abs(phase - 6.0F) / 2.0F);
		return Math.max(first, second);
	}

	/** Where on the ring a world point lies: straight ahead is the top, to your right is the right. */
	private static int[] onRing(double px, double pz, double x, double z, float yaw, int cx, int cy, int radius) {
		float toward = (float)(Mth.atan2(z - pz, x - px) * Mth.RAD_TO_DEG) - 90.0F;
		double rel = Math.toRadians(Mth.wrapDegrees(toward - yaw));
		return new int[] {cx + (int)Math.round(Math.sin(rel) * radius), cy - (int)Math.round(Math.cos(rel) * radius)};
	}

	// ---------------------------------------------------------------- over the crosshair

	private static void centerBars(GuiGraphicsExtractor graphics, Font font, ResonanceState state, long now, int cx, int cy, float time) {
		float fill;
		Component label;
		int color;
		if (state.humFrom() >= 0) {
			fill = Mth.clamp((now - state.humFrom()) / 40.0F, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.resonance.shatter");
			color = CHIP_COLOR;
		} else if (state.chargeUntil() > now) {
			fill = 1.0F - Mth.clamp((state.chargeUntil() - now) / 20.0F, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.resonance.boom");
			color = SHRIEK_COLOR;
		} else {
			return;
		}
		int width = 52;
		int x0 = cx - width / 2;
		int y = cy - 20 - RING_RADIUS / 2;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 3, 0xFF000000 | (fill >= 1.0F ? pulse(0xFFFFFF, color, time * 0.6F) : color));
		graphics.centeredText(font, label.copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	// ---------------------------------------------------------------- chips

	private static void chips(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, ResonanceState state, long now, float time) {
		boolean voice = ResonancePower.INSTANCE.slotUnlocked(player, "mode");
		boolean tune = ResonancePower.INSTANCE.slotUnlocked(player, "special");
		boolean ascended = Fate.isAscended(player);
		boolean corrupted = Fate.isCorrupted(player);
		boolean ultimate = ascended || corrupted;
		int count = 4 + (voice ? 1 : 0) + (tune ? 1 : 0) + (ultimate ? 1 : 0);
		int total = CHIP * count + CHIP_GAP * (count - 1);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		int step = CHIP + CHIP_GAP;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.BELL, state, ResonanceState.PULSE, now, -1.0F);
		float hum = state.humFrom() >= 0 ? Mth.clamp((now - state.humFrom()) / 40.0F, 0.0F, 1.0F) : -1.0F;
		chip(graphics, font, x + step, y, PowerKey.ABILITY_2, Items.AMETHYST_CLUSTER, state, ResonanceState.SHATTER, now, hum);
		chip(graphics, font, x + step * 2, y, PowerKey.ABILITY_3, Items.SCULK_SENSOR, state, ResonanceState.ECHO, now, -1.0F);
		chip(graphics, font, x + step * 3, y, PowerKey.ABILITY_4, Items.SPONGE, state, ResonanceState.HUSH, now, -1.0F);
		int next = 4;
		if (voice) {
			Item icon = VOICE_ICONS[Math.floorMod(state.sound(), VOICE_ICONS.length)];
			special(graphics, font, x + step * next++, y, new ItemStack(icon), state.left(ResonanceState.VOICE, now),
				state.length()[ResonanceState.VOICE], JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), CHIP_COLOR, null);
		}
		if (tune) {
			int pitch = state.tune();
			String label = pitch == 0 ? "0" : (pitch > 0 ? "+" : "") + pitch;
			int color = ResonanceInputClient.tuning() ? pulse(0xFFFFFF, CHIP_COLOR, time * 0.5F) : CHIP_COLOR;
			special(graphics, font, x + step * next++, y, new ItemStack(Items.NOTE_BLOCK), 0, 1,
				ResonanceInputClient.keyLabel(Minecraft.getInstance()), color, label);
		}
		if (ultimate) {
			boolean shriek = corrupted && (!ascended || player.isShiftKeyDown());
			special(graphics, font, x + step * next, y, new ItemStack(shriek ? Items.SCULK_SHRIEKER : Items.ECHO_SHARD),
				state.left(ResonanceState.ULTIMATE, now), state.length()[ResonanceState.ULTIMATE],
				JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(), shriek ? SHRIEK_COLOR : ULT_COLOR, null);
		}
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, ResonanceState state, int slot,
			long now, float bar) {
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
		shade(graphics, font, x, y, left, state.length()[slot]);
		if (bar >= 0.0F) {
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + Math.round((CHIP - 2) * bar), y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Z, X or C: their own key, an optional big label (the pitch) instead of the cooldown. */
	private static void special(GuiGraphicsExtractor graphics, Font font, int x, int y, ItemStack icon, long left, int total, String key, int color,
			String label) {
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | color : 0xA0505050);
		graphics.item(icon, x + 2, y + 2);
		shade(graphics, font, x, y, left, total);
		if (label != null) {
			graphics.text(font, label, x + 2, y + 2, 0xFFFFFFFF);
		}
		key = font.plainSubstrByWidth(key, CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	private static void shade(GuiGraphicsExtractor graphics, Font font, int x, int y, long left, int total) {
		if (left <= 0) {
			return;
		}
		int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, total)));
		graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
		String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
		graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
	}

	/** Blend between two 0xRRGGBB colours on a sine wave. */
	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int)Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
