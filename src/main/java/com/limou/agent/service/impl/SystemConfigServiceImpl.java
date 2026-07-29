package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.SystemConfig;
import com.limou.agent.mapper.SystemConfigMapper;
import com.limou.agent.service.SystemConfigService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig>  implements SystemConfigService{

}
