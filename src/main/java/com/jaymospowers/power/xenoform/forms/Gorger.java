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
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.96, Xeno-Band patch 4) Gorger, the armadillo alien: squat, plated, a horn on its snout, 30 health, fists of 5.
 * <ul>
 * <li>R, Gulp: eat the block you look at (within {@link #GULP_REACH}; never one you couldn't break). Each one fills the
 * gut, harder blocks more ({@link #GORGE_MAX} at most).</li>
 * <li>G, Gut Blast: everything eaten comes back up as a blast of rubble in a cone ahead, {@link #BLAST_PER_BITE} damage a
 * bite.</li>
 * <li>V, Horn Charge: head down and run ({@link #HORN_TICKS} ticks), goring the first creature in the way. Run off a
 * ledge mid-charge and it turns into a slam: the landing hits everything round, harder the farther you fell.</li>
 * <li>B (hold), Roll Up: curl into the shell. Arrows and other shots glance off, nothing moves you, but you crawl.</li>
 * </ul>
 */
public final class Gorger extends XenoForm {
	// ---- tuning ----
	public static final double GULP_REACH = 4.5;
	public static final int GULP_COOLDOWN = 8, GORGE_MAX = 12;
	/** The hardest block it will eat (obsidian is 50); hardness per extra bite, and the most bites one block gives. */
	public static final float GULP_HARDEST = 50.0F, HARDNESS_PER_BITE = 3.0F;
	public static final int BITES_MAX = 3;
	public static final double BLAST_REACH = 9.0, BLAST_CONE = 0.75, BLAST_PUSH = 1.4;
	public static final float BLAST_PER_BITE = 2.0F;
	public static final int BLAST_COOLDOWN = 40;
	public static final int HORN_TICKS = 22, HORN_COOLDOWN = 100;
	public static final double HORN_SPEED = 0.75, HORN_HIT_REACH = 1.6, HORN_TOSS = 1.2;
	public static final float HORN_DAMAGE = 7.0F;
	/** The ledge slam: its reach, base bite, and the bite per block fallen (up to {@link #SLAM_MAX}). */
	public static final double SLAM_RADIUS = 4.0;
	public static final float SLAM_DAMAGE = 5.0F, SLAM_PER_BLOCK = 1.0F, SLAM_MAX = 18.0F;

	public Gorger() {
		super("gorger", 1.1F, 30.0F, 5.0, -0.05, 0.5, 0.5, Items.ARMADILLO_SCUTE, 0xB06A54);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Armadillo;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.gorge = live.horn = 0;
		live.hornSlam = live.rolled = false;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (live.rolled && source.is(DamageTypeTags.IS_PROJECTILE)) {
			sound(player, SoundEvents.ARMADILLO_SCUTE_DROP, 1.0F, 1.4F);
			return false;
		}
		// the slam's own landing doesn't hurt it
		return !(live.hornSlam && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			if (live.rolled != down) {
				live.rolled = down;
				sound(player, down ? SoundEvents.ARMADILLO_ROLL : SoundEvents.ARMADILLO_UNROLL_FINISH, 1.0F, 0.8F);
				if (down) {
					cooldown(player, live, B, 1);
				}
			}
			return;
		}
		if (!down || live.rolled) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					gulp(player, live);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					if (live.gorge <= 0) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
						return;
					}
					blast(player, live);
					cooldown(player, live, G, BLAST_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && live.horn == 0) {
					live.horn = HORN_TICKS;
					live.hornSlam = false;
					sound(player, SoundEvents.RAVAGER_ROAR, 0.6F, 1.6F);
					cooldown(player, live, V, HORN_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private void gulp(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.gorge >= GORGE_MAX) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.full").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		BlockPos at = aimBlock(player, GULP_REACH);
		if (at == null) {
			return;
		}
		BlockState state = level.getBlockState(at);
		float hardness = state.getDestroySpeed(level, at);
		if (state.isAir() || hardness < 0.0F || hardness > GULP_HARDEST || level.getBlockEntity(at) != null || !player.mayBuild()
			|| !level.mayInteract(player, at)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.inedible").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		int bites = Math.min(BITES_MAX, 1 + (int)(hardness / HARDNESS_PER_BITE));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
		level.destroyBlock(at, false, player, 512);
		live.gorge = Math.min(GORGE_MAX, live.gorge + bites);
		live.hudDirty = true;
		sound(player, SoundEvents.GENERIC_EAT.value(), 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.gorge", live.gorge, GORGE_MAX).withStyle(ChatFormatting.GOLD));
		cooldown(player, live, R, GULP_COOLDOWN);
	}

	private void blast(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		float damage = live.gorge * BLAST_PER_BITE;
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		for (LivingEntity e : around(player, BLAST_REACH)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye).normalize();
			if (to.dot(look) > BLAST_CONE && player.hasLineOfSight(e)) {
				e.hurtServer(level, player.damageSources().playerAttack(player), damage);
				shove(e, player.position(), BLAST_PUSH * (0.5 + live.gorge / (double)GORGE_MAX), 0.3);
			}
		}
		BlockState rubble = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		for (int i = 1; i <= 8; i++) {
			Vec3 p = eye.add(look.scale(i * BLAST_REACH / 8.0));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rubble), p.x, p.y, p.z, 4 + live.gorge, 0.2 * i * 0.3, 0.2 * i * 0.3, 0.2 * i * 0.3, 0.2);
		}
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
		sound(player, SoundEvents.RAVAGER_STUNNED, 1.0F, 1.2F);
		live.gorge = 0;
		live.hudDirty = true;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.rolled) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 3, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 5, 1, false, false));
			player.setDeltaMovement(player.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		}
		if (live.horn > 0) {
			horn(player, live, level);
		}
		if (live.hornSlam && (player.onGround() || player.isInWater())) {
			slam(player, live, level);
		}
	}

	/** One tick of the horn charge: forward along the ground, goring the first creature met; off a ledge it becomes a slam. */
	private void horn(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		live.horn--;
		if (!player.onGround() && !player.isInWater() && player.getDeltaMovement().y < -0.1) {
			// over the edge: head down, the charge becomes the slam
			live.horn = 0;
			live.hornSlam = true;
			live.hornFrom = player.getY();
			player.setDeltaMovement(player.getDeltaMovement().x * 0.5, -1.2, player.getDeltaMovement().z * 0.5);
			player.syncVelocity = true;
			sound(player, SoundEvents.ARMADILLO_ROLL, 1.0F, 0.6F);
			return;
		}
		Vec3 ahead = player.getLookAngle().horizontal();
		ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.ZERO : ahead.normalize();
		player.setDeltaMovement(ahead.x * HORN_SPEED, player.getDeltaMovement().y, ahead.z * HORN_SPEED);
		player.syncVelocity = true;
		if (player.tickCount % 3 == 0) {
			BlockState ground = level.getBlockState(player.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
			}
		}
		for (LivingEntity e : around(player, HORN_HIT_REACH + player.getBbWidth())) {
			Vec3 to = e.position().subtract(player.position()).horizontal();
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(ahead) > 0.3) {
				e.hurtServer(level, player.damageSources().playerAttack(player), HORN_DAMAGE);
				shove(e, player.position(), HORN_TOSS, 0.6);
				sound(player, SoundEvents.RAVAGER_ATTACK, 1.0F, 1.1F);
				com.jaymospowers.core.Shakes.heavy(player, player.position(), 6.0, 0.3F);
				live.horn = 0;
				return;
			}
		}
		if (player.horizontalCollision) {
			// a wall stops it dead
			live.horn = 0;
			sound(player, SoundEvents.ARMADILLO_HURT, 1.0F, 0.7F);
		}
	}

	private void slam(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		live.hornSlam = false;
		player.resetFallDistance();
		float hit = Math.min(SLAM_MAX, SLAM_DAMAGE + (float)Math.max(0.0, live.hornFrom - player.getY()) * SLAM_PER_BLOCK);
		for (LivingEntity e : around(player, SLAM_RADIUS)) {
			e.hurtServer(level, player.damageSources().playerAttack(player), hit);
			shove(e, player.position(), 0.8, 0.5);
		}
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY(), player.getZ(), 50, SLAM_RADIUS * 0.4, 0.1,
				SLAM_RADIUS * 0.4, 0.2);
		}
		ring(level, ParticleTypes.EXPLOSION, player.position(), SLAM_RADIUS * 0.6, 6);
		sound(player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 10.0, 0.5F);
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		ring(level, ParticleTypes.EXPLOSION, player.position(), 2.0, 8);
		sound(player, SoundEvents.ARMADILLO_ROLL, 1.4F, 0.5F);
		live.gorge = GORGE_MAX;
		live.hudDirty = true;
		return true;
	}
}
