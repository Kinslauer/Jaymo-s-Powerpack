package com.jaymospowers.client.dino;

import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.dino.DinoConfig;
import com.jaymospowers.dino.DinoItems;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The status panel of the dinosaur you look at or ride (health, torpor, taming, level), and the line saying what to do
 * next with it. Both sit under the crosshair in the {@code BELOW} zone, next to the horse tag.
 */
public final class DinoHud {
	private static final int W = 120, BAR = 4, PAD = 4;
	/** BELOW rows: the panel (the horse tag's row) and its hint under it. */
	private static final int PANEL_ROW = 40, HINT_ROW = 41;
	private static final int HEALTH = 0xFFD2323C, HEALTH_DARK = 0xFF8A2030, TAME = 0xFF4CAF50, TORPOR = 0xFF9A66D6, SPECIAL = 0xFFF0C040, XP = 0xFF60C060;
	private static int ridingSince = -1;

	private DinoHud() {
	}

	static void extract(GuiGraphicsExtractor gg, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		Dinosaur ridden = DinoClient.riddenDino();
		Dinosaur d = ridden != null ? ridden : lookedAt(mc);
		if (d == null) {
			HudZones.clear(HudZones.BELOW, "jaymospowers:dino_panel");
			HudZones.clear(HudZones.BELOW, "jaymospowers:dino_hint");
			return;
		}
		Font font = mc.font;
		Component title = Component.translatable(d.isTame() ? "hud.jaymospowers.dino.title_tame" : "hud.jaymospowers.dino.title", d.getName(), d.getDinoLevel());
		Component line = null;
		int lineColor = HudStyle.TEXT;
		if (d.isKnockedOut()) {
			line = Component.translatable("hud.jaymospowers.dino.taming", Math.round(d.getTameProgress() * 100.0F), Math.round(d.getEffectiveness() * 100.0F));
		} else if (d.isTame() && d == ridden) {
			int left = d.specialCooldownLeft();
			line = Component.translatable(left == 0 ? "hud.jaymospowers.dino.special_ready" : "hud.jaymospowers.dino.special_wait",
				DinoClient.SPECIAL.getTranslatedKeyMessage(), (left + 19) / 20);
			lineColor = HudStyle.GOLD;
		}
		int inner = Math.max(W, Math.max(font.width(title), line == null ? 0 : font.width(line)));
		int bars = d.isKnockedOut() ? 2 : 1;
		int height = PAD + 11 + 7 + (bars - 1) * 6 + (line != null ? 10 : 0) + (d.isKnockedOut() ? 4 : 0) + PAD;
		int[] at = HudZones.place(HudZones.BELOW, "jaymospowers:dino_panel", PANEL_ROW, inner + PAD * 2, height);
		if (at != null) {
			HudStyle.plate(gg, at[0], at[1], inner + PAD * 2, height, HudStyle.PLATE, HudStyle.RIM_DIM);
			int mid = at[0] + PAD + inner / 2;
			int x = at[0] + PAD + (inner - W) / 2;
			int y = at[1] + PAD;
			HudStyle.centred(gg, font, title, mid, y, HudStyle.TEXT);
			y += 11;
			bar(gg, x, y, d.getHealth() / Math.max(1.0F, d.getMaxHealth()), HEALTH, HEALTH_DARK);
			y += 7;
			if (d.isKnockedOut()) {
				bar(gg, x, y, d.getTameProgress(), TAME, 0xFF2A6A30);
				bar(gg, x, y + 6, d.getTorpor() / Math.max(1.0F, d.maxTorpor()), TORPOR, 0xFF5A3E96);
				HudStyle.centred(gg, font, line, mid, y + 14, lineColor);
			} else if (!d.isTame()) {
				bar(gg, x, y, d.getTorpor() / Math.max(1.0F, d.maxTorpor()), TORPOR, 0xFF5A3E96);
			} else if (d == ridden) {
				int left = d.specialCooldownLeft();
				float ready = 1.0F - (float) left / Math.max(1, d.species().specialCooldown());
				bar(gg, x, y, ready, left == 0 ? SPECIAL : 0xFF907040, 0xFF604828);
				HudStyle.centred(gg, font, line, mid, y + 8, lineColor);
			} else {
				bar(gg, x, y, (float) d.getXp() / Math.max(1, d.xpForNextLevel()), XP, 0xFF1A6A20);
			}
		}
		Component hint = DinoConfig.SHOW_HINTS ? hint(mc, d, ridden) : null;
		if (hint == null) {
			HudZones.clear(HudZones.BELOW, "jaymospowers:dino_hint");
			return;
		}
		int w = font.width(hint) + 8;
		int[] hintAt = HudZones.place(HudZones.BELOW, "jaymospowers:dino_hint", HINT_ROW, w, 13);
		if (hintAt != null) {
			HudStyle.plate(gg, hintAt[0], hintAt[1], w, 13, HudStyle.PLATE_SOFT, HudStyle.RIM_DIM);
			HudStyle.text(gg, font, hint, hintAt[0] + 4, hintAt[1] + 3, HudStyle.GOLD);
		}
	}

