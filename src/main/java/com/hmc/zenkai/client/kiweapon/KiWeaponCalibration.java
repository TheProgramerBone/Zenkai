package com.hmc.zenkai.client.kiweapon;

import net.minecraft.world.entity.HumanoidArm;

/**
 * Calibración del arma respecto al marco de ítem. HAY CUATRO JUEGOS INDEPENDIENTES: vista
 * (mano de 1ª persona o 3ª persona/cuerpo entero de PAL) × brazo (derecho o izquierdo). Los
 * marcos no son el mismo: la mano de 1ª persona sale de RenderArmEvent con el brazo de la raza
 * calibrado a mano, y el brazo izquierdo de ese hook NO es un espejo del derecho (ver
 * ZenkaiFirstPersonArmHooks); un solo juego descuadraba una vista al ajustar la otra. Edítalos con
 * /zenkaiweapon (mode fp|tp, arm right|left).
 * El brazo izquierdo se usa con zurdos y con lo que venga por la mano secundaria (el Shield).
 * TODOS arrancan a 0 (2026-10-13): en 1ª persona el arma se cuelga de la matriz real del hueso del
 * brazo (ZenkaiFirstPersonArmHooks -> KiWeaponFirstPerson.drawOnRaceArm, o el pivote del hombro de
 * vanilla), igual que en 3ª persona; el comando solo sirve para afinar a gusto.
 */
public final class KiWeaponCalibration {
    public float x = 0F, y = 0F, z = 0F;
    public float rx = 0F, ry = 0F, rz = 0F;
    public float s = 1F;

    private static final KiWeaponCalibration FIRST_RIGHT = new KiWeaponCalibration();
    private static final KiWeaponCalibration FIRST_LEFT = new KiWeaponCalibration();
    private static final KiWeaponCalibration THIRD_RIGHT = new KiWeaponCalibration();
    private static final KiWeaponCalibration THIRD_LEFT = new KiWeaponCalibration();

    static {
        FIRST_RIGHT.resetToFactory(true, HumanoidArm.RIGHT);
        FIRST_LEFT.resetToFactory(true, HumanoidArm.LEFT);
    }

    /** El juego de esta vista y este brazo. */
    public static KiWeaponCalibration of(boolean firstPerson, HumanoidArm arm) {
        if (firstPerson) return arm == HumanoidArm.LEFT ? FIRST_LEFT : FIRST_RIGHT;
        return arm == HumanoidArm.LEFT ? THIRD_LEFT : THIRD_RIGHT;
    }

    // ── Lo que edita el comando ──────────────────────────────────────────────

    public static boolean editingFirst = false;
    public static HumanoidArm editingArm = HumanoidArm.RIGHT;

    public static KiWeaponCalibration editing() { return of(editingFirst, editingArm); }

    public static String editingName() {
        return (editingFirst ? "1ª persona (fp)" : "3ª persona (tp)") + ", brazo "
                + (editingArm == HumanoidArm.LEFT ? "izquierdo" : "derecho");
    }

    /** Vuelve a los valores de fábrica de ESTE juego. */
    public void reset() {
        for (boolean first : new boolean[]{true, false}) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                if (of(first, arm) == this) { resetToFactory(first, arm); return; }
            }
        }
    }

    private void resetToFactory(boolean first, HumanoidArm arm) {
        x = y = z = 0F;
        rx = ry = rz = 0F;
        s = 1F;
    }
}
