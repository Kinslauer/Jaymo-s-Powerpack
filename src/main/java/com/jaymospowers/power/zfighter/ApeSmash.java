package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.16.6) Andy: "the ape should no longer swing and mine blocks like normal, instead their left click is now a
 * chargeable punch like darktide". As a Z Fighter's Great Ape, left click no longer swings or digs: hold it and the
 * ape draws a fist back (full at {@link #FULL_TICKS}), let go and it slams down where you look, a blast that hurts
 * and throws everything round the impact, wider and harder the longer it was held. Not in a fit (the ape has the
 * wheel) or crippled.
 */
public final class ApeSmash {
	/** Held this long, the punch is at full power; and the least of a quick click. */
	public static final int FULL_TICKS = 20;
	static final float MIN_CHARGE = 0.25F;
	/** Between punches (ticks). */
	static final int COOLDOWN = 12;
	/** Damage: the ape's attack damage times this, from a quick click to a full charge. */
	static final float DAMAGE_MIN = 0.8F, DAMAGE_MAX = 2.5F;
	/** The blast round the impact (blocks), and how hard it throws (outward, up). */
	static final double RADIUS_MIN = 3.0, RADIUS_MAX = 8.0, THROW = 1.4, LIFT = 0.6;
	static final float SHAKE_MIN = 0.4F, SHAKE_MAX = 1.2F;
	static final double SHAKE_RANGE = 40.0;
	/** How long the blow's swing shows (ticks). */
	public static final int SWING_TICKS = 6;

	/** When he began drawing back (game time), while he is; everyone's client poses it. */
	public static final AttachmentType<Long> CHARGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_charge"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** When the last blow landed (game time). */
	public static final AttachmentType<Long> SWUNG = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_swung"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Left mouse down (true) or let go (false), as the Great Ape. */
	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("zfighter_ape_smash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Key> CODEC = CustomPacketPayload.codec((payload, buf) -> buf.writeBoolean(payload.down()),
			buf -> new Key(buf.readBoolean()));

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private ApeSmash() {
	}

	/** Whether he can punch now: his own Great Ape, standing, not in a fit. */
	static boolean able(ServerPlayer player) {
		return Powers.has(player, ZFighterPower.INSTANCE) && ZFighterLineage.ape(player) && !ZFighterLineage.crippled(player)
			&& !player.hasAttached(ApeRage.FIT) && !player.hasAttached(ZFighterLineage.BERSERK);
	}

	static void key(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		Long start = player.getAttached(CHARGE);
		if (down) {
			Long swung = player.getAttached(SWUNG);
			if (start == null && able(player) && (swung == null || now - swung >= COOLDOWN)) {
				player.setAttached(CHARGE, now);
				player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.5F);
			}
			return;
		}
		if (start == null) {
			return;
		}
		player.removeAttached(CHARGE);
		if (able(player)) {
			smash(player, Mth.clamp((now - start) / (float)FULL_TICKS, MIN_CHARGE, 1.0F));
		}
	}

	/** From the ape's tick: let go of a charge he can't keep; a growl when it's full. */
	static void tick(ServerPlayer player, long now) {
		Long start = player.getAttached(CHARGE);
		if (start == null) {
			return;
		}
		if (!able(player)) {
			player.removeAttached(CHARGE);
		} else if (now - start == FULL_TICKS) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 1.5F, 0.6F);
		}
	}

	/** The blow: where he's looking (as far as he reaches), a blast {@code charge} (0-1) strong. */
	private static void smash(ServerPlayer player, float charge) {
		ServerLevel level = player.level();
		player.setAttached(SWUNG, level.getGameTime());
		Vec3 eyes = player.getEyePosition();
		Vec3 end = eyes.add(player.getLookAngle().scale(player.entityInteractionRange()));
		HitResult hit = level.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		if (charge >= 1.0F) {
			ZFighterSenses.impactFrame(level, at);
		}
		double radius = Mth.lerp(charge, RADIUS_MIN, RADIUS_MAX);
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !player.isPassengerOfSameVehicle(e)
				&& !(e instanceof Player other && other.isCreative()))) {
			double distance = victim.position().distanceTo(at);
			if (distance > radius + victim.getBbWidth()) {
				continue;
			}
			float falloff = (float)(1.0 - 0.5 * Math.min(1.0, distance / radius));
			if (victim.hurtServer(level, ZFighterLineage.blow(level, player), damage * falloff)) {
				Vec3 away = victim.position().subtract(at).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : away.normalize();
				victim.push(away.x * THROW * charge * falloff, LIFT * charge, away.z * THROW * charge * falloff);
			}
		}
		// the ground blown out round the fist
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = level.getBlockState(BlockPos.containing(at));
		}
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, at.x, at.y + 0.2, at.z,
				(int)(40 + 80 * charge), radius * 0.4, 0.3, radius * 0.4, 0.4);
		}
		level.sendParticles(charge >= 1.0F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, true, false, at.x, at.y + 0.5, at.z,
			charge >= 1.0F ? 1 : 3, radius * 0.3, 0.2, radius * 0.3, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, at.x, at.y + 0.2, at.z, (int)(6 + 10 * charge), radius * 0.3, 0.1,
			radius * 0.3, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F + 2.0F * charge, 0.6F - 0.2F * charge);
		ZFighterLineage.shake(player, at, SHAKE_RANGE, Mth.lerp(charge, SHAKE_MIN, SHAKE_MAX));
	}
}
