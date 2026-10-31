package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.115, Scan F of {@code docs/ScanGaps-Plan.md}) Ooze, the living slime: a body of see-through green jelly with a
 * darker core and two dark eyes. 24 health, fists of 4. It bounces off every fall (sneak to land), and as it's hurt it
 * splits: a small slime of its own drops off at each quarter of its health lost, fights beside it, and creeps back to
 * rejoin it (healing it) once the fight's been over {@link #REJOIN_QUIET} ticks. Scanned from a slime, or the sulfur
 * cube, the Aether's blue or golden swet, or the Kingdoms mud golem ({@code XenoDna}). Scanned from a magma cube it's
 * {@code Magma Ooze}: the same, burning orange and black, fire can't hurt it, and its blobs are magma cubes.
 * <ul>
 * <li>R, Engulf: a small creature in reach is swallowed and held inside you, slowly digested.</li>
 * <li>G, Spit: what you swallowed is spat out where you look; with nothing inside, a glob of slime that slows.</li>
 * <li>V, Squeeze: you thin right down, small enough for a one-block gap; V again (with room) to fill out.</li>
 * <li>B, Split: two blobs drop off you at once.</li>
 * </ul>
 */
public final class Ooze extends XenoForm {
	// ---- tuning ----
	public static final double BOUNCE = 0.8, BOUNCE_FROM = -0.5;
	public static final int SPLIT_MAX = 4, REJOIN_QUIET = 100;
	public static final double REJOIN_REACH = 1.6, BLOB_FOLLOW = 3.0, BLOB_SPEED = 1.2, BLOB_GUARD = 16.0;
	public static final float REJOIN_HEAL = 3.0F;
	public static final double ENGULF_REACH = 3.0, ENGULF_WIDTH = 1.0, ENGULF_HEIGHT = 1.6;
	public static final int ENGULF_COOLDOWN = 60, DIGEST_EVERY = 20, ENGULF_TICKS = 300;
	public static final float DIGEST_DAMAGE = 1.0F;
	public static final double SPIT_SPEED = 1.6, GLOB_REACH = 12.0;
	public static final float GLOB_DAMAGE = 3.0F;
	public static final int SPIT_COOLDOWN = 30;
	/** Squeezed: its size is cut by this share. */
	public static final double SQUEEZE = 0.6;
	public static final int SQUEEZE_COOLDOWN = 20;
	public static final int SPLIT_COOLDOWN = 400;

	private static final Identifier SQUEEZE_ID = com.jaymospowers.JaymosPowers.id("xenoform_ooze_squeeze");

	private final boolean magma;

	public Ooze(boolean magma) {
		super(magma ? "magma_ooze" : "ooze", 1.0F, 24.0F, 4.0, 0.0, 0.2, 0.0, magma ? Items.MAGMA_CREAM : Items.SLIME_BALL, magma ? 0xE0602A : 0x6CC060);
		this.magma = magma;
	}

	@Override
	public boolean scans(Entity creature) {
		return this.magma ? creature instanceof MagmaCube : creature instanceof Slime || creature instanceof SulfurCube;
	}

