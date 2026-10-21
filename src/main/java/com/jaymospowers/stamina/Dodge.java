package com.jaymospowers.stamina;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.0) The dodge, replacing Combat Roll: our own after Elenai Dodge 2's ideas (see {@code docs/dodge.md}). Left
 * Alt, or Sneak tapped twice, hops you {@link #SPEED} along the way you're holding (back if you hold nothing), on the
 * ground only, for {@link #COST} of your {@link Feathers}. For the first {@link #EVADE_TICKS} of it a melee blow or a
 * projectile misses you. Hunger: {@link #EXHAUSTION}.
 * <p>Movement is the client's: it hops at once and tells the server ({@link Start}); the server checks the rules
 * ({@link #canDodge}, the cooldown, the feathers), spends, opens the evasion window, plays the whoosh for everyone and
 * tells the powers ({@link #onDodge}: Darktide's evasions, the Corrupted blink).
 */
public final class Dodge {
	// ===== the knobs ================================================================================================
	/** Feathers a dodge costs (half-feathers: 6 is three icons). */
	public static final int COST = 6;
	/** The hop: speed along the ground and up (blocks/tick). */
	public static final double SPEED = 0.85, UP = 0.2;
	/** Ticks between dodges. */
	public static final int COOLDOWN = 10;
	/** Ticks from the start in which a blow or projectile misses. */
	public static final int EVADE_TICKS = 5;
	/** Hunger (exhaustion; 4 is a hunger point) per dodge. */
	public static final float EXHAUSTION = 0.4F;
	/** The server lets a dodge through this many ticks early (client and server clocks differ a little). */
	static final int COOLDOWN_SLACK = 3;
	// ================================================================================================================

	/** Client → server: a dodge started along {@code (x, z)} (a unit vector on the ground). */
	public record Start(float x, float z) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosPowers.id("dodge"));
		public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Start::x, ByteBufCodecs.FLOAT, Start::z, Start::new);

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Told of every dodge the server lets through. */
	@FunctionalInterface
	public interface Listener {
		void dodged(ServerPlayer player, Vec3 direction);
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	/** Server: game time of each player's last dodge. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Dodge() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, ctx) -> start(ctx.player(), payload));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && evades(player, source)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/** Tells {@code listener} of every dodge from now on. */
	public static void onDodge(Listener listener) {
		LISTENERS.add(listener);
	}

	/** Whether {@code player} may dodge now (both sides): on their own feet on the ground, in none of the powers' own moves. */
	public static boolean canDodge(Player player) {
		return player.onGround() && Ledge.canHang(player) && !Ledge.onLedge(player) && !player.isUsingItem();
	}

	private static void start(ServerPlayer player, Start start) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		// the client checked the ground as it hopped; by now the server may already have it in the air
		if (last != null && now - last < COOLDOWN - COOLDOWN_SLACK || !(Ledge.canHang(player) && !Ledge.onLedge(player))) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		player.causeFoodExhaustion(EXHAUSTION);
		Vec3 dir = new Vec3(start.x(), 0.0, start.z());
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()).reverse() : dir.normalize();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.25, 0.02, 0.25, 0.02);
		for (Listener listener : LISTENERS) {
			listener.dodged(player, dir);
		}
	}

	/** Whether {@code player} is in a dodge's first ticks. */
	public static boolean evading(ServerPlayer player) {
		Long last = LAST.get(player.getUUID());
		return last != null && player.level().getGameTime() - last <= EVADE_TICKS;
	}

	/** A melee blow from something living, or a projectile, landing in the evasion window misses. */
	private static boolean evades(ServerPlayer player, DamageSource source) {
		if (!evading(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		if (!(source.getDirectEntity() instanceof Projectile) && !(source.getDirectEntity() instanceof LivingEntity)) {
			return false;
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}
}
