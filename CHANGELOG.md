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

## [1.1.9] — 2026-09-18

### Cambiado

- **Modo ambiente, primera pasada propia.** La pantalla venía de Echo tal
  cual; ahora tiene tres cosas nuestras, cada una con su interruptor en
  Ajustes → Apariencia → Modo ambiente:
  - **Zona de volumen.** Antes, cualquier arrastre vertical cambiaba el
    volumen, y bajar la barra de notificaciones o un roce lo bajaba sin
    querer. Ahora el volumen solo se toca dentro de una franja al borde
    derecho (o izquierdo, o ninguna), con su propio indicador discreto en vez
    del panel del sistema. El barrido horizontal para cambiar de canción
    sigue en toda la pantalla.
  - **Canvas en la carátula.** Si la canción tiene canvas, el vídeo se
    reproduce dentro del recuadro de la portada; si no, la carátula de
    siempre. Respeta el interruptor general de canvas del reproductor.
  - **Controles al tocar.** Un toque en la carátula asoma anterior, pausa y
    siguiente en una banda que sube por el borde inferior de la portada, sin
    moverla, como si la cortara. Tres estilos (corte sólido, suave, limpio) y
    los segundos que tardan en esconderse (de 2 a 15; cinco de serie). El
    doble toque para pausar sigue funcionando.
- «Ambient Mode» en el menú del reproductor ahora se llama «Modo ambiente».
- **Cierre del reto de la semana.** La app ya sabe que el reto terminó, no
  solo que «quedan 0 días»:
  - A la medianoche del último día (hora de Bogotá) el contador en vivo se
    para en seco: lo que suene después no se suma ni se muestra sumando.
  - La pantalla pasa a **Resultado final**: la tarjeta lleva esa etiqueta y las
    fechas del reto, la cifra es la confirmada por el servidor, el ranking se
    titula «Clasificación final» y el botón dice «Compartir resultado». Quien
    no participó ve una nota de cierre en vez de un hueco.
  - La chapita del puesto en el mini-reproductor y el interruptor del
    recordatorio desaparecen al terminar (el recordatorio ya no se programaba).
  - El servidor ya rechazaba minutos fuera de fecha; esto es lo que faltaba en
    el teléfono. El módulo sigue ocultándose solo el día 22.

## [1.1.8] — 2026-09-17

Dos cosas pequeñas para la semana del reto, que es esta. Todo lo demás sigue
igual que en la 1.1.7.

### Añadido

- **Tu puesto en el mini-reproductor.** Junto a los controles, una chapita con
  el trofeo y «#11». Se mueve sola con cada envío al ranking —cada pocos
  minutos mientras suena algo y al acabar cada canción— y da un brinco cuando
  subes de puesto. Solo existe mientras dura el reto y si estás inscrito; el
  día 22 desaparece sin dejar rastro.

- **Recordatorio diario a las 8 de la noche.** Una notificación para quien
  está inscrito: cuánto llevas hoy, cuánto te queda hasta el tope de 6 horas
  y tu puesto. Al tocarla se abre el reto en tu fila. Calla si ya llegaste al
  tope, y el último es el del 19. Se apaga con un interruptor en la pantalla
  del reto. Local del todo: lo calcula el teléfono con lo que ya contaba; no
  sale nada nuevo de él.

---

## [1.1.7] — 2026-09-15

Dos cosas que la gente pedía por WhatsApp: poder traerse sus listas de Spotify
teniendo la cuenta con Google, y no tener que ir a GitHub para actualizar.

### Añadido

- **Importar listas públicas de Spotify por enlace, sin iniciar sesión.** Google
  no permite «Continuar con Google» dentro de navegadores incrustados en apps
  —lo reconoce por una cabecera que Android añade a todas sus peticiones— y
  Life Music no lo esquiva. Así que ahora hay otro camino: pegas el enlace de
  cualquier lista pública y la app la lee como lo haría un visitante del
  reproductor web, sin cuenta, y busca cada canción en YouTube Music. Para tus
  «Me gusta» y tus listas privadas sigue haciendo falta entrar; para las
  cuentas de Google hay un botón que lleva a crear una contraseña en Spotify,
  en el navegador del sistema, donde Google sí lo permite.

- **Aviso de versión nueva aunque la app esté cerrada.** Cada 12 horas, solo
  con red, la app mira si hay una versión publicada más nueva y lo dice con
  una notificación, una sola vez por versión. Se apaga en Ajustes →
  Actualizaciones, con el mismo interruptor que la comprobación automática.

- **El actualizador entero en español.** Pantalla, ajustes y notificaciones de
  descarga estaban solo en inglés desde el origen.

### Cambiado

- **Un solo camino para actualizar, dentro de la app.** La ventana de
  «Actualización disponible» del arranque y la notificación abrían la web de
  publicaciones de GitHub en el navegador: un segundo sistema, distinto del
  actualizador de Ajustes, que mucha gente no sabía usar. Ahora las dos
  llevan a esa misma pantalla, que descarga el APK e instala. La ventana sale
  solo cuando existe una versión más nueva que la instalada.

