package com.ssafy.ar.dummy

import com.ssafy.ar.data.QuestInfo
import com.ssafy.ar.data.QuestState
import com.ssafy.ar.data.ScriptInfo
import kotlin.random.Random

val quests: Map<Long, QuestInfo> = mapOf(
    1L to QuestInfo(
        id = 1L,
        npcId = Random.nextLong(1, 13),
        npcName = "동물",
        npcPosId = 1,
        questType = 2,
        latitude = 36.1067967,
        longitude = 128.4162641,
        speciesId = 1L,
        speciesName = "심부름",
        isComplete = QuestState.WAIT,
        isPlace = false,
    ),
    2L to QuestInfo(
        id = 2L,
        npcId = Random.nextLong(1, 13),
        npcName = "동물",
        npcPosId = 2,
        questType = 1,
        latitude = 36.1066985,
        longitude = 128.4161918,
        speciesId = 1L,
        speciesName = "강아지풀",
        isComplete = QuestState.WAIT,
        isPlace = false,
    ),
    3L to QuestInfo(
        id = 3L,
        npcId = Random.nextLong(1, 13),
        npcName = "동물",
        npcPosId = 3,
        questType = 3,
        latitude = 36.1066493,
        longitude = 128.4163712,
        speciesId = 1L,
        speciesName = "은행잎",
        isComplete = QuestState.WAIT,
        isPlace = false,
    ),
    4L to QuestInfo(
        id = 4L,
        npcId = Random.nextLong(1, 13),
        npcName = "동물",
        npcPosId = 4,
        questType = 4,
        latitude = 36.1071543,
        longitude = 128.4165288,
        speciesId = 1L,
        speciesName = "솔방울",
        isComplete = QuestState.WAIT,
        isPlace = false,
    ),
    5L to QuestInfo(
        id = 5L,
        npcId = Random.nextLong(1, 13),
        npcName = "동물",
        npcPosId = 5,
        questType = 1,
        latitude = 36.101726,
        longitude = 128.4199104,
        speciesId = 1L,
        speciesName = "단풍잎",
        isComplete = QuestState.WAIT,
        isPlace = false,
    ),
)
