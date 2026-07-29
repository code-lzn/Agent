package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.service.SeatService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class SeatServiceImpl extends ServiceImpl<SeatMapper, Seat>  implements SeatService{

}
