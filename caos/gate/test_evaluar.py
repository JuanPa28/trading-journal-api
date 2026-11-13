import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import evaluar  # noqa: E402

RESULTADOS = Path(__file__).resolve().parents[1] / "resultados"


def resumen(p95, fallos=0.0, peticiones=1000):
    return {"metrics": {
        "http_req_duration": {"med": 400, "p(95)": p95, "p(99)": p95 * 1.2, "max": p95 * 2},
        "http_req_failed": {"value": fallos},
        "http_reqs": {"count": peticiones, "rate": 20.0},
    }}


class LeerTest(unittest.TestCase):
    def test_extrae_las_senales_de_un_resumen_real_de_k6(self):
        real = json.loads((RESULTADOS / "estado-estable-1.json").read_text(encoding="utf-8"))

        senales = evaluar.leer(real)

        self.assertGreater(senales["peticiones"], 3000)
        self.assertEqual(round(senales["exito_pct"]), 100)
        self.assertLess(senales["p95_ms"], 1600)


class EvaluarTest(unittest.TestCase):
    def comprobar(self, p95, fallos=0.0):
        return evaluar.evaluar(evaluar.leer(resumen(p95, fallos)), p95_max=1600, exito_min=99)

    def test_el_estado_estable_aprueba(self):
        self.assertTrue(all(c["ok"] for c in self.comprobar(1289)))

    def test_un_p95_por_encima_del_umbral_rechaza(self):
        resultado = self.comprobar(4800)
        self.assertFalse(all(c["ok"] for c in resultado))
        self.assertFalse(resultado[1]["ok"])

    def test_un_exito_por_debajo_del_minimo_rechaza(self):
        resultado = self.comprobar(1000, fallos=0.05)
        self.assertFalse(resultado[0]["ok"])

    def test_justo_en_el_umbral_aprueba(self):
        self.assertTrue(all(c["ok"] for c in self.comprobar(1600)))


class InformeTest(unittest.TestCase):
    def test_el_informe_explica_por_que_fallo(self):
        senales = evaluar.leer(resumen(4800))
        texto = evaluar.informe(senales, evaluar.evaluar(senales, 1600, 99), "Prueba")

        self.assertIn("RECHAZADO", texto)
        self.assertIn("Por qué falló", texto)
        self.assertIn("Latencia p95", texto)

    def test_el_informe_aprobado_no_lista_motivos(self):
        senales = evaluar.leer(resumen(1000))
        texto = evaluar.informe(senales, evaluar.evaluar(senales, 1600, 99), "Prueba")

        self.assertIn("APROBADO", texto)
        self.assertNotIn("Por qué falló", texto)


class CodigoDeSalidaTest(unittest.TestCase):
    def ejecutar(self, p95, tmp):
        ruta = Path(tmp) / "resumen.json"
        ruta.write_text(json.dumps(resumen(p95)), encoding="utf-8")
        return evaluar.main(["--resumen", str(ruta), "--p95-max", "1600"])

    def test_sale_con_cero_si_se_cumple_y_con_uno_si_no(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(self.ejecutar(1000, tmp), 0)
            self.assertEqual(self.ejecutar(5000, tmp), 1)


if __name__ == "__main__":
    unittest.main()
