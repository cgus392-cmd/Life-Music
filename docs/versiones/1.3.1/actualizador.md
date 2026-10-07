# Ficha técnica · Actualizador renovado

**Versión:** 1.3.1 · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-07 · **Mesa de Ideas:** «Actualizador renovado»
**Referencia que le gustó a CG:** el changelog de Vivi, con pastillas por versión y una imagen por actualización.

## 1. Qué es

Rehacer cómo Life Music avisa, cuenta y descarga cada versión nueva:

- **Una imagen propia por versión,** en el estilo que eligió CG: acrílico transparente con
  cromo verde menta sobre fondo oscuro con grano. La de la 1.3.1 es el tocadiscos de acrílico
  (original en `Brand/novedades/1.3.1-tocadiscos-acrilico.jpg`).
- **Historial de versiones con pastillas** (`1.3.1`, `1.3.0`, `1.2.0`…). Cada una muestra su
  imagen, su resumen y sus funciones.
- Las 7 mejoras que aprobó CG:
  1. Descarga más liviana.
  2. Ventana con la cara de Life Music.
  3. Progreso de verdad, con reanudar.
  4. Instalar en un paso.
  5. Novedades bonitas.
  6. Solo con wifi y recordatorio suave.
  7. Verificar la descarga.

## 2. Cómo lo vive la persona

1. **Al abrir la app, si hay versión nueva:** en vez de la ventana sencilla sube una **hoja**
   con la imagen de la versión arriba, «Life Music 1.3.1», un resumen de una línea, las 3 a 5
   funciones con su ícono y el tamaño («41 MB»). Botones **Actualizar ahora** y **Más tarde**.
2. **Actualizar ahora:** la descarga sigue aunque cierres la app. La barra dice
   «18,4 de 41 MB · 2,1 MB/s · quedan 11 s», y la notificación también.
3. **Al terminar:** se comprueba que el APK bajó completo y es de Life Music, y se abre el
   instalador.
   - Si falta el permiso «instalar apps desconocidas», primero sale una tarjeta que explica por
     qué. Al volver de Ajustes, se instala sola.
4. **Más tarde:** esa versión no vuelve a interrumpir hasta dentro de 3 días. La notificación
   sale una sola vez.
5. **Ajustes → Actualizaciones:**
   - arriba, el estado (al día, hay versión, descargando o lista para instalar);
   - debajo, el **historial de versiones** con pastillas: tocar una muestra su imagen, su
     resumen, sus novedades y sus arreglos.
6. **Opción «Descargar solo con wifi»,** apagada de serie.

## 3. Decisiones técnicas

### 3.1 De dónde salen las novedades

| Qué | Fuente | Por qué |
|---|---|---|
| La versión **nueva**, que aún no está instalada | `lifemusic.pages.dev/novedades/<versión>.json` y su imagen `.webp` | La app no puede traerla dentro. Pages es rápido y se puede corregir sin tocar la publicación de GitHub |
| Las versiones **instaladas y anteriores** (historial) | El registro `Boletines` que ya está dentro de la app | Funciona sin internet, en los dos idiomas, y ya cubre desde la 1.1.5 |
| Si no hay `novedades.json` | El texto de la publicación de GitHub, como hoy | Nada se queda en blanco |

Formato de `novedades/1.3.1.json`:

```json
{
  "version": "1.3.1",
  "imagen": "https://lifemusic.pages.dev/novedades/1.3.1.webp",
  "es": { "resumen": "…", "funciones": [{ "icono": "tv", "titulo": "…", "texto": "…" }], "arreglos": ["…"] },
  "en": { "resumen": "…", "funciones": [ … ], "arreglos": [ … ] },
  "apk": {
    "lifemusic-arm64.apk": { "bytes": 40851687, "sha256": "…" },
    "lifemusic.apk": { "bytes": 69818668, "sha256": "…" }
  }
}
```

- `icono` es un nombre de una lista fija que la app conoce (tv, palette, update, music_note,
  tune…). Un nombre desconocido usa un ícono genérico.
- La imagen va en WebP de unos 1600 px de ancho y menos de 250 KB.
- La misma imagen entra en el APK como drawable, para el boletín y el historial sin internet.

### 3.2 Descarga más liviana

Si el teléfono es arm64 (`Build.SUPPORTED_ABIS[0] == "arm64-v8a"`) y la publicación trae
`lifemusic-arm64.apk`, se baja ese: 41 MB en vez de 70 MB. Si no, el universal, como hoy.
Los dos tienen la misma firma y el mismo versionCode, así que uno instala encima del otro.

### 3.3 Progreso de verdad y reanudar

`UpdateDownloadWorker` ya corre como servicio en primer plano.

- Cada medio segundo publica los bytes bajados, el total y la velocidad (media móvil). La
  pantalla y la notificación calculan de ahí los MB y el tiempo restante.
- Si se corta la red, el archivo parcial se guarda. Al reintentar se pide
  `Range: bytes=<bajados>-`, que GitHub soporta en su descarga, y si el servidor no lo acepta se
  empieza de cero.
- «Solo con wifi»: `NetworkType.UNMETERED` en las restricciones del trabajo.

### 3.4 Verificar antes de instalar

