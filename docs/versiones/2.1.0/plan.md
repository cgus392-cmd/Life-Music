# Life Music 2.1.0 · Rendimiento + Nova DJ (plan)

> **Estado:** fase 1, ideas para aprobar (07-11-2026). Se lanza **mañana**, así que va por tandas cortas, de lo seguro
> a lo vistoso. La regla de 3 a 5 funciones nuevas se cumple con el bloque B; el bloque A es mantenimiento.
> Las rachas y mascotas de Bandas (`docs/versiones/2.1/`) pasan a la **2.2.0**, para no mezclar.

## A · Rendimiento: medir, atacar y comparar

**Primero los números** (en el S25 y en el emulador con perfil de gama baja), para comparar el antes y el después:
- arranque en frío (`am start -W`);
- cuadros lentos al deslizar el Inicio y al abrir el reproductor (`gfxinfo`);
- memoria en reposo y sonando (`meminfo`).

**Lo que se ataca, según lo que salga:**
1. **Perfil de arranque (Baseline Profile):** Android precompila lo que se usa al abrir y al deslizar. Suele bajar el
   arranque y los tirones entre un 20 y un 30 %, sin tocar nada visible.
2. **Lo que se carga al abrir:** sacar del arranque lo que no hace falta en el primer segundo (Discord, Last.fm,
   sincronización, comprobaciones).
3. **El Inicio y el reproductor:** recomposiciones de más, imágenes más grandes de lo que se muestran y efectos que
   corren aunque no se vean.
4. **Gama baja:** ampliar el modo ligero si los números lo piden.

**Éxito:** abrir más rápido y menos cuadros lentos en los números, sin quitar funciones.

## B · Nova, más DJ (lo que hace el DJ de Spotify, a nuestro modo)

El DJ de Spotify:
- arma la música por **bloques con una onda** (género, ánimo, artista o actividad);
- comenta con **contexto** (por qué esa canción y datos del artista), y Spotify dice que eso hace que la gente no salte
  canciones;
- desde 2025 **acepta pedidos por voz**: se mantiene pulsado el botón y se le pide la música;
- y con un toque **cambia de onda**.

Para Nova, todo en el teléfono y sin cuentas:
1. **Pídele a Nova.** Se mantiene pulsado su botón y se le dice «algo para entrenar» o «reggaetón viejo». El
   reconocedor de voz en español **ya está en Estudio** (NeMo); la frase se vuelve una búsqueda o una radio. También se
   puede escribir.
2. **Otra onda.** Un toque y el siguiente bloque cambia de dirección (más calmado, más movido, otro género).
3. **Comentarios con contexto real:** la hora del día, cuántas veces la has escuchado, si la escuchabas hace un año, el
   año y el álbum, si es una colaboración. Todo sale de tu historial, en el teléfono.
4. **Habla sobre la intro, como en la radio:** la canción siguiente arranca bajita mientras Nova habla y sube al
   terminar (ducking), en vez de pausar.

## C · Estudio

Por definir con CG: qué mejoras quiere en Estudio para esta versión.

## Orden de tandas

| Tanda | Qué | Por qué en ese orden |
|---|---|---|
| 1 | A: medir + Baseline Profile + arranque | Seguro y medible |
| 2 | B4 (sobre la intro) + B2 (otra onda) | Lo que más cambia cómo se siente Nova, con poco riesgo |
| 3 | B3 (contexto real) | Solo frases nuevas, con datos que ya están |
| 4 | B1 (pídele a Nova) | Lo más vistoso y lo más largo; si no alcanza, pasa a la 2.1.1 |
| 5 | A3/A4 según los números + pruebas del día antes | Cerrar |

## Preguntas para CG

1. ¿El nombre es **2.1.0**, con las rachas y mascotas pasando a la **2.2.0**?
2. ¿Qué mejoras de **Estudio** quieres en esta?
3. Si mañana «Pídele a Nova» no alcanza a quedar fino, ¿sale en beta o espera a la 2.1.1?

## Decidido por CG (07-11, noche)

**Mañana (2.1.0):**
1. **Rendimiento:** medir, Baseline Profile y arranque, comparando con números.
2. **Nova, radio de verdad:**
   - **habla encima de la intro**, con la canción entrando bajita (ducking), o «primero hablo y luego suena», a elegir;
   - **guiones con energía y pausas**;
   - **otra onda**.
3. **Mixed Editor v1 (beta):**
   - mezcla la cola, una lista o los me gusta como un set;
   - las transiciones empiezan **en el compás** (con los golpes que ya detecta la app para el TV);
   - **cambio de bajos** (bass swap);
   - el orden se arma por energía y BPM.
   - Referencia: «Mix» de Spotify (beta de Premium, 2025: fundidos, ecualización, BPM y tonalidad, Auto, «Fade» y
     «Rise»).

