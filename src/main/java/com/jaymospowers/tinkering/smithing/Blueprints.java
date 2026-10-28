package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Blueprints. Without one, a smith's hammered heads are never better than {@link #NO_BLUEPRINT_CAP}. A
 * Blueprint names one shape of forging (an option's id without its metal: "weapon/claymore_blade", "part/broad_blade")
 * and its grade cap rises as it is used: each finished forging of its shape that isn't Poor adds 1 xp, and the xp
 * levels it Well, Expert, Perfect, Master ({@link #EXPERT_XP}, {@link #PERFECT_XP}, {@link #MASTER_XP}). A Masterwork
 * needs a Master blueprint. The smith needs the blueprint anywhere in their inventory (hotbar and off hand included);
 * the highest-level one for the shape counts and earns the xp. A Drafting Table copies one, one level lower, onto paper.
 * Wandering traders sell a few ({@code data/jaymospowers/villager_trade/tinkering/wandering_trader}).
 */
public final class Blueprints {
	/** What a blueprint carries: the shape it is for and the xp it has earned. */
	public record Blueprint(String shape, int xp) {
		public static final Codec<Blueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("shape").forGetter(Blueprint::shape),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Blueprint::xp)).apply(i, Blueprint::new));

		public Grade level() {
			return levelOf(xp);
		}
	}

	public static DataComponentType<Blueprint> BLUEPRINT;
	public static Item BLUEPRINT_ITEM;
	public static Block DRAFTING_TABLE;

	/** The grade cap of a smith with no blueprint for the shape. */
	public static final Grade NO_BLUEPRINT_CAP = Grade.EXPERT;
	/** Finished forgings a blueprint needs to reach each level above Well. */
	public static final int EXPERT_XP = 5, PERFECT_XP = 15, MASTER_XP = 40;
	/** Shapes the creative tab offers a blueprint for (the wandering trader's few are in data). */
	public static final List<String> CREATIVE_SHAPES = List.of("weapon/claymore_blade", "weapon/broadsword_blade", "weapon/longsword_blade",
		"weapon/katana_blade", "weapon/ninjato_blade", "weapon/war_scythe_blade", "weapon/glaive_blade", "weapon/halberd_head",
		"part/broad_blade", "part/large_plate");

	private Blueprints() {
	}

	static void register() {
		BLUEPRINT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("blueprint_data"),
			DataComponentType.<Blueprint>builder().persistent(Blueprint.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Blueprint.CODEC)).build());
		BLUEPRINT_ITEM = Smithing.item("blueprint", BlueprintItem::new, p -> p.stacksTo(1));
		DRAFTING_TABLE = Kiln.blockWithTooltip("drafting_table", DraftingTableBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));

		Forging.CAPS.add((smith, anvil, option) -> {
			ItemStack blueprint = find(smith, option);
			if (blueprint == null) {
				return NO_BLUEPRINT_CAP;
			}
			Grade level = blueprint.get(BLUEPRINT).level();
			return level.atLeast(NO_BLUEPRINT_CAP) ? level : NO_BLUEPRINT_CAP;
		});
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			ItemStack blueprint = find(smith, option);
			Grade grade = Grades.of(result);
			if (blueprint == null || grade == null || grade == Grade.POOR) {
				return;
			}
			Blueprint before = blueprint.get(BLUEPRINT);
			Blueprint after = new Blueprint(before.shape(), before.xp() + 1);
			blueprint.set(BLUEPRINT, after);
			if (after.level() != before.level()) {
				smith.sendSystemMessage(Component.translatable("message.jaymospowers.smithing.blueprint_level", blueprint.getHoverName(),
					after.level().displayName()));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(DRAFTING_TABLE);
			for (String shape : CREATIVE_SHAPES) {
				output.accept(stack(shape, 0));
			}
		});
	}

	/** The level an xp total has reached. */
	public static Grade levelOf(int xp) {
		return xp >= MASTER_XP ? Grade.MASTER : xp >= PERFECT_XP ? Grade.PERFECT : xp >= EXPERT_XP ? Grade.EXPERT : Grade.WELL;
	}

	/** The xp a blueprint starts a level with. */
	public static int xpOf(Grade level) {
		return switch (level) {
			case MASTER -> MASTER_XP;
			case PERFECT -> PERFECT_XP;
			case EXPERT -> EXPERT_XP;
			default -> 0;
		};
	}

	/** The shape of a forging: its id without the metal on the end. */
	public static String shape(Forging.Option option) {
		String id = option.id();
		int slash = id.lastIndexOf('/');
		return slash < 0 ? id : id.substring(0, slash);
	}

	/** "Claymore Blade" for "weapon/claymore_blade". */
	public static Component shapeName(String shape) {
		String[] parts = shape.split("/");
		return parts.length >= 2 ? Component.translatable("item.jaymospowers." + parts[1]) : Component.literal(shape);
	}

	public static ItemStack stack(String shape, int xp) {
		ItemStack stack = new ItemStack(BLUEPRINT_ITEM);
		stack.set(BLUEPRINT, new Blueprint(shape, xp));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.blueprint.named", shapeName(shape)));
		return stack;
	}

	/** A copy a Drafting Table makes: the same shape, one level lower (Well is as low as it goes). */
	public static ItemStack copyOf(ItemStack blueprint) {
		Blueprint data = blueprint.get(BLUEPRINT);
		return data == null ? ItemStack.EMPTY : stack(data.shape(), xpOf(data.level().lower()));
	}

	/** The highest-level blueprint for this forging's shape anywhere on the smith, or null. */
	public static @Nullable ItemStack find(ServerPlayer smith, Forging.Option option) {
		String shape = shape(option);
		Inventory inventory = smith.getInventory();
		ItemStack best = null;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			Blueprint data = stack.is(BLUEPRINT_ITEM) ? stack.get(BLUEPRINT) : null;
			if (data != null && data.shape().equals(shape) && (best == null || data.xp() > best.get(BLUEPRINT).xp())) {
				best = stack;
			}
		}
		return best;
	}
}
