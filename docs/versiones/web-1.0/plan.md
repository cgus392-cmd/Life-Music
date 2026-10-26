# Plan técnico · Life Music Web 1.0

**Versión:** web 1.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobado por CG el 23-10 (noche), con el
reproductor escondido y los tres resguardos. Las decisiones de la sección 8 siguen abiertas.
**Idea de:** CG, 2026-10-23 · **Entrega prometida:** lunes 26-10 · **Sesión:** «Life Music · iPhone»

> **Decisión final de CG (24-10, madrugada).** Se queda el **reproductor escondido**. Antes se evaluó el reproductor
> a la vista mostrando la portada de la canción, que sí cumple las reglas, pero CG lo descartó porque se nota que es
> YouTube. A cambio, la web va **aislada en su propia cuenta de Cloudflare**, gratis y creada por CG, con su propia
> dirección `.pages.dev` y sin tocar lifemusic.app. Si un día YouTube se queja o la bloquea, solo cae esta web: la
> landing, el relevo del TV, la comunidad y la app Android no se tocan.

> **Corrección del 23-10.** La primera versión de este plan mostraba el video de YouTube en pantalla. CG la
> rechazó, y con razón: eso no es un reproductor de música. Desde esta versión **nunca se ve un video**. La persona
> ve la carátula de YouTube Music y nuestros controles, como en la app Android.

## 1. Qué es

Un **reproductor web completo, al estilo del de Spotify**, que funcione igual de bien en un computador que en un
teléfono. No es la página sencilla del 02-10: su diseño se descarta y de su código solo se reaprovechan las
funciones que le preguntan a YouTube Music. Es una plataforma para escuchar en línea: buscar, explorar, armar la
cola, guardar lo que gusta y volver.

El diseño sale de la identidad **Pulso**, y lo hace la sesión «Life Music · Diseño y marca» después de aprobar este
plan. Aquí solo va la estructura: qué tiene, cómo se comporta y cómo se construye.

## 2. Cómo suena (y por qué así)

**Por qué no se hace como en Android.** La app Android le pide la canción directo a YouTube. Una página web no
puede hacer esa petición. Se probó el 23-10: YouTube contesta 403 a toda petición que llegue desde otra web, en
`youtubei.googleapis.com`, en music y en www, con o sin preflight. Un servidor tampoco sirve de puente, porque el
archivo viene atado a la IP de quien lo pide y habría que proxear el audio, algo prohibido.

**Cómo se hace.** Suena el reproductor oficial de YouTube, pero **escondido**: solo funciona como parlante. Lo que
se ve es Life Music, con la carátula, el título, el artista, la barra de tiempo y nuestros controles.
- Se probó el 23-10 en Chromium: suena tapado por la carátula, fuera de la pantalla y en 1×1 px.
- Se prefieren las versiones de canción, que traen la portada cuadrada del álbum. Si es un video musical, suena su
  audio y su imagen se recorta como carátula cuadrada. **Nunca se ve el video.**

**Lo que eso implica:**
- Va contra las reglas de YouTube para desarrolladores: III.I.9 prohíbe el reproductor que no se ve y III.I.7
  separar el audio. CG lo decidió sabiéndolo. Es la misma zona gris de la app Android, en versión más suave: aquí
  YouTube cuenta la reproducción y pone sus anuncios.
- **En el computador** sigue sonando con la pestaña en segundo plano y pasa sola a la siguiente canción.
- **En el iPhone** suena con la pantalla encendida (hay un botón para mantenerla así). **Al bloquear la pantalla
  se pausa**, y la web lo avisa una vez.

### 2.1 Los tres resguardos (aprobados)

1. **Anuncios a la vista.** Si YouTube mete un anuncio, el reproductor de YouTube aparece para que la persona lo
   vea y lo pueda saltar, y al terminar se vuelve a esconder.
   - **Cómo se detecta:** la duración del reproductor no coincide con la de la canción que pedimos (más de 3 s de
     diferencia mientras suena).
   - **Si no queda confiable,** se le avisa a CG.
2. **Interruptor de emergencia.** El archivo `ajustes-remotos.json` publicado con la web trae
   `"reproductorVisible": false`. Si YouTube cambia algo, se pone en `true` y se vuelve a publicar: en minutos la
   web pasa a mostrar el reproductor sin rehacer nada.
3. **Perfil bajo.** Sin anuncios nuestros, sin cobrar y sin presentarla como «YouTube Music gratis sin anuncios».
   Lleva el aviso legal claro y los créditos.

