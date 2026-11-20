package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ForestFireCompat;
import com.jaymoskingdoms.client.hud.HudStyle;
import com.jaymoskingdoms.client.hud.HudZones;
import com.peterwolf.forestfire.client.hud.FirefighterHud;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.NozzleHudPayload;
import com.peterwolf.forestfire.network.WindSyncPayload;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (ff-kit) Peterwolf's firefighter readout, fitted to the HUD layout (docs/HUD-Layout.md): its own panel sat at the top
 * left of every screen (the fire, the wind and the hose, in fixed English, with an eight-second dedication line on
 * joining) whenever any fire burned anywhere in the world. Ours takes its place: the same readings on one plate on the
 * top-left rail, row 50 (standing status, under the party), and only while you carry the kit: a piece of its gear in
 * either hand or worn, or a hose run to your nozzle. The title screen's dedication stays as Peterwolf wrote it.
 */
public final class ForestFireHud {
	static final Identifier THEIRS = Identifier.fromNamespaceAndPath(ForestFireCompat.MOD, "firefighter_hud");
	static final String ID = "jaymoskingdoms:forest_fire";
	static final int ROW = 50;
	/** Text lines and the plate's padding (gui px). */
	static final int LINE = 10, PAD = 3;
	/** Fire red for the incident's name, the water's blue for the hose. */
	static final int FIRE = 0xFFFF8A4A, WATER = 0xFF6FB8FF;

	private static @Nullable Field incident, wind, nozzle;

	private ForestFireHud() {
	}

	static void register() {
		if (!ForestFireCompat.LOADED) {
			return;
		}
		try {
			incident = field("incident");
			wind = field("wind");
			nozzle = field("nozzle");
		} catch (ReflectiveOperationException e) {
			return;
		}
		// after every mod's client entrypoint, so its panel is there to be replaced
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			try {
				HudElementRegistry.replaceElement(THEIRS, theirs -> ForestFireHud::draw);
			} catch (RuntimeException ignored) {
				// its panel isn't registered under that name any more: leave it be
			}
		});
	}

	private static Field field(String name) throws ReflectiveOperationException {
		Field f = FirefighterHud.class.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || incident == null || wind == null || nozzle == null) {
			return;
		}
		IncidentHudPayload fire;
		WindSyncPayload air;
		NozzleHudPayload hose;
		try {
			fire = (IncidentHudPayload) incident.get(null);
			air = (WindSyncPayload) wind.get(null);
			hose = (NozzleHudPayload) nozzle.get(null);
		} catch (IllegalAccessException | ClassCastException e) {
			return;
		}
		boolean piped = hose != null && hose.connected();
		if (!piped && !kitted(player)) {
			HudZones.clear(HudZones.RAIL, ID);
			return;
		}
		List<Line> lines = new ArrayList<>();
		if (fire != null && fire.incidentId() > 0) {
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.incident", fire.name(), status(fire.status())), FIRE));
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.burning", fire.burningBlocks(),
				Math.round(fire.containment())), HudStyle.TEXT));
		}
		if (air != null) {
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.wind", compass(air.yawDegrees()),
				Math.round(air.speed() * 100.0F)), HudStyle.DIM));
		}
		if (piped) {
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.nozzle", plain(hose.mode()),
				Component.translatable(hose.flowActive() ? "hud.jaymoskingdoms.forest_fire.open" : "hud.jaymoskingdoms.forest_fire.shut")), WATER));
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.pressure", Math.round(hose.pressure01() * 100.0F)),
				hose.pressure01() < 0.35F ? HudStyle.WARN : HudStyle.TEXT));
			lines.add(new Line(Component.translatable("hud.jaymoskingdoms.forest_fire.hose", tension(hose.tension()), hose.remainingDistance()),
				"MAXIMUM".equals(hose.tension()) ? HudStyle.WARN : HudStyle.DIM));
		}
		if (lines.isEmpty()) {
			HudZones.clear(HudZones.RAIL, ID);
			return;
		}
		Font font = mc.font;
		int width = 0;
		for (Line l : lines) {
			width = Math.max(width, font.width(l.text()));
		}
		int[] at = HudZones.place(HudZones.RAIL, ID, ROW, width + PAD * 2, lines.size() * LINE + PAD * 2 - 1);
		if (at == null) {
			return;
		}
		HudStyle.plate(graphics, at[0], at[1], width + PAD * 2, lines.size() * LINE + PAD * 2 - 1, HudStyle.PLATE_SOFT, 0);
		int y = at[1] + PAD;
		for (Line l : lines) {
			graphics.text(font, l.text(), at[0] + PAD, y, l.color(), true);
			y += LINE;
		}
	}

	private record Line(Component text, int color) {
	}

	/** A piece of its gear in either hand or worn. */
	private static boolean kitted(LocalPlayer player) {
		if (kit(player.getMainHandItem()) || kit(player.getOffhandItem())) {
			return true;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (kit(player.getItemBySlot(slot))) {
				return true;
			}
		}
		return false;
	}

	private static boolean kit(ItemStack stack) {
		return !stack.isEmpty() && ForestFireCompat.MOD.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
	}

	/** Its incident status (REPORTED, ACTIVE, CONTAINED...) in our words. */
	private static Component status(String status) {
		return Component.translatableWithFallback("hud.jaymoskingdoms.forest_fire.status." + status.toLowerCase(Locale.ROOT), plain(status));
	}

	/** Its hose tension (SLACK, NORMAL, TIGHT, MAXIMUM) in our words. */
	private static Component tension(String tension) {
		return Component.translatableWithFallback("hud.jaymoskingdoms.forest_fire.tension." + tension.toLowerCase(Locale.ROOT), plain(tension));
	}

	/** Where the wind blows from: its yaw is the way it blows toward (Minecraft's: 0 south, 90 west). */
	private static Component compass(float yaw) {
		String[] dirs = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
		// it blows toward (-sin, cos), so it comes from (sin, -cos): a compass bearing (clockwise from north, -z) of yaw itself
		float bearing = ((yaw % 360.0F) + 360.0F) % 360.0F;
		int i = Math.round(bearing / 45.0F) % 8;
		return Component.translatable("disaster.jaymoskingdoms.dir." + dirs[i]);
	}

	private static String plain(String raw) {
		String s = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
