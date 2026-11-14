package com.jaymospowers.power.cryo;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * (Jung) Cryokinesis' corruption, kept simple: the cold that kills. Each creature that dies frozen at a Cryokinesis
 * user's hand fills the meter ({@link Fate#addCorruption}): {@link #MOB} for a beast or a monster, {@link #PERSON} for a
 * villager or a player. A full meter corrupts the power; while corrupted, frost pours off its holder ({@link #FROST})
 * and the gods of the elements watch what they kill (ElementalCorruption). The cure is warmth: a Purifying Draught drunk
 * within {@link #HEARTH} blocks of a lit campfire thaws it ({@link #thaw}).
 */
public final class CryoCorruption {
	// ---- tuning ----
	/** Meter points for a creature killed while frozen: a beast or monster, and a person. */
	static final int MOB = 4, PERSON = 15;
	/** A lit campfire within this many blocks warms the drinker of a Purifying Draught. */
	static final int HEARTH = 3;
	/** The frost a corrupted holder sheds: its colour and how often (ticks). */
	static final int FROST = 0xCFEFFF, FROST_EVERY = 10;

	private CryoCorruption() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != victim && Powers.has(killer, CryoPower.INSTANCE)
				&& victim.getTicksFrozen() > 0) {
				Fate.addCorruption(killer, person(victim) ? PERSON : MOB);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CryoCorruption::tick);
	}

	private static boolean person(LivingEntity victim) {
		return victim instanceof Player || Carry.isVillager(victim);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % FROST_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && Powers.has(player, CryoPower.INSTANCE) && Fate.isCorrupted(player)
				&& player.level() instanceof ServerLevel level) {
				level.sendParticles(MagicFx.mote(FROST, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
			}
		}
	}

	/** (Purifying Draught) A corrupted holder warmed at a lit campfire is thawed of the corruption for good. */
	public static void thaw(ServerPlayer player) {
		if (!Powers.has(player, CryoPower.INSTANCE) || !Fate.isCorrupted(player)) {
			return;
		}
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-HEARTH, -HEARTH, -HEARTH), at.offset(HEARTH, HEARTH, HEARTH))) {
			if (CampfireBlock.isLitCampfire(player.level().getBlockState(pos))) {
				if (Fate.purify(player)) {
					// (no chat: the fire's warmth reaches all the way in)
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
				}
				return;
			}
		}
	}

	/** (CryoPower#onCorrupted) The cold has their heart now. */
	static void corrupted(ServerPlayer player) {
		// (no chat: the cold finds their heart, heard and seen; the frost off them goes on after)
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE,
			net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.5F);
	}
}
