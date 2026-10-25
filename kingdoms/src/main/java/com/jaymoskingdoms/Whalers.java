package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.jaymoskingdoms.whaling.Harpoon;
import com.jaymoskingdoms.whaling.WhaleCarcass;
import com.jaymoskingdoms.whaling.Whaling;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.210) Whaling, stage 3: the kingdoms' whaling crews (Andy, 2026-10-25: "Crews can be seen at the task to provide
 * for their kingdoms/bases etc, whaling crews do need an extremely large ship to haul and butcher the whales. It should be
 * a rather long and involved process. And can be observed over the course of days").
 * <p>
 * A realm with a harbour ({@link Navies}) and {@link #TREASURY} emeralds fits out a whaler now and then (one at a time,
 * {@link #GAP} at least between voyages): a big cargo ship in its colours, a sea captain and harpooners. A voyage goes:
 * <ol>
 * <li><b>fitting out</b> at the harbour for {@link #FIT_OUT} ticks; a player who comes aboard then signs on for a share;</li>
 * <li><b>outward</b> to a whaling ground {@link #GROUND_MIN}-{@link #GROUND_MAX} blocks out to sea;</li>
 * <li><b>hunting</b>: she cruises the ground for great whales. Sighting one within {@link #LOWER_RANGE} she heaves to and
 * lowers her whaleboats (Andy: "it'd be hard for the larger ship required for processing to keep up"): {@link #BOATS}
 * rowboats, an oarsman and a harpooner in each, who row the whale down and throw irons from the boat, their lines fast
 * to it, so a running whale drags the boat after it, until it's spent and dies. She follows under easy sail. Her own
 * hands still throw from her deck if it comes near. A ground where none are sighted for a while has a pod come by, out
 * of anyone's sight; after {@link #GIVE_UP} with no luck she gives it up;</li>
 * <li><b>towing</b>: the boats tow the carcass back on their lines while she closes with it; alongside, their lines are
 * passed up and made fast aboard her and the boats are hoisted in, their crews back at their stations;</li>
 * <li><b>cutting in</b>: hove to beside the carcass on her lines, her crew flense it stroke by stroke into her hold
 * (sharks come to the blood as for anyone's); she takes {@link #WHALES} whales;</li>
 * <li><b>homeward</b>, her tryworks smoking the whole way (blubber rendered to oil aboard);</li>
 * <li><b>home</b>: her hold goes into the realm's stores (oil, meat, bone, ambergris), its purse gets the value of the
 * catch, and each hand who signed on and comes home aboard her gets a share.</li>
 * </ol>
 * Ships only sail where a player is near. One out of anyone's reach for {@link #UNSEEN} is taken to have made her voyage
 * unseen: her realm gets a catch, and if she turns up again later she's stowed. Pirates chase her as they do merchantmen,
 * and she runs.
 */
public final class Whalers {
	/** A realm fits out whalers from this treasury; one voyage at most this often (ticks: two days), with this chance each look. */
	static final long TREASURY = 600L, GAP = 48000L;
	static final float CHANCE = 0.4F;
	/** How often (ticks) the realms are seen to. */
	static final int CHECK_TICKS = 400;
	/** Fitting out at the harbour (ticks), the whaling ground's distance out (blocks), and how near it she's arrived. */
	static final long FIT_OUT = 6000L;
	static final int GROUND_MIN = 160, GROUND_MAX = 320, GROUND_CRUISE = 64;
	static final double GROUND_REACH = 24.0, ARRIVE = 22.0;
	/** Hunting: how far her lookout sees a whale, from how near her harpooners throw, and how often (ticks). */
	static final double SIGHT = 96.0, THROW_RANGE = 16.0, CLOSE = 10.0;
	static final int THROW_EVERY = 30;
	/** How long (ticks) with none sighted before a pod comes by (that far off, and that far from any player), and before she gives up. */
	static final long POD_AFTER = 3600L, GIVE_UP = 24000L;
	static final double POD_OFF = 72.0, POD_HIDE = 56.0;
	/** Cutting in: a stroke every this many ticks, the carcass found within this of her. */
	static final int STROKE_EVERY = 24;
	static final double CARCASS_NEAR = 36.0;
	/** Whales to a voyage. */
	static final int WHALES = 2;
	/** Her tryworks: this many blubber to a bottle of oil, every this many ticks. */
	static final int TRY_BLUBBER = 2, TRY_EVERY = 160;
	/** Her whaleboats: lowered for a whale this near (blocks), this many, each pulling this hard (a tick) and turning this fast (degrees a tick). */
	static final double LOWER_RANGE = 72.0;
	static final int BOATS = 2;
	static final float ROW = 0.04F, BOAT_TURN = 5.0F;
	/** A boat's harpooner throws from this near; its oarsman lies on his oars this near the whale; back beside her within this, it's hoisted in. */
	static final double BOAT_THROW = 12.0, BOAT_HOLD_OFF = 7.0, HOIST_REACH = 3.5;
	/** Her boats are found within this of her; a towed carcass this near her side is taken alongside; she follows them under this sail when farther than this. */
	static final double BOAT_RANGE = 160.0, TOW_IN = 8.0, STAND_OFF = 40.0;
	static final float FOLLOW_SAIL = 0.3F;
	/** Her complement besides her captain. */
	static final int HANDS = 5;
	/** A whaler not seen (out of any player's reach) this long has made her voyage unseen; given up for lost after this long out. */
	static final long UNSEEN = 48000L, FORGET = 24000L * 6;
	/** A catch's worth to her realm (emeralds): each bottle of oil, lump of ambergris, bone, and stack of meat. */
	static final int OIL_WORTH = 3, AMBERGRIS_WORTH = 40, BONE_WORTH = 2, MEAT_WORTH = 1;
	/** A hand's lay: this share of the catch's worth (at least {@link #LEAST_LAY}), and bottles of oil. */
	static final float LAY = 0.1F;
	static final int LEAST_LAY = 4, LAY_OIL = 2;
	/** An unseen voyage's catch, per whale: bottles of oil, meat, bone; ambergris's chance. */
	static final int UNSEEN_OIL = 12, UNSEEN_MEAT = 10, UNSEEN_BONE = 4;
	static final float UNSEEN_AMBERGRIS = 0.12F;

	static final String[] NAMES = {"Essex", "Grey Widow", "Pequod's Sister", "Leviathan", "Saltbeard", "Harpooner", "Northern Light",
		"Blubber Queen", "Kraken's Bane", "Tryworks", "Old Spout", "Fair Wind", "Iron Line", "Deepwater", "Spermaceti", "Whalesong"};

	/** A realm's whaler's voyage: her ship, when she put out, when she was last seen, her ground, whales taken, her hold, her signed hands. */
	static final class Voyage {
		UUID ship;
		long launched, seen, huntFrom;
		BlockPos ground;
		int whales;
		final Map<String, Integer> hold = new HashMap<>();
		final List<UUID> hands = new ArrayList<>();

		Voyage(UUID ship, long launched, BlockPos ground) {
			this.ship = ship;
			this.launched = launched;
			this.seen = launched;
			this.huntFrom = launched;
			this.ground = ground;
		}

		int get(String what) {
			return this.hold.getOrDefault(what, 0);
		}

		void add(String what, int n) {
			this.hold.merge(what, n, Integer::sum);
		}

		private record Saved(UUID ship, long launched, long seen, long huntFrom, BlockPos ground, int whales, Map<String, Integer> hold,
			List<UUID> hands) {
		}

		static final Codec<Voyage> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ship").forGetter(Saved::ship),
				Codec.LONG.fieldOf("launched").forGetter(Saved::launched),
				Codec.LONG.fieldOf("seen").forGetter(Saved::seen),
				Codec.LONG.optionalFieldOf("hunt_from", 0L).forGetter(Saved::huntFrom),
				BlockPos.CODEC.fieldOf("ground").forGetter(Saved::ground),
				Codec.INT.optionalFieldOf("whales", 0).forGetter(Saved::whales),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("hold", Map.of()).forGetter(Saved::hold),
				UUIDUtil.CODEC.listOf().optionalFieldOf("hands", List.of()).forGetter(Saved::hands))
			.apply(i, Saved::new)).xmap(s -> {
				Voyage v = new Voyage(s.ship(), s.launched(), s.ground());
				v.seen = s.seen();
				v.huntFrom = s.huntFrom();
				v.whales = s.whales();
				v.hold.putAll(s.hold());
				v.hands.addAll(s.hands());
				return v;
			}, v -> new Saved(v.ship, v.launched, v.seen, v.huntFrom, v.ground, v.whales, Map.copyOf(v.hold), List.copyOf(v.hands)));
	}

	/** Every realm's whaler at sea (realm id → her voyage), and when each last came home; kept on the Overworld. */
	static final AttachmentType<Map<String, Voyage>> VOYAGES = AttachmentRegistry.create(JaymosKingdoms.id("whaling_voyages"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Voyage.CODEC)));
	static final AttachmentType<Map<String, Long>> LAST = AttachmentRegistry.create(JaymosKingdoms.id("whaling_last"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** One of a whaleboat's crew: who, and their station aboard her to go back to. */
	record Hand(UUID who, Vec3 seat) {
		static final Codec<Hand> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("who").forGetter(Hand::who),
				Vec3.CODEC.fieldOf("seat").forGetter(Hand::seat))
			.apply(i, Hand::new));
	}

	/** A whaleboat: her ship, and its crew. Saved on the boat. */
	record Whaleboat(UUID ship, List<Hand> hands) {
		static final Codec<Whaleboat> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ship").forGetter(Whaleboat::ship),
				Hand.CODEC.listOf().fieldOf("hands").forGetter(Whaleboat::hands))
			.apply(i, Whaleboat::new));
	}

	static final AttachmentType<Whaleboat> WHALEBOAT = AttachmentRegistry.create(JaymosKingdoms.id("whaleboat"),
		builder -> builder.persistent(Whaleboat.CODEC));

	/** What a boat is about, from her captain's last look (its rowing is done every tick by {@link #helm}): after a whale, towing, or coming back. */
	private enum Pull { CHASE, TOW, RETURN }

	private record Order(AbstractBoat boat, Entity target, Pull pull) {
	}

	/** Each whaler's boats' orders (ship id → them); not saved, given again at her captain's next look. */
	private static final Map<UUID, List<Order>> ORDERS = new HashMap<>();

	private Whalers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Whalers::tick);
	}

	static @Nullable Voyage voyage(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(VOYAGES, Map.of()).get(Integer.toString(realm));
	}

	/** Saves {@code realm}'s voyage (the same object, changed, or a new one), or clears it. */
	static void put(MinecraftServer server, int realm, @Nullable Voyage voyage) {
		ServerLevel level = Factions.level(server);
		Map<String, Voyage> all = new HashMap<>(level.getAttachedOrElse(VOYAGES, Map.of()));
		if (voyage == null) {
			all.remove(Integer.toString(realm));
			Map<String, Long> last = new HashMap<>(level.getAttachedOrElse(LAST, Map.of()));
			last.put(Integer.toString(realm), level.getGameTime());
			level.setAttached(LAST, Map.copyOf(last));
		} else {
			all.put(Integer.toString(realm), voyage);
		}
		level.setAttached(VOYAGES, Map.copyOf(all));
	}

	// ---- fitting out, and voyages made out of sight -------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Voyage v = voyage(server, f.id());
			if (v != null) {
				if (!(level.getEntity(v.ship) instanceof Ship)) {
					if (now - v.seen > UNSEEN) {
						unseen(level, f, v);
					} else if (now - v.launched > FORGET) {
						put(server, f.id(), null);
					}
				}
				continue;
			}
			Navies.Harbour h = Navies.harbour(server, f.id());
			long last = level.getAttachedOrElse(LAST, Map.of()).getOrDefault(Integer.toString(f.id()), -GAP);
			if (h == null || f.treasury() < TREASURY || now - last < GAP || !level.isPositionEntityTicking(h.mooring())
				|| level.getRandom().nextFloat() > CHANCE) {
				continue;
			}
			fitOut(level, f, h);
		}
	}

	/** {@code f}'s whaler put in at its harbour to fit out for a voyage. */
	static @Nullable Ship fitOut(ServerLevel level, Faction f, Navies.Harbour h) {
		RandomSource random = level.getRandom();
		BlockPos ground = ground(level, h, random);
		if (ground == null) {
			return null;
		}
		Crew crew = new Crew(Crew.Side.WHALER, f.id(), "the " + NAMES[random.nextInt(NAMES.length)], h.mooring());
		crew.mode = "fitting";
		Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.SPRUCE_LOG, Fleet.block(f.color().getName() + "_wool"), null);
		Ship ship = Navies.launch(level, f, h, Fleet.Rig.CARGO_SHIP, look, crew,
			new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, HANDS, 0));
		if (ship == null) {
			return null;
		}
		put(level.getServer(), f.id(), new Voyage(ship.getUUID(), level.getGameTime(), ground));
		Raids.tell(level, h.shore(), Component.translatable("message.jaymoskingdoms.whaler.fitting", crew.name, f.name()).withStyle(ChatFormatting.AQUA));
		JaymosKingdoms.LOGGER.info("{} fits out the whaler {} for a ground at {}", f.name(), crew.name, ground);
		return ship;
	}

	/** Open deep water far out from {@code h}, for a whaling ground (null: none found). */
	private static @Nullable BlockPos ground(ServerLevel level, Navies.Harbour h, RandomSource random) {
		Vec3 out = Vec3.atCenterOf(h.mooring()).subtract(Vec3.atCenterOf(h.shore()));
		float seaward = out.lengthSqr() > 0.01 ? (float) Mth.atan2(out.z, out.x) : random.nextFloat() * Mth.TWO_PI;
		for (int tries = 0; tries < 8; tries++) {
			float angle = seaward + (random.nextFloat() - 0.5F) * 1.6F;
			double d = Mth.nextDouble(random, GROUND_MIN, GROUND_MAX);
			BlockPos at = BlockPos.containing(h.mooring().getX() + Mth.cos(angle) * d, level.getSeaLevel() - 1, h.mooring().getZ() + Mth.sin(angle) * d);
			// (unloaded is fine: it's taken on trust, and she'll cruise for open water when she's there)
			if (!level.isLoaded(at) || level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at.below(8)).is(FluidTags.WATER)) {
				return at;
			}
		}
		return null;
	}

	/** {@code f}'s whaler made her voyage out of anyone's reach: a catch for its stores and purse, and she's forgotten (stowed if seen again). */
	private static void unseen(ServerLevel level, Faction f, Voyage v) {
		RandomSource random = level.getRandom();
		int whales = Math.max(v.whales, 1 + random.nextInt(WHALES));
		v.add("oil", UNSEEN_OIL * whales);
		v.add("meat", UNSEEN_MEAT * whales);
		v.add("bone", UNSEEN_BONE * whales);
		for (int i = 0; i < whales; i++) {
			if (random.nextFloat() < UNSEEN_AMBERGRIS) {
				v.add("ambergris", 1);
			}
		}
		unload(level, f, v);
		put(level.getServer(), f.id(), null);
		JaymosKingdoms.LOGGER.info("{}'s whaler came home unseen with {} whales", f.name(), whales);
	}

	/** {@code v}'s hold into {@code f}'s stores (what won't fit is sold) and the catch's worth into its purse; its worth. */
	private static int unload(ServerLevel level, Faction f, Voyage v) {
		int worth = v.get("oil") * OIL_WORTH + v.get("ambergris") * AMBERGRIS_WORTH + v.get("bone") * BONE_WORTH + v.get("meat") * MEAT_WORTH;
		if (Storage.loaded(level, f)) {
			Storage.put(level, f, Whaling.WHALE_OIL, v.get("oil"));
			Storage.put(level, f, Whaling.WHALE_MEAT, v.get("meat"));
			Storage.put(level, f, Whaling.WHALE_BONE, v.get("bone"));
			Storage.put(level, f, Whaling.WHALE_BLUBBER, v.get("blubber"));
			Storage.put(level, f, Whaling.AMBERGRIS, v.get("ambergris"));
		}
		long gain = worth;
		Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + gain));
		return worth;
	}

	// ---- at sea (her captain's thinking, from Corsairs) --------------------------------------------------------------

	/** The heading and sail her captain wants, or null when he's dealt with her otherwise (stowed, home). */
	static float @Nullable [] think(ServerLevel level, Ship ship, Crew crew) {
		MinecraftServer server = level.getServer();
		Voyage v = voyage(server, crew.faction);
		Faction f = Factions.get(server, crew.faction);
		List<AbstractBoat> boats = boats(level, ship);
		if (v == null || f == null || !v.ship.equals(ship.getUUID())) {
			// (a voyage made unseen, or given up: she's home already)
			stow(ship, boats);
			return null;
		}
		long now = level.getGameTime();
		v.seen = now;
		List<Order> orders = new ArrayList<>();
		float[] plan = switch (crew.mode) {
			case "fitting" -> fitting(level, ship, crew, v, now);
			case "outward" -> outward(level, ship, crew, v, now);
			case "hunting" -> hunting(level, ship, crew, v, now, boats, orders);
			case "cutting" -> cutting(level, ship, crew, v, boats);
			default -> homeward(level, ship, crew, v, f, boats);
		};
		if (plan != null) {
			put(server, crew.faction, v);
			boatwork(level, ship, crew, boats, orders);
		}
		return plan;
	}

	private static float[] hold(Ship ship) {
		return new float[] {ship.getYRot(), 0.0F};
	}

	private static float[] toward(Ship ship, BlockPos to, double sail) {
		return new float[] {Fleet.bearing(ship, to.getX() + 0.5, to.getZ() + 0.5), (float) sail};
	}

	/** At the harbour: players aboard sign on; then she sails for her ground. */
	private static float[] fitting(ServerLevel level, Ship ship, Crew crew, Voyage v, long now) {
		for (ServerPlayer p : Corsairs.playersAboard(level, ship)) {
			if (!v.hands.contains(p.getUUID())) {
				v.hands.add(p.getUUID());
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.signed", crew.name).withStyle(ChatFormatting.AQUA));
				LivingEntity captain = captain(level, ship, crew);
				if (captain != null) {
					Speech.say(level, captain, Component.translatable("message.jaymoskingdoms.whaler.welcome"), Speech.YELLOW, 16.0);
				}
			}
		}
		if (now - v.launched < FIT_OUT) {
			return hold(ship);
		}
		crew.mode = "outward";
		crew.bound = v.ground;
		LivingEntity captain = captain(level, ship, crew);
		if (captain != null) {
			Speech.say(level, captain, Component.translatable("message.jaymoskingdoms.whaler.sails"), Speech.YELLOW, 32.0);
		}
		return toward(ship, v.ground, 1.0);
	}

	private static float[] outward(ServerLevel level, Ship ship, Crew crew, Voyage v, long now) {
		if (Math.sqrt(Raids.horizontal(ship.blockPosition(), v.ground)) < GROUND_REACH) {
			crew.mode = "hunting";
			crew.bound = null;
			v.huntFrom = now;
		}
		GreatWhale whale = sighted(level, ship);
		if (whale != null) {
			// (one sighted on the way: hunt it)
			crew.mode = "hunting";
			crew.bound = null;
			v.huntFrom = now;
		}
		return toward(ship, v.ground, 1.0);
	}

	/** Cruising the ground for whales; sighting one, her boats lowered after it (or she chases it herself), her harpooners throwing in range; her boats towing their catch back. */
	private static float[] hunting(ServerLevel level, Ship ship, Crew crew, Voyage v, long now, List<AbstractBoat> boats, List<Order> orders) {
		WhaleCarcass carcass = ours(level, ship, boats);
		if (carcass != null) {
			if (!fastTo(level, carcass, ship) && !boats.isEmpty()) {
				// her boats tow it back on their lines while she closes with it
				if (ship.getBoundingBox().inflate(TOW_IN, 4.0, TOW_IN).intersects(carcass.getBoundingBox())) {
					passLines(level, ship, carcass);
					crew.mode = "cutting";
					return hold(ship);
				}
				for (AbstractBoat boat : boats) {
					orders.add(new Order(boat, ship, Pull.TOW));
				}
				return ship.distanceTo(carcass) > ship.getBbWidth() * 0.5 + TOW_IN
					? new float[] {Fleet.bearing(ship, carcass.getX(), carcass.getZ()), FOLLOW_SAIL} : hold(ship);
			}
			crew.mode = "cutting";
			return hold(ship);
		}
		GreatWhale whale = sighted(level, ship);
		if (whale == null && !boats.isEmpty()) {
			// lost it: hove to for her boats to come back
			return hold(ship);
		}
		if (whale == null) {
			if (now - v.huntFrom > GIVE_UP) {
				crew.mode = "homeward";
				crew.bound = crew.home;
				return toward(ship, crew.home, 1.0);
			}
			if ((now - v.huntFrom) % POD_AFTER < Corsairs.THINK && now - v.huntFrom >= POD_AFTER) {
				pod(level, ship);
			}
			if (crew.waypoint == null || Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.waypoint)) < GROUND_REACH) {
				BlockPos next = Fleet.openSea(level, v.ground, GROUND_CRUISE, 6, level.getRandom());
				crew.waypoint = next != null ? next : v.ground;
			}
			return toward(ship, crew.waypoint, 0.6);
		}
		v.huntFrom = now;
		double d = ship.distanceTo(whale);
		if (d < THROW_RANGE + ship.getBbWidth() * 0.5 && now % THROW_EVERY < Corsairs.THINK) {
			LivingEntity harpooner = harpooner(level, ship, crew, whale);
			if (harpooner != null) {
				Harpoon.thrownAt(level, harpooner, whale);
			}
		}
		if (boats.isEmpty() && d < LOWER_RANGE) {
			boats.addAll(lower(level, ship, crew, whale));
		}
		if (!boats.isEmpty()) {
			for (AbstractBoat boat : boats) {
				orders.add(new Order(boat, whale, Pull.CHASE));
				LivingEntity harpooner = boatHarpooner(boat);
				if (harpooner != null && boat.distanceTo(whale) < BOAT_THROW + whale.getBbWidth() * 0.5
					&& (now + boat.getId() * 7L) % THROW_EVERY < Corsairs.THINK) {
					Harpoon.thrownAt(level, harpooner, whale);
				}
			}
			// she follows her boats under easy sail
			return d > STAND_OFF ? new float[] {Fleet.bearing(ship, whale.getX(), whale.getZ()), FOLLOW_SAIL} : hold(ship);
		}
		Vec3 lead = whale.position().add(whale.getDeltaMovement().scale(30.0));
		double sail = d < CLOSE + ship.getBbWidth() * 0.5 ? 0.2 : d < 40.0 ? 0.6 : 1.0;
		return new float[] {Fleet.bearing(ship, lead.x, lead.z), (float) sail};
	}

	/** Hove to beside her catch, her crew cutting it in. */
	private static float[] cutting(ServerLevel level, Ship ship, Crew crew, Voyage v, List<AbstractBoat> boats) {
		WhaleCarcass carcass = ours(level, ship, boats);
		if (carcass == null) {
			// stripped and sunk (or lost)
			v.whales++;
			v.huntFrom = level.getGameTime();
			crew.mode = v.whales >= WHALES ? "homeward" : "hunting";
			crew.bound = v.whales >= WHALES ? crew.home : null;
			return hold(ship);
		}
		if (level.getGameTime() % STROKE_EVERY < Corsairs.THINK) {
			for (ItemStack cut : carcass.cutByCrew(level)) {
				v.add(key(cut.getItem()), cut.getCount());
			}
			LivingEntity hand = harpooner(level, ship, crew, carcass);
			if (hand != null) {
				hand.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
		}
		return hold(ship);
	}

	private static float @Nullable [] homeward(ServerLevel level, Ship ship, Crew crew, Voyage v, Faction f, List<AbstractBoat> boats) {
		crew.bound = crew.home;
		if (!boats.isEmpty()) {
			// not without her boats
			return hold(ship);
		}
		if (Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.home)) < ARRIVE) {
			home(level, ship, crew, v, f);
			return null;
		}
		return toward(ship, crew.home, 1.0);
	}

	/** Home: her hold into the stores, her catch's worth into the purse, her hands paid their lay; she's laid up (gone). */
	private static void home(ServerLevel level, Ship ship, Crew crew, Voyage v, Faction f) {
		// (what's still blubber is rendered at the harbour)
		v.add("oil", v.get("blubber") / TRY_BLUBBER);
		v.hold.remove("blubber");
		int worth = unload(level, f, v);
		List<ServerPlayer> aboard = Corsairs.playersAboard(level, ship);
		for (ServerPlayer p : aboard) {
			if (v.hands.contains(p.getUUID())) {
				int lay = Math.max(LEAST_LAY, Math.round(worth * LAY));
				give(p, new ItemStack(Items.EMERALD, lay));
				give(p, new ItemStack(Whaling.WHALE_OIL, LAY_OIL));
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.lay", lay, crew.name).withStyle(ChatFormatting.GOLD));
			}
		}
		Raids.tell(level, ship.blockPosition(), Component.translatable("message.jaymoskingdoms.whaler.home", crew.name, v.whales, v.get("oil"))
			.withStyle(ChatFormatting.AQUA));
		JaymosKingdoms.LOGGER.info("{}'s whaler {} came home with {} whales ({} emeralds)", f.name(), crew.name, v.whales, worth);
		put(level.getServer(), f.id(), null);
		stow(ship, List.of());
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.addItem(stack)) {
			p.spawnAtLocation(p.level(), stack);
		}
	}

	/** Every tick she's sailed (from Corsairs): her boats rowed, and her tryworks. */
	static void helm(ServerLevel level, Ship ship, Crew crew) {
		row(level, ship);
		tryworks(level, ship, crew);
	}

	/** Her tryworks at sea: blubber in her hold rendered to oil, its smoke going up from her deck. */
	private static void tryworks(ServerLevel level, Ship ship, Crew crew) {
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		Voyage v = voyage(level.getServer(), crew.faction);
		if (v == null || !v.ship.equals(ship.getUUID()) || v.get("blubber") < TRY_BLUBBER && !"cutting".equals(crew.mode)) {
			return;
		}
		Hull hull = ship.hull();
		Vec3 at = ship.toWorld(0.0, hull == null ? 3.0 : hull.maxY + 1.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y, at.z, 1, 0.3, 0.2, 0.3, 0.01);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 3, 0.4, 0.2, 0.4, 0.02);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.6, at.z, 2, 0.3, 0.1, 0.3, 0.0);
		if (level.getGameTime() % TRY_EVERY == 0 && v.get("blubber") >= TRY_BLUBBER) {
			v.add("blubber", -TRY_BLUBBER);
			v.add("oil", 1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.NEUTRAL, 0.8F, 0.7F);
			put(level.getServer(), crew.faction, v);
		}
	}

	/** Her whaler was sunk: the voyage is over. */
	static void lost(ServerLevel level, Crew crew) {
		Voyage v = voyage(level.getServer(), crew.faction);
		if (v != null) {
			put(level.getServer(), crew.faction, null);
		}
	}

	// ---- what she sees ------------------------------------------------------------------------------------------------

	private static @Nullable GreatWhale sighted(ServerLevel level, Ship ship) {
		GreatWhale best = null;
		double bestD = SIGHT * SIGHT;
		for (GreatWhale w : level.getEntitiesOfClass(GreatWhale.class, ship.getBoundingBox().inflate(SIGHT), GreatWhale::isAlive)) {
			double d = w.distanceToSqr(ship);
			if (d < bestD) {
				best = w;
				bestD = d;
			}
		}
		return best;
	}

	/** A carcass on her lines or her boats', or failing that the nearest one by her. */
	private static @Nullable WhaleCarcass ours(ServerLevel level, Ship ship, List<AbstractBoat> boats) {
		WhaleCarcass near = null;
		double bestD = CARCASS_NEAR * CARCASS_NEAR;
		double range = boats.isEmpty() ? CARCASS_NEAR : BOAT_RANGE;
		for (WhaleCarcass c : level.getEntitiesOfClass(WhaleCarcass.class, ship.getBoundingBox().inflate(range), c -> c.isAlive() && c.left() > 0.0F)) {
			for (Harpoon h : lines(level, c)) {
				Entity a = h.anchor();
				if (a == ship || a instanceof AbstractBoat boat && boats.contains(boat)) {
					return c;
				}
			}
			double d = c.distanceToSqr(ship);
			if (d < bestD) {
				near = c;
				bestD = d;
			}
		}
		return near;
	}

	/** A pod comes by, out where nobody sees them arrive. */
	private static void pod(ServerLevel level, Ship ship) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 6; tries++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			BlockPos at = BlockPos.containing(ship.getX() + Mth.cos(angle) * POD_OFF, level.getSeaLevel() - 8, ship.getZ() + Mth.sin(angle) * POD_OFF);
			if (level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at.below(3)).is(FluidTags.WATER)
				&& !Raids.playerNear(level, at, POD_HIDE)) {
				int n = 1 + random.nextInt(2);
				for (int i = 0; i < n; i++) {
					Whaling.GREAT_WHALE.spawn(level, at.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), EntitySpawnReason.EVENT);
				}
				return;
			}
		}
	}

	private static List<Harpoon> lines(ServerLevel level, Entity in) {
		return level.getEntitiesOfClass(Harpoon.class, in.getBoundingBox().inflate(8.0), h -> h.lodged() == in);
	}

	/** Whether {@code c} is on her own lines. */
	private static boolean fastTo(ServerLevel level, WhaleCarcass c, Ship ship) {
		for (Harpoon h : lines(level, c)) {
			if (h.anchor() == ship) {
				return true;
			}
		}
		return false;
	}

	// ---- her whaleboats -----------------------------------------------------------------------------------------------

	/** Her whaleboats in the water. */
	private static List<AbstractBoat> boats(ServerLevel level, Ship ship) {
		UUID id = ship.getUUID();
		return new ArrayList<>(level.getEntitiesOfClass(AbstractBoat.class, ship.getBoundingBox().inflate(BOAT_RANGE), b -> b.isAlive()
			&& b.getAttached(WHALEBOAT) instanceof Whaleboat wb && wb.ship().equals(id)));
	}

	/** A boat's harpooner (its last hand in, forward of its oarsman). */
	private static @Nullable LivingEntity boatHarpooner(AbstractBoat boat) {
		List<Entity> in = boat.getPassengers();
		for (int i = in.size() - 1; i >= 0; i--) {
			if (in.get(i) instanceof LivingEntity hand && !(hand instanceof Player) && hand.isAlive() && !Downed.isDowned(hand)) {
				return hand;
			}
		}
		return null;
	}

	/** Where a boat at {@code from} comes back beside her: off the nearer side, abreast of it, at the waterline. */
	private static Vec3 alongside(Ship ship, Hull h, Vec3 from) {
		Vec3 local = ship.toLocal(from);
		double x = local.x >= 0.0 ? h.maxX + 2.5 : h.minX - 2.5;
		double z = Mth.clamp(local.z, h.minZ + 2.0, h.maxZ - 2.0);
		return ship.toWorld(x, h.draft, z);
	}

	/** Heaves to and lowers her boats on the side toward {@code whale}, two hands into each; the boats lowered. */
	private static List<AbstractBoat> lower(ServerLevel level, Ship ship, Crew crew, Entity whale) {
		List<AbstractBoat> lowered = new ArrayList<>();
		Hull h = ship.hull();
		if (h == null) {
			return lowered;
		}
		List<LivingEntity> hands = new ArrayList<>();
		for (LivingEntity hand : ship.crewAboard()) {
			if (!hand.getUUID().equals(crew.captain) && !Downed.isDowned(hand) && hand.hasAttached(Ship.SEAT)) {
				hands.add(hand);
			}
		}
		// (at least one left aboard her besides her captain)
		int perBoat = 2, boatsMan = Math.min(BOATS, (hands.size() - 1) / perBoat);
		double side = ship.toLocal(whale.position()).x >= 0.0 ? 1.0 : -1.0;
		for (int b = 0; b < boatsMan; b++) {
			double z = b == 0 ? 6.0 : -6.0;
			Vec3 at = null;
			for (double s : new double[] {side, -side}) {
				Vec3 spot = ship.toWorld(s > 0.0 ? h.maxX + 2.5 : h.minX - 2.5, h.draft, z);
				BlockPos p = BlockPos.containing(spot);
				if (level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.below()).is(FluidTags.WATER)) {
					at = spot;
					break;
				}
			}
			AbstractBoat boat = at == null ? null : EntityTypes.SPRUCE_BOAT.create(level, EntitySpawnReason.EVENT);
			if (boat == null) {
				continue;
			}
			boat.snapTo(at.x, at.y, at.z, Fleet.bearing(ship, whale.getX(), whale.getZ()), 0.0F);
			if (!level.addFreshEntity(boat)) {
				continue;
			}
			List<Hand> in = new ArrayList<>();
			for (int i = 0; i < perBoat; i++) {
				LivingEntity hand = hands.remove(0);
				Vec3 seat = hand.getAttached(Ship.SEAT);
				hand.stopRiding();
				crew.walks.remove(hand.getUUID());
				hand.snapTo(at.x, at.y + 0.5, at.z, boat.getYRot(), 0.0F);
				if (hand.startRiding(boat, true, false)) {
					in.add(new Hand(hand.getUUID(), seat));
				} else if (!ship.takeAboard(hand, seat)) {
					hand.discard();
				}
			}
			boat.setAttached(WHALEBOAT, new Whaleboat(ship.getUUID(), List.copyOf(in)));
			level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 24, 1.0, 0.2, 1.0, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.0F, 0.9F);
			lowered.add(boat);
		}
		if (!lowered.isEmpty()) {
			LivingEntity captain = captain(level, ship, crew);
			if (captain != null) {
				Speech.say(level, captain, Component.translatable("message.jaymoskingdoms.whaler.lower"), Speech.YELLOW, 48.0);
			}
		}
		return lowered;
	}

	/** Her boats' towlines, alongside her now, passed up and made fast aboard her. */
	private static void passLines(ServerLevel level, Ship ship, WhaleCarcass carcass) {
		Hull h = ship.hull();
		if (h == null) {
			return;
		}
		Vec3 local = ship.toLocal(carcass.position());
		Vec3 knot = new Vec3(local.x >= 0.0 ? h.maxX : h.minX, h.draft + 3.0, Mth.clamp(local.z, h.minZ + 1.0, h.maxZ - 1.0));
		for (Harpoon line : lines(level, carcass)) {
			if (line.anchor() instanceof AbstractBoat boat && boat.hasAttached(WHALEBOAT)) {
				line.passTo(ship, knot);
			}
		}
		level.playSound(null, carcass.getX(), carcass.getY(), carcass.getZ(), SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 1.0F, 0.8F);
	}

	/** Her boats given their orders (those with none come back to her), and those back beside her hoisted in. */
	private static void boatwork(ServerLevel level, Ship ship, Crew crew, List<AbstractBoat> boats, List<Order> orders) {
		Hull h = ship.hull();
		List<Order> all = new ArrayList<>(orders);
		for (AbstractBoat boat : boats) {
			if (orders.stream().anyMatch(o -> o.boat() == boat)) {
				continue;
			}
			if (boatHarpooner(boat) == null) {
				// no one left to pull her: cast adrift
				boat.removeAttached(WHALEBOAT);
				continue;
			}
			if (h != null && Math.sqrt(horizontal(boat.position(), alongside(ship, h, boat.position()))) < HOIST_REACH) {
				hoist(level, ship, crew, boat);
				continue;
			}
			all.add(new Order(boat, ship, Pull.RETURN));
		}
		if (all.isEmpty()) {
			ORDERS.remove(ship.getUUID());
		} else {
			ORDERS.put(ship.getUUID(), all);
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** A boat hoisted in: its crew back at their stations aboard her (anyone else in it is set down beside her). */
	private static void hoist(ServerLevel level, Ship ship, Crew crew, AbstractBoat boat) {
		Whaleboat wb = boat.getAttached(WHALEBOAT);
		LivingEntity captain = captain(level, ship, crew);
		Vec3 spare = captain != null && captain.getAttached(Ship.SEAT) instanceof Vec3 s ? s.add(1.0, 0.0, 0.0) : null;
		for (Entity e : List.copyOf(boat.getPassengers())) {
			e.stopRiding();
			if (e instanceof Player || !(e instanceof LivingEntity hand)) {
				continue;
			}
			Vec3 seat = spare;
			if (wb != null) {
				for (Hand hd : wb.hands()) {
					if (hd.who().equals(hand.getUUID())) {
						seat = hd.seat();
					}
				}
			}
			if (seat == null || !ship.takeAboard(hand, seat)) {
				hand.discard();
			}
		}
		level.playSound(null, boat.getX(), boat.getY(), boat.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		boat.discard();
	}

	/** She's laid up: her crew, her boats and theirs, and she, gone. */
	private static void stow(Ship ship, List<AbstractBoat> boats) {
		for (AbstractBoat boat : boats) {
			for (Entity e : List.copyOf(boat.getPassengers())) {
				if (!(e instanceof Player)) {
					e.discard();
				}
			}
			boat.ejectPassengers();
			boat.discard();
		}
		ORDERS.remove(ship.getUUID());
		Navies.stow(ship);
	}

	/** Her boats rowed, each tick, as her captain last ordered. */
	private static void row(ServerLevel level, Ship ship) {
		List<Order> orders = ORDERS.get(ship.getUUID());
		Hull h = ship.hull();
		if (orders == null || h == null) {
			return;
		}
		for (Order o : orders) {
			AbstractBoat boat = o.boat();
			if (boat.isRemoved() || o.target().isRemoved() || boatHarpooner(boat) == null) {
				continue;
			}
			Vec3 to = o.pull() == Pull.CHASE ? o.target().position() : alongside(ship, h, boat.position());
			double d = Math.sqrt(horizontal(boat.position(), to));
			boolean pull = o.pull() == Pull.CHASE ? d - o.target().getBbWidth() * 0.5 > BOAT_HOLD_OFF : o.pull() == Pull.TOW || d > 1.5;
			steer(boat, to, pull);
		}
	}

	/** A boat's oars: its head brought round toward {@code to}, and pulling for it if {@code pull}. */
	private static void steer(AbstractBoat boat, Vec3 to, boolean pull) {
		float want = (float) (Mth.atan2(-(to.x - boat.getX()), to.z - boat.getZ()) * Mth.RAD_TO_DEG);
		float off = Mth.wrapDegrees(want - boat.getYRot());
		boat.setYRot(boat.getYRot() + Mth.clamp(off, -BOAT_TURN, BOAT_TURN));
		boolean ahead = pull && Math.abs(off) < 70.0F;
		if (ahead) {
			float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
			boat.setDeltaMovement(boat.getDeltaMovement().add(-Mth.sin(yaw) * ROW, 0.0, Mth.cos(yaw) * ROW));
		}
		boat.setPaddleState(ahead || pull && off > 2.0F, ahead || pull && off < -2.0F);
	}

	private static @Nullable LivingEntity captain(ServerLevel level, Ship ship, Crew crew) {
		return crew.captain != null && level.getEntity(crew.captain) instanceof LivingEntity c && c.isAlive() ? c : null;
	}

	/** The hand aboard nearest {@code at}, not her captain. */
	private static @Nullable LivingEntity harpooner(ServerLevel level, Ship ship, Crew crew, net.minecraft.world.entity.Entity at) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity hand : ship.crewAboard()) {
			if (!hand.isAlive() || hand.getUUID().equals(crew.captain)) {
				continue;
			}
			double d = hand.distanceToSqr(at);
			if (d < bestD) {
				best = hand;
				bestD = d;
			}
		}
		return best;
	}

	private static String key(Item item) {
		return item == Whaling.WHALE_BLUBBER ? "blubber" : item == Whaling.WHALE_MEAT ? "meat" : item == Whaling.WHALE_BONE ? "bone"
			: item == Whaling.AMBERGRIS ? "ambergris" : "oil";
	}
}
