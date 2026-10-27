import { useEffect, useState } from "react";
import { Route, Routes, useLocation } from "react-router";
import { Lateral } from "./componentes/Lateral";
import { Pestanas } from "./componentes/Pestanas";
import { MiniReproductor } from "./componentes/MiniReproductor";
import { BarraReproductor } from "./componentes/BarraReproductor";
import { PanelAhora } from "./componentes/PanelAhora";
import { AhoraSuena } from "./componentes/AhoraSuena";
import { MenuCancion } from "./componentes/MenuCancion";
import { AnuncioDespues } from "./componentes/AnuncioDespues";
import { Bienvenida, GuiaInstalar } from "./componentes/Entrada";
import { GestoAtras } from "./componentes/GestoAtras";
import { Inicio } from "./pantallas/Inicio";
import { Buscar } from "./pantallas/Buscar";
import { Coleccion } from "./pantallas/Coleccion";
import { Artista } from "./pantallas/Artista";
import { Biblioteca, ListaGuardada, ListaPropia, MisListas } from "./pantallas/Biblioteca";
import { Acerca, Ajustes } from "./pantallas/Ajustes";
import { aplicarTema, usarAjustes } from "./datos/ajustes";
import { demo } from "./demo";

export default function App() {
  const [ahoraAbierta, setAhoraAbierta] = useState(demo === "ahora");
  const [panel, setPanel] = useState<"ahora" | "cola">("ahora");
  const tema = usarAjustes((s) => s.tema);
  const { pathname } = useLocation();

  useEffect(() => aplicarTema(tema), [tema]);

  // Cada pantalla nueva empieza arriba, como en una app.
  useEffect(() => {
    document.querySelector(".centro")?.scrollTo(0, 0);
    scrollTo(0, 0);
  }, [pathname]);

  // Con la página desplazada aparece el desvanecido debajo del reloj (shell.css, .app::before).
  useEffect(() => {
    const raiz = document.documentElement;
    const mirar = () => raiz.classList.toggle("desplazado", scrollY > 4);
    mirar();
    addEventListener("scroll", mirar, { passive: true });
    return () => removeEventListener("scroll", mirar);
  }, []);

  return (
    <div className="app">
      <Lateral />
      <main className="centro">
        <Routes>
          <Route path="/" element={<Inicio />} />
          <Route path="/buscar" element={<Buscar />} />
          <Route path="/album/:id" element={<Coleccion tipo="album" />} />
          <Route path="/lista/:id" element={<Coleccion tipo="lista" />} />
          <Route path="/artista/:id" element={<Artista />} />
          <Route path="/biblioteca" element={<Biblioteca />} />
          <Route path="/biblioteca/megusta" element={<ListaPropia cual="megusta" />} />
          <Route path="/biblioteca/historial" element={<ListaPropia cual="historial" />} />
          <Route path="/biblioteca/listas" element={<MisListas />} />
          <Route path="/biblioteca/lista/:id" element={<ListaGuardada />} />
          <Route path="/ajustes" element={<Ajustes />} />
          <Route path="/acerca" element={<Acerca />} />
          <Route path="*" element={<Inicio />} />
        </Routes>
      </main>
      <PanelAhora modo={panel} />
      <MiniReproductor alAbrir={() => setAhoraAbierta(true)} />
      <Pestanas />
      <BarraReproductor
        alAbrir={() => setAhoraAbierta(true)}
        alCola={() => setPanel((p) => (p === "cola" ? "ahora" : "cola"))}
        colaAbierta={panel === "cola"}
      />
      <AhoraSuena abierta={ahoraAbierta} alCerrar={() => setAhoraAbierta(false)} />
      <MenuCancion />
      <AnuncioDespues />
      <GuiaInstalar />
      <Bienvenida />
      <GestoAtras />
    </div>
  );
}
