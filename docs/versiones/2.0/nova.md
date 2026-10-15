# Ficha técnica · Modo DJ «Nova»

**Versión:** 2.0 · **Capa:** 1 (funciones nuevas) · **Fase:** 3 (ejecución) · **Estado:** APROBADA por CG el 2026-10-15
**Idea de:** CG, 2026-10-15 · **Diseño aprobado:** `Brand/maquetas/2026-10-15-nova-dj.png` y `.html` (medidas en el CSS)

## 1. Qué es

Nova es un DJ personal con voz que vive en el teléfono. Hace tres cosas:

- **Arma la música por bloques:** Lo de siempre, Volviendo al pasado, Algo nuevo y Para este momento.
- **Habla entre canciones** cada 3 o 4 (se ajusta de 2 a 8). Mientras habla, la música baja al 30 %, como en la radio.
- **Pone lo que dice en subtítulos** en Life Line.

**Lo que Nova sabe de la persona se queda en el teléfono.** Nada sale de él, salvo lo que la persona active a
propósito: la descarga de la voz o su propia IA.

**Regla de oro (la misma de la voz de Life Line, `LifeLineVoice.kt`):** Nova **solo dice cosas ciertas**. Cada frase
sale de un dato real: del historial, de la hora, de la cola o de una fuente pública. Nunca se inventa un recuerdo ni
un dato.

## 2. Cómo lo vive la persona

1. En el Inicio toca el botón de Nova. Se prende la luz **AL AIRE**, la estrella toma el color de lo que va a sonar y
   Nova dice su primera frase: «Buenas tardes, CG. Soy Nova, tu DJ…».
2. Suena el primer bloque. Arriba, en el reproductor, se ven el bloque y el dial con su estación.
3. Cada 3 o 4 canciones, en el cambio de canción, la música baja en 0,4 s, Nova habla unos 5 a 10 s y la música
   sube en 0,6 s. La frase sale en Life Line con la estrella.
4. **Cambiar de onda** mueve la aguja al bloque siguiente: Nova lo anuncia y la cola cambia, pero la canción que
   suena no se corta.
5. **La primera vez**, si se elige la voz HD, se descarga una sola vez.
6. Para apagarla se toca el botón otra vez: la luz se apaga y la música sigue normal.

## 3. Lo que la app ya tiene y se reutiliza

| Pieza que existe | Dónde | Para qué le sirve a Nova |
|---|---|---|
| Rampa bajar 0,4 s / subir 0,6 s (coseno/seno, pasos de 15 ms) | `playback/.../audio/FundidoAlElegir.kt` y su motor en `MusicService` | Bajar la música al 30 % y subirla de nuevo. Son los mismos tiempos que pide la maqueta. |
| La cola por páginas (`Queue`: `getInitialStatus`, `nextPage`) y la autocarga cuando quedan 5 canciones o menos | `playback/.../queues/Queue.kt` y `MusicService` (`AutoLoadMoreKey`) | Cada bloque es una «página» de la cola. Nova entra como una cola más, sin tocar el reproductor. |
| El historial: tabla `event` (canción, fecha y hora, tiempo escuchado) | `core/.../db/entities/Event.kt` | Horarios, repeticiones y épocas. |
| Consultas `topSongs`, `relatedSongs`, `forgottenFavorites`, `quickPicks`, `escuchadasHacePoco` | `core/.../db/DatabaseDao.kt` | Base de Lo de siempre, Volviendo al pasado y Algo nuevo. |
| La voz de Life Line, con prioridades (`LifeLineVoz`, `esInterrupcion`) | `ui/component/LifeLineVoice.kt` y `LifeLine.kt` | Un estado nuevo, `NOVA`, para los subtítulos. |
| La IA propia de la persona (OpenRouter: llave, modelo y URL en Ajustes) | `ai/AiRecommendationHelper.kt` y `PreferenceKeys` | «Habla más rico con tu IA», opcional. |
| Colores de la carátula (Palette) | `ui/component/Aurora.kt` (`rememberColoresAurora`) | El borde de la estrella y la onda. |
| Google Sans Flex variable (ancho 25–151 verificado), con `minSdk` 26 | `res/font/google_sans_flex.ttf` | El nombre «NOVA» a ancho 151 sin empaquetar fuentes. |
| El plan del Automix (cuándo dispara la transición y cuánto se encima) | `MusicService.computeAutomixPlan` | Saber cuándo empieza la canción nueva para hablar encima. |

