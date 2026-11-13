package com.example

import com.example.data.model.Track
import com.example.ui.screens.buildDriveTree
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `buildDriveTree hides folders without music files and shows folders with music`() {
    val tracks = listOf(
      Track(
        id = "drive_1",
        folderPath = "マイドライブ/Music/Rock/Queen",
        fileName = "Bohemian_Rhapsody.mp3"
      ),
      Track(
        id = "drive_2",
        folderPath = "マイドライブ/Music/Classical/Chopin",
        fileName = "Nocturne.flac"
      ),
      Track(
        id = "drive_3",
        folderPath = "マイドライブ/Documents/Notes",
        fileName = "readme.txt" // non-music file
      )
    )

    val roots = buildDriveTree(tracks)

    // "マイドライブ" (container root with 0 music files) and "Music" (empty intermediate) must NOT be shown as roots
    // Only the folders containing actual music files should be shown
    assertEquals(2, roots.size)

    val queenNode = roots.find { it.displayName.contains("Queen") }
    assertNotNull(queenNode)
    assertEquals(1, queenNode!!.tracks.size)
    assertEquals("Bohemian_Rhapsody.mp3", queenNode.tracks.first().fileName)

    val chopinNode = roots.find { it.displayName.contains("Chopin") }
    assertNotNull(chopinNode)
    assertEquals(1, chopinNode!!.tracks.size)
    assertEquals("Nocturne.flac", chopinNode.tracks.first().fileName)

    // "Documents" or "Notes" must not exist anywhere
    val docNode = roots.find { it.displayName.contains("Documents") || it.displayName.contains("Notes") }
    assertNull(docNode)
  }

  @Test
  fun `buildCombinedStorageTree orders 端末 before GoogleDrive and filters cached and past-3-days folders`() {
    val now = 1_700_000_000_000L // arbitrary fixed time
    val oneDayAgo = now - (1L * 24 * 60 * 60 * 1000L)
    val twoDaysAgo = now - (2L * 24 * 60 * 60 * 1000L)
    val fiveDaysAgo = now - (5L * 24 * 60 * 60 * 1000L)

    // Local tracks from device
    val localTracks = listOf(
      com.example.ui.TrackInfo(
        id = "local_1",
        title = "Local Song 1",
        artist = "Local Artist",
        album = "Local Album",
        uri = "/storage/emulated/0/Music/song1.mp3",
        folderUriString = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
      )
    )
    val localFolders = listOf(
      com.example.ui.MusicFolder(
        uriString = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
        displayName = "端末音楽フォルダ"
      )
    )

    // Google Drive tracks:
    // 1. "Queen": currently cached (isCached = true) -> MUST BE INCLUDED
    // 2. "Chopin": cached 2 days ago, currently un-cached (isCached = false, lastCachedAt = 2 days ago) -> MUST BE INCLUDED
    // 3. "Mozart": cached 5 days ago, currently un-cached (isCached = false, lastCachedAt = 5 days ago) -> MUST BE EXCLUDED
    // 4. "Beatles": never cached (isCached = false, lastCachedAt = 0) -> MUST BE EXCLUDED
    val driveTracks = listOf(
      Track(
        id = "d_queen_1",
        folderPath = "マイドライブ/Rock/Queen",
        fileName = "Bohemian.mp3",
        isCached = true,
        lastCachedAt = oneDayAgo
      ),
      Track(
        id = "d_chopin_1",
        folderPath = "マイドライブ/Classical/Chopin",
        fileName = "Nocturne.mp3",
        isCached = false,
        lastCachedAt = twoDaysAgo
      ),
      Track(
        id = "d_mozart_1",
        folderPath = "マイドライブ/Classical/Mozart",
        fileName = "Requiem.mp3",
        isCached = false,
        lastCachedAt = fiveDaysAgo
      ),
      Track(
        id = "d_beatles_1",
        folderPath = "マイドライブ/Rock/Beatles",
        fileName = "Yesterday.mp3",
        isCached = false,
        lastCachedAt = 0L
      )
    )

    val combinedRoots = com.example.ui.screens.buildCombinedStorageTree(
      localTracks = localTracks,
      folders = localFolders,
      driveTracks = driveTracks,
      now = now
    )

    // Should contain:
    // 1. 端末 folder ("端末 / 端末音楽フォルダ")
    // 2. Queen (cached)
    // 3. Chopin (cached 2 days ago)
    // Should NOT contain Mozart (5 days ago) or Beatles (never cached)
    assertEquals(3, combinedRoots.size)

    // First node MUST be 端末 (Device first, then Google Drive)
    assertTrue("First root must be 端末", combinedRoots[0].displayName.contains("端末"))
    assertEquals(1, combinedRoots[0].tracks.size)

    // Second and third roots are Google Drive folders
    val driveRootNames = combinedRoots.drop(1).map { it.displayName }
    assertTrue("Must contain Queen", driveRootNames.any { it.contains("Queen") })
    assertTrue("Must contain Chopin", driveRootNames.any { it.contains("Chopin") })
    assertFalse("Must NOT contain Mozart", driveRootNames.any { it.contains("Mozart") })
    assertFalse("Must NOT contain Beatles", driveRootNames.any { it.contains("Beatles") })
  }

  @Test
  fun `MusicTab HUB is present and distinct from STORAGE`() {
    val tab = com.example.ui.MusicTab.HUB
    assertNotNull(tab)
    assertNotEquals(com.example.ui.MusicTab.PLAY, tab)
    assertNotEquals(com.example.ui.MusicTab.STORAGE, tab)
    assertNotEquals(com.example.ui.MusicTab.SETTINGS, tab)
    assertEquals("HUB", tab.name)
  }

  @Test
  fun `Hub tracks group by album and artist accurately`() {
    val tracks = listOf(
      com.example.ui.TrackInfo(id = "1", title = "Song A", artist = "Artist 1", album = "Album X"),
      com.example.ui.TrackInfo(id = "2", title = "Song B", artist = "Artist 1", album = "Album X"),
      com.example.ui.TrackInfo(id = "3", title = "Song C", artist = "Artist 2", album = "Album Y"),
      com.example.ui.TrackInfo(id = "4", title = "Song D", artist = "", album = "")
    )

    val albumGroups = tracks.groupBy { if (it.album.isBlank()) "Unknown Album" else it.album }
    assertEquals(3, albumGroups.size)
    assertEquals(2, albumGroups["Album X"]?.size)
    assertEquals(1, albumGroups["Album Y"]?.size)
    assertEquals(1, albumGroups["Unknown Album"]?.size)

    val artistGroups = tracks.groupBy { if (it.artist.isBlank()) "Unknown Artist" else it.artist }
    assertEquals(3, artistGroups.size)
    assertEquals(2, artistGroups["Artist 1"]?.size)
    assertEquals(1, artistGroups["Artist 2"]?.size)
    assertEquals(1, artistGroups["Unknown Artist"]?.size)
  }

  @Test
  fun `MusicalFormClassifier prioritizes ID3 grouping tag`() {
    val form = com.example.util.MusicalFormClassifier.classify(
      grouping = "交響曲",
      title = "Track 01",
      album = "Unknown",
      folderName = "Folder"
    )
    assertEquals("交響曲", form)

    val customGrouping = com.example.util.MusicalFormClassifier.classify(
      grouping = "古楽・バロック",
      title = "Track 01",
      album = "Unknown",
      folderName = "Folder"
    )
    assertEquals("古楽・バロック", customGrouping)
  }

  @Test
  fun `MusicalFormClassifier detects form from folder name when album is in folder`() {
    // フォルダ名に「Symphonies」がある場合、タイトルが「01 - Allegro」でも交響曲と判別
    val formSymphony = com.example.util.MusicalFormClassifier.classify(
      title = "01 - Allegro con brio",
      album = "",
      folderName = "Beethoven - Symphonies (Karajan)",
      folderUriString = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FBeethoven%20-%20Symphonies%20(Karajan)"
    )
    assertEquals(com.example.util.MusicalFormClassifier.FORM_SYMPHONY, formSymphony)

    // フォルダ名に「Piano Works」がある場合
    val formPiano = com.example.util.MusicalFormClassifier.classify(
      title = "Track 03",
      album = "",
      folderName = "Chopin - Complete Piano Works",
      folderUriString = "/storage/emulated/0/Music/Chopin - Complete Piano Works"
    )
    assertEquals(com.example.util.MusicalFormClassifier.FORM_PIANO, formPiano)

    // 協奏曲 (Concertos)
    val formConcerto = com.example.util.MusicalFormClassifier.classify(
      title = "I. Allegro moderato",
      album = "Piano Concerto No. 2",
      folderName = "Rachmaninoff - Piano Concertos",
      folderUriString = "/Music/Rachmaninoff - Piano Concertos"
    )
    assertEquals(com.example.util.MusicalFormClassifier.FORM_CONCERTO, formConcerto)

    // 声楽・オペラ
    val formOpera = com.example.util.MusicalFormClassifier.classify(
      title = "Nessun dorma",
      album = "Turandot",
      folderName = "Puccini - Opera Turandot",
      folderUriString = "/Music/Puccini - Opera Turandot"
    )
    assertEquals(com.example.util.MusicalFormClassifier.FORM_VOCAL_OPERA, formOpera)
  }
}



