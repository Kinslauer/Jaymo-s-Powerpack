package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.power.zfighter.ZFighterPartner;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Find your partner, on the carrier's screen: the server says where the other earring's carrier is
 * ({@link ZFighterPartner#MARK}). Within {@link ZFighterPartner#OUTLINE_RANGE} blocks, and in view of the client, the partner gets a
 * faint gold outline through walls (two ki-sense mixins ask {@link #color}); further away, or not loaded here, a small arrow and the
 * distance on the HUD point the way.
 */
public final class ZFighterPartnerClient {
	/** The partner's outline colour. */
	static final int OUTLINE = 0xE8C860;
	/** The HUD mark sits this far down from the top of the screen. */
	static final int TOP = 22;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private ZFighterPartnerClient() {
	}

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("zfighter_partner"), ZFighterPartnerClient::draw);
	}

	private static ZFighterPartner.Mark mark() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getAttached(ZFighterPartner.MARK);
	}

	/** The outline colour {@code entity} gets on this screen for being the paired partner within range, or 0 for none. */
	public static int color(Entity entity) {
		ZFighterPartner.Mark mark = mark();
		LocalPlayer player = Minecraft.getInstance().player;
		if (mark == null || player == null || entity == player || !entity.getUUID().equals(mark.partner()) || !entity.isAlive()
			|| entity.distanceToSqr(player) > ZFighterPartner.OUTLINE_RANGE * ZFighterPartner.OUTLINE_RANGE) {
			return 0;
		}
		return OUTLINE;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ZFighterPartner.Mark mark = mark();
		if (player == null || mark == null || minecraft.level == null) {
			return;
		}
		double dx = mark.x() - player.getX(), dz = mark.z() - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz + Math.pow(mark.y() - player.getY(), 2));
		// close and loaded here: the outline is enough
		if (distance <= ZFighterPartner.OUTLINE_RANGE && minecraft.level.getPlayerByUUID(mark.partner()) != null) {
			return;
		}
		// the way to him, relative to where the player faces (up is straight ahead)
		float toward = (float)(Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		float relative = Mth.wrapDegrees(toward - player.getYRot());
		String arrow = ARROWS[Math.floorMod(Math.round(relative / 45.0F), 8)];
		Component label = Component.translatable("hud.jaymospowers.zfighter_partner", arrow, Math.round(distance));
		int w = minecraft.font.width(label) + 8;
		int x = graphics.guiWidth() / 2 - w / 2;
		graphics.fill(x, TOP, x + w, TOP + 13, HudStyle.PLATE_SOFT);
		graphics.text(minecraft.font, label, x + 4, TOP + 3, HudStyle.GOLD, true);
	}
}
