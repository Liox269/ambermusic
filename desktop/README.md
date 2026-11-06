# AmberMusic Desktop

Núcleo independiente para la versión Linux de AmberMusic. Está preparado para
CachyOS/Arch, Wayland, Hyprland y Caelestia. El núcleo no requiere librerías
gráficas para ejecutar sus pruebas; la integración GTK4/GStreamer/MPRIS se
activa con la feature `linux-ui` cuando las dependencias nativas estén
instaladas.

## Desarrollo en CachyOS

```bash
sudo pacman -S --needed rust gtk4 libadwaita gstreamer gst-plugins-base \
  gst-plugins-good pipewire sqlite dbus desktop-file-utils shared-mime-info
cargo test --manifest-path desktop/Cargo.toml
cargo run --manifest-path desktop/Cargo.toml
```

La aplicación respeta XDG:

- Configuración: `$XDG_CONFIG_HOME/ambermusic` o `~/.config/ambermusic`
- Datos: `$XDG_DATA_HOME/ambermusic` o `~/.local/share/ambermusic`
- Caché: `$XDG_CACHE_HOME/ambermusic` o `~/.cache/ambermusic`

La integración futura con Caelestia debe usar MPRIS mediante D-Bus; no debe
modificar la configuración de Hyprland ni depender de APIs privadas.
