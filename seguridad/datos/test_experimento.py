"""Pruebas de la lógica del experimento 4 con un almacén de objetos simulado (no toca Oracle).

Ejecutar: python -m pytest seguridad/datos/test_experimento.py
"""
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import pytest

import experimento as e
import oci


class AlmacenFalso:
    """Imita lo mínimo de ObjectStorageClient: versionado opcional, marcas de borrado y versiones."""

    def __init__(self):
        self.buckets = {}
        self.reloj = datetime(2026, 11, 18, 15, 0, tzinfo=timezone.utc)

    def _tick(self):
        self.reloj += timedelta(seconds=1)
        return self.reloj

    def crear(self, bucket, versionado):
        self.buckets[bucket] = {"versionado": versionado, "versiones": {}}

    def put_object(self, ns, bucket, nombre, contenido):
        b = self.buckets[bucket]
        versiones = b["versiones"].setdefault(nombre, [])
        if not b["versionado"]:
            versiones.clear()
        versiones.append({"id": f"v{self._tick().timestamp()}", "contenido": contenido, "marca": False, "t": self.reloj})

    def delete_object(self, ns, bucket, nombre, version_id=None):
        b = self.buckets[bucket]
        if b["versionado"]:
            b["versiones"].setdefault(nombre, []).append({"id": f"d{self._tick().timestamp()}", "contenido": None, "marca": True, "t": self.reloj})
        else:
            b["versiones"].pop(nombre, None)

    def get_object(self, ns, bucket, nombre, version_id=None):
        versiones = self.buckets[bucket]["versiones"].get(nombre, [])
        if version_id:
            elegida = next((v for v in versiones if v["id"] == version_id), None)
        else:
            elegida = versiones[-1] if versiones and not versiones[-1]["marca"] else None
        if elegida is None or elegida["contenido"] is None:
            raise oci.exceptions.ServiceError(404, "NotFound", {}, "no existe")
        return SimpleNamespace(data=SimpleNamespace(content=elegida["contenido"]))

    def list_object_versions(self, ns, bucket, prefix=""):
        items = [SimpleNamespace(name=n, version_id=v["id"], is_delete_marker=v["marca"], time_created=v["t"])
                 for n, vs in self.buckets[bucket]["versiones"].items() if n.startswith(prefix) for v in vs]
        return SimpleNamespace(data=SimpleNamespace(items=items))

    def update_bucket(self, ns, bucket, detalles):
        self.buckets[bucket]["versionado"] = detalles.versioning == "Enabled"


@pytest.fixture
def entorno(monkeypatch, tmp_path):
    almacen = AlmacenFalso()
    almacen.crear(e.BUCKET, False)
    almacen.crear(e.COPIA, False)
    monkeypatch.setattr(e, "cliente", lambda: (almacen, "ns", "comp"))
    monkeypatch.setattr(e, "RESULTADOS", tmp_path)
    e.poblar()
    return almacen


def test_ronda_0_sin_versionado_ni_copia_se_pierde_todo(entorno):
    e.atacar(0)
    e.restaurar(0)
    e.medir(0)
    datos = e.estado()["ronda0"]
    assert datos["recuperados"] == 0
    assert all(origen == "ninguno" for origen in datos["origen"].values())


def test_ronda_1_con_versionado_y_copia_se_recupera_todo(entorno):
    e.mejorar()
    e.atacar(1)
    e.restaurar(1)
    datos = e.estado()["ronda1"]
    assert datos["recuperados"] == len(e.OBJETOS)
    assert set(datos["origen"].values()) == {"versión anterior"}
    e.medir(1)


def test_si_solo_hay_copia_aislada_se_recupera_desde_ella(entorno):
    for nombre, contenido in e.OBJETOS.items():
        entorno.put_object("ns", e.COPIA, nombre, contenido)   # copia sin versionado en el bucket principal
    e.atacar(1)
    e.restaurar(1)
    assert set(e.estado()["ronda1"]["origen"].values()) == {"copia aislada"}


def test_una_version_alterada_no_se_acepta_como_restauracion(entorno):
    e.mejorar()
    e.atacar(1)
    # un atacante sube basura como "última versión buena" a la copia y borra las versiones buenas
    entorno.buckets[e.BUCKET]["versiones"] = {n: [v for v in vs if v["contenido"] == e.BASURA or v["marca"]]
                                              for n, vs in entorno.buckets[e.BUCKET]["versiones"].items()}
    entorno.buckets[e.COPIA]["versiones"] = {n: [{"id": "x", "contenido": e.BASURA, "marca": False, "t": entorno.reloj}] for n in e.OBJETOS}
    e.restaurar(1)
    assert e.estado()["ronda1"]["recuperados"] == 0
