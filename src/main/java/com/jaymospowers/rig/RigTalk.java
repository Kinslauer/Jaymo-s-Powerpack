package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Talking to the Mind in chat (Andy's Rig picks, ideas only). A line the wearer says that starts with one of the Mind's commands
 * (or with "mind", then anything) is caught before anyone hears it, and the Mind answers in chat to the wearer alone, in its own
 * personality ({@link RigMind#tell}): "light up" (the helm's lamps: night sight for a minute), "dig me out" (it clears the blocks
 * pressing on you), "report" (the charge), "scan" (level 3), "where am I", "how do I make &lt;thing&gt;" (what goes into it, from the
 * crafting recipes), and "fly me to the village / home" or "stop" ({@link RigPilot}). It never explains the world, only what it
 * can do. A line that isn't for it is said aloud as ever.
 */
public final class RigTalk {
	// ---- tuning ----
	/** Charge for the lamps and for digging out; how long the lamps stay lit (ticks). */
	static final int LIGHT_COST = 20, DIG_COST = 40, LIGHT_TICKS = 1200;
	/** Digging out: the hardest block it moves (stone is 1.5, obsidian 50). */
	static final float DIG_HARDNESS = 3.0F;

	private RigTalk() {
	}

	static void init() {
		// heard before Jaymo's Kingdoms' proximity chat says the line aloud, so a line for the Mind goes no further
		Identifier early = JaymosPowers.id("rig_talk");
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(early, (message, sender, params) -> !heard(sender, message.signedContent()));
	}

	/** {@code player} said {@code text}: true if it was for the Mind (and it answered). */
	static boolean heard(ServerPlayer player, String text) {
		ItemStack core = Rig.core(player);
		if (RigMind.personality(core) == null) {
			return false;
		}
		String line = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
		boolean addressed = line.startsWith("mind ") || line.equals("mind");
		if (addressed) {
			line = line.substring(4).strip();
		}
		if (line.startsWith("light up") || line.equals("light") || line.startsWith("lights")) {
			light(player, core);
		} else if (line.startsWith("dig me out") || line.startsWith("dig out") || line.startsWith("get me out")) {
			digOut(player, core);
		} else if (line.equals("report") || line.equals("status") || line.startsWith("charge")) {
			RigMind.report(player);
		} else if (line.equals("scan")) {
			if (RigActives.needs(player, Rig.SCAN_LEVEL)) {
				RigScan.scan(player, RigActives.stateOf(player));
			}
		} else if (line.startsWith("where am i")) {
			BlockPos at = player.blockPosition();
			RigMind.tell(player, core, "talk.where", at.getX(), at.getY(), at.getZ());
		} else if (line.startsWith("how do i make ") || line.startsWith("how do i craft ")) {
			recipe(player, core, line.substring(line.startsWith("how do i make ") ? "how do i make ".length() : "how do i craft ".length()).strip());
		} else if (line.startsWith("fly me to ") || line.startsWith("take me to ")) {
			RigPilot.order(player, core, line.substring(line.indexOf(" to ") + 4).strip());
		} else if (line.equals("stop") || line.startsWith("stop flying") || line.startsWith("i have control")) {
			if (!RigPilot.stop(player, true) && !addressed) {
				return false;
			}
		} else if (addressed) {
			RigMind.tell(player, core, "talk.unknown");
		} else {
			return false;
		}
		return true;
	}

	/** The helm's lamps: night sight, for charge. */
	private static void light(ServerPlayer player, ItemStack core) {
		if (!Rig.wears(player, Rig.Piece.HELM) || !Rig.spend(player, LIGHT_COST)) {
			RigMind.tell(player, core, "talk.no_charge");
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LIGHT_TICKS, 0, false, false), player);
		RigMind.tell(player, core, "talk.light");
	}

	/** Clears the blocks pressing on the wearer (where their body and head are, and above), soft ones only, for charge. */
	private static void digOut(ServerPlayer player, ItemStack core) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		java.util.List<BlockPos> stuck = new java.util.ArrayList<>();
		for (BlockPos p : new BlockPos[] {feet, feet.above(), feet.above(2)}) {
			BlockState state = level.getBlockState(p);
			float hardness = state.getDestroySpeed(level, p);
			if (!state.isAir() && !state.getCollisionShape(level, p).isEmpty() && hardness >= 0.0F && hardness <= DIG_HARDNESS
				&& player.mayInteract(level, p) && !player.blockActionRestricted(level, p, player.gameMode())) {
				stuck.add(p);
			}
		}
		if (stuck.isEmpty()) {
			RigMind.tell(player, core, "talk.dig_none");
			return;
		}
		if (!Rig.spend(player, DIG_COST)) {
			RigMind.tell(player, core, "talk.no_charge");
			return;
		}
		for (BlockPos p : stuck) {
			level.destroyBlock(p, true, player);
		}
		player.push(0.0, 0.2, 0.0);
		RigMind.tell(player, core, "talk.dig");
	}

	/** What goes into the first crafting recipe for a thing whose name has {@code words} in it. */
	private static void recipe(ServerPlayer player, ItemStack core, String words) {
		if (words.isEmpty()) {
			RigMind.tell(player, core, "talk.unknown");
			return;
		}
		for (RecipeHolder<?> holder : player.level().getServer().getRecipeManager().getRecipes()) {
			ItemStack result;
			if (holder.value() instanceof ShapedRecipe shaped) {
				result = shaped.assemble(CraftingInput.EMPTY);
			} else if (holder.value() instanceof ShapelessRecipe shapeless) {
				result = shapeless.assemble(CraftingInput.EMPTY);
			} else {
				continue;
			}
			if (result.isEmpty() || !result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(words)) {
				continue;
			}
			Map<String, Integer> needs = new LinkedHashMap<>();
			for (Ingredient ingredient : holder.value().placementInfo().ingredients()) {
				Item first = ingredient.items().findFirst().map(Holder::value).orElse(null);
				if (first != null) {
					needs.merge(first.getDefaultInstance().getHoverName().getString(), 1, Integer::sum);
				}
			}
			StringBuilder list = new StringBuilder();
			for (Map.Entry<String, Integer> e : needs.entrySet()) {
				list.append(list.isEmpty() ? "" : ", ").append(e.getValue()).append(" ").append(e.getKey());
			}
			RigMind.tell(player, core, "talk.recipe", result.getHoverName(), list.toString());
			return;
		}
		RigMind.tell(player, core, "talk.recipe_none", Component.literal(words));
	}
}
