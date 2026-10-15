package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
 * Smoke Jet (hold X): a hard jet of smoke out of your hands along where you look, for as long as you hold it and have
 * the food for it. The jet is the real gas ({@link SmokeField}): it's laid along the way, thickest where it ends, so it
 * lingers and drifts like any of your smoke, and the smoke already in its way is driven on. It hurts and throws back
 * what it catches, and fills a ship's sails, more gently than a Wind user's gale (Jaymo's Kingdoms reads the {@link #KINGDOMS_TAG}).
 * Everyone in view sees it ({@link #JETTING}; the client draws it).
 */
public final class SmokeJet {
	/** How far the jet reaches (blocks), how wide it is at your hands and how much wider a block further. */
	public static final double RANGE = 14.0, MOUTH = 0.3, SPREAD = 0.14;
	/** Smoke laid along it a tick (a block's worth is 1), and every how many blocks. */
	static final float POUR = 1.5F;
	static final double STEP = 2.0;
	/** Its push on what it catches (blocks/tick a tick, near you; nothing at its far end), and the most it gives. */
	static final double PUSH = 0.09, MOST = 1.1;
	/** It lifts what it shoves a little, so they're thrown back rather than dragged. */
	static final double LIFT = 0.25;
	/** Damage to the living it catches, every {@link #HIT_EVERY} ticks: full at your hands, half at its far end. */
	static final float DAMAGE = 2.0F;
	static final int HIT_EVERY = 10;
	/** Hunger a tick; at or below this food you can't keep it up. */
	static final float EXHAUSTION = 0.025F;
	static final int MIN_FOOD = 6;
	/** Corrupted (Soot Jet): how long what it catches is blinded. */
	static final int SOOT_BLIND_TICKS = 40;
	/** Jaymo's Kingdoms reads this tag: a jet of smoke along the bearer's look fills sails. */
	public static final String KINGDOMS_TAG = "jaymospowers_jet:smoke";

	/** On a player jetting smoke: synced to everyone, who see it. */
	public static final AttachmentType<Boolean> JETTING = AttachmentRegistry.create(JaymosPowers.id("smoke_jet"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client → server: X held ({@code down}), or let go. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("smoke_jet"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private static final Set<UUID> ACTIVE = new HashSet<>();

	private SmokeJet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			if (payload.down() && ctx.player().isShiftKeyDown()) {
				// Sneak + X steps into one of your clouds instead
				SmokeStep.step(ctx.player());
			} else if (payload.down()) {
				start(ctx.player());
			} else {
				stop(ctx.player());
			}
		});
	}

	public static boolean isActive(Player player) {
		return Boolean.TRUE.equals(player.getAttached(JETTING));
	}

	static void start(ServerPlayer player) {
		if (!Powers.has(player, SmokePower.INSTANCE) || !player.isAlive() || player.isSpectator() || spent(player) || SmokeVapour.wholly(player)) {
			return;
		}
		ACTIVE.add(player.getUUID());
		player.setAttached(JETTING, true);
		player.addTag(KINGDOMS_TAG);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	static void stop(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		player.removeAttached(JETTING);
		player.removeTag(KINGDOMS_TAG);
	}

	static void clear() {
		ACTIVE.clear();
	}

	private static boolean spent(ServerPlayer player) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= MIN_FOOD;
	}

	/** From the power's tick. */
	static void tick(ServerPlayer player) {
		if (!ACTIVE.contains(player.getUUID())) {
			if (player.entityTags().contains(KINGDOMS_TAG)) {
				// left over from a crash: nobody's jetting
				stop(player);
			}
			return;
		}
		if (!player.isAlive() || player.isSpectator() || spent(player)) {
			stop(player);
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(look.scale(0.6));
		HitResult hit = level.clip(new ClipContext(from, from.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(from);
		// laid along the way, more of it the further it gets (where the jet slows and billows); what's there is driven on
		int n = Math.max(1, (int)Math.ceil(reach / STEP));
		float weights = n * (n + 1) / 2.0F;
		for (int i = 1; i <= n; i++) {
			Vec3 p = from.add(look.scale(Math.min(reach - 0.3, i * STEP)));
			double r = MOUTH + i * STEP * SPREAD + 0.5;
			SmokeField.blow(level, p, r, look.scale(1.5));
			SmokeField.puff(level, p, POUR * i / weights, player);
		}
		boolean soot = com.jaymospowers.core.Fate.isCorrupted(player);
		AABB box = new AABB(from, from.add(look.scale(reach))).inflate(MOUTH + reach * SPREAD);
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
			Vec3 v = e.getDeltaMovement().add(look.scale(push)).add(0.0, push * LIFT, 0.0);
			if (v.length() > MOST) {
				v = v.normalize().scale(MOST);
			}
			e.setDeltaMovement(v);
			e.syncVelocity = true;
			if (e instanceof LivingEntity living && player.tickCount % HIT_EVERY == 0) {
				living.hurtServer(level, player.damageSources().playerAttack(player), DAMAGE * (float)(1.0 - 0.5 * t / RANGE));
			}
			if (soot && e instanceof LivingEntity living && player.tickCount % 10 == 0) {
				// Soot Jet (smog): it blinds what it catches
				living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, SOOT_BLIND_TICKS, 0), player);
			}
		}
		player.causeFoodExhaustion(EXHAUSTION);
		if (player.tickCount % 10 == 0) {
			level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F,
				0.4F + level.getRandom().nextFloat() * 0.15F);
		}
	}
}
