package com.jaymoskingdoms;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The names of the dead. Andy (2026-11-12), on death messages showing the names of people you were never introduced to:
 * "yes it should say stranger, the only way you can find out after death is a new spell death sorcerers can cast called
 * Speak with dead".
 * <p>
 * A death message reads to each player as they knew the dead ({@link #forViewer}, from {@code DeathNamesMixin}): anyone
 * named (a player, an MCA villager) they hadn't been introduced to is "a stranger", the dead and their killer alike. And
 * every named death is remembered here ({@link #of}): who they were, how they died, and who killed them as they knew
 * them, for {@link Necromancy#speakWithDead}.
 */
public final class DeadNames {
	/** How many deaths are remembered (the oldest go first). */
	static final int KEPT = 512;

	/** A death remembered: the dead's true name, the death as it happened (names and all), and their killer as they knew them (empty if none). */
	record Dead(Component name, Component how, Component killer) {
	}

	private static final Map<UUID, Dead> DEAD = new LinkedHashMap<>(64, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Dead> eldest) {
			return size() > KEPT;
		}
	};

	private DeadNames() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!Introductions.named(entity)) {
				return;
			}
			Component name = entity instanceof ServerPlayer p ? Identities.trueName(p) : entity.getDisplayName();
			Entity by = source.getEntity();
			Component killer = by == null || by == entity ? Component.empty()
				: entity instanceof ServerPlayer p && Introductions.named(by) && !Introductions.knows(p, by) ? Introductions.stranger() : by.getDisplayName();
			DEAD.put(entity.getUUID(), new Dead(name, source.getLocalizedDeathMessage(entity), killer));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DEAD.clear());
	}

	/** The death of {@code id}, if it's remembered. */
	static @Nullable Dead of(UUID id) {
		return DEAD.get(id);
	}

	/**
	 * {@code message}, the death of {@code dead} (at {@code killer}'s hand, if any), as {@code viewer} reads it: the names
	 * of the named they haven't been introduced to swapped for "a stranger".
	 */
	public static Component forViewer(Component message, LivingEntity dead, @Nullable Entity killer, ServerPlayer viewer) {
		if (viewer == dead || !(message.getContents() instanceof TranslatableContents tc)) {
			return message;
		}
		boolean hideDead = Introductions.named(dead) && !Introductions.knows(viewer, dead);
		boolean hideKiller = killer != null && killer != viewer && Introductions.named(killer) && !Introductions.knows(viewer, killer);
		if (!hideDead && !hideKiller) {
			return message;
		}
		String deadName = dead.getDisplayName().getString(), killerName = killer == null ? null : killer.getDisplayName().getString();
		Object[] args = tc.getArgs().clone();
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof Component c) {
				String s = c.getString();
				if (hideDead && s.equals(deadName) || hideKiller && s.equals(killerName)) {
					args[i] = Component.translatable(i == 0 ? "chat.jaymoskingdoms.death.stranger_first" : "chat.jaymoskingdoms.death.stranger");
				}
			}
		}
		Component out = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), args).withStyle(message.getStyle());
		for (Component sibling : message.getSiblings()) {
			out = out.copy().append(sibling);
		}
		return out;
	}
}
