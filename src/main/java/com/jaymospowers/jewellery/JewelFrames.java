package com.jaymospowers.jewellery;

import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smithing.Forging;
import java.util.ArrayList;
import java.util.List;

/**
 * (0.19.87) The jewellery frames on the Smithing Anvil, like the prosthetics: hammered from gold, silver or copper
 * ingots. The option ids ("jewel/ring_frame/gold") are saved on forging blanks: never rename them.
 */
public final class JewelFrames {
	public static final List<Molten> METALS = List.of(Molten.GOLD, Molten.SILVER, Molten.COPPER);

	private JewelFrames() {
	}

	static void register() {
		Forging.PROVIDERS.add(JewelFrames::options);
	}

	private static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		for (JewelKind kind : JewelKind.values()) {
			for (Molten metal : METALS) {
				out.add(new Forging.Option("jewel/" + kind.frameId + "/" + metal.id, JewelFrameItem.of(kind, metal), metal, kind.ingots,
					Forging.hitsFor(kind.ingots)));
			}
		}
		return out;
	}
}
