package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.level.MoonPhase;

/**
 * (0.7.332) The hidden change: the tells a werewolf player gives off as themselves, for others to spot. From the wild
 * stage of the curse ({@link FeralCurse}, stage {@link #FROM_STAGE}), and in the hour before a forced change
 * ({@link #HOUR_BEFORE}), their hands go hairy and dark, their eyes flash yellow now and then, and when they're hit they
 * growl ({@link #GROWL_COOLDOWN} apart). The first two are {@link #TELLS}, synced to everyone and drawn by
 * {@code WolfTells}; the growl is a sound.
 */
public final class HiddenChange {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The feral stage the tells start at. */
	static final int FROM_STAGE = 2;
	/** How long before the night's forced change the tells show (ticks: an hour of the in-game day, 1000 ticks). */
	static final long HOUR_BEFORE = 1000L;
	/** The growl when hit: its volume and pitch, and the wait before the next (ticks). */
	static final float GROWL_VOLUME = 0.9F, GROWL_PITCH = 0.7F;
	static final int GROWL_COOLDOWN = 60;

	/** The bits of {@link #TELLS}. */
	public static final int HAIRY_HANDS = 1, FLASHING_EYES = 2;

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** On a werewolf player showing the tells (bits above); synced to everyone, not saved. */
	public static final AttachmentType<Integer> TELLS = AttachmentRegistry.create(JaymosKingdoms.id("wolf_tells"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Not kept: when each may growl again. */
	private static final Map<UUID, Long> GROWLED = new HashMap<>();

	private HiddenChange() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HiddenChange::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && tells(player) != 0 && !WerewolfPlayers.beast(player)) {
				growl(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GROWLED.clear());
	}

	/** The tells {@code e} shows (bits of {@link #TELLS}), on either side. */
	public static int tells(Entity e) {
		Integer t = e.getAttached(TELLS);
		return t == null ? 0 : t;
	}

	private static void growl(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now < GROWLED.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		GROWLED.put(player.getUUID(), now + GROWL_COOLDOWN);
		player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
			SoundSource.PLAYERS, GROWL_VOLUME, GROWL_PITCH);
	}

	// ---- every second --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long clock = overworld.getOverworldClockTime();
		boolean soon = changeSoon(overworld, clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 13) {
				continue;
			}
			boolean show = FeralCurse.stillCursed(player) && player.isAlive() && !player.isSpectator() && !WerewolfPlayers.beast(player)
				&& (FeralCurse.stage(player) >= FROM_STAGE || soon);
			int bits = show ? HAIRY_HANDS | FLASHING_EYES : 0;
			if (bits != tells(player)) {
				if (bits == 0) {
					player.removeAttached(TELLS);
				} else {
					player.setAttached(TELLS, bits);
				}
			}
		}
	}

	/** It's the last {@link #HOUR_BEFORE} ticks before a night with a full moon (or one an operator called). */
	private static boolean changeSoon(ServerLevel overworld, long clock) {
		long tod = Math.floorMod(clock, 24000L);
		if (Vampires.night(clock) || tod >= Vampires.NIGHT_FROM || tod < Vampires.NIGHT_FROM - HOUR_BEFORE) {
			return false;
		}
		Long forced = overworld.getAttached(Lycans.FORCED);
		// (the day's moon phase is tonight's; Lycans.fullMoon would count last night's forced change)
		return overworld.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON
			|| forced != null && forced == Vampires.nightIndex(clock) + 1;
	}
}
