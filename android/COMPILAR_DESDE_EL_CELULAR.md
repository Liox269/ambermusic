# Crear e instalar APK usando SOLO tu Redmi (GitHub Actions)

**Todavía no hay un APK compilado.** Este proyecto permite crear uno en una máquina temporal de GitHub, controlándolo desde el navegador de tu teléfono. No necesitas Android Studio ni una PC.

1. En AmberMusic v1.2.1: **Mi AmberMusic > Ajustes > Exportar**. Guarda el JSON en Descargas. Esto es importante porque el APK construido en GitHub normalmente tendrá una firma diferente al APK que hiciste en tu PC.
2. Descarga `AmberMusic_v1.3.0_Source.zip` en tu teléfono y el archivo `GITHUB_WORKFLOW_build-apk.yml` de esta entrega.
3. Desde Chrome en el teléfono, inicia sesión en https://github.com y crea un **repositorio privado** (ej. `ambermusic-android`). Si faltan botones, habilita `Sitio de escritorio` en Chrome.
4. En ese repositorio, selecciona **Add file > Upload files** y sube `AmberMusic_v1.3.0_Source.zip` SIN descomprimirlo. Confirma el commit.
5. Crea un archivo con **Add file > Create new file**. En el campo de nombre escribe exactamente `.github/workflows/build-apk.yml`. Pega el contenido del archivo `GITHUB_WORKFLOW_build-apk.yml` (puedes abrirlo como texto). Confirma el commit.
6. Abre la pestaña **Actions**. Si GitHub pide habilitar Actions en el repositorio, habilítalo. Abre **Construir APK AmberMusic desde celular**, elige **Run workflow** si no se inició solo.
7. Cuando una ejecución esté marcada con ✓ verde, entra en ella y en **Artifacts** descarga `AmberMusic-v1.3.0-APK`. GitHub lo entrega como ZIP. Extráelo en el teléfono para obtener `AmberMusic-v1.3.0-debug.apk`.
8. Abre el APK desde Archivos/Administrador de archivos. Da permiso **Instalar apps desconocidas** a ese administrador si Android lo solicita. Después de instalar, puedes desactivar ese permiso.
9. Si Android informa que el paquete entra en conflicto con una aplicación existente, significa que las firmas NO coinciden. **No intentes forzar la actualización**: confirma que exportaste el backup y luego desinstala AmberMusic v1.2.1, instala v1.3.0 e importa el JSON en Mi AmberMusic > Ajustes > Importar. Reescanea Music/ si es necesario. Los MP3 y FLAC no se borran al desinstalar la app, pero las preferencias e historial local no incluidos en el backup pueden perderse.
10. Si el APK falla al compilar, entra en Actions > ejecución > paso rojo `Compilar APK de depuración` y comparte el error. **No se ha garantizado que compile** porque esta entrega no ha ejecutado el SDK Android.

**Seguridad:** usa un repositorio privado; descarga únicamente el APK del workflow en tu propio repositorio. Esta app no requiere ningún permiso de Internet para reproducir música.
