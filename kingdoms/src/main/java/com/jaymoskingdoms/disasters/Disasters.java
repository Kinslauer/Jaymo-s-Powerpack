package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * (0.6.12) Natural disasters, stage 1: storm fronts ({@link Storms}) and tornadoes ({@link Tornadoes}), the instruments
 * that watch for them ({@link Instruments}), two game rules, and the op command {@code /kingdom disaster}. (0.7.31)
 * Stage 2, the land's own: sandstorms, blizzards, wildfires, floods and hail ({@link Calamities}).
 */
public final class Disasters {
	/** {@code jaymoskingdoms:disaster_damage}: whether tornadoes tear up blocks. */
	public static GameRule<Boolean> DISASTER_DAMAGE;
	/** {@code jaymoskingdoms:disaster_days}: on average, a disaster this many days apart (0: none). */
	public static GameRule<Integer> DISASTER_DAYS;
	static final int DEFAULT_DAYS = 6;

	public static Block STORM_BELL, WEATHERVANE;
	/** (0.7.31) The sand a sandstorm piles up, in layers like snow. */
	public static Block SAND_LAYER, RED_SAND_LAYER;
	public static Item STORM_BELL_ITEM, WEATHERVANE_ITEM, BAROMETER;
	/** (0.7.31) The almanac: use it to read what disasters are foretold near you. */
	public static Item ALMANAC;
	/** The client's storm-cloud and funnel particles (spawned by the client only, from its copy of the storms). */
	public static SimpleParticleType STORM_CLOUD, FUNNEL;
	/** (0.7.31) The client's blowing sand, snow and hail (stage 2, {@link Calamities}). */
	public static SimpleParticleType GUST;

	private Disasters() {
	}

