package com.jaymospowers.tinkering;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Tools and kit with no head of their own, put together at the Tool Station from Tinkering parts and a plain bit or two
 * (Andy, 2026-11-12, on the tools #364 left on their grid recipes: "yes everything but the shears and flint need tinker
 * routes"). A plough is a metal share, a tough wooden handle, a wheel and planks of its wood; a frying pan a metal plate
 * on a wooden handle; MCA's comb a metal plate and iron bars; and so on ({@link #BUILDS}). Their grid recipes are dropped
 * by what they make ({@link Uncraftable#keeps}). A part's material only matters where a build says so (wood, any metal,
 * bone, rose gold...). Builds never take exactly what a Tinkering tool takes, so none hides one.
 */
public final class ToolBuilds {
	/** What a part must be made of. */
	public enum Family {
		ANY(m -> true), WOOD(m -> m == TinkerMaterial.WOOD || m == TinkerMaterial.WOOD_CRIMSON || m == TinkerMaterial.WOOD_WARPED),
		METAL(m -> m.cast), BONE(m -> m == TinkerMaterial.BONE), COPPER(m -> m == TinkerMaterial.COPPER), ROSE_GOLD(m -> m == TinkerMaterial.ROSE_GOLD),
		STRING(m -> m == TinkerMaterial.STRING), LEATHER(m -> m == TinkerMaterial.LEATHER);

		final Predicate<TinkerMaterial> takes;

		Family(Predicate<TinkerMaterial> takes) {
			this.takes = takes;
		}
	}

	/** A part a build takes, of a family. */
	public record Part(PartType type, Family family) {
	}

	/** A plain item a build takes (one), by id or by item tag ({@code #}). */
	public record Bit(String id) {
		public boolean matches(ItemStack stack) {
			if (id.startsWith("#")) {
				return stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(id.substring(1))));
			}
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
		}

		/** What it shows as in a recipe viewer. */
		public List<ItemStack> examples() {
			List<ItemStack> out = new ArrayList<>();
			if (id.startsWith("#")) {
				var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(id.substring(1)));
				BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> out.add(new ItemStack(h.value())));
			} else {
				BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(i -> out.add(new ItemStack(i)));
			}
			return out;
		}
	}

	/**
	 * A build: what it makes ({@code output}, or {@link #output} from the bits it was given, for the plough's wood), the
	 * parts and bits it takes. Absent if its mod isn't loaded.
	 */
	public record Build(String output, List<Part> parts, List<Bit> bits, @Nullable Function<List<ItemStack>, @Nullable Item> pick) {
		public @Nullable Item item() {
			return BuiltInRegistries.ITEM.getOptional(Identifier.parse(output)).orElse(null);
		}

		public boolean loaded() {
			return item() != null && bits.stream().allMatch(b -> b.id.startsWith("#") || !b.examples().isEmpty());
		}

		/** What it makes from these bits. */
		public @Nullable Item output(List<ItemStack> given) {
			return pick == null ? item() : pick.apply(given);
		}
	}

	private static Part part(PartType type, Family family) {
		return new Part(type, family);
	}

	private static Build build(String output, List<Part> parts, String... bits) {
		return new Build(output, parts, java.util.Arrays.stream(bits).map(Bit::new).toList(), null);
	}

	/** Whether a part of {@code material} is of {@code family}. */
	public static boolean takes(Family family, TinkerMaterial material) {
		return family.takes.test(material);
	}

	/** The plough woods (Kitchen's ploughs, one per wood): planks of the wood choose which. */
	public static final List<String> PLOUGH_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo",
		"crimson", "warped");

	private static @Nullable Item plough(List<ItemStack> given) {
		for (ItemStack stack : given) {
			if (stack.is(ItemTags.PLANKS)) {
				String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
				String wood = path.endsWith("_planks") ? path.substring(0, path.length() - "_planks".length()) : path;
				if (PLOUGH_WOODS.contains(wood)) {
					return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", wood + "_plow")).orElse(null);
				}
			}
		}
		return null;
	}

	/** Every build, most particular first (the first that fits is made). */
	public static final List<Build> BUILDS = builds();

	private static List<Build> builds() {
		List<Build> b = new ArrayList<>();
		PartType plate = PartType.LARGE_PLATE, handle = PartType.TOOL_HANDLE, tough = PartType.TOUGH_HANDLE, binding = PartType.TOOL_BINDING;
		// ---- Jaymo's Kitchen: tools, machines and cookware ----
		b.add(build("jaymoskitchen:chopper", List.of(part(PartType.BROAD_AXE_HEAD, Family.METAL), part(tough, Family.WOOD), part(plate, Family.WOOD)),
			"minecraft:lead"));
		b.add(build("jaymoskitchen:bloody_chopper", List.of(part(PartType.BROAD_BLADE, Family.METAL), part(plate, Family.WOOD)), "jaymoskitchen:shaft"));
		b.add(build("jaymoskitchen:rolling_pin", List.of(part(tough, Family.WOOD), part(handle, Family.WOOD), part(handle, Family.WOOD))));
		b.add(new Build("jaymoskitchen:oak_plow", List.of(part(plate, Family.METAL), part(tough, Family.WOOD)),
			List.of(new Bit("jaymoskitchen:wheel"), new Bit("#minecraft:planks")), ToolBuilds::plough));
		b.add(build("jaymoskitchen:cooking_pot", List.of(part(plate, Family.METAL), part(plate, Family.METAL)), "minecraft:bowl"));
		b.add(build("jaymoskitchen:iron_knife", List.of(part(PartType.SMALL_BLADE, Family.METAL), part(handle, Family.WOOD)), "minecraft:string"));
		// ---- Pantry for Blockheads' cookware (before Kitchen's pans: its pan has a copper handle) ----
		b.add(build("pantryforblockheads:frying_pan", List.of(part(plate, Family.METAL), part(handle, Family.COPPER))));
		b.add(build("pantryforblockheads:pot", List.of(part(plate, Family.METAL)), "minecraft:bucket"));
		b.add(build("pantryforblockheads:baking_sheet", List.of(part(plate, Family.METAL)), "minecraft:paper"));
		b.add(build("pantryforblockheads:mixing_bowl", List.of(part(plate, Family.WOOD), part(handle, Family.WOOD))));
		b.add(build("pantryforblockheads:artisan_press", List.of(part(plate, Family.METAL), part(tough, Family.WOOD)), "minecraft:anvil",
			"minecraft:iron_chain"));
		b.add(build("jaymoskitchen:frying_pan", List.of(part(plate, Family.METAL), part(handle, Family.WOOD))));
		b.add(build("jaymoskitchen:saucepan", List.of(part(plate, Family.METAL), part(handle, Family.WOOD), part(binding, Family.ANY))));
		b.add(build("jaymoskitchen:baking_tray", List.of(part(plate, Family.METAL))));
		// ---- Jaymo's Kingdoms ----
		b.add(build("jaymoskingdoms:hoof_pick", List.of(part(PartType.PICK_HEAD, Family.METAL), part(handle, Family.ANY))));
		b.add(build("jaymoskingdoms:oar", List.of(part(tough, Family.WOOD), part(plate, Family.WOOD), part(binding, Family.ANY))));
		// ---- Jaymo's Armory: the bone knife (the flint one stays a first tool in the grid) ----
		b.add(build("jaymospowers:bone_knife", List.of(part(PartType.SMALL_BLADE, Family.BONE), part(handle, Family.ANY)), "minecraft:string"));
		// ---- MCA's kit ----
		b.add(build("mca:comb", List.of(part(plate, Family.METAL)), "minecraft:iron_bars"));
		b.add(build("mca:needle_and_thread", List.of(part(PartType.ARROW_HEAD, Family.METAL)), "minecraft:string"));
		b.add(build("mca:whistle", List.of(part(handle, Family.METAL)), "minecraft:gold_block"));
		b.add(build("mca:villager_tracker", List.of(part(plate, Family.ROSE_GOLD)), "minecraft:compass", "minecraft:ender_eye"));
		// ---- Naturalist ----
		b.add(build("naturalist:capture_net", List.of(part(tough, Family.WOOD), part(binding, Family.STRING), part(binding, Family.STRING))));
		b.add(build("naturalist:whistle", List.of(part(handle, Family.COPPER)), "minecraft:string"));
		// ---- Nature's Compass, Traveler's Tool Belt (its first belt; its own upgrades take that), WeaponMod's fire rod ----
		b.add(build("naturescompass:naturescompass", List.of(part(plate, Family.METAL)), "minecraft:compass", "#minecraft:saplings"));
		b.add(build("travelertoolbelt:belt", List.of(part(binding, Family.LEATHER), part(binding, Family.LEATHER), part(binding, Family.METAL))));
		b.add(build("weaponmod:firerod", List.of(part(handle, Family.WOOD)), "minecraft:torch"));
		return List.copyOf(b);
	}

	/** Everything a build makes (every plough), whose grid recipes are dropped. */
	public static final Set<String> MADE = made();

	private static Set<String> made() {
		Set<String> out = new java.util.HashSet<>();
		for (Build b : BUILDS) {
			out.add(b.output());
		}
		for (String wood : PLOUGH_WOODS) {
			out.add("jaymoskitchen:" + wood + "_plow");
		}
		return Set.copyOf(out);
	}

	private ToolBuilds() {
	}

	/** Whether {@code stack} is a plain bit some build takes (so the Tool Station lets it in). */
	public static boolean isBit(ItemStack stack) {
		for (Build b : BUILDS) {
			for (Bit bit : b.bits()) {
				if (bit.matches(stack)) {
					return true;
				}
			}
		}
		return false;
	}

	/** What these parts and bits make (one), and the build, or null if none fits. */
	public static @Nullable Built match(List<ItemStack> parts, List<ItemStack> bits) {
		for (Build b : BUILDS) {
			if (!b.loaded() || b.parts().size() != parts.size() || b.bits().size() != bits.size()) {
				continue;
			}
			if (!fits(b.parts(), parts) || !bitsFit(b.bits(), bits)) {
				continue;
			}
			Item out = b.output(bits);
			if (out != null) {
				return new Built(b, out);
			}
		}
		return null;
	}

	public record Built(Build build, Item item) {
	}

	/** Each part given fills one the build takes (most particular families first). */
	private static boolean fits(List<Part> wanted, List<ItemStack> given) {
		List<Part> left = new ArrayList<>(wanted);
		left.sort(java.util.Comparator.comparingInt(p -> p.family() == Family.ANY ? 2 : p.family() == Family.METAL || p.family() == Family.WOOD ? 1 : 0));
		List<ItemStack> pool = new ArrayList<>(given);
		for (Part p : left) {
			ItemStack hit = null;
			for (ItemStack s : pool) {
				TinkerMaterial m = ToolPartItem.material(s);
				if (s.getItem() instanceof ToolPartItem item && item.part == p.type() && m != null && p.family().takes.test(m)) {
					hit = s;
					break;
				}
			}
			if (hit == null) {
				return false;
			}
			pool.remove(hit);
		}
		return pool.isEmpty();
	}

	private static boolean bitsFit(List<Bit> wanted, List<ItemStack> given) {
		List<ItemStack> pool = new ArrayList<>(given);
		for (Bit bit : wanted) {
			ItemStack hit = pool.stream().filter(bit::matches).findFirst().orElse(null);
			if (hit == null) {
				return false;
			}
			pool.remove(hit);
		}
		return pool.isEmpty();
	}
}
