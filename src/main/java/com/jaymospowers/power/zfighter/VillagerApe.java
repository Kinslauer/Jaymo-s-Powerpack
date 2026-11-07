package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

/**
 * (Jaymo's Kingdoms' villagers born Warrior-born) A villager who keeps its tail wrapped may still turn under a full moon:
 * the very wild Great Ape a Warrior-born with no Z Fighter power becomes ({@link ZFighterLineage}'s, its body, size,
 * stomps, berserk quarry, roaming, running from blows, crippled legs; drawn by the client's ZFighterApe on any humanoid).
 * Rolled once a full-moon night ({@link ZFighterLineage#WILD_ROLL_EVERY}) for each such villager out under the open sky
 * at night, at {@link #TURN_CHANCE}. It ends as the player's does (dawn, out of the moonlight a while, or
 * {@link ZFighterLineage#WILD_MAX_TICKS}), and it's stopped the same way: blows to the legs bring it down, and a blade
 * while it lies there cuts the tail off. Then its tail is cut for good ({@link #TAIL_CUT}, {@link #CUT_IN_RAMPAGE}); it
 * never turns again. A villager whose tail was cut at birth never turns at all.
 *
 * <p>Read and written by entity tag, so Powers needs no Kingdoms classes: Kingdoms marks the heritage and the tail
 * ({@link #WARRIOR}, {@link #TAIL_WRAPPED}, {@link #TAIL_CUT}) and its villagers' talk reads {@link #TURNED} and
 * {@link #CUT_IN_RAMPAGE}.
 *
 * <p>A villager's own steps are its AI's, so while raging the AI is kept out of it (its walk target and path cleared each
 * tick) and the ape is steered here, straight at its quarry or its goal, jumping when it runs into something it can't step
 * over.
 */
final class VillagerApe {
	/** The chance a wrapped Warrior-born villager turns, rolled once a full-moon night. */
	static final float TURN_CHANCE = 0.1F;
	/** How often villagers are looked over for one to turn (ticks). */
	static final int SCAN_EVERY = 20;
	/** At full size it walks this fast (blocks a tick) and flat out from a blow this much faster. */
	static final double WALK = 0.32, FLEE_FACTOR = 1.5;
	/** Its jump when blocked (motion up), and how high it steps without one at full size (added to the villager's own). */
	static final double JUMP = 0.9, STEP = 5.0;
	/** Its fists, at full size (a villager has no attack of its own to scale). */
	static final float FIST = 14.0F;
	/** Health added while the ape. */
	static final double HEALTH = 80.0;
	/** Players this near hear what's become of it. */
	static final double TELL_RANGE = 96.0;

	/** Kingdoms' tags: a Warrior-born, its tail kept wrapped, its tail cut. */
	static final String WARRIOR = "jaymoskingdoms_heritage_warriorborn", TAIL_WRAPPED = "jaymoskingdoms_tail_wrapped",
		TAIL_CUT = "jaymoskingdoms_tail_cut";
	/** It has turned before; its tail was cut off in the rampage. */
	static final String TURNED = "jaymospowers_ape_turned", CUT_IN_RAMPAGE = "jaymospowers_ape_tail_cut";
	/** On it while it rages (saved with it, so one saved mid-rampage wakes as itself again). */
	static final String RAGING = "jaymospowers_villager_ape";

	/** When the moon was last rolled for it (game time). Saved. */
	static final AttachmentType<Long> ROLLED = AttachmentRegistry.create(JaymosPowers.id("villager_ape_rolled"),
		builder -> builder.persistent(Codec.LONG));

	private static final Identifier SCALE_ID = JaymosPowers.id("villager_ape_scale");
	private static final Identifier HEALTH_ID = JaymosPowers.id("villager_ape_health");
	private static final Identifier STEP_ID = JaymosPowers.id("villager_ape_step");
	private static final Identifier CRIPPLE_ID = JaymosPowers.id("villager_ape_crippled");
	private static final Identifier FALL_ID = JaymosPowers.id("villager_ape_fall");

	/** The raging villagers (and those shrinking back) and what's kept on each between ticks. */
	private record Ape(Mob mob, ZFighterPower.Live live) {
	}

	private static final Map<UUID, Ape> APES = new HashMap<>();

