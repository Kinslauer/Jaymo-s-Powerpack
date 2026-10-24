package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.143) A Timeless Chrono's echo stops hunting them and takes orders, picked in the powers screen (H):
 * Orders come with time: Standby at once, then one more for each in-game day (of play, as Timeless) up to
 * {@link #UNLOCK_DAYS}; until then their buttons stay greyed out.
 * <ul>
 * <li><b>Standby:</b> it keeps near you, fights only what attacks it or you, and between fights casts Time Bloom
 * where it stands every so often (if you have it).</li>
 * <li><b>Harvest:</b> round the spot you gave the order, it reaps ripe crops (and replants them) and fells the logs and
 * mines the ores you mine most. Drops go into a chest or barrel near that spot, or onto the ground.</li>
 * <li><b>Guard:</b> it holds the spot and fights every monster that comes near, with your own Stasis and Chrono Punch.</li>
 * <li><b>Record / Loop:</b> Record, do something (walk a route, reap a field, fight), then Record again: the echo
 * repeats your walk and your breaks there, over and over. (0.15.147) It also repeats the blocks you place (from what
 * it carries or a chest by the spot, so it can replant and build) and what you use: chests and barrels (it empties
 * what it carries into them and takes out what its next placings need), levers, buttons and doors. There are
 * {@link #SLOTS} loop slots, picked in the powers screen, so three routines can be kept and swapped.</li>
 * </ul>
 * Until its owner ascends, the echo is the curse it always was. Orders go to whichever echo is out; with none out, it
 * takes them up when it comes back.
 */
public final class ChronoDirectives {
	// ---- tuning (ticks / blocks) ----
	/** The longest recording; at this length it stops on its own and starts looping. */
	static final int RECORD_MAX_TICKS = 60 * 20;
	/** How often a recording notes where you are, and how far you must have moved for it to count. */
	static final int RECORD_SAMPLE = 10;
	static final double RECORD_MIN_MOVE = 1.5;
	/** Most steps a recording keeps. */
	static final int RECORD_MAX_STEPS = 400;
	/** In-game days of play as Timeless (online, not creative or spectating) that unlock each tier: Harvest, Guard, Record/Loop. */
	static final int[] UNLOCK_DAYS = {1, 2, 3};
	static final int DAY = 24000;
	/** How often the time served is counted up. */
	static final int SERVE_STEP = 20;
	/** (0.15.147) How many recorded loops a player keeps. */
	public static final int SLOTS = 3;

	public enum Directive {
		STANDBY, HARVEST, GUARD, LOOP;

		static Directive of(int ordinal) {
			Directive[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : STANDBY;
		}

		/** Whether it works round the spot where the order was given. */
		boolean anchored() {
			return this == HARVEST || this == GUARD;
		}

		/** How many unlock tiers it takes (0: from the start). */
		public int tier() {
			return switch (this) {
				case STANDBY -> 0;
				case HARVEST -> 1;
				case GUARD -> 2;
				case LOOP -> 3;
			};
		}
	}

	/**
	 * One recorded thing: walked to {@code pos} ({@link #MOVE}), broke {@code block} at {@code pos} ({@link #BREAK}),
	 * struck at {@code pos} ({@link #STRIKE}), placed {@code block} at {@code pos} ({@link #PLACE}), or used the chest,
	 * barrel, lever, button or door {@code block} at {@code pos} ({@link #INTERACT}).
	 */
	public record Step(int kind, BlockPos pos, Optional<Identifier> block) {
		static final int MOVE = 0, BREAK = 1, STRIKE = 2, PLACE = 3, INTERACT = 4;
		static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("kind").forGetter(Step::kind),
				BlockPos.CODEC.fieldOf("pos").forGetter(Step::pos),
				Identifier.CODEC.optionalFieldOf("block").forGetter(Step::block))
			.apply(i, Step::new));
	}

	/** A recorded loop and the dimension it was recorded in. */
	public record Loop(List<Step> steps, Optional<ResourceKey<Level>> dim) {
		static final Codec<Loop> CODEC = RecordCodecBuilder.create(i -> i.group(
				Step.CODEC.listOf().optionalFieldOf("steps", List.of()).forGetter(Loop::steps),
				Level.RESOURCE_KEY_CODEC.optionalFieldOf("dim").forGetter(Loop::dim))
			.apply(i, Loop::new));
		static final Loop EMPTY = new Loop(List.of(), Optional.empty());
	}

	/** A player's standing orders: the directive, where it was given, the loop slot in use and the {@link #SLOTS} loops. */
	public record Orders(int directive, Optional<GlobalPos> anchor, int slot, List<Loop> loops) {
		static final Codec<Orders> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("directive", 0).forGetter(Orders::directive),
				GlobalPos.CODEC.optionalFieldOf("anchor").forGetter(Orders::anchor),
				Codec.INT.optionalFieldOf("slot", 0).forGetter(Orders::slot),
				Loop.CODEC.listOf().optionalFieldOf("loops", List.of()).forGetter(Orders::loops),
				// before 0.15.147 there was one loop: it becomes the first slot
				Step.CODEC.listOf().optionalFieldOf("loop", List.of()).forGetter(o -> List.of()),
				Level.RESOURCE_KEY_CODEC.optionalFieldOf("loop_dim").forGetter(o -> Optional.empty()))
			.apply(i, (directive, anchor, slot, loops, old, oldDim) -> new Orders(directive, anchor, slot,
				loops.isEmpty() && !old.isEmpty() ? List.of(new Loop(old, oldDim)) : loops)));
		static final Orders NONE = new Orders(0, Optional.empty(), 0, List.of());

		public Orders {
			slot = Math.clamp(slot, 0, SLOTS - 1);
			List<Loop> all = new ArrayList<>(loops.subList(0, Math.min(loops.size(), SLOTS)));
			while (all.size() < SLOTS) {
				all.add(Loop.EMPTY);
			}
			loops = List.copyOf(all);
		}

		Directive kind() {
			return Directive.of(this.directive);
		}

		/** The loop in the slot in use. */
		List<Step> loop() {
			return this.loops.get(this.slot).steps();
		}

		Optional<ResourceKey<Level>> loopDim() {
			return this.loops.get(this.slot).dim();
		}

		Orders with(int directive, Optional<GlobalPos> anchor) {
			return new Orders(directive, anchor, this.slot, this.loops);
		}

		Orders withLoop(int slot, Loop loop) {
			List<Loop> all = new ArrayList<>(this.loops);
			all.set(slot, loop);
			return new Orders(this.directive, this.anchor, slot, all);
		}
	}

	/** On a Chrono player: their echo's standing orders. Saved, kept through death. */
	static final AttachmentType<Orders> ORDERS = AttachmentRegistry.create(JaymosPowers.id("chrono_orders"),
		builder -> builder.persistent(Orders.CODEC).copyOnDeath());

	/** Ticks of play as Timeless, towards the next unlock. Saved, kept through death. */
	static final AttachmentType<Integer> SERVED = AttachmentRegistry.create(JaymosPowers.id("chrono_orders_served"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/**
	 * For the powers screen: the current directive's ordinal, plus {@link #RECORDING_FLAG} while recording, plus the
	 * tiers unlocked (from {@link #UNLOCKED_SHIFT}), the loop slot in use (from {@link #SLOT_SHIFT}) and which slots hold
	 * a loop (a bit each, from {@link #FILLED_SHIFT}). Owner only.
	 */
	public static final AttachmentType<Integer> SHOWN = AttachmentRegistry.create(JaymosPowers.id("chrono_orders_shown"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	public static final int RECORDING_FLAG = 0x100;
	public static final int UNLOCKED_SHIFT = 9, SLOT_SHIFT = 12, FILLED_SHIFT = 14;
	/** Recording (and looping) take this tier. */
	public static final int RECORD_TIER = 3;
	/** The button index that starts or ends a recording, after the directives. */
	public static final int RECORD_BUTTON = Directive.values().length;
	/** (0.15.147) The buttons that pick loop slot 0, 1 or 2 ({@code SLOT_0 + n}), and the one that clears the slot in use. */
	public static final int SLOT_0 = RECORD_BUTTON + 1, CLEAR_LOOP = SLOT_0 + SLOTS;

	/** Client → server: a button in the powers screen ({@link Directive} ordinal, {@link #RECORD_BUTTON}, a slot or {@link #CLEAR_LOOP}). */
	public record Order(int button) implements CustomPacketPayload {
		public static final Type<Order> TYPE = new Type<>(JaymosPowers.id("chrono_order"));
		public static final StreamCodec<ByteBuf, Order> CODEC = ByteBufCodecs.VAR_INT.map(Order::new, Order::button);

		@Override
		public Type<Order> type() {
			return TYPE;
		}
	}

	/** A recording in progress (not saved: a restart or logout drops it). */
	private static final class Recording {
		final ResourceKey<Level> dimension;
		/** The loop slot it's recorded into. */
		final int slot;
		final List<Step> steps = new ArrayList<>();
		int ticks;
		@Nullable BlockPos last;

		Recording(ResourceKey<Level> dimension, int slot) {
			this.dimension = dimension;
			this.slot = slot;
		}

		void add(Step step) {
			// (a use is heard once per hand: once is enough)
			if (this.steps.size() < RECORD_MAX_STEPS && (this.steps.isEmpty() || !this.steps.getLast().equals(step))) {
				this.steps.add(step);
			}
		}
	}

	private static final Map<UUID, Recording> RECORDINGS = new HashMap<>();

	private ChronoDirectives() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (payload, ctx) -> order(ctx.player(), payload.button()));
		ServerTickEvents.END_SERVER_TICK.register(ChronoDirectives::tick);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			Recording rec = RECORDINGS.get(player.getUUID());
			if (rec != null && level.dimension() == rec.dimension) {
				rec.add(new Step(Step.BREAK, pos.immutable(), Optional.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()))));
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			Recording rec = level.isClientSide() ? null : RECORDINGS.get(player.getUUID());
			if (rec != null && level.dimension() == rec.dimension) {
				rec.add(new Step(Step.STRIKE, entity.blockPosition(), Optional.empty()));
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register(ChronoDirectives::used);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RECORDINGS.remove(handler.player.getUUID()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.player.getAttached(ORDERS) != null) {
				show(handler.player);
			}
		});
	}

	/**
	 * (0.15.147) A use, while recording: a chest, barrel, lever, button or door is noted as used; otherwise a block in
	 * hand is noted as placed where it will go.
	 */
	private static InteractionResult used(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		Recording rec = level.isClientSide() ? null : RECORDINGS.get(player.getUUID());
		if (rec == null || level.dimension() != rec.dimension || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		ItemStack held = player.getItemInHand(hand);
		// sneaking with something in hand skips the block's own use, as it does for the player
		boolean uses = !(player.isSecondaryUseActive() && !held.isEmpty());
		if (uses && usable(level, pos, state)) {
			rec.add(new Step(Step.INTERACT, pos.immutable(), Optional.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()))));
		} else if (held.getItem() instanceof BlockItem item) {
			BlockPos at = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
			rec.add(new Step(Step.PLACE, at.immutable(), Optional.of(BuiltInRegistries.BLOCK.getKey(item.getBlock()))));
		}
		return InteractionResult.PASS;
	}

	/** The blocks an echo can use: chests and barrels, levers, buttons and doors. */
	static boolean usable(Level level, BlockPos pos, BlockState state) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || state.getBlock() instanceof LeverBlock
			|| state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof DoorBlock;
	}

	/** Whether {@code player}'s echo obeys them: they're Timeless. */
	public static boolean commandable(Player player) {
		return Fate.isAscended(player);
	}

	/** How many unlock tiers {@code player} has reached. */
	static int unlocked(ServerPlayer player) {
		Integer served = player.getAttached(SERVED);
		int ticks = served == null ? 0 : served;
		int tiers = 0;
		while (tiers < UNLOCK_DAYS.length && ticks >= UNLOCK_DAYS[tiers] * DAY) {
			tiers++;
		}
		return tiers;
	}

	static Orders orders(ServerPlayer player) {
		Orders orders = player.getAttached(ORDERS);
		return orders == null ? Orders.NONE : orders;
	}

	private static void order(ServerPlayer player, int button) {
		if (!Powers.has(player, ChronoPower.INSTANCE) || !commandable(player)) {
			return;
		}
		int tier = button >= RECORD_BUTTON ? RECORD_TIER : Directive.of(button).tier();
		if (tier > unlocked(player)) {
			return; // greyed out on the screen: not yet
		}
		if (button >= SLOT_0 && button < SLOT_0 + SLOTS) {
			// another loop slot: the echo switches to that loop (if it's looping), and the next recording goes there
			Orders old = orders(player);
			int slot = button - SLOT_0;
			if (slot != old.slot()) {
				Orders now = new Orders(old.directive(), old.anchor(), slot, old.loops());
				if (now.kind() == Directive.LOOP && now.loop().isEmpty()) {
					now = now.with(Directive.STANDBY.ordinal(), Optional.empty());
				}
				set(player, now);
				tell(player, Component.translatable("message.jaymospowers.chrono_order.slot", slot + 1));
				ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.4F, 1.5F + slot * 0.1F);
				show(player);
			}
			return;
		}
		if (button == CLEAR_LOOP) {
			Orders old = orders(player);
			Orders now = old.withLoop(old.slot(), Loop.EMPTY);
			if (now.kind() == Directive.LOOP) {
				now = now.with(Directive.STANDBY.ordinal(), Optional.empty());
			}
			set(player, now);
			tell(player, Component.translatable("message.jaymospowers.chrono_order.cleared", old.slot() + 1));
			ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.4F, 0.7F);
			show(player);
			return;
		}
		if (button == RECORD_BUTTON) {
			if (RECORDINGS.containsKey(player.getUUID())) {
				stopRecording(player);
			} else {
				RECORDINGS.put(player.getUUID(), new Recording(player.level().dimension(), orders(player).slot()));
				tell(player, Component.translatable("message.jaymospowers.chrono_order.recording"));
				ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.5F, 1.6F);
				show(player);
			}
			return;
		}
		Directive directive = Directive.of(button);
		if (directive == Directive.LOOP) {
			Orders old = orders(player);
			if (old.loop().isEmpty()) {
				tell(player, Component.translatable("message.jaymospowers.chrono_order.no_loop"));
				return;
			}
			set(player, old.with(directive.ordinal(), Optional.empty()));
		} else {
			Orders old = orders(player);
			Optional<GlobalPos> anchor = directive.anchored() ? Optional.of(GlobalPos.of(player.level().dimension(), player.blockPosition().immutable()))
				: Optional.empty();
			set(player, old.with(directive.ordinal(), anchor));
		}
		RECORDINGS.remove(player.getUUID());
		tell(player, Component.translatable("message.jaymospowers.chrono_order." + directive.name().toLowerCase(java.util.Locale.ROOT)));
		ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.5F, 1.2F);
		show(player);
	}

	private static void stopRecording(ServerPlayer player) {
		Recording rec = RECORDINGS.remove(player.getUUID());
		if (rec == null) {
			return;
		}
		if (rec.steps.size() < 2) {
			tell(player, Component.translatable("message.jaymospowers.chrono_order.too_short"));
			show(player);
			return;
		}
		set(player, orders(player).withLoop(rec.slot, new Loop(List.copyOf(rec.steps), Optional.of(rec.dimension)))
			.with(Directive.LOOP.ordinal(), Optional.empty()));
		tell(player, Component.translatable("message.jaymospowers.chrono_order.loop"));
		ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.5F, 0.9F);
		show(player);
	}

	private static void set(ServerPlayer player, Orders orders) {
		player.setAttached(ORDERS, orders);
	}

	/** Brings the screen's copy up to date. */
	static void show(ServerPlayer player) {
		Orders orders = orders(player);
		int filled = 0;
		for (int i = 0; i < SLOTS; i++) {
			if (!orders.loops().get(i).steps().isEmpty()) {
				filled |= 1 << i;
			}
		}
		int value = orders.directive() | (RECORDINGS.containsKey(player.getUUID()) ? RECORDING_FLAG : 0)
			| unlocked(player) << UNLOCKED_SHIFT | orders.slot() << SLOT_SHIFT | filled << FILLED_SHIFT;
		Integer now = player.getAttached(SHOWN);
		if (now == null || now != value) {
			player.setAttached(SHOWN, value);
		}
	}

	/** Above the hotbar, never in chat. */
	private static void tell(ServerPlayer player, Component text) {
		player.sendOverlayMessage(text.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SERVE_STEP == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				serve(player);
			}
		}
		if (RECORDINGS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Recording> e : List.copyOf(RECORDINGS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Recording rec = e.getValue();
			if (player == null || !Powers.has(player, ChronoPower.INSTANCE)) {
				RECORDINGS.remove(e.getKey());
				continue;
			}
			if (player.level().dimension() != rec.dimension || ++rec.ticks >= RECORD_MAX_TICKS) {
				stopRecording(player);
				continue;
			}
			if (rec.ticks % RECORD_SAMPLE == 0 && player.onGround()) {
				BlockPos pos = player.blockPosition();
				if (rec.last == null || rec.last.distToCenterSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) >= RECORD_MIN_MOVE * RECORD_MIN_MOVE) {
					rec.last = pos.immutable();
					rec.add(new Step(Step.MOVE, rec.last, Optional.empty()));
				}
			}
		}
	}

	/** Time served as Timeless counts towards the next order; a newly unlocked one is told above the hotbar. */
	private static void serve(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || !Powers.has(player, ChronoPower.INSTANCE) || !commandable(player)) {
			return;
		}
		int before = unlocked(player);
		if (before >= UNLOCK_DAYS.length) {
			if (player.getAttached(SHOWN) == null) {
				show(player);
			}
			return;
		}
		Integer served = player.getAttached(SERVED);
		player.setAttached(SERVED, (served == null ? 0 : served) + SERVE_STEP);
		int after = unlocked(player);
		if (after > before) {
			tell(player, Component.translatable("message.jaymospowers.chrono_order.unlocked." + after));
			ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.6F, 1.4F);
		}
		show(player);
	}

	/** The power was lost: orders and any recording go with it. */
	static void forget(ServerPlayer player) {
		RECORDINGS.remove(player.getUUID());
		player.removeAttached(ORDERS);
		player.removeAttached(SERVED);
		player.removeAttached(SHOWN);
	}
}
