package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.OldRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Hunters seek your lair by day (Night's Blood, patch 6): once a realm's suspicion of a vampire player is high
 * ({@link #suspect}, fed by each witness of {@link Suspicion}), a party of that realm's hunters (guards with torches and
 * stakes, and a priest; the way {@link Hunts} sends guards after someone it hates) comes by day looking for their coffin
 * ({@link CoffinRest#LAIR}), from {@link #SPAWN_FAR} off, never sooner than {@link #GAP_TICKS} after the last. Andy's plan:
 * "You hear them coming through the heartbeat" ({@link #dread}, read by {@link BloodFever}'s heartbeat). They walk to the
 * coffin; whoever finds a sleeper in it stakes them (which kills for good, {@link OldRules#stakeAsleep}); finding it empty
 * they tear it down. A coffin that was moved ({@link CoffinKit}) is not where they look, and they go home empty-handed.
 * They give up at dusk, after {@link #PARTY_TICKS}, or if they lose the lair.
 */
public final class LairHunts {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** A realm's suspicion of one player, in points: high from here, never more than the most; one point fades each this many ticks. */
	static final int HIGH = 10, MOST = 24;
	static final long DECAY_TICKS = 6000L;
	/** The lair may be this far (blocks) from the realm's seat for it to send a party. */
	static final double SEND_FROM = 800.0;
	/** Considered this often (ticks), with this chance, and at most one party in this long (ticks). */
	static final int CHECK_EVERY = 20 * 30;
	static final float CHANCE = 0.25F;
	static final long GAP_TICKS = 24000L;
	/** The party comes on from this far (blocks) off the lair. */
	static final double SPAWN_FAR = 44.0;
	/** Guards: this many, one more for each this many points over {@link #HIGH}, at most {@link #GUARDS_MOST}; plus a priest. */
	static final int GUARDS_LEAST = 2, GUARDS_MOST = 5, POINTS_PER_GUARD = 4;
	/** They look this long (ticks), and give up when the lair is lost this far (blocks). */
	static final long PARTY_TICKS = 20L * 60 * 6;
	static final double LOSE = 200.0;
	/** One of them within this (blocks) of the coffin has found it; within {@link #NEAR_FLAT} flat for {@link #DIG_TICKS}, they break through to it. */
	static final double REACH = 3.5, NEAR_FLAT = 10.0;
	static final long DIG_TICKS = 20L * 25;
	/** They see the vampire themselves from this far (blocks). */
	static final double SIGHT = 28.0;
	/** The heartbeat starts from this far (blocks) off the nearest of them. */
	static final double HEAR = 96.0;
	static final double SPEED = 1.0;
	/** How many lines each speaker has. */
	static final int LINES = 3;
	static final String TAG = "jaymoskingdoms.lairhunter";

	/** What a realm suspects of one player: points, and when they were last added. */
	record Tally(int points, long at) {
		static final Codec<Tally> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("points").forGetter(Tally::points),
			Codec.LONG.fieldOf("at").forGetter(Tally::at)).apply(i, Tally::new));

		int now(long time) {
			return Math.max(0, points - (int) Math.max(0L, (time - at) / DECAY_TICKS));
		}
	}

	/** On a vampire player: each realm's suspicion of them (faction id). Kept. */
	static final AttachmentType<Map<String, Tally>> REALMS = AttachmentRegistry.create(JaymosKingdoms.id("realm_suspicion"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Tally.CODEC)).copyOnDeath());
	/** On a vampire player: when a party was last sent. Kept. */
	static final AttachmentType<Long> SENT_AT = AttachmentRegistry.create(JaymosKingdoms.id("lair_hunt_sent"), b -> b.persistent(Codec.LONG));

	private static final class Party {
		final UUID player;
		final int from;
		final BlockPos lair;
		final List<UUID> men = new ArrayList<>();
		final long since;
		long near = -1L;
		long done = -1L;
		boolean spoke;

		Party(UUID player, int from, BlockPos lair, long since) {
			this.player = player;
			this.from = from;
			this.lair = lair;
			this.since = since;
		}
	}

	private static final Map<UUID, Party> ACTIVE = new HashMap<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	/** The men of the party sent after {@code player}'s lair, those loaded; empty if none are out ({@link Fledglings} come to their sire's side). */
	static List<Mob> men(MinecraftServer server, UUID player) {
		Party party = ACTIVE.get(player);
		List<Mob> out = new ArrayList<>();
		if (party != null) {
			for (UUID id : party.men) {
				for (ServerLevel level : server.getAllLevels()) {
					if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
						out.add(m);
					}
				}
			}
		}
		return out;
	}

	private LairHunts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LairHunts::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && ACTIVE.values().stream().noneMatch(p -> p.men.contains(entity.getUUID()))) {
				STRAYS.add(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			STRAYS.clear();
		});
	}

	// ---- a realm's suspicion -------------------------------------------------------------------------------------------

	/** A witness of {@code realm}'s sees {@code player} for what they are: {@code add} points more. */
	static void suspect(ServerPlayer player, Faction realm, int add, long now) {
		if (!realm.type().isRealm() || !Vampires.isVampire(player) || VampirePlayers.dhampir(player)) {
			return;
		}
		Map<String, Tally> all = new HashMap<>(player.getAttachedOrElse(REALMS, Map.<String, Tally>of()));
		String key = Integer.toString(realm.id());
		Tally old = all.get(key);
		int points = Math.min(MOST, (old == null ? 0 : old.now(now)) + add);
		all.put(key, new Tally(points, now));
		player.setAttached(REALMS, all);
	}

	static int suspicion(ServerPlayer player, int realm, long now) {
		Tally t = player.getAttachedOrElse(REALMS, Map.<String, Tally>of()).get(Integer.toString(realm));
		return t == null ? 0 : t.now(now);
	}

	// ---- the heartbeat -------------------------------------------------------------------------------------------------

	/** How loud a hunting party's coming beats for {@code player} (0 none, 1 at their door); read into their heartbeat. */
	static float dread(ServerPlayer player) {
		Party p = ACTIVE.get(player.getUUID());
		if (p == null || !(player.level() instanceof ServerLevel level)) {
			return 0.0F;
		}
		double best = Double.MAX_VALUE;
		for (UUID id : p.men) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				best = Math.min(best, m.distanceTo(player));
			}
		}
		return best >= HEAR ? 0.0F : (float) (1.0 - best / HEAR);
	}

	// ---- sending them --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!STRAYS.isEmpty()) {
			for (Entity e : List.copyOf(STRAYS)) {
				if (e.isAlive()) {
					McaCompat.vanish(e);
				}
			}
			STRAYS.clear();
		}
		long now = level.getGameTime();
		if (now % 20 == 0) {
			drive(server, level, now);
		}
		if (now % CHECK_EVERY != 0 || Vampires.night(level.getOverworldClockTime())) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || !p.isAlive() || ACTIVE.containsKey(p.getUUID()) || !Vampires.isVampire(p) || VampirePlayers.dhampir(p)
				|| now - p.getAttachedOrElse(SENT_AT, -GAP_TICKS) < GAP_TICKS || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			BlockPos lair = CoffinRest.lair(p, level);
			if (lair == null || !Loaded.made(level, lair) || !OldRules.coffin(level, lair)) {
				continue;
			}
			Faction from = hater(server, p, lair, now);
			if (from != null) {
				send(level, p, from, lair, now);
			}
		}
	}

	/** The realm near enough whose suspicion of {@code p} is highest (and high), or null. */
	private static @Nullable Faction hater(MinecraftServer server, ServerPlayer p, BlockPos lair, long now) {
		Faction best = null;
		int worst = HIGH - 1;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || f.seat().distSqr(lair) > SEND_FROM * SEND_FROM) {
				continue;
			}
			int s = suspicion(p, f.id(), now);
			if (s > worst) {
				best = f;
				worst = s;
			}
		}
		return best;
	}

	private static void send(ServerLevel level, ServerPlayer p, Faction from, BlockPos lair, long now) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		BlockPos column = BlockPos.containing(lair.getX() + Math.cos(angle) * SPAWN_FAR, lair.getY(), lair.getZ() + Math.sin(angle) * SPAWN_FAR);
		if (!Loaded.made(level, column)) {
			return;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
			return;
		}
		Party party = new Party(p.getUUID(), from.id(), lair, now);
		int points = suspicion(p, from.id(), now);
		int guards = Math.min(GUARDS_MOST, GUARDS_LEAST + Math.max(0, (points - HIGH) / POINTS_PER_GUARD));
		for (int i = 0; i < guards; i++) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2));
			Mob m = Hunts.man(level, from, i, spot);
			if (m == null) {
				continue;
			}
			arm(m);
			enlist(party, m, lair);
		}
		Villager priest = McaCompat.visitor(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(at), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.CLERIC).value(), false);
		if (priest != null) {
			enlist(party, priest, lair);
		}
		if (party.men.isEmpty()) {
			return;
		}
		p.setAttached(SENT_AT, now);
		ACTIVE.put(p.getUUID(), party);
		JaymosKingdoms.LOGGER.info("{} sends {} hunters after {}'s lair", from.name(), party.men.size(), p.getName().getString());
	}

	private static void enlist(Party party, Mob m, BlockPos lair) {
		m.addTag(TAG);
		if (m instanceof PathfinderMob pm) {
			pm.setHomeTo(lair, (int) LOSE);
		}
		party.men.add(m.getUUID());
	}

	/** A stake in the hand and a torch in the other. */
	private static void arm(Mob m) {
		ItemStack stake = BuiltInRegistries.ITEM.getOptional(Identifier.parse(Vampires.STAKE_ITEM)).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.STICK));
		m.setItemSlot(EquipmentSlot.MAINHAND, stake);
		m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
		m.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
	}

	// ---- looking --------------------------------------------------------------------------------------------------------

	private static void drive(MinecraftServer server, ServerLevel level, long now) {
		boolean night = Vampires.night(level.getOverworldClockTime());
		for (Iterator<Party> it = ACTIVE.values().iterator(); it.hasNext();) {
			Party party = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(party.player);
			List<Mob> men = new ArrayList<>();
			for (UUID id : party.men) {
				if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
					men.add(m);
				}
			}
			boolean over = men.isEmpty() || night || p == null || p.level() != level || now - party.since > PARTY_TICKS
				|| p.distanceToSqr(party.lair.getX(), party.lair.getY(), party.lair.getZ()) > LOSE * LOSE
				|| party.done >= 0L && now - party.done > 100L;
			if (over) {
				for (Mob m : men) {
					McaCompat.vanish(m);
				}
				it.remove();
				continue;
			}
			if (party.done >= 0L) {
				continue;
			}
			boolean coffin = OldRules.coffin(level, party.lair);
			boolean found = false, nearFlat = false;
			for (Mob m : men) {
				if (Downed.isDowned(m)) {
					continue;
				}
				if (m.distanceTo(p) <= SIGHT && m.hasLineOfSight(p) && !p.isSleeping()) {
					Comrades.aim(m, p);
					say(level, party, m, "seen");
					continue;
				}
				double dx = m.getX() - party.lair.getX() - 0.5, dz = m.getZ() - party.lair.getZ() - 0.5;
				found |= coffin && m.distanceToSqr(party.lair.getX() + 0.5, party.lair.getY() + 0.5, party.lair.getZ() + 0.5) <= REACH * REACH;
				nearFlat |= dx * dx + dz * dz <= NEAR_FLAT * NEAR_FLAT;
				if (!coffin) {
					continue;
				}
				if (m instanceof PathfinderMob pm && pm.getNavigation().isDone()) {
					pm.getNavigation().moveTo(party.lair.getX() + 0.5, party.lair.getY(), party.lair.getZ() + 0.5, SPEED);
				}
			}
			if (!coffin) {
				// moved: nothing where they were told it was
				if (party.near < 0L) {
					party.near = now;
				} else if (now - party.near > 200L) {
					party.done = now;
				}
				continue;
			}
			if (nearFlat && party.near < 0L) {
				party.near = now;
			}
			if (found || party.near >= 0L && now - party.near > DIG_TICKS) {
				breakIn(level, party, p, men);
				party.done = now;
			}
		}
	}

	/** They are at the coffin: a sleeper in it is staked; an empty one is torn down. */
	private static void breakIn(ServerLevel level, Party party, ServerPlayer owner, List<Mob> men) {
		BlockPos bed = party.lair;
		for (ServerPlayer sleeper : level.players()) {
			if (sleeper.isSleeping() && sleeper.getSleepingPos().map(s -> s.distSqr(bed) <= 2.0).orElse(false) && Vampires.isVampire(sleeper)) {
				OldRules.stakeAsleep(level, sleeper);
				return;
			}
		}
		CoffinKit.wreck(level, bed);
		if (!men.isEmpty()) {
			say(level, party, men.getFirst(), "tore");
		}
	}

	private static void say(ServerLevel level, Party party, Mob m, String what) {
		if (what.equals("seen") && party.spoke) {
			return;
		}
		party.spoke = party.spoke || what.equals("seen");
		Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.lair_hunt." + what + "." + level.getRandom().nextInt(LINES)), Speech.YELLOW, 24.0);
	}
}
