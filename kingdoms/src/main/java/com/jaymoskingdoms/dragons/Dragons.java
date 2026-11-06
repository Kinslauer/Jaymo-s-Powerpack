package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Stacked;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) The wind dragons ({@code docs/Dragons-Plan.md}, {@link WindDragon}): Notus, Boreas and Zephyrus, and (0.7.141)
 * Eurus and Aeolus, who go about disguised as villagers ({@link Disguises}). In
 * the Overworld each comes as a rare wilderness encounter ({@code Encounters}, biome tags
 * {@code jaymoskingdoms:has_<species>_dragon}): Notus over the badlands, Boreas over the frozen peaks, Zephyrus in the
 * swamps. In the Nether, where encounters don't reach, this looks for Notus over the basalt deltas (its tag). The
 * rider's breath key sends {@link Breathe}. Ops: {@code /kingdom dragon [notus|boreas|zephyrus] [tame]}.
 */
public final class Dragons {
	/** The Nether: how often each player there is weighed (ticks), and the chance Notus turns up then (about once in 25 hours in the deltas). */
	static final int NETHER_CHECK_TICKS = 600;
	static final float NETHER_CHANCE = 1.0F / 1500.0F;
	/** How far off it turns up there. */
	static final int NETHER_MIN = 24, NETHER_MAX = 40;

	/** The species' bodies (width, height in blocks). */
	static final float NOTUS_WIDTH = 3.0F, NOTUS_HEIGHT = 2.4F, BOREAS_WIDTH = 2.6F, BOREAS_HEIGHT = 2.0F, ZEPHYRUS_WIDTH = 2.6F,
		ZEPHYRUS_HEIGHT = 1.8F, EURUS_WIDTH = 2.4F, EURUS_HEIGHT = 1.8F, AEOLUS_WIDTH = 6.0F, AEOLUS_HEIGHT = 5.0F;
	/** (0.7.522) The lesser dragons ({@code docs/SaintsDragons-Plan.md}): Stegonaut's body. */
	static final float STEGONAUT_WIDTH = 2.8F, STEGONAUT_HEIGHT = 3.4F;
	/** (0.7.522) Stegonaut grazes in the jungles and plains (tag {@code has_stegonaut_dragon}): its spawn weight and herd. */
	static final int STEGONAUT_WEIGHT = 2, STEGONAUT_MIN = 1, STEGONAUT_MAX = 2;
	public static final TagKey<Biome> HAS_STEGONAUT = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_stegonaut_dragon"));
	/** (0.7.522) Atroxiia, the frost wyrm of the snowfields and cold coasts (tag {@code has_atroxiia_dragon}): its body, weight, herd. */
	static final float ATROXIIA_WIDTH = 3.0F, ATROXIIA_HEIGHT = 4.3F;
	static final int ATROXIIA_WEIGHT = 1, ATROXIIA_MIN = 1, ATROXIIA_MAX = 1;
	public static final TagKey<Biome> HAS_ATROXIIA = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_atroxiia_dragon"));
	/** (0.7.536) Whether Atroxiia is in the game: it was off until its walk was redone (it glided along with no steps); (0.7.612) back on. */
	public static final boolean ATROXIIA_ON = true;
	/** (0.7.522) Varasuchus, the crocodile dragon of the beaches and swamps (tag {@code has_varasuchus_dragon}): its body, weight, herd. */
	static final float VARASUCHUS_WIDTH = 4.0F, VARASUCHUS_HEIGHT = 3.3F;
	static final int VARASUCHUS_WEIGHT = 1, VARASUCHUS_MIN = 1, VARASUCHUS_MAX = 1;
	public static final TagKey<Biome> HAS_VARASUCHUS = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_varasuchus_dragon"));

	/** (0.7.522) Ignivorus, the Nether's fire dragon: its body; how often each player in the Nether is weighed for one (about once in
	 * three hours there), how far off it turns up, and none comes while one is this near already. */
	static final float IGNIVORUS_WIDTH = 4.4F, IGNIVORUS_HEIGHT = 3.7F;
	static final float IGNIVORUS_CHANCE = 1.0F / 360.0F;
	static final int IGNIVORUS_MIN = 40, IGNIVORUS_MAX = 64;
	static final double IGNIVORUS_CROWD = 160.0;

