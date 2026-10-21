package com.jaymospowers.power.cryo;

import com.jaymospowers.power.smoke.SmokeField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) B, Flash Freeze: the creature you look at (within {@link #RANGE} blocks, not through walls) is shut in a shell
 * of packed ice for {@link #HOLD_TICKS} (3 s), frozen through: held still, too numb to strike, the cold biting. Built like
 * Kingdoms' Frost-born ice prison ({@code FrostPrison}: shorter, and far more often than its once a day): the shell goes
 * only into air round it (nothing is broken to make it) and always melts away ({@link CryoIce}), early if the prisoner
 * dies. Smoke round it turns to freezing fog.
 */
public final class CryoFlashFreeze {
	static final double RANGE = 16.0;
	public static final int HOLD_TICKS = 60;
	public static final int COOLDOWN = 240;
	public static final int COOLDOWN_MASTERED = 180;
	/** The biggest thing that fits in the ice: width and height (blocks). */
	static final double MAX_WIDTH = 2.0, MAX_HEIGHT = 3.2;
	/** Smoke this near the prisoner freezes. */
	static final double FOG_RADIUS = 2.5;

	/** A shell standing: who's in it, its blocks, and when it melts (game time). */
	private record Shell(ServerLevel level, LivingEntity target, List<BlockPos> ice, long until) {
	}

	private static final List<Shell> SHELLS = new ArrayList<>();

	private CryoFlashFreeze() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoFlashFreeze::tick);
		// (CryoIce melts the blocks themselves on a server stop)
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SHELLS.clear());
	}

	static void cast(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double range = RANGE * CryoPower.strength(player);
		HitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			range = wall.getLocation().distanceTo(eye);
		}
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(range)),
			player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof ArmorStand) && e != player && !e.isSpectator(), range * range);
		if (aimed == null || !(aimed.getEntity() instanceof LivingEntity target) || jailed(target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_freeze_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (target.getBbWidth() > MAX_WIDTH || target.getBbHeight() > MAX_HEIGHT) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_freeze_big").withStyle(ChatFormatting.GRAY));
			return;
		}
		int hold = Math.round(HOLD_TICKS * CryoPower.strength(player));
		AABB box = target.getBoundingBox();
		BlockPos lo = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos hi = BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4);
		List<BlockPos> ice = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(lo.offset(-1, -1, -1), hi.offset(1, 1, 1))) {
			boolean inside = pos.getX() >= lo.getX() && pos.getX() <= hi.getX() && pos.getY() >= lo.getY() && pos.getY() <= hi.getY()
				&& pos.getZ() >= lo.getZ() && pos.getZ() <= hi.getZ();
			if (!inside && level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()
				&& CryoIce.place(level, pos.immutable(), Blocks.PACKED_ICE.defaultBlockState(), hold, CryoIce.Mode.AIR)) {
				ice.add(pos.immutable());
			}
		}
		SHELLS.add(new Shell(level, target, ice, level.getGameTime() + hold));
		freeze(player, target);
		target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
		target.syncVelocity = true;
		SmokeField.freeze(level, box.getCenter(), FOG_RADIUS);
		CryoPower.cooldown(player, CryoState.FREEZE, CryoPower.mastered(player) ? COOLDOWN_MASTERED : COOLDOWN);
		Vec3 c = box.getCenter();
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 30, box.getXsize() * 0.6, box.getYsize() * 0.5, box.getZsize() * 0.6, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Whether {@code entity} is shut in a Flash Freeze now. */
	public static boolean jailed(LivingEntity entity) {
		for (Shell shell : SHELLS) {
			if (shell.target == entity) {
				return true;
			}
		}
		return false;
	}

	/** Held still, too numb to strike, frozen through. */
	private static void freeze(ServerPlayer by, LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9, false, false), by);
		target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 20, 4, false, false), by);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 4, false, false), by);
		if (target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
		}
	}

	private static void tick(MinecraftServer server) {
		if (SHELLS.isEmpty()) {
			return;
		}
		for (Iterator<Shell> it = SHELLS.iterator(); it.hasNext();) {
			Shell shell = it.next();
			if (!shell.target.isAlive() || shell.target.level() != shell.level) {
				// the ice falls away with nobody in it
				CryoIce.meltNow(shell.level, shell.ice, shell.until);
				it.remove();
				continue;
			}
			if (shell.level.getGameTime() >= shell.until) {
				it.remove();
				continue;
			}
			shell.target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9, false, false));
			shell.target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 20, 4, false, false));
			shell.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 4, false, false));
			if (shell.target.canFreeze()) {
				shell.target.setTicksFrozen(Math.max(shell.target.getTicksFrozen(), shell.target.getTicksRequiredToFreeze() + 20));
			}
		}
	}
}
