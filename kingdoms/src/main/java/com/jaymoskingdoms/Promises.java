package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.523) Promises: bring this by that day, come back, look after my child, walk my child home, speak for me to the ruler,
 * keep the peace, pay back a loan. Kept on the player (saved, never synced) and listed in the traveller's journal. Keep one
 * and the villager (and their family, who hear of it) warm to you; break one and they remember, bring it up, and their kin and
 * friends cool too. The player's record changes the moment a promise is kept or broken; the villager's side waits until
 * they're loaded ({@link #settle}). Public and neutral, so patch 9's quests can make promises without knowing about talks.
 * Plan: docs/Talks-Plan.md.
 */
public final class Promises {
	/** Open promises a player can have, and open promises to one villager. */
	public static final int MAX_OPEN = 5, PER_VILLAGER = 1;
	/** Ticks between checks. */
	static final int TICK_EVERY = 20;
	/** Come back within this far of them; look in on the child within this far, on each of this many days after. */
	static final double COME_BACK_NEAR = 8.0, WATCH_NEAR = 16.0;
	static final int WATCH_DAYS = 2;
	/** Walking a child home: ticks to do it, and how near home (or the parent) counts. */
	static final long ESCORT_TICKS = 6000L;
	static final double ESCORT_HOME_NEAR = 8.0;
	/** A wrong done this near to the place breaks a promise to keep the peace. */
	static final double PEACE_RANGE = 96.0;
	/** The villager says it if you're this near; a settled promise unmet this many days is dropped. */
	static final double SETTLE_SAY_RANGE = 16.0;
	static final int SETTLE_LIMIT_DAYS = 30;
	/** What settling does: hearts with them, reputation with their realm, hearts of their kin. */
	static final int KEPT_HEARTS = 10, BROKEN_HEARTS = 15, RELEASED_HEARTS = 2, KIN_KEPT_HEARTS = 3, KIN_BROKEN_HEARTS = 5;
	static final int KEPT_REP = 5, BROKEN_REP = 10;
	/** Telling them honestly you can't. */
	static final int RELEASE_TRUST = -8, RELEASE_WARMTH = -2;
	/** The child came home hurt. */
	static final int CHILD_HURT_WARMTH = -5;
	/** Lines for each. */
	static final int LINES = 2;

	public static final String OPEN = "open", KEPT = "kept", BROKEN = "broken", RELEASED = "released";
	/** The status of a promise broken because the child died. */
	static final String CHILD_LOST = "broken:child_lost";

	public enum Kind {
		BRING("bring"), REPAY("repay"), COME_BACK("come_back"), WATCH_CHILD("watch_child"), ESCORT_CHILD("escort_child"),
		SPEAK_FOR("speak_for"), KEEP_PEACE("keep_peace");

		public final String key;

		Kind(String key) {
			this.key = key;
		}

		static @Nullable Kind parse(String key) {
			for (Kind k : values()) {
				if (k.key.equals(key)) {
					return k;
				}
			}
			return null;
		}
	}

