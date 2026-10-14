package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Proxy;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Combat Roll (optional, mod id {@code combat_roll}): Darktide gets {@link #CHARGES} roll charges, and a roll that
 * slips an attack is an evasion (+{@link #EVASION_FLOW} Flow and an ink trail).
 * <ul>
 * <li>Charges: Combat Roll's own attribute {@code combat_roll:count} (base 1; checked against Combat Roll's jar, where
 * {@code CombatRoll.Attributes.COUNT} is registered as "count" and its Multi Roll enchantment adds to
 * {@code combat_roll:count}), looked up in the attribute registry at runtime. A modifier adds the difference while
 * Darktide is your power; everyone else keeps the pack's default. Missing attribute: nothing happens.</li>
 * <li>Rolls: Combat Roll's server event {@code ServerSideRollEvents.PLAYER_START_ROLLING}, hooked by reflection with a
 * proxy (no compile dependency). Missing: no Flow from rolling.</li>
 * <li>Dashes: every Riptide Dash (and flurry) spends a roll charge, and a flurry that lands gives it back. Charges
 * live on the client (Combat Roll's {@code RollManager}): it reports its count ({@link DarktideNet.Rolls}) and the
 * server spends and refunds through {@link DarktideNet.RollCharge}. Until a count arrives, dashes cost nothing.</li>
 * <li>Evasion: within {@link #WINDOW} ticks of the roll's start, an enemy projectile passing within
 * {@link #NEAR_PROJECTILE} or a mob that has you as its target within {@link #NEAR_MOB}, while nothing hits you, counts
 * once per roll.</li>
 * </ul>
 */
final class CombatRollCompat {
	static final int CHARGES = 3;
	static final float EVASION_FLOW = 10.0F;
	static final int WINDOW = 8;
	static final double NEAR_PROJECTILE = 2.5, NEAR_MOB = 3.0;
	static final Identifier COUNT_ATTRIBUTE = Identifier.fromNamespaceAndPath("combat_roll", "count");
	private static final Identifier MODIFIER_ID = JaymosPowers.id("darktide_roll_charges");

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("combat_roll");
	private static @Nullable Holder<Attribute> count;
	private static boolean looked;

	private CombatRollCompat() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		try {
			Class<?> events = Class.forName("net.combat_roll.api.event.ServerSideRollEvents");
			Class<?> listener = Class.forName("net.combat_roll.api.event.ServerSideRollEvents$PlayerStartRolling");
			Object event = events.getField("PLAYER_START_ROLLING").get(null);
			Object proxy = Proxy.newProxyInstance(CombatRollCompat.class.getClassLoader(), new Class<?>[] {listener}, (self, method, args) -> {
				switch (method.getName()) {
					case "onPlayerStartedRolling" -> {
						if (args != null && args.length > 0 && args[0] instanceof ServerPlayer player) {
							started(player);
						}
						return null;
					}
					case "hashCode" -> {
						return System.identityHashCode(self);
					}
					case "equals" -> {
						return self == args[0];
					}
					case "toString" -> {
						return "DarktideRollListener";
					}
					default -> {
						return null;
					}
				}
			});
			Class.forName("net.combat_roll.api.event.Event").getMethod("register", Object.class).invoke(event, proxy);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Combat Roll is loaded but its roll event wasn't found; Darktide gets no Flow from rolling", e);
		}
	}

	private static @Nullable Holder<Attribute> count() {
		if (!looked) {
			looked = true;
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(COUNT_ATTRIBUTE);
			count = holder.isPresent() ? holder.get() : null;
		}
		return count;
	}

	/** Darktide's roll charges on ({@code on}) or off. */
	static void apply(ServerPlayer player, boolean on) {
		if (!LOADED) {
			return;
		}
		Holder<Attribute> attribute = count();
		AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (!on) {
			instance.removeModifier(MODIFIER_ID);
			return;
		}
		double extra = CHARGES - instance.getBaseValue();
		if (extra > 0.0 && !instance.hasModifier(MODIFIER_ID)) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, extra, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Whether {@code player} has a roll charge for a dash (always, without Combat Roll). */
	static boolean hasRoll(ServerPlayer player) {
		return DarktidePower.live(player).rolls != 0;
	}

	/** A dash spends a roll charge. */
	static void spendRoll(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.rolls > 0) {
			live.rolls--;
			send(player, -1);
		}
	}

	/** A landed flurry gives its roll charge back. */
	static void refundRoll(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.rolls >= 0) {
			live.rolls++;
			send(player, 1);
		}
	}

	private static void send(ServerPlayer player, int change) {
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, DarktideNet.RollCharge.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new DarktideNet.RollCharge(change));
		}
	}

	private static void started(ServerPlayer player) {
		if (!DarktidePower.has(player)) {
			return;
		}
		DarktideLive live = DarktidePower.live(player);
		live.rollStart = player.level().getGameTime();
		live.rollEvaded = false;
		live.rollHit = false;
	}

	static void tickEvasion(ServerPlayer player, DarktideLive live) {
		if (live.rollStart < 0) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.rollStart;
		if (t > WINDOW) {
			live.rollStart = -1L;
			return;
		}
		// a faint ink trail behind the roll once it's slipped something
		if (live.rollEvaded) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			level.sendParticles(new DustParticleOptions(0x0A1430, 1.0F), player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			return;
		}
		if (live.rollHit) {
			return;
		}
		AABB near = player.getBoundingBox().inflate(NEAR_PROJECTILE);
		boolean threat = !level.getEntitiesOfClass(Projectile.class, near,
			p -> p.getOwner() != player && p.getDeltaMovement().lengthSqr() > 0.04).isEmpty();
		if (!threat) {
			threat = !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR_MOB), m -> m.getTarget() == player && m.isAggressive())
				.isEmpty();
		}
		if (threat) {
			live.rollEvaded = true;
			DarktidePower.addFlow(player, EVASION_FLOW);
		}
	}
}
