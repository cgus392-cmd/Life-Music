# Ficha técnica · Equipo: la sección privada de Life Music

**Versión:** la web va primero; la puerta en la app sale con la 1.3.3.
**Fase:** 2 (planteamiento técnico) · **Estado:** espera la aprobación de CG.
**Origen:** idea de CG, 2026-10-10. Es una sección privada para las personas que contrate para ayudarle con la app.
**Maqueta:** `docs/versiones/1.3.3/maqueta-equipo.html`, versión 2 (la «cabina de producción»), con capturas en
`capturas/`. CG rechazó la versión 1 por genérica.

## 1. Lo que pidió CG y lo que ya decidió

- Una mini sección ejecutiva, privada, en el mismo dominio.
- Se entra con un **código que da CG**. Ese código es la autenticación de la persona y le abre toda su información.
- Muestra el **estado** en que está la persona.
- **Dos vacantes:**
  - **Operaciones.**
  - **Supervisor de ejecución / Planificador**, que es un mismo cargo.
- Ahí se montan las ideas. El supervisor las ejecuta con Claude.
- Se trabaja **de lunes a viernes como máximo**, y todo eso se puede configurar.

**Decisiones de CG (10-10):**

| Pregunta | Decisión |
|---|---|
| Dónde vive | **Web con puerta en la app.** La sección está en `lifemusic.app/equipo`. En la app aparece una entrada «Equipo» después de poner el código. |
| Las claves de las plataformas | **Cuentas propias y un gestor de contraseñas.** Cada persona entra con su propia cuenta a cada plataforma. La sección solo **registra** qué acceso tiene; nunca guarda claves. |
| Las vacantes | **Operaciones** y **Supervisor de ejecución / Planificador.** |
| Fuera de la jornada | **Solo lectura.** Se puede entrar y mirar, pero no mover nada. El servidor lo hace cumplir. |

## 2. Cómo funciona por dentro

```
lifemusic.app/equipo          → la página (Cloudflare Pages, el mismo proyecto de la landing)
lifemusic.app/equipo/api/*    → el Worker «lifemusic-equipo» (una ruta en nuestro dominio)
                                 └─ D1 «lifemusic-equipo» (base aparte de la comunidad)
```

- **Mismo dominio para la página y la API.** La sesión va en una cookie que el JavaScript no puede leer (`HttpOnly`,
  `Secure`, `SameSite=Strict`, prefijo `__Host-`). No hace falta CORS.
- **Base aparte de la comunidad.** Los datos del equipo no se mezclan con los del prerregistro ni con las
  calificaciones.
- **Costo: cero.**
  - Workers gratis: 100 000 peticiones al día.
  - D1 gratis: 5 GB.
  - La ruta en un dominio propio no cobra.
  - Nada de planes pagos.
- **Sin índice:** `noindex`, `Disallow: /equipo` en `robots.txt` y fuera del mapa del sitio.
- **El código de la app es público en GitHub,** así que la seguridad no puede depender de esconderlo: todo se valida en
  el servidor.

## 3. La entrada: un código y nada de contraseñas

1. **CG crea la invitación** desde su panel con el nombre, el rol y el horario. Sale un código como `LM-7KQ2-XP9M`:
   - 8 caracteres de 31 posibles, sin letras que se confundan;
   - **de un solo uso**;
   - **vence en 48 horas**.
2. **La persona entra** en `lifemusic.app/equipo` y escribe el código.
3. **Lee y acepta las reglas del equipo.** Queda la fecha de aceptación.
4. **El servidor le abre una sesión en ese aparato.** La sesión dura 30 días y se renueva sola mientras la use. **El
   código muere en ese momento**, así que si alguien lo ve después, ya no sirve.
5. **Otro aparato:** desde uno donde ya está dentro, toca «Enlazar otro aparato». Sale un código de 6 caracteres
   que **vence en 5 minutos**, con cuenta regresiva, igual que el TV. También puede pedirle a CG un código nuevo.
6. **CG puede cortar a cualquiera con un botón.** Eso cierra todas sus sesiones. También puede cerrar una sola.

