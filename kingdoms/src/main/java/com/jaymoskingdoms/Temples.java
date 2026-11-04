package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The patrons' temples (docs/Patrons-C-Plan.md): rare structures that rise out of sight near players in the Wildlands or
 * higher, each with its patron's champion asleep at the altar. Placed at runtime like the Nether forts, because worldgen cannot
 * read a world tier. The Truth's great temple is built by a Faith-creed kingdom's capital ({@link GreatTemple}).
 */
public final class Temples {
	/** How often each player's surroundings are looked at for a new temple (ticks), and the chance one rises then. */
	/** How far from a temple altar its champion is looked for (blocks). */
	static final double CHAMPION_SEARCH = 64.0;
	static final int RISE_EVERY = 20 * 60 * 3;
	static final float RISE_CHANCE = 0.12F;
	/** The player's tier (an ordinal) from which temples rise near them: Wildlands. */
	static final int RISE_TIER = 2;
	/** No new temple while any is this near the player; one patron's temples, in one world, keep this far apart (blocks). */
	static final double TEMPLE_AREA = 800.0, SAME_SPACING = 2000.0;
	/** A temple rises this far from the player, out of every player's sight (none within HIDE_RANGE). */
	static final int SITE_MIN = 72, SITE_MAX = 144, SITE_TRIES = 10;
	static final double HIDE_RANGE = 48.0;
	/** The Truth's ruined cathedral is rarer than the others: its weight when a patron is drawn for a site. */
	static final float RUIN_WEIGHT = 0.35F;
	/** Shrine days after a kill before the champion stands at its altar again. */
	static final int RETURN_DAYS = 3;
	/** How often loaded temples are tended (ticks): re-forming, and players coming near. */
	static final int TEND_EVERY = 100;
	/** A player this near an altar meets its patron (and is shown the temple's name once until they leave LEAVE_RANGE). */
	static final double LEARN_RANGE = 16.0, LEAVE_RANGE = 48.0;
	/** The great temple: a Faith-creed kingdom's capital builds it from this many townsfolk. */
	static final int GREAT_TEMPLE_FROM = 30;
	/** How many tends a temple's champion must be missing before it is raised again (so a slow load never doubles it). */
	static final int MISSING_TENDS = 2;

	public static final int WILD = 0, RUIN = 1, GREAT = 2;
	public static final String GREAT_TEMPLE = "great_temple";

