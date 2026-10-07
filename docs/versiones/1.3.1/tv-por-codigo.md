# Ficha técnica · Life Music TV por código o QR

**Versión:** 1.3.1 · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-07 · **Mesa de Ideas:** «Life Music TV en el navegador: enlace por código o QR»

## 1. Qué es

Hay televisores sin Google Cast ni DLNA que sí traen navegador. En ellos se abre
`lifemusic.pages.dev/tv` y la página muestra un código y un QR. El teléfono se enlaza
con ese código, igual que se empareja una cuenta de streaming sin escribir la
contraseña en el TV. Desde ahí la música suena en el TV con el modo ambiente y los
8 temas, y se maneja desde el teléfono como hoy se maneja el Cast.

## 2. Cómo lo vive la persona

1. Abre `lifemusic.pages.dev/tv` en el navegador del TV.
2. La página revisa el navegador durante unos 2 s («Preparando tu TV…») y elige
   cómo verse.
3. Sale un código de 6 caracteres grande (por ejemplo `K7M-Q2P`), el QR y el botón
   **Pantalla completa**.
4. Hay dos formas de enlazar:
   - Escanea el QR con la cámara del teléfono: se abre Life Music con el código
     puesto y pregunta «¿Enlazar con este TV?».
   - O en la hoja de Cast toca **TV con navegador** y escribe el código.
5. El TV pasa a «Teléfono enlazado». Si el navegador lo exige, pide **«Presiona OK
   para activar el sonido»** una sola vez.
6. Suena la canción en el TV, con su carátula, la letra y el tema que se elija.
7. **La próxima vez** el TV se acuerda: abre `/tv` y se conecta solo. En la hoja de
   Cast el teléfono muestra «TV de la sala (navegador)» para tocarlo directo, sin código.

## 3. Decisiones técnicas

### 3.1 Un intermediario en internet (relevo)

Una página `https` no puede hablarle al teléfono por el wifi de la casa: el navegador
bloquea `ws://192.168…` desde una página segura (contenido mixto) y no hay
certificado válido para una IP local. Así que los dos se conectan a un relevo en
internet que solo pasa mensajes entre ellos.

| Opción | A favor | En contra |
|---|---|---|
| **Cloudflare Worker + Durable Object** (recomendada) | En la cuenta de CG, donde ya vive la landing. Un «cuarto» por enlace con WebSocket. Plan gratis suficiente. No se pausa. | Es un servicio nuevo que hay que desplegar (`wrangler deploy`). |
| Supabase Realtime | Ya está en la app (el reto). | El proyecto gratis **se pausa** tras una semana sin uso, y el reto está apagado desde el 21-09. |
| Red local directa | Sin servidor. | Imposible desde una página `https`: el navegador lo bloquea. |

El relevo **no guarda nada**. Pasa los mensajes y olvida el cuarto cuando los dos se
desconectan. Lo único que se conserva es el token del enlace recordado, en el TV
(`localStorage`) y en el teléfono (DataStore).

### 3.2 El audio no pasa por el relevo

Igual que con el Cast de hoy (`CastConnectionHandler.loadMedia`), el teléfono resuelve
la URL de la canción y se la manda al TV. El TV la pide directo a YouTube con un
`<audio>`. Funciona porque en la misma casa el TV y el teléfono salen a internet con la
misma IP pública. Por el relevo solo viajan textos pequeños: la canción, la letra, el
estado y los comandos.

### 3.3 Un solo receptor, dos formas de hablar

`web/cast/receptor.js` ya separa el dibujo de la conexión: `iniciarCast()` para el
Chromecast e `iniciarDemo()` para el navegador. Se agrega `iniciarWeb()`:

- Reproduce con un `<audio>` propio en lugar del reproductor de CAF, y de ahí sale
  `posicion()`.
- Recibe por el relevo los mismos mensajes del canal propio (`cancion`, `letra`,
  `tempo`, `ajustes`, `saludo`, `vaciar`) y los de mando que en Cast resuelve CAF:
  `cargar`, `play`, `pausa`, `ir`, `volumen`.
- Le devuelve al teléfono el estado: posición, si suena, si carga, el fin de la
  canción y los errores.

Así los 8 temas funcionan en el TV con navegador desde el primer día y cualquier tema
nuevo llega a los dos sin trabajo extra. El SDK de Cast solo se carga cuando la página
corre en un Chromecast: `/tv` no lo descarga.

### 3.4 Evaluación del navegador (lo que pidió CG)

Antes de mostrar el código, `/tv` revisa el navegador y arma un perfil. Nada de esto
pide permisos ni tarda más de unos 2 s.

