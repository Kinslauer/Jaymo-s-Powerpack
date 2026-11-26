package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The modules a pod carries in its keel's sockets. A keel has {@link PodEntity#sockets} of them (2 on a raider's keel, up to
 * {@link #MOST_SOCKETS} on a Patrol one); every module is an item that fits one socket: set it by using it on the pod, take
 * the last one out by sneaking at the pod's bay (its lower half) with an empty hand ({@link #takeOut}).
 * <p>
 * The static API other code uses (flight, weapons, towing) is {@link #has}, {@link #count}, {@link #fitted}, {@link #free},
 * {@link #fit}, {@link #remove} and {@link #item}. The first six kinds have their effects in this track (legs: the whole pod
 * lands, {@code PodLegs}; hold; seat; extra tank; kick stage; scanner: {@code PodScanner}); {@link Kind#KI_CANNON},
 * {@link Kind#SHIELD}, {@link Kind#MINING_BEAM} and {@link Kind#TOW_LINE} are only fitted and drawn here: what they do in
 * flight is for the flight track.
 */
public final class PodModules {
	// ---- tuning ----
	/** The fewest and most sockets a keel has: a raider's, a Patrol pod's. */
	public static final int FEWEST_SOCKETS = 2, MOST_SOCKETS = 6;
	/** A hammer-low click (the pod's bay) is below this fraction of its height; above it is the cabin. */
	public static final double BAY_HEIGHT = 1.0;

	/** What a module is. Order is stored (a socket holds {@code ordinal + 1}), so only ever add at the end. */
	public enum Kind {
		/** With them the whole pod lands; without, only the cabin comes down under a chute ({@code PodLegs}). */
		LEGS("legs", true),
		/** A cargo hold: nine slots a row, rows by class ({@code PodClass#holdRows}). */
		HOLD("hold", false),
		/** A second seat beside the pilot's. */
		SEAT("seat", false),
		/** An extra tank: the pod holds half as much fuel again. */
		EXTRA_TANK("extra_tank", false),
		/** A kick stage: each trip costs a fifth less. */
		KICK_STAGE("kick_stage", false),
		/** A needle that points to the nearest settlement or mission site after landing. */
		SCANNER("scanner", false),
		/** For the flight track: a ki cannon. */
		KI_CANNON("ki_cannon", true),
		/** For the flight track: a shield. */
		SHIELD("shield", true),
		/** For the flight track: a mining beam. */
		MINING_BEAM("mining_beam", true),
		/** For the flight track: a tow line. */
		TOW_LINE("tow_line", true);

		public final String key;
		/** Made of iron, so forged (a Tinkers route: no grid recipe). */
		public final boolean forged;

		Kind(String key, boolean forged) {
			this.key = key;
			this.forged = forged;
		}

		/** The item's id path: {@code pod_module_<key>}. */
		public String itemPath() {
			return "pod_module_" + key;
		}

		public Component displayName() {
			return Component.translatable("item.jaymospowers." + itemPath());
		}

		/** The kind stored as {@code value} in a socket (1 and up), or null. */
		static @Nullable Kind ofStored(int value) {
			return value >= 1 && value <= values().length ? values()[value - 1] : null;
		}

		int stored() {
			return ordinal() + 1;
		}
	}

	private static final Map<Kind, Item> ITEMS = new EnumMap<>(Kind.class);
	private static boolean ready;

	private PodModules() {
	}

	/** Registers a module item for every kind, and their creative entries. Called once, from {@link PodTravel#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(kind.itemPath()));
			ITEMS.put(kind, Registry.register(BuiltInRegistries.ITEM, key, new ModuleItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.UNCOMMON), kind)));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Kind kind : Kind.values()) {
				output.accept(ITEMS.get(kind));
			}
		});
	}

	/** A module as an item. */
	public static final class ModuleItem extends Item {
		public final Kind kind;

		ModuleItem(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.pod_module." + kind.key).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.pod_module.fit").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	// ---------------------------------------------------------------- the API

	/** The item of {@code kind}. */
	public static Item item(Kind kind) {
		return ITEMS.get(kind);
	}

	/** The kind {@code stack} is a module of, or null. */
	public static @Nullable Kind kindOf(ItemStack stack) {
		return stack.getItem() instanceof ModuleItem module ? module.kind : null;
	}

	/** How many sockets {@code pod}'s keel has. */
	public static int sockets(PodEntity pod) {
		return pod.sockets();
	}

	/** The modules fitted, in socket order. */
	public static List<Kind> fitted(PodEntity pod) {
		List<Kind> fitted = new ArrayList<>();
		for (int socket = 0; socket < pod.sockets(); socket++) {
			Kind kind = pod.moduleAt(socket);
			if (kind != null) {
				fitted.add(kind);
			}
		}
		return fitted;
	}

	/** Whether {@code pod} has a module of {@code kind} fitted. */
	public static boolean has(PodEntity pod, Kind kind) {
		return count(pod, kind) > 0;
	}

	/** How many modules of {@code kind} are fitted. */
	public static int count(PodEntity pod, Kind kind) {
		int count = 0;
		for (int socket = 0; socket < pod.sockets(); socket++) {
			if (pod.moduleAt(socket) == kind) {
				count++;
			}
		}
		return count;
	}

	/** How many sockets are empty. */
	public static int free(PodEntity pod) {
		return pod.sockets() - fitted(pod).size();
	}

	/** Fits {@code kind} into the first empty socket; false when there is none. */
	public static boolean fit(PodEntity pod, Kind kind) {
		for (int socket = 0; socket < pod.sockets(); socket++) {
			if (pod.moduleAt(socket) == null) {
				pod.setModule(socket, kind);
				return true;
			}
		}
		return false;
	}

	/** Takes the last module fitted out; its kind, or null if the pod has none. */
	public static @Nullable Kind remove(PodEntity pod) {
		for (int socket = pod.sockets() - 1; socket >= 0; socket--) {
			Kind kind = pod.moduleAt(socket);
			if (kind != null) {
				pod.setModule(socket, null);
				return kind;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- trip numbers the kinds change

	/** What a trip costing {@code cost} fuel burns from a pod with (or without) a kick stage: a fifth less, never to nothing. */
	public static int kicked(int cost, boolean kick) {
		return kick && cost > 0 ? Math.max(1, Math.round(cost * KICK_SHARE)) : cost;
	}

	/** The share of fuel a trip with a kick stage burns (a fifth less). */
	public static final float KICK_SHARE = 0.8F;
	/** An extra tank holds this many times as much. */
	public static final float EXTRA_TANK_SHARE = 1.5F;

	// ---------------------------------------------------------------- fitting and taking out

	/** {@code held} used on {@code pod}: fits it when it is a module (told why not otherwise). False when it isn't a module at all. */
	static boolean tryFit(ServerPlayer player, PodEntity pod, ItemStack held) {
		Kind kind = kindOf(held);
		if (kind == null) {
			return false;
		}
		if (free(pod) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.sockets_full", pod.sockets()).withStyle(ChatFormatting.GRAY));
			return true;
		}
		fit(pod, kind);
		if (!player.isCreative()) {
			held.shrink(1);
		}
		effect(player.level(), pod, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.module_fitted", kind.displayName(), free(pod)).withStyle(ChatFormatting.GOLD));
		com.jaymospowers.power.zfighter.pod.PodLog.note(player, PodLog.Step.FITTED);
		return true;
	}

	/** Sneaking at the bay with an empty hand: the last module comes out into the hands (or onto the ground). */
	static void tryTake(ServerPlayer player, PodEntity pod) {
		Kind last = null;
		for (int socket = pod.sockets() - 1; socket >= 0 && last == null; socket--) {
			last = pod.moduleAt(socket);
		}
		if (last == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.no_modules").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (last == Kind.HOLD && !pod.holdEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.hold_not_empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		remove(pod);
		ItemStack stack = new ItemStack(item(last));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		effect(player.level(), pod, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.module_taken", last.displayName()).withStyle(ChatFormatting.GRAY));
	}

	private static void effect(ServerLevel level, PodEntity pod, float pitch) {
		Vec3 at = pod.position().add(0.0, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, pitch);
		level.sendParticles(MagicFx.CRACKLE, at.x, at.y, at.z, 10, 0.5, 0.3, 0.5, 0.2);
	}
}
