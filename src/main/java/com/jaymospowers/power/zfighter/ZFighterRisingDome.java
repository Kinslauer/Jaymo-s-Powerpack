package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Shakes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Rising Dome, a quick technique (G): an uppercut of ki knocks the foe in front (the locked one,
 * else the nearest in view within {@link #RANGE}) skyward, and {@link #DELAY_TICKS} later a pillar of ki erupts upward under
 * it, from the ground to well above, hurting everything in the column and throwing it higher. A good follow-up to a combo
 * finisher: it needs the foe close. The pillar is drawn as one of the wave's broad beams ({@link ZFighterKi#showBeam}) over
 * a dome of motes at its foot, and breaks no blocks.
 */
final class ZFighterRisingDome {
	static final float KI = 25.0F;
	static final int COOLDOWN = 260;
	/** The foe must be this near (blocks). */
	static final double RANGE = 6.0;
	/** The uppercut: damage (times Ki Power), and how hard the foe is thrown up. */
	static final float UPPERCUT_DAMAGE = 4.0F;
	static final double LAUNCH_UP = 1.1, LAUNCH_AWAY = 0.15;
	/** Ticks from the launch to the pillar. */
	static final int DELAY_TICKS = 12;
	/** The pillar: damage (times Ki Power), half its width, how far below and above the foe it reaches, and how far it throws. */
	static final float PILLAR_DAMAGE = 9.0F;
	static final double PILLAR_RADIUS = 1.6, PILLAR_BELOW = 40.0, PILLAR_ABOVE = 6.0, PILLAR_THROW = 1.1;
	static final float PILLAR_BLAST = 1.5F;
	static final float POISE_WEAR = 12.0F;

	private record Pillar(ServerLevel level, UUID owner, UUID foe, Vec3 fallback, float damage, int color, long erupt) {
	}

	private static final List<Pillar> PILLARS = new ArrayList<>();

	private ZFighterRisingDome() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterRisingDome::tick);
	}

	static void clear() {
		PILLARS.clear();
	}

	/** G: the uppercut. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		LivingEntity foe = ZFighterArts.find(player, RANGE);
		if (foe == null) {
			ZFighterArts.noFoe(player);
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		float kiPower = ZFighterPower.kiPower(player, live);
		Vec3 away = foe.position().subtract(player.position());
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, ZFighterKi.source(player), UPPERCUT_DAMAGE * kiPower);
		ZFighterKi.push(foe, away, LAUNCH_AWAY, LAUNCH_UP);
		ZFighterPoise.wear(foe, POISE_WEAR);
		int color = ZFighterArts.color(live);
		Vec3 at = foe.position();
		DustParticleOptions dust = new DustParticleOptions(color, 1.6F);
		level.sendParticles(dust, true, false, at.x, at.y + 0.2, at.z, 30, 0.4, 0.2, 0.4, 0.3);
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y + 1.0, at.z, 16, 0.3, 0.6, 0.3, 0.5);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		PILLARS.add(new Pillar(level, player.getUUID(), foe.getUUID(), at, PILLAR_DAMAGE * kiPower, color, level.getGameTime() + DELAY_TICKS));
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.8F);
		}
		return COOLDOWN;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Pillar> it = PILLARS.iterator(); it.hasNext(); ) {
			Pillar pillar = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(pillar.owner);
			long now = pillar.level.getGameTime();
			if (owner == null || !owner.isAlive() || owner.level() != pillar.level) {
				it.remove();
			} else if (now >= pillar.erupt) {
				erupt(owner, pillar);
				it.remove();
			}
		}
	}

	/** The pillar goes up under wherever the foe is now (where it was struck, if it is gone). */
	private static void erupt(ServerPlayer owner, Pillar pillar) {
		ServerLevel level = pillar.level;
		Vec3 over = level.getEntity(pillar.foe) instanceof LivingEntity foe && foe.isAlive() && foe.level() == level ? foe.position() : pillar.fallback;
		var hit = level.clip(new ClipContext(over, over.add(0.0, -PILLAR_BELOW, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 base = hit.getType() == HitResult.Type.MISS ? over.add(0.0, -PILLAR_BELOW, 0.0) : hit.getLocation();
		Vec3 top = new Vec3(over.x, over.y + PILLAR_ABOVE, over.z);
		var source = ZFighterKi.source(owner);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(base, top).inflate(PILLAR_RADIUS),
			e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 centre = near.getBoundingBox().getCenter();
			if (Math.hypot(centre.x - over.x, centre.z - over.z) > PILLAR_RADIUS + near.getBbWidth() * 0.5) {
				continue;
			}
			near.setInvulnerableTime(0);
			near.hurtServer(level, source, pillar.damage);
			near.push(0.0, PILLAR_THROW, 0.0);
			near.syncVelocity = true;
			ZFighterPoise.wear(near, POISE_WEAR);
		}
		level.explode(owner, source, ZFighterKi.GROUND_ONLY, base.x, base.y + 0.3, base.z, PILLAR_BLAST, false, Level.ExplosionInteraction.NONE);
		ZFighterKi.showBeam(level, base, top, (float)PILLAR_RADIUS, pillar.color);
		DustParticleOptions dust = new DustParticleOptions(pillar.color, 2.0F);
		// a dome of motes at the foot, then sparks up the column
		for (int i = 0; i < 48; i++) {
			Vec3 dir = new Vec3(level.getRandom().nextGaussian(), Math.abs(level.getRandom().nextGaussian()), level.getRandom().nextGaussian()).normalize();
			level.sendParticles(dust, true, false, base.x, base.y + 0.2, base.z, 0, dir.x, dir.y, dir.z, 0.5);
		}
		for (double y = base.y; y < top.y; y += 1.0) {
			level.sendParticles(ParticleTypes.END_ROD, true, false, over.x, y, over.z, 2, PILLAR_RADIUS * 0.4, 0.4, PILLAR_RADIUS * 0.4, 0.05);
		}
		level.playSound(null, base.x, base.y, base.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.1F);
		level.playSound(null, over.x, over.y, over.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.3F);
		Shakes.around(level, base, 16.0, 0.6F);
	}
}
