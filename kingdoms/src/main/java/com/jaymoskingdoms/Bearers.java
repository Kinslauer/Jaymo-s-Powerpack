package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.132) After the fighting, the townsfolk clear the field of the dead (with Jaymo's Kitchen's corpses). Once things
 * are quiet round a body lying in a settlement's land (no monsters about, nobody fighting, no raid), a grown villager (or
 * one of its working folk, or a guard with nothing to fight) comes out, takes it up in their arms and carries it off:
 * <ul>
 * <li><b>Their own dead</b> (a villager, a wandering trader who died among them, or the settlement's own folk) go to its
 * <b>graveyard</b>: the bearer digs, lowers them in and raises a headstone (cobblestone in a village, stone brick in a
 * realm, sandstone in the desert) over a mound of turned earth. The graveyard is laid out in rows on open ground a little
 * way from the seat ({@link #GRAVEYARD_MIN}-{@link #GRAVEYARD_MAX} blocks) and grows grave by grave. Right-click a
 * headstone with an empty hand to read who lies there.</li>
 * <li><b>The enemy's</b> (raiders, barbarians, orcs, goblins, a realm at war's soldiers, looters and assassins) are left a
 * while ({@link #FOE_SETTLE}: the victors, and looters, see to their pockets first), then burned: thrown into a safe fire
 * pit, campfire or lava nearby, or lit where they lie on bare ground ({@link Undertakers}' rules).</li>
 * </ul>
 * Monsters are still the guards' and golems' to burn ({@link Undertakers}); animals are left to be butchered; a player's
 * body is never touched (their things stay theirs).
 * <p>
 * <b>Mourning.</b> With MCA, the dead's family (their spouse first, then their kin) come to the grave once it's dug:
 * they kneel, weep, say a word, and the first leaves a flower on the mound; it weighs on their mood. A villager with no
 * family is mourned by a neighbour or two; a fallen soldier by his comrades, standing. For a few days after, the family
 * may come back to visit.
 * <p>
 * <b>Helping.</b> A player who carries one of a settlement's dead in their arms (sneak, empty hand) and lets a bearer take
 * them to the grave earns its thanks and a little reputation ({@link #REP_HELP}, at most {@link #HELP_DAILY} a day).
 */
public final class Bearers {
	/** Bodies this close to a settlement's seat are seen to (only those in its own land); bearers this close to a body may answer. */
	static final double SWEEP = 64.0, BEARER_REACH = 40.0;
	/** Nothing hostile, nobody fighting this close to a body, before anyone comes out for it. */
	static final double CALM_RANGE = 20.0;
	/** How long their own dead lie before they're fetched (ticks); the enemy's are left longer, for their pockets to be seen to. */
	static final long CALM_TICKS = 400L, FOE_SETTLE = 2400L;
	/** How often duties are walked (ticks), and new ones handed out; the most bodies taken on per settlement per round. */
	static final int STEP = 5, ASSIGN = 40, MOST_PER_ROUND = 2;
	/** Close enough to take a body up; to stand at a grave and dig; to light a body. */
	static final double REACH = 2.2, GRAVESIDE = 1.8;
	/** Digging a grave takes this long (ticks), with a spadeful every {@link #DIG_EVERY}. */
	static final int DIG_TICKS = 80, DIG_EVERY = 10;
	/** How long a duty may take before it's given up (ticks); a body given up on is left this long before it's tried again. */
	static final long GIVE_UP = 2400L, RETRY = 1200L;
	/** A villager's walking speed (its brain's scale); anything else's (navigation). */
	static final float VILLAGER_SPEED = 0.55F;
	static final double SPEED = 0.9;
	/** Seconds a body is set burning for (Kitchen keeps it burning till it's ash once it's caught). */
	static final float BURN_SECONDS = 8.0F;
	/** A safe fire this close to an enemy's body is where it's thrown. */
	static final int PYRE_REACH = 16;

	/** The graveyard: this far from the seat at the nearest and farthest, searched in rings this far apart. */
	static final int GRAVEYARD_MIN = 16, GRAVEYARD_MAX = 44, RING_STEP = 3, RING_ANGLES = 20;
	/** Graves stand this far apart along a row, rows this far apart; a graveyard holds this many either side of its first, and rows. */
	static final int GRAVE_SPACING = 2, ROW_SPACING = 3, ROW_HALF = 4, ROWS = 4;
	/** The share of headstones that are mossy. */
	static final float MOSSY = 0.25F;

	/** Mourners: how far they come from; at most this many; how long they grieve (ticks; family longer); tears and sighs. */
	static final double MOURN_REACH = 64.0, FRIEND_REACH = 32.0;
	static final int MOST_MOURNERS = 4, MOST_FRIENDS = 2;
	static final float FRIEND_CHANCE = 0.5F;
	static final int GRIEVE_TICKS = 500, KIN_GRIEVE_TICKS = 800, VISIT_TICKS = 400, TEAR_EVERY = 20, SIGH_EVERY = 100;
	/** How long a mourner may take to get there (ticks). */
	static final long MOURN_WALK = 1200L;
	/** MCA mood lost at a grave: a spouse, kin, a friend. */
	static final int GRIEF_PARTNER = -6, GRIEF_KIN = -4, GRIEF_FRIEND = -1;
	/** For this many days after a burial, family nearby may come back to the grave, each day with this chance. */
	static final int VISIT_DAYS = 4;
	static final float VISIT_CHANCE = 0.35F;
	/** Lines a bearer, a mourner (per kind) or a grateful bearer may say. */
	static final int RITE_LINES = 4, MOURN_LINES = 3, THANKS_LINES = 3;
	static final double SAY_RANGE = 16.0;

	/** A player who carried a body this recently (ticks) helped bury it; reputation for it, at most this many times a day per settlement. */
	static final long HELP_WINDOW = 2400L;
	static final int REP_HELP = 3, HELP_DAILY = 3;

	/** A grave: whose settlement, its headstone, who lies there (their name, their UUID or ""), and the day they were buried. */
	record Grave(int realm, long head, Component name, String dead, long day) {
		static final Codec<Grave> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("realm").forGetter(Grave::realm),
				Codec.LONG.fieldOf("head").forGetter(Grave::head),
				ComponentSerialization.CODEC.fieldOf("name").forGetter(Grave::name),
				Codec.STRING.optionalFieldOf("dead", "").forGetter(Grave::dead),
				Codec.LONG.fieldOf("day").forGetter(Grave::day))
			.apply(i, Grave::new));

		BlockPos headPos() {
			return BlockPos.of(head);
		}

		Optional<UUID> deadId() {
			try {
				return dead.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(dead));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}
	}

	/** Every settlement's graves (on the Overworld). */
	static final AttachmentType<List<Grave>> GRAVES = AttachmentRegistry.create(JaymosKingdoms.id("graves"),
		builder -> builder.persistent(Grave.CODEC.listOf()));

	/**
	 * Who a body was, read from its saved data: what it was, its UUID, its name, and if it was one of the realms' folk,
	 * whose ({@code realm}, -1 for none) and what ({@code role}).
	 */
	record Dead(@Nullable EntityType<?> type, Optional<UUID> id, Component name, int realm, int role) {
		boolean villager() {
			if (type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER) {
				return true;
			}
			if (type == null) {
				return false;
			}
			Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			return key.getNamespace().equals("mca") && key.getPath().endsWith("_villager") && !key.getPath().contains("zombie");
		}

		boolean folk() {
			return type == JaymosKingdoms.FOLK;
		}

		/** A person (not a monster, not an animal). */
		boolean person() {
			return villager() || folk();
		}

		/** One of {@code f}'s own people: a villager in its land, or its own folk. */
		boolean ours(Faction f) {
			return villager() || folk() && realm == f.id();
		}

		/** A soldier (a fighting folk role). */
		boolean soldier() {
			Folk.Role r = Folk.role(role);
			return folk() && (r == null || r.combatant());
		}
	}

	enum Kind { BURY, PYRE, BURN }

	enum Stage { FETCH, HAUL, DIG, LIGHT }

	private static final class Duty {
		final UUID bearer, corpse;
		final int realm;
		final Kind kind;
		/** The grave's headstone ({@link Kind#BURY}), the fire ({@link Kind#PYRE}), a bare spot ({@link Kind#BURN}) or null to burn it where it lies. */
		final @Nullable BlockPos to;
		final Dead dead;
		final long until;
		Stage stage = Stage.FETCH;
		int dug;

		Duty(UUID bearer, UUID corpse, int realm, Kind kind, @Nullable BlockPos to, Dead dead, long until) {
			this.bearer = bearer;
			this.corpse = corpse;
			this.realm = realm;
			this.kind = kind;
			this.to = to;
			this.dead = dead;
			this.until = until;
		}
	}

	enum Grief { PARTNER, KIN, FRIEND, COMRADE, VISIT }

	private static final class Mourner {
		final UUID who;
		final BlockPos head, stand;
		final Component name;
		final Grief grief;
		final boolean flower;
		final long walkUntil;
		boolean grieving;
		int grieved;

		Mourner(UUID who, BlockPos head, BlockPos stand, Component name, Grief grief, boolean flower, long walkUntil) {
			this.who = who;
			this.head = head;
			this.stand = stand;
			this.name = name;
			this.grief = grief;
			this.flower = flower;
			this.walkUntil = walkUntil;
		}

		int ticks() {
			return switch (grief) {
				case PARTNER, KIN -> KIN_GRIEVE_TICKS;
				case VISIT -> VISIT_TICKS;
				default -> GRIEVE_TICKS;
			};
		}
	}

	private record Carry(UUID player, long at) {
	}

	/** Bearer's UUID → its duty. */
	private static final Map<UUID, Duty> DUTIES = new HashMap<>();
	/** Bodies no plan could be made for (or a bearer gave up on), and when they may be tried again. */
	private static final Map<UUID, Long> LEFT = new HashMap<>();
	/** Graves being dug (their headstones), so no two bearers dig the same one. */
	private static final Map<Long, UUID> RESERVED = new HashMap<>();
	/** Mourner's UUID → their grief. */
	private static final Map<UUID, Mourner> MOURNERS = new HashMap<>();
	/** Bodies players have been carrying: by whom, and when last. */
	private static final Map<UUID, Carry> CARRIED = new HashMap<>();
	/** Player + settlement + day → how many times they've been thanked for it today. */
	private static final Map<String, Integer> HELPED = new HashMap<>();
	/** Who bodies were (read once from their saved data). */
	private static final Map<UUID, Dead> WHO = new HashMap<>();
	private static long lastDay = Long.MIN_VALUE;

	private Bearers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bearers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUTIES.clear();
			LEFT.clear();
			RESERVED.clear();
			MOURNERS.clear();
			CARRIED.clear();
			HELPED.clear();
			WHO.clear();
			lastDay = Long.MIN_VALUE;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel server && player instanceof ServerPlayer sp
				&& player.getItemInHand(hand).isEmpty() && level.getBlockState(hit.getBlockPos()).is(BlockTags.WALLS)) {
				readHeadstone(server, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
	}

	/** {@code corpse} is being seen to by a bearer (so a golem, a guard or a looter leaves it be). */
	static boolean claimed(UUID corpse) {
		for (Duty d : DUTIES.values()) {
			if (d.corpse.equals(corpse)) {
				return true;
			}
		}
		return false;
	}

	/** {@code mob} is busy carrying the dead or mourning them. */
	static boolean busy(Entity mob) {
		return DUTIES.containsKey(mob.getUUID()) || MOURNERS.containsKey(mob.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (!KitchenCompat.burials()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0) {
			if (!DUTIES.isEmpty()) {
				for (Iterator<Duty> it = DUTIES.values().iterator(); it.hasNext(); ) {
					Duty d = it.next();
					if (!step(level, d, now)) {
						it.remove();
						if (d.to != null) {
							RESERVED.remove(d.to.asLong(), d.bearer);
						}
					}
				}
			}
			if (!MOURNERS.isEmpty()) {
				for (Iterator<Mourner> it = MOURNERS.values().iterator(); it.hasNext(); ) {
					Mourner m = it.next();
					if (!mourn(level, m, now)) {
						it.remove();
						stopGrieving(level, m);
					}
				}
			}
		}
		if (now % ASSIGN == 0) {
			LEFT.values().removeIf(until -> until < now);
			CARRIED.values().removeIf(c -> now - c.at() > HELP_WINDOW);
			if (WHO.size() > 512) {
				WHO.clear();
			}
			for (ServerPlayer p : level.players()) {
				Entity carried = KitchenCompat.carriedBy(p);
				if (carried != null) {
					CARRIED.put(carried.getUUID(), new Carry(p.getUUID(), now));
				}
			}
			for (Faction f : Factions.all(server)) {
				if (!f.fallen() && f.type() != FactionType.BARBARIAN && level.isLoaded(f.seat())) {
					assign(level, f, now);
				}
			}
			long day = Production.day(server);
			if (lastDay == Long.MIN_VALUE) {
				lastDay = day;
			} else if (day != lastDay) {
				lastDay = day;
				HELPED.clear();
				visits(level, day, now);
			}
		}
	}

	// ---- who the dead were ----------------------------------------------------------------------------------------------

	static Dead who(ServerLevel level, Entity corpse) {
		Dead known = WHO.get(corpse.getUUID());
		if (known != null) {
			return known;
		}
		CompoundTag tag = KitchenCompat.lookTag(corpse);
		Dead dead;
		if (tag == null) {
			dead = new Dead(null, Optional.empty(), corpse.getName(), -1, -1);
		} else {
			Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
			EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			Optional<UUID> uuid = tag.read("UUID", UUIDUtil.CODEC);
			Component name = tag.read("CustomName", ComponentSerialization.CODEC)
				.orElse(type != null ? type.getDescription() : corpse.getName());
			boolean folk = type == JaymosKingdoms.FOLK;
			dead = new Dead(type, uuid, name, folk ? tag.getIntOr("kingdom", -1) : -1, folk ? tag.getIntOr("role", -1) : -1);
		}
		WHO.put(corpse.getUUID(), dead);
		return dead;
	}

	// ---- handing out the dead -------------------------------------------------------------------------------------------

	private static void assign(ServerLevel level, Faction f, long now) {
		AABB area = new AABB(f.seat()).inflate(SWEEP, 24.0, SWEEP);
		int taken = 0;
		for (Entity corpse : level.getEntities((Entity)null, area, KitchenCompat::isFreeCorpse)) {
			if (taken >= MOST_PER_ROUND) {
				return;
			}
			UUID id = corpse.getUUID();
			if (KitchenCompat.isPlayerCorpse(corpse) || LEFT.containsKey(id) || claimed(id) || Looters.claimed(id)) {
				continue;
			}
			Dead dead = who(level, corpse);
			if (!dead.person()) {
				continue;
			}
			Faction law = Theft.lawAt(level, corpse.blockPosition());
			if (law == null || law.id() != f.id()) {
				continue;
			}
			boolean ours = dead.ours(f);
			if (KitchenCompat.sinceDeath(corpse) < (ours ? CALM_TICKS : FOE_SETTLE) || !calm(level, corpse.blockPosition())) {
				continue;
			}
			PathfinderMob bearer = freeBearer(level, f, corpse);
			if (bearer == null) {
				// nobody free near this one: left a while, so it doesn't hold up the rest
				LEFT.put(id, now + RETRY);
				continue;
			}
			Duty duty;
			if (ours) {
				BlockPos grave = slot(level, f);
				if (grave == null) {
					// no ground for a grave anywhere near: left a while
					LEFT.put(id, now + RETRY);
					continue;
				}
				duty = new Duty(bearer.getUUID(), id, f.id(), Kind.BURY, grave, dead, now + GIVE_UP);
				RESERVED.put(grave.asLong(), bearer.getUUID());
			} else {
				BlockPos pyre = Undertakers.pyre(level, corpse.blockPosition(), PYRE_REACH);
				if (pyre != null) {
					duty = new Duty(bearer.getUUID(), id, f.id(), Kind.PYRE, pyre, dead, now + GIVE_UP);
				} else if (Undertakers.clear(level, corpse.blockPosition())) {
					duty = new Duty(bearer.getUUID(), id, f.id(), Kind.BURN, null, dead, now + GIVE_UP);
				} else {
					BlockPos spot = Undertakers.spot(level, corpse.blockPosition());
					if (spot == null) {
						LEFT.put(id, now + RETRY);
						continue;
					}
					duty = new Duty(bearer.getUUID(), id, f.id(), Kind.BURN, spot, dead, now + GIVE_UP);
				}
			}
			DUTIES.put(bearer.getUUID(), duty);
			taken++;
			JaymosKingdoms.LOGGER.debug("{} of {} takes {}'s body at {} ({})", bearer.getName().getString(), f.name(), dead.name().getString(),
				corpse.blockPosition(), duty.kind);
		}
	}

	/**
	 * (0.7.132) A person's body lying in a settlement's land: the settlement's to bury or burn (so iron golems leave it be,
	 * however long the fighting keeps the bearers away).
	 */
	static boolean theirs(ServerLevel level, Entity corpse) {
		return KitchenCompat.burials() && who(level, corpse).person() && Theft.lawAt(level, corpse.blockPosition()) != null;
	}

	/** Quiet round {@code at}: no monster, nobody fighting, no raid under way. */
	static boolean calm(ServerLevel level, BlockPos at) {
		Raid raid = level.getRaidAt(at);
		if (raid != null && raid.isActive()) {
			return false;
		}
		AABB box = new AABB(at).inflate(CALM_RANGE, 8.0, CALM_RANGE);
		return level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && (m instanceof Enemy || m.getTarget() != null && m.getTarget().isAlive())).isEmpty();
	}

	/** The nearest of {@code f}'s people to {@code corpse} free to carry it: a grown villager, a working folk, a guard with nothing to fight. */
	private static @Nullable PathfinderMob freeBearer(ServerLevel level, Faction f, Entity corpse) {
		AABB box = corpse.getBoundingBox().inflate(BEARER_REACH, 12.0, BEARER_REACH);
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
			if (!freeVillager(v)) {
				continue;
			}
			Faction theirs = Theft.lawAt(level, v.blockPosition());
			if (theirs == null || theirs.id() != f.id()) {
				continue;
			}
			double d = v.distanceToSqr(corpse);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		for (Folk folk : level.getEntitiesOfClass(Folk.class, box, x -> x.isAlive() && x.factionId() == f.id() && !x.isSleeping() && !x.isBaby())) {
			int role = folk.role();
			if (folk.leader() || folk.getTarget() != null || folk.combatant() && role != Folk.GUARD || role == Folk.CARTER || role == Folk.PULLER
				|| role == Folk.STABLEHAND || role == Folk.FARRIER || busy(folk) || Undertakers.busy(folk)) {
				continue;
			}
			double d = folk.distanceToSqr(corpse);
			if (d < bestD) {
				bestD = d;
				best = folk;
			}
		}
		return best;
	}

	/** A villager with nothing else on: not panicking, not talking to a player, not in a quarrel, a riot or a theft of its own. */
	static boolean freeVillager(Villager v) {
		return v.getTarget() == null && !McaCompat.panicking(v) && !McaCompat.busy(v) && !Neighbours.meeting(v) && !Theft.rioting(v)
			&& !busy(v) && !Looters.busy(v) && !Undertakers.busy(v);
	}

	// ---- carrying them off ----------------------------------------------------------------------------------------------

	/** Walks one duty on; false when it's done (or given up). */
	private static boolean step(ServerLevel level, Duty d, long now) {
		Entity corpse = level.getEntity(d.corpse);
		if (!(level.getEntity(d.bearer) instanceof PathfinderMob bearer) || !bearer.isAlive() || bearer.isSleeping() || corpse == null || corpse.isRemoved()) {
			if (corpse != null && bearer(level, d) instanceof PathfinderMob b && KitchenCompat.carrier(corpse) == b) {
				KitchenCompat.throwAt(corpse, b.position());
			}
			return false;
		}
		boolean holding = KitchenCompat.carrier(corpse) == bearer;
		if (bearer.getTarget() != null || now > d.until || bearer instanceof Villager v && McaCompat.panicking(v) || Undertakers.busy(bearer)) {
			// called away (a fight, a fright), or it's taking too long: put it down where they stand
			if (holding) {
				KitchenCompat.throwAt(corpse, bearer.position());
			}
			if (now > d.until) {
				LEFT.put(d.corpse, now + RETRY);
			}
			return false;
		}
		switch (d.stage) {
			case FETCH -> {
				if (KitchenCompat.carrier(corpse) != null || corpse.isPassenger()) {
					// someone else got to it first
					return false;
				}
				if (bearer.distanceToSqr(corpse) > REACH * REACH) {
					Undertakers.walk(bearer, corpse.blockPosition());
					return true;
				}
				if (d.kind == Kind.BURN && d.to == null) {
					d.stage = Stage.LIGHT;
					return true;
				}
				if (!KitchenCompat.carry(corpse, bearer)) {
					// just let go of, or still tumbling: taken up next time round
					return true;
				}
				d.stage = Stage.HAUL;
			}
			case HAUL -> {
				if (!holding) {
					d.stage = Stage.FETCH;
					return true;
				}
				BlockPos to = d.to;
				if (to == null) {
					d.stage = Stage.LIGHT;
					return true;
				}
				BlockPos stand = d.kind == Kind.BURY ? plot(to) : to;
				double reach = d.kind == Kind.BURY ? GRAVESIDE : d.kind == Kind.PYRE ? Undertakers.THROW : Undertakers.PUT_DOWN;
				if (horizontalDistSqr(bearer, stand) > reach * reach) {
					Undertakers.walk(bearer, stand);
					return true;
				}
				bearer.getNavigation().stop();
				bearer.getLookControl().setLookAt(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
				if (d.kind == Kind.BURY) {
					d.stage = Stage.DIG;
					return true;
				}
				bearer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				KitchenCompat.throwAt(corpse, Vec3.atBottomCenterOf(to));
				if (d.kind == Kind.PYRE) {
					// into the fire: it catches there
					return false;
				}
				d.stage = Stage.LIGHT;
			}
			case DIG -> {
				if (!holding) {
					d.stage = Stage.FETCH;
					return true;
				}
				BlockPos plot = plot(d.to);
				bearer.getNavigation().stop();
				bearer.getLookControl().setLookAt(plot.getX() + 0.5, plot.getY(), plot.getZ() + 0.5);
				d.dug += STEP;
				if (d.dug % DIG_EVERY < STEP) {
					bearer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					BlockState ground = level.getBlockState(plot.below());
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
						plot.getX() + 0.5, plot.getY() + 0.1, plot.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.1);
					level.playSound(null, plot, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.3F);
				}
				if (d.dug >= DIG_TICKS) {
					bury(level, d, bearer, corpse, now);
					return false;
				}
			}
			case LIGHT -> {
				if (bearer.distanceToSqr(corpse) > (REACH + 1.0) * (REACH + 1.0)) {
					Undertakers.walk(bearer, corpse.blockPosition());
					return true;
				}
				if (!corpse.onGround()) {
					return true;
				}
				bearer.getNavigation().stop();
				bearer.getLookControl().setLookAt(corpse);
				bearer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				corpse.igniteForSeconds(BURN_SECONDS);
				level.playSound(null, corpse.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
				return false;
			}
		}
		return true;
	}

	private static @Nullable Entity bearer(ServerLevel level, Duty d) {
		return level.getEntity(d.bearer);
	}

	private static double horizontalDistSqr(Entity e, BlockPos pos) {
		double dx = e.getX() - (pos.getX() + 0.5), dz = e.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	/** Where the body lies under a grave: the mound in front of its headstone. */
	static BlockPos plot(BlockPos head) {
		return head.south();
	}

	/** Lowers the body in, raises the mound and the headstone, writes the grave down, and calls the mourners. */
	private static void bury(ServerLevel level, Duty d, PathfinderMob bearer, Entity corpse, long now) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, d.realm);
		BlockPos head = d.to;
		if (f == null || head == null) {
			KitchenCompat.throwAt(corpse, bearer.position());
			return;
		}
		if (!open(level, head)) {
			// the ground was taken while they dug (a block put there): another grave, or put down and left
			RESERVED.remove(head.asLong());
			BlockPos again = slot(level, f);
			if (again == null) {
				KitchenCompat.throwAt(corpse, bearer.position());
				LEFT.put(d.corpse, now + RETRY);
				return;
			}
			head = again;
		}
		BlockPos plot = plot(head);
		RandomSource random = level.getRandom();
		BlockState ground = level.getBlockState(plot.below());
		if (diggable(ground)) {
			level.setBlock(plot.below(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (!level.getBlockState(plot).isAir() && level.getBlockState(plot).canBeReplaced()) {
			level.setBlock(plot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		BlockState stone = headstone(level, f, random);
		level.setBlock(head, Block.updateFromNeighbourShapes(stone, level, head), Block.UPDATE_ALL);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
			plot.getX() + 0.5, plot.getY() + 0.1, plot.getZ() + 0.5, 20, 0.35, 0.1, 0.35, 0.1);
		level.playSound(null, plot, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.playSound(null, head, SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 0.8F, 0.9F);
		corpse.discard();
		WHO.remove(d.corpse);
		long day = Production.day(server);
		List<Grave> graves = new ArrayList<>(level.getAttachedOrElse(GRAVES, List.of()));
		graves.add(new Grave(f.id(), head.asLong(), d.dead.name(), d.dead.id().map(UUID::toString).orElse(""), day));
		level.setAttached(GRAVES, List.copyOf(graves));
		Speech.say(level, bearer, Component.translatable("message.jaymoskingdoms.bury.rite." + random.nextInt(RITE_LINES), d.dead.name()),
			Speech.WHITE, SAY_RANGE);
		thank(level, f, bearer, d.corpse, now, day);
		callMourners(level, f, d.dead, head, now);
		JaymosKingdoms.LOGGER.debug("{} buried in {}'s graveyard at {}", d.dead.name().getString(), f.name(), head);
	}

	/** A player who carried the body here is thanked (and a little better thought of), a few times a day at most. */
	private static void thank(ServerLevel level, Faction f, PathfinderMob bearer, UUID corpse, long now, long day) {
		Carry c = CARRIED.remove(corpse);
		if (c == null || now - c.at() > HELP_WINDOW) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(c.player());
		if (player == null || player.level() != level || player.distanceToSqr(bearer) > MOURN_REACH * MOURN_REACH) {
			return;
		}
		String key = c.player() + "/" + f.id() + "/" + day;
		int times = HELPED.getOrDefault(key, 0);
		Speech.sayTo(level, bearer, player, Component.translatable("message.jaymoskingdoms.bury.thanks." + level.getRandom().nextInt(THANKS_LINES)),
			Speech.GOLD);
		if (times < HELP_DAILY) {
			HELPED.put(key, times + 1);
			Reputation.change(player, f, REP_HELP);
		}
	}

	// ---- the graveyard ----------------------------------------------------------------------------------------------

	/** The headstone for {@code f}: sandstone in the desert, stone brick in a realm, cobblestone in a village; now and then mossy. */
	private static BlockState headstone(ServerLevel level, Faction f, RandomSource random) {
		Climate climate = Climate.at(level, f.seat());
		boolean mossy = random.nextFloat() < MOSSY;
		Block block;
		if (climate == Climate.DESERT || climate == Climate.BADLANDS) {
			block = Blocks.SANDSTONE_WALL;
		} else if (f.type() == FactionType.VILLAGE) {
			block = mossy ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL;
		} else {
			block = mossy ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL;
		}
		return block.defaultBlockState();
	}

	/** {@code f}'s graves. */
	static List<Grave> graves(ServerLevel level, int realm) {
		List<Grave> out = new ArrayList<>();
		for (Grave g : level.getAttachedOrElse(GRAVES, List.of())) {
			if (g.realm() == realm) {
				out.add(g);
			}
		}
		return out;
	}

	/**
	 * The headstone of {@code f}'s next grave (not yet dug): the free spot nearest its first grave in the rows laid out
	 * from it, or with no graves yet (or no room left there), a new graveyard on open ground near its seat. Null if
	 * there's nowhere.
	 */
	static @Nullable BlockPos slot(ServerLevel level, Faction f) {
		List<Grave> graves = graves(level, f.id());
		List<BlockPos> anchors = new ArrayList<>();
		for (Grave g : graves) {
			// every graveyard begins with a grave nothing else is laid out from
			BlockPos h = g.headPos();
			boolean inRows = false;
			for (BlockPos a : anchors) {
				int dx = h.getX() - a.getX(), dz = h.getZ() - a.getZ();
				if (Math.floorMod(dx, GRAVE_SPACING) == 0 && Math.floorMod(dz, ROW_SPACING) == 0 && Math.abs(dx) <= ROW_HALF * GRAVE_SPACING
					&& Math.abs(dz) <= ROWS * ROW_SPACING) {
					inRows = true;
					break;
				}
			}
			if (!inRows) {
				anchors.add(h);
			}
		}
		for (BlockPos a : anchors) {
			BlockPos best = null;
			double bestD = Double.MAX_VALUE;
			for (int i = -ROW_HALF; i <= ROW_HALF; i++) {
				for (int j = -ROWS; j <= ROWS; j++) {
					int x = a.getX() + i * GRAVE_SPACING, z = a.getZ() + j * ROW_SPACING;
					BlockPos head = surface(level, x, z);
					if (head == null || Math.abs(head.getY() - a.getY()) > 2 || taken(graves, head)) {
						continue;
					}
					double d = head.distSqr(a);
					if (d < bestD && fits(level, head) && ours(level, f, head) && untouched(level, head)) {
						bestD = d;
						best = head;
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		// a new graveyard: the nearest open ground in rings out from the seat
		BlockPos seat = f.seat();
		for (int r = GRAVEYARD_MIN; r <= GRAVEYARD_MAX; r += RING_STEP) {
			for (int k = 0; k < RING_ANGLES; k++) {
				double angle = (k + (r % 2) * 0.5) * Math.PI * 2.0 / RING_ANGLES;
				int x = seat.getX() + (int)Math.round(Math.cos(angle) * r), z = seat.getZ() + (int)Math.round(Math.sin(angle) * r);
				BlockPos head = surface(level, x, z);
				if (head != null && Math.abs(head.getY() - seat.getY()) <= 12 && !taken(graves, head) && fits(level, head) && ours(level, f, head)
					&& roomy(level, head)) {
					return head;
				}
			}
		}
		return null;
	}

	/** The first free block above the ground at {@code x, z} (loaded only), or null. */
	private static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
		BlockPos column = new BlockPos(x, 0, z);
		if (!level.isLoaded(column)) {
			return null;
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static boolean taken(List<Grave> graves, BlockPos head) {
		if (RESERVED.containsKey(head.asLong())) {
			return true;
		}
		for (Grave g : graves) {
			BlockPos h = g.headPos();
			if (h.getX() == head.getX() && h.getZ() == head.getZ()) {
				return true;
			}
		}
		return false;
	}

	/** A grave fits at {@code head}: open sky, earth or sand under the headstone and the mound, both level and clear. */
	private static boolean fits(ServerLevel level, BlockPos head) {
		BlockPos plot = plot(head);
		return open(level, head) && open(level, plot) && level.canSeeSky(head) && diggable(level.getBlockState(head.below()))
			&& diggable(level.getBlockState(plot.below())) && level.getFluidState(head).isEmpty() && level.getFluidState(plot).isEmpty();
	}

	/** Nothing in the way at {@code pos} or above it (grass, a flower or snow is cleared). */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		return (here.isAir() || here.canBeReplaced()) && level.getBlockState(pos.above()).isAir();
	}

	/** Natural ground, earth or sand: not a path, a field, a floor. */
	private static boolean diggable(BlockState ground) {
		return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND);
	}

	/** Room for a graveyard's first rows round {@code head}: natural ground about it, nothing built, nothing farmed. */
	private static boolean roomy(ServerLevel level, BlockPos head) {
		int fit = 0, all = 0;
		for (int dx = -GRAVE_SPACING; dx <= GRAVE_SPACING; dx += GRAVE_SPACING) {
			for (int dz = -ROW_SPACING; dz <= ROW_SPACING; dz += ROW_SPACING) {
				all++;
				BlockPos h = surface(level, head.getX() + dx, head.getZ() + dz);
				if (h != null && Math.abs(h.getY() - head.getY()) <= 1 && fits(level, h)) {
					fit++;
				}
			}
		}
		return fit * 3 >= all * 2 && untouched(level, head) && !nearGraves(level, head);
	}

	/** The new graveyard's first grave isn't hard by another graveyard's (whose own walls would read as building). */
	private static boolean nearGraves(ServerLevel level, BlockPos head) {
		for (Grave g : level.getAttachedOrElse(GRAVES, List.of())) {
			if (g.headPos().distSqr(head) < 4 * 4) {
				return true;
			}
		}
		return false;
	}

	/** In {@code f}'s own land (not a neighbour's, not the wilds, not a player's place by it). */
	private static boolean ours(ServerLevel level, Faction f, BlockPos head) {
		Faction law = Theft.lawAt(level, head);
		return law != null && law.id() == f.id();
	}

	/**
	 * Nothing anyone made right beside a grave (a path, a field, a fence, a floor, a door, a bed, a placed block): only
	 * other graves' headstones, which are walls, are let by.
	 */
	private static boolean untouched(ServerLevel level, BlockPos head) {
		List<Grave> graves = level.getAttachedOrElse(GRAVES, List.of());
		for (BlockPos p : BlockPos.betweenClosed(head.offset(-2, -1, -2), head.offset(2, 1, 3))) {
			if (!level.isLoaded(p)) {
				return false;
			}
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.WALLS) && isHeadstone(graves, p)) {
				continue;
			}
			if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCES)
				|| s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.DOORS) || s.is(BlockTags.BEDS) || s.is(Blocks.COBBLESTONE) || s.is(BlockTags.STONE_BRICKS)
				|| s.is(BlockTags.LOGS) || s.is(BlockTags.WOOL) || s.is(Blocks.HAY_BLOCK) || s.is(BlockTags.CROPS)
				|| !s.isAir() && Placed.any(level, p)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isHeadstone(List<Grave> graves, BlockPos p) {
		long at = p.asLong();
		for (Grave g : graves) {
			if (g.head() == at) {
				return true;
			}
		}
		return false;
	}

	/** Who lies under the headstone at {@code pos}, read out to {@code player}. */
	private static void readHeadstone(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (level != Factions.level(level.getServer())) {
			return;
		}
		for (Grave g : level.getAttachedOrElse(GRAVES, List.of())) {
			if (g.head() == pos.asLong()) {
				Faction f = Factions.get(level.getServer(), g.realm());
				player.sendOverlayMessage(f == null
					? Component.translatable("message.jaymoskingdoms.grave.read", g.name(), g.day())
					: Component.translatable("message.jaymoskingdoms.grave.read_of", g.name(), f.name(), g.day()));
				return;
			}
		}
	}

	// ---- mourning ---------------------------------------------------------------------------------------------------

	/** The family (with MCA), else a neighbour or two, else the fallen soldier's comrades, come to the new grave. */
	private static void callMourners(ServerLevel level, Faction f, Dead dead, BlockPos head, long now) {
		RandomSource random = level.getRandom();
		List<Mourner> called = new ArrayList<>();
		AABB box = new AABB(head).inflate(MOURN_REACH, 16.0, MOURN_REACH);
		if (dead.id().isPresent()) {
			UUID id = dead.id().get();
			List<Villager> partners = new ArrayList<>(), kin = new ArrayList<>();
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isSleeping())) {
				if (!freeVillager(v)) {
					continue;
				}
				int k = McaCompat.kinOf(v, id);
				if (k == McaCompat.PARTNER) {
					partners.add(v);
				} else if (k == McaCompat.KIN) {
					kin.add(v);
				}
			}
			kin.sort((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(head)), b.distanceToSqr(Vec3.atCenterOf(head))));
			for (Villager v : partners) {
				if (called.size() < MOST_MOURNERS) {
					called.add(mourner(v, head, dead.name(), Grief.PARTNER, called.size(), now));
				}
			}
			for (Villager v : kin) {
				if (called.size() < MOST_MOURNERS) {
					called.add(mourner(v, head, dead.name(), Grief.KIN, called.size(), now));
				}
			}
		}
		if (called.isEmpty() && dead.soldier()) {
			AABB near = new AABB(head).inflate(FRIEND_REACH, 12.0, FRIEND_REACH);
			for (Folk comrade : level.getEntitiesOfClass(Folk.class, near, x -> x.isAlive() && x.factionId() == f.id() && x.combatant()
				&& !x.leader() && x.getTarget() == null && !busy(x) && !Undertakers.busy(x))) {
				if (called.size() >= MOST_FRIENDS) {
					break;
				}
				called.add(mourner(comrade, head, dead.name(), Grief.COMRADE, called.size(), now));
			}
		}
		if (called.isEmpty()) {
			AABB near = new AABB(head).inflate(FRIEND_REACH, 12.0, FRIEND_REACH);
			for (Villager v : level.getEntitiesOfClass(Villager.class, near, v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
				if (called.size() >= MOST_FRIENDS) {
					break;
				}
				Faction theirs = Theft.lawAt(level, v.blockPosition());
				if (theirs != null && theirs.id() == f.id() && freeVillager(v) && random.nextFloat() < FRIEND_CHANCE) {
					called.add(mourner(v, head, dead.name(), Grief.FRIEND, called.size(), now));
				}
			}
		}
		for (Mourner m : called) {
			MOURNERS.put(m.who, m);
		}
	}

	/** {@code who} is to grieve at the grave at {@code head}, standing at the {@code n}th place round the mound. */
	private static Mourner mourner(PathfinderMob who, BlockPos head, Component name, Grief grief, int n, long now) {
		BlockPos plot = plot(head);
		// either side of the mound, then at its foot
		BlockPos stand = switch (n % 4) {
			case 0 -> plot.east();
			case 1 -> plot.west();
			case 2 -> plot.south();
			default -> plot.south().east();
		};
		return new Mourner(who.getUUID(), head, stand, name, grief, n == 0 && grief != Grief.COMRADE, now + MOURN_WALK);
	}

	/** Walks one mourner on; false when they're done. */
	private static boolean mourn(ServerLevel level, Mourner m, long now) {
		if (!(level.getEntity(m.who) instanceof PathfinderMob who) || !who.isAlive() || who.isSleeping() || who.getTarget() != null
			|| Undertakers.busy(who)
			|| who instanceof Villager v && McaCompat.panicking(v)) {
			return false;
		}
		if (!m.grieving) {
			if (now > m.walkUntil) {
				return false;
			}
			if (horizontalDistSqr(who, m.stand) > 1.6 * 1.6) {
				Undertakers.walk(who, m.stand);
				return true;
			}
			m.grieving = true;
			who.getNavigation().stop();
			if (who instanceof Villager) {
				// down on their knees by the mound
				who.setPose(Pose.CROUCHING);
			}
			Speech.say(level, who, Component.translatable("message.jaymoskingdoms.mourn." + m.grief.name().toLowerCase(java.util.Locale.ROOT) + "."
				+ level.getRandom().nextInt(MOURN_LINES), m.name), Speech.WHITE, SAY_RANGE);
		}
		m.grieved += STEP;
		who.getNavigation().stop();
		who.getLookControl().setLookAt(m.head.getX() + 0.5, m.head.getY() + 0.3, m.head.getZ() + 0.5);
		if (m.grief != Grief.COMRADE) {
			if (m.grieved % TEAR_EVERY < STEP) {
				Vec3 eyes = who.getEyePosition();
				level.sendParticles(ParticleTypes.FALLING_WATER, eyes.x, eyes.y - 0.05, eyes.z, 2, 0.12, 0.02, 0.12, 0.0);
			}
			if (m.grieved % SIGH_EVERY < STEP && who instanceof Villager) {
				level.playSound(null, who.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.5F, 0.6F);
			}
		}
		if (m.grieved < m.ticks()) {
			return true;
		}
		// done: a flower on the mound, and the loss stays with them a while
		if (m.flower) {
			BlockPos plot = plot(m.head);
			BlockState flower = (switch (level.getRandom().nextInt(5)) {
				case 0 -> Blocks.POPPY;
				case 1 -> Blocks.DANDELION;
				case 2 -> Blocks.CORNFLOWER;
				case 3 -> Blocks.AZURE_BLUET;
				default -> Blocks.OXEYE_DAISY;
			}).defaultBlockState();
			if (level.getBlockState(plot).isAir() && flower.canSurvive(level, plot)) {
				who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.setBlock(plot, flower, Block.UPDATE_ALL);
				level.playSound(null, plot, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 0.7F, 1.0F);
			}
		}
		if (who instanceof Villager v) {
			McaCompat.cheer(v, switch (m.grief) {
				case PARTNER -> GRIEF_PARTNER;
				case KIN, VISIT -> GRIEF_KIN;
				default -> GRIEF_FRIEND;
			});
		}
		return false;
	}

	private static void stopGrieving(ServerLevel level, Mourner m) {
		if (level.getEntity(m.who) instanceof PathfinderMob who && who.getPose() == Pose.CROUCHING) {
			who.setPose(Pose.STANDING);
		}
	}

	/** A new day: family come back to the graves of the lately buried, now and then. */
	private static void visits(ServerLevel level, long day, long now) {
		RandomSource random = level.getRandom();
		for (Grave g : level.getAttachedOrElse(GRAVES, List.of())) {
			Optional<UUID> dead = g.deadId();
			BlockPos head = g.headPos();
			if (dead.isEmpty() || day - g.day() > VISIT_DAYS || !level.isLoaded(head) || random.nextFloat() >= VISIT_CHANCE) {
				continue;
			}
			AABB box = new AABB(head).inflate(MOURN_REACH, 16.0, MOURN_REACH);
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isSleeping())) {
				if (freeVillager(v) && McaCompat.kinOf(v, dead.get()) != McaCompat.STRANGERS) {
					MOURNERS.put(v.getUUID(), mourner(v, head, g.name(), Grief.VISIT, 0, now));
					break;
				}
			}
		}
	}
}
