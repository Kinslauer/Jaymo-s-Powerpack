package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.621, Rivals patch 6b) A Bane's hold ({@link Banes}): at rank five it raises a keep of its kind's stone on its ground
 * (Overworld or Nether, wherever it was born): the grave-knight's deepslate, the warlord's log palisade, the demon-sworn's
 * nether brick. From then on it waits there, its band round it, and only its men come to your fights (marks and all).
 * <ul>
 * <li><b>Its walls ward it:</b> within its hold it takes a share of the harm ({@link #WARD}) and can't be finished.</li>
 * <li><b>Storm it by the seat siege rules</b> ({@link Banners}): plant a banner within {@link #SIEGE_RANGE} of its heart and
 * hold it {@link #SIEGE_TICKS} with none of its band within {@link #HOLD_RADIUS}. Its band go to tear the banner down; a boss
 * bar shows how near it is. Held, the heart breaks and the gate falls in: its ward is gone, and it can be ended.</li>
 * <li>Left standing (not ended) it rebuilds after {@link #REBUILD_DAYS} days. Ended or sworn, the ruin stays.</li>
 * </ul>
 * (0.7.624) Its hounds and claims are {@link BaneCut}'s.
 */
public final class BaneHolds {
	// ================================================================================================================
	// Tuning
	/** It raises its hold this far (blocks) from where it was born. */
	static final int SITE_MIN = 24, SITE_MAX = 48;
	/** The keep: this far each way from its heart, its walls this high. */
	static final int HALF = 6, WALL = 5;
	/** Its band waits there while a player is this near its heart; this many of them (half its men, with marks). */
	static final double GARRISON_NEAR = 72.0;
	static final int GARRISON = 6;
	/** Within this of its heart its walls ward it: it takes this share of harm and can't be finished. */
	static final double WARD_RANGE = 40.0;
	static final float WARD = 0.35F;
	/** A banner this near its heart lays siege; held this long (ticks) with none of its band within {@link #HOLD_RADIUS}, the hold falls. */
	static final double SIEGE_RANGE = 24.0, HOLD_RADIUS = 16.0;
	static final long SIEGE_TICKS = 20L * 150;
	/** Its band this near a banner go to tear it down (this many at most), and do within this reach. */
	static final double DEFEND_RANGE = 40.0, BREAK_REACH = 2.5;
	static final int DEFENDERS = 3;
	/** The bar shows to players this near. */
	static final double BAR_RANGE = 64.0;
	/** Fallen and left standing, it rebuilds after this many days. */
	static final int REBUILD_DAYS = 3;
	/** After it leaves its hold (laughing off wrong marks, struck down by another), this long (ticks) before it waits there again. */
	static final long AWAY_TICKS = 2400L;
	static final int STEP = 20;
	// ================================================================================================================

	static final int PLANNED = 0, STANDING = 1, FALLEN = 2;

	/** A hold: whose (ladder), where (its heart, or until it's built, the place it was born), its state, and when it rebuilds. */
	record Hold(String ladder, String dim, BlockPos heart, int state, long rebuild) {
		static final Codec<Hold> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("ladder").forGetter(Hold::ladder),
				Codec.STRING.fieldOf("dim").forGetter(Hold::dim),
				BlockPos.CODEC.fieldOf("heart").forGetter(Hold::heart),
				Codec.INT.fieldOf("state").forGetter(Hold::state),
				Codec.LONG.optionalFieldOf("rebuild", 0L).forGetter(Hold::rebuild))
			.apply(i, Hold::new));

		Hold with(BlockPos heart, int state, long rebuild) {
			return new Hold(ladder, dim, heart, state, rebuild);
		}
	}

	/** On the Overworld: each Bane's hold, by captain id. Saved. */
	static final AttachmentType<Map<String, Hold>> HOLDS = AttachmentRegistry.create(JaymosKingdoms.id("bane_holds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Hold.CODEC)));

	/** A banner planted at a hold: where, by whom, held how long (ticks). */
	private static final class Siege {
		final BlockPos banner;
		final UUID by;
		long held;
		final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

		Siege(BlockPos banner, UUID by) {
			this.banner = banner;
			this.by = by;
		}
	}

	private record Placing(UUID player, BlockPos clicked, BlockPos beside, long at) {
	}

	/** Each hold's siege (captain id). In memory: a reload lifts it. */
	private static final Map<String, Siege> SIEGES = new HashMap<>();
	private static final List<Placing> PLACING = new ArrayList<>();
	/** Its band sent to tear a banner down. */
	private static final java.util.Set<UUID> SENT = new java.util.HashSet<>();
	/** Each Bane (ladder) away from its hold until then. */
	private static final Map<String, Long> AWAY = new HashMap<>();

	private BaneHolds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % STEP == 0) {
				step(server);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && player.getItemInHand(hand).getItem() instanceof BannerItem) {
				PLACING.add(new Placing(sp.getUUID(), hit.getBlockPos().immutable(), hit.getBlockPos().relative(hit.getDirection()).immutable(), server.getGameTime()));
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Siege s : SIEGES.values()) {
				s.bar.removeAllPlayers();
			}
			SIEGES.clear();
			PLACING.clear();
			SENT.clear();
			AWAY.clear();
		});
	}

	// ---- who and where ------------------------------------------------------------------------------------------------------

	static Map<String, Hold> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(HOLDS, Map.of());
	}

	private static void put(MinecraftServer server, String id, @Nullable Hold hold) {
		Map<String, Hold> all = new HashMap<>(all(server));
		if (hold == null) {
			all.remove(id);
		} else {
			all.put(id, hold);
		}
		Factions.level(server).setAttached(HOLDS, Map.copyOf(all));
	}

	/** {@code c}'s hold, standing. */
	static @Nullable Hold standing(MinecraftServer server, Captain c) {
		Hold h = all(server).get(c.id());
		return h != null && h.state() == STANDING ? h : null;
	}

	private static @Nullable ServerLevel level(MinecraftServer server, Hold h) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(h.dim())));
	}

	/** Whether {@code body} (a Bane) is within its standing hold's walls' ward. */
	static boolean warded(LivingEntity body) {
		if (!(body.level() instanceof ServerLevel level)) {
			return false;
		}
		Captain c = Captains.of(body);
		Hold h = c == null ? null : standing(level.getServer(), c);
		return h != null && level.dimension().identifier().toString().equals(h.dim()) && body.position().distanceTo(Vec3.atCenterOf(h.heart())) <= WARD_RANGE;
	}

	/** (From {@code Captains.dealt}) The share of a blow its walls let through. */
	static float ward(LivingEntity self) {
		return Banes.is(self) && warded(self) ? WARD : 1.0F;
	}

	/** Whether {@code id} (one of a band) is off to tear a banner down: Banes leaves him be. */
	static boolean sent(UUID id) {
		return SENT.contains(id);
	}

	/** (From Banes, when it leaves) It's away from its hold a while. */
	static void away(String ladder, long now) {
		AWAY.put(ladder, now + AWAY_TICKS);
	}

	/** (From Banes) The heart of the hold where {@code ladder}'s Bane waits now, or null. */
	static @Nullable BlockPos heartOf(MinecraftServer server, String ladder) {
		for (Hold h : all(server).values()) {
			if (h.ladder().equals(ladder) && h.state() == STANDING) {
				return h.heart();
			}
		}
		return null;
	}

	// ---- raised -------------------------------------------------------------------------------------------------------------

	/** (From Banes) {@code c} has reached rank five: it will raise its hold on its ground, at {@code home} in {@code dim}. */
	static void raise(MinecraftServer server, Captain c, String dim, BlockPos home) {
		Hold had = all(server).get(c.id());
		if (had == null) {
			put(server, c.id(), new Hold(c.tribe(), dim, home, PLANNED, 0L));
		}
	}

	/** Ended or sworn: its hold is a ruin, no more. */
	static void forget(MinecraftServer server, String id) {
		Siege s = SIEGES.remove(id);
		if (s != null) {
			s.bar.removeAllPlayers();
		}
		if (all(server).containsKey(id)) {
			put(server, id, null);
		}
	}

	// ---- each step ----------------------------------------------------------------------------------------------------------

	private static void step(MinecraftServer server) {
		placed(server);
		long now = Factions.level(server).getGameTime();
		for (Map.Entry<String, Hold> e : List.copyOf(all(server).entrySet())) {
			String id = e.getKey();
			Hold h = e.getValue();
			Captain c = Captains.get(server, id);
			if (c == null || !Banes.bane(c.tribe()) || SwornCaptains.sworn(server, id)) {
				forget(server, id);
				continue;
			}
			ServerLevel level = level(server, h);
			if (level == null) {
				continue;
			}
			switch (h.state()) {
				case PLANNED -> {
					if (near(level, h.heart(), GARRISON_NEAR * 2) && level.isPositionEntityTicking(h.heart())) {
						build(level, c, h);
					}
				}
				case FALLEN -> {
					if (now >= h.rebuild() && level.isPositionEntityTicking(h.heart())) {
						put(server, id, h.with(h.heart(), STANDING, 0L));
						keep(level, Rivals.side(c.tribe()), h.heart(), level.getRandom());
						Post.news(server, Component.translatable("news.jaymoskingdoms.bane.rebuilt", Component.literal(c.name())));
					}
				}
				default -> standing(level, c, h, now);
			}
		}
	}

	/** A player in {@code level} within {@code range} of {@code at}. */
	private static boolean near(ServerLevel level, BlockPos at, double range) {
		Vec3 v = Vec3.atCenterOf(at);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.position().distanceTo(v) <= range) {
				return true;
			}
		}
		return false;
	}

	/** It raises its hold near where it was born, now its chunk is loaded. */
	private static void build(ServerLevel level, Captain c, Hold h) {
		RandomSource random = level.getRandom();
		BlockPos heart = site(level, h.heart(), random);
		if (heart == null) {
			return;
		}
		keep(level, Rivals.side(c.tribe()), heart, random);
		MinecraftServer server = level.getServer();
		put(server, c.id(), h.with(heart, STANDING, 0L));
		UUID ownerId = Banes.owner(c.tribe());
		Component where = Component.translatable("bane.jaymoskingdoms.hold.where", heart.getX(), heart.getZ(),
			Component.translatable(level.dimension() == Level.NETHER ? "bane.jaymoskingdoms.hold.nether" : "bane.jaymoskingdoms.hold.overworld"));
		if (ownerId != null) {
			Post.report(ownerId, Component.translatable("bane.jaymoskingdoms." + Banes.kind(Rivals.side(c.tribe())) + ".hold." + random.nextInt(Banes.LINES),
				Component.literal(c.name()), where));
		}
		Post.news(server, Component.translatable("news.jaymoskingdoms.bane.hold", Component.literal(c.name()), where));
		JaymosKingdoms.LOGGER.info("Bane {} raises its hold at {} in {}", c.name(), heart, h.dim());
	}

	/** A fair spot for it: on the ground {@link #SITE_MIN}-{@link #SITE_MAX} from {@code home}, in loaded land, not too steep. */
	private static @Nullable BlockPos site(ServerLevel level, BlockPos home, RandomSource random) {
		boolean nether = level.dimension() == Level.NETHER;
		for (int i = 0; i < 16; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN + 1);
			BlockPos near = home.offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r));
			if (!level.isPositionEntityTicking(near.offset(-HALF, 0, -HALF)) || !level.isPositionEntityTicking(near.offset(HALF, 0, HALF))) {
				continue;
			}
			BlockPos at = Construction.standable(level, nether ? near : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near), 3);
			if (!level.getFluidState(at.below()).isEmpty() || level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
				continue;
			}
			boolean flat = true;
			for (int[] d : new int[][] {{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}}) {
				BlockPos corner = Construction.standable(level, nether ? at.offset(d[0], 0, d[1])
					: level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(d[0], 0, d[1])), 2);
				flat &= Math.abs(corner.getY() - at.getY()) <= 4;
			}
			if (flat) {
				return at;
			}
		}
		return null;
	}

	/** Its kind's stone: wall, a second wall stone now and then, floor, footing, heart, and the light on it. */
	private static Block[] palette(Rivals.Side kind) {
		return switch (kind) {
			case BANE_WARLORD -> new Block[] {Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.BARREL, Blocks.LANTERN};
			case BANE_DEMON -> new Block[] {Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.LANTERN};
			default -> new Block[] {Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.COBBLED_DEEPSLATE, Blocks.CRYING_OBSIDIAN,
				Blocks.SOUL_LANTERN};
		};
	}

	/** The keep, round {@code heart} (where one stands, its floor just under): walls, corner towers, a south gate, its heart. */
	private static void keep(ServerLevel level, Rivals.Side kind, BlockPos heart, RandomSource random) {
		Block[] p = palette(kind);
		int floor = heart.getY() - 1;
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = heart.getX() + dx, z = heart.getZ() + dz;
				boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				boolean corner = Math.abs(dx) == HALF && Math.abs(dz) == HALF;
				boolean gate = dz == HALF && Math.abs(dx) <= 1;
				for (int y = floor - 1; y >= floor - 4; y--) {
					BlockPos at = new BlockPos(x, y, z);
					if (level.getBlockState(at).canBeReplaced()) {
						set(level, at, p[3].defaultBlockState());
					}
				}
				set(level, new BlockPos(x, floor, z), p[2].defaultBlockState());
				int top = corner ? WALL + 3 : edge ? WALL + (Math.floorMod(dx + dz, 2) == 0 ? 1 : 0) : 0;
				for (int y = 1; y <= WALL + 3; y++) {
					BlockPos at = new BlockPos(x, floor + y, z);
					if (y <= top && !(gate && y <= 3)) {
						set(level, at, (random.nextInt(5) == 0 ? p[1] : p[0]).defaultBlockState());
					} else if (y <= WALL + 1 || edge) {
						set(level, at, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		set(level, heart, p[4].defaultBlockState());
		set(level, heart.above(), p[5].defaultBlockState());
	}

	/** A block set, unless something with a block entity (a chest, a player's things) is there. */
	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		BlockState was = level.getBlockState(at);
		if (was == state || was.hasBlockEntity() && !was.is(Blocks.BARREL)) {
			return;
		}
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
	}

	// ---- standing -----------------------------------------------------------------------------------------------------------

	/** Its band waits there while someone's near; a siege goes on. */
	private static void standing(ServerLevel level, Captain c, Hold h, long now) {
		Siege s = SIEGES.get(c.id());
		if (near(level, h.heart(), GARRISON_NEAR) && !c.fallen() && now >= AWAY.getOrDefault(c.tribe(), 0L) && level.isPositionEntityTicking(h.heart())) {
			Banes.garrison(level, c, h.heart());
		}
		if (s != null) {
			siege(level, c, h, s);
		}
	}

	/** Banners placed lately: one near a standing hold lays siege to it. */
	private static void placed(MinecraftServer server) {
		for (Placing pl : List.copyOf(PLACING)) {
			PLACING.remove(pl);
			ServerPlayer p = server.getPlayerList().getPlayer(pl.player());
			if (p == null || !(p.level() instanceof ServerLevel level)) {
				continue;
			}
			BlockPos banner = isBanner(level, pl.beside()) ? pl.beside() : isBanner(level, pl.clicked()) ? pl.clicked() : null;
			if (banner == null) {
				continue;
			}
			for (Map.Entry<String, Hold> e : all(server).entrySet()) {
				Hold h = e.getValue();
				if (h.state() != STANDING || !level.dimension().identifier().toString().equals(h.dim())
					|| Vec3.atCenterOf(banner).distanceTo(Vec3.atCenterOf(h.heart())) > SIEGE_RANGE) {
					continue;
				}
				Siege old = SIEGES.put(e.getKey(), new Siege(banner, p.getUUID()));
				if (old != null) {
					old.bar.removeAllPlayers();
				}
				Captain c = Captains.get(server, e.getKey());
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.siege", c == null ? "?" : c.name()).withStyle(ChatFormatting.GOLD));
				break;
			}
		}
	}

	private static boolean isBanner(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getBlock() instanceof AbstractBannerBlock;
	}

	/** The banner held: none of its band near, it's held; its band go to tear it down; held long enough, the hold falls. */
	private static void siege(ServerLevel level, Captain c, Hold h, Siege s) {
		if (!isBanner(level, s.banner)) {
			// (torn down, or taken up)
			end(c.id(), s);
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceTo(Vec3.atCenterOf(s.banner)) <= BAR_RANGE) {
					p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.siege.torn").withStyle(ChatFormatting.RED));
				}
			}
			return;
		}
		Vec3 at = Vec3.atCenterOf(s.banner);
		List<PathfinderMob> band = new ArrayList<>();
		for (UUID id : Banes.bandIds(c.tribe())) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive() && !Downed.isDowned(m)) {
				band.add(m);
			}
		}
		boolean contested = false;
		for (PathfinderMob m : band) {
			contested |= m.position().distanceTo(at) <= HOLD_RADIUS;
		}
		if (!contested) {
			s.held += STEP;
		}
		// (the nearest few go to tear it down)
		band.sort(Comparator.comparingDouble(m -> m.position().distanceTo(at)));
		SENT.removeIf(id -> band.stream().noneMatch(m -> m.getUUID().equals(id)));
		for (int i = 0; i < band.size(); i++) {
			PathfinderMob m = band.get(i);
			if (i >= DEFENDERS || m.position().distanceTo(at) > DEFEND_RANGE) {
				SENT.remove(m.getUUID());
				continue;
			}
			SENT.add(m.getUUID());
			m.setTarget(null);
			if (m.position().distanceTo(at) <= BREAK_REACH) {
				level.destroyBlock(s.banner, true, m);
			} else {
				m.getNavigation().moveTo(at.x, at.y, at.z, 1.15);
			}
		}
		s.bar.setName(Component.translatable(contested ? "message.jaymoskingdoms.bane.siege.bar.contested" : "message.jaymoskingdoms.bane.siege.bar",
			Component.literal(c.name())));
		s.bar.setColor(contested ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
		s.bar.setProgress(Math.min(1.0F, s.held / (float)SIEGE_TICKS));
		for (ServerPlayer p : List.copyOf(s.bar.getPlayers())) {
			if (p.isRemoved() || p.level() != level || p.position().distanceTo(at) > BAR_RANGE) {
				s.bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceTo(at) <= BAR_RANGE) {
				s.bar.addPlayer(p);
			}
		}
		if (s.held >= SIEGE_TICKS) {
			fall(level, c, h, s);
		}
	}

	private static void end(String id, Siege s) {
		s.bar.removeAllPlayers();
		SIEGES.remove(id);
		SENT.clear();
	}

	/** Held: its heart breaks and its gate falls in; its ward is gone. It rebuilds in {@link #REBUILD_DAYS} days if it lives. */
	private static void fall(ServerLevel level, Captain c, Hold h, Siege s) {
		end(c.id(), s);
		MinecraftServer server = level.getServer();
		BlockPos heart = h.heart();
		level.destroyBlock(heart.above(), false);
		level.destroyBlock(heart, false);
		int floor = heart.getY() - 1;
		for (int dx = -2; dx <= 2; dx++) {
			for (int y = 1; y <= WALL + 1; y++) {
				BlockPos at = new BlockPos(heart.getX() + dx, floor + y, heart.getZ() + HALF);
				if (!level.getBlockState(at).isAir()) {
					level.destroyBlock(at, false);
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, heart.getX() + 0.5, heart.getY() + 1.0, heart.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, heart, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
		put(server, c.id(), h.with(heart, FALLEN, Factions.level(server).getGameTime() + 24000L * REBUILD_DAYS));
		Entity body = c.body().map(level::getEntity).orElse(null);
		Rivals.Side kind = Rivals.side(c.tribe());
		if (body != null) {
			Speech.say(level, body, Component.translatable("bane.jaymoskingdoms." + Banes.kind(kind) + ".hold_falls." + level.getRandom().nextInt(Banes.LINES)),
				Speech.RED, Captains.SAY_RANGE);
		}
		ServerPlayer by = server.getPlayerList().getPlayer(s.by);
		Post.news(server, Component.translatable("news.jaymoskingdoms.bane.hold_fell", Component.literal(c.name()),
			by == null ? Component.literal("?") : by.getDisplayName()));
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceTo(Vec3.atCenterOf(heart)) <= BAR_RANGE) {
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.hold_fell", c.name()).withStyle(ChatFormatting.GOLD));
			}
		}
	}
}
