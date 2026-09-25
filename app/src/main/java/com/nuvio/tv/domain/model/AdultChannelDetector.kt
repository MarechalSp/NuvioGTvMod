package com.nuvio.tv.domain.model

object AdultChannelDetector {

    // Canais e marcas conhecidas de conteúdo adulto (Brasil, Portugal e Internacional)
    private val ADULT_CHANNEL_BRANDS = setOf(
        "playboy", "playboy tv", "sexy hot", "sexyhot", "venus", "sextreme",
        "hustler", "hustler tv", "blue hustler", "brazzers", "brazzers tv",
        "dorcel", "dorcel tv", "marc dorcel", "penthouse", "penthouse gold",
        "penthouse quickies", "penthouse passion", "penthouse black",
        "private tv", "private spice", "vivid", "vivid tv", "vivid red",
        "redlight", "redlight tv", "redlight hd", "babes tv", "reality kings",
        "evil angel", "x-mo", "xmo", "exotica", "exotica tv", "teleclube",
        "man-x", "manx", "forman", "for man", "centoxcento", "pink o",
        "pink o tv", "passion xxx", "the adult channel", "adult channel",
        "superone", "super one", "dusk tv", "extasy tv", "xxl", "sct",
        "satisfaction channel", "daring tv", "frenchlover", "bang bros",
        "bangbros", "naughty america", "mofos", "onlyfans", "tgirl tv",
        "hardx", "vixen", "blacked", "tushy", "sex prive", "sex privé",
        "sexprive", "sexprivé", "canal adulto", "erox", "eroxxx",
        "hot pleasures", "adultzone", "hot tv", "hot x", "taboo",
        "amatuer tv", "milf", "fetish", "gay tv", "trans tv"
    )

    // Palavras-chave e termos de categoria/gênero/rótulo
    private val ADULT_KEYWORDS = setOf(
        "adult", "adulto", "adultos", "adulte", "erotico", "erótico",
        "eroticos", "eróticos", "erotica", "erótica", "erotic", "erotika",
        "porno", "pornô", "porn", "pornografia", "nsfw", "hardcore",
        "softcore", "hentai", "xxx", "xxxx", "18+", "+18"
    )

    // Regex para detectar tags como [18+], (18+), +18, [XXX], etc. em nomes de canais
    private val ADULT_REGEX_PATTERNS = listOf(
        Regex("""(?i)\b(xxx+|18\+|\+18|adulto?s?|er[oó]tico?s?|porn[oô]?)\b"""),
        Regex("""(?i)[\[\(\{\<](xxx|18\+|\+18|adult)[\]\)\}\>]"""),
        Regex("""(?i)\b(playboy|sexy\s*hot|sex\s*priv[eé]|brazzers|hustler|dorcel|penthouse|redlight|venus\s*tv|sextreme)\b""")
    )

    // Termos que possuem a palavra "adult" mas NÃO são eróticos (Ex: Adult Swim do Cartoon Network)
    private val WHITELIST = setOf("adult swim", "adultswim", "the adults")

    /**
     * Verifica se um canal de TV é considerado conteúdo adulto / erótico.
     */
    fun isAdult(channel: TvChannelItem): Boolean {
        val cleanName = channel.name.lowercase().trim()
        if (isWhitelisted(cleanName)) return false

        // 1. Verifica genres / categorias
        for (genre in channel.genres) {
            if (isAdultText(genre)) return true
        }

        // 2. Verifica nome ou ID do catálogo
        if (isAdultText(channel.catalogName) || isAdultText(channel.catalogId)) {
            return true
        }

        // 3. Verifica nome do canal
        if (isAdultText(cleanName)) {
            return true
        }

        // 4. Verifica descrição se houver
        channel.description?.let { desc ->
            if (desc.isNotBlank() && isAdultText(desc.take(120))) {
                return true
            }
        }

        return false
    }

    /**
     * Verifica se uma categoria específica é de conteúdo adulto.
     */
    fun isAdultCategory(categoryName: String): Boolean {
        if (isWhitelisted(categoryName.lowercase())) return false
        return isAdultText(categoryName)
    }

    private fun isWhitelisted(text: String): Boolean {
        for (white in WHITELIST) {
            if (text.contains(white)) return true
        }
        return false
    }

    private fun isAdultText(rawText: String?): Boolean {
        if (rawText.isNullOrBlank()) return false
        val clean = rawText.lowercase().trim()

        if (isWhitelisted(clean)) return false

        // 1. Delimitadores comuns em IPTV para encontrar palavras isoladas
        val words = clean.split(Regex("[\\s\\[\\]\\(\\)\\{\\}\\-_/,.:;+*|#\\?!]+"))
        for (word in words) {
            if (word in ADULT_KEYWORDS) return true
        }

        // 2. Substrings adultas expressivas e símbolos (+18, 18+, 🔞, xxx)
        if (clean.contains("18+") || clean.contains("+18") || clean.contains("🔞") ||
            clean.contains("xxx") || clean.contains("porn") || clean.contains("erótic") ||
            clean.contains("erotic") || clean.contains("adulto") || clean.contains("adults")
        ) {
            return true
        }

        // 3. Marcas conhecidas
        for (brand in ADULT_CHANNEL_BRANDS) {
            if (clean.contains(brand)) return true
        }

        // 4. Regex patterns
        for (regex in ADULT_REGEX_PATTERNS) {
            if (regex.containsMatchIn(clean)) return true
        }

        return false
    }
}
