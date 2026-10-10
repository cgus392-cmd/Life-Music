# Ficha técnica · Guardar para túneles (precarga)

**Versión:** carro 0.3.0 · **Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución)
**Idea:** 26 de la hoja de ruta («Precargar la siguiente canción, para túneles y huecos de señal»)

## 1. Qué es

Mientras hay buena señal, el carro guarda en el disco **el resto de la canción que suena y la siguiente
entera**. En un túnel, un peaje o un hueco de señal, la música no se corta.

## 2. Qué hay hoy y por qué se corta

- El reproductor guarda en memoria **50 s por delante** (`DefaultLoadControl` 50 000 ms). Un túnel o hueco de
  más de 50 s corta la canción.
- `preloadUpcomingItems` del servicio ya prepara la siguiente, pero **solo su dirección**, no el audio. Sin red,
  la siguiente no arranca.
- Además, hay dos trampas en el resolvedor de `MusicService` para el caso sin red:
  - Las direcciones de YouTube caducan a la hora. Aunque el audio esté en la caché, si la dirección caducó, el
    resolvedor intenta pedir otra a YouTube y falla sin red.
  - Si el audio está en la caché pero la dirección ya no está en memoria (porque se reinició la app), lo trata
    como «entrada fantasma» y **borra la caché** de esa canción.

## 3. Cómo lo vive la persona

1. Suena una canción. A los pocos segundos, en la Cola, la canción actual y la siguiente muestran un iconito de
   «guardada». Life Line dice una vez «La siguiente ya va guardada».
2. Entra a un túnel. El indicador de red de Inicio pasa a «Sin internet» y Life Line dice «Sin señal: sonando
   de lo guardado». La canción sigue y la siguiente arranca igual.
3. Al salir del túnel, todo sigue normal y se vuelve a guardar lo que viene.

**Ajustes › «Guardar para túneles»:** tiene tres pastillas.
- **La siguiente** (de fábrica, recomendado).
- **Las dos siguientes.**
- **Apagado.**

## 4. Decisiones técnicas

### 4.1 Dónde se guarda: en el disco, no en memoria

- Se usa la misma `playerCache` del servicio: LRU en disco, de 1 GB de fábrica.
- **No gasta RAM**, lo que importa en radios de 1 GB.
- El reproductor sigue con sus 50 s en memoria. Cuando llega a la parte guardada, la lee del disco (la caché
  está en la cadena de `CacheDataSource`).

### 4.2 Cómo se guarda

- Clase nueva `Precarga` en `playback/`, junto al servicio. `MusicService` solo la crea cuando
  `BuildConfig.ES_CARRO`; el teléfono no la usa.
- Para guardar una canción, lee su audio por **la misma cadena de fuentes del reproductor**
  (`createDataSourceFactory()`: resolvedor, caché de descargas, `playerCache` y red) y descarta los bytes. Al
  pasar por `playerCache` quedan escritos en el disco exactamente como el reproductor los va a pedir.
- Orden:
  1. el resto de la actual;
  2. la siguiente;
  3. la de después, si el ajuste lo pide.
- Una sola descarga a la vez.

### 4.3 Cuándo

- Solo si el indicador de red (`MonitorDeRed`) dice **Estable o Lenta**. Con Deficiente o Sin internet no se
  intenta.
- Solo cuando el reproductor ya está sonando (`READY`) con al menos 30 s en memoria. **La canción actual
  siempre tiene prioridad.**
- Si cambia la cola (saltar, elegir otra, reordenar o vaciar), se cancela lo que iba y se recalcula.
- La decisión es una función pura, `Precarga.decidir(red, buffer, ajuste, cola)`, con tests unitarios.

### 4.4 Arreglos en el resolvedor para el caso sin red

Solo cambia el comportamiento cuando está activa la precarga:
- **Recordar las direcciones guardadas en disco**, en un archivo pequeño dentro de `filesDir`, para que
  sobrevivan a un reinicio de la app. Así ninguna canción guardada se toma por «fantasma».
- Si la canción **está entera en la caché**, se reproduce desde la caché aunque la dirección haya caducado. La
  clave de la caché es el id de la canción, así que la dirección no se usa para leer.

### 4.5 Datos y memoria

- En calidad Normal, unos **4 MB por canción**. Con «La siguiente», como mucho se gasta una canción de más por
  cada salto.
- La bitácora anota los MB guardados.
- RAM: igual que hoy.

### 4.6 En pantalla

- **Cola:** un ícono pequeño `offline_pin` junto a las canciones guardadas enteras.
- **Life Line:** las dos voces nuevas, ver la ficha de Life Line.
- **Diagnóstico:** cada paso deja una línea en logcat con la etiqueta `LifeMusicPrecarga`, y los más
  importantes van también a la bitácora.

## 5. Riesgos

| Riesgo | Qué tan probable | Cómo se cubre |
|---|---|---|
| **Toca el motor compartido con el teléfono** | Seguro | Todo va detrás de `ES_CARRO`; aviso a la sesión del teléfono antes de la tanda 4; los 33 tests de `:playback` |
| Gastar datos del hotspot en canciones que se saltan | Medio | De fábrica solo «la siguiente»; la idea 25 (ahorro de datos) lo afinará después |
| La precarga le roba velocidad a la canción que suena | Bajo | Solo arranca con 30 s en memoria; una descarga a la vez; se pausa si la red pasa a Deficiente |
| La caché llena borra algo guardado | Bajo | La LRU de 1 GB guarda unas 250 canciones; lo guardado es lo más reciente |
| Una dirección caducada con la canción a medio guardar | Medio | Solo se usa la caché sin red si la canción está entera; si no, se sigue como hoy |

## 6. Pruebas

**Automáticas:** tests de `Precarga.decidir`:
- con red Deficiente no precarga;
- espera los 30 s de búfer;
- respeta el ajuste;
- recalcula al cambiar la cola.

Además, los 33 de siempre.

**En el emulador:**
1. Reproducir; la cola muestra «guardada» en la actual y la siguiente, y la bitácora anota los MB.
2. Cortar la red con `svc wifi disable` y `svc data disable`. La canción sigue hasta el final y la siguiente
   suena entera.
3. Cortar la red, cerrar la app a la fuerza y abrirla sin red: la canción guardada suena. Es la prueba de la
   «entrada fantasma».
4. Saltar canciones rápido: la precarga se cancela y se recalcula sin errores.
5. Con el ajuste en «Apagado» todo queda como hoy.

**En el carro, las hace CG:** pasar por un túnel o un tramo sin señal conocido, o apagar el hotspot un minuto
mientras suena.
