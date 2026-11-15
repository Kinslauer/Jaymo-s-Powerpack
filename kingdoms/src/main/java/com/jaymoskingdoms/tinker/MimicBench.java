package com.jaymoskingdoms.tinker;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.money.Money;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Tinkerer's notebook (the class key) and the Mad Tinkerer's gadget trade. The notebook lists every ability a Tinkerer can
 * copy ({@link Mimic}), how far each is studied ({@link MimicStudy}), and, once one is learned, a Build button that turns its parts
 * into the gadget at a bench: a tinkering station of Jaymo's Powers, a crafting table or a smithing table within
 * {@link #BENCH_RANGE} blocks.
 *
 * <p>The Mad Tinkerer (Jaymo's Powers' wandering scientist, entity tag {@link #MAD_TINKERER_TAG}) sells each gadget for
 * {@link #BUY_SILVER} and teaches its drawing for {@link #LEARN_SILVER}, from his own talk menu, and only to Tinkerers (Andy,
 * 2026-11-15: "the mad tinkerer can sell and teach you these too. only to tinkerer").
 */
public final class MimicBench {
	static final int BENCH_RANGE = 4;
	public static final int BUY_SILVER = 12, LEARN_SILVER = 20;
	static final double SHOP_RANGE = 6.0;
	public static final String MAD_TINKERER_TAG = "jaymospowers_scientist";
	/** Jaymo's Powers' tinkering stations, and the plain benches. */
	private static final Set<String> STATIONS = Set.of("tool_station", "tool_forge", "part_builder", "crafting_station", "rig_forge", "star_forge");
	private static final Set<Identifier> BENCHES = Set.of(Identifier.withDefaultNamespace("crafting_table"), Identifier.withDefaultNamespace("smithing_table"));

	public static final int NOTEBOOK = 0, SHOP = 1;
	public static final int BUILD = 0, BUY = 1, LEARN = 2;

	/**
	 * Server → player: the notebook ({@link #NOTEBOOK}) or the Mad Tinkerer's trade ({@link #SHOP}, {@code npc} his entity id):
	 * each ability's study (ticks, in {@link Mimic} order) and whether its parts are in the pack; whether a bench is near; the
	 * prices, and whether they can be paid; the infusions line.
	 */
	public record Open(int mode, int npc, int[] progress, boolean[] parts, boolean bench, Component buyPrice, Component learnPrice,
		boolean canBuy, boolean canLearn, Component infusions) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("mimic_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeVarInt(o.mode);
			buf.writeVarInt(o.npc);
			buf.writeVarInt(o.progress.length);
			for (int i = 0; i < o.progress.length; i++) {
				buf.writeVarInt(o.progress[i]);
				buf.writeBoolean(o.parts[i]);
			}
			buf.writeBoolean(o.bench);
			ComponentSerialization.STREAM_CODEC.encode(buf, o.buyPrice);
			ComponentSerialization.STREAM_CODEC.encode(buf, o.learnPrice);
			buf.writeBoolean(o.canBuy);
			buf.writeBoolean(o.canLearn);
			ComponentSerialization.STREAM_CODEC.encode(buf, o.infusions);
		}, buf -> {
			int mode = buf.readVarInt(), npc = buf.readVarInt(), n = Math.min(buf.readVarInt(), 256);
			int[] progress = new int[n];
			boolean[] parts = new boolean[n];
			for (int i = 0; i < n; i++) {
				progress[i] = buf.readVarInt();
				parts[i] = buf.readBoolean();
			}
			boolean bench = buf.readBoolean();
			Component buy = ComponentSerialization.STREAM_CODEC.decode(buf), learn = ComponentSerialization.STREAM_CODEC.decode(buf);
			boolean canBuy = buf.readBoolean(), canLearn = buf.readBoolean();
			return new Open(mode, npc, progress, parts, bench, buy, learn, canBuy, canLearn, ComponentSerialization.STREAM_CODEC.decode(buf));
		});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: build ({@link #BUILD}), buy ({@link #BUY}) or learn ({@link #LEARN}) the gadget of ability {@code key}. */
	public record Act(int action, String key, int npc) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("mimic_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Act::action,
			ByteBufCodecs.STRING_UTF8, Act::key, ByteBufCodecs.VAR_INT, Act::npc, Act::new);

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	private MimicBench() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload));
	}

	/** Whether {@code player} may study, build and trade for these: a Tinkerer (first or second class). */
	public static boolean tinkerer(ServerPlayer player) {
		return Classes.is(player, PlayerClass.TINKERER);
	}

	/** The class key: the notebook. */
	public static void open(ServerPlayer player, Component infusions) {
		send(player, NOTEBOOK, -1, infusions);
	}

	/**
	 * (Jaymo's Powers calls this by reflection, from the Mad Tinkerer's talk menu.) His gadget trade, for a Tinkerer; false (and
	 * nothing opens) for anyone else, or for anyone who is not standing by him.
	 */
	public static boolean shop(ServerPlayer player, LivingEntity npc) {
		if (!tinkerer(player) || !madTinkerer(player, npc)) {
			return false;
		}
		send(player, SHOP, npc.getId(), Component.empty());
		return true;
	}

	private static boolean madTinkerer(ServerPlayer player, @Nullable Entity npc) {
		return npc instanceof LivingEntity l && l.isAlive() && l.entityTags().contains(MAD_TINKERER_TAG) && l.level() == player.level()
			&& l.distanceTo(player) <= SHOP_RANGE;
	}

	private static void send(ServerPlayer player, int mode, int npc, Component infusions) {
		if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
			return;
		}
		Mimic[] all = Mimic.values();
		int[] progress = new int[all.length];
		boolean[] parts = new boolean[all.length];
		for (int i = 0; i < all.length; i++) {
			progress[i] = MimicStudy.progress(player, all[i]);
			parts[i] = hasParts(player, all[i]);
		}
		long buy = Money.worth(BUY_SILVER), learn = Money.worth(LEARN_SILVER);
		ServerPlayNetworking.send(player, new Open(mode, npc, progress, parts, nearBench(player), Money.text(player, buy),
			Money.text(player, learn), Money.canPay(player, buy), Money.canPay(player, learn), infusions));
	}

	private static void act(ServerPlayer player, Act act) {
		Mimic m = Mimic.byKey(act.key());
		if (m == null || !tinkerer(player) || !player.isAlive()) {
			return;
		}
		Component said;
		boolean ok = false;
		switch (act.action()) {
			case BUILD -> {
				if (!MimicStudy.knows(player, m)) {
					said = Component.translatable("message.jaymoskingdoms.mimic.unknown");
				} else if (!nearBench(player)) {
					said = Component.translatable("message.jaymoskingdoms.mimic.no_bench");
				} else if (!hasParts(player, m)) {
					said = Component.translatable("message.jaymoskingdoms.mimic.no_parts");
				} else {
					if (!player.hasInfiniteMaterials()) {
						for (Mimic.Part part : m.cost) {
							MimicGadgets.take(player, java.util.List.of(part.item()), part.count());
						}
					}
					give(player, m);
					ok = true;
					said = Component.translatable("message.jaymoskingdoms.mimic.built", gadgetName(m));
				}
			}
			case BUY, LEARN -> {
				Entity npc = player.level().getEntity(act.npc());
				boolean learn = act.action() == LEARN;
				long price = Money.worth(learn ? LEARN_SILVER : BUY_SILVER);
				if (!madTinkerer(player, npc)) {
					return;
				}
				if (learn && MimicStudy.knows(player, m)) {
					said = Component.translatable("message.jaymoskingdoms.mimic.known");
				} else if (!Money.charge(player, price)) {
					said = Component.translatable("message.jaymoskingdoms.mimic.poor");
				} else {
					if (learn) {
						MimicStudy.teach(player, m);
						said = Component.translatable("message.jaymoskingdoms.mimic.taught", Component.translatable("mimic.jaymoskingdoms." + m.key()));
					} else {
						give(player, m);
						said = Component.translatable("message.jaymoskingdoms.mimic.bought", gadgetName(m));
					}
					ok = true;
				}
			}
			default -> {
				return;
			}
		}
		if (ok && player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), act.action() == BUILD ? SoundEvents.ANVIL_USE : SoundEvents.VILLAGER_YES,
				SoundSource.PLAYERS, 0.7F, act.action() == BUILD ? 1.4F : 1.0F);
		}
		player.sendOverlayMessage(said);
		send(player, act.action() == BUILD ? NOTEBOOK : SHOP, act.npc(), Component.empty());
	}

	private static Component gadgetName(Mimic m) {
		return new ItemStack(MimicGadgets.ITEMS.get(m)).getHoverName();
	}

	private static void give(ServerPlayer player, Mimic m) {
		ItemStack stack = new ItemStack(MimicGadgets.ITEMS.get(m), m.makes);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	static boolean hasParts(ServerPlayer player, Mimic m) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		for (Mimic.Part part : m.cost) {
			if (MimicGadgets.count(player, java.util.List.of(part.item())) < part.count()) {
				return false;
			}
		}
		return true;
	}

	/** Whether a bench is within {@link #BENCH_RANGE} of {@code player}. */
	static boolean nearBench(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-BENCH_RANGE, -2, -BENCH_RANGE), at.offset(BENCH_RANGE, 2, BENCH_RANGE))) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock());
			if (BENCHES.contains(id) || id.getNamespace().equals("jaymospowers") && STATIONS.contains(id.getPath())) {
				return true;
			}
		}
		return false;
	}
}
