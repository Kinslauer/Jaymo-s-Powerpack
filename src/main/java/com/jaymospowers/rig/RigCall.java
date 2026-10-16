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
 * above and the pieces lock on one by one ((0.11.101) the Core first, the Helm last; client RigSuit builds each one on
 * plate by plate), each with a clank and a jolt on the wearer's screen. Only Rig pieces park, and only with the Core
 * worn at level 3 (the Mind flies them). Armour already in a slot when its piece comes back goes to the inventory (or
 * drops, if that's full).
 */
public final class RigCall {
	/** The pieces parked: one stack per {@link Rig.Piece}, in order (empty where none is). */
	public static final AttachmentType<List<ItemStack>> PARKED = AttachmentRegistry.create(JaymosPowers.id("rig_parked"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
			.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** The streak of sparks coming in before the first piece lands (ticks). */
	static final int INBOUND_TICKS = 12;
	/**
	 * (0.11.101) When each piece of {@link #ORDER} lands, in ticks after the first: the Core's spine unit has to land and
	 * unfold, and its plates swing in, before the Arms' frame closes on it; the Helm locks on last (client RigSuit builds
	 * each piece on as it lands, so these follow its build times).
	 */
	static final int[] LAND_AT = {0, 14, 22, 30};
	/** Where the sparks come from: this far up and behind. */
	static final double FROM_UP = 10.0, FROM_BACK = 6.0;

	/** Players whose suit is flying in: ticks since the call. */
	private static final Map<UUID, int[]> CALLING = new HashMap<>();

	/** The order the pieces lock on: (0.11.101) the Core's big spine unit first, the Helm last. */
	private static final Rig.Piece[] ORDER = {Rig.Piece.CORE, Rig.Piece.ARMS, Rig.Piece.THRUSTERS, Rig.Piece.HELM};

	private RigCall() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CALLING.remove(handler.player.getUUID()));
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

	/** The call key: call the parked suit, else park the worn one. */
	static void use(ServerPlayer player) {
		if (CALLING.containsKey(player.getUUID())) {
			return;
		}
		if (parked(player)) {
			call(player);
		} else if (Rig.wearsAny(player)) {
			park(player);
		}
	}

	private static void park(ServerPlayer player) {
		if (Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.park_needs_core"));
			return;
		}
		if (!RigActives.needs(player, Rig.CALL_LEVEL)) {
			return;
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
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.3, 0.1, 0.3, 0.06);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 0, 0.0, 1.0, 0.0, 1.2);
		for (int i = 0; i < 10; i++) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.5 + i * 0.3, player.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	private static void call(ServerPlayer player) {
		CALLING.put(player.getUUID(), new int[1]);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.calling"));
	}

	/** Every tick, for everyone: a suit flying in. */
	static void tick(ServerPlayer player) {
		int[] calling = CALLING.get(player.getUUID());
		if (calling == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			CALLING.remove(player.getUUID());
			return;
		}
		int t = calling[0]++;
		ServerLevel level = player.level();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 back = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw)).scale(FROM_BACK);
		Vec3 source = player.position().add(back).add(0.0, FROM_UP, 0.0);
		Vec3 body = player.position().add(0.0, 1.0, 0.0);
		int last = INBOUND_TICKS + LAND_AT[LAND_AT.length - 1];
		if (t < last) {
			// the streak: sparks along the line in from the sky, closing on the body
			double share = Math.min(1.0, (t + 1) / (double)INBOUND_TICKS);
			Vec3 head = source.lerp(body, share);
			for (int i = 0; i < 4; i++) {
				Vec3 p = source.lerp(head, level.getRandom().nextDouble());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
		if (t > last) {
			CALLING.remove(player.getUUID());
			player.removeAttached(PARKED);
			RigMind.sayAlways(player, "call", 1.2F);
			return;
		}
		int index = -1;
		for (int i = 0; i < LAND_AT.length; i++) {
			if (t == INBOUND_TICKS + LAND_AT[i]) {
				index = i;
			}
		}
		if (index < 0) {
			return;
		}
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
		// each piece landing jolts the wearer's view
		Shakes.send(player, 1.2F, source);
	}
}
