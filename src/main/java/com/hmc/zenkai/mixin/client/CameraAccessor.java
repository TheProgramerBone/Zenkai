package com.hmc.zenkai.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Abre Camera.move para poder DESPLAZAR la cámara sin tocar su rotación.
 * ViewportEvent.ComputeCameraAngles solo expone yaw/pitch/roll, y una sacudida por rotación
 * marea y le quita la puntería al jugador. move() empuja por los ejes de la propia cámara
 * (frente, arriba, lateral), que es desplazamiento puro.
 * ⚠ La firma es (float,float,float) en 1.21.1. En 1.20.x eran double, y ese fue el fallo:
 * un @Invoker con los tipos equivocados no avisa en compilación, revienta al arrancar.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("move")
    void zenkai$move(float distanceOffset, float verticalOffset, float horizontalOffset);
}