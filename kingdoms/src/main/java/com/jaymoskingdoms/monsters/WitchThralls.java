package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.Factions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The witches' zombie thralls. Andy (2026-11-15): "Witches hide the cure as they command zombie thralls themselves." Beside
 * the mud golems ({@link WitchHuts}), after dark a witch's swamp hut raises a few of the dead out of the mud to keep it, up
 * to {@link #MOST} at a time. They keep near the hut, a witch's potions never hurt them, and whoever strikes a witch near
 * them has her thralls to deal with too. In daylight they shelter like any of the dead.
 */
public final class WitchThralls {
	// ---- tuning ----
	/** How often each player is looked over for a witch's hut near them (ticks), and the chance a thrall rises then. */
	static final int EVERY = 20 * 25;
	static final float RISE_CHANCE = 0.5F;
	/** How far from a player a witch may be for her hut to count (blocks). */
	static final double LOOK_RANGE = 48.0;
	/** The most thralls keeping one hut at a time, counted this far out from it (blocks). */
	static final int MOST = 3;
	static final double GUARD_RANGE = 24.0;
	/** How near the hut its thralls keep (blocks), and how far from its middle one rises (blocks, flat). */
	static final int HOME_RADIUS = 14;
	static final int SITE_MIN = 3, SITE_MAX = 10, SITE_TRIES = 10;
	/** The hut's thralls near a witch who is struck go for whoever struck her (blocks). */
	static final double ALLY_RANGE = 24.0;

	/** On every thrall; with {@code "." + the hut's chunk}, which hut it keeps. */
	static final String TAG = "jaymoskingdoms.witch_thrall";

	private WitchThralls() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WitchThralls::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(thrall(entity) && source.getEntity() instanceof Witch));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> struck(entity, source));
	}

	/** One of the witches' thralls. */
	public static boolean thrall(LivingEntity entity) {
		return entity instanceof Zombie && entity.entityTags().contains(TAG);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isDarkOutside()) {
			return;
		}
		RandomSource random = level.getRandom();
		Set<Long> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			for (Witch witch : level.getEntitiesOfClass(Witch.class, player.getBoundingBox().inflate(LOOK_RANGE), Witch::isAlive)) {
				StructureStart hut = hut(level, witch.blockPosition());
				if (hut == null || !seen.add(hut.getChunkPos().pack())) {
					continue;
				}
				String tag = TAG + "." + hut.getChunkPos().pack();
				BoundingBox box = hut.getBoundingBox();
				AABB around = AABB.of(box).inflate(GUARD_RANGE);
				int keeping = level.getEntitiesOfClass(Zombie.class, around, z -> z.isAlive() && z.entityTags().contains(tag)).size();
				if (keeping < MOST && random.nextFloat() < RISE_CHANCE) {
					rise(level, box.getCenter(), tag, random);
				}
			}
		}
	}

	/** The witch's hut {@code pos} is in, or null. */
	private static @Nullable StructureStart hut(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(BuiltinStructures.SWAMP_HUT));
		return start.isValid() ? start : null;
	}

	/** One of the dead drags itself up out of the mud near the hut at {@code middle}, and keeps it. */
	private static void rise(ServerLevel level, BlockPos middle, String tag, RandomSource random) {
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN + 1);
			BlockPos column = middle.offset((int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));
			if (!com.jaymoskingdoms.Loaded.made(level, column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getBlockState(at.below()).isSolid()) {
				continue;
			}
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			if (zombie == null) {
				return;
			}
			zombie.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(zombie)) {
				continue;
			}
			zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			zombie.setPersistenceRequired();
			zombie.setHomeTo(middle, HOME_RADIUS);
			zombie.addTag(TAG);
			zombie.addTag(tag);
			level.addFreshEntity(zombie);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), zombie.getX(), zombie.getY() + 0.3,
				zombie.getZ(), 24, 0.4, 0.3, 0.4, 0.1);
			level.playSound(null, at, SoundEvents.MUD_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
			level.playSound(null, at, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F);
			return;
		}
	}

	/** A witch was struck: her thralls near her go for whoever did it. */
	private static void struck(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Witch witch) || !(witch.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
			|| attacker instanceof Witch || thrall(attacker) || attacker instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		List<Zombie> thralls = level.getEntitiesOfClass(Zombie.class, witch.getBoundingBox().inflate(ALLY_RANGE), z -> z.isAlive() && thrall(z));
		for (Zombie thrall : thralls) {
			thrall.setTarget(attacker);
		}
	}
}
