package com.limou.agent.service.impl;

import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.mapper.ReviewCommentHelpfulMapper;
import com.limou.agent.mapper.ReviewCommentMapper;
import com.limou.agent.model.entity.ReviewComment;
import com.limou.agent.model.entity.ReviewCommentHelpful;
import com.limou.agent.service.ReviewCommentService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment>
        implements ReviewCommentService {

    @Resource
    private ReviewCommentHelpfulMapper commentHelpfulMapper;

    @Override
    public ReviewComment createComment(Long userId, Long reviewId, Long parentId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        }
        Long rootParentId = null;
        Long replyToUserId = null;
        if (parentId != null) {
            ReviewComment parentComment = this.getById(parentId);
            if (parentComment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "要回复的评论不存在");
            }
            replyToUserId = parentComment.getUserId();
            // 如果父评论本身是回复（parentId不为空），则挂到根评论下
            rootParentId = parentComment.getParentId() != null ? parentComment.getParentId() : parentComment.getId();
        }
        ReviewComment comment = ReviewComment.builder()
                .userId(userId)
                .reviewId(reviewId)
                .parentId(rootParentId)
                .replyToUserId(replyToUserId)
                .content(content.trim())
                .helpfulCount(0)
                .build();
        this.save(comment);
        return comment;
    }

    @Override
    public Page<ReviewComment> listByReview(Long reviewId, int pageNum, int pageSize) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("reviewId", reviewId)
                .orderBy("createTime", true);
        return this.page(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public void deleteComment(Long commentId, Long userId) {
        ReviewComment comment = this.getById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "只能删除自己的评论");
        }
        this.removeById(commentId);
    }

    @Override
    public boolean markHelpful(Long commentId, Long userId) {
        ReviewComment comment = this.getById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("commentId", commentId);
        ReviewCommentHelpful existing = commentHelpfulMapper.selectOneByQuery(qw);
        if (existing != null) {
            commentHelpfulMapper.deleteById(existing.getId());
            int cnt = Math.max(0, (comment.getHelpfulCount() == null ? 0 : comment.getHelpfulCount()) - 1);
            comment.setHelpfulCount(cnt);
            this.updateById(comment);
            return false;
        } else {
            ReviewCommentHelpful h = ReviewCommentHelpful.builder().userId(userId).commentId(commentId).createTime(java.time.LocalDateTime.now()).build();
            commentHelpfulMapper.insert(h);
            comment.setHelpfulCount((comment.getHelpfulCount() == null ? 0 : comment.getHelpfulCount()) + 1);
            this.updateById(comment);
            return true;
        }
    }

    @Override
    public boolean isHelpful(Long commentId, Long userId) {
        if (userId == null) return false;
        return commentHelpfulMapper.selectCountByQuery(
                QueryWrapper.create().eq("userId", userId).eq("commentId", commentId)
        ) > 0;
    }
}
