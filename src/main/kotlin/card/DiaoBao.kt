package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.DiaoBao
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_diao_bao_toc
import org.apache.log4j.Logger
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class DiaoBao : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为调包使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Diao_Bao

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("调包被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? FightPhaseIdle)?.whoseFightTurn) {
            log.error("调包的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as FightPhaseIdle
        log.info("${r}使用了$this")
        r.deleteCard(id)
        val resolveFunc = object : Fsm {
            override fun resolve(): ResolveResult {
                val oldCard = fsm.messageCard
                g.deck.discard(oldCard)
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = use_diao_bao_toc.newBuilder()
                        builder.oldMessageCard = oldCard.toPbCard()
                        builder.playerId = player.getAlternativeLocation(r.location)
                        if (player === r) builder.cardId = id
                        player.send(builder.build())
                    }
                }
                return ResolveResult(
                    fsm.copy(
                        messageCard = getOriginCard(),
                        isMessageCardFaceUp = false,
                        whoseFightTurn = fsm.inFrontOfWhom
                    ), true
                )
            }
        }
        g.resolve(OnUseCard(fsm.whoseTurn, r, null, this, card_type.Diao_Bao, r, resolveFunc))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调包"
    }

    companion object {
        private val log = Logger.getLogger(DiaoBao::class.java)
        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            if (player.game!!.qiangLingTypes.contains(card_type.Diao_Bao)) return false
            val colors = e.messageCard.colors
            if (e.inFrontOfWhom === player && (e.isMessageCardFaceUp || player === e.whoseTurn) && colors.size == 1 && colors[0] != color.Black) return false
            if (ThreadLocalRandom.current().nextInt(4) != 0) return false
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}