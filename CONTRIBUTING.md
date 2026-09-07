# Contribuir a Life Music

Gracias por el interés. Antes de nada, conviene ser claro sobre qué es esto:
**Life Music es un proyecto educativo y sin ánimo de lucro**, la práctica de una
persona sobre una base compleja. No es un producto con hoja de ruta ni con
compromisos de soporte.

Eso no cierra la puerta a nadie, pero sí ordena las expectativas: las respuestas
pueden tardar, y una propuesta puede rechazarse simplemente porque se aleja de lo
que el proyecto quiere aprender.

## Antes de escribir código

Abra una incidencia primero. Un cambio pequeño y acordado entra en minutos; uno
grande y por sorpresa puede no entrar nunca, y eso desperdicia su tiempo más que
el mío.

Si el cambio toca el reproductor, la letra o el audio, cuente **en qué
dispositivo lo probó**. Este proyecto se ha equivocado varias veces por dar por
buena una medición hecha en otro sitio.

## Cómo se trabaja aquí

**Commits convencionales**, con los prefijos en inglés porque ese es el estándar
que reconoce cualquier herramienta:

```
feat(player): anade Life Line, la letra viva en el reproductor
fix(lyrics): saca la descarga del hilo principal
refactor(ui): fundido lento en el relevo del titulo
docs: README y CHANGELOG propios
```

**El mensaje explica el porqué, no el qué.** El *qué* ya está en el diff. Lo que
se pierde con el tiempo es la razón: qué se probó, qué medición lo justificó, qué
alternativa se descartó y por qué. Un mensaje de commit de este repositorio
puede ocupar treinta líneas, y está bien.

**Comentarios en español, identificadores en inglés.** Es la convención de CG
LABS: el código se lee en el idioma universal de la programación, y el
razonamiento se escribe en el idioma en el que se piensa.

**Comente lo que no es obvio.** No `// suma uno al contador`, sino por qué esa
constante vale 200 Hz y no 300, o por qué ese `withContext` no se puede quitar.

## Medir antes de afirmar

Si dice que algo es más rápido, más pequeño o suena mejor, **traiga el número**.
Este proyecto ha corregido errores propios precisamente por medir: una ganancia
de compensación de +3 dB que sobraba, un colector que llevaba muerto desde antes
de la bifurcación, una descarga corriendo en el hilo principal. Ninguno se vio a
ojo; todos aparecieron al comprobar.

## Lo que no entra

- **Publicidad, analítica o telemetría.** Life Music no envía nada a ningún
  servidor propio. No es negociable.
- **Nada que rompa la GPL-3.0**: código sin licencia compatible, avisos de
  copyright de terceros eliminados, o dependencias privativas.
- **Renombrar módulos que llevan el nombre de su fuente** (`applecanvas`,
  `lrclib`, `kugou`, `echomusiccanvas`...). Identifican al servicio del que
  obtienen datos, no a esta aplicación.

## Licencia de sus aportaciones

Al enviar código acepta que se publique bajo **GPL-3.0**, igual que el resto del
proyecto. No hay acuerdo de cesión de derechos ni nada parecido.
