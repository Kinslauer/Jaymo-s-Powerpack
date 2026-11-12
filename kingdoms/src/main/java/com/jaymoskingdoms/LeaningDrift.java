package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * They become like you (docs/Pawn-Plan.md row B5; after the pawns whose inclinations drift with how their master plays
 * in Dragon's Dogma, a commercial game: ideas only).
 * <ul>
 * <li><b>Companions</b>: what you do nudges their leanings ({@link Comrades.Leaning}): calling them close makes guards,
 * calling for help makes medics, sending them on makes the first in, bringing down archers makes archer-hunters, big
 * foes the biggest-takers, finishing the downed the weak-clearers. Travelling beside another companion nudges them toward
 * its main leaning. At {@link #DRIFT_AT} a leaning becomes their second ("drift"); a second pushed on to
 * {@link #PROMOTE_AT} trades places with the main.</li>
 * <li><b>Bands</b>: a camp, village or realm whose archers keep falling to players ({@link #LOSSES}) sends its next
 * archers out with shields and the rest keep by them (a guard's leaning); one whose leaders keep falling guards its
 * leaders the same way.</li>
 * </ul>
 */
public final class LeaningDrift {
	/** Points to make a leaning someone's second; points for a second to trade places with the main. */
	static final int DRIFT_AT = 12, PROMOTE_AT = 24;
	/** Companions are looked at this often for who they travel beside (ticks), within this (blocks). */
	static final int BESIDE_EVERY = 1200;
	static final double BESIDE = 8.0, NEAR_KILL = 24.0;
	/** A band's archers or leaders falling this many times turns it to guarding them. */
	static final int LOSSES = 4;
	/** A foe this hale (max health) counts as a big one. */
	static final float BIG = 40.0F;

	/** On a companion: drift points per leaning key. Saved. */
	public static final AttachmentType<Map<String, Integer>> DRIFT = AttachmentRegistry.create(JaymosKingdoms.id("leaning_drift"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** On the Overworld: per band side, [archers lost, leaders lost] to players. Saved. */
	public static final AttachmentType<Map<String, List<Integer>>> BAND_LOSSES = AttachmentRegistry.create(JaymosKingdoms.id("band_losses"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf())));

	/** Archers already given their shield this fight. */
	private static final Set<java.util.UUID> SHIELDED = new HashSet<>();

	private LeaningDrift() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(LeaningDrift::died);
		ServerTickEvents.END_SERVER_TICK.register(LeaningDrift::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHIELDED.clear());
	}

	/** {@code m}'s drift toward {@code l} grows by {@code points}; it takes the leaning on when there's enough. */
	static void nudge(Mob m, Comrades.Leaning l, int points) {
		Map<String, Integer> drift = new HashMap<>(m.getAttachedOrElse(DRIFT, Map.of()));
		int now = drift.merge(l.key(), points, Integer::sum);
		Comrades.Leanings had = Comrades.leanings(m);
		if (l == had.main()) {
			drift.remove(l.key());
		} else if (l == had.second() && now >= PROMOTE_AT) {
			drift.remove(l.key());
			m.setAttached(Comrades.LEANINGS, l.key() + "," + had.main().key());
			Callouts.say(m, "drift", null, true);
		} else if (l != had.second() && now >= DRIFT_AT) {
			drift.remove(l.key());
			m.setAttached(Comrades.LEANINGS, had.main().key() + "," + l.key());
			Callouts.say(m, "drift", null, true);
		}
		m.setAttached(DRIFT, drift);
	}

	/** (From {@link QuickOrders}) an order given to {@code v} nudges it. */
	static void ordered(Villager v, int kind) {
		Comrades.Leaning l = switch (kind) {
			case QuickOrders.COME -> Comrades.Leaning.GUARD;
			case QuickOrders.HELP -> Comrades.Leaning.MEDIC;
			case QuickOrders.GO -> Comrades.Leaning.FIRST;
			default -> null;
		};
		if (l != null) {
			nudge(v, l, 1);
		}
	}

	private static void died(LivingEntity dead, DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		// the player's way of fighting rubs off on their companions
		Comrades.Leaning l = Comrades.ranged(dead) ? Comrades.Leaning.SHOOTERS : dead.getMaxHealth() >= BIG ? Comrades.Leaning.BIGGEST
			: Downed.isDowned(dead) ? Comrades.Leaning.WEAK : null;
		if (l != null) {
			String side = "p:" + player.getUUID();
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR_KILL),
				v -> v.isAlive() && Comrades.side(v).equals(side))) {
				nudge(v, l, 1);
			}
		}
		// and a band remembers what it lost to players
		if (dead instanceof Mob m) {
			String side = Comrades.side(m);
			if (FoeLore.keeps(side)) {
				boolean archer = Comrades.ranged(m), leader = BandLeaders.isLeader(m);
				if (archer || leader) {
					ServerLevel overworld = level.getServer().overworld();
					Map<String, List<Integer>> all = new HashMap<>(overworld.getAttachedOrElse(BAND_LOSSES, Map.of()));
					List<Integer> row = new ArrayList<>(all.getOrDefault(side, List.of(0, 0)));
					while (row.size() < 2) {
						row.add(0);
					}
					row.set(archer ? 0 : 1, row.get(archer ? 0 : 1) + 1);
					all.put(side, List.copyOf(row));
					overworld.setAttached(BAND_LOSSES, all);
				}
			}
		}
	}

	/** The leaning a band has turned to after its losses (a guard's), or null. Applied as everyone's second. */
	static Comrades.@Nullable Leaning bandLeans(Mob m, String side) {
		if (!FoeLore.keeps(side) || !(m.level() instanceof ServerLevel level)) {
			return null;
		}
		List<Integer> row = level.getServer().overworld().getAttachedOrElse(BAND_LOSSES, Map.of()).get(side);
		return row != null && row.stream().anyMatch(n -> n >= LOSSES) ? Comrades.Leaning.GUARD : null;
	}

	private static boolean archersLost(ServerLevel level, String side) {
		List<Integer> row = level.getServer().overworld().getAttachedOrElse(BAND_LOSSES, Map.of()).get(side);
		return row != null && !row.isEmpty() && row.get(0) >= LOSSES;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				for (Mob m : Comrades.fighters(level)) {
					String side = Comrades.side(m);
					if (FoeLore.keeps(side) && Comrades.ranged(m) && SHIELDED.add(m.getUUID()) && archersLost(level, side)
						&& m.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
						// archers kept dying: these come out behind a shield
						m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
						m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
					}
				}
			}
			if (SHIELDED.size() > 4096) {
				SHIELDED.clear();
			}
		}
		if (now % BESIDE_EVERY != 0) {
			return;
		}
		// travelling together: each companion drifts a little toward a mate's main leaning
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String side = "p:" + player.getUUID();
			List<Villager> yours = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR_KILL),
				v -> v.isAlive() && !Downed.isDowned(v) && Comrades.side(v).equals(side));
			for (Villager v : yours) {
				for (Villager mate : yours) {
					if (mate != v && mate.distanceToSqr(v) < BESIDE * BESIDE) {
						nudge(v, Comrades.leanings(mate).main(), 1);
						break;
					}
				}
			}
		}
	}
}
