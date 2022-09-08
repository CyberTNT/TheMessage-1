package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.ReceivePhaseReceiverSkill;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

/**
 * 金生火技能【谨慎】：你接收双色情报后，可以用一张手牌与该情报面朝上互换。
 */
public class JinShen implements Skill {
    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.JIN_SHEN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseReceiverSkill fsm) || fsm.inFrontOfWhom().findSkill(getSkillId()) == null)
            return null;
        if (fsm.inFrontOfWhom().getSkillUseCount(getSkillId()) > 0)
            return null;
        if (fsm.messageCard().getColors().size() < 2)
            return null;
        fsm.inFrontOfWhom().addSkillUseCount(getSkillId());
        return new ResolveResult(new executeJinShen(fsm), true);
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {

    }

    private record executeJinShen(ReceivePhaseReceiverSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJinShen.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn().getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn(), fsm.inFrontOfWhom(), fsm.messageCard(), fsm.inFrontOfWhom(), 20);
            return new ResolveResult(this, false);
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.inFrontOfWhom()) {
                log.error("不是你发技能的时机");
                return new ResolveResult(this, false);
            }
            if (message instanceof Fengsheng.end_receive_phase_tos pb) {
                if (player instanceof HumanPlayer r && !r.checkSeq(pb.getSeq())) {
                    log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
                    return new ResolveResult(this, false);
                }
                if (player != fsm.inFrontOfWhom()) {
                    log.error("还没轮到你");
                    return new ResolveResult(this, false);
                }
                player.incrSeq();
                return new ResolveResult(fsm, true);
            }
            if (!(message instanceof Role.skill_jin_shen_tos pb)) {
                log.error("错误的协议");
                return new ResolveResult(this, false);
            }
            Player r = fsm.inFrontOfWhom();
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return new ResolveResult(this, false);
            }
            Card card = r.findCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return new ResolveResult(this, false);
            }
            r.incrSeq();
            log.info("[金生火]发动了[谨慎]");
            Card messageCard = fsm.messageCard();
            r.deleteCard(card.getId());
            r.deleteMessageCard(messageCard.getId());
            r.addMessageCard(card);
            r.addCard(messageCard);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_jin_shen_toc.newBuilder().setCard(card.toPbCard())
                            .setPlayerId(player1.getAlternativeLocation(r.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }
}