	/**
	 * (0.7.523) A promise: kind, to whom (uuid, name as known, realm id or -1, where they were), what (item id and count; for a
	 * child, the child's name; for keeping the peace, the dimension), the child it's about, when it was made (clock, day), when
	 * it's due (Overworld clock), days visited (a bit for each day after, WATCH_CHILD) / last visit day, harmed (a child), status
	 * (open, kept, broken, released), and the day it was settled (-1 while open).
	 */
	public record Promise(String kind, UUID to, String name, int faction, BlockPos where, String item, int count,
		Optional<UUID> child, long made, long madeDay, long due, int visits, long lastVisit, boolean hurt, String status, long closed) {
		public static final Codec<Promise> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Promise::kind),
				UUIDUtil.STRING_CODEC.fieldOf("to").forGetter(Promise::to),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Promise::name),
				Codec.INT.optionalFieldOf("faction", -1).forGetter(Promise::faction),
				BlockPos.CODEC.optionalFieldOf("where", BlockPos.ZERO).forGetter(Promise::where),
				Codec.STRING.optionalFieldOf("item", "").forGetter(Promise::item),
				Codec.INT.optionalFieldOf("count", 0).forGetter(Promise::count),
				UUIDUtil.STRING_CODEC.optionalFieldOf("child").forGetter(Promise::child),
				Codec.LONG.fieldOf("made").forGetter(Promise::made),
				Codec.LONG.optionalFieldOf("made_day", 0L).forGetter(Promise::madeDay),
				Codec.LONG.fieldOf("due").forGetter(Promise::due),
				Codec.INT.optionalFieldOf("visits", 0).forGetter(Promise::visits),
				Codec.LONG.optionalFieldOf("last_visit", -1L).forGetter(Promise::lastVisit),
				Codec.BOOL.optionalFieldOf("hurt", false).forGetter(Promise::hurt),
				Codec.STRING.optionalFieldOf("status", OPEN).forGetter(Promise::status),
				Codec.LONG.optionalFieldOf("closed", -1L).forGetter(Promise::closed))
			.apply(i, Promise::new));

		@Nullable Kind type() {
			return Kind.parse(kind);
		}

		boolean open() {
			return status.equals(OPEN);
		}

		boolean broken() {
			return status.startsWith(BROKEN);
		}

		Promise with(String status, long closed) {
			return new Promise(kind, to, name, faction, where, item, count, child, made, madeDay, due, visits, lastVisit, hurt, status, closed);
		}

		Promise visited(int visits, long lastVisit) {
			return new Promise(kind, to, name, faction, where, item, count, child, made, madeDay, due, visits, lastVisit, hurt, status, closed);
		}

		Promise harmed() {
			return new Promise(kind, to, name, faction, where, item, count, child, made, madeDay, due, visits, lastVisit, true, status, closed);
		}

		boolean is(Promise other) {
			return to.equals(other.to) && kind.equals(other.kind) && made == other.made;
		}
	}

	/** What a new promise is about: an item and count (for a child, its name as the item), the child, when it's due, where. */
	public record Terms(String item, int count, @Nullable UUID child, long due, BlockPos where) {
	}

	/** On the player: their promises. Saved, copied on death, not synced. */
	static final AttachmentType<List<Promise>> PROMISES = AttachmentRegistry.create(JaymosKingdoms.id("promises"),
		b -> b.persistent(Promise.CODEC.listOf()).copyOnDeath());

	private Promises() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Promises::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> died(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (damage > 0.0F && entity instanceof Villager v && v.isBaby() && v.level().getServer() != null) {
				hurt(v.level().getServer(), v);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			for (Promise p : of(player)) {
				if (p.open() && p.type() == Kind.ESCORT_CHILD) {
					stopFollowing(server, p);
					breakOff(player, p, "left");
				}
			}
		});
	}

	// ---- reading ----------------------------------------------------------------------------------------------------------

	public static List<Promise> of(ServerPlayer player) {
		return List.copyOf(player.getAttachedOrElse(PROMISES, List.of()));
	}

	/** {@code player}'s open promises to {@code to}. */
	public static List<Promise> open(ServerPlayer player, UUID to) {
		return of(player).stream().filter(p -> p.open() && p.to().equals(to)).toList();
	}

	static int openCount(ServerPlayer player) {
		return (int)of(player).stream().filter(Promise::open).count();
	}

	private static void update(ServerPlayer player, UnaryOperator<List<Promise>> change) {
		List<Promise> list = new ArrayList<>(player.getAttachedOrElse(PROMISES, List.of()));
		List<Promise> next = change.apply(list);
		if (next.isEmpty()) {
			player.removeAttached(PROMISES);
		} else {
			player.setAttached(PROMISES, List.copyOf(next));
		}
	}

	private static void replace(ServerPlayer player, Promise was, Promise now) {
		update(player, list -> {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).is(was)) {
					list.set(i, now);
				}
			}
			return list;
		});
	}

	private static void remove(ServerPlayer player, Promise p) {
		update(player, list -> {
			list.removeIf(x -> x.is(p));
			return list;
		});
	}

	static long clockOf(MinecraftServer server) {
		return clock(server);
	}

	private static long clock(MinecraftServer server) {
		return Factions.level(server).getOverworldClockTime();
	}

	private static long day(long clock) {
		return Math.floorDiv(clock, 24000L);
	}

	/** The due clock of a promise "in {@code days} days": the end of that day. */
	static long dueIn(MinecraftServer server, int days) {
		return (day(clock(server)) + days + 1) * 24000L - 1L;
	}

	static @Nullable Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	private static Component goods(Promise p) {
		return new ItemStack(item(p.item())).getHoverName();
	}

	/** What the promise was, from the villager's side ("bring me 8 Bone Meal"). */
	public static Component what(Promise p) {
		Kind k = p.type();
		if (k == null) {
			return Component.empty();
		}
		return switch (k) {
			case BRING -> Component.translatable("promise.jaymoskingdoms.what.bring", p.count(), goods(p));
			case REPAY -> Component.translatable("promise.jaymoskingdoms.what.repay", Money.text(Money.worth(p.count())));
			default -> Component.translatable("promise.jaymoskingdoms.what." + k.key);
		};
	}

	// ---- making -----------------------------------------------------------------------------------------------------------

	/** {@code player} promises {@code to} something; false at the limits (too many open, or one to them already). */
	public static boolean make(ServerPlayer player, LivingEntity to, Kind kind, Terms terms) {
		if (!(player.level().getServer() instanceof MinecraftServer server) || openCount(player) >= MAX_OPEN || open(player, to.getUUID()).size() >= PER_VILLAGER) {
			return false;
		}
		long clock = clock(server);
		Faction f = Bearings.realm(to);
		String item = kind == Kind.KEEP_PEACE ? String.valueOf(to.level().dimension()) : terms.item();
		Promise p = new Promise(kind.key, to.getUUID(), to.getName().getString(), f == null ? -1 : f.id(), terms.where(), item, terms.count(),
			Optional.ofNullable(terms.child()), clock, day(clock), terms.due(), 0, -1L, false, OPEN, -1L);
		update(player, list -> {
			list.add(p);
			return list;
		});
		// (for the chat AI only)
		Recollections.remember(to, player, Recollections.Kind.PROMISED, what(p));
		return true;
	}

	// ---- keeping, breaking, releasing -------------------------------------------------------------------------------------------

	private static @Nullable Promise current(ServerPlayer player, Promise p) {
		for (Promise x : of(player)) {
			if (x.is(p)) {
				return x;
			}
		}
		return null;
	}

	public static void keep(ServerPlayer player, Promise p) {
		close(player, p, KEPT);
	}

	public static void breakOff(ServerPlayer player, Promise p, String why) {
		close(player, p, why.equals("child_lost") ? CHILD_LOST : BROKEN);
	}

	public static void release(ServerPlayer player, Promise p) {
		close(player, p, RELEASED);
	}

	private static void close(ServerPlayer player, Promise p, String status) {
		Promise now = current(player, p);
		if (now == null || !now.open() || player.level().getServer() == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		replace(player, now, now.with(status, day(clock(server))));
		settleAll(server, player);
	}

	private static void settleAll(MinecraftServer server, ServerPlayer player) {
		for (Promise p : of(player)) {
			if (!p.open() && find(server, p.to()) instanceof LivingEntity v && v.isAlive()) {
				settle(player, p, v);
			}
		}
	}

	/** The villager's side of a promise that's over: memory, bearing, hearts, reputation, kin. Then it comes off the list. */
	private static void settle(ServerPlayer player, Promise p, LivingEntity v) {
		remove(player, p);
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		Kind kind = p.type();
		if (kind == null) {
			return;
		}
		Faction f = p.faction() < 0 ? null : Factions.get(level.getServer(), p.faction());
		Component name = Recollections.nameTo(player, v);
		boolean near = player.level() == level && v.distanceTo(player) <= SETTLE_SAY_RANGE;
		Component what = what(p);
		if (p.status().equals(KEPT)) {
			Recollections.remember(v, player, Recollections.Kind.KEPT_WORD, what);
			McaCompat.hearts(v, player, KEPT_HEARTS);
			if (f != null) {
				Reputation.change(player, f, KEPT_REP);
			}
			for (Villager kin : kin(level, v)) {
				Recollections.rememberHeard(kin, player, Recollections.Kind.KEPT_WORD, what, v.getName().getString());
				McaCompat.hearts(kin, player, KIN_KEPT_HEARTS);
			}
			Patrons.deed(player, Patron.TRUTH, Patrons.DEED_MID, "kept_word");
			if (kind == Kind.WATCH_CHILD || kind == Kind.ESCORT_CHILD) {
				Patrons.deed(player, Patron.HEARTHKEEPER, Patrons.DEED_MID, "kept_child");
			}
			if (near && kind != Kind.BRING && kind != Kind.REPAY) {
				Speech.tell(level, v, player, Component.translatable("promise.jaymoskingdoms.kept." + kind.key + "." + v.getRandom().nextInt(LINES), name),
					Speech.GOLD);
			}
			if (p.hurt()) {
				Bearings.move(v, player, 0, 0, CHILD_HURT_WARMTH);
				if (near) {
					Speech.tell(level, v, player, Component.translatable("promise.jaymoskingdoms.child_hurt", name), Speech.RED);
				}
			}
		} else if (p.broken()) {
			Commandments.act(player, "broken_word");
			Recollections.remember(v, player, Recollections.Kind.BROKE_WORD, what);
			McaCompat.hearts(v, player, -BROKEN_HEARTS);
			if (f != null) {
				Reputation.change(player, f, -BROKEN_REP);
			}
			for (Villager kin : kin(level, v)) {
				Recollections.rememberHeard(kin, player, Recollections.Kind.BROKE_WORD, what, v.getName().getString());
				McaCompat.hearts(kin, player, -KIN_BROKEN_HEARTS);
			}
			if (near) {
				String key = p.status().equals(CHILD_LOST) ? "child_lost" : kind.key;
				Speech.tell(level, v, player, Component.translatable("promise.jaymoskingdoms.broken." + key + "." + v.getRandom().nextInt(LINES), name, what),
					Speech.RED);
			}
		} else if (p.status().equals(RELEASED)) {
			if (kind == Kind.REPAY) {
				// (a loan let go is not repaid: it counts as a broken word)
				Recollections.remember(v, player, Recollections.Kind.BROKE_WORD, what);
				McaCompat.hearts(v, player, -BROKEN_HEARTS);
				if (f != null) {
					Reputation.change(player, f, -BROKEN_REP);
				}
				for (Villager kin : kin(level, v)) {
					Recollections.rememberHeard(kin, player, Recollections.Kind.BROKE_WORD, what, v.getName().getString());
					McaCompat.hearts(kin, player, -KIN_BROKEN_HEARTS);
				}
			}
			Bearings.move(v, player, RELEASE_TRUST, 0, RELEASE_WARMTH);
			McaCompat.hearts(v, player, -RELEASED_HEARTS);
			if (near) {
				Speech.tell(level, v, player, Component.translatable("promise.jaymoskingdoms.released." + v.getRandom().nextInt(LINES)), Speech.WHITE);
			}
		}
	}

	private static List<Villager> kin(ServerLevel level, LivingEntity v) {
		if (!(v instanceof Villager villager)) {
			return List.of();
		}
		return level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(Recollections.KIN_RANGE),
			k -> k.isAlive() && k != v && McaCompat.kin(k, villager) != McaCompat.STRANGERS);
	}

	// ---- hand-over (Keep Your Word, and the due talk) ------------------------------------------------------------------------

	private static @Nullable Promise owed(ServerPlayer player, LivingEntity v) {
		for (Promise p : open(player, v.getUUID())) {
			if (p.type() == Kind.BRING || p.type() == Kind.REPAY) {
				return p;
			}
		}
		return null;
	}

	/** {@code player} owes {@code v} goods or coin, and carries enough. */
	public static boolean handable(ServerPlayer player, Villager v) {
		Promise p = owed(player, v);
		return p != null && carries(player, p);
	}

	/** The open BRING or REPAY to {@code v}, if any (for the due talk). */
	static @Nullable Promise owedTo(ServerPlayer player, LivingEntity v) {
		return owed(player, v);
	}

	static boolean carries(ServerPlayer player, Promise p) {
		return p.type() == Kind.REPAY ? Money.canPay(player, Money.worth(p.count())) : Purse.count(player, item(p.item())) >= p.count();
	}

	/** Takes the goods; true if it was taken. */
	static boolean take(ServerPlayer player, Promise p) {
		if (!carries(player, p)) {
			return false;
		}
		if (p.type() == Kind.REPAY) {
			return Money.charge(player, Money.worth(p.count()));
		}
		Purse.take(player, item(p.item()), p.count());
		return true;
	}

	/** Their thanks for what was handed over. */
	static Component handedLine(ServerPlayer player, LivingEntity v, Promise p) {
		return Component.translatable("promise.jaymoskingdoms.handed." + p.kind() + "." + v.getRandom().nextInt(LINES), Recollections.nameTo(player, v));
	}

	/** Keep Your Word: hand over what you promised. What they say goes back (null: nothing to hand over). */
	public static @Nullable Component handOver(ServerPlayer player, Villager v) {
		Promise p = owed(player, v);
		if (p == null || !take(player, p)) {
			return null;
		}
		keep(player, p);
		return handedLine(player, v, p);
	}

	/** An open promise to {@code v} that falls due today and isn't kept; or null. */
	public static @Nullable Promise dueToday(ServerPlayer player, LivingEntity v) {
		if (v.level().getServer() == null) {
			return null;
		}
		long today = day(clock(v.level().getServer()));
		for (Promise p : open(player, v.getUUID())) {
			// (0.7.523) not COME_BACK: the villager walking over to remind you would keep it for you
			if (p.type() != Kind.ESCORT_CHILD && p.type() != Kind.COME_BACK && day(p.due()) == today) {
				return p;
			}
		}
		return null;
	}

	// ---- checks ------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		long clock = clock(server);
		long today = day(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Promise> list = of(player);
			if (list.isEmpty()) {
				continue;
			}
			for (Promise p : list) {
				if (!p.open()) {
					if (p.closed() >= 0 && today - p.closed() > SETTLE_LIMIT_DAYS) {
						remove(player, p);
					}
					continue;
				}
				check(server, player, p, clock, today);
			}
			settleAll(server, player);
		}
	}

	private static void check(MinecraftServer server, ServerPlayer player, Promise p, long clock, long today) {
		Kind kind = p.type();
		if (kind == null) {
			return;
		}
		boolean late = clock > p.due();
		switch (kind) {
			case COME_BACK -> {
				if (today > p.madeDay() && find(server, p.to()) instanceof LivingEntity v && v.isAlive() && v.level() == player.level()
					&& v.distanceTo(player) <= COME_BACK_NEAR) {
					keep(player, p);
				} else if (late) {
					breakOff(player, p, "late");
				}
			}
			case WATCH_CHILD -> watch(server, player, p, clock, today);
			case ESCORT_CHILD -> escort(server, player, p, late);
			case KEEP_PEACE -> {
				if (late) {
					keep(player, p);
				}
			}
			default -> {
				if (late) {
					breakOff(player, p, "late");
				}
			}
		}
	}

	private static void watch(MinecraftServer server, ServerPlayer player, Promise p, long clock, long today) {
		Promise now = p;
		long first = p.madeDay() + 1L;
		if (p.child().isPresent() && today >= first && today < first + WATCH_DAYS && find(server, p.child().get()) instanceof LivingEntity child
			&& child.isAlive() && child.level() == player.level() && child.distanceTo(player) <= WATCH_NEAR) {
			int bit = 1 << (int)(today - first);
			if ((p.visits() & bit) == 0) {
				now = p.visited(p.visits() | bit, today);
				replace(player, p, now);
			}
		}
		for (int d = 0; d < WATCH_DAYS; d++) {
			if (today > first + d && (now.visits() & (1 << d)) == 0) {
				breakOff(player, now, "missed");
				return;
			}
		}
		if (clock > p.due() && now.visits() == (1 << WATCH_DAYS) - 1) {
			keep(player, now);
		}
	}

	private static void escort(MinecraftServer server, ServerPlayer player, Promise p, boolean late) {
		Entity child = p.child().isPresent() ? find(server, p.child().get()) : null;
		Entity parent = find(server, p.to());
		if (child instanceof Villager c && c.isAlive()) {
			if (c.level() != player.level()) {
				stopFollowing(server, p);
				breakOff(player, p, "left");
				return;
			}
			boolean home = parent instanceof Villager pv && McaCompat.home(pv).filter(g -> g.dimension().equals(c.level().dimension())
				&& g.pos().distToCenterSqr(c.position()) <= ESCORT_HOME_NEAR * ESCORT_HOME_NEAR).isPresent();
			boolean byParent = parent != null && parent.level() == c.level() && parent.distanceTo(c) <= ESCORT_HOME_NEAR;
			if (home || byParent) {
				McaCompat.stopFollowing(c);
				keep(player, p);
				return;
			}
		}
		if (late) {
			stopFollowing(server, p);
			breakOff(player, p, "late");
		}
	}

	private static void stopFollowing(MinecraftServer server, Promise p) {
		if (p.child().isPresent() && find(server, p.child().get()) instanceof Villager c) {
			McaCompat.stopFollowing(c);
		}
	}

	private static void died(LivingEntity entity) {
		MinecraftServer server = entity.level().getServer();
		if (server == null || !(entity instanceof Villager)) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Promise p : of(player)) {
				if (p.to().equals(entity.getUUID())) {
					// (the one promised has died: it's dropped quietly)
					if (p.open() && p.type() == Kind.ESCORT_CHILD) {
						stopFollowing(server, p);
					}
					remove(player, p);
				} else if (p.open() && p.child().isPresent() && p.child().get().equals(entity.getUUID())) {
					breakOff(player, p, "child_lost");
				}
			}
		}
	}

	private static void hurt(MinecraftServer server, Villager child) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Promise p : of(player)) {
				if (p.open() && !p.hurt() && p.child().isPresent() && p.child().get().equals(child.getUUID())) {
					replace(player, p, p.harmed());
				}
			}
		}
	}

	/** (KingdomEvents) {@code player} had an audience with the ruler of {@code factionId}: a promise to speak for someone there is kept. */
	static void audience(ServerPlayer player, int factionId) {
		for (Promise p : of(player)) {
			if (p.open() && p.type() == Kind.SPEAK_FOR && p.faction() == factionId) {
				keep(player, p);
			}
		}
	}

	/** (Recollections) {@code who} remembered {@code kind} of {@code player}: a wrong breaks a promise to keep the peace near there. */
	static void wronged(ServerPlayer player, LivingEntity who, Recollections.Kind kind) {
		if (kind != Recollections.Kind.STRUCK && kind != Recollections.Kind.CRIME && kind != Recollections.Kind.ROBBED
			&& kind != Recollections.Kind.THREATENED && kind != Recollections.Kind.KILLED_KIN) {
			return;
		}
		if (player.getAttachedOrElse(PROMISES, List.of()).isEmpty()) {
			return;
		}
		String dimension = String.valueOf(who.level().dimension());
		for (Promise p : of(player)) {
			if (p.open() && p.type() == Kind.KEEP_PEACE && p.item().equals(dimension)
				&& p.where().distToCenterSqr(who.position()) <= PEACE_RANGE * PEACE_RANGE) {
				breakOff(player, p, "wronged");
			}
		}
	}

	// ---- the journal -------------------------------------------------------------------------------------------------------

	/** {@code player}'s promises as journal entries (the Quests page). */
	static List<Journal.Entry> journal(ServerPlayer player) {
		List<Journal.Entry> out = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return out;
		}
		long clock = clock(server), today = day(clock);
		for (Promise p : of(player)) {
			Kind kind = p.type();
			if (kind == null) {
				continue;
			}
			Faction f = p.faction() < 0 ? null : Factions.get(server, p.faction());
			Component realm = f == null ? Component.translatable("promise.jaymoskingdoms.journal.realm.none") : Component.literal(f.name());
			Component giver = f == null ? Component.translatable("promise.jaymoskingdoms.journal.giver.alone", p.name())
				: Component.translatable("promise.jaymoskingdoms.journal.giver", p.name(), f.name());
			Component text = switch (kind) {
				case BRING -> Component.translatable("promise.jaymoskingdoms.journal.bring", p.name(), p.count(), goods(p));
				case REPAY -> Component.translatable("promise.jaymoskingdoms.journal.repay", p.name(), Money.text(Money.worth(p.count())));
				case WATCH_CHILD, ESCORT_CHILD -> Component.translatable("promise.jaymoskingdoms.journal." + kind.key, p.name(), p.item());
				case SPEAK_FOR, KEEP_PEACE -> Component.translatable("promise.jaymoskingdoms.journal." + kind.key, p.name(), realm);
				default -> Component.translatable("promise.jaymoskingdoms.journal." + kind.key, p.name());
			};
			boolean ready = !p.open() && p.status().equals(KEPT);
			Component objective;
			if (!p.open()) {
				objective = Component.translatable(p.status().equals(KEPT) ? "promise.jaymoskingdoms.journal.kept" : "promise.jaymoskingdoms.journal.broken");
			} else {
				if (kind == Kind.BRING || kind == Kind.REPAY) {
					ready = carries(player, p);
				}
				long left = Math.max(0L, p.due() - clock);
				objective = kind == Kind.ESCORT_CHILD ? Component.translatable("promise.jaymoskingdoms.journal.due.soon")
					: day(p.due()) <= today ? Component.translatable("promise.jaymoskingdoms.journal.due.today")
					: Component.translatable("promise.jaymoskingdoms.journal.due.days", (left + 23999L) / 24000L);
				if (kind == Kind.WATCH_CHILD) {
					long first = p.madeDay() + 1L;
					boolean seen = today >= first && today < first + WATCH_DAYS && (p.visits() & (1 << (int)(today - first))) != 0;
					ready = seen;
					objective = Component.translatable("promise.jaymoskingdoms.journal.both", objective,
						Component.translatable(seen ? "promise.jaymoskingdoms.journal.visited" : "promise.jaymoskingdoms.journal.not_visited"));
				}
			}
			Optional<BlockPos> target = Optional.of(p.where());
			if ((kind == Kind.WATCH_CHILD || kind == Kind.ESCORT_CHILD) && p.child().isPresent() && player.level().getEntity(p.child().get()) instanceof Entity c) {
				target = Optional.of(c.blockPosition());
			}
			out.add(new Journal.Entry(false, ready, Component.translatable("promise.jaymoskingdoms.journal.title", p.name()), giver, text, objective, target));
		}
		return out;
	}
}
