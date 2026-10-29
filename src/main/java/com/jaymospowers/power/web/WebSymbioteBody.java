package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.wounds.Downed;
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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.150) The symbiote's body: what it carries, wears and tastes, from Andy's ticks on the ideas list.
 * <ul>
 * <li><b>The arm</b> ({@link #ARM}, from bond {@link #ARM_BOND}): three slots in the symbiote's arm. The Arm key (default K)
 * stores what's in your main hand (an empty hand draws the picked slot back out); Sneak + the key cycles the picked slot.
 * The stored things hang on the suit (everyone sees them: {@code WebBodyLayer}). The Arm Use key (default U), the tendril
 * working the picked slot: a <i>tool</i> (anything with a tool component) is held to the block you look at and breaks it
 * over time, at the speed that tool would, wearing down; <i>food</i> is eaten (meat feeds the symbiote under the same rules
 * as the C key; the rest feeds you); <i>anything else</i> (blocks, buckets, bone meal) is used on the block you look at,
 * every {@link #PLACE_EVERY} ticks while held. Dying drops the arm like armour (kept with keepInventory); the bond ending
 * puts it back in your inventory.</li>
 * <li><b>The mantle</b> ({@link WebSymbioteState#MANTLE}, from bond {@link #MANTLE_BOND}): the Mantle key (default N)
 * unfurls a cape of symbiote or furls it away ({@link #MANTLE_AT} times the animation). Unfurled it slows a long fall to
 * {@link #FALL_CAP} blocks a tick (the client does the slowing, {@code WebSymbioteBodyClient}; a descent that slow is the
 * one controlled descent fall damage forgives) and a Shroud lasts {@link #MANTLE_SHROUD_BONUS} ticks longer under it.</li>
 * <li><b>Night hunter</b> (the black strain): stronger and faster where it's dark ({@link #DARK_LIGHT}), sluggish and weak
 * in clear daylight under the open sky ({@link #tickTraits}). The red strain hears hearts and footfalls through walls
 * ({@code WebRedSense}, client only). The white one keeps its mercy and refuses carrion ({@link #refuses}).</li>
 * <li><b>Feeding by hand</b> ({@link #onUltimate}, the C key): with meat in either hand C feeds it (raw meat in full, cooked
 * meat at {@link #COOKED_FEED_PER_NUTRITION} a point of nutrition instead of five), or refuses it while it is
 * {@link #FULL_AT} full; with a creature or player held (a tendril, the hand, your arms) C makes it hold that and
 * Consume it: finished off first, then its corpse taken and eaten ({@link WebSymbiote#consumeHeld}).</li>
 * </ul>
 */
public final class WebSymbioteBody {
	// ---- tuning: unlocks ----
	/** The bond the arm, the mantle and the red sense open at. */
	public static final int ARM_BOND = 25, MANTLE_BOND = 40, SENSE_BOND = 20;

	// ---- tuning: the arm ----
	public static final int ARM_SLOTS = 3;
	/** How far the tendril reaches to work a block (blocks from the eyes). */
	public static final double ARM_REACH = 5.5;
	/** A held tool works at this share of the speed it would in your hand. */
	static final float ARM_SPEED = 1.0F;
	/** Held on a block that isn't a tool's work (blocks to set, bone meal): once every this many ticks. */
	static final int PLACE_EVERY = 5;
	/** Working a block: the hit sound and dust every this many ticks; the tool's wear and your hunger come from the break itself. */
	static final int HIT_EVERY = 4;
	static final float BREAK_EXHAUSTION = 0.005F;
	/** The tendril stays out this long after each thing it does. */
	static final int ARM_OUT_TICKS = 24;

	// ---- tuning: feeding ----
	/** Satiety at or over this and it refuses food (it doesn't eat the item). */
	public static final int FULL_AT = 90;
	/** Cooked meat feeds it this much per point of nutrition (raw is {@link WebSymbiote#MEAT_FEED_PER_NUTRITION}). */
	public static final int COOKED_FEED_PER_NUTRITION = 3;
	/** The calm a hand-fed meal gives (stress taken off). */
	static final int FEED_CALM = 3;
	/** The C key's meals come no faster than this (ticks). */
	static final int FEED_GAP = 6;
	/** A refusal speaks no more often than this (ticks). */
	static final int REFUSE_GAP = 80;
	static final int LINES = 3;
	public static final List<Item> COOKED_MEAT = List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
		Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON);
	/** What the white strain won't touch (and the corpses of the undead, {@link #carrion}). */
	public static final List<Item> CARRION = List.of(Items.ROTTEN_FLESH);

	// ---- tuning: the mantle ----
	/** The slowest it lets a long fall go (blocks a tick; under half a block, so it's the forgiven kind of descent). */
	public static final double FALL_CAP = 0.35;
	/** It only catches the air after falling this far (blocks), so a hop or a step doesn't. */
	public static final float FALL_MIN = 3.0F;
	/** Unfurling or furling takes this long (ticks), drawn by the layer. */
	public static final int MANTLE_TICKS = 10;
	public static final int MANTLE_GAP = 10;
	/** A Shroud lasts this many ticks longer with the mantle out. */
	public static final int MANTLE_SHROUD_BONUS = 50;

	// ---- tuning: the black strain's night ----
	/** At or under this much light where you stand is dark enough for the night hunter. */
	public static final int DARK_LIGHT = 4;
	/** Attribute changes (multiplied): in the dark, then in the sun. */
	public static final double NIGHT_SPEED = 0.12, NIGHT_DAMAGE = 0.30, SUN_SPEED = -0.12, SUN_DAMAGE = -0.25;
	static final int TRAIT_EVERY = 10;
	/** It remarks on the change no more often than this (ticks). */
	static final int TRAIT_WHISPER_GAP = 1800;

	private static final Identifier NIGHT_SPEED_ID = JaymosPowers.id("web_symbiote_night_speed");
	private static final Identifier NIGHT_DAMAGE_ID = JaymosPowers.id("web_symbiote_night_damage");

	/** What the arm holds: {@code ARM_SLOTS} stacks (empty ones included), the picked slot, and whether the tendril is working. */
	public record Arm(List<ItemStack> stacks, int selected, boolean using) {
		public static final Arm EMPTY = new Arm(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), 0, false);
		public static final Codec<Arm> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Arm::stacks),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(Arm::selected),
			Codec.BOOL.optionalFieldOf("using", false).forGetter(Arm::using)).apply(i, Arm::of));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Arm> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_LIST_STREAM_CODEC, Arm::stacks,
			ByteBufCodecs.VAR_INT, Arm::selected,
			ByteBufCodecs.BOOL, Arm::using,
			Arm::of);

		/** Always exactly {@link #ARM_SLOTS} slots, the pick in range (a save from a changed mod, or a short list). */
		static Arm of(List<ItemStack> stacks, int selected, boolean using) {
			List<ItemStack> fixed = new ArrayList<>(ARM_SLOTS);
			for (int i = 0; i < ARM_SLOTS; i++) {
				fixed.add(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
			}
			return new Arm(List.copyOf(fixed), Mth.clamp(selected, 0, ARM_SLOTS - 1), using);
		}

		public ItemStack get(int slot) {
			return slot >= 0 && slot < this.stacks.size() ? this.stacks.get(slot) : ItemStack.EMPTY;
		}

		/** The picked slot's stack. */
		public ItemStack picked() {
			return get(this.selected);
		}

		public boolean isEmpty() {
			for (ItemStack stack : this.stacks) {
				if (!stack.isEmpty()) {
					return false;
				}
			}
			return true;
		}

		Arm with(int slot, ItemStack stack) {
			List<ItemStack> next = new ArrayList<>(this.stacks);
			next.set(slot, stack);
			return of(next, this.selected, this.using);
		}

		Arm pick(int slot) {
			return of(this.stacks, slot, this.using);
		}

		Arm working(boolean on) {
			return of(this.stacks, this.selected, on);
		}
	}

	/** The arm's slots. Saved, kept through death (dropped there like armour unless keepInventory), synced to everyone, who see them on the suit. */
	public static final AttachmentType<Arm> ARM = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_arm"),
		builder -> builder.persistent(Arm.CODEC).copyOnDeath().syncWith(Arm.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** When the mantle was last unfurled or furled (game time), for the layer's animation. Synced to everyone. */
	public static final AttachmentType<Long> MANTLE_AT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_mantle_at"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** Which one-time hints it has given ({@link #HINT_ARM} ...). Saved, kept through death. */
	private static final AttachmentType<Integer> HINTS = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_body_hints"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	private static final int HINT_ARM = 1, HINT_MANTLE = 2, HINT_SENSE = 4;

	/** Client → server: the Arm key. {@link #STOW}: store or draw; {@link #CYCLE}: pick the next slot. */
	public record ArmKey(int action) implements CustomPacketPayload {
		public static final Type<ArmKey> TYPE = new Type<>(JaymosPowers.id("web_symbiote_arm_key"));
		public static final StreamCodec<ByteBuf, ArmKey> CODEC = ByteBufCodecs.VAR_INT.map(ArmKey::new, ArmKey::action);

		@Override
		public Type<ArmKey> type() {
			return TYPE;
		}
	}

	/** Client → server: the Arm Use key went down or up. */
	public record ArmUse(boolean down) implements CustomPacketPayload {
		public static final Type<ArmUse> TYPE = new Type<>(JaymosPowers.id("web_symbiote_arm_use"));
		public static final StreamCodec<ByteBuf, ArmUse> CODEC = ByteBufCodecs.BOOL.map(ArmUse::new, ArmUse::down);

		@Override
		public Type<ArmUse> type() {
			return TYPE;
		}
	}

	/** Client → server: the Mantle key. */
	public record MantleKey() implements CustomPacketPayload {
		public static final Type<MantleKey> TYPE = new Type<>(JaymosPowers.id("web_symbiote_mantle_key"));
		public static final StreamCodec<ByteBuf, MantleKey> CODEC = StreamCodec.unit(new MantleKey());

		@Override
		public Type<MantleKey> type() {
			return TYPE;
		}
	}

	public static final int STOW = 0, CYCLE = 1;

	/** What the tendril does with a stored thing. */
	private enum Kind {
		NONE,
		TOOL,
		FOOD,
		USE
	}

	/** A held Arm Use: the block being worked and how far along it is, and when a held placing next goes. */
	private static final class Work {
		BlockPos target;
		float progress;
		long placeAt;
		long hitAt;
		int crackId;
	}

	private static final Map<UUID, Work> WORKING = new HashMap<>();
	private static final Map<UUID, Long> LAST_MANTLE = new HashMap<>();
	private static final Map<UUID, Long> LAST_FEED = new HashMap<>();
	private static final Map<UUID, Long> LAST_REFUSE = new HashMap<>();
	private static final Map<UUID, Long> LAST_TRAIT = new HashMap<>();
	/** 0 none, 1 night, 2 sun: what the traits are doing for each player now. */
	private static final Map<UUID, Integer> TRAIT = new HashMap<>();
	private static final Map<UUID, Double> LAST_Y = new HashMap<>();

	private WebSymbioteBody() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ArmKey.TYPE, ArmKey.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ArmUse.TYPE, ArmUse.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MantleKey.TYPE, MantleKey.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ArmKey.TYPE, (payload, ctx) -> armKey(ctx.player(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(ArmUse.TYPE, (payload, ctx) -> armUse(ctx.player(), payload.down()));
		ServerPlayNetworking.registerGlobalReceiver(MantleKey.TYPE, (payload, ctx) -> mantleKey(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
	}

	// ---- shared queries (both sides: the bond and the arm are synced) -------------------------------------------------

	/** The arm's contents (empty if none). */
	public static Arm arm(Player player) {
		return player.getAttachedOrElse(ARM, Arm.EMPTY);
	}

	/** The arm's slots work for you: its bond, or everything lent to you (creative has it). */
	public static boolean armUnlocked(Player player) {
		return WebSymbiote.isBonded(player) && (player.isCreative() || WebSymbiote.empowered(player) || WebSymbiote.state(player).bond() >= ARM_BOND);
	}

	public static boolean mantleUnlocked(Player player) {
		return WebSymbiote.isBonded(player) && (player.isCreative() || WebSymbiote.empowered(player) || WebSymbiote.state(player).bond() >= MANTLE_BOND);
	}

	/** The red strain's sense is on for this player (bonded to the red one, its bond high enough). */
	public static boolean senses(Player player) {
		WebSymbioteState state = WebSymbiote.state(player);
		return state.bonded() && state.kind() == WebSymbioteStrain.RED && (player.isCreative() || state.bond() >= SENSE_BOND);
	}

	/** The mantle is unfurled. */
	public static boolean mantleOut(Player player) {
		return WebSymbiote.isToggled(player, WebSymbioteState.MANTLE);
	}

	/**
	 * The mantle may catch the air now (both sides): unfurled, and you're not already on a web, in wings, on a wall,
	 * gliding, swimming or flying.
	 */
	public static boolean mantleCatches(Player player) {
		return mantleOut(player) && !player.onGround() && !player.getAbilities().flying && !player.isFallFlying() && !player.isInWater()
			&& !player.onClimbable() && !player.isPassenger() && player.getAttached(WebPower.LINE) == null && player.getAttached(WebPower.LINE_OFF) == null
			&& !Boolean.TRUE.equals(player.getAttached(WebPower.WINGS)) && player.getAttached(WebSymbiote.WALL) == null;
	}

	/** Meat it eats from a hand (raw or cooked). */
	public static boolean isMeat(ItemStack stack) {
		return !stack.isEmpty() && (WebSymbiote.RAW_MEAT.contains(stack.getItem()) || COOKED_MEAT.contains(stack.getItem()));
	}

	/** Its taste for the item: the white one won't touch carrion. */
	public static boolean refuses(Player player, ItemStack stack) {
		return WebSymbiote.state(player).kind() == WebSymbioteStrain.WHITE && CARRION.contains(stack.getItem());
	}

	/** The undead's corpses are carrion. */
	public static boolean carrion(@Nullable Entity entity) {
		if (entity == null) {
			return false;
		}
		EntityType<?> type = com.jaymospowers.power.gravity.GravityPower.isCorpse(entity) ? KitchenCompat.sourceType(entity) : entity.getType();
		return type != null && type.builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** The white one won't eat this body (carrion), for the feeding's pick of corpse and prey. */
	static boolean refusesBody(Player player, Entity body) {
		return WebSymbiote.state(player).kind() == WebSymbioteStrain.WHITE && carrion(body);
	}

	// ---- gates --------------------------------------------------------------------------------------------------------

	/** The symbiote will listen right now (bonded, awake, settled, not busy with the wheel). */
	private static boolean ready(ServerPlayer player) {
		return WebSymbiote.isBonded(player) && player.isAlive() && !player.isSpectator() && !WebSymbioteBond.dormant(player)
			&& !WebSymbiote.driving(player) && !WebSymbiote.roaring(player) && !Downed.isDowned(player) && !WebSymbioteStrains.asleep(player);
	}

	private static boolean due(Map<UUID, Long> last, ServerPlayer player, int gap) {
		long now = player.level().getGameTime();
		Long was = last.get(player.getUUID());
		if (was != null && now - was >= 0 && now - was < gap) {
			return false;
		}
		last.put(player.getUUID(), now);
		return true;
	}

	/** One of its three lines for {@code base} in this strain's voice ({@code base.N}, {@code white.base.N}, {@code red.base.N}). */
	private static void say(ServerPlayer player, String base, Object... args) {
		WebSymbiote.whisper(player, WebSymbiote.state(player).kind().lines() + base + "." + (1 + player.getRandom().nextInt(LINES)), args);
	}

	private static void locked(ServerPlayer player, String key, int bond) {
		if (due(LAST_REFUSE, player, REFUSE_GAP)) {
			WebSymbiote.whisper(player, key, bond);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
		}
	}

	// ---- the arm ------------------------------------------------------------------------------------------------------

	private static void setArm(ServerPlayer player, Arm arm) {
		if (arm.isEmpty() && arm.selected() == 0 && !arm.using()) {
			player.removeAttached(ARM);
		} else {
			player.setAttached(ARM, arm);
		}
	}

	private static void armKey(ServerPlayer player, int action) {
		if (!ready(player)) {
			return;
		}
		if (!armUnlocked(player)) {
			locked(player, "arm_locked", ARM_BOND);
			return;
		}
		Arm arm = arm(player);
		ServerLevel level = player.level();
		if (action == CYCLE) {
			setArm(player, arm.pick((arm.selected() + 1) % ARM_SLOTS));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5F, 1.5F);
			return;
		}
		ItemStack hand = player.getMainHandItem();
		if (!hand.isEmpty()) {
			// store: the picked slot if it's free, else the first free one, else swap with the picked one
			int slot = arm.get(arm.selected()).isEmpty() ? arm.selected() : -1;
			for (int i = 0; i < ARM_SLOTS && slot < 0; i++) {
				if (arm.get(i).isEmpty()) {
					slot = i;
				}
			}
			ItemStack back = ItemStack.EMPTY;
			if (slot < 0) {
				slot = arm.selected();
				back = arm.get(slot).copy();
			}
			setArm(player, arm.with(slot, hand.copy()).pick(slot));
			player.setItemInHand(InteractionHand.MAIN_HAND, back);
			stowEffects(player, true);
			return;
		}
		// draw: the picked slot, else the next one that holds something
		int slot = -1;
		for (int i = 0; i < ARM_SLOTS; i++) {
			int at = (arm.selected() + i) % ARM_SLOTS;
			if (!arm.get(at).isEmpty()) {
				slot = at;
				break;
			}
		}
		if (slot < 0) {
			if (due(LAST_REFUSE, player, REFUSE_GAP)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote.arm_empty").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return;
		}
		ItemStack out = arm.get(slot).copy();
		setArm(player, arm.with(slot, ItemStack.EMPTY).pick(slot));
		player.setItemInHand(InteractionHand.MAIN_HAND, out);
		stowEffects(player, false);
	}

	private static void stowEffects(ServerPlayer player, boolean in) {
		ServerLevel level = player.level();
		player.setAttached(WebSymbiote.TENDRILS_OUT, level.getGameTime() + ARM_OUT_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7F, in ? 0.8F : 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.5F, in ? 0.7F : 1.0F);
		level.sendParticles(WebSymbiote.dust(0.9F), player.getX(), player.getY() + 0.9, player.getZ(), 8, 0.25, 0.35, 0.25, 0.0);
	}

	private static Kind kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return Kind.NONE;
		}
		if (stack.has(DataComponents.TOOL)) {
			return Kind.TOOL;
		}
		if (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE) || isMeat(stack)) {
			return Kind.FOOD;
		}
		return Kind.USE;
	}

	private static void armUse(ServerPlayer player, boolean down) {
		if (!down) {
			stopWork(player);
			return;
		}
		if (!ready(player)) {
			return;
		}
		if (!armUnlocked(player)) {
			locked(player, "arm_locked", ARM_BOND);
			return;
		}
		Arm arm = arm(player);
		ItemStack stack = arm.picked();
		Kind kind = kind(stack);
		if (kind == Kind.NONE) {
			if (due(LAST_REFUSE, player, REFUSE_GAP)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote.arm_empty").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return;
		}
		stopWork(player);
		if (kind == Kind.FOOD) {
			eatFromArm(player, arm);
			return;
		}
		Work work = new Work();
		work.crackId = player.getId() * 47 + 6000;
		WORKING.put(player.getUUID(), work);
		player.setAttached(ARM, arm.working(true));
		if (kind == Kind.USE) {
			useStored(player, work);
		}
	}

	private static void stopWork(ServerPlayer player) {
		Work work = WORKING.remove(player.getUUID());
		if (work != null) {
			clearCrack(player, work);
		}
		Arm arm = player.getAttached(ARM);
		if (arm != null && arm.using()) {
			setArm(player, arm.working(false));
		}
		dropLatch(player);
	}

	private static void clearCrack(ServerPlayer player, Work work) {
		if (work.target != null) {
			player.level().destroyBlockProgress(work.crackId, work.target, -1);
			work.target = null;
		}
		work.progress = 0.0F;
	}

	/** The cord from the off-hand shoulder to the block it works, for everyone to see ({@link WebTendrils#TUG}). */
	private static void latch(ServerPlayer player, Vec3 at) {
		if (WebTendrils.tugging(player, WebNet.OFF)) {
			return;
		}
		player.setAttached(WebTendrils.TUG, player.getAttachedOrElse(WebTendrils.TUG, WebTendrils.Latch.NONE).with(WebNet.OFF, at));
	}

	private static void dropLatch(ServerPlayer player) {
		WebTendrils.Latch latch = player.getAttached(WebTendrils.TUG);
		if (latch != null && latch.has(WebNet.OFF) && !WebTendrils.tugging(player, WebNet.OFF)) {
			WebTendrils.Latch next = latch.without(WebNet.OFF);
			if (next.mask() == 0) {
				player.removeAttached(WebTendrils.TUG);
			} else {
				player.setAttached(WebTendrils.TUG, next);
			}
		}
	}

	private static @Nullable BlockHitResult aim(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(ARM_REACH)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private static boolean mayWork(ServerPlayer player, BlockPos pos) {
		return player.level().mayInteract(player, pos) && !player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer());
	}

	/** Runs {@code action} with {@code stack} swapped into the picked hotbar slot (the game reads tools and uses from there); what is left of it comes back. */
	private static ItemStack asHeld(ServerPlayer player, ItemStack stack, java.util.function.Consumer<ItemStack> action) {
		Inventory inventory = player.getInventory();
		int slot = inventory.getSelectedSlot();
		ItemStack real = inventory.getItem(slot);
		inventory.setItem(slot, stack);
		ItemStack after;
		try {
			action.accept(stack);
		} finally {
			after = inventory.getItem(slot);
			inventory.setItem(slot, real);
		}
		return after;
	}

	/** A tool held to the block you look at, over time. */
	private static void mine(ServerPlayer player, Work work, Arm arm, long now) {
		ServerLevel level = player.level();
		BlockHitResult hit = aim(player);
		if (hit == null || !mayWork(player, hit.getBlockPos())) {
			clearCrack(player, work);
			dropLatch(player);
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
			clearCrack(player, work);
			dropLatch(player);
			return;
		}
		if (!pos.equals(work.target)) {
			clearCrack(player, work);
			work.target = pos;
		}
		ItemStack tool = arm.picked();
		float[] step = new float[1];
		asHeld(player, tool, held -> step[0] = state.getDestroyProgress(player, level, pos));
		if (player.isCreative()) {
			step[0] = 1.0F;
		}
		work.progress += step[0] * ARM_SPEED;
		latch(player, hit.getLocation());
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + ARM_OUT_TICKS);
		if (work.progress < 1.0F) {
			level.destroyBlockProgress(work.crackId, pos, Math.min(9, (int)(work.progress * 10.0F)));
			if (now >= work.hitAt) {
				work.hitAt = now + HIT_EVERY;
				level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.7F, 0.6F);
				com.jaymospowers.core.Debris.puff(level, pos, state, 0.3F);
			}
			return;
		}
		// through: broken by the tool in the tendril, which wears as it would in a hand
		level.destroyBlockProgress(work.crackId, pos, -1);
		boolean[] broke = new boolean[1];
		ItemStack left = asHeld(player, tool, held -> broke[0] = player.gameMode.destroyBlock(pos));
		work.target = null;
		work.progress = 0.0F;
		if (broke[0]) {
			player.causeFoodExhaustion(BREAK_EXHAUSTION);
		}
		Arm current = arm(player);
		setArm(player, current.with(current.selected(), left));
		if (left.isEmpty()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
			stopWork(player);
		}
	}

	/** Something that isn't a tool's work, used on the block you look at (a block set, a bucket emptied, bone meal). */
	private static void useStored(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		work.placeAt = now + PLACE_EVERY;
		BlockHitResult hit = aim(player);
		Arm arm = arm(player);
		ItemStack stack = arm.picked();
		if (stack.isEmpty()) {
			return;
		}
		boolean[] done = new boolean[1];
		ItemStack after;
		if (hit != null) {
			if (!mayWork(player, hit.getBlockPos())) {
				return;
			}
			// (sneaking, so the block under the tendril is built against, not opened)
			after = asHeld(player, stack, held -> {
				boolean shift = player.isShiftKeyDown();
				player.setShiftKeyDown(true);
				try {
					done[0] = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit).consumesAction();
				} finally {
					player.setShiftKeyDown(shift);
				}
			});
			latch(player, hit.getLocation());
		} else if (!(stack.getItem() instanceof BlockItem)) {
			after = asHeld(player, stack, held -> done[0] = player.gameMode.useItem(player, level, held, InteractionHand.MAIN_HAND).consumesAction());
		} else {
			return;
		}
		Arm current = arm(player);
		setArm(player, current.with(current.selected(), after));
		if (done[0]) {
			player.setAttached(WebSymbiote.TENDRILS_OUT, now + ARM_OUT_TICKS);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.5F, 1.2F);
		}
	}

	/** Food in the arm: meat feeds the symbiote (its rules), the rest feeds you. */
	private static void eatFromArm(ServerPlayer player, Arm arm) {
		ItemStack stack = arm.picked();
		ServerLevel level = player.level();
		if (isMeat(stack)) {
			ItemStack copy = stack.copy();
			if (eatMeat(player, copy, null)) {
				setArm(player, arm.with(arm.selected(), copy));
				player.setAttached(WebSymbiote.TENDRILS_OUT, level.getGameTime() + ARM_OUT_TICKS);
			}
			return;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null && !player.canEat(food.canAlwaysEat())) {
			if (due(LAST_REFUSE, player, REFUSE_GAP)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote.arm_not_hungry").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return;
		}
		ItemStack copy = stack.copy();
		ItemStack result = copy.finishUsingItem(level, player);
		setArm(player, arm.with(arm.selected(), result));
		player.setAttached(WebSymbiote.TENDRILS_OUT, level.getGameTime() + ARM_OUT_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.5F, 0.9F);
	}

	// ---- the mantle ---------------------------------------------------------------------------------------------------

	private static void mantleKey(ServerPlayer player) {
		if (!ready(player)) {
			return;
		}
		if (!mantleUnlocked(player)) {
			locked(player, "mantle_locked", MANTLE_BOND);
			return;
		}
		if (!due(LAST_MANTLE, player, MANTLE_GAP)) {
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		boolean on = !state.has(WebSymbioteState.MANTLE);
		WebSymbiote.set(player, state.withFlag(WebSymbioteState.MANTLE, on));
		ServerLevel level = player.level();
		player.setAttached(MANTLE_AT, level.getGameTime());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.9F,
			on ? 0.7F : 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6F, on ? 0.6F : 1.0F);
		level.sendParticles(WebSymbiote.dust(1.1F), player.getX(), player.getY() + 1.3, player.getZ(), 14, 0.35, 0.4, 0.35, 0.0);
		WebSymbiote.whisper(player, on ? "mantle_on" : "mantle_off");
	}

	/** Shroud with the mantle out lasts longer (from {@link WebSymbiote}'s Shroud). */
	static int shroudBonus(ServerPlayer player) {
		return mantleOut(player) ? MANTLE_SHROUD_BONUS : 0;
	}

	// ---- feeding by hand (the C key) ----------------------------------------------------------------------------------

	/**
	 * C, before anything else it does: feeds it the meat in your hands, or Consumes what you hold. True when the press was
	 * taken (so the ascended Web Strike doesn't also fire); false when it doesn't apply.
	 */
	public static boolean onUltimate(ServerPlayer player, boolean down) {
		if (!WebSymbiote.isBonded(player)) {
			return false;
		}
		if (!down) {
			return false;
		}
		LivingEntity held = heldCreature(player);
		if (held != null) {
			if (ready(player)) {
				WebSymbiote.consumeHeld(player, held);
			} else if (WebSymbioteBond.dormant(player)) {
				WebSymbioteBond.refuse(player);
			}
			return true;
		}
		if (KitchenCompat.carriedCorpse(player) != null) {
			if (ready(player)) {
				WebSymbiote.use(player, WebSymbiote.CONSUME);
			} else if (WebSymbioteBond.dormant(player)) {
				WebSymbioteBond.refuse(player);
			}
			return true;
		}
		InteractionHand hand = isMeat(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : isMeat(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return false;
		}
		if (!ready(player)) {
			if (WebSymbioteBond.dormant(player)) {
				WebSymbioteBond.refuse(player);
			}
			return true;
		}
		if (!due(LAST_FEED, player, FEED_GAP)) {
			return true;
		}
		eatMeat(player, player.getItemInHand(hand), hand);
		return true;
	}

	/** Something living you hold: a tendril's grip, the left hand's hold, or your arms. */
	private static @Nullable LivingEntity heldCreature(ServerPlayer player) {
		LivingEntity hand = WebHeldGrab.heldEntity(player);
		if (hand != null && hand.isAlive()) {
			return hand;
		}
		WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
		for (int id : new int[] {grips.left(), grips.right()}) {
			if (id >= 0 && player.level().getEntity(id) instanceof LivingEntity living && living.isAlive() && living != player) {
				return living;
			}
		}
		Carry.Carrying carrying = player.getAttached(Carry.CARRYING);
		if (carrying != null && player.level().getEntity(carrying.target()) instanceof LivingEntity living && living.isAlive()) {
			return living;
		}
		return null;
	}

	/**
	 * It turns this meal down, with a word (no more often than {@link #REFUSE_GAP}): carrion for the white strain, anything
	 * at all while it's {@link #FULL_AT} full. Nothing is eaten; for the ordinary way of eating, you eat it yourself then.
	 */
	static boolean turnsDown(ServerPlayer player, ItemStack stack) {
		boolean carrion = refuses(player, stack);
		if (!carrion && WebSymbiote.state(player).satiety() < FULL_AT) {
			return false;
		}
		if (due(LAST_REFUSE, player, REFUSE_GAP)) {
			say(player, carrion ? "carrion" : "full");
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.5F);
		}
		return true;
	}

	/**
	 * Feeds it one of {@code stack} (shrunk by one unless you're in creative): raw meat in full, cooked meat at less. It
	 * turns the meal down when it's full ({@link #FULL_AT}), and the white strain turns carrion down; neither is eaten.
	 * {@code hand} is the hand that swings (null: the arm's own tendril did it). True when it ate.
	 */
	static boolean eatMeat(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand) {
		ServerLevel level = player.level();
		if (turnsDown(player, stack)) {
			return false;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		int nutrition = Math.max(1, food == null ? 2 : food.nutrition());
		boolean raw = WebSymbiote.RAW_MEAT.contains(stack.getItem());
		WebSymbiote.feed(player, nutrition * (raw ? WebSymbiote.MEAT_FEED_PER_NUTRITION : COOKED_FEED_PER_NUTRITION), FEED_CALM);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		if (hand != null) {
			player.swing(hand, SwingAnimation.DEFAULT, true);
		}
		player.setAttached(WebSymbiote.TENDRILS_OUT, level.getGameTime() + ARM_OUT_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.5F, 0.7F);
		Vec3 mouth = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.4));
		level.sendParticles(WebSymbiote.dust(1.0F), mouth.x, mouth.y - 0.1, mouth.z, 8, 0.1, 0.1, 0.1, 0.0);
		WebSymbiote.narrate(player, "feed");
		return true;
	}

	// ---- every tick ---------------------------------------------------------------------------------------------------

	private static void tick(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded()) {
			if (!arm(player).isEmpty() || player.getAttached(ARM) != null) {
				// the bond is over: what the arm carried comes back to you
				giveBack(player);
			}
			if (TRAIT.containsKey(player.getUUID())) {
				clearTraits(player);
			}
			return;
		}
		Work work = WORKING.get(player.getUUID());
		if (work != null) {
			tickWork(player, work);
		} else if (arm(player).using()) {
			setArm(player, arm(player).working(false));
		}
		if (player.tickCount % TRAIT_EVERY == 0) {
			tickTraits(player, state);
			tickHints(player);
		}
		tickFall(player, state);
	}

	private static void tickWork(ServerPlayer player, Work work) {
		if (!ready(player) || !armUnlocked(player)) {
			stopWork(player);
			return;
		}
		Arm arm = arm(player);
		Kind kind = kind(arm.picked());
		long now = player.level().getGameTime();
		switch (kind) {
			case TOOL -> mine(player, work, arm, now);
			case USE -> {
				if (now >= work.placeAt) {
					useStored(player, work);
				}
			}
			default -> stopWork(player);
		}
	}

	/** The mantle out and a long fall that has slowed to the cap (the client does the slowing): the descent is controlled, so it isn't a fall. */
	private static void tickFall(ServerPlayer player, WebSymbioteState state) {
		UUID id = player.getUUID();
		double y = player.getY();
		Double last = LAST_Y.put(id, y);
		if (!state.has(WebSymbioteState.MANTLE) || last == null || player.fallDistance < FALL_MIN) {
			return;
		}
		double drop = last - y;
		if (drop > 0.0 && drop < 0.5 && mantleCatches(player)) {
			player.resetFallDistance();
		}
	}

	/** The black strain's night: stronger and faster in the dark, sluggish and weak in clear daylight under the open sky. */
	private static void tickTraits(ServerPlayer player, WebSymbioteState state) {
		int mode = 0;
		if (state.kind() == WebSymbioteStrain.BLACK && !WebSymbioteBond.dormant(player) && player.isAlive()) {
			ServerLevel level = player.level();
			BlockPos feet = player.blockPosition();
			if (level.getMaxLocalRawBrightness(feet) <= DARK_LIGHT) {
				mode = 1;
			} else if (level.dimensionType().hasSkyLight() && level.isBrightOutside() && level.canSeeSky(feet.above())) {
				mode = 2;
			}
		}
		int was = TRAIT.getOrDefault(player.getUUID(), 0);
		if (mode == was) {
			return;
		}
		TRAIT.put(player.getUUID(), mode);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (mode == 0) {
			clearTraits(player);
			TRAIT.put(player.getUUID(), 0);
			return;
		}
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(NIGHT_SPEED_ID, mode == 1 ? NIGHT_SPEED : SUN_SPEED,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (damage != null) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(NIGHT_DAMAGE_ID, mode == 1 ? NIGHT_DAMAGE : SUN_DAMAGE,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (due(LAST_TRAIT, player, TRAIT_WHISPER_GAP)) {
			WebSymbiote.whisper(player, (mode == 1 ? "night." : "sun.") + (1 + player.getRandom().nextInt(LINES)));
		}
	}

	private static void clearTraits(ServerPlayer player) {
		TRAIT.remove(player.getUUID());
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(NIGHT_SPEED_ID);
		}
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(NIGHT_DAMAGE_ID);
		}
	}

	/** The first time each of its new talents opens, it says so (once, ever). */
	private static void tickHints(ServerPlayer player) {
		if (WebSymbioteBond.dormant(player)) {
			return;
		}
		if (armUnlocked(player)) {
			hint(player, HINT_ARM, "arm_unlocked");
		}
		if (mantleUnlocked(player)) {
			hint(player, HINT_MANTLE, "mantle_unlocked");
		}
		if (senses(player)) {
			hint(player, HINT_SENSE, "red.sense");
		}
	}

	private static void hint(ServerPlayer player, int bit, String key) {
		int seen = player.getAttachedOrElse(HINTS, 0);
		if ((seen & bit) != 0) {
			return;
		}
		player.setAttached(HINTS, seen | bit);
		WebSymbiote.whisper(player, key, Component.keybind(bit == HINT_ARM ? "key.jaymospowers.web_arm" : "key.jaymospowers.web_mantle"));
	}

	// ---- dying, and the bond ending ----------------------------------------------------------------------------------

	/** Dying: the arm is dropped like armour (kept with keepInventory, where the attachment is copied over). */
	private static void onDeath(ServerPlayer player) {
		stopWork(player);
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		Arm arm = arm(player);
		for (ItemStack stack : arm.stacks()) {
			if (!stack.isEmpty()) {
				Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy());
			}
		}
		player.removeAttached(ARM);
	}

	/** The bond is gone: what the arm held goes back into your inventory (what doesn't fit, at your feet). */
	private static void giveBack(ServerPlayer player) {
		stopWork(player);
		Arm arm = arm(player);
		player.removeAttached(ARM);
		for (ItemStack stack : arm.stacks()) {
			if (!stack.isEmpty()) {
				ItemStack rest = stack.copy();
				if (!player.getInventory().add(rest) && !rest.isEmpty()) {
					Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), rest);
				}
			}
		}
	}

	private static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		stopWork(player);
		WORKING.remove(id);
		LAST_MANTLE.remove(id);
		LAST_FEED.remove(id);
		LAST_REFUSE.remove(id);
		LAST_TRAIT.remove(id);
		TRAIT.remove(id);
		LAST_Y.remove(id);
	}
}
