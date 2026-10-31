package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.112, Scan E) Shade, the stealer: very tall and thin, a black body with long arms and legs, a narrow head and
 * glowing violet eyes. 30 health, fists of 6. Water and rain burn it. Scanned from an enderman, or Enderscape's drifter
 * ({@code XenoDna}).
 * <ul>
 * <li>R, Steal: the block you look at is pulled out and held over your head (as Polestar lifts iron); R again throws it.</li>
 * <li>G, Stare: the creature you lock eyes with is held still for as long as you keep looking at it.</li>
 * <li>V, Blink: a jump to where you look.</li>
 * <li>B, Wary: for {@link #WARY_TICKS} ticks, the next blow that would land blinks you away instead.</li>
 * </ul>
 */
public final class Shade extends XenoForm {
	// ---- tuning ----
	public static final double STEAL_REACH = 6.0;
	/** The hardest block it can pull out (stone 1.5, obsidian 50). */
	public static final float STEAL_HARDNESS = 10.0F;
	public static final double THROW_SPEED = 1.4;
	public static final float THROW_DAMAGE = 2.0F;
	public static final int THROW_MAX = 20, STEAL_COOLDOWN = 30;
	public static final double STARE_REACH = 24.0;
	public static final int STARE_TICKS = 100, STARE_COOLDOWN = 200;
	public static final double BLINK_REACH = 24.0;
	public static final int BLINK_COOLDOWN = 40;
	public static final int WARY_TICKS = 200, WARY_COOLDOWN = 300;
	public static final double WARY_RANGE = 10.0;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;

	public Shade() {
		super("shade", 1.3F, 30.0F, 6.0, 0.04, 0.2, 0.0, Items.ENDER_PEARL, 0x1A1420);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Enderman;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		if (live.lifted != null) {
			drop(player, live, Vec3.ZERO);
		}
		live.stalked = -1;
		live.stalkUntil = live.hideUntil = 0L;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (live.hideUntil == 0L || player.level().getGameTime() > live.hideUntil || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) {
			return true;
		}
		// Wary: gone before the blow lands
		if (away(player)) {
			live.hideUntil = 0L;
			return false;
		}
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (live.lifted != null) {
					drop(player, live, player.getLookAngle().scale(THROW_SPEED));
					sound(player, SoundEvents.ENDERMAN_SCREAM, 0.6F, 1.4F);
					cooldown(player, live, R, STEAL_COOLDOWN);
				} else if (ready(player, live, R) && steal(player, live)) {
					cooldown(player, live, R, STEAL_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (live.stalked >= 0) {
					live.stalked = -1;
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, STARE_REACH, e -> true);
				if (target == null) {
					return;
				}
				live.stalked = target.getId();
				live.stalkUntil = level.getGameTime() + STARE_TICKS;
				sound(player, SoundEvents.ENDERMAN_STARE, 1.0F, 1.0F);
				cooldown(player, live, G, STARE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && blink(player)) {
					cooldown(player, live, V, BLINK_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.hideUntil = level.getGameTime() + WARY_TICKS;
					burst(level, ParticleTypes.PORTAL, player.position().add(0.0, 1.0, 0.0), 30, 0.5, 0.3);
					sound(player, SoundEvents.ENDERMAN_AMBIENT, 1.0F, 1.2F);
					cooldown(player, live, B, WARY_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Pulls the block it looks at out of the world, if it may. */
	private static boolean steal(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		BlockPos at = aimBlock(player, STEAL_REACH);
		BlockState state = at == null ? null : level.getBlockState(at);
		float hardness = state == null ? -1.0F : state.getDestroySpeed(level, at);
		if (at == null || state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || hardness < 0.0F || hardness > STEAL_HARDNESS
			|| !player.mayBuild() || !level.mayInteract(player, at)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.shade.no_block").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		level.removeBlock(at, false);
		live.lifted = state;
		beam(level, Vec3.atCenterOf(at), player.getEyePosition().add(0.0, 0.8, 0.0), ParticleTypes.PORTAL, 8);
		sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
		return true;
	}

	/** Lets go of the held block: thrown with {@code velocity} (zero: it just drops). */
	private static void drop(ServerPlayer player, XenoformPower.Live live, Vec3 velocity) {
		BlockState state = live.lifted;
		live.lifted = null;
		if (state == null) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos over = BlockPos.containing(player.getX(), player.getEyeY() + 1.0, player.getZ());
		if (!level.getBlockState(over).isAir()) {
			over = player.blockPosition();
			if (!level.getBlockState(over).canBeReplaced()) {
				Block.popResource(level, player.blockPosition(), new ItemStack(state.getBlock()));
				return;
			}
		}
		FallingBlockEntity block = FallingBlockEntity.fall(level, over, state);
		block.setHurtsEntities(THROW_DAMAGE, THROW_MAX);
		block.setDeltaMovement(velocity.add(0.0, velocity.lengthSqr() > 0.0 ? 0.2 : 0.0, 0.0));
		block.syncVelocity = true;
	}

	/** A jump to where it looks: onto the block it looks at, or as far as it can see. */
	private static boolean blink(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = player.pick(BLINK_REACH, 1.0F, false);
		BlockPos to = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos().relative(block.getDirection())
			: BlockPos.containing(hit.getLocation());
		BlockPos spot = standing(level, to, 3);
		if (spot == null) {
			return false;
		}
		jump(player, Vec3.atBottomCenterOf(spot));
		return true;
	}

	/** Wary's escape: a random spot up to {@link #WARY_RANGE} blocks off. */
	private static boolean away(ServerPlayer player) {
		ServerLevel level = player.level();
		var random = player.getRandom();
		for (int i = 0; i < 16; i++) {
			BlockPos to = BlockPos.containing(player.getX() + (random.nextDouble() - 0.5) * 2.0 * WARY_RANGE, player.getY() + random.nextInt(5) - 2,
				player.getZ() + (random.nextDouble() - 0.5) * 2.0 * WARY_RANGE);
			BlockPos spot = standing(level, to, 4);
			if (spot != null && spot.distSqr(player.blockPosition()) > 9) {
				jump(player, Vec3.atBottomCenterOf(spot));
				return true;
			}
		}
		return false;
	}

	/** The nearest spot at or below {@code from} (within {@code down} blocks) with ground under it and room to stand. */
	private static @Nullable BlockPos standing(ServerLevel level, BlockPos from, int down) {
		for (int i = 0; i <= down; i++) {
			BlockPos at = from.below(i);
			if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
				&& level.getFluidState(at).isEmpty() && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
				return at;
			}
		}
		return null;
	}

	private static void jump(ServerPlayer player, Vec3 to) {
		ServerLevel level = player.level();
		Vec3 from = player.position();
		player.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		burst(level, ParticleTypes.PORTAL, from.add(0.0, 1.0, 0.0), 30, 0.4, 0.4);
		burst(level, ParticleTypes.REVERSE_PORTAL, to.add(0.0, 1.0, 0.0), 20, 0.4, 0.05);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		sound(player, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.4, 0.8, 0.4, 0.02);
		}
		if (player.isInWaterOrRain() && !player.isCreative()) {
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.3, 0.5, 0.3, 0.02);
			}
			if (player.tickCount % WET_EVERY == 0) {
				player.hurtServer(level, player.damageSources().drown(), WET_DAMAGE);
				sound(player, SoundEvents.ENDERMAN_HURT, 0.5F, 1.2F);
			}
		}
		if (live.lifted != null && player.tickCount % 2 == 0) {
			Vec3 over = player.getEyePosition().add(0.0, 1.0, 0.0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, live.lifted), over.x, over.y, over.z, 2, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(ParticleTypes.PORTAL, over.x, over.y, over.z, 1, 0.4, 0.4, 0.4, 0.0);
		}
		if (live.stalked >= 0) {
			stare(player, live, level);
		}
	}

	/** The stare holds while its eyes stay on the creature (and the stare lasts). */
	private static void stare(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Entity e = level.getEntity(live.stalked);
		int id = live.stalked;
		LivingEntity looked = aimed(player, STARE_REACH, x -> x.getId() == id);
		if (!(e instanceof LivingEntity held) || !held.isAlive() || looked != held || level.getGameTime() > live.stalkUntil) {
			live.stalked = -1;
			return;
		}
		held.setDeltaMovement(0.0, Math.min(0.0, held.getDeltaMovement().y), 0.0);
		held.syncVelocity = true;
		held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 9, false, false), player);
		if (held instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (player.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.PORTAL, held.getX(), held.getEyeY(), held.getZ(), 3, 0.2, 0.2, 0.2, 0.3);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.REVERSE_PORTAL, player.position().add(0.0, 1.2, 0.0), 60, 1.0, 0.05);
		sound(player, SoundEvents.ENDERMAN_SCREAM, 1.6F, 0.6F);
		return true;
	}
}