## 3. Cómo se organiza (pantallas)

### 3.1 En el computador (desde 1024 px de ancho)

| Zona | Qué lleva |
|---|---|
| **Columna izquierda** | El logo, Inicio, Buscar y Biblioteca (Me gusta, Mis listas e Historial). Al fondo, Ajustes y Acerca de. |
| **Centro** | La pantalla que se esté viendo: Inicio, Buscar, Álbum, Artista, Lista o Biblioteca. |
| **Columna derecha: Ahora suena** | La **carátula grande**, el título, el artista y Me gusta. Debajo, la cola: «Sonando», «A continuación» y la radio. |
| **Barra de abajo** | Anterior, play/pausa y siguiente, la barra de tiempo, aleatorio, repetir y volumen. |

### 3.2 En el teléfono (menos de 768 px)

- Abajo, la navegación con Inicio, Buscar y Biblioteca, y encima un reproductor pequeño con la carátula, el título
  y play/pausa.
- Al tocar el reproductor pequeño se abre **Ahora suena** a pantalla completa, con la carátula grande, los controles
  y la cola.

### 3.3 Las rutas (enlaces que se pueden compartir)

`/` Inicio · `/buscar?q=` · `/album/:id` · `/artista/:id` · `/lista/:id` · `/biblioteca` · `/ajustes` ·
`/acerca`.

## 4. Lo que trae la 1.0: cinco bloques

La regla de la casa es de 3 a 5 funciones por versión, así que la 1.0 trae cinco bloques. Además lleva una base
obligatoria de la marca (sección 4.6), que no cuenta como función.

### 4.1 Reproductor

- **Qué hace:**
  - Hay un solo reproductor de YouTube para toda la web. Se crea una vez, vive escondido fuera de la pantalla y
    nunca se recarga al cambiar de pantalla.
  - Nuestros controles lo manejan: barra, teclas (espacio, flechas, M para silenciar, L para Me gusta) y Media
    Session, para que las teclas multimedia y los controles del sistema muestren la canción.
  - Los resguardos 1 y 2 viven aquí.
  - Si una canción no se deja reproducir (errores 2, 5, 100, 101 y 150), la salta sola y lo dice con palabras.
  - En iPhone y Android trae «Mantener la pantalla encendida» (Screen Wake Lock).
- **Qué se modifica:** `webapp/src/reproductor/`
  - `youtube.ts`: el motor escondido y la detección de anuncios.
  - `tienda.ts`: la cola y el estado.
  - `sesion.ts` (Media Session) y `atajos.ts`.
- **Qué podría fallar:**
  - Mover el iframe dentro de la página lo recarga y corta la canción. Por eso vive en un nodo fijo que React no
    toca.
  - Que YouTube empiece a frenar los reproductores escondidos. Para eso está el resguardo 2.
  - En iPhone, que Safari no deje arrancar la primera canción desde nuestro botón o no deje sonar un reproductor
    fuera de la pantalla. Lo prueba el probador.
  - Con la pestaña en segundo plano, Chrome frena los temporizadores. Pasar de canción depende del evento «terminó»
    de YouTube, no de un reloj.
- **Cómo se prueba:**
  - En el PC: 15 minutos con la pestaña escondida, y que pasen solas al menos tres canciones.
  - Teclas multimedia y atajos.
  - Forzar un anuncio y ver que el reproductor aparece y se vuelve a esconder.
  - En iPhone: la primera canción, el botón de pantalla encendida y qué pasa al bloquear.

### 4.2 Buscar

- **Qué hace:**
  - Mientras se escribe, salen sugerencias.
  - Los resultados vienen por tipo: canciones, artistas, álbumes y listas, con un filtro para cada uno.
  - La última búsqueda se recuerda.
- **Qué se modifica:**
  - `functions/api/buscar` y `sugerencias`, que vienen de la versión del 02-10.
  - La pantalla `src/pantallas/Buscar.tsx`.
- **Qué podría fallar:**
  - Que YouTube frene las peticiones que salen de Cloudflare. Es el riesgo n.º 1.
  - Que cambien la forma de sus respuestas.
- **Cómo se prueba:**
  - Diez búsquedas variadas (artista, canción, álbum, con tildes y en inglés).
  - Que la respuesta llegue en menos de 1 s con la caché caliente.

### 4.3 Cola y radio

