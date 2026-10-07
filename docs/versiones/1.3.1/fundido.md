# Ficha técnica · Fundido al elegir otra canción

**Versión:** 1.3.1 · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Idea de:** CG, 2026-10-07 · **Mesa de Ideas:** «Fundido suave al cambiar de canción»

## 1. Qué es

Hoy, si suena una canción y eliges otra (desde la búsqueda, un álbum, una lista o el inicio),
la que sonaba **se corta en seco** y la nueva **arranca de golpe**. Con esta función el cambio
se hace en tres tiempos: **bajar, cambiar, subir**.

Lo que decidió CG en la fase 1:

| Pregunta | Decisión |
|---|---|
| Cómo se siente | **Bajar, cambiar, subir**, con un solo reproductor. |
| Duración | **Medio, unos 1 s** en total. |
| Dónde aplica | **Solo al elegir una canción nueva**. Siguiente, anterior, la cola y pausar quedan como hoy. |
| Ajuste | **Interruptor propio, encendido de fábrica.** |

## 2. Cómo lo vive la persona

1. Suena una canción y, desde la búsqueda, tocas otra.
2. La que suena **baja suave en 0,4 s**. En ese momento la pantalla todavía muestra la anterior.
3. Se cambia. Mientras la nueva carga hay silencio, igual que hoy, pero sin golpe.
4. Cuando la nueva empieza a sonar, **sube suave en 0,6 s** hasta tu volumen.

Bajar es corto porque retrasa el cambio, y subir es más largo porque no retrasa nada.

**Cuándo no hay fundido** (el cambio queda como hoy):

- No sonaba nada: estaba en pausa, detenida o es la primera canción. No hay nada que bajar y
  la nueva arranca con su primer golpe entero.
- Estás transmitiendo al TV (Cast, DLNA o TV con navegador), porque suena el TV, no el teléfono.
- Hay una transición de Automix o una transición suave en marcha. Ahí ya hay dos reproductores
  mezclando, y meter un tercer fundido es arriesgado.
- El teléfono está en silencio dentro de la app.
- El interruptor está apagado.
- Escuchar juntos: cuando el invitado sigue al anfitrión no hay fundido, para no
  desincronizarlo 0,4 s.

**Ajustes › Reproductor:** «Fundido al elegir otra canción», con la descripción «La que suena
baja suave y la nueva entra subiendo, sin cortes en seco». Está encendido de fábrica.

## 3. Decisiones técnicas

### 3.1 Un solo punto de entrada

Todas las formas de elegir una canción terminan en `MusicService.playQueue(queue, playWhenReady)`.
Son 113 llamadas en 43 pantallas y menús, y el carro también. Por eso el fundido va **solo ahí**,
sin tocar ninguna pantalla:

```kotlin
fun playQueue(queue: Queue, playWhenReady: Boolean = true, conFundido: Boolean = true) {
    if (conFundido && fundidoAplica(playWhenReady)) bajarCambiarSubir { playQueueYa(queue, playWhenReady) }
    else playQueueYa(queue, playWhenReady)   // el playQueue de hoy, renombrado, sin cambios
}
```

Escuchar juntos llega por `PlayerConnection.playQueue` con `allowInternalSync = true`. En ese
caso `PlayerConnection` pasa `conFundido = false`.

### 3.2 El motor (en `MusicService`)

- Hay un `fundidoJob` y un contador de generación, igual que `crossfadeJob`.
- **Bajar:** rampa del volumen del reproductor, desde el actual hasta 0, en 400 ms. Usa la curva
  coseno de potencia igual que la que ya usa el fundido de Automix, con pasos de 15 ms (los de
  100 ms sonaban a «cremallera»).
- **Cambiar:** con el volumen ya en 0 se ejecuta el `playQueue` de hoy, tal cual.
- **Subir:** espera a que el reproductor nuevo esté sonando de verdad (`isPlaying`). Entonces
  hace una rampa seno de 0 al volumen objetivo en 600 ms. El objetivo se relee en cada paso, así
  que si mueves el volumen a mitad, se respeta.
  - El **volumen objetivo** es el tuyo. Si otra app pidió bajar el volumen
    (`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`), es el 20 % que ya usa la app.
- **Si eliges otra canción durante el fundido,** el fundido anterior se cancela y el nuevo baja
  desde donde iba, sin volver a subir primero. Si tocas tres canciones seguidas, solo se cambia
  a la última.
