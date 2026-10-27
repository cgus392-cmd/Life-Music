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

| # | Qué |
|---|---|
| 3.1 | Commit local de todo (sin subir a GitHub). |
| 3.2 | Pruebas un día antes, con lista fija: CG en el PC y el probador en el iPhone. |
| 3.3 | Publicar, con el «publica» de CG. |
| 3.4 | Rehacer las capturas del video «Agregar a inicio» con el reproductor nuevo (Diseño y marca tiene las del viejo). |

## 7. Bloque 4 · Funciones nuevas (la 0.5, con la regla de 3 a 5)

| # | Función | Nota |
|---|---|---|
| 4.1 | **Letras sincronizadas** | LRCLIB se puede pedir directo desde el navegador. Ocupa el botón de la izquierda de la fila de abajo. |
| 4.2 | **Temporizador para dormir** | Como el de Android: 15, 30, 45 y 60 minutos, o al terminar la canción. |
| 4.3 | **Exportar e importar la biblioteca** | Para no perder Me gusta y listas si Safari borra los datos. |
| 4.4 | **Compartir con enlace propio** (`/cancion/:id`) | Hoy Compartir manda solo el enlace de la web. |

**Por evaluar:** Life Line narrando, enlazar con el TV.

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
