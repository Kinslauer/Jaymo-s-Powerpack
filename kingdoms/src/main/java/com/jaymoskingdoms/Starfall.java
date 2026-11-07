package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.387, Andy's SevTech picks: "Back home") Falling stars. On a rare night a light comes down out of the sky and lands in the
 * wild within {@link #NEAR_MAX} blocks of a town: a streak of fire and sparks, a boom, and a small scorched crater with a few
 * lumps of Jaymo's Powers' star metal ore in its floor (the only place the ore is found). Everyone within {@link #TELL_RANGE}
 * blocks reads of it in the next Tidings as a bearing from the town ("a light fell toward the hills north of X").
 * <p>
 * Once a night (after the world's {@link Moons#QUIET_NIGHTS} quiet ones) it rolls {@link #ODDS}; a hit comes down at some point
 * of the night. The place is picked near a player, in loaded chunks, never in a town's claimed land and never on water.
 * Without Jaymo's Powers (no ore to leave) nothing falls. {@code /kingdom starfall} (gamemasters) brings one down now, near the
 * nearest town to the sender, whatever the night.
 */
public final class Starfall {
	/** One night in this many sees a star fall. */
	static final int ODDS = 8;
	/** The crater lies this far from the town's seat (blocks); the greatest is the brief's "within 300". */
	static final int NEAR_MIN = 90, NEAR_MAX = 300;
	/** A town is chosen among those this near a player (blocks); the tidings reach players this near the crater. */
	static final double PICK_RANGE = 400.0, TELL_RANGE = 500.0;
	/** The fall: ticks it takes, how high it starts above the ground, and how far to the side it comes from. */
	static final int FALL_TICKS = 90, FALL_HEIGHT = 140, FALL_SIDE = 70;
	/** The crater: the radius of the bowl (blocks), the lining of scorched rock round it, and the ore in its floor (least, most). */
	static final double BOWL = 3.3, LINING = 1.6;
	static final int ORE_MIN = 3, ORE_MAX = 5;
	/** Tries to find a place before giving up for the night. */
	static final int TRIES = 24;
	/** Between the night's start and the fall (ticks). */
	static final int DELAY_MIN = 200, DELAY_SPREAD = 6000;
	/** The Powers block it leaves. */
	static final Identifier ORE = Identifier.fromNamespaceAndPath("jaymospowers", "star_metal_ore");

	/** The last night that has been rolled for (the Overworld's, saved). */
	private static final AttachmentType<Long> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("starfall"),
		builder -> builder.persistent(Codec.LONG).initializer(() -> -1L));

	/** A star on its way down. */
	private static final class Fall {
		final ServerLevel level;
		final BlockPos land;
		final BlockPos town;
		final double fromX, fromZ;
		int age;

		Fall(ServerLevel level, BlockPos land, BlockPos town, double fromX, double fromZ) {
			this.level = level;
			this.land = land;
			this.town = town;
			this.fromX = fromX;
			this.fromZ = fromZ;
		}
	}

	private static final List<Fall> FALLING = new ArrayList<>();
	/** The game time tonight's star comes down at, or -1. */
	private static long due = -1L;

	private Starfall() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Starfall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FALLING.clear();
			due = -1L;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("starfall").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				ServerPlayer player = c.getSource().getPlayer();
				if (player == null) {
					return 0;
				}
				boolean ok = start(c.getSource().getServer(), player);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "A star is falling." : "No place found (needs Jaymo's Powers, a town, and loaded land)."), false);
				return ok ? 1 : 0;
			}))));
	}

	/** Whether Jaymo's Powers (and so the ore) is here. */
	private static @Nullable Block ore() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return null;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(ORE);
		return block == Blocks.AIR ? null : block;
	}

	// ---- the night's roll ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (!FALLING.isEmpty()) {
			for (Iterator<Fall> it = FALLING.iterator(); it.hasNext();) {
				Fall fall = it.next();
				if (fall(fall)) {
					it.remove();
				}
			}
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long night = Moons.nightOf(overworld.getOverworldClockTime());
		if (night < 0) {
			due = -1L;
			return;
		}
		if (night >= Moons.QUIET_NIGHTS && overworld.getAttachedOrElse(ROLLED, -1L) != night) {
			overworld.setAttached(ROLLED, night);
			if (overworld.getRandom().nextInt(ODDS) == 0 && ore() != null) {
				due = overworld.getGameTime() + DELAY_MIN + overworld.getRandom().nextInt(DELAY_SPREAD);
			}
		}
		if (due >= 0 && overworld.getGameTime() >= due) {
			due = -1L;
			List<ServerPlayer> players = new ArrayList<>(overworld.players());
			Collections.shuffle(players);
			for (ServerPlayer player : players) {
				if (!player.isSpectator() && start(server, player)) {
					break;
				}
			}
		}
	}

	/** Picks a place near {@code player} and sets a star falling on it. */
	private static boolean start(MinecraftServer server, ServerPlayer player) {
		if (ore() == null || !(player.level() instanceof ServerLevel level) || level != server.overworld()) {
			return false;
		}
		List<Faction> towns = new ArrayList<>();
		for (Faction town : Factions.all(server)) {
			if (!town.fallen() && town.type() != FactionType.BARBARIAN && player.blockPosition().distSqr(town.seat()) <= PICK_RANGE * PICK_RANGE) {
				towns.add(town);
			}
		}
		if (towns.isEmpty()) {
			return false;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < TRIES; i++) {
			Faction town = towns.get(random.nextInt(towns.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR_MIN + random.nextDouble() * (NEAR_MAX - NEAR_MIN);
			int x = town.seat().getX() + (int)Math.round(Math.sin(angle) * distance);
			int z = town.seat().getZ() - (int)Math.round(Math.cos(angle) * distance);
			BlockPos land = place(server, level, x, z);
			if (land != null) {
				double side = random.nextDouble() * Math.PI * 2.0;
				FALLING.add(new Fall(level, land, town.seat(), land.getX() + Math.sin(side) * FALL_SIDE, land.getZ() - Math.cos(side) * FALL_SIDE));
				JaymosKingdoms.LOGGER.info("[starfall] a star falls at {} ({} from {})", land.toShortString(), (int)Math.sqrt(land.distSqr(town.seat())), town.name());
				return true;
			}
		}
		return false;
	}

	/** The ground at (x, z) if a star can land there: loaded all round, dry, on solid ground, and not in a town's claim. */
	private static @Nullable BlockPos place(MinecraftServer server, ServerLevel level, int x, int z) {
		int reach = (int)Math.ceil(BOWL + LINING) + 1;
		if (!level.hasChunksAt(x - reach, z - reach, x + reach, z + reach)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos ground = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(ground);
		if (y < level.getMinY() + 8 || state.isAir() || !state.getFluidState().isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
			return null;
		}
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				if (Factions.at(server, level, ground.offset(dx, 0, dz)) != null) {
					return null;
				}
			}
		}
		return ground;
	}

	// ---- the fall -----------------------------------------------------------------------------------------------------

	/** One tick of a star's fall; true once it has landed (or can't). */
	private static boolean fall(Fall fall) {
		ServerLevel level = fall.level;
		double t = ++fall.age / (double)FALL_TICKS;
		if (t >= 1.0) {
			land(fall);
			return true;
		}
		double eased = t * t * 0.6 + t * 0.4;
		double x = fall.fromX + (fall.land.getX() + 0.5 - fall.fromX) * eased;
		double z = fall.fromZ + (fall.land.getZ() + 0.5 - fall.fromZ) * eased;
		double y = fall.land.getY() + 1.0 + FALL_HEIGHT * (1.0 - eased);
		level.sendParticles(ParticleTypes.FLAME, true, true, x, y, z, 14, 0.5, 0.5, 0.5, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, true, true, x, y, z, 6, 0.7, 0.7, 0.7, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 0.5, z, 8, 0.6, 0.6, 0.6, 0.01);
		if (fall.age % 10 == 1) {
			level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 6.0F, 0.5F);
		}
		return false;
	}

	/** The boom, the crater and the word in the Tidings. */
	private static void land(Fall fall) {
		ServerLevel level = fall.level;
		Block ore = ore();
		BlockPos at = fall.land;
		int reach = (int)Math.ceil(BOWL + LINING) + 1;
		if (ore == null || !level.hasChunksAt(at.getX() - reach, at.getZ() - reach, at.getX() + reach, at.getZ() + reach)) {
			return;
		}
		// (0.7.419) a road deed: a star came down
		RoadDeeds.doneNear(level, net.minecraft.world.phys.Vec3.atCenterOf(at), RoadDeeds.STAR_SEEN, "star.fell");
		RandomSource random = level.getRandom();
		// the bowl, dug out of whatever is there (never bedrock or a chest), then lined with scorched rock
		List<BlockPos> floor = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dy = -reach; dy <= reach; dy++) {
				for (int dz = -reach; dz <= reach; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (state.getDestroySpeed(level, pos) < 0.0F || level.getBlockEntity(pos) != null) {
						continue;
					}
					if (d <= BOWL) {
						if (!state.isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					} else if (d <= BOWL + LINING && dy <= 0 && !state.isAir() && state.getFluidState().isEmpty()) {
						int pick = random.nextInt(10);
						level.setBlock(pos, (pick < 5 ? Blocks.BLACKSTONE : pick < 8 ? Blocks.BASALT : Blocks.SMOOTH_BASALT).defaultBlockState(), Block.UPDATE_ALL);
						if (dy <= -2) {
							floor.add(pos.immutable());
						}
					}
				}
			}
		}
		// lumps of star metal in the bottom of the bowl
		Collections.shuffle(floor, new java.util.Random(random.nextLong()));
		int lumps = ORE_MIN + random.nextInt(ORE_MAX - ORE_MIN + 1);
		for (int i = 0; i < Math.min(lumps, floor.size()); i++) {
			level.setBlock(floor.get(i), ore.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.explode(null, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.NONE);
		level.sendParticles(ParticleTypes.END_ROD, true, true, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 60, 2.5, 1.5, 2.5, 0.15);
		level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 40.0F, 0.6F);
		tell(level, fall);
	}

	// ---- the word -----------------------------------------------------------------------------------------------------

	/** The 8 points of the compass, clockwise from north. */
	static final int POINTS = 8;

	/** Which point of the compass {@code to} lies at from {@code from} (0 north, 2 east, 4 south, 6 west). */
	static int point(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
		return Math.floorMod((int)Math.round(angle / 45.0), POINTS);
	}

	private static void tell(ServerLevel level, Fall fall) {
		String name = "";
		Faction town = Factions.all(level.getServer()).stream().filter(f -> f.seat().equals(fall.town)).findFirst().orElse(null);
		if (town != null) {
			name = town.name();
		}
		Component dir = Component.translatable("tidings.jaymoskingdoms.starfall.dir." + point(fall.town, fall.land));
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.blockPosition().distSqr(fall.land) <= TELL_RANGE * TELL_RANGE) {
				Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.starfall." + level.getRandom().nextInt(LINES), name, dir));
			}
		}
	}

	/** Variants of the tidings line ({@code tidings.jaymoskingdoms.starfall.<n>}). */
	static final int LINES = 3;
}
