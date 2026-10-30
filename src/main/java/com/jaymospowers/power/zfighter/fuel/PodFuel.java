package com.jaymospowers.power.zfighter.fuel;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.18.14, SevTech picks) Fuel grades. Every fuel the pod's tank takes has a grade: 1 is coal, charcoal and coal blocks; 2 is
 * blaze powder and rods and Pod Spirit (the Fuel Still's); 3 is the Cinder Ember, found only on Cinder. Every world needs a grade
 * ({@link #NEEDED}); a tank holds the lowest grade that went into it since it was last empty, so one scoop of coal in a grade 2
 * tank drops it to 1 ({@link #mixed}). {@code PodEntity} keeps the number ({@code fuelGrade}), the star map shows what each world
 * needs and greys the ones the tank can't reach, and {@code ZFighterSpace.go} refuses with a line on the action bar.
 * <p>
 * The Fuel Still itself is an Alchemy route: crucible recipes in {@code data/jaymospowers/alchemy/crucible/pod_spirit_*.json}
 * (whale oil is the strong input, resin and honey the weak ones), taught by the research {@link #STILL_RESEARCH}.
 */
public final class PodFuel {
	// ---- tuning ----
	/** The grade each world needs, by destination index (moon, Rustfall, Glacia, Cinder, Maw, Verdant, future). */
	public static final int[] NEEDED = {1, 2, 2, 2, 3, 2, 3};
	/** Home needs only this, so nobody is stranded by a poor tank. */
	public static final int HOME_GRADE = 1;
	/** A tank filled before grades existed (a Patrol's, a raider's, an old pod block): counts as this. */
	public static final int UNSET_GRADE = 2;
	public static final int MAX_GRADE = 3;
	/** What one of each fuel puts in the tank (fuel units, out of {@code ZFighterSpace.MAX_FUEL}). */
	public static final int COAL_BLOCK = 9, BLAZE_ROD = 3, BLAZE_POWDER = 2, COAL = 1, POD_SPIRIT_WORTH = 2, EMBER_WORTH = 4;
	/** The Cinder Ember: the chance a block broken beside lava on Cinder gives one. */
	public static final float EMBER_CHANCE = 0.08F;
	/** The research that teaches the Fuel Still. */
	public static final String STILL_RESEARCH = "fuel_still";

	public static Item POD_SPIRIT, CINDER_EMBER;

	private PodFuel() {
	}

	public static void init() {
		POD_SPIRIT = register("pod_spirit", Rarity.UNCOMMON);
		CINDER_EMBER = register("cinder_ember", Rarity.RARE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(POD_SPIRIT);
			output.accept(CINDER_EMBER);
		});
		Research.register(new Research.Entry(STILL_RESEARCH, Research.Category.CRUCIBLE, () -> POD_SPIRIT,
			EssenceList.of(Essence.FIRE, 10, Essence.SPIRIT, 6), List.of("alembic"), 0, 2, 2));
		// the ember is found beside the lava of Cinder: breaking stone that touches it sometimes turns one up
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (!level.isClientSide() && level.dimension() == ZFighterSpace.DESTS[ZFighterSpace.CINDER].level() && !player.isCreative()
				&& touchesLava(level, pos) && level.getRandom().nextFloat() < EMBER_CHANCE) {
				Block.popResource(level, pos, new ItemStack(CINDER_EMBER));
			}
		});
	}

	private static Item register(String id, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new TipItem(new Item.Properties().setId(key).rarity(rarity), "item.jaymospowers." + id + ".tooltip"));
	}

	private static boolean touchesLava(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).is(Blocks.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** For the missions' rewards and the like: gives {@code player} {@code count} Cinder Embers, into the pack or at their feet. */
	public static void reward(ServerPlayer player, int count) {
		ItemStack stack = new ItemStack(CINDER_EMBER, count);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	// ---------------------------------------------------------------- the grades

	/** The grade of one {@code stack} as fuel: 1 to 3, or 0 when it isn't fuel. */
	public static int grade(ItemStack stack) {
		if (stack.is(Items.COAL_BLOCK) || stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return 1;
		}
		if (stack.is(Items.BLAZE_ROD) || stack.is(Items.BLAZE_POWDER) || stack.is(POD_SPIRIT)) {
			return 2;
		}
		return stack.is(CINDER_EMBER) ? 3 : 0;
	}

	/** How much fuel one of {@code stack} puts in a tank (0: it isn't fuel). */
	public static int worth(ItemStack stack) {
		if (stack.is(Items.COAL_BLOCK)) {
			return COAL_BLOCK;
		}
		if (stack.is(Items.BLAZE_ROD)) {
			return BLAZE_ROD;
		}
		if (stack.is(Items.BLAZE_POWDER)) {
			return BLAZE_POWDER;
		}
		if (stack.is(POD_SPIRIT)) {
			return POD_SPIRIT_WORTH;
		}
		if (stack.is(CINDER_EMBER)) {
			return EMBER_WORTH;
		}
		return stack.is(Items.COAL) || stack.is(Items.CHARCOAL) ? COAL : 0;
	}

	/** The grade {@code pod}'s tank holds: 0 when it is empty, {@link #UNSET_GRADE} for fuel put in before grades. */
	public static int effective(PodEntity pod) {
		if (pod.fuel() <= 0) {
			return 0;
		}
		return pod.fuelGrade() <= 0 ? UNSET_GRADE : Math.min(MAX_GRADE, pod.fuelGrade());
	}

	/** The grade {@code pod}'s tank will hold once {@code fuel} is added: an empty tank takes the fuel's, any other the lower. */
	public static int mixed(PodEntity pod, ItemStack fuel) {
		int added = grade(fuel);
		int now = effective(pod);
		return now <= 0 ? added : Math.min(now, added);
	}

	/** The grade a trip to {@code index} (or Home, any negative index) needs. */
	public static int needed(int index) {
		return index >= 0 && index < NEEDED.length ? NEEDED[index] : HOME_GRADE;
	}

	/** The grade as the star map writes it: I, II, III. */
	public static String roman(int grade) {
		return switch (grade) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "-";
		};
	}

	/** A plain item with a one-line grey tooltip. */
	public static final class TipItem extends Item {
		private final String tooltip;

		public TipItem(Item.Properties properties, String tooltip) {
			super(properties);
			this.tooltip = tooltip;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
		}
	}
}
