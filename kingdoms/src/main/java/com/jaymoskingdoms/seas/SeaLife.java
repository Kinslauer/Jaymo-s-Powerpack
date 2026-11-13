package com.jaymoskingdoms.seas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Sea life lives out at sea (Andy's ocean pick-list: "most sea life spawn further out"): fish, squid, dolphins, turtles'
 * fellows and whatever else the sea spawns on its own come up thin along the coast and thick far out, so a walk on the
 * shore is quiet and a voyage is where the life is. Rivers, lakes and caves are left as they were; only the sea's own
 * spawns ({@link #keep}, asked by {@code SeaLifeSpawnMixin}) are thinned, by how far out they'd be ({@link Offshore#far}).
 * The sea's spawn limits stay the same, so what the shore doesn't take goes further out.
 */
public final class SeaLife {
	/** The share of the sea's spawns kept right at the coast; out past {@link Offshore#OPEN}, all of them. */
	static final float COAST_KEEP = 0.15F;

	private SeaLife() {
	}

	/** Whether a natural spawn of {@code mob} at its place goes ahead. */
	public static boolean keep(ServerLevel level, Mob mob) {
		MobCategory category = mob.getType().getCategory();
		if (category != MobCategory.WATER_CREATURE && category != MobCategory.WATER_AMBIENT || level.dimension() != Level.OVERWORLD) {
			return true;
		}
		BlockPos pos = mob.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_BEACH)) {
			return true;
		}
		float keep = Mth.lerp(Offshore.far(level, pos.getX(), pos.getZ()), COAST_KEEP, 1.0F);
		return keep >= 1.0F || level.getRandom().nextFloat() < keep;
	}
}
