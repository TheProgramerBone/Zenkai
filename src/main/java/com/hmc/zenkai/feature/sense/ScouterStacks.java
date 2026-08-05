package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.item.ScouterItem;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Acceso ÚNICO al scouter equipado y a sus mejoras. Paquetes, overlay, banco y yunque pasan
 * por aquí: si mañana el scouter deja de ser casco y pasa a ser solo curio, se cambia en este
 * archivo y ya. Nadie más lee el componente ni resuelve alcance o tope de PL por su cuenta.
 *
 * ⚠ API a verificar: CuriosCompat.findEquipped debe devolver el stack VIVO, no una copia, o
 * romper/mejorar el scouter no se guardaría. Si devuelve copia, hay que añadirle un setter.
 */
public final class ScouterStacks {
    private ScouterStacks() {}

    /** El scouter puesto (curio primero, casco vanilla de respaldo), o EMPTY. */
    public static ItemStack equipped(LivingEntity le) {
        ItemStack curio = CuriosCompat.findEquipped(le, "scouter");
        if (!curio.isEmpty() && curio.getItem() instanceof ScouterItem) return curio;
        ItemStack head = le.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof ScouterItem ? head : ItemStack.EMPTY;
    }

    // ── Mejoras ──────────────────────────────────────────────────────────────

    /** Mejoras del stack. Ausente = NONE, nunca null. */
    public static ScouterUpgrades upgrades(ItemStack stack) {
        ScouterUpgrades u = stack.get(ModDataComponents.SCOUTER_UPGRADES.get());
        return u == null ? ScouterUpgrades.NONE : u;
    }

    public static void setUpgrades(ItemStack stack, ScouterUpgrades u) {
        stack.set(ModDataComponents.SCOUTER_UPGRADES.get(), u);
    }

    public static int level(ItemStack stack, ScouterUpgrade u) {
        return upgrades(stack).level(u);
    }

    public static boolean has(ItemStack stack, ScouterUpgrade u) {
        return upgrades(stack).has(u);
    }

    /** Alcance efectivo del raycast, en bloques. */
    public static double range(ItemStack stack) {
        return ScouterUpgrade.rangeFor(level(stack, ScouterUpgrade.RANGE));
    }

    /** PL máximo legible sin sobrecargarse. */
    public static long plCap(ItemStack stack) {
        return ScouterUpgrade.plCapFor(level(stack, ScouterUpgrade.PL_CAP));
    }

    public static boolean canRead(ItemStack stack, long pl) {
        return pl <= plCap(stack);
    }

    // ── Rotura ───────────────────────────────────────────────────────────────

    public static boolean isBroken(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.SCOUTER_BROKEN.get()));
    }

    /** ¿Lleva un scouter FUNCIONAL? Un scouter roto no cuenta: es peso muerto y, por eso
     *  mismo, deja de bloquear el sentir el ki. */
    public static boolean hasWorking(LivingEntity le) {
        ItemStack s = equipped(le);
        return !s.isEmpty() && !isBroken(s);
    }

    /** ¿Lleva scouter, roto o no? Lo usa la grieta del overlay, que se dibuja precisamente
     *  cuando el aparato NO funciona. */
    public static boolean hasAny(LivingEntity le) {
        return !equipped(le).isEmpty();
    }

    /**
     * Revienta el scouter puesto: componente roto, cristal, chispas y un mordisco de daño.
     * El stack NO se destruye — conserva mejoras y tinte para que la reparación lo devuelva
     * entero.
     *
     * El daño va por el pipeline normal a propósito: en creativo no hace nada, pero el
     * scouter se rompe igual. La avería es del aparato, no del jugador.
     */
    public static void breakScouter(ServerPlayer sp) {
        ItemStack stack = equipped(sp);
        if (stack.isEmpty() || isBroken(stack)) return;

        stack.set(ModDataComponents.SCOUTER_BROKEN.get(), true);

        ServerLevel lvl = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        lvl.playSound(null, sp.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, 25,
                0.25, 0.2, 0.25, 0.08);
        lvl.sendParticles(ParticleTypes.SMOKE, eye.x, eye.y, eye.z, 10,
                0.15, 0.15, 0.15, 0.02);

        sp.hurt(sp.damageSources().generic(), 2.0F);
    }

    /** Deshace la rotura conservando mejoras y tinte. Único sitio que quita el componente. */
    public static void repair(ItemStack stack) {
        stack.remove(ModDataComponents.SCOUTER_BROKEN.get());
    }
}