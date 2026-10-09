package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tempest Kick (the power's special key, X; every ability mastered): a roundhouse kick that throws a white crescent of
 * wind out sideways-flat ahead of you. It flies {@link #RANGE} blocks for {@link #DAMAGE}; ascended (the Tempest),
 * {@link #RANGE_ASCENDED} blocks for {@link #DAMAGE_ASCENDED}. It cuts through everything in its path (each once) and
 * stops at walls.
 */
public final class WindKick {
	static final double RANGE = 5.0, RANGE_ASCENDED = 20.0;
	static final float DAMAGE = 6.0F, DAMAGE_ASCENDED = 12.0F;
	/** The crescent: how fast it flies (blocks a tick), how wide it reaches either side, and how tall. */
	static final double SPEED = 1.6, HALF_WIDTH = 1.8, HALF_HEIGHT = 0.7;
	static final double PUSH = 0.8, LIFT = 0.25;
	static final int COOLDOWN = 60, COOLDOWN_ASCENDED = 40;
	/** How long the kick itself is drawn (ticks). */
	public static final int KICK_TICKS = 9;
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 1.4F);

	/** On a kicking player: when the kick began (game time). Synced to everyone, who see the roundhouse. */
	public static final AttachmentType<Long> KICKING = AttachmentRegistry.create(JaymosPowers.id("wind_kick"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Client → server: X pressed. */
	public record Kick() implements CustomPacketPayload {
		public static final Type<Kick> TYPE = new Type<>(JaymosPowers.id("wind_kick"));
		public static final StreamCodec<ByteBuf, Kick> CODEC = StreamCodec.unit(new Kick());

		@Override
		public Type<Kick> type() {
			return TYPE;
		}
	}

	private static final class Crescent {
		final ResourceKey<Level> dimension;
		final UUID owner;
		Vec3 pos;
		final Vec3 dir, side;
		final double range;
		final float damage;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		Crescent(ResourceKey<Level> dimension, UUID owner, Vec3 pos, Vec3 dir, Vec3 side, double range, float damage) {
			this.dimension = dimension;
			this.owner = owner;
			this.pos = pos;
			this.dir = dir;
			this.side = side;
			this.range = range;
			this.damage = damage;
		}
	}

	private static final List<Crescent> CRESCENTS = new ArrayList<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private WindKick() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Kick.TYPE, Kick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Kick.TYPE, (payload, ctx) -> kick(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(WindKick::tick);
	}

	private static void kick(ServerPlayer player) {
		if (!Powers.has(player, WindPower.INSTANCE) || Mastery.unlocked(player) < Mastery.ALL || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		boolean ascended = Fate.isAscended(player);
		READY_AT.put(player.getUUID(), now + (ascended ? COOLDOWN_ASCENDED : COOLDOWN));
		player.setAttached(KICKING, now);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
		Vec3 start = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0).add(flat.scale(0.8));
		CRESCENTS.add(new Crescent(level.dimension(), player.getUUID(), start, flat, side, ascended ? RANGE_ASCENDED : RANGE,
			(ascended ? DAMAGE_ASCENDED : DAMAGE) * WindModes.abilityScale(player)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Crescent> it = CRESCENTS.iterator(); it.hasNext(); ) {
			Crescent c = it.next();
			ServerLevel level = server.getLevel(c.dimension);
			ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
			if (level == null || c.travelled >= c.range) {
				it.remove();
				continue;
			}
			Vec3 next = c.pos.add(c.dir.scale(SPEED));
			if (!level.noCollision(new AABB(next, next).inflate(0.25))) {
				// a wall: it bursts against it
				level.sendParticles(ParticleTypes.GUST, c.pos.x, c.pos.y, c.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
				it.remove();
				continue;
			}
			c.pos = next;
			c.travelled += SPEED;
			// a flat crescent, swept back at its tips
			for (int i = -6; i <= 6; i++) {
				double t = i / 6.0;
				Vec3 at = c.pos.add(c.side.scale(t * HALF_WIDTH)).subtract(c.dir.scale(t * t * 0.9));
				level.sendParticles(WHITE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.CLOUD, c.pos.x, c.pos.y, c.pos.z, 2, HALF_WIDTH * 0.4, 0.1, HALF_WIDTH * 0.4, 0.01);
			AABB box = new AABB(c.pos, c.pos).inflate(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && e != owner && !c.hit.contains(e.getId()))) {
				c.hit.add(target.getId());
				target.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(), c.damage);
				target.setDeltaMovement(target.getDeltaMovement().add(c.dir.scale(PUSH)).add(0.0, LIFT, 0.0));
				target.syncVelocity = true;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		READY_AT.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) == null);
	}
}
