package com.limou.agent.controller;

import cn.hutool.core.io.FileUtil;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.constant.FileConstant;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.manager.CosManager;
import com.limou.agent.model.dto.file.UploadFileRequest;
import com.limou.agent.model.entity.User;
import com.limou.agent.model.enums.FileUploadBizEnum;
import com.limou.agent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;

/**
 * 文件接口

 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    /**
     * 文件上传
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.uploadFile(filepath, file);
            // 返回可访问地址
            return ResultUtils.success(FileConstant.COS_HOST + filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
        if (FileUploadBizEnum.FILM_POSTER.equals(fileUploadBizEnum)) {
            if (fileSize > 5 * ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "海报文件大小不能超过 5M");
            }
            if (!Arrays.asList("jpeg", "jpg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "海报文件类型仅支持 jpeg/jpg/png/webp");
            }
        }
    }

    /**
     * 头像上传（本地存储，无需 COS）
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestPart("file") MultipartFile multipartFile,
                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        // 文件大小 ≤ 2MB
        long fileSize = multipartFile.getSize();
        if (fileSize > 2 * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像大小不能超过 2MB");
        }
        // 文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        if (!Arrays.asList("jpeg", "jpg", "png", "webp", "gif").contains(suffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/png/webp/gif 格式");
        }
        // 保存到项目根目录下的 uploads/avatars
        String projectDir = System.getProperty("user.dir");
        File dir = new File(projectDir, "uploads/avatars");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filename = loginUser.getId() + "_" + System.currentTimeMillis() + "." + suffix;
        File dest = new File(dir, filename);
        try {
            multipartFile.transferTo(dest);
        } catch (Exception e) {
            log.error("avatar upload error", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
        // 返回可访问的路径
        String url = "/uploads/avatars/" + filename;
        return ResultUtils.success(url);
    }
}
