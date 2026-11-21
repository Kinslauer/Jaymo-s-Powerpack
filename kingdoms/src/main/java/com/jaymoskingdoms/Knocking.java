package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Knocking: left-click a closed door with an empty hand and you knock (twice, no damage; sneak and it's the vanilla
 * punch again). With MCA, the villager whose home is by the door, within {@link #HEARD_RANGE}, comes to it; at night
 * only if they like you ({@link #LIKE_HEARTS} hearts), and then they wake. Nobody home, or nobody who likes you: a guard
 * within earshot comes instead.
 * <p>
 * Also here: your blows skip the companions that follow you (sneak and they land), as Powers' pets are spared.
 */
public final class Knocking {
	/** How far a knock is heard, in blocks. */
	static final double HEARD_RANGE = 20.0;
	/** A villager's bed counts as the door's home when it is within this many blocks of it. */
	static final double HOME_RANGE = 12.0;
	/** Hearts a villager must have for you to open their door at night. */
	static final int LIKE_HEARTS = 30;
	/** Ticks between two knocks by one player, and between the two raps of a knock. */
	static final int COOLDOWN = 25, RAP_GAP = 6;
	/** A walk to the door is given up after this many ticks, or when they are this close. */
	static final int GIVE_UP = 20 * 20;
	static final double ARRIVED = 2.0;
	/** Ticks between re-aiming the walk (their brain may set another), and its speed. */
	static final int AIM_EVERY = 10;
	static final float WALK = 0.6F;

	private record Answer(UUID villager, BlockPos door, long until) {
	}

	private static final Map<UUID, Long> LAST_KNOCK = new HashMap<>();
	private static final Map<UUID, Answer> ANSWERS = new HashMap<>();
	/** Player, the tick the second rap sounds, and where. */
	private static final Map<UUID, Object[]> RAPS = new HashMap<>();

	private Knocking() {
	}

	static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			BlockState state = level.getBlockState(pos);
			if (player.isSpectator() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !(state.getBlock() instanceof DoorBlock)
				|| state.getValue(DoorBlock.OPEN)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				knock(server, sp, pos);
			}
			return InteractionResult.SUCCESS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> spared(player, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player player && spared(player, entity)));
		ServerTickEvents.END_SERVER_TICK.register(Knocking::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_KNOCK.clear();
			ANSWERS.clear();
			RAPS.clear();
		});
	}

	/** {@code target} is a companion following {@code player}, and they aren't sneaking. */
	private static boolean spared(Player player, Entity target) {
		return !player.level().isClientSide() && !player.isShiftKeyDown() && target instanceof LivingEntity living
			&& player.getUUID().equals(Bonds.lordOf(living));
	}

	private static void knock(ServerLevel level, ServerPlayer player, BlockPos door) {
		long now = level.getGameTime();
		Long last = LAST_KNOCK.get(player.getUUID());
		if (last != null && now - last < COOLDOWN) {
			return;
		}
		LAST_KNOCK.put(player.getUUID(), now);
		rap(level, door);
		RAPS.put(player.getUUID(), new Object[] {level.dimension(), door, now + RAP_GAP});
		if (McaCompat.LOADED) {
			Villager answering = answering(level, player, door);
			if (answering != null) {
				if (answering.isSleeping()) {
					answering.stopSleeping();
				}
				ANSWERS.put(answering.getUUID(), new Answer(player.getUUID(), door, now + GIVE_UP));
				walk(answering, door);
			}
		}
	}

	private static void rap(ServerLevel level, BlockPos door) {
		level.playSound(null, door, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/** Who comes: the resident (if it's day or they like {@code player}), else the nearest guard in earshot; or null. */
	private static @Nullable Villager answering(ServerLevel level, ServerPlayer player, BlockPos door) {
		boolean night = level.isDarkOutside();
		Villager resident = null, guard = null;
		double homeBest = Double.MAX_VALUE, guardBest = Double.MAX_VALUE;
		AABB box = new AABB(door).inflate(HEARD_RANGE);
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && McaCompat.isMca(v)
			&& !ANSWERS.containsKey(v.getUUID()) && v.getVehicle() == null)) {
			if (McaCompat.isGuard(v)) {
				double d = v.distanceToSqr(Vec3.atCenterOf(door));
				if (d < guardBest) {
					guardBest = d;
					guard = v;
				}
				continue;
			}
			GlobalPos home = v.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null || home.dimension() != level.dimension()) {
				continue;
			}
			double d = home.pos().distSqr(door);
			if (d <= HOME_RANGE * HOME_RANGE && d < homeBest && (!night || McaCompat.heartsOf(v, player) >= LIKE_HEARTS)) {
				homeBest = d;
				resident = v;
			}
		}
		return resident != null ? resident : guard;
	}

	private static void walk(Villager v, BlockPos door) {
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(Vec3.atBottomCenterOf(door), WALK, 1));
	}

	private static void tick(MinecraftServer server) {
		if (!RAPS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Object[]>> it = RAPS.entrySet().iterator(); it.hasNext();) {
				Object[] rap = it.next().getValue();
				ServerLevel level = server.getLevel((net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) rap[0]);
				if (level == null) {
					it.remove();
				} else if (level.getGameTime() >= (Long) rap[2]) {
					rap(level, (BlockPos) rap[1]);
					it.remove();
				}
			}
		}
		if (ANSWERS.isEmpty() || server.getTickCount() % AIM_EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Answer>> it = ANSWERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Answer> entry = it.next();
			Answer answer = entry.getValue();
			Villager v = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(entry.getKey()) instanceof Villager found) {
					v = found;
					break;
				}
			}
			if (v == null || !v.isAlive() || v.level().getGameTime() >= answer.until()
				|| v.position().distanceToSqr(Vec3.atCenterOf(answer.door())) <= ARRIVED * ARRIVED) {
				it.remove();
				if (v != null) {
					v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				}
			} else {
				walk(v, answer.door());
			}
		}
	}
}
