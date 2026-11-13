package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Darkened Chapel ({@code jaymospowers:darkened_chapel}; the building is Tada_no_ahiru's Church of Sin, MIT, see
 * {@code licenses/LICENSE_church_of_sin} and {@code tools/church_of_sin_port.py}): a chapel of the Lantern Warden whose
 * lamp went out, in the birch woods. Andy: "the church can be where the corrupted go to find their master, the demon
 * master can come up from the nether and speak to them there."
 * <ul>
 * <li><b>He comes up.</b> When a holder whose power is corrupted ({@link Fate#isCorrupted}) stands before the altar, the
 * floor there opens as a pool of ember light and, {@link #RISE_TICKS} later, a demon master is standing in it. He is the
 * same master as below ({@link ZFighterDemonRealm#MASTER_TAG}): his talk, his lesson in blood, the Cinder Pact. Nothing is
 * left open behind him: this is no portal.</li>
 * <li><b>He goes down again</b> once no corrupted holder has been within {@link #HOLD_RADIUS} blocks for
 * {@link #LEAVE_TICKS}. For anyone else he never comes: the chapel is only a haunted ruin.</li>
 * <li><b>The pull.</b> Out under a dark sky, a corrupted holder now and then feels which way the nearest chapel lies (a
 * line on the action bar, never a place name or a distance).</li>
 * </ul>
 */
public final class ZFighterChapel {
	// ---- tuning ----
	/** Where he stands, in the chapel template's own blocks: on the nave floor before the altar, facing the nave (+z). */
	static final BlockPos MASTER_SPOT = new BlockPos(18, 9, 22);
	/** A corrupted holder this close to that spot calls him up. */
	static final double CALL_RADIUS = 7.0;
	/** Ticks from the floor opening to him standing there. */
	static final int RISE_TICKS = 60;
	/** He stays while a corrupted holder is within this many blocks, and goes down this long after the last one left. */
	static final double HOLD_RADIUS = 28.0;
	static final int LEAVE_TICKS = 20 * 30;
	/** The pool he comes and goes through: its radius and colour (Nether ember). */
	static final float POOL_RADIUS = 1.8F;
	static final int POOL_COLOR = 0xD8421C;
	/** The pull: how often a corrupted holder may feel it (ticks), its chance then, and how far it reaches (chunks). */
	static final int PULL_EVERY = 20 * 60 * 5;
	static final float PULL_CHANCE = 0.5F;
	static final int PULL_CHUNKS = 64;
	/** Nearer than this (blocks) the pull says nothing: they can see the place. */
	static final double PULL_MIN = 64.0;
	/** A holder who has moved this far (blocks) from where the nearest chapel was last looked for is looked for again. */
	static final double PULL_RECHECK = 384.0;

	static final ResourceKey<Structure> CHAPEL = ResourceKey.create(Registries.STRUCTURE, JaymosPowers.id("darkened_chapel"));
	/** On the master who came up in a chapel (beside {@link ZFighterDemonRealm#MASTER_TAG}). */
	static final String CHAPEL_TAG = "jaymospowers_chapel_master";
	private static final String NAME = "entity.jaymospowers.demon_master_1";

	/** A floor that has opened: where, which way he will face, and the game time he stands. */
	private record Rising(ServerLevel level, Vec3 at, float yaw, long when) {
	}

	/** Where the nearest chapel was last looked for from, and what was found (null: none in reach). */
	private record Sought(BlockPos from, @Nullable BlockPos chapel) {
	}

	private static final Map<BlockPos, Rising> RISING = new HashMap<>();
	/** Chapel masters, by entity, and the game time a corrupted holder was last near each. */
	private static final Map<UUID, Long> WANTED = new HashMap<>();
	private static final Map<UUID, Sought> SOUGHT = new HashMap<>();

	private ZFighterChapel() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterChapel::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RISING.clear();
			WANTED.clear();
			SOUGHT.clear();
		});
	}

	/** {@code master} came up in a chapel (he greets differently there). */
	static boolean inChapel(LivingEntity master) {
		return master.entityTags().contains(CHAPEL_TAG);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		stand(now);
		if (now % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.OVERWORLD) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					continue;
				}
				keep(level, player, now);
				if (Fate.isCorrupted(player)) {
					call(level, player, now);
					pull(level, player, now);
				}
			}
		}
		WANTED.entrySet().removeIf(entry -> now - entry.getValue() > LEAVE_TICKS * 4L);
	}

	// ---------------------------------------------------------------- coming up

	/** The chapel {@code player} is inside, as the spot its master stands on and the way he faces; null outside one. */
	private static @Nullable Rising spot(ServerLevel level, ServerPlayer player, long now) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), (Holder<Structure> holder) -> holder.is(CHAPEL));
		if (start == null || !start.isValid()) {
			return null;
		}
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool) {
				BlockPos at = pool.getPosition().offset(StructureTemplate.transform(MASTER_SPOT, Mirror.NONE, pool.getRotation(), BlockPos.ZERO));
				float yaw = pool.getRotation().rotate(Direction.SOUTH).toYRot();
				return new Rising(level, Vec3.atBottomCenterOf(at), yaw, now + RISE_TICKS);
			}
		}
		return null;
	}

	private static void call(ServerLevel level, ServerPlayer player, long now) {
		Rising rising = spot(level, player, now);
		if (rising == null || player.distanceToSqr(rising.at) > CALL_RADIUS * CALL_RADIUS) {
			return;
		}
		BlockPos key = BlockPos.containing(rising.at);
		if (RISING.containsKey(key) || !masters(level, rising.at, HOLD_RADIUS).isEmpty() || !level.getBlockState(key).getCollisionShape(level, key).isEmpty()) {
			return;
		}
		RISING.put(key, rising);
		MagicFx.pool(level, rising.at.add(0.0, 0.05, 0.0), POOL_RADIUS, POOL_COLOR, RISE_TICKS + 30);
		MagicFx.sigil(level, rising.at.add(0.0, 0.1, 0.0), POOL_RADIUS + 0.6F, POOL_COLOR, RISE_TICKS);
		level.playSound(null, rising.at.x, rising.at.y, rising.at.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, rising.at.x, rising.at.y, rising.at.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	/** Every tick: the floors whose time has come put their master on them. */
	private static void stand(long now) {
		if (RISING.isEmpty()) {
			return;
		}
		Iterator<Rising> it = RISING.values().iterator();
		while (it.hasNext()) {
			Rising rising = it.next();
			if (now < rising.when) {
				continue;
			}
			it.remove();
			ServerLevel level = rising.level;
			if (!level.isLoaded(BlockPos.containing(rising.at)) || !masters(level, rising.at, HOLD_RADIUS).isEmpty()) {
				continue;
			}
			if (ZFighterNpcs.villager(level, rising.at, rising.yaw, ZFighterDemonRealm.MASTER_TAG, NAME, false) == null) {
				ZFighterSites.spawnNpc(level, rising.at, ZFighterDemonRealm.MASTER_TAG, NAME, new MerchantOffers());
			}
			for (LivingEntity master : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(rising.at, 4.0, 4.0, 4.0),
				e -> e.entityTags().contains(ZFighterDemonRealm.MASTER_TAG))) {
				master.addTag(CHAPEL_TAG);
				WANTED.put(master.getUUID(), now);
			}
			MagicFx.ring(level, rising.at.add(0.0, 0.1, 0.0), POOL_RADIUS + 1.0F, POOL_COLOR);
			MagicFx.burst(level, rising.at.x, rising.at.y + 1.0, rising.at.z, POOL_COLOR, 1.0F, 18, 0.4, 0.8, 0.4);
			level.playSound(null, rising.at.x, rising.at.y, rising.at.z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.5F, 0.6F);
		}
	}

	// ---------------------------------------------------------------- going down

	private static List<LivingEntity> masters(ServerLevel level, Vec3 at, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, radius * 2.0, radius * 2.0, radius * 2.0),
			e -> e.isAlive() && e.entityTags().contains(CHAPEL_TAG));
	}

	/** The chapel masters near {@code player}: kept while a corrupted holder is near, sent down once none has been for a while. */
	private static void keep(ServerLevel level, ServerPlayer player, long now) {
		for (LivingEntity master : masters(level, player.position(), HOLD_RADIUS * 2.0)) {
			boolean wanted = false;
			for (ServerPlayer other : level.players()) {
				if (!other.isSpectator() && Fate.isCorrupted(other) && other.distanceToSqr(master) <= HOLD_RADIUS * HOLD_RADIUS) {
					wanted = true;
					break;
				}
			}
			Long last = WANTED.get(master.getUUID());
			if (wanted || last == null) {
				WANTED.put(master.getUUID(), now);
			} else if (now - last >= LEAVE_TICKS) {
				WANTED.remove(master.getUUID());
				Vec3 at = master.position();
				MagicFx.pool(level, at.add(0.0, 0.05, 0.0), POOL_RADIUS, POOL_COLOR, 40);
				MagicFx.burst(level, at.x, at.y + 1.0, at.z, POOL_COLOR, 1.0F, 14, 0.3, 0.8, 0.3);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
				master.discard();
			}
		}
	}

	// ---------------------------------------------------------------- the pull

	private static void pull(ServerLevel level, ServerPlayer player, long now) {
		if ((now + Math.floorMod(player.getUUID().hashCode(), PULL_EVERY)) % PULL_EVERY >= 20 || level.getRandom().nextFloat() >= PULL_CHANCE
			|| !level.isDarkOutside() || !level.canSeeSky(player.blockPosition().above())) {
			return;
		}
		BlockPos here = player.blockPosition();
		Sought sought = SOUGHT.get(player.getUUID());
		if (sought == null || sought.from.distSqr(here) > PULL_RECHECK * PULL_RECHECK) {
			Optional<Holder.Reference<Structure>> chapel = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(CHAPEL);
			if (chapel.isEmpty()) {
				return;
			}
			sought = new Sought(here, level.findNearestMapStructure(HolderSet.direct(chapel.get()), here, PULL_CHUNKS, false));
			SOUGHT.put(player.getUUID(), sought);
		}
		if (sought.chapel == null) {
			return;
		}
		double dx = sought.chapel.getX() - here.getX(), dz = sought.chapel.getZ() - here.getZ();
		if (dx * dx + dz * dz < PULL_MIN * PULL_MIN) {
			return;
		}
		// eight winds, clockwise from north (-z)
		int wind = Math.floorMod((int)Math.round(Math.atan2(dx, -dz) / (Math.PI / 4.0)), 8);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.chapel.pull",
			Component.translatable("message.jaymospowers.chapel.wind_" + wind)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}
}
