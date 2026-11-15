package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.Item;

/**
 * Medieval picks (me-mail): mail gets a real making. Iron nuggets hammered (with any hammer) become mail rings, nine rings
 * are linked into a mail sheet, and mail sheets are laced into the chainmail helmet, hauberk, chausses and boots (5, 8,
 * 7 and 4 sheets, as many as the ingots plate takes). Rings and sheets melt back down. The recipes and melting are data
 * (tools/gen_medieval_smithing.py).
 */
public final class MailRings {
	public static Item MAIL_RING, MAIL_SHEET;

	private MailRings() {
	}

	static void register() {
		MAIL_RING = Smithing.item("mail_ring", Item::new, Function.identity());
		MAIL_SHEET = Smithing.item("mail_sheet", Item::new, Function.identity());
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(MAIL_RING);
			output.accept(MAIL_SHEET);
		});
	}
}
