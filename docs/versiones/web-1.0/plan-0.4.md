# Plan de ajustes · Life Music Web 0.4

26-10-2026. La 0.3.0 está en producción (lifemusic-web.pages.dev). Con la primera prueba en un iPhone real llegaron
quejas de diseño, y veníamos arreglando cosas sueltas sin orden. Este plan junta **todo** lo pendiente, ordenado de
lo más importante a lo menos, y fija cómo se trabaja de aquí en adelante.

## 1. Cómo se trabaja (el protocolo)

1. **Un bloque a la vez**, en el orden de este plan. No se abre el siguiente hasta cerrar el que va.
2. Cada punto pasa por cuatro pasos:
   1. **Propuesta corta:** qué cambia y cómo lo hace Apple Music. CG la aprueba o la corrige.
   2. **Se programa** en tandas de 10 a 20 minutos.
   3. **Se revisa:** captura a tamaño iPhone y prueba en el navegador.
   4. **CG lo ve y lo cierra.** Recién ahí se pasa al siguiente punto.
3. **Las ideas nuevas que salgan en medio** se anotan en la sección 8 («Después») y no se meten en el bloque que va.
4. **La referencia de diseño es Apple Music de iOS 26:** limpio, intuitivo y con buenas proporciones, con las piezas
   de la marca (Geist, Pulso). Cuando Android y Apple no coincidan, **manda Apple** (pedido de CG del 26-10).
5. **Publicar solo con el «publica» de CG.**

## 2. Dónde estamos

- **En producción:** la 0.3.0 (Automix, bienvenida con instalar y beta, mini con la línea recta, vidrio).
- **En local, sin publicar:**
  - «Ahora suena» rehecho con las piezas del reproductor Apple Music de Android;
  - la carátula que se recoge en tarjeta al pausar;
  - el desvanecido bajo las pestañas y el agarre con sombra;
  - el arreglo del círculo que se quedaba pegado al tocar en el iPhone;
  - el modo de demostración para las capturas.
- **Sin commit:** todo lo posterior a f3b2ad0b.

## 3. Bloque 0 · Que suene bien en el iPhone (primero que todo)

Si el motor no aguanta en el iPhone, el diseño no sirve de nada. Este bloque no lleva código: son preguntas al
probador.

| # | Pregunta | Si falla |
|---|---|---|
| 0.1 | ¿La primera canción suena al primer toque, o hay que darle play otra vez? | Pasa a ser el bloque 1. |
| 0.2 | ¿Sigue sonando con la pantalla bloqueada o al abrir otra app? | Se investiga; puede ser un límite de iOS. |
| 0.3 | ¿Salen la carátula y los controles en la pantalla de bloqueo? | Se revisa la Media Session. |
| 0.4 | ¿Le salió algún anuncio? ¿Cómo se vio la hoja? | Se ajusta la hoja del anuncio. |

**Quién:** CG le pregunta al probador. Mientras llegan las respuestas, se puede avanzar el bloque 1.

## 4. Bloque 1 · «Ahora suena» limpio, como Apple Music (la queja n.º 1)

