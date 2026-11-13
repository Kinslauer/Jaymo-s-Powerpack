package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A bed aboard a ship keeps your spawn point, wherever she is (the sailing plan: "A bed aboard keeps your spawn point";
 * Andy, 2026-11-12: "it should spawn you wherever the boat is when it is still there").
 *
 * <p>Each player's berth ({@link Berth}) is kept with the world. A bed slept in (or set as spawn) ashore is noted as an
 * ordinary bed; once that bed sails off with its ship (she casts off with the bed in her) the berth follows the ship
 * herself: her id and the bed's place in her frame, and where she was last seen. Using a bed aboard while she sails sets
 * your berth there too (a word says so), and at night lays you down to sleep in it, carried with her. When she drops anchor
 * your spawn is set to the bed where it now lies (or, if you're away, the next time you join). If she goes down, the berth
 * goes with her.
 *
 * <p>Where you wake: a respawn puts you back in your berth if she's still afloat; one left for dead (Jaymo's Powers'
 * death by cause) wakes in their berth, unless their killers took them captive ({@code Mortality.BERTHS}).
 */
public final class Berths {
	/** How often (ticks) a ship notes where her berths are, for when she's unloaded at sea. */
	static final int TRACK_EVERY = 100;
	/** Monsters this near a berth (blocks across, up and down) keep you from sleeping in it, as ashore. */
	static final double MONSTER_RANGE = 8.0, MONSTER_HEIGHT = 5.0;
	/** How near a berth (blocks) you must be to use it, and how high above the bed's floor you lie in it. */
	static final double REACH = 3.0, LIE = 0.6875;

