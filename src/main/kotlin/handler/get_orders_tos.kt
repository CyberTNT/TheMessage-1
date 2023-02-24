package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.protos.Fengsheng

class get_orders_tos : AbstractProtoHandler<Fengsheng.get_orders_tos?>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.get_orders_tos?) {
        r.send(
            Fengsheng.get_orders_toc.newBuilder().addAllOrders(Statistics.Companion.getInstance().getOrders(r.device))
                .build()
        )
    }
}