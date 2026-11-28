package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.AttackRange;
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
 * <p>
 * Andy: "enemies should telegraph attacks more and get closer before swinging". The wind-up is longer, and the mob
 * holds its arms up through it ({@link #hold}: zombies, skeletons, illagers raise them), steps in
 * {@link #LUNGE_LEAD} ticks before the blow, and only throws its arm when the blow falls (vanilla swung it with the
 * glint, so the arm was down again before the hit). It starts the wind-up only from {@link #START_REACH_SHARE} of its
 * reach ({@link #closeEnough}, checked by the mixins before the attack cooldown is spent), so it walks up to you instead
 * of glinting from the edge of its reach and whiffing; the blow still lands anywhere in its full reach.
 * <p>
 * Andy (2026-11-24): "armed enemies should [not] attack with unblockables EVERY attack anymore, it should be closer to
 * 20%. and the windup should be longer so players can react". A heavy hitter's blow ({@link #HEAVY_DAMAGE}) is red only
 * {@link #RED_CHANCE} of the time ({@link #red}; a body {@link #HEAVY_WIDTH} wide still always is), and a red blow
 * winds up {@link #HEAVY_WINDUP} ticks instead of {@link #WINDUP}, its star held as long.
 * <p>
 * Andy (2026-11-26): "i really like the lunge the necrophages do, that should extend to our zombies and husks, and they
 * should be telegraphed with a yellow indicator spark, meaning you should dodge or parry, but you cant block. so now
 * there will be three teirs of attack, white, yellow and red". The third tier is yellow ({@link #YELLOW_TELL_COLOR}, a gold
 * star, and a ping between the white bell and the red drum): a blow that can't be blocked ({@link Guard#UNBLOCKABLE_TAG}),
 * only parried in its window or dodged. Mobs throw yellow blows in their leaps ({@link Pounce}, {@link LungeGoal}); other
 * code calls {@link #yellow} or {@link #tellYellow} for the star and the ping.
 */
public final class AttackTells {
	/** Ticks from the glint to the blow; for a red blow, longer, so there's time to see it and roll, step or block. */
	public static final int WINDUP = 10, HEAVY_WINDUP = 20;
	/** The share of a heavy hitter's blows that are red (can't be parried); the rest glint white. */
	public static final float RED_CHANCE = 0.2F;
	/** The wind-up starts only with the target inside this share of the mob's reach (past its own body). */
	static final double START_REACH_SHARE = 0.55;
	/** Vanilla's reach past the body for a mob holding nothing with an attack range (Mob.DEFAULT_ATTACK_REACH). */
	static final double DEFAULT_REACH = Math.sqrt(2.04F) - 0.6F;
	/** The step in: this many ticks before the blow, this fast (blocks a tick) toward the target. */
	static final int LUNGE_LEAD = 3;
	static final double LUNGE_SPEED = 0.25;
	/** A blow this heavy (attack damage), or from a body this wide (blocks), can't be parried. */
	public static final double HEAVY_DAMAGE = 10.0, HEAVY_WIDTH = 1.35;
	public static final int TELL_COLOR = 0xFFFFFF, HEAVY_TELL_COLOR = 0xE02020, YELLOW_TELL_COLOR = 0xF2C230;
	public static final float TELL_SIZE = 1.2F;
	/** (0.20.4) The star's size (1 = about a block across), a heavy blow's, and its white heart's share. */
	public static final float GLINT_SIZE = 0.8F, HEAVY_GLINT_SIZE = 1.1F, YELLOW_GLINT_SIZE = 0.95F, CORE_SHARE = 0.45F;
	/** (0.20.4) The warning ping: Time Flow's bell (volume, pitch), and a heavy blow's lower bell and drum. */
	public static final float PING_VOLUME = 0.9F, PING_PITCH = 1.9F, HEAVY_PING_PITCH = 1.1F, DRUM_VOLUME = 0.8F, DRUM_PITCH = 0.7F;
	/** A yellow tell's ping: the bell pitched between the white and the red, with a soft drum under it (lighter and higher than the red one). */
	public static final float YELLOW_PING_PITCH = 1.45F, YELLOW_DRUM_VOLUME = 0.4F, YELLOW_DRUM_PITCH = 1.0F;
	/** (0.20.4) Players further than this (blocks) don't get the star (the vanilla particle range). */
	static final double GLINT_RANGE = 48.0;
	/** Reach slack (blocks) for a target that stepped a little since the wind-up began. */
	static final double REACH_SLACK = 0.25;

	/**
	 * (0.20.4) The tell's star: this colour (0xRRGGBB), this size. The star has a dark rim so it reads on pale skin and
	 * bright sky; its white heart ({@code core}, {@link #GLINT_CORE}) has none. It holds through a wind-up of
	 * {@code windup} ticks (0: {@link #WINDUP}).
	 */
	public record Glint(int color, float scale, boolean core, int windup) implements ParticleOptions {
		public static final MapCodec<Glint> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("color", TELL_COLOR).forGetter(Glint::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Glint::scale),
			Codec.BOOL.optionalFieldOf("core", false).forGetter(Glint::core),
			Codec.INT.optionalFieldOf("windup", 0).forGetter(Glint::windup)).apply(i, Glint::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Glint> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Glint::color, ByteBufCodecs.FLOAT, Glint::scale, ByteBufCodecs.BOOL, Glint::core,
			ByteBufCodecs.VAR_INT, Glint::windup, Glint::new);

		public Glint(int color, float scale, boolean core) {
			this(color, scale, core, 0);
		}

		@Override
		public ParticleType<?> getType() {
			return core ? GLINT_CORE : GLINT;
		}
	}

	public static final ParticleType<Glint> GLINT = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("tell_glint"),
		FabricParticleTypes.complex(true, Glint.CODEC, Glint.STREAM_CODEC));
	public static final ParticleType<Glint> GLINT_CORE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("tell_glint_core"),
		FabricParticleTypes.complex(true, Glint.CODEC, Glint.STREAM_CODEC));

	private record Pending(LivingEntity target, long hitAt, boolean heavy) {
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
		if (!Dismember.canAttack(mob)) {
			return false;
		}
		if (!(target instanceof LivingEntity living)) {
			if (!Dismember.armless(mob)) {
				mob.swingForAttack(InteractionHand.MAIN_HAND);
			}
			return mob.doHurtTarget(level, target);
		}
		if (PENDING.containsKey(mob) || Crowds.waiting(mob)) {
			// ((0.21.20) one waiting its turn holds its blow, Crowds)
			return false;
		}
		boolean heavy = red(mob);
		int windup = heavy ? HEAVY_WINDUP : WINDUP;
		tell(level, mob, heavy, windup);
		IncomingBlows.aimed(mob, living, windup, heavy);
		PENDING.put(mob, new Pending(living, level.getGameTime() + windup, heavy));
		if (!Dismember.armless(mob)) {
			mob.setAggressive(true);
		}
		return false;
	}

	/**
	 * Whether {@code target} is close enough for {@code mob} to start a wind-up: inside {@link #START_REACH_SHARE} of its
	 * reach. A weapon with a near limit to its reach (a spear) keeps vanilla's check, and so does a rider.
	 */
	public static boolean closeEnough(Mob mob, LivingEntity target) {
		if (!Dismember.canAttack(mob)) {
			return false;
		}
		if (mob.isPassenger()) {
			return true;
		}
		AttackRange range = mob.getActiveItem().get(DataComponents.ATTACK_RANGE);
		if (range != null && range.effectiveMinRange(mob) > 0.0F) {
			return true;
		}
		double reach = (range == null ? DEFAULT_REACH : range.effectiveMaxRange(mob)) * START_REACH_SHARE;
		return mob.getBoundingBox().inflate(reach, 0.0, reach).intersects(target.getBoundingBox());
	}

	/** Whether {@code mob}'s blows are too heavy to parry. */
	public static boolean heavy(Mob mob) {
		return mob.getBbWidth() >= HEAVY_WIDTH || mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
			&& mob.getAttributeValue(Attributes.ATTACK_DAMAGE) >= HEAVY_DAMAGE;
	}

	/**
	 * Whether this blow of {@code mob}'s is red: always from a body {@link #HEAVY_WIDTH} wide, and {@link #RED_CHANCE} of
	 * the time from a heavy hitter ({@link #HEAVY_DAMAGE}); never otherwise.
	 */
	static boolean red(Mob mob) {
		return mob.getBbWidth() >= HEAVY_WIDTH || heavy(mob) && mob.getRandom().nextFloat() < RED_CHANCE;
	}

	/** The glint: at the weapon hand for something with arms, at the head for anything else. */
	public static void tell(ServerLevel level, LivingEntity mob, boolean heavy) {
		tell(level, mob, heavy, 0);
	}

	/** {@link #tell(ServerLevel, LivingEntity, boolean)}, the star held through a {@code windup} of this many ticks (0: {@link #WINDUP}). */
	public static void tell(ServerLevel level, LivingEntity mob, boolean heavy, int windup) {
		glint(level, tellAt(mob), heavy, windup);
	}

	/** A yellow tell (a blow that can't be blocked) at {@code mob}, the star held through {@code windup} ticks (0: {@link #WINDUP}). */
	public static void tellYellow(ServerLevel level, LivingEntity mob, int windup) {
		yellow(level, tellAt(mob), windup);
	}

	private static Vec3 tellAt(LivingEntity mob) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		Vec3 at;
		if (!mob.getMainHandItem().isEmpty() && !Dismember.armless(mob)) {
			Vec3 side = new Vec3(-facing.z, 0.0, facing.x).scale(mob.getMainArm() == HumanoidArm.RIGHT ? -0.35 : 0.35);
			at = mob.getEyePosition().add(facing.scale(0.4)).add(side).add(0.0, -0.2, 0.0);
		} else if (Dismember.armless(mob) && !Dismember.biter(mob)) {
			at = mob.position().add(0.0, mob.getBbHeight() * 0.45, 0.0).add(facing.scale(mob.getBbWidth() * 0.5 + 0.15));
		} else {
			at = mob.getEyePosition().add(facing.scale(mob.getBbWidth() * 0.5 + 0.15));
		}
		return at;
	}

	/**
	 * (0.20.4) A tell's glint at {@code at}: the star in white (or red for {@code heavy}), its white heart, a ring of dust,
	 * and the warning ping. Public for Kingdoms (its {@code PowersCompat}) and our own monsters' wind-ups.
	 */
	public static void glint(ServerLevel level, Vec3 at, boolean heavy) {
		glint(level, at, heavy, 0);
	}

	/** {@link #glint(ServerLevel, Vec3, boolean)}, the star held through a {@code windup} of this many ticks (0: {@link #WINDUP}). Kingdoms' red swings use it. */
	public static void glint(ServerLevel level, Vec3 at, boolean heavy, int windup) {
		int color = heavy ? HEAVY_TELL_COLOR : TELL_COLOR;
		float size = heavy ? HEAVY_GLINT_SIZE : GLINT_SIZE;
		Glint star = new Glint(color, size * (0.95F + level.getRandom().nextFloat() * 0.1F), false, windup);
		Glint core = new Glint(0xFFFFFF, size * CORE_SHARE, true, windup);
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

	/**
	 * A yellow tell at {@code at}: a gold star, its white heart, a ring of gold dust, and the ping (a bell between the
	 * white's and the red's with a soft drum under it). Public for the monsters whose blows can't be blocked
	 * ({@link Guard#UNBLOCKABLE_TAG}) and for Kingdoms.
	 */
	public static void yellow(ServerLevel level, Vec3 at, int windup) {
		Glint star = new Glint(YELLOW_TELL_COLOR, YELLOW_GLINT_SIZE * (0.95F + level.getRandom().nextFloat() * 0.1F), false, windup);
		Glint core = new Glint(0xFFFFFF, YELLOW_GLINT_SIZE * CORE_SHARE, true, windup);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) > GLINT_RANGE * GLINT_RANGE) {
				continue;
			}
			level.sendParticles(player, star, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(player, core, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(new DustParticleOptions(YELLOW_TELL_COLOR, TELL_SIZE), at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, PING_VOLUME, YELLOW_PING_PITCH);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, YELLOW_DRUM_VOLUME, YELLOW_DRUM_PITCH);
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
			long now = level.getGameTime();
			if (now < pending.hitAt()) {
				hold(mob, pending.target(), pending.hitAt() - now);
				continue;
			}
			PENDING.remove(mob);
			mob.swingForAttack(InteractionHand.MAIN_HAND);
			LivingEntity target = pending.target();
			if (!target.isAlive() || target.level() != level || !inReach(mob, target) || !mob.getSensing().hasLineOfSight(target)) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, mob.getSoundSource(), 0.6F, 0.8F);
				continue;
			}
			boolean heavy = pending.heavy();
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

	/**
	 * Mid wind-up: arms kept up (a zombie's goal lowers them each tick by its cooldown), and the step in
	 * {@link #LUNGE_LEAD} ticks before the blow, on the ground and not already touching.
	 */
	private static void hold(Mob mob, LivingEntity target, long left) {
		if (!Dismember.armless(mob)) {
			mob.setAggressive(true);
		}
		if (left != LUNGE_LEAD || !mob.onGround() || mob.isPassenger()
			|| mob.getBoundingBox().inflate(REACH_SLACK).intersects(target.getBoundingBox())) {
			return;
		}
		Vec3 toward = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
		if (toward.lengthSqr() > 1.0E-4) {
			mob.push(toward.normalize().scale(LUNGE_SPEED));
		}
	}

	private static boolean inReach(Mob mob, LivingEntity target) {
		return mob.isWithinMeleeAttackRange(target)
			|| mob.getBoundingBox().inflate(REACH_SLACK).intersects(target.getBoundingBox());
	}
}
