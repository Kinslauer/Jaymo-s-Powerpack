package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.497) Townsfolk lock their homes and shops at night (Andy's plan). Ownership-aware: player-placed blocks are theirs
 * ({@link Placed}). The state comes from the clock every pass, never toggled, so a sleep skip ({@link Nights}) or a
 * restart lands right.
 *
 * <p>From {@link #LOCK_FROM} to {@link #UNLOCK_AT} a realm locks the wooden doors of its houses and barracks and of its
 * shops, and its shops' chests and barrels (never its storehouse). A village's house doors are found from each
 * villager's bed. A building with a player inside waits, so nobody is locked in. A realm's own folk pass its night locks
 * ({@link Locks#mayPass}).
 */
public final class NightLocks {
	/** Ticks between passes. */
	static final int EVERY = 100;
	/** Time of day the locks go on, and the time they come off (Homes.WAKE). */
	static final long LOCK_FROM = 13000L, UNLOCK_AT = 23500L;
	static final Set<String> HOMES = Set.of("house", "barracks");
	static final Set<String> SHOPS = Set.of("market", "stall", "tavern", "smithy", "butcher", "cookhouse", "mason");
	/** Doors this near a villager's bed are their house's (Trespass.HOUSE_RANGE). */
	static final double HOME_DOOR_RANGE = 6.0;
	static final int HOME_DOOR_HEIGHT = 3;
	/** Night locks per realm at most. */
	static final int MOST = 256;
	static final int LEVEL_VILLAGE = 1, LEVEL_FIEFDOM = 2, LEVEL_KINGDOM = 2, SHOP_BONUS = 1;
	/** A night lock's pick seed: its anchor xor this. */
	static final long NIGHT_SALT = 0x6E696768746CL;

	/** Faction id → the positions (packed) it night-locked. On the Overworld level. Saved, not synced. */
	static final AttachmentType<Map<String, List<Long>>> INDEX = AttachmentRegistry.create(JaymosKingdoms.id("night_locks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())));

	/** A block to lock, the box of the house it belongs to (nobody is locked in), and whether it is a shop's. */
	record Candidate(BlockPos anchor, AABB house, boolean shop) {
	}

	/** What a realm has found to lock tonight: the buildings already scanned (by their corner), the villagers' homes, the list. */
	private static final class Night {
		final Set<Long> scanned = new HashSet<>();
		boolean homesScanned;
		final List<Candidate> list = new ArrayList<>();
	}

	/** Faction id → tonight's candidates. Built at the first pass of a night, cleared in the morning or when the server stops. */
	private static final Map<Integer, Night> CANDIDATES = new HashMap<>();

	private NightLocks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightLocks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CANDIDATES.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % EVERY != 0L) {
			return;
		}
		long t = Royals.timeOfDay(level);
		boolean night = t >= LOCK_FROM && t < UNLOCK_AT;
		for (Faction f : Factions.all(server)) {
			if (f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (night && !f.fallen()) {
				lock(server, level, f);
			} else {
				unlock(level, f);
			}
		}
	}

	private static int levelFor(Faction f, boolean shop) {
		int base = switch (f.type()) {
			case VILLAGE -> LEVEL_VILLAGE;
			case FIEFDOM -> LEVEL_FIEFDOM;
			default -> LEVEL_KINGDOM;
		};
		return Mth.clamp(base + (shop ? SHOP_BONUS : 0), 1, Lockpicking.LEVELS);
	}

	private static void lock(MinecraftServer server, ServerLevel level, Faction f) {
		Night night = CANDIDATES.computeIfAbsent(f.id(), id -> new Night());
		scan(server, level, f, night);
		Map<String, List<Long>> index = level.getAttachedOrElse(INDEX, Map.of());
		List<Long> mine = new ArrayList<>(index.getOrDefault(Integer.toString(f.id()), List.of()));
		boolean changed = false;
		for (Iterator<Candidate> it = night.list.iterator(); it.hasNext();) {
			Candidate c = it.next();
			BlockPos anchor = c.anchor();
			if (!level.isLoaded(anchor)) {
				continue;
			}
			BlockState state = level.getBlockState(anchor);
			if (Placed.any(level, anchor) || !state.is(Locks.LOCKABLE) || Locks.entry(level, anchor) != null) {
				it.remove();
				continue;
			}
			if (mine.size() >= MOST) {
				break;
			}
			if (playerInside(level, c.house())) {
				continue;
			}
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
				if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(anchor).expandTowards(0.0, 1.0, 0.0), LivingEntity::isAlive).isEmpty()) {
					continue;
				}
				Locks.close(level, anchor);
			}
			long seed = anchor.asLong() ^ NIGHT_SALT;
			Locks.put(level, anchor, new Locks.Fitted(0L, (int)Math.floorMod(seed, 2L), levelFor(f, c.shop()), f.id(), true, true));
			mine.add(anchor.asLong());
			changed = true;
			it.remove();
		}
		if (changed) {
			Map<String, List<Long>> next = new HashMap<>(index);
			next.put(Integer.toString(f.id()), List.copyOf(mine));
			level.setAttached(INDEX, next);
		}
	}

	/** Finds what to lock: the doors (and a shop's containers) of its buildings, and the doors by its villagers' beds. */
	private static void scan(MinecraftServer server, ServerLevel level, Faction f, Night night) {
		Set<BlockPos> stores = new HashSet<>(f.holdings().storage());
		for (Works.Built b : Works.of(server, f.id()).built()) {
			boolean home = HOMES.contains(b.entry());
			boolean shop = SHOPS.contains(b.entry());
			if (!(home || shop) || b.ruined() != 0L || night.scanned.contains(b.min().asLong())) {
				continue;
			}
			BlockPos top = b.top();
			if (!level.isLoaded(b.min()) || !level.isLoaded(top)) {
				continue;
			}
			night.scanned.add(b.min().asLong());
			AABB house = new AABB(b.min().getX(), b.min().getY(), b.min().getZ(), top.getX() + 1.0, top.getY() + 1.0, top.getZ() + 1.0);
			for (BlockPos p : BlockPos.betweenClosed(b.min(), top)) {
				if (night.list.size() >= MOST) {
					return;
				}
				BlockState state = level.getBlockState(p);
				if (isLowerDoor(state)) {
					add(night, p, house, shop);
				} else if (shop && (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) && state.is(Locks.LOCKABLE) && !stores.contains(p)) {
					add(night, p, house, true);
				}
			}
		}
		if (night.homesScanned) {
			return;
		}
		night.homesScanned = true;
		AABB area = new AABB(f.seat()).inflate(Villages.VILLAGE_RADIUS);
		for (Villager v : level.getEntitiesOfClass(Villager.class, area)) {
			Faction law = Theft.lawAt(level, v.blockPosition());
			if (law == null || law.id() != f.id()) {
				continue;
			}
			GlobalPos home = McaCompat.home(v).orElse(null);
			if (home == null || !home.dimension().equals(level.dimension())) {
				continue;
			}
			BlockPos bed = home.pos();
			int r = (int)Math.ceil(HOME_DOOR_RANGE);
			AABB house = new AABB(bed).inflate(HOME_DOOR_RANGE, HOME_DOOR_HEIGHT, HOME_DOOR_RANGE);
			for (BlockPos p : BlockPos.betweenClosed(bed.offset(-r, -HOME_DOOR_HEIGHT, -r), bed.offset(r, HOME_DOOR_HEIGHT, r))) {
				if (night.list.size() >= MOST) {
					return;
				}
				double dx = p.getX() - bed.getX(), dz = p.getZ() - bed.getZ();
				if (dx * dx + dz * dz > HOME_DOOR_RANGE * HOME_DOOR_RANGE || !level.isLoaded(p)) {
					continue;
				}
				BlockState state = level.getBlockState(p);
				if (isLowerDoor(state)) {
					add(night, p, house, false);
				}
			}
		}
	}

	private static boolean isLowerDoor(BlockState state) {
		return state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
	}

	private static void add(Night night, BlockPos p, AABB house, boolean shop) {
		BlockPos at = p.immutable();
		for (Candidate c : night.list) {
			if (c.anchor().equals(at)) {
				return;
			}
		}
		night.list.add(new Candidate(at, house, shop));
	}

	/** A player (not a spectator) stands in {@code house}. */
	private static boolean playerInside(ServerLevel level, AABB house) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.getBoundingBox().intersects(house)) {
				return true;
			}
		}
		return false;
	}

	/** Takes the realm's night locks off (the ones in unloaded chunks wait for the next pass). */
	private static void unlock(ServerLevel level, Faction f) {
		CANDIDATES.remove(f.id());
		Map<String, List<Long>> index = level.getAttachedOrElse(INDEX, Map.of());
		List<Long> mine = index.get(Integer.toString(f.id()));
		if (mine == null) {
			return;
		}
		List<Long> left = new ArrayList<>();
		for (long packed : mine) {
			BlockPos pos = BlockPos.of(packed);
			if (!level.isLoaded(pos)) {
				left.add(packed);
				continue;
			}
			Locks.Fitted lock = Locks.raw(level, pos);
			if (lock != null && lock.night() && lock.faction() == f.id()) {
				Locks.remove(level, pos);
			}
		}
		Map<String, List<Long>> next = new HashMap<>(index);
		if (left.isEmpty()) {
			next.remove(Integer.toString(f.id()));
		} else {
			next.put(Integer.toString(f.id()), List.copyOf(left));
		}
		level.setAttached(INDEX, next);
	}
}
