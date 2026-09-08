#!/usr/bin/env python3
"""Recompone una traza ofuscada de Life Music contra su mapping.txt.

    python tools/retrace.py informe.txt app/build/outputs/mapping/universalFossRelease/mapping.txt

Por que existe: R8 se descarga durante la compilacion y no queda un jar suelto en
la cache de Gradle, asi que `com.android.tools.r8.retrace.Retrace` no esta
disponible sin red. Esto lee el mapping directamente.

Lo que no es evidente del formato, y por lo que un primer intento fallo: R8
codifica los marcos insertados (inlining) como VARIAS entradas con el MISMO rango
de linea ofuscado, de la mas interna a la mas externa. Buscar solo por nombre de
clase enga~na, porque R8 reutiliza y fusiona clases de lambda; lo que desambigua
es el rango de linea. Y cuando el rango original es un unico numero, la linea es
esa y no se interpola.

Antes de usarlo, comprobar que el `# pg_map_id:` de la cabecera del mapping
coincide con el que aparece en la traza: si no, el mapping es de otra compilacion
y el resultado seria pura ficcion.
"""
import re
import sys

FRAME = re.compile(r'^\s*at ([\w.$]+)\.([\w$<>]+)\(r8-map-id-[0-9a-f]+:(\d+)\)')

# [inicioOfus:finOfus:]tipoRetorno nombre(args)[:inicioOrig[:finOrig]] -> nombreOfus
METHOD = re.compile(
    r'^\s+(?:(\d+):(\d+):)?'
    r'([\w.$\[\]]+)\s+'
    r'([\w.$<>]+)'
    r'\((.*?)\)'
    r'(?::(\d+)(?::(\d+))?)?'
    r'\s+->\s+([\w$<>]+)\s*$'
)


def cargar_marcos(ruta):
    marcos = []
    with open(ruta, encoding='utf-8', errors='replace') as f:
        for linea in f:
            m = FRAME.match(linea)
            if m:
                marcos.append((m.group(1), m.group(2), int(m.group(3))))
    return marcos


def cargar_clases(ruta, buscadas):
    """Una sola pasada: el mapping puede pesar cientos de MB."""
    clases, actual = {}, None
    with open(ruta, encoding='utf-8', errors='replace') as f:
        for linea in f:
            if not linea.startswith((' ', '\t', '#')):
                actual = None
                if linea.rstrip().endswith(':') and ' -> ' in linea:
                    original, ofuscada = linea.rstrip()[:-1].split(' -> ')
                    if ofuscada in buscadas:
                        actual = (original, [])
                        clases[ofuscada] = actual
                continue
            if actual is None or linea.lstrip().startswith('#'):
                continue
            m = METHOD.match(linea)
            if m:
                ini_o, fin_o, _ret, nombre, _args, ini, fin = m.group(1, 2, 3, 4, 5, 6, 7)
                actual[1].append(dict(
                    obf_ini=int(ini_o) if ini_o else None,
                    obf_fin=int(fin_o) if fin_o else None,
                    nombre=nombre,
                    orig_ini=int(ini) if ini else None,
                    orig_fin=int(fin) if fin else None,
                    obf_nombre=m.group(8),
                ))
    return clases


def linea_original(entrada, linea):
    if entrada['orig_ini'] is None:
        return linea
    if entrada['orig_fin'] is None or entrada['orig_fin'] == entrada['orig_ini']:
        return entrada['orig_ini']          # forma colapsada: no se interpola
    return entrada['orig_ini'] + (linea - entrada['obf_ini'])


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    marcos = cargar_marcos(sys.argv[1])
    if not marcos:
        print("No hay marcos con r8-map-id en ese informe.")
        return 1
    clases = cargar_clases(sys.argv[2], {c for c, _, _ in marcos})

    for obf_clase, obf_metodo, linea in marcos:
        entrada = clases.get(obf_clase)
        if entrada is None:
            print(f"  ??  {obf_clase}.{obf_metodo}:{linea}")
            continue
        clase, metodos = entrada
        candidatos = [e for e in metodos
                      if e['obf_nombre'] == obf_metodo and e['obf_ini'] is not None
                      and e['obf_ini'] <= linea <= e['obf_fin']]
        if not candidatos:
            candidatos = [e for e in metodos
                          if e['obf_nombre'] == obf_metodo and e['obf_ini'] is None]
        if not candidatos:
            print(f"  ??  {clase}.{obf_metodo}:{linea}   (sin entrada de linea)")
            continue
        for i, e in enumerate(candidatos):     # varias entradas = marcos insertados
            nombre, propietaria = e['nombre'], clase
            if '.' in nombre:                  # insertado desde otra clase
                propietaria, nombre = nombre.rsplit('.', 1)
            print(f"{'     ' if i else '  at '}{propietaria}.{nombre}:{linea_original(e, linea)}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
