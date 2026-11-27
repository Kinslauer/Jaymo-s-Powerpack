package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.zfighter.ZFighterScouter;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The scouter's hold-to-scan on the wearer's screen (Wan's Bio Scanner's idea only, our own code). Hold the scan key (it
 * starts unbound, like ki sense's) on a living thing, a plant, or open sky or ground: the lens zooms a little
 * ({@link #fovFactor}), the client asks the server every {@link #ASK_EVERY} ticks what it is looking at, and the reading
 * fills a green panel line by line; let go early and what came through stays up for {@link #KEEP_TICKS}. A soft chime
 * sounds when a reading comes all the way through. A quick tap (under {@link #TAP_TICKS}) marks the last creature read
 * all the way: a chevron at the lens's edge points to each mark, and it is outlined through walls ({@link #markColor}).
 */
public final class ZFighterScanClient {
	/** Ticks between asks while the key is held, and the longest a press is still a tap. */
	static final int ASK_EVERY = 4, TAP_TICKS = 7;
	/** How long a reading stays up after the last of it came, and a mark's position past its last report (ticks). */
	static final int KEEP_TICKS = 240, MARK_HOLD = 30;
	/** The lens's field of view while scanning (share of normal) and how fast it eases there and back (per tick). */
	static final float ZOOM = 0.82F, ZOOM_EASE = 0.12F;
	/** The green of the scouter's readout, and a mark's outline. */
	static final int GREEN = 0xFF7CFFB0, TEXT = 0xFFE8FFF0, MARK_RGB = 0x7CFFB0;
	/** The chime: its pitch and volume (soft). */
	static final float CHIME_PITCH = 1.7F, CHIME_VOLUME = 0.35F;
	static final int LINE_H = 11;
	/** How far off a block or the ground is read (blocks). */
	static final double BLOCK_RANGE = 24.0;

	private static KeyMapping key;
	private static int held;
	private static boolean wasDown;
	private static float zoom;
	private static long receivedAt = Long.MIN_VALUE / 2;
	private static List<Component> lines = List.of();
	private static int total;
	/** Marked entity id → its last reported position and the game time it lapses. */
	private static final Map<Integer, Object[]> MARKS = new HashMap<>();

	private ZFighterScanClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.scouter_scan", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.ScanLines.TYPE, (payload, ctx) -> {
			Minecraft minecraft = ctx.client();
			if (minecraft.level == null || minecraft.player == null) {
				return;
			}
			receivedAt = minecraft.level.getGameTime();
			lines = payload.lines();
			total = payload.total();
			if (payload.chime()) {
				minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(),
					SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH, false);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.ScanMarks.TYPE, (payload, ctx) -> {
			Minecraft minecraft = ctx.client();
			MARKS.clear();
			if (minecraft.level != null) {
				long lapse = minecraft.level.getGameTime() + MARK_HOLD;
				for (ZFighterSensesNet.Marked mark : payload.marks()) {
					MARKS.put(mark.id(), new Object[] {new Vec3(mark.x(), mark.y(), mark.z()), lapse});
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterScanClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("zfighter_scan"), ZFighterScanClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MARKS.clear();
			lines = List.of();
			held = 0;
			zoom = 0.0F;
			wasDown = false;
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		boolean ready = ZFighterScouter.wearing(player) && !player.isSpectator() && minecraft.gui.screen() == null;
		boolean down = ready && key.isDown();
		if (down) {
			held++;
			if (held % ASK_EVERY == 1) {
				ask(minecraft, player);
			}
		} else if (wasDown && ready && held > 0 && held < TAP_TICKS) {
			ClientPlayNetworking.send(new ZFighterSensesNet.ScanMark());
		}
		if (!down) {
			held = 0;
		}
		wasDown = down;
		zoom = Mth.clamp(zoom + (down ? ZOOM_EASE : -ZOOM_EASE), 0.0F, 1.0F);
		if (!ready) {
			MARKS.clear();
		}
		while (key.consumeClick()) {
			// the press itself is read from isDown; clicks are only drained
		}
	}

	/** Asks the server about what the lens is on: a living thing, a block, or the open sky. */
	private static void ask(Minecraft minecraft, LocalPlayer player) {
		LivingEntity target = ZFighterScouterClient.lookedAt(minecraft, player);
		if (target != null) {
			ClientPlayNetworking.send(new ZFighterSensesNet.ScanAsk(ZFighterSensesNet.SCAN_ENTITY, target.getId(), BlockPos.ZERO));
			return;
		}
		HitResult hit = player.pick(BLOCK_RANGE, 1.0F, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			ClientPlayNetworking.send(new ZFighterSensesNet.ScanAsk(ZFighterSensesNet.SCAN_BLOCK, 0, block.getBlockPos()));
		} else {
			ClientPlayNetworking.send(new ZFighterSensesNet.ScanAsk(ZFighterSensesNet.SCAN_PLACE, 0, player.blockPosition()));
		}
	}

	/** How much of normal the field of view is now: a little less while scanning. */
	public static float fovFactor() {
		return zoom <= 0.0F ? 1.0F : Mth.lerp(zoom, 1.0F, ZOOM);
	}

	/** The through-the-wall outline colour of a marked living thing (RGB), or 0. */
	public static int markColor(Entity entity) {
		Object[] mark = MARKS.get(entity.getId());
		Minecraft minecraft = Minecraft.getInstance();
		return mark == null || minecraft.level == null || minecraft.level.getGameTime() > (long)mark[1] || !entity.isAlive() ? 0 : MARK_RGB;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !ZFighterScouter.wearing(player) || minecraft.gui.screen() != null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		if (!MARKS.isEmpty()) {
			chevrons(graphics, minecraft, player, now);
		}
		if (lines.isEmpty() || now - receivedAt > KEEP_TICKS) {
			return;
		}
		Font font = minecraft.font;
		boolean filling = lines.size() < total && held > 0;
		int rows = lines.size() + (filling ? 1 : 0);
		int w = 80;
		for (Component line : lines) {
			w = Math.max(w, font.width(line) + 8);
		}
		int h = 7 + rows * LINE_H;
		// (HUD rework) the scan's lines: INSPECT zone, under the power-level panel
		int[] at = HudZones.place(HudZones.INSPECT, "jaymospowers:zfighter_scan", 40, w, h);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, 0xA0001810);
		graphics.outline(at[0], at[1], w, h, 0xC07CFFB0);
		int y = at[1] + 4;
		for (Component line : lines) {
			graphics.text(font, line, at[0] + 4, y, TEXT);
			y += LINE_H;
		}
		if (filling) {
			graphics.text(font, now / 4 % 2 == 0 ? "." : "..", at[0] + 4, y, GREEN);
		}
	}

	/** A small chevron on the lens's edge pointing to each mark, with how far it is. */
	private static void chevrons(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, long now) {
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		float radius = Math.min(cx, cy) - 28.0F;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 eye = player.getEyePosition();
		for (Object[] mark : MARKS.values()) {
			if (now > (long)mark[1]) {
				continue;
			}
			Vec3 rel = ((Vec3)mark[0]).subtract(eye);
			double ahead = rel.x * -Mth.sin(yaw) + rel.z * Mth.cos(yaw);
			double right = rel.x * -Mth.cos(yaw) + rel.z * -Mth.sin(yaw);
			float angle = (float)Mth.atan2(right, ahead);
			float dx = Mth.sin(angle), dy = -Mth.cos(angle);
			float tipX = cx + dx * radius, tipY = cy + dy * radius;
			for (int i = 0; i < 7; i++) {
				for (int side = -1; side <= 1; side += 2) {
					// the arms sweep back from the tip, 40 degrees either side of the way it points
					float arm = angle + side * 0.7F;
					int px = Math.round(tipX - Mth.sin(arm) * i * 1.6F);
					int py = Math.round(tipY + Mth.cos(arm) * i * 1.6F);
					graphics.fill(px, py, px + 2, py + 2, GREEN);
				}
			}
			String far = Component.translatable("hud.jaymospowers.scan.metres", Math.round(rel.length())).getString();
			Font font = minecraft.font;
			graphics.text(font, far, Math.round(tipX - dx * 14.0F) - font.width(far) / 2, Math.round(tipY - dy * 14.0F) - 4, GREEN);
		}
	}
}
