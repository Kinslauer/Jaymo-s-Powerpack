package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Wind Control. STUB: the agent building this power replaces this file. */
public final class WindPower implements Power {
	public static final WindPower INSTANCE = new WindPower();
	public static final Identifier ID = JaymosPowers.id("wind");

	private WindPower() {
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
		return 0xBFF4EA;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.FEATHER);
	}
}
