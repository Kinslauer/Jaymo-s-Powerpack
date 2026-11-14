package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grimsby's later arts (Andy, 2026-11-14: "more of the shadow of war combat inspiration for varro, and grimsby"; the
 * ideas only, our own names and looks): the old swordsman stops fencing from behind your shoulder and starts moving you.
 * <ul>
 * <li><b>Over the Shoulder</b> (level {@value #VAULT_LEVEL}): you go over the foe in front and come down behind it, and the
 * blade is already in its back; it is off its feet for a moment.</li>
 * <li><b>Short Road</b> ({@value #STEP_LEVEL}): the foe you look at, however far within {@value #STEP_REACH} blocks: you are
 * there, and it is cut. A kill makes it ready again at once, so one death leads to the next.</li>
 * <li><b>Pale Glaive</b> ({@value #GLAIVE_LEVEL}): a long blade swept the whole way round: everything near is cut, thrown and
 * off its feet. A second sweep from level {@value #GLAIVE_TWO}, a third from {@value #GLAIVE_THREE}.</li>
 * <li><b>Take the Rest</b> ({@value #DRAIN_LEVEL}): from a foe that is off its feet or down: what mana and strength it has
 * left goes into you.</li>
 * </ul>
 */
public final class GrimsbyArts {
	static final int VAULT_LEVEL = 9, STEP_LEVEL = 11, GLAIVE_LEVEL = 13, DRAIN_LEVEL = 15, GLAIVE_TWO = 16, GLAIVE_THREE = 19;
	/** Over the Shoulder: reach, the cut (half again what a flank would be: it is in the back), how long the foe is off its feet. */
	static final double VAULT_REACH = 5.0, VAULT_BEHIND = 1.1;
	static final float VAULT_DAMAGE = 10.0F;
	static final int VAULT_STAGGER = 25;
	/** Short Road: reach, the cut, how short of the foe you arrive, and the cooldown a kill leaves. */
	static final double STEP_REACH = 20.0, STEP_SHORT = 1.3;
	static final float STEP_DAMAGE = 8.0F;
	static final int STEP_CHAIN_COOLDOWN = 10;
	/** Pale Glaive: its reach, each sweep's cut and throw, ticks between sweeps, how long those it hits are off their feet. */
	static final double GLAIVE_REACH = 4.5, GLAIVE_THROW = 0.9;
	static final float GLAIVE_DAMAGE = 7.0F;
	static final int GLAIVE_GAP = 8, GLAIVE_STAGGER = 30;
	/** Take the Rest: reach, the hurt, and the mana and health it gives back. */
	static final double DRAIN_REACH = 4.0;
	static final float DRAIN_DAMAGE = 6.0F, DRAIN_MANA = 25.0F, DRAIN_HEALTH = 4.0F;

	static final Move VAULT = new Ambrose.Def("vault", 12.0F, 80, VAULT_LEVEL, Poses.SWORD, Mana.HARM, GrimsbyArts::vault);
	static final Move STEP = new Ambrose.Def("short_road", 14.0F, 120, STEP_LEVEL, Poses.SWORD, Mana.HARM, GrimsbyArts::step);
	static final Move GLAIVE = new Ambrose.Def("pale_glaive", 20.0F, 200, GLAIVE_LEVEL, Poses.GREATSWORD, Mana.HARM, GrimsbyArts::glaive);
	static final Move DRAIN = new Ambrose.Def("take_the_rest", 0.0F, 160, DRAIN_LEVEL, Poses.GRAB, Mana.WILL, GrimsbyArts::drain);
	static final List<Move> MOVES = List.of(VAULT, STEP, GLAIVE, DRAIN);

	/** Sweeps still to come: whose, how many, when the next, how hard. */
	private static final class Sweeping {
		final ServerPlayer player;
		final float scale;
		int left;
		long next;

		Sweeping(ServerPlayer player, float scale, int left, long next) {
			this.player = player;
			this.scale = scale;
			this.left = left;
			this.next = next;
		}
	}

	private static final List<Sweeping> SWEEPS = new ArrayList<>();

	private GrimsbyArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Iterator<Sweeping> it = SWEEPS.iterator(); it.hasNext();) {
				Sweeping s = it.next();
				if (!s.player.isAlive() || s.player.isRemoved() || !(s.player.level() instanceof ServerLevel level)) {
					it.remove();
				} else if (level.getGameTime() >= s.next) {
					sweep(level, s.player, s.scale);
					Spirits.show(s.player, Poses.GREATSWORD);
					s.next += GLAIVE_GAP;
					if (--s.left <= 0) {
						it.remove();
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SWEEPS.clear());
	}

	// ---- over the shoulder ---------------------------------------------------------------------------------------------

	private static boolean vault(ServerPlayer player, SpiritState state, int charge) {
		List<LivingEntity> foes = SpiritFoes.cone(player, VAULT_REACH, 40.0, true);
		return !foes.isEmpty() && vaultOver(player, foes.get(0), Spirits.damageScale(state));
	}

	/** {@code player} goes over {@code foe} to stand behind it, and cuts it in the back; false if there is no room there. */
	static boolean vaultOver(ServerPlayer player, LivingEntity foe, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 across = new Vec3(foe.getX() - player.getX(), 0.0, foe.getZ() - player.getZ());
		if (across.lengthSqr() < 1.0E-4) {
			return false;
		}
		across = across.normalize();
		Vec3 land = foe.position().add(across.scale(foe.getBbWidth() * 0.5 + VAULT_BEHIND));
		if (!arrive(level, player, land, foe)) {
			return false;
		}
		Vec3 top = foe.position().add(0.0, foe.getBbHeight() + 0.8, 0.0);
		SpiritFoes.line(level, ParticleTypes.CRIT, player.getEyePosition(), top, 6);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, VAULT_DAMAGE * scale);
		Staggered.stagger(player, foe, VAULT_STAGGER);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY(0.6), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.8F);
		return true;
	}

	/** Puts {@code player} at {@code at} (or a little above it), facing {@code foe}; false if they don't fit there. */
	static boolean arrive(ServerLevel level, ServerPlayer player, Vec3 at, LivingEntity foe) {
		for (double up = 0.0; up <= 1.01; up += 0.5) {
			Vec3 p = at.add(0.0, up, 0.0);
			AABB box = player.getBoundingBox().move(p.subtract(player.position()));
			if (level.noCollision(player, box)) {
				Vec3 from = player.position();
				float yaw = (float)(Mth.atan2(foe.getZ() - p.z, foe.getX() - p.x) * Mth.RAD_TO_DEG) - 90.0F;
				player.connection.teleport(p.x, p.y, p.z, yaw, 10.0F);
				player.fallDistance = 0.0;
				Mana.sendSeen(level, GrimsbyBlades.EDGE, from.x, from.y + 1.0, from.z, 14, 0.3, 0.6, 0.3, 0.02);
				Mana.sendSeen(level, GrimsbyBlades.EDGE, p.x, p.y + 1.0, p.z, 14, 0.3, 0.6, 0.3, 0.02);
				return true;
			}
		}
		return false;
	}

	// ---- short road ----------------------------------------------------------------------------------------------------

	private static boolean step(ServerPlayer player, SpiritState state, int charge) {
		List<LivingEntity> foes = SpiritFoes.cone(player, STEP_REACH, 10.0, true);
		LivingEntity foe = null;
		for (LivingEntity e : foes) {
			if (player.hasLineOfSight(e)) {
				foe = e;
				break;
			}
		}
		if (foe == null || !stepTo(player, foe, Spirits.damageScale(state))) {
			return false;
		}
		if (!foe.isAlive()) {
			CastHints.setCooldown(player, STEP_CHAIN_COOLDOWN);
		}
		return true;
	}

	/** {@code player} is at {@code foe} at once, and cuts it; false if there is no room. */
	static boolean stepTo(ServerPlayer player, LivingEntity foe, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 back = new Vec3(player.getX() - foe.getX(), 0.0, player.getZ() - foe.getZ());
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : back.normalize();
		Vec3 from = player.getEyePosition();
		if (!arrive(level, player, foe.position().add(back.scale(foe.getBbWidth() * 0.5 + STEP_SHORT)), foe)) {
			return false;
		}
		Fx.beam(level, from, foe.getBoundingBox().getCenter(), 0.12F, 0x9AB8FF, 6);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, STEP_DAMAGE * scale);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	// ---- pale glaive ---------------------------------------------------------------------------------------------------

	private static boolean glaive(ServerPlayer player, SpiritState state, int charge) {
		return glaiveNow(player, state, Spirits.damageScale(state));
	}

	/** The glaive goes round now, and as many times more as the spirit's level gives. */
	static boolean glaiveNow(ServerPlayer player, SpiritState state, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		sweep(level, player, scale);
		int more = (state.level() >= GLAIVE_TWO ? 1 : 0) + (state.level() >= GLAIVE_THREE ? 1 : 0);
		if (more > 0) {
			SWEEPS.add(new Sweeping(player, scale, more, level.getGameTime() + GLAIVE_GAP));
		}
		PowersCompat.shake(player, 0.3F);
		return true;
	}

	private static void sweep(ServerLevel level, ServerPlayer player, float scale) {
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GLAIVE_REACH),
			e -> SpiritFoes.foe(player, e, false) && e.distanceToSqr(player) <= (GLAIVE_REACH + 1.0) * (GLAIVE_REACH + 1.0))) {
			foe.setInvulnerableTime(0);
			SpiritFoes.strike(level, player, foe, GLAIVE_DAMAGE * scale);
			SpiritFoes.shove(level, player, foe, GLAIVE_THROW);
			Staggered.stagger(player, foe, GLAIVE_STAGGER);
		}
		Fx.ring(level, player.position().add(0.0, 1.0, 0.0), (float)GLAIVE_REACH, 0x9AB8FF);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 6, 1.8, 0.1, 1.8, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	// ---- take the rest -------------------------------------------------------------------------------------------------

	private static boolean drain(ServerPlayer player, SpiritState state, int charge) {
		LivingEntity foe = helpless(player);
		return foe != null && drainFrom(player, foe, Spirits.damageScale(state));
	}

	/** The nearest foe in front that is off its feet or down, or null. */
	static @Nullable LivingEntity helpless(ServerPlayer player) {
		for (LivingEntity e : SpiritFoes.cone(player, DRAIN_REACH, 60.0, true)) {
			if (Staggered.is(e) || Downed.isDowned(e)) {
				return e;
			}
		}
		return null;
	}

	static boolean drainFrom(ServerPlayer player, LivingEntity foe, float scale) {
		ServerLevel level = (ServerLevel)player.level();
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, DRAIN_DAMAGE * scale);
		Mana.give(player, DRAIN_MANA);
		player.heal(DRAIN_HEALTH);
		Fx.beam(level, foe.getBoundingBox().getCenter(), player.getEyePosition().add(0.0, -0.3, 0.0), 0.1F, 0x4C8DFF, 12);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}
}
