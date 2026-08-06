package com.limou.agent.controller;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.model.entity.FilmReview;
import com.limou.agent.model.entity.User;
import com.limou.agent.model.vo.FilmReviewVO;
import com.limou.agent.service.FilmReviewService;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/filmReview")
public class FilmReviewController {

    @Resource
    private FilmReviewService filmReviewService;

    @Resource
    private UserService userService;

    @Resource
    private OrderService orderService;

    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return loginUser.getId();
    }

    @PostMapping("/create")
    public BaseResponse<FilmReviewVO> createReview(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        Long filmId = Long.valueOf(body.get("filmId").toString());
        Integer rating = (Integer) body.get("rating");
        String content = (String) body.get("content");
        String tags = (String) body.getOrDefault("tags", null);

        Long orderId = orderService.findCompletedOrderId(userId, filmId);

        FilmReview review = filmReviewService.createReview(userId, filmId, orderId, rating, content, tags);
        return ResultUtils.success(toVO(review));
    }

    @GetMapping("/list/{filmId}")
    public BaseResponse<Page<FilmReviewVO>> listByFilm(@PathVariable Long filmId,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        Page<FilmReview> page = filmReviewService.listByFilm(filmId, pageNum, pageSize);
        List<FilmReviewVO> voList = toVOList(page.getRecords());
        Page<FilmReviewVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    @PostMapping("/helpful/{reviewId}")
    public BaseResponse<Void> markHelpful(@PathVariable Long reviewId,
                                           HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        filmReviewService.markHelpful(reviewId, userId);
        return ResultUtils.success(null);
    }

    @GetMapping("/my")
    public BaseResponse<Page<FilmReviewVO>> getMyReviews(HttpServletRequest request,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = getLoginUserId(request);
        Page<FilmReview> page = filmReviewService.getMyReviews(userId, pageNum, pageSize);
        List<FilmReviewVO> voList = toVOList(page.getRecords());
        Page<FilmReviewVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    private FilmReviewVO toVO(FilmReview r) {
        User user = userService.getById(r.getUserId());
        return FilmReviewVO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .filmId(r.getFilmId())
                .orderId(r.getOrderId())
                .rating(r.getRating())
                .content(r.getContent())
                .tags(r.getTags())
                .helpfulCount(r.getHelpfulCount())
                .commentCount(r.getCommentCount())
                .createTime(r.getCreateTime())
                .userName(user != null ? user.getUserName() : null)
                .userAvatar(user != null ? user.getUserAvatar() : null)
                .isPurchased(r.getOrderId() != null)
                .build();
    }

    private List<FilmReviewVO> toVOList(List<FilmReview> records) {
        if (CollUtil.isEmpty(records)) return List.of();
        Set<Long> userIds = records.stream().map(FilmReview::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return records.stream().map(r -> {
            User u = userMap.get(r.getUserId());
            return FilmReviewVO.builder()
                    .id(r.getId())
                    .userId(r.getUserId())
                    .filmId(r.getFilmId())
                    .orderId(r.getOrderId())
                    .rating(r.getRating())
                    .content(r.getContent())
                    .tags(r.getTags())
                    .helpfulCount(r.getHelpfulCount())
                    .commentCount(r.getCommentCount())
                    .createTime(r.getCreateTime())
                    .userName(u != null ? u.getUserName() : null)
                    .userAvatar(u != null ? u.getUserAvatar() : null)
                    .isPurchased(r.getOrderId() != null)
                    .build();
        }).collect(Collectors.toList());
    }
}
