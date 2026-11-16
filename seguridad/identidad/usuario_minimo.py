#!/usr/bin/env python3
"""Mejora 2 del experimento 1: la API deja de usar el usuario ADMIN de la base de datos.

Hoy el secreto `db-password` de la Container App es la contraseña de ADMIN (privilegio máximo): si
alguien lista los secretos, se lleva la llave de toda la base. Esta mejora crea el usuario TJ_APP
con permiso de conectarse y de leer y escribir (SELECT, INSERT, UPDATE, DELETE) solo sobre las tablas del
esquema ADMIN, sin crear tablas ni tocar otros esquemas, y cambia la API para usarlo
(D3FEND: User Account Permissions, D3-UAP; ISO 27001:2022 A.8.2; NIST 800-53 AC-6).

  aplicar    Crea TJ_APP con una contraseña aleatoria, concede lo mínimo, prueba la conexión y cambia
             la Container App (secreto db-password, DB_USERNAME y esquema por defecto).
  revertir   Devuelve la Container App a ADMIN y elimina TJ_APP.

Requiere: pip install oracledb. Las contraseñas nunca se imprimen.
"""
from __future__ import annotations

import argparse
import secrets
import string
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "caos" / "azure"))
import deploy  # noqa: E402  (reutiliza az, tfvar, jdbc_url, set_env)

USUARIO = "TJ_APP"

CONCEDER = """
DECLARE
  v_usuario CONSTANT VARCHAR2(30) := '%s';
BEGIN
  FOR t IN (SELECT table_name FROM all_tables WHERE owner = 'ADMIN') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ADMIN."' || t.table_name || '" TO ' || v_usuario;
  END LOOP;
  FOR s IN (SELECT sequence_name FROM all_sequences WHERE sequence_owner = 'ADMIN') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT ON ADMIN."' || s.sequence_name || '" TO ' || v_usuario;
  END LOOP;
END;"""


def conectar(usuario: str, clave: str):
    import oracledb
    dsn = deploy.jdbc_url().split("@", 1)[1]
    return oracledb.connect(user=usuario, password=clave, dsn=dsn)


def contrasena() -> str:
    alfabeto = string.ascii_letters + string.digits
    return "Aa1#" + "".join(secrets.choice(alfabeto) for _ in range(28))


def aplicar() -> None:
    clave = contrasena()
    with conectar("ADMIN", deploy.tfvar("db_admin_password")) as admin:
        cur = admin.cursor()
        existe = cur.execute("SELECT COUNT(*) FROM dba_users WHERE username = :u", u=USUARIO).fetchone()[0]
        if existe:
            cur.execute(f'ALTER USER {USUARIO} IDENTIFIED BY "{clave}"')
        else:
            cur.execute(f'CREATE USER {USUARIO} IDENTIFIED BY "{clave}"')
        cur.execute(f"GRANT CREATE SESSION TO {USUARIO}")
        cur.execute(CONCEDER % USUARIO)
    with conectar(USUARIO, clave) as prueba:  # el usuario nuevo debe poder leer el esquema
        tablas = prueba.cursor().execute("SELECT COUNT(*) FROM all_tables WHERE owner = 'ADMIN'").fetchone()[0]
    print(f"{USUARIO} creado y probado: ve {tablas} tablas del esquema ADMIN")
    deploy.az("containerapp", "secret", "set", "-g", deploy.RESOURCE_GROUP, "-n", deploy.APP,
              "--secrets", f"db-password={clave}", "-o", "none")
    deploy.set_env({"DB_USERNAME": USUARIO,
                    "SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA": "ADMIN",
                    "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate"})


def revertir() -> None:
    deploy.az("containerapp", "secret", "set", "-g", deploy.RESOURCE_GROUP, "-n", deploy.APP,
              "--secrets", f"db-password={deploy.tfvar('db_admin_password')}", "-o", "none")
    deploy.az("containerapp", "update", "-g", deploy.RESOURCE_GROUP, "-n", deploy.APP, "--remove-env-vars",
              "SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA", "-o", "none", check=False)
    deploy.set_env({"DB_USERNAME": "ADMIN", "SPRING_JPA_HIBERNATE_DDL_AUTO": "update"})
    with conectar("ADMIN", deploy.tfvar("db_admin_password")) as admin:
        admin.cursor().execute(f"DROP USER {USUARIO} CASCADE")
    print(f"{USUARIO} eliminado; la API vuelve a ADMIN")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("accion", choices=["aplicar", "revertir"])
    {"aplicar": aplicar, "revertir": revertir}[parser.parse_args().accion]()