| # | Qué | Cómo queda |
|---|---|---|
| 1.1 | **La fila de abajo** (hoy son botones de 56 px con fondo, desproporcionados). | Como Apple: **íconos chicos (unos 24 px), sin fondo, blancos al 70 %**, en las puntas: Compartir a la izquierda y Cola a la derecha. Aleatorio y repetir pasan a la cabecera de «A continuación», que es donde los tiene Apple (ya están ahí). Cuando lleguen las letras (bloque 4), van a la izquierda y Compartir pasa al centro. |
| 1.2 | **La lista «A continuación» sale seca, sin animación.** | Sube como hoja con la curva de iOS (unos 450 ms) y la carátula grande se recoge en la miniatura de arriba. Al cerrar hace lo mismo al revés, con el botón o bajándola con el dedo. Las filas entran con un fundido corto. |
| 1.3 | **Las proporciones de toda la pantalla.** Android es más pesado que Apple. | Se miden contra Apple Music: los botones ··· y ♥ de 40 a unos 30 px; la barra del tiempo de 10 a 7 px, que crece a 12 al tocarla; el título y el artista al tamaño de Apple; los controles se revisan al ojo con una captura de Apple al lado. |
| 1.4 | **Tocar el título o el artista.** | El título abre el álbum y el artista abre su página, como en Android. |
| 1.5 | **El cambio de canción.** | El fondo se funde en 1,2 s (Android) en vez de saltar. |
| 1.6 | **El menú ··· aparece y se va de golpe** (lo vio CG el 26-10; se había quedado fuera del plan). Es el mismo menú en «Ahora suena», en las filas y en las tarjetas. | Como las hojas de iOS: sube desde abajo con la curva de iOS (unos 400 ms) mientras el fondo se oscurece con un fundido, y al cerrar baja. También se cierra bajándola con el dedo. Como todas las hojas comparten estilo, se arreglan todas de una vez. **Propuesto: justo después del 1.3.** |

**Tandas:** unas cinco, una por punto. 1.1 y 1.2 van primero porque son las quejas.

**Avance:**
- **1.1 cerrado (26-10):** Compartir y Cola en las puntas, íconos de 22 px en blanco al 70 %, con toque de 44 px.
- **1.2 cerrado (26-10):**
  - la hoja sube en 480 ms con la curva de iOS;
  - la carátula a sangre se recoge en la miniatura de 68 px de arriba;
  - la información se aparta y las primeras filas entran escalonadas;
  - se cierra tocando la canción de arriba o bajando la cabecera de la hoja más de 100 px.
- **1.3 cerrado (26-10)** con la tabla de abajo, aprobada tal cual. Además, los botones ··· y ♥ se ven de 30 px
  pero se tocan como de 44.
- **1.4 hecho en local (26-10):**
  - tocar el título abre el álbum y tocar el artista abre su página;
  - «Ahora suena» baja y la página se abre detrás con su transición;
  - se ven como texto y se aclaran al tocarlos; sin el dato quedan como texto.

  Las canciones de «Selecciones rápidas» llegan sin álbum, así que al empezar se completa con la radio de esa
  canción, que sí lo trae. El navegador la guarda dos minutos, así que no gasta peticiones de más. Falta que CG lo
  cierre.
- **1.5 hecho en local (26-10), ampliado con lo que vio CG:** «la carátula cambia de golpe, no suave como Apple
  Music». Ahora nada salta al cambiar de canción:
  - **Carátulas** (`CaratulaSuave`): la nueva se funde sobre la anterior en el fondo de «Ahora suena», el mini, la
    cápsula y el panel del computador y la cabecera de «A continuación». La vieja se quita solo cuando la nueva
    terminó de aparecer.
  - **Tiempos:** la carátula nítida y el título cambian juntos en unos 450 ms; el fondo difuminado tarda 1,2 s
    (Android) y el color de la canción 0,7 s.
  - **Precarga:** la carátula de la siguiente canción se descarga de antemano.
  - **Tiempo restante:** no muestra «−0:00» mientras no se sabe la duración.
  - **Medido cuadro a cuadro** en una pestaña al frente: carátula y título de 0 a 1 en 480 ms, juntos, sin
    parpadeo.

  Falta que CG lo cierre.
- **1.6 cerrado (26-10).** Es una sola pieza (`Hoja.tsx`) para el menú ··· (en «Ahora suena», filas y
  tarjetas) y para la guía de instalar del iPhone:
  - sube en 420 ms con la curva de iOS mientras el fondo se oscurece, y al cerrarse baja en 280 ms;
  - se cierra tocando fuera, con la X o bajándola con el dedo más de 90 px; si se suelta antes, vuelve a su sitio;
  - un toque sigue siendo un toque: el arrastre empieza pasados 8 px;
  - cada vez que se abre arranca limpio, y el aviso («Agregada a la cola») no cambia de tamaño mientras baja.

