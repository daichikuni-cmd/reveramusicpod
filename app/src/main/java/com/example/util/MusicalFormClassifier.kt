package com.example.util

import android.net.Uri
import com.example.ui.TrackInfo

/**
 * 楽曲の「形式」（交響曲、協奏曲、ピアノ、ヴァイオリン・弦楽器、室内楽、声楽・オペラ、管弦楽曲・組曲、吹奏楽・管楽器など）
 * をID3タグ（TIT1/GRP1）およびタイトル・アルバム・ジャンル・フォルダ名からハイブリッドに分類する分類器。
 */
object MusicalFormClassifier {

    const val FORM_SYMPHONY = "交響曲"
    const val FORM_CONCERTO = "協奏曲"
    const val FORM_PIANO = "ピアノ"
    const val FORM_STRINGS = "ヴァイオリン・弦楽器"
    const val FORM_CHAMBER = "室内楽"
    const val FORM_VOCAL_OPERA = "声楽・オペラ"
    const val FORM_ORCHESTRAL = "管弦楽曲・組曲"
    const val FORM_WIND_BRASS = "吹奏楽・管楽器"
    const val FORM_OTHER = "その他・未分類"

    /**
     * 標準の表示順序
     */
    val ORDERED_FORMS = listOf(
        FORM_SYMPHONY,
        FORM_CONCERTO,
        FORM_PIANO,
        FORM_STRINGS,
        FORM_CHAMBER,
        FORM_VOCAL_OPERA,
        FORM_ORCHESTRAL,
        FORM_WIND_BRASS,
        FORM_OTHER
    )

    /**
     * TrackInfoから形式名を判定して返す
     */
    fun classify(track: TrackInfo): String {
        return classify(
            grouping = track.grouping,
            title = track.title,
            album = track.album,
            folderName = track.folderName,
            folderUriString = track.folderUriString,
            uri = track.uri,
            genre = track.genre,
            composer = track.composer
        )
    }

    /**
     * ハイブリッド分類ロジック
     * 1. 明示的なGrouping/TIT1タグがあれば最優先
     * 2. なければ、フォルダ名・アルバム名・タイトル・ジャンル・パスからキーワード自動判別
     * 3. 該当なしは「その他・未分類」
     */
    fun classify(
        grouping: String = "",
        title: String = "",
        album: String = "",
        folderName: String = "",
        folderUriString: String? = null,
        uri: String = "",
        genre: String = "",
        composer: String = ""
    ): String {
        // 1. ID3 Grouping (TIT1 / GRP1) が設定されている場合
        val trimmedGrouping = grouping.trim()
        if (trimmedGrouping.isNotBlank()) {
            val mapped = mapKeywordToStandardForm(trimmedGrouping)
            if (mapped != null) return mapped
            // ユーザー独自のグループ名がタグ付けされている場合はそれを尊重
            return trimmedGrouping
        }

        // 2. パス・フォルダ名の解析（URIデコード処理を含む）
        val decodedPathSegments = extractDecodedSegments(folderUriString, uri, folderName)

        // 検索対象テキストを統合（小文字化）
        val combinedText = buildString {
            append(title).append(' ')
            append(album).append(' ')
            append(folderName).append(' ')
            append(genre).append(' ')
            append(composer).append(' ')
            for (segment in decodedPathSegments) {
                append(segment).append(' ')
            }
        }.lowercase()

        // 3. キーワード判定（優先順位順）

        // (1) 交響曲 (Symphony)
        if (matchesSymphony(combinedText)) {
            return FORM_SYMPHONY
        }

        // (2) 協奏曲 (Concerto)
        if (matchesConcerto(combinedText)) {
            return FORM_CONCERTO
        }

        // (3) 声楽・オペラ (Opera, Requiem, Choral, Vocal)
        if (matchesVocalOpera(combinedText)) {
            val hasVocalExplicit = combinedText.contains("opera") || combinedText.contains("歌劇") ||
                    combinedText.contains("requiem") || combinedText.contains("レクイエム") ||
                    combinedText.contains("cantata") || combinedText.contains("カンタータ") ||
                    combinedText.contains("missa") || combinedText.contains("ミサ") ||
                    combinedText.contains("chor") || combinedText.contains("合唱") ||
                    combinedText.contains("声楽") || combinedText.contains("歌曲") ||
                    combinedText.contains("lieder") || combinedText.contains("lied")
            if (hasVocalExplicit) {
                return FORM_VOCAL_OPERA
            }
        }

        // (4) ピアノ (Piano Solo / Sonatas / Impromptu / Nocturne)
        if (matchesPiano(combinedText)) {
            return FORM_PIANO
        }

        // (5) 室内楽 (Chamber / String Quartet / Trio / Quintet)
        if (matchesChamber(combinedText)) {
            return FORM_CHAMBER
        }

        // (6) ヴァイオリン・弦楽器 (Violin, Cello, Viola, Strings)
        if (matchesStrings(combinedText)) {
            return FORM_STRINGS
        }

        // (7) 吹奏楽・管楽器 (Wind, Brass, Woodwind)
        if (matchesWindBrass(combinedText)) {
            return FORM_WIND_BRASS
        }

        // (8) 管弦楽曲・組曲 (Orchestral, Suites, Overtures, Symphonic Poems)
        if (matchesOrchestral(combinedText)) {
            return FORM_ORCHESTRAL
        }

        // 声楽の広義マッチ（aria等）
        if (matchesVocalOpera(combinedText)) {
            return FORM_VOCAL_OPERA
        }

        return FORM_OTHER
    }

