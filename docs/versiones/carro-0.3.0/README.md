# Life Music for Car 0.3.0 · «Hecha para la carretera»

**Fase:** 2 (planteamiento técnico) · **Estado:** aprobada por CG el 2026-10-10 (fase 3, ejecución) · **Fecha:** 2026-10-10
**Número:** versionCode 3 / versionName 0.3.0 (la 0.2.0 es la del 01-10)

## Las 5 funciones

CG eligió el 10-10 las cuatro de la T4 que más se notan manejando y sumó Life Line, heredada del teléfono.
Son 5, el máximo de la regla de 3 a 5.

| # | Función | Idea en la hoja de ruta | Ficha |
|---|---|---|---|
| 1 | Reanudar al encender el carro | 16 | [reanudar.md](reanudar.md) |
| 2 | Botones del volante y del radio | 15 | [volante.md](volante.md) |
| 3 | Iniciar sesión con YouTube Music | 22 | [cuenta.md](cuenta.md) |
| 4 | Guardar para túneles (precarga) | 26 | [precarga.md](precarga.md) |
| 5 | Life Line en el carro | 32 (nueva) | [life-line.md](life-line.md) |

**Se pasan a la siguiente versión:** la 24 (descargas automáticas con Wi-Fi) y la 25 (ahorro de datos).
**Sigue por confirmar:** la 6 (latido).

## Cómo encajan

Las cinco cuentan una sola historia: el carro arranca y la música sigue sola (1). Se maneja sin tocar la
pantalla (2). Suena tu música (3), no se corta en un túnel (4), y Life Line te cuenta qué pasa (5).

Life Line tiene tres voces nuevas que salen de las otras funciones:
- «Retomando donde lo dejaste», de la 1.
- «La siguiente ya va guardada», de la 4.
- «Sin señal: sonando de lo guardado», de la 4 junto con el indicador de red.

## Pieza común: la bitácora del carro

Es una pieza de diagnóstico, no una función más. Está en **Ajustes › Acerca de › Diagnóstico**: una lista con
los últimos 50 sucesos y la hora de cada uno. Por ejemplo:
- encendido;
- apagado;
- reanudado, o por qué no se reanudó;
- botón recibido y por qué vía llegó;
- canción guardada;
- sesión iniciada;
- biblioteca sincronizada.

Hace falta porque CG no tiene `adb` en el radio, y cada marca de radio se porta distinto al encender y con los
botones. Con la bitácora, CG mira la pantalla y me dice qué pasó. Nunca guarda la sesión de la cuenta ni datos
personales.

## Qué se toca del motor compartido con el teléfono

- **Precarga:** toca `MusicService`. Va detrás de una bandera que solo enciende el carro, y el teléfono queda
  igual. Antes de esa tanda se avisa a la sesión del teléfono (la 1.3.1 y la 1.3.2 ya salieron).
- **Life Line:** se le añade a la fila un parámetro de tamaño, que de fábrica vale los 14 sp de hoy. En el
  teléfono no cambia nada visible.
- Todo lo demás vive en `app/src/car/`.

## Tandas (de 10 a 20 min cada una)

| # | Tanda | Termina con |
|---|---|---|
| 1 | Life Line en Sonando y en el panel de Inicio | APK en el emulador |
| 2 | Bitácora, probador de botones y teclas en la ventana | APK en el emulador |
| 3 | Reanudar al encender | **APK para el radio** (unos 9 min de compilación): CG prueba el volante y el encendido en el carro real |
| 4 | Precarga: el motor | APK en el emulador |
| 5 | Precarga: la cola, las voces y el ajuste | APK en el emulador |
| 6 | Cuenta: la página de Google, guardar la sesión y reiniciar | APK en el emulador |
| 7 | Cuenta: sincronizar, saludo con el nombre y cerrar sesión | APK en el emulador |
| 8 | Avisos del fabricante, según lo que CG vea en su radio | Candidata 0.3.0 para el radio |

## Pruebas internas (fase 4, un día antes)

La lista fija, adaptada al carro (Cast y los widgets no existen en el carro):
- reproducir, adelantar y la cola;
- buscar;
- descargas;
- instalar encima de la 0.2.0 sin perder la cola, los Me gusta ni los ajustes;
- los 33 tests de `:playback:testFossDebugUnitTest` (la precarga toca el motor);
- `apksigner verify`.

Además, las pruebas propias de cada ficha.

## Preguntas para CG

1. **¿Qué radio es?** La marca o lo que diga *Ajustes › Información* (CPU tipo UIS7862, PX5, MTK8227…).
   ¿Al encender el carro tarda poco (2 a 5 s, porque estaba dormido) o mucho (20 s o más, porque arranca de
   cero)?
2. **¿Los botones del volante ya cambian de canción con la 0.2.0?** Sí, no o no sé.
3. **Precarga de fábrica:** «la siguiente» (recomendado, unos 4 MB de datos más por canción) o «las dos
   siguientes».
4. **Cuenta:** si el navegador interno del radio es muy viejo y Google no deja entrar, ¿apruebas el plan B
   «entrar con el teléfono por QR»? Necesita una actualización de la app del teléfono y adelanta parte de la
   idea 23.

Si CG aprueba sin contestar alguna, se va con lo recomendado y la lista queda congelada.

### Respuestas (2026-10-10)

CG aprobó todo. Lista **congelada**.

1. El radio es un chino genérico sin marca, de los que se identifican como **ALPS**. «alps» es el nombre de la
   compilación de referencia de MediaTek, así que casi seguro tiene un chip MediaTek o Autochips (AC8227L,
   MT8163…). Para la ficha de reanudar, entre los avisos de ACC se prueban primero los de esa familia
   (`autochips.intent.action.QB_POWERON` y `QB_POWEROFF`). La bitácora confirma si duerme o arranca.
2. Sin respuesta: se averigua con el probador de botones (tanda 3).
3. Sin respuesta: precarga **«la siguiente»** de fábrica (lo recomendado).
4. **Plan B aprobado:** si Google no deja entrar desde el radio, se hace «entrar con el teléfono por QR»,
   coordinado con la sesión del teléfono.
