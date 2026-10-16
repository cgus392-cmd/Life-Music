# Ficha técnica · Karaoke propio: palabra por palabra donde no lo hay

**Versión:** 2.x · **Capa:** 1 (funciones nuevas) · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-15 («algo parecido a lo que hace VirtualDJ»)

## 1. Qué es

Hay canciones cuya letra está sincronizada **por línea**, pero no **palabra por palabra**. Las famosas ya traen la
sincronía palabra por palabra de los proveedores (letras «silábicas» de Apple y otros); con esas no se hace nada.

Para las demás, un motor en el teléfono **escucha la canción y encuentra el instante en que se canta cada palabra**.
Así Life Line, Letras y MetroLyrics pueden iluminarla palabra por palabra, **como un karaoke**.

**Límites que fijó CG:**
- **Solo canciones con letra por línea y sin palabra por palabra.**
- **Solo música descargada o local:** el archivo completo ya está en el teléfono, se procesa una vez y se guarda. No
  hay streaming de por medio ni hay que repetir el trabajo.

## 2. Cómo lo vive la persona

1. Baja una canción, o ya la tiene en local, y esa canción tiene letra por línea.
2. En segundo plano, sin hacer nada, la app la prepara: tarda de unos segundos a 1 o 2 minutos, según el teléfono.
3. La próxima vez que suena, la letra **se ilumina palabra por palabra**.
4. En Letras se ve una marca discreta, «Karaoke Life Music · beta», para que se sepa que la sincronía la hizo la app
   y no el proveedor.
5. Si una línea salió dudosa, esa línea se reparte por sílabas. Nunca queda peor que hoy.

## 3. Cómo funciona por dentro

**Alineación forzada:** el texto ya se conoce, así que solo hay que encontrar *cuándo* suena cada palabra.

| Paso | Qué hace | Con qué |
|---|---|---|
| 1. Leer el audio | Decodifica el archivo descargado a 16 kHz mono | MediaCodec / FFmpeg (la app ya trae ffmpeg-kit) |
| 2. Aislar la voz *(opcional)* | Quita la música para que el reconocedor oiga mejor | Separador de centro (ya existe) o MDX-Net (spike en src/debug). **El spike mide si hace falta.** |
| 3. Recortar por línea | Cada línea del LRC, con un margen de ±0,5 s | Los tiempos que ya trae la letra |
| 4. Reconocer | Un modelo de reconocimiento en español da tokens con su tiempo | sherpa-onnx (ya está en la app) con **NeMo FastConformer CTC español, int8, 99 MB** |
| 5. Alinear | Calza las palabras conocidas de la línea con lo reconocido (programación dinámica a nivel de palabra y sílaba). Lo que no se reconoce se interpola entre vecinos. | Código nuestro, probado con pruebas unitarias |
| 6. Guardar | La letra con tiempos por palabra, en el formato `WordTimestamp` que la app ya pinta | Room (tabla nueva o columna en `lyrics`) |

**Respaldo por línea:** si menos del 50 % de las palabras de una línea quedaron calzadas, esa línea usa el reparto por
sílabas dentro de su ventana.

## 4. Equilibrio entre teléfonos (regla de CG)

- **El modelo de 99 MB se baja una vez**, con la misma prueba de capacidad que las voces de Nova. Si el teléfono no
  puede, se queda con el **reparto por sílabas**: instantáneo, sin descarga y para todos.
- **Se procesa en segundo plano** con WorkManager: preferiblemente cargando o con la pantalla apagada, de a una canción
  y sin tocar la reproducción.

## 5. Riesgos

- **Música real:** el reggaetón, la salsa, el rap y los coros son difíciles. El spike mide la precisión con canciones
  reales.
- **Separar la voz** es lo más pesado. Si el reconocimiento funciona bien con la mezcla completa, se omite.
- **Tiempo por canción** en un teléfono medio. Se mide en el spike.
- **Licencia del modelo:** NeMo de NVIDIA suele ser CC-BY-4.0. Hay que confirmarla y dar crédito, como con las voces.

## 6. Spike (prueba técnica) antes de programar

Dura **1 o 2 días** y se hace primero en el PC, donde es más rápido probar:
1. Con sherpa-onnx en Python y el modelo español, **alinear 3 canciones reales** que tengan letra por línea.
2. Medir la **precisión**: cuántas palabras quedan a menos de 150 ms de donde se cantan, revisado a oído.
3. Probar **con y sin voz aislada**.
4. Si sirve, pasarlo al teléfono y **medir el tiempo** en el S25 (y estimar el de un teléfono medio).

**Con esos números, CG decide si va.**

## 7. Lo que necesito de CG

1. **Aprobar esta ficha** (o pedir cambios).
2. **Permiso para bajar al PC:**
   - el modelo `sherpa-onnx-nemo-fast-conformer-ctc-es-1424-int8` (98,7 MB, de las publicaciones de sherpa-onnx en
     GitHub);
   - el paquete de Python `sherpa-onnx`.
3. **Dos o tres canciones de prueba**, descargadas o locales, que tengan letra **por línea** y no palabra por palabra.
   Idealmente de estilos distintos: una balada, un reggaetón o un rap, y una salsa.

## 8. Resultados del spike (2026-10-15, noche, en el S25)

**Canción:** «Hazme Olvidarla», de Willie González (salsa, 5:37), con la letra por línea de LRCLIB.

| Camino | Cobertura (palabras calzadas por el audio) | Tiempo |
|---|---|---|
| Mezcla tal cual | 30 % | unos 10 s (1,5 s para decodificar y de 4,5 a 8 s para reconocer) |
| Mezcla con filtro de voz (banda y nivelador de FFmpeg) | 31 % | parecido: no sirve |
| **Voz separada con UVR MDX-Net KARA** (28 MB) | **56 %** | **3 min 45 s** para separar (0,67× tiempo real) y 8 s para reconocer |

- **El modelo de español carga en 1,4 s.** La FFT propia de 5120 puntos coincide con la DFT (error 0,0001).
- **Las estrofas claras quedan muy bien** y siguen el canto: por ejemplo, 2 s entre «pequeña» y «dentro», donde el
  cantante estira la vocal. Los coros, los soneos y las partes con orquesta fuerte siguen bajos.
- **Memoria:** separando, la app llegó a **1,7 GB** y Android la cerró en segundo plano. La versión real necesita:
  - un servicio en primer plano («Preparando karaoke…»), mejor con el teléfono cargando;
  - liberar memoria entre bloques y ajustar la arena de ONNX Runtime;
  - tal vez un modelo de separación más liviano.
- **Falta medir la precisión en milisegundos** contra una referencia palabra por palabra oficial: hace falta una
  canción famosa que ya la tenga.

**Diseño que sale de los números (propuesta):**
1. **Para todos, al instante:** el reparto por sílabas de las líneas.
2. **Camino rápido** (unos 10 s por canción): reconocer sobre la mezcla. Sirve sobre todo para pop y baladas con la
   voz clara.
3. **Camino completo** (solo teléfonos que pasan la prueba de capacidad, en segundo plano y cargando): separar la
   voz y reconocer. Se usa si el camino rápido quedó por debajo del 60 % de cobertura.
