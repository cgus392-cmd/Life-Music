# Publicar una versión

Proceso de publicación de Life Music. Sigue la lista de comprobación de día de
publicación de CG LABS, adaptada a Android.

## Antes de etiquetar

- [ ] **Subir el número de versión** en `app/build.gradle.kts`: `versionCode` y
      `versionName`. El código de versión **siempre sube**; Android no permite
      instalar uno menor que el ya presente.
- [ ] **`CHANGELOG.md` actualizado**, agrupado por Añadido / Cambiado /
      Corregido, escrito para quien *usa* la aplicación y no para quien la
      programa.
- [ ] **`README.md` refleja el estado real** — que no prometa funciones que no
      están, ni calle las que sí.
- [ ] **Boletín de la versión**, si hay algo que contar: cadenas
      `boletin_vXYZ_*` en `values/life_strings.xml` y `values-es/`, y una
      entrada en `ui/screens/Boletines.kt` con el `versionCode` nuevo. Sale
      encima de Inicio la primera vez que arranca la versión tras actualizar
      (la bienvenida solo sale en instalaciones nuevas) y se puede releer en
      Ajustes → Actualizaciones → Versión. Una versión sin entrada no
      interrumpe a nadie.
- [ ] **Compilación limpia en modo publicación**, sin avisos nuevos sin revisar.
- [ ] **Pruebas en verde**: `./gradlew :playback:testFossDebugUnitTest`.
- [ ] **Historial escaneado por secretos**. El repositorio es público: nunca dar
      por hecho que está limpio.
- [ ] **Probado en un dispositivo real**, no solo compilando.

## Sobre el versionado

[Semver](https://semver.org/lang/es/): `MAYOR.MENOR.PARCHE`.

Life Music empezó su propia numeración en **1.0.0 con código 1**. Heredaba el 152
de Echo Music, y conservarlo habría dejado sin camino a cualquier versión
posterior que contara desde uno.

Los códigos de versión van de uno en uno, en el mismo orden que las etiquetas.

## Generar los artefactos

```bash
./gradlew clean
./gradlew assembleArm64FossRelease
./gradlew assembleUniversalFossRelease
```

El **universal** es el que se ofrece por defecto en la publicación: incluye las
cuatro arquitecturas y evita que nadie tenga que averiguar cuál le toca. El
`arm64` se adjunta para quien quiera un archivo bastante más pequeño.

## Publicar

```bash
git tag -a v1.0.0 -m "Life Music 1.0.0"
git push origin v1.0.0
```

```bash
gh release create v1.0.0 --title "Life Music 1.0.0" --notes-file notas.md app/build/outputs/apk/universalFoss/release/*.apk
```

Las notas de la publicación salen del `CHANGELOG.md`, no se escriben aparte.

**Sobre el nombre del archivo:** el actualizador prefiere el declarado en
`constants/Repo.kt` (`lifemusic.apk`), pero si no lo encuentra se queda con
cualquier `.apk` de la publicación que no lleve «debug» en el nombre. Así una
publicación con el nombre cambiado sigue funcionando, y el nombre canónico manda
cuando está.

**Las notas salen del cuerpo de la publicación.** No hace falta subir un
`changelog.json` aparte: el actualizador lo usa si existe, y si no, muestra el
cuerpo del release. Una cosa menos que recordar.

## Guardar el mapping antes de que se pierda

`app/build/outputs/mapping/<variante>/mapping.txt` traduce las trazas ofuscadas de
esa compilación **y solo de esa**. La siguiente compilación lo sobrescribe, y un
`clean` lo borra. Sin él, un informe de fallo de una versión ya publicada es
ilegible para siempre.

Se guarda fuera del repositorio —pesa unos 170 MB, y comprimido baja a 11— junto
al keystore:

```bash
mkdir -p "../../_Mappings/v1.1.3"
gzip -c app/build/outputs/mapping/universalFossRelease/mapping.txt > "../../_Mappings/v1.1.3/universalFossRelease-mapping.txt.gz"
```

Para leer un informe: `python tools/retrace.py informe.txt mapping.txt`.

**Antes de creerse el resultado**, comprobar que el `pg_map_id` de la cabecera del
mapping es el mismo que aparece en la traza. Si no coinciden, el mapping es de otra
compilación y lo que salga será ficción con aspecto de respuesta.

## Después

- [ ] Instalar el APK publicado **sobre una versión anterior**, no en limpio.
      Es la única forma de comprobar que las migraciones de Room y el código de
      versión están bien.
- [ ] Comprobar que la pantalla «Acerca de» muestra la versión nueva.
- [ ] **`mapping.txt` archivado** para la etiqueta recién publicada.
