package com.limou.agent.service;

import com.limou.agent.model.entity.FilmReview;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

public interface FilmReviewService extends IService<FilmReview> {

    FilmReview createReview(Long userId, Long filmId, Long orderId, Integer rating, String content, String tags);

    Page<FilmReview> listByFilm(Long filmId, int pageNum, int pageSize);

    /** @return true=已标记有用, false=已取消 */
    boolean markHelpful(Long reviewId, Long userId);

    boolean isHelpful(Long reviewId, Long userId);

    Page<FilmReview> getMyReviews(Long userId, int pageNum, int pageSize);

    long countByFilmId(Long filmId);
}
