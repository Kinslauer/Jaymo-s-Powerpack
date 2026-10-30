package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.finisher.Finishers.Kind;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Wounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.25) Andy: "z fighter needs at least 3 bare handed executions, maybe more with different forms, for example
 * frostborn should use the beam". A Z Fighter's empty hands are a weapon, as the symbiote suit's are: sneak + attack held
 * on someone downed or crippled ({@link Finishers#HOLD_TICKS}, no Executioner perk, no kneeling at the head) plays one of
 * these instead of the neck snap, picked at random from what's open to him and never the same one twice running
 * ({@link #LAST}). They're {@link Finishers.Kind}s ({@code Z_*}, strike {@link Finishers.Strike#KI}), so the hold, the
 * roots, the body lying still, the view from behind and the poses ({@code FinisherPose}) are the finishers'; what's
 * here is what they look and sound like, beat by beat ({@link #tick}, {@link #blow}), and a short film for the ki ones.
 * <ul>
 * <li>Every Z Fighter: Point-Blank (a palm blast into the chest), Flurry (on a knee, fists too fast to count, the last
 * one cracking the ground) and Hammer Fist (both fists clasped overhead and brought down twice).</li>
 * <li>His lineage's, twice as likely ({@link #pool}): an Earthling (and those who read as one: Divine-born, Bio-built,
 * Rubber-folk) the Cupped Wave from the hip, a Warrior-born the Crimson Cannon, a Verdant the Spiral Lance from the brow,
 * a Frost-born the Frost Needle: unhurried, a leg, an arm, then the head, with his hair-thin beam ({@link FrostBeam}).</li>
 * <li>In any form: Supernova Palm, the hand on their chest and the aura poured through it until nothing's left (no
 * corpse), twice as likely too.</li>
 * </ul>
 * The ki moves cost ki ({@link #cost}, paid as they begin; without it only the fists' are open) and are in his ki
 * colour, the form's in a form ({@link ZFighterKi#formColor}). The Great Ape has none of these (his hands are the neck
 * snap's, as ever).
 */
public final class ZFighterExecutions {
	/** Ki each ki move costs (Point-Blank, the lineage beams, the Needle, Supernova). */
	static final float PALM_KI = 10.0F, BEAM_KI = 20.0F, NEEDLE_KI = 12.0F, NOVA_KI = 30.0F;
	/** How much likelier the lineage's move and the form's are than each of the three everyone has. */
	static final int OWN_WEIGHT = 2;
	/** The film for the ki moves: how far off others see its bars (blocks). */
	static final double FILM_WITNESSES = 16.0;
	/** How long each beam stays on them after the shot (ticks), and how thick it's drawn. */
	static final int PALM_BEAM_TICKS = 4, WAVE_BEAM_TICKS = 8, CANNON_BEAM_TICKS = 9, LANCE_BEAM_TICKS = 6, NOVA_PILLAR_TICKS = 8;
	static final float PALM_RADIUS = 0.28F, WAVE_RADIUS = 0.42F, CANNON_RADIUS = 0.5F, LANCE_RADIUS = 0.12F, NOVA_RADIUS = 0.7F;
	/** The Supernova's pillar of light (blocks up from the body). */
	static final double NOVA_PILLAR = 7.0;
	/** The cracks Flurry's and Hammer Fist's last blows leave (GroundCracks' length). */
	static final int CRACK_LENGTH = 4;
	/** Screen shake for those near the ki moves' kill, and how far. */
	static final float KI_AROUND_SHAKE = 1.0F, NOVA_AROUND_SHAKE = 1.6F;
	static final double KI_AROUND_RANGE = 14.0;
	/** The Frost Needle leaves the body this frozen (ticks past the freeze). */
	static final int NEEDLE_CHILL = 200;
	/** Where the hands are, about, at the moment of a blast (blocks up from the feet, standing and on a knee; and out toward them). */
	static final double HAND_STANDING = 1.05, HAND_KNEELING = 0.65, HAND_AHEAD = 0.55, HAND_HIP = 0.85;

	/** The last move each player used, so the next is never the same one. */
	private static final Map<UUID, Kind> LAST = new HashMap<>();
	/** The bodies the Frost Needle's first shot (the leg) has gone through: the next is the arm. */
	private static final Set<UUID> NEEDLED = new HashSet<>();

	private ZFighterExecutions() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			NEEDLED.clear();
		});
	}

	/** Whether {@code player}'s empty hands are a Z Fighter's (not the Great Ape's: his are too big for anything but the snap). */
	public static boolean applies(ServerPlayer player) {
		return Powers.has(player, ZFighterPower.INSTANCE) && !ZFighterLineage.ape(player);
	}

	/** What a move costs in ki (0: the fists' own). */
	static float cost(Kind kind) {
		return switch (kind) {
			case Z_PALM -> PALM_KI;
			case Z_WAVE, Z_CANNON, Z_LANCE -> BEAM_KI;
			case Z_NEEDLE -> NEEDLE_KI;
			case Z_NOVA -> NOVA_KI;
			default -> 0.0F;
		};
	}

	/** Whether he has the ki for it without being told he hasn't (Rage and creative pay for anything). */
	private static boolean affords(ServerPlayer player, ZFighterPower.Live live, Kind kind) {
		float cost = cost(kind) * ZFighterAndroid.costFactor(player);
		return cost <= 0.0F || player.isCreative() || live.raging() || live.ki >= cost;
	}

	/** The lineage's own move. */
	static Kind lineageMove(int lineage) {
		return switch (lineage) {
			case ZFighterLineage.WARRIOR -> Kind.Z_CANNON;
			case ZFighterLineage.VERDANT -> Kind.Z_LANCE;
			case ZFighterLineage.FROST -> Kind.Z_NEEDLE;
			default -> Kind.Z_WAVE;
		};
	}

	/** The moves open to {@code player} now, the lineage's and the form's {@link #OWN_WEIGHT} times over. */
	static List<Kind> pool(ServerPlayer player) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		List<Kind> out = new ArrayList<>(List.of(Kind.Z_PALM, Kind.Z_FLURRY, Kind.Z_HAMMER));
		int lineage = ZFighterLineage.of(player);
		for (int i = 0; i < OWN_WEIGHT; i++) {
			if (lineage != ZFighterLineage.NONE) {
				out.add(lineageMove(lineage));
			}
			if (live.form > 0) {
				out.add(Kind.Z_NOVA);
			}
		}
		out.removeIf(kind -> !affords(player, live, kind));
		return out;
	}

	/** One of his moves, never the one he used last (while there's another). */
	public static Kind pick(ServerPlayer player) {
		List<Kind> pool = pool(player);
		Kind last = LAST.get(player.getUUID());
		if (pool.stream().anyMatch(kind -> kind != last)) {
			pool.removeIf(kind -> kind == last);
		}
		Kind kind = pool.get(player.getRandom().nextInt(pool.size()));
		LAST.put(player.getUUID(), kind);
		return kind;
	}

	/** It begins ({@code Finishers.begin}): the ki paid, and what's heard first. */
	public static void begin(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		if (cost(kind) > 0.0F) {
			ZFighterPower.spend(player, live, cost(kind));
		}
		double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
		switch (kind) {
			case Z_FLURRY, Z_HAMMER -> level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
			case Z_NEEDLE -> level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
			default -> level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		}
	}

	// ---- beat by beat -----------------------------------------------------------------------------------------------------

	/** When the ki gathers for a move (ticks: from, to; nothing for the fists'). */
	private static int chargeFrom(Kind kind) {
		return switch (kind) {
			case Z_PALM, Z_WAVE, Z_CANNON, Z_LANCE -> 6;
			case Z_NOVA -> 4;
			default -> -1;
		};
	}

	/** The film each ki move plays: its shot, when it starts and how long it runs (ending before the move does). */
	private record Film(int shot, int from, int ticks) {
	}

	private static Film film(Kind kind) {
		return switch (kind) {
			case Z_PALM -> new Film(UltimateFilm.PROFILE, 12, 18);
			case Z_WAVE -> new Film(UltimateFilm.PROFILE, 18, 24);
			case Z_CANNON -> new Film(UltimateFilm.PROFILE, 16, 24);
			case Z_LANCE -> new Film(UltimateFilm.PROFILE, 18, 20);
			case Z_NEEDLE -> new Film(UltimateFilm.PUSH_IN, 26, 18);
			case Z_NOVA -> new Film(UltimateFilm.ORBIT, 8, 32);
			default -> null;
		};
	}

	/** Each tick it plays ({@code Finishers.plays}): the ki gathering, the film's start, a beam held on them after the shot. */
	public static void tick(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, int t, boolean present) {
		int color = color(player, kind);
		Film film = film(kind);
		if (film != null && t == film.from() && present) {
			UltimateFilm.play(player, film.shot(), film.ticks(), body.getBoundingBox().getCenter(), body, FILM_WITNESSES);
		}
		int from = chargeFrom(kind), kill = kind.kill();
		if (from >= 0 && t >= from && t < kill && present) {
			charge(level, player, body, kind, t, color);
		}
		if (!present && t <= kill) {
			return;
		}
		int beam = switch (kind) {
			case Z_PALM -> PALM_BEAM_TICKS;
			case Z_WAVE -> WAVE_BEAM_TICKS;
			case Z_CANNON -> CANNON_BEAM_TICKS;
			case Z_LANCE -> LANCE_BEAM_TICKS;
			case Z_NOVA -> NOVA_PILLAR_TICKS;
			default -> 0;
		};
		if (t > kill && t < kill + beam) {
			// (the beam held on where they lay a few ticks more, then fading)
			beam(level, player, body, kind, color, t == kill + beam - 1);
		}
	}

	/** The ki gathering in the hands (or the aura flaring, for the Supernova), louder as it fills. */
	private static void charge(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, int t, int color) {
		float fill = (t - chargeFrom(kind)) / (float) Math.max(1, kind.kill() - chargeFrom(kind));
		Vec3 at = switch (kind) {
			case Z_WAVE -> hip(player, body);
			case Z_LANCE -> player.getEyePosition().add(ahead(player, body).scale(0.25)).add(0.0, 0.05, 0.0);
			case Z_NOVA -> hand(player, body, HAND_KNEELING);
			default -> hand(player, body, HAND_STANDING);
		};
		float size = 0.6F + fill * 1.2F;
		level.sendParticles(new DustParticleOptions(color, size), at.x, at.y, at.z, 2 + (int) (fill * 4), 0.08, 0.08, 0.08, 0.0);
		if (t % 2 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1 + (int) (fill * 3), 0.15, 0.15, 0.15, 0.05);
		}
		if (kind == Kind.Z_NOVA) {
			// the aura swells round him
			level.sendParticles(new DustParticleOptions(color, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 3 + (int) (fill * 6), 0.4, 0.7, 0.4, 0.0);
		}
		if (t % 5 == 0) {
			level.playSound(null, at.x, at.y, at.z, kind == Kind.Z_LANCE ? SoundEvents.BEACON_AMBIENT : SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
				0.3F + fill * 0.4F, 1.2F + fill * 0.6F);
		}
	}

	/** One of the move's blows ({@code Finishers.blow}, which then jolts the body, shakes the screens and kills on the last). */
	public static void blow(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, boolean kill) {
		Vec3 middle = body.getBoundingBox().getCenter();
		double x = middle.x, y = middle.y, z = middle.z;
		float pitch = 0.85F + level.getRandom().nextFloat() * 0.3F;
		int color = color(player, kind);
		switch (kind) {
			case Z_FLURRY -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, kill ? 1.1F : 0.7F, pitch * (kill ? 0.7F : 1.1F));
				level.sendParticles(ParticleTypes.CRIT, x, y, z, kill ? 16 : 4, 0.2, 0.2, 0.2, 0.25);
				if (kill) {
					// the last one with ki behind it, into the ground under them
					level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
					flare(level, middle, color);
					crack(level, body);
					BladeBlood.crushed(body);
				} else {
					BladeBlood.blunt(body, 4.0F);
				}
			}
			case Z_HAMMER -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, kill ? 1.2F : 0.8F, pitch * (kill ? 0.6F : 0.85F));
				level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
				dust(level, body, kill ? 32 : 14);
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.4F);
					flare(level, middle, color);
					crack(level, body);
					BladeBlood.crushed(body);
				} else {
					BladeBlood.blunt(body, 7.0F);
				}
			}
			case Z_NEEDLE -> needle(level, player, body, kill);
			case Z_NOVA -> {
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
				level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.3, 0.5, 0.3, 0.15);
				level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 60, 0.4, 0.4, 0.4, 0.05);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.3, 0.3, 0.3, 0.02);
				// (burned away: no corpse; Kitchen reads the tag as it would swap the dying body for one)
				com.jaymospowers.power.web.KitchenCompat.leaveNoCorpse(body);
				beam(level, player, body, kind, color, false);
				Shakes.around(level, middle, KI_AROUND_RANGE, NOVA_AROUND_SHAKE);
			}
			default -> {
				// a ki blast into them: the palm, the wave, the cannon, the lance
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, kind == Kind.Z_PALM ? 0.6F : 0.9F,
					kind == Kind.Z_CANNON ? 0.9F : 1.3F);
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), kind == Kind.Z_LANCE ? SoundEvents.TRIDENT_RIPTIDE_1.value()
					: SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, kind == Kind.Z_LANCE ? 1.4F : 1.6F);
				beam(level, player, body, kind, color, false);
				flare(level, middle, color);
				level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
				dust(level, body, 20);
				BladeBlood.splash(body, kind == Kind.Z_LANCE ? 8.0F : 6.0F);
				Shakes.around(level, middle, KI_AROUND_RANGE, KI_AROUND_SHAKE);
			}
		}
	}

	/**
	 * The Frost Needle's shots: the first two through a leg and an arm (broken: they're not going anywhere), the last
	 * through the head. Each a hair-thin white beam from the fingertip, as his held beam's.
	 */
	private static void needle(ServerLevel level, ServerPlayer player, LivingEntity body, boolean kill) {
		Vec3 to;
		if (kill) {
			to = Finishers.headAt(body);
		} else {
			boolean leg = NEEDLED.add(body.getUUID());
			BodyPart part = leg ? (level.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG)
				: (level.getRandom().nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM);
			Wounds.breakPart(body, part, player);
			Vec3 middle = body.getBoundingBox().getCenter();
			Vec3 side = Vec3.directionFromRotation(0.0F, body.yBodyRot + 90.0F).scale(part == BodyPart.LEFT_LEG || part == BodyPart.LEFT_ARM ? 0.2 : -0.2);
			Vec3 along = Vec3.directionFromRotation(0.0F, body.yBodyRot).scale(leg ? -0.35 : 0.1);
			to = middle.add(side).add(along);
		}
		Vec3 from = hand(player, body, HAND_STANDING);
		show(level, new ZFighterNet.BeamFx(from.x, from.y, from.z, to.x, to.y, to.z, FrostBeam.RADIUS * 1.5F, FrostBeam.COLOR, FrostBeam.HELD_STYLE, 4));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.9F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, kill ? 0.9F : 1.3F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, to.x, to.y, to.z, kill ? 30 : 12, 0.15, 0.15, 0.15, 0.06);
		BladeBlood.splash(body, kill ? 6.0F : 3.0F);
		if (kill) {
			NEEDLED.remove(body.getUUID());
			body.setTicksFrozen(body.getTicksRequiredToFreeze() + NEEDLE_CHILL);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), to.x, to.y, to.z, 14, 0.15, 0.15, 0.15, 0.1);
			Shakes.around(level, to, KI_AROUND_RANGE * 0.5, KI_AROUND_SHAKE * 0.6F);
		}
	}

	/** The beam on them: from the hands (the palm, the wave from the hip, the lance, the cannon), or the Supernova's pillar. */
	private static void beam(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, int color, boolean last) {
		Vec3 to = body.getBoundingBox().getCenter();
		Vec3 from;
		float radius;
		int style = ZFighterKi.WAVE;
		switch (kind) {
			case Z_NOVA -> {
				from = new Vec3(to.x, body.getBoundingBox().minY, to.z);
				to = from.add(0.0, NOVA_PILLAR, 0.0);
				radius = NOVA_RADIUS;
			}
			case Z_LANCE -> {
				from = hand(player, body, HAND_STANDING);
				// through them and into the ground
				to = to.add(to.subtract(from).normalize().scale(1.2));
				radius = LANCE_RADIUS;
				style = ZFighterKi.SPIRAL;
			}
			case Z_CANNON -> {
				from = hand(player, body, HAND_STANDING);
				radius = CANNON_RADIUS;
			}
			case Z_WAVE -> {
				from = hand(player, body, HAND_STANDING);
				radius = WAVE_RADIUS;
			}
			default -> {
				from = hand(player, body, HAND_STANDING);
				radius = PALM_RADIUS;
			}
		}
		show(level, new ZFighterNet.BeamFx(from.x, from.y, from.z, to.x, to.y, to.z, radius, color, style, last ? ZFighterKi.BEAM_TICKS : 2));
	}

	/** Drawn for everyone near (as every ki beam is). */
	private static void show(ServerLevel level, ZFighterNet.BeamFx fx) {
		Vec3 middle = new Vec3((fx.fx() + fx.tx()) * 0.5, (fx.fy() + fx.ty()) * 0.5, (fx.fz() + fx.tz()) * 0.5);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(middle) <= ZFighterKi.BEAM_SEEN * ZFighterKi.BEAM_SEEN) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** His ki colour (the form's in a form); the Needle's is always the Frost-born's white. */
	private static int color(ServerPlayer player, Kind kind) {
		return kind == Kind.Z_NEEDLE ? FrostBeam.COLOR : ZFighterKi.formColor(ZFighterPower.live(player)) & 0xFFFFFF;
	}

	/** Along the ground from him toward them. */
	private static Vec3 ahead(ServerPlayer player, LivingEntity body) {
		Vec3 d = body.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		return d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : d.normalize();
	}

	/** About where his hands are, out toward them, this high (blocks, in his size). */
	private static Vec3 hand(ServerPlayer player, LivingEntity body, double height) {
		double scale = player.getScale();
		return player.position().add(ahead(player, body).scale(HAND_AHEAD * scale)).add(0.0, height * scale, 0.0);
	}

	/** The Cupped Wave's hands at his hip, drawn back on his right. */
	private static Vec3 hip(ServerPlayer player, LivingEntity body) {
		double scale = player.getScale();
		Vec3 ahead = ahead(player, body);
		Vec3 right = new Vec3(-ahead.z, 0.0, ahead.x);
		return player.position().add(right.scale(0.35 * scale)).add(ahead.scale(-0.15 * scale)).add(0.0, HAND_HIP * scale, 0.0);
	}

	/** A flash of his ki where it lands. */
	private static void flare(ServerLevel level, Vec3 at, int color) {
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(new DustParticleOptions(color, 2.0F), at.x, at.y, at.z, 16, 0.4, 0.3, 0.4, 0.0);
	}

	/** The ground cracks under them. */
	private static void crack(ServerLevel level, LivingEntity body) {
		GroundCracks.crack(level, BlockPos.containing(body.getX(), body.getBoundingBox().minY - 0.1, body.getZ()), level.getRandom(), CRACK_LENGTH);
	}

	private static void dust(ServerLevel level, LivingEntity body, int count) {
		BlockState on = body.getBlockStateOn();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, on.isAir() ? Blocks.DIRT.defaultBlockState() : on), body.getX(),
			body.getBoundingBox().minY + 0.05, body.getZ(), count, body.getBbWidth() * 0.6, 0.05, body.getBbWidth() * 0.6, 0.15);
	}

	/** It's over, played out or stopped: a Needle stopped halfway forgets its first shot. */
	public static void end(LivingEntity body) {
		NEEDLED.remove(body.getUUID());
	}

	/** Whether the move crushes a spider flat under it (Kitchen's legs, no body). */
	public static boolean crushes(Kind kind) {
		return kind == Kind.Z_HAMMER || kind == Kind.Z_FLURRY;
	}
}
