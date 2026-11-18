package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.KiContainmentChamberBlock;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModFluids;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.content.item.EnergyBatteryItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cámara de contención de ki (0.7.0, Fase 2a): la regla forma → energía, el drenaje con su corte
 * en la reserva y su cooldown, el forzado que nunca mata, el corte por cambio de forma, el tanque
 * de solo extracción y las tasas del conversor a FE.
 * La carga se prueba llamando a tickCharge a mano (testStartCharge salta la puerta y la sujeción,
 * que mueven al jugador); la captura por la puerta y la extracción a mobs tienen sus propios tests.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiChamberGameTests {
    private KiChamberGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static KiContainmentChamberBlockEntity chamber(GameTestHelper helper) {
        BlockState lower = ModBlocks.KI_CONTAINMENT_CHAMBER.get().defaultBlockState();
        helper.setBlock(POS, lower);
        helper.setBlock(POS.above(), lower.setValue(KiContainmentChamberBlock.HALF, DoubleBlockHalf.UPPER));
        return helper.getBlockEntity(POS);
    }

    /** Jugador con el ki al máximo y sin cooldown. */
    private static ServerPlayer chargedPlayer(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.addKi(att.getKiPool());
        att.flags().setKiChamberCooldownUntil(0L);
        return sp;
    }

    private static ResourceLocation zenkai(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void formDecidesEnergyType(GameTestHelper helper) {
        check(helper, FormIds.BASE, KiEnergyType.BASE);
        check(helper, FormIds.POTENTIAL_UNLOCK, KiEnergyType.BASE);
        check(helper, FormIds.SSJ1, KiEnergyType.SUPER);
        check(helper, FormIds.SSJ3, KiEnergyType.SUPER);
        check(helper, FormIds.SSJ4, KiEnergyType.SUPER);
        check(helper, zenkai("namek_super"), KiEnergyType.SUPER);
        check(helper, zenkai("namek_orange"), KiEnergyType.SUPER);
        check(helper, FormIds.GOLDEN_FORM, KiEnergyType.SUPER);
        check(helper, FormIds.BLACK_FIFTH_FORM, KiEnergyType.SUPER);
        check(helper, zenkai("human_super"), KiEnergyType.SUPER);
        check(helper, zenkai("majin_pure_evil"), KiEnergyType.SUPER);
        check(helper, FormIds.BEAST, KiEnergyType.SUPER);
        check(helper, FormIds.FINAL_FORM, KiEnergyType.BASE);
        check(helper, zenkai("ssj_god"), KiEnergyType.DIVINE);
        check(helper, FormIds.SSJ_BLUE_EVOLUTION, KiEnergyType.DIVINE);
        check(helper, FormIds.NAMEK_GOD, KiEnergyType.DIVINE);
        check(helper, FormIds.ULTRA_INSTINCT_SIGN, KiEnergyType.DIVINE);
        check(helper, FormIds.ULTRA_EGO, KiEnergyType.DIVINE);
        check(helper, FormIds.IKARI, KiEnergyType.LEGENDARY);
        check(helper, FormIds.SSJ_GREEN_MAX, KiEnergyType.LEGENDARY);
        check(helper, FormIds.NAMEK_LEGENDARY, KiEnergyType.LEGENDARY);
        check(helper, FormIds.OOZARU, null);
        check(helper, FormIds.SUPER_OOZARU, null);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, ResourceLocation form, KiEnergyType expected) {
        KiEnergyType got = KiEnergyType.of(form);
        helper.assertTrue(got == expected, form + " debe dar " + expected + " y da " + got);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void chargeStopsAtReserveAndStartsCooldown(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int pool = att.getKiPool();

        helper.assertTrue(be.testStartCharge(sp, false), "Con el ki lleno y sin cooldown debe arrancar");
        int ticks = 0;
        while (be.isCharging() && ticks < 2000) { be.tickCharge(sp); ticks++; }

        double reserve = pool * ServerConfig.kiChamberReservePercent() / 100.0;
        helper.assertTrue(!be.isCharging(), "La carga debe cortarse sola al llegar a la reserva");
        helper.assertTrue(Math.abs(att.getKiCurrent() - reserve) <= 1.5,
                "El ki debe quedar en la reserva (" + reserve + "); quedó en " + att.getKiCurrent());
        // 5 %/s desde el 100 % hasta el 20 % son 16 s = 320 ticks (con el redondeo, alguno más).
        helper.assertTrue(ticks >= 300 && ticks <= 400, "Bajar del 100 % al 20 % debe tardar unos 320 ticks; tardó " + ticks);
        helper.assertTrue(att.flags().getKiChamberCooldownUntil() > helper.getLevel().getGameTime(),
                "Al cortarse en la reserva el jugador queda en cooldown");
        helper.assertTrue(be.tankType() == KiEnergyType.BASE && be.tank().getFluidAmount() > 0,
                "En Base el tanque debe tener Ki Base; tiene " + be.tankType() + " x" + be.tank().getFluidAmount());
        helper.assertTrue(!be.testStartCharge(sp, false), "En cooldown no se puede volver a cargar sin forzar");
        helper.assertTrue(be.isOpen(), "Al terminar por su cuenta la puerta se abre sola");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void forcingNeverKills(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        att.flags().setKiChamberCooldownUntil(helper.getLevel().getGameTime() + 10_000L);
        att.setBody(Math.min(att.getBodyMax(), 3));
        helper.assertTrue(!be.testStartCharge(sp, false), "En cooldown y sin el ajuste Forzar no arranca");
        helper.assertTrue(be.testStartCharge(sp, true), "En cooldown, con ki y vida, forzar debe arrancar");
        helper.assertTrue(be.isForcing(), "Quien entra en cooldown con Forzar encendido carga forzando");
        int ticks = 0;
        while (be.isCharging() && ticks < 20_000) { be.tickCharge(sp); ticks++; }

        helper.assertTrue(!be.isCharging(), "Forzar debe detenerse solo");
        helper.assertTrue(att.getBody() >= 1, "Forzar nunca deja la vida por debajo de 1; quedó en " + att.getBody());
        helper.assertTrue(sp.isAlive(), "Forzar no mata");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void formChangeCutsTheCharge(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        helper.assertTrue(be.testStartCharge(sp, false), "Debe arrancar");
        for (int i = 0; i < 40; i++) be.tickCharge(sp);
        helper.assertTrue(be.isCharging(), "Tras 2 s sigue cargando");

        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        form.setFormId(FormIds.SSJ1);
        be.tickCharge(sp);

        helper.assertTrue(!be.isCharging(), "Cambiar de forma corta la carga");
        helper.assertTrue(be.tankType() == KiEnergyType.BASE, "Lo ya cargado sigue siendo Ki Base, no se convierte");
        helper.assertTrue(att.flags().getKiChamberCooldownUntil() <= helper.getLevel().getGameTime(),
                "Solo llegar a la reserva deja cooldown: el corte por cambio de forma no");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void forceSettingContinuesPastTheReserve(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        double reserve = att.getKiPool() * ServerConfig.kiChamberReservePercent() / 100.0;

        helper.assertTrue(be.testStartCharge(sp, true), "Con Forzar encendido y ki lleno arranca en modo normal");
        helper.assertTrue(!be.isForcing(), "Por encima de la reserva y sin cooldown NO se fuerza todavía");
        int ticks = 0;
        while (be.isCharging() && !be.isForcing() && ticks < 2000) { be.tickCharge(sp); ticks++; }

        helper.assertTrue(be.isCharging() && be.isForcing(), "Al llegar a la reserva la carga sigue, ya forzando");
        helper.assertTrue(att.flags().getKiChamberCooldownUntil() > helper.getLevel().getGameTime(),
                "Llegar a la reserva deja cooldown aunque se siga forzando");
        while (be.isCharging() && ticks < 20_000) { be.tickCharge(sp); ticks++; }
        helper.assertTrue(att.getKiCurrent() < reserve, "Forzando, el ki baja de la reserva");
        helper.assertTrue(att.getBody() >= 1 && sp.isAlive(), "Y sigue sin matar");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void forcingPaysWithHealthOnlyOnceKiIsZero(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        long now = helper.getLevel().getGameTime();
        att.flags().setKiChamberCooldownUntil(now + 5L);   // corto: lo que quede al final lo pone la cámara
        att.addKi(-(int) Math.ceil(att.getKiCurrent()));
        att.setBody(att.getBodyMax());
        helper.assertTrue(be.testStartCharge(sp, true), "Con ki 0 pero con vida, forzar arranca");
        int bodyBefore = att.getBody();
        for (int i = 0; i < 100 && be.isCharging(); i++) be.tickCharge(sp);
        helper.assertTrue(att.getBody() < bodyBefore, "Sin ki, forzar convierte vida");
        helper.assertTrue(be.tankType() != null && be.fluidHandler().getFluidInTank(0).getAmount() > 0,
                "Y la vida entrega Ki al tanque");
        for (int i = 0; i < 20_000 && be.isCharging(); i++) be.tickCharge(sp);   // hasta que la corte sola (vida en 1 o tanque lleno)
        long left = KiContainmentChamberBlockEntity.cooldownLeft(att.flags(), helper.getLevel().getGameTime());
        helper.assertTrue(left > ServerConfig.kiChamberCooldownSeconds() * 20L,
                "Cuanta más vida entrega, más largo el cooldown; quedan " + left + " ticks");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fluidPortAcceptsOnlyKiAndOneTypeAtATime(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        FluidStack superKi = new FluidStack(ModFluids.KI_SUPER.source().get(), 1000);

        helper.assertTrue(be.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                IFluidHandler.FluidAction.EXECUTE) == 0, "El puerto no acepta fluidos que no sean de Ki");
        helper.assertTrue(be.fluidHandler().fill(superKi.copy(), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "Por tubería sí se puede meter fluido de Ki (para convertirlo a FE)");
        helper.assertTrue(be.fluidHandler().fill(new FluidStack(ModFluids.KI_BASE.source().get(), 500),
                IFluidHandler.FluidAction.EXECUTE) == 0, "Con Ki Súper dentro no entra Ki Base: no se mezclan");
        FluidStack out = be.fluidHandler().drain(400, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(out.getAmount() == 400 && KiEnergyType.ofFluid(out.getFluid()) == KiEnergyType.SUPER,
                "Desde fuera se sacan 400 mB de Ki Súper; salió " + out.getAmount());
        helper.assertTrue(be.tank().getFluidAmount() == 600, "Quedan 600 mB");
        int fe = be.testConvertTick();
        helper.assertTrue(fe > 0, "El fluido metido por tubería se convierte a FE");
        helper.succeed();
    }

    /** Lo drenado al jugador es exactamente lo que hay entre el tanque y lo ya convertido: no se duplica. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void chargingWhileConvertingConservesFluid(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = chargedPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int before = att.getKiCurrent();

        helper.assertTrue(be.testStartCharge(sp, false), "Debe arrancar");
        int piped = 0;
        for (int i = 0; i < 200 && be.isCharging(); i++) {
            be.tickCharge(sp);
            be.testConvertTick();
            piped += be.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
        int drained = before - att.getKiCurrent();
        // Ki Base: 1 FE por mB y 1 mB por ki con los valores por defecto.
        double produced = be.tank().getFluidAmount() + piped
                + be.energyBuffer().get() / (double) KiEnergyType.BASE.fePerMb();
        helper.assertTrue(Math.abs(produced - drained * ServerConfig.kiChamberMbPerKi()) <= 1.5,
                "Tanque + tubería + FE deben sumar lo drenado (" + drained + "); suman " + produced);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void mobGivesBaseKiAndIsNeverKilled(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(
                net.minecraft.world.entity.EntityType.ZOMBIE, POS.offset(2, 0, 0));

        helper.assertTrue(be.testStartMob(zombie), "Un mob con vida puede cargar");
        int ticks = 0;
        while (be.isCharging() && ticks < 2000) { be.testMobTick(zombie); ticks++; }

        helper.assertTrue(!be.isCharging(), "La extracción se detiene sola");
        helper.assertTrue(zombie.isAlive() && zombie.getHealth() >= 1f,
                "El mob queda con 1 de vida, no muere; vida " + zombie.getHealth());
        int expected = (int) ((zombie.getMaxHealth() - 1f) * ServerConfig.kiChamberMbPerMobHealth());
        helper.assertTrue(be.tankType() == KiEnergyType.BASE && Math.abs(be.tank().getFluidAmount() - expected) <= 2,
                "Un zombi da " + expected + " mB de Ki Base; dio " + be.tank().getFluidAmount());
        helper.assertTrue(!be.testStartMob(zombie), "Con 1 de vida ya no se le puede sacar más");
        helper.succeed();
    }

    /** La puerta de verdad: abierta, atrapa al mob que está dentro, se cierra, y al abrirla lo suelta. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void doorCapturesWhoWalksInAndReleasesOnClick(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        helper.assertTrue(!be.isOpen() && be.isFree(), "Recién colocada: cerrada y vacía");

        be.toggleDoor();
        helper.assertTrue(be.isOpen(), "Clic derecho abre la puerta");
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(
                net.minecraft.world.entity.EntityType.ZOMBIE, POS);
        be.testDetect();

        helper.assertTrue(!be.isOpen(), "Con alguien dentro la puerta se cierra sola");
        helper.assertTrue(be.isOccupant(zombie) && be.isCharging(), "El mob queda atrapado y la extracción arranca sola");
        helper.assertTrue(com.hmc.zenkai.feature.combat.StunState.isStunned(zombie), "El ocupante queda sujeto");

        be.toggleDoor();
        helper.assertTrue(be.isOpen() && be.isFree() && !be.isCharging(), "Otro clic abre, corta y libera");
        helper.assertTrue(!com.hmc.zenkai.feature.combat.StunState.isStunned(zombie), "Al liberarlo deja de estar sujeto");
        be.testDetect();
        helper.assertTrue(be.isFree(), "El recién liberado no vuelve a ser atrapado mientras siga dentro");
        helper.succeed();
    }

    // ── Fase 2b: mejoras, sobrecarga y batería ───────────────────────────────

    private static ServerPlayer ssj1Player(GameTestHelper helper) {
        ServerPlayer sp = chargedPlayer(helper);
        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        form.setFormId(FormIds.SSJ1);
        return sp;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void chargingWithoutToleranceHeatsUp(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = ssj1Player(helper);

        helper.assertTrue(be.tolerates(KiEnergyType.BASE), "Base viene tolerada de serie");
        helper.assertTrue(!be.tolerates(KiEnergyType.SUPER), "Sin mejora, el Ki Súper no está tolerado");
        helper.assertTrue(be.testStartCharge(sp, false), "Cargar sin tolerancia NO está prohibido");
        double heat = 0;
        for (int i = 0; i < 20; i++) heat = be.testHeatTick(sp);
        helper.assertTrue(heat > 0.0 && heat < KiContainmentChamberBlockEntity.HEAT_MAX,
                "Un segundo sin tolerancia calienta sin llegar a explotar; calor " + heat);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void toleranceUpgradeKeepsItCold(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = ssj1Player(helper);
        be.upgradeHandler().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_TOL_SUPER,
                new ItemStack(ModItems.KI_TOLERANCE_SUPER.get()));

        helper.assertTrue(be.tolerates(KiEnergyType.SUPER), "Con la mejora, el Ki Súper está tolerado");
        helper.assertTrue(!be.tolerates(KiEnergyType.DIVINE), "La mejora de Súper no sirve para el Divino");
        helper.assertTrue(be.testStartCharge(sp, false), "Debe arrancar");
        double heat = 0;
        for (int i = 0; i < 60; i++) heat = be.testHeatTick(sp);
        helper.assertTrue(heat == 0.0, "Con tolerancia no hay calor; calor " + heat);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void overloadEmptiesTankAndNeverKills(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ServerPlayer sp = ssj1Player(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        be.tank().fill(new FluidStack(ModFluids.KI_SUPER.source().get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        att.setBody(Math.min(att.getBodyMax(), 2));

        // grief=false: una explosión que rompe bloques se llevaría las parcelas de los tests vecinos.
        be.overload(sp, false);

        helper.assertTrue(be.tank().isEmpty(), "La explosión vacía el tanque");
        helper.assertTrue(att.getBody() >= 1, "La sobrecarga no deja la vida por debajo de 1; quedó en " + att.getBody());
        helper.assertTrue(sp.isAlive(), "Quien cargaba no muere por la explosión");
        helper.assertTrue(be.heat() == 0.0, "Tras explotar, el calor vuelve a cero");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void speedAndStorageUpgradesRaiseLimits(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        int baseFe = ServerConfig.kiChamberFeMaxPerTick();
        helper.assertTrue(be.feMaxPerTick() == baseFe, "Sin mejoras, el tope es el base");

        be.upgradeHandler().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_SPEED,
                new ItemStack(ModItems.MACHINE_SPEED_UPGRADE.get(), KiContainmentChamberBlockEntity.MAX_SPEED));
        int expected = (int) (baseFe * Math.pow(ServerConfig.kiChamberSpeedMult(), KiContainmentChamberBlockEntity.MAX_SPEED));
        helper.assertTrue(be.feMaxPerTick() == expected, "Con 3 de velocidad el tope debe ser " + expected + " y es " + be.feMaxPerTick());

        be.upgradeHandler().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_STORAGE,
                new ItemStack(ModItems.MACHINE_STORAGE_UPGRADE.get(), KiContainmentChamberBlockEntity.MAX_STORAGE));
        be.testUpgradeTick();
        double factor = 1.0 + KiContainmentChamberBlockEntity.MAX_STORAGE * ServerConfig.kiChamberStoragePercent() / 100.0;
        helper.assertTrue(be.tank().getCapacity() == (int) (ServerConfig.kiChamberTankCapacity() * factor),
                "Tanque ampliado; capacidad " + be.tank().getCapacity());
        helper.assertTrue(be.energyBuffer().capacity() == (int) (ServerConfig.kiChamberFeCapacity() * factor),
                "Búfer de FE ampliado; capacidad " + be.energyBuffer().capacity());

        helper.assertTrue(!be.upgradeHandler().isItemValid(KiContainmentChamberBlockEntity.SLOT_SPEED,
                new ItemStack(ModItems.MACHINE_STORAGE_UPGRADE.get())), "Cada ranura solo acepta su mejora");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void batteryChargesFromBufferAndExposesCapability(GameTestHelper helper) {
        KiContainmentChamberBlockEntity be = chamber(helper);
        ItemStack battery = new ItemStack(ModItems.ENERGY_BATTERY.get());
        helper.assertTrue(battery.getCapability(Capabilities.EnergyStorage.ITEM) != null,
                "La batería expone la capability estándar de energía");
        be.upgradeHandler().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_BATTERY, battery);
        be.energyBuffer().generate(5_000);
        int before = be.energyBuffer().get();

        be.testUpgradeTick();

        int moved = EnergyBatteryItem.stored(be.upgradeHandler().getStackInSlot(KiContainmentChamberBlockEntity.SLOT_BATTERY));
        int expected = Math.min(ServerConfig.kiChamberFeMaxPerTick(), ServerConfig.batteryMaxTransfer());
        helper.assertTrue(moved >= expected, "Un tick pasa al menos " + expected + " FE a la batería; pasó " + moved);
        helper.assertTrue(be.energyBuffer().get() <= before - expected, "Lo que entra en la batería sale del búfer");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void converterUsesTypeRateAndCap(GameTestHelper helper) {
        int cap = ServerConfig.kiChamberFeMaxPerTick();
        for (KiEnergyType type : KiEnergyType.values()) {
            KiContainmentChamberBlockEntity be = chamber(helper);
            be.tank().fill(new FluidStack(type.fluid().source().get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            int rate = type.fePerMb();
            int expectedMb = cap / rate;
            int fe = be.testConvertTick();
            helper.assertTrue(fe == expectedMb * rate,
                    type + ": un tick debe dar " + (expectedMb * rate) + " FE y dio " + fe);
            helper.assertTrue(fe <= cap, type + ": nunca más del tope por tick");
            helper.assertTrue(be.tank().getFluidAmount() == 1000 - expectedMb,
                    type + ": debe gastar " + expectedMb + " mB");
            helper.setBlock(POS.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }
}