    private fun extractDecodedSegments(folderUriString: String?, uri: String, folderName: String): List<String> {
        val list = mutableListOf<String>()
        if (folderName.isNotBlank()) {
            list.add(folderName)
        }
        val rawCandidates = listOfNotNull(folderUriString, uri)
        for (raw in rawCandidates) {
            try {
                val decoded = Uri.decode(raw)
                val parts = decoded.split('/', ':', '%', '\\').filter { it.isNotBlank() }
                list.addAll(parts)
            } catch (_: Throwable) {
                val parts = raw.split('/', ':', '\\').filter { it.isNotBlank() }
                list.addAll(parts)
            }
        }
        return list
    }

    private fun mapKeywordToStandardForm(text: String): String? {
        val lower = text.lowercase()
        return when {
            matchesSymphony(lower) -> FORM_SYMPHONY
            matchesConcerto(lower) -> FORM_CONCERTO
            matchesVocalOpera(lower) -> FORM_VOCAL_OPERA
            matchesPiano(lower) -> FORM_PIANO
            matchesChamber(lower) -> FORM_CHAMBER
            matchesStrings(lower) -> FORM_STRINGS
            matchesWindBrass(lower) -> FORM_WIND_BRASS
            matchesOrchestral(lower) -> FORM_ORCHESTRAL
            else -> null
        }
    }

    private fun matchesSymphony(text: String): Boolean {
        return text.contains("交響曲") ||
                text.contains("シンフォニ") ||
                text.contains("symphon") ||
                text.contains("sinfoni") ||
                Regex("""\bsym(\.|\b)""").containsMatchIn(text) ||
                Regex("""\bsinf(\.|\b)""").containsMatchIn(text)
    }

    private fun matchesConcerto(text: String): Boolean {
        return text.contains("協奏曲") ||
                text.contains("コンチェルト") ||
                text.contains("concerto") ||
                text.contains("concertos") ||
                text.contains("konzert") ||
                text.contains("concertino") ||
                Regex("""\bcto(\.|\b)""").containsMatchIn(text)
    }

    private fun matchesPiano(text: String): Boolean {
        return text.contains("ピアノ") ||
                text.contains("鍵盤") ||
                text.contains("チェンバロ") ||
                text.contains("ハープシコード") ||
                text.contains("クラヴィーア") ||
                text.contains("夜想曲") ||
                text.contains("ノクターン") ||
                text.contains("練習曲") ||
                text.contains("エチュード") ||
                text.contains("ポロネーズ") ||
                text.contains("マズルカ") ||
                text.contains("バラード") ||
                text.contains("即興曲") ||
                text.contains("前奏曲") ||
                text.contains("piano") ||
                text.contains("klavier") ||
                text.contains("pianoforte") ||
                text.contains("cembalo") ||
                text.contains("harpsichord") ||
                text.contains("clavier") ||
                text.contains("nocturne") ||
                text.contains("etude") ||
                text.contains("étude") ||
                text.contains("polonaise") ||
                text.contains("mazurka") ||
                text.contains("impromptu") ||
                text.contains("goldberg") ||
                text.contains("well-tempered") ||
                text.contains("平均律")
    }

