package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.smoke.SmokeField;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gale Stream (sneak + hold X): a steady stream of wind out of your hands along where you look, for as long as you
 * hold it and have the breath (food) for it. It blows smoke out of its way and thins it, snuffs every fire it passes
 * through, shoves creatures and loose things along, and fills a ship's sails (Jaymo's Kingdoms reads the
 * {@link #KINGDOMS_TAG} and pushes the ship). Everyone in view sees the stream ({@link #JETTING}; the client draws it).
 */
public final class WindJet {
	/** How far the stream reaches (blocks), how wide it is at your hands and how much wider a block further. */
	public static final double RANGE = 18.0, MOUTH = 0.35, SPREAD = 0.12;
	/** Its push on what it catches (blocks/tick a tick, near you; nothing at its far end), and the most it gives. */
	static final double PUSH = 0.09, MOST = 1.0;
	/** How much of the smoke in its way it blows on, and thins, each tick. */
	static final float THIN = 0.25F;
	/** Hunger a tick; at or below {@link WindPower#CRUISE_MIN_FOOD} food you're too winded to keep it up. */
	static final float EXHAUSTION = 0.03F;
	/** Snuff fire every this many ticks (it scans blocks). */
	static final int DOUSE_EVERY = 2;
	/** Jaymo's Kingdoms reads this tag: a stream of wind along the bearer's look fills sails. */
	public static final String KINGDOMS_TAG = "jaymospowers_jet:wind";

	/** On a player streaming wind: synced to everyone, who see it. */
	public static final AttachmentType<Boolean> JETTING = AttachmentRegistry.create(JaymosPowers.id("wind_jet"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client → server: sneak + X held ({@code down}), or let go. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("wind_jet"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private static final Set<UUID> ACTIVE = new HashSet<>();

	private WindJet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			if (payload.down()) {
				start(ctx.player());
			} else {
				stop(ctx.player());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
	}

	public static boolean isActive(Player player) {
		return Boolean.TRUE.equals(player.getAttached(JETTING));
	}

	static void start(ServerPlayer player) {
		if (!Powers.has(player, WindPower.INSTANCE) || !player.isAlive() || player.isSpectator() || winded(player)) {
			return;
		}
		ACTIVE.add(player.getUUID());
		player.setAttached(JETTING, true);
		player.addTag(KINGDOMS_TAG);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	static void stop(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		player.removeAttached(JETTING);
		player.removeTag(KINGDOMS_TAG);
	}

	private static boolean winded(ServerPlayer player) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= WindPower.CRUISE_MIN_FOOD;
	}

	/** From the power's tick. */
	static void tick(ServerPlayer player) {
		if (!ACTIVE.contains(player.getUUID())) {
			if (player.entityTags().contains(KINGDOMS_TAG)) {
				// left over from a crash: nobody's streaming
				stop(player);
			}
			return;
		}
		if (!player.isAlive() || player.isSpectator() || winded(player)) {
			stop(player);
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(look.scale(0.6));
		double reach = reach(level, player, from, look, RANGE);
		Vec3 to = from.add(look.scale(reach));
		// the smoke in its way is blown on and thinned; the fire in its way goes out
		boolean douse = player.tickCount % DOUSE_EVERY == 0;
		for (double t = 0.0; t <= reach; t += 1.5) {
			Vec3 p = from.add(look.scale(t));
			double r = MOUTH + t * SPREAD + 0.5;
			SmokeField.blow(level, p, r, look.scale(2.0));
			SmokeField.thin(level, p, r, THIN);
			if (douse) {
				WindDouse.douse(level, p, r, player);
			}
		}
		// what it catches is shoved along it, less the further off
		AABB box = new AABB(from, to).inflate(MOUTH + reach * SPREAD);
		for (Entity e : level.getEntities(player, box, e -> e.isAlive() && !e.isSpectator() && !e.noPhysics && e != player.getVehicle())) {
			Vec3 c = e.getBoundingBox().getCenter().subtract(from);
			double t = c.dot(look);
			if (t < 0.0 || t > reach || c.subtract(look.scale(t)).length() > MOUTH + t * SPREAD + e.getBbWidth() * 0.5) {
				continue;
			}
			double resist = e instanceof LivingEntity living ? 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 1.0;
			double push = PUSH * (1.0 - t / RANGE) * resist;
			if (push <= 0.0) {
				continue;
			}
			Vec3 v = e.getDeltaMovement().add(look.scale(push));
			if (v.length() > MOST) {
				v = v.normalize().scale(MOST);
			}
			e.setDeltaMovement(v);
			e.syncVelocity = true;
			e.fallDistance = 0.0;
		}
		player.causeFoodExhaustion(EXHAUSTION);
		if (player.tickCount % 16 == 0) {
			level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.8F,
				1.4F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/** How far a stream from {@code from} along {@code look} gets before a wall stops it (at most {@code range}). */
	public static double reach(ServerLevel level, Entity by, Vec3 from, Vec3 look, double range) {
		HitResult hit = level.clip(new ClipContext(from, from.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, by));
		return hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(from);
	}
}
