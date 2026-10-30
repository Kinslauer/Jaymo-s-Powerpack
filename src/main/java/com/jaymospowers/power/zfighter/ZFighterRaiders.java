package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.6) Andy (2026-10-29): pods are the Tinkerer's lost technology, "hard to find outside the raider warrior born
 * parties". A Warrior-born raider band: every {@link #BAND_DAYS} days or so, as night falls, {@link #BAND_MIN} to
 * {@link #BAND_MAX} pods come down out of the sky a little way off a town a player is near, and out of each steps a raider
 * (a rogue fighter, {@link ZFighterRogue}), one of them their chief (a tyrant), and they go for the town's people, its golems
 * and anyone who stands with them. Beat the whole band and one of their pods is still sound: it stands where it landed as a
 * pod of your own, with a little fuel in it ({@link #PRIZE_FUEL}); the rest burn out. If the band isn't beaten by
 * {@link #BAND_TICKS}, whoever is left climbs back in and they fly off.
 */
public final class ZFighterRaiders {
	// ---- tuning ----
	/** How often a band comes (days, and a spread on top) and how long it stays before giving up. */
	static final long BAND_DAYS = 5L, BAND_SPREAD = 24000L;
	static final int BAND_TICKS = 12000;
	/** How many raiders (each in his pod), how far from the town's seat they land, and how far apart. */
	static final int BAND_MIN = 3, BAND_MAX = 5, LAND_MIN = 30, LAND_MAX = 45, SPREAD = 7;
	/** A town is raided only with a player this near its seat (and in this world tier or higher). */
	static final double NEAR = 160.0;
	static final int MIN_TIER = 1;
	/** A raider's health and blows (times a champion's), the chief's (times a tyrant's), and how far they look for prey. */
	static final double RAIDER_HEALTH = 2.5, RAIDER_DAMAGE = 1.6, CHIEF_HEALTH = 0.6, CHIEF_DAMAGE = 0.9, HUNT = 32.0;
	/** The fuel the pod left behind holds. */
	static final int PRIZE_FUEL = 3;
	/** (0.18.14) What a burnt-out pod leaves: its keel and this many sound parts at random. */
	static final int SCRAP_MIN = 1, SCRAP_MAX = 3;
	static final int NAMES = 8;
	static final String TAG = "jaymospowers.raider";
	static final long DAY = 24000L;

	/** When the next band may come (overworld game time). */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("raider_next"),
		builder -> builder.persistent(Codec.LONG));

	/** A band on the ground: its pods, its raiders, and when it gives up. */
	private static final class Band {
		final ServerLevel level;
		final List<PodEntity> pods = new ArrayList<>();
		final List<UUID> raiders = new ArrayList<>();
		final long until;
		int coming;

		Band(ServerLevel level, long until, int coming) {
			this.level = level;
			this.until = until;
			this.coming = coming;
		}
	}

	private static final List<Band> BANDS = new ArrayList<>();

	private ZFighterRaiders() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterRaiders::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BANDS.clear());
		// raiders and their pods left from before the server stopped go
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !tracked(entity)) {
				entity.discard();
			}
		});
	}

	private static boolean tracked(Entity e) {
		for (Band band : BANDS) {
			if (band.pods.contains(e) || band.raiders.contains(e.getUUID())) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		long ticks = server.getTickCount();
		if (ticks % 20 == 0 && !BANDS.isEmpty()) {
			bands();
		}
		if (ticks % 600 == 0) {
			come(server);
		}
	}

	/** Now and then, as night falls, a band comes down by a town someone is near. */
	private static void come(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!ZFighterKingdoms.present() || !BANDS.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		Long next = level.getAttached(NEXT);
		if (next == null) {
			level.setAttached(NEXT, now + 2 * DAY + random.nextInt((int)BAND_SPREAD));
			return;
		}
		long time = level.getDefaultClockTime() % DAY;
		if (now < next || time < 12000 || time > 14000) {
			return;
		}
		List<ZFighterKingdoms.Settlement> near = new ArrayList<>();
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			BlockPos seat = s.seat();
			if (!"CAMP".equals(s.type()) && level.getNearestPlayer(seat.getX(), seat.getY(), seat.getZ(), NEAR,
				p -> !p.isSpectator() && ZFighterKingdoms.worldTier((Player)p) >= MIN_TIER) != null) {
				near.add(s);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		if (land(level, near.get(random.nextInt(near.size())).seat())) {
			level.setAttached(NEXT, now + BAND_DAYS * DAY + random.nextInt((int)BAND_SPREAD));
		}
	}

	/** A band's pods start down a little way off {@code seat}. True if they found ground. */
	static boolean land(ServerLevel level, BlockPos seat) {
		RandomSource random = level.getRandom();
		BlockPos middle = null;
		for (int tries = 0; tries < 12 && middle == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = LAND_MIN + random.nextDouble() * (LAND_MAX - LAND_MIN);
			int x = seat.getX() + (int)Math.round(Math.cos(angle) * distance), z = seat.getZ() + (int)Math.round(Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			middle = ZFighterSpace.surface(level, x, z);
		}
		if (middle == null) {
			return false;
		}
		int count = BAND_MIN + random.nextInt(BAND_MAX - BAND_MIN + 1);
		Band band = new Band(level, level.getGameTime() + BAND_TICKS, count);
		BANDS.add(band);
		for (int i = 0; i < count; i++) {
			BlockPos ground = i == 0 ? middle : null;
			for (int tries = 0; tries < 6 && ground == null; tries++) {
				int x = middle.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD, z = middle.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
				ground = ZFighterSpace.surface(level, x, z);
			}
			if (ground == null) {
				band.coming--;
				continue;
			}
			boolean chief = i == 0;
			ZFighterPatrol.Flier flier = ZFighterPatrol.land(level, ground, random.nextInt(NAMES), f -> stepOut(band, f, chief));
			flier.pod.addTag(TAG);
			band.pods.add(flier.pod);
		}
		level.playSound(null, middle.getX(), middle.getY() + 30, middle.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 1.5F);
		return true;
	}

	/** A pod down: its raider steps out and goes for the town. */
	private static void stepOut(Band band, ZFighterPatrol.Flier f, boolean chief) {
		band.coming--;
		ServerLevel level = band.level;
		ZFighterRogue raider = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
		if (raider == null) {
			return;
		}
		BlockPos at = f.ground;
		raider.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		Component name = chief ? Component.translatable("entity.jaymospowers.raider.chief." + f.name) : Component.translatable("entity.jaymospowers.raider");
		raider.become(chief ? ZFighterRogue.TYRANT : ZFighterRogue.CHAMPION, name);
		double health = chief ? ZFighterRogue.TYRANT_HEALTH * CHIEF_HEALTH : ZFighterRogue.CHAMPION_HEALTH * RAIDER_HEALTH;
		raider.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);
		raider.setHealth((float)health);
		raider.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(chief ? ZFighterRogue.TYRANT_DAMAGE * CHIEF_DAMAGE
			: ZFighterRogue.CHAMPION_DAMAGE * RAIDER_DAMAGE);
		raider.addTag(TAG);
		raider.setPersistenceRequired();
		level.addFreshEntity(raider);
		band.raiders.add(raider.getUUID());
		level.playSound(null, at, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.4F);
	}

	/** Every second: raiders go for the town; a beaten band leaves one pod; a band out of time flies off. */
	private static void bands() {
		for (Iterator<Band> it = BANDS.iterator(); it.hasNext();) {
			Band band = it.next();
			ServerLevel level = band.level;
			List<ZFighterRogue> alive = new ArrayList<>();
			for (UUID id : band.raiders) {
				if (level.getEntity(id) instanceof ZFighterRogue raider && raider.isAlive()) {
					alive.add(raider);
				}
			}
			for (ZFighterRogue raider : alive) {
				hunt(level, raider);
			}
			if (band.coming > 0) {
				continue;
			}
			if (alive.isEmpty()) {
				beaten(level, band);
				it.remove();
			} else if (level.getGameTime() >= band.until) {
				for (ZFighterRogue raider : alive) {
					level.sendParticles(ParticleTypes.CLOUD, raider.getX(), raider.getY() + 1.0, raider.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
					raider.discard();
				}
				for (PodEntity pod : band.pods) {
					if (!pod.isRemoved()) {
						ZFighterPatrol.takeOff(level, pod);
					}
				}
				it.remove();
			}
		}
	}

	/** A raider with nobody to fight picks the nearest of the town's people, its golems or a player. */
	private static void hunt(ServerLevel level, ZFighterRogue raider) {
		LivingEntity target = raider.getTarget();
		if (target != null && target.isAlive()) {
			return;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB box = raider.getBoundingBox().inflate(HUNT);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && (e instanceof AbstractVillager || e instanceof IronGolem
			|| e instanceof Player p && !p.isCreative() && !p.isSpectator()))) {
			double d = raider.distanceToSqr(e);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best != null) {
			raider.setTarget(best);
		}
	}

	/** The band is beaten: one pod is still sound and stays, a pod of whoever finds it; the others burn out, leaving what is sound of them. */
	private static void beaten(ServerLevel level, Band band) {
		@Nullable PodEntity kept = null;
		for (PodEntity pod : band.pods) {
			if (pod.isRemoved()) {
				continue;
			}
			if (kept == null) {
				kept = pod;
				continue;
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pod.getX(), pod.getY() + 1.0, pod.getZ(), 30, 0.6, 0.8, 0.6, 0.04);
			level.sendParticles(ParticleTypes.FLAME, pod.getX(), pod.getY() + 0.5, pod.getZ(), 20, 0.5, 0.5, 0.5, 0.03);
			level.playSound(null, pod.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
			burnt(level, pod);
		}
		if (kept == null) {
			return;
		}
		// (0.18.14) the kept pod is a complete pod of your own, no longer locked to the band
		kept.removeTag(TAG);
		kept.removeTag(ZFighterPatrol.POD_TAG);
		kept.setFuel(PRIZE_FUEL);
		BlockPos prize = kept.blockPosition();
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(prize) < 96 * 96)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.raiders.beaten").withStyle(ChatFormatting.GOLD));
		}
	}

	/** (0.18.14) A burnt-out pod is a bare pod: its keel and {@link #SCRAP_MIN} to {@link #SCRAP_MAX} sound parts, left lying. */
	private static void burnt(ServerLevel level, PodEntity pod) {
		RandomSource random = level.getRandom();
		List<PodPart> pool = new ArrayList<>(List.of(PodPart.FIN, PodPart.FIN, PodPart.FIN, PodPart.BELL, PodPart.TANK, PodPart.PLATE, PodPart.PLATE,
			PodPart.PLATE, PodPart.PLATE, PodPart.CANOPY, PodPart.MAST));
		int[] used = new int[PodPart.values().length];
		int filled = PodPart.KEEL.bits();
		int count = SCRAP_MIN + random.nextInt(SCRAP_MAX - SCRAP_MIN + 1);
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			PodPart part = pool.remove(random.nextInt(pool.size()));
			filled |= part.bit(used[part.ordinal()]++);
		}
		pod.setParts(filled, 0);
		pod.setFuel(0);
		pod.removeTag(TAG);
		pod.removeTag(ZFighterPatrol.POD_TAG);
	}
}
