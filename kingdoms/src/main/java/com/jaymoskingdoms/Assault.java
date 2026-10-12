package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.12) Attackers (war parties' soldiers and raiders) fight harder and leave a mark. They march and fight
 * strengthened. One that gets no nearer its foe or its goal for a while breaks down the door, fence or window in its
 * way, and sets fire to a wooden wall it can't get past (then steps back from the flames). And on enemy land they put
 * its buildings to the torch now and then: wood, wool, hay and thatch of anything built (not trees). Burnt buildings
 * are put up again by their builders ({@link Rebuilds}), and villagers fight the fires ({@link Firefighting}).
 */
final class Assault {
	/** No nearer for this long (ticks) and it clears the way. */
	static final int STUCK_TICKS = 60;
	/** It's stuck only with its goal further than this. */
	static final double STUCK_FROM = 3.0;
	/** Their strength while attacking: re-applied this often (ticks), lasting this long. */
	static final int BUFF_EVERY = 100, BUFF_TICKS = 300;
	/** On enemy land: how often each attacker may torch something (ticks), the chance, and how far it reaches. */
	static final int TORCH_EVERY = 160;
	static final float TORCH_CHANCE = 0.35F;
	static final int TORCH_REACH = 6;

	private static final class Progress {
		double best = Double.MAX_VALUE;
		long since;
		@Nullable Vec3 goal;
	}

	private static final Map<Folk, Progress> PROGRESS = new WeakHashMap<>();

	private Assault() {
	}

	/** On the attack: a war party's soldier or a raider. */
	static boolean attacker(Folk folk) {
		return WarParties.isSoldier(folk) || Raids.isRaider(folk);
	}

	/** From {@link Folk#customServerAiStep}. */
	static void tick(ServerLevel level, Folk folk) {
		if (!attacker(folk)) {
			PROGRESS.remove(folk);
			return;
		}
		long now = level.getGameTime();
		if ((now + folk.getId()) % BUFF_EVERY == 0) {
			folk.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BUFF_TICKS, 0, false, false));
			if (folk.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && folk.role() != Folk.ARCHER) {
				folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
			}
		}
		stuck(level, folk, now);
		if ((now + folk.getId()) % TORCH_EVERY == 0 && level.getRandom().nextFloat() < TORCH_CHANCE) {
			torch(level, folk);
		}
	}

	// ---- clearing the way --------------------------------------------------------------------------------------------

	private static void stuck(ServerLevel level, Folk folk, long now) {
		LivingEntity foe = folk.getTarget();
		BlockPos navTarget = folk.getNavigation().getTargetPos();
		Vec3 goal = foe != null ? foe.position() : navTarget != null && !folk.getNavigation().isDone() ? Vec3.atBottomCenterOf(navTarget) : null;
		Progress p = PROGRESS.computeIfAbsent(folk, f -> new Progress());
		if (goal == null) {
			p.goal = null;
			return;
		}
		double d = folk.position().distanceTo(goal);
		if (p.goal == null || p.goal.distanceToSqr(goal) > 16.0) {
			p.goal = goal;
			p.best = d;
			p.since = now;
			return;
		}
		if (d < p.best - 0.75) {
			p.best = d;
			p.since = now;
			return;
		}
		if (d <= STUCK_FROM || now - p.since < STUCK_TICKS) {
			return;
		}
		p.since = now;
		p.best = d;
		Direction toward = Direction.getApproximateNearest(goal.x - folk.getX(), 0.0, goal.z - folk.getZ());
		BlockPos feet = folk.blockPosition();
		for (int ahead = 1; ahead <= 2; ahead++) {
			for (int up = 0; up <= 1; up++) {
				BlockPos at = feet.relative(toward, ahead).above(up);
				if (clear(level, folk, at, toward)) {
					return;
				}
			}
		}
	}

	/** Breaks or burns what's at {@code at} (true if it did). */
	private static boolean clear(ServerLevel level, Folk folk, BlockPos at, Direction toward) {
		BlockState state = level.getBlockState(at);
		if (state.isAir() || !mayHarm(level, folk, at)) {
			return false;
		}
		Block block = state.getBlock();
		boolean breaks = block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof FenceBlock || block instanceof TrapDoorBlock
			|| block instanceof IronBarsBlock || block instanceof LeavesBlock || state.is(BlockTags.IMPERMEABLE) || state.is(BlockTags.WOOL);
		if (breaks && state.getDestroySpeed(level, at) >= 0 && state.getDestroySpeed(level, at) < 10.0F) {
			folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.destroyBlock(at, false, folk);
			if (block instanceof DoorBlock) {
				level.playSound(null, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
			}
			return true;
		}
		if (state.ignitedByLava()) {
			// a wooden wall: fire set to it from this side, and back off
			BlockPos side = at.relative(toward.getOpposite());
			if (ignite(level, folk, at, side)) {
				Vec3 back = folk.position().subtract(Vec3.atLowerCornerOf(toward.getUnitVec3i()).scale(4.0));
				folk.getNavigation().moveTo(back.x, back.y, back.z, 1.1);
				return true;
			}
		}
		return false;
	}

	// ---- torching ----------------------------------------------------------------------------------------------------

	/** On enemy land, something built and wooden near it is set alight. */
	private static void torch(ServerLevel level, Folk folk) {
		if (!enemyLand(level, folk, folk.blockPosition())) {
			return;
		}
		var random = level.getRandom();
		for (int tries = 0; tries < 24; tries++) {
			BlockPos at = folk.blockPosition().offset(random.nextInt(TORCH_REACH * 2 + 1) - TORCH_REACH, random.nextInt(5) - 1,
				random.nextInt(TORCH_REACH * 2 + 1) - TORCH_REACH);
			BlockState state = level.getBlockState(at);
			if (!state.ignitedByLava() || state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LOGS) && Trees.natural(level, at)
				|| state.is(BlockTags.SAPLINGS) || !mayHarm(level, folk, at)) {
				continue;
			}
			for (Direction d : Direction.values()) {
				if (ignite(level, folk, at, at.relative(d))) {
					return;
				}
			}
		}
	}

	/** Fire put at {@code air} against {@code fuel} (true if it caught). */
	private static boolean ignite(ServerLevel level, Folk folk, BlockPos fuel, BlockPos air) {
		if (!level.getBlockState(air).isAir() || folk.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(air))) {
			return false;
		}
		BlockState fire = BaseFireBlock.getState(level, air);
		if (!fire.canSurvive(level, air)) {
			return false;
		}
		folk.getLookControl().setLookAt(fuel.getX() + 0.5, fuel.getY() + 0.5, fuel.getZ() + 0.5);
		folk.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.setBlock(air, fire, Block.UPDATE_ALL_IMMEDIATE);
		level.playSound(null, air, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
		return true;
	}

	/** Its land is an enemy's (claimed by a faction not its own and not allied with it). */
	private static boolean enemyLand(ServerLevel level, Folk folk, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Faction owner = Banners.owner(server, ChunkPos.containing(pos));
		return owner != null && owner.id() != folk.factionId() && Relations.between(server, folk.factionId(), owner.id()) != Relation.ALLIED;
	}

	/** It may break or burn here: not on its own side's land. */
	private static boolean mayHarm(ServerLevel level, Folk folk, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Faction owner = Banners.owner(server, ChunkPos.containing(pos));
		return owner == null || owner.id() != folk.factionId() && Relations.between(server, folk.factionId(), owner.id()) != Relation.ALLIED;
	}
}
