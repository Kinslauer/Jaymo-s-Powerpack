package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Bonding with the strong things you find. A bauble (and a relic, from Jaymo's Kingdoms) only does anything for you once you
 * have spent a rest with it on you: a night's sleep, or the minute of sitting that catches your breath. You can be bonded to
 * {@link #MOST} things at once; to take another you must let one go (the bonds screen, on the bauble screen). An unbonded
 * bauble can be worn but does nothing.
 *
 * <p>The bond lives in two places: the item carries a bond id in its custom data ({@link #BOND}) and the player keeps the
 * list of ids they are bonded to ({@link #BONDS}), with a picture of each so it can be let go from afar. The item also
 * carries whether its nature is known ({@link #KNOWN}) and how long it has been used ({@link #USED}); a found thing shows its
 * powers as unknown until it has been used for {@link #KNOWN_AFTER} ticks, studied by a wizard or a sage, or read by the
 * identify ritual (Jaymo's Kingdoms sets {@link #KNOWN}). A curse tag ({@link #CURSE}, the same key Jaymo's Kingdoms reads)
 * hides until the bond is made.
 */
public final class Bonds {
	/** Most things one player may be bonded to. */
	public static final int MOST = 3;
	/** Ticks of use before a found thing shows what it does (ten minutes), and how often use is counted (ticks). */
	public static final int KNOWN_AFTER = 20 * 60 * 10, USE_STEP = 100;
	/** Custom data keys, shared with Jaymo's Kingdoms (it reads them as plain strings). */
	public static final String BOND = "jaymos_bond", KNOWN = "jaymos_known", USED = "jaymos_used", FOUND = "jaymos_found";
	public static final String CURSE = "jaymoskingdoms_curse", RELIC = "jaymoskingdoms_relic", SENTIENT = "jaymos_will";

	/** One bond: the id the item carries, and a single copy of it for the bonds screen. */
	public record Bond(UUID id, ItemStack shown) {
		public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Bond::id), ItemStack.CODEC.fieldOf("shown").forGetter(Bond::shown)).apply(i, Bond::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Bond> STREAM = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Bond::id,
			ItemStack.STREAM_CODEC, Bond::shown, Bond::new);
	}

	/** On a player: what they are bonded to (saved, kept through death, synced to them). */
	public static final AttachmentType<List<Bond>> BONDS = AttachmentRegistry.create(JaymosPowers.id("bonds"),
		builder -> builder.persistent(Bond.CODEC.listOf()).copyOnDeath()
			.syncWith(Bond.STREAM.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** Client → server: let go of the bond with this id. */
	public record Unbond(UUID id) implements CustomPacketPayload {
		public static final Type<Unbond> TYPE = new Type<>(JaymosPowers.id("unbond"));
		public static final StreamCodec<ByteBuf, Unbond> CODEC = UUIDUtil.STREAM_CODEC.map(Unbond::new, Unbond::id).mapStream(b -> b);

		@Override
		public Type<Unbond> type() {
			return TYPE;
		}
	}

	/** Players who slept long enough and have not got up yet. */
	private static final Set<UUID> SLEPT = new HashSet<>();

	private Bonds() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Unbond.TYPE, Unbond.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Unbond.TYPE, (payload, context) -> unbond(context.player(), payload.id()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isSleeping()) {
					if (player.isSleepingLongEnough()) {
						SLEPT.add(player.getUUID());
					}
				} else if (SLEPT.remove(player.getUUID())) {
					rest(player);
				}
			}
		});
	}

	// ---- what a stack is ---------------------------------------------------------------------------------------------

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** Whether {@code stack} carries {@code key} in its custom data. */
	public static boolean has(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(key);
	}

	/** Whether {@code stack} is a strong thing that must be bonded to work: a bauble of ours, or a relic. */
	public static boolean needs(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof BaubleItem bauble) {
			return bauble.needsBond();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains(RELIC) || tag.contains(SENTIENT) || tag.contains(FOUND);
	}

	/** The bond id a stack carries, or null. */
	public static @Nullable UUID idOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getString(BOND).map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	/** Everything {@code player} is bonded to. */
	public static List<Bond> bonds(Player player) {
		List<Bond> list = player.getAttached(BONDS);
		return list == null ? List.of() : list;
	}

	/** Whether {@code player} is bonded to this stack. */
	public static boolean bonded(Player player, ItemStack stack) {
		UUID id = idOf(stack);
		if (id == null) {
			return false;
		}
		for (Bond bond : bonds(player)) {
			if (bond.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this stack does anything for {@code player}: it needs no bond, or they are bonded to it. */
	public static boolean works(Player player, ItemStack stack) {
		return !needs(stack) || bonded(player, stack);
	}

	/** Whether {@code stack}'s powers are still hidden: a found thing no one has studied or used long enough. */
	public static boolean unknown(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		boolean found = stack.getItem() instanceof BaubleItem bauble ? bauble.found() : false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return found;
		}
		CompoundTag tag = data.copyTag();
		return (found || tag.contains(FOUND)) && !tag.contains(KNOWN);
	}

	/** Shows what {@code stack} does from now on. */
	public static void reveal(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(KNOWN, true));
	}

	/** The curse tag on {@code stack}, or null. */
	public static @Nullable String curse(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(CURSE).filter(s -> !s.isEmpty()).orElse(null);
	}

	/** Whether the curse on {@code stack} has shown itself: it is bonded to someone, or its nature is known. */
	public static boolean curseShown(ItemStack stack) {
		return has(stack, BOND) || has(stack, KNOWN);
	}

	/** Counts {@code ticks} of use on a bonded stack (for the unknown to become known); true when it just became known. */
	public static boolean use(ItemStack stack, int ticks) {
		if (!unknown(stack)) {
			return false;
		}
		int[] total = {0};
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			total[0] = tag.getIntOr(USED, 0) + ticks;
			tag.putInt(USED, total[0]);
		});
		if (total[0] >= KNOWN_AFTER) {
			reveal(stack);
			return true;
		}
		return false;
	}

	// ---- bonding -----------------------------------------------------------------------------------------------------

	/** What {@code player} has on them that can be bonded: held, worn as armour, worn in a bauble slot (live stacks). */
	public static List<ItemStack> carried(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		out.add(player.getMainHandItem());
		out.add(player.getOffhandItem());
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			out.add(player.getItemBySlot(slot));
		}
		List<ItemStack> saved = player.getAttached(Baubles.WORN);
		if (saved != null) {
			for (int i = 0; i < saved.size() && i < Baubles.SIZE; i++) {
				if (Baubles.fits(i, saved.get(i))) {
					out.add(saved.get(i));
				}
			}
		}
		return out;
	}

	/**
	 * A rest was taken (a night's sleep, a minute of sitting): everything strong {@code player} has on them that is not yet
	 * bonded is, in the order held, worn, then bauble slots, until {@link #MOST}. Returns how many were bonded.
	 */
	public static int rest(ServerPlayer player) {
		List<Bond> bonds = new ArrayList<>(bonds(player));
		int made = 0;
		for (ItemStack stack : carried(player)) {
			if (bonds.size() >= MOST) {
				break;
			}
			if (!needs(stack) || bonded(player, stack)) {
				continue;
			}
			UUID id = UUID.randomUUID();
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(BOND, id.toString()));
			bonds.add(new Bond(id, stack.copyWithCount(1)));
			made++;
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
				level.sendParticles(com.jaymospowers.core.fx.MagicFx.mote(0xB89CFF, 0.8F), player.getX(), player.getY(1.0), player.getZ(), 14, 0.35, 0.5, 0.35, 0.02);
				if (curse(stack) != null) {
					// the curse shows itself: a low note and a dark breath
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
					level.sendParticles(com.jaymospowers.core.fx.MagicFx.miasma(0x401030, 1.0F), player.getX(), player.getY(1.0), player.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
				}
			}
		}
		if (made > 0) {
			player.setAttached(BONDS, List.copyOf(bonds));
			// the stacks changed in place; the worn list is set again so it syncs
			Baubles.setWorn(player, Baubles.worn(player));
		}
		return made;
	}

	/** Whether the thing bonded as {@code id} clings: it is cursed to, and cannot be let go. Kingdoms sets the tag. */
	public static boolean clinging(ItemStack stack) {
		return "clinging".equals(curse(stack)) && has(stack, BOND);
	}

	/** Lets go of the bond {@code id}. A clinging curse refuses. Returns whether it was let go. */
	public static boolean unbond(ServerPlayer player, UUID id) {
		List<Bond> bonds = new ArrayList<>(bonds(player));
		Bond found = null;
		for (Bond bond : bonds) {
			if (bond.id().equals(id)) {
				found = bond;
			}
		}
		if (found == null) {
			return false;
		}
		for (ItemStack stack : carried(player)) {
			if (id.equals(idOf(stack))) {
				if (clinging(stack)) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.5F);
					return false;
				}
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BOND));
			}
		}
		bonds.remove(found);
		if (bonds.isEmpty()) {
			player.removeAttached(BONDS);
		} else {
			player.setAttached(BONDS, List.copyOf(bonds));
		}
		Baubles.setWorn(player, Baubles.worn(player));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.7F);
		return true;
	}

	/** Lets go of whatever {@code stack} is bonded as, for a curse lifted or a thing unmade (no cling check). */
	public static void release(ServerPlayer player, ItemStack stack) {
		UUID id = idOf(stack);
		if (id == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BOND));
		List<Bond> bonds = new ArrayList<>(bonds(player));
		if (bonds.removeIf(b -> b.id().equals(id))) {
			if (bonds.isEmpty()) {
				player.removeAttached(BONDS);
			} else {
				player.setAttached(BONDS, List.copyOf(bonds));
			}
		}
	}
}
