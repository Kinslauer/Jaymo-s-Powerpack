package com.jaymospowers.power.cryo;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.elemental.ElementalCorruption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) Cryokinesis' part in Elemental Corruption: the cold's answer. The reckoning is a flash freeze: every block
 * within {@link #FREEZE_RADIUS} blocks of the killer turns to ice (but what holds something, and what can't be broken),
 * and {@link #SPIKES} spikes of packed ice {@link #SPIKE_HEIGHT} blocks tall burst up round them. Through the wrath, the
 * passive creatures and villagers near them freeze solid, held still and taking the powder snow's freezing damage just
 * from standing too close.
 */
final class CryoWrath implements ElementalCorruption.Wrath {
	// ---- tuning ----
	/** The flash freeze's reach (blocks). */
	static final int FREEZE_RADIUS = 12;
	/** The spikes: how many, how tall, and how near and far (blocks) round the killer they stand. */
	static final int SPIKES = 6, SPIKE_HEIGHT = 7;
	static final double SPIKE_NEAR = 4.0, SPIKE_FAR = 10.0;
	/** Passive creatures this near (blocks) freeze solid; looked at every this many ticks. */
	static final double CHILL_RADIUS = 8.0;
	static final int CHILL_EVERY = 10;
	/** How far past frozen through they are kept (powder snow's ticks), and how long each hold lasts (ticks). */
	static final int OVER_FROZEN = 60, HOLD_TICKS = 30;
	/** The frost pouring off the holder. */
	static final int COLOR = 0xBFE9FF;

	@Override
	public void erupt(ServerPlayer player, ServerLevel level) {
		BlockPos at = player.blockPosition();
		int r = FREEZE_RADIUS;
		BlockState ice = Blocks.ICE.defaultBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
			if (pos.distSqr(at) > r * r) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(Blocks.ICE) || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
				continue;
			}
			// (the player stands where they stood: the blocks round their body stay open)
			if (pos.equals(at) || pos.equals(at.above())) {
				continue;
			}
			level.setBlock(pos, ice, 2);
		}
		for (int i = 0; i < SPIKES; i++) {
			double angle = Mth.TWO_PI * i / SPIKES + level.getRandom().nextDouble() * 0.6;
			double far = Mth.lerp(level.getRandom().nextDouble(), SPIKE_NEAR, SPIKE_FAR);
			int x = Mth.floor(player.getX() + Math.cos(angle) * far), z = Mth.floor(player.getZ() + Math.sin(angle) * far);
			spike(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
		MagicFx.ring(level, player.position(), FREEZE_RADIUS, COLOR);
	}

	/** A spike of packed ice: a column {@link #SPIKE_HEIGHT} tall on a short, wider foot, only into air. */
	private static void spike(ServerLevel level, BlockPos base) {
		BlockState packed = Blocks.PACKED_ICE.defaultBlockState();
		for (int y = 0; y < SPIKE_HEIGHT; y++) {
			place(level, base.above(y), packed);
		}
		for (BlockPos side : new BlockPos[] {base.north(), base.south(), base.east(), base.west()}) {
			place(level, side, packed);
			place(level, side.above(), packed);
		}
	}

	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 2);
		}
	}

	@Override
	public void tick(ServerPlayer player, ServerLevel level, long age) {
		if (age % CHILL_EVERY != 0) {
			return;
		}
		for (LivingEntity living : ElementalCorruption.near(player, CHILL_RADIUS)) {
			if (!passive(living)) {
				continue;
			}
			// frozen through and kept so: the game's own powder-snow freezing hurts them while it lasts
			living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + OVER_FROZEN));
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOLD_TICKS, 9, false, false));
			level.sendParticles(MagicFx.mote(COLOR, 0.7F), living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
				2, 0.3, 0.4, 0.3, 0.0);
		}
	}

	/** A passive creature (an animal, a bat...) or a villager: nothing hostile. */
	private static boolean passive(LivingEntity living) {
		return !(living instanceof net.minecraft.world.entity.monster.Enemy)
			&& (living instanceof Animal || living instanceof AmbientCreature || living instanceof AgeableMob || Carry.isVillager(living));
	}

	@Override
	public void aura(ServerPlayer player, ServerLevel level) {
		level.sendParticles(MagicFx.mote(COLOR, 0.9F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.5, 0.8, 0.5, 0.01);
	}
}