    private fun matchesStrings(text: String): Boolean {
        return text.contains("ヴァイオリン") ||
                text.contains("バイオリン") ||
                text.contains("チェロ") ||
                text.contains("ヴィオラ") ||
                text.contains("ビオラ") ||
                text.contains("コントラバス") ||
                text.contains("ギター") ||
                text.contains("ハープ") ||
                text.contains("リュート") ||
                text.contains("弦楽") ||
                text.contains("violin") ||
                text.contains("violine") ||
                text.contains("violino") ||
                text.contains("cello") ||
                text.contains("violoncello") ||
                text.contains("viola") ||
                text.contains("contrabass") ||
                text.contains("double bass") ||
                text.contains("guitar") ||
                text.contains("gitarre") ||
                text.contains("harp") ||
                text.contains("lute")
    }

    private fun matchesChamber(text: String): Boolean {
        return text.contains("室内楽") ||
                text.contains("弦楽四重奏") ||
                text.contains("ピアノ三重奏") ||
                text.contains("ピアノ四重奏") ||
                text.contains("ピアノ五重奏") ||
                text.contains("四重奏") ||
                text.contains("五重奏") ||
                text.contains("三重奏") ||
                text.contains("二重奏") ||
                text.contains("弦楽五重奏") ||
                text.contains("string quartet") ||
                text.contains("quartet") ||
                text.contains("quintet") ||
                text.contains("trio") ||
                text.contains("duo") ||
                text.contains("quatuor") ||
                text.contains("quartett") ||
                text.contains("quintett") ||
                text.contains("chamber") ||
                text.contains("sonata") ||
                text.contains("sonate") ||
                text.contains("ソナタ")
    }

    private fun matchesVocalOpera(text: String): Boolean {
        return text.contains("オペラ") ||
                text.contains("歌劇") ||
                text.contains("声楽") ||
                text.contains("合唱") ||
                text.contains("レクイエム") ||
                text.contains("ミサ") ||
                text.contains("ミサ曲") ||
                text.contains("カンタータ") ||
                text.contains("オラトリオ") ||
                text.contains("アリア") ||
                text.contains("歌曲") ||
                text.contains("コーラス") ||
                text.contains("opera") ||
                text.contains("vocal") ||
                text.contains("choral") ||
                text.contains("chorus") ||
                text.contains("requiem") ||
                text.contains("missa") ||
                text.contains("cantata") ||
                text.contains("oratorio") ||
                text.contains("aria") ||
                text.contains("lieder") ||
                text.contains("lied") ||
                text.contains("chœur") ||
                text.contains("stabat mater") ||
                Regex("""\bmass\b""").containsMatchIn(text) ||
                Regex("""\bchor\b""").containsMatchIn(text)
    }

    private fun matchesWindBrass(text: String): Boolean {
        return text.contains("吹奏楽") ||
                text.contains("ブラス") ||
                text.contains("フルート") ||
                text.contains("オーボエ") ||
                text.contains("クラリネット") ||
                text.contains("ファゴット") ||
                text.contains("ホルン") ||
                text.contains("トランペット") ||
                text.contains("トロンボーン") ||
                text.contains("サックス") ||
                text.contains("木管") ||
                text.contains("金管") ||
                text.contains("リコーダー") ||
                text.contains("wind") ||
                text.contains("brass") ||
                text.contains("flute") ||
                text.contains("oboe") ||
                text.contains("clarinet") ||
                text.contains("bassoon") ||
                text.contains("horn") ||
                text.contains("trumpet") ||
                text.contains("trombone") ||
                text.contains("saxophone") ||
                text.contains("recorder")
    }

    private fun matchesOrchestral(text: String): Boolean {
        return text.contains("管弦楽") ||
                text.contains("オーケストラ") ||
                text.contains("組曲") ||
                text.contains("序曲") ||
                text.contains("交響詩") ||
                text.contains("行進曲") ||
                text.contains("セレナード") ||
                text.contains("舞曲") ||
                text.contains("ディヴェルティメント") ||
                text.contains("orchestra") ||
                text.contains("orchestral") ||
                text.contains("suite") ||
                text.contains("overture") ||
                text.contains("ouvertüre") ||
                text.contains("tone poem") ||
                text.contains("symphonic poem") ||
                text.contains("poème symphonique") ||
                text.contains("march") ||
                text.contains("marsch") ||
                text.contains("serenade") ||
                text.contains("danse") ||
                text.contains("dance") ||
                text.contains("divertimento")
    }
}
