package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.492) Knowledge: the boss ladder Andy kept from Gemini's DawnCraft handoff ("tying combat directly into progression";
 * DawnCraft's idea only, our own bosses, code and words). Five levels, each earned by one great foe slain, in order
 * ({@link #level}: a level counts only once every boss below it has fallen too). Each opens a piece of the craft:
 * <ol>
 * <li><b>The Goblin King</b>: anvils (vanilla's and the iron Smithing Anvil; the Stone Anvil is open from the start) and
 * bog iron ({@link BogIron}).</li>
 * <li><b>The Ogre King</b>: the smithing table, and the heavy arms on the Smithing Anvil ({@link #HEAVY_ARMS}).</li>
 * <li><b>The Skeleton Lord</b>: the enchanting table.</li>
 * <li><b>The Piglin King</b>: the higher spells and techniques: a staff caster learning a spell from a scroll
 * ({@link Spellcraft#learn}; reading one once still works for anyone), the Storm Arts, and skill-tree perks that want
 * pillar level {@link #TECHNIQUE_LEVEL} or more.</li>
 * <li><b>The Wither</b>: the End portal's frames take eyes.</li>
 * </ol>
 * The world tiers carry it ({@link WorldTiers#FEAT_NEEDED}): Borderlands wants level 1, Wildlands 2, Dread 4, Legend 5. The
 * kills are {@link WorldTiers.Progress} feats, so they are shared like the rest (everyone near when the boss falls, and the
 * party). The Rung Journal ticks each. A refusal is a line on the action bar and a dull sound; a new level is a chime and a
 * line on the action bar, and a line in the next Tidings. Never chat. Creative players are never stopped.
 * <p>
 * Players already on a tier when this came in keep what that tier implies ({@link #grandfather}), once.
 */
public final class Knowledge {
	// ---- tuning ----
	/** The highest level. */
	public static final int MAX = 5;
	/** Skill-tree perks that want this pillar level or more are "higher techniques" (level 4). */
	public static final int TECHNIQUE_LEVEL = 15;
	/** The Smithing Anvil forgings (shape ids, without the metal) that are heavy arms (level 2). */
	static final Set<String> HEAVY_ARMS = Set.of("weapon/claymore_blade", "weapon/war_scythe_blade", "weapon/glaive_blade",
		"weapon/halberd_head", "part/broad_blade");
	/** Per level 1 to 5, the level it opens (for the gates below). */
	public static final int ANVILS = 1, SMITHING = 2, ENCHANTING = 3, HIGHER_ARTS = 4, END_PORTAL = 5;
	/** Per world tier (Hearth to Legend), the knowledge a player already on it is granted once ({@link #grandfather}). */
	static final int[] GRANDFATHER = {0, 1, 2, 4, 5};

	/** Per level 1 to 5, its feat bit in {@link WorldTiers.Progress}. */
	static final int[] BIT = {0, WorldTiers.Progress.KNOW_1, WorldTiers.Progress.KNOW_2, WorldTiers.Progress.KNOW_3,
		WorldTiers.Progress.KNOW_4, WorldTiers.Progress.KNOW_5};
	/** Every knowledge bit. */
	static final int ALL = WorldTiers.Progress.KNOW_1 | WorldTiers.Progress.KNOW_2 | WorldTiers.Progress.KNOW_3
		| WorldTiers.Progress.KNOW_4 | WorldTiers.Progress.KNOW_5;

	/** Whether a player has been given what their tier already implied. Saved, kept through death. */
	static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(JaymosKingdoms.id("knowledge_checked"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private Knowledge() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("knowledge attachment: {}", CHECKED);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			int need = needed(level.getBlockState(hit.getBlockPos()));
			if (need == 0 || player.isCreative() || player.isSpectator() || level(player) >= need) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				refuse(sp, need);
			} else if (level.isClientSide()) {
				// the client stops the click before it's sent, so the refusal is shown here
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.knowledge.locked." + need));
				player.playSound(SoundEvents.VILLAGER_NO, 0.5F, 0.7F);
			}
			return InteractionResult.FAIL;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> grandfather(handler.player));
		heavyArms();
	}

	/** The knowledge {@code state} wants (0: none). */
	static int needed(BlockState state) {
		if (state.is(BlockTags.ANVIL)) {
			return ANVILS;
		}
		if (state.is(Blocks.SMITHING_TABLE)) {
			return SMITHING;
		}
		if (state.is(Blocks.ENCHANTING_TABLE)) {
			return ENCHANTING;
		}
		if (state.is(Blocks.END_PORTAL_FRAME)) {
			return END_PORTAL;
		}
		var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		// Jaymo's Tinkering's iron Smithing Anvil (the Stone Anvil stays open)
		return id.getNamespace().equals("jaymospowers") && id.getPath().equals("smithing_anvil") ? ANVILS : 0;
	}

	/** {@code player}'s level, 0 to {@link #MAX}: the bosses slain in order, from the first. */
	public static int level(Player player) {
		return level(WorldTiers.progress(player).feats());
	}

	static int level(int feats) {
		int n = 0;
		while (n < MAX && (feats & BIT[n + 1]) != 0) {
			n++;
		}
		return n;
	}

	/** Whether {@code player} may use what level {@code need} opens. */
	public static boolean has(Player player, int need) {
		return player.isCreative() || level(player) >= need;
	}

	/** Whether {@code perk} is a higher technique {@code player} doesn't have the knowledge for yet. */
	public static boolean lockedPerk(Player player, Skills.Perk perk) {
		return perk.minLevel >= TECHNIQUE_LEVEL && !has(player, HIGHER_ARTS);
	}

	/** The knowledge bit a great foe's fall earns (0: none). */
	static int bitFor(Entity entity) {
		if (entity instanceof Folk folk && folk.role() == Goblins.KING) {
			return BIT[1];
		}
		if (OgreHolds.king(entity)) {
			return BIT[2];
		}
		if (entity instanceof SkeletonLord) {
			return BIT[3];
		}
		if (entity instanceof com.jaymoskingdoms.beasts.PiglinKing) {
			return BIT[4];
		}
		return entity.getType() == EntityTypes.WITHER ? BIT[5] : 0;
	}

	/** (From {@link WorldTiers}' saving) {@code player}'s level went from {@code before} to {@code after}. */
	static void risen(ServerPlayer player, int before, int after) {
		if (after <= before) {
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.knowledge.risen." + after));
		for (int lv = before + 1; lv <= after; lv++) {
			Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.knowledge." + lv));
		}
	}

	/** A refusal: a line on the action bar, a dull sound. */
	public static void refuse(ServerPlayer player, int need) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.knowledge.locked." + need));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 0.7F);
	}

	/**
	 * Once per player: someone already on a tier when knowledge came in is given the levels that tier now wants
	 * ({@link #GRANDFATHER}), so no one loses a tier or a bench they had. A new player is only marked.
	 */
	static void grandfather(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(CHECKED))) {
			return;
		}
		player.setAttached(CHECKED, true);
		WorldTiers.Progress p = WorldTiers.progress(player);
		// what they earned or were given, not what the world's floor lifted them to (that gives no knowledge)
		int floor = WorldTiers.floor(player.level().getServer());
		int tier = new WorldTiers.Progress(p.foes(), p.feats(), p.unlocked() > floor ? p.unlocked() : 0).openIgnoring(ALL);
		int grant = GRANDFATHER[Math.clamp(tier, 0, GRANDFATHER.length - 1)];
		int bits = 0;
		for (int lv = 1; lv <= grant; lv++) {
			bits |= BIT[lv];
		}
		if (bits != 0 && (p.feats() & bits) != bits) {
			WorldTiers.grant(player, bits);
		}
	}

	// ---- heavy arms on Jaymo's Tinkering's Smithing Anvil (reached by reflection: Kingdoms runs without Powers) ----

	private static void heavyArms() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> forging = Class.forName("com.jaymospowers.tinkering.smithing.Forging");
			Class<?> check = Class.forName("com.jaymospowers.tinkering.smithing.Forging$Check");
			Class<?> option = Class.forName("com.jaymospowers.tinkering.smithing.Forging$Option");
			Method id = option.getMethod("id");
			Object proxy = Proxy.newProxyInstance(Knowledge.class.getClassLoader(), new Class<?>[] {check}, (self, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "hashCode" -> System.identityHashCode(self);
						case "equals" -> self == args[0];
						default -> "Knowledge.heavyArms";
					};
				}
				// refuse(ServerPlayer smith, SmithingAnvilEntity anvil, Option option)
				ServerPlayer smith = (ServerPlayer)args[0];
				String shape = shape((String)id.invoke(args[2]));
				return HEAVY_ARMS.contains(shape) && !has(smith, SMITHING)
					? Component.translatable("message.jaymoskingdoms.knowledge.locked.heavy") : null;
			});
			@SuppressWarnings("unchecked")
			List<Object> checks = (List<Object>)forging.getField("CHECKS").get(null);
			checks.add(proxy);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("Knowledge: couldn't reach Jaymo's Tinkering's forging checks; heavy arms stay open", e);
		}
	}

	/** An option id's shape: its id without the metal ("weapon/claymore_blade/iron" or "iron/weapon/..." to "weapon/claymore_blade"). */
	static String shape(String optionId) {
		for (String s : HEAVY_ARMS) {
			if (optionId.equals(s) || optionId.startsWith(s + "/") || optionId.endsWith("/" + s) || optionId.contains("/" + s + "/")) {
				return s;
			}
		}
		return optionId;
	}
}
