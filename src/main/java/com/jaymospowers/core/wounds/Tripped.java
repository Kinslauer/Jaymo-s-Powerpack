package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.power.darktide.DarktideStatus;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.71) Tripped by a bolas. Andy (2026-10-30): "bolas should force a cripple on humanoid mobs, and animate wrapping
 * and tripping them". Kingdoms' bolas (through its {@code PowersCompat}) calls {@link #bind} on any mob built like a
 * person ({@link BodyPlan#HUMANOID}: villagers, MCA folk, bandits, illagers, piglins, zombies, skeletons):
 * <ul>
 * <li>the cripple: one leg is broken in Wounds ({@link Wounds#breakPart}) unless one already is, so it limps on after it's
 * free (the doll shows it, a cleric or a splint sees to it, and Kitchen's corpse keeps it);</li>
 * <li>the trip: the cord cinches the legs together, it teeters, then falls flat on its face and lies there kicking its
 * bound legs for the trip's length ({@link #TRIP}, synced for {@code TrippedPose} and the cord drawn on the legs), then
 * works the cord off and gets up over {@link #GET_UP_TICKS}. Down, it thinks nothing (Darktide's stun) and can be
 * finished like the downed ({@code Finishers.helpless}).</li>
 * </ul>
 * Players are never tripped (Kingdoms' bolas never hits one), nor anything downed, carried or already tripped.
 */
public final class Tripped {
	/** The trip's length on the ground at least and at most (ticks), whatever Kingdoms asks. */
	public static final int MIN_TICKS = 30, MAX_TICKS = 200;
	/** The cord whipping round the legs before it falls, the fall itself (ticks; {@code TrippedPose} times its pose by these). */
	public static final int WRAP_TICKS = 6, FALL_TICKS = 8;
	/** How long it takes to work the cord off and get up once the trip's over (ticks; still stunned meanwhile). */
	public static final int GET_UP_TICKS = 14;
	/** Speed and jump lost while tripped (shares), so a shove doesn't send it skating. */
	public static final double SPEED = -0.9, JUMP = -1.0;

	/** A trip: the game time it began, and its length on the ground (ticks; the get-up comes after). */
	public record Trip(long start, int ticks) {
		public static final StreamCodec<ByteBuf, Trip> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Trip::start,
			ByteBufCodecs.VAR_INT, Trip::ticks,
			Trip::new);

		/** When it's back on its feet (game time). */
		public long over() {
			return this.start + this.ticks + GET_UP_TICKS;
		}
	}

	/** The trip on a body, through its get-up. Synced to everyone (the fall and the cord), not saved. */
	public static final AttachmentType<Trip> TRIP = AttachmentRegistry.create(JaymosPowers.id("wounds_tripped"),
		builder -> builder.syncWith(Trip.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Identifier SPEED_ID = JaymosPowers.id("wounds_tripped_speed");
	private static final Identifier JUMP_ID = JaymosPowers.id("wounds_tripped_jump");

	/** The tripped, until they're up (for the landing thud and the end). Not saved: a reload stands them up. */
	private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	private Tripped() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Tripped::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRACKED.clear());
	}

	/** Whether {@code entity} is down on the ground from a trip (not getting up yet): helpless, for finishers. */
	public static boolean tripped(Entity entity) {
		Trip trip = entity.getAttached(TRIP);
		return trip != null && entity.level().getGameTime() < trip.start() + trip.ticks();
	}

	/** Whether a bolas can trip {@code entity}: a living mob built like a person, on its feet. */
	public static boolean trippable(Entity entity) {
		return entity instanceof LivingEntity living && !(entity instanceof Player) && living.isAlive() && Wounds.plan(living) == BodyPlan.HUMANOID
			&& !Downed.isDowned(living) && !Carry.isCarried(living);
	}

	/**
	 * A bolas wraps {@code target}'s legs ({@code by} threw it): a leg broken and down it goes, on the ground for
	 * {@code ticks} (kept to {@link #MIN_TICKS}-{@link #MAX_TICKS}). False if it can't be tripped (left to the caller's
	 * own tangle), or is down already.
	 */
	public static boolean bind(LivingEntity target, @Nullable Entity by, int ticks) {
		if (!trippable(target) || !(target.level() instanceof ServerLevel level)) {
			return false;
		}
		if (target.getAttached(TRIP) != null) {
			// (still down, or getting up: it's held as it is)
			return true;
		}
		// the cripple: a leg the cord took out from under it (unless it already limps on one)
		if (!Breaks.isBroken(target, BodyPart.LEFT_LEG) && !Breaks.isBroken(target, BodyPart.RIGHT_LEG)) {
			Wounds.breakPart(target, target.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG, by);
		}
		if (!target.isAlive() || Downed.isDowned(target)) {
			// (the break was too much: the downed state has it now)
			return true;
		}
		int length = Mth.clamp(ticks, MIN_TICKS, MAX_TICKS);
		Trip trip = new Trip(level.getGameTime(), length);
		target.setAttached(TRIP, trip);
		TRACKED.add(target);
		DarktideStatus.stun(target, (int) (trip.over() - trip.start()));
		target.setSprinting(false);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		hobble(target, true);
		return true;
	}

	/** (Andy, 2026-10-30) Up at once, nothing left of it (creative). */
	static void clear(LivingEntity entity) {
		if (entity.getAttached(TRIP) != null) {
			entity.removeAttached(TRIP);
			hobble(entity, false);
		}
	}

	private static void hobble(LivingEntity entity, boolean on) {
		modifier(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? SPEED : 0.0);
		modifier(entity, Attributes.JUMP_STRENGTH, JUMP_ID, on ? JUMP : 0.0);
	}

	private static void modifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<LivingEntity> it = TRACKED.iterator(); it.hasNext(); ) {
			LivingEntity entity = it.next();
			Trip trip = entity.getAttached(TRIP);
			if (trip == null || entity.isRemoved() || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
				it.remove();
				entity.removeAttached(TRIP);
				hobble(entity, false);
				continue;
			}
			long age = level.getGameTime() - trip.start();
			if (age == WRAP_TICKS + FALL_TICKS) {
				// face first into the ground
				BlockPos below = entity.blockPosition().below();
				BlockState ground = level.getBlockState(below);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.HOSTILE, 1.0F, 0.7F);
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), entity.getX(), entity.getY() + 0.1, entity.getZ(),
						12, 0.5, 0.05, 0.5, 0.1);
				}
			}
			if (Downed.isDowned(entity) || Carry.isCarried(entity) || level.getGameTime() >= trip.over()) {
				// up again (the limp stays with the leg), or something else has it now
				it.remove();
				entity.removeAttached(TRIP);
				hobble(entity, false);
				continue;
			}
			if (entity instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}
}
