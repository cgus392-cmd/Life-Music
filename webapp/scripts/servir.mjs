// Servidor local de Life Music Web: sirve dist/ (con regreso a index.html para las rutas)
// y corre las funciones de Cloudflare Pages (functions/api/*.js) directo en Node.
// Existe porque `wrangler pages dev` falla en el PC de CG: workerd no puede crear sus carpetas.
// Uso: npm run build && node scripts/servir.mjs . 8788
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { join, extname, normalize } from "node:path";
import { pathToFileURL } from "node:url";

const [webapp, puerto = "8798"] = process.argv.slice(2);
const dist = join(webapp, "dist");
const tipos = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".woff2": "font/woff2",
};

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${puerto}`);
  try {
    if (url.pathname.startsWith("/api/")) {
      const nombre = url.pathname.slice(5).replace(/[^\w-]/g, "");
      const modulo = await import(pathToFileURL(join(webapp, "functions", "api", `${nombre}.js`)).href);
      const request = new Request(url, { headers: req.headers });
      const r = await modulo.onRequestGet({ request });
      res.writeHead(r.status, Object.fromEntries(r.headers));
      res.end(Buffer.from(await r.arrayBuffer()));
      return;
    }
    const ruta = normalize(decodeURIComponent(url.pathname)).replace(/^[/\\]+/, "");
    let archivo = join(dist, ruta || "index.html");
    let datos = await readFile(archivo).catch(() => null);
    if (!datos) {
      archivo = join(dist, "index.html");
      datos = await readFile(archivo);
    }
    res.writeHead(200, { "content-type": tipos[extname(archivo)] || "application/octet-stream" });
    res.end(datos);
  } catch (e) {
    res.writeHead(500, { "content-type": "text/plain; charset=utf-8" }).end(String(e?.stack || e));
  }
}).listen(Number(puerto), () => console.log(`Life Music Web en http://localhost:${puerto}`));