	private ParticleOptions goo() {
		return this.magma ? ParticleTypes.FLAME : ParticleTypes.ITEM_SLIME;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		spit(player, live, Vec3.ZERO);
		squeeze(player, false);
		live.squeeze = false;
		live.splits = 0;
		dismiss(player.level(), live.minions);
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return !(this.magma && source.is(DamageTypeTags.IS_FIRE));
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (live.latched >= 0 || !ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, ENGULF_REACH, e -> !(e instanceof Player) && e.getBbWidth() <= ENGULF_WIDTH && e.getBbHeight() <= ENGULF_HEIGHT);
				if (target == null) {
					return;
				}
				live.latched = target.getId();
				live.latchTicks = ENGULF_TICKS;
				burst(level, this.goo(), target.position().add(0.0, 0.5, 0.0), 16, 0.4, 0.05);
				sound(player, this.magma ? SoundEvents.MAGMA_CUBE_SQUISH : SoundEvents.SLIME_SQUISH, 1.2F, 0.6F);
				cooldown(player, live, R, ENGULF_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				if (live.latched >= 0) {
					spit(player, live, player.getLookAngle().scale(SPIT_SPEED));
				} else {
					LivingEntity target = aimed(player, GLOB_REACH, e -> true);
					if (target == null) {
						return;
					}
					target.hurtServer(level, player.damageSources().playerAttack(player), GLOB_DAMAGE);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), player);
					if (this.magma) {
						target.igniteForSeconds(3.0F);
					}
					beam(level, player.getEyePosition(), target.getBoundingBox().getCenter(), this.goo(), 10);
				}
				sound(player, this.magma ? SoundEvents.MAGMA_CUBE_JUMP : SoundEvents.SLIME_JUMP, 1.2F, 1.3F);
				cooldown(player, live, G, SPIT_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				if (live.squeeze && !roomToFill(player)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.ooze.no_room").withStyle(ChatFormatting.GRAY));
					return;
				}
				live.squeeze = !live.squeeze;
				squeeze(player, live.squeeze);
				burst(level, this.goo(), player.position().add(0.0, 0.3, 0.0), 12, 0.3, 0.02);
				sound(player, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, live.squeeze ? 1.4F : 0.8F);
				cooldown(player, live, V, SQUEEZE_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && live.minions.size() < SPLIT_MAX) {
					this.blob(player, live, level);
					this.blob(player, live, level);
					cooldown(player, live, B, SPLIT_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Squeezed small, or back to its size. */
	private static void squeeze(ServerPlayer player, boolean on) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		if (on) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(SQUEEZE_ID, -SQUEEZE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			scale.removeModifier(SQUEEZE_ID);
		}
	}

	/** Whether it has room to fill back out where it is. */
	private static boolean roomToFill(ServerPlayer player) {
		double grow = 1.0 / (1.0 - SQUEEZE);
		double w = player.getBbWidth() * grow * 0.5, h = player.getBbHeight() * grow;
		AABB box = new AABB(player.getX() - w, player.getY(), player.getZ() - w, player.getX() + w, player.getY() + h, player.getZ() + w);
		return player.level().noCollision(player, box.deflate(1.0E-4));
	}

	/** Lets out what it swallowed, thrown with {@code velocity}. */
	private static void spit(ServerPlayer player, XenoformPower.Live live, Vec3 velocity) {
		Entity e = live.latched < 0 ? null : player.level().getEntity(live.latched);
		live.latched = -1;
		live.latchTicks = 0;
		if (e == null || !e.isAlive()) {
			return;
		}
		Vec3 out = player.getEyePosition().add(player.getLookAngle().scale(1.0)).subtract(0.0, e.getBbHeight() * 0.5, 0.0);
		e.setPos(out.x, out.y, out.z);
		e.setDeltaMovement(velocity.add(0.0, velocity.lengthSqr() > 0.0 ? 0.2 : 0.0, 0.0));
		e.syncVelocity = true;
		e.resetFallDistance();
	}

	/** A blob drops off it: a small slime (magma cube) of its own. */
	private void blob(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		double a = player.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 at = player.position().add(Math.cos(a), 0.2, Math.sin(a));
		AbstractCubeMob blob = this.magma ? summon(level, EntityTypes.MAGMA_CUBE, at, player.getYRot()) : summon(level, EntityTypes.SLIME, at, player.getYRot());
		if (blob == null) {
			return;
		}
		blob.setSize(1, true);
		live.minions.add(blob.getId());
		burst(level, this.goo(), at.add(0.0, 0.3, 0.0), 10, 0.3, 0.05);
		sound(player, this.magma ? SoundEvents.MAGMA_CUBE_SQUISH_SMALL : SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 1.0F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (this.magma) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
			}
		}
		// it bounces off every fall (sneak to land)
		Vec3 m = player.getDeltaMovement();
		if (!player.onGround()) {
			live.fallSpeed = m.y;
		} else {
			if (live.fallSpeed < BOUNCE_FROM && !player.isShiftKeyDown() && !player.isInWater()) {
				player.setDeltaMovement(m.x, -live.fallSpeed * BOUNCE, m.z);
				player.syncVelocity = true;
				burst(level, this.goo(), player.position(), 8, 0.4, 0.05);
				sound(player, this.magma ? SoundEvents.MAGMA_CUBE_JUMP : SoundEvents.SLIME_JUMP, 0.8F, 1.0F);
			}
			live.fallSpeed = 0.0;
		}
		// the more it's hurt, the more of it splits off
		int wanted = Math.min(SPLIT_MAX - 1, (int)((1.0F - player.getHealth() / player.getMaxHealth()) * 4.0F));
		if (wanted > live.splits && live.minions.size() < SPLIT_MAX) {
			live.splits++;
			this.blob(player, live, level);
		} else if (wanted < live.splits) {
			live.splits = wanted;
		}
		if (live.latched >= 0) {
			this.engulfed(player, live, level);
		}
		if (!live.minions.isEmpty()) {
			this.blobs(player, live, level);
		}
	}

	/** What it swallowed: held inside, unseen, slowly digested. */
	private void engulfed(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Entity e = level.getEntity(live.latched);
		if (!(e instanceof LivingEntity held) || !held.isAlive() || --live.latchTicks <= 0) {
			spit(player, live, Vec3.ZERO);
			return;
		}
		Vec3 inside = player.position().add(0.0, player.getBbHeight() * 0.3, 0.0);
		held.setPos(inside.x, inside.y, inside.z);
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
		held.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false), player);
		if (held instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (live.latchTicks % DIGEST_EVERY == 0) {
			held.setInvulnerableTime(0);
			held.hurtServer(level, player.damageSources().playerAttack(player), DIGEST_DAMAGE);
			level.sendParticles(this.goo(), inside.x, inside.y + 0.4, inside.z, 3, 0.2, 0.2, 0.2, 0.0);
		}
	}

