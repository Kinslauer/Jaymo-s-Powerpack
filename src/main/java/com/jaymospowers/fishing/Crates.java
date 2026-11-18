package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Crates on the line (Andy picked it from the Tide list): now and then a landed fish brings a wet crate up with it, from
 * whatever water the line was in: river, sea, swamp or lava. Open one (use) for what is in it, which is that water's own
 * ({@code data/jaymospowers/loot_table/gameplay/crate_<water>.json}: bait and lures, nuggets, whatever the water keeps).
 * A lucky angler finds them a little more often.
 */
public final class Crates {
	/** A landed fish brings a crate up with it this often (before luck: each point of luck adds half again). */
	static final float CRATE_CHANCE = 0.03F;
	/** The most the chance is multiplied by, whatever the luck. */
	static final float LUCK_MOST = 3.0F;

	/** The water a crate came out of. */
	public enum Water {
		RIVER("river"), SEA("sea"), SWAMP("swamp"), LAVA("lava");

		public final String id;
		public final ResourceKey<LootTable> loot;

		Water(String id) {
			this.id = id;
			this.loot = ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("gameplay/crate_" + id));
		}
	}

	private static final Map<Water, Item> ITEMS = new EnumMap<>(Water.class);

	private Crates() {
	}

	static void init() {
		for (Water water : Water.values()) {
			String id = water.id + "_crate";
			Item.Properties props = Tackle.props(id).stacksTo(16);
			ITEMS.put(water, Tackle.register(id, new CrateItem(water == Water.LAVA ? props.fireResistant() : props, water)));
		}
	}

	/** Which water {@code pos} is, for a crate: lava if {@code lava}, the sea, a swamp's, or else a river's or lake's. */
	static Water waterOf(ServerLevel level, BlockPos pos, boolean lava) {
		if (lava) {
			return Water.LAVA;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (Species.sea(biome)) {
			return Water.SEA;
		}
		return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? Water.SWAMP : Water.RIVER;
	}

	/** A crate for the fish just landed at {@code pos}, or nothing (most times). */
	static List<ItemStack> maybe(ServerPlayer player, ServerLevel level, BlockPos pos, boolean lava) {
		float chance = CRATE_CHANCE * Math.min(LUCK_MOST, 1.0F + 0.5F * Math.max(0.0F, player.getLuck()));
		if (level.getRandom().nextFloat() >= chance) {
			return List.of();
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.crate_up").withStyle(ChatFormatting.GOLD));
		return List.of(new ItemStack(ITEMS.get(waterOf(level, pos, lava))));
	}

	/** A crate: use it to open it. */
	static final class CrateItem extends Item {
		private final Water water;

		CrateItem(Item.Properties properties, Water water) {
			super(properties);
			this.water = water;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer opener) {
				ItemStack stack = player.getItemInHand(hand);
				LootParams params = new LootParams.Builder(server)
					.withParameter(LootContextParams.ORIGIN, player.position())
					.withParameter(LootContextParams.THIS_ENTITY, player)
					.withLuck(player.getLuck())
					.create(LootContextParamSets.CHEST);
				List<ItemStack> items = server.getServer().reloadableRegistries().getLootTable(water.loot).getRandomItems(params);
				stack.shrink(1);
				for (ItemStack item : items) {
					if (!player.addItem(item)) {
						player.spawnAtLocation(server, item, 1.0F);
					}
				}
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.8F, 1.1F);
				server.sendParticles(water == Water.LAVA ? ParticleTypes.LAVA : ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0,
					player.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
				opener.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.crate_opened").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
