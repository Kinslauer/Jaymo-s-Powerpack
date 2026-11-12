package com.jaymospowers.client.rig;

import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigFrame;
import com.jaymospowers.rig.RigHelm;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The helm view: with the faceplate down, in first person, the visor draws over the world itself. A deliberate exception
 * to the rule that power HUDs sit in the corners, for the Helm only (the faceplate up, or a third person camera, and it
 * is gone). It shows:
 * <ul>
 * <li>a <b>horizon line</b> that follows the view, with the height over the ground and the speed beside it;</li>
 * <li><b>brackets with health</b> on what you look at (and numbered ones on the creatures marked for a missile volley);</li>
 * <li><b>arrows</b> round the middle of the screen toward incoming harm: projectiles flying at you, and hostile creatures
 * close in that have you as their target;</li>
 * <li>a small <b>figure</b> of the suit, each plate coloured by how worn it is (green, amber, red), grey where a piece is off;
 * with the frame's name and the overcharge, while it lasts.</li>
 * </ul>
 */
final class RigHelmView {
	/** The visor's colours: lines, the dim of a missing piece, and wear from sound to ruined. */
	static final int LINE = 0xC85FE6FF, SOFT = 0x605FE6FF, MISSING = 0x70606A74, SOUND = 0xFF5FE68A, WORN = 0xFFE6C85F, RUINED = 0xFFFF5A4A;
	/** The horizon: half its width as a share of the screen's, the gap in the middle (pixels) and the ticks along it. */
	static final float HORIZON_HALF = 0.28F;
	static final int HORIZON_GAP = 26, HORIZON_TICK = 4;
	/** How far down it measures the height over the ground (blocks). */
	static final double GROUND_REACH = 256.0;
	/** Incoming harm: how far a projectile or a hostile creature is noticed (blocks), and a projectile’s aim at you (dot of the directions). */
	static final double PROJECTILE_REACH = 28.0, HOSTILE_REACH = 9.0, AIMED_DOT = 0.82;
	/** The arrows’ ring (share of the smaller screen side) and size (pixels). */
	static final float ARROW_RING = 0.36F;
	static final int ARROW_SIZE = 9;
	/** The figure: its size in pixels (a plate cell), and its distance from the right edge and the middle of the screen. */
	static final int CELL = 4, FIGURE_EDGE = 44;
	/** The target’s health bar: width in pixels. */
	static final int HEALTH_WIDTH = 34;
	/** The overcharge frame’s colour. */
	static final int OVER = 0xFFFFE88A;

	private RigHelmView() {
	}

	/** Whether the visor view is up for this player now. */
	static boolean active(Minecraft minecraft, LocalPlayer player) {
		return Rig.wears(player, Rig.Piece.HELM) && !RigHelm.faceUp(player) && minecraft.options.getCameraType().isFirstPerson();
	}

	static void render(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, float partial) {
		int w = g.guiWidth(), h = g.guiHeight();
		Font font = minecraft.font;
		horizon(g, minecraft, player, w, h);
		readout(g, minecraft, player, font, w, h);
		threats(g, minecraft, player, w, h, partial);
		LivingEntity looked = RigHud.target(minecraft, player);
		if (looked != null) {
			bracket(g, minecraft, looked, 0xFF000000 | 0x5FE6FF, partial, true, font, null);
		}
		figure(g, player, font, w, h);
		if (RigClient.status.overcharge() > 0) {
			overcharge(g, w, h, player);
		}
	}

	// ---- the horizon, height and speed ------------------------------------------------------------------------------

	private static void horizon(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, int w, int h) {
		double fov = Math.toRadians(minecraft.options.fov().get());
		double scale = h * 0.5 / Math.tan(fov * 0.5);
		double pitch = Math.toRadians(player.getXRot());
		// the horizon sits above the middle when looking down, below it when looking up
		int y = (int)Math.round(h * 0.5 - Math.tan(pitch) * scale);
		if (y < 6 || y > h - 6) {
			// off screen: a mark at the edge says which way it lies
			int edge = y < 6 ? 6 : h - 8;
			int cx = w / 2;
			g.fill(cx - 8, edge, cx + 8, edge + 2, SOFT);
			return;
		}
		int half = Math.round(w * HORIZON_HALF);
		int cx = w / 2;
		g.fill(cx - half, y, cx - HORIZON_GAP, y + 1, LINE);
		g.fill(cx + HORIZON_GAP, y, cx + half, y + 1, LINE);
		g.fill(cx - half, y - HORIZON_TICK, cx - half + 1, y + HORIZON_TICK + 1, LINE);
		g.fill(cx + half - 1, y - HORIZON_TICK, cx + half, y + HORIZON_TICK + 1, LINE);
		g.fill(cx - 1, y - 2, cx + 1, y + 3, SOFT);
	}

