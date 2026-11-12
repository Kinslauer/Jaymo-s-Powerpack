package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The masterless (docs/Pawn-Plan.md row H3; after the lost pawns of Dragon's Dogma, a commercial game: ideas only). When
 * a player starts a new life ({@link LifeStory#reincarnateAnew}) every companion of the old one is left masterless, and
 * so is one a player parts with for good at the table ({@link #partWays}). They live on, lost ({@link #LOST}):
 * <ul>
 * <li><b>On the roads</b>: a lost one makes for the nearest road and walks it, muttering ("lost.mutter"). Met there, it
 * asks to come along ("lost.ask"); use it then and it joins you ("lost.join"). It isn't right: a few minutes on
 * ({@link #TURN_MIN}-{@link #TURN_MAX}) its eyes go red and it turns on you ("lost.turn", the hold of
 * {@link Possession}, until it's knocked down). Put it down, and when it comes round it's yours, and right ("lost.free").</li>
 * <li><b>Bandit leaders</b> (the foe side): some of the lost ({@link #BANDIT_CHANCE} when lost, or any not taken in for
 * {@link #BANDIT_AFTER}) take up the mask, gather a few men ({@link #MEN_MIN}-{@link #MEN_MAX}, a band of their own,
 * side {@code lost:<leader>}) and waylay travellers on their road ("lost.waylay"), with a bounty on them at the nearest
 * town. They fight with everything their old master taught them: their own learning and stars, taught to their men;
 * and when they meet their old master (the same player, in whatever new life), the band knows every way that master
 * fights, as though they'd met a dozen times ("lost.knows", {@link FoeLore#KNOWN}).</li>
 * </ul>
 */
public final class Masterless {
	/** A lost one turns this long after it joins someone (ticks, between). */
	static final int TURN_MIN = 2400, TURN_MAX = 7200;
	/** A lost one becomes a bandit leader at this chance when lost, or once it's gone this long without being taken in. */
	static final float BANDIT_CHANCE = 0.3F;
	static final long BANDIT_AFTER = 72000L;
	/** A leader's men. */
	static final int MEN_MIN = 2, MEN_MAX = 3;
	/** The bounty a bandit leader carries (emeralds' worth). */
	static final int BOUNTY = 25;
	/** Asks a player this near (blocks), at most this often (ticks); one who asked can be taken in for this long (ticks). */
	static final double ASK_NEAR = 8.0, WAYLAY_NEAR = 16.0, KNOWS_NEAR = 24.0, ROAD_SEEK = 192.0, MEN_KEEP = 12.0;
	static final long ASK_EVERY = 1200L, ASK_HOLDS = 600L, MUTTER_EVERY = 1800L, WAYLAY_EVERY = 1200L;
	/** A lost one walks this far up or down its road (points) between stops. */
	static final int WALK = 40;
	/** Meetings the band counts with its old master at once (enough to be ready for them: {@link FoeLore#READY_AFTER}). */
	static final int KNOWN_MEETINGS = 12;
	static final int EVERY = 20;
	static final long NONE = Long.MIN_VALUE;

	/**
	 * A lost one: its old master's id and name, since when, whom it joined since (empty if none) and when it turns on them
	 * (not joined: when it takes up the mask), whether it leads a band, the road point it keeps to, whether its band has
	 * met its old master yet, and the ways it saw its old master fight (a {@link FoeLore#KNOWN} row).
	 */
	public record Lost(String master, String name, long since, String joined, long turnAt, boolean bandit, long home, boolean knew,
		List<Integer> ways) {
		static final Codec<Lost> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("master").forGetter(Lost::master),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Lost::name),
			Codec.LONG.fieldOf("since").forGetter(Lost::since),
			Codec.STRING.optionalFieldOf("joined", "").forGetter(Lost::joined),
			Codec.LONG.optionalFieldOf("turn_at", 0L).forGetter(Lost::turnAt),
			Codec.BOOL.optionalFieldOf("bandit", false).forGetter(Lost::bandit),
			Codec.LONG.optionalFieldOf("home", NONE).forGetter(Lost::home),
			Codec.BOOL.optionalFieldOf("knew", false).forGetter(Lost::knew),
			Codec.INT.listOf().optionalFieldOf("ways", List.of()).forGetter(Lost::ways)).apply(i, Lost::new));

		Lost joined(String who, long at) {
			return new Lost(this.master, this.name, this.since, who, at, this.bandit, this.home, this.knew, this.ways);
		}

		Lost bandit(boolean b) {
			return new Lost(this.master, this.name, this.since, this.joined, this.turnAt, b, this.home, this.knew, this.ways);
		}

		Lost home(long at) {
			return new Lost(this.master, this.name, this.since, this.joined, this.turnAt, this.bandit, at, this.knew, this.ways);
		}

		Lost met() {
			return new Lost(this.master, this.name, this.since, this.joined, this.turnAt, this.bandit, this.home, true, this.ways);
		}
	}

	/** On a lost one. Saved. */
	public static final AttachmentType<Lost> LOST = AttachmentRegistry.create(JaymosKingdoms.id("masterless"),
		builder -> builder.persistent(Lost.CODEC));
	/** On a bandit leader's man: the leader's id. Saved. */
	public static final AttachmentType<String> BAND = AttachmentRegistry.create(JaymosKingdoms.id("masterless_band"),
		builder -> builder.persistent(Codec.STRING));
	/** On the overworld: players who started a new life → when (game time). Their old companions, met later, are lost. Saved. */
	static final AttachmentType<Map<String, Long>> FORSAKEN = AttachmentRegistry.create(JaymosKingdoms.id("forsaken_masters"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private static final Set<Villager> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Villager> CHECK = Collections.newSetFromMap(new IdentityHashMap<>());
	/** lost uuid|player uuid → when it last asked; lost uuid → when it last muttered; leader|player → last waylaid. */
	private static final Map<String, Long> ASKED = new HashMap<>(), WAYLAID = new HashMap<>();
	private static final Map<UUID, Long> MUTTERED = new HashMap<>();
	/** The lost that came round after a fall, waiting to be up. */
	private static final Set<UUID> FELL = new java.util.HashSet<>();

	private Masterless() {
	}

	static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(Masterless::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				if (v.hasAttached(LOST)) {
					LOADED.add(v);
				} else if (v.hasAttached(Loans.OWNER)) {
					CHECK.add(v);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				LOADED.remove(v);
				CHECK.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			CHECK.clear();
			ASKED.clear();
			WAYLAID.clear();
			MUTTERED.clear();
			FELL.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isShiftKeyDown()
				|| !(entity instanceof Villager v)) {
				return InteractionResult.PASS;
			}
			Lost lost = v.getAttached(LOST);
			if (lost == null || lost.bandit() || !lost.joined().isEmpty() || Possession.held(v) || Downed.isDowned(v)) {
				return InteractionResult.PASS;
			}
			long now = world.getGameTime();
			Long asked = ASKED.get(v.getUUID() + "|" + sp.getUUID());
			if (asked == null || now - asked > ASK_HOLDS) {
				return InteractionResult.PASS;
			}
			join(v, sp, now);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- what other systems ask --------------------------------------------------------------------------------------

	/** (Comrades.side) a bandit leader's band, or null. */
	static @Nullable String side(Entity e) {
		Lost lost = e.getAttached(LOST);
		if (lost != null && lost.bandit()) {
			return "lost:" + e.getUUID();
		}
		String band = e.getAttached(BAND);
		return band == null ? null : "lost:" + band;
	}

	public static boolean lost(Entity e) {
		return e.hasAttached(LOST);
	}

	/** (LifeStory.reincarnateAnew) {@code player} starts a new life: every companion of the old one is lost, now or when met. */
	static void forsaken(ServerPlayer player) {
		if (!McaCompat.LOADED) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		Map<String, Long> all = new HashMap<>(overworld.getAttachedOrElse(FORSAKEN, Map.of()));
		all.put(player.getUUID().toString(), now);
		overworld.setAttached(FORSAKEN, Map.copyOf(all));
		String id = player.getUUID().toString();
		String name = player.getGameProfile().name();
		List<Villager> theirs = new ArrayList<>();
		for (Mob m : Bonds.companions(player, 128.0)) {
			if (m instanceof Villager v) {
				theirs.add(v);
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			AABB box = new AABB(-3.0E7, level.getMinY() - 1, -3.0E7, 3.0E7, level.getMinY() + level.getHeight() + 1, 3.0E7);
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> {
				Loans.Owner o = v.getAttached(Loans.OWNER);
				return o != null && o.id().equals(id);
			})) {
				if (!theirs.contains(v)) {
					theirs.add(v);
				}
			}
		}
		for (Villager v : theirs) {
			if (losable(v)) {
				lose(v, id, name, ways(player));
			} else {
				CHECK.add(v);
			}
		}
	}

	/** (Loans, the table's "part ways for good") {@code player} lets {@code v} go: it's lost. */
	static void partWays(ServerPlayer player, Villager v) {
		if (!losable(v)) {
			return;
		}
		Callouts.say(v, "lost.parted", player, true, player.getDisplayName());
		lose(v, player.getUUID().toString(), player.getGameProfile().name(), ways(player));
	}

	/** One a camp holds, a sword it bought, a rival party's own, or one out on loan waits till it's back. */
	private static boolean losable(Villager v) {
		return v.isAlive() && !v.hasAttached(Loans.TAKEN) && !v.hasAttached(Veterans.SOLD) && !v.hasAttached(RivalParties.PARTY)
			&& !v.hasAttached(Loans.LOAN) && !v.hasAttached(LOST);
	}

	/** The most any band knows of how {@code player} fights: what a companion at their side saw. */
	private static List<Integer> ways(ServerPlayer player) {
		int size = 1 + FoeLore.Way.values().length;
		List<Integer> row = new ArrayList<>(Collections.nCopies(size, 0));
		for (List<Integer> r : player.getAttachedOrElse(FoeLore.KNOWN, Map.of()).values()) {
			for (int i = 1; i < size && i < r.size(); i++) {
				row.set(i, Math.max(row.get(i), r.get(i)));
			}
		}
		return List.copyOf(row);
	}

	private static void lose(Villager v, String master, String name, List<Integer> ways) {
		long now = v.level().getGameTime();
		McaCompat.stopFollowing(v);
		v.removeAttached(Followers.ORDER);
		v.removeAttached(Loans.OWNER);
		v.removeAttached(Loans.LEND);
		v.removeAttached(Loans.TAVERN);
		v.removeAttached(Loans.WANT);
		v.removeAttached(Loans.ERRAND);
		v.removeAttached(Loans.STAMPED);
		v.setPersistenceRequired();
		// some take up the mask at once (once no one's about to see the band gather), the rest if no one takes them in
		long mask = v.getRandom().nextFloat() < BANDIT_CHANCE ? now : now + BANDIT_AFTER;
		v.setAttached(LOST, new Lost(master, name, now, "", mask, false, NONE, false, ways));
		LOADED.add(v);
		CHECK.remove(v);
	}

	/** {@code v} joins {@code player}; it will turn on them. */
	private static void join(Villager v, ServerPlayer player, long now) {
		Lost lost = v.getAttached(LOST);
		if (lost == null) {
			return;
		}
		int turn = TURN_MIN + v.getRandom().nextInt(TURN_MAX - TURN_MIN + 1);
		v.setAttached(LOST, lost.joined(player.getUUID().toString(), now + turn));
		Followers.take(v, player, Followers.FOLLOW);
		Callouts.say(v, "lost.join", player, true, player.getDisplayName());
	}

	/** (Possession.release) the hold on a lost one broke: knocked down, it comes round right. */
	static void freed(Mob m, boolean knocked) {
		if (knocked) {
			FELL.add(m.getUUID());
			return;
		}
		settle(m);
	}

	/** A lost one that came round after its fall: it's right now, and the one it joined has a companion. */
	private static void settle(Mob m) {
		Lost lost = m.getAttached(LOST);
		m.removeAttached(LOST);
		if (m instanceof Villager v) {
			LOADED.remove(v);
		}
		if (lost == null || lost.joined().isEmpty() || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		UUID who;
		try {
			who = UUID.fromString(lost.joined());
		} catch (IllegalArgumentException e) {
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
		Bonds.add(m, who, 20);
		if (p != null && p.level() == level && m instanceof Villager v) {
			Followers.take(v, p, Followers.FOLLOW);
		}
		Callouts.say(m, "lost.free", p, true);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		// companions of a forsaken master, met since
		if (!CHECK.isEmpty()) {
			Map<String, Long> forsaken = server.overworld().getAttachedOrElse(FORSAKEN, Map.of());
			for (Villager v : new ArrayList<>(CHECK)) {
				Loans.Owner o = v.getAttached(Loans.OWNER);
				Long when = o == null ? null : forsaken.get(o.id());
				if (o == null || when == null || !v.isAlive() || v.isRemoved()) {
					CHECK.remove(v);
					continue;
				}
				if (v.getAttachedOrElse(Loans.STAMPED, Long.MIN_VALUE) >= when) {
					// taken up again by the new life
					CHECK.remove(v);
					continue;
				}
				if (losable(v)) {
					ServerPlayer master = server.getPlayerList().getPlayer(UUID.fromString(o.id()));
					lose(v, o.id(), o.name(), master == null ? List.of() : ways(master));
				}
			}
		}
		for (Villager v : new ArrayList<>(LOADED)) {
			Lost lost = v.getAttached(LOST);
			if (lost == null || !v.isAlive() || v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				LOADED.remove(v);
				continue;
			}
			if (Downed.isDowned(v)) {
				continue;
			}
			if (FELL.remove(v.getUUID())) {
				settle(v);
				continue;
			}
			if (Possession.held(v)) {
				continue;
			}
			if (!lost.joined().isEmpty()) {
				joined(level, v, lost, now);
			} else if (lost.bandit()) {
				leader(level, v, lost, now);
			} else {
				wander(level, v, lost, now);
			}
		}
	}

	/** Joined someone: at its time, it turns on them. */
	private static void joined(ServerLevel level, Villager v, Lost lost, long now) {
		ServerPlayer with = null;
		try {
			with = level.getServer().getPlayerList().getPlayer(UUID.fromString(lost.joined()));
		} catch (IllegalArgumentException e) {
			// not a player: never turns
		}
		if (now < lost.turnAt() || with == null || with.level() != level || with.distanceTo(v) > Possession.PREY_NEAR
			|| !("p:" + with.getUUID()).equals(Comrades.side(v))) {
			// not right: it mutters now and then (and turns only on the one it's with, with them by)
			mutter(v, now);
			return;
		}
		if (Possession.seize(v, "lost:" + v.getUUID(), -1, "lost", null)) {
			Callouts.say(v, "lost.turn", null, true);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 0.8F, 0.7F);
		}
	}

	private static void mutter(Villager v, long now) {
		if (now - MUTTERED.getOrDefault(v.getUUID(), now - MUTTER_EVERY / 2) > MUTTER_EVERY && v.getRandom().nextInt(3) == 0) {
			MUTTERED.put(v.getUUID(), now);
			Callouts.say(v, "lost.mutter", null, false);
		}
	}

	/** Not taken in: it walks its road, asks those it meets, and in time takes up the mask. */
	private static void wander(ServerLevel level, Villager v, Lost lost, long now) {
		boolean seen = !level.getPlayers(p -> !p.isSpectator() && p.distanceTo(v) < 32.0).isEmpty();
		if (!seen && now >= lost.turnAt()) {
			becomeBandit(level, v, lost);
			return;
		}
		walkRoad(level, v, lost);
		mutter(v, now);
		for (ServerPlayer p : level.getPlayers(p -> !p.isSpectator() && !Downed.isDowned(p) && p.distanceTo(v) < ASK_NEAR && v.hasLineOfSight(p))) {
			String key = v.getUUID() + "|" + p.getUUID();
			if (now - ASKED.getOrDefault(key, -ASK_EVERY * 2L) < ASK_EVERY) {
				continue;
			}
			ASKED.put(key, now);
			v.getNavigation().stop();
			v.getLookControl().setLookAt(p, 30.0F, 30.0F);
			Callouts.say(v, "lost.ask", p, true, p.getDisplayName());
			break;
		}
	}

	/** Make for a road, then walk up and down it. */
	private static void walkRoad(ServerLevel level, Villager v, Lost lost) {
		if (Comrades.target(v) != null || !v.getNavigation().isDone() || v.getRandom().nextInt(10) != 0) {
			return;
		}
		Roads.Road road = null;
		int at = -1;
		if (lost.home() != NONE) {
			BlockPos home = BlockPos.of(lost.home());
			for (Roads.Road r : Roads.network(level.getServer()).roads()) {
				int i = r.points().indexOf(lost.home());
				if (i >= 0) {
					road = r;
					at = i;
					break;
				}
			}
			if (road == null && home.distSqr(v.blockPosition()) > 16 * 16) {
				v.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.5);
				return;
			}
		}
		if (road == null) {
			// the nearest road
			double best = ROAD_SEEK * ROAD_SEEK;
			for (Roads.Road r : Roads.network(level.getServer()).roads()) {
				List<Long> pts = r.points();
				for (int i = 0; i < pts.size(); i += 4) {
					double d = BlockPos.of(pts.get(i)).distSqr(v.blockPosition());
					if (d < best) {
						best = d;
						road = r;
						at = i;
					}
				}
			}
			if (road == null) {
				// no road near: it keeps to where it is, wandering a little
				Vec3 to = v.position().add(v.getRandom().nextInt(17) - 8, 0.0, v.getRandom().nextInt(17) - 8);
				v.getNavigation().moveTo(to.x, to.y, to.z, 0.5);
				return;
			}
		}
		int next = Math.max(0, Math.min(road.points().size() - 1, at + (v.getRandom().nextBoolean() ? WALK : -WALK)));
		long point = road.points().get(next);
		v.setAttached(LOST, lost.home(point));
		BlockPos to = BlockPos.of(point);
		v.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.5);
	}

	/** {@code v} takes up the mask and gathers its men. */
	private static void becomeBandit(ServerLevel level, Villager v, Lost lost) {
		Lost now = lost.bandit(true);
		v.setAttached(LOST, now);
		if (Bandits.MASK != null) {
			v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Bandits.MASK));
			v.setDropChance(EquipmentSlot.HEAD, 1.0F);
		}
		int men = MEN_MIN + v.getRandom().nextInt(MEN_MAX - MEN_MIN + 1);
		Map<String, Integer> stars = v.getAttachedOrElse(FoeLore.STARS, Map.of());
		int wins = v.getAttachedOrElse(Learners.WINS, 0);
		for (int i = 0; i < men; i++) {
			Vec3 at = v.position().add(v.getRandom().nextDouble() * 4.0 - 2.0, 0.0, v.getRandom().nextDouble() * 4.0 - 2.0);
			Villager man = McaCompat.visitor(level, at, null, false);
			if (man == null) {
				continue;
			}
			McaBridge.toGuard(man);
			Veterans.make(man, Veterans.VETERAN);
			if (Bandits.BANDANA != null) {
				man.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Bandits.BANDANA));
			}
			// what the old master taught the leader, the leader teaches them
			if (!stars.isEmpty()) {
				man.setAttached(FoeLore.STARS, Map.copyOf(stars));
			}
			if (wins > 0) {
				man.setAttached(Learners.WINS, wins / 2);
			}
			man.setAttached(BAND, v.getUUID().toString());
			man.setPersistenceRequired();
		}
		// a price on their head at the nearest town
		Faction town = Factions.near(level.getServer(), v.blockPosition(), 512.0);
		if (town != null) {
			Bounties.crime(v, town.id(), BOUNTY);
		}
		JaymosKingdoms.LOGGER.debug("A lost companion of {} took up the mask with {} men", lost.name(), men);
	}

	/** A bandit leader: keeps its men near, waylays travellers, and knows its old master. */
	private static void leader(ServerLevel level, Villager v, Lost lost, long now) {
		List<Mob> men = level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(48.0), e -> e.isAlive() && !Downed.isDowned(e)
			&& v.getUUID().toString().equals(e.getAttached(BAND)));
		for (Mob man : men) {
			if (man.distanceTo(v) > MEN_KEEP && Comrades.target(man) == null && man.getNavigation().isDone()) {
				man.getNavigation().moveTo(v, 0.6);
			}
		}
		if (Comrades.target(v) == null) {
			walkRoad(level, v, lost);
		}
		String side = "lost:" + v.getUUID();
		for (ServerPlayer p : level.getPlayers(p -> !p.isSpectator() && !p.isCreative() && !Downed.isDowned(p) && p.distanceTo(v) < KNOWS_NEAR
			&& v.hasLineOfSight(p))) {
			if (!lost.knew() && p.getUUID().toString().equals(lost.master())) {
				knowsMaster(p, side, lost);
				v.setAttached(LOST, lost.met());
				Callouts.say(v, "lost.knows", p, true, p.getDisplayName());
			}
			if (p.distanceTo(v) > WAYLAY_NEAR || Parleys.spares(v, p)) {
				continue;
			}
			String key = v.getUUID() + "|" + p.getUUID();
			if (now - WAYLAID.getOrDefault(key, -WAYLAY_EVERY * 2L) >= WAYLAY_EVERY) {
				WAYLAID.put(key, now);
				Callouts.say(v, "lost.waylay", p, true, p.getDisplayName());
			}
			if (Comrades.target(v) == null) {
				Comrades.aim(v, p);
			}
			for (Mob man : men) {
				if (Comrades.target(man) == null) {
					Comrades.aim(man, p);
				}
			}
		}
	}

	/**
	 * The band knows its old master's ways: what the leader saw at their side in the old life, and the most any band
	 * knows of them now, as though met often enough to come ready.
	 */
	private static void knowsMaster(ServerPlayer p, String side, Lost lost) {
		Map<String, List<Integer>> known = new HashMap<>(p.getAttachedOrElse(FoeLore.KNOWN, Map.of()));
		List<Integer> now = ways(p);
		List<Integer> mine = known.getOrDefault(side, List.of());
		List<Integer> row = new ArrayList<>(now);
		for (int i = 1; i < row.size(); i++) {
			int saw = i < lost.ways().size() ? lost.ways().get(i) : 0;
			int had = i < mine.size() ? mine.get(i) : 0;
			row.set(i, Math.max(row.get(i), Math.max(saw, had)));
		}
		row.set(0, Math.max(KNOWN_MEETINGS, mine.isEmpty() ? 0 : mine.get(0)));
		known.put(side, List.copyOf(row));
		p.setAttached(FoeLore.KNOWN, known);
	}

	/** (ThrallTargetMixin) a bandit leader and its men never go for one another. */
	public static boolean sameBand(LivingEntity a, LivingEntity b) {
		String sa = side(a), sb = side(b);
		return sa != null && sa.equals(sb);
	}
}
