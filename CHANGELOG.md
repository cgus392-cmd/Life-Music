# Registro de cambios

Todos los cambios relevantes de Life Music, con el formato de
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

Life Music parte de [Echo Music](https://github.com/EchoMusicApp/Echo-Music) en su
versión 1.2.3. Este registro recoge **únicamente lo que Life Music cambia sobre
esa base**; para la historia anterior, consulte el repositorio de origen.

> **Sobre la numeración.** Life Music empieza a contar desde cero, en 1.0.0 con
> código de versión 1. Heredaba el 152 de Echo, y conservarlo habría roto las
> actualizaciones desde el primer día: Android no permite instalar un código
> menor que el ya presente, así que publicar un 152 como primera versión dejaría
> sin camino a todo lo que viniera después.

---

## [1.1.4] — 2026-09-07

### Corregido

- **Pulsar «Instalar» en la pantalla de actualización cerraba la aplicación.**
  La variante FOSS borraba del manifiesto el permiso `REQUEST_INSTALL_PACKAGES`
  —el proyecto de origen lo hacía porque F-Droid prohíbe que una aplicación se
  actualice a sí misma—, pero el actualizador seguía ahí y seguía llamando a
  `canRequestPackageInstalls()`, que lanza `SecurityException` cuando el permiso
  no está declarado.

  El resultado era el peor posible: la edición que se publica es justamente la
  FOSS, así que la función estrella del proyecto se estrellaba en el último paso,
  después de descargar los 66 MB.

  Life Music no está en F-Droid: se distribuye por GitHub y su actualizador es
  parte del proyecto. El permiso se conserva ahora también en la edición FOSS, y
  el comentario del sabor, que seguía prometiendo compatibilidad con F-Droid, dice
  ya lo que hay. Aquí «FOSS» significa «sin servicios de Google», nada más.

- Instalar ya nunca cierra la aplicación aunque el sistema se niegue: si algo
  falla se avisa y el APK descargado sigue donde estaba, listo para reintentar.

- **El logotipo de CG LABS podía quedar invisible.** Venía por recursos —negro en
  `drawable/`, blanco en `drawable-night/`— y Android elige esa variante según el
  tema del **sistema**. Con la aplicación en oscuro y el sistema en claro salía
  negro sobre negro. Ahora se tiñe con el color de contenido del tema real.

### Cambiado

- **Rediseño de la introducción de bienvenida.** Deja de ser una sucesión de
  pantallas quietas:

  - **Las barras del sistema ya no salen grises.** Un diálogo abre su propia
    ventana y esa ventana no heredaba el borde a borde de la aplicación. Ahora el
    color llega hasta el borde, y el tono de los iconos se decide por la
    luminancia del fondo real de la aplicación, no por el tema del sistema.
  - **Fondo con dos manchas de luz que derivan despacio**, tomadas del color del
    tema: con color dinámico activado, la bienvenida ya es del color del teléfono
    de cada uno.
  - **Insignia animada** en el saludo: dos estrellas blandas girando en sentidos
    opuestos con el icono encima. El giro contrario es lo que da profundidad.
  - **Entrada escalonada**: cada bloque llega un poco después del anterior.
  - Nada de esto se mueve si el sistema tiene las animaciones desactivadas.

- **Paso nuevo de preajustes.** Tema, color dinámico, fondo del reproductor y
  nuevo diseño del reproductor, elegibles sin salir de la introducción. Son los
  ajustes que se tocan el primer día, y mandar a buscarlos a un menú que todavía
  no se conoce es pedirle a alguien que se pierda antes de empezar. Escriben las
  mismas preferencias que Ajustes → Apariencia, así que no hay dos verdades.

- El chip de edición ya no dice «FOSS» a mano: lo lee del sabor compilado, así que
  una compilación GMS deja de mentir sobre lo que es.

### Conocido

- Los textos de la introducción siguen escritos en español dentro del código, sin
  pasar por recursos. Se hereda así y no se ha tocado en esta versión: traducirlo
  son unas cuarenta cadenas por idioma y merece su propio cambio.

---

## [1.1.3] — 2026-09-07

Lo encontró un probador con un moto g56 5G, no nosotros. La aplicación se cerraba
al abrir la biblioteca, y el informe de fallo llegó ofuscado; la causa apareció al
recomponer la traza contra el `mapping.txt` de esa misma compilación.

### Corregido

- **La biblioteca cerraba la aplicación si había una lista de reproducción vacía
  creada en el propio teléfono.** El código comprobaba
  `remoteSongCount!! != null`: el `!!` se evalúa **antes** que la comparación, así
  que reventaba justo en el caso que la comprobación pretendía cubrir. Y ese caso
  es de lo más corriente —una lista recién creada, todavía sin canciones—, porque
  el contador remoto solo existe en las listas que vienen de YouTube.

  Estaba en las dos presentaciones, lista y rejilla, así que caía igual con
  cualquiera de las dos vistas de la biblioteca.

- **Abrir una lista creada en el teléfono podía cerrar la aplicación por lo
  mismo.** Cuatro comprobaciones de `browseId` con el mismo error, en la pantalla
  de lista local: cambiar la portada, quitarla y el aviso que la acompaña.
  `browseId` es nulo precisamente en las listas locales, que es donde esas
  opciones tienen sentido.

- Una condición que era siempre cierta —`(remoteSongCount ?: 0) != null`, y un
  entero nunca es nulo— dejaba muerta su propia rama alternativa.

### Cambiado

- **Los restos de la marca ajena que quedaban fuera de la interfaz.** El informe
  de fallo se titulaba «echomusic Crash Report» y se guardaba como
  `echomusic_crash_*.txt` —así llegó el que originó esta versión—, y las imágenes
  compartidas se archivaban en `Imágenes/echomusic`, una carpeta con nombre de
  otro proyecto en la galería del usuario.

### Nota sobre el método

Los seis fallos son el mismo: un `!!` puesto en bloque sobre valores que sí podían
ser nulos, que no arregla la nulabilidad sino que adelanta el error. Vienen de
antes de la bifurcación. La búsqueda que los destapó —`!!` seguido de una
comparación con `null`— ya no encuentra nada en el árbol.

---

## [1.1.2] — 2026-09-07

### Cambiado

- **El fondo del reproductor viene ahora en estilo Apple Music por defecto**, en
  lugar de Degradado. A peticion de CG. Sigue siendo un ajuste: Apariencia ->
  Reproductor -> Fondo del reproductor ofrece las siete opciones de siempre.

### Interno

- El valor por defecto de ese ajuste estaba escrito a mano en **cuatro sitios**:
  el reproductor, la caratula, la pantalla de letras y la propia pantalla de
  Ajustes. Bastaba cambiar tres para que Ajustes mostrara una cosa y el
  reproductor pintara otra. Ahora se declara una sola vez, junto a la clave, y
  los cuatro lo leen de ahi.

  Es la misma leccion de la 1.1.1 —arreglar dos de cuatro sitios y dar el trabajo
  por hecho— aplicada antes de que muerda.

---

## [1.1.1] — 2026-09-07

La limpieza de la 1.1.0 se quedo corta. CG lo encontro usando la app: la pantalla
de actualizacion seguia abriendo la web del proyecto de origen.

### Corregido

- **Ajustes -> Actualizaciones -> «System update» abria echomusic.fun en el
  navegador.** Ni era nuestra web ni era una pantalla de actualizacion. Ahora
  lleva a la pantalla de actualizacion de la propia aplicacion, que consulta las
  publicaciones de este repositorio, muestra las notas y descarga el APK.
- **Los enlaces de compartir seguian apuntando al dominio del proyecto de
  origen.** La correccion de la 1.1.0 solo alcanzo los menus del modulo `app`,
  pero la propiedad `shareLink` que usa toda la aplicacion vive en el modulo
  `innertube`, y ahi seguia intacta. Tambien en `core`, para las listas
  guardadas. Cinco enlaces mas.
- El boton de Discord traia por defecto la web del proyecto de origen.
- La ultima URL del servidor de Escuchar juntos que quedaba escrita a mano.

### Nota sobre el metodo

El fallo de la 1.1.0 fue buscar solo en los modulos `app` y `core`. Este proyecto
tiene dieciocho modulos, y la marca ajena estaba repartida en cuatro de ellos.
Buscar en el arbol entero cuesta lo mismo y no deja huecos.

---

## [1.1.0] — 2026-09-07

Limpieza de la infraestructura heredada. Life Music seguia mandando trafico
—y usuarios— al proyecto de origen en sitios que no se habian encontrado antes.

### Corregido

- **Compartir una cancion abria el dominio del proyecto de origen.** Todos los
  enlaces se construian contra `share.echomusic.fun`, en catorce ficheros: canciones,
  listas, albumes y artistas. Ahora apuntan a YouTube Music directamente, que es
  donde esta el contenido. Life Music no tiene servidores y no va a fingir que
  los tiene.
- **Los enlaces legales de Ajustes abrian la politica de privacidad y los
  terminos de Echo Music.** La aplicacion mostraba documentos legales ajenos como
  si fueran suyos. Ahora abren los propios, que estan en el repositorio.
- **El dialogo de actualizacion mandaba a la web del proyecto de origen** en vez
  de a nuestras publicaciones. Igual la notificacion de actualizacion disponible.
- **La comprobacion de version no comparaba si la publicada era MAYOR**, solo si
  era distinta: ofrecia instalar hacia atras, que Android rechaza despues de
  haber descargado el APK entero. `isNewerVersion` ya existia y no la llamaba
  nadie.
- **La comprobacion no tenia limites de tiempo** —una red a medias la dejaba
  colgada— ni enviaba `User-Agent`, que GitHub exige y sin el puede responder 403.
- **El canal de versiones beta no funcionaba**: la consulta iba siempre a
  `/releases/latest`, que excluye las prepublicaciones por definicion.

### Cambiado

- El servidor de Escuchar juntos, que pertenece al proyecto de origen, pasa a
  estar declarado en `constants/Repo.kt` y **documentado en la politica de
  privacidad**. Seguia siendo una dependencia real y no constaba en ninguna parte.
- El keystore de publicacion se busca fuera del repositorio, por variable de
  entorno o por `local.properties`.

### Nota

El manejador de enlaces entrantes sigue aceptando `share.echomusic.fun`: ahi la
aplicacion no manda, recibe. Quitarlo romperia los enlaces que alguien ya haya
compartido.

---

## [1.0.0] — 2026-09-07

Primera versión pública. Recoge dos fases de trabajo: dar identidad propia a la
aplicación y añadirle las primeras funciones que no existen en el proyecto de
origen.

### Identidad propia

- Identidad visual **Life Music**: 52 iconos regenerados a partir de tres
  originales, adaptativos y con la zona segura respetada, y color de tema
  esmeralda (`#10B981`). Material You sigue activado por defecto, así que el
  verde vive en el icono y el logotipo, no en la interfaz.
- Identificador de aplicación y paquete propios, **`com.cglabs.lifemusic`**. El
  renombrado alcanzó 3 784 referencias en 510 archivos, e incluyó esquemas de
  Room, paquetes de Protobuf y fuentes generadas por Hilt.
- Pantalla **Acerca de** con la cadena completa de créditos —InnerTune,
  Metrolist / Vivi Music, Echo Music— y la licencia GPL-3.0.
- **Introducción a pantalla completa** de cuatro pasos en el primer arranque.
- Nombres de función a la marca propia: Echo Brain, Find, Chart, Tuning,
  Signature y Extractor pasan a **Life** \*.
- `constants/Repo.kt`, que centraliza la identidad del repositorio en un único
  fichero.

### Funciones propias

- **Life Line.** Una sola línea de la letra bajo el artista, resaltada palabra
  por palabra, conviviendo con la carátula. El barrido se calcula en espacio de
  caracteres —no de palabras— para que el degradado caiga donde está el texto.
  Cuando la línea no cabe, se desplaza manteniendo la palabra que suena al 40 %
  del ancho visible.
- **La voz de Life Line.** Cuando no hay letra, habla la aplicación: fundidos
  cruzados, encadenado automático, buffering, pérdida de red, últimos segundos de
  la canción. Ningún estado se inventa; todos salen de señales que el reproductor
  ya publicaba y que no miraba nadie. Treinta frases en español e inglés, que
  rotan cada 12 segundos con la semilla ligada al identificador de la canción.
- **Modo karaoke** a pantalla completa, con línea siguiente en previsualización,
  cuenta atrás en los pasajes instrumentales y la palabra actual en color de
  acento. Se abre desde el botón de micrófono del reproductor.
- **Reducción rápida de voz** por cancelación de centro en banda. Conserva el
  bajo por debajo de 200 Hz y el aire por encima de 7,5 kHz, que es lo que
  distingue esto de un `L−R` crudo. Verificado con tonos de prueba: la voz al
  centro cae 25 dB mientras el bajo y los lados quedan intactos.
- **Relevo de marca** en el encabezado de Inicio: el título se releva con el
  wordmark de CG LABS y vuelve, con fundido encadenado y curva seno.
- **Selector de color personalizado** para el resaltado de Life Line, con rueda
  HSV, entrada hexadecimal y muestras.

### Corregido sobre la base heredada

- **La letra se descargaba en el hilo principal.** `MusicService` usa un ámbito
  con `Dispatchers.Main`, y las peticiones a los cinco proveedores de letra
  corrían ahí: la interfaz se congelaba en cada cambio de pista y se quedaba
  pintada la letra anterior. Ahora va en `withContext(Dispatchers.IO)`.
- **El colector que traía la letra al cambiar de canción estaba muerto.** Colgaba
  de `ShowLyricsKey`, una preferencia que no escribe nadie en todo el proyecto.
  Venía roto desde antes de la bifurcación.
- **Nada garantizaba que la letra fuera de la canción en curso.**
  `currentLyrics` es un `flatMapLatest` sobre Room y conservaba el valor anterior
  mientras llegaba la nueva consulta. Ahora se compara el identificador antes de
  pintar.
- `InlineLyricsView` lanzaba su descarga con un `launch` que escapaba del efecto
  y sobrevivía al cambio de canción, encadenando peticiones por cada pista.

### Eliminado

- **`google-services.json`**, que apuntaba al proyecto de Firebase de Echo. Sin
  él, Gradle no aplica Google Services ni Crashlytics, en coherencia con la
  variante FOSS. Life Music no envía analítica ni informes de fallo a
  infraestructura ajena.

### Seguridad

- **El auto-actualizador venía activado por defecto** y consultaba las versiones
  del repositorio de Echo. En cuanto Echo publicara una versión superior, Life
  Music habría ofrecido instalar el APK de otro proyecto. Desactivado por defecto
  y redirigido, junto con otras cuatro dependencias de infraestructura ajena.

### Interno

- **Porte del DSP de MDX-Net a Kotlin**, con FFT de radix mixto —6144 no es
  potencia de dos— verificado contra una implementación de referencia en Python
  mediante pruebas de JVM. El módulo `playback` estrena banco de pruebas.
- **Medición de separación de voz e instrumental en dispositivo**: 1,05× tiempo
  real en un Galaxy S25 Ultra con ONNX Runtime y XNNPACK. La dependencia va en
  `debugImplementation` y no viaja en compilaciones de publicación.

---

## Nota sobre los nombres conservados

Los módulos `echomusiccanvas`, `applecanvas`, `kugou`, `lrclib`, `paxsenixlyrics`,
`simpmusic` y `unison` **mantienen su nombre a propósito**: identifican al
servicio del que obtienen datos, no a esta aplicación. Renombrarlos sería
atribuirse un servicio ajeno.

Los avisos de copyright de terceros presentes en el código se conservan intactos,
tal como exige la GPL-3.0 en sus secciones 4 y 5.

[1.1.4]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.4
[1.1.3]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.3
[1.1.2]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.2
[1.1.1]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.1
[1.1.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.0
[1.0.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.0.0
