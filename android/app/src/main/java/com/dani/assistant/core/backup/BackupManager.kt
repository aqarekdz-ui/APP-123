package com.dani.assistant.core.backup

import android.content.Context
import android.util.Base64
import com.dani.assistant.core.knowledge.KnowledgeBase
import com.dani.assistant.core.knowledge.KnowledgeEntry
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.memory.SecretStore
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import com.dani.assistant.DaniApplication
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ImportResult(
    val tasks: Int,
    val facts: Int,
    val knowledge: Int,
    val secrets: Int,
    val secretsSkipped: Boolean
)

/** نسخة احتياطية موحّدة: مهام + معلومات + معرفة + أسرار (مشفّرة بكلمة سر النسخة، اختيارية). */
object BackupManager {
    private const val VERSION = 1
    private const val ITERATIONS = 120_000

    private fun deriveKey(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encryptSecrets(plain: String, pass: String): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(ct, Base64.NO_WRAP))
    }

    private fun decryptSecrets(o: JSONObject, pass: String): String? = try {
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, deriveKey(pass, salt), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) { null }

    fun hasSecrets(json: String): Boolean = try {
        JSONObject(json).has("secrets")
    } catch (e: Exception) { false }

    fun isValid(json: String): Boolean = try {
        JSONObject(json).let { it.has("app") && it.getString("app") == "dani" }
    } catch (e: Exception) { false }

    suspend fun export(ctx: Context, pass: String): String {
        val app = DaniApplication.instance
        val tasks = app.taskRepository.getAllTasks().first()
        val tasksArr = JSONArray()
        tasks.forEach {
            tasksArr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("desc", it.description ?: JSONObject.NULL)
                    .put("priority", it.priority.level)
                    .put("status", it.status.name)
                    .put("est", it.estimatedMinutes)
                    .put("due", it.dueDate ?: JSONObject.NULL)
                    .put("created", it.createdAt)
                    .put("completed", it.completedAt ?: JSONObject.NULL)
            )
        }
        val knowledgeArr = JSONArray()
        KnowledgeBase.getAll(ctx).forEach {
            knowledgeArr.put(
                JSONObject().put("id", it.id).put("q", it.question).put("k", it.key)
                    .put("a", it.answer).put("h", it.hits).put("t", it.createdAt)
            )
        }
        val root = JSONObject()
            .put("app", "dani")
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("tasks", tasksArr)
            .put("facts", JSONArray(MemoryStore.getAll(ctx)))
            .put("knowledge", knowledgeArr)
        if (pass.isNotEmpty()) {
            val secrets = SecretStore.getAll(ctx)
            if (secrets.isNotEmpty()) {
                root.put("secrets", encryptSecrets(JSONArray(secrets).toString(), pass))
            }
        }
        return root.toString(2)
    }

    /** دمج (لا يمسح شيء موجود): يتخطى المكرّر. */
    suspend fun import(ctx: Context, json: String, pass: String): ImportResult {
        val root = JSONObject(json)
        val repo = DaniApplication.instance.taskRepository

        // المهام
        val existing = repo.getAllTasks().first().map { it.title + "|" + it.createdAt }.toMutableSet()
        var tasksAdded = 0
        val arr = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val title = o.getString("title")
            val created = o.optLong("created", System.currentTimeMillis())
            if (!existing.add(title + "|" + created)) continue
            val status = try { TaskStatus.valueOf(o.optString("status", "NEW")) } catch (e: Exception) { TaskStatus.NEW }
            val due = if (o.isNull("due")) null else o.getLong("due")
            val task = Task(
                title = title,
                description = if (o.isNull("desc")) null else o.getString("desc"),
                priority = PriorityLevel.fromLevel(o.optInt("priority", 3)),
                status = status,
                estimatedMinutes = o.optInt("est", 30),
                dueDate = due,
                createdAt = created,
                completedAt = if (o.isNull("completed")) null else o.getLong("completed")
            )
            val id = repo.insertTask(task)
            tasksAdded++
            if (due != null && due > System.currentTimeMillis() &&
                status != TaskStatus.COMPLETED && status != TaskStatus.CANCELLED
            ) {
                repo.setTaskReminder(id, title, due, ReminderType.NOTIFICATION)
            }
        }

        // المعلومات
        val factsBefore = MemoryStore.getAll(ctx).size
        val factsArr = root.optJSONArray("facts") ?: JSONArray()
        for (i in 0 until factsArr.length()) MemoryStore.add(ctx, factsArr.getString(i))
        val factsAdded = MemoryStore.getAll(ctx).size - factsBefore

        // المعرفة
        val kArr = root.optJSONArray("knowledge") ?: JSONArray()
        val entries = List(kArr.length()) {
            val o = kArr.getJSONObject(it)
            KnowledgeEntry(o.getLong("id"), o.getString("q"), o.getString("k"), o.getString("a"), o.optInt("h", 0), o.optLong("t", 0L))
        }
        val knowledgeAdded = KnowledgeBase.importEntries(ctx, entries)

        // الأسرار
        var secretsAdded = 0
        var secretsSkipped = false
        if (root.has("secrets")) {
            val plain = if (pass.isNotEmpty()) decryptSecrets(root.getJSONObject("secrets"), pass) else null
            if (plain == null) {
                secretsSkipped = true
            } else {
                val before = SecretStore.getAll(ctx).size
                val sArr = JSONArray(plain)
                for (i in 0 until sArr.length()) SecretStore.add(ctx, sArr.getString(i))
                secretsAdded = SecretStore.getAll(ctx).size - before
            }
        }
        return ImportResult(tasksAdded, factsAdded, knowledgeAdded, secretsAdded, secretsSkipped)
    }
}
