package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.PatronChampions;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The proving grounds (Andy's pick from the Kageborne gap check: "boss trials via altar offerings into instanced arenas";
 * the idea only). An offering on a lit old-age altar, with {@link #EMERALDS} emeralds beside it, no longer calls the
 * patron's champion down into the open world ({@link OldTemples#offer}): the patron's light takes whoever made it, and
 * their party standing within {@link #PARTY_REACH}, up to a ground of its own to be tested there.
 * <ul>
 * <li><b>One ground per fight:</b> a lightless void world, {@link #GROUNDS}, holds them in plots {@link #SPACING} apart;
 * each fight takes the first free plot, raises a round stone floor with a low wall ({@link #build}) and clears it after,
 * so two groups never share one.</li>
 * <li><b>The fight:</b> the champion comes down across from them after {@link #RISE_TICKS}: the same champion as a
 * temple's, scaled to how many came. Won (it falls): what a champion gives and the patron's boon, as at the altar, then
 * {@link #WON_TICKS} to pick up its spoils before the light takes them home. Anything still lying there goes home too.</li>
 * <li><b>Falling</b> (dying or going down) costs only the fight (Andy): they get up a spectator ({@link #fall}), keeping
 * everything, and watch the rest fight on; when it's over they go home with the game mode they had. The last one
 * standing falling loses it; so does {@link #LIMIT}.</li>
 * <li><b>Logging out, the server stopping, a crash:</b> back where they stood, game mode and all, at their next login
 * ({@link #BACK} is saved).</li>
 * </ul>
 */
public final class ProvingGrounds {
	// ---- tuning ----
	/** Emeralds asked beside the patron's offering (Andy: the offering and emeralds, the old age's money). */
	public static final int EMERALDS = 16;
	/** Party members within this many blocks of the altar go in with whoever made the offering. */
	static final double PARTY_REACH = 16.0;
	/** Plots sit this far apart along x; the floor is at {@link #FLOOR}. */
	static final int SPACING = 512, FLOOR = 64;
	/** The floor's radius, and the wall's height. */
	static final int RADIUS = 14, WALL = 3;
	/** How far from the centre the fighters arrive, and the champion comes down, on opposite sides. */
	static final double WAKE = 7.0;
	/** Ticks from arriving to the champion coming down. */
	static final int RISE_TICKS = 60;
	/** The fight is lost if it isn't won in this many ticks (ten minutes). */
	static final int LIMIT = 20 * 60 * 10;
	/** Ticks after the win to gather the spoils before going home. */
	static final int WON_TICKS = 20 * 15;
	/** Health a fallen fighter wakes with at home. */
	static final float FALLEN_HEALTH = 4.0F;
	/** Farther than this from the centre (an ender pearl, a power), they're put back on the floor. */
	static final double STRAY = RADIUS + 4;
	/** How much farther than {@link #STRAY} a fallen spectator may drift before they're put back over the floor. */
	static final double WATCH_ROOM = 12.0;
	/** The champion's beam: from how high, how long. */
	static final double BEAM_HEIGHT = 28.0;
	static final int BEAM_TICKS = 30;

	public static final ResourceKey<Level> GROUNDS = ResourceKey.create(Registries.DIMENSION, JaymosKingdoms.id("proving_grounds"));
	/** On every champion the grounds raise (so one left over after a crash is known and removed). */
	static final String TAG = "jaymoskingdoms.proving_grounds";

	/**
	 * Where a fighter goes home to: saved while they're in, so a logout or a crash still brings them back; and, once they
	 * fell and watch as a spectator, the game mode they had before.
	 */
	public record Back(ResourceKey<Level> dimension, Vec3 pos, float yaw, Optional<GameType> mode) {
		public static final Codec<Back> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Back::dimension),
				Vec3.CODEC.fieldOf("pos").forGetter(Back::pos),
				Codec.FLOAT.fieldOf("yaw").forGetter(Back::yaw),
				GameType.CODEC.optionalFieldOf("mode").forGetter(Back::mode))
			.apply(i, Back::new));
	}

	public static final AttachmentType<Back> BACK = AttachmentRegistry.create(JaymosKingdoms.id("proving_grounds_back"),
		builder -> builder.persistent(Back.CODEC));

	/** A fight under way. */
	private static final class Fight {
		final int plot;
		final ServerLevel arena;
		final BlockPos centre;
		final Patron patron;
		/** Who made the offering's way home: where the spoils left lying go. */
		final Back home;
		final long since;
		/** Those still in it, and their way home. */
		final Map<UUID, Back> fighters = new LinkedHashMap<>();
		/** Those who fell, watching as spectators until it's over. */
		final Set<UUID> watchers = new HashSet<>();
		@Nullable UUID champion;
		long wonAt = -1;

		Fight(int plot, ServerLevel arena, Patron patron, Back home, long since) {
			this.plot = plot;
			this.arena = arena;
			this.centre = new BlockPos(plot * SPACING, FLOOR, 0);
			this.patron = patron;
			this.home = home;
			this.since = since;
		}
	}

	private static final Map<Integer, Fight> FIGHTS = new HashMap<>();
	private static final Map<UUID, Fight> BY_PLAYER = new HashMap<>();

	private ProvingGrounds() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("proving grounds attachment: {}", BACK);
		ServerTickEvents.END_SERVER_TICK.register(ProvingGrounds::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && BY_PLAYER.containsKey(player.getUUID())) {
				player.setHealth(FALLEN_HEALTH);
				Fight f = BY_PLAYER.get(player.getUUID());
				if (!f.watchers.contains(player.getUUID())) {
					fall(player, f);
				}
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			for (Fight f : FIGHTS.values()) {
				if (entity.getUUID().equals(f.champion) && f.wonAt < 0) {
					f.wonAt = f.arena.getGameTime();
					List<ServerPlayer> all = online(f);
					all.addAll(watching(f));
					for (ServerPlayer sp : all) {
						sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.proving_grounds.won", f.patron.named(), WON_TICKS / 20)
							.withStyle(s -> s.withColor(f.patron.colour & 0xFFFFFF)));
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Fight f = BY_PLAYER.remove(handler.getPlayer().getUUID());
			if (f != null) {
				// (BACK stays: they're brought home at their next login; the fight goes on without them)
				f.fighters.remove(handler.getPlayer().getUUID());
				f.watchers.remove(handler.getPlayer().getUUID());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> mend(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Fight f : List.copyOf(FIGHTS.values())) {
				finish(f, false);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// a champion left over from a fight the server fell over in
			if (entity.entityTags().contains(TAG) && !live(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	/** Whether {@code player} is in a fight on the grounds. */
	public static boolean inside(Entity player) {
		return BY_PLAYER.containsKey(player.getUUID());
	}

	// ---- the offering -----------------------------------------------------------------------------------------------

	/**
	 * {@code sp} made a good offering to {@code p} at {@code altar}: they and their party near it go up to the grounds.
	 * False (nothing done, nothing taken) if the grounds can't hold a fight now.
	 */
	static boolean start(ServerLevel level, BlockPos altar, ServerPlayer sp, Patron p) {
		ServerLevel arena = level.getServer().getLevel(GROUNDS);
		EntityType<PatronChampion> type = PatronChampions.typeOf(p);
		if (arena == null || type == null || BY_PLAYER.containsKey(sp.getUUID())) {
			return false;
		}
		int plot = 0;
		while (FIGHTS.containsKey(plot)) {
			plot++;
		}
		Fight f = new Fight(plot, arena, p, back(sp), arena.getGameTime());
		build(arena, f.centre);
		List<ServerPlayer> going = new ArrayList<>();
		for (ServerPlayer m : Parties.members(sp)) {
			if (m == sp || m.level() == level && m.isAlive() && !m.isSpectator() && !Downed.isDowned(m) && !BY_PLAYER.containsKey(m.getUUID())
				&& m.position().distanceTo(Vec3.atCenterOf(altar)) <= PARTY_REACH) {
				going.add(m);
			}
		}
		FIGHTS.put(plot, f);
		int colour = p.colour & 0xFFFFFF;
		for (int i = 0; i < going.size(); i++) {
			ServerPlayer m = going.get(i);
			Back back = back(m);
			f.fighters.put(m.getUUID(), back);
			BY_PLAYER.put(m.getUUID(), f);
			m.setAttached(BACK, back);
			Fx.beam(level, m.position().add(0.0, BEAM_HEIGHT, 0.0), m.position(), 1.0F, colour, BEAM_TICKS);
			m.stopRiding();
			// side by side across the near half of the floor, facing the far side
			double across = (i - (going.size() - 1) / 2.0) * 2.0;
			teleport(m, arena, Vec3.atBottomCenterOf(f.centre.above()).add(-WAKE, 0.0, across), -90.0F);
			m.sendSystemMessage(Component.translatable("message.jaymoskingdoms.proving_grounds.taken", p.named())
				.withStyle(s -> s.withColor(colour)));
		}
		level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
		arena.playSound(null, f.centre.above(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
		return true;
	}

	/** Whether {@code sp} carries the emeralds the grounds ask; {@code take}, they're taken. */
	static boolean pay(ServerPlayer sp, boolean take) {
		if (sp.getAbilities().instabuild) {
			return true;
		}
		if (ContainerHelper.clearOrCountMatchingItems(sp.getInventory(), s -> s.is(Items.EMERALD), EMERALDS, true) < EMERALDS) {
			return false;
		}
		if (take) {
			ContainerHelper.clearOrCountMatchingItems(sp.getInventory(), s -> s.is(Items.EMERALD), EMERALDS, false);
		}
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (FIGHTS.isEmpty()) {
			return;
		}
		for (Fight f : List.copyOf(FIGHTS.values())) {
			long now = f.arena.getGameTime();
			for (UUID id : List.copyOf(f.fighters.keySet())) {
				ServerPlayer sp = server.getPlayerList().getPlayer(id);
				if (sp == null) {
					f.fighters.remove(id);
					BY_PLAYER.remove(id);
					continue;
				}
				if (sp.level() != f.arena) {
					// gone some other way (a power, a command): out of the fight, and nothing to bring them back from
					f.fighters.remove(id);
					BY_PLAYER.remove(id);
					sp.removeAttached(BACK);
					continue;
				}
				if (Downed.isDowned(sp)) {
					fall(sp, f);
					continue;
				}
				Vec3 mid = Vec3.atBottomCenterOf(f.centre.above());
				if (sp.position().distanceTo(mid) > STRAY && !sp.isSpectator() && !sp.isCreative()) {
					teleport(sp, f.arena, mid.add(-WAKE, 0.0, 0.0), -90.0F);
				}
			}
			for (UUID id : List.copyOf(f.watchers)) {
				ServerPlayer sp = server.getPlayerList().getPlayer(id);
				if (sp == null) {
					f.watchers.remove(id);
					BY_PLAYER.remove(id);
					continue;
				}
				if (sp.level() != f.arena) {
					// (a spectator's teleport elsewhere): they've stopped watching, so home now, as they were
					f.watchers.remove(id);
					BY_PLAYER.remove(id);
					home(sp);
					continue;
				}
				Vec3 mid = Vec3.atBottomCenterOf(f.centre.above());
				if (sp.position().distanceTo(mid) > STRAY + WATCH_ROOM) {
					teleport(sp, f.arena, mid.add(-WAKE, WALL + 2.0, 0.0), -90.0F);
				}
			}
			if (f.fighters.isEmpty()) {
				finish(f, f.wonAt >= 0);
				continue;
			}
			if (f.wonAt >= 0) {
				if (now - f.wonAt >= WON_TICKS) {
					finish(f, true);
				}
				continue;
			}
			if (f.champion == null) {
				if (now - f.since >= RISE_TICKS) {
					rise(f);
				}
				continue;
			}
			Entity champion = f.arena.getEntity(f.champion);
			if (champion == null || now - f.since > LIMIT) {
				// gone without falling (or too slow): no free win
				finish(f, false);
			}
		}
	}

	/** Across from them, the champion comes down. */
	private static void rise(Fight f) {
		EntityType<PatronChampion> type = PatronChampions.typeOf(f.patron);
		PatronChampion champion = type == null ? null : type.create(f.arena, EntitySpawnReason.EVENT);
		if (champion == null) {
			finish(f, false);
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(f.centre.above()).add(WAKE, 0.0, 0.0);
		champion.snapTo(at.x, at.y, at.z, 90.0F, 0.0F);
		champion.loose();
		champion.setPersistenceRequired();
		champion.addTag(OldTemples.CALLED);
		champion.addTag(TAG);
		// (known before it's added, or the leftover check on loading takes it for one)
		f.champion = champion.getUUID();
		f.arena.addFreshEntity(champion);
		List<ServerPlayer> in = online(f);
		if (!in.isEmpty()) {
			champion.setTarget(in.get(0));
		}
		int colour = f.patron.colour & 0xFFFFFF;
		Fx.beam(f.arena, at.add(0.0, BEAM_HEIGHT, 0.0), at, 1.4F, colour, BEAM_TICKS);
		Fx.ring(f.arena, at.add(0.0, 0.1, 0.0), 3.5F, colour);
		f.arena.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.6F, 1.3F);
	}

	// ---- the way home -----------------------------------------------------------------------------------------------

	/**
	 * {@code sp} fell in the fight (Andy: they spectate): up again, a spectator who watches the rest fight on, keeping
	 * everything; home, as they were, when it's over.
	 */
	private static void fall(ServerPlayer sp, Fight f) {
		f.fighters.remove(sp.getUUID());
		f.watchers.add(sp.getUUID());
		if (Downed.isDowned(sp)) {
			PowersCompat.getUp(f.arena, sp);
		}
		sp.setHealth(Math.max(sp.getHealth(), FALLEN_HEALTH));
		sp.clearFire();
		Back back = sp.getAttached(BACK);
		if (back != null && back.mode().isEmpty()) {
			sp.setAttached(BACK, new Back(back.dimension(), back.pos(), back.yaw(), Optional.of(sp.gameMode.getGameModeForPlayer())));
		}
		sp.setGameMode(GameType.SPECTATOR);
		sp.sendSystemMessage(Component.translatable(f.fighters.isEmpty() && f.wonAt < 0 ? "message.jaymoskingdoms.proving_grounds.lost"
			: "message.jaymoskingdoms.proving_grounds.fell", f.patron.named()).withStyle(s -> s.withColor(f.patron.colour & 0xFFFFFF)));
	}

	/** The fight is over: everyone still in it goes home, the spoils left lying with them, and the plot is cleared. */
	private static void finish(Fight f, boolean won) {
		FIGHTS.remove(f.plot);
		for (UUID id : List.copyOf(f.watchers)) {
			BY_PLAYER.remove(id);
			ServerPlayer sp = f.arena.getServer().getPlayerList().getPlayer(id);
			if (sp != null) {
				home(sp);
			}
		}
		f.watchers.clear();
		for (UUID id : List.copyOf(f.fighters.keySet())) {
			BY_PLAYER.remove(id);
			ServerPlayer sp = f.arena.getServer().getPlayerList().getPlayer(id);
			if (sp != null) {
				home(sp);
				if (!won) {
					sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.proving_grounds.lost", f.patron.named())
						.withStyle(s -> s.withColor(f.patron.colour & 0xFFFFFF)));
				}
			}
		}
		f.fighters.clear();
		AABB box = box(f.centre);
		ServerLevel homeLevel = f.arena.getServer().getLevel(f.home.dimension());
		for (ItemEntity item : f.arena.getEntitiesOfClass(ItemEntity.class, box)) {
			if (homeLevel != null) {
				// (the copy keeps whose it is)
				item.teleport(new TeleportTransition(homeLevel, f.home.pos(), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
			}
		}
		for (Entity e : f.arena.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
			e.discard();
		}
		clear(f.arena, f.centre);
	}

	/** Back where they stood (or the world's spawn if that world is gone). */
	private static void home(ServerPlayer sp) {
		Back back = sp.getAttached(BACK);
		sp.removeAttached(BACK);
		if (back == null) {
			return;
		}
		back.mode().ifPresent(sp::setGameMode);
		ServerLevel level = sp.level().getServer().getLevel(back.dimension());
		if (level == null) {
			level = sp.level().getServer().overworld();
			teleport(sp, level, Vec3.atBottomCenterOf(level.getRespawnData().pos()), back.yaw());
			return;
		}
		teleport(sp, level, back.pos(), back.yaw());
		level.sendParticles(Fx.wisp(0xFFF2C0, 1.4F), sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
	}

	/** At login: back from a fight they left (or that the server fell over in). */
	private static void mend(ServerPlayer sp) {
		if (sp.getAttached(BACK) != null && !BY_PLAYER.containsKey(sp.getUUID())) {
			home(sp);
		}
	}

	private static boolean live(UUID champion) {
		for (Fight f : FIGHTS.values()) {
			if (champion.equals(f.champion)) {
				return true;
			}
		}
		return false;
	}

	private static List<ServerPlayer> online(Fight f) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : f.fighters.keySet()) {
			ServerPlayer sp = f.arena.getServer().getPlayerList().getPlayer(id);
			if (sp != null) {
				out.add(sp);
			}
		}
		return out;
	}

	private static List<ServerPlayer> watching(Fight f) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : f.watchers) {
			ServerPlayer sp = f.arena.getServer().getPlayerList().getPlayer(id);
			if (sp != null) {
				out.add(sp);
			}
		}
		return out;
	}

	private static Back back(ServerPlayer sp) {
		return new Back(sp.level().dimension(), sp.position(), sp.getYRot(), Optional.empty());
	}

	private static void teleport(ServerPlayer sp, ServerLevel level, Vec3 to, float yaw) {
		sp.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), yaw, 0.0F, false);
		sp.resetFallDistance();
	}

	// ---- the ground -------------------------------------------------------------------------------------------------

	private static AABB box(BlockPos centre) {
		return new AABB(centre).inflate(RADIUS + 6, WALL + 12, RADIUS + 6);
	}

	/** Which block goes at ({@code dx}, {@code dy}, {@code dz}) from the floor's centre, or null for air. */
	private static @Nullable BlockState shape(int dx, int dy, int dz) {
		int r2 = dx * dx + dz * dz;
		if (r2 > RADIUS * RADIUS) {
			return null;
		}
		boolean edge = r2 > (RADIUS - 1) * (RADIUS - 1);
		if (dy == 0) {
			if (r2 >= 49 && r2 <= 64 && Math.floorMod(dx * 3 + dz * 7, 9) == 0) {
				// a ring of lamps set in the floor
				return Blocks.SEA_LANTERN.defaultBlockState();
			}
			return edge || r2 > (RADIUS - 3) * (RADIUS - 3) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
				: Blocks.POLISHED_ANDESITE.defaultBlockState();
		}
		if (dy <= WALL && edge) {
			return dy == WALL ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		}
		return null;
	}

	/** The ground raised round {@code centre}, whatever a fight before left there swept away first. */
	private static void build(ServerLevel level, BlockPos centre) {
		clear(level, centre);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box(centre), e -> !(e instanceof Player))) {
			e.discard();
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= WALL; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockState block = shape(dx, dy, dz);
					if (block != null) {
						level.setBlock(at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz), block, 2);
					}
				}
			}
		}
	}

	/** Everything in the plot's box back to air (the grounds are ours alone: nothing else is ever there). */
	private static void clear(ServerLevel level, BlockPos centre) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = -2; dy <= WALL + 8; dy++) {
			for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
				for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
					at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}
}
