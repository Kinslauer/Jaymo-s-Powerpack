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
 * (0.6.9) Natural disasters, stage 1: storm fronts ({@link Storms}) and tornadoes ({@link Tornadoes}), the instruments
 * that watch for them ({@link Instruments}), two game rules, and the op command {@code /kingdom disaster}.
 */
public final class Disasters {
	/** {@code jaymoskingdoms:disaster_damage}: whether tornadoes tear up blocks. */
	public static GameRule<Boolean> DISASTER_DAMAGE;
	/** {@code jaymoskingdoms:disaster_days}: on average, a disaster this many days apart (0: none). */
	public static GameRule<Integer> DISASTER_DAYS;
	static final int DEFAULT_DAYS = 6;

	public static Block STORM_BELL, WEATHERVANE;
	public static Item STORM_BELL_ITEM, WEATHERVANE_ITEM, BAROMETER;
	/** The client's storm-cloud and funnel particles (spawned by the client only, from its copy of the storms). */
	public static SimpleParticleType STORM_CLOUD, FUNNEL;

	private Disasters() {
	}

	public static void register() {
		DISASTER_DAMAGE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("disaster_damage"));
		DISASTER_DAYS = GameRuleBuilder.forInteger(DEFAULT_DAYS).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("disaster_days"));
		STORM_CLOUD = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("storm_cloud"), FabricParticleTypes.simple(true));
		FUNNEL = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("funnel"), FabricParticleTypes.simple(true));
		STORM_BELL = block("storm_bell", Instruments.StormBell::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
			.strength(3.0F).sound(SoundType.ANVIL).noOcclusion().requiresCorrectToolForDrops());
		WEATHERVANE = block("weathervane", Instruments.Weathervane::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(2.0F).sound(SoundType.COPPER).noOcclusion());
		STORM_BELL_ITEM = blockItem("storm_bell", STORM_BELL);
		WEATHERVANE_ITEM = blockItem("weathervane", WEATHERVANE);
		ResourceKey<Item> barometer = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("barometer"));
		BAROMETER = Registry.register(BuiltInRegistries.ITEM, barometer, new Item(new Item.Properties().setId(barometer).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.CLOCK, BAROMETER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.insertAfter(Items.DAYLIGHT_DETECTOR, STORM_BELL_ITEM);
			output.insertAfter(STORM_BELL_ITEM, WEATHERVANE_ITEM);
		});
		StormNet.register();
		ServerTickEvents.END_SERVER_TICK.register(Storms::tick);
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
			.then(Commands.literal("clear").executes(c -> {
				Storms.clear();
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
