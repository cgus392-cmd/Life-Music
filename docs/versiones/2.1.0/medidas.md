# 2.1.0 · Medidas de rendimiento

Con `medir.sh`: 5 arranques en frío (`am start -W`), 12 deslizamientos del Inicio (`gfxinfo`) y la memoria (`meminfo`).

## Antes: la 2.0.2 oficial (08-11-2026)

| | Galaxy A04s (gama baja: 3,6 GB, Android 14) | Galaxy S25 (gama alta: 10,9 GB, Android 17) |
|---|---|---|
| Arranque en frío | ~1.070 ms* | ~230 ms |
| Cuadros con tirón al deslizar el Inicio | **60,2 %** (492 de 817)* | 1,4 % (26 de 1.832) |
| Cuadro típico (p50 / p90 / p99) | 38 / 53 / 150 ms* | 11 / 13 / 27 ms |
| El hilo de la interfaz se atrasa | 309 veces | 21 veces |
| Memoria (PSS) | 152 MB | 733 MB (327 MB de gráficos y 239 MB nativos) |

**Lectura:**
- En el A04s, el cuello de botella es el **hilo de la interfaz**: componer y dibujar el Inicio. Para ir fluido, un
  cuadro tiene que tardar 16 ms.
- **La medida de gama del modo ligero falla:** con 3,6 GB y 8 núcleos clasifica al A04s como gama media, y el modo
  ligero de la 2.0.2 no se le activa.
- El APK solo trae el perfil de arranque de las librerías, no uno de Life Music.

\* Repetido con el A04s desbloqueado y la pantalla siempre encendida (`svc power stayon usb`). En la primera medida el
teléfono se bloqueó a mitad de camino.

**Experimento en el A04s con la 2.0.2:**
- la Biblioteca: 0,4 % de tirones;
- el Inicio **sin Aurora**: 13,8 %;
- el Inicio **con Aurora**: 60,2 %.

**El culpable:** `VidrioDeBarra`. En Android 12 o más captura y desenfoca la pantalla en cada cuadro.

## Después: la 2.1.0-prueba.2 (08-11-2026)

Cambios medidos:
- vidrio sólido en gama baja;
- medida de gama corregida (menos de 4,5 GB y sin clase de rendimiento cuenta como gama baja);
- perfil de arranque propio (`baseline-prof.txt`).

Antes de medir se compiló con `cmd package compile -m speed-profile`, que es lo que Android hace en reposo.

| | A04s antes → después | S25 antes → después |
|---|---|---|
| Cuadros con tirón en el Inicio (con Aurora) | **60,2 % → 17,2 %** | 1,4 % → 0,85 % |
| Cuadro típico (p50 / p90) | 38 / 53 → **22 / 38 ms** | 11 / 13 → 11 / 12 ms |
| El hilo de la interfaz se atrasa | 483 → 184 veces | 21 → 8 veces |
| Arranque en frío | ~1.070 → ~1.070 ms | ~230 → ~165 ms |
| Memoria (PSS) | 148 → 160 MB | 733 → 768 MB |

**Pendiente:**
- el 17 % que queda en el A04s, que viene del contenido del Inicio;
- la memoria del S25: 327 MB de gráficos y 239 MB nativos.
