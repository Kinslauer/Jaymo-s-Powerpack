package com.jaymospowers.power.reactor;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reactor Heart (Andy's Awakening picks: "Reactor", with the "Kinetic charger" wrapped into it; "future-themed, Chrono's
 * future"). A heart like the reactors of the ruined future beats in the holder's chest. Everything that moves them fills its
 * <b>charge</b> (0-{@link #FULL}): running ({@link #MOVE_GAIN} a block), landing from a height ({@link #LAND_GAIN} a block
 * fallen), and above all being hurt ({@link #HIT_GAIN} a point of harm). Full, it can't hold any more and <b>detonates</b>
 * round them ({@link #OVERLOAD_RADIUS} blocks, {@link #OVERLOAD_HARM}), and they take none of it; their own blasts never
 * hurt them.
 * <ul>
 * <li>Z, the <b>dial</b>: how much of the charge each move pours in (a quarter, a half, all of it); a move spends that much
 * (never under its own least) and grows with it ({@link #strength}).</li>
 * <li>R, <b>Cone Burst</b>: a punched wave of air and heat in a cone ahead. (The starter.)</li>
 * <li>G, <b>Blast Dash</b> along the look; Sneak + G, <b>Recoil</b>: blasts what's in front and throws you backwards.</li>
 * <li>V, <b>Shell Beam</b>: a shell of light down the look, 48 blocks, bursting where it hits; Sneak + V, <b>Charged
 * Throw</b>: the held item, charged, thrown to burst where it lands (one of the stack is spent).</li>
 * <li>B (hold), the <b>Drill</b>: the stone, dirt, sand or gravel you look at comes out as its items, one block at a time;
 * never an ore, never anything made; Sneak + B, <b>Ground Crack</b>: a slam that splits the ground ahead.</li>
 * <li>C (ascended, held), the <b>Nuke</b> ({@link ReactorNuke}).</li>
 * </ul>
 * Moves: {@link ReactorMoves}.
 */
public final class ReactorPower implements Power {
	public static final ReactorPower INSTANCE = new ReactorPower();
	public static final Identifier ID = JaymosPowers.id("reactor");
	public static final int COLOR = 0x9CFF3A;
	/** The heart's glow, and its core's hot white. */
	static final int GLOW = 0x9CFF3A, CORE = 0xEFFFD0;

	// ---- tuning ----
	/** The charge's top, and what fills it: a block run, a block fallen on landing (from {@link #LAND_FROM} up), a point of harm. */
	static final float FULL = 100.0F, MOVE_GAIN = 0.15F, LAND_GAIN = 1.5F, HIT_GAIN = 3.0F;
	static final double LAND_FROM = 3.0;
	/** The overload: how far, how hard. */
	static final double OVERLOAD_RADIUS = 5.0;
	static final float OVERLOAD_HARM = 12.0F;
	/** The dial's settings (shares of the charge a move spends). */
	static final float[] DIAL = {0.25F, 0.5F, 1.0F};
	/** A move's strength from what it spent: {@code STRENGTH_BASE + spent / STRENGTH_PER}. */
	static final float STRENGTH_BASE = 0.5F, STRENGTH_PER = 40.0F;

	static final class Live {
		float charge;
		int dial = 1;
		double fallen;
		Vec3 last;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private ReactorPower() {
	}

	public static void init() {
		Powers.register(INSTANCE);
		ReactorMoves.init();
		ReactorNuke.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && Powers.has(player, INSTANCE)) {
				add(player, taken * HIT_GAIN);
			}
		});
		// their own heart's blasts never hurt them
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && source.getEntity() == player && Powers.has(player, INSTANCE)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIVE.clear());
	}

	static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	private static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		ReactorMoves.forget(player);
		ReactorNuke.forget(player);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.BEACON);
	}

	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** R (Cone Burst) first, then G (Blast Dash), V (Shell Beam), B (Drill). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
	}

	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return !"special".equals(slot);
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
	}

	@Override
	public void onDowned(ServerPlayer player) {
		ReactorMoves.forget(player);
		ReactorNuke.forget(player);
	}

	// ---- the charge ----

	/** Charge in: full, it overloads. */
	static void add(ServerPlayer player, float amount) {
		Live live = live(player);
		live.charge = Math.min(FULL, live.charge + amount);
		if (live.charge >= FULL) {
			overload(player, live);
		}
	}

	/** Takes what the dial pours in (at least {@code least}); null-safe: 0 if there isn't the least. */
	static float spend(ServerPlayer player, float least) {
		Live live = live(player);
		if (player.getAbilities().instabuild) {
			return Math.max(least, FULL * DIAL[live.dial]);
		}
		float want = Math.max(least, live.charge * DIAL[live.dial]);
		if (live.charge + 1.0E-3F < least) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.low", Math.round(least)).withStyle(ChatFormatting.GRAY));
			return 0.0F;
		}
		want = Math.min(want, live.charge);
		live.charge -= want;
		return want;
	}

	/** How much harder a move is for what it spent. */
	static float strength(float spent) {
		return STRENGTH_BASE + spent / STRENGTH_PER;
	}

	private static void overload(ServerPlayer player, Live live) {
		live.charge = 0.0F;
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		blast(player, at, AbilityPower.size(player, OVERLOAD_RADIUS), OVERLOAD_HARM, 1.2);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)AbilityPower.size(player, OVERLOAD_RADIUS), GLOW);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.overload").withStyle(ChatFormatting.GREEN));
	}

	/**
	 * A blast of the heart's at {@code at}: everything within {@code radius} but its maker takes up to {@code harm} (less
	 * further out) and is thrown {@code push} away. No block is broken.
	 */
	static void blast(ServerPlayer player, Vec3 at, double radius, float harm, double push) {
		ServerLevel level = player.level();
		DamageSource source = player.damageSources().explosion(player, player);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e != player && PickKit.foe(player, e))) {
			double d = e.getBoundingBox().getCenter().distanceTo(at);
			if (d > radius) {
				continue;
			}
			float near = (float)(1.0 - d / (radius + 1.0));
			e.hurtServer(level, source, harm * near);
			Vec3 away = e.position().subtract(at);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(away.scale(push * near)).add(0.0, 0.25 * near, 0.0));
			e.syncVelocity = true;
		}
		MagicFx.blast(level, at, (float)radius * 0.6F, GLOW);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1 + (int)(radius / 3), radius * 0.2, radius * 0.2, radius * 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (float)Math.min(4.0, 0.6 + radius * 0.2), 1.2F);
		com.jaymospowers.core.Shakes.around(level, at, radius * 3.0, (float)Math.min(2.0, radius * 0.25));
	}

	// ---- keys ----

	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (!down || com.jaymospowers.core.Mastery.starterOnly(player)) {
			return;
		}
		Live live = live(player);
		live.dial = (live.dial + 1) % DIAL.length;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.2F + 0.3F * live.dial);
		hud(player, live, true);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		boolean sneak = player.isShiftKeyDown();
		if (key == PowerKey.ABILITY_4) {
			if (down && sneak) {
				ReactorMoves.crack(player);
			} else {
				ReactorMoves.drill(player, down && !sneak);
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> ReactorMoves.cone(player, com.jaymospowers.core.Mastery.starterOnly(player));
			case ABILITY_2 -> {
				if (sneak) {
					ReactorMoves.recoil(player);
				} else {
					ReactorMoves.dash(player);
				}
			}
			case ABILITY_3 -> {
				if (sneak) {
					ReactorMoves.toss(player);
				} else {
					ReactorMoves.beam(player);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		ReactorNuke.key(player, down);
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		Live live = live(player);
		Vec3 pos = player.position();
		if (live.last != null && player.onGround() && !player.isPassenger()) {
			double run = Math.sqrt(pos.subtract(live.last).multiply(1.0, 0.0, 1.0).lengthSqr());
			if (run > 0.05 && run < 2.0) {
				add(player, (float)(run * MOVE_GAIN));
			}
		}
		live.last = pos;
		// landing from a height
		if (player.fallDistance > live.fallen) {
			live.fallen = player.fallDistance;
		}
		if (player.onGround() || player.isInWater()) {
			if (live.fallen >= LAND_FROM && player.onGround()) {
				add(player, (float)(live.fallen * LAND_GAIN));
			}
			live.fallen = 0.0;
		}
		ReactorMoves.tick(player);
		ReactorNuke.tick(player);
		if (player.tickCount % 10 == 0 && live.charge > 0.5F) {
			hud(player, live, false);
		}
		if (live.charge >= FULL * 0.8F && player.tickCount % 6 == 0) {
			player.level().sendParticles(MagicFx.spark(GLOW, 0.7F), player.getX(), player.getY() + 1.1, player.getZ(), 2, 0.2, 0.3, 0.2, 0.05);
		}
	}

	private static void hud(ServerPlayer player, Live live, boolean dialed) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.charge", PickKit.gauge(live.charge / FULL, 10),
			Math.round(live.charge), Math.round(DIAL[live.dial] * 100)).withStyle(dialed ? ChatFormatting.WHITE : ChatFormatting.GREEN));
	}
}
