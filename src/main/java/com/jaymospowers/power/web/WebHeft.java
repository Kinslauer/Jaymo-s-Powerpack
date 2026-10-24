package com.jaymospowers.power.web;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A web line has weight to deal with and limits, the way a real grappling line does (our own take on grappling-hook
 * mods' rope physics): what it catches has a weight against how much the slinger can haul, and the heavier the catch,
 * the more it hauls back on them (several slingers on one catch add up); it plucks riders off their mounts and rips
 * torches, lanterns and bells off the wall; and it burns through in fire.
 */
public final class WebHeft {
	// ===== the knobs ================================================================================================
	/**
	 * How much a slinger can haul in (bounding-box volume, w × w × h: a player is 0.65, a horse about 2.4, an iron golem
	 * 5.3): {@link WebPower#YANK_HEAVY_VOLUME}, times {@code SUIT_STRENGTH} in the symbiote suit or {@code ARMOR_STRENGTH}
	 * in Living Armor. Anything heavier and you're hauled to it instead, unless others are already holding it too.
	 */
	static final double SUIT_STRENGTH = 1.5, ARMOR_STRENGTH = 2.0;
	/**
	 * A catch weighing more than {@code RECOIL_FROM} of what you can haul hauls back: the pull jerks you toward it, up to
	 * {@code RECOIL_SPEED} (blocks/tick) for one at your limit.
	 */
	static final double RECOIL_FROM = 0.35, RECOIL_SPEED = 0.9;
	/**
	 * Held on the Yank line, a heavy catch fights it: the line pulls it back that much more weakly (down to
	 * {@code 1 - DRAG_WEAKEN}), and when it pulls away it drags you along ({@code DRAG_PULL} b/t per block past the
	 * line's length, at most {@code DRAG_MAX}, every {@code DRAG_EVERY} ticks).
	 */
	static final double DRAG_WEAKEN = 0.7, DRAG_PULL = 0.12, DRAG_MAX = 0.45;
	static final int DRAG_EVERY = 4;
	/** Ripping a torch, lantern or bell off: reach (blocks) and the cooldown after (ticks). */
	static final double RIP_RANGE = 24.0;
	static final int RIP_COOLDOWN = 20;
	/** Lines are checked for fire this often (ticks), every {@code BURN_STEP} blocks along. */
	static final int BURN_EVERY = 5;
	static final double BURN_STEP = 0.75;
	// ================================================================================================================

	private WebHeft() {
	}

	/** How heavy {@code entity} is to a web (its bounding-box volume; a heavy corpse can't be hauled at all). */
	static double weight(Entity entity) {
		if (KitchenCompat.isHeavy(entity)) {
			return Double.POSITIVE_INFINITY;
		}
		return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
	}

	/** How much {@code player} can haul on a web line by themselves. */
	static double strength(ServerPlayer player) {
		double scale = WebSymbiote.livingArmor(player) ? ARMOR_STRENGTH : WebSymbiote.suitShown(player) ? SUIT_STRENGTH : 1.0;
		return WebPower.YANK_HEAVY_VOLUME * scale;
	}

	/**
	 * A rider caught by the web is plucked off what they ride; aimed at a ridden mount, the web takes the rider. Returns
	 * what the web ends up holding.
	 */
	static Entity pluck(ServerPlayer player, Entity target) {
		Entity rider = target.getFirstPassenger();
		if (rider != null && rider != player && !target.hasPassenger(player)) {
			target = rider;
		}
		if (target.isPassenger() && target.getVehicle() != player) {
			target.stopRiding();
			ServerLevel level = player.level();
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return target;
	}

	/** The pull hauls back on you: the share of your strength the catch weighs ({@code load}) jerks you toward it. */
	static void recoil(ServerPlayer player, Entity target, double load) {
		if (load <= RECOIL_FROM) {
			return;
		}
		double speed = RECOIL_SPEED * Mth.clamp((load - RECOIL_FROM) / (1.0 - RECOIL_FROM), 0.0, 1.0);
		Vec3 dir = target.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 kick = dir.normalize().scale(speed).add(0.0, 0.15 * speed, 0.0);
		WebPower.shove(player, kick);
	}

	/**
	 * Held on the Yank line: a catch weighing {@code load} of your strength pulls away from you, {@code over} blocks past
	 * the line's length. Drags you after it now and then; returns how much of its pull the line keeps.
	 */
	static double drag(ServerPlayer player, Entity target, double load, double over, boolean swinging) {
		if (load > RECOIL_FROM && over > 0.5 && !swinging && player.tickCount % DRAG_EVERY == 0) {
			Vec3 dir = target.position().subtract(player.position());
			if (dir.lengthSqr() > 1.0E-4) {
				double pull = Math.min(DRAG_MAX, over * DRAG_PULL * load);
				WebPower.shove(player, dir.normalize().scale(pull));
			}
		}
		return 1.0 - DRAG_WEAKEN * Mth.clamp(load, 0.0, 1.0);
	}

	/** Whether the web tears {@code state} off the wall when it's yanked (torches, lanterns, bells). */
	static boolean rippable(BlockState state) {
		Block block = state.getBlock();
		return block instanceof BaseTorchBlock || block instanceof LanternBlock || block instanceof BellBlock;
	}

	/**
	 * B aimed at a torch, lantern or bell: the web tears it off and it flies back to you. True if it did (or tried, and
	 * the block is protected: then nothing else happens on that B).
	 */
	static boolean ripOut(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(RIP_RANGE * WebSymbiote.reachScale(player)));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!rippable(state)) {
			return false;
		}
		Vec3 at = hit.getLocation();
		WebPower.showLine(player, new WebLine(WebLine.ZIP, at, -1), 6);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode())
			|| !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
			// it holds: someone's protecting it
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.6F, 1.4F);
			return true;
		}
		java.util.List<ItemStack> drops = player.isCreative() ? java.util.List.of() : Block.getDrops(state, level, pos, blockEntity);
		level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		level.removeBlock(pos, false);
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
		Vec3 from = Vec3.atCenterOf(pos);
		for (ItemStack stack : drops) {
			ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, stack);
			item.setDeltaMovement(toss(from, player.position().add(0.0, 1.0, 0.0)));
			item.setPickUpDelay(4);
			level.addFreshEntity(item);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
		return true;
	}

	/** A velocity that throws something from {@code from} on a rough arc to land at {@code to}. */
	private static Vec3 toss(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		// about one tick of flight per block and a half, under item gravity (0.04/tick)
		double ticks = Mth.clamp(d.length() / 1.5, 4.0, 16.0);
		return new Vec3(d.x / ticks, d.y / ticks + 0.02 * ticks, d.z / ticks);
	}

	/**
	 * Where a line from {@code from} to {@code to} (stuck to {@code stuckTo}, if anything) runs through fire, lava or a
	 * burning creature, or null if it doesn't. A web line burns through there.
	 */
	static @Nullable Vec3 burning(ServerLevel level, Vec3 from, Vec3 to, @Nullable Entity stuckTo) {
		if (stuckTo != null && stuckTo.isOnFire()) {
			return to;
		}
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int)Math.ceil(length / BURN_STEP));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			pos.set(p.x, p.y, p.z);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BaseFireBlock || CampfireBlock.isLitCampfire(state) || state.getFluidState().is(FluidTags.LAVA)) {
				return p;
			}
		}
		return null;
	}

	/** A line burns through at {@code at}: a flare of flame and the hiss of it going. */
	static void burnFx(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
	}
}
