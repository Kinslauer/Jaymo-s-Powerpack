package com.jaymospowers.fighting;

import com.jaymospowers.core.wounds.Bleeding;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hits from different sources (Clouds and Bits patch 6). The game gives anything just hurt half a second of immunity (the
 * hurt cooldown: for {@link #WINDOW} ticks a second blow only lands for the part by which it beats the first). Here the
 * immunity is per kind of damage: a hit of a kind that hasn't landed in the last {@link #WINDOW} ticks lands in full, even
 * while another kind is still cooling; the same kind still waits, as it always did. Kinds, from the damage type's tags:
 * fire (burning, lava, a flame), magic and bleeding (potions, wither, poison, an open cut), melee (a blow struck by hand),
 * projectile, explosion, and everything else (falls, drowning, cacti...) as one kind of its own.
 * <p>
 * The cooldown the game keeps is one number per body, so this works by setting it back to nothing just before a hit of a
 * fresh kind is weighed ({@code LivingEntityHitKindsMixin}); the hit then starts a new cooldown as an ordinary one does, and
 * each kind keeps its own clock. Damage that bypasses the cooldown is left alone. Whatever a power does to the cooldown
 * itself (a ki barrage that clears it for a hit of the same kind) is unchanged: those stay one kind, so they still stack
 * only as they did.
 */
public final class HitKinds {
	/** How long (ticks) a kind of damage waits after it lands before it lands in full again: the game's own half second. */
	static final int WINDOW = 10;
	/** The hurt cooldown the game counts down from {@code 20} and weighs hits against above {@code 10}. */
	private static final int COOLING = 10;

	enum Kind {
		FIRE, MAGIC, MELEE, PROJECTILE, EXPLOSION, OTHER
	}

	/** Body → game time each kind of damage last landed on it. Not saved. */
	private static final Map<LivingEntity, long[]> LANDED = new WeakHashMap<>();

	private HitKinds() {
	}

	static Kind of(DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return Kind.EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return Kind.PROJECTILE;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return Kind.FIRE;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER) || source.is(Bleeding.BLEEDING_DAMAGE)) {
			return Kind.MAGIC;
		}
		if (source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()) {
			return Kind.MELEE;
		}
		return Kind.OTHER;
	}

	/** Called as a hit is about to be weighed (server side): clears the cooldown if this hit's kind isn't in its own. */
	public static void before(LivingEntity body, DamageSource source, float amount, long now) {
		if (amount <= 0.0F || !body.isAlive()) {
			return;
		}
		// a hit the body shrugs off (immune, or fire on the fire-proof) must not open its guard for the next one
		if (body.level() instanceof net.minecraft.server.level.ServerLevel level && body.isInvulnerableTo(level, source)
			|| source.is(DamageTypeTags.IS_FIRE) && (body.fireImmune() || body.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))) {
			return;
		}
		int kind = of(source).ordinal();
		long[] landed = LANDED.computeIfAbsent(body, b -> {
			long[] fresh = new long[Kind.values().length];
			Arrays.fill(fresh, Long.MIN_VALUE / 2);
			return fresh;
		});
		if (body.damageCooldownTime > COOLING && !source.is(DamageTypeTags.BYPASSES_COOLDOWN) && now - landed[kind] >= WINDOW) {
			body.damageCooldownTime = 0;
		}
		if (body.damageCooldownTime <= COOLING) {
			landed[kind] = now;
		}
	}
}
