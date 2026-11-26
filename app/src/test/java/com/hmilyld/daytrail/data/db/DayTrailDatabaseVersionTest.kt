package com.hmilyld.daytrail.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `@Database(version = 5)` 与 `DayTrailDatabase.DB_VERSION` 是两处字面量
 * （KSP 不接受类内 const 作为注解实参）。这个测试用导出的 schema 目录钉住
 * 两者，避免迁移时只改一处，导致备份校验用错版本。
 */
class DayTrailDatabaseVersionTest {
    @Test
    fun `DB_VERSION matches the newest exported schema`() {
        val versions =
            schemaDir()
                .listFiles { file -> file.isFile && file.extension == "json" }
                ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                ?.toSet()
                .orEmpty()

        assertEquals(
            "DB_VERSION=${DayTrailDatabase.DB_VERSION} 与已导出的 schema 不一致：$versions",
            DayTrailDatabase.DB_VERSION,
            versions.maxOrNull(),
        )
    }

    private fun schemaDir(): File {
        val relative = "schemas/com.hmilyld.daytrail.data.db.DayTrailDatabase"
        return listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isDirectory }
            ?: error("找不到 Room schema 目录")
    }
}
