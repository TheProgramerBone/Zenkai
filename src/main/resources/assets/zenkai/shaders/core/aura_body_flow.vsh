#version 150

// CUERPO de la familia FLOWING (Ultra Instinct) y contorno del componente RIM. NO usa la esfera:
// la malla son seis CAJAS redondeadas (AuraBodyMesh.drawCapsules, la malla sigue siendo la de una
// cápsula unitaria) y aquí cada una se coloca sobre su hueso de la pose REAL del cuerpo (AuraAnchor:
// cabeza, torso, brazos, piernas), que llega en ZenkaiSeg{i}A/B/X en coordenadas RELATIVAS A LA
// CÁMARA: A.xyz/B.xyz = extremos del eje, A.w = semiancho (a lo largo del eje lateral X del hueso),
// B.w = semiprofundidad, X.xyz = eje lateral unitario.
//
// Por qué cajas y no cápsulas redondas (2026-10-06): el cuerpo de Minecraft es de cubos. Con
// cápsulas el torso salía igual de grueso en profundidad que de ancho y los brazos se fundían con
// él, y el contorno se leía como una esfera con bultos. La sección de cada pieza es una
// superelipse (norma 4: casi un cuadrado con las esquinas suaves) con SU ancho y SU profundidad, y
// las tapas son cúpulas bajas de 0.5 * min(rx, rz).
//
// Por vértice: Position = dirección unitaria desde el eje de la cápsula (en las tapas con
// componente Y, en el cilindro sin ella), Color.r = posición a lo largo del eje (0 = A, 1 = B),
// Color.g = índice de segmento * 40/255.
//
// Lenguaje (aura_6, aura_12, aura_14): la energía se PEGA al contorno y FLUYE hacia arriba — el
// ruido se muestrea con la altura de MUNDO, así el flujo sube continuo de las piernas al torso y a
// la cabeza en vez de repetirse por pieza. De las caras que miran arriba salen lenguas suaves.
// Nada de púas: la silueta es la del cuerpo, hinchada y viva.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform vec4 ZenkaiAuraLook;     // PRUEBA de dirección de arte (2026-10-26, AuraDebug.look*): x = fps a saltos (0 = suave), y = 1 tres bandas duras, z = tope de blanco del borde (0 = el de siempre), w = relleno de la parte alta (solo la llama)
uniform sampler2D Sampler0;

uniform mat4 ZenkaiShellModelMat;   // pose del stage (sin traslación al jugador: los segmentos ya son relativos a la cámara)
uniform float ZenkaiShellAmp;       // bloques de ondulación del contorno
uniform float ZenkaiShellSpeed;     // velocidad del flujo hacia arriba
uniform float ZenkaiShellGrowth;
uniform vec4 ZenkaiFlowShape;       // x = inflado relativo, y = lenguas, z = Y de la cámara SOBRE LOS PIES del jugador (para el ruido: así el flujo no depende de dónde esté el jugador en el mundo)
uniform vec4 ZenkaiFlowLook;        // y >= 3 = contorno de SILUETA (x pasa a ser el grosor ABSOLUTO en bloques)
uniform vec4 ZenkaiSeg0A; uniform vec4 ZenkaiSeg0B; uniform vec4 ZenkaiSeg0X;
uniform vec4 ZenkaiSeg1A; uniform vec4 ZenkaiSeg1B; uniform vec4 ZenkaiSeg1X;
uniform vec4 ZenkaiSeg2A; uniform vec4 ZenkaiSeg2B; uniform vec4 ZenkaiSeg2X;
uniform vec4 ZenkaiSeg3A; uniform vec4 ZenkaiSeg3B; uniform vec4 ZenkaiSeg3X;
uniform vec4 ZenkaiSeg4A; uniform vec4 ZenkaiSeg4B; uniform vec4 ZenkaiSeg4X;
uniform vec4 ZenkaiSeg5A; uniform vec4 ZenkaiSeg5B; uniform vec4 ZenkaiSeg5X;

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out float vPeak;
out float vLocalY;
out vec3 vUnit;
out vec2 vFlowUV;

const float TAU = 6.28318530718;

