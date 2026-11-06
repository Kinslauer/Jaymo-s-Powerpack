package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.wild.FeralWolf;
import com.jaymoskingdoms.wild.Wild;
import com.jaymoskingdoms.wild.WolfStalkers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.625, Rivals patch 5) What a Bane ({@link Banes}) takes from its owner while it lives.
 * <ul>
 * <li><b>The cut:</b> a share ({@link #CUT_BASE}, more for each place it holds) of their job wages, bounty rewards and
 * merchant sales goes to it ({@link #cut}), and now and then a coin goes from their purse ({@link #PINCH_MILLI}). All of it
 * is counted; ended or sworn, they get it back with {@link #INTEREST} more, in the coin of wherever they stand.</li>
 * <li><b>Its claims:</b> every {@link #CLAIM_DAYS} days it takes one more town or village near its owner (at most
 * {@link #CLAIMS_MAX}): that place and the roads out of it ({@link #CLAIM_NEAR} of its seat). Its men come at its owner
 * there; {@link #CLAIM_MEN} of them killed there, by anyone, frees it. Each change goes on the town boards.</li>
 * <li><b>Its hounds:</b> {@link #HOUNDS} branded feral wolves (one more from rank three). When its owner has bled lately and
 * is alone, out of any town, they come up the blood trail and stalk them (the wolf stalkers' hunt). Each one killed puts its
 * next claim off {@link #HOUND_DELAY}; all killed, it has new ones after {@link #RESTOCK_DAYS} days.</li>
 * </ul>
 */
public final class BaneCut {
	// ================================================================================================================
	// Tuning
	/** Its share of what you're paid, and how much more for each place it holds, at most this. */
	static final float CUT_BASE = 0.15F, CUT_PER_CLAIM = 0.05F, CUT_MAX = 0.4F;
	/** Ended or sworn, what it took comes back with this much more. */
	static final float INTEREST = 0.5F;
	/** Each look, the chance it takes from your purse; how much (thousandths of an emerald), times one more for each place it holds. */
	static final float PINCH_CHANCE = 1.0F / 240.0F;
	static final long PINCH_MILLI = 1000L;
	/** A new claim every this many days (its first this long after it's born), at most this many; this far (blocks) from you at most. */
	static final int CLAIM_DAYS = 3, CLAIMS_MAX = 5;
	static final double CLAIM_RANGE = 640.0;
	/** A place it holds: this near its seat (blocks; its roads too); this many of its men killed there frees it. */
	static final double CLAIM_NEAR = 128.0;
	static final int CLAIM_MEN = 3;
	/** In a place it holds, each look, the chance its men come (at most this many at once), and after, not again there for this long (ticks). */
	static final float CLAIM_MEN_CHANCE = 0.2F;
	static final int CLAIM_MEN_AT_ONCE = 2;
	static final long CLAIM_MEN_GAP = 3600L;
	/** Its hounds: how many, how near a stain of yours must be (blocks) and how fresh (ticks); no other player this near and you're alone. */
	static final int HOUNDS = 2;
	static final double SCENT_NEAR = 48.0, ALONE = 48.0;
	static final long SCENT_FRESH = 6000L;
	/** Each look while they may come, the chance they do; they turn up this far off. */
	static final float HOUND_CHANCE = 0.25F;
	static final double HOUND_MIN = 24.0, HOUND_MAX = 32.0;
	/** A hunt lasts at most this long (ticks), and ends if you get this far from them; then not again for this long. */
	static final long HUNT_TICKS = 3600L, HOUND_GAP = 12000L;
	static final double HUNT_FAR = 96.0;
	/** First hunt this long after its birth (ticks); each hound killed puts its next claim off this long; all dead, new ones after this many days. */
	static final long HOUND_FIRST = 12000L, HOUND_DELAY = 24000L;
	static final int RESTOCK_DAYS = 3;
	static final int LOOK = 100;
	// ================================================================================================================

	static final long DAY = 24000L;
	/** On a hound: this and its Bane's ladder. */
	static final String HOUND_TAG = "jaymoskingdoms.bane_hound=";

	/** A place it holds: the faction's id, how many of its men are still to kill there, when its men may next come there. */
	record Claim(int town, int left, long men) {
		static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("town").forGetter(Claim::town),
				Codec.INT.fieldOf("left").forGetter(Claim::left),
				Codec.LONG.optionalFieldOf("men", 0L).forGetter(Claim::men))
			.apply(i, Claim::new));
	}

	/**
	 * What one player's Bane ({@code bane}, its captain id) has from them: what it has taken (thousandths of an emerald), the
	 * places it holds, when it claims next, its hounds left and when they may next hunt.
	 */
	record Take(String bane, long owed, List<Claim> claims, long nextClaim, int hounds, long houndsAt) {
		static final Codec<Take> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("bane").forGetter(Take::bane),
				Codec.LONG.fieldOf("owed").forGetter(Take::owed),
				Claim.CODEC.listOf().fieldOf("claims").forGetter(Take::claims),
				Codec.LONG.fieldOf("next_claim").forGetter(Take::nextClaim),
				Codec.INT.fieldOf("hounds").forGetter(Take::hounds),
				Codec.LONG.fieldOf("hounds_at").forGetter(Take::houndsAt))
			.apply(i, Take::new));

		Take owed(long n) {
			return new Take(bane, n, claims, nextClaim, hounds, houndsAt);
		}

		Take claims(List<Claim> c, long next) {
			return new Take(bane, owed, List.copyOf(c), next, hounds, houndsAt);
		}

		Take hounds(int n, long at) {
			return new Take(bane, owed, claims, nextClaim, n, at);
		}
	}

	/** On the Overworld: each player's (id) take. Saved. */
	static final AttachmentType<Map<String, Take>> TAKES = AttachmentRegistry.create(JaymosKingdoms.id("bane_takes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Take.CODEC)));

	/** A hunt going: its hounds and when it began. In memory: a reload ends it. */
	private record Hunt(List<UUID> hounds, long at) {
	}

	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();
	/** Hounds being let loose this moment (before their hunt is known: they load first). */
	private static final java.util.Set<UUID> LOOSING = new java.util.HashSet<>();
	private static @Nullable MinecraftServer SERVER;

	private BaneCut() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SERVER = server;
			if (server.getTickCount() % LOOK == 17) {
				look(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(BaneCut::died);
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			String ladder = houndOf(e);
			if (ladder != null && !hunting(e.getUUID())) {
				// (left from before a reload)
				e.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HUNTS.clear();
			SERVER = null;
		});
	}

	private static Map<String, Take> takes(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(TAKES, Map.of());
	}

	private static @Nullable Take take(MinecraftServer server, UUID player) {
		return takes(server).get(player.toString());
	}

	private static void put(MinecraftServer server, UUID player, @Nullable Take t) {
		Map<String, Take> all = new HashMap<>(takes(server));
		if (t == null) {
			all.remove(player.toString());
		} else {
			all.put(player.toString(), t);
		}
		Factions.level(server).setAttached(TAKES, Map.copyOf(all));
	}

	/** {@code p}'s take by their Bane {@code c}, begun now if it's new (or was another's). */
	private static Take of(ServerPlayer p, Captain c) {
		MinecraftServer server = p.level().getServer();
		Take t = take(server, p.getUUID());
		if (t != null && t.bane().equals(c.id())) {
			return t;
		}
		long now = Factions.level(server).getGameTime();
		Take fresh = new Take(c.id(), t == null ? 0L : t.owed(), List.of(), now + CLAIM_DAYS * DAY, HOUNDS + (c.rank() >= 3 ? 1 : 0), now + HOUND_FIRST);
		put(server, p.getUUID(), fresh);
		return fresh;
	}

	// ---- the cut --------------------------------------------------------------------------------------------------------

	/** Its share of {@code p}'s takings just now. */
	static float share(Take t) {
		return Math.min(CUT_MAX, CUT_BASE + CUT_PER_CLAIM * t.claims().size());
	}

	/**
	 * (From the wages, bounties and merchant sales) What {@code p} is paid, less its cut while they have a Bane: counted
	 * against it, and a line saying so. Without one, {@code pay} as it is.
	 */
	public static Currencies.Pay cut(ServerPlayer p, Currencies.Pay pay) {
		Captain c = pay.isEmpty() ? null : Banes.of(p);
		if (c == null) {
			return pay;
		}
		Take t = of(p, c);
		long milli = pay.milli();
		long want = (long)(milli * share(t));
		if (want <= 0L) {
			return pay;
		}
		Currencies.Pay left = Currencies.pay(pay.coin(), milli - want);
		long taken = milli - left.milli();
		if (taken <= 0L) {
			return pay;
		}
		put(p.level().getServer(), p.getUUID(), t.owed(t.owed() + taken));
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bane.cut", Component.literal(c.name()), Currencies.amount(Currencies.pay(pay.coin(), taken)))
			.withStyle(ChatFormatting.DARK_RED));
		return left;
	}

	/** Its owner's purse, now and then. */
	private static void pinch(ServerPlayer p, Captain c, Take t) {
		if (p.getRandom().nextFloat() >= PINCH_CHANCE) {
			return;
		}
		long taken = Money.chargeUpTo(p, PINCH_MILLI * (1 + t.claims().size()));
		if (taken > 0L) {
			put(p.level().getServer(), p.getUUID(), t.owed(t.owed() + taken));
			Post.aside(p.getUUID(), Component.translatable("message.jaymoskingdoms.bane.pinch").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** Its owner's Bane is ended or sworn (or another's now): all it took, and more, back to them; its claims let go. */
	private static void settle(ServerPlayer p, Take t) {
		MinecraftServer server = p.level().getServer();
		put(server, p.getUUID(), null);
		end(p.getUUID());
		for (Claim claim : t.claims()) {
			Faction f = Factions.get(server, claim.town());
			if (f != null) {
				Post.news(server, Component.translatable("news.jaymoskingdoms.bane.let_go", f.name()));
			}
		}
		if (t.owed() <= 0L) {
			return;
		}
		Currency money = Currencies.at(p.level(), p.blockPosition());
		if (!money.money()) {
			money = Currency.SILVER;
		}
		Currencies.Pay back = Currencies.pay(money, t.owed() + (long)(t.owed() * INTEREST));
		Currencies.give(p, back);
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bane.cut_back", Currencies.amount(back)).withStyle(ChatFormatting.GOLD));
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	// ---- each look ------------------------------------------------------------------------------------------------------

	private static void look(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Take t = take(server, p.getUUID());
			Captain c = Banes.of(p);
			if (c == null || p.getAttached(Banes.BANE) == null) {
				if (t != null) {
					settle(p, t);
				}
				continue;
			}
			if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
				continue;
			}
			t = of(p, c);
			pinch(p, c, t);
			t = take(server, p.getUUID());
			t = claims(p, c, t);
			hounds(p, c, t);
		}
	}

	// ---- its claims -----------------------------------------------------------------------------------------------------

	/** A place that may be claimed: standing, a realm or a village (not a camp). */
	private static boolean claimable(Faction f) {
		return !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	private static Take claims(ServerPlayer p, Captain c, Take t) {
		MinecraftServer server = p.level().getServer();
		ServerLevel world = Factions.level(server);
		long now = world.getGameTime();
		List<Claim> claims = new ArrayList<>(t.claims());
		// (a place that fell or is gone: let go quietly)
		boolean changed = claims.removeIf(cl -> {
			Faction f = Factions.get(server, cl.town());
			return f == null || !claimable(f);
		});
		long next = t.nextClaim();
		if (now >= next && p.level() == world) {
			if (claims.size() < CLAIMS_MAX) {
				Faction pick = pick(server, p, claims);
				if (pick != null) {
					claims.add(new Claim(pick.id(), CLAIM_MEN, now + CLAIM_MEN_GAP));
					Post.news(server, Component.translatable("news.jaymoskingdoms.bane.claims", Component.literal(c.name()), pick.name(), p.getDisplayName()));
				}
			}
			next = now + CLAIM_DAYS * DAY;
			changed = true;
		}
		if (p.level() == world) {
			// (in a place it holds: its men, now and then)
			for (int i = 0; i < claims.size(); i++) {
				Claim cl = claims.get(i);
				Faction f = Factions.get(server, cl.town());
				if (f == null || now < cl.men() || p.position().distanceTo(Vec3.atCenterOf(f.seat())) > CLAIM_NEAR || Banes.visiting(c.tribe())
					|| p.getRandom().nextFloat() >= CLAIM_MEN_CHANCE) {
					continue;
				}
				if (Banes.claimMen(world, p, c, Math.min(CLAIM_MEN_AT_ONCE, cl.left()))) {
					claims.set(i, new Claim(cl.town(), cl.left(), now + CLAIM_MEN_GAP));
					changed = true;
				}
				break;
			}
		}
		if (!changed) {
			return t;
		}
		Take after = t.claims(claims, next);
		put(server, p.getUUID(), after);
		return after;
	}

	/** The nearest place to {@code p} it may claim, held by no Bane yet. */
	private static @Nullable Faction pick(MinecraftServer server, ServerPlayer p, List<Claim> mine) {
		java.util.Set<Integer> held = new java.util.HashSet<>();
		for (Take any : takes(server).values()) {
			for (Claim cl : any.claims()) {
				held.add(cl.town());
			}
		}
		for (Claim cl : mine) {
			held.add(cl.town());
		}
		Faction best = null;
		double bestD = CLAIM_RANGE;
		for (Faction f : Factions.all(server)) {
			double d = p.position().distanceTo(Vec3.atCenterOf(f.seat()));
			if (claimable(f) && !held.contains(f.id()) && d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	// ---- deaths ---------------------------------------------------------------------------------------------------------

	private static void died(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		String hound = houndOf(dead);
		String ladder = hound != null ? hound : Banes.followerOf(dead);
		UUID ownerId = ladder == null ? null : Banes.owner(ladder);
		Take t = ownerId == null ? null : take(server, ownerId);
		if (t == null) {
			return;
		}
		Captain c = Captains.get(server, t.bane());
		Component name = Component.literal(c == null ? "?" : c.name());
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		long now = Factions.level(server).getGameTime();
		if (hound != null) {
			// (one less on your trail: its next claim waits)
			int left = Math.max(0, t.hounds() - 1);
			Take after = new Take(t.bane(), t.owed(), t.claims(), t.nextClaim() + HOUND_DELAY, left, left == 0 ? now + RESTOCK_DAYS * DAY : t.houndsAt());
			put(server, ownerId, after);
			if (owner != null) {
				owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.hound_killed", name).withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		if (level != Factions.level(server)) {
			return;
		}
		List<Claim> claims = new ArrayList<>(t.claims());
		for (int i = 0; i < claims.size(); i++) {
			Claim cl = claims.get(i);
			Faction f = Factions.get(server, cl.town());
			if (f == null || dead.position().distanceTo(Vec3.atCenterOf(f.seat())) > CLAIM_NEAR) {
				continue;
			}
			if (cl.left() <= 1) {
				claims.remove(i);
				Post.news(server, Component.translatable("news.jaymoskingdoms.bane.freed", f.name(), name));
				if (owner != null) {
					owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.freed", f.name()).withStyle(ChatFormatting.GOLD));
				}
			} else {
				claims.set(i, new Claim(cl.town(), cl.left() - 1, cl.men()));
			}
			put(server, ownerId, t.claims(claims, t.nextClaim()));
			return;
		}
	}

	// ---- its hounds -----------------------------------------------------------------------------------------------------

	static @Nullable String houndOf(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(HOUND_TAG)) {
				return tag.substring(HOUND_TAG.length());
			}
		}
		return null;
	}

	private static boolean hunting(UUID hound) {
		if (LOOSING.contains(hound)) {
			return true;
		}
		for (Hunt h : HUNTS.values()) {
			if (h.hounds().contains(hound)) {
				return true;
			}
		}
		return false;
	}

	private static void hounds(ServerPlayer p, Captain c, Take t) {
		MinecraftServer server = p.level().getServer();
		ServerLevel level = (ServerLevel)p.level();
		long now = Factions.level(server).getGameTime();
		Hunt hunt = HUNTS.get(p.getUUID());
		if (hunt != null) {
			List<LivingEntity> alive = new ArrayList<>();
			for (UUID id : hunt.hounds()) {
				if (level.getEntity(id) instanceof LivingEntity h && h.isAlive()) {
					alive.add(h);
				}
			}
			double nearest = Double.MAX_VALUE;
			for (LivingEntity h : alive) {
				nearest = Math.min(nearest, h.distanceTo(p));
			}
			if (alive.isEmpty() || nearest > HUNT_FAR || now - hunt.at() > HUNT_TICKS) {
				end(p.getUUID());
				put(server, p.getUUID(), t.hounds(t.hounds(), Math.max(t.houndsAt(), now + HOUND_GAP)));
				return;
			}
			for (LivingEntity h : alive) {
				if (h instanceof FeralWolf wolf && wolf.getTarget() != p && !WolfStalkers.stalking(wolf, p)) {
					WolfStalkers.hunt(wolf, p);
				}
			}
			return;
		}
		if (t.hounds() <= 0) {
			if (now >= t.houndsAt()) {
				put(server, p.getUUID(), t.hounds(HOUNDS + (c.rank() >= 3 ? 1 : 0), now));
			}
			return;
		}
		if (now < t.houndsAt() || Banes.visiting(c.tribe()) || !alone(p) || p.getRandom().nextFloat() >= HOUND_CHANCE) {
			return;
		}
		Vec3 scent = scent(level, p, now);
		if (scent != null) {
			loose(level, p, c, t, scent);
		}
	}

	/** No other player near, and out of any town's land. */
	private static boolean alone(ServerPlayer p) {
		for (ServerPlayer o : ((ServerLevel)p.level()).players()) {
			if (o != p && !o.isSpectator() && o.distanceTo(p) < ALONE) {
				return false;
			}
		}
		return Factions.at(p.level().getServer(), p.level(), p.blockPosition()) == null && !Downed.isDowned(p);
	}

	/**
	 * Where its hounds pick up {@code p}'s trail: a fresh stain of their blood near them (Powers' blood trail), or without
	 * blood trails (no Powers) their own feet while they're badly hurt; null if there's nothing to follow.
	 */
	private static @Nullable Vec3 scent(ServerLevel level, ServerPlayer p, long now) {
		Object[] found = BloodHunters.follow(level, p.position(), SCENT_NEAR, p.getUUID(), level.getGameTime() - SCENT_FRESH);
		if (found != null && found.length > 0 && found[0] instanceof Vec3 at) {
			return at;
		}
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymospowers") && p.getHealth() < p.getMaxHealth() * 0.5F) {
			return p.position();
		}
		return null;
	}

	/** Its hounds come up the trail from {@code scent}'s side, and take up the stalk. */
	private static void loose(ServerLevel level, ServerPlayer p, Captain c, Take t, Vec3 scent) {
		RandomSource random = level.getRandom();
		Vec3 back = scent.subtract(p.position()).multiply(1.0, 0.0, 1.0);
		double base = back.lengthSqr() < 4.0 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(back.z, back.x);
		BlockPos spot = null;
		for (int i = 0; i < 12 && spot == null; i++) {
			double angle = base + (random.nextDouble() - 0.5) * 1.2;
			double r = HOUND_MIN + random.nextDouble() * (HOUND_MAX - HOUND_MIN);
			BlockPos near = BlockPos.containing(p.getX() + Math.cos(angle) * r, p.getY(), p.getZ() + Math.sin(angle) * r);
			if (!level.isPositionEntityTicking(near)) {
				continue;
			}
			BlockPos at = Construction.standable(level, near, 3);
			if (level.getBlockState(at.below()).isSolid() && Math.abs(at.getY() - p.getY()) <= 8) {
				spot = at;
			}
		}
		if (spot == null) {
			return;
		}
		List<UUID> pack = new ArrayList<>();
		for (int i = 0; i < Math.min(t.hounds(), HOUNDS + 1); i++) {
			FeralWolf wolf = Wild.FERAL_WOLF.create(level, EntitySpawnReason.EVENT);
			if (wolf == null) {
				continue;
			}
			BlockPos at = Construction.standable(level, spot.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1), 2);
			wolf.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			wolf.addTag(HOUND_TAG + c.tribe());
			wolf.addTag(Rivals.ROLLED_TAG);
			// (its owner's brand: only they see the wisp off it)
			wolf.setAttached(Banes.BRAND, p.getStringUUID());
			LOOSING.add(wolf.getUUID());
			if (level.addFreshEntity(wolf)) {
				pack.add(wolf.getUUID());
			}
		}
		if (!pack.isEmpty()) {
			HUNTS.put(p.getUUID(), new Hunt(List.copyOf(pack), Factions.level(level.getServer()).getGameTime()));
		}
		LOOSING.clear();
		if (pack.isEmpty()) {
			return;
		}
		for (UUID id : pack) {
			if (level.getEntity(id) instanceof FeralWolf wolf && !WolfStalkers.hunt(wolf, p)) {
				wolf.setTarget(p);
			}
		}
		// (far off, one long call: it knows where you are)
		level.playSound(null, spot, SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().ambientSound().value(), SoundSource.HOSTILE,
			3.0F, 0.6F);
		level.sendParticles(ParticleTypes.SMOKE, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 12, 0.6, 0.4, 0.6, 0.01);
	}

	/** {@code player}'s hunt is over: whichever of its hounds are left slip away. */
	private static void end(UUID player) {
		Hunt h = HUNTS.remove(player);
		if (h == null) {
			return;
		}
		MinecraftServer server = SERVER;
		if (server == null) {
			return;
		}
		for (UUID id : h.hounds()) {
			for (ServerLevel l : server.getAllLevels()) {
				Entity e = l.getEntity(id);
				if (e != null && e.isAlive()) {
					l.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.4, 0.3, 0.4, 0.01);
					e.discard();
				}
			}
		}
	}
}
