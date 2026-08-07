package com.limou.agent.service.impl;

import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.mapper.FilmReviewMapper;
import com.limou.agent.mapper.ReviewHelpfulMapper;
import com.limou.agent.model.entity.FilmReview;
import com.limou.agent.model.entity.ReviewHelpful;
import com.limou.agent.service.FilmReviewService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FilmReviewServiceImpl extends ServiceImpl<FilmReviewMapper, FilmReview>
        implements FilmReviewService {

    @Resource
    private ReviewHelpfulMapper reviewHelpfulMapper;

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

        // 查是否有过逻辑删除的旧记录
        FilmReview existing = this.getMapper().findAnyByUserAndFilm(userId, filmId);
        if (existing != null) {
            this.getMapper().reviveReview(existing.getId(), orderId, rating, content, tags);
            log.info("用户 {} 重新评价影片 {}: rating={}", userId, filmId, rating);
            existing.setOrderId(orderId);
            existing.setRating(rating);
            existing.setContent(content);
            existing.setTags(tags);
            existing.setIsDelete(false);
            return existing;
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
    public Page<FilmReview> listByFilm(Long filmId, int pageNum, int pageSize, String sortBy, String filterBy) {
        QueryWrapper qw = QueryWrapper.create().eq("filmId", filmId);

        if ("latest".equals(sortBy)) {
            qw.orderBy("createTime", false);
        } else {
            qw.orderBy("helpfulCount", false).orderBy("createTime", false);
        }

        if ("purchasedGood".equals(filterBy)) {
            qw.ge("rating", 3).isNotNull("orderId");
        }

        return this.page(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public boolean markHelpful(Long reviewId, Long userId) {
        FilmReview review = this.getById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "影评不存在");
        }
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("reviewId", reviewId);
        ReviewHelpful existing = reviewHelpfulMapper.selectOneByQuery(qw);
        if (existing != null) {
            reviewHelpfulMapper.deleteById(existing.getId());
            int cnt = Math.max(0, (review.getHelpfulCount() == null ? 0 : review.getHelpfulCount()) - 1);
            review.setHelpfulCount(cnt);
            this.updateById(review);
            return false;
        } else {
            ReviewHelpful h = ReviewHelpful.builder().userId(userId).reviewId(reviewId).createTime(java.time.LocalDateTime.now()).build();
            reviewHelpfulMapper.insert(h);
            review.setHelpfulCount((review.getHelpfulCount() == null ? 0 : review.getHelpfulCount()) + 1);
            this.updateById(review);
            return true;
        }
    }

    public boolean isHelpful(Long reviewId, Long userId) {
        if (userId == null) return false;
        return reviewHelpfulMapper.selectCountByQuery(
                QueryWrapper.create().eq("userId", userId).eq("reviewId", reviewId)
        ) > 0;
    }

    @Override
    public Page<FilmReview> getMyReviews(Long userId, int pageNum, int pageSize) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("createTime", false);
        return this.page(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public long countByFilmId(Long filmId) {
        return this.count(QueryWrapper.create().eq("filmId", filmId));
    }
}