**Nova no depende de la «Automix fase B».** Funciona con el Automix de hoy, con el fundido normal o sin
transición. Antes se había anotado esa dependencia, pero no hace falta.

## 4. Decisiones técnicas

### 4.1 El motor de bloques: `NovaQueue`

- Es una clase nueva que implementa `Queue`, en el módulo `app`, porque necesita la base de datos y innertube.
- `getInitialStatus()` devuelve el primer bloque y `nextPage()` el siguiente. Cada bloque trae entre 3 y 5 canciones,
  según «cuánto habla».
- El bloque de cada canción va en `MediaItem.mediaMetadata.extras` (`nova_bloque`), y así la pantalla sabe en qué
  estación está.
- **Los cuatro bloques:**
  - **Lo de siempre:** lo más escuchado en los últimos 30 días, sin repetir lo que ya sonó en la sesión.
  - **Volviendo al pasado:** lo que se escuchó mucho hace 6 meses o más y casi nada en los últimos 60 días. La época
    sale del primer `event` de esa canción o de ese artista.
  - **Algo nuevo:** las relacionadas (`relatedSongs`, o la radio de YouTube de una canción de Lo de siempre),
    quitando todo lo que ya tenga algún `event`.
  - **Para este momento:** lo que se suele escuchar a esta hora (±1 h) y este tipo de día (entre semana o fin de
    semana).
- **Orden:** rota Lo de siempre → Algo nuevo → Para este momento → Volviendo al pasado. Si un bloque no tiene datos
  suficientes, se salta y Nova no lo nombra.
- **Cambiar de onda:** reemplaza lo que viene en la cola con el bloque siguiente, igual que `startRadioSeamlessly()`:
  la canción actual sigue sonando.
- **Sin historial** (instalación nueva): Nova lo dice con honestidad («Todavía no te conozco, empecemos con algo
  nuevo») y usa Algo nuevo o los ánimos y géneros.

### 4.2 Lo que Nova sabe: el perfil local

- La tabla `event` solo guarda lo que sonó 30 s o más (`HistoryDuration`). **Hoy los saltos no se guardan.**
- Hace falta una tabla nueva, pequeña, `senal_nova` (canción, fecha, tipo: `SALTO` / `COMPLETA` / `REPETIDA`).
  Requiere una migración de Room.
- **Propuesta:** guardar las señales desde que se instala la versión, aunque Nova esté apagada, para que cuando la
  persona la prenda ya la conozca un poco. Es solo local y se borra con «Borrar historial».
- Las canciones que se saltan mucho bajan de peso en los bloques. Si se salta a Nova mientras habla, la próxima vez
  habla un poco menos.

### 4.3 Qué dice: frases con datos ciertos

1. **Plantillas en el teléfono, gratis y sin internet.** Cada plantilla tiene huecos que solo se llenan con datos
   reales. Ejemplos:
   - Saludo por hora y día, con el nombre de «Cómo te dice».
   - Anuncio del bloque («Volvamos a 2019, cuando empezaste con …»).
   - Repeticiones («Llevas 14 veces esta semana con …»).
   - La canción que viene.
2. **Datos públicos, opcionales y en caché:** Wikipedia y MusicBrainz (año del álbum, de dónde es el artista). Se
   consultan solo con Nova prendida y con red. MusicBrainz pide como máximo una consulta por segundo y un User-Agent
   propio.
3. **Con la IA propia** («Habla más rico»): se le mandan **los datos ya verificados** y ella solo los redacta, con un
   máximo de 25 palabras y sin agregar hechos. Si falla o tarda más de 3 s, se usa la plantilla.
4. **Largo:** de una a dos frases, unos 5 a 10 s. Nunca más de 12 s.
5. **Idioma:** español. Las plantillas van en `strings` para poder traducirlas después.

### 4.4 Cuándo habla

- En el cambio de canción, cada N canciones (N de 2 a 8, por defecto 3), y siempre al cambiar de bloque.
- **La frase y su audio se preparan durante la canción anterior**, unos 30 s antes del final. Así no hay espera.
- **¿Encima de la intro o encima de la salida?** Si la canción nueva tiene letra sincronizada y la primera línea
  empieza antes de que Nova termine, Nova habla sobre los últimos segundos de la anterior. Si no, habla sobre la
  intro de la nueva. Es la regla de radio: no hablar encima de la voz del cantante.
- Con el Automix, Nova espera a que termine la mezcla (la transición manda).

### 4.5 La voz: dos niveles

