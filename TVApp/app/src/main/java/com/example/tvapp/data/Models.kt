package com.example.tvapp.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String = "general",
    val epgId: String? = null,
    val channelImageUrl: String? = null,
    val fallbackStreamUrls: List<String> = emptyList(),
    val epgHref: String? = null,
    val currentProgramTitle: String? = null,
    val currentProgramStart: Long? = null,
    val currentProgramEnd: Long? = null
)

data class Program(
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val iconUrl: String? = null
) {
    fun isLive(currentTime: Long): Boolean = currentTime in startTime..endTime

    fun getDuration(): Long = endTime - startTime

    fun getElapsedTime(currentTime: Long): Long = if (currentTime > startTime) currentTime - startTime else 0
}

object ChannelList {

    private const val NG = "https://zabava-htlive.cdn.ngenix.net/hls/"

    var channels: List<Channel> = emptyList()
        private set

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
    }

    val hardcodedChannels: List<Channel> = listOf(
        Channel(
            id = "c1r",
            name = "Первый канал",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/1.png",
            streamUrl = "https://cdn.ntv.ru/vitrina18/index.m3u8",
            category = "federal",
            epgId = "c1r",
            fallbackStreamUrls = listOf(
                "http://46.32.176.50/perviy/index.m3u8",
                NG + "CH_1TVSD/variant.m3u8",
                NG + "CH_1TVSD_2/variant.m3u8",
                NG + "CH_1TVSD_3/variant.m3u8",
                NG + "CH_1TVSD_4/variant.m3u8",
                NG + "CH_1TVSD_6/variant.m3u8",
                NG + "CH_1TVSD_8/variant.m3u8"
            )
        ),
        Channel(
            id = "rossiya1",
            name = "Россия 1",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/ros-1.png",
            streamUrl = "https://live.smotrim.ru/vgtrk/0/russia1-hd/index.m3u8",
            category = "federal",
            epgId = "rossiya1",
            fallbackStreamUrls = listOf(
                "https://cdn.ntv.ru/vitrina10/index.m3u8",
                "https://stream.smotrim.ru/hls2/russia_hd/playlist_6.m3u8",
                NG + "CH_RUSSIA1/variant.m3u8"
            )
        ),
        Channel(
            id = "ntv",
            name = "НТВ",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/ntv.png",
            streamUrl = "https://cdn.ntv.ru/ntv0_hd/index.m3u8",
            category = "federal",
            epgId = "ntv",
            fallbackStreamUrls = listOf(
                "https://cdn.ntv.ru/ntv1/playlist.m3u8",
                NG + "CH_NTV/variant.m3u8",
                NG + "CH_NTV_2/variant.m3u8",
                NG + "CH_NTV_4/variant.m3u8",
                NG + "CH_NTV_7/variant.m3u8"
            )
        ),
        Channel(
            id = "5tv",
            name = "Пятый канал",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/5.png",
            streamUrl = "https://cdn.ntv.ru/vitrina8/index.m3u8",
            category = "federal",
            epgId = "5tv",
            fallbackStreamUrls = listOf(
                "http://46.32.176.50/5kanal/index.m3u8",
                NG + "CH_5TV/variant.m3u8"
            )
        ),
        Channel(
            id = "kultura",
            name = "Культура",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/ros-kult.png",
            streamUrl = "https://live.smotrim.ru/vgtrk/0/kultura-hd/index.m3u8",
            category = "culture",
            epgId = "kultura",
            fallbackStreamUrls = listOf(
                "https://cdn.ntv.ru/vitrina12/index.m3u8",
                "http://stream.mcquack.net/229/index.m3u8"
            )
        ),
        Channel(
            id = "zvezda",
            name = "Звезда",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/zvezda.png",
            streamUrl = "https://cdn.ntv.ru/vitrina2/index.m3u8",
            category = "federal",
            epgId = "zvezda",
            fallbackStreamUrls = listOf(
                "http://51.158.144.33:2021/zvezda/index.m3u8",
                NG + "CH_ZVEZDA/variant.m3u8",
                NG + "CH_ZVEZDA_2/variant.m3u8",
                NG + "CH_ZVEZDA_7/variant.m3u8"
            )
        ),
        Channel(
            id = "pz",
            name = "Пятница!",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/pyatnica.png",
            streamUrl = "https://cdn.ntv.ru/vitrina7/index.m3u8",
            category = "entertainment",
            epgId = "pz",
            fallbackStreamUrls = listOf(
                "http://stream.mcquack.net/181/index.m3u8",
                "https://fs.uplink.kz/bolshaya_pyatnica/mono.m3u8?token=onlinetv"
            )
        ),
        Channel(
            id = "sts",
            name = "СТС",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/sts.png",
            streamUrl = "https://cdn.ntv.ru/vitrina14/index.m3u8",
            category = "entertainment",
            epgId = "sts",
            fallbackStreamUrls = listOf(
                "http://tshift-1.telecoma.tv/sts/index.m3u8",
                NG + "CH_STS/variant.m3u8",
                NG + "CH_STS_2/variant.m3u8",
                NG + "CH_STS_4/variant.m3u8",
                NG + "CH_STS_7/variant.m3u8"
            )
        ),
        Channel(
            id = "domashniy",
            name = "Домашний",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/domashniy.png",
            streamUrl = "https://cdn.ntv.ru/vitrina1/index.m3u8",
            category = "entertainment",
            epgId = "domashniy",
            fallbackStreamUrls = listOf(
                "http://stream.mcquack.net/227/index.m3u8"
            )
        ),
        Channel(
            id = "tnt",
            name = "ТНТ",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/tnt.png",
            streamUrl = "https://cdn.ntv.ru/vitrina17/index.m3u8",
            category = "entertainment",
            epgId = "tnt",
            fallbackStreamUrls = listOf(
                "http://stream.mcquack.net/135/index.m3u8",
                NG + "CH_TNT/variant.m3u8",
                NG + "CH_TNT_2/variant.m3u8",
                NG + "CH_TNT_4/variant.m3u8",
                NG + "CH_TNT_7/variant.m3u8"
            )
        ),
        Channel(
            id = "ren",
            name = "РЕН ТВ",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/ren-tv.png",
            streamUrl = "https://cdn.ntv.ru/vitrina9/index.m3u8",
            category = "federal",
            epgId = "ren",
            fallbackStreamUrls = listOf(
                "http://46.32.176.50/rentv/index.m3u8",
                NG + "CH_RENTV/variant.m3u8"
            )
        ),
        Channel(
            id = "karusel",
            name = "Карусель",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/karusel.png",
            streamUrl = "https://cdn.ntv.ru/vitrina20/index.m3u8",
            category = "kids",
            epgId = "karusel",
            fallbackStreamUrls = listOf(
                "http://185.37.150.46/Karusel/index.m3u8",
                NG + "CH_KARUSEL/variant.m3u8",
                NG + "CH_KARUSEL_4/variant.m3u8",
                NG + "CH_KARUSEL_7/variant.m3u8"
            )
        ),
        Channel(
            id = "match",
            name = "Матч ТВ",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/match.png",
            streamUrl = "https://cdn.ntv.ru/vitrina4/index.m3u8",
            category = "sport",
            epgId = "match",
            fallbackStreamUrls = listOf(
                "http://46.32.176.50/matchtv/index.m3u8"
            )
        ),
        Channel(
            id = "rossiya24",
            name = "Россия 24",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/ros-24.png",
            streamUrl = "https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8",
            category = "news",
            epgId = "rossiya24",
            fallbackStreamUrls = listOf(
                "https://cdn.ntv.ru/vitrina11/index.m3u8",
                "http://77.232.131.211/Rossiya24/index.m3u8"
            )
        ),
        Channel(
            id = "tvc",
            name = "ТВ Центр",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/tvc.png",
            streamUrl = "https://cdn.ntv.ru/vitrina15/index.m3u8",
            category = "federal",
            epgId = "tvc",
            fallbackStreamUrls = listOf(
                "https://stream8.cinerama.uz/1281/tracks-v1a1/mono.m3u8",
                NG + "CH_TVC/variant.m3u8"
            )
        ),
        Channel(
            id = "spas",
            name = "СПАС",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/spas.png",
            streamUrl = "https://cdn.ntv.ru/vitrina13/index.m3u8",
            category = "religious",
            epgId = "spas",
            fallbackStreamUrls = listOf(
                "http://stream.mcquack.net/232/index.m3u8",
                NG + "CH_SPAS/variant.m3u8",
                NG + "CH_SPAS_2/variant.m3u8",
                NG + "CH_SPAS_7/variant.m3u8"
            )
        ),
        Channel(
            id = "tv3",
            name = "ТВ-3",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/tv3.png",
            streamUrl = "https://cdn.ntv.ru/vitrina16/index.m3u8",
            category = "entertainment",
            epgId = "tv3"
        ),
        Channel(
            id = "2x2",
            name = "2х2",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/fa/3d/fa3da72a76f79ed22bbbe06b17c3729b.png?size=240&quality=95",
            streamUrl = "https://bl.rutube.ru/livestream/392b4686b770bae2da6bf5ac4574add5/index.m3u8?e=2068731801&s=tenr-yHXUv1wibfka78s2A&scheme=https",
            category = "entertainment",
            epgId = "2x2"
        ),
        Channel(
            id = "mir",
            name = "МИР",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/mir.png",
            streamUrl = "https://cdn.ntv.ru/vitrina3/index.m3u8",
            category = "news",
            epgId = "mir",
            fallbackStreamUrls = listOf(
                NG + "CH_MIR/variant.m3u8",
                NG + "CH_MIR_2/variant.m3u8",
                NG + "CH_MIR_4/variant.m3u8",
                NG + "CH_MIR_7/variant.m3u8"
            )
        ),
        Channel(
            id = "otv",
            name = "ОТР",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/otr.png",
            streamUrl = "https://cdn.ntv.ru/vitrina19/index.m3u8",
            category = "regional",
            epgId = "otv",
            fallbackStreamUrls = listOf(
                NG + "CH_OTR/variant.m3u8"
            )
        ),
        Channel(
            id = "che",
            name = "Че",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/7e/de/7ede7ab4fd8d0539cf4ecd4f8b49a7a1.png?size=240&quality=95",
            streamUrl = "http://flussonic.linkintel.ru/che/index.m3u8",
            category = "entertainment",
            epgId = "che",
            fallbackStreamUrls = listOf(
                NG + "CH_PERETZ/variant.m3u8",
                NG + "CH_PERETZ_7/variant.m3u8"
            )
        ),
        Channel(
            id = "dom_kino",
            name = "Дом Кино",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/%D0%9B%D0%BE%D0%B3%D0%BE%D1%82%D0%B8%D0%BF_%D0%BA%D0%B0%D0%BD%D0%B0%D0%BB%D0%B0_%D0%94%D0%BE%D0%BC_%D0%9A%D0%B8%D0%BD%D0%BE.png/960px-%D0%9B%D0%BE%D0%B3%D0%BE%D1%82%D0%B8%D0%BF_%D0%BA%D0%B0%D0%BD%D0%B0%D0%BB%D0%B0_%D0%94%D0%BE%D0%BC_%D0%9A%D0%B8%D0%BD%D0%BE.png",
            streamUrl = "http://stream.mcquack.net/236/index.m3u8",
            category = "movies",
            epgId = "dom_kino",
            fallbackStreamUrls = listOf(
                "https://fs.uplink.kz/dom_kino/mono.m3u8?token=onlinetv"
            )
        ),
        Channel(
            id = "telecafe",
            name = "Телекафе",
            logoUrl = "https://www.telecafe.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/26.m3u8",
            category = "lifestyle",
            epgId = "telecafe"
        ),
        Channel(
            id = "mult",
            name = "МУЛЬТ",
            logoUrl = "https://i.imgur.com/xi351Fx.png",
            streamUrl = "https://fs.uplink.kz/mult/mono.m3u8?token=onlinetv",
            category = "kids",
            epgId = "mult"
        ),
        Channel(
            id = "muztv",
            name = "МУЗ-ТВ",
            logoUrl = "https://api.ntv.ru/vitrina/static/images/logo/muz-tv.png",
            streamUrl = "https://cdn.ntv.ru/vitrina5/index.m3u8",
            category = "music",
            epgId = "muztv",
            fallbackStreamUrls = listOf(
                NG + "CH_MUZTV/variant.m3u8",
                NG + "CH_MUZTV_2/variant.m3u8",
                NG + "CH_MUZTV_4/variant.m3u8",
                NG + "CH_MUZTV_7/variant.m3u8"
            )
        ),
        Channel(
            id = "tv1000",
            name = "TV1000",
            logoUrl = "https://i.imgur.com/ZMsyjSr.png",
            streamUrl = "http://stream.mcquack.net/110/index.m3u8",
            category = "movies",
            epgId = "tv1000"
        ),
        Channel(
            id = "ohota",
            name = "Охота и Рыбалка",
            logoUrl = "https://i.imgur.com/nZwBaeW.png",
            streamUrl = "https://fs.uplink.kz/ohota_i_ribalka/mono.m3u8?token=onlinetv",
            category = "hunting_fishing",
            epgId = "ohota"
        ),
        Channel(
            id = "rybolov",
            name = "Рыбалка TV",
            logoUrl = "https://www.rybalka.tv/images/logo.png",
            streamUrl = "https://stream8.cinerama.uz/1413/tracks-v1a1/mono.m3u8",
            category = "hunting_fishing",
            epgId = "rybolov"
        ),
        Channel(
            id = "animal_planet_ru",
            name = "Animal Planet Россия",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/20/2018_Animal_Planet_logo.svg/500px-2018_Animal_Planet_logo.svg.png",
            streamUrl = "https://streaming.goodstream.icu/live/120.m3u8",
            category = "nature",
            epgId = "animal_planet_ru"
        ),
        Channel(
            id = "nauka2",
            name = "Наука 2.0",
            logoUrl = "https://i.imgur.com/ZZxzueO.png",
            streamUrl = "https://streaming.goodstream.icu/live/121.m3u8",
            category = "nature",
            epgId = "nauka2"
        ),
        Channel(
            id = "moya_planeta",
            name = "Моя планета",
            logoUrl = "https://i.imgur.com/uIiAdBv.png",
            streamUrl = "http://91.226.120.120/chid210/tracks-v1a1/mono.m3u8",
            category = "nature",
            epgId = "moya_planeta"
        ),
        Channel(
            id = "zhivaya_planeta",
            name = "Живая планета",
            logoUrl = "https://i.imgur.com/onNv1tM.png",
            streamUrl = "https://stream8.cinerama.uz/1250/tracks-v1a1/mono.m3u8",
            category = "nature",
            epgId = "zhivaya_planeta"
        ),
        Channel(
            id = "dikiy",
            name = "Дикий мир",
            logoUrl = "https://i.imgur.com/VDXDiPC.png",
            streamUrl = "https://streaming.goodstream.icu/live/124.m3u8",
            category = "nature",
            epgId = "dikiy"
        ),
        Channel(
            id = "relax",
            name = "Релакс ТВ",
            logoUrl = "https://www.relaxtv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/126.m3u8",
            category = "relax",
            epgId = "relax"
        ),
        Channel(
            id = "zdorovoe",
            name = "Здоровое ТВ",
            logoUrl = "https://i.imgur.com/jedtE5l.png",
            streamUrl = "https://v4.proofix.ru/0mir/index.m3u8",
            category = "relax",
            epgId = "zdorovoe"
        ),
        Channel(
            id = "kushe",
            name = "Кухня ТВ",
            logoUrl = "https://i.imgur.com/7jxZnuS.png",
            streamUrl = "https://streaming.goodstream.icu/live/128.m3u8",
            category = "lifestyle",
            epgId = "kushe"
        ),
        Channel(
            id = "usadba",
            name = "Усадьба",
            logoUrl = "https://i.imgur.com/mf13haG.png",
            streamUrl = "http://stream.mcquack.net/211/index.m3u8",
            category = "lifestyle",
            epgId = "usadba"
        ),
        Channel(
            id = "zagorodny",
            name = "Загородный",
            logoUrl = "https://www.zagorodny.ru/images/logo.png",
            streamUrl = "http://185.57.68.33/40/index.m3u8",
            category = "lifestyle",
            epgId = "zagorodny"
        ),
        Channel(
            id = "kino_comedy",
            name = "Кинокомедия",
            logoUrl = "https://i.imgur.com/dVjtth0.png",
            streamUrl = "http://stream.mcquack.net/265/index.m3u8",
            category = "movies",
            epgId = "kino_comedy"
        ),
        Channel(
            id = "kino_series",
            name = "Киносериал",
            logoUrl = "https://i.imgur.com/iao7zLZ.png",
            streamUrl = "https://streaming.goodstream.icu/live/132.m3u8",
            category = "movies",
            epgId = "kino_series"
        ),
        Channel(
            id = "kino_hit",
            name = "Кинохит",
            logoUrl = "https://i.imgur.com/Ge8vkQM.png",
            streamUrl = "http://stream.mcquack.net/126/index.m3u8",
            category = "movies",
            epgId = "kino_hit"
        ),
        Channel(
            id = "kino_tv",
            name = "Кино ТВ",
            logoUrl = "https://i.imgur.com/sMpamNO.png",
            streamUrl = "https://cityeden.catcast.tv/content/41333/index.m3u8",
            category = "movies",
            epgId = "kino_tv"
        ),
        Channel(
            id = "ilovecinema",
            name = "I Love Cinema",
            logoUrl = "https://www.ilovecinema.ru/images/logo.png",
            streamUrl = "http://flussonic.linkintel.ru/cinema/index.m3u8",
            category = "movies",
            epgId = "ilovecinema"
        ),
        Channel(
            id = "nash_kino",
            name = "Наше кино",
            logoUrl = "https://www.nashkino.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/136.m3u8",
            category = "movies",
            epgId = "nash_kino"
        ),
        Channel(
            id = "indian_kino",
            name = "Индийское кино",
            logoUrl = "https://i.imgur.com/LL8GyCh.png",
            streamUrl = "http://188.113.190.12/329/index.m3u8",
            category = "movies",
            epgId = "indian_kino"
        ),
        Channel(
            id = "tv1000_russian",
            name = "TV1000 Русское кино",
            logoUrl = "https://i.imgur.com/ZMsyjSr.png",
            streamUrl = "https://fs.uplink.kz/viju_tv1000_russkoe/mono.m3u8?token=onlinetv",
            category = "movies",
            epgId = "tv1000_russian"
        ),
        Channel(
            id = "tv1000_action",
            name = "TV1000 Action",
            logoUrl = "https://i.imgur.com/ZMsyjSr.png",
            streamUrl = "http://stream.mcquack.net/100/index.m3u8",
            category = "movies",
            epgId = "tv1000_action"
        ),
        Channel(
            id = "sony_scifi",
            name = "Сони Sci-Fi",
            logoUrl = "https://i.imgur.com/nSSUHcg.png",
            streamUrl = "http://stream.mcquack.net/180/index.m3u8",
            category = "movies",
            epgId = "sony_scifi"
        ),
        Channel(
            id = "sony_channel",
            name = "Сони Канал",
            logoUrl = "https://upload.wikimedia.org/wikipedia/en/d/de/Sony_TV_new.png",
            streamUrl = "http://38.96.178.205/SONYHD/index.m3u8",
            category = "movies",
            epgId = "sony_channel"
        ),
        Channel(
            id = "sony_turbo",
            name = "Сони Турбо",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9f/SONY_TURBO_logo.jpg",
            streamUrl = "https://streaming.goodstream.icu/live/142.m3u8",
            category = "movies",
            epgId = "sony_turbo"
        ),
        Channel(
            id = "history_ru",
            name = "History Россия",
            logoUrl = "https://i.imgur.com/FqWkeXb.png",
            streamUrl = "http://178.124.179.122:8080/HistoryHD/index.m3u8",
            category = "documentary",
            epgId = "history_ru"
        ),
        Channel(
            id = "viasat_history",
            name = "Viasat History",
            logoUrl = "https://i.imgur.com/X16UGUf.png",
            streamUrl = "http://88.212.15.29/live/test_viasat_history_atk_tv/playlist.m3u8",
            category = "documentary",
            epgId = "viasat_history"
        ),
        Channel(
            id = "viasat_nature",
            name = "Viasat Nature",
            logoUrl = "https://i.imgur.com/ousRqqD.png",
            streamUrl = "http://88.212.15.29/live/test_viasat_nature_atk_tv/playlist.m3u8",
            category = "nature",
            epgId = "viasat_nature"
        ),
        Channel(
            id = "viasat_explore",
            name = "Viasat Explore",
            logoUrl = "https://i.imgur.com/DyT5pKB.png",
            streamUrl = "http://88.212.15.29/live/test_viasat_explore_atk_tv/playlist.m3u8",
            category = "documentary",
            epgId = "viasat_explore"
        ),
        Channel(
            id = "discovery_ru",
            name = "Discovery Россия",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/27/Discovery_Channel_-_Logo_2019.svg/500px-Discovery_Channel_-_Logo_2019.svg.png",
            streamUrl = "https://streaming.goodstream.icu/live/147.m3u8",
            category = "documentary",
            epgId = "discovery_ru"
        ),
        Channel(
            id = "nat_geo_wild",
            name = "Nat Geo Wild",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/National_Geographic_Wild_logo.svg/960px-National_Geographic_Wild_logo.svg.png",
            streamUrl = "http://88.212.15.29/live/test_ngw/playlist.m3u8",
            category = "nature",
            epgId = "nat_geo_wild",
            fallbackStreamUrls = listOf(
                "http://51.75.127.199:3141/natgeowild/index.m3u8",
                "http://198.58.104.90:8989/natgeowild/index.m3u8"
            )
        ),
        Channel(
            id = "bbc_earth",
            name = "BBC Earth",
            logoUrl = "https://i.imgur.com/nGSsUd4.png",
            streamUrl = "http://57.128.231.171:8080/307/index.m3u8",
            category = "nature",
            epgId = "bbc_earth"
        ),
        Channel(
            id = "24_doc",
            name = "24 Док",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/0/0e/Logo_24_DOC.jpg",
            streamUrl = "https://streaming.goodstream.icu/live/150.m3u8",
            category = "documentary",
            epgId = "24_doc"
        ),
        Channel(
            id = "mir_serialov",
            name = "Мир Сериалов",
            logoUrl = "https://i.imgur.com/uzj7wVp.png",
            streamUrl = "http://176.118.197.101/MirSeriala/index.m3u8",
            category = "series",
            epgId = "mir_serialov"
        ),
        Channel(
            id = "mira_detektiv",
            name = "Мир Детективов",
            logoUrl = "https://www.mirdetektivov.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/152.m3u8",
            category = "series",
            epgId = "mira_detektiv"
        ),
        Channel(
            id = "ru_tv",
            name = "RU.TV",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/d/d2/Ru_tv_%D0%BB%D0%BE%D0%B3%D0%BE%D1%82%D0%B8%D0%BF.png",
            streamUrl = "https://streaming.astrakhan.ru/astrakhanrulivehd/playlist.m3u8",
            category = "music",
            epgId = "ru_tv"
        ),
        Channel(
            id = "europa_plus",
            name = "Europa Plus TV",
            logoUrl = "https://i.imgur.com/GLc4qrc.png",
            streamUrl = "https://streaming.goodstream.icu/live/154.m3u8",
            category = "music",
            epgId = "europa_plus"
        ),
        Channel(
            id = "bridge_tv",
            name = "Bridge TV",
            logoUrl = "https://i.imgur.com/qYObfrG.png",
            streamUrl = "http://stream.mcquack.net/318/index.m3u8",
            category = "music",
            epgId = "bridge_tv"
        ),
        Channel(
            id = "bridge_hit",
            name = "Bridge TV Хит",
            logoUrl = "https://i.imgur.com/qYObfrG.png",
            streamUrl = "http://stream.mcquack.net/320/index.m3u8",
            category = "music",
            epgId = "bridge_hit"
        ),
        Channel(
            id = "bridge_classic",
            name = "Bridge TV Classic",
            logoUrl = "https://i.imgur.com/qYObfrG.png",
            streamUrl = "http://stream.mcquack.net/272/index.m3u8",
            category = "music",
            epgId = "bridge_classic"
        ),
        Channel(
            id = "shanson_tv",
            name = "Шансон ТВ",
            logoUrl = "https://i.imgur.com/Fk4sd8t.png",
            streamUrl = "http://chanson-video.hostingradio.ru:8080/hls/chansonabr/live.m3u8",
            category = "music",
            epgId = "shanson_tv"
        ),
        Channel(
            id = "first_music",
            name = "Первый Музыкальный",
            logoUrl = "https://i.imgur.com/rEo7nR1.png",
            streamUrl = "http://rtmp.one.by:1300",
            category = "music",
            epgId = "first_music"
        ),
        Channel(
            id = "match_premier",
            name = "Матч Премьер",
            logoUrl = "https://www.matchtv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/160.m3u8",
            category = "sport",
            epgId = "match_premier"
        ),
        Channel(
            id = "match_arena",
            name = "Матч! Арена",
            logoUrl = "https://i.imgur.com/udTzwzu.png",
            streamUrl = "https://streaming.goodstream.icu/live/161.m3u8",
            category = "sport",
            epgId = "match_arena"
        ),
        Channel(
            id = "match_igra",
            name = "Матч! Игра",
            logoUrl = "https://i.imgur.com/5XWpF19.png",
            streamUrl = "https://streaming.goodstream.icu/live/162.m3u8",
            category = "sport",
            epgId = "match_igra"
        ),
        Channel(
            id = "match_strana",
            name = "Матч! Страна",
            logoUrl = "https://i.imgur.com/X02s2UE.png",
            streamUrl = "https://streaming.goodstream.icu/live/163.m3u8",
            category = "sport",
            epgId = "match_strana"
        ),
        Channel(
            id = "khl_tv",
            name = "КХЛ ТВ",
            logoUrl = "https://i.imgur.com/RgdHdOV.png",
            streamUrl = "https://streaming.goodstream.icu/live/164.m3u8",
            category = "sport",
            epgId = "khl_tv"
        ),
        Channel(
            id = "football_tv",
            name = "Футбол ТВ",
            logoUrl = "https://i.imgur.com/pEuaZVx.png",
            streamUrl = "http://stream3.cinerama.uz/1010/tracks-v1a1/mono.m3u8",
            category = "sport",
            epgId = "football_tv"
        ),
        Channel(
            id = "tiji",
            name = "TiJi",
            logoUrl = "https://i.imgur.com/QCOUJ30.png",
            streamUrl = "http://stream.mcquack.net/111/index.m3u8",
            category = "kids",
            epgId = "tiji"
        ),
        Channel(
            id = "gulli",
            name = "Gulli",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/87/Logo_Gulli_2023.svg/500px-Logo_Gulli_2023.svg.png",
            streamUrl = "https://stream8.cinerama.uz/1445/tracks-v1a1/mono.m3u8",
            category = "kids",
            epgId = "gulli"
        ),
        Channel(
            id = "boom",
            name = "Boomerang",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/Boomerang_2014_logo.svg/500px-Boomerang_2014_logo.svg.png",
            streamUrl = "https://streaming.goodstream.icu/live/168.m3u8",
            category = "kids",
            epgId = "boom"
        ),
        Channel(
            id = "disney_ru",
            name = "Disney Россия",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Disney_Channel_Russia.png/500px-Disney_Channel_Russia.png",
            streamUrl = "https://streaming.goodstream.icu/live/169.m3u8",
            category = "kids",
            epgId = "disney_ru"
        ),
        Channel(
            id = "starchild",
            name = "StarChild",
            logoUrl = "https://www.starchild.ru/images/logo.png",
            streamUrl = "https://dash2.antik.sk/live/test_star_cinema_atktv/playlist.m3u8",
            category = "kids",
            epgId = "starchild"
        ),
        Channel(
            id = "rain",
            name = "Дождь",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Tvrain.svg/960px-Tvrain.svg.png",
            streamUrl = "https://streaming.goodstream.icu/live/171.m3u8",
            category = "news",
            epgId = "rain"
        ),
        Channel(
            id = "rtvi",
            name = "RTVI",
            logoUrl = "https://i.imgur.com/1AEhXyS.png",
            streamUrl = "https://streaming.goodstream.icu/live/172.m3u8",
            category = "news",
            epgId = "rtvi"
        ),
        Channel(
            id = "euronews_ru",
            name = "Euronews Русский",
            logoUrl = "https://i.imgur.com/8t9mdg9.png",
            streamUrl = "https://streaming.goodstream.icu/live/173.m3u8",
            category = "news",
            epgId = "euronews_ru"
        ),
        Channel(
            id = "friday_int",
            name = "Friday! International",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/%D0%9F%D1%8F%D1%82%D0%BD%D0%B8%D1%86%D0%B0_%28%D1%81_2013%29.svg/500px-%D0%9F%D1%8F%D1%82%D0%BD%D0%B8%D1%86%D0%B0_%28%D1%81_2013%29.svg.png",
            streamUrl = "http://stream.mcquack.net/181/index.m3u8",
            category = "entertainment",
            epgId = "friday_int"
        ),
        Channel(
            id = "paramount_comedy",
            name = "Paramount Comedy",
            logoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b5/Paramount_Comedy_old.svg/500px-Paramount_Comedy_old.svg.png",
            streamUrl = "https://streaming.goodstream.icu/live/175.m3u8",
            category = "entertainment",
            epgId = "paramount_comedy"
        ),
        Channel(
            id = "black_silver",
            name = "Black & Silver",
            logoUrl = "https://i.imgur.com/Q4A6ci4.png",
            streamUrl = "http://stream.mcquack.net/263/index.m3u8",
            category = "entertainment",
            epgId = "black_silver"
        ),
        Channel(
            id = "start_world",
            name = "Start World",
            logoUrl = "https://images.iptv.rt.ru/images/d6iist8mifeailfopupg.png",
            streamUrl = "http://176.118.197.101/START_AIR/index.m3u8",
            category = "entertainment",
            epgId = "start_world"
        )
    )

    init {
        channels = hardcodedChannels
    }
}

