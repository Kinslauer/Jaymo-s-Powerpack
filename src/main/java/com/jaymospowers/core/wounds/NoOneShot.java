package com.jaymospowers.core.wounds;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * No one-hit deaths from full health (Clouds and Bits patch 6): a blow that would kill a player who was at full health
 * downs them instead ({@link Downed}), where the downed state applies to them. Downed already catches every lethal blow on
 * a survival player, bar one way round it that this closes for a full-health player: a blast that blows them apart
 * ({@link com.jaymospowers.core.BlastGore#gibs} asks {@link #spared} first and so rolls no gib).
 * <p>
 * Left alone, because they are the point of them: damage that bypasses invulnerability (the void, /kill), a beheading, an
 * execution, a totem in hand, a downed player's own finishing blows, and Kingdoms' No Mercy. Where the downed state doesn't
 * apply (creative, spectator) nothing can kill them anyway, so there is no 1-health fallback to give.
 * <p>
 * "Full health" is {@link #SLACK} half hearts under the maximum or better when the blow began.
 */
public final class NoOneShot {
	/** How far under their maximum health still counts as full (half hearts: a regeneration tick or a rounding). */
	static final float SLACK = 0.5F;

	/** Player → game time of the last blow that found them at full health. Not saved. */
	private static final Map<ServerPlayer, Long> FULL = new WeakHashMap<>();

	private NoOneShot() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && amount > 0.0F) {
				if (player.getHealth() >= player.getMaxHealth() - SLACK) {
					FULL.put(player, player.level().getGameTime());
				} else {
					FULL.remove(player);
				}
			}
			return true;
		});
	}

	/** Whether this blow found {@code entity} (a player) at full health, and the downed state is what answers a death. */
	public static boolean spared(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		Long at = FULL.get(player);
		return at != null && at == player.level().getGameTime() && !Downed.isDowned(player) && !Downed.FINISHING.contains(player)
			&& Downed.downable(player) && Downed.spared(player, source);
	}
}
