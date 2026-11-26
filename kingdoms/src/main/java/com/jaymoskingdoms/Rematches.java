package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Great foes grow each time they fall (Andy's pick from Progressive Bosses, MIT, ideas only). Each time a player beats a
 * great foe of one kind in a world (a Giant of one element, a kind of monster of legend, a wild wind dragon, the Ender
 * Dragon), the next of that kind rises one step stronger, up to {@link #MOST} steps. A step adds health and damage, on top
 * of the world tier ({@link WorldTiers#raise}), and one more move, in this order: a rally roar at half health (Strength,
 * Speed), a firmer stance (knockback resistance), the roar shakes off what ails it, a second roar at a quarter health,
 * and it heals in the roar. A foe keeps the step it rose with.
 */
public final class Rematches {
	// ===== the knobs ================================================================================================
	/** The most steps a kind can grow. */
	static final int MOST = 5;
	/** Per step: extra health and damage (share of the base). */
	static final double HEALTH = 0.12, DAMAGE = 0.08;
	/** The firm stance's knockback resistance (step 2 on). */
	static final double STEADY = 0.3;
	/** How long the rally's Strength and Speed last (ticks). */
	static final int RALLY_TICKS = 160;
	/** The health shares the rallies come at. */
	static final float FIRST_RALLY = 0.5F, SECOND_RALLY = 0.25F;
	// ================================================================================================================

	/** How many of each kind players have beaten, on the Overworld. */
	static final AttachmentType<Map<String, Integer>> BEATEN = AttachmentRegistry.create(JaymosKingdoms.id("great_foes_beaten"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** The step a foe rose with. */
	static final AttachmentType<Integer> STEP = AttachmentRegistry.create(JaymosKingdoms.id("great_foe_step"), b -> b.persistent(Codec.INT));
	/** How many rallies it has roared. */
	static final AttachmentType<Integer> RALLIES = AttachmentRegistry.create(JaymosKingdoms.id("great_foe_rallies"), b -> b.persistent(Codec.INT));

	private static final Identifier HEALTH_ID = JaymosKingdoms.id("rematch_health"), DAMAGE_ID = JaymosKingdoms.id("rematch_damage"),
		STEADY_ID = JaymosKingdoms.id("rematch_steady");

	private Rematches() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && !mob.hasAttached(STEP) && kind(mob) != null) {
				rise(level, mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			String kind = kind(entity);
			if (kind != null && entity.getLastHurtByPlayer() != null && entity.level() instanceof ServerLevel level) {
				ServerLevel overworld = level.getServer().overworld();
				Map<String, Integer> beaten = new HashMap<>(overworld.getAttachedOrElse(BEATEN, Map.of()));
				beaten.merge(kind, 1, (a, b) -> Math.min(MOST, a + b));
				overworld.setAttached(BEATEN, beaten);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity.isAlive() && entity.level() instanceof ServerLevel level) {
				rally(level, entity);
			}
		});
	}

	/** The great foe kind {@code e} is, or null: Giants by element, monsters of legend and dragons by type. */
	static String kind(Entity e) {
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		if (e instanceof com.jaymoskingdoms.remnants.AetherGiant giant) {
			return type + "/" + giant.element().name().toLowerCase(java.util.Locale.ROOT);
		}
		if (e instanceof com.jaymoskingdoms.monsters.LegendMonster || e.getType() == EntityTypes.ENDER_DRAGON
			|| e instanceof com.jaymoskingdoms.dragons.WindDragon dragon && !dragon.isTamed()) {
			return type;
		}
		return null;
	}

	/** {@code mob} rises as strong as its kind has grown. */
	private static void rise(ServerLevel level, Mob mob) {
		int step = Math.min(MOST, level.getServer().overworld().getAttachedOrElse(BEATEN, Map.<String, Integer>of()).getOrDefault(kind(mob), 0));
		mob.setAttached(STEP, step);
		if (step == 0) {
			return;
		}
		float share = mob.getMaxHealth() > 0.0F ? mob.getHealth() / mob.getMaxHealth() : 1.0F;
		modify(mob, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH * step);
		modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE * step);
		if (step >= 2) {
			AttributeInstance knock = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knock != null) {
				knock.addOrReplacePermanentModifier(new AttributeModifier(STEADY_ID, STEADY, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		mob.setHealth(mob.getMaxHealth() * share);
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** A grown foe brought low enough roars and comes on harder (step 1 on; a second roar from step 4). */
	private static void rally(ServerLevel level, LivingEntity foe) {
		int step = foe.getAttachedOrElse(STEP, 0);
		if (step == 0) {
			return;
		}
		int done = foe.getAttachedOrElse(RALLIES, 0), most = step >= 4 ? 2 : 1;
		float at = done == 0 ? FIRST_RALLY : SECOND_RALLY;
		if (done >= most || foe.getHealth() > foe.getMaxHealth() * at) {
			return;
		}
		foe.setAttached(RALLIES, done + 1);
		foe.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 0));
		foe.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, 0));
		if (step >= 3) {
			for (MobEffectInstance effect : List.copyOf(foe.getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
					foe.removeEffect(effect.getEffect());
				}
			}
		}
		if (step >= 5) {
			foe.addEffect(new MobEffectInstance(MobEffects.REGENERATION, RALLY_TICKS / 2, 1));
		}
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.6F);
		Fx.ring(level, foe.position(), 1.5F + foe.getBbWidth(), 0xB03020);
	}
}