- **La comprobación automática viene activada.** Estaba apagada de serie desde
  que Life Music aún no publicaba versiones; ya publica. Quien la apagó a
  propósito conserva su elección. Y la pantalla del actualizador comprueba
  siempre al entrar: antes, con la automática apagada, decía «tienes la
  última versión» sin haber mirado.

### Corregido

- **Reto de la semana: los minutos extra otorgados a mano se perdían.** Un
  ajuste hecho directamente en el servidor duraba hasta el siguiente envío de
  la app, que recalculaba el total. Ahora hay un bonus aparte que el servidor
  suma siempre.

---

## [1.1.6] — 2026-09-12

Una versión para una semana concreta: el **Reto de la semana**, del 13 al 19 de
septiembre. Todo lo demás sigue igual que en la 1.1.5.

### Añadido

- **Reto de la semana.** Quien más minutos escuche en Life Music entre el 13 y
  el 19 de septiembre gana unos AirPods Pro. Los minutos son los que la app ya
  contaba para Estadísticas —reproducción real, sin pausas, canciones de más de
  30 s—, así que cuentan desde el día 13 aunque uno se inscriba después. Tope de
  6 horas al día, para que gane la constancia y no el teléfono sonando de noche.

  Participar es **opcional y explícito**: un boletín al actualizar lo presenta,
  y solo quien pulsa Participar y se inscribe (apodo público; nombre y correo
  privados, para contactar al ganador) envía algo. Sin inscripción, la app no
  hace ni una petición al servidor del reto. Se puede abandonar cuando se
  quiera y se borra todo. Es la única excepción, temporal, a «no hay servidores
  de CG LABS», y está documentada en la política de privacidad.

  El servidor —una base de datos en Supabase con funciones que validan cada
  envío— decide qué minutos son plausibles: nunca más de los transcurridos del
  día, nunca más del tope, y para los días desde la inscripción el aumento
  entre envíos no puede superar el tiempo real. Una app modificada que diga
  «9000 minutos» no cuela.

  En la app: un trofeo en Inicio con dos anillos —lo que llevas hoy hasta el
  tope, y otro que gira mientras suena algo—, pantalla del reto con la imagen
  de la campaña, tus minutos **en vivo** (la canción que suena cuenta segundo a
  segundo y se suma al acabar), tu puesto, ranking con apodos, reglas, tirar
  para actualizar, y una tarjeta compartible vertical para Instagram y WhatsApp
  con la gráfica de tus siete días. El premio se entrega solo en el
  departamento del Atlántico, y así lo dicen las reglas y la inscripción.

---

## [1.1.5] — 2026-09-11

Primera versión después del vídeo de presentación, con 53 descargas nuevas mirando.
Interfaz primero, español por defecto, y un arreglo que tocaba desde la 1.1.4.

### Corregido

- **«Apple Music Inspired» venía apagado en toda instalación nueva.** En Ajustes ese
  interruptor hace dos cosas —cambia el diseño del reproductor y fija el fondo
  Apple Music— y su valor por defecto estaba escrito a mano en **cuatro sitios**,
  todos en el sentido contrario al que CG pidió. Quien lo tenía encendido era
  porque lo había activado él. Ahora se declara una vez, junto a la clave, y viene
  encendido de serie. Quien ya eligió no nota nada: solo cambia el defecto para
  quien no ha elegido.

- **El interruptor de la bienvenida hacía otra cosa con otro nombre.** Se llamaba
  «Nuevo diseño del reproductor», escribía la preferencia al revés y no tocaba el
  fondo. Ahora es el mismo «Apple Music Inspired» que en Apariencia, con el mismo
  icono y las mismas dos acciones. Sin dos verdades. Error de la 1.1.4; lo
  encontró CG usando la app.

- **El texto del reproductor era ilegible sobre carátulas claras.** Los fondos que
  salen de la portada —Apple Music, degradado, difuminado— no llevan velo, y el
  texto era blanco fijo: con una portada blanca daba gris sobre gris, y lo pendiente
  de Life Line, al 42 %, desaparecía. Ahora el color del texto se decide por la
  luminancia real de los colores extraídos de la carátula. Afecta a todo el texto
  del reproductor, no solo a Life Line, porque el problema era de todos.

### Añadido

- **Saludo de entrada.** Al arrancar en frío, antes de Inicio, la aplicación te
  recibe con una frase: sabe la hora —«Buenos días», «Buenas noches»— y cuánto hace
  que no abres, y nada más, sin nombre ni cuenta. Se va sola en 1,6 segundos o
  antes si tocas; nunca bloquea la reproducción, que arranca detrás. Es la voz de
  Life Line saliendo del reproductor: la app habla también en la puerta.

  Veintiséis frases en español con su par en inglés, distinta en cada arranque y
  sin repetir ninguna de las tres últimas. Con su interruptor en Apariencia, y no
  se muestra si el sistema tiene las animaciones desactivadas ni cuando va a salir
  la bienvenida de primer arranque.

