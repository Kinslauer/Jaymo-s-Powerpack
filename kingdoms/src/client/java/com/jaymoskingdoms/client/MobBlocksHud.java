package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MobBlocks;
import java.lang.invoke.MethodHandle;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (defence disciplines) An enemy's passive blocks ({@link MobBlocks}), shown only while you're locked onto it (Jaymo's
 * Powers' lock-on, asked by reflection), to keep the screen clear: a row of small shield pips just above the crosshair in
 * its discipline's colour (steel grey, ki gold, rune blue), greyed out as you break them.
 */
public final class MobBlocksHud {
	/** A pip's size and the gap between pips, and how far above the crosshair the row sits (gui pixels). */
	static final int PIP = 5, GAP = 2, ABOVE = 14;
	static final int[] COLORS = {0xFFB4B4BC, 0xFFE8B028, 0xFF9CC8FF};
	static final int SPENT = 0xFF3A3A40, RIM = 0xC0000000;

	private static @Nullable MethodHandle locked;
	private static boolean lookedForLocked;

	private MobBlocksHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("mob_blocks"), MobBlocksHud::draw);
	}

	private static @Nullable LivingEntity locked() {
		if (!lookedForLocked) {
			lookedForLocked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					locked = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.LockOnClient"), "locked",
						java.lang.invoke.MethodType.methodType(LivingEntity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.debug("No lock-on to show enemies' blocks with", e);
				}
			}
		}
		if (locked == null) {
			return null;
		}
		try {
			return (LivingEntity)locked.invoke();
		} catch (Throwable e) {
			return null;
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		LivingEntity target = locked();
		Integer packed = target == null ? null : target.getAttached(MobBlocks.SHOWN);
		if (packed == null) {
			return;
		}
		int left = packed & 7, total = packed >> 3 & 7, kind = Math.min(2, packed >> 6 & 3);
		if (total <= 0) {
			return;
		}
		int width = total * PIP + (total - 1) * GAP;
		int x = graphics.guiWidth() / 2 - width / 2, y = graphics.guiHeight() / 2 - ABOVE - PIP;
		for (int i = 0; i < total; i++) {
			int px = x + i * (PIP + GAP);
			graphics.fill(px - 1, y - 1, px + PIP + 1, y + PIP + 1, RIM);
			graphics.fill(px, y, px + PIP, y + PIP, i < left ? COLORS[kind] : SPENT);
		}
	}
}