| Se revisa | Cómo | Si falta |
|---|---|---|
| Audio Opus/WebM | `canPlayType('audio/webm; codecs="opus"')` | Se pide AAC: el teléfono usa `urlAacParaDlna`, que ya existe |
| Audio AAC/MP4 | `canPlayType('audio/mp4; codecs="mp4a.40.2"')` | Sin Opus ni AAC: «Este navegador no puede reproducir la música» y no se muestra código |
| WebSocket | `'WebSocket' in window` | Modo básico: el relevo también contesta por consultas HTTP cada 1 s, con más retraso |
| WebGL | crear un contexto de prueba | Cristal líquido dibujado en 2D (ya existe) |
| Video H.264 | `canPlayType('video/mp4; codecs="avc1.42E01E"')` | Atardecer con una foto quieta del paisaje, sin video |
| Canvas de la canción (HLS) | `MediaSource` o HLS nativo | Sin canvas: se ve la carátula |
| Efectos CSS | `CSS.supports('backdrop-filter', …)`, variables CSS | Vidrios sólidos semitransparentes |
| Pantalla completa | `requestFullscreen` o `webkitRequestFullscreen` | Se esconde el botón |
| Pantalla encendida | Wake Lock o video mudo en bucle | Aviso: «Desactiva el protector de pantalla del TV» |
| Recordar el enlace | `localStorage` con prueba de escritura | Pide código cada vez |
| Potencia | `hardwareConcurrency`, `deviceMemory` y 1 s midiendo cuadros por segundo | Perfil **ligero**: fondos quietos, sin polvo ni partículas |

**Resultado:**
- Un perfil: **completo**, **medio** o **básico**.
- Una línea en el TV, por ejemplo «Compatibilidad: audio AAC · cristal 2D · sin video».
- El perfil se manda al teléfono en el mensaje `listo`. El teléfono lo usa para:
  - elegir el formato de audio,
  - marcar en la hoja de Cast los temas que ese TV no puede mostrar.

Para probarlo sin un TV viejo: `/tv?forzar=basico` simula un navegador sin WebGL, sin
video y sin WebSocket.

### 3.5 Enlace, QR y enlace recordado

**Código:** 6 caracteres de un alfabeto sin confusiones, sin 0/O ni 1/I/L, con 32⁶ ≈
mil millones de combinaciones.
- Vale 10 minutos y se renueva solo.
- El relevo limita los intentos fallidos por conexión.

**QR:** lleva `https://lifemusic.pages.dev/tv/enlazar?c=K7MQ2P`.
- Con un App Link verificado (`/.well-known/assetlinks.json` con la huella del
  certificado de firma), Android abre Life Music directo.
- Sin la app instalada, esa página explica qué es y enlaza a `/apk`.
- No hace falta un lector de QR dentro de la app: la cámara del teléfono lo lee.

**Recordar:** al enlazar, el relevo crea un token largo (128 bits) que guardan el TV y
el teléfono.
- La próxima vez el TV entra al cuarto de su token y el teléfono, al tocar ese TV en la
  lista, entra al mismo.
- «Olvidar este TV» en el teléfono, o «Enlazar otro teléfono» en el TV, borra el token.

**Activar el sonido:**
- Los navegadores no dejan sonar audio hasta que la persona toca algo.
- Si el `<audio>` falla con `NotAllowedError`, el TV muestra «Presiona OK para activar
  el sonido».
- Ese toque deja el audio activado para el resto de la sesión.

## 4. Mensajes

| De → a | Tipo | Contenido |
|---|---|---|
| TV → teléfono | `listo` | versión del receptor, perfil y capacidades |
| teléfono → TV | `cargar` | id, URL, tipo (`audio/webm` o `audio/mp4`), desdeMs, reproducir, título, artista, carátula, duraciónMs |
| teléfono → TV | `play` · `pausa` · `ir` · `volumen` | ms o 0–1 según el caso |
| teléfono → TV | `cancion` · `letra` · `tempo` · `ajustes` · `saludo` · `vaciar` | los mismos que el canal propio de Cast |
| TV → teléfono | `estado` | id, posMs, durMs, sonando, cargando (cada 1 s y en cada cambio) |
| TV → teléfono | `fin` | id: la canción terminó y el teléfono avanza la cola |
| TV → teléfono | `error` | id, código (por ejemplo 403 o formato), para reintentar o avisar |
| TV → teléfono | `diag` | texto para el diagnóstico de Cast, como hoy |

## 5. Qué se modifica

**Nuevo:**
- `enlace/` (Worker de Cloudflare): `wrangler.toml` y `src/index.js`, con el Durable
  Object `Cuarto`. Rutas: `/cuarto/nuevo`, `/cuarto/<codigo|token>` (WebSocket) y
  `/cuarto/<id>/mensajes` (modo básico por HTTP).
- `web/tv/index.html`: la entrada del TV con navegador. Usa el mismo
  `receptor.css` y `receptor.js`.
- `web/tv/enlazar/index.html`: el destino del QR cuando la app no está instalada.
- `web/.well-known/assetlinks.json`: para que el QR abra la app.
- `app/src/main/kotlin/.../cast/EnlaceWeb.kt`: el cliente WebSocket del teléfono, con
  OkHttp (ya está en el proyecto).

**Cambia:**
- `web/cast/receptor.js`:
  - `iniciarWeb()`, la evaluación del navegador y los perfiles;
  - la pantalla de código con QR, pantalla completa y «Presiona OK»;
  - el SDK de Cast se carga solo en un Chromecast.
