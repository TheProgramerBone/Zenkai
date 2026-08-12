package com.hmc.zenkai.client.sound;

import com.hmc.zenkai.content.block.ScouterBenchBlock;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Enciende y apaga el bucle de cada banco. Lo llama el ticker de cliente del block entity.
 *
 * POR QUÉ NO LO HACE EL RENDERER: un BER solo se ejecuta cuando el bloque está en pantalla,
 * así que el zumbido se cortaría al darse la vuelta. El ticker corre mientras el chunk esté
 * cargado, que es lo que corresponde a un sonido espacial.
 *
 * El mapa guarda una instancia por posición para no apilar bucles: sin él, cada tick crearía
 * un sonido nuevo encima del anterior.
 */
public final class ScouterBenchSounds {
    private ScouterBenchSounds() {}

    private static final Map<BlockPos, BenchLoopSound> ACTIVE = new HashMap<>();

    /** ¿Debe sonar ahora? Trabajando y sin pausa. */
    public static boolean shouldLoop(ScouterBenchBlockEntity be) {
        BlockState st = be.getBlockState();
        return st.hasProperty(ScouterBenchBlock.WORKING)
                && st.getValue(ScouterBenchBlock.WORKING)
                && !be.isPaused();
    }

    public static void tick(ScouterBenchBlockEntity be) {
        BlockPos pos = be.getBlockPos().immutable();
        BenchLoopSound current = ACTIVE.get(pos);

        // El motor puede haberlo parado por su cuenta (distancia, stop del propio sonido).
        if (current != null && current.isStopped()) {
            ACTIVE.remove(pos);
            current = null;
        }

        boolean want = shouldLoop(be);
        if (want && current == null) {
            BenchLoopSound loop = new BenchLoopSound(be);
            Minecraft.getInstance().getSoundManager().play(loop);
            ACTIVE.put(pos, loop);
        } else if (!want && current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            ACTIVE.remove(pos);
        }
    }

    /** Al salir del mundo. Sin esto, las posiciones del mundo anterior se quedan en el mapa. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        ACTIVE.values().forEach(s -> mc.getSoundManager().stop(s));
        ACTIVE.clear();
    }
}