- **Qué hace:**
  - Al tocar una canción arranca su **radio infinita**, con el mismo «next» de YouTube Music que usa la app.
  - Se puede «Reproducir a continuación» y «Agregar a la cola», reordenar arrastrando y quitar canciones.
  - Trae aleatorio y repetir (una o todas).
- **Qué se modifica:**
  - `functions/api/radio`.
  - `src/reproductor/tienda.ts`.
  - El panel de la cola en «Ahora suena».
- **Qué podría fallar:**
  - Que la radio repita canciones: se filtran las que ya sonaron en la sesión.
  - Que la radio se quede sin canciones: se pide más cuando quedan 3.
- **Cómo se prueba:**
  - Una hora de radio sin repetir canción ni quedarse en silencio.
  - Reordenar y quitar con mouse y con teclado.

### 4.4 Inicio y explorar

- **Qué hace:**
  - El Inicio trae las filas de YouTube Music del país de quien entra (Cloudflare lo sabe sin preguntar), más
    «Seguir escuchando», que se arma con el historial propio.
  - Tocar un álbum, un artista o una lista abre su página, con botón para reproducir todo.
- **Qué se modifica:**
  - `functions/api/inicio`, `album`, `artista` y `lista`, que son nuevas y usan `browse`.
  - Las pantallas correspondientes.
- **Qué podría fallar:**
  - El Inicio de YouTube cambia de forma seguido: si una fila no se entiende, se salta en vez de romper la pantalla.
  - Las páginas de artista tienen muchas variantes.
- **Cómo se prueba:** abrir el Inicio desde Colombia y en inglés, cinco artistas, cinco álbumes y tres listas, y
  que ninguna pantalla quede en blanco.

### 4.5 Biblioteca

- **Qué hace:**
  - **Me gusta**, **Mis listas** (crear, renombrar, borrar y agregar canciones), **Historial** y **última sesión**:
    al volver, la cola queda donde iba.
  - **Todo se guarda en el navegador** (IndexedDB). No hay cuentas, servidor ni telemetría.
- **Qué se modifica:** `src/datos/biblioteca.ts` y las pantallas de la biblioteca.
- **Qué podría fallar:**
  - Safari puede borrar los datos de una web que no se abre en semanas. Se pide almacenamiento persistente y se
    explica en Ajustes.
  - En la 1.1 llega exportar e importar.
- **Cómo se prueba:** dar Me gusta, crear dos listas, cerrar el navegador, volver y que todo siga ahí.

### 4.6 Base de la marca (obligatoria, no cuenta como función)

- **Bienvenida en la primera visita:** tres pasos cortos más el **aviso legal en español e inglés**. Life Music
  no está afiliado a YouTube ni a Google, y la música la reproduce YouTube.
- **Ajustes:**
  - Idioma (es/en).
  - Tema claro u oscuro, según lo que decida el diseño.
  - Mantener la pantalla encendida.
  - Borrar mis datos.
- **Acerca de:** la versión, los créditos GPL-3.0 de toda la cadena (InnerTune → Vivi Music / Metrolist →
  Echo Music → Life Music), el enlace al código, el wordmark de CG LABS y `© 2026 CG LABS`.
- **Instalable** (PWA): se agrega a la pantalla de inicio del iPhone y se instala como app en Chrome y Edge del
  computador.

## 5. Cómo se construye

| Pieza | Decisión | Por qué |
|---|---|---|
| Interfaz | **React 19 + TypeScript + Vite** | Es el stack web de CG LABS. Un reproductor tipo Spotify tiene mucho estado (cola, reproductor y biblioteca) y ya no cabe en JS suelto. |
| Estado | Zustand (un solo almacén pequeño) | Liviano y sin ceremonia. |
| Rutas | React Router | Enlaces que se pueden compartir y el botón «atrás» del navegador. |
| Datos de YouTube Music | Funciones de Cloudflare Pages en `webapp/functions/api/`, con WEB_REMIX | El navegador no puede preguntarle directo a YouTube Music. Solo viajan textos, nunca audio. |
| Caché | La caché de Cloudflare en cada función: Inicio 30 min, búsquedas 10 min, álbumes y artistas 6 h | Más rápido y lejos del tope gratis. |
| Biblioteca | IndexedDB en el navegador | Privada y sin servidor. |
| Letras | Geist y Geist Mono, alojadas en el sitio | Son de Pulso, y alojarlas evita llamar a terceros. |
| Hosting | Proyecto de Pages `lifemusic-web` en una **cuenta de Cloudflare aparte**, en el **plan gratis** | Aísla la web del resto de Life Music. En el plan gratis, si se pasa del tope (100 000 peticiones al día), las peticiones fallan pero **nunca se cobra**. |
| Carpeta | Se rehace `webapp/` | La versión del 02-10 queda en el historial (commit 376813a0). |

