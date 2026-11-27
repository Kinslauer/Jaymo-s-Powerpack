package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.hunter.Draught;
import com.jaymospowers.alchemy.hunter.Draughts;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Grey sight, drawn (Hunter's Road, patch 6): while the grey-sight draught works on you and the light where you stand is low,
 * the living things within its reach ({@link Draughts#SIGHT_RANGE}) get an outline, seen by you alone
 * ({@code DarkSightGlowMixin} on {@code Minecraft.shouldEntityAppearGlowing}, as the other sight powers do; nothing is set on
 * the entity, so a real glow from elsewhere is left alone).
 */
public final class DarkSightClient {
	/** The entities outlined this tick (ids). */
	private static final Set<Integer> OUTLINED = new HashSet<>();

	private DarkSightClient() {
	}

	/** Whether {@code entity} is outlined by grey sight on this screen. */
	public static boolean seen(Entity entity) {
		return OUTLINED.contains(entity.getId());
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			OUTLINED.clear();
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.level == null) {
				return;
			}
			int level = Draughts.level(player, Draught.GREY_SIGHT);
			if (level <= 0 || minecraft.level.getMaxLocalRawBrightness(player.blockPosition()) > Draughts.DARK_LIGHT) {
				return;
			}
			double range = Draughts.SIGHT_RANGE[level - 1];
			for (LivingEntity living : minecraft.level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(range),
				e -> e != player && e.isAlive() && !e.isInvisible() && e.distanceToSqr(player) <= range * range)) {
				OUTLINED.add(living.getId());
			}
		});
	}
}
