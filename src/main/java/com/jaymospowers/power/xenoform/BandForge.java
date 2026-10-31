package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.tinkering.Tinkering;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.123, Xeno-Band patch 7) The band's reforge. Each save has one far world that holds the star forge ({@link #WORLD},
 * picked at random from {@link #CHOICES} the first time it's asked for). The first time a band wearer walks that world, a
 * smithy is raised {@link #BUILD_MIN}-{@link #BUILD_MAX} blocks off ({@link #AT}): a stone hut with the Star Forge and, with
 * Kingdoms and MCA, an alien smith ({@link #SMITH_TAG}) who keeps it. A band not yet reforged hums toward the forge while
 * you're on its world ({@link #TUG_EVERY}). Bring {@link #METAL} star metal, a wreck core and {@link #DUST} of Bulwark's dust
 * (any metal) and the forge, or the smith, reforges your own band: it then has {@link BandUpgrades#SLOTS} module slots.
 */
public final class BandForge {
	// ---- tuning ----
	/** The far worlds the forge may stand on (pod destinations; not the Maw's void or the future). */
	public static final int[] CHOICES = {ZFighterSpace.MOON, ZFighterSpace.RUSTFALL, ZFighterSpace.GLACIA, ZFighterSpace.CINDER, ZFighterSpace.VERDANT};
	/** How far from the first band wearer the smithy is raised (blocks). */
	public static final double BUILD_MIN = 40.0, BUILD_MAX = 64.0;
	/** How often (ticks) the forge's world is looked at: building, and the band's hum. */
	public static final int CHECK_EVERY = 100, TUG_EVERY = 400;
	/** What a reforge takes. */
	public static final int METAL = 4, CORES = 1, DUST = 4;
	/** Lines in the lang file for the smith's talks. */
	static final int GREET_LINES = 3, AGAIN_LINES = 2;

	public static final String SMITH_TAG = "jaymospowers_star_smith";

	/** The save's forge world (a pod destination), kept on the overworld. */
	public static final AttachmentType<Integer> WORLD = AttachmentRegistry.create(JaymosPowers.id("band_forge_world"),
		builder -> builder.persistent(Codec.INT));
	/** Where the forge stands, kept on its world. */
	public static final AttachmentType<BlockPos> AT = AttachmentRegistry.create(JaymosPowers.id("band_forge_at"),
		builder -> builder.persistent(BlockPos.CODEC));

	public static Block FORGE;
	public static Item FORGE_ITEM;

	private BandForge() {
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("star_forge"));
		FORGE = Registry.register(BuiltInRegistries.BLOCK, key, new StarForgeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(-1.0F, 3600000.0F)
			.lightLevel(state -> 12)
			.sound(SoundType.NETHERITE_BLOCK)
			.pushReaction(PushReaction.IMMOVEABLE)
			.noLootTable()
			.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("star_forge"));
		FORGE_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(FORGE, new Item.Properties().setId(itemKey)
			.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
		ServerTickEvents.END_SERVER_TICK.register(BandForge::tick);
	}

	/** The Star Forge: use it with the makings in your pack and it reforges your band. Can't be broken or moved. */
	public static final class StarForgeBlock extends Block {
		public StarForgeBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer server) {
				reforge(server, Vec3.atCenterOf(pos));
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- where

	/** The save's forge world (a pod destination), picked the first time. */
	public static int world(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		Integer world = overworld.getAttached(WORLD);
		if (world == null) {
			world = CHOICES[overworld.getRandom().nextInt(CHOICES.length)];
			overworld.setAttached(WORLD, world);
		}
		return world;
	}

	/** The forge world's key ("moon", "rustfall"...), for talk. */
	public static String worldKey(MinecraftServer server) {
		return ZFighterSpace.DESTS[world(server)].key();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		ServerLevel level = server.getLevel(ZFighterSpace.DESTS[world(server)].level());
		if (level == null) {
			return;
		}
		BlockPos at = level.getAttached(AT);
		boolean tug = server.getTickCount() % TUG_EVERY == 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !XenoformPower.has(player)) {
				continue;
			}
			if (at == null) {
				at = build(level, player);
				continue;
			}
			if (tug && !BandUpgrades.get(player).reforged()) {
				hum(player, at);
			}
		}
	}

	/** The band hums toward the forge: which way, and roughly how far. */
	private static void hum(ServerPlayer player, BlockPos at) {
		double dx = at.getX() + 0.5 - player.getX(), dz = at.getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 12.0) {
			return;
		}
		// Minecraft's compass: south is +z; eight ways round from south
		int way = Math.floorMod(Math.round((float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) / 45.0F), 8);
		String[] ways = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
		String far = distance < 64.0 ? "near" : distance < 256.0 ? "mid" : "far";
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.band.hum." + far,
			Component.translatable("message.jaymospowers.band.way." + ways[way])).withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	/** Raises the smithy some way off from {@code near}; null when the ground there isn't loaded. */
	private static @Nullable BlockPos build(ServerLevel level, ServerPlayer near) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0, out = BUILD_MIN + random.nextDouble() * (BUILD_MAX - BUILD_MIN);
		int x = Mth.floor(near.getX() + Math.cos(angle) * out), z = Mth.floor(near.getZ() + Math.sin(angle) * out);
		BlockPos probe = new BlockPos(x, near.getBlockY(), z);
		if (!level.isLoaded(probe) || !level.isLoaded(probe.offset(4, 0, 4)) || !level.isLoaded(probe.offset(-4, 0, -4))) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos centre = new BlockPos(x, y, z);
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState roof = Blocks.TINTED_GLASS.defaultBlockState(), corner = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
				boolean cornerPost = Math.abs(dx) == 4 && Math.abs(dz) == 4;
				// a footing down to the ground, the floor, the walls (a door on the south side), the roof
				for (int dy = -4; dy < 0; dy++) {
					BlockPos p = centre.offset(dx, dy, dz);
					if (level.getBlockState(p).canBeReplaced()) {
						level.setBlock(p, floor, Block.UPDATE_CLIENTS);
					}
				}
				level.setBlock(centre.offset(dx, -1, dz), floor, Block.UPDATE_CLIENTS);
				for (int dy = 0; dy < 4; dy++) {
					BlockPos p = centre.offset(dx, dy, dz);
					boolean door = dz == 4 && Math.abs(dx) <= 1 && dy < 3;
					level.setBlock(p, edge && !door ? cornerPost ? corner : wall : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				level.setBlock(centre.offset(dx, 4, dz), edge ? wall : roof, Block.UPDATE_CLIENTS);
			}
		}
		BlockPos forge = centre.offset(0, 0, -2);
		level.setBlock(forge, FORGE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(centre.offset(-2, 0, -3), Blocks.BLAST_FURNACE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(centre.offset(2, 0, -3), Blocks.ANVIL.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(centre.offset(-3, 1, 0), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(centre.offset(3, 1, 0), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
		level.setAttached(AT, forge);
		Vec3 post = Vec3.atBottomCenterOf(centre.offset(1, 0, -1));
		ZFighterNpcs.villager(level, post, 180.0F, SMITH_TAG, "npc.jaymospowers.star_smith", random.nextBoolean());
		level.sendParticles(ParticleTypes.END_ROD, forge.getX() + 0.5, forge.getY() + 1.0, forge.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.05);
		return forge;
	}

	// ---------------------------------------------------------------- the reforge

	/** What {@code player} still lacks for a reforge, or an empty list. */
	private static List<Component> lacking(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		int metal = Tinkering.STAR_METAL_INGOT == null ? 0 : count(player, Tinkering.STAR_METAL_INGOT);
		if (metal < METAL) {
			out.add(Component.translatable("message.jaymospowers.band.need.metal", METAL - metal));
		}
		int cores = count(player, XenoFinds.WRECK_CORE);
		if (cores < CORES) {
			out.add(Component.translatable("message.jaymospowers.band.need.core", CORES - cores));
		}
		int dust = dust(player);
		if (dust < DUST) {
			out.add(Component.translatable("message.jaymospowers.band.need.dust", DUST - dust));
		}
		return out;
	}

	/** Reforges {@code player}'s band at {@code at}: true when it was done. */
	static boolean reforge(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.level();
		if (!XenoformPower.has(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.band.forge_no_band").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (BandUpgrades.get(player).reforged()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.band.forge_done").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (XenoformState.transformed(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.band.forge_transformed").withStyle(ChatFormatting.GRAY));
			return false;
		}
		List<Component> lacking = lacking(player);
		if (!lacking.isEmpty()) {
			Component list = Component.empty();
			for (int i = 0; i < lacking.size(); i++) {
				list = list.copy().append(i == 0 ? Component.empty() : Component.literal(", ")).append(lacking.get(i));
			}
			player.sendSystemMessage(Component.translatable("message.jaymospowers.band.forge_lacking", list).withStyle(ChatFormatting.GRAY));
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
			return false;
		}
		take(player, Tinkering.STAR_METAL_INGOT, METAL);
		take(player, XenoFinds.WRECK_CORE, CORES);
		int dust = DUST;
		for (Item item : new Item[] {XenoFinds.IRON_DUST, XenoFinds.COPPER_DUST, XenoFinds.GOLD_DUST}) {
			int n = Math.min(dust, count(player, item));
			take(player, item, n);
			dust -= n;
		}
		player.setAttached(BandUpgrades.UPGRADES, BandUpgrades.get(player).reforge());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.6, at.z, 12, 0.3, 0.2, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.band.reforged", BandUpgrades.SLOTS).withStyle(ChatFormatting.GREEN));
		return true;
	}

	private static int dust(Player player) {
		return count(player, XenoFinds.IRON_DUST) + count(player, XenoFinds.COPPER_DUST) + count(player, XenoFinds.GOLD_DUST);
	}

	static int count(Player player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static void take(Player player, Item item, int count) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}

	// ---------------------------------------------------------------- the smith's talk

	/** From MCA's "Talk about work" ({@link ZFighterNpcs#talk}). */
	public static void talk(ServerPlayer player, LivingEntity smith) {
		if (smith instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		menu(player, smith, line("greet", GREET_LINES, smith));
	}

	private static void menu(ServerPlayer player, LivingEntity smith, Component words) {
		Runnable back = () -> menu(player, smith, line("again", AGAIN_LINES, smith));
		List<ZFighterNpcs.Option> backOnly = List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back));
		List<ZFighterNpcs.Option> options = new ArrayList<>();
		boolean band = XenoformPower.has(player);
		boolean done = BandUpgrades.get(player).reforged();
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_smith.opt.reforge", band && !done, () -> {
			List<Component> lacking = band ? lacking(player) : List.of();
			if (!lacking.isEmpty()) {
				ZFighterNpcs.ask(player, smith, Component.translatable("talk.jaymospowers.star_smith.lacking", METAL, DUST), backOnly);
				return;
			}
			boolean made = reforge(player, smith.position());
			ZFighterNpcs.ask(player, smith, Component.translatable(made ? "talk.jaymospowers.star_smith.made" : "talk.jaymospowers.star_smith.not_now"), backOnly);
		}));
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_smith.opt.modules", true,
			() -> ZFighterNpcs.ask(player, smith, Component.translatable("talk.jaymospowers.star_smith.modules"), backOnly)));
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_smith.opt.points", true,
			() -> ZFighterNpcs.ask(player, smith, Component.translatable("talk.jaymospowers.star_smith.points"), backOnly)));
		ZFighterNpcs.ask(player, smith, done ? Component.translatable("talk.jaymospowers.star_smith.reforged") : words, options);
	}

	private static Component line(String name, int count, LivingEntity smith) {
		return Component.translatable("talk.jaymospowers.star_smith." + name + "." + (1 + smith.getRandom().nextInt(count)));
	}
}
