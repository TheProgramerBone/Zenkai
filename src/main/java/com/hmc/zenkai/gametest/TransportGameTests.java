package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.TransportGeometry;
import com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import com.hmc.zenkai.feature.transport.Look;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cables, tuberías y tanques: la red compartida de los cables (capacidad sumada, cuotas al partirla,
 * fluidos que no se mezclan), el equilibrado entre vecinos, los modos de cara (normal, empujar,
 * tirar, nada), qué aspecto (Look) y qué forma resultan de cada modo, y a qué cara se asigna un clic.
 * Los block entities se tickean a mano: así el test no depende de cuántos ticks lleve el mundo.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransportGameTests {
    private TransportGameTests() {}

    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(2, 1, 1);
    private static final BlockPos C = new BlockPos(3, 1, 1);

    private static void tickEnergy(GameTestHelper h, BlockPos... all) {
        for (BlockPos p : all) {
            EnergyTransportBlockEntity be = h.getBlockEntity(p);
            EnergyTransportBlockEntity.serverTick(h.getLevel(), h.absolutePos(p), h.getBlockState(p), be);
        }
    }

    private static void tickFluid(GameTestHelper h, BlockPos... all) {
        for (BlockPos p : all) {
            FluidTransportBlockEntity be = h.getBlockEntity(p);
            FluidTransportBlockEntity.serverTick(h.getLevel(), h.absolutePos(p), h.getBlockState(p), be);
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void energyFlowsFromTankThroughCableToTank(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(B, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(C, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity src = h.getBlockEntity(A);
        EnergyTransportBlockEntity dst = h.getBlockEntity(C);
        src.energyAt(null).receiveEnergy(1_000_000, false);
        int total = src.energy();

        for (int i = 0; i < 400; i++) tickEnergy(h, A, B, C);

        h.assertTrue(dst.energy() > 0, "La energía debe llegar al tanque del otro extremo");
        h.assertTrue(src.energy() + dst.energy() + ((EnergyTransportBlockEntity) h.getBlockEntity(B)).energy() == total,
                "No se crea ni se pierde energía por el camino");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void sideModesGateTheTransfer(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(B, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity a = h.getBlockEntity(A);
        EnergyTransportBlockEntity b = h.getBlockEntity(B);
        a.energyAt(null).receiveEnergy(100_000, false);

        // A empuja pero B no acepta (PUSH solo entrega): no pasa nada.
        a.cycleMode(Direction.EAST);   // NORMAL -> PUSH
        b.cycleMode(Direction.WEST);   // NORMAL -> PUSH
        for (int i = 0; i < 50; i++) tickEnergy(h, A, B);
        h.assertTrue(b.energy() == 0, "PUSH frente a PUSH no transfiere");

        // B pasa a PULL (NORMAL -> PUSH -> PULL): ahora sí recibe.
        b.cycleMode(Direction.WEST);
        for (int i = 0; i < 50; i++) tickEnergy(h, A, B);
        h.assertTrue(b.energy() > 0, "PUSH frente a PULL transfiere");

        // Desconectar corta el flujo y la capability de esa cara.
        int before = b.energy();
        a.cycleMode(Direction.EAST);   // PUSH -> PULL
        a.cycleMode(Direction.EAST);   // PULL -> NONE
        for (int i = 0; i < 50; i++) tickEnergy(h, A, B);
        h.assertTrue(b.energy() == before, "Una cara NONE no transfiere");
        h.assertTrue(a.energyAt(Direction.EAST) == null, "Una cara que no es NORMAL no expone capability");
        h.assertTrue(a.energyAt(Direction.WEST) != null, "Una cara NORMAL sí");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pullModeTakesFromNeighbour(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(B, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity tank = h.getBlockEntity(A);
        EnergyTransportBlockEntity cable = h.getBlockEntity(B);
        tank.energyAt(null).receiveEnergy(50_000, false);
        // Tanque en PUSH hacia el este y cable en PULL hacia el oeste: tira con el caudal del cable.
        tank.cycleMode(Direction.EAST);
        cable.cycleMode(Direction.WEST);
        cable.cycleMode(Direction.WEST);
        tickEnergy(h, B);
        h.assertTrue(cable.energy() > 0 && cable.energy() <= cable.rate(), "Tira de él con el tope de caudal del cable");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fluidsEqualizeAndDoNotMix(GameTestHelper h) {
        h.setBlock(A, ModBlocks.FLUID_TANK.get());
        h.setBlock(B, ModBlocks.FLUID_PIPE.get());
        h.setBlock(C, ModBlocks.FLUID_TANK.get());
        FluidTransportBlockEntity src = h.getBlockEntity(A);
        FluidTransportBlockEntity dst = h.getBlockEntity(C);
        src.tank().fill(new FluidStack(Fluids.WATER, 20_000), IFluidHandler.FluidAction.EXECUTE);
        dst.tank().fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE);

        for (int i = 0; i < 400; i++) tickFluid(h, A, B, C);
        h.assertTrue(dst.tank().getFluid().getFluid() == Fluids.LAVA && dst.tank().getFluidAmount() == 1_000,
                "Un tanque con otro fluido no recibe agua");
        h.assertTrue(((FluidTransportBlockEntity) h.getBlockEntity(B)).tank().getFluidAmount() > 0,
                "La tubería sí se llena con el fluido compatible");

        // Por la capability: con el grupo ya formado, escribir en tank() no lo vería su búfer.
        dst.fluidAt(null).drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 400; i++) tickFluid(h, A, B, C);
        h.assertTrue(dst.tank().getFluid().getFluid() == Fluids.WATER && dst.tank().getFluidAmount() > 0,
                "Vacío, el tanque recibe el agua");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cableLookFollowsConnectionsAndModes(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(B, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity be = h.getBlockEntity(A);
        be.recomputeLook();

        h.assertTrue(be.look(Direction.EAST) == Look.NORMAL, "El cable dibuja un brazo normal hacia el tanque vecino");
        h.assertTrue(be.look(Direction.WEST) == Look.OFF, "Y ninguno hacia donde no hay nada");
        be.cycleMode(Direction.EAST);   // PUSH
        h.assertTrue(be.look(Direction.EAST) == Look.PUSH, "En modo Empujar el brazo cambia a su variante");
        be.cycleMode(Direction.EAST);   // PULL
        h.assertTrue(be.look(Direction.EAST) == Look.PULL, "En modo Tirar, a la suya");
        be.cycleMode(Direction.EAST);   // NONE
        h.assertTrue(be.mode(Direction.EAST) == SideMode.NONE, "Tres clics dejan la cara desconectada");
        h.assertTrue(be.look(Direction.EAST) == Look.OFF, "Desconectada, el brazo desaparece");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tankLooksShowTheirMode(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity be = h.getBlockEntity(A);
        be.recomputeLook();
        h.assertTrue(be.look(Direction.UP) == Look.NORMAL, "Una cara nueva se ve en Normal");
        be.cycleMode(Direction.UP);
        h.assertTrue(be.look(Direction.UP) == Look.PUSH, "Empujar enseña la placa de salida");
        be.cycleMode(Direction.UP);
        h.assertTrue(be.look(Direction.UP) == Look.PULL, "Tirar, la de entrada");
        be.cycleMode(Direction.UP);
        h.assertTrue(be.look(Direction.UP) == Look.NONE, "Y desactivada, la apagada");
        h.assertTrue(be.look(Direction.DOWN) == Look.NORMAL, "Las otras caras no cambian");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pushAndPullArmsShowWithoutANeighbour(GameTestHelper h) {
        h.setBlock(A, ModBlocks.FLUID_PIPE.get());
        FluidTransportBlockEntity be = h.getBlockEntity(A);
        be.recomputeLook();
        h.assertTrue(be.look(Direction.NORTH) == Look.OFF, "Sin vecino y en Normal no hay brazo");
        be.cycleMode(Direction.NORTH);
        h.assertTrue(be.look(Direction.NORTH) == Look.PUSH, "Empujar se ve aunque no haya nada delante");
        be.cycleMode(Direction.NORTH);
        h.assertTrue(be.look(Direction.NORTH) == Look.PULL, "Tirar también");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void clickPicksTheArmOrTheFaceHit(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity be = h.getBlockEntity(A);
        be.cycleMode(Direction.NORTH);   // brazo de empujar hacia el norte (se ve aunque no haya vecino)
        BlockPos pos = be.getBlockPos();
        Vec3 origin = Vec3.atLowerCornerOf(pos);

        // Sobre la cara de ARRIBA del brazo norte (z entre 0 y 5/16): el clic es del brazo, no de «arriba».
        Direction onArm = be.sideAt(new BlockHitResult(origin.add(0.5, 11 / 16.0, 3 / 16.0), Direction.UP, pos, false));
        h.assertTrue(onArm == Direction.NORTH, "Pulsar el lado de un brazo elige ese brazo; salió " + onArm);

        // Sobre la punta del brazo.
        Direction onTip = be.sideAt(new BlockHitResult(origin.add(0.5, 0.5, 0.0), Direction.NORTH, pos, false));
        h.assertTrue(onTip == Direction.NORTH, "La punta también");

        // Sobre el núcleo (z entre 5/16 y 11/16): la cara que se toca.
        Direction onCore = be.sideAt(new BlockHitResult(origin.add(0.5, 11 / 16.0, 0.5), Direction.UP, pos, false));
        h.assertTrue(onCore == Direction.UP, "Pulsar el núcleo elige la cara tocada; salió " + onCore);

        // Y sobre un tanque siempre la cara.
        h.setBlock(B, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity tank = h.getBlockEntity(B);
        Direction onTank = tank.sideAt(new BlockHitResult(Vec3.atLowerCornerOf(tank.getBlockPos()).add(0.5, 1.0, 0.5),
                Direction.UP, tank.getBlockPos(), false));
        h.assertTrue(onTank == Direction.UP, "En un tanque, la cara tocada");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cableShapeGrowsWithItsArms(GameTestHelper h) {
        var bare = TransportGeometry.cableShape(0);
        h.setBlock(A, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity be = h.getBlockEntity(A);
        be.cycleMode(Direction.EAST);   // PUSH
        var withArm = TransportGeometry.cableShape(be.lookBits());
        h.assertTrue(withArm.max(Direction.Axis.X) > bare.max(Direction.Axis.X), "Con un brazo al este la forma llega más lejos");
        be.cycleMode(Direction.EAST);   // PULL: el collar es más ancho
        var withCollar = TransportGeometry.cableShape(be.lookBits());
        h.assertTrue(withCollar.min(Direction.Axis.Y) < withArm.min(Direction.Axis.Y), "El collar de tirar es más ancho que el tubo");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tanksKeepContentAndModesInTheirItem(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(B, ModBlocks.FLUID_TANK.get());
        EnergyTransportBlockEntity e = h.getBlockEntity(A);
        FluidTransportBlockEntity f = h.getBlockEntity(B);
        e.energyAt(null).receiveEnergy(5_000, false);
        e.cycleMode(Direction.NORTH);                       // PUSH
        f.tank().fill(new FluidStack(Fluids.WATER, 7_000), IFluidHandler.FluidAction.EXECUTE);

        // El ciclo real: collectComponents al romper y applyComponentsFromItemStack al colocar.
        var stackE = new net.minecraft.world.item.ItemStack(ModBlocks.ENERGY_TANK.get());
        stackE.applyComponents(e.collectComponents());
        var stackF = new net.minecraft.world.item.ItemStack(ModBlocks.FLUID_TANK.get());
        stackF.applyComponents(f.collectComponents());

        h.setBlock(C, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity e2 = h.getBlockEntity(C);
        e2.applyComponentsFromItemStack(stackE);
        h.assertTrue(e2.energy() == 5_000, "El tanque de energía recupera sus FE; tiene " + e2.energy());
        h.assertTrue(e2.mode(Direction.NORTH) == SideMode.PUSH, "Y los modos de sus caras");
        e2.recomputeLook();
        h.assertTrue(e2.look(Direction.NORTH) == Look.PUSH, "Y su aspecto se actualiza con ellos");

        h.setBlock(C, ModBlocks.FLUID_TANK.get());
        FluidTransportBlockEntity f2 = h.getBlockEntity(C);
        f2.applyComponentsFromItemStack(stackF);
        h.assertTrue(f2.tank().getFluid().getFluid() == Fluids.WATER && f2.tank().getFluidAmount() == 7_000,
                "El tanque de fluidos recupera su agua");

        h.setBlock(C, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity cable = h.getBlockEntity(C);
        cable.energyAt(null).receiveEnergy(100, false);
        h.assertTrue(cable.collectComponents().isEmpty(), "Un cable no lleva nada en su ítem (así siguen apilándose)");
        h.succeed();
    }

    private static final BlockPos D = new BlockPos(4, 1, 1);

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cablesShareOneBufferWithSummedCapacity(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(B, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(C, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity a = h.getBlockEntity(A);
        EnergyTransportBlockEntity b = h.getBlockEntity(B);
        EnergyTransportBlockEntity c = h.getBlockEntity(C);
        int one = a.capacity();

        // Más de lo que cabe en un cable, metido entero por un extremo (con el caudal por llamada).
        int want = one + one / 2, put = 0;
        for (int i = 0; i < 20 && put < want; i++) put += a.energyAt(null).receiveEnergy(want - put, false);

        h.assertTrue(a.energyNetwork() != null && a.energyNetwork() == c.energyNetwork(), "Los tres cables forman UNA red");
        h.assertTrue(a.energyNetwork().capacity() == 3L * one, "La capacidad de la red es la suma de la de sus cables");
        h.assertTrue(put == want, "Cabe más que en un solo cable; entró " + put + " de " + want);
        h.assertTrue(c.energyAt(null).getEnergyStored() == want, "Lo que entra por un extremo está en el otro al momento");
        h.assertTrue(a.energy() + b.energy() + c.energy() == want, "Las cuotas suman el búfer de la red");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void breakingACableSplitsTheNetworkAndKeepsShares(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(B, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(C, ModBlocks.ENERGY_CABLE.get());
        EnergyTransportBlockEntity a = h.getBlockEntity(A);
        EnergyTransportBlockEntity c = h.getBlockEntity(C);
        int put = 0;
        for (int i = 0; i < 20 && put < 9_000; i++) put += a.energyAt(null).receiveEnergy(9_000 - put, false);
        int middle = ((EnergyTransportBlockEntity) h.getBlockEntity(B)).energy();

        h.setBlock(B, net.minecraft.world.level.block.Blocks.AIR);
        h.assertTrue(a.energyNetwork() != c.energyNetwork(), "Sin el cable del medio quedan dos redes");
        h.assertTrue(a.energy() + c.energy() + middle == put,
                "Cada trozo conserva su cuota y solo se pierde la del cable roto");
        h.assertTrue(a.energyNetwork().capacity() == a.capacity(), "Y cada red tiene la capacidad de lo que le queda");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pipesWithDifferentFluidsDoNotJoin(GameTestHelper h) {
        h.setBlock(A, ModBlocks.FLUID_PIPE.get());
        h.setBlock(B, ModBlocks.FLUID_PIPE.get());
        h.setBlock(C, ModBlocks.FLUID_PIPE.get());
        FluidTransportBlockEntity a = h.getBlockEntity(A);
        FluidTransportBlockEntity b = h.getBlockEntity(B);
        FluidTransportBlockEntity c = h.getBlockEntity(C);
        // Cuotas puestas antes de que exista ninguna red: agua, vacía, lava.
        a.rawTank().setFluid(new FluidStack(Fluids.WATER, 500));
        c.rawTank().setFluid(new FluidStack(Fluids.LAVA, 500));

        h.assertTrue(a.fluidNetwork() != null && a.fluidNetwork() == b.fluidNetwork(), "La tubería vacía se une al agua");
        h.assertTrue(c.fluidNetwork() != a.fluidNetwork(), "La de lava no se une a la red de agua");
        h.assertTrue(a.fluidNetwork().fluid().getFluid() == Fluids.WATER && c.fluidNetwork().fluid().getFluid() == Fluids.LAVA,
                "Y ningún fluido se mezcla ni se pierde");
        b.recomputeLook();
        h.assertTrue(b.look(Direction.EAST) == Look.OFF, "Entre las dos no se dibuja brazo");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void networkFeedsATankAtTheFarEnd(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(B, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(C, ModBlocks.ENERGY_CABLE.get());
        h.setBlock(D, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity src = h.getBlockEntity(A);
        EnergyTransportBlockEntity dst = h.getBlockEntity(D);
        src.energyAt(null).receiveEnergy(1_000_000, false);
        int total = src.energy();
        // Solo el líder mueve la red: tickear los dos cables no la mueve dos veces.
        for (int i = 0; i < 200; i++) tickEnergy(h, A, B, C, D);
        EnergyTransportBlockEntity b = h.getBlockEntity(B);
        EnergyTransportBlockEntity c = h.getBlockEntity(C);
        h.assertTrue(dst.energy() > 0, "La energía cruza la red hasta el tanque del otro extremo");
        h.assertTrue(src.energy() + dst.energy() + b.energy() + c.energy() == total, "Sin crear ni perder energía");
        h.succeed();
    }

    // ── Tanques multibloque (al estilo Create) ──────────────────────────────

    private static void fill(GameTestHelper h, Block block, int w, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < w; x++) for (int z = 0; z < w; z++) {
            h.setBlock(new BlockPos(1 + x, 1 + y, 1 + z), block);
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tanksFormASquarePrismWithSummedCapacity(GameTestHelper h) {
        fill(h, ModBlocks.ENERGY_TANK.get(), 2, 2);
        EnergyTransportBlockEntity corner = h.getBlockEntity(new BlockPos(1, 1, 1));
        EnergyTransportBlockEntity far = h.getBlockEntity(new BlockPos(2, 2, 2));
        var group = corner.energyNetwork();
        h.assertTrue(group != null && group == far.energyNetwork() && group.size() == 8, "Un 2x2x2 es UN tanque de 8 bloques");
        h.assertTrue(group.capacity() == 8L * corner.capacity(), "Con la capacidad de los 8 sumada");

        // Textura conectada: en la cara norte de la esquina mínima, la izquierda (este) y arriba siguen
        // en el grupo; la derecha (oeste) y abajo son borde.
        int north = (corner.ctBits() >> (4 * Direction.NORTH.get3DDataValue())) & 15;
        h.assertTrue(north == (TransportBlockEntity.CT_RIGHT | TransportBlockEntity.CT_BOTTOM),
                "Bordes de la cara norte de la esquina: " + north);
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tankShapesFollowCreateRules(GameTestHelper h) {
        // Cuatro en fila: la base no es cuadrada, así que son cuatro tanques sueltos.
        for (int x = 0; x < 4; x++) h.setBlock(new BlockPos(1 + x, 1, 1), ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity row = h.getBlockEntity(new BlockPos(1, 1, 1));
        h.assertTrue(row.energyNetwork().size() == 1, "En fila no se unen");

        // Una columna de 6: sin límite de altura.
        for (int y = 0; y < 6; y++) h.setBlock(new BlockPos(1, 3 + y, 4), ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity col = h.getBlockEntity(new BlockPos(1, 3, 4));
        h.assertTrue(col.energyNetwork().size() == 6, "Una columna de 6 es un tanque de 6");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void aBaseWiderThanThreeSplits(GameTestHelper h) {
        fill(h, ModBlocks.FLUID_TANK.get(), 4, 1);
        FluidTransportBlockEntity corner = h.getBlockEntity(new BlockPos(1, 1, 1));
        h.assertTrue(corner.fluidNetwork().size() == 9, "Una base de 4x4 forma un 3x3 y el resto queda aparte; salió "
                + corner.fluidNetwork().size());
        FluidTransportBlockEntity outside = h.getBlockEntity(new BlockPos(4, 1, 4));
        h.assertTrue(outside.fluidNetwork() != corner.fluidNetwork(), "La esquina sobrante es otro tanque");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void breakingATankBlockKeepsTheRest(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        h.setBlock(A.above(), ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity bottom = h.getBlockEntity(A);
        EnergyTransportBlockEntity top = h.getBlockEntity(A.above());
        int put = 0;
        for (int i = 0; i < 20 && put < 100_000; i++) put += bottom.energyAt(null).receiveEnergy(100_000 - put, false);
        h.assertTrue(top.energyAt(null).getEnergyStored() == put, "Lo que entra por abajo es del tanque entero");

        var item = new net.minecraft.world.item.ItemStack(ModBlocks.ENERGY_TANK.get());
        item.applyComponents(top.collectComponents());
        Integer inItem = item.get(com.hmc.zenkai.registry.ModDataComponents.STORED_ENERGY.get());
        h.setBlock(A.above(), net.minecraft.world.level.block.Blocks.AIR);
        h.assertTrue(inItem != null && bottom.energy() + inItem == put,
                "El bloque roto se lleva su cuota en el ítem y el resto la conserva");
        h.assertTrue(bottom.energyNetwork().size() == 1, "Y el que queda es un tanque suelto");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tanksWithDifferentFluidsStayApart(GameTestHelper h) {
        h.setBlock(A, ModBlocks.FLUID_TANK.get());
        h.setBlock(A.above(), ModBlocks.FLUID_TANK.get());
        FluidTransportBlockEntity water = h.getBlockEntity(A);
        FluidTransportBlockEntity lava = h.getBlockEntity(A.above());
        water.rawTank().setFluid(new FluidStack(Fluids.WATER, 1_000));
        lava.rawTank().setFluid(new FluidStack(Fluids.LAVA, 1_000));
        h.assertTrue(water.fluidNetwork() != lava.fluidNetwork(), "Agua y lava no forman un tanque");

        // Vaciada la lava, el grupo se disuelve en el siguiente tick y ya se unen.
        lava.fluidAt(null).drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        tickFluid(h, A.above());
        // Primero el de la lava: es el que se disolvió y, al rehacerse, se lleva consigo al de agua.
        h.assertTrue(lava.fluidNetwork() == water.fluidNetwork(), "Vacío, se une al de agua");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void aDisconnectedFaceKeepsTwoTanksApart(GameTestHelper h) {
        h.setBlock(A, ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity bottom = h.getBlockEntity(A);
        bottom.cycleMode(Direction.UP);
        bottom.cycleMode(Direction.UP);
        bottom.cycleMode(Direction.UP);   // NONE
        h.setBlock(A.above(), ModBlocks.ENERGY_TANK.get());
        EnergyTransportBlockEntity top = h.getBlockEntity(A.above());
        h.assertTrue(bottom.energyNetwork() != top.energyNetwork(), "Con la cara Desconectada no se unen");
        h.succeed();
    }
}
