package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

/**
 * (am-colours, medieval picks; Armed Mobs' disguise in enemy colours, an idea only) Dress in a realm's colours, a
 * surcoat (or tabard) of its field and its arms on your shield or banner ({@link Outfits#heraldry}), and its guards
 * take you for one of their own at a distance: they let you be. Come within {@link #CLOSE} of one (further,
 * {@link #CLOSE_WARY}, while the realm is at war: {@link WarAlert#atWar}) and he looks closer; stay there
 * {@link #LOOK_TICKS} and he sees who you are. Speak near them, or go up to one of its people, and you're known at
 * once; and so does a blow at any of its people (or its sworn), struck by you or what you shot. Seen through, their guards
 * know you for {@link #SEEN_TICKS}; change out of the colours and back, it's no help.
 */
public final class Colours {
	/** A guard looks closer this near (blocks), and this near while his realm is at war; he takes this long to see who it is (ticks). */
	static final double CLOSE = 5.0, CLOSE_WARY = 10.0;
	static final int LOOK_TICKS = 60, LOOK_TICKS_WARY = 30;
	/** Speaking this near any of its guards gives you away. */
	static final double HEARD = 16.0;
	/** Seen through, they know you this long (ticks). */
	static final long SEEN_TICKS = 20L * 60 * 10;
	/** Checked this often (ticks). */
	static final int EVERY = 10;
	static final int LINES = 2;
	/** Realms whose seats are this near a player are checked for their colours on them. */
	static final int WEAR_RANGE = 256;

	/** player|realm → seen through until. */
	private static final Map<String, Long> SEEN = new HashMap<>();
	/** player → realm → how long a guard has been looking closer. */
	private static final Map<UUID, Map<Integer, Integer>> LOOKED = new HashMap<>();
	/** player → the realms whose colours they're in (as of the last check). */
	private static final Map<UUID, java.util.Set<Integer>> WEARING = new HashMap<>();

