use ambermusic_desktop::{LibraryStore, PlaybackState, XdgPaths};
use std::env;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let paths = XdgPaths::discover()?;
    paths.create_all()?;
    let store = LibraryStore::open(paths.data.join("library.sqlite"))?;
    if let Some(directory) = env::args().nth(1) {
        let count = store.sync_directory(directory)?;
        println!("Indexed {count} audio files");
    }
    let library = store.load_all()?;
    let state = PlaybackState::default();
    println!(
        "AmberMusic desktop core initialized at {} ({} tracks, status: {:?})",
        paths.data.display(),
        library.all().count(),
        state.status
    );
    Ok(())
}
