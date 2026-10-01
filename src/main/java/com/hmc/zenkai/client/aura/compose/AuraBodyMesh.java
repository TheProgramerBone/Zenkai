package com.hmc.zenkai.client.aura.compose;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Malla del CUERPO del aura compuesta: una esfera unitaria de latitud/longitud
 * (la forma entera la pone el vertex shader de cada familia), pero subida UNA vez a la GPU en un VBO
 * estático en vez de reemitirse por la CPU en cada draw.
 * <p>
 * POR QUÉ (auditoría del 2026-09-30): la llama sola reemite 12 288 vértices por draw; con dos
 * tonos y la pasada de bloom eso son ~49k vértices por jugador y frame escritos en CPU para una
 * malla que nunca cambia. Aquí cada draw es una llamada con el VBO ya en la GPU.
 * <p>
 * TRES NIVELES DE DETALLE, elegidos por distancia ({@link #lodFor}): de cerca 32x192 (la resolución
 * que la onda de picos necesita: con ~2.5 vértices por pico (prueba del 2026-09-29) las lenguas
 * se leían como cristales), a media distancia 24x120 y lejos 12x64 — a esa distancia un pico ocupa
 * un par de píxeles y la silueta la lleva el contorno, no el detalle.
 * <p>
 * Solo se toca desde el hilo de render (la tarea diferida de la cola), así que la creación
 * perezosa no necesita sincronización.
 */
public final class AuraBodyMesh {

    private AuraBodyMesh() {}

    public static final int LOD_NEAR = 0, LOD_MID = 1, LOD_FAR = 2;

    // Más segmentos que la llama sola (96): la llama de agujas (2026-09-30) usa ~26 picos, y con 96
    // quedaban menos de 4 vértices por pico — las agujas se leían como cristales planos.
    private static final int[][] SIZES = {{32, 192}, {24, 120}, {12, 64}};
    private static final VertexBuffer[] VBOS = new VertexBuffer[SIZES.length];

    /** Nivel de detalle por distancia al cuadrado (bloques²). Mismos cortes que AuraLod NEAR/MID. */
    public static int lodFor(double distSq) {
        if (distSq <= 10.0 * 10.0) return LOD_NEAR;
        if (distSq <= 22.0 * 22.0) return LOD_MID;
        return LOD_FAR;
    }

    /**
     * Dibuja la esfera con {@code shader}, que ya debe tener sus uniforms propios puestos. Los
     * estándar (ModelViewMat, ProjMat, GameTime, ColorModulator, Sampler0) los pone
     * {@code drawWithShader} desde el estado de RenderSystem — el mismo que usa el BufferSource.
     */
    public static void draw(int lod, ShaderInstance shader) {
        VertexBuffer vb = VBOS[lod];
        if (vb == null) {
            vb = build(SIZES[lod][0], SIZES[lod][1]);
            VBOS[lod] = vb;
        }
        vb.bind();
        vb.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    // ── Cápsulas (cuerpo FLOW y contorno) ──

    /** Anillos de cada tapa, anillos del cilindro y segmentos alrededor de cada cápsula. */
    private static final int CAP_RINGS = 5, CYL_RINGS = 8, CAP_SEGMENTS = 24;
    private static VertexBuffer capsules;

    /**
     * Dibuja las SEIS cápsulas de {@link AuraAnchor} de una vez. Cada vértice lleva su segmento y
     * su posición a lo largo del eje en el color; el shader (aura_body_flow) las coloca sobre la
     * pose real con los uniforms ZenkaiSeg{i}A/B. Un solo draw por capa en vez de seis.
     */
    public static void drawCapsules(ShaderInstance shader) {
        if (capsules == null) capsules = buildCapsules();
        capsules.bind();
        capsules.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    private static VertexBuffer buildCapsules() {
        // Anillos de abajo arriba: (posición en el eje 0..1, y de la dirección, radio de la dirección).
        int rings = 2 * (CAP_RINGS + 1) + (CYL_RINGS - 1);
        float[] axial = new float[rings], dy = new float[rings], rad = new float[rings];
        int k = 0;
        for (int j = 0; j <= CAP_RINGS; j++) {
            double lat = Math.PI - (Math.PI / 2.0) * j / CAP_RINGS;
            axial[k] = 0f; dy[k] = (float) Math.cos(lat); rad[k] = (float) Math.sin(lat); k++;
        }
        for (int j = 1; j < CYL_RINGS; j++) {
            axial[k] = j / (float) CYL_RINGS; dy[k] = 0f; rad[k] = 1f; k++;
        }
        for (int j = 0; j <= CAP_RINGS; j++) {
            double lat = (Math.PI / 2.0) * (1.0 - j / (double) CAP_RINGS);
            axial[k] = 1f; dy[k] = (float) Math.cos(lat); rad[k] = (float) Math.sin(lat); k++;
        }
        VertexFormat fmt = DefaultVertexFormat.POSITION_COLOR_NORMAL;
        int quads = AuraAnchor.SEGMENTS * (rings - 1) * CAP_SEGMENTS;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * 4 * fmt.getVertexSize())) {
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, fmt);
            for (int seg = 0; seg < AuraAnchor.SEGMENTS; seg++) {
                for (int r = 0; r < rings - 1; r++) {
                    for (int s = 0; s < CAP_SEGMENTS; s++) {
                        double lon0 = 2.0 * Math.PI * s / CAP_SEGMENTS;
                        double lon1 = 2.0 * Math.PI * (s + 1) / CAP_SEGMENTS;
                        capsuleVertex(bb, seg, axial[r], dy[r], rad[r], lon0);
                        capsuleVertex(bb, seg, axial[r + 1], dy[r + 1], rad[r + 1], lon0);
                        capsuleVertex(bb, seg, axial[r + 1], dy[r + 1], rad[r + 1], lon1);
                        capsuleVertex(bb, seg, axial[r], dy[r], rad[r], lon1);
                    }
                }
            }
            MeshData mesh = bb.buildOrThrow();
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh);
            VertexBuffer.unbind();
            return vb;
        }
    }

    private static void capsuleVertex(BufferBuilder bb, int seg, float axial, float dy, float rad, double lon) {
        float x = rad * (float) Math.cos(lon), z = rad * (float) Math.sin(lon);
        int a = Math.round(axial * 255f);
        bb.addVertex(x, dy, z).setColor(a, seg * 40, 0, 255).setNormal(x, dy, z);
    }

    private static VertexBuffer build(int rings, int segments) {
        VertexFormat fmt = DefaultVertexFormat.POSITION_COLOR_NORMAL;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(rings * segments * 4 * fmt.getVertexSize())) {
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, fmt);
            for (int r = 0; r < rings; r++) {
                double lat0 = Math.PI * r / rings;       // 0 = polo superior
                double lat1 = Math.PI * (r + 1) / rings;
                for (int s = 0; s < segments; s++) {
                    double lon0 = 2.0 * Math.PI * s / segments;
                    double lon1 = 2.0 * Math.PI * (s + 1) / segments;
                    put(bb, lat0, lon0);
                    put(bb, lat1, lon0);
                    put(bb, lat1, lon1);
                    put(bb, lat0, lon1);
                }
            }
            MeshData mesh = bb.buildOrThrow();
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh); // cierra mesh
            VertexBuffer.unbind();
            return vb;
        }
    }

    /** Posición = normal = punto de la esfera unitaria; color blanco opaco (la opacidad del aura
     *  va en ColorModulator, que sí puede cambiar por draw). */
    private static void put(BufferBuilder bb, double lat, double lon) {
        float sinLat = (float) Math.sin(lat);
        float x = sinLat * (float) Math.cos(lon);
        float y = (float) Math.cos(lat);
        float z = sinLat * (float) Math.sin(lon);
        bb.addVertex(x, y, z).setColor(0xFFFFFFFF).setNormal(x, y, z);
    }
}
