package com.jaymospowers.alchemy.oil;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.bomb.Bombs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The oil sash and the bandolier (Hunter's Road, patch 7, "T-C3"; the idea is Toxony's, written clean-room): a strap of small
 * flasks, filled from an oil pot ({@link OilPotBlock}; use it on the pot) and thrown a flask at a time as a bomb
 * ({@link Bombs}). No free projectiles: every throw spends a filled charge. The sash holds one oil, {@link #SASH_THROWS} throws;
 * the bandolier holds up to {@link #BANDOLIER_OILS} oils, {@link #BANDOLIER_THROWS} throws of each. Use throws the oil showing;
 * sneak and use turns to the next.
 * <p>
 * The endless bandolier (patch 8's forge, which costs toxin a throw) asks {@link #endless}, the one place a throw is paid for.
 */
public final class OilSashItem extends Item {
	/** Throws a sash holds; throws of each oil a bandolier holds, and how many oils. */
	public static final int SASH_THROWS = 16, BANDOLIER_THROWS = 12, BANDOLIER_OILS = 3;

	/** One oil in a strap: which, its grade, and the throws left. */
	public record Pocket(String kind, int tier, int count) {
		public static final Codec<Pocket> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Pocket::kind), Codec.INT.fieldOf("tier").forGetter(Pocket::tier),
			Codec.INT.fieldOf("count").forGetter(Pocket::count)).apply(i, Pocket::new));
		public static final StreamCodec<ByteBuf, Pocket> STREAM = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Pocket::kind, ByteBufCodecs.VAR_INT, Pocket::tier, ByteBufCodecs.VAR_INT, Pocket::count, Pocket::new);
	}

	/** What a strap holds: the oil showing (an index) and its pockets. */
	public record Pouch(int active, List<Pocket> pockets) {
		public static final Pouch EMPTY = new Pouch(0, List.of());
		public static final Codec<Pouch> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("active").forGetter(Pouch::active), Pocket.CODEC.listOf().fieldOf("pockets").forGetter(Pouch::pockets)).apply(i, Pouch::new));
		public static final StreamCodec<ByteBuf, Pouch> STREAM = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Pouch::active, Pocket.STREAM.apply(ByteBufCodecs.list()), Pouch::pockets, Pouch::new);

		@Nullable Pocket showing() {
			return this.pockets.isEmpty() ? null : this.pockets.get(Math.floorMod(this.active, this.pockets.size()));
		}
	}

	public static DataComponentType<Pouch> POUCH;
	public static Item SASH, BANDOLIER;

	private final int oils, throwsOf;

	private OilSashItem(Item.Properties properties, int oils, int throwsOf) {
		super(properties);
		this.oils = oils;
		this.throwsOf = throwsOf;
	}

	static void init() {
		POUCH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("oil_pouch"),
			DataComponentType.<Pouch>builder().persistent(Pouch.CODEC).networkSynchronized(Pouch.STREAM).build());
		SASH = Alchemy.item("oil_sash", p -> new OilSashItem(p.stacksTo(1), 1, SASH_THROWS));
		BANDOLIER = Alchemy.item("oil_bandolier", p -> new OilSashItem(p.stacksTo(1), BANDOLIER_OILS, BANDOLIER_THROWS));
	}

	/**
	 * Whether a throw from {@code strap} costs nothing in oil. Always false for now: the endless bandolier (patch 8, the alchemical
	 * forge) answers true for itself and takes its toxin here.
	 */
	public static boolean endless(ItemStack strap) {
		return false;
	}

	/**
	 * Fills {@code strap} with up to {@code available} throws of {@code kind} at {@code tier}, from a pot. Returns the throws
	 * taken (0 if the strap can't take it: full, or every pocket holds something else).
	 */
	public static int fill(ItemStack strap, OilKind kind, int tier, int available) {
		if (!(strap.getItem() instanceof OilSashItem item) || available <= 0) {
			return 0;
		}
		Pouch pouch = strap.getOrDefault(POUCH, Pouch.EMPTY);
		List<Pocket> pockets = new ArrayList<>(pouch.pockets());
		int at = -1;
		for (int i = 0; i < pockets.size(); i++) {
			if (pockets.get(i).kind().equals(kind.id)) {
				at = i;
			}
		}
		if (at < 0 && pockets.size() >= item.oils) {
			return 0;
		}
		Pocket was = at < 0 ? new Pocket(kind.id, tier, 0) : pockets.get(at);
		int taken = Math.min(available, item.throwsOf - was.count());
		if (taken <= 0) {
			return 0;
		}
		Pocket now = new Pocket(kind.id, Math.min(tier, was.tier()), was.count() + taken);
		if (at < 0) {
			pockets.add(now);
			at = pockets.size() - 1;
		} else {
			pockets.set(at, now);
		}
		strap.set(POUCH, new Pouch(at, List.copyOf(pockets)));
		return taken;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack strap = player.getItemInHand(hand);
		Pouch pouch = strap.getOrDefault(POUCH, Pouch.EMPTY);
		Pocket pocket = pouch.showing();
		if (pocket == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				strap.set(POUCH, new Pouch(Math.floorMod(pouch.active() + 1, pouch.pockets().size()), pouch.pockets()));
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.3F);
			return InteractionResult.SUCCESS;
		}
		OilKind kind = OilKind.byId(pocket.kind());
		if (kind == null) {
			return InteractionResult.PASS;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
		if (level instanceof ServerLevel server) {
			Bombs.throwFrom(server, player, Oils.flask(kind, pocket.tier()));
			if (!endless(strap)) {
				List<Pocket> pockets = new ArrayList<>(pouch.pockets());
				int at = Math.floorMod(pouch.active(), pockets.size());
				if (pocket.count() <= 1) {
					pockets.remove(at);
				} else {
					pockets.set(at, new Pocket(pocket.kind(), pocket.tier(), pocket.count() - 1));
				}
				strap.set(POUCH, new Pouch(pockets.isEmpty() ? 0 : Math.floorMod(at, pockets.size()), List.copyOf(pockets)));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		Pouch pouch = stack.getOrDefault(POUCH, Pouch.EMPTY);
		if (pouch.pockets().isEmpty()) {
			builder.accept(Component.translatable("tooltip.jaymospowers.oil_sash.empty").withStyle(ChatFormatting.GRAY));
		}
		for (int i = 0; i < pouch.pockets().size(); i++) {
			Pocket p = pouch.pockets().get(i);
			OilKind kind = OilKind.byId(p.kind());
			if (kind != null) {
				boolean showing = i == Math.floorMod(pouch.active(), pouch.pockets().size());
				builder.accept(Component.translatable("tooltip.jaymospowers.oil_sash.pocket", kind.flask.getName(new ItemStack(kind.flask)), p.count())
					.withStyle(showing ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			}
		}
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
