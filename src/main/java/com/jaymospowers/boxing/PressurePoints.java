package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.LimbUse;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.gravity.GravityPower;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Boxing's trained strikes for the bare fist, on the punch itself (no new keys): each is a punch that lands with a
 * held input, and what it earns comes from how far the boxer's style has been raised ({@link BoxingTraining}: the
 * teachers' tasks and the fighting), in whichever style is highest.
 * <ul>
 * <li><b>Loose joint</b> ({@link #PASSIVE_LEVEL}): a small chance on every punch to lock a limb for a moment
 * ({@link #LOCK_TICKS}).</li>
 * <li><b>Pressure point</b> ({@link #PRESSURE_LEVEL}; sneak + punch): numbs an arm or a leg for {@link #NUMB_TICKS}
 * without breaking it. Aim high for an arm, low for a leg. A numb limb counts as unusable in the limb table
 * ({@link LimbUse}): a numb arm drops what it holds and cannot attack, and a numb leg slows the walk (and shuts what is
 * done with the legs). A player with a power has two of their ability keys shut for {@link #KEYS_TICKS}.</li>
 * <li><b>Knee kick</b> ({@link #KNEE_LEVEL}; sprint + punch): drops the target kneeling for {@link #KNEE_TICKS}, taking
 * {@link #KNEE_BONUS}x for as long ({@link #exposed}).</li>
 * <li><b>Sweep</b> ({@link #SWEEP_LEVEL}; sneak + strafe + punch): the target goes flat on its back
 * ({@link Tripped#sweep}).</li>
 * </ul>
 */
public final class PressurePoints {
	// ---- tuning ----
	/** The style level each strike is taught at. */
	public static final int PASSIVE_LEVEL = 2, PRESSURE_LEVEL = 4, KNEE_LEVEL = 5, SWEEP_LEVEL = 7;
	/** The chance of a punch locking a limb, and for how long (ticks). */
	public static final float LOCK_CHANCE = 0.08F;
	public static final int LOCK_TICKS = 50;
	/** A pressure point: how long the limb stays dead (ticks), how long two ability keys stay shut, and the wait before the next. */
	public static final int NUMB_TICKS = 200, KEYS_TICKS = 100, PRESSURE_COOLDOWN = 100;
	/** The knee kick: ticks kneeling, the share of every blow taken meanwhile, and the wait. */
	public static final int KNEE_TICKS = 40, KNEE_COOLDOWN = 80;
	public static final float KNEE_BONUS = 1.35F;
	/** The sweep: ticks flat on the back, and the wait. */
	public static final int SWEEP_TICKS = 40, SWEEP_COOLDOWN = 160;
	/** What each strike costs (half-feathers), on top of the punch. */
	public static final float PRESSURE_COST = 2.0F, KNEE_COST = 2.0F, SWEEP_COST = 3.0F;
	/** A numb leg's share of walking speed lost, and a numb arm's of a mob's attack. */
	public static final double LEG_SLOW = -0.4, ARM_WEAK = -0.6;
	/** Where a look must pass (share of the target's height) to find a leg and not an arm. */
	static final double LEG_BELOW = 0.45;

	/** When each limb stops being numb (game time): left arm, right arm, left leg, right leg. Told to everyone, not saved. */
	public record Numb(long leftArm, long rightArm, long leftLeg, long rightLeg) {
		public static final StreamCodec<ByteBuf, Numb> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Numb::leftArm,
			ByteBufCodecs.VAR_LONG, Numb::rightArm,
			ByteBufCodecs.VAR_LONG, Numb::leftLeg,
			ByteBufCodecs.VAR_LONG, Numb::rightLeg,
			Numb::new);

		long until(BodyPart part) {
			return switch (part) {
				case LEFT_ARM -> this.leftArm;
				case RIGHT_ARM -> this.rightArm;
				case LEFT_LEG -> this.leftLeg;
				case RIGHT_LEG -> this.rightLeg;
				default -> 0L;
			};
		}

		Numb with(BodyPart part, long until) {
			return switch (part) {
				case LEFT_ARM -> new Numb(Math.max(this.leftArm, until), this.rightArm, this.leftLeg, this.rightLeg);
				case RIGHT_ARM -> new Numb(this.leftArm, Math.max(this.rightArm, until), this.leftLeg, this.rightLeg);
				case LEFT_LEG -> new Numb(this.leftArm, this.rightArm, Math.max(this.leftLeg, until), this.rightLeg);
				case RIGHT_LEG -> new Numb(this.leftArm, this.rightArm, this.leftLeg, Math.max(this.rightLeg, until));
				default -> this;
			};
		}

		long last() {
			return Math.max(Math.max(this.leftArm, this.rightArm), Math.max(this.leftLeg, this.rightLeg));
		}
	}

	public static final AttachmentType<Numb> NUMB = AttachmentRegistry.create(JaymosPowers.id("boxing_numb"),
		builder -> builder.syncWith(Numb.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Identifier LEG_ID = JaymosPowers.id("pressure_leg"), ARM_ID = JaymosPowers.id("pressure_arm");

	/** Two ability keys shut: their bits (by {@link PowerKey} place) and until when. */
	private record Shut(int mask, long until) {
	}

	/** A boxer's waits (game time). */
	private static final class State {
		long pressureReady, kneeReady, sweepReady;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Shut> SHUT = new HashMap<>();
	/** victim → until when a knee kick keeps it exposed (game time). */
	private static final Map<UUID, Long> KNEELING = new HashMap<>();
	/** The numbed, until they recover. */
	private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	private PressurePoints() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PressurePoints::tick);
		// a numb main arm can't attack (both sides, so the swing doesn't start either)
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			hand == InteractionHand.MAIN_HAND && numb(player, LimbUse.arm(player, true)) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			STATES.remove(handler.player.getUUID());
			SHUT.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			SHUT.clear();
			KNEELING.clear();
			TRACKED.clear();
		});
	}

	// ---- reading ----

	/** Whether {@code part} (an arm or a leg) of {@code entity} is numb just now (both sides). */
	public static boolean numb(LivingEntity entity, BodyPart part) {
		Numb numb = entity.getAttached(NUMB);
		return numb != null && entity.level().getGameTime() < numb.until(part);
	}

	/** Whether a press of {@code player}'s power key {@code key} is shut off just now (server). */
	public static boolean keyShut(ServerPlayer player, PowerKey key) {
		Shut shut = SHUT.get(player.getUUID());
		return shut != null && player.level().getGameTime() < shut.until() && (shut.mask() & 1 << key.ordinal()) != 0;
	}

	/** {@code damage} to {@code victim}, more while a knee kick has it down. */
	public static float exposed(LivingEntity victim, float damage) {
		if (KNEELING.isEmpty()) {
			return damage;
		}
		Long until = KNEELING.get(victim.getUUID());
		if (until == null) {
			return damage;
		}
		if (victim.level().getGameTime() >= until) {
			KNEELING.remove(victim.getUUID());
			return damage;
		}
		return damage * KNEE_BONUS;
	}

	/** The highest level {@code boxer} has in any style. */
	private static int top(ServerPlayer boxer) {
		int top = 0;
		for (Boxing.Style style : Boxing.Style.values()) {
			top = Math.max(top, Boxing.level(boxer, style));
		}
		return top;
	}

	// ---- the strikes ----

	/** A punch of {@code boxer}'s landed on {@code target} (and was not blocked): the input held picks the strike. */
	static void landed(ServerPlayer boxer, LivingEntity target) {
		int level = top(boxer);
		if (level < PASSIVE_LEVEL || !target.isAlive() || boxer.isSpectator()) {
			return;
		}
		State state = STATES.computeIfAbsent(boxer.getUUID(), id -> new State());
		long now = boxer.level().getGameTime();
		Input input = boxer.getLastClientInput();
		boolean sneak = boxer.isShiftKeyDown();
		boolean strafe = input.left() != input.right();
		if (sneak && strafe && level >= SWEEP_LEVEL && now >= state.sweepReady && Tripped.sweepable(target)) {
			if (Boxing.spend(boxer, SWEEP_COST) && Tripped.sweep(target, SWEEP_TICKS)) {
				state.sweepReady = now + SWEEP_COOLDOWN;
				fx(boxer, target, 0xD8D0C0, 0.9F);
				boxer.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.8F);
			}
			return;
		}
		if (sneak && !strafe && level >= PRESSURE_LEVEL && now >= state.pressureReady) {
			if (Boxing.spend(boxer, PRESSURE_COST)) {
				state.pressureReady = now + PRESSURE_COOLDOWN;
				pressure(boxer, target, now);
			}
			return;
		}
		if (!sneak && boxer.isSprinting() && level >= KNEE_LEVEL && now >= state.kneeReady) {
			if (Boxing.spend(boxer, KNEE_COST)) {
				state.kneeReady = now + KNEE_COOLDOWN;
				knee(boxer, target, now);
			}
			return;
		}
		if (!sneak && boxer.getRandom().nextFloat() < LOCK_CHANCE) {
			BodyPart[] limbs = {BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};
			numb(target, limbs[boxer.getRandom().nextInt(limbs.length)], LOCK_TICKS, false);
		}
	}

	private static void fx(ServerPlayer boxer, LivingEntity target, int color, float size) {
		MagicFx.ring(boxer.level(), target.getBoundingBox().getCenter(), size * 0.5F, color);
	}

	private static void knee(ServerPlayer boxer, LivingEntity target, long now) {
		GravityPower.kneel(target, now + KNEE_TICKS);
		DarktideStatus.stun(target, KNEE_TICKS);
		KNEELING.put(target.getUUID(), now + KNEE_TICKS);
		if (KNEELING.size() > 256) {
			KNEELING.values().removeIf(until -> until <= now);
		}
		fx(boxer, target, 0xC8C0D8, 0.8F);
		boxer.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	/** Which limb the boxer's look finds on {@code target}: high is an arm, low a leg, and the side the look is on. */
	private static BodyPart aimed(ServerPlayer boxer, LivingEntity target) {
		Vec3 eye = boxer.getEyePosition(), centre = target.getBoundingBox().getCenter();
		double along = Math.max(0.0, centre.subtract(eye).dot(boxer.getLookAngle()));
		Vec3 point = eye.add(boxer.getLookAngle().scale(along));
		boolean leg = (point.y - target.getY()) / Math.max(0.1, target.getBbHeight()) < LEG_BELOW;
		float yaw = target.getYRot() * Mth.DEG_TO_RAD;
		// the target's own right-hand side, across the ground
		double right = (point.x - centre.x) * -Mth.cos(yaw) + (point.z - centre.z) * -Mth.sin(yaw);
		boolean rightSide = right > 0.0;
		return leg ? (rightSide ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG) : (rightSide ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
	}

	private static void pressure(ServerPlayer boxer, LivingEntity target, long now) {
		BodyPart part = aimed(boxer, target);
		numb(target, part, NUMB_TICKS, true);
		boolean arm = part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM;
		if (arm && target instanceof ServerPlayer victim && Powers.of(victim) != null) {
			// two of the four keys, picked at random, go quiet
			int first = boxer.getRandom().nextInt(4), second = (first + 1 + boxer.getRandom().nextInt(3)) % 4;
			SHUT.put(victim.getUUID(), new Shut(1 << first | 1 << second, now + KEYS_TICKS));
			victim.sendOverlayMessage(Component.translatable("message.jaymospowers.pressure.keys").withStyle(ChatFormatting.GRAY));
		}
		fx(boxer, target, 0xB8A8E0, 0.6F);
		boxer.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	// ---- numbness ----

	/** {@code part} of {@code target} goes numb for {@code ticks}; a numb arm drops what its hand holds when {@code drop}. */
	private static void numb(LivingEntity target, BodyPart part, int ticks, boolean drop) {
		long until = target.level().getGameTime() + ticks;
		Numb was = target.getAttached(NUMB);
		target.setAttached(NUMB, (was == null ? new Numb(0L, 0L, 0L, 0L) : was).with(part, until));
		TRACKED.add(target);
		if (target instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pressure.limb", part.displayName()).withStyle(ChatFormatting.GRAY));
			if (drop && !player.isCreative() && (part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM)) {
				InteractionHand hand = part == LimbUse.arm(player, true) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				ItemStack held = player.getItemInHand(hand);
				if (!held.isEmpty()) {
					player.setItemInHand(hand, ItemStack.EMPTY);
					ItemEntity dropped = player.drop(held, false, Prediction.SERVER_ONLY);
					if (dropped != null) {
						dropped.setPickUpDelay(40);
					}
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (TRACKED.isEmpty()) {
			return;
		}
		for (Iterator<LivingEntity> it = TRACKED.iterator(); it.hasNext(); ) {
			LivingEntity entity = it.next();
			Numb numb = entity.getAttached(NUMB);
			long now = entity.level().getGameTime();
			if (numb == null || entity.isRemoved() || !entity.isAlive() || now >= numb.last()) {
				it.remove();
				entity.removeAttached(NUMB);
				modifier(entity, Attributes.MOVEMENT_SPEED, LEG_ID, 0.0);
				modifier(entity, Attributes.ATTACK_DAMAGE, ARM_ID, 0.0);
				continue;
			}
			int legs = (now < numb.leftLeg() ? 1 : 0) + (now < numb.rightLeg() ? 1 : 0);
			modifier(entity, Attributes.MOVEMENT_SPEED, LEG_ID, LEG_SLOW * legs);
			if (!(entity instanceof ServerPlayer)) {
				// (a mob has no hand to drop things from: its blows are what weaken)
				boolean arms = now < numb.leftArm() || now < numb.rightArm();
				modifier(entity, Attributes.ATTACK_DAMAGE, ARM_ID, arms ? ARM_WEAK : 0.0);
			}
		}
	}

	private static void modifier(LivingEntity entity, net.minecraft.core.Holder<Attribute> attribute, Identifier id, double amount) {
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
}
