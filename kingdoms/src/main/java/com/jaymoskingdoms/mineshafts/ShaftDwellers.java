package com.jaymoskingdoms.mineshafts;

import com.jaymoskingdoms.beasts.Beasts;
import com.jaymoskingdoms.creatures.Creatures;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, mm-creatures) Who lives down the shafts, by depth and land, kept up round a player in one: near the
 * top goblin scouts (out of a goblin village's reach, they run wild) and diggers; lower, diggers, spiders and the dead of
 * old miners (zombies, husks in the sands, with their picks and caps); deepest, spiders (a cavern spider below the
 * surface, now and then a netcaster deep down: {@code wild/SpiderKinds} sees to that) and the dead. Jungle shafts crawl
 * with spiders; mushroom shafts hold only diggers. Where a settlement works a shaft ({@link ShaftMiners}) its upper part is
 * mostly clear, while the deeper rooms keep their dwellers.
 */
public final class ShaftDwellers {
	// ---- tuning ----
	/** How often a player in a shaft is looked at (ticks); at most this many foes about them; how far round counts. */
	static final int EVERY = 300, CAP = 5;
	static final double COUNT_RANGE = 32.0;
	/** How far from the player a dweller comes (blocks), and the block light it keeps under. */
	static final int NEAR = 12, FAR = 24, LIGHT_BELOW = 8;
	/** In a worked part of a shaft, the share of the dwellers that still come. */
	static final float WORKED_SHARE = 0.2F;
	/** The heights that split the shallows, the middle and the deep. */
	static final int SHALLOW_ABOVE = 30, DEEP_BELOW = 0;
	/** The chance a dead miner carries an iron pick (else a stone one), and wears a cap. */
	static final float IRON_PICK = 0.3F, CAP_CHANCE = 0.6F;

	private ShaftDwellers() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY == 0L && level.dimension() == Level.OVERWORLD && level.getDifficulty() != Difficulty.PEACEFUL) {
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && Mineshafts.in(level, p.blockPosition())) {
						keep(level, p);
					}
				}
			}
		});
	}

	private static void keep(ServerLevel level, ServerPlayer p) {
		RandomSource random = level.getRandom();
		if (level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(COUNT_RANGE), m -> m instanceof Enemy).size() >= CAP) {
			return;
		}
		BlockPos spot = spot(level, p.blockPosition(), random);
		if (spot == null || ShaftMiners.worked(level, spot) && random.nextFloat() >= WORKED_SHARE) {
			return;
		}
		Holder<Biome> biome = level.getBiome(spot);
		boolean sands = biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS);
		EntityType<? extends Mob> kind = kind(biome, spot.getY(), sands, random);
		Mob mob = kind.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) {
			return;
		}
		mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.NATURAL, null);
		if (kind == EntityTypes.ZOMBIE || kind == EntityTypes.HUSK) {
			// one of the old miners: pick in hand, cap on
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextFloat() < IRON_PICK ? Items.IRON_PICKAXE : Items.STONE_PICKAXE));
			if (random.nextFloat() < CAP_CHANCE) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			}
		}
		level.addFreshEntityWithPassengers(mob);
	}

	/** Who comes, by the land and how deep. */
	static EntityType<? extends Mob> kind(Holder<Biome> biome, int y, boolean sands, RandomSource random) {
		EntityType<? extends Mob> dead = sands ? EntityTypes.HUSK : EntityTypes.ZOMBIE;
		if (biome.is(Biomes.MUSHROOM_FIELDS)) {
			return Creatures.DIGGER;
		}
		int spider = biome.is(BiomeTags.IS_JUNGLE) ? 3 : 0;
		int[] w;
		if (y > SHALLOW_ABOVE) {
			w = new int[] {4, 3, 2, 1 + spider};
		} else if (y > DEEP_BELOW) {
			w = new int[] {1, 3, 3, 3 + spider};
		} else {
			w = new int[] {0, 2, 3, 4 + spider};
		}
		int r = random.nextInt(w[0] + w[1] + w[2] + w[3]);
		if ((r -= w[0]) < 0) {
			return Beasts.GOBLIN_SCOUT;
		}
		if ((r -= w[1]) < 0) {
			return Creatures.DIGGER;
		}
		if (r - w[2] < 0) {
			return dead;
		}
		return EntityTypes.SPIDER;
	}

	/** A dark floor in the shaft {@link #NEAR} to {@link #FAR} from {@code from}, or null. */
	private static @Nullable BlockPos spot(ServerLevel level, BlockPos from, RandomSource random) {
		for (int tries = 0; tries < 12; tries++) {
			double a = random.nextDouble() * Math.PI * 2.0, d = NEAR + random.nextDouble() * (FAR - NEAR);
			BlockPos column = from.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos at = column.above(dy);
				if (level.isLoaded(at) && ShaftMiners.open(level, at) && ShaftMiners.open(level, at.above()) && level.getBlockState(at.below()).isSolid()
					&& level.getBrightness(LightLayer.BLOCK, at) < LIGHT_BELOW && !level.canSeeSky(at) && Mineshafts.in(level, at)) {
					return at;
				}
			}
		}
		return null;
	}
}
