# Ficha técnica · Life Music TV: enlace seguro, estado del sistema y pantalla nueva

**Versión:** 1.3.2 · **Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG
**Origen:** las pruebas de CG con la 1.3.1, 2026-10-08.

## 1. Qué encontró CG

| # | Qué pasa | Por qué pasa (revisado en el código) |
|---|---|---|
| 1 | El botón de transmitir no sale para un TV con navegador ya enlazado, y toca volver a enlazar. | `CastEnBarra` solo mira los Chromecast y los DLNA de la red; ignora los TV recordados. |
| 2 | La línea de tiempo se sincroniza solo la segunda vez que se conecta. | **Falta saber exactamente qué línea es.** Ya está grabando el registro `LifeMusicCast` del S25. |
| 3 | En el TV, «Enlazar otro teléfono» muestra un código nuevo **pero la música sigue sonando**, como si nada. | `tv.js` abre una sala de código nueva **sin cerrar la sesión vieja**. El teléfono viejo sigue mandando y nadie le avisa. |
| 4 | No se sabe si el otro lado sigue vivo. | El teléfono solo **anota** en el registro cuando el TV sale (`alTvPresente`). El TV sí lo nota, pero sigue sonando sin decir nada. No hay latido entre ellos. |
| 5 | Conectar y desconectar es tan fácil que no hay verificación. | La sala del token deja entrar a **varios teléfonos a la vez**, y cualquiera con el token manda. |
| 6 | El código no vence de verdad. | Dura 10 minutos, pero solo porque el TV lo cambia; el relevo no lo hace cumplir. |
| 7 | Los botones del TV son texto («Pantalla completa», «Enlazar otro teléfono») y ocupan espacio. | Así se hicieron. |
| 8 | La pantalla de enlace se siente simple y genérica. | Así se hizo. |
| 9 | Falta un estado del sistema que diga «el servidor está activo, sin problemas». | No existe. |

## 2. Qué seguridad tiene hoy (respuesta a CG)

- **Cifrado en tránsito: sí.** Todo va por **HTTPS y WebSocket seguro (wss, TLS)** hasta Cloudflare:
  teléfono↔relevo, TV↔relevo y la página. Nadie en tu wifi ni en el camino puede leer los mensajes.
- **Cifrado de extremo a extremo: no.** El relevo (nuestro código en Cloudflare) ve los mensajes en claro mientras
  los pasa, pero **no guarda nada**.
- **El código:** 6 caracteres de 31 posibles, unos 887 millones de combinaciones. El TV lo genera con el azar
  criptográfico del navegador (`crypto.getRandomValues`).
- **La llave del enlace (token):** 128 bits al azar (`crypto.randomUUID`), imposible de adivinar. Se guarda en el
  TV (`localStorage`) y en el teléfono (DataStore). **Hoy no vence nunca.**
- **Qué pasa por el relevo:**
  - el título, el artista y la carátula de la canción;
  - la letra, los colores y el tempo;
  - el **enlace del audio** de YouTube (firmado, atado a la IP pública de tu casa y vence en horas);
  - el nombre del teléfono y el del TV.
  - Ni contraseñas ni la sesión de Google: eso nunca sale del teléfono.
- **Protecciones que ya hay:** solo se aceptan páginas de `lifemusic.pages.dev`, los mensajes tienen un máximo de
  512 KB y en la sala del código entra un solo TV.

## 3. Qué se propone

### A. Código que vence a los 5 minutos, con cuenta regresiva
- En el TV, el código lleva un **anillo o barra que se va vaciando** con el tiempo restante («4:12»). A los 5 minutos
  sale otro solo.
- **El relevo también lo hace cumplir:** la sala del código anota cuándo se creó, y pasados 5 minutos rechaza al
  teléfono con `codigo-vencido` y cierra la sala. En el teléfono sale «Ese código venció; usa el que muestra el TV
  ahora».
- El azar se toma sin el sesgo del módulo que tiene hoy.

### B. Una sola sesión a la vez, con verificación
- **La sala del token admite un solo teléfono.** Si entra otro con el mismo token, se rechaza con `ocupado`.
- **«Enlazar otro teléfono» en el TV pide confirmación:** «Esto desconecta a *Galaxy S25 de Camilo*. ¿Seguir?»,
  con OK o Volver en el control. Al confirmar:
  1. el TV **para el audio** y le avisa al teléfono con `desenlazado`;
  2. el teléfono sale de la transmisión y dice «El TV se desenlazó»;
  3. el TV borra el token y recién ahí muestra el código nuevo.
- **Ningún código nuevo mientras haya una sesión viva sin confirmar.** Se acaba lo de «sigue sonando como si nada».

### C. Latido de la sesión (saber si el otro sigue ahí)
- El teléfono y el TV se mandan un `latido` cada 10 segundos por el relevo.
- **El TV, si pasan 30 segundos sin nada del teléfono,** pone «Se perdió la conexión con tu teléfono». Termina la
  canción que suena y no sigue con otra. Si el teléfono vuelve, sigue solo.
- **El teléfono, si el relevo dice que el TV salió o pasan 30 segundos sin latido,** espera 15 segundos de gracia por
  si es una recarga. Después sale de la transmisión con «Se perdió la conexión con el TV» y queda en pausa, igual que
  con Cast.

