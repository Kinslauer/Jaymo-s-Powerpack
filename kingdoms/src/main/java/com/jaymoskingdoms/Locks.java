package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.497) Locks and keys. Andy picked the idea of Locks Reforged and wanted our own version: this is our own build, with
 * nothing copied from it. His plan text: "Forge a lock and its key, fit it to a door, chest, trapdoor or gate. Locked
 * blocks resist villagers, hoppers and breaking. Anyone else uses the Pin or Rotary game you already have. Townsfolk lock
 * their homes and shops at night, and a break-in that's seen is a crime. Key ring in a bauble slot."
 *
 * <p>A forged {@link #LOCK} is fitted to a door, trapdoor, fence gate, chest or barrel, and a {@link #KEY} cut to it goes in
 * the player's pack (or the lock is cut to a key they hold in the off hand, so one key opens many locks). The key opens it
 * from a hand, the pack, a {@link #KEY_RING} in the pack, or one worn in Jaymo's Powers' belt bauble slot (by a tag; no
 * Powers change). Anyone else gets the Pin or Rotary game ({@link Lockpicking#pickLock}), and a pick seen in town is
 * burglary ({@link Theft}). Locked blocks are slow to break, and villagers, redstone, hoppers and copper golems can't
 * open or empty them (see {@code mixin/locks}). Townsfolk lock their own homes and shops at night ({@link NightLocks}).
 */
public final class Locks {
	/** A forged lock's level (Lockpicking's 1-5: "fine"). */
	static final int FORGED_LEVEL = 3;
	/** Breaking a locked block without its key: progress times this, by lock level 1..5. */
	static final float[] BREAK_FACTOR = {0.25F, 0.15F, 0.10F, 0.07F, 0.05F};
	/** Keys stack this high; a key ring (a bundle) holds this many (each key weighs 1/KEY_STACK). */
	static final int KEY_STACK = 16;
	/** Worn key rings are read this often (ticks). */
	static final int KEYRING_REFRESH_TICKS = 20;
	/** "It's locked: this will take a while" is said at most this often per player (ticks). */
	static final int SLOW_MESSAGE_TICKS = 100;

	/**
	 * A lock on a block: the key id it answers (0: a realm's lock, no key), Pin or Rotary, its level 1-5, the realm that
	 * keeps it (-1: none), whether it's locked now, and whether it's a night lock (taken off in the morning).
	 */
	public record Fitted(long key, int kind, int level, int faction, boolean locked, boolean night) {
		static final Codec<Fitted> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("key", 0L).forGetter(Fitted::key),
			Codec.INT.optionalFieldOf("kind", 0).forGetter(Fitted::kind),
			Codec.INT.optionalFieldOf("level", FORGED_LEVEL).forGetter(Fitted::level),
			Codec.INT.optionalFieldOf("faction", -1).forGetter(Fitted::faction),
			Codec.BOOL.optionalFieldOf("locked", true).forGetter(Fitted::locked),
			Codec.BOOL.optionalFieldOf("night", false).forGetter(Fitted::night)
		).apply(i, Fitted::new));
		static final StreamCodec<ByteBuf, Fitted> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Fitted::key, ByteBufCodecs.VAR_INT, Fitted::kind, ByteBufCodecs.VAR_INT, Fitted::level,
			ByteBufCodecs.VAR_INT, Fitted::faction, ByteBufCodecs.BOOL, Fitted::locked, ByteBufCodecs.BOOL, Fitted::night, Fitted::new);

		Fitted withLocked(boolean locked) {
			return new Fitted(this.key, this.kind, this.level, this.faction, locked, this.night);
		}

		boolean keyed() {
			return this.key != 0L;
		}

		/** The seed the picking game grows from: the key's (every lock cut to one key shares it), or the spot's. */
		long seed(BlockPos anchor) {
			return this.keyed() ? this.key : anchor.asLong() ^ NightLocks.NIGHT_SALT;
		}
	}

	/** The key id a cut key or lock carries. */
	public static DataComponentType<Long> LOCK_ID;
	public static Item LOCK, KEY, BLANK_KEY, KEY_RING;

	/** Per chunk: anchor position (packed, as text) → the lock fitted there. Saved and synced. */
	static final AttachmentType<Map<String, Fitted>> LOCKS = AttachmentRegistry.create(JaymosKingdoms.id("fitted_locks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Fitted.CODEC))
			.syncWith(ByteBufCodecs.<ByteBuf, String, Fitted, Map<String, Fitted>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, Fitted.STREAM_CODEC),
				AttachmentSyncPredicate.all()));

	/** The key ids on a key ring a player wears as a bauble, so the client's crack speed matches the server's. Not saved. */
	static final AttachmentType<List<Long>> WORN_KEYS = AttachmentRegistry.create(JaymosKingdoms.id("worn_keys"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** What a lock fits. */
	public static final TagKey<Block> LOCKABLE = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("lockable"));

	/** Player → when they were last told a locked block is slow to break (game time). */
	private static final Map<UUID, Long> SLOW_TOLD = new HashMap<>();

	private Locks() {
	}

	/** The lock, the key and the blank: one class, three tooltips. */
	static final class Lockwork extends Item {
		Lockwork(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			Long id = stack.get(LOCK_ID);
			if (stack.is(LOCK)) {
				if (id == null) {
					tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.lock.fresh").withStyle(ChatFormatting.GRAY));
					tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.lock.share").withStyle(ChatFormatting.GRAY));
				} else {
					tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.lock.cut", code(id)).withStyle(ChatFormatting.GRAY));
				}
			} else if (stack.is(KEY)) {
				if (id != null) {
					tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.key.cut", code(id)).withStyle(ChatFormatting.GRAY));
				}
				tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.key.how").withStyle(ChatFormatting.GRAY));
			} else {
				tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.blank_key").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** A key's short code, as its tooltip shows it. */
	static String code(long id) {
		return String.format("%04X", (int)((id ^ (id >>> 32)) & 0xFFFF));
	}

	/** The kind of lock a key id is cut for. */
	static int kind(long id) {
		return Math.floorMod(Long.hashCode(id), Lockpicking.Kind.values().length);
	}

	static void register() {
		LOCK_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("lock_id"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		LOCK = item("lock", key -> new Lockwork(new Item.Properties().setId(key).stacksTo(16)));
		KEY = item("key", key -> new Lockwork(new Item.Properties().setId(key).stacksTo(KEY_STACK)));
		BLANK_KEY = item("blank_key", key -> new Lockwork(new Item.Properties().setId(key).stacksTo(16)));
		KEY_RING = item("key_ring", key -> new KeyRingItem(new Item.Properties().setId(key).stacksTo(1)
			.component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)));
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosKingdoms.id("key_copy"), KeyCopyRecipe.SERIALIZER);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Lockpicking.LOCKPICK, LOCK, BLANK_KEY, KEY_RING));
		UseBlockCallback.EVENT.register(Locks::use);
		AttackBlockCallback.EVENT.register(Locks::attacked);
		// (must come after Theft.register(), so that Theft.broke still sees the lock)
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
			BREAKING.clear();
			if (!level.isClientSide()) {
				BREAKING.addAll(anchors(pos, state));
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register(Locks::broken);
		ServerTickEvents.END_SERVER_TICK.register(Locks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SLOW_TOLD.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SLOW_TOLD.remove(handler.getPlayer().getUUID()));
	}

	private static Item item(String name, java.util.function.Function<ResourceKey<Item>, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(key));
	}

	// ---- where a lock lives --------------------------------------------------------------------------------------

	/** Where the lock for the block {@code state} at {@code pos} could be kept: a door's lower half; a chest's either half. */
	private static List<BlockPos> anchors(BlockPos pos, BlockState state) {
		List<BlockPos> out = new ArrayList<>(2);
		if (state.getBlock() instanceof DoorBlock) {
			out.add(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos);
			return out;
		}
		out.add(pos);
		if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
			out.add(ChestBlock.getConnectedBlockPos(pos, state));
		}
		return out;
	}

	/** The lock record kept at exactly {@code anchor}. */
	static @Nullable Fitted raw(Level level, BlockPos anchor) {
		return level.getChunkAt(anchor).getAttachedOrElse(LOCKS, Map.of()).get(Long.toString(anchor.asLong()));
	}

	/** Keeps {@code fitted} at {@code anchor}. */
	static void put(Level level, BlockPos anchor, Fitted fitted) {
		var chunk = level.getChunkAt(anchor);
		Map<String, Fitted> map = new HashMap<>(chunk.getAttachedOrElse(LOCKS, Map.of()));
		map.put(Long.toString(anchor.asLong()), fitted);
		chunk.setAttached(LOCKS, map);
	}

	/** Forgets the lock at {@code anchor}. */
	static void remove(Level level, BlockPos anchor) {
		var chunk = level.getChunkAt(anchor);
		Map<String, Fitted> map = chunk.getAttached(LOCKS);
		if (map != null && map.containsKey(Long.toString(anchor.asLong()))) {
			Map<String, Fitted> left = new HashMap<>(map);
			left.remove(Long.toString(anchor.asLong()));
			if (left.isEmpty()) {
				chunk.removeAttached(LOCKS);
			} else {
				chunk.setAttached(LOCKS, left);
			}
		}
	}

	/** Anchors of the block a player is breaking now. Their locks are dropped by {@link #broken}, not forgotten. */
	private static final java.util.Set<BlockPos> BREAKING = new java.util.HashSet<>();

	/**
	 * A block was removed some way other than a player breaking it (explosion, mob, piston, lost support). Its lock goes
	 * with it, so a block placed there later is not locked to the old key. Called from the block state mixin.
	 */
	public static void forgetRemoved(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.is(LOCKABLE)) {
			return;
		}
		if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			return;
		}
		if (BREAKING.contains(pos)) {
			return;
		}
		// Weathering, waxing and scraping swap in another lockable block. The lock stays.
		if (level.getBlockState(pos).is(LOCKABLE)) {
			return;
		}
		if (raw(level, pos) != null) {
			remove(level, pos);
		}
	}

	/** A lock found, and where it is kept. */
	private record Hit(BlockPos anchor, Fitted fitted) {
	}

	private static @Nullable Hit find(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(LOCKABLE)) {
			return null;
		}
		for (BlockPos anchor : anchors(pos, state)) {
			Fitted f = raw(level, anchor);
			if (f == null) {
				continue;
			}
			if (level instanceof ServerLevel && !level.getBlockState(anchor).is(LOCKABLE)) {
				// the block is gone: so is its lock
				remove(level, anchor);
				continue;
			}
			return new Hit(anchor, f);
		}
		return null;
	}

	/** The lock on the block at pos (door: its lower half; chest: either half), or null. Server: forgets one whose block is gone. */
	static @Nullable Fitted entry(Level level, BlockPos pos) {
		Hit hit = find(level, pos);
		return hit == null ? null : hit.fitted();
	}

	// ---- who has the key -----------------------------------------------------------------------------------------

	/**
	 * Whether player carries the key cut to id: hands, pack (36 + equipment), key rings in the pack, the worn key ring
	 * (server: PowersCompat.baubles; client: WORN_KEYS). Always false for id 0.
	 */
	public static boolean holdsKey(Player player, long id) {
		if (id == 0L) {
			return false;
		}
		if (cuts(player.getMainHandItem(), id) || cuts(player.getOffhandItem(), id)) {
			return true;
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (cuts(inventory.getItem(i), id)) {
				return true;
			}
		}
		if (player instanceof ServerPlayer) {
			for (ItemStack worn : PowersCompat.baubles(player)) {
				if (cuts(worn, id)) {
					return true;
				}
			}
			return false;
		}
		return player.getAttachedOrElse(WORN_KEYS, List.<Long>of()).contains(id);
	}

	/** {@code stack} is the key cut to id, or a key ring holding it. */
	private static boolean cuts(ItemStack stack, long id) {
		if (stack.is(KEY)) {
			Long own = stack.get(LOCK_ID);
			return own != null && own == id;
		}
		return stack.is(KEY_RING) && KeyRingItem.ids(stack).anyMatch(k -> k == id);
	}

	/** The key ids on the key rings among the bauble stacks {@code player} wears. */
	private static List<Long> wornKeyIds(Player player) {
		List<Long> out = new ArrayList<>();
		for (ItemStack worn : PowersCompat.baubles(player)) {
			if (worn.is(KEY_RING)) {
				KeyRingItem.ids(worn).forEach(out::add);
			}
		}
		return out;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % KEYRING_REFRESH_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Long> ids = wornKeyIds(player);
			List<Long> had = player.getAttached(WORN_KEYS);
			if (ids.isEmpty()) {
				if (had != null) {
					player.removeAttached(WORN_KEYS);
				}
			} else if (!ids.equals(had)) {
				player.setAttached(WORN_KEYS, ids);
			}
		}
	}

	/**
	 * Whether who may open f: creative player; holdsKey; a realm's lock: a ServerPlayer with Theft.exempt, Folk with
	 * factionId() == f.faction(), a Villager whose Theft.lawAt(level, its pos) is f.faction(). Null and everyone else: no.
	 */
	static boolean mayPass(@Nullable Entity who, Fitted f, Level level) {
		if (who == null) {
			return false;
		}
		if (who instanceof Player player) {
			if (player.isCreative() || holdsKey(player, f.key())) {
				return true;
			}
			if (f.faction() >= 0 && player instanceof ServerPlayer sp) {
				Faction realm = Factions.get(sp.level().getServer(), f.faction());
				return realm != null && Theft.exempt(sp, realm);
			}
			return false;
		}
		if (f.faction() < 0) {
			return false;
		}
		if (who instanceof Folk folk) {
			return folk.factionId() == f.faction();
		}
		if (who instanceof Villager villager && level instanceof ServerLevel server) {
			Faction law = Theft.lawAt(server, villager.blockPosition());
			return law != null && law.id() == f.faction();
		}
		return false;
	}

	/** A locked lock on the block that who may not pass (DoorBlock.setOpen, TrapDoorBlock.toggle, MCA, Doorways). */
	public static boolean barred(Level level, BlockPos pos, @Nullable Entity who) {
		Fitted f = entry(level, pos);
		return f != null && f.locked() && !mayPass(who, f, level);
	}

	/** A locked lock on the shut block (redstone checks). */
	public static boolean shut(Level level, BlockPos pos) {
		Fitted f = entry(level, pos);
		return f != null && f.locked();
	}

	/** Its container may not be pulled from: a locked fitted lock, or a locked loot lock (Lockpicking.sealed). Server. */
	public static boolean sealed(ServerLevel level, BlockPos pos) {
		Fitted f = entry(level, pos);
		return f != null && f.locked() || Lockpicking.sealed(level, pos);
	}

	/** A player's lock (keyed) that keeps faction's own folk out (Storage). */
	static boolean barredTo(ServerLevel level, BlockPos pos, Faction faction) {
		Fitted f = entry(level, pos);
		return f != null && f.locked() && f.keyed() && f.faction() != faction.id();
	}

	/** Theft.broke: the block just broken was locked against player (fitted without their key, or a locked loot lock on entity). */
	static boolean lockedAgainst(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, ServerPlayer player) {
		if (player.isCreative()) {
			return false;
		}
		for (BlockPos anchor : anchors(pos, state)) {
			Fitted f = raw(level, anchor);
			if (f != null && f.locked() && (f.faction() >= 0 || !holdsKey(player, f.key()))) {
				return true;
			}
		}
		if (entity != null) {
			Lockpicking.Lock own = entity.getAttached(Lockpicking.LOCK);
			if (own != null && own.locked()) {
				return true;
			}
		}
		if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
			BlockEntity other = level.getBlockEntity(ChestBlock.getConnectedBlockPos(pos, state));
			Lockpicking.Lock lock = other == null ? null : other.getAttached(Lockpicking.LOCK);
			return lock != null && lock.locked();
		}
		return false;
	}

	/** Break progress factor for player on this block: 1, or BREAK_FACTOR[level - 1] when locked against them. Both sides. */
	static float breakFactor(Level level, BlockPos pos, BlockState state, Player player) {
		int lockLevel = 0;
		Fitted f = entry(level, pos);
		if (f != null && f.locked() && (f.faction() >= 0 || !holdsKey(player, f.key()))) {
			lockLevel = f.level();
		}
		for (BlockPos half : Lockpicking.halves(level, pos)) {
			BlockEntity be = level.getBlockEntity(half);
			Lockpicking.Lock lock = be == null ? null : be.getAttached(Lockpicking.LOCK);
			if (lock != null && lock.locked()) {
				lockLevel = Math.max(lockLevel, lock.level());
			}
		}
		return lockLevel <= 0 ? 1.0F : BREAK_FACTOR[Mth.clamp(lockLevel, 1, BREAK_FACTOR.length) - 1];
	}

	/** Mixin entry: original * breakFactor, unless !state.is(LOCKABLE) or creative, or level isn't a Level. */
	public static float slowBreak(BlockState state, Player player, BlockGetter level, BlockPos pos, float original) {
		if (!state.is(LOCKABLE) || player.isCreative() || !(level instanceof Level real)) {
			return original;
		}
		return original * breakFactor(real, pos, state, player);
	}

	// ---- using a lock --------------------------------------------------------------------------------------------

	private static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms." + key, args).withStyle(colour));
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		ItemStack main = player.getMainHandItem();
		boolean sneak = player.isSecondaryUseActive();
		if (level.isClientSide()) {
			// SUCCESS on the client sends the packet without predicting the door open
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (main.is(LOCK) && level.getBlockState(pos).is(LOCKABLE)) {
				return InteractionResult.SUCCESS;
			}
			Fitted e = entry(level, pos);
			if (e == null) {
				return InteractionResult.PASS;
			}
			if (e.locked() && !player.isCreative() && !holdsKey(player, e.key())) {
				return InteractionResult.SUCCESS;
			}
			if (sneak && e.keyed() && (matches(main, e) || main.isEmpty() && !e.locked() && holdsKey(player, e.key()))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND && main.is(LOCK)) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(LOCKABLE)) {
				return InteractionResult.PASS;
			}
			return fit(sp, server, pos, state, main) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		Hit found = find(level, pos);
		if (found == null) {
			return InteractionResult.PASS;
		}
		Fitted f = found.fitted();
		BlockPos anchor = found.anchor();
		if (sneak && hand == InteractionHand.MAIN_HAND && main.is(KEY) && f.keyed()) {
			if (!matches(main, f)) {
				say(sp, "locks.wrong_key", ChatFormatting.GRAY);
				return InteractionResult.FAIL;
			}
			boolean lock = !f.locked();
			if (lock) {
				close(server, anchor);
			}
			put(server, anchor, f.withLocked(lock));
			server.playSound(null, anchor, lock ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.6F);
			say(sp, lock ? "locks.locked" : "locks.unlocked", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (sneak && hand == InteractionHand.MAIN_HAND && main.isEmpty() && f.keyed() && !f.locked() && holdsKey(sp, f.key())) {
			remove(server, anchor);
			ItemStack lock = new ItemStack(LOCK);
			lock.set(LOCK_ID, f.key());
			give(sp, lock);
			say(sp, "locks.removed", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (!f.locked() || sp.isCreative() || mayPass(sp, f, server)) {
			return InteractionResult.PASS;
		}
		server.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (hand != InteractionHand.MAIN_HAND) {
			// (the off hand's go at it is refused too, without a second game)
			return InteractionResult.FAIL;
		}
		Lockpicking.Kind[] kinds = Lockpicking.Kind.values();
		Lockpicking.Kind kind = kinds[Math.floorMod(f.kind(), kinds.length)];
		if (!Lockpicking.pickLock(sp, anchor, kind, f.level(), f.seed(anchor), () -> picked(sp, anchor))) {
			if (f.night()) {
				say(sp, "locks.night", ChatFormatting.GRAY);
			} else {
				say(sp, "locks.no_key." + (kind == Lockpicking.Kind.ROTARY ? "rotary" : "pin"), ChatFormatting.GRAY,
					Component.translatable("message.jaymoskingdoms.lock.level." + f.level()));
			}
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	/** {@code stack} is the key cut to {@code f}. */
	private static boolean matches(ItemStack stack, Fitted f) {
		Long id = stack.get(LOCK_ID);
		return stack.is(KEY) && id != null && id == f.key();
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Shuts the door, trapdoor or gate at {@code pos} if it is open. */
	static void close(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock door) {
			door.setOpen(null, level, state, pos, false);
		} else if ((state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) && state.getValue(TrapDoorBlock.OPEN)) {
			level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false), 10);
		}
	}

	/** A forged lock is fitted to the block at pos (a refusal says why). True if it was fitted. */
	private static boolean fit(ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState state, ItemStack lock) {
		BlockPos anchor = anchors(pos, state).getFirst();
		if (Lockpicking.lockLevel(level, pos) > 0 || entry(level, pos) != null) {
			say(sp, "locks.already", ChatFormatting.GRAY);
			return false;
		}
		if (Factions.storageOwner(level.getServer(), pos) != null) {
			say(sp, "locks.storehouse", ChatFormatting.GRAY);
			return false;
		}
		Faction law = Theft.lawAt(level, pos);
		if (law != null && !Placed.byPlayer(sp, level, anchor, level.getBlockState(anchor)) && !Theft.exempt(sp, law)) {
			say(sp, "locks.not_yours", ChatFormatting.GRAY);
			return false;
		}
		Long own = lock.get(LOCK_ID);
		ItemStack off = sp.getOffhandItem();
		Long offId = off.is(KEY) ? off.get(LOCK_ID) : null;
		long id;
		if (own != null) {
			id = own;
			say(sp, "locks.refitted", ChatFormatting.GRAY, code(id));
		} else if (offId != null) {
			id = offId;
			say(sp, "locks.fitted_to_key", ChatFormatting.GRAY, code(id));
		} else {
			do {
				id = level.getRandom().nextLong();
			} while (id == 0L);
			ItemStack key = new ItemStack(KEY);
			key.set(LOCK_ID, id);
			give(sp, key);
			say(sp, "locks.fitted", ChatFormatting.GRAY);
		}
		put(level, anchor, new Fitted(id, kind(id), FORGED_LEVEL, -1, true, false));
		close(level, anchor);
		if (!sp.isCreative()) {
			lock.shrink(1);
		}
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.4F);
		return true;
	}

	/** The pick game at the lock on {@code anchor} was won. */
	private static void picked(ServerPlayer sp, BlockPos anchor) {
		ServerLevel level = sp.level();
		Fitted f = entry(level, anchor);
		if (f == null) {
			return;
		}
		if (f.night()) {
			remove(level, anchor);
		} else {
			put(level, anchor, f.withLocked(false));
		}
		BlockState state = level.getBlockState(anchor);
		Theft.burgled(sp, level, anchor, state);
		say(sp, "lock.picked", ChatFormatting.GREEN);
		state.useWithoutItem(level, sp, new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
	}

	/** Starting to break a block: a loot chest is read now, so its lock syncs before the crack starts; a slow one is said. */
	private static InteractionResult attacked(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || player.isCreative()) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(LOCKABLE)) {
			return InteractionResult.PASS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
			Lockpicking.locked(server, pos);
		}
		if (breakFactor(level, pos, state, player) < 1.0F) {
			long now = server.getGameTime();
			Long told = SLOW_TOLD.get(sp.getUUID());
			if (told == null || now - told >= SLOW_MESSAGE_TICKS) {
				SLOW_TOLD.put(sp.getUUID(), now);
				say(sp, "locks.slow", ChatFormatting.GRAY);
			}
		}
		return InteractionResult.PASS;
	}

	/** A block was broken: its lock goes with it, and a player's keyed lock drops, still cut. */
	private static void broken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
		if (level.isClientSide()) {
			return;
		}
		BREAKING.clear();
		boolean door = state.getBlock() instanceof DoorBlock;
		for (BlockPos anchor : anchors(pos, state)) {
			if (!door && !anchor.equals(pos)) {
				// the other half of a double chest is still standing: it keeps its lock
				continue;
			}
			Fitted f = raw(level, anchor);
			if (f == null) {
				continue;
			}
			remove(level, anchor);
			if (f.keyed() && !player.isCreative()) {
				ItemStack lock = new ItemStack(LOCK);
				lock.set(LOCK_ID, f.key());
				Block.popResource(level, pos, lock);
			}
			return;
		}
	}
}
