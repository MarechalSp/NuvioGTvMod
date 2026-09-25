package com.nuvio.tv.data.repository.epg

data class XmlTvChannel(
    val id: String,
    val displayNames: List<String> = emptyList(),
    val iconUrl: String? = null,
)

data class XmlTvParseResult(
    val channels: Map<String, XmlTvChannel>,
    val programsByChannelId: Map<String, List<EpgProgram>>,
    val totalProgramsParsed: Int,
)

object XmlTvParser {

    /**
     * Faz o parse de um conteúdo XMLTV com scanner sequencial de alta performance.
     * Evita alocações massivas de Regex em strings grandes e descarta
     * antecipadamente eventos fora da janela de retenção (48h passado até 4 dias futuro).
     */
    fun parse(
        xmlContent: String,
        referenceEpochMs: Long = System.currentTimeMillis(),
        windowPastHours: Long = 48L,
        windowFutureHours: Long = 96L,
    ): XmlTvParseResult {
        val minEpoch = referenceEpochMs - (windowPastHours * 3600_000L)
        val maxEpoch = referenceEpochMs + (windowFutureHours * 3600_000L)

        val channels = mutableMapOf<String, XmlTvChannel>()
        val programsMap = mutableMapOf<String, MutableList<EpgProgram>>()
        var totalParsed = 0

        val len = xmlContent.length

        // PASSO 1: Parse de Canais (<channel ...> ... </channel>)
        var cursor = 0
        while (cursor < len) {
            val start = xmlContent.indexOf("<channel", cursor)
            if (start == -1) break

            val tagClose = xmlContent.indexOf('>', start)
            if (tagClose == -1) break

            val isSelfClosing = xmlContent[tagClose - 1] == '/'
            val end = if (isSelfClosing) tagClose + 1 else xmlContent.indexOf("</channel>", tagClose)
            if (end == -1) break

            val channelHeader = xmlContent.substring(start, tagClose)
            val channelId = extractAttribute(channelHeader, "id")

            if (!channelId.isNullOrBlank()) {
                val names = mutableListOf<String>()
                var iconUrl: String? = null

                if (!isSelfClosing) {
                    val body = xmlContent.substring(tagClose + 1, end)

                    var nameCursor = 0
                    while (nameCursor < body.length) {
                        val nStart = body.indexOf("<display-name", nameCursor)
                        if (nStart == -1) break
                        val nClose = body.indexOf('>', nStart)
                        if (nClose == -1) break
                        val nEnd = body.indexOf("</display-name>", nClose)
                        if (nEnd == -1) break

                        val nameText = body.substring(nClose + 1, nEnd).decodeXml().trim()
                        if (nameText.isNotBlank()) {
                            names.add(nameText)
                        }
                        nameCursor = nEnd + 15
                    }

                    val iconStart = body.indexOf("<icon", 0)
                    if (iconStart != -1) {
                        val iconClose = body.indexOf('>', iconStart)
                        if (iconClose != -1) {
                            val iconHeader = body.substring(iconStart, iconClose)
                            iconUrl = extractAttribute(iconHeader, "src")
                        }
                    }
                }

                channels[channelId] = XmlTvChannel(
                    id = channelId,
                    displayNames = names,
                    iconUrl = iconUrl,
                )
            }

            cursor = if (isSelfClosing) end else end + 10
        }

        // PASSO 2: Parse de Programas (<programme start="..." stop="..." channel="..."> ... </programme>)
        cursor = 0
        while (cursor < len) {
            val start = xmlContent.indexOf("<programme", cursor)
            if (start == -1) break

            val tagClose = xmlContent.indexOf('>', start)
            if (tagClose == -1) break

            val isSelfClosing = xmlContent[tagClose - 1] == '/'
            val end = if (isSelfClosing) tagClose + 1 else xmlContent.indexOf("</programme>", tagClose)
            if (end == -1) break

            val progHeader = xmlContent.substring(start, tagClose)
            val channelId = extractAttribute(progHeader, "channel")
            val startStr = extractAttribute(progHeader, "start")
            val stopStr = extractAttribute(progHeader, "stop")

            if (!channelId.isNullOrBlank() && !startStr.isNullOrBlank() && !stopStr.isNullOrBlank()) {
                val startEpoch = parseXmlTvDate(startStr)
                val stopEpoch = parseXmlTvDate(stopStr)

                if (startEpoch != null && stopEpoch != null && stopEpoch > startEpoch) {
                    if (stopEpoch >= minEpoch && startEpoch <= maxEpoch) {
                        var title: String? = null
                        var desc: String? = null
                        var category: String? = null
                        var iconUrl: String? = null

                        if (!isSelfClosing) {
                            val body = xmlContent.substring(tagClose + 1, end)

                            val tStart = body.indexOf("<title", 0)
                            if (tStart != -1) {
                                val tClose = body.indexOf('>', tStart)
                                val tEnd = if (tClose != -1) body.indexOf("</title>", tClose) else -1
                                if (tEnd != -1) {
                                    title = body.substring(tClose + 1, tEnd).decodeXml().trim()
                                }
                            }

                            val dStart = body.indexOf("<desc", 0)
                            if (dStart != -1) {
                                val dClose = body.indexOf('>', dStart)
                                val dEnd = if (dClose != -1) body.indexOf("</desc>", dClose) else -1
                                if (dEnd != -1) {
                                    desc = body.substring(dClose + 1, dEnd).decodeXml().trim()
                                }
                            }

                            val cStart = body.indexOf("<category", 0)
                            if (cStart != -1) {
                                val cClose = body.indexOf('>', cStart)
                                val cEnd = if (cClose != -1) body.indexOf("</category>", cClose) else -1
                                if (cEnd != -1) {
                                    category = body.substring(cClose + 1, cEnd).decodeXml().trim()
                                }
                            }

                            val iStart = body.indexOf("<icon", 0)
                            if (iStart != -1) {
                                val iClose = body.indexOf('>', iStart)
                                if (iClose != -1) {
                                    val iHeader = body.substring(iStart, iClose)
                                    iconUrl = extractAttribute(iHeader, "src")
                                }
                            }
                        }

                        if (!title.isNullOrBlank()) {
                            val program = EpgProgram(
                                id = "$channelId:$startEpoch",
                                channelId = channelId,
                                title = title,
                                description = desc,
                                startEpochMs = startEpoch,
                                endEpochMs = stopEpoch,
                                category = category,
                                iconUrl = iconUrl,
                            )
                            programsMap.getOrPut(channelId) { mutableListOf() }.add(program)
                            totalParsed++
                        }
                    }
                }
            }

            cursor = if (isSelfClosing) end else end + 12
        }

        val sortedPrograms = programsMap.mapValues { (_, list) ->
            list.sortedBy { it.startEpochMs }
        }

        return XmlTvParseResult(
            channels = channels,
            programsByChannelId = sortedPrograms,
            totalProgramsParsed = totalParsed,
        )
    }