**Piezas compartidas:** la web no toca `innertube`, la comunidad ni la landing. Solo si se elige un subdominio de
lifemusic.app (sección 8) se coordina con General.

## 6. Riesgos, de mayor a menor

1. **Que YouTube frene a Cloudflare.**
   - **Problema:** no se sabe si YouTube Music responde bien a las peticiones que salen de los servidores de
     Cloudflare. En local no se puede probar, porque salen con la IP de la casa de CG. Si YouTube frena, no hay
     búsqueda ni Inicio.
   - **Qué hacer:** probarlo lo primero el sábado, con un despliegue de prueba (necesita el «publica» de CG).
2. **Que YouTube actúe contra el reproductor escondido.** Puede detectarlo, bloquear el dominio o mandar una queja.
   Se cubre con el resguardo 2 y el perfil bajo. Lo peor realista es tener que bajar la web, sin perder plata.
3. **Los anuncios.** No se sabe cada cuánto salen ni si la detección será confiable. Se mide en las pruebas.
4. **El iPhone.** Pausa al bloquear la pantalla (ya se sabe), y falta confirmar que deje arrancar y sonar el
   reproductor escondido.
5. **El tiempo.** Hasta el lunes hay que programar los cinco bloques, recibir la maqueta, aplicarla y probar el
   domingo. Está apretado.
6. **Canciones que no se dejan reproducir:** se saltan solas. Si en una lista son muchas, se avisa.

## 7. Calendario hasta el lunes

| Día | Qué |
|---|---|
| **Vie 23 (hoy)** | Plan corregido y la **mini versión 0.1** en local. |
| **Sáb 24** | Prueba de Cloudflare (con «publica»). Los cinco bloques funcionando. Pedido de la maqueta a Diseño y marca (escritorio y teléfono). |
| **Dom 25** | Se aplica el diseño aprobado. **Pruebas internas** con la lista fija: CG en el PC, un probador en iPhone. |
| **Lun 26** | Con el «publica» de CG: despliegue y entrega. |

### 7.1 La mini versión 0.1 (hoy)

- **Qué trae:**
  - El esqueleto en React con las cuatro zonas: columna izquierda, centro, «Ahora suena» y la barra de abajo.
  - Buscar canciones, tocar una y que suene **con el reproductor escondido**.
  - Play/pausa, anterior y siguiente, barra de tiempo y volumen.
  - Radio automática en la cola.
  - El resguardo de los anuncios y el interruptor de emergencia.
- **El estilo:** sobrio, con lo básico de Pulso (hueso, humo, carbón y Geist), sin adornos, para ver la
  estructura. **No es el diseño.**
- **Dónde corre:** solo en local, con `wrangler pages dev`. Se prueba en el navegador integrado, en escritorio y en
  teléfono emulado. No se publica nada.

## 8. Decisiones para CG (abiertas)

1. ~~La dirección del lunes~~ **Resuelto el 24-10:** va en la `.pages.dev` de la cuenta aparte, nunca en
   lifemusic.app.
2. **¿Los cinco bloques del lunes están bien?** Las letras sincronizadas, exportar e importar y compartir una
   canción quedarían para la 1.1.
3. **Si la maqueta no está aprobada el sábado en la noche:** ¿el lunes sale con el estilo sobrio de Pulso y el
   diseño llega en la 1.1, o se corre la entrega?

4. **El estilo de «Ahora suena».** Como la carátula vuelve a ser nuestra imagen, se pueden copiar todos los fondos
   del reproductor Android, incluido Apple Music, con la carátula a sangre y fundida. El mapa está en
   `PlayerBackgroundStyle` y `Player.kt`, y la demo en el scratchpad de la sesión (`escondido/estilos.html`).
   Falta que CG elija el principal.

## 9. Para la 1.1 y después (no entra en la 1.0)

- Letras sincronizadas: LRCLIB se puede pedir directo desde el navegador.
- Exportar e importar la biblioteca, y quizás importar desde la app Android.
- Compartir una canción con su enlace (`/cancion/:id`).
- Por evaluar:
  - Una mezcla suave entre canciones con dos reproductores escondidos que se cruzan el volumen.
  - Life Line narrando.
  - Enlazar con el TV.
