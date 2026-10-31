package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.102, Xeno-Band patch 5) Cortex, the thinking crab: a wide plated body, one big claw and one small, and an open
 * skull where a glowing brain sparks. 24 health, fists of 4. Scanned from a guardian. While you're it, looking at a
 * creature reads it: its health, and for folk with a temperament (Kingdoms), what kind of person they are.
 * <ul>
 * <li>R, Arc: lightning from the skull to the creature you look at, jumping on to {@link #ARC_JUMPS} more nearby.</li>
 * <li>G, Bubble: a mind-bubble for {@link #BUBBLE_TICKS} ticks that stops every shot.</li>
 * <li>V, Mind Lift: lift the creature you look at into the air with your mind, and drop it.</li>
 * <li>B, Claw Snap: the big claw, hard, and it holds the target slow.</li>
 * </ul>
 */
public final class Cortex extends XenoForm {
	// ---- tuning ----
	public static final double ARC_REACH = 14.0, ARC_JUMP = 5.0;
	public static final int ARC_JUMPS = 2, ARC_COOLDOWN = 30;
	public static final float ARC_DAMAGE = 5.0F, ARC_JUMP_DAMAGE = 3.0F;
	public static final int BUBBLE_TICKS = 120, BUBBLE_COOLDOWN = 300;
	public static final double LIFT_REACH = 16.0;
	public static final int LIFT_TICKS = 30, LIFT_COOLDOWN = 140;
	public static final double SNAP_REACH = 3.5;
	public static final float SNAP_DAMAGE = 7.0F;
	public static final int SNAP_COOLDOWN = 40;
	/** How far the mind reads, and how often (ticks). */
	public static final double READ_REACH = 16.0;
	public static final int READ_EVERY = 10;

	public Cortex() {
		super("cortex", 1.0F, 24.0F, 4.0, 0.0, 0.3, 0.0, Items.PRISMARINE_SHARD, 0x4FB8B0);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Guardian;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.bubbleUntil = 0L;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (live.bubbleUntil > player.level().getGameTime() && source.is(DamageTypeTags.IS_PROJECTILE)) {
			burst(player.level(), ParticleTypes.BUBBLE_POP, player.position().add(0.0, 1.0, 0.0), 10, 0.8, 0.05);
			sound(player, SoundEvents.SHULKER_SHOOT, 0.6F, 1.8F);
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
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity first = aimed(player, ARC_REACH, e -> true);
				if (first == null) {
					return;
				}
				arc(player, level, first);
				live.hurt = true;
				cooldown(player, live, R, ARC_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					live.bubbleUntil = level.getGameTime() + BUBBLE_TICKS;
					sound(player, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.0F, 1.2F);
					cooldown(player, live, G, BUBBLE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, LIFT_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_TICKS, 4, false, false), player);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LIFT_TICKS, 3, false, false), player);
				beam(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.ELECTRIC_SPARK, 14);
				sound(player, SoundEvents.GUARDIAN_AMBIENT, 1.0F, 1.6F);
				cooldown(player, live, V, LIFT_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (!ready(player, live, B)) {
					return;
				}
				LivingEntity target = aimed(player, SNAP_REACH, e -> true);
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), SNAP_DAMAGE);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), player);
					live.hurt = true;
				}
				sound(player, SoundEvents.GOAT_RAM_IMPACT, 0.8F, 1.6F);
				cooldown(player, live, B, SNAP_COOLDOWN);
			}
			default -> {
			}
		}
	}

	/** Lightning from the skull to {@code first}, then on to the nearest others. */
	private static void arc(ServerPlayer player, ServerLevel level, LivingEntity first) {
		List<LivingEntity> struck = new ArrayList<>();
		Vec3 from = player.getEyePosition().add(0.0, 0.3, 0.0);
		LivingEntity next = first;
		float damage = ARC_DAMAGE;
		while (next != null) {
			beam(level, from, next.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 12);
			next.hurtServer(level, player.damageSources().playerAttack(player), damage);
			struck.add(next);
			if (struck.size() > ARC_JUMPS) {
				break;
			}
			from = next.getBoundingBox().getCenter();
			damage = ARC_JUMP_DAMAGE;
			LivingEntity at = next;
			next = level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(ARC_JUMP),
					e -> e != player && e.isAlive() && !struck.contains(e) && !spared(player, e) && !e.isSpectator()).stream()
				.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
		}
		sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.8F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.bubbleUntil > level.getGameTime() && player.tickCount % 4 == 0) {
			ring(level, ParticleTypes.BUBBLE_POP, player.position().add(0.0, 1.0, 0.0), 1.2, 10);
		}
		if (player.tickCount % READ_EVERY != 0) {
			return;
		}
		LivingEntity seen = aimed(player, READ_REACH, e -> true);
		if (seen == null) {
			return;
		}
		MutableComponent line = Component.translatable("message.jaymospowers.xenoform.cortex.read", seen.getDisplayName(),
			Math.round(seen.getHealth()), Math.round(seen.getMaxHealth())).withStyle(ChatFormatting.AQUA);
		String temper = temper(seen);
		if (temper != null) {
			line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("temperament.jaymoskingdoms." + temper).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		player.sendOverlayMessage(line);
	}

	private static @Nullable Method temperOf, temperKey;
	private static boolean looked;

	/** {@code e}'s temperament key from Kingdoms' Temperaments (by reflection), or null without Kingdoms or for a creature with none. */
	private static @Nullable String temper(Entity e) {
		if (!looked) {
			looked = true;
			try {
				Class<?> c = Class.forName("com.jaymoskingdoms.Temperaments");
				temperOf = c.getMethod("of", Entity.class);
				temperKey = Class.forName("com.jaymoskingdoms.Temperaments$Temperament").getMethod("key");
			} catch (ReflectiveOperationException | LinkageError ex) {
				temperOf = temperKey = null;
			}
		}
		if (temperOf == null || temperKey == null) {
			return null;
		}
		try {
			Object t = temperOf.invoke(null, e);
			return t == null ? null : (String)temperKey.invoke(t);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 1.6, 0.0), 40, 0.8, 0.2);
		sound(player, SoundEvents.TRIDENT_THUNDER.value(), 0.8F, 1.4F);
		return true;
	}
}
