package com.jaymospowers.cloudbottle;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Bottle of Cloud. Used on a block it lays a puff ({@link CloudPuffBlock}) of 2x1x2 blocks beside the face you clicked, out
 * from the spot you clicked; used in mid-air while falling it lays one under your feet. Either way the bottle is spent and the
 * glass is handed back. It does nothing where the puff would not fit (solid ground, water) or when you are not falling.
 */
public class BottleOfCloudItem extends Item {
	// ---- tuning ----
	/** The fall (blocks) a player must be into before a bottle used in the air catches them. */
	public static final float MIN_FALL = 2.0F;
	/** How many puff blocks' worth of particles the bottle puffs out. */
	private static final int SPRAY = 12;

	public BottleOfCloudItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Direction face = context.getClickedFace();
		Vec3 at = context.getClickLocation();
		BlockPos target = context.getClickedPos().relative(face);
		// the puff lies away from the clicked spot: out from a side face, toward the nearer half across an upper or lower one
		int ox = face.getAxis() == Direction.Axis.X ? face.getStepX() : at.x - Mth.floor(at.x) >= 0.5 ? 1 : -1;
		int oz = face.getAxis() == Direction.Axis.Z ? face.getStepZ() : at.z - Mth.floor(at.z) >= 0.5 ? 1 : -1;
		return lay(level, player, context.getHand(), List.of(target, target.offset(ox, 0, 0), target.offset(0, 0, oz), target.offset(ox, 0, oz)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSpectator() || player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger()
			|| player.getAbilities().flying || player.isFallFlying() || player.fallDistance < MIN_FALL) {
			return InteractionResult.PASS;
		}
		// one puff, centred under the feet
		BlockPos corner = BlockPos.containing(Mth.floor(player.getX() - 0.5), Mth.floor(player.getY()) - 1, Mth.floor(player.getZ() - 0.5));
		return lay(level, player, hand, List.of(corner, corner.east(), corner.south(), corner.east().south()));
	}

	/** Lays the puff on the cells that are open, spends the bottle; passes if none are. */
	private static InteractionResult lay(Level level, Player player, InteractionHand hand, List<BlockPos> cells) {
		if (!player.mayBuild()) {
			return InteractionResult.PASS;
		}
		List<BlockPos> open = cells.stream().filter(pos -> CloudBottle.open(level, pos) && level.mayInteract(player, pos)).toList();
		if (open.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			for (BlockPos pos : open) {
				server.setBlock(pos, CloudBottle.PUFF.defaultBlockState(), 3);
				server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SPRAY / 4, 0.3, 0.3, 0.3, 0.02);
			}
			server.playSound(null, open.get(0), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.2F);
			ItemStack held = player.getItemInHand(hand);
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
		}
		return InteractionResult.SUCCESS;
	}
}
