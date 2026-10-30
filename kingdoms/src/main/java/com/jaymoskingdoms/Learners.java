package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * (0.7.413) Companions who learn. An MCA villager hired as a mercenary (MCA's Hire: {@code makeMercenary}) gets tougher
 * with every fight they win while hired: a hostile they hit that dies, or a fight against hostiles they come out of alive
 * ({@link #QUIET_TICKS} with nothing hurting them). Every {@link #WINS_PER_STEP} wins is one step, to {@link #MAX_STEPS}:
 * each step is {@link #HEALTH_PER_STEP} more health and {@link #DAMAGE_PER_STEP} more attack damage, as permanent
 * attribute modifiers (so they stay if the contract runs out and they're un-hired; they just stop learning). The wins
 * are kept per villager in {@link #WINS}. A step up gets one short line and a few green sparks. Without MCA none of this loads.
 */
public final class Learners {
	/** Wins to a step, and the most steps. */
	static final int WINS_PER_STEP = 2, MAX_STEPS = 10;
	/** What each step adds: health points and attack damage. */
	static final double HEALTH_PER_STEP = 2.0, DAMAGE_PER_STEP = 0.5;
	/** A fight is over (and survived) after this long (ticks) with no hostile having hurt them. */
	static final long QUIET_TICKS = 200L;
	/** How often fights are checked (ticks). */
	static final int EVERY = 20;
	/** The line said on a step: {@code speech.jaymoskingdoms.learner.N}. */
	static final int LINES = 4;
	static final double SPEECH_RANGE = 12.0;

	static final Identifier HEALTH = JaymosKingdoms.id("learned_health"), DAMAGE = JaymosKingdoms.id("learned_damage");

	/** Fights won while hired (saved per villager). */
	public static final AttachmentType<Integer> WINS = AttachmentRegistry.create(JaymosKingdoms.id("learned_wins"),
		builder -> builder.persistent(Codec.INT));

	/** Hired villagers a hostile has hurt, and when last (a fight in progress). */
	private static final Map<UUID, Long> FIGHTING = new HashMap<>();

	private Learners() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Learners::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof VillagerEntityMCA v && hired(v) && source.getEntity() instanceof Enemy && taken > 0) {
				FIGHTING.put(v.getUUID(), v.level().getGameTime());
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof VillagerEntityMCA v && v.hasAttached(WINS)) {
				apply(v);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Learners::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIGHTING.clear());
	}

	/** {@code v} is hired just now: MCA's mercenary. */
	static boolean hired(VillagerEntityMCA v) {
		return v.getProfession() == ProfessionsMCA.MERCENARY;
	}

	/** Steps {@code wins} come to. */
	static int steps(int wins) {
		return Math.min(MAX_STEPS, wins / WINS_PER_STEP);
	}

	/** The steps {@code v} has learned. */
	public static int stepsOf(VillagerEntityMCA v) {
		return steps(v.getAttachedOrElse(WINS, 0));
	}

	private static void died(LivingEntity victim, DamageSource source) {
		if (victim instanceof Enemy && source.getEntity() instanceof VillagerEntityMCA v && v.isAlive() && hired(v)) {
			FIGHTING.remove(v.getUUID());
			win(v);
		}
	}

	private static void tick(MinecraftServer server) {
		if (FIGHTING.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		var it = FIGHTING.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Entity found = null;
			for (ServerLevel level : server.getAllLevels()) {
				found = level.getEntity(e.getKey());
				if (found != null) {
					break;
				}
			}
			if (!(found instanceof VillagerEntityMCA v) || !v.isAlive() || !hired(v)) {
				// (gone, fallen, unloaded or no longer hired: no fight to count)
				it.remove();
				continue;
			}
			if (v.level().getGameTime() - e.getValue() >= QUIET_TICKS) {
				it.remove();
				win(v);
			}
		}
	}

	/** One more fight won by {@code v}; a step up if it comes to one. */
	static void win(VillagerEntityMCA v) {
		int before = steps(v.getAttachedOrElse(WINS, 0));
		int wins = v.getAttachedOrElse(WINS, 0) + 1;
		if (before >= MAX_STEPS) {
			return;
		}
		v.setAttached(WINS, wins);
		if (steps(wins) > before) {
			apply(v);
			if (v.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.2, v.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
				Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.learner." + v.getRandom().nextInt(LINES)),
					Speech.WHITE, SPEECH_RANGE);
			}
		}
	}

	/** {@code v}'s modifiers for the steps they've learned (kept after they're un-hired). */
	static void apply(VillagerEntityMCA v) {
		int steps = stepsOf(v);
		AttributeInstance health = v.getAttribute(Attributes.MAX_HEALTH), damage = v.getAttribute(Attributes.ATTACK_DAMAGE);
		float was = v.getMaxHealth();
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH, steps * HEALTH_PER_STEP, AttributeModifier.Operation.ADD_VALUE));
			if (v.getMaxHealth() > was) {
				// (the new health comes whole, as a rest would)
				v.heal(v.getMaxHealth() - was);
			}
		}
		if (damage != null) {
			damage.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE, steps * DAMAGE_PER_STEP, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
