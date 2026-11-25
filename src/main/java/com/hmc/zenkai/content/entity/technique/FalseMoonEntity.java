package com.hmc.zenkai.content.entity.technique;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Esfera de la técnica LUNA FALSA ({@code PhysicalTechnique.FALSE_MOON}, ver {@code feature.forms.FalseMoon}). Sale despedida
 * hacia donde mira el lanzador, sube hasta una altura de «luna» y se queda FLOTANDO quieta hasta que se agota su vida (~1 min);
 * mientras existe, un Saiyan con cola que la mire cuenta como si viera la luna llena (OozaruConditions). No se golpea, no
 * daña y no persiste: vive un rato y desaparece (no se guarda con el mundo).
 *
 * Movimiento: el servidor guía la fase de ascenso (setPos hacia {@code hoverPos}) y el cliente la extrapola con la velocidad
 * que le llega; sin colisiones.
 */
public class FalseMoonEntity extends Entity {

    /** Vida total por defecto: 1 minuto. */
    public static final int DEFAULT_LIFE_TICKS = 1200;
    /** Velocidad del ascenso en bloques por tick. */
    private static final double RISE_SPEED = 0.9;

    private int life = DEFAULT_LIFE_TICKS;
    private Vec3 hoverPos = null;

    public FalseMoonEntity(EntityType<? extends FalseMoonEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Solo servidor: dónde se queda flotando y cuánto vive. */
    public void configure(Vec3 hover, int lifeTicks) {
        this.hoverPos = hover;
        this.life = lifeTicks;
    }

    /** Ticks que le quedan de vida (solo fiable en el servidor). */
    public int lifeLeft() { return life; }

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            if (--life < 0) { discard(); return; }
            if (hoverPos != null) {
                Vec3 to = hoverPos.subtract(position());
                double d = to.length();
                if (d <= RISE_SPEED) {
                    setPos(hoverPos.x, hoverPos.y, hoverPos.z);
                    hoverPos = null;   // llegó: se queda quieta
                    setDeltaMovement(Vec3.ZERO);
                } else {
                    Vec3 step = to.scale(RISE_SPEED / d);
                    setDeltaMovement(step);
                    setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
                }
            }
        } else {
            // El cliente la mueve con la velocidad que le llega mientras sube; quieta, no se mueve.
            Vec3 v = getDeltaMovement();
            if (v.lengthSqr() > 1.0E-6) setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        }
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {}

    /** No se guarda con el mundo: una luna falsa que sobrevive a un reinicio sería una luna eterna. */
    @Override
    public boolean shouldBeSaved() { return false; }
}
