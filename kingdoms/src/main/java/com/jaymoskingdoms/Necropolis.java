package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The one who comes back (fifth edition's lich and its phylactery, Andy's pick D5). Out in the Overworld, here and there
 * (one cell of the world in {@link #CHANCE}), a great necromancer ({@link GreatNecromancer}) sleeps in a crypt dug deep in
 * natural ground, under a hatch among mossy stones. It is made when a player first comes near, and written down, so it is
 * the same crypt every time.
 * <p>
 * He keeps his life in a soul urn ({@link Vessel}) sealed in a hidden room {@link #VESSEL_FAR} blocks or so from the hatch,
 * under a cairn of mossy stones with a skull on it. Kill him and he rises again in his crypt {@link #RISE_MIN} to
 * {@link #RISE_MAX} ticks later, whole, until the urn is smashed. Smash it and his next death is the last: the lair lifts
 * ({@link GreatLairs}), his bones are left, and so is his staff.
 * <p>
 * Nothing says where the urn is. The sexton's tally (a barrel by the hatch) says which way and how far from the hatch; the
 * Hollow Man's own page (the chest in the crypt) says how deep and what marks it; villagers within {@link #GOSSIP_REACH}
 * blocks know the crypt is there and that he cannot die while his heart is hid ({@link CryptWords}); and the urn hums to
 * those who pass near it with a good ear ({@link Notice}).
 */
public final class Necropolis {
	// ---- tuning ----
	/** The world is cut into cells this wide (blocks); one in {@link #CHANCE} of them holds a crypt. */
	static final int CELL = 768;
	static final float CHANCE = 0.5F;
	/** How near (blocks, flat) a player comes before the crypt is made, and how far from a village's people it must lie. */
	static final int WAKE = 70, VILLAGE_CLEAR = 150;
	/** How far under the surface the crypt's floor lies, its half width and its inside height. */
	static final int DEPTH = 14, HALF = 5, HEIGHT = 5;
	/** The share of the crypt's blocks that may be air already (small caves are fine). */
	static final float AIR_ALLOWED = 0.25F;
	/** How far the vessel lies from the hatch (blocks), and how deep under the surface (blocks). */
	static final int VESSEL_NEAR = 24, VESSEL_FAR = 46, VESSEL_DEEP_MIN = 8, VESSEL_DEEP_MAX = 13;
	/** After a death with the vessel whole, he rises this long after (ticks): two to four days. */
	static final long RISE_MIN = 48000L, RISE_MAX = 96000L;
	/** How often the crypts are seen to (ticks), new ones looked for, and gossip tried. */
	static final int CHECK = 20, SETTLE = 100, GOSSIP = 200;
	/** Villagers know of the crypt within this (blocks); the same player hears gossip no more often than this (ticks). */
	static final double GOSSIP_REACH = 700.0, GOSSIP_NEAR = 7.0;
	static final long GOSSIP_AGAIN = 20L * 60 * 5;
	/** The urn hums to someone this near (blocks). */
	static final double HUM_RANGE = 9.0;

	public static EntityType<GreatNecromancer> TYPE;

	/** One crypt: where its hall, hatch, cairn and vessel are, whether the vessel stands, when he rises (0: not waiting), and whether it is over. */
	public record Crypt(UUID id, BlockPos hall, BlockPos hatch, BlockPos cairn, BlockPos vessel, boolean whole, long risesAt, boolean done) {
		static final Codec<Crypt> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Crypt::id),
				BlockPos.CODEC.fieldOf("hall").forGetter(Crypt::hall),
				BlockPos.CODEC.fieldOf("hatch").forGetter(Crypt::hatch),
				BlockPos.CODEC.fieldOf("cairn").forGetter(Crypt::cairn),
				BlockPos.CODEC.fieldOf("vessel").forGetter(Crypt::vessel),
				Codec.BOOL.fieldOf("whole").forGetter(Crypt::whole),
				Codec.LONG.fieldOf("rises_at").forGetter(Crypt::risesAt),
				Codec.BOOL.fieldOf("done").forGetter(Crypt::done))
			.apply(i, Crypt::new));
	}

	/** How near (blocks) a crypt must be for a sage or a library to have its records. */
	static final int LOOKUP_RANGE = 1500;

	static final AttachmentType<List<Crypt>> CRYPTS = AttachmentRegistry.create(JaymosKingdoms.id("necromancer_crypts"), b -> b.persistent(Crypt.CODEC.listOf()));
	/** The cells already seen to (with a crypt or without), packed. */
	static final AttachmentType<List<Long>> CELLS = AttachmentRegistry.create(JaymosKingdoms.id("necromancer_cells"), b -> b.persistent(Codec.LONG.listOf()));

	private static @Nullable List<Crypt> cache;
	/** The game time each player last heard gossip. */
	private static final Map<UUID, Long> HEARD = new HashMap<>();

	private Necropolis() {
	}

	public static void register() {
		Vessel.register();
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("great_necromancer"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(GreatNecromancer::new, MobCategory.MONSTER)
			.sized(0.7F, 2.4F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, GreatNecromancer.createAttributes());
		GreatLairs.register();
		LairBeats.register();
		LairLand.register();
		BossMoves.register();
		ServerTickEvents.END_SERVER_TICK.register(Necropolis::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> died(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			cache = null;
			HEARD.clear();
		});
		// a sage or a library can tell where his heart is hid (looking it up)
		Research.addTopic(new Research.Topic() {
			@Override
			public String id() {
				return "vessels";
			}

			@Override
			public Component title() {
				return Component.translatable("research.jaymoskingdoms.topic.vessels");
			}

			@Override
			public List<Research.Subject> subjects(ServerPlayer player) {
				List<Research.Subject> out = new ArrayList<>();
				if (player.level().dimension() != Level.OVERWORLD) {
					return out;
				}
				for (Crypt c : crypts(player.level().getServer())) {
					if (c.whole() && !c.done() && player.blockPosition().distSqr(c.hall()) <= LOOKUP_RANGE * LOOKUP_RANGE) {
						out.add(new Research.Subject(c.id().toString(), Component.translatable("research.jaymoskingdoms.vessels.subject",
							CryptWords.point(player.blockPosition(), c.hall()))));
					}
				}
				return out;
			}

			@Override
			public Component learn(ServerPlayer player, String subject) {
				for (Crypt c : crypts(player.level().getServer())) {
					if (c.id().toString().equals(subject)) {
						return Component.translatable("research.jaymoskingdoms.vessels.learned", CryptWords.paces(c.hatch(), c.vessel(), 5),
							CryptWords.point(c.hatch(), c.vessel()), c.cairn().getY() - c.vessel().getY());
					}
				}
				return Component.translatable("research.jaymoskingdoms.vessels.gone");
			}
		});
		// the urn hums to a good ear that passes near it
		Notice.add((player, into) -> {
			if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
				return;
			}
			for (Crypt c : crypts(level.getServer())) {
				if (c.whole() && !c.done() && player.distanceToSqr(Vec3.atCenterOf(c.vessel())) <= HUM_RANGE * HUM_RANGE) {
					into.add(new Notice.Spot(c.vessel().asLong() ^ 0x5EEDL, Vec3.atCenterOf(c.vessel()), SoundEvents.AMETHYST_BLOCK_RESONATE));
				}
			}
		});
	}

	// ---- the record --------------------------------------------------------------------------------------------------

	private static List<Crypt> crypts(MinecraftServer server) {
		if (cache == null) {
			cache = new ArrayList<>(server.overworld().getAttachedOrElse(CRYPTS, List.<Crypt>of()));
		}
		return cache;
	}

	private static void save(MinecraftServer server) {
		if (cache != null) {
			server.overworld().setAttached(CRYPTS, List.copyOf(cache));
		}
	}

	private static void put(MinecraftServer server, Crypt crypt) {
		List<Crypt> all = crypts(server);
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).id().equals(crypt.id())) {
				all.set(i, crypt);
				save(server);
				return;
			}
		}
		all.add(crypt);
		save(server);
	}

	private static @Nullable Crypt of(MinecraftServer server, @Nullable BlockPos hall) {
		if (hall == null) {
			return null;
		}
		for (Crypt c : crypts(server)) {
			if (c.hall().equals(hall)) {
				return c;
			}
		}
		return null;
	}

	/** Whether {@code necromancer}, if he falls now, will rise again: his vessel stands. */
	public static boolean rises(GreatNecromancer necromancer) {
		MinecraftServer server = necromancer.level().getServer();
		Crypt c = server == null ? null : of(server, necromancer.crypt());
		return c != null && c.whole() && !c.done();
	}

	// ---- death and rising ----------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity) {
		if (!(entity instanceof GreatNecromancer necromancer) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Crypt c = of(server, necromancer.crypt());
		if (c == null || c.done()) {
			return;
		}
		if (c.whole()) {
			long wait = RISE_MIN + (long)(level.getRandom().nextDouble() * (RISE_MAX - RISE_MIN));
			put(server, new Crypt(c.id(), c.hall(), c.hatch(), c.cairn(), c.vessel(), true, level.getGameTime() + wait, false));
		} else {
			finish(server, c);
		}
	}

	/** It is over: his last death, or the vessel smashed while he was down. The lair lifts. */
	private static void finish(MinecraftServer server, Crypt c) {
		put(server, new Crypt(c.id(), c.hall(), c.hatch(), c.cairn(), c.vessel(), false, 0L, true));
		GreatLairs.lift(server, c.id());
	}

	/** The vessel at {@code pos} is broken: the necromancer's life is out in the open. */
	public static void smashed(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		for (Crypt c : new ArrayList<>(crypts(server))) {
			if (c.whole() && c.vessel().equals(pos)) {
				shatter(level, c);
			}
		}
	}

	private static void shatter(ServerLevel level, Crypt c) {
		MinecraftServer server = level.getServer();
		Vec3 at = Vec3.atCenterOf(c.vessel());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.6F, 0.4F);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 60, 0.5, 0.8, 0.5, 0.12);
		Fx.blast(level, at, 3.0F, 0xA85CEE);
		if (c.risesAt() > 0L) {
			// he was down, waiting for his hour: he does not get up
			finish(server, c);
		} else {
			put(server, new Crypt(c.id(), c.hall(), c.hatch(), c.cairn(), c.vessel(), false, 0L, false));
			// he feels it wherever he is: a wound, and a scream through his crypt
			for (GreatNecromancer n : level.getEntitiesOfClass(GreatNecromancer.class, new AABB(c.hall()).inflate(80.0), n -> n.isAlive() && c.hall().equals(n.crypt()))) {
				n.hurtServer(level, level.damageSources().magic(), n.getMaxHealth() * 0.1F);
				level.playSound(null, n.getX(), n.getY(), n.getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 1.6F, 0.5F);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		ServerLevel level = server.overworld();
		if (t % CHECK == 0) {
			for (Crypt c : new ArrayList<>(crypts(server))) {
				if (c.done()) {
					continue;
				}
				// the urn gone by any other means (an explosion, a piston)
				if (c.whole() && level.hasChunkAt(c.vessel()) && !level.getBlockState(c.vessel()).is(Vessel.BLOCK)) {
					shatter(level, c);
					continue;
				}
				if (c.risesAt() > 0L && level.getGameTime() >= c.risesAt() && level.hasChunkAt(c.hall())) {
					rise(level, c);
				}
			}
		}
		if (t % SETTLE == 0) {
			settle(server, level);
		}
		if (t % GOSSIP == 0) {
			gossip(server, level);
		}
	}

	/** He rises in his crypt, whole and awake. */
	private static void rise(ServerLevel level, Crypt c) {
		MinecraftServer server = level.getServer();
		if (!c.whole()) {
			finish(server, c);
			return;
		}
		put(server, new Crypt(c.id(), c.hall(), c.hatch(), c.cairn(), c.vessel(), true, 0L, false));
		Vec3 top = Vec3.atBottomCenterOf(c.hall().above(2));
		GreatNecromancer n = spawn(level, c, false);
		if (n == null) {
			return;
		}
		net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(top.x, top.y, top.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.SOUL, top.x, top.y, top.z, 60, 1.0, 1.2, 1.0, 0.08);
		Fx.sigil(level, top, 8.0F, 0x7A1FA2, 80);
	}

	private static @Nullable GreatNecromancer spawn(ServerLevel level, Crypt c, boolean asleep) {
		GreatNecromancer n = TYPE.create(level, EntitySpawnReason.STRUCTURE);
		if (n == null) {
			return null;
		}
		BlockPos stand = c.hall().above(2);
		n.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		n.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), EntitySpawnReason.STRUCTURE, null);
		n.settle(c.hall(), asleep);
		level.addFreshEntity(n);
		return n;
	}

	// ---- making a crypt ------------------------------------------------------------------------------------------------

	private static void settle(MinecraftServer server, ServerLevel level) {
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		Set<Long> seen = null;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			int cx = Math.floorDiv(player.getBlockX(), CELL), cz = Math.floorDiv(player.getBlockZ(), CELL);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx, z = cz + dz;
					long key = (long)x << 32 ^ (z & 0xFFFFFFFFL);
					Random random = new Random(level.getSeed() ^ x * 341873128712L ^ z * 132897987541L ^ 0x6E6563726FL);
					if (random.nextFloat() >= CHANCE) {
						continue;
					}
					int sx = x * CELL + 96 + random.nextInt(CELL - 192), sz = z * CELL + 96 + random.nextInt(CELL - 192);
					double near = Math.hypot(player.getX() - sx, player.getZ() - sz);
					if (near > WAKE) {
						continue;
					}
					if (seen == null) {
						seen = new HashSet<>(level.getAttachedOrElse(CELLS, List.<Long>of()));
					}
					if (seen.contains(key)) {
						continue;
					}
					Boolean made = build(level, sx, sz, random);
					if (made != null) {
						seen.add(key);
						level.setAttached(CELLS, List.copyOf(seen));
					}
				}
			}
		}
	}

	/** Makes the crypt about ({@code sx}, {@code sz}); true if made, false if the ground won't take one, null if the land isn't all loaded yet. */
	private static @Nullable Boolean build(ServerLevel level, int sx, int sz, Random random) {
		int reach = VESSEL_FAR + 6;
		if (!level.hasChunksAt(sx - reach, sz - reach, sx + reach, sz + reach)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (Factions.around(server, level, new BlockPos(sx, level.getSeaLevel(), sz), VILLAGE_CLEAR) != null) {
			return false;
		}
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz);
		BlockPos floor = new BlockPos(sx, surface - DEPTH, sz);
		if (floor.getY() <= level.getMinY() + 6 || !natural(level, new BlockPos(sx + HALF, surface - 1, sz)) || !fits(level, floor, surface)) {
			return false;
		}
		BlockPos hatch = new BlockPos(sx + HALF, surface - 1, sz);
		// the vessel's room: a spot well off, under natural ground
		BlockPos vessel = null, cairn = null;
		for (int tries = 0; tries < 24 && vessel == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, d = VESSEL_NEAR + random.nextDouble() * (VESSEL_FAR - VESSEL_NEAR);
			int vx = hatch.getX() + (int)Math.round(Math.cos(angle) * d), vz = hatch.getZ() + (int)Math.round(Math.sin(angle) * d);
			int vs = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, vx, vz);
			int vy = vs - VESSEL_DEEP_MIN - random.nextInt(VESSEL_DEEP_MAX - VESSEL_DEEP_MIN + 1);
			if (vy <= level.getMinY() + 4 || !natural(level, new BlockPos(vx, vs - 1, vz)) || level.getBlockState(new BlockPos(vx, vs, vz)).isSolid()
				|| !solidBox(level, new BlockPos(vx, vy, vz))) {
				continue;
			}
			vessel = new BlockPos(vx, vy, vz);
			cairn = new BlockPos(vx, vs, vz);
		}
		if (vessel == null) {
			return false;
		}
		RandomSource rng = level.getRandom();
		carve(level, floor, surface, rng);
		BlockPos topHatch = hatch;
		furnish(level, floor, rng);
		hideVessel(level, vessel, rng);
		markCairn(level, cairn, rng);
		markHatch(level, topHatch, rng);
		UUID id = UUID.randomUUID();
		String book = id.toString().substring(0, 8);
		Crypt crypt = new Crypt(id, floor, hatch, cairn, vessel, true, 0L, false);
		stock(level, floor, hatch, crypt, book, rng);
		put(server, crypt);
		GreatLairs.found(server, id, level, floor, GreatLairs.Kind.CRYPT);
		spawn(level, crypt, true);
		return true;
	}

	private static boolean natural(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return Lairs.natural(s) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT);
	}

	/** A 5 by 5 by 5 block of natural, mostly solid ground about {@code at}. */
	private static boolean solidBox(ServerLevel level, BlockPos at) {
		int solid = 0, all = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			all++;
			if (s.isAir() || !s.getFluidState().isEmpty()) {
				return false;
			}
			if (!Lairs.natural(s) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.DIRT)) {
				return false;
			}
			solid++;
		}
		return solid == all;
	}

	/** Whether the hall, its shaft and the hatch can be dug here: nothing but natural ground (some air allowed). */
	private static boolean fits(ServerLevel level, BlockPos floor, int surface) {
		int air = 0, all = 0;
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-HALF - 1, 0, -HALF - 1), floor.offset(HALF + 1, HEIGHT + 1, HALF + 1))) {
			BlockState s = level.getBlockState(p);
			all++;
			if (s.isAir()) {
				air++;
			} else if (!Lairs.natural(s)) {
				return false;
			}
		}
		for (int y = floor.getY() + HEIGHT + 1; y < surface; y++) {
			BlockState s = level.getBlockState(new BlockPos(floor.getX() + HALF, y, floor.getZ()));
			if (!s.isAir() && !Lairs.natural(s) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.DIRT)) {
				return false;
			}
		}
		return air <= all * AIR_ALLOWED;
	}

	/** The hall's shell, floor and shaft. */
	private static void carve(ServerLevel level, BlockPos floor, int surface, RandomSource rng) {
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-HALF - 1, 0, -HALF - 1), floor.offset(HALF + 1, HEIGHT + 1, HALF + 1))) {
			int dx = Math.abs(p.getX() - floor.getX()), dz = Math.abs(p.getZ() - floor.getZ()), dy = p.getY() - floor.getY();
			boolean wall = dx > HALF || dz > HALF || dy == HEIGHT + 1;
			BlockState state;
			if (wall) {
				float r = rng.nextFloat();
				state = (r < 0.15F ? Blocks.MOSSY_STONE_BRICKS : r < 0.35F ? Blocks.CRACKED_STONE_BRICKS : r < 0.55F ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
			} else if (dy == 0) {
				state = (rng.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).defaultBlockState();
			} else {
				state = Blocks.AIR.defaultBlockState();
			}
			level.setBlock(p, state, Block.UPDATE_ALL);
		}
		// the shaft up the east side to the hatch, a ladder against its wall
		int x = floor.getX() + HALF, z = floor.getZ();
		for (int y = floor.getY() + HEIGHT + 1; y < surface - 1; y++) {
			level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (int y = floor.getY() + 1; y < surface - 1; y++) {
			BlockPos wall = new BlockPos(x + 1, y, z);
			if (!level.getBlockState(wall).isSolid()) {
				level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.setBlock(new BlockPos(x, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
		}
		level.setBlock(new BlockPos(x, surface - 1, z), Blocks.DARK_OAK_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Braziers in the corners, a dais, skulls and cobwebs. */
	private static void furnish(ServerLevel level, BlockPos floor, RandomSource rng) {
		int y = floor.getY() + 1;
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				BlockPos corner = new BlockPos(floor.getX() + sx * (HALF - 1), y, floor.getZ() + sz * (HALF - 1));
				level.setBlock(corner, Blocks.SOUL_CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(corner.above(HEIGHT - 1), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), Block.UPDATE_ALL);
				if (rng.nextBoolean()) {
					level.setBlock(new BlockPos(floor.getX() + sx * HALF, y + HEIGHT - 1, floor.getZ() + sz * HALF), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(new BlockPos(floor.getX() + dx, y, floor.getZ() + dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			level.setBlock(new BlockPos(floor.getX() + c[0], y + 1, floor.getZ() + c[1]), Blocks.SKELETON_SKULL.defaultBlockState()
				.setValue(SkullBlock.ROTATION, rng.nextInt(16)), Block.UPDATE_ALL);
		}
		// bones strewn about the floor, clear of the dais
		for (int i = 0; i < 6; i++) {
			int dx = rng.nextInt(HALF * 2 - 1) - HALF + 1, dz = rng.nextInt(HALF * 2 - 1) - HALF + 1;
			if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
				level.setBlock(new BlockPos(floor.getX() + dx, y, floor.getZ() + dz), Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	/** The vessel's room: a small sealed chamber in the rock, the urn on its floor among candles and bones. */
	private static void hideVessel(ServerLevel level, BlockPos vessel, RandomSource rng) {
		for (BlockPos p : BlockPos.betweenClosed(vessel.offset(-1, 0, -1), vessel.offset(1, 1, 1))) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (BlockPos p : BlockPos.betweenClosed(vessel.offset(-1, -1, -1), vessel.offset(1, -1, 1))) {
			level.setBlock(p, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.setBlock(vessel, Vessel.BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			BlockPos p = vessel.offset(c[0], 0, c[1]);
			level.setBlock(p, rng.nextBoolean() ? Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + rng.nextInt(3)) : Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** The mossy cairn over the vessel: three stones and a skull. */
	private static void markCairn(ServerLevel level, BlockPos cairn, RandomSource rng) {
		level.setBlock(cairn, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(cairn.above(), Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(cairn.above().above(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rng.nextInt(16)), Block.UPDATE_ALL);
		level.setBlock(cairn.north(), Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** The hatch among its mossy stones. */
	private static void markHatch(ServerLevel level, BlockPos hatch, RandomSource rng) {
		BlockPos top = hatch.above();
		for (Direction d : new Direction[] {Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
			if (level.getBlockState(top.relative(d)).isAir()) {
				level.setBlock(top.relative(d), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		if (level.getBlockState(top.east().above()).isAir() && level.getBlockState(top.east()).isAir()) {
			level.setBlock(top.east(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(top.east().above(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rng.nextInt(16)), Block.UPDATE_ALL);
		}
	}

	/** The chest in the hall with the Hollow Man's page, the sexton's barrel by the hatch with the tally. */
	private static void stock(ServerLevel level, BlockPos floor, BlockPos hatch, Crypt crypt, String id, RandomSource rng) {
		BlockPos chest = new BlockPos(floor.getX(), floor.getY() + 1, floor.getZ() - HALF);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(chest) instanceof Container c) {
			c.setItem(13, CryptWords.page(id, crypt.cairn(), crypt.vessel()));
			c.setItem(10, new ItemStack(Items.DIAMOND, 2 + rng.nextInt(3)));
			c.setItem(11, new ItemStack(Items.GOLD_INGOT, 6 + rng.nextInt(8)));
			c.setItem(15, new ItemStack(Items.EMERALD, 4 + rng.nextInt(6)));
			c.setItem(16, new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + rng.nextInt(4)));
			c.setItem(2, new ItemStack(Items.BONE, 8 + rng.nextInt(9)));
		}
		BlockPos barrel = hatch.above().south().south();
		for (int dy = 3; dy >= -2; dy--) {
			BlockPos p = barrel.above(dy);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
				level.setBlock(p, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Block.UPDATE_ALL);
				if (level.getBlockEntity(p) instanceof Container c) {
					c.setItem(4, CryptWords.tally(id, hatch, crypt.vessel()));
					c.setItem(11, new ItemStack(Items.CANDLE, 2));
					c.setItem(13, new ItemStack(Items.BREAD, 2));
				}
				return;
			}
		}
		// nowhere to set the barrel: the tally goes in the chest
		if (level.getBlockEntity(chest) instanceof Container c) {
			c.setItem(14, CryptWords.tally(id, hatch, crypt.vessel()));
		}
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/** A villager near someone, in reach of a crypt whose master still lives or waits, may say what folk say. */
	private static void gossip(MinecraftServer server, ServerLevel level) {
		long now = level.getGameTime();
		HEARD.values().removeIf(t -> now - t > GOSSIP_AGAIN * 4);
		for (ServerPlayer player : level.players()) {
			Long last = HEARD.get(player.getUUID());
			if (player.isSpectator() || last != null && now - last < GOSSIP_AGAIN) {
				continue;
			}
			Crypt near = null;
			for (Crypt c : crypts(server)) {
				if (!c.done() && c.hall().distSqr(player.blockPosition()) <= GOSSIP_REACH * GOSSIP_REACH) {
					near = c;
					break;
				}
			}
			if (near == null) {
				continue;
			}
			AbstractVillager speaker = null;
			for (AbstractVillager v : level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(GOSSIP_NEAR))) {
				if (v.isAlive() && !v.isSleeping() && v.hasLineOfSight(player)) {
					speaker = v;
					break;
				}
			}
			if (speaker == null) {
				continue;
			}
			HEARD.put(player.getUUID(), now);
			Component words = CryptWords.gossip(level.getRandom().nextInt(3), speaker.blockPosition(), near.hall(), near.vessel());
			Speech.say(level, speaker, words, Speech.WHITE, GOSSIP_NEAR + 2.0);
		}
	}
}
