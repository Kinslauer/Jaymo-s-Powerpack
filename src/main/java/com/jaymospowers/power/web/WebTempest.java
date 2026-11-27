package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tendril tempest (Legends patch 4, {@code sy_tempest}): with the tendrils out, press the Tempest key and they snatch every
 * foe near you that is in the fight ({@link WebBlossom#fight}, nothing the tendrils cannot haul: {@link WebHaul#haulable},
 * so no bosses), reel them in for {@link #PULL_TICKS}, then fling them all up into the air at once. It is the two-foe slam
 * reflex ({@link WebSlamReflex}) for a crowd, on demand: it needs at least one foe, costs a little satiety, and no foe is
 * ever left held, stunned or without its mind: they only fly. It ends on death, downed, disconnect, a change of world and
 * power loss ({@link WebPose#TEMPEST} draws the body).
 */
public final class WebTempest {
	/** How far it reaches (blocks), how many it takes, how long the reel lasts (ticks), the wait after, the satiety. */
	static final double RANGE = 9.0;
	static final int MAX_FOES = 10, PULL_TICKS = 12, COOLDOWN = 220, COST = 6;
	/** The reel: top speed (blocks/tick), the share of the gap it asks for, how near they are held (blocks). */
	static final double PULL_SPEED = 0.7, PULL_SHARE = 0.35, HOLD = 1.8;
	/** The fling: upward speed, outward speed, damage, the tendrils' colour. */
	static final double FLING_UP = 1.1, FLING_OUT = 0.28;
	static final float FLING_DAMAGE = 3.0F;
	static final int TENDRIL_COLOR = 0x14141C;

	/** The key. */
	public record Press() implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("web_tempest"));
		public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(new Press());

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	private record Live(ServerLevel level, long start, List<Integer> foes) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebTempest() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, ctx) -> press(ctx.player()));
	}

	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && Powers.has(player, WebPower.INSTANCE)
			&& !Powers.silenced(player) && WebSymbiote.isBonded(player) && WebSymbiote.tendrilKeys(player) && !WebSymbiote.driving(player)
			&& !player.isPassenger();
	}

	private static void press(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (LIVE.containsKey(player.getUUID()) || !able(player) || now < READY.getOrDefault(player.getUUID(), 0L) || WebHeldGrab.holding(player)
			|| WebGrind.active(player) || WebPower.ragdolled(player)) {
			return;
		}
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(RANGE),
			e -> e.distanceToSqr(player) <= RANGE * RANGE && WebBlossom.fight(player, e) && WebHaul.haulable(e) && !WebHeldGrab.heldByAnyone(e)
				&& !WebTendrils.held(e)));
		if (found.isEmpty()) {
			return;
		}
		if (!player.isCreative()) {
			WebSymbioteState state = WebSymbiote.state(player);
			if (state.satiety() < COST) {
				return;
			}
			WebSymbiote.set(player, state.withSatiety(state.satiety() - COST));
		}
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : found.subList(0, Math.min(MAX_FOES, found.size()))) {
			ids.add(e.getId());
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		LIVE.put(player.getUUID(), new Live(level, now, ids));
		WebPower.pose(player, WebPose.TEMPEST, 1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = live.level();
		if (!able(player) || level != player.level()) {
			LIVE.remove(player.getUUID());
			return;
		}
		long t = level.getGameTime() - live.start();
		Vec3 back = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		if (t >= PULL_TICKS) {
			fling(player, live, back);
			LIVE.remove(player.getUUID());
			return;
		}
		for (int id : live.foes()) {
			if (!(level.getEntity(id) instanceof LivingEntity foe) || !foe.isAlive()) {
				continue;
			}
			Vec3 gap = player.position().subtract(foe.position());
			double d = gap.length();
			if (d > HOLD) {
				WebHaul.push(foe, gap.scale(Math.min(PULL_SPEED, (d - HOLD) * PULL_SHARE) / d));
				foe.resetFallDistance();
			}
			if (t % 2 == 0) {
				MagicFx.stroke(level, back, foe.getBoundingBox().getCenter(), 0.06F, TENDRIL_COLOR);
			}
		}
		if (t % 4 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.7F, 0.7F);
		}
	}

	/** All of them up at once. */
	private static void fling(ServerPlayer player, Live live, Vec3 back) {
		ServerLevel level = live.level();
		Vec3 at = player.position();
		for (int id : live.foes()) {
			if (!(level.getEntity(id) instanceof LivingEntity foe) || !foe.isAlive() || foe.distanceToSqr(player) > (RANGE + 2.0) * (RANGE + 2.0)) {
				continue;
			}
			Vec3 away = foe.position().subtract(at);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : flat.normalize();
			foe.hurtServer(level, level.damageSources().playerAttack(player), FLING_DAMAGE);
			if (foe.isAlive()) {
				WebHaul.push(foe, flat.scale(FLING_OUT).add(0.0, FLING_UP, 0.0));
			}
			MagicFx.stroke(level, back, foe.getBoundingBox().getCenter().add(0.0, 1.0, 0.0), 0.05F, TENDRIL_COLOR);
		}
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), 3.5F, 0x1B1B26);
		level.sendParticles(WebSymbiote.dust(1.6F), at.x, at.y + 1.0, at.z, 40, 0.8, 0.8, 0.8, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.6F);
		Shakes.around(level, at, 14.0, 0.6F);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
	}
}
