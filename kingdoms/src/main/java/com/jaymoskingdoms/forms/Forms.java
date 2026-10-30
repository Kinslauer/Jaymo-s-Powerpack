package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.381) Weapon Forms, the engine (Andy's picks from Ultimate Battle, ideas only: docs/WeaponForms-Plan.md). A <i>form</i>
 * is one learned fighting move, tied to a weapon family ({@link Family}), an art or a masterstroke ({@link Kind}).
 * Nobody has a belt or a stance at first: learning your first form ({@link #teach}, from a teacher or {@code /forms teach})
 * unlocks both.
 *
 * <ul>
 * <li>The Five-Art Belt: {@value #BELT_SLOTS} slots in {@link Book}. Slots are changed only within {@link #TRAIN_RANGE} of a
 * training post, a sparring dummy or any mannequin, by sneaking and using one with an empty hand (the belt screen opens).
 * <li>Battle Stance: hold the Combat Art key with a known form's weapon out to take it ({@link #stance}); hold again, put the
 * weapon away or switch to something that doesn't match to leave. In stance a tap fires the selected slot's form
 * ({@link #fire}), sneak and tap the next slot's; Alt and scroll picks the slot. Out of stance the key keeps doing what it did
 * (Charge, War Cry, Shield Throw: {@link CombatArts}).
 * <li>Practice: a form that lands adds practice ({@link #landed}; a real foe counts {@value #FOE_PRACTICE}, a dummy
 * {@value #DUMMY_PRACTICE}) and ranks it up to {@value #RANKS}; each rank hits a little harder and reaches a little further.
 * <li>Masterstrokes only fire on a fallen foe ({@link #masterTarget}): downed, or K.O.'d by Powers' poise.
 * <li>Form Cancels: a form pressed within {@value #CANCEL_TICKS} ticks of a landed chain blow ({@link CombatArts#sinceLanded})
 * skips its wind-up.
 * <li>The chain counter by the crosshair is fed from {@link #chained}.
 * </ul>
 * Powers' lock-on, poise and shake are reached through {@link PowersCompat}; Kingdoms runs without Powers.
 */
public final class Forms {
	public static final int BELT_SLOTS = 5;
	public static final int RANKS = 3;
	/** The two forms anyone can be taught first, so the system can be tested. */
	public static final List<String> STARTER_FORMS = List.of("sheath_draw", "crescent_sweep");

	/** A form pressed this many ticks after a chain blow landed skips its wind-up. */
	static final long CANCEL_TICKS = 14L;
	/** Each rank past the first: this much more damage, and this much more reach (blocks). */
	static final float RANK_DAMAGE = 0.15F;
	static final double RANK_REACH = 0.3;
	/** Practice a landed form earns: on a real foe, and on a dummy. */
	static final int FOE_PRACTICE = 2, DUMMY_PRACTICE = 1;
	/** Rank 2 needs one form's practice figure, rank 3 this many times it. */
	static final int RANK_THREE_FACTOR = 3;
	/** How near a training post, sparring dummy or mannequin you must stand to change the belt or to practise on a post (blocks). */
	static final double TRAIN_RANGE = 5.0;
	/** A form won't fire below this much food (creative aside). */
	static final int MIN_FOOD = 4;
	/** How far ahead a masterstroke looks for a fallen foe (blocks), and how straight ahead it must be (cosine). */
	static final double MASTER_REACH = 4.5, MASTER_FACING = 0.5;
	/** (0.7.406) How far along the look an aimed-at creature counts as a form's target, and how much the aim may be off. */
	static final double AIM_REACH = 10.0, AIM_SLACK = 0.4;
	/** Powers' block and tag for its training post and sparring dummy (read by name: Kingdoms never needs Powers). */
	static final String TRAINING_POST = "jaymospowers:training_post", SPARRING_TAG = "jaymospowers_sparring";
	/** Powers' Afterimage decoys are mannequins too: never a dummy to practise on or set the belt at. */
	static final String AFTERIMAGE_TAG = "jaymospowers_afterimage";

	/** The weapon families; a form works with the weapons its family recognises. */
	public enum Family {
		/** Swords (the katana of Balkon's WeaponMod among them, through {@link #BLADES}). */
		BLADE,
		SPEAR,
		/** An empty main hand. */
		FIST,
		/** Daggers and the like: the {@code light_blades} tag the Thief class reads. */
		KNIFE,
		CLAW;

		public boolean holds(ItemStack main) {
			return switch (this) {
				case BLADE -> main.is(BLADES);
				case SPEAR -> main.is(SPEARS);
				case FIST -> main.isEmpty() || main.is(FISTS);
				case KNIFE -> main.is(ClassPerks.LIGHT_BLADES);
				case CLAW -> main.is(CLAWS);
			};
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public enum Kind {
		ART,
		/** Fires only on a fallen foe. */
		MASTERSTROKE
	}

	/**
	 * One form. {@code windUp} is how long it takes to come out (ticks; a Form Cancel skips it), {@code hunger} the food
	 * exhaustion it costs, {@code practice} the practice rank 2 needs (rank 3 needs {@value #RANK_THREE_FACTOR} times that).
	 */
	public record Form(String id, Family family, Kind kind, int cooldown, int windUp, float hunger, int practice) {
		public Component name() {
			return Component.translatable("form.jaymoskingdoms." + id);
		}
	}

	/** Item tags: the weapons of each family (we add Balkon's katanas and spears, optionally; Powers' pieces are in {@code light_blades}). */
	public static final TagKey<Item> BLADES = tag("form_blades"), SPEARS = tag("form_spears"), CLAWS = tag("form_claws"),
		/** (0.7.383) Cestus-like fist weapons: an empty hand is a fist as well. */
		FISTS = tag("form_fists");

	private static TagKey<Item> tag(String name) {
		return TagKey.create(Registries.ITEM, JaymosKingdoms.id(name));
	}

	/** Every form, in the order they are listed. */
	private static final Map<String, Form> FORMS = new LinkedHashMap<>();

	static {
		BladeForms.addTo(FORMS);
		SpearForms.addTo(FORMS);
		FistForms.addTo(FORMS);
		KnifeForms.addTo(FORMS);
		ClawForms.addTo(FORMS);
	}

	/**
	 * A player's forms: the practice of each one they know (a taught form has an entry, even at 0), the belt ("" for a bare
	 * slot), whether they're in stance, and the selected slot. Saved, kept through death and synced to its own player.
	 */
	public record Book(Map<String, Integer> practice, List<String> belt, boolean stance, int slot) {
		public static final Book EMPTY = new Book(Map.of(), List.of(), false, 0);
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("practice", Map.of()).forGetter(Book::practice),
			Codec.STRING.listOf().optionalFieldOf("belt", List.of()).forGetter(Book::belt),
			Codec.BOOL.optionalFieldOf("stance", false).forGetter(Book::stance),
			Codec.INT.optionalFieldOf("slot", 0).forGetter(Book::slot)
		).apply(i, Book::new));
		static final StreamCodec<FriendlyByteBuf, Book> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), b -> new HashMap<>(b.practice()),
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Book::belt,
			ByteBufCodecs.BOOL, Book::stance,
			ByteBufCodecs.VAR_INT, Book::slot,
			(practice, belt, stance, slot) -> new Book(Map.copyOf(practice), belt, stance, slot));

		/** The belt always has {@value Forms#BELT_SLOTS} slots, and the selected one is one of them. */
		public Book {
			List<String> padded = new ArrayList<>(belt);
			while (padded.size() < BELT_SLOTS) {
				padded.add("");
			}
			belt = List.copyOf(padded.subList(0, BELT_SLOTS));
			slot = Math.floorMod(slot, BELT_SLOTS);
		}

		public boolean knows(String id) {
			return practice.containsKey(id);
		}

		/** The forms known, in registry order. */
		public List<String> known() {
			List<String> list = new ArrayList<>();
			for (String id : FORMS.keySet()) {
				if (practice.containsKey(id)) {
					list.add(id);
				}
			}
			return list;
		}

		public int rank(String id) {
			Form form = FORMS.get(id);
			Integer done = practice.get(id);
			if (form == null || done == null) {
				return 0;
			}
			return done >= form.practice() * RANK_THREE_FACTOR ? 3 : done >= form.practice() ? 2 : 1;
		}

		Book with(Map<String, Integer> practice) {
			return new Book(Map.copyOf(practice), belt, stance, slot);
		}

		Book withBelt(List<String> belt) {
			return new Book(practice, belt, stance, slot);
		}

		Book withStance(boolean stance) {
			return new Book(practice, belt, stance, slot);
		}

		Book withSlot(int slot) {
			return new Book(practice, belt, stance, slot);
		}
	}

	public static final AttachmentType<Book> BOOK = AttachmentRegistry.create(JaymosKingdoms.id("forms"),
		builder -> builder.persistent(Book.CODEC).copyOnDeath().syncWith(Book.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: take the stance, or leave it. */
	public record Stance(boolean on) implements CustomPacketPayload {
		public static final Type<Stance> TYPE = new Type<>(JaymosKingdoms.id("form_stance"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stance> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.on), buf -> new Stance(buf.readBoolean()));

		@Override
		public Type<Stance> type() {
			return TYPE;
		}
	}

	/** Player → server: fire the selected slot's form, or (sneaking) the next slot's. */
	public record Fire(boolean sneaking) implements CustomPacketPayload {
		public static final Type<Fire> TYPE = new Type<>(JaymosKingdoms.id("form_fire"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.sneaking), buf -> new Fire(buf.readBoolean()));

		@Override
		public Type<Fire> type() {
			return TYPE;
		}
	}

	/** Player → server: move the selected slot (Alt and scroll); {@code delta} is -1 or 1. */
	public record Pick(int delta) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("form_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.delta), buf -> new Pick(buf.readVarInt()));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** Player → server (the belt screen): put {@code form} ("" for none) in {@code slot}. */
	public record Assign(int slot, String form) implements CustomPacketPayload {
		public static final Type<Assign> TYPE = new Type<>(JaymosKingdoms.id("form_assign"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Assign> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.slot);
				buf.writeUtf(p.form);
			}, buf -> new Assign(buf.readVarInt(), buf.readUtf()));

		@Override
		public Type<Assign> type() {
			return TYPE;
		}
	}

	/** Server → player: open the belt screen (they sneaked and used a training post or dummy). */
	public record OpenBelt() implements CustomPacketPayload {
		public static final Type<OpenBelt> TYPE = new Type<>(JaymosKingdoms.id("form_open_belt"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenBelt> CODEC = StreamCodec.unit(new OpenBelt());

		@Override
		public Type<OpenBelt> type() {
			return TYPE;
		}
	}

	/** Server → player: their chain of linked blows is now {@code links} long (the counter by the crosshair). */
	public record Chain(int links) implements CustomPacketPayload {
		public static final Type<Chain> TYPE = new Type<>(JaymosKingdoms.id("form_chain"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Chain> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.links), buf -> new Chain(buf.readVarInt()));

		@Override
		public Type<Chain> type() {
			return TYPE;
		}
	}

	/** A form still winding up. */
	private record Windup(UUID who, Form form, long at) {
	}

	/** Player → game time each form is ready again. */
	private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();
	private static final List<Windup> WINDUPS = new ArrayList<>();

	private Forms() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Stance.TYPE, Stance.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Fire.TYPE, Fire.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Assign.TYPE, Assign.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenBelt.TYPE, OpenBelt.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Chain.TYPE, Chain.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Stance.TYPE, (payload, context) -> stance(context.player(), payload.on()));
		ServerPlayNetworking.registerGlobalReceiver(Fire.TYPE, (payload, context) -> fire(context.player(), payload.sneaking()));
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> pick(context.player(), payload.delta()));
		ServerPlayNetworking.registerGlobalReceiver(Assign.TYPE, (payload, context) -> assign(context.player(), payload.slot(), payload.form()));
		ServerTickEvents.END_SERVER_TICK.register(Forms::tick);
		SpearForms.register();
		BladeForms.register();
		FistForms.register();
		KnifeForms.register();
		ClawForms.register();
		// (the stance isn't kept across a log-in)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Book book = book(handler.player);
			if (book.stance()) {
				handler.player.setAttached(BOOK, book.withStance(false));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			READY.remove(handler.player.getUUID());
			WINDUPS.removeIf(w -> w.who().equals(handler.player.getUUID()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			WINDUPS.clear();
		});
		// the belt opens on a sneaking use, empty-handed, of a training post or a mannequin (a sparring dummy); a Z Fighter's
		// use of a post is Powers' drill screen, so a Z Fighter sets the belt at a mannequin
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && wantsBelt(sp, hand == net.minecraft.world.InteractionHand.MAIN_HAND)
				&& !"zfighter".equals(com.jaymoskingdoms.PowersCompat.power(sp))
				&& BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).toString().equals(TRAINING_POST)) {
				return openBelt(sp);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof Mannequin && dummy(entity) && wantsBelt(sp, hand == net.minecraft.world.InteractionHand.MAIN_HAND)) {
				return openBelt(sp);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- the public interface (the veteran guards' drills call these) --------------------------------------------------

	public static Form get(String formId) {
		return FORMS.get(formId);
	}

	/** Every form's id, in order. */
	public static List<String> ids() {
		return List.copyOf(FORMS.keySet());
	}

	public static Book book(Player player) {
		return player.getAttachedOrElse(BOOK, Book.EMPTY);
	}

	/** Whether {@code player} knows the form. */
	public static boolean knows(Player player, String formId) {
		return book(player).knows(formId);
	}

	/** {@code player}'s rank in the form: 0 unknown, 1 to {@value #RANKS}. */
	public static int rank(Player player, String formId) {
		return book(player).rank(formId);
	}

	/** The forms {@code player} knows, in order. */
	public static List<String> known(Player player) {
		return book(player).known();
	}

	/**
	 * Teaches {@code player} the form. True if it is new to them. Their first form unlocks the belt and the stance and goes
	 * in slot 0; later ones go in the first bare slot, if any. Told on the action bar.
	 */
	public static boolean teach(ServerPlayer player, String formId) {
		Form form = FORMS.get(formId);
		Book before = book(player);
		if (form == null || before.knows(formId)) {
			return false;
		}
		Map<String, Integer> practice = new HashMap<>(before.practice());
		practice.put(formId, 0);
		List<String> belt = new ArrayList<>(before.belt());
		int bare = belt.indexOf("");
		if (bare >= 0) {
			belt.set(bare, formId);
		}
		player.setAttached(BOOK, before.with(practice).withBelt(belt));
		boolean first = before.practice().isEmpty();
		player.sendOverlayMessage(Component.translatable(first ? "message.jaymoskingdoms.form.first" : "message.jaymoskingdoms.form.learned",
			form.name()).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
		return true;
	}

	/** Takes the form back (and off the belt). False if they didn't know it. */
	public static boolean forget(ServerPlayer player, String formId) {
		Book before = book(player);
		if (!before.knows(formId)) {
			return false;
		}
		Map<String, Integer> practice = new HashMap<>(before.practice());
		practice.remove(formId);
		List<String> belt = new ArrayList<>(before.belt());
		belt.replaceAll(id -> id.equals(formId) ? "" : id);
		Book after = before.with(practice).withBelt(belt);
		player.setAttached(BOOK, practice.isEmpty() ? Book.EMPTY : after);
		return true;
	}

	/** Practice {@code amount} on the form (rank ups are announced). Nothing if they don't know it. */
	public static void practice(ServerPlayer player, String formId, int amount) {
		Book before = book(player);
		Form form = FORMS.get(formId);
		if (form == null || amount <= 0 || !before.knows(formId)) {
			return;
		}
		Map<String, Integer> practice = new HashMap<>(before.practice());
		practice.merge(formId, amount, Integer::sum);
		Book after = before.with(practice);
		player.setAttached(BOOK, after);
		// (0.7.384) practice in a joined school's family is standing there, but only from real fights and teachers (a post or dummy alone earns none)
		if (amount >= FOE_PRACTICE) {
			Schools.practiced(player, form.family(), amount);
		}
		if (after.rank(formId) > before.rank(formId)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.rank", form.name(), after.rank(formId)).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.8F);
		}
	}

	/** Whether {@code player} holds a weapon any form they know works with: the stance can be taken. */
	public static boolean canStance(Player player) {
		if (SpearForms.pinned(player)) {
			// (0.7.382) a Pinning Throw has the spear out of the hand: the stance holds until it is tugged back
			return true;
		}
		Book book = book(player);
		ItemStack main = player.getMainHandItem();
		for (String id : book.known()) {
			if (FORMS.get(id).family().holds(main)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code target} lies fallen: downed, or knocked out by Powers' poise or wounds. */
	public static boolean fallen(LivingEntity target) {
		return target.isAlive() && (Downed.isDowned(target) || PowersCompat.poiseBroken(target) || PowersCompat.helpless(target));
	}

	/** The nearest fallen foe straight ahead of {@code player} within {@link #MASTER_REACH}, or null: what a masterstroke needs. */
	public static @Nullable LivingEntity masterTarget(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MASTER_REACH), e -> e != player && fallen(e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition());
			double d = to.length();
			if (d <= MASTER_REACH && d > 1.0E-3 && to.scale(1.0 / d).dot(look) > MASTER_FACING && d < bestDistance) {
				best = e;
				bestDistance = d;
			}
		}
		return best;
	}

	// ---- helpers for the families' forms --------------------------------------------------------------------------------

	/** What a form of {@code multiple} of {@code player}'s attack deals at {@code rank}. */
	static float damage(ServerPlayer player, float multiple, int rank) {
		return (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiple * (1.0F + RANK_DAMAGE * (rank - 1));
	}

	/** {@code base} blocks of reach at {@code rank}. */
	static double reach(double base, int rank) {
		return base + RANK_REACH * (rank - 1);
	}

	/**
	 * The foes a form may hit in {@code box}: the Combat Arts' foes, the mannequins (sparring dummies) there, and (0.7.406)
	 * whatever {@code player} is locked onto or aiming at in it, hostile or not (a deer, a wolf, a rival), short of a pet
	 * of theirs or a party member.
	 */
	static List<LivingEntity> targets(ServerLevel level, ServerPlayer player, AABB box) {
		List<LivingEntity> list = new ArrayList<>(CombatArts.foes(level, player, box));
		for (Mannequin dummy : level.getEntitiesOfClass(Mannequin.class, box, e -> e.isAlive() && dummy(e) && !list.contains(e))) {
			list.add(dummy);
		}
		LivingEntity lock = PowersCompat.lockTarget(player);
		Vec3 eye = player.getEyePosition();
		Vec3 far = eye.add(player.getLookAngle().scale(AIM_REACH));
		for (LivingEntity aimed : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !list.contains(e)
			&& !(e instanceof Mannequin) && !friendly(player, e))) {
			if (aimed == lock || aimed.getBoundingBox().inflate(AIM_SLACK).clip(eye, far).isPresent()) {
				list.add(aimed);
			}
		}
		return list;
	}

	/** (0.7.406) Whether a form of {@code player}'s should pass {@code entity} by: their own pet, or a party member. */
	private static boolean friendly(ServerPlayer player, LivingEntity entity) {
		if (entity instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerReference() == null ? null : pet.getOwnerReference().getUUID())) {
			return true;
		}
		return entity instanceof ServerPlayer other && Parties.allied(player, other);
	}

	/** Practice from a form that landed on {@code hits} (the best one: a sweep through three foes isn't three lessons). */
	static void landed(ServerPlayer player, Form form, List<LivingEntity> hits) {
		int best = 0;
		for (LivingEntity hit : hits) {
			best = Math.max(best, hit instanceof Mannequin ? DUMMY_PRACTICE : FOE_PRACTICE);
		}
		if (hits.isEmpty() && nearPost(player)) {
			best = DUMMY_PRACTICE;
		}
		practice(player, form.id(), best);
	}

	/** Whether {@code player} stands near a training post, a sparring dummy or a mannequin: where the belt can be changed. */
	/** Whether {@code entity} (a mannequin) is a practice dummy, not someone's Afterimage decoy. */
	static boolean dummy(net.minecraft.world.entity.Entity entity) {
		return !entity.entityTags().contains(AFTERIMAGE_TAG) && !entity.entityTags().contains(KnifeForms.DECOY_TAG);
	}

	public static boolean trainingSpot(ServerPlayer player) {
		return nearPost(player) || !player.level().getEntitiesOfClass(Mannequin.class, player.getBoundingBox().inflate(TRAIN_RANGE), Forms::dummy).isEmpty();
	}

	private static boolean nearPost(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		int r = (int)TRAIN_RANGE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
			if (BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString().equals(TRAINING_POST)) {
				return true;
			}
		}
		return false;
	}

	/** Tells the chain counter: {@code links} blows now. Called by {@link CombatArts} each time a blow links on. */
	public static void chained(ServerPlayer player, int links) {
		if (ServerPlayNetworking.canSend(player, Chain.TYPE)) {
			ServerPlayNetworking.send(player, new Chain(links));
		}
	}

	// ---- belt ----------------------------------------------------------------------------------------------------------

	private static boolean wantsBelt(ServerPlayer player, boolean mainHand) {
		return mainHand && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && !player.isSpectator() && !book(player).practice().isEmpty();
	}

	private static InteractionResult openBelt(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, OpenBelt.TYPE)) {
			ServerPlayNetworking.send(player, new OpenBelt());
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void assign(ServerPlayer player, int slot, String formId) {
		Book book = book(player);
		if (slot < 0 || slot >= BELT_SLOTS || !(formId.isEmpty() || book.knows(formId)) || book.stance() || player.isSpectator()) {
			return;
		}
		if (!trainingSpot(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.belt_away").withStyle(ChatFormatting.GRAY));
			return;
		}
		List<String> belt = new ArrayList<>(book.belt());
		if (!formId.isEmpty()) {
			// (a form sits in one slot only: it moves)
			belt.replaceAll(id -> id.equals(formId) ? "" : id);
		}
		belt.set(slot, formId);
		player.setAttached(BOOK, book.withBelt(belt));
	}

	private static void pick(ServerPlayer player, int delta) {
		Book book = book(player);
		if (book.stance() && delta != 0) {
			player.setAttached(BOOK, book.withSlot(book.slot() + (delta > 0 ? 1 : -1)));
		}
	}

	// ---- stance --------------------------------------------------------------------------------------------------------

	private static void stance(ServerPlayer player, boolean on) {
		Book book = book(player);
		if (book.stance() == on || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (on && (book.practice().isEmpty() || !canStance(player) || Downed.isDowned(player))) {
			return;
		}
		player.setAttached(BOOK, book.withStance(on));
		player.sendOverlayMessage(Component.translatable(on ? "message.jaymoskingdoms.form.stance" : "message.jaymoskingdoms.form.stance_off")
			.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5F,
			on ? 1.6F : 1.2F);
	}

	// ---- firing --------------------------------------------------------------------------------------------------------

	private static void fire(ServerPlayer player, boolean sneaking) {
		Book book = book(player);
		if (!book.stance() || !(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		// (0.7.382) a spear thrown to pin a foe: any press tugs it back
		if (SpearForms.tug(player)) {
			return;
		}
		// (0.7.386) inside a Divine-born's Eight-Sign Circle no form can be used
		if (PowersCompat.sealed(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.sealed").withStyle(ChatFormatting.GOLD));
			return;
		}
		// (0.7.406) two broken arms can't swing a weapon, so they can't do a form either
		if (PowersCompat.armless(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.armless").withStyle(ChatFormatting.GRAY));
			return;
		}
		String id = book.belt().get(sneaking ? Math.floorMod(book.slot() + 1, BELT_SLOTS) : book.slot());
		Form form = FORMS.get(id);
		if (form == null || !book.knows(id)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.bare").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!form.family().holds(player.getMainHandItem())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.weapon", form.name()).withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = level.getGameTime();
		long left = READY.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).getOrDefault(id, 0L) - now;
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.wait", form.name(), (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (form.kind() == Kind.MASTERSTROKE && masterTarget(player) == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.masterstroke").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.hungry").withStyle(ChatFormatting.GRAY));
			return;
		}
		// (0.7.382) Couched Lance is the one form that wants a mount; the others can't be done from a saddle
		if (form == SpearForms.COUCHED_LANCE ? !SpearForms.mounted(player) : player.isPassenger()) {
			if (form == SpearForms.COUCHED_LANCE) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.mounted").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (player.isFallFlying()) {
			return;
		}
		// the cooldown is set now (no second press during the wind-up); the food is spent when the form goes off
		READY.get(player.getUUID()).put(id, now + form.cooldown());
		// Form Cancel: right after a chain blow landed, the wind-up is skipped
		boolean cancel = CombatArts.sinceLanded(player) <= CANCEL_TICKS;
		if (cancel || form.windUp() <= 0) {
			if (cancel) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.cancel", form.name()).withStyle(ChatFormatting.GOLD));
			}
			perform(player, form);
			return;
		}
		WINDUPS.add(new Windup(player.getUUID(), form, now + form.windUp()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	private static void perform(ServerPlayer player, Form form) {
		// (0.7.386) a wind-up that ran on into an Eight-Sign Circle comes to nothing (no hunger spent)
		if (PowersCompat.sealed(player)) {
			return;
		}
		player.causeFoodExhaustion(form.hunger());
		int rank = Math.max(1, rank(player, form.id()));
		if (form.family() == Family.BLADE) {
			BladeForms.perform(player, form, rank);
		} else if (form.family() == Family.SPEAR) {
			SpearForms.perform(player, form, rank);
		}
		if (form.family() == Family.FIST) {
			FistForms.perform(player, form, rank);
		}
		if (form.family() == Family.KNIFE) {
			KnifeForms.perform(player, form, rank);
		}
		if (form.family() == Family.CLAW) {
			ClawForms.perform(player, form, rank);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Book book = book(player);
			if (book.stance() && (!player.isAlive() || player.isSpectator() || !canStance(player) || Downed.isDowned(player))) {
				// the weapon was put away, or swapped for something none of their forms fit
				stance(player, false);
				if (!player.isAlive() || player.isSpectator()) {
					player.setAttached(BOOK, book(player).withStance(false));
				}
			}
		}
		for (Iterator<Windup> it = WINDUPS.iterator(); it.hasNext(); ) {
			Windup w = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(w.who());
			if (player == null || !player.isAlive() || !book(player).stance()) {
				it.remove();
				// a wind-up cut short by the stance ending gives its cooldown back
				Map<String, Long> ready = player == null ? null : READY.get(w.who());
				if (ready != null) {
					ready.remove(w.form().id());
				}
			} else if (player.level().getGameTime() >= w.at()) {
				it.remove();
				perform(player, w.form());
			}
		}
	}
}
