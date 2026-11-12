package com.jaymospowers.costume;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.gifted.GiftedCostume;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Matching colours: costumes sewn from the same design make one company ({@link SecretIdentity#key}: the same name and
 * the same whole look). Two or more of a company within {@link #RANGE} blocks of each other (players, and gifted folk
 * in the same costume) each feel it: a little resistance and a slow recovery, nothing shown on the screen. When a
 * company forms, a faint shimmer in the costume's main colour passes over its members. No town gives reputation for it:
 * townsfolk have no love of masked vigilantes.
 */
public final class Rally {
	// ---- tuning ----
	/** How close a company stands (blocks), and how often it is looked at (ticks). */
	public static final double RANGE = 12.0;
	public static final int EVERY = 20;
	/** The rally: Resistance level (0 is the first), and what each second of it mends (health). */
	public static final int RESISTANCE_AMPLIFIER = 0;
	public static final float MEND = 0.25F;

	/** Players whose company stood last look, to show the shimmer only when it forms. */
	private static final Set<UUID> RALLIED = new HashSet<>();

	private Rally() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Rally::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RALLIED.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		Set<UUID> now = new HashSet<>();
		for (ServerPlayer player : players) {
			CostumeDesign design = player.isAlive() && !player.isSpectator() ? Costume.shown(player) : null;
			if (design == null || !companion(player, design, players)) {
				continue;
			}
			now.add(player.getUUID());
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EVERY * 2, RESISTANCE_AMPLIFIER, true, false, false));
			if (player.getHealth() < player.getMaxHealth()) {
				player.heal(MEND);
			}
			if (!RALLIED.contains(player.getUUID())) {
				shimmer(player, design);
			}
		}
		RALLIED.clear();
		RALLIED.addAll(now);
	}

	/** Whether another of {@code design}'s company stands within {@link #RANGE} of {@code player}. */
	private static boolean companion(ServerPlayer player, CostumeDesign design, List<ServerPlayer> players) {
		String key = SecretIdentity.key(design);
		double range = RANGE * RANGE;
		for (ServerPlayer other : players) {
			if (other != player && other.isAlive() && !other.isSpectator() && other.level() == player.level() && other.distanceToSqr(player) <= range) {
				CostumeDesign theirs = Costume.shown(other);
				if (theirs != null && SecretIdentity.key(theirs).equals(key)) {
					return true;
				}
			}
		}
		// gifted folk in the same costume count too
		for (Villager folk : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE), e -> e.isAlive())) {
			CostumeDesign theirs = GiftedCostume.shown(folk);
			if (theirs != null && SecretIdentity.key(theirs).equals(key)) {
				return true;
			}
		}
		return false;
	}

	/** The faint shimmer of a company forming: motes in the costume's main colour rising round {@code member}. */
	private static void shimmer(LivingEntity member, CostumeDesign design) {
		if (!(member.level() instanceof ServerLevel level)) {
			return;
		}
		int colour = design.primary().getTextureDiffuseColor() & 0xFFFFFF;
		level.sendParticles(MagicFx.mote(colour, 0.7F), member.getX(), member.getY() + member.getBbHeight() * 0.5, member.getZ(), 8, 0.35, 0.5, 0.35, 0.01);
	}
}
