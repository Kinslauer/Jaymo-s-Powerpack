package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.chrono.future.Future;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.62, Andy's NOEA picks) The space pod: a craftable ship, placed like a block. Right-click it to open the star map and pick
 * where to fly: the Pale Moon (low gravity), Rustfall (a heavy red world), Glacia (a frozen sea), Cinder (a forge world of lava and
 * ore), the Maw (a black hole in a void, with wrecks to loot), the Verdant World and the future. The flight is a countdown, a lift
 * off, and on the far side a controlled descent onto a landing pad built where the world allows it, with a pod standing on it for
 * the way back. The pad of each place is saved on the place ({@link #PAD}) so every landing comes down at the same pad.
 * <p>
 * Where you flew from is kept ({@link #HOME}) when it was not one of the far places, so "Home" on any pod brings you down by the pod
 * you left from (or the world spawn if it is gone). Gravity is an attribute modifier (the moon light, Rustfall heavy); Z Fighters
 * train {@link #RUSTFALL_TRAINING} times faster in Rustfall. The planet missions are in {@link ZFighterSpaceMissions}.
 */
public final class ZFighterSpace {
	// ---- tuning ----
	/** The countdown before the lift (ticks), the lift (ticks, its speed in blocks a tick) and the wait before another launch. */
	static final int COUNTDOWN_TICKS = 60, LIFT_TICKS = 30, COOLDOWN_TICKS = 100;
	static final double LIFT_SPEED = 0.8;
	/** How far from the pod a launch may start and carry on (blocks), and how long the open star map stays good (ticks). */
	static final double POD_REACH = 10.0;
	static final int OPEN_TICKS = 1200;
	/** The landing: how high the descent starts (at most), how fast it falls (blocks a tick), and how long it may last (ticks). */
	static final int DESCENT_HEIGHT = 30, DESCENT_MIN = 4, DESCENT_TICKS = 220;
	static final double DESCENT_SPEED = 0.3;
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
	public static final int GO = 0, BEGIN_MISSION = 1, ABANDON_MISSION = 2;
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

	/** Server → client: open the star map. */
	public record Open() implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("zfighter_space_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

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

	/** A star map a player has open: the pod and when. */
	private record Opened(BlockPos pod, long at) {
	}

	/** A launch under way: where to, the pod, and ticks left (the countdown and then the lift). */
	private static final class Launch {
		final int dest;
		final BlockPos pod;
		/** Where he stood when the launch began (the lift carries him off it). */
		final Vec3 origin;
		int left = COUNTDOWN_TICKS + LIFT_TICKS;

		Launch(int dest, BlockPos pod, Vec3 origin) {
			this.dest = dest;
			this.pod = pod;
			this.origin = origin;
		}
	}

	/** A descent under way: the dimension, the pad's height, when it began and the game time it gives up at. */
	private record Landing(ResourceKey<Level> level, int padY, long since, long until) {
	}

	private static final Map<UUID, Opened> OPENED = new HashMap<>();
	private static final Map<UUID, Launch> LAUNCHES = new HashMap<>();
	private static final Map<UUID, Landing> LANDINGS = new HashMap<>();
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
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Command.TYPE, Command.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Command.TYPE, (payload, ctx) -> command(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSpace::tick);
		ZFighterSpaceMissions.init();
	}

	// ---------------------------------------------------------------- the pod

	/** The pod: right-click for the star map. */
	static final class PodBlock extends Block {
		private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(Block.box(3, 0, 3, 13, 9, 13), Block.box(4, 9, 4, 12, 13, 12),
			Block.box(6, 13, 6, 10, 16, 10), Block.box(1, 0, 7, 3, 5, 9), Block.box(13, 0, 7, 15, 5, 9));

		PodBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer server) {
				OPENED.put(server.getUUID(), new Opened(pos, level.getGameTime()));
				ServerPlayNetworking.send(server, new Open());
				level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
			}
			return InteractionResult.SUCCESS;
		}
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
			default -> {
			}
		}
	}

	private static void go(ServerPlayer player, int index) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		if (LAUNCHES.containsKey(id)) {
			return;
		}
		Opened opened = OPENED.get(id);
		long now = player.level().getGameTime();
		if (opened == null || now - opened.at() > OPEN_TICKS || !player.level().getBlockState(opened.pod()).is(POD)
			|| player.distanceToSqr(Vec3.atCenterOf(opened.pod())) > POD_REACH * POD_REACH) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_no_pod").withStyle(ChatFormatting.GRAY));
			return;
		}
		Long next = NEXT_LAUNCH.get(id);
		if (next != null && now < next) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_cooldown", (next - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean inFuture = player.level().dimension() == DESTS[FUTURE].level();
		if (index == HOME_INDEX) {
			// nothing to do if he is already where home is: the overworld with no trip taken
		} else if (index == FUTURE) {
			// (0.18.2) the future is off the star map; only the pod already in the future still flies (home)
			return;
		} else if (index < 0 || index >= DESTS.length || DESTS[index].level() == player.level().dimension()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_here").withStyle(ChatFormatting.GRAY));
			return;
		} else if (inFuture) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_future_only_home").withStyle(ChatFormatting.GRAY));
			return;
		} else if (server.getLevel(DESTS[index].level()) == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_unreachable").withStyle(ChatFormatting.RED));
			return;
		}
		Launch launch = new Launch(index, opened.pod(), player.position());
		LAUNCHES.put(id, launch);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_countdown", COUNTDOWN_TICKS / 20).withStyle(ChatFormatting.GOLD));
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (!LAUNCHES.isEmpty()) {
			launches(server);
		}
		if (!LANDINGS.isEmpty()) {
			landings(server);
		}
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

	private static void launches(MinecraftServer server) {
		List<UUID> done = new ArrayList<>();
		for (Map.Entry<UUID, Launch> entry : new ArrayList<>(LAUNCHES.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Launch launch = entry.getValue();
			if (player == null || !player.isAlive() || player.isSpectator()) {
				done.add(entry.getKey());
				continue;
			}
			ServerLevel level = player.level();
			Vec3 pod = Vec3.atBottomCenterOf(launch.pod);
			if (launch.left > LIFT_TICKS) {
				if (!level.getBlockState(launch.pod).is(POD) || player.distanceToSqr(pod) > (POD_REACH + 4.0) * (POD_REACH + 4.0)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_aborted").withStyle(ChatFormatting.RED));
					done.add(entry.getKey());
					continue;
				}
				int count = launch.left - LIFT_TICKS;
				if (count % 20 == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_count", count / 20).withStyle(ChatFormatting.GOLD));
					level.playSound(null, pod.x, pod.y, pod.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 0.8F + 0.2F * (3 - count / 20));
				}
				level.sendParticles(ParticleTypes.SMOKE, pod.x, pod.y + 0.2, pod.z, 4, 0.5, 0.1, 0.5, 0.02);
				if (launch.left % 8 == 0) {
					Shakes.send(player, 0.25F, pod);
				}
			} else {
				// the lift: straight up on the pod's fire
				player.setDeltaMovement(0.0, LIFT_SPEED, 0.0);
				player.syncVelocity = true;
				player.resetFallDistance();
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 8, 0.25, 0.1, 0.25, 0.04);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() - 0.3, player.getZ(), 4, 0.3, 0.1, 0.3, 0.02);
				if (launch.left % 4 == 0) {
					Shakes.send(player, 0.45F, player.position().add(0.0, -3.0, 0.0));
				}
				if (launch.left == LIFT_TICKS) {
					level.playSound(null, pod.x, pod.y, pod.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0F, 0.5F);
				}
			}
			launch.left--;
			if (launch.left < 0) {
				done.add(entry.getKey());
				fly(player, launch);
			}
		}
		for (UUID id : done) {
			LAUNCHES.remove(id);
		}
	}

	private static void landings(MinecraftServer server) {
		List<UUID> done = new ArrayList<>();
		for (Map.Entry<UUID, Landing> entry : LANDINGS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Landing landing = entry.getValue();
			if (player == null || !player.isAlive() || player.level().dimension() != landing.level()) {
				done.add(entry.getKey());
				continue;
			}
			ServerLevel level = player.level();
			if ((player.onGround() && level.getGameTime() - landing.since() > 10) || level.getGameTime() > landing.until() || player.getY() <= landing.padY() + 0.2 || player.getAbilities().flying) {
				done.add(entry.getKey());
				player.removeEffect(MobEffects.SLOW_FALLING);
				player.resetFallDistance();
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 24, 0.8, 0.1, 0.8, 0.06);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
				Shakes.send(player, 0.5F, player.position().add(0.0, -2.0, 0.0));
				continue;
			}
			Vec3 motion = player.getDeltaMovement();
			if (motion.y < -DESCENT_SPEED) {
				player.setDeltaMovement(motion.x * 0.5, -DESCENT_SPEED, motion.z * 0.5);
				player.syncVelocity = true;
			}
			player.resetFallDistance();
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 3, 0.2, 0.05, 0.2, 0.03);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.05, 0.2, 0.01);
		}
		for (UUID id : done) {
			LANDINGS.remove(id);
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
			if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying) {
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
	private static void buildMaw(ServerLevel level) {
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
		for (int i = 0; i < MAW_WRECKS; i++) {
			double angle = Math.PI * 2.0 * i / MAW_WRECKS + random.nextDouble() * 0.5;
			double distance = MAW_WRECK_MIN + random.nextDouble() * (MAW_WRECK_MAX - MAW_WRECK_MIN);
			BlockPos centre = new BlockPos((int)Math.round(Math.cos(angle) * distance), MAW_Y + random.nextInt(MAW_WRECK_RISE * 2 + 1) - MAW_WRECK_RISE,
				(int)Math.round(Math.sin(angle) * distance));
			if (Math.abs(centre.getX() - MAW_PAD_X) < 12 && Math.abs(centre.getZ()) < 12) {
				continue;
			}
			wreck(level, centre, random);
		}
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
	}

	// ---------------------------------------------------------------- flying

	/** The lift is done: out of this world and down into the next. */
	private static void fly(ServerPlayer player, Launch launch) {
		MinecraftServer server = player.level().getServer();
		ServerLevel from = player.level();
		NEXT_LAUNCH.put(player.getUUID(), from.getGameTime() + COOLDOWN_TICKS);
		if (player.isPassenger()) {
			player.stopRiding();
		}
		boolean inFuture = from.dimension() == DESTS[FUTURE].level();
		if (!far(from.dimension()) && !inFuture) {
			player.setAttached(HOME, new Home(from.dimension().identifier().toString(), launch.pod));
		}
		if (launch.dest == HOME_INDEX) {
			if (inFuture) {
				Future.podBack(player);
				landMessage(player, null);
				return;
			}
			arriveHome(player, server);
			return;
		}
		Dest dest = DESTS[launch.dest];
		if (dest.kind() == Kind.FUTURE) {
			// the leap remembers where he left from: back on the ground by the pod, not up in the lift
			player.teleportTo(from, launch.origin.x, launch.origin.y, launch.origin.z, Set.of(), player.getYRot(), player.getXRot(), false);
			if (Future.podGo(player)) {
				podBeside(player);
				landMessage(player, dest);
			}
			return;
		}
		ServerLevel to = server.getLevel(dest.level());
		if (to == null) {
			return;
		}
		BlockPos pad = pad(to, dest);
		descend(player, to, pad, DESCENT_HEIGHT);
		landMessage(player, dest);
	}

	private static void landMessage(ServerPlayer player, @Nullable Dest dest) {
		player.sendOverlayMessage(Component.translatable(dest == null ? "message.jaymospowers.space_home" : "message.jaymospowers.space_landed",
			Component.translatable("space.jaymospowers.dest." + (dest == null ? "home" : dest.key()))).withStyle(ChatFormatting.AQUA));
		weigh(player);
		ZFighterSpaceMissions.arrived(player);
	}

	/** Home: down by the pod he left from, or at the world's spawn if the pod or the world is gone. */
	private static void arriveHome(ServerPlayer player, MinecraftServer server) {
		Home home = player.getAttached(HOME);
		ServerLevel to = null;
		if (home != null) {
			Identifier id = Identifier.tryParse(home.dimension());
			to = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		if (home != null && to != null && to.getBlockState(home.pos()).is(POD)) {
			descend(player, to, home.pos(), DESCENT_HEIGHT);
		} else {
			to = server.overworld();
			BlockPos spawn = to.getRespawnData().pos();
			to.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
			int y = to.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
			player.teleportTo(to, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		}
		landMessage(player, null);
	}

	/** Puts the player over the pod at {@code pad} (high up if the way is clear) and starts the controlled fall. */
	private static void descend(ServerPlayer player, ServerLevel to, BlockPos pad, int height) {
		to.getChunk(pad.getX() >> 4, pad.getZ() >> 4);
		// beside the pod, on the first side with room to stand
		int[][] sides = {{0, 2}, {2, 0}, {-2, 0}, {0, -2}, {1, 1}, {-1, -1}};
		int[] side = sides[0];
		for (int[] candidate : sides) {
			BlockPos feet = pad.offset(candidate[0], 0, candidate[1]);
			if (to.getBlockState(feet).getCollisionShape(to, feet).isEmpty() && to.getBlockState(feet.above()).getCollisionShape(to, feet.above()).isEmpty()
				&& !to.getBlockState(feet.below()).getCollisionShape(to, feet.below()).isEmpty()) {
				side = candidate;
				break;
			}
		}
		int clear = 0;
		while (clear < height && to.getBlockState(pad.offset(side[0], clear + 3, side[1])).getCollisionShape(to, pad.offset(side[0], clear + 3, side[1])).isEmpty()
			&& pad.getY() + clear + 3 < to.getMaxY() - 2) {
			clear++;
		}
		int up = Math.max(DESCENT_MIN, clear);
		Vec3 at = new Vec3(pad.getX() + side[0] + 0.5, pad.getY() + up, pad.getZ() + side[1] + 0.5);
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), player.getYRot(), 0.0F, true);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DESCENT_TICKS + 40, 0, true, false), player);
		LANDINGS.put(player.getUUID(), new Landing(to.dimension(), pad.getY(), to.getGameTime(), to.getGameTime() + DESCENT_TICKS));
		to.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5F, 0.6F);
	}

	// ---------------------------------------------------------------- the landing pad

	/** The place's pad: where its pod stands. Found and built the first time (or again, if the pod is gone), then kept. */
	static BlockPos pad(ServerLevel level, Dest dest) {
		BlockPos saved = level.getAttached(PAD);
		if (saved != null) {
			level.getChunk(saved.getX() >> 4, saved.getZ() >> 4);
			if (level.getBlockState(saved).is(POD)) {
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

	private static @Nullable BlockPos forgeFloor(ServerLevel level, int x, int z) {
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

	/** The pad: a floor of {@link Dest#floor}, a lamp at each corner, the air above cleared, and the pod in the middle. */
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
		level.setBlock(spot, POD.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** In the future: a pod on the ground close to where the leap set him down (best effort; the city is built, not generated). */
	private static void podBeside(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 1}, {-3, -1}, {1, 3}, {-1, -3}};
		for (int[] offset : offsets) {
			for (int dy = 2; dy >= -2; dy--) {
				BlockPos spot = at.offset(offset[0], dy, offset[1]);
				if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(),
					net.minecraft.core.Direction.UP)) {
					level.setBlock(spot, POD.defaultBlockState(), Block.UPDATE_ALL);
					return;
				}
			}
		}
	}
}
