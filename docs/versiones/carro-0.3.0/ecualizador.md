# Ficha técnica · Ecualizador del carro (mesa de sonido)

**Versión:** carro 0.3.0 · **Fase:** 3 (ejecución) · **Estado:** aprobada por CG el 2026-10-10 («sigue con esto junto
tus recomendaciones»)
**Idea:** 20 de la hoja de ruta, ampliada por CG: un ecualizador serio, profesional, que den ganas de usar

## 1. Qué es

Una mesa de sonido a pantalla completa, que se abre desde Sonando o desde Ajustes. Tiene:
- **ganancia general**;
- **graves y agudos**, en perillas grandes como las de un radio;
- **10 bandas** de 31 Hz a 16 kHz;
- una **curva** que dibuja lo que de verdad le pasa al sonido;
- un **analizador** que se mueve con la música detrás de cada banda;
- un **vúmetro** de salida izquierdo y derecho, con pico sostenido y luz de limitador;
- **presets** pensados para parlantes de carro.

## 2. Lo que pidió CG y cómo se cumple

> «Que no sea como el de Android, que cuando uno sube bajo o brillo o ganancia el volumen se baja.»

El efecto de Android (y la mayoría de ecualizadores) resta volumen a todo para dejar espacio al refuerzo. Por eso,
al subir graves, la canción entera suena más bajita. Aquí **no se resta nada**:
- Subir graves, agudos, una banda o la ganancia **suena más fuerte**.
- Para que eso no distorsione al pasar del máximo digital, al final de la cadena va un **limitador**. Solo actúa en
  los picos que se pasarían: ataca al instante y suelta en 120 ms. Detrás de él hay un recorte suave, como red de
  seguridad.
- El vúmetro muestra cuánto trabaja el limitador («Limitador −2,1 dB»). Así se ve que no hay trampa.

## 3. Decisiones técnicas

### 3.1 Motor nuevo en `:playback`, no el del teléfono

`CustomEqualizerAudioProcessor` (el del teléfono) no sirve para el radio por tres razones:
- recorta en seco cuando la señal se pasa;
- crea un objeto por cada muestra de audio, lo que es basura para el GC de un radio de 1 GB;
- reinicia los filtros con cada cambio, lo que hace chasquidos al arrastrar.

Por eso se escribe otro motor:
- **`MesaDeSonido`**: los ajustes (`AjusteMesa`), la matemática de los filtros (RBJ cookbook) y la respuesta en dB
  para dibujar la curva. Es Kotlin puro y tiene tests.
- **`MesaDeSonidoAudioProcessor`**: procesa en coma flotante de doble precisión, sin crear objetos por muestra, y
  conserva el estado de los filtros al cambiarlos.
  - Ganancia y encendido se suavizan en 20 ms; prender o apagar la mesa es un fundido, sin chasquido.
  - Una banda en 0 dB no se calcula.
  - El analizador (10 pasabandas sobre la mezcla mono) solo corre con la pantalla abierta.

### 3.2 Dónde va en la cadena

Al final, antes del medidor de ambiente. Así el limitador es lo último que toca el audio:
`voces → EQ del teléfono → filtro de transición → silencio → envolvente → MESA → medidor`.
`MusicService` la crea con `habilitado = BuildConfig.ES_CARRO`. En el teléfono devuelve `NOT_SET` y la cadena la salta
entera, sin ningún costo.

### 3.3 La pantalla (`Ecualizador.kt`)

Va a pantalla completa (en el riel ya no cabe otro botón en 600 px):
- **Arriba:** cerrar, el título, los presets en fila y el interruptor.
- **Al centro:** la tarjeta con la escala en dB, la curva con brillo, el analizador y los 10 deslizadores (±12 dB, de
  0,5 en 0,5; el doble toque vuelve a 0).
- **Abajo, el «rack»:** tres perillas (Graves 90 Hz, Agudos 9 kHz y Ganancia, ±12 dB) y el vúmetro LED de −42 a 0 dBFS:
  - balística VU: sube en 50 ms y baja en 300 ms;
  - pico sostenido 1,5 s;
  - luz LIM con los dB que quita el limitador.

El medidor va adelantado respecto a lo que suena, por el búfer de la tarjeta de audio, y se compensa con 300 ms de
retraso. En Ligero se mide a 15 cuadros por segundo; en Completo, a 30.

### 3.4 Presets

| Preset | Para qué |
|---|---|
| Plano | Sin cambios |
| Graves | Refuerzo de graves |
| Urbano | Reguetón y urbano |
| Tropical | Salsa, merengue y bachata: metales y percusión al frente |
| Rock | Rock |
| Pop | Pop |
| Voces | Voces al frente |
| Noche | Suave, cansa menos de noche |
| Volumen bajo | Curva de sonoridad para cuando se oye bajito |

Si se toca algo, queda «Personalizado». La ganancia no cambia con el preset.

### 3.5 Se guarda

En el DataStore del carro: `carroEq*`. Se aplica al arrancar el proceso (`InicioDelCarro`), aunque no se abra la
pantalla.

## 4. Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| CPU en radios flojos | Unos 18 MFLOPS con todo activo; las bandas en 0 no cuestan; el analizador solo corre con la pantalla abierta |
| Distorsión al subir mucho | El limitador y el recorte suave; la luz LIM lo avisa |
| Chasquidos al mover o al prender | Suavizado de 20 ms, fundido al prender o apagar y filtros que conservan su estado |
| Tocar el motor compartido | Va detrás de `habilitado`; en el teléfono no existe |

## 5. Pruebas

**Automáticas (`MesaDeSonidoTest`):**
- en plano, sale lo mismo que entra;
- con +12 dB de graves, sube el nivel y nunca pasa de 32767;
- la curva marca la ganancia de cada banda en su frecuencia.

**En el radio, las hace CG:**
- se oye el cambio al mover cada control;
- subir no baja el volumen;
- no hay chasquidos;
- el vúmetro baila al ritmo de la música.

## 6. Efecto en la 0.3.0

Con el ecualizador serían 6 funciones y la regla dice como máximo 5. Recomendación: **la cuenta de YouTube Music pasa
a la 0.4.0**. Es la de más riesgo, por el navegador viejo de los radios y el plan B por QR, y no se ha empezado.
Pendiente de que CG lo confirme.
