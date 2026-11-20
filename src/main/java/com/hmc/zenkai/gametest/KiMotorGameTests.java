package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.create.KiMotorFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Motor de Ki (0.7.0, compat con Create): conversión del fluido a FE con la tabla de la cámara, el
 * arranque por energía, las reglas de los puertos por cara y por modo, y la persistencia.
 * Sin Create cargado cada test se da por bueno (el bloque es inerte). No prueba la red de Create
 * (estrés real): eso se ve en juego.
 *
 * OJO: aquí no puede aparecer NINGÚN tipo de compat/create que extienda Create, ni siquiera en un
 * helper privado: NeoForge lee las firmas de esta clase con reflexión al arrancar y, sin Create,
 * el juego se cae antes del menú. Los cuerpos están en {@link KiMotorTestCases}.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiMotorGameTests {
    private KiMotorGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void motorConvertsFluidAndStarts(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.motorConvertsFluidAndStarts(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void motorDoesNotStartWithoutEnergy(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.motorDoesNotStartWithoutEnergy(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void portsFollowModeAndFace(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.portsFollowModeAndFace(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tankRejectsMixedKiAndOtherFluids(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.tankRejectsMixedKiAndOtherFluids(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void settingsSurviveSaveAndLoad(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.settingsSurviveSaveAndLoad(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tankHoldsConfiguredCapacity(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.tankHoldsConfiguredCapacity(helper);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void generatorPushesIntoCable(GameTestHelper helper) {
        if (!KiMotorFactory.loaded()) { helper.succeed(); return; }
        KiMotorTestCases.generatorPushesIntoCable(helper);
    }
}
