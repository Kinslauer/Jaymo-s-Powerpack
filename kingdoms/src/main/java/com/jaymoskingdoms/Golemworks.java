package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.529) Andy (2026-11-04): "a magically attuned kingdom using golems like these for everyday things".
 * <p>
 * A share of realms ({@link #REALM_SHARE}; never a purist's, never a camp) are <b>golemworks</b>: their folk are attuned
 * (each glows faintly to the attuned, {@link Mana}, and can be asked about their magic), and the realm keeps its own town
 * golems ({@link TownGolem}) about its seat: {@link #ROSTER} of each kind (lamp wardens, menders, and a throne golem knelt
 * into its throne by the seat). When a player first comes near, the whole roster
 * is built at once; after that a lost golem is rebuilt one at a time, one every {@link #REBUILD_TICKS}. Whether a realm is
 * one is drawn once from who it is, so it never changes; an op can set it with {@code /kingdom golemworks on|off} at a seat.
 * <p>
 * Also here: a mender's watch ({@link #sparedByMender}).
 */
public final class Golemworks {
	/** How often a realm is a golemworks. */
	static final float REALM_SHARE = 0.15F;
	/** How many of each golem a golemworks keeps. */
	static final Map<TownGolem.Kind, Integer> ROSTER = new EnumMap<>(Map.of(TownGolem.Kind.GILT, 2, TownGolem.Kind.VERDANT, 2,
		TownGolem.Kind.DIAMOND, 1));
	/** How often seats near players are looked at (ticks), how near a player must be, and a lost golem's rebuild time. */
	static final int CHECK_TICKS = 200;
	static final double PLAYER_NEAR = 96.0;
	static final long REBUILD_TICKS = 6000L;
	/** Golems are set down this far from the seat (the throne nearer), and counted this far. */
	static final int SET_MIN = 6, SET_MAX = 20, THRONE_MIN = 3, THRONE_MAX = 7;
	static final double COUNT_RANGE = 64.0;
	/** Its folk: attuned this near the seat, with a power of 1 to this. */
	static final double FOLK_RANGE = 64.0;
	static final int FOLK_POWER = 2;
	/** A mender keeps one from dying this near, at most once in this long (ticks), and leaves them this much health. */
	static final double WATCH_RANGE = 12.0;
	static final long WATCH_EVERY = 6000L;
	static final float WATCH_HEALTH = 4.0F;

	static final String ON = "1", OFF = "0";
	/** Faction id → "1"/"0" (its draw, or an op's), on the Overworld. */
	static final AttachmentType<Map<String, String>> WORKS = AttachmentRegistry.create(JaymosKingdoms.id("golemworks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** Faction id → game time a golem was last built there. */
	static final AttachmentType<Map<String, Long>> BUILT = AttachmentRegistry.create(JaymosKingdoms.id("golemworks_built"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** When a mender last mended this one (game time; not saved). */
	static final AttachmentType<Long> MENDED = AttachmentRegistry.create(JaymosKingdoms.id("golem_mended"));
	/** When a mender last kept this one from dying (game time). */
	static final AttachmentType<Long> WATCHED = AttachmentRegistry.create(JaymosKingdoms.id("golem_watched"),
		builder -> builder.persistent(Codec.LONG));

	private Golemworks() {
	}

	static void register() {
		TownGolem.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				tick(server);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !sparedByMender(entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("golemworks").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
				.then(Commands.literal("off").executes(c -> set(c.getSource(), false))))));
	}

	private static int set(CommandSourceStack source, boolean on) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		Faction f = Factions.around(source.getServer(), player.level(), player.blockPosition(), COUNT_RANGE);
		if (f == null) {
			source.sendFailure(Component.translatable("golem.jaymoskingdoms.works.none"));
			return 0;
		}
		ServerLevel store = Factions.level(source.getServer());
		Map<String, String> next = new HashMap<>(store.getAttachedOrElse(WORKS, Map.of()));
		next.put(Integer.toString(f.id()), on ? ON : OFF);
		store.setAttached(WORKS, Map.copyOf(next));
		source.sendSuccess(() -> Component.translatable(on ? "golem.jaymoskingdoms.works.on" : "golem.jaymoskingdoms.works.off", f.name()), true);
		return 1;
	}

	/** {@code f} is a golemworks. */
	public static boolean is(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.fallen() || !f.type().isRealm()) {
			return false;
		}
		ServerLevel store = Factions.level(server);
		Map<String, String> map = store.getAttachedOrElse(WORKS, Map.of());
		String key = Integer.toString(f.id());
		String v = map.get(key);
		if (v == null) {
			v = draw(f) ? ON : OFF;
			Map<String, String> next = new HashMap<>(map);
			next.put(key, v);
			store.setAttached(WORKS, Map.copyOf(next));
		}
		return ON.equals(v);
	}

	private static boolean draw(Faction f) {
		if (f.type() == FactionType.BARBARIAN || Tempers.of(f) == Tempers.Temper.PURIST) {
			return false;
		}
		long mix = (f.seat().asLong() ^ 0x2545F4914F6CDD1DL) * 0x9E3779B97F4A7C15L + ((long)f.id() << 13);
		mix ^= mix >>> 31;
		return Math.floorMod(mix, 1000L) < (long)(REALM_SHARE * 1000);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen() || !level.isLoaded(f.seat())) {
				continue;
			}
			BlockPos seat = f.seat();
			if (level.getNearestPlayer(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, PLAYER_NEAR, false) == null || !is(server, f)) {
				continue;
			}
			attuneFolk(level, f);
			keepRoster(level, f);
		}
	}

	/** Its folk round the seat are attuned (a faint glow, a little power). */
	private static void attuneFolk(ServerLevel level, Faction f) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.seat()).inflate(FOLK_RANGE),
			e -> e.isAlive() && !Mana.attuned(e) && (e instanceof Villager && !Barbarians.isCampVillager(e) || e instanceof Folk folk && folk.factionId() == f.id()))) {
			int power = 1 + Math.floorMod(e.getUUID().hashCode(), FOLK_POWER);
			e.setAttached(Mana.ATTUNED, true);
			e.setAttached(Mana.POWER, Math.max(power, ManaFolk.power(e)));
		}
	}

	private static void keepRoster(ServerLevel level, Faction f) {
		List<TownGolem> golems = level.getEntitiesOfClass(TownGolem.class, new AABB(f.seat()).inflate(COUNT_RANGE),
			g -> g.isAlive() && g.faction() == f.id());
		Map<TownGolem.Kind, Integer> have = new EnumMap<>(TownGolem.Kind.class);
		for (TownGolem g : golems) {
			have.merge(g.kind(), 1, Integer::sum);
		}
		ServerLevel store = Factions.level(level.getServer());
		Map<String, Long> built = store.getAttachedOrElse(BUILT, Map.of());
		String key = Integer.toString(f.id());
		Long last = built.get(key);
		long now = level.getGameTime();
		boolean first = last == null;
		if (!first && now - last < REBUILD_TICKS) {
			return;
		}
		boolean any = false;
		for (TownGolem.Kind kind : TownGolem.Kind.values()) {
			int missing = ROSTER.getOrDefault(kind, 0) - have.getOrDefault(kind, 0);
			for (int i = 0; i < missing; i++) {
				if (build(level, f, kind)) {
					any = true;
					if (!first) {
						break;
					}
				}
			}
			if (any && !first) {
				break;
			}
		}
		if (any || first) {
			Map<String, Long> next = new HashMap<>(built);
			next.put(key, now);
			store.setAttached(BUILT, Map.copyOf(next));
		}
	}

	private static boolean build(ServerLevel level, Faction f, TownGolem.Kind kind) {
		BlockPos seat = f.seat();
		boolean throne = kind == TownGolem.Kind.DIAMOND;
		int min = throne ? THRONE_MIN : SET_MIN, max = throne ? THRONE_MAX : SET_MAX;
		for (int tries = 0; tries < 12; tries++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			int r = min + level.getRandom().nextInt(max - min + 1);
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				seat.offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r)));
			if (!level.getBlockState(at.below()).isSolid() || Math.abs(at.getY() - seat.getY()) > 12) {
				continue;
			}
			TownGolem golem = TownGolem.make(level, kind, seat, f.id(), null);
			if (golem == null) {
				return false;
			}
			golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(golem)) {
				continue;
			}
			level.addFreshEntity(golem);
			if (throne) {
				// a realm's throne golem kneels by the seat as its throne, and stays one
				golem.setYBodyRot(level.getRandom().nextFloat() * 360.0F);
				golem.setThrone(true);
			}
			level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.5);
			return true;
		}
		return false;
	}

	/**
	 * A mender's watch: {@code dying}, one a mender looks after within {@link #WATCH_RANGE}, is kept alive (at
	 * {@link #WATCH_HEALTH}, with a moment's Regeneration and Resistance) if no mender has done so for them lately.
	 */
	static boolean sparedByMender(LivingEntity dying) {
		if (!(dying.level() instanceof ServerLevel level) || dying instanceof TownGolem) {
			return false;
		}
		long now = level.getGameTime();
		Long last = dying.getAttached(WATCHED);
		if (last != null && now - last < WATCH_EVERY) {
			return false;
		}
		for (TownGolem g : level.getEntitiesOfClass(TownGolem.class, dying.getBoundingBox().inflate(WATCH_RANGE),
			g -> g.isAlive() && g.kind() == TownGolem.Kind.VERDANT && g.watches(dying))) {
			dying.setAttached(WATCHED, now);
			dying.setHealth(WATCH_HEALTH);
			dying.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), g);
			dying.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2), g);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, dying.getX(), dying.getY() + 1.0, dying.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
			level.playSound(null, dying.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2F, 1.4F);
			return true;
		}
		return false;
	}
}
