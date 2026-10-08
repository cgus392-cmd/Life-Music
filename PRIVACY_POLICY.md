# Política de privacidad

**Life Music no recoge datos personales por su cuenta.** No hay analítica, ni
informes de fallos, ni publicidad, ni cuenta con nosotros. A un servidor de CG
LABS solo llega algo **cuando usted lo pide con un botón**: calificar la app o
prerregistrarse en un concurso (ver «Comunidad», más abajo).

> **In English** — Life Music collects nothing on its own: no analytics, no crash
> reporting, no advertising and no account with us. Everything the app stores
> lives on your device. Something reaches a CG LABS server only when you ask for
> it with a button: rating the app or pre-registering for a contest (see
> «Comunidad» below). Sections below also list the third-party services the app
> talks to and why.

Esta no es una declaración de intenciones: es una consecuencia de cómo está
construida la aplicación, y se puede comprobar en el código fuente.

## Lo que se queda en su dispositivo

Todo. En concreto:

| Dato | Dónde vive |
|---|---|
| Historial de escucha y estadísticas | base de datos local (Room) |
| Listas, favoritos y descargas | almacenamiento de la aplicación |
| Letras descargadas | caché local |
| Ajustes y preferencias | DataStore local |
| Sesión de YouTube, si inicia sesión | almacenamiento de la aplicación |

Desinstalar la aplicación borra todo eso. No queda copia en ninguna parte, porque
nunca salió del teléfono.

## Lo que se eliminó del proyecto de origen

Life Music parte de Echo Music, que **sí** enviaba datos:

- **Firebase Analytics y Crashlytics.** El fichero `google-services.json`
  apuntaba al proyecto de Firebase de Echo, de modo que la analítica de uso y las
  trazas de fallo iban a infraestructura de terceros. **Se retiró.** Sin ese
  fichero, Gradle ni siquiera aplica los complementos.

Esa decisión es lo que permite escribir esta política en una línea.

## Con quién habla la aplicación

Life Music es un cliente: para funcionar tiene que pedir datos a servicios
ajenos. Cuando lo hace, esos servicios ven su dirección IP y la petición, como
ocurre con cualquier aplicación conectada.

| Servicio | Para qué | Cuándo |
|---|---|---|
| YouTube / YouTube Music | catálogo y reproducción | siempre |
| LRCLIB, Kugou, YouLyPlus, PaxSenix, SimpMusic, Unison | letras | al reproducir, si están activados |
| Apple Music, `canvas.echomusic.fun` | vídeos de fondo (*canvas*) | si activa la función |
| `echomusic-listen-together.onrender.com` | salas de Escuchar juntos | si usa la función |
| GitHub | comprobar si hay versión nueva | **desactivado por defecto** |
| Discord | presencia enriquecida | si la activa usted |
| Servicio de traducción, si lo configura | traducir letras | solo con clave propia |
| Proveedor de IA, si lo configura | listas generadas | solo con clave propia |

**Ninguno de estos servicios es nuestro.** Dos de ellos —el de *canvas* y el de
Escuchar juntos— pertenecen al proyecto de origen, y así consta en
`constants/Repo.kt` para que la dependencia esté a la vista y no escondida en
una pantalla cualquiera. Cada uno tiene su propia política de
privacidad y CG LABS no controla qué hacen con la petición.

Si inicia sesión con su cuenta de Google, esa sesión va **directamente** a
YouTube. Life Music no la ve, no la copia y no la envía a ningún otro sitio.

## Reto de la semana (concurso, 13–19 de septiembre de 2026)

La única excepción a «no hay servidores de CG LABS», y es **opcional, temporal
y explícita**. Durante el concurso, y solo si usted pulsa **Participar** y
rellena la inscripción, la aplicación envía a una base de datos de CG LABS
alojada en Supabase:

- sus **minutos de escucha por día** dentro de las fechas del concurso (la
  misma cifra de la pantalla de Estadísticas: solo el total, nunca qué canciones);
- el **apodo** que elija, que es lo único que ven los demás participantes;
- su **nombre y correo**, que solo sirven para contactar al ganador. No se
  muestran a nadie, no se usan para otra cosa y **se borran al terminar el
  concurso**.

Sin inscripción no se envía nada: la aplicación no hace ni una sola petición a
ese servidor. Puede **abandonar el reto** desde su pantalla cuando quiera, y en
ese momento se borra su fila entera —minutos, apodo, nombre y correo—. La
inscripción constituye su autorización previa, expresa e informada para ese
tratamiento (Ley 1581 de 2012, Colombia), limitada a la finalidad y al plazo
descritos. Al terminar el concurso el módulo se desactiva y la tabla se elimina.

## Comunidad (desde la 1.3.1): calificación y prerregistro

Las dos van a un servidor de CG LABS en Cloudflare
(`lifemusic-comunidad.cho--usic.workers.dev`), y **solo cuando usted toca el
botón**. Nada se envía solo.

**Calificación con estrellas.** Si toca **Enviar**, se guardan las estrellas,
el comentario si escribió uno, la versión de la app, el idioma y un número al
azar creado en su teléfono. Ese número sirve para que cada teléfono cuente una
sola vez y pueda cambiar su voto.
- No se guarda nombre, correo ni dirección IP.
- En público solo se muestran el promedio y el total, desde 25 calificaciones.
- Los comentarios solo los lee CG LABS.
- El número al azar es distinto del del prerregistro: no se puede saber quién
  puso qué estrellas.

**Prerregistro del nuevo concurso.** Si toca **Prerregistrarme**, se guardan su
**nombre**, su **correo**, que confirmó ser mayor de edad, la versión de la app,
un número al azar de su teléfono y la huella (hash) de un secreto que solo
conoce su teléfono.
- **No hay ninguna lista pública**: solo se muestra cuántas personas van.
- El nombre y el correo sirven únicamente para avisarle cuando empiece el
  concurso y para contactar a quien gane. No se venden ni se comparten.
- **Se borran al terminar el concurso.**
- Con **Salir del prerregistro** se borran del servidor al momento.
- Prerregistrarse constituye su autorización previa, expresa e informada para
  ese tratamiento (Ley 1581 de 2012, Colombia), limitada a esa finalidad y a
  ese plazo. Bases completas: <https://lifemusic.pages.dev/concurso/bases>.

**Life Music TV en el navegador.** Para enlazar un TV con código, el teléfono y
el TV se conectan a un relevo de CG LABS en Cloudflare
(`lifemusic-enlace.cho--usic.workers.dev`) que pasa sus mensajes de uno a otro.
El relevo **no guarda nada**: ni canciones ni códigos. El audio va del TV a
YouTube directamente, sin pasar por él.

## Modo de ahorro de datos

Activarlo reduce las peticiones a terceros: entre otras cosas, deja de
descargarse la letra automáticamente al cambiar de canción.

## Permisos

La aplicación pide lo mínimo: red, notificaciones para el reproductor, y
almacenamiento cuando descarga música. No pide contactos, ni ubicación, ni
cámara, ni micrófono.

El **modo karaoke no graba nada**. Procesa el audio que ya está sonando; no
enciende el micrófono.

## Menores

El proyecto no está dirigido a menores de 13 años. Los concursos y su
prerregistro son solo para mayores de edad, y lo piden confirmar antes de enviar
nada.

## Cambios

Si esta política cambia, quedará reflejado en el
[registro de cambios](CHANGELOG.md) y en el historial de este fichero.

## Contacto

**cgus392@gmail.com**

---

*Última revisión: 7 de octubre de 2026 · Life Music 1.3.1*
