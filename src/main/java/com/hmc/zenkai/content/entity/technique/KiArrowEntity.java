package com.hmc.zenkai.content.entity.technique;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Flecha infusionada con ki. Extiende Arrow y no AbstractArrow a propósito: así las flechas
 * con punta (efectos) y los encantamientos del arco (Poder, Impacto, Llama) siguen
 * funcionando igual, que era el motivo de reemplazar la entidad en vez de inventarse una.
 *
 * Dos diferencias con una flecha normal, y las dos por el mismo motivo — el ki ya se pagó al
 * disparar, así que la flecha no puede volver al inventario:
 *  - No se recoge: si se pudiera, saldrían flechas infusionadas gratis recogiendo las tuyas.
 *  - Se desvanece al aterrizar en vez de quedarse clavada.
 *
 * El daño extra y el escalado de defensa NO viven aquí: viajan en el attachment
 * KiInfusedShot, porque el datapack puede infusionar proyectiles que no son de esta clase
 * (tridentes, proyectiles de otros mods) y el pipeline de daño tiene que tratarlos igual.
 * Sin visual propio: se registra con el renderer de flecha vanilla.
 */
public class KiArrowEntity extends Arrow {

    public KiArrowEntity(EntityType<? extends KiArrowEntity> type, Level level) {
        super(type, level);
        // ⚠ API a verificar al compilar: campo público 'pickup' de AbstractArrow en 1.21.1.
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) discard();
    }
}