**El enlace para compartir (pedido de CG, 10-10).** Al tocar «Imprimir pase», además del código sale un enlace listo
para mandar: `https://lifemusic.app/equipo#pase=LM-7KQ2-XP9M`.
- **El código va después del `#`.** Esa parte el navegador no la manda al servidor, así que no queda en registros
  de Cloudflare. La página la lee, la borra de la barra de direcciones y la manda en una petición `POST`.
- **El panel lo ofrece de varias formas:**
  - «Copiar enlace»;
  - «Compartir…», que abre el menú del teléfono o del PC para mandarlo por WhatsApp, Telegram o correo;
  - un **QR** para escanearlo en persona;
  - un **mensaje ya escrito** con la fecha en que vence.
- **Al abrirlo, el pase sale a nombre de la persona,** con el cargo, la fecha de vencimiento y el código ya puesto. La
  persona confirma «Soy Andrés · validar pase». **Abrir el enlace no gasta el código; validarlo sí.**
- **El enlace es el código,** así que tiene las mismas reglas: sirve una sola vez y vence en 48 horas.
  - El panel recuerda mandarlo por un chat privado y nunca por un grupo.
  - Si se filtra, «Anular este pase» lo invalida y CG imprime otro.
  - En la bitácora de CG queda cuándo y desde qué aparato se abrió y se validó.

**Protecciones:**
- Los códigos y las sesiones se guardan solo como **huella SHA-256**: en la base no hay nada que sirva para entrar.
- **5 intentos fallidos** desde una conexión bloquean esa conexión 15 minutos. Si hay muchos fallos seguidos, a CG le
  sale una alerta en su panel.
- Las acciones solo se aceptan desde `https://lifemusic.app` y en JSON.
- La página no carga scripts de afuera y no se puede meter dentro de otra página.

**CG entra como dueño con una clave de arranque que pone él mismo** en el servidor
(`npx wrangler secret put ARRANQUE`, dentro de `equipo/`).
1. Abre `lifemusic.app/equipo` y toca «Soy Camilo: abrir la cabina por primera vez».
2. Escribe esa clave, su nombre completo, la ciudad y el correo para asuntos de datos. Esos tres datos salen en la
   política como responsable.
3. **Funciona una sola vez:** después la cabina queda a su nombre y esa ruta se cierra. **Claude nunca ve la clave.**

La otra llave del servidor, `PIMIENTA` (la del HMAC de códigos y sesiones), se genera al azar al desplegar y va
directo a `wrangler secret put` sin mostrarse en pantalla.

## 3b. El acta del cargo temporal y la firma (pedido de CG, 10-10)

**Cuándo se firma.** Justo después de validar el código y antes de entrar a la cabina. Sin el acta firmada no se
entra.

**Qué dice el acta.** Es un borrador; CG revisa las cláusulas antes de usarla.
- **Folio** `LM-EQ-2026-002` y la versión del acta.
- **Términos:** cargo, vigencia (de tal fecha a tal fecha, porque el cargo es temporal), jornada y modalidad.
- **Siete cláusulas:**
  1. funciones;
  2. publicación (solo con la orden de CG);
  3. confidencialidad;
  4. datos de usuarios;
  5. accesos;
  6. licencia GPL-3.0 de lo que se aporte;
  7. fin del cargo.
- **Datos de quien firma:** nombre completo y, si CG lo decide, documento. El documento se guarda tapado y solo CG lo
  ve completo.

**La firma.**
- Se dibuja con el dedo o el mouse. Se guarda como **los trazos en SVG**, que pesan poco y no pierden calidad, junto
  con la fecha y la hora, el aparato y la **huella SHA-256** de todo junto: texto del acta, datos y firma.
- **El acta firmada no se puede cambiar.** Si CG cambia una cláusula, sale una versión nueva y se vuelve a firmar.
- **CG contrafirma desde su panel.** Ahí el acta queda completa y los dos pueden bajar una copia en PDF, generada en
  el navegador, sin servicios de afuera.
- **Se guarda mientras dure el cargo y un año más.** Después se borra (esto lo decide CG).

**Qué valor tiene.**
- En Colombia esto es una **firma electrónica** (Ley 527 de 1999 y Decreto 2364 de 2012). Vale entre las partes si
  el método es confiable y las dos lo aceptan.
- Una «firma digital» en sentido estricto es la que lleva un certificado de una entidad como Certicámara, y eso
  cuesta.
