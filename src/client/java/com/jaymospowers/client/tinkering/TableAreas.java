package com.jaymospowers.client.tinkering;

import java.util.List;
import net.minecraft.client.renderer.Rect2i;

/**
 * (0.15.135) A Tinkers table screen's panels that lie outside its own image (the side inventory, the info panel), so JEI
 * lays its item list and search bar around them instead of over them.
 */
public interface TableAreas {
	List<Rect2i> extraAreas();
}
