package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (0.15.143) A giant reaches as far as he's grown, after the Size Changing Mod (KProject; All Rights Reserved, so only
 * the idea): any player the scale attribute has made {@link #GIANT_FROM} times bigger or more (the Great Ape, a
 * Behemoth...) has his reach for hitting and for blocks grown with him. The Great Ape's reach for hitting is his own
 * ({@code ZFighterLineage.apeReach}). The client's giant camera ({@code client/GiantCamera}) aims the crosshair along
 * its own line, inside this reach.
 */
public final class GiantReach {
	/** From this scale up, a player counts as a giant (the camera goes by it too). */
	public static final double GIANT_FROM = 1.6;
	/** Reach added for each 1 of scale past a player's own, for hitting and for blocks (vanilla's 3 and 4.5: it grows with him). */
	static final double ENTITY_PER_SCALE = 3.0, BLOCK_PER_SCALE = 4.5;
	/** Never more than this added (blocks): the Great Ape's eyes are ~27 blocks up, so this reaches ~20 across the ground. */
	static final double MAX_BONUS = 30.0;
	/** Checked this often (ticks). */
	static final int EVERY = 10;
	private static final Identifier ENTITY_ID = JaymosPowers.id("giant_entity_reach");
	private static final Identifier BLOCK_ID = JaymosPowers.id("giant_block_reach");

	private GiantReach() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GiantReach::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			double scale = player.getScale();
			double grown = scale >= GIANT_FROM ? scale - 1.0 : 0.0;
			set(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_ID, ZFighterLineage.ape(player) ? 0.0 : Math.min(grown * ENTITY_PER_SCALE, MAX_BONUS));
			set(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_ID, Math.min(grown * BLOCK_PER_SCALE, MAX_BONUS));
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (amount <= 0.0) {
			if (now != null) {
				instance.removeModifier(id);
			}
		} else if (now == null || Math.abs(now.amount() - amount) > 0.01) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
