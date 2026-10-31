package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.jaymoskingdoms.whaling.Sharks;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.433) The far grounds (Andy: "whalers should go further and further out to open ocean with every successful
 * expedition, incurring greater risks of attack from pirates and wildlife, but also greater reward").
 *
 * <p>Each realm keeps a count of its whalers' voyages that came home with whales ({@link #REACH}). The nearer grounds
 * are fished out by them: every one pushes the realm's next whaling ground {@link #STEP} blocks further out to sea, up to
 * {@link #MOST} steps (a voyage's {@code far}). A whaler lost at sea, or one never heard from again, puts it back a step.
 *
 * <p>Out there:
 * <ul>
 * <li><b>pirates</b>: sea rovers out of some pirate cove (any barbarian camp if there's no cove) cruise the far grounds.
 * While she's on her ground, every {@link #RISK_EVERY} ticks there's a chance (more each step out) one heaves into sight
 * {@link #ROVER_MIN}-{@link #ROVER_MAX} blocks off and runs her down, a galleon likelier the further out. Their home is
 * that ground, so they keep cruising it (they're stowed once nobody's been near for {@link #ROVER_LIFE});</li>
 * <li><b>wildlife</b>: more sharks come to her carcass, sooner ({@link com.jaymoskingdoms.whaling.WhaleCarcass#wildness});
 * from step {@link #SHARKS_FROM} sharks come up under her lowered boats after the oarsmen; her pods hold more sperm
 * whales (which turn on the boats that strike them) and the white whale now and then;</li>
 * <li><b>reward</b>: the old bulls of the far grounds are fatter: her hold comes home {@link #RICHER} more for each step
 * out, pods are bigger, and ambergris is likelier;</li>
 * <li>a voyage made out of anyone's sight may not come home at all ({@link #UNSEEN_LOSS} a step out).</li>
 * </ul>
 */
final class FarGrounds {
	/** Each step out puts her ground this many blocks further from her harbour; at most this many steps. */
	static final int STEP = 160, MOST = 10;
	/** Her hold comes home this share richer for each step out; ambergris this much likelier. */
	static final float RICHER = 0.25F, AMBERGRIS_PER_FAR = 0.05F;
	/** Her pods out there: a whale more every this many steps, this chance more a step of a sperm whale, and of the white whale. */
	static final int POD_MORE_EVERY = 3;
	static final float SPERM_PER_FAR = 0.06F, WHITE_PER_FAR = 0.015F;
	/** How often (ticks) on her ground the dangers are rolled. */
	static final int RISK_EVERY = 1200;
	/** A sea rover's chance each roll for each step out (at most this), from this step; at most one more rover every this many steps. */
	static final float PIRATE_PER_FAR = 0.035F, PIRATE_MOST = 0.35F;
	static final int PIRATES_FROM = 1, ROVER_MORE_EVERY = 4;
	/** Where a rover heaves into sight (blocks off her), out of this much of any player; galleon's chance, and its rise a step. */
	static final double ROVER_MIN = 96.0, ROVER_MAX = 128.0, ROVER_HIDE = 72.0;
	static final float GALLEON = 0.2F, GALLEON_PER_FAR = 0.06F;
	/** A rover is stowed once this long out (ticks) with no player this near. */
	static final long ROVER_LIFE = 24000L;
	static final double ROVER_NEAR = 112.0;
	/** Sharks under her boats: from this step, this chance a roll for each step out. */
	static final int SHARKS_FROM = 2;
	static final float SHARK_PER_FAR = 0.07F;
	/** A voyage made unseen is lost with this chance for each step out (at most this). */
	static final float UNSEEN_LOSS = 0.04F, UNSEEN_LOSS_MOST = 0.35F;

	/** Each realm's whaling voyages come home with whales (realm id → count); kept on the Overworld. */
	static final AttachmentType<Map<String, Integer>> REACH = AttachmentRegistry.create(JaymosKingdoms.id("whaling_reach"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** Sea rovers put out on the far grounds (ship id → when); kept on the Overworld. */
	static final AttachmentType<Map<String, Long>> ROVERS = AttachmentRegistry.create(JaymosKingdoms.id("sea_rovers"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private FarGrounds() {
	}

	// ---- how far out ---------------------------------------------------------------------------------------------------

	/** How many steps out realm {@code realm}'s next whaling ground lies. */
	static int far(MinecraftServer server, int realm) {
		int reach = Factions.level(server).getAttachedOrElse(REACH, Map.of()).getOrDefault(Integer.toString(realm), 0);
		return Mth.clamp(reach, 0, MOST);
	}

	private static void reach(MinecraftServer server, int realm, int by) {
		ServerLevel level = Factions.level(server);
		Map<String, Integer> all = new HashMap<>(level.getAttachedOrElse(REACH, Map.of()));
		String key = Integer.toString(realm);
		// (past the most it's no further out, but a loss still only puts it back one step from there)
		all.put(key, Mth.clamp(all.getOrDefault(key, 0) + by, 0, MOST));
		level.setAttached(REACH, Map.copyOf(all));
	}

	/** {@code v} came home with whales: her realm's next ground is a step further out. */
	static void homeWithWhales(ServerLevel level, Faction f, Whalers.Voyage v, BlockPos at) {
		if (v.whales <= 0) {
			return;
		}
		int before = far(level.getServer(), f.id());
		reach(level.getServer(), f.id(), 1);
		if (far(level.getServer(), f.id()) > before) {
			Raids.tell(level, at, Component.translatable("message.jaymoskingdoms.whaler.further", f.name()).withStyle(ChatFormatting.AQUA));
		}
	}

	/** {@code f}'s whaler was lost: the grounds that took her are shunned, a step nearer home. */
	static void lost(MinecraftServer server, int realm) {
		reach(server, realm, -1);
	}

	/** Her catch from {@code v.far} steps out: every part of it richer, ambergris likelier. */
	static void richer(Whalers.Voyage v, RandomSource random) {
		if (v.far <= 0) {
			return;
		}
		float more = 1.0F + RICHER * v.far;
		for (String what : List.of("oil", "meat", "bone", "blubber", "ambergris")) {
			int n = v.get(what);
			if (n > 0) {
				v.hold.put(what, Math.round(n * more));
			}
		}
		for (int i = 0; i < v.whales; i++) {
			if (random.nextFloat() < AMBERGRIS_PER_FAR * v.far) {
				v.add("ambergris", 1);
			}
		}
	}

	/** An unseen voyage {@code v.far} steps out: lost with all hands (true), or home. */
	static boolean lostUnseen(Whalers.Voyage v, RandomSource random) {
		return random.nextFloat() < Math.min(UNSEEN_LOSS_MOST, UNSEEN_LOSS * v.far);
	}

	/** How many whales a pod {@code far} steps out holds, from the near grounds' {@code n}. */
	static int pod(int n, int far) {
		return n + far / POD_MORE_EVERY;
	}

	/** A pod whale {@code far} steps out: a sperm whale likelier, the white whale now and then. */
	static void breed(GreatWhale whale, int far, RandomSource random) {
		if (far <= 0) {
			return;
		}
		if (random.nextFloat() < WHITE_PER_FAR * far) {
			whale.becomes(GreatWhale.WHITE);
		} else if (random.nextFloat() < SPERM_PER_FAR * far) {
			whale.becomes(GreatWhale.SPERM);
		}
	}

	// ---- the dangers out there -------------------------------------------------------------------------------------------

	/** Her captain's look, on her ground {@code v.far} steps out: now and then a sea rover, or sharks under her boats. */
	static void dangers(ServerLevel level, Ship ship, Crew crew, Whalers.Voyage v, List<AbstractBoat> boats, long now) {
		if (v.far <= 0 || (now + ship.getId()) % RISK_EVERY >= Corsairs.THINK) {
			return;
		}
		RandomSource random = level.getRandom();
		if (v.far >= PIRATES_FROM && v.rovers < 1 + v.far / ROVER_MORE_EVERY
			&& random.nextFloat() < Math.min(PIRATE_MOST, PIRATE_PER_FAR * v.far)) {
			Ship rover = rover(level, ship, v.far, random);
			if (rover != null) {
				v.rovers++;
				LivingEntity captain = crew.captain != null && level.getEntity(crew.captain) instanceof LivingEntity c ? c : null;
				if (captain != null) {
					Speech.say(level, captain, Component.translatable("message.jaymoskingdoms.whaler.pirates"), Speech.RED, 64.0);
				}
				for (ServerPlayer p : Corsairs.playersAboard(level, ship)) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.pirates_sighted", crew.name,
						rover.crew != null ? rover.crew.name : "").withStyle(ChatFormatting.RED));
				}
			}
		}
		if (v.far >= SHARKS_FROM && !boats.isEmpty() && random.nextFloat() < SHARK_PER_FAR * v.far) {
			AbstractBoat boat = boats.get(random.nextInt(boats.size()));
			LivingEntity prey = boat.getFirstPassenger() instanceof LivingEntity l ? l : null;
			if (Sharks.hunt(level, boat.position(), prey)) {
				for (ServerPlayer p : Raids.players(level, boat.blockPosition(), 48.0)) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.sharks").withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	/** A sea rover heaves into sight off {@code ship}, out where nobody sees her come, and makes for her (null: no room, or no pirates in the world). */
	static @Nullable Ship rover(ServerLevel level, Ship ship, int far, RandomSource random) {
		Faction camp = sponsor(level.getServer(), ship.blockPosition());
		if (camp == null) {
			return null;
		}
		for (int tries = 0; tries < 6; tries++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double d = Mth.nextDouble(random, ROVER_MIN, ROVER_MAX);
			BlockPos column = BlockPos.containing(ship.getX() + Mth.cos(angle) * d, ship.getY(), ship.getZ() + Mth.sin(angle) * d);
			BlockPos water = Fleet.openSea(level, column, 16, 3, random);
			if (water == null || Raids.playerNear(level, water, ROVER_HIDE)) {
				continue;
			}
			boolean galleon = random.nextFloat() < GALLEON + GALLEON_PER_FAR * far;
			Fleet.Rig rig = galleon ? Fleet.Rig.GALLEON : random.nextFloat() < PirateCoves.DRAKKAR_CHANCE ? Fleet.Rig.DRAKKAR : Fleet.Rig.SLOOP;
			// her home's the ground itself: she cruises it, and her chases are leashed to it
			Crew crew = new Crew(Crew.Side.PIRATE, camp.id(), Pirates.pirateShip(random), water);
			Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG,
				Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE));
			float yaw = (float) (Mth.atan2(ship.getZ() - water.getZ(), ship.getX() - water.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			Ship rover = Corsairs.launch(level, rig, look, water, Direction.fromYRot(yaw), crew, camp,
				new Corsairs.Complement(Pirates.CAPTAIN, Pirates.GUNNER, Pirates.DECKHAND,
					galleon ? PirateCoves.BRIG_HANDS : PirateCoves.SLOOP_HANDS, PirateCoves.BOYS));
			if (rover == null) {
				continue;
			}
			Map<String, Long> all = new HashMap<>(level.getAttachedOrElse(ROVERS, Map.of()));
			all.put(rover.getUUID().toString(), level.getGameTime());
			level.setAttached(ROVERS, Map.copyOf(all));
			JaymosKingdoms.LOGGER.info("A sea rover {} ({}, out of {}) heaves into sight of the whaler at {}", crew.name, rig.id(), camp.name(),
				water.toShortString());
			return rover;
		}
		return null;
	}

	/** The pirates a far ground's rovers sail out of: the nearest pirate cove, or failing any, the nearest barbarian camp. */
	private static @Nullable Faction sponsor(MinecraftServer server, BlockPos near) {
		Faction best = null, bestCamp = null;
		double bestD = Double.MAX_VALUE, bestCampD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (f.type() != FactionType.BARBARIAN || f.fallen()) {
				continue;
			}
			double d = f.seat().distSqr(near);
			if (Camps.pirate(server, f.id())) {
				if (d < bestD) {
					best = f;
					bestD = d;
				}
			} else if (d < bestCampD) {
				bestCamp = f;
				bestCampD = d;
			}
		}
		return best != null ? best : bestCamp;
	}

	/** {@code ship} is one of the far grounds' rovers. */
	static boolean rover(ServerLevel level, Ship ship) {
		return Factions.level(level.getServer()).getAttachedOrElse(ROVERS, Map.of()).containsKey(ship.getUUID().toString());
	}

	/** A rover went down: forgotten (her camp's own ship is still its own). */
	static void roverLost(ServerLevel level, Ship ship) {
		ServerLevel home = Factions.level(level.getServer());
		Map<String, Long> all = new HashMap<>(home.getAttachedOrElse(ROVERS, Map.of()));
		if (all.remove(ship.getUUID().toString()) != null) {
			home.setAttached(ROVERS, Map.copyOf(all));
		}
	}

	/** Every so often at her helm: a rover long out with nobody near her is stowed (true: she's gone). */
	static boolean stowIdle(ServerLevel level, Ship ship) {
		long now = level.getGameTime();
		if ((now + ship.getId()) % 200 != 0) {
			return false;
		}
		Long out = Factions.level(level.getServer()).getAttachedOrElse(ROVERS, Map.of()).get(ship.getUUID().toString());
		if (out == null || now - out < ROVER_LIFE || Raids.playerNear(level, ship.blockPosition(), ROVER_NEAR)) {
			return false;
		}
		roverLost(level, ship);
		Navies.stow(ship);
		return true;
	}
}
