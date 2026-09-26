package com.example.tvapp.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String = "general",
    val epgId: String? = null,
    val channelImageUrl: String? = null,
    val fallbackStreamUrls: List<String> = emptyList()
)

data class Program(
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val iconUrl: String? = null
) {
    fun isLive(currentTime: Long): Boolean {
        return currentTime in startTime..endTime
    }

    fun getDuration(): Long = endTime - startTime

    fun getElapsedTime(currentTime: Long): Long {
        return if (currentTime > startTime) currentTime - startTime else 0
    }
}

object ChannelList {
    val channels = listOf(
        Channel(
            id = "c1r",
            name = "Первый канал",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/bfe633c124309302e71fda2d3e9fe84f.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/210.m3u8",
            category = "federal",
            epgId = "c1r"
        ),
        Channel(
            id = "rossiya1",
            name = "Россия 1",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/d8fae7cfd00bea0824ef9b31c171d39c.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/211.m3u8",
            category = "federal",
            epgId = "rossiya1"
        ),
        Channel(
            id = "ntv",
            name = "НТВ",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/ddfb3e3d87a0561ba0aba5938f7bc541.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/213.m3u8",
            category = "federal",
            epgId = "ntv"
        ),
        Channel(
            id = "5tv",
            name = "5 Канал",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/566b4321fbe64f815a095c0ac5abdd60.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/8.m3u8",
            category = "federal",
            epgId = "5tv"
        ),
        Channel(
            id = "kultura",
            name = "Культура",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/31/ef/31ef8cd591b4cbbdaed8088922e80971.png?size=240&quality=95",
            streamUrl = "https://streaming.goodstream.icu/live/9.m3u8",
            category = "culture",
            epgId = "kultura"
        ),
        Channel(
            id = "zvezda",
            name = "Звезда",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/2f144b0b169431328bcf93adfaa6318f.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/10.m3u8",
            category = "federal",
            epgId = "zvezda"
        ),
        Channel(
            id = "pz",
            name = "Пятница!",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/16405c0a5a5e0ddbfbbc0cc07223432f.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/19.m3u8",
            category = "entertainment",
            epgId = "pz"
        ),
        Channel(
            id = "sts",
            name = "СТС",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/4af38baf4d33cd65fbb531e10fe04853.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/296.m3u8",
            category = "entertainment",
            epgId = "sts"
        ),
        Channel(
            id = "domashniy",
            name = "Домашний",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/3cbd856d0056a56737c500a0b0c35fe6.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/17.m3u8",
            category = "entertainment",
            epgId = "domashniy"
        ),
        Channel(
            id = "tnt",
            name = "ТНТ",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/3290b7b34d44bce3dd6ad7d21f627a26.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/21.m3u8",
            category = "entertainment",
            epgId = "tnt"
        ),
        Channel(
            id = "ren",
            name = "РЕН ТВ",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/eda9e7b046ab960211253a4c2c184894.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/14.m3u8",
            category = "federal",
            epgId = "ren"
        ),
        Channel(
            id = "karusel",
            name = "Карусель",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/56f48702828de52c360d184e67d3a929.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/232.m3u8",
            category = "kids",
            epgId = "karusel"
        ),
        Channel(
            id = "match",
            name = "Матч ТВ",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/b3f85e30a68115b50ed8c0da3fddf986.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/6.m3u8",
            category = "sport",
            epgId = "match"
        ),
        Channel(
            id = "rossiya24",
            name = "Россия 24",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/1c04982637b89cc88b711c72e0fa682e.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/30.m3u8",
            category = "news",
            epgId = "rossiya24"
        ),
        Channel(
            id = "tvc",
            name = "ТВ Центр",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/58f76d130f36333da764317576b3e648.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/13.m3u8",
            category = "federal",
            epgId = "tvc"
        ),
        Channel(
            id = "spas",
            name = "СПАС",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/9b93cae084d12203cbc9ddcfa6af6ed1.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/15.m3u8",
            category = "religious",
            epgId = "spas"
        ),
        Channel(
            id = "tv3",
            name = "ТВ-3",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/eb511e94654ecd565a8f3aa3cd06d4a9.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/18.m3u8",
            category = "entertainment",
            epgId = "tv3"
        ),
        Channel(
            id = "2x2",
            name = "2х2",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/fa/3d/fa3da72a76f79ed22bbbe06b17c3729b.png?size=240&quality=95",
            streamUrl = "https://streaming.goodstream.icu/live/20.m3u8",
            category = "entertainment",
            epgId = "2x2"
        ),
        Channel(
            id = "mir",
            name = "МИР",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/504aa8acd11b3a39e374e9edc1a8bc75.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/22.m3u8",
            category = "news",
            epgId = "mir"
        ),
        Channel(
            id = "otv",
            name = "ОТВ",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/61/4c/614cd7a162e697751a87fef05f82703b.png?size=240&quality=95",
            streamUrl = "https://streaming.goodstream.icu/live/12.m3u8",
            category = "regional",
            epgId = "otv"
        ),
        Channel(
            id = "che",
            name = "Че",
            logoUrl = "https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/7e/de/7ede7ab4fd8d0539cf4ecd4f8b49a7a1.png?size=240&quality=95",
            streamUrl = "https://streaming.goodstream.icu/live/23.m3u8",
            category = "entertainment",
            epgId = "che"
        ),
        Channel(
            id = "dom_kino",
            name = "Дом Кино",
            logoUrl = "https://www.domkino.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/44.m3u8",
            category = "movies",
            epgId = "dom_kino"
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
            logoUrl = "https://smotrim.ru/images/2023/05/18/logo_mult.png",
            streamUrl = "https://streaming.goodstream.icu/live/112.m3u8",
            category = "kids",
            epgId = "mult"
        ),
        Channel(
            id = "muztv",
            name = "МУЗ-ТВ",
            logoUrl = "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/db74d0889767bf974b84dbe702d4387e.jpg/x240/",
            streamUrl = "https://streaming.goodstream.icu/live/618.m3u8",
            category = "music",
            epgId = "muztv"
        ),
        Channel(
            id = "tv1000",
            name = "TV1000",
            logoUrl = "https://www.tv1000.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/114.m3u8",
            category = "movies",
            epgId = "tv1000"
        ),
        Channel(
            id = "ohota",
            name = "Охота и рыбалка",
            logoUrl = "https://www.ohotarybalka.tv/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/116.m3u8",
            category = "hunting_fishing",
            epgId = "ohota"
        ),
        Channel(
            id = "rybolov",
            name = "Рыбалка TV",
            logoUrl = "https://www.rybalka.tv/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/117.m3u8",
            category = "hunting_fishing",
            epgId = "rybolov"
        ),
        Channel(
            id = "animal_planet_ru",
            name = "Animal Planet Россия",
            logoUrl = "https://www.animalplanet.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/120.m3u8",
            category = "nature",
            epgId = "animal_planet_ru"
        ),
        Channel(
            id = "nauka2",
            name = "Наука 2.0",
            logoUrl = "https://www.nauka2.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/121.m3u8",
            category = "nature",
            epgId = "nauka2"
        ),
        Channel(
            id = "moya_planeta",
            name = "Моя планета",
            logoUrl = "https://www.moyaplaneta.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/122.m3u8",
            category = "nature",
            epgId = "moya_planeta"
        ),
        Channel(
            id = "zhivaya_planeta",
            name = "Живая планета",
            logoUrl = "https://www.zhiplaneta.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/123.m3u8",
            category = "nature",
            epgId = "zhivaya_planeta"
        ),
        Channel(
            id = "dikiy",
            name = "Дикий мир",
            logoUrl = "https://www.dikiymir.ru/images/logo.png",
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
            logoUrl = "https://www.zdorovoetv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/127.m3u8",
            category = "relax",
            epgId = "zdorovoe"
        ),
        Channel(
            id = "kushe",
            name = "Кухня ТВ",
            logoUrl = "https://www.kuhnya.tv/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/128.m3u8",
            category = "lifestyle",
            epgId = "kushe"
        ),
        Channel(
            id = "usadba",
            name = "Усадьба",
            logoUrl = "https://www.usadba.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/129.m3u8",
            category = "lifestyle",
            epgId = "usadba"
        ),
        Channel(
            id = "zagorodny",
            name = "Загородный",
            logoUrl = "https://www.zagorodny.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/130.m3u8",
            category = "lifestyle",
            epgId = "zagorodny"
        ),
        Channel(
            id = "kino_comedy",
            name = "Кинокомедия",
            logoUrl = "https://www.kinokomediya.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/131.m3u8",
            category = "movies",
            epgId = "kino_comedy"
        ),
        Channel(
            id = "kino_series",
            name = "Киносериал",
            logoUrl = "https://www.kinoserial.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/132.m3u8",
            category = "movies",
            epgId = "kino_series"
        ),
        Channel(
            id = "kino_hit",
            name = "Кинохит",
            logoUrl = "https://www.kinohit.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/133.m3u8",
            category = "movies",
            epgId = "kino_hit"
        ),
        Channel(
            id = "kino_tv",
            name = "Кино ТВ",
            logoUrl = "https://www.kino.tv/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/134.m3u8",
            category = "movies",
            epgId = "kino_tv"
        ),
        Channel(
            id = "ilovecinema",
            name = "I Love Cinema",
            logoUrl = "https://www.ilovecinema.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/135.m3u8",
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
            logoUrl = "https://www.indiankino.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/137.m3u8",
            category = "movies",
            epgId = "indian_kino"
        ),
        Channel(
            id = "tv1000_russian",
            name = "TV1000 Русское кино",
            logoUrl = "https://www.tv1000.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/138.m3u8",
            category = "movies",
            epgId = "tv1000_russian"
        ),
        Channel(
            id = "tv1000_action",
            name = "TV1000 Action",
            logoUrl = "https://www.tv1000.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/139.m3u8",
            category = "movies",
            epgId = "tv1000_action"
        ),
        Channel(
            id = "sony_scifi",
            name = "Сони Sci-Fi",
            logoUrl = "https://www.sony.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/140.m3u8",
            category = "movies",
            epgId = "sony_scifi"
        ),
        Channel(
            id = "sony_channel",
            name = "Сони Канал",
            logoUrl = "https://www.sony.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/141.m3u8",
            category = "movies",
            epgId = "sony_channel"
        ),
        Channel(
            id = "sony_turbo",
            name = "Сони Турбо",
            logoUrl = "https://www.sony.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/142.m3u8",
            category = "movies",
            epgId = "sony_turbo"
        ),
        Channel(
            id = "history_ru",
            name = "History Россия",
            logoUrl = "https://www.history.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/143.m3u8",
            category = "documentary",
            epgId = "history_ru"
        ),
        Channel(
            id = "viasat_history",
            name = "Viasat History",
            logoUrl = "https://www.viasat.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/144.m3u8",
            category = "documentary",
            epgId = "viasat_history"
        ),
        Channel(
            id = "viasat_nature",
            name = "Viasat Nature",
            logoUrl = "https://www.viasat.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/145.m3u8",
            category = "nature",
            epgId = "viasat_nature"
        ),
        Channel(
            id = "viasat_explore",
            name = "Viasat Explore",
            logoUrl = "https://www.viasat.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/146.m3u8",
            category = "documentary",
            epgId = "viasat_explore"
        ),
        Channel(
            id = "discovery_ru",
            name = "Discovery Россия",
            logoUrl = "https://www.discovery.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/147.m3u8",
            category = "documentary",
            epgId = "discovery_ru"
        ),
        Channel(
            id = "nat_geo_wild",
            name = "Nat Geo Wild",
            logoUrl = "https://www.natgeowild.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/148.m3u8",
            category = "nature",
            epgId = "nat_geo_wild"
        ),
        Channel(
            id = "bbc_earth",
            name = "BBC Earth",
            logoUrl = "https://www.bbcearth.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/149.m3u8",
            category = "nature",
            epgId = "bbc_earth"
        ),
        Channel(
            id = "24_doc",
            name = "24 Док",
            logoUrl = "https://www.24doc.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/150.m3u8",
            category = "documentary",
            epgId = "24_doc"
        ),
        Channel(
            id = "mir_serialov",
            name = "Мир Сериалов",
            logoUrl = "https://www.mirserialov.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/151.m3u8",
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
            logoUrl = "https://www.ru.tv/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/153.m3u8",
            category = "music",
            epgId = "ru_tv"
        ),
        Channel(
            id = "europa_plus",
            name = "Europa Plus TV",
            logoUrl = "https://www.europaplus.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/154.m3u8",
            category = "music",
            epgId = "europa_plus"
        ),
        Channel(
            id = "bridge_tv",
            name = "Bridge TV",
            logoUrl = "https://www.bridgetv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/155.m3u8",
            category = "music",
            epgId = "bridge_tv"
        ),
        Channel(
            id = "bridge_hit",
            name = "Bridge TV Хит",
            logoUrl = "https://www.bridgetv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/156.m3u8",
            category = "music",
            epgId = "bridge_hit"
        ),
        Channel(
            id = "bridge_classic",
            name = "Bridge TV Classic",
            logoUrl = "https://www.bridgetv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/157.m3u8",
            category = "music",
            epgId = "bridge_classic"
        ),
        Channel(
            id = "shanson_tv",
            name = "Шансон ТВ",
            logoUrl = "https://www.shansontv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/158.m3u8",
            category = "music",
            epgId = "shanson_tv"
        ),
        Channel(
            id = "first_music",
            name = "Первый Музыкальный",
            logoUrl = "https://www.pervyymuzicalnyy.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/159.m3u8",
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
            logoUrl = "https://www.matchtv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/161.m3u8",
            category = "sport",
            epgId = "match_arena"
        ),
        Channel(
            id = "match_igra",
            name = "Матч! Игра",
            logoUrl = "https://www.matchtv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/162.m3u8",
            category = "sport",
            epgId = "match_igra"
        ),
        Channel(
            id = "match_strana",
            name = "Матч! Страна",
            logoUrl = "https://www.matchtv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/163.m3u8",
            category = "sport",
            epgId = "match_strana"
        ),
        Channel(
            id = "khl_tv",
            name = "КХЛ ТВ",
            logoUrl = "https://www.khltv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/164.m3u8",
            category = "sport",
            epgId = "khl_tv"
        ),
        Channel(
            id = "football_tv",
            name = "Футбол ТВ",
            logoUrl = "https://www.futboltv.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/165.m3u8",
            category = "sport",
            epgId = "football_tv"
        ),
        Channel(
            id = "tiji",
            name = "TiJi",
            logoUrl = "https://www.tiji.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/166.m3u8",
            category = "kids",
            epgId = "tiji"
        ),
        Channel(
            id = "gulli",
            name = "Gulli",
            logoUrl = "https://www.gulli.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/167.m3u8",
            category = "kids",
            epgId = "gulli"
        ),
        Channel(
            id = "boom",
            name = "Boomerang",
            logoUrl = "https://www.boomerang.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/168.m3u8",
            category = "kids",
            epgId = "boom"
        ),
        Channel(
            id = "disney_ru",
            name = "Disney Россия",
            logoUrl = "https://www.disney.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/169.m3u8",
            category = "kids",
            epgId = "disney_ru"
        ),
        Channel(
            id = "starchild",
            name = "StarChild",
            logoUrl = "https://www.starchild.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/170.m3u8",
            category = "kids",
            epgId = "starchild"
        ),
        Channel(
            id = "rain",
            name = "Дождь",
            logoUrl = "https://www.tvrain.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/171.m3u8",
            category = "news",
            epgId = "rain"
        ),
        Channel(
            id = "rtvi",
            name = "RTVI",
            logoUrl = "https://www.rtvi.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/172.m3u8",
            category = "news",
            epgId = "rtvi"
        ),
        Channel(
            id = "euronews_ru",
            name = "Euronews Русский",
            logoUrl = "https://www.euronews.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/173.m3u8",
            category = "news",
            epgId = "euronews_ru"
        ),
        Channel(
            id = "friday_int",
            name = "Friday! International",
            logoUrl = "https://www.piatnitsa.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/174.m3u8",
            category = "entertainment",
            epgId = "friday_int"
        ),
        Channel(
            id = "paramount_comedy",
            name = "Paramount Comedy",
            logoUrl = "https://www.paramount.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/175.m3u8",
            category = "entertainment",
            epgId = "paramount_comedy"
        ),
        Channel(
            id = "black_silver",
            name = "Black & Silver",
            logoUrl = "https://www.blacksilver.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/176.m3u8",
            category = "entertainment",
            epgId = "black_silver"
        ),
        Channel(
            id = "start_world",
            name = "Start World",
            logoUrl = "https://www.startworld.ru/images/logo.png",
            streamUrl = "https://streaming.goodstream.icu/live/177.m3u8",
            category = "entertainment",
            epgId = "start_world"
        )
    )
}
