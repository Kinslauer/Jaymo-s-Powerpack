package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * B, Hush: a dome of silence {@code RADIUS} blocks round where you stand, for {@code TICKS}. Inside it:
 * <ul>
 * <li>no sounds (each client mutes what's inside, and everything a listener inside would hear; the caster still hears,
 * unless corrupted: Shrieker's Hush drains their own hearing too);</li>
 * <li>no vibrations: sculk sensors, shriekers and Wardens hear nothing that happens there;</li>
 * <li>other players' power keys do nothing (the core asks {@link #silenced} before passing a press on; cooldowns keep
 * ticking). The caster's own powers work, and the Web symbiote's own doings never go through the keys, so it's
 * untouched.</li>
 * </ul>
 * Also Footstep sense: a sneaking Resonance user's steps make no vibrations (Shrieker loses this).
 */
public final class ResonanceHush {
	public static final double RADIUS = 6.0;
	public static final int TICKS = 100;

	/** On the caster, synced to everyone: their dome (each client mutes and shades it). */
	public record Dome(Vec3 center, long until, boolean corrupted) {
		public static final StreamCodec<ByteBuf, Dome> STREAM_CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, Dome::center,
			ByteBufCodecs.VAR_LONG, Dome::until,
			ByteBufCodecs.BOOL, Dome::corrupted,
			Dome::new);

		public boolean contains(Vec3 pos) {
			return pos.distanceToSqr(center) <= RADIUS * RADIUS;
		}
	}

	public static final AttachmentType<Dome> DOME = AttachmentRegistry.create(JaymosPowers.id("resonance_hush"),
		builder -> builder.syncWith(Dome.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private record Active(ServerLevel level, UUID owner, Dome dome) {
	}

	private static final List<Active> ACTIVE = new ArrayList<>();

	private ResonanceHush() {
	}

	static void init() {
		Powers.addSilencer(ResonanceHush::silenced);
		ServerTickEvents.END_SERVER_TICK.register(ResonanceHush::tickAll);
	}

	static boolean cast(ServerPlayer player) {
		end(player);
		return cast(player.level(), player, Fate.isCorrupted(player), TICKS);
	}

	/**
	 * (0.17.9) A dome round {@code caster} for {@code ticks}: a player's Hush, or a gifted villager's
	 * ({@code ResonanceGift}; Andy: "It's okay to silence the players").
	 */
	static boolean cast(ServerLevel level, LivingEntity caster, boolean corrupted, int ticks) {
		Dome dome = new Dome(caster.position(), level.getGameTime() + ticks, corrupted);
		// the last sound before the silence
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		ACTIVE.removeIf(active -> active.owner.equals(caster.getUUID()));
		ACTIVE.add(new Active(level, caster.getUUID(), dome));
		caster.setAttached(DOME, dome);
		ResonanceEcho.ripple(level, dome.center().add(0.0, 0.1, 0.0), RADIUS);
		for (ServerPlayer other : level.players()) {
			if (other != caster && dome.contains(other.position()) && Powers.of(other) != null) {
				other.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_hushed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
		return true;
	}

	/** Takes the player's dome down early (power lost). */
	static void end(ServerPlayer player) {
		ACTIVE.removeIf(active -> active.owner.equals(player.getUUID()));
		if (player.getAttached(DOME) != null) {
			player.removeAttached(DOME);
		}
	}

	/** Whether {@code player}'s power keys are switched off: they stand in someone else's dome. */
	public static boolean silenced(ServerPlayer player) {
		for (Active active : ACTIVE) {
			if (active.level == player.level() && !active.owner.equals(player.getUUID()) && active.dome.contains(player.position())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a game event (a vibration) at {@code pos} goes unheard: it's inside a dome, or it's the step of a sneaking
	 * Resonance user (Footstep sense; not for Shrieker, who is always faintly audible).
	 */
	public static boolean muffles(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		Entity source = context.sourceEntity();
		if (source instanceof Player player && player.isShiftKeyDown() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)
			&& Powers.has(player, ResonancePower.INSTANCE) && !Fate.isCorrupted(player)) {
			return true;
		}
		for (Active active : ACTIVE) {
			if (active.level == level && active.dome.contains(pos)) {
				return true;
			}
		}
		return false;
	}

	private static void tickAll(MinecraftServer server) {
		for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext();) {
			Active active = it.next();
			long now = active.level.getGameTime();
			if (now >= active.dome.until()) {
				it.remove();
				Entity owner = active.level.getEntity(active.owner);
				if (owner != null && owner.getAttached(DOME) != null) {
					owner.removeAttached(DOME);
				}
				continue;
			}
			if (now % 5 == 0) {
				// the dome's shell: grey motes drifting on its surface
				Vec3 c = active.dome.center();
				for (int i = 0; i < 14; i++) {
					double yaw = active.level.getRandom().nextDouble() * Math.PI * 2.0;
					double up = active.level.getRandom().nextDouble() * Math.PI * 0.5;
					active.level.sendParticles(ParticleTypes.WHITE_ASH, c.x + Math.cos(yaw) * Math.cos(up) * RADIUS, c.y + Math.sin(up) * RADIUS,
						c.z + Math.sin(yaw) * Math.cos(up) * RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
	}
}
