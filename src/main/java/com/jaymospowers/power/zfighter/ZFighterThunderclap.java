package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Bleeding;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Thunderclap, a quick technique (G): he claps his hands and the air cracks.
 * <ul>
 * <li><b>The clap</b>: a shockwave out to {@link #WAVE_RADIUS} blocks through everything alive near him that he may harm: thrown
 * back, a little hurt ({@link #WAVE_DAMAGE}, times Ki Power), their poise worn. A ring and a wave of ki roll out from his hands.</li>
 * <li><b>Boxing the ears</b>: with one foe in reach ({@link #CLOSE_RANGE}, the locked one else the nearest in front) the clap goes
 * on either side of the head instead. It hurts ({@link #EARS_DAMAGE}), makes them reel (nausea for {@link #NAUSEA_TICKS}), opens a
 * cut at the head (core.wounds.Bleeding) and, for a player, leaves them <b>deaf</b> for {@link #DEAF_TICKS}: every sound on their
 * side drops to a murmur under a high ringing tone ({@link Deaf}; the client's {@code ZFighterDeafClient} does the muffling).
 * A mob loses track of him.</li>
 * </ul>
 * Not to be confused with the Stormborn's lightning Thunderclap (power.heritage): this is the Z Fighter's own technique.
 */
public final class ZFighterThunderclap {
	static final float KI = 18.0F;
	static final int COOLDOWN = 200;

	static final double WAVE_RADIUS = 7.0;
	static final float WAVE_DAMAGE = 2.0F;
	static final double WAVE_PUSH = 1.3, WAVE_LIFT = 0.3;
	static final float WAVE_POISE = 6.0F;
	static final float WAVE_SHAKE = 0.6F;
	static final double SHAKE_RANGE = 16.0;

	static final double CLOSE_RANGE = 2.6;
	static final float EARS_DAMAGE = 3.0F;
	static final double EARS_PUSH = 0.5;
	static final int NAUSEA_TICKS = 160, DEAF_TICKS = 160, DAZE_TICKS = 40;
	static final float EARS_POISE = 10.0F;

	/** Server to a player: they are deaf for this many ticks (0 ends it). */
	public record Deaf(int ticks) implements CustomPacketPayload {
		public static final Type<Deaf> TYPE = new Type<>(JaymosPowers.id("zfighter_deaf"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Deaf> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Deaf::ticks, Deaf::new);

		@Override
		public Type<Deaf> type() {
			return TYPE;
		}
	}

	private ZFighterThunderclap() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Deaf.TYPE, Deaf.CODEC);
	}

	/** G: the clap. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		LivingEntity close = ZFighterArts.find(player, CLOSE_RANGE);
		if (close != null) {
			ears(player, live, close);
		} else {
			clap(player, live);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.6F);
		}
		return COOLDOWN;
	}

	private static void clap(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		int color = ZFighterArts.color(live);
		float power = ZFighterPower.kiPower(player, live);
		Vec3 hands = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).add(0.0, -0.5, 0.0);
		var source = ZFighterKi.source(player);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WAVE_RADIUS),
			e -> ZFighterArts.valid(player, e) && !(e instanceof ArmorStand))) {
			Vec3 away = near.position().subtract(player.position());
			if (away.horizontalDistance() > WAVE_RADIUS) {
				continue;
			}
			near.setInvulnerableTime(0);
			near.hurtServer(level, source, WAVE_DAMAGE * power);
			ZFighterKi.push(near, away, WAVE_PUSH, WAVE_LIFT);
			ZFighterPoise.wear(near, WAVE_POISE);
		}
		MagicFx.blast(level, hands, 1.4F, color);
		MagicFx.ring(level, player.position().add(0.0, 0.15, 0.0), (float)WAVE_RADIUS, color);
		MagicFx.ring(level, hands, null, (float)WAVE_RADIUS * 0.6F, 0xFFFFFF, 12);
		level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9F, 1.7F);
		level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 1.8F);
		Shakes.around(level, player.position(), SHAKE_RANGE, WAVE_SHAKE);
	}

	private static void ears(ServerPlayer player, ZFighterPower.Live live, LivingEntity foe) {
		ServerLevel level = player.level();
		int color = ZFighterArts.color(live);
		float power = ZFighterPower.kiPower(player, live);
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, ZFighterKi.source(player), EARS_DAMAGE * power);
		ZFighterKi.push(foe, foe.position().subtract(player.position()), EARS_PUSH, 0.1);
		ZFighterPoise.wear(foe, EARS_POISE);
		foe.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0), player);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE_TICKS, 1), player);
		Bleeding.open(foe, BodyPart.HEAD, false);
		if (foe instanceof Mob mob) {
			mob.setTarget(null);
		}
		if (foe instanceof ServerPlayer deafened) {
			ServerPlayNetworking.send(deafened, new Deaf(DEAF_TICKS));
		}
		Vec3 head = foe.getEyePosition();
		Vec3 right = player.getViewVector(1.0F).cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		for (int side = -1; side <= 1; side += 2) {
			MagicFx.blast(level, head.add(right.scale(0.35 * side)), 0.7F, color, 8);
		}
		MagicFx.ring(level, head, null, 1.6F, 0xFFFFFF, 10);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 2.0F);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		Shakes.around(level, head, 8.0, 0.35F);
	}
}
