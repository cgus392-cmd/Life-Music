# Política de seguridad

## Versiones con soporte

Life Music es un proyecto educativo mantenido por una persona. **Solo la última
versión publicada recibe correcciones.** No hay ramas de soporte prolongado.

| Versión | Soporte |
|---|---|
| 1.0.x | ✅ |
| anteriores a la bifurcación (Echo 1.2.3 y previas) | ❌ — diríjase al [proyecto de origen](https://github.com/EchoMusicApp/Echo-Music) |

## Cómo informar de una vulnerabilidad

**No abra una incidencia pública.** Escriba a **cgus392@gmail.com** con:

- Qué versión y qué dispositivo.
- Qué pasa y cómo reproducirlo.
- Qué impacto cree que tiene.

Recibirá acuse en unos días. No hay recompensas económicas: el proyecto no genera
ingresos de ninguna clase.

## Qué entra y qué no

**Entra:** ejecución de código, filtración de datos del usuario fuera del
dispositivo, escalada de permisos, cualquier ruta por la que la aplicación
mande información a un servidor no declarado.

**No entra**, porque son propiedades conocidas y documentadas del proyecto:

- Que la aplicación acceda a YouTube Music por APIs no oficiales. Está explicado
  en el [aviso legal](README.md#aviso-legal); es la naturaleza del proyecto, no
  un fallo.
- Que las compilaciones de depuración sean depurables.
- Vulnerabilidades del sistema operativo o de dependencias de terceros que no
  podamos mitigar desde aquí; repórtelas a quien corresponda.

## Lo que ya se hizo por seguridad

Merece constar, porque el proyecto de origen lo tenía de otra manera:

- **Se retiró `google-services.json`**, que apuntaba al proyecto de Firebase de
  Echo. La aplicación enviaba analítica y trazas de fallo a infraestructura de
  terceros.
- **Se desactivó el auto-actualizador**, que venía encendido por defecto y
  consultaba las versiones de otro repositorio. En cuanto ese proyecto publicara
  una versión superior, Life Music habría ofrecido instalar un APK ajeno.