**Propuesta del 1.3: medidas de hoy (sacadas de Android) contra las de Apple Music.** Las de Apple están medidas
al ojo; si el probador manda una captura de Apple Music sonando, se afinan con ella.

| Pieza | Hoy (Android) | Propuesta (Apple) |
|---|---|---|
| Título | 22 px, negrita | 20 px, seminegrita |
| Artista | 16 px, blanco al 78 % | 18 px, blanco al 60 % |
| Botones ··· y ♥ | círculos de 40 px, fondo al 20 %, ícono de 22 | círculos de 30 px, fondo al 15 %, ícono de 16 |
| Barra del tiempo | 10 px, crece a 16 | 7 px, crece a 12 |
| Tiempos | a la derecha la duración total («3:35») | a la derecha lo que falta («−2:14»), en blanco al 55 % |
| Anterior y siguiente | 48 px | 42 px |
| Play y pausa | 72 px | 80 px: más protagonista, como en Apple |
| Volumen | barra de 10 px, altavoces de 20 | barra de 7 px, altavoces de 16 al 60 % |
| Espacios | apretados (8 a 20 px entre filas) | más aire: unos 24 px entre título, barra, controles, volumen y la fila de abajo |

Lo demás no cambia: márgenes de 32 px, carátula a sangre, fila de abajo y colores.

## 5. Bloque 2 · Que toda la app se sienta de iPhone

| # | Qué | Cómo queda |
|---|---|---|
| 2.1 | **Debajo del reloj**, al hacer scroll, el contenido pasa pelado bajo la hora y la batería. | Un desvanecido con desenfoque, como en las apps de iOS. |
| 2.2 | **No se puede volver deslizando desde el borde.** | En álbum, artista y lista, deslizar desde el borde izquierdo vuelve atrás, con la transición que ya existe. |
| 2.3 | **No hay menús al mantener presionado.** | Mantener presionada una canción o tarjeta abre su menú (como Haptic Touch). |
| 2.4 | **Proporciones de Inicio, Buscar y Biblioteca.** | Misma revisión del 1.3, con capturas de Apple al lado. |

**Descartado:** la vibración al tocar. Safari del iPhone no deja que una página vibre.

**Avance:**
- **El bloque 1 quedó cerrado el 26-10.**
- **2.1 hecho en local (26-10):**
  - Debajo del reloj, lo que pasa se desvanece y se desenfoca (82 px en un iPhone: el reloj y 28 px más).
  - Solo aparece con la página desplazada, para no tapar la foto del artista.
  - Se aparta cuando sale la barra de vidrio del álbum o del artista.
  - Sin zona segura (Safari con sus barras, o el computador) no ocupa nada.

  Se miró en el Inicio, con carátulas pasando por debajo. En álbum y artista se comprobó la lógica, pero no la
  imagen, porque el panel del navegador estaba escondido. **Cerrado por CG (26-10).**
- **2.2 hecho en local (26-10)** (`GestoAtras.tsx`):
  - el gesto empieza en los primeros 24 px del borde izquierdo, y la página sigue al dedo con una sombra en su
    orilla;
  - al soltar vuelve si se pasó del 35 % del ancho o si fue un empujón rápido; si no, regresa a su sitio;
  - al volver, la transición sigue de corrido desde donde quedó la página;
  - si el dedo se detuvo antes de soltar, no cuenta como empujón.

  **Solo en la app instalada en el iPhone:** en Safari normal y en Android ya está el gesto del navegador o del
  sistema, y los dos juntos retrocederían dos veces. Tampoco arranca si no hay a dónde volver dentro de la app
  (enlace directo) ni con «Ahora suena», un menú o la bienvenida encima.

  Probado simulando el dedo como iPhone instalado: el gesto corto regresa, el largo vuelve y el empujón vuelve.
  **Cerrado por CG (26-10)**; falta verlo en el iPhone real.
