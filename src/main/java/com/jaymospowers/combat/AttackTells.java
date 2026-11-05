package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.67) Attack tells for every mob. Andy: "Can you make every mob use this glint mechanic?" Kingdoms' fighters already
 * wind up their Better Combat swings with a glint; every other melee mob (vanilla's goal-driven ones, the brain-driven
 * piglins and hoglins, and every modded mob that attacks through either) now does too. Its blow no longer lands the
 * instant it's thrown: a glint shows at its weapon or head, and the blow lands {@link #WINDUP} ticks later if its target
 * is still in reach and in sight (step back or dodge and it whiffs, raise a guard and you can parry it). Its attack
 * cooldown runs as before. White: parryable. Red: heavy ({@link #HEAVY_DAMAGE} attack damage or more, or a body
 * {@link #HEAVY_WIDTH} wide), which carries {@link Guard#UNPARRYABLE_TAG} as it lands, so a guard only blocks it.
 * <p>
 * The blows are taken in {@code mixin/AttackTellGoalMixin} (MeleeAttackGoal) and {@code mixin/AttackTellBrainMixin}
 * (the MeleeAttack behaviour).
 * <p>
 * (0.20.4) Andy: "the particle effect for attack indicators should be more visible, and a sound should play like for
 * time flow. maybe a star or flash in the respective color". The glint is now our own {@link Glint}: a full-bright
 * four-point star in the tell's colour (the client's {@code TellGlintParticle}) that pops, turns and holds through the
 * wind-up, a small white-hot star at its heart, and a ring of dust in the colour. It's sent to everyone near even on
 * reduced particles. With it comes Time Flow's warning ping (a high note-block bell; a heavy blow's rings low, with a
 * drum under it), heard from the mob. Kingdoms' fighters and gunners use the same glint ({@link #glint}).
 */
public final class AttackTells {
	/** Ticks from the glint to the blow. */
	public static final int WINDUP = 6;
	/** A blow this heavy (attack damage), or from a body this wide (blocks), can't be parried. */
	public static final double HEAVY_DAMAGE = 10.0, HEAVY_WIDTH = 1.35;
	public static final int TELL_COLOR = 0xFFFFFF, HEAVY_TELL_COLOR = 0xE02020;
	public static final float TELL_SIZE = 1.2F;
	/** (0.20.4) The star's size (1 = about a block across), a heavy blow's, and its white heart's share. */
	public static final float GLINT_SIZE = 0.8F, HEAVY_GLINT_SIZE = 1.1F, CORE_SHARE = 0.45F;
	/** (0.20.4) The warning ping: Time Flow's bell (volume, pitch), and a heavy blow's lower bell and drum. */
	public static final float PING_VOLUME = 0.9F, PING_PITCH = 1.9F, HEAVY_PING_PITCH = 1.1F, DRUM_VOLUME = 0.8F, DRUM_PITCH = 0.7F;
	/** (0.20.4) Players further than this (blocks) don't get the star (the vanilla particle range). */
	static final double GLINT_RANGE = 48.0;
	/** Reach slack (blocks) for a target that stepped a little since the wind-up began. */
	static final double REACH_SLACK = 0.25;

	/**
	 * (0.20.4) The tell's star: this colour (0xRRGGBB), this size. The star has a dark rim so it reads on pale skin and
	 * bright sky; its white heart ({@code core}, {@link #GLINT_CORE}) has none.
	 */
	public record Glint(int color, float scale, boolean core) implements ParticleOptions {
		public static final MapCodec<Glint> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("color", TELL_COLOR).forGetter(Glint::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Glint::scale),
			Codec.BOOL.optionalFieldOf("core", false).forGetter(Glint::core)).apply(i, Glint::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Glint> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Glint::color, ByteBufCodecs.FLOAT, Glint::scale, ByteBufCodecs.BOOL, Glint::core, Glint::new);

		@Override
		public ParticleType<?> getType() {
			return core ? GLINT_CORE : GLINT;
		}
	}

	public static final ParticleType<Glint> GLINT = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("tell_glint"),
		FabricParticleTypes.complex(true, Glint.CODEC, Glint.STREAM_CODEC));
	public static final ParticleType<Glint> GLINT_CORE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("tell_glint_core"),
		FabricParticleTypes.complex(true, Glint.CODEC, Glint.STREAM_CODEC));

	private record Pending(LivingEntity target, long hitAt) {
	}

	private static final Map<Mob, Pending> PENDING = new IdentityHashMap<>();

	private AttackTells() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AttackTells::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	/**
	 * A melee blow thrown by {@code mob} at {@code target}: the glint now and the blow after the wind-up. Always false
	 * (nothing landed yet); the callers drop the result, as vanilla does.
	 */
	public static boolean windUp(Mob mob, ServerLevel level, Entity target) {
		if (!(target instanceof LivingEntity living)) {
			return mob.doHurtTarget(level, target);
		}
		if (PENDING.containsKey(mob)) {
			return false;
		}
		boolean heavy = heavy(mob);
		tell(level, mob, heavy);
		IncomingBlows.aimed(mob, living, WINDUP, heavy);
		PENDING.put(mob, new Pending(living, level.getGameTime() + WINDUP));
		return false;
	}

	/** Whether {@code mob}'s blows are too heavy to parry. */
	public static boolean heavy(Mob mob) {
		return mob.getBbWidth() >= HEAVY_WIDTH || mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
			&& mob.getAttributeValue(Attributes.ATTACK_DAMAGE) >= HEAVY_DAMAGE;
	}

	/** The glint: at the weapon hand for something with arms, at the head for anything else. */
	public static void tell(ServerLevel level, LivingEntity mob, boolean heavy) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		Vec3 at;
		if (!mob.getMainHandItem().isEmpty()) {
			Vec3 side = new Vec3(-facing.z, 0.0, facing.x).scale(mob.getMainArm() == HumanoidArm.RIGHT ? -0.35 : 0.35);
			at = mob.getEyePosition().add(facing.scale(0.4)).add(side).add(0.0, -0.2, 0.0);
		} else {
			at = mob.getEyePosition().add(facing.scale(mob.getBbWidth() * 0.5 + 0.15));
		}
		glint(level, at, heavy);
	}

	/**
	 * (0.20.4) A tell's glint at {@code at}: the star in white (or red for {@code heavy}), its white heart, a ring of dust,
	 * and the warning ping. Public for Kingdoms (its {@code PowersCompat}) and our own monsters' wind-ups.
	 */
	public static void glint(ServerLevel level, Vec3 at, boolean heavy) {
		int color = heavy ? HEAVY_TELL_COLOR : TELL_COLOR;
		float size = heavy ? HEAVY_GLINT_SIZE : GLINT_SIZE;
		Glint star = new Glint(color, size * (0.95F + level.getRandom().nextFloat() * 0.1F), false);
		Glint core = new Glint(0xFFFFFF, size * CORE_SHARE, true);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) > GLINT_RANGE * GLINT_RANGE) {
				continue;
			}
			level.sendParticles(player, star, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(player, core, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(new DustParticleOptions(color, TELL_SIZE), at.x, at.y, at.z, heavy ? 8 : 5, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, PING_VOLUME, heavy ? HEAVY_PING_PITCH : PING_PITCH);
		if (heavy) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, DRUM_VOLUME, DRUM_PITCH);
		}
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Mob, Pending>> it = new ArrayList<>(PENDING.entrySet()).iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Pending> entry = it.next();
			Mob mob = entry.getKey();
			Pending pending = entry.getValue();
			if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || !mob.isAlive()) {
				PENDING.remove(mob);
				continue;
			}
			if (level.getGameTime() < pending.hitAt()) {
				continue;
			}
			PENDING.remove(mob);
			LivingEntity target = pending.target();
			if (!target.isAlive() || target.level() != level || !inReach(mob, target) || !mob.getSensing().hasLineOfSight(target)) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, mob.getSoundSource(), 0.6F, 0.8F);
				continue;
			}
			boolean heavy = heavy(mob);
			if (heavy) {
				mob.addTag(Guard.UNPARRYABLE_TAG);
			}
			try {
				mob.doHurtTarget(level, target);
			} finally {
				if (heavy) {
					mob.removeTag(Guard.UNPARRYABLE_TAG);
				}
			}
		}
	}

	private static boolean inReach(Mob mob, LivingEntity target) {
		return mob.isWithinMeleeAttackRange(target)
			|| mob.getBoundingBox().inflate(REACH_SLACK).intersects(target.getBoundingBox());
	}
}
