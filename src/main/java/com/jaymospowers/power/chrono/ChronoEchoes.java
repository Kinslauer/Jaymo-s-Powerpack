package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where echoes come from and go: one is owed for every level-up and turns up 5-25 minutes later; a shattered one comes
 * back 1-10 minutes later; both count down only while the owner is online and in survival. Echoes appear 16-28 blocks
 * away, out of sight if possible, follow their owner through dimension changes when close, and fade if the owner loses
 * Chrono. A shattered echo of an offline owner waits in the overworld's attachment until they're back.
 */
public final class ChronoEchoes {
	// ---- tuning (ticks / blocks) ----
	/** After each level-up, an echo turns up this much later (5-25 minutes of survival play). */
	static final int ECHO_DELAY_MIN = 5 * 1200;
	static final int ECHO_DELAY_MAX = 25 * 1200;
	/** A shattered echo comes back this much later (1-10 minutes). */
	static final int RETURN_DELAY_MIN = 1200;
	static final int RETURN_DELAY_MAX = 10 * 1200;
	static final int SPAWN_MIN_DISTANCE = 16;
	static final int SPAWN_MAX_DISTANCE = 28;
	static final int SPAWN_ATTEMPTS = 32;
	/** How far up/down from the owner's height a spawn spot is searched for. */
	static final int SPAWN_VERTICAL_RANGE = 10;
	/** No spot found: try again this much later. */
	static final int SPAWN_RETRY = 200;
	/** Spots in front of the owner with a dot product above this (about 70 degrees each side) count as "in view". */
	static final double VIEW_DOT = 0.35;
	/** Echoes within this range of their owner follow them through a dimension change... */
	static final double FOLLOW_RANGE = 24.0;
	/** ...and turn up this far from them on the other side. */
	static final int FOLLOW_MIN_DISTANCE = 5;
	static final int FOLLOW_MAX_DISTANCE = 9;
	/** How often an online owner picks up echoes that shattered while they were away. */
	static final int ORPHAN_CHECK_INTERVAL = 100;

	public static EntityType<ChronoEcho> ECHO;