- **2.3 hecho en local (26-10)** (`useMantener` en `MenuCancion.tsx`):
  - mantener presionada una canción (fila o tarjeta del carrusel) 450 ms abre su menú; mientras tanto se hunde
    poco a poco, como Haptic Touch;
  - en el computador, el clic derecho hace lo mismo;
  - desplazar más de 8 px no cuenta;
  - el toque que abre el menú no lo cierra ni reproduce la canción, y un toque rápido reproduce como siempre;
  - no se selecciona texto ni sale el menú de Safari;
  - la manija de la cola y el ··· siguen con lo suyo;
  - las tarjetas de álbum, artista y lista no tienen menú, así que no cambian.

  Probado simulando el dedo: mantener (fila y tarjeta), clic derecho, toque rápido y desplazar. **Cerrado por CG
  (26-10).**
- **2.4 hecho en local (26-10)** con la tabla de abajo, aprobada tal cual. Se miró con capturas de antes y
  después de las tres pantallas.
  - **Falla que venía de antes:** los títulos de las canciones en las filas salían grises y chicos. La regla del
    renglón de abajo (`.fila-cuerpo span`) también agarraba el título, que va en un span dentro del `<b>`. Ahora es
    `.fila-cuerpo > span`, y lo mismo en la cola.
  - Falta que CG lo cierre; con eso se cierra el bloque 2.

| Pantalla | Pieza | Hoy | Propuesta (Apple) |
|---|---|---|---|
| Inicio | Títulos de sección («Seguir escuchando») | 21 px, seminegrita | 22 px, negrita |
| Inicio | Tarjetas del carrusel | 150 px, esquinas de 12, separadas 14 | 160 px, esquinas de 8, separadas 12 |
| Inicio | Título de la tarjeta | 15 px, seminegrita | 14 px, peso normal |
| Todas | Filas de canciones: carátula | 50 px, esquinas de 8 | 48 px, esquinas de 6 |
| Todas | Filas de canciones: título | 16 px, seminegrita | 16 px, peso normal (Apple no usa negrita en las filas) |
| Todas | Filas de canciones: debajo del título | 14 px | 13 px |
| Todas | Filas de canciones: alto | 60 px | 56 px |
| Buscar | Campo de búsqueda | 46 px de alto, esquinas de 14 | 44 px, en forma de píldora (iOS 26) |
| Buscar | «Explorar por momento» | texto de 17, seminegrita | texto de 17, peso normal |
| Biblioteca | Renglones (Me gusta, Mis listas…) | texto de 17, peso medio | texto de 20, peso normal, como la Biblioteca de Apple |

No cambian: el título grande (34 px, igual que Apple), los colores, la letra Geist ni los márgenes de 20 px.

**Ideas que salieron al mirar** (van a la sección 8, no entran en el 2.4):
- En Buscar, cuadros de colores por género, como «Explorar categorías» de Apple.
- En Biblioteca, «Escuchado hace poco» en cuadrícula de dos columnas, como «Agregado recientemente».

## 6. Bloque 3 · Cerrar y entregar la 0.4

**El bloque 2 quedó cerrado el 26-10.**

| # | Qué |
|---|---|
| 3.1 | Commit local de todo (sin subir a GitHub). **Hecho el 26-10: 69e5fe6d en `main`**, solo `webapp/` y `docs/versiones/web-1.0/`. |
| 3.2 | Pruebas un día antes, con lista fija: CG en el PC y el probador en el iPhone. |
| 3.3 | Publicar, con el «publica» de CG. **Hecho el 27-10, con su orden: la 0.4.0 en lifemusic-web.pages.dev** (despliegue 1d084228), con los bloques 1 y 2 y las letras (4.1 y 4.1b). Comprobado en la dirección publicada: sirve la 0.4.0, Inicio, búsqueda y radio dan 200, una canción suena, y YouLyPlus y LrcLib aceptan pedidos desde ahí. **Sin commit todavía de lo posterior a 69e5fe6d.** |
| 3.4 | Rehacer las capturas del video «Agregar a inicio» con el reproductor nuevo (Diseño y marca tiene las del viejo). |

