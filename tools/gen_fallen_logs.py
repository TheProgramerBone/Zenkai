"""Genera 3 estructuras NBT de troncos caídos (madera vanilla) directamente en
data/zenkai/structure/, sin abrir Minecraft — decoración escasa para biomas sin
árboles (rocky_wasteland, hfil_wastes/badlands/dunes; ver FallenLogFeature).

FORMATO: un structure template de Minecraft es un compound NBT raíz (nombre
vacío, gzip) con:
  DataVersion: Int            -- 3955 para 1.21.1. Confirmado leyendo con
                                  nbtlib el DataVersion real de un .nbt YA
                                  existente en este repo (kaiosama_1.nbt), no
                                  asumido de memoria.
  size: List[Int] (3)         -- [sizeX, sizeY, sizeZ]
  entities: List[Compound]    -- vacío, ninguna de estas estructuras usa entidades
  palette: List[Compound]     -- cada uno {"Name": String, "Properties"?: Compound}
  blocks: List[Compound]      -- cada uno {"pos": List[Int](3), "state": Int
                                  (índice en palette)}

DEPENDENCIA: usa la librería nbtlib (ya presente en este entorno de
desarrollo; instalar con `pip install nbtlib` si hiciera falta en otro
equipo) en vez de un escritor NBT binario a mano — para un formato binario
que no se puede validar más que cargándolo en el propio juego, una librería
ya probada reduce muchísimo el riesgo de un byte mal puesto frente a
reimplementar TAG_Compound/TAG_List a mano.

Ejecutar: python tools/gen_fallen_logs.py
"""

import os

from nbtlib import Compound, Int, List, String, File

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "data", "zenkai", "structure")

DATA_VERSION = 3955  # 1.21.1, confirmado contra kaiosama_1.nbt


def block(pos, name, properties=None):
    """(pos_tuple, 'minecraft:block_name', {prop: value} | None) -> entrada intermedia."""
    entry = {"Name": name}
    if properties:
        entry["Properties"] = properties
    return pos, entry


def build_structure(blocks, size):
    """blocks: lista de (pos, {"Name":..., "Properties":...}). Deduplica la
    paleta por (Name, Properties) preservando orden de primera aparición."""
    palette = []
    palette_index = {}
    block_list = []

    for pos, entry in blocks:
        key = (entry["Name"], tuple(sorted(entry.get("Properties", {}).items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            compound = {"Name": String(entry["Name"])}
            if "Properties" in entry:
                compound["Properties"] = Compound(
                    {k: String(v) for k, v in entry["Properties"].items()})
            palette.append(Compound(compound))
        block_list.append(Compound({
            "pos": List[Int]([Int(c) for c in pos]),
            "state": Int(palette_index[key]),
        }))

    return File({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(c) for c in size]),
        "entities": List[Compound]([]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](block_list),
    }, gzipped=True, root_name="")


def fallen_log_1():
    """Tronco recto, 6 bloques tumbados sobre el eje X."""
    blocks = [block((x, 0, 0), "minecraft:dark_oak_log", {"axis": "x"}) for x in range(6)]
    return build_structure(blocks, size=(6, 1, 1))


def fallen_log_2():
    """Tronco con una rama rota: 4 bloques en X + 2 en Z pegados al extremo,
    formando una L tumbada (fork partido por la caída)."""
    trunk = [block((x, 0, 0), "minecraft:dark_oak_log", {"axis": "x"}) for x in range(4)]
    branch = [block((3, 0, z), "minecraft:dark_oak_log", {"axis": "z"}) for z in range(1, 3)]
    return build_structure(trunk + branch, size=(4, 1, 3))


def fallen_log_3():
    """Tronco recto con hojarasca persistente en un extremo (no decae:
    persistent=true evita que estas hojas se calculen/caigan solas)."""
    trunk = [block((x, 0, 0), "minecraft:dark_oak_log", {"axis": "x"}) for x in range(5)]
    leaves = [
        block((0, 1, 0), "minecraft:dark_oak_leaves", {"persistent": "true"}),
        block((1, 1, 0), "minecraft:dark_oak_leaves", {"persistent": "true"}),
    ]
    return build_structure(trunk + leaves, size=(5, 2, 1))


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    variants = {
        "fallen_log_1": fallen_log_1(),
        "fallen_log_2": fallen_log_2(),
        "fallen_log_3": fallen_log_3(),
    }
    for name, structure in variants.items():
        path = os.path.join(OUTPUT_DIR, f"{name}.nbt")
        structure.save(path)
        print(f"Escrito {path}")


if __name__ == "__main__":
    main()