	public static void register() {
		DISASTER_DAMAGE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("disaster_damage"));
		DISASTER_DAYS = GameRuleBuilder.forInteger(DEFAULT_DAYS).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("disaster_days"));
		STORM_CLOUD = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("storm_cloud"), FabricParticleTypes.simple(true));
		FUNNEL = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("funnel"), FabricParticleTypes.simple(true));
		GUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("gust"), FabricParticleTypes.simple(true));
		SAND_LAYER = block("sand_layer", SandLayerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
			.strength(0.2F).sound(SoundType.SAND).replaceable().forceSolidOff().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
		RED_SAND_LAYER = block("red_sand_layer", SandLayerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.strength(0.2F).sound(SoundType.SAND).replaceable().forceSolidOff().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
		STORM_BELL = block("storm_bell", Instruments.StormBell::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
			.strength(3.0F).sound(SoundType.ANVIL).noOcclusion().requiresCorrectToolForDrops());
		WEATHERVANE = block("weathervane", Instruments.Weathervane::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(2.0F).sound(SoundType.COPPER).noOcclusion());
		STORM_BELL_ITEM = blockItem("storm_bell", STORM_BELL);
		WEATHERVANE_ITEM = blockItem("weathervane", WEATHERVANE);
		ResourceKey<Item> barometer = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("barometer"));
		BAROMETER = Registry.register(BuiltInRegistries.ITEM, barometer, new Item(new Item.Properties().setId(barometer).stacksTo(1)));
		ResourceKey<Item> almanac = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("almanac"));
		ALMANAC = Registry.register(BuiltInRegistries.ITEM, almanac, new Almanac(new Item.Properties().setId(almanac).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.CLOCK, BAROMETER);
			output.insertAfter(BAROMETER, ALMANAC);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.insertAfter(Items.DAYLIGHT_DETECTOR, STORM_BELL_ITEM);
			output.insertAfter(STORM_BELL_ITEM, WEATHERVANE_ITEM);
		});
		StormNet.register();
		Calamities.register();
		ServerTickEvents.END_SERVER_TICK.register(Storms::tick);
		ServerTickEvents.END_SERVER_TICK.register(Calamities::tick);
		// (0.7.31) floods drain before the world is saved
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Calamities.clear(server.overworld()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Storms.clear());
	}

	private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/**
	 * {@code /kingdom disaster storm|thunder|supercell|dust_devil|tornado|monster|clear} (ops): a storm cell right
	 * overhead, or a tornado touching down 60 blocks ahead of you (under a supercell of its own); or clear them all.
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("disaster").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("storm").executes(c -> cell(c, Storm.RAIN)))
			.then(Commands.literal("thunder").executes(c -> cell(c, Storm.THUNDER)))
			.then(Commands.literal("supercell").executes(c -> cell(c, Storm.SUPERCELL)))
			.then(Commands.literal("dust_devil").executes(c -> tornado(c, Tornado.DUST_DEVIL)))
			.then(Commands.literal("tornado").executes(c -> tornado(c, Tornado.TORNADO)))
			.then(Commands.literal("monster").executes(c -> tornado(c, Tornado.MONSTER)))
			.then(Commands.literal("sandstorm").executes(c -> land(c, "sandstorm")))
			.then(Commands.literal("blizzard").executes(c -> land(c, "blizzard")))
			.then(Commands.literal("wildfire").executes(c -> land(c, "wildfire")))
			.then(Commands.literal("flood").executes(c -> land(c, "flood")))
			.then(Commands.literal("hail").executes(c -> land(c, "hail")))
			.then(Commands.literal("quake").executes(c -> land(c, "quake")))
			.then(Commands.literal("forecast").executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
					return 0;
				}
				Calamities.foretellNow(level, p);
				c.getSource().sendSuccess(() -> Component.translatable("disaster.jaymoskingdoms.foretold"), true);
				return 1;
			}))
			.then(Commands.literal("clear").executes(c -> {
				Storms.clear();
				Calamities.clear(c.getSource().getServer().overworld());
				c.getSource().sendSuccess(() -> Component.translatable("disaster.jaymoskingdoms.cleared"), true);
				return 1;
			}));
	}

	private static int cell(CommandContext<CommandSourceStack> c, int kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			c.getSource().sendFailure(Component.translatable("disaster.jaymoskingdoms.overworld"));
			return 0;
		}
		Storm s = Storms.form(level, p.position(), kind, level.getRandom(), false);
		s.spawned = true;
		c.getSource().sendSuccess(() -> Component.translatable("disaster.jaymoskingdoms.formed"), true);
		return 1;
	}

	/** (0.7.31) One of stage 2's, by you (a wildfire upwind, a flood on the nearest water, hail on a storm coming in). */
	private static int land(CommandContext<CommandSourceStack> c, String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			c.getSource().sendFailure(Component.translatable("disaster.jaymoskingdoms.overworld"));
			return 0;
		}
		if (!Calamities.start(level, p, kind, level.getRandom())) {
			c.getSource().sendFailure(Component.translatable("disaster.jaymoskingdoms.cant." + kind));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("disaster.jaymoskingdoms.formed"), true);
		return 1;
	}

	private static int tornado(CommandContext<CommandSourceStack> c, int size) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			c.getSource().sendFailure(Component.translatable("disaster.jaymoskingdoms.overworld"));
			return 0;
		}
		Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.normalize();
		Vec3 at = p.position().add(look.scale(60.0));
		Storm storm = null;
		if (size != Tornado.DUST_DEVIL) {
			storm = Storms.form(level, at, Storm.SUPERCELL, level.getRandom(), false);
			storm.spawned = true;
			// it comes your way
			double speed = Math.sqrt(storm.vx * storm.vx + storm.vz * storm.vz);
			storm.vx = -look.x * speed;
			storm.vz = -look.z * speed;
		}
		Tornadoes.spawn(level, size, storm, at.x, at.z, level.getRandom());
		c.getSource().sendSuccess(() -> Component.translatable("disaster.jaymoskingdoms.formed"), true);
		return 1;
	}
}
