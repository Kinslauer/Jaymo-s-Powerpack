package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Quiet One's later arts (Andy, 2026-11-14: "okay start on the other spirits"; who he was, Andy 2026-11-11: the first
 * to use the web, a cloak that becomes his webbing and tendrils, and for quick work up close a hidden blade). Nothing here
 * makes a sound of its own.
 * <ul>
 * <li><b>Thread</b> (level {@value #THREAD_LEVEL}): a line to what you look at within {@value #THREAD_REACH} blocks, and you
 * are drawn along it: to a wall or a ledge, or onto a foe, with the blade.</li>
 * <li><b>Shroud</b> ({@value #WRAP_LEVEL}): the foe you look at is wrapped where it stands for {@value #WRAP_TICKS} ticks.</li>
 * <li><b>Hidden Blade</b> ({@value #BLADE_LEVEL}): one short thrust at the foe in reach; from behind, or into one that is
 * wrapped, off its feet or down, it lands {@value #BLADE_UNSEEN} times as hard.</li>
 * <li><b>Many Hands</b> ({@value #MANY_LEVEL}): the cloak comes apart into tendrils that lash every foe within
 * {@value #MANY_RANGE} blocks, {@value #MANY_LASHES} times.</li>
 * </ul>
 */
public final class QuietArts {
	static final int THREAD_LEVEL = 9, WRAP_LEVEL = 11, BLADE_LEVEL = 13, MANY_LEVEL = 15;
	static final double THREAD_REACH = 24.0, WRAP_REACH = 16.0, BLADE_REACH = 3.5, MANY_RANGE = 6.0;
	static final float THREAD_DAMAGE = 7.0F, BLADE_DAMAGE = 9.0F, BLADE_UNSEEN = 2.5F, MANY_DAMAGE = 4.0F;
	static final int WRAP_TICKS = 100, MANY_LASHES = 3, MANY_GAP = 5;
	static final int PALE = 0xC8C8DC;

	static final Move THREAD = new Ambrose.Def("thread", 10.0F, 80, THREAD_LEVEL, Poses.PULL, Mana.WILL, (p, s, c) -> thread(p, s));
	static final Move WRAP = new Ambrose.Def("shroud", 14.0F, 200, WRAP_LEVEL, Poses.GRAB, Mana.WARD, (p, s, c) -> wrap(p));
	static final Move BLADE = new Ambrose.Def("hidden_blade", 12.0F, 100, BLADE_LEVEL, Poses.SILENT, Mana.HARM, (p, s, c) -> blade(p, s));
	static final Move MANY = new Ambrose.Def("many_hands", 22.0F, 240, MANY_LEVEL, Poses.FLURRY, Mana.HARM, (p, s, c) -> manyHands(p, Spirits.damageScale(s)));
	static final List<Move> MOVES = List.of(THREAD, WRAP, BLADE, MANY);

	private static final class Lashing {
		final ServerPlayer player;
		final float scale;
		int left = MANY_LASHES;
		long next;

		Lashing(ServerPlayer player, float scale, long next) {
			this.player = player;
			this.scale = scale;
			this.next = next;
		}
	}

	private static final List<Lashing> LASHES = new ArrayList<>();

	private QuietArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Iterator<Lashing> it = LASHES.iterator(); it.hasNext();) {
				Lashing l = it.next();
				if (!l.player.isAlive() || l.player.isRemoved() || !(l.player.level() instanceof ServerLevel level)) {
					it.remove();
				} else if (level.getGameTime() >= l.next) {
					lash(level, l.player, l.scale);
					l.next += MANY_GAP;
					if (--l.left <= 0) {
						it.remove();
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LASHES.clear());
	}

	private static boolean thread(ServerPlayer player, SpiritState state) {
		ServerLevel level = (ServerLevel)player.level();
		for (LivingEntity foe : SpiritFoes.cone(player, THREAD_REACH, 8.0, true)) {
			if (player.hasLineOfSight(foe)) {
				return threadTo(player, foe, Spirits.damageScale(state));
			}
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(THREAD_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		Vec3 back = player.getLookAngle().scale(-0.8);
		Vec3 to = hit.getLocation().add(back.x, 0.0, back.z);
		for (double up = 1.2; up >= -1.6; up -= 0.4) {
			Vec3 p = to.add(0.0, up, 0.0);
			if (level.noCollision(player, player.getBoundingBox().move(p.subtract(player.position())))) {
				Fx.beam(level, eye, hit.getLocation(), 0.05F, PALE, 8);
				player.connection.teleport(p.x, p.y, p.z, player.getYRot(), player.getXRot());
				player.fallDistance = 0.0;
				return true;
			}
		}
		return false;
	}

	/** Along the line onto {@code foe}, with the blade. */
	static boolean threadTo(ServerPlayer player, LivingEntity foe, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 back = new Vec3(player.getX() - foe.getX(), 0.0, player.getZ() - foe.getZ());
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : back.normalize();
		if (!GrimsbyArts.arrive(level, player, foe.position().add(back.scale(foe.getBbWidth() * 0.5 + 1.2)), foe)) {
			return false;
		}
		Fx.beam(level, eye, foe.getBoundingBox().getCenter(), 0.05F, PALE, 8);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, THREAD_DAMAGE * scale);
		return true;
	}

	private static boolean wrap(ServerPlayer player) {
		for (LivingEntity foe : SpiritFoes.cone(player, WRAP_REACH, 10.0, true)) {
			if (player.hasLineOfSight(foe) && foe.getMaxHealth() <= 150.0F) {
				wrapUp(player, foe);
				CastHints.setReach(player, foe.distanceTo(player));
				return true;
			}
		}
		return false;
	}

	static void wrapUp(ServerPlayer player, LivingEntity foe) {
		ServerLevel level = (ServerLevel)player.level();
		Staggered.root(player, foe, WRAP_TICKS);
		level.sendParticles(ParticleTypes.WHITE_ASH, foe.getX(), foe.getY(0.5), foe.getZ(), 40, 0.3, 0.6, 0.3, 0.0);
		Mana.sendSeen(level, Fx.wisp(PALE, 1.0F), foe.getX(), foe.getY(0.5), foe.getZ(), 16, 0.3, 0.6, 0.3, 0.0);
	}

	private static boolean blade(ServerPlayer player, SpiritState state) {
		List<LivingEntity> foes = SpiritFoes.cone(player, BLADE_REACH, 40.0, true);
		return !foes.isEmpty() && stab(player, foes.get(0), Spirits.damageScale(state));
	}

	static boolean stab(ServerPlayer player, LivingEntity foe, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 to = new Vec3(player.getX() - foe.getX(), 0.0, player.getZ() - foe.getZ());
		Vec3 facing = new Vec3(foe.getLookAngle().x, 0.0, foe.getLookAngle().z);
		boolean behind = to.lengthSqr() > 1.0E-4 && facing.lengthSqr() > 1.0E-4 && to.normalize().dot(facing.normalize()) < -0.3;
		boolean unseen = behind || Staggered.is(foe) || Downed.isDowned(foe);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, BLADE_DAMAGE * scale * (unseen ? BLADE_UNSEEN : 1.0F));
		level.sendParticles(unseen ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), 8, 0.2, 0.3, 0.2, 0.2);
		return true;
	}

	/** The tendrils go out now, and twice more. */
	static boolean manyHands(ServerPlayer player, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		lash(level, player, scale);
		Lashing l = new Lashing(player, scale, level.getGameTime() + MANY_GAP);
		l.left = MANY_LASHES - 1;
		LASHES.add(l);
		return true;
	}

	private static void lash(ServerLevel level, ServerPlayer player, float scale) {
		Vec3 from = player.position().add(0.0, 1.2, 0.0);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MANY_RANGE),
			e -> SpiritFoes.foe(player, e, false) && e.distanceToSqr(player) <= MANY_RANGE * MANY_RANGE)) {
			foe.setInvulnerableTime(0);
			SpiritFoes.strike(level, player, foe, MANY_DAMAGE * scale);
			Fx.beam(level, from, foe.getBoundingBox().getCenter(), 0.05F, PALE, 4);
		}
	}
}
