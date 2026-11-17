package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Oaths and pacts that bind (Andy, 2026-11-17: "the players are UNABLE to do things outside their parameters without
 * specifically breaking the path or pact"; the terms after 5e's tenets and patron obligations, agreed the same day).
 * A Paladin whose oath holds, and every Warlock, simply can't do what their vow forbids: the act is refused and the
 * action bar names the term. Doing it again within {@link #AGAIN_TICKS}, sneaking (or, for a choice in a menu, choosing
 * it again), breaks the vow on purpose and the act goes through: the Paladin's oath breaks ({@link Paladins#breakOath},
 * mended as before at a shrine or a priest), the Warlock's patron collects at once ({@link Warlocks#collect}) and lets
 * that term be for {@link #LAPSE_TICKS}.
 * <ul>
 * <li><b>Oaths:</b> Mercy, no killing blow on the helpless. Justice, no theft, pickpocketing, lockpicking, vandalism,
 * arson or assault on a settlement's people. Honour, no unprovoked blow, no theft or pickpocketing.</li>
 * <li><b>Pacts:</b> the Hearthkeeper, no blow on the downed or on anyone indoors in a settlement who isn't fighting you,
 * no fire lit in a settlement. The Anvil-Father, no trident and no turtle shell (the sea rusts what he makes). The Deep
 * Mother, no pick on ore, no harm to dolphins or turtles. The Green Huntsman, no young beast killed. The Lantern
 * Warden, no death or blood magic and no feeding on blood. The Crowned Sun, no theft, pickpocketing or lockpicking, no
 * unprovoked blow on a guard or a ruler. The Ash Whisperer, no yielding to the guards, no fine paid, no bounty turned in,
 * and no good choice in a tale (mercy to a yielded foe; a reply that goes against gain or vengeance). The Reaper sets no
 * terms yet.</li>
 * <li><b>The Truth:</b> everyone sworn to it (and its Warlocks) is a pacifist: no killing blow on anything living but the
 * undead (Andy, 2026-11-17: "thou shall not kill"). One who kills on purpose falls: the Truth lets them go, and their
 * class becomes the Death Sorcerer's ({@link Classes#replace}).</li>
 * </ul>
 * Crimes a Justice oath can't stop before the act (trespass, poaching, dealing...) still break it when done, as before
 * ({@link Paladins#crime}).
 */
public final class Vows {
	/** How long after a refusal the same act, done again (sneaking), breaks the vow (ticks); how soon it may come (so one held key isn't two tries). */
	static final int AGAIN_TICKS = 200, SETTLE_TICKS = 20;
	/** How long a Warlock's patron lets a broken term be, once collected (ticks). */
	static final int LAPSE_TICKS = 200;
	/** How recently a creature must have hurt you for your blow on it to be no unprovoked one (ticks). */
	static final int PROVOKED_TICKS = 200;
	/** How often worn gear is checked (ticks). */
	static final int GEAR_EVERY = 10;

	/** What each oath forbids (its terms). */
	static final Map<String, Set<String>> OATHS = Map.of(
		"mercy", Set.of("helpless"),
		"justice", Set.of("theft", "pickpocket", "lockpick", "vandal", "arson", "assault"),
		"honour", Set.of("unprovoked", "theft", "pickpocket"));
	/** What each patron's pact forbids. */
	static final Map<String, Set<String>> PACTS = Map.of(
		"hearthkeeper", Set.of("downed", "indoors", "arson"),
		"anvil_father", Set.of("trident", "sea_gear"),
		"deep_mother", Set.of("ore", "sea_beast"),
		"green_huntsman", Set.of("young_beast"),
		"lantern_warden", Set.of("dark_arts", "feeding"),
		"crowned_sun", Set.of("theft", "pickpocket", "lockpick", "guard"),
		"ash_whisperer", Set.of("yield", "fine", "bounty", "good"));
	/** What the Truth forbids everyone sworn to it, and its Warlocks. */
	static final Set<String> TRUTH = Set.of("kill");

	private static final Map<String, Long> REFUSED = new HashMap<>();
	private static final Map<String, Long> LAPSED = new HashMap<>();

	private Vows() {
	}

	/** Registered ahead of {@link Theft}, so a refused chest or fire never reaches its record of what was opened. */
	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !(source.getEntity() instanceof ServerPlayer player)
			|| victim == player || struck(player, victim, source, amount));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !(player instanceof ServerPlayer sp)
			|| !(level instanceof ServerLevel server) || breaking(sp, server, pos, state, entity));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (Theft.lightsFire(sp, hand) && Theft.arson(sp, server, pos.relative(hit.getDirection())) && !allows(sp, "arson")) {
				return InteractionResult.FAIL;
			}
			if (!(sp.isSecondaryUseActive() && !sp.getItemInHand(hand).isEmpty()) && Theft.rummages(sp, server, pos) && !allows(sp, "theft")) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof ServerPlayer sp && sp.getItemInHand(hand).is(Items.TRIDENT)
			&& !allows(sp, "trident") ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(Vows::tick);
	}

	static void clear() {
		REFUSED.clear();
		LAPSED.clear();
	}

	// ---- the vow ------------------------------------------------------------------------------------------------------

	/** What binds: a Paladin's oath, a Warlock's pact, or being sworn to the Truth. */
	private enum Kind {
		OATH, PACT, TRUTH
	}

	/** The oath ("mercy"...) or the patron's key a term binds {@code player} by, and how; null if it doesn't bind them. */
	private record Vow(String what, Kind kind) {
	}

	private static @Nullable Vow vow(ServerPlayer player, String term) {
		boolean warlock = Classes.is(player, PlayerClass.WARLOCK);
		String patron = warlock ? Classes.optionOf(player, PlayerClass.WARLOCK) : "";
		if (TRUTH.contains(term) && (Patrons.of(player) == Patron.TRUTH || patron.equals(Patron.TRUTH.key))) {
			return new Vow(Patron.TRUTH.key, Kind.TRUTH);
		}
		if (Paladins.keeping(player) && OATHS.getOrDefault(Paladins.oath(player), Set.of()).contains(term)) {
			return new Vow(Paladins.oath(player), Kind.OATH);
		}
		if (warlock && PACTS.getOrDefault(patron, Set.of()).contains(term)) {
			return new Vow(patron, Kind.PACT);
		}
		return null;
	}

	/** Whether {@code term} binds {@code player} at all (for a choice that only offers itself to the free). */
	static boolean binds(ServerPlayer player, String term) {
		return vow(player, term) != null;
	}

	/**
	 * Whether {@code player} may do what {@code term} names: true if no vow of theirs forbids it, or if this is the second
	 * try, sneaking, that breaks it on purpose. False (and the action bar says why) if it's refused.
	 */
	static boolean allows(ServerPlayer player, String term) {
		return allows(player, term, true);
	}

	/** {@link #allows}, where a second try needn't be made sneaking ({@code sneak} false: a choice made in a menu). */
	static boolean allows(ServerPlayer player, String term, boolean sneak) {
		Vow vow = vow(player, term);
		if (vow == null || player.isCreative() || player.isSpectator()) {
			return true;
		}
		long now = player.level().getGameTime();
		String key = player.getUUID() + "/" + term;
		Long lapse = LAPSED.get(key);
		if (lapse != null && now < lapse) {
			return true;
		}
		Long first = REFUSED.get(key);
		if (first != null && now - first >= SETTLE_TICKS && now - first <= AGAIN_TICKS && (!sneak || player.isShiftKeyDown())) {
			REFUSED.remove(key);
			switch (vow.kind()) {
				case OATH -> Paladins.breakOath(player, why(vow.what(), term));
				case PACT -> {
					Warlocks.collect(player);
					LAPSED.put(key, now + LAPSE_TICKS);
				}
				case TRUTH -> fall(player);
			}
			return true;
		}
		if (first == null || now - first > AGAIN_TICKS) {
			REFUSED.put(key, now);
		}
		Component whose = vow.kind() == Kind.OATH ? Component.translatable("option.jaymoskingdoms.oath." + vow.what()) : patronName(vow.what());
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow.refused." + vow.kind().name().toLowerCase(java.util.Locale.ROOT)
			+ (sneak ? "" : ".choose"), whose, Component.translatable("message.jaymoskingdoms.vow.term." + term)).withStyle(ChatFormatting.GOLD));
		return false;
	}

	/** One sworn to the Truth killed on purpose: it lets them go, and death takes its place (a Death Sorcerer now). */
	private static void fall(ServerPlayer player) {
		Patrons.forsake(player);
		Classes.replace(player, PlayerClass.SORCERER, "death");
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.vow.truth_fallen").withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** The reason {@link Paladins#breakOath} gives for an oath broken over {@code term}. */
	private static String why(String oath, String term) {
		return switch (term) {
			case "helpless", "unprovoked" -> term;
			default -> oath.equals("justice") ? "crime" : "theft";
		};
	}

	private static Component patronName(String key) {
		Patron patron = Patron.byKey(key);
		return patron != null ? patron.named() : Component.literal(key);
	}

	// ---- blows --------------------------------------------------------------------------------------------------------

	/** A blow of {@code player}'s on {@code victim}: whether it's let land. */
	private static boolean struck(ServerPlayer player, LivingEntity victim, DamageSource source, float amount) {
		ItemStack weapon = source.getWeaponItem();
		if ((source.getDirectEntity() instanceof ThrownTrident || weapon != null && weapon.is(Items.TRIDENT)) && !allows(player, "trident")) {
			return false;
		}
		if (amount >= victim.getHealth() && Paladins.helpless(player, victim) && !allows(player, "helpless")) {
			return false;
		}
		if (amount >= victim.getHealth() && (victim instanceof Mob || victim instanceof net.minecraft.world.entity.player.Player)
			&& !victim.getType().is(EntityTypeTags.UNDEAD) && !allows(player, "kill")) {
			return false;
		}
		boolean provoked = provoked(player, victim);
		if (!provoked && Paladins.unprovoked(player, victim) && !allows(player, "unprovoked")) {
			return false;
		}
		if (!provoked && assault(player, victim) && !allows(player, "assault")) {
			return false;
		}
		if (Downed.isDowned(victim) && !allows(player, "downed")) {
			return false;
		}
		if (!provoked && !(victim instanceof Enemy) && indoors(victim) && !allows(player, "indoors")) {
			return false;
		}
		if ((victim instanceof Dolphin || victim instanceof Turtle) && !allows(player, "sea_beast")) {
			return false;
		}
		if (victim instanceof Animal animal && animal.isBaby() && !allows(player, "young_beast")) {
			return false;
		}
		return provoked || !(Downed.isGuard(victim) || Rulers.is(victim)) || allows(player, "guard");
	}

	/** Whether {@code victim} came at {@code player} first: it's after them, or hurt them a moment ago. */
	static boolean provoked(ServerPlayer player, LivingEntity victim) {
		return victim instanceof Mob mob && mob.getTarget() == player
			|| player.getLastHurtByMob() == victim && player.tickCount - player.getLastHurtByMobTimestamp() < PROVOKED_TICKS;
	}

	/** A blow on one of a settlement's people, where its law holds, that isn't a spar, a duel, a war or a crook's due. */
	private static boolean assault(ServerPlayer player, LivingEntity victim) {
		if (!(victim instanceof Villager || victim instanceof Folk) || !(victim.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction f = Theft.lawAt(level, victim.blockPosition());
		return f != null && !Wars.enemy(player, f) && !Crooks.fairGame(victim, player) && !ValorFolk.sparring(player, victim)
			&& !com.jaymoskingdoms.forms.GuardDrills.sparring(player, victim) && !com.jaymoskingdoms.forms.Duels.dueling(player, victim);
	}

	/** Under a roof in a settlement. */
	private static boolean indoors(LivingEntity victim) {
		if (!(victim.level() instanceof ServerLevel level) || Theft.lawAt(level, victim.blockPosition()) == null) {
			return false;
		}
		BlockPos at = victim.blockPosition();
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) > at.getY() + 1;
	}

	// ---- blocks -------------------------------------------------------------------------------------------------------

	/** {@code player} is breaking the block at {@code pos}: whether they may. */
	private static boolean breaking(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
		if (state.is(ConventionalBlockTags.ORES) && player.getMainHandItem().is(ItemTags.PICKAXES) && !allows(player, "ore")) {
			return false;
		}
		Theft.Crime crime = Theft.blockCrime(player, level, pos, state, entity);
		if (crime == null) {
			return true;
		}
		boolean theft = crime == Theft.Crime.STOREHOUSE || crime == Theft.Crime.CHEST || crime == Theft.Crime.BURGLARY;
		return allows(player, theft ? "theft" : "vandal");
	}

	// ---- worn gear ----------------------------------------------------------------------------------------------------

	/** A turtle shell on the head of one the Anvil-Father forbids it: taken off and put away (worn again, sneaking, it breaks the pact). */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % GEAR_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
			if (!head.is(Items.TURTLE_HELMET) || !binds(player, "sea_gear") || allows(player, "sea_gear")) {
				continue;
			}
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(head, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
