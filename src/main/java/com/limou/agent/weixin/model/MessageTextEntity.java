package com.limou.agent.weixin.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Data;

/**
 * 微信公众号消息实体
 */
@Data
@XStreamAlias("xml")
public class MessageTextEntity {

    @XStreamAlias("ToUserName")
    private String toUserName;

    @XStreamAlias("FromUserName")
    private String fromUserName;

    @XStreamAlias("CreateTime")
    private String createTime;

    @XStreamAlias("MsgType")
    private String msgType;

    @XStreamAlias("Event")
    private String event;

    @XStreamAlias("EventKey")
    private String eventKey;

    @XStreamAlias("MsgId")
    private String msgId;

    @XStreamAlias("MsgID")
    private String msgID;

    @XStreamAlias("Status")
    private String status;

    @XStreamAlias("Ticket")
    private String ticket;

    @XStreamAlias("Content")
    private String content;

}
