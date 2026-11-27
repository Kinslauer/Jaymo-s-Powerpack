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
 * the living things within its reach ({@link Draughts#SIGHT_RANGE}) get an outline, seen by you alone (the outline flag is
 * set on this client's copy of the entity and never sent).
 */
final class DarkSightClient {
	/** The entities this client has outlined, to take the outline off again. */
	private static final Set<Integer> OUTLINED = new HashSet<>();

	private DarkSightClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.level == null) {
				OUTLINED.clear();
				return;
			}
			int level = Draughts.level(player, Draught.GREY_SIGHT);
			boolean dark = level > 0 && minecraft.level.getMaxLocalRawBrightness(player.blockPosition()) <= Draughts.DARK_LIGHT;
			Set<Integer> now = new HashSet<>();
			if (dark) {
				double range = Draughts.SIGHT_RANGE[level - 1];
				for (LivingEntity living : minecraft.level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(range),
					e -> e != player && e.isAlive() && !e.isInvisible() && e.distanceToSqr(player) <= range * range)) {
					living.setGlowingTag(true);
					now.add(living.getId());
				}
			}
			for (int id : OUTLINED) {
				if (!now.contains(id)) {
					Entity e = minecraft.level.getEntity(id);
					if (e != null) {
						e.setGlowingTag(false);
					}
				}
			}
			OUTLINED.clear();
			OUTLINED.addAll(now);
		});
	}
}
