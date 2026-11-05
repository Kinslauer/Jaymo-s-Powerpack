package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.9, Ultimates patch 0) The change-in as the world sees it. When a player's Ultimate turns on, this client times a morph
 * (as long as the film: {@code ULT_FILM_TICKS}, or {@code ULT_FILM_FIRST} the first time, whatever the film packet said): each
 * part named in {@link XenoUltimate#morphParts()} grows from small into place over its beat, in order, its seams flaring as it
 * lands, and the whole body is there for the last stretch. Everyone sees it, film or not. With no parts listed the whole model
 * grows in instead ({@link #whole}). {@code UltimateCinematic}'s MORPH shot uses the same beats ({@link #windowStart}).
 */
public final class XenoMorph {
	/** The share of the morph spent before the first part (a wide establishing shot) and after the last (the pull-back to the whole body). */
	public static final float INTRO = 0.10F, OUTRO = 0.20F;
	/** A part grows from this share of its size (its place in the Ultimate's model), from this far into its beat to this far. */
	static final float FROM_SCALE = 0.3F, GROW_START = 0.3F, GROW_END = 0.95F;
	/** How long a landed part's seams flare (share of the morph). */
	static final float FLARE = 0.06F;
	/** How much the whole model starts at when no parts are listed. */
	static final float WHOLE_FROM = 0.5F;
	/** How far apart (ticks) a film packet and the Ultimate turning on can be and still belong together. */
	private static final int MATCH_TICKS = 20;

	/** A morph in progress on a render state: the parts in order, the share done ({@code p}, 0 to 1) and the seams' flare (0 to 1). */
	public record Morph(List<String> parts, float p, float flare) {
	}

	private static final class Track {
		boolean seen, was, first, roared;
		/** (0.20.9) How many parts' first-time crackles have played. */
		int crackled;
		long start = -1L;
		int ticks;
		List<String> parts = List.of();
	}

	private record Pending(long at, int ticks, List<String> parts, boolean first) {
	}

	private static final Map<Player, Track> TRACKS = new WeakHashMap<>();
	private static final Map<Integer, Pending> PENDING = new HashMap<>();

	private XenoMorph() {
	}

	/** From the client tick: notices each player's Ultimate turning on. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			TRACKS.clear();
			PENDING.clear();
			return;
		}
		long now = minecraft.level.getGameTime();
		PENDING.values().removeIf(p -> now - p.at > MATCH_TICKS);
		for (Player player : minecraft.level.players()) {
			com.jaymospowers.power.xenoform.XenoForm form = XenoformState.form(player);
			XenoUltimate ult = form != null && Boolean.TRUE.equals(player.getAttached(XenoformState.ULTIMATE)) ? form.evolved() : null;
			boolean on = ult != null;
			Track t = TRACKS.computeIfAbsent(player, p -> new Track());
			if (!t.seen) {
				// (one already an Ultimate when it came into view hasn't just changed)
				t.seen = true;
				t.was = on;
				continue;
			}
			if (on && !t.was) {
				t.start = now;
				t.ticks = XenoformPower.ULT_FILM_TICKS;
				t.first = false;
				t.roared = false;
				t.crackled = 0;
				t.parts = new ArrayList<>(Arrays.asList(ult.morphParts()));
				Pending film = PENDING.remove(player.getId());
				if (film != null) {
					fill(t, film.ticks, film.parts, film.first);
				}
			} else if (!on) {
				t.start = -1L;
			}
			t.was = on;
			if (on && t.first && t.start >= 0L) {
				sounds(minecraft, player, t, now);
			}
		}
	}

	/** (0.20.9) The first-time cut's sound: the band crackles as each part locks on, and a roar lands with the last beat. */
	private static void sounds(Minecraft minecraft, Player player, Track t, long now) {
		float p = (now - t.start) / (float)t.ticks;
		int n = t.parts.size();
		while (t.crackled < n && p >= windowStart(t.crackled, n) + GROW_END * slot(n)) {
			float pitch = 1.0F + 0.08F * t.crackled;
			minecraft.level.playLocalSound(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
				net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, pitch, false);
			minecraft.level.playLocalSound(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
				net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, pitch, false);
			t.crackled++;
		}
		if (!t.roared && p >= 1.0F - OUTRO) {
			t.roared = true;
			minecraft.level.playLocalSound(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
				net.minecraft.sounds.SoundEvents.WARDEN_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 0.8F, false);
		}
	}

	private static void fill(Track t, int ticks, List<String> parts, boolean first) {
		t.ticks = Math.max(1, ticks);
		t.first = first;
		if (!parts.isEmpty()) {
			t.parts = new ArrayList<>(parts);
		}
	}

	/** A film packet arrived ({@code UltimateCinematic}): its length and parts are the morph's, whichever came first, the film or the Ultimate. */
	public static void film(Minecraft minecraft, int casterId, int ticks, List<String> parts, boolean first) {
		if (minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		Entity who = minecraft.level.getEntity(casterId);
		Track t = who instanceof Player p ? TRACKS.get(p) : null;
		if (t != null && t.start >= 0L && now - t.start <= MATCH_TICKS) {
			fill(t, ticks, parts, first);
		} else {
			PENDING.put(casterId, new Pending(now, ticks, List.copyOf(parts), first));
		}
	}

	/** From {@code XenoformClient#extract}: the morph {@code player} is in, or null. */
	static @Nullable Morph get(Player player, float partialTicks) {
		Track t = TRACKS.get(player);
		if (t == null || t.start < 0L) {
			return null;
		}
		float p = (player.level().getGameTime() - t.start + partialTicks) / t.ticks;
		if (p >= 1.0F || p < 0.0F) {
			return null;
		}
		return new Morph(t.parts, p, flare(t.parts.size(), p));
	}

	/** The share of the film where part {@code index} of {@code count} has its beat (the camera moves to it then). */
	public static float windowStart(int index, int count) {
		return INTRO + index * slot(count);
	}

	/** The length of each part's beat, as a share of the film. */
	public static float slot(int count) {
		return (1.0F - OUTRO - INTRO) / Math.max(1, count);
	}

	/** How grown part {@code index} of {@code count} is at {@code p}: 0 (still small) to 1 (in place), overshooting a little as it locks on. */
	static float grown(int index, int count, float p) {
		float start = windowStart(index, count) + GROW_START * slot(count);
		float end = windowStart(index, count) + GROW_END * slot(count);
		return easeOutBack(Mth.clamp((p - start) / (end - start), 0.0F, 1.0F));
	}

	private static float flare(int count, float p) {
		float best = 0.0F;
		for (int i = 0; i < count; i++) {
			float landed = p - (windowStart(i, count) + GROW_END * slot(count));
			if (landed >= 0.0F && landed < FLARE) {
				best = Math.max(best, 1.0F - landed / FLARE);
			}
		}
		return best;
	}

	private static float easeOutBack(float x) {
		if (x >= 1.0F) {
			return 1.0F;
		}
		float c1 = 1.2F, c3 = c1 + 1.0F;
		float u = x - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	/** How big the whole model is drawn when no parts are listed (1 otherwise, or when no morph runs). */
	static float whole(@Nullable Morph morph) {
		if (morph == null || !morph.parts.isEmpty()) {
			return 1.0F;
		}
		return Mth.lerp(easeOutBack(Mth.clamp(morph.p / (1.0F - OUTRO), 0.0F, 1.0F)), WHOLE_FROM, 1.0F);
	}

	/** From {@link XenoFormModels.UltimateModel#setupAnim}, after the pose: parts not grown in yet are small. A name the model doesn't have is skipped. */
	static void apply(XenoFormModels.UltimateModel model, AvatarRenderState state) {
		Morph morph = state.getData(XenoformClient.MORPH);
		if (morph == null) {
			return;
		}
		int n = morph.parts.size();
		for (int i = 0; i < n; i++) {
			float s = Mth.lerp(grown(i, n, morph.p), FROM_SCALE, 1.0F);
			// (0.20.9) "a+b" is one beat for several parts (Titan's two shoulder plates)
			for (String name : morph.parts.get(i).split("\\+")) {
				ModelPart part = model.part(name);
				if (part == null || part == model.root()) {
					continue;
				}
				net.minecraft.client.model.geom.PartPose rest = part.getInitialPose();
				part.xScale = rest.xScale() * s;
				part.yScale = rest.yScale() * s;
				part.zScale = rest.zScale() * s;
			}
		}
	}
}
