package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.core.BlockChoice;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.CarryOnCompat;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The sneak-click box ({@link BlockChoice}): sneak + right-click, empty hands, on a block Carry On could lift opens this
 * small box right of the crosshair instead of lifting it. "Use (bake)" (the word for what the block does, else "Use
 * (open)" for anything with an inventory, else plain "Use"), "Open door" on a Cooking for Blockheads oven, and "Lift".
 * Escape closes it and nothing happens. The words are in lang ({@code screen.jaymospowers.block_choice.*}).
 */
public final class BlockChoiceScreen extends Screen {
	static final int W = 110, ROW = 18, PAD = 6, TITLE = 14;
	/** How far right of the crosshair the box sits. */
	static final int OFFSET = 14;
	private static final Identifier PHASE = JaymosPowers.id("block_choice");
	private static final TagKey<Block> CFB_COUNTERS = cfb("counters"), CFB_COOKING_TABLES = cfb("cooking_tables");
	/** What "Use" does, by block id (tags are checked in {@link #verb}). Andy's words. */
	private static final Map<String, String> VERBS = Map.ofEntries(
		Map.entry("minecraft:furnace", "bake"), Map.entry("minecraft:smoker", "smoke"), Map.entry("minecraft:blast_furnace", "smelt"),
		Map.entry("cookingforblockheads:cutting_board", "chop"), Map.entry("jaymoskitchen:stove", "cook"),
		Map.entry("jaymoskitchen:cutting_board", "chop"), Map.entry("jaymoskitchen:cooking_pot", "stew"),
		Map.entry("jaymoskitchen:frying_pan", "fry"), Map.entry("jaymoskitchen:spit", "roast"), Map.entry("jaymoskitchen:great_spit", "roast"),
		Map.entry("jaymoskitchen:hearth", "warm"), Map.entry("jaymoskitchen:brewing_keg", "brew"), Map.entry("jaymoskitchen:cask", "age"),
		Map.entry("jaymoskitchen:still", "distill"), Map.entry("jaymoskitchen:tea_kettle", "brew_tea"), Map.entry("jaymoskitchen:sushi_mat", "roll"));

	private final BlockHitResult hit;
	private final boolean sneaking;
	private final BlockChoice.Option[] options;
	private final @Nullable String verb;

	private BlockChoiceScreen(Component title, BlockHitResult hit, boolean sneaking, BlockChoice.Option[] options, @Nullable String verb) {
		super(title);
		this.hit = hit;
		this.sneaking = sneaking;
		this.options = options;
		this.verb = verb;
	}

	static void init() {
		// before every other use hook, so the click goes nowhere else while the box is up
		UseBlockCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(PHASE, BlockChoiceScreen::use);
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide() || player.isSpectator() || !CarryOnCompat.keyPressed(player) || !player.getMainHandItem().isEmpty()
			|| !player.getOffhandItem().isEmpty() || CarryOnCompat.isCarrying(player) || Carry.isCarrying(player) || Carry.isCarried(player)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!CarryOnCompat.couldLift(player, level, pos, state)) {
			return InteractionResult.PASS;
		}
		// the main hand comes first; FAIL stops the click there (nothing is sent), so the off hand never gets it
		if (hand == InteractionHand.MAIN_HAND) {
			Minecraft.getInstance().gui.setScreen(new BlockChoiceScreen(state.getBlock().getName(), hit, player.isShiftKeyDown(),
				BlockChoice.options(state), verb(state, level.getBlockEntity(pos))));
		}
		return InteractionResult.FAIL;
	}

	/** The word in "Use (...)" for this block, or null for plain "Use". */
	static @Nullable String verb(BlockState state, @Nullable BlockEntity entity) {
		if (state.is(BlockTags.CAMPFIRES)) {
			return "cook";
		}
		if (state.is(BlockChoice.OVENS)) {
			return "bake";
		}
		if (state.is(CFB_COUNTERS)) {
			return "prep";
		}
		if (state.is(CFB_COOKING_TABLES)) {
			return "cook";
		}
		String word = VERBS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (word != null) {
			return word;
		}
		return entity instanceof Container || entity instanceof MenuProvider ? "open" : null;
	}

	private static TagKey<Block> cfb(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cookingforblockheads", path));
	}

	private int boxHeight() {
		return PAD * 2 + TITLE + options.length * ROW;
	}

	private int left() {
		return Math.min(this.width / 2 + OFFSET, this.width - W - 2);
	}

	private int top() {
		return Math.max(2, this.height / 2 - boxHeight() / 2);
	}

	@Override
	protected void init() {
		for (int i = 0; i < options.length; i++) {
			BlockChoice.Option option = options[i];
			addRenderableWidget(FlatButton.builder(label(option), b -> pick(option))
				.bounds(left() + PAD, top() + PAD + TITLE + i * ROW, W - 2 * PAD, ROW - 2).build());
		}
	}

	private Component label(BlockChoice.Option option) {
		return switch (option) {
			case USE -> verb == null ? Component.translatable("screen.jaymospowers.block_choice.use")
				: Component.translatable("screen.jaymospowers.block_choice.use_as", Component.translatable("screen.jaymospowers.block_choice.verb." + verb));
			case DOOR -> Component.translatable("screen.jaymospowers.block_choice.door");
			case LIFT -> Component.translatable("screen.jaymospowers.block_choice.lift");
		};
	}

	private void pick(BlockChoice.Option option) {
		ClientPlayNetworking.send(new BlockChoice.Choose(hit, sneaking, option));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		UiStyle.panelFramed(graphics, x, y, W, boxHeight(), UiStyle.ACCENT);
		graphics.centeredText(this.font, this.title, x + W / 2, y + PAD, UiStyle.ACCENT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