## 7. Bloque 4 · Funciones nuevas (la 0.5, con la regla de 3 a 5)

| # | Función | Nota |
|---|---|---|
| 4.1 | **Letras sincronizadas** | LRCLIB se puede pedir directo desde el navegador. Ocupa el botón de la izquierda de la fila de abajo. |
| 4.2 | **Temporizador para dormir** | Como el de Android: 15, 30, 45 y 60 minutos, o al terminar la canción. |
| 4.3 | **Exportar e importar la biblioteca** | Para no perder Me gusta y listas si Safari borra los datos. |
| 4.4 | **Compartir con enlace propio** (`/cancion/:id`) | Hoy Compartir manda solo el enlace de la web. |

**Por evaluar:** Life Line narrando, enlazar con el TV.

**Propuesta del 4.1, letras sincronizadas (26-10).** CG pidió heredar los proveedores de la app Android
(`LyricsProviderRegistry`: YouLyPlus, PaxSenix, Unison, BetterLyrics, SimpMusic, LrcLib, KuGou, YouTube). Se
probaron desde aquí con «Despacito», como si preguntara la web publicada:

| Proveedor | ¿Sirve en la web? | Qué trae |
|---|---|---|
| YouLyPlus (servidores binimum y prjktla) | Sí, directo desde el navegador | **Palabra por palabra**, como Apple Music; dice que viene de Apple Music |
| LrcLib | Sí, directo | Por línea; base abierta de la comunidad; tiene hasta «Todo Lo Fue» de Lenin Ramirez |
| Unison | Deja preguntar, pero no tenía «Despacito» | Por línea; de reserva |
| SimpMusic | Solo si se pide con su firma de app, que un navegador no puede poner | Por línea; habría que pasarlo por nuestro servidor |
| PaxSenix | No: exige hacerse pasar por la app Echo Music | — |
| Better Lyrics | No: pide autorización (401) | — |
| KuGou | No deja que una página lea la respuesta | Catálogo chino, poco latino |
| YouTube Music | Por nuestro servidor | Casi siempre sin sincronizar |

- **Orden propuesto, el mismo de Android con los que sirven:** YouLyPlus → LrcLib → Unison. Todo directo desde el
  navegador: no gasta el cupo de Cloudflare ni pasa por nuestro servidor.
- **Cómo se ve (como Apple Music):**
  - un botón de letra en la esquina izquierda de abajo de «Ahora suena», y Compartir pasa al centro, como quedó
    en el 1.1;
  - la carátula se recoge arriba, como en la cola;
  - las líneas grandes y en negrita: la que suena en blanco y las demás tenues;
  - avanza sola, y con YouLyPlus se llena palabra por palabra;
  - tocar una línea lleva la canción ahí;
  - si no hay letra, lo dice.
- **Riesgo:** YouLyPlus es un servicio no oficial que copia las letras de Apple Music. Puede caerse o cerrar, y
  por eso LrcLib va de reserva.

**4.1 hecho en local (26-10), aprobado por CG tal cual** (`datos/letras.ts` y `componentes/Letras.tsx`):
- **Proveedores, en orden:** YouLyPlus (primero binimum, luego prjktla), LrcLib y Unison.
  - Se descarta la letra que no cuadra: casi toda en escritura asiática, como la «Despacito» de JJ Lin que
    devolvió prjktla, o que empieza después del final de la canción.
  - Se busca con el título limpio (sin «(Visualizer)», «feat.»…) y el primer artista.
  - Cada canción se pide una sola vez.