	/** (0.7.518) A patron's temple: where, whose, which kind, its altar and champion, and when its champion last fell. */
	public record Temple(int id, String patron, String world, BlockPos center, BlockPos altar, Direction facing, int kind, int faction,
		long slainDay, Optional<UUID> champion) {
		public static final Codec<Temple> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Temple::id), Codec.STRING.fieldOf("patron").forGetter(Temple::patron),
			Codec.STRING.fieldOf("world").forGetter(Temple::world), BlockPos.CODEC.fieldOf("center").forGetter(Temple::center),
			BlockPos.CODEC.fieldOf("altar").forGetter(Temple::altar), Direction.CODEC.fieldOf("facing").forGetter(Temple::facing),
			Codec.INT.fieldOf("kind").forGetter(Temple::kind), Codec.INT.fieldOf("faction").forGetter(Temple::faction),
			Codec.LONG.fieldOf("slain_day").forGetter(Temple::slainDay),
			UUIDUtil.CODEC.optionalFieldOf("champion").forGetter(Temple::champion)).apply(i, Temple::new));

		Temple withSlainDay(long day) {
			return new Temple(this.id, this.patron, this.world, this.center, this.altar, this.facing, this.kind, this.faction, day, Optional.empty());
		}

		Temple withChampion(UUID champion) {
			return new Temple(this.id, this.patron, this.world, this.center, this.altar, this.facing, this.kind, this.faction, this.slainDay,
				Optional.of(champion));
		}

		@Nullable Patron patronOf() {
			return Patron.byKey(this.patron);
		}
	}

	/** Every temple there's been, in every world, on the Overworld ({@link Factions#level}). */
	static final AttachmentType<List<Temple>> TEMPLES = AttachmentRegistry.create(JaymosKingdoms.id("patron_temples"),
		b -> b.persistent(Temple.CODEC.listOf()));

	/** Who is near which altar (not saved), and how long a temple's champion has been missing (not saved). */
	private static final Map<Integer, Set<UUID>> NEAR = new HashMap<>();
	private static final Map<Integer, Integer> MISSING = new HashMap<>();

	private Temples() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("temple attachment: {}", TEMPLES);
		ServerTickEvents.END_SERVER_TICK.register(Temples::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEAR.clear();
			MISSING.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("temple").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("raise").then(Commands.argument("patron", StringArgumentType.word()).suggests(PatronChampions::suggest)
					.executes(Temples::raiseCommand)))
				.then(Commands.literal("list").executes(Temples::listCommand))
				.then(Commands.literal("reform").executes(Temples::reformCommand)))));
	}

	// ---- the records ---------------------------------------------------------------------------------------------------------

	public static List<Temple> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(TEMPLES, List.of());
	}

	static void put(MinecraftServer server, Temple temple) {
		List<Temple> next = new ArrayList<>(all(server));
		next.removeIf(t -> t.id() == temple.id());
		next.add(temple);
		Factions.level(server).setAttached(TEMPLES, List.copyOf(next));
	}

	static @Nullable Temple byId(MinecraftServer server, int id) {
		for (Temple t : all(server)) {
			if (t.id() == id) {
				return t;
			}
		}
		return null;
	}

	static @Nullable ServerLevel world(MinecraftServer server, Temple temple) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(temple.world())));
	}

	static @Nullable Temple nearest(MinecraftServer server, ServerLevel level, BlockPos pos, double range) {
		Temple best = null;
		double bestD = range * range;
		String world = level.dimension().identifier().toString();
		for (Temple t : all(server)) {
			double d = t.center().distSqr(pos);
			if (t.world().equals(world) && d <= bestD) {
				best = t;
				bestD = d;
			}
		}
		return best;
	}

	private static int nextId(MinecraftServer server) {
		int max = 0;
		for (Temple t : all(server)) {
			max = Math.max(max, t.id());
		}
		return max + 1;
	}

	/** A champion's spot: two blocks before the altar, on the floor or the dais. */
	static BlockPos spotFor(ServerLevel level, BlockPos altar, Direction facing) {
		BlockPos spot = altar.relative(facing, 2);
		for (int i = 0; i < 4 && level.getBlockState(spot).isSolid(); i++) {
			spot = spot.above();
		}
		for (int i = 0; i < 4 && !level.getBlockState(spot.below()).isSolid(); i++) {
			spot = spot.below();
		}
		return spot;
	}

	// ---- rising --------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % RISE_EVERY == 0) {
			rise(server);
		}
		if (t % TEND_EVERY == 0) {
			tend(server);
		}
	}

	/** (0.7.528) By the realm the player stands in, so a One Dimension world's stacked Nether and Aether bands count ({@link Stacked}). */
	private static boolean worldOk(ServerPlayer sp) {
		ResourceKey<Level> realm = Stacked.realm(sp);
		return realm == Level.OVERWORLD || realm == Level.NETHER || TempleSites.aether(realm);
	}

	private static void rise(MinecraftServer server) {
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (sp.isSpectator() || sp.isCreative() || !(sp.level() instanceof ServerLevel level) || !worldOk(sp)
				|| WorldTiers.of(sp).ordinal() < RISE_TIER || level.getRandom().nextFloat() >= RISE_CHANCE) {
				continue;
			}
			// (0.7.528) in the band the player stands in: an Aether temple looks for the islands' ground, not the Overworld's
			Stacked.within(level, sp.getBlockY(), () -> riseNear(server, sp, level));
		}
	}

	private static void riseNear(MinecraftServer server, ServerPlayer sp, ServerLevel level) {
		String world = level.dimension().identifier().toString();
		boolean crowded = false;
		for (Temple t : all(server)) {
			if (t.world().equals(world) && t.center().closerThan(sp.blockPosition(), TEMPLE_AREA)) {
				crowded = true;
				break;
			}
		}
		if (crowded) {
			return;
		}
		for (int i = 0; i < SITE_TRIES; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2.0;
			double d = SITE_MIN + level.getRandom().nextDouble() * (SITE_MAX - SITE_MIN);
			int x = (int)(sp.getX() + Math.cos(a) * d), z = (int)(sp.getZ() + Math.sin(a) * d);
			if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
				continue;
			}
			int y = Stacked.in(sp, Level.OVERWORLD) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z)
				: sp.getBlockY();
			BlockPos spot = new BlockPos(x, y, z);
			Patron p = draw(server, level, spot, sp.blockPosition(), world);
			if (p == null) {
				continue;
			}
			BlockPos site = TempleSites.find(level, p, spot, sp.blockPosition(), false);
			if (site != null) {
				found(level, p, site);
				break;
			}
		}
	}

	/** A patron drawn by weight from those whose rules fit the spot and who have no temple of their own near it. */
	private static @Nullable Patron draw(MinecraftServer server, ServerLevel level, BlockPos spot, BlockPos playerPos, String world) {
		List<Patron> fit = new ArrayList<>();
		List<Float> weights = new ArrayList<>();
		float total = 0.0F;
		for (Patron p : Patron.values()) {
			if (!TempleSites.fits(level, p, spot, playerPos)) {
				continue;
			}
			boolean taken = false;
			for (Temple t : all(server)) {
				if (t.patron().equals(p.key) && t.world().equals(world) && t.center().closerThan(spot, SAME_SPACING)) {
					taken = true;
					break;
				}
			}
			if (taken) {
				continue;
			}
			float w = p == Patron.TRUTH ? RUIN_WEIGHT : 1.0F;
			fit.add(p);
			weights.add(w);
			total += w;
		}
		if (fit.isEmpty()) {
			return null;
		}
		float roll = level.getRandom().nextFloat() * total;
		for (int i = 0; i < fit.size(); i++) {
			roll -= weights.get(i);
			if (roll <= 0.0F) {
				return fit.get(i);
			}
		}
		return fit.get(fit.size() - 1);
	}

	/** Builds {@code p}'s temple at {@code site} and sets its champion down asleep. */
	static Temple found(ServerLevel level, Patron p, BlockPos site) {
		MinecraftServer server = level.getServer();
		TempleBuildings.Layout layout = TempleBuildings.build(level, p, site, level.getRandom());
		int id = nextId(server);
		int kind = p == Patron.TRUTH ? RUIN : WILD;
		Temple temple = new Temple(id, p.key, level.dimension().identifier().toString(), site, layout.altar(), layout.facing(), kind, -1, -1L,
			Optional.empty());
		PatronChampion c = PatronChampions.raise(level, p, id, kind, layout.altar(), spotFor(level, layout.altar(), layout.facing()), layout.facing());
		if (c != null) {
			temple = temple.withChampion(c.getUUID());
		}
		put(server, temple);
		JaymosKingdoms.LOGGER.info("{}'s temple rose at {} in {}", p.key, site, temple.world());
		return temple;
	}

	// ---- tending -------------------------------------------------------------------------------------------------------------

	private static void tend(MinecraftServer server) {
		long today = Patrons.today(server);
		for (Temple t : List.copyOf(all(server))) {
			ServerLevel level = world(server, t);
			Patron p = t.patronOf();
			if (level == null || p == null || !level.isPositionEntityTicking(t.altar())) {
				continue;
			}
			boolean there = championThere(level, t);
			if (t.slainDay() >= 0) {
				if (today >= t.slainDay() + RETURN_DAYS && !there) {
					raiseAgain(level, t, p);
				}
			} else if (!there) {
				if (MISSING.merge(t.id(), 1, Integer::sum) >= MISSING_TENDS) {
					raiseAgain(level, t, p);
				}
			} else {
				MISSING.remove(t.id());
			}
			meet(level, t, p);
		}
	}

	private static boolean championThere(ServerLevel level, Temple t) {
		return !level.getEntitiesOfClass(PatronChampion.class, new AABB(t.altar()).inflate(CHAMPION_SEARCH), c -> c.templeId() == t.id() && c.isAlive()).isEmpty();
	}

	private static void raiseAgain(ServerLevel level, Temple t, Patron p) {
		MISSING.remove(t.id());
		PatronChampion c = PatronChampions.raise(level, p, t.id(), t.kind(), t.altar(), spotFor(level, t.altar(), t.facing()), t.facing());
		Temple next = t.withSlainDay(-1L);
		put(level.getServer(), c == null ? next : next.withChampion(c.getUUID()));
	}

	private static void meet(ServerLevel level, Temple t, Patron p) {
		Set<UUID> near = NEAR.computeIfAbsent(t.id(), id -> new HashSet<>());
		for (ServerPlayer sp : level.players()) {
			double d = sp.distanceToSqr(t.altar().getX() + 0.5, t.altar().getY(), t.altar().getZ() + 0.5);
			if (d > LEAVE_RANGE * LEAVE_RANGE) {
				near.remove(sp.getUUID());
			} else if (d <= LEARN_RANGE * LEARN_RANGE && !sp.isSpectator() && near.add(sp.getUUID())) {
				Patrons.met(sp, p, t.altar());
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.temple.near", Patrons.coloured(p)));
			}
		}
	}

	/** Called when a temple's champion falls: its slain day is kept. */
	static void slain(MinecraftServer server, int id, long today) {
		Temple t = byId(server, id);
		if (t != null) {
			put(server, t.withSlainDay(today));
		}
	}

	// ---- the great temple ----------------------------------------------------------------------------------------------------

	/** Only the capital of a Faith-creed realm builds the great temple. */
	static boolean greatTempleFor(MinecraftServer server, Faction f) {
		return f.type() == FactionType.KINGDOM && f.overlord() < 0 && !f.fallen() && GreatWars.creed(server, f) == GreatWars.Creed.FAITH;
	}

	/** From {@link Gatherers#built}: the great temple stands; its altar is where the Inquisitor sleeps. */
	static void greatBuilt(ServerLevel level, Faction f, BlockPos min, BlockPos max) {
		BlockPos altar = null;
		for (BlockPos pos : BlockPos.betweenClosed(min, max.above(GreatTemple.H - 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Healing.SHRINE) && state.getValue(Shrine.DEDICATION) == Shrine.Dedication.TRUTH) {
				altar = pos.immutable();
				break;
			}
		}
		if (altar == null) {
			JaymosKingdoms.LOGGER.warn("The great temple of faction {} has no altar between {} and {}", f.id(), min, max);
			return;
		}
		BlockPos mid = new BlockPos((min.getX() + max.getX()) / 2, altar.getY(), (min.getZ() + max.getZ()) / 2);
		Direction facing = Direction.getApproximateNearest(mid.getX() - altar.getX(), 0, mid.getZ() - altar.getZ());
		if (facing.getAxis().isVertical()) {
			facing = Direction.SOUTH;
		}
		MinecraftServer server = level.getServer();
		for (Temple old : all(server)) {
			if (old.kind() == GREAT && old.faction() == f.id()) {
				// a rebuild: the temple and its Inquisitor are already on record
				JaymosKingdoms.LOGGER.info("The great temple of faction {} was rebuilt at {}", f.id(), altar);
				return;
			}
		}
		int id = nextId(server);
		Temple temple = new Temple(id, Patron.TRUTH.key, level.dimension().identifier().toString(), mid, altar, facing, GREAT, f.id(), -1L, Optional.empty());
		PatronChampion c = PatronChampions.raise(level, Patron.TRUTH, id, GREAT, altar, spotFor(level, altar, facing), facing);
		if (c != null) {
			temple = temple.withChampion(c.getUUID());
		}
		put(server, temple);
		JaymosKingdoms.LOGGER.info("The great temple of faction {} stands at {}", f.id(), altar);
	}

	// ---- op commands ---------------------------------------------------------------------------------------------------------

	private static int raiseCommand(CommandContext<CommandSourceStack> c) {
		Patron p = Patron.byKey(StringArgumentType.getString(c, "patron"));
		ServerPlayer sp = c.getSource().getPlayer();
		if (p == null || sp == null || !(sp.level() instanceof ServerLevel level)) {
			c.getSource().sendFailure(Component.literal("No such patron, or no player."));
			return 0;
		}
		BlockPos ahead = BlockPos.containing(sp.position().add(sp.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(TempleBuildings.radius(p) + 6)));
		BlockPos site = TempleSites.find(level, p, ahead, sp.blockPosition(), true);
		if (site == null) {
			c.getSource().sendFailure(Component.literal("No fit ground for " + p.key + " here."));
			return 0;
		}
		Temple t = found(level, p, site);
		c.getSource().sendSuccess(() -> Component.literal("Temple " + t.id() + " of " + p.key + " at " + t.center().toShortString()), false);
		return 1;
	}

	private static int listCommand(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		for (Temple t : all(server)) {
			ServerLevel level = world(server, t);
			String state = level == null ? "unknown world" : championThere(level, t) ? "present" : t.slainDay() >= 0 ? "gone" : "missing";
			c.getSource().sendSuccess(() -> Component.literal(t.id() + " " + t.patron() + " " + t.world() + " " + t.center().toShortString() + " kind " + t.kind()
				+ " champion " + state + " slain day " + t.slainDay()), false);
		}
		return all(server).size();
	}

	private static int reformCommand(CommandContext<CommandSourceStack> c) {
		ServerPlayer sp = c.getSource().getPlayer();
		if (sp == null || !(sp.level() instanceof ServerLevel level)) {
			return 0;
		}
		Temple t = nearest(level.getServer(), level, sp.blockPosition(), 4000.0);
		Patron p = t == null ? null : t.patronOf();
		if (t == null || p == null || !level.isLoaded(t.altar())) {
			c.getSource().sendFailure(Component.literal("No loaded temple near."));
			return 0;
		}
		if (!championThere(level, t)) {
			raiseAgain(level, t, p);
		}
		c.getSource().sendSuccess(() -> Component.literal("Temple " + t.id() + "'s champion stands."), false);
		return 1;
	}
}
