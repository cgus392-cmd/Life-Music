import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import "@fontsource-variable/geist";
import "./estilo/base.css";
import "./estilo/shell.css";
import "./estilo/ahora.css";
import "./estilo/pantallas.css";
import "./estilo/vistas.css";
import "./estilo/entrada.css";
import App from "./App";
import { arrancar } from "./reproductor/tienda";
import { activarPantallaEncendida } from "./reproductor/pantalla";

arrancar();
activarPantallaEncendida();

createRoot(document.getElementById("raiz")!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);

// Se quita la imagen de arranque cuando la app ya pintó.
requestAnimationFrame(() => {
  const arranque = document.getElementById("arranque");
  if (!arranque) return;
  arranque.style.opacity = "0";
  setTimeout(() => arranque.remove(), 300);
});

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}
