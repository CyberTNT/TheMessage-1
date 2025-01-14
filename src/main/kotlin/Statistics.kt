package com.fengsheng

import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.get_record_list_toc
import com.fengsheng.skill.RoleCache
import org.apache.log4j.Logger
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object Statistics {
    private val pool = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val playerInfoMap = ConcurrentHashMap<String, PlayerInfo>()
    private val totalWinCount = AtomicInteger()
    private val totalGameCount = AtomicInteger()
    private val trialStartTime = ConcurrentHashMap<String, Long>()
    fun add(records: List<Record>) {
        pool.submit {
            try {
                val time = dateFormat.format(Date())
                val sb = StringBuilder()
                for (r in records) {
                    sb.append(r.role).append(',')
                    sb.append(r.isWinner).append(',')
                    sb.append(r.identity).append(',')
                    sb.append(if (r.identity == color.Black) r.task.toString() else "").append(',')
                    sb.append(r.totalPlayerCount).append(',')
                    sb.append(time).append('\n')
                }
                writeFile("stat.csv", sb.toString().toByteArray(), true)
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun addPlayerGameCount(playerGameResultList: List<PlayerGameResult>) {
        pool.submit {
            try {
                var win = 0
                var game = 0
                var updateTrial = false
                for (count in playerGameResultList) {
                    if (count.isWin) {
                        win++
                        if (trialStartTime.remove(count.player.device) != null) updateTrial = true
                    }
                    game++
                    playerInfoMap.computeIfPresent(count.player.playerName) { _, v ->
                        val addWin = if (count.isWin) 1 else 0
                        PlayerInfo(v.name, v.deviceId, v.password, v.winCount + addWin, v.gameCount + 1)
                    }
                }
                totalWinCount.addAndGet(win)
                totalGameCount.addAndGet(game)
                savePlayerInfo()
                if (updateTrial) saveTrials()
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun login(name: String, deviceId: String, pwd: String?): PlayerInfo? {
        val password = try {
            if (pwd.isNullOrEmpty()) "" else md5(name + pwd)
        } catch (e: NoSuchAlgorithmException) {
            log.error("md5加密失败", e)
            return null
        }
        val playerInfo = playerInfoMap.getOrPut(name) {
            pool.submit(::savePlayerInfo)
            PlayerInfo(name, deviceId, password, 0, 0)
        }.let {
            return@let if (it.password.isEmpty() && password.isNotEmpty()) it.copy(password = password) else it
        }
        if (password != playerInfo.password) return null
        return playerInfo
    }

    fun getPlayerGameCount(name: String): PlayerGameCount {
        val playerInfo = playerInfoMap[name] ?: return PlayerGameCount(0, 0)
        return PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
    }

    val totalPlayerGameCount: PlayerGameCount
        get() = PlayerGameCount(totalWinCount.get(), totalGameCount.get())

    private fun savePlayerInfo() {
        val sb = StringBuilder()
        for ((_, info) in playerInfoMap) {
            sb.append(info.winCount).append(',')
            sb.append(info.gameCount).append(',')
            sb.append(info.name).append(',')
            sb.append(info.deviceId).append(',')
            sb.append(info.password).append('\n')
        }
        writeFile("playerInfo.csv", sb.toString().toByteArray())
    }

    private fun saveTrials() {
        val sb = StringBuilder()
        for ((key, value) in trialStartTime) {
            sb.append(value).append(',')
            sb.append(key).append('\n')
        }
        writeFile("trial.csv", sb.toString().toByteArray())
    }

    @Throws(IOException::class)
    fun load() {
        var winCount = 0
        var gameCount = 0
        try {
            BufferedReader(InputStreamReader(FileInputStream("playerInfo.csv"))).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(",".toRegex(), limit = 5).toTypedArray()
                    val password = a[4]
                    val deviceId = a[3]
                    val name = a[2]
                    val win = a[0].toInt()
                    val game = a[1].toInt()
                    if (playerInfoMap.put(
                            name,
                            PlayerInfo(name, deviceId, password, win, game)
                        ) != null
                    ) throw RuntimeException("数据错误，有重复的玩家name")
                    winCount += win
                    gameCount += game
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
        totalWinCount.set(winCount)
        totalGameCount.set(gameCount)
        try {
            BufferedReader(InputStreamReader(FileInputStream("trial.csv"))).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line!!.split(",".toRegex(), limit = 2).toTypedArray()
                    trialStartTime[a[1]] = a[0].toLong()
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
    }

    fun getTrialStartTime(deviceId: String): Long {
        return trialStartTime.getOrDefault(deviceId, 0L)
    }

    fun setTrialStartTime(device: String, time: Long) {
        pool.submit {
            try {
                trialStartTime[device] = time
                saveTrials()
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun displayRecordList(player: HumanPlayer) {
        pool.submit {
            val builder = get_record_list_toc.newBuilder()
            val dir = File("records")
            val files = dir.list()
            if (files != null) {
                files.sort()
                var lastPrefix: String? = null
                var j = 0
                for (i in files.indices.reversed()) {
                    if (files[i].length < 19) continue
                    if (lastPrefix == null || !files[i].startsWith(lastPrefix)) {
                        if (++j > Config.RecordListSize) break
                        lastPrefix = files[i].substring(0, 19)
                    }
                    builder.addRecords(files[i])
                }
            }
            player.send(builder.build())
        }
    }

    data class Record(
        val role: role,
        val isWinner: Boolean,
        val identity: color,
        val task: secret_task,
        val totalPlayerCount: Int
    )

    data class PlayerGameResult(val player: HumanPlayer, val isWin: Boolean)

    data class PlayerGameCount(val winCount: Int, val gameCount: Int) {
        fun random(): PlayerGameCount {
            val i = Random.nextInt(20)
            return PlayerGameCount(winCount * i / 100, gameCount * i / 100)
        }
    }

    data class PlayerInfo(
        val name: String,
        val deviceId: String,
        val password: String,
        val winCount: Int,
        val gameCount: Int
    )

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
    }

    private val log = Logger.getLogger(Statistics::class.java)

    private fun writeFile(fileName: String, buf: ByteArray, append: Boolean = false) {
        try {
            FileOutputStream(fileName, append).use { fileOutputStream -> fileOutputStream.write(buf) }
        } catch (e: IOException) {
            log.error("write file failed", e)
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        fun IntArray.inc(index: Int? = null) {
            this[0]++
            if (index != null) {
                this[2]++
                this[index]++
            } else {
                this[1]++
            }
        }

        fun <K> HashMap<K, IntArray>.sum(index: Int): Int {
            var sum = 0
            this.forEach { sum += it.value[index] }
            return sum
        }

        val appearCount = HashMap<role, IntArray>()
        val winCount = HashMap<role, IntArray>()
        FileInputStream("stat.csv").use { `is` ->
            BufferedReader(InputStreamReader(`is`)).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val role = role.valueOf(a[0])
                    val appear = appearCount.computeIfAbsent(role) { IntArray(8) }
                    val win = winCount.computeIfAbsent(role) { IntArray(8) }
                    val index =
                        if ("Black" == a[2]) secret_task.valueOf(a[3]).number + 3
                        else null
                    appear.inc(index)
                    if (a[1].toBoolean()) win.inc(index)
                }
            }
        }
        val lines = TreeMap<Double, String>(Collections.reverseOrder())
        for ((key, value) in appearCount) {
            val sb = StringBuilder()
            val roleName = RoleCache.getRoleName(key) ?: ""
            sb.append(roleName)
            sb.append(",")
            sb.append(value[0])
            var winRate: Double? = null
            for ((i, v) in value.withIndex()) {
                sb.append(",")
                winCount[key]?.let { if (v == 0) null else it[i] * 100.0 / v }?.let { r ->
                    if (winRate == null) winRate = r
                    sb.append("%.2f%%".format(r))
                }
            }
            lines[winRate!!] = sb.toString()
        }
        FileOutputStream("stat0.csv").use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                writer.write("角色,场次,胜率,军潜胜率,神秘人胜率,镇压者胜率,簒夺者胜率,双重间谍胜率,诱变者胜率,先行者胜率")
                writer.newLine()
                writer.write("全部,${appearCount.sum(0)}")
                for (i in 0 until 8) {
                    writer.write(",")
                    writer.write("%.2f%%".format(winCount.sum(i) * 100.0 / appearCount.sum(i)))
                }
                writer.newLine()
                for (line in lines.values) {
                    writer.write(line)
                    writer.newLine()
                }
                writer.newLine()
            }
        }
    }

    private val hexDigests =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    @Throws(NoSuchAlgorithmException::class)
    private fun md5(s: String): String {
        try {
            val `in` = s.toByteArray(StandardCharsets.UTF_8)
            val messageDigest = MessageDigest.getInstance("md5")
            messageDigest.update(`in`)
            // 获得密文
            val md = messageDigest.digest()
            // 将密文转换成16进制字符串形式
            val j = md.size
            val str = CharArray(j * 2)
            var k = 0
            for (b in md) {
                str[k++] = hexDigests[b.toInt() ushr 4 and 0xf] // 高4位
                str[k++] = hexDigests[b.toInt() and 0xf] // 低4位
            }
            return String(str)
        } catch (e: NoSuchAlgorithmException) {
            log.warn("calculate md5 failed: ", e)
            return s
        }
    }
}