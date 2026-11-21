package com.jaymospowers.rig;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Rotations;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Mind looks after its host when it's nearly over (Andy's Rig picks, ideas only).
 * <ul>
 * <li><b>Evacuate.</b> The whole set worn at Mind level {@link Rig#CALL_LEVEL} or more: a hit that leaves its plates worn down to
 * {@link #WEAR} of their life or less with the Core at {@link #CHARGE} or less throws the wearer clear, up and out, and the suit
 * slumps where they stood (parked as ever, {@link RigCall}; an armour stand wears it, slumped, and doesn't guard: {@link #WRECK_TAG}).
 * The call key brings it back; the Forge mends it.</li>
 * <li><b>Fights while you're down.</b> Downed in the whole set at Mind level {@link Rig#TAKEOVER_LEVEL}, the Mind keeps firing the
 * palm at whatever is on you (what last hurt you, else the nearest foe) every {@link #DOWNED_EVERY} ticks, while the charge lasts.</li>
 * </ul>
 */
public final class RigEvac {
	// ---- tuning ----
	/** Evacuation: the plates' life left (share) and the Core's charge (share) at or under which it throws you clear. */
	static final float WEAR = 0.15F, CHARGE = 0.1F;
	/** The throw: up (b/t), and the slow fall after it (ticks). */
	static final double THROW_UP = 1.4;
	static final int SLOW_FALL_TICKS = 160;
	/** At most this often (ticks). */
	static final long EVAC_COOLDOWN = 2400L;
	/** Downed: how often it fires (ticks), how far it looks for the foe (blocks). */
	static final int DOWNED_EVERY = 30;
	static final double DOWNED_RANGE = 16.0;
	/** On a slumped suit's stand: it doesn't guard. */
	public static final String WRECK_TAG = "jaymospowers.rig_wreck";

	private static final Map<UUID, Long> NEXT_EVAC = new HashMap<>();

	private RigEvac() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && player.isAlive() && !player.isCreative()) {
				evacuate(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RigEvac::tick);
	}

	/** The worst-worn plate's life left (share of its durability), of the worn Rig pieces. */
	private static float worstWear(ServerPlayer player) {
		float worst = 1.0F;
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() != null && worn.is(piece.item()) && worn.isDamageableItem()) {
				worst = Math.min(worst, 1.0F - worn.getDamageValue() / (float)Math.max(1, worn.getMaxDamage()));
			}
		}
		return worst;
	}

	private static void evacuate(ServerPlayer player) {
		if (!Rig.fullSet(player) || Rig.level(player) < Rig.CALL_LEVEL || Rig.chargeShare(player) > CHARGE || worstWear(player) > WEAR) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < NEXT_EVAC.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		NEXT_EVAC.put(player.getUUID(), now + EVAC_COOLDOWN);
		List<ItemStack> wore = new ArrayList<>();
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			wore.add(piece.item() != null && worn.is(piece.item()) ? worn.copy() : ItemStack.EMPTY);
		}
		RigMind.sayAlways(player, "evac", 0.6F);
		if (!RigCall.park(player)) {
			return;
		}
		ServerLevel level = player.level();
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (stand != null) {
			stand.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
			stand.setShowArms(true);
			stand.setNoBasePlate(true);
			stand.setPermanentlyInvulnerable(true);
			// slumped: head down, arms hanging
			stand.setHeadPose(new Rotations(45.0F, 0.0F, 0.0F));
			stand.setLeftArmPose(new Rotations(15.0F, 0.0F, -8.0F));
			stand.setRightArmPose(new Rotations(15.0F, 0.0F, 8.0F));
			for (Rig.Piece piece : Rig.Piece.values()) {
				if (!wore.get(piece.ordinal()).isEmpty()) {
					stand.setItemSlot(piece.slot, wore.get(piece.ordinal()));
				}
			}
			stand.addTag(WRECK_TAG);
			stand.setAttached(RigSentry.OWNER, player.getUUID());
			level.addFreshEntity(stand);
			player.setAttached(RigSentry.POST, stand.getUUID());
		}
		player.setDeltaMovement(player.getDeltaMovement().x, THROW_UP, player.getDeltaMovement().z);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0), player);
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.6F, RigForgeBlock.FX_COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % DOWNED_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Downed.isDowned(player) && Rig.fullSet(player) && Rig.level(player) >= Rig.TAKEOVER_LEVEL) {
				guard(player);
			}
		}
	}

	/** Downed: the palm fires at whatever is on them, while the charge lasts. */
	private static void guard(ServerPlayer player) {
		LivingEntity foe = foe(player);
		if (foe == null || !Rig.spend(player, RigActives.PALM_COST)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position().add(0.0, 0.5, 0.0), to = foe.getBoundingBox().getCenter();
		MagicFx.beam(level, from, to, 0.08F, RigForgeBlock.FX_COLOR, 5);
		MagicFx.blast(level, to, 0.5F, RigForgeBlock.FX_COLOR);
		foe.hurtServer(level, level.damageSources().playerAttack(player), RigActives.PALM_DAMAGE * RigActives.levelDamage(player));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.9F);
		RigMind.say(player, "downed", 0.8F);
	}

	private static @Nullable LivingEntity foe(ServerPlayer player) {
		LivingEntity last = player.getLastHurtByMob();
		if (last != null && last.isAlive() && last.level() == player.level() && last.distanceTo(player) <= DOWNED_RANGE && player.hasLineOfSight(last)) {
			return last;
		}
		LivingEntity best = null;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DOWNED_RANGE),
			e -> e.isAlive() && e instanceof Enemy && player.hasLineOfSight(e))) {
			if (best == null || e.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = e;
			}
		}
		return best;
	}
}