	/**
	 * One player's berth: the bed's head, in the world ({@code at}: where it lies, or where she was last seen with it); the
	 * ship it's aboard (while she sails) and its place in her frame; whether the bed is a ship's at all; and whether, a ship
	 * having anchored while they were away, their spawn is still to be set to it.
	 */
	public record Berth(GlobalPos at, Optional<UUID> ship, int x, int y, int z, boolean aboard, boolean pending) {
		public static final Codec<Berth> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("at").forGetter(Berth::at),
			UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Berth::ship),
			Codec.INT.optionalFieldOf("x", 0).forGetter(Berth::x),
			Codec.INT.optionalFieldOf("y", 0).forGetter(Berth::y),
			Codec.INT.optionalFieldOf("z", 0).forGetter(Berth::z),
			Codec.BOOL.optionalFieldOf("aboard", false).forGetter(Berth::aboard),
			Codec.BOOL.optionalFieldOf("pending", false).forGetter(Berth::pending)).apply(i, Berth::new));

		boolean atSea() {
			return this.ship.isPresent();
		}
	}

	/** Every player's berth (by their id), kept on the Overworld. */
	static final AttachmentType<Map<String, Berth>> BERTHS = AttachmentRegistry.create(JaymosKingdoms.id("ship_berths"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Berth.CODEC)));

	/** Those asleep in a berth aboard a ship under way: player id to {ship id, the bed's head in her frame}. */
	private static final Map<UUID, Object[]> SLEEPERS = new HashMap<>();
	/** Set while the game copies a respawn point across a respawn (not a new one being chosen). */
	private static boolean copying;
	/** Set while we set a player's spawn ourselves (a ship anchored with their berth in her). */
	private static boolean ours;

	private Berths() {
	}

	static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				respawned(newPlayer);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!SLEEPERS.isEmpty() && server.getTickCount() % 20 == 0) {
				SLEEPERS.keySet().removeIf(id -> {
					ServerPlayer p = server.getPlayerList().getPlayer(id);
					return p == null || !p.isSleeping();
				});
			}
		});
		shelter();
	}

	// ---- the store ----

	private static Map<String, Berth> all(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(BERTHS, Map.of());
	}

	private static void put(MinecraftServer server, UUID player, @Nullable Berth berth) {
		Map<String, Berth> map = new HashMap<>(all(server));
		if (berth == null) {
			if (map.remove(player.toString()) == null) {
				return;
			}
		} else {
			map.put(player.toString(), berth);
		}
		server.overworld().setAttached(BERTHS, Map.copyOf(map));
	}

	public static @Nullable Berth of(MinecraftServer server, UUID player) {
		return all(server).get(player.toString());
	}

	// ---- setting it ----

	/** (mixin) The game is copying a respawn point over to a respawned player: that isn't a new choice of bed. */
	public static void copying(boolean on) {
		copying = on;
	}

	/**
	 * (mixin) {@code player}'s respawn point was just set to {@code config} (a bed slept in, a story's forced spawn, or
	 * cleared): their berth follows it. A bed ashore is noted (it may sail off one day); anything else ends a berth at sea.
	 */
	public static void respawnSet(ServerPlayer player, ServerPlayer.@Nullable RespawnConfig config) {
		if (copying || ours || !(player.level() instanceof ServerLevel)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (config == null) {
			put(server, player.getUUID(), null);
			return;
		}
		GlobalPos at = config.respawnData().globalPos();
		ServerLevel level = server.getLevel(at.dimension());
		Berth now = of(server, player.getUUID());
		if (now != null && !now.atSea() && now.at().equals(at)) {
			// the same bed again (a ship's, still where she anchored)
			if (now.pending()) {
				put(server, player.getUUID(), new Berth(now.at(), Optional.empty(), 0, 0, 0, now.aboard(), false));
			}
			return;
		}
		boolean bed = level != null && level.isLoaded(at.pos()) && level.getBlockState(at.pos()).getBlock() instanceof AbstractBedBlock;
		put(server, player.getUUID(), bed ? new Berth(at, Optional.empty(), 0, 0, 0, false, false) : null);
	}

	/**
	 * {@code player} uses the bed at (x, y, z) in her frame, aboard {@code ship} under sail: it becomes their berth (where
	 * they'll wake), and at night they lie down in it. True if anything came of it (else they're told why not).
	 */
	static boolean use(ServerPlayer player, Ship ship, int x, int y, int z) {
		Hull h = ship.hull();
		if (h == null || !(ship.level() instanceof ServerLevel level)) {
			return false;
		}
		int[] head = head(h, x, y, z);
		if (head == null) {
			return false;
		}
		BlockState state = h.pieces.get(h.at(head[0], head[1], head[2])).blockState();
		AbstractBedBlock bed = (AbstractBedBlock)state.getBlock();
		Vec3 world = ship.toWorld(head[0], head[1], head[2]);
		BlockPos pos = BlockPos.containing(world.x, world.y + 0.5, world.z);
		BedRule rule = bed.getBedRule(level, pos);
		if (rule.destroyOnUse()) {
			// (a bed's no good where beds burst: the Nether, the End)
			rule.errorMessage().ifPresent(player::sendOverlayMessage);
			return true;
		}
		if (player.position().distanceTo(world.add(0.0, 0.5, 0.0)) > REACH + 1.0) {
			player.sendOverlayMessage(Component.translatable("block.minecraft.bed.too_far_away"));
			return true;
		}
		if (rule.canSetSpawn(level)) {
			Berth was = of(level.getServer(), player.getUUID());
			Berth berth = new Berth(GlobalPos.of(level.dimension(), pos), Optional.of(ship.getUUID()), head[0], head[1], head[2], true, false);
			put(level.getServer(), player.getUUID(), berth);
			if (was == null || !was.equals(berth)) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.berth_set"));
			}
		}
		if (!rule.canSleep(level)) {
			rule.errorMessage().ifPresent(player::sendOverlayMessage);
			return true;
		}
		if (!player.isCreative()) {
			AABB near = new AABB(world.x - MONSTER_RANGE, world.y - MONSTER_HEIGHT, world.z - MONSTER_RANGE, world.x + MONSTER_RANGE,
				world.y + MONSTER_HEIGHT, world.z + MONSTER_RANGE);
			if (!level.getEntitiesOfClass(Monster.class, near, m -> m.isPreventingPlayerRest(level, player)).isEmpty()) {
				player.sendOverlayMessage(Component.translatable("block.minecraft.bed.not_safe"));
				return true;
			}
		}
		if (player.isSleeping() || player.isPassenger()) {
			return true;
		}
		// down in her bed: lying as the bed lies in her, carried with her (see #carry)
		Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
		float yaw = ship.getYRot() + facing.toYRot() + 180.0F;
		player.teleportTo(level, world.x, world.y + LIE, world.z, Set.of(), yaw, 0.0F, false);
		player.setPose(Pose.SLEEPING);
		player.setSleepingPos(BlockPos.containing(world.x, world.y + LIE, world.z));
		player.setDeltaMovement(Vec3.ZERO);
		((com.jaymoskingdoms.mixin.sleep.PlayerSleepAccessor)player).jaymoskingdoms$setSleepCounter(0);
		player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
		player.awardStat(Stats.SLEEP_IN_BED);
		SLEEPERS.put(player.getUUID(), new Object[] {ship.getUUID(), head});
		if (!level.canSleepThroughNights()) {
			player.sendOverlayMessage(Component.translatable("sleep.not_possible"));
		}
		level.updateSleepingPlayerList();
		return true;
	}

	/** The head of the bed one part of which is at (x, y, z) in her frame, or null if it isn't a whole bed. */
	private static int @Nullable [] head(Hull h, int x, int y, int z) {
		int i = h.at(x, y, z);
		if (i < 0 || !(h.pieces.get(i).blockState().getBlock() instanceof BedBlock)) {
			return null;
		}
		BlockState state = h.pieces.get(i).blockState();
		if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
			return new int[] {x, y, z};
		}
		Direction f = state.getValue(HorizontalDirectionalBlock.FACING);
		int hx = x + f.getStepX(), hz = z + f.getStepZ();
		int j = h.at(hx, y, hz);
		return j >= 0 && h.pieces.get(j).blockState().getBlock() instanceof BedBlock ? new int[] {hx, y, hz} : null;
	}

	/** Whether there's still a bed's head at (x, y, z) in her frame. */
	private static boolean bedAt(Hull h, int x, int y, int z) {
		int i = h.at(x, y, z);
		return i >= 0 && h.pieces.get(i).blockState().getBlock() instanceof BedBlock
			&& h.pieces.get(i).blockState().getValue(BedBlock.PART) == BedPart.HEAD;
	}

	// ---- the ship's side ----

	/**
	 * {@code ship} has just cast off from {@code level}: any berth in a bed of hers (by the world places of her beds' heads,
	 * {@code heads}: world position to her frame) now sails with her.
	 */
	static void castOff(ServerLevel level, Ship ship, Map<Long, int[]> heads) {
		if (heads.isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, Berth> map = new HashMap<>(all(server));
		boolean changed = false;
		for (Map.Entry<String, Berth> e : map.entrySet()) {
			Berth b = e.getValue();
			if (b.atSea() || b.at().dimension() != level.dimension()) {
				continue;
			}
			int[] local = heads.get(b.at().pos().asLong());
			if (local != null) {
				e.setValue(new Berth(b.at(), Optional.of(ship.getUUID()), local[0], local[1], local[2], true, false));
				changed = true;
			}
		}
		if (changed) {
			server.overworld().setAttached(BERTHS, Map.copyOf(map));
		}
	}

	/**
	 * {@code ship} has just dropped anchor, her pieces put down at {@code where} (her frame to the world, by packed local
	 * position): her berths are beds in the world again, and their sleepers' spawns are set to them (now, or when they join).
	 */
	static void anchored(ServerLevel level, Ship ship, Map<Long, BlockPos> where) {
		MinecraftServer server = level.getServer();
		Map<String, Berth> map = new HashMap<>(all(server));
		boolean changed = false;
		for (Map.Entry<String, Berth> e : map.entrySet()) {
			Berth b = e.getValue();
			if (!b.ship().map(ship.getUUID()::equals).orElse(false)) {
				continue;
			}
			changed = true;
			BlockPos at = where.get(BlockPos.asLong(b.x(), b.y(), b.z()));
			if (at == null) {
				e.setValue(null);
				continue;
			}
			GlobalPos bed = GlobalPos.of(level.dimension(), at);
			ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
			if (p != null) {
				setSpawn(p, bed);
			}
			e.setValue(new Berth(bed, Optional.empty(), 0, 0, 0, true, p == null));
		}
		if (changed) {
			map.values().removeIf(java.util.Objects::isNull);
			server.overworld().setAttached(BERTHS, Map.copyOf(map));
		}
	}

	/** {@code ship} is lost (sunk, broken up): the berths in her go down with her. */
	static void lost(ServerLevel level, Ship ship) {
		MinecraftServer server = level.getServer();
		Map<String, Berth> map = new HashMap<>(all(server));
		if (map.values().removeIf(b -> b.ship().map(ship.getUUID()::equals).orElse(false))) {
			server.overworld().setAttached(BERTHS, Map.copyOf(map));
		}
	}

	/**
	 * Each tick of {@code ship} under way: those asleep in her berths are carried with her, those whose bed was smashed out
	 * wake, berths in beds she no longer has are gone, and now and then she notes where her berths are.
	 */
	static void tick(ServerLevel level, Ship ship, Hull h) {
		if (!SLEEPERS.isEmpty()) {
			for (Map.Entry<UUID, Object[]> e : List.copyOf(SLEEPERS.entrySet())) {
				if (!ship.getUUID().equals(e.getValue()[0])) {
					continue;
				}
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
				int[] head = (int[])e.getValue()[1];
				if (p == null || !p.isSleeping() || p.level() != level) {
					SLEEPERS.remove(e.getKey());
					continue;
				}
				if (!bedAt(h, head[0], head[1], head[2])) {
					SLEEPERS.remove(e.getKey());
					p.stopSleepInBed(true, true);
					continue;
				}
				Vec3 at = ship.toWorld(head[0], head[1] + LIE, head[2]);
				p.setPos(at.x, at.y, at.z);
			}
		}
		if (ship.tickCount % TRACK_EVERY != 0) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, Berth> map = all(server);
		Map<String, Berth> next = null;
		for (Map.Entry<String, Berth> e : map.entrySet()) {
			Berth b = e.getValue();
			if (!b.ship().map(ship.getUUID()::equals).orElse(false)) {
				continue;
			}
			if (next == null) {
				next = new HashMap<>(map);
			}
			if (!bedAt(h, b.x(), b.y(), b.z())) {
				next.remove(e.getKey());
				continue;
			}
			Vec3 w = ship.toWorld(b.x(), b.y() + 0.5, b.z());
			next.put(e.getKey(), new Berth(GlobalPos.of(level.dimension(), BlockPos.containing(w)), b.ship(), b.x(), b.y(), b.z(), true, false));
		}
		if (next != null) {
			server.overworld().setAttached(BERTHS, Map.copyOf(next));
		}
	}

	/** (mixin) Whether {@code player}, asleep, lies in a berth aboard a ship that's still there with its bed. */
	public static boolean sleepingAboard(ServerPlayer player) {
		Object[] s = SLEEPERS.get(player.getUUID());
		if (s == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.getEntity((UUID)s[0]) instanceof Ship ship && ship.hull() != null) {
			int[] head = (int[])s[1];
			if (bedAt(ship.hull(), head[0], head[1], head[2])) {
				return true;
			}
		}
		SLEEPERS.remove(player.getUUID());
		return false;
	}

	// ---- waking ----

	/** Where {@code player} wakes in their berth aboard a ship (or a ship's bed she left where she anchored), or null. */
	public static @Nullable GlobalPos where(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Berth b = of(server, player.getUUID());
		if (b == null || !b.aboard()) {
			return null;
		}
		ServerLevel level = server.getLevel(b.at().dimension());
		if (level == null) {
			return null;
		}
		if (b.atSea()) {
			Entity e = level.getEntity(b.ship().get());
			if (e instanceof Ship ship && ship.hull() != null) {
				if (!bedAt(ship.hull(), b.x(), b.y(), b.z())) {
					return null;
				}
				// standing on her bed
				Vec3 on = ship.toWorld(b.x(), b.y() + LIE, b.z());
				return GlobalPos.of(level.dimension(), BlockPos.containing(on));
			}
			// (she's out of sight, unloaded at sea: where she was last seen)
			return GlobalPos.of(level.dimension(), b.at().pos().above());
		}
		BlockPos at = b.at().pos();
		if (level.isLoaded(at) && !(level.getBlockState(at).getBlock() instanceof AbstractBedBlock)) {
			return null;
		}
		return GlobalPos.of(level.dimension(), at.above());
	}

	/** A respawned player whose berth is aboard a ship at sea wakes in it. */
	private static void respawned(ServerPlayer player) {
		Berth b = of(player.level().getServer(), player.getUUID());
		if (b == null || !b.atSea()) {
			return;
		}
		GlobalPos to = where(player);
		ServerLevel level = to == null ? null : player.level().getServer().getLevel(to.dimension());
		if (level != null) {
			player.teleportTo(level, to.pos().getX() + 0.5, to.pos().getY(), to.pos().getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, false);
		}
	}

	/** One whose ship anchored while they were away has their spawn set to their berth as they join. */
	private static void joined(ServerPlayer player) {
		Berth b = of(player.level().getServer(), player.getUUID());
		if (b != null && b.pending() && !b.atSea()) {
			setSpawn(player, b.at());
			put(player.level().getServer(), player.getUUID(), new Berth(b.at(), Optional.empty(), 0, 0, 0, b.aboard(), false));
		}
	}

	private static void setSpawn(ServerPlayer player, GlobalPos bed) {
		ours = true;
		try {
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(bed.dimension(), bed.pos(), player.getYRot(), 0.0F), false), false);
		} finally {
			ours = false;
		}
	}

	/** One left for dead (Jaymo's Powers' death by cause) wakes in their berth aboard, if she's still there. */
	@SuppressWarnings("unchecked")
	private static void shelter() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Object list = Class.forName("com.jaymospowers.core.wounds.Mortality").getField("BERTHS").get(null);
			((List<java.util.function.Function<ServerPlayer, GlobalPos>>)list).add(Berths::where);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Mortality berths (older Powers?)", e);
		}
	}
}