void main() {
    int seg = int(Color.g * 255.0 / 40.0 + 0.5);
    vec4 A = ZenkaiSeg0A, B = ZenkaiSeg0B, L = ZenkaiSeg0X;
    if (seg == 1) { A = ZenkaiSeg1A; B = ZenkaiSeg1B; L = ZenkaiSeg1X; }
    else if (seg == 2) { A = ZenkaiSeg2A; B = ZenkaiSeg2B; L = ZenkaiSeg2X; }
    else if (seg == 3) { A = ZenkaiSeg3A; B = ZenkaiSeg3B; L = ZenkaiSeg3X; }
    else if (seg == 4) { A = ZenkaiSeg4A; B = ZenkaiSeg4B; L = ZenkaiSeg4X; }
    else if (seg == 5) { A = ZenkaiSeg5A; B = ZenkaiSeg5B; L = ZenkaiSeg5X; }

    float t = GameTime * 1200.0;
    // Movimiento A SALTOS (prueba de dirección de arte): el tiempo avanza a pocos fotogramas por
    // segundo, como animación dibujada; 0 = continuo.
    if (ZenkaiAuraLook.x > 0.0) t = floor(t * ZenkaiAuraLook.x) / ZenkaiAuraLook.x;
    vec3 axis = B.xyz - A.xyz;
    float len = max(length(axis), 0.0001);
    vec3 Y = axis / len;
    // Eje lateral del hueso, hecho perpendicular al eje de la pieza (un brazo levantado inclina el
    // eje; el lateral sigue siendo el X del hueso). Sin lateral válido, uno cualquiera.
    vec3 X = L.xyz - Y * dot(L.xyz, Y);
    float xl = length(X);
    if (xl < 0.001) {
        vec3 ref = abs(Y.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        X = normalize(cross(Y, ref));
    } else {
        X /= xl;
    }
    vec3 Z = cross(X, Y);

    float rx = max(A.w, 0.0), rz = max(B.w, 0.0);
    float rcap = 0.5 * min(rx, rz);

    // Sección: del círculo unitario a una superelipse de norma 4 (las direcciones de los ejes se
    // quedan en 1, las diagonales salen a ~1.19: casi un cuadrado con esquinas suaves).
    vec2 c = Position.xz;
    float cl = length(c);
    // Contorno de silueta: esquinas casi vivas (norma 8) para que el cubo real no asome por ellas.
    bool sil = ZenkaiFlowLook.y > 2.5;
    float nrmExp = sil ? 8.0 : 4.0;
    float n4 = pow(pow(abs(c.x), nrmExp) + pow(abs(c.y), nrmExp), 1.0 / nrmExp);
    vec2 cs = n4 > 0.0001 ? c * (cl / n4) : c;
    vec3 off = X * (cs.x * rx) + Z * (cs.y * rz) + Y * (Position.y * rcap);
    // Normal de elipsoide (suave sobre las caras planas: el borde fresnel no se parte en facetas).
    vec3 nrm = X * (c.x / max(rx, 0.0001)) + Z * (c.y / max(rz, 0.0001)) + Y * (Position.y / max(rcap, 0.0001));
    nrm = length(nrm) > 0.0001 ? normalize(nrm) : Y;

    float g = clamp(ZenkaiShellGrowth, 0.0, 1.0);
    float smoothGrowth = g * g * (3.0 - 2.0 * g);
    // Silueta: la caja a su tamaño y el grosor sumado a lo largo de la normal (igual en todos los
    // huesos); el resto de modos inflan en proporción al tamaño de cada hueso.
    float f = (sil ? 1.0 : 1.0 + ZenkaiFlowShape.x) * smoothGrowth;
    float thick = sil ? ZenkaiFlowShape.x * smoothGrowth : 0.0;
    vec3 center = mix(A.xyz, B.xyz, Color.r);

    // Flujo: U = ángulo alrededor del segmento (entero, sin costura) desfasado por segmento;
    // V = altura sobre los PIES del jugador (no de mundo: al volar, la de mundo cambia a toda velocidad
    // y el ruido barría el contorno entero) que sube con el tiempo.
    float ang = atan(Position.z, Position.x == 0.0 ? 0.00001 : Position.x);
    float worldY = center.y + off.y * f + ZenkaiFlowShape.z;
    vec2 uv = vec2(ang / TAU * 2.0 + float(seg) * 0.23, worldY * 0.9 - t * ZenkaiShellSpeed * 0.6);
    float n = textureLod(Sampler0, uv, 0.0).r;

    float disp = ZenkaiShellAmp * (n - 0.35) * smoothGrowth;
    float rmin = min(rx, rz) * (1.0 + ZenkaiFlowShape.x);
    vec3 pos = center + off * f + nrm * (max(disp, -rmin * 0.3) + thick);
    // Lenguas suaves hacia arriba desde las caras que miran al cielo (hombros, cabeza).
    float lick = max(n - 0.5, 0.0) * 2.0 * smoothstep(-0.1, 0.9, nrm.y);
    pos.y += ZenkaiShellAmp * ZenkaiFlowShape.y * lick * 2.5 * smoothGrowth;

    vec4 world = ZenkaiShellModelMat * vec4(pos, 1.0);
    vec4 viewPos = ModelViewMat * world;
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * mat3(ZenkaiShellModelMat) * nrm);
    vViewDir = -viewPos.xyz;
    vColor   = vec4(1.0);
    vPeak    = clamp(n, 0.0, 1.0);
    vLocalY  = nrm.y;
    vUnit    = nrm;
    vFlowUV  = uv;
}
