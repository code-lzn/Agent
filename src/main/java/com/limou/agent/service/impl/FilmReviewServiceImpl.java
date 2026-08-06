package com.limou.agent.service.impl;

import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.mapper.FilmReviewMapper;
import com.limou.agent.model.entity.FilmReview;
import com.limou.agent.service.FilmReviewService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FilmReviewServiceImpl extends ServiceImpl<FilmReviewMapper, FilmReview>
        implements FilmReviewService {

    @Override
    public FilmReview createReview(Long userId, Long filmId, Long orderId,
                                    Integer rating, String content, String tags) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评分需在1-5之间");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "影评内容不能为空");
        }
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("filmId", filmId);
        if (this.count(qw) > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已评价过该影片");
        }

        FilmReview review = FilmReview.builder()
                .userId(userId)
                .filmId(filmId)
                .orderId(orderId)
                .rating(rating)
                .content(content)
                .tags(tags)
                .helpfulCount(0)
                .commentCount(0)
                .build();
        this.save(review);
        log.info("用户 {} 评价影片 {}: rating={}", userId, filmId, rating);
        return review;
    }

    @Override
    public Page<FilmReview> listByFilm(Long filmId, int pageNum, int pageSize) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("filmId", filmId)
                .orderBy("helpfulCount", false)
                .orderBy("createTime", false);
        return this.page(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public void markHelpful(Long reviewId, Long userId) {
        FilmReview review = this.getById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "影评不存在");
        }
        review.setHelpfulCount((review.getHelpfulCount() == null ? 0 : review.getHelpfulCount()) + 1);
        this.updateById(review);
    }

    @Override
    public Page<FilmReview> getMyReviews(Long userId, int pageNum, int pageSize) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("createTime", false);
        return this.page(new Page<>(pageNum, pageSize), qw);
    }
}
