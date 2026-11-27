package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Research;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Formulas (Hunter's Road, patch 6, "D9"): a hunter's brew (every draught and monster brew, {@link Draught#formulaId}) can't
 * be made in the crucible until its formula has been read. A formula is a paper ({@link FormulaItem}) that names one brew: use
 * it and it is read for good, and the entry shows in the Codex's Formulas tab ({@link Research.Category#FORMULAS}: a new kind of
 * entry, never paid for with insight, shown only once known). Formulas are found in the chests of old places
 * ({@link #FOUND_IN}, one in {@link #FOUND_CHANCE} chests), and bought from the wandering trader
 * ({@code villager_trade/hunter/wandering_trader}); the herbalist and the guild hall (patches 7 and 9) sell them by
 * {@link #stack}.
 */
public final class Formulas {
	/** The chance a chest of one of these places holds a formula. */
	public static final float FOUND_CHANCE = 0.12F;
	/** Chest loot tables (ids) a formula can turn up in: the old, deep and forgotten places. */
	public static final Set<String> FOUND_IN = Set.of("minecraft:chests/abandoned_mineshaft", "minecraft:chests/simple_dungeon",
		"minecraft:chests/stronghold_library", "minecraft:chests/woodland_mansion", "minecraft:chests/jungle_temple",
		"minecraft:chests/ruined_portal", "minecraft:chests/underwater_ruin_big", "minecraft:chests/ancient_city",
		"jaymospowers:chests/watchtower", "jaymospowers:chests/brexallen_ruins");

	/** On a formula stack: which brew's formula it is (a {@link Draught#id}). */
	public static DataComponentType<String> FORMULA;
	public static Item ITEM;

	private Formulas() {
	}

	static void init() {
		FORMULA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("formula"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		ITEM = Alchemy.item("formula", p -> new FormulaItem(p.stacksTo(16)));
		Draught[] all = Draught.values();
		for (int i = 0; i < all.length; i++) {
			Draught d = all[i];
			Research.register(new Research.Entry(d.formulaId(), Research.Category.FORMULAS, () -> d.item, EssenceList.EMPTY, List.of(), 0, i % 5, i / 5));
		}
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty() || !FOUND_IN.contains(key.get().identifier().toString())) {
				return;
			}
			RandomSource random = context.getRandom();
			if (random.nextFloat() < FOUND_CHANCE) {
				drops.add(stack(all[random.nextInt(all.length)]));
			}
		});
	}

	/** A formula paper for {@code draught}. */
	public static ItemStack stack(Draught draught) {
		ItemStack out = new ItemStack(ITEM);
		out.set(FORMULA, draught.id);
		return out;
	}

	/** The paper that is read: a formula teaches one brew. */
	public static class FormulaItem extends Item {
		public FormulaItem(Item.Properties properties) {
			super(properties);
		}

		private static Draught of(ItemStack stack) {
			String id = stack.get(FORMULA);
			return id == null ? null : Draught.byId(id);
		}

		@Override
		public Component getName(ItemStack stack) {
			Draught d = of(stack);
			return d == null ? super.getName(stack) : Component.translatable("item.jaymospowers.formula.of", Component.translatable(d.item.getDescriptionId()));
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			Draught d = of(stack);
			if (d == null) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.SUCCESS;
			}
			if (Knowledge.knows(server, d.formulaId()) && !server.isCreative()) {
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.formula.known"));
				return InteractionResult.FAIL;
			}
			Knowledge.learn(server, d.formulaId());
			stack.consume(1, player);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 0.9F);
			serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getEyeY(), player.getZ(), 14, 0.3, 0.3, 0.3, 0.4);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.formula").withStyle(ChatFormatting.GRAY));
			super.appendHoverText(stack, context, display, builder, flag);
		}
	}
}
