package com.jaymospowers.rig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Mind: the Core's AI. Its personality ({@link Personality}) is picked once, at random, the first time the Core is
 * worn, and kept on the Core ({@link Rig#MIND}). Stage 1 only warns, in short lines on the action bar (lang
 * {@code rig.jaymospowers.mind.<personality>.<warning>}): low charge, low health, a projectile or an explosion about to
 * hit, lava or the void below a fall, and a hostile closing in from behind. Lines are rate-limited: one every
 * {@link #GAP} ticks at most, and the same warning not again for {@link #REPEAT} (urgent ones {@link #REPEAT_URGENT}).
 * The warnings also light the Helm's HUD ({@link #warnings}).
 *
 * <p>(0.11.83) A warning that turned out right (you were still alive {@link #PROOF_TICKS} later) teaches the Mind
 * ({@link RigLevels#WARNING_XP}); it remarks on each new level in its own voice ({@link #levelUp}).
 */
public final class RigMind {
	/** How often the Mind looks around (ticks). */
	static final int EVERY = 5;
	/** The fewest ticks between two lines, and between the same warning twice (urgent ones sooner). */
	static final int GAP = 60, REPEAT = 600, REPEAT_URGENT = 160;
	/** Low charge and low health (shares of full). */
	static final float LOW_CHARGE = 0.15F, LOW_HEALTH = 0.3F;
	/** Projectiles: looked for this far, a hit when they'll pass this close within this many ticks; slower ones ignored. */
	static final double PROJECTILE_RANGE = 16.0, PROJECTILE_MISS = 1.6, PROJECTILE_MIN_SPEED = 0.2;
	static final int PROJECTILE_TICKS = 25;
	/** Explosions: lit TNT this close, a creeper swelling this close. */
	static final double TNT_RANGE = 8.0, CREEPER_RANGE = 6.0;
	/** Falling at least this fast (b/t), lava this far below. */
	static final double FALLING = 0.25;
	static final int LAVA_DEPTH = 24;
	/** Hostiles hunting you from behind, this close (Darktide's augment: farther), this far behind your look (dot). */
	static final double BEHIND_RANGE = 8.0, DARKTIDE_BEHIND_RANGE = 14.0, BEHIND_DOT = -0.35;
	/** (0.11.83) How long after a danger warning you must still be alive for it to count as right (ticks). */
	static final int PROOF_TICKS = 200;

	public enum Personality {
		LOYAL, DRY, GRIM;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The warnings, most urgent first: their {@link RigNet} bit and lang key. */
	private enum Warning {
		EXPLOSION(RigNet.WARN_EXPLOSION, true), PROJECTILE(RigNet.WARN_PROJECTILE, true), LAVA(RigNet.WARN_LAVA, true),
		VOID(RigNet.WARN_VOID, true), BEHIND(RigNet.WARN_BEHIND, false), LOW_HEALTH(RigNet.WARN_LOW_HEALTH, false),
		LOW_CHARGE(RigNet.WARN_LOW_CHARGE, false);

		final int bit;
		final boolean urgent;

		Warning(int bit, boolean urgent) {
			this.bit = bit;
			this.urgent = urgent;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static final class State {
		int warnings;
		long lastLine = Long.MIN_VALUE / 2;
		/** (0.11.83) When the last danger warning is proved right (0: none waiting). */
		long proofAt;
		final long[] lastWarned = new long[Warning.values().length];

		State() {
			java.util.Arrays.fill(this.lastWarned, Long.MIN_VALUE / 2);
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private RigMind() {
	}

	static void forget(UUID player) {
		STATES.remove(player);
	}

	/** The warnings standing for this player now (the last look), as {@link RigNet} bits. */
	static int warnings(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null ? 0 : state.warnings;
	}

	static void tick(ServerPlayer player) {
		ItemStack core = Rig.core(player);
		boolean helm = Rig.wears(player, Rig.Piece.HELM);
		if (core.isEmpty() && !helm) {
			STATES.remove(player.getUUID());
			return;
		}
		if (!core.isEmpty() && core.get(Rig.MIND) == null) {
			// the first time this Core is worn: the Mind wakes, and is who it is from now on
			Personality[] all = Personality.values();
			Personality picked = all[player.getRandom().nextInt(all.length)];
			core.set(Rig.MIND, picked.id());
			player.sendSystemMessage(line(picked.id(), "wake"));
			Rig.Augment augment = Rig.augment(player);
			if (augment != null) {
				// it adapts to the power it finds in its wearer
				player.sendSystemMessage(Component.translatable("rig.jaymospowers.augment." + augment.id()).withStyle(ChatFormatting.DARK_AQUA));
			}
			ping(player, 1.2F);
		}
		if (player.tickCount % EVERY != 0) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), u -> new State());
		state.warnings = look(player);
		String personality = core.isEmpty() ? null : core.get(Rig.MIND);
		if (personality == null) {
			// the Helm alone shows the warnings; only the Core's Mind speaks
			return;
		}
		long now = player.level().getGameTime();
		if (state.proofAt != 0 && now >= state.proofAt) {
			state.proofAt = 0;
			if (player.isAlive()) {
				RigLevels.add(player, RigLevels.WARNING_XP);
			}
		}
		if (now - state.lastLine < GAP) {
			return;
		}
		for (Warning warning : Warning.values()) {
			if ((state.warnings & warning.bit) == 0) {
				continue;
			}
			if (now - state.lastWarned[warning.ordinal()] < (warning.urgent ? REPEAT_URGENT : REPEAT)) {
				continue;
			}
			state.lastWarned[warning.ordinal()] = now;
			state.lastLine = now;
			if ((warning.urgent || warning == Warning.LOW_HEALTH) && state.proofAt == 0) {
				state.proofAt = now + PROOF_TICKS;
			}
			player.sendOverlayMessage(line(personality, warning.key()));
			ping(player, warning.urgent ? 1.8F : 1.2F);
			return;
		}
	}

	private static int look(ServerPlayer player) {
		int bits = 0;
		ServerLevel level = player.level();
		if (!Rig.core(player).isEmpty() && Rig.chargeShare(player) < LOW_CHARGE) {
			bits |= RigNet.WARN_LOW_CHARGE;
		}
		if (player.getHealth() < player.getMaxHealth() * LOW_HEALTH) {
			bits |= RigNet.WARN_LOW_HEALTH;
		}
		Vec3 centre = player.getBoundingBox().getCenter();
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(PROJECTILE_RANGE))) {
			if (projectile.getOwner() == player) {
				continue;
			}
			Vec3 v = projectile.getDeltaMovement();
			double speed2 = v.lengthSqr();
			if (speed2 < PROJECTILE_MIN_SPEED * PROJECTILE_MIN_SPEED) {
				continue;
			}
			Vec3 rel = centre.subtract(projectile.position());
			double t = rel.dot(v) / speed2;
			if (t > 0.0 && t < PROJECTILE_TICKS && rel.subtract(v.scale(t)).lengthSqr() < PROJECTILE_MISS * PROJECTILE_MISS) {
				bits |= RigNet.WARN_PROJECTILE;
				break;
			}
		}
		if (!level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(TNT_RANGE)).isEmpty()
			|| !level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(CREEPER_RANGE), c -> c.isAlive() && c.getSwellDir() > 0).isEmpty()) {
			bits |= RigNet.WARN_EXPLOSION;
		}
		if (!player.onGround() && !player.isFallFlying() && player.getDeltaMovement().y < -FALLING && !RigActives.hovering(player)) {
			BlockPos feet = player.blockPosition();
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) <= level.getMinY()) {
				bits |= RigNet.WARN_VOID;
			} else {
				BlockPos.MutableBlockPos at = feet.mutable();
				for (int i = 0; i < LAVA_DEPTH && at.getY() > level.getMinY(); i++) {
					at.move(0, -1, 0);
					if (level.getFluidState(at).is(FluidTags.LAVA)) {
						bits |= RigNet.WARN_LAVA;
						break;
					}
					if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
						break;
					}
				}
			}
		}
		double range = Rig.augment(player) == Rig.Augment.DARKTIDE ? DARKTIDE_BEHIND_RANGE : BEHIND_RANGE;
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() > 1.0E-4) {
			Vec3 facing = look.normalize();
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
				m -> m instanceof Enemy && m.isAlive() && m.getTarget() == player)) {
				Vec3 to = mob.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
				if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(facing) < BEHIND_DOT) {
					bits |= RigNet.WARN_BEHIND;
					break;
				}
			}
		}
		return bits;
	}

	/** Out of charge, an active refused (rate-limited like a warning). */
	static void empty(ServerPlayer player) {
		say(player, "empty", 1.0F);
	}

	/** Struck by lightning: full. */
	static void struck(ServerPlayer player) {
		say(player, "struck", 1.5F);
	}

	/** The report key: the charge, in the Mind's own words (no Core: the suit's dead weight). */
	static void report(ServerPlayer player) {
		ItemStack core = Rig.core(player);
		String personality = core.isEmpty() ? null : core.get(Rig.MIND);
		if (personality == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.no_mind"));
			return;
		}
		int percent = Math.round(Rig.chargeShare(player) * 100.0F);
		player.sendOverlayMessage(line(personality, "report", percent));
		ping(player, 1.0F);
	}

	/** A line in the Mind's voice on the action bar (rate-limited: not within a third of {@link #GAP} of the last). */
	static void say(ServerPlayer player, String key, float pitch, Object... args) {
		ItemStack core = Rig.core(player);
		String personality = core.isEmpty() ? null : core.get(Rig.MIND);
		if (personality == null) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), u -> new State());
		long now = player.level().getGameTime();
		if (now - state.lastLine < GAP / 3) {
			return;
		}
		state.lastLine = now;
		player.sendOverlayMessage(line(personality, key, args));
		ping(player, pitch);
	}

	/** (0.11.83) A line in the Mind's voice on the action bar, now, whatever was said last. */
	static void sayAlways(ServerPlayer player, String key, float pitch, Object... args) {
		String personality = personality(Rig.core(player));
		if (personality == null) {
			return;
		}
		STATES.computeIfAbsent(player.getUUID(), u -> new State()).lastLine = player.level().getGameTime();
		player.sendOverlayMessage(line(personality, key, args));
		ping(player, pitch);
	}

	/** (0.11.83) A longer line in the Mind's voice, in chat (scan results, level-ups). */
	static void tell(ServerPlayer player, ItemStack core, String key, Object... args) {
		String personality = personality(core);
		if (personality != null) {
			player.sendSystemMessage(line(personality, key, args));
		}
	}

	/** The Core's personality id, or null (no Core, or its Mind hasn't woken). */
	static @org.jspecify.annotations.Nullable String personality(ItemStack core) {
		return core.isEmpty() ? null : core.get(Rig.MIND);
	}

	/** (0.11.83) The Mind reached {@code level}: it remarks on it, and names what it can do now. */
	static void levelUp(ServerPlayer player, ItemStack core, int level) {
		tell(player, core, "level." + level);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig.unlock." + level,
			Component.keybind("key.jaymospowers.rig"), Component.keybind("key.jaymospowers.ability_4"), Component.keybind("key.jaymospowers.ultimate"),
			Component.keybind("key.jump")).withStyle(ChatFormatting.DARK_AQUA));
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP),
			SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.6F, 1.6F, player.getRandom().nextLong()));
		ping(player, 1.8F);
	}

	private static MutableComponent line(String personality, String key, Object... args) {
		return Component.translatable("rig.jaymospowers.mind.prefix").withStyle(ChatFormatting.DARK_AQUA)
			.append(Component.translatable("rig.jaymospowers.mind." + personality + "." + key, args).withStyle(ChatFormatting.AQUA));
	}

	/** A soft chime only the wearer hears. */
	private static void ping(ServerPlayer player, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME),
			SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.6F, pitch, player.getRandom().nextLong()));
	}
}
