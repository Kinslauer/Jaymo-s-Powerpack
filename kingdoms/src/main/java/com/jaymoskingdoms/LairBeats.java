package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lair that fights for its master (fifth edition's lair actions, Andy's pick D3). Fight a boss in its home (a dragon's den,
 * a ruler's throne room in a citadel, the minotaur's ruins, a necromancer's crypt) and the place joins in on a beat, every
 * {@link #BEAT} ticks while someone who has struck the boss is in the arena, each beat a different one of the place's own
 * in turn, always shown first:
 * <ul>
 * <li><b>Rocks</b>: a scatter of dust and pebbles falls where each of you stands, and a moment later stone drops there
 * (a dodged blow takes half). No block is changed.</li>
 * <li><b>Roots</b>: the ground under you cracks and creeping roots rise; those who don't tear free (a Might save) are netted
 * and crawl until they cut or work loose ({@link Nets}).</li>
 * <li><b>Fog</b>: a cold fog rolls out across the floor and those in it are blinded by darkness for a few seconds.</li>
 * <li><b>Flare</b>: the braziers and vents of the place flare, columns of flame at the lit fires about it (or, where there are
 * none, at its own), and those standing in them burn.</li>
 * </ul>
 * The beat keeps its time, so it is learned: ring, then {@link #STRIKE} ticks, then it lands.
 */
public final class LairBeats {
	/** Ticks between beats, and from a beat's tell to its landing. */
	static final int BEAT = 20 * 7, STRIKE = 26;
	/** The first beat comes this long after a fight starts in the arena (ticks). */
	static final int FIRST = 20 * 5;
	/** How often the arenas are looked over (ticks); how far a player's search for bosses runs (blocks). */
	static final int LOOK = 10;
	static final double SEARCH = 60.0;
	/** Rocks: damage, radius of a fall, and how many fall on a player's own spot besides random ones. */
	static final float ROCK_DAMAGE = 5.0F;
	static final double ROCK_RADIUS = 1.7;
	/** Fog: how long the darkness lasts (ticks). */
	static final int FOG_TICKS = 20 * 4;
	/** Flare: damage, radius of a column, how long those caught burn (seconds), columns per beat, and how many fires are searched for. */
	static final float FLARE_DAMAGE = 4.0F;
	static final double FLARE_RADIUS = 2.0;
	static final int FLARE_BURN = 4, FLARES = 4, FIRES_SEARCHED = 48;

	/** The beats a place has, in the order it plays them. */
	public enum Beat {
		ROCKS, ROOTS, FOG, FLARE
	}

	/** The places a boss may call home, and the beats of each. */
	public enum Place {
		DEN(26, Beat.ROCKS, Beat.FLARE, Beat.FOG),
		THRONE(20, Beat.FLARE, Beat.ROCKS, Beat.FOG),
		RUINS(22, Beat.ROCKS, Beat.ROOTS, Beat.FOG),
		CRYPT(16, Beat.ROOTS, Beat.FOG, Beat.FLARE);

		public final int arena;
		final Beat[] beats;

		Place(int arena, Beat... beats) {
			this.arena = arena;
			this.beats = beats;
		}
	}

	/** Where a boss's home is and what kind of place it is. */
	public record Home(BlockPos at, Place place) {
	}

	private static final class Fight {
		long nextAt = -1L;
		int step;
	}

	private record Landing(Mob boss, ServerLevel level, Beat beat, long at, List<Vec3> spots, Place place) {
	}

	private static final Map<LivingEntity, Fight> FIGHTS = new WeakHashMap<>();
	private static final List<Landing> LANDINGS = new ArrayList<>();

	private LairBeats() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LairBeats::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FIGHTS.clear();
			LANDINGS.clear();
		});
	}

	/** The home of {@code boss}, or null if it has none (or it is lifted). */
	public static @Nullable Home homeOf(Mob boss) {
		MinecraftServer server = boss.level().getServer();
		if (server == null) {
			return null;
		}
		if (boss instanceof com.jaymoskingdoms.dragons.WindDragon dragon && !dragon.isTamed()) {
			GreatLairs.Lair lair = GreatLairs.of(server, boss.getUUID());
			return lair == null || lair.lifted() ? null : new Home(lair.at(), Place.DEN);
		}
		if (boss instanceof com.jaymoskingdoms.beasts.Minotaur) {
			GreatLairs.Lair lair = GreatLairs.of(server, boss.getUUID());
			return lair == null || lair.lifted() ? null : new Home(lair.at(), Place.RUINS);
		}
		if (boss instanceof GreatNecromancer necro) {
			return necro.crypt() == null ? null : new Home(necro.crypt(), Place.CRYPT);
		}
		if (Rulers.is(boss) || boss instanceof Folk folk && folk.leader()) {
			Castle.Keep keep = Factions.keep(server, Rulers.faction(boss));
			return keep == null ? null : new Home(keep.throne(), Place.THRONE);
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (!LANDINGS.isEmpty()) {
			landings();
		}
		if (server.getTickCount() % LOOK != 0) {
			return;
		}
		Set<Mob> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || !player.isAlive()) {
					continue;
				}
				for (Mob boss : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEARCH), b -> b.isAlive() && Legendary.boss(b))) {
					if (seen.add(boss)) {
						fight(level, boss);
					}
				}
			}
		}
	}

	/** Looks to the fight at {@code boss}'s home: a beat if one is due. */
	private static void fight(ServerLevel level, Mob boss) {
		Home home = homeOf(boss);
		if (home == null) {
			return;
		}
		double r = home.place().arena;
		Vec3 centre = Vec3.atCenterOf(home.at());
		if (boss.position().subtract(centre).horizontalDistanceSqr() > r * r) {
			return;
		}
		List<ServerPlayer> fighters = fighters(level, boss, centre, r);
		long now = level.getGameTime();
		Fight f = FIGHTS.computeIfAbsent(boss, b -> new Fight());
		if (fighters.isEmpty()) {
			FIGHTS.remove(boss);
			return;
		}
		if (f.nextAt < 0L) {
			f.nextAt = now + FIRST;
			return;
		}
		if (now < f.nextAt) {
			return;
		}
		f.nextAt = now + BEAT;
		Beat beat = home.place().beats[f.step++ % home.place().beats.length];
		tell(level, boss, beat, home, fighters, centre, r);
	}

	/** The players in the arena who have struck the boss or met it. */
	private static List<ServerPlayer> fighters(ServerLevel level, Mob boss, Vec3 centre, double r) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && p.position().subtract(centre).horizontalDistanceSqr() <= r * r && BossBars.hasHit(boss, p)) {
				out.add(p);
			}
		}
		return out;
	}

	/** The beat's tell, and its landing booked. */
	private static void tell(ServerLevel level, Mob boss, Beat beat, Home home, List<ServerPlayer> fighters, Vec3 centre, double r) {
		List<Vec3> spots = new ArrayList<>();
		switch (beat) {
			case ROCKS -> {
				for (ServerPlayer p : fighters) {
					spots.add(p.position());
				}
				Vec3 rock = scatter(level, centre, r);
				if (rock != null) {
					spots.add(rock);
				}
				for (Vec3 s : spots) {
					Fx.sigil(level, s.add(0.0, 0.05, 0.0), (float)ROCK_RADIUS * 2.0F, 0xB0A090, STRIKE);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), s.x, s.y + 5.0, s.z, 12, 0.7, 0.2, 0.7, 0.02);
				}
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GRAVEL_FALL, SoundSource.HOSTILE, 1.4F, 0.6F);
			}
			case ROOTS -> {
				for (ServerPlayer p : fighters) {
					spots.add(p.position());
					Fx.sigil(level, p.position().add(0.0, 0.05, 0.0), 2.4F, 0x6B8A3A, STRIKE);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), p.getX(), p.getY() + 0.1, p.getZ(), 14, 0.6, 0.1, 0.6, 0.05);
				}
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
			}
			case FOG -> {
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
				level.sendParticles(Fx.miasma(0x8890A0, 2.2F), centre.x, centre.y + 0.5, centre.z, 10, 3.0, 0.2, 3.0, 0.0);
			}
			case FLARE -> {
				spots.addAll(fires(level, centre, r));
				for (Vec3 s : spots) {
					Fx.sigil(level, s.add(0.0, 0.05, 0.0), (float)FLARE_RADIUS * 2.0F, 0xE07830, STRIKE);
					level.sendParticles(ParticleTypes.SMOKE, s.x, s.y + 0.3, s.z, 6, 0.2, 0.1, 0.2, 0.02);
				}
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.5F);
			}
		}
		LANDINGS.add(new Landing(boss, level, beat, level.getGameTime() + STRIKE, spots, home.place()));
	}

	/** A spot on the arena's floor off at random. */
	private static @Nullable Vec3 scatter(ServerLevel level, Vec3 centre, double r) {
		double a = level.getRandom().nextDouble() * Math.PI * 2.0, d = level.getRandom().nextDouble() * r * 0.8;
		BlockPos at = BlockPos.containing(centre.x + Math.cos(a) * d, centre.y, centre.z + Math.sin(a) * d);
		for (int dy = 4; dy >= -4; dy--) {
			BlockPos p = at.above(dy);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
				return Vec3.atBottomCenterOf(p);
			}
		}
		return null;
	}

	/** Lit fires about the place (campfires, torches, lanterns, fire) up to {@link #FLARES}; failing those, spots of its own. */
	private static List<Vec3> fires(ServerLevel level, Vec3 centre, double r) {
		List<Vec3> out = new ArrayList<>();
		BlockPos mid = BlockPos.containing(centre);
		for (int i = 0; i < FIRES_SEARCHED && out.size() < FLARES; i++) {
			BlockPos p = mid.offset(level.getRandom().nextInt((int)r * 2 + 1) - (int)r, level.getRandom().nextInt(7) - 3, level.getRandom().nextInt((int)r * 2 + 1) - (int)r);
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES) && s.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
				|| s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
				out.add(Vec3.atBottomCenterOf(p));
			}
		}
		while (out.size() < Math.min(2, FLARES)) {
			Vec3 own = scatter(level, centre, r);
			if (own == null) {
				break;
			}
			out.add(own);
		}
		return out;
	}

	private static void landings() {
		for (java.util.Iterator<Landing> it = LANDINGS.iterator(); it.hasNext();) {
			Landing l = it.next();
			if (!l.boss().isAlive() || l.boss().isRemoved() || l.boss().level() != l.level()) {
				it.remove();
				continue;
			}
			if (l.level().getGameTime() < l.at()) {
				continue;
			}
			it.remove();
			land(l);
		}
	}

	private static void land(Landing l) {
		ServerLevel level = l.level();
		Mob boss = l.boss();
		Home home = homeOf(boss);
		Vec3 centre = home == null ? boss.position() : Vec3.atCenterOf(home.at());
		switch (l.beat()) {
			case ROCKS -> {
				for (Vec3 s : l.spots()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), s.x, s.y + 0.4, s.z, 30, 0.6, 0.3, 0.6, 0.12);
					level.sendParticles(ParticleTypes.POOF, s.x, s.y + 0.2, s.z, 6, 0.5, 0.1, 0.5, 0.03);
					level.playSound(null, s.x, s.y, s.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
					for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(s.x - ROCK_RADIUS, s.y - 1.0, s.z - ROCK_RADIUS,
						s.x + ROCK_RADIUS, s.y + 3.0, s.z + ROCK_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
						float hurt = Saves.resist(p, Saves.Kind.BLAST) ? ROCK_DAMAGE * 0.5F : ROCK_DAMAGE;
						p.hurtServer(level, level.damageSources().fallingStalactite(boss), hurt);
					}
				}
			}
			case ROOTS -> {
				for (Vec3 s : l.spots()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), s.x, s.y + 0.5, s.z, 24, 0.4, 0.5, 0.4, 0.08);
					level.playSound(null, s.x, s.y, s.z, SoundEvents.HANGING_ROOTS_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
					for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(s.x - 1.5, s.y - 1.0, s.z - 1.5,
						s.x + 1.5, s.y + 3.0, s.z + 1.5), p -> p.isAlive() && !p.isSpectator())) {
						if (Saves.resist(p, Saves.Kind.GRAB)) {
							level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.4F);
						} else {
							Nets.catchIn(p, boss);
						}
					}
				}
			}
			case FOG -> {
				double r = l.place().arena;
				level.sendParticles(Fx.miasma(0x8890A0, 3.0F), centre.x, centre.y + 0.4, centre.z, 60, r * 0.5, 0.4, r * 0.5, 0.0);
				for (ServerPlayer p : level.players()) {
					if (p.isAlive() && !p.isSpectator() && p.position().subtract(centre).horizontalDistanceSqr() <= r * r) {
						p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FOG_TICKS, 0, true, false, false));
					}
				}
				level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
			}
			case FLARE -> {
				for (Vec3 s : l.spots()) {
					level.sendParticles(ParticleTypes.FLAME, s.x, s.y + 0.5, s.z, 40, 0.3, 1.2, 0.3, 0.05);
					level.sendParticles(ParticleTypes.LAVA, s.x, s.y + 0.5, s.z, 6, 0.4, 0.4, 0.4, 0.0);
					level.playSound(null, s.x, s.y, s.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.6F);
					for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(s.x - FLARE_RADIUS, s.y - 1.0, s.z - FLARE_RADIUS,
						s.x + FLARE_RADIUS, s.y + 3.0, s.z + FLARE_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
						p.hurtServer(level, level.damageSources().inFire(), FLARE_DAMAGE);
						p.igniteForSeconds(FLARE_BURN);
					}
				}
			}
		}
	}
}
