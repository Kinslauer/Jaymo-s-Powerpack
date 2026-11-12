package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The powderhead (Andy, 2026-11-12, on the pawn picks: "a companion that crafts and throws bombs... good partner to
 * marksmen that need gunpowder constantly"). A knack ({@link Specialties.Specialty#POWDERHEAD}), taught from the
 * weaponsmith's or armourer's book, and a foe specialist in the bands. Nothing comes from nowhere: everything it makes
 * and throws is made from what it carries ({@link SimpleContainer the villager's own pack}).
 * <ul>
 * <li><b>Stocking it</b>: use charcoal, bone meal, gunpowder, string, paper, an iron ingot, gravel or a dynamite stick
 * on your powderhead and it packs the lot ({@link #STOCK}). It also picks up loose gunpowder near it (a creeper's).</li>
 * <li><b>Black powder</b>: out of a fight it grinds one charcoal and two bone meal (the saltpetre of the bone heap)
 * into a gunpowder ({@link #GRIND_EVERY}), keeping a few in hand; players craft it the same way (recipe
 * {@code black_powder}).</li>
 * <li><b>For the marksman</b>: if you carry a gun (a WeaponMod musket, flintlock or blunderbuss) and run low on its
 * ammunition ({@link #LOW_AMMO}), it rolls you cartridges from its pack by the gunsmith's own measure (an iron ingot
 * or gravel, a gunpowder and a paper for eight: bullets, or shot for a blunderbuss); without the makings it hands you
 * raw gunpowder when you're short ({@link #LOW_POWDER}). A companion of yours with a musket that ran dry gets shot
 * from it too ({@link #SHOT_PER_POWDER} a gunpowder).</li>
 * <li><b>Bombs</b>: two gunpowder and a string make two dynamite sticks (the stick's own recipe), up to
 * {@link #STICKS_KEPT}. In a fight it lobs one at a foe {@link #THROW_NEAR}-{@link #THROW_FAR} blocks off, every
 * {@link #THROW_EVERY} ticks at most, and only when no friend, you included, nor bystander is where it would land
 * ({@link Dynamite#clear}): otherwise it shouts and holds it.</li>
 * <li><b>The foe's powderhead</b>: a band member may be one ({@link #FOE_CHANCE}, rolled with the band's other
 * specialists): it carries {@link #FOE_STICKS} sticks and {@link #FOE_POWDER} measures of powder, throws the sticks
 * with the same care, tops up a mate's musket that ran dry, and drops what's left when it falls.</li>
 * </ul>
 */
public final class Powderheads {
	/** Out of a fight it works this often (ticks); in one it looks for a throw this often. */
	static final int GRIND_EVERY = 200, FIGHT_EVERY = 10;
	/** Ammunition and gunpowder below which it tops you up. */
	static final int LOW_AMMO = 16, LOW_POWDER = 4;
	/** Gunpowder it hands over at a time, and keeps back for its own sticks. */
	static final int HAND_POWDER = 4, KEEP_POWDER = 2;
	/** Shot a measure of powder gives a companion's or mate's musket. */
	static final int SHOT_PER_POWDER = 4;
	/** Sticks it keeps made up. */
	static final int STICKS_KEPT = 6;
	/** Its throw: from this near to this far (blocks), at most this often (ticks). */
	static final double THROW_NEAR = 5.0, THROW_FAR = 18.0, PICK_UP = 6.0;
	static final int THROW_EVERY = 100;
	/** A band member is a powderhead at this chance; carries this many sticks and measures of powder (between). */
	static final float FOE_CHANCE = 0.05F;
	static final int[] FOE_STICKS = {2, 4}, FOE_POWDER = {2, 4};

	/** On a foe powderhead: [sticks, powder] left. Saved. */
	public static final AttachmentType<java.util.List<Integer>> FOE_STOCK = AttachmentRegistry.create(JaymosKingdoms.id("powderhead_stock"),
		builder -> builder.persistent(Codec.INT.listOf()));

	private static final Identifier BULLET = Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "bullet");
	private static final Identifier SHOT = Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "shot");
	private static final Identifier BLUNDERBUSS = Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "blunderbuss");

	private static final Map<UUID, Long> THROWN = new HashMap<>(), HELD_SAID = new HashMap<>();

	private Powderheads() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Powderheads::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			THROWN.clear();
			HELD_SAID.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			java.util.List<Integer> stock = entity.getAttached(FOE_STOCK);
			if (stock != null && !stock.isEmpty() && stock.get(0) > 0 && entity.level() instanceof ServerLevel level) {
				entity.spawnAtLocation(level, new ItemStack(Dynamite.stick(), stock.get(0)));
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()
				|| !(entity instanceof Villager v) || !Specialties.is(v, Specialties.Specialty.POWDERHEAD) || !sp.getUUID().equals(Bonds.lordOf(v))
				|| Downed.isDowned(v)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (!stock(held)) {
				return InteractionResult.PASS;
			}
			ItemStack left = v.getInventory().addItem(held.copy());
			if (left.getCount() == held.getCount()) {
				Callouts.say(v, "powder.full", sp, true);
				return InteractionResult.SUCCESS;
			}
			Callouts.say(v, "powder.stocked", sp, true, held.getHoverName());
			held.setCount(left.getCount());
			world.playSound(null, v.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL, 0.8F, 1.0F);
			return InteractionResult.SUCCESS;
		});
	}

	/** What a powderhead packs when you hand it over. */
	static boolean stock(ItemStack s) {
		return s.is(Items.CHARCOAL) || s.is(Items.BONE_MEAL) || s.is(Items.GUNPOWDER) || s.is(Items.STRING) || s.is(Items.PAPER)
			|| s.is(Items.IRON_INGOT) || s.is(Items.GRAVEL) || s.is(Dynamite.stick());
	}

	/** (Specialties.roll) a band member's chance of being a powderhead, and what it carries. */
	static void rollFoe(Mob m) {
		RandomSource r = m.getRandom();
		m.setAttached(FOE_STOCK, java.util.List.of(FOE_STICKS[0] + r.nextInt(FOE_STICKS[1] - FOE_STICKS[0] + 1),
			FOE_POWDER[0] + r.nextInt(FOE_POWDER[1] - FOE_POWDER[0] + 1)));
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % FIGHT_EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		boolean work = t % GRIND_EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			for (Mob m : Bonds.companions(player, 32.0)) {
				if (!(m instanceof Villager v) || !Specialties.is(v, Specialties.Specialty.POWDERHEAD)) {
					continue;
				}
				LivingEntity foe = Comrades.target(v);
				if (foe != null) {
					throwAt(level, v, foe, now, true);
				} else if (work) {
					work(level, v, player);
				}
			}
			// the bands' own, near a player
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), e -> e.isAlive() && !Downed.isDowned(e)
				&& Specialties.is(e, Specialties.Specialty.POWDERHEAD) && e.hasAttached(FOE_STOCK) && !(e instanceof Villager v && player.getUUID().equals(Bonds.lordOf(v))))) {
				LivingEntity foe = Comrades.target(m);
				if (foe != null) {
					throwAt(level, m, foe, now, false);
					if (work) {
						supplyMates(level, m);
					}
				}
			}
		}
	}

	/** Out of a fight: pick up loose powder, see to the marksmen, grind powder, make sticks. One job a time. */
	private static void work(ServerLevel level, Villager v, ServerPlayer player) {
		SimpleContainer pack = v.getInventory();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, v.getBoundingBox().inflate(PICK_UP), e -> e.isAlive()
			&& e.getItem().is(Items.GUNPOWDER))) {
			ItemStack left = pack.addItem(item.getItem().copy());
			if (left.isEmpty()) {
				item.discard();
			} else {
				item.setItem(left);
			}
		}
		if (marksman(v, player, pack) || musketeers(level, v, player, pack)) {
			return;
		}
		int powder = count(pack, Items.GUNPOWDER);
		if (powder < KEEP_POWDER + HAND_POWDER && count(pack, Items.CHARCOAL) >= 1 && count(pack, Items.BONE_MEAL) >= 2) {
			take(pack, Items.CHARCOAL, 1);
			take(pack, Items.BONE_MEAL, 2);
			pack.addItem(new ItemStack(Items.GUNPOWDER));
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, v.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
			level.sendParticles(ParticleTypes.SMOKE, v.getX(), v.getY() + 1.0, v.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
			if (v.getRandom().nextInt(4) == 0) {
				Callouts.say(v, "powder.grind", player, false);
			}
			return;
		}
		if (count(pack, Dynamite.stick()) < STICKS_KEPT && powder >= 2 && count(pack, Items.STRING) >= 1) {
			take(pack, Items.GUNPOWDER, 2);
			take(pack, Items.STRING, 1);
			pack.addItem(new ItemStack(Dynamite.stick(), 2));
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, v.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL, 0.6F, 0.8F);
			if (v.getRandom().nextInt(3) == 0) {
				Callouts.say(v, "powder.sticks", player, false);
			}
		}
	}

	/** You carry a gun and are low on what it fires: cartridges if it has the makings, else raw powder. */
	private static boolean marksman(Villager v, ServerPlayer player, SimpleContainer pack) {
		boolean blunderbuss = false, gun = false;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(ClassPerks.GUNS)) {
				gun = true;
				blunderbuss |= BuiltInRegistries.ITEM.getKey(s.getItem()).equals(BLUNDERBUSS);
			}
		}
		if (!gun || player.distanceTo(v) > 8.0) {
			return false;
		}
		Item ammo = BuiltInRegistries.ITEM.getOptional(blunderbuss ? SHOT : BULLET).orElse(null);
		Item base = blunderbuss ? Items.GRAVEL : Items.IRON_INGOT;
		if (ammo != null && carried(player, ammo) < LOW_AMMO && count(pack, base) >= 1 && count(pack, Items.GUNPOWDER) >= 1
			&& count(pack, Items.PAPER) >= 1) {
			take(pack, base, 1);
			take(pack, Items.GUNPOWDER, 1);
			take(pack, Items.PAPER, 1);
			hand(player, new ItemStack(ammo, 8));
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Callouts.say(v, "powder.cartridges", player, true, new ItemStack(ammo).getHoverName());
			return true;
		}
		int spare = count(pack, Items.GUNPOWDER) - KEEP_POWDER;
		if (carried(player, Items.GUNPOWDER) < LOW_POWDER && spare > 0) {
			int n = Math.min(HAND_POWDER, spare);
			take(pack, Items.GUNPOWDER, n);
			hand(player, new ItemStack(Items.GUNPOWDER, n));
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Callouts.say(v, "powder.hand", player, true, n);
			return true;
		}
		return false;
	}

	/** A companion of yours with a musket that ran dry: a measure of powder makes its shot. */
	private static boolean musketeers(ServerLevel level, Villager v, ServerPlayer player, SimpleContainer pack) {
		if (count(pack, Items.GUNPOWDER) < 1) {
			return false;
		}
		for (Mob mate : Bonds.companions(player, 16.0)) {
			if (mate != v && MobAmmo.is(mate.getMainHandItem(), MobAmmo.Kind.SHOT) && MobAmmo.left(mate, MobAmmo.Kind.SHOT) == 0) {
				take(pack, Items.GUNPOWDER, 1);
				MobAmmo.give(mate, MobAmmo.Kind.SHOT, SHOT_PER_POWDER);
				Callouts.say(v, "powder.mate", mate, true, mate.getDisplayName());
				return true;
			}
		}
		return false;
	}

	/** A band's powderhead in a fight tops up a mate whose musket ran dry. */
	private static void supplyMates(ServerLevel level, Mob m) {
		java.util.List<Integer> stock = m.getAttached(FOE_STOCK);
		if (stock == null || stock.size() < 2 || stock.get(1) <= 0) {
			return;
		}
		String side = Comrades.side(m);
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16.0), e -> e != m && e.isAlive() && !Downed.isDowned(e)
			&& Comrades.side(e).equals(side) && MobAmmo.is(e.getMainHandItem(), MobAmmo.Kind.SHOT) && MobAmmo.left(e, MobAmmo.Kind.SHOT) == 0)) {
			MobAmmo.give(mate, MobAmmo.Kind.SHOT, SHOT_PER_POWDER);
			m.setAttached(FOE_STOCK, java.util.List.of(stock.get(0), stock.get(1) - 1));
			Callouts.say(m, "powder.mate", mate, false, mate.getDisplayName());
			return;
		}
	}

	/** A throw at {@code foe}, if one's in hand, the range is right and no friend or bystander is in the blast. */
	private static void throwAt(ServerLevel level, Mob m, LivingEntity foe, long now, boolean companion) {
		double d = m.distanceTo(foe);
		if (d < THROW_NEAR || d > THROW_FAR || now - THROWN.getOrDefault(m.getUUID(), -THROW_EVERY * 2L) < THROW_EVERY || !m.hasLineOfSight(foe)) {
			return;
		}
		if (companion ? count(((Villager) m).getInventory(), Dynamite.stick()) < 1 : sticks(m) < 1) {
			return;
		}
		RandomSource random = m.getRandom();
		int flight = (int) Math.round(Math.min(40.0, Math.max(14.0, 10.0 + d)));
		double stray = 1.5 * d / THROW_FAR;
		Vec3 mark = foe.position().add(foe.getDeltaMovement().multiply(flight * 0.6, 0.0, flight * 0.6))
			.add((random.nextDouble() * 2.0 - 1.0) * stray, 0.0, (random.nextDouble() * 2.0 - 1.0) * stray);
		if (!Dynamite.clear(level, m, foe, mark)) {
			// a friend (or you) where it would land: it holds it, and says so now and then
			THROWN.put(m.getUUID(), now - THROW_EVERY + 20);
			if (now - HELD_SAID.getOrDefault(m.getUUID(), -600L) > 600L) {
				HELD_SAID.put(m.getUUID(), now);
				Callouts.say(m, "powder.hold", foe, false);
			}
			return;
		}
		THROWN.put(m.getUUID(), now);
		if (companion) {
			take(((Villager) m).getInventory(), Dynamite.stick(), 1);
		} else {
			java.util.List<Integer> stock = m.getAttached(FOE_STOCK);
			m.setAttached(FOE_STOCK, java.util.List.of(stock.get(0) - 1, stock.size() > 1 ? stock.get(1) : 0));
		}
		m.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Vec3 from = new Vec3(m.getX(), m.getEyeY() - 0.2, m.getZ());
		Dynamite.light(level, m, Dynamite.lob(from, mark, flight));
		Callouts.say(m, "powder.throw", foe, false);
	}

	private static int sticks(Mob m) {
		java.util.List<Integer> stock = m.getAttached(FOE_STOCK);
		return stock == null || stock.isEmpty() ? 0 : stock.get(0);
	}

	// ---- the pack ------------------------------------------------------------------------------------------------------

	static int count(SimpleContainer pack, Item item) {
		int n = 0;
		for (int i = 0; i < pack.getContainerSize(); i++) {
			ItemStack s = pack.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(SimpleContainer pack, Item item, int n) {
		for (int i = 0; i < pack.getContainerSize() && n > 0; i++) {
			ItemStack s = pack.getItem(i);
			if (s.is(item)) {
				int k = Math.min(n, s.getCount());
				s.shrink(k);
				n -= k;
				pack.setChanged();
			}
		}
	}

	private static int carried(ServerPlayer player, Item item) {
		int n = 0;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void hand(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
