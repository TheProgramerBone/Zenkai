#version 150

// Copia el depth de la escena a una textura de color (R32F) ANTES de dibujar a un jugador con contorno
// de silueta (SilhouetteFlameRenderer). Después se compara con el depth de después: lo que se acercó a
// la cámara es lo que pintó ese jugador.

uniform sampler2D Sampler0; // depth del principal

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, texCoord).r, 0.0, 0.0, 1.0);
}
