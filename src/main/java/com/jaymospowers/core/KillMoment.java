package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The kill moment: the player's killing blow on a hostile, when it was the last one in the fight, tells that player's
 * client to play a short slow motion with a thin letterbox ({@code KillMomentClient}). Never while another hostile
 * within {@link #RANGE} blocks is targeting the player or is fighting them (they hit it lately, or it hit them), so no
 * one is hit during the moment. The server's own tick is never slowed. The player's client turns it off under Reduced motion.
 */
public final class KillMoment {
	/** Blocks round the player in which another hostile spoils it. */
	public static final double RANGE = 24.0;
	/** A hostile counts as fighting the player if either hit the other within this many ticks. */
	public static final int FIGHTING_TICKS = 100;
	/** Ticks between two moments for one player. */
	public static final int COOLDOWN = 200;

	/** The cue: the one killed (its id), for the client to know it. */
	public record Cue(int victim) implements CustomPacketPayload {
		public static final Type<Cue> TYPE = new Type<>(JaymosPowers.id("kill_moment"));
		public static final StreamCodec<ByteBuf, Cue> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Cue::victim, Cue::new);

		@Override
		public Type<Cue> type() {
			return TYPE;
		}
	}

	private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();

	private KillMoment() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Cue.TYPE, Cue.CODEC);
		ServerLivingEntityEvents.AFTER_DEATH.register(KillMoment::afterDeath);
	}

	private static void afterDeath(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(victim instanceof Enemy) || !(player.level() instanceof ServerLevel level)
			|| player.isSpectator() || !player.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player);
		if (last != null && now - last < COOLDOWN) {
			return;
		}
		for (Mob other : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> m != victim && m.isAlive() && m instanceof Enemy)) {
			if (other.getTarget() == player || fighting(player, other)) {
				return;
			}
		}
		LAST.put(player, now);
		ServerPlayNetworking.send(player, new Cue(victim.getId()));
	}

	/** Whether {@code mob} and {@code player} have hit each other lately. */
	private static boolean fighting(ServerPlayer player, Mob mob) {
		boolean hitByPlayer = mob.getLastHurtByMob() == player && mob.tickCount - mob.getLastHurtByMobTimestamp() < FIGHTING_TICKS;
		boolean hitPlayer = player.getLastHurtByMob() == mob && player.tickCount - player.getLastHurtByMobTimestamp() < FIGHTING_TICKS;
		return hitByPlayer || hitPlayer;
	}
}
