package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rituals (fifth edition's slow casting): the small useful spells (ward a camp, find magic, identify, speak with animals) can
 * be cast with no mana at all, by standing still ten seconds with a book open. A blow, a step, a closed book or a gag breaks
 * it. What a spell spends (an ender pearl for identify) is still spent. This class also holds the three knowing spells:
 * <ul>
 * <li><b>Find magic:</b> what is magical within reach glimmers for the caster alone: enchanted things lying about or carried,
 * casters holding a spell, lasting fields, and the blocks of old magic.</li>
 * <li><b>Identify:</b> the thing in hand shows what it hides: every enchantment, its curse, an imbued element. Other systems
 * add to what is shown through {@link #onIdentify}.</li>
 * <li><b>Speak with animals:</b> for a while, the beasts near the caster say what is on their minds (a bubble only the caster
 * sees); sneak and use on one to hear it.</li>
 * </ul>
 */
public final class Rituals {
	/** How long a ritual takes (ticks, ten seconds), and how far a step may drift before it breaks. */
	public static final int TICKS = 200;
	static final double STILL = 0.35;
	/** Find magic: how far it looks (blocks), how long the glimmers last (ticks), and the most it shows. */
	static final double FIND_REACH = 24.0;
	static final int FIND_MOST = 60;
	/** Speak with animals: how long it lasts (ticks), how far, how many answer at once. */
	static final int SPEAK_TICKS = 20 * 60 * 10, SPEAK_REACH = 12, SPEAK_AT_ONCE = 4;
	static final int SIGIL = 0xD9C27A;

	/** What an identify found: the reader, the thing, and the lines about to be shown (a listener may add to them). */
	public record Reveal(ServerPlayer player, ItemStack stack, List<Component> lines) {
	}

	private static final List<Consumer<Reveal>> LISTENERS = new ArrayList<>();

	private record Going(String spell, Vec3 at, long begun, float health) {
	}

	private static final Map<UUID, Going> GOING = new HashMap<>();
	private static final Map<UUID, Long> SPEAKING = new HashMap<>();

	private Rituals() {
	}

	/** Lets another system add lines to an identify (an unknown property, a maker's mark). */
	public static void onIdentify(Consumer<Reveal> listener) {
		LISTENERS.add(listener);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Rituals::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GOING.clear();
			SPEAKING.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p && p.isShiftKeyDown() && entity instanceof Animal animal
				&& SPEAKING.getOrDefault(p.getUUID(), 0L) > level.getGameTime()) {
				hear(server, p, animal);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** The rituals {@code player} knows, in order. */
	public static List<String> known(ServerPlayer player) {
		List<String> known = new ArrayList<>();
		for (String id : NewSpells.IDS) {
			if (NewSpells.RITUALS.contains(id) && NewSpells.knows(player, id)) {
				known.add(id);
			}
		}
		return known;
	}

	/** Whether {@code player} has a book open: a tome or a plain book in either hand. */
	static boolean book(Player player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (Tomes.isTome(stack) || stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK)) {
				return true;
			}
		}
		return false;
	}

	/** Begins {@code spell} as a ritual for {@code player}. */
	static void begin(ServerPlayer player, String spell) {
		if (!(player.level() instanceof ServerLevel level) || GOING.containsKey(player.getUUID())) {
			return;
		}
		if (!book(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ritual.book"));
			return;
		}
		if (!SpellParts.allow(player, spell, null)) {
			return;
		}
		GOING.put(player.getUUID(), new Going(spell, player.position(), level.getGameTime(), player.getHealth()));
		Concentration.hold(player, "ritual", TICKS + 5, () -> {
		});
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), 1.6F, SIGIL, TICKS + 10);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** Whether {@code player} is in a ritual just now. */
	public static boolean going(Player player) {
		return GOING.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (GOING.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Going> e : List.copyOf(GOING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Going g = e.getValue();
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				GOING.remove(e.getKey());
				continue;
			}
			long gone = level.getGameTime() - g.begun;
			boolean broken = player.position().distanceToSqr(g.at) > STILL * STILL || player.getHealth() < g.health || !book(player)
				|| !SpellParts.canSpeak(player) || !SpellParts.handFree(player, null) || !"ritual".equals(Concentration.holding(player));
			if (broken) {
				GOING.remove(e.getKey());
				Concentration.drop(player);
				continue;
			}
			if (gone % 10 == 0) {
				double a = gone * 0.12;
				level.sendParticles(Fx.rune(SIGIL, 0.8F), player.getX() + Math.cos(a) * 1.2, player.getY() + 0.1 + gone * 0.008, player.getZ() + Math.sin(a) * 1.2,
					1, 0.0, 0.0, 0.0, 0.0);
			}
			if (gone >= TICKS) {
				GOING.remove(e.getKey());
				Concentration.hold(player, "ritual_done", 1, () -> {
				});
				finish(level, player, g.spell);
			}
		}
	}

	private static void finish(ServerLevel level, ServerPlayer player, String spell) {
		if (!SpellParts.allow(player, spell, null)) {
			return;
		}
		if (!NewSpells.cast(spell, level, player, 1.0F)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		SpellParts.spend(player, spell);
		Fx.blast(level, player.position().add(0.0, 0.2, 0.0), 2.2F, SIGIL);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.1F);
		Spellcraft.afterCast(player, level, spell);
	}

	/** The three knowing spells and the camp's ward line when it is cast the quick way. */
	static boolean cast(String spell, ServerLevel level, Player player, float power) {
		return switch (spell) {
			case "find_magic" -> findMagic(level, player);
			case "identify" -> player instanceof ServerPlayer sp && identify(level, sp);
			case "speak_animals" -> player instanceof ServerPlayer sp && speak(level, sp);
			default -> false;
		};
	}

	// ---- find magic -----------------------------------------------------------------------------------------------------------

	private static boolean magical(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemEnchantments enchants = stack.getEnchantments();
		return !enchants.isEmpty() || stack.is(Items.ENCHANTED_BOOK) || Spellcraft.charge(stack) != null || ItemCurses.cursed(stack)
			|| stack.getItem() instanceof Arcana.Scroll || Arcana.isStaff(stack) || stack.getItem() instanceof ManaWand;
	}

	private static boolean magical(Entity e) {
		if (e instanceof ItemEntity item) {
			return magical(item.getItem());
		}
		if (e instanceof LivingEntity living) {
			if (Concentration.holding(living) != null || Thralls.isThrall(living)) {
				return true;
			}
			if (living instanceof Player p && Mana.attuned(p)) {
				return true;
			}
			for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
				if (magical(living.getItemBySlot(slot))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean findMagic(ServerLevel level, Player player) {
		if (!(player instanceof ServerPlayer viewer)) {
			return false;
		}
		int shown = 0;
		Vec3 eye = player.getEyePosition();
		for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(FIND_REACH), x -> x != player && x.isAlive() && magical(x))) {
			if (shown++ >= FIND_MOST) {
				break;
			}
			glimmer(level, viewer, e.getBoundingBox().getCenter());
		}
		for (Fields.Field f : Fields.all()) {
			if (f.level == level && shown < FIND_MOST && f.at.distanceTo(eye) <= FIND_REACH + f.radius) {
				glimmer(level, viewer, f.at.add(0.0, Math.max(0.3, f.lift), 0.0));
				shown++;
			}
		}
		int cx = BlockPos.containing(eye).getX() >> 4, cz = BlockPos.containing(eye).getZ() >> 4;
		int span = (int) Math.ceil(FIND_REACH / 16.0);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
					Vec3 c = Vec3.atCenterOf(be.getBlockPos());
					if (shown < FIND_MOST && c.distanceTo(eye) <= FIND_REACH && oldMagic(be)) {
						glimmer(level, viewer, c);
						shown++;
					}
				}
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.ring(level, player.position().add(0.0, 0.2, 0.0), (float) FIND_REACH * 0.5F, SIGIL);
		return true;
	}

	private static boolean oldMagic(BlockEntity be) {
		if (be instanceof net.minecraft.world.level.block.entity.EnchantingTableBlockEntity || be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity || be instanceof net.minecraft.world.level.block.entity.TheEndPortalBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
			return true;
		}
		if (be instanceof BaseContainerBlockEntity container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (magical(container.getItem(i))) {
					return true;
				}
			}
		}
		return false;
	}

	private static void glimmer(ServerLevel level, ServerPlayer viewer, Vec3 at) {
		level.sendParticles(viewer, Fx.rune(SIGIL, 1.2F), true, true, at.x, at.y, at.z, 3, 0.2, 0.3, 0.2, 0.0);
		level.sendParticles(viewer, Fx.GLYPH, true, true, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.3);
	}

	// ---- identify ---------------------------------------------------------------------------------------------------------------

	private static boolean identify(ServerLevel level, ServerPlayer player) {
		// the thing in hand: the main hand's, unless that is the book being read from
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty() || stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || Tomes.isTome(stack)) {
			stack = player.getOffhandItem();
		}
		if (stack.isEmpty()) {
			return false;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
		ItemEnchantments enchants = stack.getEnchantments();
		if (enchants.isEmpty()) {
			lines.add(Component.translatable("magic.jaymoskingdoms.identify.plain").withStyle(ChatFormatting.GRAY));
		}
		for (var entry : enchants.entrySet()) {
			boolean curse = entry.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE);
			lines.add(Component.empty().append(net.minecraft.world.item.enchantment.Enchantment.getFullname(entry.getKey(), entry.getIntValue()))
				.withStyle(curse ? ChatFormatting.RED : ChatFormatting.AQUA));
		}
		String tag = ItemCurses.tag(stack);
		if (tag != null) {
			lines.add(Component.translatable("magic.jaymoskingdoms.identify.curse", tag).withStyle(ChatFormatting.DARK_RED));
		}
		Spellcraft.Charge charge = Spellcraft.charge(stack);
		if (charge != null) {
			lines.add(Component.translatable("magic.jaymoskingdoms.identify.imbued", Component.translatable("class.jaymoskingdoms.option." + charge.element()))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (stack.getItem() instanceof Arcana.Scroll scroll) {
			lines.add(Component.translatable("magic.jaymoskingdoms.identify.scroll", Spellcraft.spellName(scroll.spell)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Reveal reveal = new Reveal(player, stack, lines);
		for (Consumer<Reveal> listener : LISTENERS) {
			listener.accept(reveal);
		}
		MagicMenu.show(player, Component.translatable("magic.jaymoskingdoms.identify.title"), reveal.lines());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.sendParticles(Fx.GLYPH, player.getX(), player.getY(1.1), player.getZ(), 14, 0.3, 0.3, 0.3, 0.4);
		return true;
	}

	// ---- speak with animals -------------------------------------------------------------------------------------------------------

	private static boolean speak(ServerLevel level, ServerPlayer player) {
		SPEAKING.put(player.getUUID(), level.getGameTime() + SPEAK_TICKS);
		int n = 0;
		for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(SPEAK_REACH), Entity::isAlive)) {
			if (n++ >= SPEAK_AT_ONCE) {
				break;
			}
			hear(level, player, animal);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PARROT_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	private static String kind(Animal animal) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
		return switch (path) {
			case "cow", "mooshroom" -> "cow";
			case "pig" -> "pig";
			case "sheep" -> "sheep";
			case "chicken" -> "chicken";
			case "horse", "donkey", "mule", "skeleton_horse" -> "horse";
			case "wolf" -> "wolf";
			case "cat", "ocelot" -> "cat";
			case "fox" -> "fox";
			case "rabbit" -> "rabbit";
			default -> "other";
		};
	}

	/** {@code animal} says something to {@code player}, and only them: how it feels first, else what its kind thinks. */
	static void hear(ServerLevel level, ServerPlayer player, Animal animal) {
		String key;
		if (animal.getHealth() < animal.getMaxHealth() * 0.5F) {
			key = "hurt." + level.getRandom().nextInt(2);
		} else if (animal.isBaby()) {
			key = "baby." + level.getRandom().nextInt(2);
		} else {
			key = kind(animal) + "." + level.getRandom().nextInt(3);
		}
		Speech.privately(animal, player, Component.translatable("speech.jaymoskingdoms.animal." + key), Speech.GOLD);
		level.sendParticles(Fx.GLYPH, animal.getX(), animal.getY(1.2), animal.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
	}
}
