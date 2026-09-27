/* Cristal liquido del receptor, en la GPU.

   Es la misma idea que el cristal de la app (la libreria de Kyant, que imita
   el Liquid Glass de Apple), escrita como un shader de WebGL 1:

   - La escena de detras es la caratula en grande (moviendose despacio) con
     las luces del fondo encima. Sin detalle detras, un vidrio no se nota.
   - Cada panel es un rectangulo redondeado descrito por su funcion de
     distancia (SDF). Cerca del canto, la luz se dobla segun un perfil
     circular (el borde de un vidrio grueso): se muestrea la escena desplazada
     a lo largo de la normal del canto.
   - Aberracion cromatica: rojo, verde y azul se doblan distinto en el canto.
   - Dentro, la escena se ve desenfocada (niveles de mipmap de la caratula),
     mas viva (saturacion) y con el tinte del usuario.
   - Brillo especular en el canto segun el angulo respecto a la luz (arriba a
     la izquierda fuerte, abajo a la derecha suave) y sombra proyectada.

   Sin modulos ni sintaxis reciente: los Chromecast viejos traen un Chrome
   antiguo. Si el aparato no tiene WebGL, crearCristalGL devuelve null y el
   receptor usa el vidrio dibujado en 2D. */
(function () {
  "use strict";

  var VERTICES = [
    "attribute vec2 aPos;",
    "varying vec2 vUv;",
    "void main() {",
    "  vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);", // origen arriba a la izquierda
    "  gl_Position = vec4(aPos, 0.0, 1.0);",
    "}",
  ].join("\n");

  var FRAGMENTOS = [
    "#ifdef GL_FRAGMENT_PRECISION_HIGH",
    "precision highp float;",
    "#else",
    "precision mediump float;",
    "#endif",
    "varying vec2 vUv;",
    "uniform vec2 uRes;",
    "uniform sampler2D uArteN;",  // caratula actual
    "uniform sampler2D uArteV;",  // la anterior, mientras se funden
    "uniform float uHayVieja;",
    "uniform float uMezcla;",
    "uniform sampler2D uLuces;",
    "uniform float uHayArte;",
    "uniform vec4 uArteXf;",     // escala (xy) y desplazamiento (zw) de la caratula
    "uniform vec4 uPanel0;",     // x, y, ancho, alto en px del lienzo
    "uniform vec4 uPanel1;",
    "uniform float uRadio;",
    "uniform float uLente;",     // alto del canto que refracta, px
    "uniform float uRefr;",      // cuanto se dobla la luz en el canto, px
    "uniform float uAberr;",
    "uniform float uBlur;",      // sesgo de mipmap dentro del vidrio
    "uniform float uSat;",
    "uniform vec4 uTinte;",
    "uniform float uSombra;",
    "uniform float uLatido;",
    "uniform float uSigno;",

    "float sdCaja(vec2 p, vec4 r, float rad) {",
    "  vec2 c = r.xy + r.zw * 0.5;",
    "  vec2 q = abs(p - c) - (r.zw * 0.5 - rad);",
    "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;",
    "}",
    "float sdf(vec2 p) {",
    "  float d = 1e5;",
    "  if (uPanel0.z > 0.0) d = sdCaja(p, uPanel0, uRadio);",
    "  if (uPanel1.z > 0.0) d = min(d, sdCaja(p, uPanel1, uRadio));",
    "  return d;",
    "}",
    // La escena: la caratula oscurecida y las luces encima (mezcla «pantalla»).
    "vec3 escena(vec2 uv, float sesgo) {",
    "  vec3 c = vec3(0.02);",
    "  vec2 a = uv * uArteXf.xy + uArteXf.zw;",
    "  if (uHayArte > 0.5) c = texture2D(uArteN, a, sesgo).rgb;",
    "  if (uMezcla < 0.999) {",
    "    vec3 v = uHayVieja > 0.5 ? texture2D(uArteV, a, sesgo).rgb : vec3(0.02);",
    "    c = mix(v, c, uMezcla);",
    "  }",
    "  c *= 0.66 + 0.10 * uLatido;",
    "  vec3 l = texture2D(uLuces, clamp(uv, 0.0, 1.0)).rgb * 0.42;",
    "  return 1.0 - (1.0 - c) * (1.0 - l);",
    "}",

    "void main() {",
    "  vec2 p = vUv * uRes;",
    "  float d = sdf(p);",
    "  vec3 fuera = vec3(0.0);",
    "  vec3 dentro = vec3(0.0);",
    "  if (d > -1.0) {",
    "    fuera = escena(vUv, 0.6);",
    "    if (uSombra > 0.5) {",
    "      float ds = sdf(p - vec2(0.0, uRes.y * 0.02));",
    "      fuera *= 1.0 - 0.5 * exp(-max(ds, 0.0) / (uRes.y * 0.035));",
    "    }",
    "  }",
    "  if (d < 1.0) {",
    "    float hondo = max(-d, 0.0);",
    "    vec2 e = vec2(1.0, 0.0);",
    "    vec2 n = vec2(sdf(p + e.xy) - sdf(p - e.xy), sdf(p + e.yx) - sdf(p - e.yx));",
    "    n = n / max(length(n), 1e-4);",                 // normal hacia fuera
    "    float t = clamp(1.0 - hondo / uLente, 0.0, 1.0);",
    "    float perfil = 1.0 - sqrt(1.0 - t * t);",       // 0 dentro, 1 en el canto (borde circular)
    "    vec2 desp = n * perfil * uRefr / uRes;",
    "    vec2 uv = vUv;",
    "    vec3 g;",
    "    if (uAberr > 0.5) {",
    "      g.r = escena(uv + uSigno * desp * 1.00, uBlur).r;",
    "      g.g = escena(uv + uSigno * desp * 0.88, uBlur).g;",
    "      g.b = escena(uv + uSigno * desp * 0.76, uBlur).b;",
    "    } else {",
    "      g = escena(uv + uSigno * desp, uBlur);",
    "    }",
    "    float lum = dot(g, vec3(0.299, 0.587, 0.114));",
    "    g = mix(vec3(lum), g, uSat) * 1.05;",
    "    g = mix(g, uTinte.rgb, uTinte.a);",
    // Brillo especular: el canto se enciende donde mira hacia la luz.
    "    float haciaLuz = max(dot(n, vec2(-0.7071, -0.7071)), 0.0);",
    "    float contraLuz = max(dot(n, vec2(0.7071, 0.7071)), 0.0);",
    "    float luz = pow(haciaLuz, 2.0) + 0.5 * pow(contraLuz, 2.0);",
    "    float canto = 1.0 - smoothstep(0.0, 2.2, hondo);",
    "    g += canto * (0.10 + 0.75 * luz);",
    "    g += perfil * 0.16 * luz;",
    // Reflejo difuso de la superficie, de la esquina de arriba a la izquierda.
    "    g += 0.05 * (1.0 - smoothstep(0.0, 0.45, vUv.x * 0.6 + vUv.y * 0.4));",
    "    dentro = g;",
    "  }",
    "  float a = clamp(0.5 - d, 0.0, 1.0);",            // borde suave de un pixel
    "  gl_FragColor = vec4(mix(fuera, dentro, a), 1.0);",
    "}",
  ].join("\n");

  function compilar(gl, tipo, fuente) {
    var s = gl.createShader(tipo);
    gl.shaderSource(s, fuente);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error("shader: " + gl.getShaderInfoLog(s));
    return s;
  }

  window.crearCristalGL = function (lienzo) {
    var gl = null;
    try {
      gl = lienzo.getContext("webgl", { alpha: false, antialias: false, depth: false, stencil: false, preserveDrawingBuffer: false, powerPreference: "low-power" }) ||
        lienzo.getContext("experimental-webgl");
    } catch (e) { gl = null; }
    if (!gl) return null;

    var prog = gl.createProgram();
    gl.attachShader(prog, compilar(gl, gl.VERTEX_SHADER, VERTICES));
    gl.attachShader(prog, compilar(gl, gl.FRAGMENT_SHADER, FRAGMENTOS));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error("programa: " + gl.getProgramInfoLog(prog));
    gl.useProgram(prog);

    var buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    var aPos = gl.getAttribLocation(prog, "aPos");
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);


    var u = {};
    ["uRes", "uArteN", "uArteV", "uHayArte", "uHayVieja", "uMezcla", "uArteXf", "uLuces", "uPanel0", "uPanel1", "uRadio",
      "uLente", "uRefr", "uAberr", "uBlur", "uSat", "uTinte", "uSombra", "uLatido", "uSigno"]
      .forEach(function (n) { u[n] = gl.getUniformLocation(prog, n); });

    function textura(unidad) {
      var t = gl.createTexture();
      gl.activeTexture(gl.TEXTURE0 + unidad);
      gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array([5, 5, 5, 255]));
      return t;
    }
    // Dos caratulas (unidades 0 y 2) para fundir la vieja con la nueva; las luces en la 1.
    var UNIDADES = [0, 2];
    var texArte = [textura(0), textura(2)];
    var texLuces = textura(1);
    gl.uniform1i(u.uLuces, 1);
    var actual = 0, hay = [false, false], mezclaDesde = -1e9;
    var FUNDIDO_MS = 1200;

    // La caratula va a un lienzo cuadrado de 512 (potencia de dos) para tener
    // mipmaps: son el desenfoque del interior del vidrio, casi gratis.
    var cuadro = document.createElement("canvas");
    cuadro.width = cuadro.height = 512;
    var cctx = cuadro.getContext("2d");

    return {
      gl: gl,
      /** La caratula nueva (una <img> con CORS) o null. Se funde con la anterior. */
      ponerArte: function (img) {
        var sig = 1 - actual;
        if (img) {
          var iw = img.naturalWidth || img.width, ih = img.naturalHeight || img.height;
          var lado = Math.min(iw, ih);
          cctx.drawImage(img, (iw - lado) / 2, (ih - lado) / 2, lado, lado, 0, 0, 512, 512);
          gl.activeTexture(gl.TEXTURE0 + UNIDADES[sig]);
          gl.bindTexture(gl.TEXTURE_2D, texArte[sig]);
          gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, cuadro); // lanza si la imagen no tiene CORS
          gl.generateMipmap(gl.TEXTURE_2D);
          gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
        }
        hay[sig] = !!img;
        actual = sig;
        mezclaDesde = performance.now();
      },
      subirLuces: function (fuente) {
        gl.activeTexture(gl.TEXTURE1);
        gl.bindTexture(gl.TEXTURE_2D, texLuces);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, fuente);
      },
      /* e: { ancho, alto (px del lienzo), paneles: [[x, y, w, h], ...] en px del lienzo,
              radio, lente, refr (px), aberracion, desenfoque (sesgo de mipmap), saturacion,
              tinte: [r, g, b, a] de 0 a 1, sombra, latido, arte: [escalaX, escalaY, despX, despY] } */
      dibujar: function (e) {
        if (lienzo.width !== e.ancho || lienzo.height !== e.alto) { lienzo.width = e.ancho; lienzo.height = e.alto; }
        gl.viewport(0, 0, e.ancho, e.alto);
        gl.uniform2f(u.uRes, e.ancho, e.alto);
        gl.uniform1i(u.uArteN, UNIDADES[actual]);
        gl.uniform1i(u.uArteV, UNIDADES[1 - actual]);
        gl.uniform1f(u.uHayArte, hay[actual] ? 1 : 0);
        gl.uniform1f(u.uHayVieja, hay[1 - actual] ? 1 : 0);
        gl.uniform1f(u.uMezcla, Math.min(1, (performance.now() - mezclaDesde) / FUNDIDO_MS));
        gl.uniform4f(u.uArteXf, e.arte[0], e.arte[1], e.arte[2], e.arte[3]);
        var p0 = e.paneles[0] || [0, 0, 0, 0], p1 = e.paneles[1] || [0, 0, 0, 0];
        gl.uniform4f(u.uPanel0, p0[0], p0[1], p0[2], p0[3]);
        gl.uniform4f(u.uPanel1, p1[0], p1[1], p1[2], p1[3]);
        gl.uniform1f(u.uRadio, e.radio);
        gl.uniform1f(u.uLente, Math.max(1, e.lente));
        gl.uniform1f(u.uRefr, e.refr);
        gl.uniform1f(u.uAberr, e.aberracion ? 1 : 0);
        gl.uniform1f(u.uBlur, e.desenfoque);
        gl.uniform1f(u.uSat, e.saturacion);
        gl.uniform4f(u.uTinte, e.tinte[0], e.tinte[1], e.tinte[2], e.tinte[3]);
        gl.uniform1f(u.uSombra, e.sombra ? 1 : 0);
        gl.uniform1f(u.uLatido, e.latido || 0);
        gl.uniform1f(u.uSigno, e.signo === undefined ? -1 : e.signo);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      },
    };
  };
})();
