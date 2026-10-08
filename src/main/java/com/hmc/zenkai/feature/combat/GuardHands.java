package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.item.KiWeaponItem;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Qué llevas en las manos al defenderte (bloquear o parar). Código COMÚN a cliente y servidor: el
 * cliente lo usa para decidir si pide la guardia y para cancelar la interacción vanilla, el
 * servidor para validar y calcular cuánto daño se corta.
 *
 *  - EMPTY: manos vacías. La guardia de siempre (reducción de la habilidad Ki Block).
 *  - WEAPON: un arma de melee en la mano principal (espada, hacha, arma de ki) y nada en la
 *    secundaria. Se puede defender, con MENOS reducción: el arma ocupa la mano que bloquearía. A
 *    cambio, el contraataque ya sale con el arma.
 *  - SHIELD: un escudo en la mano secundaria (con lo que sea, o nada, en la principal). Cuenta
 *    como mano libre y defiende MEJOR: más reducción y sin ralentización, a costa de durabilidad;
 *    con la habilidad Ki Infuse, además se imbuye de ki (más reducción, gasta ki por golpe).
 *  - INVALID: cualquier otra cosa en las manos (comida, bloques, un arco...): no se puede defender
 *    y el botón de usar vuelve a ser el de vanilla.
 */
public final class GuardHands {
    private GuardHands() {}

    public enum Kind { EMPTY, WEAPON, SHIELD, INVALID }

    public static boolean isShield(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof ShieldItem
                || s.canPerformAction(ItemAbilities.SHIELD_BLOCK));
    }

    public static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES)
                || s.getItem() instanceof KiWeaponItem);
    }

    /** Espada de verdad (o arma de ki) en la mano principal, sin nada raro en la secundaria. Es lo
     *  que exige una técnica con {@code weapon_requirement: sword}; el hacha defiende pero no cuenta. */
    public static boolean isHoldingSword(LivingEntity e) {
        ItemStack main = e.getMainHandItem();
        boolean sword = !main.isEmpty() && (main.is(ItemTags.SWORDS) || main.getItem() instanceof KiWeaponItem);
        return sword && kind(e) != Kind.INVALID;
    }

    public static Kind kind(LivingEntity e) {
        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffhandItem();
        boolean mainOk = main.isEmpty() || isWeapon(main);
        boolean offOk = off.isEmpty() || isShield(off);
        if (!mainOk || !offOk) return Kind.INVALID;
        if (isShield(off)) return Kind.SHIELD;
        return main.isEmpty() ? Kind.EMPTY : Kind.WEAPON;
    }

    public static boolean canGuard(LivingEntity e) { return kind(e) != Kind.INVALID; }

    /**
     * Multiplicador de daño RECIBIDO al defender (menos es mejor), con sus costes. SE LLAMA UNA VEZ
     * POR GOLPE bloqueado: con escudo gasta durabilidad y, si está imbuido, ki.
     */
    public static double consumeBlockMultiplier(ServerPlayer sp) {
        double r = SkillEffects.blockReduction(sp);
        switch (kind(sp)) {
            case WEAPON -> r *= ServerConfig.blockWeaponFactor();
            case SHIELD -> r = shieldReduction(sp, r);
            default -> { }
        }
        return Math.max(0.0, 1.0 - Math.min(0.95, Math.max(0.0, r)));
    }

    private static double shieldReduction(ServerPlayer sp, double base) {
        double r = base * (1.0 + ServerConfig.blockShieldBonus());
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.SHIELD_BLOCKED);

        // Imbuido de ki (mejora de la habilidad Ki Infuse): más reducción a cambio de ki por golpe.
        // Sin ki suficiente no se imbuye y el escudo defiende como un escudo normal.
        double infuse = SkillEffects.kiInfuseShieldBonus(sp);
        if (infuse > 0.0) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            int cost = (int) Math.ceil(att.getEnergyMax() * ServerConfig.blockShieldKiPct());
            if (att.getEnergy() >= cost) {
                att.consumeEnergy(cost);
                r += infuse;
                com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.KI_SHIELD);
            }
        }

        // Durabilidad: cada golpe bloqueado con el escudo lo desgasta.
        int wear = ServerConfig.blockShieldDurability();
        ItemStack shield = sp.getOffhandItem();
        if (wear > 0 && !shield.isEmpty() && shield.isDamageableItem()) {
            shield.hurtAndBreak(wear, sp, EquipmentSlot.OFFHAND);
        }
        return Math.min(r, ServerConfig.blockShieldCap());
    }

    /** Fracción de velocidad que se pierde defendiendo con estos agarres (0.6 = -60 %). */
    public static double slowdown(ServerPlayer sp, double defaultSlow) {
        return kind(sp) == Kind.SHIELD ? ServerConfig.blockShieldSlow() : defaultSlow;
    }
}
