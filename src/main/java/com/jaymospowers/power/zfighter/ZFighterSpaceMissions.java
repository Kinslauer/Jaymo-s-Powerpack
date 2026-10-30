package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.62, Andy's NOEA picks) Planet missions: on Rustfall, Glacia and Cinder a fighter can free the world or conquer it, by the
 * path he walks in Jaymo's Kingdoms ({@link ZFighterKingdoms#rogue}: the Rogue's path conquers, any other frees; without Kingdoms,
 * freeing). Begin it from the star map while standing on the planet.
 * <ul>
 * <li><b>Freeing</b>: the occupier's captains land near you in turn ({@link #CAPTAINS} of them to defeat), then the occupier's boss.</li>
 * <li><b>Conquering</b>: the natives' champions come at you in turn ({@link #CAPTAINS} of them), then you are handed a Conquest Flag
 * to plant on the ground.</li>
 * </ul>
 * Every defeat and the planting count for the killer's party members within {@link #PARTY_RANGE} blocks
 * ({@link ZFighterSagas#witnesses}), each on his own mission. The reward is TP (emeralds for one without the spark), ores, and the
 * planet's title. A planet is settled once, one way or the other. Progress is the {@code zfighter_space} attachment, synced to its owner.
 */
public final class ZFighterSpaceMissions {
	// ---- tuning ----
	/** The foes to defeat before the boss or the flag. */
	public static final int CAPTAINS = 4;
	/** How many of a planet's fighters stand near a fighter at once; ticks between landings; and before the boss (and again, if he is lost). */
	static final int FOES_AT_ONCE = 2, SPAWN_DELAY = 100, BOSS_DELAY = 140;
	/** How often (ticks) the missions are looked after. */
	static final int SCAN_TICKS = 20;
	/** A kill counts for the killer's party within this many blocks. */
	static final double PARTY_RANGE = ZFighterSagas.PARTY_RANGE;
	/** A fighter counts as near within this many blocks of the player; he lands this far off (min, max). */
	static final double FOE_NEAR = 96.0, SPAWN_MIN = 14.0, SPAWN_MAX = 24.0;
	/** A captain's or champion's health and damage as shares of a tyrant's; the boss is a whole tyrant. */
	static final float CAPTAIN_HEALTH = 0.22F, CAPTAIN_DAMAGE = 0.55F, BOSS_HEALTH = 1.0F, BOSS_DAMAGE = 1.0F;
	/** Each party member near the spawn adds this share to a fighter's health. */
	static final float PARTY_SCALE = 0.4F;
	/** The ore prizes for conquering are this many times the freeing prizes (freeing pays more TP). */
	static final float CONQUER_ORE = 1.5F;
	/** Ticks between handing a conquering fighter a lost flag again. */
	static final int FLAG_AGAIN = 600;
	/** The flag pole's height, in blocks. */
	static final int POLE = 5;

	public static final int FREE = 1, CONQUER = 2;
	public static final String FOE_TAG = "jaymospowers_space_foe";
	public static final String BOSS_TAG = "jaymospowers_space_boss";

	/** A planet with a mission: its place on the pod's map, its key, how hard its fighters hit, TP for each way, the ores, and the flag's cloth. */
	record Planet(int dest, String key, float might, int freeTp, int conquerTp, Item[] items, int[] counts, String cloth) {
	}

	static final Planet[] PLANETS = {
		new Planet(ZFighterSpace.RUSTFALL, "rustfall", 0.8F, 600, 450, new Item[] {Items.IRON_BLOCK, Items.DIAMOND, Items.RAW_COPPER},
			new int[] {4, 4, 24}, "minecraft:red_wool"),
		new Planet(ZFighterSpace.GLACIA, "glacia", 1.0F, 900, 700, new Item[] {Items.EMERALD, Items.LAPIS_LAZULI, Items.DIAMOND},
			new int[] {16, 24, 6}, "minecraft:light_blue_wool"),
		new Planet(ZFighterSpace.CINDER, "cinder", 1.3F, 1400, 1100, new Item[] {Items.NETHERITE_SCRAP, Items.GOLD_BLOCK, Items.DIAMOND},
			new int[] {4, 4, 8}, "minecraft:orange_wool"),
	};

	/**
	 * A fighter's missions: for each planet one number, {@code mode | phase << 2 | count << 4} (mode 0 none, 1 freeing, 2 conquering;
	 * phase 0 the captains or champions, 1 the boss or the flag), and the planets freed and conquered (bitmasks).
	 */
	public record Data(List<Integer> state, int freed, int conquered) {
		public static final Data NONE = new Data(List.of(), 0, 0);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf().optionalFieldOf("state", List.of()).forGetter(Data::state),
			Codec.INT.optionalFieldOf("freed", 0).forGetter(Data::freed),
			Codec.INT.optionalFieldOf("conquered", 0).forGetter(Data::conquered)).apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Data::state,
			ByteBufCodecs.VAR_INT, Data::freed,
			ByteBufCodecs.VAR_INT, Data::conquered,
			Data::new);

		public int of(int planet) {
			return planet >= 0 && planet < state.size() ? state.get(planet) : 0;
		}

		public boolean freedPlanet(int planet) {
			return (freed & 1 << planet) != 0;
		}

		public boolean conqueredPlanet(int planet) {
			return (conquered & 1 << planet) != 0;
		}

		public boolean settled(int planet) {
			return freedPlanet(planet) || conqueredPlanet(planet);
		}

		Data with(int planet, int value) {
			List<Integer> next = new ArrayList<>(state);
			while (next.size() < PLANETS.length) {
				next.add(0);
			}
			next.set(planet, value);
			return new Data(next, freed, conquered);
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_space"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	public static Item FLAG;

	/** When each fighter's next foe may land, and when a lost flag may be handed over again (game time). Not saved. */
	private static final Map<UUID, Long> NEXT_FOE = new HashMap<>();
	private static final Map<UUID, Long> NEXT_FLAG = new HashMap<>();

	private ZFighterSpaceMissions() {
	}

	public static int mode(int value) {
		return value & 3;
	}

	public static int phase(int value) {
		return value >> 2 & 3;
	}

	public static int count(int value) {
		return value >> 4;
	}

	static int pack(int mode, int phase, int count) {
		return mode | phase << 2 | count << 4;
	}

	public static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	/** Which planet with a mission {@code level} is, or -1. */
	public static int planetOf(Level level) {
		for (int i = 0; i < PLANETS.length; i++) {
			if (ZFighterSpace.DESTS[PLANETS[i].dest()].level() == level.dimension()) {
				return i;
			}
		}
		return -1;
	}

	/** The planet's index for a place on the map (a {@link ZFighterSpace} index), or -1. */
	public static int planetAt(int dest) {
		for (int i = 0; i < PLANETS.length; i++) {
			if (PLANETS[i].dest() == dest) {
				return i;
			}
		}
		return -1;
	}

	/** The place on the pod's map that planet {@code planet} is. */
	public static int destOf(int planet) {
		return PLANETS[planet].dest();
	}

	public static String key(int planet) {
		return PLANETS[planet].key();
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("planet_flag"));
		FLAG = Registry.register(BuiltInRegistries.ITEM, key, new FlagItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(FLAG));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSpaceMissions::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(ZFighterSpaceMissions::afterDeath);
	}

	// ---------------------------------------------------------------- the flag

	/** The Conquest Flag: use it on the ground to plant it, on a planet you are conquering once its champions are down. */
	static final class FlagItem extends Item {
		FlagItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos base = context.getClickedPos().relative(context.getClickedFace());
			if (plant(player, base)) {
				context.getItemInHand().shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- beginning and giving up

	/** The star map's button: begin the mission of the planet he stands on. */
	static void begin(ServerPlayer player) {
		int planet = planetOf(player.level());
		if (planet < 0) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_no_mission").withStyle(ChatFormatting.GRAY));
			return;
		}
		Data data = of(player);
		if (data.settled(planet)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_settled", Component.translatable("space.jaymospowers.dest." + key(planet)))
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		if (mode(data.of(planet)) != 0) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_under_way").withStyle(ChatFormatting.GRAY));
			return;
		}
		int mode = ZFighterKingdoms.rogue(player) ? CONQUER : FREE;
		player.setAttached(DATA, data.with(planet, pack(mode, 0, 0)));
		NEXT_FOE.put(player.getUUID(), player.level().getGameTime() + SPAWN_DELAY / 2);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		player.sendSystemMessage(Component.translatable("space.jaymospowers." + key(planet) + (mode == FREE ? ".free_intro" : ".conquer_intro")).withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.translatable(mode == FREE ? "message.jaymospowers.space_begin_free" : "message.jaymospowers.space_begin_conquer", CAPTAINS)
			.withStyle(ChatFormatting.GOLD));
	}

	/** The star map's button: give the mission here up (it can be begun again; the count is lost). */
	static void abandon(ServerPlayer player) {
		int planet = planetOf(player.level());
		Data data = of(player);
		if (planet < 0 || mode(data.of(planet)) == 0) {
			return;
		}
		player.setAttached(DATA, data.with(planet, 0));
		clearFoes(player);
		takeFlags(player);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.space_abandoned").withStyle(ChatFormatting.GRAY));
	}

	/** He has just landed: if there is a mission here he has not begun, say so. */
	static void arrived(ServerPlayer player) {
		int planet = planetOf(player.level());
		if (planet >= 0 && !of(player).settled(planet) && mode(of(player).of(planet)) == 0) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_mission_here", Component.translatable("space.jaymospowers.dest." + key(planet)))
				.withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------- watching

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int planet = planetOf(player.level());
			if (planet < 0 || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			int value = of(player).of(planet);
			if (mode(value) == 0) {
				continue;
			}
			if (phase(value) == 0) {
				keepFoes(player, planet, mode(value), false);
			} else if (mode(value) == FREE) {
				keepFoes(player, planet, mode(value), true);
			} else {
				keepFlag(player);
			}
		}
	}

	private static String planetTag(int planet) {
		return "jaymospowers_space_" + PLANETS[planet].key();
	}

	private static String ownerTag(UUID owner) {
		return FOE_TAG + "_" + owner;
	}

	/** While the fighting goes on, {@link #FOES_AT_ONCE} of the planet's fighters (or its boss) stand near him; a new one lands after a wait. */
	private static void keepFoes(ServerPlayer player, int planet, int mode, boolean boss) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		String tag = planetTag(planet);
		int near = level.getEntitiesOfClass(ZFighterRogue.class, player.getBoundingBox().inflate(FOE_NEAR),
			e -> e.isAlive() && e.entityTags().contains(tag) && e.entityTags().contains(BOSS_TAG) == boss).size();
		if (near >= (boss ? 1 : FOES_AT_ONCE)) {
			NEXT_FOE.put(player.getUUID(), now + (boss ? BOSS_DELAY : SPAWN_DELAY));
			return;
		}
		Long next = NEXT_FOE.get(player.getUUID());
		if (next == null) {
			NEXT_FOE.put(player.getUUID(), now + (boss ? BOSS_DELAY : SPAWN_DELAY));
			return;
		}
		if (now < next) {
			return;
		}
		NEXT_FOE.put(player.getUUID(), now + (boss ? BOSS_DELAY : SPAWN_DELAY));
		spawn(player, planet, mode, boss);
	}

	private static void spawn(ServerPlayer player, int planet, int mode, boolean boss) {
		ServerLevel level = player.level();
		Planet info = PLANETS[planet];
		ZFighterRogue fighter = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
		if (fighter == null) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos at = null;
		for (int tries = 0; tries < 6 && at == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			at = floor(level, Mth.floor(player.getX() + Math.cos(angle) * distance), Mth.floor(player.getZ() + Math.sin(angle) * distance), player.getBlockY());
		}
		if (at == null) {
			fighter.discard();
			return;
		}
		String role = boss ? "boss" : mode == FREE ? "captain" : "champion";
		fighter.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		fighter.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.space_foe." + info.key() + "." + role));
		int party = ZFighterSagas.neighbours(player).size();
		float share = (boss ? BOSS_HEALTH : CAPTAIN_HEALTH) * (1.0F + PARTY_SCALE * (party - 1));
		double health = ZFighterRogue.TYRANT_HEALTH * share * info.might();
		fighter.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		fighter.setHealth((float)health);
		fighter.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ZFighterRogue.TYRANT_DAMAGE * (boss ? BOSS_DAMAGE : CAPTAIN_DAMAGE) * info.might());
		fighter.addTag(FOE_TAG);
		fighter.addTag(planetTag(planet));
		fighter.addTag(ownerTag(player.getUUID()));
		if (boss) {
			fighter.addTag(BOSS_TAG);
		}
		fighter.setPersistenceRequired();
		fighter.setTarget(player);
		level.addFreshEntity(fighter);
		if (boss) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 1.3F);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_boss", fighter.getDisplayName()).withStyle(ChatFormatting.RED));
		} else {
			level.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0F, 1.4F);
		}
	}

	/** A floor to land on near column (x, z): the surface under open sky, or a cave floor under a ceiling; null if there is none. */
	private static @Nullable BlockPos floor(ServerLevel level, int x, int z, int aroundY) {
		if (level.dimensionType().hasCeiling()) {
			for (int y = Math.min(level.getMaxY() - 8, aroundY + 8); y > Math.max(level.getMinY() + 2, aroundY - 24); y--) {
				BlockPos feet = new BlockPos(x, y, z);
				if (level.getBlockState(feet.below()).isSolidRender() && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
					&& level.getBlockState(feet.below()).getFluidState().isEmpty()) {
					return feet;
				}
			}
			return null;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		return level.getBlockState(top.below()).getFluidState().isEmpty() ? top : null;
	}

	/** A conquering fighter whose champions are down has a flag to plant; if he lost it, another is handed over after a wait. */
	private static void keepFlag(ServerPlayer player) {
		if (carriesFlag(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long next = NEXT_FLAG.get(player.getUUID());
		if (next != null && now < next) {
			return;
		}
		NEXT_FLAG.put(player.getUUID(), now + FLAG_AGAIN);
		ZFighterSagas.give(player, new ItemStack(FLAG));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.space_flag").withStyle(ChatFormatting.GOLD));
	}

	private static boolean carriesFlag(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(FLAG)) {
				return true;
			}
		}
		return false;
	}

	private static void takeFlags(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(FLAG)) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** Sends the planet's fighters that came for this fighter away (he gave up, or it is done). */
	private static void clearFoes(ServerPlayer player) {
		String tag = ownerTag(player.getUUID());
		for (ZFighterRogue foe : player.level().getEntities(EntityTypeTest.forClass(ZFighterRogue.class), e -> e.entityTags().contains(tag))) {
			foe.discard();
		}
	}

	// ---------------------------------------------------------------- kills

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ZFighterRogue foe) || !foe.entityTags().contains(FOE_TAG) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int planet = -1;
		for (int i = 0; i < PLANETS.length; i++) {
			if (foe.entityTags().contains(planetTag(i))) {
				planet = i;
			}
		}
		ServerPlayer killer = killer(level, foe, source);
		if (planet < 0 || killer == null) {
			return;
		}
		boolean boss = foe.entityTags().contains(BOSS_TAG);
		for (ServerPlayer player : ZFighterSagas.witnesses(killer, foe)) {
			if (planetOf(player.level()) != planet) {
				continue;
			}
			Data data = of(player);
			int value = data.of(planet);
			if (mode(value) == 0) {
				continue;
			}
			if (boss) {
				if (phase(value) == 1 && mode(value) == FREE) {
					finish(player, planet, FREE);
				}
			} else if (phase(value) == 0) {
				int total = count(value) + 1;
				if (total >= CAPTAINS) {
					player.setAttached(DATA, data.with(planet, pack(mode(value), 1, 0)));
					NEXT_FOE.put(player.getUUID(), level.getGameTime() + BOSS_DELAY);
					player.sendSystemMessage(Component.translatable(mode(value) == FREE ? "message.jaymospowers.space_captains_down" : "message.jaymospowers.space_champions_down")
						.withStyle(ChatFormatting.GOLD));
					if (mode(value) == CONQUER) {
						keepFlag(player);
					}
				} else {
					player.setAttached(DATA, data.with(planet, pack(mode(value), 0, total)));
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_foe_down", total, CAPTAINS).withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	private static @Nullable ServerPlayer killer(ServerLevel level, ZFighterRogue victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}
		UUID best = null;
		float most = 0.0F;
		for (Map.Entry<UUID, Float> hurt : victim.hurtBy.entrySet()) {
			if (hurt.getValue() > most) {
				most = hurt.getValue();
				best = hurt.getKey();
			}
		}
		return best == null ? null : level.getServer().getPlayerList().getPlayer(best);
	}

	// ---------------------------------------------------------------- planting the flag

	/** Plants the flag at {@code base} for {@code player} and his party; false if it is not the time or the place (the flag is kept). */
	static boolean plant(ServerPlayer player, BlockPos base) {
		ServerLevel level = player.level();
		int planet = planetOf(level);
		if (planet < 0 || mode(of(player).of(planet)) != CONQUER || phase(of(player).of(planet)) != 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_flag_not_yet").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockState pole = Blocks.OAK_FENCE.defaultBlockState();
		Identifier clothId = Identifier.tryParse(PLANETS[planet].cloth());
		Block cloth = clothId == null ? Blocks.OAK_PLANKS : BuiltInRegistries.BLOCK.getValue(clothId);
		List<BlockPos> spots = new ArrayList<>();
		for (int up = 0; up < POLE; up++) {
			spots.add(base.above(up));
		}
		for (int across = 1; across <= 3; across++) {
			spots.add(base.above(POLE - 1).east(across));
			spots.add(base.above(POLE - 2).east(across));
		}
		for (BlockPos spot : spots) {
			if (!level.getBlockState(spot).canBeReplaced()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_flag_blocked").withStyle(ChatFormatting.GRAY));
				return false;
			}
		}
		for (int up = 0; up < POLE; up++) {
			level.setBlock(base.above(up), pole, Block.UPDATE_ALL);
		}
		for (int across = 1; across <= 3; across++) {
			level.setBlock(base.above(POLE - 1).east(across), cloth.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(base.above(POLE - 2).east(across), cloth.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.playSound(null, base, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.playSound(null, base, SoundEvents.RAID_HORN.value(), SoundSource.BLOCKS, 2.0F, 1.0F);
		for (ServerPlayer member : ZFighterSagas.neighbours(player)) {
			if (planetOf(member.level()) == planet && mode(of(member).of(planet)) == CONQUER && phase(of(member).of(planet)) == 1) {
				finish(member, planet, CONQUER);
				if (member != player) {
					takeFlags(member);
				}
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- the prize

	private static void finish(ServerPlayer player, int planet, int mode) {
		Planet info = PLANETS[planet];
		boolean firstTime = !of(player).settled(planet);
		Data data = of(player).with(planet, 0);
		boolean free = mode == FREE;
		player.setAttached(DATA, new Data(data.state(), free ? data.freed() | 1 << planet : data.freed(), free ? data.conquered() : data.conquered() | 1 << planet));
		clearFoes(player);
		int tp = free ? info.freeTp() : info.conquerTp();
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(player, tp);
		} else {
			ZFighterSagas.give(player, new ItemStack(Items.EMERALD, tp / 20));
		}
		float ore = free ? 1.0F : CONQUER_ORE;
		for (int i = 0; i < info.items().length; i++) {
			ZFighterSagas.give(player, new ItemStack(info.items()[i], Math.max(1, Math.round(info.counts()[i] * ore))));
		}
		// (0.18.49) the first time a world is settled it also gives the one part only it has, for mending a pod better
		Item worldPart = firstTime ? com.jaymospowers.power.zfighter.pod.PodGrades.worldPart(info.key()) : null;
		if (worldPart != null) {
			ZFighterSagas.give(player, new ItemStack(worldPart));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.space_part", new ItemStack(worldPart).getHoverName()).withStyle(ChatFormatting.AQUA));
		}
		Component title = title(planet, mode);
		player.sendSystemMessage(Component.translatable("space.jaymospowers." + info.key() + (free ? ".free_done" : ".conquer_done")).withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.space_done", title, tp).withStyle(ChatFormatting.GOLD));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.06);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.space_told", player.getDisplayName(), title)
			.withStyle(ChatFormatting.GRAY), false);
	}

	/** "Liberator of Rustfall" or "Conqueror of Rustfall". */
	public static Component title(int planet, int mode) {
		return Component.translatable("title.jaymospowers.space." + (mode == FREE ? "free" : "conquer"),
			Component.translatable("space.jaymospowers.dest." + key(planet)));
	}

	/** The titles {@code player} has earned, best (the latest planets) first. */
	public static List<Component> titles(Player player) {
		Data data = of(player);
		List<Component> out = new ArrayList<>();
		for (int i = PLANETS.length - 1; i >= 0; i--) {
			if (data.freedPlanet(i)) {
				out.add(title(i, FREE));
			}
			if (data.conqueredPlanet(i)) {
				out.add(title(i, CONQUER));
			}
		}
		return out;
	}
}
