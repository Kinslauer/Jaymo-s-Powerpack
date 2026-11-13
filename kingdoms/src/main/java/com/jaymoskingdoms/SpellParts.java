package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Words, hands and things (fifth edition's verbal, somatic and material components, Andy's pick H2). Casting a spell wants
 * what the spell wants:
 * <ul>
 * <li><b>Words</b>: a spoken spell fails for a caster who is gagged ({@link Gags}), stands in a bubble of silence
 * ({@link Fields}), or cannot hear themselves ({@link Deafened}: that one only goes wrong now and then, as before).</li>
 * <li><b>A free hand</b>: held fast, netted, in chains or down, or both hands full of something that is not a focus, and
 * the gesture cannot be made. A staff, tome or holy symbol in a hand is a focus ({@link #FOCI}): that hand can still
 * gesture. The scroll being read counts as one.</li>
 * <li><b>A thing used up</b>: the great spells spend diamond dust, a pearl or a bone ({@link #PARTS}); the smaller ones
 * want a trifle, which a focus in hand stands in for. Reading a scroll spends none (the magic is in the scroll), and a
 * creative player never does.</li>
 * </ul>
 * Counterspell is the one spell with gestures only; it is cast through a gag and in silence.
 */
public final class SpellParts {
	/** Items that stand in for a cheap component: staffs, wands and holy symbols (tomes are checked in code). */
	public static final TagKey<Item> FOCI = TagKey.create(Registries.ITEM, JaymosKingdoms.id("spell_foci"));

	/** What a spell wants: spoken, a free hand, and (if {@code bit} is set) {@code count} of it, {@code costly} ones spent even with a focus. */
	record Part(boolean spoken, boolean handed, @Nullable Supplier<Item> bit, int count, boolean costly) {
	}

	private static final Part PLAIN = new Part(true, true, null, 0, false);

	/** Spells with something other than words and a hand. Anything not listed is spoken and handed, and wants nothing. */
	static final Map<String, Part> PARTS = new HashMap<>();

	/** The dust of a ground diamond (made at the crafting table from a diamond and flint). */
	public static Item DIAMOND_DUST;

	static {
		// the great spells, paid for in full
		PARTS.put("restoration", new Part(true, true, () -> DIAMOND_DUST, 1, true));
		PARTS.put("sanctuary", new Part(true, true, () -> DIAMOND_DUST, 1, true));
		PARTS.put("raising", new Part(true, true, () -> Items.BONE, 2, true));
		PARTS.put("deaths_door", new Part(true, true, () -> Items.BONE, 3, true));
		PARTS.put("hut", new Part(true, true, () -> Items.ENDER_PEARL, 1, true));
		PARTS.put("identify", new Part(true, true, () -> Items.ENDER_PEARL, 1, true));
		PARTS.put("dispel", new Part(true, true, () -> DIAMOND_DUST, 1, true));
		// the small ones, a trifle a focus stands in for
		PARTS.put("web", new Part(true, true, () -> Items.STRING, 1, false));
		PARTS.put("grease", new Part(true, true, () -> Items.SLIME_BALL, 1, false));
		PARTS.put("darkness", new Part(true, true, () -> Arcana.BAT_WING, 1, false));
		PARTS.put("spike_growth", new Part(true, true, () -> Items.STICK, 2, false));
		PARTS.put("ward_line", new Part(true, true, () -> Items.STRING, 1, false));
		PARTS.put("water_walk", new Part(true, true, () -> Items.KELP, 1, false));
		// gestures only
		PARTS.put("counterspell", new Part(false, true, null, 0, false));
	}

	private SpellParts() {
	}

	static void register() {
		net.minecraft.resources.ResourceKey<Item> key = net.minecraft.resources.ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("diamond_dust"));
		DIAMOND_DUST = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, key,
			new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)
			.register(output -> output.insertAfter(Items.DIAMOND, DIAMOND_DUST));
	}

	static Part of(String spell) {
		return PARTS.getOrDefault(spell, PLAIN);
	}

	/** Whether {@code stack} is a focus: a staff, a wand, a tome, a holy symbol. */
	public static boolean focus(ItemStack stack) {
		return !stack.isEmpty() && (Arcana.isStaff(stack) || stack.is(Arcana.BONE_STAFF) || stack.is(Arcana.TOTEM_STAFF) || Tomes.isTome(stack)
			|| stack.getItem() instanceof ManaWand || stack.is(FOCI));
	}

	/** Whether {@code who} can speak a spell just now (a gag or a bubble of silence stops them). */
	public static boolean canSpeak(LivingEntity who) {
		return !Gags.gagged(who) && !Fields.silent(who);
	}

	/**
	 * Whether {@code who} has a hand to gesture with: not held fast, netted, in chains, down or tied, and a hand empty or
	 * holding a focus (or {@code casting}, the scroll being read).
	 */
	public static boolean handFree(LivingEntity who, @Nullable ItemStack casting) {
		if (HeldFast.locked(who) || Nets.netted(who) || Captives.bound(who) || Downed.isDowned(who)) {
			return false;
		}
		ItemStack main = who.getMainHandItem(), off = who.getOffhandItem();
		return main.isEmpty() || off.isEmpty() || focus(main) || focus(off) || casting != null && (casting == main || casting == off);
	}

	/**
	 * Whether {@code player} may begin casting {@code spell}: words, a hand, and something to spend (unless it is a scroll
	 * read, which spends nothing but the scroll). Tells them what is wrong, with a sound; false when something is.
	 */
	public static boolean allow(ServerPlayer player, String spell, @Nullable ItemStack scroll) {
		Part part = of(spell);
		if (part.spoken() && !canSpeak(player)) {
			refuse(player, Gags.gagged(player) ? "message.jaymoskingdoms.parts.gagged" : "message.jaymoskingdoms.parts.silenced");
			return false;
		}
		if (part.handed() && !handFree(player, scroll)) {
			refuse(player, "message.jaymoskingdoms.parts.hands");
			return false;
		}
		if (scroll == null && part.bit() != null && !player.isCreative() && !covered(player, part) && have(player, part.bit().get()) < part.count()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.parts.needs", part.count(), new ItemStack(part.bit().get()).getHoverName())
				.withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 0.7F);
			return false;
		}
		return true;
	}

	/** The thing a cast used up is gone. Call after the spell took. */
	public static void spend(ServerPlayer player, String spell) {
		Part part = of(spell);
		if (part.bit() == null || player.isCreative() || covered(player, part)) {
			return;
		}
		Item bit = part.bit().get();
		int left = part.count();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(bit)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
	}

	/** Whether a focus in hand stands in for this part (only for the cheap ones). */
	private static boolean covered(Player player, Part part) {
		return !part.costly() && (focus(player.getMainHandItem()) || focus(player.getOffhandItem()));
	}

	private static int have(Player player, Item bit) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(bit)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void refuse(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** The id of a Spell Engine spell is "spoken and handed" like the rest: whether a Spell Engine caster is gagged, silenced or bound. */
	public static boolean engineBlocked(LivingEntity caster, Identifier spell) {
		return !canSpeak(caster) || !handFree(caster, null);
	}
}
