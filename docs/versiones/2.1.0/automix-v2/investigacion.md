# Automix: lo que dice la investigación (10-11-2026, madrugada)

Pedido de CG: dejar de inventar e investigar cómo lo hacen de verdad. El detonante: con el largo en 8 compases, la
canción que entraba sonaba escondida debajo de la que salía y aparecía por su segundo 15 o 16; además se estiraba el
tempo en cualquier pareja a menos del 8 %. Eso sirve para un set de DJ, no para la gente que deja sonar su música.

## Fuentes

1. **Apple AutoMix (iOS 26).**
   - Apple no publica cómo funciona por dentro. Su guía dice que hace transiciones «como un DJ» y que, según la
     música, quita silencios, hace un fundido o algo más complejo.
     [Soporte de Apple](https://support.apple.com/guide/iphone/aside/iphcba31c44b/ios),
     [9to5Mac](https://9to5mac.com/2025/06/16/automix-apple-music-ios-26/).
   - **Quejas reales:**
     - empieza a mezclar con más de 30 s por delante y se come el final;
     - «casi siempre se salta la primera estrofa»;
     - en un ejemplo, la siguiente entró en el segundo 49.

     [TechRadar](https://www.techradar.com/audio/apple-music/apple-music-fans-are-obsessed-with-automix-in-ios-26-but-one-big-flaw-could-be-its-downfall),
     [heise](https://heise.de/-11139247),
     [BGR: «la peor función»](https://bgr.com/2059450/how-to-turn-off-automix-apple-music-worst-feature).
   - **Conclusión:** el video de TikTok era el mejor caso, no lo normal.
2. **Spotify Mix (agosto de 2025, beta Premium).** Es opcional y por lista: Auto, Fade o Rise, con ondas, BPM y
   tonalidad. Spotify dice que funciona mejor con house, techno y electrónica.
   [MacRumors](https://www.macrumors.com/2025/08/20/spotify-debuts-playlist-mixing-tool/),
   [MusicRadar](https://www.musicradar.com/music-tech/spotify-responds-to-apple-musics-new-automix-feature-by-letting-you-turn-your-playlists-into-ready-made-dj-sets-with-seamless-transitions).
3. **Lo que hacen los DJ: Kim et al., ISMIR 2020** ([arXiv 2008.10267](https://arxiv.org/abs/2008.10267)).
   Analizaron 1.557 sets reales y 20.765 transiciones.
   - Tempo: el **86,1 % de los ajustes es menor al 5 %**, el 94,5 % menor al 10 % y el 98,6 % menor al 20 %.
   - Tonalidad: solo se transpone el **2,5 %**, y el 94,3 % de esas veces es un semitono.
   - Largo: picos cada **32 golpes** (una frase), en sets de música electrónica.
   - Los puntos de entrada caen cerca del comienzo de la canción; entre DJ distintos, el 73,6 % coincide a menos de
     8 compases.
4. **Puntos de mezcla automáticos: Zehren et al. (Computer Music Journal, 2023)**
   ([arXiv 2007.08411](https://arxiv.org/abs/2007.08411)). Reglas sacadas de entrevistas a DJ profesionales, más un
   conjunto anotado: ~90 % de puntos usables en canciones nuevas, en electrónica. Código: github.com/MZehren/Automix.
5. **Radio profesional, el modelo de «dejar sonar».** Cada canción lleva marcas de intro y de salida. La
   superposición es **la menor de las dos marcas**; sin marcas, el valor por defecto es **un segundo**. El editor de
   segues corrige a mano. Nunca se corta contenido.
   [TuneTracker](https://tunetrackersystems.com/tips/UnderstandingSegueMarkers.html),
   [PlayIt](https://docs.playitsoftware.com/PlayItLive/216/segue_editor.html),
   [Myriad](https://help.broadcastradio.com/support/solutions/articles/101000549613-setting-using-timing-markers-in-myriad-playout-6).

## Propuesta: dos productos, no uno (pendiente del sí de CG)

| | Auto (escuchar normal) | Mixed Editor / modo DJ (a pedido) |
|---|---|---|
| Modelo | Radio | Sets de DJ |
| Superposición | Corta: solo sobre el final que ya se apaga y la intro sin voz (marcas del mapa); sin marcas, pocos segundos | Por frases: 8, 16 o 32 golpes |
| Comienzo de la nueva | Nunca se salta la intro ni la primera estrofa | Puede entrar por estructura (batería, coro) |
| Estirar tempo | Menos del 5 %, con buena confianza y una mezcla de al menos 4 compases | Hasta el 5 % |
| Transponer | Nunca | Nunca |
| Por género | Salsa, vallenato y baladas: fundido corto o cierre. Reguetón y electrónica: al golpe si encaja | Lo elige quien mezcla |
| Aprender | Parejas que sonaron bien y un 👍/👎 local, sin nube | Lo mismo, más los puntos elegidos en la mesa |

Siguiente tanda (prueba.21), si CG aprueba:
1. Un Auto conservador al estilo radio.
2. Tope de estiramiento del 5 %.
3. Mezclas largas solo en el Mixed Editor.
4. Primera versión de los perfiles por género.