- `web/cast/receptor.css`: la pantalla del código y la compatibilidad con perfiles.
- `CastConnectionHandler.kt` (foss): una tercera sesión, `sesionWeb`, junto a Cast y
  DLNA. Cargar, mando y seguimiento siguen el mismo patrón que DLNA.
- `CastButton.kt` (foss): la fila **TV con navegador**, el diálogo del código, los TV
  recordados y «Olvidar este TV».
- `AndroidManifest.xml`: el App Link de `lifemusic.pages.dev/tv/enlazar`.
- `MainActivity.kt`: abre la hoja de Cast con el código.
- `PreferenceKeys.kt`: los TV recordados (nombre y token).
- `life_strings.xml` (es y en).
- `PRIVACY_POLICY.md`: mencionar el relevo y que no guarda nada.

La variante gms no se publica, pero debe seguir compilando: `EnlaceWeb` vive en `main`
y la gms no lo ofrece.

## 6. Qué podría fallar

| Riesgo | Qué se ve | Cómo se cubre |
|---|---|---|
| TV y teléfono en redes distintas (datos móviles y wifi) | El TV recibe 403 de YouTube | El TV manda `error 403`. El teléfono avisa: «Conecta el teléfono al mismo wifi del TV» |
| IPv6 en el teléfono e IPv4 en el TV, o al revés | 403 aunque estén en la misma casa | Primero se reintenta con la URL nueva. Si se repite, se avisa, y la fase 4 dice si hace falta forzar IPv4 |
| La URL caduca tras una pausa larga (unas 6 h) | No vuelve a sonar al darle play | Al reanudar después de mucho tiempo, el teléfono manda `cargar` con URL nueva y la misma posición |
| Navegador muy viejo | Página en blanco | Perfil básico, código sin efectos y modo HTTP. Si no hay audio, mensaje claro, nunca en blanco |
| El navegador bloquea el audio | No suena | «Presiona OK para activar el sonido» |
| El TV se duerme o pone el protector | Se corta | Wake Lock, video mudo y aviso |
| Se cae el relevo o internet | El teléfono pierde el TV | Reenganche automático como el de Cast (`perdida()` y reintentos). Si no vuelve, la música sigue en el teléfono |
| Alguien adivina un código | Controla tu TV 10 min | 1 000 millones de combinaciones, 10 min de vida, intentos limitados y aviso en el TV de cada teléfono que se enlaza |
| Plan gratis de Cloudflare | El relevo deja de contestar | Uso pequeño (texto). Si se acerca al límite, se ve en el panel |

## 7. Cómo se prueba (fase 4)

- **Navegadores:** Chrome, Edge y Firefox de escritorio; el navegador de un Android TV
  (TV Bro o similar); el del TV de CG; y `/tv?forzar=basico`.
- **Enlace:**
  - con código;
  - con QR, con la app instalada y sin ella;
  - enlace recordado después de cerrar y reabrir;
  - «Olvidar este TV».
- **Mando:** play, pausa, adelantar, siguiente, anterior y volumen. La cola avanza
  sola al terminar una canción.
- **Temas:** los 8, incluido Tocadiscos V1/V2. Atardecer cambia según la hora.
- **Fallas a propósito:**
  - teléfono en datos móviles (debe avisar);
  - cortar el wifi del TV 10 s (debe reengancharse);
  - pausa de 1 h y reanudar.
- **Que nada se rompa:** Cast y DLNA siguen igual, y la lista fija de la versión pasa.

## 8. Fuera de esta versión

- Manejar la música con el control remoto del TV (OK = pausa, flechas = cambiar de canción).
- Varios teléfonos en un mismo TV.
- Videos musicales en el TV.

## 9. Orden de ejecución (fase 3)

| Tanda | Qué | Se puede probar |
|---|---|---|
| 1 | Relevo (`enlace/`) desplegado y probado con dos pestañas | Mensajes de ida y vuelta |
| 2 | `/tv`: evaluación del navegador, pantalla del código, QR y pantalla completa | En el navegador del PC y del TV |
| 3 | `iniciarWeb()`: audio propio, mensajes y estado | Una canción de prueba manejada desde una pestaña |
| 4 | App: `EnlaceWeb` y `sesionWeb` en `CastConnectionHandler` | Enlazar con código y que suene |
| 5 | App: hoja de Cast (fila, diálogo, TV recordados), App Link y QR | Flujo completo en el teléfono |
| 6 | Pulido: perfiles, avisos, privacidad y textos en/es | Lista de la fase 4 |

## 10. Lo que CG debe decidir

1. **Aprobar el relevo en Cloudflare:** un Worker nuevo, `lifemusic-enlace`, en su cuenta, desplegado con `wrangler`.
2. **La dirección:** `lifemusic.pages.dev/tv`. Cuando haya dominio propio, se cambia.
3. **Qué sesión lo programa:** como toca `receptor.js`, debe trabajarlo una sola sesión
   a la vez. Propuesta: esta (General), y la del TV no toca el receptor mientras tanto.
