package com.jaymoskingdoms.dungeon;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import com.jaymoskingdoms.inhabitants.RaidKing;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Stormborn Tomb's great hall and what sleeps under it. The Frostborn jailer's seal lies broken through the tomb:
 * every wight, scourge and hall-guard among the draugr carries a <b>shard</b> of it ({@link #SEAL_SHARD}). Three shards
 * make the <b>mended seal</b> ({@link #MENDED_SEAL}, a plain recipe: it is stone). Set down on the floor of the great
 * hall (the tomb's {@code main} piece) it wakes the hall: the dead come up out of the floor in {@link #WAVES waves}, each
 * when the last is down, and when the last wave is down the {@link RaidKing} rises where the seal was laid. Nothing on
 * the screen says any of it.
 *
 * <p>Each tomb's lord falls once ({@link #FALLEN}); after that a seal does nothing there. If everyone leaves while the
 * waves still come, the hall goes quiet and the seal is left on the floor. A fight under way is not kept through a
 * restart (the seal is lost with it; the lord, once risen, stays).
 */
public final class TombHall {
	public static Item SEAL_SHARD, MENDED_SEAL;
	/** On a draugr the hall raised: it carries no shard, and falls with the hall's quiet. */
	public static final String WAVE_TAG = "jaymoskingdoms.hall_wave";

	/** The waves, in order: who comes up in each. */
	static final Draugr.Rank[][] WAVES = {
		{Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.ARCHER, Draugr.Rank.ARCHER},
		{Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.ARCHER, Draugr.Rank.ARCHER, Draugr.Rank.WIGHT, Draugr.Rank.SCOURGE},
		{Draugr.Rank.HALL_GUARD, Draugr.Rank.HALL_GUARD, Draugr.Rank.WIGHT, Draugr.Rank.WIGHT, Draugr.Rank.SCOURGE, Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR},
	};
	/** Who the lord's roar may raise. */
	static final Draugr.Rank[] CALLED = {Draugr.Rank.WARRIOR, Draugr.Rank.WARRIOR, Draugr.Rank.ARCHER, Draugr.Rank.WIGHT};
	/** The breath before the first wave and between waves (ticks); a wave not down by this long is followed anyway. */
	static final int PAUSE = 80, WAVE_LIMIT = 20 * 120;
	/** How far from the seal the dead come up (blocks, least and most), and how far they keep to it. */
	static final double RISE_MIN = 5.0, RISE_MAX = 12.0;
	static final int WAVE_HOME = 24;
	/** With no player within this (blocks) for this long (ticks) the hall goes quiet. */
	static final double PRESENT = 48.0;
	static final int QUIET_AFTER = 400;
	static final int COLD_COLOR = 0xDDF6FF;

	/** On a level: the tombs (their start chunks, packed) whose lord has fallen. Saved. */
	static final AttachmentType<List<Long>> FALLEN = AttachmentRegistry.create(JaymosKingdoms.id("tomb_lords_fallen"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	/** A hall being fought for. */
	private static final class Fight {
		final ResourceKey<Level> level;
		final long tomb;
		final BlockPos seal;
		final BoundingBox hall;
		final List<UUID> dead = new ArrayList<>();
		int wave = -1, timer, quiet;
		@Nullable UUID lord;

		Fight(ResourceKey<Level> level, long tomb, BlockPos seal, BoundingBox hall) {
			this.level = level;
			this.tomb = tomb;
			this.seal = seal;
			this.hall = hall;
		}
	}

	private static final Map<Long, Fight> FIGHTS = new HashMap<>();

	private TombHall() {
	}

	public static void register() {
		SEAL_SHARD = item("seal_shard", new Item(props("seal_shard").stacksTo(16)));
		MENDED_SEAL = item("mended_seal", new Seal(props("mended_seal").stacksTo(1)));
		ServerTickEvents.END_SERVER_TICK.register(TombHall::tick);
	}

	private static Item.Properties props(String name) {
		return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name)));
	}

	private static Item item(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, JaymosKingdoms.id(name), item);
	}

	/** The mended seal: set down on the great hall's floor. */
	static final class Seal extends Item {
		Seal(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().above();
			Vec3 centre = Vec3.atBottomCenterOf(at);
			Hall hall = hall(level, at);
			if (hall == null || FIGHTS.containsKey(hall.tomb()) || level.getAttachedOrElse(FALLEN, List.of()).contains(hall.tomb())) {
				// not the place, or nothing left under it: the seal only breathes cold
				Fx.burst(level, centre.x, centre.y + 0.3, centre.z, COLD_COLOR, 0.6F, 8, 0.3, 0.2, 0.3);
				level.playSound(null, at, SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 0.8F, 0.6F);
				return InteractionResult.SUCCESS;
			}
			context.getItemInHand().shrink(1);
			FIGHTS.put(hall.tomb(), new Fight(level.dimension(), hall.tomb(), at, hall.box()));
			Fx.sigil(level, centre.add(0.0, 0.05, 0.0), 4.0F, COLD_COLOR, PAUSE);
			Fx.ring(level, centre.add(0.0, 0.1, 0.0), 12.0F, COLD_COLOR);
			level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3.0F, 0.5F);
			level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
			return InteractionResult.SUCCESS;
		}
	}

	/** A tomb's great hall: the tomb (its start chunk, packed) and the hall's box. */
	record Hall(long tomb, BoundingBox box) {
	}

	/** The great hall {@code pos} is in, if it is in one. */
	static @Nullable Hall hall(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure -> structure.is(StormbornTomb.KEY));
		if (start == null || !start.isValid()) {
			return null;
		}
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement().toString().contains("stormborn_tomb/main]")
				&& piece.getBoundingBox().isInside(pos)) {
				return new Hall(start.getChunkPos().pack(), piece.getBoundingBox());
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Fight> it = FIGHTS.values().iterator(); it.hasNext();) {
			Fight fight = it.next();
			ServerLevel level = server.getLevel(fight.level);
			if (level == null || step(level, fight)) {
				it.remove();
			}
		}
	}

	/** One tick of {@code fight}; true when it is over. */
	private static boolean step(ServerLevel level, Fight fight) {
		Vec3 centre = Vec3.atBottomCenterOf(fight.seal);
		Player near = level.getNearestPlayer(centre.x, centre.y, centre.z, PRESENT, true);
		fight.quiet = near == null ? fight.quiet + 1 : 0;
		if (fight.lord != null) {
			// he is up: the hall is his to keep, with or without anyone in it
			Entity lord = level.getEntity(fight.lord);
			return lord == null && near != null || lord != null && !lord.isAlive();
		}
		if (fight.quiet > QUIET_AFTER) {
			quiet(level, fight.seal);
			level.addFreshEntity(new ItemEntity(level, centre.x, centre.y + 0.2, centre.z, new ItemStack(MENDED_SEAL)));
			return true;
		}
		fight.timer++;
		fight.dead.removeIf(id -> {
			Entity entity = level.getEntity(id);
			return entity == null || !entity.isAlive();
		});
		boolean first = fight.wave < 0;
		if (first ? fight.timer < PAUSE : !fight.dead.isEmpty() && fight.timer < WAVE_LIMIT) {
			return false;
		}
		if (!first && fight.timer < PAUSE) {
			return false;
		}
		fight.wave++;
		fight.timer = 0;
		LivingEntity target = near instanceof ServerPlayer player ? player : null;
		level.playSound(null, fight.seal, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.5F);
		Fx.ring(level, centre.add(0.0, 0.1, 0.0), (float) RISE_MAX, COLD_COLOR);
		if (fight.wave < WAVES.length) {
			for (Draugr.Rank rank : WAVES[fight.wave]) {
				Draugr risen = raise(level, fight.seal, fight.hall, rank, target, 2.4F);
				if (risen != null) {
					fight.dead.add(risen.getUUID());
				}
			}
			return false;
		}
		RaidKing king = Inhabitants.RAID_KING.create(level, EntitySpawnReason.TRIGGERED);
		if (king == null) {
			return true;
		}
		king.snapTo(centre.x, centre.y, centre.z, near == null ? 0.0F : (float) Math.toDegrees(Math.atan2(-(near.getX() - centre.x), near.getZ() - centre.z)), 0.0F);
		king.setHomeTo(fight.seal, RaidKing.HOME_RADIUS);
		king.wake();
		level.addFreshEntity(king);
		Fx.blast(level, centre.add(0.0, 1.5, 0.0), 4.0F, COLD_COLOR);
		fight.lord = king.getUUID();
		return false;
	}

	/**
	 * One of the hall's dead up out of the floor somewhere round {@code heart} (inside {@code within}, if given), of
	 * {@code rank}, after {@code target}; null if there was nowhere to stand it.
	 */
	static @Nullable Draugr raise(ServerLevel level, BlockPos heart, @Nullable BoundingBox within, Draugr.Rank rank, @Nullable LivingEntity target, float height) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 24; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, far = RISE_MIN + random.nextDouble() * (RISE_MAX - RISE_MIN);
			BlockPos column = BlockPos.containing(heart.getX() + 0.5 + Math.cos(angle) * far, heart.getY(), heart.getZ() + 0.5 + Math.sin(angle) * far);
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos at = column.above(dy);
				if (within != null && !within.isInside(at) || !stands(level, at, height)) {
					continue;
				}
				Draugr risen = Inhabitants.DRAUGR.create(level, EntitySpawnReason.TRIGGERED);
				if (risen == null) {
					return null;
				}
				risen.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				risen.arm(rank, rank == Draugr.Rank.WARRIOR && random.nextFloat() < 0.4F);
				risen.addTag(WAVE_TAG);
				risen.setPersistenceRequired();
				risen.setHomeTo(heart, WAVE_HOME);
				risen.setTarget(target);
				level.addFreshEntity(risen);
				risen.triggerAnim("action", "rise");
				Vec3 feet = Vec3.atBottomCenterOf(at);
				Fx.burst(level, feet.x, feet.y + 0.2, feet.z, COLD_COLOR, 0.7F, 10, 0.4, 0.2, 0.4);
				level.playSound(null, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6F, 0.6F);
				return risen;
			}
		}
		return null;
	}

	private static boolean stands(ServerLevel level, BlockPos at, float height) {
		if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
			return false;
		}
		for (int up = 0; up < Math.ceil(height); up++) {
			if (!level.getBlockState(at.above(up)).getCollisionShape(level, at.above(up)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The lord's roar: {@code count} more of the hall's dead come up round him. */
	public static void call(ServerLevel level, RaidKing king, int count) {
		BlockPos heart = king.hasHome() ? king.getHomePosition() : king.blockPosition();
		Hall hall = hall(level, heart);
		for (int i = 0; i < count; i++) {
			raise(level, king.blockPosition(), hall == null ? null : hall.box(), CALLED[level.getRandom().nextInt(CALLED.length)], king.getTarget(), 2.4F);
		}
	}

	/** The lord is down: his tomb's hall is done for good, and the dead it raised fall with him. */
	public static void fell(ServerLevel level, RaidKing king) {
		BlockPos heart = king.hasHome() ? king.getHomePosition() : king.blockPosition();
		Hall hall = hall(level, heart);
		if (hall != null) {
			List<Long> fallen = new ArrayList<>(level.getAttachedOrElse(FALLEN, List.of()));
			if (!fallen.contains(hall.tomb())) {
				fallen.add(hall.tomb());
				level.setAttached(FALLEN, List.copyOf(fallen));
			}
		}
		quiet(level, heart);
	}

	/** Every draugr the hall raised near {@code heart} falls. */
	private static void quiet(ServerLevel level, BlockPos heart) {
		for (Draugr risen : level.getEntitiesOfClass(Draugr.class, new AABB(heart).inflate(48.0), d -> d.isAlive() && d.entityTags().contains(WAVE_TAG))) {
			Fx.burst(level, risen.getX(), risen.getY() + 1.0, risen.getZ(), COLD_COLOR, 0.7F, 12, 0.3, 0.6, 0.3);
			risen.discard();
		}
	}
}
