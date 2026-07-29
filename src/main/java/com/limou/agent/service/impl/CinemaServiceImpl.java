package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.mapper.CinemaMapper;
import com.limou.agent.service.CinemaService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema>  implements CinemaService{

}
