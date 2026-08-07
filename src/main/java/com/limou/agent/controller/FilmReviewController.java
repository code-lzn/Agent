package com.limou.agent.controller;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.mapper.ReviewCommentMapper;
import com.limou.agent.mapper.ReviewHelpfulMapper;
import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.FilmReview;
import com.limou.agent.model.entity.ReviewComment;
import com.limou.agent.model.entity.ReviewHelpful;
import com.limou.agent.model.entity.User;
import com.limou.agent.model.vo.CommentBriefVO;
import com.limou.agent.model.vo.FilmReviewVO;
import com.limou.agent.model.vo.MyReviewVO;
import com.limou.agent.model.vo.ReviewCommentVO;
import com.limou.agent.model.vo.UserBriefVO;
import com.limou.agent.service.FilmReviewService;
import com.limou.agent.service.FilmService;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.ReviewCommentService;
import com.limou.agent.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private ReviewCommentService reviewCommentService;

    @Resource
    private UserService userService;

    @Resource
    private FilmService filmService;

    @Resource
    private OrderService orderService;

    @Resource
    private ReviewHelpfulMapper reviewHelpfulMapper;

    @Resource
    private ReviewCommentMapper reviewCommentMapper;

    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return loginUser.getId();
    }

    private Long tryGetLoginUserId(HttpServletRequest request) {
        try {
            return getLoginUserId(request);
        } catch (Exception e) {
            return null;
        }
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
        return ResultUtils.success(toVO(review, userId));
    }

    @GetMapping("/list/{filmId}")
    public BaseResponse<Page<FilmReviewVO>> listByFilm(@PathVariable Long filmId,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) String sortBy,
                                                        @RequestParam(required = false) String filterBy,
                                                        HttpServletRequest request) {
        Long currentUserId = tryGetLoginUserId(request);
        Page<FilmReview> page = filmReviewService.listByFilm(filmId, pageNum, pageSize, sortBy, filterBy);
        List<FilmReviewVO> voList = toVOList(page.getRecords(), currentUserId);
        Page<FilmReviewVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    @PostMapping("/helpful/{reviewId}")
    public BaseResponse<Boolean> markHelpful(@PathVariable Long reviewId,
                                              HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        boolean helpful = filmReviewService.markHelpful(reviewId, userId);
        return ResultUtils.success(helpful);
    }

    @GetMapping("/count/{filmId}")
    public BaseResponse<Long> countByFilm(@PathVariable Long filmId) {
        return ResultUtils.success(filmReviewService.countByFilmId(filmId));
    }

    // ================= 评论 =================

    @PostMapping("/comment")
    public BaseResponse<ReviewCommentVO> createComment(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        Long reviewId = Long.valueOf(body.get("reviewId").toString());
        String content = (String) body.get("content");
        Long parentId = body.containsKey("parentId") && body.get("parentId") != null
                ? Long.valueOf(body.get("parentId").toString()) : null;

        ReviewComment comment = reviewCommentService.createComment(userId, reviewId, parentId, content);
        // 更新影评的评论计数
        FilmReview review = filmReviewService.getById(reviewId);
        if (review != null) {
            review.setCommentCount((review.getCommentCount() == null ? 0 : review.getCommentCount()) + 1);
            filmReviewService.updateById(review);
        }
        return ResultUtils.success(toCommentVO(comment));
    }

    @GetMapping("/comment/list/{reviewId}")
    public BaseResponse<Page<ReviewCommentVO>> listComments(@PathVariable Long reviewId,
                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              HttpServletRequest request) {
        Long currentUserId = tryGetLoginUserId(request);
        Page<ReviewComment> page = reviewCommentService.listByReview(reviewId, pageNum, pageSize);
        List<ReviewCommentVO> voList = toCommentVOList(page.getRecords(), currentUserId);
        Page<ReviewCommentVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/comment/count/{reviewId}")
    public BaseResponse<Long> countComments(@PathVariable Long reviewId) {
        long count = reviewCommentService.count(
                QueryWrapper.create().eq("reviewId", reviewId));
        return ResultUtils.success(count);
    }

    @PostMapping("/comment/helpful/{commentId}")
    public BaseResponse<Boolean> markCommentHelpful(@PathVariable Long commentId,
                                                      HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        boolean helpful = reviewCommentService.markHelpful(commentId, userId);
        return ResultUtils.success(helpful);
    }

    @DeleteMapping("/comment/{commentId}")
    public BaseResponse<Void> deleteComment(@PathVariable Long commentId,
                                             HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        ReviewComment comment = reviewCommentService.getById(commentId);
        reviewCommentService.deleteComment(commentId, userId);
        if (comment != null) {
            FilmReview review = filmReviewService.getById(comment.getReviewId());
            if (review != null) {
                review.setCommentCount(Math.max(0, (review.getCommentCount() == null ? 0 : review.getCommentCount()) - 1));
                filmReviewService.updateById(review);
            }
        }
        return ResultUtils.success(null);
    }

    @GetMapping("/my")
    public BaseResponse<Page<MyReviewVO>> getMyReviews(HttpServletRequest request,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = getLoginUserId(request);
        Page<FilmReview> page = filmReviewService.getMyReviews(userId, pageNum, pageSize);
        List<MyReviewVO> voList = toMyReviewVOList(page.getRecords());
        Page<MyReviewVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    @DeleteMapping("/{reviewId}")
    public BaseResponse<Void> deleteMyReview(@PathVariable Long reviewId,
                                               HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        FilmReview review = filmReviewService.getById(reviewId);
        if (review == null || !review.getUserId().equals(userId)) {
            throw new com.limou.agent.exception.BusinessException(
                    com.limou.agent.exception.ErrorCode.OPERATION_ERROR, "无权删除该影评");
        }
        // 级联删除该影评下的所有评论
        List<ReviewComment> comments = reviewCommentMapper.selectListByQuery(
                QueryWrapper.create().eq("reviewId", reviewId));
        for (ReviewComment c : comments) {
            reviewCommentMapper.deleteById(c.getId());
        }
        // 删除该影评的有用记录
        List<ReviewHelpful> helpfuls = reviewHelpfulMapper.selectListByQuery(
                QueryWrapper.create().eq("reviewId", reviewId));
        for (ReviewHelpful h : helpfuls) {
            reviewHelpfulMapper.deleteById(h.getId());
        }
        // 删除影评本身
        filmReviewService.removeById(reviewId);
        return ResultUtils.success(null);
    }

    private List<UserBriefVO> getHelpfulUsers(Long reviewId) {
        List<ReviewHelpful> list = reviewHelpfulMapper.selectListByQuery(
                QueryWrapper.create().eq("reviewId", reviewId));
        if (CollUtil.isEmpty(list)) return List.of();
        Set<Long> userIds = list.stream().map(ReviewHelpful::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(h -> {
            User u = userMap.get(h.getUserId());
            return UserBriefVO.builder()
                    .userId(h.getUserId())
                    .userName(u != null ? u.getUserName() : null)
                    .userAvatar(u != null ? u.getUserAvatar() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    private List<CommentBriefVO> getComments(Long reviewId) {
        List<ReviewComment> list = reviewCommentMapper.selectListByQuery(
                QueryWrapper.create().eq("reviewId", reviewId).orderBy("createTime", true));
        if (CollUtil.isEmpty(list)) return List.of();
        Set<Long> userIds = list.stream().map(ReviewComment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(c -> {
            User u = userMap.get(c.getUserId());
            return CommentBriefVO.builder()
                    .userId(c.getUserId())
                    .userName(u != null ? u.getUserName() : null)
                    .userAvatar(u != null ? u.getUserAvatar() : null)
                    .content(c.getContent())
                    .createTime(c.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<MyReviewVO> toMyReviewVOList(List<FilmReview> records) {
        if (CollUtil.isEmpty(records)) return List.of();
        Set<Long> filmIds = records.stream().map(FilmReview::getFilmId).collect(Collectors.toSet());
        Map<Long, Film> filmMap = filmService.listByIds(filmIds).stream()
                .collect(Collectors.toMap(Film::getId, f -> f));
        return records.stream().map(r -> {
            Film film = filmMap.get(r.getFilmId());
            List<CommentBriefVO> comments = getComments(r.getId());
            return MyReviewVO.builder()
                    .id(r.getId())
                    .userId(r.getUserId())
                    .filmId(r.getFilmId())
                    .orderId(r.getOrderId())
                    .rating(r.getRating())
                    .content(r.getContent())
                    .tags(r.getTags())
                    .helpfulCount(r.getHelpfulCount())
                    .commentCount(comments.size())
                    .createTime(r.getCreateTime())
                    .filmName(film != null ? film.getName() : null)
                    .filmPosterUrl(film != null ? film.getPosterUrl() : null)
                    .isPurchased(r.getOrderId() != null)
                    .helpfulUsers(getHelpfulUsers(r.getId()))
                    .comments(comments)
                    .build();
        }).collect(Collectors.toList());
    }

    private FilmReviewVO toVO(FilmReview r, Long currentUserId) {
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
                .isHelpful(filmReviewService.isHelpful(r.getId(), currentUserId))
                .build();
    }

    private List<FilmReviewVO> toVOList(List<FilmReview> records, Long currentUserId) {
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
                    .isHelpful(filmReviewService.isHelpful(r.getId(), currentUserId))
                    .build();
        }).collect(Collectors.toList());
    }

    private ReviewCommentVO toCommentVO(ReviewComment c) {
        User user = userService.getById(c.getUserId());
        String replyTo = null;
        if (c.getReplyToUserId() != null) {
            User replyToUser = userService.getById(c.getReplyToUserId());
            replyTo = replyToUser != null ? replyToUser.getUserName() : null;
        }
        return ReviewCommentVO.builder()
                .id(c.getId())
                .reviewId(c.getReviewId())
                .userId(c.getUserId())
                .parentId(c.getParentId())
                .content(c.getContent())
                .helpfulCount(c.getHelpfulCount())
                .createTime(c.getCreateTime())
                .userName(user != null ? user.getUserName() : null)
                .userAvatar(user != null ? user.getUserAvatar() : null)
                .replyToUserName(replyTo)
                .isHelpful(false)
                .build();
    }

    private List<ReviewCommentVO> toCommentVOList(List<ReviewComment> records, Long currentUserId) {
        if (CollUtil.isEmpty(records)) return List.of();
        Set<Long> userIds = records.stream().map(ReviewComment::getUserId).collect(Collectors.toSet());
        // 把 replyToUserId 也加入查询
        records.stream().map(ReviewComment::getReplyToUserId).filter(id -> id != null).forEach(userIds::add);
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return records.stream().map(c -> {
            User u = userMap.get(c.getUserId());
            String replyTo = null;
            if (c.getReplyToUserId() != null) {
                User replyToUser = userMap.get(c.getReplyToUserId());
                replyTo = replyToUser != null ? replyToUser.getUserName() : null;
            }
            return ReviewCommentVO.builder()
                    .id(c.getId())
                    .reviewId(c.getReviewId())
                    .userId(c.getUserId())
                    .parentId(c.getParentId())
                    .content(c.getContent())
                    .helpfulCount(c.getHelpfulCount())
                    .createTime(c.getCreateTime())
                    .userName(u != null ? u.getUserName() : null)
                    .userAvatar(u != null ? u.getUserAvatar() : null)
                    .replyToUserName(replyTo)
                    .isHelpful(reviewCommentService.isHelpful(c.getId(), currentUserId))
                    .build();
        }).collect(Collectors.toList());
    }
}
