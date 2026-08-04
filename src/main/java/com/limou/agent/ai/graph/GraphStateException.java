package com.limou.agent.ai.graph;

/**
 * 图状态异常
 * <p>
 * 在图的构建（编译校验失败）或执行（节点异常）阶段抛出。
 * 继承自 RuntimeException，业务层可按需捕获。
 */
public class GraphStateException extends RuntimeException {

    public GraphStateException(String message) {
        super(message);
    }

    public GraphStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
