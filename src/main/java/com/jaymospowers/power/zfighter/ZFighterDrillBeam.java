package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.Soundscape;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Drill Beam, a charged technique (sneak+V, hold and let go): a thin, fast beam wound with a
 * double spiral, from two fingers. It pierces every creature in a line to {@link #RANGE} blocks, bores through soft blocks
 * (it passes them; where mob griefing is on it breaks them too, and only them) and stops at a hard one, where a small
 * blast goes off that hurts but never tears the ground. Drawn with the Spiral Lance's thin style ({@link ZFighterBeams}
 * on the client), so there is no new art. Damage, reach and ki are scaled by Ki Power as the Ki Wave's are.
 */
final class ZFighterDrillBeam {
	static final float MIN_KI = 25.0F;
	static final float MAX_KI = 45.0F;
	/** Ticks of gathering to a full charge. */
	static final int FULL_TICKS = 40;
	/** Damage to each creature, charge 0 to full (times Ki Power). */
	static final float DAMAGE_MIN = 7.0F;
	static final float DAMAGE_MAX = 13.0F;
	static final double RANGE = 48.0;
	/** Half the width it hurts across, and how finely its line is walked (blocks). */
	static final double RADIUS = 0.5;
	static final double STEP = 0.5;
	/** A block this soft (destroy time) or softer is bored through; harder, unbreakable or holding a block entity, it stops the beam. */
	static final float SOFT_HARDNESS = 1.0F;
	/** At most this many soft blocks broken by one beam (a guard). */
	static final int MAX_BREAKS = 40;
	/** The small blast where it ends: hurt reach (blocks) and the share of the beam's damage at its centre. */
	static final double BLAST_RADIUS = 2.5;
	static final float BLAST_SHARE = 0.5F;
	static final float BLAST_POWER = 1.0F;
	static final double PUSH = 0.5;
	static final float POISE_WEAR = 4.0F;
	static final float EXHAUSTION = 1.2F;
	/** The thin core's radius, and the double spiral's: how tightly it winds and how wide. */
	static final float CORE = 0.1F;
	static final double COIL_STEP = 0.4, COIL_TURNS = 2.2, COIL_WIDTH = 0.35;

	private ZFighterDrillBeam() {
	}