object RegionList {

    val regions: List<Pair<Int, String>> = listOf(
        1 to "Москва",
        2 to "Московская область",
        3 to "Санкт-Петербург",
        4 to "Ленинградская область",
        5 to "Вологодская область",
        6 to "Воронежская область",
        7 to "Ивановская область",
        8 to "Калужская область",
        9 to "Костромская область",
        10 to "Курская область",
        11 to "Липецкая область",
        12 to "Московская область (запад)",
        13 to "Московская область (восток)",
        14 to "Новгородская область",
        15 to "Псковская область",
        16 to "Рязанская область",
        17 to "Смоленская область",
        18 to "Тверская область",
        19 to "Тульская область",
        20 to "Тамбовская область",
        21 to "Ярославская область",
        22 to "Белгородская область",
        23 to "Брянская область",
        24 to "Владимирская область",
        25 to "Калининградская область",
        26 to "Орловская область",
        27 to "Саратовская область",
        28 to "Сахалинская область",
        29 to "Свердловская область",
        30 to "Челябинская область",
        31 to "Ямало-Ненецкий АО",
        32 to "Амурская область",
        33 to "Архангельская область",
        34 to "Астраханская область",
        35 to "Бурятия",
        36 to "Волгоградская область",
        37 to "Вологодская область (север)",
        38 to "Дагестан",
        39 to "Еврейская АО",
        40 to "Забайкальский край",
        41 to "Иркутская область",
        42 to "Камчатский край",
        43 to "Кемеровская область",
        44 to "Кировская область",
        45 to "Краснодарский край",
        46 to "Красноярский край",
        47 to "Крым",
        48 to "Магаданская область",
        49 to "Марий Эл",
        50 to "Мордовия",
        51 to "Мурманская область",
        52 to "Ненецкий АО",
        53 to "Нижегородская область",
        54 to "Омская область",
        55 to "Оренбургская область",
        56 to "Пензенская область",
        57 to "Пермский край",
        58 to "Приморский край",
        59 to "Ростовская область",
        60 to "Рязанская область (юг)",
        61 to "Ставропольский край",
        62 to "Татарстан",
        63 to "Томская область",
        64 to "Тульская область (юг)",
        65 to "Тыва",
        66 to "Удмуртия",
        67 to "Хабаровский край",
        68 to "Ханты-Мансийский АО",
        69 to "Чечня",
        70 to "Чувашия",
        71 to "Якутия"
    )

    fun offset(regionId: Int): Int = when (regionId) {
        25 -> -1
        34, 49, 50, 62, 66, 70 -> 1
        29, 30, 31, 57, 68 -> 2
        54 -> 3
        43, 46, 63, 65 -> 4
        35, 41 -> 5
        40, 71 -> 6
        28, 32, 39, 58, 67 -> 7
        48 -> 8
        42 -> 9
        else -> 0
    }
}
