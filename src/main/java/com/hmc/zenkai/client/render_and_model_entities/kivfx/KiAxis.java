package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import net.minecraft.world.phys.Vec3;

/**
 * Base ortonormal ESTABLE en espacio de mundo a partir de una dirección — el único sitio del
 * pipeline de VFX que construye "adelante/derecha/arriba" para geometría que NO debe billboardear.
 *
 * POR QUÉ EXISTE ESTA CLASE (auditoría, 2026-09-23). El sistema anterior calculaba esto mismo en
 * tres sitios independientes que podían desincronizarse: {@code KiProjectileEntity.flightBasis()},
 * {@code KiProjectileRenderer.perpendicularPair()} y, peor, un tercer cálculo INESTABLE dentro de
 * {@code ribbon()} que derivaba el lateral cada frame como {@code dir.cross(cam - punto)} — la
 * causa real del "volteo" de estela/rayos al mirar casi en paralelo a un haz (confirmado con
 * capturas del usuario): ese producto cruzado tiende a longitud cero cuando la vista queda
 * paralela a `dir`, y al cruzar ese ángulo el vector normalizado invierte de signo DE GOLPE.
 * {@link #basis(Vec3)} no depende de la cámara en absoluto, así que no hay ángulo crítico que
 * cruzar — el mismo principio que ya usa el haz de un faro o los rayos de fin de portal en
 * vainilla, ninguno de los dos billboardea.
 *
 * {@code KiProjectileEntity.flightBasis()} SIGUE existiendo aparte (no delega aquí): es código
 * COMÚN (server+client) y no puede importar una clase de {@code client.render_and_model_entities}
 * sin romper el build de servidor dedicado — la separación física cliente/servidor de Minecraft/
 * NeoForge no es una capa que se pueda saltar con "total, es la misma fórmula". Su fórmula es
 * deliberadamente idéntica a esta, y se cachea ahí (una vez por proyectil, en el primer uso) para
 * que HOMING no retuerza la estela de golpe si el rumbo gira.
 */
public final class KiAxis {
    private KiAxis() {}

    /**
     * @param dir dirección de vuelo/eje, no necesita estar normalizada. Vector nulo -> +Z.
     * @return [adelante, derecha, arriba] — los tres unitarios y perpendiculares entre sí.
     *         "Arriba" de referencia es el eje mundo Y, salvo que `dir` vaya casi vertical
     *         (paralelo a Y), donde ese cruce degeneraría a un vector casi nulo — ahí se usa el
     *         eje mundo X como referencia en su lugar.
     */
    public static Vec3[] basis(Vec3 dir) {
        Vec3 fwd = dir.lengthSqr() > 1.0e-8 ? dir.normalize() : new Vec3(0, 0, 1);
        Vec3 worldUp = Math.abs(fwd.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(worldUp).normalize();
        Vec3 up = right.cross(fwd).normalize();
        return new Vec3[]{fwd, right, up};
    }

    /**
     * Decide cuál de dos planos perpendiculares FIJOS (lateral {@code sideA}/{@code sideB}) se
     * dibuja ÚLTIMO — y por tanto "gana" la mezcla al solaparse — según cuál esté más DE FRENTE a
     * la cámara EN ESTE FRAME. Compartido por estela, rayos y estela en hélice: los tres dibujan
     * geometría como un par de planos cruzados de orientación fija (ver {@link KiRibbon}) en vez
     * de un único billboard recalculado contra cámara.
     * SOLO cambia el ORDEN de dibujo, nunca una posición de vértice — no hay aquí ningún ángulo
     * crítico que cruzar: el "ganador" de la mezcla puede cambiar de un frame a otro, pero eso se
     * ve como una transición de blend suave, no como geometría que salta.
     * La CARA de un plano es perpendicular a su propio "ancho" (side): el plano cuyo ancho es
     * sideA tiene la cara mirando hacia sideB, y viceversa — "de frente" se mide alineando la
     * vista con esa cara (el normal), no con el ancho.
     * @return [atrás, frente] — dibujar en ese orden.
     */
    public static Vec3[] orderByFacing(Vec3 point, Vec3 cam, Vec3 sideA, Vec3 sideB) {
        Vec3 toCam = cam.subtract(point);
        double alignA = Math.abs(toCam.dot(sideB));
        double alignB = Math.abs(toCam.dot(sideA));
        return alignA <= alignB ? new Vec3[]{sideA, sideB} : new Vec3[]{sideB, sideA};
    }
}
