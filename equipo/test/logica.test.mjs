// Pruebas de la logica pura de la cabina: `node --test equipo/test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ALFABETO, codigoAlAzar, normalizarCodigo, formatoPase, estadoJornada, msDeColombia, enColombia,
  diaDelCargo, puedeMoverIdea, puede, actaDe, firmaDibujadaSvg, firmaEscritaSvg, taparDocumento,
  semanaIso, fechaValida, hora12, textoDias, aparatoDe, texto,
} from "../src/logica.js";

const persona = (extra = {}) => ({
  rol: "supervisor", estado: "activa", numero: 2, desde: "2026-10-13", hasta: "2026-12-11",
  dias: "1,2,3,4,5", entrada: "08:00", salida: "17:00", ...extra,
});
const sinFestivos = new Set();

test("los codigos solo usan el alfabeto y no tienen sesgo de modulo", () => {
  for (let i = 0; i < 200; i++) {
    const c = codigoAlAzar(8);
    assert.equal(c.length, 8);
    assert.ok([...c].every((x) => ALFABETO.includes(x)));
  }
  // un azar que siempre da 255 (por encima del limite) y luego 0 no se cuela como letra sesgada
  let llamada = 0;
  const azar = (b) => { b.fill(llamada++ === 0 ? 255 : 0); return b; };
  assert.equal(codigoAlAzar(3, azar), "222");
});

test("normalizar acepta con o sin LM, guiones y minusculas; rechaza letras confusas", () => {
  assert.equal(normalizarCodigo("lm-7kq2 xp9m"), "7KQ2XP9M");
  assert.equal(normalizarCodigo("7KQ2XP9M"), "7KQ2XP9M");
  assert.equal(normalizarCodigo("abc-def"), "ABCDEF");
  assert.equal(normalizarCodigo("O0I1L"), "");
  assert.equal(formatoPase("7KQ2XP9M"), "LM-7KQ2-XP9M");
});

test("jornada: abierta en horario de un dia laborable, cerrada fuera", () => {
  const p = persona();
  const miercoles10am = msDeColombia("2026-10-14", 10 * 60);
  assert.deepEqual(estadoJornada(p, sinFestivos, miercoles10am), { abierta: true, motivo: "jornada", hasta: msDeColombia("2026-10-14", 17 * 60) });
  const miercoles6pm = msDeColombia("2026-10-14", 18 * 60);
  const e = estadoJornada(p, sinFestivos, miercoles6pm);
  assert.equal(e.abierta, false);
  assert.equal(e.motivo, "fuera-de-horario");
  assert.equal(e.siguiente, msDeColombia("2026-10-15", 8 * 60));
});

test("jornada: el sabado es dia libre y la siguiente es el lunes", () => {
  const e = estadoJornada(persona(), sinFestivos, msDeColombia("2026-10-17", 11 * 60));
  assert.equal(e.motivo, "dia-libre");
  assert.equal(e.siguiente, msDeColombia("2026-10-19", 8 * 60));
});

test("jornada: los festivos no cuentan (12 de octubre de 2026 es lunes festivo)", () => {
  const e = estadoJornada(persona({ desde: "2026-10-01" }), new Set(["2026-10-12"]), msDeColombia("2026-10-12", 9 * 60));
  assert.equal(e.motivo, "festivo");
});

test("jornada: antes y despues de la vigencia, en pausa, y el dueño siempre puede", () => {
  assert.equal(estadoJornada(persona(), sinFestivos, msDeColombia("2026-10-12", 9 * 60)).motivo, "antes-de-vigencia");
  const despues = estadoJornada(persona(), sinFestivos, msDeColombia("2026-12-14", 9 * 60));
  assert.equal(despues.motivo, "vigencia-terminada");
  assert.equal(despues.siguiente, null);
  assert.equal(estadoJornada(persona({ estado: "pausa" }), sinFestivos, msDeColombia("2026-10-14", 9 * 60)).motivo, "pausa");
  assert.equal(estadoJornada(persona({ rol: "duenio" }), sinFestivos, 0).abierta, true);
});

test("hora de Colombia: medianoche UTC son las 7 p. m. del dia anterior", () => {
  assert.deepEqual(enColombia(Date.UTC(2026, 9, 15, 0, 0)), { fecha: "2026-10-14", dia: 3, minuto: 19 * 60 });
});

test("dia del cargo", () => {
  assert.deepEqual(diaDelCargo(persona(), msDeColombia("2026-10-14", 9 * 60)), { dia: 2, total: 60 });
});

test("solo el dueño da luz verde y lanza; el supervisor avanza lo demas; operaciones no mueve", () => {
  assert.equal(puedeMoverIdea("supervisor", 1, 2), true);
  assert.equal(puedeMoverIdea("supervisor", 2, 3), false);
  assert.equal(puedeMoverIdea("supervisor", 3, 4), true);
  assert.equal(puedeMoverIdea("supervisor", 5, 6), false);
  assert.equal(puedeMoverIdea("duenio", 2, 3), true);
  assert.equal(puedeMoverIdea("duenio", 5, 6), true);
  assert.equal(puedeMoverIdea("operaciones", 1, 2), false);
  assert.equal(puede("operaciones", "revision"), true);
  assert.equal(puede("supervisor", "invitar"), false);
});

test("el acta trae folio, terminos y las 8 clausulas", () => {
  const a = actaDe(persona(), 1, 2026);
  assert.equal(a.folio, "LM-EQ-2026-002");
  assert.equal(a.clausulas.length, 8);
  assert.equal(a.terminos[1][1], "Del 13 de octubre de 2026 al 11 de diciembre de 2026 (60 días)");
  assert.equal(a.terminos[2][1], "Lunes a viernes, de 8:00 a. m. a 5:00 p. m.");
});

test("la firma dibujada se arma en el servidor y rechaza lo raro", () => {
  const svg = firmaDibujadaSvg([[10, 20, 30, 40]], 400, 160);
  assert.match(svg, /^<svg[^>]*viewBox="0 0 400 160"><path [^>]*d="M10 20L30 40"\/><\/svg>$/);
  assert.equal(firmaDibujadaSvg([], 400, 160), null);
  assert.equal(firmaDibujadaSvg([["<script>", 1]], 400, 160), null);
  assert.equal(firmaDibujadaSvg([[1, 2, 3]], 400, 160), null);
  assert.equal(firmaDibujadaSvg([[1, 2]], 10, 10), null);
});

test("la firma escrita escapa el nombre", () => {
  assert.match(firmaEscritaSvg("Ana <b>&"), /Ana &lt;b&gt;&amp;<\/text>/);
});

test("utilidades", () => {
  assert.equal(taparDocumento("1020456821"), "10•••••821");
  assert.equal(semanaIso("2026-10-14"), "2026-W42");
  assert.equal(fechaValida("2026-02-30"), false);
  assert.equal(fechaValida("2026-10-14"), true);
  assert.equal(hora12("17:00"), "5:00 p. m.");
  assert.equal(hora12("00:30"), "12:30 a. m.");
  assert.equal(textoDias({ dias: "1,3,5" }), "Lunes, miércoles, viernes");
  assert.equal(aparatoDe("Mozilla/5.0 (Windows NT 10.0) Chrome/150 Safari/537"), "Chrome en Windows");
  assert.equal(texto("  hola  ", 10), "hola");
  assert.equal(texto("a\nb", 10), null);
  assert.equal(texto("a\nb", 10, { multilinea: true }), "a\nb");
  assert.equal(texto("x".repeat(11), 10), null);
});
