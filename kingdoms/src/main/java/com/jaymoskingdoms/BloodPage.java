package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.OldRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Blood page (Night's Blood, patch 2): what a full vampire's blood has learned, which of it is slotted, how old it is and
 * what it has given up for more room. Andy: "these options only appear for vampires" (the page is the vampire's, and the
 * dhampir's with only the nodes a dhampir can take; werewolves and wolfbloods keep their pack nodes on the skill web).
 *
 * <ul>
 * <li><b>Gifts and slots.</b> Every move of the bar, and the passive gifts (Lifeleech, Night Sense, Bat Wings), is a node a full
 * vampire learns with the page's points ({@link Gift}). A learned gift does nothing until it is slotted: {@link #FREE_SLOTS}
 * slots are free, and each one past them needs a pact, up to {@link #MAX_SLOTS}. Only slotted gifts show on the skill bar
 * ({@link com.jaymoskingdoms.forms.FormSkills}); Feed is the body's own and is always there.</li>
 * <li><b>Blood age.</b> Fledgling, grown, old, from the nights lived as a vampire, the new moons seen and the great ones fed
 * upon ({@link #score}). Each age gives page points ({@link #AGE_POINTS}) and, by night, a little strength
 * ({@link #AGE_ATTACK}). Surrender ({@link BloodFever}) stays the faster, darker road, and keeps what it gives.</li>
 * <li><b>Pacts</b> ({@link Pact}): extra weaknesses, taken and given up only at a shrine.</li>
 * <li><b>The shrine.</b> Candles, a skull and a blood bottle (in an item frame) at the head of a coffin ({@link OldRules#coffin}).
 * Sneak-use on any of them opens the page with slots and pacts; anywhere else the page only shows what you have. The turning
 * crypt ({@link Lairs}) is built with one already set.</li>
 * </ul>
 *
 * Nycto (MoriyaShiine, All Rights Reserved) gave the idea of a few gifts bought with weaknesses; nothing of it is used.
 */
public final class BloodPage {
	// ---- tuning ----
	/** Slots a vampire has without a pact, and the most there can be (each slot past the free ones needs one pact). */
	public static final int FREE_SLOTS = 3, MAX_SLOTS = 6;
	/** What counts toward blood age: a night lived is one point, a new moon seen and a great one fed upon count for more. */
	static final int NIGHT_SCORE = 1, MOON_SCORE = 8, GREAT_SCORE = 12;
	/** The score at which a fledgling is grown, and a grown one is old. */
	public static final int GROWN_AT = 30, OLD_AT = 100;
	/** Page points each age gives (fledgling's come with the turning). */
	public static final int[] AGE_POINTS = {5, 4, 5};
	/** Night strength by age: attack damage added by night (fledgling none), and an old one's extra hearts-worth of health. */
	static final double[] AGE_ATTACK = {0.0, 1.0, 2.0};
	static final double OLD_HEALTH = 4.0;
	/** The great ones remembered (so the same king fed on twice counts once), at most this many. */
	static final int GREAT_KEPT = 12;
	/** How far from a shrine's coffin head its candles, skull and bottle may be, and how far a player may be from the shrine. */
	static final int SHRINE_SPAN = 2;
	public static final double SHRINE_REACH = 6.0;
	/** The item frame's bottle, by id (Kitchen's). */
	static final Identifier BLOOD_BOTTLE = Identifier.fromNamespaceAndPath("jaymoskitchen", "blood_bottle");
	/** Drowned blood: Slowness amplifier in water, and in the rain. */
	static final int DROWNED_WATER = 1, DROWNED_RAIN = 0;
	/** Kindling: the share fire's damage is multiplied by, the torch's sting a second (at {@link #TORCH_REACH}), and how far the noon burn widens (ticks of day) and lengthens. */
	static final float KINDLING_FIRE = 1.5F, TORCH_STING = 1.0F;
	static final double TORCH_REACH = 1.5;
	public static final long KINDLING_NOON_WIDEN = 1500L;
	public static final float KINDLING_BURN_TIMES = 2.0F;
	/** Fine palate: a bottle gives this share. */
	static final float PALATE_BOTTLE = 0.5F;
	/** Unquiet: how far animals shy from you, and how much more a trader asks (a share of the price, at least one). */
	public static final double UNQUIET_RANGE = 14.0;
	static final float UNQUIET_TOLL = 0.5F;
	/** Thin blood: regeneration stops under this share of the reserve. */
	static final float THIN_BELOW = 0.5F;

	/** A gift: a node on the page. {@code move} is its index on the bar (-1 for a passive one), {@code cost} its page points. */
	public enum Gift {
		DASH(NightGifts.DASH, 0, null), POUND(NightGifts.POUND, 1, TreeLocks.Lock.NIGHT_POUND), GAZE(NightGifts.GAZE, 2, null),
		BAT(NightGifts.BAT, 2, null), COLONY(NightGifts.COLONY, 2, null), MIST(NightGifts.MIST, 2, null), STARE(NightGifts.STARE, 2, null),
		RAKE(NightGifts.RAKE, 1, null), REND(NightGifts.REND, 1, null), POUNCE(NightGifts.POUNCE, 1, null), ROAR(NightGifts.ROAR, 2, null),
		LIFELEECH(-1, 1, TreeLocks.Lock.LIFELEECH), NIGHT_SENSE(-1, 1, TreeLocks.Lock.NIGHT_SENSE), BAT_WINGS(-1, 2, TreeLocks.Lock.BAT_WINGS);

		public final int move, cost;
		public final TreeLocks.@Nullable Lock lock;

		Gift(int move, int cost, TreeLocks.@Nullable Lock lock) {
			this.move = move;
			this.cost = cost;
			this.lock = lock;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Gift of(String key) {
			for (Gift g : values()) {
				if (g.key().equals(key)) {
					return g;
				}
			}
			return null;
		}

		static @Nullable Gift ofLock(TreeLocks.Lock lock) {
			for (Gift g : values()) {
				if (g.lock == lock) {
					return g;
				}
			}
			return null;
		}

		static @Nullable Gift ofMove(int move) {
			for (Gift g : values()) {
				if (g.move == move) {
					return g;
				}
			}
			return null;
		}
	}

	/** The five pacts: extra weaknesses taken for gift slots. */
	public enum Pact {
		DROWNED, KINDLING, PALATE, UNQUIET, THIN;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Pact of(String key) {
			for (Pact p : values()) {
				if (p.key().equals(key)) {
					return p;
				}
			}
			return null;
		}
	}

	/** What a vampire's blood has learned and lived: kept, told to its owner only. */
	public record Blood(List<String> learned, List<String> slots, List<String> pacts, int nights, int moons, List<String> great, long lastNight, long lastMoon) {
		public static final Codec<Blood> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("learned", List.of()).forGetter(Blood::learned),
			Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(Blood::slots),
			Codec.STRING.listOf().optionalFieldOf("pacts", List.of()).forGetter(Blood::pacts),
			Codec.INT.optionalFieldOf("nights", 0).forGetter(Blood::nights),
			Codec.INT.optionalFieldOf("moons", 0).forGetter(Blood::moons),
			Codec.STRING.listOf().optionalFieldOf("great", List.of()).forGetter(Blood::great),
			Codec.LONG.optionalFieldOf("last_night", Long.MIN_VALUE).forGetter(Blood::lastNight),
			Codec.LONG.optionalFieldOf("last_moon", Long.MIN_VALUE).forGetter(Blood::lastMoon)).apply(i, Blood::new));

		/** A freshly turned vampire's: the Blood Dash learned and slotted. */
		static Blood start() {
			return new Blood(List.of(Gift.DASH.key()), List.of(Gift.DASH.key()), List.of(), 0, 0, List.of(), Long.MIN_VALUE, Long.MIN_VALUE);
		}

		Blood with(List<String> learned, List<String> slots, List<String> pacts) {
			return new Blood(learned, slots, pacts, this.nights, this.moons, this.great, this.lastNight, this.lastMoon);
		}

		public boolean knows(Gift g) {
			return this.learned.contains(g.key());
		}

		public boolean slotted(Gift g) {
			return this.slots.contains(g.key());
		}

		public boolean has(Pact p) {
			return this.pacts.contains(p.key());
		}

		/** The score blood age is read from. */
		public int score() {
			return this.nights * NIGHT_SCORE + this.moons * MOON_SCORE + this.great.size() * GREAT_SCORE;
		}

		/** 0 fledgling, 1 grown, 2 old. */
		public int age() {
			int s = score();
			return s >= OLD_AT ? 2 : s >= GROWN_AT ? 1 : 0;
		}

		/** The slots there are: the free ones and one for each pact, up to the most. */
		public int slotCount() {
			return Math.min(MAX_SLOTS, FREE_SLOTS + this.pacts.size());
		}

		/** Points the age has given, less the learned gifts' cost. */
		public int points() {
			int earned = 0;
			for (int a = 0; a <= age(); a++) {
				earned += AGE_POINTS[a];
			}
			int spent = 0;
			for (String k : this.learned) {
				Gift g = Gift.of(k);
				spent += g == null ? 0 : g.cost;
			}
			return earned - spent;
		}
	}

	public static final AttachmentType<Blood> STATE = AttachmentRegistry.create(JaymosKingdoms.id("blood_page"),
		b -> b.persistent(Blood.CODEC).copyOnDeath().initializer(Blood::start).syncWith(ByteBufCodecs.fromCodec(Blood.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** What a press on the page asks (server-bound). */
	public enum Op {
		LEARN, SLOT, UNSLOT, TAKE, GIVE
	}

	/** Player to server: a press on the page. {@code what} is a gift's or a pact's key. */
	public record Act(int op, String what) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("blood_page_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.op);
				buf.writeUtf(p.what, 32);
			}, buf -> new Act(buf.readVarInt(), buf.readUtf(32)));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	/** Server to player: open the page; {@code shrine} says they stand at one (the server checks every press again). */
	public record Open(boolean shrine) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("blood_page_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.shrine), buf -> new Open(buf.readBoolean()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	private static final Identifier ATTACK_ID = JaymosKingdoms.id("blood_age_attack"), HEALTH_ID = JaymosKingdoms.id("blood_age_health");

	private BloodPage() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(BloodPage::tick);
		// sneak-use on a shrine's candle, skull or the coffin, or on its framed bottle, opens the page
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)
				|| !VampireBlood.full(sp)) {
				return InteractionResult.PASS;
			}
			BlockState s = server.getBlockState(hit.getBlockPos());
			if (!(s.is(BlockTags.CANDLES) || s.is(BlockTags.SKULLS) || s.is(BlockTags.BEDS))) {
				return InteractionResult.PASS;
			}
			// a coffin sneak-used with a thrall to ride lies the vampire down to ride it (ThrallRide); the candle and skull still open the page
			if (shrineNear(server, hit.getBlockPos(), SHRINE_SPAN + 1) == null || s.is(BlockTags.BEDS) && ThrallRide.willRide(sp)) {
				return InteractionResult.PASS;
			}
			ServerPlayNetworking.send(sp, new Open(true));
			return InteractionResult.SUCCESS_SERVER;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && level instanceof ServerLevel server && VampireBlood.full(sp)) {
				if (player.isShiftKeyDown() && entity instanceof ItemFrame frame && bottle(frame) && shrineNear(server, frame.blockPosition(), SHRINE_SPAN + 1) != null) {
					ServerPlayNetworking.send(sp, new Open(true));
					return InteractionResult.SUCCESS_SERVER;
				}
				// unquiet: nothing tames or breeds for you
				if (has(sp, Pact.UNQUIET) && (entity instanceof Animal animal && animal.isFood(sp.getItemInHand(hand)) || entity instanceof TamableAnimal t && !t.isTame()
					|| entity instanceof AbstractHorse horse && !horse.isTamed())) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_page.unquiet"));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
	}

	// ---- reading ---------------------------------------------------------------------------------------------------------

	/** The page's state for a full vampire, else null. */
	public static @Nullable Blood of(Player player) {
		return VampireBlood.full(player) ? player.getAttachedOrElse(STATE, Blood.start()) : null;
	}

	/** Whether a full vampire has taken {@code pact}; on either side. */
	public static boolean has(Player player, Pact pact) {
		Blood b = of(player);
		return b != null && b.has(pact);
	}

	/** Whether the gift behind {@code lock} is the page's to give (a full vampire's Lifeleech, Night Sense, Groundpound, Bat Wings). */
	static boolean governs(Player player, TreeLocks.Lock lock) {
		return Gift.ofLock(lock) != null && VampireBlood.full(player);
	}

	/** Whether the full vampire has {@code lock}'s gift learned and slotted. */
	static boolean slotted(Player player, TreeLocks.Lock lock) {
		Gift g = Gift.ofLock(lock);
		Blood b = of(player);
		return g != null && b != null && b.knows(g) && b.slotted(g);
	}

	/** Whether move {@code which} of the bar is the player's to use: Feed always, a dhampir's all they have, a full vampire's the slotted gifts. */
	public static boolean moveOpen(Player player, int which) {
		if (!VampireBlood.full(player) || which == NightGifts.FEED || player.isCreative()) {
			return true;
		}
		Gift g = Gift.ofMove(which);
		Blood b = of(player);
		return g == null || b != null && b.knows(g) && b.slotted(g);
	}

	/** Thin blood: a vampire holding it whose reserve is under half mends nothing. */
	public static boolean thin(Player player) {
		return has(player, Pact.THIN) && VampireBlood.get(player) < VampireBlood.MAX * THIN_BELOW;
	}

	/** Fine palate: whether a drink from {@code target} gives a vampire with that pact nothing (only people's blood feeds). */
	static boolean palateRefuses(ServerPlayer player, LivingEntity target) {
		return has(player, Pact.PALATE) && !(Vampires.person(target) || Vampires.mortal(target) || Vampires.netherPrey(target));
	}

	/** A bottle's mB for {@code player}: halved by fine palate. */
	static int bottle(ServerPlayer player, int mb) {
		return has(player, Pact.PALATE) ? Math.round(mb * PALATE_BOTTLE) : mb;
	}

	/** How far animals shy from {@code player}. */
	public static double shyRange(Player player, double normal) {
		return has(player, Pact.UNQUIET) ? UNQUIET_RANGE : normal;
	}

	/** Kindling: fire hurts more (called for every blow, with {@link Vampires#weakness}). */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		return target instanceof Player p && source.is(DamageTypeTags.IS_FIRE) && has(p, Pact.KINDLING) ? amount * KINDLING_FIRE : amount;
	}

	/** Unquiet: a trader asks more of {@code player} once they start trading. */
	public static void tolls(AbstractVillager trader, @Nullable Player player) {
		// only a villager: it clears its special prices each time trading starts (updateSpecialPrices), so the toll is
		// laid fresh once a talk; a wandering trader never clears them, and a toll there would pile up talk after talk
		if (player == null || !(trader instanceof Villager) || !has(player, Pact.UNQUIET)) {
			return;
		}
		for (MerchantOffer offer : trader.getOffers()) {
			offer.addToSpecialPriceDiff(Math.max(1, Math.round(offer.getBaseCostA().getCount() * UNQUIET_TOLL)));
		}
	}

	// ---- the shrine -------------------------------------------------------------------------------------------------------

	private static boolean bottle(ItemFrame frame) {
		return !frame.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(frame.getItem().getItem()).equals(BLOOD_BOTTLE);
	}

	/** Whether the coffin whose head is at {@code head} is dressed: two candles (one lit), a skull and a framed blood bottle near its head. */
	static boolean dressed(ServerLevel level, BlockPos head) {
		int candles = 0;
		boolean lit = false, skull = false;
		for (BlockPos p : BlockPos.betweenClosed(head.offset(-SHRINE_SPAN, -1, -SHRINE_SPAN), head.offset(SHRINE_SPAN, 3, SHRINE_SPAN))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.CANDLES)) {
				candles++;
				lit |= s.hasProperty(CandleBlock.LIT) && s.getValue(CandleBlock.LIT);
			} else if (s.is(BlockTags.SKULLS)) {
				skull = true;
			}
		}
		if (candles < 2 || !lit || !skull) {
			return false;
		}
		AABB box = new AABB(head).inflate(SHRINE_SPAN + 0.5).expandTowards(0.0, 2.0, 0.0);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
			if (bottle(frame)) {
				return true;
			}
		}
		return false;
	}

	/** The head of a dressed coffin within {@code reach} blocks of {@code around}, or null. */
	static @Nullable BlockPos shrineNear(ServerLevel level, BlockPos around, int reach) {
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-reach, -3, -reach), around.offset(reach, 3, reach))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.BEDS) && s.hasProperty(AbstractBedBlock.PART) && s.getValue(AbstractBedBlock.PART) == BedPart.HEAD) {
				BlockPos head = p.immutable();
				if (OldRules.coffin(level, head) && dressed(level, head)) {
					return head;
				}
			}
		}
		return null;
	}

	/** Whether {@code player} stands at a dressed coffin. */
	public static boolean atShrine(ServerPlayer player) {
		return shrineNear(player.level(), player.blockPosition(), (int)SHRINE_REACH) != null;
	}

	// ---- pressing ---------------------------------------------------------------------------------------------------------

	private static void act(ServerPlayer player, Act act) {
		Blood b = of(player);
		if (b == null || !player.isAlive() || act.op < 0 || act.op >= Op.values().length || !atShrine(player)) {
			return;
		}
		List<String> learned = new ArrayList<>(b.learned()), slots = new ArrayList<>(b.slots()), pacts = new ArrayList<>(b.pacts());
		switch (Op.values()[act.op]) {
			case LEARN -> {
				Gift g = Gift.of(act.what);
				if (g == null || b.knows(g) || b.points() < g.cost) {
					return;
				}
				learned.add(g.key());
			}
			case SLOT -> {
				Gift g = Gift.of(act.what);
				if (g == null || !b.knows(g) || b.slotted(g) || slots.size() >= b.slotCount()) {
					return;
				}
				slots.add(g.key());
			}
			case UNSLOT -> {
				if (!slots.remove(act.what)) {
					return;
				}
			}
			case TAKE -> {
				Pact p = Pact.of(act.what);
				if (p == null || b.has(p)) {
					return;
				}
				pacts.add(p.key());
			}
			case GIVE -> {
				if (!pacts.remove(act.what)) {
					return;
				}
				// a slot that no longer exists is let go, the last first
				int room = Math.min(MAX_SLOTS, FREE_SLOTS + pacts.size());
				while (slots.size() > room) {
					slots.remove(slots.size() - 1);
				}
			}
		}
		player.setAttached(STATE, b.with(learned, slots, pacts));
		player.level().playSound(null, player.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	// ---- a life ----------------------------------------------------------------------------------------------------------

	/** A freshly turned vampire starts the page over. */
	static void wake(ServerPlayer player) {
		player.setAttached(STATE, Blood.start());
	}

	/** A bite or a drink on {@code target}: a great one fed upon (a ruler, a warlord, a champion, a boss) is remembered once. */
	static void fedOn(ServerPlayer player, LivingEntity target) {
		Blood b = of(player);
		if (b == null || !great(target) || b.great().contains(target.getUUID().toString())) {
			return;
		}
		List<String> great = new ArrayList<>(b.great());
		great.add(target.getUUID().toString());
		while (great.size() > GREAT_KEPT * 4) {
			great.remove(0);
		}
		advance(player, b, new Blood(b.learned(), b.slots(), b.pacts(), b.nights(), b.moons(), great, b.lastNight(), b.lastMoon()));
	}

	/** A great one: a ruler, a warlord, a champion or anything with a boss's bar. */
	static boolean great(LivingEntity e) {
		return BossBars.boss(e) || e instanceof Mob m && Senses.boss(m);
	}

	private static void advance(ServerPlayer player, Blood was, Blood now) {
		player.setAttached(STATE, now);
		if (now.age() > was.age()) {
			ServerLevel level = player.level();
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), player.getX(), player.getY(0.6), player.getZ(), 14, 0.3, 0.4, 0.3, 0.0);
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		boolean newMoon = night && overworld.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.NEW_MOON;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!VampireBlood.full(player) || !player.isAlive() || player.isSpectator()) {
				strength(player, 0, false);
				continue;
			}
			Blood b = player.getAttachedOrElse(STATE, Blood.start());
			if ((player.tickCount + player.getId()) % 20 == 0) {
				if (night && b.lastNight() != nightNo) {
					boolean moon = newMoon && b.lastMoon() != nightNo;
					b = new Blood(b.learned(), b.slots(), b.pacts(), b.nights() + 1, b.moons() + (moon ? 1 : 0), b.great(), nightNo, moon ? nightNo : b.lastMoon());
					advance(player, player.getAttachedOrElse(STATE, Blood.start()), b);
				} else if (newMoon && b.lastMoon() != nightNo) {
					b = new Blood(b.learned(), b.slots(), b.pacts(), b.nights(), b.moons() + 1, b.great(), b.lastNight(), nightNo);
					advance(player, player.getAttachedOrElse(STATE, Blood.start()), b);
				}
				pacts(player, b);
			}
			strength(player, b.age(), night);
		}
	}

	/** Drowned blood and kindling's stings, once a second. */
	private static void pacts(ServerPlayer player, Blood b) {
		if (player.isCreative()) {
			return;
		}
		ServerLevel level = player.level();
		if (b.has(Pact.DROWNED)) {
			if (player.isInWater()) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, DROWNED_WATER, true, false, true));
			} else if (level.isRainingAt(player.blockPosition())) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, DROWNED_RAIN, true, false, true));
			}
		}
		if (b.has(Pact.KINDLING)) {
			AABB near = player.getBoundingBox().inflate(TORCH_REACH);
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(near.minX, near.minY, near.minZ), BlockPos.containing(near.maxX, near.maxY, near.maxZ))) {
				BlockState s = level.getBlockState(p);
				if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.CAMPFIRE) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
					player.hurtServer(level, level.damageSources().inFire(), TORCH_STING);
					break;
				}
			}
		}
	}

	/** A little night strength with age, by night only. */
	private static void strength(ServerPlayer player, int age, boolean night) {
		modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, night ? AGE_ATTACK[age] : 0.0);
		modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, night && age >= 2 ? OLD_HEALTH : 0.0);
	}

	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
		} else if (current == null || current.amount() != amount) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** The item frame holding the shrine's blood bottle, filled for a crypt's wall. */
	static net.minecraft.world.item.@Nullable ItemStack bottleStack() {
		var item = BuiltInRegistries.ITEM.getOptional(BLOOD_BOTTLE);
		return item.map(net.minecraft.world.item.ItemStack::new).orElse(null);
	}
}
