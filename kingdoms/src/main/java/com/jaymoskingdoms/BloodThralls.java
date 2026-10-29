package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.323) Thrall a friend. Each night a vampire drinks from the same mortal player counts ({@link #NIGHTS}, kept on the
 * vampire); after {@link #NIGHTS_TO_BIND} separate nights that player is the vampire's thrall ({@link #MASTER}, kept on
 * the thrall, who is told only by an overlay line).
 *
 * <ul>
 * <li>A thrall hears their master's heartbeat when the master is in their sight, the reverse of the vampire's own
 * ({@link #BEAT}, a lower, slower beat: {@code BloodVision}).</li>
 * <li>The master's bites on their thrall heal twice over and ease the fever more ({@link BloodFever#bit}).</li>
 * <li>The link breaks if the thrall is cured by the priest ({@link Cures}), or turns ({@link VampirePlayers}).</li>
 * </ul>
 * (Not the necromancer's thralls: those are {@link Necromancy}'s.)
 */
public final class BloodThralls {
	/** Separate nights of drinking from the same player before they're bound. */
	static final int NIGHTS_TO_BIND = 3;
	/** How far a thrall hears their master (blocks). */
	static final double HEAR_RANGE = BloodFever.PLAYER_RANGE;

	/** On the vampire: for each mortal player (UUID), the nights (see {@link Vampires#nightIndex}) it drank from them; kept. */
	static final AttachmentType<Map<String, List<Long>>> NIGHTS = AttachmentRegistry.create(JaymosKingdoms.id("blood_thrall_nights"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())).copyOnDeath());
	/** On the thrall: their master's UUID; kept, and synced to the thrall. */
	static final AttachmentType<String> MASTER = AttachmentRegistry.create(JaymosKingdoms.id("blood_master"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
	/** On the thrall: how close their master is while in sight (0-1); synced to the thrall, not kept. */
	static final AttachmentType<Float> BEAT = AttachmentRegistry.create(JaymosKingdoms.id("blood_master_beat"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	private BloodThralls() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodThralls::tick);
	}

	/** How loudly the master beats for this thrall right now (0 none, 1 face to face); on either side. */
	public static float beat(Player thrall) {
		Float f = thrall.getAttached(BEAT);
		return f == null ? 0.0F : f;
	}

	/** Whether {@code target} is {@code vampire}'s thrall. */
	static boolean isThrall(ServerPlayer vampire, LivingEntity target) {
		return target instanceof ServerPlayer p && vampire.getUUID().toString().equals(p.getAttached(MASTER));
	}

	/** They're free of the link (cured, or turned). */
	static void release(ServerPlayer thrall) {
		thrall.removeAttached(MASTER);
		thrall.removeAttached(BEAT);
	}

	/** {@code vampire} drank from {@code victim} on night {@code night}: counts toward binding them. */
	static void drank(ServerPlayer vampire, ServerPlayer victim, long night) {
		if (victim.hasAttached(MASTER) || !Vampires.mortal(victim)) {
			return;
		}
		Map<String, List<Long>> all = new HashMap<>(vampire.getAttachedOrElse(NIGHTS, Map.of()));
		String key = victim.getUUID().toString();
		List<Long> nights = new ArrayList<>(all.getOrDefault(key, List.of()));
		if (nights.contains(night)) {
			return;
		}
		nights.add(night);
		all.put(key, nights);
		vampire.setAttached(NIGHTS, all);
		if (nights.size() >= NIGHTS_TO_BIND) {
			victim.setAttached(MASTER, vampire.getUUID().toString());
			victim.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.thrall.bound").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			vampire.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.thrall.yours", victim.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			victim.level().playSound(null, victim.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
			all.remove(key);
			vampire.setAttached(NIGHTS, all);
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer thrall : server.getPlayerList().getPlayers()) {
			if ((now + thrall.getId()) % 20 != 9) {
				continue;
			}
			String master = thrall.getAttached(MASTER);
			if (master == null) {
				continue;
			}
			if (Vampires.isVampire(thrall)) {
				release(thrall); // turned
				continue;
			}
			ServerPlayer lord = null;
			try {
				lord = server.getPlayerList().getPlayer(java.util.UUID.fromString(master));
			} catch (IllegalArgumentException e) {
				release(thrall);
				continue;
			}
			float beat = 0.0F;
			if (lord != null && thrall.isAlive() && lord.isAlive() && Vampires.isVampire(lord) && !lord.isInvisible() && lord.level() == thrall.level()
				&& !lord.isSpectator() && lord.distanceTo(thrall) <= HEAR_RANGE && BloodFever.inSight(thrall, lord)) {
				beat = (float)(1.0 - lord.distanceTo(thrall) / HEAR_RANGE);
			}
			if (beat <= 0.0F) {
				if (thrall.hasAttached(BEAT)) {
					thrall.removeAttached(BEAT);
				}
			} else if (Math.abs(beat - beat(thrall)) > 0.01F) {
				thrall.setAttached(BEAT, beat);
			}
		}
	}

	/** Their master's UUID, if any (a thrall's). */
	static @Nullable String master(Player thrall) {
		return thrall.getAttached(MASTER);
	}
}
