#version 150

// Blur gaussiano separable de UN eje (KiVfxBloomPipeline lo llama dos veces por frame: una con
// BlurDir=(1,0) y otra con BlurDir=(0,1)) — kernel fijo y pequeño (9 muestras): las fuentes de
// bloom de ki son formas pequeñas/medianas en pantalla, no hace falta un radio configurable en
// tiempo real como en un blur de escena completa.

uniform sampler2D Sampler0;
uniform vec2 TexelSize; // 1/ancho, 1/alto del render target de origen
uniform vec2 BlurDir;   // (1,0) horizontal o (0,1) vertical

in vec2 texCoord;
out vec4 fragColor;

// Pesos de un kernel gaussiano estándar de 9 muestras (sigma ~2), simétrico: WEIGHTS[0] es el
// texel central, WEIGHTS[1..4] se aplican a ambos lados.
const float WEIGHTS[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec2 step = TexelSize * BlurDir;
    vec4 sum = texture(Sampler0, texCoord) * WEIGHTS[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = step * float(i);
        sum += texture(Sampler0, texCoord + offset) * WEIGHTS[i];
        sum += texture(Sampler0, texCoord - offset) * WEIGHTS[i];
    }
    fragColor = sum;
}
