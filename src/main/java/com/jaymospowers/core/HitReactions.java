package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.BodyPart;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a hit lands on a body: the part that was struck flinches (client {@code HitReactionsClient}), by how hard the
 * blow was for a body that size, and a light mob is shoved a little. Called from {@code Wounds} with the part its hit
 * routing found, once the hit has landed and the creature is still alive.
 * <ul>
 * <li><b>Strength:</b> the damage that landed on the part, over the body's weight (its box's volume), over
 * {@link #DAMAGE_PER_VOLUME}, clamped to {@link #MIN_STRENGTH}-{@link #MAX_STRENGTH}: a sword on a chicken throws it,
 * the same on a giant barely moves it.</li>
 * <li><b>The shove:</b> a mob lighter than {@link #LIGHT_VOLUME} (a chicken, a cat, a rabbit, a wolf) is pushed
 * {@link #SHOVE} blocks a tick at the most, away from the blow, scaled by the strength.</li>
 * </ul>
 * Everyone within {@link HitStop#RANGE} of the one struck is told, to draw it.
 */
public final class HitReactions {
	/** A blow under this much damage on the part makes no flinch. */
	public static final float MIN_DAMAGE = 0.5F;
	/** The damage a body one block of volume takes for a full flinch. */
	public static final float DAMAGE_PER_VOLUME = 18.0F;
	/** The weakest and the strongest flinch (0-1). */
	public static final float MIN_STRENGTH = 0.12F, MAX_STRENGTH = 1.0F;
	/** The lightest weight counted (blocks cubed): a body smaller than this is weighed as this. */
	public static final float MIN_VOLUME = 0.1F;
	/** A mob of less volume than this is shoved by a blow, by most {@link #SHOVE} blocks a tick (a player-sized body is 0.65). */
	public static final float LIGHT_VOLUME = 0.4F;
	public static final double SHOVE = 0.22, SHOVE_LIFT = 0.06;

	/** The flinch: who, which {@link BodyPart} (its ordinal), how hard (0-1), and which side the blow came from (0 unknown, 1 left, 2 right). */
	public record Flinch(int entity, int part, float strength, int side) implements CustomPacketPayload {
		public static final Type<Flinch> TYPE = new Type<>(JaymosPowers.id("hit_flinch"));
		public static final StreamCodec<ByteBuf, Flinch> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Flinch::entity, ByteBufCodecs.VAR_INT,
			Flinch::part, ByteBufCodecs.FLOAT, Flinch::strength, ByteBufCodecs.VAR_INT, Flinch::side, Flinch::new);

		@Override
		public Type<Flinch> type() {
			return TYPE;
		}
	}

	private HitReactions() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Flinch.TYPE, Flinch.CODEC);
		MobHurtStances.register();
	}

	/** The weight of a body, for how hard a blow is on it: the volume of its box, never under {@link #MIN_VOLUME}. */
	public static float volume(LivingEntity entity) {
		return Math.max(MIN_VOLUME, entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight());
	}

	/** How hard a blow of {@code damage} is on {@code entity}, 0-1 (0 if too light to count). */
	public static float strength(LivingEntity entity, float damage) {
		if (damage < MIN_DAMAGE) {
			return 0.0F;
		}
		return Mth.clamp(damage / volume(entity) / DAMAGE_PER_VOLUME, MIN_STRENGTH, MAX_STRENGTH);
	}

	/** {@code victim} was just hit by {@code source} on {@code landed} (null: not known: the body) for {@code damage}, and lives. */
	public static void onHit(LivingEntity victim, DamageSource source, @Nullable BodyPart landed, float damage) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}
		MobHurtStances.refresh(victim);
		float strength = strength(victim, damage);
		if (strength <= 0.0F) {
			return;
		}
		Vec3 from = source.getSourcePosition();
		Flinch flinch = new Flinch(victim.getId(), (landed == null ? BodyPart.BODY : landed).ordinal(), strength, side(victim, from));
		for (ServerPlayer player : PlayerLookup.around(level, victim.position(), HitStop.RANGE)) {
			ServerPlayNetworking.send(player, flinch);
		}
		if (victim instanceof Mob mob && volume(victim) <= LIGHT_VOLUME && from != null && !mob.isPassenger()) {
			shove(mob, from, strength);
		}
	}

	/** Which side of the body the blow came from, by where it was and which way the body faces. */
	private static int side(LivingEntity victim, @Nullable Vec3 from) {
		if (from == null) {
			return 0;
		}
		double yaw = Math.toRadians(victim.yBodyRot);
		double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
		double toX = from.x - victim.getX(), toZ = from.z - victim.getZ();
		double cross = forwardX * toZ - forwardZ * toX;
		return Math.abs(cross) < 1.0E-3 ? 0 : cross > 0.0 ? 1 : 2;
	}

	private static void shove(Mob mob, Vec3 from, float strength) {
		double dx = mob.getX() - from.x, dz = mob.getZ() - from.z;
		double flat = Math.hypot(dx, dz);
		if (flat < 1.0E-4) {
			return;
		}
		double push = SHOVE * strength;
		mob.setDeltaMovement(mob.getDeltaMovement().add(dx / flat * push, mob.onGround() ? SHOVE_LIFT * strength : 0.0, dz / flat * push));
		mob.syncVelocity = true;
	}
}