	/** Two fingers raised, a bright point at their tips growing with the charge. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 tip = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.25, 0.0);
		int color = ZFighterArts.color(live);
		ZFighterKi.orb(level, -(player.getId() * 4 + 3), tip, 0.1 + 0.2 * charge, color);
		if (level.getRandom().nextInt(2) == 0) {
			double ox = (level.getRandom().nextDouble() - 0.5) * 1.2;
			double oy = (level.getRandom().nextDouble() - 0.5) * 1.2;
			double oz = (level.getRandom().nextDouble() - 0.5) * 1.2;
			level.sendParticles(new DustParticleOptions(color, 0.7F + charge * 0.5F), true, false, tip.x + ox, tip.y + oy, tip.z + oz, 0, -ox, -oy, -oz, 0.12);
		}
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, tip.x, tip.y, tip.z, 2, 0.1, 0.1, 0.1, 0.2);
		}
		if (Soundscape.due(player, "ki.drill", 6, 10)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(0.7F).pitched(1.3F + charge * 0.7F), ZFighterKi.KI_SURGE);
		}
	}

	/** Let go: the beam is out, all the way, now. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		Vec3 from = ZFighterKi.waveHands(player, look);
		int color = ZFighterArts.color(live);
		float kiPower = ZFighterPower.kiPower(player, live);
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * kiPower;
		boolean griefing = ZFighterArts.griefing(level);

		// walk the line: soft blocks are bored through (and broken where griefing allows), a hard one ends it
		double length = RANGE;
		List<BlockPos> breaks = new ArrayList<>();
		for (double d = STEP; d <= RANGE; d += STEP) {
			BlockPos pos = BlockPos.containing(from.add(look.scale(d)));
			if (!level.isLoaded(pos)) {
				length = d - STEP;
				break;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.liquid()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F || hardness > SOFT_HARDNESS || state.hasBlockEntity()) {
				length = d;
				break;
			}
			if (griefing && breaks.size() < MAX_BREAKS && !breaks.contains(pos) && mayBreak(player, level, pos, state)) {
				breaks.add(pos.immutable());
			}
		}
		Vec3 end = from.add(look.scale(length));
		bore(player, level, breaks);

		// every creature along it, once
		var source = ZFighterKi.source(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(RADIUS + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			if (ZFighterKi.distanceToSegment(target.getBoundingBox().getCenter(), from, end) > RADIUS + target.getBbWidth() * 0.5) {
				continue;
			}
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, damage);
			ZFighterKi.push(target, look, PUSH, 0.1);
			ZFighterPoise.wear(target, POISE_WEAR);
			level.sendParticles(ParticleTypes.CRIT, true, false, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.3, 0.2, 0.3);
		}

		// the small blast: hurts what is near, tears nothing
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(BLAST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			double distance = near.getBoundingBox().getCenter().distanceTo(end);
			if (distance > BLAST_RADIUS) {
				continue;
			}
			near.setInvulnerableTime(0);
			near.hurtServer(level, source, damage * BLAST_SHARE * (float)(1.0 - 0.5 * distance / BLAST_RADIUS));
			ZFighterKi.push(near, near.position().subtract(end), 0.6, 0.3);
		}
		level.explode(player, source, ZFighterKi.GROUND_ONLY, end.x, end.y, end.z, BLAST_POWER, false, Level.ExplosionInteraction.NONE);
		level.sendParticles(new DustParticleOptions(color, 2.0F), true, false, end.x, end.y, end.z, 24, 0.5, 0.5, 0.5, 0.25);

		draw(level, from, end, look, color);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.2F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.9F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
		Shakes.send(player, 0.3F + 0.5F * charge, from);
		Shakes.around(level, end, 12.0, 0.3F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION * (1.0F + charge));
		}
	}

	/** Breaks the soft blocks it bored through, as the fighter would (the break's drops and events). */
	private static void bore(ServerPlayer player, ServerLevel level, List<BlockPos> breaks) {
		ZFighterGrowth.cutting = true;
		try {
			for (BlockPos pos : breaks) {
				BlockState state = level.getBlockState(pos);
				level.destroyBlock(pos, true, player);
				PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
			}
		} finally {
			ZFighterGrowth.cutting = false;
		}
	}

	private static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		return player.mayBuild() && player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode())
			&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null);
	}

	/** The thin core for everyone near (the Spiral Lance's style), and two strands of light winding round it. */
	private static void draw(ServerLevel level, Vec3 from, Vec3 end, Vec3 look, int color) {
		ZFighterNet.BeamFx fx = new ZFighterNet.BeamFx(from.x, from.y, from.z, end.x, end.y, end.z, CORE, color, ZFighterKi.SPIRAL, ZFighterKi.BEAM_TICKS);
		Vec3 middle = from.lerp(end, 0.5);
		double seen = ZFighterKi.BEAM_SEEN + from.distanceTo(end) * 0.5;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(middle) <= seen * seen) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
		Vec3 side = look.cross(Math.abs(look.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 up = side.cross(look).normalize();
		DustParticleOptions glow = new DustParticleOptions(color, 0.9F);
		double length = from.distanceTo(end);
		for (double d = 0.0; d <= length; d += COIL_STEP) {
			double t = d * COIL_TURNS;
			for (int strand = 0; strand < 2; strand++) {
				double a = t + strand * Math.PI;
				Vec3 at = from.add(look.scale(d)).add(side.scale(Math.cos(a) * COIL_WIDTH)).add(up.scale(Math.sin(a) * COIL_WIDTH));
				level.sendParticles(glow, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
