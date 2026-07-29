package com.limou.agent.service.impl;


import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.service.OrderService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
}
