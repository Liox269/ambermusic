# AmberMusic v1.3.0 · Fluent Themes

Esta actualización parte de **v1.2.1** (no requiere v1.2.2).

## Funcionalidades nuevas

- Tocar canciones reproduce sin abandonar la biblioteca; abrir Now Playing desde el minirreproductor.
- Conservación de navegación, pestaña, búsqueda y desplazamiento mediante SaveableStateHolder y LazyListState.
- Localizar la canción actual desde el menú superior; resalta la pista activa.
- Deslizar filas a la derecha: siguiente en cola. Deslizar a la izquierda: marcar/desmarcar favorito. Pulsación prolongada: acciones.
- Cola: mantener presionada una canción y arrastrar verticalmente para reordenar; controles arriba/abajo siguen disponibles.
- 7 temas AMOLED: Azul nocturno, Aigis rosa, Violeta noche, Jade profundo, Carmesí elegante, Grafito y Ámbar clásico.
- Paletas tonales para Material3; el blur precalculado de portada no se modifica.
- Índice de CD y disco desde metadatos de archivo si están presentes, con migración Room **4→5**; orden por pista de álbum.
- Reescaneo puntual de etiquetas de pista tras actualizar; luego vuelve a ser incremental.
- Se mantienen widgets, ReplayGain opcional, listas inteligentes, estadísticas, letras, filtros y duplicados existentes de v1.2.1.

## Límites importantes

- **La compilación Android no se probó en esta máquina**: no hay SDK Android ni acceso a sus dependencias de Gradle.
- Preferir 120 Hz no asegura 120 fps constantes. Se deben medir en el dispositivo.
- La edición de etiquetas no reescribe los MP3/FLAC.
- El reconocimiento de número de pista o disco depende de las etiquetas presentes en cada archivo.
- No se incluye Android Auto. No se accede a internet desde AmberMusic.
- La instalación por GitHub Actions genera un APK de depuración firmado con otra clave distinta a la de tu PC. Si Android muestra conflicto de firma, exporta antes el backup, desinstala la anterior, instala la nueva e importa el backup. El JSON no incluye los propios MP3, todo el historial ni todas las preferencias.

## Instalación habitual con CachyOS

```bash
cd ~/Downloads
unzip -o AmberMusic_Parche_v1.3.0_FluentThemes.zip
bash AmberMusic-Parche-v1.3.0/instalar.sh
cd ~/Downloads/AmberMusic-Completo
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Instalación sin PC

Ver `COMPILAR_DESDE_EL_CELULAR.md` y `GITHUB_WORKFLOW_build-apk.yml`.

## Pruebas manuales antes de dar por finalizada la instalación

1. Abre biblioteca, baja a mitad de lista, toca una canción: debe sonar sin navegar.
2. Abre mini reproductor, cierra con swipe abajo: misma búsqueda y posición de lista.
3. En el menú superior elige `Encontrar canción actual`.
4. Desliza una fila a izquierda y derecha; comprueba Favoritos y cola.
5. En Mi AmberMusic > Ajustes, cambia por las siete paletas.
6. Cambia entre Persona y Cyberpunk: el blur debe seguir siendo continuo y coloreado por la portada.
7. Verifica reproducir/pausar, auriculares, pantalla apagada y controles de HyperOS.
8. Abre un álbum etiquetado: deberá ordenar por disco y pista.
9. Vuelve a escanear y comprueba que los archivos cortos siguen excluidos.
10. Comprueba favoritos, playlists y datos tras la migración Room.
