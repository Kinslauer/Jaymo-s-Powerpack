package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Debris;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bulwark, the four-armed brute (scanned from an iron golem): half again your size, 40 health, fists that hit for 7.
 * <ul>
 * <li>R, Fist Combo: three quick blows on whatever is in reach, the last one sending it flying.</li>
 * <li>G, Ground Clap: a shockwave through everything within {@link #CLAP_RADIUS}.</li>
 * <li>V, Grab and Throw: pick up the creature you look at and carry it overhead; V again hurls it. Looking at a block
 * instead rips a chunk out of the ground and throws it. Sneak + V while holding a creature is the suplex instead
 * ({@link BulwarkSuplex}): it is driven head first into the ground behind.</li>
 * <li>Passive, fury ({@link BulwarkFury}): blows given and taken fill a rage meter; fuller, it hits harder and is hard to shove.</li>
 * <li>Passive, charge ({@link BulwarkCharge}): sprint a short while and Bulwark barrels through stone and wood and flings what it hits.</li>
 * <li>B, Leap Slam: a great leap; landing hits everything within {@link #SLAM_RADIUS}.</li>
 * <li>C (ascended), Titan: (0.20.9) its Ultimate is its own evolved form, {@link com.jaymospowers.power.xenoform.forms.ultimate.Titan}.</li>
 * </ul>
 */
public final class Bulwark extends XenoForm {
	// ---- tuning ----
	public static final int COMBO_HITS = 3, COMBO_SPACING = 4, COMBO_COOLDOWN = 40;
	public static final double COMBO_REACH = 4.0;
	public static final float COMBO_DAMAGE = 5.0F;
	public static final int CLAP_COOLDOWN = 160;
	public static final double CLAP_RADIUS = 5.0;
	public static final float CLAP_DAMAGE = 6.0F;
	public static final int GRAB_COOLDOWN = 20;
	public static final double GRAB_REACH = 4.5, GRAB_MAX_WIDTH = 1.6;
	/** How hard a held creature or a torn chunk is thrown (blocks/tick), and how big a chunk (blocks). */
	public static final double THROW_SPEED = 1.5;
	public static final int CHUNK_BLOCKS = 4;
	public static final int SLAM_COOLDOWN = 120;
	public static final double LEAP_OUT = 1.1, LEAP_UP = 0.95, SLAM_RADIUS = 4.0;
	public static final float SLAM_DAMAGE = 8.0F;
	/** Screen shake (degrees, and how far it's felt): the clap, the leap's landing. */
	public static final float CLAP_SHAKE = 1.0F, SLAM_SHAKE = 1.5F;
	public static final double CLAP_SHAKE_RANGE = 14.0, SLAM_SHAKE_RANGE = 16.0;

	/** (0.20.9) Its Ultimate, built once. */
	private final com.jaymospowers.power.xenoform.XenoUltimate titan = new com.jaymospowers.power.xenoform.forms.ultimate.Titan(this);

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		BulwarkFury.clear(player);
		BulwarkCharge.clear(player);
		BulwarkSuplex.clear(player);
	}

	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		BulwarkFury.dealt(player);
	}

	public Bulwark() {
		super("bulwark", 1.5F, 40.0F, 7.0, -0.1, 0.6, 0.6, Items.IRON_BLOCK, 0xB8C4D0);
	}

	@Override
	public boolean scans(Entity creature) {
		// (0.18.98) Kingdoms' clockwork golem is built on the iron golem, but it carries Rivet, not Bulwark
		return creature instanceof IronGolem && !"jaymoskingdoms".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()).getNamespace());
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					live.combo = COMBO_HITS;
					live.comboNext = player.level().getGameTime();
					cooldown(player, live, R, COMBO_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					clap(player, live);
					cooldown(player, live, G, CLAP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && grabOrThrow(player, live)) {
					cooldown(player, live, V, GRAB_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && player.onGround() && !live.leaping) {
					Vec3 out = player.getLookAngle().horizontal();
					out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(LEAP_OUT);
					player.setDeltaMovement(out.x, LEAP_UP, out.z);
					player.syncVelocity = true;
					live.leaping = true;
					live.leapTicks = 0;
					sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
					com.jaymospowers.core.Shakes.send(player, 0.5F, player.position());
					cooldown(player, live, B, SLAM_COOLDOWN);
				}
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		BulwarkFury.tick(player);
		BulwarkCharge.tick(player);
		BulwarkSuplex.tick(player);
		long now = player.level().getGameTime();
		if (live.combo > 0 && now >= live.comboNext) {
			live.combo--;
			live.comboNext = now + COMBO_SPACING;
			punch(player, live.combo == 0, live.combo);
		}
		if (live.leaping) {
			live.leapTicks++;
			if (live.leapTicks > 3 && (player.onGround() || player.isInWater())) {
				live.leaping = false;
				slam(player);
			} else if (live.leapTicks > 100) {
				live.leaping = false;
			}
		}
		// the creature held overhead
		if (live.grabbed >= 0 && player.tickCount % 10 == 0) {
			Entity held = player.level().getEntity(live.grabbed);
			if (held instanceof LivingEntity living) {
				living.hurtTime = 0;
			}
		}
	}

	/** A leap's landing never hurts the brute. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (amount > 0.0F && !source.is(DamageTypeTags.IS_FALL)) {
			BulwarkFury.taken(player, amount);
		}
		return !(source.is(DamageTypeTags.IS_FALL) && (live.leaping || live.leapTicks > 0 && player.tickCount % 1 == 0 && live.leapTicks < 120));
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.leaping;
	}

	// ---- Titan ----

	/** (0.20.9) Titan is its own evolved form now: the Ultimate move, health bar, model and kit are in {@code Titan}. */
	@Override
	public com.jaymospowers.power.xenoform.XenoUltimate evolved() {
		return titan;
	}

	private static void punch(ServerPlayer player, boolean last, int left) {
		// (0.20.60) the hands take turns (in first person too); every client throws each blow with the next of the four arms
		player.swing(left % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		LivingEntity target = aimed(player, COMBO_REACH, e -> true);
		if (target == null) {
			sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.7F);
			return;
		}
		ServerLevel level = player.level();
		target.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, COMBO_DAMAGE));
		target.hurtTime = 0;
		if (last) {
			shove(target, player.position(), 1.2, 0.5);
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
			burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 12, 0.4, 0.3);
			com.jaymospowers.core.Shakes.heavy(player, target.getBoundingBox().getCenter(), 10.0, 1.0F);
		} else {
			shove(target, player.position(), 0.25, 0.1);
			sound(player, SoundEvents.IRON_GOLEM_ATTACK, 0.8F, 1.0F);
		}
	}

	private static void clap(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (LivingEntity living : around(player, CLAP_RADIUS)) {
			if (living.getId() == live.grabbed) {
				continue;
			}
			living.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, CLAP_DAMAGE));
			shove(living, player.position(), 1.0, 0.5);
		}
		Vec3 feet = player.position();
		ring(level, ParticleTypes.CLOUD, feet, CLAP_RADIUS * 0.5, 16);
		ring(level, ParticleTypes.POOF, feet, CLAP_RADIUS, 24);
		Debris.ring(level, player.blockPosition(), (int)CLAP_RADIUS, 1.0F);
		sound(player, SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.3F);
		com.jaymospowers.core.Shakes.heavy(player, feet, CLAP_SHAKE_RANGE, CLAP_SHAKE);
	}

	/** V: throw what's held; else grab what's looked at; else rip a chunk out of the ground and throw it. */
	private static boolean grabOrThrow(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.grabbed >= 0) {
			Entity held = level.getEntity(live.grabbed);
			live.grabbed = -1;
			if (held instanceof LivingEntity creature && held.getVehicle() == player && player.isShiftKeyDown()) {
				// sneak + V: the suplex
				BulwarkSuplex.begin(player, creature);
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				return true;
			}
			if (held != null && held.getVehicle() == player) {
				held.stopRiding();
				Vec3 look = player.getLookAngle();
				held.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
				held.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, 0.25, 0.0));
				held.syncVelocity = true;
				held.resetFallDistance();
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
				return true;
			}
		}
		LivingEntity target = aimed(player, GRAB_REACH, e -> e.getBbWidth() <= GRAB_MAX_WIDTH && !(e instanceof IronGolem));
		if (target != null) {
			if (target.startRiding(player, true, true)) {
				live.grabbed = target.getId();
				player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				sound(player, SoundEvents.IRON_GOLEM_ATTACK, 0.8F, 0.8F);
				return true;
			}
			return false;
		}
		// a chunk of the ground, hurled
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(GRAB_REACH + 1.0)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockCluster chunk = BlockCluster.rip(level, hit, CHUNK_BLOCKS, 50.0F, player);
		if (chunk == null) {
			return false;
		}
		chunk.setPos(player.getX() + player.getLookAngle().x, player.getEyeY(), player.getZ() + player.getLookAngle().z);
		chunk.setDeltaMovement(player.getLookAngle().scale(THROW_SPEED * 0.9).add(0.0, 0.15, 0.0));
		chunk.syncVelocity = true;
		chunk.launch(player);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
		return true;
	}

	private static void slam(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SLAM_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, SLAM_DAMAGE));
			com.jaymospowers.core.Crushing.mark(living);
			shove(living, player.position(), 0.8, 0.6);
		}
		Vec3 feet = player.position();
		Debris.smash(level, feet, 1.4, player);
		ring(level, ParticleTypes.POOF, feet, SLAM_RADIUS, 20);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.1F);
		sound(player, SoundEvents.IRON_GOLEM_HURT, 1.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, feet, SLAM_SHAKE_RANGE, SLAM_SHAKE);
		player.resetFallDistance();
	}
}
