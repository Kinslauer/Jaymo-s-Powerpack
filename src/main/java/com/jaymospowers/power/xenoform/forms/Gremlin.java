package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gremlin, the tiny tinkerer (scanned from a silverfish): a third your size, so it fits through one-block gaps, 10
 * health, quick.
 * <ul>
 * <li>R, Spring: a huge jump, landing softly.</li>
 * <li>G, Sabotage: from up to {@link #SABOTAGE_REACH} blocks, flip a lever or button, throw a door, trapdoor or gate,
 * or pop a piece of redstone off its block.</li>
 * <li>V, Bite: a nip that steals hunger from the bitten and feeds you.</li>
 * <li>B (hold), Climb: scuttle straight up any wall.</li>
 * </ul>
 */
public final class Gremlin extends XenoForm {
	// ---- tuning ----
	public static final int SPRING_COOLDOWN = 30;
	public static final double SPRING_UP = 1.1, SPRING_OUT = 0.6;
	public static final int SABOTAGE_COOLDOWN = 100;
	public static final double SABOTAGE_REACH = 16.0;
	public static final int BITE_COOLDOWN = 60, BITE_FOOD = 4, BITE_HUNGER_TICKS = 200;
	public static final double BITE_REACH = 2.5;
	public static final float BITE_DAMAGE = 3.0F, BITE_HEAL = 2.0F;
	public static final double CLIMB_SPEED = 0.18;

	public Gremlin() {
		super("gremlin", 0.3F, 10.0F, 2.0, 0.2, 0.0, 0.0, Items.REDSTONE, 0x9ADC5A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Silverfish;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.climb = false;
		live.spring = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			live.climb = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && (player.onGround() || player.horizontalCollision)) {
					Vec3 out = player.getLookAngle().horizontal();
					out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(SPRING_OUT);
					player.setDeltaMovement(out.x, SPRING_UP, out.z);
					player.syncVelocity = true;
					live.spring = 1;
					sound(player, SoundEvents.SLIME_JUMP_SMALL, 1.0F, 1.6F);
					burst(player.level(), ParticleTypes.POOF, player.position(), 6, 0.2, 0.02);
					cooldown(player, live, R, SPRING_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && sabotage(player)) {
					cooldown(player, live, G, SABOTAGE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && bite(player)) {
					cooldown(player, live, V, BITE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (live.spring > 0) {
			live.spring++;
			if (live.spring > 3 && (player.onGround() || player.isInWater())) {
				live.spring = 0;
			} else if (live.spring > 200) {
				live.spring = 0;
			}
		}
		if (live.climb && player.horizontalCollision && !player.onGround() && !player.isInWater()) {
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, CLIMB_SPEED, motion.z);
			player.syncVelocity = true;
			player.resetFallDistance();
			if (player.tickCount % 6 == 0) {
				sound(player, SoundEvents.SILVERFISH_STEP, 0.6F, 1.5F);
			}
		}
	}

	/** Light enough to land from anything. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.climb;
	}

	/** The redstone thing you look at, meddled with. */
	private static boolean sabotage(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(SABOTAGE_REACH)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		boolean done;
		if (block instanceof LeverBlock lever) {
			lever.pull(state, level, pos, null);
			done = true;
		} else if (block instanceof ButtonBlock button) {
			button.press(state, level, pos, null);
			done = true;
		} else if (block instanceof DoorBlock door) {
			door.setOpen(player, level, state, pos, !state.getValue(DoorBlock.OPEN));
			done = true;
		} else if (block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
			level.setBlock(pos, state.cycle(TrapDoorBlock.OPEN), Block.UPDATE_ALL);
			level.playSound(null, pos, state.getValue(TrapDoorBlock.OPEN) ? SoundEvents.WOODEN_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_OPEN,
				SoundSource.BLOCKS, 1.0F, 1.0F);
			done = true;
		} else if (block instanceof RedstoneWireBlock || block instanceof RepeaterBlock || block instanceof ComparatorBlock || block instanceof RedstoneTorchBlock) {
			level.destroyBlock(pos, true, player);
			done = true;
		} else {
			done = false;
		}
		if (done) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
		return done;
	}

	private static boolean bite(ServerPlayer player) {
		LivingEntity target = aimed(player, BITE_REACH, e -> true);
		if (target == null) {
			return false;
		}
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.hurtServer(level, player.damageSources().playerAttack(player), BITE_DAMAGE);
		if (target instanceof Player bitten) {
			bitten.getFoodData().setFoodLevel(Math.max(0, bitten.getFoodData().getFoodLevel() - BITE_FOOD));
		} else {
			target.addEffect(new MobEffectInstance(MobEffects.HUNGER, BITE_HUNGER_TICKS, 1));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BITE_HUNGER_TICKS / 2, 0));
		}
		player.heal(BITE_HEAL);
		player.getFoodData().eat(2, 0.3F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
		sound(player, SoundEvents.GENERIC_EAT.value(), 1.0F, 1.5F);
		sound(player, SoundEvents.SILVERFISH_HURT, 0.6F, 1.8F);
		return true;
	}
}
