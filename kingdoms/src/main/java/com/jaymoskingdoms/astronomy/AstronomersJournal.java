package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * The Astronomer's Journal: the scholar's book of the sky (crafted from a book and a spyglass). An armillary sphere's
 * readings are written in it ({@link Measurements}), and only while you carry it. Using it opens it on the client, which
 * reads what the server keeps for the player: the measurements, the stars discovered ({@link StarDiscovery}) and the
 * Scholar pillar of the skill tree, whose points are spent on Astrometry from the journal's Skills pages. Its Study pages
 * teach the sky's model with its formulas, and where the model stops being the real sky.
 * <p>
 * The idea of a measuring journal with a skill ladder and study pages comes from Jubileu_13's Astronomy mod (MIT); the
 * code, the text and the art are ours.
 */
public final class AstronomersJournal extends Item {
	public static Item ITEM;

	/** Server to player: open your journal. */
	public record Open() implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("astronomers_journal_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	private AstronomersJournal(Properties properties) {
		super(properties);
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("astronomers_journal"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new AstronomersJournal(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.SPYGLASS, ITEM));
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		Measurements.register();
	}

	/** Whether {@code player} carries a journal to write readings in. */
	public static boolean carries(Player player) {
		return player.getInventory().contains(stack -> stack.is(ITEM));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && ServerPlayNetworking.canSend(sp, Open.TYPE)) {
			ServerPlayNetworking.send(sp, new Open());
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}
}
