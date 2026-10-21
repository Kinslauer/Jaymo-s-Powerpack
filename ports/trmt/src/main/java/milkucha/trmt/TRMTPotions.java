package milkucha.trmt;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public final class TRMTPotions {

    public static Potion LIGHTNESS;
    public static Potion LONG_LIGHTNESS;

    private TRMTPotions() {}

    public static void register() {
        LIGHTNESS = Registry.register(
                BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath("trmt", "lightness"),
                new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 3600))
        );
        LONG_LIGHTNESS = Registry.register(
                BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath("trmt", "long_lightness"),
                new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 9600))
        );
        // 26.3: brewing is data-driven (data/trmt/recipe/brewing/*.json)
    }
}
