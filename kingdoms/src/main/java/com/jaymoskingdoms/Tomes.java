package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.346) The elemental spell tomes, a new kind of weapon. Andy: "make the starter tome a new type of weapon.
 * Casting quick bolts with left click that can be charged. There should be different tomes that each have a primary
 * element, all attuned can use them and you can load a spell page with shift right click. It stays with that book. It can
 * be cast with right click on a varying cooldown dependent on the skill's power."
 * <ul>
 * <li><b>One tome per element</b> of the Sorcerers ({@link #ELEMENTS}); death's and blood's are forbidden like their
 * scrolls ({@link ForbiddenArts}). Only the attuned work one ({@link Mana}); for anyone else it fizzles with a sound.</li>
 * <li><b>Left click</b> (main hand): a quick bolt of the tome's element. Hold it to charge (a bar on the action bar, which
 * Jaymo's Powers' held-input lock reads as a hold) and let go for a stronger one. Vanilla sends nothing for a click on air,
 * so the client sends the press and the release ({@link Attack}) and the server times the hold. The bolt is flown on the
 * server ({@link Bolt}): a trail of the element's particles, hitting the first creature or block in its way with the element's
 * own burst ({@link Spellcraft#strike}); Restoration's heals who it hits.</li>
 * <li><b>Sneak + right click</b>: loads the spell scroll in your off hand (or, with that hand empty, the first in your
 * pack) as the tome's page; the scroll is spent, the spell stays in the book ({@link #PAGE}) and a new page replaces it.
 * A page of the tome's own element casts {@link #OWN_PAGE} times as hard.</li>
 * <li><b>Right click</b>: casts the page ({@link Arcana#cast}; it isn't used up) for mana, then the tome waits as long as
 * the spell's tier says ({@link #TIER}, {@link #COOLDOWN}, {@link #MANA}).</li>
 * <li><b>Crafting</b> is by infusion ({@code data/jaymoskingdoms/alchemy/infusion/<element>_tome.json}), behind Jaymo's
 * Powers' Tomecraft research. <b>Glyphs</b> (the enchanting table's runes with the element's own particle) stream off the
 * pages in amounts that vary by tome ({@link #GLYPHS}) and by action; a bolt's trail mixes the element's particles with the
 * mana glow. The book opens, and turns its pages, on the client ({@code TomeClient}).</li>
 * </ul>
 */
public final class Tomes {
	/** The Sorcerer's elements, one tome each. */
	public static final List<String> ELEMENTS = List.of("water", "fire", "earth", "air", "lightning", "ice", "restoration", "death", "blood");

	// ---- tuning: the spell page ----
	/** How hard a page of the tome's own element casts. */
	static final float OWN_PAGE = 1.25F;
	/** A tome's spell power by who holds it: a Mage, a Sorcerer, and any other attuned (as a scroll); then a share of their {@link Mana#power}. */
	static final float MAGE_STRENGTH = Spellcraft.MAGE_POWER, SORCERER_STRENGTH = 1.5F, ATTUNED_STRENGTH = Spellcraft.ATTUNED_POWER;
	static final float POWER_BASE = 0.8F, POWER_PER_POINT = 0.04F;
	/** Anyone attuned who isn't a Mage or a Sorcerer waits this much longer between spells. */
	static final float ATTUNED_WAIT = Spellcraft.ATTUNED_COOLDOWN;
	/** Each spell's tier (1 quick .. 5 very long); anything not listed is tier 2. */
	static final Map<String, Integer> TIER = Map.ofEntries(
		Map.entry("fire", 1), Map.entry("arcane", 1), Map.entry("whispers", 1), Map.entry("blood_grip", 1),
		Map.entry("warding", 2), Map.entry("draining", 2), Map.entry("mending", 2),
		Map.entry("raising", 3), Map.entry("sanctuary", 3), Map.entry("blight", 3), Map.entry("hemorrhage", 3),
		Map.entry("frenzy", 4),
		Map.entry("restoration", 5), Map.entry("deaths_door", 5));
	/** The wait after a spell (ticks) and its mana, by tier (index 0 is tier 1). Blood's spells are paid in blood instead ({@link Hemomancy#cost}). */
	static final int[] COOLDOWN = {20 * 4, 20 * 12, 20 * 30, 20 * 50, 20 * 75};
	static final float[] MANA = {8.0F, 15.0F, 30.0F, 45.0F, 60.0F};
	/** The element each spell belongs to (for {@link #OWN_PAGE}). */
	static final Map<String, String> SPELL_ELEMENT = Map.ofEntries(
		Map.entry("fire", "fire"), Map.entry("arcane", "lightning"), Map.entry("warding", "earth"),
		Map.entry("draining", "death"), Map.entry("raising", "death"), Map.entry("blight", "death"), Map.entry("whispers", "death"),
		Map.entry("deaths_door", "death"), Map.entry("mending", "restoration"), Map.entry("sanctuary", "restoration"),
		Map.entry("restoration", "restoration"), Map.entry("hemorrhage", "blood"), Map.entry("blood_grip", "blood"), Map.entry("frenzy", "blood"));

	// ---- tuning: the bolt ----
	/** A click let go in this many ticks or fewer is a quick bolt; the charge runs from there to {@link #FULL_TICKS}. */
	static final int QUICK_TICKS = 5, FULL_TICKS = 30;
	/** The bar's length (Jaymo's Powers' lock reads 8 or more), shown from this many ticks of holding. */
	static final int BAR_LENGTH = 12, BAR_FROM = 2;
	/** Damage (or healing, for restoration) of a quick bolt and a full charge, before who casts it. */
	static final float QUICK_DAMAGE = 3.0F, FULL_DAMAGE = 9.0F, QUICK_HEAL = 3.0F, FULL_HEAL = 8.0F;
	/** Mana of a quick bolt and a full charge. */
	static final float QUICK_MANA = 3.0F, FULL_MANA = 10.0F;
	/** The wait after a quick bolt and after a full charge (ticks). */
	static final int QUICK_GAP = 8, FULL_GAP = 22;
	/** How fast it flies (blocks per tick), how long (ticks, quick and full) and how wide it hits. */
	static final double SPEED = 1.7, HIT_WIDTH = 0.3;
	static final int QUICK_LIFE = 14, FULL_LIFE = 22;
	/** A hold nobody lets go of is dropped after this (ticks). */
	static final int MAX_HOLD = 1200;
	/** The mana glow behind a bolt: motes per sample point along its path (quick, charged) and a cluster at its head, beside the element's own particles. */
	static final int TRAIL_MANA = 1, TRAIL_MANA_CHARGED = 2, HEAD_MANA = 2;
	/** How far apart the trail's samples lie (blocks) and how loosely the mana motes drift from them. */
	static final double TRAIL_STEP = 0.35, MANA_DRIFT = 0.07;

	// ---- tuning: the glyphs off the pages (the enchanting table's runes, with the element's own particle beside them) ----
	/** Each tome's own base glyph count (a calm tome sheds few, lightning's many); every action scales it. */
	static final Map<String, Integer> GLYPHS = Map.ofEntries(
		Map.entry("water", 4), Map.entry("fire", 6), Map.entry("earth", 3), Map.entry("air", 5), Map.entry("lightning", 7),
		Map.entry("ice", 3), Map.entry("restoration", 5), Map.entry("death", 6), Map.entry("blood", 4));
	static final int DEFAULT_GLYPHS = 4;
	/** While charging, every this many ticks: base * (CHARGE_MIN + charge * CHARGE_GROWTH) glyphs. */
	static final int CHARGE_EVERY = 2;
	static final float CHARGE_MIN = 0.4F, CHARGE_GROWTH = 2.4F;
	/** A bolt loosed: base * (SHOT_MIN + charge * SHOT_GROWTH). */
	static final float SHOT_MIN = 1.5F, SHOT_GROWTH = 3.5F;
	/** A page stored: base * this, all at once. */
	static final float STORE_BURST = 6.0F;
	/** A page cast: base * (CAST_MIN + spell tier * CAST_PER_TIER). */
	static final float CAST_MIN = 1.0F, CAST_PER_TIER = 1.2F;
	/** The element's own particle, as a share of the glyph count (never fewer than one). */
	static final float OWN_SHARE = 0.4F;
	/** Rare loot: a tome from a slain caster, and one in the rarest chests. */
	static final float CASTER_DROP = 0.06F, CHEST_LOOT = 0.04F;

	public static final Map<String, Item> TOMES = new HashMap<>();
	/** The spell loaded into a tome (its name, as {@link Arcana#SPELLS}). */
	public static DataComponentType<String> PAGE;

	/** Who holds the left button on a tome and since when; when each may next loose a bolt (game time). */
	private static final Map<UUID, Long> HOLDS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();

	/** Client to server: the left button on a tome. {@code action}: 1 pressed, 0 let go (fire), 2 dropped (item changed, screen opened). */
	public record Attack(int action) implements CustomPacketPayload {
		public static final Type<Attack> TYPE = new Type<>(JaymosKingdoms.id("tome_attack"));
		public static final StreamCodec<ByteBuf, Attack> CODEC = ByteBufCodecs.VAR_INT.map(Attack::new, Attack::action);

		@Override
		public Type<Attack> type() {
			return TYPE;
		}
	}

	private Tomes() {
	}

	static void register() {
		PAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("tome_page"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		for (String element : ELEMENTS) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(element + "_tome"));
			TOMES.put(element, Registry.register(BuiltInRegistries.ITEM, key, new Tome(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE), element)));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Arcana.HEALER_STAFF;
			for (String element : ELEMENTS) {
				output.insertAfter(after, TOMES.get(element));
				after = TOMES.get(element);
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(Attack.TYPE, Attack.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Attack.TYPE, (payload, context) -> onAttack(context.player(), payload.action()));
		ServerTickEvents.END_SERVER_TICK.register(Tomes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDS.clear();
			READY.clear();
			BOLTS.clear();
		});
		// a caster's tome, now and then: a necromancer's is death's, a blood witch's blood's, a shaman's fire's, a wizard's any of the rest
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Worker caster && Sorcery.isCaster(caster.role()) && source.getEntity() instanceof ServerPlayer
				&& entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < CASTER_DROP) {
				entity.spawnAtLocation(level, new ItemStack(TOMES.get(elementFor(caster.role(), level.getRandom()))));
			}
		});
		// and one in the rarest chests
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(k -> k.equals(BuiltInLootTables.STRONGHOLD_LIBRARY) || k.equals(BuiltInLootTables.ANCIENT_CITY)
				|| k.equals(BuiltInLootTables.WOODLAND_MANSION)).isPresent() && context.getRandom().nextFloat() < CHEST_LOOT) {
				List<String> open = List.of("water", "fire", "earth", "air", "lightning", "ice", "restoration");
				drops.add(new ItemStack(TOMES.get(open.get(context.getRandom().nextInt(open.size())))));
			}
		});
	}

	private static String elementFor(int role, RandomSource random) {
		if (role == Sorcery.NECROMANCER) {
			return "death";
		}
		if (role == Sorcery.BLOOD_WITCH) {
			return "blood";
		}
		if (role == Sorcery.SHAMAN || role == Orcs.SHAMAN) {
			return "fire";
		}
		List<String> wizard = List.of("water", "earth", "air", "lightning", "ice", "restoration");
		return wizard.get(random.nextInt(wizard.size()));
	}

	/** Whether {@code stack} is one of the tomes. */
	public static boolean isTome(ItemStack stack) {
		return stack.getItem() instanceof Tome;
	}

	/** The loaded page of {@code stack}, or null. */
	public static @Nullable String page(ItemStack stack) {
		String page = stack.get(PAGE);
		return page == null || page.isEmpty() ? null : page;
	}

	// ---- the glyphs off the pages -------------------------------------------------------------------------------------

	/** Where the pages are: the book in the hand. */
	private static Vec3 book(ServerPlayer player) {
		return player.getEyePosition().add(player.getLookAngle().scale(0.7)).add(0.0, -0.3, 0.0);
	}

	/** {@code tome}'s base glyph count. */
	private static int glyphs(String element) {
		return GLYPHS.getOrDefault(element, DEFAULT_GLYPHS);
	}

	/** Sheds {@code count} glyphs (the enchanting table's runes) around {@code at}, and the element's own particle among them. */
	private static void shed(ServerLevel level, Vec3 at, String element, float count) {
		int n = Math.max(1, Math.round(count));
		level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, n, 0.25, 0.25, 0.25, 0.5);
		level.sendParticles(Spellcraft.particle(element), at.x, at.y, at.z, Math.max(1, Math.round(n * OWN_SHARE)), 0.12, 0.12, 0.12, 0.02);
	}

	// ---- who can use it, and how hard ----------------------------------------------------------------------------------

	private static boolean usable(Player player) {
		return Mana.attuned(player) || player.isCreative();
	}

	/** How hard {@code player}'s magic hits through a tome. */
	static float strength(Player player) {
		float base = Classes.is(player, Classes.PlayerClass.MAGE) ? MAGE_STRENGTH : Classes.is(player, Classes.PlayerClass.SORCERER) ? SORCERER_STRENGTH : ATTUNED_STRENGTH;
		return base * (POWER_BASE + POWER_PER_POINT * Math.max(1, Mana.power(player)));
	}

	private static void fizzle(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.7F);
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
	}

	// ---- the item: right click -----------------------------------------------------------------------------------------

	/** An elemental tome. */
	public static final class Tome extends Item {
		final String element;

		Tome(Item.Properties properties, String element) {
			super(properties);
			this.element = element;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return usable(player) && (player.isShiftKeyDown() || page(stack) != null) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!usable(sp)) {
				fizzle(sp);
				return InteractionResult.FAIL;
			}
			if (sp.isShiftKeyDown()) {
				return load(server, sp, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			return castPage(server, sp, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
			String page = page(stack);
			if (page != null) {
				lines.accept(Spellcraft.spellName(page).copy().withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** Loads a page: the scroll in the off hand, else the first in the pack. */
	private static boolean load(ServerLevel level, ServerPlayer player, ItemStack tome) {
		ItemStack scroll = player.getOffhandItem();
		if (scroll.isEmpty()) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize() && !(scroll.getItem() instanceof Arcana.Scroll); i++) {
				scroll = inv.getItem(i);
			}
		}
		if (!(scroll.getItem() instanceof Arcana.Scroll page)) {
			return false;
		}
		tome.set(PAGE, page.spell);
		scroll.consume(1, player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		String element = ((Tome) tome.getItem()).element;
		shed(level, book(player), element, glyphs(element) * STORE_BURST);
		player.sendOverlayMessage(Spellcraft.spellName(page.spell));
		return true;
	}

	/** Casts the loaded page. */
	private static boolean castPage(ServerLevel level, ServerPlayer player, ItemStack tome) {
		String spell = page(tome);
		if (spell == null) {
			// a blank book: a soft fizzle
			fizzle(player);
			return false;
		}
		if (player.getCooldowns().isOnCooldown(tome)) {
			return false;
		}
		int tier = Math.clamp(TIER.getOrDefault(spell, 2), 1, COOLDOWN.length);
		// blood's spells are paid in blood, the rest in mana
		boolean blood = ForbiddenArts.blood(spell);
		float cost = blood ? Hemomancy.cost(spell) : MANA[tier - 1] * DivineBorn.spellCost(player, spell);
		if (blood) {
			if (!player.isCreative() && player.getHealth() - cost < Hemomancy.MIN_LEFT) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.too_little").withStyle(ChatFormatting.DARK_RED));
				return false;
			}
		} else if (!Mana.afford(player, cost)) {
			return false;
		}
		boolean own = ((Tome) tome.getItem()).element.equals(SPELL_ELEMENT.get(spell));
		if (!Arcana.cast(spell, level, player, strength(player) * (own ? OWN_PAGE : 1.0F) * (blood ? Hemomancy.power(player) : 1.0F))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return false;
		}
		if (blood) {
			Hemomancy.pay(player, cost);
		} else {
			Mana.take(player, cost);
			Mana.ripple(player, Mana.intent(spell));
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		String element = ((Tome) tome.getItem()).element;
		shed(level, book(player), element, glyphs(element) * (CAST_MIN + tier * CAST_PER_TIER));
		boolean caster = Mana.natural(player);
		player.getCooldowns().addCooldown(tome, Math.round(COOLDOWN[tier - 1] * (caster ? 1.0F : ATTUNED_WAIT)));
		Spellcraft.afterCast(player, level, spell);
		return true;
	}

	// ---- left click: the bolt ------------------------------------------------------------------------------------------

	private static void onAttack(ServerPlayer player, int action) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (action == 1) {
			if (!(player.getMainHandItem().getItem() instanceof Tome) || READY.getOrDefault(player.getUUID(), 0L) > now) {
				return;
			}
			if (!usable(player)) {
				READY.put(player.getUUID(), now + QUICK_GAP);
				fizzle(player);
				return;
			}
			if (Will.hushed(player)) {
				READY.put(player.getUUID(), now + QUICK_GAP);
				fizzle(player);
				return;
			}
			if (!Mana.canPay(player, QUICK_MANA)) {
				// the low-mana word on the screen, once
				READY.put(player.getUUID(), now + QUICK_GAP * 2);
				Mana.afford(player, QUICK_MANA);
				return;
			}
			HOLDS.put(player.getUUID(), now);
			return;
		}
		Long since = HOLDS.remove(player.getUUID());
		if (since == null) {
			return;
		}
		player.sendOverlayMessage(Component.empty());
		if (action == 0 && player.getMainHandItem().getItem() instanceof Tome tome) {
			release(level, player, tome.element, (int) Math.min(now - since, MAX_HOLD));
		}
	}

	/** {@code held} ticks of charge, as a share of the full charge (0 for a tap). */
	public static float charge(int held) {
		return held <= QUICK_TICKS ? 0.0F : Math.min(1.0F, (held - QUICK_TICKS) / (float) (FULL_TICKS - QUICK_TICKS));
	}

	private static void release(ServerLevel level, ServerPlayer player, String element, int held) {
		float f = charge(held);
		float cost = QUICK_MANA + f * (FULL_MANA - QUICK_MANA);
		if (f > 0.0F && !Mana.canPay(player, cost)) {
			f = 0.0F;
			cost = QUICK_MANA;
		}
		if (!Mana.pay(player, cost, element.equals("restoration") ? Mana.HEAL : Mana.HARM)) {
			return;
		}
		READY.put(player.getUUID(), level.getGameTime() + Math.round(QUICK_GAP + f * (FULL_GAP - QUICK_GAP)));
		Vec3 look = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.25, 0.0);
		BOLTS.add(new Bolt(level, player.getUUID(), element, start, look.scale(SPEED), f, strength(player), Math.round(QUICK_LIFE + f * (FULL_LIFE - QUICK_LIFE))));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		shed(level, book(player), element, glyphs(element) * (SHOT_MIN + f * SHOT_GROWTH));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Spellcraft.sound(element), SoundSource.PLAYERS, 0.8F, 1.5F - 0.4F * f);
		Spellcraft.afterCast(player, level, element);
	}

	private static void tick(MinecraftServer server) {
		// the holds: the bar, and a drop if the book is gone
		for (UUID id : new ArrayList<>(HOLDS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			long held = player == null ? 0 : player.level().getGameTime() - HOLDS.get(id);
			if (player == null || !player.isAlive() || !(player.getMainHandItem().getItem() instanceof Tome tome) || held > MAX_HOLD) {
				HOLDS.remove(id);
				continue;
			}
			if (held >= BAR_FROM) {
				float f = charge((int) held);
				int filled = Math.round(f * BAR_LENGTH);
				player.sendOverlayMessage(Component.literal("|".repeat(filled) + ".".repeat(BAR_LENGTH - filled)).withStyle(ChatFormatting.AQUA));
				if (held % CHARGE_EVERY == 0 && player.level() instanceof ServerLevel level) {
					shed(level, book(player), tome.element, glyphs(tome.element) * (CHARGE_MIN + f * CHARGE_GROWTH));
				}
				if (held == FULL_TICKS) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
				}
			}
		}
		for (int i = BOLTS.size() - 1; i >= 0; i--) {
			if (!BOLTS.get(i).step(server)) {
				BOLTS.remove(i);
			}
		}
	}

	/** A bolt in flight, flown by the server: a trail of the element's particles mixed with the mana glow, stopped by the first creature or block. */
	private static final class Bolt {
		final ServerLevel level;
		final UUID owner;
		final String element;
		final float charge, strength;
		Vec3 pos, vel;
		int life;

		Bolt(ServerLevel level, UUID owner, String element, Vec3 pos, Vec3 vel, float charge, float strength, int life) {
			this.level = level;
			this.owner = owner;
			this.element = element;
			this.pos = pos;
			this.vel = vel;
			this.charge = charge;
			this.strength = strength;
			this.life = life;
		}

		boolean aims(ServerPlayer shooter, LivingEntity e) {
			if (e == shooter || !e.isAlive() || e.isSpectator() || !e.isPickable()) {
				return false;
			}
			// a healing bolt is for the people it can help (and the undead it sears)
			return !element.equals("restoration") || Restoration.undead(e) || Restoration.friend(shooter, e) && !Downed.isDowned(e);
		}

		/** Flies one tick; false once it has hit or run out. */
		boolean step(MinecraftServer server) {
			ServerPlayer shooter = server.getPlayerList().getPlayer(owner);
			if (shooter == null || shooter.level() != level || life-- <= 0) {
				return false;
			}
			Vec3 from = pos, to = from.add(vel);
			BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			boolean blocked = wall.getType() != HitResult.Type.MISS;
			Vec3 end = blocked ? wall.getLocation() : to;
			LivingEntity target = null;
			double nearest = Double.MAX_VALUE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0), e -> aims(shooter, e))) {
				AABB box = e.getBoundingBox().inflate(HIT_WIDTH);
				Optional<Vec3> at = box.contains(from) ? Optional.of(from) : box.clip(from, end);
				if (at.isPresent() && from.distanceToSqr(at.get()) < nearest) {
					nearest = from.distanceToSqr(at.get());
					target = e;
				}
			}
			ParticleOptions trail = Spellcraft.particle(element);
			Vec3 stop = target != null ? from.add(vel.normalize().scale(Math.sqrt(nearest))) : end;
			int n = Math.max(1, (int) (from.distanceTo(stop) / TRAIL_STEP));
			for (int i = 0; i <= n; i++) {
				Vec3 p = from.lerp(stop, i / (double) n);
				level.sendParticles(trail, p.x, p.y, p.z, 1 + (charge > 0.5F ? 1 : 0), 0.04, 0.04, 0.04, 0.0);
				// the mana glow, left loosely behind where the bolt has been
				level.sendParticles(Mana.MOTE, p.x, p.y, p.z, charge > 0.5F ? TRAIL_MANA_CHARGED : TRAIL_MANA, MANA_DRIFT, MANA_DRIFT, MANA_DRIFT, 0.0);
			}
			level.sendParticles(Mana.MOTE, stop.x, stop.y, stop.z, HEAD_MANA, 0.1, 0.1, 0.1, 0.01);
			if (target != null) {
				hit(shooter, target);
				return false;
			}
			if (blocked) {
				level.sendParticles(trail, stop.x, stop.y, stop.z, 6 + (int) (charge * 10), 0.2, 0.2, 0.2, 0.05);
				level.playSound(null, stop.x, stop.y, stop.z, Spellcraft.sound(element), SoundSource.PLAYERS, 0.4F, 1.8F);
				return false;
			}
			pos = end;
			return true;
		}

		private void hit(ServerPlayer shooter, LivingEntity target) {
			float scale = 1.0F;
			if (element.equals("restoration")) {
				if (Restoration.undead(target)) {
					target.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), (QUICK_DAMAGE + charge * (FULL_DAMAGE - QUICK_DAMAGE)) * strength * 2.0F);
					target.igniteForSeconds(2.0F);
					level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
				} else {
					PowersCompat.mend(target, (QUICK_HEAL + charge * (FULL_HEAL - QUICK_HEAL)) * strength * DivineBorn.heal(shooter));
					PowersCompat.closeCut(target);
					level.sendParticles(Restoration.GOLD_MOTE, target.getX(), target.getY(0.6), target.getZ(), 16, 0.35, 0.5, 0.35, 0.02);
					level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.3F);
					Restoration.credit(level, shooter, target, false);
				}
				return;
			}
			float damage = (QUICK_DAMAGE + charge * (FULL_DAMAGE - QUICK_DAMAGE)) * strength * scale;
			target.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), damage);
			Spellcraft.strike(level, shooter, target, element, true);
		}
	}
}
