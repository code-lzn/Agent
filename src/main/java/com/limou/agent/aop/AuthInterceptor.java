package com.limou.agent.aop;

import com.limou.agent.annotation.AuthCheck;
import com.limou.agent.constant.UserConstant;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.entity.User;
import com.limou.agent.service.UserService;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //1.获取 用户

//        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
//        HttpServletRequest request = (HttpServletRequest)requestAttributes;
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        User loginUser = userService.getLoginUser(servletRequestAttributes.getRequest());
        //2.获取需要的用户
        String mustRole = authCheck.mustRole();
        //3.如果authCheck用户为空说明不需要，直接放行
        if(mustRole==null){
            return joinPoint.proceed();
        }
        //4.如果需要管理员权限，但是当前用户不是管理员，则抛出异常
        if(mustRole.equals(UserConstant.ADMIN_ROLE)&&!loginUser.getUserRole().equals(UserConstant.ADMIN_ROLE)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有权限");
        }
        //5.返回结果
        return joinPoint.proceed();
    }

}
