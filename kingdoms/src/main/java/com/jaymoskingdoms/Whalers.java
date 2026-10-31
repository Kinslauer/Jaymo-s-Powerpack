package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Deck;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.jaymoskingdoms.whaling.Harpoon;
import com.jaymoskingdoms.whaling.TryworksBlock;
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
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
	/**
	 * (0.7.307) Cutting in (Andy: "the boat should weigh anchor when the whale is attached, then the crew should start
	 * filling the tryworks. and they burn as we either return home or search for another"): the carcass lashed this far
	 * off her side (blocks, past its half width), her anchor renewed for this long (ticks) while she lies to it; her
	 * cutting-in gang this many hands, walking this fast (blocks a tick), carrying this much blubber at a time to the
	 * tryworks, and this long (ticks) at the pots pitching it in.
	 */
	static final double LASH_GAP = 0.6;
	static final int ANCHOR_HOLD = 20;
	static final int GANG = 3, CARRY = 4, AT_WORK = 12;
	static final double GANG_PACE = 0.09;
	/** (0.7.307) Where her tryworks go on her deck if she has none: this share of her length forward of her middle. */
	static final double WORKS_FORWARD = 0.25;
	/** Her complement besides her captain. */
	static final int HANDS = 5;
	/** A whaler not seen (out of any player's reach) this long has made her voyage unseen; given up for lost after this long out. */
	static final long UNSEEN = 48000L, FORGET = 24000L * 6;
	/** A catch's worth to her realm (emeralds): each bottle of oil, lump of ambergris, bone, and stack of meat. */
	static final int OIL_WORTH = 3, AMBERGRIS_WORTH = 40, BONE_WORTH = 2, MEAT_WORTH = 1;
	/** A hand's lay: this share of the catch's worth (at least {@link #LEAST_LAY}), and bottles of oil. */
	static final float LAY = 0.1F;
	static final int LEAST_LAY = 4, LAY_OIL = 2;
	/**
	 * (0.7.270) A player's berth aboard ({@link Voyage#berths}): a harpooner (in the boats with the irons), a deckhand,
	 * or a powder monkey (the ship's boy); and the share of a hand's lay each gets (a harpooner's is the bigger).
	 */
	static final String HARPOONER = "harpooner", DECKHAND = "deckhand", MONKEY = "monkey";
	static final float HARPOONER_LAY = 1.5F, MONKEY_LAY = 0.6F;
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
		/** (0.7.437) How many steps out her ground lies ({@link FarGrounds}), and the sea rovers that have come after her there. */
		int far, rovers;
		final Map<String, Integer> hold = new HashMap<>();
		final List<UUID> hands = new ArrayList<>();
		/** (0.7.270) Each signed player's berth ({@link #HARPOONER}, {@link #DECKHAND}, {@link #MONKEY}). */
		final Map<UUID, String> berths = new HashMap<>();

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
			List<UUID> hands, Map<String, String> berths, int far, int rovers) {
		}

		static final Codec<Voyage> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ship").forGetter(Saved::ship),
				Codec.LONG.fieldOf("launched").forGetter(Saved::launched),
				Codec.LONG.fieldOf("seen").forGetter(Saved::seen),
				Codec.LONG.optionalFieldOf("hunt_from", 0L).forGetter(Saved::huntFrom),
				BlockPos.CODEC.fieldOf("ground").forGetter(Saved::ground),
				Codec.INT.optionalFieldOf("whales", 0).forGetter(Saved::whales),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("hold", Map.of()).forGetter(Saved::hold),
				UUIDUtil.CODEC.listOf().optionalFieldOf("hands", List.of()).forGetter(Saved::hands),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("berths", Map.of()).forGetter(Saved::berths),
				Codec.INT.optionalFieldOf("far", 0).forGetter(Saved::far),
				Codec.INT.optionalFieldOf("rovers", 0).forGetter(Saved::rovers))
			.apply(i, Saved::new)).xmap(s -> {
				Voyage v = new Voyage(s.ship(), s.launched(), s.ground());
				v.seen = s.seen();
				v.huntFrom = s.huntFrom();
				v.whales = s.whales();
				v.hold.putAll(s.hold());
				v.hands.addAll(s.hands());
				s.berths().forEach((who, berth) -> v.berths.put(UUID.fromString(who), berth));
				v.far = s.far();
				v.rovers = s.rovers();
				return v;
			}, v -> {
				Map<String, String> berths = new HashMap<>();
				v.berths.forEach((who, berth) -> berths.put(who.toString(), berth));
				return new Saved(v.ship, v.launched, v.seen, v.huntFrom, v.ground, v.whales, Map.copyOf(v.hold), List.copyOf(v.hands), berths, v.far, v.rovers);
			});
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

	/**
	 * (0.7.307) A whaleboat her crew are pulling, sent to every game: nobody else steers it, not even a player who gets
	 * in (Andy: "When you join on a harpoon boat the ai should retain control, all you are doing is helping throw
	 * harpoons"; {@code BoatCrewedMixin}).
	 */
	public static final AttachmentType<Boolean> CREWED = AttachmentRegistry.create(JaymosKingdoms.id("whaleboat_crewed"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.351) A whaleboat, sent to every game and kept for good, so the client draws her as a launch (Blockbench
	 * Workshop "Launch", CC BY 4.0: the client's {@code WhaleboatLookMixin}) instead of a spruce boat, even once cast adrift.
	 */
	public static final AttachmentType<Boolean> LAUNCH = AttachmentRegistry.create(JaymosKingdoms.id("whaleboat_launch"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** What a boat is about, from her captain's last look (its rowing is done every tick by {@link #helm}): after a whale, towing, or coming back. */
	private enum Pull { CHASE, TOW, RETURN }

	private record Order(AbstractBoat boat, Entity target, Pull pull) {
	}

	/** (0.7.307) A whaler cutting in: her carcass, the side it's lashed on, the blubber cut and waiting at her rail, and her gang at work. */
	private static final class Cutting {
		final UUID carcass;
		final int side;
		int waiting;
		final Map<UUID, Job> gang = new HashMap<>();

		Cutting(UUID carcass, int side) {
			this.carcass = carcass;
			this.side = side;
		}
	}

	/** (0.7.307) One of her gang: the cutter (at the rail with the spade) or a carrier; bound for the tryworks or not, the blubber in their arms, when they're done at the pots, what they held before. */
	private static final class Job {
		boolean cutter, toWorks;
		int carrying;
		long until;
		ItemStack was = ItemStack.EMPTY;
	}

	/** (0.7.307) Each whaler cutting in (ship id → it); not saved, taken up again at her captain's next look. */
	private static final Map<UUID, Cutting> CUTTING = new HashMap<>();
	/** (0.7.307) Each hull's tryworks piece (-1: none). */
	private static final Map<Hull, Integer> WORKS = new java.util.WeakHashMap<>();

	/** Each whaler's boats' orders (ship id → them); not saved, given again at her captain's next look. */
	private static final Map<UUID, List<Order>> ORDERS = new HashMap<>();

	private Whalers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Whalers::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				reboard(newPlayer);
			}
		});
	}

	/**
	 * (0.7.253, Andy: "it should put me back on the boat") A signed hand who died is back on her deck, if she's still
	 * within reach (else they wake at her harbour, their story's spawn).
	 */
	private static void reboard(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		for (Voyage v : level.getAttachedOrElse(VOYAGES, Map.of()).values()) {
			if (v.hands.contains(player.getUUID()) && level.getEntity(v.ship) instanceof Ship ship && !ship.isRemoved()) {
				Vec3 deck = ship.helmStand().add(0.0, 0.1, 0.0);
				player.teleportTo(level, deck.x, deck.y, deck.z, java.util.Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
				player.resetFallDistance();
				return;
			}
		}
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
						FarGrounds.lost(server, f.id());
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
		// (0.7.437) the further out the more voyages it's had home with whales
		int far = FarGrounds.far(level.getServer(), f.id());
		BlockPos ground = ground(level, h, random, far);
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
		// (0.7.437) her far ground's rovers need a cove to sail out of: founded now if there's none
		FarGrounds.coveFor(level, h, far);
		Voyage voyage = new Voyage(ship.getUUID(), level.getGameTime(), ground);
		voyage.far = far;
		put(level.getServer(), f.id(), voyage);
		Raids.tell(level, h.shore(), Component.translatable("message.jaymoskingdoms.whaler.fitting", crew.name, f.name()).withStyle(ChatFormatting.AQUA));
		JaymosKingdoms.LOGGER.info("{} fits out the whaler {} for a ground at {}", f.name(), crew.name, ground);
		return ship;
	}

	/** {@code f} has a harbour, and its whaler is in or fitting out, not away on a voyage (a whaling story can sign on). */
	static boolean free(MinecraftServer server, Faction f) {
		if (Navies.harbour(server, f.id()) == null) {
			return false;
		}
		Voyage v = voyage(server, f.id());
		return v == null || Factions.level(server).getGameTime() - v.launched < FIT_OUT;
	}

	/**
	 * (0.7.235) {@code f}'s whaler afloat at harbour {@code h} for a whaling story to sign on to: the one fitting out, or
	 * one fitted out now. Her sea must be loaded and ticking round the mooring, or she won't float.
	 */
	static boolean ready(ServerLevel level, Faction f, Navies.Harbour h) {
		Voyage v = voyage(level.getServer(), f.id());
		if (v != null && level.getGameTime() - v.launched >= FIT_OUT) {
			return false;
		}
		if (v != null && level.getEntity(v.ship) instanceof Ship s && !s.isRemoved()) {
			return true;
		}
		return fitOut(level, f, h) != null;
	}

	/**
	 * (0.7.228) A life story's whaling start (Andy: "start with whalers, on the crew as a harpooner"): {@code player} is
	 * signed on as a harpooner aboard the whaler fitting out at the harbour nearest {@code near}, one fitted out for them
	 * if no realm's is in port (whatever its treasury or the time since its last voyage). Returns where they stand on her
	 * deck, or null (no realm has a harbour, or she couldn't be fitted out).
	 */
	static @Nullable Vec3 signOn(ServerPlayer player, BlockPos near) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		Faction best = null;
		Navies.Harbour bestHarbour = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			Navies.Harbour h = f.type().isRealm() && !f.fallen() ? Navies.harbour(server, f.id()) : null;
			Voyage v = h == null ? null : voyage(server, f.id());
			// a realm with a voyage already out at sea can't fit out another
			if (h == null || v != null && now - v.launched >= FIT_OUT) {
				continue;
			}
			// (0.7.235) the realm whose whaler was made ready for their story, before any nearer
			double d = f.id() == StoryGround.whalerRealm(player) ? -1.0 : h.mooring().distSqr(near);
			if (d < bestD) {
				bestD = d;
				best = f;
				bestHarbour = h;
			}
		}
		if (best == null) {
			return null;
		}
		level.getChunk(bestHarbour.mooring().getX() >> 4, bestHarbour.mooring().getZ() >> 4);
		Voyage v = voyage(server, best.id());
		Ship ship = v != null && level.getEntity(v.ship) instanceof Ship s && !s.isRemoved() ? s : null;
		if (ship == null) {
			ship = fitOut(level, best, bestHarbour);
			v = voyage(server, best.id());
		}
		if (ship == null || v == null) {
			return null;
		}
		if (!v.hands.contains(player.getUUID())) {
			v.hands.add(player.getUUID());
		}
		// (0.7.270) a whaling story signs them on as her harpooner
		v.berths.put(player.getUUID(), HARPOONER);
		put(server, best.id(), v);
		// (0.7.253) their story's spawn: her harbour, should she be out of reach when they die
		LifeStory.home(player, level, bestHarbour.shore());
		String name = ship.crew != null ? ship.crew.name : best.name();
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.whalers", name).withStyle(ChatFormatting.AQUA));
		return ship.helmStand().add(0.0, 0.1, 0.0);
	}

	/** Open deep water far out from {@code h}, for a whaling ground (null: none found). */
	private static @Nullable BlockPos ground(ServerLevel level, Navies.Harbour h, RandomSource random, int far) {
		Vec3 out = Vec3.atCenterOf(h.mooring()).subtract(Vec3.atCenterOf(h.shore()));
		float seaward = out.lengthSqr() > 0.01 ? (float) Mth.atan2(out.z, out.x) : random.nextFloat() * Mth.TWO_PI;
		for (int tries = 0; tries < 8; tries++) {
			float angle = seaward + (random.nextFloat() - 0.5F) * 1.6F;
			double d = Mth.nextDouble(random, GROUND_MIN, GROUND_MAX) + far * FarGrounds.STEP;
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
		if (FarGrounds.lostUnseen(v, random)) {
			// (0.7.437) the far grounds took her: never heard of again
			put(level.getServer(), f.id(), null);
			FarGrounds.lost(level.getServer(), f.id());
			Navies.Harbour h = Navies.harbour(level.getServer(), f.id());
			if (h != null) {
				Raids.tell(level, h.shore(), Component.translatable("message.jaymoskingdoms.whaler.never_home", f.name()).withStyle(ChatFormatting.GRAY));
			}
			JaymosKingdoms.LOGGER.info("{}'s whaler was lost unseen {} steps out", f.name(), v.far);
			return;
		}
		int whales = Math.max(v.whales, 1 + random.nextInt(WHALES));
		v.add("oil", UNSEEN_OIL * whales);
		v.add("meat", UNSEEN_MEAT * whales);
		v.add("bone", UNSEEN_BONE * whales);
		for (int i = 0; i < whales; i++) {
			if (random.nextFloat() < UNSEEN_AMBERGRIS) {
				v.add("ambergris", 1);
			}
		}
		v.whales = whales;
		FarGrounds.richer(v, random);
		unload(level, f, v);
		put(level.getServer(), f.id(), null);
		Navies.Harbour h = Navies.harbour(level.getServer(), f.id());
		FarGrounds.homeWithWhales(level, f, v, h != null ? h.shore() : f.seat());
		JaymosKingdoms.LOGGER.info("{}'s whaler came home unseen with {} whales", f.name(), whales);
	}

	/** What {@code v}'s hold is worth to her realm (emeralds), as it stands. */
	static int worth(Voyage v) {
		return v.get("oil") * OIL_WORTH + v.get("blubber") / TRY_BLUBBER * OIL_WORTH + v.get("ambergris") * AMBERGRIS_WORTH
			+ v.get("bone") * BONE_WORTH + v.get("meat") * MEAT_WORTH;
	}

	/** (0.7.270) {@code player} signs on for {@code v} in {@code berth}. */
	static void signOn(Voyage v, ServerPlayer player, String berth) {
		if (!v.hands.contains(player.getUUID())) {
			v.hands.add(player.getUUID());
		}
		v.berths.put(player.getUUID(), berth);
		// (0.7.419) a road deed: someone signed on with a whaler
		Deeds.done(player.level().getServer(), "whalers.signed");
	}

	/**
	 * (0.7.270) {@code player}'s berth on {@code v} (they're one of her hands). One signed on before berths were kept: a
	 * whaling story's harpooner, else a deckhand (a small one, her powder monkey).
	 */
	static String berth(Voyage v, ServerPlayer player) {
		String berth = v.berths.get(player.getUUID());
		if (berth != null) {
			return berth;
		}
		StoryTalk.Landing l = player.getAttached(StoryTalk.LANDING);
		if (l != null && "whalers".equals(l.start())) {
			return HARPOONER;
		}
		return player.getScale() < StoryTalk.SMALL ? MONKEY : DECKHAND;
	}

	/** "harpooner", "deckhand", "powder monkey". */
	static Component berthName(String berth) {
		return Component.translatable("story.jaymoskingdoms.berth." + berth);
	}

	/** A hand's lay from a catch worth {@code worth}, by their berth. */
	static int lay(int worth, String berth) {
		float share = berth.equals(HARPOONER) ? HARPOONER_LAY : berth.equals(MONKEY) ? MONKEY_LAY : 1.0F;
		return Math.max(LEAST_LAY, Math.round(worth * LAY * share));
	}

	/** {@code v}'s hold into {@code f}'s stores (what won't fit is sold) and the catch's worth into its purse; its worth. */
	private static int unload(ServerLevel level, Faction f, Voyage v) {
		int worth = worth(v);
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
		if (plan != null && ("hunting".equals(crew.mode) || "cutting".equals(crew.mode))) {
			// (0.7.437) out on a far ground: pirates, and sharks under her boats
			FarGrounds.dangers(level, ship, crew, v, boats, now);
		}
		if (plan != null) {
			put(server, crew.faction, v);
			boatwork(level, ship, crew, boats, orders);
		}
		return plan;
	}

	private static float[] hold(Ship ship) {
		return new float[] {ship.getYRot(), 0.0F};
	}

	private static float[] toward(Crew crew, Ship ship, BlockPos to, double sail) {
		// (0.7.285) where she's making for, so her captain can work out a course round the land (SeaRoutes)
		crew.goal = to;
		return new float[] {Fleet.bearing(ship, to.getX() + 0.5, to.getZ() + 0.5), (float) sail};
	}

	/** At the harbour: players aboard sign on; then she sails for her ground. */
	private static float[] fitting(ServerLevel level, Ship ship, Crew crew, Voyage v, long now) {
		for (ServerPlayer p : Corsairs.playersAboard(level, ship)) {
			if (!v.hands.contains(p.getUUID())) {
				// (0.7.270) aboard without a word to her captain: a deckhand (a small one, her powder monkey)
				signOn(v, p, p.getScale() < StoryTalk.SMALL ? MONKEY : DECKHAND);
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.signed", crew.name, berthName(berth(v, p)))
					.withStyle(ChatFormatting.AQUA));
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
			// (0.7.437) the nearer grounds fished out: further out this time
			int out = (int) Math.round(Math.sqrt(Raids.horizontal(crew.home, v.ground)));
			Speech.say(level, captain, v.far > 0 ? Component.translatable("message.jaymoskingdoms.whaler.sails_far", out)
				: Component.translatable("message.jaymoskingdoms.whaler.sails"), Speech.YELLOW, 32.0);
		}
		return toward(crew, ship, v.ground, 1.0);
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
		return toward(crew, ship, v.ground, 1.0);
	}

	/** Cruising the ground for whales; sighting one, her boats lowered after it (or she chases it herself), her harpooners throwing in range; her boats towing their catch back. */
	private static float[] hunting(ServerLevel level, Ship ship, Crew crew, Voyage v, long now, List<AbstractBoat> boats, List<Order> orders) {
		WhaleCarcass carcass = ours(level, ship, boats);
		if (carcass != null) {
			// (0.7.437) more sharks come to it the further out she is
			carcass.wildness = v.far;
			if (!fastTo(level, carcass, ship) && !boats.isEmpty()) {
				// her boats tow it back on their lines while she closes with it
				if (ship.getBoundingBox().inflate(TOW_IN, 4.0, TOW_IN).intersects(carcass.getBoundingBox())) {
					begin(level, ship, crew, carcass, true);
					return hold(ship);
				}
				for (AbstractBoat boat : boats) {
					orders.add(new Order(boat, ship, Pull.TOW));
				}
				return ship.distanceTo(carcass) > ship.getBbWidth() * 0.5 + TOW_IN
					? new float[] {Fleet.bearing(ship, carcass.getX(), carcass.getZ()), FOLLOW_SAIL} : hold(ship);
			}
			begin(level, ship, crew, carcass, true);
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
				return toward(crew, ship, crew.home, 1.0);
			}
			if ((now - v.huntFrom) % POD_AFTER < Corsairs.THINK && now - v.huntFrom >= POD_AFTER) {
				pod(level, ship, v.far);
			}
			if (crew.waypoint == null || Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.waypoint)) < GROUND_REACH) {
				BlockPos next = Fleet.openSea(level, v.ground, GROUND_CRUISE, 6, level.getRandom());
				crew.waypoint = next != null ? next : v.ground;
			}
			return toward(crew, ship, crew.waypoint, 0.6);
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
		Cutting work = CUTTING.get(ship.getUUID());
		WhaleCarcass carcass = work != null && level.getEntity(work.carcass) instanceof WhaleCarcass c && c.isAlive() && c.left() > 0.0F ? c
			: ours(level, ship, boats);
		if (carcass == null) {
			// stripped and sunk (or lost): her anchor weighed, and her tryworks burn on as she hunts the next or sails home
			v.whales++;
			v.huntFrom = level.getGameTime();
			crew.mode = v.whales >= WHALES ? "homeward" : "hunting";
			crew.bound = v.whales >= WHALES ? crew.home : null;
			done(level, ship, crew, v);
			return hold(ship);
		}
		carcass.wildness = v.far;
		if (work == null || !work.carcass.equals(carcass.getUUID())) {
			// (taken up again after a restart, or another carcass come alongside)
			done(level, ship, crew, v);
			begin(level, ship, crew, carcass, false);
			work = CUTTING.get(ship.getUUID());
		}
		if (work != null && !work.gang.isEmpty()) {
			// her gang cut it in, a stroke at a time (gangwork)
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
		return toward(crew, ship, crew.home, 1.0);
	}

	/** Home: her hold into the stores, her catch's worth into the purse, her hands paid their lay; she's laid up (gone). */
	private static void home(ServerLevel level, Ship ship, Crew crew, Voyage v, Faction f) {
		// (what's still blubber, in her hold and her tryworks' pots, is rendered at the harbour)
		done(level, ship, crew, v);
		v.add("blubber", emptyWorks(ship, crew));
		v.add("oil", v.get("blubber") / TRY_BLUBBER);
		v.hold.remove("blubber");
		// (0.7.437) the far grounds' old bulls are fatter
		FarGrounds.richer(v, level.getRandom());
		int worth = unload(level, f, v);
		Navies.Harbour h = Navies.harbour(level.getServer(), f.id());
		// (0.7.242) her crew paid their lay ashore, each to sign on again or leave the sea with it
		Crewmen.payOff(level, ship, crew, h != null ? h.shore() : crew.home, Math.max(LEAST_LAY, Math.round(worth * LAY)), v.whales);
		List<ServerPlayer> aboard = Corsairs.playersAboard(level, ship);
		for (ServerPlayer p : aboard) {
			if (v.hands.contains(p.getUUID())) {
				// (0.7.270) by their berth: a harpooner's lay is the bigger
				String berth = berth(v, p);
				int lay = lay(worth, berth);
				give(p, new ItemStack(Items.EMERALD, lay));
				give(p, new ItemStack(Whaling.WHALE_OIL, LAY_OIL));
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.whaler.lay", lay, crew.name, berthName(berth)).withStyle(ChatFormatting.GOLD));
			}
		}
		Raids.tell(level, ship.blockPosition(), Component.translatable("message.jaymoskingdoms.whaler.home", crew.name, v.whales, v.get("oil"))
			.withStyle(ChatFormatting.AQUA));
		JaymosKingdoms.LOGGER.info("{}'s whaler {} came home with {} whales ({} emeralds)", f.name(), crew.name, v.whales, worth);
		put(level.getServer(), f.id(), null);
		FarGrounds.homeWithWhales(level, f, v, ship.blockPosition());
		stow(ship, List.of());
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.addItem(stack)) {
			p.spawnAtLocation(p.level(), stack);
		}
	}

	/** Every tick she's sailed (from Corsairs): her boats rowed, her catch lashed alongside and cut in, and her tryworks. */
	static void helm(ServerLevel level, Ship ship, Crew crew) {
		row(level, ship);
		Voyage v = voyage(level.getServer(), crew.faction);
		if (v == null || !v.ship.equals(ship.getUUID())) {
			return;
		}
		if ("cutting".equals(crew.mode)) {
			Cutting work = CUTTING.get(ship.getUUID());
			if (work != null && level.getEntity(work.carcass) instanceof WhaleCarcass carcass && carcass.isAlive()) {
				ship.rideAtAnchor(ANCHOR_HOLD);
				lash(ship, carcass, work.side);
				gangwork(level, ship, crew, v, work, carcass);
			}
		}
		tryworks(level, ship, crew, v);
	}

	/**
	 * (0.7.307) Cutting in begins: her anchor let go, her boats' lines passed up to her, the carcass lashed alongside on
	 * the side it lies, and her gang turned to with the spade and the blubber. {@code say}: her captain gives the word.
	 */
	private static void begin(ServerLevel level, Ship ship, Crew crew, WhaleCarcass carcass, boolean say) {
		passLines(level, ship, carcass);
		crew.mode = "cutting";
		int side = ship.toLocal(carcass.position()).x >= 0.0 ? 1 : -1;
		Cutting work = new Cutting(carcass.getUUID(), side);
		CUTTING.put(ship.getUUID(), work);
		ship.rideAtAnchor(ANCHOR_HOLD);
		Hull h = ship.hull();
		Deck deck = Deck.of(ship, crew);
		Vec3 rail = deck == null ? null : rail(ship, deck, side);
		if (rail != null) {
			List<LivingEntity> hands = new ArrayList<>();
			for (LivingEntity hand : ship.crewAboard()) {
				if (!hand.getUUID().equals(crew.captain) && !Downed.isDowned(hand) && hand.hasAttached(Ship.SEAT)
					&& !crew.gunners.containsValue(hand.getUUID())) {
					hands.add(hand);
				}
			}
			// the nearest to the rail cuts, the rest carry
			hands.sort(java.util.Comparator.comparingDouble(e -> e.getAttached(Ship.SEAT).distanceToSqr(rail)));
			for (int i = 0; i < Math.min(GANG, hands.size()); i++) {
				LivingEntity hand = hands.get(i);
				Job job = new Job();
				job.cutter = i == 0;
				job.was = hand.getMainHandItem().copy();
				work.gang.put(hand.getUUID(), job);
				if (job.cutter) {
					hand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
				}
				walkTo(crew, deck, hand, rail);
			}
		}
		if (say) {
			Vec3 bow = h == null ? ship.position() : ship.toWorld(0.0, h.draft, h.maxZ);
			level.playSound(null, bow.x, bow.y, bow.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.4F, 0.6F);
			level.playSound(null, bow.x, bow.y, bow.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.2F, 0.7F);
			level.sendParticles(ParticleTypes.SPLASH, bow.x, bow.y + 0.5, bow.z, 30, 0.6, 0.2, 0.6, 0.2);
			LivingEntity captain = captain(level, ship, crew);
			if (captain != null) {
				Speech.say(level, captain, Component.translatable("message.jaymoskingdoms.whaler.anchor"), Speech.YELLOW, 48.0);
			}
		}
	}

	/** (0.7.307) Cutting in's done: her gang back to their own work (what blubber they still hold to her hold), and her anchor weighed. */
	private static void done(ServerLevel level, Ship ship, Crew crew, Voyage v) {
		Cutting work = CUTTING.remove(ship.getUUID());
		if (work == null) {
			return;
		}
		v.add("blubber", work.waiting);
		for (Map.Entry<UUID, Job> e : work.gang.entrySet()) {
			Job job = e.getValue();
			v.add("blubber", job.carrying);
			if (level.getEntity(e.getKey()) instanceof LivingEntity hand) {
				hand.setItemSlot(EquipmentSlot.MAINHAND, job.was);
			}
			Crew.Walk walk = crew.walks.get(e.getKey());
			if (walk != null) {
				walk.rest = level.getGameTime() + AT_WORK;
			}
		}
		put(level.getServer(), crew.faction, v);
	}

	/** (0.7.307) The carcass held lashed alongside her on {@code side}, amidships, lying along her. */
	private static void lash(Ship ship, WhaleCarcass carcass, int side) {
		Hull h = ship.hull();
		if (h == null) {
			return;
		}
		double off = carcass.getBbWidth() * 0.5 + LASH_GAP + 0.5;
		Vec3 at = ship.toWorld(side > 0 ? h.maxX + off : h.minX - off, 0.0, (h.minZ + h.maxZ) * 0.5);
		carcass.lash(new Vec3(at.x, carcass.getY(), at.z), ship.getYRot());
	}

	/** (0.7.307) Where her cutter stands at her rail on {@code side}, abreast of the carcass (in her frame); null: no such place. */
	private static @Nullable Vec3 rail(Ship ship, Deck deck, int side) {
		Hull h = ship.hull();
		if (h == null) {
			return null;
		}
		double z = (h.minZ + h.maxZ) * 0.5;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 p : deck.places.values()) {
			// as far out on her side as her deck goes, as near abreast of it, on her lowest open deck
			double d = (p.z - z) * (p.z - z) - p.x * side * 4.0 + p.y * 2.0;
			if (p.x * side > 0.0 && d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** (0.7.307) Where her tryworks are tended from: the place on her deck nearest them (in her frame); null: none. */
	private static @Nullable Vec3 worksSide(Ship ship, Crew crew, Deck deck) {
		int i = works(ship, crew);
		Hull h = ship.hull();
		if (i < 0 || h == null) {
			return null;
		}
		Ship.Piece p = h.pieces.get(i);
		return deck.nearest(new Vec3(p.x(), p.y(), p.z()));
	}

	/** (0.7.307) {@code hand} sent along her deck to {@code to} (in her frame), kept from strolling off till they're given more. */
	private static void walkTo(Crew crew, Deck deck, LivingEntity hand, Vec3 to) {
		Vec3 seat = hand.getAttached(Ship.SEAT);
		Crew.Walk walk = crew.walks.computeIfAbsent(hand.getUUID(), k -> new Crew.Walk());
		List<Vec3> way = seat == null ? null : deck.path(seat, to);
		walk.way = way == null ? new ArrayList<>() : new ArrayList<>(way);
		walk.pace = GANG_PACE;
		walk.rest = Long.MAX_VALUE;
	}

	/** (0.7.307) Whether {@code hand} has got where they were walking (or as near as her deck lets them). */
	private static boolean at(Crew crew, LivingEntity hand) {
		Vec3 seat = hand.getAttached(Ship.SEAT);
		Crew.Walk walk = crew.walks.get(hand.getUUID());
		return seat != null && (walk == null || walk.way.isEmpty());
	}

	/**
	 * (0.7.307) Her gang at work, each tick: her cutter at the rail over the carcass, a stroke of the spade at a time (the
	 * meat, bone and ambergris struck below into her hold); her carriers taking the blubber from the rail to the tryworks
	 * in their arms, pitching it in, and going back for more (a lone hand does both).
	 */
	private static void gangwork(ServerLevel level, Ship ship, Crew crew, Voyage v, Cutting work, WhaleCarcass carcass) {
		Deck deck = Deck.of(ship, crew);
		Vec3 rail = deck == null ? null : rail(ship, deck, work.side);
		if (rail == null) {
			return;
		}
		Vec3 works = worksSide(ship, crew, deck), pots = worksAt(ship, crew);
		long now = level.getGameTime();
		boolean carriers = false;
		for (Map.Entry<UUID, Job> e : work.gang.entrySet()) {
			carriers |= !e.getValue().cutter && able(level, ship, e.getKey());
		}
		for (Map.Entry<UUID, Job> e : work.gang.entrySet()) {
			Job job = e.getValue();
			if (!able(level, ship, e.getKey()) || !(level.getEntity(e.getKey()) instanceof LivingEntity hand)) {
				// (fallen, or gone over the side: what they held goes to her hold)
				v.add("blubber", job.carrying);
				job.carrying = 0;
				job.toWorks = false;
				continue;
			}
			if (job.toWorks) {
				if (!at(crew, hand)) {
					continue;
				}
				face(hand, pots);
				if (job.until == 0L) {
					job.until = now + AT_WORK;
				}
				if (now < job.until) {
					continue;
				}
				// pitched into the pots (what won't go in, down to the blubber room)
				hand.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				feed(level, ship, crew, v, job.carrying);
				job.carrying = 0;
				job.toWorks = false;
				job.until = 0L;
				hand.setItemSlot(EquipmentSlot.MAINHAND, job.cutter ? new ItemStack(Items.IRON_SHOVEL) : job.was);
				walkTo(crew, deck, hand, rail);
				continue;
			}
			if (!at(crew, hand)) {
				continue;
			}
			face(hand, carcass.position());
			if (job.cutter && (carriers || work.waiting < CARRY)) {
				if (now % STROKE_EVERY == 0) {
					hand.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					for (ItemStack piece : carcass.cutByCrew(level)) {
						if (piece.is(Whaling.WHALE_BLUBBER)) {
							work.waiting += piece.getCount();
						} else {
							v.add(key(piece.getItem()), piece.getCount());
						}
					}
					put(level.getServer(), crew.faction, v);
				}
				continue;
			}
			if (work.waiting >= CARRY || work.waiting > 0 && (carcass.left() <= 0.0F || job.cutter)) {
				// an armful of blubber to the tryworks
				job.carrying = Math.min(CARRY, work.waiting);
				work.waiting -= job.carrying;
				job.toWorks = true;
				job.until = 0L;
				hand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Whaling.WHALE_BLUBBER, job.carrying));
				if (works != null) {
					walkTo(crew, deck, hand, works);
				}
			}
		}
	}

	/** (0.7.307) Whether {@code id} of her gang is still aboard her and on their feet. */
	private static boolean able(ServerLevel level, Ship ship, UUID id) {
		return level.getEntity(id) instanceof LivingEntity hand && hand.isAlive() && hand.getVehicle() == ship && !Downed.isDowned(hand);
	}

	/** (0.7.307) {@code hand} turned to face {@code at} (in the world). */
	private static void face(LivingEntity hand, @Nullable Vec3 at) {
		if (at == null) {
			return;
		}
		float yaw = (float)(Mth.atan2(at.z - hand.getZ(), at.x - hand.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		hand.setYRot(yaw);
		hand.setYBodyRot(yaw);
		hand.setYHeadRot(yaw);
	}

	/**
	 * (0.7.307) Her tryworks (Andy: "the whale just didnt ... visibly get harvested into the tryworks"): the piece of her
	 * that is one, set on her deck forward of her middle if she has none (a whaler already at sea gets hers too); -1 if
	 * there's nowhere for them.
	 */
	private static int works(Ship ship, Crew crew) {
		Hull h = ship.hull();
		if (h == null) {
			return -1;
		}
		Integer known = WORKS.get(h);
		if (known != null) {
			return known;
		}
		for (int i = 0; i < h.pieces.size(); i++) {
			if (h.pieces.get(i).blockState().is(Whaling.TRYWORKS)) {
				WORKS.put(h, i);
				return i;
			}
		}
		Deck deck = Deck.of(ship, crew);
		if (deck == null) {
			WORKS.put(h, -1);
			return -1;
		}
		double want = (h.minZ + h.maxZ) * 0.5 + (h.maxZ - h.minZ) * WORKS_FORWARD;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 p : deck.places.values()) {
			int x = Mth.floor(p.x + 0.5), y = Mth.floor(p.y + 0.01), z = Mth.floor(p.z + 0.5);
			if (Math.abs(x) > 1 || h.at(x, y, z) >= 0 || h.at(x, y + 1, z) >= 0 || deck.guns.containsValue(p)) {
				continue;
			}
			// on her centreline, forward, on her lowest open deck there
			double d = (p.z - want) * (p.z - want) + x * x * 4.0 + p.y * 2.0;
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		if (best == null) {
			WORKS.put(h, -1);
			return -1;
		}
		int x = Mth.floor(best.x + 0.5), y = Mth.floor(best.y + 0.01), z = Mth.floor(best.z + 0.5);
		ship.setPieceAt(x, y, z, Whaling.TRYWORKS.defaultBlockState().setValue(TryworksBlock.FACING, Direction.SOUTH));
		Hull now = ship.hull();
		int i = now == null ? -1 : now.at(x, y, z);
		if (now != null) {
			WORKS.put(now, i);
		}
		return i;
	}

	/** (0.7.307) The top of her tryworks' pots, in the world (null: she has none). */
	private static @Nullable Vec3 worksAt(Ship ship, Crew crew) {
		int i = works(ship, crew);
		Hull h = ship.hull();
		if (i < 0 || h == null) {
			return null;
		}
		Ship.Piece p = h.pieces.get(i);
		return ship.toWorld(p.x(), p.y() + 1.0, p.z());
	}

	/** (0.7.307) {@code blubber} pitched into her tryworks, lighting them; what's more than they hold goes to her hold. */
	private static void feed(ServerLevel level, Ship ship, Crew crew, Voyage v, int blubber) {
		int i = works(ship, crew);
		Hull h = ship.hull();
		if (i < 0 || h == null) {
			v.add("blubber", blubber);
			put(level.getServer(), crew.faction, v);
			return;
		}
		Ship.Piece p = h.pieces.get(i);
		BlockState state = p.blockState();
		int in = state.getValue(TryworksBlock.BLUBBER), into = Math.min(TryworksBlock.MOST_BLUBBER - in, blubber);
		v.add("blubber", blubber - into);
		if (into > 0 || !state.getValue(TryworksBlock.LIT)) {
			ship.setPieceAt(p.x(), p.y(), p.z(), state.setValue(TryworksBlock.BLUBBER, in + into).setValue(TryworksBlock.LIT, in + into > 0));
		}
		Vec3 at = ship.toWorld(p.x(), p.y() + 1.0, p.z());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.NEUTRAL, 0.9F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 0.8F);
		put(level.getServer(), crew.faction, v);
	}

	/**
	 * Her tryworks at sea: the blubber in their pots rendered to oil, fire under them and smoke going up from her deck,
	 * at the cutting-in and after it as she hunts the next whale or sails home, fed again from the blubber in her hold
	 * till it's all rendered.
	 */
	private static void tryworks(ServerLevel level, Ship ship, Crew crew, Voyage v) {
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		int i = works(ship, crew);
		Hull hull = ship.hull();
		if (i < 0 || hull == null) {
			// (no deck for them: rendered in her hold, as before)
			if (v.get("blubber") >= TRY_BLUBBER && level.getGameTime() % TRY_EVERY == 0) {
				v.add("blubber", -TRY_BLUBBER);
				v.add("oil", 1);
				put(level.getServer(), crew.faction, v);
			}
			return;
		}
		Ship.Piece p = hull.pieces.get(i);
		BlockState state = p.blockState();
		int in = state.getValue(TryworksBlock.BLUBBER);
		if (in < TRY_BLUBBER && v.get("blubber") > 0 && !CUTTING.containsKey(ship.getUUID())) {
			// fed again from the blubber room, a hand pitching it in
			int more = Math.min(TryworksBlock.MOST_BLUBBER - in, v.get("blubber"));
			v.add("blubber", -more);
			in += more;
			LivingEntity hand = harpooner(level, ship, crew, ship);
			if (hand != null) {
				hand.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			state = state.setValue(TryworksBlock.BLUBBER, in).setValue(TryworksBlock.LIT, true);
			ship.setPieceAt(p.x(), p.y(), p.z(), state);
			put(level.getServer(), crew.faction, v);
		}
		if (in <= 0) {
			if (state.getValue(TryworksBlock.LIT)) {
				ship.setPieceAt(p.x(), p.y(), p.z(), state.setValue(TryworksBlock.LIT, false));
			}
			return;
		}
		Vec3 at = ship.toWorld(p.x(), p.y() + 1.0, p.z());
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 0.2, at.z, 1, 0.2, 0.1, 0.2, 0.01);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 2, 0.3, 0.1, 0.3, 0.02);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.3, at.z, 2, 0.25, 0.05, 0.25, 0.0);
		if (level.getGameTime() % TRY_EVERY == 0 && in >= TRY_BLUBBER) {
			in -= TRY_BLUBBER;
			v.add("oil", 1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.NEUTRAL, 0.8F, 0.7F);
			ship.setPieceAt(p.x(), p.y(), p.z(), state.setValue(TryworksBlock.BLUBBER, in).setValue(TryworksBlock.LIT, in > 0 || v.get("blubber") > 0));
			put(level.getServer(), crew.faction, v);
		}
	}

	/** (0.7.307) What's still in her tryworks' pots (blubber), taken out (she's home). */
	private static int emptyWorks(Ship ship, Crew crew) {
		int i = works(ship, crew);
		Hull h = ship.hull();
		if (i < 0 || h == null) {
			return 0;
		}
		Ship.Piece p = h.pieces.get(i);
		int in = p.blockState().getValue(TryworksBlock.BLUBBER);
		ship.setPieceAt(p.x(), p.y(), p.z(), p.blockState().setValue(TryworksBlock.BLUBBER, 0).setValue(TryworksBlock.LIT, false));
		return in;
	}

	/** Her whaler was sunk: the voyage is over. */
	static void lost(ServerLevel level, Crew crew) {
		Voyage v = voyage(level.getServer(), crew.faction);
		if (v != null) {
			put(level.getServer(), crew.faction, null);
			// (0.7.437) the grounds that took her are shunned
			FarGrounds.lost(level.getServer(), crew.faction);
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
	private static void pod(ServerLevel level, Ship ship, int far) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 6; tries++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			BlockPos at = BlockPos.containing(ship.getX() + Mth.cos(angle) * POD_OFF, level.getSeaLevel() - 8, ship.getZ() + Mth.sin(angle) * POD_OFF);
			if (level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at.below(3)).is(FluidTags.WATER)
				&& !Raids.playerNear(level, at, POD_HIDE)) {
				// (0.7.437) bigger pods on the far grounds, sperm whales and the white whale among them
				int n = FarGrounds.pod(1 + random.nextInt(2), far);
				for (int i = 0; i < n; i++) {
					GreatWhale whale = Whaling.GREAT_WHALE.spawn(level, at.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), EntitySpawnReason.EVENT);
					if (whale != null) {
						FarGrounds.breed(whale, far, random);
					}
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
			boat.setAttached(CREWED, true);
			boat.setAttached(LAUNCH, true);
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
				// no one left to pull her: cast adrift (whoever's in her may take her oars)
				boat.removeAttached(WHALEBOAT);
				boat.removeAttached(CREWED);
				continue;
			}
			if (!boat.hasAttached(CREWED)) {
				// (a boat lowered before 0.7.307)
				boat.setAttached(CREWED, true);
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
		Deck deck = Deck.of(ship, crew);
		for (Entity e : List.copyOf(boat.getPassengers())) {
			e.stopRiding();
			if (e instanceof ServerPlayer player) {
				// (0.7.307) hoisted in with the boat: set on her deck by the falls, not left in the sea beside her
				Vec3 place = deck == null ? null : deck.nearest(ship.toLocal(boat.position()));
				Vec3 at = place != null ? ship.toWorld(place.x, place.y + 0.05, place.z) : ship.helmStand().add(0.0, 0.1, 0.0);
				player.teleportTo(level, at.x, at.y, at.z, java.util.Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), player.getXRot(), false);
				player.resetFallDistance();
				continue;
			}
			if (!(e instanceof LivingEntity hand)) {
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
