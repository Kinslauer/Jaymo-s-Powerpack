package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.gifted.Gifted;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.50, Andy's NOEA picks) The god form ritual. Past Super 3 ({@link ZFighterTraining#formsOpen} at the top), five
 * good-hearted fighters stand in a ring on the Lookout's courtyard and lend their ki to lift one of them into the god
 * form ({@link ZFighterGod}). The ritual circle is painted on the Lookout's floor, south of the pole: a gold block in a
 * 3x3 of chiseled quartz for the one to be lifted, and five amethyst pads on a ring of radius {@code 4} round it. It is
 * worked from the geometry, so the blocks are only a sign (they are repainted if gone). (0.17.58) The Lookout is wider now
 * ({@link ZFighterLookout}) and the circle sits at its south side ({@link #CIRCLE_DZ}).
 * <ul>
 * <li><b>Who counts:</b> good-hearted means not Rogue ({@link ZFighterKingdoms#rogue}), not corrupted
 * ({@link Fate#isCorrupted}). A ring member is a Z Fighter player standing on a pad with at least {@link #RING_KI} ki, a
 * gifted villager with the Z Fighter gift ({@link Gifted}, not corrupted) within {@link #CALL_RADIUS} blocks of the circle
 * (called to an empty pad when the ritual starts, held there by slowness), or a <b>master's spirit</b>.</li>
 * <li><b>Solo play:</b> every {@link #LESSONS_PER_SPIRIT} lessons a good master has taught him ({@link ZFighterMasters},
 * one a day) let one master's spirit take a pad, so with {@code 5 * LESSONS_PER_SPIRIT} lessons he can stand in the
 * circle alone. A spirit lends nothing but is shown as a column of light.</li>
 * <li><b>The rite:</b> the one to be lifted stands in the middle; once the ring is whole and he has stood still for
 * {@link #GATHER_TICKS} ticks it begins and runs {@link #RITUAL_TICKS} ticks. Every player on a pad pays {@link #RING_KI}
 * ki over it; the one in the middle rises, fills with ki and the air hums. If anyone leaves a pad or the middle, dies,
 * runs dry or turns out not to be good-hearted, the ritual falls apart (what was paid is gone). Done, the god form is his
 * for good, and every player in the ring gets {@link #RING_TP} TP.</li>
 * </ul>
 */
public final class ZFighterGodRitual {
	static final int PAD_COUNT = 5;
	/**
	 * The circle's middle: this far south of the tower's middle, on the Lookout's floor. (0.17.58) It was 5, on the old
	 * 13-block platform; the bigger Lookout ({@link ZFighterLookout}) has it at the south side, clear of the courtyard.
	 */
	static final int CIRCLE_DZ = 17;
	/** The five pads from the middle (x, z): a ring of radius 4, the first due south. */
	static final int[][] PADS = {{0, 4}, {-4, 1}, {-2, -3}, {2, -3}, {4, 1}};
	/** Standing on a spot: within this many blocks (flat), and this far above or below. */
	static final double STAND_RADIUS = 1.6, STAND_HEIGHT = 2.5;
	/** Once begun, the middle and the pads may be left by this much before it falls apart. */
	static final double LEAVE_RADIUS = 2.6;
	/** Gifted villagers this near the middle (blocks) can be called to a pad. */
	static final double CALL_RADIUS = 12.0;
	/** The ring whole and the one in the middle still for this long (ticks) before it begins; how long it runs. */
	static final int GATHER_TICKS = 60;
	static final int RITUAL_TICKS = 260;
	/** Ki each player on a pad pays over the rite; each gets this much TP when it is done. */
	static final float RING_KI = 40.0F;
	static final int RING_TP = 150;
	/** Good masters' lessons that make one spirit. */
	static final int LESSONS_PER_SPIRIT = 4;
	/** How high he is lifted above the floor he stood on (blocks), and the rise a tick. */
	static final double LIFT = 2.5, RISE = 0.07;
	/** How often (ticks) the circle is looked at, and repainted if it is gone; how near a player must be for that. */
	static final int SCAN_EVERY = 10, PAINT_EVERY = 100;
	static final double NEAR = 48.0;
	/** The pads show to a Z Fighter within this many blocks, every this many ticks. */
	static final double HINT_RADIUS = 20.0;
	static final int HINT_EVERY = 40;
	/** The ring's light: the god form's red, and a gold. */
	static final int RED = 0xE0302A, GOLD = 0xFFD54A;

	/** How many lessons a good master has given him (saved, kept through death). */
	public static final AttachmentType<Integer> LESSONS = AttachmentRegistry.create(JaymosPowers.id("zfighter_master_lessons"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** A place in the ring: a player, a gifted villager or a master's spirit. */
	private record Member(int pad, UUID id, boolean villager) {
	}

	/** The rite under way (one circle, one rite at a time). */
	private static final class Rite {
		final UUID candidate;
		final long since;
		final List<Member> members;
		final int spirits;
		final double baseY;

		Rite(UUID candidate, long since, List<Member> members, int spirits, double baseY) {
			this.candidate = candidate;
			this.since = since;
			this.members = members;
			this.spirits = spirits;
			this.baseY = baseY;
		}
	}

	private static Rite rite;
	/** Who is waiting in the middle for the ring to be whole, and since when; and when the missing-ring message last showed. */
	private static UUID waiting;
	private static long waitingSince;
	private static long lastNeedSaid;

	private ZFighterGodRitual() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterGodRitual::tick);
	}

	static void clear() {
		rite = null;
		waiting = null;
	}

	static void forget(ServerPlayer player) {
		if (waiting != null && waiting.equals(player.getUUID())) {
			waiting = null;
		}
	}

	// ---------------------------------------------------------------- lessons (the solo ring)

	/** A good master has taught him today. */
	static void lessonTaught(ServerPlayer player) {
		player.setAttached(LESSONS, lessons(player) + 1);
	}

	public static int lessons(Player player) {
		return player.getAttachedOrElse(LESSONS, 0);
	}

	static void setLessons(ServerPlayer player, int n) {
		player.setAttached(LESSONS, Math.max(0, n));
	}

	/** How many master's spirits stand for him in the ring. */
	public static int spirits(Player player) {
		return Math.min(PAD_COUNT, lessons(player) / LESSONS_PER_SPIRIT);
	}

	// ---------------------------------------------------------------- the circle

	/** The circle's middle on the Lookout (where the one to be lifted stands), or null before the Tower is placed. */
	private static Vec3 middle(MinecraftServer server) {
		ZFighterSites.Data data = ZFighterSites.data(server);
		if (!data.towerBuilt() || data.tower().isEmpty()) {
			return null;
		}
		BlockPos tower = data.tower().get();
		return new Vec3(tower.getX() + 0.5, ZFighterSites.LOOKOUT_Y + 1.0, tower.getZ() + CIRCLE_DZ + 0.5);
	}

	private static Vec3 pad(Vec3 middle, int i) {
		return middle.add(PADS[i][0], 0.0, PADS[i][1]);
	}

	/** Paints the circle on the Lookout now (debug, or when someone comes near and it is not there). */
	static boolean paintNow(MinecraftServer server) {
		Vec3 middle = middle(server);
		if (middle == null) {
			return false;
		}
		paint(server.overworld(), middle);
		return true;
	}

	/** (0.17.58) Paints the circle round {@code middle} (the Lookout's builder uses this). */
	static void paintAt(ServerLevel level, Vec3 middle) {
		paint(level, middle);
	}

	private static void paint(ServerLevel level, Vec3 middle) {
		int x = Mth.floor(middle.x), y = ZFighterSites.LOOKOUT_Y, z = Mth.floor(middle.z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(new BlockPos(x, y, z), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (int[] pad : PADS) {
			level.setBlock(new BlockPos(x + pad[0], y, z + pad[1]), Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// ---------------------------------------------------------------- who counts

	/** Whether a heart is good: not on the Rogue's path and not corrupted. */
	static boolean goodHearted(ServerPlayer player) {
		return !Fate.isCorrupted(player) && !ZFighterKingdoms.rogue(player) && !ZFighterCorruption.corrupted(player);
	}

	/** Whether a gifted villager (or other gifted mob) is a Z Fighter of good heart. */
	private static boolean goodGift(LivingEntity e) {
		Gifted.Gift gift = Gifted.of(e);
		return gift != null && ZFighterPower.ID.equals(gift.power()) && !gift.corrupted();
	}

	/** Whether {@code player} could be lifted: a Z Fighter at the top of the road, good of heart, no god form yet. */
	private static boolean liftable(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Powers.has(player, ZFighterPower.INSTANCE) && !ZFighterGod.unlocked(player)
			&& ZFighterTraining.formsOpen(player) >= ZFighterTraining.FORMS && goodHearted(player) && !ZFighterLineage.ape(player);
	}

	private static double flat(Vec3 a, double x, double z) {
		double dx = a.x - x, dz = a.z - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean standsOn(LivingEntity e, Vec3 spot, double radius) {
		return flat(spot, e.getX(), e.getZ()) <= radius && Math.abs(e.getY() - spot.y) <= STAND_HEIGHT;
	}

	/** The ring as it stands round {@code candidate}: null for any pad nobody fills. {@code call} also takes gifted villagers from the courtyard. */
	private static Member[] ring(ServerLevel level, Vec3 middle, ServerPlayer candidate, boolean call) {
		Member[] ring = new Member[PAD_COUNT];
		Set<UUID> used = new HashSet<>();
		used.add(candidate.getUUID());
		for (ServerPlayer p : level.players()) {
			if (used.contains(p.getUUID()) || !p.isAlive() || p.isSpectator() || !Powers.has(p, ZFighterPower.INSTANCE) || !goodHearted(p)
				|| !p.isCreative() && ZFighterPower.live(p).ki < RING_KI) {
				continue;
			}
			for (int i = 0; i < PAD_COUNT; i++) {
				if (ring[i] == null && standsOn(p, pad(middle, i), STAND_RADIUS)) {
					ring[i] = new Member(i, p.getUUID(), false);
					used.add(p.getUUID());
					break;
				}
			}
		}
		List<PathfinderMob> folk = level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(middle, middle).inflate(CALL_RADIUS, 6.0, CALL_RADIUS),
			e -> e.isAlive() && goodGift(e) && flat(middle, e.getX(), e.getZ()) <= CALL_RADIUS);
		for (int i = 0; i < PAD_COUNT; i++) {
			if (ring[i] != null) {
				continue;
			}
			Vec3 spot = pad(middle, i);
			PathfinderMob best = null;
			double bestD = Double.MAX_VALUE;
			for (PathfinderMob e : folk) {
				double d = e.distanceToSqr(spot);
				if (!used.contains(e.getUUID()) && d < bestD) {
					best = e;
					bestD = d;
				}
			}
			if (best != null) {
				ring[i] = new Member(i, best.getUUID(), true);
				used.add(best.getUUID());
			}
		}
		int spirits = spirits(candidate);
		for (int i = 0; i < PAD_COUNT && spirits > 0; i++) {
			if (ring[i] == null) {
				ring[i] = new Member(i, candidate.getUUID(), false);
				spirits--;
			}
		}
		return ring;
	}

	private static int filled(Member[] ring) {
		int n = 0;
		for (Member m : ring) {
			if (m != null) {
				n++;
			}
		}
		return n;
	}

	/** A member that is neither a player nor a villager is a spirit (the candidate's own id stands in for it). */
	private static boolean spirit(Member m, UUID candidate) {
		return m.id().equals(candidate);
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (rite != null) {
			Vec3 middle = middle(server);
			if (middle == null) {
				rite = null;
			} else {
				run(server, level, middle, now);
			}
			return;
		}
		if (now % SCAN_EVERY != 0) {
			return;
		}
		Vec3 middle = middle(server);
		if (middle == null) {
			return;
		}
		boolean paint = now % PAINT_EVERY == 0;
		ServerPlayer standing = null;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || flat(middle, p.getX(), p.getZ()) > NEAR || Math.abs(p.getY() - middle.y) > 40.0) {
				continue;
			}
			if (paint && !level.getBlockState(BlockPos.containing(middle.x, ZFighterSites.LOOKOUT_Y, middle.z)).is(Blocks.GOLD_BLOCK)) {
				paint(level, middle);
				paint = false;
			}
			if (Powers.has(p, ZFighterPower.INSTANCE) && now % HINT_EVERY == 0 && flat(middle, p.getX(), p.getZ()) <= HINT_RADIUS) {
				hint(level, p, middle);
			}
			if (standing == null && standsOn(p, middle, STAND_RADIUS) && liftable(p)) {
				standing = p;
			}
		}
		if (standing == null) {
			waiting = null;
			return;
		}
		if (!standing.getUUID().equals(waiting)) {
			waiting = standing.getUUID();
			waitingSince = now;
			lastNeedSaid = now;
		}
		Member[] ring = ring(level, middle, standing, true);
		int n = filled(ring);
		if (n < PAD_COUNT) {
			waitingSince = now;
			if (now - lastNeedSaid >= 200L) {
				lastNeedSaid = now;
				standing.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ritual_need", n, PAD_COUNT).withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		if (now - waitingSince >= GATHER_TICKS) {
			begin(level, middle, standing, ring, now);
		}
	}

	/** A soft light on each pad, for the Z Fighter's eyes alone. */
	private static void hint(ServerLevel level, ServerPlayer viewer, Vec3 middle) {
		for (int i = 0; i < PAD_COUNT; i++) {
			Vec3 p = pad(middle, i);
			level.sendParticles(viewer, ParticleTypes.END_ROD, true, false, p.x, p.y + 0.2, p.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		level.sendParticles(viewer, new DustParticleOptions(GOLD, 1.0F), true, false, middle.x, middle.y + 0.2, middle.z, 2, 0.4, 0.1, 0.4, 0.0);
	}

	private static void begin(ServerLevel level, Vec3 middle, ServerPlayer candidate, Member[] ring, long now) {
		List<Member> members = new ArrayList<>();
		int spirits = 0;
		for (Member m : ring) {
			members.add(m);
			if (spirit(m, candidate.getUUID())) {
				spirits++;
			}
		}
		rite = new Rite(candidate.getUUID(), now, members, spirits, middle.y);
		waiting = null;
		for (Member m : members) {
			if (m.villager()) {
				if (level.getEntity(m.id()) instanceof PathfinderMob mob) {
					Vec3 spot = pad(middle, m.pad());
					float yaw = (float)(Mth.atan2(middle.z - spot.z, middle.x - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
					mob.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, false);
					mob.getNavigation().stop();
				}
			} else if (!spirit(m, candidate.getUUID()) && level.getServer().getPlayerList().getPlayer(m.id()) instanceof ServerPlayer lender) {
				lender.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ritual_lend", candidate.getDisplayName()).withStyle(ChatFormatting.AQUA));
			}
		}
		candidate.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ritual_begin").withStyle(ChatFormatting.RED));
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	/** One tick of the rite. */
	private static void run(MinecraftServer server, ServerLevel level, Vec3 middle, long now) {
		ServerPlayer candidate = server.getPlayerList().getPlayer(rite.candidate);
		if (candidate == null || !candidate.isAlive() || candidate.level() != level || !liftable(candidate)
			|| flat(middle, candidate.getX(), candidate.getZ()) > LEAVE_RADIUS || Math.abs(candidate.getY() - rite.baseY) > LIFT + 3.0) {
			abort(server, level, middle, "message.jaymospowers.zfighter_ritual_broken");
			return;
		}
		long t = now - rite.since;
		float perTick = RING_KI / RITUAL_TICKS;
		for (Member m : rite.members) {
			Vec3 spot = pad(middle, m.pad());
			if (spirit(m, rite.candidate)) {
				continue;
			}
			if (m.villager()) {
				if (!(level.getEntity(m.id()) instanceof PathfinderMob mob) || !mob.isAlive() || !goodGift(mob)) {
					abort(server, level, middle, "message.jaymospowers.zfighter_ritual_fell");
					return;
				}
				mob.getNavigation().stop();
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, false, false));
				if (flat(spot, mob.getX(), mob.getZ()) > 0.8) {
					mob.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), mob.getYRot(), 0.0F, false);
				}
				continue;
			}
			ServerPlayer lender = server.getPlayerList().getPlayer(m.id());
			if (lender == null || !lender.isAlive() || lender.level() != level || !goodHearted(lender) || flat(spot, lender.getX(), lender.getZ()) > LEAVE_RADIUS
				|| Math.abs(lender.getY() - spot.y) > STAND_HEIGHT) {
				abort(server, level, middle, "message.jaymospowers.zfighter_ritual_fell");
				return;
			}
			if (!lender.isCreative()) {
				ZFighterPower.Live live = ZFighterPower.live(lender);
				if (live.ki < perTick) {
					abort(server, level, middle, "message.jaymospowers.zfighter_ritual_dry");
					return;
				}
				live.ki -= perTick;
			}
		}
		ZFighterPower.Live self = ZFighterPower.live(candidate);
		self.ki = Math.min(ZFighterPower.maxKi(candidate), self.ki + ZFighterPower.maxKi(candidate) * 1.5F / RITUAL_TICKS);
		lift(candidate);
		effects(level, middle, candidate, t);
		if (t >= RITUAL_TICKS) {
			complete(server, level, middle, candidate, self);
		}
	}

	/** He rises until he hangs {@link #LIFT} blocks above the floor, held there; slow falling is on so a break lets him down gently. */
	private static void lift(ServerPlayer candidate) {
		double target = rite.baseY + LIFT;
		double vy = candidate.getY() < target ? RISE : 0.0;
		candidate.setDeltaMovement(0.0, vy, 0.0);
		candidate.syncVelocity = true;
		candidate.fallDistance = 0.0;
		candidate.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
	}

	/** The light: beams from each pad into the middle, a column rising, a hum that climbs, a tremor. */
	private static void effects(ServerLevel level, Vec3 middle, ServerPlayer candidate, long t) {
		float fill = Mth.clamp(t / (float)RITUAL_TICKS, 0.0F, 1.0F);
		Vec3 heart = candidate.getBoundingBox().getCenter();
		DustParticleOptions beam = new DustParticleOptions(fill > 0.5F ? RED : GOLD, 1.0F + fill);
		for (Member m : rite.members) {
			Vec3 from = pad(middle, m.pad()).add(0.0, 1.0, 0.0);
			if (t % 2 == 0) {
				for (int i = 0; i < 3; i++) {
					Vec3 at = from.lerp(heart, level.getRandom().nextDouble());
					level.sendParticles(beam, true, false, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			if (spirit(m, rite.candidate) && t % 4 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, true, false, from.x, from.y + 0.6, from.z, 3, 0.1, 0.7, 0.1, 0.01);
			}
		}
		level.sendParticles(new DustParticleOptions(RED, 1.6F), true, false, candidate.getX(), candidate.getY() + 1.0, candidate.getZ(), 2 + Math.round(6 * fill), 0.4,
			0.9, 0.4, 0.05);
		if (t % 20 == 0) {
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.6F + 1.0F * fill);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F + fill);
			com.jaymospowers.core.Shakes.send(candidate, 0.2F + 0.6F * fill, null);
		}
	}

	/** It is done: the god form is his, the ring is thanked, the sky answers. */
	private static void complete(MinecraftServer server, ServerLevel level, Vec3 middle, ServerPlayer candidate, ZFighterPower.Live live) {
		ZFighterGod.unlock(candidate);
		live.ki = ZFighterPower.maxKi(candidate);
		candidate.setHealth(candidate.getMaxHealth());
		candidate.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
		ZFighterPower.transform(candidate, live, ZFighterForms.GOD);
		for (Member m : rite.members) {
			if (spirit(m, rite.candidate) || m.villager()) {
				continue;
			}
			ServerPlayer lender = server.getPlayerList().getPlayer(m.id());
			if (lender != null) {
				ZFighterTraining.grant(lender, RING_TP);
				lender.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_ritual_thanks", candidate.getDisplayName(), RING_TP)
					.withStyle(ChatFormatting.GOLD));
			}
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD0C0), true, true, middle.x, middle.y + 2.0, middle.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(new DustParticleOptions(RED, 2.4F), true, false, middle.x, middle.y + 2.0, middle.z, 120, 1.5, 2.5, 1.5, 0.3);
		level.sendParticles(ParticleTypes.END_ROD, true, false, middle.x, middle.y + 2.0, middle.z, 60, 1.0, 2.0, 1.0, 0.2);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.9F);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2.0F, 0.7F);
		com.jaymospowers.core.Shakes.heavy(candidate, middle, 48.0, 2.0F);
		Component said = Component.translatable("message.jaymospowers.zfighter_ritual_done", candidate.getDisplayName()).withStyle(ChatFormatting.RED);
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(candidate) <= 96.0 * 96.0) {
				near.sendSystemMessage(said);
			}
		}
		rite = null;
	}

	/** The rite falls apart: everyone is told why, the one in the middle comes down. */
	private static void abort(MinecraftServer server, ServerLevel level, Vec3 middle, String why) {
		Rite broken = rite;
		rite = null;
		if (broken == null) {
			return;
		}
		Component said = Component.translatable(why).withStyle(ChatFormatting.GRAY);
		ServerPlayer candidate = server.getPlayerList().getPlayer(broken.candidate);
		if (candidate != null) {
			candidate.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
			candidate.sendSystemMessage(said);
		}
		for (Member m : broken.members) {
			if (!m.villager() && !m.id().equals(broken.candidate) && server.getPlayerList().getPlayer(m.id()) instanceof ServerPlayer lender) {
				lender.sendSystemMessage(said);
			} else if (m.villager() && level.getEntity(m.id()) instanceof Mob mob) {
				mob.removeEffect(MobEffects.SLOWNESS);
			}
		}
		level.sendParticles(ParticleTypes.POOF, true, false, middle.x, middle.y + 1.5, middle.z, 30, 0.8, 1.0, 0.8, 0.05);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
	}
}
