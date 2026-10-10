package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Sowing by hand: right-click with seeds to scatter a few in a fan in front of you. Those that land on open farmland
 * take root; the rest lie on the ground, where chickens (and anything else that eats seeds) will go and peck them up
 * (see {@link EatGroundSeedsGoal}). Sneak + right-click still plants one seed exactly where you're looking.
 */
public final class Sowing {
	/** Seeds that can be scattered (data/jaymoskitchen/tags/item/sowable_seeds.json). */
	public static final TagKey<Item> SOWABLE = TagKey.create(Registries.ITEM, JaymosKitchen.id("sowable_seeds"));
	/** Seeds per throw, how far they go (blocks ahead, nearest and furthest), and how wide the fan spreads. */
	private static final int PER_THROW = 3;
	private static final double NEAR = 1.5, FAR = 4.0, SPREAD = 1.6;
	private static final int COOLDOWN = 6;

	private Sowing() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> sow(player, level, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> sow(player, level, hand));
		// anything that eats seeds goes for them on the ground
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Animal animal && animal.isFood(new ItemStack(Items.WHEAT_SEEDS))) {
				((MobAccessor) animal).jaymoskitchen$goals().addGoal(3, new EatGroundSeedsGoal(animal));
			}
		});
	}

	private static InteractionResult sow(Player player, Level level, InteractionHand hand) {
		ItemStack seeds = player.getItemInHand(hand);
		if (!seeds.is(SOWABLE) || player.isShiftKeyDown() || player.isSpectator() || player.getCooldowns().isOnCooldown(seeds)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			scatter(server, player, seeds);
			player.getCooldowns().addCooldown(seeds, COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}

	/** Throws a handful of seeds out in front: each takes root on open farmland, or lies where it lands. */
	private static void scatter(ServerLevel level, Player player, ItemStack seeds) {
		RandomSource random = level.getRandom();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		Vec3 hand = player.getEyePosition().add(forwardX * 0.4, -0.4, forwardZ * 0.4);
		int thrown = Math.min(PER_THROW, seeds.getCount());
		ItemStack one = seeds.copyWithCount(1);
		for (int i = 0; i < thrown; i++) {
			double ahead = NEAR + random.nextDouble() * (FAR - NEAR);
			double across = (random.nextDouble() * 2.0 - 1.0) * SPREAD * (ahead / FAR);
			double x = player.getX() + forwardX * ahead + forwardZ * across;
			double z = player.getZ() + forwardZ * ahead - forwardX * across;
			BlockPos ground = findGround(level, BlockPos.containing(x, player.getY() + 1.0, z));
			Vec3 landing = ground == null ? new Vec3(x, player.getY(), z) : new Vec3(x, ground.getY() + 1.0, z);
			// a little arc of seed flying out to where it lands
			Vec3 path = landing.subtract(hand);
			for (int p = 0; p < 4; p++) {
				Vec3 at = hand.add(path.scale(p / 4.0)).add(0.0, Math.sin(p / 4.0 * Math.PI) * 0.4, 0.0);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, one.getItem()), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.02);
			}
			if (ground == null || !plant(level, ground, one, player)) {
				ItemEntity scattered = new ItemEntity(level, landing.x, landing.y + 0.05, landing.z, one.copy(), 0.0, 0.02, 0.0);
				scattered.setPickUpDelay(20);
				level.addFreshEntity(scattered);
			}
		}
		if (!player.hasInfiniteMaterials()) {
			seeds.shrink(thrown);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROP_PLANTED, SoundSource.PLAYERS, 0.7F,
			0.8F + random.nextFloat() * 0.4F);
	}

	/** The top solid block under (or at) a spot, within a few blocks. */
	private static BlockPos findGround(ServerLevel level, BlockPos start) {
		BlockPos pos = start;
		for (int i = 0; i < 6; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.canBeReplaced()) {
				return pos;
			}
			pos = pos.below();
		}
		return null;
	}

	/** Plants a seed on open farmland, as a hand would. */
	private static boolean plant(ServerLevel level, BlockPos ground, ItemStack seed, Player player) {
		if (!(seed.getItem() instanceof BlockItem blockItem) || !level.getBlockState(ground).is(Blocks.FARMLAND)) {
			return false;
		}
		BlockPos above = ground.above();
		BlockState crop = blockItem.getBlock().defaultBlockState();
		if (!level.getBlockState(above).isAir() || !crop.canSurvive(level, above)) {
			return false;
		}
		level.setBlock(above, crop, Block.UPDATE_ALL);
		level.gameEvent(GameEvent.BLOCK_PLACE, above, GameEvent.Context.of(player, crop));
		return true;
	}
}
