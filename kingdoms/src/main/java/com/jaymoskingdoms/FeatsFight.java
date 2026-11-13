package com.jaymoskingdoms;

import com.jaymoskingdoms.Feats.Feat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The feats that change a fight (see {@link Feats}):
 * <ul>
 * <li><b>Sentinel</b>: free blows come twice as often ({@link #SENTINEL_GAP} ticks, {@link Opportunity}) and stop whoever they land on, even
 * one breaking off.</li>
 * <li><b>Shield Master</b>: the shove ({@link Shoves}) comes round in {@link #SHIELD_SHOVE_GAP} ticks, wins {@link #SHIELD_SHOVE_ODDS} more
 * often and throws {@link #SHIELD_PUSH}x as hard; a raised shield takes the blast down to {@link #SHIELD_BLAST_LEFT}.</li>
 * <li><b>Mage Slayer</b>: a caster whose spell is in its window ({@link Counterspell}) is struck down by a Slayer within
 * {@link #SLAYER_REACH} blocks (the blow, and the spell is snuffed); blows on a casting mob land {@link #SLAYER_CASTING}x; spell
 * damage on them lands at {@link #SLAYER_MAGIC_LEFT}.</li>
 * <li><b>Mounted Fighter</b>: blows from the saddle land {@link #MOUNTED_BLOW}x, the mount takes {@link #MOUNTED_LEFT} of what would hurt
 * it, and a horse trusts the rider {@link #MOUNTED_TRUST}x sooner ({@link ClassPerks#trust}).</li>
 * <li><b>Charger</b>: a blow after {@link #CHARGE_TICKS} ticks of running flat out lands {@link #CHARGE_BONUS} harder and throws the foe back,
 * once every {@link #CHARGE_GAP} ticks.</li>
 * <li><b>Crossbow Expert</b>: the crossbow loads in {@link #CROSSBOW_LOAD} of the time ({@code CrossbowChargeMixin}) and its bolts
 * hit {@link #CROSSBOW_BOLT}x.</li>
 * </ul>
 */
public final class FeatsFight {
	static final int SENTINEL_GAP = 20;
	static final int SHIELD_SHOVE_GAP = 12;
	static final float SHIELD_SHOVE_ODDS = 0.25F, SHIELD_PUSH = 1.4F, SHIELD_BLAST_LEFT = 0.7F;
	static final double SLAYER_REACH = 5.0;
	static final int SLAYER_GAP = 40;
	static final float SLAYER_BLOW = 4.0F, SLAYER_CASTING = 1.5F, SLAYER_MAGIC_LEFT = 0.8F;
	static final float MOUNTED_BLOW = 1.2F, MOUNTED_LEFT = 0.6F, MOUNTED_TRUST = 1.5F;
	static final int CHARGE_TICKS = 30, CHARGE_GAP = 80;
	static final float CHARGE_BONUS = 4.0F, CHARGE_PUSH = 0.9F;
	static final float CROSSBOW_LOAD = 0.6F, CROSSBOW_BOLT = 1.15F;

	/** How long each Charger has run flat out, and when each last charged. */
	private static final Map<UUID, Integer> RUNNING = new HashMap<>();
	private static final Map<UUID, Long> CHARGED = new HashMap<>();

	private FeatsFight() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FeatsFight::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNNING.clear();
			CHARGED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Feats.has(player, Feat.CHARGER)) {
				continue;
			}
			if (player.isSprinting() && !player.isPassenger()) {
				RUNNING.merge(player.getUUID(), 1, Integer::sum);
			} else {
				RUNNING.remove(player.getUUID());
			}
		}
	}

	/** Whether {@code who} (a Sentinel) lands a free blow twice as often. */
	static boolean sentinel(LivingEntity who) {
		return who instanceof ServerPlayer p && Feats.has(p, Feat.SENTINEL);
	}

	static boolean shieldMaster(ServerPlayer player) {
		return Feats.has(player, Feat.SHIELD_MASTER);
	}

	/** Mounted Fighter's trust bonus for a horse's gain. */
	static float trust(Player player, float delta) {
		return delta > 0.0F && Feats.has(player, Feat.MOUNTED_FIGHTER) ? delta * MOUNTED_TRUST : delta;
	}

	/** Crossbow Expert: how long a crossbow takes to load for {@code who}, given {@code ticks}. */
	public static int loading(LivingEntity who, int ticks) {
		return who instanceof ServerPlayer p && Feats.has(p, Feat.CROSSBOW_EXPERT) ? Math.max(1, Math.round(ticks * CROSSBOW_LOAD)) : ticks;
	}

	/** The Mage Slayer's blow at a cast in its window: a Slayer within reach strikes the caster down. True if one did. */
	static boolean slay(ServerLevel level, net.minecraft.world.entity.PathfinderMob caster, ServerPlayer slayer) {
		if (!Feats.has(slayer, Feat.MAGE_SLAYER) || slayer.distanceToSqr(caster) > SLAYER_REACH * SLAYER_REACH || slayer.getMainHandItem().isEmpty()
			|| !slayer.hasLineOfSight(caster)) {
			return false;
		}
		slayer.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		caster.hurtServer(level, level.damageSources().playerAttack(slayer), SLAYER_BLOW);
		return true;
	}

	/** What the fighting feats do to a blow. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer p) {
			if (shieldMaster(p) && p.isBlocking() && source.is(DamageTypeTags.IS_EXPLOSION)) {
				amount *= SHIELD_BLAST_LEFT;
			}
			if (Feats.has(p, Feat.MAGE_SLAYER) && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
				amount *= SLAYER_MAGIC_LEFT;
			}
		}
		if (target.getControllingPassenger() instanceof ServerPlayer rider && Feats.has(rider, Feat.MOUNTED_FIGHTER)) {
			amount *= MOUNTED_LEFT;
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || sp == target) {
			return amount;
		}
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			ItemStack weapon = arrow.getWeaponItem();
			if (weapon != null && weapon.getItem() instanceof CrossbowItem && Feats.has(sp, Feat.CROSSBOW_EXPERT)) {
				amount *= CROSSBOW_BOLT;
			}
			return amount;
		}
		if (source.getDirectEntity() != sp) {
			return amount;
		}
		if (sp.isPassenger() && Feats.has(sp, Feat.MOUNTED_FIGHTER)) {
			amount *= MOUNTED_BLOW;
		}
		if (target instanceof Mob mob && Counterspell.casting(mob) && Feats.has(sp, Feat.MAGE_SLAYER)) {
			amount *= SLAYER_CASTING;
		}
		if (Feats.has(sp, Feat.CHARGER) && sp.level() instanceof ServerLevel level) {
			long now = level.getGameTime();
			if (RUNNING.getOrDefault(sp.getUUID(), 0) >= CHARGE_TICKS && now - CHARGED.getOrDefault(sp.getUUID(), -1000L) >= CHARGE_GAP) {
				CHARGED.put(sp.getUUID(), now);
				RUNNING.remove(sp.getUUID());
				amount += CHARGE_BONUS;
				Vec3 away = target.position().subtract(sp.position()).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() > 1.0E-4) {
					away = away.normalize().scale(CHARGE_PUSH);
					target.setDeltaMovement(away.x, Math.max(target.getDeltaMovement().y, 0.2), away.z);
					target.syncVelocity = true;
				}
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
		}
		return amount;
	}
}
