package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Film;
import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.service.FilmService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class FilmServiceImpl extends ServiceImpl<FilmMapper, Film>  implements FilmService{

}
