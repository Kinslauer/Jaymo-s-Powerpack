package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.item.FlowWandItem;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;

public class ModItems {
    public static final Registrar<Item> ITEMS = Registrar.create(Registries.ITEM);

    public static final Supplier<Item> FLOW_WAND = ITEMS.register("flow_wand",
            () -> new FlowWandItem(new Item.Properties().setId(ITEMS.key("flow_wand")).stacksTo(1)));
}
