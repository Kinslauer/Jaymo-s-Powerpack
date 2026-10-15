package com.jaymospowers.client.rig;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Helm's HUD, bottom-right above the power chips: the Core's charge, the three actives (their keys, cooldowns, hover
 * lit), what's under the crosshair out to {@link #TARGET_RANGE} blocks, and the Mind's warnings (blinking). Only with
 * the Helm on.
 */
final class RigHud {
	static final int WIDTH = 104, LINE = 10;
	/** How far the Helm reads a target. */
	static final double TARGET_RANGE = 32.0;
	static final int ACCENT = 0xFF5FE6FF, LOW = 0xFFFF5A4A, DIM = 0x60FFFFFF, PANEL = 0x90081018;
	/** The actives' cooldowns' full lengths (ticks), for shading (the server's {@code RigActives} values). */
	static final int PALM_COOLDOWN = 15, LAUNCHER_COOLDOWN = 60;
	private static final String[] ACTIVE_KEYS = {"rig.jaymospowers.hud.hover", "rig.jaymospowers.hud.palm", "rig.jaymospowers.hud.launcher"};
	private static final int[] WARN_BITS = {RigNet.WARN_EXPLOSION, RigNet.WARN_PROJECTILE, RigNet.WARN_LAVA, RigNet.WARN_VOID, RigNet.WARN_BEHIND,
		RigNet.WARN_LOW_HEALTH, RigNet.WARN_LOW_CHARGE};
	private static final String[] WARN_KEYS = {"explosion", "projectile", "lava", "void", "behind", "low_health", "low_charge"};

	private RigHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !Rig.wears(player, Rig.Piece.HELM)) {
			return;
		}
		Font font = minecraft.font;
		RigNet.Status status = RigClient.status;
		boolean blink = (player.tickCount / 5) % 2 == 0;
		LivingEntity target = target(minecraft, player);
		int warnings = status.warnings();
		int lines = 2 + (target != null ? 1 : 0) + (warnings != 0 ? 1 : 0);
		int x = graphics.guiWidth() - WIDTH - 2;
		int bottom = graphics.guiHeight() - 40;
		int y = bottom - lines * LINE - 4;
		graphics.fill(x, y, x + WIDTH, bottom, PANEL);
		graphics.fill(x, y, x + WIDTH, y + 1, ACCENT);
		int row = y + 3;

		// the charge
		ItemStack core = Rig.core(player);
		String mind = core.isEmpty() ? null : core.get(Rig.MIND);
		graphics.text(font, Component.translatable("rig.jaymospowers.hud.title"), x + 3, row, ACCENT, false);
		if (core.isEmpty()) {
			graphics.text(font, Component.translatable("rig.jaymospowers.hud.no_core"), x + 26, row, DIM, false);
		} else {
			float share = Rig.chargeShare(player);
			boolean low = (warnings & RigNet.WARN_LOW_CHARGE) != 0;
			int barX = x + 26, barW = 46;
			graphics.fill(barX, row + 2, barX + barW, row + 6, 0x60000000);
			graphics.fill(barX, row + 2, barX + Math.round(barW * share), row + 6, low && blink ? LOW : ACCENT);
			graphics.text(font, Math.round(share * 100) + "%", barX + barW + 3, row, low ? LOW : 0xFFFFFFFF, false);
			if (mind != null) {
				graphics.text(font, Component.translatable("rig.jaymospowers.hud.mind." + mind), x + WIDTH - 8, row, DIM, false);
			}
		}
		row += LINE;

		// the actives
		if (!Rig.fullSet(player)) {
			graphics.text(font, Component.translatable("rig.jaymospowers.hud.incomplete"), x + 3, row, DIM, false);
		} else {
			boolean powerless = Powers.of(player) == null;
			int cell = (WIDTH - 6) / 3;
			for (int i = 0; i < 3; i++) {
				int cx = x + 3 + i * cell;
				int cooldown = i == RigNet.PALM ? status.palmCooldown() : i == RigNet.LAUNCHER ? status.launcherCooldown() : 0;
				int total = i == RigNet.PALM ? PALM_COOLDOWN : LAUNCHER_COOLDOWN;
				boolean lit = i == RigNet.HOVER && status.hovering();
				boolean picked = !powerless && i == RigClient.selected;
				graphics.fill(cx, row - 1, cx + cell - 2, row + 9, lit ? 0x8040C0E0 : picked ? 0x50FFFFFF : 0x40000000);
				if (cooldown > 0) {
					int w = Math.round((cell - 2) * Math.min(1.0F, cooldown / (float)total));
					graphics.fill(cx, row + 7, cx + w, row + 9, 0xC0FF8040);
				}
				String key = powerless ? JaymosPowersClient.keyMapping(PowerKey.byIndex(i)).getTranslatedKeyMessage().getString()
					: picked ? RigClient.rigKey().getTranslatedKeyMessage().getString() : "";
				Component label = Component.translatable(ACTIVE_KEYS[i]);
				String text = key.isEmpty() ? label.getString() : key + " " + label.getString();
				graphics.text(font, text, cx + 2, row, cooldown > 0 ? DIM : 0xFFFFFFFF, false);
			}
		}
		row += LINE;

		// the target
		if (target != null) {
			String name = target.getDisplayName().getString();
			String hp = Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth());
			int hpWidth = font.width(hp);
			graphics.text(font, cut(font, name, WIDTH - hpWidth - 10), x + 3, row, 0xFFE0E0E0, false);
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

	private static String cut(Font font, String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		while (!s.isEmpty() && font.width(s + "…") > width) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
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
