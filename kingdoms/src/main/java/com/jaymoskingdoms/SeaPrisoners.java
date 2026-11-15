package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-15): "only pirates raze ships and enslave their victims". He chose that navies spare the beaten. When
 * a ship goes down to another ship's guns, some of her crew are pulled out of the sea instead of all drowning:
 * <ul>
 * <li>a navy that sinks a pirate takes her survivors ashore at its harbour, where its guards take them before the ruler
 * and to the cells ({@link Arrests#jail});</li>
 * <li>a pirate that sinks anyone else drags her survivors to its cove in chains, captives to be freed and brought home
 * ({@link Captives}). With the cove out of reach they're held below till someone comes near it.</li>
 * </ul>
 * Only MCA's villagers are taken (a folk crew drowns as before).
 */
final class SeaPrisoners {
	/** At most this many of a sunk crew are taken, each with this chance. */
	static final int MOST = 3;
	static final float CHANCE = 0.6F;
	/** How far round the harbour's shore, or the cove's camp, they're put down (blocks). */
	static final int SPREAD = 4;
	/** How often a cove's held captives are brought up (ticks), and how near a player must be. */
	static final int CHECK_TICKS = 200;
	static final double NEAR = 64.0;
	/** The crime a navy's prisoners are judged for. */
	static final String PIRACY = "piracy";

	/** Captives held below for a cove out of reach: camp id → the realms they were taken from (-1: none). */
	static final AttachmentType<Map<String, List<Integer>>> HELD = AttachmentRegistry.create(JaymosKingdoms.id("sea_captives"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf())));

	private SeaPrisoners() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SeaPrisoners::tick);
	}

	/** {@code sunk} is going down, {@code by} the shot that did it: her survivors taken, if her sinker takes any (before the rest drown). */
	static void taken(ServerLevel level, Ship sunk, Crew crew, @Nullable Entity by) {
		Entity who = by instanceof Projectile p ? p.getOwner() : by;
		if (who == null || !McaCompat.LOADED) {
			return;
		}
		MinecraftServer server = level.getServer();
		Crew.Side side;
		int faction;
		if (who.getVehicle() instanceof Ship s && s != sunk && s.crew != null) {
			side = s.crew.side;
			faction = s.crew.faction;
		} else if (Corsairs.isCrew(who) && who instanceof Worker w) {
			// (a gunner off his ship's deck: whose he is)
			faction = w.factionId();
			side = PirateCoves.is(server, faction) ? Crew.Side.PIRATE : Crew.Side.NAVY;
		} else {
			return;
		}
		if (faction == crew.faction) {
			return;
		}
		if (side == Crew.Side.PIRATE && crew.side != Crew.Side.PIRATE) {
			enslave(level, sunk, crew, faction);
		} else if (side == Crew.Side.NAVY && crew.side == Crew.Side.PIRATE) {
			jail(level, sunk, crew, faction);
		}
	}

	/** Who of her crew are pulled out: up to {@link #MOST} of MCA's villagers aboard, taken off her. */
	private static List<Villager> survivors(ServerLevel level, Ship sunk, Crew crew) {
		RandomSource random = level.getRandom();
		List<Villager> out = new ArrayList<>();
		for (LivingEntity hand : sunk.crewAboard()) {
			if (out.size() >= MOST) {
				break;
			}
			if (hand instanceof Villager v && !v.isBaby() && !Downed.isDowned(v) && random.nextFloat() < CHANCE) {
				Crewmen.release(v, crew);
				out.add(v);
			}
		}
		return out;
	}

	/** A navy's prisoners: ashore at its harbour, to the cells. */
	private static void jail(ServerLevel level, Ship sunk, Crew crew, int realm) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, realm);
		Navies.Harbour h = Navies.harbour(server, realm);
		if (f == null || f.fallen() || h == null) {
			return;
		}
		List<Villager> taken = survivors(level, sunk, crew);
		if (taken.isEmpty()) {
			return;
		}
		boolean seen = level.isPositionEntityTicking(h.shore());
		for (Villager v : taken) {
			if (seen) {
				put(level, v, h.shore());
				if (Arrests.jail(level, v, f, PIRACY)) {
					continue;
				}
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			}
			// (no guard there, or nobody to see it: taken below all the same)
			v.discard();
		}
		Raids.tell(level, sunk.blockPosition(), Component.translatable("message.jaymoskingdoms.sea_prisoners.jailed", taken.size(), crew.name, f.name())
			.withStyle(ChatFormatting.GOLD));
		JaymosKingdoms.LOGGER.info("{} took {} of the {}'s crew prisoner", f.name(), taken.size(), crew.name);
	}

	/** A pirate's captives: in chains at its cove (or held below till it's in reach). */
	private static void enslave(ServerLevel level, Ship sunk, Crew crew, int camp) {
		MinecraftServer server = level.getServer();
		Faction pirates = Factions.get(server, camp);
		if (pirates == null || pirates.fallen()) {
			return;
		}
		Faction home = Factions.get(server, crew.faction);
		if (home == null || !home.type().isRealm() || home.fallen()) {
			home = Captives.nearestSettlement(server, sunk.blockPosition());
		}
		if (home == null) {
			return;
		}
		List<Villager> taken = survivors(level, sunk, crew);
		if (taken.isEmpty()) {
			return;
		}
		BlockPos center = center(server, pirates);
		if (level.isPositionEntityTicking(center)) {
			for (Villager v : taken) {
				put(level, v, center);
				Captives.hold(level, v, home);
			}
		} else {
			Map<String, List<Integer>> all = new HashMap<>(level.getAttachedOrElse(HELD, Map.of()));
			List<Integer> held = new ArrayList<>(all.getOrDefault(Integer.toString(camp), List.of()));
			for (Villager v : taken) {
				held.add(home.id());
				v.discard();
			}
			all.put(Integer.toString(camp), List.copyOf(held));
			level.setAttached(HELD, Map.copyOf(all));
		}
		Raids.tell(level, sunk.blockPosition(), Component.translatable("message.jaymoskingdoms.sea_prisoners.enslaved", taken.size(), crew.name, pirates.name())
			.withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.info("{} carried off {} of the {}'s crew in chains", pirates.name(), taken.size(), crew.name);
	}

	/** Captives held below for a cove come up in chains there once a player comes near it. */
	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % CHECK_TICKS != 0) {
			return;
		}
		Map<String, List<Integer>> all = level.getAttachedOrElse(HELD, Map.of());
		if (all.isEmpty()) {
			return;
		}
		Map<String, List<Integer>> next = new HashMap<>(all);
		RandomSource random = level.getRandom();
		for (Map.Entry<String, List<Integer>> e : all.entrySet()) {
			Faction pirates = Factions.get(server, Integer.parseInt(e.getKey()));
			if (pirates == null || pirates.fallen()) {
				next.remove(e.getKey());
				continue;
			}
			BlockPos center = center(server, pirates);
			if (!level.isPositionEntityTicking(center)
				|| level.getNearestPlayer(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, NEAR, false) == null) {
				continue;
			}
			for (int realm : e.getValue()) {
				BlockPos near = center.offset(Mth.nextInt(random, -SPREAD, SPREAD), 0, Mth.nextInt(random, -SPREAD, SPREAD));
				Faction home = realm < 0 ? null : Factions.get(server, realm);
				Captives.take(level, Construction.standable(level, near, 4), home != null && !home.fallen() ? home : null);
			}
			next.remove(e.getKey());
		}
		level.setAttached(HELD, Map.copyOf(next));
	}

	/** The middle of a pirate camp, where its captives are kept. */
	private static BlockPos center(MinecraftServer server, Faction camp) {
		Camps.CampData data = Camps.data(server, camp.id());
		return data != null ? data.center() : camp.seat();
	}

	/** {@code v} put down on open ground near {@code at}. */
	private static void put(ServerLevel level, Villager v, BlockPos at) {
		RandomSource random = level.getRandom();
		BlockPos near = at.offset(Mth.nextInt(random, -SPREAD, SPREAD), 0, Mth.nextInt(random, -SPREAD, SPREAD));
		BlockPos ground = ShutIn.openGround(level, near);
		Vec3 to = Vec3.atBottomCenterOf(ground != null ? ground : at);
		v.snapTo(to.x, to.y, to.z, v.getYRot(), 0.0F);
		v.setDeltaMovement(Vec3.ZERO);
		v.resetFallDistance();
	}
}
