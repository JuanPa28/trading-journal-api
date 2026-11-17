#!/usr/bin/env python3
"""Ensambla el documento final de un informe: el informe principal más la sección de cada integrante.

Cada integrante entrega su sección ya recortada (PDF) en `secciones/` de la carpeta del informe y este script las
anexa, en orden alfabético, al PDF principal. Comprueba el máximo de páginas de la rúbrica y falla si lo supera.

Uso:
  python caos/informe/ensamblar.py caos        # caos/informe/informe-caos.pdf + secciones -> informe-caos-final.pdf (máx. 6)
  python caos/informe/ensamblar.py seguridad   # seguridad/informe/informe-seguridad.pdf + secciones -> informe-seguridad-final.pdf (máx. 10)
Opcional: --principal RUTA y --salida RUTA para ensamblar otra versión (por ejemplo la que nombra la herramienta de IA).
"""
import argparse
import sys
from pathlib import Path

import pymupdf

RAIZ = Path(__file__).resolve().parents[2]
LIMITES = {"caos": 6, "seguridad": 10}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("informe", choices=sorted(LIMITES))
    parser.add_argument("--principal", type=Path)
    parser.add_argument("--salida", type=Path)
    a = parser.parse_args()
    carpeta = RAIZ / a.informe / "informe"
    principal = a.principal or carpeta / f"informe-{a.informe}.pdf"
    salida = a.salida or carpeta / f"informe-{a.informe}-final.pdf"
    secciones = sorted((carpeta / "secciones").glob("*.pdf"))
    final = pymupdf.open(principal)
    paginas_principal = len(final)
    for seccion in secciones:
        with pymupdf.open(seccion) as extra:
            final.insert_pdf(extra)
    final.save(salida)
    total = len(final)
    print(f"{salida.name}: {paginas_principal} páginas del informe + {total - paginas_principal} de {len(secciones)} sección(es) = {total} (máximo {LIMITES[a.informe]})")
    if total > LIMITES[a.informe]:
        print("ERROR: supera el máximo de páginas de la rúbrica", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
