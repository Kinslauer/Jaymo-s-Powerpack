package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.514) The bridge perks: six perks on the borders between two pillars ({@link Skills.Perk#bridge()}), each
 * opened from a perk on either side and paid from either side's points, so a build can cross into its neighbour.
 * <ul>
 * <li>Stealth and Strength: Ambusher (a sneak attack staggers: slowed and weakened) and Cutthroat (a kill straight after
 * a sneak attack or an assassination drops you out of sight for a moment).</li>
 * <li>Strength and Survival: Iron Hide (armour and toughness) and Beast of Burden (a heart more, hunger slower).</li>
 * <li>Survival and Stealth: Tracker (whatever you wound bleeds a trail you can follow) and Light Pack (sneaking and
 * sprinting cost far less hunger).</li>
 * </ul>
 * Tuning numbers are the constants below.
 */
public final class Bridges {
	/** Ambusher: what a sneak attack leaves on its mark (ticks, amplifiers). */
	static final int AMBUSH_TICKS = 60, AMBUSH_SLOW = 2, AMBUSH_WEAK = 0;
	/** Cutthroat: a kill this soon after a sneak attack or assassination (ticks) counts, and what it gives (ticks). */
	static final int CUTTHROAT_WINDOW = 20, CUTTHROAT_TICKS = 80;
	/** Iron Hide: armour and toughness added. */
	static final double IRON_HIDE_ARMOR = 2.0, IRON_HIDE_TOUGHNESS = 1.0;
	/** Beast of Burden: health added, and the factor on hunger. */
	static final double BURDEN_HEALTH = 2.0;
	static final float BURDEN_EXHAUSTION = 0.8F;
	/**
	 * Tracker (Andy: "they just bleed so you can track them"): a wounded creature bleeds this long (ticks), a drop of its
	 * own blood (Powers' blood trail, marked as its) every {@link #TRACKER_DROP_TICKS}, this big.
	 */
	static final int TRACKER_TICKS = 600, TRACKER_DROP_TICKS = 15;
	static final float TRACKER_DROP_SIZE = 0.6F;
	/** Light Pack: the factor on hunger while sneaking or sprinting. */
	static final float LIGHT_PACK_EXHAUSTION = 0.6F;
	static final String ARMOR_ID = "bridge_iron_hide_armor", TOUGHNESS_ID = "bridge_iron_hide_toughness", HEALTH_ID = "bridge_beast_of_burden";

	/** Player → game time of their last sneak attack or assassination (Cutthroat). */
	private static final Map<UUID, Long> LAST_UNSEEN_BLOW = new HashMap<>();
	/** Tracker: who's bleeding a trail, and the game time it stops. */
	private static final Map<LivingEntity, Long> BLEEDING = new java.util.WeakHashMap<>();

	private Bridges() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_UNSEEN_BLOW.clear();
			BLEEDING.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (BLEEDING.isEmpty() || server.getTickCount() % TRACKER_DROP_TICKS != 0) {
				return;
			}
			var it = BLEEDING.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				LivingEntity bleeder = e.getKey();
				if (!bleeder.isAlive() || bleeder.isRemoved() || !(bleeder.level() instanceof ServerLevel level) || level.getGameTime() > e.getValue()) {
					it.remove();
				} else if (!bleeder.isInWater()) {
					PowersCompat.trailDrop(level, bleeder, TRACKER_DROP_SIZE);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity && Skills.rank(killer, Skills.Perk.CUTTHROAT) > 0) {
				Long last = LAST_UNSEEN_BLOW.get(killer.getUUID());
				if (last != null && killer.level().getGameTime() - last <= CUTTHROAT_WINDOW) {
					LAST_UNSEEN_BLOW.remove(killer.getUUID());
					killer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CUTTHROAT_TICKS, 0, false, false, true));
					killer.addEffect(new MobEffectInstance(MobEffects.SPEED, CUTTHROAT_TICKS, 0, false, false, true));
					if (killer.level() instanceof ServerLevel level) {
						level.sendParticles(ParticleTypes.LARGE_SMOKE, killer.getX(), killer.getY(0.5), killer.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
					}
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0.0F && entity.isAlive() && !(entity instanceof Player) && source.getEntity() instanceof ServerPlayer player
				&& player != entity && Skills.rank(player, Skills.Perk.TRACKER) > 0) {
				BLEEDING.put(entity, entity.level().getGameTime() + TRACKER_TICKS);
			}
		});
	}

	/** A sneak attack landed on {@code target} ({@link Senses#dealt}): Ambusher staggers it, and Cutthroat starts counting. */
	static void sneakAttack(ServerPlayer player, LivingEntity target) {
		unseenBlow(player);
		if (Skills.rank(player, Skills.Perk.AMBUSHER) > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AMBUSH_TICKS, AMBUSH_SLOW), player);
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AMBUSH_TICKS, AMBUSH_WEAK), player);
			player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.5F, 0.7F);
		}
	}

	/** A sneak attack or an assassination ({@link HiddenBlade}) by {@code player}, just now. */
	static void unseenBlow(ServerPlayer player) {
		LAST_UNSEEN_BLOW.put(player.getUUID(), player.level().getGameTime());
	}

	/** Iron Hide's armour and Beast of Burden's heart, as attribute modifiers. */
	static void attributes(ServerPlayer player) {
		boolean hide = Skills.rank(player, Skills.Perk.IRON_HIDE) > 0, burden = Skills.rank(player, Skills.Perk.BEAST_OF_BURDEN) > 0;
		Skills.modifier(player, Attributes.ARMOR, ARMOR_ID, hide ? IRON_HIDE_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, hide ? IRON_HIDE_TOUGHNESS : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, burden ? BURDEN_HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		if (!burden && player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** The factor on {@code player}'s hunger: Beast of Burden always, Light Pack while sneaking or sprinting. */
	static float exhaustion(ServerPlayer player) {
		float f = 1.0F;
		if (Skills.rank(player, Skills.Perk.BEAST_OF_BURDEN) > 0) {
			f *= BURDEN_EXHAUSTION;
		}
		if ((player.isShiftKeyDown() || player.isSprinting()) && Skills.rank(player, Skills.Perk.LIGHT_PACK) > 0) {
			f *= LIGHT_PACK_EXHAUSTION;
		}
		return f;
	}
}
