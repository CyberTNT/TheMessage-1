package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId

com.fengsheng.card.*
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
class use_wu_dao_tos : AbstractProtoHandler<Fengsheng.use_wu_dao_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_wu_dao_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        if (card.type != card_type.Wu_Dao && (card.type != card_type.Jie_Huo || r.findSkill<Skill?>(SkillId.YING_BIAN) == null)) {
            log.error("这张牌不是误导，而是$card")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= r.game.players.size) {
            log.error("目标错误")
            return
        }
        val target = r.game.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (card.type != card_type.Wu_Dao) card = Card.Companion.falseCard(card_type.Wu_Dao, card)
        if (card.canUse(r.game, r, target)) {
            r.incrSeq()
            card.execute(r.game, r, target)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_wu_dao_tos::class.java)
    }
}