### D. El enlace recordado vence
- Un TV recordado que **no se usa en 30 días** se olvida solo, en el teléfono y en el TV.
- En el TV, al tocar «Enlazar otro teléfono» y confirmar, se olvida el anterior.
- En el teléfono sigue la X para olvidarlo, y ahora el TV también se entera.

### E. Límite de intentos para adivinar códigos
- El relevo cuenta los intentos fallidos de código por dirección: más de 20 en 10 minutos y espera.
- Se usa la limitación de Workers **solo si es gratis en tu plan**; lo compruebo en la tanda 1. Si no lo es, se
  omite: con códigos de 5 minutos y el tope gratis de 100.000 peticiones al día, adivinar uno es prácticamente
  imposible.

### F. Estado del sistema, integrado
- **Cada servidor tiene su `/salud`:** el relevo del TV responde y la comunidad revisa también su base D1. Contestan
  `{"estado":"ok","version":…}`.
- **La página `lifemusic.pages.dev/estado`** muestra un semáforo por servicio (TV con navegador, comunidad,
  descargas y web) con el tiempo de respuesta. La consulta se hace en vivo desde el navegador, sin pagar ningún
  monitor.
- **En la pantalla de enlace del TV** sale un indicador «● Servidor en línea». Si falla, cambia a «Servidor sin
  respuesta · reintentando».
- **En la hoja de transmitir del teléfono**, en la sección del TV con navegador, sale el mismo punto con «Servidor en
  línea».

### G. Botón de transmitir híbrido
- **Se ve siempre** si tienes algún TV con navegador recordado o estás transmitiendo. Si no, se ve cuando hay un
  Chromecast o un DLNA en la red, como antes.
- **En la hoja**, cada TV recordado dice si **está en línea**, es decir, si tiene la página `/tv` abierta. Para
  saberlo, el relevo tiene la ruta `/presencia`, que se consulta solo al abrir la hoja, nunca en bucle.

### H. Botones con íconos en el TV
- «Pantalla completa» pasa a ser el ícono ⤢ y «Enlazar otro teléfono», un ícono de teléfono con +.
- Con el control, el ícono que tiene el foco muestra su nombre en una etiqueta pequeña. Así no ocupan espacio y se
  siguen entendiendo, también con lector de pantalla.

### I. Pantalla de enlace nueva
- **Antes de programarla, CG ve una maqueta y la aprueba.** Dirección propuesta, con la marca de la landing:
  - fondo vivo con la aurora verde menta;
  - el código en fichas grandes, una por carácter;
  - el QR en una tarjeta de cristal con el anillo de vigencia alrededor;
  - los tres pasos con íconos;
  - el estado del servidor;
  - la compatibilidad del navegador, discreta.
- En vertical, todo apilado, como el arreglo de la 1.3.1.

### J. La línea de tiempo (bug 2)
- Se arregla cuando CG diga qué línea es y con el registro de la prueba.
- Sospecha: en el primer enlace el teléfono manda la canción y su ambiente mientras el TV todavía pasa de la sala del
  código a la del token, y el relevo los descarta porque no hay nadie al otro lado. Con B y C, el teléfono espera a
  que el TV esté presente antes de mandar.

## 4. Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| Teléfonos con la 1.3.1, sin latido ni mensajes nuevos, contra un TV con la página nueva | El TV no exige latido a un teléfono que nunca lo mandó (lo nota por su `listo`), y la sala de un solo teléfono no rompe a nadie |
| Una recarga de la página del TV corta la sesión | 15 segundos de gracia antes de desconectar |
| Costo de Cloudflare | Latido cada 10 s por sesión activa: unos 8.600 mensajes al día por TV encendido todo el día. Los mensajes de WebSocket cuentan 20 a 1, así que son unas 430 peticiones. Se revisa contra el tope gratis, y si aprieta, el latido pasa a 20 s |
| Desplegar el relevo y la web | Con orden explícita de CG |

## 5. Tandas

1. **Relevo, unos 25 min:** código de 5 minutos, sala de un solo teléfono, `desenlazado`, `/presencia`, `/salud` y
   el límite de intentos si es gratis. Lo pruebo con el script del relevo.
2. **TV, unos 25 min:** cuenta regresiva, confirmación de «enlazar otro», latido, íconos y el indicador del servidor.
3. **Teléfono, unos 25 min:** latido, aviso al perder el TV, `desenlazado`, `codigo-vencido`, botón híbrido, «en
   línea» en la hoja y vencimiento a 30 días. Termina con un APK instalado.
4. **Pantalla de enlace nueva,** tras aprobar la maqueta, más la página `/estado`.
5. **Bug 2,** con el registro.

## 6. Pruebas

1. Enlazar por QR y por código, la primera vez y la segunda.
2. Esperar más de 5 minutos con un código y comprobar que el relevo lo rechaza.
3. Con música sonando, tocar «Enlazar otro teléfono» en el TV: sale la confirmación. Al aceptar, el audio para y el
   teléfono se entera.
4. Probar un segundo teléfono con el mismo token: lo rechaza.
5. Apagar el wifi del teléfono transmitiendo: a los 30 segundos el TV lo dice.
6. Cerrar la pestaña del TV: a los 15 a 45 segundos el teléfono sale de la transmisión.
7. Recargar la página del TV: la sesión sigue.
8. El botón de transmitir sale con un TV recordado, aunque no haya Chromecast en la red.
9. `/estado` en verde, y en rojo con un servidor caído (se simula en local).
10. Los íconos del TV, navegando con las flechas del control.