	private Colours() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Colours::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEEN.clear();
			LOOKED.clear();
			WEARING.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer p && victim != p && WEARING.containsKey(p.getUUID())) {
				struck(p, victim);
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof Worker w && w.factionId() >= 0) {
				Faction f = Factions.get(sp.level().getServer(), w.factionId());
				if (f != null && wears(sp, f)) {
					seen(sp.level(), sp, f, w.mob());
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** {@code p} wears {@code f}'s colours: a surcoat of its field, and its arms (on that surcoat, a shield or a banner in hand). */
	static boolean wears(ServerPlayer p, Faction f) {
		if (!f.type().isRealm() || f.member(p.getUUID()) != null) {
			return false;
		}
		Outfits.Heraldry arms = Outfits.heraldry(f, p.level().getServer());
		BannerPatternLayers layers = Outfits.layers(p.level().registryAccess(), arms);
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		DyedItemColor dyed = chest.get(DataComponents.DYED_COLOR);
		boolean armsOn = bears(chest, arms.field(), layers);
		boolean surcoat = armsOn || dyed != null && (dyed.rgb() & 0xFFFFFF) == Outfits.rgb(arms.field());
		return surcoat && (armsOn || bears(p.getMainHandItem(), arms.field(), layers) || bears(p.getOffhandItem(), arms.field(), layers));
	}

	/** {@code stack} carries these arms: a shield or a heraldic piece in the field colour, or a banner of it. */
	private static boolean bears(ItemStack stack, DyeColor field, BannerPatternLayers layers) {
		if (stack.isEmpty()) {
			return false;
		}
		DyeColor base = stack.getItem() instanceof BannerItem b ? b.getColor() : stack.get(DataComponents.BASE_COLOR);
		return base == field && stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().equals(layers.layers());
	}

	private static boolean known(ServerPlayer p, int realm) {
		return SEEN.getOrDefault(p.getUUID() + "|" + realm, 0L) > p.level().getGameTime();
	}

	/** The realm whose guard {@code guard} is, if he's one. */
	private static @Nullable Faction realm(LivingEntity guard) {
		if (!(guard instanceof Worker w) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER || w.factionId() < 0
			|| guard.level().getServer() == null) {
			return null;
		}
		return Factions.get(guard.level().getServer(), w.factionId());
	}

	/** (ThrallTargetMixin, McaGuardFoesMixin) {@code attacker}, a realm's guard, takes {@code target} in its colours for one of his own. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		if (!(target instanceof ServerPlayer p) || !(attacker instanceof Mob)) {
			return false;
		}
		java.util.Set<Integer> in = WEARING.get(p.getUUID());
		if (in == null || in.isEmpty() || !(attacker instanceof Worker w) || !in.contains(w.factionId())) {
			return false;
		}
		// (at a distance; or close, while he's still looking: the tick sees him through, after)
		return realm(attacker) != null && !known(p, w.factionId());
	}

	private static int looking(ServerPlayer p, Faction f) {
		return WarAlert.atWar(p.level().getServer(), f) ? LOOK_TICKS_WARY : LOOK_TICKS;
	}

	/** (ProximityChat) {@code p} spoke: any realm whose colours they're in, with a guard near enough to hear, knows them. */
	static void spoke(ServerPlayer p) {
		ServerLevel level = p.level();
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(HEARD), e -> e.isAlive() && realm(e) != null)) {
			Faction f = realm(m);
			if (f != null && !known(p, f.id()) && wears(p, f)) {
				seen(level, p, f, m);
			}
		}
	}

	/** {@code p} in a realm's colours struck {@code victim}: if it's one of that realm's (its folk, or one sworn to it), they're known. */
	private static void struck(ServerPlayer p, LivingEntity victim) {
		MinecraftServer server = p.level().getServer();
		int realm = victim instanceof Worker w ? w.factionId() : -1;
		if (realm < 0 && victim instanceof ServerPlayer other) {
			Faction sworn = Factions.of(server, other.getUUID());
			realm = sworn == null ? -1 : sworn.id();
		}
		java.util.Set<Integer> in = WEARING.get(p.getUUID());
		Faction f = realm >= 0 && in != null && in.contains(realm) ? Factions.get(server, realm) : null;
		if (f == null || known(p, f.id())) {
			return;
		}
		Mob by = victim instanceof Mob m ? m : null;
		int id = f.id();
		if (by == null) {
			for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(HEARD), e -> e.isAlive() && e instanceof Worker w && w.factionId() == id)) {
				by = m;
				break;
			}
		}
		if (by != null) {
			seen(p.level(), p, f, by);
		} else {
			SEEN.put(p.getUUID() + "|" + f.id(), p.level().getGameTime() + SEEN_TICKS);
		}
	}

	private static void seen(ServerLevel level, ServerPlayer p, Faction f, Mob by) {
		SEEN.put(p.getUUID() + "|" + f.id(), level.getGameTime() + SEEN_TICKS);
		Map<Integer, Integer> m = LOOKED.get(p.getUUID());
		if (m != null) {
			m.remove(f.id());
		}
		by.getLookControl().setLookAt(p, 30.0F, 30.0F);
		Speech.tell(level, by, p, Component.translatable("speech.jaymoskingdoms.colours.seen." + level.getRandom().nextInt(LINES), f.name()), Speech.RED);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isCreative()) {
				LOOKED.remove(p.getUUID());
				WEARING.remove(p.getUUID());
				continue;
			}
			ServerLevel level = p.level();
			// whose colours they're in: any realm with a surcoat of its field
			java.util.Set<Integer> wearing = new java.util.HashSet<>();
			DyedItemColor dyed = p.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR);
			DyeColor base = p.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.BASE_COLOR);
			if (dyed != null || base != null) {
				for (Faction f : Factions.all(server)) {
					if (!f.fallen() && level == Factions.level(server) && f.seat().distSqr(p.blockPosition()) <= (double)WEAR_RANGE * WEAR_RANGE && wears(p, f)) {
						wearing.add(f.id());
					}
				}
			}
			if (wearing.isEmpty()) {
				WEARING.remove(p.getUUID());
			} else {
				WEARING.put(p.getUUID(), wearing);
			}
			Map<Integer, Integer> looked = LOOKED.get(p.getUUID());
			Map<Integer, Integer> now = new HashMap<>();
			for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(CLOSE_WARY), e -> e.isAlive() && !Downed.isDowned(e) && realm(e) != null)) {
				Faction f = realm(m);
				if (f == null || now.containsKey(f.id()) || known(p, f.id()) || !wears(p, f)) {
					continue;
				}
				double close = WarAlert.atWar(server, f) ? CLOSE_WARY : CLOSE;
				if (m.distanceToSqr(p) > close * close || !m.hasLineOfSight(p)) {
					continue;
				}
				int was = looked == null ? 0 : looked.getOrDefault(f.id(), 0);
				if (was == 0) {
					// he looks closer
					m.getLookControl().setLookAt(p, 30.0F, 30.0F);
					Speech.tell(level, m, p, Component.translatable("speech.jaymoskingdoms.colours.look." + level.getRandom().nextInt(LINES)), Speech.YELLOW);
				}
				int at = was + EVERY;
				if (at >= looking(p, f)) {
					seen(level, p, f, m);
					continue;
				}
				now.put(f.id(), at);
			}
			if (now.isEmpty()) {
				LOOKED.remove(p.getUUID());
			} else {
				LOOKED.put(p.getUUID(), now);
			}
		}
	}
}
