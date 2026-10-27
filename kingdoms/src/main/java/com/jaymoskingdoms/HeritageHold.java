package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.261, Andy's NOEA picks) The heritage key held down ({@code ;}, the same key as a Warrior-born's tail): the Bio-built
 * and the Rubber-folk absorb with it. The client sends {@link Held} every other tick while the key is down; the server
 * counts it held for a few ticks after ({@link #HELD_GRACE}). {@link #advance} runs one absorbing channel: look at a
 * fitting foe up close, keep holding, and when the bar fills the foe is yours to take.
 */
public final class HeritageHold {
	/** The key counts as held this many ticks past the last packet (they come every other tick). */
	static final int HELD_GRACE = 4;
	/** A message on the action bar every this many ticks while absorbing. */
	static final int PROGRESS_EVERY = 5;

	/** Player → server: the heritage key is down. */
	public record Held() implements CustomPacketPayload {
		public static final Type<Held> TYPE = new Type<>(JaymosKingdoms.id("heritage_held"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Held> CODEC = StreamCodec.unit(new Held());

		@Override
		public Type<Held> type() {
			return TYPE;
		}
	}

	/** One absorbing under way: the foe, and how many ticks of the hold it has had. */
	static final class Channel {
		final LivingEntity target;
		int ticks;

		Channel(LivingEntity target) {
			this.target = target;
		}
	}

	/** When each player's key was last seen down (game time). */
	private static final Map<UUID, Long> HELD = new HashMap<>();

	private HeritageHold() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Held.TYPE, Held.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Held.TYPE, (payload, context) -> HELD.put(context.player().getUUID(), context.player().level().getGameTime()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.player.getUUID()));
	}

	/** Whether {@code player} is holding the heritage key now. */
	static boolean held(ServerPlayer player) {
		Long at = HELD.get(player.getUUID());
		long now = player.level().getGameTime();
		return at != null && now - at <= HELD_GRACE && now >= at;
	}

	/**
	 * Something a heritage may take as a foe: a monster, or anything going for {@code player}; never a player, a villager, a
	 * kingdom's folk, or a tamed animal.
	 */
	static boolean foe(ServerPlayer player, LivingEntity e) {
		if (!e.isAlive() || e instanceof Player || e instanceof Villager || e instanceof Folk || e instanceof OwnableEntity owned && owned.getOwner() != null) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob m && m.getTarget() == player;
	}

	/**
	 * One tick of an absorbing for {@code player}: holding the key while looking at a foe {@code fits} within {@code reach}
	 * starts it (and keeps it going while they hold on and the foe stays in sight and in reach), a stream of {@code fx} runs
	 * from the foe to them, and the bar shows on the action bar ({@code bar}). Returns the foe once {@code need} ticks
	 * are held, else null; letting go, looking away or the foe moving off starts it over.
	 */
	static @Nullable LivingEntity advance(Map<UUID, Channel> channels, ServerPlayer player, Predicate<LivingEntity> fits, double reach, int need,
		ParticleOptions fx, String bar) {
		Channel channel = channels.get(player.getUUID());
		if (!player.isAlive() || player.isSpectator() || !held(player)) {
			if (channel != null) {
				channels.remove(player.getUUID());
			}
			return null;
		}
		LivingEntity sighted = Arcana.sighted(player);
		boolean ok = sighted != null && fits.test(sighted) && player.distanceTo(sighted) <= reach;
		if (!ok) {
			if (channel != null) {
				channels.remove(player.getUUID());
			}
			return null;
		}
		if (channel == null || channel.target != sighted) {
			channel = new Channel(sighted);
			channels.put(player.getUUID(), channel);
		}
		channel.ticks++;
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		if (channel.ticks % 2 == 0) {
			pull(level, channel.target, player, fx, channel.ticks / (double)need);
		}
		if (channel.ticks % PROGRESS_EVERY == 0) {
			player.sendOverlayMessage(Component.translatable(bar, Math.min(100, channel.ticks * 100 / need)));
		}
		if (channel.ticks % 10 == 1) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.6F + channel.ticks / (float)need);
		}
		if (channel.ticks >= need) {
			channels.remove(player.getUUID());
			return channel.target;
		}
		return null;
	}

	/** A few motes along the line from {@code from} to {@code to}, thicker toward the end ({@code progress} 0..1). */
	static void pull(ServerLevel level, LivingEntity from, LivingEntity to, ParticleOptions fx, double progress) {
		double fx0 = from.getX(), fy0 = from.getY(0.5), fz0 = from.getZ();
		double tx = to.getX(), ty = to.getY(0.6), tz = to.getZ();
		int n = 3 + (int)(progress * 4.0);
		for (int i = 0; i < n; i++) {
			double t = level.getRandom().nextDouble();
			level.sendParticles(fx, fx0 + (tx - fx0) * t, fy0 + (ty - fy0) * t, fz0 + (tz - fz0) * t, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
