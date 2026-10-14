package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.kiweapon.KiWeaponDef;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
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
 *  - SHIELD: un escudo (vanilla o DE KI, ver hasKiShield) en la mano secundaria. Cuenta
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
        return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES));
    }

    /** ¿Tiene materializada un arma de ki? No es un ítem: la mano principal está VACÍA y el arma
     *  la dibuja una capa, así que todo lo que antes miraba "mano principal vacía" tiene que
     *  preguntar también esto (ver KiWeaponState). */
    public static boolean hasKiWeapon(LivingEntity e) {
        return KiWeaponState.isActive(e);
    }

    /** ¿Tiene materializado un escudo de ki en la mano SECUNDARIA? Tampoco es un ítem: la mano
     *  secundaria está vacía y lo dibuja una capa (ver KiWeaponState.offhandOf). */
    public static boolean hasKiShield(LivingEntity e) {
        return KiWeaponState.offhandOf(e) != null;
    }

    /** ¿Lleva un arma de ki que cuenta como ESPADA (Blade o Sword)? El hacha, la maza, el mazo y la
     *  guadaña son armas de melee pero no abren las técnicas de espada. */
    public static boolean hasKiSword(LivingEntity e) {
        String v = KiWeaponState.variantOf(e);
        return com.hmc.zenkai.feature.skills.SkillEffects.KI_BLADE.equals(v)
                || com.hmc.zenkai.feature.skills.SkillEffects.KI_SWORD.equals(v);
    }

    /** Manos realmente libres para una técnica: nada en ninguna mano Y ningún arma de ki activa.
     *  Sustituye a {@code main.isEmpty() && off.isEmpty()} dondequiera que se pida "manos libres". */
    public static boolean handsFree(LivingEntity e) {
        return e.getMainHandItem().isEmpty() && e.getOffhandItem().isEmpty()
                && !hasKiWeapon(e) && !hasKiShield(e);
    }

    /** Espada de verdad (o arma de ki) en la mano principal, sin nada raro en la secundaria. Es lo
     *  que exige una técnica con {@code weapon_requirement: sword}; el hacha defiende pero no cuenta. */
    public static boolean isHoldingSword(LivingEntity e) {
        ItemStack main = e.getMainHandItem();
        boolean sword = (!main.isEmpty() && main.is(ItemTags.SWORDS)) || hasKiSword(e);
        return sword && kind(e) != Kind.INVALID;
    }

    public static Kind kind(LivingEntity e) {
        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffhandItem();
        boolean mainOk = main.isEmpty() || isWeapon(main);
        boolean offOk = off.isEmpty() || isShield(off);
        if (!mainOk || !offOk) return Kind.INVALID;
        if (isShield(off) || hasKiShield(e)) return Kind.SHIELD;
        return main.isEmpty() && !hasKiWeapon(e) ? Kind.EMPTY : Kind.WEAPON;
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
        if (hasKiShield(sp)) return kiShieldReduction(sp, base);
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

    /**
     * Escudo DE KI: defiende como un escudo (mismo bonus de config) y suma su {@code block_bonus};
     * en vez de durabilidad, cada golpe bloqueado cuesta ki ({@code block_ki_pct} del máximo). Sin
     * ki suficiente el escudo se "agrieta" y defiende solo como la guardia normal: no deja de
     * protegerte del todo, pero pierde la ventaja que justifica llevarlo.
     */
    private static double kiShieldReduction(ServerPlayer sp, double base) {
        KiWeaponDef def = KiWeaponServer.heldShield(sp);
        if (def == null) return base;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int cost = (int) Math.ceil(att.getEnergyMax() * def.blockKiPct());
        if (att.getEnergy() < cost) return base;
        att.consumeEnergy(cost);
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp,
                com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.SHIELD_BLOCKED);
        double r = base * (1.0 + ServerConfig.blockShieldBonus()) + def.blockBonus();
        return Math.min(r, ServerConfig.blockShieldCap());
    }

    /** Fracción de velocidad que se pierde defendiendo con estos agarres (0.6 = -60 %). */
    public static double slowdown(ServerPlayer sp, double defaultSlow) {
        return kind(sp) == Kind.SHIELD ? ServerConfig.blockShieldSlow() : defaultSlow;
    }
}
