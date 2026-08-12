package com.hmc.zenkai.client.sound;

import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/**
 * Bucle del banco trabajando. Existe una instancia por banco activo y se apaga sola.
 *
 * NO se reproduce por tick: se crea UNA vez, el motor de sonido la repite, y ella misma
 * decide cuándo callarse desde tick(). Un playSound por tick sonaría a metralleta y saturaría
 * los canales de audio.
 *
 * El sonido es del BLOQUE, no del jugador: posición fija en el centro del banco y categoría
 * BLOCKS, así que se atenúa con la distancia y lo oye cualquiera que pase cerca aunque no
 * tenga la GUI abierta.
 *
 * ⚠ VERIFICAR 1.21.1: constructor AbstractTickableSoundInstance(SoundEvent, SoundSource,
 * RandomSource) y SoundInstance.createUnseededRandom().
 */
public class BenchLoopSound extends AbstractTickableSoundInstance {

    private final ScouterBenchBlockEntity bench;

    public BenchLoopSound(ScouterBenchBlockEntity bench) {
        super(ModSounds.SCOUTER_BENCH_WORKING.get(), SoundSource.BLOCKS,
                SoundInstance.createUnseededRandom());
        this.bench = bench;

        this.looping = true;
        this.delay = 0;
        this.volume = 0.5f;
        this.pitch = 1.0f;

        BlockPos pos = bench.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    public ScouterBenchBlockEntity bench() { return bench; }

    @Override
    public void tick() {
        // Se apaga si el banco desaparece, para, o entra en pausa. La pausa cuenta: una
        // máquina sin corriente no zumba.
        if (bench.isRemoved() || !ScouterBenchSounds.shouldLoop(bench)) {
            stop();
        }
    }
}