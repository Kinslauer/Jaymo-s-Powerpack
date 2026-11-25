package com.jaymoskingdoms;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Fantasy Mounts (optional; NOT shipped in the pack until its author agrees, docs/FaeWoods-Plan.md, Andy's answer 3), reached
 * by registry id only, so Kingdoms never compiles against it. Meant to do three things when it is loaded:
 * <ul>
 * <li>its stag, treant, luna moth and jackalope spawn near Sylvan havens;</li>
 * <li>a Sylvan haven's wardens ride its stags;</li>
 * <li>its mounts' levels are unlocked by our horse bond ({@code equestrian.Bonding}).</li>
 * </ul>
 * TODO: none of that is built. Its jar could not be fetched from the cloud (CurseForge answers 403, cfwidget has no entry
 * for its slug, Modrinth has no project), so its mod id and entity ids are unknown and are not guessed here. Fill in
 * {@link #MOD} and {@link #STAG}, {@link #TREANT}, {@link #LUNA_MOTH} and {@link #JACKALOPE} from its fabric.mod.json and
 * entity registry, then add the three behaviours above; until then {@link #type} answers null and nothing happens.
 */
public final class FantasyMountsCompat {
	/** Its mod id, and its entities' paths: unknown until its jar is read. */
	static final @Nullable String MOD = null;
	static final @Nullable String STAG = null, TREANT = null, LUNA_MOTH = null, JACKALOPE = null;

	static final boolean LOADED = MOD != null && FabricLoader.getInstance().isModLoaded(MOD);

	private FantasyMountsCompat() {
	}

	/** Its entity type by path, or null when it isn't loaded, the path isn't known yet, or it has no such entity. */
	static @Nullable EntityType<?> type(@Nullable String path) {
		if (!LOADED || path == null) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(MOD, path)).orElse(null);
	}
}
