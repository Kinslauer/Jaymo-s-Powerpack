package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The bracers, three things on top of the guard ({@link ZFighterGuard}, right click held, forearms crossed):
 * <ul>
 * <li><b>Soaking</b>: the damage the guard takes (what it blocks, and the whole of a parried blow) fills a bar, up to
 * {@link #SOAK_CAP}, shown in the action bar. When the guard drops the bar pays out as a boost to his damage:
 * {@link #BOOST_PER_POINT} a point soaked, at most {@link #BOOST_CAP}, for {@link #BOOST_TICKS}, if at least
 * {@link #MIN_PAYOUT} was soaked ({@link ZFighterGuard#counter} applies it).</li>
 * <li><b>Clash</b>: attack (left click) while the guard is up and the bracers are struck together: a ring of force that throws back
 * everything within {@link #CLASH_RADIUS} in front of him and hurts it a little. {@link #CLASH_KI} ki, {@link #CLASH_COOLDOWN} ticks.</li>
 * <li><b>Turning shots back</b>: an arrow that strikes the raised guard is sent back at whoever loosed it (free in the first
 * ticks of the guard, which is the parry; later {@link #DEFLECT_KI} ki each). Other shots the guard still only half-blocks; they
 * can't be sent back.</li>
 * </ul>
 */
public final class ZFighterBracers {
	static final float SOAK_CAP = 24.0F, MIN_PAYOUT = 3.0F;
	static final float BOOST_PER_POINT = 0.02F, BOOST_CAP = 0.5F;
	static final int BOOST_TICKS = 100;

	static final float CLASH_KI = 8.0F;
	static final int CLASH_COOLDOWN = 30;
	static final double CLASH_RADIUS = 3.4, CLASH_ARC_DEGREES = 140.0, CLASH_PUSH = 1.4, CLASH_LIFT = 0.25;
	static final float CLASH_DAMAGE = 2.0F, CLASH_POISE = 14.0F, CLASH_SHAKE = 0.4F;

	static final float DEFLECT_KI = 4.0F;
	static final double DEFLECT_MIN_SPEED = 1.2, DEFLECT_SPEED = 1.0, DEFLECT_SCATTER = 2.0;

	/** Client to server: the bracers are struck together (left click while guarding). */
	public record Clash() implements CustomPacketPayload {
		public static final Type<Clash> TYPE = new Type<>(JaymosPowers.id("zfighter_bracer_clash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Clash> CODEC = StreamCodec.unit(new Clash());

		@Override
		public Type<Clash> type() {
			return TYPE;
		}
	}

	private static final class Bar {
		float soaked;
		long boostUntil, clashReadyAt;
		float boost;
	}

	private static final Map<UUID, Bar> BARS = new HashMap<>();

	private ZFighterBracers() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Clash.TYPE, Clash.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Clash.TYPE, (payload, ctx) -> clash(ctx.player()));
	}

	static void clear() {
		BARS.clear();
	}

	/** The guard took {@code amount}: the bar fills. */
	static void soak(ServerPlayer player, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		Bar bar = BARS.computeIfAbsent(player.getUUID(), id -> new Bar());
		bar.soaked = Math.min(SOAK_CAP, bar.soaked + amount);
		int filled = Mth.clamp(Math.round(bar.soaked / SOAK_CAP * 10.0F), 0, 10);
		player.sendOverlayMessage(Component.literal("[" + "|".repeat(filled) + ".".repeat(10 - filled) + "]").withStyle(ChatFormatting.GOLD));
	}

	/** The guard came down: what it soaked pays out as a boost. */
	static void released(ServerPlayer player) {
		Bar bar = BARS.get(player.getUUID());
		if (bar == null || bar.soaked < MIN_PAYOUT) {
			if (bar != null) {
				bar.soaked = 0.0F;
			}
			return;
		}
		bar.boost = Math.min(BOOST_CAP, bar.soaked * BOOST_PER_POINT);
		bar.boostUntil = player.level().getGameTime() + BOOST_TICKS;
		bar.soaked = 0.0F;
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.ring(level, at, null, 1.8F, ZFighterPower.kiColor(player), 12);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.bracer_boost", Math.round(bar.boost * 100.0F)).withStyle(ChatFormatting.GOLD));
	}

	/** He lost the power, died or left. */
	static void dropped(ServerPlayer player) {
		BARS.remove(player.getUUID());
	}

	/** What his damage is multiplied by from a paid-out bar (1 when none). */
	static float boost(ServerPlayer player, long now) {
		Bar bar = BARS.get(player.getUUID());
		return bar != null && now < bar.boostUntil ? 1.0F + bar.boost : 1.0F;
	}

	/**
	 * An arrow struck the raised guard outside the parry: sent back for {@link #DEFLECT_KI} ki. False if he can't pay (the guard then
	 * just half-blocks it).
	 */
	static boolean turnBack(ServerPlayer player, ZFighterPower.Live live, AbstractArrow arrow, float damage) {
		if (!player.isCreative() && !live.raging() && live.ki < DEFLECT_KI * ZFighterAndroid.costFactor(player)) {
			return false;
		}
		if (!ZFighterPower.spend(player, live, DEFLECT_KI)) {
			return false;
		}
		sendBack(player, arrow);
		soak(player, damage);
		return true;
	}

	/** The arrow is spent on the guard and one like it flies back at whoever loosed it. */
	static void sendBack(ServerPlayer player, AbstractArrow shot) {
		ServerLevel level = player.level();
		Entity shooter = shot.getOwner();
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		Vec3 dir = shooter != null && shooter != player && shooter.isAlive()
			? shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0).subtract(from).normalize() : player.getLookAngle();
		net.minecraft.world.entity.projectile.arrow.Arrow back = new net.minecraft.world.entity.projectile.arrow.Arrow(level, player,
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
		back.setPos(from);
		back.pickup = AbstractArrow.Pickup.DISALLOWED;
		back.shoot(dir.x, dir.y + 0.05, dir.z, (float)Math.max(DEFLECT_MIN_SPEED, shot.getDeltaMovement().length() * DEFLECT_SPEED), (float)DEFLECT_SCATTER);
		level.addFreshEntity(back);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 2.0F);
		MagicFx.blast(level, from, 0.5F, ZFighterPower.kiColor(player), 6);
	}

	/** Left click with the guard up: the clash. */
	private static void clash(ServerPlayer player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Bar bar = BARS.computeIfAbsent(player.getUUID(), id -> new Bar());
		if (!live.guarding || now < bar.clashReadyAt || !ZFighterPower.spend(player, live, CLASH_KI)) {
			return;
		}
		bar.clashReadyAt = now + CLASH_COOLDOWN;
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		double cos = Math.cos(Math.toRadians(CLASH_ARC_DEGREES / 2.0));
		float power = ZFighterPower.kiPower(player, live);
		var source = ZFighterKi.source(player);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CLASH_RADIUS),
			e -> ZFighterArts.valid(player, e) && !(e instanceof ArmorStand))) {
			Vec3 to = near.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double distance = to.length();
			if (distance > CLASH_RADIUS || distance > 0.3 && to.scale(1.0 / distance).dot(look) < cos) {
				continue;
			}
			near.setInvulnerableTime(0);
			near.hurtServer(level, source, CLASH_DAMAGE * power);
			ZFighterKi.push(near, to, CLASH_PUSH, CLASH_LIFT);
			ZFighterPoise.wear(near, CLASH_POISE);
		}
		Vec3 at = player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.5, 0.0);
		int color = ZFighterPower.kiColor(player);
		MagicFx.blast(level, at, 1.0F, color, 8);
		MagicFx.ring(level, at, look, (float)CLASH_RADIUS * 0.7F, color, 10);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
		Shakes.around(level, at, 8.0, CLASH_SHAKE);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}
}
