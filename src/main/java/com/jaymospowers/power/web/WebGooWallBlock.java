package com.jaymospowers.power.web;

import net.minecraft.world.level.block.Block;

/**
 * One cell of the symbiote's goo shell (see {@link WebWall}): a full block of black goo that stops arrows and bodies for a
 * few seconds, then draws back. {@link WebWall} puts it only in empty air and takes it out again.
 */
public class WebGooWallBlock extends Block {
	public WebGooWallBlock(final Properties properties) {
		super(properties);
	}
}