| Nivel | Cómo | Tamaño | Cuándo |
|---|---|---|---|
| **Voz del teléfono** | `android.speech.tts.TextToSpeech` → `synthesizeToFile` → se reproduce por la app | 0 MB | Desde la primera tanda. Funciona en cualquier teléfono con voz en español. |
| **Voz HD de Nova** | Piper con sherpa-onnx (Apache-2.0), dentro de la app | La librería suma unos 10 MB al APK (por medir). Cada voz se descarga una vez (unos 60–75 MB). | En la tanda de la voz. Es la de la maqueta («Dale voz a Nova»). |

- **Las dos generan el audio a un archivo y la app lo reproduce ella misma.** Así se puede bajar la música
  exactamente mientras habla, y la onda y el latido de la estrella salen de **la voz de verdad** (energía del audio
  por tramos de 50 ms), no de una animación inventada.
- **Las cuatro voces de la maqueta** (femenina y masculina, de Latinoamérica y de España): hay voces Piper en
  español de México, Argentina y España. **Hay que revisar la licencia de cada una antes de ofrecerla.**
- **La descarga** va desde Hugging Face o GitHub directamente, sin servidor nuestro: cero costo de Cloudflare.
  Solo la hace la persona al tocar «Dale voz a Nova», y por Wi-Fi a menos que diga otra cosa.
- **No se descarga código:** las librerías nativas van dentro del APK. Solo se descarga el modelo de voz.

### 4.6 Bajar la música

- Se reutiliza el motor del fundido: un factor `duckNova` (1 → 0,3 en 0,4 s, 0,3 → 1 en 0,6 s) que se multiplica en
  el mismo lugar donde `MusicService` calcula el volumen (usuario, silencio, foco de audio). Así no pelea con esas
  partes.
- Se usa `player.volume`, que actúa al instante; un procesador de DSP actuaría con el retraso del búfer.
- La voz suena por la app con `USAGE_MEDIA` y sin pedir foco de audio, para salir por el mismo lugar que la música:
  parlante, audífonos, Bluetooth o el carro.

### 4.7 Subtítulos en Life Line

- Un estado nuevo, `LifeLineVoz.NOVA`, con `esInterrupcion = true`: mientras Nova habla, su frase pisa la letra.
- Va con la estrella y la onda, y la frase entra palabra por palabra al ritmo del audio.
- Al terminar, Life Line vuelve sola a la letra.

### 4.8 Cuándo Nova no habla

- En pausa o con el silencio de la app activado.
- **Transmitiendo al TV** (Cast, DLNA o TV con navegador): la voz sonaría en el teléfono y no en el TV, así que se
  muestran solo los subtítulos.
- En «Escuchar juntos», salvo que sea el anfitrión. Por ahora no habla.
- **Si la persona salta la canción o toca algo mientras Nova habla,** Nova se calla al instante y la música vuelve a
  su volumen.
- Si la frase o el audio no estuvieron listos a tiempo: esa vez no habla, y no espera.

## 5. Adaptación a Material You, con identidad

**El diseño aprobado se respeta.** Lo que cambia es de dónde salen los colores y las formas, para que Nova se sienta
parte de Material You sin volverse genérica.

| Elemento | Cómo queda |
|---|---|
| **Superficies** (paneles, Personalizar, la hoja de descarga) | Roles de Material 3 (`surfaceContainer`, `surfaceContainerHigh`). Así respetan claro, oscuro y negro puro sin código aparte. |
| **El núcleo de la estrella** | Blanco cálido fijo: es Nova. |
| **El borde de la estrella y la onda** | Color de la carátula (`Aurora.kt`). Sin música, el `primary` del tema: con el color dinámico del sistema, el del fondo de pantalla. |
| **La luz AL AIRE** | **Naranja #FF5B2E fijo**, no cambia con el tema. Es la señal de la cabina de CG LABS, como un foco de estudio de verdad. |
| **El botón de encender** | Formas de Material 3 Expressive (`MaterialShapes`, ya disponibles en material3 1.5.0-alpha18). Apagado es un círculo; al aire se transforma en una forma de destello que gira despacio. Es Material You con firma propia. |
| **La aguja del dial** | `primary`, con el movimiento con resorte de M3 Expressive (`MotionScheme.expressive()`), para que «se sintonice» con rebote. |
| **El nombre «NOVA»** | Google Sans Flex, ancho 151, peso 300–380, espaciado. Lo demás va con la escala tipográfica de M3. |
| **Personalizar** | Filas de Ajustes de la app (`Material3SettingsItem`) y tarjetas M3 seleccionables para las voces. Se siente parte de Ajustes. |

