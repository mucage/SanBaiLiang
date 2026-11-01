package com.mucage.sync

/** 同步文件的编解码：统一的 JSON 结构，安卓端与桌面端互通 */
object SyncCodec {

    const val FORMAT = "mucage-ledger"
    const val VERSION = 2
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
                    "type" to JsonValue.Str(category.type),
                    "icon" to JsonValue.Str(category.icon),
                    "sortOrder" to JsonValue.Num(category.sortOrder.toString()),
                    "updatedAt" to JsonValue.Num(category.updatedAt.toString()),
                    "deleted" to JsonValue.Bool(category.deleted)
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

        val categories = root.get("categories")?.arr()?.items.orEmpty().mapNotNull { item ->
            val obj = item.obj() ?: return@mapNotNull null
            val id = obj.get("id")?.long() ?: return@mapNotNull null
            SyncCategory(
                id = id,
                name = obj.get("name")?.str().orEmpty(),
                icon = obj.get("icon")?.str().orEmpty().ifBlank { "more" },
                type = CategoryType.from(obj.get("type")?.str()).code,
                sortOrder = obj.get("sortOrder")?.int() ?: 0,
                // 旧版文件没有这两个字段：按「内置分类、未删除」处理
                updatedAt = obj.get("updatedAt")?.long() ?: 0L,
                deleted = obj.get("deleted")?.bool() ?: false
            )
        }

        return SyncPayload(
            exportedAt = root.get("exportedAt")?.long() ?: 0L,
            records = records,
            balance = balance,
            // 文件未带分类时退回内置分类，保证老文件也能正常导入
            categories = categories.ifEmpty { FixedCategories.defaultSyncCategories }
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

    /** 判断分类是否需要写入（本地不存在，或文件版本更新；名称为空视为无效） */
    fun shouldWriteCategory(incoming: SyncCategory, localUpdatedAt: Long?): Boolean {
        if (incoming.name.isBlank()) return false
        if (localUpdatedAt == null) return true
        return incoming.updatedAt > localUpdatedAt
    }

    /** 规整导入账目的分类：不在本地有效分类内时归入同类型的「其他」 */
    fun normalize(record: SyncRecord, validExpenseIds: Set<Long>, validIncomeIds: Set<Long>): SyncRecord {
        val valid = if (CategoryType.from(record.type) == CategoryType.INCOME) validIncomeIds else validExpenseIds
        val fallback = FixedCategories.otherIdFor(record.type)
        return record.copy(categoryId = if (record.categoryId in valid) record.categoryId else fallback)
    }
}
