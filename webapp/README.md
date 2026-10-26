# Life Music Web

Reproductor web de Life Music, al estilo del de Spotify, para computador, iPhone y Android. El plan completo está en
[`docs/versiones/web-1.0/plan.md`](../docs/versiones/web-1.0/plan.md).

**Estado:** 0.2, con el diseño «El trazo» que CG aprobó el 24-10-2026. La maqueta y su LEEME están en
`Brand/maquetas/2026-10-24-web-app/maqueta/`. Sin publicar.

Lo que trae:
- **Inicio:** las filas de YouTube Music del país, más «Seguir escuchando».
- **Buscar:** por tipo (todo, canciones, artistas, álbumes y listas).
- **Páginas:** Álbum, Artista y Lista.
- **Reproductor:** «Ahora suena» con los fondos Apple Music y Live Mesh, la cola con radio y el menú de cada canción.
- **Biblioteca:** Me gusta, Mis listas e Historial.
- **Entrar a la app:** Ajustes, Acerca de, la bienvenida con el aviso legal, la guía para instalarla en el iPhone,
  la hoja del anuncio y que se instale como app.

## Cómo suena

El navegador no puede pedirle la canción a YouTube como lo hace la app Android: YouTube contesta 403 a las
peticiones que llegan desde otra web. Por eso suena el **reproductor oficial de YouTube, escondido** fuera de la
pantalla, y lo que se ve es Life Music: la carátula, el título y nuestros controles. Nunca se muestra un video.

Esto va contra las reglas de YouTube para desarrolladores (III.I.9). CG lo decidió el 23-10-2026 sabiéndolo, con
tres resguardos:

1. **Anuncios a la vista.** Si suena un anuncio, el reproductor de YouTube aparece para poder saltarlo.
2. **Interruptor de emergencia.** Con `"reproductorVisible": true` en `public/ajustes-remotos.json` y una nueva
   publicación, el reproductor queda visible siempre.
3. **Perfil bajo.** Sin anuncios nuestros ni cobros, y con el aviso legal y los créditos.

El audio nunca pasa por nuestro servidor.

## Carpetas

- `src/reproductor/`: el motor escondido (`youtube.ts`), la cola y la radio (`tienda.ts`), Media Session
  (`sesion.ts`) y los atajos de teclado (`atajos.ts`).
- `src/pantallas/` y `src/componentes/`: las pantallas y las piezas del diseño, como el vidrio, la marca con su
  nota y la barra del tiempo.
- `src/datos/`: lo que se pide a las funciones (`api.ts`), la biblioteca y los ajustes de cada persona (que se
  guardan en el navegador) y el color de la canción (`color.ts`).
- `functions/api/` y `lib/yt.js`: las funciones de Cloudflare Pages que le preguntan a YouTube Music con el cliente
  WEB_REMIX. Son inicio, buscar, sugerencias, radio, álbum, artista y lista. Guardan las respuestas en la caché de
  Cloudflare por región, y solo viajan textos.
- `public/`: el ícono y la marca, el manifiesto de la PWA, el service worker y `ajustes-remotos.json`.

**Ojo con las carátulas:** el servidor de YouTube Music (googleusercontent) a veces responde 429. Por eso cada
carátula va sin Referer y tiene de respaldo la imagen del video en i.ytimg.com. El color de la canción también se
saca de i.ytimg.com, porque googleusercontent no deja leer los píxeles desde el navegador.

**Stack:** React 19, TypeScript y Vite, que es el stack web de CG LABS. Las letras son Geist, alojadas en el sitio.

## Probar en local

```bash
npm install
npm run build
```

`wrangler pages dev` falla en este PC: workerd no puede crear sus carpetas («Acceso denegado»). Mientras se
resuelve, la web se prueba con un servidor pequeño en Node que sirve `dist/` y corre las mismas funciones.

## Publicar

Solo con el «publica» de CG:

```bash
npx wrangler pages deploy dist --project-name lifemusic-web --branch main
```

## Límites conocidos

- En iPhone la música se pausa al bloquear la pantalla. Es una regla de Apple y no se puede evitar.
- Las canciones que no se dejan reproducir fuera de YouTube se saltan solas.
- YouTube puede meter anuncios. No se bloquean.

GPL-3.0, heredada de Echo Music. Life Music by CG · CG LABS.
