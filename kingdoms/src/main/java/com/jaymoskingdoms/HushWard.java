package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Warding Hush (Andy's Awakening pick, 2026-11-17: "the Lantern Warden's blessing, and mages can learn it from a scroll").
 * Blows and shots stop a hand's breadth short of you, and you pay for it: a point of stamina (Jaymo's Powers' feathers) for every
 * point of harm stopped. When the feathers run short, what they could not pay for gets through. Explosions always get through;
 * so does what comes without a hand behind it (a fall, fire, drowning, hunger).
 * <ul>
 * <li>The Lantern Warden's sworn have it from the third tier (beside Lamp Against the Night): the Warden keeps the dark at bay
 * round his own.</li>
 * <li>A Mage (or any staff caster) learns it from a Scroll of the Warding Hush ({@link NewSpells}, a court wizard's); cast,
 * it holds round the caster for {@link #SPELL_TICKS} ticks (longer from a strong staff).</li>
 * </ul>
 * Without Powers there are no feathers: the hush is paid in hunger instead, {@link #HUNGER_PER_POINT} exhaustion a point.
 */
public final class HushWard {
	// ---- tuning ----
	/** The Lantern Warden's tier that brings it. */
	static final int WARDEN_TIER = 3;
	/** How long the spell holds at power 1 (ticks), and the mana it costs at the staff. */
	static final int SPELL_TICKS = 20 * 30;
	/** Feathers per point of harm stopped (rounded up), and the exhaustion paid instead without Powers. */
	static final float FEATHERS_PER_POINT = 1.0F, HUNGER_PER_POINT = 1.5F;
	/** The Warden's pale lamp-light. */
	static final int COLOUR = 0x9AA8FF;
	/** How far short of the body the blow is shown to stop (blocks). */
	static final double SHORT = 0.6;

	public static final String SPELL = "hush_ward";

	/** The spell's end times (game time). Not saved. */
	private static final Map<UUID, Long> SPELLS = new HashMap<>();
	/** Harm the feathers paid for on a blow they could not wholly stop: taken off the blow this tick. */
	private static final Map<UUID, Float> PAID = new HashMap<>();

	private HushWard() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HushWard::allow);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SPELLS.remove(handler.player.getUUID());
			PAID.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPELLS.clear();
			PAID.clear();
		});
	}

	/** Whether {@code player} is hushed round right now. */
	public static boolean warded(Player player) {
		if (Patrons.blessed(player, Patron.LANTERN_WARDEN, WARDEN_TIER)) {
			return true;
		}
		Long until = SPELLS.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	/** The spell: the hush round the caster for a while. */
	static boolean cast(ServerLevel level, Player player, float power) {
		SPELLS.put(player.getUUID(), level.getGameTime() + Math.round(SPELL_TICKS * Math.max(0.5F, power)));
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), 1.4F, COLOUR, 30);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	/** A blow or a shot with someone behind it, and not a blast. */
	static boolean stoppable(DamageSource source) {
		return source.getEntity() != null && source.getDirectEntity() != null && !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !stoppable(source) || !warded(player)) {
			return true;
		}
		int points = Mth.ceil(amount * FEATHERS_PER_POINT);
		int paid = 0;
		if (!PowersCompat.hasFeathers()) {
			player.causeFoodExhaustion(points * HUNGER_PER_POINT);
			paid = player.getFoodData().getFoodLevel() > 0 ? points : 0;
		} else {
			while (paid < points && PowersCompat.spendFeathers(player, 1)) {
				paid++;
			}
		}
		if (paid <= 0) {
			return true;
		}
		show(player, source);
		if (paid >= points) {
			return false;
		}
		PAID.merge(player.getUUID(), paid / FEATHERS_PER_POINT, Float::sum);
		return true;
	}

	/** On the hurt path ({@code LivingEntityHurtMixin}): what the feathers paid for is taken off the blow that got through. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		Float paid = PAID.remove(target.getUUID());
		return paid == null ? amount : Math.max(0.0F, amount - paid);
	}

	/** The blow stopping short: a ring of light where it met the hush, between the body and what struck. */
	private static void show(ServerPlayer player, DamageSource source) {
		ServerLevel level = player.level();
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
		Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : source.getEntity() != null ? source.getEntity().getEyePosition() : centre;
		Vec3 dir = from.subtract(centre);
		dir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : dir.normalize();
		Vec3 at = centre.add(dir.scale(player.getBbWidth() * 0.5 + SHORT));
		Fx.ring(level, at, 0.7F, COLOUR);
		level.sendParticles(Fx.rune(COLOUR, 0.8F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 0.5F + level.getRandom().nextFloat() * 0.2F);
	}
}
