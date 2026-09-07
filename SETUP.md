# Compilar Life Music

## Requisitos

| | |
|---|---|
| JDK | **21** |
| Android SDK | 36 |
| NDK | 27.0.12077973 |
| Gradle | 9.3.1 (incluido en el envoltorio) |

Android Studio los instala todos. Si compila desde la línea de órdenes,
asegúrese de que `JAVA_HOME` apunta al JDK 21 — **no basta con que `java -version`
diga algo razonable**: en muchos equipos el `java` del PATH es un JRE viejo y
Gradle falla con un error que no menciona la versión.

## Compilar

```bash
git clone https://github.com/cgus392-cmd/Life-Music.git
cd Life-Music
./gradlew assembleUniversalFossDebug
```

El APK queda en `app/build/outputs/apk/universalFoss/debug/`.

La primera compilación tarda —del orden de diez minutos— porque hay 18 módulos y
KSP tiene que generar el código de Room y Hilt.

## Variantes

Dos dimensiones de sabor, que se combinan:

**Servicios:**
- **`foss`** *(por defecto)* — sin dependencias de Google.
- **`gms`** — con servicios de Google.

**Arquitectura:** `arm64`, `arm`, `x86`, `x64`, `universal`.

Para un teléfono actual, `arm64` es lo normal y pesa bastante menos. El
`universal` incluye las cuatro y sirve para no preguntarse cuál toca.

```bash
./gradlew assembleArm64FossRelease     # lo habitual
./gradlew assembleUniversalFossDebug   # para desarrollar
```

## Pruebas

```bash
./gradlew :playback:testFossDebugUnitTest
```

Ojo con el nombre: **`testDebugUnitTest` no existe** y Gradle responde que la
tarea es ambigua, porque los sabores obligan a decir cuál.

## Estructura

18 módulos Gradle. Los importantes:

| Módulo | Qué hay |
|---|---|
| `app` | interfaz, pantallas, servicio de reproducción |
| `core` | base de datos, preferencias, modelos |
| `playback` | procesamiento de audio, ecualizador, análisis de ritmo |
| `innertube` | cliente de la API de YouTube |
| `lyrics` | agregador de proveedores de letra |
| el resto | un proveedor externo cada uno |

**Añadir una función no obliga a tocar el núcleo:** módulo nuevo →
`settings.gradle.kts` → `implementation(project(":x"))` → cablear con Hilt.

## Configuración opcional

Copie `gradle.properties.template` y `local.properties.template` quitándoles el
sufijo si necesita ajustar algo. La compilación funciona sin tocarlos.

**No hace falta `google-services.json`**, y no debe añadirse: el proyecto se
compila sin Firebase a propósito. Si aparece uno, Gradle activará Google Services
y Crashlytics, que es justo lo que Life Music quitó.

## Problemas conocidos

**El envoltorio de Gradle agota el tiempo al descargarse** si la red va lenta:
tiene un límite de 10 segundos. Descargue el zip a mano a
`~/.gradle/wrapper/dists/<version>/<hash>/`, descomprímalo y cree el fichero
marcador `.ok` en esa carpeta.

**Tras renombrar paquetes o cambiar el esquema de Room**, un `./gradlew clean` es
obligatorio: quedan fuentes generadas por Hilt apuntando al paquete anterior y el
error que sale no dice eso en ninguna parte.
