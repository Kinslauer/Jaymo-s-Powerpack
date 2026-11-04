package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (0.7.517) The Brute's rage (Andy: "Brute needs a type of rage"; the idea from Warrior Rage, which is all rights reserved,
 * so nothing taken but the idea). Blows given and taken in melee, and kills, fill a rage meter; it empties again a few
 * seconds after the fight ends. Full, the Brute roars and rages: harder blows, hard to shove, fire round their fists,
 * and every kill keeps it burning longer. When it burns out they are spent for a few seconds.
 */
public final class BruteRage {
	/** The meter gained (of 1) for a melee blow given, a blow taken (blocked or not) and a kill. */
	static final float FILL_HIT = 0.08F, FILL_HURT = 0.10F, FILL_KILL = 0.25F;
	/** No blow for this long (ticks) and the meter drains, this much a tick. */
	static final int CALM_AFTER = 20 * 5;
	static final float DRAIN = 0.01F;
	/** How long a rage lasts (ticks), what each kill adds, and the most it can hold. */
	public static final int RAGE_TICKS = 20 * 12, KILL_EXTEND = 20 * 3, RAGE_MAX = 20 * 20;
	/** While raging: melee blows this much harder (a share), knockback resistance, and enemies set alight (ticks). */
	static final float RAGE_DAMAGE = 0.25F;
	static final double RAGE_KNOCKBACK = 0.5;
	static final int RAGE_BURN_SECONDS = 2;
	/** Spent afterwards: Slowness and Mining Fatigue for this long (ticks). */
	static final int SPENT_TICKS = 20 * 6;

	/** The meter (0-1), shown on its owner's screen. Not kept. */
	public static final AttachmentType<Float> METER = AttachmentRegistry.create(JaymosKingdoms.id("brute_rage"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
	/** Ticks of rage left (0 = calm), seen by everyone (the fire round them). Not kept. */
	public static final AttachmentType<Integer> RAGING = AttachmentRegistry.create(JaymosKingdoms.id("brute_raging"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** When each Brute last gave or took a blow (game tick). */
	private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();

	private BruteRage() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BruteRage::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(BruteRage::died);
	}

	/** The meter (0-1), on either side. */
	public static float meter(Player player) {
		Float f = player.getAttached(METER);
		return f == null ? 0.0F : f;
	}

	/** Ticks of rage left, on either side. */
	public static int raging(Player player) {
		Integer i = player.getAttached(RAGING);
		return i == null ? 0 : i;
	}

	/** {@code target} taking {@code amount}: a raging Brute hits harder, and either side of a melee blow fills a Brute's meter. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer brute && source.getDirectEntity() == brute && brute != target
			&& Classes.is(brute, PlayerClass.BRUTE)) {
			fought(brute);
			if (raging(brute) > 0) {
				amount *= 1.0F + RAGE_DAMAGE;
				target.igniteForSeconds(RAGE_BURN_SECONDS);
			} else {
				fill(brute, FILL_HIT);
			}
		}
		if (target instanceof ServerPlayer hurt && Classes.is(hurt, PlayerClass.BRUTE) && source.getEntity() instanceof LivingEntity
			&& source.getEntity() != hurt) {
			fought(hurt);
			if (raging(hurt) == 0) {
				fill(hurt, FILL_HURT);
			}
		}
		return amount;
	}

	private static void died(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer brute) || brute == dead || !Classes.is(brute, PlayerClass.BRUTE)) {
			return;
		}
		fought(brute);
		int left = raging(brute);
		if (left > 0) {
			brute.setAttached(RAGING, Math.min(RAGE_MAX, left + KILL_EXTEND));
		} else {
			fill(brute, FILL_KILL);
		}
	}

	private static void fought(ServerPlayer brute) {
		LAST_FIGHT.put(brute.getUUID(), brute.level().getGameTime());
	}

	private static void fill(ServerPlayer brute, float by) {
		brute.setAttached(METER, Math.min(1.0F, meter(brute) + by));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Classes.is(player, PlayerClass.BRUTE) || !player.isAlive()) {
				if (player.hasAttached(METER) || player.hasAttached(RAGING)) {
					player.removeAttached(METER);
					player.removeAttached(RAGING);
					Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "brute_rage", 0.0, AttributeModifier.Operation.ADD_VALUE);
				}
				continue;
			}
			ServerLevel level = player.level();
			int left = raging(player);
			if (left > 0) {
				left--;
				player.setAttached(RAGING, left);
				if (left % 10 == 0) {
					level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(0.6), player.getZ(), 6, 0.35, 0.4, 0.35, 0.02);
				}
				if (left == 0) {
					player.removeAttached(RAGING);
					player.setAttached(METER, 0.0F);
					Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "brute_rage", 0.0, AttributeModifier.Operation.ADD_VALUE);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPENT_TICKS, 0));
					player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SPENT_TICKS, 0));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
				continue;
			}
			float meter = meter(player);
			if (meter >= 1.0F) {
				player.setAttached(RAGING, RAGE_TICKS);
				Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "brute_rage", RAGE_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.PLAYERS, 1.3F, 0.7F);
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(0.5), player.getZ(), 30, 0.5, 0.6, 0.5, 0.06);
				continue;
			}
			Long last = LAST_FIGHT.get(player.getUUID());
			if (meter > 0.0F && (last == null || level.getGameTime() - last > CALM_AFTER)) {
				float next = Math.max(0.0F, meter - DRAIN);
				if (next == 0.0F) {
					player.removeAttached(METER);
					LAST_FIGHT.remove(player.getUUID());
				} else {
					player.setAttached(METER, next);
				}
			}
		}
	}
}
