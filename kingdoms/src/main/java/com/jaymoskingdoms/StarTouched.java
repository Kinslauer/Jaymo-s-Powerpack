package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.384, SevTech: far worlds restart the stone age) Star-touched foes. On Jaymo's Powers' six far worlds, a hostile mob now and
 * then comes up touched by the world's stars: it shows it ({@link MonsterGear#starLook}: dyed leather in its world's colour
 * where it wears armour, and motes of light for all of them) and carries one trait for the world. No new mobs, no new text:
 * <ul>
 * <li>the Pale Moon: long leaps at its target;</li>
 * <li>Rustfall: heavy blows (harder, and they throw you);</li>
 * <li>Glacia: a freezing touch (slows, and chills the bones);</li>
 * <li>Cinder: its blows set you alight;</li>
 * <li>the Maw: it drags what it hunts toward it;</li>
 * <li>the Verdant World: it grows back from its wounds.</li>
 * </ul>
 * Jaymo's Powers is never compiled against: a far world is a dimension of its namespace by name, the same way the other
 * cross-mod hooks go.
 */
public final class StarTouched {
	// ---- tuning ----
	/** The share of hostile mobs on a far world that are star-touched. */
	static final float STAR_CHANCE = 0.3F;
	/** How often (ticks) star-touched mobs near a player are looked after, and how near a player they must be. */
	static final int SWEEP_TICKS = 10;
	static final double SWEEP_RANGE = 48.0;
	/** Moon: ticks between leaps, the range a leap is made in (min, max), and its speed and lift. */
	static final int LEAP_TICKS = 60;
	static final double LEAP_MIN = 4.0, LEAP_MAX = 18.0, LEAP_SPEED = 1.1, LEAP_LIFT = 0.65;
	/** Rustfall: the share added to its blows, and how hard a blow throws what it hits. */
	static final double HEAVY_DAMAGE = 0.5, HEAVY_PUSH = 1.2, HEAVY_LIFT = 0.35;
	/** Glacia: how long its touch slows (ticks), how deep, and the freezing ticks it adds. */
	static final int CHILL_TICKS = 100, CHILL_FREEZE = 80;
	/** Cinder: seconds a blow sets alight. */
	static final float BURN_SECONDS = 4.0F;
	/** Maw: how far its pull reaches (it holds off nearer than {@link #PULL_STOP}), and how hard it pulls each sweep. */
	static final double PULL_RANGE = 14.0, PULL_STOP = 3.0, PULL_STRENGTH = 0.22;
	/** Verdant: health regrown each sweep. */
	static final float REGROW = 0.6F;

	/** The far worlds, by their dimension's name. The index is the trait (and what is saved is the index + 1). */
	private static final List<String> WORLDS = List.of("moon", "rustfall", "glacia", "cinder", "maw", "verdant_world");
	/** Each world's colour (for the dyed armour). */
	static final int[] COLOURS = {0xD8E4F0, 0xB5532A, 0x8FD3F0, 0xE0782A, 0x7A3FD0, 0x6FD06A};
	private static final Identifier HEAVY_ID = JaymosKingdoms.id("star_touched_heavy");

	/** Whether (and how) a mob has been looked at: unset = not yet, -1 = not touched, else its world's index + 1. Saved with it. */
	static final AttachmentType<Integer> STAR = AttachmentRegistry.create(JaymosKingdoms.id("star_touched"), builder -> builder.persistent(Codec.INT));

	private StarTouched() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.getAttached(STAR) == null) {
				roll(level, mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!blocked && source.getEntity() instanceof Mob mob) {
				struck(mob, victim);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (world(player.level()) < 0 || !player.isAlive()) {
					continue;
				}
				for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SWEEP_RANGE),
					m -> m.isAlive() && m.getAttachedOrElse(STAR, 0) > 0)) {
					sweep(player.level(), mob, mob.getAttachedOrElse(STAR, 0) - 1, server.getTickCount());
				}
			}
		});
	}

	/** Which far world {@code level} is (the index into {@link #WORLDS}), or -1. */
	static int world(ServerLevel level) {
		Identifier id = level.dimension().identifier();
		return "jaymospowers".equals(id.getNamespace()) ? WORLDS.indexOf(id.getPath()) : -1;
	}

	/** A hostile mob meeting a far world for the first time: touched by the stars now and then. */
	private static void roll(ServerLevel level, Mob mob) {
		int world = world(level);
		if (world < 0 || !WorldTiers.hostile(mob)) {
			return;
		}
		if (mob.getRandom().nextFloat() >= STAR_CHANCE) {
			mob.setAttached(STAR, -1);
			return;
		}
		mob.setAttached(STAR, world + 1);
		if (world == 1) {
			AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.addOrReplacePermanentModifier(new AttributeModifier(HEAVY_ID, HEAVY_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		MonsterGear.starLook(mob, COLOURS[world]);
	}

	/** Its blows: Rustfall throws, Glacia chills, Cinder burns. */
	private static void struck(Mob mob, LivingEntity victim) {
		int world = mob.getAttachedOrElse(STAR, 0) - 1;
		if (world == 1) {
			Vec3 away = victim.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				away = away.normalize().scale(HEAVY_PUSH);
				victim.push(away.x, HEAVY_LIFT, away.z);
				victim.syncVelocity = true;
			}
		} else if (world == 2) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1));
			victim.setTicksFrozen(victim.getTicksFrozen() + CHILL_FREEZE);
		} else if (world == 3) {
			victim.igniteForSeconds(BURN_SECONDS);
		}
	}

	/** Every {@link #SWEEP_TICKS}: its motes, and the traits that act on their own (leap, pull, regrowth). */
	private static void sweep(ServerLevel level, Mob mob, int world, int tick) {
		if (world < 0 || world >= WORLDS.size()) {
			return;
		}
		level.sendParticles(mote(world), mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3,
			mob.getBbWidth() * 0.4, 0.01);
		LivingEntity target = mob.getTarget();
		switch (world) {
			case 0 -> {
				if (target != null && mob.onGround() && tick % LEAP_TICKS < SWEEP_TICKS) {
					double distance = mob.distanceTo(target);
					if (distance >= LEAP_MIN && distance <= LEAP_MAX) {
						Vec3 toward = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0).normalize();
						mob.setDeltaMovement(toward.x * LEAP_SPEED, LEAP_LIFT, toward.z * LEAP_SPEED);
					}
				}
			}
			case 4 -> {
				if (target != null) {
					double distance = mob.distanceTo(target);
					if (distance > PULL_STOP && distance < PULL_RANGE) {
						Vec3 toward = mob.position().subtract(target.position()).normalize().scale(PULL_STRENGTH);
						target.push(toward.x, toward.y * 0.5, toward.z);
						target.syncVelocity = true;
					}
				}
			}
			case 5 -> {
				if (mob.getHealth() < mob.getMaxHealth()) {
					mob.heal(REGROW);
				}
			}
			default -> {
			}
		}
	}

	private static ParticleOptions mote(int world) {
		return switch (world) {
			case 0 -> ParticleTypes.END_ROD;
			case 1 -> ParticleTypes.SMOKE;
			case 2 -> ParticleTypes.SNOWFLAKE;
			case 3 -> ParticleTypes.FLAME;
			case 4 -> ParticleTypes.PORTAL;
			default -> ParticleTypes.HAPPY_VILLAGER;
		};
	}
}
