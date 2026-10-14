package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Web's raw strength (0.10.21): the spider's body, and the webs that hold things down.
 * <ul>
 * <li><b>Spider physique</b>: always on, harder punches, quicker feet, higher jumps, steadier footing and softer
 * landings, growing with every ability unlocked ({@link #physique}).</li>
 * <li><b>Impact web</b>: a Web Shot charged past {@link #PIN_CHARGE} pins what it hits to the wall behind it (or the
 * floor) for a few seconds; anything pinned takes {@link #PINNED_DAMAGE} times the damage.</li>
 * <li><b>Taser web</b>: V while the Yank web holds something sends a shock down the line ({@link #shock}).</li>
 * <li><b>Web bomb</b>: Sneak + tap V lobs a ball that cocoons everything around where it bursts.</li>
 * <li><b>Webbed fists</b>: Sneak + Z coats your fists for a while; every hit slows harder, the third cocoons.</li>
 * </ul>
 * A cocoon holds a creature where it is (and a player all but still) for a few seconds.
 */
public final class WebStrength {
	/** Spider physique at every ability unlocked (scaled down before: a fresh awakening has a fifth of it). */
	public static final double PHYSIQUE_DAMAGE = 3.0;
	public static final double PHYSIQUE_SPEED = 0.15;
	public static final double PHYSIQUE_JUMP = 0.25;
	public static final double PHYSIQUE_KNOCKBACK_RESIST = 0.4;
	/** Fall damage still always applies; a spider just lands softer (this fraction less). */
	public static final double PHYSIQUE_FALL = 0.3;

	/** A Web Shot charged at least this much (0-1) is an impact web. */
	public static final float PIN_CHARGE = 0.5F;
	/** How long an impact web pins, at {@link #PIN_CHARGE} and at a full charge (ticks). */
	public static final int PIN_TICKS_MIN = 60, PIN_TICKS_MAX = 120;
	/** How far the shot carries what it hits back to a wall. */
	public static final double PIN_WALL_REACH = 3.0;
	/** Damage taken while pinned is multiplied by this. */
	public static final float PINNED_DAMAGE = 1.5F;

	/** Taser web: each shock's damage, stun, and the wait between shocks (ticks). */
	public static final float SHOCK_DAMAGE = 4.0F;
	public static final int SHOCK_STUN_TICKS = 15;
	public static final int SHOCK_COOLDOWN = 20;

	/** Web bomb: the cocoon radius where it bursts, how long the cocoon holds, and the V cooldown after a throw. */
	public static final double BOMB_RADIUS = 4.0;
	public static final int BOMB_COCOON_TICKS = 80;
	public static final int BOMB_COOLDOWN = 120;
	public static final float BOMB_SPEED = 1.2F;

	/** Webbed fists: how long they last, the cooldown from when they're coated, and the hit that cocoons. */
	public static final int FISTS_TICKS = 200;
	public static final int FISTS_COOLDOWN = 600;
	public static final int FISTS_COCOON_HIT = 3;
	public static final int FISTS_COCOON_TICKS = 60;
	/** Each stuck hit's Slowness lasts this long; a target's stack of hits is forgotten after it. */
	public static final int FISTS_SLOW_TICKS = 60;

	/** Slowness VII: nobody walks through that. */
	private static final int COCOON_SLOWNESS = 6;

	private static final Identifier DAMAGE_ID = JaymosPowers.id("web_physique_damage");
	private static final Identifier SPEED_ID = JaymosPowers.id("web_physique_speed");
	private static final Identifier JUMP_ID = JaymosPowers.id("web_physique_jump");
	private static final Identifier STEADY_ID = JaymosPowers.id("web_physique_steady");
	private static final Identifier FALL_ID = JaymosPowers.id("web_physique_fall");

	/** Something held in place: where (null for a player: their movement is their own), until when, and pinned or cocooned. */
	private record Bind(ResourceKey<Level> dimension, Vec3 at, long until, boolean pinned) {
	}

	private record Stuck(UUID by, int hits, long until) {
	}

	private static final Map<UUID, Bind> BINDS = new HashMap<>();
	private static final Map<UUID, Stuck> STUCK = new HashMap<>();
	private static final Map<UUID, Long> FISTS_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> FISTS_READY = new HashMap<>();
	private static final Map<UUID, Long> SHOCK_READY = new HashMap<>();
	private static long clock;

	private WebStrength() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebStrength::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BINDS.clear();
			STUCK.clear();
			FISTS_UNTIL.clear();
			FISTS_READY.clear();
			SHOCK_READY.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && damageTaken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer player
				&& source.getDirectEntity() == player && entity != player && fistsOn(player)) {
				stickyHit(player, entity);
			}
		});
	}

	// ---- spider physique --------------------------------------------------------------------------------------------

	/** Puts the physique on (or brings it up to date with what's unlocked); called every second from the power's tick. */
	static void physique(ServerPlayer player) {
		double share = (Mastery.unlocked(player) + 1) / (double)(Mastery.ALL + 1);
		modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, PHYSIQUE_DAMAGE * share, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, PHYSIQUE_SPEED * share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, PHYSIQUE_JUMP * share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID, PHYSIQUE_KNOCKBACK_RESIST * share, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, -PHYSIQUE_FALL * share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	static void onLost(ServerPlayer player) {
		remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID);
		remove(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID);
		FISTS_UNTIL.remove(player.getUUID());
	}

	private static void modifier(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (current == null || current.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(Player player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	// ---- pins and cocoons -------------------------------------------------------------------------------------------

	/** The damage {@code entity} is about to take, more if it's pinned (from {@code WebLivingEntityMixin}). */
	public static float modifyDamage(LivingEntity entity, float damage) {
		if (BINDS.isEmpty()) {
			return damage;
		}
		Bind bind = BINDS.get(entity.getUUID());
		return bind != null && bind.pinned() && clock < bind.until() ? damage * PINNED_DAMAGE : damage;
	}

	/** An impact web hit {@code target}, flying along {@code motion}: carried back to the wall behind it (if one's close) and pinned. */
	static void pin(ServerLevel level, LivingEntity target, Vec3 motion, float charge, boolean dark) {
		Vec3 at = target.position();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z);
		if (flat.lengthSqr() > 1.0E-4 && !(target instanceof Player)) {
			Vec3 dir = flat.normalize();
			Vec3 from = target.getBoundingBox().getCenter();
			BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(PIN_WALL_REACH + target.getBbWidth() * 0.5)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
			if (hit.getType() == HitResult.Type.BLOCK) {
				Vec3 wall = hit.getLocation().subtract(dir.scale(target.getBbWidth() * 0.5 + 0.05));
				Vec3 moved = new Vec3(wall.x, at.y, wall.z);
				if (level.noCollision(target, target.getBoundingBox().move(moved.subtract(at)))) {
					at = moved;
					target.teleportTo(at.x, at.y, at.z);
				}
			}
		}
		int ticks = net.minecraft.util.Mth.lerpInt((charge - PIN_CHARGE) / (1.0F - PIN_CHARGE), PIN_TICKS_MIN, PIN_TICKS_MAX);
		bind(level, target, at, ticks, true, dark);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Spun up where it stands: it can't move or jump for {@code ticks}. */
	static void cocoon(ServerLevel level, LivingEntity target, int ticks, boolean dark) {
		bind(level, target, target.position(), ticks, false, dark);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	private static void bind(ServerLevel level, LivingEntity target, Vec3 at, int ticks, boolean pinned, boolean dark) {
		WebTraps.ensnare(level, target, COCOON_SLOWNESS, ticks, dark);
		target.setDeltaMovement(Vec3.ZERO);
		target.syncVelocity = true;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		// a stronger hold replaces a weaker; a pin stays a pin
		Bind old = BINDS.get(target.getUUID());
		boolean wasPinned = old != null && old.pinned() && clock < old.until();
		long until = Math.max(clock + ticks, old == null ? 0L : old.until());
		BINDS.put(target.getUUID(), new Bind(level.dimension(), target instanceof Player ? null : (wasPinned && !pinned ? old.at() : at), until, pinned || wasPinned));
		level.sendParticles(dark ? WebSymbiote.dust(1.4F) : ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			24, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5, target.getBbWidth() * 0.5, 0.02);
	}

	private static void tick(MinecraftServer server) {
		clock++;
		if (!BINDS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Bind>> it = BINDS.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Bind> entry = it.next();
				Bind bind = entry.getValue();
				ServerLevel level = server.getLevel(bind.dimension());
				Entity entity = level == null ? null : level.getEntity(entry.getKey());
				if (clock >= bind.until() || !(entity instanceof LivingEntity living) || !living.isAlive()) {
					it.remove();
					continue;
				}
				if (bind.at() != null) {
					// held fast: wherever it was knocked or tried to walk, it's back
					if (living.position().distanceToSqr(bind.at()) > 0.0025) {
						living.teleportTo(bind.at().x, bind.at().y, bind.at().z);
					}
					living.setDeltaMovement(Vec3.ZERO);
					if (living instanceof Mob mob) {
						mob.getNavigation().stop();
					}
				}
				if (clock % 10 == 0) {
					level.sendParticles(ParticleTypes.ITEM_COBWEB, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
						2, living.getBbWidth() * 0.4, living.getBbHeight() * 0.4, living.getBbWidth() * 0.4, 0.0);
				}
			}
		}
		if (!STUCK.isEmpty()) {
			STUCK.values().removeIf(stuck -> clock >= stuck.until());
		}
		if (!FISTS_UNTIL.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = FISTS_UNTIL.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Long> entry = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || clock >= entry.getValue() || !Powers.has(player, WebPower.INSTANCE)) {
					it.remove();
					if (player != null) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_fists_off").withStyle(net.minecraft.ChatFormatting.GRAY));
					}
				} else if (clock % 5 == 0) {
					Vec3 hand = player.position().add(player.getViewVector(1.0F).scale(0.4)).add(0.0, player.getBbHeight() * 0.55, 0.0);
					player.level().sendParticles(ParticleTypes.ITEM_COBWEB, hand.x, hand.y, hand.z, 1, 0.2, 0.1, 0.2, 0.0);
				}
			}
		}
	}

	// ---- taser web --------------------------------------------------------------------------------------------------

	/** V with the Yank web on {@code target}: a shock down the line. False if it isn't ready yet. */
	static boolean shock(ServerPlayer player, Entity target) {
		Long ready = SHOCK_READY.get(player.getUUID());
		if (ready != null && clock < ready) {
			return false;
		}
		SHOCK_READY.put(player.getUUID(), clock + SHOCK_COOLDOWN);
		ServerLevel level = player.level();
		Holder<net.minecraft.world.damagesource.DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.LIGHTNING_BOLT);
		target.hurtServer(level, new DamageSource(type, player), SHOCK_DAMAGE);
		if (target instanceof LivingEntity living && living.isAlive()) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHOCK_STUN_TICKS, COCOON_SLOWNESS));
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SHOCK_STUN_TICKS * 2, 1));
			if (living instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
		}
		// sparks crawl down the line
		Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		Vec3 end = target.getBoundingBox().getCenter();
		for (int i = 1; i <= 8; i++) {
			Vec3 p = hand.lerp(end, i / 8.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.05);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.4, target.getBbWidth() * 0.4, 0.2);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 1.0F, 1.6F);
		return true;
	}

	// ---- webbed fists -----------------------------------------------------------------------------------------------

	static boolean fistsOn(Player player) {
		Long until = FISTS_UNTIL.get(player.getUUID());
		return until != null && clock < until;
	}

	/** Sneak + Z: coat the fists in web. */
	static void fists(ServerPlayer player) {
		Long ready = FISTS_READY.get(player.getUUID());
		if (fistsOn(player) || ready != null && clock < ready) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 2.0F);
			return;
		}
		FISTS_UNTIL.put(player.getUUID(), clock + FISTS_TICKS);
		FISTS_READY.put(player.getUUID(), clock + FISTS_COOLDOWN);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_fists_on").withStyle(net.minecraft.ChatFormatting.WHITE));
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 1.3F);
		player.level().sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
	}

	/** A punch with webbed fists sticks: slower with each hit, cocooned on the third. */
	private static void stickyHit(ServerPlayer player, LivingEntity target) {
		if (!target.isAlive() || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		Stuck old = STUCK.get(target.getUUID());
		int hits = (old != null && old.by().equals(player.getUUID()) ? old.hits() : 0) + 1;
		if (hits >= FISTS_COCOON_HIT) {
			STUCK.remove(target.getUUID());
			cocoon(level, target, FISTS_COCOON_TICKS, false);
			return;
		}
		STUCK.put(target.getUUID(), new Stuck(player.getUUID(), hits, clock + FISTS_SLOW_TICKS));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FISTS_SLOW_TICKS, hits));
		level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			6 * hits, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 0.9F + 0.2F * hits);
	}

	// ---- web bomb ---------------------------------------------------------------------------------------------------

	/** A web bomb burst at {@code center}: everything around it is cocooned. */
	static void bomb(ServerLevel level, Vec3 center, @org.jspecify.annotations.Nullable Entity owner, boolean dark) {
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(center, center).inflate(BOMB_RADIUS);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			if (living.distanceToSqr(center) <= BOMB_RADIUS * BOMB_RADIUS) {
				cocoon(level, living, BOMB_COCOON_TICKS, dark);
			}
		}
		WebTraps.scatter(level, center, BOMB_RADIUS, 10, dark);
		level.sendParticles(dark ? WebSymbiote.dust(1.6F) : ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 80, BOMB_RADIUS * 0.4, 0.6, BOMB_RADIUS * 0.4, 0.15);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 20, BOMB_RADIUS * 0.3, 0.3, BOMB_RADIUS * 0.3, 0.08);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		com.jaymospowers.core.Shakes.around(level, center, 10.0, 0.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
	}
}
