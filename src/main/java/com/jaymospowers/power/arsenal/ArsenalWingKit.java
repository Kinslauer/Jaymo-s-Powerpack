package com.jaymospowers.power.arsenal;

import com.jaymospowers.armory.GunBlast;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.chrono.future.MuzzleFlash;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the Attendant Blades ({@link ArsenalWing}) do because of what they are:
 * <ul>
 * <li><b>Guns and bows</b> on the wing don't fly at a target, they fire from where they hang. A bow or crossbow looses
 * an arrow and is ready again like any blade; a flintlock or musket fires its one shot, with its muzzle flash, and goes
 * home (one of the holder's own, back to their pack).</li>
 * <li><b>Working hands</b>: while the wing is steered (Sneak + B held) at a block, the first tool on it that suits the
 * block digs it, at that tool's speed, with its drops. A tool of the holder's own wears as it would in the hand.</li>
 * <li><b>The Void wing</b>: Endermen near a corrupted holder's blades turn on the holder.</li>
 * </ul>
 * A relic's own gift on a flying blade is in {@code RelicWeapons.flyingCut} (from {@code RiftBlade}).
 */
final class ArsenalWingKit {
	/** A gun on the wing: what its shot does. */
	static final float GUN_DAMAGE = 9.0F;
	static final float ARROW_SPEED = 2.6F;
	/** The Void wing: this often, one Enderman within this range with nothing else to hunt turns on the holder. */
	static final int NOTICE_EVERY = 80;
	static final double NOTICE_RANGE = 16.0;

	/** What each holder's wing is digging, and how far through (0 to 1). */
	private record Dig(BlockPos pos, float progress) {
	}

	private static final Map<UUID, Dig> DIGS = new HashMap<>();

	private ArsenalWingKit() {
	}

	static boolean gun(ItemStack stack) {
		return GunBlast.Gun.of(stack) != null || ArsenalFlintlock.isFlintlock(stack);
	}

	static boolean shoots(ItemStack stack) {
		return gun(stack) || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	/** Blade {@code i} (a gun or a bow) fires at {@code target} from where it hangs. */
	static void shoot(ServerPlayer player, ArsenalWing.Wing wing, int i, LivingEntity target) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		ArsenalWing.Blade blade = wing.blades().get(i);
		boolean corrupted = Fate.isCorrupted(player);
		Vec3 from = ArsenalWing.slot(player, wing, i, now, 1.0F);
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 dir = to.subtract(from).normalize();
		List<ArsenalWing.Blade> blades = new ArrayList<>(wing.blades());
		if (!gun(blade.item())) {
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			arrow.setPos(from);
			arrow.shoot(dir.x, dir.y + 0.02, dir.z, ARROW_SPEED, 1.0F);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			level.addFreshEntity(arrow);
			level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
			blades.set(i, blade.away(now + ArsenalWing.RETURN_TICKS));
			player.setAttached(ArsenalWing.WING, wing.with(blades));
			return;
		}
		// one ball, straight: it lands unless something solid is in the way
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() == HitResult.Type.MISS) {
			target.setInvulnerableTime(0);
			if (target.hurtServer(level, player.damageSources().playerAttack(player), GUN_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F))) {
				com.jaymospowers.core.BladeBlood.shot(target, player, GUN_DAMAGE);
				if (corrupted) {
					ArsenalVoid.voidHit(target, player, false);
				}
			}
		}
		MuzzleFlash.execution(level, blade.item(), from.add(dir.scale(0.5)), dir);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.7F);
		// spent: home it goes
		Summoned.dissolveEffects(level, from);
		ArsenalWing.home(player, blade);
		blades.remove(i);
		if (blades.isEmpty()) {
			player.removeAttached(ArsenalWing.WING);
		} else {
			player.setAttached(ArsenalWing.WING, wing.with(blades));
		}
	}

	/** Every tick the wing is steered: a tool on it digs the block its holder looks at. */
	static void work(ServerPlayer player, ArsenalWing.Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(ArsenalWing.STEER_RANGE)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		ItemStack tool = null;
		if (hit.getType() != HitResult.Type.MISS && hardness >= 0.0F && now - wing.modeStart() >= ArsenalWing.STEER_OUT_TICKS) {
			for (ArsenalWing.Blade blade : wing.blades()) {
				if (blade.back() <= now && !blade.item().isEmpty() && blade.item().getDestroySpeed(state) > 1.0F) {
					tool = blade.item();
					break;
				}
			}
		}
		Dig dig = DIGS.get(id);
		if (tool == null || !ArsenalDamocles.mayBreak(player, level, pos, state)) {
			stopWork(player);
			return;
		}
		if (dig == null || !dig.pos().equals(pos)) {
			stopWork(player);
			dig = new Dig(pos.immutable(), 0.0F);
		}
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float progress = dig.progress() + tool.getDestroySpeed(state) / Math.max(hardness, 0.05F) / (correct ? 30.0F : 100.0F);
		if (progress < 1.0F) {
			DIGS.put(id, new Dig(dig.pos(), progress));
			level.destroyBlockProgress(crackId(player), pos, (int) (progress * 10.0F) - 1);
			if (now % 4 == 0) {
				level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 0.8F);
			}
			return;
		}
		DIGS.remove(id);
		level.destroyBlockProgress(crackId(player), pos, -1);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (correct) {
			Block.dropResources(state, level, pos, blockEntity, player, tool);
		}
		level.destroyBlock(pos, false, player);
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
		if (!Summoned.is(tool)) {
			tool.hurtAndBreak(1, level, player, item -> {
			});
		}
	}

	/** The wing is no longer at a block: its cracks go. */
	static void stopWork(ServerPlayer player) {
		Dig dig = DIGS.remove(player.getUUID());
		if (dig != null) {
			player.level().destroyBlockProgress(crackId(player), dig.pos(), -1);
		}
	}

	/** Cracks drawn by the wing never share an id with the ones its holder's own hands draw. */
	private static int crackId(ServerPlayer player) {
		return -player.getId() - 4200;
	}

	/** The Void wing is out: now and then an Enderman near it turns on its holder. */
	static void notice(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!Fate.isCorrupted(player) || (level.getGameTime() + player.getId()) % NOTICE_EVERY != 0 || player.isCreative()) {
			return;
		}
		for (Enderman enderman : level.getEntitiesOfClass(Enderman.class, player.getBoundingBox().inflate(NOTICE_RANGE),
			e -> e.isAlive() && e.getTarget() == null && e.hasLineOfSight(player))) {
			enderman.setTarget(player);
			return;
		}
	}
}
