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
    assert datos["recuperados"] == len(e.cargar_objetos())
    assert set(datos["origen"].values()) == {"versión anterior"}
    e.medir(1)


def test_si_solo_hay_copia_aislada_se_recupera_desde_ella(entorno):
    for nombre, contenido in e.cargar_objetos().items():
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
    entorno.buckets[e.COPIA]["versiones"] = {n: [{"id": "x", "contenido": e.BASURA, "marca": False, "t": entorno.reloj}] for n in e.cargar_objetos()}
    e.restaurar(1)
    assert e.estado()["ronda1"]["recuperados"] == 0


def test_un_aporte_con_hash_correcto_reemplaza_el_objeto_de_esa_api(tmp_path):
    carpeta = tmp_path / "objetos" / "statsnostra"
    carpeta.mkdir(parents=True)
    contenido = b'{"partido": "real-shape", "prediccion": "3-0"}\n'
    (carpeta / "prediccion.json").write_bytes(contenido)
    (carpeta / "prediccion.json.sha256").write_text(e.sha(contenido) + "  prediccion.json\n", encoding="utf-8")
    objetos = e.cargar_objetos(tmp_path / "objetos")
    assert objetos["statsnostra/prediccion.json"] == contenido
    assert "statsnostra/prediccion-001.json" not in objetos
    assert "trading-journal/adjunto-001.txt" in objetos and "ecommerce/pedido-001.json" in objetos


def test_un_aporte_con_hash_alterado_se_rechaza(tmp_path):
    carpeta = tmp_path / "objetos" / "statsnostra"
    carpeta.mkdir(parents=True)
    (carpeta / "prediccion.json").write_bytes(b"contenido")
    (carpeta / "prediccion.json.sha256").write_text("0" * 64 + "\n", encoding="utf-8")
    with pytest.raises(SystemExit):
        e.cargar_objetos(tmp_path / "objetos")


def test_sin_carpeta_de_aportes_quedan_los_objetos_por_defecto(tmp_path):
    assert e.cargar_objetos(tmp_path / "no-existe") == e.OBJETOS


def test_las_versiones_sin_fecha_tambien_se_restauran(entorno, monkeypatch):
    """La API real de Oracle devuelve time_created vacío en el listado de versiones."""
    original = entorno.list_object_versions

    def sin_fecha(ns, bucket, prefix=""):
        resultado = original(ns, bucket, prefix)
        for v in resultado.data.items:
            v.time_created = None
        return resultado

    monkeypatch.setattr(entorno, "list_object_versions", sin_fecha)
    e.mejorar()
    e.atacar(1)
    e.restaurar(1)
    assert e.estado()["ronda1"]["recuperados"] == len(e.cargar_objetos())