**La siguiente (2.1.1):**
- **Voces naturales:**
  - neuronales en la nube y gratis (las de Microsoft: colombianas, mexicanas y argentinas), elegibles por el usuario y
    con aviso de privacidad, porque el texto que dice Nova pasa por Microsoft;
  - sin internet, las voces del teléfono.
- **Pídele a Nova.**
- **Mezcla v2:** igualar el tempo (beatmatching) y efectos tipo «Rise».

Las rachas y mascotas pasan a la 2.2.0. Lo de Estudio está pendiente de definir con CG.

## Mixed Editor: el editor y cómo se ve la transición (pedido de CG, 07-11)

**Referencias:**
- Spotify Mix, en modo manual: volumen, ecualización, datos de los golpes, efectos y la forma de onda de cada transición.
- Apple Music AutoMix (iOS 26): una animación de «Mezclando» en el reproductor mientras ocurre la transición; por dentro
  usa estiramiento de tiempo y beatmatching.
- **La meta de CG:** algo parecido, pero mejor y nuestro.

### La mesa de mezcla (el editor)

- Se abre desde una lista, la cola o los me gusta. Se ven **las dos ondas**, la de la canción que sale y la de la que
  entra, una sobre otra.
- **Se arrastra el punto de entrada**, que se pega solo al compás (la rejilla de golpes que ya calcula la app para el
  TV).
- **El largo de la transición** se mide en compases: 4, 8 o 16.
- **Estilos:** Auto, Fundido, Subida (un filtro que abre y un riser), Corte en el golpe y Eco.
- **Por canción:** volumen, **cambio de bajos** y ecualización de 3 bandas (graves, medios y agudos), con curvas.
- **Botón «Escuchar la transición»:** reproduce los 10 segundos alrededor del cambio.
- **Los mixes se guardan por lista**; más adelante se podrán compartir en una banda.
- **Con qué se dibuja la onda:** el mismo análisis que ya hace la app con la canción entera (golpes y volumen).

### Cómo se ve la transición en el reproductor

- **La barra de progreso se vuelve el trazo del logo:** unos pulsos coral la recorren unos segundos antes del cambio,
  avisando que viene una mezcla. Es nuestra identidad, no la de otros.
- **Las carátulas se funden en un tocadiscos:** la que sale se vuelve un disco que gira y se hunde mientras la que entra
  aparece encima.
- **Un rótulo pequeño, «Mezclando · Nova»,** mientras dura.
- Respetar «quitar animaciones» del sistema y el modo ligero de gama baja: ahí, solo un fundido sencillo.

### Reparto recomendado

- **Mañana (2.1.0):**
  - la mezcla automática v1;
  - **cómo se ve la transición** (los pulsos en la barra y las carátulas en el tocadiscos).
- **2.1.1:** **la mesa de mezcla**. Es grande (ondas, gestos, escucha previa y guardado) y conviene que Diseño haga la
  maqueta primero, para que no salga genérica.

## Nombre y video (08-11)

- **La función se llama «Mixed Editor»** (decisión de CG): ese nombre va en la app, en las fichas y en las notas. El aviso
  del reproductor dice «Mezclando · Nova».
- El video «Próximamente» está APROBADO: `Brand/videos/2026-11-07-proximamente-mezcla/proximamente-mezcla-1080x1920.mp4`,
  de 35 s, vertical y claro.
  - Muestra «Dale Don Dale» de Don Omar y «Por si mañana no estoy» de Omar Courtz.
  - Lleva música pop original, cierre con el logo de CG LABS y las ondas latiendo al ritmo.
- Pendiente de Diseño: la lámina de la transición en cada estilo de reproductor (DEFAULT, GRADIENT, BLUR, GLOW_ANIMATED,
  APPLE_MUSIC, LIVE_MESH, LIQUID_GLASS y Canvas).

## Mixed Editor · Tanda 4: el tocadiscos real (pedido de CG, 09-11)

La Tanda 3 (prueba.9 y prueba.10) quedó probada. CG la ve «muy simplona: solo gira» y pide un tocadiscos que se vea casi real: el vinilo sale de su funda (la carátula), se pone en el plato y el otro se guarda. Tiene que ser gráfico de verdad, no figuras armadas por código.

- **Diseño** (encargado el 09-11): maqueta en movimiento para la carátula en tarjeta y para Apple Music, kit de piezas por capas (vinilo, brillo, etiqueta, funda, brazo) y hoja de movimiento.
- **Apple Music:** es el estilo diario de CG; la carátula va a sangre y no se puede dañar ese estilo. Hoy no tiene tocadiscos: la carátula en tarjeta no existe en ese estilo.
- **General**, cuando CG apruebe:
  - programar con las piezas de Diseño, con la carátula y la etiqueta dinámicas;
  - giro con inercia, a los BPM o a 33⅓ rpm;
  - versión quieta para gama baja y para «Quitar animaciones»;
  - después, el resto de estilos de la lámina 4.
