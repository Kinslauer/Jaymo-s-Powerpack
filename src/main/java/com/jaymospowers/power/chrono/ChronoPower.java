package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Chronokinesis. STUB: the agent building this power replaces this file. */
public final class ChronoPower implements Power {
	public static final ChronoPower INSTANCE = new ChronoPower();
	public static final Identifier ID = JaymosPowers.id("chrono");

	private ChronoPower() {
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
		return 0xF2C14E;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.CLOCK);
	}
}