	/** (0.7.522) Aetherion, the star dragon over the Aether (Powers' {@code jaymospowers:aether}): its body, and how often each player
	 * there is weighed for one (about once in two hours), how far off it turns up, and none comes while one is this near already. */
	static final float AETHERION_WIDTH = 3.2F, AETHERION_HEIGHT = 3.8F;
	static final float AETHERION_CHANCE = 1.0F / 240.0F;
	static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "aether"));

	public static final TagKey<Biome> HAS_NOTUS = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_notus_dragon"));

	public static EntityType<NotusDragon> NOTUS_DRAGON;
	public static EntityType<BoreasDragon> BOREAS_DRAGON;
	public static EntityType<ZephyrusDragon> ZEPHYRUS_DRAGON;
	public static EntityType<EurusDragon> EURUS_DRAGON;
	public static EntityType<AeolusDragon> AEOLUS_DRAGON;
	public static EntityType<StegonautDragon> STEGONAUT_DRAGON;
	public static EntityType<AtroxiiaDragon> ATROXIIA_DRAGON;
	public static EntityType<VarasuchusDragon> VARASUCHUS_DRAGON;
	public static EntityType<IgnivorusDragon> IGNIVORUS_DRAGON;
	public static EntityType<AetherionDragon> AETHERION_DRAGON;
	public static Item NOTUS_DRAGON_SPAWN_EGG, BOREAS_DRAGON_SPAWN_EGG, ZEPHYRUS_DRAGON_SPAWN_EGG, EURUS_DRAGON_SPAWN_EGG, AEOLUS_DRAGON_SPAWN_EGG,
		STEGONAUT_DRAGON_SPAWN_EGG, ATROXIIA_DRAGON_SPAWN_EGG, VARASUCHUS_DRAGON_SPAWN_EGG, IGNIVORUS_DRAGON_SPAWN_EGG,
		AETHERION_DRAGON_SPAWN_EGG;
	/** By name, for the op command. */
	private static final Map<String, EntityType<? extends WindDragon>> BY_NAME = new LinkedHashMap<>();

	private Dragons() {
	}

	/** Client → server: the rider holds the breath key, looking along yaw/pitch (sent every other tick while held). */
	public record Breathe(float yaw, float pitch) implements CustomPacketPayload {
		public static final Type<Breathe> TYPE = new Type<>(JaymosKingdoms.id("dragon_breathe"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Breathe> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.yaw);
				buf.writeFloat(p.pitch);
			},
			buf -> new Breathe(buf.readFloat(), buf.readFloat()));

		@Override
		public Type<Breathe> type() {
			return TYPE;
		}
	}

	public static void register() {
		NOTUS_DRAGON = dragon("notus", NotusDragon::new, NOTUS_WIDTH, NOTUS_HEIGHT, true, NotusDragon.createAttributes());
		BOREAS_DRAGON = dragon("boreas", BoreasDragon::new, BOREAS_WIDTH, BOREAS_HEIGHT, false, BoreasDragon.createAttributes());
		ZEPHYRUS_DRAGON = dragon("zephyrus", ZephyrusDragon::new, ZEPHYRUS_WIDTH, ZEPHYRUS_HEIGHT, false, ZephyrusDragon.createAttributes());
		// (0.7.141) the two in disguise (Disguises): Eurus, the east wind, and Aeolus, keeper of the winds
		EURUS_DRAGON = dragon("eurus", EurusDragon::new, EURUS_WIDTH, EURUS_HEIGHT, false, EurusDragon.createAttributes());
		AEOLUS_DRAGON = dragon("aeolus", AeolusDragon::new, AEOLUS_WIDTH, AEOLUS_HEIGHT, true, AeolusDragon.createAttributes());
		NOTUS_DRAGON_SPAWN_EGG = egg(NOTUS_DRAGON);
		BOREAS_DRAGON_SPAWN_EGG = egg(BOREAS_DRAGON);
		ZEPHYRUS_DRAGON_SPAWN_EGG = egg(ZEPHYRUS_DRAGON);
		EURUS_DRAGON_SPAWN_EGG = egg(EURUS_DRAGON);
		AEOLUS_DRAGON_SPAWN_EGG = egg(AEOLUS_DRAGON);
		// (0.7.522) the lesser dragons, after Saint's Dragons (its code is MIT; the art is our own): Stegonaut, a walker that
		// grazes in the jungles and plains and spawns with the herds
		STEGONAUT_DRAGON = dragon("stegonaut", StegonautDragon::new, STEGONAUT_WIDTH, STEGONAUT_HEIGHT, false, StegonautDragon.createAttributes());
		STEGONAUT_DRAGON_SPAWN_EGG = egg(STEGONAUT_DRAGON);
		SpawnPlacements.register(STEGONAUT_DRAGON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_STEGONAUT), MobCategory.CREATURE, STEGONAUT_DRAGON, STEGONAUT_WEIGHT, STEGONAUT_MIN,
			STEGONAUT_MAX);
		// (0.7.522) Atroxiia and Varasuchus, walkers that swim: alone, on the snow and the cold shores, and the beaches and swamps
		// (any ground they can stand on: snow and sand aren't grass)
		ATROXIIA_DRAGON = dragon("atroxiia", AtroxiiaDragon::new, ATROXIIA_WIDTH, ATROXIIA_HEIGHT, false, AtroxiiaDragon.createAttributes());
		ATROXIIA_DRAGON_SPAWN_EGG = egg(ATROXIIA_DRAGON);
		SpawnPlacements.register(ATROXIIA_DRAGON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		// (0.7.536) Andy: Atroxiia is pulled until it has a proper walk (ATROXIIA_ON): no biome spawns, no op command, and
		// any already in a world are cleared away (AtroxiiaDragon.tick). Its type, model and art stay for when it's fixed.
		if (ATROXIIA_ON) {
			BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_ATROXIIA), MobCategory.CREATURE, ATROXIIA_DRAGON, ATROXIIA_WEIGHT, ATROXIIA_MIN,
				ATROXIIA_MAX);
		} else {
			BY_NAME.remove("atroxiia");
		}
		VARASUCHUS_DRAGON = dragon("varasuchus", VarasuchusDragon::new, VARASUCHUS_WIDTH, VARASUCHUS_HEIGHT, false, VarasuchusDragon.createAttributes());
		VARASUCHUS_DRAGON_SPAWN_EGG = egg(VARASUCHUS_DRAGON);
		SpawnPlacements.register(VARASUCHUS_DRAGON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_VARASUCHUS), MobCategory.CREATURE, VARASUCHUS_DRAGON, VARASUCHUS_WEIGHT, VARASUCHUS_MIN,
			VARASUCHUS_MAX);
		// (0.7.522) Ignivorus, the Nether's fire dragon on the Kintsugi wyvern: it turns up flying about the Nether (nether())
		IGNIVORUS_DRAGON = dragon("ignivorus", IgnivorusDragon::new, IGNIVORUS_WIDTH, IGNIVORUS_HEIGHT, true, IgnivorusDragon.createAttributes());
		IGNIVORUS_DRAGON_SPAWN_EGG = egg(IGNIVORUS_DRAGON);
		// (0.7.522) Aetherion, the star dragon: it turns up flying over the Aether (aether())
		AETHERION_DRAGON = dragon("aetherion", AetherionDragon::new, AETHERION_WIDTH, AETHERION_HEIGHT, false, AetherionDragon.createAttributes());
		AETHERION_DRAGON_SPAWN_EGG = egg(AETHERION_DRAGON);
		JaymosKingdoms.LOGGER.debug("dragon records: {} {}", DragonRecords.SLAIN, DragonRecords.TAMER);
		Disguises.register();
		DragonEggs.register();
		// (0.7.158) Andy: the dragons' spawn eggs are out of creative (tab and search); /give still has them

		PayloadTypeRegistry.serverboundPlay().register(Breathe.TYPE, Breathe.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Breathe.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.getVehicle() instanceof WindDragon dragon && dragon.isTamed() && dragon.getControllingPassenger() == player) {
				dragon.riderBreath(payload.yaw(), payload.pitch());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Dragons::nether);
		// testing (op): /kingdom dragon [notus|boreas|zephyrus|eurus|aeolus|stegonaut|varasuchus|ignivorus|aetherion] [tame]: a wild one (Notus unless named), or a
		// tamed, saddled one, at your side
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> dragon = Commands.literal("dragon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> spawn(c.getSource().getPlayerOrException(), NOTUS_DRAGON, false))
				.then(Commands.literal("tame").executes(c -> spawn(c.getSource().getPlayerOrException(), NOTUS_DRAGON, true)));
			BY_NAME.forEach((name, type) -> dragon.then(Commands.literal(name)
				.executes(c -> spawn(c.getSource().getPlayerOrException(), type, false))
				.then(Commands.literal("tame").executes(c -> spawn(c.getSource().getPlayerOrException(), type, true)))));
			dispatcher.register(Commands.literal("kingdom").then(dragon));
		});
	}

	private static <T extends WindDragon> EntityType<T> dragon(String name, EntityType.EntityFactory<T> factory, float width, float height,
		boolean fireImmune, AttributeSupplier.Builder attributes) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name + "_dragon"));
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, MobCategory.CREATURE).sized(width, height).clientTrackingRange(16);
		if (fireImmune) {
			builder = builder.fireImmune();
		}
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		FabricDefaultAttributeRegistry.register(type, attributes);
		BY_NAME.put(name, type);
		return type;
	}

	private static Item egg(EntityType<? extends WindDragon> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + "_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
	}

	private static int spawn(ServerPlayer player, EntityType<? extends WindDragon> type, boolean tame) {
		WindDragon dragon = type.create(player.level(), EntitySpawnReason.COMMAND);
		if (dragon == null) {
			return 0;
		}
		BlockPos at = player.blockPosition().relative(player.getDirection(), 5);
		dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		dragon.finalizeSpawn(player.level(), player.level().getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
		player.level().addFreshEntity(dragon);
		if (tame && dragon.tameable()) {
			dragon.tameWithName(player);
			DragonRecords.tamed(player);
			dragon.setPersistenceRequired();
			dragon.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		}
		return 1;
	}

	/** Over the basalt deltas (anywhere tagged {@code has_notus_dragon} in the Nether), now and then Notus finds a player. */
	private static void nether(MinecraftServer server) {
		if (server.getTickCount() % NETHER_CHECK_TICKS != 0) {
			return;
		}
		aether(server);
		// (0.7.528) the Nether's own level, or a One Dimension world's stacked band (Stacked)
		ServerLevel level = Stacked.level(server, Level.NETHER);
		if (level == null || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : Stacked.players(server, Level.NETHER)) {
			ignivorus(level, player, random);
			if (player.isCreative() || player.isSpectator() || !level.getBiome(player.blockPosition()).is(HAS_NOTUS)
				|| random.nextFloat() >= NETHER_CHANCE) {
				continue;
			}
			BlockPos spot = spot(level, player, random);
			if (spot == null) {
				continue;
			}
			NotusDragon dragon = NOTUS_DRAGON.create(level, EntitySpawnReason.EVENT);
			if (dragon == null) {
				continue;
			}
			dragon.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			if (!level.noCollision(dragon)) {
				continue;
			}
			level.addFreshEntity(dragon);
			dragon.setTarget(player);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.encounter.notus_dragon").withStyle(ChatFormatting.GOLD));
		}
	}

	/** (0.7.522) Over the Aether, now and then an Aetherion comes flying about some way off from a player (unless one's near already). */
	private static void aether(MinecraftServer server) {
		ServerLevel level = Stacked.level(server, AETHER);
		if (level == null || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : Stacked.players(server, AETHER)) {
			if (!player.isSpectator() && random.nextFloat() < AETHERION_CHANCE) {
				aloft(level, player, random, AETHERION_DRAGON, AetherionDragon.class);
			}
		}
	}

	/**
	 * (0.7.522) Anywhere in the Nether, now and then an Ignivorus comes flying about some way off from a player (unless
	 * one's near already). It leaves them be unless they hurt it.
	 */
	private static void ignivorus(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (player.isSpectator() || !level.getBiome(player.blockPosition()).is(BiomeTags.IS_NETHER) || random.nextFloat() >= IGNIVORUS_CHANCE) {
			return;
		}
		aloft(level, player, random, IGNIVORUS_DRAGON, IgnivorusDragon.class);
	}

	/** (0.7.522) A flier of {@code type} turned up in the air some way off from {@code player}, unless one's near already. */
	private static <T extends WindDragon> void aloft(ServerLevel level, ServerPlayer player, RandomSource random, EntityType<T> type, Class<T> kind) {
		if (!level.getEntitiesOfClass(kind, player.getBoundingBox().inflate(IGNIVORUS_CROWD)).isEmpty()) {
			return;
		}
		for (int i = 0; i < 16; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = IGNIVORUS_MIN + random.nextInt(IGNIVORUS_MAX - IGNIVORUS_MIN + 1);
			BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY() + 4 + random.nextInt(12),
				player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(at)) {
				continue;
			}
			T dragon = type.create(level, EntitySpawnReason.EVENT);
			if (dragon == null) {
				return;
			}
			dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(dragon) || level.containsAnyLiquid(dragon.getBoundingBox())) {
				continue;
			}
			dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			level.addFreshEntity(dragon);
			return;
		}
	}

	/** Open air over solid ground some way from the player, at about their height (under the Nether's roof). */
	private static @Nullable BlockPos spot(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = NETHER_MIN + random.nextInt(NETHER_MAX - NETHER_MIN + 1);
			BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY() + random.nextInt(9) - 2,
				player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(at)) {
				continue;
			}
			boolean room = true;
			for (int y = 0; y < 4 && room; y++) {
				room = level.getBlockState(at.above(y)).isAir();
			}
			if (room && level.getBlockState(at.below()).isSolid()) {
				return at;
			}
		}
		return null;
	}
}
