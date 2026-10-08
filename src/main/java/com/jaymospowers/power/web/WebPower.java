package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Web Slinger. STUB: the agent building this power replaces this file. */
public final class WebPower implements Power {
	public static final WebPower INSTANCE = new WebPower();
	public static final Identifier ID = JaymosPowers.id("web");

	private WebPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0xE0303A;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.COBWEB);
	}
}
