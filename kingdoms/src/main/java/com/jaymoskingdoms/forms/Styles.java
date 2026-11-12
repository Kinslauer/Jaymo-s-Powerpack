package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Schools.School;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The fighting styles (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its seven lightsaber forms are stat
 * trades). Ours are seven too: one a school, named for the schools, and two any fighter can learn, the Iron Gate (a
 * soldier's drill) and the Long Road (the sellsword's way). A style trades the feathers a landed swing costs, the feathers
 * a blocked blow costs, the speed of the swings and how fast feathers come back ({@link Style}; Powers applies them,
 * {@link PowersCompat#bladeStyle}).
 * <ul>
 * <li><b>Learnt, never given</b> (Andy, 2026-11-12: "you should have to train every one of the styles or study
 * technique books that are findable around"). A school's master teaches its style once your standing there reaches
 * {@link #STYLE_STANDING} ({@code Schools.talk}). The Iron Gate and the Long Road are drilled at a Training Post or on a
 * dummy ({@link #DRILL_IRON_GATE}, {@link #DRILL_LONG_ROAD} blows). Any of the seven can also be learnt from its technique
 * book ({@link #book}), found now and then in chests ({@link #BOOK_CHANCE}): open it to study it.</li>
 * <li><b>Set, not switched on by itself</b>: you fight in the style you chose ({@link #CHOSEN}), and only with a weapon it
 * fits. Sneak and use a Training Post with the weapon in hand to go through the styles you know that fit it, then plain.
 * A master who teaches you a style sets you in it.</li>
 * <li>The style you are in shows over the belt in battle stance ({@link #ACTIVE}, synced to you).</li>
 * <li>A school's guards and duellists fight in it too: its rhythm ({@link #tempo}), how often they turn a blow in a
 * duel ({@link #turns}), and whether they tire.</li>
 * </ul>
 */
public final class Styles {
	/** How often each player's style is looked at again (ticks). */
	static final int CHECK_EVERY = 10;

	/**
	 * A school's style. For a player: feathers a landed swing costs, what a blocked blow costs (times the usual),
	 * attack speed (times the weapon's), feather regrowth (times the usual). For its guards and duellists: the wait
	 * between blows (times the usual), the chance they turn a duel blow aside, and after how many blows they tire (0:
	 * never) and how much slower they are then.
	 */
	public enum Style {
		/** The Iron Gate: a soldier's drill, any weapon. Even, sure and slow to tire. */
		IRON_GATE(null, 0.7F, 0.75F, 0.95F, 1.15F, 1.05F, 0.3F, 0, 1.0F),
		/** The Long Road: the sellsword's way, any weapon. Quick to swing, quick to recover, a middling guard. */
		LONG_ROAD(null, 0.6F, 1.1F, 1.05F, 1.05F, 0.95F, 0.2F, 0, 1.0F),
		/** The Quiet Tide: the warding style. It turns blows cheaply all day and pays for its own. */
		STILL_WATER(School.BLADE, 0.75F, 0.6F, 0.95F, 1.0F, 1.15F, 0.45F, 0, 1.0F),
		/** The Skyward Reach: the fencer's style. Cheap thrusts at full measure, a poor guard. */
		LONG_MEASURE(School.SPEAR, 0.4F, 1.4F, 1.0F, 1.1F, 1.0F, 0.25F, 0, 1.0F),
		/** The Stone Root: slow, sure, and never out of breath. */
		DEEP_ROOT(School.FIST, 0.5F, 0.8F, 0.9F, 1.3F, 1.2F, 0.3F, 0, 1.0F),
		/** The Low Lantern: quick hands and nothing behind them to block with. */
		LOW_FLAME(School.KNIFE, 0.5F, 1.5F, 1.15F, 1.0F, 0.85F, 0.15F, 14, 1.3F),
		/** The Ashen Fang: the wild style. The fastest, the dearest to block with, and it burns out. */
		RED_EMBER(School.CLAW, 1.0F, 1.8F, 1.25F, 0.85F, 0.7F, 0.05F, 8, 1.8F);

		/** The school whose style it is, or null for the two anyone can learn. */
		public final @Nullable School school;
		final float swing, block, speed, regen, tempo, turn, tired;
		final int tireAfter;

		Style(@Nullable School school, float swing, float block, float speed, float regen, float tempo, float turn, int tireAfter, float tired) {
			this.school = school;
			this.swing = swing;
			this.block = block;
			this.speed = speed;
			this.regen = regen;
			this.tempo = tempo;
			this.turn = turn;
			this.tireAfter = tireAfter;
			this.tired = tired;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("style.jaymoskingdoms." + key());
		}

		/** Whether this style can be fought in with {@code main}: its school's weapon, or for the open two any weapon but a bare hand. */
		public boolean fits(net.minecraft.world.item.ItemStack main) {
			if (this.school != null) {
				return this.school.family().holds(main);
			}
			return !main.isEmpty() && !Forms.Family.FIST.holds(main) && (main.has(net.minecraft.core.component.DataComponents.WEAPON)
				|| main.has(net.minecraft.core.component.DataComponents.TOOL) || Forms.Family.BLADE.holds(main) || Forms.Family.SPEAR.holds(main)
				|| Forms.Family.KNIFE.holds(main) || Forms.Family.CLAW.holds(main));
		}

		public static Style of(School school) {
			for (Style style : values()) {
				if (style.school == school) {
					return style;
				}
			}
			return STILL_WATER;
		}

		public static @Nullable Style byKey(@Nullable String key) {
			for (Style style : values()) {
				if (style.key().equals(key)) {
					return style;
				}
			}
			return null;
		}
	}

	/** A school's standing at which its master teaches its style. */
	public static final int STYLE_STANDING = 60;
	/** Drilled blows at a Training Post or a dummy that teach the Iron Gate, and then the Long Road. */
	static final int DRILL_IRON_GATE = 300, DRILL_LONG_ROAD = 700;
	/** The least wait between two drilled blows that count (ticks): a full swing, not a flurry of clicks. */
	static final int DRILL_GAP = 10;
	/** The chance a chest of a kind that might hold one ({@link #BOOK_CHESTS}) holds a technique book. */
	static final float BOOK_CHANCE = 0.07F;
	static final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> BOOK_CHESTS = java.util.Set.of(
		net.minecraft.world.level.storage.loot.BuiltInLootTables.VILLAGE_WEAPONSMITH, net.minecraft.world.level.storage.loot.BuiltInLootTables.VILLAGE_ARMORER,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.STRONGHOLD_LIBRARY, net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.PILLAGER_OUTPOST, net.minecraft.world.level.storage.loot.BuiltInLootTables.WOODLAND_MANSION,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.BASTION_OTHER, net.minecraft.world.level.storage.loot.BuiltInLootTables.DESERT_PYRAMID,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.JUNGLE_TEMPLE, net.minecraft.world.level.storage.loot.BuiltInLootTables.SHIPWRECK_MAP);
	/** A technique book's lore id: this, then the style's key. */
	static final String BOOK_PREFIX = "style:";

	/** On a player who has put the styles aside (before styles were learnt: no longer read). Saved, kept through death. */
	public static final AttachmentType<Boolean> PLAIN = AttachmentRegistry.create(JaymosKingdoms.id("style_plain"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** On a player: the styles they have learnt (keys). Saved, kept through death. */
	public static final AttachmentType<java.util.List<String>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("styles_learned"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** On a player: the style they chose to fight in (absent: plain). Saved, kept through death. */
	public static final AttachmentType<String> CHOSEN = AttachmentRegistry.create(JaymosKingdoms.id("style_chosen"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/** On a player: blows drilled toward the open styles. Saved, kept through death. */
	public static final AttachmentType<Integer> DRILLED = AttachmentRegistry.create(JaymosKingdoms.id("style_drilled"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** When each player's last drilled blow counted (game time). Not saved. */
	private static final Map<UUID, Long> LAST_DRILL = new HashMap<>();
	/** On a player: the key of the style they are fighting in now, for their own HUD. Not saved. */
	public static final AttachmentType<String> ACTIVE = AttachmentRegistry.create(JaymosKingdoms.id("style_active"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

	/** The style each player was last found in (absent: none). */
	private static final Map<UUID, Style> LAST = new HashMap<>();

	private Styles() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Styles::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST.remove(handler.player.getUUID());
			LAST_DRILL.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			LAST_DRILL.clear();
		});
		// sneak and use a Training Post with a weapon in hand: the next style you know that fits it, then plain
		// (an empty hand there opens the belt, Forms)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && sp.isShiftKeyDown() && !sp.getMainHandItem().isEmpty()
				&& !sp.isSpectator() && !learned(sp).isEmpty()
				&& BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).toString().equals(Forms.TRAINING_POST)) {
				Style next = next(sp);
				choose(sp, next);
				sp.sendOverlayMessage(next == null ? Component.translatable("message.jaymoskingdoms.style.plain").withStyle(ChatFormatting.GRAY)
					: Component.translatable("message.jaymoskingdoms.style.set", next.displayName()).withStyle(ChatFormatting.GOLD));
				sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5F, next == null ? 1.0F : 1.5F);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		// drilling: blows on a Training Post or a dummy with a weapon in hand
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player instanceof ServerPlayer sp && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(Forms.TRAINING_POST)) {
				drilled(sp);
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof net.minecraft.world.entity.decoration.Mannequin && Forms.dummy(entity)) {
				drilled(sp);
			}
			return InteractionResult.PASS;
		});
		// studying a technique book: opening it is enough
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp) {
				String id = com.jaymoskingdoms.LoreBooks.idOf(sp.getItemInHand(hand));
				Style style = id.startsWith(BOOK_PREFIX) ? Style.byKey(id.substring(BOOK_PREFIX.length())) : null;
				if (style != null && learn(sp, style)) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.style.studied", style.displayName()).withStyle(ChatFormatting.GOLD));
				}
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(BOOK_CHESTS::contains).isPresent() && context.getRandom().nextFloat() < BOOK_CHANCE) {
				Style[] all = Style.values();
				drops.add(book(all[context.getRandom().nextInt(all.length)]));
			}
		});
	}

	// ---- learning and choosing --------------------------------------------------------------------------------------------

	/** The styles {@code player} has learnt. */
	public static java.util.List<Style> learned(Player player) {
		java.util.List<Style> out = new java.util.ArrayList<>();
		for (String key : player.getAttachedOrElse(LEARNED, java.util.List.of())) {
			Style style = Style.byKey(key);
			if (style != null) {
				out.add(style);
			}
		}
		return out;
	}

	public static boolean knows(Player player, Style style) {
		return player.getAttachedOrElse(LEARNED, java.util.List.of()).contains(style.key());
	}

	/** {@code player} learns {@code style}; true if they didn't know it. */
	public static boolean learn(ServerPlayer player, Style style) {
		if (knows(player, style)) {
			return false;
		}
		java.util.List<String> more = new java.util.ArrayList<>(player.getAttachedOrElse(LEARNED, java.util.List.of()));
		more.add(style.key());
		player.setAttached(LEARNED, more);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		return true;
	}

	/** {@code player} fights in {@code style} from now on (null: plain). */
	public static void choose(ServerPlayer player, @Nullable Style style) {
		if (style == null) {
			player.removeAttached(CHOSEN);
		} else {
			player.setAttached(CHOSEN, style.key());
		}
	}

	/** After the style chosen now, the next one {@code player} knows that fits the weapon in hand; null for plain. */
	private static @Nullable Style next(ServerPlayer player) {
		java.util.List<Style> fit = new java.util.ArrayList<>();
		for (Style style : learned(player)) {
			if (style.fits(player.getMainHandItem())) {
				fit.add(style);
			}
		}
		Style now = Style.byKey(player.getAttached(CHOSEN));
		int at = fit.indexOf(now);
		return at + 1 < fit.size() ? fit.get(at + 1) : null;
	}

	/** A blow drilled at a post or on a dummy: toward the Iron Gate, then the Long Road. */
	private static void drilled(ServerPlayer player) {
		if (player.getMainHandItem().isEmpty() || player.isSpectator() || !Style.IRON_GATE.fits(player.getMainHandItem())) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_DRILL.get(player.getUUID());
		if (last != null && now - last < DRILL_GAP) {
			return;
		}
		LAST_DRILL.put(player.getUUID(), now);
		int drilled = player.getAttachedOrElse(DRILLED, 0) + 1;
		player.setAttached(DRILLED, drilled);
		for (Style style : new Style[] {Style.IRON_GATE, Style.LONG_ROAD}) {
			if (drilled >= (style == Style.IRON_GATE ? DRILL_IRON_GATE : DRILL_LONG_ROAD) && learn(player, style)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.style.drilled", style.displayName()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** {@code style}'s technique book: a written book of its stances and its trade, tagged so it teaches when opened. */
	public static net.minecraft.world.item.ItemStack book(Style style) {
		net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		java.util.List<net.minecraft.server.network.Filterable<Component>> pages = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) {
			pages.add(net.minecraft.server.network.Filterable.passThrough(Component.translatable("book.jaymoskingdoms.style." + style.key() + "." + i)));
		}
		String title = Component.translatable("book.jaymoskingdoms.style.title", style.displayName()).getString();
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
			net.minecraft.server.network.Filterable.passThrough(title), Component.translatable("book.jaymoskingdoms.style.author").getString(), 0, pages, true));
		com.jaymoskingdoms.LoreBooks.tag(book, BOOK_PREFIX + style.key());
		return book;
	}

	/** The style {@code player} fights in now, or null: none chosen, not learnt, or the weapon in hand doesn't fit it. */
	public static @Nullable Style of(Player player) {
		Style chosen = Style.byKey(player.getAttached(CHOSEN));
		return chosen != null && knows(player, chosen) && chosen.fits(player.getMainHandItem()) ? chosen : null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Style style = player.isAlive() && !player.isSpectator() ? of(player) : null;
			if (LAST.get(player.getUUID()) == style && (style == null) == (player.getAttached(ACTIVE) == null)) {
				continue;
			}
			if (style == null) {
				LAST.remove(player.getUUID());
				player.removeAttached(ACTIVE);
				PowersCompat.clearBladeStyle(player);
			} else {
				LAST.put(player.getUUID(), style);
				player.setAttached(ACTIVE, style.key());
				PowersCompat.bladeStyle(player, style.swing, style.block, style.speed, style.regen);
			}
		}
	}

	// ---- the schools' own fighters ----------------------------------------------------------------------------------------

	/** The wait between a {@code school} fighter's blows, times the usual, after {@code blows} of them (a wild style tires). */
	public static float tempo(@Nullable School school, int blows) {
		if (school == null) {
			return 1.0F;
		}
		Style style = Style.of(school);
		return style.tempo * (style.tireAfter > 0 && blows >= style.tireAfter ? style.tired : 1.0F);
	}

	/**
	 * A duel blow of {@code attacker}'s on a {@code school} duellist: true when they turned it aside in their style (the
	 * sparks and the ring are done here, and none of it lands). A tired fighter turns nothing.
	 */
	static boolean turns(ServerLevel level, School school, LivingEntity duellist, LivingEntity attacker, int blows) {
		Style style = Style.of(school);
		if (style.tireAfter > 0 && blows >= style.tireAfter || level.getRandom().nextFloat() >= style.turn) {
			return false;
		}
		duellist.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		PowersCompat.guardMet(level, duellist, duellist.getMainHandItem(), attacker, 8, 0.9F, 0);
		level.playSound(null, duellist.getX(), duellist.getY(), duellist.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.7F, 1.4F);
		return true;
	}

	/** The school whose weapon family is {@code family}. */
	static @Nullable School schoolOf(Forms.Family family) {
		for (School school : School.values()) {
			if (school.family() == family) {
				return school;
			}
		}
		return null;
	}
}
