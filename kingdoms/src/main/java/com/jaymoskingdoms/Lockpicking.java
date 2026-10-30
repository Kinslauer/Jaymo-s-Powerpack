package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.234) Locked loot chests and the two lockpicking games, Pin and Rotary. Andy asked for Locks Unofficial's
 * minigames; that mod is All Rights Reserved and Forge 1.20.1 only, so these are our own, from the idea alone.
 *
 * <p>A loot chest (or barrel, Lootr's included) is read the first time anyone tries to open or break it, before its loot
 * is ever put in: its loot table is rolled with the chest's own seed and what would come out is weighed
 * ({@link #worth}). Worth enough and the chest is locked, from level 1 to {@link #LEVELS}, with a Pin or a Rotary lock.
 * Anyone with a {@link #LOCKPICK} can try it: the game runs on their screen ({@code LockpickScreen}), and a slip that
 * snaps the pick uses one up. Stealth helps: each level of practice and each rank of Lockpicking Prowess (the perk that
 * took Slip Away's place) widens the sweet spots and eases the strain on the pick; from rank 2 the pick feels the
 * binding pin or the gate, and at rank 3 the first slip of each try is forgiven.
 */
public final class Lockpicking {
	/** What a chest's loot must be worth to be locked, and the worth each lock level from 2 up starts at. */
	static final float LOCK_WORTH = 14.0F;
	static final float[] LEVEL_WORTH = {24.0F, 38.0F, 60.0F, 90.0F};
	public static final int LEVELS = 5;
	/** Worth for a stack's rarity (uncommon, rare, epic), for an enchantment on it, for a treasure loot table. */
	static final float UNCOMMON = 3.0F, RARE = 8.0F, EPIC = 16.0F, ENCHANTED = 6.0F, TREASURE_TABLE = 6.0F;
	/** How far from the lock a picker may stand (blocks). */
	static final double REACH = 6.0;
	/** Stealth experience for a lock picked: this, and this more per level. */
	static final int XP_PICK = 2, XP_PER_LEVEL = 2;
	/** Practice: each Stealth level (of the first page's 15) widens the sweet spots by this share. */
	static final float PRACTICE_PER_LEVEL = 0.02F;
	/** Lockpicking Prowess, per rank: sweet spots this share wider, this share less strain on the pick. */
	static final float PROWESS_WIDTH = 0.15F, PROWESS_STRAIN = 0.2F;
	/** Lockpicking Prowess: the rank from which the pick feels the binding pin or gate, and the rank that forgives a slip. */
	static final int PROWESS_FEEL = 2, PROWESS_FORGIVE = 3;

	/** The two locks. */
	public enum Kind {
		PIN, ROTARY
	}

	/** A container's lock: read (so never weighed again), locked or not, which kind, how hard, and the seed its secret grows from. */
	record Lock(boolean locked, int kind, int level, long seed) {
		static final Lock OPEN = new Lock(false, 0, 0, 0L);
		static final Codec<Lock> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("locked").forGetter(Lock::locked),
			Codec.INT.optionalFieldOf("kind", 0).forGetter(Lock::kind),
			Codec.INT.optionalFieldOf("level", 0).forGetter(Lock::level),
			Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Lock::seed)
		).apply(i, Lock::new));
	}

	/** On a loot container's block entity once it's been read. Saved with the chest. */
	static final AttachmentType<Lock> LOCK = AttachmentRegistry.create(JaymosKingdoms.id("lock"), builder -> builder.persistent(Lock.CODEC));

	public static Item LOCKPICK;

	/**
	 * Server → player: play this lock. {@code width} and {@code strain} are the factors on its sweet spots and on the
	 * pick's strain, {@code feel} whether the pick feels the binding pin or gate, {@code forgive} how many slips a try
	 * forgives, {@code picks} how many lockpicks they carry.
	 */
	public record Open(BlockPos pos, int kind, int level, long seed, float width, float strain, boolean feel, int forgive, int picks)
		implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("lock_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeBlockPos(p.pos);
			buf.writeVarInt(p.kind);
			buf.writeVarInt(p.level);
			buf.writeLong(p.seed);
			buf.writeFloat(p.width);
			buf.writeFloat(p.strain);
			buf.writeBoolean(p.feel);
			buf.writeVarInt(p.forgive);
			buf.writeVarInt(p.picks);
		}, buf -> new Open(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
			buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: how a try at the lock at {@code pos} went ({@link #GAVE_UP}, {@link #PICKED}, {@link #SNAPPED}). */
	public record Result(BlockPos pos, int outcome) implements CustomPacketPayload {
		public static final int GAVE_UP = 0, PICKED = 1, SNAPPED = 2;
		public static final Type<Result> TYPE = new Type<>(JaymosKingdoms.id("lock_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
			net.minecraft.core.BlockPos.STREAM_CODEC, Result::pos, ByteBufCodecs.VAR_INT, Result::outcome, Result::new);

		@Override
		public Type<Result> type() {
			return TYPE;
		}
	}

	/** Player → the lock they're picking. */
	private static final Map<UUID, BlockPos> PICKING = new HashMap<>();
	/** (0.7.412) Player → what happens when the lock they're picking is no container's but a story's (a shackle, a cell door): see {@link #pickVirtual}. */
	private static final Map<UUID, Runnable> VIRTUAL = new HashMap<>();

	private Lockpicking() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lockpick"));
		LOCKPICK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.SHEARS, LOCKPICK));
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Result.TYPE, Result.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Result.TYPE, (payload, context) -> result(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PICKING.remove(handler.getPlayer().getUUID());
			VIRTUAL.remove(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PICKING.clear();
			VIRTUAL.clear();
		});
		UseBlockCallback.EVENT.register(Lockpicking::use);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || player.isCreative() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return true;
			}
			if (!locked(server, pos)) {
				return true;
			}
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.unbreakable").withStyle(ChatFormatting.GRAY));
			return false;
		});
	}

	/** A player went to open a container: a locked one (read now if it never was) opens the lockpicking game instead. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			// (the client can't know: the server answers, and opens the chest itself once it's picked)
			return InteractionResult.PASS;
		}
		if (!locked(server, pos)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			// (the off hand's go at it is refused too, without a second game)
			return InteractionResult.FAIL;
		}
		BlockPos lockPos = lockAt(server, pos);
		Lock lock = lockPos == null ? null : lockOf(server, lockPos);
		if (lock == null || !lock.locked()) {
			return InteractionResult.PASS;
		}
		server.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
		int picks = sp.getInventory().countItem(LOCKPICK);
		if (picks == 0) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.no_pick." + (lock.kind() == Kind.ROTARY.ordinal() ? "rotary" : "pin"),
				Component.translatable("message.jaymoskingdoms.lock.level." + lock.level())).withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (!ServerPlayNetworking.canSend(sp, Open.TYPE)) {
			return InteractionResult.FAIL;
		}
		VIRTUAL.remove(sp.getUUID());
		PICKING.put(sp.getUUID(), lockPos);
		int prowess = Skills.rank(sp, Skills.Perk.LOCKPICKING);
		int practice = Math.min(Skills.MAX_LEVEL, Skills.of(sp).level(Skills.Pillar.STEALTH));
		float width = (1.0F + PRACTICE_PER_LEVEL * practice) * (1.0F + PROWESS_WIDTH * prowess);
		float strain = Math.max(0.2F, 1.0F - PROWESS_STRAIN * prowess);
		ServerPlayNetworking.send(sp, new Open(lockPos, lock.kind(), lock.level(), lock.seed(), width, strain, prowess >= PROWESS_FEEL,
			prowess >= PROWESS_FORGIVE ? 1 : 0, picks));
		return InteractionResult.SUCCESS;
	}

	/** How a try went. A snapped pick is used up; a picked lock opens for good, and the chest opens. */
	private static void result(ServerPlayer player, Result result) {
		BlockPos pos = PICKING.get(player.getUUID());
		if (pos == null || !pos.equals(result.pos())) {
			return;
		}
		ServerLevel level = player.level();
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
			PICKING.remove(player.getUUID());
			VIRTUAL.remove(player.getUUID());
			return;
		}
		Runnable virtual = VIRTUAL.get(player.getUUID());
		if (virtual != null) {
			virtualResult(player, result, pos, virtual);
			return;
		}
		Lock lock = lockOf(level, pos);
		if (lock == null || !lock.locked()) {
			PICKING.remove(player.getUUID());
			return;
		}
		switch (result.outcome()) {
			case Result.SNAPPED -> {
				if (!usePick(player)) {
					PICKING.remove(player.getUUID());
				}
				level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
			}
			case Result.PICKED -> {
				PICKING.remove(player.getUUID());
				for (BlockPos half : halves(level, pos)) {
					BlockEntity be = level.getBlockEntity(half);
					if (be != null && be.hasAttached(LOCK)) {
						be.setAttached(LOCK, Lock.OPEN);
						be.setChanged();
					}
				}
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.5F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.picked").withStyle(ChatFormatting.GREEN));
				Skills.grant(player, Skills.Pillar.STEALTH, XP_PICK + XP_PER_LEVEL * lock.level());
				// and in they go
				BlockState state = level.getBlockState(pos);
				state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
			}
			default -> PICKING.remove(player.getUUID());
		}
	}

	/**
	 * (0.7.412) A lock that is no container's: a story's shackle or cell door, picked with the same Pin game on the lock at
	 * {@code at} (the pick's reach is measured from there). {@code picked} runs once the game is won. False if they've no
	 * lockpick (or can't play it): nothing opens.
	 */
	public static boolean pickVirtual(ServerPlayer sp, BlockPos at, int level, long seed, Runnable picked) {
		int picks = sp.getInventory().countItem(LOCKPICK);
		if (picks == 0 || !ServerPlayNetworking.canSend(sp, Open.TYPE)) {
			return false;
		}
		VIRTUAL.put(sp.getUUID(), picked);
		PICKING.put(sp.getUUID(), at);
		int prowess = Skills.rank(sp, Skills.Perk.LOCKPICKING);
		int practice = Math.min(Skills.MAX_LEVEL, Skills.of(sp).level(Skills.Pillar.STEALTH));
		float width = (1.0F + PRACTICE_PER_LEVEL * practice) * (1.0F + PROWESS_WIDTH * prowess);
		float strain = Math.max(0.2F, 1.0F - PROWESS_STRAIN * prowess);
		ServerPlayNetworking.send(sp, new Open(at, Kind.PIN.ordinal(), level, seed, width, strain, prowess >= PROWESS_FEEL,
			prowess >= PROWESS_FORGIVE ? 1 : 0, picks));
		return true;
	}

	/** (0.7.412) How a try at a story's lock went: a snapped pick is used up, a won game runs {@code picked}. */
	private static void virtualResult(ServerPlayer player, Result result, BlockPos pos, Runnable picked) {
		ServerLevel level = player.level();
		switch (result.outcome()) {
			case Result.SNAPPED -> {
				if (!usePick(player)) {
					PICKING.remove(player.getUUID());
					VIRTUAL.remove(player.getUUID());
				}
				level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
			}
			case Result.PICKED -> {
				PICKING.remove(player.getUUID());
				VIRTUAL.remove(player.getUUID());
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.5F);
				Skills.grant(player, Skills.Pillar.STEALTH, XP_PICK + XP_PER_LEVEL);
				picked.run();
			}
			default -> {
				PICKING.remove(player.getUUID());
				VIRTUAL.remove(player.getUUID());
			}
		}
	}

	/** (0.7.245) The level of the lock on the container at {@code pos} (either half of a double chest), or 0 when it is not locked. */
	public static int lockLevel(ServerLevel level, BlockPos pos) {
		BlockPos at = lockAt(level, pos);
		Lock lock = at == null ? null : lockOf(level, at);
		return lock == null || !lock.locked() ? 0 : lock.level();
	}

	/**
	 * (0.7.245) A mana spirit picks the lock on the container at {@code pos}: Thessaly the Learned, Andy's "things from far
	 * away". It opens as a picked lock does (for good, both halves of a double chest, the Stealth practice for {@code player})
	 * when {@code spiritLevel} is at least twice the lock's level; otherwise nothing happens and this is false. The chest is not
	 * opened for them: the lock is only undone.
	 */
	public static boolean pickBySpirit(ServerLevel level, BlockPos pos, ServerPlayer player, int spiritLevel) {
		BlockPos at = lockAt(level, pos);
		Lock lock = at == null ? null : lockOf(level, at);
		if (lock == null || !lock.locked() || spiritLevel < 2 * lock.level()) {
			return false;
		}
		for (BlockPos half : halves(level, at)) {
			BlockEntity be = level.getBlockEntity(half);
			if (be != null && be.hasAttached(LOCK)) {
				be.setAttached(LOCK, Lock.OPEN);
				be.setChanged();
			}
		}
		level.playSound(null, at, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.picked").withStyle(ChatFormatting.GREEN));
		Skills.grant(player, Skills.Pillar.STEALTH, XP_PICK + XP_PER_LEVEL * lock.level());
		return true;
	}

	/** Uses up one of {@code player}'s lockpicks; false when that was their last (or they had none). */
	private static boolean usePick(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(LOCKPICK)) {
				stack.shrink(1);
				return inventory.countItem(LOCKPICK) > 0;
			}
		}
		return false;
	}

	// ---- reading a chest -----------------------------------------------------------------------------------------

	/** Whether the container at {@code pos} (either half of a double chest) is locked, reading it first if it never was. */
	static boolean locked(ServerLevel level, BlockPos pos) {
		return lockAt(level, pos) != null;
	}

	/** The half of the container at {@code pos} that's locked (reading each half that was never read), or null. */
	private static @Nullable BlockPos lockAt(ServerLevel level, BlockPos pos) {
		for (BlockPos half : halves(level, pos)) {
			Lock lock = lockOf(level, half);
			if (lock != null && lock.locked()) {
				return half;
			}
		}
		return null;
	}

	/** The container at {@code pos} and, for a double chest, its other half. */
	private static List<BlockPos> halves(ServerLevel level, BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(2);
		out.add(pos);
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
			out.add(ChestBlock.getConnectedBlockPos(pos, state));
		}
		return out;
	}

	/** The lock on the container at {@code pos}: read now if it never was; null when it isn't a loot container. */
	private static @Nullable Lock lockOf(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) {
			return null;
		}
		Lock lock = be.getAttached(LOCK);
		if (lock != null) {
			return lock;
		}
		Loot loot = loot(be);
		if (loot == null) {
			return null;
		}
		lock = read(level, pos, loot);
		be.setAttached(LOCK, lock);
		be.setChanged();
		return lock;
	}

	/** A container's loot table and seed, before it's filled. */
	private record Loot(ResourceKey<LootTable> table, long seed) {
	}

	/**
	 * The loot still to come in {@code be}: a vanilla container's own table (given a seed now if it had none, so what's
	 * read is what it'll hold), or Lootr's, by reflection (null once anyone's opened it).
	 */
	private static @Nullable Loot loot(BlockEntity be) {
		if (Lootr.is(be)) {
			return Lootr.loot(be);
		}
		if (!(be instanceof RandomizableContainer container) || container.getLootTable() == null) {
			return null;
		}
		if (container.getLootTableSeed() == 0L && be.getLevel() != null) {
			container.setLootTableSeed(be.getLevel().getRandom().nextLong());
		}
		return new Loot(container.getLootTable(), container.getLootTableSeed());
	}

	/** Reads the loot that's coming: worth enough and the container's locked, its level by how much. */
	private static Lock read(ServerLevel level, BlockPos pos, Loot loot) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(loot.table());
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
		long seed = loot.seed() != 0L ? loot.seed() : level.getRandom().nextLong();
		float worth = worth(table.getRandomItems(params, seed));
		String path = loot.table().identifier().getPath();
		if (path.contains("treasure") || path.contains("ancient_city") || path.contains("end_city") || path.contains("bastion")
			|| path.contains("stronghold") || path.contains("woodland_mansion")) {
			worth += TREASURE_TABLE;
		}
		if (worth < LOCK_WORTH) {
			return Lock.OPEN;
		}
		int lockLevel = 1;
		for (float w : LEVEL_WORTH) {
			if (worth >= w) {
				lockLevel++;
			}
		}
		RandomSource random = RandomSource.create(seed ^ pos.asLong());
		return new Lock(true, random.nextInt(Kind.values().length), Mth.clamp(lockLevel, 1, LEVELS), random.nextLong());
	}

	/** What loot is worth: its rarer stacks, its enchantments, and so much a piece for the valuables. */
	static float worth(List<ItemStack> stacks) {
		float worth = 0.0F;
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			worth += switch (stack.getRarity()) {
				case UNCOMMON -> UNCOMMON;
				case RARE -> RARE;
				case EPIC -> EPIC;
				default -> 0.0F;
			};
			if (stack.isEnchanted() || stack.is(Items.ENCHANTED_BOOK)) {
				worth += ENCHANTED;
			}
			worth += PIECE.getOrDefault(stack.getItem(), 0.0F) * stack.getCount();
		}
		return worth;
	}

	/** The valuables, by the piece. */
	private static final Map<Item, Float> PIECE = Map.ofEntries(
		Map.entry(Items.DIAMOND, 4.0F), Map.entry(Items.EMERALD, 1.5F), Map.entry(Items.GOLD_INGOT, 1.0F), Map.entry(Items.GOLD_NUGGET, 0.1F),
		Map.entry(Items.IRON_INGOT, 0.4F), Map.entry(Items.NETHERITE_SCRAP, 12.0F), Map.entry(Items.NETHERITE_INGOT, 30.0F),
		Map.entry(Items.GOLDEN_APPLE, 4.0F), Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 10.0F), Map.entry(Items.NAME_TAG, 2.0F),
		Map.entry(Items.SADDLE, 1.0F), Map.entry(Items.DIAMOND_HORSE_ARMOR, 4.0F), Map.entry(Items.GOLDEN_HORSE_ARMOR, 2.0F),
		Map.entry(Items.DIAMOND_BLOCK, 36.0F), Map.entry(Items.GOLD_BLOCK, 9.0F), Map.entry(Items.EMERALD_BLOCK, 13.5F),
		Map.entry(Items.DIAMOND_SWORD, 6.0F), Map.entry(Items.DIAMOND_PICKAXE, 8.0F), Map.entry(Items.DIAMOND_CHESTPLATE, 10.0F),
		Map.entry(Items.TOTEM_OF_UNDYING, 10.0F));

	/** Lootr's chests and barrels, by reflection (it's optional, and we don't compile against it). */
	private static final class Lootr {
		private static final String BLOCK_ENTITY = "noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity";
		private static final Map<Class<?>, Method[]> METHODS = new HashMap<>();

		static boolean is(BlockEntity be) {
			for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
				for (Class<?> i : c.getInterfaces()) {
					if (i.getName().equals(BLOCK_ENTITY)) {
						return true;
					}
				}
			}
			return false;
		}

		@SuppressWarnings("unchecked")
		static @Nullable Loot loot(BlockEntity be) {
			try {
				Method[] m = METHODS.computeIfAbsent(be.getClass(), c -> {
					try {
						return new Method[] {c.getMethod("getDataLootTable"), c.getMethod("getDataLootSeed"), c.getMethod("hasBeenOpened")};
					} catch (NoSuchMethodException e) {
						return new Method[0];
					}
				});
				if (m.length == 0 || (Boolean)m[2].invoke(be)) {
					return null;
				}
				ResourceKey<LootTable> table = (ResourceKey<LootTable>)m[0].invoke(be);
				return table == null ? null : new Loot(table, (Long)m[1].invoke(be));
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}
	}
}
