package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * (0.7.490) The hook for buildings from other packs: structure templates (a structure block's NBT) saved under
 * {@code data/jaymoskingdoms/structure/horde/<orc|goblin>/<tier>/} (tier 2, 3 or 4) are read through the game's
 * {@link StructureTemplateManager}, so they can come from this mod's resources, a data pack, or the world's own
 * {@code generated/} folder. If a tier has any, they are that tier's whole building list ({@link Hordes}' planner places
 * them like the coded designs); if it has none, the coded designs in {@link HordeBuildings} stand.
 *
 * <p>A template's front is its low-z face (north, as saved), where its door should be. A file name ending {@code _x3}
 * asks for three of it (up to {@link #MAX_COPIES}). Templates too big to fit a band, or empty, are skipped with a log line.
 */
final class HordeTemplates {
	/** The folder under {@code structure/}. */
	static final String FOLDER = "horde/";
	/** A template may be this many blocks across (it still has to find room in its band) and hold this many blocks. */
	static final int MAX_SIDE = 24, MAX_BLOCKS = 20000;
	/** The most copies of one template a {@code _xN} name asks for. */
	static final int MAX_COPIES = 6;

	/** (0.7.490) At most two digits, so a long number in a pack's file name can't overflow the parse. */
	private static final Pattern COPIES = Pattern.compile("_x(\\d{1,2})$");
	/** "orc/3" → that tier's templates, each repeated as asked (read once per server run). */
	private static final Map<String, List<Blueprint>> CACHE = new HashMap<>();

	private HordeTemplates() {
	}

	/** Forgets what was read (a server stopped; the next one may have other packs). */
	static void clear() {
		CACHE.clear();
	}

	/** The templates for {@code tier} of {@code orc}'s or goblins' camps (empty if there are none), ready as blueprints. */
	static List<Blueprint> load(MinecraftServer server, boolean orc, int tier) {
		String key = (orc ? "orc" : "goblin") + "/" + tier;
		List<Blueprint> cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		List<Blueprint> out = new ArrayList<>();
		String prefix = FOLDER + key + "/";
		StructureTemplateManager manager = server.getStructureTemplateManager();
		List<Identifier> found = manager.listTemplates()
			.filter(id -> id.getNamespace().equals(JaymosKingdoms.MOD_ID) && id.getPath().startsWith(prefix)).sorted().toList();
		for (Identifier id : found) {
			Blueprint blueprint = read(manager, id);
			if (blueprint == null) {
				continue;
			}
			Matcher m = COPIES.matcher(id.getPath());
			int copies = m.find() ? Math.clamp(Integer.parseInt(m.group(1)), 1, MAX_COPIES) : 1;
			for (int i = 0; i < copies; i++) {
				out.add(blueprint);
			}
		}
		List<Blueprint> result = List.copyOf(out);
		CACHE.put(key, result);
		return result;
	}

	private static Blueprint read(StructureTemplateManager manager, Identifier id) {
		Optional<StructureTemplate> template;
		try {
			template = manager.get(id);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Horde template {} can't be read: {}", id, e.toString());
			return null;
		}
		if (template.isEmpty()) {
			return null;
		}
		Blueprint blueprint = Blueprint.fromTemplate("horde_template_" + id.getPath().replace('/', '_'), template.get().save(new CompoundTag()));
		if (blueprint == null || blueprint.blocks().size() > MAX_BLOCKS || blueprint.width() > MAX_SIDE || blueprint.depth() > MAX_SIDE) {
			JaymosKingdoms.LOGGER.info("Horde template {} skipped ({} blocks, {} x {})", id, blueprint == null ? 0 : blueprint.blocks().size(),
				blueprint == null ? 0 : blueprint.width(), blueprint == null ? 0 : blueprint.depth());
			return null;
		}
		// (0.7.490) fromTemplate puts the lowest layer at y=0, but Hordes raises its designs on the first air block with
		// the floor at y=-1, so lower every block one to keep the template's floor in place of the ground
		List<Blueprint.Placement> lowered = new ArrayList<>(blueprint.blocks().size());
		for (Blueprint.Placement pl : blueprint.blocks()) {
			lowered.add(new Blueprint.Placement(pl.offset().offset(0, -1, 0), pl.state()));
		}
		return new Blueprint(blueprint.id(), blueprint.width(), blueprint.depth(), blueprint.height(), List.copyOf(lowered));
	}
}
