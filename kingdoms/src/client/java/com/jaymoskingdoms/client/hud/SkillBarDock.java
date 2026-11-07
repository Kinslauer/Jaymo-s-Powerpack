package com.jaymoskingdoms.client.hud;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * (0.7.550) Andy: "more UI overlap with the rpg skills". Spell Engine (the RPG Series' skill bar: class skills and, since
 * 0.7.550, Weapon Forms) draws its bar at a fixed spot left of the hotbar, which is where our left dock stacks the body row
 * (stance, wounds doll, body heat), so the two drew over each other. Now the bar is the left dock's lowest row
 * ({@link #ROW}): {@link HudZones} gives it its place each frame (its slots plus the key labels over them) and Spell Engine's
 * HUD setting is pointed there, so the body row and the rest stack above it. During a film it goes off screen with the rest.
 * Spell Engine's own HUD editor can't move the bar any more (this sets it every frame).
 */
public final class SkillBarDock {
	/** The left dock's row: under the stomach (10), valor (15), symbiote arm (20) and body row (30). */
	static final int ROW = 5;
	/** Spell Engine's slot: 20 wide, the bar 22 tall; its key labels sit this far over the slots. */
	static final int SLOT_W = 20, BAR_H = 22, KEYS_H = 11;
	static final String ID = "spell_engine:skill_bar";

	private SkillBarDock() {
	}

	public static void register() {
		if (FabricLoader.getInstance().isModLoaded("spell_engine")) {
			HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, JaymosKingdoms.id("skill_bar_dock"), Hooks::place);
		}
	}

	/** Kept apart so Spell Engine's classes load only when it's there. */
	private static final class Hooks {
		/** Spell Engine's {@code hudConfig} (a tiny_config ConfigManager, read by reflection: that library is nested in its jar) and its {@code value}. */
		private static java.lang.reflect.@org.jspecify.annotations.Nullable Field managerField, valueField;
		private static boolean broken;

		static void place(GuiGraphicsExtractor graphics, DeltaTracker delta) {
			net.spell_engine.client.input.SpellHotbar hotbar = net.spell_engine.client.input.SpellHotbar.INSTANCE;
			net.spell_engine.config.HudConfig config = config();
			if (hotbar == null || config == null) {
				return;
			}
			int slots = hotbar.slots == null ? 0 : hotbar.slots.size();
			if (slots <= 0) {
				HudZones.clear(HudZones.DOCK_LEFT, ID);
				return;
			}
			int w = slots * SLOT_W;
			int[] at = HudZones.place(HudZones.DOCK_LEFT, ID, ROW, w, KEYS_H + BAR_H);
			int guiW = graphics.guiWidth(), guiH = graphics.guiHeight();
			// Spell Engine centres the bar on its origin (the bottom middle of the screen) plus this offset; a film sends it off screen
			float x = at == null ? -guiW : at[0] + w / 2.0F - guiW / 2.0F;
			float y = at == null ? 0.0F : at[1] + KEYS_H + BAR_H / 2.0F - guiH;
			config.hotbar = new net.spell_engine.client.gui.HudElement(net.spell_engine.client.gui.HudElement.Origin.BOTTOM,
				new net.minecraft.world.phys.Vec2(x, y));
		}

		private static net.spell_engine.config.@org.jspecify.annotations.Nullable HudConfig config() {
			if (broken) {
				return null;
			}
			try {
				if (managerField == null) {
					managerField = net.spell_engine.client.SpellEngineClient.class.getField("hudConfig");
				}
				Object manager = managerField.get(null);
				if (manager == null) {
					return null;
				}
				if (valueField == null) {
					valueField = manager.getClass().getField("value");
				}
				return valueField.get(manager) instanceof net.spell_engine.config.HudConfig config ? config : null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				broken = true;
				JaymosKingdoms.LOGGER.warn("Spell Engine's HUD setting couldn't be read; its skill bar stays where it is", e);
				return null;
			}
		}
	}
}