- El acta sirve como constancia de que la persona aceptó el cargo y sus reglas. **Si se quiere un contrato laboral
  formal, que lo revise un abogado o un contador.**

## 3c. Dirección visual: la cabina de producción del sello

La versión 1 parecía una plantilla. La versión 2 toma todo del mundo de un estudio de grabación:
- **La entrada** es un **pase de acceso total (AAA)**, como los de backstage, con franja holográfica, número de pase
  y código de barras. El código se escribe en el mismo pase.
- **El acta** es un documento en papel, con folio, cláusulas numeradas y la firma en tinta azul. Al firmar le cae un
  sello de «FIRMADO».
- **La jornada** se ve como la luz **AL AIRE** de los estudios. Encendida es jornada; apagada es «fuera del aire» y
  solo lectura.
- **Arriba va la hoja de llamado del día:** fecha grande, hora de llamado y de cierre, y en qué día del cargo
  temporal va la persona.
- **El tablero de ideas es una sesión de grabación.** Cada idea es una pista y su «clip» avanza por las fases. Las
  puertas que solo abre CG, «Luz verde» y «Lanzar», van rayadas en rojo. Un medidor de 5 luces cuenta las funciones
  de la versión.
- **Los accesos son conexiones** con indicador de jack: conectado o pedido.
- **Operaciones** tiene medidores por servicio, una bitácora con hora y un contador de cinta para el concurso.
- **Tipografía:** Archivo (ancha y pesada para títulos, angosta para rótulos), Instrument Sans para leer e IBM Plex
  Mono para códigos y horas.
- **Tema oscuro (consola de grafito) y claro (aluminio).**

**En la app (1.3.3):**
1. En *Acerca de*, si se toca 7 veces la versión, sale «Código de equipo».
2. Al ponerlo, se abre `lifemusic.app/equipo#c=…` en una pestaña de Chrome. El código va después del `#`, así que no
   viaja en la dirección al servidor ni queda en registros, y la página lo borra al leerlo.
3. Desde ahí, en *Acerca de* queda una fila **«Equipo»** que abre la sección.
4. La app no guarda el código ni la sesión: eso vive en el navegador.

## 4. Roles y lo que ve cada uno

| | Dueño (CG) | Supervisor de ejecución / Planificador | Operaciones |
|---|---|---|---|
| Su estado, su jornada, avisos y reglas | ✔ | ✔ | ✔ |
| **Tablero de ideas** | Todo, y es el único que **aprueba** y **publica** | Propone y escribe el planteamiento técnico (fases 1 y 2); ejecuta y prueba lo aprobado (fases 3 y 4) | Propone ideas (fase 1) y mira el resto |
| **Incidencias** (fallos, informes de probadores) | ✔ | Mira y toma las que van a ejecución | Crea, ordena y sigue |
| **Estado de los servicios** (TV, comunidad, descargas, web) | ✔ | Mira | ✔ y su lista de revisión semanal |
| **Concurso** | Todo | — | Solo el contador. **Nunca los nombres ni los correos.** |
| **Accesos** (registro, sin claves) | Concede y retira | Ve los suyos y pide | Ve los suyos y pide |
| **Personas e invitaciones** | ✔ | — | — |
| **Actividad** (quién hizo qué) | Toda | La suya | La suya |

**El estado de una persona:**
- **De contratación:** *en prueba*, *activa*, *en pausa* o *terminada*.
- **Del momento:** *en jornada hasta las 5:00 p. m.*, *fuera de jornada · solo lectura* o *día libre*.

## 5. El tablero de ideas: el ciclo de 4 fases

```
Idea → Planteamiento técnico → ✋ Aprobada (CG) → En ejecución → En pruebas → ✋ Publicada (CG)
                                                                     └─ o Descartada
```

- Cada idea lleva:
  - título, para qué sirve y versión objetivo;
  - responsable y fase;
  - notas y enlaces a la ficha y al PR en GitHub.
- **La regla de 3 a 5 funciones por versión** se ve como un contador: «1.3.3: 2 de 5».
- **Solo CG aprueba y solo CG publica.** Es la misma regla de siempre: aprobar el trabajo no es aprobar publicarlo.
- Al empezar, se pueden pasar al tablero las ideas que hay hoy en la Mesa de Ideas.

