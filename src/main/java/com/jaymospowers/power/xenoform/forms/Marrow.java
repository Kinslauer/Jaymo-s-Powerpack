package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.107, Scan B) Marrow, the bone alien: a tall skeleton of white bone in an open ribcage, horned skull, a spine
 * that juts out of its back. 24 health, fists of 4, a little quick. Scanned from a skeleton (or Kingdoms' silver
 * skeleton and Skeleton Lord, {@code XenoDna}).
 * <ul>
 * <li>R, Bone Spikes: a line of bone spears bursts from the ground ahead, {@link #SPIKE_REACH} blocks.</li>
 * <li>G, Rib Throw: a rib flung out like a boomerang; it cuts what it passes going out and coming back.</li>
 * <li>V, Bone Shield: bone plates for {@link #SHIELD_TICKS} ticks; shots glance off and blows land softer.</li>
 * <li>B, Fall Apart: drop into a heap of bones; what hunts you loses interest, and you can't move.</li>
 * </ul>
 * Once each change, a killing blow drops it into a heap instead; a moment later it stands back up at half health.
 */
public final class Marrow extends XenoForm {
	// ---- tuning ----
	public static final double SPIKE_REACH = 9.0, SPIKE_WIDTH = 1.2, SPIKE_LIFT = 0.55;
	public static final float SPIKE_DAMAGE = 6.0F;
	public static final int SPIKE_COOLDOWN = 60;
	public static final int RIB_OUT = 10, RIB_COOLDOWN = 40;
	public static final double RIB_SPEED = 1.0, RIB_HIT = 1.0;
	public static final float RIB_DAMAGE = 4.0F;
	public static final int SHIELD_TICKS = 100, SHIELD_COOLDOWN = 300;
	public static final int HEAP_TICKS = 60, HEAP_COOLDOWN = 240;
	/** The once-a-change rise: how long it lies in a heap (ticks), and the health it gets up with (a fraction of the most). */
	public static final int RISE_TICKS = 60;
	public static final float RISE_HEALTH = 0.5F;

	public Marrow() {
		super("marrow", 1.0F, 24.0F, 4.0, 0.05, 0.2, 0.0, Items.BONE, 0xE6E0CC);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Skeleton;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.risen = false;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.spin = 0;
		live.whirl = null;
		live.ribDir = null;
		live.spun.clear();
		live.shieldUntil = live.heapUntil = 0L;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		long now = player.level().getGameTime();
		if (live.heapUntil > now && live.risen && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// lying in a heap after the killing blow: nothing more to break
			return false;
		}
		if (live.shieldUntil > now && source.is(DamageTypeTags.IS_PROJECTILE)) {
			sound(player, SoundEvents.SKELETON_HURT, 0.8F, 1.6F);
			return false;
		}
		if (!live.risen && amount >= player.getHealth() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			live.risen = true;
			live.heapUntil = now + RISE_TICKS;
			collapse(player);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.marrow.heap").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down || live.heapUntil > player.level().getGameTime()) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					spikes(player, level);
					live.hurt = true;
					cooldown(player, live, R, SPIKE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && live.spin == 0) {
					live.whirl = player.getEyePosition().add(0.0, -0.4, 0.0);
					live.ribDir = player.getLookAngle();
					live.spin = RIB_OUT * 2;
					live.spun.clear();
					sound(player, SoundEvents.SKELETON_SHOOT, 1.0F, 0.7F);
					cooldown(player, live, G, RIB_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.shieldUntil = level.getGameTime() + SHIELD_TICKS;
					player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_TICKS, 1, false, true));
					sound(player, SoundEvents.BONE_BLOCK_PLACE, 1.0F, 0.8F);
					cooldown(player, live, V, SHIELD_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.heapUntil = level.getGameTime() + HEAP_TICKS;
					collapse(player);
					cooldown(player, live, B, HEAP_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Bone spears up out of the ground along a line ahead. */
	private static void spikes(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		Vec3 from = player.position();
		for (LivingEntity e : around(player, SPIKE_REACH + 1.0)) {
			Vec3 c = e.position().subtract(from);
			double along = c.dot(look);
			if (along <= 0.0 || along > SPIKE_REACH || c.subtract(look.scale(along)).horizontalDistance() > SPIKE_WIDTH + e.getBbWidth() * 0.5
				|| Math.abs(c.y) > 2.0) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), SPIKE_DAMAGE);
			e.setDeltaMovement(e.getDeltaMovement().add(0.0, SPIKE_LIFT, 0.0));
			e.syncVelocity = true;
		}
		BlockParticleOption bone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
		for (int i = 1; i <= (int)SPIKE_REACH; i++) {
			Vec3 p = from.add(look.scale(i));
			level.sendParticles(bone, p.x, p.y + 0.6, p.z, 8, 0.2, 0.6, 0.2, 0.05);
		}
		sound(player, SoundEvents.BONE_BLOCK_BREAK, 1.2F, 0.6F);
	}

	/** A heap of bones: nothing hunting it keeps on. */
	private static void collapse(ServerPlayer player) {
		ServerLevel level = player.level();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), player.getX(), player.getY() + 0.8,
			player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
		sound(player, SoundEvents.SKELETON_DEATH, 1.0F, 0.9F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.heapUntil > now) {
			// lying in a heap: no moving, no one minds it
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 9, false, false));
			player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
			if (player.tickCount % 10 == 0) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> m.getTarget() == player)) {
					mob.setTarget(null);
				}
			}
			if (live.heapUntil == now + 1 && live.risen && player.getHealth() < player.getMaxHealth() * RISE_HEALTH) {
				// back up from the killing blow
				player.setHealth(player.getMaxHealth() * RISE_HEALTH);
				sound(player, SoundEvents.SKELETON_AMBIENT, 1.2F, 0.6F);
			}
		}
		if (live.spin > 0 && live.whirl != null && live.ribDir != null) {
			// the rib: out along its heading for RIB_OUT ticks, then home to the hand
			boolean back = live.spin <= RIB_OUT;
			if (back && live.spin == RIB_OUT) {
				live.spun.clear();
			}
			Vec3 step = back ? player.getEyePosition().subtract(live.whirl).normalize().scale(RIB_SPEED * 1.2) : live.ribDir.scale(RIB_SPEED);
			live.whirl = live.whirl.add(step);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), live.whirl.x, live.whirl.y, live.whirl.z,
				2, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, live.whirl.x, live.whirl.y, live.whirl.z, 1, 0.0, 0.0, 0.0, 0.0);
			Vec3 at = live.whirl;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(RIB_HIT),
				e -> e != player && e.isAlive() && !e.isSpectator() && !spared(player, e))) {
				if (live.spun.add(e.getId())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), RIB_DAMAGE);
					live.hurt = true;
				}
			}
			live.spin--;
			if (live.spin == 0 || back && live.whirl.distanceTo(player.getEyePosition()) < 1.2) {
				live.spin = 0;
				live.whirl = null;
				live.ribDir = null;
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		// the Ultimate grows back the rise, if it was spent
		live.risen = false;
		burst(player.level(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), player.position().add(0.0, 1.0, 0.0), 40, 0.8, 0.1);
		sound(player, SoundEvents.WITHER_SKELETON_AMBIENT, 1.4F, 0.6F);
		return true;
	}
}
