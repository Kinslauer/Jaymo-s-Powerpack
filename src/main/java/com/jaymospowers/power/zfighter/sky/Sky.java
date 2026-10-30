package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * (0.18.18, Andy's SevTech picks: "Reading the sky") The star charts and the Star-Sight Bench. A world is on the pod's star
 * map only once its chart is read ({@link Charts}); the first chart (the Pale Moon) comes from the bench ({@link StarBench}),
 * the Rustfall chart from the Patrol, the others from sky hermits and stargazing villagers (Jaymo's Kingdoms, through
 * {@link SkyApi}). The sky itself (a point of light for each world read) is drawn by the client.
 */
public final class Sky {
	// ---- tuning ----
	/** Sentences on a chart's page (lang {@code book.jaymospowers.star_chart.<world>.1..n}). */
	static final int PAGE_LINES = 4;
	/** How often (ticks) a player standing in a far world is noted as having reached it. */
	static final int ARRIVAL_TICKS = 40;

	/** The world a chart is of (a destination key such as {@code moon}). */
	public static DataComponentType<String> CHART_WORLD;
	public static Item STAR_CHART;
	public static Block STAR_BENCH;
	private static boolean ready;

	/** Server → client: show the book page of {@code world}'s chart. */
	public record Page(int world) implements CustomPacketPayload {
		public static final Type<Page> TYPE = new Type<>(JaymosPowers.id("zfighter_sky_page"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeVarInt(p.world),
			buf -> new Page(buf.readVarInt()));

		@Override
		public Type<Page> type() {
			return TYPE;
		}
	}

	private Sky() {
	}

	/** Registers the chart, the bench, their creative entries and the events. Called once, from the Z Fighter power's init. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		CHART_WORLD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("star_chart_world"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		ResourceKey<Item> chartKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("star_chart"));
		STAR_CHART = Registry.register(BuiltInRegistries.ITEM, chartKey, new StarChartItem(new Item.Properties().setId(chartKey).stacksTo(1)
			.rarity(Rarity.UNCOMMON)));
		ResourceKey<Block> benchKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("star_bench"));
		STAR_BENCH = Registry.register(BuiltInRegistries.BLOCK, benchKey, new StarBench(BlockBehaviour.Properties.of().strength(2.0F, 6.0F)
			.noOcclusion().sound(SoundType.STONE).lightLevel(state -> state.getValue(StarBench.AWAKE) ? StarBench.AWAKE_LIGHT : 0).setId(benchKey)));
		ResourceKey<Item> benchItemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("star_bench"));
		Item benchItem = Registry.register(BuiltInRegistries.ITEM, benchItemKey,
			new BlockItem(STAR_BENCH, new Item.Properties().setId(benchItemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(benchItem));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (int world = 0; world < Charts.WORLDS; world++) {
				output.accept(Charts.stack(world));
			}
		});
		PayloadTypeRegistry.clientboundPlay().register(Page.TYPE, Page.CODEC);
		// worlds reached before charts existed count as read, once, as a player is first seen
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Charts.credit(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(Sky::tick);
	}

	/** A player standing in a far world has been there: its chart counts as read. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % ARRIVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int here = Charts.indexHere(player);
			if (here >= 0) {
				Charts.mark(player, here);
			}
		}
	}
}
