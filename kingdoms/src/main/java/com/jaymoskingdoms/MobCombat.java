package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Better Mob Combat, our own way: when Better Combat is loaded, humanoid melee mobs (our fighting {@link Folk} first,
 * and armed zombies, vindicators, wither skeletons and the like) fight with their weapon's Better Combat moves instead of
 * vanilla's poke. Each swing is that weapon's next attack in its combo: a wind-up (its upswing), then one blow that
 * strikes every foe in the attack's arc and reach, at the attack's damage multiplier, with its swing sound; the mob
 * slows as it swings, as a player does. Watchers' clients play the attack's animation on the mob
 * ({@code MobSwing}, client {@code MobAnimations}), when Player Animation Library is there to read it.
 * <p>
 * Fighting folk also <b>sidestep</b>: a player's blow that would land may be dodged (a hop to the side, no damage), now
 * and then, with a cooldown.
 * <p>
 * Everything is off without Better Combat ({@link #ENABLED}); its classes are only touched in
 * {@link BetterCombatBridge}. The vanilla goal hands its attacks over in {@code MeleeAttackGoalMixin}. Piglins (whose
 * brains attack without a goal) keep vanilla's blow.
 */
public final class MobCombat {
	/** Better Combat's mod id; nothing here runs without it. */
	public static final String BETTER_COMBAT = "bettercombat";
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded(BETTER_COMBAT);

	/** A mob's reach before its weapon's bonus (a player's is 3). */
	static final double BASE_REACH = 3.0;
	/** A swing starts only when the target is within this share of the reach (so the blow lands as it steps in). */
	static final double START_REACH_SHARE = 0.9;
	/** Mobs swing this much slower than a player with the same weapon (time to see it coming). */
	static final double MOB_TEMPO = 1.35;
	/** The shortest and longest swing (ticks). */
	static final int MIN_SWING_TICKS = 10, MAX_SWING_TICKS = 40;
	/** The blow lands no sooner than this into the swing (ticks). */
	static final int MIN_UPSWING_TICKS = 4;
	/** A pause between swings (ticks), on top of the swing itself. */
	static final int RECOVERY_TICKS = 4;
	/** The combo starts over after this long without a swing (ticks). */
	static final int COMBO_RESET_TICKS = 40;
	/** Half-width of the arc for a stab (forward box) and for an overhead (vertical plane), in degrees. */
	static final double STAB_HALF_ANGLE = 18.0, OVERHEAD_HALF_ANGLE = 28.0;
	/** Most foes one swing can strike. */
	static final int MAX_TARGETS = 5;
	/** A player's base attack speed (attacks a second), before the weapon's modifiers. */
	static final double BASE_ATTACK_SPEED = 4.0;

	/** The sidestep: chance a player's blow is dodged, by role (guards, archers, leaders, barbarians). */
	static final float DODGE_GUARD = 0.15F, DODGE_ARCHER = 0.10F, DODGE_LEADER = 0.25F, DODGE_BARBARIAN = 0.10F;
	/** The sidestep's push (blocks a tick, sideways), a little back, and up; and the wait before another (ticks). */
	static final double DODGE_SIDE = 0.55, DODGE_BACK = 0.2, DODGE_UP = 0.22;
	static final int DODGE_COOLDOWN = 60;

	private static final Identifier COMBO_DAMAGE = JaymosKingdoms.id("combo_damage");
	private static final Identifier SWING_SLOW = JaymosKingdoms.id("swing_slow");

	/** A weapon's moves, read from Better Combat ({@link BetterCombatBridge}) so nothing else touches its classes. */
	public record Moves(double reach, boolean twoHanded, List<Move> attacks) {
	}

	/**
	 * One attack of a combo: its hitbox ({@code shape}: 0 forward box, 1 vertical plane, 2 horizontal plane), arc
	 * ({@code angle}, degrees), damage multiplier, reach multiplier, upswing (share of the swing before the blow),
	 * movement while swinging, its animation's id and its swing sound (null for none).
	 */
	public record Move(int shape, double angle, double damage, double reachScale, double upswing, double movement,
		@Nullable String animation, @Nullable String sound, float volume, float pitch, float pitchSpread,
		boolean needsShield, boolean needsEmptyOffhand) {
	}

	/** A mob's swing in progress, and its combo. */
	private static final class Swing {
		@Nullable LivingEntity target;
		@Nullable Move move;
		long hitAt = -1, endsAt = -1, lastSwing = -1000;
		int combo;
	}

	private static final Map<Mob, Swing> SWINGS = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> DODGED = new WeakHashMap<>();

	/** Server → watchers: play {@code animation} on the mob, stretched over {@code length} ticks. */
	public record MobSwing(int entityId, String animation, int length, float upswing) implements CustomPacketPayload {
		public static final Type<MobSwing> TYPE = new Type<>(JaymosKingdoms.id("mob_swing"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MobSwing> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeUtf(p.animation);
				buf.writeVarInt(p.length);
				buf.writeFloat(p.upswing);
			},
			buf -> new MobSwing(buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readFloat()));

		@Override
		public Type<MobSwing> type() {
			return TYPE;
		}
	}

	private MobCombat() {
	}

	/** The packet is always registered (both sides agree with or without Better Combat); the rest only with it. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MobSwing.TYPE, MobSwing.CODEC);
		if (!ENABLED) {
			return;
		}
		JaymosKingdoms.LOGGER.info("Better Combat is here: humanoid mobs fight with its weapon moves");
		ServerTickEvents.END_SERVER_TICK.register(MobCombat::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MobCombat::allowDamage);
	}

	/** Whether this mob fights Better Combat's way: a humanoid with a weapon Better Combat knows. */
	public static boolean handles(Mob mob) {
		if (!ENABLED || !humanoid(mob)) {
			return false;
		}
		if (mob instanceof Folk folk && (!folk.combatant() || folk.role() == Folk.ARCHER)) {
			return false;
		}
		return BetterCombatBridge.moves(mob.getMainHandItem()) != null;
	}

	/** Humanoid melee mobs that can carry a weapon: our folk, zombies (and their kin), illagers, skeletons, piglins. */
	static boolean humanoid(Mob mob) {
		return mob instanceof Folk || mob instanceof Zombie || mob instanceof AbstractIllager || mob instanceof AbstractSkeleton
			|| mob instanceof AbstractPiglin;
	}

	/**
	 * Called every tick its melee goal would attack ({@code MeleeAttackGoal.checkAndPerformAttack}, which then does
	 * nothing more): starts the next swing of the combo when the target is in reach and the last swing is over.
	 */
	public static void attackTick(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Moves moves = BetterCombatBridge.moves(mob.getMainHandItem());
		if (moves == null || moves.attacks().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Swing swing = SWINGS.computeIfAbsent(mob, m -> new Swing());
		if (now < swing.endsAt + RECOVERY_TICKS || swing.hitAt >= 0) {
			return;
		}
		if (now - swing.endsAt > COMBO_RESET_TICKS) {
			swing.combo = 0;
		}
		Move move = pick(mob, moves, swing.combo);
		double reach = moves.reach() * move.reachScale();
		if (gap(mob, target) > reach * START_REACH_SHARE || !mob.getSensing().hasLineOfSight(target)) {
			return;
		}
		int length = swingTicks(mob);
		int upswing = Math.max(MIN_UPSWING_TICKS, (int) Math.round(length * move.upswing()));
		swing.target = target;
		swing.move = move;
		swing.hitAt = now + Math.min(upswing, length - 1);
		swing.endsAt = now + length;
		swing.lastSwing = now;
		swing.combo++;
		// slowed while swinging, as the attack says
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && move.movement() < 1.0) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SWING_SLOW, move.movement() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		// watchers see the swing: Better Combat's animation where their client can play it, vanilla's arm swing otherwise
		if (move.animation() != null) {
			MobSwing packet = new MobSwing(mob.getId(), move.animation(), length, (float) move.upswing());
			for (ServerPlayer player : PlayerLookup.tracking(mob)) {
				if (ServerPlayNetworking.canSend(player, MobSwing.TYPE)) {
					ServerPlayNetworking.send(player, packet);
				}
			}
		} else {
			mob.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
		mob.setAggressive(true);
	}

	/** The attack the combo is on, skipping those whose conditions this mob doesn't meet. */
	private static Move pick(Mob mob, Moves moves, int combo) {
		List<Move> usable = new ArrayList<>();
		boolean shield = !mob.getOffhandItem().isEmpty() && mob.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
		boolean emptyOffhand = mob.getOffhandItem().isEmpty();
		for (Move move : moves.attacks()) {
			if ((!move.needsShield() || shield) && (!move.needsEmptyOffhand() || emptyOffhand)) {
				usable.add(move);
			}
		}
		if (usable.isEmpty()) {
			usable = moves.attacks();
		}
		return usable.get(Math.floorMod(combo, usable.size()));
	}

	/** A swing's length (ticks): the weapon's attack speed, slowed for mobs. */
	static int swingTicks(Mob mob) {
		double attacksPerSecond = attackSpeed(mob);
		int ticks = (int) Math.round(20.0 / Math.max(0.25, attacksPerSecond) * MOB_TEMPO);
		return Mth.clamp(ticks, MIN_SWING_TICKS, MAX_SWING_TICKS);
	}

	/** Its attack speed: the attribute if it has one (folk don't), or a player's base with the weapon's modifiers. */
	static double attackSpeed(Mob mob) {
		AttributeInstance attribute = mob.getAttribute(Attributes.ATTACK_SPEED);
		if (attribute != null) {
			return attribute.getValue();
		}
		double[] add = {BASE_ATTACK_SPEED, 0.0, 1.0};
		ItemStack weapon = mob.getMainHandItem();
		weapon.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.forEach(EquipmentSlot.MAINHAND, (holder, modifier) -> {
				if (holder.is(Attributes.ATTACK_SPEED)) {
					switch (modifier.operation()) {
						case ADD_VALUE -> add[0] += modifier.amount();
						case ADD_MULTIPLIED_BASE -> add[1] += modifier.amount();
						case ADD_MULTIPLIED_TOTAL -> add[2] *= 1.0 + modifier.amount();
					}
				}
			});
		return add[0] * (1.0 + add[1]) * add[2];
	}

	/** Distance from the mob's eyes to the nearest point of the target's box. */
	static double gap(LivingEntity from, LivingEntity to) {
		Vec3 eye = from.getEyePosition();
		AABB box = to.getBoundingBox();
		double x = Mth.clamp(eye.x, box.minX, box.maxX);
		double y = Mth.clamp(eye.y, box.minY, box.maxY);
		double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
		return eye.distanceTo(new Vec3(x, y, z));
	}

	private static void tick(MinecraftServer server) {
		if (SWINGS.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, Swing> entry : new ArrayList<>(SWINGS.entrySet())) {
			Mob mob = entry.getKey();
			Swing swing = entry.getValue();
			if (mob == null || !(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			if (!mob.isAlive() || mob.isRemoved()) {
				SWINGS.remove(mob);
				continue;
			}
			if (swing.hitAt >= 0 && now >= swing.hitAt) {
				swing.hitAt = -1;
				if (swing.move != null && !mob.getMainHandItem().isEmpty()) {
					strike(level, mob, swing.move, swing.target);
				}
			}
			if (now >= swing.endsAt) {
				AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(SWING_SLOW);
				}
				if (now - swing.lastSwing > COMBO_RESET_TICKS * 4L) {
					SWINGS.remove(mob);
				}
			}
		}
	}

	/** The blow: every foe in the arc and reach, at the attack's damage multiplier. */
	private static void strike(ServerLevel level, Mob mob, Move move, @Nullable LivingEntity primary) {
		Moves moves = BetterCombatBridge.moves(mob.getMainHandItem());
		if (moves == null) {
			return;
		}
		double reach = moves.reach() * move.reachScale();
		double half = switch (move.shape()) {
			case 2 -> Math.max(STAB_HALF_ANGLE, move.angle() / 2.0);
			case 1 -> Math.max(OVERHEAD_HALF_ANGLE, Math.min(move.angle(), 60.0) / 2.0);
			default -> STAB_HALF_ANGLE;
		};
		float yaw = mob.getYHeadRot();
		Vec3 facing = Vec3.directionFromRotation(0.0F, yaw);
		List<LivingEntity> hits = new ArrayList<>();
		if (primary != null && primary.isAlive() && gap(mob, primary) <= reach + 0.25 && mob.getSensing().hasLineOfSight(primary)) {
			hits.add(primary);
		}
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach + 1.0),
			e -> e != mob && e != primary && e.isAlive() && e.isAttackable() && !mob.isAlliedTo(e) && !mob.isPassengerOfSameVehicle(e))) {
			if (hits.size() >= MAX_TARGETS) {
				break;
			}
			if (gap(mob, other) > reach || !foe(mob, other) || !mob.hasLineOfSight(other)) {
				continue;
			}
			Vec3 to = new Vec3(other.getX() - mob.getX(), 0.0, other.getZ() - mob.getZ());
			if (to.lengthSqr() > 1.0E-4) {
				double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(facing), -1.0, 1.0)));
				if (angle > half) {
					continue;
				}
			}
			hits.add(other);
		}
		if (move.sound() != null) {
			Holder<SoundEvent> sound = soundHolder(move.sound());
			float pitch = move.pitch() + (mob.getRandom().nextFloat() - 0.5F) * move.pitchSpread();
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, mob.getSoundSource(), move.volume(), pitch);
		}
		if (hits.isEmpty()) {
			return;
		}
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null && move.damage() != 1.0) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(COMBO_DAMAGE, move.damage() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		try {
			for (LivingEntity victim : hits) {
				mob.doHurtTarget(level, victim);
			}
		} finally {
			if (damage != null) {
				damage.removeModifier(COMBO_DAMAGE);
			}
		}
	}

	private static Holder<SoundEvent> soundHolder(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return Holder.direct(SoundEvents.PLAYER_ATTACK_SWEEP);
		}
		return BuiltInRegistries.SOUND_EVENT.get(key).<Holder<SoundEvent>>map(h -> h)
			.orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(key)));
	}

	/** Who a sweep may strike besides its target: whoever is fighting the mob, and those it would fight anyway. */
	static boolean foe(Mob mob, LivingEntity other) {
		if (other == mob.getTarget() || other instanceof Mob m && m.getTarget() == mob) {
			return true;
		}
		if (mob instanceof Folk folk) {
			MinecraftServer server = mob.level().getServer();
			if (other instanceof Folk them) {
				return server != null && !folk.sameSide(them) && Relations.between(server, folk.factionId(), them.factionId()) == Relation.WAR;
			}
			boolean wild = folk.style() == FactionType.BARBARIAN;
			if (other instanceof ServerPlayer player) {
				return !player.isCreative() && !player.isSpectator()
					&& (wild || Reputation.get(player, folk.factionId()) <= Reputation.HOSTILE || Theft.wanted(player, folk.factionId())
						|| Aliens.feared(player, folk.factionId()));
			}
			return !wild && (other instanceof Enemy && !(other instanceof Creeper) || Oddities.hunted(other) || Vampires.exposed(other));
		}
		// a monster's sweep catches players, never its own kind
		return other instanceof Player player && !player.isCreative() && !player.isSpectator() && !(other instanceof Enemy);
	}

	/** Whether this mob is mid-swing (between its wind-up and the end of its swing). */
	public static boolean swinging(Mob mob) {
		Swing swing = SWINGS.get(mob);
		return swing != null && mob.level().getGameTime() < swing.endsAt;
	}

	/** The sidestep: fighting folk may dodge a player's blow. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Folk folk) || !folk.combatant() || !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player || source.isCreativePlayer()) {
			return true;
		}
		if (!folk.onGround() || folk.isPassenger() || swinging(folk) || folk.hurtTime > 0) {
			return true;
		}
		long now = level.getGameTime();
		Long last = DODGED.get(folk);
		if (last != null && now - last < DODGE_COOLDOWN) {
			return true;
		}
		if (folk.getRandom().nextFloat() >= dodgeChance(folk)) {
			return true;
		}
		DODGED.put(folk, now);
		Vec3 away = new Vec3(folk.getX() - player.getX(), 0.0, folk.getZ() - player.getZ());
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, folk.getYRot()).reverse() : away.normalize();
		Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(folk.getRandom().nextBoolean() ? 1.0 : -1.0);
		folk.setDeltaMovement(side.scale(DODGE_SIDE).add(away.scale(DODGE_BACK)).add(0.0, DODGE_UP, 0.0));
		folk.syncVelocity = true;
		folk.getLookControl().setLookAt(player, 30.0F, 30.0F);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 0.1, folk.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		return false;
	}

	static float dodgeChance(Folk folk) {
		if (folk.leader()) {
			return DODGE_LEADER;
		}
		if (folk.style() == FactionType.BARBARIAN) {
			return DODGE_BARBARIAN;
		}
		return folk.role() == Folk.ARCHER ? DODGE_ARCHER : DODGE_GUARD;
	}
}
