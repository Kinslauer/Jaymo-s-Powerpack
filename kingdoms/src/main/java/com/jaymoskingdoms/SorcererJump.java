package com.jaymoskingdoms;

import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (pb-jump) Andy: "so can wind, fire, and lightning sorcerers". A Fire Sorcerer's second jump in the air is a burst of
 * fire under the feet; a Lightning Sorcerer's, a crack of lightning; a Wind (air) Sorcerer's, a gust. Jaymo's Powers gives every power its own second jump
 * ({@code PowerJump}); Kingdoms can't compile against it, so the sorcerers have this small one of their own, the same way:
 * the client makes the jump ({@code SorcererJumpClient}: once for each time off the ground) and sends {@link Jump}; the
 * server clears the fall (so it counts from the top of the jump), takes a little hunger and shows the effect to everyone.
 */
public final class SorcererJump {
	/** Hunger the jump costs (exhaustion). */
	static final float EXHAUSTION = 0.1F;

	/** Client to server: "I made my second jump" (the element is read from the class, not sent). */
	public record Jump() implements CustomPacketPayload {
		public static final Jump INSTANCE = new Jump();
		public static final Type<Jump> TYPE = new Type<>(JaymosKingdoms.id("sorcerer_jump"));
		public static final StreamCodec<ByteBuf, Jump> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Jump> type() {
			return TYPE;
		}
	}

	/** Players who have jumped since they last stood on something. Server memory only. */
	private static final Set<UUID> SPENT = new HashSet<>();

	private SorcererJump() {
	}

	/** Whether {@code player} is a Sorcerer of an element with a jump (fire, lightning or air). Either side. */
	public static boolean has(Player player) {
		String element = Absorption.element(player);
		return element.equals("fire") || element.equals("lightning") || element.equals("air");
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Jump.TYPE, Jump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Jump.TYPE, (payload, context) -> jump(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!SPENT.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
						|| player.isPassenger() || player.onClimbable()) {
						SPENT.remove(player.getUUID());
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPENT.remove(handler.player.getUUID()));
	}

	private static void jump(ServerPlayer player) {
		if (!has(player) || player.isSpectator() || player.onGround() || !(player.level() instanceof ServerLevel level) || !SPENT.add(player.getUUID())) {
			return;
		}
		player.resetFallDistance();
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		Vec3 feet = player.position();
		Vec3 at = feet.add(0.0, 0.05, 0.0);
		if (Absorption.element(player).equals("fire")) {
			// a burst of fire under the feet
			Fx.blast(level, at, 1.1F, 0xFF7A1F);
			Fx.ring(level, at, 1.2F, 0xFFB347);
			level.sendParticles(Fx.mote(0xFFA040, 1.0F), feet.x, feet.y, feet.z, 10, 0.3, 0.05, 0.3, 0.08);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
		} else if (Absorption.element(player).equals("air")) {
			// a push off the air itself: a gust under the feet
			Fx.ring(level, at, 1.3F, 0xCDEFE6);
			Fx.blast(level, at, 0.8F, 0xE8FAF5);
			level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y, feet.z, 8, 0.3, 0.05, 0.3, 0.04);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 1.2F);
		} else {
			// a crack of lightning under the feet: a bolt of light struck down to them, sparks thrown off
			Fx.beam(level, feet.add(0.0, 2.5, 0.0), feet, 0.12F, 0xE8F4FF, 4);
			Fx.blast(level, at, 0.9F, 0xBFD8FF);
			level.sendParticles(Fx.spark(0xFFF7B0, 1.0F), feet.x, feet.y, feet.z, 10, 0.3, 0.05, 0.3, 0.1);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
		}
	}
}
