package com.jaymoskingdoms;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Getting around (fifth edition's feather fall, spider climb, water walk, jump and misty step, Andy's pick H7): short
 * movement spells.
 * <ul>
 * <li><b>Feather fall</b>: the caster and the players beside them fall slowly for a minute (a controlled descent).</li>
 * <li><b>Spider climb</b>: any wall counts as a ladder for a minute; a held spell ({@link Concentration}).</li>
 * <li><b>Water walk</b>: still water holds you up for three minutes; sneak and you sink. The walking spells work through
 * Powers' {@code MagicMoves}, which the body's own movement reads.</li>
 * <li><b>Long jump</b>: a jump goes far and high for a minute.</li>
 * <li><b>Misty step</b>: blink up to a few blocks along your look in a puff of silver mist, short of any wall.</li>
 * </ul>
 */
public final class Strides {
	/** How long each lasts (ticks), at the power of a scroll. */
	static final int FEATHER_TICKS = 20 * 60, CLIMB_TICKS = 20 * 60, WATER_TICKS = 20 * 180, JUMP_TICKS = 20 * 60;
	/** Feather fall reaches players this near the caster (blocks). */
	static final double FEATHER_RANGE = 4.0;
	/** Misty step: how far it carries (blocks), and the silver of its mist. */
	static final double STEP_RANGE = 7.0;
	static final int MIST = 0xC8D6E8;

	private Strides() {
	}

	static boolean ours(String spell) {
		return switch (spell) {
			case "feather_fall", "spider_climb", "water_walk", "long_jump", "misty_step" -> true;
			default -> false;
		};
	}

	static boolean cast(String spell, ServerLevel level, Player caster, float power) {
		int scale = Math.round(Math.max(0.5F, power) * 100.0F);
		return switch (spell) {
			case "feather_fall" -> featherFall(level, caster, FEATHER_TICKS * scale / 100);
			case "spider_climb" -> spiderClimb(level, caster, CLIMB_TICKS * scale / 100);
			case "water_walk" -> walk(level, caster, "water", WATER_TICKS * scale / 100, SoundEvents.PLAYER_SPLASH);
			case "long_jump" -> walk(level, caster, "jump", JUMP_TICKS * scale / 100, SoundEvents.RABBIT_JUMP);
			case "misty_step" -> mistyStep(level, caster);
			default -> false;
		};
	}

	private static boolean featherFall(ServerLevel level, Player caster, int ticks) {
		List<Player> friends = level.getEntitiesOfClass(Player.class, caster.getBoundingBox().inflate(FEATHER_RANGE),
			p -> p.isAlive() && !p.isSpectator() && (p == caster || p.isAlliedTo(caster) || p instanceof ServerPlayer o && caster instanceof ServerPlayer c && Parties.allied(c, o)));
		for (Player p : friends) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, true, true, true), caster);
			level.sendParticles(Fx.PALE, p.getX(), p.getY(0.6), p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			Fx.ring(level, p.position(), 1.2F, MIST);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	private static boolean spiderClimb(ServerLevel level, Player caster, int ticks) {
		if (!PowersCompat.moves(caster, "climb", ticks)) {
			return false;
		}
		// a held spell: struck hard enough, they let go of the wall
		Concentration.hold(caster, "spider_climb", ticks, () -> PowersCompat.endMoves(caster, "climb"));
		level.sendParticles(Fx.miasma(0xBFC4C8, 1.2F), caster.getX(), caster.getY(0.5), caster.getZ(), 12, 0.3, 0.4, 0.3, 0.01);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.5F);
		return true;
	}

	private static boolean walk(ServerLevel level, Player caster, String kind, int ticks, net.minecraft.sounds.SoundEvent sound) {
		if (!PowersCompat.moves(caster, kind, ticks)) {
			return false;
		}
		Fx.ring(level, caster.position(), 1.6F, kind.equals("water") ? 0x9FD8FF : MIST);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), sound, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	private static boolean mistyStep(ServerLevel level, Player caster) {
		Vec3 to = destination(level, caster);
		if (to == null) {
			return false;
		}
		Vec3 from = caster.position();
		puff(level, from);
		double fallen = caster.fallDistance;
		if (caster instanceof ServerPlayer sp) {
			sp.teleportTo(level, to.x, to.y, to.z, Set.of(), sp.getYRot(), sp.getXRot(), false);
		} else {
			caster.teleportTo(to.x, to.y, to.z);
		}
		caster.fallDistance = fallen;
		puff(level, to);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.8F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	/** A puff of silver mist where someone was or came. */
	private static void puff(ServerLevel level, Vec3 at) {
		Fx.blast(level, at.add(0.0, 1.0, 0.0), 1.4F, MIST);
		level.sendParticles(Fx.miasma(MIST, 2.0F), at.x, at.y + 1.0, at.z, 16, 0.4, 0.6, 0.4, 0.01);
		level.sendParticles(Fx.mote(MIST, 0.9F), at.x, at.y + 1.0, at.z, 10, 0.4, 0.6, 0.4, 0.04);
	}

	/** Where the step lands: along the look, short of any wall, somewhere there is room to stand and no drop into lava; null if there is none. */
	private static Vec3 destination(ServerLevel level, Player caster) {
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(STEP_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		double reach = hit.getType() == HitResult.Type.MISS ? STEP_RANGE : Math.max(0.0, eye.distanceTo(hit.getLocation()) - 0.6);
		for (double d = reach; d >= 1.5; d -= 0.5) {
			Vec3 to = caster.position().add(look.scale(d));
			if (!level.noCollision(caster, caster.getBoundingBox().move(to.subtract(caster.position())))) {
				continue;
			}
			BlockState under = level.getBlockState(net.minecraft.core.BlockPos.containing(to.x, to.y - 0.5, to.z));
			if (under.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				continue;
			}
			return to;
		}
		return null;
	}
}
