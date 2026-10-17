package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * (0.7.95) Andy: "warrior borns that get damaged to low health and survive get an attribute point. this effect resets
 * every 5m." Brought to {@link #LOW} of their health or less, a Warrior-born is on the brink; once they're through it
 * (no hit for {@link #SURVIVE_TICKS}, or healed back over {@link #SAFE}), they come back stronger: an attribute point
 * of their own ({@link #POINTS}, counted in {@link Stats#earned}). Dying on the brink gives nothing. Once a point is
 * won, the next can't be for {@link #REST_TICKS}.
 */
public final class WarriorZenkai {
	/** On the brink at or under this share of max health; through it once healed over this share. */
	static final float LOW = 0.25F, SAFE = 0.5F;
	/** Ticks without a hit, still alive, that count as having survived. */
	static final int SURVIVE_TICKS = 200;
	/** Ticks after a point before the next can be won (5 minutes). */
	static final int REST_TICKS = 6000;
	static final int TICK_EVERY = 10;

	/** Attribute points won this way. Saved, kept through death, synced to the owner (the K screen counts them). */
	public static final AttachmentType<Integer> POINTS = AttachmentRegistry.create(JaymosKingdoms.id("warrior_zenkai_points"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** The game time the next point can be won from. Saved, kept through death. */
	static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosKingdoms.id("warrior_zenkai_next"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** On the brink: when they were last hit. */
	private static final Map<UUID, Long> BRINK = new HashMap<>();

	private WarriorZenkai() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !player.isAlive() || !Heritage.is(player, Heritage.Kind.WARRIORBORN)) {
				return;
			}
			long now = player.level().getGameTime();
			if (BRINK.containsKey(player.getUUID())) {
				BRINK.put(player.getUUID(), now);
			} else if (player.getHealth() <= player.getMaxHealth() * LOW && now >= player.getAttachedOrElse(NEXT, 0L)) {
				BRINK.put(player.getUUID(), now);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> BRINK.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(WarriorZenkai::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BRINK.clear());
	}

	/** Attribute points {@code player} has won on the brink. */
	public static int points(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(POINTS, 0);
	}

	private static void tick(MinecraftServer server) {
		if (BRINK.isEmpty() || server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		BRINK.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !Heritage.is(player, Heritage.Kind.WARRIORBORN)) {
				return true;
			}
			long now = player.level().getGameTime();
			if (now - e.getValue() < SURVIVE_TICKS && player.getHealth() < player.getMaxHealth() * SAFE) {
				return false;
			}
			grant(player, now);
			return true;
		});
	}

	private static void grant(ServerPlayer player, long now) {
		player.setAttached(POINTS, points(player) + 1);
		player.setAttached(NEXT, now + REST_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 0.7F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5F, 1.4F);
		player.level().sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warrior_zenkai").withStyle(ChatFormatting.GOLD));
	}
}
