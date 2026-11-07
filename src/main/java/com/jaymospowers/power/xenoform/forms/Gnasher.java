package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.111, Scan D) Gnasher, the shark: a heavy grey-blue body, white underneath, a blunt snout, a jaw lined with
 * teeth, a tall fin on its back and a tail fin. 30 health, fists of 6. A fast swimmer (Dolphin's Grace II, water
 * breathing), and it smells blood in the water: bleeders in water within {@link #SMELL_RANGE} blocks glow for it.
 * Scanned from a dolphin, or a great white shark, Naturalist's alligator, a piranha or the Friends &amp; Foes mauler
 * ({@code XenoDna}).
 * <ul>
 * <li>R, Clamp: a bite that holds on; R again lets go.</li>
 * <li>G, Lunge: a leap where you look, out of the water and up onto the shore.</li>
 * <li>V, Frenzy: faster and stronger for each bleeder near you.</li>
 * <li>B, Death Roll: what you hold in your jaws is rolled and torn, then thrown down.</li>
 * </ul>
 */
public final class Gnasher extends XenoForm {
	// ---- tuning ----
	public static final double SMELL_RANGE = 48.0;
	public static final double CLAMP_REACH = 3.0, CLAMP_LEASH = 1.6;
	public static final int CLAMP_TICKS = 80, CLAMP_COOLDOWN = 100;
	public static final float CLAMP_DAMAGE = 6.0F;
	public static final double LUNGE_OUT = 1.5, LUNGE_UP = 0.6, LUNGE_HIT = 1.0;
	public static final int LUNGE_TICKS = 20, LUNGE_COOLDOWN = 80;
	public static final float LUNGE_DAMAGE = 5.0F;
	public static final double FRENZY_RANGE = 16.0;
	public static final int FRENZY_TICKS = 200, FRENZY_COOLDOWN = 400, FRENZY_MAX = 3;
	public static final int ROLL_TICKS = 40, ROLL_EVERY = 10, ROLL_COOLDOWN = 200, ROLL_DOWN = 60;
	public static final float ROLL_DAMAGE = 3.0F;

	public Gnasher() {
		super("gnasher", 1.1F, 30.0F, 6.0, 0.0, 0.4, 0.0, Items.COD, 0x5C7488);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Dolphin;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.latched = -1;
		live.latchTicks = live.rush = live.frenzy = live.rolls = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (live.latched >= 0) {
					live.latched = -1;
					live.latchTicks = live.rolls = 0;
					return;
				}
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, CLAMP_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), CLAMP_DAMAGE);
				Bleeding.open(target, BodyPart.BODY, true);
				live.latched = target.getId();
				live.latchTicks = CLAMP_TICKS;
				live.hurt = true;
				sound(player, SoundEvents.DOLPHIN_ATTACK, 1.2F, 0.6F);
				cooldown(player, live, R, CLAMP_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					Vec3 look = player.getLookAngle();
					player.setDeltaMovement(look.x * LUNGE_OUT, Math.max(LUNGE_UP, look.y * LUNGE_OUT), look.z * LUNGE_OUT);
					player.syncVelocity = true;
					live.rush = LUNGE_TICKS;
					sound(player, player.isInWater() ? SoundEvents.DOLPHIN_JUMP : SoundEvents.PLAYER_SPLASH, 1.2F, 0.7F);
					cooldown(player, live, G, LUNGE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.frenzy = FRENZY_TICKS;
					sound(player, SoundEvents.DOLPHIN_PLAY, 1.2F, 0.5F);
					cooldown(player, live, V, FRENZY_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (!ready(player, live, B)) {
					return;
				}
				if (live.latched < 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gnasher.no_hold").withStyle(ChatFormatting.GRAY));
					return;
				}
				live.rolls = ROLL_TICKS;
				live.latchTicks = Math.max(live.latchTicks, ROLL_TICKS + 1);
				sound(player, SoundEvents.PLAYER_SPLASH, 1.4F, 0.6F);
				cooldown(player, live, B, ROLL_COOLDOWN);
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, 1, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
		}
		// blood in the water
		if (player.tickCount % 20 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SMELL_RANGE),
				e -> e != player && e.isAlive() && e.isInWater() && Bleeding.bleeding(e) != 0)) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false), player);
			}
		}
		if (live.rush > 0) {
			live.rush--;
			for (LivingEntity e : around(player, LUNGE_HIT + 1.0)) {
				if (e.getId() != live.latched && e.getBoundingBox().inflate(LUNGE_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), LUNGE_DAMAGE);
					live.hurt = true;
					live.rush = 0;
					break;
				}
			}
			if (player.onGround() && live.rush < LUNGE_TICKS - 3) {
				live.rush = 0;
			}
		}
		if (live.frenzy > 0) {
			live.frenzy--;
			if (live.frenzy % 20 == 0) {
				int bleeders = Math.min(FRENZY_MAX, (int)around(player, FRENZY_RANGE).stream().filter(e -> Bleeding.bleeding(e) != 0).count());
				if (bleeders > 0) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, bleeders - 1, false, false));
					player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, bleeders - 1, false, false));
				}
			}
		}
		if (live.latched >= 0) {
			jaws(player, live, level);
		}
	}

	/** Held in the jaws; and while the roll lasts, rolled and torn. */
	private static void jaws(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Entity e = level.getEntity(live.latched);
		if (!(e instanceof LivingEntity held) || !held.isAlive() || held.distanceTo(player) > CLAMP_REACH * 2.0 || --live.latchTicks <= 0) {
			live.latched = -1;
			live.latchTicks = live.rolls = 0;
			return;
		}
		held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false), player);
		Vec3 jaw = player.position().add(player.getLookAngle().scale(1.2));
		Vec3 pull = jaw.subtract(held.position());
		if (pull.length() > CLAMP_LEASH * 0.5) {
			held.setDeltaMovement(pull.scale(0.4));
			held.syncVelocity = true;
		}
		if (live.rolls > 0) {
			live.rolls--;
			held.setYRot(held.getYRot() + 45.0F);
			if (player.tickCount % 2 == 0) {
				level.sendParticles(held.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, held.getX(), held.getY() + held.getBbHeight() * 0.5,
					held.getZ(), 6, 0.4, 0.3, 0.4, 0.05);
			}
			if (live.rolls % ROLL_EVERY == 0) {
				held.setInvulnerableTime(0);
				held.hurtServer(level, player.damageSources().playerAttack(player), ROLL_DAMAGE);
				Bleeding.open(held, BodyPart.BODY, true);
				live.hurt = true;
			}
			if (live.rolls == 0) {
				// the roll ends: thrown down and let go
				if (!Tripped.bind(held, player, ROLL_DOWN)) {
					held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROLL_DOWN, 4), player);
				}
				live.latched = -1;
				live.latchTicks = 0;
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.BUBBLE, player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.1);
		sound(player, SoundEvents.GUARDIAN_ATTACK, 1.4F, 0.5F);
		return true;
	}
}
