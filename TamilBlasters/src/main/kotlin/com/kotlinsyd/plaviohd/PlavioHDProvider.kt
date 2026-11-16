package com.kotlinsyd.plaviohd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Bridge provider: CloudStream in front, the PlavioHD Stremio addon behind.
 *
 *  search()   -> PlavioMeta search (IMDb-id based results)
 *  load()     -> PlavioMeta meta (movie info / series episodes)
 *  loadLinks()-> PlavioHD stream rows converted to ExtractorLinks,
 *                including the per-row proxy headers some sources need,
 *                plus subtitles from the PlavioSubs addon.
 *
 * The `data` string passed between load() and loadLinks() is simply the
 * PlavioHD stream URL itself, e.g.
 *   https://hd.plavio.site/stream/movie/tt15398776.json
 *   https://hd.plavio.site/stream/series/tt5180504:3:5.json
 * built from the addon base URL in the plugin settings.
 */
class PlavioHDProvider : MainAPI() {
    override var mainUrl = "https://hd.plavio.site"
    override var name = "TamilBlasters" // GENERATED-PROVIDER-NAME
    override var lang = "en"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movie/trending-movies" to "Trending Movies",
        "series/trending-series" to "Trending Series",
        "movie/popular-movies" to "Popular Movies",
        "series/popular-series" to "Popular Series",
        "series/airing-today-series" to "Airing Today",
        "movie/critically-acclaimed-movies" to "Critically Acclaimed Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Return every row in one response so Home shows the full stacked
        // catalogue (CloudStream otherwise displays only the requested row).
        // Rows are fetched in parallel so Home opens at the speed of one row.
        val skip = (page - 1) * 20
        val lists = coroutineScope {
            mainPage.map { entry ->
                async {
                    val path = if (skip > 0) "${entry.data}/skip=$skip" else entry.data
                    val text = runCatching { app.get("$metaBase/catalog/$path.json").text }
                        .getOrNull() ?: return@async null
                    val dto = tryParseJson<CatalogDto>(text) ?: return@async null
                    val items = dto.metas.orEmpty().mapNotNull { it.toSearchResponse() }
                    if (items.isEmpty()) null else HomePageList(entry.name, items)
                }
            }.awaitAll().filterNotNull()
        }
        return newHomePageResponse(lists, lists.isNotEmpty())
    }

    private val metaBase = "https://meta.plavio.site"
    private val subsBase = "https://subs.plavio.site"

    private val addonBase: String
        get() = PlavioSettings.effectiveBaseUrl

    // region JSON shapes (Jackson; unknown fields ignored)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaItemDto(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("imdbId") val imdbId: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("year") val year: String? = null,
        @JsonProperty("releaseInfo") val releaseInfo: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchDto(
        @JsonProperty("results") val results: List<MetaItemDto>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CatalogDto(
        @JsonProperty("metas") val metas: List<MetaItemDto>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VideoDto(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaDto(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("background") val background: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseInfo") val releaseInfo: String? = null,
        @JsonProperty("runtime") val runtime: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("videos") val videos: List<VideoDto>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaWrapDto(
        @JsonProperty("meta") val meta: MetaDto? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProxyHeadersDto(
        @JsonProperty("request") val request: Map<String, String>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class HintsDto(
        @JsonProperty("filename") val filename: String? = null,
        @JsonProperty("proxyHeaders") val proxyHeaders: ProxyHeadersDto? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StreamDto(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("behaviorHints") val behaviorHints: HintsDto? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StreamsDto(
        @JsonProperty("streams") val streams: List<StreamDto>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SubItemDto(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val lang: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SubsDto(
        @JsonProperty("subtitles") val subtitles: List<SubItemDto>? = null
    )

    // endregion

    private fun MetaItemDto.toSearchResponse(): SearchResponse? {
        val id = id?.takeIf { it.startsWith("tt") }
            ?: imdbId?.takeIf { it.startsWith("tt") }
            ?: return null
        val title = name?.takeIf { it.isNotBlank() } ?: return null
        val yr = year?.toIntOrNull()
            ?: releaseInfo?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }
        return if (type == "series") {
            newTvSeriesSearchResponse(title, "$metaBase/meta/series/$id.json", TvType.TvSeries) {
                this.posterUrl = poster
                this.year = yr
            }
        } else {
            newMovieSearchResponse(title, "$metaBase/meta/movie/$id.json", TvType.Movie) {
                this.posterUrl = poster
                this.year = yr
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val text = runCatching { app.get("$metaBase/search.json?q=$encoded").text }
            .getOrNull() ?: return emptyList()
        val dto = tryParseJson<SearchDto>(text) ?: return emptyList()

        return dto.results.orEmpty().mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        // url is the meta URL built in search(): $metaBase/meta/<movie|series>/<imdb>.json
        val match = Regex("""/meta/(movie|series)/(tt\d+)\.json""").find(url) ?: return null
        val type = match.groupValues[1]
        val imdb = match.groupValues[2]

        val text = runCatching { app.get(url).text }.getOrNull() ?: return null
        val meta = tryParseJson<MetaWrapDto>(text)?.meta ?: return null
        val title = meta.name?.takeIf { it.isNotBlank() } ?: return null

        val year = meta.releaseInfo?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }
        val duration = meta.runtime?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        return if (type == "series") {
            val episodes = meta.videos.orEmpty().mapNotNull { video ->
                val season = video.season ?: return@mapNotNull null
                val episode = video.episode ?: return@mapNotNull null
                val videoId = video.id ?: "$imdb:$season:$episode"
                newEpisode("$addonBase/stream/series/$videoId.json") {
                    this.name = video.title ?: video.name
                    this.season = season
                    this.episode = episode
                    this.posterUrl = video.thumbnail
                    this.description = video.overview
                    addDate(video.released)
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = meta.poster
                this.backgroundPosterUrl = meta.background
                this.year = year
                this.plot = meta.description
                this.tags = meta.genres
                this.duration = duration
                this.score = Score.from(meta.imdbRating, 10)
                this.showStatus =
                    if (meta.status?.contains("end", ignoreCase = true) == true) {
                        ShowStatus.Completed
                    } else {
                        ShowStatus.Ongoing
                    }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "$addonBase/stream/movie/$imdb.json") {
                this.posterUrl = meta.poster
                this.backgroundPosterUrl = meta.background
                this.year = year
                this.plot = meta.description
                this.tags = meta.genres
                this.duration = duration
                this.score = Score.from(meta.imdbRating, 10)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data is the PlavioHD stream URL built in load()
        val text = runCatching { app.get(data).text }.getOrNull() ?: return false
        val dto = tryParseJson<StreamsDto>(text) ?: return false

        val seen = HashSet<String>()
        var emitted = 0
        dto.streams.orEmpty().forEach { row ->
            val link = row.url?.takeIf { it.isNotBlank() } ?: return@forEach
            if (!seen.add(link)) return@forEach

            val headers = row.behaviorHints?.proxyHeaders?.request.orEmpty()
            val referer = headers.entries
                .firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value.orEmpty()
            val provider = providerFor(row)
            val label = composeLabel(row, provider)
            val isHls = link.substringBefore('?').lowercase().endsWith(".m3u8")

            callback(
                newExtractorLink(
                    source = provider ?: name,
                    name = label,
                    url = link,
                    type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                    this.quality = parseQuality(row)
                    this.headers = headers
                }
            )
            emitted++
        }

        fetchSubtitles(data, subtitleCallback)

        return emitted > 0
    }

    /** Best-effort subtitles from the PlavioSubs addon; never fails loadLinks. */
    private suspend fun fetchSubtitles(data: String, subtitleCallback: (SubtitleFile) -> Unit) {
        val match = Regex("""/stream/(movie|series)/(.+)\.json$""").find(data) ?: return
        val type = match.groupValues[1]
        val id = match.groupValues[2]
        val text = runCatching { app.get("$subsBase/subtitles/$type/$id.json").text }
            .getOrNull() ?: return
        val dto = tryParseJson<SubsDto>(text) ?: return
        dto.subtitles.orEmpty().forEach { sub ->
            val subUrl = sub.url?.takeIf { it.isNotBlank() } ?: return@forEach
            val lang = sub.lang?.takeIf { it.isNotBlank() } ?: return@forEach
            subtitleCallback(newSubtitleFile(lang, subUrl))
        }
    }

    /** Source names worth scanning for, longest first; a few short ones are
     *  also ordinary words in titles, so they only count via the name line. */
    private val scanNames: List<String> =
        (PlavioSettings.ALL_SOURCES - setOf("Castle", "Dulo", "Movy", "Dexter") +
            listOf("CNCVerse")).sortedByDescending { it.length }

    private val scanAliases = mapOf(
        "CNCVerse" to "MultiMovies"
    )

    /**
     * The source a stream row belongs to, read off the row itself: the
     * addon's plain name line ("Title Year Source Quality") names it
     * directly whenever no configure-page formatter has reshaped that line;
     * otherwise the name, the file name and the title are scanned for a
     * known source name. Null when nothing gives the source away — the
     * caller then files the link under the plugin's own name, as before.
     */
    private fun providerFor(row: StreamDto): String? {
        val nameLine = row.name?.takeIf { it.isNotBlank() }
        if (nameLine != null) {
            val m = Regex("""^\S.*?\s+\d{4}\s+(.+?)\s+(?:2160p|1440p|1080p|720p|480p|360p|4K|FHD|HD|SD)\s*$""")
                .find(nameLine)
            if (m != null) return m.groupValues[1].trim()
        }
        val haystacks = listOfNotNull(
            nameLine,
            row.behaviorHints?.filename,
            row.title
        )
        for (hay in haystacks) {
            for (candidate in scanNames) {
                val pattern = Regex(
                    """(?<![A-Za-z0-9])""" + Regex.escape(candidate) + """(?![A-Za-z0-9])""",
                    RegexOption.IGNORE_CASE
                )
                if (pattern.containsMatchIn(hay)) return scanAliases[candidate] ?: candidate
            }
        }
        return null
    }

    /**
     * CloudStream shows one text per link, so compose it here in the plugin:
     * the addon's title block carries the file/release name (first line) and
     * the size (in the tag lines), while the plain name line alone can be
     * little more than a quality tag under some addon formatters. The addon
     * itself is untouched; this only changes how the plugin labels its links.
     */
    private fun composeLabel(row: StreamDto, provider: String?): String {
        val lines = row.title?.lineSequence()
            ?.map { it.trim() }?.filter { it.isNotBlank() }?.toList().orEmpty()
        val firstLine = lines.firstOrNull()
        val nameLine = row.name?.takeIf { it.isNotBlank() }
        val size = lines.firstNotNullOfOrNull { line ->
            Regex("""\d+(?:\.\d+)?\s?(?:GB|MB)""").find(line)?.value?.replace(" ", "")
        }
        val firstIsName = firstLine != null && firstLine.length >= 12 &&
            !firstLine.contains('·') &&
            Regex("""[A-Za-z]{3,}""").containsMatchIn(firstLine)
        val base = when {
            firstIsName && provider != null && !firstLine.contains(provider, ignoreCase = true) ->
                "$provider · $firstLine"
            firstIsName -> firstLine
            nameLine != null -> nameLine
            firstLine != null -> firstLine
            else -> name
        }
        return if (size != null && !base.contains(size, ignoreCase = true)) "$base · $size" else base
    }

    private fun parseQuality(row: StreamDto): Int {
        val text = listOfNotNull(row.name, row.title, row.behaviorHints?.filename)
            .joinToString("\n")
        val match = Regex("""(2160|1440|1080|720|480|360|240|144)\s*p""", RegexOption.IGNORE_CASE)
            .find(text)
        if (match != null) {
            return when (match.groupValues[1]) {
                "2160" -> Qualities.P2160.value
                "1440" -> Qualities.P1440.value
                "1080" -> Qualities.P1080.value
                "720" -> Qualities.P720.value
                "480" -> Qualities.P480.value
                "360" -> Qualities.P360.value
                "240" -> Qualities.P240.value
                "144" -> Qualities.P144.value
                else -> Qualities.Unknown.value
            }
        }
        if (Regex("""\b(4k|uhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return Qualities.P2160.value
        }
        return Qualities.Unknown.value
    }
}