- **Pantalla:**
  - botón de letra abajo a la izquierda (Compartir pasa al centro), encendido con un cuadrito blanco;
  - la carátula se recoge arriba y los controles quedan abajo;
  - líneas de 28 px, la que suena en blanco;
  - palabra por palabra con YouLyPlus, con el tiempo contado en cada cuadro;
  - avanza sola, pero espera 3 s si la persona está leyendo; tocar una línea salta ahí;
  - dice de dónde viene la letra;
  - en el computador va a la derecha.
- **Arreglo que salió:** la carátula de la cabecera de la cola y de la letra se estiraba hasta la mitad del ancho
  (`.cola-compacto > span` también agarraba la carátula, que desde el 1.5 es un span).
- **Probado con «Todo Lo Fue»** (YouLyPlus, 24 líneas): saltar con una línea, palabra por palabra, desplazamiento
  solo, y pasar a la cola y volver. Falta que CG lo cierre y verlo en el iPhone real.

**Propuesta del 4.1b, el diseño de la letra como Apple Music (26-10).** Lo pidió CG: los modos de letra, el
difuminado de Apple Music y los detalles chicos.

Se investigaron dos fuentes:
- **La letra de nuestra app Android** (`ui/component/Lyrics.kt`): diez estilos (Apple, Apple V2, Karaoke, Brillo…),
  el difuminado por distancia, los duetos y los coros.
- **AMLL** («Apple Music-like Lyrics»), el proyecto abierto que replica Apple Music en la web: se leyó su código
  publicado. Es AGPL-3.0, así que **se toman las ideas y los valores, no el código**.

| # | Detalle | Cómo queda | De dónde sale |
|---|---|---|---|
| a | **Difuminado por distancia** | La línea que suena, nítida; las demás se desenfocan más cuanto más lejos están (1 px por línea, hasta 5 px), y las ya cantadas un poco más. Al desplazar con el dedo todo se ve nítido, para leer. | AMLL (`computeLineBlur`); Android: 0, 0, 2, 4, 6 px |
| b | **Escala** | La línea que suena, al 100 %; las demás, un poco más chicas (unos 97 %), crecen al llegarles el turno. | AMLL y Android (1,05) |
| c | **Movimiento en ola** | Al cambiar de línea, todas suben con un resorte, cada una un pelín después de la anterior. Es el movimiento típico de Apple. | AMLL (resortes por línea, con retraso) |
| d | **Dónde va la línea** | A un 35 % de la altura, como Apple. | AMLL (`alignPosition: .35`) |
| e | **Palabra por palabra con barrido** | Cada palabra se ilumina de izquierda a derecha con una orilla suave, en vez de encenderse de golpe. | AMLL (`wordFadeWidth: .5`, máscara) |
| f | **Modo «por línea» también con barrido** | Cuando la letra viene solo por línea (LrcLib, Unison), se reparte el tiempo de la línea entre las palabras según sus letras, y se barre igual. | Android, estilo Apple V2 |
| g | **Énfasis** | Las palabras largas y sostenidas (1 s o más, de 2 a 7 letras) brillan un poco y suben apenas al cantarse. | Regla de Apple, según AMLL |
| h | **Coros** | Las voces de fondo van más chicas y más tenues, debajo de la línea principal. | Android (85 %) y YouLyPlus las marca |
| i | **Duetos** | La segunda voz va alineada a la derecha y el grupo al centro. | Android (agentes v1, v2 y v1000) |
| j | **Pausas instrumentales** | En una pausa larga (más de 4 s) salen tres puntos que respiran y se llenan mientras dura. | Apple; AMLL (`InterludeDots`) |
| k | **Créditos al final** | «Escrita por …» (YouLyPlus trae los compositores) y de dónde viene la letra. | Apple Music |
| l | **Modo sin sincronizar** | Se lee como texto normal, sin difuminado ni barrido. | Ya está |

