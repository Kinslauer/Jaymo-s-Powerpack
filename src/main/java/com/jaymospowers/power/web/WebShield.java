package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Web shield: with Webbed Fists on ({@link WebStrength#fistsOn}) and an empty hand, a right click spins a round web
 * shield onto your arm. It takes the next {@link #HITS} hits whole, with no knockback (the blow is cancelled before it
 * lands), then tears apart in shreds. It also ends with the webbed fists. Our own damage hook rather than the Guard: it
 * has no timing and costs no feathers, it just holds.
 */
public final class WebShield {
	/** Hits the shield takes before it tears. */
	public static final int HITS = 3;
	/** After one tears, ticks before the next can be spun on. */
	public static final int COOLDOWN = 60;

	/** Hits left (absent: no shield), synced to everyone so the disc is drawn on the arm. */
	public static final AttachmentType<Integer> LEFT = AttachmentRegistry.create(JaymosPowers.id("web_shield"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client to server: a right click with an empty hand, webbed fists on, wanting the shield. */
	public record Spin() implements CustomPacketPayload {
		public static final Type<Spin> TYPE = new Type<>(JaymosPowers.id("web_shield_spin"));
		public static final StreamCodec<ByteBuf, Spin> CODEC = StreamCodec.unit(new Spin());

		@Override
		public Type<Spin> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebShield() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Spin.TYPE, Spin.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spin.TYPE, (payload, ctx) -> spin(ctx.player()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(WebShield::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> READY.clear());
	}

	public static boolean on(Player player) {
		return player.getAttachedOrElse(LEFT, 0) > 0;
	}

	private static void spin(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE) || Powers.silenced(player)
			|| !WebStrength.fistsOn(player) || !player.getMainHandItem().isEmpty() || on(player) || WebCounter.ready(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		player.setAttached(LEFT, HITS);
		Vec3 arm = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, arm.x, arm.y, arm.z, 14, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	/** A blow meant for you (something struck or shot you: not a fall, fire or hunger) is held off whole, with no knockback. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !on(player) || amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (from == null || from == player) {
			return true;
		}
		ServerLevel level = player.level();
		int left = player.getAttachedOrElse(LEFT, 0) - 1;
		Vec3 toward = from.getBoundingBox().getCenter().subtract(player.position());
		Vec3 at = (toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(0.6)).add(player.position()).add(0.0, player.getBbHeight() * 0.6, 0.0);
		if (left > 0) {
			player.setAttached(LEFT, left);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.03);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 1.3F);
		} else {
			end(player, true);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.12);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.04);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.COBWEB_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
		}
		return false;
	}

	/** The shield is gone: torn by its last hit (the next one waits {@link #COOLDOWN}), or let go with the fists. */
	static void end(ServerPlayer player, boolean torn) {
		if (player.hasAttached(LEFT)) {
			player.removeAttached(LEFT);
			if (torn) {
				READY.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
			}
		}
	}

	static void forget(ServerPlayer player) {
		player.removeAttached(LEFT);
		READY.remove(player.getUUID());
	}
}