	private static void readout(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, Font font, int w, int h) {
		Vec3 eye = player.position();
		BlockHitResult below = player.level().clip(new ClipContext(eye, eye.add(0.0, -GROUND_REACH, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		int height = below.getType() == HitResult.Type.MISS ? -1 : (int)Math.round(eye.y - below.getLocation().y);
		Vec3 move = player.getDeltaMovement();
		int speed = (int)Math.round(Math.sqrt(move.x * move.x + move.z * move.z) * 20.0);
		int climb = (int)Math.round(move.y * 20.0);
		int x = Math.round(w * 0.5F - w * HORIZON_HALF) - 70;
		int y = h / 2 - 12;
		g.text(font, Component.translatable("rig.jaymospowers.view.height", height < 0 ? "--" : Integer.toString(height)), x, y, LINE, false);
		g.text(font, Component.translatable("rig.jaymospowers.view.altitude", Math.round(player.getY())), x, y + 10, SOFT, false);
		g.text(font, Component.translatable("rig.jaymospowers.view.speed", speed), x, y + 22, LINE, false);
		g.text(font, Component.translatable("rig.jaymospowers.view.climb", (climb > 0 ? "+" : "") + climb), x, y + 32, SOFT, false);
	}

	// ---- incoming harm ----------------------------------------------------------------------------------------------

	private static void threats(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, int w, int h, float partial) {
		Vec3 me = player.getBoundingBox().getCenter();
		int cx = w / 2, cy = h / 2;
		double ring = Math.min(w, h) * ARROW_RING;
		boolean blink = (player.tickCount / 3) % 2 == 0;
		for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(PROJECTILE_REACH),
			p -> p.isAlive() && p.getOwner() != player && !p.onGround())) {
			Vec3 to = me.subtract(projectile.position());
			double distance = to.length();
			Vec3 flying = projectile.getDeltaMovement();
			if (distance < 1.0E-3 || flying.lengthSqr() < 1.0E-3 || flying.normalize().dot(to.scale(1.0 / distance)) < AIMED_DOT) {
				continue;
			}
			arrow(g, player, projectile.position().subtract(me), cx, cy, ring, blink ? 0xFFFF6A3C : 0xFFFFB03C, distance / PROJECTILE_REACH);
		}
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HOSTILE_REACH),
			m -> m instanceof Enemy && m.isAlive() && m.getTarget() == player)) {
			arrow(g, player, mob.position().subtract(me), cx, cy, ring, 0xFFFFC85F, mob.distanceTo(player) / HOSTILE_REACH);
		}
	}

	/** An arrow on the ring pointing toward {@code offset} (a world direction from the player), bigger the nearer {@code closeness} is to 0. */
	private static void arrow(GuiGraphicsExtractor g, LocalPlayer player, Vec3 offset, int cx, int cy, double ring, int color, double closeness) {
		double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
		if (horizontal < 0.05) {
			return;
		}
		// Minecraft’s yaw turns clockwise; straight ahead is the top of the ring
		double bearing = Math.toDegrees(Math.atan2(-offset.x, offset.z));
		double angle = Math.toRadians(-90.0 + Mth.wrapDegrees(bearing - player.getYRot()));
		int size = Math.round(ARROW_SIZE * (float)(1.4 - Math.min(1.0, closeness) * 0.6));
		int tipX = cx + (int)Math.round(Math.cos(angle) * ring), tipY = cy + (int)Math.round(Math.sin(angle) * ring);
		// two arms back from the tip, 150 degrees either side of the way it points
		for (int side = -1; side <= 1; side += 2) {
			double back = angle + Math.PI + side * Math.toRadians(32.0);
			for (int i = 0; i <= size; i++) {
				int x = tipX + (int)Math.round(Math.cos(back) * i), y = tipY + (int)Math.round(Math.sin(back) * i);
				g.fill(x, y, x + 2, y + 2, color);
			}
		}
		g.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, color);
	}

	// ---- brackets ---------------------------------------------------------------------------------------------------

	/**
	 * Corner brackets round {@code entity}, sized to it; with {@code health}, its name above and its health bar under; with a
	 * {@code number}, the mark’s number beside the top corner.
	 */
	static void bracket(GuiGraphicsExtractor g, Minecraft minecraft, LivingEntity entity, int color, float partial, boolean health, Font font,
		Integer number) {
		Vec3 at = entity.getPosition(partial).add(0.0, entity.getBbHeight() * 0.5, 0.0);
		double[] p = RigHud.project(g, minecraft, at);
		if (p == null) {
			return;
		}
		int halfH = (int)Math.clamp(entity.getBbHeight() * 0.5 * p[2], 6.0, 90.0);
		int halfW = (int)Math.clamp(Math.max(entity.getBbWidth(), entity.getBbHeight() * 0.35) * 0.5 * p[2], 5.0, 70.0);
		int cx = (int)p[0], cy = (int)p[1];
		int arm = Math.max(3, Math.min(halfW, halfH) / 3);
		int l = cx - halfW, r = cx + halfW, t = cy - halfH, b = cy + halfH;
		g.fill(l, t, l + arm, t + 1, color);
		g.fill(l, t, l + 1, t + arm, color);
		g.fill(r - arm, t, r, t + 1, color);
		g.fill(r - 1, t, r, t + arm, color);
		g.fill(l, b - 1, l + arm, b, color);
		g.fill(l, b - arm, l + 1, b, color);
		g.fill(r - arm, b - 1, r, b, color);
		g.fill(r - 1, b - arm, r, b, color);
		if (number != null) {
			g.text(font, Integer.toString(number), r + 3, t, color, false);
		}
		if (health) {
			float share = Mth.clamp(entity.getHealth() / Math.max(1.0F, entity.getMaxHealth()), 0.0F, 1.0F);
			int bar = Math.min(HEALTH_WIDTH, Math.max(16, halfW * 2));
			int bx = cx - bar / 2, by = b + 3;
			g.fill(bx - 1, by - 1, bx + bar + 1, by + 4, 0x90000000);
			g.fill(bx, by, bx + Math.round(bar * share), by + 3, share > 0.5F ? SOUND : share > 0.25F ? WORN : RUINED);
			String name = entity.getDisplayName().getString();
			String hp = Math.round(entity.getHealth()) + "/" + Math.round(entity.getMaxHealth());
			g.text(font, name, cx - font.width(name) / 2, t - 10, 0xFFE0F4FF, false);
			g.text(font, hp, cx - font.width(hp) / 2, by + 5, 0xFFB0C8D0, false);
		}
	}

	// ---- the figure -------------------------------------------------------------------------------------------------

	/** The suit as a small figure: each plate in the colour of its wear. */
	private static void figure(GuiGraphicsExtractor g, LocalPlayer player, Font font, int w, int h) {
		int cx = w - FIGURE_EDGE, top = h / 2 - 6 * CELL;
		int head = plate(player, Rig.Piece.HELM), core = plate(player, Rig.Piece.CORE), arms = plate(player, Rig.Piece.ARMS),
			boots = plate(player, Rig.Piece.THRUSTERS);
		int c = CELL;
		// head, chest, shoulders and arms (the arms piece also drives the hips and thighs), shins and boots
		box(g, cx - c, top, 2 * c, 2 * c, head);
		box(g, cx - 2 * c, top + 2 * c + 1, 4 * c, 3 * c, core);
		box(g, cx - 3 * c - 1, top + 2 * c + 1, c, 3 * c, arms);
		box(g, cx + 2 * c + 1, top + 2 * c + 1, c, 3 * c, arms);
		box(g, cx - 2 * c, top + 5 * c + 2, 4 * c, c, arms);
		box(g, cx - 2 * c, top + 6 * c + 3, 2 * c - 1, 2 * c, arms);
		box(g, cx + 1, top + 6 * c + 3, 2 * c - 1, 2 * c, arms);
		box(g, cx - 2 * c, top + 8 * c + 4, 2 * c - 1, c, boots);
		box(g, cx + 1, top + 8 * c + 4, 2 * c - 1, c, boots);
		String frame = Component.translatable("rig.jaymospowers.frame." + Rig.frame(player).id()).getString();
		g.text(font, frame, cx - font.width(frame) / 2, top + 9 * c + 8, SOFT, false);
	}

	private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + h, color);
		g.outline(x, y, w, h, 0x90000000);
	}

	/** A piece’s plate colour: green sound to red ruined, grey when it is not worn. */
	private static int plate(LocalPlayer player, Rig.Piece piece) {
		ItemStack worn = player.getItemBySlot(piece.slot);
		if (piece.item() == null || !worn.is(piece.item())) {
			return MISSING;
		}
		float sound = worn.isDamageableItem() ? 1.0F - worn.getDamageValue() / (float)Math.max(1, worn.getMaxDamage()) : 1.0F;
		if (sound > 0.66F) {
			return SOUND;
		}
		return sound > 0.33F ? WORN : RUINED;
	}

	// ---- overcharge -------------------------------------------------------------------------------------------------

	/** A lightning strike has overfilled the Core: a gold frame at the edge, flickering, and a bar of what is left. */
	private static void overcharge(GuiGraphicsExtractor g, int w, int h, LocalPlayer player) {
		float left = Mth.clamp(RigClient.status.overcharge() / 240.0F, 0.0F, 1.0F);
		int alpha = player.tickCount % 3 == 0 ? 70 : 130;
		int color = ARGB.color(alpha, OVER);
		g.fill(0, 0, w, 2, color);
		g.fill(0, h - 2, w, h, color);
		g.fill(0, 2, 2, h - 2, color);
		g.fill(w - 2, 2, w, h - 2, color);
		int bar = w / 5;
		g.fill(w / 2 - bar / 2, 14, w / 2 + bar / 2, 17, 0x80000000);
		g.fill(w / 2 - bar / 2, 14, w / 2 - bar / 2 + Math.round(bar * left), 17, OVER);
	}

	/** The marked creatures of a missile volley, numbered. */
	static void marks(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, List<Integer> ids, float partial) {
		for (int i = 0; i < ids.size(); i++) {
			if (player.level().getEntity(ids.get(i)) instanceof LivingEntity living && living.isAlive()) {
				bracket(g, minecraft, living, 0xFF000000 | RigScanClient.LOCK_COLOR, partial, false, minecraft.font, i + 1);
			}
		}
	}

	/** The frame the worn suit is (for the HUD’s other readers). */
	static RigFrame frame(LocalPlayer player) {
		return Rig.frame(player);
	}
}
