package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.GreatFoes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gravity's picks, every one a variant on a key or a passive:
 * <ul>
 * <li>sneak + B: <b>Binding Bands</b>, rings of weight that pin up to {@link #BANDS} enemies at once (Lift holds one).</li>
 * <li>sneak + Z: <b>Trading Places</b>: at a target, you swap places; at nothing, a sideways blink that leaves a copy;
 * looking straight down, everything you hit lately is dragged to your feet.</li>
 * <li>passive, <b>Last Footing</b>: when your health drops low you snap back to the last safe place you stood.</li>
 * <li>sneak + C in Collapse: <b>Nothing Stops Me</b>, about ten seconds with no knockback and no slowing; soft walls give
 * way as you walk, a leap lands in a crater that grows with the fall, and your steps shake the ground.</li>
 * </ul>
 * Trading Places lives here, not with the Xenoform alien Nadir: Nadir already has a whole kit of rifts and drops, and weight
 * pulling things to your feet fits Gravity.
 */
final class GravityPicks {
	private static final String TABLE = "gravity_picks";
	private static final int BIND = 0, TRADE = 1, SNAP = 2, STOP = 3;

	/** Binding Bands: how many at once, reach, ticks held, damage and its beat, the strength that breaks free, the cooldown. */
	static final int BANDS = 5, BAND_TICKS = 100, BAND_BEAT = 20, BIND_COOLDOWN = 360;
	static final double BAND_REACH = 18.0;
	static final float BAND_DAMAGE = 1.0F, STRONG_HEALTH = 60.0F;
	static final int STRONG_BREAKS_AFTER = 20;
	static final int BAND_COLOR = 0x5B4A8C;
	/** Trading Places: reach of the swap, of the sideways blink, the copy's life, the drag's reach, how long "lately" is, cooldown. */
	static final double SWAP_REACH = 40.0, BLINK = 6.0, DRAG_REACH = 48.0;
	static final int COPY_TICKS = 60, LATELY_TICKS = 120, TRADE_COOLDOWN = 240;
	static final float DRAG_DAMAGE = 2.0F;
	static final float DRAG_LOOK_PITCH = 65.0F;
	static final double LURE_RANGE = 32.0;
	static final String COPY_TAG = "jaymospowers_gravity_copy";
	/** Last Footing: health share that triggers it, how often a safe place is noted (ticks), how long since a hurt, the cooldown, the Resistance after. */
	static final float SNAP_BELOW = 0.3F;
	static final int NOTE_EVERY = 20, CALM_TICKS = 60, SNAP_COOLDOWN = 3600, SNAP_PROTECT = 60;
	static final double SNAP_MIN_DISTANCE = 4.0;
	/** Nothing Stops Me: ticks, cooldown, the hardest block that gives way, jump boost level, the leap's smallest fall and its caps. */
	static final int STOP_TICKS = 200, STOP_COOLDOWN = 1200;
	static final float WALL_HARDNESS = 1.0F;
	static final int LEAP_BOOST = 1;
	static final float LEAP_MIN_FALL = 3.0F, LEAP_PIECES_BASE = 3.0F;
	static final int CRATER_MAX_CLUSTERS = 3;
	static final float CRATER_HARDNESS = 2.5F, LEAP_DAMAGE_MAX = 14.0F;
	static final double LEAP_RADIUS_MAX = 7.0;
	static final int STEP_SHAKE_EVERY = 6, STEP_CRACK_EVERY = 12;
	static final float STEP_SHAKE = 0.35F;
	static final double STEP_SPEED = 0.08;
	private static final Identifier STOP_KNOCK_ID = JaymosPowers.id("gravity_nothing_stops_me");

	private record Band(int entity, long start, long until, boolean strong) {
	}

	private record Stop(long until, float maxFall) {
	}

	private record Place(net.minecraft.resources.ResourceKey<Level> level, Vec3 at) {
	}

	private record Copy(ServerLevel level, UUID copy, long until) {
	}

	private static final Map<UUID, List<Band>> BANDED = new HashMap<>();
	private static final Map<UUID, Stop> STOPS = new HashMap<>();
	private static final Map<UUID, Place> SAFE = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	/** Who the player hit lately: entity id to game time of the hit. */
	private static final Map<UUID, Map<Integer, Long>> HITS = new HashMap<>();
	private static final Map<UUID, Copy> COPIES = new HashMap<>();

	private GravityPicks() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Powers.has(attacker, GravityPower.INSTANCE)) {
				HITS.computeIfAbsent(attacker.getUUID(), k -> new HashMap<>()).put(entity.getId(), attacker.level().getGameTime());
			}
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && Powers.has(player, GravityPower.INSTANCE)) {
				LAST_HURT.put(player.getUUID(), player.level().getGameTime());
				lastFooting(player);
			}
		});
		// the first hit pops a copy and does nothing else
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(COPY_TAG) && entity.level() instanceof ServerLevel level) {
				COPIES.values().removeIf(c -> c.copy.equals(entity.getUUID()));
				pop(level, entity);
				return false;
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(COPY_TAG) && COPIES.values().stream().noneMatch(c -> c.copy.equals(entity.getUUID()))) {
				entity.discard();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Copy copy : COPIES.values()) {
				Entity e = copy.level.getEntity(copy.copy);
				if (e != null) {
					e.discard();
				}
			}
			COPIES.clear();
			BANDED.clear();
			STOPS.clear();
			SAFE.clear();
			LAST_HURT.clear();
			HITS.clear();
		});
	}

	static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		BANDED.remove(id);
		endStop(player);
		SAFE.remove(id);
		LAST_HURT.remove(id);
		HITS.remove(id);
		Copy copy = COPIES.remove(id);
		if (copy != null) {
			Entity e = copy.level.getEntity(copy.copy);
			if (e != null) {
				e.discard();
			}
		}
		PickKit.forget(player);
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_picks." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static boolean griefing(ServerLevel level, ServerPlayer player) {
		return player.mayBuild() && level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	// ---------------------------------------------------------------- Binding Bands

	/** sneak + B with nothing in hand: true if the bands went out (or were refused, so Crush doesn't start). */
	static boolean bands(ServerPlayer player) {
		if (!Mastery.isUnlocked(player, PowerKey.ABILITY_4)) {
			return false;
		}
		if (!PickKit.ready(TABLE, player, BIND)) {
			say(player, "cooling");
			return true;
		}
		ServerLevel level = player.level();
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, PickKit.around(player.position(), BAND_REACH), e -> PickKit.enemy(player, e)));
		foes.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		List<Band> bands = new ArrayList<>();
		long now = level.getGameTime();
		for (LivingEntity foe : foes) {
			if (bands.size() >= BANDS) {
				break;
			}
			bands.add(new Band(foe.getId(), now, now + BAND_TICKS, foe.getMaxHealth() > STRONG_HEALTH));
		}
		if (bands.isEmpty()) {
			say(player, "no_foes");
			return true;
		}
		BANDED.put(player.getUUID(), bands);
		PickKit.spend(TABLE, player, BIND, BIND_COOLDOWN);
		player.causeFoodExhaustion(1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.7F, 0.6F);
		return true;
	}

	private static void bandsTick(ServerPlayer player, ServerLevel level, long now) {
		List<Band> bands = BANDED.get(player.getUUID());
		if (bands == null) {
			return;
		}
		DamageSource source = player.damageSources().playerAttack(player);
		for (Iterator<Band> it = bands.iterator(); it.hasNext(); ) {
			Band band = it.next();
			Entity e = level.getEntity(band.entity);
			if (!(e instanceof LivingEntity foe) || !foe.isAlive() || now >= band.until || foe.distanceToSqr(player) > 64.0 * 64.0) {
				it.remove();
				continue;
			}
			boolean great = GreatFoes.is(foe);
			if (great) {
				// too great to hold: only slowed
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, true, false));
			} else if (band.strong && now - band.start >= STRONG_BREAKS_AFTER) {
				// much stronger: it breaks out early
				it.remove();
				MagicFx.blast(level, foe.getBoundingBox().getCenter(), 1.4F, BAND_COLOR);
				level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
				continue;
			} else {
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, true, false));
				Vec3 v = foe.getDeltaMovement();
				foe.setDeltaMovement(0.0, Math.min(v.y, -0.25), 0.0);
				foe.syncVelocity = true;
				if (foe instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				if ((now - band.start) % BAND_BEAT == BAND_BEAT - 1) {
					foe.hurtServer(level, source, BAND_DAMAGE);
				}
			}
			if (now % 8 == 0) {
				MagicFx.ring(level, foe.position().add(0.0, 0.1, 0.0), null, (float)Math.max(0.8, foe.getBbWidth() * 0.9), BAND_COLOR, 8);
				MagicFx.ring(level, foe.position().add(0.0, foe.getBbHeight() * 0.6, 0.0), null, (float)Math.max(0.7, foe.getBbWidth() * 0.8), BAND_COLOR, 8);
			}
		}
		if (bands.isEmpty()) {
			BANDED.remove(player.getUUID());
		}
	}

	// ---------------------------------------------------------------- Trading Places

	/** sneak + Z. True if it handled the key. */
	static boolean trade(ServerPlayer player) {
		if (!Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
			return false;
		}
		if (!PickKit.ready(TABLE, player, TRADE)) {
			say(player, "cooling");
			return true;
		}
		boolean went;
		if (player.getXRot() >= DRAG_LOOK_PITCH) {
			went = drag(player);
		} else {
			LivingEntity target = PickKit.aimed(player, SWAP_REACH);
			went = target != null ? swap(player, target) : blinkCopy(player);
		}
		if (went) {
			PickKit.spend(TABLE, player, TRADE, TRADE_COOLDOWN);
			player.causeFoodExhaustion(1.0F);
		}
		return true;
	}

	private static boolean swap(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		if (target.level() != level) {
			return false;
		}
		Vec3 mine = player.position(), theirs = target.position();
		MagicFx.beam(level, mine.add(0.0, 1.0, 0.0), theirs.add(0.0, 1.0, 0.0), 0.2F, BAND_COLOR, 8);
		target.teleportTo(level, mine.x, mine.y, mine.z, Set.of(), target.getYRot(), target.getXRot(), false);
		player.teleportTo(level, theirs.x, theirs.y, theirs.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		target.resetFallDistance();
		if (target instanceof Mob mob && mob.getTarget() == null) {
			mob.setTarget(player);
		}
		MagicFx.blast(level, mine.add(0.0, 1.0, 0.0), 1.4F, BAND_COLOR);
		MagicFx.blast(level, theirs.add(0.0, 1.0, 0.0), 1.4F, BAND_COLOR);
		level.playSound(null, theirs.x, theirs.y, theirs.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	/** The mannequin's skin, which only a mannequin's own code may set: read through a subclass that is never made. */
	private abstract static class Profile extends Mannequin {
		private Profile(Level level) {
			super(level);
		}

		static EntityDataAccessor<ResolvableProfile> key() {
			return DATA_PROFILE;
		}
	}

	private static boolean blinkCopy(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 start = player.position();
		Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
		Vec3 dest = null;
		for (double side : new double[] {1.0, -1.0}) {
			for (double d = BLINK; d >= 1.5 && dest == null; d -= 0.5) {
				Vec3 candidate = start.add(right.scale(side * d));
				if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
					dest = candidate;
				}
			}
			if (dest != null) {
				break;
			}
		}
		if (dest == null) {
			say(player, "no_room");
			return false;
		}
		Copy old = COPIES.remove(player.getUUID());
		if (old != null && old.level.getEntity(old.copy) != null) {
			pop(old.level, old.level.getEntity(old.copy));
		}
		Mannequin copy = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (copy != null) {
			copy.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(player.getGameProfile()));
			copy.addTag(COPY_TAG);
			copy.snapTo(start.x, start.y, start.z, player.getYRot(), player.getXRot());
			copy.setYHeadRot(player.getYHeadRot());
			copy.setYBodyRot(player.yBodyRot);
			COPIES.put(player.getUUID(), new Copy(level, copy.getUUID(), level.getGameTime() + COPY_TICKS));
			if (level.addFreshEntity(copy)) {
				lure(player, level, copy);
			} else {
				COPIES.remove(player.getUUID());
			}
		}
		MagicFx.blast(level, start.add(0.0, 1.0, 0.0), 1.2F, BAND_COLOR);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		MagicFx.blast(level, dest.add(0.0, 1.0, 0.0), 1.0F, BAND_COLOR);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	private static void lure(ServerPlayer owner, ServerLevel level, LivingEntity copy) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, PickKit.around(owner.position(), LURE_RANGE), m -> m.isAlive() && m.getTarget() == owner)) {
			mob.setTarget(copy);
		}
	}

	private static void pop(ServerLevel level, Entity entity) {
		Vec3 at = entity.position();
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 1.1F, BAND_COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ALLAY_DEATH, SoundSource.PLAYERS, 0.8F, 0.8F);
		entity.discard();
	}

	private static void copyTick(ServerPlayer player, ServerLevel level, long now) {
		Copy copy = COPIES.get(player.getUUID());
		if (copy == null) {
			return;
		}
		Entity e = copy.level.getEntity(copy.copy);
		if (!(e instanceof Mannequin mannequin) || !mannequin.isAlive()) {
			COPIES.remove(player.getUUID());
			return;
		}
		if (now >= copy.until || copy.level != level) {
			pop(copy.level, mannequin);
			COPIES.remove(player.getUUID());
			return;
		}
		if (now % 5 == 0) {
			lure(player, level, mannequin);
		}
	}

	private static boolean drag(ServerPlayer player) {
		ServerLevel level = player.level();
		Map<Integer, Long> hits = HITS.get(player.getUUID());
		long now = level.getGameTime();
		List<LivingEntity> victims = new ArrayList<>();
		if (hits != null) {
			hits.values().removeIf(t -> now - t > LATELY_TICKS);
			for (int id : hits.keySet()) {
				if (level.getEntity(id) instanceof LivingEntity e && e.isAlive() && e != player && e.distanceToSqr(player) <= DRAG_REACH * DRAG_REACH) {
					victims.add(e);
				}
			}
		}
		if (victims.isEmpty()) {
			say(player, "no_hits");
			return false;
		}
		DamageSource source = player.damageSources().playerAttack(player);
		int n = victims.size();
		for (int i = 0; i < n; i++) {
			LivingEntity e = victims.get(i);
			double a = i * Math.PI * 2.0 / n;
			Vec3 spot = player.position().add(Math.cos(a) * 1.6, 0.0, Math.sin(a) * 1.6);
			MagicFx.beam(level, e.position().add(0.0, 1.0, 0.0), player.position().add(0.0, 0.6, 0.0), 0.15F, BAND_COLOR, 6);
			if (GreatFoes.is(e)) {
				e.setDeltaMovement(e.getDeltaMovement().add(player.position().subtract(e.position()).normalize().scale(0.9)));
				e.syncVelocity = true;
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, true, false));
				continue;
			}
			e.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), e.getYRot(), e.getXRot(), false);
			e.setDeltaMovement(0.0, -0.4, 0.0);
			e.syncVelocity = true;
			e.hurtServer(level, source, DRAG_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, true, false));
		}
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), null, 2.6F, BAND_COLOR, 12);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.7F, 0.5F);
		Shakes.around(level, player.position(), 14.0, 0.7F);
		hits.clear();
		return true;
	}

	// ---------------------------------------------------------------- Last Footing

	private static void noteSafe(ServerPlayer player, ServerLevel level, long now) {
		Long hurt = LAST_HURT.get(player.getUUID());
		if (now % NOTE_EVERY != 0 || hurt != null && now - hurt < CALM_TICKS || !player.onGround() || player.isInWater() || player.isInLava() || player.isOnFire()
			|| player.getHealth() < player.getMaxHealth() * 0.5F) {
			return;
		}
		BlockState under = level.getBlockState(player.blockPosition().below());
		if (under.isAir() || !under.getFluidState().isEmpty()) {
			return;
		}
		SAFE.put(player.getUUID(), new Place(level.dimension(), player.position()));
	}

	private static void lastFooting(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!player.isAlive() || player.getHealth() > player.getMaxHealth() * SNAP_BELOW || !PickKit.ready(TABLE, player, SNAP)) {
			return;
		}
		Place safe = SAFE.get(player.getUUID());
		if (safe == null || !safe.level.equals(level.dimension()) || safe.at.distanceToSqr(player.position()) < SNAP_MIN_DISTANCE * SNAP_MIN_DISTANCE) {
			return;
		}
		PickKit.spend(TABLE, player, SNAP, SNAP_COOLDOWN);
		MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.4F, BAND_COLOR);
		player.teleportTo(level, safe.at.x, safe.at.y, safe.at.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SNAP_PROTECT, 1, true, true));
		MagicFx.blast(level, safe.at.add(0.0, 1.0, 0.0), 1.4F, BAND_COLOR);
		level.playSound(null, safe.at.x, safe.at.y, safe.at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
		say(player, "snapped");
	}

	// ---------------------------------------------------------------- Nothing Stops Me

	/** sneak + C in Collapse. True if it handled the key. */
	static boolean unstoppable(ServerPlayer player) {
		if (!Fate.isCorrupted(player)) {
			return false;
		}
		if (STOPS.containsKey(player.getUUID())) {
			return true;
		}
		if (!PickKit.ready(TABLE, player, STOP)) {
			say(player, "cooling");
			return true;
		}
		ServerLevel level = player.level();
		STOPS.put(player.getUUID(), new Stop(level.getGameTime() + STOP_TICKS, 0.0F));
		PickKit.spend(TABLE, player, STOP, STOP_COOLDOWN);
		AttributeInstance knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knock != null && !knock.hasModifier(STOP_KNOCK_ID)) {
			knock.addTransientModifier(new AttributeModifier(STOP_KNOCK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
		}
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), null, 3.0F, BAND_COLOR, 14);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.6F);
		say(player, "unstoppable");
		return true;
	}

	private static void endStop(ServerPlayer player) {
		STOPS.remove(player.getUUID());
		AttributeInstance knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knock != null) {
			knock.removeModifier(STOP_KNOCK_ID);
		}
		player.removeEffect(MobEffects.JUMP_BOOST);
	}

	private static void stopTick(ServerPlayer player, ServerLevel level, long now) {
		Stop stop = STOPS.get(player.getUUID());
		if (stop == null) {
			return;
		}
		if (now >= stop.until || !Fate.isCorrupted(player)) {
			endStop(player);
			return;
		}
		// no slowing: whatever would drag the player down is shrugged off
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeEffect(MobEffects.MINING_FATIGUE);
		player.setTicksFrozen(0);
		player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 10, LEAP_BOOST, true, false));
		Vec3 v = player.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0.0, v.z);
		double speed = flat.length();
		// soft walls give way
		if (speed > STEP_SPEED * 0.6 && griefing(level, player)) {
			Vec3 dir = flat.normalize();
			Vec3 front = player.position().add(dir.scale(player.getBbWidth() * 0.5 + 0.5));
			for (int dy = 0; dy < 2; dy++) {
				BlockPos pos = BlockPos.containing(front.x, player.getY() + 0.1 + dy, front.z);
				if (BlockCluster.takeable(level, pos, WALL_HARDNESS, player)) {
					level.destroyBlock(pos, true, player);
				}
			}
		}
		// steps that shake the ground
		if (player.onGround() && speed > STEP_SPEED) {
			if (now % STEP_SHAKE_EVERY == 0) {
				Shakes.around(level, player.position(), 14.0, STEP_SHAKE);
			}
			if (now % STEP_CRACK_EVERY == 0) {
				GroundCracks.crack(level, player.blockPosition().below(), level.getRandom(), 3);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.25F, 0.6F);
			}
		}
		// the leap: the fall is measured, and the landing digs in
		float fall = Math.max(stop.maxFall, (float)player.fallDistance);
		if (!player.onGround()) {
			STOPS.put(player.getUUID(), new Stop(stop.until, fall));
		} else if (stop.maxFall > 0.0F) {
			STOPS.put(player.getUUID(), new Stop(stop.until, 0.0F));
			if (stop.maxFall >= LEAP_MIN_FALL) {
				land(player, level, stop.maxFall);
			}
		}
	}

	private static void land(ServerPlayer player, ServerLevel level, float fall) {
		Vec3 at = player.position();
		double radius = Math.min(LEAP_RADIUS_MAX, 2.5 + fall * 0.35);
		float damage = Math.min(LEAP_DAMAGE_MAX, 3.0F + fall * 0.5F);
		DamageSource source = player.damageSources().playerAttack(player);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, radius), c -> PickKit.foe(player, c))) {
			if (e.distanceToSqr(at) <= radius * radius) {
				e.hurtServer(level, source, damage);
				Vec3 out = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
				e.setDeltaMovement(e.getDeltaMovement().add(out.lengthSqr() > 1.0E-4 ? out.normalize().scale(0.7) : Vec3.ZERO).add(0.0, 0.5, 0.0));
				e.syncVelocity = true;
			}
		}
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), null, (float)radius, BAND_COLOR, 14);
		MagicFx.blast(level, at.add(0.0, 0.5, 0.0), (float)Math.min(4.0, radius * 0.6), BAND_COLOR);
		GroundCracks.crack(level, player.blockPosition().below(), level.getRandom(), Mth.clamp((int)(fall * 0.8), 3, 10));
		Shakes.around(level, at, 28.0, Math.min(2.2F, 0.6F + fall * 0.08F));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		if (!griefing(level, player)) {
			return;
		}
		int clusters = Mth.clamp(1 + (int)(fall / 6.0F), 1, CRATER_MAX_CLUSTERS);
		int pieces = Math.min(BlockCluster.MAX_PIECES, (int)(LEAP_PIECES_BASE + fall / 2.0F));
		for (int i = 0; i < clusters; i++) {
			double a = i * Math.PI * 2.0 / clusters;
			double off = i == 0 ? 0.0 : Math.min(3.0, radius * 0.5);
			BlockPos centre = BlockPos.containing(at.x + Math.cos(a) * off, at.y - 1.0, at.z + Math.sin(a) * off);
			List<BlockPos> taken = BlockCluster.crater(level, centre, pieces, CRATER_HARDNESS, player);
			if (!taken.isEmpty()) {
				BlockCluster.of(level, taken, new Vec3(Math.cos(a) * 0.3, 0.9, Math.sin(a) * 0.3), player);
			}
		}
	}

	// ---------------------------------------------------------------- every tick

	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive()) {
			if (BANDED.containsKey(player.getUUID()) || STOPS.containsKey(player.getUUID())) {
				forget(player);
			}
			return;
		}
		noteSafe(player, level, now);
		bandsTick(player, level, now);
		stopTick(player, level, now);
		copyTick(player, level, now);
	}
}
