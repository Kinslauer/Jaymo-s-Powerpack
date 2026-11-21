package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rewards held until you claim them, a page of the H menu (Notices): pay that would otherwise be dropped into your pack the
 * moment it is earned waits here instead, so it can't be lost to a full pack or a missed line of chat, and is taken
 * one at a time or all at once. Three kinds are held, each from a place that can safely wait: a bounty's pay ({@link Bounties}),
 * a job's wages ({@link Careers}), and what a rescue brought ({@link Deeds#broughtHome}: the family's purse, the realm's
 * bounty, a guard's gift or a family's thanks). Nothing held ever expires or is taken back.
 */
public final class Rewards {
	/** The kinds of reward held (their lang keys are {@code rewards.jaymoskingdoms.source.<key>}). */
	public static final String BOUNTY = "bounty", WAGES = "wages", RESCUE = "rescue";
	/** The most rewards held at once; a reward past that is paid out at once, as it always was. */
	public static final int MOST = 40;

	/** One thing held: an item (its registry id) and how many. */
	public record Part(String item, int count) {
		public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("item").forGetter(Part::item),
				Codec.INT.fieldOf("count").forGetter(Part::count))
			.apply(i, Part::new));

		public ItemStack stack() {
			Identifier key = Identifier.tryParse(this.item);
			Item found = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
			return found == Items.AIR ? ItemStack.EMPTY : new ItemStack(found, Math.max(1, this.count));
		}
	}

	/** One reward held: what kind it is, who it is from (a town's or a person's name), the game time it came, and what it is. */
	public record Held(String source, String from, long at, List<Part> parts) {
		public static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("source").forGetter(Held::source),
				Codec.STRING.fieldOf("from").forGetter(Held::from),
				Codec.LONG.fieldOf("at").forGetter(Held::at),
				Part.CODEC.listOf().fieldOf("parts").forGetter(Held::parts))
			.apply(i, Held::new));
	}

	/** On a player: the rewards held for them, oldest first. Saved, kept through death, synced to them alone. */
	public static final AttachmentType<List<Held>> HELD = AttachmentRegistry.create(JaymosKingdoms.id("held_rewards"),
		builder -> builder.persistent(Held.CODEC.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.fromCodec(Held.CODEC.listOf()), AttachmentSyncPredicate.targetOnly()));

	/** Player → server: claim the reward at this place in the list, or every one ({@code -1}). */
	public record Claim(int index) implements CustomPacketPayload {
		public static final Type<Claim> TYPE = new Type<>(JaymosKingdoms.id("rewards_claim"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Claim> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.index + 1), buf -> new Claim(buf.readVarInt() - 1));

		@Override
		public Type<Claim> type() {
			return TYPE;
		}
	}

	private Rewards() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Claim.TYPE, Claim.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Claim.TYPE, (payload, context) -> claim(context.player(), payload.index()));
	}

	public static List<Held> of(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(HELD, List.of());
	}

	/**
	 * Holds {@code stacks} for {@code player} as a reward of {@code source} from {@code from}, and tells them. With the list
	 * full ({@link #MOST}) or nothing worth holding the stacks go straight to them instead.
	 */
	public static void hold(ServerPlayer player, String source, String from, List<ItemStack> stacks) {
		hold(player, source, from, stacks, true);
	}

	/** {@link #hold(ServerPlayer, String, String, List)}; {@code tell} false when the caller's own message already says it is held. */
	public static void hold(ServerPlayer player, String source, String from, List<ItemStack> stacks, boolean tell) {
		Map<String, Integer> merged = new LinkedHashMap<>();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				merged.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		if (merged.isEmpty()) {
			return;
		}
		List<Held> held = new ArrayList<>(of(player));
		if (held.size() >= MOST) {
			Money.deposit(player, stacks);
			return;
		}
		List<Part> parts = new ArrayList<>();
		merged.forEach((item, count) -> parts.add(new Part(item, count)));
		held.add(new Held(source, from, player.level().getGameTime(), List.copyOf(parts)));
		player.setAttached(HELD, List.copyOf(held));
		if (tell) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rewards.held", Component.translatable("rewards.jaymoskingdoms.source." + source), from));
		}
	}

	private static void claim(ServerPlayer player, int index) {
		List<Held> held = new ArrayList<>(of(player));
		if (held.isEmpty() || index >= held.size()) {
			return;
		}
		List<Held> taken = index < 0 ? List.copyOf(held) : List.of(held.get(index));
		if (index < 0) {
			held.clear();
		} else {
			held.remove(index);
		}
		// (saved before it is handed over, so a reward is never paid twice)
		if (held.isEmpty()) {
			player.removeAttached(HELD);
		} else {
			player.setAttached(HELD, List.copyOf(held));
		}
		for (Held h : taken) {
			for (Part part : h.parts()) {
				ItemStack stack = part.stack();
				for (int left = stack.getCount(); left > 0; ) {
					int n = Math.min(left, stack.getMaxStackSize());
					Money.deposit(player, stack.copyWithCount(n));
					left -= n;
				}
			}
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
	}
}
