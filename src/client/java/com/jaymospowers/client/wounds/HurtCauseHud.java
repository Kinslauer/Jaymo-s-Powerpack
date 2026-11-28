package com.jaymospowers.client.wounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.blood.BloodScreen;
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
 * Why you're hurt, on your screen ({@link HurtCause}), told once and kept short. Andy (2026-11-28): "the hit indicators
 * should only tell you once if you are hit and that should be at the very start of combat. it should NOT be naming the
 * attackers, nor should it be as descriptive. 'im hit' instead of a skeleton got me from afar."
 * <ul>
 * <li>A blow from someone or something (a swing, an arrow, a bite, a blast): "I'm hit!" under the crosshair, and a red
 * mark round the crosshair on the side it came from (top: in front of you, bottom: behind), only for the first blow of a
 * fight: one after {@link #FIGHT_GAP_TICKS} with no blow. The blows after it in that fight show nothing.</li>
 * <li>A hurt nobody dealt (the sun, fire, poison, hunger, cold, a fall, drowning): its own short line, in your
 * character's voice, once when it starts; the same cause shows again only after {@link #CAUSE_GAP_TICKS} without it.
 * So a player can always tell why they're hurt, without a line on every tick of it.</li>
 * </ul>
 * Shown downed too. Like the hurt flash, it's up only while it matters, so Auto HUD's hiding never keeps it from you.
 */
public final class HurtCauseHud {
	/** A line stays this long, the last {@link #FADE_TICKS} of it fading (ticks). */
	static final int LINE_TICKS = 70, FADE_TICKS = 15;
	/** At most this many lines at once (the oldest go). */
	static final int MAX_LINES = 2;
	/** A blow after this long with none (ticks: 20 s) starts a new fight, and says "I'm hit!" again. */
	static final int FIGHT_GAP_TICKS = 400;
	/** A cause that's let up this long (ticks: 10 s) is said again when it comes back. */
	static final int CAUSE_GAP_TICKS = 200;
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
	/**
	 * Our features that deal a blow without saying who struck it (an untyped hurt with no attacker): these count as a
	 * fight's blow ("I'm hit!" once), not a cause of their own, so no line names the colossus or the goblin that did it.
	 */
	static final Set<String> BLOW_FEATURES = Set.of("Carousing", "AetherShot", "NadirSnap", "WindKick", "StoneSwords", "RelicWeapons",
		"ClawArts", "InvokerFangs", "GolemRock", "ElementSpells", "Slaves", "Colossi", "KingWeapons", "Farmers", "DrunkGoblins",
		"Throwables", "ThrowingCards", "FluxWisp", "MatrixEntity", "AirCombo", "RootSpikeEntity", "GravityMeteor", "Grue",
		"HeritagePaths", "ZFighterKiThrow", "WebSpit", "VenomSpit");

	private static final String KEY = "hurt_cause.jaymospowers.";

	private record Line(Component text, long at) {
	}

	private record Mark(int attacker, Vec3 at, long since) {
	}

	private static final List<Line> LINES = new ArrayList<>();
	private static final List<Mark> MARKS = new ArrayList<>();
	/** When each cause (by its line) last hurt, and when the last blow of a fight landed. */
	private static final Map<String, Long> CAUSES = new HashMap<>();
	private static long lastBlow = Long.MIN_VALUE / 2;
	private static long now;

	private HurtCauseHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HurtCause.Hurt.TYPE, (payload, context) -> received(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LINES.clear();
			MARKS.clear();
			CAUSES.clear();
			lastBlow = Long.MIN_VALUE / 2;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("hurt_cause"), HurtCauseHud::draw);
	}

	private static void received(HurtCause.Hurt hurt) {
		Component text;
		if (blow(hurt)) {
			// every blow that hurts throws its blood on the screen, on the side it came from (told or not)
			LocalPlayer self = Minecraft.getInstance().player;
			if (self != null) {
				BloodScreen.blow(hurt.amount(), self.getMaxHealth(), hurt.at().orElse(null));
			}
			boolean starts = now - lastBlow > FIGHT_GAP_TICKS;
			lastBlow = now;
			if (!starts) {
				return;
			}
			text = Component.translatable(KEY + "hit");
		} else {
			text = line(hurt);
			Long last = CAUSES.put(text.getString(), now);
			if (last != null && now - last <= CAUSE_GAP_TICKS) {
				return;
			}
		}
		String said = text.getString();
		LINES.removeIf(line -> line.text().getString().equals(said));
		LINES.add(0, new Line(text, now));
		while (LINES.size() > MAX_LINES) {
			LINES.remove(LINES.size() - 1);
		}
		if (hurt.at().isPresent()) {
			MARKS.clear();
			MARKS.add(new Mark(hurt.attacker(), hurt.at().get(), now));
		}
	}

	/** A blow from someone or something: a fight's, told once as "I'm hit!". */
	static boolean blow(HurtCause.Hurt hurt) {
		return hurt.fight() || !hurt.kind().startsWith("jaymos") && BLOW_FEATURES.contains(hurt.from());
	}

	/**
	 * What the character says of a hurt nobody dealt: the feature of ours that dealt it ({@code from.<Class>}); else the
	 * damage type's own line; else the type (or the feature) as words.
	 */
	static Component line(HurtCause.Hurt hurt) {
		Language language = Language.getInstance();
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
		LINES.removeIf(line -> now - line.at() > LINE_TICKS);
		CAUSES.values().removeIf(at -> now - at > CAUSE_GAP_TICKS);
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
				width = Math.max(width, font.width(line.text()));
			}
			// (the zone under the crosshair, after the hold bars: it keeps clear of the bars, the guard and the rest)
			int[] at = HudZones.place(HudZones.BELOW, "jaymospowers:hurt_cause", ROW, width, LINES.size() * LINE_GAP - 1);
			if (at != null) {
				int mid = at[0] + width / 2, y = at[1];
				for (Line line : LINES) {
					int alpha = alpha(now - line.at() + partial, LINE_TICKS);
					if (alpha > 8) {
						graphics.text(font, line.text(), mid - font.width(line.text()) / 2, y, alpha << 24 | LINE_RGB, true);
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
