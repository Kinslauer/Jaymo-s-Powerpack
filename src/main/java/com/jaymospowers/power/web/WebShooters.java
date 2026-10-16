package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * (0.12.3) Web shooters (Andy liked the web shooter idea from the swinging video; our own take, our own art): a pair of
 * silver wrist shooters, each holding one Web Cartridge of web fluid. Built as an <b>optional upgrade</b> (the default
 * recommended while Andy chooses; {@link #REQUIRED} switches to "no shooters, no webs"):
 * <ul>
 * <li>Right-click the pair to strap them on (one item is the pair; it's kept in a synced player attachment, saved, and
 * dropped where you die like armour unless keepInventory holds). Sneak + right-click with an empty main hand, aiming
 * at nothing, takes them off into the hand, cartridges and all.</li>
 * <li>Each wrist holds one cartridge of {@link #CARTRIDGE_SHOTS} webs. Right-click a cartridge while wearing them to
 * load the emptier wrist (the old cartridge comes back, or its empty bottle); Sneak + right-click the pair in hand to
 * fill its empty wrists from your inventory.</li>
 * <li>Every web the power fires from a hand (swing webs, Zip, Yank, weblines, hitches) uses one shot from that wrist:
 * it leaves the nozzle with a mechanical click, as a thick braided line, reaching {@link #RANGE_SCALE} further and
 * reeling {@link #PULL_SCALE} harder. A dry wrist clicks empty and the power's own organic web goes out instead.</li>
 * <li>The symbiote's suit ignores them: its webs are its own.</li>
 * </ul>
 */
public final class WebShooters {
	// ===== the knobs ================================================================================================
	/** Webs in one full cartridge. */
	public static final int CARTRIDGE_SHOTS = 64;
	/** A loaded shooter's web reaches this much further, and reels in (swing) and zips this much harder. */
	public static final double RANGE_SCALE = 1.25;
	public static final double PULL_SCALE = 1.15;
	/**
	 * Off (the default): shooters are an optional upgrade, and without them (or dry) the power still fires its organic
	 * webs. On: no shooter fluid, no web from that hand (swing webs, Zip and Yank click dry and fail); the suit still
	 * fires its own.
	 */
	public static final boolean REQUIRED = false;
	/** Full cartridges stack this high (a part-used one stacks only with the same fill). */
	public static final int CARTRIDGE_STACK = 16;
	// ================================================================================================================

	/**
	 * What a pair of shooters holds: the shots left in each wrist's cartridge ({@code main} the main hand's wrist,
	 * {@code off} the other), -1 with no cartridge in. {@code nozzle} (bits by hand, worn only): whether that hand's
	 * web now out came from the shooter, so everyone draws it braided.
	 */
	public record Load(int main, int off, int nozzle) {
		public static final Load EMPTY = new Load(-1, -1, 0);
		public static final Codec<Load> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("main").forGetter(Load::main),
			Codec.INT.fieldOf("off").forGetter(Load::off),
			Codec.INT.optionalFieldOf("nozzle", 0).forGetter(Load::nozzle)
		).apply(i, Load::new));
		public static final StreamCodec<ByteBuf, Load> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, l -> l.main() + 1,
			ByteBufCodecs.VAR_INT, l -> l.off() + 1,
			ByteBufCodecs.VAR_INT, Load::nozzle,
			(main, off, nozzle) -> new Load(main - 1, off - 1, nozzle));

		/** The shots left on {@code hand}'s wrist (-1: no cartridge). */
		public int fluid(int hand) {
			return hand == WebNet.OFF ? this.off : this.main;
		}

		public Load withFluid(int hand, int fluid) {
			return hand == WebNet.OFF ? new Load(this.main, fluid, this.nozzle) : new Load(fluid, this.off, this.nozzle);
		}

		public boolean nozzle(int hand) {
			return (this.nozzle >> hand & 1) != 0;
		}

		public Load withNozzle(int hand, boolean on) {
			int bits = on ? this.nozzle | 1 << hand : this.nozzle & ~(1 << hand);
			return new Load(this.main, this.off, bits);
		}
	}

	/** On a player: the shooters on their wrists (saved; synced to everyone, for drawing them and their lines). */
	public static final AttachmentType<Load> WORN = AttachmentRegistry.create(JaymosPowers.id("web_shooters"),
		builder -> builder.persistent(Load.CODEC).copyOnDeath().syncWith(Load.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** On the shooter item: what its wrists hold. On a cartridge: the shots left (none: full). */
	public static DataComponentType<Load> LOAD;
	public static DataComponentType<Integer> FLUID;
	public static Item SHOOTER, CARTRIDGE;

	/** Client → server: Sneak + use with an empty hand at nothing: take the shooters off. */
	public record TakeOff() implements CustomPacketPayload {
		public static final Type<TakeOff> TYPE = new Type<>(JaymosPowers.id("web_shooters_off"));
		public static final StreamCodec<ByteBuf, TakeOff> CODEC = StreamCodec.unit(new TakeOff());

		@Override
		public Type<TakeOff> type() {
			return TYPE;
		}
	}

	private WebShooters() {
	}

	static void register() {
		LOAD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("web_shooter_load"),
			DataComponentType.<Load>builder().persistent(Load.CODEC).networkSynchronized(Load.STREAM_CODEC).build());
		FLUID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("web_fluid"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		SHOOTER = item("web_shooter", p -> new WebShooterItem(p.stacksTo(1)));
		CARTRIDGE = item("web_cartridge", p -> new WebCartridgeItem(p.stacksTo(CARTRIDGE_STACK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(SHOOTER);
			output.accept(CARTRIDGE);
		});
		PayloadTypeRegistry.serverboundPlay().register(TakeOff.TYPE, TakeOff.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TakeOff.TYPE, (payload, context) -> takeOffIntoHand(context.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && !player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				// dropped like armour (kept with keepInventory: the attachment is copied over)
				ItemStack stack = takeOff(player);
				if (stack != null) {
					Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
				}
			}
		});
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	// ---- reading (both sides) ----------------------------------------------------------------------------------------

	/** The shooters this player wears, or null. */
	public static @Nullable Load worn(Player player) {
		return player.getAttached(WORN);
	}

	/** A web from {@code hand} would leave a shooter's nozzle now: worn, that wrist has fluid, and no suit on. */
	public static boolean loaded(Player player, int hand) {
		Load load = worn(player);
		return load != null && load.fluid(hand) > 0 && !WebSymbiote.suitShown(player);
	}

	/** The web out of {@code hand} came from a shooter (or would, fired now): drawn as the braided line, from the wrist. */
	public static boolean nozzleLine(Player player, int hand) {
		Load load = worn(player);
		return load != null && !WebSymbiote.suitShown(player) && (load.fluid(hand) > 0 || load.nozzle(hand));
	}

	/** How much further a web from {@code hand} reaches (a loaded shooter's). */
	public static double rangeScale(Player player, int hand) {
		return loaded(player, hand) ? RANGE_SCALE : 1.0;
	}

	/** How much harder a web from {@code hand} reels in. */
	public static double pullScale(Player player, int hand) {
		return loaded(player, hand) ? PULL_SCALE : 1.0;
	}

	/** The range the server allows a client's web from this player (leniently: the shot may just have used the last fluid). */
	static double serverRangeScale(Player player) {
		return worn(player) != null && !WebSymbiote.suitShown(player) ? RANGE_SCALE : 1.0;
	}

	/** {@link #REQUIRED} is on and {@code hand} has nothing to fire (the suit always can). */
	public static boolean blocked(Player player, int hand) {
		return REQUIRED && !WebSymbiote.suitShown(player) && !loaded(player, hand);
	}

	/** The shots left in a cartridge item. */
	public static int fluid(ItemStack cartridge) {
		return cartridge.getOrDefault(FLUID, CARTRIDGE_SHOTS);
	}

	/** A cartridge item holding {@code fluid} shots (none: the glass bottle it was), or empty for no cartridge. */
	static ItemStack cartridge(int fluid) {
		if (fluid < 0) {
			return ItemStack.EMPTY;
		}
		if (fluid == 0) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		ItemStack stack = new ItemStack(CARTRIDGE);
		if (fluid < CARTRIDGE_SHOTS) {
			stack.set(FLUID, fluid);
		}
		return stack;
	}

	// ---- firing (server) ---------------------------------------------------------------------------------------------

	/**
	 * A web fired from {@code hand}: from the shooter on that wrist if it has fluid (one shot used, a mechanical click),
	 * else, wearing them, a dry click (the power's own web goes out). {@code heard}: the firer's game already played it.
	 * True if it came from the nozzle.
	 */
	public static boolean fire(ServerPlayer player, int hand, boolean heard) {
		Load load = worn(player);
		if (load == null || WebSymbiote.suitShown(player)) {
			return false;
		}
		int fluid = load.fluid(hand);
		boolean nozzle = fluid > 0;
		player.setAttached(WORN, load.withFluid(hand, nozzle ? fluid - 1 : fluid).withNozzle(hand, nozzle));
		Player except = heard ? player : null;
		if (nozzle) {
			player.level().playSound(except, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5F, 1.9F);
		} else {
			player.level().playSound(except, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.35F, 1.8F);
		}
		return nozzle;
	}

	// ---- wearing (server) --------------------------------------------------------------------------------------------

	/** Straps the pair in {@code stack} on (its cartridges with it). */
	static void putOn(ServerPlayer player, ItemStack stack) {
		Load load = stack.getOrDefault(LOAD, Load.EMPTY);
		player.setAttached(WORN, new Load(load.main(), load.off(), 0));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** Takes the worn pair off and gives it back as an item (cartridges kept in it), or null if none. */
	static @Nullable ItemStack takeOff(ServerPlayer player) {
		Load load = worn(player);
		if (load == null) {
			return null;
		}
		player.removeAttached(WORN);
		ItemStack stack = new ItemStack(SHOOTER);
		stack.set(LOAD, new Load(load.main(), load.off(), 0));
		return stack;
	}

	private static void takeOffIntoHand(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			return;
		}
		ItemStack stack = takeOff(player);
		if (stack == null) {
			return;
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 1.3F);
	}

	// ---- loading (server) --------------------------------------------------------------------------------------------

	/**
	 * A cartridge used while wearing shooters: it goes into the emptier wrist, and what was there comes back (a part-used
	 * cartridge, or the bottle of a spent one). False if both wrists already hold at least as much.
	 */
	static boolean loadWorn(ServerPlayer player, ItemStack cartridge) {
		Load load = worn(player);
		if (load == null) {
			return false;
		}
		int hand = load.fluid(WebNet.OFF) < load.fluid(WebNet.MAIN) ? WebNet.OFF : WebNet.MAIN;
		int fresh = fluid(cartridge);
		int old = load.fluid(hand);
		if (old >= fresh) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_shooter.full"));
			return false;
		}
		player.setAttached(WORN, load.withFluid(hand, fresh));
		cartridge.shrink(1);
		giveBack(player, cartridge(old));
		clunk(player);
		return true;
	}

	/** Sneak + use with the pair in hand: its empty wrists (none, or spent) loaded from the fullest cartridges carried. */
	static boolean loadHeld(ServerPlayer player, ItemStack shooter) {
		Load load = shooter.getOrDefault(LOAD, Load.EMPTY);
		boolean loadedAny = false;
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			if (load.fluid(hand) > 0) {
				continue;
			}
			ItemStack best = ItemStack.EMPTY;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (stack.is(CARTRIDGE) && (best.isEmpty() || fluid(stack) > fluid(best))) {
					best = stack;
				}
			}
			if (best.isEmpty()) {
				break;
			}
			int old = load.fluid(hand);
			load = load.withFluid(hand, fluid(best));
			best.shrink(1);
			giveBack(player, cartridge(old));
			loadedAny = true;
		}
		if (!loadedAny) {
			player.sendOverlayMessage(Component.translatable(load.fluid(WebNet.MAIN) > 0 && load.fluid(WebNet.OFF) > 0
				? "message.jaymospowers.web_shooter.full" : "message.jaymospowers.web_shooter.no_cartridge"));
			return false;
		}
		shooter.set(LOAD, load);
		clunk(player);
		return true;
	}

	private static void giveBack(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}

	private static void clunk(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.8F, 1.5F);
	}
}
