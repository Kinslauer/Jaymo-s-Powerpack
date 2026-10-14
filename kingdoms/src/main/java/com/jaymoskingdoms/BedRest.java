package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.sleep.PlayerSleepAccessor;
import com.mojang.datafixers.util.Either;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.5) Resting in bed without sleeping, our own take on Sleeping Overhaul 2's bed rest: by day (or wherever sleep isn't
 * possible, a blood moon included), or at any time while sneaking, using a bed lays you down awake. Your screen dims a
 * little, you don't count as asleep (so you never hold up or bring on the morning), and while you rest poison and withering
 * can't hurt you. Any other hurt gets you up, as in vanilla. At night a Sleep button above Leave Bed puts you to sleep
 * from there. Like sleeping, resting needs the bed in reach, room above it and no monsters about, and sets your spawn.
 */
public final class BedRest {
	/** Monsters this near the bed (blocks across, up and down) keep you from resting, as from sleeping. */
	static final double MONSTER_RANGE = 8.0, MONSTER_HEIGHT = 5.0;

	private static final Set<UUID> RESTING = new HashSet<>();

	/** Server → player: whether they're resting (not sleeping) in bed. */
	public record State(boolean resting) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(JaymosKingdoms.id("bed_rest"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = ByteBufCodecs.BOOL.map(State::new, State::resting).cast();

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	/** Player → server: go to sleep from resting (the Sleep button). */
	public record Sleep() implements CustomPacketPayload {
		public static final Type<Sleep> TYPE = new Type<>(JaymosKingdoms.id("bed_rest_sleep"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sleep> CODEC = StreamCodec.unit(new Sleep());

		@Override
		public Type<Sleep> type() {
			return TYPE;
		}
	}

	private BedRest() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Sleep.TYPE, Sleep.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Sleep.TYPE, (payload, context) -> sleep(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(BedRest::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && resting(p)
			&& (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER))));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RESTING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RESTING.clear());
	}

	/** Whether this player is lying in bed awake. Server side only (false on the client). */
	public static boolean resting(Player player) {
		return !player.level().isClientSide() && RESTING.contains(player.getUUID());
	}

	/** Whether using a bed should lay this player down to rest rather than try to sleep. */
	public static boolean wantsRest(ServerPlayer player, BedRule rule) {
		return !player.isSleeping() && player.isAlive() && (player.isShiftKeyDown() || !rule.canSleep(player.level()));
	}

	/** Lays {@code player} down awake in the bed at {@code pos} (its head), vanilla's checks first. */
	public static Either<Player.BedSleepingProblem, Unit> start(ServerPlayer player, BlockPos pos, boolean inRange, boolean blocked, boolean canSetSpawn) {
		if (!inRange) {
			return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
		}
		if (blocked) {
			return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
		}
		if (canSetSpawn) {
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(player.level().dimension(), pos, player.getYRot(), player.getXRot()), false), true);
		}
		if (monstersNear(player, pos)) {
			return Either.left(Player.BedSleepingProblem.NOT_SAFE);
		}
		RESTING.add(player.getUUID());
		if (!player.startSleeping(pos)) {
			RESTING.remove(player.getUUID());
			return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
		}
		((PlayerSleepAccessor) player).jaymoskingdoms$setSleepCounter(0);
		player.level().updateSleepingPlayerList();
		ServerPlayNetworking.send(player, new State(true));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bed_rest"));
		return Either.right(Unit.INSTANCE);
	}

	private static boolean monstersNear(ServerPlayer player, BlockPos pos) {
		if (player.isCreative()) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 c = Vec3.atBottomCenterOf(pos);
		return !level.getEntitiesOfClass(Monster.class, new AABB(c.x - MONSTER_RANGE, c.y - MONSTER_HEIGHT, c.z - MONSTER_RANGE,
			c.x + MONSTER_RANGE, c.y + MONSTER_HEIGHT, c.z + MONSTER_RANGE), m -> m.isPreventingPlayerRest(level, player)).isEmpty();
	}

	/** From resting to asleep, if sleep is possible now. */
	private static void sleep(ServerPlayer player) {
		if (!resting(player) || !player.isSleeping()) {
			return;
		}
		BlockPos pos = player.getSleepingPos().orElse(null);
		BlockState state = pos == null ? null : player.level().getBlockState(pos);
		if (state == null || !(state.getBlock() instanceof AbstractBedBlock bed)) {
			return;
		}
		var rule = bed.getBedRule(player.level(), pos);
		if (!rule.canSleep(player.level())) {
			Component message = rule.asProblem().message();
			if (message != null) {
				player.sendOverlayMessage(message);
			}
			return;
		}
		if (monstersNear(player, pos)) {
			player.sendOverlayMessage(Player.BedSleepingProblem.NOT_SAFE.message());
			return;
		}
		RESTING.remove(player.getUUID());
		((PlayerSleepAccessor) player).jaymoskingdoms$setSleepCounter(0);
		player.awardStat(bed.getSleptInBedStatType());
		ServerPlayNetworking.send(player, new State(false));
		player.level().updateSleepingPlayerList();
	}

	/** Anyone resting who's left their bed (or the world) stops resting. */
	private static void tick(MinecraftServer server) {
		for (Iterator<UUID> it = RESTING.iterator(); it.hasNext(); ) {
			ServerPlayer p = server.getPlayerList().getPlayer(it.next());
			if (p == null) {
				it.remove();
			} else if (!p.isSleeping() || !p.isAlive()) {
				it.remove();
				ServerPlayNetworking.send(p, new State(false));
			}
		}
	}

	/** Not counted among the sleepers by {@code SleepStatus}. */
	public static boolean countsAsSleeping(LivingEntity e) {
		return e.isSleeping() && !(e instanceof Player p && resting(p));
	}
}