**Cómo se hace en la web:**
- todo con `transform`, `opacity` y `filter: blur()` por línea, que el iPhone acelera con la tarjeta gráfica;
- el resorte, con una cuenta propia por línea;
- el barrido, con una máscara degradada en cada palabra;
- se anima solo lo que se ve en pantalla, para no cargar el iPhone.

**4.1b hecho en local (26-10), aprobado por CG con los doce detalles.**
- **La ola:** técnica FLIP (la lista salta y cada línea se anima desde donde estaba, con 40 ms más por línea) y
  una curva con rebote. Medido: la línea siguiente arranca primero y las de abajo después.
- **Difuminado:** 1 px por línea de distancia y 1 px más en las ya cantadas, hasta 5 px. Al leer con el dedo, todo
  nítido durante 3 s.
- **Barrido, énfasis y pausa:** van directo en el DOM cada cuadro (`--p` y `--brillo`), sin repintar React.
- **Probado con «Despacito»:** coros, dueto a la derecha, grupo al centro, énfasis, pausa con sus tres puntos y
  «Escrita por…».

**Arreglos que salieron al mirar:**
- **YouLyPlus elige la versión por la duración.** Con la del video oficial (4:40, con intro) trajo «Despacito
  (Live)».
  - Ahora se descarta la letra de otra versión (Live, Remix, Acústico…) y se vuelve a preguntar sin duración.
  - Si la versión dura distinto que lo que suena, la letra se muestra para leer, con un aviso.
  - Sin datos de versión (servidor prjktla), se comprueba que la letra termine cerca del final.
  - Se quitan las líneas de créditos de QQ Music («Título - Artista (…)», «作词：…»).
- **La letra espera a saber la duración** antes de buscar: si se abría mientras la canción cargaba, elegía mal.
- **Tocar una canción en el primer segundo de la app fallaba** («loadVideoById is not a function»; venía de
  antes). Ahora queda en espera hasta que el reproductor está listo. Probado: tocada a los 0,4 s, sonó.

**Pendiente, de la fuente:** algunas versiones traen espacios de más en palabras con tilde, en la línea completa
también («mirá ndote»). No se adivina con reglas, porque rompería «está bien». Falta que CG lo cierre y verlo en el
iPhone real.

## 8. Después (ideas que llegan en medio)

Aquí se anota lo que salga mientras se trabaja un bloque. Se revisa al cerrar cada bloque.

- **¿Un anuncio que no se detectó?** (26-10, prueba en local) Con «BbY WOW (Visualizer)», el tiempo iba en 0:14 y
  volvió a 0:01 sin que nadie tocara nada. Parece un anuncio de unos 15 s que pasó escondido, sin la hoja del
  anuncio (resguardo 1). Hay que reproducirlo y revisar la detección (`TOLERANCIA_ANUNCIO` en `tienda.ts`).
- **Buscar con cuadros de colores por género** (26-10, salió en el 2.4), como «Explorar categorías» de Apple Music.
  Hoy la pantalla se ve vacía debajo de «Explorar por momento».
- **«Escuchado hace poco» en cuadrícula de dos columnas** (26-10, salió en el 2.4), como «Agregado recientemente».
- **El servidor de pruebas se apaga solo** (26-10): la app lo detuvo y el panel siguió mostrando lo que tenía
  guardado. Antes de probar, revisar con `preview_list` que esté encendido.
- **Arreglado el 26-10:** el modo demo congelaba el reproductor aunque se navegara fuera de él, y guardaba sus
  datos de ejemplo encima de los reales (tema claro, historial falso). Ahora guarda aparte
  (`lifemusic-*-demo`) y se descongela al tocar una canción.

## 9. Decisiones de CG

**Aprobado el 26-10, tal cual:**
- el orden de los bloques;
- la fila de abajo con Compartir y Cola en las puntas;
- cuando Android y Apple no coincidan, manda Apple.
