package com.mucage.sync

/** 同步文件的编解码：统一的 JSON 结构，安卓端与桌面端互通 */
object SyncCodec {

    const val FORMAT = "mucage-ledger"
    const val VERSION = 1
    const val FILE_PREFIX = "此地无银三百两-账本-"

    fun encode(payload: SyncPayload): String {
        val gameRecords = JsonValue.Arr(payload.records.map { record ->
            JsonValue.Obj(
                linkedMapOf(
                    "syncId" to JsonValue.Str(record.syncId),
                    "amountCents" to JsonValue.Num(record.amountCents.toString()),
                    "type" to JsonValue.Str(record.type),
                    "categoryId" to JsonValue.Num(record.categoryId.toString()),
                    "note" to JsonValue.Str(record.note),
                    "date" to JsonValue.Num(record.date.toString()),
                    "limitExempt" to JsonValue.Num(record.limitExempt.toString()),
                    "updatedAt" to JsonValue.Num(record.updatedAt.toString()),
                    "deleted" to JsonValue.Bool(record.deleted)
                )
            )
        })
        val categories = JsonValue.Arr(payload.categories.map { category ->
            JsonValue.Obj(
                linkedMapOf(
                    "id" to JsonValue.Num(category.id.toString()),
                    "name" to JsonValue.Str(category.name),
                    "type" to JsonValue.Str(category.type.code),
                    "icon" to JsonValue.Str(category.icon),
                    "sortOrder" to JsonValue.Num(category.sortOrder.toString())
                )
            )
        })
        val root = JsonValue.Obj(
            linkedMapOf(
                "format" to JsonValue.Str(FORMAT),
                "version" to JsonValue.Num(VERSION.toString()),
                "exportedAt" to JsonValue.Num(payload.exportedAt.toString()),
                "balance" to JsonValue.Obj(
                    linkedMapOf(
                        "balanceCents" to JsonValue.Num(payload.balance.balanceCents.toString()),
                        "anchoredAt" to JsonValue.Num(payload.balance.anchoredAt.toString()),
                        "initialized" to JsonValue.Bool(payload.balance.initialized),
                        "updatedAt" to JsonValue.Num(payload.balance.updatedAt.toString())
                    )
                ),
                "categories" to categories,
                "records" to gameRecords
            )
        )
        return Json.write(root)
    }

    /** 解析同步文件；格式非法或不是本应用导出的文件时返回 null */
    fun decode(text: String): SyncPayload? {
        val root = Json.parse(text)?.obj() ?: return null
        val format = root.get("format")?.str()
        if (format != FORMAT) return null

        val balanceObj = root.get("balance")?.obj()
        val balance = SyncBalance(
            balanceCents = balanceObj?.get("balanceCents")?.long() ?: 0L,
            anchoredAt = balanceObj?.get("anchoredAt")?.long() ?: 0L,
            initialized = balanceObj?.get("initialized")?.bool() ?: false,
            updatedAt = balanceObj?.get("updatedAt")?.long() ?: 0L
        )

        val records = root.get("records")?.arr()?.items.orEmpty().mapNotNull { item ->
            val obj = item.obj() ?: return@mapNotNull null
            val syncId = obj.get("syncId")?.str().orEmpty()
            if (syncId.isBlank()) return@mapNotNull null
            SyncRecord(
                syncId = syncId,
                amountCents = obj.get("amountCents")?.long() ?: 0L,
                type = obj.get("type")?.str() ?: "EXPENSE",
                categoryId = obj.get("categoryId")?.long()
                    ?: FixedCategories.INCOME_OTHER,
                note = obj.get("note")?.str().orEmpty(),
                date = obj.get("date")?.long() ?: 0L,
                limitExempt = obj.get("limitExempt")?.int() ?: 0,
                updatedAt = obj.get("updatedAt")?.long() ?: 0L,
                // 旧版文件没有 deleted 字段，默认 false
                deleted = obj.get("deleted")?.bool() ?: false
            )
        }

        return SyncPayload(
            exportedAt = root.get("exportedAt")?.long() ?: 0L,
            records = records,
            balance = balance
        )
    }

    /** 生成默认文件名：此地无银三百两-账本-20261031-1200.json */
    fun fileName(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min = cal.get(java.util.Calendar.MINUTE)
        return "$FILE_PREFIX" + String.format("%04d%02d%02d-%02d%02d", y, m, d, h, min) + ".json"
    }
}

/** 合并策略：以 updatedAt 较新者为准，同一 syncId 不重复插入 */
object SyncMerge {

    /**
     * 通用合并入口（两端共用）：把文件中的账目合并进本地列表。
     * @param localIds 本地已存在的 syncId 集合
     */
    fun plan(
        incoming: List<SyncRecord>,
        localById: Map<String, Long>
    ): List<SyncRecord> = incoming.filter { record ->
        record.syncId.isNotBlank() && record.amountCents > 0
    }

    /** 判断账目是否需要写入（本地不存在，或文件版本更新） */
    fun shouldWrite(incoming: SyncRecord, localUpdatedAt: Long?): Boolean {
        if (incoming.amountCents <= 0) return false
        if (localUpdatedAt == null) return true
        return incoming.updatedAt > localUpdatedAt
    }

    /** 判断余额是否需要覆盖（文件版本更新且已初始化） */
    fun shouldTakeBalance(incoming: SyncBalance, local: SyncBalance?): Boolean {
        if (!incoming.initialized) return false
        if (local == null || !local.initialized) return true
        return incoming.updatedAt > local.updatedAt
    }

    /** 规整导入账目的分类：非法分类归入内置「其他」 */
    fun normalize(record: SyncRecord): SyncRecord =
        record.copy(categoryId = FixedCategories.normalizeCategoryId(record.type, record.categoryId))
}
