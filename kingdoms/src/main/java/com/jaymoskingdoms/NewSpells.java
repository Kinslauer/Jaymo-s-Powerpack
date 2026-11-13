package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The first spells of the magic rules: counterspell and dispel, the four rituals, the camp spells, the ones for getting
 * about and the ones that change the ground of a fight. This class holds their names, costs, scrolls and who is born
 * knowing them, and sends a cast to the class that does it. A Mage or an attuned staff caster learns them from scrolls
 * like any other spell; a Sorcerer is born knowing {@link #SORCERER_KNOWS} and reads the rest once.
 */
public final class NewSpells {
	/** Every spell here, in the order their scrolls are shown. */
	public static final List<String> IDS = List.of("counterspell", "dispel", "ward_line", "find_magic", "identify", "speak_animals", "hut",
		"create_food", "purify_food", "feather_fall", "spider_climb", "water_walk", "long_jump", "misty_step", "web", "grease", "fog", "darkness",
		"silence", "spike_growth", "charm", "command", "suggestion", "hold", "zone_of_truth", "geas", "modify_memory", "locate_object",
		"locate_creature", "augury", "scrying", "plant_growth", "control_weather", "moonbeam", "heroes_feast", "find_familiar",
		"calling_back", "keeping", "rebirth");

	/** What each costs in mana at the staff. */
	static final Map<String, Float> COST = Map.ofEntries(
		Map.entry("counterspell", 12.0F), Map.entry("dispel", 25.0F), Map.entry("ward_line", 10.0F), Map.entry("find_magic", 6.0F),
		Map.entry("identify", 8.0F), Map.entry("speak_animals", 6.0F), Map.entry("hut", 35.0F), Map.entry("create_food", 15.0F),
		Map.entry("purify_food", 8.0F), Map.entry("feather_fall", 6.0F), Map.entry("spider_climb", 14.0F), Map.entry("water_walk", 16.0F),
		Map.entry("long_jump", 8.0F), Map.entry("misty_step", 14.0F), Map.entry("web", 18.0F), Map.entry("grease", 10.0F), Map.entry("fog", 14.0F),
		Map.entry("darkness", 22.0F), Map.entry("silence", 20.0F), Map.entry("spike_growth", 22.0F),
		Map.entry("charm", 18.0F), Map.entry("command", 8.0F), Map.entry("suggestion", 14.0F), Map.entry("hold", 22.0F), Map.entry("zone_of_truth", 16.0F),
		Map.entry("geas", 30.0F), Map.entry("modify_memory", 24.0F), Map.entry("locate_object", 10.0F), Map.entry("locate_creature", 12.0F),
		Map.entry("augury", 8.0F), Map.entry("scrying", 28.0F), Map.entry("plant_growth", 30.0F), Map.entry("control_weather", 60.0F),
		Map.entry("moonbeam", 24.0F), Map.entry("heroes_feast", 50.0F), Map.entry("find_familiar", 20.0F),
		Map.entry("calling_back", 70.0F), Map.entry("keeping", 12.0F), Map.entry("rebirth", 80.0F));
	/** The wait after a staff cast of each (ticks). */
	static final Map<String, Integer> COOLDOWN = Map.ofEntries(
		Map.entry("counterspell", 20 * 3), Map.entry("dispel", 20 * 8), Map.entry("ward_line", 20 * 20), Map.entry("find_magic", 20 * 5),
		Map.entry("identify", 20 * 3), Map.entry("speak_animals", 20 * 5), Map.entry("hut", 20 * 60 * 2), Map.entry("create_food", 20 * 15),
		Map.entry("purify_food", 20 * 5), Map.entry("feather_fall", 20 * 6), Map.entry("spider_climb", 20 * 20), Map.entry("water_walk", 20 * 20),
		Map.entry("long_jump", 20 * 8), Map.entry("misty_step", 20 * 4), Map.entry("web", 20 * 12), Map.entry("grease", 20 * 8),
		Map.entry("fog", 20 * 15), Map.entry("darkness", 20 * 25), Map.entry("silence", 20 * 25), Map.entry("spike_growth", 20 * 25),
		Map.entry("charm", 20 * 20), Map.entry("command", 20 * 6), Map.entry("suggestion", 20 * 20), Map.entry("hold", 20 * 25), Map.entry("zone_of_truth", 20 * 30),
		Map.entry("geas", 20 * 60), Map.entry("modify_memory", 20 * 60), Map.entry("locate_object", 20 * 10), Map.entry("locate_creature", 20 * 10),
		Map.entry("augury", 20 * 30), Map.entry("scrying", 20 * 60), Map.entry("plant_growth", 20 * 60), Map.entry("control_weather", 20 * 60 * 5),
		Map.entry("moonbeam", 20 * 40), Map.entry("heroes_feast", 20 * 60 * 5), Map.entry("find_familiar", 20 * 60 * 2),
		Map.entry("calling_back", 20 * 60 * 10), Map.entry("keeping", 20 * 30), Map.entry("rebirth", 20 * 60 * 20));
	/** What each is for, as {@link Mana} counts it (harm is the default). */
	static final Map<String, Integer> INTENT = Map.ofEntries(
		Map.entry("counterspell", Mana.WARD), Map.entry("dispel", Mana.WILL), Map.entry("ward_line", Mana.WARD), Map.entry("find_magic", Mana.WILL),
		Map.entry("identify", Mana.WILL), Map.entry("speak_animals", Mana.WILL), Map.entry("hut", Mana.WARD), Map.entry("create_food", Mana.HEAL),
		Map.entry("purify_food", Mana.HEAL), Map.entry("feather_fall", Mana.WILL), Map.entry("spider_climb", Mana.WILL), Map.entry("water_walk", Mana.WILL),
		Map.entry("long_jump", Mana.WILL), Map.entry("misty_step", Mana.WILL), Map.entry("web", Mana.WILL), Map.entry("grease", Mana.WILL),
		Map.entry("fog", Mana.WILL), Map.entry("darkness", Mana.WILL), Map.entry("silence", Mana.WILL),
		Map.entry("charm", Mana.WILL), Map.entry("command", Mana.WILL), Map.entry("suggestion", Mana.WILL), Map.entry("hold", Mana.WILL),
		Map.entry("zone_of_truth", Mana.WARD), Map.entry("geas", Mana.WILL), Map.entry("modify_memory", Mana.WILL), Map.entry("locate_object", Mana.WILL),
		Map.entry("locate_creature", Mana.WILL), Map.entry("augury", Mana.WILL), Map.entry("scrying", Mana.WILL), Map.entry("plant_growth", Mana.HEAL),
		Map.entry("control_weather", Mana.WILL), Map.entry("moonbeam", Mana.WARD), Map.entry("heroes_feast", Mana.HEAL), Map.entry("find_familiar", Mana.WARD),
		Map.entry("calling_back", Mana.HEAL), Map.entry("keeping", Mana.WARD), Map.entry("rebirth", Mana.HEAL));
	/** The small useful ones that can be cast slowly with no mana ({@link Rituals}). */
	public static final Set<String> RITUALS = Set.of("ward_line", "dispel", "find_magic", "identify", "speak_animals", "control_weather", "find_familiar", "keeping");
	/** The rite of calling a familiar back from its ash: no spell of its own, known with the one that bound it. */
	public static final String CALL_FAMILIAR = "call_familiar";
	/** What a Sorcerer is born knowing: the quick, small ones. */
	public static final Set<String> SORCERER_KNOWS = Set.of("counterspell", "dispel", "ward_line", "find_magic", "identify", "speak_animals");
	/** How far a ground spell reaches from the caster's eyes. */
	static final double AIM_REACH = 24.0;

	private NewSpells() {
	}

	static void register() {
		for (String id : IDS) {
			Arcana.NEW_SCROLLS.put(id, Arcana.item("scroll_of_" + id, p -> new Arcana.Scroll(p, id)));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Arcana.SCROLL_HARRYING_GALE;
			for (String id : IDS) {
				Item scroll = Arcana.NEW_SCROLLS.get(id);
				output.insertAfter(after, scroll);
				after = scroll;
			}
		});
		SpellParts.register();
		Gags.register();
		Fields.register();
		Counterspell.register();
		Rituals.register();
		Cantrips.register();
		Choices.register();
		MindSpells.register();
		Truth.register();
		Geas.register();
		Divination.register();
		Hair.register();
		Scrying.register();
		NatureRites.register();
		Passers.register();
		Moonbeam.register();
		HeroesFeast.register();
		Familiars.register();
		WildMagic.register();
	}

	/** Whether {@code spell} is one of these. */
	public static boolean ours(String spell) {
		return COST.containsKey(spell);
	}

	/** Casts {@code spell} for {@code player}: true if it took. */
	static boolean cast(String spell, ServerLevel level, Player player, float power) {
		if (BattleSpells.ours(spell)) {
			return BattleSpells.cast(spell, level, player, power);
		}
		if (CampSpells.ours(spell)) {
			return CampSpells.cast(spell, level, player, power);
		}
		if (Strides.ours(spell)) {
			return Strides.cast(spell, level, player, power);
		}
		if (MindSpells.ours(spell)) {
			return MindSpells.cast(spell, level, player, power);
		}
		if (Divination.ours(spell)) {
			return Divination.cast(spell, level, player, power);
		}
		if (NatureRites.ours(spell)) {
			return NatureRites.cast(spell, level, player, power);
		}
		if (DeathRites.ours(spell)) {
			return DeathRites.cast(spell, level, player, power);
		}
		return switch (spell) {
			case "find_familiar", "call_familiar" -> Familiars.cast(spell, level, player);
			case "counterspell" -> Counterspell.react(level, player, power);
			case "dispel" -> Dispel.cast(level, player, power);
			case "find_magic", "identify", "speak_animals" -> Rituals.cast(spell, level, player, power);
			default -> false;
		};
	}

	/** Whether {@code player} knows {@code spell}: learned, born with it, or in creative. */
	public static boolean knows(Player player, String spell) {
		if (player.isCreative()) {
			return true;
		}
		if (spell.equals(CALL_FAMILIAR)) {
			return knows(player, "find_familiar");
		}
		if (Classes.is(player, Classes.PlayerClass.SORCERER) && SORCERER_KNOWS.contains(spell)) {
			return true;
		}
		return Classes.of(player).spells().contains(spell);
	}

	/** The ground where {@code player} looks, settled onto what is below it. */
	public static Vec3 aim(ServerLevel level, Player player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		if (hit.getType() != HitResult.Type.MISS && hit.getDirection().getAxis() != net.minecraft.core.Direction.Axis.Y) {
			at = at.add(hit.getDirection().getStepX() * 0.3, 0.0, hit.getDirection().getStepZ() * 0.3);
		}
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
		for (int i = 0; i < 12 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty() && pos.getY() > level.getMinY(); i++) {
			pos = pos.below();
		}
		return new Vec3(at.x, pos.getY() + (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? 0.0 : level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y)), at.z);
	}
}
