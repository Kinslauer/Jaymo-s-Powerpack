package com.jaymospowers.client.rig;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigNet;
import com.jaymospowers.rig.RigScan;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The Helm's HUD, bottom-right above the power chips: the Core's charge and the Mind's level (a thin strip under the
 * top edge fills toward the next), the three actives (their keys, cooldowns, hover lit), from level 3 a second row
 * (Scan, Suit, the takeover save's readiness, full flight), what's under the crosshair out to {@link #TARGET_RANGE}
 * blocks (or the lock), and the Mind's warnings (blinking). Only with the Helm on. (0.11.87) Also in the world, on the
 * wearer's screen: brackets round the locked target and marks on a scan's chests and ores; and, Helm or not, the red
 * frame of the Mind's takeover.
 */
final class RigHud {
	static final int WIDTH = 120, LINE = 10;
	/** How far the Helm reads a target. */
	static final double TARGET_RANGE = 32.0;
	static final int ACCENT = 0xFF5FE6FF, LOW = 0xFFFF5A4A, DIM = 0x60FFFFFF, PANEL = 0x90081018;
	/** The actives' cooldowns' full lengths (ticks), for shading (the server's {@code RigActives} values). */
	static final int PALM_COOLDOWN = 15, LAUNCHER_COOLDOWN = 60, UNIBEAM_COOLDOWN = 200;
	/** The takeover frame: its strongest alpha (0-255), depth (share of the smaller side), soft steps. */
	static final int TAKEOVER_ALPHA = 170, TAKEOVER_STEPS = 8;
	static final float TAKEOVER_DEPTH = 0.2F;
	private static final String[] ACTIVE_KEYS = {"rig.jaymospowers.hud.hover", "rig.jaymospowers.hud.palm", "rig.jaymospowers.hud.launcher"};
	private static final int[] WARN_BITS = {RigNet.WARN_EXPLOSION, RigNet.WARN_PROJECTILE, RigNet.WARN_LAVA, RigNet.WARN_VOID, RigNet.WARN_BEHIND,
		RigNet.WARN_LOW_HEALTH, RigNet.WARN_LOW_CHARGE};
	private static final String[] WARN_KEYS = {"explosion", "projectile", "lava", "void", "behind", "low_health", "low_charge"};

	private RigHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		if (RigClient.takeoverTicks > 0) {
			takeover(graphics, player);
		}
		if (!Rig.wears(player, Rig.Piece.HELM)) {
			return;
		}
		world(graphics, minecraft, player);
		Font font = minecraft.font;
		RigNet.Status status = RigClient.status;
		boolean blink = (player.tickCount / 5) % 2 == 0;
		LivingEntity lock = RigClient.locked(player);
		LivingEntity target = lock != null ? lock : target(minecraft, player);
		int warnings = status.warnings();
		int level = Rig.level(player);
		boolean second = level >= Rig.SCAN_LEVEL;
		int lines = 2 + (second ? 1 : 0) + (target != null ? 1 : 0) + (warnings != 0 ? 1 : 0);
		int x = graphics.guiWidth() - WIDTH - 2;
		int bottom = graphics.guiHeight() - 40;
		int y = bottom - lines * LINE - 4;
		graphics.fill(x, y, x + WIDTH, bottom, PANEL);
		graphics.fill(x, y, x + WIDTH, y + 1, ACCENT);
		if (level > 0) {
			// the Mind's progress to its next level
			graphics.fill(x, y + 1, x + Math.round(WIDTH * Rig.levelProgress(player)), y + 2, 0x905FE6FF);
		}
		int row = y + 3;

		// the charge, the Mind and its level
		ItemStack core = Rig.core(player);
		String mind = core.isEmpty() ? null : core.get(Rig.MIND);
		graphics.text(font, Component.translatable("rig.jaymospowers.hud.title"), x + 3, row, ACCENT, false);
		if (core.isEmpty()) {
			graphics.text(font, Component.translatable("rig.jaymospowers.hud.no_core"), x + 26, row, DIM, false);
		} else {
			float share = Rig.chargeShare(player);
			boolean low = (warnings & RigNet.WARN_LOW_CHARGE) != 0;
			int barX = x + 26, barW = 36;
			graphics.fill(barX, row + 2, barX + barW, row + 6, 0x60000000);
			graphics.fill(barX, row + 2, barX + Math.round(barW * share), row + 6, low && blink ? LOW : ACCENT);
			graphics.text(font, Math.round(share * 100) + "%", barX + barW + 3, row, low ? LOW : 0xFFFFFFFF, false);
			if (mind != null) {
				String tag = Component.translatable("rig.jaymospowers.hud.mind." + mind).getString() + " "
					+ Component.translatable("rig.jaymospowers.hud.level", level).getString();
				graphics.text(font, tag, x + WIDTH - 3 - font.width(tag), row, DIM, false);
			}
		}
		row += LINE;

		// the actives
		boolean powerless = Powers.of(player) == null;
		if (!Rig.fullSet(player)) {
			graphics.text(font, Component.translatable("rig.jaymospowers.hud.incomplete"), x + 3, row, DIM, false);
		} else {
			// (0.11.107) the Unibeam joins them at level 4
			boolean uni = level >= Rig.UNIBEAM_LEVEL;
			int cell = (WIDTH - 6) / (uni ? 4 : 3);
			if (uni) {
				int cx = x + 3 + 3 * cell;
				boolean picked = !powerless && RigClient.selected == RigNet.UNIBEAM;
				String key = powerless ? "S" + keyName(JaymosPowersClient.keyMapping(PowerKey.ABILITY_2).getTranslatedKeyMessage())
					: picked ? keyName(RigClient.rigKey().getTranslatedKeyMessage()) : "";
				cell(graphics, font, cx, row, cell, key, Component.translatable("rig.jaymospowers.hud.unibeam").getString(), false, picked,
					status.unibeamCooldown(), UNIBEAM_COOLDOWN);
			}
			for (int i = 0; i < 3; i++) {
				int cx = x + 3 + i * cell;
				int cooldown = i == RigNet.PALM ? status.palmCooldown() : i == RigNet.LAUNCHER ? status.launcherCooldown() : 0;
				int total = i == RigNet.PALM ? PALM_COOLDOWN : LAUNCHER_COOLDOWN;
				boolean lit = i == RigNet.HOVER && status.hovering();
				boolean picked = !powerless && i == RigClient.selected;
				String key = powerless ? keyName(JaymosPowersClient.keyMapping(PowerKey.byIndex(i)).getTranslatedKeyMessage())
					: picked ? keyName(RigClient.rigKey().getTranslatedKeyMessage()) : "";
				cell(graphics, font, cx, row, cell, key, Component.translatable(ACTIVE_KEYS[i]).getString(), lit, picked, cooldown, total);
			}
		}
		row += LINE;

		// (0.11.87) the Mind's own: Scan, Suit, the save, flight
		if (second) {
			int cell = (WIDTH - 6) / 4;
			int cx = x + 3;
			String scanKey = powerless ? keyName(JaymosPowersClient.keyMapping(PowerKey.ABILITY_4).getTranslatedKeyMessage())
				: RigClient.selected == RigNet.SCAN ? keyName(RigClient.rigKey().getTranslatedKeyMessage()) : "";
			int scanCooldown = status.scanCooldown();
			int hold = RigClient.scanHeldTicks();
			cell(graphics, font, cx, row, cell, scanKey, Component.translatable("rig.jaymospowers.hud.scan").getString(), hold > 0,
				!powerless && RigClient.selected == RigNet.SCAN, scanCooldown, RigScan.SCAN_COOLDOWN);
			if (hold > 0 && scanCooldown == 0) {
				int w = Math.round((cell - 2) * Math.min(1.0F, hold / (float)RigClient.SCAN_HOLD));
				graphics.fill(cx, row + 7, cx + w, row + 9, ACCENT);
			}
			cx += cell;
			String callKey = powerless ? keyName(JaymosPowersClient.ultimateKey().getTranslatedKeyMessage())
				: RigClient.selected == RigNet.CALL ? keyName(RigClient.rigKey().getTranslatedKeyMessage()) : "";
			cell(graphics, font, cx, row, cell, callKey, Component.translatable("rig.jaymospowers.hud.call").getString(), false,
				!powerless && RigClient.selected == RigNet.CALL, 0, 1);
			cx += cell;
			if (level >= Rig.TAKEOVER_LEVEL) {
				int s = status.takeoverSeconds();
				String save = s <= 0 ? Component.translatable("rig.jaymospowers.hud.save").getString() : (s / 60) + ":" + String.format("%02d", s % 60);
				cell(graphics, font, cx, row, cell, "", save, s <= 0 && RigClient.takeoverTicks > 0, false, 0, 1);
				if (s > 0) {
					graphics.fill(cx, row + 7, cx + cell - 2, row + 9, 0x60FF8040);
				}
			}
			cx += cell;
			if (level >= Rig.FLIGHT_LEVEL) {
				cell(graphics, font, cx, row, cell, "", Component.translatable("rig.jaymospowers.hud.fly").getString(), status.flying(), false, 0, 1);
			}
			row += LINE;
		}

		// the target (or the lock)
		if (target != null) {
			String name = target.getDisplayName().getString();
			if (target == lock) {
				name = Component.translatable("rig.jaymospowers.hud.lock").getString() + " " + name;
			}
			String hp = Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth());
			int hpWidth = font.width(hp);
			graphics.text(font, cut(font, name, WIDTH - hpWidth - 10), x + 3, row, target == lock ? 0xFF000000 | RigScanClient.LOCK_COLOR : 0xFFE0E0E0, false);
			graphics.text(font, hp, x + WIDTH - 3 - hpWidth, row, 0xFFFF7070, false);
			row += LINE;
		}

		// the Mind's warnings
		if (warnings != 0) {
			int wx = x + 3;
			for (int i = 0; i < WARN_BITS.length; i++) {
				if ((warnings & WARN_BITS[i]) == 0) {
					continue;
				}
				String word = Component.translatable("rig.jaymospowers.hud.warn." + WARN_KEYS[i]).getString();
				int w = font.width(word);
				if (wx + w > x + WIDTH - 2) {
					break;
				}
				graphics.text(font, word, wx, row, blink || i >= 5 ? LOW : 0xFFFFB0A0, false);
				wx += w + 4;
			}
		}
	}

	/** One active's cell: lit (on), picked (the Rig key's), its cooldown shading. */
	private static void cell(GuiGraphicsExtractor graphics, Font font, int cx, int row, int cell, String key, String label, boolean lit, boolean picked,
			int cooldown, int total) {
		graphics.fill(cx, row - 1, cx + cell - 2, row + 9, lit ? 0x8040C0E0 : picked ? 0x50FFFFFF : 0x40000000);
		if (cooldown > 0) {
			int w = Math.round((cell - 2) * Math.min(1.0F, cooldown / (float)total));
			graphics.fill(cx, row + 7, cx + w, row + 9, 0xC0FF8040);
		}
		String text = key.isEmpty() ? label : key + " " + label;
		graphics.text(font, cut(font, text, cell - 3), cx + 2, row, cooldown > 0 ? DIM : 0xFFFFFFFF, false);
	}

	/** A key's name, short (the first letters of a long one). */
	private static String keyName(Component name) {
		String s = name.getString();
		return s.length() > 3 ? s.substring(0, 3) : s;
	}

	private static String cut(Font font, String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		while (!s.isEmpty() && font.width(s + "…") > width) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	/** The marks in the world: brackets round the lock, a scan's chests and ores. */
	private static void world(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		LivingEntity lock = RigClient.locked(player);
		if (lock != null) {
			Vec3 at = lock.getPosition(partial).add(0.0, lock.getBbHeight() * 0.5, 0.0);
			double[] p = project(graphics, minecraft, at);
			if (p != null) {
				int size = (int)Math.clamp(lock.getBbHeight() * 0.5 * p[2], 5.0, 60.0);
				int cx = (int)p[0], cy = (int)p[1];
				int color = 0xFF000000 | RigScanClient.LOCK_COLOR;
				int arm = Math.max(2, size / 3);
				// four corners
				graphics.fill(cx - size, cy - size, cx - size + arm, cy - size + 1, color);
				graphics.fill(cx - size, cy - size, cx - size + 1, cy - size + arm, color);
				graphics.fill(cx + size - arm, cy - size, cx + size, cy - size + 1, color);
				graphics.fill(cx + size - 1, cy - size, cx + size, cy - size + arm, color);
				graphics.fill(cx - size, cy + size - 1, cx - size + arm, cy + size, color);
				graphics.fill(cx - size, cy + size - arm, cx - size + 1, cy + size, color);
				graphics.fill(cx + size - arm, cy + size - 1, cx + size, cy + size, color);
				graphics.fill(cx + size - 1, cy + size - arm, cx + size, cy + size, color);
				graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
			}
		}
		float fade = RigScanClient.fade();
		if (fade > 0.0F) {
			int alpha = Math.round(255 * Math.min(1.0F, fade * 3.0F));
			for (RigScanClient.Marker marker : RigScanClient.blocks()) {
				double[] p = project(graphics, minecraft, Vec3.atCenterOf(marker.pos()));
				if (p == null) {
					continue;
				}
				int cx = (int)p[0], cy = (int)p[1];
				int color = ARGB.color(alpha, RigScanClient.COLORS[Math.clamp(marker.kind(), 0, RigScanClient.COLORS.length - 1)]);
				graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, ARGB.color(alpha / 2, 0));
				graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
				graphics.outline(cx - 3, cy - 3, 6, 6, color);
			}
		}
	}

	/**
	 * Where a point in the world falls on the screen: {x, y, pixels per block there}, or null if it's behind the camera or
	 * off the screen.
	 */
	private static double @Nullable [] project(GuiGraphicsExtractor graphics, Minecraft minecraft, Vec3 world) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 rel = world.subtract(camera.position());
		Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
		double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		if (z < 0.2) {
			return null;
		}
		double lx = rel.x * left.x() + rel.y * left.y() + rel.z * left.z();
		double uy = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
		float fov = camera.getFov() > 1.0F ? camera.getFov() : minecraft.options.fov().get();
		double scale = graphics.guiHeight() * 0.5 / Math.tan(Math.toRadians(fov) * 0.5);
		double sx = graphics.guiWidth() * 0.5 - lx / z * scale;
		double sy = graphics.guiHeight() * 0.5 - uy / z * scale;
		if (sx < 0 || sy < 0 || sx > graphics.guiWidth() || sy > graphics.guiHeight()) {
			return null;
		}
		return new double[] {sx, sy, scale / z};
	}

	/** The Mind's takeover: a red frame round the screen, flickering, fading out. */
	private static void takeover(GuiGraphicsExtractor graphics, LocalPlayer player) {
		float strength = RigClient.takeoverTicks / (float)RigClient.TAKEOVER_SCREEN;
		if (player.tickCount % 3 == 0) {
			strength *= 0.7F;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int depth = Math.round(Math.min(w, h) * TAKEOVER_DEPTH);
		int step = Math.max(1, depth / TAKEOVER_STEPS);
		for (int i = 0; i < TAKEOVER_STEPS; i++) {
			int a = Math.round(TAKEOVER_ALPHA * strength * (1.0F - i / (float)TAKEOVER_STEPS));
			int color = ARGB.color(a, i % 2 == 0 ? 0xFF2030 : 0xFF4060);
			int o = i * step;
			graphics.fill(o, o, w - o, o + step, color);
			graphics.fill(o, h - o - step, w - o, h - o, color);
			graphics.fill(o, o + step, o + step, h - o - step, color);
			graphics.fill(w - o - step, o + step, w - o, h - o - step, color);
		}
		// a cyan scanline sweeping down: the Mind at the controls
		int line = (int)((1.0F - strength) * h);
		graphics.fill(0, line, w, line + 1, ARGB.color(Math.round(120 * strength), 0x5FE6FF));
	}

	/** What the Helm sees under the crosshair. */
	private static @Nullable LivingEntity target(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(TARGET_RANGE));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !e.isInvisible(), eye.distanceToSqr(end));
		Entity entity = hit == null ? null : hit.getEntity();
		return entity instanceof LivingEntity living ? living : null;
	}
}
