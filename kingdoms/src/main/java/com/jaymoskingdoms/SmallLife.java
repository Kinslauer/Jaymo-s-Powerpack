package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Critter;
import com.jaymoskingdoms.monsters.Critter.Kind;
import com.jaymoskingdoms.monsters.MocWild;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Small life everywhere (Reference Mods patch 7, Mo' Creatures' crickets, snails, flies, maggots, roaches, mice and moles,
 * {@link Critter}). Each comes for a reason the world gives it, in small numbers, and goes when the reason does:
 * <ul>
 * <li><b>Crickets</b> chirp in the grass of warm lands at night, and are gone at dawn.</li>
 * <li><b>Snails</b> come out after rain onto grass and earth, draw into their shells when you come near, nibble the short
 * grass at their feet, and go back under when the ground dries.</li>
* <li><b>Flies</b> come to a body left rotting a while, or to a pile of the dead (Jaymo's Kitchen's corpses, {@link KitchenCompat}) and <b>maggots</b> to one left
 * longer, and the maggots eat it ({@link Devouring#bite}), so a body left out is soon gone. A body being seen to (a
 * bearer's duty, {@link Bearers#claimed}) or kept by a rite or a wrap ({@link BodyKeeping#isPreserved}) draws none: the
 * stewardship is burying the dead.</li>
 * <li><b>Roaches</b> come to a filthy home: blood lying indoors (Powers' stains, {@link PowersCompat#bloodStains}) or rotten
 * flesh on the floor; they go when it is scrubbed away ({@code BloodChores}), so the villagers' scrubbing is what keeps them out.</li>
 * <li><b>Mice</b> come to a grain store (a chest or a barrel with {@link #GRAIN_MIN} or more of grain in it) and take a
 * grain now and then; a cat about the place (any cat within {@link #CAT_KEEPS} blocks) keeps them away and catches the ones
 * that come, so the stewardship is keeping a cat ({@link CatKeeping}).</li>
 * <li><b>Moles</b> come to a field (farmland all round), heap a hill of earth now and then (at most {@link #HILLS} each) and
 * work the ground so a crop beside them grows a little faster: the hills are a small price for it, and a smashed hill is
 * just earth.</li>
 * </ul>
 * Cheap by design: each player's surroundings are looked at every {@link #SPAWN_EVERY} ticks, each kind has a cap near them,
 * and each small thing lives only {@code Critter#LIFE_TICKS} at most and is dropped as soon as nothing wants it.
 */
public final class SmallLife {
	/** How often a creature thinks for itself (ticks); how often a player's surroundings are looked at (ticks). */
	public static final int EVERY = 40;
	static final int SPAWN_EVERY = 100;
	/** How far round a player the small life is brought and counted (blocks, horizontally). */
	static final int AROUND = 44, NEAR_MIN = 5, NEAR_MAX = 18;
	/** The most of each near a player. */
	static final int CRICKET_CAP = 5, SNAIL_CAP = 3, ROACH_CAP = 3, MOUSE_CAP = 2, MOLE_CAP = 1;
	/**
	 * The most flies and maggots at one body, and near a player all told (Andy, 2026-11-25: "them and maggots should
	 * spawn in less number").
	 */
	static final int FLIES_AT_BODY = 1, MAGGOTS_AT_BODY = 1, FLIES_NEAR = 4, MAGGOTS_NEAR = 4;
	/**
	 * Flies come only to a body that has lain rotting {@link #FLY_AFTER} ticks (3 minutes, Kitchen's rot clock, see
	 * {@link KitchenCompat#decayTicks}), or to a pile: more than {@link #PILE_MORE_THAN} bodies within {@link #PILE_REACH}
	 * blocks across ({@link #PILE_UP} up and down) of it, fresh or not. Tiny bodies (a dead fly, a mouse, under
	 * {@link #TINY} blocks) make no pile. Andy, 2026-11-28: "they should only come around bodies that are decaying for over
	 * 3m, and to piles of over 6 bodies." Maggots still come {@link #MAGGOT_AFTER} ticks after death.
	 */
	static final long FLY_AFTER = 3600L, MAGGOT_AFTER = 12000L;
	static final int PILE_MORE_THAN = 6;
	static final double PILE_REACH = 4.0, PILE_UP = 2.0, TINY = 0.5;
	/** How long after rain the snails are out (ticks); the night's hours (day time). */
	static final long SNAIL_WINDOW = 6000L;
	static final int NIGHT_FROM = 13000, NIGHT_TO = 23000;
	/** The coolest a land can be and still have crickets. */
	static final float CRICKET_WARM = 0.4F;
	/** A snail hides when someone is this near (blocks). */
	static final double HIDE_NEAR = 3.0;
	/** Mice: the grain that makes a store, the grain a mouse takes at a time, the chance each think it does, how far a cat keeps them off (blocks), how near it must be to take. */
	static final int GRAIN_MIN = 24;
	static final float NIBBLE_CHANCE = 0.3F;
	static final double CAT_KEEPS = 24.0, NIBBLE_REACH = 2.5, CATCH_REACH = 1.6, STALK = 10.0;
	/** Moles: how much farmland (blocks within {@link #FIELD_REACH}) makes a field, how many hills each heaps, the chance each think, how much it works a crop. */
	static final int FIELD_MIN = 6, HILLS = 3;
	static final double FIELD_REACH = 4.0, DIG_REACH = 3.0;
	static final float DIG_CHANCE = 0.4F;
	/** Roaches: how near to its stain it stays, how near a stain a new one comes (blocks). */
	static final double FILTH_REACH = 5.0;
	/** The grain a mouse wants. */
	static final List<net.minecraft.world.item.Item> GRAIN = List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BREAD);

	/** The last game time it rained in each dimension: the snails come after. */
	private static final Map<ResourceKey<Level>, Long> RAINED = new HashMap<>();

	private SmallLife() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SmallLife::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RAINED.clear());
	}

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.isRaining()) {
				RAINED.put(level.dimension(), level.getGameTime());
			}
		}
		if (ticks % SPAWN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && player.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
				bring(level, player);
			}
		}
	}

	// ---- bringing them ------------------------------------------------------------------------------------------------

	private static void bring(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(AROUND, 24, AROUND);
		Map<Kind, Integer> have = new EnumMap<>(Kind.class);
		for (Critter c : level.getEntitiesOfClass(Critter.class, box)) {
			have.merge(c.kind, 1, Integer::sum);
		}
		RandomSource random = level.getRandom();
		BlockPos at = player.blockPosition();
		long now = level.getGameTime();
		long dayTime = level.getOverworldClockTime() % 24000L;
		boolean night = dayTime >= NIGHT_FROM && dayTime <= NIGHT_TO && !level.isRaining();
		if (night && have.getOrDefault(Kind.CRICKET, 0) < CRICKET_CAP) {
			BlockPos spot = groundNear(level, at, random);
			if (spot != null && level.getBlockState(spot.below()).is(Blocks.GRASS_BLOCK) && level.getBiome(spot).value().getBaseTemperature() >= CRICKET_WARM
				&& !level.getBiome(spot).is(BiomeTags.IS_OCEAN)) {
				put(level, MocWild.CRICKET, spot, null);
			}
		}
		Long rained = RAINED.get(level.dimension());
		if (rained != null && !level.isRaining() && now - rained <= SNAIL_WINDOW && have.getOrDefault(Kind.SNAIL, 0) < SNAIL_CAP) {
			BlockPos spot = groundNear(level, at, random);
			if (spot != null && snailGround(level.getBlockState(spot.below())) && !level.getBiome(spot).is(BiomeTags.IS_BADLANDS)) {
				put(level, MocWild.SNAIL, spot, null);
			}
		}
		bodies(level, box, at);
		if (have.getOrDefault(Kind.ROACH, 0) < ROACH_CAP) {
			filth(level, at);
		}
		if (have.getOrDefault(Kind.MOUSE, 0) < MOUSE_CAP) {
			stores(level, at);
		}
		if (have.getOrDefault(Kind.MOLE, 0) < MOLE_CAP) {
			BlockPos spot = groundNear(level, at, random);
			if (spot != null && level.getBlockState(spot.below()).is(Blocks.GRASS_BLOCK) && farmland(level, spot) >= FIELD_MIN) {
				put(level, MocWild.MOLE, spot, spot);
			}
		}
	}

	private static boolean snailGround(BlockState below) {
		return below.is(Blocks.GRASS_BLOCK) || below.is(BlockTags.DIRT) || below.is(Blocks.MOSS_BLOCK);
	}

	/** A spot of open ground at a random distance from {@code around}: the air above its surface. */
	private static @Nullable BlockPos groundNear(ServerLevel level, BlockPos around, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = NEAR_MIN + random.nextInt(NEAR_MAX - NEAR_MIN);
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance)));
		if (Math.abs(spot.getY() - around.getY()) > 12 || !level.getBlockState(spot).isAir() || !level.getFluidState(spot).isEmpty()) {
			return null;
		}
		return spot;
	}

	private static @Nullable Critter put(ServerLevel level, EntityType<Critter> type, BlockPos at, @Nullable BlockPos anchor) {
		Critter c = type.create(level, EntitySpawnReason.EVENT);
		if (c == null) {
			return null;
		}
		c.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		c.setAnchor(anchor);
		c.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(c);
		return c;
	}

	/** Flies, then maggots, at the bodies lying near the player that no one is seeing to. */
	private static void bodies(ServerLevel level, AABB box, BlockPos around) {
		if (!KitchenCompat.corpsesExist()) {
			return;
		}
		int fliesNear = 0, maggotsNear = 0;
		for (Critter c : level.getEntitiesOfClass(Critter.class, box)) {
			if (c.kind == Kind.FLY) {
				fliesNear++;
			} else if (c.kind == Kind.MAGGOT) {
				maggotsNear++;
			}
		}
		for (Entity body : level.getEntities((Entity) null, box, KitchenCompat::isCorpse)) {
			if (fliesNear >= FLIES_NEAR && maggotsNear >= MAGGOTS_NEAR) {
				return;
			}
			if (Bearers.claimed(body.getUUID()) || BodyKeeping.isPreserved(body) || body.isPassenger()) {
				continue;
			}
			long since = KitchenCompat.sinceDeath(body);
			boolean fly = KitchenCompat.decayTicks(body) >= FLY_AFTER || pile(level, body);
			if (!fly && since < MAGGOT_AFTER) {
				continue;
			}
			BlockPos at = body.blockPosition();
			AABB near = new AABB(at).inflate(5.0, 3.0, 5.0);
			int flies = 0;
			int maggots = 0;
			for (Critter c : level.getEntitiesOfClass(Critter.class, near)) {
				if (c.kind == Kind.FLY) {
					flies++;
				} else if (c.kind == Kind.MAGGOT) {
					maggots++;
				}
			}
			if (fly && flies < FLIES_AT_BODY && fliesNear < FLIES_NEAR && put(level, MocWild.FLY, at.above(), at) != null) {
				fliesNear++;
			}
			if (since >= MAGGOT_AFTER && maggots < MAGGOTS_AT_BODY && maggotsNear < MAGGOTS_NEAR && put(level, MocWild.MAGGOT, at, at) != null) {
				maggotsNear++;
			}
		}
	}

	/** Whether {@code body} lies in a pile: more than {@link #PILE_MORE_THAN} bodies (itself among them) close round it. */
	private static boolean pile(ServerLevel level, Entity body) {
		AABB round = body.getBoundingBox().inflate(PILE_REACH, PILE_UP, PILE_REACH);
		return level.getEntities((Entity) null, round, e -> KitchenCompat.isCorpse(e) && !tiny(e)).size() > PILE_MORE_THAN;
	}

	/** A body too small to count towards a pile. */
	private static boolean tiny(Entity body) {
		return body.getBbWidth() <= TINY && body.getBbHeight() < TINY;
	}

	/** Roaches at blood on a settlement's floor, or at rotten flesh lying indoors. */
	private static void filth(ServerLevel level, BlockPos around) {
		for (Vec3 stain : PowersCompat.bloodStains(level, Vec3.atCenterOf(around), AROUND, 8)) {
			BlockPos at = BlockPos.containing(stain.x, stain.y + 0.1, stain.z);
			if (Strays.indoors(level, at) && level.getBlockState(at).isAir() && roachesAt(level, at) < 2) {
				put(level, MocWild.ROACH, at, at);
				return;
			}
		}
		for (ItemEntity rot : level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(AROUND, 12, AROUND), i -> i.getItem().is(Items.ROTTEN_FLESH))) {
			BlockPos at = rot.blockPosition();
			if (Strays.indoors(level, at) && roachesAt(level, at) < 2) {
				put(level, MocWild.ROACH, at, at);
				return;
			}
		}
	}

	private static int roachesAt(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(Critter.class, new AABB(at).inflate(FILTH_REACH * 2), c -> c.kind == Kind.ROACH).size();
	}

	/** Whether there is filth within {@link #FILTH_REACH} of {@code at}: blood on the floor or rotten flesh. */
	private static boolean filthy(ServerLevel level, BlockPos at) {
		return !PowersCompat.bloodStains(level, Vec3.atCenterOf(at), FILTH_REACH, 1).isEmpty()
			|| !level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(FILTH_REACH), i -> i.getItem().is(Items.ROTTEN_FLESH)).isEmpty();
	}

	/** Mice at the grain stores of the chunks round the player that no cat is keeping. */
	private static void stores(ServerLevel level, BlockPos around) {
		int cx = around.getX() >> 4;
		int cz = around.getZ() >> 4;
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = e.getValue();
					if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && grain((Container) be) >= GRAIN_MIN && !catKeeps(level, e.getKey())
						&& level.getEntitiesOfClass(Critter.class, new AABB(e.getKey()).inflate(6.0), c -> c.kind == Kind.MOUSE).isEmpty()) {
						BlockPos floor = e.getKey();
						for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
							BlockPos beside = floor.relative(side);
							if (level.getBlockState(beside).isAir()) {
								put(level, MocWild.MOUSE, beside, e.getKey());
								return;
							}
						}
					}
				}
			}
		}
	}

	private static int grain(Container store) {
		int n = 0;
		for (int i = 0; i < store.getContainerSize(); i++) {
			ItemStack s = store.getItem(i);
			if (GRAIN.contains(s.getItem())) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean catKeeps(ServerLevel level, BlockPos at) {
		return !level.getEntitiesOfClass(Cat.class, new AABB(at).inflate(CAT_KEEPS), Cat::isAlive).isEmpty();
	}

	private static int farmland(ServerLevel level, BlockPos at) {
		int n = 0;
		int r = (int) FIELD_REACH;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
			if (level.getBlockState(p).is(Blocks.FARMLAND)) {
				n++;
			}
		}
		return n;
	}

	// ---- what each does, and when it goes -----------------------------------------------------------------------------

	/** One think of {@code c}: still wanted, and what it does. */
	public static void act(ServerLevel level, Critter c) {
		BlockPos at = c.blockPosition();
		long dayTime = level.getOverworldClockTime() % 24000L;
		RandomSource random = level.getRandom();
		switch (c.kind) {
			case CRICKET -> {
				if (level.isBrightOutside() && dayTime < NIGHT_FROM) {
					leave(level, c);
				} else if (random.nextFloat() < 0.5F) {
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.NOTE_BLOCK_HAT, SoundSource.AMBIENT, 0.25F, 2.0F);
				}
			}
			case SNAIL -> {
				Long rained = RAINED.get(level.dimension());
				if (rained == null || level.getGameTime() - rained > SNAIL_WINDOW + 3000L) {
					leave(level, c);
					return;
				}
				c.setHiding(c.coat() < 5 && !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, c.getBoundingBox().inflate(HIDE_NEAR), p -> !p.isSpectator()).isEmpty());
				if (!c.hiding() && random.nextFloat() < 0.1F && level.getBlockState(at).is(Blocks.SHORT_GRASS)) {
					// it eats the short grass it sits in
					level.destroyBlock(at, false);
				}
			}
			case FLY -> {
				BlockPos body = c.anchor();
				if (body == null || !bodyNear(level, body)) {
					leave(level, c);
				}
			}
			case MAGGOT -> {
				BlockPos body = c.anchor();
				Entity corpse = body == null ? null : corpseAt(level, body);
				if (corpse == null) {
					leave(level, c);
				} else if (c.distanceToSqr(corpse) < 4.0 && random.nextFloat() < 0.3F) {
					Devouring.bite(level, c, corpse);
				}
			}
			case ROACH -> {
				BlockPos spot = c.anchor();
				if (spot == null || !filthy(level, spot)) {
					leave(level, c);
				}
			}
			case MOUSE -> mouse(level, c);
			case MOLE -> mole(level, c);
		}
	}

	/** Away: a puff, and it is gone. */
	private static void leave(ServerLevel level, Critter c) {
		level.sendParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.1, c.getZ(), 2, 0.1, 0.05, 0.1, 0.0);
		c.discard();
	}

	private static boolean bodyNear(ServerLevel level, BlockPos at) {
		return corpseAt(level, at) != null;
	}

	private static @Nullable Entity corpseAt(ServerLevel level, BlockPos at) {
		if (!KitchenCompat.corpsesExist()) {
			return null;
		}
		for (Entity e : level.getEntities((Entity) null, new AABB(at).inflate(3.0), KitchenCompat::isCorpse)) {
			if (!Bearers.claimed(e.getUUID()) && !BodyKeeping.isPreserved(e)) {
				return e;
			}
		}
		return null;
	}

	/** A mouse takes a grain from its store; a cat near enough catches it; with a cat keeping the place or the grain gone, it leaves. */
	private static void mouse(ServerLevel level, Critter c) {
		BlockPos store = c.anchor();
		BlockEntity be = store == null ? null : level.getBlockEntity(store);
		if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) || grain((Container) be) == 0) {
			leave(level, c);
			return;
		}
		for (Cat cat : level.getEntitiesOfClass(Cat.class, c.getBoundingBox().inflate(STALK), Cat::isAlive)) {
			if (cat.distanceToSqr(c) <= CATCH_REACH * CATCH_REACH) {
				level.sendParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.1, c.getZ(), 3, 0.1, 0.05, 0.1, 0.0);
				c.discard();
				CatKeeping.caught(level, cat);
				return;
			}
			if (!cat.isOrderedToSit() && !cat.isInSittingPose()) {
				cat.getNavigation().moveTo(c, 1.0);
			}
		}
		if (c.distanceToSqr(Vec3.atCenterOf(store)) <= NIBBLE_REACH * NIBBLE_REACH && level.getRandom().nextFloat() < NIBBLE_CHANCE) {
			Container container = (Container) be;
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack s = container.getItem(i);
				if (GRAIN.contains(s.getItem())) {
					s.shrink(1);
					container.setChanged();
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.NEUTRAL, 0.3F, 1.8F);
					break;
				}
			}
		}
	}

	/** A mole heaps a hill beside a field now and then, and its working of the ground helps a crop beside it. */
	private static void mole(ServerLevel level, Critter c) {
		BlockPos at = c.blockPosition();
		if (farmland(level, at) < FIELD_MIN / 2) {
			leave(level, c);
			return;
		}
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= DIG_CHANCE) {
			return;
		}
		int r = (int) DIG_REACH;
		BlockPos hill = at.offset(random.nextIntBetweenInclusive(-r, r), 0, random.nextIntBetweenInclusive(-r, r));
		if (c.hills() < HILLS && level.getBlockState(hill).isAir() && level.getBlockState(hill.below()).is(Blocks.GRASS_BLOCK)) {
			level.setBlock(hill, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.POOF, hill.getX() + 0.5, hill.getY() + 0.9, hill.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
			c.heaped();
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CropBlock crop && !crop.isMaxAge(s)) {
				s.randomTick(level, p.immutable(), random);
				break;
			}
		}
	}
}
