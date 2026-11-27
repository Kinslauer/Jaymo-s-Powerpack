package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A mage's wand (Andy, 2026-11-25: "They should learn spells and cast from the wand"). A wand is wood and a focus: the wood is
 * in the stack ({@link #WOOD_KEY}, set by the recipe: oak, birch, cherry or dark oak), the focus is the wand itself (amethyst,
 * quartz, emerald or diamond). There is no new way to learn: a wand casts the spells its holder already knows.
 * <ul>
 * <li>A Mage, or anyone else who casts from a staff ({@link Spellcraft#staffCaster}: an attuned player, a Cleric, a Druid, a
 * Warlock), uses it as a staff: right click casts the spell it is turned to, sneak and right click turns it to the next they
 * know ({@link Classes.Chosen#spells}); mana, words, cooldown and the rest as for a staff ({@code Spellcraft#castStaff}).</li>
 * <li>A Sorcerer, who cannot hold a staff's spells, casts the spells they have kept ({@link ElementSpells#known}) through it
 * the same way, the one it is turned to (kept in the wand, {@link #PICK_KEY}).</li>
 * <li>Quality: the wood and the focus each scale how hard the spell hits ({@link #power}) and how long the wand waits
 * ({@link #cooldown}), by the tables below. The same oak-and-amethyst wand in a better hand casts as well as the hand does.</li>
 * </ul>
 * The wand holds nothing of its own and casts no spell its holder hasn't learned, so in anyone else's hand it only serves as a
 * focus for spoken magic ({@link SpellParts}) and says so.
 */
public final class MageWand extends Item {
	/** The stack's custom data keys: the wood, and (for a Sorcerer) which of their spells it is turned to. */
	static final String WOOD_KEY = "jaymoskingdoms_wand_wood", PICK_KEY = "jaymoskingdoms_wand_pick";
	/** What a wood gives: {spell power, wait}, both as multiples (a wait under 1 is quicker). */
	static final Map<String, float[]> WOODS = Map.of(
		"oak", new float[] {1.00F, 1.00F},
		"birch", new float[] {0.95F, 0.80F},
		"cherry", new float[] {1.05F, 0.90F},
		"dark_oak", new float[] {1.15F, 1.10F});
	/** What a focus gives: {spell power, wait}, and the colour of its glint. */
	static final Map<String, float[]> FOCI = Map.of(
		"amethyst", new float[] {1.10F, 1.00F},
		"quartz", new float[] {1.00F, 0.85F},
		"emerald", new float[] {1.15F, 0.95F},
		"diamond", new float[] {1.30F, 0.90F});
	static final Map<String, Integer> COLOURS = Map.of("amethyst", 0xB48CFF, "quartz", 0xF2EBDD, "emerald", 0x3FD070, "diamond", 0x6FE8E8);
	/** The wait before the wand can be turned or a Sorcerer's spell tried again (ticks): a Mage's spells have their own cooldowns. */
	static final int TURN_WAIT = 10;
	/** The glint at the tip when it casts: its size, and how far in front of the eyes (blocks). */
	static final float GLINT = 0.35F;
	static final double TIP = 0.7;

	public static Item AMETHYST, QUARTZ, EMERALD, DIAMOND;

	private final String focus;

	MageWand(Properties properties, String focus) {
		super(properties);
		this.focus = focus;
	}

	static void register() {
		AMETHYST = Arcana.item("amethyst_wand", p -> new MageWand(p.stacksTo(1), "amethyst"));
		QUARTZ = Arcana.item("quartz_wand", p -> new MageWand(p.stacksTo(1), "quartz"));
		EMERALD = Arcana.item("emerald_wand", p -> new MageWand(p.stacksTo(1), "emerald"));
		DIAMOND = Arcana.item("diamond_wand", p -> new MageWand(p.stacksTo(1), "diamond"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			for (Item wand : List.of(AMETHYST, QUARTZ, EMERALD, DIAMOND)) {
				output.insertAfter(ManaWand.WAND, made(wand, "oak"));
			}
		});
	}

	/** A wand of {@code wood} (oak, birch, cherry or dark_oak). */
	static ItemStack made(Item wand, String wood) {
		ItemStack stack = new ItemStack(wand);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(WOOD_KEY, wood));
		return stack;
	}

	public static String wood(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		String wood = data == null ? "" : data.copyTag().getStringOr(WOOD_KEY, "");
		return WOODS.containsKey(wood) ? wood : "oak";
	}

	/** How hard a spell cast through {@code stack} hits, as a multiple (1 for anything that isn't a wand). */
	static float power(ItemStack stack) {
		return stack.getItem() instanceof MageWand wand ? WOODS.get(wood(stack))[0] * FOCI.get(wand.focus)[0] : 1.0F;
	}

	/** How long {@code stack} waits between casts, as a multiple (1 for anything that isn't a wand). */
	static float cooldown(ItemStack stack) {
		return stack.getItem() instanceof MageWand wand ? WOODS.get(wood(stack))[1] * FOCI.get(wand.focus)[1] : 1.0F;
	}

	/** The glint at a wand's tip as it casts. */
	static void glint(ServerLevel level, ServerPlayer player, ItemStack stack) {
		if (stack.getItem() instanceof MageWand wand) {
			Vec3 tip = player.getEyePosition().add(player.getViewVector(1.0F).scale(TIP)).add(0.0, -0.25, 0.0);
			Fx.blast(level, tip, GLINT * power(stack), COLOURS.get(wand.focus));
		}
	}

	/**
	 * A Sorcerer's wand: sneaking turns it to the next of the spells they have kept, else it casts the one it is turned to
	 * (scaled by the wand). Called from the use hook ({@code Spellcraft}), which has found a Sorcerer holding a wand.
	 */
	static void sorcerer(ServerPlayer player, ServerLevel level, ItemStack stack) {
		List<String> known = ElementSpells.known(player);
		if (known.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.no_spells").withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		int pick = Math.floorMod(data == null ? 0 : data.copyTag().getIntOr(PICK_KEY, 0), known.size());
		if (player.isShiftKeyDown()) {
			int next = Math.floorMod(pick + 1, known.size());
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(PICK_KEY, next));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spell.turned", Spellcraft.spellName(known.get(next))));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
		} else if (ElementSpells.castKnown(player, level, known.get(pick), power(stack), cooldown(stack))) {
			glint(level, player, stack);
		}
		player.getCooldowns().addCooldown(stack, TURN_WAIT);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// (a caster's use is taken by Spellcraft's hook before it comes here)
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.unlearned").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return InteractionResult.FAIL;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("item.jaymoskingdoms.wand.wood", Component.translatable("item.jaymoskingdoms.wand.wood." + wood(stack)))
			.withStyle(ChatFormatting.GOLD));
		lines.accept(Component.translatable("item.jaymoskingdoms.wand.quality", Math.round(power(stack) * 100.0F), Math.round(cooldown(stack) * 100.0F))
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		lines.accept(Component.translatable("item.jaymoskingdoms.wand.use").withStyle(ChatFormatting.GRAY));
	}
}
