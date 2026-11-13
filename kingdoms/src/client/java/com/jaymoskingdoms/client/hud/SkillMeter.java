package com.jaymoskingdoms.client.hud;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.client.mixin.tweaks.BossHealthOverlayAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "the meter dropping in and then back out after filling with the earned xp ... it should animate as grinding or
 * burning through whenever it increases, and play a low toast at the level up."
 *
 * <p>The experience meter: whenever a skill pillar ({@link Skills}) or a job ({@link Careers}) gains experience, its bar
 * drops in at the top of the screen with its name and level, the fill burns along to the new amount (a white-hot front
 * throwing sparks back, the fresh part glowing and cooling to the track's colour), waits a moment and slides back out.
 * A level reached flashes the bar, starts it again from empty, and puts a small toast under it with a low, quiet chime.
 *
 * <p>Nothing is sent for it: both tracks are attachments already synced to their own player, so the meter watches them
 * each tick and shows whatever went up. One meter shows at a time; gains in another track wait their turn.
 */
public final class SkillMeter {
	/** The bar: width (the boss bar's), height, and the gap from the screen's top (below any boss bars). */
	static final int BAR_W = 182, BAR_H = 5, TOP = 6;
	/** Vanilla's boss bars: the first one's top and each one's height (BossHealthOverlay). */
	static final int BOSS_TOP = 12, BOSS_EACH = 19;
	/** Seconds: the drop in and slide out, the wait once filled, the flash at a level, how long a toast stays, its fade. */
	static final float SLIDE = 0.28F, HOLD = 1.6F, FLASH = 0.4F, TOAST = 3.6F, TOAST_FADE = 0.4F;
	/** The fill: a gain takes about this long (seconds), but never crawls slower than this share of the bar a second. */
	static final float FILL_TIME = 0.8F, FILL_MIN_SPEED = 0.3F;
	/**
	 * The fresh part of the fill cools at this share of the bar a second: slowly while the front is still moving, so the
	 * whole gain glows, then quickly; the front's heat dies in this long (seconds).
	 */
	static final float COOL_BURNING = 0.06F, COOL_SPEED = 0.45F, HEAT_FADE = 0.35F;
	/** Sparks off the front: how many, how far back they fly and how high (gui pixels). */
	static final int SPARKS = 10;
	static final float SPARK_BACK = 24.0F, SPARK_UP = 10.0F;
	/** The burn's colours: the white-hot front, the glow behind it. */
	static final int HOT = 0xFFF6D8, GLOW = 0xFFB040;
	/** Jobs' colour (a guild's brass). */
	static final int JOB_COLOR = 0xC9A24A;
	/** The level-up chime: vanilla's toast, pitched low and kept quiet. */
	static final float CHIME_PITCH = 0.55F, CHIME_VOLUME = 0.45F;
	/** After joining, respawning or changing world, changes for this many ticks are the save arriving, not gains. */
	static final int SETTLE_TICKS = 60;

	/** One track's meter: where the fill is drawn, where it's headed (level + share of the next), and how it reads. */
	private static final class Track {
		final String id;
		final int color;
		/** The highest level: there the bar is full and stays so. */
		final int max;
		final IntFunction<Component> label;
		/** The toast for reaching a level (its lines), or null for none (a milestone has its own fanfare). */
		final IntFunction<@Nullable List<Component>> toast;
		float shown, target;
		/** The share of the bar where the still-glowing part starts. */
		float coolFrom;

		Track(String id, int color, int max, IntFunction<Component> label, IntFunction<@Nullable List<Component>> toast, float from, float to) {
			this.id = id;
			this.color = color;
			this.max = max;
			this.label = label;
			this.toast = toast;
			this.shown = from;
			this.target = to;
			this.coolFrom = share(from);
		}

		float share(float position) {
			return position >= this.max ? 1.0F : position - (float)Math.floor(position);
		}
	}

	private static @Nullable WeakReference<LocalPlayer> watched;
	private static int settle;
	/** Track id -> the experience last seen. */
	private static final Map<String, Integer> LAST = new HashMap<>();
	private static @Nullable Track current;
	private static final LinkedHashMap<String, Track> WAITING = new LinkedHashMap<>();
	/** 0 hidden above the screen, 1 in place. */
	private static float slide;
	/** Seconds left: the wait before sliding out, a level's flash, the front's heat, the toast. */
	private static float hold, flash, heat, toastLeft;
	private static List<Component> toastLines = List.of();
	private static long lastFrame;

	private SkillMeter() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SkillMeter::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("skill_meter"), SkillMeter::hud);
	}

	// ---- watching ----------------------------------------------------------------------------------------------

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			watched = null;
			LAST.clear();
			current = null;
			WAITING.clear();
			slide = 0.0F;
			toastLeft = 0.0F;
			return;
		}
		if (watched == null || watched.get() != player) {
			watched = new WeakReference<>(player);
			settle = SETTLE_TICKS;
		}
		boolean settling = settle > 0;
		if (settling) {
			settle--;
		}
		Skills.Progress progress = Skills.of(player);
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			int xp = progress.xp(pillar);
			Integer was = LAST.put("skill." + pillar.key(), xp);
			if (!settling && was != null && xp > was) {
				gained(pillar, was, xp);
			}
		}
		Careers.Career career = Careers.of(player);
		for (Map.Entry<String, Integer> e : career.xp().entrySet()) {
			Integer was = LAST.put("job." + e.getKey(), e.getValue());
			Careers.Job job = Careers.Job.of(e.getKey());
			if (!settling && job != null && e.getValue() > (was == null ? 0 : was)) {
				gained(job, was == null ? 0 : was, e.getValue());
			}
		}
	}

	private static float position(Skills.Pillar pillar, int xp) {
		int level = Skills.level(xp, pillar.maxLevel);
		return level >= pillar.maxLevel ? level : level + (xp - Skills.total(level)) / (float)Skills.toNext(level);
	}

	private static float position(int jobXp) {
		int rank = Careers.rankFor(jobXp);
		int next = Careers.nextRankXp(rank);
		if (next < 0) {
			return rank;
		}
		int floor = rank == 0 ? 0 : Careers.nextRankXp(rank - 1);
		return rank + Mth.clamp((jobXp - floor) / (float)(next - floor), 0.0F, 0.999F);
	}

	private static void gained(Skills.Pillar pillar, int was, int xp) {
		Component name = Component.translatable("skill.jaymoskingdoms." + pillar.key());
		show("skill." + pillar.key(), pillar.color, pillar.maxLevel,
			level -> Component.translatable("hud.jaymoskingdoms.skill_meter.level", name,
				Component.translatable("hud.jaymoskingdoms.skill_meter.lv", level).withColor(HudStyle.mix(pillar.color, 0xFFFFFF, 0.35F))),
			level -> {
				// (every fifth level and the top have the big fanfare: the meter only flashes for those)
				if (level >= pillar.maxLevel || level % Skills.MILESTONE_EVERY == 0) {
					return null;
				}
				return List.of(Component.translatable("hud.jaymoskingdoms.skill_meter.level_up", name, level),
					pillar.web() ? Component.translatable("hud.jaymoskingdoms.skill_meter.point", Component.keybind("key.jaymoskingdoms.skills"))
						: Component.translatable("hud.jaymoskingdoms.skill_meter.point_journal"));
			}, position(pillar, was), position(pillar, xp));
	}

	private static void gained(Careers.Job job, int was, int xp) {
		show("job." + job.key, JOB_COLOR, Careers.MASTER, rank -> Careers.title(job, rank),
			rank -> List.of(Component.translatable("hud.jaymoskingdoms.skill_meter.promoted", Careers.title(job, rank)),
				Component.translatable("job.jaymoskingdoms." + job.key + ".perk." + rank)),
			position(was), position(xp));
	}

	/**
	 * Shows a gain on the meter: the track {@code id} went from {@code from} to {@code to}, each a level plus the share of
	 * the way to the next (3.25: level 3, a quarter filled). {@code label} names the track at a level; {@code toast} gives
	 * the lines for reaching one (null: no toast).
	 */
	public static void show(String id, int color, int maxLevel, IntFunction<Component> label, IntFunction<@Nullable List<Component>> toast, float from,
		float to) {
		if (to <= from) {
			return;
		}
		if (current != null && current.id.equals(id)) {
			current.target = Math.max(current.target, to);
			hold = HOLD;
			return;
		}
		Track waiting = WAITING.get(id);
		if (waiting != null) {
			waiting.target = Math.max(waiting.target, to);
			return;
		}
		WAITING.put(id, new Track(id, color & 0xFFFFFF, maxLevel, label, toast, from, to));
	}

	// ---- drawing -----------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1.0e9F);
		lastFrame = now;
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		advance(dt);
		Track track = current;
		// (a film hides the HUD: the meter keeps its place in time and shows again after)
		if (track == null || slide <= 0.0F || HudZones.hidden()) {
			return;
		}
		Font font = minecraft.font;
		int level = Math.min(track.max, (int)Math.floor(track.shown + 1.0e-4F));
		Component label = track.label.apply(level);
		int labelW = font.width(label);
		int cx = graphics.guiWidth() / 2;
		int top = restTop(minecraft);
		// (dropping in: from just above the screen's edge, easing to a stop)
		float eased = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
		int pieceH = 9 + 2 + BAR_H + 1;
		int y = Math.round(Mth.lerp(eased, -pieceH - 2, top));
		int plateW = Math.max(BAR_W, labelW) + 8;
		HudStyle.plate(graphics, cx - plateW / 2, y - 2, plateW, pieceH + 4, HudStyle.PLATE_SOFT, 0);
		HudStyle.centred(graphics, font, label, cx, y, HudStyle.TEXT);
		int bx = cx - BAR_W / 2, by = y + 11;
		bar(graphics, track, bx, by, now);
		toast(graphics, font, cx, y + pieceH + 4);
	}

	/** Moves the meter on by {@code dt} seconds: the slide, the fill, the level-ups, the wait, the next track. */
	private static void advance(float dt) {
		toastLeft = Math.max(0.0F, toastLeft - dt);
		heat = Math.max(0.0F, heat - dt);
		if (current == null) {
			Iterator<Track> next = WAITING.values().iterator();
			if (!next.hasNext()) {
				return;
			}
			current = next.next();
			next.remove();
			slide = 0.0F;
			hold = HOLD;
			flash = 0.0F;
		}
		Track track = current;
		boolean filling = track.shown < track.target;
		// (it stays while it fills, while it waits, and while its toast is up; then makes way)
		boolean stay = filling || flash > 0.0F || hold > 0.0F || toastLeft > TOAST_FADE;
		slide = Mth.clamp(slide + (stay ? dt : -dt) / SLIDE, 0.0F, 1.0F);
		if (!stay && slide <= 0.0F) {
			current = null;
			return;
		}
		if (slide < 1.0F) {
			return;
		}
		if (flash > 0.0F) {
			flash = Math.max(0.0F, flash - dt);
			if (flash <= 0.0F) {
				track.coolFrom = 0.0F;
			}
			return;
		}
		if (filling) {
			float speed = Math.max(FILL_MIN_SPEED, (track.target - track.shown) / FILL_TIME);
			float to = Math.min(track.target, track.shown + speed * dt);
			float nextLevel = (float)Math.floor(track.shown + 1.0e-4F) + 1.0F;
			if (to >= nextLevel) {
				track.shown = nextLevel;
				levelUp(track, (int)nextLevel);
			} else {
				track.shown = to;
			}
			heat = HEAT_FADE;
			hold = HOLD;
		} else {
			hold = Math.max(0.0F, hold - dt);
		}
		float share = track.share(track.shown);
		track.coolFrom = share < track.coolFrom ? share : Math.min(share, track.coolFrom + (filling ? COOL_BURNING : COOL_SPEED) * dt);
	}

	private static void levelUp(Track track, int level) {
		flash = FLASH;
		List<Component> lines = track.toast.apply(level);
		if (lines == null || lines.isEmpty()) {
			return;
		}
		toastLines = lines;
		toastLeft = TOAST;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, CHIME_PITCH, CHIME_VOLUME));
	}

	/** The meter's top when at rest: under vanilla's boss bars when any show. */
	private static int restTop(Minecraft minecraft) {
		int bosses = ((BossHealthOverlayAccessor)minecraft.gui.hud.getBossOverlay()).jaymoskingdoms$events().size();
		return bosses <= 0 ? TOP : BOSS_TOP + BOSS_EACH * bosses + 2;
	}

	/** The bar: frame and track, the settled fill, the glowing fresh part, the front and its sparks, a level's flash. */
	private static void bar(GuiGraphicsExtractor graphics, Track track, int x, int y, long now) {
		float share = flash > 0.0F ? 1.0F : track.share(track.shown);
		int color = HudStyle.opaque(track.color);
		graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, HudStyle.PLATE);
		graphics.fill(x, y, x + BAR_W, y + BAR_H, HudStyle.TRACK);
		int filled = Math.round(BAR_W * share);
		int cool = Math.min(filled, Math.round(BAR_W * (flash > 0.0F ? 1.0F : track.coolFrom)));
		if (cool > 0) {
			graphics.fill(x, y, x + cool, y + BAR_H, color);
		}
		// the fresh part: still glowing, hotter towards the front
		for (int px = cool; px < filled; px++) {
			float t = (px - cool + 1) / (float)Math.max(1, filled - cool);
			graphics.fill(x + px, y, x + px + 1, y + BAR_H, HudStyle.opaque(HudStyle.mix(track.color, GLOW, 0.35F + 0.65F * t)));
		}
		// (a lighter line along the top, so the bar reads as a bar)
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + 1, alpha(0xFFFFFF, 0.22F));
		}
		if (flash > 0.0F) {
			float k = flash / FLASH;
			graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, alpha(HOT, 0.85F * k * k));
			return;
		}
		float k = heat / HEAT_FADE;
		if (k <= 0.0F || filled <= 0) {
			return;
		}
		// the front: white-hot, standing proud of the bar, flickering like metal on a wheel
		double seconds = now / 1.0e9;
		float flicker = 0.75F + 0.25F * (float)Math.sin(seconds * 47.0);
		int fx = x + filled;
		graphics.fill(fx - 3, y - 2, fx, y + BAR_H + 2, alpha(HOT, k * flicker));
		graphics.fill(fx - 6, y - 1, fx - 3, y + BAR_H + 1, alpha(GLOW, 0.55F * k * flicker));
		// sparks thrown back off it, up and down, falling as they go
		for (int i = 0; i < SPARKS; i++) {
			float rate = 1.6F + 0.3F * i;
			float life = (float)((seconds * rate + i * 0.37) % 1.0);
			float back = SPARK_BACK * life * (0.5F + 0.5F * hash(i, 1));
			float lift = (hash(i, 2) * 2.0F - 1.0F) * SPARK_UP;
			float sy = y + BAR_H / 2.0F + lift * life + 6.0F * life * life;
			int sx = Math.round(fx - 1 - back);
			if (sx < x - 2) {
				continue;
			}
			int spark = HudStyle.mix(HOT, GLOW, life);
			// (fresh off the wheel a spark is a short streak; then a point)
			graphics.fill(sx, Math.round(sy), sx + (life < 0.4F ? 2 : 1), Math.round(sy) + 1, alpha(spark, k * (1.0F - life)));
		}
	}

	/** The level-up toast, under the bar: every line whole (wrapped if the screen is narrow), fading in and out. */
	private static void toast(GuiGraphicsExtractor graphics, Font font, int cx, int y) {
		if (toastLeft <= 0.0F || toastLines.isEmpty()) {
			return;
		}
		float k = Mth.clamp(Math.min(toastLeft, TOAST - toastLeft) / TOAST_FADE, 0.0F, 1.0F);
		int maxW = Math.max(60, graphics.guiWidth() - 24);
		List<FormattedCharSequence> rows = new ArrayList<>();
		int first = 0, width = 0;
		for (int i = 0; i < toastLines.size(); i++) {
			List<FormattedCharSequence> split = font.split(toastLines.get(i), maxW);
			if (i == 0) {
				first = split.size();
			}
			for (FormattedCharSequence row : split) {
				width = Math.max(width, font.width(row));
				rows.add(row);
			}
		}
		// (text under about a tenth alpha isn't drawn by the font: let the plate lead it in)
		int height = rows.size() * 10 + 3;
		int rise = Math.round((1.0F - k) * 3.0F);
		HudStyle.plate(graphics, cx - width / 2 - 5, y - rise, width + 10, height, alpha(0x000000, 0.56F * k), alpha(HudStyle.GOLD & 0xFFFFFF, 0.5F * k));
		if (k < 0.15F) {
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			FormattedCharSequence row = rows.get(i);
			graphics.text(font, row, cx - font.width(row) / 2, y - rise + 2 + i * 10, alpha((i < first ? HudStyle.GOLD : HudStyle.DIM) & 0xFFFFFF, k), true);
		}
	}

	private static int alpha(int rgb, float a) {
		return (Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
	}

	/** A fixed 0-1 number for spark {@code i}'s property {@code n}, so each spark keeps its own path. */
	private static float hash(int i, int n) {
		int h = (i * 374761393 + n * 668265263) ^ 0x5BF03635;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
	}
}