	private static Component hint(Minecraft mc, Dinosaur d, Dinosaur ridden) {
		if (d == ridden) {
			if (ridingSince < 0 || ridingSince > mc.player.tickCount) {
				ridingSince = mc.player.tickCount;
			}
			if (mc.player.tickCount - ridingSince > 300) {
				return null;
			}
			Component attack = mc.options.keyAttack.getTranslatedKeyMessage();
			Component special = DinoClient.SPECIAL.getTranslatedKeyMessage();
			Component roar = DinoClient.ROAR.getTranslatedKeyMessage();
			Component off = mc.options.keyShift.getTranslatedKeyMessage();
			return d.species().jumps()
				? Component.translatable("hint.jaymospowers.dino.riding_jump", attack, special, roar, mc.options.keyJump.getTranslatedKeyMessage(), off)
				: Component.translatable("hint.jaymospowers.dino.riding", attack, special, roar, off);
		}
		ridingSince = -1;
		if (d.isKnockedOut()) {
			Species s = d.species();
			return Component.translatable(s.eatsMeat() ? (s.eatsPlants() ? "hint.jaymospowers.dino.out_any" : "hint.jaymospowers.dino.out_meat")
				: "hint.jaymospowers.dino.out_plants");
		}
		if (d.isTame()) {
			if (!d.isOwnedBy(mc.player)) {
				return Component.translatable("hint.jaymospowers.dino.others");
			} else if (d.isBaby()) {
				return Component.translatable("hint.jaymospowers.dino.baby");
			} else if (!d.species().rideable()) {
				return Component.translatable("hint.jaymospowers.dino.no_ride");
			}
			return d.isSaddled() ? Component.translatable("hint.jaymospowers.dino.ride")
				: Component.translatable("hint.jaymospowers.dino.saddle", d.saddleName());
		}
		boolean armed = mc.player.getMainHandItem().is(DinoItems.TRANQ_RIFLE) || mc.player.getMainHandItem().is(DinoItems.TRANQ_DART)
			|| mc.player.getOffhandItem().is(DinoItems.TRANQ_DART);
		return Component.translatable(armed && d.getTorpor() > 0.0F ? "hint.jaymospowers.dino.wild_armed" : "hint.jaymospowers.dino.wild");
	}

	private static Dinosaur lookedAt(Minecraft mc) {
		Entity camera = mc.getCameraEntity();
		if (camera == null) {
			return null;
		}
		if (mc.crosshairPickEntity instanceof Dinosaur dino && dino.isAlive()) {
			return dino;
		}
		double range = 32.0;
		Vec3 from = camera.getEyePosition(1.0F);
		Vec3 dir = camera.getViewVector(1.0F);
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = camera.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
		double max = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(from);
		Vec3 end = from.add(dir.scale(max));
		AABB search = camera.getBoundingBox().expandTowards(dir.scale(max)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, from, end, search,
			e -> e instanceof Dinosaur && !e.isSpectator() && e.getRootVehicle() != camera.getRootVehicle(), max * max);
		return hit != null && hit.getEntity() instanceof Dinosaur dino && dino.isAlive() ? dino : null;
	}

	private static void bar(GuiGraphicsExtractor gg, int x, int y, float fill, int top, int bottom) {
		gg.fill(x, y, x + W, y + BAR, 0xFF1A1A1A);
		int w = Math.round(W * Math.max(0.0F, Math.min(1.0F, fill)));
		if (w > 0) {
			gg.fill(x, y, x + w, y + BAR / 2, top);
			gg.fill(x, y + BAR / 2, x + w, y + BAR, bottom);
		}
	}
}
