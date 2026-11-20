package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Introductions;
import com.jaymoskingdoms.ZFighterFolk;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (2026-11-20) Andy: "every humanoid that speaks should have to be introduced except for the exceptions we made before
 * like the judge". The server names someone to you as you know them ({@link Introductions#called}); this catches the
 * lines it doesn't: any chat or action-bar line, or talk card head, carrying a villager's or player's name (a name keeps
 * which entity it is in its hover) of someone you haven't been introduced to has it swapped for what they are
 * ("Villager", "Orc", a stranger), as their name tag and Jade already do. Powers' Z Fighter characters (the Judge, the
 * Keeper, the masters) keep their names, as before.
 */
public final class IntroNames {
	private IntroNames() {
	}

	static void register() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> hide(message));
	}

	/** {@code text} with the names its reader doesn't know swapped for what they are; {@code text} itself if there are none. */
	public static Component hide(Component text) {
		Component out = walk(text);
		return out == null ? text : out;
	}

	/** {@code c} rebuilt with unknown names swapped, or null if it has none. */
	private static @Nullable Component walk(Component c) {
		Component swapped = unknown(c.getStyle());
		if (swapped != null) {
			// the name's colour and the like kept, not who it points at
			return swapped.copy().withStyle(c.getStyle().withHoverEvent(null).withInsertion(null));
		}
		boolean changed = false;
		ComponentContents contents = c.getContents();
		if (contents instanceof TranslatableContents t && t.getArgs().length > 0) {
			Object[] args = t.getArgs().clone();
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Component arg) {
					Component out = walk(arg);
					if (out != null) {
						args[i] = out;
						changed = true;
					}
				}
			}
			if (changed) {
				contents = new TranslatableContents(t.getKey(), t.getFallback(), args);
			}
		}
		List<Component> siblings = new ArrayList<>(c.getSiblings());
		for (int i = 0; i < siblings.size(); i++) {
			Component out = walk(siblings.get(i));
			if (out != null) {
				siblings.set(i, out);
				changed = true;
			}
		}
		if (!changed) {
			return null;
		}
		MutableComponent out = MutableComponent.create(contents).setStyle(c.getStyle());
		siblings.forEach(out::append);
		return out;
	}

	/** What to call the one {@code style}'s hover points at, if it's a name this client's player doesn't know; else null. */
	private static @Nullable Component unknown(Style style) {
		if (!(style.getHoverEvent() instanceof HoverEvent.ShowEntity show)) {
			return null;
		}
		HoverEvent.EntityTooltipInfo info = show.entity();
		Minecraft mc = Minecraft.getInstance();
		Player me = mc.player;
		if (me == null || info.uuid.equals(me.getUUID())) {
			return null;
		}
		Entity e = mc.level == null ? null : mc.level.getEntity(info.uuid);
		if (e != null) {
			if (Introductions.knownHere(e) || e.getAttached(ZFighterFolk.PART) != null) {
				return null;
			}
			return e instanceof Player ? Introductions.stranger() : Introductions.what(e);
		}
		// not about: only players and MCA's villagers keep their names from strangers
		if (Introductions.knownHere(info.uuid)) {
			return null;
		}
		if (info.type == EntityTypes.PLAYER) {
			return Introductions.stranger();
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(info.type);
		return "mca".equals(id.getNamespace()) && id.getPath().endsWith("villager") ? info.type.getDescription() : null;
	}
}