1. El tamaño coincide con el de la publicación (`size` del asset en GitHub).
2. Si `novedades.json` trae `sha256`, la huella coincide.
3. `PackageManager.getPackageArchiveInfo()`: el paquete es `com.cglabs.lifemusic` y su
   versionCode es mayor que el instalado.

Si algo falla, se borra el archivo y se ofrece «Descargar de nuevo». Así se evita el «error al
analizar el paquete» de un APK a medias.

### 3.5 Instalar en un paso

- Antes de pedir el permiso, una tarjeta explica: «Para instalar la versión nueva, Android te
  pedirá permitir que Life Music instale apps. Solo se usa para sus propias actualizaciones».
- Al volver a la app (`ON_RESUME`) con el permiso concedido y el APK listo, se lanza el
  instalador solo.

### 3.6 La hoja nueva

- Sustituye al `AlertDialog` de `MainActivity` y usa `ModalBottomSheet` con el estilo de la app.
- **La regla de siempre sigue:** solo sale si hay una versión publicada más nueva.
- Recordatorio suave: se guarda «pospuesta: versión y fecha», y no vuelve a salir para esa
  versión hasta 3 días después.

## 4. Qué se modifica

**Nuevo:**
- `web/novedades/1.3.1.json` y `web/novedades/1.3.1.webp`.
- `app/.../appcore/updater/Novedades.kt`: modelo, lectura del JSON y respaldo a GitHub.
- `app/.../appcore/updater/HojaDeActualizacion.kt`: la hoja nueva.
- `app/.../appcore/updater/HistorialDeVersiones.kt`: pastillas y detalle de cada versión.
- `res/drawable-nodpi/novedades_131.webp`: la imagen dentro del APK.

**Cambia:**
- `lifemusicupdater.kt`:
  - `checkForUpdate` lee `novedades.json` y elige el APK por ABI;
  - `UpdateScreen` se rehace con el estado arriba y el historial abajo;
  - verificación e instalación en un paso.
- `downloadmanager/UpdateDownloadWorker.kt`: bytes, velocidad, reanudar y solo wifi.
- `downloadmanager/downloadnotificationmanager.kt`: la notificación con MB y tiempo.
- `MainActivity.kt`: la hoja en vez del diálogo, y el «más tarde» de 3 días.
- `ui/screens/settings/UpdateSettings.kt`: el interruptor «Descargar solo con wifi».
- `Boletines.kt`:
  - `heroImagen` de la 1.3.1;
  - el historial lee de aquí;
  - se agrega el resumen corto por versión que el historial necesita.
- `life_strings.xml` (es y en).
- `RELEASE_INFO.md`: el ritual suma «subir `novedades/<versión>.json` y la imagen» antes del
  release.

## 5. Qué podría fallar

| Riesgo | Qué se ve | Cómo se cubre |
|---|---|---|
| Teléfono 32 bits con el APK de arm64 | No instala | Solo se elige arm64 si es el primer ABI del teléfono. Si no, el universal |
| GitHub no acepta reanudar | La descarga empieza de cero | Se detecta (respuesta 200 en vez de 206) y se baja entera |
| Límite de la API de GitHub (60 por hora por IP) | «No se pudo comprobar» | Se revisa cada 12 h, y el historial no usa la API: es local |
| `novedades.json` no se subió | La hoja sin funciones | Usa el texto de GitHub, como hoy |
| La imagen pesa o no carga | Hueco arriba | WebP de menos de 250 KB, con la marca de fondo mientras carga y si falla |
| Samsung o Xiaomi cambian el flujo del permiso | No vuelve sola a instalar | Queda el botón «Instalar» como hoy |
| Verificación demasiado estricta | Rechaza un APK bueno | El sha256 es opcional; tamaño y paquete siempre se revisan |

## 6. Cómo se prueba (fase 4)

- Con una versión de prueba en el teléfono, por ejemplo 1.3.0 contra una 1.3.1 publicada como
  prerelease beta:
  - la hoja sale;
  - el APK de arm64 baja (unos 41 MB);
  - el progreso muestra MB, velocidad y tiempo;
  - cortar el wifi a la mitad y volver: reanuda;
  - instala; sin el permiso, la tarjeta, Ajustes y la instalación sola al volver.
- **Más tarde:** no vuelve a salir al abrir la app de nuevo. Adelantando la fecha 3 días, sí.
- **Solo con wifi:** con datos móviles espera y con wifi baja.
- **APK cortado a propósito** (truncar el archivo): no lo instala y ofrece bajarlo de nuevo.
- **Historial:** las pastillas desde 1.1.5 hasta 1.3.1, sin internet.
- **Sin `novedades.json`:** el texto de GitHub, como hoy.

## 7. Orden de ejecución (fase 3)

| Tanda | Qué | Se prueba |
|---|---|---|
| 1 | Imagen a WebP, `novedades/1.3.1.json`, `Novedades.kt` y elección del APK por ABI | Lectura del JSON y del APK elegido, en un test |
| 2 | Descarga: progreso real, reanudar, solo wifi y verificación | En el teléfono, con una publicación de prueba |
| 3 | La hoja nueva y el «más tarde» de 3 días | Al abrir la app |
| 4 | `UpdateScreen` nueva con el historial de pastillas | En Ajustes |
| 5 | Instalar en un paso, textos y ritual en RELEASE_INFO | Flujo completo |
