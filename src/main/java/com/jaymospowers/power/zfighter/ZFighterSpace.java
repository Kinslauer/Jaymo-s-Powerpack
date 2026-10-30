package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.chrono.future.Future;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.62, Andy's NOEA picks) The space pod: a craftable ship, placed like a block. Right-click it to open the star map and pick
 * where to fly: the Pale Moon (low gravity), Rustfall (a heavy red world), Glacia (a frozen sea), Cinder (a forge world of lava and
 * ore), the Maw (a black hole in a void, with wrecks to loot) and the Verdant World. (0.18.4, Andy) You ride the pod there
 * ({@link ZFighterPodTrip}): a countdown, a lift-off, a flight shown on screen, and a landing on open ground anywhere on the far
 * world; the pod you came in stands there for the way back. A pod holds fuel ({@link #FUEL}, coal or blaze powder used on it) and
 * each trip burns some by distance ({@link #COSTS}). The pads ({@link #PAD}) are now only the life-story starts' ports.
 * (0.18.47) The pod is now an entity built from parts ({@link PodEntity}); the old pod block stays registered so saved worlds load,
 * and becomes a complete pod, holding its fuel, the first time anyone uses it.
 * <p>
 * Where you flew from is kept ({@link #HOME}) when it was not one of the far places, so "Home" on any pod brings you down where you
 * left from (or the world spawn if that world is gone). Gravity is an attribute modifier (the moon light, Rustfall heavy); Z Fighters
 * train {@link #RUSTFALL_TRAINING} times faster in Rustfall. The planet missions are in {@link ZFighterSpaceMissions}.
 */
public final class ZFighterSpace {
	// ---- tuning ----
	/** The wait after a landing before another launch (ticks). The countdown, lift, flight and landing are in {@link ZFighterPodTrip}. */
	static final int COOLDOWN_TICKS = 100;
	/** How far from the pod a launch may start (blocks), and how long the open star map stays good (ticks). */
	static final double POD_REACH = 10.0;
	static final int OPEN_TICKS = 1200;
	/** (0.18.6) The fuel a pod holds at most, and what each trip burns, by destination index (Home burns what the world you are on costs). */
	public static final int MAX_FUEL = 12;
	public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, MAX_FUEL);
	public static final int[] COSTS = {1, 3, 3, 4, 6, 2, 0};
	/** What Home burns from anywhere that isn't a far world (the overworld, the Nether...). */
	public static final int HOME_COST = 1;
	/** (0.18.47) The Maw wrecks' pods: how many of their parts (besides the keel and fins) are there, and how many of those are damaged. */
	static final int WRECK_POD_EXTRA_MIN = 2, WRECK_POD_EXTRA_MAX = 5, WRECK_POD_DAMAGED_MIN = 1, WRECK_POD_DAMAGED_MAX = 3;
	/** The landing pad's radius, and how far (blocks) the search for a flat place widens each try; how many tries. */
	static final int PAD_RADIUS = 3, SEARCH_STEP = 28, SEARCH_TRIES = 36;
	/** How much the surface may rise and fall across a pad before the spot is passed over. */
	static final int PAD_SLOPE = 4;
	/** The moon's gravity (share of normal) and the extra fall it forgives; Rustfall's gravity, slowing and jump. */
	public static final double MOON_GRAVITY = 0.35, MOON_SAFE_FALL = 8.0;
	public static final double RUSTFALL_GRAVITY = 2.2, RUSTFALL_SPEED = -0.15, RUSTFALL_JUMP = -0.2;
	/** Z Fighters train this many times faster under Rustfall's weight. */
	public static final float RUSTFALL_TRAINING = 2.0F;
	/** Cinder's heat: extra hunger every second. */
	static final float CINDER_EXHAUSTION = 0.15F;
	/** The Maw: the hole's height, radius, where the pad is, how far its pull reaches, and the pull far out and at the rim. */
	static final int MAW_Y = 80, MAW_HOLE = 5, MAW_RING_IN = 10, MAW_RING_OUT = 13;
	static final int MAW_PAD_X = -44;
	static final double MAW_PULL_RANGE = 90.0, MAW_PULL_FAR = 0.008, MAW_PULL_NEAR = 0.03;
	/** Inside this many blocks of the hole's middle the tide throws you out and hurts you ({@link #MAW_TIDE_DAMAGE} every half second). */
	static final double MAW_TIDE_RANGE = 9.0, MAW_TIDE_PUSH = 1.3;
	static final float MAW_TIDE_DAMAGE = 6.0F;
	/** The wrecks round the hole: how many, how far out (min, max), and how high above or below the hole's middle. */
	static final int MAW_WRECKS = 9, MAW_WRECK_MIN = 22, MAW_WRECK_MAX = 54, MAW_WRECK_RISE = 14;
	static final long MAW_SEED = 0x4D4157L;

	/** The star map's commands. */
	public static final int GO = 0, BEGIN_MISSION = 1, ABANDON_MISSION = 2, SKIP = 3;
	/** Home, as a destination index. */
	public static final int HOME_INDEX = -1;

	/** What a place is made of, which sets how a landing is found. */
	public enum Kind {
		SURFACE, FORGE, VOID, FUTURE
	}

	/** A place the pod flies to: its key (lang), its dimension, how a landing is found, the pad's floor and light blocks. */
	public record Dest(String key, ResourceKey<Level> level, Kind kind, Block floor, Block light) {
	}

	public static final int MOON = 0, RUSTFALL = 1, GLACIA = 2, CINDER = 3, MAW = 4, VERDANT = 5, FUTURE = 6;

	private static ResourceKey<Level> dimension(String name) {
		return ResourceKey.create(Registries.DIMENSION, JaymosPowers.id(name));
	}

	public static final Dest[] DESTS = {
		new Dest("moon", dimension("moon"), Kind.SURFACE, Blocks.POLISHED_ANDESITE, Blocks.SEA_LANTERN),
		new Dest("rustfall", dimension("rustfall"), Kind.SURFACE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN),
		new Dest("glacia", dimension("glacia"), Kind.SURFACE, Blocks.QUARTZ_BRICKS, Blocks.SEA_LANTERN),
		new Dest("cinder", dimension("cinder"), Kind.FORGE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SHROOMLIGHT),
		new Dest("maw", dimension("maw"), Kind.VOID, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN),
		new Dest("verdant", ZFighterSites.VERDANT_WORLD, Kind.SURFACE, Blocks.MOSSY_STONE_BRICKS, Blocks.SEA_LANTERN),
		new Dest("future", Future.FUTURE, Kind.FUTURE, Blocks.STONE_BRICKS, Blocks.SEA_LANTERN),
	};

	/** Where a player came from before the far places, kept through death: the dimension and the pod he used. */
	public record Home(String dimension, BlockPos pos) {
		public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Home::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Home::pos)).apply(i, Home::new));
	}

	public static final AttachmentType<Home> HOME = AttachmentRegistry.create(JaymosPowers.id("zfighter_space_home"),
		builder -> builder.persistent(Home.CODEC).copyOnDeath());
	/** A place's landing pad (where the pod stands), saved on the place. */
	public static final AttachmentType<BlockPos> PAD = AttachmentRegistry.create(JaymosPowers.id("zfighter_space_pad"),
		builder -> builder.persistent(BlockPos.CODEC));

	/** Server → client: open the star map, for a pod holding {@code fuel} of grade {@code grade} (0.18.49: the tank's grade). */
	public record Open(int fuel, int grade) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("zfighter_space_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.fuel);
			buf.writeVarInt(p.grade);
		}, buf -> new Open(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: a star map button ({@link #GO} to a place or home, begin or give up the mission here). */
	public record Command(int action, int arg) implements CustomPacketPayload {
		public static final Type<Command> TYPE = new Type<>(JaymosPowers.id("zfighter_space_command"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Command> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.arg);
			},
			buf -> new Command(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Command> type() {
			return TYPE;
		}
	}

	public static Block POD;
	public static Item POD_ITEM;

	private static final Identifier GRAVITY_ID = JaymosPowers.id("space_gravity");
	private static final Identifier SAFE_FALL_ID = JaymosPowers.id("space_safe_fall");
	private static final Identifier SPEED_ID = JaymosPowers.id("space_speed");
	private static final Identifier JUMP_ID = JaymosPowers.id("space_jump");
	private static final ResourceKey<LootTable> WRECK_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("chests/maw_wreck"));

	/** A star map a player has open: the pod (its entity's id) and when. */
	private record Opened(UUID pod, long at) {
	}

	private static final Map<UUID, Opened> OPENED = new HashMap<>();
	private static final Map<UUID, Long> NEXT_LAUNCH = new HashMap<>();

	private ZFighterSpace() {
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("space_pod"));
		POD = Registry.register(BuiltInRegistries.BLOCK, key, new PodBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()
			.lightLevel(state -> 6).sound(SoundType.COPPER).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("space_pod"));
		POD_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(POD, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
			.rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(POD_ITEM));
		PodEntity.init();
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Command.TYPE, Command.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Command.TYPE, (payload, ctx) -> command(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSpace::tick);
		ZFighterSpaceMissions.init();
		ZFighterPodTrip.init();
	}

	// ---------------------------------------------------------------- the pod

	/** The old pod block (0.18.47: kept so saved worlds load): right-click and it becomes a pod entity; coal or blaze powder still fuels it. */
	static final class PodBlock extends Block {
		private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(Block.box(3, 0, 3, 13, 9, 13), Block.box(4, 9, 4, 12, 13, 12),
			Block.box(6, 13, 6, 10, 16, 10), Block.box(1, 0, 7, 3, 5, 9), Block.box(13, 0, 7, 15, 5, 9));

		PodBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FUEL, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FUEL);
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			int worth = fuelWorth(stack);
			if (worth <= 0) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			int fuel = state.getValue(FUEL);
			if (fuel >= MAX_FUEL) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_fuel_full", MAX_FUEL).withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				int now = Math.min(MAX_FUEL, fuel + worth);
				level.setBlock(pos, state.setValue(FUEL, now), Block.UPDATE_ALL);
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_fuel", now, MAX_FUEL).withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer server) {
				// (0.18.47) an old pod block becomes a complete pod standing where it was, with its fuel
				serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				open(server, PodEntity.spawn(serverLevel, Vec3.atBottomCenterOf(pos), true, state.getValue(FUEL)));
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** (0.18.47) Opens the star map for {@code player} at {@code pod}. */
	public static void open(ServerPlayer player, PodEntity pod) {
		Level level = player.level();
		OPENED.put(player.getUUID(), new Opened(pod.getUUID(), level.getGameTime()));
		ServerPlayNetworking.send(player, new Open(pod.fuel(), PodFuel.effective(pod)));
		level.playSound(null, pod.getX(), pod.getY() + 1.0, pod.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.6F);
	}

	/** How much fuel one of {@code stack} puts in a pod (0: none). (0.18.49) The numbers and grades are {@link PodFuel}'s. */
	public static int fuelWorth(ItemStack stack) {
		return PodFuel.worth(stack);
	}

	/** What a trip to {@code index} (or Home) burns from {@code from}. */
	public static int cost(int index, ResourceKey<Level> from) {
		if (index != HOME_INDEX) {
			return index >= 0 && index < COSTS.length ? COSTS[index] : HOME_COST;
		}
		for (int i = 0; i < DESTS.length; i++) {
			if (DESTS[i].level() == from) {
				return Math.max(HOME_COST, COSTS[i]);
			}
		}
		return HOME_COST;
	}

	// ---------------------------------------------------------------- what the places are

	public static @Nullable Dest here(Level level) {
		for (Dest dest : DESTS) {
			if (dest.level() == level.dimension()) {
				return dest;
			}
		}
		return null;
	}

	/** Whether {@code level} is one of the five places the pod made new (not the Verdant World or the future). */
	static boolean far(ResourceKey<Level> level) {
		for (int i = MOON; i <= MAW; i++) {
			if (DESTS[i].level() == level) {
				return true;
			}
		}
		return false;
	}

	/** The share of training Rustfall's weight gives a Z Fighter. */
	public static float trainingRate(ServerPlayer player) {
		return player.level().dimension() == DESTS[RUSTFALL].level() ? RUSTFALL_TRAINING : 1.0F;
	}

	// ---------------------------------------------------------------- the star map's commands

	private static void command(ServerPlayer player, Command command) {
		switch (command.action()) {
			case GO -> go(player, command.arg());
			case BEGIN_MISSION -> ZFighterSpaceMissions.begin(player);
			case ABANDON_MISSION -> ZFighterSpaceMissions.abandon(player);
			case SKIP -> ZFighterPodTrip.skip(player);
			default -> {
			}
		}
	}

	private static void go(ServerPlayer player, int index) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		if (ZFighterPodTrip.flying(player)) {
			return;
		}
		Opened opened = OPENED.get(id);
		long now = player.level().getGameTime();
		PodEntity pod = opened != null && player.level().getEntity(opened.pod()) instanceof PodEntity found && found.isAlive() && !found.locked() ? found : null;
		if (pod == null || now - opened.at() > OPEN_TICKS || player.distanceToSqr(pod) > POD_REACH * POD_REACH) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_no_pod").withStyle(ChatFormatting.GRAY));
			return;
		}
		Long next = NEXT_LAUNCH.get(id);
		if (next != null && now < next) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_cooldown", (next - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (player.level().dimension() == DESTS[FUTURE].level()) {
			// (0.18.4, Andy) a lifetime on, every world the pod knows is dust: only going back in time leads home
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_future_dead").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (index == HOME_INDEX) {
			// nothing to do if he is already where home is: the overworld with no trip taken
		} else if (index == FUTURE) {
			// (0.18.6) the future is off the star map
			return;
		} else if (index < 0 || index >= DESTS.length || DESTS[index].level() == player.level().dimension()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_here").withStyle(ChatFormatting.GRAY));
			return;
		} else if (server.getLevel(DESTS[index].level()) == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_unreachable").withStyle(ChatFormatting.RED));
			return;
		}
		PodPart missing = pod.missingForLaunch();
		if (missing != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.missing", Component.translatable("block.jaymospowers.pod_" + missing.key))
				.withStyle(ChatFormatting.RED));
			return;
		}
		int fuel = pod.fuel();
		int cost = player.isCreative() ? 0 : cost(index, player.level().dimension());
		if (fuel < cost) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_no_fuel", cost, fuel).withStyle(ChatFormatting.RED));
			return;
		}
		// (0.18.49) the tank's fuel must be fine enough for the world
		int need = PodFuel.needed(index);
		int grade = PodFuel.effective(pod);
		if (!player.isCreative() && grade < need) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_grade", PodFuel.roman(need), PodFuel.roman(grade))
				.withStyle(ChatFormatting.RED));
			return;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		ResourceKey<Level> from = player.level().dimension();
		if (!far(from)) {
			player.setAttached(HOME, new Home(from.identifier().toString(), pod.blockPosition()));
		}
		OPENED.remove(id);
		ZFighterPodTrip.start(player, index, pod, fuel - cost, fuel);
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		ZFighterPodTrip.tick(server);
		if (now % 10 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				weigh(player);
			}
		}
		ServerLevel maw = server.getLevel(DESTS[MAW].level());
		if (maw != null && !maw.players().isEmpty()) {
			pull(maw);
		}
		ServerLevel cinder = server.getLevel(DESTS[CINDER].level());
		if (cinder != null && now % 20 == 0) {
			for (ServerPlayer player : cinder.players()) {
				if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
					player.causeFoodExhaustion(CINDER_EXHAUSTION);
				}
			}
		}
	}

	// ---------------------------------------------------------------- gravity

	/** Gravity and weight for where {@code player} is: the moon light, Rustfall heavy, everywhere else nothing. */
	private static void weigh(ServerPlayer player) {
		ResourceKey<Level> where = player.level().dimension();
		boolean moon = where == DESTS[MOON].level();
		boolean rustfall = where == DESTS[RUSTFALL].level();
		set(player, Attributes.GRAVITY, GRAVITY_ID, moon ? MOON_GRAVITY - 1.0 : rustfall ? RUSTFALL_GRAVITY - 1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, moon ? MOON_SAFE_FALL : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, rustfall ? RUSTFALL_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.JUMP_STRENGTH, JUMP_ID, rustfall ? RUSTFALL_JUMP : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount != 0.0) {
			AttributeModifier current = instance.getModifier(id);
			if (current == null || current.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
			}
		} else if (instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- the black hole

	private static BlockState vanilla(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static Vec3 holeMiddle() {
		return new Vec3(0.5, MAW_Y + 2.5, 0.5);
	}

	/** The Maw's slow pull on everyone in it, and the tide at its rim. */
	private static void pull(ServerLevel maw) {
		Vec3 middle = holeMiddle();
		for (ServerPlayer player : maw.players()) {
			if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || ZFighterPodTrip.flying(player)) {
				continue;
			}
			Vec3 to = middle.subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
			double distance = to.length();
			if (distance > MAW_PULL_RANGE || distance < 0.01) {
				continue;
			}
			Vec3 direction = to.scale(1.0 / distance);
			if (distance < MAW_TIDE_RANGE) {
				// the tide: thrown out, hurt
				player.setDeltaMovement(direction.scale(-MAW_TIDE_PUSH).add(0.0, 0.25, 0.0));
				player.syncVelocity = true;
				if (maw.getGameTime() % 10 == 0 && !player.isCreative()) {
					player.hurtServer(maw, maw.damageSources().magic(), MAW_TIDE_DAMAGE);
					maw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.5F);
					Shakes.send(player, 0.9F, middle);
				}
				continue;
			}
			double share = 1.0 - distance / MAW_PULL_RANGE;
			double pull = MAW_PULL_FAR + (MAW_PULL_NEAR - MAW_PULL_FAR) * share * share;
			player.setDeltaMovement(player.getDeltaMovement().add(direction.scale(pull)));
			player.syncVelocity = true;
			if (maw.getGameTime() % 40 == 0) {
				Shakes.send(player, 0.08F + (float)share * 0.2F, middle);
			}
		}
	}

	/** The hole itself, the glowing ring round it, and the wrecks to loot: built the first time anyone lands, if not there. */
	static void buildMaw(ServerLevel level) {
		BlockPos middle = BlockPos.containing(holeMiddle());
		if (level.getBlockState(middle).is(vanilla("black_concrete").getBlock())) {
			return;
		}
		for (int dx = -MAW_RING_OUT; dx <= MAW_RING_OUT; dx++) {
			for (int dy = -MAW_HOLE; dy <= MAW_HOLE; dy++) {
				for (int dz = -MAW_RING_OUT; dz <= MAW_RING_OUT; dz++) {
					double flat = Math.sqrt(dx * dx + dz * dz);
					double round = Math.sqrt(dx * dx + dy * dy + dz * dz);
					BlockPos at = middle.offset(dx, dy, dz);
					if (round <= MAW_HOLE) {
						level.setBlock(at, vanilla("black_concrete"), Block.UPDATE_CLIENTS);
					} else if (dy == 0 && flat >= MAW_RING_IN && flat <= MAW_RING_OUT && (dx + dz) % 2 == 0) {
						level.setBlock(at, vanilla(flat > MAW_RING_IN + 1 ? "orange_stained_glass" : "yellow_stained_glass"), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		RandomSource random = RandomSource.create(MAW_SEED);
		wrecks(random, centre -> wreck(level, centre, random));
	}

	/** The wrecks' middles, the same every time ({@link #MAW_SEED}), each handed to {@code each} with the random still in step. */
	private static void wrecks(RandomSource random, java.util.function.Consumer<BlockPos> each) {
		for (int i = 0; i < MAW_WRECKS; i++) {
			double angle = Math.PI * 2.0 * i / MAW_WRECKS + random.nextDouble() * 0.5;
			double distance = MAW_WRECK_MIN + random.nextDouble() * (MAW_WRECK_MAX - MAW_WRECK_MIN);
			BlockPos centre = new BlockPos((int)Math.round(Math.cos(angle) * distance), MAW_Y + random.nextInt(MAW_WRECK_RISE * 2 + 1) - MAW_WRECK_RISE,
				(int)Math.round(Math.sin(angle) * distance));
			if (Math.abs(centre.getX() - MAW_PAD_X) < 12 && Math.abs(centre.getZ()) < 12) {
				continue;
			}
			each.accept(centre);
		}
	}

	/** (0.18.6) Where a pod may come down in the Maw: on a wreck's deck, clear of its chest, lantern, mast and (0.18.47) wrecked pod. */
	static List<BlockPos> mawDecks() {
		List<BlockPos> decks = new ArrayList<>();
		RandomSource random = RandomSource.create(MAW_SEED);
		// the same draws as building them, so the middles match: each wreck takes its own random draws after its middle
		wrecks(random, centre -> {
			decks.add(centre.offset(1, 1, -2));
			wreckDraws(random);
		});
		if (decks.isEmpty()) {
			decks.add(new BlockPos(MAW_PAD_X, MAW_Y, 0));
		}
		return decks;
	}

	/** The random draws {@link #wreck} makes, made without building (keep the two in step). */
	private static void wreckDraws(RandomSource random) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz > 10 || (Math.abs(dx) == 3 && random.nextInt(2) == 0)) {
					continue;
				}
				random.nextInt(4);
			}
		}
		// the chest's loot seed
		random.nextLong();
	}

	/** One wreck: a ragged floating deck of blackstone with a chest of the Maw's loot, a lantern and a broken mast. */
	private static void wreck(ServerLevel level, BlockPos centre, RandomSource random) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz > 10 || (Math.abs(dx) == 3 && random.nextInt(2) == 0)) {
					continue;
				}
				level.setBlock(centre.offset(dx, 0, dz), (random.nextInt(4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS)
					.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					level.setBlock(centre.offset(dx, -1, dz), Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		BlockPos chest = centre.above();
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		RandomizableContainer.setBlockEntityLootTable(level, random, chest, WRECK_LOOT);
		level.setBlock(centre.offset(2, 1, 1), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (int up = 1; up <= 3; up++) {
			level.setBlock(centre.offset(-2, up, -1), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		// (0.18.47) and a pod that came down with the ship: spare parts, some of them broken (its own random: the wrecks' draws stay in step)
		wreckedPod(level, Vec3.atBottomCenterOf(centre.offset(-1, 1, 2)), RandomSource.create(MAW_SEED ^ centre.asLong()));
	}

	/**
	 * (0.18.47) A wrecked pod at {@code at}: the keel and the three fins, {@link #WRECK_POD_EXTRA_MIN} to {@link #WRECK_POD_EXTRA_MAX}
	 * of the other parts at random, and {@link #WRECK_POD_DAMAGED_MIN} to {@link #WRECK_POD_DAMAGED_MAX} of its parts damaged.
	 * No fuel. The Maw is where spare parts come from.
	 */
	static PodEntity wreckedPod(ServerLevel level, Vec3 at, RandomSource random) {
		List<PodPart> pool = new ArrayList<>(List.of(PodPart.BELL, PodPart.TANK, PodPart.CANOPY, PodPart.MAST));
		for (int i = 0; i < PodPart.PLATE.count; i++) {
			pool.add(PodPart.PLATE);
		}
		int filled = PodPart.KEEL.bits() | PodPart.FIN.bits();
		int[] used = new int[PodPart.values().length];
		int extra = WRECK_POD_EXTRA_MIN + random.nextInt(WRECK_POD_EXTRA_MAX - WRECK_POD_EXTRA_MIN + 1);
		for (int i = 0; i < extra && !pool.isEmpty(); i++) {
			PodPart part = pool.remove(random.nextInt(pool.size()));
			filled |= part.bit(used[part.ordinal()]++);
		}
		List<Integer> soundBits = new ArrayList<>();
		for (int bit = 1; bit != 0 && bit <= PodPart.ALL_SLOTS; bit <<= 1) {
			if ((filled & bit) != 0 && (PodPart.KEEL.bits() & bit) == 0) {
				soundBits.add(bit);
			}
		}
		int damaged = 0;
		int count = WRECK_POD_DAMAGED_MIN + random.nextInt(WRECK_POD_DAMAGED_MAX - WRECK_POD_DAMAGED_MIN + 1);
		for (int i = 0; i < count && !soundBits.isEmpty(); i++) {
			damaged |= soundBits.remove(random.nextInt(soundBits.size()));
		}
		PodEntity pod = PodEntity.spawn(level, at, false, 0);
		pod.setParts(filled, damaged);
		pod.setYRot(random.nextFloat() * 360.0F);
		return pod;
	}

	// ---------------------------------------------------------------- arriving

	/** Down on {@code dest} (null: home): told where, weighed for the place, and any mission there spoken of. */
	static void arrived(ServerPlayer player, @Nullable Dest dest) {
		player.sendOverlayMessage(Component.translatable(dest == null ? "message.jaymospowers.space_home" : "message.jaymospowers.space_landed",
			Component.translatable("space.jaymospowers.dest." + (dest == null ? "home" : dest.key()))).withStyle(ChatFormatting.AQUA));
		weigh(player);
		NEXT_LAUNCH.put(player.getUUID(), player.level().getGameTime() + COOLDOWN_TICKS);
		ZFighterSpaceMissions.arrived(player);
		ZFighterPatrol.arrived(player, dest);
	}

	// ---------------------------------------------------------------- the landing pad

	/** The place's pad: where its pod stands. Found and built the first time (or again, if the pod is gone), then kept. */
	static BlockPos pad(ServerLevel level, Dest dest) {
		BlockPos saved = level.getAttached(PAD);
		if (saved != null) {
			level.getChunk(saved.getX() >> 4, saved.getZ() >> 4);
			if (level.getBlockState(saved).is(POD) || !level.getEntitiesOfClass(PodEntity.class, new AABB(saved).inflate(PAD_RADIUS)).isEmpty()) {
				return saved;
			}
		}
		BlockPos spot = saved != null ? saved : dest.kind() == Kind.VOID ? new BlockPos(MAW_PAD_X, MAW_Y, 0) : find(level, dest);
		build(level, dest, spot);
		level.setAttached(PAD, spot);
		if (dest.kind() == Kind.VOID) {
			buildMaw(level);
		}
		return spot;
	}

	/** A flat place of this world to stand: the surface, or a floor in the forge's caves. Falls back to the middle of the map. */
	private static BlockPos find(ServerLevel level, Dest dest) {
		for (int i = 0; i < SEARCH_TRIES; i++) {
			double angle = i * 2.399963;
			double distance = i == 0 ? 0.0 : SEARCH_STEP * (1.0 + i / 6.0);
			int x = (int)Math.round(Math.cos(angle) * distance);
			int z = (int)Math.round(Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			BlockPos spot = dest.kind() == Kind.FORGE ? forgeFloor(level, x, z) : surface(level, x, z);
			if (spot != null) {
				return spot;
			}
		}
		level.getChunk(0, 0);
		if (dest.kind() == Kind.FORGE) {
			return new BlockPos(0, 80, 0);
		}
		return new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
	}

	/** A flat spot of open ground at column (x, z) for a pad (no water under it, no steep slope across it), or null. */
	static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int y = 0;
		for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx += PAD_RADIUS) {
			for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz += PAD_RADIUS) {
				int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
				BlockState below = level.getBlockState(new BlockPos(x + dx, height - 1, z + dz));
				if (!below.getFluidState().isEmpty() || height <= level.getMinY() + 4) {
					return null;
				}
				low = Math.min(low, height);
				high = Math.max(high, height);
				if (dx == 0 && dz == 0) {
					y = height;
				}
			}
		}
		return high - low <= PAD_SLOPE ? new BlockPos(x, y, z) : null;
	}

	static @Nullable BlockPos forgeFloor(ServerLevel level, int x, int z) {
		for (int y = 116; y > 36; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			BlockState floor = level.getBlockState(feet.below());
			if (floor.isSolidRender() && floor.getFluidState().isEmpty() && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
				&& level.getBlockState(feet.above(2)).isAir() && noLava(level, feet)) {
				return feet;
			}
		}
		return null;
	}

	private static boolean noLava(ServerLevel level, BlockPos feet) {
		for (int dx = -PAD_RADIUS - 1; dx <= PAD_RADIUS + 1; dx++) {
			for (int dz = -PAD_RADIUS - 1; dz <= PAD_RADIUS + 1; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					if (!level.getBlockState(feet.offset(dx, dy, dz)).getFluidState().isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** The pad: a floor of {@link Dest#floor}, a lamp at each corner, the air above cleared, and a pod (a complete one, no fuel) in the middle. */
	private static void build(ServerLevel level, Dest dest, BlockPos spot) {
		for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
			for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
				boolean corner = Math.abs(dx) == PAD_RADIUS && Math.abs(dz) == PAD_RADIUS;
				BlockPos floor = spot.offset(dx, -1, dz);
				level.setBlock(floor, (corner ? dest.light() : dest.floor()).defaultBlockState(), Block.UPDATE_CLIENTS);
				BlockPos under = floor.below();
				BlockState underState = level.getBlockState(under);
				if (dest.kind() != Kind.VOID && (underState.isAir() || !underState.getFluidState().isEmpty())) {
					level.setBlock(under, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int up = 0; up <= 4; up++) {
					BlockPos at = spot.offset(dx, up, dz);
					if (!level.getBlockState(at).isAir() && !level.getBlockState(at).is(Blocks.BEDROCK)) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		PodEntity.spawn(level, Vec3.atBottomCenterOf(spot), true, 0);
	}
}