	private VillagerApe() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(VillagerApe::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> APES.clear());
		// saved while raging: it wakes as itself
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.entityTags().contains(RAGING) && !APES.containsKey(mob.getUUID())) {
				calm(mob);
			}
		});
	}

	/** It's raging as the Great Ape now (not shrinking back). */
	static boolean raging(Entity entity) {
		return !(entity instanceof Player) && Boolean.TRUE.equals(entity.getAttached(ZFighterLineage.GREAT_APE));
	}

	/** A Warrior-born villager whose tail is kept wrapped: the one kind the moon can take. */
	static boolean wrapped(Entity entity) {
		var tags = entity.entityTags();
		return !(entity instanceof Player) && tags.contains(WARRIOR) && tags.contains(TAIL_WRAPPED) && !tags.contains(TAIL_CUT);
	}

	private static boolean fullMoonNight(ServerLevel level) {
		return level.isDarkOutside() && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
	}

	private static boolean moonlit(Mob mob, ServerLevel level) {
		return fullMoonNight(level) && level.canSeeSky(BlockPos.containing(mob.getEyePosition())) || ZFighterFakeMoon.shines(level, mob);
	}

	/** (Andy, 2026-11-01) A false moon's light takes it ({@link ZFighterFakeMoon}), whatever the real moon is doing. */
	static void turnUnderFakeMoon(Mob mob, ServerLevel level) {
		if (APES.containsKey(mob.getUUID()) || !mob.isAlive()) {
			return;
		}
		turn(mob, level, level.getGameTime(), "message.jaymospowers.villager_ape_turn_fake_moon");
	}

	private static void tick(MinecraftServer server) {
		if (!APES.isEmpty()) {
			for (Ape ape : new ArrayList<>(APES.values())) {
				step(ape);
			}
		}
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (!fullMoonNight(level)) {
				continue;
			}
			long now = level.getGameTime();
			List<Mob> turning = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && mob.isAlive() && !APES.containsKey(mob.getUUID()) && wrapped(mob) && !mob.isBaby()
					&& level.canSeeSky(BlockPos.containing(mob.getEyePosition()))
					&& now - mob.getAttachedOrElse(ROLLED, Long.MIN_VALUE / 2) >= ZFighterLineage.WILD_ROLL_EVERY) {
					// the moon may or may not take it, once a night
					mob.setAttached(ROLLED, now);
					if (mob.getRandom().nextFloat() < TURN_CHANCE) {
						turning.add(mob);
					}
				}
			}
			for (Mob mob : turning) {
				turn(mob, level, now, "message.jaymospowers.villager_ape_turn");
			}
		}
	}

	/** The moon takes it: the tail tears loose and it swells into the wild Great Ape. */
	private static void turn(Mob mob, ServerLevel level, long now, String told) {
		ZFighterPower.Live live = new ZFighterPower.Live();
		live.wildFrom = now;
		live.apePeak = mob.getY();
		APES.put(mob.getUUID(), new Ape(mob, live));
		mob.stopRiding();
		if (mob.isSleeping()) {
			mob.stopSleeping();
		}
		mob.addTag(RAGING);
		mob.addTag(TURNED);
		mob.setAttached(ZFighterLineage.GREAT_APE, true);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, mob.getX(), mob.getY() + 1.0, mob.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.4F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
		ZFighterLineage.shake(mob, mob.position(), ZFighterLineage.APE_STEP_RANGE * 1.5, ZFighterLineage.APE_CHANGE_SHAKE);
		tell(mob, level, told, ChatFormatting.RED);
		body(mob, live);
	}

	/** Every tick: it grows (or shrinks back), rages, and is steered; the night, the sky or a blade ends it. */
	private static void step(Ape entry) {
		Mob mob = entry.mob();
		ZFighterPower.Live live = entry.live();
		if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			APES.remove(mob.getUUID());
			if (!mob.isRemoved() || mob.isAlive()) {
				calm(mob);
			}
			return;
		}
		long now = level.getGameTime();
		boolean ape = raging(mob);
		grow(mob, live, level, ape);
		if (!ape) {
			if (live.apeSize <= 0.0F) {
				APES.remove(mob.getUUID());
				calm(mob);
			}
			return;
		}
		live.moonless = moonlit(mob, level) ? 0 : live.moonless + 1;
		if (live.moonless >= ZFighterLineage.APE_MOONLESS || level.isBrightOutside() || now - live.wildFrom >= ZFighterLineage.WILD_MAX_TICKS) {
			end(mob, live, level);
			return;
		}
		// its AI has no say while it rages
		mob.getNavigation().stop();
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		mob.setTarget(null);
		if (ZFighterLineage.crippled(mob)) {
			// down on its back until its legs come back to it
			mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.0, 1.0, 0.0));
			if (now >= live.crippledUntil) {
				mob.removeAttached(ZFighterLineage.CRIPPLED);
				live.crippledUntil = -1L;
			}
			return;
		}
		if (ApeGrab.tick(mob, live, level, now)) {
			// (0.17.68) its grab has it: it stands and plays it out
			return;
		}
		berserk(mob, live, level, now);
		if (now % 3 == 0) {
			level.sendParticles(new DustParticleOptions(0xC03020, 2.5F), true, false, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 2,
				mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.0);
		}
		if (now % 200 == 0) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
		// a long drop lands as a stomp; every stride shakes the ground
		if (mob.onGround()) {
			if (live.apePeak - mob.getY() >= ZFighterLineage.APE_LANDING && live.apeSize >= 0.5F) {
				ZFighterLineage.footfall(mob, live, level);
				ZFighterLineage.footfall(mob, live, level);
			}
			live.apePeak = mob.getY();
		} else {
			live.apePeak = Math.max(live.apePeak, mob.getY());
		}
		if (mob.moveDist - live.apeStepAt >= ZFighterLineage.APE_STRIDE * Math.max(0.5F, live.apeSize)) {
			live.apeStepAt = mob.moveDist;
			if (mob.onGround() && live.apeSize >= 0.5F) {
				ZFighterLineage.footfall(mob, live, level);
			}
		}
		if (now % ZFighterLineage.APE_STOMP_EVERY == 0 && live.apeSize >= 0.5F) {
			ZFighterLineage.stomp(mob, level);
		}
		if (live.apeSize >= 0.5F) {
			// (0.19.39) walking into a tree pushes it over
			com.jaymospowers.core.GiantTrample.tick(mob, now);
		}
	}

	/** It swells into the ape (or shrinks back), a little every tick. */
	private static void grow(Mob mob, ZFighterPower.Live live, ServerLevel level, boolean ape) {
		float target = ape ? 1.0F : 0.0F;
		if (live.apeSize == target) {
			return;
		}
		live.apeSize = ape ? Math.min(1.0F, live.apeSize + 1.0F / ZFighterLineage.APE_GROW_TICKS)
			: Math.max(0.0F, live.apeSize - 1.0F / ZFighterLineage.APE_SHRINK_TICKS);
		body(mob, live);
		if (ape) {
			double width = mob.getBbWidth();
			level.sendParticles(new DustParticleOptions(0x6B3A1E, 3.0F), true, false, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 6,
				width * 0.4, mob.getBbHeight() * 0.35, width * 0.4, 0.0);
			if (level.getGameTime() % 10 == 0) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0F,
					0.5F + 0.3F * (1.0F - live.apeSize));
			}
			if (live.apeSize >= 1.0F) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.4F);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, mob.getX(), mob.getY() + 2.0, mob.getZ(), 3, width * 0.4, 1.0,
					width * 0.4, 0.0);
				mob.setHealth(mob.getMaxHealth());
				ZFighterLineage.shake(mob, mob.position(), 48.0, 2.8F);
			}
		}
	}

	/** Its body as it stands: the wild ape's size, health and stride, and no legs under it while crippled. */
	private static void body(Mob mob, ZFighterPower.Live live) {
		float t = Mth.clamp(live.apeSize, 0.0F, 1.0F);
		double grown = t * t * (3.0F - 2.0F * t);
		boolean ape = raging(mob);
		set(mob, Attributes.SCALE, SCALE_ID, ZFighterLineage.WILD_SCALE * grown, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mob, Attributes.MAX_HEALTH, HEALTH_ID, ape ? HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(mob, Attributes.STEP_HEIGHT, STEP_ID, STEP * grown, AttributeModifier.Operation.ADD_VALUE);
		// it lands its own leaps as stomps, unhurt
		set(mob, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, ape ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(mob, Attributes.MOVEMENT_SPEED, CRIPPLE_ID, ape && ZFighterLineage.crippled(mob) ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void set(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/**
	 * Berserk, as the player's wild ape is: the nearest living thing it can see and get at ({@link ZFighterLineage#quarry}),
	 * fists when it's in reach, roaming when there's nothing, running from a blow a while.
	 */
	private static void berserk(Mob mob, ZFighterPower.Live live, ServerLevel level, long now) {
		BlockPos goal = null;
		boolean fleeing = false;
		if (live.fleeUntil >= 0L) {
			if (now < live.fleeUntil && !ZFighterLineage.near(mob, mob.getAttached(ZFighterLineage.WILD_GOAL))) {
				steer(mob, live, Vec3.atBottomCenterOf(mob.getAttached(ZFighterLineage.WILD_GOAL)), true);
				return;
			}
			live.fleeUntil = -1L;
			live.fleeRestUntil = now + ZFighterLineage.FLEE_REST;
			mob.removeAttached(ZFighterLineage.WILD_GOAL);
			live.roamAt = 0L;
		}
		Entity known = live.berserkTarget >= 0 ? level.getEntity(live.berserkTarget) : null;
		LivingEntity target = known instanceof LivingEntity living && living.isAlive()
			&& living.distanceTo(mob) <= ZFighterLineage.BERSERK_RANGE * 1.5 ? living : null;
		if (target != null && now - live.berserkSince >= ZFighterLineage.BERSERK_GIVE_UP) {
			live.berserkIgnore = target.getId();
			live.berserkIgnoreUntil = now + ZFighterLineage.BERSERK_IGNORE;
			target = null;
		}
		if (target == null || now % ZFighterLineage.BERSERK_PICK_EVERY == 0) {
			LivingEntity nearest = null;
			double best = ZFighterLineage.BERSERK_RANGE * ZFighterLineage.BERSERK_RANGE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(ZFighterLineage.BERSERK_RANGE),
				e -> ZFighterLineage.quarry(mob, live, e, now))) {
				double d = e.distanceToSqr(mob);
				if (d < best) {
					best = d;
					nearest = e;
				}
			}
			if (nearest != null || target == null) {
				target = nearest;
			}
		}
		int id = target == null ? -1 : target.getId();
		if (id != live.berserkTarget || !mob.hasAttached(ZFighterLineage.BERSERK)) {
			live.berserkTarget = id;
			live.berserkSince = now;
			mob.setAttached(ZFighterLineage.BERSERK, id);
		}
		if (target == null) {
			ZFighterLineage.roam(mob, live, level, now);
			goal = mob.getAttached(ZFighterLineage.WILD_GOAL);
			if (goal != null && !ZFighterLineage.near(mob, goal)) {
				steer(mob, live, Vec3.atBottomCenterOf(goal), fleeing);
			}
			return;
		}
		mob.removeAttached(ZFighterLineage.WILD_GOAL);
		if (ZFighterLineage.inReach(mob, live, target)) {
			mob.lookAt(target, 30.0F, 30.0F);
			if (now >= live.grabReadyAt && ApeGrab.begin(mob, live, level, target, now)) {
				// (0.17.68) every so often it takes its quarry in hand instead
				live.berserkSince = now;
				return;
			}
			if (now >= live.berserkSwingAt) {
				live.berserkSwingAt = now + ZFighterLineage.BERSERK_SWING_EVERY;
				live.berserkSince = now;
				mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				if (target.hurtServer(level, ZFighterLineage.blow(level, mob), FIST * Math.max(0.25F, live.apeSize))) {
					Vec3 away = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0).normalize();
					target.push(away.x * 0.8, 0.4, away.z * 0.8);
				}
			}
		} else {
			steer(mob, live, target.position(), false);
		}
	}

	/** Walks it straight at {@code to}, facing that way, jumping when it runs into something too tall to step over. */
	private static void steer(Mob mob, ZFighterPower.Live live, Vec3 to, boolean flat) {
		Vec3 along = to.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (along.lengthSqr() < 1.0E-4) {
			return;
		}
		double speed = WALK * Math.max(0.3F, live.apeSize) * (flat ? FLEE_FACTOR : 1.0);
		Vec3 v = along.normalize().scale(speed);
		double up = mob.getDeltaMovement().y;
		if (mob.horizontalCollision && mob.onGround()) {
			up = JUMP * Math.max(0.5F, live.apeSize);
		}
		mob.setDeltaMovement(v.x, up, v.z);
		float yaw = (float)(Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
	}

	/**
	 * A blow on it: low ones are on its legs, and enough of them (or one hard enough) bring it down; while it lies there,
	 * a blade cuts its tail off for good. Hurt while standing, it runs from the blow a while.
	 */
	static void hurt(Mob mob, DamageSource source, float taken) {
		Ape ape = APES.get(mob.getUUID());
		if (ape == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		ZFighterPower.Live live = ape.live();
		long now = level.getGameTime();
		if (ZFighterLineage.crippled(mob)) {
			if (source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by && com.jaymospowers.core.Dismember.blade(by.getMainHandItem())) {
				cutTail(mob, live, level);
			}
			return;
		}
		if (taken > 0.0F) {
			ZFighterLineage.flee(mob, live, source, now);
		}
		Vec3 from = source.getSourcePosition();
		if (now < live.crippleSafeUntil || from == null || from.y > mob.getY() + mob.getBbHeight() * ZFighterLineage.LEG_SHARE || live.apeSize < 0.5F) {
			return;
		}
		live.legHits++;
		if (taken >= ZFighterLineage.WILD_CRIPPLE_BLOW || live.legHits >= ZFighterLineage.WILD_CRIPPLE_HITS) {
			live.legHits = 0;
			live.crippledUntil = now + ZFighterLineage.WILD_CRIPPLED_TICKS;
			live.crippleSafeUntil = live.crippledUntil + ZFighterLineage.CRIPPLE_SAFE_TICKS;
			live.fleeUntil = -1L;
			mob.removeAttached(ZFighterLineage.WILD_GOAL);
			mob.setAttached(ZFighterLineage.CRIPPLED, true);
			body(mob, live);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 4.0F, 0.4F);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.4F);
			ZFighterLineage.shake(mob, mob.position(), ZFighterLineage.APE_STEP_RANGE * 1.5, ZFighterLineage.APE_CHANGE_SHAKE);
			tell(mob, level, "message.jaymospowers.villager_ape_crippled", ChatFormatting.GOLD);
		}
	}

	/** The tail's off: it shrinks back, and it's cut for good (it never turns again). */
	private static void cutTail(Mob mob, ZFighterPower.Live live, ServerLevel level) {
		mob.removeTag(TAIL_WRAPPED);
		mob.addTag(TAIL_CUT);
		mob.addTag(CUT_IN_RAMPAGE);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 4.0F, 0.4F);
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(0x8A0A0A, 3.0F), true, false, mob.getX(), mob.getY() + 1.0, mob.getZ(), 40, mob.getBbWidth() * 0.4,
			0.5, mob.getBbWidth() * 0.4, 0.0);
		tell(mob, level, "message.jaymospowers.villager_ape_cut", ChatFormatting.GOLD);
		end(mob, live, level);
	}

	/** The rage is over: it shrinks back over the next ticks ({@link #step}), then is itself again. */
	private static void end(Mob mob, ZFighterPower.Live live, ServerLevel level) {
		mob.removeAttached(ZFighterLineage.GREAT_APE);
		mob.removeAttached(ZFighterLineage.CRIPPLED);
		mob.removeAttached(ZFighterLineage.BERSERK);
		mob.removeAttached(ZFighterLineage.WILD_GOAL);
		live.crippledUntil = -1L;
		live.fleeUntil = -1L;
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 1.0, 1.5, 1.0, 0.05);
		body(mob, live);
	}

	/** Itself again: its size, its health, the marks of the rage taken off. */
	private static void calm(Mob mob) {
		mob.removeTag(RAGING);
		mob.removeAttached(ZFighterLineage.GREAT_APE);
		mob.removeAttached(ZFighterLineage.CRIPPLED);
		mob.removeAttached(ZFighterLineage.BERSERK);
		mob.removeAttached(ZFighterLineage.WILD_GOAL);
		mob.removeAttached(ApeGrab.GRAB);
		ZFighterPower.Live none = new ZFighterPower.Live();
		body(mob, none);
	}

	/** Every player near hears of it (named, so they know who it was). */
	private static void tell(Mob mob, ServerLevel level, String key, ChatFormatting color) {
		Component line = Component.translatable(key, mob.getDisplayName()).withStyle(color);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(mob) <= TELL_RANGE * TELL_RANGE) {
				player.sendSystemMessage(line);
			}
		}
	}
}
