# Automix v2: mezclas de verdad (plan por bloques)

Pedido de CG el 09-11-2026, a partir de un TikTok del AutoMix de Apple Music (iOS 26) con «No Me Quiero Casar» (Bad
Bunny) y «Al Natural (feat. Yandel)» (Tego Calderón). Con esas mismas dos canciones, nuestro modo mezcla no logra nada
parecido con ningún estilo. Es la «Mezcla v2» que estaba pensada para la 2.1.1; CG quiere arrancarla ya.

Nada de código hasta que CG apruebe el plan. Se hace un bloque a la vez y lo nuevo va a «Después».

## 1. Qué hace Apple: análisis cuadro a cuadro y de audio

Herramientas: ffmpeg para los cuadros y el audio, librosa para tempo, golpes, tonalidad y espectro, y demucs para
separar voces, batería, bajo y lo demás. Las imágenes están en esta carpeta.

| Video | Qué pasa |
|---|---|
| 0–10,8 s | Suena A sola, de 3:04 a 3:15. La canción dura 3:46. Su outro **no tiene batería** (demucs: −72 dB); suenan la voz y el colchón. |
| 10,8 s | Aparece «Mixing» y la barra brilla. **La mezcla arranca 31 s antes del final**, en el outro de A. |
| 10,8 s | Entra B, **no desde el principio: desde su 0:11**. Se salta la intro y entra justo donde arranca **su batería** (−43 dB), debajo de la última frase de A. |
| 12,5 s | Termina la última frase de Bad Bunny (voz grave, 115–140 Hz) y **en ese mismo golpe** empieza el gancho de B (voz más alta, 230–280 Hz). Las voces nunca se pisan. |
| 12,5–20,7 s | **El mashup:** voz y batería de B sobre el colchón armónico de A, que sigue sonando (−39 dB). Funciona porque los tonos son relativos: A en La# menor, B en Do# mayor. |
| 14,6–19,5 s | **Rampa de tempo:** B entra estirada al tempo de A (99,4 BPM) y las dos bajan juntas hasta el de B (95,7) sin que se descuadre un golpe. |
| 20,7 s | **Traspaso:** cambian título y progreso. Ocurre a mitad de la mezcla y en límite de frase (16 golpes después de empezar). Se va el colchón de A (−47 dB). |
| 21,2–23 s | La carátula se funde como tinta: la nueva aparece por manchas borrosas desde una esquina y el fondo pasa de beige a rojo. |
| ~31 s | Termina «Mixing». En total: **unos 20 s, 32 golpes, 2 frases de 4 compases**. La frase vocal se repite exacta cada 9,9 s. |

**La receta.** Apple no mezcla «al final de la canción»: busca dónde encajan las dos.

1. Sale por **estructura**: el outro de A, sin batería.
2. Entra por **estructura**: el arranque de la batería de B, alineado a frase.
3. Las **capas no chocan**: batería nueva bajo voz vieja; voz nueva cuando la vieja calla; colchón viejo bajo voz nueva solo
   si el tono es compatible.
4. **Estira el tempo de verdad**, con rampa.
5. **Mezcla larga**, de 2 frases, cuando la pareja lo permite.
6. El **traspaso** (título, carátula y progreso) va a mitad de la mezcla.

## 2. Por qué el nuestro no lo logra (revisado en el código)

1. **El tempo nunca se estira.** `computeAutomixPlan` calcula `tempoRatio` y `pitchRatio`, pero solo llegan al panel de
   depuración: el reproductor entrante nunca los recibe. Con 99,4 contra 95,7 BPM los golpes se separan medio golpe en 16
   tiempos y suena desordenado con cualquier estilo.
2. **Mezcla corta y tarde.** En «canción completa» entra en los últimos compases audibles, con 16 golpes y 16 s como máximo.
3. **La nueva entra por su intro** (o por un `mixInPoint` de energía), sin saber dónde arranca su batería o su gancho.
4. **No sabe dónde hay voces ni batería.** El analizador solo mira energía, tempo y tono, y únicamente de los primeros
   16 s y los últimos 60 s.
5. **El traspaso va al empezar**: el reproductor cambia a la nueva en el primer instante de la mezcla.
6. **Corrección de tono de hasta ±3 semitonos**, calculada pero sin aplicar. Apple no transpone: elige parejas que encajan.

## 3. Plan por bloques

### Bloque A · Que los golpes no se separen (hoy)
Lo que más se oye y lo que menos arriesga. Sin esto, nada de lo demás suena bien.
- **A1. Estirar de verdad:** la entrante arranca a `PlaybackParameters(speed = bpmA / bpmB)`. Es estiramiento sin cambio de
  tono (Sonic, ya incluido en ExoPlayer). Se limita al 8 %; más allá se queda el estilo asistido actual.
