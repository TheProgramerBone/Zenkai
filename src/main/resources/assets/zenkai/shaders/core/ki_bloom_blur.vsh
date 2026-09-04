#version 150

// Quad de pantalla completa en espacio de recorte (NDC): KiBloomPipeline sube Position ya en
// -1..1 y UV0 en 0..1, sin matrices — no hay nada que transformar, es una pasada 2D pura.

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position, 1.0);
    texCoord = UV0;
}
