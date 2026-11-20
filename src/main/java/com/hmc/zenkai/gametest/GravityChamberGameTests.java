package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.event.tick.TickCtx;
import com.hmc.zenkai.event.tick.WeightLoadSystem;
import com.hmc.zenkai.feature.gravity.GravityFields;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cámara de gravedad (0.7.0, Fase 3): la fórmula g → toneladas, la validación de la sala esférica
 * (cerrada, fuga, más grande que el radio, puertos), el consumo de FE, los mobs frenados y el drenaje
 * del jugador que nunca mata. La sala se construye fuera de la plantilla, por eso su prueba va en un
 * lote propio (que nada más se coloque al lado mientras corre).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityChamberGameTests {
    private GravityChamberGameTests() {}

    private static final ResourceLocation MOVE_MOD = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "gravity_move");

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tonsFormulaMatchesDesign(GameTestHelper helper) {
        helper.assertTrue(Math.abs(WeightSystem.tonsForGravity(10) - 39.96) < 1e-6, "x10 debe pesar 39,96 t");
        helper.assertTrue(Math.abs(WeightSystem.tonsForGravity(2) - 4.44) < 1e-6, "x2 (HTC) debe pesar 4,44 t");
        helper.assertTrue(WeightSystem.tonsForGravity(1) == 0.0, "x1 no pesa");
        helper.assertTrue(Math.abs(WeightSystem.tonsForGravity(500) - 2215.56) < 1e-6, "x500 debe pesar 2 215,56 t");
        helper.assertTrue(GravityChamberBlockEntity.costPerTick(10, 5) == 18, "x10 con radio 5 = 18 FE/t");
        helper.assertTrue(GravityChamberBlockEntity.costPerTick(10, 10) == 41, "x10 con radio 10 = 18 x 2,25 = 40,5 -> 41 FE/t");
        helper.assertTrue(GravityChamberBlockEntity.costPerTick(1, 10) == 0, "x1 no gasta");
        helper.succeed();
    }

    /** Sala de radio r alrededor de la consola: interior a aire y corteza de piedra. */
    private static void buildRoom(ServerLevel level, GravityChamberBlockEntity be, int r) {
        be.setRadiusForTests(r);
        BlockPos c = GravityChamberBlockEntity.sphereCenter(be.getBlockPos(), r);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dy = -r - 1; dy <= r + 1; dy++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(be.getBlockPos())) continue;
                    if (GravityChamberBlockEntity.insideSphere(dx, dy, dz, r)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (long k : be.shell()) level.setBlock(BlockPos.of(k), Blocks.STONE.defaultBlockState(), 2);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty", batch = "gravity_room", timeoutTicks = 200)
    public static void sphericalRoomValidation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos console = helper.absolutePos(new BlockPos(1, 12, 1));
        level.setBlock(console, ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState(), 3);
        GravityChamberBlockEntity be = (GravityChamberBlockEntity) level.getBlockEntity(console);
        helper.assertTrue(be != null, "La consola debe tener block entity");

        // 1) Sala cerrada de radio 5.
        buildRoom(level, be, 5);
        be.validate();
        helper.assertTrue(be.isFormed(), "Una esfera cerrada de radio 5 debe formarse (interior " + be.interiorCount()
                + ", fugas " + be.leaks().length + ")");
        helper.assertTrue(be.interiorCount() >= GravityChamberBlockEntity.MIN_INTERIOR, "Interior mínimo");
        helper.assertTrue(be.interiorContains(console.above()), "La celda sobre la consola es interior");

        // 2) Un puerto en la pared: se enlaza y pasa la FE a la consola por su cara exterior.
        BlockPos side = GravityChamberBlockEntity.sphereCenter(console, 5).east(5);
        level.setBlock(side, ModBlocks.GRAVITY_CHAMBER_PORT.get().defaultBlockState(), 3);
        be.validate();
        helper.assertTrue(be.isFormed() && be.portCount() == 1, "El puerto cuenta como pared y se enlaza");
        IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, side, Direction.EAST);
        helper.assertTrue(cap != null, "El puerto enlazado ofrece capability de energía");
        int got = cap.receiveEnergy(1000, false);
        helper.assertTrue(got == 1000 && be.energyStored() == 1000, "La FE del puerto llega a la consola");

        // 3) Un hueco en la pared: fuga y sin sala; el puerto se desenlaza.
        BlockPos hole = GravityChamberBlockEntity.sphereCenter(console, 5).west(5);
        level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
        be.validate();
        helper.assertTrue(!be.isFormed() && be.leaks().length > 0, "Con un hueco hay fuga");
        helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, side, Direction.EAST) == null,
                "Sin sala el puerto no ofrece capability");
        level.setBlock(hole, Blocks.STONE.defaultBlockState(), 3);

        // 4) Sala de radio 6 con el radio elegido en 5: más grande que la esfera = fuga.
        buildRoom(level, be, 6);
        be.setRadiusForTests(5);
        be.validate();
        helper.assertTrue(!be.isFormed(), "Una sala más grande que el radio elegido no vale");
        be.setRadiusForTests(6);
        be.validate();
        helper.assertTrue(be.isFormed(), "Con el radio igual a la sala, vale");

        // 5) Rendimiento: el relleno de la sala mayor (radio 10, unas 3 600 celdas) por debajo de 2 ms (media de 20).
        buildRoom(level, be, 10);
        be.validate();
        helper.assertTrue(be.isFormed(), "La esfera de radio 10 debe formarse");
        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) be.validate();
        double ms = (System.nanoTime() - t0) / 20.0 / 1_000_000.0;
        helper.assertTrue(ms < 2.0, "validate() con radio 10 tarda " + ms + " ms (tope 2 ms)");

        // 6) Energía y g: sin mejora no se activa; con la de cobre, la g se recorta a x10 y gasta lo suyo.
        be.setTargetGravityForTests(50);
        be.fillEnergyForTests(100_000);
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(!be.isActive(), "Sin mejora no genera gravedad");
        be.upgradeHandler().setStackInSlot(0, new ItemStack(ModItems.GRAVITY_UPGRADE_COPPER.get()));
        int before = be.energyStored();
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(be.isActive() && be.effectiveGravity() == 10, "Con cobre, x50 se recorta a x10");
        helper.assertTrue(before - be.energyStored() == GravityChamberBlockEntity.costPerTick(10, 10),
                "Gasta 2·(g−1)·(1 + 0,25·(R−5)) FE en el tick");

        // 7) Mobs: un zombi dentro se frena; uno del tag de inmunes no; al salir pierde el freno.
        BlockPos in = console.above().east(2);
        Mob zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5);
        level.addFreshEntity(zombie);
        Mob namek = ModEntities.NAMEKIAN.get().create(level);
        namek.moveTo(in.getX() - 3.5, in.getY(), in.getZ() + 0.5);
        level.addFreshEntity(namek);
        be.tickEntitiesForTests();
        helper.assertTrue(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MOVE_MOD) != null,
                "El zombi dentro debe quedar frenado");
        helper.assertTrue(namek.getAttribute(Attributes.MOVEMENT_SPEED) == null
                        || namek.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MOVE_MOD) == null,
                "Un NPC del tag gravity_immune no se frena");
        zombie.moveTo(console.getX() + 40.5, console.getY(), console.getZ() + 0.5);
        be.tickEntitiesForTests();
        helper.assertTrue(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MOVE_MOD) == null,
                "Al salir el zombi pierde el freno");
        zombie.discard();
        namek.discard();

        // 8) El interruptor de la pantalla apaga la gravedad sin gastar y la vuelve a encender.
        be.toggleEnabled();
        int beforeOff = be.energyStored();
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(!be.isActive() && be.energyStored() == beforeOff, "Apagada no genera ni gasta");
        be.toggleEnabled();
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(be.isActive(), "Al encenderla vuelve a generar");

        // 9) Sin energía la g vuelve a 1 al instante.
        be.fillEnergyForTests(0);
        GravityChamberBlockEntity.serverTick(level, console, level.getBlockState(console), be);
        helper.assertTrue(!be.isActive(), "Sin FE la cámara se apaga");
        level.setBlock(console, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    private static TickCtx ctx(ServerPlayer sp) {
        return new TickCtx(sp, PlayerStatsAttachment.get(sp),
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()),
                sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()));
    }

    /** La forma multiplica la capacidad ENTERA (pedido del usuario, 2026-11-20): ×2,5 aguanta 2,5 veces más. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void capacityScalesWithForm(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        double old = att.getStatMultiplier();
        try {
            att.setStatMultiplier(1.0);
            double base = WeightSystem.playerCapacity(att);
            att.setStatMultiplier(2.5);
            double ssj = WeightSystem.playerCapacity(att);
            helper.assertTrue(Math.abs(ssj / base - 2.5) < 1e-6, "Con ×2,5 la capacidad debe ser ×2,5 y es ×" + ssj / base);
            helper.assertTrue(Math.abs(base - WeightSystem.capacityTons(att.getPowerLevelWithStatMultiplier(1.0))) < 1e-9,
                    "En base la capacidad no cambia respecto a la fórmula de siempre");
        } finally {
            att.setStatMultiplier(old);
        }
        helper.succeed();
    }

    /** El drenaje crece con lo que te pasas de tu capacidad y tiene techo (pedido del usuario, 2026-11-20). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void drainIsProportionalToExcessLoad(GameTestHelper helper) {
        double threshold = ServerConfig.gravityChamberDrainLoad();
        helper.assertTrue(WeightSystem.drainPercentPerSecond(threshold) == 0.0, "En el umbral no hay drenaje");
        double a = WeightSystem.drainPercentPerSecond(threshold + 1.0);
        double b = WeightSystem.drainPercentPerSecond(threshold + 4.0);
        helper.assertTrue(a > 0.0 && b > a, "Más carga, más drenaje (" + a + " -> " + b + ")");
        helper.assertTrue(WeightSystem.drainPercentPerSecond(10_000.0) <= ServerConfig.gravityChamberDrainMaxPercent() + 1e-9,
                "El drenaje respeta su techo");
        helper.succeed();
    }

    /** El TP escala con la g de la cámara (log), no en la HTC/Kaiosama y no en sobrecarga (2026-11-20). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tpScalesWithChamberGravity(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        long now = helper.getLevel().getGameTime();
        try {
            helper.assertTrue(WeightSystem.gravityTpFactor(sp, 0.5) == 1.0, "Fuera de cámara no hay término de g");
            GravityFields.refresh(sp, 10, now);
            double g10 = WeightSystem.gravityTpFactor(sp, 0.5);
            GravityFields.refresh(sp, 100, now);
            double g100 = WeightSystem.gravityTpFactor(sp, 0.5);
            helper.assertTrue(g10 > 1.0 && g100 > g10, "A más g, más TP (" + g10 + " -> " + g100 + ")");
            helper.assertTrue(WeightSystem.gravityTpFactor(sp, 50.0) == 1.0, "En sobrecarga no entrena");
        } finally {
            GravityFields.forget(sp.getUUID());
        }
        helper.succeed();
    }

    /** Las pesas puestas pesan equipo × g: el peso es masa por gravedad (pedido del usuario, 2026-11-20). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void equippedWeightsScaleWithGravity(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        long now = helper.getLevel().getGameTime();
        try {
            double at1 = WeightSystem.effectiveEquippedTons(sp);
            GravityFields.refresh(sp, 10, now);
            double at10 = WeightSystem.effectiveEquippedTons(sp);
            helper.assertTrue(Math.abs(at10 - WeightSystem.equippedTons(sp) * 10.0) < 1e-9 && Math.abs(at1 - WeightSystem.equippedTons(sp)) < 1e-9,
                    "Las pesas a ×10 deben pesar diez veces su valor");
        } finally {
            GravityFields.forget(sp.getUUID());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void chamberDrainStopsAtOneAndRespectsGamerule(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setBody(att.getBodyMax());
        long now = helper.getLevel().getGameTime();

        // Fuera de una cámara: nada.
        for (int i = 0; i < 200; i++) WeightLoadSystem.tick(ctx(sp));
        helper.assertTrue(att.getBody() == att.getBodyMax(), "Sin cámara no hay drenaje");

        // Dentro a x500 (r muy por encima de 2): baja hasta 1 y se queda ahí.
        GravityFields.refresh(sp, 500, now);
        helper.assertTrue(WeightSystem.computeLoad(sp) > ServerConfig.gravityChamberDrainLoad(), "r debe pasar de 2");
        for (int i = 0; i < 2500; i++) WeightLoadSystem.tick(ctx(sp));
        helper.assertTrue(att.getBody() == 1, "El drenaje se para en 1, nunca mata (body " + att.getBody() + ")");
        helper.assertTrue(sp.isAlive(), "Sigue vivo");

        // Con la gamerule apagada no drena.
        att.setBody(att.getBodyMax());
        helper.getLevel().getGameRules().getRule(ModGameRules.GRAVITY_DAMAGE).set(false, helper.getLevel().getServer());
        try {
            for (int i = 0; i < 400; i++) WeightLoadSystem.tick(ctx(sp));
            helper.assertTrue(att.getBody() == att.getBodyMax(), "Con zenkai_gravityDamage apagada no hay drenaje");
        } finally {
            helper.getLevel().getGameRules().getRule(ModGameRules.GRAVITY_DAMAGE).set(true, helper.getLevel().getServer());
            GravityFields.forget(sp.getUUID());
        }
        helper.succeed();
    }
}
