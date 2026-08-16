package com.hmc.zenkai.client.sound;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.function.Predicate;

/**
 * Bucle anclado a un jugador: suena desde donde está y lo SIGUE mientras se mueve, y se
 * apaga solo cuando la condición deja de cumplirse.
energy_generator * POR QUÉ SIGUE AL JUGADOR Y NO ES UN playSound EN EL SERVIDOR: un playSound se emite en una
 * coordenada y se queda ahí. Alguien cargando ki mientras vuela dejaría el zumbido flotando
 * en el punto donde empezó. Actualizar x/y/z en tick() es lo que hace que el sonido viaje
 * con él, y eso solo se puede hacer desde el cliente.
energy_generator * SIGUE SIENDO POSICIONAL, que era el requisito: la categoría y las coordenadas hacen que se
 * atenúe con la distancia y que lo oigan los demás jugadores, no solo el dueño. Lo único que
 * es de cliente es QUIÉN lleva la cuenta; el estado que decide si suena (isChargingKi, turbo)
 * es del servidor y llega ya sincronizado.
energy_generator * ⚠ VERIFICAR 1.21.1: constructor AbstractTickableSoundInstance(SoundEvent, SoundSource,
 * RandomSource) y SoundInstance.createUnseededRandom(). Mismo par que usa BenchLoopSound.
 */
public class PlayerLoopSound extends AbstractTickableSoundInstance {

    private final AbstractClientPlayer player;
    private final Predicate<AbstractClientPlayer> stillActive;

    public PlayerLoopSound(SoundEvent event, AbstractClientPlayer player,
                           Predicate<AbstractClientPlayer> stillActive, float volume) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.stillActive = stillActive;

        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = 1.0f;

        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    public AbstractClientPlayer player() { return player; }

    @Override
    public void tick() {
        if (player.isRemoved() || !player.isAlive() || !stillActive.test(player)) {
            stop();
            return;
        }
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}