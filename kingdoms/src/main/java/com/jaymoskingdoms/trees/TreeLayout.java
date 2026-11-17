package com.jaymoskingdoms.trees;

/**
 * Every class tree's nodes ({@link com.jaymoskingdoms.ClassTrees}), one file per class: each adds its own perks and the old
 * pages that moved to it. Rows run down the class tab (roughly by the class level they need), columns 0 to 4 across.
 */
public final class TreeLayout {
	private TreeLayout() {
	}

	public static void build() {
		ThiefTree.build();
		MageTree.build();
		SorcererTree.build();
		RangerTree.build();
		WarfareTrees.build();
		MarksmanTree.build();
		TinkererTree.build();
		RiderTree.build();
		MonkTree.build();
		PaladinTree.build();
		ClericTree.build();
		DruidTree.build();
		WarlockTree.build();
	}
}
