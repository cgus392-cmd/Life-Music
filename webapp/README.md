# Life Music Web

Versión web de Life Music para iPhone, iPad y computador. Se instala desde Safari
con **Compartir → Agregar a inicio**.

- `public/`: la página (HTML, CSS y JS sin compilar), el manifiesto y el service worker.
- `functions/api/`: funciones de Cloudflare Pages que le preguntan a YouTube Music
  (búsqueda, sugerencias y radio) con el mismo cliente WEB_REMIX de la app.
- `lib/yt.js`: el puente con YouTube Music y la lectura de sus respuestas.

El audio **no** pasa por nuestro servidor: suena en el reproductor oficial de
YouTube incrustado y visible, como piden sus reglas (mínimo 200 × 200 px).

## Límites conocidos

- En iPhone la música se pausa al bloquear la pantalla o salir de la app (regla de
  Apple para apps web y para los videos incrustados).
- Algunas canciones no se pueden reproducir fuera de YouTube (errores 101 y 150):
  se saltan solas.
- Puede salir publicidad de YouTube. No hay descargas ni ecualizador.

## Probar y publicar

```bash
npx wrangler pages dev public --port 8788
npx wrangler pages deploy public --project-name lifemusic-web --branch main
```
