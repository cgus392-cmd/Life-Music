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

## Después

- [ ] Instalar el APK publicado **sobre una versión anterior**, no en limpio.
      Es la única forma de comprobar que las migraciones de Room y el código de
      versión están bien.
- [ ] Comprobar que la pantalla «Acerca de» muestra la versión nueva.
