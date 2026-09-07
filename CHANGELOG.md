# Registro de cambios

Todos los cambios relevantes de Life Music, con el formato de
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

Life Music parte de [Echo Music](https://github.com/EchoMusicApp/Echo-Music) en su
versión 1.2.3. Este registro recoge **únicamente lo que Life Music cambia sobre
esa base**; para la historia anterior, consulte el repositorio de origen.

---

## [1.1.0] — 2026-09-07

Primera tanda de funciones propias. Todas nacen de la misma idea: que el
reproductor cuente lo que está pasando en vez de limitarse a sonar.

### Añadido

- **Life Line.** Una sola línea de la letra bajo el artista, resaltada palabra
  por palabra, conviviendo con la carátula. El barrido se calcula en espacio de
  caracteres —no de palabras— para que el degradado caiga donde está el texto.
  Cuando la línea no cabe, se desplaza manteniendo la palabra que suena al 40 %
  del ancho visible.
- **La voz de Life Line.** Cuando no hay letra, habla la app: fundidos cruzados,
  encadenado automático, buffering, pérdida de red, últimos segundos de la
  canción. Ningún estado se inventa; todos salen de señales que el reproductor ya
  publicaba. Treinta frases en español e inglés, rotando cada 12 segundos con la
  semilla ligada al identificador de la canción.
- **Modo karaoke** a pantalla completa, con línea siguiente en previsualización,
  cuenta atrás en los pasajes instrumentales y la palabra actual en color de
  acento. Acceso desde el botón de micrófono del reproductor.
- **Reducción rápida de voz** por cancelación de centro en banda. Conserva el
  bajo por debajo de 200 Hz y el aire por encima de 7,5 kHz, que es lo que
  distingue esto de un `L−R` crudo. Verificado con tonos de prueba: la voz al
  centro cae 25 dB mientras el bajo y los lados quedan intactos.
- **Relevo de marca** en el encabezado de Inicio: el título se releva con el
  wordmark de CG LABS y vuelve, con fundido encadenado y curva seno. Configurable
  en Ajustes → Apariencia → Tema.
- **Selector de color personalizado** para el resaltado de Life Line, con rueda
  HSV, entrada hexadecimal y muestras.

### Corregido

- **La letra se descargaba en el hilo principal.** `MusicService` usa un scope
  con `Dispatchers.Main`, y las peticiones a los cinco proveedores de letra
  corrían ahí: la interfaz se congelaba en cada cambio de pista y se quedaba
  pintada la letra anterior. Ahora va en `withContext(Dispatchers.IO)`.
- **El colector que traía la letra al cambiar de canción estaba muerto.** Colgaba
  de `ShowLyricsKey`, una preferencia que no escribe nadie en todo el proyecto.
  Venía roto desde antes del fork.
- **Nada garantizaba que la letra fuera de la canción en curso.** `currentLyrics`
  es un `flatMapLatest` sobre Room y conservaba el valor anterior mientras
  llegaba la nueva consulta. Ahora se compara el identificador antes de pintar.
- `InlineLyricsView` lanzaba su descarga con un `launch` que escapaba del efecto
  y sobrevivía al cambio de canción, encadenando peticiones por cada pista.
- El acceso al karaoke solo aparecía en una de las dos filas de botones del
  reproductor, dejando sin la función a quien tuviera el diseño nuevo desactivado.

### Cambiado

- La reducción de voz pasa a llamarse **«Reducción rápida de voz»** y explica en
  su propia pantalla que es resta y no separación, con un techo que ningún ajuste
  supera. Antes se presentaba como algo que no era.

### Interno

- **Porte del DSP de MDX-Net a Kotlin**, con FFT de radix mixto —6144 no es
  potencia de dos— verificado contra la implementación de referencia en Python
  mediante pruebas de JVM. El módulo `playback` estrena banco de pruebas.
- **Medición de separación de stems en dispositivo**: 1,05× tiempo real en un
  Galaxy S25 Ultra con ONNX Runtime y XNNPACK. La dependencia va en
  `debugImplementation` y no viaja en compilaciones de publicación.

---

## [1.0.0] — 2026-08-30

Identidad propia. La base funcional es la de Echo Music 1.2.3; lo que cambia aquí
es de quién es la aplicación.

### Añadido

- Identidad visual **Life Music**: 52 iconos regenerados a partir de tres
  originales, adaptativos con zona segura respetada, y color de tema esmeralda
  (`#10B981`). Material You sigue activado por defecto, así que el verde vive en
  el icono y el logotipo, no en la interfaz.
- Pantalla **Acerca de** con la cadena completa de créditos —InnerTune, Metrolist
  / Vivi Music, Echo Music— y la licencia GPL-3.0.
- **Introducción a pantalla completa** de cuatro pasos en el primer arranque, con
  el wordmark de CG LABS y los créditos al proyecto de origen.
- `constants/Repo.kt`, que centraliza la identidad del repositorio en un único
  fichero.

### Cambiado

- Identificador de aplicación y paquete a **`com.cglabs.lifemusic`**. El
  renombrado alcanzó 3 784 referencias en 510 archivos, e incluyó esquemas de
  Room, paquetes de Protobuf y fuentes generadas por Hilt.
- Nombres de función a la marca propia: Echo Brain, Find, Chart, Tuning,
  Signature y Extractor pasan a **Life** \*.

### Eliminado

- **`google-services.json`**, que apuntaba al proyecto de Firebase de Echo. Sin
  él, Gradle no aplica Google Services ni Crashlytics, en coherencia con la
  variante FOSS. El fork no envía analítica ni informes de fallo a
  infraestructura ajena.

### Seguridad

- **El auto-actualizador venía activado por defecto** y consultaba las versiones
  del repositorio de Echo. En cuanto Echo publicara una versión superior, Life
  Music habría ofrecido instalar el APK de otro proyecto. Desactivado por defecto
  y redirigido; con él, otras cuatro dependencias de infraestructura ajena.

---

## Nota sobre los nombres conservados

Los módulos `echomusiccanvas`, `applecanvas`, `kugou`, `lrclib`, `paxsenixlyrics`,
`simpmusic` y `unison` **mantienen su nombre a propósito**: identifican al
servicio del que obtienen datos, no a esta aplicación. Renombrarlos sería
atribuirse un servicio ajeno.

Los avisos de copyright de terceros presentes en el código se conservan intactos,
tal como exige la GPL-3.0 en sus secciones 4 y 5.

[1.1.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.0
[1.0.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.0.0
