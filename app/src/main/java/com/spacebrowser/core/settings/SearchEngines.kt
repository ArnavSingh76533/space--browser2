package com.spacebrowser.core.settings

data class SearchEngine(
    val id: String,
    val name: String,
    /** Query template containing `%s`. */
    val queryUrl: String,
    /** OpenSearch-style suggestion endpoint (`%s` placeholder), or null. */
    val suggestUrl: String? = null,
)

object SearchEngines {

    const val CUSTOM_ID = "custom"

    val all: List<SearchEngine> = listOf(
        SearchEngine(
            id = "ddg", name = "DuckDuckGo",
            queryUrl = "https://duckduckgo.com/?q=%s",
            suggestUrl = "https://ac.duckduckgo.com/ac/?q=%s&type=list",
        ),
        SearchEngine(
            id = "brave", name = "Brave Search",
            queryUrl = "https://search.brave.com/search?q=%s",
        ),
        SearchEngine(
            id = "startpage", name = "Startpage",
            queryUrl = "https://www.startpage.com/sp/search?query=%s",
        ),
        SearchEngine(
            id = "ecosia", name = "Ecosia",
            queryUrl = "https://www.ecosia.org/search?q=%s",
        ),
        SearchEngine(
            id = "google", name = "Google",
            queryUrl = "https://www.google.com/search?q=%s",
            suggestUrl = "https://suggestqueries.google.com/complete/search?client=firefox&q=%s",
        ),
        SearchEngine(
            id = "bing", name = "Bing",
            queryUrl = "https://www.bing.com/search?q=%s",
        ),
        SearchEngine(
            id = CUSTOM_ID, name = "Custom",
            queryUrl = "https://duckduckgo.com/?q=%s", // fallback until the user sets a template
        ),
    )

    fun byId(id: String): SearchEngine = all.firstOrNull { it.id == id } ?: all.first()

    fun resolveTemplate(settings: SpaceSettings): String {
        val engine = byId(settings.searchEngineId)
        return if (engine.id == CUSTOM_ID && settings.customSearchUrl.contains("%s")) {
            settings.customSearchUrl
        } else if (engine.id == CUSTOM_ID) {
            all.first().queryUrl
        } else engine.queryUrl
    }

    fun resolveSuggestUrl(settings: SpaceSettings): String? =
        if (!settings.searchSuggestions) null else byId(settings.searchEngineId).suggestUrl
}
