package com.jaymospowers.core.jump;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.ember.EmberKindle;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (Powerborne picks, pb-jump): "all of them, arsenal uses a portal atop a mountain so it's peak projects him further
 * up, chrono jumps off a speed remnant wind just off air, so can wind, fire, and lightning sorcerers and any others you
 * think applicable". One more jump in the air for every power, once for each time off the ground (the ground, water,
 * lava, a ladder, a mount or flying give it back), each in that power's own look and feel.
 * <p>
 * The jump itself is the client's, as all movement is ({@code PowerJumpClient}: which kind a player has, how hard it
 * kicks); it tells the server with a {@link Jump} packet, and the server (here) clears the fall (so the fall is counted
 * from the top of the jump, as vanilla's is), takes a little hunger, and shows everyone the effect and sound. The kind is
 * the player's power's ({@link #kindOf}), or their Emberborn blood's fire.
 * <p>
 * Left alone, because they already have their own way up: anyone in flight (Wind, Smoke, Z Fighter ki flight, Xenoform's
 * fliers, Gravity's rock flight), on a web line or a ride keeps what they have. Web's wall jump, cling and charged jump
 * still work as they did; this is a separate spring off the air.
 */
public final class PowerJump {
	/** The kinds. 0 is none. */
	public static final byte NONE = 0, ARSENAL = 1, CHRONO = 2, WIND = 3, FIRE = 4, WEB = 5, GRAVITY = 6, SMOKE = 7, CRYO = 8,
		DARKTIDE = 9, RESONANCE = 10, XENOFORM = 11, ZFIGHTER = 12, MORTAL = 13;

	/** Hunger the jump costs (exhaustion; a point of hunger is four of this). */
	public static final float EXHAUSTION = 0.1F;

	/** Client to server: "I made my second jump, of this kind". */
	public record Jump(byte kind) implements CustomPacketPayload {
		public static final Type<Jump> TYPE = new Type<>(JaymosPowers.id("power_jump"));
		public static final StreamCodec<ByteBuf, Jump> CODEC = ByteBufCodecs.BYTE.map(Jump::new, Jump::kind);

		@Override
		public Type<Jump> type() {
			return TYPE;
		}
	}

	/** Players who have made their second jump since they last stood on something. Server memory only. */
	private static final Set<UUID> SPENT = new HashSet<>();

	private PowerJump() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Jump.TYPE, Jump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Jump.TYPE, (payload, context) -> jump(context.player(), payload.kind()));
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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPENT.remove(handler.getPlayer().getUUID()));
	}

	/** The kind of second jump {@code player} has (either side), or {@link #NONE}: their power's, else Emberborn blood's fire. */
	public static byte kindOf(Player player) {
		Power power = Powers.of(player);
		if (power != null) {
			byte kind = switch (power.id().getPath()) {
				case "arsenal" -> ARSENAL;
				case "chrono" -> CHRONO;
				case "wind" -> WIND;
				case "web" -> WEB;
				case "gravity" -> GRAVITY;
				case "smoke" -> SMOKE;
				case "cryo" -> CRYO;
				case "darktide" -> DARKTIDE;
				case "resonance" -> RESONANCE;
				case "xenoform" -> XENOFORM;
				case "zfighter" -> ZFIGHTER;
				default -> NONE;
			};
			if (kind != NONE) {
				return kind;
			}
		}
		return EmberKindle.ember(player) ? FIRE : NONE;
	}

	private static void jump(ServerPlayer player, byte kind) {
		if (kind <= NONE || kind > MORTAL || player.isSpectator() || player.onGround() || !(player.level() instanceof ServerLevel level)
			|| !SPENT.add(player.getUUID())) {
			return;
		}
		player.resetFallDistance();
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		show(level, player.position(), player.getBbHeight(), kind);
	}

	// ---- the look and sound of each ---------------------------------------------------------------------------------

	private static void show(ServerLevel level, Vec3 feet, double height, byte kind) {
		Vec3 at = feet.add(0.0, 0.05, 0.0);
		Vec3 mid = feet.add(0.0, height * 0.5, 0.0);
		switch (kind) {
			case ARSENAL -> {
				// a small rift opens under the feet and a mountain's peak stands up out of it
				MagicFx.sigil(level, at, 1.1F, 0xB26BFF, 12);
				MagicFx.ring(level, at, 1.3F, 0xB26BFF);
				MagicFx.beam(level, feet.add(0.0, -0.9, 0.0), feet.add(0.0, 0.35, 0.0), 0.34F, 0x8E8E9A, 8);
				MagicFx.blast(level, feet.add(0.0, 0.3, 0.0), 0.35F, 0xF2F2FA);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), feet.x, feet.y, feet.z, 14, 0.35, 0.05, 0.35, 0.1);
				sound(level, feet, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.6F);
				sound(level, feet, SoundEvents.STONE_BREAK, 0.9F, 0.7F);
			}
			case CHRONO -> {
				// he kicks off a speed remnant: his own shape left standing in the air, fading where he was
				MagicFx.orb(level, mid, 0.5F, 0xFFE9A8, 10);
				MagicFx.stroke(level, feet, feet.add(0.0, height, 0.0), 0.55F, 0xFFD36B);
				MagicFx.ring(level, at, 0.9F, 0xFFD36B);
				level.sendParticles(MagicFx.mote(0xFFD36B, 0.9F), mid.x, mid.y, mid.z, 8, 0.25, 0.45, 0.25, 0.02);
				sound(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.7F);
				sound(level, feet, SoundEvents.BREEZE_JUMP, 0.6F, 1.6F);
			}
			case WIND -> {
				// he pushes off the air itself: a gust under the feet
				MagicFx.ring(level, at, 1.3F, 0xCDEFE6);
				MagicFx.blast(level, at, 0.8F, 0xE8FAF5);
				level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y, feet.z, 8, 0.3, 0.05, 0.3, 0.04);
				sound(level, feet, SoundEvents.BREEZE_JUMP, 0.9F, 1.2F);
			}
			case FIRE -> {
				// a burst of fire under the feet
				MagicFx.blast(level, at, 1.1F, 0xFF7A1F);
				MagicFx.ring(level, at, 1.2F, 0xFFB347);
				level.sendParticles(MagicFx.mote(0xFFA040, 1.0F), feet.x, feet.y, feet.z, 10, 0.3, 0.05, 0.3, 0.08);
				sound(level, feet, SoundEvents.FIRECHARGE_USE, 0.8F, 1.2F);
			}
			case WEB -> {
				// a spring off a puff of web
				MagicFx.ring(level, at, 0.9F, 0xEDEDED);
				MagicFx.blast(level, at, 0.6F, 0xFFFFFF);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()), feet.x, feet.y, feet.z, 10, 0.3, 0.05, 0.3, 0.05);
				sound(level, feet, SoundEvents.SLIME_JUMP, 0.7F, 1.5F);
			}
			case GRAVITY -> {
				// he lightens himself for a moment: weight lifted off, a pale column drawn up from the feet
				MagicFx.sigil(level, at, 0.9F, 0x8A6CFF, 10);
				MagicFx.beam(level, feet, feet.add(0.0, height + 0.8, 0.0), 0.16F, 0xB9A6FF, 8);
				level.sendParticles(MagicFx.mote(0x8A6CFF, 0.8F), mid.x, mid.y, mid.z, 8, 0.3, 0.6, 0.3, 0.02);
				sound(level, feet, SoundEvents.BREEZE_JUMP, 0.8F, 0.5F);
			}
			case SMOKE -> {
				// a puff of smoke he steps off
				MagicFx.blast(level, at, 0.9F, 0xA7ADB5);
				level.sendParticles(MagicFx.miasma(0xA7ADB5, 1.4F), feet.x, feet.y, feet.z, 10, 0.3, 0.1, 0.3, 0.02);
				sound(level, feet, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.6F);
			}
			case CRYO -> {
				// a thin plate of ice, stepped off
				MagicFx.pool(level, at, 0.9F, 0x8FD3F5, 12);
				level.sendParticles(ParticleTypes.SNOWFLAKE, feet.x, feet.y, feet.z, 10, 0.35, 0.05, 0.35, 0.03);
				sound(level, feet, SoundEvents.GLASS_BREAK, 0.5F, 1.7F);
			}
			case DARKTIDE -> {
				// a splash of dark water
				MagicFx.pool(level, at, 1.0F, 0x2F6FB5, 10);
				level.sendParticles(ParticleTypes.SPLASH, feet.x, feet.y, feet.z, 14, 0.35, 0.05, 0.35, 0.2);
				level.sendParticles(MagicFx.miasma(0x14304F, 1.2F), feet.x, feet.y, feet.z, 6, 0.3, 0.1, 0.3, 0.02);
				sound(level, feet, SoundEvents.PLAYER_SPLASH, 0.7F, 1.3F);
			}
			case RESONANCE -> {
				// a pulse of sound under the feet
				MagicFx.ring(level, at, 1.1F, 0xB48CF0);
				MagicFx.ring(level, at.add(0.0, 0.2, 0.0), 1.7F, 0xD9C4FA);
				level.sendParticles(MagicFx.rune(0xB48CF0, 0.9F), feet.x, feet.y + 0.2, feet.z, 5, 0.3, 0.1, 0.3, 0.02);
				sound(level, feet, SoundEvents.BELL_BLOCK, 0.7F, 1.6F);
			}
			case XENOFORM -> {
				// no one form of the band is fixed, so a plain push off a pad of its light
				MagicFx.blast(level, at, 0.9F, 0x7CE0A8);
				MagicFx.ring(level, at, 1.0F, 0x7CE0A8);
				sound(level, feet, SoundEvents.SLIME_JUMP, 0.8F, 0.8F);
			}
			case ZFIGHTER -> {
				// a ki burst under the feet
				MagicFx.blast(level, at, 1.4F, 0xFFE680);
				MagicFx.ring(level, at, 1.5F, 0xFFF3B0);
				sound(level, feet, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.4F);
			}
			default -> {
				// no power (a draught's lift): a soft puff
				level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y, feet.z, 6, 0.25, 0.05, 0.25, 0.02);
				sound(level, feet, SoundEvents.WOOL_PLACE, 0.6F, 1.6F);
			}
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
