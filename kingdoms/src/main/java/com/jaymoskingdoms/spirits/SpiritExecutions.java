package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.280) Executions. Andy (2026-10-28): "they should also all have execution animations". Each spirit finishes a helpless
 * foe (downed or crippled, Powers' {@code Finishers.helpless}) in a short scene of its own, the arms playing it out pose by
 * pose ({@link Spirits#show}, the hand reaching to the foe) while the foe is held where the scene wants it, and the keeper's
 * camera films it when Jaymo's Powers is there ({@link PowersCompat#film}):
 * <ul>
 * <li><b>Grimsby, Last Lesson</b>: the blade turns sword, spear and axe in three cuts, and the greatsword ends it.</li>
 * <li><b>Varro, Crush</b>: the gauntlet seizes the foe, hoists it high, squeezes, and slams it into the ground.</li>
 * <li><b>Ambrose, Last Rites</b>: the great palm lifts the foe up in light, and both hands close on it.</li>
 * <li><b>Thessaly, Last Lock</b>: the glove draws the foe in, reaches into its chest and turns, like a key.</li>
 * <li><b>The Quiet One, Hush</b>: one of the other four's, in silence, and different each time.</li>
 * </ul>
 * A scene ends early if the foe dies or is lost, the keeper falls or moves off, or the spirit is put away; the foe is let go.
 */
public final class SpiritExecutions {
	/** Mana, cooldown (ticks) and the spirit level an execution unlocks at. */
	static final float COST = 20.0F;
	static final int COOLDOWN = 300, LEVEL = 5;
	/** How far the foe may lie (blocks beyond its box), plus this much per unit of the spirit's size; beyond {@code LOSE} the scene ends. */
	static final double REACH = 3.5, REACH_PER_SIZE = 1.0, LOSE = 10.0;
	/** Damage of each cut before the last (scaled by the spirit's level); the last one kills. */
	static final float CUT = 2.0F;
	/** Varro hoists the foe this high (blocks); Ambrose lifts it this high; Thessaly draws it to this far ahead of you. */
	static final double HOIST = 2.5, RISE = 1.5, DRAW_TO = 1.6;
	/** The camera: who sees the bars, within blocks. */
	static final double WITNESSES = 24.0;

	/** The scenes' lengths (ticks), by the style they play. */
	static final int[] LENGTH = {36, 40, 44, 34};

	/** A scene under way: the keeper, the foe, the scene it plays ({@code style}: Grimsby's to Thessaly's), whether silent (the Quiet One's). */
	private static final class Scene {
		final UUID keeper;
		final LivingEntity foe;
		final int style;
		final boolean silent;
		final long start;
		final Vec3 base;
		final boolean gravity;
		final float scale;
		int done = -1;

		Scene(UUID keeper, LivingEntity foe, int style, boolean silent, long start, float scale) {
			this.keeper = keeper;
			this.foe = foe;
			this.style = style;
			this.silent = silent;
			this.start = start;
			this.base = foe.position();
			this.gravity = !foe.isNoGravity();
			this.scale = scale;
		}
	}

	private static final Map<UUID, Scene> SCENES = new HashMap<>();

	private SpiritExecutions() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpiritExecutions::tickAll);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Scene scene : SCENES.values()) {
				release(scene);
			}
			SCENES.clear();
		});
	}

	/** The execution move of {@code kind} (Grimsby's keeps the old Finisher's id). */
	static Move move(String id, int kind) {
		return new Execute(id, kind);
	}

	private record Execute(String id, int kind) implements Move {
		@Override
		public float cost() {
			return COST;
		}

		@Override
		public int cooldown() {
			return COOLDOWN;
		}

		@Override
		public int level() {
			return LEVEL;
		}

		@Override
		public int pose() {
			return Poses.FINISH;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			return begin(player, state, this.kind);
		}
	}

	private static boolean begin(ServerPlayer player, SpiritState state, int kind) {
		if (SCENES.containsKey(player.getUUID()) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		float size = Spirits.of(kind).size(state);
		LivingEntity foe = target(player, REACH + REACH_PER_SIZE * Math.min(size, 2.0F));
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.execute.none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		boolean quiet = kind == Spirits.QUIET;
		int style = quiet ? player.getRandom().nextInt(LENGTH.length) : kind;
		Scene scene = new Scene(player.getUUID(), foe, style, quiet, level.getGameTime(), Spirits.damageScale(state));
		SCENES.put(player.getUUID(), scene);
		if (foe instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		// the first beat is the cast's own pose
		CastHints.setPose(player, firstPose(style));
		CastHints.setReach(player, player.getEyePosition().distanceTo(foe.getBoundingBox().getCenter()));
		PowersCompat.film(player, player, PowersCompat.FILM_ORBIT, LENGTH[style], foe.position(), foe, WITNESSES);
		beat(level, player, scene, 0);
		return true;
	}

	/** The helpless foe in sight within {@code reach}, or else the nearest one ahead. */
	private static @Nullable LivingEntity target(ServerPlayer player, double reach) {
		LivingEntity seen = Arcana.sighted(player);
		if (fits(player, seen, reach)) {
			return seen;
		}
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : SpiritFoes.cone(player, reach + 1.0, 45.0, false)) {
			double d = e.distanceToSqr(player);
			if (d < bestDist && fits(player, e, reach)) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	private static boolean fits(ServerPlayer player, @Nullable LivingEntity foe, double reach) {
		return foe != null && foe.isAlive() && foe.distanceTo(player) - foe.getBbWidth() * 0.5 <= reach
			&& SpiritFoes.foe(player, foe, true) && PowersCompat.helpless(foe) && !held(foe);
	}

	private static boolean held(LivingEntity foe) {
		for (Scene scene : SCENES.values()) {
			if (scene.foe == foe) {
				return true;
			}
		}
		return false;
	}

	private static int firstPose(int style) {
		return switch (style) {
			case Spirits.GRIMSBY -> Poses.SWORD;
			case Spirits.VARRO -> Poses.GRAB;
			case Spirits.AMBROSE -> Poses.BLESS;
			default -> Poses.PULL;
		};
	}

	private static void tickAll(MinecraftServer server) {
		if (SCENES.isEmpty()) {
			return;
		}
		for (Iterator<Scene> it = SCENES.values().iterator(); it.hasNext();) {
			Scene scene = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(scene.keeper);
			LivingEntity foe = scene.foe;
			if (player == null || !player.isAlive() || !Spirits.out(player) || !(player.level() instanceof ServerLevel level)
				|| foe.level() != level || foe.isRemoved() || !foe.isAlive() || foe.distanceTo(player) > LOSE) {
				release(scene);
				it.remove();
				continue;
			}
			int t = (int)(level.getGameTime() - scene.start);
			hold(player, scene, t);
			if (t > scene.done) {
				beat(level, player, scene, t);
			}
			if (t >= LENGTH[scene.style]) {
				release(scene);
				it.remove();
			}
		}
	}

	/** Holds the foe where the scene has it at tick {@code t} (a player lying downed is left where they lie). */
	private static void hold(ServerPlayer player, Scene scene, int t) {
		LivingEntity foe = scene.foe;
		if (foe instanceof Player) {
			return;
		}
		Vec3 at = scene.base;
		switch (scene.style) {
			case Spirits.VARRO -> {
				// seized, hoisted over four to twenty ticks, held, then driven down at twenty-eight
				double up = t < 4 ? 0.0 : t < 20 ? HOIST * smooth((t - 4) / 16.0F) : t < 28 ? HOIST : HOIST * (1.0 - Math.min(1.0, (t - 28) / 3.0));
				at = at.add(0.0, up, 0.0);
			}
			case Spirits.AMBROSE -> at = at.add(0.0, RISE * smooth(Math.min(1.0F, t / 24.0F)), 0.0);
			case Spirits.THESSALY -> {
				// drawn in to just ahead of the keeper over the first eight ticks
				Vec3 flat = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
				flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
				Vec3 near = new Vec3(player.getX(), scene.base.y, player.getZ()).add(flat.scale(DRAW_TO));
				at = scene.base.lerp(near, smooth(Math.min(1.0F, t / 8.0F)));
			}
			default -> {
			}
		}
		foe.setNoGravity(scene.style == Spirits.VARRO || scene.style == Spirits.AMBROSE || !scene.gravity);
		foe.setDeltaMovement(Vec3.ZERO);
		foe.teleportTo(at.x, at.y, at.z);
		foe.needsSync = true;
		if (foe instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
	}

	private static void release(Scene scene) {
		if (scene.foe.isAlive() && !(scene.foe instanceof Player)) {
			scene.foe.setNoGravity(!scene.gravity);
		}
	}

	/** What happens at tick {@code t} of the scene: the arms' next pose, the cut, the sound, and at the end the kill. */
	private static void beat(ServerLevel level, ServerPlayer player, Scene scene, int t) {
		scene.done = t;
		LivingEntity foe = scene.foe;
		Vec3 at = foe.getBoundingBox().getCenter();
		double out = player.getEyePosition().distanceTo(at);
		switch (scene.style) {
			case Spirits.GRIMSBY -> {
				switch (t) {
					case 0 -> cut(level, player, scene, SoundEvents.PLAYER_ATTACK_SWEEP, ParticleTypes.SWEEP_ATTACK, at.add(-0.7, 0.7, 0.0), at.add(0.7, -0.7, 0.0));
					case 8 -> {
						Spirits.show(player, Poses.SPEAR, out);
						cut(level, player, scene, SoundEvents.TRIDENT_HIT, ParticleTypes.CRIT, at.add(0.0, 0.0, -0.8), at.add(0.0, 0.0, 0.8));
					}
					case 16 -> {
						Spirits.show(player, Poses.AXE, out);
						cut(level, player, scene, SoundEvents.PLAYER_ATTACK_STRONG, ParticleTypes.FLAME, at.add(0.0, 0.9, 0.0), at.add(0.0, -0.6, 0.0));
					}
					case 26 -> Spirits.show(player, Poses.GREATSWORD, out);
					case 31 -> end(level, player, scene, SoundEvents.PLAYER_ATTACK_CRIT, ParticleTypes.SWEEP_ATTACK, 0.4F);
					default -> {
					}
				}
			}
			case Spirits.VARRO -> {
				switch (t) {
					case 0 -> sound(level, scene, at, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.6F);
					case 8 -> Spirits.show(player, Poses.HOLD, out);
					case 22 -> {
						Spirits.show(player, Poses.PIN, out);
						cut(level, player, scene, SoundEvents.BONE_BLOCK_BREAK, ParticleTypes.DAMAGE_INDICATOR, at.add(-0.4, 0.0, 0.0), at.add(0.4, 0.0, 0.0));
						PowersCompat.shake(player, 0.25F);
					}
					case 28 -> Spirits.show(player, Poses.SLAM, out);
					case 31 -> {
						Vec3 ground = foe.position();
						level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 0.3, ground.z, 1, 0.0, 0.0, 0.0, 0.0);
						for (int n = 0; n < 16; n++) {
							double a = n * Mth.TWO_PI / 16.0;
							level.sendParticles(ParticleTypes.CLOUD, ground.x, ground.y + 0.1, ground.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.35);
						}
						sound(level, scene, ground, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.2F);
						PowersCompat.shake(player, 0.6F);
						end(level, player, scene, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT, 0.6F);
					}
					default -> {
					}
				}
			}
			case Spirits.AMBROSE -> {
				if (t < 24 && t % 4 == 0) {
					level.sendParticles(scene.silent ? ParticleTypes.WHITE_ASH : ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.4, 0.6, 0.4, 0.02);
				}
				switch (t) {
					case 0 -> sound(level, scene, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
					case 12 -> sound(level, scene, at, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.4F);
					case 24 -> Spirits.show(player, Poses.TRAP, out);
					case 30 -> {
						level.sendParticles(scene.silent ? ParticleTypes.WHITE_ASH : ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 30, 0.3, 0.4, 0.3, 0.4);
						sound(level, scene, at, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
						end(level, player, scene, SoundEvents.AMETHYST_CLUSTER_BREAK, ParticleTypes.END_ROD, 0.3F);
					}
					default -> {
					}
				}
			}
			default -> {
				switch (t) {
					case 0 -> sound(level, scene, at, SoundEvents.CHAIN_BREAK, 0.8F, 1.4F);
					case 10 -> {
						Spirits.show(player, Poses.STEAL, out);
						cut(level, player, scene, SoundEvents.ITEM_PICKUP, ParticleTypes.ENCHANTED_HIT, at.add(0.0, 0.2, 0.0), at);
					}
					case 18 -> {
						Spirits.show(player, Poses.UNLOCK, out);
						sound(level, scene, at, SoundEvents.IRON_DOOR_OPEN, 0.7F, 1.6F);
					}
					case 26 -> end(level, player, scene, SoundEvents.LEVER_CLICK, ParticleTypes.ENCHANTED_HIT, 0.2F);
					default -> {
					}
				}
			}
		}
	}

	/** A cut before the last: a streak through the foe, a sound, a little hurt. */
	private static void cut(ServerLevel level, ServerPlayer player, Scene scene, SoundEvent sound, ParticleOptions spark, Vec3 from, Vec3 to) {
		SpiritFoes.line(level, scene.silent ? ParticleTypes.WHITE_ASH : spark, from, to, 8);
		sound(level, scene, to, sound, 1.0F, 0.8F);
		LivingEntity foe = scene.foe;
		if (foe.getHealth() > CUT * scene.scale + 1.0F) {
			foe.setInvulnerableTime(0);
			SpiritFoes.strike(level, player, foe, CUT * scene.scale);
		}
	}

	/** The end: the blow that kills (and if the hurt didn't take, the foe dies anyway: it was an execution). */
	private static void end(ServerLevel level, ServerPlayer player, Scene scene, SoundEvent sound, ParticleOptions spark, float shake) {
		LivingEntity foe = scene.foe;
		Vec3 at = foe.getBoundingBox().getCenter();
		level.sendParticles(scene.silent ? ParticleTypes.WHITE_ASH : spark, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.15);
		Mana.sendSeen(level, Mana.MOTE, at.x, at.y, at.z, 24, 0.4, 0.5, 0.4, 0.08);
		sound(level, scene, at, sound, 1.0F, 0.7F);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, Math.max(foe.getHealth() + foe.getAbsorptionAmount() + 1.0F, 30.0F * scene.scale));
		if (foe.isAlive()) {
			foe.kill(level);
		}
		if (shake > 0.0F) {
			PowersCompat.shake(player, shake);
		}
		release(scene);
	}

	/** A sound of the scene (none for the Quiet One's: it ends things in silence). */
	private static void sound(ServerLevel level, Scene scene, Vec3 at, SoundEvent sound, float volume, float pitch) {
		if (!scene.silent) {
			level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
		}
	}

	private static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}
}
