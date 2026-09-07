# Criterios de diseño

Life Music hereda una interfaz que ya funcionaba. Lo que sigue no es un manual de
estilo completo: son las decisiones que este proyecto ha tomado por su cuenta y
el razonamiento detrás, para que la próxima función no las contradiga sin querer.

## Material You se queda encendido

Es el comportamiento nativo de Android 12 en adelante y el que ya usaban los
proyectos de los que desciende. La consecuencia se acepta: el verde esmeralda de
la marca (`#10B981`) **solo se ve si el usuario desactiva el color dinámico**.

El verde vive en el icono y en el logotipo. La interfaz es del usuario, no de la
marca.

## Una función nueva no añade espacio: ocupa el que sobra

La lección más cara de este proyecto. Life Line se colocó primero **encima** de
la separación que ya había entre el bloque de título y la barra de progreso: el
resultado fue una línea apretada contra el artista y un hueco muerto debajo.

La versión correcta **sustituye** esa separación en vez de sumarse a ella. El
reproductor no crece ni un dp, y cuando no hay letra la maqueta vuelve a ser
exactamente la original.

Tres cosas que conviene saber antes de tocar el reproductor:

- **El margen horizontal lo pone cada hijo**, no el contenedor. Sin
  `padding(horizontal = PlayerHorizontalPadding)` el elemento se va al borde.
- **La carátula va en un `Box(weight(1f))` y absorbe lo que sobre.** Si el bloque
  de controles se acorta, la cabecera baja sola. Esa es la palanca para moverla;
  no hay que empujarla.
- **El `Slider` de Material 3 trae unos 26 dp de relleno propio** antes de su
  pista, por el objetivo táctil de 48 dp. Hay que contarlo al repartir aire, o el
  reparto sale torcido.

## Medir la tinta, no las cajas

Al ajustar espaciados se miden **los glifos en una captura**, no los límites de
maquetado. Las cajas de un texto con marquesina mienten: declaran alto que no se
ve. Densidad del dispositivo de referencia: 548 dpi, 3,425 px/dp.

## Se distingue por comportamiento, no por decoración

Life Line y la voz de la app comparten contenedor, tamaño y zona táctil. Lo que
las diferencia es cómo se mueven: **la letra barre** de izquierda a derecha
siguiendo la voz, y **la voz de la app respira** despacio. Basta para leer de un
vistazo quién está hablando, sin cambiar la tipografía ni meter iconos.

## Los avisos no respiran

Todo lo que sea una advertencia —se cayó la red, esto no se pudo reproducir— va
en color de error, sin animación y sin atenuar. Se tiene que leer a la primera.

## Una flecha promete algo

El chevron solo aparece si tocar lleva a alguna parte. Sin letra en ningún
proveedor, abrir el panel mostraría una pantalla vacía: ahí no hay flecha. Una
flecha que no cumple es peor que ninguna.

## Decir la verdad sobre lo que hace la función

La reducción rápida de voz se llamaba «Bajar la voz principal» y prometía
conservar la mezcla. Lo que hace es restar lo que suena igual en los dos canales,
y eso se lleva coros y percusión central. **Se renombró y se explicó en su propia
pantalla.**

Si alguien sube un control esperando una cosa y oye otra, el fallo no es de la
función: es que nadie le contó qué hace.

## El tema de la aplicación no es el del sistema

Ajustes ofrece claro, oscuro, automático y negro puro. Por eso los recursos
`drawable-night`, que Android elige por el modo del **sistema**, pueden servir la
variante equivocada.

Para gráficos monocromos la solución es **teñirlos con `LocalContentColor`**: así
quedan atados al color que de verdad se está usando, venga el tema de donde
venga.

## Respetar cuando el sistema pide quietud

Si `ANIMATOR_DURATION_SCALE` vale 0 —ajuste de accesibilidad, o ahorro de batería
en algunos equipos— las animaciones decorativas no se ejecutan. Un elemento que
cambia sin transición no es minimalista: es un error visual.

## Todo se puede apagar

Cada función propia trae su interruptor en Ajustes → Apariencia, con un valor por
defecto sensato. Apagada, la función no gasta temporizadores, ni animaciones, ni
recomposiciones: sale por la misma rama que cuando no aplica.
