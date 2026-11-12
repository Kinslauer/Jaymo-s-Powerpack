package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.MadeBrute;
import com.jaymoskingdoms.monsters.Monsters;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A forbidden art of blood magic ({@link ForbiddenArts}): a Blood Sorcerer ({@link Hemomancy#bloodMage}) makes a great foe of
 * a captive. It is learned from a stitched page ({@link #PAGE}), which a camp's blood witch may leave on her body
 * ({@link #PAGE_DROP}) and the rarest chests may hold. The rite is the Blood Sorcerer's sneak-and-{@code J} (the bolt key)
 * with a bound captive in their sights: a villager, an illager or a witch, chained ({@link Captives#bound}) or wrapped
 * on a rope ({@link com.jaymoskingdoms.forms.Lasso#wrapped}); never a player. No block is needed: the rite draws its
 * own circle on the ground under the captive and works for {@link #RITE_TICKS} ticks.
 * <ul>
 * <li><b>The cost:</b> {@link #BLOOD_COST} blood (the reservoir first, then the body), {@link #REDSTONE} redstone and
 * {@link #BONES} bones, taken as it begins and lost if it is broken; the Higher Arts ({@link Knowledge#HIGHER_ARTS}) first.</li>
 * <li><b>The channel</b> breaks if the maker is struck, moves more than {@link #HOLD_RANGE} blocks off, falls or is downed,
 * or the captive is struck, freed, loosed from the rope or killed. Anyone near can end it that way.</li>
 * <li><b>The result:</b> the captive is gone and a {@link MadeBrute} stands in their place, with their name kept hidden
 * and the maker remembered. One living brute per maker.</li>
 * <li><b>The crime:</b> blood magic where a realm sees it ({@link ForbiddenArts#cast}, {@link #CORRUPTION} corruption
 * more), and the heavier {@link Theft#unmaking}: more reputation lost than any other crime, the villager's own settlement
 * holds it against the maker, and the villager's kin remember it ({@link Recollections#murdered}).</li>
 * </ul>
 */
public final class BruteRite {
	// ---- tuning ----
	/** How long the rite works (ticks); how far from the captive the maker may be (blocks). */
	static final int RITE_TICKS = 20 * 9;
	static final double HOLD_RANGE = 6.0, REACH = 6.0;
	/** The blood it costs (health), the redstone and bones it uses up, and the corruption it leaves on top of a blood spell's. */
	static final float BLOOD_COST = 8.0F;
	static final int REDSTONE = 6, BONES = 4, CORRUPTION = 5;
	/** The chance a slain blood witch leaves a stitched page; the chance a rare chest holds one. */
	static final float PAGE_DROP = 0.1F, PAGE_CHEST = 0.05F;
	/** The rite's colours: its circle, its glow. */
	static final int CRIMSON = 0x8A0F14, GLOW = 0xC4252C;
	/** How wide the circle is (blocks). */
	static final float CIRCLE = 2.4F;

	/** Whether a player has learned the rite. Saved, kept through death. */
	public static final AttachmentType<Boolean> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("brute_rite"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	public static Item PAGE;

	/** A rite being worked: who, upon whom, where, how much is left. */
	private static final class Rite {
		final ServerLevel level;
		final UUID maker, captive;
		final Vec3 centre;
		int left = RITE_TICKS;
		float makerHealth, captiveHealth;

		Rite(ServerLevel level, UUID maker, UUID captive, Vec3 centre, float makerHealth, float captiveHealth) {
			this.level = level;
			this.maker = maker;
			this.captive = captive;
			this.centre = centre;
			this.makerHealth = makerHealth;
			this.captiveHealth = captiveHealth;
		}
	}

	private static final Map<UUID, Rite> RITES = new HashMap<>();

	private BruteRite() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("stitched_page"));
		PAGE = Registry.register(BuiltInRegistries.ITEM, key, new Page(new Item.Properties().setId(key).rarity(Rarity.RARE).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(PAGE));
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RITES.clear());
		// a camp's blood witch, slain by a player, may leave one on her
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Worker witch && witch.role() == Sorcery.BLOOD_WITCH && source.getEntity() instanceof ServerPlayer
				&& entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < PAGE_DROP) {
				entity.spawnAtLocation(level, new ItemStack(PAGE));
			}
		});
		// and the rarest chests
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(k -> k.equals(BuiltInLootTables.STRONGHOLD_LIBRARY) || k.equals(BuiltInLootTables.ANCIENT_CITY)
				|| k.equals(BuiltInLootTables.WOODLAND_MANSION)).isPresent() && context.getRandom().nextFloat() < PAGE_CHEST) {
				drops.add(new ItemStack(PAGE));
			}
		});
	}

	/** The stitched page: read, it teaches the rite to a Blood Sorcerer and is spent. */
	static final class Page extends Item {
		Page(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			if (!Hemomancy.bloodMage(sp)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.cold").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (known(sp)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.known").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			sp.setAttached(KNOWN, true);
			player.getItemInHand(hand).consume(1, player);
			server.sendParticles(Fx.GLYPH, sp.getX(), sp.getY() + 1.2, sp.getZ(), 40, 0.4, 0.5, 0.4, 0.8);
			server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.7F);
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stitch.learned").withStyle(ChatFormatting.DARK_RED));
			return InteractionResult.SUCCESS;
		}
	}

	static boolean known(Player player) {
		return Boolean.TRUE.equals(player.getAttached(KNOWN));
	}

	// ---- beginning ---------------------------------------------------------------------------------------------------------

	/** What a captive was, for the brute's look: {@link MadeBrute#VILLAGER}, {@link MadeBrute#ILLAGER}, {@link MadeBrute#WITCH}; -1 for none that can be made. */
	static int kindOf(LivingEntity e) {
		if (e instanceof Villager) {
			return MadeBrute.VILLAGER;
		}
		if (e instanceof AbstractIllager) {
			return MadeBrute.ILLAGER;
		}
		return e instanceof Witch ? MadeBrute.WITCH : -1;
	}

	/** Whether {@code e} is held fast: in chains, or wrapped on a rope. */
	static boolean bound(LivingEntity e) {
		return Captives.bound(e) || com.jaymoskingdoms.forms.Lasso.wrapped(e);
	}

	/**
	 * The Blood Sorcerer's sneak-and-{@code J} with a bound captive in sight, once they know the rite: begins it. True if it
	 * took the key (begun, or refused with a word); false to let the key do its usual work.
	 */
	static boolean key(ServerPlayer player, ServerLevel level) {
		if (!known(player) || RITES.containsKey(player.getUUID())) {
			return false;
		}
		LivingEntity captive = Arcana.sighted(player);
		if (captive == null || kindOf(captive) < 0 || !bound(captive) || player.distanceTo(captive) > REACH) {
			return false;
		}
		if (!Hemomancy.bloodMage(player) || !Knowledge.has(player, Knowledge.HIGHER_ARTS)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.unready").withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		if (ForbiddenArts.stayed(player)) {
			return true;
		}
		if (alive(level, player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.one").withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		if (!player.isCreative() && (count(player, Items.REDSTONE) < REDSTONE || count(player, Items.BONE) < BONES)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.wants", REDSTONE, BONES).withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		if (!Hemomancy.pay(player, BLOOD_COST)) {
			return true;
		}
		if (!player.isCreative()) {
			take(player, Items.REDSTONE, REDSTONE);
			take(player, Items.BONE, BONES);
		}
		Vec3 centre = captive.position();
		RITES.put(player.getUUID(), new Rite(level, player.getUUID(), captive.getUUID(), centre, player.getHealth(), captive.getHealth()));
		Fx.sigil(level, centre.add(0.0, 0.05, 0.0), CIRCLE, CRIMSON, RITE_TICKS);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}

	/** Whether {@code player} has a made brute alive and loaded now. */
	static boolean alive(ServerLevel level, Player player) {
		for (ServerLevel l : level.getServer().getAllLevels()) {
			if (!l.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(MadeBrute.class),
				b -> b.isAlive() && player.getUUID().equals(b.maker())).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static int count(Player player, Item item) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void take(Player player, Item item, int amount) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				int used = Math.min(amount, stack.getCount());
				stack.shrink(used);
				amount -= used;
			}
		}
	}

	// ---- the channel -------------------------------------------------------------------------------------------------------

	private static void tick() {
		if (RITES.isEmpty()) {
			return;
		}
		for (Iterator<Rite> it = RITES.values().iterator(); it.hasNext();) {
			Rite rite = it.next();
			ServerLevel level = rite.level;
			Player maker = level.getPlayerByUUID(rite.maker);
			LivingEntity captive = level.getEntity(rite.captive) instanceof LivingEntity l ? l : null;
			String broken = null;
			if (maker == null || !maker.isAlive() || Downed.isDowned(maker)) {
				broken = "maker";
			} else if (captive == null || !captive.isAlive() || !bound(captive)) {
				broken = "captive";
			} else if (maker.distanceToSqr(captive) > HOLD_RANGE * HOLD_RANGE || maker.getHealth() < rite.makerHealth - 0.01F
				|| captive.getHealth() < rite.captiveHealth - 0.01F) {
				broken = "struck";
			}
			if (broken != null) {
				if (maker instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.broken").withStyle(ChatFormatting.DARK_RED));
				}
				level.playSound(null, rite.centre.x, rite.centre.y, rite.centre.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
				Fx.blast(level, rite.centre.add(0.0, 0.5, 0.0), 1.2F, CRIMSON);
				it.remove();
				continue;
			}
			rite.makerHealth = Math.max(rite.makerHealth, maker.getHealth());
			rite.captiveHealth = Math.max(rite.captiveHealth, captive.getHealth());
			rite.left--;
			Vec3 hand = maker.getEyePosition().add(0.0, -0.4, 0.0);
			Vec3 heart = captive.position().add(0.0, captive.getBbHeight() * 0.6, 0.0);
			if (rite.left % 10 == 0) {
				Fx.beam(level, hand, heart, 0.08F, GLOW, 12);
				Fx.orb(level, heart, 0.7F, CRIMSON, 12);
				level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.6F + (RITE_TICKS - rite.left) * 0.004F);
			}
			if (rite.left % 4 == 0) {
				level.sendParticles(Fx.rune(GLOW, 1.0F), heart.x, heart.y, heart.z, 2, 0.6, 0.9, 0.6, 0.05);
			}
			if (maker instanceof ServerPlayer sp && rite.left % 10 == 0) {
				int filled = (RITE_TICKS - rite.left) * 12 / RITE_TICKS;
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stitch.working", "=".repeat(filled) + "-".repeat(12 - filled))
					.withStyle(ChatFormatting.DARK_RED));
			}
			if (rite.left <= 0) {
				it.remove();
				if (maker instanceof ServerPlayer sp) {
					finish(level, sp, captive);
				}
			}
		}
	}

	/** The rite is done: {@code captive} is gone and a brute stands where they were. */
	private static void finish(ServerLevel level, ServerPlayer maker, LivingEntity captive) {
		int kind = kindOf(captive);
		Component who = captive.hasCustomName() ? captive.getCustomName() : captive instanceof Villager ? captive.getName() : null;
		Faction home = captive instanceof Villager ? homeOf(level, captive) : null;
		MadeBrute brute = Monsters.MADE_BRUTE.create(level, EntitySpawnReason.EVENT);
		if (brute == null) {
			return;
		}
		Vec3 at = captive.position();
		com.jaymoskingdoms.forms.Lasso.drop(captive);
		brute.snapTo(at.x, at.y, at.z, captive.getYRot(), 0.0F);
		if (!level.noCollision(brute)) {
			Vec3 moved = SorceryPicks.landing(level, brute, at.x, at.y, at.z, 2);
			if (moved != null) {
				brute.snapTo(moved.x, moved.y, moved.z, captive.getYRot(), 0.0F);
			}
		}
		Vec3 where = brute.position();
		brute.make(maker.getUUID(), kind, who);
		brute.finalizeSpawn(level, level.getCurrentDifficultyAt(brute.blockPosition()), EntitySpawnReason.EVENT, null);
		if (captive instanceof Villager) {
			Recollections.murdered(level, maker, captive);
		}
		captive.discard();
		level.addFreshEntity(brute);
		Fx.blast(level, where.add(0.0, 1.5, 0.0), 3.0F, CRIMSON);
		Fx.ring(level, where.add(0.0, 0.1, 0.0), 6.0F, GLOW);
		level.playSound(null, where.x, where.y, where.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
		PowersCompat.shakeAround(level, where, 24.0, 2.0F);
		maker.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stitch.done").withStyle(ChatFormatting.DARK_RED));
		// the cost: a forbidden cast's corruption and crime, and the heavier one of the making
		ForbiddenArts.cast(maker, level, true, CORRUPTION);
		Theft.unmaking(maker, level, brute.blockPosition(), home);
	}

	/** The settlement a villager belongs to: the one they were taken from, else the one whose land they stand on. */
	private static @Nullable Faction homeOf(ServerLevel level, LivingEntity villager) {
		Captives.Captive c = villager.getAttached(Captives.CAPTIVE);
		if (c != null && c.home() >= 0) {
			Faction home = Factions.get(level.getServer(), c.home());
			if (home != null) {
				return home;
			}
		}
		return Theft.lawAt(level, villager.blockPosition());
	}
}
