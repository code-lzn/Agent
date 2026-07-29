package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.mapper.OrderSeatMapper;
import com.limou.agent.service.OrderSeatService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class OrderSeatServiceImpl extends ServiceImpl<OrderSeatMapper, OrderSeat>  implements OrderSeatService{

}
