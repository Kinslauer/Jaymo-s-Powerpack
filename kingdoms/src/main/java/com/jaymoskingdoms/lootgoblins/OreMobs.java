package com.jaymoskingdoms.lootgoblins;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * The minor goblins: a vanilla mob made of an ore, sparking like a loot goblin. Each fights as its vanilla self, drops its
 * vanilla loot, and when a player kills it bursts into its own treasure (one item, {@link LootItems}) and a pile of loot:
 * the golden skeleton (golden bones), lapis zombie (stoneflesh; no sunburn, never drowns into a drowned), iron spider (iron
 * eyes), redstone creeper (red bombs), quartz hoglin (quartzchops; never zombifies), emerald illager (an emerald crossbow;
 * it fights with a plain one) and prismarine enderman (prismarine pearls; water doesn't hurt it).
 */
public final class OreMobs {
	private OreMobs() {
	}

	private static void burst(ServerLevel level, LivingEntity mob, boolean killedByPlayer, String treasure) {
		if (killedByPlayer) {
			LootGoblins.burst(level, mob, new String[] {JaymosKingdoms.MOD_ID + ":" + treasure});
		}
	}

	public static class GoldenSkeleton extends Skeleton {
		public GoldenSkeleton(EntityType<? extends GoldenSkeleton> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		public boolean isShaking() {
			return false;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "gold_bone");
		}
	}

	public static class LapisZombie extends Zombie {
		public LapisZombie(EntityType<? extends LapisZombie> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		protected boolean isSunSensitive() {
			return false;
		}

		@Override
		protected boolean convertsInWater() {
			return false;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "stoneflesh");
		}
	}

	public static class IronSpider extends Spider {
		public IronSpider(EntityType<? extends IronSpider> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "iron_eye");
		}
	}

	public static class RedstoneCreeper extends Creeper {
		public RedstoneCreeper(EntityType<? extends RedstoneCreeper> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "red_bomb");
		}
	}

	public static class QuartzHoglin extends Hoglin {
		public QuartzHoglin(EntityType<? extends QuartzHoglin> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
			this.setImmuneToZombification(true);
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "quartzchop");
		}
	}

	public static class EmeraldIllager extends Pillager {
		public EmeraldIllager(EntityType<? extends EmeraldIllager> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "emerald_crossbow");
		}
	}

	public static class PrismarineEnderman extends Enderman {
		public PrismarineEnderman(EntityType<? extends PrismarineEnderman> type, Level level) {
			super(type, level);
			this.xpReward = LootGoblins.MINOR_XP;
		}

		@Override
		public boolean isSensitiveToWater() {
			return false;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			LootGoblin.spark(this);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			burst(level, this, killedByPlayer, "prismarine_pearl");
		}
	}
}
