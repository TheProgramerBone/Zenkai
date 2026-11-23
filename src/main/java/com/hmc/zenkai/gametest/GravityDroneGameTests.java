package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.content.entity.misc.TrainingDroneEntity;
import com.hmc.zenkai.content.item.TrainingDroneItem;
import com.hmc.zenkai.feature.gravity.DroneDifficulty;
import com.hmc.zenkai.feature.gravity.GravityDroneRound;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Drones de entrenamiento (0.7.0, Fase 4): ronda pasiva completa (TP, usos gastados, dron agotado que se
 * rompe, ranuras bloqueadas), golpes de otro que no cuentan, cancelación al salir de la sala (sin TP) y
 * ronda agresiva (golpes contados, paga el múltiplo). Mismo lote que la sala de la Fase 3 porque también
 * construye fuera de la plantilla.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityDroneGameTests {
    private GravityDroneGameTests() {}

    private static List<TrainingDroneEntity> drones(ServerLevel level, BlockPos console) {
        return level.getEntitiesOfClass(TrainingDroneEntity.class, new AABB(console).inflate(12), e -> e.isAlive());
    }

    private static void hit(TrainingDroneEntity d, ServerPlayer sp) {
        ZenkaiGameTestHelpers.invokeHandler(() -> d.hurt(sp.damageSources().playerAttack(sp), 5f));
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty", batch = "gravity_room", timeoutTicks = 200)
    public static void droneRounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos console = helper.absolutePos(new BlockPos(1, 12, 1));
        level.setBlock(console, ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState(), 3);
        GravityChamberBlockEntity be = (GravityChamberBlockEntity) level.getBlockEntity(console);
        helper.assertTrue(be != null, "La consola debe tener block entity");
        GravityChamberGameTests.buildRoom(level, be, 5);
        be.validate();
        helper.assertTrue(be.isFormed(), "La sala de radio 5 debe formarse");
        be.upgradeHandler().setStackInSlot(0, new ItemStack(ModItems.GRAVITY_UPGRADE_COPPER.get()));
        be.setTargetGravityForTests(10);
        be.fillEnergyForTests(150_000);
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(be.isActive(), "La cámara debe generar ×10");
        helper.assertTrue(be.maxDrones() == 3, "Radio 5 → 3 drones a la vez");

        // Cuatro drones: el primero con un solo uso. Caben 3 a la vez.
        ItemStack worn = new ItemStack(ModItems.TRAINING_DRONE.get());
        worn.setDamageValue(ServerConfig.DRONE_MAX_USES - 1);
        be.droneHandler().setStackInSlot(0, worn);
        for (int i = 1; i < 4; i++) be.droneHandler().setStackInSlot(i, new ItemStack(ModItems.TRAINING_DRONE.get()));

        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        BlockPos inside = console.east();
        sp.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        helper.assertTrue(be.interiorContains(sp.blockPosition()), "El jugador debe estar dentro de la sala");

        // 1) Ronda pasiva completa.
        String err = be.droneRound().start(level, sp, false, DroneDifficulty.NORMAL);
        helper.assertTrue(err == null, "La ronda debe empezar: " + err);
        helper.assertTrue(be.droneRound().total() == 3, "Salen 3 drones (radio − 2), no 4");
        helper.assertTrue(be.droneHandler().extractItem(1, 1, false).isEmpty(), "Las ranuras se bloquean durante la ronda");
        List<TrainingDroneEntity> list = drones(level, console);
        helper.assertTrue(list.size() == 3, "Debe haber 3 entidades y hay " + list.size());
        // Un golpe que no es del dueño no cuenta.
        ZenkaiGameTestHelpers.invokeHandler(() -> list.get(0).hurt(level.damageSources().generic(), 5f));
        helper.assertTrue(be.droneRound().doneCount() == 0, "Un golpe ajeno no cuenta");
        int tpBefore = att.getTP();
        for (TrainingDroneEntity d : list) hit(d, sp);
        helper.assertTrue(!be.droneRound().running() && be.droneRound().lastResult() == GravityDroneRound.RESULT_SUCCESS,
                "Golpeados los 3, la ronda termina con éxito");
        helper.assertTrue(att.getTP() > tpBefore && be.droneRound().lastTp() == att.getTP() - tpBefore,
                "La ronda completa da TP (" + tpBefore + " -> " + att.getTP() + ")");
        helper.assertTrue(be.droneHandler().getStackInSlot(0).isEmpty(), "El dron con un uso se rompe al gastarlo");
        helper.assertTrue(TrainingDroneItem.usesLeft(be.droneHandler().getStackInSlot(1)) == ServerConfig.DRONE_MAX_USES - 1,
                "Los demás gastan un uso");
        helper.assertTrue(TrainingDroneItem.usesLeft(be.droneHandler().getStackInSlot(3)) == ServerConfig.DRONE_MAX_USES,
                "El cuarto no salió: no gasta");
        helper.assertTrue(drones(level, console).isEmpty(), "Al terminar, las entidades desaparecen");
        int passiveTp = be.droneRound().lastTp();

        // 2) Cancelada al salir de la sala: sin TP, y el golpeado antes de salir gasta su uso igual.
        helper.assertTrue(be.droneRound().start(level, sp, false, DroneDifficulty.NORMAL) == null, "Segunda ronda");
        List<TrainingDroneEntity> second = drones(level, console);
        int usesFirst = 0;
        for (int i = 0; i < 4; i++) usesFirst += TrainingDroneItem.usesLeft(be.droneHandler().getStackInSlot(i));
        hit(second.get(0), sp);
        int tpMid = att.getTP();
        sp.moveTo(console.getX() + 40.5, console.getY(), console.getZ() + 0.5);
        be.droneRound().tick(level, level.getGameTime());
        helper.assertTrue(!be.droneRound().running() && be.droneRound().lastResult() == GravityDroneRound.RESULT_CANCELLED,
                "Salir de la sala cancela la ronda");
        helper.assertTrue(att.getTP() == tpMid, "Una ronda cancelada no da TP");
        int usesAfter = 0;
        for (int i = 0; i < 4; i++) usesAfter += TrainingDroneItem.usesLeft(be.droneHandler().getStackInSlot(i));
        helper.assertTrue(usesAfter == usesFirst - 1, "El dron golpeado antes de cancelar gasta su uso");
        helper.assertTrue(drones(level, console).isEmpty(), "Al cancelar, los drones vuelven a la máquina");

        // 3) Ronda agresiva: hacen falta varios golpes por dron y paga el múltiplo.
        sp.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        helper.assertTrue(be.droneRound().start(level, sp, true, DroneDifficulty.EASY) == null, "Ronda agresiva (fácil: 2 golpes)");
        List<TrainingDroneEntity> third = drones(level, console);
        hit(third.get(0), sp);
        helper.assertTrue(third.get(0).isAlive() && be.droneRound().doneCount() == 0, "Un golpe no basta en agresivo");
        for (TrainingDroneEntity d : third) {
            for (int h = 0; h < DroneDifficulty.EASY.hitsToBreak; h++) if (d.isAlive()) hit(d, sp);
        }
        helper.assertTrue(be.droneRound().lastResult() == GravityDroneRound.RESULT_SUCCESS, "Destruidos todos, éxito");
        helper.assertTrue(GravityDroneRound.rawTp(10, 3, true, DroneDifficulty.NORMAL)
                        == GravityDroneRound.rawTp(10, 3, false, DroneDifficulty.NORMAL) * ServerConfig.droneAggressiveTpMult(),
                "El agresivo paga el múltiplo del pasivo");
        helper.assertTrue(Math.abs(GravityDroneRound.rawTp(30, 6, false, DroneDifficulty.EXTREME) - 450.0) < 1e-9,
                "Tabla aprobada: ×30, 6 drones, pasivo extremo = 450 de TP base");
        helper.assertTrue(passiveTp > 0, "La pasiva pagó algo");

        // 3b) Fuera de combate en agresivo: un disparo te deja en 1 → la ronda acaba y paga SOLO los ya destruidos.
        helper.assertTrue(be.droneRound().start(level, sp, true, DroneDifficulty.EASY) == null, "Ronda agresiva para el derribo");
        List<TrainingDroneEntity> fourth = drones(level, console);
        int total4 = fourth.size();
        for (int h = 0; h < DroneDifficulty.EASY.hitsToBreak; h++) hit(fourth.get(0), sp);
        helper.assertTrue(be.droneRound().doneCount() == 1, "Un dron destruido antes del derribo");
        att.setBody(2);
        int tpBeforeKo = att.getTP();
        TrainingDroneEntity shooter = fourth.get(1);
        ZenkaiGameTestHelpers.invokeHandler(() -> {
            if (com.hmc.zenkai.feature.gravity.DroneShotDamage.apply(sp, 1f, DroneDifficulty.EASY.shotBodyPercent)) shooter.onOwnerKnockedOut(sp);
        });
        helper.assertTrue(att.getBody() == 1, "El disparo deja en 1, no mata");
        helper.assertTrue(!be.droneRound().running() && be.droneRound().lastResult() == GravityDroneRound.RESULT_DEFEATED,
                "A 1 de body la ronda termina como derribo");
        helper.assertTrue(att.getTP() > tpBeforeKo && be.droneRound().lastTp() == att.getTP() - tpBeforeKo,
                "El derribo paga el TP de lo destruido (" + tpBeforeKo + " -> " + att.getTP() + ", 1 de " + total4 + ")");
        helper.assertTrue(drones(level, console).isEmpty(), "Tras el derribo los drones vuelven a la máquina");

        // 4) Daño de un impacto directo: el % de la dificultad sobre el body máximo, y nunca baja de 1.
        att.setBody(att.getBodyMax());
        int max = att.getBodyMax();
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.gravity.DroneShotDamage.apply(sp, 1f,
                DroneDifficulty.EXTREME.shotBodyPercent));
        int expected = max - (int) Math.ceil(max * DroneDifficulty.EXTREME.shotBodyPercent / 100.0);
        helper.assertTrue(att.getBody() == expected, "Impacto directo en Extremo: " + max + " -> " + att.getBody() + " (esperado " + expected + ")");
        att.setBody(2);
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.gravity.DroneShotDamage.apply(sp, 1f, 100.0));
        helper.assertTrue(att.getBody() == 1, "El disparo nunca baja de 1");
        att.setBody(max);

        level.setBlock(console, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }
}
