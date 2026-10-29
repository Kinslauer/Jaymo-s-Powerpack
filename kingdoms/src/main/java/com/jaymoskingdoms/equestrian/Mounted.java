package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.305) Riding extras (ideas from Icy's Better Horses, our own code): a ridden horse steps up a block and a half (an
 * elephant two), swims with its rider instead of throwing them in deep water, carries a second rider behind the saddle,
 * keeps going on its own when its rider double-taps forward (the client decides, {@link #cruise}), and builds a charge
 * as it gallops: past {@link #CHARGE_READY} it runs down the enemies in its way, harder the faster it goes and the
 * heavier its barding, spending its charge on each. (Leaves are already soft to everything in the pack: Jaymo's Powers.)
 * Tuning numbers live at the top.
 */
public final class Mounted {
	/** How high a ridden horse steps, and a ridden elephant (unridden ones keep vanilla's block, so pens still hold them). */
	public static final float RIDDEN_STEP = 1.5F, ELEPHANT_STEP = 2.0F;
	/** Swimming: it rises while the water is over this share of its height, this fast at most (blocks a tick). */
	public static final double FLOAT_DEPTH = 0.55, FLOAT_LIFT = 0.06, FLOAT_RISE = 0.12;
	/** The second rider sits this far behind the saddle (blocks, at scale 1). */
	public static final double PILLION_BACK = 0.6, PILLION_DROP = 0.05;
	/** (0.7.322) How much higher a rider sits on our taller horses (3 model px; none while they're the classic shape). */
	public static final double SEAT_LIFT = 0.0;
	/** The charge: seconds of galloping to fill it (a sprint fills it twice as fast), how fast it drains at a trot or slower (a second). */
	public static final float CHARGE_SECONDS = 3.0F, CHARGE_DRAIN = 0.5F;
	/** It runs things down from this much charge; each one it hits costs this much of it. */
	public static final float CHARGE_READY = 0.35F, CHARGE_COST = 0.45F;
	/** The blow at full charge and full speed, more for each point of barding (a share), how far it's thrown, how often the same one is hit. */
	public static final float CHARGE_HIT = 12.0F, BARDING_HIT = 0.05F;
	static final double CHARGE_KNOCKBACK = 1.2;
	static final int CHARGE_COOLDOWN = 20;
	/** How often the charge is seen to (ticks). */
	static final int STEP = 2;

	/** How built-up its charge is (0-1). Synced to every client (its rider's HUD); not saved. */
	public static final AttachmentType<Float> CHARGE = AttachmentRegistry.create(JaymosKingdoms.id("charge"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	/** Whether {@code rider} rides on without holding forward (set by the client; never on a server). */
	public static Predicate<Player> cruise = rider -> false;

	/** Struck entity id → game time it may be run down again. */
	private static final Map<Integer, Long> HIT = new HashMap<>();

	private Mounted() {
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("mounted attachment: {}", CHARGE);
		ServerTickEvents.END_LEVEL_TICK.register(Mounted::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HIT.clear());
	}

	// ---- from the mixins ---------------------------------------------------------------------------------------------

	/** (From {@code LivingEntityMountMixin}) how high it steps: higher with a player in the saddle. */
	public static float step(LivingEntity e, float vanilla) {
		if (e instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.getControllingPassenger() instanceof Player) {
			return Math.max(vanilla, horse instanceof WarElephant ? ELEPHANT_STEP : RIDDEN_STEP);
		}
		return vanilla;
	}

	/** (From {@code EntityMountMixin}) our horses don't throw their riders in deep water: they swim. */
	public static boolean swims(Entity e) {
		return e instanceof AbstractHorse horse && Equestrian.covered(horse);
	}

	/** (From {@code EntityMountMixin}) room for a second rider: a player behind the first, on a saddled, tamed horse. */
	public static boolean pillion(Entity vehicle, Entity passenger) {
		return vehicle instanceof AbstractHorse horse && Equestrian.covered(horse) && !(horse instanceof WarElephant) && horse.isTamed()
			&& !horse.isBaby() && horse.isSaddled() && passenger instanceof Player && horse.getPassengers().size() == 1
			&& horse.getFirstPassenger() instanceof Player;
	}

	/** (From {@code AbstractHorseMixin}) where riders sit: up on our taller horses, and the second behind the first. */
	public static Vec3 seat(AbstractHorse horse, Entity passenger, Vec3 vanilla, float scale) {
		// (0.7.322) our horses stand taller than vanilla's (BreedHorseModel.LIFT, 3 px), so every seat is that much higher
		Vec3 seat = horse instanceof WarElephant ? vanilla : vanilla.add(0.0, SEAT_LIFT * scale, 0.0);
		if (horse.getPassengers().indexOf(passenger) != 1) {
			return seat;
		}
		return seat.add(new Vec3(0.0, -PILLION_DROP * scale, -PILLION_BACK * scale).yRot(-horse.getYRot() * Mth.DEG_TO_RAD));
	}

	/** (From {@code AbstractHorseMixin}, on whichever side moves it) a ridden horse in deep water swims up to keep its rider's head out. */
	public static void swim(AbstractHorse horse) {
		if (!Equestrian.covered(horse) || !horse.isInWater()) {
			return;
		}
		double depth = horse.getFluidHeight(FluidTags.WATER);
		if (depth > horse.getBbHeight() * FLOAT_DEPTH) {
			Vec3 v = horse.getDeltaMovement();
			horse.setDeltaMovement(v.x, Math.min(FLOAT_RISE, Math.max(v.y, 0.0) + FLOAT_LIFT), v.z);
		}
	}

	/** (From {@code AbstractHorseMixin}) the rider's input: straight on when they ride on without holding forward. */
	public static Vec3 input(AbstractHorse horse, Player controller, Vec3 input) {
		if (cruise.test(controller) && !horse.isStanding()) {
			return new Vec3(input.x, input.y, 1.0);
		}
		return input;
	}

	public static float charge(AbstractHorse horse) {
		Float c = horse.getAttached(CHARGE);
		return c == null ? 0.0F : c;
	}

	// ---- the charge ------------------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % STEP != 0) {
			return;
		}
		for (ServerPlayer rider : level.players()) {
			if (rider.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse) && !(horse instanceof WarElephant)
				&& horse.getControllingPassenger() == rider) {
				charge(level, horse, rider, now);
			}
		}
		if (now % 1200 == 0) {
			HIT.values().removeIf(t -> t < now);
		}
	}

	/** Builds or spends its charge by its gait, and runs down what's in its way once it's built. */
	private static void charge(ServerLevel level, AbstractHorse horse, ServerPlayer rider, long now) {
		Stride s = Equestrian.stride(horse);
		Gait gait = s.gaitOf();
		double speed = horse.getDeltaMovement().horizontalDistance();
		double top = Math.max(0.05, Bonding.topSpeed(horse, Equestrian.equine(horse)));
		float was = charge(horse);
		float c = was;
		float seconds = STEP / 20.0F;
		if (gait.ordinal() >= Gait.CANTER.ordinal() && speed > top * 0.4) {
			float fill = (gait == Gait.GALLOP ? 1.0F : 0.5F) * (s.sprint() ? 2.0F : 1.0F);
			c += fill * seconds / CHARGE_SECONDS;
		} else {
			c -= CHARGE_DRAIN * seconds;
		}
		c = Mth.clamp(c, 0.0F, 1.0F);
		if (c >= CHARGE_READY && gait.ordinal() >= Gait.CANTER.ordinal()) {
			c = runDown(level, horse, rider, c, speed / top, now);
		}
		if (Math.abs(c - was) > 0.01F || c == 0.0F && was != 0.0F || c == 1.0F && was != 1.0F) {
			horse.setAttached(CHARGE, c == 0.0F ? null : c);
		}
	}

	/** Strikes the enemies just ahead (monsters, and anything after its rider); the charge it has left. */
	private static float runDown(ServerLevel level, AbstractHorse horse, ServerPlayer rider, float c, double pace, long now) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, horse.getYRot());
		AABB box = horse.getBoundingBox().move(ahead.scale(horse.getBbWidth() * 0.7)).inflate(0.4, 0.0, 0.4);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && t != horse && t != rider
			&& !t.isPassengerOfSameVehicle(rider) && (t instanceof Enemy || t instanceof Mob m && m.getTarget() == rider))) {
			if (c < CHARGE_READY) {
				break;
			}
			Long until = HIT.get(target.getId());
			if (until != null && until > now) {
				continue;
			}
			HIT.put(target.getId(), now + CHARGE_COOLDOWN);
			float damage = CHARGE_HIT * c * (float)Mth.clamp(pace, 0.4, 1.3) * (1.0F + BARDING_HIT * horse.getArmorValue());
			DamageSource source = level.damageSources().mobAttack(horse);
			target.hurtServer(level, source, damage);
			target.knockback(CHARGE_KNOCKBACK * c, -ahead.x, -ahead.z, source, damage);
			target.push(0.0, 0.3, 0.0);
			c -= CHARGE_COST;
			level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 1.0F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			if (horse.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).is(Items.LEATHER_HORSE_ARMOR)) {
				// leather barding gives little behind the blow
				c -= CHARGE_COST * 0.25F;
			}
		}
		return Math.max(0.0F, c);
	}
}
