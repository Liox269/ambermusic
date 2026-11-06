use rusqlite::{params, Connection};
use std::collections::{HashMap, HashSet};
use std::env;
use std::fs;
use std::io;
use std::path::PathBuf;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct XdgPaths {
    pub config: PathBuf,
    pub data: PathBuf,
    pub cache: PathBuf,
}

impl XdgPaths {
    pub fn discover() -> io::Result<Self> {
        let home = env::var_os("HOME")
            .map(PathBuf::from)
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "HOME is not set"))?;
        let config = env_path("XDG_CONFIG_HOME").unwrap_or_else(|| home.join(".config"));
        let data = env_path("XDG_DATA_HOME").unwrap_or_else(|| home.join(".local/share"));
        let cache = env_path("XDG_CACHE_HOME").unwrap_or_else(|| home.join(".cache"));
        Ok(Self {
            config: config.join("ambermusic"),
            data: data.join("ambermusic"),
            cache: cache.join("ambermusic"),
        })
    }

    pub fn create_all(&self) -> io::Result<()> {
        fs::create_dir_all(&self.config)?;
        fs::create_dir_all(&self.data)?;
        fs::create_dir_all(&self.cache)?;
        Ok(())
    }
}

fn env_path(name: &str) -> Option<PathBuf> {
    env::var_os(name)
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Track {
    pub id: String,
    pub path: PathBuf,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub artwork: Option<PathBuf>,
}

impl Track {
    pub fn from_path(path: impl Into<PathBuf>) -> Self {
        let path = path.into();
        let title = path
            .file_stem()
            .and_then(|name| name.to_str())
            .unwrap_or("Unknown track")
            .to_owned();
        Self {
            id: path.to_string_lossy().into_owned(),
            path,
            title,
            artist: "Unknown artist".to_owned(),
            album: "Unknown album".to_owned(),
            duration_ms: 0,
            artwork: None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PlaybackStatus {
    Stopped,
    Playing,
    Paused,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlaybackState {
    pub queue: Vec<String>,
    pub current: Option<String>,
    pub position_ms: u64,
    pub status: PlaybackStatus,
    pub shuffle: bool,
    pub repeat: bool,
}

impl Default for PlaybackState {
    fn default() -> Self {
        Self {
            queue: Vec::new(),
            current: None,
            position_ms: 0,
            status: PlaybackStatus::Stopped,
            shuffle: false,
            repeat: false,
        }
    }
}

impl PlaybackState {
    pub fn set_queue(&mut self, tracks: &[Track], index: usize) {
        self.queue = tracks.iter().map(|track| track.id.clone()).collect();
        self.current = self.queue.get(index).cloned();
        self.position_ms = 0;
        self.status = if self.current.is_some() {
            PlaybackStatus::Paused
        } else {
            PlaybackStatus::Stopped
        };
    }

    pub fn next(&mut self) {
        let Some(current) = self.current.as_ref() else {
            return;
        };
        let Some(index) = self.queue.iter().position(|id| id == current) else {
            return;
        };
        let next = if index + 1 < self.queue.len() {
            Some(index + 1)
        } else if self.repeat {
            Some(0)
        } else {
            None
        };
        self.current = next.and_then(|index| self.queue.get(index).cloned());
        self.position_ms = 0;
        if self.current.is_none() {
            self.status = PlaybackStatus::Stopped;
        }
    }

    pub fn previous(&mut self) {
        let Some(current) = self.current.as_ref() else {
            return;
        };
        let Some(index) = self.queue.iter().position(|id| id == current) else {
            return;
        };
        let previous = index
            .checked_sub(1)
            .or_else(|| self.repeat.then_some(self.queue.len().saturating_sub(1)));
        self.current = previous.and_then(|index| self.queue.get(index).cloned());
        self.position_ms = 0;
    }
}

#[derive(Debug, Default)]
pub struct Library {
    tracks: HashMap<String, Track>,
}

impl Library {
    pub fn insert(&mut self, track: Track) {
        self.tracks.insert(track.id.clone(), track);
    }

    pub fn get(&self, id: &str) -> Option<&Track> {
        self.tracks.get(id)
    }

    pub fn search(&self, query: &str) -> Vec<&Track> {
        let query = normalize(query);
        let mut results: Vec<_> = self
            .tracks
            .values()
            .filter(|track| {
                normalize(&format!("{} {} {}", track.title, track.artist, track.album))
                    .contains(&query)
            })
            .collect();
        results.sort_by_key(|track| normalize(&track.title));
        results
    }

    pub fn all(&self) -> impl Iterator<Item = &Track> {
        self.tracks.values()
    }
}

pub struct LibraryStore {
    connection: Connection,
}

impl LibraryStore {
    pub fn open(path: impl AsRef<std::path::Path>) -> rusqlite::Result<Self> {
        let connection = Connection::open(path)?;
        connection.execute_batch(
            "CREATE TABLE IF NOT EXISTS tracks (
                id TEXT PRIMARY KEY,
                path TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                artwork TEXT
            );
            CREATE INDEX IF NOT EXISTS tracks_title_idx ON tracks(title COLLATE NOCASE);",
        )?;
        Ok(Self { connection })
    }

    pub fn upsert(&self, track: &Track) -> rusqlite::Result<()> {
        self.connection.execute(
            "INSERT INTO tracks (id, path, title, artist, album, duration_ms, artwork)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
             ON CONFLICT(id) DO UPDATE SET
               path=excluded.path, title=excluded.title, artist=excluded.artist,
               album=excluded.album, duration_ms=excluded.duration_ms, artwork=excluded.artwork",
            params![
                track.id,
                track.path.to_string_lossy(),
                track.title,
                track.artist,
                track.album,
                track.duration_ms,
                track
                    .artwork
                    .as_ref()
                    .map(|path| path.to_string_lossy().into_owned()),
            ],
        )?;
        Ok(())
    }

    pub fn load_all(&self) -> rusqlite::Result<Library> {
        let mut statement = self.connection.prepare(
            "SELECT id, path, title, artist, album, duration_ms, artwork FROM tracks ORDER BY title COLLATE NOCASE",
        )?;
        let tracks = statement.query_map([], |row| {
            Ok(Track {
                id: row.get(0)?,
                path: PathBuf::from(row.get::<_, String>(1)?),
                title: row.get(2)?,
                artist: row.get(3)?,
                album: row.get(4)?,
                duration_ms: row.get(5)?,
                artwork: row.get::<_, Option<String>>(6)?.map(PathBuf::from),
            })
        })?;
        let mut library = Library::default();
        for track in tracks {
            library.insert(track?);
        }
        Ok(library)
    }

    pub fn sync_directory(&self, root: impl AsRef<std::path::Path>) -> rusqlite::Result<usize> {
        let root = root
            .as_ref()
            .canonicalize()
            .map_err(|error| rusqlite::Error::ToSqlConversionFailure(Box::new(error)))?;
        let mut paths = Vec::new();
        collect_audio_files(&root, &mut paths)
            .map_err(|error| rusqlite::Error::ToSqlConversionFailure(Box::new(error)))?;
        for path in &paths {
            self.upsert(&Track::from_path(path))?;
        }
        let current: HashSet<_> = paths
            .iter()
            .map(|path| path.to_string_lossy().into_owned())
            .collect();
        let root_prefix = format!("{}/%", root.to_string_lossy());
        let mut statement = self
            .connection
            .prepare("SELECT path FROM tracks WHERE path LIKE ?1")?;
        let stored: Vec<String> = statement
            .query_map(params![root_prefix], |row| row.get(0))?
            .collect::<rusqlite::Result<_>>()?;
        drop(statement);
        for path in stored {
            if !current.contains(&path) {
                self.connection
                    .execute("DELETE FROM tracks WHERE path = ?1", params![path])?;
            }
        }
        Ok(paths.len())
    }
}

fn collect_audio_files(root: &std::path::Path, output: &mut Vec<PathBuf>) -> io::Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            collect_audio_files(&path, output)?;
        } else if path.is_file() && is_audio_path(&path) {
            output.push(path);
        }
    }
    Ok(())
}

fn is_audio_path(path: &std::path::Path) -> bool {
    matches!(
        path.extension()
            .and_then(|extension| extension.to_str())
            .map(|extension| extension.to_ascii_lowercase())
            .as_deref(),
        Some("mp3" | "flac" | "m4a" | "wav" | "ogg" | "oga" | "opus" | "aac")
    )
}

pub fn normalize(value: &str) -> String {
    value
        .chars()
        .flat_map(|character| match character {
            'á' | 'à' | 'ä' | 'â' | 'Á' | 'À' | 'Ä' | 'Â' => {
                "a".chars().collect::<Vec<_>>()
            }
            'é' | 'è' | 'ë' | 'ê' | 'É' | 'È' | 'Ë' | 'Ê' => "e".chars().collect(),
            'í' | 'ì' | 'ï' | 'î' | 'Í' | 'Ì' | 'Ï' | 'Î' => "i".chars().collect(),
            'ó' | 'ò' | 'ö' | 'ô' | 'Ó' | 'Ò' | 'Ö' | 'Ô' => "o".chars().collect(),
            'ú' | 'ù' | 'ü' | 'û' | 'Ú' | 'Ù' | 'Ü' | 'Û' => "u".chars().collect(),
            'ñ' | 'Ñ' => "n".chars().collect(),
            character => vec![character.to_ascii_lowercase()],
        })
        .collect()
}

pub fn parse_m3u(input: &str) -> Vec<PathBuf> {
    input
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(PathBuf::from)
        .collect()
}

pub fn write_m3u(paths: &[PathBuf]) -> String {
    let mut output = String::from("#EXTM3U\n");
    for path in paths {
        output.push_str(&path.to_string_lossy());
        output.push('\n');
    }
    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_accents_and_case() {
        assert_eq!(normalize("Beyoncé — Álbum"), "beyonce — album");
    }

    #[test]
    fn searches_using_normalized_metadata() {
        let mut library = Library::default();
        let mut track = Track::from_path("/music/song.flac");
        track.title = "Canción".into();
        track.artist = "Artista".into();
        library.insert(track);
        assert_eq!(library.search("cancion").len(), 1);
    }

    #[test]
    fn restores_and_advances_by_stable_ids() {
        let tracks = vec![Track::from_path("a.mp3"), Track::from_path("b.mp3")];
        let mut state = PlaybackState::default();
        state.set_queue(&tracks, 0);
        state.next();
        assert_eq!(state.current, Some("b.mp3".into()));
    }

    #[test]
    fn round_trips_m3u_paths() {
        let paths = vec![
            PathBuf::from("/music/a.mp3"),
            PathBuf::from("/music/b.flac"),
        ];
        assert_eq!(parse_m3u(&write_m3u(&paths)), paths);
    }

    #[test]
    fn persists_tracks_in_sqlite() {
        let directory = tempfile::tempdir().unwrap();
        let store = LibraryStore::open(directory.path().join("library.sqlite")).unwrap();
        let track = Track::from_path("/music/song.flac");
        store.upsert(&track).unwrap();
        let library = store.load_all().unwrap();
        assert_eq!(library.get(&track.id), Some(&track));
    }

    #[test]
    fn syncs_supported_files_and_removes_missing_files() {
        let directory = tempfile::tempdir().unwrap();
        let music = directory.path().join("music");
        std::fs::create_dir_all(&music).unwrap();
        std::fs::write(music.join("first.mp3"), b"audio").unwrap();
        std::fs::write(music.join("ignore.txt"), b"text").unwrap();
        let store = LibraryStore::open(directory.path().join("library.sqlite")).unwrap();
        assert_eq!(store.sync_directory(&music).unwrap(), 1);
        assert_eq!(store.load_all().unwrap().all().count(), 1);
        std::fs::remove_file(music.join("first.mp3")).unwrap();
        store.sync_directory(&music).unwrap();
        assert_eq!(store.load_all().unwrap().all().count(), 0);
    }
}
