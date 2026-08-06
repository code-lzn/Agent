package com.limou.agent.service;

import com.limou.agent.model.entity.ReviewComment;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

public interface ReviewCommentService extends IService<ReviewComment> {

    ReviewComment createComment(Long userId, Long reviewId, Long parentId, String content);

    Page<ReviewComment> listByReview(Long reviewId, int pageNum, int pageSize);

    void deleteComment(Long commentId, Long userId);

    /** @return true=已标记有用, false=已取消 */
    boolean markHelpful(Long commentId, Long userId);

    boolean isHelpful(Long commentId, Long userId);
}
