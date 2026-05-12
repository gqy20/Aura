package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMoodToolTest {

    private val moodSnapshotDao: MoodSnapshotDao = mockk(relaxed = true)

    @Test
    fun execute_savesMoodSnapshot() = runTest {
        val tool = UpdateMoodTool(moodSnapshotDao, companionIdProvider = { "comp-1" })

        val result = tool.execute(
            UpdateMoodTool.Args(
                mood = "happy",
                trigger = "User shared good news",
                intensity = 0.8f,
            )
        )

        assertTrue(result.contains("saved"))
        coVerify {
            moodSnapshotDao.insert(match<MoodSnapshotEntity> {
                it.mood == "happy" &&
                    it.trigger == "User shared good news" &&
                    it.intensity == 0.8f &&
                    it.companionId == "comp-1"
            })
        }
    }

    @Test
    fun execute_handlesOptionalTrigger() = runTest {
        val tool = UpdateMoodTool(moodSnapshotDao, companionIdProvider = { "comp-1" })

        tool.execute(UpdateMoodTool.Args(mood = "calm"))

        coVerify {
            moodSnapshotDao.insert(match<MoodSnapshotEntity> {
                it.mood == "calm" && it.trigger == null
            })
        }
    }

    @Test
    fun execute_coercesIntensityRange() = runTest {
        val tool = UpdateMoodTool(moodSnapshotDao, companionIdProvider = { "comp-1" })

        tool.execute(UpdateMoodTool.Args(mood = "excited", intensity = 5f))

        coVerify {
            moodSnapshotDao.insert(match<MoodSnapshotEntity> {
                it.intensity == 1f  // coerced from 5.0 to 1.0
            })
        }
    }

    @Test
    fun execute_keepsToolImplementationFocusedOnMoodPersistence() = runTest {
        val tool = UpdateMoodTool(moodSnapshotDao, companionIdProvider = { "comp-1" })

        tool.execute(UpdateMoodTool.Args(mood = "sad"))

        coVerify { moodSnapshotDao.insert(any()) }
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = UpdateMoodTool(mockk(), companionIdProvider = { "comp-1" })

        assertEquals("update_mood", tool.name)
        val desc = tool.descriptor.description
        assertTrue(desc.contains("mood", ignoreCase = true) || desc.contains("emotion", ignoreCase = true))
    }
}
