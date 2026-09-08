# Política de privacidad

**Life Music no recoge, almacena ni transmite datos personales a ningún servidor
de CG LABS.** No hay servidores de CG LABS.

> **In English** — Life Music collects nothing. There is no CG LABS server, no
> analytics, no crash reporting, no advertising and no account with us. Everything
> the app stores lives on your device. Sections below list the third-party
> services the app talks to and why.

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

El proyecto no está dirigido a menores de 13 años y no recoge datos de nadie, de
ninguna edad.

## Cambios

Si esta política cambia, quedará reflejado en el
[registro de cambios](CHANGELOG.md) y en el historial de este fichero.

## Contacto

**cgus392@gmail.com**

---

*Última revisión: 7 de septiembre de 2026 · Life Music 1.1.0*
