package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * How the armourers' orders' sets show (Fantasy Armor, {@link FantasyArmorCompat}; docs/FantasyArmor-Plan.md, "set framework").
 * <ul>
 * <li><b>A whole set's sign.</b> Anyone in the whole of an order's set (and no worn-out piece) gives off a faint mote now
 * and then, in the set's colour ({@link #COLOUR}).</li>
 * <li><b>Remarks.</b> A player in the whole of one is remarked on by the people round them, each once in a long while
 * ({@link #COOLDOWN}): an oblique line, never an account (lang {@code speech.jaymoskingdoms.set.<set>.<n>}), the way
 * dirt and blood are ({@link Stench#remark}).</li>
 * </ul>
 * Fantasy Armor's own capes already hang from its sets, so none of ours are added.
 */
public final class SetSigns {
	/** Each order's set and the colour of its motes. */
	static final Map<String, Integer> COLOUR = Map.of(
		FantasyArmorCompat.DAWNWATCH, 0xF2C14E, FantasyArmorCompat.HEADSMAN, 0xB8912A, FantasyArmorCompat.SKY_ROBES, 0xE4EEFA,
		FantasyArmorCompat.WELL_WARDEN, 0xB4BEC8, FantasyArmorCompat.ASHEN, 0xD9542B, FantasyArmorCompat.HUNTER, 0xC8D0E0,
		FantasyArmorCompat.BARROW, 0x8A6A4B, FantasyArmorCompat.GIANT_SLAYER, 0x9FB1A0, FantasyArmorCompat.PIT, 0xA33B2B,
		FantasyArmorCompat.TWINNED, 0x8FB8E0);
	/** How often the motes and remarks are looked at (ticks), and how far round each player for motes (blocks). */
	static final int EVERY = 40;
	static final double MOTE_RANGE = 32.0;
	/** A mote's size and how many come at once. */
	static final float MOTE_SIZE = 0.35F;
	static final int MOTES = 1;
	/** Each person remarks on a player's set once in this many ticks; lines a set has (and for goblins and orcs). */
	static final int COOLDOWN = 24000, LINES = 3, GREEN_LINES = 1;

	/** When each person last remarked on each player's set (key: speaker|player). */
	private static final Map<String, Long> SAID = new HashMap<>();

	private SetSigns() {
	}

	static void register() {
		if (!FantasyArmorCompat.LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SAID.clear());
	}

	/** The order's set {@code e} wears whole, or null. */
	static @Nullable String worn(LivingEntity e) {
		for (String set : COLOUR.keySet()) {
			if (FantasyArmorCompat.whole(e, set)) {
				return set;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		java.util.Set<LivingEntity> seen = new java.util.HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MOTE_RANGE),
				e -> e.isAlive() && !e.isInvisible() && seen.add(e));
			for (LivingEntity e : near) {
				String set = worn(e);
				if (set != null) {
					level.sendParticles(Fx.mote(COLOUR.get(set), MOTE_SIZE), e.getX(), e.getY(0.6), e.getZ(), MOTES, 0.3, 0.4, 0.3, 0.0);
				}
			}
			String set = player.isInvisible() ? null : worn(player);
			if (set != null) {
				Stench.remark(player, SAID, COOLDOWN, "speech.jaymoskingdoms.set." + set, LINES, GREEN_LINES, now);
			}
		}
	}
}