- **A2. Rampa de tempo como Apple:** durante la primera frase las dos bajan juntas al tempo de la nueva, con
  `velA · bpmA = velB · bpmB` en todo momento. Al traspaso, la nueva queda a su velocidad normal.
- **A3. Mantener la fase:** cada ~250 ms se mide el desfase entre los golpes de las dos y se corrige con un toque mínimo de
  velocidad (±0,5 %), como el «pitch bend» de un DJ. Se anota en el registro (`LifeMusicMezcla`).
- **A4. Sin transponer:** se quita la corrección de ±3 semitonos. La compatibilidad de tono decide el largo de la mezcla
  (bloque B), no se fuerza.
- **Prueba:** las mismas dos canciones del video, con el registro del desfase golpe a golpe.

### Bloque B · Dónde y cuánto mezclar
- **B1. Largo según la pareja:** 2 frases (32 golpes) si tempo ≤ 8 % y tonos compatibles (rueda de Camelot: el mismo, su
  relativo o ±1 quinta); 1 frase si solo el tempo encaja; si no, corte en el golpe o eco.
- **B2. Salida y entrada por estructura** (necesita el bloque C): sale donde A se queda sin batería o empieza su outro, y
  entra donde arranca la batería o el gancho de B, las dos en límite de frase de 4 compases.
- **B3. Traspaso a mitad de la mezcla:** título, carátula, progreso y Nova cambian en el límite de frase del medio.
  Es el «relevo» que ya existe para el estilo CIERRE, llevado a todos los estilos.

### Bloque C · Un análisis que entienda la canción (en el teléfono)
- **C1. Batería sí/no por compás:** separación armónico-percusiva sencilla sobre el espectro que ya se calcula.
- **C2. Voz sí/no por compás:** primero heurística (energía armónica en 200 Hz–3 kHz con altura estable). Si no alcanza,
  un modelo pequeño en ONNX, que ya corre en la app por Nova. Solo con licencia abierta.
- **C3. Secciones:** intro, estrofa, coro y outro por novedad y autosimilitud. Analizar más de la canción: hoy son
  16 s + 60 s; pasar a 40 s + 75 s, o la canción entera si el tiempo de análisis lo aguanta.
- **C4. Puntaje de pareja:** tempo, tono, energía y choque de voces. Con eso se elige el estilo (MASHUP, BLEND, FILTRO,
  CORTE) y se muestra en la etiqueta y el aviso: «Mixed Editor · Mashup».

### Bloque D · El mashup por capas
- Ecualización por bandas en cada reproductor: la batería y los graves de la nueva entran primero, el colchón de la vieja
  se queda bajo la voz nueva y los graves cambian de mano en el límite de frase.
- Solo cuando C dice que las voces no chocan y el tono es compatible.

### Bloque E · Laboratorio para afinar sin adivinar
- Registro por mezcla: desfase de golpes, tempos, tonos, puntos elegidos y estilo.
- Script en el PC que lee ese registro y grafica cómo quedó cada mezcla, como el análisis del video.

## 4. Orden y alcance

| Bloque | Qué se nota | Tamaño | Cuándo |
|---|---|---|---|
| A | Los golpes por fin cuadran y el cambio de tempo no se nota | 1 tanda | Hoy, si CG aprueba |
| B1 + B3 | Mezclas largas cuando encajan y traspaso a la mitad | 1–2 tandas | Siguiente |
| C | La app sabe dónde hay voz y batería | 2–3 tandas | 2.1.1 |
| B2 + D | El mashup de verdad | 2 tandas | 2.1.1 |
| E | Afinar con datos | junto a A | Siempre |

## Después (ideas que salieron y no entran ahora)
- La carátula que se funde como tinta (Apple). Nuestro equivalente aprobado es el tocadiscos; la tinta podría ser un
  estilo de transición visual aparte.
- ~~El brillo de la barra durante «Mixing».~~ Pasó a hacerse el 11-11, a pedido de CG.
  - **Medido cuadro a cuadro**, a 10 cuadros por segundo:
    - la parte llena de la barra se prende en unos 0,3 s, justo cuando aparece «Mixing» (10,8 s);
    - queda quieta, sin titilar, toda la mezcla;
    - sigue sobre el pedacito lleno de la canción nueva;
    - se apaga despacio, en unos 4 s, antes de que vuelva «Lossless» (~31 s);
    - en el tramo oscuro del video la parte llena brilla un 50 % más que al final.
  - La etiqueta «Mixing» también se ilumina.
  - **Programado** en `ui/player/BrilloDeMezcla.kt`: un halo de cinco trazos, sin filtros de desenfoque, que sirve en
    cualquier Android.
    - Se prende en 0,35 s y se apaga en 3,5 s.
    - Va blanco en fondos oscuros y en el color del tema en los claros.
