package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Hall;
import com.limou.agent.mapper.HallMapper;
import com.limou.agent.service.HallService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class HallServiceImpl extends ServiceImpl<HallMapper, Hall>  implements HallService{

}