- **Boletín de versión.** La primera vez que arranca una versión tras
  actualizar, un boletín a pantalla completa cuenta lo nuevo —en esta, «Presentamos
  Automix»: modos, estilos, dónde está— y se puede releer en Ajustes →
  Actualizaciones → Versión. Va dentro de la app, en español e inglés, sin
  depender de la red. La bienvenida ya solo sale en instalaciones nuevas: antes
  salía también en cada actualización, como si el usuario no la hubiera visto.

### Automix: de fundido de radio a transición de DJ

- **Un filtro de verdad en cada plato.** Automix ejecutaba toda transición como
  un fundido de volúmenes con un shelf de graves de −10 dB. Ahora cada plato
  lleva un filtro de estado variable de **24 dB por octava**, paso bajo y paso
  alto, con el corte deslizándose geométricamente para que nunca se oiga un
  salto. Es el efecto que hace que una mezcla suene a DJ, y no lo teníamos.
  Portado de BitChord, con su copyright intacto, bajo la misma GPL-3.0.

- **Dos estilos de transición, elegidos por el material.** Con tempos a menos del
  4 % de distancia, **blend**: mezcla al beat y los graves cambian de mano una sola
  vez, en el 70 % del solape. Con tempos más lejanos, **barrido**: el paso bajo se
  cierra sobre la saliente hasta una cama de 300 Hz mientras la entrante entra
  pasada por alto y se va abriendo. Nadie elige el efecto: lo decide la distancia
  de tempo entre las dos pistas.

- **Tres niveles en vez de dos, y ya no es todo o nada.** Antes, si la pista
  entrante no tenía análisis o su confianza era baja, se tiraba el plan entero y
  caía a un fundido plano —ese era «a veces suena brusco»—. Ahora hay un nivel
  intermedio, **asistido**: no estira el tempo ni alinea el beat, pero ancla la
  salida a frase de la saliente y conduce el barrido de filtro. Sigue sonando a
  DJ aunque falte la mitad de la información.

- **Modo de mezcla: «Canción completa» de serie, «Modo DJ» a elección.** El
  motor saltaba la intro de la pista entrante —7, 14 segundos— porque solo mide
  energía, y una intro de acordeón o de metales es más floja que el coro. Eso es
  criterio de cabina, no de quien escucha una salsa entera. Ahora el análisis
  guarda también dónde **empieza** a oírse la música (antes solo dónde acaba), y
  en «Canción completa» la entrante arranca ahí: se salta el silencio digital de
  la subida y nada más, y la saliente se lleva su mambo entero. «Modo DJ» es lo
  de antes: entra al cuerpo y sale en el outro. Y por si alguien quiere mandar,
  **Estilo de transición**: automático, blend, barrido de filtro, plano o
  **«Cierre y arranque»**: la saliente se cierra con el filtro hasta callarse
  (8 beats) mientras la siguiente arranca por su principio y sube limpia debajo,
  entera justo cuando la otra calla. Cuándo empieza a subir se elige con un
  deslizador (de serie, a la mitad del cierre). Mientras se cierra, la
  notificación y la barra siguen contando la verdad, porque el relevo ocurre al
  final y no al empezar. Todo en el bloque de Automix (Beta) de Ajustes, que
  además ya está en español.

- **Life Line cuenta el estilo, no solo que hay transición.** «Fundiendo las
  notas…» solo en blend; «Cerrando el filtro…» en barrido; «Cambiando de
  pista…» en fundido plano. Antes decía «Mezclando…» en cualquier caso.

- **Cada transición queda registrada** —estilo, o motivo del fundido plano— en el
  teléfono, y la pantalla de depuración de Automix resume las últimas cincuenta.
  Es lo que permite seguir mejorándolo con datos.

- **Medido, no supuesto.** El filtro se verificó con tonos en pruebas de JVM: 0 dB
  en la banda de paso, −48 dB a dos octavas del corte. Las curvas de conducción
  tienen sus propias pruebas en los puntos que importan.

### Interno

- Se retira `AutomixDuckAudioProcessor`, el shelf de graves del proyecto de
  origen: el paso alto del filtro nuevo hace su trabajo, mejor.
- Base de datos 44 → 46: `beat_info` gana `contentEndMs` y `contentStartMs`. Las
  filas antiguas se reanalizan una vez, solas, la primera vez que suena la pista.

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

[1.1.5]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.5
[1.1.4]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.4
[1.1.3]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.3
[1.1.2]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.2
[1.1.1]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.1
[1.1.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.1.0
[1.0.0]: https://github.com/cgus392-cmd/Life-Music/releases/tag/v1.0.0