    fun extractAttribute(header: String, attrName: String): String? {
        val patternDouble = "$attrName=\""
        val idxD = header.indexOf(patternDouble)
        if (idxD != -1) {
            val vStart = idxD + patternDouble.length
            val vEnd = header.indexOf('"', vStart)
            if (vEnd != -1) {
                return header.substring(vStart, vEnd).trim()
            }
        }

        val patternSingle = "$attrName='"
        val idxS = header.indexOf(patternSingle)
        if (idxS != -1) {
            val vStart = idxS + patternSingle.length
            val vEnd = header.indexOf('\'', vStart)
            if (vEnd != -1) {
                return header.substring(vStart, vEnd).trim()
            }
        }

        return null
    }

    fun String.decodeXml(): String {
        if (!this.contains('&')) return this
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#x26;", "&")
            .replace("&#x27;", "'")
    }

    fun parseXmlTvDate(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (trimmed.length < 14) return null

        return runCatching {
            val year = trimmed.substring(0, 4).toInt()
            val month = trimmed.substring(4, 6).toInt()
            val day = trimmed.substring(6, 8).toInt()
            val hour = trimmed.substring(8, 10).toInt()
            val minute = trimmed.substring(10, 12).toInt()
            val second = trimmed.substring(12, 14).toInt()

            var tzOffsetMinutes = 0
            if (trimmed.length >= 19) {
                val tzPart = trimmed.substring(14).trim()
                if (tzPart.isNotEmpty()) {
                    val sign = if (tzPart.startsWith("-")) -1 else 1
                    val tzDigits = tzPart.removePrefix("+").removePrefix("-").trim()
                    if (tzDigits.length >= 4) {
                        val tzHours = tzDigits.substring(0, 2).toIntOrNull() ?: 0
                        val tzMins = tzDigits.substring(2, 4).toIntOrNull() ?: 0
                        tzOffsetMinutes = sign * (tzHours * 60 + tzMins)
                    }
                }
            }

            val epochDays = daysFromCivil(year, month, day)
            val totalSeconds = (epochDays * 86400L) + (hour * 3600L) + (minute * 60L) + second - (tzOffsetMinutes * 60L)
            totalSeconds * 1000L
        }.getOrNull()
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        var y = year
        val m = month
        val d = day
        y -= if (m <= 2) 1 else 0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
