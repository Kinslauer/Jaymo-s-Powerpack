package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.boxing.PressurePoints;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.gear.shield.ShieldMoves;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Guard options for everyone, on the parry ({@link Guard}) and without touching its timing:
 * <ul>
 * <li><b>Plant</b>: sneak while any guard is up (a weapon's, a fist's, a power's own arms, a shield) for
 * {@link #PLANT_TICKS}: far less comes through ({@link #PLANT_CUT}), nothing can shove or stun you, and you move at a
 * crawl while it holds. A shield's plant is the shield brace ({@link ShieldMoves}): sneaking counts as standing still
 * for it, and the brace does the damage; the plant adds only the crawl and the steadiness, so the two never stack.</li>
 * <li><b>Hand back</b>: a parried melee blow from the front hands part of its weight back to whoever swung
 * ({@link #handBack}); a perfect parry hands back more.</li>
 * <li><b>Step</b>: jump while guarding and you hop up and back, out of the melee and over a low wall, with a moment of
 * resistance; it needs a short rest and a little stamina.</li>
 * </ul>
 */
public final class Stances {
	// ---- tuning ----
	/** Plant: ticks of sneaking behind a guard before it takes hold, the share of a blow cut, and what it costs in movement (shares). */
	public static final int PLANT_TICKS = 20;
	public static final float PLANT_CUT = 0.6F;
	public static final double PLANT_SPEED = -0.8, PLANT_JUMP = -1.0;
	/** Hand back: the share of a parried blow thrown back at the attacker, on a parry and on a perfect one, and the most it can be. */
	public static final float HAND_BACK_SHARE = 0.3F, HAND_BACK_PERFECT_SHARE = 0.5F, HAND_BACK_CAP = 8.0F;
	/** Step: how hard it throws you back and up (blocks/tick), the wait before the next (ticks), stamina (half-feathers), and the resistance after it (ticks, amplifier). */
	public static final double STEP_BACK = 0.6, STEP_UP = 0.58;
	public static final int STEP_COOLDOWN = 60, STEP_FEATHERS = 2, STEP_RESIST_TICKS = 14, STEP_RESIST_AMP = 1;

	private static final Identifier PLANT_SPEED_ID = JaymosPowers.id("plant_speed");
	private static final Identifier PLANT_JUMP_ID = JaymosPowers.id("plant_jump");
	private static final Identifier PLANT_ROOT_ID = JaymosPowers.id("plant_root");

	private static final class State {
		int planting;
		boolean planted, jumped;
		long stepReady;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static boolean handing;

	private Stances() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Stances::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	/** Whether {@code player} is planted just now (server). */
	public static boolean planted(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.planted;
	}

	/** Whether nothing can move or stun {@code entity}: it is planted. For the stun and the stagger. */
	public static boolean immovable(LivingEntity entity) {
		return entity instanceof ServerPlayer player && planted(player);
	}

	/** Whether {@code player} has any guard up: a weapon's, a shield's, a fist's, or a power's own. */
	private static boolean guarding(ServerPlayer player) {
		return Guard.raised(player) || player.hasAttached(Guard.SHOWN);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.get(player.getUUID());
			boolean guarding = player.isAlive() && !player.isSpectator() && guarding(player);
			if (state == null && !guarding) {
				continue;
			}
			if (state == null) {
				state = new State();
				STATES.put(player.getUUID(), state);
			}
			plant(player, state, guarding);
			step(player, state, guarding);
		}
	}

	// ---- plant ----

	private static void plant(ServerPlayer player, State state, boolean guarding) {
		boolean sneaking = guarding && player.isShiftKeyDown() && player.onGround() && !player.isPassenger() && !player.isFallFlying();
		state.planting = sneaking ? state.planting + 1 : 0;
		boolean planted = state.planting >= PLANT_TICKS;
		if (planted == state.planted) {
			return;
		}
		state.planted = planted;
		modifier(player, Attributes.MOVEMENT_SPEED, PLANT_SPEED_ID, planted ? PLANT_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.JUMP_STRENGTH, PLANT_JUMP_ID, planted ? PLANT_JUMP : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, PLANT_ROOT_ID, planted ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		if (planted) {
			ServerLevel level = player.level();
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.plant").withStyle(ChatFormatting.GRAY));
			MagicFx.pool(level, player.position().add(0.0, 0.05, 0.0), 0.9F, 0xB8A888, 14);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.25F, 0.6F);
		}
	}

	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
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
	 * A blow on its way to {@code victim}, from the front of the damage chain ({@link Guard#riposte}): a planted guard
	 * (not a shield's, whose brace cuts it) lets only the rest through, and a knee-kicked foe takes more
	 * ({@link PressurePoints#exposed}).
	 */
	public static float adjust(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F) {
			return damage;
		}
		if (victim instanceof ServerPlayer player && planted(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !ShieldMoves.shieldUp(player)) {
			damage *= 1.0F - PLANT_CUT;
		}
		return PressurePoints.exposed(victim, damage);
	}

	// ---- hand back ----

	/** A melee blow of {@code amount} was parried by {@code player}: part of it goes back to {@code attacker}. */
	public static void handBack(ServerPlayer player, @Nullable LivingEntity attacker, float amount, boolean perfect) {
		if (handing || attacker == null || !attacker.isAlive() || player.isAlliedTo(attacker) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		float back = Math.min(HAND_BACK_CAP, amount * (perfect ? HAND_BACK_PERFECT_SHARE : HAND_BACK_SHARE));
		if (back <= 0.0F) {
			return;
		}
		handing = true;
		try {
			attacker.hurtServer(level, level.damageSources().thorns(player), back);
		} finally {
			handing = false;
		}
		MagicFx.blast(level, attacker.getBoundingBox().getCenter(), perfect ? 0.8F : 0.5F, 0xE8D8A0);
	}

	// ---- step ----

	private static void step(ServerPlayer player, State state, boolean guarding) {
		Input input = player.getLastClientInput();
		boolean jump = input.jump();
		boolean pressed = jump && !state.jumped;
		state.jumped = jump;
		long now = player.level().getGameTime();
		if (!pressed || !guarding || !player.onGround() || player.isShiftKeyDown() || player.isPassenger() || now < state.stepReady || state.planted) {
			return;
		}
		if (!Feathers.spend(player, STEP_FEATHERS)) {
			return;
		}
		state.stepReady = now + STEP_COOLDOWN;
		Vec3 back = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(-STEP_BACK);
		player.setDeltaMovement(back.x, STEP_UP, back.z);
		player.syncVelocity = true;
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, STEP_RESIST_TICKS, STEP_RESIST_AMP, false, false, false));
		ServerLevel level = player.level();
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 0.8F, 0xC8D0D8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.5F);
	}
}
