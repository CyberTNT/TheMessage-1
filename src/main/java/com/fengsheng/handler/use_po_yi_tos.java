package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class use_po_yi_tos extends AbstractProtoHandler<Fengsheng.use_po_yi_tos> {
    private static final Logger log = Logger.getLogger(use_po_yi_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.use_po_yi_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        Card card = r.findCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张牌");
            return;
        }
        if (card.getType() != Common.card_type.Po_Yi) {
            log.error("这张牌不是破译，而是" + card);
            return;
        }
        if (card.canUse(r.getGame(), r)) {
            r.incrSeq();
            card.execute(r.getGame(), r);
        }
    }
}