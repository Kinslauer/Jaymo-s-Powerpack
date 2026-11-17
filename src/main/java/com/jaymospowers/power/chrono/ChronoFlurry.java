package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-17): "chronos should also get a flurry punch combo when they hold left click while overclocked". Held
 * attack ({@link #HOLD_TICKS} on the client, never at a block, which is mining) while Overclock runs: a string of
 * {@link #JABS} jabs from alternating fists, one every {@link #JAB_EVERY} ticks, each a little harder than the last, into
 * the nearest creature in front of you, with your fists (an empty main hand or boxing gear: {@link #inHand}; a melee weapon swings instead, below); then an uppercut that throws it ({@link #FINISH_WINDUP} ticks to land); then a
 * breath ({@link #REST} ticks) and the next string, for as long as the button is held. Hits grow with mastery. Not downed,
 * not in Time Flow (which has its own blows), and it ends with Overclock. The client says when the hold starts and stops
 * ({@link Hold}).
 *
 * <p>Andy (2026-11-17): "Melee weapons should also swing fast but only deal damage once every 4 ticks". With a melee
 * weapon in hand (anything with attack damage on it), the held attack swings it every {@link #JAB_EVERY} ticks, but only
 * every {@link #WEAPON_HIT_EVERY} ticks does a swing land: a full-strength attack of the weapon (its damage,
 * enchantments, sweep and wear) on the nearest one in front. Meanwhile no other melee hit of yours lands, so the held
 * swings can't be stacked with clicks.
 */
public final class ChronoFlurry {
	// ---- tuning ----
	/** Ticks of held attack before the flurry starts (a click stays a click). */
	public static final int HOLD_TICKS = 5;
	static final int JAB_EVERY = 2, JABS = 10, FINISH_WINDUP = 4, REST = 6;
	/** With a melee weapon: one swing in this many ticks lands. */
	static final int WEAPON_HIT_EVERY = 4;
	/** Reach (blocks, eyes to the target's middle) and the cosine of how wide either side of your look it finds one. */
	static final double REACH = 3.0, CONE = 0.5;
	/** A jab: base damage, plus this per ability mastered; each jab of the string lands this share harder than the first. */
	static final float JAB_BASE = 0.5F, JAB_PER_LEVEL = 0.15F, RAMP = 0.08F;
	/** The uppercut: this many times a jab of the string's end, and its throw. */
	static final float FINISH_MULTIPLIER = 3.0F;
	static final double FINISH_PUSH = 0.9, FINISH_LIFT = 0.55;
	static final float SHAKE = 0.08F, FINISH_SHAKE = 0.6F;
	static final int STROKE_COLOR = 0xFFE08A;
	private static final SwingAnimation JAB = new SwingAnimation(SwingAnimationType.WHACK, JAB_EVERY);
	private static final SwingAnimation UPPERCUT = new SwingAnimation(SwingAnimationType.WHACK, FINISH_WINDUP + 2);

	/** The client: the held attack has started (true) or stopped (false). */
	public record Hold(boolean on) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("chrono_flurry"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::on);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	/** Ticks into the current string, per flurrying player. */
	private static final Map<UUID, Integer> RUNS = new HashMap<>();
	/** A weapon flurry's own landing blow is under way (any other melee hit of a weapon flurrier is refused). */
	private static boolean landing;

	private ChronoFlurry() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> hold(ctx.player(), payload.on()));
		ServerTickEvents.END_SERVER_TICK.register(ChronoFlurry::tick);
		// a weapon flurry lands once every WEAPON_HIT_EVERY ticks, whatever else is clicked or swung meanwhile
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			landing || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
				|| !RUNS.containsKey(player.getUUID()) || !weapon(player.getMainHandItem()));
	}

	private static void hold(ServerPlayer player, boolean on) {
		if (on && able(player)) {
			RUNS.putIfAbsent(player.getUUID(), 0);
		} else {
			RUNS.remove(player.getUUID());
		}
	}

	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && ChronoPower.overclocked(player) && !Downed.isDowned(player)
			&& !ChronoFlow.on(player) && inHand(player);
	}

	/** A flurry wants fists (an empty main hand, or boxing gear on it) or a melee weapon. */
	public static boolean inHand(net.minecraft.world.entity.player.Player player) {
		net.minecraft.world.item.ItemStack held = player.getMainHandItem();
		return held.isEmpty() || com.jaymospowers.boxing.BoxingGear.isGear(held) || weapon(held);
	}

	/** A melee weapon (not boxing gear): anything that adds attack damage in the hand. */
	static boolean weapon(net.minecraft.world.item.ItemStack held) {
		return !held.isEmpty() && !com.jaymospowers.boxing.BoxingGear.isGear(held)
			&& held.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
				net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
				.anyMatch(e -> e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
	}

	static void stop(ServerPlayer player) {
		RUNS.remove(player.getUUID());
	}

	static void clear() {
		RUNS.clear();
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Integer>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !able(player)) {
				it.remove();
				continue;
			}
			int t = entry.getValue();
			if (weapon(player.getMainHandItem())) {
				if (t % JAB_EVERY == 0) {
					swingWeapon(player, t % WEAPON_HIT_EVERY == 0);
				}
				entry.setValue((t + 1) % (JAB_EVERY * WEAPON_HIT_EVERY));
				continue;
			}
			int jabsEnd = JABS * JAB_EVERY;
			if (t < jabsEnd && t % JAB_EVERY == 0) {
				jab(player, t / JAB_EVERY);
			} else if (t == jabsEnd) {
				// the uppercut winds up
				player.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.UPPERCUT, player.level().getGameTime(), -1, 1));
				player.swing(InteractionHand.MAIN_HAND, UPPERCUT, true);
			} else if (t == jabsEnd + FINISH_WINDUP) {
				finish(player);
			}
			entry.setValue(t + 1 >= jabsEnd + FINISH_WINDUP + REST ? 0 : t + 1);
		}
	}

	/** One swing of a held weapon; {@code lands}, it strikes as a fully charged attack. */
	private static void swingWeapon(ServerPlayer player, boolean lands) {
		player.swing(InteractionHand.MAIN_HAND, JAB, true);
		ServerLevel level = player.level();
		LivingEntity target = lands ? target(player) : null;
		if (target != null) {
			target.setInvulnerableTime(0);
			((com.jaymospowers.power.chrono.mixin.ChronoAttackStrengthAccessor) player).jaymospowers$setAttackStrengthTicker(1000);
			landing = true;
			try {
				player.attack(target);
			} finally {
				landing = false;
			}
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35F, 1.6F);
		}
	}

	private static float jabDamage(ServerPlayer player, int n) {
		return (JAB_BASE + JAB_PER_LEVEL * com.jaymospowers.core.Mastery.unlocked(player)) * (1.0F + RAMP * n);
	}

	private static void jab(ServerPlayer player, int n) {
		ServerLevel level = player.level();
		int side = n % 2 == 0 ? 1 : -1;
		player.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.RAPID, level.getGameTime(), -1, side));
		player.swing(side > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, JAB, true);
		Vec3 look = player.getLookAngle();
		Vec3 fist = player.getEyePosition().add(look.scale(1.2)).add(0.0, -0.3, 0.0);
		Vec3 across = new Vec3(-look.z, 0.0, look.x);
		if (across.lengthSqr() < 1.0E-6) {
			across = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 from = fist.add(across.normalize().scale(0.2 * side));
		MagicFx.stroke(level, from, from.add(look.scale(0.6)), 0.25F, STROKE_COLOR);
		LivingEntity target = target(player);
		boolean landed = target != null && strike(player, target, jabDamage(player, n));
		if (landed) {
			Vec3 at = target.getBoundingBox().getCenter();
			// held in the moment: the jabs pin them where they stand
			target.setDeltaMovement(target.getDeltaMovement().scale(0.2));
			target.syncVelocity = true;
			ChronoFx.particles(level, player, ParticleTypes.CRIT, at.x, at.y, at.z, 2, 0.2, 0.25, 0.2, 0.2);
			com.jaymospowers.core.Shakes.send(player, SHAKE);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), landed ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
			SoundSource.PLAYERS, 0.5F, 1.2F + n * 0.04F);
	}

	private static void finish(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = target(player);
		boolean landed = target != null && strike(player, target, jabDamage(player, JABS - 1) * FINISH_MULTIPLIER);
		if (landed) {
			Vec3 to = target.position().subtract(player.position()).horizontal();
			Vec3 away = to.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().normalize() : to.normalize();
			target.setDeltaMovement(away.scale(FINISH_PUSH).add(0.0, FINISH_LIFT, 0.0));
			target.syncVelocity = true;
			ChronoFlow.reel(target, ChronoFlow.FLUNG, 0);
			Vec3 at = target.getBoundingBox().getCenter();
			ChronoFx.crackSound(level, player, at.x, at.y, at.z);
			ChronoFx.particles(level, player, MagicFx.spark(STROKE_COLOR, 1.0F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
			MagicFx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.2F, 0xFFFFFF);
			com.jaymospowers.core.Shakes.send(player, FINISH_SHAKE);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	/** A hit through any hit immunity (the jabs come faster than it lasts). True if it hurt. */
	private static boolean strike(ServerPlayer player, LivingEntity target, float damage) {
		target.setInvulnerableTime(0);
		return target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
	}

	/** The nearest creature in front of you, in reach and in sight; never a friend or your own pet. */
	private static @Nullable LivingEntity target(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !player.isAlliedTo(e)
				&& !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > REACH + e.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < CONE
				|| distance >= bestDistance || !player.hasLineOfSight(e)) {
				continue;
			}
			best = e;
			bestDistance = distance;
		}
		return best;
	}
}
