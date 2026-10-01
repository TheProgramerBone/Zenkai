#version 150

// CUERPO de la familia FLOWING (Ultra Instinct) y contorno del componente RIM. NO usa la esfera:
// la malla son seis CÁPSULAS unitarias (AuraBodyMesh.drawCapsules) y aquí cada una se coloca sobre
// su segmento de la pose REAL del cuerpo (AuraAnchor: cabeza, torso, brazos, piernas), que llega
// en ZenkaiSeg{i}A/B en coordenadas RELATIVAS A LA CÁMARA (.w de A = radio en bloques).
//
// Por vértice: Position = dirección unitaria desde el eje de la cápsula (en las tapas con
// componente Y, en el cilindro sin ella), Color.r = posición a lo largo del eje (0 = A, 1 = B),
// Color.g = índice de segmento * 40/255.
//
// Lenguaje (aura_6, aura_12, aura_14): la energía se PEGA al contorno y FLUYE hacia arriba — el
// ruido se muestrea con la altura de MUNDO, así el flujo sube continuo de las piernas al torso y a
// la cabeza en vez de repetirse por cápsula. De las caras que miran arriba salen lenguas suaves.
// Nada de púas: la silueta es la del cuerpo, hinchada y viva.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform sampler2D Sampler0;

uniform mat4 ZenkaiShellModelMat;   // pose del stage (sin traslación al jugador: los segmentos ya son relativos a la cámara)
uniform float ZenkaiShellAmp;       // bloques de ondulación del contorno
uniform float ZenkaiShellSpeed;     // velocidad del flujo hacia arriba
uniform float ZenkaiShellGrowth;
uniform vec4 ZenkaiFlowShape;       // x = inflado relativo, y = lenguas, z = Y de la cámara en mundo (para el ruido)
uniform vec4 ZenkaiSeg0A; uniform vec4 ZenkaiSeg0B;
uniform vec4 ZenkaiSeg1A; uniform vec4 ZenkaiSeg1B;
uniform vec4 ZenkaiSeg2A; uniform vec4 ZenkaiSeg2B;
uniform vec4 ZenkaiSeg3A; uniform vec4 ZenkaiSeg3B;
uniform vec4 ZenkaiSeg4A; uniform vec4 ZenkaiSeg4B;
uniform vec4 ZenkaiSeg5A; uniform vec4 ZenkaiSeg5B;

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
    vec4 A = ZenkaiSeg0A, B = ZenkaiSeg0B;
    if (seg == 1) { A = ZenkaiSeg1A; B = ZenkaiSeg1B; }
    else if (seg == 2) { A = ZenkaiSeg2A; B = ZenkaiSeg2B; }
    else if (seg == 3) { A = ZenkaiSeg3A; B = ZenkaiSeg3B; }
    else if (seg == 4) { A = ZenkaiSeg4A; B = ZenkaiSeg4B; }
    else if (seg == 5) { A = ZenkaiSeg5A; B = ZenkaiSeg5B; }

    float t = GameTime * 1200.0;
    vec3 axis = B.xyz - A.xyz;
    float len = max(length(axis), 0.0001);
    vec3 Y = axis / len;
    vec3 ref = abs(Y.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 X = normalize(cross(Y, ref));
    vec3 Z = cross(X, Y);
    vec3 d = normalize(Position.x * X + Position.y * Y + Position.z * Z);

    float g = clamp(ZenkaiShellGrowth, 0.0, 1.0);
    float smoothGrowth = g * g * (3.0 - 2.0 * g);
    float r = A.w * (1.0 + ZenkaiFlowShape.x) * smoothGrowth;
    vec3 center = mix(A.xyz, B.xyz, Color.r);

    // Flujo: U = ángulo alrededor del segmento (entero, sin costura) desfasado por segmento;
    // V = altura de MUNDO que sube con el tiempo.
    float ang = atan(Position.z, Position.x == 0.0 ? 0.00001 : Position.x);
    float worldY = center.y + d.y * r + ZenkaiFlowShape.z;
    vec2 uv = vec2(ang / TAU * 2.0 + float(seg) * 0.23, worldY * 0.9 - t * ZenkaiShellSpeed * 0.6);
    float n = textureLod(Sampler0, uv, 0.0).r;

    float disp = ZenkaiShellAmp * (n - 0.35) * smoothGrowth;
    vec3 pos = center + d * (r + max(disp, -r * 0.3));
    // Lenguas suaves hacia arriba desde las caras que miran al cielo (hombros, cabeza).
    float lick = max(n - 0.5, 0.0) * 2.0 * smoothstep(-0.1, 0.9, d.y);
    pos.y += ZenkaiShellAmp * ZenkaiFlowShape.y * lick * 2.5 * smoothGrowth;

    vec4 world = ZenkaiShellModelMat * vec4(pos, 1.0);
    vec4 viewPos = ModelViewMat * world;
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * mat3(ZenkaiShellModelMat) * d);
    vViewDir = -viewPos.xyz;
    vColor   = vec4(1.0);
    vPeak    = clamp(n, 0.0, 1.0);
    vLocalY  = d.y;
    vUnit    = d;
    vFlowUV  = uv;
}
