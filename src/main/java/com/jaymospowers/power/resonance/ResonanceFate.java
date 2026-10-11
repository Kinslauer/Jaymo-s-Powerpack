package com.jaymospowers.power.resonance;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * Resonance's fate.
 * <ul>
 * <li><b>Ascend</b> (Choir): kill a Warden while holding a Resonant Fork, once all four abilities are mastered.</li>
 * <li><b>Corrupt</b> (Shrieker): +{@code SHRIEKER_CORRUPTION} for every sculk shrieker you set off, +{@code WARDEN_CORRUPTION}
 * when a Warden emerges because of you (the shrieker mixin reports both). Shrieker is always faintly audible: every
 * {@code AUDIBLE_EVERY} ticks it makes a vibration even when sneaking.</li>
 * <li><b>Cure</b>: listen to a music disc play all the way through on a jukebox within {@code CURE_RADIUS} blocks, from
 * its start (within {@code CURE_START_GRACE}), without attacking anything meanwhile.</li>
 * </ul>
 */
public final class ResonanceFate {
	public static final int SHRIEKER_CORRUPTION = 20;
	public static final int WARDEN_CORRUPTION = 50;
	/** How long a shrieker remembers who set it off (a Warden can take a while to come). */
	static final int SHRIEKER_MEMORY = 20 * 60;
	static final int AUDIBLE_EVERY = 40;
	static final double CURE_RADIUS = 4.0;
	static final int CURE_START_GRACE = 60;
	static final int CURE_CHECK_EVERY = 5;

	private record Shriek(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private record Trigger(UUID player, long at) {
	}

	/** A song being listened to: the jukebox, and how far into it we last heard it. */
	private static final class Listening {
		final BlockPos jukebox;
		final JukeboxSong song;
		long heard;

		Listening(BlockPos jukebox, JukeboxSong song, long heard) {
			this.jukebox = jukebox;
			this.song = song;
			this.heard = heard;
		}
	}

	private static final Map<Shriek, Trigger> SHRIEKERS = new HashMap<>();
	private static final Map<UUID, Listening> LISTENING = new HashMap<>();

	private ResonanceFate() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Warden && source.getEntity() instanceof ServerPlayer player && Powers.has(player, ResonancePower.INSTANCE)
				&& Fate.canAscend(player) && (player.getMainHandItem().is(TuningForkItem.RESONANT_FORK)
					|| player.getOffhandItem().is(TuningForkItem.RESONANT_FORK))) {
				Fate.ascend(player);
			}
		});
		// attacking anything breaks the listening
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer) {
				LISTENING.remove(player.getUUID());
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player) {
				LISTENING.remove(player.getUUID());
			}
		});
	}

	/** From the shrieker mixin: a shrieker at {@code pos} shrieked because of {@code player} (if anyone). */
	public static void shrieked(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
		if (player == null || !Powers.has(player, ResonancePower.INSTANCE)) {
			return;
		}
		SHRIEKERS.put(new Shriek(level.dimension(), pos.immutable()), new Trigger(player.getUUID(), level.getGameTime()));
		Fate.addCorruption(player, SHRIEKER_CORRUPTION);
	}

	/** From the shrieker mixin: a Warden emerged at that shrieker's call. */
	public static void wardenSummoned(ServerLevel level, BlockPos pos) {
		Trigger trigger = SHRIEKERS.remove(new Shriek(level.dimension(), pos));
		if (trigger == null || level.getGameTime() - trigger.at > SHRIEKER_MEMORY) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(trigger.player);
		if (player != null && Powers.has(player, ResonancePower.INSTANCE)) {
			Fate.addCorruption(player, WARDEN_CORRUPTION);
		}
	}

	static void tick(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			LISTENING.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		boolean corrupted = Fate.isCorrupted(player);
		if (corrupted && player.tickCount % AUDIBLE_EVERY == 0) {
			// Shrieker: always faintly audible, even sneaking (sculk sensors pick it up)
			level.gameEvent(GameEvent.ENTITY_ACTION, player.position(), GameEvent.Context.of(player));
		}
		if (!corrupted) {
			LISTENING.remove(player.getUUID());
			return;
		}
		if (player.tickCount % CURE_CHECK_EVERY == 0) {
			listen(player, level);
		}
		if (player.tickCount % 200 == 0) {
			forgetOldShrieks(level.getGameTime());
		}
	}

	/** The cure: sitting through a whole song. */
	private static void listen(ServerPlayer player, ServerLevel level) {
		Listening listening = LISTENING.get(player.getUUID());
		if (listening != null) {
			JukeboxSongPlayer songs = level.getBlockEntity(listening.jukebox) instanceof JukeboxBlockEntity jukebox ? jukebox.getSongPlayer() : null;
			boolean near = player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(listening.jukebox)) <= (CURE_RADIUS + 0.5) * (CURE_RADIUS + 0.5);
			if (songs == null || !near) {
				LISTENING.remove(player.getUUID());
				return;
			}
			if (songs.isPlaying() && java.util.Objects.equals(songs.getSong(), listening.song)) {
				listening.heard = songs.getTicksSinceSongStarted();
				if (listening.heard >= listening.song.lengthInTicks()) {
					cure(player);
				}
				return;
			}
			// it stopped: if that was the end of the song, it counts
			if (listening.heard >= listening.song.lengthInTicks() - CURE_CHECK_EVERY * 2) {
				cure(player);
			} else {
				LISTENING.remove(player.getUUID());
			}
			return;
		}
		BlockPos center = player.blockPosition();
		int r = (int)Math.ceil(CURE_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
				continue;
			}
			JukeboxSongPlayer songs = jukebox.getSongPlayer();
			if (songs.isPlaying() && songs.getSong() != null && songs.getTicksSinceSongStarted() <= CURE_START_GRACE
				&& player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= CURE_RADIUS * CURE_RADIUS) {
				LISTENING.put(player.getUUID(), new Listening(pos.immutable(), songs.getSong(), songs.getTicksSinceSongStarted()));
				return;
			}
		}
	}

	private static void cure(ServerPlayer player) {
		LISTENING.remove(player.getUUID());
		if (Fate.purify(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.resonance_cured").withStyle(ChatFormatting.LIGHT_PURPLE,
				ChatFormatting.ITALIC));
		}
	}

	private static void forgetOldShrieks(long now) {
		for (Iterator<Trigger> it = SHRIEKERS.values().iterator(); it.hasNext();) {
			if (now - it.next().at > SHRIEKER_MEMORY) {
				it.remove();
			}
		}
	}

	static void forget(ServerPlayer player) {
		LISTENING.remove(player.getUUID());
	}

	static void clear() {
		SHRIEKERS.clear();
		LISTENING.clear();
	}
}