**Ojo:** la v2 «Receptor hi-fi» (sin aprobar) cambia el módulo de Nova en el Inicio y en el reproductor (ojo de
cristal, pulsador y dial en una ventana del receptor). **El motor no depende de eso.** Lo visual del Inicio y del
mini reproductor espera a que CG decida la v2.

## 6. Cambios que propongo a la maqueta

1. **«Solo subtítulos (sin voz)»**, un interruptor en Personalizar: para el trabajo, el bus o cuando hay alguien
   durmiendo. También se usa solo al transmitir al TV.
2. **«Voz del teléfono» como opción gratis e inmediata,** junto a la voz HD. La pantalla «Dale voz a Nova» pasa a
   ser «Mejora su voz», opcional.
3. **Tocar el subtítulo repite lo último que dijo Nova.**
4. **La perilla «Cuánto habla»:** de 2 a 8, y además «Solo al cambiar de bloque».

## 7. Tandas (cada una instalable y probable en el teléfono)

Todo va detrás del interruptor **«Nova (vista previa)»** en Ajustes, apagado de fábrica, hasta que CG lo apruebe en
el teléfono.

| # | Tanda | Qué se prueba |
|---|---|---|
| 0 | **Prueba de voz:** interruptor más una frase fija con la voz del teléfono, bajando la música al 30 % en el cambio de canción | Que la bajada y la voz suenen bien en el S25, con audífonos y en el carro |
| 1 | **El motor de bloques** (`NovaQueue`) y Cambiar de onda, sin pantalla nueva todavía | Que los bloques tengan sentido con el historial real de CG |
| 2 | **Señales locales** (`senal_nova` y saltos) y **frases con datos ciertos** (plantillas) | Que lo que dice sea cierto y no se repita |
| 3 | **Subtítulos** en Life Line (`NOVA`) y la **onda real** | Que se sepa quién habla |
| 4 | **Reproductor con Nova:** bloque, dial, AL AIRE y estrella | La maqueta 3, adaptada a M3 |
| 5 | **Personalizar:** nombre, Cómo te dice, voz, Cuánto habla y Solo subtítulos | La maqueta 6 |
| 6 | **Voz HD:** sherpa-onnx, la descarga y las 4 voces | La maqueta 5, el peso del APK y las licencias |
| 7 | **La entrada en el Inicio** y el mini reproductor | Espera la decisión de CG sobre la v2 |
| 8 | **Más rico:** Wikipedia y MusicBrainz, y la IA propia | Opcional |

## 8. Riesgos

- **El APK crece unos 10 MB con la voz HD.** Por eso va en la tanda 6 y la voz del teléfono cubre lo anterior.
- **Licencias de las voces Piper:** hay que revisarlas una por una antes de ofrecerlas.
- **Teléfonos sin voz en español instalada:** se detecta con `isLanguageAvailable` y se le explica a la persona cómo
  instalarla.
- **Choque con el foco de audio y el fundido:** se evita con el factor central `duckNova` (ver 4.6).
- **Historial corto:** los bloques se saltan con honestidad (ver 4.1).

## 9. Decisiones de CG (2026-10-15)

CG aprobó la ficha con «sigue lo que recomendaciones» y «no sigas el v2 de nova». Queda así:

- **Voz del teléfono primero;** la voz HD va en la tanda 6.
- **Los saltos se guardan en el teléfono desde ya,** aunque Nova esté apagada. Es solo local.
- **Van los cambios de la sección 6:** Solo subtítulos, la voz del teléfono como opción, repetir lo último y «Solo al cambiar de bloque».
- **Nova NO sigue la v2 «Receptor hi-fi».** Su aspecto es el de la maqueta aprobada (`2026-10-15-nova-dj`), adaptada a Material You como dice la sección 5. Esto vale también para la entrada en el Inicio y el mini reproductor (tanda 7).

### Las preguntas que se hicieron

1. ¿Empezamos con la **voz del teléfono** y dejamos la **voz HD** para la tanda 6? (Recomendado.)
2. ¿Se guardan los **saltos** en el teléfono **desde ya**, aunque Nova esté apagada? (Recomendado: solo local.)
3. ¿Van los cambios de la sección 6 (Solo subtítulos, voz del teléfono, repetir y «Solo al cambiar de bloque»)?
