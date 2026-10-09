package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Storm corruption, the Stormbound form, and its cure, the Eye of the Storm. Server side.
 *
 * <ul>
 * <li><b>The meter:</b> using the wind (flying, channelling, a barrier up, or just after a cast) under a real
 * thunderstorm fills it one point per {@link #ACTIVE_TICKS_PER_POINT} ticks (100 points ≈ 3 minutes). Out of the storm it
 * drains a point per {@link #DRAIN_TICKS}.</li>
 * <li><b>Stormbound:</b> every cast calls down lightning at its target (the caster is never hurt by it), thunder rolls at
 * the caster, gales hit twice as hard, and the weather always counts as a storm. In return: slower on foot and in
 * the air.</li>
 * <li><b>The lightning</b> is a visual-only bolt plus this class's own damage, so it never sets blocks alight (no
 * burnt-down bases) and never hurts its caster. Mobs it hits still burn.</li>
 * <li><b>The cure:</b> hover at {@link #EYE_MIN_Y}+ in the Overworld during a thunderstorm for {@link #EYE_TICKS}
 * without casting.</li>
 * </ul>
 */
public final class WindStorm {
	/** One corruption point per this many active ticks under a storm (100 points ≈ 3 min). */
	static final int ACTIVE_TICKS_PER_POINT = 36;
	/** A cast keeps you "active" for this long afterwards. */
	static final int ACTIVE_AFTER_CAST = 100;
	/** Out of the storm, a point drains away this often (15 s). */
	static final int DRAIN_TICKS = 300;
	/** Stormbound: walking speed and cruise speed multipliers. */
	static final double STORMBOUND_MOVE_PENALTY = -0.25;
	public static final double STORMBOUND_CRUISE_MULTIPLIER = 0.75;
	/** Stormbound: gales (the ground gust, the barrier's push) hit this much harder. */
	static final float STORMBOUND_GALE_MULTIPLIER = 2.0F;
	/** The lightning's damage, reach (vanilla's 3-block box) and how long it sets mobs burning. */
	static final float LIGHTNING_DAMAGE = 5.0F;
	static final double LIGHTNING_REACH = 3.0;
	static final int LIGHTNING_BURN_TICKS = 80;
	/** Thunder at the caster on each cast. */
	static final float CAST_THUNDER_VOLUME = 3.0F;
	/** The Eye of the Storm: how high, and how long (60 s), hovering in a thunderstorm without casting. */
	static final double EYE_MIN_Y = 192.0;
	public static final int EYE_TICKS = 1200;
	/** The calm is synced in this many steps (so others see the static fade). */
	public static final int CALM_STEPS = 20;

	private static final Identifier SLOW_MODIFIER = JaymosPowers.id("wind_stormbound_slow");

	/** How calm a Stormbound wind has grown (0..{@link #CALM_STEPS}), synced to everyone so the static thins out. */
	public static final AttachmentType<Integer> CALM = AttachmentRegistry.create(JaymosPowers.id("wind_calm"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Data> DATA = new HashMap<>();

	private static final class Data {
		int activeTicks;
		int drainTicks;
		int calmTicks;
		int sinceCast = 1000;
		/** The whisper has been given this storm. */
		boolean hinted;
	}

	private WindStorm() {
	}

	private static Data data(ServerPlayer player) {
		return DATA.computeIfAbsent(player.getUUID(), uuid -> new Data());
	}

	/** The calm's progress toward the cure, in ticks (debug). */
	static int calmTicks(ServerPlayer player) {
		return data(player).calmTicks;
	}

	static void setCalmTicks(ServerPlayer player, int ticks) {
		data(player).calmTicks = Math.max(0, ticks);
	}

	/** Gales hit this much harder (2× Stormbound). */
	public static float galeMultiplier(Player player) {
		return Fate.isCorrupted(player) ? STORMBOUND_GALE_MULTIPLIER : 1.0F;
	}

	/** Every tick while the player is alive with the power. */
	static void tick(ServerPlayer player, WindFlight flight, boolean channelling, boolean barrierUp) {
		Data d = data(player);
		if (d.sinceCast < 100000) {
			d.sinceCast++;
		}
		boolean corrupted = Fate.isCorrupted(player);
		if (corrupted) {
			applySlow(player);
		} else {
			removeSlow(player);
		}

		// ---- the meter
		if (!corrupted && Fate.canCorrupt(player)) {
			if (WindWeather.stormOverhead(player)) {
				d.drainTicks = 0;
				boolean active = flight.flying() || channelling || barrierUp || d.sinceCast < ACTIVE_AFTER_CAST;
				if (active && ++d.activeTicks >= ACTIVE_TICKS_PER_POINT) {
					d.activeTicks = 0;
					Fate.addCorruption(player, 1);
				}
			} else if (Fate.corruption(player) > 0 && ++d.drainTicks >= DRAIN_TICKS) {
				d.drainTicks = 0;
				Fate.addCorruption(player, -1);
			}
		}

		// ---- the Eye of the Storm
		ServerLevel level = player.level();
		boolean thunder = level.dimension() == Level.OVERWORLD && level.isThundering();
		if (!thunder) {
			d.hinted = false;
		}
		if (corrupted) {
			if (thunder && !d.hinted && flight.flying()) {
				d.hinted = true;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wind.whisper").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			boolean inTheEye = thunder && flight == WindFlight.HOVER && player.getY() >= EYE_MIN_Y && !channelling;
			if (inTheEye) {
				d.calmTicks++;
				if (d.calmTicks >= EYE_TICKS) {
					d.calmTicks = 0;
					Fate.purify(player);
					removeSlow(player);
				}
			} else {
				d.calmTicks = 0;
			}
		} else {
			d.calmTicks = 0;
		}
		syncCalm(player, d);
	}

	private static void syncCalm(ServerPlayer player, Data d) {
		int calm = Math.min(CALM_STEPS, d.calmTicks * CALM_STEPS / EYE_TICKS);
		Integer current = player.getAttached(CALM);
		if (calm == 0) {
			if (current != null) {
				player.removeAttached(CALM);
			}
		} else if (current == null || current != calm) {
			player.setAttached(CALM, calm);
		}
	}

	/**
	 * A cast happened (a channelled ability released, or the lance): the eye's calm is broken, the use counts toward the
	 * meter, and a Stormbound caster hears thunder roll.
	 */
	static void onCast(ServerPlayer player) {
		Data d = data(player);
		d.sinceCast = 0;
		d.calmTicks = 0;
		if (Fate.isCorrupted(player)) {
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
				CAST_THUNDER_VOLUME, 0.8F + player.getRandom().nextFloat() * 0.2F);
		}
	}

	/** A channel started: that breaks the calm too. */
	static void onChannel(ServerPlayer player) {
		data(player).calmTicks = 0;
	}

	/**
	 * Calls lightning down at {@code at} for a Stormbound caster: a visual-only bolt (no fire, no vanilla damage) and
	 * our own lightning damage to everything nearby except the caster.
	 */
	static void strike(ServerPlayer caster, Vec3 at) {
		ServerLevel level = caster.level();
		// thunder only comes out of real weather: none under a clear sky, at full strength only in a storm
		float sky = WindModes.thunderPower(level);
		if (sky <= 0.0F) {
			return;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at);
			bolt.setVisualOnly(true);
			bolt.setCause(caster);
			level.addFreshEntity(bolt);
		}
		AABB area = new AABB(at.x - LIGHTNING_REACH, at.y - LIGHTNING_REACH, at.z - LIGHTNING_REACH,
			at.x + LIGHTNING_REACH, at.y + 6.0 + LIGHTNING_REACH, at.z + LIGHTNING_REACH);
		DamageSource source = caster.damageSources().source(DamageTypes.LIGHTNING_BOLT, caster);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != caster && !e.isSpectator())) {
			target.hurtServer(level, source, LIGHTNING_DAMAGE * sky);
			WindModes.chargeCreeper(target);
			if (!target.fireImmune()) {
				target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), LIGHTNING_BURN_TICKS));
			}
		}
	}

	private static void applySlow(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(SLOW_MODIFIER)) {
			speed.addTransientModifier(new AttributeModifier(SLOW_MODIFIER, STORMBOUND_MOVE_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	static void removeSlow(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.hasModifier(SLOW_MODIFIER)) {
			speed.removeModifier(SLOW_MODIFIER);
		}
	}

	/** The power went (or purified): drop the penalty and the calm. */
	static void clear(ServerPlayer player) {
		removeSlow(player);
		player.removeAttached(CALM);
		Data d = DATA.get(player.getUUID());
		if (d != null) {
			d.calmTicks = 0;
		}
	}

	static void forget(ServerPlayer player) {
		DATA.remove(player.getUUID());
	}
}
