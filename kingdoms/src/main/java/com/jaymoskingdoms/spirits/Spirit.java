package com.jaymoskingdoms.spirits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.238) A kind of mana spirit. Andy: "5 personalities, one of those being seemingly non responsive at all ... all
 * different sizes". One instance per kind ({@link Spirits#of}); the player's own state (level, bond) is in
 * {@link SpiritState}. A spirit has its look ({@link #color}, {@link #size}), its own {@link #moves} (the player's list is
 * {@link HandMoves#SHARED} then these, filtered by level) and hooks that let it act on its own once the bond is deep enough.
 * Every hook does nothing by default, and {@link Spirits} calls them only while the spirit is out and the player can pay.
 */
public abstract class Spirit {
	private final int kind;
	private final String id;
	private final int color;
	private final float size;
	/** Game time each autonomous act of each player is ready again (key: the player's id and the act). */
	private final Map<String, Long> cooldowns = new HashMap<>();

	protected Spirit(int kind, String id, int color, float size) {
		this.kind = kind;
		this.id = id;
		this.color = color;
		this.size = size;
	}

	public final int kind() {
		return kind;
	}

	/** Its id ({@code "grimsby"}): lang keys {@code spirit.jaymoskingdoms.<id>.*}. */
	public final String id() {
		return id;
	}

	/** Its colour (ARGB), blended into the aura's blue by the client. */
	public final int color() {
		return color;
	}

	/** The scale of its arms (1.0 is a player's). */
	public final float size() {
		return size;
	}

	/** The scale of its arms as conjured now: {@link #size()} times the state's {@code scale} (the Quiet One's changes every summon). */
	public final float size(SpiritState state) {
		return size * state.scale() / 100.0F;
	}

	/** Its own moves (empty for the placeholders). */
	public List<Move> moves() {
		return List.of();
	}

	/** Every 10 ticks while it's out. {@code threat} is the nearest mob targeting the player within 16, or null. */
	public void tick(ServerPlayer player, SpiritState state, @Nullable LivingEntity threat, long now) {
	}

	/** A hit is about to land on the player ({@code ALLOW_DAMAGE}). True cancels it: a parry, a ward. */
	public boolean hurt(ServerPlayer player, SpiritState state, DamageSource source, float amount) {
		return false;
	}

	/** The player killed {@code victim} with the spirit out. */
	public void killed(ServerPlayer player, SpiritState state, LivingEntity victim) {
	}

	/** {@code archer}, with a bow or crossbow, has the player in its sights (every 10 ticks). */
	public void aimedAt(ServerPlayer player, SpiritState state, LivingEntity archer) {
	}

	/** Whether {@code player}'s autonomous {@code act} is off cooldown. */
	protected final boolean cooled(ServerPlayer player, String act, long now) {
		Long at = cooldowns.get(player.getUUID() + act);
		return at == null || now >= at;
	}

	/** Starts {@code player}'s {@code act} cooldown ({@code ticks}). */
	protected final void cool(ServerPlayer player, String act, long now, int ticks) {
		cooldowns.put(player.getUUID() + act, now + ticks);
	}

	/** Forgets a player's act cooldowns (they left). */
	final void forget(UUID id) {
		cooldowns.keySet().removeIf(k -> k.startsWith(id.toString()));
	}

	final void forgetAll() {
		cooldowns.clear();
	}
}