- **Red de seguridad, lo más importante.** Nunca puede quedar el reproductor mudo:
  - si el fundido se cancela y nadie lo reemplaza, se devuelve el volumen objetivo;
  - si pausas, si hay un error o si la espera pasa de 20 s, se devuelve el volumen y el
    fundido termina;
  - si el Automix cambia de reproductor a mitad, el fundido no toca el nuevo.

### 3.3 La parte que se puede probar sin teléfono

`playback/audio/FundidoAlElegir.kt` va junto a `ConduccionAutomix` y no depende de Android:

```kotlin
object FundidoAlElegir {
    const val BAJAR_MS = 400L
    const val SUBIR_MS = 600L
    fun bajada(progreso: Float): Float      // 1 → 0, coseno
    fun subida(progreso: Float): Float      // 0 → 1, seno
    fun pasos(ms: Long): Int                // ~15 ms por paso
    fun aplica(activo: Boolean, sonando: Boolean, transmitiendo: Boolean,
               enTransicion: Boolean, silenciado: Boolean, playWhenReady: Boolean): Boolean
}
```

### 3.4 Ajuste

- `FundidoAlElegirKey = booleanPreferencesKey("fundidoAlElegir")` en `PreferenceKeys.kt`, con
  `true` de fábrica.
- Un `SwitchPreference` en `PlayerSettings`, junto a «Transición suave», y su entrada en
  `SearchableSettings` para que salga al buscar «fundido».
- Textos en es y en.

### 3.5 Novedades de la 1.3.1

Pasan a 4 funciones, dentro de la regla de 3 a 5:

- una cuarta sección en `Boletines.v1131`;
- una cuarta función en `web/novedades/1.3.1.json`, con el ícono `graphic_eq`.

El JSON de producción tiene 3 funciones. Se vuelve a desplegar en el ritual de publicación, que
de todos modos tiene que poner los bytes y el sha256 del APK.

## 4. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| El reproductor queda mudo | Bajo, pero sería grave | Red de seguridad (3.2) más una prueba manual específica de pausar a mitad del fundido |
| El 0,4 s de bajar se siente lento | Medio | Es una constante; si CG lo siente lento en la prueba, se baja a 0,25 s en un minuto |
| Choque con el volumen de la app o con otra app que pide bajar el volumen | Bajo | El objetivo se relee en cada paso |
| Con «descarga de audio» activa, el volumen no se mueve | Bajo; ExoPlayer lo maneja | Prueba manual con la opción activa |
| La sesión del carro toca `MusicService` a la vez | Medio | Aviso a la sesión «Life Music · Carro»: que no toque `playQueue` mientras dure esto |

## 5. Tandas

1. **Motor, unos 20 min más la compilación.** `FundidoAlElegir.kt` con sus tests de `:app`, la
   clave de ajuste y el gancho en `playQueue`. Termina con un APK instalado en el S25.
2. **Ajuste y novedades, unos 15 min.** El interruptor, la búsqueda en Ajustes, los textos y la
   cuarta función del boletín y de `novedades/1.3.1.json`. Termina con otro APK instalado.

## 6. Pruebas

**Automáticas (`:app:testArm64FossDebugUnitTest`):**

- las curvas empiezan y terminan exacto (1→0 y 0→1) y nunca suben al bajar ni bajan al subir;
- `aplica` da falso en cada caso de «cuándo no hay fundido»;
- `NovedadesTest` sigue en verde con 4 funciones.

**En el S25, las hace CG:**

1. Suena una canción; buscar otra y tocarla. Debe bajar, cambiar y subir.
2. Lo mismo desde un álbum, una lista y el inicio.
3. Con la música en pausa, tocar una canción: arranca normal, sin fundido.
4. Tocar tres canciones seguidas rápido: suena solo la última, sin saltos de volumen.
5. Tocar una canción y pausar justo en el fundido; al reanudar suena a volumen completo.
6. Elegir una canción durante una transición del Automix: queda como hoy, sin silencio.
7. Bajar el volumen de la app a la mitad, elegir otra canción: sube hasta la mitad, no más.
8. Con el TV conectado: sin fundido y el TV cambia normal.
9. Apagar el interruptor: vuelve el corte de antes.
10. En el carro, buscar y tocar una canción: también funde.

**Diagnóstico:** cada fundido deja una línea en logcat con la etiqueta `LifeMusicFundido`
(empezó, cambió, subió, cancelado, red de seguridad), para leerla desde el teléfono si algo
suena raro.
