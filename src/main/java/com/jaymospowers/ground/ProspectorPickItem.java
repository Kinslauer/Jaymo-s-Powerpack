package com.jaymospowers.ground;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The prospector's pick (Andy's TFC Hardrock picks; forged at the Tool Station from a copper Prospector's Pick Head, a
 * Tool Handle and a Tool Binding, {@code tinkering/WeaponParts}). Tapped on rock, it tells you what ore lies within
 * {@link #RADIUS} blocks and roughly how much: nothing, traces, or a lot. It never says where, so you still have to dig.
 */
public class ProspectorPickItem extends Item {
	/** How far it hears (blocks), how many ore blocks make "a lot", and the ticks between taps. */
	static final int RADIUS = 12, A_LOT = 24, COOLDOWN = 20;

	/** The ores it can tell apart, each with its name in lang ({@code ore_kind.jaymospowers.<name>}). */
	static final List<Map.Entry<TagKey<Block>, String>> KINDS = List.of(
		Map.entry(ConventionalBlockTags.IRON_ORES, "iron"),
		Map.entry(ConventionalBlockTags.COPPER_ORES, "copper"),
		Map.entry(ConventionalBlockTags.GOLD_ORES, "gold"),
		Map.entry(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores/silver")), "silver"),
		Map.entry(ConventionalBlockTags.COAL_ORES, "coal"),
		Map.entry(ConventionalBlockTags.REDSTONE_ORES, "redstone"),
		Map.entry(ConventionalBlockTags.LAPIS_ORES, "lapis"),
		Map.entry(ConventionalBlockTags.EMERALD_ORES, "emerald"),
		Map.entry(ConventionalBlockTags.DIAMOND_ORES, "diamond"),
		Map.entry(ConventionalBlockTags.QUARTZ_ORES, "quartz"),
		Map.entry(ConventionalBlockTags.NETHERITE_SCRAP_ORES, "debris"));

	public ProspectorPickItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockState hit = context.getLevel().getBlockState(context.getClickedPos());
		if (!hit.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return InteractionResult.PASS;
		}
		Player player = context.getPlayer();
		if (player == null || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos at = context.getClickedPos();
		level.playSound(null, at, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.sendOverlayMessage(report(level, at));
		player.getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN);
		context.getItemInHand().hurtAndBreak(1, player, context.getHand());
		return InteractionResult.SUCCESS;
	}

	/** What it hears round {@code at}: the most common ore, and the next one if there is one. */
	static Component report(ServerLevel level, BlockPos at) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				p.set(at.getX() + dx, at.getY(), at.getZ() + dz);
				if (!level.isLoaded(p)) {
					continue;
				}
				for (int dy = -RADIUS; dy <= RADIUS; dy++) {
					p.setY(at.getY() + dy);
					if (level.isOutsideBuildHeight(p)) {
						continue;
					}
					BlockState state = level.getBlockState(p);
					if (!state.is(ConventionalBlockTags.ORES)) {
						continue;
					}
					counts.merge(kind(state), 1, Integer::sum);
				}
			}
		}
		if (counts.isEmpty()) {
			return Component.translatable("message.jaymospowers.prospector_pick.nothing");
		}
		List<Map.Entry<String, Integer>> found = counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();
		Component first = amount(found.get(0));
		return found.size() == 1 ? first
			: Component.translatable("message.jaymospowers.prospector_pick.and", first, amount(found.get(1)));
	}

	private static Component amount(Map.Entry<String, Integer> kind) {
		return Component.translatable(kind.getValue() >= A_LOT ? "message.jaymospowers.prospector_pick.lot" : "message.jaymospowers.prospector_pick.traces",
			Component.translatable("ore_kind.jaymospowers." + kind.getKey()));
	}

	private static String kind(BlockState state) {
		for (Map.Entry<TagKey<Block>, String> kind : KINDS) {
			if (state.is(kind.getKey())) {
				return kind.getValue();
			}
		}
		return "other";
	}
}
