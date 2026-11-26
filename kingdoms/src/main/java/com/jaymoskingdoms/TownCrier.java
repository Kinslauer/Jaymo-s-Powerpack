package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The town crier. Andy (2026-11-26), of "X sentences Y for theft: pardoned." turning up in chat: "messages like these
 * shouldnt be displayed. maybe told by a crier in town near the jail."
 *
 * <p>A realm's court no longer tells anyone in chat what it did. Each verdict (an arrest judged, {@link Arrests}; a player
 * caught by the watch, {@link Busts}; a poacher a ranger brings in, {@link ForestRangers}) goes to the realm's crier: one of
 * its grown townsfolk who keeps the street by its seat, where the cells are. While a player is in earshot of him he rings
 * his handbell, calls "Hear ye!" and cries the news, one piece at a time, in speech bubbles over his head. Nobody away from
 * him is told anything; news nobody has come by to hear is forgotten after a day. He is whoever of the realm's people is
 * nearest the seat (and he keeps the post while he's about), never its ruler.
 */
public final class TownCrier {
	/** The crier is the realm's grown townsfolk nearest its seat, from this close; players this near him hear him. */
	static final double POST_REACH = 24.0, EARSHOT = 20.0;
	/** News waits this long to be cried (ticks: a day), and at most this many pieces per realm. */
	static final long KEEP = 24000L;
	static final int MOST = 6;
	/** From his "Hear ye!" to the news, and from one piece of news to the next (ticks). */
	static final long OPEN_TICKS = 30L, GAP_TICKS = 20L * 6;
	/** His handbell: this loud (a quiet bell, heard about as far as his voice), this high. */
	static final float BELL_VOLUME = 0.7F, BELL_PITCH = 1.8F;
	/** Lines each (lang {@code crier.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;
	/** His bubble colour. */
	static final int COLOR = Speech.GOLD;

	/** Someone the news is about, named to each listener as they know them ({@link Introductions#called}). */
	record Named(UUID id, Component name, Component what, boolean kept) {
		static Named of(Entity e) {
			Component name = e instanceof ServerPlayer p ? Post.nameOf(p) : e.getDisplayName();
			// someone the listener hasn't met is named for what they are, lower case, as it's cried mid-line
			Heritage.Kind brood = Greenskins.of(e);
			String what = e instanceof ServerPlayer ? "stranger" : brood == Heritage.Kind.ORC ? "orc" : brood == Heritage.Kind.GOBLIN ? "goblin" : "villager";
			return new Named(e.getUUID(), name, Component.translatable("crier.jaymoskingdoms.someone." + what), Introductions.named(e));
		}

		Component to(ServerLevel level, ServerPlayer viewer) {
			Entity e = level.getEntity(this.id);
			boolean knows = e != null ? Introductions.knows(viewer, e) : !this.kept || Introductions.knowsId(viewer, this.id);
			return knows ? this.name : this.what;
		}
	}

	/** One piece of news: the line ({@code crier.jaymoskingdoms.<key>}, its number picked), who it's about, and the rest. */
	record Cry(String key, Named who, List<Component> rest, long at) {
		Component words(ServerLevel level, ServerPlayer viewer) {
			Object[] args = new Object[this.rest.size() + 1];
			args[0] = this.who.to(level, viewer);
			for (int i = 0; i < this.rest.size(); i++) {
				args[i + 1] = this.rest.get(i);
			}
			return Component.translatable(this.key, args);
		}
	}

	/** A realm's news, its crier, and where he is in his crying. */
	private static final class Stand {
		final Deque<Cry> news = new ArrayDeque<>();
		@Nullable UUID crier;
		/** Rung his bell and called out: the news comes next. */
		boolean opened;
		long next;
	}

	/** Realm id → its crier's post. */
	private static final Map<Integer, Stand> POSTS = new HashMap<>();

	private TownCrier() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownCrier::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> POSTS.clear());
	}

	/** {@code f}'s court has sentenced {@code who} for {@code crime} to {@code sentence} (pardon, flog, exile, execute, jail). */
	static void verdict(ServerLevel level, Faction f, Entity who, Component crime, String sentence) {
		cry(level, f, "verdict." + sentence, who, crime);
	}

	/** {@code f}'s crier is to cry {@code crier.jaymoskingdoms.<key>.<n>} about {@code who}, with {@code rest} after their name. */
	static void cry(ServerLevel level, Faction f, String key, Entity who, Component... rest) {
		Stand post = POSTS.computeIfAbsent(f.id(), id -> new Stand());
		if (post.news.size() >= MOST) {
			// the oldest is stale by now
			post.news.pollFirst();
		}
		String line = "crier.jaymoskingdoms." + key + "." + level.getRandom().nextInt(LINES);
		post.news.addLast(new Cry(line, Named.of(who), List.of(rest), level.getGameTime()));
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (POSTS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 10 != 0) {
			return;
		}
		for (Iterator<Map.Entry<Integer, Stand>> it = POSTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Stand> e = it.next();
			Stand post = e.getValue();
			post.news.removeIf(c -> now - c.at() > KEEP);
			Faction f = Factions.get(server, e.getKey());
			if (post.news.isEmpty() || f == null || f.fallen()) {
				it.remove();
				continue;
			}
			if (now >= post.next) {
				step(level, f, post, now);
			}
		}
	}

	/** If anyone is in earshot of the crier, he rings and calls out, or cries the next piece of news. */
	private static void step(ServerLevel level, Faction f, Stand post, long now) {
		Villager crier = crier(level, f, post);
		if (crier == null) {
			post.opened = false;
			return;
		}
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, crier.getBoundingBox().inflate(EARSHOT),
			p -> p.isAlive() && !p.isSpectator() && !Deafened.unheard(p, crier))) {
			near.add(p);
		}
		if (near.isEmpty()) {
			// nobody to hear it: the news keeps till someone comes by
			post.opened = false;
			return;
		}
		ServerPlayer nearest = near.get(0);
		for (ServerPlayer p : near) {
			if (p.distanceToSqr(crier) < nearest.distanceToSqr(crier)) {
				nearest = p;
			}
		}
		crier.getNavigation().stop();
		crier.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
		if (!post.opened) {
			level.playSound(null, crier.getX(), crier.getEyeY(), crier.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, BELL_VOLUME, BELL_PITCH);
			Speech.say(level, crier, Component.translatable("crier.jaymoskingdoms.hear_ye." + level.getRandom().nextInt(LINES), f.name()), COLOR, EARSHOT);
			post.opened = true;
			post.next = now + OPEN_TICKS;
			return;
		}
		Cry cry = post.news.pollFirst();
		if (cry == null) {
			post.opened = false;
			return;
		}
		for (ServerPlayer p : near) {
			// each hears the names they know, in the crier's tongue
			Speech.privately(crier, p, Languages.heard(p, crier, cry.words(level, p)), COLOR);
		}
		post.opened = !post.news.isEmpty();
		post.next = now + (post.opened ? OPEN_TICKS + GAP_TICKS / 2 : GAP_TICKS);
	}

	/** {@code f}'s crier: the one who had the post if he's still fit for it, else its grown townsfolk nearest the seat. */
	private static @Nullable Villager crier(ServerLevel level, Faction f, Stand post) {
		Vec3 seat = Vec3.atBottomCenterOf(f.seat());
		if (post.crier != null && level.getEntity(post.crier) instanceof Villager v && fit(level, f, v) && v.position().distanceTo(seat) < POST_REACH) {
			return v;
		}
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(POST_REACH), v -> fit(level, f, v))) {
			if (best == null || v.distanceToSqr(seat) < best.distanceToSqr(seat)) {
				best = v;
			}
		}
		post.crier = best == null ? null : best.getUUID();
		return best;
	}

	private static boolean fit(ServerLevel level, Faction f, Villager v) {
		if (!v.isAlive() || v.isBaby() || v.isSleeping() || Downed.isDowned(v) || Arrests.held(v) || Arrests.busy(v) || Rulers.is(v)
			|| Retaliation.brawling(v) || v.getTradingPlayer() != null) {
			return false;
		}
		Faction theirs = Theft.lawAt(level, v.blockPosition());
		return theirs != null && theirs.id() == f.id();
	}
}
