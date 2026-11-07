package com.jaymospowers.power.xenoform;

import com.jaymospowers.power.darktide.DarktideStatus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The band's flash when its wearer changes shape (slamming into an alien, or turning back): a burst of bright green light
 * that blinds whoever sees it for {@link #TICKS}. Players in sight get a green whiteout on screen ({@link XenoNet.Flash},
 * drawn by the client's {@code BandFlashHud}); creatures in sight are blinded, lose their target and stand dazed
 * (Darktide's stun). The wearer isn't blinded by their own band.
 */
public final class BandFlash {
	/** How far the flash reaches (blocks) and how long it blinds (ticks: half a second). */
	public static final double RADIUS = 10.0;
	public static final int TICKS = 10;

	private BandFlash() {
	}

	/** {@code wearer}'s band flashes at {@code at}. */
	static void flash(ServerPlayer wearer, Vec3 at) {
		for (LivingEntity e : wearer.level().getEntitiesOfClass(LivingEntity.class, wearer.getBoundingBox().inflate(RADIUS),
			e -> e != wearer && e.isAlive() && !e.isSpectator() && e.distanceToSqr(at) <= RADIUS * RADIUS)) {
			// only what can see the band: nothing behind a wall
			if (!e.hasLineOfSight(wearer)) {
				continue;
			}
			if (e instanceof ServerPlayer seer) {
				ServerPlayNetworking.send(seer, new XenoNet.Flash(at, BandStyle.glow(wearer)));
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TICKS, 0, false, false), wearer);
				if (e instanceof Mob mob) {
					mob.setTarget(null);
				}
				DarktideStatus.stun(e, TICKS);
			}
		}
	}
}