**Trabajar con Claude:**
- Cada persona usa **su propia cuenta de Claude** con el repositorio.
- Trabaja en una rama y abre un PR; **CG revisa y une**.
- Se propone **proteger la rama `main` en GitHub**: sin push directo, con PR y la aprobación de CG. Es gratis en
  repositorios públicos.

## 6. La jornada

- **Por persona:**
  - días: de lunes a viernes como máximo; nunca se pueden marcar el sábado ni el domingo;
  - hora de entrada y de salida, en hora de Colombia (`America/Bogota`);
  - **festivos de Colombia** cargados por año; CG los puede editar.
- **Fuera de la jornada es solo lectura, y el servidor lo hace cumplir:** cualquier cambio responde
  `fuera-de-jornada`. No es solo un botón gris.
- **Opcional:** marcar entrada y salida con un botón, para el reporte semanal.

## 7. Accesos y claves (lo decidido: cuentas propias y un gestor)

- **La sección es un registro** de qué plataforma tiene cada persona, con qué permiso, en qué estado (*pedido*,
  *concedido* o *retirado*) y desde cuándo. **Nunca guarda claves.**
- **Cada persona con su cuenta,** invitada con el permiso más bajo que le alcance:
  - **GitHub:** colaborador, con `main` protegida;
  - **Cloudflare:** miembro de la cuenta;
  - documentos compartidos donde haga falta.
- **Gestor de contraseñas,** solo para lo que de verdad no se pueda dar por invitación:
  - **Bitwarden** gratis alcanza para 2 personas (CG y una más);
  - para pasar un secreto una sola vez, **Bitwarden Send**: un enlace cifrado que se borra solo. También es gratis.
- **Lo que no se comparte nunca:** la llave de firma de la app (el keystore), la sesión de Google de CG y la cuenta de
  Claude de CG.
- **Salida ordenada:** al pasar a una persona a *terminada*, sale la lista de todos sus accesos para retirarlos uno
  por uno en cada plataforma, y se cierran sus sesiones. Sus datos se borran 90 días después.

## 8. Lo que agrego por mi cuenta

- **Reglas del equipo** que se aceptan al entrar y otra vez cuando cambian:
  - confidencialidad;
  - nada se publica sin CG;
  - el prerregistro no se toca;
  - las claves nunca van por chat.
- **Avisos de CG** fijados arriba.
- **Reporte semanal automático,** cada viernes: ideas movidas, incidencias, días en jornada. Lo ven CG y la persona.
- **Registro de actividad:** cada cambio queda anotado con quién, qué y cuándo.
- **Lista de revisión semanal de Operaciones:**
  - estado de los servicios;
  - calificaciones nuevas;
  - informes de probadores;
  - issues de GitHub.

## 9. Privacidad

- **De cada persona del equipo se guarda lo mínimo:** nombre, rol, horario y, si quiere, un correo de contacto.
- **Los usuarios de la app no se ven afectados:** nada de esto toca a quien solo escucha música, y no hay telemetría.
- **La política de privacidad** suma un párrafo «Equipo».

## 9b. Estreno limpio y reglamento (pedido de CG, 10-10)

CG pidió que **«la pág venga limpia, no haya nada absolutamente»** y que **cumpla con todos los reglamentos de una
página**.

### Estreno limpio
- **La base sale vacía:** solo el esquema de las tablas, sin datos de ejemplo. Lo primero que se guarda es la huella
  del código de dueño que genera CG.
- **Cada bloque tiene su estado vacío diseñado** (maqueta: botón «Ver estreno (vacío)»):
  - «Nadie en el equipo todavía»;
  - «La sesión está en silencio»;
  - «Sin incidencias»;
  - «Nada espera tu luz verde»;
  - la bitácora, que empieza con el primer pase.
- **Lo único que se ve desde el primer día son datos reales y en vivo:** el monitoreo de los servicios y el contador
  del concurso, marcados «EN VIVO».
- **Revisión antes de lanzar:** un comando de verificación revisa que no haya filas de prueba, que las cabeceras de
  seguridad respondan y que `/equipo` no esté en el mapa del sitio ni sea indexable. El lanzamiento solo va con el
  «sube» de CG.

