package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Encounters;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.WorldTiers;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.145) The Aether's remnants, Andy's lore for the pack: the Aether scattered pieces of itself across the Overworld,
 * and the last of them are the monoliths and their Giants. Our own remake of Ancient Remnants' monoliths (that mod has
 * no Fabric 26.3 build; nothing of its code, structures or art is used): a broken pillar of its element's stone with a
 * glowing core floating over its crown, raised now and then out in the wild a little way from a player
 * ({@link #RAISE_CHANCE} every {@link #RAISE_TICKS}, never within {@link #SPACING} of another). Its element follows the
 * biome ({@link Element#of}). A player coming near an awake monolith wakes its Giant ({@link AetherGiant}), which rises
 * beside it and keeps to it. Slain, the Giant leaves its monolith dark for good, and the first Giant slain in the world
 * raises the world tier for everyone ({@link WorldTiers#raiseFloor}). With Ancient Remnants loaded (once it has a 26.3
 * build) its own monoliths wake Giants too ({@link AncientRemnantsCompat}).
 */
public final class Monoliths {
	/** How often the monoliths are looked over (ticks), and how near a player must come to wake its Giant. */
	static final int CHECK_TICKS = 40;
	static final double WAKE_RANGE = 40.0;
	/** How far round its monolith a Giant is looked for (blocks), and how far it roams from it. */
	static final double GIANT_SEARCH = 96.0;
	public static final int HOME_RADIUS = 28;
	/** Where a Giant rises: this far from its monolith (blocks). */
	static final int RISE_NEAREST = 16, RISE_FURTHEST = 22;
	/** Raising one: how often players in the wild are weighed (ticks), the chance each time, how far off it rises, how far apart they are. */
	static final int RAISE_TICKS = 2400;
	/** (0.7.159) Andy: "the giants need to be rarer": a third as likely as before (about one per 100 minutes in the wild), twice as far apart. */
	static final float RAISE_CHANCE = 0.02F;
	static final int RAISE_NEAREST = 64, RAISE_FURTHEST = 112, SPACING = 2400;
	/**
	 * (0.7.159) Andy: "they shouldnt activate until after borderlands world tier, the monument will only hum", then "do
	 * wildlands". A Giant rises only for a player at this world tier (the one after Borderlands) or higher; for anyone
	 * below, its monolith only hums.
	 */
	static final WorldTiers.Tier WAKE_TIER = WorldTiers.Tier.WILDLANDS;
	/** No monolith this close to a settlement (blocks). */
	public static final double SETTLEMENT_CLEARANCE = 256.0;
	/** Tries at finding ground for a monolith, or for a Giant. */
	static final int SPOT_TRIES = 10;
	/**
	 * (0.7.262) Andy: "the monoliths should be triple their current size". The monolith is drawn as before, every block of
	 * it this many blocks across: the pillar 9 wide, 39 to 54 tall, its crown 3 wide, its core and shards 3 blocks a side.
	 */
	static final int SCALE = 3;
	/** The pillar (in its own blocks, {@link #SCALE} each): how tall before its broken crown, its bands' spacing, the gap under the floating core. */
	static final int PILLAR_LEAST = 13, PILLAR_MOST = 18, BAND_EVERY = 4, CORE_GAP = 2;
	/** The paved ground round its foot (radius, blocks), and how many broken pieces lie about. */
	static final int GROUND_RADIUS = 4 * SCALE, RUBBLE = 9;
	/** How uneven the ground under it may be (blocks). */
	static final int FIT_ROUGHNESS = 6;
	/**
	 * (0.7.262) Andy: "they are only mineable with high teir tinker tools". Its stone, bands and core break only to a
	 * Jaymo's Powers Tinkers tool of this mining tier or better (amethyst bronze, pig iron, slimesteel, steel heads).
	 */
	static final String TINKERS_TIER_TAG = "incorrect_for_diamond_tool";
	/** How far round its foot (blocks, level) and how high above it its own blocks reach. */
	static final int PART_RADIUS = 8, PART_HEIGHT = (PILLAR_MOST + CORE_GAP + 3) * SCALE;
	/** The world tier the first Giant's fall raises the world to (1: Borderlands). */
	static final int FIRST_GIANT_TIER = 1;

	/** Its hum, before a Giant can wake: how loud, and how low. */
	static final float HUM_VOLUME = 2.0F, HUM_PITCH = 0.5F;

	public static final int AWAKE = 0, CLAIMED = 1;

	/** One monolith: where (its foot), its element, whether its Giant has fallen, and whether we raised it (else Ancient Remnants did). */
	public record Monolith(BlockPos pos, Element element, int state, boolean ours) {
		static final Codec<Monolith> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(m -> m.pos().asLong()),
			Codec.INT.optionalFieldOf("element", 0).forGetter(m -> m.element().ordinal()),
			Codec.INT.optionalFieldOf("state", AWAKE).forGetter(Monolith::state),
			Codec.BOOL.optionalFieldOf("ours", true).forGetter(Monolith::ours)
		).apply(i, (pos, element, state, ours) -> new Monolith(BlockPos.of(pos), Element.byOrdinal(element), state, ours)));

		Monolith claimed() {
			return new Monolith(this.pos, this.element, CLAIMED, this.ours);
		}
	}

	/** Every monolith known (raised by us or found of Ancient Remnants'), on the Overworld. */
	static final AttachmentType<List<Monolith>> MONOLITHS = AttachmentRegistry.create(JaymosKingdoms.id("monoliths"),
		builder -> builder.persistent(Monolith.CODEC.listOf()));
	/** Whether a Giant has fallen in this world yet (the world tier rose with it). On the Overworld. */
	static final AttachmentType<Boolean> FIRST_FALLEN = AttachmentRegistry.create(JaymosKingdoms.id("first_giant_fallen"),
		builder -> builder.persistent(Codec.BOOL));

	/** Whether the first of the Aether's Giants, the world's first world boss, has fallen in this world (on the Overworld). */
	public static boolean firstFallen(ServerLevel overworld) {
		return overworld.getAttachedOrElse(FIRST_FALLEN, false);
	}

	private static final Identifier VANILLA_GIANT_SCALE = JaymosKingdoms.id("vanilla_giant_scale");
	/** Jaymo's Powers' Tinkers tools carry their parts' materials in this component. */
	private static final Identifier TINKER_MATERIALS = Identifier.fromNamespaceAndPath("jaymospowers", "tool_materials");

	public static final Map<Element, EntityType<AetherGiant>> GIANTS = new EnumMap<>(Element.class);

	private Monoliths() {
	}

	public static void register() {
		for (Element element : Element.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(element.key() + "_giant"));
			EntityType<AetherGiant> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<AetherGiant>of((t, l) -> new AetherGiant(t, l, element), MobCategory.MONSTER).sized(AetherGiant.WIDTH, AetherGiant.HEIGHT)
					.clientTrackingRange(12).build(key));
			GIANTS.put(element, type);
			FabricDefaultAttributeRegistry.register(type, AetherGiant.createAttributes());
		}
		ServerTickEvents.END_SERVER_TICK.register(Monoliths::tick);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> breaking(level, player, pos, state));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AetherGiant giant) {
				giant.dropBar();
				if (entity.level() instanceof ServerLevel level) {
					slain(level, giant);
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> sizeVanillaGiant(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof AetherGiant giant) {
				giant.dropBar();
			}
		});
		AncientRemnantsCompat.register();
	}

	/** Whether {@code entity} is one of the Aether's Giants. */
	public static boolean giant(@Nullable Object entity) {
		return entity instanceof AetherGiant;
	}

	static List<Monolith> all(ServerLevel level) {
		return level.getAttachedOrElse(MONOLITHS, List.of());
	}

	/**
	 * (0.7.214) Whether a monolith of {@code element} stands within {@code range} of {@code pos} on {@code level} (only the
	 * Overworld has them), its Giant fallen or not: the remnant still seeps into the bloods born near it
	 * ({@code HeritageVariants}).
	 */
	public static boolean near(ServerLevel level, BlockPos pos, Element element, double range) {
		if (level != level.getServer().overworld()) {
			return false;
		}
		for (Monolith m : all(level)) {
			if (m.element() == element && m.pos().distSqr(pos) <= range * range) {
				return true;
			}
		}
		return false;
	}

	/** Remembers {@code monolith} (or, if one is already known at its spot, replaces it). */
	static void remember(ServerLevel level, Monolith monolith) {
		List<Monolith> all = new ArrayList<>(all(level));
		all.removeIf(m -> m.pos().equals(monolith.pos()));
		all.add(monolith);
		level.setAttached(MONOLITHS, List.copyOf(all));
	}

	/** The known monolith nearest {@code pos} within {@code range} (horizontally), or null. */
	static @Nullable Monolith nearest(ServerLevel level, BlockPos pos, double range) {
		Monolith best = null;
		double bestD = range * range;
		for (Monolith m : all(level)) {
			double d = horizontal(m.pos(), pos);
			if (d <= bestD) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	/** (0.7.216) The foot of the known monolith nearest {@code pos} on {@code level}, or null with none known (a life story's start, {@code LifeStory}). */
	public static @Nullable BlockPos nearestFoot(ServerLevel level, BlockPos pos) {
		Monolith m = nearest(level, pos, Double.MAX_VALUE / 4.0);
		return m == null ? null : m.pos();
	}

	static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// ---- the tick: Giants woken, monoliths raised --------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = Factions.level(server);
		if (now % CHECK_TICKS == 0) {
			wake(level);
		}
		if (now % RAISE_TICKS == 0) {
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (random.nextFloat() < RAISE_CHANCE && Encounters.inTheWild(server, level, player)
					&& nearest(level, player.blockPosition(), SPACING) == null) {
					raiseNear(level, player, random);
				}
			}
		}
		if (now % AncientRemnantsCompat.SCAN_TICKS == 0) {
			AncientRemnantsCompat.scan(level);
		}
	}

	/** Each awake monolith a player has come near, whose Giant isn't about: its Giant rises. */
	private static void wake(ServerLevel level) {
		List<Monolith> all = all(level);
		// on Peaceful a Giant would vanish as it rose, and rise again
		if (all.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		for (Monolith m : all) {
			if (m.state() != AWAKE || !level.isPositionEntityTicking(m.pos())) {
				continue;
			}
			ServerPlayer near = null;
			boolean anyone = false;
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && !player.isCreative() && horizontal(player.blockPosition(), m.pos()) < WAKE_RANGE * WAKE_RANGE) {
					anyone = true;
					if (WorldTiers.of(player).ordinal() >= WAKE_TIER.ordinal()) {
						near = player;
						break;
					}
				}
			}
			if (near == null) {
				if (anyone) {
					hum(level, m);
				}
				continue;
			}
			if (giantOf(level, m) != null) {
				continue;
			}
			rise(level, m);
		}
	}

	/** {@code m} hums, its Giant still asleep (no one near is in the world tier to wake it): a low drone and its motes. */
	static void hum(ServerLevel level, Monolith m) {
		BlockPos pos = m.pos();
		level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, HUM_VOLUME, HUM_PITCH);
		level.sendParticles(m.element().mote, pos.getX() + 0.5, pos.getY() + PILLAR_LEAST, pos.getZ() + 0.5, 6, 0.6, 1.0, 0.6, 0.01);
	}

	/** The living Giant bound to {@code m}, if one is about. */
	static @Nullable AetherGiant giantOf(ServerLevel level, Monolith m) {
		List<AetherGiant> found = level.getEntitiesOfClass(AetherGiant.class, new AABB(m.pos()).inflate(GIANT_SEARCH),
			g -> g.isAlive() && m.pos().equals(g.monolith()));
		return found.isEmpty() ? null : found.getFirst();
	}

	/** (0.7.152) The vanilla Giant grown to the Aether's Giants' height ({@link AetherGiant#HEIGHT}), a Great Ape's. */
	static void sizeVanillaGiant(Entity entity) {
		if (entity instanceof Giant giant) {
			AttributeInstance scale = giant.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.addOrUpdateTransientModifier(new AttributeModifier(VANILLA_GIANT_SCALE,
					AetherGiant.HEIGHT / giant.getType().getDimensions().height() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	/** {@code m}'s Giant rises beside it, and everyone near hears it; null if there was no ground for it. */
	static @Nullable AetherGiant rise(ServerLevel level, Monolith m) {
		RandomSource random = level.getRandom();
		BlockPos spot = null;
		for (int i = 0; i < SPOT_TRIES && spot == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = RISE_NEAREST + random.nextInt(RISE_FURTHEST - RISE_NEAREST + 1);
			BlockPos column = m.pos().offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(ground.below()).isEmpty() && Math.abs(ground.getY() - m.pos().getY()) < 10) {
				spot = ground;
			}
		}
		if (spot == null) {
			return null;
		}
		AetherGiant giant = GIANTS.get(m.element()).create(level, EntitySpawnReason.EVENT);
		if (giant == null) {
			return null;
		}
		giant.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		giant.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		giant.setPersistenceRequired();
		giant.bind(m.pos());
		giant.setHomeTo(m.pos(), HOME_RADIUS);
		level.addFreshEntity(giant);
		level.sendParticles(m.element().mote, spot.getX() + 0.5, spot.getY() + 2.0, spot.getZ() + 0.5, 120, 1.5, 3.0, 1.5, 0.05);
		level.playSound(null, spot, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0F, 0.6F);
		level.playSound(null, m.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.5F);
		Component word = Component.translatable("message.jaymoskingdoms.giant.rises", giant.getDisplayName()).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.players()) {
			if (horizontal(player.blockPosition(), m.pos()) < GIANT_SEARCH * GIANT_SEARCH) {
				player.sendOverlayMessage(word);
			}
		}
		return giant;
	}

	/** A Giant has fallen: its monolith goes dark for good; the first in the world raises the world tier. */
	static void slain(ServerLevel level, AetherGiant giant) {
		BlockPos bound = giant.monolith();
		ServerLevel overworld = Factions.level(level.getServer());
		if (bound != null && level == overworld) {
			for (Monolith m : all(level)) {
				if (m.pos().equals(bound) && m.state() == AWAKE) {
					remember(level, m.claimed());
					if (m.ours()) {
						darken(level, m);
					}
				}
			}
		}
		if (overworld.getAttachedOrElse(FIRST_FALLEN, false)) {
			return;
		}
		overworld.setAttached(FIRST_FALLEN, true);
		MinecraftServer server = level.getServer();
		WorldTiers.Tier raised = WorldTiers.Tier.byOrdinal(Math.max(FIRST_GIANT_TIER, WorldTiers.floor(server)));
		com.jaymoskingdoms.Post.news(server, Component.translatable("message.jaymoskingdoms.giant.first_fallen",
			giant.getDisplayName(), raised.title().copy().withColor(raised.color)).withStyle(ChatFormatting.LIGHT_PURPLE));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 0.6F, 0.7F);
		}
		WorldTiers.raiseFloor(server, FIRST_GIANT_TIER);
	}

	// ---- raising one -------------------------------------------------------------------------------------------------

	/** A monolith raised somewhere out of sight a little way from {@code player}; false if no ground could be found. */
	static boolean raiseNear(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < SPOT_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = RAISE_NEAREST + random.nextInt(RAISE_FURTHEST - RAISE_NEAREST + 1);
			BlockPos column = player.blockPosition().offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos foot = fit(level, column);
			if (foot == null || Factions.around(level.getServer(), level, foot, SETTLEMENT_CLEARANCE) != null) {
				continue;
			}
			raise(level, foot, Element.of(level.getBiome(foot)), random);
			return true;
		}
		return false;
	}

	/** Where a monolith may stand at {@code column}: open natural ground, not water, fairly level; else null. */
	public static @Nullable BlockPos fit(ServerLevel level, BlockPos column) {
		BlockPos foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		int least = Integer.MAX_VALUE, most = Integer.MIN_VALUE;
		for (int dx = -GROUND_RADIUS; dx <= GROUND_RADIUS; dx += GROUND_RADIUS) {
			for (int dz = -GROUND_RADIUS; dz <= GROUND_RADIUS; dz += GROUND_RADIUS) {
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.offset(dx, 0, dz));
				if (!level.getFluidState(at.below()).isEmpty() || !natural(level.getBlockState(at.below()))) {
					return null;
				}
				least = Math.min(least, at.getY());
				most = Math.max(most, at.getY());
			}
		}
		return most - least <= FIT_ROUGHNESS ? foot : null;
	}

	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
			|| state.is(Blocks.CLAY) || state.is(BlockTags.TERRACOTTA);
	}

	/**
	 * Builds a monolith of {@code element} with its foot at {@code foot}, and remembers it: a ring of its ground, a 3x3
	 * pillar of its stone banded every few blocks, narrowing to one block and broken off at the top, its core floating
	 * over the break with four shards round it, and broken pieces fallen about its foot. (0.7.262) Every one of those
	 * blocks is {@link #SCALE} blocks a side, of the Aether's own stone ({@link Element#stone}).
	 */
	public static Monolith raise(ServerLevel level, BlockPos foot, Element element, RandomSource random) {
		int height = PILLAR_LEAST + random.nextInt(PILLAR_MOST - PILLAR_LEAST + 1);
		BlockState stone = element.stone().defaultBlockState(), band = element.band().defaultBlockState();
		// the ground round its foot, its own
		for (int dx = -GROUND_RADIUS; dx <= GROUND_RADIUS; dx++) {
			for (int dz = -GROUND_RADIUS; dz <= GROUND_RADIUS; dz++) {
				if (dx * dx + dz * dz > GROUND_RADIUS * GROUND_RADIUS + 1 || random.nextInt(5) == 0) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.offset(dx, 0, dz)).below();
				if (natural(level.getBlockState(top))) {
					set(level, top, element.ground().defaultBlockState());
				}
			}
		}
		// the pillar: 3x3, then a 1x1 crown, broken off (each of its blocks SCALE across, the middle one over the foot)
		int narrow = height - 4;
		for (int y = 0; y < height; y++) {
			BlockState here = y % BAND_EVERY == BAND_EVERY - 1 ? band : stone;
			int r = y < narrow ? 1 : 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					// the top rows of the broad part crumble at the corners
					boolean crumbled = r == 1 && y >= narrow - 2 && Math.abs(dx) + Math.abs(dz) == 2 && random.nextBoolean();
					cube(level, foot, dx, y, dz, crumbled ? Blocks.AIR.defaultBlockState() : here);
				}
			}
		}
		// the core, floating over the break, and its four shards
		for (int y = height; y < height + CORE_GAP; y++) {
			cube(level, foot, 0, y, 0, Blocks.AIR.defaultBlockState());
		}
		cube(level, foot, 0, height + CORE_GAP, 0, element.core().defaultBlockState());
		cube(level, foot, 2, height + CORE_GAP - 1, 2, band);
		cube(level, foot, -2, height + CORE_GAP, 2, band);
		cube(level, foot, 2, height + CORE_GAP + 1, -2, band);
		cube(level, foot, -2, height + CORE_GAP - 1, -2, band);
		BlockPos core = foot.above((height + CORE_GAP) * SCALE + SCALE / 2);
		// broken pieces about its foot, a few blocks each, sunk into the ground
		for (int i = 0; i < RUBBLE; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = (3 + random.nextInt(3)) * SCALE;
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.offset(Mth.floor(Math.cos(angle) * r), 0,
				Mth.floor(Math.sin(angle) * r)));
			BlockState piece = random.nextInt(3) == 0 ? band : stone;
			int size = 1 + random.nextInt(SCALE);
			for (BlockPos p : BlockPos.betweenClosed(at.offset(0, -1, 0), at.offset(size - 1, size - 1, size - 1))) {
				set(level, p, piece);
			}
		}
		level.sendParticles(element.mote, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, 120, 3.0, 3.0, 3.0, 0.02);
		Monolith m = new Monolith(foot.immutable(), element, AWAKE, true);
		remember(level, m);
		JaymosKingdoms.LOGGER.info("A {} monolith rose at {}", element.key(), foot);
		return m;
	}

	/** One of the monolith's own blocks, {@code (x, y, z)} of them from its foot: a cube {@link #SCALE} blocks a side. */
	private static void cube(ServerLevel level, BlockPos foot, int x, int y, int z, BlockState state) {
		int half = SCALE / 2;
		BlockPos corner = foot.offset(x * SCALE - half, y * SCALE, z * SCALE - half);
		for (BlockPos p : BlockPos.betweenClosed(corner, corner.offset(SCALE - 1, SCALE - 1, SCALE - 1))) {
			set(level, p, state);
		}
	}

	/** Whether {@code pos} is within the monolith standing at {@code foot}: its pillar, crown, shards and core. */
	public static boolean part(BlockPos foot, BlockPos pos) {
		return Math.abs(pos.getX() - foot.getX()) <= PART_RADIUS && Math.abs(pos.getZ() - foot.getZ()) <= PART_RADIUS
			&& pos.getY() >= foot.getY() - 1 && pos.getY() <= foot.getY() + PART_HEIGHT;
	}

	/**
	 * (0.7.262) Its stone, bands and core break only to a high-tier Tinkers tool (one that harvests what a diamond tool
	 * does, {@link #TINKERS_TIER_TAG}); anything else is told so and the block stands. Creative players break it as ever.
	 */
	private static boolean breaking(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, BlockPos pos,
		BlockState state) {
		if (!(level instanceof ServerLevel server) || player.isCreative() || server != Factions.level(server.getServer())) {
			return true;
		}
		for (Monolith m : all(server)) {
			if (m.ours() && part(m.pos(), pos) && m.element().builtOf(state)) {
				if (highTinkers(player.getMainHandItem())) {
					return true;
				}
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monolith.needs_tinkers").withStyle(ChatFormatting.GRAY));
				return false;
			}
		}
		return true;
	}

	/** Whether {@code stack} is one of Jaymo's Powers' Tinkers mining tools of {@link #TINKERS_TIER_TAG}'s tier or better. */
	static boolean highTinkers(net.minecraft.world.item.ItemStack stack) {
		net.minecraft.core.component.DataComponentType<?> materials = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(TINKER_MATERIALS);
		net.minecraft.world.item.component.Tool tool = stack.get(net.minecraft.core.component.DataComponents.TOOL);
		if (materials == null || tool == null || !stack.has(materials)) {
			return false;
		}
		// a Tinkers mining tool is denied the drops of the blocks past its tier: one denied nothing past diamond is diamond or better
		for (net.minecraft.world.item.component.Tool.Rule rule : tool.rules()) {
			if (rule.correctForDrops().isPresent() && !rule.correctForDrops().get()) {
				return rule.blocks().unwrapKey().map(key -> {
					String path = key.location().getPath();
					return path.equals(TINKERS_TIER_TAG) || path.equals("incorrect_for_netherite_tool");
				}).orElse(false);
			}
		}
		return false;
	}

	/** Its Giant fallen, the monolith's core goes out: the floating core is gone, the shards left hanging. */
	private static void darken(ServerLevel level, Monolith m) {
		// (0.7.262) its core a cube now (and the single block of one raised before)
		BlockPos out = null;
		int half = SCALE / 2;
		for (BlockPos at : BlockPos.betweenClosed(m.pos().offset(-half, 1, -half), m.pos().offset(half, PART_HEIGHT, half))) {
			BlockState state = level.getBlockState(at);
			if (state.is(m.element().core()) || state.is(m.element().vanilla[Element.CORE])) {
				set(level, at, Blocks.AIR.defaultBlockState());
				out = at.immutable();
			}
		}
		if (out != null) {
			level.playSound(null, out, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0F, 0.5F);
		}
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	// ---- for ops -------------------------------------------------------------------------------------------------------

	/** {@code /kingdom monolith raise [element]|list}, for ops. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		var raise = Commands.literal("raise").executes(c -> raiseHere(c.getSource(), null));
		for (Element element : Element.values()) {
			raise.then(Commands.literal(element.key()).executes(c -> raiseHere(c.getSource(), element)));
		}
		return Commands.literal("monolith").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(raise)
			.then(Commands.literal("list").executes(c -> {
				ServerLevel level = Factions.level(c.getSource().getServer());
				List<Monolith> all = all(level);
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.monolith.count", all.size(),
					level.getAttachedOrElse(FIRST_FALLEN, false) ? Component.translatable("gui.yes") : Component.translatable("gui.no")), false);
				for (Monolith m : all) {
					c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.monolith.entry", m.element().title(),
						m.pos().getX(), m.pos().getY(), m.pos().getZ(), Component.translatable(m.state() == AWAKE
							? "command.jaymoskingdoms.monolith.awake" : "command.jaymoskingdoms.monolith.claimed")), false);
				}
				return all.size();
			}));
	}

	/** A monolith raised a few blocks in front of the op (its element by the biome unless given). */
	private static int raiseHere(CommandSourceStack source, @Nullable Element element) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = Factions.level(source.getServer());
		if (player.level() != level) {
			source.sendFailure(Component.translatable("command.jaymoskingdoms.monolith.overworld"));
			return 0;
		}
		BlockPos column = BlockPos.containing(player.position().add(player.getLookAngle().multiply(12.0, 0.0, 12.0)));
		BlockPos foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		Element e = element != null ? element : Element.of(level.getBiome(foot));
		Monolith m = raise(level, foot, e, level.getRandom());
		source.sendSuccess(() -> Component.translatable("command.jaymoskingdoms.monolith.raised", e.title(), m.pos().getX(), m.pos().getY(),
			m.pos().getZ()), true);
		return 1;
	}
}