	/** The blobs fight beside it; once the fight's been over a while they creep back and rejoin it. */
	private void blobs(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		LivingEntity foe = null;
		LivingEntity hit = player.getLastHurtMob();
		if (hit != null && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() < 200 && hit.distanceTo(player) < BLOB_GUARD) {
			foe = hit;
		}
		LivingEntity by = player.getLastHurtByMob();
		if (foe == null && by != null && by.isAlive() && by.distanceTo(player) < BLOB_GUARD) {
			foe = by;
		}
		if (foe instanceof Player || foe != null && (spared(player, foe) || foe.entityTags().contains(XenoformState.MINION_TAG))) {
			foe = null;
		}
		boolean quiet = player.tickCount - Math.max(player.getLastHurtMobTimestamp(), player.getLastHurtByMobTimestamp()) > REJOIN_QUIET;
		for (var it = live.minions.iterator(); it.hasNext();) {
			if (!(level.getEntity(it.next()) instanceof Mob blob) || !blob.isAlive()) {
				it.remove();
				continue;
			}
			LivingEntity target = blob.getTarget();
			if (target instanceof Player || target != null && (spared(player, target) || target.entityTags().contains(XenoformState.MINION_TAG))) {
				blob.setTarget(null);
				target = null;
			}
			if (quiet) {
				blob.setTarget(null);
				if (blob.distanceTo(player) < REJOIN_REACH) {
					// back into the body
					burst(level, this.goo(), blob.position().add(0.0, 0.3, 0.0), 8, 0.2, 0.02);
					blob.discard();
					it.remove();
					player.heal(REJOIN_HEAL);
					sound(player, SoundEvents.SLIME_SQUISH, 0.8F, 1.2F);
				} else if (blob.tickCount % 10 == 0) {
					blob.getNavigation().moveTo(player, BLOB_SPEED);
				}
			} else if (foe != null && target != foe) {
				blob.setTarget(foe);
			} else if (target == null && blob.tickCount % 10 == 0 && blob.distanceToSqr(player) > BLOB_FOLLOW * BLOB_FOLLOW) {
				blob.getNavigation().moveTo(player, BLOB_SPEED);
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), this.goo(), player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.1);
		sound(player, this.magma ? SoundEvents.MAGMA_CUBE_SQUISH : SoundEvents.SLIME_SQUISH, 1.6F, 0.5F);
		return true;
	}
}
