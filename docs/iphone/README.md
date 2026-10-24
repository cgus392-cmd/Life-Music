# Life Music para iPhone · arranque de la sesión

Esta es la guía con la que arranca la sesión **«Life Music · iPhone»**. La abrió CG la noche del 23-10-2026, con
una orden clara: desde ahora el iPhone tiene su propio chat, y General («el chat raíz») se dedica **solo a Android**.

> Lo que pidió CG: «la dedicaremos de ahora en adelante para iPhone, no sé cómo le haremos, pero algo podemos
> entregar funcional y cumpla el objetivo».

## 1. Primero, con CG

1. **Confirmar el objetivo.** Lo más probable es que la gente con iPhone pueda escuchar con Life Music, y que siga
   sonando con la pantalla bloqueada. Hay que preguntarle qué es lo mínimo que daría por cumplido.
2. **Presentarle los caminos** (sección 4) con sus pros y contras reales, y que él elija. No decidir solo.
3. **Trabajar en tandas cortas** (10–20 min) y avisar si algo va a tardar más.

## 2. Lo que ya pasó (no repetir sin saberlo)

- **11-09: el port nativo se aplazó.** Con Kotlin Multiplatform y Compose Multiplatform se pueden compartir
  `innertube`, los modelos, Room, DataStore y el DSP puro. El motor de reproducción, en cambio, habría que
  reescribirlo sobre AVFoundation, porque Media3 (`MusicService`, el módulo `playback`, MediaSession y las descargas)
  es solo de Android. **La App Store la rechazaría** (guías 5.2.3 y 5.2.2), por la misma razón por la que no está en
  Google Play. Fuera de la tienda solo queda instalarla por fuera: AltStore o SideStore, con una firma que dura
  7 días si se usa un Apple ID gratis.
- **02-10: Life Music Web (PWA)** en `Echo-Music/webapp/` (commit local 376813a0, **nunca desplegada**). Está hecha
  así:
  - `public/` tiene HTML, CSS y JS sin compilar, el manifiesto y `sw.js`;
  - `functions/api/{buscar,radio,sugerencias}.js` y `lib/yt.js` corren en Cloudflare Pages Functions con WEB_REMIX;
  - el audio suena en el **reproductor oficial de YouTube incrustado y visible**, de al menos 200×200, que es lo más
    seguro legalmente;
  - hay cola con radio infinita, Me gusta y la última sesión guardada en localStorage.
- **07-10: CG la ARCHIVÓ** porque **en iPhone no suena en segundo plano**: Apple corta el audio de los videos
  incrustados al bloquear la pantalla. Esa es la vara: cualquier camino nuevo tiene que resolver el segundo plano o
  decirle a CG, de frente, que no se puede.

## 3. Lo que no cambia

- **CG no tiene Mac ni iPhone.** Las pruebas las hace un probador con iPhone (hay probadores externos que mandan
  informes) o se hacen en la nube. Hay que planear cómo se prueba cada cosa antes de construirla.
- **Sin costos sin su sí:** nunca generar cobros en Cloudflare, y no abrir cuentas de pago (Apple Developer cuesta
  unos 99 USD al año) sin que CG lo diga.
- **No se proxea el audio desde un servidor:** YouTube bloquea las IP de los centros de datos y CG quedaría como
  redistribuidor.
- **Publicar** (desplegar, hacer push, tag o release) **solo con un «publica» de CG** en ese mismo ciclo.
- Nunca escribir contraseñas, claves ni tokens. Nada de telemetría ni analítica.
- **Es un proyecto educativo y no comercial**, con la licencia GPL-3.0 heredada de Echo-Music.
- **Marca:** la identidad se llama «Pulso». El logo «La nota es el pulso» está en
  `Brand/logo/2026-10-22-nota-pulso/` (con su `LEEME.md`), la letra es Geist y nada de patrones de diseño de IA.
  **Todo lo de diseño, íconos y gráficos va a la sesión «Life Music · Diseño y marca».**
- **Tono:** en español y con cariño, más cuando CG anda cansado o frustrado. Cada avance cierra diciendo si hay algo
  para probar en iPhone (sí: cuál y cómo / no todavía).
- **Coordinación:** lo que toque piezas compartidas (`innertube`, el servidor de la comunidad, lifemusic.app) se
  avisa a General con un mensaje entre sesiones antes de cambiarlo.

## 4. Caminos para evaluar con CG

Hay que investigar y verificar el estado actual de iOS; no se vale suponerlo. Cada camino se mide con lo mismo:
cómo lo instala la gente, si suena en segundo plano, si es legal y respeta las reglas de YouTube, cuánto cuesta y
cómo lo prueba CG sin iPhone.

1. **La web, mejorada.** Ver qué permite hoy iOS con el audio en segundo plano en Safari y en las PWA instaladas
   (Media Session, `<audio>`), y si hay una forma limpia de usarlo sin proxear audio.
2. **App nativa compilada en la nube**, con equipos macOS de CI. Se instalaría por fuera con AltStore o SideStore,
   o por TestFlight, que pide la cuenta de pago y tiene su propia revisión.
3. **Otra idea** que salga de investigar. Se trae con datos, no con promesas.

## 5. Dónde está todo

- **Repositorio Android:** `Echo-Music/`.
  - El módulo `innertube` no depende de Android y se podría compartir.
  - La web archivada está en `webapp/`.
  - Las fichas de cada versión están en `docs/versiones/`.
- **La página y los servidores:** `Echo-Music/web/` (lifemusic.app en Cloudflare Pages) y los workers `comunidad`
  y `equipo`.
- **Memoria compartida:** las sesiones de esta carpeta comparten la memoria del proyecto. Las notas
  `port-ios-aplazado`, `life-music-web-iphone` y `sesion-iphone` tienen el contexto. Lo nuevo de iPhone se anota en
  `sesion-iphone`.
