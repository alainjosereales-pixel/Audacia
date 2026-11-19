# Guía de Usuario Oficial · Projection Mapper Studio

Bienvenido a **Projection Mapper Studio**, un software profesional, potente y fácil de usar diseñado para crear proyecciones mapeadas (*Video Mapping*) sobre objetos físicos, fachadas, escenarios, esculturas y muros.

Desarrollado por **Jhan By AudacIA**.

---

## 📑 Contenido

1. [Conceptos Básicos de Video Mapping](#1-conceptos-básicos-de-video-mapping)
2. [Preparar tu Computadora y el Proyector](#2-preparar-tu-computadora-y-el-proyector)
3. [Cómo Iniciar y Salir del Programa](#3-cómo-iniciar-y-salir-del-programa)
4. [Recorrido por la Pantalla de Control](#4-recorrido-por-la-pantalla-de-control)
5. [Cómo Crear y Mapear Superficies](#5-cómo-crear-y-mapear-superficies)
6. [Asignar Videos, Imágenes, Colores y Contornos Animados](#6-asignar-videos-imágenes-y-colores)
7. [Calibración Óptica de Máxima Precisión](#7-calibración-óptica-de-máxima-precisión)
8. [Presentación en Vivo (Modo Show)](#8-presentación-en-vivo-modo-show)
9. [Guardar y Administrar tus Escenas](#9-guardar-y-administrar-tus-escenas)
10. [Compartir Proyectos entre Equipos (.pmap)](#10-compartir-proyectos-entre-equipos-pmap)
11. [Exportar tu Diseño a Video (.mp4)](#11-exportar-tu-diseño-a-video-mp4)
12. [Tabla de Atajos de Teclado](#12-tabla-de-atajos-de-teclado)
13. [Preguntas Frecuentes y Solución de Problemas](#13-preguntas-frecuentes-y-solución-de-problemas)

---

## 1. Conceptos Básicos de Video Mapping

El **Video Mapping** consiste en adaptar imágenes y videos a superficies del mundo real (como cajas, columnas, muros o letras 3D) para que la luz del proyector calce exactamente en los bordes del objeto.

Projection Mapper funciona con una **arquitectura de doble ventana**:
* **Ventana de Control:** La pantalla donde trabajas, mueves los puntos, eliges videos y organizas tus capas.
* **Ventana de Proyector:** Una ventana limpia y negra que se envía al proyector físico. Todo lo que haces en la ventana de control se refleja en el proyector en **tiempo real (0 ms)**.

---

## 2. Preparar tu Computadora y el Proyector

Antes de abrir el programa, configura la conexión de tu pantalla en Windows:

1. Conecta el proyector a tu laptop o PC mediante cable **HDMI**, **DisplayPort** o **VGA**.
2. Enciende el proyector.
3. En tu teclado de Windows, presiona la combinación de teclas:
   <kbd>Windows</kbd> + <kbd>P</kbd>
4. En el panel que aparece a la derecha de la pantalla, selecciona la opción **"Extender"** (NO uses "Duplicar").
5. *(Opcional)* Entra en `Configuración de Pantalla de Windows` y asegúrate de que la resolución recomendada del proyector esté en **1920 × 1080** (o su resolución nativa).

---

## 3. Cómo Iniciar y Salir del Programa

### Para Iniciar
Haz doble clic en el archivo:
👉 **`INICIAR.bat`**

El programa realizará comprobaciones automáticas, arrancará el motor local y abrirá tu navegador web en la dirección `http://localhost:8085/`.

### Para Abrir la Ventana del Proyector
1. En la barra superior de la Ventana de Control, haz clic en el botón azul:  
   **`🖥️ Abrir Proyector`**
2. Se abrirá una nueva ventana con fondo negro.
3. Arrastra esa ventana hacia la pantalla de tu proyector.
4. Presiona la tecla <kbd>F11</kbd> (o presiona <kbd>F</kbd> o haz doble clic sobre ella) para ponerla a **pantalla completa sin marcos**.

### Para Salir y Apagar el Programa
Tienes dos opciones fáciles y seguras:
* **Desde el navegador:** Haz clic en el menú de **tres puntos (`⋮`)** en la esquina superior derecha y selecciona **"🛑 Salir y Detener Servidor"**.
* **Desde la carpeta:** Haz doble clic en el archivo **`DETENER.bat`**.

---

## 4. Recorrido por la Pantalla de Control

La interfaz está organizada de forma intuitiva en 4 zonas:

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ [PM] Projection Mapper | Escena: Mi_Escena  [Deshacer] [Guardar] [⋮ Menú]│
├───────────────┬──────────────────────────────────────────┬───────────────┤
│               │  [✏️ Polígono Recto] [➰ Polígono Curvo]  │               │
│   PANEL DE    │                                          │   PANEL DE    │
│    CAPAS      │             LIENZO CENTRAL               │  PROPIEDADES  │
│               │           ÁREA DE CALIBRACIÓN            │   Y MEDIOS    │
│  (Subir/Bajar │                                          │               │
│   Superficies)│                                          │ (Videos, Opac,│
│               │                                          │  Transformac.)│
└───────────────┴──────────────────────────────────────────┴───────────────┘
```

1. **Barra Superior:**
   * **Logo PM y Nombre de la Escena:** Haz clic en la caja de texto para renombrar tu escena en cualquier momento.
   * **Botones Rápidos:** Deshacer (<kbd>Ctrl+Z</kbd>), Rehacer (<kbd>Ctrl+Y</kbd>), Alternar Modo Calibración/Presentación (<kbd>Espacio</kbd>), Guardar (<kbd>Ctrl+S</kbd>), Exportar Video y Abrir Proyector.
   * **Menú de 3 Puntos (`⋮`):** Gestor de escenas, importar proyectos, exportar paquetes `.pmap`, galería de videos renderizados, ayuda y apagado del sistema.
2. **Panel Izquierdo (Capas):** Muestra el orden de profundidad (Z-Index). Las superficies que están arriba cubren a las que están abajo. Puedes reordenarlas con las flechas o los botones de envío al frente/fondo.
3. **Lienzo Central:** El espacio visual donde ves y ajustas tus figuras con guías de alineación milimétricas.
4. **Panel Derecho (Propiedades y Medios):** Aquí configuras el nombre, color, opacidad, tipo de medio (imagen o video), controles de reproducción (play/pausa, velocidad, loop) de la figura seleccionada.

---

## 5. Cómo Crear y Mapear Superficies

En la barra de herramientas sobre el lienzo central dispones de diferentes métodos para crear figuras:

### A. Figuras Estándar (Menú "➕ Añadir Superficie")
Haz clic en el menú desplegable y elige:
* **Cuadrilátero (Quad):** La figura reina del mapping. Posee 4 esquinas deformables de forma libre para calzar en caras de cubos, cuadros o paredes.
* **Cuadrado / Rectángulo:** Figura regular con vértices alineados.
* **Círculo / Elipse:** Superficie curva perfecta para platos, relojes, columnas circulares o esferas.
* **Triángulo, Pentágono o Hexágono:** Geometrías poligonales regulares.

### B. Dibujo de Polígono Recto Libre (Tecla <kbd>P</kbd>)
Ideal para objetos de formas irregulares o polígonos de muchas caras:
1. Haz clic en **"✏️ Polígono Recto"** o presiona la tecla <kbd>P</kbd>.
2. Haz clics sucesivos sobre el lienzo siguiendo las esquinas de tu objeto real.
3. Para finalizar la figura, haz clic sobre el primer vértice o presiona la tecla <kbd>Enter</kbd>.

### C. Dibujo y Edición de Polígonos Curvos Bézier (Tecla <kbd>C</kbd>)
Diseñado para estructuras arquitectónicas curvas, arcos, domos o siluetas orgánicas:
1. Haz clic en **"➰ Polígono Curvo"** o presiona la tecla <kbd>C</kbd>.
2. Haz clic en el primer punto. Conforme muevas el mouse, una **línea guía curva inteligente** te mostrará la trayectoria prevista.
3. Si deseas arquear la arista en tiempo real durante el trazado, **mantén presionado el clic y arrastra hacia donde quieras curvar** antes de soltar.
4. Presiona <kbd>Enter</kbd> (o haz clic en el primer vértice) para cerrar el polígono curvo.
5. **Edición y Manipulación Avanzada de Curvas:**
   * **Manijas Tangentes Púrpuras ($C_1$ y $C_2$):** Arrastra las manijas circulares púrpuras para controlar el arqueo, tensión y ángulo de cada arista con precisión milimétrica.
   * **Añadir Nuevas Esquinas (Subdivisión De Casteljau):** Haz clic en el punto medio cian (`+`). El sistema aplica el algoritmo exacto de **De Casteljau a $t=0.5$**, dividiendo la curva en dos mitades perfectas sin alterar su forma y otorgando al nuevo vértice manijas tangenciales a ambos lados.
   * **Alternar Arista Individual (Recta ↔ Curva):** Mantén presionado <kbd>Alt</kbd> (o <kbd>Shift</kbd>) y haz **clic en el punto medio (`+`)** de cualquier arista para convertirla de recta a curva o viceversa. También dispones del botón *"Alternar Curvatura Arista"* en el panel derecho al seleccionar un vértice.
   * **Fusión Inteligente al Eliminar Vértices:** Si seleccionas y eliminas una esquina (<kbd>Supr</kbd>) en un polígono curvo, el programa fusiona automáticamente las aristas incidentes conservando la curvatura suave y manteniendo las manijas Bézier de los vértices adyacentes.

### D. Máscaras de Oclusión (Menú "⬛ Añadir Máscara")
Si en tu escenario hay un elemento que **no debe recibir luz** (como una puerta, una ventana, un logotipo o un agujero):
1. Añade una **Máscara Cuadrada**, **Circular** u **Oclusor Libre**.
2. Ubícala y calibra sus esquinas sobre el obstáculo.
3. La máscara proyectará **negro absoluto (`#000000`)**, bloqueando cualquier video o luz que esté pasando por detrás.

---

## 6. Asignar Videos, Imágenes y Colores

Una vez creada una superficie, puedes vestirla con contenido visual de tres formas:

### 1. Arrastrar y Soltar (El método más rápido)
Abre el explorador de archivos de Windows y **arrastra directamente un archivo de video (`.mp4`, `.mov`) o imagen (`.png`, `.jpg`) sobre la superficie en el lienzo**. El programa lo cargará y asignará de inmediato.

### 2. Desde el Panel de Propiedades
Selecciona una figura haciendo clic sobre ella. En el panel derecho:
* **Imagen:** Haz clic en *"🖼️ Cambiar Imagen"* y selecciona un archivo de tu disco.
* **Video:** Haz clic en *"🎬 Cambiar Video"* y elige un clip.
* **Color Sólido:** Si no deseas asignar multimedia, puedes elegir un color de relleno con el selector de color y regular su opacidad.

### 3. Controles de Reproducción de Video
Para cada superficie con video asignado, dispones de:
* **Play / Pausa:** Detener o reanudar la animación.
* **Bucle (Loop):** Repetición continua y fluida.
* **Silencio (Mute):** Activar o silenciar el audio del video.
* **Velocidad:** Reproducir a cámara lenta (0.25x, 0.5x) o cámara rápida (1.5x, 2.0x).

### 4. Fondo de Superficie (Con Color vs. Sin Fondo)
En el panel derecho dispones del selector segmentado **Fondo de Superficie**:
* **Con Color:** La figura proyecta su color sólido de relleno (con la opacidad seleccionada) o su textura/video asignado.
* **Sin Fondo (`fillEnabled: false`):** La figura se vuelve completamente transparente en el proyector. Cero luz es emitida hacia el interior del área delimitada. Esto resulta fundamental para:
  * Iluminar únicamente los bordes de objetos físicos colocados en vitrinas, mostradores de retail o estanterías de exhibición.
  * Mantener figuras huecas con contornos animados sin opacar el producto real que se encuentra detrás.
* **Visibilidad en el Controlador:** Mientras en el proyector el fondo es 100% transparente, en el lienzo del operador la figura se visualiza con una línea guía punteada sutil y conserva toda su interactividad para seleccionar, arrastrar vértices y ajustar curvaturas Bézier. En la lista de capas, las figuras sin fondo se distinguen visualmente con un círculo indicador hueco.

### 5. Contornos Proyectables y Animaciones Perimetrales
Puedes añadir un contorno visual independiente que se proyecte en tiempo real sobre la superficie física:
* **Activar / Ocultar Contorno:** Haz clic en el botón de visibilidad del contorno (**Mostrar / Ocultar**).
* **Propiedades del Contorno:**
  * **Color:** Selector de color dedicado con botón **Igualar** para sincronizar rápidamente el tono del contorno con el color de la figura.
  * **Grosor:** Regulador de 1 a 50 píxeles para ajustar el ancho de la línea proyectada.
  * **Opacidad:** De 0% a 100% para mezclas sutiles de luz o trazos de alto impacto visual.
  * **Resplandor Óptico (Glow):** Activa un filtro de difusión luminosa con efecto neón que realza los bordes sobre superficies oscuras o estructuras metálicas.
* **Estilos de Línea:**
  * **Continua (`solid`):** Línea sólida estándar para delimitación geométrica nítida.
  * **Discontinua (`dashed`):** Trazos rítmicos uniformes.
  * **Punteada (`dotted`):** Puntos de luz definidos a lo largo de todo el perímetro.
  * **Trazo y Punto (`dash_dot`):** Cadencia alternada elegante.
  * **Tramos Largos (`long_dash`):** Segmentos extendidos con separaciones marcadas.
  * **Doble Carril (`double`):** Trazo doble con canal central calado para acabados de alta definición y estilo arquitectónico.
  * **Escala de Trazo:** Ajusta interactivamente la separación y longitud relativa de los patrones discontinuos.
* **Control de Longitud de Trazo (Contorno Completo vs. Segmentos):**
  * Para efectos móviles como El Gusano, Haz Láser o Trazado Progresivo, dispones del control deslizante **Longitud de Trazo** (del 5% al 100%).
  * Al configurarlo al **100% (Completo)**, la figura dibuja todo su perímetro.
  * Al reducirlo (ej. **30% o 50%**), la animación se convierte en un segmento móvil o haz dinámico que recorre los bordes de la figura sin proyectar ninguna silueta tenue en las zonas inactivas, logrando un fondo negro puro en el proyector.
* **Catálogo de Animaciones Perimetrales Profesionales:**
  * **Ninguna (`none`):** Trazo estático continuo.
  * **El Gusano / Chaser (`trace_chaser`):** Una línea luminosa que viaja a lo largo del perímetro dibujando y desdibujando la figura con cabeza brillante y longitud configurable, sin siluetas de fondo.
  * **Marcha Continua (`march`):** Desplazamiento perimetral continuo del patrón de trazo (efecto scanner o plano blueprint). Si el estilo de línea actual es sólido, el sistema conmuta automáticamente a discontinuo.
  * **Haz Láser (`laser_comet`):** Un cometa de luz concentrada con núcleo brillante y estela degradada que recorre el perímetro.
  * **Doble Persecución (`dual_chase`):** Dos cometas luminosos opuestos que giran al unísono en sincronía.
  * **Trazado Progresivo (`draw_wipe`):** Trazado circular continuo que se dibuja y desvanece de forma cíclica.
  * **Pulso Rítmico (`pulse`):** Respiración y bombeo volumétrico modulando tanto el grosor físico del trazo como la intensidad del brillo, ideal para acompañar música y ritmos.
  * **Edge Glitch (`edge_glitch`):** Micro-desplazamientos rápidos, cortes staccato y parpadeos energéticos estilo holograma.
  * **Gradient March (`gradient_march`):** Un degradado de colores continuo que da vueltas infinitamente alrededor del perímetro.
  * **Flujo de Color (`color_flow`):** Transición cromática continua a lo largo del círculo de color HSL.
* **Controles Dinámicos:**
  * **Velocidad:** Regulable de 0.1x (movimiento ultra lento y elegante) a 5.0x (dinamismo rápido para eventos de alta energía).
  * **Sentido:** Botones de dirección Horario y Antihorario.

---

## 7. Calibración Óptica de Máxima Precisión

Para lograr que la proyección calce exactamente con la realidad:

1. **Arrastrar Vértices:** Haz clic sobre cualquier vértice (círculos en las esquinas) y arrástralo hasta que coincida con el borde físico del objeto real. El proyector se actualiza instantáneamente.
2. **Micro-Ajuste con Teclado (Precisión de 1 Píxel):**
   * Haz clic sobre un vértice para seleccionarlo.
   * Usa las **flechas del teclado** (<kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd>) para moverlo milímetro a milímetro (1 px por toque).
   * Mantén presionado <kbd>Shift</kbd> + <kbd>Flechas</kbd> para moverlo en pasos de 10 px.
3. **Selección Múltiple:** Mantén presionado <kbd>Shift</kbd> y haz clic en varios vértices para mover un lado completo a la vez.
4. **Mover Superficie Completa:** Arrastra desde el centro de la figura para reubicarla por completo.

---

## 8. Presentación en Vivo (Modo Show)

Durante el montaje y la calibración necesitas ver las líneas de guía, los números de vértice y las cuadrículas. Pero cuando el show o evento comienza, la proyección debe ser completamente limpia:

* **Para alternar entre Calibración y Presentación:** Presiona la tecla <kbd>Espacio</kbd> o la tecla <kbd>M</kbd> en tu teclado (o haz clic en el botón superior *"🎯 Modo: Calibración"*).
* En **Modo Presentación**:
  * Todas las líneas blancas, vectores de borde y números desaparecen del proyector.
  * Solo se proyectan tus videos, imágenes y animaciones.
  * El cursor del mouse se oculta por completo automáticamente en el proyector.

---

## 9. Guardar y Administrar tus Escenas

* **Guardado Rápido (<kbd>Ctrl + S</kbd>):** Guarda los cambios en la escena actual inmediatamente. Aparecerá una notificación verde en pantalla confirmando el guardado.
* **Guardar Como... (<kbd>Ctrl + Shift + S</kbd>):** Te permite guardar la configuración actual con un nombre nuevo (ej. `Escena_Nocturna_v2`).
* **Gestor de Escenas (<kbd>Ctrl + O</kbd>):**
  * Abre una ventana con buscador en tiempo real.
  * Muestra miniaturas visuales de tus escenas guardadas con fecha, cantidad de superficies y tamaño.
  * Permite cargar cualquier escena con 1 clic, duplicarla o eliminarla.

---

## 10. Compartir Proyectos entre Equipos (.pmap)

Si diseñas tu mapping en una computadora potente de escritorio y luego debes llevarlo en una memoria USB a la laptop conectada al proyector en el evento:

### ¿Qué es un archivo `.pmap`?
Es un **Paquete de Proyecto Autónomo**. A diferencia de un archivo JSON que solo guarda números de coordenadas, el archivo `.pmap` empaqueta en un único archivo comprimido:
1. Todas las geometrías, vértices, deformaciones y calibraciones.
2. **Todos los archivos reales de video e imagen** asignados a tus figuras.

### Cómo Exportar un Paquete (.pmap):
1. Abre el menú de **tres puntos (`⋮`)** en la barra superior.
2. Selecciona **"📦 Exportar Paquete (.pmap)"**.
3. Se descargará un archivo con el nombre de tu escena (ej. `Mi_Escena.pmap`). Guarda ese archivo en tu memoria USB.

### Cómo Importar en la otra Computadora:
1. En la laptop del evento, abre Projection Mapper.
2. En el menú de **tres puntos (`⋮`)**, haz clic en **"📥 Importar Proyecto (.pmap / .json)"**.
3. Selecciona tu archivo `.pmap`. El sistema extraerá los videos e imágenes automáticamente en las carpetas locales y cargará la escena lista para proyectar sin ningún enlace roto.

> [!NOTE]
> Si solo deseas respaldar la geometría sin los archivos pesados de video, utiliza la opción **"📄 Exportar Escena (.json)"**.

---

## 11. Exportar tu Diseño a Video (.mp4)

Projection Mapper incluye un motor que permite renderizar todo tu diseño pre-mapeado en un archivo de video `.mp4`. Esto te permite reproducir el mapping final con cualquier reproductor multimedia (VLC, BrightSign, reproductores de hardware autónomos) sin necesidad de tener el editor abierto.

### Pasos para Exportar:
1. Haz clic en el botón naranja **`🎬 Exportar Video`**.
2. Configura los parámetros de exportación:
   * **Resolución:** `1920 × 1080` (Full HD), `3840 × 2160` (4K) o `1280 × 720` (HD Rápido).
   * **Cuadros por Segundo (FPS):** `30 FPS` (recomendado) o `60 FPS` (ultra suave).
   * **Duración:** Haz clic en **"⏱️ Auto-detectar Duración"** para que el software calcule automáticamente los segundos necesarios para un bucle perfecto según el video más largo.
3. Haz clic en **`🚀 Iniciar Renderizado`**.
4. El sistema utilizará la aceleración por hardware de tu tarjeta gráfica (NVIDIA, AMD o Intel) y te mostrará el avance fotograma a fotograma en tiempo real.
5. Al finalizar, haz clic en **`⬇️ Descargar Video Mapeado (.mp4)`**.

### Cancelación Segura
Si deseas abortar el renderizado, pulsa **`⏹ Cancelar Render`**. El sistema detendrá el proceso de inmediato y **eliminará automáticamente el archivo truncado del disco**, manteniendo tu galería de videos limpia y ordenada.

### Renderizado de Contornos Animados y Bucle Perfecto (Seamless Loop)
Al exportar una escena con contornos animados a video `.mp4`:
* El motor calcula con precisión matemática la fase temporal fotograma a fotograma mediante algoritmos de rasterización vectorial deterministas en OpenCV y NumPy.
* Los ciclos de animación se cuantizan automáticamente respecto a la duración total del video. De este modo, el último fotograma conecta de forma idéntica con el primero, logrando un bucle sin saltos visuales (*seamless loop*) ideal para reproductores de hardware o pantallas en bucle continuo en mostradores, escaparates y stands comerciales.
* La ventana modal de exportación detecta si tu escena contiene figuras con contorno animado y sugiere automáticamente una duración recomendada en segundos para optimizar el ciclo de repetición.

### Galería de Videos Renderizados
En el menú de tres puntos (`⋮`) -> **"🎞️ Videos Renderizados"**, puedes ver, previsualizar, descargar o eliminar todos los videos que hayas exportado previamente.

---

## 12. Tabla de Atajos de Teclado

| Tecla | Acción |
|---|---|
| <kbd>V</kbd> | Activar Modo Selección y Edición de Puntos |
| <kbd>P</kbd> | Herramienta de Dibujo de Polígono Recto |
| <kbd>C</kbd> | Herramienta de Dibujo de Polígono Curvo (Bézier) |
| <kbd>Espacio</kbd> o <kbd>M</kbd> | Alternar Modo Calibración / Modo Presentación |
| <kbd>Enter</kbd> | Cerrar y finalizar el polígono en curso de dibujo |
| <kbd>Esc</kbd> | Cancelar dibujo actual o deseleccionar figura |
| <kbd>Ctrl + S</kbd> | Guardar escena actual rápidamente |
| <kbd>Ctrl + Shift + S</kbd> | Guardar escena como... |
| <kbd>Ctrl + O</kbd> | Abrir el Gestor de Escenas |
| <kbd>Ctrl + Z</kbd> | Deshacer último movimiento |
| <kbd>Ctrl + Y</kbd> | Rehacer acción deshecha |
| <kbd>Supr</kbd> / <kbd>Del</kbd> | Eliminar vértice seleccionado o figura activa |
| <kbd>Alt + Rueda</kbd> | Escalar la figura seleccionada proporcionalmente desde su centro |
| <kbd>Alt + Arrastrar</kbd> | Escalar la figura seleccionada arrastrando sobre su cuerpo |
| <kbd>Manija en Esquina</kbd> | Redimensionar figura o círculo arrastrando su manija interactiva |
| <kbd>Shift + Clic</kbd> | Selección múltiple de vértices |
| <kbd>Clic en (+)</kbd> | Añadir nuevo vértice en arista (subdivisión matemática De Casteljau) |
| <kbd>Alt + Clic en (+)</kbd> | Alternar arista individual entre Recta y Curva Bézier |
| <kbd>Flechas</kbd> | Mover vértices seleccionados 1 píxel |
| <kbd>Shift + Flechas</kbd> | Mover vértices seleccionados 10 píxeles |
| <kbd>?</kbd> o <kbd>F1</kbd> | Abrir ventana de ayuda rápida y atajos |
| <kbd>F</kbd> o <kbd>F11</kbd> *(En Proyector)* | Pantalla completa con fondo negro |

---

## 13. Preguntas Frecuentes y Solución de Problemas

#### 1. ¿Por qué la ventana del proyector muestra la barra de tareas de Windows?
Presiona la tecla <kbd>F11</kbd> (o presiona la tecla <kbd>F</kbd>) mientras estés en la ventana del proyector para activar la pantalla completa sin bordes.

#### 2. ¿Qué hago si cerré la ventana del proyector por error?
En la ventana de control, simplemente vuelve a hacer clic en el botón **`🖥️ Abrir Proyector`**. Se abrirá de nuevo y sincronizará todas tus figuras automáticamente en menos de 1 segundo.

#### 3. Los videos no se reproducen o se ven en pausa:
Selecciona la figura en la ventana de control y revisa que en el panel derecho el botón indique **"⏸ Pausar"**. Si dice "▶ Reproducir", haz clic sobre él para iniciar la reproducción.

#### 4. Cambié de computadora y las figuras están pero los videos no cargan:
Eso sucede cuando compartes solo el archivo `.json`. Para transferir proyectos completos a otra computadora con todos sus videos, utiliza siempre la opción **"📦 Exportar Paquete (.pmap)"** y cárgalo con **"📥 Importar Proyecto"**.

#### 5. ¿Puedo usar la versión portable en una memoria USB?
Sí, la carpeta `ProjectionMapper-Portable` contiene todo lo necesario. Puedes copiarla directamente a tu pendrive USB y ejecutar `INICIAR.bat` en cualquier PC con Windows 10 u 11 sin necesidad de instalar nada ni tener conexión a Internet.

---

## 👤 Créditos y Autoría

**Projection Mapper Studio** ha sido diseñado y desarrollado por:  
**Jhan By AudacIA**

*Innovación en tecnologías audiovisuales interactivas y mapeo de proyección.*