	/** A shattered echo whose owner was offline, waiting (in the overworld's data) for them to come back. */
	record Orphan(UUID owner, long bond, int ticks) {
		static final Codec<Orphan> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Orphan::owner),
				Codec.LONG.fieldOf("bond").forGetter(Orphan::bond),
				Codec.INT.fieldOf("ticks").forGetter(Orphan::ticks))
			.apply(i, Orphan::new));
	}

	static final AttachmentType<List<Orphan>> ORPHANS = AttachmentRegistry.create(JaymosPowers.id("chrono_orphans"),
		builder -> builder.persistent(Orphan.CODEC.listOf()));

	private ChronoEchoes() {
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("chrono_echo"));
		ECHO = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<ChronoEcho>of(ChronoEcho::new, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(8)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(ECHO, ChronoEcho.createAttributes());
	}

	// ---------------------------------------------------------------- timers

	/** A level-up: an echo is owed. */
	static void owe(ServerPlayer player) {
		ChronoMemory memory = ChronoMemory.of(player);
		memory.echoesDue.add(between(player.getRandom(), ECHO_DELAY_MIN, ECHO_DELAY_MAX));
	}

	/** Every tick for a Chrono player: count the timers down (survival only) and pick up orphans. */
	static void tick(ServerPlayer player, ChronoMemory memory) {
		if (player.tickCount % ORPHAN_CHECK_INTERVAL == 0) {
			adoptOrphans(player, memory);
		}
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			return;
		}
		countDown(player, memory, memory.echoesDue);
		countDown(player, memory, memory.returnsDue);
	}

	private static void countDown(ServerPlayer player, ChronoMemory memory, List<Integer> timers) {
		for (int i = 0; i < timers.size(); i++) {
			int left = timers.get(i) - 1;
			if (left > 0) {
				timers.set(i, left);
			} else if (spawn(player, memory) != null) {
				timers.remove(i--);
			} else {
				timers.set(i, SPAWN_RETRY);
			}
		}
	}

	private static int between(RandomSource random, int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	/** An echo was hurt outside the Nether: it will be back, near its owner, wherever they are by then. */
	static void queueReturn(MinecraftServer server, @Nullable UUID owner, long bond) {
		if (owner == null) {
			return; // a summoned echo with no owner just breaks
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		int ticks = between(RandomSource.create(), RETURN_DELAY_MIN, RETURN_DELAY_MAX);
		if (player != null) {
			ChronoMemory memory = ChronoMemory.peek(player);
			if (Powers.has(player, ChronoPower.INSTANCE) && memory != null && memory.bond == bond) {
				memory.returnsDue.add(ticks);
			}
			return;
		}
		ServerLevel overworld = server.overworld();
		List<Orphan> orphans = new ArrayList<>(overworld.getAttachedOrElse(ORPHANS, List.of()));
		orphans.add(new Orphan(owner, bond, ticks));
		overworld.setAttached(ORPHANS, orphans);
	}

	private static void adoptOrphans(ServerPlayer player, ChronoMemory memory) {
		ServerLevel overworld = player.level().getServer().overworld();
		List<Orphan> orphans = overworld.getAttached(ORPHANS);
		if (orphans == null || orphans.stream().noneMatch(o -> o.owner().equals(player.getUUID()))) {
			return;
		}
		List<Orphan> rest = new ArrayList<>();
		for (Orphan orphan : orphans) {
			if (!orphan.owner().equals(player.getUUID())) {
				rest.add(orphan);
			} else if (orphan.bond() == memory.bond) {
				memory.returnsDue.add(orphan.ticks());
			}
		}
		if (rest.isEmpty()) {
			overworld.removeAttached(ORPHANS);
		} else {
			overworld.setAttached(ORPHANS, rest);
		}
	}

	// ---------------------------------------------------------------- spawning

	/** Spawns one of the player's echoes near them, now. Returns null if there was nowhere to put it. */
	static @Nullable ChronoEcho spawn(ServerPlayer player, ChronoMemory memory) {
		ServerLevel level = player.level();
		BlockPos spot = findSpot(player, SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE, true);
		if (spot == null) {
			return null;
		}
		ChronoEcho echo = ECHO.create(level, EntitySpawnReason.EVENT);
		if (echo == null) {
			return null;
		}
		Vec3 at = Vec3.atBottomCenterOf(spot);
		float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - at.x), player.getZ() - at.z));
		echo.snapTo(at, yaw, 0.0F);
		echo.setYHeadRot(yaw);
		echo.bindTo(player, memory);
		level.addFreshEntity(echo);
		arriveFx(level, at);
		return echo;
	}

	/** A spot the echo fits on, {@code min}-{@code max} blocks from the player; out of their sight if {@code hide} can manage it. */
	static @Nullable BlockPos findSpot(ServerPlayer player, int min, int max, boolean hide) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		BlockPos fallback = null;
		for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos ground = findGround(level, x, player.getBlockY(), z, SPAWN_VERTICAL_RANGE, ECHO);
			if (ground == null) {
				continue;
			}
			if (!hide || !inSight(player, ground)) {
				return ground;
			}
			if (fallback == null) {
				fallback = ground;
			}
		}
		return fallback;
	}

	/** Standing room for {@code type} at column x/z, searched outwards from height {@code y}. Null if there's none (or it isn't loaded). */
	static @Nullable BlockPos findGround(Level level, int x, int y, int z, int range, EntityType<?> type) {
		for (int k = 0; k <= range * 2; k++) {
			int dy = (k % 2 == 0) ? k / 2 : -(k + 1) / 2;
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (!level.isLoaded(pos)) {
				return null;
			}
			BlockPos below = pos.below();
			BlockState floor = level.getBlockState(below);
			BlockState feet = level.getBlockState(pos);
			if (!floor.isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(pos).isEmpty()
				|| !level.getFluidState(pos.above()).isEmpty() || feet.getBlock() instanceof BaseFireBlock) {
				continue;
			}
			if (level.noCollision(type.getDimensions().makeBoundingBox(x + 0.5, y + dy, z + 0.5))) {
				return pos;
			}
		}
		return null;
	}

	/** Roughly: would the player see something standing at {@code pos}? (in front of them, nothing in the way) */
	static boolean inSight(ServerPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
		Vec3 to = target.subtract(eye).normalize();
		if (player.getLookAngle().dot(to) < VIEW_DOT) {
			return false;
		}
		return player.level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType()
			== HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------- following and fading

	/** The player just changed dimension: echoes that were close behind them come along. */
	static void follow(ServerPlayer player, ServerLevel from, Vec3 fromPos) {
		List<ChronoEcho> close = new ArrayList<>(from.getEntities(ECHO,
			e -> e.isAlive() && e.isEchoOf(player) && e.position().distanceToSqr(fromPos) < FOLLOW_RANGE * FOLLOW_RANGE));
		for (ChronoEcho echo : close) {
			BlockPos spot = findSpot(player, FOLLOW_MIN_DISTANCE, FOLLOW_MAX_DISTANCE, false);
			Vec3 at = spot != null ? Vec3.atBottomCenterOf(spot) : player.position();
			fadeFx(from, echo);
			Entity moved = echo.teleport(new TeleportTransition(player.level(), at, Vec3.ZERO, echo.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
			if (moved != null) {
				arriveFx(player.level(), at);
			}
		}
	}

	/** The player lost Chrono: every loaded echo of theirs fades now (unloaded ones fade when next seen by the bond check). */
	static void fadeAll(ServerPlayer player) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			for (ChronoEcho echo : new ArrayList<>(level.getEntities(ECHO, e -> e.isEchoOf(player)))) {
				echo.fade(level);
			}
		}
	}

	/** Killed in the Nether: time collapses on it, for good. */
	static void collapse(ServerLevel level, ChronoEcho echo) {
		double x = echo.getX();
		double y = echo.getY() + 1.0;
		double z = echo.getZ();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 80, 0.5, 0.9, 0.5, 0.3);
		level.sendParticles(ChronoEcho.SHARD, x, y, z, 60, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 10, 0.4, 0.6, 0.4, 0.05);
		// a ring of light rushing inwards: the moment folding shut
		for (int i = 0; i < 32; i++) {
			double a = i / 32.0 * Math.PI * 2.0;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			level.sendParticles(ParticleTypes.END_ROD, x + dx * 3.0, y, z + dz * 3.0, 0, -dx, 0.0, -dz, 0.25);
		}
		level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F);
		UUID owner = echo.owner();
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_echo_collapsed")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
			if (level.dimension() == net.minecraft.world.level.Level.NETHER && com.jaymospowers.core.Powers.has(player, ChronoPower.INSTANCE)
				&& com.jaymospowers.core.Fate.canCorrupt(player)) {
				unravel(level, echo, player);
			}
			if (player.level() != level || player.distanceToSqr(echo) > 32.0 * 32.0) {
				soundFor(player, SoundEvents.BELL_RESONATE, 0.5F, 0.5F);
			}
		}
	}

	/**
	 * Corruption: an echo destroyed for good in the Nether tears a hole in its owner's timeline. The moment rings out
	 * across the dimension, the owner's sky flickers (blindness, then darkness) and their power is corrupted.
	 */
	private static void unravel(ServerLevel level, ChronoEcho echo, ServerPlayer player) {
		double x = echo.getX();
		double y = echo.getY() + 1.0;
		double z = echo.getZ();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 120, 1.2, 1.6, 1.2, 0.2);
		level.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.6, 1.2, 0.6, 0.6);
		level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5F, 0.6F);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 30, 0), player);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 140, 0), player);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.chrono_echo_unravelled").withStyle(ChatFormatting.DARK_PURPLE));
		com.jaymospowers.core.Fate.corrupt(player);
	}

	static void shatterFx(ServerLevel level, Entity echo) {
		double x = echo.getX();
		double y = echo.getY() + 1.0;
		double z = echo.getZ();
		level.sendParticles(ChronoEcho.SHARD, x, y, z, 40, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ChronoEcho.SHARD_GREY, x, y, z, 20, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 16, 0.4, 0.7, 0.4, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.3, 0.6, 0.3, 0.12);
		level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	static void fadeFx(ServerLevel level, Entity echo) {
		double x = echo.getX();
		double y = echo.getY() + 1.0;
		double z = echo.getZ();
		level.sendParticles(ChronoEcho.SHARD_GREY, x, y, z, 24, 0.35, 0.8, 0.35, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 12, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.5F, 1.6F);
	}

	static void arriveFx(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.1);
		level.sendParticles(ChronoEcho.SHARD, at.x, at.y + 1.0, at.z, 16, 0.3, 0.8, 0.3, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.6F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COMPARATOR_CLICK, SoundSource.HOSTILE, 0.5F, 0.7F);
	}

	/** A sound only {@code player} hears, at their position. */
	static void soundFor(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
			player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}
}
