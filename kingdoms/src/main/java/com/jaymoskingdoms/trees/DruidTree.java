package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.DRUID;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.WildShapes;
import java.util.List;
import java.util.Set;

/**
 * The Druid's wild shapes ({@link WildShapes}): More Shapes, Long Shape, Fox and Stag, the Circle of the Moon or of the Land
 * at 10, Pack Leader, Barkskin and the Elder Shape at the top. One shape (the first beast studied) is the Druid's from the
 * start.
 */
final class DruidTree {
	private DruidTree() {
	}

	static void build() {
		Set<PlayerClass> druid = Set.of(DRUID);
		ClassTrees.add(WildShapes.SHAPES, druid, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(WildShapes.LONG_KEY, druid, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(WildShapes.FOXSTAG, druid, 1, 2, 5, 2, 3, List.of(), List.of());
		ClassTrees.add(WildShapes.MOON, druid, 1, 3, 10, 3, 1, List.of(), List.of(WildShapes.LAND));
		ClassTrees.add(WildShapes.LAND, druid, 1, 3, 10, 3, 3, List.of(), List.of(WildShapes.MOON));
		ClassTrees.add(WildShapes.PACK, druid, 1, 2, 12, 4, 2, List.of(), List.of());
		ClassTrees.add(WildShapes.BARK, druid, 1, 2, 15, 5, 2, List.of(), List.of());
		ClassTrees.add(WildShapes.ELDER, druid, 1, 3, 20, 6, 2, List.of(), List.of());
	}
}
