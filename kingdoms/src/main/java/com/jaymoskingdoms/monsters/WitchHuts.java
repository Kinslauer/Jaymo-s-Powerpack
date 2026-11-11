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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The witches' mud golems. Andy: the mud golem "should spawn in witch huts as their allies, in the swamp as regular
 * threats" (its swamp spawns are in {@code Encounters}). Wherever a witch keeps a swamp hut and a player comes near, now and
 * then a mud golem heaves itself up out of the mud by the hut, up to {@link #MOST} at a time. They keep near the hut, a
 * witch's potions never hurt them, and whoever strikes a witch near them has the hut's golems to deal with.
 */
public final class WitchHuts {
	// ---- tuning ----
	/** How often each player is looked over for a witch's hut near them (ticks), and the chance a golem rises then. */
	static final int EVERY = 20 * 20;
	static final float RISE_CHANCE = 0.5F;
	/** How far from a player a witch may be for her hut to count (blocks). */
	static final double LOOK_RANGE = 48.0;
	/** The most golems keeping one hut at a time, counted this far out from it (blocks). */
	static final int MOST = 2;
	static final double GUARD_RANGE = 24.0;
	/** How near the hut its golems keep (blocks). */
	static final int HOME_RADIUS = 12;
	/** A golem rises this far from the hut's middle (blocks, flat). */
	static final int SITE_MIN = 3, SITE_MAX = 9, SITE_TRIES = 10;
	/** The hut's golems near a witch who is struck go for whoever struck her (blocks). */
	static final double ALLY_RANGE = 24.0;

	/** On every hut golem; with {@code "." + the hut's chunk}, which hut it keeps. */
	static final String TAG = "jaymoskingdoms.witch_ally";

	private WitchHuts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WitchHuts::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(ally(entity) && source.getEntity() instanceof Witch));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> struck(entity, source));
	}

	/** One of the witches' golems. */
	public static boolean ally(LivingEntity entity) {
		return entity instanceof MudGolem && entity.entityTags().contains(TAG);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.getDifficulty() == Difficulty.PEACEFUL || Monsters.MUD_GOLEM == null) {
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
				int keeping = level.getEntitiesOfClass(MudGolem.class, around, g -> g.isAlive() && g.entityTags().contains(tag)).size();
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

	/** A golem heaves itself up out of the mud near the hut at {@code middle}, and keeps it. */
	private static void rise(ServerLevel level, BlockPos middle, String tag, RandomSource random) {
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN + 1);
			BlockPos column = middle.offset((int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			BlockPos below = at.below();
			boolean footing = level.getBlockState(below).isSolid()
				|| level.getFluidState(below).isSource() && level.getBlockState(below.below()).isSolid();
			if (!footing) {
				continue;
			}
			MudGolem golem = Monsters.MUD_GOLEM.create(level, EntitySpawnReason.EVENT);
			if (golem == null) {
				return;
			}
			golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(golem)) {
				continue;
			}
			golem.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			golem.setPersistenceRequired();
			golem.setHomeTo(middle, HOME_RADIUS);
			golem.addTag(TAG);
			golem.addTag(tag);
			level.addFreshEntity(golem);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), golem.getX(), golem.getY() + 0.5,
				golem.getZ(), 30, 0.6, 0.4, 0.6, 0.1);
			level.playSound(null, at, SoundEvents.MUD_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
			return;
		}
	}

	/** A witch was struck: the hut golems near her go for whoever did it. */
	private static void struck(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Witch witch) || !(witch.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
			|| attacker instanceof Witch || attacker instanceof MudGolem || attacker instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		List<MudGolem> allies = level.getEntitiesOfClass(MudGolem.class, witch.getBoundingBox().inflate(ALLY_RANGE), g -> g.isAlive() && ally(g));
		for (MudGolem golem : allies) {
			golem.setTarget(attacker);
		}
	}
}
