package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Landing beacons. Set a landing beacon down at home and light it with flint and steel: it is saved as the place the pod comes
 * down for you ({@link ZFighterSpace#HOME}), beside it, when you fly home. A player keeps up to {@link #MOST} beacons; lighting a
 * fifth puts the oldest out. Sneak at a lit one with an empty hand to make it the one you come home to. Its light is seen only by the
 * one who lit it and the players on their team (motes sent to those players alone, {@link #show}); to anyone else it is a dark post.
 */
public final class PodBeacons {
	// ---- tuning ----
	/** Beacons a player may keep lit. */
	public static final int MOST = 4;
	/** Ticks between the beacon's motes, how many each time, and how high the light climbs (blocks). */
	static final int SHOW_EVERY = 10, MOTES = 5;
	static final double RISE = 5.0;
	/** How far (blocks) the pod comes down from the beacon. */
	public static final int BESIDE = 3;

	/** A lit beacon of a player: its world and place. */
	public record Spot(String dimension, BlockPos pos) {
		static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Spot::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos)).apply(i, Spot::new));
	}

	/** On the player: the beacons they have lit, oldest first. Kept through death. */
	public static final AttachmentType<List<Spot>> BEACONS = AttachmentRegistry.create(JaymosPowers.id("zfighter_pod_beacons"),
		builder -> builder.persistent(Spot.CODEC.listOf()).copyOnDeath());

	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static Block BLOCK;
	public static BlockEntityType<Beacon> ENTITY;
	private static boolean ready;

	private PodBeacons() {
	}

	/** Registers the block, its item and block entity, and the clean-up when one is broken. Called once, from {@link PodTravel#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("landing_beacon"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new BeaconBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.COPPER)
			.noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("landing_beacon"));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("landing_beacon"), new BlockEntityType<>(Beacon::new, Set.of(BLOCK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(item));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (entity instanceof Beacon beacon && beacon.owner != null && level instanceof ServerLevel server) {
				ServerPlayer owner = server.getServer().getPlayerList().getPlayer(beacon.owner);
				if (owner != null) {
					forget(owner, spotOf(server, pos));
				}
			}
		});
	}

	private static Spot spotOf(Level level, BlockPos pos) {
		return new Spot(level.dimension().identifier().toString(), pos.immutable());
	}

	/** The beacons {@code player} has lit. */
	public static List<Spot> of(Player player) {
		return player.getAttachedOrElse(BEACONS, List.of());
	}

	/** Whether the place {@code player} comes home to is one of their beacons. */
	public static boolean homeIsBeacon(ServerPlayer player) {
		ZFighterSpace.Home home = player.getAttached(ZFighterSpace.HOME);
		return home != null && of(player).contains(new Spot(home.dimension(), home.pos()));
	}

	private static void forget(ServerPlayer player, Spot spot) {
		List<Spot> next = new ArrayList<>(of(player));
		if (next.remove(spot)) {
			player.setAttached(BEACONS, List.copyOf(next));
		}
	}

	/** {@code player} lights the beacon at {@code pos}: it joins their list (the oldest goes out when it is full) and is where they come home. */
	private static void light(ServerLevel level, BlockPos pos, ServerPlayer player, Beacon beacon) {
		Spot spot = spotOf(level, pos);
		List<Spot> next = new ArrayList<>(of(player));
		next.remove(spot);
		next.add(spot);
		while (next.size() > MOST) {
			next.removeFirst();
		}
		player.setAttached(BEACONS, List.copyOf(next));
		beacon.owner = player.getUUID();
		beacon.setChanged();
		level.setBlock(pos, level.getBlockState(pos).setValue(LIT, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.5F);
		player.setAttached(ZFighterSpace.HOME, new ZFighterSpace.Home(spot.dimension(), spot.pos()));
		PodLog.note(player, PodLog.Step.BEACON);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.beacon.lit", next.size(), MOST).withStyle(ChatFormatting.GOLD));
	}

	/** Motes of light climbing from {@code pos}, sent to the beacon's owner and their team and nobody else. */
	private static void show(ServerLevel level, BlockPos pos, UUID owner) {
		ServerPlayer lighter = level.getServer().getPlayerList().getPlayer(owner);
		if (lighter == null) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(pos).add(0.0, 1.0, 0.0);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == lighter || viewer.getTeam() != null && viewer.getTeam() == lighter.getTeam()) {
				level.sendParticles(viewer, MagicFx.LIGHT, true, false, at.x, at.y + RISE / 2.0, at.z, MOTES, 0.1, RISE / 2.0, 0.1, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------- the block

	private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 0, 6, 10, 12, 10), Block.box(4, 12, 4, 12, 16, 12));

	private static final class BeaconBlock extends Block implements EntityBlock {
		BeaconBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(defaultBlockState().setValue(LIT, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(LIT);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Beacon(pos, state);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level.isClientSide() || type != ENTITY ? null : (BlockEntityTicker<T>)(BlockEntityTicker<Beacon>)Beacon::serverTick;
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
			super.setPlacedBy(level, pos, state, placer, stack);
			if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof Beacon beacon) {
				beacon.owner = player.getUUID();
				beacon.setChanged();
			}
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer lighter) || !(level.getBlockEntity(pos) instanceof Beacon beacon)) {
				return InteractionResult.SUCCESS;
			}
			if (state.getValue(LIT) && lighter.getUUID().equals(beacon.owner) && of(lighter).contains(spotOf(level, pos))) {
				lighter.sendOverlayMessage(Component.translatable("message.jaymospowers.beacon.already").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS_SERVER;
			}
			light(server, pos, lighter, beacon);
			if (stack.is(Items.FLINT_AND_STEEL)) {
				stack.hurtAndBreak(1, lighter, hand);
			} else {
				stack.consume(1, lighter);
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)) {
				return InteractionResult.SUCCESS;
			}
			Spot spot = spotOf(level, pos);
			if (state.getValue(LIT) && of(user).contains(spot) && user.isSecondaryUseActive()) {
				user.setAttached(ZFighterSpace.HOME, new ZFighterSpace.Home(spot.dimension(), spot.pos()));
				server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.3F);
				user.sendOverlayMessage(Component.translatable("message.jaymospowers.beacon.home").withStyle(ChatFormatting.GOLD));
			} else if (!state.getValue(LIT)) {
				user.sendOverlayMessage(Component.translatable("message.jaymospowers.beacon.unlit").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	/** A beacon's owner: whoever last lit it. */
	public static final class Beacon extends BlockEntity {
		@Nullable UUID owner;

		public Beacon(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		static void serverTick(Level level, BlockPos pos, BlockState state, Beacon beacon) {
			if (!state.getValue(LIT) || !(level instanceof ServerLevel server) || level.getGameTime() % SHOW_EVERY != 0) {
				return;
			}
			ServerPlayer owner = beacon.owner == null ? null : server.getServer().getPlayerList().getPlayer(beacon.owner);
			// put out when its owner no longer has it among theirs (a fifth was lit, or it was lit by someone else since)
			if (owner != null && !of(owner).contains(spotOf(level, pos))) {
				level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
				return;
			}
			if (beacon.owner != null) {
				show(server, pos, beacon.owner);
			}
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			if (owner != null) {
				output.store("owner", UUIDUtil.CODEC, owner);
			}
		}
	}
}
