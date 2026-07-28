package com.limou.agent.manager;


import com.limou.agent.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传文件
     *
     * @param key 文件的绝对路径
     * @param file 文件
     * @return PutObjectResult
     */

    public PutObjectResult pubObject(String key, File file){
        COSClient cosClient = cosClientConfig.cosClient();
        return cosClient.putObject(cosClientConfig.getBucket(), key, file);
    }

    public String uploadFile(String key, File file){
        PutObjectResult putObjectResult = pubObject(key, file);
        if (putObjectResult == null){
            log.error("上传文件失败,返回结果为空");
            return null;
        }
        log.info("上传文件成功,返回结果为:{}", putObjectResult);
        return cosClientConfig.getHost() + key;
    }


}