### Reglamento
| Norma | Qué exige | Cómo se cumple |
|---|---|---|
| **Ley 1581 de 2012** y Decreto 1377 de 2013 (compilado en el 1074 de 2015): protección de datos | Política de tratamiento, autorización previa, expresa e informada, derechos del titular, plazos de respuesta | **Política de datos y términos** en `/equipo` (maqueta, pantalla 7). Casilla de **autorización aparte** en el acta, guardada con fecha y versión de la política. Bloque **«Tus datos»** para ver, descargar, corregir o borrar. Consultas en 10 días hábiles y reclamos en 15. |
| Ley 1581, art. 26: transferencia internacional | Informar dónde quedan los datos | La política dice que los guarda Cloudflare como encargado y que puede ser fuera de Colombia. |
| **Ley 527 de 1999** y Decreto 2364 de 2012: firma electrónica | Método confiable: identidad, integridad, aceptación | Pase de un solo uso, huella SHA-256 del acta firmada, casillas de aceptación, copia para las dos partes. |
| **Ley 1273 de 2009**, art. 269A: acceso abusivo | Advertir que el sistema es privado | Aviso en la entrada y en los términos. |
| Mayoría de edad | Quien firma debe ser capaz | Casilla «Soy mayor de edad». |
| Relación laboral | El acta no es un contrato de trabajo | Cláusula 08, «Contrato aparte». **El contrato lo revisa un abogado o un contador.** |
| Cookies y rastreo | Informar y no rastrear sin permiso | Una sola cookie, la de sesión, que es necesaria. Sin analítica ni publicidad. **Fuentes alojadas en lifemusic.app**, no en Google, para no mandarle la IP a terceros. |
| Accesibilidad (WCAG 2.1 AA, buena práctica; Resolución 1519 de 2020 de MinTIC como referencia) | Contraste, teclado, foco visible, etiquetas, sin depender del mouse | Foco visible y etiquetas en todo. **«Firmar escribiendo mi nombre»** para quien no puede dibujar. Se respeta «reducir movimiento». |
| Seguridad | Proteger los datos | HTTPS con HSTS; CSP estricta; `frame-ancestors 'none'`; `X-Content-Type-Options`; `Referrer-Policy: no-referrer`; `Permissions-Policy` sin cámara, micrófono ni ubicación; `X-Robots-Tag: noindex`; `Cache-Control: no-store` en la API; cookie `__Host-` HttpOnly; huellas en vez de secretos; límite de intentos; bitácora. |
| Registro Nacional de Bases de Datos (SIC) | Solo para empresas con activos de más de 100 000 UVT | No aplica hoy a CG LABS. Se revisa si crece. |
| Licencia GPL-3.0 | Avisar que el software es libre | En el pie y en la cláusula 06. |

**Lo que CG tiene que darme para la política:**
- su nombre completo como responsable;
- la ciudad;
- un correo de contacto para asuntos de datos. Se puede crear gratis `datos@lifemusic.app` con el reenvío de correo de
  Cloudflare hacia su Gmail, con su permiso.
- **Recomendación:** que un abogado lea la política y el acta antes del estreno. Esto es una base bien armada, no
  asesoría legal.

## 10. Tandas propuestas

| Tanda | Qué | Dónde se prueba |
|---|---|---|
| 1 | Servidor: Worker, D1 y ruta; invitaciones, sesiones, dueño, revocar, intentos y jornada. Con pruebas. | Local y luego producción, con tu «sube» |
| 2 | Página: el pase, **el acta con firma y contrafirma**, el panel de la persona (estado, jornada, avisos) y el panel del dueño (personas, invitaciones). | Tu PC y tu teléfono |
| 3 | Tablero de ideas, incidencias, accesos, actividad y reporte semanal. | Tu PC |
| 4 | Puerta en la app (1.3.3), política de privacidad y docs. | S25 |

## 11. Falta que decidas

1. El horario por defecto: ¿8:00 a. m. a 5:00 p. m.?
2. El registro de entrada y salida con un botón: ¿sí o no?
3. ¿Las personas del equipo ven quiénes son los demás (nombre y rol), o cada una ve solo lo suyo?
4. ¿El acta pide el documento de identidad? En la maqueta va como opcional.
5. ¿Las condiciones económicas van en el acta o aparte?
6. ¿Cuánto tiempo se guardan las actas después de terminar el cargo? Se propone un año.
