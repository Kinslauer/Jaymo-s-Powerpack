package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.core.fx.MagicFx;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One cue for every special hit (Andy's Hackers 'n Slashers picks, All Rights Reserved, ideas only: the sound, the flash,
 * the camera punch and the ring are our own). A riposte, a perfect parry, a jumping crit, and whatever Kingdoms calls it
 * for (a sneak attack, an assassination, a headshot, through {@code PowersCompat} reflection on {@link #cue}):
 * <ul>
 * <li>a short sharp ring (synthesised, {@code tools/gen_blade_sounds.py}) and a small white flash at the hit;</li>
 * <li>the attacker's own camera punches ({@link Punch}, off with {@code /critpunch off});</li>
 * <li>a crit with a heavy weapon (a great weapon, a warhammer, a mace) also throws a ring and knocks back those close by.</li>
 * </ul>
 */
public final class CritCue {
	// ===== the knobs ================================================================================================
	/** The flash at the hit: how big, for how many ticks, and its colour (white). */
	static final float FLASH_RADIUS = 0.7F;
	static final int FLASH_TICKS = 5, FLASH_COLOR = 0xFFFFFF;
	/** The cue's sound volume and pitch. */
	static final float VOLUME = 0.9F, PITCH = 1.0F;
	/** The camera punch: degrees at its peak, and how many ticks it lasts. */
	static final float PUNCH_DEGREES = 1.8F;
	static final int PUNCH_TICKS = 8;
	/** A heavy crit's ring and knock-back: how far both reach, the ring's colour and length, the shove (at its strongest, at the middle) and its lift. */
	static final double HEAVY_RADIUS = 3.0, HEAVY_SHOVE = 0.8, HEAVY_LIFT = 0.25;
	static final int HEAVY_COLOR = 0xFFE9B8, HEAVY_TICKS = 12;
	// ================================================================================================================

	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.crit"));

	/** Server → the one who landed it: punch your camera ({@code from}: the hit, so the view is kicked away from it). */
	public record Punch(float degrees, int ticks, Vec3 from) implements CustomPacketPayload {
		public static final Type<Punch> TYPE = new Type<>(JaymosPowers.id("crit_punch"));
		public static final StreamCodec<ByteBuf, Punch> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Punch::degrees,
			ByteBufCodecs.VAR_INT, Punch::ticks,
			Vec3.STREAM_CODEC, Punch::from,
			Punch::new);

		@Override
		public Type<Punch> type() {
			return TYPE;
		}
	}

	private CritCue() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Punch.TYPE, Punch.CODEC);
	}

	/**
	 * A special hit landed at {@code at}: the sound and the flash for everyone near, and, when {@code attacker} is a player, the
	 * punch on their screen. Public for Kingdoms.
	 */
	public static void cue(ServerLevel level, Vec3 at, @Nullable ServerPlayer attacker) {
		level.playSound(null, at.x, at.y, at.z, SOUND, SoundSource.PLAYERS, VOLUME, PITCH + (level.getRandom().nextFloat() - 0.5F) * 0.2F);
		MagicFx.blast(level, at, FLASH_RADIUS, FLASH_COLOR, FLASH_TICKS);
		if (attacker != null) {
			ServerPlayNetworking.send(attacker, new Punch(PUNCH_DEGREES, PUNCH_TICKS, at));
		}
	}

	/** {@link #cue(ServerLevel, Vec3, ServerPlayer)} with no one's camera to punch (Kingdoms' {@code PowersCompat.critCue} calls this one). */
	public static void cue(ServerLevel level, Vec3 at) {
		cue(level, at, null);
	}

	/** A jumping crit by {@code player} on {@code victim}: the cue, and the heavy ring if the weapon is heavy. */
	static void crit(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = player.level();
		cue(level, victim.getBoundingBox().getCenter(), player);
		if (heavy(player.getMainHandItem())) {
			heavyRing(level, player, victim);
		}
	}

	/** A great weapon, a warhammer or a mace. */
	static boolean heavy(ItemStack weapon) {
		return Armed.kind(weapon) == Armed.Kind.TWO_HAND || HammerSlam.isWarhammer(weapon.getItem()) || weapon.is(Items.MACE);
	}

	/** The ring across the ground at the foe's feet, and a shove outward for everything else near it. */
	private static void heavyRing(ServerLevel level, ServerPlayer player, LivingEntity victim) {
		Vec3 centre = victim.position();
		MagicFx.ring(level, centre.add(0.0, 0.1, 0.0), null, (float) HEAVY_RADIUS, HEAVY_COLOR, HEAVY_TICKS);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(HEAVY_RADIUS))) {
			if (near == player || near == victim || !near.isAlive() || near.isSpectator() || near.isAlliedTo(player) || Stances.immovable(near)) {
				continue;
			}
			Vec3 away = near.position().subtract(centre).multiply(1.0, 0.0, 1.0);
			double distance = away.length();
			if (distance >= HEAVY_RADIUS) {
				continue;
			}
			away = distance > 1.0E-4 ? away.scale(1.0 / distance) : player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			double shove = HEAVY_SHOVE * (1.0 - distance / HEAVY_RADIUS);
			near.push(away.x * shove, HEAVY_LIFT, away.z * shove);
			near.needsSync = true;
		}
	}
}
