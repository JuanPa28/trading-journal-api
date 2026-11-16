#!/usr/bin/env python3
"""Experimento 4 de seguridad (grupal): resiliencia de los datos en Oracle Object Storage.

Condición adversa: un actor con acceso al bucket sobrescribe y borra los objetos (tipo
ransomware). Se mide cuánto de lo que había se puede recuperar (RPO) y en cuánto tiempo (RTO).
Técnica de ATT&CK: T1485 (Data Destruction) y T1486 (Data Encrypted for Impact), esta última por la
sobrescritura. Control de D3FEND: Restore Object (D3-RO) y copias aisladas.

El bucket de prueba guarda un objeto sintético por cada API del grupo, que representa lo que cada
una guarda del flujo (adjunto de trading-journal, predicción de StatsNostra, pedido de ecommerce).

Subcomandos (todo en el compartimento del grupo; solo datos sintéticos):
  preparar   Crea el bucket de prueba y el bucket de la copia aislada. Ronda 0: sin versionado.
  poblar     Sube los objetos sintéticos y guarda su suma de verificación (sha256).
  atacar     Sobrescribe con basura y borra los objetos. Anota t0.
  restaurar  Recupera los objetos desde versiones anteriores o desde la copia aislada y los compara.
  mejorar    Ronda 1: activa el versionado del bucket y copia los objetos al bucket aislado.
  medir      Resume RPO, RTO e integridad en seguridad/resultados/04-ronda-N.json.
  destruir   Elimina los dos buckets de prueba (limpieza final).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import oci

RAIZ = Path(__file__).resolve().parents[2]
RESULTADOS = RAIZ / "seguridad" / "resultados"
BUCKET = "caos-datos-sinteticos"
COPIA = "caos-datos-copia-aislada"
OBJETOS = {
    "trading-journal/adjunto-001.txt": b"adjunto sintetico del diario de trading, operacion 5540\n",
    "statsnostra/prediccion-001.json": b'{"partido": "sintetico-1", "prediccion": "2-1"}\n',
    "ecommerce/pedido-001.json": b'{"pedido": "sintetico-1", "total": 125000}\n',
}
BASURA = b"CIFRADO-SIMULADO-DE-LA-PRUEBA\n"
APORTES = Path(__file__).resolve().parent / "objetos"


def cargar_objetos(carpeta: Path = APORTES) -> dict[str, bytes]:
    """Objetos sintéticos de la prueba: los de cada API del grupo si la carpeta `objetos/` trae su aporte.

    Un aporte es un archivo en `objetos/<api>/<nombre>`; si junto a él hay un `<nombre>.sha256`, el hash
    declarado por quien lo aportó debe coincidir con el contenido (si no, se rechaza). Las APIs que no
    aportan archivo quedan con el objeto sintético por defecto.
    """
    objetos = dict(OBJETOS)
    if carpeta.is_dir():
        for archivo in sorted(p for p in carpeta.rglob("*") if p.is_file() and p.suffix != ".sha256"):
            contenido = archivo.read_bytes()
            declarado = archivo.with_name(archivo.name + ".sha256")
            if declarado.exists() and declarado.read_text(encoding="utf-8").split()[0].lower() != sha(contenido):
                raise SystemExit(f"El hash declarado de {archivo.name} no coincide con su contenido")
            clave = archivo.relative_to(carpeta).as_posix()
            api = clave.split("/")[0]
            for por_defecto in [k for k in objetos if k.startswith(api + "/")]:
                del objetos[por_defecto]   # el aporte reemplaza al objeto por defecto de esa API
            objetos[clave] = contenido
    return objetos


def ahora() -> datetime:
    return datetime.now(timezone.utc)


def hora_colombia() -> str:
    return (ahora() - timedelta(hours=5)).strftime("%H:%M:%S")


def iso(m: datetime) -> str:
    return m.strftime("%Y-%m-%dT%H:%M:%SZ")


def cliente():
    config = oci.config.from_file()
    texto = (RAIZ / "canary" / "db" / "db.auto.tfvars").read_text(encoding="utf-8")
    compartimento = re.search(r'^oci_compartment_ocid\s*=\s*"([^"]+)"', texto, flags=re.M).group(1)
    almacen = oci.object_storage.ObjectStorageClient(config)
    return almacen, almacen.get_namespace().data, compartimento


def bitacora(accion: str, resultado: str) -> None:
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    archivo = RESULTADOS.parent / "bitacora.md"
    if not archivo.exists():
        archivo.write_text("# Bitácora de los experimentos de seguridad (trading-journal)\n\n"
                           "| Hora (UTC) | Quién | Acción | Resultado |\n|---|---|---|---|\n", encoding="utf-8")
    with archivo.open("a", encoding="utf-8") as f:
        f.write(f"| {hora_colombia()} | Juan Pablo | {accion} | {resultado} |\n")


def estado(ronda: int | None = None) -> dict:
    archivo = RESULTADOS / "04-estado.json"
    return json.loads(archivo.read_text(encoding="utf-8")) if archivo.exists() else {}


def guardar(datos: dict) -> None:
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    (RESULTADOS / "04-estado.json").write_text(json.dumps(datos, indent=2), encoding="utf-8")


def sha(contenido: bytes) -> str:
    return hashlib.sha256(contenido).hexdigest()


def leer(almacen, ns, bucket, nombre, version=None) -> bytes | None:
    try:
        return almacen.get_object(ns, bucket, nombre, version_id=version).data.content
    except oci.exceptions.ServiceError:
        return None


def preparar() -> None:
    almacen, ns, comp = cliente()
    for nombre in (BUCKET, COPIA):
        try:
            almacen.create_bucket(ns, oci.object_storage.models.CreateBucketDetails(
                name=nombre, compartment_id=comp, public_access_type="NoPublicAccess", versioning="Disabled",
                freeform_tags={"proyecto": "trading-journal", "proposito": "caos-seguridad", "borrar-despues": "si"}))
        except oci.exceptions.ServiceError as e:
            if e.status != 409:
                raise
    bitacora("Preparación: creo los buckets de prueba y de copia aislada (sin versionado)", "listos")
    print("Buckets listos:", BUCKET, COPIA)


def poblar() -> None:
    almacen, ns, _ = cliente()
    manifiesto = {}
    for nombre, contenido in cargar_objetos().items():
        almacen.put_object(ns, BUCKET, nombre, contenido)
        manifiesto[nombre] = sha(contenido)
    datos = estado()
    datos["manifiesto"] = manifiesto
    datos["poblado"] = iso(ahora())
    guardar(datos)
    bitacora("Poblar: subo los objetos sintéticos", f"{len(manifiesto)} objetos")
    print(f"{len(manifiesto)} objetos subidos")


def atacar(ronda: int) -> None:
    almacen, ns, _ = cliente()
    t0 = ahora()
    objetos_del_ataque = estado()["manifiesto"]
    for nombre in objetos_del_ataque:
        almacen.put_object(ns, BUCKET, nombre, BASURA)   # sobrescritura (cifrado simulado)
        almacen.delete_object(ns, BUCKET, nombre)        # borrado
    datos = estado()
    datos[f"ronda{ronda}"] = {"t0": iso(t0)}
    guardar(datos)
    bitacora(f"Ronda {ronda}: sobrescribo y borro los objetos del bucket", f"{len(objetos_del_ataque)} objetos afectados a las {iso(t0)}")
    print("Ataque simulado hecho; t0 =", iso(t0))


def restaurar(ronda: int) -> None:
    almacen, ns, _ = cliente()
    datos = estado()
    manifiesto = datos["manifiesto"]
    recuperados, origen = 0, {}
    for nombre, esperado in manifiesto.items():
        contenido, de = None, "ninguno"
        versiones = almacen.list_object_versions(ns, BUCKET, prefix=nombre).data.items
        for v in sorted((v for v in versiones if not v.is_delete_marker), key=lambda v: v.time_created, reverse=True):
            candidato = leer(almacen, ns, BUCKET, nombre, v.version_id)
            if candidato is not None and sha(candidato) == esperado:
                contenido, de = candidato, "versión anterior"
                break
        if contenido is None:
            candidato = leer(almacen, ns, COPIA, nombre)
            if candidato is not None and sha(candidato) == esperado:
                contenido, de = candidato, "copia aislada"
        if contenido is not None:
            almacen.put_object(ns, BUCKET, nombre, contenido)
            recuperados += 1
        origen[nombre] = de
    fin = ahora()
    datos[f"ronda{ronda}"].update({"fin_restauracion": iso(fin), "recuperados": recuperados, "total": len(manifiesto), "origen": origen})
    guardar(datos)
    bitacora(f"Ronda {ronda}: restauración", f"{recuperados}/{len(manifiesto)} objetos recuperados ({', '.join(sorted(set(origen.values())))})")
    print(f"Recuperados {recuperados}/{len(manifiesto)}: {origen}")


def mejorar() -> None:
    almacen, ns, _ = cliente()
    almacen.update_bucket(ns, BUCKET, oci.object_storage.models.UpdateBucketDetails(versioning="Enabled"))
    datos = estado()
    for nombre, contenido in cargar_objetos().items():
        almacen.put_object(ns, BUCKET, nombre, contenido)       # vuelve a quedar la versión buena
        almacen.put_object(ns, COPIA, nombre, contenido)        # copia aislada (otro bucket, sin escritura de la API)
    datos["ultima_copia"] = iso(ahora())
    guardar(datos)
    bitacora("Mejoras: versionado del bucket y copia a un bucket aislado", "aplicadas")
    print("Versionado activado y copia aislada hecha")


def medir(ronda: int) -> None:
    datos = estado()
    r = datos[f"ronda{ronda}"]
    t0 = datetime.strptime(r["t0"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    fin = datetime.strptime(r["fin_restauracion"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    almacen, ns, _ = cliente()
    integros = sum(1 for n, esperado in datos["manifiesto"].items()
                   if (c := leer(almacen, ns, BUCKET, n)) is not None and sha(c) == esperado)
    salida = {"ronda": ronda, "objetos_total": r["total"], "objetos_recuperados": r["recuperados"], "integros": integros,
              "rto_s": round((fin - t0).total_seconds()) if r["recuperados"] else None,
              "rpo": "pérdida total: no había copia" if r["recuperados"] == 0 else "0 objetos perdidos (última versión íntegra)"}
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    (RESULTADOS / f"04-ronda-{ronda}.json").write_text(json.dumps(salida, indent=2, ensure_ascii=False), encoding="utf-8")
    bitacora(f"Ronda {ronda}: medición", json.dumps(salida, ensure_ascii=False))
    print(json.dumps(salida, indent=2, ensure_ascii=False))


def destruir() -> None:
    almacen, ns, _ = cliente()
    for bucket in (BUCKET, COPIA):
        try:
            for v in almacen.list_object_versions(ns, bucket).data.items:
                almacen.delete_object(ns, bucket, v.name, version_id=v.version_id)
            almacen.delete_bucket(ns, bucket)
        except oci.exceptions.ServiceError as e:
            if e.status != 404:
                raise
    bitacora("Limpieza: elimino los buckets de prueba", "eliminados")
    print("Buckets eliminados")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    for nombre in ("preparar", "poblar", "mejorar", "destruir"):
        sub.add_parser(nombre)
    for nombre in ("atacar", "restaurar", "medir"):
        sub.add_parser(nombre).add_argument("--ronda", type=int, choices=[0, 1], required=True)
    a = parser.parse_args()
    acciones = {"preparar": preparar, "poblar": poblar, "mejorar": mejorar, "destruir": destruir,
                "atacar": lambda: atacar(a.ronda), "restaurar": lambda: restaurar(a.ronda), "medir": lambda: medir(a.ronda)}
    acciones[a.cmd]()


if __name__ == "__main__":
    sys.exit(main())
