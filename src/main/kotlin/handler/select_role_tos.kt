package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

class select_role_tos : AbstractProtoHandler<Fengsheng.select_role_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.select_role_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}