package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.11.87) Suit calling (Mind level 3). The call key (no power: C; with one: the Rig key's Suit) <b>parks</b> the worn
 * Rig: the pieces blast off (everyone sees them go) and wait wherever the Mind keeps them, remembered on the player
 * ({@link #PARKED}, kept through death: the suit wasn't on you). Pressed again it <b>calls</b> them: sparks stream in from
 * above and the pieces lock on one by one ((0.11.104) the Core first, the Helm last; client RigSuit builds each one on
 * plate by plate), each with a clank and a jolt on the wearer's screen. Only Rig pieces park, and only with the Core
 * worn at level 3 (the Mind flies them). Armour already in a slot when its piece comes back goes to the inventory (or
 * drops, if that's full).
 * <p>
 * Parked by the key while standing still and out of a fight, it comes off as a <b>suit-down</b> (Andy: "parking the suit
 * should show a suit down like the clip", the Iron Man 2 suit-down): the arms go out as the suit-up's wrap, and the
 * pieces come off one at a time in the wrap's reverse, the helm first (the faceplate lifts, the helm folds back off the
 * head), then the legs, the chest, and last the gauntlets sliding off past the hands; each plate swings open and flies
 * off. A blow part way, leaving or a sentry post parks the rest at once.
 */
public final class RigCall {
	/** The pieces parked: one stack per {@link Rig.Piece}, in order (empty where none is). */
	public static final AttachmentType<List<ItemStack>> PARKED = AttachmentRegistry.create(JaymosPowers.id("rig_parked"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
			.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** The suit is flying in plate by plate (synced to everyone: client RigSuit scatters the plates and brings them home). */
	public static final AttachmentType<Boolean> FLYING_IN = AttachmentRegistry.create(JaymosPowers.id("rig_flying_in"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** How long a landed piece's plates take to fly home and lock on (ticks; the client RigSuit uses the same). */
	public static final int FLY_TICKS = 10;
	/** Where a call from a sentry post starts. */
	private static final Map<UUID, Vec3> FROM = new HashMap<>();

	/**
	 * The failsafe: a hit that leaves the owner alive at {@link #FAILSAFE_HEALTH} or less brings the parked suit on its own,
	 * if its Mind is level {@link #FAILSAFE_LEVEL}, no oftener than every {@link #FAILSAFE_COOLDOWN} ticks. The Rig menu
	 * switches it off ({@link #FAILSAFE_OFF}, kept).
	 */
	static final int FAILSAFE_LEVEL = 4, FAILSAFE_COOLDOWN = 1200;
	static final float FAILSAFE_HEALTH = 6.0F;
	public static final AttachmentType<Boolean> FAILSAFE_OFF = AttachmentRegistry.create(JaymosPowers.id("rig_failsafe_off"),
		builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath()
			.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	private static final Map<UUID, Long> FAILSAFE_READY = new HashMap<>();

	/** The streak of sparks coming in before the first piece lands (ticks) in the plain call; each suit-up has its own ({@link RigSuitUp#inbound}). */
	static final int INBOUND_TICKS = 12;
	/**
	 * (0.11.104) When each piece of {@link #ORDER} lands, in ticks after the first: the Core's spine unit has to land and
	 * unfold, and its plates swing in, before the Arms' frame closes on it; the Helm locks on last (client RigSuit builds
	 * each piece on as it lands, so these follow its build times).
	 */
	static final int[] LAND_AT = {0, 14, 22, 30};
	/** Where the sparks come from: this far up and behind. */
	static final double FROM_UP = 10.0, FROM_BACK = 6.0;

	/** Players whose suit is flying in: ticks since the call. */
	private static final Map<UUID, int[]> CALLING = new HashMap<>();

	/** Whether this player's suit is flying in right now (server). */
	static boolean calling(Player player) {
		return CALLING.containsKey(player.getUUID());
	}

	/** Players whose suit is coming off in a suit-down: ticks since it began. */
	private static final Map<UUID, int[]> PARKING = new HashMap<>();
	/** The suit-down's order (the wrap's backwards: helm, legs, chest, arms), when each comes off (ticks in), and how long the last one's plates take to fly. */
	private static final Rig.Piece[] DOWN_ORDER = {Rig.Piece.HELM, Rig.Piece.THRUSTERS, Rig.Piece.CORE, Rig.Piece.ARMS};
	static final int[] DOWN_AT = {10, 24, 38, 52};
	static final int DOWN_SETTLE = 24;

	/** Whether this player's suit is coming off in a suit-down right now (server). */
	static boolean parking(Player player) {
		return PARKING.containsKey(player.getUUID());
	}

	/** The order the pieces lock on: (0.11.104) the Core's big spine unit first, the Helm last. */
	private static final Rig.Piece[] ORDER = {Rig.Piece.CORE, Rig.Piece.ARMS, Rig.Piece.THRUSTERS, Rig.Piece.HELM};

	private RigCall() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CALLING.remove(handler.player.getUUID());
			if (PARKING.containsKey(handler.player.getUUID())) {
				downRest(handler.player);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim instanceof ServerPlayer player && taken > 0.0F && player.isAlive() && player.getHealth() <= FAILSAFE_HEALTH) {
				failsafe(player);
			}
		});
	}

	/** Whether this player's failsafe is switched on (either side). */
	public static boolean failsafeOn(Player player) {
		return !player.getAttachedOrElse(FAILSAFE_OFF, false);
	}

	/** The Rig menu's switch. */
	static void toggleFailsafe(ServerPlayer player) {
		if (failsafeOn(player)) {
			player.setAttached(FAILSAFE_OFF, true);
		} else {
			player.removeAttached(FAILSAFE_OFF);
		}
	}

	private static void failsafe(ServerPlayer player) {
		if (PARKING.containsKey(player.getUUID())) {
			// hurt part way through a suit-down: the rest parks now, and the failsafe may bring it straight back
			downRest(player);
		}
		if (!failsafeOn(player) || CALLING.containsKey(player.getUUID()) || !parked(player)) {
			return;
		}
		List<ItemStack> parked = player.getAttachedOrElse(PARKED, List.of());
		int core = Rig.Piece.CORE.ordinal();
		if (parked.size() <= core || Rig.level(parked.get(core)) < FAILSAFE_LEVEL) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < FAILSAFE_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		FAILSAFE_READY.put(player.getUUID(), now + FAILSAFE_COOLDOWN);
		call(player);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.failsafe"));
		RigFinish.earn(player, RigFinish.LAST_STAND);
	}

	/** Whether this player has pieces parked (either side). */
	public static boolean parked(Player player) {
		List<ItemStack> parked = player.getAttached(PARKED);
		if (parked == null) {
			return false;
		}
		for (ItemStack stack : parked) {
			if (!stack.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The Octo Core's "Core only" switch (Andy: "you can now choose to have only the chest piece equipped, and still use the
	 * arms"): every other worn Rig piece blasts off and parks, as a plain park sends them, and the Core stays on with its
	 * arms. Picked again (or the call key), the parked pieces come back as any call brings them. A slot whose parked piece
	 * is already waiting sends the worn one to the inventory instead (or drops it), so nothing parked is lost. Needs no
	 * Mind level: the arms are the Core's own.
	 */
	static void coreOnly(ServerPlayer player) {
		if (CALLING.containsKey(player.getUUID()) || PARKING.containsKey(player.getUUID()) || Rig.core(player).isEmpty()) {
			return;
		}
		boolean others = false;
		for (Rig.Piece piece : Rig.Piece.values()) {
			others |= piece != Rig.Piece.CORE && Rig.wears(player, piece);
		}
		if (!others) {
			if (parked(player)) {
				call(player);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_octo.core_only_already"));
			}
			return;
		}
		List<ItemStack> parked = new ArrayList<>(player.getAttachedOrElse(PARKED, List.of()));
		while (parked.size() < Rig.Piece.values().length) {
			parked.add(ItemStack.EMPTY);
		}
		for (Rig.Piece piece : Rig.Piece.values()) {
			if (piece == Rig.Piece.CORE || !Rig.wears(player, piece)) {
				continue;
			}
			ItemStack worn = player.getItemBySlot(piece.slot).copy();
			player.setItemSlot(piece.slot, ItemStack.EMPTY);
			if (parked.get(piece.ordinal()).isEmpty()) {
				parked.set(piece.ordinal(), worn);
			} else {
				// (its parked spot already full) into the pack, or dropped at your feet with no room
				player.getInventory().placeItemBackInInventory(worn, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		player.setAttached(PARKED, parked);
		parkFx(player);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_octo.core_only"));
	}

	/** The call key: call the parked suit, else park the worn one. */
	static void use(ServerPlayer player) {
		if (CALLING.containsKey(player.getUUID()) || PARKING.containsKey(player.getUUID())) {
			return;
		}
		if (parked(player)) {
			call(player);
		} else if (Rig.wearsAny(player)) {
			if (RigSuitUp.stillAndCalm(player)) {
				suitDown(player);
			} else {
				park(player);
			}
		}
	}

	/** The suit-down begins (the same needs as parking): the arms go out, and {@link #tick} takes the pieces off one by one. */
	static boolean suitDown(ServerPlayer player) {
		if (Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.park_needs_core"));
			return false;
		}
		if (!RigActives.needs(player, Rig.CALL_LEVEL)) {
			return false;
		}
		RigMind.sayAlways(player, "park", 1.0F);
		List<ItemStack> parked = new ArrayList<>();
		for (int i = 0; i < Rig.Piece.values().length; i++) {
			parked.add(ItemStack.EMPTY);
		}
		player.setAttached(PARKED, parked);
		PARKING.put(player.getUUID(), new int[] {0});
		RigSuitUp.beginDown(player);
		return true;
	}

	/** One step of a suit-down. */
	private static void tickDown(ServerPlayer player, int[] parking) {
		if (!player.isAlive() || player.isSpectator() || player.hurtTime > 0) {
			downRest(player);
			return;
		}
		int t = parking[0]++;
		for (int i = 0; i < DOWN_AT.length; i++) {
			if (t == DOWN_AT[i]) {
				takeOff(player, DOWN_ORDER[i], i);
				if (i == DOWN_AT.length - 1) {
					parkFx(player);
				}
			}
		}
		if (t > DOWN_AT[DOWN_AT.length - 1] + DOWN_SETTLE) {
			PARKING.remove(player.getUUID());
			RigSuitUp.end(player);
		}
	}

	/** A suit-down cut short: what is still on parks now (the plates still come apart as the plain park's do). */
	private static void downRest(ServerPlayer player) {
		PARKING.remove(player.getUUID());
		RigSuitUp.end(player);
		boolean any = false;
		for (Rig.Piece piece : DOWN_ORDER) {
			any |= takeOff(player, piece, -1);
		}
		if (any) {
			parkFx(player);
		}
	}

	/** One piece comes off into the parked suit; {@code index} its place in the suit-down (-1: no sound of its own). */
	private static boolean takeOff(ServerPlayer player, Rig.Piece piece, int index) {
		ItemStack worn = player.getItemBySlot(piece.slot);
		if (piece.item() == null || !worn.is(piece.item())) {
			return false;
		}
		List<ItemStack> parked = new ArrayList<>(player.getAttachedOrElse(PARKED, List.of()));
		while (parked.size() < Rig.Piece.values().length) {
			parked.add(ItemStack.EMPTY);
		}
		parked.set(piece.ordinal(), worn.copy());
		player.setItemSlot(piece.slot, ItemStack.EMPTY);
		player.setAttached(PARKED, parked);
		if (index >= 0) {
			RigSuitUp.tookOff(player, index);
		}
		return true;
	}

	/** Parks the worn suit (the Core and the Mind's level 3 needed); false if it did not. */
	static boolean park(ServerPlayer player) {
		if (Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.park_needs_core"));
			return false;
		}
		if (!RigActives.needs(player, Rig.CALL_LEVEL)) {
			return false;
		}
		// the Mind says goodbye while it still has a voice
		RigMind.sayAlways(player, "park", 1.0F);
		List<ItemStack> parked = new ArrayList<>();
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() != null && worn.is(piece.item())) {
				parked.add(worn.copy());
				player.setItemSlot(piece.slot, ItemStack.EMPTY);
			} else {
				parked.add(ItemStack.EMPTY);
			}
		}
		player.setAttached(PARKED, parked);
		parkFx(player);
		return true;
	}

	/** The pieces blast off to wherever the Mind keeps them. */
	private static void parkFx(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.3, 0.1, 0.3, 0.06);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 0, 0.0, 1.0, 0.0, 1.2);
		for (int i = 0; i < 10; i++) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.5 + i * 0.3, player.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	static void call(ServerPlayer player) {
		// a suit standing sentry leaves its post: the plates come from where it stood
		Vec3 post = RigSentry.recall(player);
		if (post != null) {
			FROM.put(player.getUUID(), post);
		}
		player.setAttached(FLYING_IN, true);
		// the suit-up that fits the moment (standing, at the parked post, in the air, falling from a height) and its pose
		boolean thrusters = Rig.wears(player, Rig.Piece.THRUSTERS) || hasParked(player, Rig.Piece.THRUSTERS);
		int style = RigSuitUp.pick(player, post, thrusters);
		RigSuitUp.begin(player, style, 0L);
		CALLING.put(player.getUUID(), new int[] {0, style});
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.calling"));
	}

	/** Whether this player has pieces parked in this slot. */
	static boolean hasParked(Player player, Rig.Piece piece) {
		List<ItemStack> parked = player.getAttached(PARKED);
		return parked != null && parked.size() > piece.ordinal() && !parked.get(piece.ordinal()).isEmpty();
	}

	/** Every tick, for everyone: a suit flying in. */
	static void tick(ServerPlayer player) {
		int[] parking = PARKING.get(player.getUUID());
		if (parking != null) {
			tickDown(player, parking);
		}
		int[] calling = CALLING.get(player.getUUID());
		if (calling == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			CALLING.remove(player.getUUID());
			FROM.remove(player.getUUID());
			player.removeAttached(FLYING_IN);
			RigSuitUp.forget(player);
			return;
		}
		int t = calling[0]++;
		int style = calling[1];
		int inbound = RigSuitUp.inbound(style);
		int[] landAt = RigSuitUp.landAt(style);
		ServerLevel level = player.level();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 back = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw)).scale(FROM_BACK);
		Vec3 post = FROM.get(player.getUUID());
		Vec3 source = post != null ? post.add(0.0, 1.0, 0.0) : player.position().add(back).add(0.0, FROM_UP, 0.0);
		Vec3 body = player.position().add(0.0, 1.0, 0.0);
		int last = inbound + landAt[landAt.length - 1];
		if (t < last) {
			// the streak: sparks along the line in from the sky, closing on the body
			double share = Math.min(1.0, (t + 1) / (double)inbound);
			Vec3 head = source.lerp(body, share);
			for (int i = 0; i < 4; i++) {
				Vec3 p = source.lerp(head, level.getRandom().nextDouble());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
		// (the wrap holds on until the helm's last plate, the crest, is home: its plates close in as they fly)
		if (t > last + (style == RigSuitUp.WRAP ? RigSuitUp.WRAP_SETTLE : FLY_TICKS)) {
			finish(player);
			return;
		}
		int index = -1;
		for (int i = 0; i < landAt.length; i++) {
			if (t == inbound + landAt[i]) {
				index = i;
			}
		}
		if (index >= 0) {
			land(player, index, style, source);
		}
	}

	/** The suit has come all the way in. */
	private static void finish(ServerPlayer player) {
		CALLING.remove(player.getUUID());
		FROM.remove(player.getUUID());
		player.removeAttached(FLYING_IN);
		player.removeAttached(PARKED);
		RigMind.sayAlways(player, "call", 1.2F);
		if (player.getAttachedOrElse(RigSuitUp.STYLE, 0) != RigSuitUp.FALL) {
			// (a falling assembly keeps its style until the thrusters have set you down)
			RigSuitUp.end(player);
		}
	}

	/** Whatever has not landed yet lands now (a fall too short for the whole assembly) and the call is done. */
	static void finishNow(ServerPlayer player) {
		int[] calling = CALLING.get(player.getUUID());
		if (calling == null) {
			return;
		}
		int style = calling[1];
		int[] landAt = RigSuitUp.landAt(style);
		int from = Math.max(0, Math.min(landAt.length, countLanded(calling[0], style)));
		for (int i = from; i < landAt.length; i++) {
			land(player, i, style, player.position().add(0.0, 4.0, 0.0));
		}
		finish(player);
	}

	/** How many pieces have landed by call tick {@code t}. */
	private static int countLanded(int t, int style) {
		int[] landAt = RigSuitUp.landAt(style);
		int inbound = RigSuitUp.inbound(style);
		int n = 0;
		for (int at : landAt) {
			if (t > inbound + at) {
				n++;
			}
		}
		return n;
	}

	/** Piece {@code index} of the order lands on the wearer. */
	private static void land(ServerPlayer player, int index, int style, Vec3 source) {
		ServerLevel level = player.level();
		Rig.Piece piece = ORDER[index];
		List<ItemStack> parked = new ArrayList<>(player.getAttachedOrElse(PARKED, List.of()));
		while (parked.size() < Rig.Piece.values().length) {
			parked.add(ItemStack.EMPTY);
		}
		ItemStack stack = parked.get(piece.ordinal());
		if (stack.isEmpty()) {
			return;
		}
		ItemStack worn = player.getItemBySlot(piece.slot);
		if (!worn.isEmpty()) {
			// what was in the way goes to the pack
			if (!player.getInventory().add(worn.copy())) {
				player.drop(worn.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		player.setItemSlot(piece.slot, stack);
		parked.set(piece.ordinal(), ItemStack.EMPTY);
		player.setAttached(PARKED, parked);
		double y = player.getY() + switch (piece) {
			case HELM -> 1.7;
			case CORE -> 1.2;
			case ARMS -> 0.7;
			case THRUSTERS -> 0.15;
		};
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), y, player.getZ(), 14, 0.3, 0.1, 0.3, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 4, 0.2, 0.05, 0.2, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F,
			0.9F + index * 0.1F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.8F);
		RigSuitUp.landed(player, style, index, y);
		// each piece landing jolts the wearer's view
		Shakes.send(player, 1.2F, source);
	}
}
