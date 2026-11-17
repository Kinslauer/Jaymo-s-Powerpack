package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) <b>The Cordon</b>: a sect of the Ash Whisperer's faithful (Andy, 2026-11-17: "a faction of the ash that
 * forbid anything other than the practice of blindseeing, blood and death magic") who hunt the awakened. Our own take on
 * the idea: none of the Awakening's code, names or art.
 * <ul>
 * <li><b>Who they hunt</b> ({@link #heretic}): anyone holding a power of Jaymo's Powers, but a Resonance holder who can't see
 * (blindseeing); and anyone working sanctioned magic: a Mage, Cleric, Druid or Paladin, a Warlock (but the Reaper's), a
 * Sorcerer of any element but death and blood. A blood or death mage, the blind who see by sound, and everyone without
 * an art are left alone.</li>
 * <li><b>Patrols</b> ({@link #patrol}): now and then, out in the wild of the Overworld (never in a realm's land), a patrol
 * comes for a heretic, bigger the higher the world tier: sentinels, and from the Borderlands a stalker, from the Wildlands
 * an enforcer, and from the Dread an Overseer at its head ({@link CordonFolk} for what each does).</li>
 * <li><b>The summons</b>: an Overseer carries one. Read (used) in the Overworld, it turns into a map with the Court of
 * Judgment marked on it, {@link #COURT_MIN}-{@link #COURT_MAX} blocks off. The court is raised when someone comes near
 * ({@link #COURT_NEAR}), and the Arbiter sits in it: the Cordon's judge, who learns the harm you deal it ({@code aw-adapt}).</li>
 * </ul>
 */
public final class Cordon {
	// ---- tuning ----
	/** How often each player is looked at for a patrol (ticks), the least time between two patrols for one player, and the chance. */
	static final int LOOK_EVERY = 2400;
	static final long PATROL_GAP = 12000L;
	static final float PATROL_CHANCE = 0.12F, PATROL_CHANCE_PER_TIER = 0.04F;
	/** How far off a patrol arrives (blocks), and how many sentinels at the first tier (one more each tier, up to the most). */
	static final int ARRIVE_MIN = 26, ARRIVE_MAX = 40, SENTINELS = 2, MOST_SENTINELS = 5;
	/** The world tier (ordinal) a stalker, an enforcer and an Overseer first come with a patrol. */
	static final int STALKER_FROM = 1, ENFORCER_FROM = 2, OVERSEER_FROM = 3;
	/** How far off the court is from where the summons is read (blocks), and how near a player must come for it to rise. */
	static final int COURT_MIN = 400, COURT_MAX = 700, COURT_NEAR = 48;
	/** The court's floor radius, its pillars' height, and how many sentinels stand guard with the Arbiter. */
	/** How many spots a summons tries for one outside every realm's land. */
	static final int COURT_TRIES = 16;
	/** A chest, bed or the like this far below the ground (blocks) or deeper is not someone's building on the court's spot. */
	static final int BUILT_BELOW = 6;
	static final int COURT_RADIUS = 11, PILLAR_HEIGHT = 6, COURT_GUARDS = 2;
	/** Ash grey, and the binding red of their seals. */
	public static final int ASH = 0x8A8580, BINDING = 0xB0202A;

	/** Their ranks, one entity type each: health, harm, armour, speed. */
	public enum Rank {
		SENTINEL(26.0, 5.0, 4.0, 0.25), STALKER(22.0, 4.0, 2.0, 0.30), ENFORCER(36.0, 6.0, 8.0, 0.23), OVERSEER(60.0, 7.0, 8.0, 0.25),
		ARBITER(320.0, 11.0, 12.0, 0.26);

		final double health, damage, armour, speed;

		Rank(double health, double damage, double armour, double speed) {
			this.health = health;
			this.damage = damage;
			this.armour = armour;
			this.speed = speed;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final Map<Rank, EntityType<CordonFolk>> TYPES = new EnumMap<>(Rank.class);
	public static Item SUMMONS;

	/**
	 * The courts on the Overworld: packed position → 0 marked on a map, not yet raised; 1 raised, its Arbiter sitting;
	 * 2 judged (its Arbiter fallen). Kept on the Overworld.
	 */
	static final AttachmentType<Map<String, Integer>> COURTS = AttachmentRegistry.create(JaymosKingdoms.id("cordon_courts"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	static final int MARKED = 0, RAISED = 1, JUDGED = 2;

	/** When each player last had a patrol sent after them (game time). */
	private static final Map<UUID, Long> LAST_PATROL = new HashMap<>();

	private Cordon() {
	}

	static void register() {
		for (Rank rank : Rank.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("cordon_" + rank.key()));
			float scale = rank == Rank.ARBITER ? 1.25F : 1.0F;
			EntityType<CordonFolk> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<CordonFolk>of(CordonFolk::new, MobCategory.MONSTER).noLootTable().sized(0.6F * scale, 1.95F * scale)
					.eyeHeight(1.74F * scale).clientTrackingRange(10).build(key));
			TYPES.put(rank, type);
			FabricDefaultAttributeRegistry.register(type, CordonFolk.attributes(rank));
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("cordon_summons"));
		SUMMONS = Registry.register(BuiltInRegistries.ITEM, key, new Summons(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, SUMMONS));
		JaymosKingdoms.LOGGER.debug("cordon courts attachment: {}", COURTS);
		ServerTickEvents.END_SERVER_TICK.register(Cordon::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_PATROL.clear());
	}

	/** {@code type}'s rank, or null for no Cordon type. */
	static @Nullable Rank rank(EntityType<?> type) {
		for (Map.Entry<Rank, EntityType<CordonFolk>> e : TYPES.entrySet()) {
			if (e.getValue() == type) {
				return e.getKey();
			}
		}
		return null;
	}

	// ---- who they hunt ------------------------------------------------------------------------------------------------

	/** Whether the Cordon hunts {@code player}: a power, or a sanctioned art (see the class note). */
	public static boolean heretic(Player player) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		String power = PowersCompat.power(player);
		if (power != null && !("resonance".equals(power) && PowersCompat.sightless(player))) {
			return true;
		}
		for (Classes.PlayerClass cls : Classes.both(player)) {
			String option = Classes.optionOf(player, cls);
			switch (cls) {
				case MAGE, CLERIC, DRUID, PALADIN -> {
					return true;
				}
				case WARLOCK -> {
					if (!"reaper".equals(option)) {
						return true;
					}
				}
				case SORCERER -> {
					if (!ForbiddenArts.death(option) && !ForbiddenArts.blood(option)) {
						return true;
					}
				}
				default -> {
				}
			}
		}
		return false;
	}

	// ---- patrols ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if (now % 40 == 0) {
			courts(overworld);
		}
		for (ServerPlayer player : overworld.players()) {
			if ((now + player.getId()) % LOOK_EVERY != 0 || !player.isAlive() || !heretic(player)) {
				continue;
			}
			Long last = LAST_PATROL.get(player.getUUID());
			int tier = WorldTiers.of(player).ordinal();
			if (last != null && now - last < PATROL_GAP || overworld.getRandom().nextFloat() >= PATROL_CHANCE + PATROL_CHANCE_PER_TIER * tier) {
				continue;
			}
			if (Factions.at(server, overworld, player.blockPosition()) != null) {
				continue; // they hunt in the wild, not in a realm's land
			}
			if (patrol(overworld, player, tier)) {
				LAST_PATROL.put(player.getUUID(), now);
			}
		}
	}

	/** Sends a patrol after {@code player}, sized by world tier {@code tier}: true if it came. */
	static boolean patrol(ServerLevel level, ServerPlayer player, int tier) {
		RandomSource random = level.getRandom();
		BlockPos at = null;
		for (int tries = 0; tries < 8 && at == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, distance = Mth.nextInt(random, ARRIVE_MIN, ARRIVE_MAX);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getBlockState(top.below()).isSolid() && level.getFluidState(top).isEmpty() && Math.abs(top.getY() - player.getY()) < 16) {
				at = top;
			}
		}
		if (at == null) {
			return false;
		}
		List<Rank> ranks = new java.util.ArrayList<>();
		for (int i = 0; i < Math.min(MOST_SENTINELS, SENTINELS + tier); i++) {
			ranks.add(Rank.SENTINEL);
		}
		if (tier >= STALKER_FROM) {
			ranks.add(Rank.STALKER);
		}
		if (tier >= ENFORCER_FROM) {
			ranks.add(Rank.ENFORCER);
		}
		if (tier >= OVERSEER_FROM) {
			ranks.add(0, Rank.OVERSEER);
		}
		CordonFolk first = null;
		for (Rank rank : ranks) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
			CordonFolk folk = spawn(level, rank, spot);
			if (folk != null) {
				folk.setTarget(player);
				if (first == null) {
					first = folk;
				}
			}
		}
		if (first == null) {
			return false;
		}
		Speech.say(level, first, Component.translatable("speech.jaymoskingdoms.cordon.arrive." + random.nextInt(3)), Speech.RED, 48.0);
		level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.8F, 0.6F);
		return true;
	}

	/** One of the Cordon of {@code rank}, at {@code at}, in the world; null if it couldn't be made. */
	static @Nullable CordonFolk spawn(ServerLevel level, Rank rank, BlockPos at) {
		CordonFolk folk = TYPES.get(rank).create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(folk);
		return folk;
	}

	// ---- the summons and the court ------------------------------------------------------------------------------------

	/** The summons an Overseer carries: read in the Overworld, it becomes a map to the Court of Judgment. */
	static final class Summons extends Item {
		Summons(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			if (server != server.getServer().overworld()) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cordon.summons_blank"));
				return InteractionResult.FAIL;
			}
			ItemStack map = map(server, mark(server, sp));
			server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cordon.summons_read"));
			ItemStack held = player.getItemInHand(hand);
			held.consume(1, player);
			if (held.isEmpty()) {
				return InteractionResult.SUCCESS.heldItemTransformedTo(map);
			}
			if (!sp.addItem(map)) {
				sp.drop(map, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Marks a new court in the wild, {@link #COURT_MIN}-{@link #COURT_MAX} blocks from {@code near}. */
	private static BlockPos mark(ServerLevel level, ServerPlayer near) {
		RandomSource random = level.getRandom();
		BlockPos court = null;
		for (int tries = 0; tries < COURT_TRIES; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, distance = Mth.nextInt(random, COURT_MIN, COURT_MAX);
			court = BlockPos.containing(near.getX() + Math.cos(angle) * distance, 64, near.getZ() + Math.sin(angle) * distance);
			if (Factions.at(level.getServer(), level, court) == null) {
				break; // the court sits in the wild, never in a realm's land
			}
		}
		Map<String, Integer> courts = new HashMap<>(level.getAttachedOrElse(COURTS, Map.of()));
		courts.put(Long.toString(court.asLong()), MARKED);
		level.setAttached(COURTS, courts);
		return court;
	}

	/** The map that leads to {@code court}. */
	private static ItemStack map(ServerLevel level, BlockPos court) {
		ItemStack map = MapItem.create(level, court.getX(), court.getZ(), (byte)2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, court, "+", MapDecorationTypes.TARGET_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.cordon_court_map"));
		return map;
	}

	/** Raises any court a player has come near, with its Arbiter and guards. */
	private static void courts(ServerLevel level) {
		Map<String, Integer> courts = level.getAttached(COURTS);
		if (courts == null || !courts.containsValue(MARKED)) {
			return;
		}
		for (Map.Entry<String, Integer> e : courts.entrySet()) {
			if (e.getValue() != MARKED) {
				continue;
			}
			BlockPos marked = BlockPos.of(Long.parseLong(e.getKey()));
			for (ServerPlayer player : level.players()) {
				double dx = player.getX() - marked.getX(), dz = player.getZ() - marked.getZ();
				if (dx * dx + dz * dz <= COURT_NEAR * COURT_NEAR && level.isLoaded(marked)
					&& level.hasChunksAt(marked.offset(-COURT_RADIUS, 0, -COURT_RADIUS), marked.offset(COURT_RADIUS, 0, COURT_RADIUS))) {
					// never over anyone's work: a realm's land, or ground with a chest, bed, furnace or the like on it
					if (Factions.at(level.getServer(), level, marked) == null && !built(level, marked)) {
						raise(level, marked);
						Map<String, Integer> next = new HashMap<>(courts);
						next.put(e.getKey(), RAISED);
						level.setAttached(COURTS, next);
						return; // one a look
					}
					// the ground was taken since the summons was read: the court sits elsewhere, and whoever came gets the way to it
					Map<String, Integer> next = new HashMap<>(courts);
					next.remove(e.getKey());
					level.setAttached(COURTS, next);
					ItemStack map = map(level, mark(level, player));
					if (!player.addItem(map)) {
						player.drop(map, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cordon.court_moved"));
					return; // one a look
				}
			}
		}
	}

	/**
	 * Whether someone has built where the court would stand: any block entity inside its round, at the ground or above it
	 * (a mineshaft's chest or a spawner far below is nobody's work).
	 */
	private static boolean built(ServerLevel level, BlockPos marked) {
		int r = COURT_RADIUS + 2;
		int floor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, marked).getY() - BUILT_BELOW;
		for (int cx = (marked.getX() - r) >> 4; cx <= (marked.getX() + r) >> 4; cx++) {
			for (int cz = (marked.getZ() - r) >> 4; cz <= (marked.getZ() + r) >> 4; cz++) {
				for (BlockPos at : level.getChunk(cx, cz).getBlockEntitiesPos()) {
					int dx = at.getX() - marked.getX(), dz = at.getZ() - marked.getZ();
					if (dx * dx + dz * dz <= r * r && at.getY() >= floor) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The Arbiter at {@code court} has fallen: the court is judged. */
	static void judged(ServerLevel level, long court) {
		ServerLevel overworld = level.getServer().overworld();
		Map<String, Integer> courts = overworld.getAttached(COURTS);
		if (courts != null && courts.containsKey(Long.toString(court))) {
			Map<String, Integer> next = new HashMap<>(courts);
			next.put(Long.toString(court), JUDGED);
			overworld.setAttached(COURTS, next);
		}
	}

	/**
	 * The Court of Judgment, raised at {@code marked} on the ground there: a round floor of dark brick, a ring of ash pillars
	 * each with a soul lantern, the judge's dais on the north side, the air cleared above it; the Arbiter on the dais and
	 * sentinels at its feet. Block entities are never touched.
	 */
	static void raise(ServerLevel level, BlockPos marked) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, marked);
		int y = ground.getY();
		BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), under = Blocks.STONE_BRICKS.defaultBlockState(),
			aisle = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), pillar = Blocks.POLISHED_BASALT.defaultBlockState();
		int r = COURT_RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = -3; dy <= PILLAR_HEIGHT + 2; dy++) {
					BlockPos p = new BlockPos(marked.getX() + dx, y + dy, marked.getZ() + dz);
					if (level.getBlockEntity(p) != null || level.getBlockState(p).is(Blocks.BEDROCK)) {
						continue;
					}
					BlockState put = dy < -1 ? under : dy == -1 ? (dx == 0 ? aisle : floor) : Blocks.AIR.defaultBlockState();
					if (dy < -1 && !level.getBlockState(p).isAir() && level.getFluidState(p).isEmpty()) {
						continue; // keep the solid ground under it
					}
					level.setBlock(p, put, 2);
				}
			}
		}
		// the pillars, each with a soul lantern
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			int px = marked.getX() + (int)Math.round(Math.cos(a) * (r - 1)), pz = marked.getZ() + (int)Math.round(Math.sin(a) * (r - 1));
			for (int dy = 0; dy < PILLAR_HEIGHT; dy++) {
				level.setBlock(new BlockPos(px, y + dy, pz), pillar, 2);
			}
			level.setBlock(new BlockPos(px, y + PILLAR_HEIGHT, pz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
		}
		// the judge's dais, on the north side
		BlockPos dais = new BlockPos(marked.getX(), y, marked.getZ() - r + 4);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(dais.offset(dx, 0, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
				if (Math.abs(dx) <= 1) {
					level.setBlock(dais.offset(dx, 1, dz), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
				}
			}
		}
		CordonFolk arbiter = spawn(level, Rank.ARBITER, dais.above(2));
		if (arbiter != null) {
			arbiter.setCourt(marked.asLong());
			arbiter.setPersistenceRequired();
		}
		for (int i = 0; i < COURT_GUARDS; i++) {
			CordonFolk guard = spawn(level, Rank.SENTINEL, new BlockPos(marked.getX() + (i == 0 ? -3 : 3), y, marked.getZ() - r + 7));
			if (guard != null) {
				guard.setPersistenceRequired();
			}
		}
		level.playSound(null, marked.getX(), y, marked.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		Fx.ring(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(marked.atY(y)), r, BINDING);
	}
}
