package com.jaymospowers.client.wounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.core.wounds.HurtCause;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Why you're hurt, on your screen ({@link HurtCause}): every hurt puts a short red line under the crosshair in your
 * character's voice ("The sun! My skin's rotting...", "Maiko hit me!"), and one from somewhere puts a red mark round
 * the crosshair on that side (top: in front of you, bottom: behind), outside the incoming-blow dots. The same cause again
 * keeps its line up instead of adding another; a few causes at once stack, newest on top. Shown downed too. Like the hurt
 * flash, it's up only while it matters, so Auto HUD's hiding never keeps it from you.
 */
public final class HurtCauseHud {
	/** A line stays this long after its last hurt, the last {@link #FADE_TICKS} of it fading (ticks). */
	static final int LINE_TICKS = 70, FADE_TICKS = 15;
	/** At most this many lines at once (the oldest go). */
	static final int MAX_LINES = 3;
	/** The lines' row in the zone under the crosshair ({@link HudZones#BELOW}: after the hold bars' 21), and how far apart they are (pixels). */
	static final int ROW = 30, LINE_GAP = 10;
	/** Their colour. */
	static final int LINE_RGB = 0xFF5050;
	/** A side mark stays this long (ticks), and sits this far out from the crosshair, this wide (degrees) and this thick (pixels). */
	static final int MARK_TICKS = 40;
	static final float MARK_RADIUS = 46.0F, MARK_SPREAD = 26.0F;
	static final int MARK_DOTS = 9, MARK_SIZE = 2, MARK_RGB = 0xE01818;
	/** Vanilla damage types whose own line says nothing: the feature that dealt one names it instead. */
	static final Set<String> UNSPECIFIC = Set.of("generic", "magic", "genericKill", "indirectMagic");

	private static final String KEY = "hurt_cause.jaymospowers.";

	private static final class Line {
		Component text;
		String said;
		long at;
	}

	private record Mark(int attacker, Vec3 at, long since) {
	}

	private static final List<Line> LINES = new ArrayList<>();
	private static final List<Mark> MARKS = new ArrayList<>();
	private static long now;

	private HurtCauseHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HurtCause.Hurt.TYPE, (payload, context) -> received(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LINES.clear();
			MARKS.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("hurt_cause"), HurtCauseHud::draw);
	}

	private static void received(HurtCause.Hurt hurt) {
		Component text = line(hurt);
		String said = text.getString();
		Line line = null;
		for (Iterator<Line> it = LINES.iterator(); it.hasNext(); ) {
			Line l = it.next();
			if (l.said.equals(said)) {
				line = l;
				it.remove();
				break;
			}
		}
		if (line == null) {
			line = new Line();
			line.text = text;
			line.said = said;
		}
		line.at = now;
		LINES.add(0, line);
		while (LINES.size() > MAX_LINES) {
			LINES.remove(LINES.size() - 1);
		}
		if (hurt.at().isPresent()) {
			MARKS.removeIf(mark -> hurt.attacker() >= 0 && mark.attacker() == hurt.attacker());
			MARKS.add(new Mark(hurt.attacker(), hurt.at().get(), now));
		}
	}

	/**
	 * What the character says: who did it, when someone did; else the feature of ours that dealt it
	 * ({@code from.<Class>}); else the damage type's own line; else the type (or the feature) as words.
	 */
	static Component line(HurtCause.Hurt hurt) {
		Language language = Language.getInstance();
		if (hurt.who().isPresent()) {
			String typed = KEY + "by." + hurt.kind();
			String key = language.has(typed) ? typed : KEY + (hurt.shot() ? "shot_by" : "hit_by");
			return Component.translatable(key, hurt.who().get());
		}
		boolean unspecific = UNSPECIFIC.contains(hurt.kind());
		// (one of our own damage types names itself; a vanilla one a feature borrowed is named by the feature)
		if (!hurt.from().isEmpty() && !hurt.kind().startsWith("jaymos")) {
			String from = KEY + "from." + hurt.from();
			if (language.has(from)) {
				return Component.translatable(from);
			}
			if (unspecific) {
				return Component.translatable(KEY + "unnamed", HurtCause.plain(hurt.from()));
			}
		}
		String own = KEY + hurt.kind();
		return language.has(own) ? Component.translatable(own) : Component.translatable(KEY + "unnamed", HurtCause.plain(hurt.kind()));
	}

	private static void tick() {
		now++;
		LINES.removeIf(line -> now - line.at > LINE_TICKS);
		MARKS.removeIf(mark -> now - mark.since() > MARK_TICKS);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || player.isSpectator() || LINES.isEmpty() && MARKS.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		Font font = minecraft.font;
		if (!LINES.isEmpty()) {
			int width = 0;
			for (Line line : LINES) {
				width = Math.max(width, font.width(line.text));
			}
			// (the zone under the crosshair, after the hold bars: it keeps clear of the bars, the guard and the rest)
			int[] at = HudZones.place(HudZones.BELOW, "jaymospowers:hurt_cause", ROW, width, LINES.size() * LINE_GAP - 1);
			if (at != null) {
				int mid = at[0] + width / 2, y = at[1];
				for (Line line : LINES) {
					int alpha = alpha(now - line.at + partial, LINE_TICKS);
					if (alpha > 8) {
						graphics.text(font, line.text, mid - font.width(line.text) / 2, y, alpha << 24 | LINE_RGB, true);
					}
					y += LINE_GAP;
				}
			}
		}
		if (HudZones.hidden()) {
			return;
		}
		float yaw = player.getViewYRot(partial);
		Vec3 eye = player.getEyePosition(partial);
		for (Mark mark : MARKS) {
			// (one who did it and is still about: the mark follows them; else where it came from)
			Entity by = mark.attacker() >= 0 ? minecraft.level.getEntity(mark.attacker()) : null;
			Vec3 to = (by != null ? by.getEyePosition(partial) : mark.at()).subtract(eye);
			if (to.horizontalDistanceSqr() < 1.0E-4) {
				continue;
			}
			float bearing = (float) Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F - yaw;
			int alpha = alpha(now - mark.since() + partial, MARK_TICKS);
			for (int i = 0; i < MARK_DOTS; i++) {
				float off = (i - (MARK_DOTS - 1) / 2.0F) / ((MARK_DOTS - 1) / 2.0F);
				float angle = (float) Math.toRadians(bearing + off * MARK_SPREAD / 2.0F);
				// (thickest in the middle, a crescent)
				float radius = MARK_RADIUS - (1.0F - Math.abs(off)) * 2.0F;
				int x = Math.round(cx + Mth.sin(angle) * radius), my = Math.round(cy - Mth.cos(angle) * radius);
				int grow = Math.abs(off) < 0.3F ? 1 : 0;
				graphics.fill(x - 1 - grow, my - 1 - grow, x + MARK_SIZE + 1 + grow, my + MARK_SIZE + 1 + grow, (alpha * 3 / 5) << 24);
				graphics.fill(x - grow, my - grow, x + MARK_SIZE + grow, my + MARK_SIZE + grow, alpha << 24 | MARK_RGB);
			}
		}
	}

	/** Full until the last {@link #FADE_TICKS} of {@code length}, then fading out. */
	private static int alpha(float age, int length) {
		float left = length - age;
		return Math.round(255.0F * Mth.clamp(left / FADE_TICKS, 0.0F, 1.0F));
	}
}
