package com.jaymospowers.core.finisher;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Treatment;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import com.jaymospowers.power.darktide.DarktideStatus;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.98) Finishers. Andy: "every weapon should be able to right click and hold a downed enemy, and then do a unique
 * finisher animation". Hold use (not sneaking: sneak + use stays the revive and the pick-up) on someone downed for
 * {@link #HOLD_TICKS} and you finish them: one move per kind of weapon in your main hand ({@link Kind}), read off the
 * item's tags and name, so a weapon forged at the Tool Station or another mod's falls in with its kind.
 * <p>
 * (0.15.110) Andy: "make the execution only possible with bare hands, and only activated after tapping the target three
 * times. this is to prevent accidental neck snaps. Also make all the other executions unlocked in the combat arts tree
 * replacing the current executioner function". So the neck snap is bare hands only, on the {@link #TAPS}th quick tap (a
 * hold does nothing), and anything else that isn't a weapon (a torch, a loaf) finishes nobody. The weapons' finishers
 * are Kingdoms' Combat Arts perk Executioner ({@link #weaponsUnlocked}); without it a hold with a weapon does nothing.
 * <p>
 * The move plays out on its own once begun: you stand rooted facing them (no walking, jumping, swinging or using), the
 * body drawn by {@code FinisherPose} on every client, and the blows land on its beats (sound, blood or bone, a jolt
 * through the body). The last blow is their death, credited to you ({@link Downed#execute}); an axe takes the head
 * first. It stops, sparing them, if you're downed or die, or they get up, are carried off or end up out of reach.
 * <p>
 * Not on tamed animals (someone's dog or horse), and only one finisher on a body at a time. A plain tap on a downed
 * guard still has Kingdoms take its weapons first.
 * <p>
 * (0.15.113) Andy: "the pointblank finisher should use a bullet and actually play the blast", and "neck break should take
 * 5s to finish". Guns (our TaCZ flintlocks, TaCZ's own, the Void Armory's) have their own finisher, {@link Kind#GUN}: it
 * needs a round in the gun, spends it, and fires with the gun's own shot (through TaCZ), flash and smoke. A bow or
 * crossbow likewise needs an arrow and spends it ({@link #loaded}, {@link #spend}). The neck snap is a slow strangle-hold
 * now, the snap {@link Kind#FIST}'s 100th tick.
 */
public final class Finishers {
	/** How long use is held on the downed before a weapon's finisher begins (ticks), and the grace between use's repeats (every 4 ticks). */
	public static final int HOLD_TICKS = 8, HOLD_GRACE = 8;
	/**
	 * (0.15.110) Bare hands take taps, not a hold, so nobody snaps a neck by accident: this many taps, each a fresh press
	 * (use quiet for more than {@link #PRESS_GAP} ticks between them, held no longer than {@link #TAP_MAX}), none more than
	 * {@link #TAP_WINDOW} ticks after the last.
	 */
	public static final int TAPS = 3, PRESS_GAP = 5, TAP_MAX = 8, TAP_WINDOW = 20;
	/** (0.15.110) The Combat Arts perk (Jaymo's Kingdoms) that unlocks every weapon's finisher; bare hands need none. */
	static final String UNLOCK_PERK = "executioner";
	/** How far they may be from you for it to go on (blocks, past your reach for the use). */
	static final double SLACK = 1.0, MAX_DISTANCE = 4.0;
	/** The hold's bar length. */
	static final int HOLD_BAR = 8;
	/** Screen shake on a blow for you and the one being finished, and the last blow's for both and those around. */
	static final float BEAT_SHAKE = 0.6F, KILL_SHAKE = 1.4F, AROUND_SHAKE = 0.8F;
	static final double AROUND_RANGE = 10.0;
	/** (0.15.113) How far the point-blank shot is heard through TaCZ (blocks), and its shake for those around. */
	static final int SHOT_HEARD = 64;
	static final float SHOT_AROUND_SHAKE = 0.5F;

	/** How a blow lands: what it sounds and looks like. */
	public enum Strike { CUT, STAB, CRUSH, PIERCE, SHOT, BOLT, BLAST, SNAP }

	/**
	 * One finisher per kind of weapon: how long it plays (ticks), the beats its blows land on (the last is the kill), how
	 * each lands, and what is heard as it begins. The poses are the client's ({@code FinisherPose}), keyed by name.
	 */
	public enum Kind {
		/** Run Through: the blade turned point down in both hands, raised, driven in, twisted and wrenched free. */
		SWORD(38, new int[] {17, 28}, Strike.STAB),
		/** Headsman's Stroke: a two-handed blade swung up over the shoulder and down in one great cleave. */
		GREATSWORD(36, new int[] {20}, Strike.CUT),
		/** Mercy Stroke: down on a knee, three quick stabs, and the throat drawn across. */
		DAGGER(40, new int[] {10, 14, 18, 28}, Strike.STAB),
		/** Executioner: feet planted, the axe raised high and brought down on the neck. The head comes off. */
		AXE(36, new int[] {21}, Strike.CUT),
		/** Skull Crusher: a blow, the weapon heaved up again, and a second brought down with everything behind it. */
		BLUNT(44, new int[] {14, 31}, Strike.CRUSH),
		/** Pinned: the spear turned point down, driven through, leant on, and wrenched out. */
		SPEAR(36, new int[] {14, 27}, Strike.PIERCE),
		/** Reaping: the polearm swept round behind, and down in a wide arc across them. */
		POLEARM(34, new int[] {20}, Strike.CUT),
		/** The staff twirled, then the butt driven down twice. */
		STAFF(38, new int[] {18, 27}, Strike.CRUSH),
		/** Loosed Close: an arrow nocked, drawn slow, aimed straight down, loosed. (0.15.113) Spends an arrow. */
		BOW(32, new int[] {22}, Strike.SHOT),
		/** A bolt to the heart: the crossbow levelled at them, and the trigger pulled. (0.15.113) Spends its bolt. */
		CROSSBOW(26, new int[] {13}, Strike.BOLT),
		/** (0.15.113) Point Blank: the gun brought up, cocked, aimed down at the head at arm's length, and fired. Spends a round. */
		GUN(30, new int[] {20}, Strike.BLAST),
		/**
		 * Bare hands: kneel, take the head in both hands, and twist. (0.15.113) Andy: "neck break should take 5s to finish":
		 * a hold that strains twice against them before the snap at 5 s.
		 */
		FIST(118, new int[] {40, 70, 100}, Strike.SNAP);

		public final int length;
		public final int[] beats;
		public final Strike strike;

		Kind(int length, int[] beats, Strike strike) {
			this.length = length;
			this.beats = beats;
			this.strike = strike;
		}

		/** The beat its kill lands on. */
		public int kill() {
			return this.beats[this.beats.length - 1];
		}

		public static Kind of(int ordinal) {
			Kind[] all = values();
			return all[Math.floorMod(ordinal, all.length)];
		}
	}

	/** Name words for each kind, checked in this order (a poleaxe is a polearm before it's an axe). */
	private static final String[] POLEARM_WORDS = {"glaive", "halberd", "scythe", "naginata", "bardiche", "poleaxe", "pole_axe", "partisan",
		"guisarme", "voulge", "billhook"};
	private static final String[] STAFF_WORDS = {"staff", "quarterstaff", "bo_staff"};
	private static final String[] SPEAR_WORDS = {"spear", "lance", "pike", "trident", "javelin", "pilum"};
	private static final String[] DAGGER_WORDS = {"dagger", "knife", "kunai", "tanto", "dirk", "stiletto", "misericorde", "shiv", "athame"};
	private static final String[] GREATSWORD_WORDS = {"claymore", "longsword", "greatsword", "great_sword", "broadsword", "zweihander", "flamberge",
		"bastard_sword"};
	private static final String[] SWORD_WORDS = {"sword", "katana", "saber", "sabre", "rapier", "cutlass", "scimitar", "falchion", "machete",
		"wakizashi", "ninjato", "blade", "estoc"};
	private static final String[] AXE_WORDS = {"axe", "hatchet", "cleaver", "tomahawk"};
	/** (0.15.113) Another mod's gun, by name (TaCZ's and ours are known by their gun id). */
	private static final String[] GUN_WORDS = {"musket", "blunderbuss", "flintlock", "pistol", "rifle", "revolver", "arquebus", "handgun",
		"shotgun"};
	private static final String[] BLUNT_WORDS = {"mace", "hammer", "cudgel", "club", "flail", "morningstar", "morning_star", "maul", "sledge",
		"mattock", "pickaxe", "scepter", "sceptre"};

	/** The finisher being played: which, since when (game time), and on whom (entity id). Synced to everyone, not saved. */
	public record Finish(int kind, long start, int target) {
		public static final StreamCodec<ByteBuf, Finish> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Finish::kind,
			ByteBufCodecs.VAR_LONG, Finish::start, ByteBufCodecs.VAR_INT, Finish::target, Finish::new);

		public Kind type() {
			return Kind.of(this.kind);
		}
	}

	public static final AttachmentType<Finish> FINISHING = AttachmentRegistry.create(JaymosPowers.id("finishing"),
		builder -> builder.syncWith(Finish.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Rooted while it plays: walking and jumping gone (the finisher, and a downed player being finished). */
	static final Identifier ROOT_ID = JaymosPowers.id("finisher_root"), JUMP_ID = JaymosPowers.id("finisher_jump");

	/**
	 * Use held on a downed one: on whom, since when, and when use last came; (0.15.110) bare-handed, the taps so far and
	 * when this press began.
	 */
	private static final class Hold {
		final LivingEntity body;
		final long start;
		final boolean bare;
		long last, pressStart;
		int presses = 1;

		Hold(LivingEntity body, long start, boolean bare) {
			this.body = body;
			this.start = start;
			this.bare = bare;
			this.last = start;
			this.pressStart = start;
		}
	}

	private static final boolean KINGDOMS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static final java.lang.reflect.@Nullable Method KINGDOMS_RANK;

	static {
		java.lang.reflect.Method rank = null;
		if (KINGDOMS) {
			try {
				rank = Class.forName("com.jaymoskingdoms.Skills").getMethod("rank", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				rank = null;
			}
		}
		KINGDOMS_RANK = rank;
	}

	/** A finisher playing: who, on whom. */
	private record Playing(ServerPlayer player, LivingEntity body, boolean downed) {
	}

	private static final Map<UUID, Hold> HOLDING = new HashMap<>();
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	/** The bodies being finished, by entity UUID, and by whom. */
	private static final Map<UUID, UUID> TARGETED = new HashMap<>();

	private Finishers() {
	}

	public static void register() {
		// before Kingdoms' disarm and Downed's catch-all, but only watching: they still get the click
		Identifier watch = JaymosPowers.id("finisher_watch");
		UseEntityCallback.EVENT.addPhaseOrdering(watch, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(watch, Finishers::onUseEntity);
		// rooted and busy: nothing else from your hands while it plays
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			holds(server);
			plays(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDING.clear();
			PLAYING.clear();
			TARGETED.clear();
		});
	}

	// ---- queries (both sides: the attachment is synced) ------------------------------------------------------------------

	/** The finisher {@code player} is playing, or null. */
	public static @Nullable Finish of(Entity player) {
		return player.getAttached(FINISHING);
	}

	/** Whether {@code player} is in the middle of a finisher (rooted, hands busy). */
	public static boolean busy(Entity player) {
		return player.hasAttached(FINISHING);
	}

	/** Server side: whether someone is finishing {@code body} just now (it lies still for it: {@code Downed.step}). */
	public static boolean targeted(Entity body) {
		return TARGETED.containsKey(body.getUUID());
	}

	/** The finisher a player holding {@code stack} plays. */
	public static Kind kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return Kind.FIST;
		}
		if (gun(stack)) {
			return Kind.GUN;
		}
		if (stack.getItem() instanceof CrossbowItem) {
			return Kind.CROSSBOW;
		}
		if (stack.getItem() instanceof BowItem) {
			return Kind.BOW;
		}
		if (stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS)) {
			return Kind.SPEAR;
		}
		if (stack.is(Items.MACE)) {
			return Kind.BLUNT;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.contains("crossbow")) {
			return Kind.CROSSBOW;
		}
		if (path.endsWith("bow") && !path.endsWith("elbow") || path.contains("longbow") || path.contains("shortbow")) {
			return Kind.BOW;
		}
		if (any(path, POLEARM_WORDS)) {
			return Kind.POLEARM;
		}
		if (any(path, STAFF_WORDS)) {
			return Kind.STAFF;
		}
		if (any(path, SPEAR_WORDS)) {
			return Kind.SPEAR;
		}
		if (any(path, DAGGER_WORDS)) {
			return Kind.DAGGER;
		}
		if (any(path, GREATSWORD_WORDS)) {
			return Kind.GREATSWORD;
		}
		if (stack.is(ItemTags.SWORDS) || any(path, SWORD_WORDS)) {
			return Kind.SWORD;
		}
		if (stack.is(ItemTags.PICKAXES)) {
			return Kind.BLUNT;
		}
		if (stack.is(ItemTags.AXES) || any(path, AXE_WORDS)) {
			return Kind.AXE;
		}
		if (any(path, BLUNT_WORDS)) {
			return Kind.BLUNT;
		}
		return Kind.FIST;
	}

	/** (0.15.113) A gun: TaCZ's (any with a gun id), the Void Armory's flintlock, or another mod's by name. */
	static boolean gun(ItemStack stack) {
		if (!TaczGuns.gunId(stack).isEmpty() || ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.endsWith("_gun") || any(path, GUN_WORDS);
	}

	/**
	 * (0.15.113) Whether {@code player}'s {@code stack} has what its finisher shoots: a round in the gun (TaCZ's, or the
	 * Void Armory flintlock unspent; another mod's gun we can't read, so yes), an arrow for the bow, the crossbow loaded
	 * or an arrow for it. Anything else needs nothing.
	 */
	static boolean loaded(Player player, ItemStack stack, Kind kind) {
		return switch (kind) {
			case GUN -> !TaczGuns.gunId(stack).isEmpty() ? TaczGuns.ammo(stack) > 0
				: !(ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) || !Boolean.TRUE.equals(stack.get(ArsenalFlintlock.SPENT));
			case BOW, CROSSBOW -> player.hasInfiniteMaterials() || charged(stack) || !player.getProjectile(stack).isEmpty();
			default -> true;
		};
	}

	private static boolean charged(ItemStack stack) {
		ChargedProjectiles charge = stack.get(DataComponents.CHARGED_PROJECTILES);
		return charge != null && !charge.isEmpty();
	}

	/** (0.15.113) The shot's spent: the gun's round, the crossbow's bolt, or an arrow (Infinity and creative keep theirs). */
	static void spend(ServerLevel level, Player player, ItemStack stack, Kind kind) {
		switch (kind) {
			case GUN -> {
				if (!TaczGuns.spendRound(stack) && ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) {
					stack.set(ArsenalFlintlock.SPENT, true);
				}
			}
			case BOW, CROSSBOW -> {
				if (kind == Kind.CROSSBOW && charged(stack)) {
					stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
					return;
				}
				if (player.hasInfiniteMaterials()) {
					return;
				}
				ItemStack arrow = player.getProjectile(stack);
				if (!arrow.isEmpty()) {
					arrow.shrink(EnchantmentHelper.processAmmoUse(level, stack, arrow, 1));
				}
			}
			default -> {
			}
		}
	}

	private static boolean any(String path, String[] words) {
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (0.15.110) Whether {@code player} has the weapons' finishers: Andy, "make all the other executions unlocked in the combat
	 * arts tree replacing the current executioner function". Kingdoms' Executioner perk (read by reflection, as Freerun
	 * reads its perks); everyone in creative, and everyone if Kingdoms isn't loaded.
	 */
	public static boolean weaponsUnlocked(Player player) {
		if (player.isCreative() || !KINGDOMS) {
			return true;
		}
		if (KINGDOMS_RANK == null) {
			return false;
		}
		try {
			return (int) KINGDOMS_RANK.invoke(null, player, UNLOCK_PERK) > 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * (0.15.113) Whether {@code body} is down for a finisher: downed, or (Andy: "crippled enemies should be executable too")
	 * crippled, off its feet: Darktide's crawl, both legs broken ({@link Breaks#crawlOnly}), or a leg cut off.
	 */
	public static boolean helpless(LivingEntity body) {
		return Downed.isDowned(body) || DarktideStatus.crippled(body) || Breaks.crawlOnly(body)
			|| (Dismember.missing(body) & (Dismember.LEFT_LEG | Dismember.RIGHT_LEG)) != 0;
	}

	/** Whether {@code player} may finish {@code body}: downed, not themselves, not someone's tamed animal, and not already being finished. */
	static boolean finishable(Player player, Entity entity) {
		return entity instanceof LivingEntity body && body != player && body.isAlive() && helpless(body) && !Carry.isCarried(body)
			&& !(body instanceof TamableAnimal tame && tame.isTame()) && !(body instanceof AbstractHorse horse && horse.isTamed())
			&& !targeted(body);
	}

	// ---- the hold ---------------------------------------------------------------------------------------------------------

	private static InteractionResult onUseEntity(Player player, net.minecraft.world.level.Level world, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (busy(player)) {
			// (still holding use through it: nothing else happens)
			return InteractionResult.SUCCESS;
		}
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() || Downed.isDowned(player)
			|| !finishable(player, entity) || Treatment.treatsOnPlainUse(player.getMainHandItem(), world)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getMainHandItem();
		boolean bare = stack.isEmpty();
		if (!bare && kind(stack) == Kind.FIST) {
			// (0.15.110) Andy: "make the execution only possible with bare hands": a torch or a loaf finishes nobody
			return InteractionResult.PASS;
		}
		long now = world.getGameTime();
		Hold held = HOLDING.get(player.getUUID());
		if (held != null && held.body == entity && held.bare == bare) {
			if (bare && now - held.last > PRESS_GAP) {
				// a fresh tap
				held.presses++;
				held.pressStart = now;
			}
			held.last = now;
		} else {
			held = new Hold((LivingEntity) entity, now, bare);
			HOLDING.put(player.getUUID(), held);
		}
		if (bare && held.presses >= TAPS && player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
			// the third tap: the neck snap
			HOLDING.remove(player.getUUID());
			begin(level, sp, held.body);
		}
		// (only watching: Kingdoms still takes a downed guard's weapons, and Downed's catch-all swallows the rest; (0.15.113)
		// a crippled one isn't downed, so its click is kept here, no trading screen between taps)
		return Downed.isDowned(entity) ? InteractionResult.PASS : InteractionResult.SUCCESS;
	}

	private static void holds(MinecraftServer server) {
		if (HOLDING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Hold hold = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level) || hold.body.level() != level || !player.isAlive()
				|| player.isShiftKeyDown() || !finishable(player, hold.body)) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (!player.isWithinEntityInteractionRange(hold.body, SLACK)) {
				it.remove();
				continue;
			}
			if (hold.bare) {
				// (0.15.110) bare hands: taps, and a press held down (or taps too slow) counts for nothing
				if (now - hold.last > TAP_WINDOW || hold.last - hold.pressStart > TAP_MAX) {
					it.remove();
				} else if (now - hold.pressStart <= 1) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.taps", hold.body.getDisplayName(), TAPS - hold.presses)
						.withStyle(ChatFormatting.DARK_RED));
				}
				continue;
			}
			if (now - hold.last > HOLD_GRACE) {
				// let go (a tap: whatever else a click on the downed does, it did)
				it.remove();
				continue;
			}
			long held = now - hold.start;
			if (held >= HOLD_TICKS && !weaponsUnlocked(player)) {
				// (0.15.110) a weapon's finisher is Combat Arts' Executioner
				it.remove();
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.locked").withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (held >= HOLD_TICKS) {
				it.remove();
				Kind kind = kind(player.getMainHandItem());
				if (!loaded(player, player.getMainHandItem(), kind)) {
					// (0.15.113) nothing to shoot
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.unloaded." + kind.name().toLowerCase(java.util.Locale.ROOT))
						.withStyle(ChatFormatting.GRAY));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
					continue;
				}
				begin(level, player, hold.body);
				continue;
			}
			if (held >= 2 && weaponsUnlocked(player)) {
				int filled = Math.round(HOLD_BAR * held / (float) HOLD_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.holding", hold.body.getDisplayName(),
					"|".repeat(filled) + ".".repeat(HOLD_BAR - filled)).withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	// ---- playing it out ---------------------------------------------------------------------------------------------------

	private static void begin(ServerLevel level, ServerPlayer player, LivingEntity body) {
		Kind kind = kind(player.getMainHandItem());
		player.setAttached(FINISHING, new Finish(kind.ordinal(), level.getGameTime(), body.getId()));
		PLAYING.put(player.getUUID(), new Playing(player, body, Downed.isDowned(body)));
		TARGETED.put(body.getUUID(), player.getUUID());
		player.stopUsingItem();
		player.setSprinting(false);
		root(player, true);
		if (body instanceof Player || !Downed.isDowned(body)) {
			// (0.15.113) a crippled one is held where it is too
			root(body, true);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher." + kind.name().toLowerCase(java.util.Locale.ROOT),
			body.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		if (kind == Kind.GUN && TaczGuns.gunSound(player, TaczGuns.gunId(player.getMainHandItem()), "fire_select", 16, 1.0F, 1.0F)) {
			// (0.15.113) the gun's own cock
			return;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), opening(kind), SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	private static void plays(MinecraftServer server) {
		if (PLAYING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Playing p = e.getValue();
			ServerPlayer player = p.player();
			LivingEntity body = p.body();
			Finish finish = of(player);
			boolean online = server.getPlayerList().getPlayer(e.getKey()) == player;
			if (finish == null || !online || !(player.level() instanceof ServerLevel level) || !player.isAlive() || Downed.isDowned(player)) {
				it.remove();
				end(player, body);
				continue;
			}
			Kind kind = finish.type();
			int t = (int) (level.getGameTime() - finish.start());
			// (0.15.113) a crippled one stays held till it's over; a downed one that gets up is spared
			boolean present = body.isAlive() && !body.isRemoved() && body.level() == level && (!p.downed() || Downed.isDowned(body)) && !Carry.isCarried(body)
				&& player.distanceTo(body) <= MAX_DISTANCE;
			if (t < kind.kill() && !present) {
				// (up, carried off or gone: spared)
				it.remove();
				end(player, body);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			// rooted where they stand, facing the body
			player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
			if (!p.downed() && body instanceof Mob mob) {
				// (0.15.113) a crippled mob, held: no crawling off, no fighting back
				mob.getNavigation().stop();
				mob.setTarget(null);
				mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
			}
			for (int beat : kind.beats) {
				if (t == beat && present) {
					blow(level, player, body, kind, beat == kind.kill());
				}
			}
			if (t >= kind.length) {
				it.remove();
				end(player, body);
			}
		}
	}

	/** One of the finisher's blows: the sound and the gore, the body jolting; the last one is their death. */
	private static void blow(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, boolean kill) {
		double x = body.getX(), y = body.getY() + 0.3, z = body.getZ();
		var random = level.getRandom();
		float pitch = 0.85F + random.nextFloat() * 0.3F;
		switch (kind.strike) {
			case CUT -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, pitch);
				BladeBlood.splash(body, kill ? 12.0F : 6.0F);
			}
			case STAB -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, pitch * 1.1F);
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F, pitch * 1.3F);
				BladeBlood.splash(body, kill ? 9.0F : 4.0F);
			}
			case PIERCE -> {
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch * 0.9F);
				BladeBlood.splash(body, kill ? 10.0F : 6.0F);
			}
			case CRUSH -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, kill ? 1.0F : 0.7F, pitch * 0.9F);
				level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, body.getBlockStateOn().isAir() ? Blocks.DIRT.defaultBlockState()
					: body.getBlockStateOn()), x, body.getY() + 0.05, z, kill ? 30 : 14, 0.5, 0.05, 0.5, 0.15);
				if (kill) {
					BladeBlood.crushed(body);
				} else {
					BladeBlood.blunt(body, 7.0F);
				}
			}
			case SHOT -> {
				spend(level, player, player.getMainHandItem(), kind);
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 12, 0.2, 0.2, 0.2, 0.3);
				BladeBlood.splash(body, 6.0F);
			}
			case BOLT -> {
				spend(level, player, player.getMainHandItem(), kind);
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.7F);
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 16, 0.2, 0.2, 0.2, 0.35);
				BladeBlood.splash(body, 7.0F);
			}
			case BLAST -> {
				ItemStack gun = player.getMainHandItem();
				spend(level, player, gun, kind);
				// the muzzle at arm's length toward their head, the ball's smoke into them
				Vec3 eye = player.getEyePosition();
				Vec3 head = new Vec3(x, y + 0.1, z);
				Vec3 dir = head.subtract(eye).normalize();
				Vec3 muzzle = eye.add(dir.scale(Math.min(1.0, eye.distanceTo(head) * 0.6)));
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0A0), muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.03);
				for (double d = 0.0, len = muzzle.distanceTo(head); d < len; d += 0.3) {
					Vec3 at = muzzle.add(dir.scale(d));
					level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.1, z, 14, 0.15, 0.15, 0.15, 0.3);
				String id = TaczGuns.gunId(gun);
				if (!TaczGuns.gunSound(player, id, "shoot_3p", SHOT_HEARD, 1.0F, 0.95F + random.nextFloat() * 0.1F)) {
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.7F);
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
				if (!TaczGuns.gunSound(body, id, "flesh_hit", 16, 1.0F, 0.9F)) {
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch * 0.8F);
				}
				BladeBlood.splash(body, 10.0F);
				Shakes.around(level, muzzle, AROUND_RANGE, SHOT_AROUND_SHAKE);
			}
			case SNAP -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.6F);
				} else {
					// (0.15.113) the hold tightens: they struggle, the neck strains
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.35F, 0.85F + random.nextFloat() * 0.1F);
					level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 0.7F);
				}
			}
		}
		// (the body jolts with it; no damage: the last blow is the death outright)
		level.broadcastDamageEvent(body, level.damageSources().playerAttack(player));
		Shakes.send(player, kill ? KILL_SHAKE : BEAT_SHAKE, body.position());
		if (body instanceof ServerPlayer victim) {
			Shakes.send(victim, kill ? KILL_SHAKE : BEAT_SHAKE, player.position());
		}
		if (!kill) {
			return;
		}
		if (kind.strike == Strike.CRUSH) {
			Shakes.around(level, body.position(), AROUND_RANGE, AROUND_SHAKE);
		}
		if (kind == Kind.AXE && Dismember.canLoseParts(body)) {
			// the head comes off first (Kitchen's corpse goes without it)
			Dismember.sever(body, Dismember.HEAD, player);
		}
		Downed.execute(level, body, player);
	}

	/** What's heard as it begins: steel drawn, a bowstring, a crossbow wound, cracking knuckles. */
	private static SoundEvent opening(Kind kind) {
		return switch (kind) {
			case BOW -> SoundEvents.ARROW_SHOOT;
			case CROSSBOW -> SoundEvents.CROSSBOW_LOADING_END.value();
			case FIST -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
			case BLUNT, STAFF -> SoundEvents.ARMOR_EQUIP_CHAIN.value();
			default -> SoundEvents.ARMOR_EQUIP_IRON.value();
		};
	}

	/** It's over (played out, or stopped): the marks off, the roots gone. */
	private static void end(ServerPlayer player, LivingEntity body) {
		player.removeAttached(FINISHING);
		root(player, false);
		TARGETED.remove(body.getUUID());
		// (unrooting one that never was is nothing)
		root(body, false);
	}

	private static void root(LivingEntity entity, boolean on) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(ROOT_ID);
			}
			if (jump != null) {
				jump.removeModifier(JUMP_ID);
			}
		}
	}
}
