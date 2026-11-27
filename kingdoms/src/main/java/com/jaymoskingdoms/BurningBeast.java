package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Luxtructosaurus is a demon of the Ash (Andy, 2026-11-24: "the luxtructosaurus should be a demon entity that also
 * spawns in the nether after your encounter in the cave"; Lore.md, "The burning beast"). It counts as a demon
 * ({@code tags/entity_type/demon.json}: the Devil Splitter lands every blow on it twice). A player who has come within {@link #MEET}
 * blocks of one in the Overworld has met it ({@link #MET}, kept for good); after that, while they are in the Nether,
 * now and then ({@link #COME_CHANCE} each {@link #CHECK_TICKS}) one walks out of the Ash {@link #NEAR_MIN} to
 * {@link #NEAR_MAX} blocks from them, never while another is within {@link #ALONE}. Only with Alex's Caves loaded.
 */
public final class BurningBeast {
	static final Identifier BEAST = Identifier.fromNamespaceAndPath(AlexsCavesCompat.MOD_ID, "luxtructosaurus");
	/** Blocks: close enough in the Overworld to have met it. */
	static final double MEET = 48.0;
	/** How often (ticks) the Overworld is checked for a meeting. */
	static final int MEET_TICKS = 100;
	/** In the Nether: how often (ticks) one may come, and the chance each time (about once an hour of Nether time). */
	static final int CHECK_TICKS = 1200;
	static final float COME_CHANCE = 1.0F / 60.0F;
	/** Blocks: where it comes out, and no second one while one is this near. */
	static final int NEAR_MIN = 48, NEAR_MAX = 80;
	static final double ALONE = 192.0;
	/** Tries to find standing room for it. */
	static final int TRIES = 16;

	/** This player has met the burning beast in the caves. */
	public static final AttachmentType<Boolean> MET = AttachmentRegistry.create(JaymosKingdoms.id("met_luxtructosaurus"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private BurningBeast() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BurningBeast::tick);
	}

	private static @Nullable EntityType<?> type() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(BEAST).orElse(null);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % MEET_TICKS != 0) {
			return;
		}
		EntityType<?> type = type();
		if (type == null) {
			return;
		}
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		if (overworld != null) {
			for (ServerPlayer player : overworld.players()) {
				if (!Boolean.TRUE.equals(player.getAttached(MET)) && !player.isSpectator()
					&& !overworld.getEntities(type, player.getBoundingBox().inflate(MEET), Entity::isAlive).isEmpty()) {
					player.setAttached(MET, true);
				}
			}
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null || now % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : nether.players()) {
			if (Boolean.TRUE.equals(player.getAttached(MET)) && !player.isSpectator() && !player.isCreative()
				&& nether.getRandom().nextFloat() < COME_CHANCE
				&& nether.getEntities(type, player.getBoundingBox().inflate(ALONE), Entity::isAlive).isEmpty()) {
				come(nether, player, type);
			}
		}
	}

	/** Out of the Ash: standing room for its whole bulk on solid ground, away from lava, somewhere round the player. */
	private static void come(ServerLevel level, ServerPlayer player, EntityType<?> type) {
		RandomSource random = level.getRandom();
		int tall = Mth.ceil(type.getHeight()), half = Mth.ceil(type.getWidth() / 2.0F);
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = NEAR_MIN + random.nextInt(NEAR_MAX - NEAR_MIN + 1);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
			BlockPos at = ground(level, column, tall, half);
			if (at == null) {
				continue;
			}
			Entity beast = type.create(level, EntitySpawnReason.EVENT);
			if (beast == null) {
				return;
			}
			beast.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(beast);
			return;
		}
	}

	/** A floor within 16 blocks up or down of {@code column} with room above it {@code tall} high and {@code half} each way. */
	private static @Nullable BlockPos ground(ServerLevel level, BlockPos column, int tall, int half) {
		for (int dy = 16; dy >= -16; dy--) {
			BlockPos at = column.above(dy);
			if (at.getY() <= level.getMinY() + 1 || at.getY() + tall >= level.getMinY() + level.getLogicalHeight()) {
				continue;
			}
			BlockPos floor = at.below();
			if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !roomy(level, at, tall, half)) {
				continue;
			}
			return at;
		}
		return null;
	}

	private static boolean roomy(ServerLevel level, BlockPos at, int tall, int half) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-half, 0, -half), at.offset(half, tall, half))) {
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
