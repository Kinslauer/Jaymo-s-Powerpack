package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bringing back the freshly dead (Andy's pick E3). A death that would end the character for good ({@link Mortality#FINAL}) is
 * held for up to {@link #WINDOW} ticks (a minute) when someone who can bring them back is there when it happens: they see the
 * death screen as any death, its Reincarnate button works at once (the death then goes ahead as decided), and the screen says
 * that someone may yet reach them, with the seconds left. Nothing is dropped and no body falls while it is held.
 * <ul>
 * <li>Who can: anything added to {@link #REVIVERS} (Jaymo's Kingdoms adds the clerics of the settlements and any player
 * carrying the scroll of revivify with a diamond's dust). With none near, the death goes ahead as it always did: no wait.
 * If every one of them leaves or falls for {@link #IDLE} ticks, the death goes ahead early.</li>
 * <li>{@link #revive}: up on their feet at {@link #HEALTH} of their health, wounds closed, the death undone, and it is not
 * counted as one of their sure wakes. It costs: weakness and a slow foot for {@link #SICK} ticks, and half their levels.</li>
 * <li>Beyond bringing back: a head taken, the void, lava ({@link #BEYOND}).</li>
 * </ul>
 */
public final class Revival {
	// ---- tuning ----
	/** How long a death may be held (a minute), and how long it waits with no one left who could revive. */
	public static final int WINDOW = 20 * 60;
	static final int IDLE = 20 * 15;
	/** The share of their health they come back with. */
	static final float HEALTH = 0.3F;
	/** How long the sickness lasts (ticks), and the share of their levels they keep. */
	static final int SICK = 20 * 60 * 5;
	static final float LEVELS_KEPT = 0.5F;
	/** Deaths nothing brings back (the keys of {@link Mortality.Cause}). */
	static final Set<String> BEYOND = Set.of("beheaded", "void", "lava");

	/** Whoever can bring {@code dead} back right now (a cleric near, a scroll in reach), asked as they die and each second. */
	public static final List<Predicate<ServerPlayer>> REVIVERS = new CopyOnWriteArrayList<>();

	/** The game time a held death runs out, told to its player alone: the death screen says someone may yet reach them. */
	public static final AttachmentType<Long> WAITING = AttachmentRegistry.create(JaymosPowers.id("revival_waiting"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	static final Identifier PHASE = JaymosPowers.id("revival");

	private static final class Held {
		final ResourceKey<Level> dimension;
		final Vec3 pos;
		final boolean wasInvisible;
		final long since;
		final DamageSource felled;
		int idle;

		Held(ServerPlayer player, long now, DamageSource felled) {
			this.felled = felled;
			this.dimension = player.level().dimension();
			this.pos = player.position();
			this.wasInvisible = player.isInvisible();
			this.since = now;
		}
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();
	private static final java.util.Set<UUID> LETTING_GO = new java.util.HashSet<>();

	/** Server to client: you were brought back (the death screen closes). */
	public record Revived() implements CustomPacketPayload {
		public static final Type<Revived> TYPE = new Type<>(JaymosPowers.id("revival_revived"));
		public static final StreamCodec<ByteBuf, Revived> CODEC = StreamCodec.unit(new Revived());

		@Override
		public Type<Revived> type() {
			return TYPE;
		}
	}

	private Revival() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Revived.TYPE, Revived.CODEC);
		// after the downed state, death by cause and Chrono's window (a Chrono's R gets its four seconds first), before the
		// killing blow is noted
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Mortality.PHASE, PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(JaymosPowers.id("chrono_cheat_death"), PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("wounds_killing_blow"));
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("web_symbiote_life"));
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, Revival::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer) || !HELD.containsKey(entity.getUUID()) || source.is(DamageTypes.GENERIC_KILL));
		ServerTickEvents.END_SERVER_TICK.register(Revival::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (HELD.containsKey(handler.player.getUUID())) {
				letDie(handler.player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				if (HELD.containsKey(player.getUUID())) {
					letDie(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HELD.clear();
			LETTING_GO.clear();
		});
	}

	/** Whether {@code entity} is a death held back for a reviver just now. */
	public static boolean held(LivingEntity entity) {
		return HELD.containsKey(entity.getUUID());
	}

	/** Those dying and waiting for a reviver in {@code level} within {@code range} of {@code at}. */
	public static List<ServerPlayer> dyingNear(ServerLevel level, Vec3 at, double range) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (HELD.containsKey(player.getUUID()) && player.position().distanceToSqr(at) <= range * range) {
				near.add(player);
			}
		}
		return near;
	}

	private static boolean anyoneCan(ServerPlayer dying) {
		for (Predicate<ServerPlayer> reviver : REVIVERS) {
			try {
				if (reviver.test(dying)) {
					return true;
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("A reviver check failed for {}", dying.getScoreboardName(), e);
			}
		}
		return false;
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypes.GENERIC_KILL) || LETTING_GO.contains(entity.getUUID())) {
			return true;
		}
		if (HELD.containsKey(player.getUUID())) {
			player.setHealth(1.0F);
			return false;
		}
		String cause = player.getAttached(Mortality.FINAL);
		if (cause == null || BEYOND.contains(cause) || player.isCreative() || player.isSpectator() || ZFighterAfterlife.among(player)
			|| Mortality.executing(entity) || !anyoneCan(player)) {
			return true;
		}
		player.closeContainer();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.setHealth(1.0F);
		player.clearFire();
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		long now = player.level().getServer().overworld().getGameTime();
		HELD.put(player.getUUID(), new Held(player, now, source));
		player.setInvisible(true);
		player.setAttached(WAITING, now + WINDOW);
		// to them it's a death like any other: health 0 and the death screen with its message
		((com.jaymospowers.power.chrono.mixin.ChronoServerPlayerAccessor) player).jaymospowers$setLastSentHealth(player.getHealth());
		player.connection.send(new ClientboundSetHealthPacket(0.0F, player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
		player.connection.send(new ClientboundPlayerCombatKillPacket(player.getId(), source.getLocalizedDeathMessage(player)));
		JaymosPowers.LOGGER.info("{} died for good ({}), held back {} ticks for a reviver", player.getScoreboardName(), cause, WINDOW);
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (HELD.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (UUID id : List.copyOf(HELD.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			Held held = HELD.get(id);
			if (player == null || held == null) {
				HELD.remove(id);
				continue;
			}
			if (now - held.since >= WINDOW) {
				letDie(player);
				continue;
			}
			if (now % 20 == 0) {
				held.idle = anyoneCan(player) ? 0 : held.idle + 20;
				if (held.idle >= IDLE) {
					letDie(player);
					continue;
				}
			}
			// unhurt, unmoving, breathing, held where they fell
			if (player.getHealth() < 1.0F) {
				player.setHealth(1.0F);
			}
			player.clearFire();
			player.resetFallDistance();
			player.setAirSupply(player.getMaxAirSupply());
			player.setDeltaMovement(Vec3.ZERO);
			if (player.level().dimension() == held.dimension && player.position().distanceToSqr(held.pos) > 0.01) {
				player.teleportTo(player.level(), held.pos.x, held.pos.y, held.pos.z, Set.of(), player.getYRot(), player.getXRot(), false);
			}
		}
	}

	/** Reincarnate pressed inside the wait: the death goes ahead now (and the respawn follows it). */
	public static void respawnPressed(ServerPlayer player) {
		if (HELD.containsKey(player.getUUID())) {
			letDie(player);
		}
	}

	/** Nobody came: the death goes ahead as it was decided (not rolled again; the body falls, things drop). */
	private static void letDie(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		player.removeAttached(WAITING);
		player.setInvisible(held.wasInvisible);
		Mortality.letThrough(player);
		LETTING_GO.add(player.getUUID());
		try {
			Downed.letDie(player.level(), player, held.felled);
		} finally {
			LETTING_GO.remove(player.getUUID());
		}
		JaymosPowers.LOGGER.info("{} was not reached in time", player.getScoreboardName());
	}

	/**
	 * {@code by} (a cleric, a scroll's reader; may be null) brings {@code dead} back. False if they are not waiting. They stand
	 * up where they fell, a death that never was: no wake counted, nothing lost but the cost.
	 */
	public static boolean revive(ServerPlayer dead, @Nullable Entity by) {
		Held held = HELD.remove(dead.getUUID());
		if (held == null) {
			return false;
		}
		ServerLevel level = dead.level();
		dead.removeAttached(WAITING);
		dead.setInvisible(held.wasInvisible);
		Mortality.undoFinal(dead);
		if (Downed.isDowned(dead)) {
			Downed.undo(level, dead);
		}
		Wounds.healAll(dead);
		dead.setHealth(Math.max(1.0F, dead.getMaxHealth() * HEALTH));
		Wounds.reconcile(dead);
		dead.clearFire();
		dead.getFoodData().setFoodLevel(Math.max(dead.getFoodData().getFoodLevel(), 6));
		// the cost: a long sickness, and half the levels
		dead.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SICK, 1));
		dead.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SICK, 0));
		dead.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 12, 0));
		int levels = dead.experienceLevel;
		dead.setExperienceLevels(Math.round(levels * LEVELS_KEPT));
		if (ServerPlayNetworking.canSend(dead, Revived.TYPE)) {
			ServerPlayNetworking.send(dead, new Revived());
		}
		level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
		level.sendParticles(ParticleTypes.END_ROD, dead.getX(), dead.getY() + 1.0, dead.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		JaymosPowers.LOGGER.info("{} was brought back by {}", dead.getScoreboardName(), by == null ? "someone" : by.getName().getString());
		return true;
	}
}
